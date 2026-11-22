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

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lockroot.app.autorun.AutorunManager
import com.lockroot.app.detection.Detection
import com.lockroot.app.exploit.ExploitResult
import com.lockroot.app.exploit.ExploitRunner
import com.lockroot.app.exploit.RootManager
import com.lockroot.app.exploit.RootState
import com.lockroot.app.logging.LogManager
import com.lockroot.app.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Placeholder UI (Views, no Compose) for the LockRoot v1 skeleton.
 *
 * Everything on this screen is wired to the stubs: tapping "Obter
 * root" runs [ExploitRunner], which today only logs TODOs and returns
 * a refusal — no root is obtained. The screen exists so the wiring
 * (detection -> run -> verify -> log) can be tested end to end once
 * the routes are implemented.
 *
 * Controls:
 * - Detection status text (from [Detection.detect]).
 * - "Obter root" button.
 * - "Iniciar com módulos desligados" switch (default off).
 * - "Soft reboot após o root" switch (default off; never automatic).
 * - "Soft reboot agora" button — disabled until root is verified.
 * - "Autorun (Expert)" switch — disabled unless Expert Mode is on.
 * - "Ver log" (shows the log text) and "Compartilhar log" (exports
 *   to Downloads/LockRoot and opens the share sheet).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var log: LogManager
    private lateinit var settings: Settings

    private lateinit var statusText: TextView
    private lateinit var rootButton: Button
    private lateinit var modulesOffSwitch: Switch
    private lateinit var softRebootSwitch: Switch
    private lateinit var softRebootNowButton: Button
    private lateinit var expertSwitch: Switch
    private lateinit var autorunSwitch: Switch
    private lateinit var autoSoftRebootSwitch: Switch
    private lateinit var viewLogButton: Button
    private lateinit var shareLogButton: Button
    private lateinit var logText: TextView

    private var rooted: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        log = LogManager(applicationContext)
        settings = Settings(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        val scroll = ScrollView(this).apply { addView(root) }

        statusText = TextView(this).apply {
            text = "LockRoot 0.1.0 — esqueleto\nNenhum exploit implementado."
            textSize = 16f
        }
        rootButton = Button(this).apply { text = "Obter root" }
        modulesOffSwitch = Switch(this).apply {
            text = "Iniciar com todos os módulos desligados"
        }
        softRebootSwitch = Switch(this).apply {
            text = "Soft reboot após o root (nunca automático sem isto)"
        }
        softRebootNowButton = Button(this).apply {
            text = "Soft reboot agora"
            isEnabled = false
        }
        expertSwitch = Switch(this).apply { text = "Modo Expert" }
        autorunSwitch = Switch(this).apply {
            text = "Autorun no boot (Expert)"
            isEnabled = false
        }
        autoSoftRebootSwitch = Switch(this).apply {
            text = "Soft reboot no boot (Expert + Autorun)"
            isEnabled = false
        }
        viewLogButton = Button(this).apply { text = "Ver log" }
        shareLogButton = Button(this).apply { text = "Compartilhar log" }
        logText = TextView(this).apply {
            text = ""
            textSize = 13f
        }

        for (view in listOf(
            statusText, rootButton, modulesOffSwitch, softRebootSwitch,
            softRebootNowButton, expertSwitch, autorunSwitch,
            autoSoftRebootSwitch, viewLogButton, shareLogButton, logText
        )) {
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 24
                gravity = Gravity.CENTER_HORIZONTAL
            }
            root.addView(view, params)
        }

        setContentView(scroll)

        wireControls()
        refreshDetection()
    }

    private fun wireControls() {
        rootButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val result = ExploitRunner().run(applicationContext, log)
                withContext(Dispatchers.Main) {
                    when (result) {
                        is ExploitResult.Success -> {
                            rooted = true
                            softRebootNowButton.isEnabled = true
                            statusText.text = "Root obtido via ${result.route}.\n" +
                                "Atenção: SELinux permissivo até o reboot."
                            Toast.makeText(
                                this@MainActivity,
                                "Root obtido: ${result.route}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is ExploitResult.Failed -> {
                            statusText.text = "Falhou (código ${result.code}): ${result.reason}\n" +
                                "Faça reboot total antes de tentar de novo."
                        }
                        is ExploitResult.NotAttempted -> {
                            statusText.text = "Não tentado: ${result.reason}"
                        }
                    }
                }
            }
        }

        modulesOffSwitch.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { settings.setModulesOff(checked) }
            log.append("ui: modules_off=$checked (vale na próxima subida do root)")
        }

        softRebootSwitch.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { settings.setSoftRebootEnabled(checked) }
        }

        softRebootNowButton.setOnClickListener {
            // TODO: ask for confirmation first (new behaviour — the
            // reference design never asks). Only enabled when rooted.
            lifecycleScope.launch(Dispatchers.IO) {
                val ok = RootManager.softRebootNow(applicationContext, log)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        if (ok) "Soft reboot solicitado" else "Soft reboot indisponível (stub)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        expertSwitch.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { settings.setExpertMode(checked) }
            autorunSwitch.isEnabled = checked
            autoSoftRebootSwitch.isEnabled = checked && autorunSwitch.isChecked
            if (!checked) {
                autorunSwitch.isChecked = false
                AutorunManager.setEnabled(applicationContext, false)
            }
        }

        autorunSwitch.setOnCheckedChangeListener { _, checked ->
            AutorunManager.setEnabled(applicationContext, checked)
            lifecycleScope.launch { settings.setAutorunEnabled(checked) }
            autoSoftRebootSwitch.isEnabled = checked
            log.append("ui: autorun=$checked")
        }

        autoSoftRebootSwitch.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { settings.setAutoSoftReboot(checked) }
        }

        viewLogButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val text = log.readAll()
                withContext(Dispatchers.Main) {
                    logText.text = text.ifEmpty { "(log vazio)" }
                }
            }
        }

        shareLogButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val exported = log.exportToDownloads()
                withContext(Dispatchers.Main) {
                    if (!exported) {
                        Toast.makeText(
                            this@MainActivity,
                            "Nada para exportar (log vazio)",
                            Toast.LENGTH_LONG
                        ).show()
                        return@withContext
                    }
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, log.readAll())
                        putExtra(
                            Intent.EXTRA_SUBJECT,
                            "LockRoot log ${android.os.Build.MODEL}"
                        )
                    }
                    startActivity(Intent.createChooser(share, "Compartilhar log"))
                    Toast.makeText(
                        this@MainActivity,
                        "Log salvo em Downloads/LockRoot",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /** Runs the read-only detection pass and updates the status text. */
    private fun refreshDetection() {
        lifecycleScope.launch(Dispatchers.IO) {
            val info = Detection.detect(applicationContext)
            val state = RootManager.currentState(applicationContext, log)
            withContext(Dispatchers.Main) {
                val verdict = when {
                    info.selectedModule == null -> "Sem perfil / kernel sem suporte"
                    info.moduleIsGuess -> "Módulo por palpite (KMI aproximado)"
                    else -> "Módulo encontrado: ${info.selectedModule}"
                }
                statusText.text = "LockRoot 0.1.0 — esqueleto\n" +
                    "Modelo: ${info.model} · kernel: ${info.kernelRelease}\n" +
                    "KMI: android${info.gkiLevel ?: "?"}\n" +
                    "Detecção: $verdict\n" +
                    "Managers com ksud: ${info.managersWithKsud.ifEmpty { "nenhum" }}\n" +
                    "Estado do boot: $state"
                rooted = state == RootState.ROOTED
                softRebootNowButton.isEnabled = rooted
            }
        }
        lifecycleScope.launch {
            val expert = settings.expertMode.first()
            expertSwitch.isChecked = expert
            autorunSwitch.isEnabled = expert
            autorunSwitch.isChecked = AutorunManager.isEnabled(applicationContext)
            autoSoftRebootSwitch.isEnabled = expert && autorunSwitch.isChecked
            modulesOffSwitch.isChecked = settings.modulesOff.first()
            softRebootSwitch.isChecked = settings.softRebootEnabled.first()
            autoSoftRebootSwitch.isChecked = settings.autoSoftReboot.first()
        }
    }
}
