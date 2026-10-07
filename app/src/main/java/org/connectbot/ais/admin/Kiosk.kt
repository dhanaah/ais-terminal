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

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import org.connectbot.ais.AisPrefs
import timber.log.Timber

/**
 * Kiosk mode uses Android screen pinning (lock task). On a normal device Android asks
 * the user to confirm pinning once; on a device-owner / MDM-provisioned scanner the app
 * can be whitelisted for silent lock task mode.
 */
object Kiosk {
    /** Ask for screen pinning once per app start, so a declined prompt is not repeated on every resume. */
    private var requested = false

    fun apply(activity: Activity) {
        val prefs = AisPrefs(activity)
        if (prefs.keepScreenOn) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (prefs.kioskEnabled && AdminLock.isPinSet(activity) && !isPinned(activity) && !requested) {
            requested = true
            try {
                activity.startLockTask()
            } catch (e: Exception) {
                Timber.w(e, "Unable to start lock task")
            }
        }
    }

    fun exit(activity: Activity) {
        try {
            requested = false
            if (isPinned(activity)) activity.stopLockTask()
        } catch (e: Exception) {
            Timber.w(e, "Unable to stop lock task")
        }
    }

    fun isPinned(context: Context): Boolean {
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }
}

fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
