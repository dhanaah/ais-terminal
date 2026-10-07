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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.connectbot.ais.AisPrefs
import org.connectbot.ais.macros.MacroParser
import org.connectbot.ais.macros.MacroStep
import org.connectbot.ais.scanner.ScanFormatter
import org.connectbot.ais.scanner.ScanProfile
import org.connectbot.ais.scanner.ScanProfileStore
import org.connectbot.ais.scanner.ScanTemplates
import org.connectbot.ais.scanner.ScannerPreset
import org.connectbot.ais.scanner.rememberCameraScanner

private const val TEMPLATE_HELP =
    "{SCAN} whole code · {S1} {S2}… split fields · {S3|1} field 3 or default 1 · " +
        "{DATE} {DATE:ddMMyyyy} {TIME} · plus macro keys {TAB} {ENTER} {F2} {DELAY:300}"

private val insertTokens = listOf("{SCAN}", "{S1}", "{S2}", "{S3|1}", "{TAB}", "{ENTER}", "{DATE}", "{DELAY:300}")

/** Renders a macro sequence as readable keys for previews. */
internal fun previewKeys(sequence: String): String = MacroParser.parse(sequence).joinToString("") { step ->
    when (step) {
        is MacroStep.Delay -> "⏱${step.millis}ms"
        is MacroStep.Text -> step.text
            .replace("\r\n", "⏎")
            .replace("\r", "⏎")
            .replace("\n", "↵")
            .replace("\t", "⇥")
            .replace("\u001b", "␛")
            .replace("\u001d", "‹GS›")
    }
}

@Composable
fun ScannerSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AisPrefs(context) }
    val store = remember { ScanProfileStore(context) }
    val initial = remember { store.defaultProfile() }

    var enabled by remember { mutableStateOf(prefs.scannerBroadcastEnabled) }
    var preset by remember { mutableStateOf(runCatching { ScannerPreset.valueOf(prefs.scannerPreset) }.getOrDefault(ScannerPreset.GENERIC)) }
    var action by remember { mutableStateOf(prefs.scannerAction) }
    var extras by remember { mutableStateOf(prefs.scannerExtraKeys) }
    var template by remember { mutableStateOf(TextFieldValue(initial.template)) }
    var delimiter by remember { mutableStateOf(initial.delimiter) }
    var hostProfiles by remember { mutableStateOf(store.hostProfiles()) }
    var editingProfile by remember { mutableStateOf<ScanProfile?>(null) }
    var trim by remember { mutableStateOf(prefs.scannerTrim) }
    var vibrate by remember { mutableStateOf(prefs.scannerVibrate) }
    var camera by remember { mutableStateOf(prefs.scannerShowCameraButton) }
    var presetMenu by remember { mutableStateOf(false) }
    var sample by remember { mutableStateOf("ITEM-1001|LOT-55") }

    fun saveTemplate(t: String = template.text, d: String = delimiter) = store.saveDefault(t, d)

    // Live test: a broadcast scan fills the sample while this screen is open.
    DisposableEffect(enabled, action, extras) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                sample = ScanFormatter.extract(intent, extras)
                    ?: "No matching extra. Keys: ${intent.extras?.keySet()?.joinToString()}"
            }
        }
        val registered = enabled && action.isNotBlank()
        if (registered) {
            ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_EXPORTED)
        }
        onDispose {
            if (registered) runCatching { context.unregisterReceiver(receiver) }
        }
    }

    val openCamera = rememberCameraScanner { sample = it }

    AisScaffold(title = "Barcode scanner", onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            // ---- Template ----
            SectionHeader("Scan template & default data")
            SettingsCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = template,
                        onValueChange = {
                            template = it
                            saveTemplate(it.text)
                        },
                        label = { Text("Default template (all hosts)") },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier
                            .padding(top = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        insertTokens.forEach { tok ->
                            AssistChip(
                                onClick = {
                                    val t = template.text
                                    val at = template.selection.start.coerceIn(0, t.length)
                                    val newText = t.substring(0, at) + tok + t.substring(at)
                                    template = TextFieldValue(newText, TextRange(at + tok.length))
                                    saveTemplate(newText)
                                },
                                label = { Text(tok, fontFamily = FontFamily.Monospace) },
                            )
                        }
                    }
                    Text(
                        TEMPLATE_HELP,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text("Split QR fields on", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ScanTemplates.delimiterChoices.forEach { (key, label) ->
                            FilterChip(
                                selected = delimiter == key,
                                onClick = {
                                    delimiter = key
                                    saveTemplate(d = key)
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }

            // ---- Preview ----
            SectionHeader("Try it")
            SettingsCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = sample,
                        onValueChange = { sample = it },
                        label = { Text("Sample scan (or scan now)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val rendered = ScanTemplates.render(template.text, sample, delimiter, trim)
                    val fields = ScanTemplates.split(sample, delimiter, trim)
                    Text(
                        fields.mapIndexed { i, f -> "S${i + 1}=$f" }.joinToString("   "),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Box(
                        Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .background(Color(0xFF0E1218), MaterialTheme.shapes.small)
                            .padding(12.dp),
                    ) {
                        Text(previewKeys(rendered), color = Color(0xFF7EF8E7), fontFamily = FontFamily.Monospace)
                    }
                    Row(Modifier.padding(top = 10.dp)) {
                        FilledTonalButton(onClick = openCamera) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                            Text("Test with camera", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }

            // ---- Per-host ----
            SectionHeader("Per-host templates")
            SettingsCard {
                Text(
                    "Override the default for a specific host (by nickname), e.g. a different org code per plant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                hostProfiles.forEach { profile ->
                    ListItem(
                        headlineContent = { Text(profile.host) },
                        supportingContent = { Text(profile.template, fontFamily = FontFamily.Monospace) },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    hostProfiles = hostProfiles - profile
                                    store.saveHostProfiles(hostProfiles)
                                },
                            ) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.padding(0.dp),
                    )
                }
                TextButton(
                    onClick = { editingProfile = ScanProfile(host = "", template = template.text, delimiter = delimiter) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Add host template", modifier = Modifier.padding(start = 6.dp))
                }
            }

            // ---- Device ----
            SectionHeader("Scanner device")
            SettingsCard {
                SwitchRow("Receive scanner broadcasts", "For scanners in intent / broadcast output mode", enabled) {
                    enabled = it
                    prefs.scannerBroadcastEnabled = it
                }
                Box(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedButton(onClick = { presetMenu = true }) { Text(preset.label + "  ▾") }
                    DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                        ScannerPreset.entries.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.label) },
                                onClick = {
                                    presetMenu = false
                                    preset = p
                                    action = p.action
                                    extras = p.extras
                                    prefs.scannerPreset = p.name
                                    prefs.scannerAction = p.action
                                    prefs.scannerExtraKeys = p.extras
                                },
                            )
                        }
                    }
                }
                Text(
                    preset.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                OutlinedTextField(
                    value = action,
                    onValueChange = {
                        action = it
                        prefs.scannerAction = it
                    },
                    label = { Text("Broadcast action") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                OutlinedTextField(
                    value = extras,
                    onValueChange = {
                        extras = it
                        prefs.scannerExtraKeys = it
                    },
                    label = { Text("Data extra key(s), comma separated") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    "Keyboard-wedge scanners need no setup; they type straight into the console.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            SectionHeader("Behaviour")
            SettingsCard {
                SwitchRow("Trim spaces / line breaks", "Cleans the code and each split field", trim) {
                    trim = it
                    prefs.scannerTrim = it
                }
                SwitchRow("Vibrate on scan", null, vibrate) {
                    vibrate = it
                    prefs.scannerVibrate = it
                }
                SwitchRow("Camera scan button in console", "For phones without a hardware scanner", camera) {
                    camera = it
                    prefs.scannerShowCameraButton = it
                }
            }
            Box(Modifier.padding(bottom = 24.dp))
        }
    }

    editingProfile?.let { draft ->
        HostTemplateDialog(
            initial = draft,
            onDismiss = { editingProfile = null },
            onSave = { saved ->
                hostProfiles = hostProfiles.filterNot { it.host.equals(saved.host, ignoreCase = true) } + saved
                store.saveHostProfiles(hostProfiles)
                editingProfile = null
            },
        )
    }
}

@Composable
private fun HostTemplateDialog(initial: ScanProfile, onDismiss: () -> Unit, onSave: (ScanProfile) -> Unit) {
    var host by remember { mutableStateOf(initial.host) }
    var template by remember { mutableStateOf(initial.template) }
    var delimiter by remember { mutableStateOf(initial.delimiter) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Host template") },
        text = {
            Column {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host nickname (e.g. WMS-ANT)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = { Text("Template") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                Row(
                    Modifier
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ScanTemplates.delimiterChoices.forEach { (key, label) ->
                        FilterChip(selected = delimiter == key, onClick = { delimiter = key }, label = { Text(label) })
                    }
                }
                Text(TEMPLATE_HELP, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(ScanProfile(host.trim(), template, delimiter)) },
                enabled = host.isNotBlank() && template.isNotEmpty(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
