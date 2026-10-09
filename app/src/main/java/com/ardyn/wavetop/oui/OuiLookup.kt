package com.ardyn.wavetop.oui

import java.io.BufferedReader
import java.io.InputStream
import java.util.zip.GZIPInputStream

class OuiLookup(private val table: Map<Int, String>) {

    fun manufacturerFor(mac: String): String {
        if (isLocallyAdministered(mac)) return "Unknown (randomized MAC)"
        val oui = oui24(mac) ?: return "Unknown"
        return table[oui] ?: "Unknown"
    }

    companion object {
        fun fromMap(table: Map<Int, String>): OuiLookup = OuiLookup(table)

        fun loadFromGzipTsv(input: InputStream): OuiLookup {
            val table = HashMap<Int, String>(40960)
            GZIPInputStream(input).bufferedReader().use { reader ->
                parseTsv(reader, table)
            }
            return OuiLookup(table)
        }

        fun loadFromTsv(input: InputStream): OuiLookup {
            val table = HashMap<Int, String>()
            input.bufferedReader().use { reader -> parseTsv(reader, table) }
            return OuiLookup(table)
        }

        fun oui24(mac: String): Int? {
            val hex = mac.filter { it.isLetterOrDigit() }.uppercase()
            if (hex.length < 6) return null
            return hex.substring(0, 6).toIntOrNull(16)
        }

        fun isLocallyAdministered(mac: String): Boolean {
            val hex = mac.filter { it.isLetterOrDigit() }
            if (hex.length < 2) return false
            val first = hex.substring(0, 2).toIntOrNull(16) ?: return false
            return first and 0x02 != 0
        }

        private fun parseTsv(reader: BufferedReader, table: MutableMap<Int, String>) {
            reader.lineSequence().forEach { line ->
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                val tab = line.indexOf('\t')
                if (tab <= 0) return@forEach
                val oui = line.substring(0, tab).trim().toIntOrNull(16) ?: return@forEach
                val name = line.substring(tab + 1).trim()
                if (name.isNotEmpty()) table[oui] = name
            }
        }
    }
}
