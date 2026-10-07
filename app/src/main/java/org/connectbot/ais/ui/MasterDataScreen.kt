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

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.ais.txn.MasterStore
import org.connectbot.ais.txn.MasterTable

@Composable
fun MasterDataScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { MasterStore(context) }
    val scope = rememberCoroutineScope()
    var tables by remember { mutableStateOf(store.list()) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingFileName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<MasterTable?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingFileName = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            } ?: "master.csv"
            pendingUri = uri
        }
    }

    AisScaffold(
        title = "Master data",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                text = { Text("Import Excel / CSV") },
            )
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                "Import master files (.xlsx or .csv). Row 1 must hold the column names. " +
                    "Pick the key column that matches the value in the QR code; transactions then " +
                    "use the other columns as defaults, e.g. {M.SUBINVENTORY}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (busy) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            if (tables.isEmpty() && !busy) {
                SettingsCard {
                    Text("No master data yet. Tap “Import Excel / CSV”.", modifier = Modifier.padding(16.dp))
                }
            }
            tables.forEach { t ->
                MasterTableCard(
                    table = t,
                    store = store,
                    onKeyChange = { key ->
                        store.setKeyColumn(t.name, key)
                        tables = store.list()
                    },
                    onDelete = { confirmDelete = t },
                )
            }
            Box(Modifier.padding(bottom = 96.dp))
        }
    }

    val uri = pendingUri
    if (uri != null) {
        var name by remember(uri) {
            mutableStateOf(pendingFileName.substringBeforeLast('.').uppercase().replace(Regex("[^A-Z0-9_]+"), "_"))
        }
        AlertDialog(
            onDismissRequest = { pendingUri = null },
            title = { Text("Import master") },
            text = {
                Column {
                    Text(pendingFileName, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Table name (e.g. ITEM_MASTER)") },
                        singleLine = true,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "Importing with an existing name replaces that table.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val fileName = pendingFileName
                        val existingKey = store.get(name.trim().uppercase())?.keyColumn
                        pendingUri = null
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    context.contentResolver.openInputStream(uri)?.use { store.import(name, fileName, it, existingKey) }
                                        ?: error("Cannot open file")
                                }
                            }
                            busy = false
                            tables = store.list()
                            result.onSuccess {
                                Toast.makeText(context, "Imported ${it.rows} rows into ${it.name}", Toast.LENGTH_LONG).show()
                            }.onFailure {
                                Toast.makeText(context, "Import failed: ${it.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                ) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { pendingUri = null }) { Text("Cancel") } },
        )
    }

    confirmDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${t.name}?") },
            text = { Text("Transactions using this table will stop finding master data.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.delete(t.name)
                        tables = store.list()
                        confirmDelete = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MasterTableCard(
    table: MasterTable,
    store: MasterStore,
    onKeyChange: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var test by remember(table.name) { mutableStateOf("") }
    SettingsCard {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(table.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${table.rows} rows · ${table.columns.size} columns · ${table.importedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(table.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
        }
        Text("Key column (matches the QR value)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            table.columns.forEach { col ->
                FilterChip(selected = col == table.keyColumn, onClick = { onKeyChange(col) }, label = { Text(col) })
            }
        }
        OutlinedTextField(
            value = test,
            onValueChange = { test = it },
            label = { Text("Test lookup: enter a ${table.keyColumn}") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
        if (test.isNotBlank()) {
            val row = remember(test, table) { store.lookup(table.name, test) }
            Box(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.small)
                    .padding(10.dp),
            ) {
                Text(
                    row?.entries?.joinToString("\n") { "${it.key} = ${it.value}" } ?: "Not found",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
