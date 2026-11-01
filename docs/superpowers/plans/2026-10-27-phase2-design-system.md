# Fase 2: identidade visual e design system "Giz & Ferro", plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Criar o módulo `:designsystem` (Kotlin Multiplatform + Compose Multiplatform, Android + iOS) com os tokens, os componentes, as fontes, os ícones, as strings em inglês e pt-BR e a galeria da spec "Giz & Ferro", e refazer só a tela de Configurações com ele, sem mudar dados nem o resto do app.

**Architecture:** Primeiro um spike descartável prova `tnum` e o eixo de peso das fontes no simulador iOS do CI e no emulador, antes de qualquer fonte entrar na branch da fase. Depois nasce o `:designsystem`, com tokens como dados em `commonMain` e testados (contraste recalculado a partir dos hex), um tema próprio sobre `foundation` (sem Material 3) e uma ponte no `:app` que mantém as telas antigas com as cores e a tipografia de hoje. Os componentes entram em lotes pequenos, cada um com testes de UI que rodam no simulador iOS (CI) e no emulador, e cada lote acrescenta a sua página à galeria. Por fim, as strings de Configurações vão para recursos e a tela é refeita só com o sistema, com capturas no emulador e crítica de tela.

**Tech Stack:** Kotlin 2.4.21 Multiplatform, AGP 9.3.3 (`com.android.kotlin.multiplatform.library`), Gradle 9.7.1, Compose Multiplatform 1.12.2 (plugin `org.jetbrains.compose` e `org.jetbrains.kotlin.plugin.compose` 2.4.21), Compose Multiplatform resources (`org.jetbrains.compose.components:components-resources:1.12.2`), `org.jetbrains.compose.ui:ui-test:1.12.2` (`runComposeUiTest`), Jetpack Compose 1.12.1 no `:app` (BOM 2026.09.00, sem mudança), JUnit 4 + Robolectric 4.17 no `:app`, `kotlin.test`, Python 3 com `fonttools` e `uharfbuzz` só no CI, GitHub Actions (`ubuntu-latest` e `macos-26` com Xcode 26.4).

**Spec:** [docs/superpowers/specs/2026-10-27-phase2-design-system-design.md](../specs/2026-10-27-phase2-design-system-design.md), aprovada em 2026-10-27 com os cinco padrões da seção "Aprovação". Pesquisas: [P1](../reports/2026-10-27-referencias-ux-apps-treino.md), [P3](../reports/2026-10-27-bibliotecas-cmp-graficos-animacao-midia.md), [P4](../reports/2026-10-27-fontes-icones-livres.md). Base: [plano da etapa 1B](2026-10-27-phase1b-shared-module.md) e [spec da fase 1](../specs/2026-10-27-phase1-shared-base-design.md).

## Dependência: só começa depois da 1B

A etapa 1B (módulo `:shared`, plugin `com.android.kotlin.multiplatform.library` no root, KGP no `buildscript`, job `ios` no CI) está sendo implementada na branch `chore/phase1b-shared-module`. **Nenhuma task deste plano começa antes de a 1B estar na `main` do fork com o CI verde (`host` e `ios`).** A Task 1 confere isso no Step 1 e para se não estiver. Este plano usa da 1B: o padrão de módulo KMP do `shared/build.gradle.kts`, o job `ios`, o resumo de testes com `MIN_HOST_TESTS` e o pacote `com.mooscles.backup` (com `ImportSummary` e `BackupTexts`) no `:shared`. Se a 1B tiver mudado algum desses nomes, use o nome real e registre no commit.

## Decisões travadas por este plano

As marcadas com **(minha escolha)** são as que a spec deixou em aberto ou que este plano decidiu sozinho, com a autorização do dono para decisões autônomas.

1. **Versão do Compose Multiplatform: 1.12.2 (minha escolha).** A tabela oficial de compatibilidade ([kotlinlang.org](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html), consultada em 2026-10-27) diz que o CMP 1.12.2 usa o Jetpack Compose 1.12.1, que é exatamente o que o BOM 2026.09.00 do `:app` já traz (`ui`, `foundation`, `animation` e `runtime` 1.12.1, [mapeamento do BOM](https://developer.android.com/develop/ui/compose/bom/bom-mapping)). Assim, no Android, o `:designsystem` não muda nenhuma versão do Compose do app. A mesma página diz que o CMP mais novo é compatível com o Kotlin mais novo, e o guia do KMP lista para o Kotlin 2.4.20–2.4.21 o Xcode 26.4 (o do job `ios`). O guia lista AGP até 9.3.1 e Gradle até 9.7.0; a 1A e a 1B já rodam em 9.3.3 e 9.7.1, e a Task 2 confirma que o plugin do CMP também roda.
2. **Spike antes das fontes, numa branch descartável (minha escolha).** O S1/S2 roda na branch `spike/phase2-tnum`, num módulo temporário `:fontspike`, que nunca entra na `main`. A branch da fase (`feat/phase2-design-system`) só recebe o relatório do spike na Task 1 e as fontes escolhidas na Task 2. Provas do spike:
   - **iOS:** `runComposeUiTest` + `rememberTextMeasurer` em `iosSimulatorArm64Test`, no job `ios` do CI;
   - **Android:** o mesmo teste como teste instrumentado no emulador local `Mooscles_Pixel8_API35` (o Robolectric não vale como prova, como manda a spec);
   - **checagem de host sem emulador:** `tools/check_fonts.py`, que molda os dígitos com HarfBuzz (`uharfbuzz`) com e sem `tnum`. O Android (Minikin) e o Skia do CMP no iOS moldam texto com HarfBuzz, então essa checagem é um bom indicador e roda em todo push. Ela não substitui as duas provas acima, e o relatório diz isso.
3. **Módulo `:designsystem`.** É um KMP com `android { … }` (namespace `com.mooscles.designsystem`, `compileSdk = 37`, `minSdk = 26`, `androidResources { enable = true }`, `withHostTest { isIncludeAndroidResources = true }`, `withDeviceTestBuilder { sourceSetTreeName = "test" }.configure { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }`, `jvmTarget` 17), `iosArm64()` e `iosSimulatorArm64()`. Ele depende só de `runtime`, `foundation`, `ui`, `animation` e `components-resources` do CMP. **Não depende do `:shared` nem do Material 3 (minha escolha):** é uma biblioteca de UI pura, e os componentes são desenhados sobre `foundation`, como a spec prefere. `compose.resources { publicResClass = true; packageOfResClass = "com.mooscles.designsystem.resources"; generateResClass = always }`.
4. **Onde cada teste roda (minha escolha).**
   - `commonTest`: testes puros (tokens, contraste, formatador, mapeamentos). Rodam em `:designsystem:testAndroidHostTest` (JVM) e em `:designsystem:iosSimulatorArm64Test`.
   - `designsystem/src/uiTest/kotlin`: testes de UI (`runComposeUiTest`). O diretório entra por `kotlin.srcDir` no `iosTest` e no `androidDeviceTest`, e não no `androidHostTest`, porque o `runComposeUiTest` precisa de Robolectric na JVM e um teste comum não pode ter `@RunWith`. Rodam no simulador do CI e no emulador local (`:designsystem:connectedAndroidDeviceTest`; confira o nome com `./gradlew :designsystem:tasks --all | grep -i devicetest` e use o real).
   - `designsystem/src/androidHostTest`: testes que leem os arquivos do repositório (paridade de strings).
   - `:app` (Robolectric): a ponte de tema, a tela piloto, o desbloqueio da galeria e a prova de que os recursos do CMP carregam no Robolectric.
5. **API de tema (minha escolha).** O `:app` já tem `com.mooscles.ui.MoosclesTheme(theme: String, content)`, chamado pelo `MoosclesApp`. Para não ter dois `MoosclesTheme` no mesmo arquivo, o design system usa:
   - `@Composable fun MoosclesDesignTheme(dark: Boolean, reducedMotion: Boolean = rememberSystemReducedMotion(), haptics: MoosclesHaptics = rememberPlatformHaptics(), content: @Composable () -> Unit)`;
   - `object MTheme` com `colors`, `type`, `space`, `radius`, `elevation`, `targets`, `sizes`, `motion`, `timing`, `reducedMotion`, `haptics` e `isDark`, lidos de `CompositionLocal`s estáticos.

   Os nomes dos tokens são os da spec com camelCase (`bg.base` → `bgBase`, `accent.solidPressed` → `accentSolidPressed`, `num.display` → `numDisplay`, `space.4` → `s4`, `radius.md` → `md`, `motion.fast` → `fast`, `spring.set` → `springSet`, `target.workout` → `workout`). Os componentes usam o prefixo `M` da spec (`MButton`, `MIconButton`).
6. **Ponte com as telas antigas (minha escolha).** O `MoosclesTheme(theme)` do `:app` passa a envolver o `MaterialTheme` de hoje, **com o mesmo `ColorScheme` e a mesma `Typography` de hoje, sem mudança**, e por dentro o `MoosclesDesignTheme(dark)`. As telas antigas continuam idênticas, e os componentes novos ganham os tokens. A escolha de claro ou escuro é a mesma para os dois. Isso segue a spec ("para as telas antigas não ficarem com cores trocadas até serem redesenhadas"): o `ColorScheme` não é remapeado para os tokens nesta fase.
7. **Tokens além das tabelas da spec (minha escolha).** Nenhum componente pode ter literal de medida ou duração, então as medidas e os tempos que a spec cita no inventário viram tokens nomeados:
   - `MoosclesSizes`: botão L 56, M 48 e S 40; botão de ícone 48 e 56; disco do check 44; `NumericField` 72 × 48; botão do stepper 64 × 56; anel do dock 48 com traço 4; anel expandido 280 com traço 12, 60 marcas de 8 × 2; disco de ±15 s 80; mídia 64; alça do sheet 32 × 4; altura máxima do sheet 0,9 (fração); ícones 16, 20, 24 e 28; linha mínima 56; borda 1; borda de erro 2; anel de foco 2 com afastamento 2. Os traços dos anéis e o tamanho das marcas não estão na spec.
   - `MoosclesTiming`, em ms: `stepperRepeatDelay` 400, `stepperRepeatInterval` 80, `toastInfo` 4.000, `toastAction` 6.000, `prToast` 4.000, `timerDoneHold` 3.000, `skeletonPulse` 1.200, `illustrationFrame` 900 e `hapticDoubleGap` 80.
   - Tipografia: `numDock` (Barlow 600, 32/34) e `numSetIndex` (Barlow 500, 22/24), as duas variantes citadas nas linhas `num.md` e `num.sm`.
   - Inter: um só `FontFamily` com o eixo `wght` por peso e o `opsz` no padrão do arquivo (14). Ajustar o `opsz` por tamanho fica para a fase 3.
8. **Háptico (minha escolha).** `MoosclesHaptics.perform(HapticEvent)` é a entrada única. No Android, o `actual` usa `LocalHapticFeedback` com os `HapticFeedbackType` da tabela da spec. No iOS, o `actual` chama direto os geradores do UIKit (`UIImpactFeedbackGenerator`, `UINotificationFeedbackGenerator` e `UISelectionFeedbackGenerator`) com o mapeamento da coluna iOS da spec, sem passar pelo `LocalHapticFeedback`. Assim o S3 (quais tipos o CMP implementa no iOS) deixa de decidir o comportamento: o S3 fica reduzido a compilar e testar os mapeamentos, e a sensação real fica como não verificada.
9. **`expect/actual` permitidos:** háptico, leitura de "reduzir movimento" e, **só na galeria (minha escolha)**, a troca de idioma `LocalAppLocale`, que segue o contorno documentado pela JetBrains ([compose-resource-environment](https://kotlinlang.org/docs/multiplatform/compose-resource-environment.html)). Nenhum componente usa `LocalAppLocale`, então não existe um segundo design para o iOS.
10. **Formatação de números (minha escolha).** O arredondamento continua no domínio (`number()` do `:app` e `DecimalText.fixed2` do `:shared`), e o design system só localiza o texto pronto: `MoosclesNumbers.localize("1234.5", NumberLocale.PT_BR) == "1.234,5"`. Isso evita uma segunda regra de arredondamento. `NumberLocale.from(tag)` dá `PT_BR` para toda tag que começa com `pt` e `EN` para o resto, porque o app só tem os dois idiomas.
11. **Strings.** Ficam no `:designsystem`, em `composeResources/values/` (inglês, base) e `values-pt-rBR/`, em três arquivos: `strings_components.xml`, `strings_gallery.xml` e `strings_settings.xml`. O texto em inglês da tela piloto é **idêntico ao de hoje**, para que os testes atuais do `:app` continuem valendo sem mudar asserções. Plurais: em pt, o CLDR põe o 0 na categoria `one` ("0 treino"). A regra do app (KDoc do `BackupTexts`: "zero stays plural") vence, e o helper `pluralCount` passa a quantidade 2 quando `n == 0`. **Fica em inglês nesta fase (minha escolha):** o texto que já chega pronto no `UiState`, como `UpdateUi.message`, `BackupUi.message` e `BackupUi.working`, porque quem o produz (o updater e o `AppViewModel`) só é refeito na fase 6. Com o aparelho em pt-BR, Configurações fica em português com essas mensagens em inglês, e as outras telas continuam em inglês até serem redesenhadas. Isso entra no relatório.
12. **Tela piloto (minha escolha).**
    - Na rota de Configurações, a `TopAppBar` global dá lugar ao `TopBar` do sistema, com voltar. A barra inferior continua a de hoje.
    - A ordem das seções é a da spec: Aparência, Unidades, Descanso automático, Seus dados, Atualizações do app, privacidade e "Open-source licenses". Hoje "App updates" vem primeiro. Por isso a rota vira `settings?section={section}`, e o pedido do updater (`updateSettingsRequest`) abre `settings?section=updates` já rolado até a seção de atualizações.
    - As licenças abrem numa rota nova, `licenses`.
    - Saem o sobretítulo "Make it yours" e o subtítulo da `PageHeading`, porque o `TopBar` já diz "Settings" e o texto de privacidade continua na tela.
13. **Galeria (minha escolha).** São 7 toques seguidos na linha da versão instalada, com no máximo 2 s entre um toque e o próximo (senão a contagem recomeça). A entrada fica num `object DeveloperGallery` do `:app`, só em memória: não grava preferência, não vai para o `SavedState` e some quando o processo morre. Ela entra já na Task 13, na seção de atualizações antiga, e a Task 15 a leva para a tela nova. O esqueleto da galeria nasce na Task 3 e cada lote de componentes acrescenta a sua página, para que **toda task termine com a galeria compilando nos dois alvos**, como a spec pede.
14. **Capturas de tela (minha escolha).** Sem Roborazzi nesta fase: a compatibilidade dele com o AGP 9.3 não foi verificada, e a spec deixou a escolha para quem implementa. As capturas são do emulador (`adb exec-out screencap -p`) e ficam em `docs/superpowers/reports/AAAA-MM-DD-phase2-verificacao/`, como nos relatórios anteriores.
15. **Orçamento de APK (minha escolha nos números além do 1 MB).** As fontes, medidas pelo tamanho comprimido das entradas `assets/composeResources/**/font/*` no APK debug, ficam em até 900 KiB, o que deixa ~124 KiB do 1 MB da spec para os ícones, que viram código. O APK debug inteiro pode crescer até 1,5 MiB em relação à base da fase: além das fontes e dos ícones, entram o runtime de recursos do CMP e o código do sistema, que não passa pelo R8 no debug. `tools/check-apk-budget.ps1` verifica os dois limites no CI.
16. **Sem versão nova nesta fase (minha escolha).** `versionCode` 16 e `versionName` 1.4.5 não mudam. A release v1.7 segue a fila das releases pendentes (a v1.5.0 depende da chave de assinatura que está com o dono do repositório original) e terá o seu próprio roteiro.

## Global Constraints

- **Início:** só depois de a 1B estar na `main` do fork com o CI verde. A branch é `feat/phase2-design-system`, criada a partir dessa `main`. O spike usa a `spike/phase2-tnum`, que sai do mesmo commit e nunca é mesclada. O push vai só para `origin`, sem force. O merge na `main` exige CI verde (`host` e `ios`) e `/code-review`.
- **Dados intactos.** Nada muda em:
  - o Room: `version` 5, entidades, migrações e os schemas JSON;
  - o DataStore (`local_preferences` e `app_updates`): arquivos, chaves e tipos;
  - `applicationId = com.mooscles`, `versionCode` 16, `versionName` 1.4.5, a assinatura, o `update.json` e `ReleaseUrls`.

  A tela piloto chama as mesmas ações de hoje (`setTheme`, `setPounds`, `setAutoRest`, `setDefaultRest`, `setAutomaticUpdates`, `setUpdateWifiOnly`, `setIncludePrereleases`, `checkForUpdates`, `downloadUpdate`, `cancelUpdate`, `installUpdate`, `exportData`, `prepareImport`, `confirmImport`, `cancelImport` e `clearBackupMessage`), com os mesmos valores. Nenhum arquivo de `data/`, `timer/`, `updates/` ou `AppPreferences.kt` é editado.
- **Nenhuma mudança perceptível fora da tela de Configurações (rotas `settings` e `licenses`) e da galeria escondida.** As telas antigas mantêm o `ColorScheme`, a `Typography`, os textos fixos em inglês e a formatação de números de hoje.
- **Versões:**
  - entram só o CMP 1.12.2 (`runtime`, `foundation`, `ui`, `animation`, `components-resources` e, nos testes, `ui-test`) e, no `androidDeviceTest`, `androidx.test:runner:1.7.0`, `androidx.test.ext:junit:1.3.0`, `androidx.compose.ui:ui-test-junit4:1.12.1` e `androidx.compose.ui:ui-test-manifest:1.12.1`, se o `runComposeUiTest` do Android pedir;
  - nenhuma dependência existente do `:app` muda de versão, e o diff do `debugRuntimeClasspath` (Task 2) é a prova;
  - ficam de fora Material 3 no `:designsystem`, Lottie/Compottie, Coil, Vico, Roborazzi e bibliotecas de ícones em tempo de execução;
  - Python (`fonttools`, `uharfbuzz`) só no CI e nos scripts de `tools/`, com versões fixadas no workflow.
- **Tokens obrigatórios.** Nos arquivos de `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/components/`, `.../gallery/` e `.../licenses/` e nos da tela piloto (`app/src/main/java/com/mooscles/ui/SettingsScreen.kt`, `UpdateSection.kt`, `DataSection.kt`, `SettingsRestDurationField.kt`, `SettingsTexts.kt`), `tools/check-design-tokens.ps1` reprova:
  - `Color(0x`;
  - `Color.White`, `Color.Black` e as outras cores nomeadas;
  - número seguido de `.dp` ou `.sp`;
  - `tween(`, `spring(` ou `delay(` com número.

  Os valores existem só em `.../designsystem/tokens/`.
- **Acessibilidade:**
  - alvo de 48 dp em todo controle, 56 dp nas ações do treino e 8 dp entre alvos;
  - nada abaixo de 12 sp nem de peso 400;
  - texto a 4,5:1 ou mais e não-texto a 3:1 ou mais, nos dois temas;
  - número sem reticências;
  - nenhum estado só por cor;
  - todo botão só de ícone com rótulo.
- **Textos:**
  - inglês é a base e pt-BR a tradução, com a voz da spec: "você", frases curtas, um verbo no botão, sem exclamação, emoji ou caixa alta escrita;
  - toda chave existe nos dois idiomas, com os mesmos argumentos;
  - o 0 fica no plural.
- **Plataforma.** Todo o design system fica em `commonMain`. Os únicos `expect/actual` são os da decisão 9. `grep -rnE "\b(java|javax|android)\.|System\.|String\.format" designsystem/src/commonMain` tem que voltar vazio.
- **Processo:**
  - durante a task, rode só os testes da área; faça o push depois de cada commit, e o CI roda a suíte inteira e o job `ios`. Um job vermelho bloqueia a revisão da task;
  - este Mac não tem Xcode, então o iOS se prova só no CI. Os testes de `uiTest` no Android rodam no emulador `Mooscles_Pixel8_API35`. Nunca desinstale o app de um aparelho físico do dono (a chave de debug deste Mac não é a das releases);
  - o `compat-reviewer` é obrigatório nas Tasks 2, 15 e 17 e tem que dar `SEGURO` antes do commit;
  - a crítica de tela (`critique-screen` e `better-interface`, pelo `kit-design`) é feita na Task 16, sobre capturas do emulador;
  - o total de testes de host nunca cai abaixo do `MIN_HOST_TESTS` que a 1B deixou no `ci.yml`. Nenhuma asserção existente é enfraquecida; nos testes de UI do `:app`, só o invólucro `MaterialTheme { }` pode virar `MoosclesTheme("light") { }`.
- Código, comentários, KDoc e mensagens de commit em inglês. Este plano e os relatórios em pt-BR, como os anteriores.

## Review Focus

1. **Sair da galeria depois de trocar o idioma para pt-BR.** No Android, o contorno do `LocalAppLocale` muda `Locale.setDefault` e a `Configuration` do processo. Ao voltar, o resto do app precisa estar de novo no idioma do sistema (datas do histórico, strings de Configurações). Coberto na Task 13 por `GalleryLocaleRestoreHostTest.leavingTheGalleryRestoresTheSystemLanguage`.
2. **Uma contagem 0 ou 1 em pt-BR, ou um argumento que não bate entre os idiomas.** "0 treinos" (e não "0 treino"), "1 rotina", e nenhuma chave com `%1$d` num idioma e `%1$s` no outro, que quebraria em tempo de execução. Coberto na Task 5 por `StringsParityTest.placeholdersMatchPerKey` e `PluralCountTest.zeroStaysPluralInPortuguese`, e na Task 14 por `SettingsTextsHostTest.portugueseImportSummaryUsesPluralsAndZeroStaysPlural`.
3. **Configurações a 200% num aparelho estreito (320 dp) em pt-BR**, com os textos longos do updater ("Atualização pronta · instalação adiada"). Todo controle continua alcançável e com alvo de 48 dp ou mais, e nenhum botão tem texto cortado. Coberto na Task 15 por `SettingsScreenHostTest.everyControlIsReachableAtTwoHundredPercentOn320dpInPortuguese`.
4. **A notificação do updater abrindo Configurações**, agora que "App updates" desceu na tela. A pessoa precisa cair na seção de atualizações, como hoje. Coberto na Task 15 por `SettingsScreenHostTest.updateRequestOpensSettingsAtTheUpdatesSection`.
5. **Os 7 toques na versão com TalkBack, toques lentos ou depois de girar a tela.** O desbloqueio não pode gravar nada no DataStore, recomeça a contagem depois de 2 s parado e continua valendo enquanto o processo vive. Coberto na Task 13 por `GalleryUnlockHostTest.sevenQuickTapsRevealTheEntry`, `slowTapsRestartTheCount` e `unlockWritesNoPreference`.

---

### Task 1: Spike S1/S2 de `tnum` e eixo de peso, antes de fixar as fontes

**Files (branch `spike/phase2-tnum`, nunca mesclada):**
- Modify: `settings.gradle.kts` (`include(":fontspike")`), `build.gradle.kts` (`id("org.jetbrains.compose") version "1.12.2" apply false`), `.github/workflows/ci.yml`:
  - no job `ios`, `:fontspike:iosSimulatorArm64Test` depois dos testes do `:shared`;
  - no job `host`, um passo `actions/setup-python` + `pip install fonttools==<estável do dia> uharfbuzz==<estável do dia>` + `python3 tools/check_fonts.py fontspike/src/commonMain/composeResources/font/*.ttf`.
- Create: `fontspike/build.gradle.kts` (igual à decisão 3, com namespace `com.mooscles.fontspike`), `fontspike/src/commonMain/composeResources/font/inter_variable.ttf`, `barlow_condensed_medium.ttf`, `barlow_condensed_semibold.ttf`, `barlow_condensed_bold.ttf`, `fontspike/src/commonMain/kotlin/com/mooscles/fontspike/SpikeFonts.kt`, `fontspike/src/spikeTest/kotlin/com/mooscles/fontspike/TnumSpikeTest.kt` (entra por `srcDir` no `iosTest` e no `androidDeviceTest`), `tools/check_fonts.py`
- Create (branch `feat/phase2-design-system`): `docs/superpowers/reports/AAAA-MM-DD-phase2-tnum-spike.md`, com a data do dia da execução

**Interfaces:**
- Produces: `@Composable fun spikeInter(weight: Int): FontFamily`, com `Font(Res.font.inter_variable, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))`, e `@Composable fun spikeBarlow(): FontFamily`, com 500, 600 e 700.
- Produces: `tools/check_fonts.py <ttf...> [--lock FILE] [--require-tabular GLOB] [--require-proportional GLOB]`. O script imprime, por fonte, os avanços de `0`–`9` moldados por HarfBuzz sem features e com `tnum`, e sai com 1 se:
  - uma fonte de `--require-tabular` tiver avanços diferentes com `tnum`;
  - uma de `--require-proportional` tiver avanços todos iguais sem `tnum`, ou seja, o controle não é capaz de reprovar;
  - o SHA-256 de um arquivo divergir do `--lock`.
- Produces (para a Task 2): a **decisão de fontes** do relatório. É uma destas, com os arquivos exatos, a origem e o SHA-256 de cada um:
  - **A**, aprovada como está;
  - **A'**, aprovada com algarismos tabulares por padrão;
  - **B1**, `pyftfeatfreeze`, do pacote `opentype-feature-freezer`, com renomeação se houver Reserved Font Name;
  - **B2**, Geist Mono só nos timers;
  - **Inter estática** (400, 500, 600 e 700), se o S2 reprovar.

- [ ] **Step 1: Pré-condição.** Rodar `git fetch origin && git log origin/main --oneline -15` e `gh run list --branch main --limit 3`. Conferir que o merge da 1B está na `main` e que o último run tem `host` e `ios` verdes. Senão, **pare e reporte**: o plano não começa.
- [ ] **Step 2: Fontes.** Criar `spike/phase2-tnum` e `feat/phase2-design-system` a partir de `origin/main`. Baixar do repositório `google/fonts`, num commit fixo:
  - `ofl/inter/Inter[opsz,wght].ttf` e `ofl/inter/OFL.txt`;
  - `ofl/barlowcondensed/BarlowCondensed-Medium.ttf`, `-SemiBold.ttf`, `-Bold.ttf` e `OFL.txt`.

  Renomear para os nomes do `composeResources`, em minúsculas e com `_`. Ler os dois `OFL.txt` e anotar se há "Reserved Font Name". Anotar o commit e o SHA-256 de cada arquivo.
- [ ] **Step 3: Testes do spike.** Em `TnumSpikeTest`, todas as larguras são medidas em px com `Density(2f)`, 48 sp, e a tolerância é 0,5 px:
  - `interWithoutTnumIsProportional` (controle): |w("1111") − w("8888")| > 0,5 na Inter 400 sem `tnum`;
  - `barlowIsReallyLoaded` (controle): w("8888") da Barlow 600 < 0,85 × w("8888") da Inter 600, e também diferente de `FontFamily.Default`. Prova que a fonte carregou e não caiu no fallback;
  - `interWithTnumIsTabular`: |w("1111") − w("8888")| ≤ 0,5 com `fontFeatureSettings = "tnum"`;
  - `barlowWithTnumIsTabular`: o mesmo na Barlow 500, 600 e 700;
  - `barlowTimerKeepsWidthWithTnum`: w("0:00") == w("9:59") na Barlow 600 com `tnum`;
  - `barlowDefaultFiguresAreRecorded`: imprime as larguras sem `tnum` com o prefixo `SPIKE`, para o relatório, e não tem asserção;
  - `interWeightAxisApplies` (S2): |w₄₀₀("8888") − w₇₀₀("8888")| > 0,5.

  Cada teste imprime as larguras com `println("SPIKE …")`.
- [ ] **Step 4: Android.** Com o emulador `Mooscles_Pixel8_API35` ligado, rodar `./gradlew :fontspike:connectedAndroidDeviceTest`, usando o nome real da task. Guardar o XML de `fontspike/build/outputs/androidTest-results/` e as linhas `SPIKE` do logcat.
- [ ] **Step 5: CI.** Commit `spike: measure tabular figures and the Inter weight axis` na branch do spike e push. Esperar com `gh run watch`. Baixar o XML do `iosSimulatorArm64Test` e a saída do `check_fonts.py`.
- [ ] **Step 6: Decidir.** Aplicar a regra da spec:
  - Barlow igual com `tnum` nas duas plataformas: **A**;
  - igual já sem `tnum`: **A'**;
  - diferente em alguma plataforma: **B1**, que é a preferência da spec. Gerar com `pyftfeatfreeze -f tnum`, renomear se houver RFN e repetir os Steps 3–5 com a fonte gerada. Se ainda reprovar, **B2**;
  - S2 reprovado no iOS: Inter estática, a partir do zip da release oficial `rsms/inter`, com repetição dos Steps 3–5;
  - um controle reprovado: o teste não vale. Corrija o teste antes de decidir.
- [ ] **Step 7: Relatório.** Na `feat/phase2-design-system`, escrever `docs/superpowers/reports/AAAA-MM-DD-phase2-tnum-spike.md` com:
  - as tabelas de larguras por plataforma e a saída do HarfBuzz;
  - os links do run do CI e o SHA do commit do spike;
  - a decisão de fontes com os arquivos, a origem e os SHA-256;
  - o resultado da leitura das licenças;
  - o que **não** foi provado: a qualidade visual, a desambiguação de 1/l/I e 0/O e um iPhone real.

  Commit `docs(report): phase 2 tnum spike` e push. A `spike/phase2-tnum` fica no `origin` como evidência e não é mesclada.

### Task 2: Módulo `:designsystem`, fontes, licenças, CI e orçamento de APK

**Files:**
- Modify: `settings.gradle.kts` (`include(":app", ":shared", ":designsystem")`), `build.gradle.kts` (`id("org.jetbrains.compose") version "1.12.2" apply false`), `app/build.gradle.kts` (`implementation(project(":designsystem"))`), `build.ps1` (`:designsystem:testAndroidHostTest` na lista padrão de `$Tasks`), `.github/workflows/ci.yml`
- Create: `designsystem/build.gradle.kts` (decisões 3 e 4), `designsystem/font-sources.txt` (uma linha por fonte: arquivo, URL de origem no commit fixo e SHA-256), as fontes da decisão da Task 1 em `designsystem/src/commonMain/composeResources/font/`, `designsystem/src/commonMain/composeResources/files/licenses/inter_ofl.txt`, `barlow_condensed_ofl.txt` e `lucide_isc.txt` (o `LICENSE` do Lucide na versão usada na Task 6), `designsystem/src/commonMain/composeResources/values/strings_components.xml` e `values-pt-rBR/strings_components.xml` (a primeira chave, `ds_licenses_title`: "Open-source licenses" / "Licenças de código aberto"), `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/tokens/Fonts.kt`, `tools/check_fonts.py` (o da Task 1), `tools/check-apk-budget.ps1`, `tools/apk-budget.json`
- Test: `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/TnumRegressionTest.kt`, `app/src/test/java/com/mooscles/DesignSystemResourcesHostTest.kt`

**Interfaces:**
- Produces: `@Composable fun interFamily(): FontFamily` (os pesos 400, 500, 600 e 700 pelo eixo `wght`, ou as instâncias estáticas, conforme a decisão) e `@Composable fun numberFamily(): FontFamily` (Barlow Condensed 500, 600 e 700, ou a fonte do plano B), em `com.mooscles.designsystem.tokens`.
- Produces: `com.mooscles.designsystem.resources.Res`, público.
- Produces: `tools/check-apk-budget.ps1 -Apk <path> [-Config tools/apk-budget.json]`. O `apk-budget.json` tem `{ "baseCommit": "<sha da main pós-1B>", "baseApkBytes": N, "maxGrowthBytes": 1572864, "maxFontBytes": 921600 }`. O script soma o tamanho comprimido das entradas de fonte do APK (`System.IO.Compression.ZipFile`) e falha acima de qualquer um dos dois limites, imprimindo os números.
- Produces, no `ci.yml`:
  - **job `host`:**
    - `:designsystem:testAndroidHostTest` e o lint Android do `:designsystem`, se o plugin registrar um;
    - o resumo soma `designsystem/build/test-results/testAndroidHostTest`;
    - o passo Python roda `tools/check_fonts.py designsystem/src/commonMain/composeResources/font/*.ttf --lock designsystem/font-sources.txt --require-tabular "*" --require-proportional "inter*"`. Se a decisão for **A'** ou **B1**, a Barlow não tem controle proporcional; ajuste os padrões conforme a decisão e explique no commit;
    - depois do `assembleDebug`, `pwsh tools/check-apk-budget.ps1 -Apk app/build/outputs/apk/debug/app-debug.apk`;
    - o upload de relatórios inclui `designsystem/build/reports/` e `designsystem/build/test-results/`;
  - **job `ios`:** `:designsystem:compileKotlinIosArm64 :designsystem:iosSimulatorArm64Test`, com o resumo dos XML de `designsystem/build/test-results/iosSimulatorArm64Test` e a chave do cache `~/.konan` incluindo `designsystem/build.gradle.kts`.

- [ ] **Step 1: Base.** Na `main` pós-1B, rodar `./gradlew :app:assembleDebug` e anotar o tamanho do APK (ou usar o tamanho do artefato `app-debug-apk` do último run verde da `main`; o valor do CI é o que vale, porque o CI compara com ele). Salvar `./gradlew :app:dependencies --configuration debugRuntimeClasspath` em `docs/superpowers/reports/AAAA-MM-DD-phase2-verificacao/deps-before.txt`.
- [ ] **Step 2: Testes que falham.**
  - `TnumRegressionTest`: os testes do spike que se aplicam à decisão, agora contra `interFamily()` e `numberFamily()`, para que uma troca futura de fonte que perca o `tnum` reprove no CI.
  - `DesignSystemResourcesHostTest` (Robolectric, sdk 35):
    - `stringResourceResolvesUnderRobolectric`: `stringResource(Res.string.ds_licenses_title)` renderizado é "Open-source licenses";
    - `fontsLoadUnderRobolectric`: compor um `Text` com `interFamily()` e outro com `numberFamily()` não lança;
    - `licenseFilesAreBundled`: `Res.readBytes("files/licenses/inter_ofl.txt")`, o da Barlow e o do Lucide não são vazios, e os dois OFL contêm "SIL OPEN FONT LICENSE".
- [ ] **Step 3:** Rodar `./gradlew :app:testDebugUnitTest --tests "com.mooscles.DesignSystemResourcesHostTest"` e ver falhar, porque o módulo ainda não existe.
- [ ] **Step 4:** Criar o módulo, as fontes, as licenças, os scripts e as mudanças de CI. Se o plugin do CMP 1.12.2 não configurar com o AGP 9.3.3 ou o Gradle 9.7.1, ou se o Robolectric não achar os recursos do CMP (por exemplo, `MissingResourceException`), **pare e reporte com o erro**, sem contornar: as Tasks 4, 13 e 15 dependem disso.
- [ ] **Step 5: Verificar.**
  - `./gradlew :designsystem:testAndroidHostTest :designsystem:compileCommonMainKotlinMetadata :app:testDebugUnitTest --tests "com.mooscles.DesignSystemResourcesHostTest" :app:assembleDebug` verde;
  - `./gradlew :designsystem:connectedAndroidDeviceTest` verde no emulador;
  - `python3 tools/check_fonts.py …` (os mesmos argumentos do CI) sai com 0;
  - `pwsh tools/check-apk-budget.ps1 -Apk app/build/outputs/apk/debug/app-debug.apk` passa e imprime os números;
  - salvar o `debugRuntimeClasspath` em `deps-after.txt` e o `diff` em `deps-diff.txt`, na mesma pasta. Só podem aparecer linhas novas (o CMP e os recursos). Nenhuma versão existente pode mudar, em especial `androidx.compose.*`, que continua em 1.12.1. Se alguma mudar, **pare e reporte**.
- [ ] **Step 6:** Rodar o `compat-reviewer` (mexe em `app/build.gradle.kts` e no `settings.gradle.kts`; esperado `SEGURO`). Commit `build: add the :designsystem multiplatform module with fonts and licenses` e push. Conferir com `gh run watch` o `host` (com o total de testes e o orçamento no resumo) e o `ios` verdes.

### Task 3: Tokens, tema, contraste provado, reduzir movimento e o esqueleto da galeria

**Files:**
- Create: em `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/tokens/`:
  - `Primitives.kt` (as 43 primitivas da spec);
  - `ColorTokens.kt` (`MoosclesColors`, com os 33 tokens semânticos mais `scrim` e `shadow`);
  - `Contrast.kt`;
  - `TypeTokens.kt`;
  - `SpaceTokens.kt` (`MoosclesSpace`, `MoosclesRadius`, `MoosclesTargets` e `MoosclesSizes`);
  - `ElevationTokens.kt`;
  - `MotionTokens.kt` (`MoosclesMotion` e `MoosclesTiming`).
- Create: em `.../designsystem/theme/`, `MTheme.kt`, `MoosclesDesignTheme.kt` e `ReducedMotion.kt` (`expect`); `.../designsystem/interaction/Haptics.kt` (só os tipos); `designsystem/src/androidMain/kotlin/com/mooscles/designsystem/theme/ReducedMotion.android.kt`; `designsystem/src/iosMain/kotlin/com/mooscles/designsystem/theme/ReducedMotion.ios.kt`
- Create: `.../designsystem/gallery/GalleryPage.kt`, `.../gallery/GalleryScreen.kt` (só a lista de páginas e a página aberta; os controles entram na Task 13), `.../gallery/pages/FoundationsPage.kt` (cores com a razão calculada de cada par, tipografia, espaço, raio, elevação e movimento)
- Create: `tools/check-design-tokens.ps1`, `tools/fixtures/design-tokens/violations.kt.txt`, `tools/fixtures/design-tokens/clean.kt.txt`
- Modify: `.github/workflows/ci.yml` (job `host`: `pwsh tools/check-design-tokens.ps1`, e a autoverificação contra as duas fixtures)
- Test: em `designsystem/src/commonTest/kotlin/com/mooscles/designsystem/tokens/`, `PrimitiveColorsTest.kt`, `ContrastMathTest.kt`, `ContrastTokensTest.kt`, `TypographyTokensTest.kt`, `SpaceTokensTest.kt`, `MotionTokensTest.kt` e `ReducedMotionTest.kt`; `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/ThemeTest.kt`, `.../gallery/GallerySmokeTest.kt` e o helper `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/TestScaffold.kt` (`fun ComposeUiTest.withTheme(dark: Boolean = false, fontScale: Float = 1f, reducedMotion: Boolean = false, haptics: MoosclesHaptics = RecordingHaptics(), content: @Composable () -> Unit)`, que substitui o `LocalDensity` por `Density(density, fontScale)`)

**Interfaces:**
- Produces:
  - `data class MoosclesColors(...)` com `companion object { val Light: MoosclesColors; val Dark: MoosclesColors }`;
  - `object Contrast { fun relativeLuminance(c: Color): Double; fun ratio(a: Color, b: Color): Double }`. Usa a fórmula do WCAG 2.2, com limiar de linearização 0,04045, e trata o alfa compondo sobre branco;
  - `data class MoosclesTypography(numDisplay, numLg, numMd, numDock, numSm, numSetIndex, titleLg, titleMd, titleSm, bodyLg, bodyMd, labelLg, labelMd, labelCaps, caption: TextStyle)`, com `@Composable fun rememberMoosclesTypography(): MoosclesTypography` e a tabela pura `val TypeSpec: Map<String, TypeSpecRow>` (`family`, `weight`, `sizeSp`, `lineHeightSp`, `letterSpacingEm`, `tabular`), da qual os estilos são montados. Todo `num*` leva `fontFeatureSettings = "tnum"`, e `fun TextStyle.tabular(): TextStyle` acrescenta o mesmo nos estilos de Inter;
  - `MoosclesSpace(s1 = 4.dp … s16 = 64.dp)`, `MoosclesRadius(xs, sm, md, lg, xl: Dp; full: Shape)`, `MoosclesTargets(min = 48.dp, workout = 56.dp, gap = 8.dp)`, `MoosclesSizes` (decisão 7), `MoosclesElevation(e1, e2, e3: ElevationSpec)` (`ElevationSpec(shadowLight: Dp, hairlineDark: Color?)`), `MoosclesMotion` (`instant`, `press`, `fast`, `base`, `slow` e `slowExit` em ms; `easeOut`, `easeInOut`, `easeDrawer` como `CubicBezierEasing`; `springSnappy`, `springSet`, `springPr`, `springFinish` como `SpringTokens(dampingRatio, stiffness)`; `pressScale = 0.96f`, `setCheckFrom = 0.92f`, `prFrom = 0.9f`, `prRise = 8.dp`) e `MoosclesTiming` (decisão 7);
  - `MoosclesDesignTheme(...)` e `MTheme` (decisão 5). Nesta task nascem, em `.../interaction/Haptics.kt`, só os tipos: `enum class HapticEvent { SetCompleted, PersonalRecord, RestFinished, StepperStep, SetUnchecked, Rejected, LongPress, ToggleOn, ToggleOff }`, `fun interface MoosclesHaptics { fun perform(event: HapticEvent) }`, `object NoHaptics : MoosclesHaptics` e `class RecordingHaptics : MoosclesHaptics` (com `val events: List<HapticEvent>`, para os testes). O padrão de `haptics` no tema é `NoHaptics` até a Task 7;
  - `@Composable expect fun rememberSystemReducedMotion(): Boolean` e `fun isReducedMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f`. No Android, o `actual` lê `Settings.Global.ANIMATOR_DURATION_SCALE` e reage a mudanças com um `ContentObserver` num `DisposableEffect`. No iOS, lê `UIAccessibilityIsReduceMotionEnabled()` e observa `UIAccessibilityReduceMotionStatusDidChangeNotification`;
  - `enum class GalleryPage(val title: StringResource) { Foundations, … }`, ao qual cada lote acrescenta a sua página, com `@Composable fun GalleryPage.Content()`, e `@Composable fun GalleryScreen(onBack: () -> Unit)`;
  - `tools/check-design-tokens.ps1 [-Path <arquivo ou pasta>...]`. Sem `-Path`, verifica a lista de Global Constraints. Sai com 1 e imprime `arquivo:linha: regra` para cada violação, ignorando comentários de linha (`//`).

- [ ] **Step 1: Testes que falham.**
  - `PrimitiveColorsTest.hexValuesMatchTheSpecTable`: a tabela de primitivas da spec colada como `listOf("ferro.19" to 0xFF111513, …)`, comparada com `Primitives`.
  - `ContrastMathTest.knownRatios`: preto sobre branco dá 21,0; uma cor sobre ela mesma dá 1,0; `#767676` sobre branco dá 4,54 (±0,01).
  - `ContrastTokensTest`. A tabela "Pares medidos" da spec, inteira (50 linhas), é colada como `pair(MoosclesColors::textPrimary, MoosclesColors::bgBase, Kind.Text, light = 15.85, dark = 15.91)`.
    - `everyTextPairMeetsFourAndAHalfInBothThemes`;
    - `everyNonTextPairMeetsThreeInBothThemes`;
    - `ratiosMatchTheSpecTable`: a razão calculada, arredondada a 2 casas, é igual à da tabela (±0,01). Isso prende os hex à spec;
    - `exemptPairsAreOnlyDisabledTextAndSubtleBorder`.
  - `TypographyTokensTest.matchesTheSpecTable`, que inclui `numDock` 32/34 e `numSetIndex` 22/24, e `TypographyTokensTest.nothingBelowTwelveSpOrWeightFourHundred` e `numberStylesAreTabular`.
  - `SpaceTokensTest.spaceScaleMatchesTheSpecAndIsMultipleOfFour`, `radiusMatchesTheSpec`, `targetsAreFortyEightFiftySixAndEight` e `drawnControlsFitTheirTargets` (disco 44 ≤ 56, botão S 40 ≤ 48).
  - `MotionTokensTest.valuesMatchTheSpec` (durações, os quatro pontos das curvas e as quatro molas), `exitIsShorterThanEntry` (`slowExit` < `slow`) e `onlyKeyMomentSpringsBounce` (só `springSet`, `springPr` e `springFinish` com `dampingRatio` < 1).
  - `ReducedMotionTest.zeroAnimatorScaleMeansReduced`: 0 dá verdadeiro; 0,5, 1 e 10 dão falso.
  - `ThemeTest.darkAndLightProvideTheirColorSets` (`MTheme.colors` é `Dark` com `dark = true` e `Light` com `false`) e `ThemeTest.reducedMotionOverrideIsVisible`.
  - `GallerySmokeTest.everyPageRendersInBothThemes`: um laço sobre `GalleryPage.entries`, que cresce sozinho a cada lote.
- [ ] **Step 2:** Rodar `./gradlew :designsystem:testAndroidHostTest` e ver falhar.
- [ ] **Step 3:** Implementar os tokens, o tema, o `expect/actual`, a galeria e o script. As fixtures:
  - `violations.kt.txt` tem uma linha para cada regra (`Color(0xFF000000)`, `Color.White`, `16.dp`, `14.sp`, `tween(200)`, `spring(stiffness = 300f)` e `delay(4000)`);
  - `clean.kt.txt` usa só `MTheme.*`.
- [ ] **Step 4: Verificar.**
  - `./gradlew :designsystem:testAndroidHostTest :designsystem:compileCommonMainKotlinMetadata` verde;
  - `./gradlew :designsystem:connectedAndroidDeviceTest` verde no emulador;
  - `pwsh tools/check-design-tokens.ps1` sai com 0;
  - `pwsh tools/check-design-tokens.ps1 -Path tools/fixtures/design-tokens/violations.kt.txt` sai com 1 e lista as sete linhas;
  - com `-Path tools/fixtures/design-tokens/clean.kt.txt`, sai com 0.
- [ ] **Step 5:** Commit `designsystem: Giz & Ferro tokens with contrast proven by tests` e push. O job `ios` verde.

### Task 4: Ponte de tema no `:app`, sem mudar as telas antigas

**Files:**
- Modify: `app/src/main/java/com/mooscles/ui/Theme.kt`. O `MoosclesTheme(theme: String, content)` passa a ser `MaterialTheme(colorScheme = <os mesmos LightColors/DarkColors>, typography = <a mesma AppTypography>) { MoosclesDesignTheme(dark = dark) { content() } }`, e o cálculo de `dark` e o `SideEffect` das barras do sistema não mudam.
- Test: `app/src/test/java/com/mooscles/ThemeBridgeHostTest.kt`

**Interfaces:**
- Consumes: `MoosclesDesignTheme` e `MTheme` (Task 3).
- Produces: dentro do `MoosclesTheme(theme)`, os dois temas estão disponíveis: `MaterialTheme.*` para as telas antigas e `MTheme.*` para os componentes novos.

- [ ] **Step 1: Testes que falham** (Robolectric, sdk 35):
  - `legacyColorSchemeIsUnchanged`: para "light" e "dark", cada propriedade definida hoje em `LightColors` e `DarkColors` (`primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer`, `secondary`, `secondaryContainer`, `tertiary`, `tertiaryContainer`, `background`, `onBackground`, `surface`, `onSurface`, `surfaceVariant`, `onSurfaceVariant`, `outline` e `outlineVariant`) é igual ao hex de hoje, colado como literal no teste;
  - `legacyTypographyIsUnchanged`: `displaySmall`, `headlineLarge`, `headlineMedium`, `headlineSmall`, `titleLarge`, `titleMedium`, `bodyLarge`, `bodyMedium` e `labelLarge` com o tamanho, a altura de linha, o peso e o espaçamento de hoje;
  - `designTokensFollowTheSameThemeChoice`: "dark" dá `MTheme.colors == MoosclesColors.Dark`, "light" dá `Light`, e "system" com `@Config(qualifiers = "night")` dá `Dark`;
  - `unknownThemeValueFollowsTheSystem`: "amoled" se comporta como "system".

  O primeiro deve passar já antes da mudança (é a caracterização). Os outros falham.
- [ ] **Step 2:** Rodar `./gradlew :app:testDebugUnitTest --tests "com.mooscles.ThemeBridgeHostTest"` e ver os três últimos falharem.
- [ ] **Step 3:** Implementar a ponte.
- [ ] **Step 4: Verificar.** `./gradlew :app:testDebugUnitTest --tests "com.mooscles.ThemeBridgeHostTest" --tests "com.mooscles.*HostTest" :app:assembleDebug` verde.
- [ ] **Step 5:** Commit `ui: provide design tokens under the unchanged Material theme` e push.

### Task 5: Números por idioma, plurais, idioma da galeria e paridade de strings

**Files:**
- Create: `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/format/NumberLocale.kt`, `.../format/MoosclesNumbers.kt`, `.../format/PluralCount.kt`, `.../i18n/AppLocale.kt` (`expect object LocalAppLocale` e `@Composable fun ProvideAppLocale(tag: String?, content)`), `designsystem/src/androidMain/kotlin/com/mooscles/designsystem/i18n/AppLocale.android.kt`, `designsystem/src/iosMain/kotlin/com/mooscles/designsystem/i18n/AppLocale.ios.kt`
- Modify: `designsystem/src/commonMain/composeResources/values/strings_components.xml` e `values-pt-rBR/strings_components.xml`:
  - `ds_sets_count`, um plural: "%1$d set" / "%1$d sets" e "%1$d série" / "%1$d séries";
  - `ds_unit_kg_spoken`: "%1$s kilograms" / "%1$s quilos";
  - `ds_unit_lb_spoken`: "%1$s pounds" / "%1$s libras".
- Test: `designsystem/src/commonTest/kotlin/com/mooscles/designsystem/format/MoosclesNumbersTest.kt`, `designsystem/src/androidHostTest/kotlin/com/mooscles/designsystem/StringsParityTest.kt`, `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/format/PluralCountTest.kt`, `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/i18n/AppLocaleTest.kt`

**Interfaces:**
- Produces:
  - `enum class NumberLocale { EN, PT_BR; companion object { fun from(languageTag: String): NumberLocale } }` e `@Composable fun currentNumberLocale(): NumberLocale`, a partir de `androidx.compose.ui.text.intl.Locale.current`;
  - `object MoosclesNumbers`:
    - `fun localize(plain: String, locale: NumberLocale): String`. A entrada é um decimal simples, como o `number()` e o `DecimalText` produzem (`-?[0-9]+(\.[0-9]+)?`), e o resto lança `IllegalArgumentException`. Agrupa a parte inteira de 3 em 3 a partir de 1.000;
    - `fun weight(plain: String, unit: String, locale: NumberLocale): String`, com ` ` antes da unidade;
    - `fun setText(loadPlain: String, unit: String, reps: Int, locale: NumberLocale): String`, que dá "87,5 kg × 6";
    - `fun clock(seconds: Int): String`, que dá "1:24" em qualquer idioma;
  - `@Composable fun pluralCount(res: PluralStringResource, n: Int): String`, que chama `pluralStringResource(res, if (n == 0) 2 else n, n)`;
  - `LocalAppLocale`, que segue o contorno oficial da decisão 9 e é usado só pela galeria e pelos testes.

- [ ] **Step 1: Testes que falham.**
  - `MoosclesNumbersTest.localizesEnglish`: "82.5"→"82.5", "6840"→"6,840", "1234.75"→"1,234.75", "-2.5"→"-2.5", "0"→"0", "100000"→"100,000";
  - `localizesPortuguese`: "82.5"→"82,5", "6840"→"6.840", "1234.75"→"1.234,75", "999"→"999";
  - `weightUsesNoBreakSpace`: "82,5 kg";
  - `setTextUsesTheMultiplicationSign`;
  - `clockPadsSeconds`: 0→"0:00", 84→"1:24", 599→"9:59";
  - `localeFromTag`: "pt-BR", "pt" e "pt-PT" dão `PT_BR`; "en-US", "de-DE" e "" dão `EN`;
  - `rejectsNonPlainInput`: "1,5", "1e3" e "" lançam.
  - `StringsParityTest`, lendo os XML de `src/commonMain/composeResources/values*/strings_*.xml`:
    - `everyEnglishKeyExistsInPortugueseAndViceVersa` (strings e plurals);
    - `pluralsDefineOneAndOtherInBothLanguages`;
    - `placeholdersMatchPerKey`: o mesmo conjunto de `%n$s`/`%n$d` em cada par;
    - `noEmptyValues`;
    - `copyHasNoExclamationOrEmoji`: a regra de voz da spec. O "!" é permitido só dentro de `<!-- -->`.
  - `PluralCountTest.zeroStaysPluralInPortuguese` (com `ProvideAppLocale("pt-BR")`: 0→"0 séries", 1→"1 série", 2→"2 séries") e `PluralCountTest.englishUsesOneAndOther`.
  - `AppLocaleTest.providingPortugueseResolvesPortugueseStrings` e `AppLocaleTest.providingNullRestoresTheSystemLanguage`.
- [ ] **Step 2:** Rodar `./gradlew :designsystem:testAndroidHostTest` e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4: Verificar.** `./gradlew :designsystem:testAndroidHostTest` e `:designsystem:connectedAndroidDeviceTest` verdes, e o `ios` verde no CI. O iOS escreve `AppleLanguages` no `NSUserDefaults` do simulador: o teste restaura com `null` no `@AfterTest`.
- [ ] **Step 5:** Commit `designsystem: locale-aware numbers, plural rule and string parity checks` e push.

### Task 6: Ícones Lucide e ícones próprios

**Files:**
- Create: `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/icons/MIcons.kt` (`object MIcons`, que lista todos), `.../icons/MIcon.kt`, um arquivo por ícone do Lucide em `.../icons/lucide/` (gerado uma vez com o Valkyrie, com cabeçalho dizendo a versão do Lucide, o arquivo de origem e "generated, do not edit"), um arquivo por ícone próprio em `.../icons/own/`, `.../gallery/pages/IconsPage.kt`
- Modify: `.../gallery/GalleryPage.kt` (`Icons`); `designsystem/src/commonMain/composeResources/files/licenses/lucide_isc.txt`, se a versão do Lucide for outra que a da Task 2
- Test: `designsystem/src/commonTest/kotlin/com/mooscles/designsystem/icons/IconsTest.kt`

**Interfaces:**
- Produces: `MIcons` com:
  - **do Lucide, nesta lista fechada:** `ArrowLeft`, `Check`, `X`, `Plus`, `Minus`, `ChevronRight`, `ChevronDown`, `ChevronUp`, `EllipsisVertical`, `CircleAlert`, `CircleCheck`, `Info`, `Lock`, `Sun`, `Moon`, `MonitorSmartphone`, `Download`, `Upload`, `RefreshCw`, `Timer`, `SkipForward`, `Pause`, `Play`, `Trash2`, `Undo2`, `FileText`, `Wifi`, `ImageOff`;
  - **próprios:** `Plate` (anilha), `PlateRecord` (anilha com seta), `Dumbbell`, `Barbell`, `Kettlebell`, `Bench`, `Cable` (polia), `Machine`, `Rope` e `Body`;
  - `val all: List<Pair<String, ImageVector>>`.
- Produces: `@Composable fun MIcon(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, size: Dp = MTheme.sizes.icon24, tint: Color = LocalMContentColor.current)` e `val LocalMContentColor`, cujo padrão é `MTheme.colors.textPrimary`.

- [ ] **Step 1: Testes que falham.**
  - `IconsTest.everyIconIsOnTheTwentyFourGrid`: `defaultWidth` e `defaultHeight` 24 dp, viewport 24 × 24;
  - `everyPathStrokesTwoWithRoundCapsAndJoins`: percorre os `VectorGroup`/`VectorPath`; `strokeLineWidth == 2f`, `StrokeCap.Round`, `StrokeJoin.Round`, e o preenchimento é nulo, exceto nos caminhos marcados como estado ativo;
  - `everyIconIsTintable`: todo traço é `SolidColor(Color.Black)`, que o `tint` substitui;
  - `iconListIsExactly`: os 38 nomes da lista acima.
- [ ] **Step 2:** Rodar `./gradlew :designsystem:testAndroidHostTest --tests "*IconsTest*"` e ver falhar.
- [ ] **Step 3:** Converter os SVGs do Lucide (repositório oficial, na versão da release estável do dia, registrada no commit). Desenhar os ícones próprios na mesma grade, com traço de 2, cantos redondos e anilha de frente (disco com furo, como na prancha).
- [ ] **Step 4: Verificar.**
  - `./gradlew :designsystem:testAndroidHostTest` verde;
  - a página `Icons` da galeria no emulador, nos dois temas, mostra cada ícone a 16, 20, 24 e 28 com o nome;
  - salvar uma captura em `docs/superpowers/reports/AAAA-MM-DD-phase2-verificacao/icons-light.png` e `icons-dark.png`.
- [ ] **Step 5:** Commit `designsystem: Lucide subset and own equipment icons as image vectors` e push.

### Task 7: Fundamentos de interação: háptico, política de movimento, anel de foco e pressão

**Files:**
- Create: `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/interaction/HapticsMapping.kt`, `.../interaction/MotionPolicy.kt`, `.../interaction/FocusRing.kt`, `.../interaction/PressScale.kt`, `designsystem/src/androidMain/kotlin/com/mooscles/designsystem/interaction/Haptics.android.kt`, `designsystem/src/iosMain/kotlin/com/mooscles/designsystem/interaction/Haptics.ios.kt`
- Modify: `.../interaction/Haptics.kt` (o `expect fun rememberPlatformHaptics()`), `.../theme/MoosclesDesignTheme.kt` (o padrão de `haptics` passa a ser `rememberPlatformHaptics()`)
- Test: `designsystem/src/commonTest/kotlin/com/mooscles/designsystem/interaction/HapticsMappingTest.kt`, `.../interaction/MotionPolicyTest.kt`, `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/interaction/FocusRingTest.kt`

**Interfaces:**
- Produces:
  - `HapticEvent`, `MoosclesHaptics` e `RecordingHaptics` já existem (Task 3);
  - `enum class AndroidHaptic { Confirm, Reject, SegmentTick, SegmentFrequentTick, LongPress, ToggleOn, ToggleOff }` e `data class HapticStep(val kind: AndroidHaptic, val delayBeforeMs: Long)`;
  - `fun androidHapticPlan(event: HapticEvent): List<HapticStep>`;
  - `enum class IosHaptic { ImpactMedium, ImpactLight, NotificationSuccess, NotificationWarning, NotificationError, Selection }` e `fun iosHapticFor(event: HapticEvent): IosHaptic`;
  - `@Composable expect fun rememberPlatformHaptics(): MoosclesHaptics`.
- Produces:
  - `enum class KeyMoment { SetCompleted, PersonalRecord, WorkoutFinished }`;
  - `sealed interface MotionChoice { data class Spring(val tokens: SpringTokens, val fromScale: Float, val riseDp: Dp) : MotionChoice; data class Fade(val durationMs: Int) : MotionChoice }`;
  - `object MotionPolicy { fun keyMoment(moment: KeyMoment, reduced: Boolean): MotionChoice; fun enter(reduced: Boolean, durationMs: Int): MotionChoice; fun pressScale(pressed: Boolean, enabled: Boolean, reduced: Boolean): Float }`.
- Produces: `fun Modifier.mFocusRing(shape: Shape, interactionSource: MutableInteractionSource): Modifier`. Desenha `focus.ring` com 2 dp de largura e 2 dp de afastamento quando focado, e expõe `SemanticsPropertyKey<Boolean>("FocusRingVisible")`, para os testes. `fun Modifier.mPressScale(interactionSource: MutableInteractionSource, enabled: Boolean): Modifier` aplica `motion.press` com `ease.out`.

- [ ] **Step 1: Testes que falham.**
  - `HapticsMappingTest.androidPlanMatchesTheSpecTable`: os 9 eventos da tabela da spec;
  - `HapticsMappingTest.recordConfirmsTwiceEightyMsApart`: `[Confirm@0, Confirm@80]`;
  - `HapticsMappingTest.iosMappingMatchesTheSpecTable`: medium, success, warning, selection, selection, error, light e selection nos dois interruptores;
  - `MotionPolicyTest.keyMomentsUseTheirSprings`: `SetCompleted` dá `springSet` de 0,92; `PersonalRecord` dá `springPr` de 0,9 e 8 dp; `WorkoutFinished` dá `springFinish`;
  - `MotionPolicyTest.reducedMotionTurnsEverythingIntoAFastFade`;
  - `MotionPolicyTest.disabledNeverScalesOnPress`: `pressScale(true, false, false) == 1f`; `pressScale(true, true, false) == 0.96f`; `pressScale(true, true, true) == 1f`;
  - `FocusRingTest.focusedControlShowsTheRingAndBlurredDoesNot`.
- [ ] **Step 2:** Rodar `./gradlew :designsystem:testAndroidHostTest` e ver falhar.
- [ ] **Step 3:** Implementar. No Android, `AndroidHaptic` vira o `HapticFeedbackType` de mesmo nome, e o atraso é um `delay` num escopo lembrado. No iOS, cada `IosHaptic` vira o gerador do UIKit correspondente, com `prepare()` antes de `impactOccurred`/`notificationOccurred`/`selectionChanged`.
- [ ] **Step 4: Verificar.** `./gradlew :designsystem:testAndroidHostTest :designsystem:connectedAndroidDeviceTest` verdes, e o `ios` verde, que é a prova de que o `actual` do iOS compila (S3).
- [ ] **Step 5:** Commit `designsystem: haptics, motion policy, focus ring and press feedback` e push.

### Task 8: Lote C1: botões, chips, interruptor, rádio, linhas e barra do topo

**Files:**
- Create: em `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/components/`, `MButton.kt`, `MIconButton.kt`, `MChip.kt`, `MSwitch.kt`, `MRadio.kt`, `Rows.kt` (`SettingsRow`, `RadioRow`, `SwitchRow` e `SectionHeader`), `TopBar.kt`, `MText.kt` (o `MText` aplica a caixa alta do `labelCaps` com `uppercase()` no idioma atual); `.../gallery/pages/ButtonsPage.kt`, `ChipsPage.kt` e `RowsPage.kt`
- Modify: `GalleryPage.kt`; `strings_components.xml` (en e pt-BR): `ds_back` ("Back" / "Voltar"), `ds_loading` ("Loading" / "Carregando")
- Test: em `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/components/`, `MButtonTest.kt`, `MIconButtonTest.kt`, `MChipTest.kt` e `RowsTest.kt`, com o `withTheme` da Task 3

**Interfaces:**
- Consumes: os tokens (Task 3), os ícones (Task 6) e a interação (Task 7).
- Produces:
  - `enum class MButtonVariant { Primary, Secondary, Tonal, Text, Destructive, Danger }` e `enum class MButtonSize { L, M, S }`;
  - `@Composable fun MButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, variant: MButtonVariant = Primary, size: MButtonSize = M, icon: ImageVector? = null, enabled: Boolean = true, loading: Boolean = false)`;
  - `@Composable fun MIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = MTheme.sizes.iconButton48, style: MIconButtonStyle = Neutral, enabled: Boolean = true, toggled: Boolean? = null)`;
  - `@Composable fun MChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, kind: MChipKind = Filter, enabled: Boolean = true)`;
  - `@Composable fun MSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true)`, que dispara `ToggleOn`/`ToggleOff`;
  - `@Composable fun MRadio(selected: Boolean, onClick: (() -> Unit)?, enabled: Boolean = true)`;
  - `@Composable fun SettingsRow(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onClick: (() -> Unit)? = null, enabled: Boolean = true, trailing: (@Composable () -> Unit)? = null)`;
  - `@Composable fun RadioRow(title: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, enabled: Boolean = true)`;
  - `@Composable fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, enabled: Boolean = true)`;
  - `@Composable fun SectionHeader(text: String, modifier: Modifier = Modifier)`;
  - `@Composable fun TopBar(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {})`.

  Todos aceitam `Modifier.testTag` do chamador no nó clicável. Os testes da Task 15 dependem disso.

- [ ] **Step 1: Testes que falham** (cada um com `withTheme`, nos dois temas, quando o nome diz "both"):
  - `MButtonTest.everySizeHasAtLeastTheMinimumTarget`: L, M e S com `assertTouchHeightIsAtLeast(MoosclesTargets().min)`; L com altura desenhada de 56;
  - `MButtonTest.loadingKeepsWidthIsNotClickableAndSaysLoading`: largura igual (±0,5 px), `onClick` não chamado e `contentDescription` contendo "Loading";
  - `MButtonTest.disabledIsNotEnabled`;
  - `MButtonTest.labelNeverOverflowsAtTwoHundredPercentOn320dp`: o `TextLayoutResult` pela ação de semântica `GetTextLayoutResult`, com `hasVisualOverflow == false` e o rótulo quebrando em linhas;
  - `MIconButtonTest.toggledExposesToggleState` e `MIconButtonTest.hasTheGivenLabel`;
  - `MChipTest.selectedShowsACheckIconNotOnlyColor`: o nó `chip-check` existe só quando selecionado (`useUnmergedTree`);
  - `RowsTest.switchRowTogglesFromTheWholeRowAndFiresToggleHaptics`: com `RecordingHaptics`, os eventos são `[ToggleOn]` e depois `[ToggleOn, ToggleOff]`;
  - `RowsTest.radioRowIsASelectableRadioButton`: `Role.RadioButton` e `assertIsSelected`;
  - `RowsTest.rowsAreAtLeastFiftySixTallAtEveryScale`: 1,0, 1,3 e 2,0;
  - `RowsTest.sectionHeaderIsAHeading`;
  - `RowsTest.topBarBackIsLabelledAndCallsBack`.
- [ ] **Step 2:** Rodar `./gradlew :designsystem:connectedAndroidDeviceTest` e ver falhar.
- [ ] **Step 3:** Implementar sobre `foundation`: `clickable`/`toggleable`/`selectable` com `indication = null`, `mPressScale` e `mFocusRing`. A área de toque vem de `Modifier.sizeIn(minWidth = targets.min, minHeight = targets.min)`. As cores seguem os papéis da spec:
  - primário: `accent.solid` com `accent.on`, e `solidPressed` ao pressionar;
  - secundário: contorno `border.strong`;
  - tonal: `accent.subtle`;
  - destrutivo: contorno `danger.text`;
  - perigo: `danger.solid`;
  - desabilitado: `text.disabled` sobre `bg.inset`.
- [ ] **Step 4: Verificar.** O mesmo comando verde no emulador; `./gradlew :designsystem:testAndroidHostTest` e `pwsh tools/check-design-tokens.ps1` verdes; e o `ios` verde, com os testes de UI no resumo.
- [ ] **Step 5:** Commit `designsystem: buttons, chips, switch, radio, rows and top bar` e push.

### Task 9: Lote C2a: campo de texto, progresso, aviso de erro, estado vazio e skeleton

**Files:**
- Create: em `.../designsystem/components/`, `MTextField.kt`, `MProgressBar.kt`, `ErrorBanner.kt`, `EmptyState.kt` e `Skeleton.kt`; `.../gallery/pages/FieldsPage.kt` e `StatesPage.kt`
- Modify: `GalleryPage.kt`; `strings_components.xml` (en e pt-BR): `ds_error_prefix` ("Error" / "Erro")
- Test: em `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/components/`, `MTextFieldTest.kt`, `MProgressBarTest.kt` e `FeedbackTest.kt`

**Interfaces:**
- Produces:
  - `@Composable fun MTextField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, label: String, modifier: Modifier = Modifier, help: String? = null, error: String? = null, enabled: Boolean = true, keyboardOptions: KeyboardOptions = KeyboardOptions.Default, onFocusChange: (Boolean) -> Unit = {}, textStyle: TextStyle = MTheme.type.bodyLg)`. Usa `BasicTextField`, com borda `border.strong` e 2 dp `danger.text` no erro, ícone `CircleAlert` + mensagem, `bg.inset` quando desabilitado e o anel de foco;
  - `@Composable fun MProgressBar(progress: Float?, modifier: Modifier = Modifier, contentDescription: String? = null)`, onde `null` é indeterminado. Com "reduzir movimento", o indeterminado fica parado num trilho de 30%. Expõe `ProgressBarRangeInfo` na semântica;
  - `@Composable fun ErrorBanner(message: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null)`, com `danger.subtle`, `CircleAlert` e `liveRegion = Polite`;
  - `@Composable fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, primaryAction: Pair<String, () -> Unit>? = null, secondaryAction: Pair<String, () -> Unit>? = null)`. Nesta fase, a "ilustração de giz" é o ícone próprio a 64 dp em `text.tertiary`, porque as ilustrações são da fase 4;
  - `@Composable fun Skeleton(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(MTheme.radius.xs))`, com pulso de opacidade de `timing.skeletonPulse` e parado com "reduzir movimento".

- [ ] **Step 1: Testes que falham.**
  - `MTextFieldTest.errorShowsIconAndMessageNotOnlyColor`;
  - `MTextFieldTest.disabledIsNotEditable`;
  - `MTextFieldTest.labelAndHelpAreReadWithTheField`: o nó editável tem o rótulo e o texto de ajuda;
  - `MTextFieldTest.grownHeightAtTwoHundredPercentWithoutClipping`: altura mínima e não fixa;
  - `MProgressBarTest.determinateExposesRange`: 0,5 dá `ProgressBarRangeInfo(0.5f, 0f..1f)`;
  - `MProgressBarTest.indeterminateExposesIndeterminate`;
  - `FeedbackTest.errorBannerIsAPoliteLiveRegionWithIcon`;
  - `FeedbackTest.emptyStateHasOnePrimaryAction`;
  - `FeedbackTest.skeletonIsStillWithReducedMotion`: com `reducedMotion = true` e `mainClock.autoAdvance = false`, avançar `timing.skeletonPulse` não muda a opacidade exposta pela propriedade de teste `SkeletonAlpha`.
- [ ] **Step 2:** Rodar `./gradlew :designsystem:connectedAndroidDeviceTest` e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4: Verificar.** Emulador, host, `check-design-tokens` e `ios` verdes.
- [ ] **Step 5:** Commit `designsystem: text field, progress, error banner, empty state and skeleton` e push.

### Task 10: Lote C2b: diálogo, bottom sheet e toasts

**Files:**
- Create: em `.../designsystem/components/`, `MDialog.kt`, `MBottomSheet.kt` e `MToast.kt` (`MToastHostState`, `MToastHost`); `.../gallery/pages/OverlaysPage.kt`
- Modify: `GalleryPage.kt`; `strings_components.xml` (en e pt-BR): `ds_close` ("Close" / "Fechar"), `ds_dismiss` ("Dismiss" / "Dispensar")
- Test: em `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/components/`, `MDialogTest.kt`, `MBottomSheetTest.kt` e `MToastTest.kt`

**Interfaces:**
- Produces:
  - `@Composable fun MDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, dismissLabel: String? = null, destructive: Boolean = false)`. Usa `androidx.compose.ui.window.Dialog`, que é comum e prende o foco e trata o voltar. As ações ficam à direita, com a confirmação na ponta; `destructive` usa `MButtonVariant.Danger`. Entra com `motion.slow` + `ease.drawer`, sai com `slowExit` e tem o scrim do token;
  - `@Composable fun MBottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit)`. Tem alça de 32 × 4, topo `radius.xl`, altura máxima de 90% e arrasto por `anchoredDraggable`, e fecha com voltar, toque no scrim e arrasto;
  - `class MToastHostState { suspend fun show(message: String, kind: MToastKind = Info, actionLabel: String? = null): MToastResult }`, com `enum class MToastKind { Info, Error }` e `enum class MToastResult { Dismissed, ActionPerformed }`. Informativo some em `timing.toastInfo`, com ação em `timing.toastAction`, e erro só ao fechar;
  - `@Composable fun MToastHost(state: MToastHostState, modifier: Modifier = Modifier)`, com `bg.inverse` e a ação em `accent.inverseText`.

- [ ] **Step 1: Testes que falham.**
  - `MDialogTest.confirmIsTheLastActionAndCallsConfirm`;
  - `MDialogTest.backAndScrimDismiss`;
  - `MDialogTest.destructiveUsesTheFilledDangerButton`, pela tag `dialog-confirm-danger`;
  - `MBottomSheetTest.scrimTapAndBackClose`;
  - `MBottomSheetTest.heightIsCappedAtNinetyPercent`;
  - `MToastTest.infoDismissesAfterFourSeconds`: com `mainClock`, existe em 3.999 ms e some em 4.000 + `motion.fast`;
  - `MToastTest.actionToastLastsSixSecondsAndReportsAction`;
  - `MToastTest.errorToastStaysUntilClosed`.
- [ ] **Step 2:** Rodar `./gradlew :designsystem:connectedAndroidDeviceTest` e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4: Verificar.** Emulador, host, `check-design-tokens` e `ios` verdes.
- [ ] **Step 5:** Commit `designsystem: dialog, bottom sheet and toasts` e push.

### Task 11: Lote C3: campo numérico, stepper, check, linha de série e selo de recorde

**Files:**
- Create: em `.../designsystem/components/`, `NumericInput.kt` (análise pura), `NumericField.kt`, `Stepper.kt`, `SetCheck.kt`, `SetRow.kt` e `PrBadge.kt`; `.../gallery/pages/WorkoutInputsPage.kt` e `SetRowPage.kt`
- Modify: `GalleryPage.kt`; `strings_components.xml` (en e pt-BR), com as chaves de acessibilidade da spec:
  - `ds_set_row_a11y`: "Set %1$d of %2$d, previous %3$s, load %4$s, %5$s, %6$s" / "Série %1$d de %2$d, anterior %3$s, carga %4$s, %5$s, %6$s";
  - `ds_reps_count`, um plural: "%1$d rep(s)" / "%1$d repetição(ões)";
  - `ds_set_done` / `ds_set_not_done`: "completed" / "not completed" e "concluída" / "não concluída";
  - `ds_action_complete_set`, `ds_action_edit_load`, `ds_action_edit_reps`, `ds_action_change_type`, `ds_action_remove_set`: "Complete set", "Edit load", "Edit reps", "Change type", "Remove set" / "Concluir série", "Editar carga", "Editar repetições", "Mudar tipo", "Remover série";
  - `ds_complete_set_n`: "Complete set %1$d" / "Concluir série %1$d";
  - `ds_stepper_increase` / `ds_stepper_decrease`: "Increase %1$s by %2$s" / "Aumentar %1$s em %2$s", e o equivalente para diminuir;
  - `ds_pr_label` "PR" e `ds_pr_spoken`: "personal record" / "recorde pessoal";
  - `ds_set_type_warmup_short`, `ds_set_type_drop_short`, `ds_set_type_failure_short`: "W", "D", "F" / "A", "D", "F". **Minha escolha:** em inglês, "W" de *warm-up*; o "A" da spec vale para o pt-BR.
- Test: em `designsystem/src/commonTest/kotlin/com/mooscles/designsystem/components/`, `NumericInputTest.kt`; em `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/components/`, `StepperTest.kt`, `SetCheckTest.kt`, `SetRowTest.kt` e `PrBadgeTest.kt`

**Interfaces:**
- Produces:
  - `object NumericInput { fun parseDecimal(text: String, maxDecimals: Int = 2, max: Double): Double?; fun parseInteger(text: String, max: Int): Int? }`. Aceita vírgula e ponto como separador decimal (**minha escolha**), recusa sinal, espaço, separador de milhar e texto vazio;
  - `enum class NumericKind { Load, Reps }`;
  - `@Composable fun NumericField(value: String, onValueChange: (String) -> Unit, kind: NumericKind, accessibilityLabel: String, modifier: Modifier = Modifier, suggested: String? = null, error: String? = null, enabled: Boolean = true)`. Tem 72 × 48, `num.md`, `KeyboardType.Decimal`/`Number`, e o sugerido em `text.secondary` enquanto vazio;
  - `@Composable fun Stepper(value: Double, onValueChange: (Double) -> Unit, step: Double, min: Double, display: String, decreaseLabel: String, increaseLabel: String, modifier: Modifier = Modifier)`. Os botões têm 64 × 56, o valor `num.lg` e o háptico `StepperStep` a cada passo. Ao segurar, repete depois de `timing.stepperRepeatDelay` e a cada `timing.stepperRepeatInterval`, e no mínimo o − fica desabilitado;
  - `@Composable fun SetCheck(checked: Boolean, onCheckedChange: (Boolean) -> Unit, setNumber: Int, modifier: Modifier = Modifier, enabled: Boolean = true)`. É um disco de 44 num alvo de 56, `Role.Checkbox`, com rótulo `ds_complete_set_n`. Usa `MotionPolicy.keyMoment(SetCompleted)` e os hápticos `SetCompleted`/`SetUnchecked`; o `PersonalRecord` é disparado pela `SetRow`, não pelo check;
  - `enum class SetType { Normal, Warmup, Drop, Failure }` e `enum class SetStatus { Pending, Editing, Completed, CompletedRecord }`;
  - `data class SetRowState(val index: Int, val total: Int, val previous: String?, val previousSpoken: String?, val load: String, val loadSpoken: String, val reps: Int, val type: SetType, val status: SetStatus)`;
  - `@Composable fun SetRow(state: SetRowState, onCompleteChange: (Boolean) -> Unit, onEditLoad: () -> Unit, onEditReps: () -> Unit, onChangeType: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier, loadField: (@Composable () -> Unit)? = null, repsField: (@Composable () -> Unit)? = null)`. As colunas são série, anterior, carga, reps e check, com altura mínima de 56. Com `fontScale >= 1.5f`, vira duas linhas: anterior em cima; carga, reps e check embaixo. É um nó de semântica fundido com `ds_set_row_a11y` e cinco `customActions`. O estado `CompletedRecord` mostra `PrBadge(soft = true)` e dispara `PersonalRecord` no lugar de `SetCompleted`. A remoção com "desfazer" é do chamador (toast);
  - `@Composable fun PrBadge(modifier: Modifier = Modifier, soft: Boolean = false)`. É uma pílula `pr.solid` (ou `pr.subtle`) com `PlateRecord` e "PR", lida como `ds_pr_spoken`.

- [ ] **Step 1: Testes que falham.**
  - `NumericInputTest.parsesBothSeparators`: "82,5" e "82.5" dão 82.5; "82" dá 82.0;
  - `NumericInputTest.rejectsInvalid`: "", "-1", "1.234,5", "1.255" (3 casas), "abc" e acima de `max` dão `null`;
  - `NumericInputTest.parsesIntegers`;
  - `StepperTest.stepChangesValueAndTicks`: + dá `value + step` e `[StepperStep]`;
  - `StepperTest.minimumDisablesDecrease`;
  - `StepperTest.holdRepeatsAfterFourHundredThenEveryEighty`: `down`, depois 399 ms dá 0 passos, 400 dá 1, 480 dá 2, 560 dá 3, e `up` para;
  - `StepperTest.buttonsAreLabelledWithTheStep`: "Increase load by 2.5 kilograms" vindo de `increaseLabel`;
  - `StepperTest.buttonsMeetTheWorkoutTarget`: 56;
  - `SetCheckTest.isAToggleWithTheSetNumber`;
  - `SetCheckTest.targetIsFiftySix`;
  - `SetCheckTest.checkAndUncheckFireTheirHaptics`;
  - `SetRowTest.readsAsOneNodeWithTheSpecLabel`: em inglês, o `contentDescription` é "Set 4 of 5, previous 80 kilograms by 6, load 85 kilograms, 6 reps, not completed"; em pt-BR, "Série 4 de 5, anterior 80 quilos por 6, carga 85 quilos, 6 repetições, não concluída". O "por 6" vem do `previousSpoken` do chamador;
  - `SetRowTest.exposesFiveCustomActionsThatCallBack`;
  - `SetRowTest.splitsIntoTwoLinesAtOneAndAHalf`: a 1,5, o fundo do nó "anterior" fica acima do topo do campo de carga; a 1,0, os dois têm o mesmo `top`;
  - `SetRowTest.recordShowsTheBadgeAndFiresTheRecordHaptic`;
  - `SetRowTest.typeIsALetterNotOnlyColor`;
  - `SetRowTest.numbersNeverEllipsizeAtTwoHundredPercentOn320dp`;
  - `PrBadgeTest.isReadAsPersonalRecord`.
- [ ] **Step 2:** Rodar `./gradlew :designsystem:testAndroidHostTest --tests "*NumericInputTest*"` e `:designsystem:connectedAndroidDeviceTest` e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4: Verificar.** Emulador, host, `check-design-tokens` e `ios` verdes.
- [ ] **Step 5:** Commit `designsystem: numeric field, stepper, set check, set row and PR badge` e push.

### Task 12: Lote C4: timer de descanso (dock e expandido), aviso de recorde e card de exercício

**Files:**
- Create: em `.../designsystem/components/`, `RestTimerRing.kt` (desenho do anel-anilha), `RestTimerDock.kt`, `RestTimerExpanded.kt`, `PrToast.kt` e `ExerciseCard.kt`; `.../gallery/pages/TimerPage.kt`, `RecordsPage.kt` e `ExerciseCardPage.kt`
- Modify: `GalleryPage.kt`; `strings_components.xml` (en e pt-BR):
  - `ds_timer_rest` ("Rest" / "Descanso");
  - `ds_timer_done` ("Rest complete" / "Descanso concluído");
  - `ds_timer_minus15` / `ds_timer_plus15` ("Subtract 15 seconds" / "Tirar 15 segundos", "Add 15 seconds" / "Adicionar 15 segundos");
  - `ds_timer_skip` ("Skip rest" / "Pular descanso");
  - `ds_timer_up_next` ("Up next" / "A seguir");
  - `ds_timer_pause` / `ds_timer_resume`;
  - `ds_timer_announce` ("Rest, %1$s remaining" / "Descanso, %1$s restantes");
  - `ds_minutes` e `ds_seconds`, plurais;
  - `ds_duration_and` ("%1$s and %2$s" / "%1$s e %2$s");
  - `ds_pr_toast_title` ("New record: %1$s" / "Novo recorde: %1$s") e `ds_pr_toast_before` ("before %1$s" / "antes %1$s");
  - `ds_add_set` ("Add set" / "Adicionar série");
  - `ds_media_offline` ("Image not downloaded yet" / "Imagem ainda não baixada");
  - `ds_exercise_menu` ("Exercise options" / "Opções do exercício").
- Test: em `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/components/`, `RestTimerDockTest.kt`, `RestTimerExpandedTest.kt`, `PrToastTest.kt` e `ExerciseCardTest.kt`; em `designsystem/src/commonTest/kotlin/com/mooscles/designsystem/components/`, `RestTimerRingMathTest.kt`

**Interfaces:**
- Produces:
  - `enum class TimerPhase { Running, Paused, Done }` e `data class RestTimerVisual(val remainingSeconds: Int, val totalSeconds: Int, val phase: TimerPhase, val label: String)`;
  - `fun ringProgress(remainingMs: Long, totalMs: Long, reduced: Boolean): Float`. É linear; com `reduced`, arredonda para o segundo inteiro. `fun tickAngles(count: Int = 60): List<Float>`;
  - `@Composable fun RestTimerDock(visual: RestTimerVisual, onMinus15: () -> Unit, onPlus15: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier)`. Tem anel de 48, rótulo, tempo `numDock` tabular e −15/+15/pular com 56 de alvo. Com `fontScale >= 1.5f`, os botões empilham. Quando a fase passa a `Done`, dispara `RestFinished` uma vez e mostra `ds_timer_done`; o tempo de 3 s de exibição é do chamador (fase 3). Tem um nó oculto com `liveRegion = Polite` cujo texto (`ds_timer_announce`) muda só ao começar, ao ser ajustado e ao terminar;
  - `@Composable fun RestTimerExpanded(visual: RestTimerVisual, upNext: String?, onMinus15: () -> Unit, onPlus15: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier)`. Tem anel de 280 com 60 marcas, `numDisplay`, discos de 80 e "Skip rest";
  - `@Composable fun PrToast(title: String, detail: String, visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier)`. Entra com `MotionPolicy.keyMoment(PersonalRecord)`, chama `onDismiss` depois de `timing.prToast` e não tem ação;
  - `sealed interface ExerciseMedia { data object None; data object Offline; data class Image(val painter: Painter) }`;
  - `@Composable fun ExerciseCard(name: String, meta: String, media: ExerciseMedia, equipmentIcon: ImageVector, onMenu: () -> Unit, onAddSet: () -> Unit, modifier: Modifier = Modifier, collapsed: Boolean = false, sets: @Composable ColumnScope.() -> Unit)`.

- [ ] **Step 1: Testes que falham.**
  - `RestTimerRingMathTest.progressIsLinear`: 30 s de 60 dá 0,5;
  - `RestTimerRingMathTest.reducedMotionStepsBySecond`: 59.400 ms de 120.000 dá exatamente 60/120;
  - `RestTimerRingMathTest.sixtyTicksSixDegreesApart`;
  - `RestTimerDockTest.timeIsTabularAndNeverEllipsized`;
  - `RestTimerDockTest.announcementChangesOnlyOnStartAdjustAndFinish`: com 84→83→82 s, o texto da live region fica igual; com +15, muda; com `Done`, muda;
  - `RestTimerDockTest.finishFiresRestFinishedOnce`: duas recomposições em `Done` dão um evento;
  - `RestTimerDockTest.actionsMeetTheWorkoutTarget`: 56;
  - `RestTimerDockTest.buttonsStackAtOneAndAHalf`;
  - `RestTimerExpandedTest.discsAreEightyAndLabelled`;
  - `RestTimerExpandedTest.showsUpNextWhenGiven`;
  - `PrToastTest.dismissesAfterFourSecondsWithoutAction`;
  - `PrToastTest.reducedMotionUsesFade`, pela propriedade de teste `MotionChoiceUsed`;
  - `ExerciseCardTest.offlineMediaShowsTheIconAndText`;
  - `ExerciseCardTest.noMediaShowsOnlyTheEquipmentIcon`;
  - `ExerciseCardTest.menuIsLabelled`;
  - `ExerciseCardTest.addSetMeetsTheWorkoutTarget`;
  - `ExerciseCardTest.collapsedHidesTheSets`.
- [ ] **Step 2:** Rodar `./gradlew :designsystem:testAndroidHostTest --tests "*RestTimerRingMathTest*"` e `:designsystem:connectedAndroidDeviceTest` e ver falhar.
- [ ] **Step 3:** Implementar. O anel é desenhado em `Canvas`, com `timer.track` e `timer.progress`, e as marcas da anilha em `border.subtle`.
- [ ] **Step 4: Verificar.** Emulador, host, `check-design-tokens` e `ios` verdes.
- [ ] **Step 5:** Commit `designsystem: rest timer dock and expanded ring, PR toast and exercise card` e push.

### Task 13: Galeria completa, rota `gallery` e os 7 toques na versão

**Files:**
- Modify: `.../designsystem/gallery/GalleryScreen.kt`. Entram os controles do topo (tema claro ou escuro, escala 1,0, 1,3 ou 2,0, "reduzir movimento" forçado e idioma en ou pt-BR), num `GalleryControls` em memória. A tela envolve a página aberta com `MoosclesDesignTheme(dark, reducedMotion)`, `LocalDensity` com o `fontScale` escolhido e `ProvideAppLocale`. Ao sair (`DisposableEffect`), restaura o idioma com `null`.
- Modify: `strings_gallery.xml` (en e pt-BR): os títulos das páginas e dos controles.
- Create: `app/src/main/java/com/mooscles/ui/DeveloperGallery.kt`
- Modify: `app/src/main/java/com/mooscles/ui/MoosclesApp.kt` (`composable("gallery") { GalleryScreen(onBack = { nav.popBackStack() }) }`, sem a `TopAppBar` global nessa rota) e `app/src/main/java/com/mooscles/ui/UpdateSection.kt` (o texto da versão instalada, tag `update-installed-version`, passa a contar os toques. Depois do desbloqueio, aparece um `TextButton` "Component gallery", tag `open-gallery`, que navega para `gallery`. A navegação chega pelo novo parâmetro `onOpenGallery: () -> Unit = {}`.)
- Test: `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/gallery/GallerySmokeTest.kt` (ampliado), `app/src/test/java/com/mooscles/GalleryUnlockHostTest.kt`, `app/src/test/java/com/mooscles/GalleryLocaleRestoreHostTest.kt`

**Interfaces:**
- Consumes: todas as páginas das Tasks 3, 6, 8, 9, 10, 11 e 12; `ProvideAppLocale` (Task 5).
- Produces: `object DeveloperGallery { val unlocked: Boolean; fun registerVersionTap(nowMs: Long): Boolean; internal fun resetForTest() }`. O estado é `mutableStateOf` só em memória. A função devolve `true` no 7º toque seguido, com no máximo 2.000 ms entre toques.
- Produces: `GalleryScreen(onBack: () -> Unit)` com os controles, que a Task 15 abre da tela nova.

- [ ] **Step 1: Testes que falham.**
  - `GallerySmokeTest.everyPageRendersInBothThemesAtTwoHundredPercent`: as páginas × claro/escuro × 2,0, sem exceção;
  - `GallerySmokeTest.everyClickableNodeHasALabel`: em toda página, todo nó com `OnClick` tem `contentDescription` ou `text` não vazio;
  - `GallerySmokeTest.everyClickableTargetIsAtLeastTheMinimum`: `touchBoundsInRoot` ≥ 48 dp nas duas dimensões;
  - `GallerySmokeTest.fontScaleControlChangesTheDensityOfThePage`;
  - `GalleryUnlockHostTest.sevenQuickTapsRevealTheEntry`: no `UpdateSection`, 6 toques não mostram `open-gallery` e o 7º mostra;
  - `GalleryUnlockHostTest.slowTapsRestartTheCount`: com `registerVersionTap` e relógio falso, um intervalo de 2.001 ms recomeça a contagem;
  - `GalleryUnlockHostTest.unlockWritesNoPreference`: com o padrão de isolamento do store do `AppPreferencesTest`, as chaves e os valores do DataStore antes e depois do desbloqueio são iguais;
  - `GalleryUnlockHostTest.unlockSurvivesRecreation`: `ActivityScenario.recreate()` mantém a entrada;
  - `GalleryLocaleRestoreHostTest.leavingTheGalleryRestoresTheSystemLanguage`: abrir `gallery` no `MoosclesApp`, trocar o idioma para pt-BR e voltar. `Locale.getDefault()` volta ao original, e um `stringResource(Res.string.ds_licenses_title)` fora da galeria é "Open-source licenses".
- [ ] **Step 2:** Rodar `./gradlew :app:testDebugUnitTest --tests "com.mooscles.Gallery*"` e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4: Verificar.**
  - `./gradlew :app:testDebugUnitTest --tests "com.mooscles.Gallery*" --tests "com.mooscles.UpdateSectionHostTest" :app:assembleDebug` verde;
  - `./gradlew :designsystem:connectedAndroidDeviceTest` verde;
  - no emulador, os 7 toques abrem a galeria, e cada controle muda a página;
  - `ios` verde, com o `GallerySmokeTest` no resumo. Isso substitui o "a galeria roda no iOS" do ROADMAP, como a spec registra.
- [ ] **Step 5:** Commit `ui: component gallery behind seven taps on the installed version` e push.

### Task 14: Textos de Configurações em recursos (en idêntico a hoje + pt-BR) e a regra no AGENTS.md

**Files:**
- Create: `designsystem/src/commonMain/composeResources/values/strings_settings.xml` e `values-pt-rBR/strings_settings.xml`. Todo texto fixo de hoje em `SettingsScreen`, `UpdateSection` (inclusive os 14 títulos de `updateStatusTitle` e os textos de cada fase), `DataSection` e `RestDurationField` na versão de Configurações vira chave `settings_*`. Entram também "Open-source licenses", "Component gallery" e os plurais `settings_workouts`, `settings_routines`, `settings_custom_exercises` e `settings_workouts_kept`.
- Create: `app/src/main/java/com/mooscles/ui/SettingsTexts.kt`
- Modify: `AGENTS.md`. A frase "UI, comentários e docs do repositório são em inglês: mantenha assim." vira "A UI tem inglês como idioma base e tradução pt-BR por recursos (`composeResources` do `:designsystem`); telas ainda não redesenhadas continuam com textos fixos em inglês até a sua fase. Comentários e docs de código são em inglês."
- Test: `app/src/test/java/com/mooscles/SettingsTextsHostTest.kt`

**Interfaces:**
- Consumes: `pluralCount` e `ProvideAppLocale` (Task 5); `ImportSummary` e `BackupTexts` (`:shared`, `com.mooscles.backup`); `UpdatePhase`.
- Produces: `@Composable fun importSummaryText(summary: ImportSummary): String`, que em inglês dá o mesmo texto que `BackupTexts.summary`, e `@Composable fun updateStatusTitleText(phase: UpdatePhase): String`, que em inglês dá o mesmo que `updateStatusTitle`. O texto do diálogo "nada novo" é `settings_import_nothing_new`, igual a `BackupTexts.NOTHING_NEW` em inglês.
- Glossário pt-BR (vale para toda chave):
  - Settings → Configurações; Appearance → Aparência; System/Light/Dark → Sistema/Claro/Escuro;
  - Weight units → Unidades de peso; Kilograms · kg → Quilogramas · kg; Pounds · lb → Libras · lb;
  - Automatic rest → Descanso automático; Default rest duration → Duração padrão do descanso;
  - Your data → Seus dados; Export data/Import data → Exportar dados/Importar dados;
  - App updates → Atualizações do app; Check for updates → Procurar atualizações; Download update → Baixar atualização; Install update → Instalar atualização;
  - workout → treino; routine → rotina; custom exercise → exercício personalizado; set → série; rest → descanso; record → recorde; phone → celular;
  - Open-source licenses → Licenças de código aberto; Component gallery → Galeria de componentes.

- [ ] **Step 1: Testes que falham.**
  - `SettingsTextsHostTest.englishImportSummaryEqualsBackupTexts`: para os resumos, na ordem `(workoutsAdded, routinesAdded, customExercisesAdded, workoutsKept, restoresPreferences)`, `(180, 4, 3, 28, false)`, `(1, 1, 1, 1, true)`, `(0, 0, 0, 0, true)`, `(2, 0, 1, 0, false)` e `(0, 1, 0, 5, false)`, o texto renderizado é igual a `BackupTexts.summary(summary)`;
  - `SettingsTextsHostTest.englishStatusTitlesEqualToday`: os 14 `UpdatePhase` iguais a `updateStatusTitle`;
  - `SettingsTextsHostTest.portugueseImportSummaryUsesPluralsAndZeroStaysPlural`: com `ProvideAppLocale("pt-BR")`:
    - `(180, 4, 3, 28, false)` dá "Adicionar 180 treinos, 4 rotinas e 3 exercícios personalizados. 28 treinos já estão neste celular e serão mantidos como estão.";
    - `(1, 1, 0, 1, true)` dá "Adicionar 1 treino, 1 rotina e 0 exercícios personalizados. 1 treino já está neste celular e será mantido como está. Suas unidades, tema e configurações de descanso também serão restaurados.";
  - `SettingsTextsHostTest.everySettingsKeyIsUsed`: toda chave `settings_*` aparece em algum arquivo de `app/src/main/java/com/mooscles/ui/`. Com `StringsParityTest`, isso evita chave órfã.
- [ ] **Step 2:** Rodar `./gradlew :app:testDebugUnitTest --tests "com.mooscles.SettingsTextsHostTest"` e ver falhar.
- [ ] **Step 3:** Escrever os XML, o `SettingsTexts.kt` e a regra do `AGENTS.md`.
- [ ] **Step 4: Verificar.** `./gradlew :app:testDebugUnitTest --tests "com.mooscles.SettingsTextsHostTest" :designsystem:testAndroidHostTest --tests "*StringsParityTest*"` verde. O último teste da lista acima só fica verde na Task 15: marque-o com `@Ignore("Enabled in Task 15")` e tire o `@Ignore` lá.
- [ ] **Step 5:** Commit `i18n: settings copy in resources with Brazilian Portuguese` e push.

### Task 15: Tela piloto: Configurações refeita só com o sistema

**Files:**
- Create: `app/src/main/java/com/mooscles/ui/SettingsScreen.kt` (o `SettingsScreen` sai de `MoosclesApp.kt`), `app/src/main/java/com/mooscles/ui/SettingsRestDurationField.kt`, `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/licenses/LicensesScreen.kt`
- Modify:
  - `app/src/main/java/com/mooscles/ui/UpdateSection.kt` e `DataSection.kt`: reescritos com o sistema, **com a mesma assinatura pública, as mesmas tags de teste e o mesmo texto em inglês**;
  - `app/src/main/java/com/mooscles/ui/Common.kt`: o estado do `RestDurationField` vira `@Composable fun rememberRestDurationEditor(seconds: Int, onSecondsChange: (Int) -> Unit, key: String): RestDurationEditorState`, usado pelo campo antigo (que continua igual nas outras telas) e pelo novo;
  - `app/src/main/java/com/mooscles/ui/MoosclesApp.kt`:
    - rota `settings?section={section}`, com padrão `""`;
    - rota `licenses`;
    - na rota de Configurações, o `topBar` é o `TopBar` do sistema, com voltar e o título `settings_title`;
    - o `updateSettingsRequest` navega para `settings?section=updates`;
  - `app/src/test/java/com/mooscles/UpdateSectionHostTest.kt` e `DataSectionHostTest.kt`: só o invólucro `MaterialTheme { }` vira `MoosclesTheme("light") { }`;
  - `app/src/test/java/com/mooscles/SettingsTextsHostTest.kt`: sai o `@Ignore`.
- Test: `app/src/test/java/com/mooscles/SettingsScreenHostTest.kt`

**Interfaces:**
- Consumes: os componentes das Tasks 8–10 (`TopBar`, `SectionHeader`, `RadioRow`, `SwitchRow`, `SettingsRow`, `MButton`, `MTextField`, `MProgressBar`, `ErrorBanner`, `MDialog` e `MIcon`), `SettingsTexts` (Task 14), `DeveloperGallery` (Task 13) e `MoosclesNumbers.localize` para o tamanho do download em MB.
- Produces:
  - `@Composable fun SettingsScreen(state: UiState, actions: MoosclesActions, section: String = "", onOpenLicenses: () -> Unit = {}, onOpenGallery: () -> Unit = {})`;
  - `@Composable fun LicensesScreen(onBack: () -> Unit)`, que lista Inter (OFL), Barlow Condensed (OFL) e Lucide (ISC) com o texto lido de `Res.readBytes("files/licenses/…")`.
- Layout, na ordem:
  1. `TopBar`;
  2. "Appearance": três `RadioRow`, System, Light e Dark, que chamam `setTheme("system"|"light"|"dark")`;
  3. "Weight units": dois `RadioRow`, que chamam `setPounds(false|true)`, mais a nota de precisão;
  4. "Automatic rest": `SwitchRow` com `setAutoRest`, a nota, o `SettingsRestDurationField` (tag `default-rest-duration`, com `setDefaultRest`) e a nota por exercício;
  5. "Your data": `MButton` primário "Export data" (`data-export`) e secundário "Import data" (`data-import`); a nota de treino ativo; `MProgressBar` (`data-progress`) com o texto `working`; a mensagem com `ErrorBanner` (`data-message-error`) ou uma linha com `CircleCheck` (`data-message-success`) e "Dismiss" (`data-message-dismiss`); a nota de privacidade do backup; e o `MDialog` de importação, com `importSummaryText`;
  6. "App updates" (`app-updates`): a versão (`update-installed-version`, com os 7 toques); os três `SwitchRow` (`update-automatic`, `update-wifi-only`, `update-prereleases`); o canal; o bloco de status (`update-status`, com os 14 estados, `update-progress`, `update-download`, `update-install`, `update-retry` e `update-cancel`, com as mesmas regras de hoje); as notas da versão com "Read all notes"; a última checagem com "Check for updates" (`update-check`); os dois textos explicativos; e "Component gallery" (`open-gallery`) só com a galeria desbloqueada;
  7. privacidade: `Lock` e o mesmo texto de hoje;
  8. `SettingsRow` "Open-source licenses" (`open-licenses`), que navega para `licenses`.

- [ ] **Step 1: Caracterização antes de mudar.** Escrever `SettingsScreenHostTest.everyControlCallsTheSameActionsAsBefore`: cada controle, por tag ou por texto, gera a lista de chamadas de hoje, por exemplo `["theme:system", "theme:light", "theme:dark", "pounds:false", "pounds:true", "autoRest:false", "defaultRest:90", …]`, com o mesmo `CapturedActions` dos testes atuais. Rodar contra a tela antiga: ele **tem que passar** antes da reescrita.
- [ ] **Step 2: Testes que falham.**
  - `SettingsScreenHostTest.sectionsFollowTheSpecOrder`;
  - `SettingsScreenHostTest.unknownThemeValueSelectsNoRadioAndStillRenders`: com "amoled";
  - `SettingsScreenHostTest.updateRequestOpensSettingsAtTheUpdatesSection`: no `MoosclesApp`, subir `updateSettingsRequest` deixa `app-updates` visível sem rolar;
  - `SettingsScreenHostTest.everyControlIsReachableAtTwoHundredPercentOn320dpInPortuguese`: com `@Config(qualifiers = "w320dp-h640dp")`, `fontScale` 2 e `ProvideAppLocale("pt-BR")`, cada tag é alcançável por `performScrollTo`, com `assertTouchHeightIsAtLeast(48.dp)` e sem overflow visual de texto em botão;
  - `SettingsScreenHostTest.licensesRowOpensTheLicenses`: mostra "SIL OPEN FONT LICENSE" e "ISC";
  - `SettingsScreenHostTest.galleryEntryIsHiddenUntilUnlocked`;
  - `SettingsScreenHostTest.privacyTextIsUnchanged`: o texto completo de hoje.
- [ ] **Step 3:** Rodar `./gradlew :app:testDebugUnitTest --tests "com.mooscles.SettingsScreenHostTest"` e ver os novos falharem.
- [ ] **Step 4:** Implementar. Qualquer coisa que a tela precise e o sistema não tenha vira componente ou token no `:designsystem`, com teste e página na galeria, e não código local (regra da spec). Registre cada caso no commit.
- [ ] **Step 5: Verificar.**
  - `./gradlew :app:testDebugUnitTest --tests "com.mooscles.SettingsScreenHostTest" --tests "com.mooscles.UpdateSectionHostTest" --tests "com.mooscles.DataSectionHostTest" --tests "com.mooscles.SettingsTextsHostTest" --tests "com.mooscles.RestTimerHostTest" --tests "com.mooscles.DiscardNavigationHostTest" --tests "com.mooscles.UiFlowHostTest" --tests "com.mooscles.WorkoutInputHostTest" --tests "com.mooscles.Inline*" --tests "com.mooscles.Gallery*" --tests "com.mooscles.ThemeBridgeHostTest" :app:lintDebug :app:assembleDebug` verde;
  - `pwsh tools/check-design-tokens.ps1` verde, agora com os arquivos da tela piloto;
  - `pwsh tools/check-apk-budget.ps1 …` dentro do orçamento.
- [ ] **Step 6:** Rodar o `compat-reviewer` (a tela lê e grava as mesmas preferências; esperado `SEGURO`). Commit `ui: rebuild Settings with the Giz & Ferro design system` e push. O CI tem que ficar verde.

### Task 16: Verificação no emulador, TalkBack, reduzir movimento e crítica de tela

**Files:**
- Create: `docs/superpowers/reports/AAAA-MM-DD-phase2-verificacao/`, com as capturas:
  - `settings-{light,dark}-{en,pt}.png` e `settings-dark-pt-200.png`;
  - `licenses.png`;
  - `gallery-{page}-{light,dark}.png` e `gallery-setrow-200.png`;
  - `animator-0.txt`;
  - `talkback.md`, as notas de leitura.
- Create: `docs/superpowers/reports/AAAA-MM-DD-phase2-critica-configuracoes.md`
- Modify: o que a crítica pedir, nos arquivos da tela piloto e nos componentes, com testes quando a correção for de comportamento.

**Interfaces:**
- Consumes: tudo das Tasks 2–15.

- [ ] **Step 1: Dispositivo.** Instalar o APK debug no emulador `Mooscles_Pixel8_API35` **por cima** de um APK da `main` pós-1B com dados (repetir a preparação da Task 10 da 1B: treino ativo, timer, preferências não padrão). Conferir que Configurações mostra as mesmas preferências e que o treino e o timer continuam.
- [ ] **Step 2: Capturas.** Usar `adb exec-out screencap -p` nos dois temas, em en e em pt-BR (`adb shell cmd locale set-app-locales com.mooscles --locales pt-BR`, ou o idioma do sistema), e a 200% (`adb shell settings put system font_scale 2.0`). Fazer o mesmo nas páginas da galeria, pelos 7 toques.
- [ ] **Step 3: TalkBack.** Na tela piloto e na página `SetRow` da galeria, registrar em `talkback.md` a ordem de leitura, a leitura da linha de série e as ações personalizadas. A leitura tem que bater com a spec.
- [ ] **Step 4: Reduzir movimento.** Rodar `adb shell settings put global animator_duration_scale 0`. Conferir na galeria que as molas do check, do recorde e do toast viram fade, que o skeleton fica parado e que o anel avança de segundo em segundo. Registrar em `animator-0.txt` e restaurar a escala para 1.
- [ ] **Step 5: Testes de dispositivo.** Rodar `./gradlew :designsystem:connectedAndroidDeviceTest` verde no emulador.
- [ ] **Step 6: Crítica.** Aplicar `critique-screen` (pelo `kit-design`) às capturas de Configurações e `better-interface` às da galeria (botões, linha de série, timer). Corrigir o que for prioridade alta e média, com nova captura. As de prioridade baixa ficam no relatório da crítica, com o motivo de não entrar.
- [ ] **Step 7: Verificar.** Repetir os comandos do Step 5 da Task 15 e o `check-design-tokens`, todos verdes depois das correções.
- [ ] **Step 8:** Commit `docs(report): phase 2 emulator verification and settings critique`, mais as correções em commits próprios (`ui: …`/`designsystem: …`), e push.

### Task 17: Documentação, ROADMAP e relatório da fase

**Files:**
- Create: `docs/superpowers/reports/AAAA-MM-DD-phase2-verificacao.md`
- Modify:
  - `AGENTS.md`:
    - Arquitetura: o módulo `:designsystem` e o que ele guarda;
    - os comandos `:designsystem:testAndroidHostTest`, `:designsystem:connectedAndroidDeviceTest` e `:designsystem:iosSimulatorArm64Test`;
    - a regra "nenhum literal de cor, medida ou duração fora de `designsystem/.../tokens/`, verificada por `tools/check-design-tokens.ps1`";
    - onde ficam as strings;
    - as rotas `settings?section=`, `licenses` e `gallery`;
  - `README.md` (Configurações em pt-BR e licenças), `VERIFICATION.md` (o que foi e o que não foi verificado nesta fase);
  - `docs/superpowers/ROADMAP.md`: a fase 2 com o link do plano e dos relatórios e o status; o "Onde estamos" do design system;
  - a spec, com uma nota datada em "Notas de implementação" para cada decisão deste plano que divergiu dela (por exemplo, o "W" em inglês, a ordem das seções com `section=updates` e as mensagens do updater em inglês).

- [ ] **Step 1: Relatório.**
  - **Provado:** testes de host e de `commonTest` com os números do CI; o `ios` verde com os testes de UI e a galeria; o emulador (Tasks 15 e 16); o contraste por teste; o orçamento de APK com os números; e o diff de dependências.
  - **Não verificado:** VoiceOver, háptico e renderização num iPhone real; háptico num aparelho Android físico; o contraste sob luz forte de academia; a sensação das molas a 60 fps num aparelho de referência; a revisão visual da galeria no iOS (não há `iosApp`); e as mensagens do updater e do backup em pt-BR (decisão 11).
- [ ] **Step 2:** Atualizar os documentos da lista.
- [ ] **Step 3: Verificar.** Os links do ROADMAP e do relatório abrem (`grep -o "](\([^)]*\))" … | xargs -I{} test -e …`, ou abrindo à mão). O último run do CI da branch está verde.
- [ ] **Step 4:** Rodar o `compat-reviewer` na branch inteira (`git diff origin/main...HEAD`; esperado `SEGURO`) e o `/code-review`. Commit `docs: phase 2 design system verified` e push. Abrir o PR para a `main` do fork com o resumo do relatório. O merge fica liberado com o CI verde e o `/code-review` sem achados de prioridade alta.

## Notas

- **2026-10-28, decisões da varredura prévia.** Antes da Task 2, uma varredura só de leitura cruzou o plano com a spec e com o código pós-1B e encontrou 37 pontos (F1–F38; o F35 não foi usado). Cada um tem uma decisão, e cada task segue as que levam o seu número. A tabela fica no workspace de execução, fora do git. As que mudam o texto acima:
  - **F1:** na Task 3, o `check-design-tokens.ps1` verifica só `components/`, `gallery/` e `licenses/` do design system. A Task 15 acrescenta os arquivos do `:app`.
  - **F2:** o orçamento continua o da decisão 15: fontes até 900 KiB e o APK debug inteiro crescendo até 1,5 MiB. Além disso, fontes e ícones juntos ficam em até 1 MiB, que é o número da spec. A Task 17 registra isso na spec.
  - **F3:** a Task 3 cria o `strings_gallery.xml`.
  - **F5:** um só `accent.solid` por tela. Na tela piloto, Export e Import são `Secondary`.
  - **F10:** o iOS do design system ganha um resumo e um mínimo próprios (`MIN_DS_IOS_TESTS`). A checagem de 6 testes de entrada do `:shared` não muda.
  - **F17:** o script também reprova o uso de `Primitives.` fora de `tokens/`.
  - **F23:** o `--lock` do `check_fonts.py` lê linhas `<arquivo> <url> <sha256>`.
  - **F26:** quem produz as mensagens do updater e do backup é o `MoosclesViewModel`, no `:shared`.
  - **F28:** a Task 17 cria a seção "Notas de implementação" na spec.
  - **F30:** `Contrast.ratio` exige cores opacas.
  - **F33:** `-Xexpect-actual-classes` no `designsystem/build.gradle.kts`.
  - **F34:** `actions/setup-python` fixada por SHA, com versões exatas no `pip`.
- **2026-10-28, resultado do spike ([relatório](../reports/2026-10-28-phase2-tnum-spike.md)):**
  - **Decisão A:** as fontes do `google/fonts` no commit `9862abc`, sem modificação.
  - **Testes de UI:** o `runComposeUiTest` está obsoleto no CMP 1.12.2. Os testes de UI da Task 2 em diante usam `androidx.compose.ui.test.v2.runComposeUiTest`. Ele enfileira as tarefas em vez de rodá-las na hora, então alguns testes podem precisar de espera explícita.
