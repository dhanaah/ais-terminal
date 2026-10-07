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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.connectbot.ais.AisPrefs
import org.connectbot.ais.scanner.ScanFormatter
import org.connectbot.ais.scanner.ScannerPreset
import org.connectbot.ais.scanner.rememberCameraScanner

private fun visible(s: String): String = s
    .replace("\r", "⏎")
    .replace("\n", "↵")
    .replace("\t", "⇥")
    .replace("\u001b", "␛")

@Composable
fun ScannerSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AisPrefs(context) }
    var enabled by remember { mutableStateOf(prefs.scannerBroadcastEnabled) }
    var preset by remember { mutableStateOf(runCatching { ScannerPreset.valueOf(prefs.scannerPreset) }.getOrDefault(ScannerPreset.GENERIC)) }
    var action by remember { mutableStateOf(prefs.scannerAction) }
    var extras by remember { mutableStateOf(prefs.scannerExtraKeys) }
    var prefix by remember { mutableStateOf(prefs.scannerPrefix) }
    var suffix by remember { mutableStateOf(prefs.scannerSuffix) }
    var trim by remember { mutableStateOf(prefs.scannerTrim) }
    var vibrate by remember { mutableStateOf(prefs.scannerVibrate) }
    var camera by remember { mutableStateOf(prefs.scannerShowCameraButton) }
    var presetMenu by remember { mutableStateOf(false) }
    var lastScan by remember { mutableStateOf<String?>(null) }

    // Live test: shows what a broadcast scan would type, while this screen is open.
    DisposableEffect(enabled, action, extras) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                lastScan = ScanFormatter.extract(intent, extras)
                    ?: "Broadcast received, but none of the extra keys matched: ${intent.extras?.keySet()?.joinToString()}"
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

    val openCamera = rememberCameraScanner { lastScan = it }

    AisScaffold(title = "Barcode scanner", onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                "Keyboard-wedge scanners work without setup: they type straight into the console. " +
                    "For intent/broadcast output, choose your device below.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )

            SwitchRow("Receive scanner broadcasts", "Types scans into the open console session", enabled) {
                enabled = it
                prefs.scannerBroadcastEnabled = it
            }

            SectionHeader("Device preset")
            Box(Modifier.padding(horizontal = 16.dp)) {
                OutlinedButton(onClick = { presetMenu = true }) { Text(preset.label) }
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

            SectionHeader("Formatting")
            OutlinedTextField(
                value = prefix,
                onValueChange = {
                    prefix = it
                    prefs.scannerPrefix = it
                },
                label = { Text("Prefix (sent before each scan)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Text("Suffix (sent after each scan)", modifier = Modifier.padding(start = 16.dp, top = 12.dp))
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("ENTER" to "Enter", "TAB" to "Tab", "CRLF" to "CR+LF", "NONE" to "None").forEach { (key, label) ->
                    RadioButton(
                        selected = suffix == key,
                        onClick = {
                            suffix = key
                            prefs.scannerSuffix = key
                        },
                    )
                    Text(label)
                }
            }
            SwitchRow("Trim spaces / line breaks", "Removes whitespace the scanner adds around the code", trim) {
                trim = it
                prefs.scannerTrim = it
            }
            SwitchRow("Vibrate on scan", null, vibrate) {
                vibrate = it
                prefs.scannerVibrate = it
            }
            SwitchRow("Camera scan button in console", "Uses the phone camera when there is no hardware scanner", camera) {
                camera = it
                prefs.scannerShowCameraButton = it
            }

            SectionHeader("Test")
            Row(Modifier.padding(horizontal = 16.dp)) {
                OutlinedButton(onClick = openCamera) { Text("Test camera scan") }
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Press the scan trigger now to test broadcast setup.", style = MaterialTheme.typography.bodySmall)
                    val scan = lastScan
                    if (scan != null) {
                        Text("Raw: $scan", fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 8.dp))
                        Text(
                            "Sent: " + visible(ScanFormatter.format(prefs, scan)),
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
