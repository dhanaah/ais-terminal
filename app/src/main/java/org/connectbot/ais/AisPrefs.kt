/*
 * AIS Terminal — additions to ConnectBot
 * Developed by DT (AIS Glass, Supply Chain Planning)
 *
 * Based on ConnectBot, Copyright Kenny Root and contributors.
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

package org.connectbot.ais

import android.content.Context
import android.content.SharedPreferences

/**
 * Settings for all AIS Terminal additions. Kept in a separate preferences file so
 * upstream ConnectBot settings, backups and migrations are untouched.
 */
class AisPrefs(context: Context) {
    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- Barcode scanner ----
    var scannerBroadcastEnabled: Boolean
        get() = prefs.getBoolean(K_SCAN_BCAST, true)
        set(v) = prefs.edit().putBoolean(K_SCAN_BCAST, v).apply()

    var scannerPreset: String
        get() = prefs.getString(K_SCAN_PRESET, "GENERIC") ?: "GENERIC"
        set(v) = prefs.edit().putString(K_SCAN_PRESET, v).apply()

    var scannerAction: String
        get() = prefs.getString(K_SCAN_ACTION, DEFAULT_SCAN_ACTION) ?: DEFAULT_SCAN_ACTION
        set(v) = prefs.edit().putString(K_SCAN_ACTION, v.trim()).apply()

    /** Comma separated intent extra keys; the first one present wins. */
    var scannerExtraKeys: String
        get() = prefs.getString(K_SCAN_EXTRA, DEFAULT_SCAN_EXTRAS) ?: DEFAULT_SCAN_EXTRAS
        set(v) = prefs.edit().putString(K_SCAN_EXTRA, v).apply()

    var scannerPrefix: String
        get() = prefs.getString(K_SCAN_PREFIX, "") ?: ""
        set(v) = prefs.edit().putString(K_SCAN_PREFIX, v).apply()

    /** One of NONE, ENTER, TAB, CRLF. */
    var scannerSuffix: String
        get() = prefs.getString(K_SCAN_SUFFIX, "ENTER") ?: "ENTER"
        set(v) = prefs.edit().putString(K_SCAN_SUFFIX, v).apply()

    var scannerTrim: Boolean
        get() = prefs.getBoolean(K_SCAN_TRIM, true)
        set(v) = prefs.edit().putBoolean(K_SCAN_TRIM, v).apply()

    var scannerShowCameraButton: Boolean
        get() = prefs.getBoolean(K_SCAN_CAMERA, true)
        set(v) = prefs.edit().putBoolean(K_SCAN_CAMERA, v).apply()

    var scannerVibrate: Boolean
        get() = prefs.getBoolean(K_SCAN_VIBRATE, true)
        set(v) = prefs.edit().putBoolean(K_SCAN_VIBRATE, v).apply()

    /** Default scan template (see ScanTemplates); empty = migrate from prefix/suffix. */
    var scanTemplate: String
        get() = prefs.getString(K_SCAN_TEMPLATE, "") ?: ""
        set(v) = prefs.edit().putString(K_SCAN_TEMPLATE, v).apply()

    var scanDelimiter: String
        get() = prefs.getString(K_SCAN_DELIM, "PIPE") ?: "PIPE"
        set(v) = prefs.edit().putString(K_SCAN_DELIM, v).apply()

    var scanHostProfilesJson: String
        get() = prefs.getString(K_SCAN_HOSTS, "") ?: ""
        set(v) = prefs.edit().putString(K_SCAN_HOSTS, v).apply()

    // ---- Macros ----
    var macrosJson: String
        get() = prefs.getString(K_MACROS, "") ?: ""
        set(v) = prefs.edit().putString(K_MACROS, v).apply()

    var showMacroButton: Boolean
        get() = prefs.getBoolean(K_MACRO_BTN, true)
        set(v) = prefs.edit().putBoolean(K_MACRO_BTN, v).apply()

    // ---- Session logging ----
    var loggingEnabled: Boolean
        get() = prefs.getBoolean(K_LOG_ON, false)
        set(v) = prefs.edit().putBoolean(K_LOG_ON, v).apply()

    var logTimestamps: Boolean
        get() = prefs.getBoolean(K_LOG_TS, true)
        set(v) = prefs.edit().putBoolean(K_LOG_TS, v).apply()

    var logInput: Boolean
        get() = prefs.getBoolean(K_LOG_INPUT, false)
        set(v) = prefs.edit().putBoolean(K_LOG_INPUT, v).apply()

    var logRetentionDays: Int
        get() = prefs.getInt(K_LOG_DAYS, 30)
        set(v) = prefs.edit().putInt(K_LOG_DAYS, v.coerceIn(1, 3650)).apply()

    // ---- Admin lock / kiosk ----
    var adminPinHash: String
        get() = prefs.getString(K_PIN_HASH, "") ?: ""
        set(v) = prefs.edit().putString(K_PIN_HASH, v).apply()

    var adminPinSalt: String
        get() = prefs.getString(K_PIN_SALT, "") ?: ""
        set(v) = prefs.edit().putString(K_PIN_SALT, v).apply()

    var adminRelockMinutes: Int
        get() = prefs.getInt(K_RELOCK, 5)
        set(v) = prefs.edit().putInt(K_RELOCK, v.coerceIn(1, 240)).apply()

    var kioskEnabled: Boolean
        get() = prefs.getBoolean(K_KIOSK, false)
        set(v) = prefs.edit().putBoolean(K_KIOSK, v).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(K_SCREEN_ON, false)
        set(v) = prefs.edit().putBoolean(K_SCREEN_ON, v).apply()

    companion object {
        const val FILE = "ais_terminal"
        const val DEFAULT_SCAN_ACTION = "com.aisglass.terminal.SCAN"
        const val DEFAULT_SCAN_EXTRAS =
            "com.symbol.datawedge.data_string,data,barcode_string,SCAN_BARCODE1,value,barcodeData,scannerdata,barcode"

        private const val K_SCAN_BCAST = "scan_broadcast"
        private const val K_SCAN_PRESET = "scan_preset"
        private const val K_SCAN_ACTION = "scan_action"
        private const val K_SCAN_EXTRA = "scan_extra"
        private const val K_SCAN_PREFIX = "scan_prefix"
        private const val K_SCAN_SUFFIX = "scan_suffix"
        private const val K_SCAN_TRIM = "scan_trim"
        private const val K_SCAN_CAMERA = "scan_camera"
        private const val K_SCAN_VIBRATE = "scan_vibrate"
        private const val K_SCAN_TEMPLATE = "scan_template"
        private const val K_SCAN_DELIM = "scan_delimiter"
        private const val K_SCAN_HOSTS = "scan_host_profiles"
        private const val K_MACROS = "macros_json"
        private const val K_MACRO_BTN = "macro_button"
        private const val K_LOG_ON = "log_enabled"
        private const val K_LOG_TS = "log_timestamps"
        private const val K_LOG_INPUT = "log_input"
        private const val K_LOG_DAYS = "log_retention_days"
        private const val K_PIN_HASH = "admin_pin_hash"
        private const val K_PIN_SALT = "admin_pin_salt"
        private const val K_RELOCK = "admin_relock_minutes"
        private const val K_KIOSK = "kiosk_enabled"
        private const val K_SCREEN_ON = "keep_screen_on"
    }
}
