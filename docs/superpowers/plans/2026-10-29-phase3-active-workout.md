# Fase 3: redesign do treino ativo, plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trocar o treino ativo, o resumo de fim, o cartão de compartilhamento e a casca do app (abas e barra do treino) por telas novas em código comum (`:screens`), feitas só com o `:designsystem`, sem mudar dados, chaves ou identidade do app.

**Architecture:** Três spikes descartáveis (cartão comum, leitura do `MTextField`, teclado no iOS) decidem os caminhos arriscados antes de tudo. Em paralelo, o `:shared` ganha os acréscimos do contrato (erros tipados e `UiError`, `setId` dos recordes, `SetUi.record`, `previousSets`, `restoreSet`, descanso 0:00, regras de sugestão e o `InProcessRestTimer`), todos com testes de host e `compat-reviewer`. Depois nasce o módulo KMP `:screens` (Android + iOS) com CI e guardas, o `:designsystem` ganha as peças que faltam, e as telas entram em lotes pequenos, cada um com `uiTest` rodando no emulador na própria task. Por fim o `:app` passa a hospedar as telas novas (casca, rotas, notificação, cartão, ícone, build `preview`), e a fase fecha com o teste de ponta a ponta no simulador iOS, o orçamento de toques, a atualização por cima, o desempenho, a crítica de tela e a documentação.

**Tech Stack:** Kotlin 2.4.21 Multiplatform, AGP 9.3.3 (`com.android.kotlin.multiplatform.library`), Gradle 9.7.1, Compose Multiplatform 1.12.2 (`runtime`, `foundation`, `ui`, `animation`, `components-resources`; nos testes `ui-test` com `androidx.compose.ui.test.v2.runComposeUiTest`), Jetpack Compose 1.12.1 no `:app` (BOM 2026.09.00, sem mudança), Room 2.8.5, DataStore 1.3.0, `org.jetbrains.kotlinx:kotlinx-datetime` (única dependência nova), Navigation 2.10.2 (sem mudança), JUnit 4 + Robolectric 4.17, `kotlin.test`, GitHub Actions (`ubuntu-latest` e `macos-26` com Xcode 26.4).

**Spec:** [docs/superpowers/specs/2026-10-29-phase3-active-workout-design.md](../specs/2026-10-29-phase3-active-workout-design.md), incluindo a seção "Decisões após a leitura do construtor (2026-10-29)". Base: [spec](../specs/2026-10-27-phase2-design-system-design.md) e [plano](2026-10-27-phase2-design-system.md) da fase 2, [verificação](../reports/2026-10-28-phase2-verificacao.md) e [crítica](../reports/2026-10-28-phase2-critica-configuracoes.md) da fase 2, [verificação da 1B](../reports/2026-10-27-phase1b-verificacao.md) e o [PERFORMANCE.md](../../../PERFORMANCE.md). Direção visual: pranchas "Treino ativo — escuro/claro" e "Descanso e recorde" (cópia local em `.superpowers/scratch-backup/design-canvas/project/`, publicada em <https://claude.ai/artifact/Y4pTUbYqTcCCrznT74raBM>).

## Decisões travadas por este plano

As decisões da spec (D1–D24 e as sete regras de 2026-10-29) valem como estão. Estas são as que a spec deixou para quem implementa, ou que este plano precisou fechar. Todas são **minhas**, com a delegação do dono.

1. **Branches e push.** O controlador mescla `docs/phase3-spec` (spec e este plano) na `main` do fork e cria `feat/phase3-active-workout` a partir dela antes das Tasks 1 e 4. Os spikes usam `spike/phase3-s1`, `spike/phase3-s2` e `spike/phase3` (que junta os dois e o S3), a partir da mesma `main`, nunca mesclados. **Push só nos checkpoints**, marcados **Push** no fim das Tasks 3 (branch do spike), 8, 11, 16, 22, 26, 29 e 32. Nas outras, só commit local. O runner `macos-26` falta com frequência e a regra de concorrência cancela o run anterior: não faça um push novo enquanto o job `ios` do checkpoint anterior estiver na fila ou rodando, a não ser que ele já esteja perdido.
2. **Erros tipados.** Em `shared/.../data/DataErrors.kt`: `enum class DataError { WORKOUT_NOT_FOUND, WORKOUT_EXERCISE_NOT_FOUND, SET_NOT_FOUND, EXERCISE_NOT_FOUND, ROUTINE_NOT_FOUND, FOLDER_NOT_FOUND, SET_LIMIT, INVALID_VALUE, NO_COMPLETED_SET, WORKOUT_NOT_ACTIVE, RESTORE_UNAVAILABLE }` e `class DataRuleException(val error: DataError, message: String) : IllegalArgumentException(message)`. Subclasse de `IllegalArgumentException` porque é o tipo que `require`/`requireNotNull` lançam hoje: o comportamento e a mensagem não mudam, só o tipo fica mais preciso. O `check` do `importBackup` (backup, fora do treino) não muda.
3. **Bloqueio por atualização tipado (desvio pequeno da spec).** A spec lista "treino bloqueado por uma atualização" entre os `UiError`, mas as duas fontes tipadas que ela prevê (`data/` e `RestTimer`) não produzem esse erro: ele vem do `UpdateCoordinator.assertTrainingMayStart()`. Por isso `shared/.../updates/UpdateCoordinator.kt` ganha `class TrainingBlockedException(message: String) : IllegalArgumentException(message)`, que o `UpdateManager` lança no lugar do `require`, com a mesma mensagem.
4. **`UiError`** (em `UiContract.kt`): `SAVE_FAILED, TIMER_STORAGE, LOCAL_DATA_UNAVAILABLE, BLOCKED_BY_UPDATE, SET_GONE, EXERCISE_GONE, WORKOUT_GONE, SET_LIMIT, INVALID_VALUE, NEEDS_COMPLETED_SET, SET_NOT_RESTORED, GENERIC`. A tradução de exceção para código fica em `shared/.../ui/UiErrors.kt` (`object UiErrors`), e o VM a usa em todo `catch`. As falhas de abrir ou ler o banco e as preferências viram `LOCAL_DATA_UNAVAILABLE`, porque o VM já sabe a origem.
5. **Dois recordes na mesma série.** Se a série escolhida for ao mesmo tempo recorde de carga e de repetições, `SetUi.record` é `SetRecordUi(WEIGHT, previousKg)`. O aviso diz "recorde de carga", e o resumo continua listando os dois (`WorkoutUi.records`).
6. **`previousDate`** é o `startedAt` da sessão anterior. A `previousSets` sai da mesma `ExercisePerformance` que já monta o texto `previous`, no mesmo cache (`previousDescriptions` passa a guardar texto, séries e data).
7. **Fonte 2 da sugestão (esclarecimento do D10).** "A série imediatamente acima concluída hoje" é a série **mais próxima acima, do mesmo grupo** (aquecimento com aquecimento; trabalho, incluindo assistida, com trabalho), já concluída. *Por quê:* sem o grupo, a primeira série de trabalho receberia a carga do último aquecimento (40 kg no lugar de 80 kg), o oposto do objetivo.
8. **Regras de sugestão e pareamento no `:shared`.** `SetSuggestions` é Kotlin puro em `shared/.../ui/SetSuggestions.kt`, junto do contrato, com testes em `commonTest`, que rodam no host e no simulador iOS. A tela só guarda os campos apagados de propósito e chama as funções.
9. **Estabilidade do Compose no `:screens`:** o `screens/build.gradle.kts` lê o mesmo `app/compose-stability.conf` (`rootProject.layout.projectDirectory.file("app/compose-stability.conf")`), sem mover o arquivo. `PreviousSetUi` e `SetRecordUi` entram na lista (Task 5).
10. **`kotlinx-datetime` só no `:screens`** (`commonMain`), na versão estável do dia com alvos `iosArm64`/`iosSimulatorArm64` e compatível com Kotlin 2.4.21, registrada no commit. O `:shared` não muda de dependências.
11. **Pacotes do `:screens`:** os quatro da spec mais `com.mooscles.screens.format` (datas, durações, rótulos do catálogo), que três deles usam. Nenhum arquivo nos pacotes `com.mooscles` ou `com.mooscles.ui`.
12. **Formatos de hora e duração.** Relógio de 24 h nos dois idiomas ("ter., 29 out · 18:02–18:44"; "Tue, Oct 29 · 18:02–18:44"), porque ler a preferência de 12/24 h do sistema seria código de plataforma fora da lista permitida. Tempo do treino: "42:18", e "1:02:18" a partir de 1 h. Duração por extenso: "42 min", e "1 h 12 min" a partir de 60 min.
13. **Janela de constância:** treinos finalizados cujo `startedAt`, no fuso do aparelho, cai entre hoje − 27 e hoje (28 dias de calendário, hoje incluído).
14. **`restoreSet` passa pelo `trainingGate`** com `assertTrainingMayStart()`, como o `updateSet`, porque pode regravar uma série concluída. Toda recusa do repositório (exercício removido, treino finalizado, id já existente) é `DataError.RESTORE_UNAVAILABLE`, que vira `UiError.SET_NOT_RESTORED`; o limite de 100 continua `SET_LIMIT`.
15. **O aviso de recorde espera a edição terminar** (uma das duas opções da spec): enquanto houver uma linha em edição, o `PrToast` fica na fila e aparece quando ela sai da edição.
16. **Renomear treino** é uma `MBottomSheet` com `MTextField` e "Salvar", porque o `MDialog` não tem campo.
17. **Skeleton por exercício da rotina:** o `:app` embrulha o `MoosclesActions` passado à tela Treinar num decorador (`object : MoosclesActions by actions`) que guarda, em `rememberSaveable`, o `routineId` do último `startWorkout`. A rota `workout` recebe `skeletonCards = routine.exercises.size` (1 para treino vazio). Nenhuma tela antiga muda de assinatura.
18. **Tags de teste mantidas:** a barra de abas nova mantém `app-footer`, `tab-routines`, `tab-library`, `tab-history` e `tab-records`, para que os testes de Configurações e da galeria continuem valendo.
19. **O snackbar global do `MoosclesApp` não aparece nas rotas `workout` e `workoutDetail/{id}?summary=true`.** Lá, a tela nova mostra o texto localizado do `UiError` e chama `clearError()`.
20. **Contador de recomposição:** `internal val LocalRecompositionProbe = staticCompositionLocalOf<((String) -> Unit)?> { null }` e `@Composable internal fun RecompositionProbe(key: String)` (um `SideEffect` que chama o probe, nulo em produção). Cards, linhas, relógio, dock, barra e abas chamam o probe com chaves fixas (`card-<id>`, `row-<id>`, `clock`, `dock`, `bar`, `tabs`).
21. **"Descanso concluído" só depois de uma transição vista.** O dock e o expandido mostram o estado de fim por `timing.timerDoneHold` só quando esta composição viu o timer passar de correndo a terminado. Um `finished` que já estava no estado ao abrir não mostra nada.
22. **Teclado injetável.** A rota do treino e a `AppShellBottom` recebem `imeInsets: WindowInsets = WindowInsets.ime`, como o `WorkoutBottomBar` de hoje. Os testes injetam `WindowInsets(bottom = 300.dp)` quando o S3 mostrar que o simulador não abre teclado.
23. **Timer expandido é um `Dialog` de tela cheia** (`usePlatformDefaultWidth = false`), que já prende o foco e fecha com o voltar nas duas plataformas, sem `BackHandler` comum.
24. **Ações da notificação** passam por uma classe do `:app`, `RestTimerActions(gate, updates, timer)`, com a mesma regra de cada ação no VM: ±15 s com `trainingGate` + `assertTrainingMayStart()`; pular sem o gate, seguido de `onTrainingChanged()`. Uma recusa no receiver não mostra nada (não há tela): a notificação fica como estava.
25. **Abrir o treino pela notificação** usa `AppViewModel.workoutRequest: StateFlow<Long>` e `openWorkout()` no `:app`, espelhando o `openUpdateSettings`, e chega ao `MoosclesApp` pelo parâmetro novo `workoutRequest: Long = 0L`. O contrato comum não muda por isso.
26. **Build `preview` (D24):** `UpdateManager` ganha `enabled: Boolean = BuildConfig.UPDATER_ENABLED`; com `false`, não agenda trabalho, não procura nem baixa. O nome "Mooscles Preview" vem de `app/src/preview/res/values/strings.xml`. `debug` e `performance` têm `UPDATER_ENABLED = true`.
27. **`ActiveWorkoutFlowTest` roda duas vezes** (claro a 1,0 e escuro a 2,0), o que cobre os dois temas e as duas escalas com metade do tempo do simulador. A varredura de acessibilidade (Task 29) cobre as quatro combinações nos estados principais.
28. **Sem versão nova:** `versionCode` 16 e `versionName` 1.4.5 não mudam (como nas fases 1 e 2).

## Global Constraints

- **Dados intactos.** Room na v5, sem entidade, coluna nem migração nova (`shared/schemas` sem mudança, conferido pelo CI). Nenhuma chave nova, renomeada ou com outro tipo em `local_preferences` ou `app_updates`. `applicationId = com.mooscles`, `versionCode` 16, `versionName` 1.4.5, a assinatura, o `update.json`, `ReleaseUrls.REPOSITORY`, o canal `rest` e a notificação de id 1 não mudam. Escrita nova só `restoreSet`, numa `writeTransaction`, **sem `catch` dentro**.
- **Dependências:** entra só o `kotlinx-datetime` (no `:screens`); nos testes do `:screens`, as mesmas do `:designsystem` mais `kotlinx-coroutines-test` 1.11.0. Nada de Material 3 no `:screens`, Lottie, Coil, Vico ou Roborazzi. Nenhuma versão existente muda (diff do `debugRuntimeClasspath` na Task 9).
- **APK debug:** crescimento até 1 MiB (1.048.576 bytes) sobre a `main` de depois da fase 2 (`4f2911c`), medido por `tools/check-apk-budget.ps1` com empacotamento do zero; fontes até 900 KiB e fontes + ícones até 1 MiB, como na fase 2.
- **Um design só:** toda tela, componente e texto novo em `commonMain`. Código de plataforma só para háptico, movimento reduzido, idioma, codificação de PNG e o que é de sistema (notificação, compartilhamento). Datas, meses e dias da semana sem formatador da plataforma. `grep -rnE "\b(java|javax|android)\.|System\.|String\.format" screens/src/commonMain` volta vazio.
- **Tokens:** nenhum literal de cor, medida ou duração no `:screens` (`tools/check-design-tokens.ps1` passa a cobrir `screens/src/commonMain/kotlin/com/mooscles/screens`). Medida nova vira token no `:designsystem`, com teste.
- **Acessibilidade:** alvos de 48 dp, 56 dp nas ações do treino (check, stepper, ±15 s, pular, adicionar série, finalizar) e 8 dp entre alvos; nada abaixo de 12 sp nem de peso 400; só os 50 pares de cor medidos da fase 2 (par novo entra no `ContrastTokensTest`); nenhum estado só por cor; todo botão de ícone com rótulo; nenhum número cortado, provado por `assertShownWhole` a 1,0, 1,3 e 2,0 em 320 e 412 dp, **e a 2,01 no Android** (o iOS escala a fonte de forma linear, e o Android abaixo de 2x não: foi assim que o "187.5" cortado da fase 2 escapou do emulador).
- **Textos:** inglês base e pt-BR, voz da fase 2 (direto, "você", um verbo no botão, sem exclamação nem emoji). Todo texto novo do `:screens` em `screens/src/commonMain/composeResources/values*/strings_*.xml`, os de componente no `strings_components.xml` do `:designsystem`, os de notificação em `app/src/main/res/values*/rest_strings.xml`. O 0 fica no plural (`pluralCount`). Nomes do catálogo continuam em inglês (exceção registrada). As telas novas nunca mostram `UiState.error` cru.
- **Regras de tela:** o tick do timer nunca é lido no escopo da lista; o relógio do treino é um composable próprio; o estado de tela em `rememberSaveable` com a identidade real (`set.id`, `entry.id`); toda escrita pela `MoosclesActions`.
- **Háptico:** a tabela da spec ("Háptico"), sem nenhum outro momento. O de recorde vem depois do de concluir, a única sequência dupla.
- **Lições da fase 2 que viram regra:**
  - testes de UI com `androidx.compose.ui.test.v2.runComposeUiTest`, que enfileira tarefas (espere explicitamente quando preciso);
  - o `BasicTextField` é o próprio nó de mescla: nos testes de campo, use `useUnmergedTree = true`;
  - `hasVisualOverflow` dá falso positivo: meça números com o `assertShownWhole` (copiado para `screens/src/uiTest` na Task 9);
  - animação de entrada roda uma vez só: um flag em `rememberSaveable` impede repetir ao girar a tela ou ao rolar a linha para fora e de volta.
- **Processo:**
  - durante a task, rode só os testes da área; o CI roda a suíte inteira nos checkpoints;
  - **todo `uiTest` novo roda no emulador `Mooscles_Pixel8_API35` dentro da própria task** (compilar não é prova): `./gradlew :<módulo>:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>` (confira o nome da task com `./gradlew :<módulo>:tasks --all | grep -i devicetest`). Esperado: `BUILD SUCCESSFUL` e o XML em `<módulo>/build/outputs/androidTest-results/` sem falhas;
  - no máximo **um implementador usa o emulador por vez**, e nenhum Gradle roda enquanto um agente dirige a UI do emulador por `adb`; com duas lanes em paralelo, cada Gradle usa `--max-workers=2`;
  - toda task que acrescenta testes sobe o mínimo do conjunto no `ci.yml` pelo número de testes novos, com a linha por classe no comentário: `MIN_HOST_TESTS` (host de `:app`, `:shared`, `:designsystem` e `:screens`), `MIN_IOS_TESTS` (`:shared` no simulador), `MIN_DS_IOS_TESTS` e `MIN_SCREENS_IOS_TESTS` (novo, Task 9). Um teste aposentado só desconta com o motivo no commit. Quando duas tasks paralelas sobem a mesma linha, o controlador resolve o conflito somando;
  - o `compat-reviewer` (`.claude/agents/compat-reviewer.md`) roda e dá `SEGURO` antes do commit nas Tasks 4, 5, 6, 8, 9, 25, 27 e 32;
  - o iOS só se prova no CI (este Mac não tem Xcode); nunca desinstale o app de um aparelho do dono; desligue a rede do emulador (`svc wifi disable`, `svc data disable`) antes de instalar APKs de teste.
- Código, comentários, KDoc e commits em inglês. Plano e relatórios em pt-BR. Commits terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **Evidências:** tudo vai para `docs/superpowers/reports/2026-10-29-phase3-verificacao/` (a pasta nasce na Task 9, com o `deps-before.txt`; as tasks seguintes reutilizam a mesma pasta; ver a nota F27).

## Review Focus

1. **Girar a tela ou o sistema matar o processo com uma linha em edição e o toast de desfazer aberto.** O rascunho, a linha em edição, os campos apagados de propósito e o desfazer voltam (`rememberSaveable`); nenhum valor se perde. Coberto na Task 15 por `SetEditingTest.editingDraftAndClearedFieldsSurviveRecreation` e na Task 16 por `SetTypeAndRemovalTest.undoSurvivesRecreation`.
2. **Rolar uma linha com recorde para fora da tela e de volta.** O selo continua, sem tocar o háptico de novo, sem repetir o aviso e sem reanimar. Coberto na Task 21 por `RecordReactionTest.scrollingARecordRowAwayAndBackDoesNotReplay`.
3. **O mesmo exercício duas vezes no treino.** O selo fica só na série que o `WorkoutCalculations` escolheu, mesmo que a outra instância tenha uma série igual, e cada instância mostra o "anterior" pareado com as próprias séries. Coberto na Task 5 por `WorkoutPresentationTest.recordMarksOneSetAcrossTwoEntriesOfTheSameExercise` e na Task 17 por `ExerciseListTest.repeatedInstancesKeepTheirOwnValues`.
4. **Treino vazio (`startWorkout(null)`) sem nenhum exercício.** O cabeçalho mostra "0 de 0 séries", a lista só tem "Adicionar exercício", e "Finalizar" recusa com o toast, sem diálogo. Coberto na Task 19 por `ActiveWorkoutScreenTest.emptyActiveWorkoutShowsOnlyAddExerciseAndBlocksFinish`.
5. **Ação da notificação com o processo frio ou com uma atualização instalando.** O ±15 s chega pelo receiver sem Activity, passa pelo `trainingGate` e é recusado (sem mudar o timer) enquanto a fase do updater for `INSTALLING`. Coberto na Task 25 por `RestTimerActionsHostTest.notificationActionIsRefusedWhileAnUpdateInstalls` e `RestProgressNotificationHostTest.recreatedWhenTheProcessReturnsInTheBackground`.

## Destino dos testes atuais do treino ativo

Regra: um teste cujo alvo continua no código fica como está; um cujo alvo sai é portado (nome novo na task indicada) ou aposentado com o motivo abaixo. A Task 24 apaga os testes portados no mesmo commit em que tira o código antigo.

| Teste atual | Destino |
| --- | --- |
| `WorkoutInputHostTest.restCanReturnToOriginalValueBeforePersistenceEmits`, `numericTimerTypingKeepsColonAndAdvancesSeconds`, `invalidInputDoesNotModifyLegacyHistoricalDuration` | ficam (o `RestDurationField` continua no editor de rotina) |
| `WorkoutInputHostTest.timerIncreaseButtonIsDisabledAtFiveMinutes` | Task 20, `WorkoutTimerTest.plusFifteenIsDisabledAtFiveMinutes` |
| `WorkoutInputHostTest.clearedIncompleteRepsStayBlankWhenWeightChanges` | Task 14, `WorkoutSetRowTest.clearedRepsStayBlankWhenTheLoadChanges` |
| `WorkoutInputHostTest.blankWeightCompletesAsBodyweightAndStaysBlankAfterEditorRestoration` | Task 14, `WorkoutSetRowTest.addedWeightZeroCompletesAndSurvivesRestoration` |
| `WorkoutInputHostTest.restTimerStaysVisibleWhileLongWorkoutScrollsOnSmallScreen` | Task 20, `WorkoutTimerTest.dockStaysVisibleWhileALongWorkoutScrolls` |
| `WorkoutLazyRowsHostTest.aSingleLargeExerciseOnlyComposesVisibleSetInputs` | Task 17, `ExerciseListTest.largeExerciseComposesOnlyVisibleRows` |
| `WorkoutLazyRowsHostTest.repeatedExerciseInstancesKeepTheirOwnValuesAndCompletionAfterReorderAndScroll` | Task 17, `ExerciseListTest.repeatedInstancesKeepTheirOwnValues` |
| `WorkoutLazyRowsHostTest.blankAndInvalidDraftsSurviveDisposalBeforePersistenceEchoes`, `aDisposedRowRestoresWeightUsingTheCurrentDisplayUnitWithoutRoundingItsStoredKg`, `rapidCrossFieldEditsAndDelayedOlderEchoDoNotRevertCompletionOrOtherValues`, `acknowledgedFieldsAcceptExternalChangesWithoutErasingAnInvalidLocalDraft` | Task 15, `SetEditingTest.invalidDraftIsNotSentAndNotLost`, `unitSwitchConvertsTheDraftFromExactKg`, `lateEchoDoesNotEraseANewerDraft`, `acknowledgedFieldsAcceptExternalChanges` |
| `InlineRestEditorHostTest` 1–3 (`WorkoutExerciseCard` antigo, que fica para o histórico) | ficam |
| `InlineRestEditorHostTest.activeWorkoutDialogSurvivesLazyCardDisposalAndClosesIfItsExerciseIsRemoved` | Task 18, `RestSheetTest.sheetSurvivesCardDisposalAndClosesWhenItsExerciseIsRemoved` |
| `InlineRestEditorHostTest.progressSemanticsReflectRunningPausedAdjustedResetAndCompletedSnapshots` | Task 20, `WorkoutTimerTest.progressSemanticsFollowRunningPausedAdjustedResetAndDone` |
| `InlineRestVisualHostTest.nativePreviewsShowInlineRestLabelProgressAndFooterInConstrainedViewport` | aposentado se usar `TimerPanel` ou `WorkoutBottomBar` (saem); a prova visual passa às capturas da Task 31 e aos testes da Task 20. Se só usar o `WorkoutExerciseCard`, fica |
| `TimerKeyboardInsetsHostTest.imeMovesTheWholeTimerOnceWhileFooterAndCountdownStayUnchanged` | Task 24, reapontado para a `AppShellBottom` (a spec diz que continua valendo) |
| `RestTimerHostTest` (3), `DiscardNavigationHostTest` (3), `UiFlowHostTest` (1) | Task 24, mesmos nomes, sobre a casca e as telas novas |
| `androidTest/UiFlowTest`, `androidTest/UiFeatureSmokeTest` | Task 24 (tags novas, compila), rodados no emulador na Task 30 |
| `RoutineResumeHostTest`, `HistoryRecordsHostTest`, `WorkoutSharePreviewHostTest`, `SettingsScreenHostTest`, `Gallery*HostTest` | ficam (tela Treinar, detalhe antigo e Configurações não mudam; tags mantidas) |

---

### Task 1: Spike S1, cartão de compartilhamento em código comum

**Files (branch `spike/phase3-s1`, nunca mesclada):**
- Create: `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/spike/SpikeShareCard.kt`, `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/spike/ShareCardSpikeTest.kt`, `designsystem/src/androidDeviceTest/kotlin/com/mooscles/designsystem/spike/ShareCardPngSpikeTest.kt`

**Interfaces:**
- Produces (só no spike): `fun paintSpikeCard(card: SpikeCard, measurer: TextMeasurer, styles: SpikeCardStyles, density: Density): List<ImageBitmap>`, que desenha com `CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), size) { … }` e `drawText(measurer, …)` páginas de 1080 px de largura, fundo `MoosclesColors.Dark.bgBase`, marca "MOOSCLES", nome e data, Duração · Séries · Volume em `numberFamily()` com `tnum`, recordes em `prSolid` e a lista de exercícios. `SpikeCardStyles` carrega os `TextStyle` resolvidos na composição (fontes do `:designsystem`); o desenho roda fora dela.
- Produces (para a Task 3): a decisão S1 (**A**, cartão comum, ou **B**, o plano B do D20).

- [ ] **Step 1:** Criar `spike/phase3-s1` a partir da `main`. Montar o fixture `SpikeCard`: 6 exercícios, um com nome "Agachamento búlgaro com halteres e pausa", 2 recordes, 18 séries.
- [ ] **Step 2: Testes do spike** (cada um imprime `SPIKE …`):
  - `ShareCardSpikeTest.paintsOffTheMainThreadAt1080`: com `withContext(Dispatchers.Default)`, cada página tem `width == 1080` e `height > 0`, sem exceção;
  - `ShareCardSpikeTest.textIsDrawn`: na faixa da marca, algum pixel difere de `bgBase`;
  - `ShareCardSpikeTest.layoutSignature`: imprime `SPIKE layout pages=<n> rows=<linhas por exercício>`, sem asserção;
  - `ShareCardPngSpikeTest.pngMatchesThePreviewBitmap`: `Bitmap.compress(PNG)`, depois `BitmapFactory.decodeByteArray`, tem os mesmos pixels de `ImageBitmap.readPixels`, exatamente;
  - `ShareCardPngSpikeTest.paintAndEncodeUnder500ms`: mediana de 5 execuções (desenho + PNG) < 500 ms;
  - `ShareCardPngSpikeTest.mainThreadStaysResponsive`: durante o desenho em `Dispatchers.Default`, `Looper.getMainLooper().isCurrentThread` é falso na thread do desenho, e um `Runnable` postado na thread principal roda em menos de 100 ms.
- [ ] **Step 3:** Rodar `./gradlew :designsystem:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.package=com.mooscles.designsystem.spike` no emulador e guardar o XML e as linhas `SPIKE` do logcat para o relatório da Task 3.
- [ ] **Step 4:** Commit local `spike: draw the share card with common canvas APIs` (sem push: a Task 3 junta os spikes e faz um push só).
- [ ] **Regra de decisão (aplicada na Task 3, com o resultado do iOS):** **A** se, no Android, o PNG for igual ao bitmap, a mediana ficar abaixo de 500 ms e a thread principal responder; e, no iOS, os três testes comuns passarem com a mesma `layoutSignature` do Android (mesmo número de páginas e de linhas por exercício). Senão, **B**: a Task 26 segue o plano B do D20.

### Task 2: Spike S2, `MTextField` lendo o rótulo antes do valor (#11)

**Files (branch `spike/phase3-s2`, nunca mesclada):**
- Modify: `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/components/MTextField.kt` (parâmetro só do spike `labelStrategy: SpikeLabelStrategy = Today`)
- Create: `designsystem/src/androidDeviceTest/kotlin/com/mooscles/designsystem/spike/TextFieldNodeSpikeTest.kt`, `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/spike/TextFieldEditSpikeTest.kt`

**Interfaces:**
- Produces (para a Task 3 e a Task 11): a decisão S2, com a estratégia escolhida (ou nenhuma).

- [ ] **Step 1:** Criar `spike/phase3-s2` a partir da `main`. Estratégias: `Today`; `ContentDescription` (o nó editável com `contentDescription = label` e o valor como texto); `StateDescription` (`contentDescription = label`, `stateDescription = valor`). Uma quarta pode entrar se a leitura do `AndroidComposeViewAccessibilityDelegateCompat` sugerir, com o motivo no commit.
- [ ] **Step 2: Testes.**
  - `TextFieldNodeSpikeTest.recordsTheNodeOfEachStrategy`: numa `ComponentActivity`, lê o `AccessibilityNodeInfo` do campo por `UiAutomation.rootInActiveWindow` e imprime `SPIKE <estratégia> text=… hint=… contentDescription=… stateDescription=… editable=…`;
  - `TextFieldEditSpikeTest.everyStrategyStaysEditable` (Android e iOS): para cada estratégia, `performTextInput("2:30")` muda o valor, a ação `SetText` existe e a ação de IME chama de volta.
- [ ] **Step 3: TalkBack.** Conferir se a imagem do emulador tem TalkBack (`adb shell pm list packages | grep -i talkback`). Se tiver, ligar com `settings put secure enabled_accessibility_services …`, ativar "Mostrar saída de fala" nas opções de desenvolvedor do TalkBack e tentar mover o foco pelo teclado (Alt+setas). Registrar a fala de cada estratégia, ou que o TalkBack não pôde ser conduzido. Nenhum Gradle roda em outra lane durante este step.
- [ ] **Step 4:** Rodar `./gradlew :designsystem:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.package=com.mooscles.designsystem.spike`. Commit local `spike: label-first reading strategies for MTextField`.
- [ ] **Regra de decisão (aplicada na Task 3):** uma estratégia é **aprovada** só se o TalkBack real disser o rótulo antes do valor, a edição continuar igual no Android e no iOS (`everyStrategyStaysEditable` verde no CI) e nada mais da leitura mudar. Se o TalkBack não puder ser conduzido, **nada muda no `MTextField`**: o #11 fica registrado de novo, e a melhor estratégia pela árvore (rótulo em `contentDescription`, valor como texto, editável) entra no item 8 da lista de TalkBack da Task 31 como "candidata do S2".

### Task 3: Spike S3, teclado no iOS; push único dos spikes e decisões

**Files:**
- Branch `spike/phase3` (a partir da `main`, com merge de `spike/phase3-s1` e `spike/phase3-s2`). Create: `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/spike/ImeSpikeTest.kt`
- Branch `feat/phase3-active-workout`. Create: `docs/superpowers/reports/AAAA-MM-DD-phase3-spikes.md` (data do dia)

**Interfaces:**
- Produces (para as Tasks 11, 15, 20, 26): as decisões S1 (A ou B), S2 (estratégia ou nenhuma) e S3 (teclado real nos testes iOS, ou insets injetados).

- [ ] **Step 1: Teste do S3.** `ImeSpikeTest`:
  - `focusingAFieldRecordsTheImeInset`: uma coluna rolável com um `BasicTextField` no fim e uma caixa de 56 dp presa acima de `WindowInsets.ime`; `requestFocus()` no campo, espera de 2 s; imprime `SPIKE ime bottom=<px> fieldVisible=<bool>`; asserção só de que o campo ficou focado;
  - `injectedInsetsPlaceTheCompactDockAboveThem`: com `imeInsets = WindowInsets(bottom = 300.dp)`, o fundo da caixa fica em `altura − 300 dp` (±1 px) e, depois de `bringIntoView`, o campo fica inteiro acima da caixa.
- [ ] **Step 2:** Rodar o `ImeSpikeTest` no emulador. Push de `spike/phase3` (**Push**) e `gh run watch -R pedroyuuki/Mooscles`. Baixar os XML do `iosSimulatorArm64Test` do `:designsystem` e as linhas `SPIKE`.
- [ ] **Step 3: Decidir.**
  - S1 e S2: as regras das Tasks 1 e 2.
  - S3: se `ime bottom > 0` no simulador, os testes iOS das Tasks 15 e 20 usam o teclado real; senão, usam os insets injetados, e o teclado real no iOS fica "não verificado".
  - Um controle do spike que não reprova nunca (por exemplo, `textIsDrawn` passando sem desenhar nada) invalida o teste: corrija antes de decidir.
- [ ] **Step 4: Relatório** em `feat/phase3-active-workout`: tabelas por plataforma, links do run, SHA do `spike/phase3`, as três decisões, o que **não** foi provado (a qualidade visual do cartão, a fala real do TalkBack se ele não rodou, o teclado num iPhone). Commit `docs(report): phase 3 spikes` (sem push; vai no próximo checkpoint).

### Task 4: Erros tipados, `UiError` e `errorCode`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mooscles/data/DataErrors.kt`, `shared/src/commonMain/kotlin/com/mooscles/ui/UiErrors.kt`
- Modify: `shared/.../data/EditRules.kt`, `shared/.../data/WorkoutRepository.kt` (todo `require`/`requireNotNull`, menos o `check` do `importBackup`), `shared/.../updates/UpdateCoordinator.kt`, `app/src/main/java/com/mooscles/updates/UpdateManager.kt:83-85`, `shared/.../timer/RestTimer.kt`, `app/src/main/java/com/mooscles/timer/RestTimerController.kt` (`report()`), `shared/.../ui/UiContract.kt`, `shared/.../MoosclesViewModel.kt`, `.github/workflows/ci.yml`
- Test: `shared/src/commonTest/kotlin/com/mooscles/data/DataErrorsTest.kt`, `shared/src/commonTest/kotlin/com/mooscles/ui/UiErrorsTest.kt`, `app/src/test/java/com/mooscles/data/WorkoutRepositoryErrorTypesTest.kt`, `app/src/test/java/com/mooscles/AppViewModelErrorCodeHostTest.kt`

**Interfaces:**
- Produces: `DataError`, `DataRuleException` (decisão 2); `internal inline fun dataRequire(value: Boolean, error: DataError, lazyMessage: () -> String)` e `internal inline fun <T : Any> dataRequireNotNull(value: T?, error: DataError, lazyMessage: () -> String): T`.
- Produces: `class TrainingBlockedException(message: String) : IllegalArgumentException(message)` (decisão 3).
- Produces: `enum class RestTimerError { STORAGE_UNAVAILABLE }` e `val errorCode: StateFlow<RestTimerError?>` na interface `RestTimer`; `clearError()` limpa os dois.
- Produces: `enum class UiError` (decisão 4), `UiState.errorCode: UiError? = null` (último parâmetro), `object UiErrors { fun from(failure: Throwable): UiError; fun from(timer: RestTimerError): UiError }`. Mapeamento: `SET_NOT_FOUND`→`SET_GONE`; `WORKOUT_EXERCISE_NOT_FOUND` e `EXERCISE_NOT_FOUND`→`EXERCISE_GONE`; `WORKOUT_NOT_FOUND` e `WORKOUT_NOT_ACTIVE`→`WORKOUT_GONE`; `SET_LIMIT`→`SET_LIMIT`; `INVALID_VALUE`→`INVALID_VALUE`; `NO_COMPLETED_SET`→`NEEDS_COMPLETED_SET`; `RESTORE_UNAVAILABLE`→`SET_NOT_RESTORED`; `ROUTINE_NOT_FOUND` e `FOLDER_NOT_FOUND`→`GENERIC`; `TrainingBlockedException`→`BLOCKED_BY_UPDATE`; `CancellationException` (comando falho)→`SAVE_FAILED`; o resto→`GENERIC`.
- Produces: o VM preenche `errorCode` junto de `error` (o texto de hoje continua igual), e `clearError()` limpa os dois.

- [ ] **Step 1: Testes que falham.**
  - `DataErrorsTest.editRulesKeepTheirMessagesAndGainAType`, tabela `(chamada, tipo, mensagem)`: `EditRules.name("")` → `INVALID_VALUE`, "Enter a name."; `EditRules.workoutSetCount(101)` → `SET_LIMIT`, "A maximum of 100 sets is supported per exercise."; `EditRules.rest(301)` → `INVALID_VALUE`, "Rest must be between 0:00 and 5:00."; `EditRules.workoutSet(<completed, reps 0>)` → `INVALID_VALUE`, "Enter at least 1 rep before completing a set."; `EditRules.plannedSets(0)` → `INVALID_VALUE`, "Planned sets must be between 1 and 50."; `EditRules.routineSetCount(51)` → `SET_LIMIT`. Cada um também `assertIs<IllegalArgumentException>`.
  - `UiErrorsTest.mapsEveryDataErrorKind` (a tabela acima, todos os `DataError.entries`), `trainingBlockedMapsToBlockedByUpdate`, `cancellationIsSaveFailed`, `unknownExceptionIsGeneric`, `timerStorageMapsToTimerStorage`.
  - `WorkoutRepositoryErrorTypesTest` (Robolectric, Room real como os testes de `data/`): `missingSetUpdateIsSetNotFoundWithTheSameMessage` ("Set not found."), `finishingWithoutACompletedSetIsNoCompletedSet` ("Complete at least one set before finishing."), `theHundredAndFirstSetIsSetLimit`, `discardingAFinishedWorkoutIsWorkoutNotActive` ("Only active workouts can be discarded.").
  - `AppViewModelErrorCodeHostTest` (montado como o `AppViewModelCommandLoopHostTest`): `missingSetSetsTextAndCode` (`updateSet` de um id que não existe: `error == "Set unavailable"`, `errorCode == SET_GONE`); `installingUpdateBlocksWithTheSameText` (fase `INSTALLING`: `error == "An update is being installed. Training will be available again shortly."`, `errorCode == BLOCKED_BY_UPDATE`); `timerStorageFailureSetsTimerStorage`; `clearErrorClearsBoth`.
- [ ] **Step 2:** Rodar `./gradlew :shared:testAndroidHostTest --tests "*DataErrorsTest*" --tests "*UiErrorsTest*" :app:testDebugUnitTest --tests "com.mooscles.data.WorkoutRepositoryErrorTypesTest" --tests "com.mooscles.AppViewModelErrorCodeHostTest"` e ver falhar.
- [ ] **Step 3:** Implementar. Cada troca de `require` mantém a mensagem caractere por caractere. Os `requireNotNull` do VM ("Set unavailable", "Workout exercise unavailable", "Exercise unavailable", "Routine exercise unavailable", "Folder unavailable.") viram `dataRequireNotNull` com o tipo correspondente.
- [ ] **Step 4: Verificar.** O comando do Step 2 verde, mais `./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest --tests "com.mooscles.AppViewModel*" --tests "com.mooscles.data.*" --tests "com.mooscles.updates.*" --tests "com.mooscles.timer.*" :shared:compileCommonMainKotlinMetadata` verde (os testes atuais sem mudança de asserção).
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_IOS_TESTS` pelos testes novos. Rodar o `compat-reviewer` (`data/`, `timer/`, `updates/`; esperado `SEGURO`: mesmas mensagens, nada gravado). Commit `shared: typed data errors, timer error code and UiError`.

### Task 5: Recorde por série e "anterior" por série

**Files:**
- Modify: `shared/.../domain/WorkoutCalculations.kt` (`WeightPr` e `RepsPr`), `shared/.../WorkoutPresentation.kt`, `shared/.../ui/UiContract.kt`, `app/compose-stability.conf`, `.github/workflows/ci.yml`
- Test: `shared/src/commonTest/kotlin/com/mooscles/domain/WorkoutCalculationsTest.kt`, `WorkoutCalculationsBatchTest.kt` (as 3 construções das linhas 214, 269 e 270 ganham o `setId`), `shared/src/commonTest/kotlin/com/mooscles/WorkoutPresentationTest.kt`

**Interfaces:**
- Produces: `WeightPr.setId: String` e `RepsPr.setId: String`, último parâmetro, sem padrão. Em `weightPrs`, é o id do `best` de `maxWithOrNull(compareBy { weightKg }.thenBy { reps })`, que fica com o primeiro num empate total; em `repsPrs`, o `candidate.set.id` do `bestRepetitionSets`.
- Produces: `data class PreviousSetUi(val weightKg: Double, val reps: Int, val warmup: Boolean, val assistance: Boolean)`; `data class SetRecordUi(val kind: RecordKind, val previousKg: Double? = null, val previousReps: Int? = null)`; `SetUi.record: SetRecordUi? = null`; `WorkoutExerciseUi.previousSets: List<PreviousSetUi> = emptyList()` e `previousDate: Long? = null`. Todos como últimos parâmetros com padrão.
- Regras: `SetUi.record` é preenchido só para recordes batidos (`firstRecord == false`), na série cujo id é o `setId`, na entrada com o mesmo `exerciseId` e a mesma convenção; o de carga tem `previousKg = previousWeightKg`, o de repetições `previousReps`; os dois na mesma série seguem a decisão 5. `previousSets` são as séries de `ExercisePerformance.sets` (já `validCompleted`, em ordem de posição) da primeira sessão anterior (decisão 6).

- [ ] **Step 1: Testes que falham.**
  - `WorkoutCalculationsTest.weightPrCarriesTheChosenSetId` e `repsPrCarriesTheChosenSetId`; `WorkoutCalculationsBatchTest.batchAndPerWorkoutRecordsAgreeOnSetIds` (a equivalência atual, agora com o `setId`).
  - Em `WorkoutPresentationTest`, todos com um histórico finalizado e o treino ativo:
    - `weightRecordMarksExactlyTheChosenSet`: histórico 80 kg × 6; hoje 85 × 6, 87,5 × 6 e 87,5 × 6 concluídas. Só a segunda tem `record == SetRecordUi(WEIGHT, previousKg = 80.0)`;
    - `heavierTieBreaksByReps`: hoje 87,5 × 5 e 87,5 × 6. O selo fica na de × 6;
    - `repsRecordMarksTheFirstWithMostRepsAtThatLoad`: histórico 80 × 6; hoje 80 × 8 e 80 × 8. Só a primeira, com `SetRecordUi(REPS, previousReps = 6)`;
    - `firstRecordMarksNothing` (D18): sem histórico, nenhuma série com `record`;
    - `warmupAndAssistanceRules`: um aquecimento de 100 kg concluído fica sem `record`; uma série assistida não ganha recorde de carga, e um recorde de repetições assistido marca a série assistida;
    - `ulpToleranceIsNotARecord`: histórico 80.0; hoje `80.0` mais 2 ulp fica sem `record`;
    - `poundsDoNotChangeTheMarkedSet`: as mesmas marcas com `pounds = true`;
    - `recordMarksOneSetAcrossTwoEntriesOfTheSameExercise` (Review Focus 3): supino duas vezes no treino; a série 90 × 5 da segunda entrada é a única marcada, mesmo com uma 90 × 5 pendente na primeira;
    - `uncheckingTheRecordSetRemovesTheMark`;
    - `previousSetsAreTheLastSessionsCompletedSetsInOrder`: última sessão com 40 × 10 (aquecimento), 80 × 8, 80 × 7 (não concluída) e 82,5 × 6 dá `[PreviousSetUi(40.0, 10, true, false), PreviousSetUi(80.0, 8, false, false), PreviousSetUi(82.5, 6, false, false)]`, e `previousDate` igual ao `startedAt` dela; uma sessão mais antiga não aparece;
    - `noHistoryHasNoPreviousSets`: `emptyList()` e `null`;
    - `previousSetsAreReusedWhenOnlyTheActiveWorkoutChanges`: depois de mudar uma série do treino ativo, `previousSets` é a mesma instância (`assertSame`), prova de que nada refez a passada do histórico.
- [ ] **Step 2:** Rodar `./gradlew :shared:testAndroidHostTest --tests "*WorkoutPresentationTest*" --tests "*WorkoutCalculations*"` e ver falhar.
- [ ] **Step 3:** Implementar. Acrescentar `com.mooscles.ui.PreviousSetUi` e `com.mooscles.ui.SetRecordUi` ao `app/compose-stability.conf`, um por linha, sem curinga.
- [ ] **Step 4: Verificar.** O comando do Step 2 verde; `./gradlew :shared:testAndroidHostTest --tests "*WorkoutPresentationTimingTest*" --tests "*WorkoutCalculationsBatchTimingTest*" :app:testDebugUnitTest --tests "com.mooscles.AppViewModelRecordsHostTest" --tests "com.mooscles.HistoryRecordsHostTest" :app:assembleDebug` verde (os limites de tempo de hoje continuam).
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_IOS_TESTS`. Rodar o `compat-reviewer` (domínio e estabilidade; esperado `SEGURO`: nada é gravado). Commit `shared: mark the record set and expose the previous sets`.

### Task 6: `restoreSet` e descanso 0:00

**Files:**
- Modify: `shared/.../data/WorkoutRepository.kt`, `shared/.../ui/UiContract.kt` (`MoosclesActions`), `shared/.../MoosclesViewModel.kt`, `.github/workflows/ci.yml`
- Test: `app/src/test/java/com/mooscles/data/WorkoutSetRestoreTest.kt`, `app/src/test/java/com/mooscles/AppViewModelRestoreHostTest.kt`

**Interfaces:**
- Consumes: `DataRuleException`, `DataError.RESTORE_UNAVAILABLE`, `UiError.SET_NOT_RESTORED` (Task 4).
- Produces: `suspend fun WorkoutRepository.restoreSet(workoutExerciseId: String, position: Int, item: WorkoutSet)`: numa `writeTransaction`, recusa com `RESTORE_UNAVAILABLE` (exercício inexistente, treino não `ACTIVE`, `dao.set(item.id) != null`); `EditRules.workoutSetCount(n + 1)` e `EditRules.workoutSet(item)`; `p = position.coerceIn(0, n)`; desloca em +1 as posições ≥ `p` e insere `item.copy(workoutExerciseId = workoutExerciseId, position = p)` com o mesmo id e os mesmos valores. Sem `catch` dentro.
- Produces: `fun restoreSet(entryId: String, position: Int, set: SetUi)` em `MoosclesActions`; no VM, `act { trainingGate.withLock { assertTrainingMayStart(); repo.restoreSet(…) } }` (decisão 14).
- Produces: `updateSet` só chama `timer.start` quando `entry.restSeconds > 0`.

- [ ] **Step 1: Testes que falham.**
  - `WorkoutSetRestoreTest`: `restoreReinsertsTheSameIdAndValuesAtItsPosition` (A, B, C; remove B; restore B na 1: ordem A, B, C, e B com o mesmo id, reps, carga, `completed`, `warmup` e `assistance`); `restorePastTheEndGoesLast` (posição 7 com 2 séries); `restoreRefusesAFinishedWorkout`, `restoreRefusesARemovedExercise` e `restoreRefusesAnExistingId` (`RESTORE_UNAVAILABLE`, nada gravado); `restoreRefusesTheHundredAndFirstSet` (`SET_LIMIT`); `restoreValidatesValues` (reps −1: `INVALID_VALUE`); `failedRestoreLeavesPositionsUntouched`.
  - `AppViewModelRestoreHostTest`: `restoreSetWritesUnderTheGate`; `refusedRestoreSetsSetNotRestored` (`errorCode == SET_NOT_RESTORED`); `zeroRestDoesNotStartTheTimer` (descanso 0, descanso automático ligado: depois de concluir, `timer.ui.value.running == false`); `positiveRestStillStartsTheTimer`.
- [ ] **Step 2:** Rodar `./gradlew :app:testDebugUnitTest --tests "com.mooscles.data.WorkoutSetRestoreTest" --tests "com.mooscles.AppViewModelRestoreHostTest"` e ver falhar.
- [ ] **Step 3:** Implementar. O schema v5 não tem índice único em `workout_sets.position` (conferido no JSON exportado), então a ordem do deslocamento não importa; o `removeSet` de hoje já regrava as posições do mesmo jeito.
- [ ] **Step 4: Verificar.** O comando do Step 2 verde, mais `--tests "com.mooscles.data.*" --tests "com.mooscles.AppViewModel*" --tests "com.mooscles.timer.*"`.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS`. `compat-reviewer` (`data/`; esperado `SEGURO`: escrita com as regras do `EditRules`, schema igual). Commit `shared: restore a removed set and skip a zero rest`.

### Task 7: Regras de sugestão e pareamento do "anterior"

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mooscles/ui/SetSuggestions.kt`
- Test: `shared/src/commonTest/kotlin/com/mooscles/ui/SetSuggestionsTest.kt`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: `SetUi`, `PreviousSetUi` (Task 5); `LoadConvention` labels.
- Produces:
  - `enum class SetField { LOAD, REPS }`;
  - `data class SetSuggestion(val weightKg: Double? = null, val reps: Int? = null) { companion object { val None: SetSuggestion } }` (null = sem sugestão naquele campo);
  - `data class EffectiveSet(val weightKg: Double, val reps: Int)`;
  - `object SetSuggestions`:
    - `fun loadIsEmpty(weightKg: Double, convention: String): Boolean`: verdadeiro só para `weightKg == 0.0` com `convention` em `Total load`, `Per hand` e `One side`;
    - `fun pairPrevious(sets: List<SetUi>, previous: List<PreviousSetUi>): List<PreviousSetUi?>`: o n-ésimo aquecimento com o n-ésimo aquecimento, a n-ésima de trabalho (a assistida conta) com a n-ésima de trabalho; sem par, `null`;
    - `fun forExercise(sets: List<SetUi>, previous: List<PreviousSetUi>, convention: String, cleared: Set<Pair<String, SetField>>): List<SetSuggestion>`: para cada série pendente e cada campo vazio (reps 0; carga pela regra acima) que não está em `cleared`, a fonte 2 (decisão 7) e senão a fonte 3 (`pairPrevious`); série concluída ou campo preenchido dá `null`;
    - `fun effective(set: SetUi, suggestion: SetSuggestion): EffectiveSet`.

- [ ] **Step 1: Testes que falham** (`SetSuggestionsTest`):
  - `savedValuesAreNeverSuggestions`: 80 kg × 8 pendente dá `None`;
  - `zeroKgOnCommonLoadGetsTheLoadSuggestion`: para `Total load`, `Per hand` e `One side`, 0 kg recebe a carga da fonte;
  - `zeroKgOnAddedWeightAndAssistanceIsAValue`: `Added weight` e `Assistance` dão `weightKg == null`, com reps ainda sugeridas;
  - `aValueAboveZeroIsNeverReplaced`;
  - `theCompletedSetAboveWins`: série 1 82,5 × 8 concluída, série 2 vazia, anterior `[80 × 8, 80 × 8]` dá `SetSuggestion(82.5, 8)` na série 2;
  - `thePreviousSessionCoversTheFirstSet`: série 1 vazia dá `SetSuggestion(80.0, 8)`;
  - `pairingIsByKindThenOrder`: hoje `[W, W, N, N]`, anterior `[W 40 × 10, N 80 × 8, N 80 × 6]` dá `[W 40 × 10, null, N 80 × 8, N 80 × 6]`;
  - `assistedCountsAsWorking`;
  - `aWarmupAboveDoesNotFeedAWorkingSet` (decisão 7): aquecimento 40 × 10 concluído acima da primeira de trabalho vazia, anterior de trabalho 80 × 8: a sugestão é 80 × 8;
  - `poundsKeepTheExactSourceKg`: fonte 36.287389 kg dá `weightKg == 36.287389` (sem ida e volta por lb);
  - `aClearedFieldGetsNoSuggestion`: `cleared = {(s2, LOAD)}` dá `weightKg == null` e reps sugeridas;
  - `completedSetsGetNoSuggestion`;
  - `effectiveUsesSuggestionsOnlyForEmptyFields`;
  - `nothingWithoutASource`.
- [ ] **Step 2:** Rodar `./gradlew :shared:testAndroidHostTest --tests "*SetSuggestionsTest*"` e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** O mesmo comando verde e `./gradlew :shared:compileCommonMainKotlinMetadata`.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_IOS_TESTS`. Commit `shared: set suggestion and previous pairing rules`.

### Task 8: `InProcessRestTimer`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mooscles/timer/InProcessRestTimer.kt`, `shared/src/commonTest/kotlin/com/mooscles/timer/InMemoryPreferencesStore.kt`
- Test: `shared/src/commonTest/kotlin/com/mooscles/timer/InProcessRestTimerTest.kt`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: `RestTimer`, `RestTimerError` (Task 4); `RestTimerPersistence(store, now)`, sem mudar o formato.
- Produces: `class InProcessRestTimer(persistence: RestTimerPersistence, scope: CoroutineScope, activeWorkoutId: suspend () -> String?, now: () -> Long, tickMillis: Long = 250L) : RestTimer`. O `ui` combina os estados gravados com um ticker de corrotina, como o `RestTimerController`; ao chegar a 0, chama `claimCompletion` uma vez; falhas de armazenamento dão o mesmo texto de erro do controller e `errorCode == STORAGE_UNAVAILABLE`. Sem alarme nem notificação.
- Produces: `class InMemoryPreferencesStore : DataStore<Preferences>` (só de teste), com `var failWrites: Boolean`.

- [ ] **Step 1: Testes que falham** (`runTest`, relógio do `testScheduler`; os casos espelham o `RestTimerControllerTest`):
  - `startCountsDownFromTheDuration`: `start("Bench", 120)` dá 120; depois de 30 s virtuais, 90;
  - `pauseFreezesAndResumeContinues`;
  - `plusFifteenIsCappedAtFiveMinutes`: com 290 restantes, +15 dá 300;
  - `minusFifteenNeverGoesBelowZero`;
  - `skipEndsTheIntervalWithoutCompletion`, `resetRestartsTheInterval`;
  - `reachingZeroCompletesOnce`: `finished == true`, uma conclusão só;
  - `clearForSessionEndRemovesTheTimer`;
  - `noActiveWorkoutIgnoresStart`;
  - `writesTheSameKeysAsTheController`: depois de `start`, as chaves do store são exatamente as `timer_*` que o `RestTimerPersistence` grava;
  - `storageFailureSetsTheErrorCode`.
- [ ] **Step 2:** Rodar `./gradlew :shared:testAndroidHostTest --tests "*InProcessRestTimerTest*"` e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** O mesmo comando verde, mais `--tests "*RestTimerStateTest*"` e `:app:testDebugUnitTest --tests "com.mooscles.timer.*"`.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_IOS_TESTS`. `compat-reviewer` (`timer/`; esperado `SEGURO`: mesmo formato e mesmas chaves). Commit `shared: in-process rest timer over the same persistence`. **Push** (checkpoint das Tasks 3–8). Conferir com `gh run watch` que `host` e `ios` ficam verdes.

### Task 9: Módulo `:screens`, CI, orçamento de APK e rótulos do catálogo

**Files:**
- Modify: `settings.gradle.kts` (`include(":app", ":shared", ":designsystem", ":screens")`), `app/build.gradle.kts` (`implementation(project(":screens"))`), `build.ps1` (`:screens:testAndroidHostTest` no `$Tasks` padrão), `tools/check-design-tokens.ps1` (caminho padrão `screens/src/commonMain/kotlin/com/mooscles/screens`), `tools/apk-budget.json`, `.github/workflows/ci.yml`
- Create: `screens/build.gradle.kts`; `screens/src/commonMain/composeResources/values/strings_catalog.xml` e `values-pt-rBR/strings_catalog.xml`; `screens/src/commonMain/kotlin/com/mooscles/screens/format/CatalogLabels.kt`; `screens/src/commonMain/kotlin/com/mooscles/screens/workout/EquipmentIcons.kt`; `screens/src/commonMain/kotlin/com/mooscles/screens/workout/RecompositionProbe.kt`
- Test: `screens/src/androidHostTest/kotlin/com/mooscles/screens/StringsParityTest.kt` (como o do `:designsystem`), `screens/src/commonTest/kotlin/com/mooscles/screens/workout/EquipmentIconsTest.kt`, `screens/src/uiTest/kotlin/com/mooscles/screens/format/CatalogLabelsTest.kt`; helpers `screens/src/uiTest/kotlin/com/mooscles/screens/ScreensTestScaffold.kt`, `TextFit.kt` (cópia do `assertShownWhole` do `:designsystem`), `RecordingActions.kt`

**Interfaces:**
- Produces: o módulo como o `designsystem/build.gradle.kts` (namespace `com.mooscles.screens`, `compileSdk` 37, `minSdk` 26, `androidResources`, `withHostTest`, `withDeviceTestBuilder`, `iosArm64`, `iosSimulatorArm64`, `src/uiTest/kotlin` no `iosTest` e no `androidDeviceTest`), com `api(project(":shared"))`, `api(project(":designsystem"))`, `compose.resources { packageOfResClass = "com.mooscles.screens.resources"; generateResClass = always }` e a estabilidade da decisão 9, declarada como input das tasks de compilação.
- Produces: `object EquipmentIcons { fun forEquipment(label: String): ImageVector }`: `Barbell`→`MIcons.Barbell`, `Dumbbell`→`Dumbbell`, `Kettlebell`→`Kettlebell`, `Cable`→`Cable`, `Bodyweight`→`Body`, `Smith machine`, `Selectorized machine` e `Plate-loaded machine`→`Machine`, `Other` e desconhecido→`Plate`.
- Produces: `@Composable fun muscleLabel(label: String): String`, `equipmentLabel(label: String): String`, `conventionLabel(label: String): String`, `conventionCaption(label: String): String?` (null em `Total load`) e `conventionExplanation(label: String): String`, que acham o enum pelo `label` em inglês e leem os recursos. pt-BR: Peito, Costas, Ombros, Bíceps, Tríceps, Quadríceps, Posteriores de coxa, Glúteos, Panturrilhas, Abdômen, Antebraços, Corpo inteiro; Barra, Halter, Kettlebell, Polia, Peso corporal, Smith, Máquina de placas, Máquina de anilhas, Outro; Carga total, Por mão, Um lado, Peso adicional, Assistência. Legendas: "Por mão: registre a carga de um halter." / "Per hand: log one dumbbell's load."; "Um lado: registre a carga usada em um lado." / "One side: log the load used on one side."; "Peso adicional: deixe 0 para só o peso do corpo." / "Added weight: leave 0 for bodyweight only."; "Assistência: registre a assistência da máquina." / "Assistance: log the machine's assistance." Textos completos, en e pt: "Log the total external load; volume = logged load × reps." / "Registre a carga externa total; volume = carga registrada × repetições."; "Log one dumbbell's load and reps. Volume is per hand; it is never doubled." / "Registre a carga e as repetições de um halter. O volume é por mão; nunca é dobrado."; "Log the total external load used on one side and the reps for that side. Volume is per side; it is never doubled." / "Registre a carga externa usada em um lado e as repetições desse lado. O volume é por lado; nunca é dobrado."; "Leave the load at 0 for bodyweight, or log the added external weight. Bodyweight is not counted in weight moved." / "Deixe a carga em 0 para só o peso do corpo, ou registre o peso externo adicional. O peso do corpo não entra no peso movido."; "Log the assistance separately. Assistance earns no external-weight record and is not counted in weight moved." / "Registre a assistência à parte. Assistência não gera recorde de carga externa nem entra no peso movido."
- Produces: `LocalRecompositionProbe` e `RecompositionProbe(key)` (decisão 20).
- Produces (testes): `fun ComposeUiTest.withScreen(dark: Boolean = false, fontScale: Float = 1f, reducedMotion: Boolean = false, haptics: MoosclesHaptics = RecordingHaptics(), locale: String? = null, size: DpSize? = null, probe: ((String) -> Unit)? = null, content: @Composable () -> Unit)`; `class RecordingActions : MoosclesActions { val calls: List<String> }`, que grava `"<método>:<arg1>,<arg2>,…"` com o `toString()` de cada argumento (por exemplo `"updateSet:s2,8,82.5,true,false,false"`), e `"<método>:"` quando não há argumento (`"skipTimer:"`, `"clearError:"`); `fun assertShownWhole(layout: TextLayoutResult, where: String = "")`; `fun SemanticsNodeInteractionsProvider.assertNumbersWhole()`, que aplica o `assertShownWhole` a todo nó de texto cujo conteúdo é só número, unidade, `×`, `:` e separadores.
- Produces, no `ci.yml`: no `host`, `:screens:testAndroidHostTest` no comando, `screens/build/test-results/testAndroidHostTest` no resumo e `screens/build/` no upload; no `ios`, `:screens:compileKotlinIosArm64 :screens:iosSimulatorArm64Test`, `screens/build.gradle.kts` na chave do cache `~/.konan`, um passo "Summarize screens iOS simulator tests" igual ao do design system com `MIN_SCREENS_IOS_TESTS` e `screens/build/` no upload.
- Produces: `tools/apk-budget.json` com `baseCommit` `4f2911c…` (SHA completo), `baseApkSource` (o run verde da `main` nesse commit), `baseApkBytes` (tamanho do artefato `app-debug-apk` desse run), `maxGrowthBytes: 1048576`, `maxFontBytes: 921600`, `maxFontsAndIconsBytes: 1048576`.

- [ ] **Step 1: Base.** `gh run list -R pedroyuuki/Mooscles --branch main --commit 4f2911c…` e `gh run download <id> -n app-debug-apk`: anotar os bytes. Salvar `./gradlew :app:dependencies --configuration debugRuntimeClasspath` em `docs/superpowers/reports/AAAA-MM-DD-phase3-verificacao/deps-before.txt` (a pasta nasce aqui; ver Global Constraints).
- [ ] **Step 2: Testes que falham.**
  - `EquipmentIconsTest.matchesTheSpecTable` (os 9 equipamentos e um desconhecido);
  - `StringsParityTest`: `everyEnglishKeyExistsInPortugueseAndViceVersa`, `pluralsDefineOneAndOtherInBothLanguages`, `placeholdersMatchPerKey`, `noEmptyValues`, `copyHasNoExclamationOrEmoji`;
  - `CatalogLabelsTest.portugueseLabels` (Chest→"Peito", Barbell→"Barra", Per hand→"Por mão"), `captionOnlyForNonTotalLoad` (Total load→null; Per hand→"Por mão: registre a carga de um halter."), `unknownLabelFallsBackToItself`.
- [ ] **Step 3:** Rodar `./gradlew :screens:testAndroidHostTest` e ver falhar (o módulo não existe).
- [ ] **Step 4:** Criar o módulo, os recursos, os helpers e as mudanças de CI e de scripts. Se o plugin não configurar ou o Robolectric do `:app` não achar os recursos do `:screens`, **pare e reporte** com o erro.
- [ ] **Step 5: Verificar.**
  - `./gradlew :screens:testAndroidHostTest :screens:compileCommonMainKotlinMetadata :app:testDebugUnitTest --tests "com.mooscles.DesignSystemResourcesHostTest" :app:assembleDebug` verde;
  - `./gradlew :screens:connectedAndroidDeviceTest` verde no emulador (`CatalogLabelsTest`);
  - `pwsh tools/check-design-tokens.ps1` sai com 0, e a contagem de arquivos inclui os do `:screens`;
  - `pwsh tools/check-apk-budget.ps1 -Apk app/build/outputs/apk/debug/app-debug.apk` passa e imprime os números (depois de `./gradlew :app:clean :app:assembleDebug`);
  - `deps-after.txt` e `deps-diff.txt` na mesma pasta: só linhas novas (o próprio `:screens`); nenhuma versão existente muda. Se mudar, **pare e reporte**;
  - `grep -rnE "\b(java|javax|android)\.|System\.|String\.format" screens/src/commonMain` vazio.
- [ ] **Step 6:** Subir `MIN_HOST_TESTS` e criar `MIN_SCREENS_IOS_TESTS` com a contagem desta task. `compat-reviewer` (`settings.gradle.kts`, `app/build.gradle.kts`; esperado `SEGURO`). Commit `build: add the :screens multiplatform module`.

### Task 10: Datas, durações e o `kotlinx-datetime`

**Files:**
- Modify: `screens/build.gradle.kts` (`implementation("org.jetbrains.kotlinx:kotlinx-datetime:<versão>")`, decisão 10), `.github/workflows/ci.yml`
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/format/ScreenDates.kt`, `.../format/Durations.kt`, `screens/src/commonMain/composeResources/values/strings_dates.xml` e `values-pt-rBR/strings_dates.xml`
- Test: `screens/src/commonTest/kotlin/com/mooscles/screens/format/ScreenDatesTest.kt`, `screens/src/androidHostTest/kotlin/com/mooscles/screens/format/ScreenDatesJvmEquivalenceTest.kt`, `screens/src/uiTest/kotlin/com/mooscles/screens/format/DateTextsTest.kt`

**Interfaces:**
- Produces: `object ScreenDates { fun localDateTime(epochMillis: Long, zone: TimeZone): LocalDateTime; fun finishedInLast28Days(history: List<WorkoutUi>, nowMillis: Long, zone: TimeZone): Int }` (decisão 13).
- Produces: `object Durations { fun clock(elapsedSeconds: Long): String }` (decisão 12) e, composables, `@Composable fun shortDate(epochMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String` ("21 out" / "Oct 21"), `@Composable fun summaryDateRange(startMillis: Long, endMillis: Long, zone: TimeZone = …): String` e `@Composable fun durationText(seconds: Long): String`.
- Strings: meses `jan, fev, mar, abr, mai, jun, jul, ago, set, out, nov, dez` / `Jan … Dec`; dias `dom., seg., ter., qua., qui., sex., sáb.` / `Sun, Mon, Tue, Wed, Thu, Fri, Sat`; `date_short_format` "%2$s %1$d" (en) / "%1$d %2$s" (pt); `date_range_format` "%1$s, %2$s · %3$s–%4$s" (o dia da semana, a data curta e as horas, nas duas línguas); `duration_minutes` "%1$d min"; `duration_hours_minutes` "%1$d h %2$02d min".

- [ ] **Step 1: Testes que falham.**
  - `ScreenDatesTest.windowCountsTwentyEightCalendarDaysIncludingToday`: agora 2026-10-29 10:00 em `America/Sao_Paulo`; treinos em 2026-10-02 (dentro) e 2026-10-01 (fora) dão 1;
  - `windowUsesTheDeviceZone`: `2026-10-02T01:00Z` é 2026-10-01 em São Paulo, e fica fora;
  - `clockFormats`: 2538 → "42:18"; 3738 → "1:02:18"; 0 → "0:00";
  - `ScreenDatesJvmEquivalenceTest.localDateTimeMatchesJavaTime` (instantes nas viradas de horário de verão de `America/Sao_Paulo` em 2018-11-04 e 2019-02-17, de `Europe/Berlin` em 2026-03-29 e 2026-10-25, `UTC` e `Asia/Kolkata`) e `windowMatchesJavaTime` (a contagem com `java.time` nas mesmas zonas);
  - `DateTextsTest`: `shortDateBothLanguages` ("Oct 21", "21 out"), `summaryRangePortuguese` ("ter., 29 out · 18:02–18:44"), `summaryRangeEnglish` ("Tue, Oct 29 · 18:02–18:44"), `durations` (2520 s → "42 min"; 4320 s → "1 h 12 min").
- [ ] **Step 2:** Rodar `./gradlew :screens:testAndroidHostTest` e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4: Verificar.** `./gradlew :screens:testAndroidHostTest :screens:compileCommonMainKotlinMetadata`; `DateTextsTest` no emulador; `pwsh tools/check-apk-budget.ps1 …` dentro do orçamento, com o crescimento anotado no commit.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS`. Commit `screens: calendar dates and durations without platform formatters`.

### Task 11: Barra de abas, barra do treino e ícones novos

**Files:**
- Create: em `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/`, `components/MTabBar.kt`, `components/ActiveWorkoutBar.kt`, `icons/lucide/LucideHistory.kt`, `LucideLayoutGrid.kt`, `LucideSearch.kt`, `LucideKeyboard.kt`, `LucideShare2.kt`, `LucidePencil.kt`, `LucideArrowUp.kt`, `LucideArrowDown.kt`, `LucideArrowLeftRight.kt` (Lucide 1.63.0, como os atuais), `icons/own/OwnDumbbellFilled.kt`, `OwnLayoutGridFilled.kt`, `OwnHistoryFilled.kt`, `OwnPlateRecordFilled.kt`, `gallery/pages/ShellPage.kt`
- Modify: `icons/MIcons.kt`, `tokens/SpaceTokens.kt` (`barMin = 64.dp`, `ringBar = 32.dp`, `ringBarStroke = 3.dp`), `gallery/GalleryPage.kt`, `strings_components.xml` (en e pt), `.github/workflows/ci.yml`; se o S2 aprovou, `components/MTextField.kt`
- Test: `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/components/MTabBarTest.kt`, `ActiveWorkoutBarTest.kt`; `designsystem/src/commonTest/kotlin/com/mooscles/designsystem/icons/IconsTest.kt` e os testes de tokens (atualizados)

**Interfaces:**
- Produces: `data class MTab(val key: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)` e `@Composable fun MTabBar(tabs: List<MTab>, selectedKey: String?, onSelect: (String) -> Unit, onReselect: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)`. Cada aba tem a tag `tab-<key>`. `bg.raised` com `elevation.2`; selecionada com `selectedIcon` em `accent.text` e a pílula `accent.subtle` (nó `tab-pill`); as outras em `text.secondary`. Altura mínima `sizes.barMin`, alvos de 48. Semântica: a linha com `selectableGroup()` e `collectionInfo(rowCount = 1, columnCount = n)`; cada aba `Role.Tab`, `selected`, `collectionItemInfo(0, 1, índice, 1)`. Quando algum rótulo não cabe na coluna (medido) ou `fontScale > 1.5f`, só a selecionada mostra o rótulo; as outras têm `contentDescription = label`. Tocar na selecionada chama `onReselect`, não `onSelect`.
- Produces: `data class ActiveWorkoutBarState(val workoutName: String, val elapsedText: String, val elapsedSpoken: String, val setsText: String, val rest: RestTimerVisual?, val restSpoken: String?)` e `@Composable fun ActiveWorkoutBar(state: ActiveWorkoutBarState, onOpen: () -> Unit, onSkipRest: () -> Unit, modifier: Modifier = Modifier)`: 56 dp de altura mínima, `bg.raised`, `radius.lg` no topo; nome em `title.sm` (uma linha, reticências permitidas), tempo, séries; com descanso, o anel de `ringBar` e o tempo do descanso no lugar das séries e o pular (56 dp). É um botão (tag `workout-bar`) com o nome acessível `ds_workout_bar_spoken`, sem live region. A 200%, duas linhas, sem esconder o pular.
- Produces: `MIcons.History`, `LayoutGrid`, `Search`, `Keyboard`, `Share2`, `Pencil`, `ArrowUp`, `ArrowDown`, `ArrowLeftRight`, `DumbbellFilled`, `LayoutGridFilled`, `HistoryFilled`, `PlateRecordFilled`.
- Strings: `ds_workout_bar_spoken` "Workout in progress, %1$s, %2$s" / "Treino em andamento, %1$s, %2$s"; `ds_workout_bar_rest_spoken` "%1$s. Rest, %2$s" / "%1$s. Descanso, %2$s".

- [ ] **Step 1: Testes que falham.**
  - `MTabBarTest`: `isSixtyFourTallWithFortyEightTargets`; `tabsAreSelectableTabsWithPosition` (`Role.Tab`, uma selecionada, `collectionItemInfo` 0..3); `selectedHasFilledIconAndPillNotOnlyColor` (`tab-pill` só na selecionada, `useUnmergedTree`); `reselectCallsOnReselect`; `onlySelectedLabelAboveOneAndAHalf` (a 1,6, só um rótulo visível, os quatro `contentDescription` presentes); `onlySelectedLabelWhenALabelDoesNotFit` (320 dp, 1,3, rótulos longos); `selectedLabelIsWholeAtTwoHundred` (`assertShownWhole` a 2,0 e 2,01 em 320 dp); `followsTheLightTheme` (fundo `MoosclesColors.Light.bgRaised`).
  - `ActiveWorkoutBarTest`: `isAButtonWithTheSpokenSummary` (en "Workout in progress, Workout A, 42 minutes. Rest, 1 minute and 24 seconds"; pt "Treino em andamento, Treino A, 42 minutos. Descanso, 1 minuto e 24 segundos"); `withoutRestShowsSetsAndNoSkip` ("9 de 15 séries"); `skipIsFiftySixAndCallsSkip`; `hasNoLiveRegion`; `twoLinesAtTwoHundredKeepsSkip` (320 dp, 2,0: o fundo do nome ≤ topo do tempo, pular exibido); `timeIsWholeWhileTheNameEllipsizes`.
  - `IconsTest.iconListIsExactly` (51 nomes) e `onlyFilledVariantsFill` (preenchimento só nos `*Filled`).
- [ ] **Step 2:** Rodar `./gradlew :designsystem:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.mooscles.designsystem.components.MTabBarTest` (e as outras classes) e `:designsystem:testAndroidHostTest --tests "*IconsTest*"`; ver falhar.
- [ ] **Step 3:** Implementar, com a página `Shell` na galeria (abas a 1,0 e 2,0; barra com e sem descanso).
- [ ] **Step 4 (só se o S2 aprovou):** aplicar a estratégia ao `MTextField`, com `MTextFieldTest.labelIsReadBeforeTheValue` (a ordem da semântica que o S2 aprovou) e os testes atuais do `MTextField` e de Configurações (`./gradlew :app:testDebugUnitTest --tests "com.mooscles.SettingsScreenHostTest"`) verdes.
- [ ] **Step 5: Verificar.** As classes novas e `GallerySmokeTest` no emulador; `:designsystem:testAndroidHostTest`; `pwsh tools/check-design-tokens.ps1`.
- [ ] **Step 6:** Subir `MIN_DS_IOS_TESTS`. Commit `designsystem: tab bar, active workout bar and the phase 3 icons`. **Push** (checkpoint das Tasks 9–11: o job `ios` prova o `:screens` e o `:designsystem` no simulador).

### Task 12: Linha de série: assistida, sugestão, toque duplo, deslize, campo e stepper

**Files:**
- Modify: em `designsystem/.../components/`, `SetRow.kt`, `NumericField.kt`, `Stepper.kt`; `gallery/pages/SetRowPage.kt`; `strings_components.xml` (en e pt); `.github/workflows/ci.yml`
- Create: `designsystem/.../components/SetRowSwipe.kt`
- Test: em `designsystem/src/uiTest/.../components/`, `SetRowTest.kt`, `SetRowSwipeTest.kt` (novo), `NumericFieldTest.kt`, `StepperTest.kt`

**Interfaces:**
- Produces: `SetType.Assisted` (letra "AS" nos dois idiomas, falada `ds_set_type_assisted_spoken` "assisted set" / "série assistida").
- Produces: `SetRowState` ganha `loadSuggested: Boolean = false` e `repsSuggested: Boolean = false` (cor `text.secondary` e a palavra da sugestão na fala). A fala: com a carga sugerida, "carga X" vira "sugestão X"; só as reps sugeridas, "sugestão 8 repetições"; as duas, "sugestão 82,5 quilos, 8 repetições" (`ds_suggestion_spoken` "suggestion %1$s" / "sugestão %1$s").
- Produces: `SetRow(…, onEditRow: (() -> Unit)? = null, onCopyPrevious: (() -> Unit)? = null)`: `onEditRow` é o `onClick` da linha, com o rótulo `ds_action_edit_set` "Edit set" / "Editar série" (D21); `onCopyPrevious` torna a célula "anterior" um alvo de 48 dp (tag `set-row-previous`) com a ação de acessibilidade `ds_action_copy_previous` "Copy previous" / "Copiar anterior".
- Produces: `fun Modifier.setRowSwipe(enabled: Boolean, onComplete: () -> Unit, onRemove: () -> Unit): Modifier`, sobre `anchoredDraggable` do `foundation`: limite de 35% da largura (`const val SetRowSwipeThreshold = 0.35f`), `StepperStep` ao cruzar, volta ao centro ao soltar; direita conclui, esquerda remove; não rouba a rolagem vertical.
- Produces: `NumericField(…, selected: Boolean = false, imeAction: ImeAction = ImeAction.Done, onImeAction: () -> Unit = {}, focusRequester: FocusRequester? = null)`: `selected` desenha o anel de foco sem focar.
- Produces: `Stepper(…, onValueChangeFinished: () -> Unit = {})`: chamado uma vez depois de um toque, uma vez ao soltar depois de segurar, e depois da ação de semântica.

- [ ] **Step 1: Testes que falham.**
  - `SetRowTest`: `suggestedValuesAreSecondaryAndSpoken` (pt "Série 2 de 5, anterior 80 quilos por 8, sugestão 82,5 quilos, 8 repetições, não concluída"; en "Set 2 of 5, previous 80 kilograms by 8, suggestion 82.5 kilograms, 8 reps, not completed"); `onlyRepsSuggestedSaysSuggestionBeforeReps` ("…, carga 85 quilos, sugestão 8 repetições, …"); `assistedShowsASAndIsSpoken`; `rowClickIsEditSet` (rótulo do `OnClick` "Editar série", chama `onEditRow`); `noRowClickWithoutCallback`; `previousIsATargetThatCopies` (48 dp, chama `onCopyPrevious`).
  - `SetRowSwipeTest`: `rightPastThirtyFivePercentCompletes` (40%: `onComplete` uma vez, `[StepperStep]`); `shortOfTheThresholdSettlesBack` (30%: nada); `leftPastTheThresholdRemoves`; `disabledDoesNothing`; `verticalDragScrollsTheList` (numa `LazyColumn`, arrasto vertical rola e não chama nada).
  - `NumericFieldTest`: `selectedShowsTheRingWithoutFocus`; `imeActionCallsBack`.
  - `StepperTest`: `tapFinishesOnceAfterTheStep`; `holdFinishesOnceOnRelease` (segurar 560 ms: 3 passos, 1 `onValueChangeFinished`); `semanticActionStepsAndFinishes`.
- [ ] **Step 2:** Rodar as classes no emulador e ver falhar.
- [ ] **Step 3:** Implementar, com as amostras novas na página `SetRow` da galeria.
- [ ] **Step 4: Verificar.** As classes e o `GallerySmokeTest` no emulador; os testes de 2,01 do `SetRowTest` continuam verdes; `pwsh tools/check-design-tokens.ps1`.
- [ ] **Step 5:** Subir `MIN_DS_IOS_TESTS`. Commit `designsystem: assisted sets, suggestions, swipe and edit hooks on the set row`.

### Task 13: Card segmentado e as superfícies do timer

**Files:**
- Modify: em `designsystem/.../components/`, `ExerciseCard.kt`, `RestTimerDock.kt`, `RestTimerExpanded.kt`; `tokens/SpaceTokens.kt` (`contentMaxWidth = 600.dp`), `tokens/MotionTokens.kt` (`MoosclesTiming.cardAdvance = 300`); páginas `ExerciseCardPage.kt` e `TimerPage.kt`; `strings_components.xml` (en e pt); `.github/workflows/ci.yml`
- Create: `designsystem/.../components/RestTimerCompact.kt`
- Test: `ExerciseCardTest.kt`, `RestTimerDockTest.kt`, `RestTimerExpandedTest.kt`, `RestTimerCompactTest.kt` (novo) e os testes de tokens

**Interfaces:**
- Produces (as peças que a lista preguiçosa usa, desenhando o mesmo card; o `ExerciseCard` atual passa a delegar a elas e os testes dele continuam):
  - `@Composable fun ExerciseCardHeader(name: String, meta: String, media: ExerciseMedia, equipmentIcon: ImageVector, onMenu: () -> Unit, collapsed: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, progress: String? = null, done: Boolean = false, caption: String? = null, support: (@Composable RowScope.() -> Unit)? = null, columns: (@Composable () -> Unit)? = null)`: recolhido, mostra `progress` ("3 de 3 séries") e, com `done`, o check (nó `card-done`); tocar no cabeçalho chama `onToggle`;
  - `@Composable fun ExerciseCardSegment(modifier: Modifier = Modifier, content: @Composable () -> Unit)` (o miolo, sem cantos);
  - `@Composable fun ExerciseCardFooter(onAddSet: () -> Unit, modifier: Modifier = Modifier, addEnabled: Boolean = true)` ("Adicionar série", tonal L, largura toda, 56 dp).
- Produces: `RestTimerDock(…, onOpen: (() -> Unit)? = null, canAdd: Boolean = true)`: tocar no corpo chama `onOpen`, rótulo `ds_timer_open` "Open rest timer, %1$s remaining" / "Abrir timer de descanso, %1$s restantes"; pausado mostra o ícone `Pause` (nó `dock-paused-icon`) e o arco em `text.tertiary`; `canAdd = false` desabilita o +15 s.
- Produces: `@Composable fun RestTimerCompact(visual: RestTimerVisual, onSkip: () -> Unit, modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null)`: uma linha de `rowMin` (56 dp) com o anel `ringBar`, o tempo `numDock` tabular e o pular, sem ±15 s.
- Produces: `RestTimerExpanded(…, title: String, totalText: String?, onPauseResume: () -> Unit, onRestart: () -> Unit, onClose: () -> Unit, canAdd: Boolean = true)`: o título ("Descanso · Supino reto · série 4 de 5"), "de 2:00" (`ds_timer_of` "of %1$s" / "de %1$s"), "Pular descanso" tonal L, "Pausar"/"Retomar" secundário, "Recomeçar" texto (`ds_timer_restart` "Restart" / "Recomeçar") e "Fechar timer" (`ds_timer_close` "Close timer" / "Fechar timer", `ChevronDown`, no topo); controles na metade de baixo.
- Strings: `ds_sets_done_of`, plural, "%1$d of %2$d sets" / "%1$d de %2$d séries".

- [ ] **Step 1: Testes que falham.**
  - `ExerciseCardTest`: `segmentsDrawOneCard` (topo do cabeçalho com `radius.lg`, miolo sem raio, rodapé com raio embaixo, pela propriedade de teste `CardSegmentShape`); `collapsedHeaderShowsProgressAndCheck`; `headerTapToggles`; `footerAddSetIsFiftySixAndFullWidth`.
  - `RestTimerDockTest`: `bodyTapOpensWithTheSpokenLabel` (pt "Abrir timer de descanso, 1 minuto e 24 segundos restantes"); `pausedShowsThePauseIconAndTheTertiaryArc`; `plusIsDisabledWhenItCannotAdd`.
  - `RestTimerCompactTest`: `isFiftySixTallWithRingTimeAndSkip`; `timeIsWholeAtTwoHundredOn320dp` (2,0 e 2,01); `hasNoAdjustButtons`.
  - `RestTimerExpandedTest`: `showsTitleTotalAndControls` (Pausar em `Running`, Retomar em `Paused`, Recomeçar); `closeIsLabelled`; `controlsSitInTheBottomHalf` (topo da linha de ±15 s ≥ metade da altura).
  - Tokens: `contentMaxWidth == 600.dp`, `cardAdvance == 300`, `barMin == 64.dp`, `ringBar == 32.dp`.
- [ ] **Step 2:** Rodar as classes no emulador e `:designsystem:testAndroidHostTest`; ver falhar.
- [ ] **Step 3:** Implementar, com as páginas da galeria.
- [ ] **Step 4: Verificar.** As classes, `GallerySmokeTest` no emulador; host; `check-design-tokens`.
- [ ] **Step 5:** Subir `MIN_DS_IOS_TESTS`. Commit `designsystem: segmented exercise card and compact, openable timer surfaces`.

### Task 14: A linha de série na tela: sugestões, check otimista e copiar o anterior

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/workout/ActiveWorkoutState.kt`, `.../workout/WorkoutSetRow.kt`, `.../workout/SetTexts.kt`, `screens/src/commonMain/composeResources/values/strings_workout.xml` e `values-pt-rBR/strings_workout.xml`
- Test: `screens/src/uiTest/kotlin/com/mooscles/screens/workout/WorkoutSetRowTest.kt`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: `SetSuggestions`, `SetField`, `SetSuggestion` (Task 7); `SetRow`, `SetRowState`, `Modifier.setRowSwipe`, `NumericField` (Task 12); `RecordingActions`, `withScreen` (Task 9).
- Produces: `data class SetEditTarget(val setId: String, val field: SetField, val keyboard: Boolean = false, val error: Boolean = false)`; `@Stable class ActiveWorkoutScreenState` com `cleared: Set<Pair<String, SetField>>`, `optimistic: Map<String, Boolean>` e `editing: SetEditTarget?`, e `@Composable fun rememberActiveWorkoutScreenState(): ActiveWorkoutScreenState` (com `Saver`). As Tasks 15–21 acrescentam campos a esta classe.
- Produces: `@Composable internal fun WorkoutSetRow(set: SetUi, number: Int, total: Int, entry: WorkoutExerciseUi, suggestion: SetSuggestion, previous: PreviousSetUi?, pounds: Boolean, screen: ActiveWorkoutScreenState, actions: MoosclesActions, onChangeType: (SetUi) -> Unit, onRemove: (SetUi, Int) -> Unit, modifier: Modifier = Modifier)`.
- Produces (`SetTexts.kt`): `@Composable internal fun loadText(weightKg: Double, pounds: Boolean): String` e `loadSpoken(…)`, com `WeightUnits.fromKg`, `MoosclesNumbers.plain` e `localize`; `"—"` para o anterior sem par.
- Strings: `workout_enter_reps` "Enter the reps" / "Informe as repetições".
- Comportamento:
  - o check com reps válidas (salvas ou sugeridas) marca `optimistic[set.id] = true` na hora e chama `updateSet(id, eff.reps, eff.weightKg, true, warmup, assistance)` com o `effective` (carga em kg exata da origem); o otimista sai quando o estado chega igual;
  - sem reps válidas: `Rejected` e `editing = SetEditTarget(id, REPS, error = true)`, sem `updateSet`;
  - desmarcar chama `updateSet(…, false, …)`;
  - tocar no "anterior" de uma pendente chama `updateSet(id, prev.reps, prev.weightKg, false, …)` com `StepperStep`; numa concluída, nada;
  - deslizar para a direita é o check; para a esquerda, `onRemove(set, position)`;
  - apagar um campo de propósito põe `(set.id, campo)` em `cleared`.

- [ ] **Step 1: Testes que falham** (`WorkoutSetRowTest`, nos dois idiomas onde há texto):
  - `prefilledSetCompletesWithOneTap`: alvo de rotina 80 × 8, um toque, `calls.last() == "updateSet:s1,8,80.0,true,false,false"`;
  - `suggestionFromAboveIsShownAndSentOnCheck`: série 1 82,5 × 8 concluída; o check da série 2 grava `"updateSet:s2,8,82.5,true,false,false"`, e antes a linha diz "sugestão 82,5 quilos, 8 repetições";
  - `discFillsBeforeTheEcho`: logo depois do toque, sem mudar o estado, o nó do check está marcado;
  - `optimisticClearsWhenTheEchoArrives`;
  - `noRepsRejectsAndOpensRepsWithError`: hápticos `[Rejected]`, "Informe as repetições" visível, nenhum `updateSet`;
  - `uncheckSendsCompletedFalse`;
  - `tappingPreviousCopiesWithoutCompleting`: `"updateSet:s1,6,80.0,false,false,false"`, háptico `StepperStep`;
  - `tappingPreviousOnACompletedSetDoesNothing`;
  - `swipeRightCompletesLikeTheCheck`;
  - `poundsSendTheExactSourceKg`: anterior 36.287389 kg, com libras, grava `36.287389`;
  - `clearedRepsStayBlankWhenTheLoadChanges` (portado);
  - `addedWeightZeroCompletesAndSurvivesRestoration` (portado: `Added weight`, carga 0 sem sugestão, grava `0.0` e continua igual depois do `StateRestorationTester`).
- [ ] **Step 2:** Rodar `./gradlew :screens:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.mooscles.screens.workout.WorkoutSetRowTest` e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4: Verificar.** O mesmo comando verde; `:screens:testAndroidHostTest --tests "*StringsParityTest*"`; `check-design-tokens`.
- [ ] **Step 5:** Subir `MIN_SCREENS_IOS_TESTS`. Commit `screens: set row with suggestions, optimistic check and copy previous`.

### Task 15: Edição da série: stepper, teclado sob demanda e as garantias do `SetEditor`

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/workout/SetEditing.kt`
- Modify: `.../workout/WorkoutSetRow.kt`, `.../workout/ActiveWorkoutState.kt` (`drafts: Map<Pair<String, SetField>, String>`, salvável), `strings_workout.xml` (en e pt), `.github/workflows/ci.yml`
- Test: `screens/src/uiTest/kotlin/com/mooscles/screens/workout/SetEditingTest.kt`

**Interfaces:**
- Consumes: `Stepper(onValueChangeFinished)`, `NumericField(selected, imeAction, onImeAction, focusRequester)` (Task 12); `ActiveWorkoutScreenState` (Task 14).
- Produces: `@Composable internal fun SetEditPanel(target: SetEditTarget, set: SetUi, pounds: Boolean, screen: ActiveWorkoutScreenState, actions: MoosclesActions, modifier: Modifier = Modifier)`: o `Stepper` do campo sob a linha (2,5 kg, 5 lb ou 1 rep; rótulo "passo de 2,5 kg", `workout_step` "step of %1$s" / "passo de %1$s") e o botão de teclado (48 dp, `Keyboard`, `workout_open_keyboard` "Open keyboard" / "Abrir teclado"). O stepper tem o nome `workout_editing_load` "Editing load of set %1$d" / "Editando carga da série %1$d" (e `workout_editing_reps` "Editing reps of set %1$d" / "Editando repetições da série %1$d") e recebe o foco quando a edição vem do `onEditRow` ou das ações de acessibilidade (D21).
- Comportamento (D12): o primeiro toque num campo põe a linha em edição (`bg.raised`, `motion.base`), com o campo `selected` e sem foco; o segundo toque no campo (ou o botão de teclado) foca e abre o teclado (decimal na carga, numérico nas reps); "Próximo" da carga leva às reps, "Concluído" só fecha o teclado. Cada `onValueChangeFinished` e cada tecla válida gravam na hora; segurar grava uma vez ao soltar; tocar fora, no check ou noutra linha sai da edição. As garantias de hoje: um eco atrasado não apaga um rascunho mais novo; um rascunho inválido não é enviado nem perdido; a troca kg/lb converte o rascunho a partir do kg exato; a vírgula é aceita.

- [ ] **Step 1: Testes que falham** (`SetEditingTest`):
  - `firstTapEditsWithoutKeyboard` (stepper visível, campo não focado);
  - `secondTapFocusesTheField`;
  - `stepperStepsTwoPointFiveKgAndWritesEachStep` (pt mostra "85,0" e "passo de 2,5 kg"; grava 87.5 depois de +);
  - `poundsStepFiveLb` (80 lb, +, grava `WeightUnits.lbToKg(85.0)`);
  - `repsStepOne`;
  - `holdWritesOnceOnRelease` (segurar 560 ms: valor mostrado +3 passos, um `updateSet`);
  - `nextGoesToRepsAndDoneOnlyClosesTheKeyboard` (nenhuma chamada com `completed = true`);
  - `tappingOutsideEndsEditing`;
  - `lateEchoDoesNotEraseANewerDraft`, `invalidDraftIsNotSentAndNotLost`, `unitSwitchConvertsTheDraftFromExactKg`, `acknowledgedFieldsAcceptExternalChanges` (portados);
  - `commaDecimalIsAccepted` ("82,5" grava 82.5);
  - `editingDraftAndClearedFieldsSurviveRecreation` (Review Focus 1: `StateRestorationTester` mantém a linha em edição, o rascunho e o `cleared`);
  - `editSetActionFocusesTheLoadStepper` (D21: o `onClick` "Editar série" da linha põe a linha em edição e foca o stepper da carga, que tem o nome "Editando carga da série 4" / "Editing load of set 4"; `assertIsFocused`);
  - `editLoadAndEditRepsActionsFocusTheirStepper` (as ações de acessibilidade "Editar carga" e "Editar repetições");
  - `editingRowIsBroughtIntoViewAboveTheKeyboard` (insets injetados de 300 dp ou o teclado real, conforme o S3).
- [ ] **Step 2:** Rodar a classe no emulador e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** A classe e a `WorkoutSetRowTest` verdes no emulador; `check-design-tokens`.
- [ ] **Step 5:** Subir `MIN_SCREENS_IOS_TESTS`. Commit `screens: in-row editing with stepper and on-demand keyboard`.

### Task 16: Tipo da série, remover com desfazer e adicionar série

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/workout/SetTypeSheet.kt`, `.../workout/UndoRemoval.kt`
- Modify: `.../workout/ActiveWorkoutState.kt` (`lastRemoved: RemovedSet?`, salvável), `.../workout/WorkoutSetRow.kt`, `strings_workout.xml` (en e pt), `.github/workflows/ci.yml`
- Test: `screens/src/uiTest/kotlin/com/mooscles/screens/workout/SetTypeAndRemovalTest.kt`

**Interfaces:**
- Consumes: `restoreSet` (Task 6); `MBottomSheet`, `RadioRow`, `MButton`, `MToastHostState` (fase 2); `UiError.SET_NOT_RESTORED`.
- Produces: `data class RemovedSet(val entryId: String, val position: Int, val set: SetUi, val number: Int)`; `@Composable internal fun SetTypeSheet(set: SetUi, convention: String, onChoose: (warmup: Boolean, assistance: Boolean) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit)`; `internal suspend fun showUndo(host: MToastHostState, removed: RemovedSet, actions: MoosclesActions, texts: UndoTexts)`.
- Strings: "Tipo da série" / "Set type"; "Normal" / "Normal"; "Aquecimento" + "não conta para recordes" / "Warm-up" + "doesn't count for records"; "Assistida" + "recordes de repetições à parte" / "Assisted" + "separate rep records"; "Remover série" / "Remove set"; "Série %1$d removida" / "Set %1$d removed"; "Desfazer" / "Undo".
- Comportamento: tocar no número abre a sheet; escolher grava `updateSet` com o tipo e fecha (dois toques no total); "Assistida" só quando a convenção não é `Assistance`; remover (sheet, deslize ou ação) chama `removeSet`, guarda o `RemovedSet` e mostra o toast com ação (6 s, ou o tempo da acessibilidade do sistema, já no `MToast`); "Desfazer" chama `restoreSet(entryId, position, set)`; outra remoção, finalizar ou descartar fecham o toast; "Adicionar série" chama `addSet`, a linha nova entra com `spring.snappy` e o rodapé fica desabilitado com 100 séries.

- [ ] **Step 1: Testes que falham** (`SetTypeAndRemovalTest`):
  - `typeSheetOffersNormalWarmupAssisted` (os textos pt acima);
  - `assistedIsHiddenForTheAssistanceConvention`;
  - `choosingWarmupSendsWarmupAndClosesInTwoTaps` (`"updateSet:s4,6,85.0,false,true,false"`);
  - `removeFromTheSheetShowsUndo` ("Série 4 removida", "Desfazer"; `"removeSet:s4"`);
  - `undoRestoresTheSnapshot` (`calls.last()` começa com `"restoreSet:e1,3,"` e contém o id `s4`);
  - `swipeLeftRemovesWithUndo`;
  - `anotherRemovalReplacesTheUndo`;
  - `undoSurvivesRecreation` (Review Focus 1);
  - `undoStaysForSixSeconds` (visível a 5.900 ms);
  - `refusedRestoreShowsSetNotRestored` (com `errorCode = SET_NOT_RESTORED`, o toast "A série não pôde voltar.");
  - `addSetShowsTheSuggestionFromAbove`;
  - `addSetIsDisabledAtOneHundred`.
- [ ] **Step 2:** Rodar a classe no emulador e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** As classes do pacote `workout` verdes no emulador; `check-design-tokens`.
- [ ] **Step 5:** Subir `MIN_SCREENS_IOS_TESTS`. Commit `screens: set type sheet, removal with undo and add set`. **Push** (checkpoint das Tasks 12–16).

### Task 17: O card do exercício na lista: segmentos, recolher sozinho e menu

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/workout/ExerciseItems.kt`, `.../workout/CardExpansion.kt`, `.../workout/ExerciseMenu.kt`
- Modify: `.../workout/ActiveWorkoutState.kt` (`expanded: Map<String, Boolean>`, `manual: Set<String>`), `strings_workout.xml` (en e pt), `.github/workflows/ci.yml`
- Test: `screens/src/commonTest/kotlin/com/mooscles/screens/workout/CardExpansionTest.kt`, `screens/src/uiTest/kotlin/com/mooscles/screens/workout/ExerciseListTest.kt`

**Interfaces:**
- Consumes: `ExerciseCardHeader`, `ExerciseCardSegment`, `ExerciseCardFooter`, `timing.cardAdvance` (Task 13); `WorkoutSetRow` (Tasks 14–16); `EquipmentIcons`, `CatalogLabels` (Task 9); `shortDate` (Task 10); `RecompositionProbe` (Task 9).
- Produces: `internal fun LazyListScope.exerciseItems(entry: WorkoutExerciseUi, index: Int, count: Int, pounds: Boolean, screen: ActiveWorkoutScreenState, actions: MoosclesActions, callbacks: ExerciseCallbacks)`: um item para o cabeçalho, um para o cabeçalho das colunas, um por série e um para o rodapé, com chaves `header-<id>`, `cols-<id>`, `row-<setId>` e `footer-<id>`.
- Produces: `data class ExerciseCallbacks(val onRestChip: (WorkoutExerciseUi) -> Unit, val onSwap: (WorkoutExerciseUi) -> Unit, val onHistory: (exerciseId: String) -> Unit, val onHowToLog: (WorkoutExerciseUi) -> Unit, val onChangeType: (SetUi, WorkoutExerciseUi) -> Unit, val onRemoveSet: (RemovedSet) -> Unit)`.
- Produces: `object CardExpansion { fun current(exercises: List<WorkoutExerciseUi>): String?; fun isOpen(entryId: String, exercises: List<WorkoutExerciseUi>, expanded: Map<String, Boolean>, manual: Set<String>): Boolean }` (D9: o atual é o primeiro com série pendente; os outros recolhidos; o que a pessoa abriu ou fechou não muda sozinho).
- Strings: meta "%1$s · %2$s · série %3$d de %4$d" / "%1$s · %2$s · set %3$d of %4$d"; "Descanso %1$s" / "Rest %1$s"; "Anterior: %1$s" / "Previous: %1$s"; "Primeira vez" / "First time"; colunas "Série", "Anterior", "Reps" / "Set", "Previous", "Reps"; unidade da coluna "kg", "lb", "kg assist." / "lb assist."; menu "Histórico do exercício", "Trocar exercício", "Mover para cima", "Mover para baixo", "Como registrar", "Remover exercício" / "Exercise history", "Swap exercise", "Move up", "Move down", "How to log", "Remove exercise"; confirmação "Remover %1$s? As %2$d séries dele saem deste treino." (plural) / "Remove %1$s? Its %2$d sets leave this workout."; troca com séries concluídas "Trocar %1$s? As séries registradas dele saem deste treino." / "Swap %1$s? Its logged sets leave this workout."; botões "Remover" / "Remove", "Trocar" / "Swap", "Continuar treinando" / "Keep training".
- Comportamento: ao concluir a última pendente, depois de `timing.cardAdvance`, o card recolhe ("3 de 3 séries" com check), o próximo com pendente abre com `spring.snappy` e a lista rola até o cabeçalho dele; com movimento reduzido, tudo instantâneo. Cabeçalho das colunas oculto do leitor de tela.

- [ ] **Step 1: Testes que falham.**
  - `CardExpansionTest`: `currentIsTheFirstWithAPendingSet`; `manualChoiceIsNeverOverridden`; `afterTheLastPendingTheNextPendingOpens`.
  - `ExerciseListTest`: `largeExerciseComposesOnlyVisibleRows` (100 séries: no máximo 20 linhas compostas, pelo probe; portado); `repeatedInstancesKeepTheirOwnValues` (portado; Review Focus 3); `metaLineIsLocalized` ("Barra · Peito · série 4 de 5" aberto; recolhido "0 de 3 séries"); `supportLineShowsRestChipAndPreviousDate` ("Descanso 2:00", "Anterior: 21 out"; sem histórico "Primeira vez"); `conventionCaptionOnlyForNonTotalLoad`; `columnHeaderIsHiddenFromScreenReaders`; `columnUnitFollowsUnitAndConvention`; `completingTheLastPendingCollapsesAndOpensTheNext`; `reducedMotionSwitchesInstantly`; `manualToggleStaysPut`; `menuHasTheSixItems`; `removeExerciseConfirms` ("Remover Supino reto? As 3 séries dele saem deste treino." e depois `"removeWorkoutExercise:e1"`); `swapWithCompletedSetsConfirms`; `swapWithoutCompletedSetsOpensThePickerDirectly`; `moveUpAndDownCallMove` (`"moveWorkoutExercise:e2,-1"`); `historyCallsBackWithTheExerciseId`.
- [ ] **Step 2:** Rodar `./gradlew :screens:testAndroidHostTest --tests "*CardExpansionTest*"` e a classe de UI no emulador; ver falhar.
- [ ] **Step 3:** Implementar ("Como registrar" abre uma `MBottomSheet` com o `conventionExplanation`).
- [ ] **Step 4:** As duas verdes; `check-design-tokens`.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS`. Commit `screens: exercise cards as lazy segments that advance on their own`.

### Task 18: Sheet de descanso do exercício e seletor de exercício

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/workout/RestSheet.kt`, `.../workout/ExercisePicker.kt`, `.../workout/ExercisePickerSheet.kt`
- Modify: `strings_workout.xml` (en e pt), `.github/workflows/ci.yml`
- Test: `screens/src/commonTest/kotlin/com/mooscles/screens/workout/ExercisePickerTest.kt`, `screens/src/uiTest/kotlin/com/mooscles/screens/workout/RestSheetTest.kt`, `ExercisePickerSheetTest.kt`

**Interfaces:**
- Produces: `@Composable internal fun RestSheet(entry: WorkoutExerciseUi, actions: MoosclesActions, onDismiss: () -> Unit)` (D14): `Stepper` de 15 s entre 0:00 e 5:00, atalhos 1:00, 1:30, 2:00 e 3:00 (`MChip`), a nota "Vale para os próximos descansos deste exercício; o descanso atual mantém o próprio tempo." / "Applies to this exercise's next rests; the current rest keeps its own time.", e "Iniciar descanso agora" / "Start rest now" (`startTimer(entry.exercise.name, seconds)`). Cada `onValueChangeFinished` e cada atalho gravam `updateWorkoutExerciseRest`. Fecha sozinha se o exercício sumir do treino.
- Produces: `object ExercisePicker { fun recents(history: List<WorkoutUi>, limit: Int = 8): List<ExerciseUi>; fun filter(exercises: List<ExerciseUi>, query: String): List<ExerciseUi> }`: recentes dos últimos treinos, sem repetir, até 8; filtro por `ExerciseSearch.matches(id, name, muscle, equipment, query)`; o resto em ordem alfabética.
- Produces: `@Composable internal fun ExercisePickerSheet(exercises: List<ExerciseUi>, history: List<WorkoutUi>, onPick: (ExerciseUi) -> Unit, onDismiss: () -> Unit)`: `MBottomSheet` até 90%, `MTextField` "Buscar exercício" / "Search exercises", "Recentes" / "Recent" e "Todos" / "All"; linhas de 56 dp com o nome, "Peito · Barra" e o ícone do equipamento. Adicionar chama `addWorkoutExercise(workoutId, exerciseId)`; trocar, `replaceWorkoutExercise(entryId, exerciseId)`; os dois fecham.

- [ ] **Step 1: Testes que falham.**
  - `ExercisePickerTest`: `recentsAreTheLastWorkoutsExercisesUpToEightWithoutRepeats`; `searchFoldsAccentsLikeToday` (a mesma `ExerciseSearch`); `allIsAlphabetical`.
  - `RestSheetTest`: `stepperMovesInFifteenSecondsFromZeroToFive` (− desabilitado em 0:00, + desabilitado em 5:00); `shortcutsSetTheRest` (1:30 grava `"updateWorkoutExerciseRest:e1,90"`); `zeroTurnsTheRestOff` (grava 0); `noteIsShown` (pt exato); `startNowStartsTheTimer` (`"startTimer:Supino reto,120"`); `returningToTheSavedValueBeforeTheEcho` (120 → 135 → 120 antes do eco mostra 2:00); `openingAndChangingDoesNotTouchTheRunningTimer`; `sheetSurvivesCardDisposalAndClosesWhenItsExerciseIsRemoved` (portado).
  - `ExercisePickerSheetTest`: `addsAndCloses`; `swapsAndCloses`; `rowsAreFiftySixWithMuscleAndEquipment` ("Peito · Barra"); `recentsComeFirst`; `searchFilters`.
- [ ] **Step 2:** Rodar `:screens:testAndroidHostTest --tests "*ExercisePickerTest*"` e as classes de UI no emulador; ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Verdes; `check-design-tokens`.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS`. Commit `screens: exercise rest sheet and exercise picker`.

### Task 19: A tela do treino ativo: cabeçalho, relógio, fim da lista, vazio, skeleton, finalizar, descartar e erros

**Files:**
- Create: em `screens/src/commonMain/kotlin/com/mooscles/screens/workout/`, `ActiveWorkoutRoute.kt`, `ActiveWorkoutScreen.kt`, `WorkoutHeader.kt`, `WorkoutClock.kt`, `FinishDiscard.kt`, `RenameSheet.kt`, `WorkoutErrors.kt`; `screens/src/commonMain/composeResources/values/strings_errors.xml` e `values-pt-rBR/strings_errors.xml`
- Modify: `strings_workout.xml` (en e pt), `.github/workflows/ci.yml`
- Test: `screens/src/uiTest/kotlin/com/mooscles/screens/workout/ActiveWorkoutScreenTest.kt`

**Interfaces:**
- Consumes: tudo das Tasks 14–18; `Durations`, `durationText` (Task 10); `UiState.errorCode` (Task 4).
- Produces: `@Composable fun ActiveWorkoutRoute(state: UiState, timer: State<TimerUi>, actions: MoosclesActions, onMinimize: () -> Unit, onOpenSummary: (workoutId: String) -> Unit, onExerciseHistory: (exerciseId: String) -> Unit, onChooseRoutine: () -> Unit, modifier: Modifier = Modifier, skeletonCards: Int = 1, imeInsets: WindowInsets = WindowInsets.ime, nowMillis: () -> Long = { currentTimeMillis() })`. O `timer` não é lido nesta task (a Task 20 o liga às superfícies).
- Produces: `@Composable internal fun WorkoutClock(startedAt: Long, nowMillis: () -> Long)`: lê o tempo uma vez por segundo, `num.sm`, nome acessível "Tempo de treino, 42 minutos" / "Workout time, 42 minutes", sem live region, `RecompositionProbe("clock")`.
- Produces: `@Composable internal fun uiErrorText(error: UiError): String` com os textos de `strings_errors.xml`:
  - `SAVE_FAILED` e `GENERIC`: "Não foi possível salvar a última mudança. Seus dados continuam no aparelho." / "Couldn't save the last change. Your data is still on this phone.";
  - `TIMER_STORAGE`: "O timer de descanso não conseguiu acessar o armazenamento. Seu treino está salvo." / "The rest timer can't reach storage. Your workout is saved.";
  - `LOCAL_DATA_UNAVAILABLE`: "Não foi possível abrir os dados do aparelho. Feche e abra o app." / "Couldn't open the data on this phone. Close and reopen the app.";
  - `BLOCKED_BY_UPDATE`: "Uma atualização está sendo instalada. O treino volta em instantes." / "An update is installing. Training will be back in a moment.";
  - `SET_GONE`: "Esta série não existe mais." / "This set no longer exists."; `EXERCISE_GONE`: "Este exercício não está mais no treino." / "This exercise is no longer in the workout."; `WORKOUT_GONE`: "Este treino não está mais em andamento." / "This workout is no longer in progress.";
  - `SET_LIMIT`: "Cada exercício tem no máximo 100 séries." / "Each exercise can have up to 100 sets."; `INVALID_VALUE`: "Confira o valor: ele não pôde ser salvo." / "Check the value: it couldn't be saved.";
  - `NEEDS_COMPLETED_SET`: "Conclua pelo menos uma série para finalizar." / "Complete at least one set to finish."; `SET_NOT_RESTORED`: "A série não pôde voltar." / "The set couldn't come back."
- Layout e textos (spec, "Tela do treino ativo"): cabeçalho fixo de `barMin` com "Minimizar treino" (`ChevronDown`, 48 dp), nome (`title.sm`, até 2 linhas), "42:18 · 9 de 15 séries" ("%1$d of %2$d sets", plural), "Finalizar" (tonal S) e "Mais opções" (`EllipsisVertical`: "Renomear treino" / "Rename workout", "Descartar treino" / "Discard workout"); `LazyColumn` com margem `s4`, cards a `s3`, largura até `contentMaxWidth`, centrada; no fim, "Adicionar exercício" (secundário L) e "Finalizar treino" (primário L, o único `accent.solid` da tela), com a nota "Conclua pelo menos uma série para finalizar." enquanto não houver série concluída.
- Finalizar: sem série concluída, `Rejected` e o toast da nota, sem diálogo; com série, `MDialog` "Finalizar treino?" / "Finish workout?" com "%1$d séries em %2$d exercícios · %3$s." / "%1$d sets across %2$d exercises · %3$s." e, com pendentes, "%1$d séries não concluídas ficam de fora." / "%1$d incomplete sets will be left out."; ações "Continuar treinando" e "Finalizar" (primário, na ponta). Confirmar mostra o carregamento e espera o treino aparecer em `state.history` (o `finishingId` de hoje, em `rememberSaveable`) para chamar `onOpenSummary`; um erro devolve o botão.
- Descartar: `MDialog` destrutivo "Descartar treino? As %1$d séries registradas serão apagadas." (plural) / "Discard workout? The %1$d logged sets will be deleted."; sem séries, "Descartar treino? Nada foi registrado ainda." / "Discard workout? Nothing has been logged yet."; ações "Continuar treinando" e "Descartar" (perigo). Finalizar e descartar fecham o toast de desfazer.
- Sem treino ativo: `EmptyState` "Nenhum treino em andamento" / "No workout in progress", "Escolher rotina" / "Choose routine" (`onChooseRoutine`) e "Treino vazio" / "Empty workout" (`startWorkout(null)`). Com `startingWorkout` e sem treino: `skeletonCards` `Skeleton` no formato dos cards.
- Erros: com `state.errorCode != null`, um `MToast` de erro com `uiErrorText`, depois `clearError()`; `state.error` nunca aparece.

- [ ] **Step 1: Testes que falham** (`ActiveWorkoutScreenTest`):
  - `headerHasMinimizeTitleClockAndFinish` (rótulos; cabeçalho ≥ 64 dp; com o relógio fixo em 2538 s, "42:18 · 9 de 15 séries"; o relógio tem o nome "Tempo de treino, 42 minutos" e nenhuma live region);
  - `minimizeCallsBack`;
  - `endOfListHasAddExerciseAndFinishWithTheNote`;
  - `finishWithoutACompletedSetRejectsWithTheToast` (hápticos `[Rejected]`, sem diálogo);
  - `finishDialogTextsAndOrder` ("Finalizar treino?", "9 séries em 4 exercícios · 42 min.", "3 séries não concluídas ficam de fora."; "Finalizar" é o último botão);
  - `confirmingShowsLoadingAndOpensTheSummaryWhenHistoryHasIt`;
  - `finishErrorRestoresTheButton`;
  - `discardDialogWithAndWithoutSets` (os dois textos) e `discardCallsDiscard`;
  - `renameSheetRenames` (`"renameWorkout:w1,Treino B"`);
  - `noWorkoutShowsTheEmptyStateWithTwoActions`;
  - `startingShowsOneSkeletonPerCard` (`skeletonCards = 3`: três skeletons);
  - `errorsShowLocalizedTextAndNeverTheRawError` (com `errorCode = SET_GONE` e `error = "Set unavailable"`: "Esta série não existe mais." visível, "Set unavailable" ausente, `"clearError:"` chamado);
  - `contentIsAtMostSixHundredWide` (janela de 900 dp);
  - `emptyActiveWorkoutShowsOnlyAddExerciseAndBlocksFinish` (Review Focus 4: "0 de 0 séries");
  - `finishingClosesTheUndoToastAndSendsNoInvalidDraft`;
  - `headerTitleTakesTwoLinesAtTwoHundredWithTheClockWhole` (320 dp, 2,0 e 2,01).
- [ ] **Step 2:** Rodar a classe no emulador e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Todas as classes do pacote `workout` verdes no emulador; `check-design-tokens`; `grep` de plataforma vazio.
- [ ] **Step 5:** Subir `MIN_SCREENS_IOS_TESTS`. Commit `screens: the active workout screen`.

### Task 20: O descanso na tela: dock, compacto, expandido e zero recomposição por tick

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/workout/WorkoutTimerSurfaces.kt`, `.../workout/RestUpNext.kt`
- Modify: `.../workout/ActiveWorkoutScreen.kt`, `strings_workout.xml` (en e pt), `.github/workflows/ci.yml`
- Test: `screens/src/commonTest/kotlin/com/mooscles/screens/workout/RestUpNextTest.kt`, `screens/src/uiTest/kotlin/com/mooscles/screens/workout/WorkoutTimerTest.kt`

**Interfaces:**
- Consumes: `RestTimerDock(onOpen, canAdd)`, `RestTimerCompact`, `RestTimerExpanded(title, totalText, onPauseResume, onRestart, onClose, canAdd)` (Task 13); `TimerUi.workoutExerciseId`.
- Produces: `object RestUpNext { fun of(workout: WorkoutUi, timerEntryId: String?): UpNext? }` com `data class UpNext(val setNumber: Int, val weightKg: Double, val reps: Int)`: a próxima pendente do exercício do timer, senão a primeira pendente do treino, senão `null`.
- Produces: `@Composable internal fun WorkoutTimerLayer(timer: State<TimerUi>, workout: WorkoutUi, pounds: Boolean, actions: MoosclesActions, imeInsets: WindowInsets, modifier: Modifier = Modifier)`, o único lugar da tela que lê `timer.value`.
- Comportamento: o dock só com o timer correndo, pausado ou nos `timerDoneHold` depois de uma transição vista (decisão 21), entra com `motion.base` e sai com `motion.fast`; compacto com o teclado aberto (acima dele) e com `fontScale >= 1.5f`; o corpo abre o expandido (decisão 23), que entra com `motion.slow` + `ease.drawer` (fade com movimento reduzido), mostra "Descanso · %1$s · série %2$d de %3$d" e "A seguir: série %1$d · %2$s" / "Up next: set %1$d · %2$s" ("A seguir: série 5 · 85 × 6"; sem próxima, a linha some) e fecha sozinho depois do "Descanso concluído". `canAdd = remaining < 300`.

- [ ] **Step 1: Testes que falham.**
  - `RestUpNextTest`: `nextPendingOfTheTimerExercise`; `fallsBackToTheFirstPendingOfTheWorkout`; `noneWhenEverythingIsDone`.
  - `WorkoutTimerTest`:
    - `noTimerTakesNoSpace`;
    - `dockShowsWhileRunningAndThreeSecondsAfterDone` (`mainClock`: presente a 2.999 ms do fim, ausente a 3.000 + `motion.fast`);
    - `doneAlreadyFinishedAtOpenShowsNothing`;
    - `adjustAndSkipCallTheActions` (`"adjustTimer:15"`, `"adjustTimer:-15"`, `"skipTimer:"`);
    - `plusFifteenIsDisabledAtFiveMinutes` (portado);
    - `bodyTapOpensTheExpanded` e `closingTheExpandedReturnsFocusToTheDock` (fechar pelo "Fechar timer" ou pelo voltar devolve o foco ao corpo do dock);
    - `expandedShowsTitleUpNextAndControls` ("Descanso · Supino reto · série 4 de 5", "de 2:00", "A seguir: série 5 · 85 × 6"; pausar → `pauseTimer`, retomar → `resumeTimer`, recomeçar → `resetTimer`);
    - `expandedClosesAfterTheDoneHold`;
    - `compactSitsAboveTheKeyboard` (300 dp: fundo do compacto em `altura − 300 dp` ±1 px; sem ±15 s);
    - `compactAtOneAndAHalf`;
    - `listKeepsSixtyPercentAtTwoHundredWithTheTimer` (`size = DpSize(412.dp, 915.dp)`, 2,0: altura da lista ≥ 0,6 × 915 dp);
    - `editingRowStaysAboveKeyboardAndCompactDock`;
    - `timerTicksRecomposeNoCardOrRow` (10 emissões do `timer`: contagens de `card-*` e `row-*` iguais; `dock` cresce);
    - `clockRecomposesOnlyItself` (5 s: `clock` +5, `row-*` +0);
    - `dockStaysVisibleWhileALongWorkoutScrolls` e `progressSemanticsFollowRunningPausedAdjustedResetAndDone` (portados).
- [ ] **Step 2:** Rodar `:screens:testAndroidHostTest --tests "*RestUpNextTest*"` e a classe de UI no emulador; ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Verdes, junto com as outras classes do pacote; `check-design-tokens`.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS`. Commit `screens: rest timer dock, compact and expanded in the workout`.

### Task 21: A reação ao recorde

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/workout/RecordAnnouncer.kt`
- Modify: `.../workout/ActiveWorkoutScreen.kt`, `.../workout/WorkoutSetRow.kt` (`SetStatus.CompletedRecord` quando `set.record != null`), `.../workout/ActiveWorkoutState.kt` (`completedHere: Set<String>` e `announced: Set<String>`, salváveis), `strings_workout.xml` (en e pt), `.github/workflows/ci.yml`
- Test: `screens/src/commonTest/kotlin/com/mooscles/screens/workout/RecordAnnouncerTest.kt`, `screens/src/uiTest/kotlin/com/mooscles/screens/workout/RecordReactionTest.kt`

**Interfaces:**
- Consumes: `SetUi.record`, `SetRecordUi` (Task 5); `PrToast`, `PrBadge` (fase 2).
- Produces: `object RecordAnnouncer { fun next(workout: WorkoutUi, completedHere: Set<String>, announced: Set<String>): RecordEvent? }` com `data class RecordEvent(val key: String, val setId: String, val exerciseName: String, val record: SetRecordUi, val weightKg: Double, val reps: Int)` e `key = "<setId>:<weightKg>:<reps>:<kind>"`: só séries em `completedHere`, com `record` não nulo e chave fora de `announced`.
- Comportamento (D17, D18, decisão 15): o selo entra com `spring.pr` (pelo `SetRow`, que também toca o `PersonalRecord` na transição para `CompletedRecord`, depois do `SetCompleted` do toque); o `PrToast` fica no topo da lista, abaixo do cabeçalho, por `timing.prToast`, lido de forma educada, e espera enquanto houver linha em edição. Títulos "Novo recorde de carga" / "New weight record" e "Novo recorde de repetições" / "New reps record"; detalhe "%1$s × %2$d · antes %3$s" ("87,5 kg × 6 · antes 85 kg") e "%1$s × %2$d · antes %1$s × %3$d" ("80 kg × 8 · antes 80 kg × 6"); en "before". Recordes que já estavam no estado ao abrir só mostram o selo.

- [ ] **Step 1: Testes que falham.**
  - `RecordAnnouncerTest`: `announcesOnlySetsCompletedHere`; `recordsPresentAtOpenAreNotAnnounced`; `aLaterSetBeatingItAnnouncesAgainWithTheNewValue`; `losingARecordIsSilent`; `theSameRecordIsAnnouncedOnce`.
  - `RecordReactionTest`: `weightRecordShowsBadgeToastAndHaptic` (hápticos `[SetCompleted, PersonalRecord]`; "Novo recorde de carga" e "87,5 kg × 6 · antes 85 kg"; `PrBadge` na linha); `repsRecordToastText`; `firstRecordDoesNothing`; `resumedRecordShowsOnlyTheBadge`; `toastLeavesAfterFourSecondsAndIsPolite`; `toastWaitsWhileARowIsEditing`; `scrollingARecordRowAwayAndBackDoesNotReplay` (Review Focus 2: um `PersonalRecord` só, nenhum segundo aviso); `badgeFollowsTheChosenSet`.
- [ ] **Step 2:** Rodar `:screens:testAndroidHostTest --tests "*RecordAnnouncerTest*"` e a classe de UI no emulador; ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Verdes; `check-design-tokens`.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS`. Commit `screens: record badge, toast and haptic confirmed by the presentation`.

### Task 22: O resumo de fim

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/summary/WorkoutSummaryScreen.kt`, `.../summary/SummaryStats.kt`, `screens/src/commonMain/composeResources/values/strings_summary.xml` e `values-pt-rBR/strings_summary.xml`
- Modify: `.github/workflows/ci.yml`
- Test: `screens/src/commonTest/kotlin/com/mooscles/screens/summary/SummaryStatsTest.kt`, `screens/src/uiTest/kotlin/com/mooscles/screens/summary/WorkoutSummaryScreenTest.kt`

**Interfaces:**
- Consumes: `ScreenDates`, `summaryDateRange`, `durationText` (Task 10); `MotionPolicy.keyMoment(WorkoutFinished)` (fase 2).
- Produces: `object SummaryStats { fun bestSet(entry: WorkoutExerciseUi): SetUi? }` (a mais pesada entre as concluídas que não são aquecimento, desempate pelas reps; sem nenhuma, a de mais reps entre as concluídas).
- Produces: `@Composable fun WorkoutSummaryScreen(workout: WorkoutUi, history: List<WorkoutUi>, pounds: Boolean, onDone: () -> Unit, onShare: () -> Unit, onDetails: () -> Unit, modifier: Modifier = Modifier, nowMillis: Long = currentTimeMillis(), zone: TimeZone = TimeZone.currentSystemDefault())`.
- Layout e textos (D19): entra uma vez (flag em `rememberSaveable`) com `spring.finish` no card de estatísticas, de 0,92 a 1 com opacidade (fade com movimento reduzido), e o háptico `SetCompleted`; "Treino concluído" / "Workout complete", o nome e o `summaryDateRange`; estatísticas em `num.lg`, 2 colunas (1 a partir de 1,5): "Duração" / "Duration", "Séries" / "Sets", "Volume" / "Volume" (`workout.volumeKg`), "Exercícios" / "Exercises"; "Recordes" / "Records" com `PrBadge` e "%1$s · %2$s" ("Supino reto · 87,5 kg × 6 · antes 85 kg"); "Primeiros registros" / "First records" com o selo suave; a seção some sem nenhum; "%1$d treinos nas últimas 4 semanas" / "%1$d workouts in the last 4 weeks" (plural); por exercício, o nome e a melhor série em `num.sm`; ações "Concluir" / "Done" (primário L), "Compartilhar" / "Share" (secundário L, `Share2`) e "Ver detalhes" / "View details" (texto).

- [ ] **Step 1: Testes que falham.**
  - `SummaryStatsTest`: `bestSetIsTheHeaviestNonWarmupThenMostReps`; `warmupOnlyFallsBackToMostReps`.
  - `WorkoutSummaryScreenTest`: `headerSaysWorkoutCompleteWithTheRange` ("Treino concluído", "ter., 29 out · 18:02–18:44"); `statsTwoColumnsThenOneAtOneAndAHalf`; `recordLines` ("Supino reto · 87,5 kg × 6 · antes 85 kg" e a linha de repetições); `firstRecordsSection`; `noRecordsHidesTheSection`; `consistencyLine` ("12 treinos nas últimas 4 semanas"; com 1, "1 treino nas últimas 4 semanas"); `perExerciseBestSet` ("82,5 kg × 8"); `actionsCallBack`; `entersOnceWithTheFinishSpringAndHaptic` (`[SetCompleted]`; depois do `StateRestorationTester`, nada novo); `reducedMotionFades`; `numbersWholeAtTwoHundred` (320 dp, 2,0 e 2,01).
- [ ] **Step 2:** Rodar `:screens:testAndroidHostTest --tests "*SummaryStatsTest*"` e a classe de UI no emulador; ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Verdes; `check-design-tokens`.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS`. Commit `screens: workout summary`. **Push** (checkpoint das Tasks 17–22).

### Task 23: A casca comum: `AppShellBottom`

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/shell/AppShellBottom.kt`, `.../shell/ShellTabs.kt`, `.../shell/WorkoutBarState.kt`, `screens/src/commonMain/composeResources/values/strings_shell.xml` e `values-pt-rBR/strings_shell.xml`
- Modify: `.github/workflows/ci.yml`
- Test: `screens/src/uiTest/kotlin/com/mooscles/screens/shell/AppShellBottomTest.kt`

**Interfaces:**
- Consumes: `MTabBar`, `ActiveWorkoutBar` (Task 11); `Durations.clock` e `durationText` (Task 10). O `WorkoutBarState` lê o relógio uma vez por segundo, só dentro da barra.
- Produces: `object ShellTabs { const val TRAIN = "routines"; const val LIBRARY = "library"; const val HISTORY = "history"; const val RECORDS = "records"; val keys: List<String> }` e `@Composable fun shellTabs(): List<MTab>` ("Train" / "Treinar" com `Dumbbell`, "Library" / "Biblioteca" com `LayoutGrid`, "History" / "Histórico" com `History`, "Records" / "Recordes" com `PlateRecord`; as variantes `*Filled` no selecionado).
- Produces: `@Composable fun AppShellBottom(state: UiState, timer: State<TimerUi>, selectedTab: String?, onSelectTab: (String) -> Unit, onReselectTab: (String) -> Unit, onOpenWorkout: () -> Unit, actions: MoosclesActions, modifier: Modifier = Modifier, imeInsets: WindowInsets = WindowInsets.ime, enabled: Boolean = true, nowMillis: () -> Long = { currentTimeMillis() })`, com tag `app-footer`: a `ActiveWorkoutBar` (com `state.active != null`) acima da `MTabBar`, e a reserva do teclado feita uma vez, sem subir as abas (o layout do `WorkoutBottomBar` de hoje). Só a barra lê `timer.value`.

- [ ] **Step 1: Testes que falham** (`AppShellBottomTest`): `fourTabsInOrderWithLabels` (pt Treinar, Biblioteca, Histórico, Recordes; tags `tab-routines` … `tab-records`); `noWorkoutNoBar`; `workoutBarShowsElapsedAndSets` ("9 de 15 séries"); `withRestShowsTheRingAndSkip` (`"skipTimer:"`); `barTapOpensTheWorkout`; `imeIsReservedOnceWithoutLiftingTheTabs`; `timerTicksDoNotRecomposeTheTabs` (probe `tabs` igual depois de 10 emissões); `disabledWhileLoading`.
- [ ] **Step 2:** Rodar a classe no emulador e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Verde; `check-design-tokens`.
- [ ] **Step 5:** Subir `MIN_SCREENS_IOS_TESTS`. Commit `screens: app shell bottom with tabs and the workout bar`.

### Task 24: O `:app` hospeda as telas novas

**Files:**
- Create: `docs/superpowers/reports/AAAA-MM-DD-phase3-verificacao/tap-baseline.md`, `app/src/test/java/com/mooscles/TapBudgetBaselineHostTest.kt` (só no Step 1, apagado no Step 4), `app/src/test/java/com/mooscles/ShellNavigationHostTest.kt`
- Modify: `app/src/main/java/com/mooscles/ui/MoosclesApp.kt`, `app/src/main/java/com/mooscles/ui/WorkoutScreens.kt` (saem `ActiveWorkoutScreen`, `WorkoutTimerDock` e o seletor antigo; ficam `WorkoutExerciseCard`, `SetEditor` e `CompletedSetRow`, que o histórico usa), `app/src/main/java/com/mooscles/ui/Common.kt` (sai o `TimerPanel`, se nada mais o usar), os testes da tabela "Destino dos testes atuais", `app/src/androidTest/java/com/mooscles/UiFlowTest.kt` e `UiFeatureSmokeTest.kt`, `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: `ActiveWorkoutRoute` (Task 19), `WorkoutSummaryScreen` (Task 22), `AppShellBottom`, `ShellTabs` (Task 23).
- Produces: `fun MoosclesApp(state: UiState, actions: MoosclesActions, timerState: State<TimerUi>? = null, workoutRequest: Long = 0L)`. Na rota `workout`, sem `topBar` nem `bottomBar`; nas outras (inclusive `gallery` e `licenses`), a `AppShellBottom`. A aba selecionada é a raiz da pilha atual (guardada ao navegar para uma aba, em `rememberSaveable`); tocar nela de novo volta à raiz, também de dentro da galeria e das licenças (#20). `composable("workout")` chama `ActiveWorkoutRoute` com `onMinimize = { if (!nav.popBackStack()) nav.navigate("routines") }`, `onOpenSummary = { nav.navigate("workoutDetail/$it?summary=true") { popUpTo("workout") { inclusive = true } } }`, `skeletonCards` (decisão 17) e o `timerState`. `workoutDetail/{id}?summary=true` chama `WorkoutSummaryScreen` (`onDone` vai a Treinar, `onShare` abre o `WorkoutSharePreview`, `onDetails` abre `?summary=false`); `false` continua a tela antiga. Decisão 19 no snackbar. `workoutRequest` diferente do último atendido (`rememberSaveable`) navega para `workout`, como o `handledUpdateRequest`.

- [ ] **Step 1: Contagem de hoje (antes de trocar).** Escrever `TapBudgetBaselineHostTest` sobre a tela antiga, com um contador de toques (`performClick`, gesto e cada caractere de `performTextInput` contam 1) e os nove fluxos da tabela "Orçamento de toques" da spec, no mesmo roteiro que a Task 29 usa. Rodar, copiar as contagens para `tap-baseline.md` (com a SHA) e fazer o commit `test: record today's tap counts before the workout swap`.
- [ ] **Step 2: Testes que falham.** `ShellNavigationHostTest`: `workoutRouteHasNoTabsOrBar`; `tabRoutesShowTheBarWhileActive`; `reselectingTheCurrentTabReturnsToItsRoot`; `reselectFromGalleryAndLicensesReturnsToTheRoot`; `startNavigatesAtOnceAndShowsOneSkeletonPerRoutineExercise`; `minimizeReturnsWhereTheWorkoutWasOpened`; `systemBackFromTheWorkoutMinimizes`; `summaryTrueOpensTheNewSummaryAndFalseTheOldDetail`; `summaryShareOpensThePreview`; `exerciseHistoryFromTheWorkoutReturnsToIt`; `globalSnackbarNeverShowsOnTheWorkoutRoute`; `workoutRequestOpensTheWorkoutOnce`. Mais os reapontados da tabela (`RestTimerHostTest`, `DiscardNavigationHostTest`, `UiFlowHostTest`, `TimerKeyboardInsetsHostTest`), com os mesmos nomes.
- [ ] **Step 3:** Rodar `./gradlew :app:testDebugUnitTest --tests "com.mooscles.ShellNavigationHostTest" --tests "com.mooscles.RestTimerHostTest" --tests "com.mooscles.DiscardNavigationHostTest" --tests "com.mooscles.UiFlowHostTest" --tests "com.mooscles.TimerKeyboardInsetsHostTest"` e ver falhar.
- [ ] **Step 4:** Implementar. Tirar o código antigo do caminho do treino, apagar os testes portados (o `WorkoutLazyRowsHostTest` inteiro, os 4 do `WorkoutInputHostTest` e os 2 do `InlineRestEditorHostTest`) e o `TapBudgetBaselineHostTest`. O commit lista cada teste apagado com o destino da tabela.
- [ ] **Step 5: Verificar.** `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` verde (a suíte do `:app` inteira, porque a casca toca todas as rotas); `pwsh tools/check-apk-budget.ps1 …` dentro do orçamento.
- [ ] **Step 6:** Ajustar `MIN_HOST_TESTS` (+ novos − apagados, com a conta no comentário). Commit `ui: host the new workout, summary and app shell`.

### Task 25: Notificação em andamento, textos localizados e abrir o treino

**Files:**
- Create: `app/src/main/java/com/mooscles/timer/RestTimerActions.kt`, `.../timer/RestTimerActionReceiver.kt`, `.../timer/RestNotificationTexts.kt`, `app/src/main/res/values/rest_strings.xml`, `app/src/main/res/values-pt-rBR/rest_strings.xml`
- Modify: `app/src/main/java/com/mooscles/timer/RestTimerController.kt`, `app/src/main/AndroidManifest.xml` (receiver `exported="false"`), `app/src/main/java/com/mooscles/MainActivity.kt`, `app/src/main/java/com/mooscles/AppViewModel.kt`, `.github/workflows/ci.yml`
- Test: `app/src/test/java/com/mooscles/timer/RestProgressNotificationHostTest.kt`, `RestTimerActionsHostTest.kt`, `app/src/test/java/com/mooscles/MainActivityOpenWorkoutHostTest.kt`

**Interfaces:**
- Consumes: `MoosclesApp(workoutRequest)` (Task 24).
- Produces: `class RestTimerActions(gate: Mutex, updates: UpdateCoordinator, timer: RestTimer) { suspend fun minus15(); suspend fun plus15(); suspend fun skip() }` (decisão 24).
- Produces: `object RestNotifications { const val PROGRESS_CHANNEL = "rest_progress"; const val PROGRESS_ID = 2; const val OPEN_WORKOUT = "com.mooscles.timer.OPEN_WORKOUT"; const val ACTION_MINUS = "com.mooscles.timer.MINUS15"; const val ACTION_PLUS = "com.mooscles.timer.PLUS15"; const val ACTION_SKIP = "com.mooscles.timer.SKIP" }`.
- Produces: `AppViewModel.workoutRequest: StateFlow<Long>` e `fun openWorkout()` (decisão 25).
- Comportamento (D16): com `foreground = false` e o timer correndo ou pausado, posta no canal `rest_progress` (`IMPORTANCE_LOW`, sem som nem vibração, criado ao lado do `rest`) a notificação 2: título "Descanso" / "Rest"; correndo, `setUsesChronometer(true)`, `setChronometerCountDown(true)` e `setWhen(deadline)`; pausada, o tempo fixo; texto "%1$s · a seguir série %2$d" / "%1$s · next set %2$d" (a próxima pendente do exercício do timer, lida do repositório; sem ela, só o nome); ações "−15 s", "+15 s" e "Pular" / "Skip" por `PendingIntent.getBroadcast` explícito e imutável para o `RestTimerActionReceiver`. Atualiza só quando o estado gravado muda; some ao voltar ao primeiro plano, ao pular, ao terminar, ao finalizar e ao descartar; é recriada quando o processo volta em segundo plano com o timer correndo. A de fim continua id 1, canal `rest`, com título "Descanso acabou" / "Rest is over" e texto "Próxima: %1$s, série %2$d" / "Next: %1$s, set %2$d". Os nomes dos canais passam a ser localizados ("Rest timer" / "Timer de descanso"; "Rest in progress" / "Descanso em andamento"); o `rest` mantém o id e a importância. Tocar em qualquer uma abre o `MainActivity` com a ação `OPEN_WORKOUT`, tratada só num lançamento novo (`savedInstanceState == null`) ou em `onNewIntent`.

- [ ] **Step 1: Testes que falham.**
  - `RestProgressNotificationHostTest` (Robolectric, `ShadowNotificationManager`): `backgroundWithARunningTimerPostsOngoingOnRestProgress` (id 2, canal `rest_progress`, `IMPORTANCE_LOW`, `usesChronometer`, `chronometerCountDown`, `when == deadline`, 3 ações); `pausedShowsAFixedTime`; `foregroundCancelsIt`; `skipFinishAndDiscardCancelIt`; `onlyStateChangesUpdateIt` (ticks não renotificam; um ajuste sim); `textsArePortugueseOnAPtBrDevice` (`@Config(qualifiers = "pt-rBR")`: "Descanso", "Supino reto · a seguir série 5"); `restChannelKeepsItsIdAndImportance`; `finishNotificationKeepsIdOneWithLocalizedText`; `recreatedWhenTheProcessReturnsInTheBackground` (Review Focus 5); `tappingOpensTheWorkout`.
  - `RestTimerActionsHostTest`: `plusFifteenRunsUnderTheGate`; `notificationActionIsRefusedWhileAnUpdateInstalls` (Review Focus 5: fase `INSTALLING`, o timer não muda); `skipCallsOnTrainingChanged`.
  - `MainActivityOpenWorkoutHostTest`: `freshLaunchWithOpenWorkoutOpensIt`; `recreationDoesNotRepeat`; `onNewIntentOpensIt`.
- [ ] **Step 2:** Rodar `./gradlew :app:testDebugUnitTest --tests "com.mooscles.timer.*" --tests "com.mooscles.MainActivityOpenWorkoutHostTest"` e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** O comando do Step 2 verde, mais `--tests "com.mooscles.updates.UpdateRestGuardTest" --tests "com.mooscles.UpdateNotificationRecreationHostTest" :app:lintDebug`.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS`. `compat-reviewer` (`timer/`, manifesto, canais; esperado `SEGURO`: o canal `rest` e a notificação 1 mantêm id e importância, o receiver é `exported=false`, nenhuma chave nova). Commit `timer: ongoing rest notification with actions and localized texts`.

### Task 26: O cartão de compartilhamento

**Files (decisão S1 = A):**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/share/ShareCardData.kt`, `.../share/ShareCardPainter.kt`, `screens/src/commonTest/kotlin/com/mooscles/screens/share/ShareCardDataTest.kt`, `screens/src/uiTest/kotlin/com/mooscles/screens/share/ShareCardPainterTest.kt`, `app/src/test/java/com/mooscles/sharing/ShareCardDataEquivalenceTest.kt` (só até o Step 4)
- Modify: `app/src/main/java/com/mooscles/sharing/WorkoutCardRenderer.kt` (desenho pelo painter, PNG por `Bitmap.compress`, mesmos arquivos e páginas), `app/src/main/java/com/mooscles/ui/WorkoutSharePreview.kt` (mostra o mesmo bitmap), `app/src/main/java/com/mooscles/sharing/WorkoutShareCard.kt` (sai no Step 4), `.github/workflows/ci.yml`
- **(decisão S1 = B):** só `WorkoutCardRenderer.kt` e `WorkoutCardRendererTest.kt`: as cores vêm de `MoosclesColors.Dark` e as fontes do APK (`Typeface.createFromAsset` nos arquivos de fonte do `:designsystem` em `assets/composeResources/…`); o cartão comum fica registrado para a fase 5 ou 7.

**Interfaces:**
- Produces (A): `data class ShareCardData(val workoutName: String, val dateText: String, val durationText: String, val setsText: String, val volumeText: String, val records: List<ShareRecordRow>, val rows: List<ShareExerciseRow>)` e `object ShareCards { fun from(data: AppData, workoutId: String, pounds: Boolean, locale: NumberLocale, zone: TimeZone, texts: ShareCardTexts): ShareCardData }` (mesmas regras do `WorkoutShareCards.from` de hoje, com `MoosclesNumbers` e `kotlinx-datetime` no lugar de `NumberFormat` e `java.time`).
- Produces (A): `class ShareCardPainter(styles: ShareCardStyles, density: Density) { fun paint(card: ShareCardData, measurer: TextMeasurer): List<ImageBitmap> }`: 1080 px de largura, as páginas do `WorkoutSharePreview` de hoje quando não cabe numa, fundo `MoosclesColors.Dark.bgBase` sempre, "MOOSCLES" com as anilhas, nome e data, Duração · Séries · Volume em Barlow Condensed, recordes em `prSolid` com o selo, exercícios com a melhor série. Roda fora da thread principal.
- O `:app` continua dono do cache privado, do `FileProvider`, da limpeza de 7 dias e do atalho do WhatsApp.

- [ ] **Step 1: Testes que falham (A).**
  - `ShareCardDataEquivalenceTest`: para os fixtures do `WorkoutShareCardTest` em `en-US` e `pt-BR`, `ShareCards.from` dá as mesmas linhas, melhores séries, contagens de recorde, duração e volume que `WorkoutShareCards.from`;
  - `ShareCardDataTest`: os mesmos casos com os valores esperados como literais (copiados da execução de equivalência);
  - `ShareCardPainterTest`: `everyPageIs1080Wide`; `longWorkoutUsesTheSamePageCountAsToday`; `backgroundIsDarkInBothThemes`; `brandIsDrawn`.
- [ ] **Step 2:** Rodar `./gradlew :app:testDebugUnitTest --tests "com.mooscles.sharing.*" :screens:testAndroidHostTest --tests "*ShareCardDataTest*"` e a classe de UI no emulador; ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Com a equivalência verde, apagar `WorkoutShareCard.kt` e `ShareCardDataEquivalenceTest`. `WorkoutSharePreviewHostTest`, `WorkoutShareExportIntegrationTest`, `WorkoutShareIntentTest` e `WorkoutCardRendererTest` (ajustado ao painter) verdes.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS`. Commit `share: the workout card drawn in common code`. **Push** (checkpoint das Tasks 23–26).

### Task 27: Ícone do launcher e build `preview`

**Files:**
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` (as duas anilhas de canto chanfrado formando o "OO", da prancha), `app/src/preview/res/values/strings.xml` (`app_name` "Mooscles Preview"), `app/src/test/java/com/mooscles/updates/UpdaterDisabledHostTest.kt`, `app/src/test/java/com/mooscles/LauncherIconHostTest.kt`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` e `ic_launcher_round.xml` (fundo, frente e `monochrome`), `app/src/main/res/values/launcher_colors.xml` (grafite), `app/build.gradle.kts` (tipo `preview`: `initWith(debug)`, `applicationIdSuffix = ".preview"`, `versionNameSuffix = "-preview"`, `buildConfigField("boolean", "UPDATER_ENABLED", "false")`; `true` em `debug` e `performance`), `app/src/main/java/com/mooscles/updates/UpdateManager.kt` (decisão 26)
- Delete: `app/src/main/res/drawable-nodpi/ic_launcher_*.png` e os `mipmap-*/ic_launcher*.png` (com `minSdk` 26, o adaptativo sempre vale)

**Interfaces:**
- Produces: `UpdateManager(…, enabled: Boolean = BuildConfig.UPDATER_ENABLED)`.

- [ ] **Step 1: Testes que falham.** `UpdaterDisabledHostTest.disabledSchedulesNoWorkAndChecksNothing` (com `enabled = false`, `onForeground()`, `requestCheck(true)` e `enqueueDownload(true)` não enfileiram nada no `WorkManager` de teste e não chamam o `ReleaseClient`); `debugKeepsTheUpdaterOn` (`BuildConfig.UPDATER_ENABLED == true`). `LauncherIconHostTest.adaptiveIconHasBackgroundForegroundAndMonochrome`.
- [ ] **Step 2:** Rodar `./gradlew :app:testDebugUnitTest --tests "com.mooscles.updates.UpdaterDisabledHostTest" --tests "com.mooscles.LauncherIconHostTest"` e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4: Verificar.**
  - `./gradlew :app:assembleDebug :app:assemblePreview :app:testDebugUnitTest --tests "com.mooscles.updates.*"` verde;
  - `aapt2 dump badging` no APK de `preview`: `package: name='com.mooscles.preview'`, `application-label:'Mooscles Preview'`; no `debug`: `name='com.mooscles'`, `versionCode='16'`, `versionName='1.4.5'`;
  - `apksigner verify --print-certs` nos dois: o mesmo certificado debug deste Mac;
  - `grep -rn "com.mooscles.workout-shares\|\"com.mooscles\"" app/src/main/java` não acha autoridade nem pacote fixo (o `FileProvider` usa `${applicationId}`);
  - a instalação lado a lado fica para a Task 30.
- [ ] **Step 5:** `compat-reviewer` (`app/build.gradle.kts`, `updates/`; esperado `SEGURO`: o `debug` distribuído não muda de `applicationId`, versão, assinatura nem comportamento do updater). Commit `app: new launcher icon and a side-by-side preview build`.

### Task 28: `ActiveWorkoutFlowTest` de ponta a ponta (D6)

**Files:**
- Create: `screens/src/uiTest/kotlin/com/mooscles/screens/flow/ActiveWorkoutFlowTest.kt`, `.../flow/FlowHarness.kt`, `screens/src/iosTest/kotlin/com/mooscles/screens/flow/FlowPlatform.ios.kt`, `screens/src/androidDeviceTest/kotlin/com/mooscles/screens/flow/FlowPlatform.android.kt`
- Modify: `.github/workflows/ci.yml` (no `ios`, upload de `screens/build/ios-captures/**` com `if-no-files-found: ignore`; `MIN_SCREENS_IOS_TESTS`)

**Interfaces:**
- Consumes: `InProcessRestTimer` (Task 8); `MoosclesViewModel`; `ActiveWorkoutRoute` e `WorkoutSummaryScreen`.
- Produces (só testes): em cada plataforma, `internal fun openFlowDatabase(directory: String): WorkoutDatabase` e `internal fun openFlowStore(directory: String): DataStore<Preferences>` (iOS: `openWorkoutDatabase` e `createMoosclesStore`; Android: Room com `AndroidSQLiteDriver` num arquivo temporário e `buildMooscles()`, e `PreferenceDataStoreFactory`), declaradas com o mesmo nome nos dois diretórios de teste (não é `expect`); `FlowHarness` monta os `MoosclesServices` (repositório real, `AppPreferences(store)`, `InProcessRestTimer`, `UpdateCoordinator` e `BackupDocumentStore` vazios, `Mutex`, `BackupApp` de teste) e `class FlowViewModel(services) : MoosclesViewModel(services) { override suspend fun install() {} }`, limpo por um `ViewModelStore` no fim.

- [ ] **Step 1: Teste.** `flowLightAtOneX` e `flowDarkAtTwoX` (decisão 27), cada um: semear uma rotina com três exercícios e um histórico com 85 kg × 6 no supino; `startWorkout(routineId)`; concluir uma série pré-preenchida; subir a carga pelo stepper e concluir; concluir por sugestão; bater o recorde semeado (87,5 kg × 6) e ver o aviso e o selo; ver o dock, +15 s, abrir o expandido, pular; remover e desfazer; adicionar e trocar um exercício; finalizar; ver o resumo com os números e o recorde. Em cada estado principal: todo nó clicável com rótulo; alvos ≥ 48 dp (56 nas ações do treino); `assertNumbersWhole()`. Em cada estado, tentar `onRoot().captureToImage()` e, no iOS, gravar o PNG (Skia `encodeToData`) em `screens/build/ios-captures/<estado>.png`; se `captureToImage` lançar, imprimir `FLOW capture unsupported` e seguir.
- [ ] **Step 2:** Rodar a classe no emulador até ficar verde. Corrigir na task dona qualquer defeito que o fluxo revelar, com um teste focado lá.
- [ ] **Step 3:** Subir `MIN_SCREENS_IOS_TESTS` (+2). Commit `screens: end-to-end active workout flow over the real view model, Room and DataStore`.

### Task 29: Orçamento de toques e varredura de acessibilidade e fonte grande

**Files:**
- Create: `screens/src/uiTest/kotlin/com/mooscles/screens/budget/TapBudgetTest.kt`, `screens/src/uiTest/kotlin/com/mooscles/screens/a11y/WorkoutAccessibilitySweepTest.kt`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: as contagens de `tap-baseline.md` (Task 24), copiadas como literais com o link no comentário.

- [ ] **Step 1: Testes.**
  - `TapBudgetTest`, um teste por fluxo, com o mesmo contador da Task 24; cada contagem ≤ a coluna "Novo" e ≤ a de hoje: `prefilledSet` 1; `sameAsLastWeekWithoutTarget` 1; `oneLoadStepUpAndComplete` 3; `oneRepLessAndComplete` 3; `addSetLikeAboveAndComplete` 2; `markWarmup` 2; `removeSetBySwipe` 1 e `removeSetBySheet` 2; `skipRestAndPlusFifteen` 1 cada; `finish` 2 (sem rolagem).
  - `WorkoutAccessibilitySweepTest`: os estados pendente, em edição, com recorde, com dock, expandido, pausado, resumo, barra de abas e barra do treino × claro e escuro × 1,0, 1,3 e 2,0 (mais 2,01 no Android) × 320 e 412 dp. Em cada combinação: rótulo em todo clicável; alvos de 48 dp e 56 nas ações do treino; `assertNumbersWhole()`; nenhum texto de botão cortado. Mais `readingOrderIsHeaderToastCardsDock` (a ordem de travessia: Minimizar, título, tempo e séries, Finalizar, Mais opções, o aviso de recorde quando houver, os cards em ordem e o dock por último) e `dockAndWorkoutBarAreTraversalGroups` (`isTraversalGroup` nos dois).
- [ ] **Step 2:** Rodar as duas classes no emulador até ficarem verdes, corrigindo na task dona.
- [ ] **Step 3:** Subir `MIN_SCREENS_IOS_TESTS`. Commit `screens: tap budget and accessibility sweep`. **Push** (checkpoint das Tasks 27–29). Conferir com `gh run watch` o `host` e o `ios` (inclusive o `ActiveWorkoutFlowTest` e as capturas, se houver).

### Task 30: Atualização por cima e desempenho

**Files:**
- Create: em `docs/superpowers/reports/AAAA-MM-DD-phase3-verificacao/`, `upgrade.md`, `preview-side-by-side.md`, `performance.md` e os traces e logs
- Modify: o que as medidas pedirem, em commits próprios com teste

- [ ] **Step 1: Testes instrumentados do `:app`.** Antes de tudo (o `connectedDebugAndroidTest` desinstala o app no fim), rodar `./gradlew :app:connectedDebugAndroidTest` no emulador: `UiFlowTest` e `UiFeatureSmokeTest` verdes.
- [ ] **Step 2: Atualização por cima** (rede do emulador desligada): com o APK da `main` em `4f2911c`, em pt-BR e em libras, criar um treino a partir de uma rotina com três exercícios, quatro séries concluídas, uma removida e o descanso correndo; exportar; `md5` de `files/datastore/local_preferences.preferences_pb` (`run-as com.mooscles`); `adb install -r` do APK da branch. Conferir e registrar em `upgrade.md`: as mesmas séries; o dock chega a "Descanso concluído" no horário salvo; o mesmo MD5; a notificação em andamento ao ir para segundo plano, com as ações; finalizar e o resumo com os números certos; o export depois igual ao de antes, tirando `exportedAt` e o treino finalizado (`diff` dos JSON).
- [ ] **Step 3: `preview` lado a lado.** Instalar o APK `preview` com o debug já instalado: dois ícones, "Mooscles Preview" abre vazio, o `md5` do `local_preferences` do `com.mooscles` não muda, e o `logcat` do preview não mostra nenhuma checagem de atualização. Registrar em `preview-side-by-side.md`.
- [ ] **Step 4: Desempenho** pelo procedimento do PERFORMANCE.md, no `assemblePerformance`, com a base grande (`mooscles-large-208.json`, 208 treinos, 12.688 séries) e um treino ativo de 6 exercícios × 5 séries, no mesmo emulador:
  - toque no check até o disco cheio: no quadro seguinte, sem `Mooscles:present` nem trabalho de banco nesse quadro (`atrace`);
  - "Começar" até o skeleton (quadro seguinte) e até o treino editável, contra a 1B;
  - rolagem rápida do treino grande: p95 do quadro ≤ 1,10 × a base do PERFORMANCE.md;
  - `present()` depois de concluir uma série: sem piora contra a 1B;
  - `gfxinfo` nas molas (concluir, recorde, resumo) e na rolagem;
  - as recomposições por tick vêm dos testes da Task 20.
  Registrar tudo em `performance.md`, com o que ficou só no emulador.
- [ ] **Step 5:** Commit `docs(report): phase 3 upgrade, preview and performance evidence`.

### Task 31: Capturas, crítica de tela e TalkBack

**Files:**
- Create: em `docs/superpowers/reports/AAAA-MM-DD-phase3-verificacao/`, `workout-{pending,editing,keyboard,record,dock,expanded,paused}-{light,dark}-{en,pt}.png`, as mesmas a 200% em pt, `summary-*.png`, `share-card-*.png`, `tabs-*.png`, `workout-bar-*.png`, `talkback.md`; `docs/superpowers/reports/AAAA-MM-DD-phase3-critica-treino.md`
- Modify: o que a crítica pedir, nos arquivos do `:screens` e do `:designsystem`, com teste quando a correção for de comportamento

- [ ] **Step 1: Capturas** com `adb exec-out screencap -p`, nos dois temas, em en e pt-BR (`adb shell cmd locale set-app-locales com.mooscles --locales pt-BR`) e a 200% (`adb shell settings put system font_scale 2.0`).
- [ ] **Step 2: Crítica.** `critique-screen` (pelo `kit-design`) nas capturas do treino e do resumo, `better-interface` na casca e no cartão. Corrigir as de prioridade alta e média, com nova captura; as baixas ficam no relatório com o motivo.
- [ ] **Step 3: TalkBack e aparelho.** Se o dono topar (pergunta 2 da spec), ele instala o `preview` no aparelho e percorre os 11 itens da spec (inclusive a candidata do S2, se houver); o resultado vai para `talkback.md`. Sem ele, os itens ficam como não verificados, e `talkback.md` registra a ordem e as ações tiradas do `uiautomator dump`.
- [ ] **Step 4: Verificar.** As classes de `uiTest` do `:screens` e do `:designsystem` verdes no emulador depois das correções; `check-design-tokens` limpo.
- [ ] **Step 5:** Commit `docs(report): phase 3 workout critique`, com as correções em commits próprios (`screens: …`, `designsystem: …`).

### Task 32: Documentação, relatório e revisão final

**Files:**
- Create: `docs/superpowers/reports/AAAA-MM-DD-phase3-verificacao.md`
- Modify: `AGENTS.md` (Arquitetura com o `:screens` e o que ele guarda; os comandos `:screens:testAndroidHostTest`, `:screens:connectedAndroidDeviceTest` e `:screens:iosSimulatorArm64Test`; o `app/compose-stability.conf` lido pelo `:app` e pelo `:screens`; o guard de tokens cobrindo o `:screens`; onde ficam as strings; as rotas e a casca; os canais `rest` e `rest_progress` e os ids 1 e 2; o build `preview`; os mínimos do CI), `README.md`, `VERIFICATION.md`, `UPDATES.md` (o `preview` não se atualiza), `docs/superpowers/ROADMAP.md` (fase 3 com plano, relatórios e status; o "alvo visível" movido para a fase 4, datado), a spec (seção "Notas de implementação", datada, com cada decisão deste plano que a detalha ou desvia: 3, 5, 7, 12, 13, 15, 17, 19, 21, 25, 26 e 27, e as decisões dos spikes), a spec da fase 2 (nota da sequência dupla de háptico de recorde, como a spec da fase 3 pede)

- [ ] **Step 1: Relatório.** **Provado:** testes de host e de `commonTest` com os números do CI; o `ios` verde com o `ActiveWorkoutFlowTest` e as capturas (ou o registro de que `captureToImage` não funcionou); o emulador (Tasks 9–31); o orçamento de toques contra `tap-baseline.md`; a atualização por cima; o `preview` lado a lado; o desempenho; o orçamento de APK com os números; o diff de dependências. **Não verificado:** tudo num iPhone (VoiceOver, háptico, teclado real, gesto de voltar, desempenho), o caminho da release com a chave original, os itens de TalkBack e de aparelho que ninguém percorreu, os 60 fps num aparelho de referência (pergunta 1), e os nomes do catálogo e as mensagens do updater e do backup em inglês (D22).
- [ ] **Step 2:** Atualizar os documentos da lista.
- [ ] **Step 3: Verificar.** Os links do ROADMAP e dos relatórios abrem; o último run da branch está verde.
- [ ] **Step 4:** `compat-reviewer` na branch inteira (`git diff origin/main...HEAD`; esperado `SEGURO`) e `/code-review`. Commit `docs: phase 3 active workout verified`. **Push**. O merge na `main` do fork fica com o controlador, depois do CI verde e do `/code-review` sem achado de prioridade alta.

## Paralelismo

No máximo dois implementadores ao mesmo tempo, em worktrees separadas, e no máximo um deles usando o emulador.

| Par | Por que pode | Cuidado |
| --- | --- | --- |
| Task 1 ∥ Task 4 | branch do spike × `:shared` e `:app` (host) | — |
| Task 2 ∥ Task 5 | idem | a outra lane pausa o Gradle durante o Step 3 (TalkBack por `adb`) da Task 2 |
| Task 3 ∥ Task 6 | CI do spike e relatório × `:shared` (host) | — |
| Task 7 ∥ Task 8 | arquivos novos e disjuntos em `ui/` e `timer/` | os dois sobem `MIN_HOST_TESTS` e `MIN_IOS_TESTS`: o controlador soma |
| Task 10 ∥ Task 11 | `:screens` (host) × `:designsystem` (emulador) | mínimos em linhas diferentes |
| Task 12 ∥ Task 27 | `:designsystem` (emulador) × `:app` (só host; a instalação fica na Task 30) | — |
| Task 25 ∥ Task 26 | `timer/`, manifesto e `MainActivity` × `sharing/`, `WorkoutSharePreview` e `screens/share` | os dois sobem `MIN_HOST_TESTS` |

Todo o resto é sequencial: as Tasks 4–6 tocam `MoosclesViewModel.kt` e `UiContract.kt`; as Tasks 13–23 usam o emulador e, de 14 a 21, o mesmo `ActiveWorkoutState.kt`; a Task 24 precisa de 19, 22 e 23; a 25 precisa do parâmetro `workoutRequest` da 24; as 28–31 precisam de tudo.

## Pré-voo: arquivos e interfaces compartilhados

| Tasks | O que compartilham | Nomes e assinaturas batem? |
| --- | --- | --- |
| 4 → 6 | `DataRuleException`, `DataError.RESTORE_UNAVAILABLE`, `UiError.SET_NOT_RESTORED`, `MoosclesViewModel.kt`, `UiContract.kt` | sim: a 6 só consome os três nomes que a 4 produz |
| 4 → 8 | `RestTimer.errorCode: StateFlow<RestTimerError?>`, `RestTimerError.STORAGE_UNAVAILABLE` | sim |
| 4 → 19 | `UiState.errorCode: UiError?` e os 12 valores de `UiError` | sim: `uiErrorText` cobre os 12 |
| 5 → 7 | `PreviousSetUi(weightKg, reps, warmup, assistance)` | sim |
| 5 → 21 | `SetUi.record: SetRecordUi?`, `SetRecordUi(kind, previousKg, previousReps)`, `RecordKind` | sim |
| 7 → 14 | `SetSuggestions.forExercise/pairPrevious/effective`, `SetField`, `SetSuggestion`, `EffectiveSet` | sim |
| 9 → 14…29 | `withScreen(…)`, `RecordingActions.calls` (formato `"método:args"`), `assertShownWhole`, `assertNumbersWhole`, `RecompositionProbe` | sim: o formato `"updateSet:s2,8,82.5,true,false,false"` é o mesmo em 14, 15, 16 |
| 9 → 17, 18 | `EquipmentIcons.forEquipment`, `muscleLabel`, `equipmentLabel`, `conventionCaption`, `conventionExplanation` | sim |
| 10 → 17, 19, 22, 26 | `shortDate`, `summaryDateRange`, `durationText`, `Durations.clock`, `ScreenDates.finishedInLast28Days` | sim |
| 11 → 23 | `MTab`, `MTabBar(tabs, selectedKey, onSelect, onReselect, modifier, enabled)`, `ActiveWorkoutBarState`, `ActiveWorkoutBar(state, onOpen, onSkipRest)` | sim |
| 11 → 23, 24 | tags `tab-<key>` com `key` = rota (`routines`, `library`, `history`, `records`) e `app-footer` | sim: `ShellTabs` usa as mesmas rotas |
| 12 → 14, 15, 16 | `SetRowState.loadSuggested/repsSuggested`, `SetType.Assisted`, `SetRow(onEditRow, onCopyPrevious)`, `Modifier.setRowSwipe`, `NumericField(selected, imeAction, onImeAction, focusRequester)`, `Stepper(onValueChangeFinished)` | sim |
| 13 → 17, 20 | `ExerciseCardHeader/Segment/Footer`, `RestTimerDock(onOpen, canAdd)`, `RestTimerCompact`, `RestTimerExpanded(title, totalText, onPauseResume, onRestart, onClose, canAdd)`, `contentMaxWidth`, `cardAdvance` | sim |
| 14 → 15, 16, 17, 21 | `ActiveWorkoutScreenState` e `rememberActiveWorkoutScreenState()` em `ActiveWorkoutState.kt` (cada task acrescenta campos) | sim; sequenciais, sem conflito |
| 16 → 17 | `RemovedSet(entryId, position, set, number)` em `ExerciseCallbacks.onRemoveSet` | sim |
| 19 → 24 | `ActiveWorkoutRoute(state, timer, actions, onMinimize, onOpenSummary, onExerciseHistory, onChooseRoutine, modifier, skeletonCards, imeInsets, nowMillis)` | sim |
| 22 → 24, 26 | `WorkoutSummaryScreen(workout, history, pounds, onDone, onShare, onDetails, …)`; `onShare` abre o `WorkoutSharePreview`, que a 26 troca por dentro | sim |
| 23 → 24 | `AppShellBottom(state, timer, selectedTab, onSelectTab, onReselectTab, onOpenWorkout, actions, …)` | sim |
| 24 → 25 | `MoosclesApp(…, workoutRequest: Long = 0L)` | sim |
| 25 ∥ 26 | `.github/workflows/ci.yml` (`MIN_HOST_TESTS`) | conflito de uma linha, resolvido somando |
| 4 → 25 | `RestTimerController.kt` (4: `report()` e `errorCode`; 25: notificação) | sim; a 25 mantém o `report()` da 4 |
| 4 → 27 | `UpdateManager.kt` (4: `TrainingBlockedException`; 27: `enabled`) | sim; a 25 não toca nesse arquivo, então 25 ∥ 26 e 27 não se cruzam nele |
| 6 → 9 | `MoosclesActions.restoreSet`, que o `RecordingActions` implementa | sim: a 9 vem depois da 6 e já implementa a interface com `restoreSet` |
| 26 → 30, 31 | o painter e o `WorkoutSharePreview` usados nas capturas `share-card-*` | sim |


## Notas

- **2026-10-29, varredura prévia.** Antes da Task 3, uma varredura só de leitura cruzou o plano com a spec e com o código pós-fase 2 e encontrou 41 pontos (F1–F41). Cada task segue as decisões que levam o seu número; a tabela fica no workspace de execução, fora do git. As que mudam o texto acima:
  - **F1:** a notificação em andamento do descanso usa o id 3 (o 2 é do updater). Desvio do D16, registrado na spec e no AGENTS.md na Task 32.
  - **F2:** `app_name` com `translatable="false"` e `share_strings.xml` com `tools:ignore="MissingTranslation"`, para o lint não quebrar quando a Task 25 criar `values-pt-rBR`.
  - **F4:** o formatador do CMP só troca `%N$s` e `%N$d`; nada de largura (`%2$02d`). O `StringsParityTest` passa a reprovar isso.
  - **F9:** no CI, os relatórios do iOS são enviados também com o job verde (`if: !cancelled()`), para os spikes e as capturas da Task 28.
  - **F23:** cada lane paralela tem a sua branch; o controlador mescla. O relatório dos spikes (Task 3) entra na branch da fase depois da Task 6.
  - **F25:** a Task 4 leva o `MIN_HOST_TESTS` à contagem real (≥ 624), e as Tasks 11, 13 e 27 o sobem.
- **2026-10-29, Task 9.** Três decisões da varredura mudam o texto acima:
  - **F27:** a pasta de evidências nasce na Task 9 (Step 1, `deps-before.txt`), não na 24. Recebeu a data da fase, `2026-10-29-phase3-verificacao`, como os outros documentos da fase 3, embora o relógio da máquina marcasse 2026-10-28 quando ela foi criada.
  - **F35:** o caminho padrão do `check-design-tokens.ps1` **acrescenta** `screens/src/commonMain/kotlin/com/mooscles/screens` à lista atual (DS e tela piloto), sem tirar nada; as fixtures continuam com 8 linhas.
  - **F41:** `withScreen` ganha `density: Float? = null`, como o `withTheme` do DS, para reproduzir no Android as medidas do iOS (com `LINEAR_TWO_X_FONT_SCALE`, 2,01).
- **2026-10-29, resultados dos spikes S1 (Android) e S2.** S1 no Android: PNG igual ao bitmap, mediana 446 ms, thread principal livre; a metade iOS é decidida na Task 3. S2: nenhuma estratégia leva o rótulo ao nó editável e o TalkBack do emulador não pôde ser conduzido, então, pela regra de decisão, o `MTextField` não muda e o #11 continua aberto.
- **2026-10-29, fechamento (Task 32).** O que a execução mudou no texto acima. As decisões que detalham ou desviam da spec estão nas [notas de implementação da spec](../specs/2026-10-29-phase3-active-workout-design.md#notas-de-implementação); aqui ficam as que são só do plano:
  - **Decisão 1, push:** além dos checkpoints, houve pushes extras para provar no iOS mais cedo o que só o simulador decide (depois da Task 12, o `ActiveWorkoutFlowTest` da Task 28 e a correção iOS da Task 29, numa lane própria). As lanes paralelas rodaram em worktrees com branch própria (`lane/phase3-tN`) e o controlador mesclou, somando os mínimos do CI (F23).
  - **Decisão 10, `kotlinx-datetime`:** entrou na versão 0.8.0, no `commonMain` do `:screens`. Na Task 26 o escopo passou de `implementation` para `api`, porque o `ShareCards.from` recebe o `TimeZone` do `:app`; o artefato já ia no APK, então nada mudou no `debugRuntimeClasspath`.
  - **Task 14 → Task 19, check otimista:** o estado otimista não vai para o `Saver` (não sobrevive à morte do processo), e um erro de gravação o descarta (`dropOptimistic`, `dropAllOptimistic`, que também descarta as gravações pendentes da edição). A Task 19 chama isso quando chega um `errorCode` (`failedCheckClearsTheDisc`).
  - **Task 20, destino de `progressSemanticsReflect…`:** a tabela "Destino dos testes atuais" o porta para `WorkoutTimerTest.progressSemanticsFollowRunningPausedAdjustedResetAndDone`, mas o teste novo não confere um valor de progresso: o anel do timer não expõe `ProgressBarRangeInfo`, porque um valor de progresso que muda a cada segundo seria lido pelo leitor de tela a cada tick, contra a regra "anuncia ao começar, ao ajustar e ao terminar". O teste confere o estado e os anúncios. Os limites do progresso (restante maior que o total, total 0) ficaram em `RestTimerRingMathTest`.
  - **Restauração no iOS (Task 16):** o `StateRestorationTester` lança `NotImplementedError` no Skiko. Os testes de recriação usam o `ScreenRestoration` do `:screens`, que no Android embrulha o `StateRestorationTester` e no iOS recompõe com um `SaveableStateRegistry` próprio.
  - **Contador de recomposição (Task 23):** o `RecompositionProbe` virou `@NonRestartableComposable`; antes ele só contava a primeira composição, e um teste de "zero recomposições" passaria sem provar nada. Os testes de tick têm controle positivo (o `dock`, a barra ou o `clock` sobem).
  - **Gestos no iOS (Task 12):** o Skiko não entrega `cancel()` de toque, então o teste do arrasto cancelado só roda onde o toque cancelado existe (`INJECTS_TOUCH_CANCEL`, falso no `iosTest`). Como um `touchesCancelled` real chega no iPhone fica para a fase 7.
  - **Fonte grande nos testes Android (Task 29):** os diálogos e as sheets dos testes de UI do Android ignoram a escala de fonte do quadro de teste. É limite do harness; a sheet do tipo e o seletor a 200% foram vistos nas capturas da Task 31, com a escala de fonte do sistema em 2,0.
  - **Mínimos do CI:** cada task subiu o mínimo pelo número de testes novos. No fim, o `MIN_HOST_TESTS` foi levado à contagem real do CI (F25), com a conta no comentário do `ci.yml`.
  - **Decisão 28, versão:** `versionCode` 16 e `versionName` 1.4.5 continuam. A release v1.5.0 depende da chave original de assinatura, que não está neste Mac.
