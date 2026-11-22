/*
 * Copyright 2026 LockRoot contributors
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

package com.lockroot.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.lockroot.app.autorun.AutorunManager
import com.lockroot.app.detection.Detection
import com.lockroot.app.exploit.ExploitResult
import com.lockroot.app.exploit.ExploitRunner
import com.lockroot.app.logging.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Boot receiver for Autorun (re-root after reboot).
 *
 * Declared `enabled="false"` + `directBootAware` in the manifest; the
 * user enables it from the app behind the Expert Mode gate.
 *
 * Sequence (idea from the DirtyFrag fork, described in
 * study-parts/a-dirtyfrag.md §6.2):
 * 1. Guard: if the one-shot marker already exists this boot, do
 *    nothing (the hook cannot fire twice in one boot).
 * 2. Gate: Expert Mode off -> skip and log it.
 * 3. TODO: mirror `auto_soft_reboot` into `soft_reboot` before the run.
 * 4. Take a partial wake lock, run the exploit chain on a background
 *    thread, release the lock in `finally`.
 * 5. On failure (any non-zero result or exception): log it and trip
 *    the interlock — [AutorunManager.disableAfterFailure] switches
 *    Autorun off. The user re-enables it by hand.
 *
 * TODO: the receiver runs before unlock, so it must use only
 * device-protected storage (LogManager already does; Settings still
 * needs the device-protected DataStore — see Settings.kt).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_BOOT_COMPLETED
        ) {
            return
        }
        val appContext = context.applicationContext
        val log = LogManager(appContext)
        log.append("boot: receiver called (${intent.action})")

        if (Detection.isHookMarkerPresent()) {
            log.append("boot: one-shot marker already present — skipping autorun")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val settings = com.lockroot.app.settings.Settings(appContext)
            val expert = try {
                settings.expertMode.first()
            } catch (_: Exception) {
                false
            }
            if (!expert) {
                log.append("boot: expert mode off — autorun skipped")
                return@launch
            }

            // TODO: mirror auto_soft_reboot -> soft_reboot here
            // (a-dirtyfrag.md §6.2 step 4).

            var wakeLock: PowerManager.WakeLock? = null
            try {
                val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "LockRoot:boot"
                ).apply {
                    acquire(WAKE_LOCK_TIMEOUT_MS)
                }
                val result = ExploitRunner().run(appContext, log)
                log.append("boot: exploit result=$result")
                if (result !is ExploitResult.Success) {
                    log.append("boot: failed — disabling autorun interlock")
                    AutorunManager.disableAfterFailure(appContext)
                }
            } catch (e: Exception) {
                log.append("boot: exception — ${e.message}; disabling autorun interlock")
                AutorunManager.disableAfterFailure(appContext)
            } finally {
                try {
                    wakeLock?.release()
                } catch (_: Exception) {
                    // Wake lock already released or never acquired.
                }
            }
        }
    }

    companion object {
        /** Wake lock timeout: 2 minutes, safety net for the boot run. */
        const val WAKE_LOCK_TIMEOUT_MS = 2 * 60 * 1000L
    }
}
