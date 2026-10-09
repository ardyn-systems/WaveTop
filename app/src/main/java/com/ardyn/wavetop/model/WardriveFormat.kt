package com.ardyn.wavetop.model

/** What a saved wardrive says about itself; counts are null until the drive is finished. */
data class DriveMeta(
    val name: String,
    val startedMs: Long,
    val endedMs: Long? = null,
    val observations: Int? = null,
    val wifiDevices: Int? = null,
    val bluetoothDevices: Int? = null,
    val geotagged: Int? = null,
) {
    /** A drive with no end line was cut short (app killed, phone died) and saved up to that point. */
    val interrupted: Boolean get() = endedMs == null
}

class ParsedDrive(val meta: DriveMeta, val observations: List<Observation>) {
    /** Ended time, or the last sighting for an interrupted drive. */
    val endMs: Long get() = meta.endedMs ?: observations.maxOfOrNull { it.timeMs } ?: meta.startedMs
    val durationMs: Long get() = (endMs - meta.startedMs).coerceAtLeast(0)
}

/**
 * WaveTop's native wardrive file: a CSV with one row per sighting, wrapped in `#` metadata
 * lines. Rows are appended as the drive runs, so a crash still leaves a readable file;
 * the footer with the end time and totals is written when the drive is stopped.
 *
 * ```
 * # wavetop-wardrive,1
 * # name,Downtown loop
 * # started,1791142200000
 * time_ms,phy,mac,name,type,crypto,capabilities,channel,freq_mhz,rssi,lat,lon,accuracy_m,manufacturer
 * 1791142201234,wifi,aa:bb:…,HomeNet,AP,WPA2 (CCMP),[WPA2-PSK-CCMP][ESS],6,2437,-51,37.4,-122.0,4.0,Netgear
 * # ended,1791145800000
 * # totals,812,140,61,790
 * ```
 */
object WardriveCsv {
    const val MAGIC = "wavetop-wardrive"
    const val VERSION = 1
    const val EXTENSION = ".wtdrive.csv"
    private const val COLUMNS =
        "time_ms,phy,mac,name,type,crypto,capabilities,channel,freq_mhz,rssi,lat,lon,accuracy_m,manufacturer"

    fun header(name: String, startedMs: Long): String = buildString {
        append("# ").append(Csv.join(listOf(MAGIC, VERSION.toString()))).append('\n')
        append("# ").append(Csv.join(listOf("name", name))).append('\n')
        append("# ").append(Csv.join(listOf("started", startedMs.toString()))).append('\n')
        append(COLUMNS).append('\n')
    }

    fun row(o: Observation): String = Csv.join(
        listOf(
            o.timeMs.toString(),
            if (o.phy == Phy.Wifi) "wifi" else "bt",
            o.mac,
            o.name,
            o.type,
            o.crypto,
            o.capabilities,
            o.channel?.toString().orEmpty(),
            o.frequencyMhz?.toString().orEmpty(),
            o.rssi?.toString().orEmpty(),
            o.fix?.lat?.toString().orEmpty(),
            o.fix?.lon?.toString().orEmpty(),
            o.fix?.accuracyM?.toString().orEmpty(),
            o.manufacturer,
        ),
    ) + "\n"

    fun footer(endedMs: Long, observations: Int, wifi: Int, bluetooth: Int, geotagged: Int): String =
        "# " + Csv.join(listOf("ended", endedMs.toString())) + "\n" +
            "# " + Csv.join(listOf("totals", "$observations", "$wifi", "$bluetooth", "$geotagged")) + "\n"

    /** Reads only the `#` lines — enough for the drive list without loading every row. */
    fun parseMeta(commentLines: List<String>): DriveMeta? {
        var name: String? = null
        var started: Long? = null
        var ended: Long? = null
        var totals: List<String>? = null
        var magic = false
        for (line in commentLines) {
            if (!line.startsWith("#")) continue
            val f = Csv.split(line.removePrefix("#").trim())
            when (f.firstOrNull()) {
                MAGIC -> magic = true
                "name" -> name = f.getOrNull(1)
                "started" -> started = f.getOrNull(1)?.toLongOrNull()
                "ended" -> ended = f.getOrNull(1)?.toLongOrNull()
                "totals" -> totals = f.drop(1)
            }
        }
        if (!magic || started == null) return null
        return DriveMeta(
            name = name ?: "Untitled drive",
            startedMs = started,
            endedMs = ended,
            observations = totals?.getOrNull(0)?.toIntOrNull(),
            wifiDevices = totals?.getOrNull(1)?.toIntOrNull(),
            bluetoothDevices = totals?.getOrNull(2)?.toIntOrNull(),
            geotagged = totals?.getOrNull(3)?.toIntOrNull(),
        )
    }

    fun parse(lines: Sequence<String>): ParsedDrive? {
        val comments = ArrayList<String>()
        val observations = ArrayList<Observation>()
        for (line in lines) {
            when {
                line.isBlank() -> Unit
                line.startsWith("#") -> comments += line
                line.startsWith("time_ms,") -> Unit
                else -> parseRow(line)?.let(observations::add)
            }
        }
        val meta = parseMeta(comments) ?: return null
        return ParsedDrive(meta, observations)
    }

    /** A half-written last row (power cut mid-write) is skipped rather than failing the file. */
    private fun parseRow(line: String): Observation? {
        val f = Csv.split(line)
        if (f.size < 13) return null
        val time = f[0].toLongOrNull() ?: return null
        val lat = f[10].toDoubleOrNull()
        val lon = f[11].toDoubleOrNull()
        val fix = if (lat != null && lon != null) {
            GeoFix(lat, lon, f[12].toFloatOrNull() ?: 0f, time)
        } else {
            null
        }
        return Observation(
            timeMs = time,
            phy = if (f[1] == "wifi") Phy.Wifi else Phy.Bluetooth,
            mac = f[2],
            name = f[3],
            type = f[4],
            crypto = f[5],
            capabilities = f[6],
            channel = f[7].toIntOrNull(),
            frequencyMhz = f[8].toIntOrNull(),
            manufacturer = f.getOrNull(13).orEmpty(),
            rssi = f[9].toIntOrNull(),
            fix = fix,
        )
    }
}

/** Minimal RFC 4180 CSV: quote fields containing commas, quotes or line breaks. */
object Csv {
    fun join(fields: List<String>): String = fields.joinToString(",") { escape(it) }

    fun escape(field: String): String {
        // Line breaks would split a row; SSIDs can legally contain them.
        val clean = field.replace('\n', ' ').replace('\r', ' ')
        return if (clean.any { it == ',' || it == '"' }) "\"" + clean.replace("\"", "\"\"") + "\"" else clean
    }

    fun split(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> {
                    out += sb.toString()
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }
}
