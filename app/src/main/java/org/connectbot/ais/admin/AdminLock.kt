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
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.connectbot.ais.AisPrefs
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Two roles: Operator (default, can only connect to saved hosts, run macros and scan)
 * and Admin (unlocked with a PIN, can edit hosts, keys, settings, logs).
 *
 * When no PIN is configured the lock is disabled and everyone is treated as Admin.
 * Unlock state lives in memory only and expires after the configured idle time.
 */
object AdminLock {
    private const val ITERATIONS = 20_000
    private const val KEY_BITS = 256

    private val unlockedUntil = MutableStateFlow(0L)

    /** Emits a new value whenever the lock state may have changed. */
    private val revision = MutableStateFlow(0)
    val changes: StateFlow<Int> = revision.asStateFlow()

    fun isPinSet(context: Context): Boolean = AisPrefs(context).adminPinHash.isNotEmpty()

    /** True when admin features are available right now. */
    fun isAdmin(context: Context): Boolean {
        if (!isPinSet(context)) return true
        val until = unlockedUntil.value
        if (until == 0L) return false
        if (SystemClock.elapsedRealtime() > until) {
            lock()
            return false
        }
        return true
    }

    fun tryUnlock(context: Context, pin: String): Boolean {
        val prefs = AisPrefs(context)
        if (prefs.adminPinHash.isEmpty()) return true
        val ok = try {
            val salt = Base64.decode(prefs.adminPinSalt, Base64.NO_WRAP)
            MessageDigest.isEqual(hash(pin, salt), Base64.decode(prefs.adminPinHash, Base64.NO_WRAP))
        } catch (e: IllegalArgumentException) {
            false
        }
        if (ok) touch(context)
        return ok
    }

    /** Extend the admin session while the admin is actively working. */
    fun touch(context: Context) {
        val minutes = AisPrefs(context).adminRelockMinutes
        unlockedUntil.value = SystemClock.elapsedRealtime() + minutes * 60_000L
        revision.value = revision.value + 1
    }

    fun lock() {
        unlockedUntil.value = 0L
        revision.value = revision.value + 1
    }

    /** Sets or replaces the PIN. Passing an empty PIN removes the lock. */
    fun setPin(context: Context, pin: String) {
        val prefs = AisPrefs(context)
        if (pin.isEmpty()) {
            prefs.adminPinHash = ""
            prefs.adminPinSalt = ""
            prefs.kioskEnabled = false
        } else {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            prefs.adminPinSalt = Base64.encodeToString(salt, Base64.NO_WRAP)
            prefs.adminPinHash = Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP)
            touch(context)
        }
        revision.value = revision.value + 1
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            // HmacSHA1 variant: available on every supported API level (minSdk 24).
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
