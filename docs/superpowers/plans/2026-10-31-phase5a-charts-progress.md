# Fase 5A: gráficos, progresso por exercício e linha do tempo de recordes, plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar ao app a fundação de gráficos própria (linha, barras, sparkline em `Canvas`, provada pelo spike S1), o 1RM estimado, um índice de progresso derivado do `UiState` fora da thread principal, a tela "Progresso do exercício" (rota `exercise/{id}?metric=`), a aba Recordes como linha do tempo e a sparkline no card "Seu histórico" do detalhe, tirando do `:app` as telas antigas `RecordsScreen` e `ExerciseHistoryScreen`, sem mudar dados, chaves, rede nem identidade do app.

**Architecture:** O `:designsystem` ganha o pacote `charts/` (componentes sem `:shared` nem `kotlinx-datetime`: recebem números na unidade de exibição e textos prontos), com tokens, ícones e a página "Gráficos" da galeria. O `:shared` ganha só `domain/EstimatedOneRepMax.kt`. O `:screens` ganha `progress/` (regras de série de trabalho sobre `SetUi`, o `ProgressIndex`, as consultas puras, a tendência, o cache único e a tela do exercício) e `records/` (consulta e aba), mais `format/WeekStart.kt`. O `:app` guarda o cache no `MoosclesApplication`, o entrega por `LocalProgressIndexCache`, hospeda as rotas novas e apaga as telas antigas. A etapa fecha com o fluxo de ponta a ponta no simulador, o orçamento de toques, a varredura de acessibilidade, uma rodada única das suítes de aparelho, a atualização por cima, sem rede, desempenho, tamanho, crítica e documentação.

**Tech Stack:** Kotlin 2.4.21 Multiplatform, AGP 9.3.3, Gradle 9.7.1, Compose Multiplatform 1.12.2 (`foundation`, `ui`, `animation`, `components-resources`; `Canvas`, `drawWithCache`, `TextMeasurer`; testes com `androidx.compose.ui.test.v2.runComposeUiTest`), Jetpack Compose 1.12.1 no `:app`, `kotlinx-datetime` 0.8.0 (só no `:screens`), `kotlinx-coroutines-test` 1.11.0, Room 2.8.5 (schema **v6**, sem mudança), JUnit 4 + Robolectric 4.17, `kotlin.test`, GitHub Actions (`ubuntu-latest` e `macos-26` com Xcode 26.4). Nenhuma dependência nova.

**Spec:** [docs/superpowers/specs/2026-10-31-phase5-progress-history-design.md](../specs/2026-10-31-phase5-progress-history-design.md) (etapa 5A: D1–D11, D15–D19, "Telas da 5A", "Arquitetura", "Dados e compatibilidade", "Desempenho", "Orçamento de APK", "Orçamento de toques", S1 e "Como verificar"). Estrutura e convenções: [plano da 4B](2026-10-30-phase4b-routines.md) e [plano da 4A](2026-10-29-phase4a-library.md).

**Fora deste plano (plano próprio da 5B):** calendário (`MonthCalendar`, S2), aba Histórico (segmentos, filtros, segmento Progresso com barras semanais e músculos, D9, D12, D17), detalhe e edição do treino salvo (D13), prévia de compartilhamento (D14) e as peças antigas que só eles usam (`HistoryScreen`, `WorkoutDetailScreen`, `WorkoutScreens.kt`, `MissingScreen`, `share_strings.xml`). O `MBarChart` nasce aqui (é do S1), mas só a galeria e os testes o usam até a 5B.

## Decisões travadas por este plano

As decisões da spec valem como estão. Estas são as que a spec deixou para quem implementa e os conflitos achados. Todas são **minhas**, com a delegação do dono. A task que muda uma decisão da spec escreve a nota datada nela (ver "Notas datadas").

1. **Branches e push.** O controlador mescla `docs/phase5a-plan` na `main` do fork e cria `feat/phase5a-progress` a partir dela antes da Task 1; edita o `tools/apk-budget.json` (D18): `baseCommit` = o commit da `main` onde a branch nasce, `baseApkSource` = o artefato `app-debug-apk` do CI verde desse commit, `baseApkBytes` = o tamanho dele, `maxGrowthBytes: 524288` (5A e 5B somadas; a 5A mira 262.144). O Vico, se o S1 reprovar, só em `spike/phase5a-vico`, nunca mesclada. **Push só em três checkpoints:** fim da Task 4 (Push 1, traz o veredito iOS do S1), fim da Task 15 (Push 2, todo o código e os testes antes da rodada de aparelho) e Task 18 (Push 3). Não faça push com o job `ios` do anterior na fila ou rodando, a não ser que ele já esteja perdido.
2. **O S1 é o componente de verdade** (como o S2 da 4B). Os gráficos nascem na Task 1, na própria branch, e os testes do spike são os testes dos componentes. O veredito Android sai na Task 1; o iOS, do CI do Push 1, **antes da Task 9** (a primeira tela com gráfico). As Tasks 2–8 não dependem dele. O veredito é tomado depois de no máximo duas rodadas de correção da Task 1. O item (c) do S1 no emulador tem controle: a mesma medida de `gfxinfo` na rolagem da Biblioteca; se o controle também passar de 5%, o item fica "inconclusivo pela carga do Mac" e não reprova, e o contador de `Path` decide. Reprovado, vale a Task 1R.
3. **O índice da 5A guarda só o que as telas da 5A leem:** as sessões por exercício e convenção, os eventos de recorde e a busca dos recordes. Os campos por treino do D15 (regiões, rotina, texto dobrado, baldes semanais e mensais) entram no plano da 5B, sobre a mesma classe. `ProgressIndex` e o cache são públicos (`com.mooscles.screens.progress`), porque o `:app` os guarda e um teste de host do `:app` os confere.
4. **Regras de série sobre `SetUi`**, em `progress/WorkingSets.kt`, espelhando o `WorkoutCalculations` (que fica intocado, e `workingSet` continua privada): válida = concluída, `reps > 0`, carga finita `>= 0`; de trabalho (carga) = válida, sem aquecimento, sem assistência, convenção diferente de Assistência, carga `> 0`; de repetição = válida, sem aquecimento, no grupo de assistência da série (`set.assistance || convenção == Assistência`); volume = válida, sem assistência, convenção diferente de Assistência → `carga × reps`. A prova é o `ProgressIndexDomainHostTest` do `:app` (Task 7), contra o domínio real na base grande. O `:shared` só ganha `domain/EstimatedOneRepMax.kt`, fora da lista do `compat-reviewer`.
5. **Métricas:** `enum class ProgressMetric(val route: String) { LOAD("load"), REPS("reps"), E1RM("e1rm"), VOLUME("volume") }`. `LOAD` é a medida de carga da convenção: Carga (Carga total, Por mão, Um lado), Carga adicional (peso corporal) ou Assistência. Ordem (a primeira é a padrão): Carga total, Por mão, Um lado → `LOAD, E1RM, VOLUME`; Carga adicional → `REPS`, mais `LOAD` e `VOLUME` só se alguma sessão tiver carga `> 0`; Assistência → `REPS, LOAD`. Um rótulo de convenção que o `LoadConvention.fromLabelOrName` não conhece segue as regras de Carga total.
6. **Assistência por sessão** (D6, "a menor assistência registrada"): a menor `weightKg > 0` entre as séries de repetição da sessão; uma sessão só com 0 não tem ponto. Na redução semanal (decisão 7), a semana da Assistência guarda o mínimo, não o máximo.
7. **Redução acima de 104 pontos (D3):** um ponto por semana (início pelo `WeekStart`, fuso da tela), com o melhor valor da semana (máximo; mínimo na Assistência), a data do início da semana, `workoutId = null` e a marca de recorde se alguma sessão da semana tem marca. A tabela diz "Semana de 6 out" e a nota "um ponto por semana". O teste de equivalência das marcas (D6) roda no modo por sessão. *(2026-10-31, controlador, depois da revisão da Task 5: a marca do ponto semanal é a da sessão que deu o valor da semana, não "se alguma sessão tem"; num empate de valor vale a sessão com marca. Ver a nota datada na spec.)*
8. **Recorde de convenção antiga (D2):** sem argumento de convenção na rota (o D2 proíbe), a tela não sabe qual recorde foi tocado. A frase do D2 ("Este recorde foi registrado como…") sai; fica só a linha do D6 ("Os treinos registrados como Por mão não entram neste gráfico."), que aparece sempre que o exercício tem sessões em outra convenção e já diz o mesmo.
9. **Cache único:** `ProgressIndexCache` criado uma vez no `MoosclesApplication` (escopo `appScope`, `Dispatchers.Default`), entregue por `LocalProgressIndexCache` na `MainActivity`. O `MoosclesApp` chama `prewarm` a cada nova identidade de `(history, records)` fora do carregamento; o prewarm espera 500 ms, constrói o índice e já calcula a consulta padrão dos Recordes. Sem provedor (testes), `rememberProgressIndex` usa um cache da própria composição. Enquanto o índice novo não fica pronto, a tela mostra o anterior (o padrão do `rememberLibraryModel`); o índice é todo em kg, então a unidade da tela nunca mistura.
10. **Peças antigas que saem na 5A:** `RecordsScreen` e `ExerciseHistoryScreen` (`HistoryScreens.kt`) e, por ficarem sem chamador, `CustomExerciseDialog` e `ChoiceFilter` (`LibraryScreens.kt`; a tabela do D19 os punha na 5B, mas a regra do D19 é "o que fica sem chamador sai junto"). Editar um personalizado a partir do progresso passa por "Sobre o exercício" → "Editar exercício" no detalhe (um toque a mais que o lápis da tela antiga; o fluxo não está no orçamento da spec). `SearchField`, `ExercisePicker`, `MissingScreen` e o resto ficam para a 5B.
11. **A 5A tem a sua rodada única de aparelho** (Task 16: suítes completas, atualização por cima, sem rede, desempenho e tamanho), além da do fim da fase depois da 5B (spec, "Uma vez, no fim da fase"). *Por quê:* a 5A entra sozinha na `main` (D1) e os testes de UI do Android não rodam no CI; mesclar sem uma rodada de aparelho deixaria a `main` sem prova no Android.
12. **Sem release nem versão nova:** `versionCode` 16 e `versionName` 1.4.5 não mudam (spec, "Release publicada" fora de escopo). As notas seguem a convenção existente: a 5A vira a linha 10 da tabela "O que entra" do rascunho cumulativo [`releases/v1.5.0.md`](../releases/v1.5.0.md); não se cria `v1.6.0.md`.
13. **Tags e nomes antigos continuam:** `exercise-menu-history` (menu do treino ativo), `exercise-info-history-full` e `exercise-info-menu-history` (detalhe), e o parâmetro `onOpenHistory` do `ExerciseInfoRoute`; só os textos e o destino mudam.
14. **Ícones novos:** `Table` (Lucide `table`) e `ChartLine` (Lucide `chart-line`), gerados pelo `tools/icons/gen.py` a partir de `npm pack lucide-static@1.63.0` extraído numa pasta nova do scratchpad (`python3 -I tools/icons/gen.py <pasta>/package/icons designsystem/src/commonMain/kotlin/com/mooscles/designsystem/icons`). "Ver gráfico" e os itens de menu de progresso usam `ChartLine`; "Ver dados", `Table`.
15. **Os componentes de gráfico recebem valores na unidade de exibição** (o `:screens` converte kg → lb antes), e o `ChartScale` calcula as marcas sobre esses valores, então o eixo em lb é redondo (D7). Os rótulos chegam formatados por uma função de quem chama.
16. **Tendência (D10):** `x` em dias desde o primeiro ponto; variação projetada = inclinação × (último `x` − primeiro `x`), comparada a ±5% da média dos `y`; média zero ou menos de 3 pontos → sem tendência.
17. **Período sem ponto** numa métrica que não é o 1RM (por exemplo, "3 meses" com a última sessão há um ano): `progress_no_points` ("Nenhum treino com essa medida neste período."). A spec só previa a frase do 1RM.
18. **"Atual"** (D11): um evento de recorde é atual quando `UiState.records` tem um `RecordUi` com o mesmo `exercise.id`, `exercise.convention`, `kind`, `assistance` e `workoutId`, e a mesma carga (`BestSets.sameLoad`).
19. **Chips:** métricas em `MChipKind.Segmented` (`Role.Tab`), períodos em `MChipKind.SetType` (`Role.RadioButton`), filtros dos Recordes em `MChipKind.Filter` (`Role.Checkbox`), como a spec pede.
20. **O `AGENTS.md` diz "schema atual v5"**, mas o código e a spec estão na v6 (desde a 4B). A Task 18 corrige o documento; nada de schema muda aqui.

## Global Constraints

- **Dados (D16):** nenhuma mudança em Room (continua `version = 6`, nenhum JSON novo em `shared/schemas`), DataStore (nenhuma chave nova ou renomeada; `local_preferences.preferences_pb` com o mesmo MD5 depois da atualização), backup, manifesto, permissões, `<queries>`, `FileProvider`, `applicationId`, assinatura, `versionCode`, `update.json` e `ReleaseUrls.REPOSITORY`. `git diff --stat main -- shared/src/commonMain/kotlin/com/mooscles/data shared/src/commonMain/kotlin/com/mooscles/timer app/src/main/java/com/mooscles/timer shared/src/androidMain app/src/main/java/com/mooscles/updates app/src/main/java/com/mooscles/AppPreferences.kt shared/src/commonMain/kotlin/com/mooscles/AppPreferences.kt shared/build.gradle.kts app/build.gradle.kts` volta vazio; se não voltar, roda o `compat-reviewer`.
- **Contrato:** `UiContract.kt`, `WorkoutPresentation` e o `present()` não mudam; nenhuma classe nova do `:shared` vira parâmetro de composable; os modelos novos do `:screens` são imutáveis (`@Immutable` ou só `val` de tipos estáveis).
- **Rede:** nenhuma requisição nova.
- **Dependências:** nenhuma nova; o `debugRuntimeClasspath` do `:app` fica igual. Nada de Material 3 no `:screens` nem no `:designsystem`; nenhuma biblioteca de gráficos.
- **Desempenho (D15):** nenhum cálculo de índice ou consulta na composição nem na thread principal: tudo em `Dispatchers.Default`, memorizado; a composição só lê resultados prontos e mostra `Skeleton` até eles chegarem.
- **APK debug:** até 524.288 bytes sobre a base nova (decisão 1), 5A e 5B somadas; a 5A mira 262.144; fontes e ícones com os tetos de hoje.
- **Um design só:** toda tela, componente e texto novo em `commonMain`; nenhum `expect/actual` novo. `grep -rnE "\b(java|javax|android)\.|System\.|String\.format" screens/src/commonMain designsystem/src/commonMain/kotlin/com/mooscles/designsystem/charts` volta vazio.
- **Tokens:** nenhum literal de cor, medida ou duração fora de `tokens/`; o `tools/check-design-tokens.ps1` passa a cobrir `designsystem/.../charts`; medida nova vira token com o valor fixado em `commonTest`.
- **Acessibilidade (D4, "Acessibilidade, fonte grande e movimento"):** alvos de 48 dp com 8 dp entre alvos; nada abaixo de 12 sp; todo botão de ícone com rótulo; cada gráfico é um nó só com `Role.Image` e o resumo falado; "Ver dados" visível com a tabela de uma linha por nó; nada só por cor; pares de cor novos no `ComponentColorsTest`; nenhum número cortado (`assertShownWhole`/`assertNumbersWhole`) a 1,0, 1,3 e 2,0 em 320 e 412 dp, e a 2,01 no Android; o gráfico cresce com a escala da fonte até 1,5 vez o token; movimento reduzido sem cross-fade.
- **Textos:** inglês base e pt-BR, voz da fase 2 (direto, "você", sem exclamação, sem emoji, sem culpa nem comparação negativa). Strings novas em `screens/src/commonMain/composeResources/values*/strings_progress.xml` e `strings_records.xml`, acréscimos em `strings_dates.xml`, `strings_exercise.xml` e `strings_workout.xml`; no `:designsystem`, `ds_chart_*` em `strings_components.xml` e `gallery_page_charts` em `strings_gallery.xml`. Só `%N$s` e `%N$d` (números chegam formatados como texto); plurais por `pluralCount` (o 0 no plural). Nomes de exercício por `displayName`/`exerciseName` (catálogo no idioma da tela, também nos treinos antigos). Nenhum texto desta etapa em inglês com o aparelho em pt-BR, menos os nomes digitados.
- **Lições que viram regra:** `runComposeUiTest` da `v2` com espera explícita; `useUnmergedTree = true` nos campos; recriação pelo `ScreenRestoration`; contador pelo `RecompositionProbe` com controle positivo; **nunca rolar uma lista lazy com o relógio do teste parado**; testes de sheet passam pelo `MOverlayWindow` e um teste "2×" confere a janela a 2×; estado de harness gravado num `SideEffect` sem rastreio não prova nada (confira o nó, não a variável).
- **Processo (cadência do dono para a fase 5, revisão por risco):**
  - ambiente de toda chamada de shell: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) ANDROID_HOME=$HOME/Android/sdk ANDROID_SERIAL=emulator-5556 PATH="$HOME/.local/bin:$PATH"`;
  - **todo comando do Gradle ou do aparelho tem limite** por `perl -e 'alarm shift; exec @ARGV' <s> …`: 900 s para testes de host focados, compilação e builds, 1800 s para uma classe no aparelho, 5400 s para as suítes completas da Task 16;
  - **em cada task:** (1) os testes de host focados no que a task tocou; (2) `:<módulo>:compileTestKotlinIosSimulatorArm64` dos módulos tocados (compila sem Xcode; os testes iOS só rodam no CI); (3) no emulador, **só a classe de `uiTest` que a task criou ou mudou, e só quando a task muda comportamento de tela**, com `-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>[,…]`; (4) `pwsh tools/check-design-tokens.ps1` quando mexe em tela ou componente; (5) `StringsParityTest` do módulo quando mexe em strings;
  - **portão e trava do emulador:** todo comando que usa o aparelho passa por `<scratchpad>/gated-run-lock.sh` (a trava por `mkdir` da 4B, que espera 60 s sem `*.test` rodando e sem "run started" órfão); um "run started" órfão é limpo pelo controlador com `adb logcat -c`, nunca pelo implementador;
  - **uma vez no fim da 5A** (Task 16): as suítes completas de aparelho, a atualização por cima, sem rede, desempenho e tamanho, com as correções aplicadas num bloco;
  - no máximo **dois implementadores** ao mesmo tempo, em worktrees com branch própria (`lane/phase5a-tN`), e no máximo **um usando o emulador**; nenhum Gradle roda enquanto um agente dirige a UI do emulador por `adb`; com duas lanes, cada Gradle usa `--max-workers=2`;
  - **revisão por risco** (cada task diz a sua): *Opus completa* para cálculo, concorrência, navegação e telas com comportamento novo; *Sonnet curta* para texto, formato e testes; *controlador* para spike, verificação e documentos. Uma correção pequena provada por um teste que falha e depois passa leva só a conferência do controlador. Edições mecânicas (mínimos do CI ao mesclar lanes, notas datadas, o `apk-budget.json`) são do controlador;
  - toda task que acrescenta testes sobe o mínimo no `ci.yml` pelo número de testes novos, com a linha por classe no comentário: `MIN_HOST_TESTS` (hoje 1112; conta os `commonTest` de cada módulo e os testes de host), `MIN_IOS_TESTS` (275; `commonTest` do `:shared`), `MIN_DS_IOS_TESTS` (380; `commonTest` e `uiTest` do `:designsystem`), `MIN_SCREENS_IOS_TESTS` (666; `commonTest` e `uiTest` do `:screens`). Um teste aposentado só desconta com o motivo no commit. Duas lanes que sobem a mesma linha: o controlador soma;
  - o iOS só se prova no CI; desligue a rede do emulador (`adb shell svc wifi disable; adb shell svc data disable`) antes de instalar APKs; nunca desinstale nada de um aparelho do dono.
- Código, comentários, KDoc e commits em inglês. Plano e relatórios em pt-BR. Commits terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **Evidências:** `docs/superpowers/reports/AAAA-MM-DD-phase5a-verificacao/` (criada pela primeira task que grava nela, com `mkdir -p`). Só entram no git as capturas citadas nos relatórios, em PNG reduzido (`pngquant --quality 60-80`) quando a crítica não precisar de cor exata; o resto vai para `…/local/` (já no `.gitignore`).

## Review Focus

1. **Exercício com a convenção trocada ou que não existe mais:** um personalizado que passou de Por mão a Carga total, com recordes nas duas; um exercício apagado com histórico; um recorde antigo tocado na linha do tempo. A tela abre na convenção atual (ou na mais recente do histórico), mostra a linha das outras convenções, usa o nome do snapshot e nunca mostra "não encontrado" com histórico. Coberto na Task 4 por `ProgressIndexTest.conventionsAreListedMostRecentFirst` e `deletedExerciseKeepsItsLatestSnapshot`, na Task 10 por `ExerciseProgressSectionsTest.conventionChangedShowsTheOtherConventionLine` e `deletedExerciseUsesTheSnapshotName`, e na Task 13 por `ProgressNavigationHostTest.deletedExerciseWithHistoryOpensWithTheSnapshotName`.
2. **Séries nas bordas das regras:** peso corporal sem carga (0), séries assistidas num exercício de carga, Assistência com 0, só aquecimentos, todas as séries acima de 10 repetições, série incompleta. As métricas aparecem ou somem pela decisão 5, o 1RM mostra a frase do período, a linha de recorde de repetições em peso corporal diz "Peso corporal × 12". Coberto na Task 4 por `bodyweightWithoutLoadHasOnlyReps`, `assistedSetsStayOutOfLoadAndInTheirRepsGroup` e `assistanceIsTheLowestLoggedAboveZero`, na Task 5 por `ExerciseChartsTest.metricWithoutPointsInPeriodSaysWhy`, na Task 9 por `ExerciseProgressScreenTest.noEstimateInPeriodShowsTheSentence` e na Task 11 por `RecordsScreenTest.bodyweightRepsRecordReadsBodyweight`.
3. **O histórico muda com a tela aberta** (finalizar, editar ou excluir um treino noutra tela, importar um backup, trocar kg/lb). Uma identidade nova constrói o índice uma vez, chamadas simultâneas dividem a mesma construção, a tela mostra o índice anterior até o novo chegar (nunca o `Skeleton` de novo) e formata na unidade atual; um evento cujo treino sumiu some da lista. Coberto na Task 7 por `ProgressIndexCacheTest.concurrentGetsShareOneBuild` e `newIdentityBuildsAgainAndPeekReturnsOnlyTheExactOne`, e na Task 10 por `ExerciseProgressSectionsTest.newHistoryKeepsThePreviousChartUntilTheIndexIsReady` e `poundsSwitchFormatsInTheNewUnit`.
4. **Tempo:** treino perto da meia-noite, virada de horário de verão (`America/Sao_Paulo` em 2018, `Europe/Berlin` em 2026), início do período exatamente na meia-noite local, semana começando em domingo, segunda ou sábado na redução, mês do cabeçalho da linha do tempo no fuso local. Coberto na Task 3 por `WeekStartJvmEquivalenceTest`, na Task 5 por `periodStartIsInclusiveAtLocalMidnight`, `dstChangeKeepsWorkoutsOnTheirStartDay`, `nearMidnightWorkoutCountsOnItsStartDay` e `weeklyReductionUsesTheGivenWeekStart`, e na Task 6 por `RecordTimelinesTest.monthsUseTheLocalZone`.
5. **Muitos recordes e busca rápida:** "Só atuais" com centenas de recordes de repetições, um recorde que é atual e também evento (aparece uma vez, com "Atual"), busca em pt-BR achando o nome do catálogo, texto com só espaços. Coberto na Task 6 por `currentOnlyIsUiStateRecordsIncludingFirsts`, `aCurrentEventAppearsOnce`, `searchMatchesBothLanguagesAndAddsCurrentRecordsOfMatches` e `blankSearchIsNoSearch`, na Task 7 pelo `ProgressIndexTimingTest` e na Task 16 pela rolagem rápida da linha do tempo.

## Destino das telas e dos testes atuais

Regra (D19): um teste cujo alvo continua fica; um cujo alvo sai é portado ou aposentado com o motivo, no mesmo commit em que o código sai.

| Peça ou teste atual | Destino |
| --- | --- |
| `RecordsScreen` (`HistoryScreens.kt`) | **sai** na Task 13 (aba Recordes nova, Task 11) |
| `ExerciseHistoryScreen` (`HistoryScreens.kt`) | **sai** na Task 13 (progresso do exercício, Tasks 9–10) |
| `CustomExerciseDialog`, `ChoiceFilter` (`LibraryScreens.kt`) | **saem** na Task 13 se o `grep` confirmar que ficaram sem chamador (decisão 10) |
| `recordAchievementText`, `CompletedSetRow`, `PageHeading`, `SectionTitle`, `dateText`, `human`, `conventionExplanation`, `SearchField`, `ExercisePicker`, `MissingScreen` | ficam (o histórico e o detalhe antigos ainda os usam; saem na 5B) |
| `ShellNavigationHostTest.exerciseHistoryFromTheWorkoutReturnsToIt` e o auxiliar `openExerciseHistory` | ficam, com o mesmo nome; passam a esperar `ExerciseProgressTag` em vez do texto "Movement history" (Task 13) |
| `ShellNavigationHostTest` | ganha `progressRouteWithoutMetricOpensTheDefault`, `progressRouteWithMetricOpensIt` e `unknownMetricFallsBackToTheDefault` (Task 13) |
| `LibraryNavigationHostTest.detailFullHistoryOpensTheOldScreenAndBackReturns` | **renomeado** `detailProgressOpensTheProgressAndBackReturns` e passa a esperar a tela nova (Task 13; a contagem não muda) |
| `DiscardNavigationHostTest`, `UiFlowHostTest`, `androidTest/UiFeatureSmokeTest` (varredura de abas) | ficam sem mudança (tags `tab-*`); o `UiFeatureSmokeTest` roda na Task 16 |
| `HistoryRecordsHostTest`, `InlineRestEditorHostTest`, `WorkoutInputHostTest`, `WorkoutSharePreviewHostTest` | ficam (5B) |
| `ExerciseInfoScreenTest`, `LibraryAccessibilitySweepTest`, `ActiveWorkoutScreenTest` | ficam; ajustados só onde procuram "View full history" ou "Exercise history" (Task 12) |
| `AppViewModelRecordsHostTest` e os do ViewModel | não mudam |

Nenhum teste de host cobre só `RecordsScreen` ou `ExerciseHistoryScreen`, então nenhum é aposentado na 5A.

---

### Task 1: Fundação dos gráficos (spike S1)

**Revisão:** Opus completa (componentes novos, gesto, desenho e desempenho). O veredito do spike é do controlador.

**Files:**
- Create: em `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/charts/`, `ChartData.kt`, `ChartScale.kt`, `ChartGeometry.kt`, `MLineChart.kt`, `MBarChart.kt`, `MSparkline.kt`, `ChartTable.kt`, `ChartProbe.kt`; `designsystem/.../gallery/pages/ChartsPage.kt`; `designsystem/.../icons/lucide/LucideTable.kt`, `LucideChartLine.kt` (gerados); `docs/superpowers/reports/2026-10-31-phase5a-spike-graficos.md`
- Modify: `designsystem/.../tokens/SpaceTokens.kt` (`MoosclesSizes`), `.../icons/MIcons.kt` e `tools/icons/gen.py` (decisão 14), `.../gallery/GalleryPage.kt` (`Charts`), `designsystem/src/commonMain/composeResources/values*/strings_components.xml` e `strings_gallery.xml`, `tools/check-design-tokens.ps1` (`"$base/charts"` na lista padrão), `.github/workflows/ci.yml` (o artefato `ios-flow-captures` passa a incluir `designsystem/build/ios-captures/**`; `MIN_HOST_TESTS`, `MIN_DS_IOS_TESTS`)
- Test: `designsystem/src/commonTest/kotlin/com/mooscles/designsystem/charts/ChartScaleTest.kt`, `ChartGeometryTest.kt`; `.../tokens/SizesTimingElevationTest.kt`; `.../components/ComponentColorsTest.kt`; `.../icons/IconsTest.kt`; `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/charts/ChartsTest.kt` e `ChartsTestScreen.kt`

**Interfaces:**
- Produces (dados, `@Immutable`): `data class LinePoint(val x: Double, val y: Double, val record: Boolean = false)`; `data class BarDatum(val value: Double)`; `data class AxisLabel(val index: Int, val text: String)`; `data class ChartTableRowData(val key: String, val label: String, val value: String, val detail: String? = null, val record: Boolean = false)`; `data class ChartRange(val min: Double, val max: Double, val ticks: List<Double>)`.
- Produces: `object ChartScale { fun line(values: List<Double>): ChartRange` (sem zero obrigatório, folga de 10% da faixa, passo "redondo" em {1, 2, 2,5, 5} × 10ᵏ, até 4 marcas, faixa nunca vazia com um valor só)`; fun bar(maxValue: Double, integerTicks: Boolean = false): ChartRange` (de zero, até 4 marcas, só inteiros com `integerTicks`)`; fun labelIndices(count: Int, max: Int = 5): List<Int>` (espalhados, com o primeiro e o último)`; fun fitLabels(centers: List<Float>, widths: List<Float>, gap: Float): List<Int> }` (os índices que cabem sem se sobrepor, sempre o primeiro).
- Produces: `internal object ChartGeometry { fun nearest(xs: List<Float>, touchX: Float): Int?; fun toPx(value: Double, range: ChartRange, top: Float, bottom: Float): Float }`.
- Produces (composables): `MLineChart(points: List<LinePoint>, summary: String, yLabel: (Double) -> String, xLabels: List<AxisLabel>, selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier, contentKey: Any? = null)`; `MBarChart(bars: List<BarDatum>, summary: String, yLabel: (Double) -> String, xLabels: List<AxisLabel>, selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier, integerTicks: Boolean = false, contentKey: Any? = null)`; `MSparkline(values: List<Double>, modifier: Modifier = Modifier)`; `ChartDataButton(showingTable: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)`; `ChartTableRow(row: ChartTableRowData, modifier: Modifier = Modifier)`; `MChartLegendRecord(modifier: Modifier = Modifier)` (o ícone `PlateRecord` com "Recorde").
- Produces (seam de teste): `internal val LocalChartPathBuilds = staticCompositionLocalOf<((String) -> Unit)?> { null }`, chamado com `"line"` ou `"bar"` cada vez que o `Path` ou as barras são refeitos.
- Comportamento (D3, D4): gráfico e sparkline com `drawWithCache`; o `Path`, as marcas e os rótulos medidos (`TextMeasurer`) só são refeitos quando os dados ou o tamanho mudam; a seleção é desenhada numa camada própria (`drawWithContent`), sem refazer o cache e sem bitmap intermediário. Linha em `chartLine` com traço `chartStroke`, pontos de raio `chartPoint`, recorde como anel `chartPr` de raio `chartRecordRing`; seleção: anel `accentSolid` de raio `chartSelectionRing` e guia vertical `chartGrid`; grade em `chartGrid`. Barras de zero, cantos `radius.xs`, cor `chartLine`, vão `chartBarGap`. Toque ou arraste escolhe o ponto ou a barra mais perto (`onSelect`); a seleção não é anunciada. Altura = `chartHeight × fontScale` limitada a `chartMaxScale`. `contentKey` diferente faz cross-fade de `MTheme.motion.base`; com movimento reduzido, troca na hora. Sem animação de entrada. Nó do gráfico: `clearAndSetSemantics { contentDescription = summary; role = Role.Image }`; sparkline sem semântica. `ChartDataButton`: `MButton` secundário de 48 dp com `Table`/"Ver dados" ou `ChartLine`/"Ver gráfico". `ChartTableRow`: um nó só (`mergeDescendants`), com "Recorde" escrito quando `record`.
- Tokens (`MoosclesSizes`, fixados no teste): `chartHeight = 200.dp`, `chartMaxScale = 1.5f`, `sparklineHeight = 40.dp`, `chartStroke = 2.dp`, `chartPoint = 3.dp`, `chartRecordRing = 6.dp`, `chartSelectionRing = 8.dp`, `chartBarGap = 4.dp`.
- Strings (en / pt-BR): `ds_chart_view_data` "View data"/"Ver dados"; `ds_chart_view_chart` "View chart"/"Ver gráfico"; `ds_chart_record` "Record"/"Recorde"; `gallery_page_charts` "Charts"/"Gráficos".
- Galeria "Gráficos": linha com e sem recorde, com 2 e 300 pontos; barras com uma semana vazia; sparkline; cada um com "Ver dados" e a tabela; nos dois temas, a 1,0, 1,3 e 2,0 (pelos controles que a galeria já tem).

- [ ] **Step 1: Testes que falham.**
  - `ChartScaleTest`: `lineRangePadsTenPercentAndRoundsTicks` (80, 92,5, 95 → todas as marcas dentro da faixa, passo em {1, 2, 2,5, 5} × 10ᵏ, `ticks.size <= 4`, `min < 80` e `max > 95`); `lineRangeOfOneValueStillHasARange` (100 → `max > min`, o 100 dentro); `poundTicksAreRound` (os kg 36,29…45,36 convertidos para lb → toda marca é múltipla inteira do passo); `barRangeStartsAtZero`; `integerTicksForCounts` (máximo 3 → marcas inteiras, a última `>= 3`); `labelIndicesKeepFirstAndLastAndAtMostFive` (count 300 → 5 índices, `0` e `299`; count 2 → `[0, 1]`); `fitLabelsDropsOverlapsAndKeepsTheFirst`.
  - `ChartGeometryTest`: `nearestPicksTheClosestX` (empate fica com o da esquerda); `nearestOfNothingIsNull`; `valuesMapInsideTheDrawingArea`.
  - `SizesTimingElevationTest.chartTokens` (os oito valores acima). `ComponentColorsTest.chartSelectionRingOnSurface` (`accentSolid` sobre `bgSurface` ≥ 3:1 nos dois temas) e, se o par ainda não estiver nos 50, `chartLineOnRaised` (`chartLine` sobre `bgRaised` ≥ 3:1). `IconsTest`: a contagem passa de 54 para 56 com `Table` e `ChartLine`.
  - `ChartsTest` (`withScreen` do design system, sobre o `ChartsTestScreen`: um gráfico, o `ChartDataButton` e as linhas da tabela): `lineChartIsOneImageNodeWithTheSummary`; `barChartIsOneImageNodeWithTheSummary`; `sparklineHasNoSemantics`; `dataButtonSwapsChartAndTableAndBack` (o nó do gráfico some, as linhas aparecem com "Ver gráfico"; voltar devolve o nó); `tableRowIsOneNodeAndSaysRecord`; `tapSelectsTheNearestPointAndDragMovesIt`; `pathIsBuiltOnceWhileTheSelectionMoves` (300 pontos; depois de compor, 20 seleções por toque e arraste: `LocalChartPathBuilds` contou `line` uma vez; controle positivo: trocar os dados conta mais uma); `resizingRebuildsThePathOnce`; `heightGrowsWithFontScaleUpToOneAndAHalf` (1,0 → 200 dp; 1,3 → 260 dp; 2,0 → 300 dp); `contentKeyCrossfadesAndReducedMotionSwapsAtOnce`; `capturesBothThemesAtOneAndTwo` (linha, barras e sparkline em claro e escuro a 1,0 e 2,0; no iOS, `onRoot().captureToImage()` em `designsystem/build/ios-captures/charts-<tema>-<escala>.png`, e se lançar imprime `CHARTS capture unsupported` e segue; no Android só confere que desenha).
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :designsystem:testAndroidHostTest --tests "*ChartScaleTest*" --tests "*ChartGeometryTest*" --tests "*SizesTimingElevationTest*" --tests "*ComponentColorsTest*" --tests "*IconsTest*"` e `ChartsTest` no emulador pela trava; ver falhar.
- [ ] **Step 3:** Gerar os ícones (decisão 14) e implementar os tokens, o `ChartScale`, a geometria, os componentes, a página da galeria e as strings.
- [ ] **Step 4: Verificar.** Os comandos do Step 2 verdes, mais `GallerySmokeTest` no emulador, `:designsystem:compileTestKotlinIosSimulatorArm64`, o `StringsParityTest` do `:designsystem` e `pwsh tools/check-design-tokens.ps1` limpo (agora com `charts/`).
- [ ] **Step 5: Medidas do S1 no Android.** (a) capturas da página "Gráficos" com `adb exec-out screencap -p`, nos dois temas a 1,0 e 2,0, sem texto cortado ou sobreposto; (b) a árvore de semântica do `ChartsTest`; (c) no `assemblePerformance`, a página "Gráficos" com a linha de 300 pontos: `adb shell dumpsys gfxinfo com.mooscles reset`, 10 `adb shell input swipe`, `gfxinfo` janky ≤ 5%, e o controle da decisão 2 na Biblioteca; o contador do `ChartsTest`; (d) `./gradlew :app:assembleDebug` nesta task e no commit de origem da branch, na mesma máquina, com `stat -f%z`: crescimento ≤ 61.440 bytes.
- [ ] **Step 6: Relatório do spike** `reports/2026-10-31-phase5a-spike-graficos.md`, com os quatro critérios do S1, as medidas e o veredito Android; a linha iOS fica "pendente do Push 1" e o controlador a preenche (decisão 2).
- [ ] **Step 7:** Subir `MIN_HOST_TESTS` (os `commonTest` novos) e `MIN_DS_IOS_TESTS` (`commonTest` e `uiTest` novos). Commit `designsystem: own Canvas charts with summary, data table and gallery page (spike S1)`.

### Task 1R (condicional, só com o S1 reprovado): medir o Vico

**Revisão:** controlador.

- [ ] Em `spike/phase5a-vico`, a partir da Task 1: a versão do Vico compatível com Kotlin 2.4.21 e CMP 1.12.2, o mesmo gráfico de linha da página "Gráficos" e os mesmos itens (a)–(d), mais o leitor de tela (a árvore de semântica). Registrar no relatório do S1. O controlador decide entre o Vico e a correção da fundação própria e escreve a nota datada na spec (D3), com o efeito no D18 e nas Tasks 9–12. A branch nunca é mesclada.

### Task 2: 1RM estimado e tendência

**Revisão:** Opus completa (cálculo, D5 e D10).

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mooscles/domain/EstimatedOneRepMax.kt`, `screens/src/commonMain/kotlin/com/mooscles/screens/progress/Trend.kt`
- Modify: `.github/workflows/ci.yml`
- Test: `shared/src/commonTest/kotlin/com/mooscles/domain/EstimatedOneRepMaxTest.kt`, `screens/src/commonTest/kotlin/com/mooscles/screens/progress/TrendTest.kt`

**Interfaces:**
- Produces: `object EstimatedOneRepMax { const val MAX_REPS = 10; fun estimate(weightKg: Double, reps: Int): Double? }` (Epley, `weightKg × (1 + reps / 30.0)`; 1 repetição vale a carga; nulo com `reps` fora de 1..10, carga `<= 0` ou não finita).
- Produces: `enum class Trend { UP, DOWN, FLAT }`; `object Trends { fun of(xs: List<Double>, ys: List<Double>): Trend? }` (decisão 16).

- [ ] **Step 1: Testes que falham.**
  - `EstimatedOneRepMaxTest`: `oneRepIsTheLoad` (100, 1 → 100,0); `epleyVectors` (100 × 5 → 116,666…; 85 × 3 → 93,5; 80 × 10 → 106,666…; tolerância 1e-9); `tenIsTheLastAndElevenHasNone`; `zeroRepsOrZeroLoadHasNone`; `negativeOrNonFiniteLoadHasNone` (−1, `NaN`, `Infinity`).
  - `TrendTest`: `risingIsUp` (80, 85, 90, 95); `fallingIsDown`; `flatIsFlat` (100, 101, 99, 100); `fivePercentIsStillFlat` (variação projetada exatamente 5% da média → `FLAT`); `twoPointsHaveNoTrend`; `onePointHasNoTrend`; `zeroMeanHasNoTrend`; `unevenSpacingUsesTheRealDays`.
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :shared:testAndroidHostTest --tests "*EstimatedOneRepMaxTest*" :screens:testAndroidHostTest --tests "*TrendTest*"` e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** O comando do Step 2 verde, mais `:shared:compileCommonMainKotlinMetadata`, `:shared:compileTestKotlinIosSimulatorArm64` e `:screens:compileTestKotlinIosSimulatorArm64`.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS`, `MIN_IOS_TESTS` e `MIN_SCREENS_IOS_TESTS`. Commit `domain: estimated one-rep max (Epley, up to 10 reps); screens: trend of a series`.

### Task 3: Primeiro dia da semana por região

**Revisão:** Sonnet curta.

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/format/WeekStart.kt`
- Modify: `screens/src/commonMain/kotlin/com/mooscles/screens/format/ScreenDates.kt` (`localDate`), `.github/workflows/ci.yml`
- Test: `screens/src/commonTest/kotlin/com/mooscles/screens/format/WeekStartTest.kt`, `screens/src/androidHostTest/kotlin/com/mooscles/screens/format/WeekStartJvmEquivalenceTest.kt`

**Interfaces:**
- Produces: `object WeekStart { fun of(languageTag: String): DayOfWeek }` (tabela própria em Kotlin puro com o `weekData/firstDay` do CLDR por região; sem região, domingo para `pt` e segunda para o resto); `@Composable fun currentWeekStart(): DayOfWeek` (`Locale.current.toLanguageTag()`); `fun ScreenDates.localDate(epochMillis: Long, zone: TimeZone): LocalDate`.

- [ ] **Step 1: Testes que falham.** `WeekStartTest`: `brazilUsAndCanadaStartOnSunday` (`pt-BR`, `en-US`, `en-CA`); `mostOfEuropeStartsOnMonday` (`de-DE`, `fr-FR`, `pt-PT`); `aSaturdayRegion` (uma região que o CLDR marca `sat`, escolhida da tabela e confirmada pelo teste da JVM); `withoutRegionPortugueseIsSundayOthersMonday` (`pt` → domingo, `en` → segunda); `unknownRegionFallsBackByLanguage` (`pt-ZZ`). `WeekStartJvmEquivalenceTest.everyLocaleWithACountryMatchesWeekFields` (cada `Locale.getAvailableLocales()` com país: `WeekStart.of(locale.toLanguageTag()) == WeekFields.of(locale).firstDayOfWeek`, convertido para o `DayOfWeek` do `kotlinx-datetime`; divergência de versão do CLDR só numa lista de exceções com o motivo, no próprio teste).
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :screens:testAndroidHostTest --tests "*WeekStart*"` e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** O comando do Step 2 verde e `:screens:compileTestKotlinIosSimulatorArm64`.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS` (só o `WeekStartTest`, que é `commonTest`). Commit `screens: first day of the week by region (CLDR), checked against the JVM`.

### Task 4: Índice de progresso: sessões, métricas e marcas de recorde

**Revisão:** Opus completa (cálculo; equivalência com o domínio, D6). **Push 1** no fim.

**Files:**
- Create: em `screens/src/commonMain/kotlin/com/mooscles/screens/progress/`, `WorkingSets.kt`, `ProgressIndex.kt`, `ProgressMetrics.kt`
- Modify: `.github/workflows/ci.yml`; a spec (nota datada das decisões 5 e 6)
- Test: `screens/src/commonTest/kotlin/com/mooscles/screens/progress/ProgressFixtures.kt`, `ProgressIndexTest.kt`

**Interfaces:**
- Consumes: `EstimatedOneRepMax` (Task 2); `BestSets.best` e `BestSets.sameLoad` (`format/BestSets.kt`); `ExerciseSearchIndex`, `SearchEntry` (`:shared`); `LoadConvention.fromLabelOrName`.
- Produces: `internal object WorkingSets` com `valid(set: SetUi)`, `forLoad(set: SetUi, convention: LoadConvention)`, `forReps(set: SetUi, assistanceGroup: Boolean, convention: LoadConvention)`, `volumeKg(set: SetUi, convention: LoadConvention): Double` (decisão 4).
- Produces: `data class SeriesKey(val exerciseId: String, val convention: String)`; `@Immutable data class SetValue(val value: Double, val weightKg: Double, val reps: Int)` (o valor da métrica e a série que o gerou); `@Immutable class SessionPoint(val workoutId: String, val workoutName: String, val startedAt: Long, val load: SetValue?, val estimate: SetValue?, val reps: SetValue?, val volumeKg: Double, val weightRecord: Boolean, val repsRecord: Boolean, val sets: List<SetUi>)` (`sets` = as séries concluídas das entradas desse exercício e convenção no treino, na ordem; duas entradas do mesmo exercício no treino são uma sessão; `load` é a carga, a carga adicional ou a assistência, pela convenção e pela decisão 6).
- Produces: `@Immutable class RecordEvent(val record: RecordUi, val workoutName: String, val order: Int, val current: Boolean)` (`order` = posição da primeira entrada do exercício e convenção no treino; `current` pela decisão 18).
- Produces: `@Immutable class ProgressIndex { fun sessions(key: SeriesKey): List<SessionPoint>` (mais antiga primeiro)`; fun conventionsOf(exerciseId: String): List<String>` (com sessão, a mais recente primeiro)`; fun snapshot(exerciseId: String): ExerciseUi?` (o `ExerciseUi` da sessão mais recente)`; val events: List<RecordEvent>` (todos os `WorkoutUi.records` do histórico, primeiros registros inclusive, na ordem do D11: data decrescente, `order`, carga antes de repetições, carga decrescente)`; val currentRecords: List<RecordEvent>` (de `UiState.records`, a mesma ordem)`; fun searchRecords(query: String): Set<String>` (ids de exercício, pelo `ExerciseSearchIndex` dos exercícios que têm recorde)`; companion object { fun build(history: List<WorkoutUi>, records: List<RecordUi>): ProgressIndex } }`.
- Produces: `enum class ProgressMetric(val route: String) { LOAD("load"), REPS("reps"), E1RM("e1rm"), VOLUME("volume"); companion object { fun parse(value: String?): ProgressMetric? } }`; `object ProgressMetrics { fun available(convention: String, sessions: List<SessionPoint>): List<ProgressMetric>; fun initial(requested: ProgressMetric?, available: List<ProgressMetric>): ProgressMetric; fun value(point: SessionPoint, metric: ProgressMetric): SetValue?; fun marked(point: SessionPoint, metric: ProgressMetric, convention: String): Boolean }` (decisão 5; `marked`: `weightRecord` em `LOAD` fora da Assistência, `repsRecord` em `REPS`, nada em `E1RM` e `VOLUME`).
- Regras (D5, D6): carga = `BestSets.best` sobre `WorkingSets.forLoad` (mais pesada, empate pelas mais repetições, depois a primeira na ordem do treino); 1RM = o maior `EstimatedOneRepMax.estimate` entre as mesmas séries com até 10 repetições, com a série; repetições = o máximo de `reps` em `WorkingSets.forReps` no grupo da convenção (não assistidas fora da Assistência, assistidas nela); volume = soma de `WorkingSets.volumeKg`, aquecimento incluído. `weightRecord` = existe um `RecordUi` de `kind == WEIGHT`, não primeiro registro, com o mesmo `exercise.id`, `exercise.convention` e `workoutId` em `records` ou nos `WorkoutUi.records`; `repsRecord` = o mesmo com `REPS` e `assistance` igual ao grupo da convenção.
- `ProgressFixtures.kt` (só de teste): `fun workout(id: String, startedAt: Long, vararg entries: WorkoutExerciseUi, records: List<RecordUi> = emptyList()): WorkoutUi`, `fun entry(exercise: ExerciseUi, vararg sets: SetUi): WorkoutExerciseUi`, `fun set(weightKg: Double, reps: Int, warmup: Boolean = false, assistance: Boolean = false, completed: Boolean = true): SetUi`, e os exercícios `bench` (Carga total), `dumbbellPress` (Por mão), `pullUp` (Carga adicional), `assistedDip` (Assistência), `customRow` (personalizado).

- [ ] **Step 1: Testes que falham** (`ProgressIndexTest`): `loadIsTheHeaviestWorkingSetTiesByRepsThenFirst`; `estimateIsTheBestEpleyOfSetsUpToTenReps` (com uma série de 12 repetições mais pesada fora); `volumeOfEachWorkoutMatchesItsWorkoutVolume` (soma das sessões de todas as séries de um treino `== WorkoutUi.volumeKg`, 1e-9); `twoEntriesOfOneExerciseInAWorkoutAreOneSession`; `warmupsAndIncompleteSetsFollowTheDomain` (aquecimento entra no volume e não na carga; incompleta em nada); `bodyweightWithoutLoadHasOnlyReps`; `bodyweightWithLoadAddsAddedWeightAndVolume`; `assistedSetsStayOutOfLoadAndInTheirRepsGroup`; `assistanceIsTheLowestLoggedAboveZero` (decisão 6; uma sessão só com 0 não tem ponto); `marksMatchTheRecordsExactly` (D6 nos dois sentidos: toda marca tem um recorde batido do mesmo tipo, exercício, grupo de convenção e sessão em `records` ∪ `WorkoutUi.records`, e todo recorde assim marca a sessão); `firstRecordIsNeverAMark`; `metricsPerConvention` (a tabela da decisão 5, inclusive Carga adicional com e sem carga); `initialMetricIsTheRequestedOneOrTheFirst` (`e1rm` em Carga adicional → `REPS`); `parseKnowsTheFourRoutesAndNothingElse` (`"load"`, `"reps"`, `"e1rm"`, `"volume"`; `"LOAD"`, `""` e `null` → `null`); `unknownConventionLabelFollowsTotalLoad`; `conventionsAreListedMostRecentFirst`; `deletedExerciseKeepsItsLatestSnapshot`; `eventsAreNewestFirstThenWorkoutOrderThenWeightBeforeReps`; `currentMatchesExerciseConventionKindLoadAssistanceAndWorkout`; `searchRecordsFindsTheCatalogNameInBothLanguages` ("supino" e "bench" acham o supino do catálogo).
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :screens:testAndroidHostTest --tests "*ProgressIndexTest*"` e ver falhar.
- [ ] **Step 3:** Implementar. Escrever na spec a nota datada das decisões 5 e 6 (seção "Notas de implementação", criada no fim da spec se não existir).
- [ ] **Step 4:** O comando do Step 2 verde e `:screens:compileTestKotlinIosSimulatorArm64`.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS`. Commit `screens: progress index with per-session metrics and record marks from the record lists`. **Push 1** (checkpoint das Tasks 1–4): `git push origin feat/phase5a-progress` e `gh run watch -R pedroyuuki/Mooscles` sem bloquear. Com o `ios` verde, o controlador olha as capturas `charts-*` do artefato e a árvore de semântica do `ChartsTest` no simulador e preenche a linha iOS do relatório do S1 (decisão 2).

### Task 5: Consulta do gráfico do exercício

**Revisão:** Opus completa (cálculo, tempo e fuso).

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/progress/ExerciseCharts.kt`
- Modify: `.github/workflows/ci.yml`; a spec (nota datada da decisão 7)
- Test: `screens/src/commonTest/kotlin/com/mooscles/screens/progress/ExerciseChartsTest.kt`

**Interfaces:**
- Consumes: Task 4 (`ProgressIndex`, `SeriesKey`, `SessionPoint`, `SetValue`, `ProgressMetric`, `ProgressMetrics`); Task 2 (`Trends`); Task 3 (`ScreenDates.localDate`).
- Produces: `enum class ExercisePeriod(val months: Int?) { THREE_MONTHS(3), SIX_MONTHS(6), ONE_YEAR(12), ALL(null) }` (padrão `ALL`); `data class ExerciseChartQuery(val key: SeriesKey, val metric: ProgressMetric, val period: ExercisePeriod, val today: LocalDate, val zone: TimeZone, val weekStart: DayOfWeek)`; `enum class EmptyReason { NO_SESSIONS, NO_ESTIMATE, NO_POINTS }`; `@Immutable data class ChartPoint(val startedAt: Long, val value: Double, val set: SetValue?, val record: Boolean, val workoutId: String?)` (kg ou repetições); `@Immutable class ExerciseChart(val points: List<ChartPoint>, val weekly: Boolean, val sessionCount: Int, val first: ChartPoint?, val last: ChartPoint?, val best: ChartPoint?, val trend: Trend?, val empty: EmptyReason?)` (`best` = o máximo, ou o mínimo na Assistência; `sessionCount` = sessões com ponto no período); `object ExerciseCharts { const val MAX_POINTS = 104; fun query(index: ProgressIndex, query: ExerciseChartQuery): ExerciseChart; fun sparkline(index: ProgressIndex, key: SeriesKey, last: Int = 12): List<Double> }` (a convenção é a de `key.convention`; a sparkline é a primeira métrica da convenção nas últimas 12 sessões, mais antiga primeiro).
- Regras (D7, D8, decisão 7): início do período = `today.minus(DatePeriod(months = n))` à meia-noite local, inclusive; um treino entra pelo dia local do `startedAt`; até 104 pontos, um por sessão; acima, um por semana; `NO_ESTIMATE` quando a métrica é `E1RM` e o período tem sessões mas nenhuma com série de até 10 repetições; `NO_POINTS` quando o período não tem ponto da métrica; `NO_SESSIONS` sem sessão nenhuma da série.

- [ ] **Step 1: Testes que falham** (`ExerciseChartsTest`): `pointsAreOnePerSessionOldestFirstInsideThePeriod`; `periodsCountCalendarMonthsFromToday` (hoje 2026-10-31: "3 meses" começa em 2026-07-31); `periodStartIsInclusiveAtLocalMidnight` (um treino às 00:00 locais do primeiro dia entra; às 23:59 do dia anterior, não); `allIncludesEverySession`; `nearMidnightWorkoutCountsOnItsStartDay` (início 23:50, fim 00:40); `dstChangeKeepsWorkoutsOnTheirStartDay` (`America/Sao_Paulo` em 2018-11-04 e `Europe/Berlin` em 2026-03-29); `moreThan104PointsReduceToTheWeeklyBest` (`weekly = true`, valor = máximo da semana, data = início da semana, `workoutId = null`); `weeklyAssistanceKeepsTheLowest`; `weeklyReductionUsesTheGivenWeekStart` (os mesmos treinos em domingo, segunda e sábado dão semanas diferentes); `weeklyPointIsMarkedWhenAnySessionOfTheWeekIs`; `firstLastBestAndTrend`; `metricWithoutPointsInPeriodSaysWhy` (`NO_ESTIMATE` com todas as séries acima de 10; `NO_POINTS` num período vazio; `NO_SESSIONS` sem sessão); `sparklineIsTheFirstMetricOfTheLastTwelveSessions`.
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :screens:testAndroidHostTest --tests "*ExerciseChartsTest*"` e ver falhar.
- [ ] **Step 3:** Implementar. Escrever na spec a nota datada da decisão 7.
- [ ] **Step 4:** O comando do Step 2 verde e `:screens:compileTestKotlinIosSimulatorArm64`.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS`. Commit `screens: exercise chart query with periods, weekly reduction and trend`.

### Task 6: Consulta da linha do tempo de recordes

**Revisão:** Opus completa (regra nova do D11).

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/records/RecordTimelines.kt`
- Modify: `.github/workflows/ci.yml`
- Test: `screens/src/commonTest/kotlin/com/mooscles/screens/records/RecordTimelinesTest.kt`

**Interfaces:**
- Consumes: Task 4 (`ProgressIndex.events`, `currentRecords`, `searchRecords`, `RecordEvent`); `ScreenDates.localDate` (Task 3).
- Produces: `data class RecordsQuery(val text: String = "", val currentOnly: Boolean = false, val firstRecords: Boolean = false, val zone: TimeZone)`; `@Immutable class RecordMonth(val year: Int, val month: Month, val items: List<RecordEvent>)`; `@Immutable class RecordsTimeline(val months: List<RecordMonth>, val total: Int)`; `object RecordTimelines { fun query(index: ProgressIndex, query: RecordsQuery): RecordsTimeline; fun key(event: RecordEvent): String }` (chave estável: treino, exercício, convenção, tipo, carga, assistência).
- Regras (D11, "Recordes (aba)"): sem texto, os eventos batidos (`!record.firstRecord`), mais os primeiros registros com `firstRecords`; `currentOnly` lista `currentRecords` (inclusive primeiros registros nunca batidos) e ignora `firstRecords`; com texto (aparado; só espaços = sem texto), os eventos dos exercícios achados mais os recordes atuais deles mesmo quando são primeiros registros; um evento que também é atual aparece uma vez, com `current = true`; agrupado pelo mês local do `record.date`, meses do mais recente.

- [ ] **Step 1: Testes que falham** (`RecordTimelinesTest`): `beatenOnlyByDefaultNewestFirst`; `firstRecordsOnlyWithTheChip`; `currentOnlyIsUiStateRecordsIncludingFirsts` (e ignora `firstRecords`); `searchMatchesBothLanguagesAndAddsCurrentRecordsOfMatches`; `aCurrentEventAppearsOnce`; `blankSearchIsNoSearch`; `monthsUseTheLocalZone` (um recorde às 23:30 UTC de 31 out em `America/Sao_Paulo` fica em outubro, e às 02:30 UTC de 1º nov também); `keysAreUniqueAcrossTheTimeline` (a base de 3 treinos com recordes de repetições em cargas iguais por arredondamento de libra).
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :screens:testAndroidHostTest --tests "*RecordTimelinesTest*"` e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** O comando do Step 2 verde e `:screens:compileTestKotlinIosSimulatorArm64`.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS`. Commit `screens: records timeline query (beaten first, current label, bilingual search)`.

### Task 7: Cache único do índice, equivalência com o domínio e tempo

**Revisão:** Opus completa (concorrência, D15).

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/progress/ProgressIndexCache.kt`; `screens/src/commonTest/kotlin/com/mooscles/screens/progress/LargeProgressHistory.kt`; `app/src/test/java/com/mooscles/ProgressIndexDomainHostTest.kt`
- Modify: `.github/workflows/ci.yml`
- Test: `screens/src/commonTest/kotlin/com/mooscles/screens/progress/ProgressIndexCacheTest.kt`, `screens/src/androidHostTest/kotlin/com/mooscles/screens/progress/ProgressIndexTimingTest.kt`, `ProgressIndexDomainHostTest`

**Interfaces:**
- Consumes: Tasks 4, 5 e 6.
- Produces: `class ProgressIndexCache(private val scope: CoroutineScope, private val context: CoroutineContext = Dispatchers.Default, private val prewarmDelayMillis: Long = 500, private val build: (List<WorkoutUi>, List<RecordUi>) -> ProgressIndex = ProgressIndex::build) { fun peek(history: List<WorkoutUi>, records: List<RecordUi>): ProgressIndex?` (só o índice destas mesmas instâncias, `===`)`; suspend fun get(history: List<WorkoutUi>, records: List<RecordUi>): ProgressIndex` (uma construção por identidade, em `context`; chamadas simultâneas esperam o mesmo `Deferred`)`; fun prewarm(history: List<WorkoutUi>, records: List<RecordUi>, zone: TimeZone)` (decisão 9: espera `prewarmDelayMillis`, `get`, e calcula `RecordTimelines.query(index, RecordsQuery(zone = zone))`; um prewarm novo cancela o pendente)`; fun <K : Any, V : Any> peekQuery(index: ProgressIndex, key: K): V?; suspend fun <K : Any, V : Any> query(index: ProgressIndex, key: K, compute: (ProgressIndex) -> V): V }` (memória por `(índice, chave)`, as 32 mais recentes, protegida por `Mutex`, cálculo em `context`).
- Produces: `val LocalProgressIndexCache = staticCompositionLocalOf<ProgressIndexCache?> { null }`; `@Composable fun rememberProgressIndex(history: List<WorkoutUi>, records: List<RecordUi>): ProgressIndex?` (`peek` no mesmo quadro; senão `get` num efeito, mantendo o índice anterior até o novo chegar; sem provedor, um cache da composição); `@Composable fun <K : Any, V : Any> rememberProgressQuery(index: ProgressIndex?, key: K, compute: (ProgressIndex) -> V): V?` (`peekQuery` no mesmo quadro; senão `query` num efeito; o anterior fica até o novo).
- Produces (teste): `object LargeProgressHistory { fun state(exercises: Int = 48): Pair<List<WorkoutUi>, List<RecordUi>> }` (208 treinos × 61 séries sobre 48 exercícios do catálogo de todas as convenções, a forma do `LargeHistoryFixture`; recordes de carga quando a carga do treino passa a melhor anterior e de repetições quando passa as repetições anteriores na mesma carga, para o tempo; não é a prova de equivalência, que é do `:app`).

- [ ] **Step 1: Testes que falham.**
  - `ProgressIndexCacheTest` (`runTest` com `StandardTestDispatcher` como `context` e um `build` que conta): `sameIdentityBuildsOnce`; `concurrentGetsShareOneBuild`; `newIdentityBuildsAgainAndPeekReturnsOnlyTheExactOne` (uma lista igual mas outra instância não é servida pelo `peek`); `prewarmWaitsFiveHundredMillisAndANewerOneCancelsIt` (499 ms → nada; um segundo prewarm aos 300 ms → uma construção só, da segunda identidade); `prewarmAlsoComputesTheDefaultRecordsQuery` (`peekQuery` com `RecordsQuery(zone = …)` não é nulo depois); `queriesAreMemoizedPerIndexAndKey` (mesma chave → um cálculo; outro índice → outro); `memoKeepsAtMostThirtyTwoEntries`.
  - `ProgressIndexTimingTest` (JVM, folga larga, como o `LibraryModelTimingTest`): `buildAndQueriesAreFastOnTheLargeHistory` (5 construções, mediana < 500 ms; 20 consultas de gráfico em métricas e períodos variados e 20 da linha do tempo com teclas de "supino", mediana < 20 ms; imprime `TIMING progress build=… chart=… records=…`).
  - `ProgressIndexDomainHostTest` (Robolectric, Room e `AppViewModel` reais, como o `AppViewModelPresentationThreadHostTest`; semeia `LargeHistoryFixture.rows(LargeHistoryFixture.variedExercises(48))` pelo DAO numa `writeTransaction`, mais um treino à mão com séries assistidas, peso corporal sem carga, Assistência e aquecimentos): `indexAgreesWithTheDomainOnTheLargeHistory` (do `UiState` publicado: o volume das sessões de cada treino soma o `WorkoutUi.volumeKg`; as marcas casam com os recordes nos dois sentidos, como em `marksMatchTheRecordsExactly`; a carga de cada sessão é a série que `SummaryStats.bestSet` escolhe quando ela tem carga `> 0`); `timelineCurrentMatchesTheRecordsTab` (o número de `currentRecords` é o de `UiState.records`).
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :screens:testAndroidHostTest --tests "*ProgressIndexCacheTest*" --tests "*ProgressIndexTimingTest*" :app:testDebugUnitTest --tests "com.mooscles.ProgressIndexDomainHostTest"` e ver falhar.
- [ ] **Step 3:** Implementar o cache e os dois `remember…`. Um defeito de regra que o teste do `:app` revelar é corrigido na Task 4, com um teste focado lá.
- [ ] **Step 4:** O comando do Step 2 verde e `:screens:compileTestKotlinIosSimulatorArm64`; anotar os `TIMING` no commit.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS` (o `ProgressIndexCacheTest` é `commonTest`). Commit `screens: one progress index cache built off the main thread, checked against the domain`.

### Task 8: Textos do progresso e dos recordes

**Revisão:** Sonnet curta.

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/progress/ProgressTexts.kt`, `ProgressNumbers.kt`; `screens/src/commonMain/composeResources/values/strings_progress.xml`, `strings_records.xml` e os mesmos em `values-pt-rBR/`
- Modify: `strings_dates.xml` (en e pt-BR), `screens/src/commonMain/kotlin/com/mooscles/screens/format/ScreenDates.kt` (`monthYear`, `spokenDate`, `weekOf`), `.github/workflows/ci.yml`
- Test: `screens/src/commonTest/kotlin/com/mooscles/screens/progress/ProgressNumbersTest.kt`, `screens/src/uiTest/kotlin/com/mooscles/screens/progress/ProgressTextsTest.kt`

**Interfaces:**
- Consumes: Task 5 (`ExerciseChart`, `ChartPoint`, `ExercisePeriod`, `EmptyReason`), Task 2 (`Trend`), Task 4 (`ProgressMetric`); `bestSetText`, `loadText`, `loadSpoken`, `unitSymbol`, `conventionLabel`, `shortDate` (fase 3 e 4A).
- Produces (puro): `object ProgressNumbers { fun roundEstimate(kg: Double, pounds: Boolean): Double` (0,5 kg ou 1 lb, na unidade de exibição)`; fun displayValue(kgOrReps: Double, metric: ProgressMetric, pounds: Boolean): Double; fun isShort(value: Double): Boolean` (`>= 10_000`)`}`.
- Produces (`@Composable`): `metricLabel(metric: ProgressMetric, convention: String): String`; `periodLabel(period: ExercisePeriod): String`; `valueText(point: ChartPoint, metric: ProgressMetric, pounds: Boolean): String` ("92,5 kg", "12 repetições"); `axisText(value: Double, metric: ProgressMetric): String` (forma curta a partir de 10.000: "42 mil"/"42k"); `sinceText(first: ChartPoint, last: ChartPoint, metric: ProgressMetric, pounds: Boolean, zone: TimeZone): String` ("+12,5 kg desde 3 jun", "−5 kg desde 3 jun" com U+2212, "0 kg desde…"); `readoutText(point: ChartPoint, metric: ProgressMetric, pounds: Boolean, zone: TimeZone): String` ("28 out · 92,5 kg · 85 kg × 3"); `chartSummary(chart: ExerciseChart, metric: ProgressMetric, convention: String, period: ExercisePeriod, pounds: Boolean, zone: TimeZone): String` (D4: o que é, o período, primeiro e último com as datas por extenso, o melhor com a data, a tendência quando houver, a contagem; unidades por extenso); `tableRows(chart: ExerciseChart, metric: ProgressMetric, pounds: Boolean, zone: TimeZone, weekStart: DayOfWeek): List<ChartTableRowData>` (mais recente primeiro; "Semana de 6 out" no modo semanal, e a semana corrente como "esta semana (em andamento)", D7); `ScreenDates.monthYear(year: Int, month: Month): String`; `spokenDate(epochMillis: Long, zone: TimeZone, today: LocalDate): String` (com o ano só fora do ano corrente).
- Strings (en / pt-BR), `strings_progress.xml`: `progress_title` "Exercise progress"/"Progresso do exercício"; `progress_more` "Exercise options"/"Opções do exercício"; `progress_about` "About the exercise"/"Sobre o exercício"; `progress_metric_load` "Load"/"Carga"; `progress_metric_added` "Added weight"/"Carga adicional"; `progress_metric_assistance` "Assistance"/"Assistência"; `progress_metric_e1rm` "Estimated 1RM"/"1RM estimado"; `progress_metric_volume` "Volume"/"Volume"; `progress_metric_reps` "Reps"/"Repetições"; `progress_period_3m` "3 months"/"3 meses"; `progress_period_6m` "6 months"/"6 meses"; `progress_period_1y` "1 year"/"1 ano"; `progress_period_all` "All"/"Tudo"; `progress_period_spoken_3m` "last 3 months"/"últimos 3 meses" (e `_6m`, `_1y` "last year"/"último ano", `_all` "all time"/"todo o período"); `progress_highlight` "%1$s · %2$s"; `progress_since` "%1$s since %2$s"/"%1$s desde %2$s"; `progress_readout` "%1$s · %2$s · %3$s"; `progress_summary` "%1$s, %2$s: from %3$s on %4$s to %5$s on %6$s; highest %7$s on %8$s"/"%1$s, %2$s: de %3$s em %4$s a %5$s em %6$s; máximo %7$s em %8$s" (e `progress_summary_lowest` com "lowest"/"mínimo" para a Assistência); `progress_trend_up` "trending up"/"tendência em alta"; `progress_trend_down` "trending down"/"tendência em baixa"; `progress_trend_flat` "steady"/"tendência estável"; plural `progress_workouts` "%1$d workout(s)"/"%1$d treino(s)"; `progress_week_of` "Week of %1$s"/"Semana de %1$s"; `progress_weekly_note` "One point per week: the week's best."/"Um ponto por semana: o melhor da semana."; `progress_this_week` "this week (in progress)"/"esta semana (em andamento)"; `progress_one_session` "The chart appears after 2 workouts."/"O gráfico aparece a partir de 2 treinos."; `progress_no_estimate` "No sets of 10 reps or fewer to estimate a 1RM in this period."/"Sem séries de até 10 repetições para estimar o 1RM neste período."; `progress_no_points` "No workouts with this measure in this period."/"Nenhum treino com essa medida neste período."; `progress_assistance_legend` "Less assistance, less help from the machine."/"Menos assistência, menos ajuda da máquina."; `progress_other_convention` "Workouts logged as %1$s are not in this chart."/"Os treinos registrados como %1$s não entram neste gráfico."; `progress_records` "Records for this exercise"/"Recordes deste exercício"; plural `progress_view_all_records` "View all %1$d"/"Ver todos os %1$d"; `progress_sessions` "Sessions"/"Sessões"; `progress_empty_title` "You haven't done this exercise yet."/"Você ainda não fez este exercício."; `progress_empty_action` "View in the library"/"Ver na biblioteca"; `progress_missing` "Exercise not found"/"Exercício não encontrado"; `progress_short_thousands` "%1$sk"/"%1$s mil".
- Strings, `strings_records.xml`: `records_title` "Records"/"Recordes"; `records_settings` "Settings"/"Configurações"; `records_rules` "How we count records"/"Como contamos recordes"; `records_rule_weight` "Load: your heaviest working set."/"Carga: a série de trabalho mais pesada."; `records_rule_reps` "Reps: more reps at the same load, bodyweight included."/"Repetições: mais repetições na mesma carga, inclusive peso corporal."; `records_rule_excluded` "Warm-ups and incomplete sets don't count."/"Aquecimento e séries incompletas não contam."; `records_rule_assistance` "Assisted sets have records of their own."/"Séries assistidas têm recordes à parte."; `records_search` "Search exercise"/"Buscar exercício"; `records_current_only` "Current only"/"Só atuais"; `records_first` "First records"/"Primeiros registros"; `records_month` "%1$s · %2$s"; plural `records_count` "%1$d record(s)"/"%1$d recorde(s)"; `records_kind_weight` "Load"/"Carga"; `records_kind_reps` "Reps"/"Repetições"; `records_was_load` "%1$s · was %2$s"/"%1$s · antes %2$s"; plural `records_was_reps` "%1$s · was %2$d rep(s)"/"%1$s · antes %2$d repetição(ões)"; `records_current` "Current"/"Atual"; `records_first_badge` "First record"/"Primeiro registro"; `records_open_workout` "Open workout"/"Abrir treino"; `records_empty_title` "Your first record shows up here"/"Seu primeiro recorde aparece aqui"; `records_empty_body` "Complete working sets to set load and rep records."/"Conclua séries de trabalho para registrar recordes de carga e de repetições."; `records_none_found` "No records found"/"Nenhum recorde encontrado".
- Strings, `strings_dates.xml`: `date_month_full_jan` … `date_month_full_dec` ("January"…/"janeiro"…); `date_month_year` "%1$s %2$s"/"%1$s de %2$s" ("October 2026"/"outubro de 2026"); `date_spoken` "%2$s %1$s"/"%1$s de %2$s" ("June 3"/"3 de junho"); `date_spoken_year` "%2$s %1$s, %3$s"/"%1$s de %2$s de %3$s".

- [ ] **Step 1: Testes que falham.**
  - `ProgressNumbersTest`: `estimateRoundsToHalfAKilo` (93,6 → 93,5; 93,8 → 94); `estimateRoundsToOnePound`; `displayValueConvertsOnlyLoads` (repetições não convertem); `shortFormFromTenThousand` (9.999 não; 10.000 sim).
  - `ProgressTextsTest` (pt-BR e en): `summarySaysPeriodFirstLastBestTrendAndCount` (as duas frases do D4 para o 1RM, com "quilos"/"kilograms"); `noTrendBelowThreePoints`; `assistanceSummarySaysLowest`; `axisUsesTheShortFormFromTenThousand` ("42 mil"/"42k"); `sinceIsAFactWithItsSign` ("+12,5 kg desde 3 jun", "+12.5 kg since Jun 3", "−5 kg desde 3 jun"); `readoutJoinsDateValueAndSet`; `tableRowsNewestFirstWithSetAndRecord` (e "Semana de 6 out"/"Week of Oct 6" no modo semanal, com a semana corrente como "esta semana (em andamento)"); `metricLabelFollowsTheConvention` (`LOAD` em Carga adicional → "Carga adicional"); `monthYearAndSpokenDates` ("outubro de 2026"/"October 2026", "3 de junho"/"June 3", com o ano fora do ano corrente).
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :screens:testAndroidHostTest --tests "*ProgressNumbersTest*"` e `ProgressTextsTest` no emulador pela trava; ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Os comandos do Step 2 verdes, mais `StringsParityTest` e `DateTextsTest` (as datas mudaram) e `:screens:compileTestKotlinIosSimulatorArm64`.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS`. Commit `screens: progress and records texts in English and Portuguese`.

### Task 9: Progresso do exercício: topo, métricas, destaque, período e gráfico

**Revisão:** Opus completa (tela nova com comportamento). Só começa com o veredito iOS do S1 aprovado (decisão 2).

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/progress/ExerciseProgressScreen.kt`
- Modify: `.github/workflows/ci.yml`
- Test: `screens/src/uiTest/kotlin/com/mooscles/screens/progress/ExerciseProgressScreenTest.kt`

**Interfaces:**
- Consumes: Task 1 (`MLineChart`, `ChartDataButton`, `ChartTableRow`, `MChartLegendRecord`, `ChartScale`), Tasks 4–8; `TopBar`, `MChip`, `Skeleton`, `ScreenErrorToast`, `exerciseName`/`displayName`, `muscleLabel`, `equipmentLabel`, `conventionLabel`, `conventionCaption`, `currentWeekStart`.
- Produces: `const val ExerciseProgressTag = "exercise-progress"`; `@Composable fun ExerciseProgressRoute(exerciseId: String, metric: String?, state: UiState, actions: MoosclesActions, onBack: () -> Unit, onAbout: () -> Unit, onOpenWorkout: (workoutId: String) -> Unit, modifier: Modifier = Modifier)` e a forma interna com `ProgressSeams(nowMillis: () -> Long, zone: () -> TimeZone, weekStart: @Composable () -> DayOfWeek)`; `internal fun LazyListScope.progressSections(…)` vazio nesta task, preenchido na Task 10.
- Comportamento ("Progresso do exercício" da spec, decisões 5, 8, 15, 17, 19): `LazyColumn` com a tag; barra com voltar e o menu (`progress-menu`, um item "Sobre o exercício" → `onAbout`); primeira linha do conteúdo: o nome no idioma da tela (`title.md`, inteiro a 2,0) e "Peito · Barra"; a convenção atual (`conventionLabel`, com a legenda de `conventionCaption` quando houver, e a da Assistência); chips de métrica (`progress-metric-<route>`) com a inicial da rota ou a primeira; destaque com o último valor em `num.lg` e o `sinceText` (sem cor de perigo); chips de período (`progress-period-<NAME>`, padrão `ALL`); o gráfico (`progress-chart`) com valores na unidade de exibição, rótulos de eixo por `axisText`, até 5 datas (`ChartScale.labelIndices` + `shortDate`), a linha de leitura acima (`progress-readout`) só com seleção, a legenda de recorde só com marca e `ChartDataButton` (`progress-data-toggle`), que troca o gráfico pelas linhas da tabela como itens da lista (`progress-table-row-<i>`), com `progress_weekly_note` no modo semanal; trocar métrica ou período limpa a seleção e faz o cross-fade (`contentKey`); 1RM arredondado por `ProgressNumbers.roundEstimate`. O índice e o gráfico chegam por `rememberProgressIndex` e `rememberProgressQuery(index, ExerciseChartQuery(…))`, com hoje e o fuso lidos ao entrar e a cada troca de período (D8); enquanto não chegam, `Skeleton` com a altura do gráfico. Uma sessão: o destaque e `progress_one_session` no lugar do gráfico. `EmptyReason.NO_ESTIMATE`/`NO_POINTS`: a frase no lugar do gráfico. Estado em `rememberSaveable` por exercício: métrica, período, "Ver dados"; a seleção não é salva.

- [ ] **Step 1: Testes que falham** (`ExerciseProgressScreenTest`, `withScreen` com `RecordingActions` e um `ProgressIndexCache` de teste por `LocalProgressIndexCache`): `nameIsTheFirstContentLineWithMuscleAndEquipment` (nome do catálogo em pt-BR); `metricsFollowTheConvention` (Carga total, Carga adicional com e sem carga, Assistência com a legenda); `metricArgumentOpensThatMetricAndAMissingOneTheFirst` (`e1rm` → 1RM selecionado; `e1rm` em Carga adicional → Repetições; `null` → a primeira); `highlightShowsTheLastValueAndTheDifferenceAsAFact`; `allIsTheDefaultPeriodAndChipsChangeThePointsAndClearTheSelection`; `chartIsOneImageNodeWithTheSpokenSummary` (o texto de `chartSummary`, com a tendência e a contagem); `viewDataSwapsToTheTableNewestFirstAndBack`; `selectingAPointShowsTheReadout`; `recordLegendOnlyWithAMark`; `estimateIsRoundedToHalfAKiloOrOnePound`; `poundsConvertValuesAndTicksAreRound`; `loadingShowsTheChartSkeleton` (o `build` do cache de teste segura até o teste soltar); `oneSessionShowsTheSentenceInsteadOfTheChart`; `noEstimateInPeriodShowsTheSentence`; `emptyPeriodShowsNoPoints`; `stateSurvivesRecreation` (métrica, período e "Ver dados" pelo `ScreenRestoration`); `textsWholeAtTwoXAndTheChartStopsAtOneAndAHalf` (320 dp, `assertNumbersWhole`, a altura do nó do gráfico ≤ 300 dp).
- [ ] **Step 2:** Rodar a classe no emulador pela trava e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** O comando do Step 2 verde; `StringsParityTest`; `:screens:compileTestKotlinIosSimulatorArm64`; `check-design-tokens` limpo.
- [ ] **Step 5:** Subir `MIN_SCREENS_IOS_TESTS`. Commit `screens: exercise progress screen with metrics, periods, chart and data table`.

### Task 10: Progresso do exercício: recordes, sessões e estados

**Revisão:** Opus completa (tela nova com comportamento).

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/progress/ExerciseProgressSections.kt`
- Modify: `.../progress/ExerciseProgressScreen.kt`, `.github/workflows/ci.yml`; a spec (nota datada da decisão 8)
- Test: `screens/src/uiTest/kotlin/com/mooscles/screens/progress/ExerciseProgressSectionsTest.kt`

**Interfaces:**
- Consumes: Task 9; `PrBadge`, `EmptyState`, `bestSetText`, `RecompositionProbe`, `screenReaderOrder`.
- Produces: `progressSections(…)` preenchido; tags `progress-records`, `progress-records-all`, `progress-session-<workoutId>`, `progress-convention-note`, `progress-empty`, `progress-missing`.
- Comportamento: "Recordes deste exercício" (de `UiState.records`, exercício e convenção atual): o de carga atual ("100 kg × 3 · 12 set") e os de repetições por carga, até 5 (as cargas mais altas) e "Ver todos os N" (expande no lugar, `rememberSaveable`); "Sessões", da mais recente: um nó por sessão com data, nome do treino e as séries concluídas em linha ("A 40 × 10 · 80 × 8 · …", o rótulo curto de aquecimento que o `SetRow` usa e o `PrBadge` pequeno na série com `SetUi.record`), tocar → `onOpenWorkout`; a linha de `progress_other_convention` sob o gráfico quando `conventionsOf` tem outras (decisão 8); convenção = a atual do exercício, senão a mais recente do histórico; nome = o atual, senão o `snapshot`; sem sessão: `EmptyState` `progress_empty_title` com "Ver na biblioteca" → `onAbout`; exercício que não existe e sem histórico: `EmptyState` `progress_missing`. Ordem de leitura: nome, convenção, métrica, destaque, período, gráfico, "Ver dados", recordes, sessões.

- [ ] **Step 1: Testes que falham** (`ExerciseProgressSectionsTest`): `currentWeightRecordAndUpToFiveRepsRecordsWithViewAll`; `sessionsNewestFirstWithWarmupLetterAndRecordBadge`; `sessionIsOneNodeAndOpensTheWorkout`; `conventionChangedShowsTheOtherConventionLine`; `deletedExerciseUsesTheSnapshotName`; `noSessionShowsTheEmptyStateWithViewInLibrary`; `missingExerciseWithoutHistoryShowsNotFound`; `aboutMenuCallsOnAbout`; `movingTheSelectionDoesNotRecomposeTheSessions` (`RecompositionProbe` nas linhas; controle positivo: trocar o período recompõe); `newHistoryKeepsThePreviousChartUntilTheIndexIsReady` (o `build` segurado; o gráfico anterior continua, sem `Skeleton`); `poundsSwitchFormatsInTheNewUnit`; `readingOrderFollowsTheSpec` (`screenReaderOrder`).
- [ ] **Step 2:** Rodar a classe no emulador pela trava, mais `ExerciseProgressScreenTest`; ver falhar.
- [ ] **Step 3:** Implementar. Escrever na spec a nota datada da decisão 8.
- [ ] **Step 4:** O comando do Step 2 verde; `StringsParityTest`; `:screens:compileTestKotlinIosSimulatorArm64`; `check-design-tokens` limpo. Estabilidade (D15): com os relatórios do compilador Compose ligados só localmente (`composeCompiler { reportsDestination = … }` no `screens/build.gradle.kts` e no `designsystem/build.gradle.kts`, sem commit), `SessionPoint`, `SetValue`, `ChartPoint`, `ExerciseChart`, `RecordEvent`, `RecordsTimeline`, `RecordMonth` e os dados de `charts/` saem estáveis, e os composables das linhas de sessão e de recorde, `skippable`; anotar no commit.
- [ ] **Step 5:** Subir `MIN_SCREENS_IOS_TESTS`. Commit `screens: exercise progress records, sessions and empty states`.

### Task 11: Aba Recordes

**Revisão:** Opus completa (tela nova com comportamento).

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/records/RecordsScreen.kt`, `RecordRow.kt`, `RecordRulesSheet.kt`
- Modify: `.github/workflows/ci.yml`
- Test: `screens/src/uiTest/kotlin/com/mooscles/screens/records/RecordsScreenTest.kt`

**Interfaces:**
- Consumes: Tasks 6, 7 e 8; `MSearchField`, `MChip`, `PrBadge`, `MBottomSheet`, `EmptyState`, `Skeleton`, `TopBar`, `ScreenErrorToast`, `bestSetText`, `displayName`/`catalogInPortuguese`.
- Produces: `const val RecordsScreenTag = "records-list"`; `@Composable fun RecordsRoute(state: UiState, actions: MoosclesActions, onOpenProgress: (exerciseId: String, metric: ProgressMetric) -> Unit, onOpenWorkout: (workoutId: String) -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier)` e a forma interna com o fuso como seam.
- Comportamento ("Recordes (aba)" e D11): topo "Recordes" com Configurações (`records-settings`) e "Como contamos recordes" (`records-rules`, ícone `Info`), que abre a `MBottomSheet` com as quatro regras; `MSearchField` fixo (`records-search`); chips `records-current-only` e `records-first` (`Filter`); a lista por `rememberProgressQuery(index, RecordsQuery(…))` com cabeçalho fixo por mês (`stickyHeader`, `records-month-<aaaa-mm>`, "outubro de 2026 · 5 recordes"); cada linha um nó (`record-<key>`) com o `PrBadge` (`soft` no primeiro registro), o nome no idioma da tela, o tipo, "87,5 kg × 6 · antes 85 kg" ou "80 kg × 8 · antes 6 repetições" (peso corporal por `bestSetText`), a data curta e "Atual" escrito quando `current`; tocar → `onOpenProgress(id, LOAD|REPS)` pelo tipo; ação de acessibilidade `records_open_workout` → `onOpenWorkout`. Estados: `Skeleton` sem índice ou sem resultado; sem recorde nenhum: `EmptyState` `records_empty_*`; busca sem resultado: `records_none_found`. Estado em `rememberSaveable`: texto, chips, rolagem.

- [ ] **Step 1: Testes que falham** (`RecordsScreenTest`): `timelineGroupsByMonthNewestFirstWithBeatenRecordsOnly`; `rowIsOneNodeWithBadgeNameKindValuePreviousDateAndCurrent`; `repsRecordReadsWasNReps`; `bodyweightRepsRecordReadsBodyweight`; `firstRecordsChipAddsThemWithTheSoftBadge`; `currentOnlyListsEveryCurrentRecordAndIgnoresTheOtherChip`; `searchInPortugueseFindsTheCatalogNameWithItsCurrentRecordOnTop`; `noMatchShowsNoRecordsFound`; `emptyHistoryShowsTheFirstRecordEmptyState`; `loadingShowsSkeleton`; `tapOpensProgressWithTheRecordsMetric` (carga → `LOAD`, repetições → `REPS`); `openWorkoutAccessibilityAction`; `rulesSheetListsTheFourRulesAtTwoX` (pelo `MOverlayWindow`, conferindo a janela a 2×); `settingsShortcutCallsBack`; `stateSurvivesRecreation` (texto, chips e rolagem pelo `ScreenRestoration`); `textsWholeAtTwoX` (320 dp, `assertNumbersWhole`, "Atual" e o tipo inteiros).
- [ ] **Step 2:** Rodar a classe no emulador pela trava e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** O comando do Step 2 verde; `StringsParityTest`; `:screens:compileTestKotlinIosSimulatorArm64`; `check-design-tokens` limpo.
- [ ] **Step 5:** Subir `MIN_SCREENS_IOS_TESTS`. Commit `screens: records tab as a timeline with current labels, search and rules`.

### Task 12: "Seu histórico" com sparkline e "Ver progresso"

**Revisão:** Sonnet curta.

**Files:**
- Modify: `screens/src/commonMain/kotlin/com/mooscles/screens/exercise/ExerciseInfoScreen.kt` (`InfoHistory`, o menu), `.../workout/ExerciseMenu.kt` (ícone `ChartLine`), `strings_exercise.xml` e `strings_workout.xml` (en e pt-BR), `.github/workflows/ci.yml`
- Test: `screens/src/uiTest/kotlin/com/mooscles/screens/exercise/ExerciseInfoScreenTest.kt`, `.../workout/ActiveWorkoutScreenTest.kt` e `.../a11y/LibraryAccessibilitySweepTest.kt` (só onde procuram os textos antigos)

**Interfaces:**
- Consumes: `MSparkline` (Task 1), `ExerciseCharts.sparkline` (Task 5), `rememberProgressIndex` e `rememberProgressQuery` (Task 7).
- Comportamento: com 2 sessões ou mais da convenção atual, o card "Seu histórico" mostra a `MSparkline` (decorativa, 40 dp) ao lado das linhas que já existem; o botão do card e o item do menu passam a "Ver progresso" (mesmas tags, `onOpenHistory` com o mesmo nome, decisão 13); sem índice pronto, o card aparece sem a sparkline (nunca um `Skeleton` no card).
- Strings (en / pt-BR): `exercise_history_full` "View progress"/"Ver progresso"; `workout_menu_history` "Exercise progress"/"Progresso do exercício".

- [ ] **Step 1: Testes que falham** (`ExerciseInfoScreenTest`): `historyCardShowsASparklineFromTwoSessions` (a sparkline existe, sem nó de semântica, e a leitura do card não muda); `noSparklineWithOneSession`; `viewProgressFromTheCardAndTheMenu` (os dois chamam `onOpenHistory(id)` com o texto novo). Ajustar `ActiveWorkoutScreenTest` e `LibraryAccessibilitySweepTest` onde procuram "Exercise history" ou "View full history".
- [ ] **Step 2:** Rodar `ExerciseInfoScreenTest` no emulador pela trava e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** `ExerciseInfoScreenTest`, `ActiveWorkoutScreenTest` e `LibraryAccessibilitySweepTest` verdes no emulador; `StringsParityTest`; `:screens:compileTestKotlinIosSimulatorArm64`; `check-design-tokens` limpo.
- [ ] **Step 5:** Subir `MIN_SCREENS_IOS_TESTS`. Commit `screens: history card sparkline and "View progress"`.

### Task 13: O `:app` hospeda Recordes e o progresso; as telas antigas saem

**Revisão:** Opus completa (navegação, cache no processo, telas apagadas).

**Files:**
- Create: `app/src/test/java/com/mooscles/ProgressNavigationHostTest.kt`; `docs/superpowers/reports/AAAA-MM-DD-phase5a-verificacao/tap-baseline-5a.md`; temporário `app/src/test/java/com/mooscles/RecordsTapBaselineHostTest.kt` (apagado no mesmo commit)
- Modify: `app/src/main/java/com/mooscles/MoosclesApplication.kt` (`val progressIndex: ProgressIndexCache by lazy { ProgressIndexCache(appScope) }`), `app/src/main/java/com/mooscles/MainActivity.kt` (`LocalProgressIndexCache provides app.progressIndex`), `app/src/main/java/com/mooscles/ui/MoosclesApp.kt` (`PROGRESS_ROUTE = "exercise/{id}?metric={metric}"` com `metric` anulável; `ShellTabs.RECORDS` → `RecordsRoute`; `PROGRESS_ROUTE` → `ExerciseProgressRoute`; ambos em `OWN_PLACEHOLDER_ROUTES`; `LaunchedEffect(state.history, state.records)` → `prewarm` fora do carregamento; `onAbout` volta para o detalhe de baixo quando ele é `exerciseInfo/{mesmo id}`, senão `openExerciseInfo`; as sessões e "Abrir treino" abrem `detailRoute(id, summary = false)`), `app/src/test/java/com/mooscles/ShellNavigationHostTest.kt`, `LibraryNavigationHostTest.kt`, `.github/workflows/ci.yml`; a spec (nota datada da decisão 10)
- Delete: `RecordsScreen` e `ExerciseHistoryScreen` de `app/src/main/java/com/mooscles/ui/HistoryScreens.kt`; `CustomExerciseDialog` e `ChoiceFilter` de `LibraryScreens.kt` (decisão 10, depois do `grep`); os imports que ficarem sem uso

**Interfaces:**
- Consumes: `RecordsRoute` (Task 11), `ExerciseProgressRoute` e `ExerciseProgressTag` (Tasks 9–10), `ProgressIndexCache`, `LocalProgressIndexCache` (Task 7), `ProgressMetric.route`.
- Produces (para a Task 15): a contagem de toques de hoje em `tap-baseline-5a.md`.

- [ ] **Step 1: Contagem de hoje** (antes de trocar as telas; pode rodar em paralelo com a Task 9). `RecordsTapBaselineHostTest` (Robolectric, sobre as telas antigas, contando `performClick`, sem contar texto digitado): "ver o recorde atual de um exercício" (aba, busca); "do recorde ao histórico do exercício" (aba, card). Rodar com `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :app:testDebugUnitTest --tests "com.mooscles.RecordsTapBaselineHostTest"` e registrar os números com o commit de origem.
- [ ] **Step 2: Testes que falham.** `ProgressNavigationHostTest`: `recordsTabShowsTheTimeline`; `recordRowOpensProgressWithItsMetric`; `recordsToProgressAndBackKeepsSearchChipsAndScroll`; `openWorkoutActionOpensTheDetail`; `sessionOpensTheWorkoutDetail`; `aboutOpensTheDetailWhenRecordsOpenedTheProgress`; `deletedExerciseWithHistoryOpensWithTheSnapshotName`; `missingExerciseWithoutHistoryShowsNotFound`; `globalSnackbarNeverShowsOnRecordsOrProgress`; `settingsShortcutOpensSettings`; `progressIndexIsTheApplicationsAndIsPrewarmed` (o provedor entrega `app.progressIndex`; depois de uma mudança no histórico e 500 ms, `peek` não é nulo). `ShellNavigationHostTest`: `progressRouteWithoutMetricOpensTheDefault`, `progressRouteWithMetricOpensIt`, `unknownMetricFallsBackToTheDefault`; `exerciseHistoryFromTheWorkoutReturnsToIt` e `openExerciseHistory` esperando `ExerciseProgressTag`. `LibraryNavigationHostTest.detailProgressOpensTheProgressAndBackReturns` (renomeado) e `progressAboutReturnsToTheDetailBelow`.
- [ ] **Step 3:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :app:testDebugUnitTest --tests "com.mooscles.ProgressNavigationHostTest" --tests "com.mooscles.ShellNavigationHostTest" --tests "com.mooscles.LibraryNavigationHostTest"` e ver falhar.
- [ ] **Step 4:** Implementar. Apagar as peças antigas e o `RecordsTapBaselineHostTest` no mesmo commit (motivo: as telas saíram; os números ficam no relatório). Escrever na spec a nota datada da decisão 10.
- [ ] **Step 5: Verificar.** O comando do Step 3 verde, mais `:app:testDebugUnitTest` inteiro (a rede que pega as telas apagadas), `:app:lintDebug`, `:app:assembleDebug`, `:app:compileDebugAndroidTestKotlin` e `pwsh tools/check-apk-budget.ps1 -Apk app/build/outputs/apk/debug/app-debug.apk` (anotar o crescimento); o `git diff --stat` dos caminhos de compatibilidade (Global Constraints) vazio.
- [ ] **Step 6:** Ajustar `MIN_HOST_TESTS` (novos; a contagem do renomeado não muda). Commit `app: host the records timeline and exercise progress; retire the old records and exercise history screens`.

### Task 14: Fluxo de ponta a ponta do progresso

**Revisão:** Sonnet curta. **Lote:** um agente só para as Tasks 14 e 15 (testes, mesma forma).

**Files:**
- Create: `screens/src/uiTest/kotlin/com/mooscles/screens/flow/ProgressFlowApp.kt`, `ProgressFlowTest.kt`
- Modify: `.github/workflows/ci.yml` (`MIN_SCREENS_IOS_TESTS`)

**Interfaces:**
- Consumes: `FlowHarness`, `FlowViewModel`, `openFlowDatabase`, `openFlowStore`, `capture` (fase 3); `RecordsRoute`, `ExerciseProgressRoute`, `ExerciseInfoRoute`.

- [ ] **Step 1: Teste.** `progressFlowPortugueseLightAtOneX` e `progressFlowEnglishDarkAtTwoX`, sobre o ViewModel, o Room (v6) e o DataStore reais, com um seletor de tela simples (`ProgressFlowApp`): semear pelo repositório seis treinos finalizados de supino com carga subindo e um recorde de repetições; na aba Recordes, buscar "supino" (pt-BR) ou "bench" (en) e ver o recorde atual no topo com "Atual"; tocar e ver o progresso na Carga com a marca; trocar para "1RM estimado"; "Ver dados" e conferir uma linha por sessão; "Sobre o exercício" abre o detalhe. Em cada estado: todo clicável com rótulo, alvos ≥ 48 dp, `assertNumbersWhole()`; `capture("progress-<estado>")` no iOS.
- [ ] **Step 2:** Rodar a classe no emulador pela trava até ficar verde. Defeito revelado é corrigido na task dona, com um teste focado lá.
- [ ] **Step 3:** Subir `MIN_SCREENS_IOS_TESTS` (+2). Commit `screens: end-to-end progress flow over the real view model, Room and DataStore`.

### Task 15: Orçamento de toques e varredura de acessibilidade

**Revisão:** Sonnet curta (no lote da Task 14). **Push 2** no fim.

**Files:**
- Create: `screens/src/uiTest/kotlin/com/mooscles/screens/a11y/ProgressAccessibilitySweepTest.kt`
- Modify: `screens/src/uiTest/kotlin/com/mooscles/screens/budget/TapBudgetTest.kt`, `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: os números de `tap-baseline-5a.md` (Task 13), copiados como literais com o link no comentário; `assertClickablesLabeledAndSized`, `assertButtonTextsWhole`, `assertNumbersWhole`, `screenReaderOrder`.

- [ ] **Step 1: Testes.**
  - `TapBudgetTest` (texto digitado não conta; cada um também ≤ a contagem de hoje): `seeTheCurrentRecordOfAnExercise` ≤ 1 (aba; a busca é digitada); `fromARecordToTheExerciseProgress` ≤ 2 (aba, linha), já com o gráfico; `seeTheEstimatedOneRepMaxOfTheBench` ≤ 3 (aba, linha, chip "1RM estimado").
  - `ProgressAccessibilitySweepTest`: Recordes (linha do tempo, "Só atuais", com busca, vazio, carregando, a sheet de regras), progresso (Carga total com recordes, Carga adicional, Assistência, uma sessão, tabela aberta, vazio) e o card "Seu histórico" com a sparkline × claro e escuro × 1,0, 1,3 e 2,0 (mais 2,01 no Android) × 320 e 412 dp × topo, meio e fim da rolagem. Em cada combinação: rótulo em todo clicável, alvos de 48 dp, `assertNumbersWhole()`, nenhum texto de botão cortado, o nó do gráfico com `Role.Image` e resumo não vazio. Mais `recordsReadingOrder` (topo, busca, chips, cabeçalho do mês, linhas) e `progressReadingOrder` (a ordem da Task 10).
- [ ] **Step 2:** Rodar as duas classes no emulador pela trava até ficarem verdes, corrigindo na task dona.
- [ ] **Step 3:** Subir `MIN_SCREENS_IOS_TESTS`. Commit `screens: progress tap budget and accessibility sweep`. **Push 2** (checkpoint das Tasks 5–15) e `gh run watch -R pedroyuuki/Mooscles` sem bloquear.

### Task 16: Suítes de aparelho, atualização por cima, sem rede, desempenho e tamanho

**Revisão:** controlador.

**Files:**
- Create: em `docs/superpowers/reports/AAAA-MM-DD-phase5a-verificacao/`, `device-suites.md`, `upgrade.md`, `offline.md`, `performance.md`, `apk.md`, `deps-diff.txt`; `screens/src/androidDeviceTest/kotlin/com/mooscles/screens/progress/ProgressIndexDeviceTimingTest.kt`
- Modify: o que as medidas pedirem, num bloco de commits com teste; a spec (nota datada da decisão 11)

- [ ] **Step 1: Suítes completas, uma vez** (nenhuma outra lane usa o emulador nem o Gradle; trava antes): `perl -e 'alarm shift; exec @ARGV' 5400 ./gradlew :designsystem:connectedAndroidDeviceTest`, depois `:screens:connectedAndroidDeviceTest`, depois `:app:connectedDebugAndroidTest` (inclui `UiFlowTest`, `UiFeatureSmokeTest`, `LegacyInstallOpenTest`; desinstala o app no fim, por isso vem antes da atualização). Registrar os totais. As falhas são corrigidas juntas, num bloco, cada uma com o teste que a prova; depois, só as classes afetadas rodam de novo.
- [ ] **Step 2: Atualização por cima** (rede desligada): com o APK da `main` no commit de origem da branch, em pt-BR e em libras, com a base grande importada (`mooscles-large-208.json`), um treino ativo com séries concluídas e o descanso correndo; exportar; `md5` de `files/datastore/local_preferences.preferences_pb` (`run-as com.mooscles`); contar treinos, séries e recordes e anotar o volume de três treinos; `adb install -r` do APK da branch. Conferir e registrar: o treino ativo e o timer intactos; as mesmas contagens e volumes; a aba Recordes e o progresso de um exercício abrem com os números do histórico; o banco puxado (`adb exec-out run-as com.mooscles cat databases/mooscles.db` mais `-wal` e `-shm`, lido com `sqlite3` no Mac) com `PRAGMA user_version` 6 e o schema igual; o mesmo MD5; o export depois igual ao de antes, tirando `exportedAt` (`diff` com `jq -S`).
- [ ] **Step 3: Sem rede:** instalação nova com a rede desligada desde antes: Recordes, busca, chips, progresso com cada métrica e período, "Ver dados" e o card com a sparkline funcionam. `git diff main...HEAD` sem código de rede novo (procurar `http`, `URL`, `HttpURLConnection`, `ktor`).
- [ ] **Step 4: Desempenho** pelo PERFORMANCE.md, no `assemblePerformance`, com a base grande: `ProgressIndexDeviceTimingTest` (aparelho, `LargeProgressHistory`: construção do índice em 9 rodadas, mediana ≤ 60 ms e p95 ≤ 120 ms; consultas de gráfico e de linha do tempo, e as teclas de "supino" na busca dos Recordes, mediana ≤ 4 ms e p95 ≤ 8 ms; imprime e confere); tocar na aba Recordes até a lista (segundo quadro, com o índice pronto, por `atrace`); tocar num recorde até o gráfico (≤ 2 quadros); rolagem rápida da linha do tempo com "Só atuais" (`gfxinfo` janky ≤ 5%); trocar kg/lb (o índice refeito uma vez, com o tempo); o `present()` completo da base grande (pendência da 4B); o treino ativo sem piora contra a fase 3. O teste de aparelho roda no APK de teste do módulo, não no R8: registrar isso e o que ficou só no emulador. Escrever na spec a nota datada da decisão 11.
- [ ] **Step 5: Tamanho e dependências:** `./gradlew :app:clean :app:assembleDebug` e `pwsh tools/check-apk-budget.ps1 -Apk app/build/outputs/apk/debug/app-debug.apk` (≤ 524.288 sobre a base; a 5A contra a meta de 262.144, com o que cada pacote somou) em `apk.md`; `./gradlew :app:dependencies --configuration debugRuntimeClasspath` contra a `main` em `deps-diff.txt`, vazio.
- [ ] **Step 6:** Commit `docs(report): phase 5A device suites, upgrade, offline, performance and size`, com as correções em commits próprios antes.

### Task 17: Capturas e crítica de tela

**Revisão:** controlador.

**Files:**
- Create: `docs/superpowers/reports/AAAA-MM-DD-phase5a-critica.md` e as capturas citadas em `…-phase5a-verificacao/` (`records-{timeline,current,search,empty,rules}-…`, `progress-{load,e1rm,table,bodyweight,assistance,one-session,empty}-…`, `info-sparkline-…`, `gallery-charts-…`; nos dois temas, em en e pt-BR, e a 200% em pt-BR)
- Modify: o que a crítica pedir, nos arquivos do `:screens` e do `:designsystem`, com teste quando a correção for de comportamento

- [ ] **Step 1: Capturas** com `adb exec-out screencap -p`, nos dois temas, em en e pt-BR (`adb shell cmd locale set-app-locales com.mooscles --locales pt-BR`) e a 200% (`adb shell settings put system font_scale 2.0`), mais as do iOS do `ProgressFlowTest` e do `ChartsTest` (artefato `ios-flow-captures` do Push 2).
- [ ] **Step 2: Crítica.** `critique-screen` (pelo `kit-design`) nos Recordes, no progresso (cada convenção e a tabela) e no card com a sparkline. Corrigir as de prioridade alta e média, com captura nova; as baixas ficam no relatório com o motivo.
- [ ] **Step 3: Verificar.** As classes de `uiTest` das telas corrigidas verdes no emulador; `check-design-tokens` limpo.
- [ ] **Step 4:** Commit `docs(report): phase 5A progress and records critique`, com as correções em commits próprios.

### Task 18: Documentação, relatório, revisão final e merge

**Revisão:** Sonnet (documentos) + `/code-review` + revisão final Opus da branch inteira (+ `compat-reviewer` se o `git diff --stat` dos caminhos de compatibilidade não estiver vazio).

**Files:**
- Create: `docs/superpowers/reports/AAAA-MM-DD-phase5a-verificacao.md`
- Modify: `AGENTS.md` (schema atual **v6** com `MIGRATION_5_6` nas listas, decisão 20; `charts/` no design system e o `check-design-tokens` cobrindo-o; `progress/` e `records/` e `format/WeekStart.kt` no `:screens`; `domain/EstimatedOneRepMax.kt` no `:shared`; o `ProgressIndexCache` no `MoosclesApplication` e o `LocalProgressIndexCache`; a rota `exercise/{id}?metric=`; Recordes sai da lista de telas não redesenhadas e de textos fixos em inglês; `strings_progress.xml` e `strings_records.xml`), `VERIFICATION.md`, `docs/superpowers/ROADMAP.md` (fase 5: 5A com spec, plano, relatórios e status), `docs/superpowers/releases/v1.5.0.md` (linha 10 de "O que entra", decisão 12), a spec (seção "Notas de implementação": confere que as notas das decisões 5–8, 10 e 11 estão lá e acrescenta as do S1 e o resultado das medidas)

- [ ] **Step 1: Relatório.** **Provado:** testes de host e de `commonTest` com os números do CI; o `ios` verde com o `ChartsTest`, o `ProgressFlowTest` e as capturas; o S1 nas duas plataformas; o `ProgressIndexDomainHostTest`; as suítes de aparelho (Task 16); o orçamento de toques contra `tap-baseline-5a.md`; a atualização por cima; o sem rede; o desempenho; o tamanho; o diff de dependências; a crítica; o `git diff --stat` dos caminhos de compatibilidade vazio. **Não verificado:** tudo num iPhone (VoiceOver lendo os resumos, arraste no gráfico); num Android físico (TalkBack, arrastar a seleção com o dedo, 120 Hz, desempenho no S23 Ultra); se os gráficos motivam; a qualidade do 1RM estimado para quem treina; o desempenho no R8 de verdade para o índice (o teste de aparelho roda no APK de teste); a release com a chave original.
- [ ] **Step 2:** Atualizar os documentos da lista.
- [ ] **Step 3: Verificar.** Os links do ROADMAP, da spec e dos relatórios abrem; o último run da branch está verde.
- [ ] **Step 4:** `/code-review` e a revisão final Opus da branch inteira (`git diff origin/main...HEAD`), com as correções num bloco e só as classes afetadas rodando de novo. Commit `docs: phase 5A charts, progress and records verified`. **Push 3** e `gh run watch -R pedroyuuki/Mooscles`. O merge de `feat/phase5a-progress` na `main` do fork (sem force, push para `origin`, nunca para o original) fica com o controlador, depois do CI verde e da revisão sem achado alto.

---

## Paralelismo

No máximo dois implementadores ao mesmo tempo, em worktrees separadas (`lane/phase5a-tN`), e no máximo um deles usando o emulador.

| Par | Por que pode | Cuidado |
| --- | --- | --- |
| Task 1 ∥ Task 2 | `:designsystem` (emulador) × `:shared`/`:screens` puros (host) | os dois sobem `MIN_HOST_TESTS`: o controlador soma |
| Task 1 ∥ Task 3 | emulador × host | — |
| Task 1 ∥ Task 4 | emulador × host | a 4 precisa da 2; o Push 1 espera as duas |
| Task 5 ∥ Task 6 | host × host, arquivos diferentes em `progress/` e `records/` | as duas precisam da 4; `ci.yml` somado pelo controlador |
| Task 7 ∥ Task 8 | host (com Robolectric) × emulador (`ProgressTextsTest`) | a 7 precisa da 5 e da 6; a 8 precisa da 5 |
| Task 9 ∥ Task 13 Step 1 | emulador × contagem nas telas antigas (host) | a contagem roda antes de qualquer tela antiga sair |

Todo o resto é sequencial: a 9 precisa da 1 (com o veredito iOS), 7 e 8; a 10 da 9 (mesmos arquivos); a 11 da 6, 7 e 8; a 12 da 1, 5 e 7; a 13 (do Step 2 em diante) da 10, 11 e 12; a 14 e a 15 da 13; a 16, a 17 e a 18 de tudo, e rodam sozinhas.

## Pré-voo: arquivos e interfaces compartilhados

| Tasks | O que compartilham | Nomes e assinaturas batem? |
| --- | --- | --- |
| 1 → 9, 10, 12, 15 | `MLineChart(points, summary, yLabel, xLabels, selected, onSelect, modifier, contentKey)`, `MSparkline(values, modifier)`, `ChartDataButton(showingTable, onClick, modifier)`, `ChartTableRow(row, modifier)`, `ChartTableRowData`, `LinePoint`, `AxisLabel`, `ChartScale.labelIndices`, `MChartLegendRecord`, `MIcons.Table`, `MIcons.ChartLine` | sim |
| 2 → 4, 5, 8 | `EstimatedOneRepMax.estimate(weightKg, reps)`, `Trend`, `Trends.of(xs, ys)` | sim |
| 3 → 5, 6, 9 | `WeekStart.of(tag)`, `currentWeekStart()`, `ScreenDates.localDate(epochMillis, zone)` | sim |
| 4 → 5–13 | `SeriesKey`, `SetValue`, `SessionPoint`, `RecordEvent`, `ProgressIndex.build(history, records)`, `sessions`, `conventionsOf`, `snapshot`, `events`, `currentRecords`, `searchRecords`, `ProgressMetric`, `ProgressMetrics.available/initial/value/marked` | sim |
| 5 → 7, 8, 9, 12 | `ExercisePeriod`, `ExerciseChartQuery(key, metric, period, today, zone, weekStart)`, `ExerciseChart`, `ChartPoint`, `EmptyReason`, `ExerciseCharts.query(index, query)`, `ExerciseCharts.sparkline(index, key, last)` | sim |
| 6 → 7, 11 | `RecordsQuery(text, currentOnly, firstRecords, zone)`, `RecordsTimeline`, `RecordMonth`, `RecordTimelines.query(index, query)`, `RecordTimelines.key(event)` | sim |
| 7 → 9–13, 16 | `ProgressIndexCache(scope, context, prewarmDelayMillis, build)`, `peek`, `get`, `prewarm(history, records, zone)`, `peekQuery`, `query`, `LocalProgressIndexCache`, `rememberProgressIndex(history, records)`, `rememberProgressQuery(index, key, compute)`, `LargeProgressHistory.state(exercises)` | sim |
| 8 → 9–11 | `metricLabel`, `periodLabel`, `valueText`, `axisText`, `sinceText`, `readoutText`, `chartSummary`, `tableRows`, `ProgressNumbers`, `ScreenDates.monthYear`, `spokenDate` | sim |
| 9, 10 → 13 | `ExerciseProgressRoute(exerciseId, metric, state, actions, onBack, onAbout, onOpenWorkout, modifier)`, `ExerciseProgressTag` | sim |
| 11 → 13 | `RecordsRoute(state, actions, onOpenProgress, onOpenWorkout, onOpenSettings, modifier)`, `RecordsScreenTag` | sim |
| 13 → 15 | `tap-baseline-5a.md` | sim |
| 1 ∥ 2, 5 ∥ 6, 7 ∥ 8 | `.github/workflows/ci.yml` (mínimos) | conflito de uma linha, resolvido somando |

## Notas datadas

A task que muda uma decisão da spec escreve a nota datada nela, na seção "Notas de implementação" no fim da spec (criada pela primeira que precisar), com a data, a task e o motivo: decisões 5 e 6 (Task 4), 7 (Task 5), 8 (Task 10), 10 (Task 13), 11 (Task 16) e o veredito do S1 (Task 1, preenchido pelo controlador depois do Push 1). Uma decisão nova que surgir na implementação segue a mesma regra, na task em que surgir. A Task 18 só confere.
