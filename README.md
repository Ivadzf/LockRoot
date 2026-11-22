# LockRoot

Root temporário para Android, com bootloader bloqueado e nada flashado.
O root some no reboot — a cada boot, o app pode tentar de novo (Autorun).

> **STATUS: ESQUELETO — nenhum exploit implementado.**
> Este projeto contém apenas estrutura, detecção somente-leitura, log real
> e stubs com TODOs. Nenhuma rota de root (DirtyFrag, GhostLock ou Timer)
> funciona ainda. Não instale esperando root.

## O que é

Um app Android que dá root temporário (some no reboot, nada vai para
`/system`, `/vendor` ou boot), com detecção antes de tentar, fallback
entre rotas, log detalhado em arquivo e verificação real de sucesso
(`uid=0` **e** `su` respondendo).

## Escopo da v1 (fechado, 2026-11-21)

1. **Detecção antes de tentar** — `uname -r`, KMI, SPL, modelo, vermagic
   match, manager instalado com `libksud.so`. A tela mostra
   "vulnerável / patcheado / sem perfil" antes de qualquer tentativa.
2. **DirtyFrag (CVE-2026-43284) primeiro** — via `IpSecManager`, sem
   Shizuku/ADB.
3. **GhostLock (CVE-2026-43499) como fallback** — só se o DirtyFrag
   falhar; exige perfil exato por `uname -r`, nunca tentar "no chute".
4. **Timer (CVE-2026-98260) como terceira rota** — só se as duas
   anteriores falharem. Exige Shizuku (uid 2000) e o binário do exploit
   vem de fora.
5. **Log detalhado por etapa em arquivo**, com rotação e exportação
   para Downloads.
6. **Handoff via `ksud` do manager instalado** (KernelSU, KernelSU-Next,
   SukiSU, ReSukiSU, OriginSU, BakaSU, SUSFS). **Não embutir `su`,
   `magisk`, `busybox` nem o APK do manager.**
7. **Verificação real de sucesso** — `uid=0` **e** `su` respondendo.
8. **Tratamento de falha como primeira classe** — run anterior sem
   marcador de fim, timeout no carregamento do módulo (code 2),
   limite de tentativas por boot, sem loop infinito.
9. **Aviso de SELinux permissivo** — o SELinux fica permissivo até o
   reboot; isso faz parte do root temporário.
10. **Soft reboot MANUAL após o sucesso** — botão manual, com toggle
    para deixar ativado ou desativado; nunca automático. Preserva o
    root (o kernel não reinicia); reboot total remove o root.
11. **Autorun no boot** — re-root automático depois do reboot, com
    gate de Expert Mode e interlock que desliga o Autorun sozinho
    após 1 falha. Desligado por padrão.
12. **Iniciar o root com todos os módulos desligados** — opção na tela
    de root; o bootstrap cria um arquivo `disable` por módulo em
    `/data/adb/modules` antes do `ksud` subir. Desligado por padrão.

Fora da v1: D2 / Recovery Writer / Odin, qualquer escrita em
`/system`, `/vendor` ou boot, rotas alternativas (GPU Adreno,
CopyFail, Telecom), gerenciamento de módulos KernelSU dentro do app.

## Regras do projeto

- **Reimplementar as ideias, nunca copiar código.** As referências
  (DFRoot/DirtyFrag) são GPL-2.0; ghostlock-app é Apache-2.0; TimerRoot
  é MIT. Se algum trecho precisar ser copiado verbatim de repo
  GPL-2.0, ele NÃO entra aqui — vira TODO "requer reescrita ou troca
  de licença para GPL-2.0" (ver `TODO.md`).
- Apenas APIs públicas do Android + Kotlin stdlib.
- Nada de binários, LKMs (`.ko`) ou APKs embutidos no repo.

## Como compilar (via CI, sem computador)

O projeto foi pensado para quem só tem celular: suba ao GitHub e
compile via GitHub Actions (mesmo modelo do AOTV).

1. Crie um repositório no GitHub e suba este projeto.
2. Adicione um workflow (ex.: `.github/workflows/build.yml`) com
   `actions/checkout`, `actions/setup-java` (JDK 17) e
   `./gradlew :app:assembleDebug` (ou `assembleRelease`).
3. Baixe o APK nos artefatos do run.

Local (se tiver computador com Android SDK):

```sh
./gradlew :app:assembleDebug
```

> O build roda na CI do GitHub (workflow `Android CI`, `assembleDebug`):
> o primeiro build verde foi o run #4 (2026-11-21, v0.2.0), com o
> artefato `lockroot-debug-apk`.

## Avisos de risco

- Root temporário mexe no kernel em tempo de execução. Pode travar,
  reiniciar ou, em casos raros, deixar o aparelho instável até o
  reboot. Faça backup antes de testar.
- Enquanto o root estiver ativo, o SELinux fica permissivo — bancos e
  jogos podem detectar isso.
- O root some no reboot. Nada é persistido: nem root, nem módulo,
  nem alteração de sistema.
- Nunca rode duas vezes no mesmo boot: a cadeia tem um marcador
  one-shot por boot — uma segunda tentativa no mesmo boot não pode
  funcionar e só confunde o diagnóstico. Se uma run falhar, faça
  reboot total antes de retentar.

## Estrutura

```
app/src/main/java/com/lockroot/app/
  MainActivity.kt        tela principal (layout v0.2.0, no modelo do Temporary Root)
  SettingsActivity.kt    tela de Configurações (lote layout v0.2.0)
  BootReceiver.kt        Autorun no boot (desligado por padrão)
  managers/KnownManagers.kt  managers conhecidos + URLs oficiais de download
  detection/Detection.kt     detecção somente-leitura (uname/KMI/SPL/manager)
  logging/LogManager.kt      log em arquivo (implementado de verdade)
  exploit/ExploitRunner.kt   interface Exploit + stubs das 3 rotas + ordem
  exploit/RootManager.kt     handoff ksud + verificação uid=0 e su (stub)
  autorun/AutorunManager.kt  enable/disable + interlock de 1 falha (stub)
  settings/Settings.kt       DataStore Preferences
```

Ver `TODO.md` para a lista ordenada do que falta implementar.
