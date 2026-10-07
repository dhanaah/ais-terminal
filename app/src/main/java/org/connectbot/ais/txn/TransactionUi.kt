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

package org.connectbot.ais.txn

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.connectbot.ui.theme.StatusError
import org.connectbot.ui.theme.StatusLive

fun transactionIcon(id: String): ImageVector = when (id) {
    "FGWH_RECEIVING" -> Icons.Default.Inventory
    "MOVE_TO_PDI" -> Icons.Default.Verified
    "MOVE_TO_PACKING" -> Icons.Default.Inventory2
    "LOTOUT" -> Icons.Default.LocalShipping
    "ORG_TRANSFER" -> Icons.Default.SwapHoriz
    else -> Icons.Default.PowerSettingsNew
}

@Composable
private fun tileColors(index: Int): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    return when (index % 5) {
        0 -> c.primaryContainer to c.onPrimaryContainer
        1 -> c.secondaryContainer to c.onSecondaryContainer
        2 -> c.tertiaryContainer to c.onTertiaryContainer
        3 -> c.primaryContainer to c.onPrimaryContainer
        else -> c.secondaryContainer to c.onSecondaryContainer
    }
}

/** 2-column grid of the operator menus plus Exit. */
@Composable
fun TransactionTiles(
    configs: List<TransactionConfig>,
    onSelect: (TransactionConfig) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items: List<TransactionConfig?> = configs.filter { it.enabled } + listOf<TransactionConfig?>(null)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { colIndex, cfg ->
                    val index = rowIndex * 2 + colIndex
                    if (cfg == null) {
                        MenuTile(
                            icon = Icons.Default.PowerSettingsNew,
                            title = "Exit",
                            number = index + 1,
                            container = MaterialTheme.colorScheme.errorContainer,
                            onContainer = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                            onClick = onExit,
                        )
                    } else {
                        val (bg, fg) = tileColors(index)
                        MenuTile(
                            icon = transactionIcon(cfg.id),
                            title = cfg.title,
                            number = index + 1,
                            container = bg,
                            onContainer = fg,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(cfg) },
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MenuTile(
    icon: ImageVector,
    title: String,
    number: Int,
    container: Color,
    onContainer: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ElevatedCard(onClick = onClick, shape = MaterialTheme.shapes.large, modifier = modifier.height(104.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(container, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = onContainer)
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text("$number", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Bottom sheet with the operator menus, opened from the console. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionMenuSheet(
    configs: List<TransactionConfig>,
    onDismiss: () -> Unit,
    onSelect: (TransactionConfig) -> Unit,
    onExit: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text("Transactions", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Choose a menu, then scan the QR code",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            TransactionTiles(configs = configs, onSelect = onSelect, onExit = onExit)
        }
    }
}

/**
 * Panel shown over the console while a menu is active: last scan, the values defaulted
 * from the QR and master data, and actions.
 */
@Composable
fun TransactionPanel(
    config: TransactionConfig,
    last: ScanResult?,
    count: Int,
    onScanCamera: () -> Unit,
    onSend: () -> Unit,
    onChange: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(transactionIcon(config.id), null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                ) {
                    Text(config.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (last == null) "Scan the QR code" else "$count sent this session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onScanCamera) { Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan with camera") }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Toggle details")
                }
            }

            if (expanded && last != null) {
                val (statusColor, statusText) = when (last.status) {
                    ScanStatus.SENT -> StatusLive to "Sent to terminal"
                    ScanStatus.PENDING_CONFIRM -> MaterialTheme.colorScheme.tertiary to "Check and tap Send"
                    ScanStatus.NOT_IN_MASTER -> StatusError to last.message
                    ScanStatus.ERROR -> StatusError to last.message
                    ScanStatus.READY -> MaterialTheme.colorScheme.primary to "Ready"
                }
                Row(
                    Modifier
                        .padding(top = 10.dp)
                        .background(statusColor.copy(alpha = 0.15f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (last.status == ScanStatus.SENT) Icons.Default.CheckCircle else Icons.Default.Error,
                        null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(statusText, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
                }
                Text(
                    last.raw,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    Modifier
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    last.values.filter { it.second.isNotBlank() }.forEach { (k, v) ->
                        Column(
                            Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.small)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(k.substringAfter('.'), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(v, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        }
                    }
                }
            }

            if (expanded) {
                Row(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (last?.status == ScanStatus.PENDING_CONFIRM) {
                        Button(onClick = onSend) { Text("Send") }
                    }
                    FilledTonalButton(onClick = onChange) { Text("Menu") }
                    OutlinedButton(onClick = onExit) { Text("Exit", textAlign = TextAlign.Center) }
                }
            }
        }
    }
}
