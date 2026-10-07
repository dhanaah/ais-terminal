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

package org.connectbot.ais.admin

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

private object PinAttempts {
    var failures = 0
    var blockedUntil = 0L
}

/** Dialog asking for the admin PIN. Calls [onUnlocked] once the PIN is correct. */
@Composable
fun AdminPinDialog(
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit,
    title: String = "Admin PIN required",
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val now = SystemClock.elapsedRealtime()
        if (now < PinAttempts.blockedUntil) {
            error = "Too many attempts. Wait ${(PinAttempts.blockedUntil - now) / 1000 + 1} s."
            return
        }
        if (AdminLock.tryUnlock(context, pin)) {
            PinAttempts.failures = 0
            onUnlocked()
        } else {
            PinAttempts.failures++
            pin = ""
            if (PinAttempts.failures >= 5) {
                PinAttempts.blockedUntil = now + 30_000L
                PinAttempts.failures = 0
                error = "Too many attempts. Locked for 30 s."
            } else {
                error = "Wrong PIN"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "This action is restricted to administrators.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(12) },
                    label = { Text("PIN") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = pin.length >= 4) { Text("Unlock") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Wraps an admin-only screen. Operators see the PIN dialog; cancelling calls [onCancel]
 * (normally "navigate back").
 */
@Composable
fun AdminGate(onCancel: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val revision by AdminLock.changes.collectAsState()
    val admin = remember(revision) { AdminLock.isAdmin(context) }
    if (admin) {
        LaunchedEffect(Unit) {
            if (AdminLock.isPinSet(context)) AdminLock.touch(context)
        }
        content()
    } else {
        AdminPinDialog(onDismiss = onCancel, onUnlocked = {})
    }
}

/** Runs actions only for admins, asking for the PIN first when locked. */
class AdminGuard internal constructor(private val context: Context) {
    private var pending by mutableStateOf<(() -> Unit)?>(null)

    fun run(action: () -> Unit) {
        if (AdminLock.isAdmin(context)) {
            if (AdminLock.isPinSet(context)) AdminLock.touch(context)
            action()
        } else {
            pending = action
        }
    }

    /** Must be placed once in the composition that uses this guard. */
    @Composable
    fun PromptHost() {
        val action = pending
        if (action != null) {
            AdminPinDialog(
                onDismiss = { pending = null },
                onUnlocked = {
                    pending = null
                    action()
                },
            )
        }
    }
}

@Composable
fun rememberAdminGuard(): AdminGuard {
    val context = LocalContext.current
    return remember(context) { AdminGuard(context) }
}
