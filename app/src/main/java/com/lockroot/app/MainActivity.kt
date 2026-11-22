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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lockroot.app.databinding.ActivityMainBinding
import com.lockroot.app.databinding.ItemManagerBinding
import com.lockroot.app.detection.Detection
import com.lockroot.app.exploit.ExploitResult
import com.lockroot.app.exploit.ExploitRunner
import com.lockroot.app.exploit.RootManager
import com.lockroot.app.exploit.RootState
import com.lockroot.app.logging.LogManager
import com.lockroot.app.managers.KnownManager
import com.lockroot.app.managers.KnownManagers
import com.lockroot.app.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tela principal do LockRoot (layout v0.2.0, no modelo do Temporary Root).
 *
 * Tudo continua ligado aos stubs do esqueleto: tocar "Obter root" roda
 * [ExploitRunner], que hoje só registra TODOs e recusa — nenhum root é
 * obtido. O que mudou neste lote é só a apresentação e a organização:
 * pílula de status, card de manager com dropdown, card de
 * vulnerabilidade, card de log do console e uma tela de Configurações
 * separada ([SettingsActivity]).
 *
 * Otimização: o log da tela mostra só o fim do arquivo ([LOG_TAIL_CHARS])
 * e é carregado quando a tela abre e quando o card é expandido — nunca
 * um TextView com o arquivo inteiro de uma vez.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var log: LogManager
    private lateinit var settings: Settings

    private var deviceInfo: Detection.DeviceInfo? = null
    private var selectedManager: KnownManager = KnownManagers.ALL[0]
    private var rooted: Boolean = false
    private var managerListExpanded = false
    private var vulnerabilityExpanded = true
    private var logExpanded = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        log = LogManager(applicationContext)
        settings = Settings(applicationContext)

        binding.subtitleText.text =
            getString(R.string.app_subtitle, BuildConfig.VERSION_NAME)
        binding.logText.movementMethod = ScrollingMovementMethod()

        wireControls()
        refreshDetection()
        loadLogTail()
    }

    private fun wireControls() {
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.managerHeader.setOnClickListener { toggleManagerList() }
        binding.downloadButton.setOnClickListener { downloadSelectedManager() }
        binding.openButton.setOnClickListener { openSelectedManager() }
        binding.vulnerabilityHeader.setOnClickListener { toggleVulnerability() }
        binding.rootButton.setOnClickListener { runRoot() }
        binding.softRebootButton.setOnClickListener { confirmSoftReboot() }
        binding.checkCompatButton.setOnClickListener { refreshDetection(showCompatDialog = true) }
        binding.logCopyButton.setOnClickListener { copyLog() }
        binding.logChevron.setOnClickListener { toggleLog() }
        binding.copyLogText.setOnClickListener { copyLog() }
        binding.shareLogText.setOnClickListener { shareLog() }
    }

    /** Roda a detecção (só leitura) e atualiza pílula, manager e estado. */
    private fun refreshDetection(showCompatDialog: Boolean = false) {
        lifecycleScope.launch(Dispatchers.IO) {
            val info = Detection.detect(applicationContext)
            val state = RootManager.currentState(applicationContext, log)
            val savedManager = settings.selectedManagerPackage.first()
            withContext(Dispatchers.Main) {
                deviceInfo = info
                selectedManager = KnownManagers.byPackage(savedManager ?: "")
                    ?: info.managersWithKsud.firstNotNullOfOrNull { KnownManagers.byPackage(it) }
                    ?: KnownManagers.ALL[0]
                rooted = state == RootState.ROOTED
                updateStatusPill(info, state)
                updateManagerHeader()
                buildManagerList()
                binding.softRebootButton.visibility = if (rooted) View.VISIBLE else View.GONE
                binding.selinuxWarningText.visibility = if (rooted) View.VISIBLE else View.GONE
                if (showCompatDialog) showCompatDialog(info, state)
            }
        }
    }

    private fun updateStatusPill(info: Detection.DeviceInfo, state: RootState) {
        val (text, ok) = when {
            state == RootState.ROOTED ->
                getString(R.string.status_rooted, selectedManager.name) to true
            state == RootState.HOOKED_NO_ROOT ->
                getString(R.string.status_hooked_no_root) to false
            info.selectedModule == null ->
                getString(R.string.status_no_profile) to false
            info.managersWithKsud.isEmpty() ->
                getString(R.string.status_no_manager) to false
            else ->
                getString(R.string.status_ready, selectedManager.name) to true
        }
        binding.statusText.text = text
        binding.statusDot.setBackgroundResource(
            if (ok) R.drawable.bg_dot_ok else R.drawable.bg_dot_warn
        )
    }

    private fun updateManagerHeader() {
        binding.managerNameText.text = selectedManager.name
        binding.managerPackageText.text = selectedManager.packageName
        binding.openButton.isEnabled = isManagerInstalled(selectedManager.packageName)
    }

    private fun isManagerInstalled(packageName: String): Boolean {
        return deviceInfo?.managersWithKsud?.contains(packageName) == true
    }

    private fun toggleManagerList() {
        managerListExpanded = !managerListExpanded
        binding.managerListContainer.visibility =
            if (managerListExpanded) View.VISIBLE else View.GONE
        binding.managerChevron.setImageResource(
            if (managerListExpanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down
        )
    }

    /** Monta as linhas do dropdown (uma por manager conhecido). */
    private fun buildManagerList() {
        val container = binding.managerListContainer
        container.removeAllViews()
        for (manager in KnownManagers.ALL) {
            val row = ItemManagerBinding.inflate(layoutInflater, container, false)
            row.managerItemName.text = manager.name
            row.managerItemDescription.text = manager.description
            row.managerItemPackage.text = manager.packageName
            val installed = isManagerInstalled(manager.packageName)
            row.managerItemBadge.setText(
                if (installed) R.string.manager_installed else R.string.manager_not_installed
            )
            row.managerItemBadge.setBackgroundResource(
                if (installed) R.drawable.bg_badge_installed
                else R.drawable.bg_badge_not_installed
            )
            row.managerItemBadge.setTextColor(
                getColor(
                    if (installed) R.color.badge_installed_text
                    else R.color.badge_not_installed_text
                )
            )
            row.root.setOnClickListener { selectManager(manager) }
            container.addView(row.root)
        }
    }

    private fun selectManager(manager: KnownManager) {
        selectedManager = manager
        lifecycleScope.launch { settings.setSelectedManagerPackage(manager.packageName) }
        log.append("ui: manager selecionado=${manager.packageName}")
        updateManagerHeader()
        if (managerListExpanded) toggleManagerList()
    }

    /** Abre a página oficial de download do manager selecionado. */
    private fun downloadSelectedManager() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(selectedManager.downloadUrl))
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.toast_no_browser, Toast.LENGTH_SHORT).show()
        }
    }

    /** Lança o manager selecionado, se estiver instalado. */
    private fun openSelectedManager() {
        val launch = packageManager.getLaunchIntentForPackage(selectedManager.packageName)
        if (launch == null) {
            Toast.makeText(this, R.string.toast_manager_not_installed, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(launch)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.toast_manager_not_installed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleVulnerability() {
        vulnerabilityExpanded = !vulnerabilityExpanded
        binding.vulnerabilityBody.visibility =
            if (vulnerabilityExpanded) View.VISIBLE else View.GONE
        binding.vulnerabilityChevron.setImageResource(
            if (vulnerabilityExpanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down
        )
    }

    private fun toggleLog() {
        logExpanded = !logExpanded
        binding.logBody.visibility = if (logExpanded) View.VISIBLE else View.GONE
        binding.logChevron.setImageResource(
            if (logExpanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down
        )
        if (logExpanded) loadLogTail()
    }

    private fun runRoot() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = ExploitRunner().run(applicationContext, log)
            val launchManager = settings.launchManagerAfterSuccess.first()
            withContext(Dispatchers.Main) {
                when (result) {
                    is ExploitResult.Success -> {
                        rooted = true
                        binding.softRebootButton.visibility = View.VISIBLE
                        binding.selinuxWarningText.visibility = View.VISIBLE
                        deviceInfo?.let { updateStatusPill(it, RootState.ROOTED) }
                        Toast.makeText(
                            this@MainActivity,
                            "Root obtido: ${result.route}",
                            Toast.LENGTH_LONG
                        ).show()
                        if (launchManager) openSelectedManager()
                    }
                    is ExploitResult.Failed -> {
                        binding.statusText.text =
                            "Falhou (código ${result.code}): ${result.reason}\n" +
                            "Faça reboot total antes de tentar de novo."
                    }
                    is ExploitResult.NotAttempted -> {
                        binding.statusText.text = "Não tentado: ${result.reason}"
                    }
                }
                loadLogTail()
            }
        }
    }

    /** Soft reboot manual, com confirmação antes (TODO do esqueleto). */
    private fun confirmSoftReboot() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_soft_reboot_title)
            .setMessage(R.string.confirm_soft_reboot_msg)
            .setPositiveButton(R.string.confirm_yes) { _, _ ->
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
            .setNegativeButton(R.string.confirm_cancel, null)
            .show()
    }

    private fun showCompatDialog(info: Detection.DeviceInfo, state: RootState) {
        val verdict = when {
            info.selectedModule == null -> "Sem perfil / kernel sem suporte"
            info.moduleIsGuess -> "Módulo por palpite (KMI aproximado)"
            else -> "Módulo encontrado: ${info.selectedModule}"
        }
        val message = "Modelo: ${info.model}\n" +
            "Kernel: ${info.kernelRelease}\n" +
            "KMI: android${info.gkiLevel ?: "?"}\n" +
            "Detecção: $verdict\n" +
            "Managers com ksud: ${info.managersWithKsud.ifEmpty { "nenhum" }}\n" +
            "Estado do boot: $state"
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.compat_dialog_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /** Mostra só o fim do log na tela (o arquivo inteiro é pesado demais). */
    private fun loadLogTail() {
        lifecycleScope.launch(Dispatchers.IO) {
            val tail = log.readAll().takeLast(LOG_TAIL_CHARS)
            withContext(Dispatchers.Main) {
                binding.logText.text =
                    tail.ifEmpty { getString(R.string.log_empty) }
                binding.logText.post {
                    val layout = binding.logText.layout ?: return@post
                    binding.logText.scrollTo(0, layout.getLineTop(binding.logText.lineCount))
                }
            }
        }
    }

    private fun copyLog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val text = log.readAll()
            withContext(Dispatchers.Main) {
                if (text.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.toast_log_empty_share,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@withContext
                }
                val clipboard =
                    getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("LockRoot log", text))
                Toast.makeText(this@MainActivity, R.string.toast_log_copied, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun shareLog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val exported = log.exportToDownloads()
            val text = log.readAll()
            withContext(Dispatchers.Main) {
                if (!exported) {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.toast_log_empty_share,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@withContext
                }
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        "LockRoot log ${android.os.Build.MODEL}"
                    )
                }
                startActivity(Intent.createChooser(share, getString(R.string.share_log)))
                Toast.makeText(
                    this@MainActivity,
                    R.string.toast_share_saved,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        /** Máximo de caracteres do log exibidos na tela (o resto fica no arquivo). */
        const val LOG_TAIL_CHARS = 16 * 1024
    }
}
