# LockRoot — TODO (ordem de implementação)

Cada item cita a seção do estudo que o cobre:
- `a-dirtyfrag.md` = estudo DirtyFrag/DFRoot
- `b-ghostlock.md` = estudo GhostLock
- `c-timer.md` = estudo Timer (CVE-2026-98260)
- `d-awesome.md` = índice awesome-temporary-root
- `plano-v1.md` = escopo fechado da v1

Status do esqueleto: só o `LogManager` está implementado de verdade.
Todo o resto é stub com TODO numerado no código.

## 1. Detecção (base de tudo)

- [ ] 1.1 Confirmar a lista de package names dos managers suportados
      (KernelSU, KernelSU-Next, SukiSU, ReSukiSU, OriginSU, BakaSU,
      SUSFS) e o caminho do `libksud.so` em cada um — A VERIFICAR
      (`a-dirtyfrag.md` §1.1 passo 4; `TODO` em `Detection.kt`).
- [ ] 1.2 Extrair as 8 strings exatas de vermagic dos `.ko` de
      referência em ambiente controlado — NÃO copiar da tabela do
      estudo sem conferir (`a-dirtyfrag.md` §3.1, §10 item 7).
- [ ] 1.3 Implementar o match KMI → módulo (8 ramos android12-5.10 …
      android17-6.18) com a regra nearest-match-para-baixo e log do
      palpite (`a-dirtyfrag.md` §3.2).
- [ ] 1.4 Probe vivo separado da cadeia: escrever 16 bytes de teste,
      ler de volta, registrar "primitivo aberto/fechado" como fato
      separado de "módulo carregou / ksud subiu"
      (`a-dirtyfrag.md` §2).
- [ ] 1.5 Decidir e documentar um único timeout (polling de marcadores
      + timeout total): o estudo registra 10 s no código e 70 s na
      documentação — não herdar dois números contraditórios
      (`a-dirtyfrag.md` §7.2, §10 item 3).
- [ ] 1.6 UI de detecção: mostrar "vulnerável / patcheado / sem perfil"
      antes de qualquer tentativa (`plano-v1.md` item 2).

## 2. DirtyFrag (CVE-2026-43284) — primeira rota

- [ ] 2.1 Reimplementar do zero o binário nativo estagiado (argumento
      por CLI, não JNI): transform IPsec via `IpSecManager`, escrita
      de 16 bytes por vez no page cache (`a-dirtyfrag.md` §1.1–§1.2).
      **Atenção:** qualquer trecho que precisasse ser copiado verbatim
      de repo GPL-2.0 requer reescrita ou troca de licença para
      GPL-2.0 — marcar no código.
- [ ] 2.2 Reimplementar o ajudante dentro do `crash_dump64` (ponte
      para arquivos vendor) (`a-dirtyfrag.md` §1.3).
- [ ] 2.3 Escrita do `.ko` por KMI dentro do arquivo vendor alvo
      (`a-dirtyfrag.md` §1.4). Os 8 `.ko` devem ser construídos por CI
      (DDK), nunca commitados como binário — ver §3.3 do estudo.
- [ ] 2.4 Gancho calculado por ELF parsing em tempo de execução
      (nunca constante fixa); avaliar `libselinux.so` como alvo
      alternativo ao `libc++.so` (`a-dirtyfrag.md` §1.5, §1.11, §10
      item 8).
- [ ] 2.5 Reimplementar o shellcode one-shot com mutex `/dev/df`
      (`a-dirtyfrag.md` §1.6). **TODO "requer reescrita ou troca de
      licença para GPL-2.0" se algum trecho precisar ser copiado
      verbatim.**
- [ ] 2.6 Reimplementar o módulo `dfroot.ko`: desligar SELinux
      (escrita + fallback de patch), DEFEX/Oppo condicionais à
      presença dos símbolos, bootstrap por `call_usermodehelper`,
      auto-descarregamento (`a-dirtyfrag.md` §1.7, §4).
- [ ] 2.7 Reimplementar o bootstrap: ler prefs protegidas, adotar
      ambiente do zygote, travar partições RO, arquivo `disable` por
      módulo (`ENOENT` = sucesso), `ksud late-load`
      (`a-dirtyfrag.md` §1.8, §5).
- [ ] 2.8 Confirmar a sequência de descarregamento do módulo
      (`-ENODEV` vs `rmmod` pelo bootstrap) e a limpeza do page cache
      do alvo vendor (`a-dirtyfrag.md` §10 itens 4–5).
- [ ] 2.9 Confirmar o que é a "accidental mitigation" (kernel 6.1) e
      como detectá-la (`a-dirtyfrag.md` §10 item 6).
- [ ] 2.10 Confirmar o comportamento do carregamento do módulo com
      vermagic divergente / assinatura forçada e se o erro é observável
      (`a-dirtyfrag.md` §10 item 7).

## 3. GhostLock (CVE-2026-43499) — fallback

- [ ] 3.1 Extrair um perfil exato para o kernel do aparelho de
      referência (`5.15.194-android13-8-...`) com o `ghostlock-extract`
      (boot.img/OTA) — sem perfil exato, a rota recusa
      (`b-ghostlock.md` §2–§3).
- [ ] 3.2 Reimplementar a cadeia W1 (SELinux) → W2 (cred) → W3
      (seccomp), com as tentativas e reparos descritos no estudo
      (`b-ghostlock.md` §1).
- [ ] 3.3 Importação/download de perfis com cache (nunca tentar sem
      perfil) e validação de campos antes de executar
      (`b-ghostlock.md` §2).
- [ ] 3.4 Handoff: script root executando o `ksud` extraído do manager
      instalado (`ksud module late-load --kmi <kmi>`)
      (`b-ghostlock.md` §3).

## 4. Timer (CVE-2026-98260) — terceira rota

- [ ] 4.1 Confirmar o transporte Shizuku (uid 2000 para ler
      `/sys/kernel/tracing`) e a origem externa do binário do exploit
      (`c-timer.md`).
- [ ] 4.2 Reimplementar a rota a partir do PoC público, sem copiar
      código; deixar claro na UI quando for essa rota e registrar a
      dependência do Shizuku no log (`plano-v1.md` item 4).
- [ ] 4.3 Avaliar cobertura: o CVE afeta quais kernels/faixas e qual
      o piso de patch (`d-awesome.md` §1.3).

## 5. Verificação, log e falhas

- [ ] 5.1 Verificação real: `uid=0` **e** `su` respondendo; confirmar
      os caminhos onde o `su` é procurado (`a-dirtyfrag.md` §7.4,
      §10 item 11).
- [ ] 5.2 Modelar os três estados desde o dia 1: `NOT_HOOKED` /
      `HOOKED_NO_ROOT` / `ROOTED` — `/dev/df` não é "rooted"
      (`a-dirtyfrag.md` §7.4, §11 item 3).
- [ ] 5.3 Detectar "run anterior sem marcador de fim" e orientar
      reboot total antes de retentar; code 2 (timeout) como primeira
      classe (`a-dirtyfrag.md` §7.2).
- [ ] 5.4 Limite de tentativas por boot, sem loop infinito
      (`plano-v1.md` item 7).
- [ ] 5.5 Aviso de SELinux permissivo na UI e no log
      (`plano-v1.md` item 8).

## 6. Autorun, módulos-off e soft reboot

- [ ] 6.1 Autorun: gate Expert + interlock de 1 falha + espelho do
      soft reboot por pref + registro quando o sistema sequer chama o
      receiver (`a-dirtyfrag.md` §6).
- [ ] 6.2 Módulos-off: pref + arquivo `disable` por módulo, com
      `ENOENT` = sucesso e aborto pré-`ksud` em erro real
      (`a-dirtyfrag.md` §5).
- [ ] 6.3 Soft reboot MANUAL: separar (a) toggle "fazer soft reboot
      após o root" de (b) ação manual "soft reboot agora" numa sessão
      já rooteada — a ação manual não existe no DFRoot e precisa ser
      desenhada do zero (`a-dirtyfrag.md` §8, §10 item 17).
- [ ] 6.4 Confirmar o diretório de módulos dos outros managers
      (KernelSU-Next, SukiSU/ReSukiSU, BakaSU, OriginSU usam o mesmo
      `/data/adb/modules`?) (`a-dirtyfrag.md` §10 item 15).

## 7. Build e publicação

- [ ] 7.1 Testar o build pela primeira vez (CI do GitHub) e ajustar
      versões de AGP/Kotlin/Gradle — **o build NÃO foi testado**.
- [ ] 7.2 Workflow de CI (`.github/workflows/build.yml`) + release de
      APK de debug.
- [ ] 7.3 Revisitar a licença antes do primeiro commit: se algum
      trecho GPL-2.0 precisar ser reaproveitado, a licença passa a
      ser GPL-2.0 (`plano-v1.md` §0).
- [ ] 7.4 Tabela de compatibilidade por aparelho (modelo + build exata
      + `uname -r` + loader + status), no modelo do awesome-temporary-
      root (`d-awesome.md` §1.2).
