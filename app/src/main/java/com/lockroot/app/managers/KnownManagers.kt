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

package com.lockroot.app.managers

/**
 * Root managers conhecidos, para o dropdown do card "Gerenciador de root".
 *
 * A lista (nome, descrição, package) segue a do Temporary Root, que o
 * Davi enviou como referência de layout. As URLs de download são as
 * páginas oficiais de releases de cada projeto, verificadas por busca
 * em 2026-11-21 (ver lote-layout-plano.md).
 *
 * "Instalado" aqui significa o mesmo que [com.lockroot.app.detection.Detection]
 * já calcula: o package está instalado **e** traz `libksud.so`.
 * Nada é embutido: o botão "Baixar" só abre a página oficial no
 * navegador; o botão "Abrir" só lança o app já instalado.
 */
data class KnownManager(
    val name: String,
    val description: String,
    val packageName: String,
    val downloadUrl: String,
)

object KnownManagers {

    val ALL: List<KnownManager> = listOf(
        KnownManager(
            name = "Official KernelSU",
            description = "Projeto original · manager padrão",
            packageName = "me.weishu.kernelsu",
            downloadUrl = "https://github.com/tiann/KernelSU/releases",
        ),
        KnownManager(
            name = "OriginSU",
            description = "Fork do KernelSU · manager moderno (recomendado)",
            packageName = "com.originsu.manager",
            downloadUrl = "https://samakshkambxj.github.io/OriginSU/",
        ),
        KnownManager(
            name = "KernelSU-Next",
            description = "Suporte estendido de kernel e integração SUSFS",
            packageName = "com.rifs.kernelsu",
            downloadUrl = "https://github.com/KernelSU-Next/KernelSU-Next/releases",
        ),
        KnownManager(
            name = "BakaSU",
            description = "Antes ReSukiSU · suporte a vários managers",
            packageName = "com.baka.sukisu",
            downloadUrl = "https://github.com/Baka-SU/BakaSU/releases",
        ),
        KnownManager(
            name = "SukiSU-Ultra",
            description = "Suporte a KPM e gerenciamento SUSFS",
            packageName = "com.sukisu.ultra",
            downloadUrl = "https://github.com/SukiSU-Ultra/SukiSU-Ultra/releases",
        ),
    )

    fun byPackage(packageName: String): KnownManager? =
        ALL.firstOrNull { it.packageName == packageName }
}
