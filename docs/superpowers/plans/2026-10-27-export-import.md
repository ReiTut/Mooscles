# Export e import dos dados — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** O usuário exporta todos os dados para um arquivo JSON versionado e importa esse arquivo em qualquer instalação, sem nunca apagar nem sobrescrever o que já existe.

**Architecture:** O formato, a validação e o merge ficam no pacote novo `com.mooscles.backup`, em Kotlin puro e sem `android.*`, para servirem ao iOS na fase 1. O `WorkoutRepository` lê o estado local e grava o plano de import numa única transação. O `AppViewModel` cuida do seletor de arquivos (via `ContentResolver`), do `trainingGate` e do estado da UI. Uma seção nova em Configurações dispara o fluxo.

**Tech Stack:** Kotlin 2.1.20, kotlinx.serialization (JSON), Room 2.7.2, DataStore, Jetpack Compose, JUnit 4 + Robolectric (sdk 35).

**Spec:** [docs/superpowers/specs/2026-10-27-export-import-design.md](../specs/2026-10-27-export-import-design.md)

## Global Constraints

- Branch `feat/export-import`. Nada vai para a `main` sem PR.
- Sem mudança de schema do Room: `version` continua 5 e os JSON em `app/schemas/` não mudam.
- Plugin `org.jetbrains.kotlin.plugin.serialization` versão `2.1.20` (igual ao Kotlin). Biblioteca `org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1`.
- O pacote `com.mooscles.backup` não importa `android.*` nem `androidx.*`, com exceção de `BackupDocuments.kt`.
- `format = "mooscles-backup"`, `formatVersion = 1`. O nome sugerido do arquivo é `mooscles-backup-AAAA-MM-DD.json`, com a data local. O MIME é `application/json`.
- Enums gravados pelo nome da constante, pesos em kg (`Double`), horários em epoch ms (`Long`). Os campos têm os mesmos nomes das entidades.
- Leitura com `ignoreUnknownKeys = true`. Um `formatVersion` maior que 1 é recusado.
- Ler, validar, serializar e gravar acontece fora da thread principal. O import grava numa única `db.withTransaction` e segura `app.trainingGate`.
- A UI continua em inglês. As cópias fixas da spec estão em "Textos" (Task 6).
- Rodar os testes: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` e depois `./gradlew :app:testDebugUnitTest --tests "<classe>" --console=plain`.

## Review Focus

1. **Arquivo do provedor sem tipo MIME, ou com BOM UTF-8** (como acontece com arquivos baixados do Drive): o import precisa ler normalmente. O teste fica na Task 1 (`decodeAcceptsUtf8Bom`) e o `*/*` no seletor na Task 6.
2. **Arquivo enorme ou que não é texto** (um vídeo escolhido por engano): a resposta é uma recusa rápida com "damaged or incomplete", sem estourar a memória. O teste fica na Task 5 (`readRejectsOversizedDocument`).
3. **O usuário inicia um treino entre o resumo e a confirmação**: a confirmação recusa com "Finish or discard the active workout to import" e não grava nada. O teste fica na Task 5 (`confirmImportRefusesWhenWorkoutStartedAfterPreview`).
4. **O mesmo exercício personalizado com ID diferente em dois aparelhos** (o mesmo nome criado duas vezes): os dois coexistem e nenhum é mesclado por nome. O teste fica na Task 3 (`customExercisesWithSameNameButDifferentIdsBothSurvive`).
5. **Rotina que referencia um exercício de catálogo que o aparelho não tem** (catálogo de outra versão): o exercício do arquivo é inserido e a rotina importa normalmente. O teste fica na Task 3 (`catalogExerciseMissingLocallyIsInsertedFromFile`).

---

### Task 1: Formato do arquivo e codec

**Files:**
- Modify: `build.gradle.kts` (plugin `org.jetbrains.kotlin.plugin.serialization` `2.1.20` `apply false`)
- Modify: `app/build.gradle.kts` (aplicar o plugin e adicionar `kotlinx-serialization-json:1.8.1`)
- Create: `app/src/main/java/com/mooscles/backup/BackupFormat.kt` (DTOs `@Serializable`)
- Create: `app/src/main/java/com/mooscles/backup/BackupRows.kt` (conversão entidade ↔ linha)
- Create: `app/src/main/java/com/mooscles/backup/BackupCodec.kt`
- Create: `app/src/main/java/com/mooscles/backup/BackupException.kt`
- Create: `app/src/test/resources/backup/v1-sample.json` (fixture permanente)
- Test: `app/src/test/java/com/mooscles/backup/BackupCodecTest.kt`

**Interfaces:**
- Produces:
  - `@Serializable data class BackupFile(format: String, formatVersion: Int, exportedAt: String, app: BackupApp, preferences: BackupPreferences, data: BackupData)`
  - `@Serializable data class BackupApp(versionName: String, versionCode: Int, platform: String)`
  - `@Serializable data class BackupPreferences(pounds: Boolean, theme: String, autoRest: Boolean, defaultRestSeconds: Int, selectedRoutineFolderId: String)`
  - `@Serializable data class BackupData(exercises: List<ExerciseRow>, routineFolders: List<RoutineFolderRow>, routines: List<RoutineRow>, routineExercises: List<RoutineExerciseRow>, routineSets: List<RoutineSetRow>, workouts: List<WorkoutRow>, workoutExercises: List<WorkoutExerciseRow>, workoutSets: List<WorkoutSetRow>, routineTargetCheckpoints: List<CheckpointRow>)`. Todas as listas são obrigatórias, sem default.
  - Uma `…Row` por entidade, com exatamente os campos de `data/Models.kt` e `RoutineTargetCheckpoint` (enums como `String`).
  - Em `BackupRows.kt`:
    - `fun BackupData.Companion.from(data: AppData, checkpoints: List<RoutineTargetCheckpoint>): BackupData`
    - `fun BackupData.toEntities(): BackupEntities`, que lança `BackupException.Invalid` para enum desconhecido
    - `data class BackupEntities(exercises, routineFolders, routines, routineExercises, routineSets, workouts, workoutExercises, workoutSets, checkpoints)`, com as listas das entidades Room
  - `object BackupCodec { const val FORMAT = "mooscles-backup"; const val FORMAT_VERSION = 1; fun encode(file: BackupFile): String; fun decode(text: String): BackupFile }`
  - `sealed class BackupException(message: String) : Exception(message)`, com os subtipos `NotABackup`, `NewerVersion(found: Int)`, `Corrupted(detail: String)` e `Invalid(detail: String)`. As mensagens estão na Task 6, em "Textos".

- [ ] **Step 1: Escrever os testes que falham** em `BackupCodecTest` (JUnit puro, sem Robolectric):
  - `encodeThenDecodeReturnsEqualFile`: um `BackupFile` com uma linha de cada tipo, campos anuláveis nulos e não nulos, e pesos como `22.75` e `0.1 + 0.2`. Afirma que `decode(encode(f)) == f`.
  - `decodeIgnoresUnknownFields`: o JSON tem `"extra": 1` na raiz e dentro de uma linha. Decodifica sem erro.
  - `decodeRejectsNewerVersion`: `formatVersion: 2` lança `NewerVersion` com `found == 2`, **mesmo que** o resto do arquivo tenha outra estrutura (por exemplo, `data` como string).
  - `decodeRejectsOtherFormat`: `"format": "something"` e `"[]"` lançam `NotABackup`.
  - `decodeRejectsMalformedJson`: `"{"`, `""` e `"{\"format\":\"mooscles-backup\",\"formatVersion\":1}"` (sem `data`) lançam `Corrupted`.
  - `decodeAcceptsUtf8Bom`: o texto começa com `﻿` e decodifica normalmente.
  - `unknownEnumIsInvalid`: `"muscle": "TAIL"` → `toEntities()` lança `Invalid`.
  - `v1SampleFixtureStaysReadable`: carrega `backup/v1-sample.json` pelo classloader, decodifica, converte em entidades e afirma contagens e um valor concreto de cada lista. **Este teste nunca é apagado.**
- [ ] **Step 2: Rodar e ver falhar.** Comando: `./gradlew :app:testDebugUnitTest --tests "com.mooscles.backup.BackupCodecTest"`. Resultado esperado: erro de compilação, porque as classes não existem.
- [ ] **Step 3: Implementar os DTOs, as conversões e o codec.** O `decode` remove o BOM, faz o parse como `JsonObject` e confere `format` e depois `formatVersion` **antes** de desserializar o resto. Qualquer `SerializationException` ou `IllegalArgumentException` do parse vira `Corrupted`. O `Json` usa `ignoreUnknownKeys = true` e `encodeDefaults = true`. Para escrever `v1-sample.json`, gere um arquivo com `encode` e congele-o: ele passa a ser o contrato.
- [ ] **Step 4: Rodar e ver passar** (mesmo comando, resultado `BUILD SUCCESSFUL`). Depois, rodar a suíte inteira para confirmar que o plugin novo não quebrou nada: `./gradlew :app:testDebugUnitTest`.
- [ ] **Step 5: Commit** com `feat(backup): add versioned backup format and codec`.

### Task 2: Regras de edição compartilhadas

O import precisa aplicar exatamente as regras que o repositório aplica ao editar. Esta task extrai essas regras sem mudar comportamento.

**Files:**
- Create: `app/src/main/java/com/mooscles/data/EditRules.kt`
- Modify: `app/src/main/java/com/mooscles/data/WorkoutRepository.kt` (`checkedName`, `validateTarget`, `validateSet` e os `require` de `plannedSets`, descanso e quantidade de séries passam a delegar)
- Test: `app/src/test/java/com/mooscles/data/EditRulesTest.kt`

**Interfaces:**
- Produces: `internal object EditRules`. Cada função lança `IllegalArgumentException` com a **mesma mensagem** que o repositório usa hoje:
  - `fun name(value: String): String` (trim, não vazio, até 120)
  - `fun target(reps: Int, weightKg: Double?)`
  - `fun plannedSets(count: Int)` (1..50)
  - `fun rest(seconds: Int?)` (null ou 0..`RestDuration.MAX_SECONDS`)
  - `fun workoutSet(item: WorkoutSet)` (reps 0..9999, peso finito 0..100000, concluída exige reps > 0)
  - `const val MAX_ROUTINE_SETS = 50`
  - `const val MAX_WORKOUT_SETS = 100`

- [ ] **Step 1: Escrever `EditRulesTest`** com um caso válido e um inválido por função, conferindo o texto exato da mensagem, por exemplo `"Rest must be between 0:00 and 5:00."` e `"Names must be 120 characters or fewer."`.
- [ ] **Step 2: Rodar e ver falhar** (erro de compilação).
- [ ] **Step 3: Implementar `EditRules`** e trocar os `require` do repositório por chamadas a ele. Nenhuma mensagem muda.
- [ ] **Step 4: Rodar `EditRulesTest` e a suíte inteira.** Resultado esperado: tudo passa, com o mesmo total de antes mais os testes novos.
- [ ] **Step 5: Commit** com `refactor(data): share edit rules between repository and import`.

### Task 3: Planejador de import (validação e merge)

**Files:**
- Create: `app/src/main/java/com/mooscles/backup/ImportPlanner.kt`
- Test: `app/src/test/java/com/mooscles/backup/ImportPlannerTest.kt`

**Interfaces:**
- Consumes: `BackupFile`, `BackupEntities` (Task 1), `EditRules` (Task 2), `AppData`, `RoutineTargetCheckpoint`, `RoutineFolder.DEFAULT_ID`.
- Produces:
  - `data class LocalState(val data: AppData, val checkpoints: List<RoutineTargetCheckpoint>)`
  - `data class ImportSummary(val workoutsAdded: Int, val workoutsKept: Int, val routinesAdded: Int, val routinesKept: Int, val customExercisesAdded: Int, val restoresPreferences: Boolean)`
  - `data class ImportPlan(val entities: BackupEntities, val preferences: BackupPreferences?, val summary: ImportSummary)`. `entities` contém **só** o que será inserido. `preferences` é não nulo só quando vão ser aplicadas.
  - `object ImportPlanner { fun plan(file: BackupFile, local: LocalState): ImportPlan }`, que lança `BackupException.Invalid`.

Regras do algoritmo (da spec; o código decide como):

1. Converter com `toEntities()`. IDs duplicados dentro de uma lista do arquivo → `Invalid`.
2. **Agregados:**
   - Um treino cujo `id` existe localmente é descartado com todos os seus `WorkoutExercise` e `WorkoutSet` do arquivo, e conta em `workoutsKept`.
   - O mesmo vale para a rotina e seus `RoutineExercise` e `RoutineSet`, que contam em `routinesKept`.
   - Exercício, pasta e checkpoint são descartados um a um quando a chave já existe.
3. A validação acontece **depois** de descartar o que já existe. O que foi descartado não é validado.
4. **Referências** são resolvidas contra (o que será inserido) ∪ (o que existe localmente):
   - `routineExercise.routineId` → rotina;
   - `routineExercise.exerciseId` → exercício;
   - `routineSet.routineExerciseId` → item de rotina;
   - `workoutExercise.workoutId` → treino;
   - `workoutSet.workoutExerciseId` → exercício do treino.

   Referência que não fecha → `Invalid`. A exceção é `routine.folderId` desconhecido, que vira `RoutineFolder.DEFAULT_ID`.
5. Mais de um `Workout` `ACTIVE` no arquivo → `Invalid`. Um `ACTIVE` entra como ativo, porque quem chama garante que não há ativo local.
6. **Regras estruturais**, para tudo que vai ser inserido:
   - IDs não vazios;
   - números `>= 0` e finitos;
   - `startedAt > 0`;
   - `endedAt` nulo ou `>= startedAt`.
7. **Regras de edição** (`EditRules`), só para:
   - pastas;
   - rotinas, com seus itens e séries prescritas;
   - exercícios `custom = true`;
   - o treino `ACTIVE` com seus exercícios e séries.

   O histórico `FINISHED` **não** passa por elas.
8. **Ordem:** as rotinas inseridas numa pasta que já tem rotinas locais recebem `position` a partir de `max(local) + 1`, mantendo a ordem relativa do arquivo. Pastas novas recebem posição depois das locais pelo mesmo critério.
9. **Preferências:** aplicadas só se `local.data.workouts` e `local.data.routines` estiverem vazios. Se `selectedRoutineFolderId` não existir depois do import, usar `DEFAULT_ID`.

- [ ] **Step 1: Escrever os testes que falham.** São testes JUnit puros que montam `BackupFile` e `LocalState` à mão. Um helper `fileWith(...)` reduz o ruído. Os testes:
  - `emptyLocalImportsEverythingAndRestoresPreferences`: todas as contagens batem e `preferences != null`.
  - `existingWorkoutSkipsWholeAggregateEvenWithExtraSets`: o treino local tem 2 séries e o do arquivo tem 3, com uma série de ID novo. Afirma 0 séries inseridas e `workoutsKept == 1`.
  - `existingRoutineSkipsItsItemsAndPlannedSets`.
  - `planningSameFileAgainstItsOwnImportIsEmpty`: o local é igual ao conteúdo do arquivo. Afirma `entities` vazio e `preferences == null`.
  - `routineWithUnknownFolderGoesToDefaultFolder`.
  - `importedRoutinesAppendAfterLocalOnesInSameFolder`: as posições locais são 0 e 1 e as do arquivo são 0 e 1. Afirma que as inseridas ficam com 2 e 3, na ordem do arquivo.
  - `preferencesKeptWhenLocalHasData`.
  - `duplicateIdInsideFileIsInvalid`.
  - `missingReferenceIsInvalid`: um caso para cada uma das 5 referências.
  - `twoActiveWorkoutsAreInvalid`.
  - `finishedHistoryWithLongRestIsAccepted`: um `WorkoutExercise` `FINISHED` com `restSeconds = 400` entra com 400.
  - `routineWithLongRestIsInvalid`: `restSeconds = 400` numa rotina → `Invalid`.
  - `invalidRecordAlreadyOnPhoneDoesNotFailFile`: um treino com nome em branco, mas cujo ID existe localmente, não falha o arquivo.
  - `negativeOrNonFiniteNumbersAreInvalid`: `weightKg = -1.0` e `Double.NaN` num treino finalizado.
  - `customExercisesWithSameNameButDifferentIdsBothSurvive` (Review Focus 4).
  - `catalogExerciseMissingLocallyIsInsertedFromFile` (Review Focus 5).
- [ ] **Step 2: Rodar e ver falhar.** Comando: `./gradlew :app:testDebugUnitTest --tests "com.mooscles.backup.ImportPlannerTest"`.
- [ ] **Step 3: Implementar `ImportPlanner.plan`** seguindo as regras 1–9.
- [ ] **Step 4: Rodar e ver passar.**
- [ ] **Step 5: Commit** com `feat(backup): plan additive imports with validation`.

### Task 4: Repositório, DAO e preferências

**Files:**
- Modify: `app/src/main/java/com/mooscles/data/WorkoutDatabase.kt` (DAO: inserts em lista, **sem** mudar entidades nem `version`)
- Modify: `app/src/main/java/com/mooscles/data/WorkoutRepository.kt`
- Modify: `app/src/main/java/com/mooscles/AppPreferences.kt`
- Test: `app/src/test/java/com/mooscles/data/BackupRepositoryTest.kt` (Robolectric, no padrão de `RoomPersistenceTest`: banco em memória e `initialize()`)
- Test: `app/src/test/java/com/mooscles/AppPreferencesTest.kt` (acrescentar casos)

**Interfaces:**
- Consumes: `BackupFile`, `BackupData.from`, `BackupCodec` (Task 1), `ImportPlanner`, `LocalState`, `ImportPlan` (Task 3).
- Produces:
  - DAO, todos `@Insert` com o conflito padrão (ABORT), para uma colisão inesperada desfazer a transação:
    - `insertRoutineFolders(items: List<RoutineFolder>)`
    - `insertExercises(items: List<Exercise>)`
    - `insertRoutines(items: List<Routine>)`
    - `insertRoutineExercises(items: List<RoutineExercise>)`
    - `insertWorkouts(items: List<Workout>)`
    - `insertTargetCheckpoints(items: List<RoutineTargetCheckpoint>)`
  - `WorkoutRepository`:
    - `suspend fun localState(): LocalState`, numa transação, com `snapshot()` + `dao.targetCheckpoints()`
    - `suspend fun exportBackup(preferences: BackupPreferences, app: BackupApp, exportedAt: String): BackupFile`
    - `suspend fun importBackup(file: BackupFile): ImportPlan`
  - `AppPreferences`:
    - `suspend fun restore(value: BackupPreferences)`, que grava as 5 chaves num único `edit`, validando tema e descanso como os setters atuais
    - `fun PreferencesUi.toBackup(): BackupPreferences`

O `importBackup` faz tudo dentro de **uma** `db.withTransaction`, nesta ordem:

1. Se `dao.activeWorkout() != null`, lança `IllegalStateException("Finish or discard the active workout to import")`.
2. `ImportPlanner.plan(file, localState())`.
3. Insere na ordem das chaves estrangeiras: pastas, exercícios, rotinas, itens de rotina, séries de rotina, treinos, exercícios de treino, séries e checkpoints.
4. Chama `synchronizeRoutineTargets()`.
5. Retorna o plano.

Ele não toca nas preferências; quem aplica é o ViewModel.

- [ ] **Step 1: Escrever os testes que falham** em `BackupRepositoryTest`:
  - `exportThenImportIntoEmptyDatabaseIsIdentical`: cria rotina, exercício personalizado, um treino finalizado com séries e checkpoints no banco A. Exporta, codifica, decodifica e importa no banco B (vazio, inicializado). Afirma `B.snapshot() == A.snapshot()` e checkpoints iguais.
  - `importingSameFileTwiceChangesNothing`: o snapshot depois do 2º import é igual ao depois do 1º.
  - `mergeKeepsEveryLocalRowUnchanged`: o banco tem dados próprios. Importa um arquivo com agregados novos e com um treino de ID igual mas conteúdo diferente. Todas as linhas locais continuam iguais, todos os agregados novos estão presentes, e o treino de ID igual continua com o conteúdo local.
  - `invalidFileLeavesDatabaseUntouched`: para cada caso inválido (referência faltando e dois ativos), o snapshot e os checkpoints antes e depois são iguais e a exceção é `BackupException.Invalid`.
  - `importRefusedWhileWorkoutActive`: com `startEmpty()` ativo, lança `IllegalStateException` com a mensagem exata, e nada muda.
  - `inheritedTargetsFollowImportedRecord`: uma rotina local com série `inheritPr = true` e outra `inheritPr = false`. O histórico importado tem PR maior. Depois do import, a série herdada mostra o PR novo e a fixa não muda.
  - `largeArchiveExportAndImportStayFast`: gera 208 treinos finalizados com 61 séries cada, cerca de 12,7 mil séries. Mede `exportBackup` + `encode` e `decode` + `importBackup` num banco vazio, imprime os tempos com `println("export=…ms import=…ms")` e afirma que cada etapa leva menos de 15 000 ms. É um teto de host: o número real vai para a Task 7.

  Em `AppPreferencesTest`, acrescentar:
  - `restoreWritesAllFiveValues`
  - `restoreRejectsInvalidTheme`
- [ ] **Step 2: Rodar e ver falhar.** Comando: `./gradlew :app:testDebugUnitTest --tests "com.mooscles.data.BackupRepositoryTest" --tests "com.mooscles.AppPreferencesTest"`.
- [ ] **Step 3: Implementar** os métodos do DAO, do repositório e das preferências.
- [ ] **Step 4: Rodar e ver passar.** Depois, rodar a suíte inteira, que tem que continuar verde, inclusive os testes de migração.
- [ ] **Step 5: Commit** com `feat(backup): export and additive import in the repository`.

### Task 5: Arquivos do sistema e ações no ViewModel

**Files:**
- Create: `app/src/main/java/com/mooscles/backup/BackupDocuments.kt` (o único arquivo Android do pacote)
- Modify: `app/src/main/java/com/mooscles/ui/UiContract.kt` (`BackupUi`, `DataActions`, campo em `UiState`, `MoosclesActions : UpdateActions, DataActions`)
- Modify: `app/src/main/java/com/mooscles/AppViewModel.kt`
- Test: `app/src/test/java/com/mooscles/backup/BackupDocumentsTest.kt` (Robolectric)
- Test: `app/src/test/java/com/mooscles/AppViewModelBackupHostTest.kt` (Robolectric, no padrão de `AppViewModelFoldersDiscardHostTest`)

**Interfaces:**
- Consumes: Tasks 1, 3 e 4.
- Produces:
  - `object BackupDocuments`:
    - `suspend fun write(context: Context, uri: String, text: String)`
    - `suspend fun read(context: Context, uri: String, maxBytes: Long = 64L * 1024 * 1024): String`

    Os dois rodam em `Dispatchers.IO` e usam `ContentResolver`. Passar de `maxBytes` ou um conteúdo que não é UTF-8 válido → `BackupException.Corrupted`.
  - `fun suggestedBackupFileName(date: java.time.LocalDate): String`, que devolve `"mooscles-backup-2026-10-27.json"`. Fica em `BackupDocuments.kt`.
  - `data class BackupUi(val busy: Boolean = false, val pendingImport: ImportSummary? = null, val message: String? = null)`. `UiState` ganha `val backup: BackupUi = BackupUi()`.
  - `interface DataActions { fun exportData(uri: String); fun prepareImport(uri: String); fun confirmImport(); fun cancelImport(); fun clearBackupMessage() }`. A URI vai como `String` para o contrato da UI continuar livre de `android.*`.

Comportamento no `AppViewModel`: todos os passos passam por `act { … }`, e `busy` fica verdadeiro durante o trabalho.

- **`exportData`**:
  1. Lê as preferências com `app.preferences.snapshot().toBackup()`.
  2. Monta `BackupApp(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, "android")` e `exportedAt` com `Instant.now().toString()`.
  3. Chama `repo.exportBackup`, depois `BackupCodec.encode`, depois `BackupDocuments.write`.
  4. Mostra a mensagem de export da seção "Textos".
- **`prepareImport`**:
  1. Lê com `read`, decodifica com `decode` e planeja com `ImportPlanner.plan(file, repo.localState())`. Isso só calcula o resumo e não grava nada.
  2. Guarda o `BackupFile` num campo privado e coloca o resumo em `pendingImport`.
  3. Uma `BackupException` vira `message` com o texto da exceção.
- **`confirmImport`**:
  1. Com `app.trainingGate.withLock`, chama `app.updates.assertTrainingMayStart()` e depois `repo.importBackup(file)`.
  2. Se `plan.preferences != null`, chama `app.preferences.restore(it)`.
  3. Limpa `pendingImport` e mostra a mensagem de import concluído.
  4. Uma falha vira `message`, e nada parcial fica gravado.
- **`cancelImport`** descarta o arquivo guardado e `pendingImport`.

- [ ] **Step 1: Escrever os testes que falham.**

  Em `BackupDocumentsTest`, usando `Uri.fromFile` num diretório temporário, que o `ContentResolver` do Robolectric abre:
  - `writeThenReadRoundTrips`
  - `readRejectsOversizedDocument`: `maxBytes = 10` lança `Corrupted` (Review Focus 2)
  - `suggestedNameUsesLocalDate`

  Em `AppViewModelBackupHostTest`:
  - `exportWritesDecodableFileAndReportsCounts`
  - `prepareImportShowsSummaryWithoutWriting`: o snapshot não muda até confirmar.
  - `confirmImportAppliesDataAndPreferencesOnFreshInstall`
  - `confirmImportRefusesWhenWorkoutStartedAfterPreview`: `prepareImport`, depois `startWorkout(null)`, depois `confirmImport` resulta em mensagem de bloqueio, snapshot sem os dados do arquivo e `pendingImport` limpo (Review Focus 3).
  - `invalidFileShowsReasonAndKeepsData`
  - `cancelImportDiscardsPendingFile`
- [ ] **Step 2: Rodar e ver falhar.**
- [ ] **Step 3: Implementar.**
- [ ] **Step 4: Rodar e ver passar.** Depois, rodar a suíte inteira.
- [ ] **Step 5: Commit** com `feat(backup): wire export and import through the view model`.

### Task 6: Seção "Your data" em Configurações

**Files:**
- Create: `app/src/main/java/com/mooscles/ui/DataSection.kt`
- Modify: `app/src/main/java/com/mooscles/ui/MoosclesApp.kt` (`SettingsScreen`: um `item { DataSection(...) }` depois dos cards existentes)
- Test: `app/src/test/java/com/mooscles/DataSectionHostTest.kt` (no padrão de `UpdateSectionHostTest`, com `createComposeRule` e `LooperMode.PAUSED`)

**Interfaces:**
- Consumes: `BackupUi`, `DataActions` e `ImportSummary` (Task 5).
- Produces: `@Composable fun DataSection(state: BackupUi, workoutActive: Boolean, actions: DataActions, modifier: Modifier = Modifier)`.

Layout no padrão dos `Card` atuais de `SettingsScreen`:
- Os launchers são:
  - `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"))`, que abre com `suggestedBackupFileName(LocalDate.now())`;
  - `OpenDocument()`, com `arrayOf("application/json", "*/*")`.
- Cancelar o seletor (URI `null`) não faz nada.
- `workoutActive` desabilita **Import data** e mostra a linha de bloqueio.
- `pendingImport != null` abre o diálogo de confirmação com o texto do resumo.
- `message` aparece no card até `clearBackupMessage()`.

**Textos** (exatos, da spec ou definidos aqui):
- Título: `Your data`
- Botões: `Export data` e `Import data`
- Aviso fixo: `Backups contain your full workout history without encryption. Keep the file somewhere private.`
- Bloqueio: `Finish or discard the active workout to import`
- Resumo (diálogo, título `Import backup?`, botão `Import`): `Add {w} workouts, {r} routines and {c} custom exercises. {k} workouts are already on this phone and will be kept as they are.` A segunda frase só aparece se `workoutsKept > 0`. Se `restoresPreferences`, acrescentar `Your units, theme and rest settings will also be restored.`
- Export concluído: `Exported {w} workouts, {r} routines and {c} custom exercises`
- Import concluído: `Imported {w} workouts, {r} routines and {c} custom exercises`
- `BackupException`:
  - `NotABackup`: `This file is not a Mooscles backup.`
  - `NewerVersion`: `This backup was made by a newer version of Mooscles. Update the app and try again.`
  - `Corrupted`: `This backup file is damaged or incomplete.`
  - `Invalid`: `This backup contains invalid data: {detail}`
- Use singular quando a contagem for 1 (`1 workout`). Contagens zero ficam como estão.

- [ ] **Step 1: Escrever os testes que falham** em `DataSectionHostTest`:
  - `showsBothButtonsAndPrivacyNote`
  - `importDisabledDuringActiveWorkoutWithReason`
  - `summaryDialogShowsCountsAndConfirmCallsAction`: o resumo `ImportSummary(180, 28, 4, 0, 3, false)` mostra o texto exato da spec, e tocar em `Import` chama `confirmImport()`.
  - `singularCountsUseSingularNouns`
  - `messageIsShownUntilCleared`
- [ ] **Step 2: Rodar e ver falhar.**
- [ ] **Step 3: Implementar `DataSection` e incluí-la em `SettingsScreen`.** Passar `workoutActive = state.active != null` e as ações do `AppViewModel`.
- [ ] **Step 4: Rodar e ver passar.** Depois, rodar a suíte inteira e `./gradlew :app:lintDebug`, que precisa terminar sem erros novos.
- [ ] **Step 5: Commit** com `feat(backup): add Your data section to settings`.

### Task 7: Verificação no emulador, revisão de compatibilidade e docs

**Files:**
- Create: `.maestro/export-import.yaml`, só se o fluxo pelo seletor do sistema ficar estável no Maestro. Se não ficar, registre o motivo na verificação.
- Modify: `README.md` (um parágrafo curto "Your data" junto às outras features)
- Modify: `docs/superpowers/ROADMAP.md` (Andamento da fase 0: export/import concluído, com links para a spec e o plano)
- Create: `docs/superpowers/reports/2026-10-27-export-import-verificacao.md`

- [ ] **Step 1: Build e instalação.** Comandos: `./gradlew :app:assembleDebug` e depois `adb install -r app/build/outputs/apk/debug/app-debug.apk` no emulador `Mooscles_Pixel8_API35`.
- [ ] **Step 2: Preparar a base grande.** Por instrumentação ou pelo próprio fluxo de import, colocar no emulador uma base de cerca de 208 treinos e 12,7 mil séries, gerada com o mesmo gerador do teste da Task 4 e exportada para um arquivo. Medir no emulador o tempo de export e de import e confirmar que a UI não congela (sem ANR, com o indicador de `busy` visível).
- [ ] **Step 3: Fluxo completo pelo seletor** (mobile-mcp, Maestro ou `adb`):
  1. Exportar para Downloads.
  2. Rodar `adb shell pm clear com.mooscles`. Isso só pode ser feito no **emulador**: no celular real apaga os dados.
  3. Importar o arquivo.
  4. Conferir histórico, rotinas, recordes e preferências.
  5. Importar de novo e conferir o resumo "already on this phone".

  Guardar os prints da tela nos pontos-chave.
- [ ] **Step 4: Arquivos inválidos.** Importar um `.json` qualquer e um arquivo com `formatVersion: 2`, e conferir as mensagens e os dados intactos.
- [ ] **Step 5: Revisão de compatibilidade.** Rodar o subagente `compat-reviewer` sobre o diff da branch. Resultado esperado: `SEGURO`, sem mudança de `version`, schemas, chaves do DataStore e `update.json`.
- [ ] **Step 6: Escrever o relatório e os docs.** O relatório de verificação registra os tempos medidos (host e emulador), os prints, o que foi verificado e o que **não** foi (por exemplo, o celular físico e o Google Drive real, se não forem testados). Atualizar README e ROADMAP.
- [ ] **Step 7: Suíte completa e commit final.**

  ```bash
  ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
  git add README.md docs .maestro
  git commit -m "docs(backup): record export/import verification"
  ```
