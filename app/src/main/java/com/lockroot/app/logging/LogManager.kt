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

package com.lockroot.app.logging

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Detailed per-step log, written to a file.
 *
 * This is the one module implemented for real in the skeleton: logging
 * is not an exploit, so a minimal real implementation is allowed here.
 *
 * Design (ideas from DFRoot/DirtyFrag, described in
 * study-parts/a-dirtyfrag.md §7):
 * - Plain text, one line per event, each line timestamped (the reference
 *   design has no timestamps; the LockRoot adds them because diagnosing
 *   the Davi runs would have been easier with them).
 * - Starts with a device banner (app version, manufacturer, model,
 *   Android version, security patch, kernel version) so a pasted log
 *   can be triaged without asking anything.
 * - Stored in device-protected storage (`/data/user_de/0/<pkg>/files/`),
 *   so the boot receiver can write it before unlock.
 * - Invalidated by boot count: a log from a previous boot is stale,
 *   because the root of that boot is already dead; on open, if the
 *   saved boot count differs from the current one, the file is deleted
 *   and the last-run success flag is reset.
 * - Rotation by size: when the file grows past [MAX_BYTES], only the
 *   last [MAX_BYTES] are kept (plus a "log trimmed" marker line).
 *   The reference design rewrites the whole file on every append and
 *   has no size rotation; the LockRoot keeps the TemporaryRoot-style
 *   trim instead.
 * - Export to Downloads via MediaStore (`Download/LockRoot/`).
 *
 * TODO: wire the boot-count invalidation to [Settings] (`logBootCount`
 * / `lastRunSuccess`) — the plumbing is here, the call sites are not
 * yet written.
 */
class LogManager(private val context: Context) {

    /** Device-protected files dir, so the log works before unlock. */
    private val filesDir: File
        get() = context.createDeviceProtectedStorageContext().filesDir

    private val logFile: File
        get() = File(filesDir, LOG_FILE_NAME)

    private val lock = Any()

    /** Current boot count, or -1 if it cannot be read. */
    fun currentBootCount(): Int {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.BOOT_COUNT,
                -1
            )
        } catch (_: Exception) {
            -1
        }
    }

    /**
     * Deletes the log if it belongs to a previous boot.
     *
     * @param savedBootCount the boot count stored when the log was last
     * written (see [com.lockroot.app.settings.Settings.logBootCount]).
     * @return true if the log was deleted as stale.
     */
    fun invalidateIfStale(savedBootCount: Int): Boolean {
        val current = currentBootCount()
        if (current == -1 || savedBootCount == -1) return false
        if (savedBootCount == current) return false
        synchronized(lock) {
            if (logFile.exists()) logFile.delete()
        }
        append("log: previous boot log discarded (boot $savedBootCount -> $current)")
        return true
    }

    /** Appends one timestamped line to the log file. */
    fun append(line: String) {
        val stamped = "${timestamp()} $line"
        synchronized(lock) {
            try {
                filesDir.mkdirs()
                logFile.appendText(stamped + "\n")
                rotateIfNeeded()
            } catch (_: Exception) {
                // Logging must never crash the app; a failed write is
                // silently dropped (there is nowhere else to report it).
            }
        }
    }

    /** Writes the device banner at the start of a run. */
    fun appendBanner(appVersion: String, kernelVersion: String) {
        append("=== LockRoot $appVersion ===")
        append("manufacturer: ${android.os.Build.MANUFACTURER}")
        append("model: ${android.os.Build.MODEL}")
        append("android version: ${android.os.Build.VERSION.RELEASE}")
        append("security patch: ${android.os.Build.VERSION.SECURITY_PATCH}")
        append("kernel version: $kernelVersion")
    }

    /** Whole current log as text (empty string if there is none). */
    fun readAll(): String {
        synchronized(lock) {
            return try {
                if (logFile.exists()) logFile.readText() else ""
            } catch (_: Exception) {
                ""
            }
        }
    }

    /**
     * Exports the log to `Download/LockRoot/lockroot-log-<date>.txt`
     * via MediaStore. Returns true on success.
     */
    fun exportToDownloads(): Boolean {
        val content = readAll()
        if (content.isEmpty()) return false
        return try {
            val date = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "lockroot-log-$date.txt")
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/LockRoot"
                )
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                ?: return false
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Keeps only the tail of the file once it passes [MAX_BYTES]. */
    private fun rotateIfNeeded() {
        try {
            if (!logFile.exists() || logFile.length() <= MAX_BYTES) return
            val bytes = logFile.readBytes()
            val tail = bytes.takeLast(MAX_BYTES).toByteArray()
            logFile.writeBytes(tail)
            // The trim marker goes at the front on the next append;
            // note it here so the truncation is visible in the file.
            logFile.appendText("$TRIM_MARKER\n")
        } catch (_: Exception) {
            // Rotation failure must not break logging.
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    companion object {
        const val LOG_FILE_NAME = "last_run.log"

        /** Max log size before rotation (512 KiB). */
        const val MAX_BYTES = 512 * 1024

        const val TRIM_MARKER = "--- older log trimmed ---"
    }
}
