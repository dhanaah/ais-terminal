/*
 * AIS Terminal — additions to ConnectBot
 * Developed by DT (AIS Glass, Supply Chain Planning)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.ais.txn

import android.content.Context
import android.util.Xml
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

/** One imported master table (from an Excel or CSV file). First row = column headers. */
data class MasterTable(
    val name: String,
    val columns: List<String>,
    val keyColumn: String,
    val rows: Int,
    val importedAt: String,
    val source: String,
)

/** Simple CSV reader/writer supporting quoted fields, commas, quotes and new lines. */
object Csv {
    fun parse(text: String, separator: Char? = null): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        val src = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        // Detect ; separated files (common from Excel in some locales).
        val firstLine = src.lineSequence().firstOrNull().orEmpty()
        val sep = separator ?: listOf(',', ';', '\t').maxByOrNull { ch -> firstLine.count { it == ch } } ?: ','
        while (i < src.length) {
            val c = src[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < src.length && src[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        quoted = false
                    }
                } else {
                    cell.append(c)
                }
            } else {
                when (c) {
                    // A quote only opens a quoted cell at the start of a cell (6" PANEL stays literal).
                    '"' -> if (cell.isEmpty()) quoted = true else cell.append(c)
                    sep -> {
                        row.add(cell.toString())
                        cell.setLength(0)
                    }
                    '\r' -> Unit
                    '\n' -> {
                        row.add(cell.toString())
                        cell.setLength(0)
                        rows.add(row.toList())
                        row.clear()
                    }
                    else -> cell.append(c)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row.toList())
        }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    private fun quote(v: String) =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' || it == ';' || it == '\t' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    fun write(rows: List<List<String>>): String = rows.joinToString("\n") { r -> r.joinToString(",") { quote(it) } } + "\n"
}

/** Minimal .xlsx reader (first worksheet, values only) without external libraries. */
object Xlsx {
    fun read(input: InputStream): List<List<String>> {
        var shared: ByteArray? = null
        val sheets = sortedMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                when {
                    e.name == "xl/sharedStrings.xml" -> shared = zip.readBytes()
                    e.name.startsWith("xl/worksheets/sheet") && e.name.endsWith(".xml") -> sheets[e.name] = zip.readBytes()
                }
                e = zip.nextEntry
            }
        }
        val sheet = sheets["xl/worksheets/sheet1.xml"] ?: sheets.values.firstOrNull()
            ?: throw IllegalArgumentException("No worksheet found in the Excel file")
        val strings = shared?.let { sharedStrings(it) } ?: emptyList()
        return sheetRows(sheet, strings)
    }

    private fun parser(bytes: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(ByteArrayInputStream(bytes), "UTF-8")
    }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val out = mutableListOf<String>()
        val p = parser(bytes)
        val sb = StringBuilder()
        var inSi = false
        var inT = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> {
                        inSi = true
                        sb.setLength(0)
                    }
                    "t" -> inT = inSi
                }
                XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "t" -> inT = false
                    "si" -> {
                        out.add(sb.toString())
                        inSi = false
                    }
                }
            }
            ev = p.next()
        }
        return out
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1) else break
        }
        return n - 1
    }

    private fun cleanNumber(v: String): String {
        val d = v.toDoubleOrNull() ?: return v
        return if (d == Math.floor(d) && !d.isInfinite() && kotlin.math.abs(d) < 1e15) d.toLong().toString() else v
    }

    private fun sheetRows(bytes: ByteArray, strings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val p = parser(bytes)
        var current = mutableMapOf<Int, String>()
        var col = 0
        var type = ""
        val value = StringBuilder()
        var capture = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> current = mutableMapOf()
                    "c" -> {
                        col = p.getAttributeValue(null, "r")?.let { colIndex(it) } ?: (current.keys.maxOrNull()?.plus(1) ?: 0)
                        type = p.getAttributeValue(null, "t") ?: ""
                        value.setLength(0)
                    }
                    "v", "t" -> capture = true
                }
                XmlPullParser.TEXT -> if (capture) value.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> capture = false
                    "c" -> {
                        val raw = value.toString()
                        current[col] = when (type) {
                            "s" -> strings.getOrNull(raw.trim().toIntOrNull() ?: -1).orEmpty()
                            "inlineStr", "str" -> raw
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            else -> cleanNumber(raw)
                        }
                    }
                    "row" -> {
                        val width = (current.keys.maxOrNull() ?: -1) + 1
                        rows.add((0 until width).map { current[it].orEmpty() })
                    }
                }
            }
            ev = p.next()
        }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }
}

/** Column / table names usable in {M.NAME}: upper case letters, digits and underscores. */
fun normalise(name: String): String = name.trim().uppercase(Locale.US).replace(Regex("[^A-Z0-9_]+"), "_").trim('_')

class MasterStore(private val context: Context) {
    private fun dir() = File(context.filesDir, "masters").apply { mkdirs() }
    private fun dataFile(name: String) = File(dir(), "$name.csv")
    private fun metaFile(name: String) = File(dir(), "$name.meta.json")

    fun list(): List<MasterTable> = dir().listFiles { f -> f.name.endsWith(".meta.json") }
        ?.mapNotNull { runCatching { readMeta(it) }.getOrNull() }
        ?.sortedBy { it.name }
        ?: emptyList()

    fun names(): List<String> = list().map { it.name }

    private fun readMeta(f: File): MasterTable {
        val o = JSONObject(f.readText())
        val cols = o.getJSONArray("columns")
        return MasterTable(
            name = o.getString("name"),
            columns = (0 until cols.length()).map { cols.getString(it) },
            keyColumn = o.getString("keyColumn"),
            rows = o.getInt("rows"),
            importedAt = o.optString("importedAt"),
            source = o.optString("source"),
        )
    }

    fun get(name: String): MasterTable? = metaFile(name).takeIf { it.exists() }?.let { runCatching { readMeta(it) }.getOrNull() }

    /** Imports a CSV or XLSX stream as table [name]. Returns the stored table description. */
    fun import(name: String, fileName: String, input: InputStream, keyColumn: String? = null): MasterTable {
        val safe = normalise(name).ifEmpty { "MASTER" }
        val bytes = input.readBytes()
        val isXlsx = fileName.lowercase(Locale.US).endsWith(".xlsx") ||
            (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte())
        val rows = if (isXlsx) Xlsx.read(ByteArrayInputStream(bytes)) else Csv.parse(bytes.decodeToString())
        require(rows.size >= 2) { "The file needs a header row and at least one data row" }
        val header = rows.first().mapIndexed { i, h -> normalise(h).ifEmpty { "COL${i + 1}" } }
        val data = rows.drop(1).map { r -> header.indices.map { r.getOrElse(it) { "" }.trim() } }
        val key = keyColumn?.let { normalise(it) }?.takeIf { it in header } ?: header.first()
        dataFile(safe).writeText(Csv.write(listOf(header) + data))
        val meta = MasterTable(
            name = safe,
            columns = header,
            keyColumn = key,
            rows = data.size,
            importedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()),
            source = fileName,
        )
        saveMeta(meta)
        MasterCache.invalidate(safe)
        return meta
    }

    fun setKeyColumn(name: String, key: String) {
        val m = get(name) ?: return
        saveMeta(m.copy(keyColumn = key))
        MasterCache.invalidate(name)
    }

    private fun saveMeta(m: MasterTable) {
        metaFile(m.name).writeText(
            JSONObject()
                .put("name", m.name)
                .put("columns", org.json.JSONArray(m.columns))
                .put("keyColumn", m.keyColumn)
                .put("rows", m.rows)
                .put("importedAt", m.importedAt)
                .put("source", m.source)
                .toString(),
        )
    }

    fun delete(name: String) {
        dataFile(name).delete()
        metaFile(name).delete()
        MasterCache.invalidate(name)
    }

    /** Finds the master row whose key column equals [key] (case-insensitive, trimmed). */
    fun lookup(name: String, key: String): Map<String, String>? {
        val meta = get(name) ?: return null
        return MasterCache.index(meta, dataFile(name))[key.trim().uppercase(Locale.US)]
    }

    fun sample(name: String, limit: Int = 5): List<Map<String, String>> {
        val meta = get(name) ?: return emptyList()
        return MasterCache.index(meta, dataFile(name)).values.take(limit)
    }
}

/** In-memory index per table, rebuilt when the file or key column changes. */
private object MasterCache {
    private data class Entry(val stamp: String, val index: Map<String, Map<String, String>>)
    private val cache = mutableMapOf<String, Entry>()

    @Synchronized
    fun invalidate(name: String) {
        cache.remove(name)
    }

    @Synchronized
    fun index(meta: MasterTable, file: File): Map<String, Map<String, String>> {
        val stamp = "${file.lastModified()}:${meta.keyColumn}"
        cache[meta.name]?.takeIf { it.stamp == stamp }?.let { return it.index }
        val rows = if (file.exists()) Csv.parse(file.readText(), ',') else emptyList()
        val header = rows.firstOrNull() ?: emptyList()
        val keyIdx = header.indexOf(meta.keyColumn).coerceAtLeast(0)
        val index = LinkedHashMap<String, Map<String, String>>()
        rows.drop(1).forEach { r ->
            val map = header.indices.associate { header[it] to r.getOrElse(it) { "" } }
            val k = r.getOrElse(keyIdx) { "" }.trim().uppercase(Locale.US)
            if (k.isNotEmpty() && k !in index) index[k] = map
        }
        cache[meta.name] = Entry(stamp, index)
        return index
    }
}
