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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.connectbot.ais.admin.AdminLock
import org.connectbot.ui.theme.StatusLive
import java.util.Calendar

/** Gradient brand header used at the top of the home screen. */
@Composable
fun AisHomeHeader(
    hostCount: Int,
    liveCount: Int,
    actions: @Composable RowScope.() -> Unit,
) {
    val context = LocalContext.current
    val revision by AdminLock.changes.collectAsState()
    val pinSet = remember(revision) { AdminLock.isPinSet(context) }
    val admin = remember(revision) { AdminLock.isAdmin(context) }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val gradient = if (dark) {
        Brush.linearGradient(listOf(Color(0xFF0F2A4D), Color(0xFF123B5C), Color(0xFF0B3D3A)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF1F5FAF), Color(0xFF1C77C3), Color(0xFF00897B)))
    }
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(">_", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text("AIS_Terminal", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(greeting, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
            }
            actions()
        }
        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeaderPill(dot = if (liveCount > 0) StatusLive else Color.White.copy(alpha = 0.5f), text = "$liveCount live")
            HeaderPill(dot = null, text = if (hostCount == 1) "1 host" else "$hostCount hosts")
            if (pinSet) {
                HeaderPill(dot = null, text = if (admin) "Admin" else "Operator")
            }
        }
    }
}

@Composable
private fun HeaderPill(dot: Color?, text: String) {
    Row(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.16f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(
                Modifier
                    .padding(end = 6.dp)
                    .size(8.dp)
                    .background(dot, CircleShape),
            )
        }
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}
