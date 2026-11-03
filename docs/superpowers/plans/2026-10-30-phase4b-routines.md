# Fase 4B: rotinas, plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trocar a aba Treinar e o editor de rotina por telas novas em código comum (lista de rotinas por pasta, gerenciador de pastas, editor com arrastar e soltar, prescrição por série com aquecimento), dar a linha de alvo ao card do treino, a seleção múltipla ao seletor e a foto ao exercício personalizado, com a única mudança de schema da fase (v5 → v6, aditiva) e sem mudar chaves, rede nem identidade do app.

**Architecture:** O `:shared` ganha a coluna `routine_sets.warmup` (migração aditiva, backup com campo opcional), os comandos novos de rotina e pasta no repositório, as ações do contrato, a linha de alvo calculada na apresentação e a atribuição de erro por comando (`UiState.errorSubject`). O `:designsystem` ganha o modificador de reordenação (que é o spike S2, provado no emulador e no simulador do CI) e a figura deixa de pedir quadros na pausa. Um spike Android (S4) fecha o pipeline da foto, que entra como interface comum no `:shared`, implementação no `:app` e telas no `:screens`. O `:screens` ganha `routines/` (Treinar, pastas, editor), o formato do alvo, o seletor múltiplo e a foto no formulário (que fica aberto até o exercício chegar). Por fim o `:app` hospeda as rotas e apaga as telas antigas, e a etapa fecha com o fluxo de ponta a ponta, o orçamento de toques, a varredura de acessibilidade, uma rodada única das suítes de aparelho, a atualização por cima v5 → v6, o desempenho, a crítica e a documentação.

**Tech Stack:** Kotlin 2.4.21 Multiplatform, AGP 9.3.3, Gradle 9.7.1, Compose Multiplatform 1.12.2 (`foundation`, `ui`, `animation`, `components-resources` com `decodeToImageBitmap`; testes com `androidx.compose.ui.test.v2.runComposeUiTest`), Jetpack Compose 1.12.1 e `activity-compose` 1.13.0 no `:app` (`PickVisualMedia`, `TakePicture`), Room 2.8.5 (schema **v6**), `kotlinx-serialization-json` 1.12.0, `kotlinx-datetime` 0.8.0, `android.graphics.ImageDecoder` (API 28+) e `android.media.ExifInterface` do framework (API 26–27), JUnit 4 + Robolectric 4.17 (`@GraphicsMode(NATIVE)` para a foto), `kotlin.test`, GitHub Actions (`ubuntu-latest` e `macos-26` com Xcode 26.4). Nenhuma dependência nova.

**Spec:** [docs/superpowers/specs/2026-10-29-phase4-library-routines-design.md](../specs/2026-10-29-phase4-library-routines-design.md) (etapa 4B: "Telas da 4B", D14–D20, "Arquitetura", "Dados e compatibilidade", "Desempenho", "Orçamento de toques", S2 e S4, "Como verificar", e as [notas de implementação](../specs/2026-10-29-phase4-library-routines-design.md#notas-de-implementação) de 2026-10-30). Estrutura e convenções: [plano da 4A](2026-10-29-phase4a-library.md) e [plano da fase 3](2026-10-29-phase3-active-workout.md). Pendências herdadas: [verificação da 4A](../reports/2026-10-30-phase4a-verificacao.md#pendências-que-não-bloqueiam-o-merge).

**Fora deste plano:**
- **Lotes de figuras e retirada do piloto (G1):** continuam como as Tasks L e R do [plano da 4A](2026-10-29-phase4a-library.md#task-l-condicional-só-com-o-g1-aprovado-um-lote-de-figuras). Ver "Task L" no fim.
- **4M, download de mídia (D10):** só com mídia encomendada.
- **Histórico, recordes e o detalhe antigo do treino (`HistoryScreens.kt`):** fase 5. Eles continuam usando `ExercisePicker`, `CustomExerciseDialog`, `SearchField`, `ChoiceFilter`, `WorkoutExerciseCard` e `RestDurationField`, que ficam.
- **Seletor de fotos e câmera do iOS:** fase 7, com o `iosApp`.

## Decisões travadas por este plano

As decisões da spec valem como estão. Estas são as que a spec deixou para quem implementa, as pendências da 4A que este plano fecha e os conflitos achados. Todas são **minhas**, com a delegação do dono.

1. **Branches e push.** O controlador mescla `docs/phase4b-plan` na `main` do fork e cria `feat/phase4b-routines` a partir dela antes da Task 1. O spike S4 usa `spike/phase4b-photo`, nunca mesclada. **Push só em três checkpoints**, marcados **Push** no fim das Tasks 6, 15 e 20. Não faça push enquanto o job `ios` do checkpoint anterior estiver na fila ou rodando, a não ser que ele já esteja perdido.
2. **O S2 é o componente de verdade.** A reordenação nasce no `:designsystem` na Task 4, na própria branch, com os testes do spike como testes do componente. O lado Android se prova no emulador dentro da task; o lado iOS, no CI do Push 1, antes de qualquer tela usar o componente. Se o arraste injetado falhar no simulador, os testes de arraste passam a rodar só onde `INJECTS_DRAG` (constante nova de teste, como a `INJECTS_TOUCH_CANCEL`) for verdadeira, o produto não muda (as ações de acessibilidade e o menu funcionam nas duas plataformas) e a falha vai para o relatório do spike e para uma nota datada da spec.
3. **Reordenar rotinas grava numa transação só.** A posição das rotinas é global. A ação `reorderRoutines(folderId, ids)` chama o repositório novo `reorderFolderRoutines(folderId, ids)`, que, dentro de uma `writeTransaction`, põe os `ids` nas posições globais que as rotinas da pasta já ocupavam e não mexe nas outras. *Por quê:* a D15 previa o VM montando a ordem completa, o que abre uma janela entre ler e gravar; a spec deixa a assinatura com quem implementa. O `reorderRoutines(ids)` e o `moveRoutine` atuais ficam.
4. **Excluir pasta**, numa `writeTransaction`, sem `catch`: recusa a padrão (`INVALID_VALUE`) e uma pasta que não existe (`FOLDER_NOT_FOUND`); garante a padrão; renumera as posições globais das rotinas pondo as da pasta excluída no fim, na ordem relativa delas (elas aparecem no fim de "Minhas rotinas"); muda o `folderId` delas para `default`; apaga a pasta; renumera as pastas de 0 a n−1. O VM, depois, volta a seleção para a padrão se a pasta excluída era a selecionada. **A pasta padrão** se reordena como qualquer outra e só não pode ser excluída.
5. **Desfazer a remoção de uma série planejada guarda o id.** A assinatura é `restoreRoutineSet(entryId, position, set: RoutineSetUi)` (a spec listava os valores sem o id): um segundo desfazer, um id que já existe, a entrada que sumiu ou a 51ª série são recusados com `RESTORE_UNAVAILABLE` (que vira `UiError.SET_NOT_RESTORED`), como no treino.
6. **Regras do aquecimento planejado (D14).** `setRoutineSetWarmup(id, true)` deixa a série manual (`inheritPr = false`) com os mesmos valores; `false` a mantém manual. `inheritRoutineSet` sempre produz uma série de trabalho (`warmup = false`). A sincronização de alvos herdados ignora linhas com `warmup`, mesmo que um backup traga `warmup` e `inheritPr` juntos. `addRoutineSet` copia a última série (valores, `warmup`, `inheritPr`); sem nenhuma, herda o recorde como hoje. `startRoutine` copia o `warmup`; `duplicateRoutine` copia a linha inteira. No menu da série, "Seguir o recorde" só aparece numa série de trabalho manual.
7. **Erro por comando (fecha o "per-command results" da 4A).** `UiState.errorSubject: String?` diz sobre que id foi o comando que falhou. Os comandos com assunto: `createCustomExercise` e `saveCustomExercise` (o id do exercício), `createRoutine` (o id novo), `duplicateRoutine` (o id da cópia), `addRoutineExercise` e `addRoutineExercises` (o id da rotina), `addWorkoutExercise` (o id do treino), `deleteRoutineFolder` (o id da pasta), `restoreRoutineSet` e `restoreSet` (o id da série). Os outros ficam com `null`. Uma tela que espera um eco só o cancela com `errorCode != null && errorSubject == <o id que ela espera>`; um erro de outro assunto continua aparecendo no toast e não mexe na espera.
8. **Linha de alvo (D16).** O `WorkoutPresentation` preenche `WorkoutExerciseUi.target` só no treino ativo, a partir da rotina de origem como está agora: a n-ésima entrada do treino com um `exerciseId` casa com a n-ésima ocorrência dele na rotina, por posição. O formato é uma função pura do `:screens` com os textos injetados, usada no card do treino e no editor. Regras exatas na Task 8.
9. **"Começar" nos cards é `Tonal` L (56 dp), não `Primary`.** O `MButton` do design system admite um só `Primary` por tela, e a lista tem um "Começar" por card (conflito com a spec, que diz "primário"). O `Primary` da lista fica sem uso; no editor, "Começar treino" é o `Primary` L fixo. O card da rotina de origem do treino ativo mostra "Retomar treino" (abre o treino, como o "Resume active" de hoje); "Começar" em outra rotina com um treino ativo abre o `MDialog` "Já existe um treino em andamento".
10. **As tags de teste antigas continuam nas telas novas**, para os testes da casca no `:app` não mudarem: `routines-list` (a lista da aba Treinar), `routine-<id>-start`, `routine-folder-selector` (a linha de chips de pasta), `routine-editor-list` e `routine-editor-start`.
11. **Foto: onde cada parte mora.** Interface `ExercisePhotoStore` no `:shared` (`com.mooscles.photos`), com a origem como `String` de URI (como o backup); implementação Android no `:app` (`com.mooscles.photos.AndroidExercisePhotoStore`); a captura (`PhotoCapture`, lançadores de galeria e câmera) é interface do `:screens` implementada no `:app`; as telas recebem tudo por `LocalExercisePhotos`. Nenhum `expect/actual` novo. **Sem `androidx.exifinterface`:** nas APIs 26 e 27 o pipeline usa o `android.media.ExifInterface` do framework (que lê de `InputStream` desde a API 24), e da API 28 em diante o `ImageDecoder`, que já aplica a orientação (desvio da D17, que previa a biblioteca; nenhuma dependência entra). Arquivos: `files/exercise_photos/<id>.jpg` e `<id>.thumb.jpg`; rascunhos em `cache/exercise_photo_drafts/<draftKey>.jpg` e `.thumb.jpg`; a captura da câmera em `cache/photo-capture/`, exposta pelo `FileProvider`. Na partida, depois do `initialize()`, saem as fotos de ids que não são exercícios personalizados existentes e os rascunhos com mais de 24 h (um rascunho mais novo pode ser de um formulário que o Android vai restaurar depois da morte do processo). Ao editar, trocar ou remover a foto só vale no "Salvar".
12. **O formulário fica aberto até o exercício chegar** (substitui a decisão 13 da 4A, como a nota de 2026-10-30 da spec previa). "Salvar" fica ocupado (`MButton(loading = true)`) até o eco (criar: o id aparece em `state.exercises`; editar: o exercício no estado tem os campos salvos) ou um erro com `errorSubject` igual ao id, que libera o botão com o rascunho e a foto intactos. Com o eco, a foto do rascunho é confirmada no id e só então `onSaved(id)`; a biblioteca abre o detalhe no mesmo quadro em que o formulário fecha (fecha o M5 da crítica da 4A).
13. **O nome da rotina no editor é a primeira linha do conteúdo que rola** (`title.md`, inteiro, tocável para renomear), e a barra fixa fica com voltar e o menu, como o detalhe do exercício depois da correção da Task 17 da 4A. *Por quê:* o mesmo nome de 120 caracteres a 200% esconderia as ações na barra.
14. **Arrastar um exercício no editor recolhe os cards.** Ao erguer a alça, todos os cards do editor passam a mostrar só o cabeçalho (como o `collapsed` do `ExerciseCardHeader`), e o item arrastado fica sob o dedo (o estado da reordenação reancora pelo `key` quando a altura muda). Ao soltar, voltam como estavam. *Por quê:* a lista do editor tem um item por série; arrastar um grupo de itens de alturas variáveis numa `LazyColumn` não tem solução estável, e é o padrão dos apps de treino.
15. **Componentes da fase 3 generalizados, não copiados:** `RenameSheet` ganha `title`, `confirmLabel`, `fieldLabel` e `selectAll` (padrões = o comportamento de hoje) e serve para nova rotina, renomear rotina, nova pasta e renomear pasta; `RestSheet` ganha a forma `RestSheet(exerciseName, seconds, key, onSave, onStartNow: ((Int) -> Unit)?, onDismiss)` (o editor passa `onStartNow = null`), e a forma de hoje passa a chamá-la.
16. **A edição da série planejada tem um modelo próprio**, `PlannedSetDrafts` (carga anulável, kg exato atrás da libra, confirmação fora de ordem), em vez do `SetRowEditor` do treino, que é preso a `SetUi` e ao estado da tela do treino. Os testes do `RoutineEditorHostTest` antigo viram testes dele.
17. **Duplicar dá nome no idioma da tela.** `duplicateRoutine(id, copyName, newId)`: a tela manda "Treino A (cópia)" / "Push day (copy)" (cortado em 120) e o id da cópia; o `duplicateRoutine(id)` de hoje, que acrescenta " (copy)" em inglês, fica só para quem já o chama. Depois do eco, o toast "Rotina duplicada" com "Abrir".
18. **"Movido para a posição" sem gênero:** "Supino reto agora na posição 1 de 5" / "Bench press moved to position 1 of 5" (a forma da spec, "movido", erra o gênero de "Remada").
19. **Barras de sistema cinzas da janela do formulário no tema claro:** continuam estacionadas, com o L5 da fase 3, para a fase 6. Corrigir pede um quarto `expect/actual` no design system, que o AGENTS.md limita a três. Custo: as barras cinzas no tema claro.
20. **Figura do detalhe na pausa (bateria):** a alternância deixa de pedir quadros entre um cross-fade e o outro (Task 5).
21. **`LoadConvention.fromLabelOrName`** em `Models.kt`, usado pelo VM e pelo `CatalogLabels` (a duplicação que a revisão final da 4A adiou).
22. **"Última vez"** vem de `RoutineUi.lastFinishedAt` (o maior `endedAt` dos treinos finalizados com `sourceRoutineId` igual), calculado na apresentação, e se lê "hoje", "ontem", "há N dias" (até 30) ou a data curta.
23. **Orçamento de APK:** `maxGrowthBytes: 1048576` sobre a mesma base da 4A (decisão 18 da 4A), editado pelo controlador antes da Task 1.
24. **Sem versão nova:** `versionCode` 16 e `versionName` 1.4.5 não mudam.

## Global Constraints

- **Dados.** Room passa a `version = 6` com uma só migração nova, `MIGRATION_5_6`: `ALTER TABLE routine_sets ADD COLUMN warmup INTEGER NOT NULL DEFAULT 0`, e `@ColumnInfo(defaultValue = "0")` na entidade, registrada em `buildMooscles()`. O `6.json` é gerado pelo KSP e entra no commit; nenhum JSON de schema editado à mão (o hook bloqueia). Nenhuma chave nova, renomeada ou com outro tipo em `local_preferences` ou `app_updates`; a pasta selecionada continua em `selected_routine_folder`. Backup: `RoutineSetRow.warmup: Boolean = false`, opcional, **sem** subir `FORMAT_VERSION`. Toda escrita nova numa `writeTransaction`, **sem `catch` dentro**. `applicationId`, assinatura, `minSdk`, `update.json`, `ReleaseUrls.REPOSITORY` e a descoberta de atualizações não mudam; `allowBackup=false` continua.
- **Android:** só acréscimos no manifesto (`<queries>` com `android.media.action.IMAGE_CAPTURE`) e no XML do `FileProvider` (`<cache-path name="photo_capture" path="photo-capture/" />`). **Nenhuma permissão nova**, em especial nenhuma `CAMERA`.
- **Rede:** nenhuma requisição nova.
- **Dependências:** nenhuma nova; o `debugRuntimeClasspath` do `:app` fica igual (diff na Task 18). Nada de Material 3 no `:screens`, nem biblioteca de reordenação, imagem (Coil) ou EXIF.
- **APK debug:** até 1.048.576 bytes sobre a base da 4A (D20, 4A e 4B somadas); fontes até 900 KiB e fontes + ícones até 1 MiB, como antes.
- **Um design só:** toda tela, componente e texto novo em `commonMain`; o único código de plataforma novo é a foto no `:app`. Nenhum `expect/actual` novo. `grep -rnE "\b(java|javax|android)\.|System\.|String\.format" screens/src/commonMain designsystem/src/commonMain/kotlin/com/mooscles/designsystem/interaction` volta vazio.
- **Tokens:** nenhum literal de cor, medida ou duração fora de `tokens/`; o `tools/check-design-tokens.ps1` cobre os arquivos novos do design system; medida nova vira token com o valor fixado em `commonTest`.
- **Acessibilidade:** alvos de 48 dp com 8 dp entre alvos (56 dp em "Começar" e "Começar treino"); nada abaixo de 12 sp; todo botão de ícone com rótulo; nenhum estado só por cor; todo arraste com as quatro ações de acessibilidade e o menu (WCAG 2.5.7); pares de cor novos no `ComponentColorsTest`; nenhum número cortado (`assertShownWhole`/`assertNumbersWhole`) a 1,0, 1,3 e 2,0 em 320 e 412 dp, e a 2,01 no Android.
- **Textos:** inglês base e pt-BR, voz da fase 2 (direto, "você", um verbo no botão, sem exclamação nem emoji). Strings do `:screens` em `screens/src/commonMain/composeResources/values*/strings_{routines,workout,exercise}.xml`; de componente no `strings_components.xml`; da galeria no `strings_gallery.xml`; de Configurações no `strings_settings.xml` do `:designsystem`. O 0 fica no plural (`pluralCount`). Nenhum texto desta etapa em inglês com o aparelho em pt-BR, menos os nomes digitados e a lacuna da decisão 11 da 4A (descanso automático).
- **Lições que viram regra:** `runComposeUiTest` da `v2` com espera explícita; `useUnmergedTree = true` nos campos; recriação pelo `ScreenRestoration`; contador pelo `RecompositionProbe` com controle positivo; gesto cancelado só onde `INJECTS_TOUCH_CANCEL`; **nunca rolar uma lista lazy com o relógio do teste parado** (pare o relógio só depois de rolar); **testes de diálogo e de sheet passam pelo `MOverlayWindow`** (densidade e escala de fonte do host), e um teste "2×" confere que a janela está mesmo a 2×.
- **Processo (fast dev loop, revisão enxuta):**
  - ambiente de toda chamada de shell: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) ANDROID_HOME=$HOME/Android/sdk ANDROID_SERIAL=emulator-5556 PATH="$HOME/.local/bin:$PATH"`;
  - **todo comando do Gradle ou do aparelho tem limite de tempo** por `perl -e 'alarm shift; exec @ARGV' <s> …`: 900 s para testes de host focados e builds, 1800 s para classes no aparelho, 5400 s para as suítes completas da Task 18;
  - **portão do emulador:** antes de qualquer comando que use o aparelho, rode o portão abaixo (guardado como `<scratchpad>/emu-gate.sh`) no mesmo comando (`sh <scratchpad>/emu-gate.sh && perl -e 'alarm shift; exec @ARGV' 1800 ./gradlew …`). Se uma execução morta deixou um "run started" sem "run finished", o controlador limpa com `adb logcat -c`, nunca o implementador:

    ```sh
    quiet=0
    while [ "$quiet" -lt 60 ]; do
      busy=$(adb shell ps -A | grep -c '\.test$')
      log=$(adb logcat -d -s TestRunner)
      started=$(printf '%s\n' "$log" | grep -c 'run started')
      finished=$(printf '%s\n' "$log" | grep -c 'run finished')
      if [ "$busy" -eq 0 ] && [ "$started" -le "$finished" ]; then quiet=$((quiet + 5)); else quiet=0; fi
      sleep 5
    done
    ```
  - durante a task, o implementador roda **só os testes que tocou**: as classes de host e, no aparelho, só as classes de `uiTest` que criou ou mudou, com `-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>[,<FQCN>…]`. Compilar não é prova: toda classe de `uiTest` nova ou mudada roda no emulador dentro da própria task. Esperado: `BUILD SUCCESSFUL` e o XML em `<módulo>/build/outputs/androidTest-results/` sem falhas;
  - **as suítes completas de aparelho** (`:designsystem` e `:screens` `connectedAndroidDeviceTest`, `:app:connectedDebugAndroidTest`) **rodam uma vez só**, na Task 18. O CI roda host e iOS nos três pushes;
  - no máximo **dois implementadores** ao mesmo tempo, em worktrees com branch própria (`lane/phase4b-tN`), e no máximo **um usando o emulador**; nenhum Gradle roda enquanto um agente dirige a UI do emulador por `adb`; com duas lanes, cada Gradle usa `--max-workers=2`;
  - **revisão por risco** (cada task diz a sua): *Opus completa* para dados e schema, navegação, telas com comportamento novo e concorrência; *Sonnet curta* para conteúdo, testes e formato; *controlador* para spike, verificação e documentação. Uma correção pequena provada por um teste que falha e depois passa leva só a conferência do controlador. Edições mecânicas (mínimos do CI ao mesclar lanes, notas datadas, o `apk-budget.json`) são do controlador, sem agente;
  - toda task que acrescenta testes sobe o mínimo no `ci.yml` pelo número de testes novos, com a linha por classe no comentário: `MIN_HOST_TESTS` (hoje 935), `MIN_IOS_TESTS` (259), `MIN_DS_IOS_TESTS` (340), `MIN_SCREENS_IOS_TESTS` (433). Um teste aposentado só desconta com o motivo no commit. Duas lanes que sobem a mesma linha: o controlador soma;
  - o `compat-reviewer` dá `SEGURO` antes do commit nas Tasks 1, 2, 3 e 7, e na branch inteira na Task 20;
  - o iOS só se prova no CI; desligue a rede do emulador (`adb shell svc wifi disable; adb shell svc data disable`) antes de instalar APKs; nunca desinstale nada de um aparelho do dono.
- Código, comentários, KDoc e commits em inglês. Plano e relatórios em pt-BR. Commits terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **Evidências:** `docs/superpowers/reports/AAAA-MM-DD-phase4b-verificacao/` (criada pela primeira task que grava nela, com `mkdir -p`). Só entram no git as capturas citadas nos relatórios, em PNG com paleta reduzida quando a crítica não precisar de cor exata (`pngquant --quality 60-80`); o resto vai para `…/local/` (já no `.gitignore`).

## Review Focus

1. **Excluir uma pasta que tem a rotina de origem do treino ativo, que está selecionada ou que tem rotinas.** Nenhuma rotina some; o treino ativo continua com o mesmo `sourceRoutineId` e a linha de alvo continua no card; a seleção volta para "Minhas rotinas"; as rotinas movidas aparecem no fim dela. Coberto na Task 2 por `RoutineCommandsPersistenceTest.deletingAFolderMovesItsRoutinesToTheEndOfTheDefault` e `deletingAFolderKeepsTheActiveWorkoutsSource`, e na Task 3 por `AppViewModelRoutinesHostTest.deletingTheSelectedFolderSelectsTheDefault`.
2. **A lista muda debaixo de um arraste** (um eco de outra escrita, um exercício adicionado pelo detalhe, um import), **o item arrastado some, ou o gesto é cancelado.** Nada é gravado antes de soltar; ao soltar, a ordem gravada tem exatamente os ids atuais (nenhum `INVALID_VALUE`); um item que sumiu encerra o arraste sem gravar; o cancelamento devolve o item. Coberto na Task 4 por `ReorderTest.listChangeDuringDragDropsWithTheCurrentKeys`, `draggedItemThatDisappearsEndsTheDragWithoutAWrite` e `cancelledGestureRestoresAndWritesNothing`, e na Task 13 por `RoutineEditorScreenTest.exerciseRemovedElsewhereDuringADragDropsWithoutAnError`.
3. **Desfazer depois que a origem mudou:** a série removida cuja entrada foi trocada ou removida antes do "Desfazer", o desfazer tocado duas vezes, ou a série removida enquanto estava em edição com um rascunho. A restauração é recusada com o texto de `SET_NOT_RESTORED`, nunca duplica, e o rascunho da série removida é descartado. Coberto na Task 2 por `RoutineCommandsPersistenceTest.restoreRoutineSetTwiceRefusesTheSecond` e `restoreAfterTheEntryIsRemovedIsRefused`, e na Task 14 por `RoutineEditorSetsTest.undoAfterTheExerciseIsGoneShowsNotRestored` e `removingASetInEditDropsItsDraft`.
4. **Foto escolhida e depois: girar a tela, o processo morrer, a gravação do exercício falhar, o disco encher, cancelar, ou um backup importado tirar o exercício.** O rascunho sobrevive à recriação; uma falha de gravação mantém o formulário aberto com a foto; uma falha da foto mantém o exercício salvo e mostra "Não foi possível salvar a foto."; cancelar apaga o rascunho; a foto órfã sai na partida. Coberto na Task 10 por `CustomExerciseFormPhotoTest.photoDraftSurvivesRecreation`, `failedCreateKeepsTheFormOpenWithThePhoto`, `photoFailureToastsAndKeepsTheExercise` e `cancelDiscardsTheDraft`, e na Task 7 por `ExercisePhotoStoreHostTest.oldDraftsGoFreshDraftsStay` e `AppViewModelPhotosHostTest.orphanPhotosAreRemovedAfterInitialize`.
5. **Alvo com entradas incomuns:** o mesmo exercício duas vezes na rotina e no treino, uma entrada trocada ou acrescentada no treino, a rotina apagada ou editada no meio do treino, libras, só aquecimentos, cargas vazias misturadas. Cada entrada casa por ocorrência; trocada ou nova não tem alvo; rotina apagada tira a linha; editar a rotina muda a linha e nunca as séries registradas; o texto segue as regras da Task 8. Coberto na Task 3 por `WorkoutPresentationTest.sameExerciseTwiceMatchesByOccurrence`, `replacedOrAddedEntryHasNoTarget`, `deletedRoutineDropsTheTarget` e `AppViewModelRoutinesHostTest.editingTheSourceRoutineMidWorkoutChangesOnlyTheTarget`, e na Task 8 pela tabela do `RoutineTargetFormatTest`.

## Destino dos testes atuais

Regra: um teste cujo alvo continua no código fica; um cujo alvo sai é portado (nome novo na task indicada) ou aposentado com o motivo abaixo, no mesmo commit em que o código sai.

| Teste atual | Destino |
| --- | --- |
| `RoutineTargetMigrationTest`, `RoutineFolderPersistenceTest`, `RollbackV142DataCompatibilityTest` | Task 1: `MIGRATION_5_6` entra nas listas montadas à mão |
| `MigrationEquivalenceTest` | Task 1: as referências **não mudam**; a comparação passa a tirar do dump as adições da v6 (a coluna `warmup` de `routine_sets` no `sqlite_master` e nas linhas, e o `user_version` 6 → 5), e um teste novo confere que tudo o que foi tirado era `0` (a spec dizia que o teste "não muda"; com a `version` 6, o `WorkoutDatabase.open` leva o banco à v6 e a comparação crua quebraria) |
| `IosDatabaseTest.freshDatabaseHasTheDefaultFolderAndVersionFive` | Task 1: vira `…VersionSix` |
| Testes que conferem `user_version == 5` como "a versão atual" (`grep -rn "user_version\|VersionFive\|version = 5" app/src shared/src`) | Task 1: passam a 6, com o motivo; os que conferem um banco legado continuam |
| `RoutineEditorHostTest` (13) | portados: a lógica de rascunho para `PlannedSetDraftsTest` (Task 14, `commonTest`, mesmos nomes), a interface para `RoutineEditorSetsTest` (Task 14) e o descanso para `RoutineEditorScreenTest.restChipOpensTheRestSheetAndSavesOnlyThatEntry` (Task 13); aposentados na Task 15 |
| `RoutineEditorVisualHostTest` (1) | aposentado na Task 15 (capturas da tela antiga; as capturas novas são da Task 19) |
| `RoutineFoldersHostTest` (2) | portados para `TrainScreenTest` (Task 11), mesmos nomes |
| `RoutineResumeHostTest` (7) | portados para `TrainScreenTest` (Task 11), mesmos nomes; `editorAlsoProtectsAnUnrelatedActiveSession` vai para `RoutineEditorScreenTest` (Task 13) |
| `UiFlowHostTest.routineToWorkoutRestSummaryAndHistory` | Task 15: reescrito sobre as telas novas, mesmo nome |
| `androidTest/UiFlowTest`, `androidTest/UiFeatureSmokeTest.foldersAndRepeatedDiscard…` | Task 15: ajustados às telas novas (tags da decisão 10; "Resume active" → `routines_resume`); rodam na Task 18 |
| `ShellNavigationHostTest`, `MainActivityOpenWorkoutHostTest`, `DiscardNavigationHostTest`, `LibraryNavigationHostTest.addToRoutineOpenOpensTheRoutineEditor` | ficam (tags da decisão 10); ajustados só onde um texto antigo é procurado |
| `InlineRestEditorHostTest`, `WorkoutInputHostTest` | ficam: `WorkoutExerciseCard` e `RestDurationField` continuam no histórico (fase 5) |
| `ExercisePickerSheetTest`, `CustomExerciseFormTest`, `LibraryScreenTest`, `ExerciseInfoScreenTest` | ficam; ganham testes nas Tasks 9 e 10; `LibraryScreenTest.creatingOpensTheNewExerciseWhenItArrives` e `creationErrorDropsThePendingOpen` mudam de forma na Task 10 (a espera passa para o formulário) e mantêm os nomes |

---

### Task 1: Schema v6 e aquecimento no backup

**Revisão:** Opus completa + `compat-reviewer`.

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/mooscles/data/Models.kt` (`RoutineSet`), `.../data/WorkoutDatabase.kt` (`version = 6`, `MIGRATION_5_6`, `buildMooscles`), `shared/src/commonMain/kotlin/com/mooscles/backup/BackupFormat.kt` (`RoutineSetRow`), `.../backup/BackupRows.kt`, `app/src/test/java/com/mooscles/data/RoutineTargetMigrationTest.kt`, `RoutineFolderPersistenceTest.kt`, `RollbackV142DataCompatibilityTest.kt`, `MigrationEquivalenceTest.kt`, `shared/src/iosTest/kotlin/com/mooscles/data/IosDatabaseTest.kt`, `.github/workflows/ci.yml`
- Create: `shared/schemas/com.mooscles.data.WorkoutDatabase/6.json` (gerado pelo KSP), `shared/src/testFixtures/kotlin/com/mooscles/data/SchemaV5.kt`
- Test: `app/src/test/java/com/mooscles/data/RoutineWarmupMigrationTest.kt`, `app/src/test/java/com/mooscles/data/SchemaV5FixtureTest.kt`, testes novos em `shared/src/commonTest/.../backup/BackupCodecTest.kt`, `ImportPlannerTest.kt`, `MigrationEquivalenceTest.kt`, `IosDatabaseTest.kt`

**Interfaces:**
- Produces: `RoutineSet(…, val inheritPr: Boolean = true, @ColumnInfo(defaultValue = "0") val warmup: Boolean = false)` (último parâmetro); `WorkoutDatabase.MIGRATION_5_6`; `RoutineSetRow(…, val inheritPr: Boolean, val warmup: Boolean = false)`.
- Produces: `object SchemaV5 { val statements: List<String> }` (os `createSql` e o `setupQueries` do `5.json`, com `${TABLE_NAME}` resolvido), visível no `commonTest` e no `iosTest`.

- [ ] **Step 1: Testes que falham.**
  - `RoutineWarmupMigrationTest` (Robolectric, banco v5 criado do `5.json` pelo `DriverTestSupport`): `v5DatabaseMigratesWithEveryRowIntactAndWarmupZero` (duas pastas, três rotinas, séries planejadas herdadas e manuais, um treino finalizado e um ativo com `sourceRoutineId`, checkpoints: todas as linhas iguais depois, `warmup = 0` em toda `routine_sets`, `PRAGMA user_version` 6); `freshInstallAndMigrationReachTheSameSchema` (compara, tabela a tabela, `PRAGMA table_info`, `foreign_key_list`, `index_list` e `index_info` de uma instalação nova e de uma v5 migrada; o texto do `sqlite_master` difere pela ordem da coluna e não entra); `warmupRoundTripsThroughTheEntity`.
  - `SchemaV5FixtureTest.matchesTheExportedJson`.
  - `MigrationEquivalenceTest.v6AdditionsAreOnlyTheWarmupColumnAtZero` (e o `migrationsProduceTheSameDatabaseAsBefore` com a normalização da tabela "Destino").
  - `IosDatabaseTest.v5DatabaseMigratesToSixOnTheBundledDriver` (cria o v5 por `SchemaV5.statements` com o `BundledSQLiteDriver`, insere uma rotina com duas séries, abre por `openWorkoutDatabase`, confere `user_version` 6 e `warmup = false`).
  - `BackupCodecTest`: `routineSetWarmupRoundTrips`; `fileWithoutWarmupReadsAsWorkingSets` (um JSON de hoje, sem o campo); `olderReaderIgnoresWarmup` (o JSON novo lido pelo `Json` do codec numa classe de teste com a forma antiga de `RoutineSetRow`); `formatVersionIsUnchanged`. `ImportPlannerTest.plannedWarmupsImport`.
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :app:testDebugUnitTest --tests "com.mooscles.data.RoutineWarmupMigrationTest" --tests "com.mooscles.data.SchemaV5FixtureTest" :shared:testAndroidHostTest --tests "*BackupCodecTest*" --tests "*ImportPlannerTest*"` e ver falhar.
- [ ] **Step 3:** Implementar a coluna, a migração (só o `ALTER`), o registro em `buildMooscles()` e o campo do backup; acrescentar `MIGRATION_5_6` às três listas legadas; rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :app:assembleDebug` (o KSP do `:shared` exporta o schema nesse build, como no CI) e conferir que só o `6.json` apareceu em `shared/schemas`.
- [ ] **Step 4:** O comando do Step 2 verde, mais `--tests "com.mooscles.data.*"` do `:app` (inclui `MigrationEquivalenceTest`, `RoomPersistenceTest`, `BackupRepositoryTest`, as três listas legadas) e `:shared:compileCommonMainKotlinMetadata`; `git status shared/schemas` mostra só o `6.json` novo.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_IOS_TESTS`. `compat-reviewer` (schema, migração, backup; esperado `SEGURO`). Commit `shared: schema v6 with planned warm-up sets`.

> **Nota (2026-10-30, Task 1, pré-voo F1–F3, F14, F23 e F26):** os caminhos certos são `app/src/test/java/com/mooscles/data/MigrationEquivalenceTest.kt` e `shared/src/iosTest/kotlin/com/mooscles/data/IosDatabaseTest.kt`. O `sqlite_master` da instalação nova e o da migrada saíram iguais (o texto não difere pela ordem da coluna), então o `freshInstallAndMigrationReachTheSameSchema` o compara direto. A normalização do `MigrationEquivalenceTest` também troca o `identity_hash` (6.json → 5.json). A Task 1 mudou também `DriverTestSupport.kt` (`exportedSchemaStatements`, `exportedIdentityHash`), `WorkoutDatabaseFileTest`, `LegacyInstallOpenTest` (androidTest: `user_version` 6 e nenhum `warmup`, roda na Task 18), `RoutineFolderPersistenceTest` (lê `routine_sets` pelas colunas da v4), `BackupCodec.kt` (`json` interno), `BackupSampleFileTest` (`formatVersionStaysOneForAnOptionalField` no lugar de `formatVersionIsUnchanged`) e a contagem exata de testes de entrada do iOS no `ci.yml` (6 → 7). O banco v5 compartilhado traz também `SchemaV5.seed` e `SchemaV5.savedQueries`, e o teste iOS faz as mesmas conferências do Android. Detalhes nas notas da spec.

### Task 2: Comandos de rotina e pasta no repositório

**Revisão:** Opus completa + `compat-reviewer`.

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/mooscles/data/WorkoutRepository.kt`, `.../data/WorkoutDatabase.kt` (`WorkoutDao.deleteRoutineFolder`), `.github/workflows/ci.yml`
- Test: `app/src/test/java/com/mooscles/data/RoutineCommandsPersistenceTest.kt`

**Interfaces:**
- Consumes: `RoutineSet.warmup` (Task 1).
- Produces (todas numa `writeTransaction`, sem `catch` dentro):
  - `suspend fun createRoutine(name: String, folderId: String = RoutineFolder.DEFAULT_ID, id: String = newId()): String` (recusa id em branco ou existente com `INVALID_VALUE`);
  - `suspend fun duplicateRoutine(id: String, name: String? = null, newId: String = newId()): String` (com `name`, usa `EditRules.name(name)`; sem, o " (copy)" de hoje);
  - `suspend fun addRoutineExercises(routineId: String, exerciseIds: List<String>): List<String>` (na ordem dada, cada um como o `addRoutineExercise`; um id que não existe desfaz todos);
  - `suspend fun deleteRoutineFolder(id: String)` (decisão 4);
  - `suspend fun reorderRoutineFolders(ids: List<String>)` (os ids de todas as pastas, senão `INVALID_VALUE`);
  - `suspend fun reorderFolderRoutines(folderId: String, ids: List<String>)` (decisão 3; os ids exatos da pasta, senão `INVALID_VALUE`);
  - `suspend fun setRoutineSetWarmup(setId: String, warmup: Boolean)`;
  - `suspend fun restoreRoutineSet(entryId: String, position: Int, item: RoutineSet)` (decisão 5; `EditRules.routineSetCount(size + 1)` e `EditRules.target`; as seguintes descem uma posição; `mirrorPlan`);
  - mudanças: `addRoutineSet` copia a última; `inheritRoutineSet` produz `warmup = false`; `synchronizeRoutineTargets` filtra `!warmup`; `startRoutine` copia o `warmup` para `WorkoutSet`.

- [ ] **Step 1: Testes que falham** (`RoutineCommandsPersistenceTest`, Room real como os testes de `data/`): `createRoutineUsesTheGivenIdAndFolder`; `createRoutineRefusesAnExistingId`; `duplicateWithANameUsesItAndTheGivenId`; `duplicateCopiesWarmups`; `addRoutineExercisesKeepsTheOrderInOneTransaction`; `aMissingExerciseAddsNone`; `deletingAFolderMovesItsRoutinesToTheEndOfTheDefault`; `deletingAFolderKeepsTheActiveWorkoutsSource`; `theDefaultFolderCannotBeDeleted`; `deletingRenumbersTheFolders`; `reorderRoutineFoldersNeedsEveryId`; `reorderFolderRoutinesKeepsTheOtherFoldersSlots`; `reorderFolderRoutinesRefusesForeignIds`; `warmupMakesTheSetManualAndKeepsItsValues`; `inheritingAWarmupMakesAWorkingSet`; `synchronizationIgnoresWarmups` (uma linha `warmup` com `inheritPr` vinda de import não muda depois de `finishWorkout`); `addRoutineSetCopiesTheLastSet` (valores, `warmup`, `inheritPr`); `startRoutineCopiesWarmups`; `restoreRoutineSetPutsItBackAtItsPositionWithItsId`; `restoreRoutineSetTwiceRefusesTheSecond`; `restoreAfterTheEntryIsRemovedIsRefused`; `restoreRefusesTheFiftyFirstSet`.
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :app:testDebugUnitTest --tests "com.mooscles.data.RoutineCommandsPersistenceTest"` e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** O comando do Step 2 verde, mais `--tests "com.mooscles.data.*"` e `:shared:compileCommonMainKotlinMetadata`.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS`. `compat-reviewer` (`data/`; esperado `SEGURO`). Commit `shared: routine and folder commands for the new routines screens`.

> **Nota (2026-10-30, Task 2, pré-voo F9, F18 e F19 e o M1 da revisão da Task 1):** a 51ª série restaurada é recusada com `SET_LIMIT` (`EditRules.routineSetCount`, o mesmo texto do `addRoutineSet`), não com `RESTORE_UNAVAILABLE` como dizia a decisão 5; `RESTORE_UNAVAILABLE` fica para a entrada que sumiu e o id que já existe (o segundo desfazer). `replaceRoutineExercise` mantém os aquecimentos como aquecimentos (valores zerados, série manual) e só as séries de trabalho herdam o recorde do exercício novo. As colunas antigas de `routine_exercises` (`mirrorPlan`) espelham a primeira série de trabalho (sem nenhuma, a primeira linha). Decidido aqui, onde o plano não dizia: `setRoutineSetWarmup(id, false)` numa série que já é de trabalho não grava nada (uma série herdada continua herdada); `addRoutineSet` depois de uma série de trabalho herdada, e `restoreRoutineSet` de uma, dão uma série herdada com o recorde de agora, e não uma cópia dos valores (uma linha herdada pode estar velha até a próxima sincronização, porque apagar um treino não sincroniza); por isso o `RoutineSetPersistenceTest.genuineRecordBelowDeletedMaximumUpdatesExistingAndNewInheritedRows` passa a ter a série manual em primeiro (com ela por último, a série nova agora a copiaria); `duplicateRoutine` recusa um `newId` em branco ou existente com `INVALID_VALUE`, como o `createRoutine`; `reorderFolderRoutines` numa pasta que não existe e `addRoutineExercises` numa rotina que não existe são recusados com `FOLDER_NOT_FOUND` e `ROUTINE_NOT_FOUND`. Testes a mais na lista: `replaceRoutineExerciseKeepsWarmups` (F18) e `mirroredPlanColumnsFollowTheFirstWorkingSet` (F19). O `RecordingActions` com o `RoutineSetUi` inteiro (F15) e a regra de ocorrência do alvo (F16) são da Task 3.

### Task 3: Contrato, ViewModel, apresentação e erro por comando

**Revisão:** Opus completa + `compat-reviewer` (toca `Models.kt`).

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/mooscles/ui/UiContract.kt`, `.../ui/UiErrors.kt` (o assunto passa junto), `.../MoosclesViewModel.kt`, `.../WorkoutPresentation.kt`, `.../data/Models.kt` (`LoadConvention.fromLabelOrName`), `screens/src/commonMain/kotlin/com/mooscles/screens/format/CatalogLabels.kt` (usa o helper), `screens/src/uiTest/kotlin/com/mooscles/screens/RecordingActions.kt`, `app/compose-stability.conf`, `.github/workflows/ci.yml`
- Test: `shared/src/commonTest/kotlin/com/mooscles/WorkoutPresentationTest.kt` (testes novos), `shared/src/commonTest/.../ui/UiErrorsTest.kt` (testes novos), `app/src/test/java/com/mooscles/AppViewModelRoutinesHostTest.kt`

**Interfaces:**
- Consumes: Tasks 1 e 2.
- Produces (contrato, aditivo): `RoutineSetUi(id, reps = 0, weightKg = null, inherited = true, warmup = false)`; `RoutineUi(…, lastFinishedAt: Long? = null)`; `data class TargetSetUi(val reps: Int, val weightKg: Double?)`; `data class RoutineTargetUi(val working: List<TargetSetUi>, val warmups: Int)`; `WorkoutExerciseUi(…, val target: RoutineTargetUi? = null)` (último); `UiState(…, val errorSubject: String? = null)`.
- Produces (`MoosclesActions`): `createRoutine(name: String, folderId: String, id: String)` (sobrecarga; a de um parâmetro fica); `duplicateRoutine(id: String, copyName: String, newId: String)`; `addRoutineExercises(routineId: String, exerciseIds: List<String>)`; `reorderRoutineExercises(routineId: String, ids: List<String>)`; `reorderRoutines(folderId: String, ids: List<String>)`; `reorderRoutineFolders(ids: List<String>)`; `deleteRoutineFolder(id: String)`; `setRoutineSetWarmup(setId: String, warmup: Boolean)`; `restoreRoutineSet(entryId: String, position: Int, set: RoutineSetUi)`.
- Produces: `LoadConvention.fromLabelOrName(value: String): LoadConvention?`.
- Produces (`RecordingActions`, listas unidas por `|`): `"createRoutine:<name>,<folderId>,<id>"`, `"duplicateRoutine:<id>,<copyName>,<newId>"`, `"addRoutineExercises:<routineId>,<id1>|<id2>"`, `"reorderRoutineExercises:<routineId>,<ids>"`, `"reorderRoutines:<folderId>,<ids>"`, `"reorderRoutineFolders:<ids>"`, `"deleteRoutineFolder:<id>"`, `"setRoutineSetWarmup:<setId>,<warmup>"`, `"restoreRoutineSet:<entryId>,<position>,<setId>"`.
- VM: `act(subject: String? = null, block)`; o problema guarda o assunto (decisão 7). `deleteRoutineFolder` seleciona a padrão depois, se era a selecionada. `createRoutine(name, folderId, id)` usa a pasta dada (não a preferência).
- Apresentação: `lastFinishedAt` e `target` (decisões 8 e 22), preservando a identidade de `RoutineUi` e de `WorkoutExerciseUi` quando nada deles mudou.

- [ ] **Step 1: Testes que falham.**
  - `WorkoutPresentationTest`: `targetComesFromTheSourceRoutineWorkingSets` (aquecimentos contados à parte); `sameExerciseTwiceMatchesByOccurrence`; `replacedOrAddedEntryHasNoTarget`; `deletedRoutineDropsTheTarget`; `noSourceRoutineNoTarget`; `finishedWorkoutsHaveNoTarget`; `lastFinishedAtIsTheLatestFinishedWorkoutOfTheRoutine`; `routineIdentityKeptWhenOnlyAnotherRoutinesHistoryChanges`; `warmupFlowsToRoutineSetUi`.
  - `UiErrorsTest.subjectTravelsWithTheProblem`.
  - `AppViewModelRoutinesHostTest` (montado como o `AppViewModelFoldersDiscardHostTest`): `createRoutineUsesTheScreenIdAndFolder`; `duplicateUsesTheScreensNameAndId`; `addRoutineExercisesKeepsTheTapOrder`; `reorderRoutinesInAFolderKeepsTheOthers`; `deletingTheSelectedFolderSelectsTheDefault`; `deletingAnotherFolderKeepsTheSelection`; `errorSubjectNamesTheFailedCommand` (criar com id existente: `errorSubject == id`); `unrelatedErrorHasItsOwnSubject`; `editingTheSourceRoutineMidWorkoutChangesOnlyTheTarget` (as séries do treino não mudam); `restoreRoutineSetRefusedIsSetNotRestored`.
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :shared:testAndroidHostTest --tests "*WorkoutPresentationTest*" --tests "*UiErrorsTest*" :app:testDebugUnitTest --tests "com.mooscles.AppViewModelRoutinesHostTest"` e ver falhar.
- [ ] **Step 3:** Implementar. Acrescentar `com.mooscles.ui.TargetSetUi` e `com.mooscles.ui.RoutineTargetUi` ao `app/compose-stability.conf`.
- [ ] **Step 4:** O comando do Step 2 verde, mais `--tests "com.mooscles.AppViewModel*"`, `:screens:testAndroidHostTest` e a compilação dos testes de aparelho do `:screens` (o nome da task pelo `tasks --all`).
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_IOS_TESTS`. `compat-reviewer` (esperado `SEGURO`: nada gravado muda). Commit `shared: routine actions, target line and per-command error subject`.

> **Nota (2026-10-30, Task 3, pré-voo F10, F15 e F16, M3 e M4 da revisão da Task 2):** a decisão 7 muda num ponto: uma tela que espera um eco não o cancela por `errorCode != null && errorSubject == id`, e sim pela contagem nova `UiState.subjectFailures[id]`, que cresce a cada comando sobre o id que falha e nunca é zerada; o `errorSubject` é o do erro mostrado, e um segundo erro antes de a tela ver o primeiro o substituiria (F10, teste `aSecondFailureStillReleasesTheFirstWait`, a mais na lista). O `RecordingActions` grava o `RoutineSetUi` inteiro no `restoreRoutineSet` (`"restoreRoutineSet:<entryId>,<position>,RoutineSetUi(…)"`, como o `restoreSet`), e não só o id (F15), com o teste `ScreensHelpersTest.recordingActionsJoinsListsAndKeepsTheRestoredRoutineSet`. A regra de ocorrência vale também para uma entrada trocada (F16; nota na spec). `RoutineSetUi.inherited` é `inheritPr && !warmup`, e o resumo do `PlannedExerciseUi` (`reps`, `weightKg`) segue a primeira série de trabalho, como o `mirrorPlan`, para o resumo nunca mostrar o valor de um aquecimento como alvo (correção de 2026-10-30, revisão da Task 3: o motivo antes registrado, o editor antigo reenviar esses valores no `updateRoutineExercise`, não procede, porque a ação legada não tem chamador na UI; ela pode sair do contrato com as telas antigas, na Task 15). A apresentação mostra na pasta padrão uma rotina de uma pasta que não existe, e o `reorderFolderRoutines` da padrão a conta (M3, `defaultFolderReorderTakesRoutinesOfAMissingFolder` e `routineOfAMissingFolderIsPresentedInTheDefaultOne`); o `updateRoutineExercise` antigo não grava o alvo nos aquecimentos (M4, `legacyTargetEditLeavesWarmupsAlone`). O `routineReorderingAndIndependentTargetEditsInvalidateImmediately` deixa de esperar o mesmo `active`: a rotina editada é a de origem do treino, e a linha de alvo muda (as séries continuam os mesmos objetos).

### Task 4: Reordenação no design system (spike S2)

**Revisão:** Opus completa (gesto, concorrência com a lista).

**Files:**
- Create: `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/interaction/Reorder.kt`, `.../gallery/pages/ReorderPage.kt`, `docs/superpowers/reports/AAAA-MM-DD-phase4b-spike-reordenar.md`
- Modify: `.../tokens/MotionTokens.kt` (`dragLiftScale = 1.02f`), `.../tokens/SpaceTokens.kt` (`MoosclesSizes.reorderEdgeZone = 64.dp`, `MoosclesSizes.reorderScrollStep = 12.dp` por quadro), `.../components/ExerciseCard.kt` (`ExerciseCardHeader(…, handle: (@Composable () -> Unit)? = null, subline: AnnotatedString? = null)`; o `subline` serve à Task 8), a galeria (página nova), `strings_components.xml` e `strings_gallery.xml` (en e pt-BR), `designsystem/src/androidDeviceTest/.../TestPlatform.kt` e `designsystem/src/iosTest/.../TestPlatform.kt` (`INJECTS_DRAG = true` nos dois por ora), `.github/workflows/ci.yml`
- Test: `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/interaction/ReorderTest.kt`, `.../tokens/SizesTimingElevationTest.kt` e `MotionTokensTest` (testes novos), `.../components/ExerciseCardTest.kt` (teste novo), `GallerySmokeTest` (teste novo)

**Interfaces:**
- Produces: `@Stable class ReorderState<K : Any>` com `val order: List<K>` (a ordem mostrada: a dos `keys` fora de um arraste) e `val dragging: K?`; `@Composable fun <K : Any> rememberReorderState(listState: LazyListState, keys: List<K>, onDrop: (List<K>) -> Unit, onLift: (K) -> Unit = {}): ReorderState<K>`; `fun Modifier.reorderItem(state: ReorderState<*>, key: Any): Modifier` (no item da lista: deslocamento, `zIndex`, elevação `MTheme.elevation.e3` e escala `dragLiftScale` no item erguido, só a elevação com movimento reduzido; vizinhos com `springSnappy`, instantâneos com movimento reduzido); `fun Modifier.reorderHandle(state: ReorderState<*>, key: Any): Modifier` (`detectDragGesturesAfterLongPress`; háptico `LongPress` ao erguer e `StepperStep` a cada posição cruzada; rolagem automática na `reorderEdgeZone`); `fun <K : Any> Modifier.reorderActions(state: ReorderState<K>, key: K, name: String): Modifier` (ações "Mover para cima", "Mover para baixo", "Mover para o topo", "Mover para o fim", só as possíveis; cada uma chama `onDrop` com a ordem nova e anuncia `ds_reorder_moved`). `@Composable fun reorderHandleLabel(name: String): String`.
- Regras: `onDrop` uma vez, ao soltar, só se a ordem mudou; cancelar não grava e devolve o item; `keys` que mudam no meio de um arraste se reconciliam (ids que saíram somem, ids novos entram na posição que têm em `keys`) e o drop usa exatamente os `keys` atuais; o item arrastado que some encerra o arraste sem gravar; quando a altura dos itens muda no meio do arraste (decisão 14), o item arrastado fica sob o dedo.
- Strings (en / pt-BR): `ds_reorder_handle` "Reorder %1$s"/"Reordenar %1$s"; `ds_reorder_up` "Move up"/"Mover para cima"; `ds_reorder_down` "Move down"/"Mover para baixo"; `ds_reorder_top` "Move to top"/"Mover para o topo"; `ds_reorder_bottom` "Move to bottom"/"Mover para o fim"; `ds_reorder_moved` "%1$s moved to position %2$d of %3$d"/"%1$s agora na posição %2$d de %3$d" (decisão 18).

- [ ] **Step 1: Testes que falham.** `ReorderTest` (`uiTest`, uma `LazyColumn` com itens de três alturas): `dragAcrossItemsOfDifferentHeightsReordersWithoutJumps`; `dropWritesOnceWithTheNewOrder`; `nothingIsWrittenWhileDragging`; `cancelledGestureRestoresAndWritesNothing` (só onde `INJECTS_TOUCH_CANCEL`); `autoScrollsNearTheEdges`; `aFlingOnTheHandleScrollsWithoutLifting`; `accessibilityActionsMoveAndAnnounce` (as quatro; o primeiro não tem "cima" nem "topo"); `liftHasElevationAndScaleAndReducedMotionKeepsOnlyElevation`; `hapticOnLiftAndOnEachPositionCrossed` (`RecordingHaptics`); `worksInsideABottomSheet` (pelo `MOverlayWindow`; a sheet não desce durante o arraste); `itemsResizingOnLiftKeepTheDraggedItemUnderTheFinger`; `listChangeDuringDragDropsWithTheCurrentKeys`; `draggedItemThatDisappearsEndsTheDragWithoutAWrite`; `handleLabelNamesTheItem` (en e pt-BR). `SizesTimingElevationTest.reorderTokens`; `MotionTokensTest.dragLiftScale`; `ExerciseCardTest.headerShowsTheHandleAndTheSubline`; `GallerySmokeTest.reorderPageOpens`.
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :designsystem:testAndroidHostTest --tests "*SizesTimingElevationTest*" --tests "*MotionTokensTest*"` e, com o portão, `perl -e 'alarm shift; exec @ARGV' 1800 ./gradlew :designsystem:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.mooscles.designsystem.interaction.ReorderTest`; ver falhar.
- [ ] **Step 3:** Implementar sem biblioteca (D15). Nenhum literal de medida ou duração fora de `tokens/`.
- [ ] **Step 4:** Os comandos do Step 2 verdes, mais `ExerciseCardTest` e `GallerySmokeTest` no emulador; `pwsh tools/check-design-tokens.ps1` sai com 0; `:designsystem:compileCommonMainKotlinMetadata` verde.
- [ ] **Step 5: Relatório do spike** com os critérios da S2 (sem saltos, a rolagem não briga com o gesto, uma gravação, ações nas duas plataformas, dentro de uma sheet, redimensionamento ao erguer): o resultado do emulador agora; a linha do iOS fica "pendente do Push 1" e o controlador a preenche (decisão 2).
- [ ] **Step 6:** Subir `MIN_HOST_TESTS` e `MIN_DS_IOS_TESTS`. Commit `designsystem: drag-and-drop reordering for lazy lists`.

*Nota datada (2026-10-30, implementação da Task 4, com as decisões F7 e F8 da varredura prévia):*
- `reorderActions` passou a ser `@Composable fun <K : Any> Modifier.reorderActions(state, key, name)` (F8): os rótulos das quatro ações e o texto de `ds_reorder_moved` vêm de `stringResource`. Ela define as `customActions` do nó; um nó com outras ações próprias precisa listá-las junto.
- O anúncio precisa de um nó: `@Composable fun ReorderAnnouncer(state)` (região viva educada de 1 dp, como a do timer, vazia depois de `timing.timerAnnounceHold`) vai uma vez ao lado da lista. As Tasks 11, 12 e 13 o colocam.
- `@Composable fun ReorderHandle(state, key, name, modifier)`: a alça pronta (ícone novo `MIcons.GripVertical`, Lucide `grip-vertical`, num alvo de 48 dp, rotulada "Reordenar <nome>" como parada própria do leitor de tela). É ela que vai no `handle` do `ExerciseCardHeader`.
- Depois de soltar (ou de uma ação), `order` mostra a ordem gravada até os `keys` mudarem, no máximo `MoosclesTiming.reorderEchoHold = 2_000` ms (token novo, fixado em `SizesTimingElevationTest.reorderTokens`): sem isso o item voltaria ao lugar antigo durante o eco do VM; uma escrita que falha devolve os `keys` depois do prazo. Teste novo `ReorderTest.droppedOrderWaitsForTheKeys` (15 testes na classe).
- A chave de cada item na `LazyColumn` tem de ser o próprio `K`. `ReorderLiftElevation` (propriedade de semântica para teste, como `CardSegmentShape`) expõe a elevação do item erguido.
- Decisão 14: quando a vaga do item arrastado muda sozinha (cards recolhidos ao erguer, um id que entra ou sai), a lista rola para devolver a vaga para baixo do dedo e o que a rolagem não absorve sai do gesto, então o redimensionamento sozinho nunca reordena; o item desenhado fica sob o dedo (`grab` limitado à altura nova).
  *Nota (2026-10-30, investigação do arraste a 2x):* o que a rolagem não absorve deixava a vaga longe do item desenhado para sempre. No editor a 2x, com a lista rolada até a alça do terceiro card, recolher os cards trazia o fim da lista para a vista e a vaga ia centenas de dp para baixo do dedo, já na borda de cima: o card subia por cima do primeiro e ficava onde estava (nenhuma gravação). Agora, depois de o dedo andar duas distâncias de toque, a posição lógica volta para baixo do dedo e as trocas levam a vaga até o item desenhado (`ReorderState.rejoin`, teste `ReorderTest.aSlotTheScrollCannotBringBackFollowsTheFingerOnceItMoves`). O redimensionamento sozinho continua sem reordenar nada. Relatório: `.superpowers/sdd/2026-10-30-phase4b-routines/reorder-2x-investigation.md`.
  *Nota (2026-10-30, rodada de correção 1 da revisão):* a volta para baixo do dedo conta o deslocamento líquido desde que a vaga se afastou, como o armamento da rolagem automática, e não o caminho somado: um dedo parado que treme não reordena nem grava (`ReorderTest.aTremblingFingerDoesNotPullAFarSlotToIt`). Ela vale para qualquer vaga que um redimensionamento afastou sem rolagem que a devolva, não só no fim da lista. No caso mais comum, a lista no topo e um card do meio erguido, os cards de cima recolhem, a vaga sobe e o card continua desenhado sob o dedo, abaixo dela. Depois de duas distâncias de toque, a vaga desce até onde o card está desenhado, uma troca por layout, cada uma com o seu deslize e o seu háptico, mesmo com o dedo subindo, e o card arrastado não salta. Decisão: aceito, porque a queda fica onde a pessoa vê o card (`ReorderTest.aSlotLeftAboveTheFingerGoesToWhereTheItemIsDrawnOneSwapAtATime`).
  *Nota (2026-10-30, CI do iOS):* enquanto a vaga está afastada, nada troca. Antes, a posição lógica, contada a partir da vaga, ainda trocava. Com a distância de toque de 18 dp do iOS, o card do meio subia um lugar antes da volta para baixo do dedo e depois descia, desfazendo essa troca. Agora a cascata só anda num sentido e nenhuma troca é desfeita.
- O `check-design-tokens.ps1` passa a varrer só `interaction/Reorder.kt` de `interaction/` (F7), para não acusar o `MotionChoice.Spring(` do `MotionPolicy.kt`.
- *Rodada de correção 1 (2026-10-30, revisão da Task 4):* as quatro ações ficam na própria alça: `ReorderHandle<K>(state: ReorderState<K>, key: K, name, modifier)` aplica `reorderActions`, e a alça é a parada do leitor de tela ("Reordenar <nome>"), então as telas não põem `reorderActions` no item (num nó que não junta descendentes as ações ficam fora de alcance). No tema escuro, onde `elevation.3` não tem sombra, o item erguido ganha um contorno `border.strong` de `sizes.borderSelected` (3:1 sobre `bg.base`, `bg.surface` e `bg.overlay`, em `ComponentColorsTest`), também com movimento reduzido; `ReorderState.isLifted(key)` é público para o conteúdo usar `bg.overlay` se quiser. A troca exige passar do meio do vizinho por meia distância de toque (histerese), para um dedo tremendo no limite não trocar e destrocar. O KDoc de `ReorderState` diz que cada `K` é um único item da lista e que os itens reordenáveis são contíguos durante o arraste (na Task 13, só o cabeçalho de cada exercício fica como item enquanto arrasta, sem itens de altura 0).

### Task 5: Figura sem quadros na pausa

**Revisão:** Sonnet curta.

**Files:**
- Modify: `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/illustration/ExerciseFigure.kt`, `.github/workflows/ci.yml` (se o número de testes mudar)
- Test: `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/illustration/ExerciseFigureTest.kt` (teste novo)

**Interfaces:**
- Consumes: `AnimatedExerciseFigure` (4A), sem mudar a assinatura nem o `FigureFrameKey`.

- [ ] **Step 1: Teste que falha.** `ExerciseFigureTest.holdRequestsNoFrames`: com o relógio parado depois de compor, avança a pausa (900 ms menos o fade) quadro a quadro; o contador de desenho da camada da figura (probe de teste) não sobe; no fade, sobe.
- [ ] **Step 2:** Rodar a classe no emulador (com o portão) e ver falhar.
- [ ] **Step 3:** Trocar a transição infinita por um `LaunchedEffect` que espera a pausa (`delay` com o tempo do token) e anima só o fade (`Animatable`). Os testes atuais da classe continuam (alternância, movimento reduzido, segundo plano, fora da tela). Se o relógio do teste não governar o `delay` no simulador iOS (o CI do Push 1 mostra), volta a transição infinita e a pausa fica estacionada com nota datada.
- [ ] **Step 4:** A classe inteira verde no emulador. Medir: detalhe de um exercício do piloto aberto por 10 s, `adb shell dumpsys gfxinfo com.mooscles framestats` antes e depois da mudança; anotar os quadros por segundo durante a pausa em `…-phase4b-verificacao/figure-hold.md`.
- [ ] **Step 5:** Commit `designsystem: exercise figure requests no frames while it holds`.

> **Nota (2026-10-30, Task 5):** o Step 3 ficou com a espera e o fade dentro da `InfiniteAnimationPolicy` (`onInfiniteOperation` em volta do `delay` e do `Animatable.animateTo`), não num laço de `delay` solto (F5 do preflight): assim o relógio de teste continua parando a alternância com `autoAdvance` ligado. O Step 1 conta quadros pedidos (`BroadcastFrameClock.hasAwaiters` do recomposer), não desenhos: na transição infinita a opacidade não muda na pausa, então um contador de desenho não falharia antes da mudança. O Step 4 mediu com `atrace` (`Choreographer#doFrame`) numa atividade de teste do design system, não com `gfxinfo` no app: o `gfxinfo` só conta quadros desenhados. Detalhes em [figure-hold.md](../reports/2026-10-30-phase4b-verificacao/figure-hold.md).

### Task 6: Spike S4, foto no Android

**Revisão:** controlador (relatório).

**Files (branch `spike/phase4b-photo`, nunca mesclada):**
- Create: `app/src/debug/java/com/mooscles/spike/PhotoSpikeActivity.kt` (dois botões, galeria e câmera), `app/src/androidTest/java/com/mooscles/spike/PhotoPipelineSpikeTest.kt`
- Create (em `feat/phase4b-routines`): `docs/superpowers/reports/AAAA-MM-DD-phase4b-spike-foto.md`

**Interfaces:**
- Produces (para a Task 7): o pipeline escolhido por faixa de API (decodificação com amostragem, orientação, redimensionamento a 1080 px no lado maior, JPEG 85, miniatura quadrada de 192 px), a memória máxima medida, os tamanhos, o comportamento de `PickVisualMedia` e `TakePicture` sem permissão nas APIs testadas e a lista do que não foi provado.

- [ ] **Step 1: Imagens de emulador.** `sdkmanager --list | grep -E "system-images;android-(26|28);google_apis;arm64-v8a"`; se existirem, instalar e criar AVDs temporários no disco externo (`ANDROID_AVD_HOME`), um de cada vez, com outro serial. Sem imagem arm64 para uma API, ela fica "não verificada" no relatório (o código dela se prova no Robolectric na Task 7).
- [ ] **Step 2: Pipeline.** `PhotoPipelineSpikeTest` gera no próprio teste um JPEG de 4000 × 3000 com EXIF de orientação 6 e 8 e um PNG, processa pelos candidatos (`ImageDecoder` com `setTargetSize` na 28+; `BitmapFactory` com `inSampleSize` + `android.media.ExifInterface` e `Matrix` na 26–27) e imprime `SPIKE photo api=<n> path=<p> out=<w>x<h> bytes=<n> thumb=<n> peakNativeKb=<n> upright=<bool>`.
- [ ] **Step 3: Seletor e câmera.** Na `PhotoSpikeActivity`, por `adb`/mobile-mcp com a rede desligada: a galeria abre o seletor do sistema (ou o `ACTION_OPEN_DOCUMENT` de reserva) sem pedido de permissão; a câmera do emulador grava no arquivo do `FileProvider` (`cache/photo-capture/`) sem pedido de permissão; `aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk` sem `CAMERA`; com o app de câmera desativado (`pm disable-user`), `resolveActivity` volta nulo.
- [ ] **Step 4: Regra de decisão.** Aprova se a foto sai em pé e abaixo de 300 KB, sem `OutOfMemoryError` e sem permissão nova. Relatório com as medidas, a escolha e o que não foi provado (aparelho físico, One UI, outros fabricantes). Commit no spike `spike: Android photo pipeline`; commit do relatório em `feat/phase4b-routines`: `docs(report): phase 4B photo spike`. Apagar os AVDs temporários.
- [ ] **Step 5: Push 1** (checkpoint das Tasks 1–6): `git push origin feat/phase4b-routines` e `gh run watch -R pedroyuuki/Mooscles` sem bloquear. Com o `ios` verde, o controlador preenche a linha iOS do relatório do S2 (decisão 2) e o resultado da Task 5 no simulador.

### Task 7: Foto: interface comum e implementação Android

**Revisão:** Opus completa + `compat-reviewer` (manifesto, arquivos novos, serviços).

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mooscles/photos/ExercisePhotoStore.kt`, `app/src/main/java/com/mooscles/photos/AndroidExercisePhotoStore.kt`, `app/src/main/java/com/mooscles/photos/PhotoProcessing.kt`
- Modify: `shared/src/commonMain/kotlin/com/mooscles/MoosclesServices.kt` (`photos: ExercisePhotoStore = NoExercisePhotos`, último), `.../MoosclesViewModel.kt` (limpeza depois do `initialize()`), `app/src/main/java/com/mooscles/MoosclesApplication.kt` (`val photos by lazy`), `app/src/main/AndroidManifest.xml` (`<queries>`), `app/src/main/res/xml/workout_share_paths.xml` (caminho `photo_capture`), `.github/workflows/ci.yml`
- Test: `app/src/test/java/com/mooscles/photos/ExercisePhotoStoreHostTest.kt`, `app/src/test/java/com/mooscles/photos/PhotoManifestHostTest.kt`, `app/src/test/java/com/mooscles/AppViewModelPhotosHostTest.kt`

**Interfaces:**
- Consumes: a decisão da Task 6.
- Produces (`:shared`):

  ```kotlin
  class PhotoFile(val bytes: ByteArray, val version: Long)
  interface ExercisePhotoStore {
      /** Bumped after every change, so screens read again. */
      val revision: StateFlow<Long>
      /** Decodes [source] (a platform URI), orients, resizes and writes it as the draft [draftKey]; false if it cannot. */
      suspend fun stage(draftKey: String, source: String): Boolean
      suspend fun draft(draftKey: String, thumbnail: Boolean): PhotoFile?
      /** Moves the draft to [exerciseId] by rename, replacing an older photo; false if it cannot. */
      suspend fun commit(draftKey: String, exerciseId: String): Boolean
      suspend fun discard(draftKey: String)
      suspend fun read(exerciseId: String, thumbnail: Boolean): PhotoFile?
      suspend fun remove(exerciseId: String)
      /** Deletes photos whose id is not in [customExerciseIds] and drafts older than [draftMaxAgeMillis]. */
      suspend fun removeOrphans(customExerciseIds: Set<String>, draftMaxAgeMillis: Long = 86_400_000)
  }
  object NoExercisePhotos : ExercisePhotoStore  // every read null, every write false or no-op
  ```
- Produces (`:app`): `class AndroidExercisePhotoStore(context: Context, io: CoroutineDispatcher = Dispatchers.IO) : ExercisePhotoStore` (decisão 11; `version` = `lastModified`; grava num temporário e renomeia); `internal object PhotoProcessing { fun process(input: () -> InputStream, sdk: Int): ProcessedPhoto? }` com `class ProcessedPhoto(val full: ByteArray, val thumb: ByteArray)`; `const val PHOTO_CAPTURE_DIR = "photo-capture"`.
- VM: depois do `repo.initialize()`, `services.photos.removeOrphans(ids dos personalizados)`, com `CancellationException` relançada e qualquer outra falha ignorada (não é escrita do banco nem passa por transação).

- [ ] **Step 1: Testes que falham.** `ExercisePhotoStoreHostTest` (Robolectric, `@GraphicsMode(NATIVE)`, `@Config(sdk = [26, 35])`, imagens geradas no teste): `stageResizesTheLongSideTo1080AndWritesA192Thumb`; `exifRotationIsApplied` (orientação 6 sai em pé); `outputIsUnder300KB`; `undecodableSourceFailsWithoutFiles`; `commitReplacesAtomically` (a foto antiga fica inteira até o rename); `discardDeletesTheDraft`; `removeDeletesBoth`; `oldDraftsGoFreshDraftsStay`; `orphanPhotosGoCustomOnesStay`; `revisionBumpsOnEveryChange`. `PhotoManifestHostTest`: `noCameraPermission`; `captureQueryIsDeclared`; `capturePathIsShared` (o `FileProvider` dá URI para `cache/photo-capture/x.jpg`). `AppViewModelPhotosHostTest.orphanPhotosAreRemovedAfterInitialize`; `aFailingCleanupDoesNotBlockTheApp`.
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :app:testDebugUnitTest --tests "com.mooscles.photos.*" --tests "com.mooscles.AppViewModelPhotosHostTest"` e ver falhar.
- [ ] **Step 3:** Implementar pelo pipeline da Task 6.
- [ ] **Step 4:** O comando do Step 2 verde, mais `--tests "com.mooscles.AppViewModel*"`, `:app:lintDebug` e `:app:assembleDebug`; `aapt2 dump permissions` sem `CAMERA`.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS`. `compat-reviewer` (manifesto, `FileProvider`, `files/exercise_photos/`; esperado `SEGURO`). Commit `app: exercise photo store with an app-private resized copy`.

> **Nota (2026-10-30, Task 7, spike S4, pré-voo F4, F24, F25 e F27):** a decisão 11 mudou: o `ImageDecoder` da API 28 em diante virou `BitmapFactory` com `inSampleSize` em todas as APIs (orientação pelo `ExifInterface` do framework, lido do stream, e uma `Matrix`), com o `ImageDecoder` só como reserva na API 29+ quando o `BitmapFactory` devolve nulo; o motivo está nas medidas do spike. Um HEIC na API 28 sai deitado, risco aceito e item das notas de release. A limpeza das fotos órfãs (F4) só roda depois de um `initialize()` que deu certo, com os ids de uma leitura própria, em linha antes do primeiro comando; a falha dela é ignorada e o cancelamento é relançado. A `version` da foto é o `lastModified`, empurrado 1 s além do anterior se o novo não for mais novo, no mesmo tique ou com o relógio para trás (F24); `removeOrphans` também apaga temporários velhos. O cache do Robolectric no CI ganha o sufixo `-sdk26` (F25: o jar da sdk 26 tem 96 MB). O `androidx.exifinterface` da D17 não entrou: o `ExifInterface` do framework basta, sem dependência nova. Layout em disco, que muda a D17 e esta decisão 11: `files/exercise_photos/<id>.jpg` e `thumbs/<id>.jpg` (o sufixo `.thumb.jpg` cairia na regra das órfãs), e rascunhos em `files/exercise_photos/drafts/` em vez de `cache/exercise_photo_drafts/` (o sistema pode limpar o cache e o rascunho precisa sobreviver à morte do processo; a limpeza de 24 h os apaga). O `commit` renomeia a foto antes da miniatura e, se o segundo rename falha, devolve `false` com o par antigo e o rascunho intactos.

### Task 8: Linha de alvo: formato e card do treino

**Revisão:** Sonnet curta.

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/format/RoutineTargets.kt`, `screens/src/commonMain/composeResources/values/strings_routines.xml` e `values-pt-rBR/strings_routines.xml`
- Modify: `screens/.../workout/ExerciseItems.kt` (o `subline` do cabeçalho), `.github/workflows/ci.yml`
- Test: `screens/src/commonTest/kotlin/com/mooscles/screens/format/RoutineTargetFormatTest.kt`, `screens/src/uiTest/kotlin/com/mooscles/screens/format/RoutineTargetLineTest.kt`, testes novos em `ActiveWorkoutScreenTest`

**Interfaces:**
- Consumes: `RoutineTargetUi`, `TargetSetUi` (Task 3); `ExerciseCardHeader(subline)` (Task 4); `loadText`/`MoosclesNumbers`, `NumberLocale`.
- Produces: `internal class TargetTexts(val prefix: String, val sets: (Int) -> String, val reps: (String) -> String, val warmups: (Int) -> String, val unit: String)`; `internal data class TargetPart(val text: String, val number: Boolean)`; `internal object RoutineTargetFormat { fun parts(target: RoutineTargetUi, pounds: Boolean, locale: NumberLocale, texts: TargetTexts): List<TargetPart>? }`; `@Composable internal fun targetLine(target: RoutineTargetUi?, pounds: Boolean): AnnotatedString?` (texto em `caption`, os números em `num.sm`).
- Regras (D16), sobre as séries de trabalho, com reps 0 e carga nula como vazias e fora das faixas: todas com as mesmas reps r e a mesma carga L → "Alvo 3×8 · 80 kg"; mesmas reps, cargas diferentes → "Alvo 3×8 · 70–80 kg"; reps diferentes → "Alvo 3 séries · 6–10 reps · 70–80 kg"; cargas todas vazias → sem a parte da carga ("Alvo 3×12"); reps todas vazias → "Alvo 3 séries · 80 kg"; as duas vazias → "Alvo 3 séries"; faixa com mínimo igual ao máximo vira um valor; aquecimentos → sufixo " + 2 aquec."; só aquecimentos → "Alvo + 2 aquec."; nada → `null`. Carga pelo mesmo texto do treino (lb a partir do kg exato), números no formato do idioma, "×" (U+00D7) e "–" (U+2013).
- Strings (en / pt-BR): `routines_target_prefix` "Target"/"Alvo"; plural `routines_target_sets` "%1$d set(s)"/"%1$d série(s)"; `routines_target_reps` "%1$s reps"/"%1$s reps"; plural `routines_target_warmups` "+ %1$d warm-up"/"+ %1$d aquec." (as duas formas iguais nos dois idiomas, como a amostra da spec).

- [ ] **Step 1: Testes que falham.** `RoutineTargetFormatTest` (textos en e pt-BR passados à mão): `allEqual`; `sameRepsDifferentLoads`; `differentRepsAndLoads`; `blankLoads`; `blankReps`; `bothBlank`; `mixedBlankLoadsAreLeftOutOfTheRange`; `warmupsSuffix`; `warmupsOnly`; `nothingIsNull`; `poundsFromExactKg`; `numbersFollowTheLocale` ("72,5" e "72.5"); `numbersAreMarked` (só os números com `number = true`). `RoutineTargetLineTest.resourcesInBothLanguages`. `ActiveWorkoutScreenTest`: `targetLineSitsUnderTheMeta`; `noTargetWithoutASourceRoutine`; `targetLineWholeAtTwoX`.
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :screens:testAndroidHostTest --tests "*RoutineTargetFormatTest*" --tests "*StringsParityTest*"` e as duas classes de UI no emulador (com o portão); ver falhar.
- [ ] **Step 3:** Implementar e ligar no card do treino.
- [ ] **Step 4:** Os comandos do Step 2 verdes, mais `ExerciseListTest` no emulador.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS`. Commit `screens: routine target line on the workout card`.

### Task 9: Seletor com seleção múltipla

**Revisão:** Opus completa (comportamento novo).

**Files:**
- Modify: `screens/src/commonMain/kotlin/com/mooscles/screens/workout/ExercisePickerSheet.kt`, `.../library/LibraryRow.kt` (caixa de seleção opcional), `strings_workout.xml` (en e pt-BR), `.github/workflows/ci.yml`
- Test: `screens/src/uiTest/kotlin/com/mooscles/screens/workout/ExercisePickerSheetTest.kt` (testes novos)

**Interfaces:**
- Produces: `enum class PickerMode { Single, Multiple }`; `ExercisePickerSheet(…, mode: PickerMode = PickerMode.Single, onPickMany: (List<ExerciseUi>) -> Unit = {})`. Em `Multiple`: cada linha com caixa (`Role.Checkbox`, estado falado), a ordem dos toques guardada (desmarcar tira), a seleção em `rememberSaveable` (lista de ids) e mantida entre buscas e filtros, um exercício que some de `exercises` sai da seleção; embaixo, fixo, `MButton` `Primary` L "Adicionar 5 exercícios" (desligado com 0), que chama `onPickMany` uma vez e depois `onDismiss`.
- Strings: plural `workout_picker_add_count` "Add %1$d exercise(s)"/"Adicionar %1$d exercício(s)"; `workout_picker_selected` "Selected"/"Selecionado".

- [ ] **Step 1: Testes que falham.** `multipleShowsCheckboxesAndACountedAddButton`; `addKeepsTheTapOrder`; `untappingRemoves`; `selectionSurvivesSearchFilterAndRecreation`; `addTwiceCallsOnce`; `nothingSelectedDisablesAdd`; `vanishedExerciseLeavesTheSelection`; `addButtonWholeAtTwoX`. Os testes de seleção única ficam como estão.
- [ ] **Step 2:** Rodar a classe no emulador (com o portão) e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** O comando do Step 2 verde, mais `LibraryScreenTest` (a linha mudou).
- [ ] **Step 5:** Subir `MIN_SCREENS_IOS_TESTS`. Commit `screens: multi-select exercise picker`.

### Task 10: Foto nas telas e formulário aberto até salvar

**Revisão:** Opus completa (comportamento novo, espera por eco).

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/exercise/photo/ExercisePhotos.kt`, `.../exercise/photo/PhotoRow.kt`, `screens/src/uiTest/kotlin/com/mooscles/screens/exercise/FakeExercisePhotos.kt`
- Modify: `.../exercise/CustomExerciseForm.kt`, `.../library/LibraryScreen.kt` (a espera do id sai daqui), `.../library/LibraryRow.kt`, `.../exercise/ExerciseInfoScreen.kt` (mídia e a espera do "Adicionar à rotina" por assunto), `.../workout/ExercisePickerSheet.kt`, `.../workout/ExerciseItems.kt`, `strings_exercise.xml` (en e pt-BR), `.github/workflows/ci.yml`
- Test: `screens/src/uiTest/kotlin/com/mooscles/screens/exercise/CustomExerciseFormPhotoTest.kt`, testes novos e ajustados em `LibraryScreenTest`, `ExerciseInfoScreenTest`, `CustomExerciseFormTest`

**Interfaces:**
- Consumes: `ExercisePhotoStore`, `PhotoFile` (Task 7); `UiState.errorSubject` (Task 3); `ExerciseMedia.Image(painter)`.
- Produces: `interface PhotoCapture { val cameraAvailable: Boolean; @Composable fun rememberGalleryLauncher(onResult: (uri: String?) -> Unit): () -> Unit; @Composable fun rememberCameraLauncher(onResult: (uri: String?) -> Unit): () -> Unit }`; `class ExercisePhotos(val store: ExercisePhotoStore, val capture: PhotoCapture?)`; `val LocalExercisePhotos = staticCompositionLocalOf<ExercisePhotos?> { null }` (sem provedor, nenhuma linha "Foto" nem leitura); `@Composable fun rememberExerciseMedia(exercise: ExerciseUi, thumbnail: Boolean): ExerciseMedia` (personalizado com foto: `ExerciseMedia.Image` decodificado por `decodeToImageBitmap` em `Dispatchers.Default`, com cache em memória por id, tamanho e `version`, até 64 miniaturas e 4 fotos; senão o `exerciseMedia(exercise)` de hoje). A foto aparece recortada em 1:1 (D5): se o `ExerciseThumbnail` ainda não recorta o `ExerciseMedia.Image`, passa a `ContentScale.Crop`, com `ExerciseCardTest.imageIsCroppedSquare` (e o `MIN_DS_IOS_TESTS` sobe).
- Formulário (decisão 12): a linha "Foto" com a prévia, "Escolher da galeria", "Tirar foto" (só com `cameraAvailable`) e, havendo foto, "Remover foto"; o rascunho com a chave `"<id do formulário>"`, guardado em `rememberSaveable` junto com "remover no salvar"; "Cancelar" e Voltar descartam o rascunho.
- Strings (en / pt-BR): `exercise_form_photo` "Photo"/"Foto"; `exercise_form_photo_gallery` "Choose from gallery"/"Escolher da galeria"; `exercise_form_photo_camera` "Take photo"/"Tirar foto"; `exercise_form_photo_remove` "Remove photo"/"Remover foto"; `exercise_photo_failed` "Couldn’t save the photo."/"Não foi possível salvar a foto."; `exercise_photo_label` "Photo of %1$s"/"Foto de %1$s".

- [ ] **Step 1: Testes que falham.** `CustomExerciseFormPhotoTest` (com `FakeExercisePhotos`): `galleryPickShowsThePreview`; `cameraHiddenWithoutACameraApp`; `saveStaysOpenAndBusyUntilTheExerciseArrives`; `failedCreateKeepsTheFormOpenWithThePhoto` (erro com `errorSubject` igual ao id); `unrelatedErrorDoesNotStopTheWait`; `photoIsCommittedUnderTheNewIdBeforeSaved`; `photoFailureToastsAndKeepsTheExercise`; `cancelDiscardsTheDraft`; `photoDraftSurvivesRecreation`; `removeOnEditAppliesOnlyOnSave`; `photoRowWholeAtTwoX`. `LibraryScreenTest`: `creatingOpensTheNewExerciseWhenItArrives` (sem quadro da biblioteca entre o formulário e o detalhe); `creationErrorDropsThePendingOpen` (agora: o formulário continua aberto); `rowShowsTheCustomPhotoThumbnail`. `ExerciseInfoScreenTest`: `customWithPhotoShowsThePhotoWithoutAlternation`; `unrelatedErrorKeepsThePendingAddToast`.
- [ ] **Step 2:** Rodar as classes no emulador (com o portão) e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** O comando do Step 2 verde, mais `CustomExerciseFormTest`, `ExercisePickerSheetTest` e `ActiveWorkoutScreenTest`; `StringsParityTest` verde; `check-design-tokens` limpo.
- [ ] **Step 5:** Subir `MIN_SCREENS_IOS_TESTS`. Commit `screens: custom exercise photo and a form that waits for the save`.

**Notas datadas da Task 10 (2026-10-30):** (1) F17: a decisão 19 e a linha 600 ("as barras cinzas … continuam estacionadas") ficam substituídas pelo gancho `LocalOverlayWindowSetup`, sem novo `expect/actual`. (2) `PendingAdd` do detalhe não é mais salvo, porque a contagem de falhas restaurada nunca liberaria a espera. (3) Editar espera o eco como criar. (4) Cancelar e Voltar são ignorados com o formulário ocupado. (5) O aviso de foto não salva aparece no detalhe (`ExercisePhotos.failedCommits`). (6) Token `saveEchoWait` = 15 000 ms. As decisões e o texto completo estão nas notas datadas da 4B da spec.

### Task 11: Aba Treinar

**Revisão:** Opus completa (tela nova com comportamento).

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/routines/TrainScreen.kt`, `.../routines/RoutineCard.kt`, `.../routines/RoutineSummaries.kt`, `.../routines/RoutineSheets.kt` (mover para pasta, excluir, conflito com o treino ativo)
- Modify: `.../workout/RenameSheet.kt` (decisão 15), `designsystem/.../tokens/SpaceTokens.kt` (`MoosclesSizes.mediaStrip = 40.dp`), `strings_routines.xml` (en e pt-BR), `.github/workflows/ci.yml`
- Create: `screens/src/androidDeviceTest/kotlin/com/mooscles/screens/TestPlatform.android.kt` (`internal const val INJECTS_DRAG = true`) e `screens/src/iosTest/kotlin/com/mooscles/screens/TestPlatform.ios.kt` (o valor que o Push 1 provou, decisão 2)
- Test: `screens/src/commonTest/kotlin/com/mooscles/screens/routines/RoutineSummariesTest.kt`, `screens/src/uiTest/kotlin/com/mooscles/screens/routines/TrainScreenTest.kt`, `designsystem/.../tokens/SizesTimingElevationTest.kt` (`mediaStripIsForty`)

**Interfaces:**
- Consumes: Task 3 (ações, `lastFinishedAt`, `errorSubject`); Task 4 (`rememberReorderState`, `reorderItem`, `reorderHandle`, `reorderActions`); Task 10 (`rememberExerciseMedia`); `folderName`, `exerciseName`, `muscleLabel`, `shortDate`, `ScreenErrorToast` (4A e fase 3).
- Produces: `const val TrainScreenTag = "routines-list"`; `@Composable fun TrainRoute(state: UiState, actions: MoosclesActions, onOpenRoutine: (String) -> Unit, onOpenWorkout: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier, now: () -> Long = ::currentTimeMillis)`. A Task 12 acrescenta o gerenciador de pastas dentro dele.
- Produces: `internal object RoutineSummaries { fun exerciseCount(r: RoutineUi): Int; fun setCount(r: RoutineUi): Int; fun topMuscles(r: RoutineUi, max: Int = 3): List<String>` (por frequência, empate pela ordem do enum)`; fun lastDone(at: Long?, now: Long, zone: TimeZone): LastDone }` com `sealed interface LastDone { Never; Today; Yesterday; DaysAgo(n: Int); On(epochMillis: Long) }` (até 30 dias, `DaysAgo`); `RenameSheet(name, onRename, onDismiss, title = …, confirmLabel = …, fieldLabel = …, selectAll = false)`.
- Comportamento ("Treinar (aba)" da spec, mais as decisões 9, 10 e 17): chips de pasta na ordem gravada com a selecionada destacada (`selectRoutineFolder`), "Pastas" no fim; cards na ordem gravada, com nome e resumo num nó, "Começar"/"Retomar treino" e o menu (Editar, Renomear, Duplicar, Mover para pasta, Mover para cima/baixo/topo/fim, Excluir) como botões próprios; a faixa com as miniaturas dos 4 primeiros (`MTheme.sizes.mediaStrip`, token novo de 40 dp, com teste); arrastar pela alça grava `reorderRoutines(folderId, ids)`; "Nova rotina" abre a `RenameSheet` com `routines_new_name` selecionado e o teclado aberto, cria com `createRoutine(nome, pasta, newId())` e abre o editor no eco (erro com o assunto descarta a espera); "Treino vazio" chama `startWorkout(null)` e `onOpenWorkout`; pasta vazia: `EmptyState` com "Nova rotina"; carregando: `Skeleton` de cards; o "Começar" passa pelo `actions` hospedado (o decorador de skeleton da fase 3).
- Tags: `routines-list`, `routine-folder-selector`, `routine-<id>-start`, `routine-<id>-menu`, `routine-<id>-handle`, `train-new-routine`, `train-empty-workout`, `train-folders`, `train-settings`.
- Strings (en / pt-BR): `routines_title` "Train"/"Treinar"; `routines_settings` "Settings"/"Configurações"; `routines_folders` "Folders"/"Pastas"; plurais `routines_exercise_count` "%1$d exercise(s)"/"%1$d exercício(s)" e `routines_set_count` "%1$d set(s)"/"%1$d série(s)"; `routines_last_today` "Last done: today"/"Última vez: hoje"; `routines_last_yesterday` "Last done: yesterday"/"Última vez: ontem"; plural `routines_last_days` "Last done: %1$d day(s) ago"/"Última vez: há %1$d dia(s)"; `routines_last_date` "Last done: %1$s"/"Última vez: %1$s"; `routines_never` "Never done"/"Nunca feita"; `routines_start` "Start"/"Começar"; `routines_resume` "Resume workout"/"Retomar treino"; `routines_more` "Options for %1$s"/"Opções de %1$s"; `routines_edit` "Edit"/"Editar"; `routines_rename` "Rename"/"Renomear"; `routines_duplicate` "Duplicate"/"Duplicar"; `routines_copy_name` "%1$s (copy)"/"%1$s (cópia)"; `routines_duplicated` "Routine duplicated"/"Rotina duplicada"; `routines_open` "Open"/"Abrir"; `routines_move_to_folder` "Move to folder"/"Mover para pasta"; `routines_current_folder` "Current folder"/"Pasta atual"; `routines_delete` "Delete"/"Excluir"; `routines_delete_title` "Delete %1$s?"/"Excluir %1$s?"; `routines_delete_body` "Workouts already done with it stay in your history."/"Os treinos já feitos com ela continuam no histórico."; `routines_new` "New routine"/"Nova rotina"; `routines_new_name` "New routine"/"Nova rotina"; `routines_create` "Create"/"Criar"; `routines_name_field` "Routine name"/"Nome da rotina"; `routines_empty_workout` "Empty workout"/"Treino vazio"; `routines_empty_folder` "No routines in this folder"/"Nenhuma rotina nesta pasta"; `routines_active_title` "A workout is in progress"/"Já existe um treino em andamento"; `routines_active_body` "Finish or discard “%1$s” before starting another."/"Termine ou descarte “%1$s” antes de começar outro."; `routines_cancel` "Cancel"/"Cancelar".

- [ ] **Step 1: Testes que falham.**
  - `RoutineSummariesTest`: `countsExercisesAndPlannedSets`; `topThreeMusclesByFrequencyThenEnumOrder`; `lastDoneBuckets` (hoje, ontem, 3 dias, 30 dias, 31 dias → data, nunca).
  - `TrainScreenTest`: `foldersAreChipsInSavedOrderWithTheSelectedHighlighted`; `selectingAFolderCallsSelect`; `cardIsOneNodeWithNameAndSummaryPlusStartAndMenu`; `cardShowsCountsMusclesLastDoneAndFourThumbnails`; `startStartsAndOpensTheWorkout`; `startWhileAnotherWorkoutIsActiveOpensTheDialog`; `sourceRoutineCardResumes`; `menuRenameDuplicateMoveDelete` (duplicar manda o nome com "(cópia)" e o toast vem no eco); `deleteDialogSaysHistoryStays`; `moveActionsReorderInsideTheFolder`; `dragReordersInsideTheFolder`; `newRoutineCreatesInTheSelectedFolderAndOpensOnTheEcho`; `newRoutineErrorDropsTheWait`; `emptyWorkoutStartsAnEmptyWorkout`; `emptyFolderOffersNewRoutine`; `loadingShowsSkeletonCards`; `defaultFolderIsTranslated`; `startIsFiftySixAndTextsWholeAtTwoX`; `errorShowsTheLocalizedToast`; os portados `switchingFoldersFiltersCardsWithoutChangingActiveRoutineAssociation`, `movingAnActiveRoutinesCardUsesItsIdAndKeepsTheSession`, `identicalNamesUseOnlyTheMatchingPersistentIdAndResumeWithoutStarting`, `renamingAndReorderingDoNotMoveTheResumeAssociation`, `startingAnotherRoutineProtectsTheExistingSessionAndOffersExplicitResume`, `freeOrLegacyUnassociatedSessionNeverMarksACardAsResumable`, `finishingAndDiscardingRefreshBothCardsImmediately`, `noActiveSessionStartsTheSelectedRoutine`.
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :screens:testAndroidHostTest --tests "*RoutineSummariesTest*"` e `TrainScreenTest` no emulador (com o portão), mais `ActiveWorkoutScreenTest` (a `RenameSheet` mudou); ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Os comandos do Step 2 verdes; `StringsParityTest`; `check-design-tokens` limpo.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS` (e `MIN_DS_IOS_TESTS` pelo token). Commit `screens: Train tab with folders, routine cards and reordering`.

> **Nota (2026-10-30, Task 11, pré-voo F21, F6 e F10):** a faixa de miniaturas do card usa 56 dp, não 40 dp (D5 da spec: a mesma mídia de 56 dp da biblioteca); o token `MoosclesSizes.mediaStrip` vale `mediaList` (56 dp) e o teste é `mediaStripIsFiftySix`, no lugar de `mediaStripIsForty` (F21). A `RenameSheet` ganha também `submitUnchanged` (F6): "Nova rotina" cria mesmo com o nome padrão intocado (`newRoutineWithTheDefaultNameCreates`). A espera do eco de "Nova rotina" e da duplicação não vai para o `rememberSaveable`, solta pela chegada da rotina, por `subjectFailures[id]` acima da base do envio ou por `saveEchoWait`, e o toast sai de um escopo que sobrevive ao efeito da espera (F10). `INJECTS_DRAG = true` nos dois `TestPlatform` do `:screens` (o Push 1 provou o arraste injetado no simulador). Textos a mais que o brief: `routines_summary` ("%1$s · %2$s") e `routines_empty_folder_body`. "Treino vazio" com um treino em andamento mostra "Retomar treino" e só abre o treino (o comportamento da tela antiga; a spec não diz nada), nunca chama `startWorkout`. "Pastas" não faz nada visível até a Task 12. Rodada de correção 1 (revisão): os itens do menu do card e da sheet "Mover para pasta" rolam (`verticalScroll`); o foco inicial da `RenameSheet` vem do novo `MBottomSheet(initialFocus)`, pedido uma vez; os dois "Nova rotina" e o "Duplicar" ficam ocupados enquanto uma espera de eco corre; os movimentos do menu passam pelo `ReorderState` (`reorderMover`: mesma retenção e mesmo anúncio do arraste); o diálogo de treino em andamento guarda o id do treino e sobrevive à recriação; o nome da cópia é cortado antes do sufixo (`NameRule.MAX_LENGTH`, sem partir par substituto). Ícones novos (M7) ficam para depois: o `gen.py` precisa do pacote npm `lucide-static@1.63.0`, que não está no repositório.

### Task 12: Gerenciar pastas

**Revisão:** Opus completa (exclusão, reordenação dentro de sheet).

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/routines/FolderManagerSheet.kt`
- Modify: `.../routines/TrainScreen.kt` ("Pastas" abre o gerenciador), `strings_routines.xml` (en e pt-BR), `.github/workflows/ci.yml`
- Test: `screens/src/uiTest/kotlin/com/mooscles/screens/routines/FolderManagerSheetTest.kt`

**Interfaces:**
- Consumes: Task 4, Task 11 (`RenameSheet` generalizada), Task 3 (`reorderRoutineFolders`, `deleteRoutineFolder`, `createRoutineFolder`, `renameRoutineFolder`).
- Produces: `@Composable internal fun FolderManagerSheet(state: UiState, actions: MoosclesActions, onDismiss: () -> Unit)`: `MBottomSheet` com as pastas na ordem, cada linha com a alça, o nome (`folderName`), a contagem de rotinas e o menu (Renomear; Excluir, que não existe na padrão); "Nova pasta" no fim; o rótulo da alça e as quatro ações em cada linha; o estado (sheet aberta, diálogo, renomeando) sobrevive à recriação.
- Strings (en / pt-BR): `folders_title` "Folders"/"Pastas"; plural `folders_routine_count` "%1$d routine(s)"/"%1$d rotina(s)"; `folders_new` "New folder"/"Nova pasta"; `folders_name_field` "Folder name"/"Nome da pasta"; `folders_more` "Options for %1$s"/"Opções de %1$s"; `folders_rename` "Rename"/"Renomear"; `folders_delete` "Delete"/"Excluir"; `folders_delete_title` "Delete the %1$s folder?"/"Excluir a pasta %1$s?"; plural `folders_delete_body` one "Its routine moves to %2$s." / "A rotina dela vai para %2$s.", other "Its %1$d routines move to %2$s." / "As %1$d rotinas dela vão para %2$s." (sem rotinas, o diálogo só tem o título).

- [ ] **Step 1: Testes que falham.** `rowsInSavedOrderWithCountsAndHandles`; `defaultHasNoDelete`; `deleteDialogNamesWhereTheRoutinesGo` (com 3, 1 e 0 rotinas, en e pt-BR); `confirmDeleteCallsDeleteOnce`; `renameIncludingTheDefault`; `newFolderCreates`; `dragReordersTheFolders`; `accessibilityActionsReorder`; `sheetStateSurvivesRecreation`; `textsWholeAtTwoX` (a sheet a 2× pelo `MOverlayWindow`).
- [ ] **Step 2:** Rodar a classe no emulador (com o portão) e ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** O comando do Step 2 verde, mais `TrainScreenTest`.
- [ ] **Step 5:** Subir `MIN_SCREENS_IOS_TESTS`. Commit `screens: manage folders`.

### Task 13: Editor de rotina: lista, exercícios e ações

**Revisão:** Opus completa (tela nova com comportamento, arraste).

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/routines/RoutineEditorScreen.kt`, `.../routines/RoutineExerciseItems.kt`
- Modify: `.../workout/RestSheet.kt` (decisão 15), `strings_routines.xml` (en e pt-BR), `.github/workflows/ci.yml`
- Test: `screens/src/uiTest/kotlin/com/mooscles/screens/routines/RoutineEditorScreenTest.kt`

**Interfaces:**
- Consumes: Tasks 3, 4 (com a decisão 14), 8 (`targetLine`), 9 (`PickerMode.Multiple`), 10 (`rememberExerciseMedia`), 11 (`RenameSheet`, `RoutineSheets`, o conflito com o treino ativo).
- Produces: `const val RoutineEditorTag = "routine-editor-list"`; `@Composable fun RoutineEditorRoute(routineId: String, state: UiState, actions: MoosclesActions, onBack: () -> Unit, onGone: () -> Unit, onOpenWorkout: () -> Unit, onExerciseInfo: (exerciseId: String) -> Unit, modifier: Modifier = Modifier)`.
- Produces (para a Task 14): `internal fun LazyListScope.routineExerciseItems(…)` com um item de cabeçalho, um por série e um rodapé por exercício; nesta task cada série é uma linha só de leitura (rótulo, carga, repetições), trocada pela editável na Task 14.
- Comportamento ("Editor de rotina" da spec, decisões 13 e 14): o nome como primeira linha (tocar renomeia); barra com voltar e o menu (Renomear, Duplicar, Mover para pasta, Excluir rotina, que volta a Treinar depois do eco da exclusão); cabeçalho do exercício com alça (48 dp), mídia de 56 dp, nome, "Peito · Barra" e o menu (Trocar exercício, Sobre o exercício, Mover para cima, para baixo, para o topo, para o fim, Remover da rotina); linha de apoio com o chip "Descanso 2:00" (abre a `RestSheet` sem "Começar descanso agora", grava `updateRoutineExerciseRest`) e a linha de alvo; arrastar grava `reorderRoutineExercises` ao soltar; "Adicionar exercícios" abre o seletor múltiplo e chama `addRoutineExercises`; trocar pede confirmação só com alvo manual; remover pede confirmação; "Começar treino" fixo (`Primary` L, 56 dp) com as regras do card (decisão 9); rotina vazia: `EmptyState` com "Adicionar exercícios"; rotina que sumiu: `onGone()` uma vez.
- Tags: `routine-editor-list`, `routine-editor-start`, `routine-editor-name`, `routine-editor-menu`, `routine-exercise-<entryId>-handle`, `routine-exercise-<entryId>-menu`, `routine-exercise-<entryId>-rest`, `routine-editor-add`.
- Strings (en / pt-BR): `editor_more` "Routine options"/"Opções da rotina"; `editor_add_exercises` "Add exercises"/"Adicionar exercícios"; `editor_start` "Start workout"/"Começar treino"; `editor_rest_chip` "Rest %1$s"/"Descanso %1$s"; `editor_empty` "Routine without exercises"/"Rotina sem exercícios"; `editor_replace` "Replace exercise"/"Trocar exercício"; `editor_about` "About the exercise"/"Sobre o exercício"; `editor_remove_exercise` "Remove from routine"/"Remover da rotina"; `editor_remove_title` "Remove %1$s from the routine?"/"Remover %1$s da rotina?"; plural `editor_remove_body` one "Its planned set goes."/"A série planejada sai.", other "Its %1$d planned sets go."/"As %1$d séries planejadas saem."; `editor_replace_title` "Replace the exercise?"/"Trocar o exercício?"; `editor_replace_body` "Its manual targets go back to following your record."/"Os alvos manuais dele voltam a seguir o recorde."; `editor_exercise_more` "Options for %1$s"/"Opções de %1$s".

- [ ] **Step 1: Testes que falham** (`RoutineEditorScreenTest`): `nameIsTheFirstContentLineAndRenames`; `topMenuRenameDuplicateMoveDelete`; `deletingReturnsToTrainOnTheEcho`; `goneRoutineCallsOnGoneOnce`; `headerHasHandleMediaNameMetaAndMenu`; `restChipOpensTheRestSheetAndSavesOnlyThatEntry` (portado; sem "Começar descanso agora"); `targetLineInTheSupportRow`; `dragCollapsesCardsAndReordersOnDrop` (uma chamada `reorderRoutineExercises`); `exerciseRemovedElsewhereDuringADragDropsWithoutAnError`; `menuMovesUpDownTopBottom`; `addExercisesUsesTheMultiPickerInTapOrder`; `replaceAsksOnlyWithManualTargets`; `removeAsksAndNamesThePlannedSets`; `aboutOpensTheExerciseInfo`; `startStartsAndOpensTheWorkout`; `editorAlsoProtectsAnUnrelatedActiveSession` (portado); `emptyRoutineOffersAddExercises`; `startIsFiftySixAndTextsWholeAtTwoX`.
- [ ] **Step 2:** Rodar a classe no emulador (com o portão), mais `RestSheetTest` (a sheet mudou de forma); ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** O comando do Step 2 verde; `StringsParityTest`; `check-design-tokens` limpo.
- [ ] **Step 5:** Subir `MIN_SCREENS_IOS_TESTS`. Commit `screens: routine editor with drag-and-drop and multi-select add`.

**Notas datadas (2026-10-30, Task 13):** a linha de alvo fica na `subline` do cabeçalho, como no card do treino (D16); a mídia do cabeçalho mantém o tamanho do `ExerciseCardHeader` compartilhado, igual ao card do treino, e não os 56 dp da linha 296 da spec; os textos moram em `strings_editor.xml`, e as chaves do rótulo curto da série de aquecimento são `editor_set_warmup_short` ("W"/"A"), deixando `editor_set_warmup` para a Task 14; o toast da cópia tem "Abrir" por `onOpenRoutine: ((String) -> Unit)? = null` no `RoutineEditorRoute`, que a Task 15 liga; trocar pergunta também com aquecimento preenchido; a `RestSheet` ganhou o parâmetro `note`. `ExerciseCardHeader` ganhou `onToggle` nulo e `menuTag`/`menuLabel`.

### Task 14: Editor de rotina: séries planejadas

**Revisão:** Opus completa (edição, desfazer, rascunhos).

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/routines/PlannedSetRow.kt`, `.../routines/PlannedSetEditing.kt`, `.../routines/PlannedSetMenu.kt`
- Modify: `.../routines/RoutineExerciseItems.kt`, `.../routines/RoutineEditorScreen.kt`, `strings_routines.xml` (en e pt-BR), `.github/workflows/ci.yml`
- Test: `screens/src/commonTest/kotlin/com/mooscles/screens/routines/PlannedSetDraftsTest.kt`, `screens/src/uiTest/kotlin/com/mooscles/screens/routines/RoutineEditorSetsTest.kt`

**Interfaces:**
- Consumes: Task 13; `RoutineSetUi.warmup` e as ações de série (Task 3); `Stepper`, `NumericField`, `MToastHostState`, `undoTexts(number)` (fase 3); `loadStep`, `MAX_REPS`, `MAX_LOAD_KG`.
- Produces: `internal class PlannedSetDrafts` (decisão 16) com `fun shown(setId, field): String`, `fun edit(setId, field, text): PlannedSave?`, `fun step(setId, field, delta): PlannedSave?`, `fun acknowledge(saved: RoutineSetUi)` e `fun drop(setId)`, e `data class PlannedSave(val setId: String, val reps: Int, val weightKg: Double?)`; carga vazia é `null` (sem carga, ou peso corporal pela convenção), repetições vazias são 0, um rascunho inválido nunca é gravado, uma confirmação mais velha não troca um rascunho mais novo.
- Comportamento ("Editor de rotina" da spec, D14, decisão 6): linha com o rótulo (número das séries de trabalho; `editor_set_warmup_short` no aquecimento), carga e repetições em `NumericField`, e o ícone de recorde com o nome falado `editor_set_follows_record` quando `inherited`; o toque põe a linha em edição e mostra o `Stepper` do campo (2,5 kg ou 5 lb; 1 repetição), o segundo toque abre o teclado; editar deixa a série manual; o toque no rótulo abre o menu da série (Tipo da série: Normal ou Aquecimento; Seguir o recorde, só numa série de trabalho manual; Remover série, desligado na última com `editor_set_last`); remover mostra "Série 3 removida" com "Desfazer" (`toastAction` ou o tempo da acessibilidade), que chama `restoreRoutineSet`; uma recusa mostra o texto de `SET_NOT_RESTORED`; rodapé "Adicionar série" (`Tonal` L), desligado em 50 com `editor_set_limit`; a 200%, carga e repetições em duas linhas, como o `SetRow` da fase 3.
- Tags: `routine-set-<setId>`, `routine-set-<setId>-load`, `routine-set-<setId>-reps`, `routine-set-<setId>-label`, `routine-exercise-<entryId>-add-set`.
- Strings (en / pt-BR): `editor_set_warmup_short` "W"/"A"; `editor_set_follows_record` "follows the record"/"segue o recorde"; `editor_set_no_load` "No load"/"Sem carga"; `editor_set_bodyweight` "Bodyweight"/"Peso corporal"; `editor_set_type` "Set type"/"Tipo da série"; `editor_set_normal` "Normal"/"Normal"; `editor_set_warmup` "Warm-up"/"Aquecimento"; `editor_set_follow_record` "Follow the record"/"Seguir o recorde"; `editor_set_remove` "Remove set"/"Remover série"; `editor_set_last` "A routine needs at least one set per exercise."/"Uma rotina precisa de pelo menos uma série por exercício."; `editor_add_set` "Add set"/"Adicionar série"; `editor_set_limit` "Up to 50 sets per exercise."/"Até 50 séries por exercício."; `editor_set_label` "Set %1$d"/"Série %1$d"; `editor_set_warmup_label` "Warm-up set"/"Série de aquecimento".

- [ ] **Step 1: Testes que falham.**
  - `PlannedSetDraftsTest`, portados do `RoutineEditorHostTest`: `individualRowsShowReferenceValuesAndSaveOnlyTheEditedRow`; `blankRepetitionsAndInvalidWeightRemainEditableWithoutBlockingSiblingSaves`; `olderAutosaveAcknowledgementsCannotReplaceNewerDrafts`; `repositoryPrRefreshUpdatesBothValuesWithoutSavingDuringComposition`; `editingRepetitionsRetainsExactKilogramsBehindRoundedPoundsAndInvalidDraft`; `explicitlyClearedWeightSavesBodyweightAndPrInheritanceIsAnExplicitSeparateAction`; `replacingAnExerciseResetsDraftBuffersAndNeverAutosavesTheOldMovement`; `explicitPrDefaultAcknowledgementClearsAnInvalidDraftEvenWhenThePairIsUnchanged`; `delayedPrDefaultAcknowledgementCannotOverwriteAMoreRecentManualEdit`; mais `stepsUseTwoAndAHalfKgOrFiveLbAndOneRep`.
  - `RoutineEditorSetsTest`: `labelsNumberWorkingSetsAndMarkWarmups`; `inheritedSetShowsTheRecordIconWithItsSpokenName`; `blankValuesShowNoLoadOrBodyweightByConvention`; `firstTapShowsTheStepperSecondOpensTheKeyboard`; `stepperWritesOncePerRun`; `editedSetBecomesManual`; `setMenuMarksWarmupAndBack`; `followRecordOnlyOnManualWorkingSets`; `removeShowsUndoAndRestoreCallsRestoreWithTheSnapshot`; `undoAfterTheExerciseIsGoneShowsNotRestored`; `removingASetInEditDropsItsDraft`; `lastSetCannotBeRemovedAndSaysWhy`; `addSetCopiesTheOneAbove` (a ação; a cópia é do repositório); `fiftiethSetDisablesAddAndSaysWhy`; `addAndRemoveAreScopedToTheirExercise`; `largeFontPutsLoadAndRepsOnTwoLinesWithNumbersWhole`; `longNamesAndLaterSetControlsStayReachableWithTheKeyboard`.
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :screens:testAndroidHostTest --tests "*PlannedSetDraftsTest*"` e `RoutineEditorSetsTest` no emulador (com o portão); ver falhar.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Os comandos do Step 2 verdes, mais `RoutineEditorScreenTest`; `StringsParityTest`; `check-design-tokens` limpo.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS`. Commit `screens: planned sets with warm-up, record following and undo`.

### Task 15: O `:app` hospeda Treinar, o editor e a foto

**Revisão:** Opus completa (navegação, testes portados).

**Files:**
- Create: `app/src/main/java/com/mooscles/photos/AndroidPhotoCapture.kt`, `app/src/test/java/com/mooscles/RoutinesNavigationHostTest.kt`, `docs/superpowers/reports/AAAA-MM-DD-phase4b-verificacao/tap-baseline-4b.md`; temporário `app/src/test/java/com/mooscles/RoutinesTapBaselineHostTest.kt` (apagado no mesmo commit)
- Modify: `app/src/main/java/com/mooscles/ui/MoosclesApp.kt` (`ShellTabs.TRAIN` → `TrainRoute`; `routine/{id}` → `RoutineEditorRoute` com `onGone` voltando a Treinar; `OWN_PLACEHOLDER_ROUTES` com `ShellTabs.TRAIN` e `routine/{id}`; `onExerciseInfo` → `openExerciseInfo`), `app/src/main/java/com/mooscles/MainActivity.kt` (`CompositionLocalProvider(LocalExercisePhotos provides ExercisePhotos(app.photos, AndroidPhotoCapture(…)))`), `app/src/main/java/com/mooscles/ui/DataSection.kt` (a frase da D18 como legenda sob "Exportar"), `designsystem/src/commonMain/composeResources/values*/strings_settings.xml`, os testes da tabela "Destino", `.github/workflows/ci.yml`
- Delete: `app/src/main/java/com/mooscles/ui/RoutineScreens.kt`, `app/src/main/java/com/mooscles/ui/RoutineSetEditor.kt`, os auxiliares de `Common.kt` que ficarem sem uso (`grep` antes; o histórico usa `NameDialog`, `ConfirmDialog` e outros), `RoutineEditorHostTest.kt`, `RoutineEditorVisualHostTest.kt`, `RoutineFoldersHostTest.kt`, `RoutineResumeHostTest.kt`

**Interfaces:**
- Consumes: `TrainRoute` (Tasks 11 e 12), `RoutineEditorRoute` (Tasks 13 e 14), `PhotoCapture`, `ExercisePhotos`, `LocalExercisePhotos` (Task 10), `AndroidExercisePhotoStore` (Task 7).
- Produces: `class AndroidPhotoCapture(context: Context) : PhotoCapture` (`PickVisualMedia(ImageOnly)`; `TakePicture` num arquivo novo de `cache/photo-capture/` pelo `FileProvider`, apagado depois do `stage`; `cameraAvailable` por `resolveActivity` de `ACTION_IMAGE_CAPTURE`).
- Strings: `settings_data_export_photos_note` "Photos of custom exercises are not included in the file."/"As fotos dos exercícios personalizados não entram no arquivo."
- Produces (para as Tasks 17 e 18): a contagem de toques de hoje em `tap-baseline-4b.md`.

- [ ] **Step 1: Contagem de hoje** (antes de trocar as telas; pode rodar em paralelo com a Task 11). `RoutinesTapBaselineHostTest` (Robolectric, sobre as telas antigas, contando `performClick`, sem contar texto digitado): "montar uma rotina nova com 5 exercícios, da aba até o editor com os 5"; "reordenar: o 5º exercício para o 1º lugar"; "adicionar uma série planejada igual à de cima"; "começar o treino de uma rotina". Rodar com `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :app:testDebugUnitTest --tests "com.mooscles.RoutinesTapBaselineHostTest"` e registrar os números, com o commit.
- [ ] **Step 2: Testes que falham.** `RoutinesNavigationHostTest`: `trainToEditorAndBackKeepsTheSelectedFolderAndScroll`; `newRoutineOpensTheEditorOnItsEcho`; `deletedRoutineInTheEditorReturnsToTrain`; `editorAboutOpensTheDetailAndBackReturnsToTheEditor`; `startFromTheEditorOpensTheWorkout`; `globalSnackbarNeverShowsOnTrainOrEditor`; `reselectingTrainFromTheEditorReturnsToTheList`; `settingsShortcutOpensSettings`; `photosAreProvidedToTheScreens`. `DataSectionHostTest.exportShowsThePhotosNote`. Ajustar `UiFlowHostTest.routineToWorkoutRestSummaryAndHistory` às telas novas e os testes da casca que procuram textos antigos.
- [ ] **Step 3:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :app:testDebugUnitTest --tests "com.mooscles.RoutinesNavigationHostTest" --tests "com.mooscles.DataSectionHostTest" --tests "com.mooscles.UiFlowHostTest"` e ver falhar.
- [ ] **Step 4:** Implementar. Apagar as telas antigas, os quatro testes aposentados e o `RoutinesTapBaselineHostTest` no mesmo commit (motivo: as telas saíram; a lógica foi portada nas Tasks 11, 13 e 14; os números ficam no relatório). Ajustar `androidTest/UiFlowTest` e `UiFeatureSmokeTest` (só compilam aqui; rodam na Task 18).
- [ ] **Step 5: Verificar.** O comando do Step 3 verde, mais `:app:testDebugUnitTest` inteiro (o host do `:app` é a rede que pega as telas apagadas), `:app:lintDebug`, `:app:assembleDebug`, `:app:compileDebugAndroidTestKotlin`, `SettingsTextsHostTest` e `pwsh tools/check-apk-budget.ps1 -Apk app/build/outputs/apk/debug/app-debug.apk` (anotar o crescimento).
- [ ] **Step 6:** Ajustar `MIN_HOST_TESTS` (novos menos os aposentados, com a conta no commit). Commit `app: host the new Train tab, routine editor and photos`. **Push 2** (checkpoint das Tasks 7–15) e `gh run watch -R pedroyuuki/Mooscles` sem bloquear.

### Task 16: Fluxo de ponta a ponta das rotinas

**Revisão:** Sonnet curta. **Lote:** um agente só para as Tasks 16 e 17 (testes, mesma forma).

**Files:**
- Create: `screens/src/uiTest/kotlin/com/mooscles/screens/flow/RoutinesFlowTest.kt`
- Modify: `.github/workflows/ci.yml` (`MIN_SCREENS_IOS_TESTS`)

**Interfaces:**
- Consumes: `FlowHarness`, `FlowViewModel`, `openFlowDatabase`, `openFlowStore` (fase 3); `LibraryRoute`, `ExerciseInfoRoute`, `TrainRoute`, `RoutineEditorRoute`, `ActiveWorkoutRoute`; `FakeExercisePhotos` (Task 10).

- [ ] **Step 1: Teste.** `routinesFlowPortugueseLightAtOneX` e `routinesFlowEnglishDarkAtTwoX`, sobre o ViewModel, o Room (v6) e o DataStore reais, com um seletor de tela simples no teste: buscar "supino" (pt-BR) ou "bench" (en) na biblioteca, abrir o detalhe, "Adicionar à rotina" → "Treino A"; na aba Treinar, "Nova rotina" com o nome padrão; no editor, "Adicionar exercícios" com 3 marcados; reordenar (arraste onde `INJECTS_DRAG`, senão a ação "Mover para o topo"); marcar a primeira série de um exercício como aquecimento; "Começar treino" e ver a linha de alvo com "+ 1 aquec." no card. Em cada estado: todo clicável com rótulo, alvos ≥ 48 dp, `assertNumbersWhole()`; `onRoot().captureToImage()` no iOS em `screens/build/ios-captures/routines-<estado>.png` (se lançar, imprimir `FLOW capture unsupported` e seguir).
- [ ] **Step 2:** Rodar a classe no emulador até ficar verde (com o portão). Defeito revelado é corrigido na task dona, com um teste focado lá.
- [ ] **Step 3:** Subir `MIN_SCREENS_IOS_TESTS` (+2). Commit `screens: end-to-end routines flow over the real view model, Room and DataStore`.

### Task 17: Orçamento de toques e varredura de acessibilidade

**Revisão:** Sonnet curta (no lote da Task 16).

**Files:**
- Create: `screens/src/uiTest/kotlin/com/mooscles/screens/a11y/RoutinesAccessibilitySweepTest.kt`
- Modify: `screens/src/uiTest/kotlin/com/mooscles/screens/budget/TapBudgetTest.kt`, `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: os números de `tap-baseline-4b.md` (Task 15), copiados como literais com o link no comentário; `assertClickablesLabeledAndSized`, `assertButtonTextsWhole`, `assertNumbersWhole`, `screenReaderOrder`.

- [ ] **Step 1: Testes.**
  - `TapBudgetTest` (texto digitado não conta; cada um também ≤ a contagem de hoje): `buildARoutineWithFiveExercises` ≤ 9; `moveTheFifthExerciseToTheTop` ≤ 2 (menu e "Mover para o topo"); `addAPlannedSetLikeTheOneAbove` ≤ 1; `markAPlannedSetAsWarmup` ≤ 2; `startAWorkoutFromARoutine` ≤ 1.
  - `RoutinesAccessibilitySweepTest`: Treinar (com rotinas, pasta vazia, carregando), gerenciador de pastas, diálogo de excluir pasta, editor (com aquecimento, com uma série em edição, vazio) e o seletor múltiplo × claro e escuro × 1,0, 1,3 e 2,0 (mais 2,01 no Android) × 320 e 412 dp × topo, meio e fim da rolagem. Em cada combinação: rótulo em todo clicável, alvos de 48 dp (56 em "Começar" e "Começar treino"), `assertNumbersWhole()`, nenhum texto de botão cortado, as quatro ações de reordenação em cada item arrastável. Mais `trainReadingOrder` (topo, pastas, cards com nome e resumo num nó, "Começar" e menu depois) e `editorReadingOrder` (nome, cada exercício e as séries, "Adicionar exercícios", "Começar treino").
- [ ] **Step 2:** Rodar as duas classes no emulador até ficarem verdes (com o portão), corrigindo na task dona.
- [ ] **Step 3:** Subir `MIN_SCREENS_IOS_TESTS`. Commit `screens: routines tap budget and accessibility sweep`.

### Task 18: Suítes de aparelho, atualização por cima, sem rede, desempenho e tamanho

**Revisão:** controlador.

**Files:**
- Create: em `docs/superpowers/reports/AAAA-MM-DD-phase4b-verificacao/`, `device-suites.md`, `upgrade.md`, `offline.md`, `performance.md`, `apk.md`, `deps-diff.txt`
- Modify: o que as medidas pedirem, em commits próprios com teste

- [ ] **Step 1: Suítes completas, uma vez** (nenhuma outra lane usa o emulador nem o Gradle; portão antes): `perl -e 'alarm shift; exec @ARGV' 5400 ./gradlew :designsystem:connectedAndroidDeviceTest`, depois `:screens:connectedAndroidDeviceTest`, depois `:app:connectedDebugAndroidTest` (inclui `UiFlowTest`, `UiFeatureSmokeTest`, `LegacyInstallOpenTest`; desinstala o app no fim, por isso vem antes da atualização). Registrar os totais e as falhas corrigidas. Uma falha é corrigida na task dona, e só a classe dela roda de novo.
- [ ] **Step 2: Atualização por cima** (rede desligada): com o APK da `main` em `2359ab6` (depois da fase 3, banco v5), em pt-BR e em libras: duas pastas, três rotinas (uma com séries manuais, uma herdando o recorde), um exercício personalizado, um treino ativo a partir de uma rotina com séries concluídas e o descanso correndo; exportar; `md5` de `files/datastore/local_preferences.preferences_pb`; `adb install -r` do APK da branch. Conferir e registrar: Treinar e o editor com tudo igual; a linha de alvo no card do treino; o banco puxado (`adb exec-out run-as com.mooscles cat databases/mooscles.db` mais `-wal` e `-shm`, lido com `sqlite3` no Mac) com `PRAGMA user_version` 6 e `SELECT count(*) FROM routine_sets WHERE warmup != 0` igual a 0; o mesmo MD5; o export depois igual ao de antes, tirando `exportedAt` e o `"warmup": false` em cada série planejada (diff campo a campo com `jq`). Depois, a foto: escolher pela galeria e tirar pela câmera do emulador no personalizado, conferir `files/exercise_photos/` e que nenhum pedido de permissão apareceu.
- [ ] **Step 3: Sem rede:** instalação nova com a rede desligada desde antes: Treinar, pastas, editor, seletor múltiplo, foto e o treino com a linha de alvo funcionam inteiros. `git diff main...HEAD` sem código de rede novo (procurar `http`, `URL`, `HttpURLConnection`, `ktor`).
- [ ] **Step 4: Desempenho** pelo PERFORMANCE.md, no `assemblePerformance`, com a base grande: arrastar no editor com 10 exercícios × 5 séries (`adb shell input draganddrop` na alça, `gfxinfo` janky ≤ 5%; a gravação única já está provada pelo `RoutineEditorScreenTest.dragCollapsesCardsAndReordersOnDrop`, e aqui só se confere a ordem nova no banco depois de soltar); `present()` depois de reordenar e de editar uma série planejada contra a medida da 1B; as medidas do treino ativo da fase 3 (sem piora; a linha de alvo fora do caminho do check); rolagem da aba Treinar com 30 rotinas. Registrar o que ficou só no emulador.
- [ ] **Step 5: Tamanho e dependências:** `./gradlew :app:clean :app:assembleDebug` e `pwsh tools/check-apk-budget.ps1` (≤ 1.048.576 sobre a base; 4A e 4B separadas) em `apk.md`; `./gradlew :app:dependencies --configuration debugRuntimeClasspath` contra a `main` em `deps-diff.txt`, vazio.
- [ ] **Step 6:** Commit `docs(report): phase 4B device suites, upgrade, offline, performance and size`.

### Task 19: Capturas e crítica de tela

**Revisão:** controlador.

**Files:**
- Create: `docs/superpowers/reports/AAAA-MM-DD-phase4b-critica-rotinas.md` e as capturas citadas em `…-phase4b-verificacao/` (`train-{folder,empty}-…`, `folders-…`, `editor-{sets,warmup,dragging,empty}-…`, `picker-multi-…`, `form-photo-…`, `workout-target-…`, nos dois temas, em en e pt-BR, e a 200% em pt-BR)
- Modify: o que a crítica pedir, nos arquivos do `:screens` e do `:designsystem`, com teste quando a correção for de comportamento

- [ ] **Step 1: Capturas** com `adb exec-out screencap -p`, nos dois temas, em en e pt-BR (`adb shell cmd locale set-app-locales com.mooscles --locales pt-BR`) e a 200% (`adb shell settings put system font_scale 2.0`), mais as do iOS do `RoutinesFlowTest` (artefato do job `ios`).
- [ ] **Step 2: Crítica.** `critique-screen` (pelo `kit-design`) em Treinar, nas pastas, no editor (inclusive arrastando), no seletor múltiplo e no formulário com foto. Corrigir as de prioridade alta e média, com nova captura; as baixas ficam no relatório com o motivo.
- [ ] **Step 3: Verificar.** As classes de `uiTest` das telas corrigidas verdes no emulador; `check-design-tokens` limpo.
- [ ] **Step 4:** Commit `docs(report): phase 4B routines critique`, com as correções em commits próprios.

### Task 20: Documentação, relatório e revisão final

**Revisão:** Sonnet (documentos) + `compat-reviewer` na branch + `/code-review` + revisão final Opus da branch inteira.

**Files:**
- Create: `docs/superpowers/reports/AAAA-MM-DD-phase4b-verificacao.md`
- Modify: `AGENTS.md` (schema atual v6 e `MIGRATION_5_6` nas listas; `routines/` no `:screens`; `interaction/Reorder.kt` no design system; `com.mooscles.photos` no `:shared` e no `:app`, `files/exercise_photos/`, os rascunhos, o caminho `photo_capture` e o `<queries>`; `errorSubject`; as telas antigas que saíram), `VERIFICATION.md`, `docs/superpowers/ROADMAP.md` (fase 4: a 4B com plano, relatórios e status), `docs/superpowers/releases/v1.5.0.md` (item 5 de "Antes de abrir o PR": Room na v6 com o `6.json` e a migração aditiva), a spec (seção "Notas de implementação", datada, com as decisões 2, 3, 5, 7, 9, 11, 12, 13, 14, 17, 18 e 19 deste plano, a mudança de comparação do `MigrationEquivalenceTest`, e as decisões dos spikes S2 e S4)

- [ ] **Step 1: Relatório.** **Provado:** testes de host e de `commonTest` com os números do CI; o `ios` verde com o `RoutinesFlowTest`, a migração v5 → v6 no `BundledSQLiteDriver` e o resultado iOS do S2; as suítes de aparelho (Task 18); o orçamento de toques contra `tap-baseline-4b.md`; a atualização por cima v5 → v6; o sem rede; o desempenho; o tamanho; o diff de dependências; a crítica. **Não verificado:** tudo num iPhone (VoiceOver, arraste com o dedo, seletor de fotos e câmera, que nem existem no iOS); num Android físico (TalkBack nas ações de reordenação, câmera real, seletor de fotos do One UI, orientação de fotos de outros fabricantes, 120 Hz no arraste); as APIs 26 e 28 se o S4 não achou imagem; as barras cinzas do formulário no tema claro (decisão 19); a lacuna da decisão 11 da 4A; a release com a chave original.
- [ ] **Step 2:** Atualizar os documentos da lista.
- [ ] **Step 3: Verificar.** Os links do ROADMAP e dos relatórios abrem; o último run da branch está verde.
- [ ] **Step 4:** `compat-reviewer` na branch inteira (`git diff origin/main...HEAD`; esperado `SEGURO`), `/code-review` e a revisão final Opus da branch. Commit `docs: phase 4B routines verified`. **Push 3** e `gh run watch -R pedroyuuki/Mooscles`. O merge na `main` do fork fica com o controlador, depois do CI verde e da revisão sem achado alto.

---

### Task L (condicional): lotes de figuras

Não é replanejada aqui: vale a [Task L do plano da 4A](2026-10-29-phase4a-library.md#task-l-condicional-só-com-o-g1-aprovado-um-lote-de-figuras) (e a Task R, se o G1 for reprovado). Os lotes nascem da `main` do fork, em branch própria, e podem entrar durante a 4B sem bloqueá-la. Se algum entrar na `main` antes da Task 18, o controlador mescla a `main` na `feat/phase4b-routines` antes de medir o APK, porque o D20 soma tudo da fase; se o orçamento apertar, o controlador decide antes do Push 3.

## Paralelismo

No máximo dois implementadores ao mesmo tempo, em worktrees separadas, e no máximo um deles usando o emulador.

| Par | Por que pode | Cuidado |
| --- | --- | --- |
| Task 1 ∥ Task 4 | `:shared`/`:app` (host) × `:designsystem` (emulador) | os dois sobem `MIN_HOST_TESTS`: o controlador soma |
| Task 2 ∥ Task 5 | host × emulador | — |
| Task 3 ∥ Task 6 | host × spike (emulador, outra worktree) | — |
| Task 7 ∥ Task 8 | `:shared`/`:app` Robolectric (host) × `:screens` (emulador) | a 8 precisa da 3 |
| Task 7 (fim) ∥ Task 9 | host × emulador | — |
| Task 15 Step 1 ∥ Task 11 | contagem nas telas antigas (host) × Treinar (emulador) | a contagem roda antes de qualquer tela antiga sair |
| Task 20 (documentos) ∥ `compat-reviewer` da branch | só leitura | — |

Todo o resto é sequencial: a 2 precisa da 1; a 3 da 2; a 7 da 6 e da 3; a 10 da 7 e da 3; a 11 da 4 e da 10; a 12 da 11; a 13 da 8, 9 e 12; a 14 da 13; a 15 da 7 e da 11–14; a 16 a 20 de tudo. As Tasks 18 e 19 rodam sozinhas.

## Pré-voo: arquivos e interfaces compartilhados

| Tasks | O que compartilham | Nomes e assinaturas batem? |
| --- | --- | --- |
| 1 → 2, 3 | `RoutineSet.warmup`, `MIGRATION_5_6`, `RoutineSetRow.warmup` | sim |
| 1 → iOS | `SchemaV5.statements` (testFixtures, visível no `iosTest`) | sim |
| 2 → 3 | `createRoutine(name, folderId, id)`, `duplicateRoutine(id, name, newId)`, `addRoutineExercises`, `deleteRoutineFolder`, `reorderRoutineFolders`, `reorderFolderRoutines`, `setRoutineSetWarmup`, `restoreRoutineSet(entryId, position, item)` | sim |
| 3 → 8–15 | `RoutineSetUi.warmup`, `RoutineUi.lastFinishedAt`, `RoutineTargetUi(working, warmups)`, `TargetSetUi(reps, weightKg)`, `WorkoutExerciseUi.target`, `UiState.errorSubject`, as nove ações novas e o formato do `RecordingActions` | sim |
| 4 → 8, 11, 12, 13 | `rememberReorderState(listState, keys, onDrop, onLift)`, `reorderItem`, `reorderHandle`, `reorderActions(state, key, name)`, `ExerciseCardHeader(handle, subline)`, `INJECTS_DRAG` | sim |
| 6 → 7 | o pipeline por faixa de API | sim: a 7 só consome a decisão |
| 7 → 10, 15 | `ExercisePhotoStore` (`revision`, `stage`, `draft`, `commit`, `discard`, `read`, `remove`, `removeOrphans`), `PhotoFile(bytes, version)`, `NoExercisePhotos`, `AndroidExercisePhotoStore`, `PHOTO_CAPTURE_DIR` | sim |
| 8 → 13 | `targetLine(target, pounds)` | sim |
| 9 → 13 | `ExercisePickerSheet(…, mode, onPickMany)`, `PickerMode` | sim |
| 10 → 11, 13, 15, 16 | `PhotoCapture`, `ExercisePhotos(store, capture)`, `LocalExercisePhotos`, `rememberExerciseMedia(exercise, thumbnail)`, `FakeExercisePhotos` | sim |
| 11 → 12, 13, 15 | `TrainRoute(state, actions, onOpenRoutine, onOpenWorkout, onOpenSettings, modifier, now)`, `RenameSheet(…, title, confirmLabel, fieldLabel, selectAll)`, `RoutineSheets` | sim |
| 13 → 14, 15 | `RoutineEditorRoute(routineId, state, actions, onBack, onGone, onOpenWorkout, onExerciseInfo, modifier)`, `routineExerciseItems`, `RestSheet(exerciseName, seconds, key, onSave, onStartNow, onDismiss)` | sim |
| 15 → 17 | `tap-baseline-4b.md` | sim |
| 1 ∥ 4, 7 ∥ 8 | `.github/workflows/ci.yml` (mínimos) | conflito de uma linha, resolvido somando |

## Notas

- **Conflitos com a spec achados ao planejar (2026-10-30)**, resolvidos pelas decisões e registrados na spec na Task 20: "Começar" primário em cada card contra a regra de um `Primary` por tela (decisão 9); o VM montando a ordem global das rotinas (decisão 3, transação única); `restoreRoutineSet` sem o id (decisão 5); o `androidx.exifinterface` nas APIs 26 e 27 (decisão 11, framework); o `MigrationEquivalenceTest` "sem mudança" (muda a comparação, não as referências); a frase "movido para a posição" com gênero (decisão 18); o nome da rotina na barra fixa (decisão 13); o " (copy)" em inglês do `duplicateRoutine` atual (decisão 17).
- **Pendências da 4A fechadas aqui:** formulário aberto até salvar (decisões 12, Task 10); erro por comando (decisão 7, Tasks 3 e 10); figura sem quadros na pausa (decisão 20, Task 5); `LoadConvention` duplicado (decisão 21, Task 3); as telas antigas de rotina saem (Task 15). **Continuam estacionadas:** as barras cinzas do formulário (decisão 19) e a lacuna da decisão 11 da 4A.
