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

package org.connectbot.ais.macros

import android.content.Context
import kotlinx.coroutines.delay
import org.connectbot.ais.AisPrefs
import org.connectbot.service.TerminalBridge
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Macro(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sequence: String,
    /** Optional host nickname filter; blank = all hosts. */
    val hostFilter: String = "",
)

sealed class MacroStep {
    data class Text(val text: String) : MacroStep()
    data class Delay(val millis: Long) : MacroStep()
}

/**
 * Macro syntax: plain text plus tokens in braces, e.g. `admin{TAB}secret{ENTER}`.
 *
 * Tokens: {ENTER} {TAB} {ESC} {BKSP} {SPACE} {UP} {DOWN} {LEFT} {RIGHT} {HOME} {END}
 * {PGUP} {PGDN} {INS} {DEL} {F1}..{F12} {CTRL+A}..{CTRL+Z} {DELAY:500} and {{ for a
 * literal brace. Unknown tokens are sent as typed.
 */
object MacroParser {
    private const val ESC = "\u001b"

    private val named = mapOf(
        "ENTER" to "\r",
        "CR" to "\r",
        "LF" to "\n",
        "TAB" to "\t",
        "ESC" to ESC,
        "BKSP" to "\u007f",
        "BS" to "\b",
        "SPACE" to " ",
        "UP" to "$ESC[A",
        "DOWN" to "$ESC[B",
        "RIGHT" to "$ESC[C",
        "LEFT" to "$ESC[D",
        "HOME" to "$ESC[1~",
        "END" to "$ESC[4~",
        "INS" to "$ESC[2~",
        "DEL" to "$ESC[3~",
        "PGUP" to "$ESC[5~",
        "PGDN" to "$ESC[6~",
        "F1" to "${ESC}OP",
        "F2" to "${ESC}OQ",
        "F3" to "${ESC}OR",
        "F4" to "${ESC}OS",
        "F5" to "$ESC[15~",
        "F6" to "$ESC[17~",
        "F7" to "$ESC[18~",
        "F8" to "$ESC[19~",
        "F9" to "$ESC[20~",
        "F10" to "$ESC[21~",
        "F11" to "$ESC[23~",
        "F12" to "$ESC[24~",
    )

    fun parse(sequence: String): List<MacroStep> {
        val steps = mutableListOf<MacroStep>()
        val buf = StringBuilder()
        fun flush() {
            if (buf.isNotEmpty()) {
                steps.add(MacroStep.Text(buf.toString()))
                buf.setLength(0)
            }
        }
        var i = 0
        while (i < sequence.length) {
            val c = sequence[i]
            if (c == '{') {
                if (i + 1 < sequence.length && sequence[i + 1] == '{') {
                    buf.append('{')
                    i += 2
                    continue
                }
                val close = sequence.indexOf('}', i + 1)
                if (close > i) {
                    val token = sequence.substring(i + 1, close).trim().uppercase()
                    val resolved = resolve(token)
                    when {
                        resolved is MacroStep.Delay -> {
                            flush()
                            steps.add(resolved)
                        }
                        resolved is MacroStep.Text -> buf.append(resolved.text)
                        else -> buf.append(sequence, i, close + 1)
                    }
                    i = close + 1
                    continue
                }
            }
            if (c == '}' && i + 1 < sequence.length && sequence[i + 1] == '}') {
                buf.append('}')
                i += 2
                continue
            }
            buf.append(c)
            i++
        }
        flush()
        return steps
    }

    private fun resolve(token: String): MacroStep? {
        named[token]?.let { return MacroStep.Text(it) }
        if (token.startsWith("DELAY:") || token.startsWith("WAIT:")) {
            val ms = token.substringAfter(':').trim().toLongOrNull() ?: return null
            return MacroStep.Delay(ms.coerceIn(0L, 60_000L))
        }
        if (token.startsWith("CTRL+") && token.length == 6) {
            val ch = token[5]
            if (ch in 'A'..'Z' || ch in "[\\]^_@") {
                return MacroStep.Text((ch.code and 0x1f).toChar().toString())
            }
        }
        return null
    }

    /** Sends a macro to a terminal session, honouring {DELAY:n} steps. */
    suspend fun send(bridge: TerminalBridge, sequence: String) {
        for (step in parse(sequence)) {
            when (step) {
                is MacroStep.Text -> bridge.injectString(step.text)
                is MacroStep.Delay -> delay(step.millis)
            }
        }
    }
}

class MacroStore(context: Context) {
    private val prefs = AisPrefs(context)

    fun load(): List<Macro> {
        val json = prefs.macrosJson
        if (json.isBlank()) return defaults()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { idx ->
                val o = arr.getJSONObject(idx)
                Macro(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.optString("name"),
                    sequence = o.optString("sequence"),
                    hostFilter = o.optString("hostFilter"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(macros: List<Macro>) {
        prefs.macrosJson = toJson(macros)
    }

    fun forHost(nickname: String?): List<Macro> = load().filter {
        it.hostFilter.isBlank() || (nickname != null && it.hostFilter.equals(nickname, ignoreCase = true))
    }

    fun importJson(json: String): Int {
        val arr = JSONArray(json)
        val incoming = (0 until arr.length()).map { idx ->
            val o = arr.getJSONObject(idx)
            Macro(
                name = o.getString("name"),
                sequence = o.getString("sequence"),
                hostFilter = o.optString("hostFilter"),
            )
        }
        save(load() + incoming)
        return incoming.size
    }

    companion object {
        fun toJson(macros: List<Macro>): String {
            val arr = JSONArray()
            macros.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("name", it.name)
                        .put("sequence", it.sequence)
                        .put("hostFilter", it.hostFilter),
                )
            }
            return arr.toString(2)
        }

        fun defaults(): List<Macro> = listOf(
            Macro(name = "Enter", sequence = "{ENTER}"),
            Macro(name = "Ctrl+C", sequence = "{CTRL+C}"),
            Macro(name = "Ctrl+X", sequence = "{CTRL+X}"),
            Macro(name = "Ctrl+Z", sequence = "{CTRL+Z}"),
            Macro(name = "Esc", sequence = "{ESC}"),
        )
    }
}
