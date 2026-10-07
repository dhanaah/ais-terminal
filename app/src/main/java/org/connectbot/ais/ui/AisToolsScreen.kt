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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.connectbot.BuildConfig
import org.connectbot.ais.admin.AdminLock
import org.connectbot.ais.admin.AdminPinDialog

object AisRoutes {
    const val TOOLS = "ais_tools"
    const val MACROS = "ais_macros"
    const val SCANNER = "ais_scanner"
    const val LOGS = "ais_logs"
    const val ADMIN = "ais_admin"
}

@Composable
fun AisToolsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val revision by AdminLock.changes.collectAsState()
    val pinSet = remember(revision) { AdminLock.isPinSet(context) }
    val admin = remember(revision) { AdminLock.isAdmin(context) }
    var askPin by remember { mutableStateOf(false) }

    AisScaffold(title = "AIS Tools", onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (pinSet) {
                ListItem(
                    headlineContent = { Text(if (admin) "Admin mode (unlocked)" else "Operator mode (locked)") },
                    supportingContent = {
                        Text(if (admin) "Admin features unlocked; relocks automatically" else "Unlock to change hosts and settings")
                    },
                    leadingContent = { Icon(if (admin) Icons.Default.LockOpen else Icons.Default.Lock, null) },
                    trailingContent = {
                        if (admin) {
                            TextButton(onClick = { AdminLock.lock() }) { Text("Lock now") }
                        } else {
                            TextButton(onClick = { askPin = true }) { Text("Unlock") }
                        }
                    },
                )
                HorizontalDivider()
            }

            ToolItem(Icons.Default.Bolt, "Macros", "One-tap text and key sequences for the console") {
                onOpen(AisRoutes.MACROS)
            }
            ToolItem(Icons.Default.QrCodeScanner, "Barcode scanner", "Hardware scanner broadcast, camera scan, prefix / suffix") {
                onOpen(AisRoutes.SCANNER)
            }
            ToolItem(Icons.Default.Description, "Session logs", "Record sessions, view, share and export") {
                onOpen(AisRoutes.LOGS)
            }
            ToolItem(Icons.Default.AdminPanelSettings, "Admin lock & kiosk", "PIN, operator mode, screen pinning") {
                onOpen(AisRoutes.ADMIN)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("AIS Terminal", style = MaterialTheme.typography.titleMedium)
                    Text("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
                    Text(AIS_DEVELOPER_CREDIT, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Based on ConnectBot © Kenny Root and contributors, Apache License 2.0.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }

    if (askPin) {
        AdminPinDialog(onDismiss = { askPin = false }, onUnlocked = { askPin = false })
    }
}

@Composable
private fun ToolItem(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
