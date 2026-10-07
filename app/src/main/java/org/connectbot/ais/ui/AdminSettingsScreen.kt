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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.connectbot.ais.AisPrefs
import org.connectbot.ais.admin.AdminLock
import org.connectbot.ais.admin.Kiosk
import org.connectbot.ais.admin.findActivity

@Composable
fun AdminSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AisPrefs(context) }
    var pinSet by remember { mutableStateOf(AdminLock.isPinSet(context)) }
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var relock by remember { mutableStateOf(prefs.adminRelockMinutes.toString()) }
    var kiosk by remember { mutableStateOf(prefs.kioskEnabled) }
    var screenOn by remember { mutableStateOf(prefs.keepScreenOn) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    AisScaffold(title = "Admin lock & kiosk", onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionHeader(if (pinSet) "Change admin PIN" else "Set admin PIN")
            Text(
                "With a PIN set, operators can only connect to saved hosts, use macros and scan. " +
                    "Editing hosts, keys, settings, macros, scanner setup and logs needs the PIN.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = pin1,
                    onValueChange = { pin1 = it.filter(Char::isDigit).take(12) },
                    label = { Text("New PIN (4–12 digits)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pin2,
                    onValueChange = { pin2 = it.filter(Char::isDigit).take(12) },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                Row(Modifier.padding(top = 12.dp)) {
                    Button(
                        onClick = {
                            if (pin1.length < 4) {
                                toast("PIN must be at least 4 digits")
                            } else if (pin1 != pin2) {
                                toast("PINs do not match")
                            } else {
                                AdminLock.setPin(context, pin1)
                                pin1 = ""
                                pin2 = ""
                                pinSet = true
                                toast("Admin PIN saved")
                            }
                        },
                    ) { Text(if (pinSet) "Change PIN" else "Set PIN") }
                    if (pinSet) {
                        OutlinedButton(
                            onClick = {
                                context.findActivity()?.let { Kiosk.exit(it) }
                                AdminLock.setPin(context, "")
                                pinSet = false
                                kiosk = false
                                toast("Admin lock removed")
                            },
                            modifier = Modifier.padding(start = 12.dp),
                        ) { Text("Remove lock") }
                    }
                }
            }

            if (pinSet) {
                SectionHeader("Session")
                OutlinedTextField(
                    value = relock,
                    onValueChange = { v ->
                        relock = v.filter(Char::isDigit).take(3)
                        relock.toIntOrNull()?.let { prefs.adminRelockMinutes = it }
                    },
                    label = { Text("Relock after (minutes)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                OutlinedButton(
                    onClick = {
                        AdminLock.lock()
                        onBack()
                    },
                    modifier = Modifier.padding(16.dp),
                ) { Text("Lock now") }

                SectionHeader("Kiosk")
                SwitchRow(
                    "Kiosk mode (screen pinning)",
                    "Pins AIS Terminal to the screen so operators cannot switch apps. " +
                        "Android asks to confirm the first time unless the device is MDM-managed.",
                    kiosk,
                ) { on ->
                    kiosk = on
                    prefs.kioskEnabled = on
                    val activity = context.findActivity()
                    if (activity != null) {
                        if (on) Kiosk.apply(activity) else Kiosk.exit(activity)
                    }
                }
            }

            SwitchRow("Keep screen on", "Stops the screen sleeping while the app is open", screenOn) { on ->
                screenOn = on
                prefs.keepScreenOn = on
                context.findActivity()?.let { Kiosk.apply(it) }
            }
        }
    }
}
