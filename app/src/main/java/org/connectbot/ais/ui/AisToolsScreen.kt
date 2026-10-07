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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ElevatedCard
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.TableChart
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
    const val TXN_SETUP = "ais_txn_setup"
    const val MASTERS = "ais_masters"
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

            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolTile(
                        Icons.Default.Bolt,
                        "Macros",
                        "One-tap key sequences",
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                        Modifier.weight(1f),
                    ) { onOpen(AisRoutes.MACROS) }
                    ToolTile(
                        Icons.Default.QrCodeScanner,
                        "Scanner",
                        "Templates, defaults, devices",
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        Modifier.weight(1f),
                    ) { onOpen(AisRoutes.SCANNER) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolTile(
                        Icons.Default.Description,
                        "Session logs",
                        "Record, view, export",
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        Modifier.weight(1f),
                    ) { onOpen(AisRoutes.LOGS) }
                    ToolTile(
                        Icons.Default.AdminPanelSettings,
                        "Admin & kiosk",
                        "PIN, operator mode",
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer,
                        Modifier.weight(1f),
                    ) { onOpen(AisRoutes.ADMIN) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolTile(
                        Icons.Default.Inventory,
                        "Transactions",
                        "Menus, templates, keys",
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        Modifier.weight(1f),
                    ) { onOpen(AisRoutes.TXN_SETUP) }
                    ToolTile(
                        Icons.Default.TableChart,
                        "Master data",
                        "Import Excel / CSV",
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                        Modifier.weight(1f),
                    ) { onOpen(AisRoutes.MASTERS) }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("AIS_Terminal", style = MaterialTheme.typography.titleMedium)
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
private fun ToolTile(
    icon: ImageVector,
    title: String,
    summary: String,
    container: Color,
    onContainer: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.height(150.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(container, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = onContainer)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp))
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
