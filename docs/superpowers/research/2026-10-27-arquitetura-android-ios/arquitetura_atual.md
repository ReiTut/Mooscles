# Arquitetura atual do Mooscles Android (v1.4.5 / build 16, outubro de 2026)

Fonte: leitura direta do código em este repositório (commit `4045bb8`, "Release v1.4.5 ..."). Todos os caminhos citados são relativos à raiz desse repositório. Contagens de linhas foram feitas com `wc -l` e as de imports com `grep '^import (android|androidx)\.'`. Nada foi modificado no projeto.

## 1. Camadas, fluxo de dados e threads

### Takeaway
É um app de um módulo só (`:app`), sem DI, com fluxo unidirecional: `MoosclesApplication` cria singletons preguiçosos → `WorkoutRepository.data` relê as 8 tabelas numa transação a cada invalidação (em `Dispatchers.Default`) → `AppViewModel` junta dados + preferências e chama `WorkoutPresentation.present()` **na thread principal** → `StateFlow<UiState>` (`contentUi`) → Compose/NavHost com rotas em string. Toda escrita passa por um `Channel` de comandos que serializa as operações. A contagem do timer fica isolada num `timerUi` separado.

### Cited Findings
- Um módulo só (`include(":app")`); stack: AGP 8.7.3, Kotlin 2.1.20, KSP 2.1.20-1.0.32, Compose BOM 2025.04.01, Material3, Navigation-Compose 2.8.9, Room 2.7.2, DataStore Preferences 1.1.4, WorkManager 2.10.5, Coroutines 1.10.2; minSdk 26, target/compile 35, JVM 17 — [settings.gradle.kts](settings.gradle.kts), [build.gradle.kts](build.gradle.kts), [app/build.gradle.kts](app/build.gradle.kts)
- Não há framework de DI. `MoosclesApplication` (26 linhas) declara `appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` e, com `by lazy`, `database`, `repository`, `preferences` (DataStore), `timer` (`RestTimerController`) e `updates` (`UpdateManager`), além de `trainingGate = Mutex()`, compartilhado entre treino e instalação de update. O `UpdateManager` recebe um lambda `trainingBusy` que consulta `repository.activeWorkout()` e o estado persistido do timer — [app/src/main/java/com/mooscles/MoosclesApplication.kt](app/src/main/java/com/mooscles/MoosclesApplication.kt)
- As dependências são achadas por service locator, com cast: `AppViewModel` faz `application as MoosclesApplication`, e `RestTimerController` acessa `(context.applicationContext as MoosclesApplication).repository` nos valores padrão do construtor — [AppViewModel.kt](app/src/main/java/com/mooscles/AppViewModel.kt), [timer/RestTimerController.kt](app/src/main/java/com/mooscles/timer/RestTimerController.kt)
- Uma Activity só (`MainActivity`, `ComponentActivity`, `enableEdgeToEdge`), que coleta `vm.contentUi` e `vm.timerUi` com `collectAsStateWithLifecycle` e chama `MoosclesApp(state, vm, timer)`. Ela também cuida do launcher de aprovação de instalação (`StartActivityForResult`), da permissão `POST_NOTIFICATIONS` (API 33+) e de `timer.foreground` em onStart/onStop, e chama `updates.onForeground()` em onResume — [MainActivity.kt](app/src/main/java/com/mooscles/MainActivity.kt)
- Repositório: `WorkoutRepository.data` é um `flow` sobre `db.invalidationTracker.createFlow(...)` das 8 tabelas. A cada sinal ele chama `snapshot()`, que lê as 8 tabelas inteiras numa `withTransaction`, e reaproveita listas iguais para manter identidade (`reuseEqual`). Depois aplica `.distinctUntilChanged().flowOn(Dispatchers.Default)`. O comentário explica por que não lê só a tabela que mudou: outra transação pode commitar entre o sinal e a leitura — [data/WorkoutRepository.kt](app/src/main/java/com/mooscles/data/WorkoutRepository.kt) (linhas ~17–45)
- Existe um observador estreito à parte (`activeWorkoutIds`, `SELECT id FROM workouts WHERE status='ACTIVE'`) usado pelo timer, para não assinar o arquivo inteiro — [data/WorkoutRepository.kt](app/src/main/java/com/mooscles/data/WorkoutRepository.kt), [data/WorkoutDatabase.kt](app/src/main/java/com/mooscles/data/WorkoutDatabase.kt)
- Canal de comandos: `private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)` e `act { }` = `commands.trySend(block)`. Um único consumidor em `viewModelScope` roda `repo.initialize()`, depois executa os comandos um a um e converte exceções em `error.value` (o estado de UI, não um crash). `MoosclesActions` (interface em `ui/UiContract.kt`) tem cerca de 50 operações, todas implementadas como `act { repo.x(...) }` — [AppViewModel.kt](app/src/main/java/com/mooscles/AppViewModel.kt), [ui/UiContract.kt](app/src/main/java/com/mooscles/ui/UiContract.kt)
- Apresentação na thread de UI: `presentation = combine(source, app.preferences.flow).map { presenter.present(data, prefs) }` não tem dispatcher próprio e é coletado via `stateIn(viewModelScope, Eagerly)`. O comentário diz "prepare card details on the UI thread", e o código abre um `Trace.beginSection("Mooscles:present")` — [AppViewModel.kt](app/src/main/java/com/mooscles/AppViewModel.kt)
- PERFORMANCE.md confirma que em 23/10/2026 a apresentação saiu de `withContext(Dispatchers.Default)` e voltou para o fluxo coletado por `viewModelScope` na main thread, e que `AppPreferences.flow` ganhou `distinctUntilChanged()`. No emulador, cada Start passou de 3 passes no worker para 1 na main. Os spans de apresentação na main duraram 26,70–40,50 ms. O resultado de desempenho foi "mixed", sem melhora consistente demonstrada — [PERFORMANCE.md](PERFORMANCE.md)
- `WorkoutPresentation` (127 linhas) é uma classe com caches mutáveis (`historySource`, `historyUi`, `recordsUi`, `routinesUi`, `previousPerformances`, `previousDescriptions`, `previousActive`). Ela só recalcula histórico e recordes quando os dados finalizados ou a unidade mudam, e reaproveita objetos `WorkoutExerciseUi`/`SetUi` iguais. Mas o treino ativo é recalculado a cada `present()`, chamando `WorkoutCalculations.prsForWorkout(data, id)` e `repsPrsForWorkout(data, id)` sobre o `AppData` completo — [WorkoutPresentation.kt](app/src/main/java/com/mooscles/WorkoutPresentation.kt)
- Isolamento do timer: `RestTimerController.ui` combina o estado persistido com `ticks` de 250 ms. O `AppViewModel` expõe `timerUi = app.timer.ui` separado de `contentUi`, e só o dock coleta `timerUi` ("Screens do not observe countdown ticks") — [timer/RestTimerController.kt](app/src/main/java/com/mooscles/timer/RestTimerController.kt), [AppViewModel.kt](app/src/main/java/com/mooscles/AppViewModel.kt)
- Navegação: um `NavHost(nav, startDestination = "routines")` com rotas em string `routines`, `routine/{id}`, `workout`, `library`, `history`, `workoutDetail/{id}?summary={summary}`, `exercise/{id}`, `records`, `settings`. Os argumentos usam `navArgument(... NavType.StringType/BoolType)` e as telas recebem o `UiState` inteiro mais `actions` — [ui/MoosclesApp.kt](app/src/main/java/com/mooscles/ui/MoosclesApp.kt) (linhas 87–111)
- Pontos de entrada e threads de trabalho: o updater roda em `Dispatchers.IO` (`check`, `download`, `onForeground`) e WorkManager. A prévia de compartilhamento decodifica os PNGs e chama `WorkoutCardRenderer().export(...)` dentro de `withContext(Dispatchers.IO)`. Os receivers (`InstallStatusReceiver`, `UpdateReplacedReceiver`) usam `goAsync()` — [updates/UpdateManager.kt](app/src/main/java/com/mooscles/updates/UpdateManager.kt), [ui/WorkoutSharePreview.kt](app/src/main/java/com/mooscles/ui/WorkoutSharePreview.kt), [updates/UpdateWorkers.kt](app/src/main/java/com/mooscles/updates/UpdateWorkers.kt), [updates/InstallStatusReceiver.kt](app/src/main/java/com/mooscles/updates/InstallStatusReceiver.kt)
- `startWorkout` e `discardWorkout` usam `MutableStateFlow.compareAndSet` contra toque repetido, pegam o `trainingGate` (`withLock`) e esperam até 30 s (`withTimeout(30_000)`) o `contentUi` refletir o estado salvo antes de navegar ou liberar — [AppViewModel.kt](app/src/main/java/com/mooscles/AppViewModel.kt)

### Inferences
- O desenho prioriza correção e coerência (snapshot atômico, comandos serializados, identidade estável para o Compose) acima de escala. Qualquer escrita, até marcar uma série, relê todas as tabelas (inclusive todo o histórico de séries) e roda de novo, na main thread, o cálculo de PR do treino ativo sobre o arquivo inteiro. O custo cresce com o tamanho do histórico.
- A cadeia Repository → Presentation → `UiState` imutável → Compose segue um padrão MVI/UDF que dá para reproduzir em SwiftUI (ObservableObject/`@Observable` com um único estado) ou compartilhar via KMP (StateFlow exposto ao Swift).
- Como não há DI, os construtores com parâmetros padrão (por exemplo `RestTimerController(context, scope, persistence, activeWorkoutId, sessionChanges, now)`) funcionam como pontos de injeção manual para testes.

### Gaps
- Não medi quantos milissegundos `present()` leva com o histórico do usuário real. Os números disponíveis são de emulador ou benchmark de host (ver seção 5).

## 2. Portabilidade: o que é Kotlin puro e o que está preso ao Android

### Takeaway
Das 6.715 linhas de código de produção, cerca de 1.723 (≈26%) estão em arquivos sem nenhum import `android.*`/`androidx.*`. Só uma parte disso é Kotlin "common" de verdade: domínio, modelos de UI, catálogo e a regra do timer usam aqui e ali APIs da JVM (`Math.ulp`, `String.format`, `java.util.Locale`, `UUID`, `java.text.Normalizer`, `java.time`, `org.json`, `HttpURLConnection`). O núcleo que dá para compartilhar com iOS com ajustes pequenos (domínio, regra do timer, contrato e mapeamento de UI, catálogo, busca, modelo de cartão) soma por volta de 1.300 linhas (≈20%). O resto (Compose UI com 2.376 linhas, Room/DAO, DataStore, AlarmManager e notificações, WorkManager + PackageInstaller, Bitmap/Canvas/FileProvider) é específico do Android ou, no caso do updater, nem faz sentido no iOS.

### Cited Findings
- Linhas por pacote (main): raiz `com.mooscles` 478; `data/` 1.277; `domain/` 419; `timer/` 356; `updates/` 1.299; `sharing/` 510; `ui/` 2.376. Total 6.715. Testes de host: 9.310 linhas. androidTest: 971 — contagem `wc -l` em `app/src/main/java/com/mooscles/**`, `app/src/test/**`, `app/src/androidTest/**`
- Arquivos **sem** import android/androidx (1.723 linhas): `WorkoutPresentation.kt` (127), `ui/UiContract.kt` (126), `ui/UpdateUi.kt` (35), `sharing/WorkoutShareCard.kt` (115), `timer/RestTimerState.kt` (57), `updates/ApkDownload.kt` (69), `updates/UpdateModels.kt` (124), `updates/ReleaseClient.kt` (219), `data/ExerciseCatalog.kt` (343), `data/ExerciseSearch.kt` (89), `domain/RestDuration.kt` (84), `domain/WorkoutCalculations.kt` (275), `domain/RoutineTargetRecords.kt` (60) — grep de imports nos arquivos citados
- APIs da JVM nesses arquivos "puros":
  - `WorkoutPresentation.kt` usa `java.util.Locale`, `String.format(Locale.US, "%.2f", …)` e `Math.ulp` — [WorkoutPresentation.kt](app/src/main/java/com/mooscles/WorkoutPresentation.kt)
  - `WorkoutCalculations.kt` usa `Math.ulp` em `heavier`/`sameLoad` — [domain/WorkoutCalculations.kt](app/src/main/java/com/mooscles/domain/WorkoutCalculations.kt)
  - `WorkoutShareCard.kt` usa `java.text.NumberFormat` e `java.time.*` — [sharing/WorkoutShareCard.kt](app/src/main/java/com/mooscles/sharing/WorkoutShareCard.kt)
  - `ExerciseSearch.kt` usa `java.text.Normalizer` — [data/ExerciseSearch.kt](app/src/main/java/com/mooscles/data/ExerciseSearch.kt)
  - `ReleaseClient.kt` e `UpdateModels.kt` usam `org.json.JSONArray/JSONObject`, `java.net.HttpURLConnection/URL/URI` e `java.time.ZonedDateTime` — [updates/ReleaseClient.kt](app/src/main/java/com/mooscles/updates/ReleaseClient.kt), [updates/UpdateModels.kt](app/src/main/java/com/mooscles/updates/UpdateModels.kt)
  - `ApkDownload.kt` usa `java.io.File` e `HttpURLConnection` — [updates/ApkDownload.kt](app/src/main/java/com/mooscles/updates/ApkDownload.kt)
- Os tipos de domínio dependem das entidades Room: `WorkoutCalculations`, `RoutineTargetRecords` e `WorkoutPresentation` operam sobre `AppData`, `Workout`, `WorkoutSet`, `WorkoutExercise` e `LoadConvention`, definidos em `data/Models.kt`. Esse arquivo importa `androidx.room.Entity/ColumnInfo/ForeignKey/Index/PrimaryKey` e `java.util.UUID` (`newId()`), e usa `System.currentTimeMillis()` como default de `Workout.startedAt` — [data/Models.kt](app/src/main/java/com/mooscles/data/Models.kt)
- `RestTimerState` é puro: um data class com deadline no relógio de parede, `remaining(now)` e `progress(now)` — [timer/RestTimerState.kt](app/src/main/java/com/mooscles/timer/RestTimerState.kt). Já `RestTimerPersistence` usa `androidx.datastore` — [timer/RestTimerPersistence.kt](app/src/main/java/com/mooscles/timer/RestTimerPersistence.kt)
- `WorkoutRepository.kt` (515 linhas) só importa `androidx.room.withTransaction`, mas depende de `WorkoutDatabase`/`WorkoutDao` (Room) e concentra regras de negócio: semear e sincronizar o catálogo, sincronizar alvos de rotina com PRs, montar snapshots ao iniciar treino, reordenar — [data/WorkoutRepository.kt](app/src/main/java/com/mooscles/data/WorkoutRepository.kt)
- Pontos presos ao Android:
  - Room: `@Dao`, `@Database`, `Migration`, `SupportSQLiteDatabase`, `Room.databaseBuilder(context, …, "mooscles.db")` — [data/WorkoutDatabase.kt](app/src/main/java/com/mooscles/data/WorkoutDatabase.kt)
  - DataStore: `preferencesDataStore(name = "local_preferences")` é o store único de preferências e também do timer — [AppPreferences.kt](app/src/main/java/com/mooscles/AppPreferences.kt)
  - Timer: `AlarmManager.setExactAndAllowWhileIdle`/`setAndAllowWhileIdle(RTC_WAKEUP, …)`, `RestAlarmReceiver`, `NotificationChannel("rest", …, IMPORTANCE_HIGH)` e `ToneGenerator(STREAM_NOTIFICATION, 85)` — [timer/RestTimerController.kt](app/src/main/java/com/mooscles/timer/RestTimerController.kt)
  - Updater: `PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, HOURS)`, `PackageInstaller` sessions, `getPackageArchiveInfo` + `signingInfo` e `MY_PACKAGE_REPLACED` — [updates/UpdateManager.kt](app/src/main/java/com/mooscles/updates/UpdateManager.kt), [updates/UpdateInstaller.kt](app/src/main/java/com/mooscles/updates/UpdateInstaller.kt), [updates/ApkVerifier.kt](app/src/main/java/com/mooscles/updates/ApkVerifier.kt), [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml)
  - Cartão de compartilhamento: `Bitmap.createBitmap(WIDTH, h, ARGB_8888)`, `Canvas`, `StaticLayout`, `bitmap.compress(PNG, 100, …)` em `cacheDir/workout-shares`, `FileProvider` com authority `${applicationId}.workout-shares` e queries para `com.whatsapp`/`com.whatsapp.w4b` — [sharing/WorkoutCardRenderer.kt](app/src/main/java/com/mooscles/sharing/WorkoutCardRenderer.kt), [sharing/WorkoutShareIntent.kt](app/src/main/java/com/mooscles/sharing/WorkoutShareIntent.kt), [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml)
  - UI: todos os 11 arquivos de tela/compose em `ui/` importam `androidx.compose.*`; `MoosclesApp.kt` importa `androidx.navigation.*`; `WorkoutSharePreview.kt` importa `android.graphics.Bitmap/BitmapFactory` e `LocalContext`; `Theme.kt` importa `android.app`/`androidx.core.view` — grep de imports em `app/src/main/java/com/mooscles/ui/*.kt`
- Textos de UI: quase todos estão hardcoded em inglês dentro dos composables. Só `WorkoutSharePreview.kt` (17 usos) e `HistoryScreens.kt` (2) usam `stringResource`/`R.string`, e há 16 `<string>` em `res/values` (15 em `share_strings.xml`) — grep em `app/src/main/java/com/mooscles/ui/*.kt` e `app/src/main/res/values/*.xml`
- Catálogo embutido: `ExerciseCatalog.exercises` é uma lista Kotlin pura com 287 chamadas `exercise("id", "Nome", Muscle.X)`, IDs explícitos e estáveis, agrupados por `section(Gear.X)` — [data/ExerciseCatalog.kt](app/src/main/java/com/mooscles/data/ExerciseCatalog.kt)

### Inferences
- Candidatos naturais a um módulo `shared` (KMP commonMain), com adaptação pequena:
  - `domain/` (419 linhas): trocar `Math.ulp` por `Double.ulp` do stdlib Kotlin, que é multiplataforma.
  - `RestTimerState` (57) e `RestDuration`.
  - `ui/UiContract.kt` (126) e `ui/UpdateUi.kt` (35, modelos de estado).
  - `ExerciseCatalog` (343).
  - `ExerciseSearch` (89): exige substituir `java.text.Normalizer`.
  - `WorkoutShareCard` (115): exige substituir `java.time`/`NumberFormat`, por exemplo com kotlinx-datetime.
  - `WorkoutPresentation` (127): exige substituir `String.format`/`Locale`.
  - Total aproximado: 1.300 linhas, ≈20% do código de produção.
  - As entidades de `Models.kt` (163) também entram se as anotações Room forem aceitáveis em commonMain (Room 2.7.x anuncia suporte a KMP; confirmar com outra pesquisa) ou se os modelos virarem data classes puras com mapeamento.
- `WorkoutRepository` (515) carrega muita regra de negócio misturada com DAO. Para compartilhar, ou se adota Room KMP/SQLDelight em common, ou se separa a regra (por exemplo `synchronizeRoutineTargets`, montagem de snapshots) do acesso a dados.
- O updater inteiro (`updates/`, 1.299 linhas, ≈19% do código) e a lógica de `trainingGate` ligada à instalação **não têm equivalente no iOS** (não existe sideload de APK e a distribuição seria via App Store/TestFlight). Esse código simplesmente não seria portado.
- O renderizador do cartão (303 linhas de Canvas) precisaria ser reescrito (Core Graphics/SwiftUI `ImageRenderer`) ou trocado por renderização compartilhada (Skia/Compose Multiplatform). O modelo de dados do cartão (`WorkoutShareCard`) dá para compartilhar.
- O timer de descanso depende de alarmes exatos e notificação em background. No iOS o equivalente seria notificação local agendada (UNUserNotificationCenter). A lógica de deadline no relógio de parede de `RestTimerState` já é independente de processo, o que facilita a portabilidade.
- Como as strings são hardcoded e não há i18n, uma UI compartilhada (Compose Multiplatform) ou reescrita (SwiftUI) não perde infraestrutura de tradução, porque ela não existe.

### Gaps
- Não verifiquei se Room 2.7.2, DataStore 1.1.4 e Navigation Compose 2.8.9 nessas versões exatas têm artefatos KMP/iOS usáveis. Isso é conhecimento externo ao código e cabe a outro pesquisador.
- A porcentagem "compartilhável" é estimativa por arquivo inteiro. Não separei função por função dentro de arquivos mistos como `WorkoutRepository.kt` ou `AppViewModel.kt`.

## 3. Modelo de dados, schema v5, migrações e catálogo

### Takeaway
SQLite via Room com 9 entidades e IDs `String` UUID. O histórico é um snapshot independente (o exercício é copiado para `workout_exercises`). O schema está na v5, com 4 migrações aditivas escritas à mão e schemas JSON v1–v5 exportados. Não há fallback destrutivo. O catálogo de 287 exercícios é semeado e reconciliado a cada inicialização. A estratégia é conservadora e bem testada, mas o app desliga todo backup e não tem export/import: perder o aparelho significa perder os dados.

### Cited Findings
- Entidades no `@Database(version = 5, exportSchema = true)`: `Exercise`, `RoutineFolder`, `Routine`, `RoutineExercise`, `RoutineSet`, `Workout`, `WorkoutExercise`, `WorkoutSet`, `RoutineTargetCheckpoint`. Os `@TypeConverters(EnumConverters)` gravam enums como `name` — [data/WorkoutDatabase.kt](app/src/main/java/com/mooscles/data/WorkoutDatabase.kt)
- Detalhes das tabelas — [data/Models.kt](app/src/main/java/com/mooscles/data/Models.kt), [data/RoutineTargetCheckpoint.kt](app/src/main/java/com/mooscles/data/RoutineTargetCheckpoint.kt):
  - `exercises`: id, name, muscle, equipment, convention, custom.
  - `routine_folders`: id, name, position. Pasta default `id="default"`, "My routines".
  - `routines`: id, name, position, `folderId` com DEFAULT `'default'` e índice.
  - `routine_exercises`: FK CASCADE para routine e FK RESTRICT para exercise. Campos plannedSets, targetReps, targetWeightKg e restSeconds são legados e espelhados.
  - `routine_sets`: FK CASCADE, targetReps, targetWeightKg?, inheritPr.
  - `workouts`: startedAt, endedAt?, status ACTIVE/FINISHED, sourceRoutineId?.
  - `workout_exercises`: FK CASCADE para workout. Copia name, muscle, equipment e convention ("Immutable exercise metadata is copied here when a workout starts").
  - `workout_sets`: reps, weightKg, completed, warmup, assistance.
  - `routine_target_checkpoints`: PK composta (exerciseId, convention).
- Os pesos são sempre guardados em kg e a unidade só existe na entrada e na exibição. Os enums `LoadConvention` (TOTAL_LOAD, PER_HAND, ONE_SIDE, BODYWEIGHT, ASSISTANCE) definem a semântica de volume, que nunca é duplicado — [data/Models.kt](app/src/main/java/com/mooscles/data/Models.kt), [AGENTS.md](AGENTS.md)
- Migrações, todas aditivas e registradas em `open()` com `addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)`, mais um callback `onCreate` que chama `createDefaultFolder` para a instalação nova chegar ao mesmo estado — [data/WorkoutDatabase.kt](app/src/main/java/com/mooscles/data/WorkoutDatabase.kt):
  - 1→2: cria `routine_target_checkpoints`.
  - 2→3: cria `routine_sets` e copia o alvo legado de cada `routine_exercise` em N linhas com `inheritPr=0`, via cursor e `INSERT`.
  - 3→4: `ALTER TABLE workouts ADD COLUMN sourceRoutineId TEXT` (anulável, sem backfill adivinhado).
  - 4→5: cria `routine_folders`, insere a default, `ALTER TABLE routines ADD COLUMN folderId TEXT NOT NULL DEFAULT 'default'` e cria o índice.
- Os schemas JSON v1–v5 estão em `app/schemas/com.mooscles.data.WorkoutDatabase/{1..5}.json`, gerados pelo KSP (`room.schemaLocation`). Um hook do Claude Code bloqueia edição manual desses JSONs — [app/build.gradle.kts](app/build.gradle.kts), [CLAUDE.md](CLAUDE.md), listagem de `app/schemas/`
- Regras de compatibilidade documentadas: `version` só cresce, mesmo em build de recuperação; migrações são aditivas; chaves do DataStore não podem ser renomeadas; `applicationId` e chave debug são fixos; `versionCode` nunca é reutilizado — [AGENTS.md](AGENTS.md)
- Os testes de migração constroem bancos antigos de verdade a partir do JSON exportado de cada versão ("Builds actual old databases from Room's exported schemas before running every migration"), lendo `schemas/com.mooscles.data.WorkoutDatabase/$version.json` com `org.json`. Não usam `MigrationTestHelper` — [app/src/test/java/com/mooscles/data/RoutineTargetMigrationTest.kt](app/src/test/java/com/mooscles/data/RoutineTargetMigrationTest.kt)
- Há testes de rollback/compatibilidade com o v1.4.2 (schema 4), com fixture `app/src/test/resources/rollback/v142-room-schema4.json`: `RollbackV142DataCompatibilityTest`, `RollbackV142PreferencesCompatibilityTest` e `RollbackUpdatePolicyTest` — listagem de `app/src/test/`
- Migração real no emulador (schema 4→5): preservou 5 rotinas, 994 séries planejadas, 208 treinos concluídos, 1 treino ativo e 12.672 séries, além de checkpoints de PR e bytes de preferências e timer — [VERIFICATION.md](VERIFICATION.md) (seção Release 1.4.5)
- `initialize()` roda numa transação a cada abertura do app — [data/WorkoutRepository.kt](app/src/main/java/com/mooscles/data/WorkoutRepository.kt):
  - garante a pasta default;
  - semeia `ExerciseCatalog.exercises` com `INSERT OR IGNORE`;
  - atualiza name, muscle e equipment dos não-custom que divergirem do catálogo;
  - garante `routine_sets`;
  - limita restSeconds ao máximo;
  - chama `synchronizeRoutineTargets()`, que roda `RoutineTargetRecords.latest(snapshot())` sobre o banco inteiro.
- `inheritedSet()` também chama `RoutineTargetRecords.latest(snapshot())`, ou seja, lê o arquivo inteiro por série herdada criada. `synchronizeRoutineTargets()` é chamado em pelo menos 3 pontos do repositório (linhas 72, 152 e 491) — [data/WorkoutRepository.kt](app/src/main/java/com/mooscles/data/WorkoutRepository.kt)
- Backup desligado de propósito: `android:allowBackup="false"`, `fullBackupContent="false"` e `data_extraction_rules.xml` excluindo todos os domínios de cloud-backup e device-transfer. A tela de Settings avisa: "Workout data is excluded from automatic cloud backups" — [AndroidManifest.xml](app/src/main/AndroidManifest.xml), [res/xml/data_extraction_rules.xml](app/src/main/res/xml/data_extraction_rules.xml), [ui/MoosclesApp.kt](app/src/main/java/com/mooscles/ui/MoosclesApp.kt) linha 198
- Não encontrei código de export/import de dados (CSV/JSON). Os únicos "exports" são os PNGs do cartão de compartilhamento — grep por `backup|csv|export` em `app/src/main/java`
- Os recordes são derivados do histórico concluído, não armazenados. Correções e deleções recalculam tudo — [README.md](README.md) (linha ~88)

### Inferences
- Para uma versão iOS, o schema (9 tabelas, chaves UUID em texto, enums como texto, kg) é simples e portável para SQLite, seja com Room KMP, SQLDelight, GRDB ou SwiftData. O histórico de migrações v1–v4 só importa no Android. Uma base iOS nova poderia começar direto no equivalente do v5.
- Sem backup nem export, migrar um usuário de Android para iOS exigiria criar um formato de export/import, que hoje não existe.
- Os campos legados mantidos por compatibilidade (`routine_exercises.plannedSets/targetReps/targetWeightKg`, a tabela `routine_target_checkpoints` "retained for upgrade compatibility") são dívida que um app iOS novo não precisaria herdar.

### Gaps
- Não li `app/schemas/*/5.json` linha a linha para conferir cada índice. A descrição vem das entidades e migrações em Kotlin.

## 4. Testes e sinais de qualidade

### Takeaway
A estratégia é fortemente baseada em testes de host: 363 `@Test` em 49 arquivos de `app/src/test`, com JUnit4 + Robolectric (sdk 35), Room/DataStore reais em arquivo e Compose UI tests rodando no host. Há poucos testes instrumentados (21 `@Test` em 6 arquivos), raramente executados por falta de aparelho ou emulador funcional. A documentação é bastante honesta sobre o que não foi verificado: desempenho em aparelho físico, teclado real e upgrade real via PackageInstaller. Não há CI.

### Cited Findings
- Dependências de teste: junit 4.13.2, robolectric 4.14.1, room-testing, work-testing, compose ui-test-junit4, coroutines-test; `unitTests.isIncludeAndroidResources = true` — [app/build.gradle.kts](app/build.gradle.kts)
- Contagens:
  - 363 anotações `@Test` em `app/src/test`; 40 arquivos usam `RobolectricTestRunner`/`AndroidJUnit4`.
  - `@Config(sdk=[35])` aparece em 34 classes, mais variantes com `qualifiers = "w430dp-h932dp-xhdpi"`, `shadows = [HostFileProviderPaths]` e uma com `sdk=[34]`.
  - 21 `@Test` em `app/src/androidTest`.
  - Fonte: grep em `app/src/test` e `app/src/androidTest`.
- Cobertura por área (nomes dos arquivos de teste):
  - domínio: `WorkoutCalculationsTest`, `WorkoutCalculationIndexTest`, `RoutineTargetRecordsTest`, `RestDurationTest`;
  - dados e migração: `RoomPersistenceTest`, `RoutineTargetMigrationTest`, `RoutineFolderPersistenceTest`, `CatalogUpgradeTest`, `WorkoutLoadingDataTest`, `RollbackV142DataCompatibilityTest`;
  - timer: `RestTimerControllerTest`, `RestTimerPersistenceTest`, `RestTimerSessionLifecycleTest`;
  - updater: `ReleaseClientTest`, `ApkVerifierTest`, `UpdateStorePolicyTest`, `UpdateSchedulingHostTest`, `UpdateInstallerHostTest`, `UpdateDataPreservationTest`;
  - sharing: `WorkoutCardRendererTest`, `WorkoutShareIntentTest`, `WorkoutShareExportIntegrationTest`;
  - UI no host: `UiFlowHostTest`, `RoutineEditorHostTest`, `WorkoutLazyRowsHostTest`, `DiscardNavigationHostTest`, `TimerKeyboardInsetsHostTest` e outros;
  - fonte: listagem de `app/src/test/java/com/mooscles/**`.
- androidTest: `UiFlowTest` (fluxo rotina→treino→descanso→histórico), `UiFeatureSmokeTest` (teclado real e pastas), `data/WorkoutRepositoryTest`, `timer/RestTimerPersistenceTest`, mais fixtures opt-in de desempenho (`WorkoutPerformanceFixtureTest`, `ThreadComparisonFixtureTest`) — listagem de `app/src/androidTest/java/com/mooscles/**`
- Resultado registrado: suíte completa de host com "347 tests in 49 suites; zero failures/errors/skips" (registro da era 1.4.3/Build 14) e lint debug com 0 erros e 21 warnings. Para 1.4.5: "43 focused host checks", lint 0/21, builds debug e instrumentado; a checagem de navegação/pastas no emulador Android 15 passou — [VERIFICATION.md](VERIFICATION.md), [PERFORMANCE.md](PERFORMANCE.md)
- Itens não verificados, declarados explicitamente:
  - posicionamento do teclado real ("Gboard rendered only its small control bar"), em [VERIFICATION.md](VERIFICATION.md) seção 1.4.5;
  - "Device startup, scroll/timer frame timing, PSS and repeated-cycle performance | UNVERIFIED", em [VERIFICATION.md](VERIFICATION.md) linha ~95;
  - "Actual Android upgrade and restored installed UI | Not verified", em [VERIFICATION.md](VERIFICATION.md) linha ~133;
  - `UiFlowTest` completo "did not complete" (`UiAutomation.disconnect`/`DeadObjectException`), "This is not a passing UI suite", em [PERFORMANCE.md](PERFORMANCE.md).
- Benchmark de host da preparação de treinos (30 sessões × 48 exercícios, 5.952 séries): 8.482,5 ms antes contra 51,6 ms com índices. 120 snapshots cacheados levaram 94,4 ms. Os próprios autores avisam que é CPU de host, não tempo em aparelho — [VERIFICATION.md](VERIFICATION.md) linha ~164
- Existe um build type `performance` (R8, shrink, não-debuggable, profileable, assinado com a chave debug) para medições — [app/build.gradle.kts](app/build.gradle.kts), [PERFORMANCE.md](PERFORMANCE.md)
- Processo: o subagente `compat-reviewer` é obrigatório antes de mudar `data/`, `updates/`, `timer/`, `AppPreferences.kt` ou `build.gradle.kts`, e um hook bloqueia edição de schemas JSON, `*.jks` e `local.properties` — [CLAUDE.md](CLAUDE.md), [.claude/agents/compat-reviewer.md](.claude/agents/compat-reviewer.md)
- Não existe diretório `.github/` (sem CI/GitHub Actions). Remotes: `origin` = `pedroyuuki/Mooscles`, `upstream` = `ReiTut/Mooscles` — `ls .github`, `git remote -v`

### Inferences
- A lógica de domínio, persistência, migração e updater está bem coberta por testes determinísticos de host. O ponto fraco é a validação em aparelho real (UI, desempenho, upgrade), feita de forma manual e esporádica.
- Os testes de domínio (`WorkoutCalculationsTest` etc.) seriam os mais fáceis de levar para `commonTest` num cenário KMP. Os testes Robolectric e Compose são específicos do Android.

### Gaps
- Não rodei a suíte (seria preciso JDK 21/SDK 35 configurados e não era objetivo). As contagens de `@Test` são estáticas e podem divergir dos totais reportados (347 em VERIFICATION.md contra 363 anotações hoje, possivelmente por testes adicionados em 1.4.4/1.4.5).
- Não há métrica de cobertura (JaCoCo/Kover) configurada no build.

## 5. Problemas conhecidos e dívida técnica

### Takeaway
Os principais problemas são de desempenho com histórico grande: o snapshot do banco inteiro a cada escrita e a apresentação e o cálculo de PR na main thread, com um travamento de inicialização documentado e não resolvido. Somam-se a isso scripts de build/release só para Windows, ausência de DI e de CI, rotas em string, UI sem i18n, distribuição por APK debug com chave local (risco de perder a chave) e ausência total de backup.

### Cited Findings
- Travamento conhecido: "A large-fixture startup input timeout was observed in the unchanged UI-thread PR calculation path. No improvement to that existing limitation is claimed by this release" ([VERIFICATION.md](VERIFICATION.md), seção 1.4.5) e "A startup freeze in the existing UI-thread PR-calculation path remains with the very large test workout" ([README.md](README.md) linha 11). Antes, no baseline histórico, o Android reportou "5002 ms focus-input timeout" ([PERFORMANCE.md](PERFORMANCE.md))
- Mecânica por trás disso: `present()` roda na main thread e, a cada emissão, chama `WorkoutCalculations.prsForWorkout(data, w.id)` e `repsPrsForWorkout` para o treino ativo. Cada chamada cria um `SnapshotIndex(data)` que agrupa e ordena **todas** as séries e exercícios do arquivo (`data.sets.groupBy … sortedBy`) e percorre todos os treinos anteriores. `bestRepetitionSets` usa `indexOfFirst` linear sobre a lista `best` (comportamento quadrático no número de variações) — [WorkoutPresentation.kt](app/src/main/java/com/mooscles/WorkoutPresentation.kt), [domain/WorkoutCalculations.kt](app/src/main/java/com/mooscles/domain/WorkoutCalculations.kt)
- O próprio projeto reconhece que o "Broad snapshots still include archived sets; runtime traces are needed to decide whether further data partitioning is warranted" — [VERIFICATION.md](VERIFICATION.md)
- O vaivém de thread entre as versões (1.4.3 moveu para `Dispatchers.Default`, 1.4.4 voltou para a main) mostra que a decisão continua aberta. Os dados de emulador não comprovaram melhora em nenhuma das direções — [PERFORMANCE.md](PERFORMANCE.md), [README.md](README.md) linhas 15–17
- Scripts só para Windows: `build.ps1` chama `.\gradlew.bat` e usa `.tooling\` com separador `\`. `tools/prepare-release.ps1` procura `aapt.exe` e `apksigner.bat`. `tools/publish-release.ps1` exige `gh`. O AGENTS.md diz que no macOS se chama o Gradle direto e se usa `pwsh` só para release — [build.ps1](build.ps1), [tools/prepare-release.ps1](tools/prepare-release.ps1), [tools/publish-release.ps1](tools/publish-release.ps1), [AGENTS.md](AGENTS.md)
- Também presos ao Windows: o caminho de evidência em VERIFICATION.md (`D:\Programing\Mooscles\...`) e o emulador quebrando com "Windows exception 0xC0000005" / "hypervisor driver is not installed" — [VERIFICATION.md](VERIFICATION.md), [PERFORMANCE.md](PERFORMANCE.md)
- Sem DI (service locator via cast de `Application`), rotas de navegação em string com interpolação (`nav.navigate("workoutDetail/$id?summary=true")`) e telas que recebem o `UiState` inteiro — [MoosclesApplication.kt](app/src/main/java/com/mooscles/MoosclesApplication.kt), [ui/MoosclesApp.kt](app/src/main/java/com/mooscles/ui/MoosclesApp.kt)
- Identidade de distribuição frágil: o APK de distribuição é **debug**, assinado por "a chave debug local existente (fora do git)". Trocar a chave impede updates. O fingerprint público é `0cd093b2…bb35` e "Moving future distributions to production signing requires a signing compatibility decision first" — [AGENTS.md](AGENTS.md), [UPDATES.md](UPDATES.md)
- Arquivos grandes e densos: `WorkoutRepository.kt` (515 linhas, 29 KB), `ui/WorkoutScreens.kt` (496 linhas, 33,7 KB) e `updates/UpdateManager.kt` (423 linhas, 26 KB). O código usa linhas longas, com vários comandos por linha — `wc -l`/`wc -c` nos arquivos citados
- `MainActivity` guarda o flag `notification_requested` em `getPreferences(MODE_PRIVATE)`, que é SharedPreferences, ou seja, um terceiro mecanismo de persistência além de Room e DataStore — [MainActivity.kt](app/src/main/java/com/mooscles/MainActivity.kt)
- Timer: no Android 12+ sem acesso a alarme exato, o alarme "can arrive late under power saving". Reboot ou force-stop cancelam o alarme do SO, e o timer é restaurado ao reabrir o app — [README.md](README.md) linha ~90
- O manifest pede `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, `REQUEST_INSTALL_PACKAGES`, `SCHEDULE_EXACT_ALARM` e `POST_NOTIFICATIONS` — [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml)

### Inferences
- O travamento de inicialização provavelmente vem da soma de três coisas: `initialize()` com `synchronizeRoutineTargets()` sobre `snapshot()` completo, a primeira emissão do snapshot completo e o primeiro `present()` na main construindo histórico, recordes e PRs do treino ativo. Os documentos só atribuem o problema explicitamente ao "UI-thread PR calculation path"; as outras duas partes são inferência minha a partir do código.
- Um port para iOS (ou uma refatoração KMP) é uma boa ocasião para trocar o "snapshot de tudo + recálculo" por consultas incrementais, recordes materializados ou índices persistentes. Ainda assim, a regra de coerência atômica descrita no comentário do repositório precisa ser preservada.

### Gaps
- Não reproduzi o travamento nem medi `initialize()` separadamente. Não há trace de aparelho físico no repositório; as evidências citadas ficam em diretórios ignorados (`app/build/outputs/...`, `.tooling/`) que não li.

## 6. Backend e chamadas de rede

### Takeaway
Não há backend, conta, analytics nem sincronização. Toda a rede do app é do updater: uma chamada à API pública do GitHub Releases (`ReiTut/Mooscles`), o download do `update.json` e o download do APK de hosts do GitHub numa allowlist. Tudo usa `HttpURLConnection` puro, sem OkHttp/Retrofit. O compartilhamento é local (PNG + Intent).

### Cited Findings
- `ReleaseUrls.REPOSITORY = "ReiTut/Mooscles"`, `API_URL = "https://api.github.com/repos/$REPOSITORY/releases?per_page=100"`. O link de release deve ser `github.com/ReiTut/Mooscles/releases/tag/...` — [updates/UpdateModels.kt](app/src/main/java/com/mooscles/updates/UpdateModels.kt) linhas 92–98
- `ReleaseClient`:
  - usa `HttpURLConnection` com `connectTimeout = 15_000`, `readTimeout = 20_000`, `Accept: application/vnd.github+json` e `User-Agent: Mooscles-Android-Updater`;
  - percorre até 100 releases e ignora draft, prerelease (salvo opt-in) e release sem `published_at`;
  - exige um asset único `update.json` com `state == "uploaded"` e valida o APK correspondente e o checksum;
  - quando a API está indisponível, faz backoff de 6 h;
  - fonte: [updates/ReleaseClient.kt](app/src/main/java/com/mooscles/updates/ReleaseClient.kt).
- Download do APK: `HttpURLConnection` com timeouts de 20/25 s, só para hosts `github.com`, `objects.githubusercontent.com`, `release-assets.githubusercontent.com` e `github-releases.githubusercontent.com` — [updates/ApkDownload.kt](app/src/main/java/com/mooscles/updates/ApkDownload.kt)
- Verificação antes de instalar: mesmo package, `versionCode` maior que o instalado, versionCode, versionName e minSdk iguais aos do `update.json`, tamanho, SHA-256 (`MessageDigest`) e assinatura compatível (`signingInfo`). A instalação usa sessão `PackageInstaller` e espera não haver treino ativo nem timer rodando — [updates/ApkVerifier.kt](app/src/main/java/com/mooscles/updates/ApkVerifier.kt), [updates/UpdateInstaller.kt](app/src/main/java/com/mooscles/updates/UpdateInstaller.kt), [UPDATES.md](UPDATES.md)
- Agendamento: checagem ao voltar para o primeiro plano (com throttling) e `PeriodicWorkRequest` de 6 h com `NetworkType.CONNECTED`. O download exige `UNMETERED` quando "Wi-Fi only" está ligado, que é o padrão — [updates/UpdateManager.kt](app/src/main/java/com/mooscles/updates/UpdateManager.kt) linhas 88–106 e 197–201, [UPDATES.md](UPDATES.md)
- "Downloads contain only APK release assets. No workout data is uploaded. Offline routines, workouts, history, PRs, and the rest timer continue to work" — [UPDATES.md](UPDATES.md)
- O texto de privacidade no app diz que os dados ficam no aparelho e que "App connections check and download updates from GitHub" — [ui/MoosclesApp.kt](app/src/main/java/com/mooscles/ui/MoosclesApp.kt) linha 198
- O compartilhamento não usa rede própria: gera PNG de 1080 px no cache privado e entrega via `FileProvider` com permissão temporária ao WhatsApp/Sharesheet. Exports com mais de 7 dias são limpos — [AGENTS.md](AGENTS.md), [README.md](README.md) linha 57, [sharing/WorkoutShareIntent.kt](app/src/main/java/com/mooscles/sharing/WorkoutShareIntent.kt)
- Permissões de rede: `INTERNET` e `ACCESS_NETWORK_STATE` — [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml)

### Inferences
- Para iOS, a camada de rede inteira desaparece (a App Store/TestFlight cuida das atualizações) ou, no máximo, vira uma checagem informativa de versão. O app iOS seria 100% offline, sem necessidade de backend.
- O `update.json` e o `REPOSITORY` são contrato com builds Android já instalados (AGENTS.md). Qualquer reorganização do repositório para acomodar iOS (monorepo, renomear releases, mudar assets) precisa manter esses assets e tags compatíveis, ou os apps Android existentes deixam de achar updates.

### Gaps
- Não verifiquei se `org.json` (usado em `ReleaseClient`/`UpdateModels`) roda nos testes de host via Robolectric ou via stubs. É irrelevante para iOS, porque esse código não seria portado.
