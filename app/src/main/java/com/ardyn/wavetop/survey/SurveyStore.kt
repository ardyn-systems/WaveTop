package com.ardyn.wavetop.survey

import android.content.Context
import android.os.Build
import com.ardyn.wavetop.model.SurveyExport
import com.ardyn.wavetop.model.SurveyMeta
import com.ardyn.wavetop.model.Observation
import com.ardyn.wavetop.model.ParsedSurvey
import com.ardyn.wavetop.model.Phy
import com.ardyn.wavetop.model.SurveyCsv
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.RandomAccessFile

/** A saved survey as listed on the Surveys tab. */
data class SurveyEntry(val file: File, val meta: SurveyMeta, val sizeBytes: Long) {
    val id: String get() = file.name
}

enum class ExportFormat(val label: String, val suffix: String, val mime: String) {
    KismetNetxml("Kismet netxml (NetSeer)", ".netxml", "application/xml"),
    WigleCsv("WiGLE CSV", ".wigle.csv", "text/csv"),
    WaveTopCsv("WaveTop raw CSV", SurveyCsv.EXTENSION, "text/csv"),
}

/**
 * Surveys live in app-private storage (filesDir/surveys), one native CSV each.
 * Exports are written to cache/exports so they can be shared without exposing the originals.
 */
class SurveyStore(private val context: Context) {
    private val dir = File(context.filesDir, "surveys").apply { mkdirs() }
    private val exportDir get() = File(context.cacheDir, "exports").apply { mkdirs() }

    /** Picks a file for a new survey; the user's name is kept in the file, this is just a safe path. */
    fun newFile(name: String): File {
        val stem = safeStem(name)
        var file = File(dir, stem + SurveyCsv.EXTENSION)
        var i = 2
        while (file.exists()) file = File(dir, "$stem-${i++}" + SurveyCsv.EXTENSION)
        return file
    }

    fun list(): List<SurveyEntry> =
        dir.listFiles { f -> f.name.endsWith(SurveyCsv.EXTENSION) }.orEmpty()
            .mapNotNull { f -> SurveyCsv.parseMeta(headAndTail(f))?.let { SurveyEntry(f, it, f.length()) } }
            .sortedByDescending { it.meta.startedMs }

    fun load(entry: SurveyEntry): ParsedSurvey? =
        entry.file.bufferedReader().useLines { SurveyCsv.parse(it) }

    fun delete(entry: SurveyEntry): Boolean = entry.file.delete()

    fun export(entry: SurveyEntry, survey: ParsedSurvey, format: ExportFormat): File {
        val out = File(exportDir, safeStem(entry.meta.name) + format.suffix)
        when (format) {
            ExportFormat.KismetNetxml -> out.writeText(SurveyExport.kismetNetxml(survey))
            ExportFormat.WigleCsv -> out.writeText(SurveyExport.wigleCsv(survey, deviceInfo()))
            ExportFormat.WaveTopCsv -> entry.file.copyTo(out, overwrite = true)
        }
        return out
    }

    /** Every saved survey in one zip of the native files, for backups or moving to a new phone. Null if none. */
    fun exportAll(stamp: String): File? {
        val surveys = list()
        if (surveys.isEmpty()) return null
        val out = File(exportDir, "WaveTop-surveys-$stamp.zip")
        java.util.zip.ZipOutputStream(out.outputStream().buffered()).use { zip ->
            for (d in surveys) {
                zip.putNextEntry(java.util.zip.ZipEntry(d.file.name))
                d.file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return out
    }

    /** @return how many surveys were deleted. */
    fun deleteAll(except: File?): Int =
        list().count { it.file != except && it.file.delete() }

    private fun deviceInfo(): SurveyExport.DeviceInfo {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
        return SurveyExport.DeviceInfo(
            appVersion = "WaveTop-$version",
            model = Build.MODEL.orEmpty(),
            release = Build.VERSION.RELEASE.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            brand = Build.BRAND.orEmpty(),
        )
    }

    /** Metadata sits in the first and last few lines; reading only those keeps the list fast for long surveys. */
    private fun headAndTail(file: File): List<String> {
        val lines = ArrayList<String>()
        file.bufferedReader().use { r ->
            repeat(4) { r.readLine()?.let(lines::add) }
        }
        RandomAccessFile(file, "r").use { raf ->
            val len = raf.length()
            val start = (len - 2048).coerceAtLeast(0)
            raf.seek(start)
            val bytes = ByteArray((len - start).toInt())
            raf.readFully(bytes)
            lines += String(bytes, Charsets.UTF_8).lines().filter { it.startsWith("#") }
        }
        return lines
    }

    companion object {
        fun safeStem(name: String): String =
            name.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-', '.').take(60).ifBlank { "survey" }
    }
}

/**
 * Appends one survey's sightings to its file as they happen. Rows are flushed every few
 * seconds, so a crash or a dead battery loses at most that much; [finish] adds the footer.
 * Main thread only — writes are small and buffered.
 */
class SurveyRecorder(val file: File, val name: String, val startedMs: Long) {
    // FileWriter(File, Charset, …) is API 33+; this works back to minSdk 24.
    private val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file, false), Charsets.UTF_8))
    private val wifi = HashSet<String>()
    private val bluetooth = HashSet<String>()
    private var lastFlushMs = 0L

    var observations = 0
        private set
    var geotagged = 0
        private set
    val wifiDevices get() = wifi.size
    val bluetoothDevices get() = bluetooth.size

    init {
        writer.write(SurveyCsv.header(name, startedMs))
        writer.flush()
    }

    fun record(batch: List<Observation>, nowMs: Long) {
        if (batch.isEmpty()) return
        for (o in batch) {
            // Cached scan results from before Start aren't part of this survey.
            if (o.timeMs < startedMs) continue
            writer.write(SurveyCsv.row(o))
            observations++
            if (o.fix != null) geotagged++
            if (o.phy == Phy.Wifi) wifi += o.mac.lowercase() else bluetooth += o.mac.lowercase()
        }
        if (nowMs - lastFlushMs >= FLUSH_MS) {
            writer.flush()
            lastFlushMs = nowMs
        }
    }

    fun finish(endedMs: Long): SurveyMeta {
        writer.write(SurveyCsv.footer(endedMs, observations, wifiDevices, bluetoothDevices, geotagged))
        writer.close()
        return SurveyMeta(name, startedMs, endedMs, observations, wifiDevices, bluetoothDevices, geotagged)
    }

    private companion object {
        const val FLUSH_MS = 5_000L
    }
}
