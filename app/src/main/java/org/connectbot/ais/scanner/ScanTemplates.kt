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

package org.connectbot.ais.scanner

import android.content.Context
import org.connectbot.ais.AisPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A scan profile turns one scanned code into the keystrokes sent to the host.
 *
 * Template tokens (on top of all macro tokens such as {ENTER} {TAB} {F2} {DELAY:300}):
 *  - {SCAN}            the whole scanned text
 *  - {S1} {S2} …       field n after splitting the scan with [delimiter]
 *  - {S3|1}            field 3, or the default "1" when the QR has no third field / it is empty
 *  - {DATE} {DATE:ddMMyyyy} {TIME}  current date / time
 *
 * Example: `CHN{TAB}{S1}{TAB}{S2|NA}{TAB}{S3|1}{ENTER}`
 */
data class ScanProfile(
    /** Host nickname this profile applies to; blank = default profile for all hosts. */
    val host: String,
    val template: String,
    /** NONE, PIPE, COMMA, SEMICOLON, TAB, GS, SPACE or any literal text. */
    val delimiter: String = "PIPE",
)

object ScanTemplates {
    const val DEFAULT_TEMPLATE = "{SCAN}{ENTER}"

    val delimiterChoices = listOf(
        "AT" to "@",
        "PIPE" to "|",
        "COMMA" to ",",
        "SEMICOLON" to ";",
        "TAB" to "Tab",
        "GS" to "GS (GS1)",
        "SPACE" to "Space",
        "NONE" to "No split",
    )

    // Groups: 1 token, 2 field no., 3 field default, 4 date pattern, 5 QR./M. name, 6 its default.
    private val token = Regex(
        """(?<!\{)\{(SCAN|S(\d{1,2})(?:\|([^}]*))?|DATE(?::([^}]*))?|TIME|((?:QR|M)\.[A-Za-z0-9_]+)(?:\|([^}]*))?)\}""",
        RegexOption.IGNORE_CASE,
    )

    /** Trims the scan; keeps leading/trailing tabs or spaces when they are the delimiter. */
    fun trimScan(raw: String, delimiter: String): String {
        val d = delimiterText(delimiter)
        return if (d == "\t" || d == " ") raw.trim { it == '\r' || it == '\n' } else raw.trim()
    }

    fun delimiterText(delimiter: String): String? = when (delimiter.uppercase(Locale.US)) {
        "NONE", "" -> null
        "AT" -> "@"
        "PIPE" -> "|"
        "COMMA" -> ","
        "SEMICOLON" -> ";"
        "TAB" -> "\t"
        "GS" -> "\u001D"
        "SPACE" -> " "
        else -> delimiter
    }

    /** Escapes text so the macro parser sends it literally. */
    private fun literal(text: String) = text.replace("{", "{{").replace("}", "}}")

    fun split(raw: String, delimiter: String, trim: Boolean): List<String> {
        val d = delimiterText(delimiter) ?: return listOf(if (trim) raw.trim() else raw)
        return raw.split(d).map { if (trim) it.trim() else it }
    }

    /** Resolves scan tokens; the result is a macro sequence for MacroParser. */
    fun render(
        template: String,
        raw: String,
        delimiter: String,
        trim: Boolean = true,
        resolver: ((String) -> String?)? = null,
    ): String {
        val whole = if (trim) trimScan(raw, delimiter) else raw
        val fields = split(whole, delimiter, trim)
        return token.replace(template) { m ->
            val name = m.groupValues[1].uppercase(Locale.US)
            when {
                name == "SCAN" -> literal(whole)
                name == "TIME" -> SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                m.groupValues[5].isNotEmpty() -> {
                    val value = resolver?.invoke(m.groupValues[5].uppercase(Locale.US)).orEmpty()
                    literal(value.ifEmpty { m.groupValues[6] })
                }
                name.startsWith("DATE") -> {
                    val pattern = m.groupValues[4].ifBlank { "yyyy-MM-dd" }
                    runCatching { SimpleDateFormat(pattern, Locale.US).format(Date()) }.getOrDefault(m.value)
                }
                else -> {
                    val index = m.groupValues[2].toIntOrNull() ?: 0
                    val value = fields.getOrNull(index - 1).orEmpty()
                    literal(value.ifEmpty { m.groupValues[3] })
                }
            }
        }
    }
}

class ScanProfileStore(context: Context) {
    private val prefs = AisPrefs(context)

    /** Default profile; migrates the 1.0 prefix/suffix settings on first use. */
    fun defaultProfile(): ScanProfile {
        val stored = prefs.scanTemplate
        val template = stored.ifEmpty {
            val suffix = when (prefs.scannerSuffix) {
                "TAB" -> "{TAB}"
                "CRLF" -> "{CR}{LF}"
                "NONE" -> ""
                else -> "{ENTER}"
            }
            prefs.scannerPrefix.replace("{", "{{").replace("}", "}}") + "{SCAN}" + suffix
        }
        return ScanProfile(host = "", template = template, delimiter = prefs.scanDelimiter)
    }

    fun saveDefault(template: String, delimiter: String) {
        prefs.scanTemplate = template
        prefs.scanDelimiter = delimiter
    }

    fun hostProfiles(): List<ScanProfile> = try {
        val arr = JSONArray(prefs.scanHostProfilesJson.ifBlank { "[]" })
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            ScanProfile(o.getString("host"), o.getString("template"), o.optString("delimiter", "PIPE"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveHostProfiles(list: List<ScanProfile>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("host", it.host).put("template", it.template).put("delimiter", it.delimiter))
        }
        prefs.scanHostProfilesJson = arr.toString()
    }

    /** Per-host profile when one matches the host nickname, otherwise the default. */
    fun forHost(nickname: String?): ScanProfile = hostProfiles().firstOrNull {
        nickname != null && it.host.equals(nickname, ignoreCase = true)
    } ?: defaultProfile()

    fun render(nickname: String?, raw: String): String {
        val profile = forHost(nickname)
        return ScanTemplates.render(profile.template, raw, profile.delimiter, prefs.scannerTrim)
    }
}
