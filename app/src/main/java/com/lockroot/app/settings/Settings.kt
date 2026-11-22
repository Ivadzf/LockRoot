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

package com.lockroot.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * App settings, stored in DataStore Preferences.
 *
 * Design note (idea from DFRoot/DirtyFrag, described in
 * study-parts/a-dirtyfrag.md §5–§6): the preferences the exploit chain
 * reads must live in **device-protected storage**, because the bootstrap
 * runs before the user unlocks the device. TODO: the DataStore below
 * must be created from a device-protected context
 * (`createDeviceProtectedStorageContext()`) — a plain context stores
 * prefs in credential-protected storage, which the bootstrap cannot
 * read before unlock.
 *
 * Keys (names kept in English, as in the reference design):
 * - [expertMode]: gates the Autorun card. Off by default.
 * - [modulesOff]: "start root with all modules disabled". Off by default.
 *   The bootstrap reads this and creates one `disable` file per module
 *   directory before the manager daemon starts (a-dirtyfrag.md §5).
 * - [softRebootEnabled]: "soft reboot after a successful root".
 *   Off by default; never automatic without this toggle.
 * - [autoSoftReboot]: soft reboot on boot runs (mirrored into
 *   [softRebootEnabled] by the BootReceiver before the run).
 * - [autorunEnabled]: mirror of the BootReceiver component state, kept
 *   for display only — the source of truth is the PackageManager
 *   component state (see [com.lockroot.app.autorun.AutorunManager]).
 * - [lastRunSuccess]: whether the last run in this boot succeeded.
 * - [logBootCount]: boot count at which the current log was written;
 *   used to invalidate a log from a previous boot (a-dirtyfrag.md §7.3).
 */
class Settings(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = PREFS_NAME
    )

    val expertMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_EXPERT_MODE] ?: false }
    val modulesOff: Flow<Boolean> = context.dataStore.data.map { it[KEY_MODULES_OFF] ?: false }
    val softRebootEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SOFT_REBOOT] ?: false }
    val autoSoftReboot: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AUTO_SOFT_REBOOT] ?: false }
    val autorunEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AUTORUN] ?: false }
    val lastRunSuccess: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_LAST_RUN_SUCCESS] ?: false }
    val logBootCount: Flow<Int> = context.dataStore.data.map { it[KEY_LOG_BOOT_COUNT] ?: -1 }

    suspend fun setExpertMode(value: Boolean) = edit { it[KEY_EXPERT_MODE] = value }
    suspend fun setModulesOff(value: Boolean) = edit { it[KEY_MODULES_OFF] = value }
    suspend fun setSoftRebootEnabled(value: Boolean) = edit { it[KEY_SOFT_REBOOT] = value }
    suspend fun setAutoSoftReboot(value: Boolean) = edit { it[KEY_AUTO_SOFT_REBOOT] = value }
    suspend fun setAutorunEnabled(value: Boolean) = edit { it[KEY_AUTORUN] = value }
    suspend fun setLastRunSuccess(value: Boolean) = edit { it[KEY_LAST_RUN_SUCCESS] = value }
    suspend fun setLogBootCount(value: Int) = edit { it[KEY_LOG_BOOT_COUNT] = value }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    companion object {
        const val PREFS_NAME = "lockroot"

        private val KEY_EXPERT_MODE = booleanPreferencesKey("expert_mode")
        private val KEY_MODULES_OFF = booleanPreferencesKey("modules_off")
        private val KEY_SOFT_REBOOT = booleanPreferencesKey("soft_reboot")
        private val KEY_AUTO_SOFT_REBOOT = booleanPreferencesKey("auto_soft_reboot")
        private val KEY_AUTORUN = booleanPreferencesKey("autorun")
        private val KEY_LAST_RUN_SUCCESS = booleanPreferencesKey("last_run_success")
        private val KEY_LOG_BOOT_COUNT = intPreferencesKey("log_boot_count")
    }
}
