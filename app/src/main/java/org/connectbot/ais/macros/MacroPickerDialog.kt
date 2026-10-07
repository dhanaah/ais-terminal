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

package org.connectbot.ais.macros

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Lists the macros available for the current host; tapping one sends it. */
@Composable
fun MacroPickerDialog(
    hostNickname: String?,
    onDismiss: () -> Unit,
    onSend: (Macro) -> Unit,
) {
    val context = LocalContext.current
    val macros = remember(hostNickname) { MacroStore(context).forHost(hostNickname) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Macros") },
        text = {
            if (macros.isEmpty()) {
                Text("No macros yet. Add them in AIS Tools → Macros.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(macros, key = { it.id }) { macro ->
                        ListItem(
                            headlineContent = { Text(macro.name) },
                            supportingContent = {
                                Text(macro.sequence, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            modifier = Modifier.clickable {
                                onSend(macro)
                                onDismiss()
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
