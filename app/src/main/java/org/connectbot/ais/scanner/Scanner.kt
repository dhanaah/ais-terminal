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

package org.connectbot.ais.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.connectbot.ais.AisPrefs
import org.connectbot.service.TerminalBridge
import timber.log.Timber

/** Broadcast settings for common rugged scanners (intent output / "broadcast" mode). */
enum class ScannerPreset(val label: String, val action: String, val extras: String, val note: String) {
    GENERIC(
        "Generic / custom",
        AisPrefs.DEFAULT_SCAN_ACTION,
        AisPrefs.DEFAULT_SCAN_EXTRAS,
        "Set your scanner's broadcast action to the value below.",
    ),
    ZEBRA(
        "Zebra (DataWedge)",
        AisPrefs.DEFAULT_SCAN_ACTION,
        "com.symbol.datawedge.data_string",
        "DataWedge profile → Intent output ON, delivery = Broadcast, action as below. Turn Keystroke output OFF.",
    ),
    HONEYWELL(
        "Honeywell",
        "com.honeywell.decode.intent.action.EDIT_DATA",
        "data",
        "Scanner settings → Data processing → Wedge method = Intent (broadcast). Check action on the device.",
    ),
    UROVO(
        "Urovo",
        "android.intent.ACTION_DECODE_DATA",
        "barcode_string",
        "Scanner settings → Output mode = Intent output.",
    ),
    NEWLAND(
        "Newland",
        "nlscan.action.SCANNER_RESULT",
        "SCAN_BARCODE1",
        "Scan settings → Output mode = Broadcast.",
    ),
    IDATA(
        "iData",
        "android.intent.action.SCANRESULT",
        "value",
        "iScan → Output mode = Broadcast output.",
    ),
    SUNMI(
        "Sunmi",
        "com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED",
        "data",
        "Scanner settings → Output via broadcast ON.",
    ),
    CHAINWAY(
        "Chainway",
        "com.scanner.broadcast",
        "data",
        "Keyboard emulator → Output mode = Broadcast.",
    ),
}

object ScanFormatter {
    fun format(prefs: AisPrefs, raw: String): String {
        val data = if (prefs.scannerTrim) raw.trim { it == '\r' || it == '\n' || it == ' ' || it == '\t' } else raw
        val suffix = when (prefs.scannerSuffix) {
            "TAB" -> "\t"
            "CRLF" -> "\r\n"
            "NONE" -> ""
            else -> "\r"
        }
        return prefs.scannerPrefix + data + suffix
    }

    fun extract(intent: Intent, keys: String): String? {
        for (key in keys.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
            val value = intent.getStringExtra(key) ?: intent.getCharSequenceExtra(key)?.toString()
            if (!value.isNullOrEmpty()) return value
        }
        return null
    }
}

/** Sends a scan into the session the same way the keyboard would. */
fun injectScan(context: Context, bridge: TerminalBridge, raw: String) {
    val prefs = AisPrefs(context)
    bridge.injectString(ScanFormatter.format(prefs, raw))
    if (prefs.scannerVibrate) bridge.tryKeyVibrate()
}

/**
 * Listens for hardware-scanner broadcasts while the console is visible and types the
 * barcode into [bridge]. Keyboard-wedge scanners need no setup; they already type into
 * the terminal.
 */
@Composable
fun ScannerBroadcastEffect(bridge: TerminalBridge?) {
    val context = LocalContext.current
    val currentBridge by rememberUpdatedState(bridge)
    DisposableEffect(context) {
        val prefs = AisPrefs(context)
        if (!prefs.scannerBroadcastEnabled || prefs.scannerAction.isBlank()) {
            return@DisposableEffect onDispose { }
        }
        val action = prefs.scannerAction
        val keys = prefs.scannerExtraKeys
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val target = currentBridge ?: return
                val data = ScanFormatter.extract(intent, keys) ?: return
                injectScan(ctx, target, data)
            }
        }
        try {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(action),
                ContextCompat.RECEIVER_EXPORTED,
            )
        } catch (e: Exception) {
            Timber.w(e, "Unable to register scanner receiver")
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Timber.w(e, "Scanner receiver already unregistered")
            }
        }
    }
}

/** Returns a function that opens the camera scanner and delivers the text to [onResult]. */
@Composable
fun rememberCameraScanner(onResult: (String) -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { latest(it) }
    }
    return {
        launcher.launch(
            ScanOptions().apply {
                setPrompt("Scan a barcode or QR code")
                setBeepEnabled(true)
                setOrientationLocked(false)
            },
        )
    }
}
