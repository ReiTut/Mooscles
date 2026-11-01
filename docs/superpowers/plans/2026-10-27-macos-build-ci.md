# Build no macOS e CI: plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Os scripts de build e de release passam a rodar no macOS sem deixar de rodar no Windows, e o GitHub Actions do fork roda testes de host, lint e build a cada push e PR.

**Architecture:** Os mesmos `.ps1` passam a funcionar nos dois sistemas: o PowerShell 7 (`pwsh`) roda em Windows, macOS e Linux. Não há versão em bash, porque duplicar a checagem de assinatura do `prepare-release.ps1` arriscaria as duas versões divergirem. O CI usa `ubuntu-latest` para o Gradle. Um job do CI roda `prepare-release.ps1` no APK que o próprio CI gerou, em Ubuntu e em Windows, para provar que o script é portável.

**Tech Stack:** PowerShell 7, Gradle 8 com JDK 21, Android SDK 35, GitHub Actions.

**Spec:** não tem. É trabalho de infraestrutura, sem mudança de comportamento do app; pelo AGENTS.md a spec não é obrigatória nesse caso. As decisões abaixo são minhas: o usuário delegou ("decide por mim") e aprovou o push para o fork em 2026-10-27.

## Global Constraints

- Branch `chore/macos-build-ci`, a partir da `main` do fork. O push vai só para `origin` (`pedroyuuki/Mooscles`), sem force; nunca para `upstream`.
- O comportamento no Windows não muda: mesmos parâmetros, mesmos padrões, mesmo `.tooling\` e mesmas mensagens de erro.
- A proteção da assinatura continua: `prepare-release.ps1` só aceita o APK cujo certificado bate com `ExpectedSignerSha256`, e o padrão continua sendo a impressão digital da chave original (`0cd093b2…bb35`). O CI e o Mac passam a impressão digital deles de forma explícita, e só para testar o script. Nada do CI é publicável.
- Nada de segredo no workflow. O CI não publica release, não cria tag e não tem acesso à chave.
- As actions do GitHub ficam fixadas por SHA de commit completo, com a versão num comentário.
- Sem mudança no app: código Kotlin, Room, DataStore, `versionCode` e `applicationId` ficam como estão.

## Review Focus

1. **Caminho do SDK com espaço** (`/Volumes/Disco Macbook/...`): o script resolve e chama `aapt` e `apksigner` sem quebrar no espaço. Coberto na Task 1 (execução no Mac).
2. **Certificado diferente:** no Mac e no CI, `prepare-release.ps1` sem `-ExpectedSignerSha256` falha com a mensagem "Do not create a new key…", em vez de gerar o `update.json`. Coberto nas Tasks 1 e 3.
3. **CI sem cache na primeira execução** (Robolectric baixa os jars `android-all`, o Gradle baixa o toolchain): termina dentro do limite de tempo do job. Coberto na Task 3.
4. **`build.ps1` no Mac sem `.tooling/jdk-*`:** usa o `JAVA_HOME` do ambiente, e o `GRADLE_USER_HOME` em `.tooling/gradle-home` também funciona no Mac. Coberto na Task 1.
5. **Teste instável no CI** (`DiscardNavigationHostTest`, `AppViewModelFoldersDiscardHostTest:135`): uma falha aparece como falha, sem retentativa automática que a esconda, e os relatórios sobem como artefato. Coberto na Task 3.

---

### Task 1: Scripts portáveis

**Files:**
- Modify: `build.ps1`, `tools/prepare-release.ps1`, `tools/publish-release.ps1`, `tools/capture-workout-performance.ps1`, `tools/prepare-performance-baseline.ps1`, `tools/generate-launcher.ps1`

**Interfaces:** os parâmetros de todos os scripts ficam iguais. Não entra parâmetro novo, a menos que seja indispensável; se entrar, ele tem que estar documentado no relatório.

- [ ] **Step 1: Inventário.** Listar cada ponto que só funciona no Windows: `gradlew.bat`, `aapt.exe`, `apksigner.bat`, `adb.exe`, `gh.exe`, `$env:LOCALAPPDATA`, caminhos com `\` dentro de `Join-Path`, `[StringComparison]::OrdinalIgnoreCase` em caminhos (no macOS, comparar sem diferenciar maiúsculas de minúsculas está certo; no Linux, não) e a codificação.
- [ ] **Step 2: Corrigir.**
  - Montar caminhos com `Join-Path` por segmento.
  - Escolher o executável por plataforma (`$IsWindows`): `gradlew.bat` ou `./gradlew`, `aapt.exe` ou `aapt`, `apksigner.bat` ou `apksigner`, e o mesmo para `adb` e `gh`.
  - Candidatos de SDK, nesta ordem: `.tooling/android-sdk`, `ANDROID_SDK_ROOT`, `ANDROID_HOME`, `LOCALAPPDATA/Android/Sdk` (só no Windows) e `~/Library/Android/sdk` (só no macOS).
  - Comparar caminhos sem diferenciar maiúsculas e minúsculas só no Windows e no macOS.
- [ ] **Step 3: Verificar no Mac** (registrar a saída real):
  - `pwsh ./build.ps1` com o `JAVA_HOME` do Mac roda as tasks padrão.
  - A chamada ao `prepare-release.ps1` dentro dele **falha** pelo certificado, com a mensagem de chave (Review Focus 2). Isso é o esperado.
  - `pwsh tools/prepare-release.ps1 -ApkPath app/build/outputs/apk/debug/app-debug.apk -ExpectedSignerSha256 <impressão digital da chave do Mac> -PreviousVersionCode 15` gera APK, `update.json` e `.sha256` em `app/build/outputs/release/`. O `update.json` tem `applicationId`, `versionCode`, `versionName`, `minSdk`, `apkFileName`, `apkSize` e `sha256`.
  - O SDK é chamado pelo caminho com espaço (Review Focus 1).
  - `pwsh -c "Get-Help ./tools/publish-release.ps1"` carrega sem erro. Não executar a publicação.
- [ ] **Step 4: Commit** com `chore(build): make PowerShell scripts portable to macOS`.

### Task 2: PowerShell no Mac e documentação do ambiente

**Files:**
- Modify: `AGENTS.md` (seção "Ambiente e comandos": no macOS, `pwsh` é instalado assim; os scripts rodam com `pwsh ./build.ps1`; o CI existe e está em `.github/workflows/ci.yml`)
- Modify: `README.md` (seção de desenvolvimento, se existir: uma linha sobre `pwsh` no macOS e o CI)

- [ ] **Step 1:** Instalar o PowerShell 7 estável mais recente sem `sudo`: tarball oficial `osx-arm64` do GitHub `PowerShell/PowerShell`, com o sha256 conferido contra o publicado na release, extraído em `~/.local/share/powershell/<versão>` e com link em `~/.local/bin/pwsh`. Não mexer em `~/.zshrc` (o modo automático negou isso antes): documentar `export PATH="$HOME/.local/bin:$PATH"` para o usuário colar.
- [ ] **Step 2:** `pwsh -v` imprime a versão. Na Task 1 esse `pwsh` pode ser chamado pelo caminho absoluto, caso a Task 2 ainda não tenha rodado. O controlador decide a ordem.
- [ ] **Step 3: Commit** com `docs(env): PowerShell on macOS and CI`.

### Task 3: CI no GitHub Actions

**Files:**
- Create: `.github/workflows/ci.yml`

Desenho:
- Gatilhos: `push` em qualquer branch e `pull_request` para `main`. `concurrency` por ref, cancelando a execução anterior. Permissões mínimas (`contents: read`).
- Job `host` em `ubuntu-latest`, com `timeout-minutes: 45`:
  - checkout;
  - `actions/setup-java` com Temurin 21;
  - `android-actions/setup-android`, ou `sdkmanager`, com platform 35 e build-tools 35.0.0;
  - `gradle/actions/setup-gradle` para o cache;
  - `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain`;
  - em caso de falha, subir `app/build/reports/` e `app/build/test-results/` como artefato;
  - sempre subir o `app-debug.apk` como artefato, com retenção curta (7 dias) e só para inspeção.
- Job `release-script`, que depende de `host`, em matriz `ubuntu-latest` e `windows-latest`:
  - baixa o APK do artefato e instala o pwsh, que já vem nos runners;
  - calcula a impressão digital do certificado do APK com `apksigner verify --print-certs`;
  - roda `prepare-release.ps1 -ExpectedSignerSha256 <a calculada> -PreviousVersionCode 15`;
  - confere que o `update.json` tem os 7 campos e que o sha256 bate;
  - roda de novo **sem** `-ExpectedSignerSha256` e confere que falha com a mensagem de chave (Review Focus 2).
- Nenhum retry automático de teste (Review Focus 5).

- [ ] **Step 1:** Escrever o workflow com as actions fixadas por SHA, resolvido com `gh api repos/<owner>/<repo>/git/ref/tags/<tag>`, e a versão num comentário.
- [ ] **Step 2:** Validar localmente a sintaxe com `actionlint`, se der para instalar sem `sudo`; se não der, registrar.
- [ ] **Step 3:** Fazer push da branch para `origin` e acompanhar com `gh run watch`. Registrar o tempo de cada job na primeira execução (sem cache) e numa segunda execução (com cache).
- [ ] **Step 4:** Commit com `ci: host tests, lint and portable release script check`. Fazer o push antes do Step 3 é aceitável, porque o CI precisa do commit no remoto.

### Task 4: Docs e ROADMAP

**Files:**
- Modify: `docs/superpowers/ROADMAP.md` (fase 0: "Build e release rodando no macOS" e "CI no GitHub Actions" concluídos, com links para este plano e para a primeira execução verde)
- Modify: `docs/superpowers/releases/v1.5.0.md` (as branches de export/import e de PR já estão na `main` do fork; o CI verde passa a ser pré-requisito do PR final; o badge ou o link do CI)
- Create: `docs/superpowers/reports/2026-10-27-macos-build-ci-verificacao.md` (o que foi verificado no Mac e no CI, os tempos, e o que **não** foi: o Windows real fora do CI e a publicação de release, que não foi executada)

- [ ] **Step 1:** Escrever o relatório e atualizar os docs.
- [ ] **Step 2: Commit** com `docs(ci): record macOS build and CI verification`.
