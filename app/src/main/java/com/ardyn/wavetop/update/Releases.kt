package com.ardyn.wavetop.update

/** One downloadable file on a GitHub release. */
data class ReleaseAsset(val name: String, val url: String, val size: Long)

/** A WaveTop release on GitHub, reduced to what the Updates screen needs. */
data class Release(
    val tag: String,
    val version: String,
    val name: String,
    val publishedAt: String,
    val notes: String,
    val htmlUrl: String,
    val prerelease: Boolean,
    val apk: ReleaseAsset?,
    val sums: ReleaseAsset?,
)

/** How a listed release relates to the installed version. */
enum class ReleaseKind { Newer, Current, Older }

/**
 * Version and asset rules, kept free of Android so they can be unit tested.
 * Release tags look like `v1.2.3`; the APK is `WaveTop-1.2.3.apk` with a `SHA256SUMS.txt` beside it.
 */
object Releases {
    const val APK_SUFFIX = ".apk"
    const val SUMS_NAME = "SHA256SUMS.txt"

    fun normalize(v: String): String = v.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')

    private fun parts(v: String): List<Int> = normalize(v).split('.').map { it.toIntOrNull() ?: 0 }

    /** Negative if a < b, zero if equal, positive if a > b. `1.10.0` > `1.9.9`; `1.2` == `1.2.0`. */
    fun compare(a: String, b: String): Int {
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }

    fun kind(release: Release, installed: String): ReleaseKind {
        val c = compare(release.version, installed)
        return when {
            c > 0 -> ReleaseKind.Newer
            c == 0 -> ReleaseKind.Current
            else -> ReleaseKind.Older
        }
    }

    /** The APK to install from a release's assets: `WaveTop-<ver>.apk`, else any single .apk. */
    fun pickApk(assets: List<ReleaseAsset>): ReleaseAsset? {
        val apks = assets.filter { it.name.endsWith(APK_SUFFIX, ignoreCase = true) }
        return apks.firstOrNull { it.name.startsWith("WaveTop", ignoreCase = true) } ?: apks.singleOrNull()
    }

    fun pickSums(assets: List<ReleaseAsset>): ReleaseAsset? =
        assets.firstOrNull { it.name.equals(SUMS_NAME, ignoreCase = true) }
            ?: assets.firstOrNull { it.name.contains("sha256sums", ignoreCase = true) }

    /** A file's expected hash from a `sha256sum` listing ("<hex>  name" or "<hex> *name"), lower-case. */
    fun expectedHash(sumsText: String, fileName: String): String? =
        sumsText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val hash = line.substringBefore(' ').lowercase()
                val name = line.substringAfter(' ').trim().removePrefix("*")
                if (name == fileName && hash.length == 64 && hash.all { it in "0123456789abcdef" }) hash else null
            }
            .firstOrNull()

    /** Newest first; drafts never reach here (the API hides them from unauthenticated callers). */
    fun sorted(releases: List<Release>): List<Release> =
        releases.sortedWith { a, b -> compare(b.version, a.version) }
}
