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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.connectbot.ais.macros.Macro
import org.connectbot.ais.macros.MacroStore

private const val TOKEN_HELP =
    "Tokens: {ENTER} {TAB} {ESC} {BKSP} {UP} {DOWN} {LEFT} {RIGHT} {HOME} {END} {PGUP} {PGDN} " +
        "{INS} {DEL} {F1}…{F12} {CTRL+C} {DELAY:500}. Use {{ for a literal brace.\n" +
        "Example: admin{TAB}{DELAY:300}secret{ENTER}"

@Composable
fun MacrosScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { MacroStore(context) }
    var macros by remember { mutableStateOf(store.load()) }
    var editing by remember { mutableStateOf<Macro?>(null) }
    var creating by remember { mutableStateOf(false) }

    fun persist(list: List<Macro>) {
        macros = list
        store.save(list)
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(MacroStore.toJson(macros).toByteArray()) }
            }.onSuccess { Toast.makeText(context, "Macros exported", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: ""
                store.importJson(text)
            }.onSuccess { count ->
                macros = store.load()
                Toast.makeText(context, "Imported $count macros", Toast.LENGTH_SHORT).show()
            }.onFailure { Toast.makeText(context, "Import failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    AisScaffold(
        title = "Macros",
        onBack = onBack,
        actions = {
            IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                Icon(Icons.Default.FileUpload, contentDescription = "Import macros")
            }
            IconButton(onClick = { exportLauncher.launch("ais_terminal_macros.json") }) {
                Icon(Icons.Default.FileDownload, contentDescription = "Export macros")
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add macro")
            }
        },
    ) {
        Text(
            "Macros appear under the ⚡ button in the console. " + TOKEN_HELP,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp),
        )
        HorizontalDivider()
        LazyColumn {
            itemsIndexed(macros, key = { _, m -> m.id }) { index, macro ->
                ListItem(
                    headlineContent = { Text(macro.name) },
                    supportingContent = {
                        Column {
                            Text(macro.sequence, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (macro.hostFilter.isNotBlank()) {
                                Text("Only for host: ${macro.hostFilter}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = {
                                    val l = macros.toMutableList()
                                    l.add(index - 1, l.removeAt(index))
                                    persist(l)
                                },
                                enabled = index > 0,
                            ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Move up") }
                            IconButton(
                                onClick = {
                                    val l = macros.toMutableList()
                                    l.add(index + 1, l.removeAt(index))
                                    persist(l)
                                },
                                enabled = index < macros.lastIndex,
                            ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Move down") }
                            IconButton(onClick = { persist(macros.filterNot { it.id == macro.id }) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    },
                    modifier = Modifier.clickable { editing = macro },
                )
                HorizontalDivider()
            }
        }
    }

    val target = editing
    if (creating || target != null) {
        MacroEditDialog(
            initial = target,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { saved ->
                persist(
                    if (target == null) {
                        macros + saved
                    } else {
                        macros.map { if (it.id == saved.id) saved else it }
                    },
                )
                creating = false
                editing = null
            },
        )
    }
}

@Composable
private fun MacroEditDialog(initial: Macro?, onDismiss: () -> Unit, onSave: (Macro) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var sequence by remember { mutableStateOf(initial?.sequence ?: "") }
    var host by remember { mutableStateOf(initial?.hostFilter ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New macro" else "Edit macro") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Button name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = sequence,
                    onValueChange = { sequence = it },
                    label = { Text("Sequence") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Only for host nickname (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                Text(TOKEN_HELP, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val base = initial ?: Macro(name = "", sequence = "")
                    onSave(base.copy(name = name.trim(), sequence = sequence, hostFilter = host.trim()))
                },
                enabled = name.isNotBlank() && sequence.isNotEmpty(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
