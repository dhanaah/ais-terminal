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
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class SessionLogInfo(
    val file: File,
    val host: String,
    val started: String,
    val ended: String,
    val lines: Int,
    val inputFile: File?,
)

object SessionLogStore {
    fun list(context: Context): List<SessionLogInfo> {
        val dir = SessionLogger.logDir(context)
        val files = dir.listFiles { f -> f.name.endsWith(SessionLogger.LOG_EXT) } ?: return emptyList()
        return files.sortedByDescending { it.name }.map { info(it) }
    }

    private fun info(file: File): SessionLogInfo {
        var host = ""
        var started = ""
        var ended = ""
        var lines = 0
        file.useLines { seq ->
            seq.forEach { l ->
                when {
                    l.startsWith("# AIS Terminal") -> Unit
                    l.startsWith("# Host: ") -> host = l.removePrefix("# Host: ")
                    l.startsWith("# Started: ") -> started = l.removePrefix("# Started: ")
                    l.startsWith("# Ended: ") -> ended = l.removePrefix("# Ended: ")
                    else -> lines++
                }
            }
        }
        val input = File(file.parentFile, file.name.removeSuffix(SessionLogger.LOG_EXT) + SessionLogger.INPUT_EXT)
        return SessionLogInfo(file, host, started, ended.ifEmpty { "(open)" }, lines, input.takeIf { it.exists() })
    }

    fun delete(info: SessionLogInfo) {
        info.file.delete()
        info.inputFile?.delete()
    }

    fun deleteAll(context: Context) {
        SessionLogger.logDir(context).listFiles()?.forEach { it.delete() }
    }

    fun purgeOlderThan(dir: File, days: Int) {
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        // Files touched in the last hour may belong to a live session; keep them.
        val recent = System.currentTimeMillis() - 3_600_000L
        dir.listFiles()?.filter { it.lastModified() < cutoff && it.lastModified() < recent }?.forEach { it.delete() }
    }

    private fun csv(v: String) = "\"" + v.replace("\"", "\"\"") + "\""

    fun indexCsv(logs: List<SessionLogInfo>): String = buildString {
        append("file,host,started,ended,lines,size_bytes,input_log\n")
        logs.forEach {
            append(csv(it.file.name)).append(',')
            append(csv(it.host)).append(',')
            append(csv(it.started)).append(',')
            append(csv(it.ended)).append(',')
            append(it.lines).append(',')
            append(it.file.length()).append(',')
            append(csv(it.inputFile?.name ?: "")).append('\n')
        }
    }

    /** Writes every log plus an index.csv into a ZIP. */
    fun writeZip(context: Context, target: OutputStream) {
        val logs = list(context)
        ZipOutputStream(target).use { zip ->
            zip.putNextEntry(ZipEntry("index.csv"))
            zip.write(indexCsv(logs).toByteArray())
            zip.closeEntry()
            logs.forEach { log ->
                listOfNotNull(log.file, log.inputFile).forEach { f ->
                    zip.putNextEntry(ZipEntry(f.name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    fun zipFileName(): String =
        "ais_terminal_logs_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.zip"

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".aisfiles", file)

    fun shareIntent(context: Context, files: List<File>, mime: String): Intent {
        val uris = ArrayList(files.map { uriFor(context, it) })
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.type = mime
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Share session log")
    }

    /** Builds the ZIP in the cache dir and returns a share intent for it. */
    fun shareAllIntent(context: Context): Intent {
        val dir = File(context.cacheDir, "ais_export").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val zip = File(dir, zipFileName())
        zip.outputStream().use { writeZip(context, it) }
        return shareIntent(context, listOf(zip), "application/zip")
    }
}
