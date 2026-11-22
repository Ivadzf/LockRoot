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

package com.lockroot.app.detection

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * Read-only detection, run **before** any exploit attempt.
 *
 * Nothing here touches the kernel exploit surface: it only reads public
 * device properties and the list of installed packages. The screen must
 * show "vulnerable / patched / no profile" before any attempt
 * (plano-v1.md item 2).
 *
 * Ideas from DFRoot/DirtyFrag, described in study-parts/a-dirtyfrag.md
 * §2–§3, reimplemented from scratch:
 * - The kernel release comes from `uname -r` (here: the `os.version`
 *   system property, same string); the `androidNN` token in it is the
 *   GKI level and the key into the module table.
 * - If the token is missing (some vendor kernels omit it), fall back to
 *   the vendor build release property first, never the system one —
 *   the system release is the marketing Android version, not the GKI
 *   level (a-dirtyfrag.md §3.2).
 * - The module is chosen by exact (major, minor) kernel match; between
 *   rows with the same pair, the exact KMI wins, otherwise the nearest
 *   KMI **below** is taken and the guess is logged (a-dirtyfrag.md §3.2).
 * - A manager is usable only if it is installed **and** ships
 *   `libksud.so` in its native library dir; the app never embeds its
 *   own `ksud` (a-dirtyfrag.md §1.1 step 4).
 *
 * TODO (a-dirtyfrag.md §10):
 * - Confirm the exact list of manager package names to look for
 *   (KernelSU, KernelSU-Next, SukiSU, ReSukiSU, OriginSU, BakaSU,
 *   SUSFS). The current code does NOT hard-code package names: it
 *   scans every installed package for `libksud.so`, which is the
 *   behaviour that matters. A VERIFICAR: whether any manager ships
 *   the library under a different name or path.
 * - Extract the 8 exact vermagic strings from the reference `.ko`
 *   files in a controlled environment; do NOT copy them from the
 *   study table without checking (a-dirtyfrag.md §3.1).
 * - The live probe (write 16 test bytes, read them back) is NOT here:
 *   it belongs to the exploit chain (a-dirtyfrag.md §2) and is a
 *   TODO in [com.lockroot.app.exploit.DirtyFragExploit].
 */
object Detection {

    /** Result of the read-only detection pass. */
    data class DeviceInfo(
        val manufacturer: String,
        val model: String,
        val androidVersion: String,
        val securityPatch: String,
        /** Full `uname -r` string, e.g. `5.15.194-android13-8-...`. */
        val kernelRelease: String,
        /** GKI level parsed from the release, e.g. 13, or null. */
        val gkiLevel: Int?,
        /** Kernel major.minor, e.g. Pair(5, 15), or null. */
        val kernelMajorMinor: Pair<Int, Int>?,
        /** The module file the table selects for this device, or null. */
        val selectedModule: String?,
        /** True if the selection was a nearest-below guess, not exact. */
        val moduleIsGuess: Boolean,
        /** Installed managers that ship libksud.so (package names). */
        val managersWithKsud: List<String>,
    )

    /**
     * The 8 module branches (KMI -> kernel), from a-dirtyfrag.md §3.1.
     * These are file names only — no binary is embedded in this repo;
     * the modules must be built by CI from source (DDK) when the
     * DirtyFrag route is implemented (TODO.md item 2.3).
     */
    val MODULE_TABLE: List<ModuleRow> = listOf(
        ModuleRow(gkiLevel = 12, major = 5, minor = 10, file = "dfroot-android12-5.10.ko"),
        ModuleRow(gkiLevel = 13, major = 5, minor = 10, file = "dfroot-android13-5.10.ko"),
        ModuleRow(gkiLevel = 13, major = 5, minor = 15, file = "dfroot-android13-5.15.ko"),
        ModuleRow(gkiLevel = 14, major = 5, minor = 15, file = "dfroot-android14-5.15.ko"),
        ModuleRow(gkiLevel = 14, major = 6, minor = 1, file = "dfroot-android14-6.1.ko"),
        ModuleRow(gkiLevel = 15, major = 6, minor = 6, file = "dfroot-android15-6.6.ko"),
        ModuleRow(gkiLevel = 16, major = 6, minor = 12, file = "dfroot-android16-6.12.ko"),
        ModuleRow(gkiLevel = 17, major = 6, minor = 18, file = "dfroot-android17-6.18.ko"),
    )

    data class ModuleRow(val gkiLevel: Int, val major: Int, val minor: Int, val file: String)

    /** Runs the read-only detection pass. No exploit is attempted. */
    fun detect(context: Context): DeviceInfo {
        val release = kernelRelease()
        val gki = parseGkiLevel(release) ?: parseGkiLevelFromVendorProps()
        val majorMinor = parseMajorMinor(release)
        val (module, isGuess) = selectModule(gki, majorMinor)
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            kernelRelease = release,
            gkiLevel = gki,
            kernelMajorMinor = majorMinor,
            selectedModule = module,
            moduleIsGuess = isGuess,
            managersWithKsud = findManagersWithKsud(context),
        )
    }

    /** `uname -r` as seen from userspace (no shell involved). */
    fun kernelRelease(): String = System.getProperty("os.version").orEmpty()

    /** Extracts the `androidNN` token from a kernel release string. */
    fun parseGkiLevel(release: String): Int? {
        val match = Regex("android(\\d+)").find(release) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    /** Parses the leading `major.minor` from a kernel release string. */
    fun parseMajorMinor(release: String): Pair<Int, Int>? {
        val match = Regex("^(\\d+)\\.(\\d+)").find(release) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        return major to minor
    }

    /**
     * Fallback when `uname -r` has no `androidNN` token: prefer the
     * vendor build release, never the system (marketing) one.
     * A VERIFICAR: the exact property names are taken from the study
     * (a-dirtyfrag.md §3.2); confirm on a device whose kernel omits
     * the token.
     */
    fun parseGkiLevelFromVendorProps(): Int? {
        // TODO: read ro.vendor.build.version.release (preferred) and
        // ro.build.version.release via android.os.SystemProperties
        // (hidden API — use reflection only if no public alternative
        // exists; do not shell out to `getprop`).
        return null
    }

    /**
     * Selects the module for (gkiLevel, major.minor).
     * Exact (major, minor) is a hard filter. Among rows with the same
     * pair, the exact KMI wins; otherwise the nearest KMI below is
     * taken and flagged as a guess (never guess upwards).
     * Returns (file, isGuess), or (null, false) if no row matches.
     */
    fun selectModule(
        gkiLevel: Int?,
        majorMinor: Pair<Int, Int>?,
    ): Pair<String?, Boolean> {
        if (gkiLevel == null || majorMinor == null) return null to false
        val (major, minor) = majorMinor
        val rows = MODULE_TABLE.filter { it.major == major && it.minor == minor }
        if (rows.isEmpty()) return null to false
        val exact = rows.firstOrNull { it.gkiLevel == gkiLevel }
        if (exact != null) return exact.file to false
        val below = rows.filter { it.gkiLevel < gkiLevel }.maxByOrNull { it.gkiLevel }
            ?: return null to false
        return below.file to true
    }

    /**
     * Finds installed packages that ship `libksud.so` in their native
     * library directory. Requires QUERY_ALL_PACKAGES.
     * Returns package names; empty means "no usable manager installed".
     */
    fun findManagersWithKsud(context: Context): List<String> {
        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val packages = pm.getInstalledPackages(PackageManager.GET_SHARED_LIBRARY_FILES)
            packages.mapNotNull { pkg ->
                val libDir = pkg.applicationInfo?.nativeLibraryDir ?: return@mapNotNull null
                if (File(libDir, "libksud.so").exists()) pkg.packageName else null
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Whether the one-shot marker for this boot already exists.
     * Read-only check: if the marker exists, the hook already fired
     * this boot and a second run cannot work — the app must refuse
     * and tell the user to reboot first (a-dirtyfrag.md §1.1 step 1,
     * §6.2 step 2). The marker path itself is a TODO to confirm
     * against the implementation (A VERIFICAR).
     */
    fun isHookMarkerPresent(): Boolean {
        // TODO: confirm the marker path (reference design uses /dev/df).
        return File("/dev/df").exists()
    }

    /** Reads /proc/version for the log banner (read-only). */
    fun procVersion(): String {
        return try {
            File("/proc/version").readText().trim()
        } catch (_: Exception) {
            ""
        }
    }
}
