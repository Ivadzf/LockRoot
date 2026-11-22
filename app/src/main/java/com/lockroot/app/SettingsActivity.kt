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

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lockroot.app.autorun.AutorunManager
import com.lockroot.app.databinding.ActivitySettingsBinding
import com.lockroot.app.logging.LogManager
import com.lockroot.app.settings.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Tela de Configurações (layout v0.2.0, no modelo da tela Configuration
 * do Temporary Root): linhas com ícone + título + subtítulo + toggle
 * ou chevron.
 *
 * As chaves aqui são as mesmas do esqueleto — só mudaram de lugar
 * (antes ficavam na tela principal). O gate continua o mesmo: Autorun
 * só liga com o Modo Expert, e o soft reboot continua manual, nunca
 * automático (decisão do Davi, plano-v1.md item 9).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var log: LogManager
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        log = LogManager(applicationContext)
        settings = Settings(applicationContext)

        binding.backButton.setOnClickListener { finish() }
        wireRows()
        loadPrefs()
    }

    private fun wireRows() {
        binding.rowExpert.setOnClickListener { binding.switchExpert.toggle() }
        binding.switchExpert.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { settings.setExpertMode(checked) }
            binding.switchAutorun.isEnabled = checked
            if (!checked) {
                binding.switchAutorun.isChecked = false
                AutorunManager.setEnabled(applicationContext, false)
            }
        }

        binding.rowLaunchManager.setOnClickListener { binding.switchLaunchManager.toggle() }
        binding.switchLaunchManager.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { settings.setLaunchManagerAfterSuccess(checked) }
        }

        binding.rowAutorun.setOnClickListener {
            if (binding.switchAutorun.isEnabled) binding.switchAutorun.toggle()
        }
        binding.switchAutorun.setOnCheckedChangeListener { _, checked ->
            AutorunManager.setEnabled(applicationContext, checked)
            lifecycleScope.launch { settings.setAutorunEnabled(checked) }
            log.append("ui: autorun=$checked")
        }

        binding.rowSoftReboot.setOnClickListener { binding.switchSoftReboot.toggle() }
        binding.switchSoftReboot.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { settings.setSoftRebootEnabled(checked) }
        }

        binding.rowModulesOff.setOnClickListener { binding.switchModulesOff.toggle() }
        binding.switchModulesOff.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { settings.setModulesOff(checked) }
            log.append("ui: modules_off=$checked (vale na próxima subida do root)")
        }

        binding.rowImportProfile.setOnClickListener {
            // TODO (TODO.md 3.3): importar um .conf de perfil GhostLock.
            Toast.makeText(this, R.string.toast_profile_todo, Toast.LENGTH_SHORT).show()
        }

        binding.rowAbout.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_about)
                .setMessage(getString(R.string.about_text, BuildConfig.VERSION_NAME))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    /** Lê as prefs uma vez e reflete nos switches (na ordem certa). */
    private fun loadPrefs() {
        lifecycleScope.launch {
            val expert = settings.expertMode.first()
            val launchManager = settings.launchManagerAfterSuccess.first()
            val autorunOn = AutorunManager.isEnabled(applicationContext)
            val softReboot = settings.softRebootEnabled.first()
            val modulesOff = settings.modulesOff.first()
            // Expert primeiro: o listener dele habilita/desabilita o Autorun.
            binding.switchExpert.isChecked = expert
            binding.switchLaunchManager.isChecked = launchManager
            binding.switchAutorun.isEnabled = expert
            binding.switchAutorun.isChecked = autorunOn
            binding.switchSoftReboot.isChecked = softReboot
            binding.switchModulesOff.isChecked = modulesOff
        }
    }
}
