package com.ardyn.wavetop.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import com.ardyn.wavetop.BuildConfig
import com.ardyn.wavetop.prefs.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** What the Updates screen shows. */
sealed interface UpdatePhase {
    data object Idle : UpdatePhase
    data object Checking : UpdatePhase
    data class Downloading(val release: Release, val percent: Int) : UpdatePhase
    data class Verifying(val release: Release) : UpdatePhase
    data class Installing(val release: Release) : UpdatePhase
    /** Android needs the user to allow WaveTop to install apps before it can update itself. */
    data class NeedsInstallPermission(val release: Release) : UpdatePhase
}

data class UpdaterState(
    val phase: UpdatePhase = UpdatePhase.Idle,
    /** Every release on GitHub, newest first; empty until the first check. */
    val releases: List<Release> = emptyList(),
    val checkedAtMs: Long = 0L,
    /** The newest release if it's newer than this install: lights the dot on the cog. */
    val available: Release? = null,
    /** Last outcome, e.g. "WaveTop is up to date" or why something failed. */
    val message: String? = null,
    val messageIsError: Boolean = false,
)

/**
 * WaveTop's self-updater, the Android counterpart of NetSeer's and NineLives':
 * check GitHub releases → download the APK → verify it against the release's SHA256SUMS →
 * hand it to Android's package installer. Android then shows its own confirmation (skipped on
 * Android 12+ once WaveTop installed itself), replaces the app, and [UpdateReceiver] says hello
 * from the new version.
 *
 * Android never installs an older version over a newer one, so "roll back" means uninstalling
 * first; the UI explains that instead of offering a button that can't work.
 */
class Updater private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settings = AppSettings.get(context)
    private val _state = MutableStateFlow(UpdaterState())
    val state: StateFlow<UpdaterState> = _state.asStateFlow()
    private var job: Job? = null

    /** The running version without the "-debug" suffix debug builds carry. */
    val installedVersion: String = BuildConfig.VERSION_NAME.substringBefore("-")
    val isDebugBuild: Boolean = BuildConfig.DEBUG
    val releasesPageUrl: String = "https://github.com/${BuildConfig.GITHUB_REPO}/releases"

    /** On launch: check at most once a day, and only if the user left that switch on. */
    fun autoCheckIfDue() {
        val s = settings.current
        if (!s.autoCheckUpdates) return
        if (System.currentTimeMillis() - s.lastUpdateCheckMs < DAY_MS) return
        check(quiet = true)
    }

    /** @param quiet don't report "up to date" or network errors (the background launch check). */
    fun check(quiet: Boolean = false) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.update { it.copy(phase = UpdatePhase.Checking, message = null) }
            val result = runCatching { withContext(Dispatchers.IO) { fetchReleases() } }
            val now = System.currentTimeMillis()
            settings.update { it.copy(lastUpdateCheckMs = now) }
            result.onSuccess { list ->
                val newest = list.firstOrNull { !it.prerelease && it.apk != null }
                val available = newest?.takeIf { Releases.compare(it.version, installedVersion) > 0 }
                _state.update {
                    it.copy(
                        phase = UpdatePhase.Idle,
                        releases = list,
                        checkedAtMs = now,
                        available = available,
                        message = when {
                            available != null -> "WaveTop ${available.version} is available."
                            quiet -> null
                            list.isEmpty() -> "No releases have been published yet."
                            else -> "WaveTop is up to date."
                        },
                        messageIsError = false,
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        phase = UpdatePhase.Idle,
                        message = if (quiet) null else "Couldn't check for updates: ${e.message ?: e.javaClass.simpleName}",
                        messageIsError = !quiet,
                    )
                }
            }
        }
    }

    /** Update, or reinstall the current version. The click is the go-ahead, as in NetSeer. */
    fun install(release: Release) {
        if (job?.isActive == true) return
        val apk = release.apk ?: return fail("Release ${release.version} has no APK to install.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            _state.update { it.copy(phase = UpdatePhase.NeedsInstallPermission(release), message = null) }
            return
        }
        job = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val dest = File(dir, apk.name)
                    download(apk.url, dest) { pct ->
                        _state.update { it.copy(phase = UpdatePhase.Downloading(release, pct)) }
                    }
                    dest
                }
                _state.update { it.copy(phase = UpdatePhase.Verifying(release)) }
                withContext(Dispatchers.IO) { verify(release, file) }
                _state.update { it.copy(phase = UpdatePhase.Installing(release), message = null) }
                withContext(Dispatchers.IO) { commit(file) }
                // From here Android's installer takes over; UpdateReceiver reports back.
            } catch (e: Exception) {
                fail(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Opens Android's "Install unknown apps" switch for WaveTop. */
    fun installPermissionIntent(): Intent =
        Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun cancelPermissionPrompt() = _state.update { it.copy(phase = UpdatePhase.Idle) }

    /** Called by [UpdateReceiver] when Android's installer reports a failure or a cancel. */
    fun reportInstallResult(success: Boolean, message: String?) {
        _state.update {
            it.copy(
                phase = UpdatePhase.Idle,
                message = if (success) "Installed. WaveTop will restart." else "The update didn't install: ${message ?: "cancelled"}",
                messageIsError = !success,
            )
        }
    }

    private fun fail(message: String) {
        _state.update { it.copy(phase = UpdatePhase.Idle, message = message, messageIsError = true) }
    }

    // --- network ----------------------------------------------------------------------------

    private fun fetchReleases(): List<Release> {
        val body = get("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases?per_page=30")
        val arr = JSONArray(body)
        val list = (0 until arr.length()).map { i ->
            val r = arr.getJSONObject(i)
            val assetsJson = r.optJSONArray("assets") ?: JSONArray()
            val assets = (0 until assetsJson.length()).map { j ->
                val a = assetsJson.getJSONObject(j)
                ReleaseAsset(a.optString("name"), a.optString("browser_download_url"), a.optLong("size"))
            }
            val tag = r.optString("tag_name")
            Release(
                tag = tag,
                version = Releases.normalize(tag),
                name = r.optString("name").ifBlank { tag },
                publishedAt = r.optString("published_at"),
                notes = r.optString("body"),
                htmlUrl = r.optString("html_url"),
                prerelease = r.optBoolean("prerelease"),
                apk = Releases.pickApk(assets),
                sums = Releases.pickSums(assets),
            )
        }
        return Releases.sorted(list)
    }

    private fun verify(release: Release, file: File) {
        val sums = release.sums ?: throw IOException("Release ${release.version} has no SHA256SUMS to check the download against.")
        val expected = Releases.expectedHash(get(sums.url), file.name)
            ?: throw IOException("${file.name} isn't listed in ${sums.name}.")
        val actual = sha256(file)
        if (actual != expected) {
            file.delete()
            throw IOException("The download doesn't match GitHub's checksum, so it wasn't installed. Try again.")
        }
    }

    private fun commit(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Lets Android skip the confirmation once WaveTop is the app that installed itself.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, UpdateReceiver::class.java).setAction(UpdateReceiver.ACTION_INSTALL_RESULT),
                flags,
            )
            session.commit(callback.intentSender)
        }
    }

    private fun get(url: String): String {
        val conn = open(url)
        try {
            when (val code = conn.responseCode) {
                in 200..299 -> Unit
                // A private repo, or one without any releases, looks like "not found" to GitHub's API.
                404 -> throw IOException("GitHub has no public WaveTop releases yet.")
                403, 429 -> throw IOException("GitHub is rate-limiting update checks; try again in an hour.")
                else -> throw IOException("HTTP $code from ${URL(url).host}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun download(url: String, dest: File, progress: (Int) -> Unit) {
        val conn = open(url)
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode} downloading the update")
            val total = conn.contentLengthLong
            var done = 0L
            var lastPct = -1
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else 0
                        if (pct != lastPct) {
                            lastPct = pct
                            progress(pct)
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            // GitHub's API rejects requests without a User-Agent.
            setRequestProperty("User-Agent", "WaveTop/$installedVersion (Android)")
            setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
        }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        @Volatile
        private var instance: Updater? = null

        fun get(context: Context): Updater =
            instance ?: synchronized(this) {
                instance ?: Updater(context.applicationContext).also { instance = it }
            }
    }
}
