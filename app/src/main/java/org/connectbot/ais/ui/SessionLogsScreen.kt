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

package org.connectbot.ais.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.connectbot.ais.AisPrefs
import org.connectbot.ais.logging.SessionLogInfo
import org.connectbot.ais.logging.SessionLogStore

@Composable
fun SessionLogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AisPrefs(context) }
    var enabled by remember { mutableStateOf(prefs.loggingEnabled) }
    var timestamps by remember { mutableStateOf(prefs.logTimestamps) }
    var logInput by remember { mutableStateOf(prefs.logInput) }
    var days by remember { mutableStateOf(prefs.logRetentionDays.toString()) }
    var logs by remember { mutableStateOf(SessionLogStore.list(context)) }
    var viewing by remember { mutableStateOf<SessionLogInfo?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var confirmInput by remember { mutableStateOf(false) }

    fun refresh() {
        logs = SessionLogStore.list(context)
    }

    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { SessionLogStore.writeZip(context, it) }
            }.onSuccess { Toast.makeText(context, "Logs saved", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Save failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    AisScaffold(
        title = "Session logs",
        onBack = onBack,
        actions = {
            IconButton(
                onClick = {
                    runCatching { context.startActivity(SessionLogStore.shareAllIntent(context)) }
                        .onFailure { Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_LONG).show() }
                },
                enabled = logs.isNotEmpty(),
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share all as ZIP")
            }
            IconButton(onClick = { saveZip.launch(SessionLogStore.zipFileName()) }, enabled = logs.isNotEmpty()) {
                Icon(Icons.Default.Save, contentDescription = "Save all as ZIP")
            }
            IconButton(onClick = { confirmDeleteAll = true }, enabled = logs.isNotEmpty()) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Delete all logs")
            }
        },
    ) {
        SwitchRow("Record sessions", "Saves a text transcript of every new connection", enabled) {
            enabled = it
            prefs.loggingEnabled = it
        }
        SwitchRow("Timestamp each line", null, timestamps) {
            timestamps = it
            prefs.logTimestamps = it
        }
        SwitchRow("Also log keys sent (CSV)", "Records typed input, scans and macros. Passwords typed into the terminal will be recorded.", logInput) {
            if (it) {
                confirmInput = true
            } else {
                logInput = false
                prefs.logInput = false
            }
        }
        OutlinedTextField(
            value = days,
            onValueChange = { v ->
                days = v.filter(Char::isDigit).take(4)
                days.toIntOrNull()?.let { prefs.logRetentionDays = it }
            },
            label = { Text("Keep logs for (days)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Text(
            "Changes apply to the next connection. ZIP exports include index.csv (host, start, end, lines, size).",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp),
        )
        HorizontalDivider()
        if (logs.isEmpty()) {
            Text("No session logs yet.", modifier = Modifier.padding(16.dp))
        }
        LazyColumn {
            items(logs, key = { it.file.name }) { log ->
                ListItem(
                    headlineContent = { Text(log.host.ifEmpty { log.file.name }) },
                    supportingContent = {
                        Text("${log.started} → ${log.ended} · ${log.lines} lines · ${log.file.length() / 1024} KB")
                    },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = {
                                    context.startActivity(
                                        SessionLogStore.shareIntent(context, listOfNotNull(log.file, log.inputFile), "text/plain"),
                                    )
                                },
                            ) { Icon(Icons.Default.Share, contentDescription = "Share") }
                            IconButton(
                                onClick = {
                                    SessionLogStore.delete(log)
                                    refresh()
                                },
                            ) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                        }
                    },
                    modifier = Modifier.clickable { viewing = log },
                )
                HorizontalDivider()
            }
        }
    }

    viewing?.let { log ->
        val text = remember(log) {
            runCatching {
                val tail = ArrayDeque<String>()
                var total = 0
                log.file.useLines { seq ->
                    seq.forEach { l ->
                        total++
                        tail.addLast(l)
                        if (tail.size > 1000) tail.removeFirst()
                    }
                }
                (if (total > 1000) "… (showing last 1000 of $total lines)\n" else "") + tail.joinToString("\n")
            }.getOrElse { "Unable to read log: ${it.message}" }
        }
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(log.host.ifEmpty { log.file.name }) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("Close") } },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all logs?") },
            text = { Text("This permanently removes ${logs.size} session logs from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        SessionLogStore.deleteAll(context)
                        refresh()
                        confirmDeleteAll = false
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } },
        )
    }

    if (confirmInput) {
        AlertDialog(
            onDismissRequest = { confirmInput = false },
            title = { Text("Log typed input?") },
            text = {
                Text(
                    "Everything sent to the host is written to a CSV file, including anything typed " +
                        "at a password prompt in the terminal. Only enable this for audit needs.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        logInput = true
                        prefs.logInput = true
                        confirmInput = false
                    },
                ) { Text("Enable") }
            },
            dismissButton = { TextButton(onClick = { confirmInput = false }) { Text("Cancel") } },
        )
    }
}
