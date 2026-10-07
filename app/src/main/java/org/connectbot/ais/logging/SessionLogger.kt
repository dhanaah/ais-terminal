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

package org.connectbot.ais.logging

import android.content.Context
import org.connectbot.ais.AisPrefs
import org.connectbot.data.entity.Host
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes a plain-text transcript of one terminal session (escape sequences removed) and,
 * optionally, a CSV of everything sent to the host. All methods are thread-safe: output
 * arrives on the relay thread, input on the transport writer thread.
 */
class SessionLogger private constructor(
    private val logFile: File,
    private val inputFile: File?,
    private val timestamps: Boolean,
) {
    private val out: BufferedWriter = writer(logFile)
    private val inp: BufferedWriter? = inputFile?.let { writer(it) }
    private val line = StringBuilder()
    private var state = State.NORMAL
    private var lastWasCr = false
    private var closed = false

    private enum class State { NORMAL, ESC, CSI, OSC, OSC_ESC, SKIP1 }

    @Synchronized
    fun output(text: CharSequence) {
        if (closed) return
        try {
            for (i in 0 until text.length) feed(text[i])
            if (line.length > MAX_LINE) flushLine()
            out.flush()
        } catch (e: Exception) {
            Timber.w(e, "Session log write failed")
        }
    }

    @Synchronized
    fun input(data: ByteArray) {
        val w = inp ?: return
        if (closed || data.isEmpty()) return
        try {
            val readable = buildString {
                for (b in data) {
                    val c = b.toInt() and 0xff
                    when {
                        c == 0x0d -> append("<CR>")
                        c == 0x0a -> append("<LF>")
                        c == 0x09 -> append("<TAB>")
                        c == 0x1b -> append("<ESC>")
                        c == 0x7f -> append("<BKSP>")
                        c < 0x20 -> append("<CTRL+").append(('@'.code + c).toChar()).append('>')
                        c >= 0x80 -> append("<0x").append(Integer.toHexString(c).uppercase()).append('>')
                        else -> append(c.toChar())
                    }
                }
            }
            w.write(stamp(FULL))
            w.write(",")
            w.write(data.size.toString())
            w.write(",\"")
            w.write(readable.replace("\"", "\"\""))
            w.write("\"\n")
            w.flush()
        } catch (e: Exception) {
            Timber.w(e, "Session input log write failed")
        }
    }

    @Synchronized
    fun close(reason: String) {
        if (closed) return
        closed = true
        try {
            flushLine()
            out.write("# Ended: ${stamp(FULL)} ($reason)\n")
            out.close()
            inp?.close()
        } catch (e: Exception) {
            Timber.w(e, "Session log close failed")
        }
    }

    private fun feed(c: Char) {
        when (state) {
            State.NORMAL -> normal(c)
            State.ESC -> state = when (c) {
                '[' -> State.CSI
                ']', 'P', 'X', '^', '_' -> State.OSC
                '(', ')', '*', '+', '#', '%' -> State.SKIP1
                else -> State.NORMAL
            }
            State.CSI -> if (c.code in 0x40..0x7e) {
                state = State.NORMAL
                // Cursor positioning / screen clears start a new logical line.
                if (c == 'H' || c == 'f' || c == 'J' || c == 'd') flushLine()
            }
            State.OSC -> when (c) {
                '\u0007' -> state = State.NORMAL
                '\u001b' -> state = State.OSC_ESC
                else -> Unit
            }
            State.OSC_ESC -> state = if (c == '\\') State.NORMAL else State.OSC
            State.SKIP1 -> state = State.NORMAL
        }
    }

    private fun normal(c: Char) {
        val wasCr = lastWasCr
        lastWasCr = false
        when {
            c == '\u001b' -> state = State.ESC
            c == '\n' -> {
                if (!wasCr) flushLine(force = true) else Unit
            }
            c == '\r' -> {
                flushLine(force = true)
                lastWasCr = true
            }
            c == '\b' -> if (line.isNotEmpty()) line.setLength(line.length - 1)
            c == '\t' -> line.append(c)
            c.code < 0x20 -> Unit
            else -> line.append(c)
        }
    }

    private fun flushLine(force: Boolean = false) {
        if (line.isEmpty() && !force) return
        if (timestamps) out.write("[${stamp(TIME)}] ")
        out.write(line.toString())
        out.write("\n")
        line.setLength(0)
    }

    companion object {
        private const val MAX_LINE = 4000
        private const val FULL = "yyyy-MM-dd HH:mm:ss"
        private const val TIME = "HH:mm:ss"
        const val DIR = "session_logs"
        const val LOG_EXT = ".log"
        const val INPUT_EXT = ".input.csv"

        private fun stamp(pattern: String): String = SimpleDateFormat(pattern, Locale.US).format(Date())

        private fun writer(file: File) = BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8))

        fun logDir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

        /** Starts a new log for [host] if logging is enabled; returns null otherwise. */
        fun open(context: Context, host: Host): SessionLogger? {
            val prefs = AisPrefs(context)
            if (!prefs.loggingEnabled) return null
            return try {
                val dir = logDir(context)
                SessionLogStore.purgeOlderThan(dir, prefs.logRetentionDays)
                val safeName = host.nickname.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(40).ifEmpty { "session" }
                val base = "${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}_$safeName"
                val log = File(dir, base + LOG_EXT)
                val input = if (prefs.logInput) File(dir, base + INPUT_EXT) else null
                input?.writeText("timestamp,bytes,data\n")
                val target = when (host.protocol) {
                    "local" -> "local"
                    "telnet" -> "telnet://${host.hostname}:${host.port}"
                    else -> "${host.protocol}://${host.username}@${host.hostname}:${host.port}"
                }
                log.writeText(
                    "# AIS Terminal session log\n" +
                        "# Host: ${host.nickname} ($target)\n" +
                        "# Started: ${stamp(FULL)}\n",
                )
                SessionLogger(log, input, prefs.logTimestamps)
            } catch (e: Exception) {
                Timber.w(e, "Unable to open session log")
                null
            }
        }
    }
}
