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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.connectbot.ais.AisPrefs
import org.connectbot.ais.scanner.ScanTemplates
import org.connectbot.ais.txn.MasterStore
import org.connectbot.ais.txn.ScanStatus
import org.connectbot.ais.txn.TransactionConfig
import org.connectbot.ais.txn.TransactionEngine
import org.connectbot.ais.txn.TransactionStore
import org.connectbot.ais.txn.transactionIcon

private const val TXN_HELP =
    "Template tokens: {QR.ITEM} QR field by name · {M.SUBINVENTORY} master column · " +
        "{M.LOCATOR|A01} with default · {S1} {SCAN} {DATE} · keys {TAB} {ENTER} {F2} {CTRL+X} {DELAY:300}"

@Composable
fun TransactionsSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { TransactionStore(context) }
    val prefs = remember { AisPrefs(context) }
    var configs by remember { mutableStateOf(store.load()) }
    var editing by remember { mutableStateOf<TransactionConfig?>(null) }
    var defaultHost by remember { mutableStateOf(prefs.defaultTxnHost) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(store.exportJson().toByteArray()) } }
                .onSuccess { Toast.makeText(context, "Transaction setup exported", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: ""
                store.importJson(text)
            }.onSuccess {
                configs = store.load()
                Toast.makeText(context, "Transaction setup imported", Toast.LENGTH_SHORT).show()
            }.onFailure { Toast.makeText(context, "Import failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    AisScaffold(
        title = "Transactions setup",
        onBack = onBack,
        actions = {
            IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                Icon(Icons.Default.FileUpload, contentDescription = "Import setup")
            }
            IconButton(onClick = { exportLauncher.launch("ais_terminal_transactions.json") }) {
                Icon(Icons.Default.FileDownload, contentDescription = "Export setup")
            }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                "Each menu: the operator scans the QR code; the app splits it into fields, looks up the master " +
                    "row and types the template into the terminal. Export the setup to copy it to other devices.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SettingsCard {
                OutlinedTextField(
                    value = defaultHost,
                    onValueChange = {
                        defaultHost = it
                        prefs.defaultTxnHost = it
                    },
                    label = { Text("Default host for menus (nickname)") },
                    supportingText = { Text("Used by the home-screen menus when a menu has no host of its own") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            configs.forEachIndexed { i, c ->
                SettingsCard(Modifier.clickable { editing = c }) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(transactionIcon(c.id), null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        ) {
                            Text("${i + 1}. ${c.title}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOf(
                                    c.hostNickname.ifBlank { "default host" },
                                    c.masterTable.ifBlank { "no master" },
                                    if (c.autoSend) "auto-send" else "confirm",
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(c.scanTemplate, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1)
                        }
                        Switch(
                            checked = c.enabled,
                            onCheckedChange = { on ->
                                store.update(c.copy(enabled = on))
                                configs = store.load()
                            },
                        )
                    }
                }
            }
            Box(Modifier.padding(bottom = 24.dp))
        }
    }

    editing?.let { draft ->
        TransactionEditDialog(
            initial = draft,
            masterNames = remember { MasterStore(context).names() },
            onDismiss = { editing = null },
            onSave = {
                store.update(it)
                configs = store.load()
                editing = null
            },
        )
    }
}

@Composable
private fun TransactionEditDialog(
    initial: TransactionConfig,
    masterNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (TransactionConfig) -> Unit,
) {
    val context = LocalContext.current
    var c by remember { mutableStateOf(initial) }
    var sample by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(initial.title) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(c.title, { c = c.copy(title = it) }, label = { Text("Menu title") }, singleLine = true)
                OutlinedTextField(
                    c.hostNickname,
                    { c = c.copy(hostNickname = it) },
                    label = { Text("Host nickname (blank = default)") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 6.dp),
                )

                Text("Master table", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = c.masterTable.isBlank(), onClick = { c = c.copy(masterTable = "") }, label = { Text("None") })
                    masterNames.forEach { n ->
                        FilterChip(selected = c.masterTable == n, onClick = { c = c.copy(masterTable = n) }, label = { Text(n) })
                    }
                }

                Text("QR split on", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScanTemplates.delimiterChoices.forEach { (key, label) ->
                        FilterChip(selected = c.qrDelimiter == key, onClick = { c = c.copy(qrDelimiter = key) }, label = { Text(label) })
                    }
                }
                OutlinedTextField(
                    c.qrFields,
                    { c = c.copy(qrFields = it) },
                    label = { Text("QR field names, in order") },
                    supportingText = { Text("e.g. KEY,ITEM,LOT,QTY") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text("Lookup key field", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    c.fieldNames().forEach { f ->
                        FilterChip(selected = c.keyField.equals(f, true), onClick = { c = c.copy(keyField = f) }, label = { Text(f) })
                    }
                }

                OutlinedTextField(
                    c.startSequence,
                    { c = c.copy(startSequence = it) },
                    label = { Text("On open: keys to reach the screen") },
                    supportingText = { Text("e.g. 4{ENTER}{DELAY:400}2{ENTER}") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedTextField(
                    c.scanTemplate,
                    { c = c.copy(scanTemplate = it) },
                    label = { Text("Per scan: keys / fields to send") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(top = 6.dp),
                )
                OutlinedTextField(
                    c.exitSequence,
                    { c = c.copy(exitSequence = it) },
                    label = { Text("On exit: keys to leave the screen") },
                    supportingText = { Text("e.g. {F2}{DELAY:300}{F2}") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(TXN_HELP, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Send automatically after scan", Modifier.weight(1f))
                    Switch(checked = c.autoSend, onCheckedChange = { c = c.copy(autoSend = it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Block if not in master", Modifier.weight(1f))
                    Switch(checked = c.requireMaster, onCheckedChange = { c = c.copy(requireMaster = it) })
                }

                OutlinedTextField(
                    sample,
                    { sample = it },
                    label = { Text("Test with a sample QR") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (sample.isNotBlank()) {
                    val result = remember(sample, c) { TransactionEngine.process(context, c, sample) }
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth()
                            .background(Color(0xFF0E1218), MaterialTheme.shapes.small)
                            .padding(10.dp),
                    ) {
                        Text(
                            if (result.status == ScanStatus.NOT_IN_MASTER) {
                                "✗ ${result.message}"
                            } else {
                                previewKeys(result.sequence)
                            },
                            color = Color(0xFF7EF8E7),
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(c) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
