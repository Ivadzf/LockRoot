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

package com.lockroot.app.autorun

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.lockroot.app.BootReceiver

/**
 * Autorun on boot: re-root automatically after a reboot, because
 * temporary root disappears on reboot.
 *
 * Design from the DirtyFrag fork, described in
 * study-parts/a-dirtyfrag.md §6 (the fork's design, not the upstream's
 * simpler receiver — the upstream has no Expert gate and no interlock):
 *
 * - "Autorun on?" is NOT a boolean preference — it is the **component
 *   state** of [BootReceiver] in the PackageManager. The manifest
 *   declares it `enabled="false"`; the switch in the app turns the
 *   component on/off via `setComponentEnabledSetting`.
 * - Gate: Expert Mode. Without it, the Autorun switches stay disabled
 *   and their titles gain "(Expert)".
 * - Interlock: after **1** failure, the receiver disables its own
 *   component. The user must re-enable Autorun by hand in the app.
 *   There is no failure counter and no backoff — one failure,
 *   switched off. This is what prevents a bad run from bootlooping.
 * - The receiver is `directBootAware` and runs before unlock, so
 *   everything it touches must work with device-protected storage
 *   only.
 * - Soft reboot on boot: the receiver mirrors the `auto_soft_reboot`
 *   pref into `soft_reboot` before the run, because the bootstrap
 *   reads `soft_reboot` from prefs, not from an argument.
 * - The run itself is guarded: if the one-shot marker already exists
 *   this boot, the receiver does nothing.
 *
 * TODO (stub — the state plumbing is real, the run wiring is not):
 * 1. Wire [BootReceiver] to call [com.lockroot.app.exploit.ExploitRunner]
 *    on a background thread with a partial wake lock (done in the
 *    receiver skeleton — verify on device).
 * 2. Record "receiver was never called by the system" when applicable
 *    (the reference design notes a One UI device where the boot log
 *    had no receiver lines at all — a-dirtyfrag.md §6.5).
 * 3. Mirror `auto_soft_reboot` -> `soft_reboot` before the run.
 */
object AutorunManager {

    private fun component(context: Context): ComponentName =
        ComponentName(context, BootReceiver::class.java)

    /** True if the BootReceiver component is currently enabled. */
    fun isEnabled(context: Context): Boolean {
        return try {
            context.packageManager.getComponentEnabledSetting(component(context)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Turns Autorun on/off by enabling/disabling the BootReceiver
     * component. The caller must check Expert Mode first.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        try {
            context.packageManager.setComponentEnabledSetting(
                component(context),
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP,
            )
        } catch (_: Exception) {
            // Component state change failure: surface it in the UI
            // (TODO: report back to the caller instead of swallowing).
        }
    }

    /**
     * The interlock: after 1 failed boot run, disable Autorun.
     * Called by [BootReceiver] on failure. The user re-enables it
     * manually in the app.
     */
    fun disableAfterFailure(context: Context) {
        setEnabled(context, false)
    }
}
