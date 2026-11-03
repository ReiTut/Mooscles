# Fase 1B: módulo `:shared` em Kotlin Multiplatform, plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Levar dados, regras, preferências, a persistência do timer, o contrato de UI, a apresentação e a lógica do `AppViewModel` para um módulo `:shared` (Android + `iosArm64` + `iosSimulatorArm64`), sem nenhuma mudança visível e sem tocar nos dados de quem já usa o app, com o iOS compilando e rodando `commonTest` no CI.

**Architecture:** Primeiro nasce o `:shared` com o job iOS do CI, já hospedando as funções que substituem as APIs da JVM (`UUID`, relógio, `Instant`, `String.format`, `Math.ulp`, `Normalizer`). Cada substituição entra ainda com o código antigo no `:app`, com um teste de equivalência contra a JVM. Depois o código se move de baixo para cima no grafo de dependências: o bloco `data`/`domain`/`backup` (que depende de si mesmo e vai junto, com o Room KMP), os testes puros para `commonTest`, o contrato de UI com preferências e timer, e por fim o `AppViewModel`, dividido entre uma classe comum e uma subclasse Android. Cada task deixa o app compilando e os testes verdes. A prova final é a atualização por cima no emulador, como na 1A.

**Tech Stack:** Kotlin 2.4.21 Multiplatform, AGP 9.3.3 (`com.android.kotlin.multiplatform.library`), Gradle 9.7.1, KSP 2.3.12, Room 2.8.5 com o plugin Gradle `androidx.room`, androidx.sqlite 2.6.2 (`sqlite-framework` no Android, `sqlite-bundled` no iOS), DataStore Preferences 1.3.0, Lifecycle ViewModel 2.11.0, kotlinx.serialization 1.12.0, coroutines 1.11.0, `kotlin.test`, JUnit 4 + Robolectric no `:app`, GitHub Actions (`macos-26`, Xcode 26.4).

**Spec:** [docs/superpowers/specs/2026-10-27-phase1-shared-base-design.md](../specs/2026-10-27-phase1-shared-base-design.md) (etapa 1B). Resultado da 1A: [relatório](../reports/2026-10-27-phase1a-verificacao.md) e `AGENTS.md`.

## Decisões travadas por este plano

As decisões marcadas com **(minha escolha)** são as que a spec deixou em aberto.

1. **Gradle.** O root declara `id("com.android.kotlin.multiplatform.library") version "9.3.3" apply false` e `id("androidx.room") version "2.8.5" apply false`. O KGP e o KSP já estão no `buildscript` (fixados na 1A), então o `:shared` aplica `org.jetbrains.kotlin.multiplatform` e `com.google.devtools.ksp` só pelo id, sem versão, porque o Gradle recusa versão para plugin que já está no classpath. O bloco é `kotlin { android { … } }` (o `androidLibrary {}` está deprecado desde o AGP 9.1), com `namespace = "com.mooscles.shared"`, `compileSdk = 37`, `minSdk = 26`, `withHostTest { isIncludeAndroidResources = true }` e `compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }`, o mesmo nível do `:app`. O namespace não pode ser `com.mooscles`, para não ter dois `R` no mesmo pacote. No `gradle.properties` entra `kotlin.native.ignoreDisabledTargets=true`, para o job Linux configurar o projeto sem aviso dos alvos iOS.
2. **Source sets.** São `commonMain`, `androidMain`, `iosMain`, `commonTest`, `androidHostTest` (testes de equivalência contra a JVM, JUnit 4 sem Robolectric) e `iosTest`. A tarefa de testes de host é `:shared:testAndroidHostTest`. Se o AGP 9.3.3 der outro nome, confira com `./gradlew :shared:tasks --all | grep -i hosttest`, use o nome real e registre no commit. Os pacotes continuam os de hoje (`com.mooscles.data`, `.domain`, `.backup`, `.timer`, `.ui`, `com.mooscles`). Um pacote pode ter arquivos nos dois módulos.
3. **Room KMP.** O plugin `androidx.room` usa `room { schemaDirectory("$projectDir/schemas") }`, sem o argumento KSP `room.schemaLocation`, porque o plugin recusa os dois juntos. O `room-compiler` 2.8.5 entra em `kspAndroid`, `kspIosArm64` e `kspIosSimulatorArm64`, e nunca em `kspCommonMainMetadata`. `WorkoutDatabase` ganha `@ConstructedBy(WorkoutDatabaseConstructor::class)` e `@Suppress("KotlinNoActualForExpect") expect object WorkoutDatabaseConstructor : RoomDatabaseConstructor<WorkoutDatabase>`. Se o compilador avisar que classes expect/actual estão em Beta, entra `-Xexpect-actual-classes`. **O driver é injetado pelo builder, sem expect/actual (minha escolha).** O código comum tem `fun RoomDatabase.Builder<WorkoutDatabase>.buildMooscles(driver: SQLiteDriver): WorkoutDatabase`, que aplica `setDriver`, as quatro migrações e o callback `onCreate`, sem `setQueryCoroutineContext`, para o Android manter o executor padrão do Room. O `androidMain` do `:shared` tem `fun WorkoutDatabase.Companion.open(context: Context): WorkoutDatabase` com `AndroidSQLiteDriver()` e `WorkoutDatabase.FILE_NAME = "mooscles.db"`. É o único `Context` do `:shared` (**minha escolha**: o jeito de abrir o banco em cada plataforma fica junto do banco). O iOS usa `BundledSQLiteDriver` (Task 8).
4. **DataStore (minha escolha).** O delegate `val Context.moosclesStore by preferencesDataStore(name = MoosclesStore.NAME)` **fica no `:app`**, em `app/src/main/java/com/mooscles/AppPreferences.kt`. O Android continua com a mesma instância única por processo, o mesmo `FileStorage` e o mesmo caminho `files/datastore/local_preferences.preferences_pb`, sem nenhum código novo de abertura. Os testes que isolam o store por reflexão em `com.mooscles.AppPreferencesKt.moosclesStore$delegate` continuam funcionando. O código comum recebe `DataStore<Preferences>` pronto: `AppPreferences(store)` e `RestTimerPersistence(store, now)`. O timer não tem arquivo próprio: ele usa as chaves `timer_*` dentro de `local_preferences`. O `app_updates` continua no `:app`, com o updater. O iOS ganha uma fábrica `createMoosclesStore(directory, scope)` em `iosMain` (Task 8).
5. **`AppViewModel` (minha escolha).** A lógica vai para `abstract class MoosclesViewModel(services: MoosclesServices, platform: ViewModelPlatform) : ViewModel(), MoosclesActions` em `commonMain`, que usa o `viewModelScope` do Lifecycle KMP. O que é Android entra por interfaces:
   - `RestTimer` (implementada pelo `RestTimerController`);
   - `UpdateCoordinator` (implementada pelo `UpdateManager`);
   - `BackupDocumentStore` (implementada pelo `AndroidBackupDocuments`, que chama o `BackupDocuments` atual e classifica `IOException` e `SecurityException`);
   - `ViewModelPlatform` (o `Trace`, o `Build.VERSION` e o `Looper`);
   - `BackupApp` (com o `BuildConfig`).

   A instalação, que devolve um `Intent`, fica num método abstrato que a subclasse Android implementa. O `AppViewModel(application: Application)` do `:app` passa a ser essa subclasse, com `installIntents` e as sondas de teste `presentedCount`/`presentedOnMainThread`. Como ele deixa de ser `AndroidViewModel`, a `MainActivity` passa a usar `viewModels { AppViewModel.Factory }`.
6. **Substituição das APIs da JVM.** Todas são em Kotlin puro e cada uma tem teste de equivalência na JVM e uma tabela fixa em `commonTest`, que roda também no iOS:
   - `UUID.randomUUID().toString()` vira `kotlin.uuid.Uuid.random().toString()`;
   - `System.currentTimeMillis()` vira `kotlin.time.Clock.System.now().toEpochMilliseconds()`;
   - `java.time.Instant.now().toString()` vira `kotlin.time.Clock.System.now().toString()`. **Minha escolha:** sem dependência do `kotlinx-datetime`, porque desde a 0.7 o `Instant` dele é o `kotlin.time.Instant` e o código movido não usa nenhum tipo de calendário. O `LocalDate` do nome do arquivo de backup fica no `:app`;
   - `Math.ulp` vira `kotlin.math.ulp`;
   - `String.format(Locale.US, "%.2f", x)` vira `DecimalText.fixed2(x)`, aritmética própria (Task 2);
   - `Normalizer` vira `SearchFolding.fold`, com uma tabela gerada da JVM e conferida para todos os code points (Task 3). **Minha escolha:** tabela em vez de `expect/actual`, porque só assim o teste compara com a JVM de verdade;
   - `@Volatile`/`@Synchronized` em `writeTransaction` viram `kotlin.concurrent.atomics.AtomicReference`;
   - `AtomicInteger` e `System.identityHashCode` saem do código comum (decisão 5).
7. **Ordem.** Os movimentos vão do fundo do grafo para cima, e cada um deixa o `:app` compilando:
   1. o módulo e o CI, com as funções folha (ids e relógio);
   2. os números;
   3. a busca;
   4. o bloco `data`+`domain`+`backup`, que forma um ciclo e se move inteiro, com o Room e os schemas;
   5. os testes puros;
   6. o contrato de UI, as preferências e o timer;
   7. a apresentação e o ViewModel;
   8. as entradas iOS;
   9. o teste instrumentado de instalação antiga;
   10. a prova no emulador.
8. **Testes.** Vão para `commonTest`: cálculos (`domain/*`), backup (`BackupCodecTest`, `BackupTextsTest`, `ImportPlannerTest`), `CatalogTest`, `EditRulesTest`, `ExerciseSearchTest`, `RestTimerStateTest` e `WorkoutPresentationTest`. Ficam na JVM:
   - os testes que leem recurso por classloader, os que mudam o `Locale` padrão e os limites de tempo de `WorkoutPresentationTest` (**minha escolha**: o simulador iOS em debug não tem os tempos da JVM). Eles vão para `androidHostTest`;
   - tudo o que usa Room, Robolectric, DataStore em disco ou `MoosclesApplication`. Fica no `:app`, inclusive os testes de migração, como manda a spec.

   O `LargeHistoryFixture` vai para `shared/src/testFixtures/kotlin`, um diretório que entra no `commonTest` do `:shared` e no `test` do `:app`.
9. **CI iOS.** É um job novo, `ios`, em `runs-on: macos-26` (arm64), com `sudo xcode-select -s /Applications/Xcode_26.4.app`. O Xcode 26.4 é o que a tabela de compatibilidade do Kotlin lista para 2.4.20–2.4.21. Antes de fixar, confira no README da imagem `macos-26` do `actions/runner-images` que esse Xcode existe. O job:
   - instala o SDK Android como o job `host`, porque o Gradle configura o `:app` e o `:shared` também no Mac;
   - usa `gradle/actions/setup-gradle` com o mesmo pin e o mesmo `cache-provider: basic`;
   - guarda `~/.konan` em `actions/cache`, com chave `konan-${{ runner.os }}-${{ hashFiles('build.gradle.kts', 'shared/build.gradle.kts', 'gradle/wrapper/gradle-wrapper.properties') }}`;
   - roda `./gradlew :shared:compileKotlinIosArm64 :shared:iosSimulatorArm64Test --console=plain --continue`;
   - resume os XML de `shared/build/test-results/iosSimulatorArm64Test` no `GITHUB_STEP_SUMMARY`;
   - sobe os relatórios quando falha.

   Ele roda em todo push e PR, como o `host`.
10. **Prova final.** São duas tasks no emulador:
    - um teste instrumentado abre um `mooscles.db` v5 e um `local_preferences.preferences_pb` criados pelo APK de antes da 1B, como a spec pede e a 1A não fez;
    - a atualização por cima, igual à da 1A: dump, preferências e export idênticos, e treino ativo com timer retomado.

## Global Constraints

- Branch `chore/phase1b-shared-module`, a partir da `main` do fork, que já tem a 1A. O push vai só para `origin`, sem force. O merge na `main` acontece com o CI verde: `host`, `ios` e o `release-script` quando ele rodar.
- **Dados intactos.** Continuam iguais:
  - `mooscles.db` no mesmo lugar, `version` 5, os nomes de tabelas e colunas, e os schemas JSON byte a byte;
  - `files/datastore/local_preferences.preferences_pb` (preferências e timer) e `files/datastore/app_updates.preferences_pb`, com as mesmas chaves e os mesmos tipos;
  - `applicationId = com.mooscles`, `versionCode` 16, `versionName` 1.4.5, a assinatura, o `update.json` e `ReleaseUrls`.
- **Nenhuma mudança perceptível.** Nada muda em tela, texto, comportamento ou desempenho. O APK debug pode crescer no máximo cerca de 1 MB em relação ao de antes da 1B.
- **Drivers:**
  - Android: `AndroidSQLiteDriver`, com `androidx.sqlite:sqlite-framework:2.6.2` (fixado; a linha 2.7 é do Room 3);
  - iOS: `BundledSQLiteDriver`, com `androidx.sqlite:sqlite-bundled:2.6.2`.

  Room 2.8.5 nos dois. Sem Room 3, SKIE, Swift Export ou `iosApp/`.
- **Versões.** Nenhuma biblioteca da 1A muda. As únicas novas são `sqlite-bundled` 2.6.2 e `androidx.annotation:annotation` na estável mais recente com suporte KMP no dia, registrada no commit.
- **`:app`** mantém o nome, o `applicationId` e o caminho `app/build/outputs/apk/debug/app-debug.apk`.
- **Código comum puro.** `grep -rnE "\b(java|javax|android)\.|System\.|Math\.|String\.format|@Synchronized|@JvmStatic" shared/src/commonMain` tem que voltar vazio. O compilador de metadados (`:shared:compileCommonMainKotlinMetadata`) é a prova local, e o job `ios` é a prova final.
- **Nomes de fachada.** No mesmo pacote, nenhum arquivo do `:shared` com declaração de nível superior pode ter o mesmo nome de um arquivo do `:app` também com declaração de nível superior. Os dois gerariam a mesma classe `XxxKt`, e o D8 falha. Em particular, o `AppPreferences.kt` do `:shared` contém só a classe. O `toBackup()` vai para `PreferencesBackup.kt`, e `com.mooscles.AppPreferencesKt` continua sendo do `:app`.
- **Transações.** Só por `WorkoutDatabase.writeTransaction { … }`. Nunca capture exceção dentro de uma transação de escrita.
- **Schemas.** Nunca edite um JSON de schema. Eles mudam de lugar só com `git mv`, e o build tem que gerar `5.json` idêntico.
- **Movimentos.** Use `git mv` e edite o mínimo, para a revisão ver renomeações (`git diff -M --stat`). A ordem dos membros do `AppViewModel` e do `WorkoutPresentation` não muda.
- **Testes:**
  - nenhuma asserção é enfraquecida;
  - a soma de `:app:testDebugUnitTest` com `:shared:testAndroidHostTest` nunca fica abaixo de 487 (o total na cabeça da 1A; a spec pede 482 ou mais);
  - na conversão para `commonTest`:
    - `org.junit.Test` vira `kotlin.test.Test`;
    - `@Before`/`@After` viram `@BeforeTest`/`@AfterTest`;
    - `Assert.*` vira `kotlin.test.*`, com a mensagem passando de primeiro para último argumento;
    - `assertThrows(X::class.java) { }` e `@Test(expected = X::class)` viram `assertFailsWith<X> { }`;
    - `assertArrayEquals` vira `assertContentEquals`;
    - `Math.nextUp`/`nextDown`/`ulp` viram `kotlin.math`;
    - `measureTimeMillis` vira `kotlin.time.measureTime`.
- **Processo.** Durante cada task, rode só os testes da área mexida. Faça o push depois de cada commit; o CI roda a suíte inteira e o job iOS. Um job vermelho bloqueia a revisão da task. Este Mac não tem Xcode, então tudo o que é iOS se prova no CI. O `compat-reviewer` é obrigatório nas Tasks 1, 4, 6, 7 e 8 e tem que dar `SEGURO` antes do commit.
- Código, comentários e mensagens de commit em inglês.

## Review Focus

1. **Treino ativo com séries completas e o timer de descanso rodando na hora da troca de APK.** Depois da troca, o treino retoma e o timer termina no prazo salvo. Coberto pela Task 9 (`legacyPreferencesAndTimerReadBack`, com o relógio fixo no instante da captura) e pela Task 10, Step 2.
2. **Uma carga cuja exibição em lb ou kg cai exatamente num empate `.xx5` (2.675) ou carrega ruído de ida e volta (`45.00000000000001`).** O texto "previous" e os selos de PR ficam iguais aos de hoje. Coberto na Task 2 por `fixed2EqualsStringFormatAroundEveryTie` e `fixed2EqualsStringFormatOnUnitConversions`, e pela tabela de `DecimalTextTest`, que inclui esses dois valores.
3. **Nome de exercício próprio com acentos, marcas combinantes (inclusive fora do BMP, como U+E0100), `İ` turco ou o sinal Kelvin.** A busca acha os mesmos exercícios de hoje. Coberto na Task 3 por `foldEqualsNormalizerForEveryCodePoint`, `foldEqualsNormalizerForRandomStrings` e `searchMatchesAreUnchangedForCatalogAndAliases`, e na Task 5 por `ExerciseSearchLocaleTest`.
4. **Uma segunda instância de DataStore sobre `local_preferences`**, por exemplo um código comum que abre o próprio store. Isso derruba o app com "multiple DataStores active" ou perde escritas do timer. Coberto na Task 6 por `factoryWritesThroughTheDelegateStore` e `delegateFileIsFilesDatastoreLocalPreferences`, e pela regra da decisão 4.
5. **O Room gerando um `5.json` diferente nos três alvos KSP, ou o caminho `@ConstructedBy` mudando a abertura do banco.** Coberto na Task 4: o passo do CI que exige `shared/schemas` sem diff depois do build, o `MigrationEquivalenceTest` (que compara o dump da instalação nova e das migrações com as referências da 1A) e o `productionOpenUsesMoosclesDbInTheDatabasesDirectory`.

---

### Task 1: Módulo `:shared`, CI iOS e as funções folha (ids e relógio)

**Files:**
- Modify: `settings.gradle.kts` (`include(":app", ":shared")`), `build.gradle.kts` (os dois plugins da decisão 1), `gradle.properties`, `app/build.gradle.kts` (`implementation(project(":shared"))`), `build.ps1` (`:shared:testAndroidHostTest` na lista padrão de `$Tasks`), `.github/workflows/ci.yml`
- Create: `shared/build.gradle.kts` (sem KSP e sem Room nesta task). Dependências:
  - `commonMain`: `api` do `kotlinx-coroutines-core` 1.11.0;
  - `commonTest`: `implementation(kotlin("test"))` e `kotlinx-coroutines-test` 1.11.0;
  - `androidHostTest`: `implementation(kotlin("test-junit"))` e `junit:junit:4.13.2`.
- Create: `shared/src/commonMain/kotlin/com/mooscles/data/Ids.kt`, `shared/src/commonMain/kotlin/com/mooscles/domain/Time.kt`
- Modify: `app/src/main/java/com/mooscles/data/Models.kt` (sai `newId` e o import de `UUID`; `startedAt = currentTimeMillis()`), `data/WorkoutRepository.kt` (os padrões de `now` em `startEmpty`, `startRoutine` e `finishWorkout`), `domain/WorkoutCalculations.kt` (`summary`), `timer/RestTimerPersistence.kt` (`now: () -> Long = ::currentTimeMillis`), `AppViewModel.kt` (`currentIsoInstant()` no lugar de `Instant.now().toString()`)
- Test: `shared/src/commonTest/kotlin/com/mooscles/data/IdsTest.kt`, `shared/src/androidHostTest/kotlin/com/mooscles/data/IdsJvmEquivalenceTest.kt`, `shared/src/androidHostTest/kotlin/com/mooscles/domain/TimeJvmEquivalenceTest.kt`

**Interfaces:**
- Produces:
  - `fun newId(): String` em `com.mooscles.data`, que devolve `Uuid.random().toString()`;
  - `fun currentTimeMillis(): Long` e `fun currentIsoInstant(): String` em `com.mooscles.domain`.

  Use `@OptIn` no arquivo se `Uuid` ou `Clock` ainda forem experimentais no Kotlin 2.4.21.
- Produces: o job `ios` (decisão 9). No job `host`:
  - o comando passa a `./gradlew :app:testDebugUnitTest :shared:testAndroidHostTest :app:lintDebug :app:assembleDebug --console=plain --continue`, mais o lint Android do `:shared`, se o plugin registrar um (`./gradlew :shared:tasks --all | grep -i lint`);
  - o resumo soma `app/build/test-results/testDebugUnitTest` e `shared/build/test-results/testAndroidHostTest` e falha se o total ficar abaixo de `MIN_HOST_TESTS: 487`;
  - o upload de relatórios inclui `shared/build/reports/` e `shared/build/test-results/`.

- [ ] **Step 1: Testes que falham.** Escrever:
  - `IdsTest.newIdIsALowercaseVersionFourUuid`: 1.000 ids, todos casando `^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`;
  - `IdsTest.newIdsAreDistinct`: 1.000 ids, todos distintos;
  - `IdsJvmEquivalenceTest.newIdParsesAsJavaUuidWithSameText`: para 1.000 ids, `UUID.fromString(id).toString() == id`, `version() == 4` e `variant() == 2`;
  - `TimeJvmEquivalenceTest.instantTextMatchesJavaTime`: `Instant.fromEpochMilliseconds(ms).toString() == java.time.Instant.ofEpochMilli(ms).toString()` para 0, 1, 999, 1000, 1_000_000, 1_793_000_000_000 e 10.000 valores de `Random(1027)` em `[0, 4_102_444_800_000)`. Também `Instant.fromEpochSeconds(s, n)` contra `ofEpochSecond(s, n)` para `n` em {0, 1_000_000, 120_000_000, 123_456_000, 123_456_789};
  - `TimeJvmEquivalenceTest.currentTimeMillisTracksTheSystemClock`: `abs(currentTimeMillis() - System.currentTimeMillis()) <= 50`.
- [ ] **Step 2:** Rodar `./gradlew :shared:testAndroidHostTest` e ver falhar, porque as funções ainda não existem.
- [ ] **Step 3:** Criar o módulo e as funções, trocar os usos no `:app` e editar o `ci.yml` e o `build.ps1`.
- [ ] **Step 4: Verificar**, localmente:
  - `./gradlew :shared:testAndroidHostTest :shared:compileCommonMainKotlinMetadata` verde;
  - `./gradlew :app:testDebugUnitTest --tests "com.mooscles.data.*" --tests "com.mooscles.timer.*" --tests "com.mooscles.AppViewModelBackupHostTest" :app:assembleDebug` verde;
  - `./gradlew :shared:compileKotlinIosSimulatorArm64`, que pode falhar só por falta de Xcode. Nesse caso o CI decide.
- [ ] **Step 5:** Rodar o `compat-reviewer` (`SEGURO`), fazer o commit com `build: add the :shared multiplatform module and its iOS CI job` e o push. Conferir com `gh run watch` que os jobs `host` (com o total de testes no resumo) e `ios` ficam verdes.

### Task 2: Texto decimal e `ulp` em Kotlin puro

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mooscles/domain/DecimalText.kt`
- Modify: `app/src/main/java/com/mooscles/WorkoutPresentation.kt` (o `String.format` do "previous" e o `Math.ulp` do `sameLoad`; sai o import de `Locale`), `app/src/main/java/com/mooscles/domain/WorkoutCalculations.kt` e `domain/RoutineTargetRecords.kt` (`Math.ulp(x)` vira `x.ulp`)
- Test: `shared/src/commonTest/kotlin/com/mooscles/domain/DecimalTextTest.kt`, `shared/src/androidHostTest/kotlin/com/mooscles/domain/DecimalTextJvmEquivalenceTest.kt`, `app/src/androidTest/java/com/mooscles/DecimalTextDeviceTest.kt`

**Interfaces:**
- Produces: `object DecimalText { fun fixed2(value: Double): String }`, igual a `String.format(Locale.US, "%.2f", value)` para NaN, ±Infinity e todo `value` finito com `|value| < 1e13`. Acima disso, devolve `value.toString()`, uma divergência documentada no KDoc que nunca acontece, porque o `EditRules` limita a carga a 100.000 kg.

  O algoritmo reproduz o arredondamento `HALF_UP` do `Formatter` sobre os dígitos curtos do double, sem depender do `toString` da plataforma:
  1. o sinal sai à parte, e `-0.0` e os negativos que arredondam para zero dão `"-0.00"`;
  2. com `a = |value|`, o empate entre os centavos `k` e `k+1` é `t = (2k+1).toDouble() / 200.0`. A divisão IEEE é arredondada corretamente, então `t` é o double mais próximo do empate decimal;
  3. se `a == t`, arredonda para cima;
  4. senão, compara `a` com `t` para escolher o lado. Teste `k` e os vizinhos de `floor(a * 100)`, porque o produto tem ruído;
  5. formata os centavos (`Long`) como `"<inteiro>.<dois dígitos>"`.

- [ ] **Step 1: Testes que falham.**
  - `DecimalTextTest.fixed2MatchesTheJavaFormatterTable`: uma tabela literal. Os valores esperados vêm de `String.format(Locale.US, "%.2f", v)` na JVM, colados como texto. A tabela tem pelo menos: 0.0→"0.00", -0.0→"-0.00", 0.005→"0.01", 0.015→"0.02", 1.005→"1.01", 2.675→"2.68", 999.995→"1000.00", -0.001→"-0.00", 1e-7→"0.00", 45.00000000000001→"45.00", 44.99999999999999→"45.00", `WeightUnits.kgToLb(100000.0)`, NaN→"NaN", +∞→"Infinity" e -∞→"-Infinity".
  - `DecimalTextTest.ulpMatchesTheJavaTable`: `x.ulp.toRawBits()` contra os bits de `Math.ulp` colados como literais, para 0.0, -0.0, `Double.MIN_VALUE`, 2.2250738585072014E-308, 1.0, 20.0, 100.0, 45.359237, 102.05828325, 100000.0, `Double.MAX_VALUE`, +∞ e NaN.
  - `DecimalTextJvmEquivalenceTest`, cada um comparando `DecimalText.fixed2(v)` com `String.format(Locale.US, "%.2f", v)`:
    - `fixed2EqualsStringFormatOnEveryThousandthUpTo2000`: `v = k / 1000.0` para `k` em `0..2_000_000`;
    - `fixed2EqualsStringFormatAroundEveryTie`: `t = (2k+1) / 200.0`, `t.nextUp()` e `t.nextDown()`, com os dois sinais, para `k` em `0..400_000`;
    - `fixed2EqualsStringFormatOnUnitConversions`: `fromKg(kg, true)` para kg de 0 a 100.000 em passos de 0,25, e `fromKg(lbToKg(lb), true)` para lb de 0 a 220.000 em passos de 0,25;
    - `fixed2EqualsStringFormatOnRandomDoubles`: 200.000 valores de `Random(1027)`, com magnitudes de 1e-9 a 1e13 e os dois sinais;
    - `fixed2EqualsStringFormatOnSpecialValues`;
    - `ulpEqualsMathUlp`: os mesmos conjuntos de valores mais 100.000 doubles de bits aleatórios.
  - `DecimalTextDeviceTest.fixed2EqualsStringFormatOnArt`: o mesmo contra o `Formatter` do ART, com `k` em `0..200_000` e os empates até `k = 100_000`.
- [ ] **Step 2:** Rodar `./gradlew :shared:testAndroidHostTest --tests "*DecimalText*"` e ver falhar.
- [ ] **Step 3:** Implementar `DecimalText.fixed2` e trocar os usos no `:app`.
- [ ] **Step 4: Verificar.**
  - `./gradlew :shared:testAndroidHostTest --tests "*DecimalText*"` verde;
  - `./gradlew :app:testDebugUnitTest --tests "com.mooscles.domain.*" --tests "com.mooscles.WorkoutPresentationTest" --tests "com.mooscles.HistoryRecordsHostTest" --tests "com.mooscles.AppViewModelRecordsHostTest"` verde.

  Se algum valor divergir da JVM, **pare e reporte**, sem ajustar o teste. Se houver um emulador ligado, rode `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mooscles.DecimalTextDeviceTest`. Senão, a Task 10 roda.
- [ ] **Step 5:** Fazer o commit com `domain: pure-Kotlin fixed decimal text and ulp, proven equal to the JVM` e o push.

### Task 3: Busca de exercícios sem `java.text.Normalizer`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mooscles/data/SearchFolding.kt` (a função), `shared/src/commonMain/kotlin/com/mooscles/data/SearchFoldingTable.kt` (gerado; o cabeçalho diz como gerar e que não se edita à mão)
- Modify: `app/src/main/java/com/mooscles/data/ExerciseSearch.kt` (o `normalize` passa a chamar `SearchFolding.fold(value)` e depois aplica as mesmas `equivalents`; saem os imports de `java.text` e `java.util`)
- Test: `shared/src/commonTest/kotlin/com/mooscles/data/SearchFoldingTest.kt`, `shared/src/androidHostTest/kotlin/com/mooscles/data/SearchFoldingJvmEquivalenceTest.kt`, `app/src/test/java/com/mooscles/data/ExerciseSearchEquivalenceTest.kt`, `app/src/androidTest/java/com/mooscles/SearchFoldingDeviceTest.kt`

**Interfaces:**
- Produces: `object SearchFolding { fun fold(value: String): String }`, igual, para toda string, a `Regex("[^a-z0-9]+").replace(Regex("\\p{M}+").replace(Normalizer.normalize(value, NFD), "").lowercase(Locale.ROOT), " ").trim()`.
  - A função percorre code points (decodifica pares de surrogates; um surrogate solto é separador). ASCII `A–Z` vira minúscula, e `a–z0-9` fica igual. Os outros caracteres vão à tabela: a substituição é `""` para as marcas e as letras ASCII para os caracteres que decompõem nelas (`é`→`e`, `İ`→`i`, `K` Kelvin→`k`, `Å` Angstrom→`a`). Fora da tabela, o caractere é separador. No fim, as sequências de separadores viram um espaço e as pontas são cortadas.
  - A tabela guarda intervalos (`IntArray` de inícios e fins mais as substituições). Ela é gerada pelo teste `SearchFoldingJvmEquivalenceTest.writeTableWhenRequested`, que só escreve quando `MOOSCLES_WRITE_FOLDING_TABLE=<arquivo>` está definida, seguindo o padrão do `MigrationEquivalenceTest`. O teste aplica a cadeia antiga a cada code point de 0 a 0x10FFFF sozinho.
  - O cabeçalho registra a versão do Unicode do JDK usado (o 21 tem Unicode 15.0).

- [ ] **Step 1: Testes que falham.**
  - `SearchFoldingTest.foldMatchesTheJvmTable`: "Élévation"→"elevation", "Café crème"→"cafe creme", "é"→"e", "İstanbul"→"istanbul", "Kettlebell"→"kettlebell", "Straße"→"stra e", "ﬁ"→"", "Ｐｕｌｌ"→"", "한국"→"", "pull-up_2x"→"pull up 2x", "a󠄀b"→"ab", "\uD800"→"". Se a JVM discordar de algum desses, vale a JVM e a tabela do teste é corrigida.
  - `SearchFoldingJvmEquivalenceTest`. Cada teste compara com uma referência privada `legacyFold`, que é a cadeia antiga:
    - `foldEqualsNormalizerForEveryCodePoint`: todo code point fora dos surrogates, sozinho e entre "a" e "b";
    - `foldEqualsNormalizerForCombiningSequences`: cada letra de U+0041–U+024F e U+1E00–U+1EFF seguida de cada marca de U+0300–U+036F;
    - `foldEqualsNormalizerForRandomStrings`: 50.000 strings de 1 a 24 caracteres, de `Random(1027)`, sorteadas de um conjunto com ASCII imprimível, Latin-1, Latin Extended A/B/Additional, marcas combinantes, grego, cirílico, Hangul, CJK, emoji (pares de surrogates), surrogates soltos e U+E0100.
  - `ExerciseSearchEquivalenceTest.searchMatchesAreUnchangedForCatalogAndAliases`: para cada exercício do catálogo e para dois exercícios próprios acentuados ("Élévation latérale", "Supino reto"), contra cada consulta, `ExerciseSearch.matches` dá o mesmo que uma cópia privada da implementação antiga (`legacyMatches`, com `Normalizer`). As consultas são cada nome e alias, as palavras deles, as variantes acentuadas e "DB curl", "pull-ups", "skullcrushers", "ＰＵＬＬ" e "". A referência fica duplicada de propósito neste arquivo e no anterior, com um comentário que explica.
  - `SearchFoldingDeviceTest.foldEqualsArtNormalizerForEveryBmpCodePoint`: o mesmo do primeiro teste do `SearchFoldingJvmEquivalenceTest`, no BMP, contra o `Normalizer` do ART.
- [ ] **Step 2:** Rodar `./gradlew :shared:testAndroidHostTest --tests "*SearchFolding*"` e ver falhar.
- [ ] **Step 3:** Gerar a tabela com a variável de ambiente, implementar `fold` e trocar o `normalize` do `ExerciseSearch`.
- [ ] **Step 4: Verificar.**
  - `./gradlew :shared:testAndroidHostTest --tests "*SearchFolding*"` verde;
  - `./gradlew :app:testDebugUnitTest --tests "com.mooscles.data.ExerciseSearch*" --tests "com.mooscles.data.CatalogTest"` verde.

  Uma divergência é motivo para **parar e reportar**. Rode o teste de aparelho se houver emulador.
- [ ] **Step 5:** Fazer o commit com `data: exercise search folding without java.text.Normalizer` e o push.

### Task 4: Room KMP e o bloco `data`/`domain`/`backup` no `:shared`

**Files:**
- Move (`git mv` para `shared/src/commonMain/kotlin/com/mooscles/…`):
  - `app/src/main/java/com/mooscles/data/{Models,EditRules,ExerciseCatalog,ExerciseSearch,RoutineTargetCheckpoint,WorkoutDatabase,WorkoutRepository}.kt`;
  - `domain/{RestDuration,RoutineTargetRecords,WorkoutCalculations}.kt`;
  - `backup/{BackupCodec,BackupException,BackupFormat,BackupRows,BackupTexts,ImportPlanner,PreferenceRules}.kt`.

  `backup/BackupDocuments.kt` fica no `:app`.
- Move: `git mv app/schemas shared/schemas`
- Create: `shared/src/androidMain/kotlin/com/mooscles/data/WorkoutDatabaseAndroid.kt`
- Modify:
  - `shared/build.gradle.kts`: os plugins `com.google.devtools.ksp`, `androidx.room` e `org.jetbrains.kotlin.plugin.serialization`; o bloco `room`; o KSP por alvo; as dependências:
    - `commonMain`: `api` de `room-runtime` 2.8.5, `androidx.sqlite:sqlite` 2.6.2 e `kotlinx-serialization-json` 1.12.0;
    - `androidMain`: `api("androidx.sqlite:sqlite-framework:2.6.2")`, com o comentário do pin e o `//noinspection GradleDependency` que já existem no `:app`.
  - `app/build.gradle.kts`: saem o plugin KSP, o `ksp(room-compiler)`, o bloco `ksp { … }`, `room-runtime` e `sqlite-framework`, que agora chegam pelo `api`. Também saem o plugin de serialization e o `kotlinx-serialization-json`, se nada mais no `app/src` usar `@Serializable`. Fica o `room-testing`.
  - `WorkoutDatabase.kt`: `@ConstructedBy`, o `expect object`, `FILE_NAME`, `buildMooscles`, e o `EnclosingWriteTransaction` com `AtomicReference<Throwable?>`. A primeira falha vence, por `compareAndSet(null, nested)`. Sai o `open(context)`, que vai para o `androidMain`.
  - `.claude/settings.json`: o padrão do hook passa a `*/app/schemas/*.json|*/shared/schemas/*.json|*.jks|…`, com a mesma mensagem.
  - `CLAUDE.md`: a linha do hook cita os dois caminhos.
  - `.claude/agents/compat-reviewer.md`: os gatilhos e o caminho do schema passam a citar `shared/src/commonMain/kotlin/com/mooscles/{data,timer}`, `AppPreferences.kt` nos dois módulos, `shared/build.gradle.kts` e `shared/schemas/…`, além dos caminhos do `:app` que continuam valendo.
  - `app/src/test/java/com/mooscles/data/DriverTestSupport.kt`: `fun exportedSchemaFile(version: Int): File` procura `../shared/schemas/com.mooscles.data.WorkoutDatabase/$version.json` e `shared/schemas/…`. Ela é usada em `RoutineTargetMigrationTest`, `MigrationEquivalenceTest` e `RollbackV142DataCompatibilityTest`, que hoje montam os caminhos à mão.
  - `.github/workflows/ci.yml`: no `host`, depois do build, o passo "Room schemas unchanged" roda `git diff --exit-code -- shared/schemas` e falha se `git status --porcelain -- shared/schemas` não vier vazio.
- Move + convert: `app/src/test/java/com/mooscles/data/EditRulesTest.kt` e `backup/ImportPlannerTest.kt` vão para `shared/src/commonTest/kotlin/com/mooscles/…`. Eles usam o `EditRules`, que é `internal` e fica invisível para o `:app`.
- Test: `app/src/test/java/com/mooscles/data/WorkoutDatabaseFileTest.kt` (Robolectric)

**Interfaces:**
- Consumes: `newId`, `currentTimeMillis` (Task 1), `DecimalText` (Task 2), `SearchFolding` (Task 3).
- Produces:
  - `WorkoutDatabase.FILE_NAME`;
  - `fun RoomDatabase.Builder<WorkoutDatabase>.buildMooscles(driver: SQLiteDriver): WorkoutDatabase`;
  - `fun WorkoutDatabase.Companion.open(context: Context): WorkoutDatabase`, com o mesmo nome de chamada de hoje. Quem não está no pacote `data` importa `com.mooscles.data.open`;
  - `WorkoutDatabase.MIGRATION_1_2` … `MIGRATION_4_5` no `companion`, como hoje.

- [ ] **Step 1: Teste que falha.** `WorkoutDatabaseFileTest.productionOpenUsesMoosclesDbInTheDatabasesDirectory`: depois de `WorkoutDatabase.open(context)` e de uma leitura, `context.getDatabasePath("mooscles.db").isFile` é verdadeiro, `userVersion() == 5` e nenhum outro `.db` aparece no diretório. Rodar e ver falhar, porque ainda não compila sem o `open` novo.
- [ ] **Step 2:** Mover os arquivos, configurar o Room KMP, ajustar os builds, os caminhos de schema, o hook e os docs dos agentes, e converter os dois testes movidos.
- [ ] **Step 3: Verificar.**
  - `./gradlew :shared:compileCommonMainKotlinMetadata :shared:testAndroidHostTest` verde e o grep do código comum puro (Global Constraints) vazio;
  - `./gradlew :app:testDebugUnitTest --tests "com.mooscles.data.*" --tests "com.mooscles.backup.*" --tests "com.mooscles.domain.*" --tests "com.mooscles.AppViewModelFoldersDiscardHostTest" --tests "com.mooscles.updates.UpdateDataPreservationTest" :app:assembleDebug :app:assemblePerformance` verde. Inclui o `MigrationEquivalenceTest` e os três testes de migração;
  - `git status --porcelain -- shared/schemas app/schemas` mostra só as renomeações, e `git diff -M --stat HEAD` mostra os arquivos movidos como renomeações.
- [ ] **Step 4:** Rodar o `compat-reviewer` (`SEGURO`), fazer o commit com `data: move data, domain and backup to :shared with Room KMP` e o push. Os jobs `host` (com o passo dos schemas) e `ios` têm que ficar verdes.

### Task 5: Testes puros em `commonTest`

**Files:**
- Move + convert (`git mv` para `shared/src/commonTest/kotlin/com/mooscles/…`):
  - `app/src/test/java/com/mooscles/domain/{RestDurationTest,RoutineTargetRecordsTest,WorkoutCalculationIndexTest,WorkoutCalculationsBatchTest,WorkoutCalculationsTest}.kt`;
  - `backup/{BackupCodecTest,BackupTextsTest}.kt`;
  - `data/{CatalogTest,ExerciseSearchTest}.kt`.
- Move: `app/src/test/java/com/mooscles/LargeHistoryFixture.kt` → `shared/src/testFixtures/kotlin/com/mooscles/LargeHistoryFixture.kt`. O diretório entra no `commonTest` do `:shared` (`kotlin.srcDir`) e no source set `test` do `:app`, pela API de source sets do AGP 9: `sourceSets.getByName("test").kotlin.srcDir(…)`, ou `directories.add(…)` se o `srcDir` não existir mais.
- Create: `shared/src/androidHostTest/kotlin/com/mooscles/backup/BackupSampleFileTest.kt`, com o teste do `BackupCodecTest` que lê `backup/v1-sample.json` pelo classloader, e o recurso movido com `git mv app/src/test/resources/backup/v1-sample.json shared/src/androidHostTest/resources/backup/v1-sample.json`
- Create: `shared/src/androidHostTest/kotlin/com/mooscles/data/ExerciseSearchLocaleTest.kt`, com `searchIsIndependentOfTheDevicesCaseMappingLocale`, que muda o `Locale` padrão

**Interfaces:**
- Consumes: os tipos movidos na Task 4. Os testes não mudam de nome, de método nem de asserção. Muda só a API de teste (Global Constraints).

- [ ] **Step 1:** Rodar `./gradlew :app:testDebugUnitTest :shared:testAndroidHostTest` e anotar o total dos dois relatórios. É o número que esta task preserva.
- [ ] **Step 2:** Mover e converter os testes. Os métodos e as asserções continuam os mesmos.
- [ ] **Step 3: Verificar.**
  - `./gradlew :shared:testAndroidHostTest :shared:compileTestKotlinIosSimulatorArm64` verde. O segundo pode faltar Xcode; nesse caso o CI decide;
  - `./gradlew :app:testDebugUnitTest --tests "com.mooscles.data.BackupRepositoryTest" --tests "com.mooscles.AppViewModelPresentationThreadHostTest"` verde. Os dois usam o `LargeHistoryFixture` pelo diretório compartilhado;
  - o total dos dois relatórios é igual ao do Step 1.
- [ ] **Step 4:** Fazer o commit com `test: run the pure data, domain and backup tests on every target` e o push. O job `ios` agora roda esses testes no simulador e tem que ficar verde.

### Task 6: Contrato de UI, preferências e persistência do timer

**Files:**
- Move (`git mv` para `shared/src/commonMain/kotlin/com/mooscles/…`): `app/src/main/java/com/mooscles/ui/{UiContract,UpdateUi}.kt` e `timer/{RestTimerPersistence,RestTimerState}.kt`
- Create:
  - `shared/src/commonMain/kotlin/com/mooscles/AppPreferences.kt`: a classe, com as mesmas chaves e o mesmo `flow`, `snapshot` e `restore`, linha por linha. Sem nenhuma declaração de nível superior;
  - `shared/src/commonMain/kotlin/com/mooscles/PreferencesBackup.kt`: o `fun PreferencesUi.toBackup()`, movido;
  - `shared/src/commonMain/kotlin/com/mooscles/MoosclesStore.kt`.
- Modify:
  - `app/src/main/java/com/mooscles/AppPreferences.kt`: fica só o delegate (`name = MoosclesStore.NAME`) e a fábrica `fun AppPreferences(context: Context): AppPreferences = AppPreferences(context.moosclesStore)`;
  - `shared/build.gradle.kts`: `commonMain` `api("androidx.datastore:datastore-preferences-core:1.3.0")`;
  - `app/build.gradle.kts`: continua com `datastore-preferences` 1.3.0, que traz o delegate.
- Move + convert: `app/src/test/java/com/mooscles/timer/RestTimerStateTest.kt` → `shared/src/commonTest/kotlin/com/mooscles/timer/`
- Test: `app/src/test/java/com/mooscles/MoosclesStoreTest.kt` (Robolectric) e um método novo em `app/src/test/java/com/mooscles/AppPreferencesTest.kt`

**Interfaces:**
- Produces:
  - `object MoosclesStore { const val NAME = "local_preferences"; const val FILE_NAME = "local_preferences.preferences_pb" }`;
  - `class AppPreferences(store: DataStore<Preferences>)`, com os membros públicos de hoje (`error`, `clearError`, `snapshot`, `flow`, `pounds`, `theme`, `autoRest`, `defaultRest`, `selectRoutineFolder`, `restore`);
  - `class RestTimerPersistence(store: DataStore<Preferences>, now: () -> Long = ::currentTimeMillis)`, igual à de hoje.

  `MoosclesApplication`, `RestTimerController`, `UpdateManager` e os testes continuam chamando `AppPreferences(context)` e `context.moosclesStore` sem mudança.

- [ ] **Step 1: Testes que falham.**
  - `MoosclesStoreTest.delegateFileIsFilesDatastoreLocalPreferences`: `context.preferencesDataStoreFile(MoosclesStore.NAME) == File(context.filesDir, "datastore/local_preferences.preferences_pb")` e `MoosclesStore.FILE_NAME == "${MoosclesStore.NAME}.preferences_pb"`;
  - `AppPreferencesTest.factoryWritesThroughTheDelegateStore`, com o isolamento por reflexão que a classe já usa: `AppPreferences(context).pounds(true)`, depois `context.moosclesStore.data.first()[booleanPreferencesKey("pounds")] == true`, e um segundo `AppPreferences(context)` lê o mesmo valor sem exceção.
- [ ] **Step 2:** Rodar e ver falhar, porque `MoosclesStore` ainda não existe.
- [ ] **Step 3:** Mover e criar os arquivos.
- [ ] **Step 4: Verificar.**
  - `./gradlew :shared:compileCommonMainKotlinMetadata :shared:testAndroidHostTest` verde;
  - `./gradlew :app:testDebugUnitTest --tests "com.mooscles.AppPreferencesTest" --tests "com.mooscles.MoosclesStoreTest" --tests "com.mooscles.RollbackV142PreferencesCompatibilityTest" --tests "com.mooscles.timer.*" --tests "com.mooscles.updates.*" --tests "com.mooscles.DataSectionHostTest" :app:assembleDebug` verde;
  - `unzip -l app/build/outputs/apk/debug/app-debug.apk` não tem nenhum conflito de classe, e o build já teria falhado no D8 se tivesse.
- [ ] **Step 5:** Rodar o `compat-reviewer` (`SEGURO`; ele confere chaves, tipos e caminho), fazer o commit com `prefs: preferences, timer persistence and the UI contract in :shared` e o push.

### Task 7: Apresentação e `AppViewModel` no código comum

**Files:**
- Move: `git mv app/src/main/java/com/mooscles/WorkoutPresentation.kt shared/src/commonMain/kotlin/com/mooscles/`; `git mv app/src/main/java/com/mooscles/AppViewModel.kt shared/src/commonMain/kotlin/com/mooscles/MoosclesViewModel.kt`, editado depois no mesmo lugar para preservar o histórico
- Create, em `shared/src/commonMain/kotlin/com/mooscles/`: `timer/RestTimer.kt`, `updates/UpdateCoordinator.kt`, `backup/BackupDocumentStore.kt`, `ViewModelPlatform.kt`, `MoosclesServices.kt`
- Create, em `app/src/main/java/com/mooscles/`: `AppViewModel.kt` (a subclasse nova), `AndroidViewModelPlatform.kt`, `backup/AndroidBackupDocuments.kt`
- Modify:
  - `timer/RestTimerController.kt` (`: RestTimer`, com `override` e retorno `Unit`);
  - `updates/UpdateManager.kt` (`: UpdateCoordinator`, com `override`; os valores padrão dos parâmetros ficam só na interface);
  - `MoosclesApplication.kt` (`val services by lazy { … }`);
  - `MainActivity.kt` (`viewModels { AppViewModel.Factory }`);
  - `shared/build.gradle.kts`: `commonMain` `api("androidx.lifecycle:lifecycle-viewmodel:2.11.0")` e `implementation` do `androidx.annotation`.
- Move + split: `app/src/test/java/com/mooscles/WorkoutPresentationTest.kt` → `shared/src/commonTest/kotlin/com/mooscles/WorkoutPresentationTest.kt`. Os dois testes que chamam `assertPresentsQuickly` (com os limites de 1.000 ms e 200 ms) vão para `shared/src/androidHostTest/kotlin/com/mooscles/WorkoutPresentationTimingTest.kt`, com os mesmos nomes.

**Interfaces:**
- Produces:
  - `interface RestTimer`:
    - `val ui: StateFlow<TimerUi>`, `val error: StateFlow<String?>`, `fun clearError()`;
    - `suspend fun start(name: String, seconds: Int, workoutExerciseId: String? = null)`;
    - `suspend fun clear()`, `clearForSessionEnd()`, `pause()`, `resume()`, `reset()`, `skip()`, `adjust(seconds: Int)`.
  - `interface UpdateCoordinator`:
    - `val ui: Flow<UpdateUi>`;
    - `suspend fun onTrainingChanged()`, `assertTrainingMayStart()`, `requestCheck(manual: Boolean)`, `setAutomatic(enabled: Boolean)`, `setWifiOnly(enabled: Boolean)`, `setPrereleases(enabled: Boolean)`;
    - `suspend fun enqueueDownload(manual: Boolean = false)` e `cancelDownload(markCancelled: Boolean = true)`.
  - `interface BackupDocumentStore`: `suspend fun write(uri: String, text: String)`, `discard(uri: String)`, `read(uri: String): String` e `fun isFileAccessFailure(failure: Exception): Boolean`. O Android devolve `failure is IOException || failure is SecurityException`.
  - `interface ViewModelPlatform`: `fun <T> presentation(block: () -> T): T` e `suspend fun <T> startingSession(block: suspend () -> T): T`, com `object None : ViewModelPlatform`, que só executa o bloco. O Android:
    - na apresentação, envolve o bloco em `Trace.beginSection("Mooscles:present")`/`endSection()` e, quando o bloco termina, incrementa `presentedOnMainThread` (se estiver no `Looper` principal) e `presentedCount`;
    - na sessão, envolve o bloco em `Trace.beginAsyncSection`/`endAsyncSection("Mooscles:startSession", cookie)` a partir da API 29, com o cookie `System.identityHashCode(this)`.
  - `class MoosclesServices(val repository: WorkoutRepository, val preferences: AppPreferences, val timer: RestTimer, val updates: UpdateCoordinator, val trainingGate: Mutex, val documents: BackupDocumentStore, val about: BackupApp)`.
  - `abstract class MoosclesViewModel(services: MoosclesServices, platform: ViewModelPlatform = ViewModelPlatform.None) : ViewModel(), MoosclesActions`:
    - `contentUi`, `timerUi`, `ui` e `openUpdateSettings()` como hoje;
    - `@VisibleForTesting fun enqueueCommandForTest(block: suspend () -> Unit)` e `@VisibleForTesting var screenWaitMillis`, agora públicos, porque o `internal` não passa de um módulo para outro;
    - `protected abstract suspend fun install()`, chamado por `installUpdate()` dentro de `act`;
    - o `finally` de `startWorkout` continua zerando `startingWorkout` antes do fim da seção de trace.
  - `class AppViewModel private constructor(app: MoosclesApplication, platform: AndroidViewModelPlatform) : MoosclesViewModel(app.services, platform)`:
    - `constructor(application: Application)`;
    - `val installIntents: Flow<Intent>`;
    - `@VisibleForTesting internal val presentedCount` e `presentedOnMainThread`, os `AtomicInteger` da plataforma;
    - `companion object { val Factory: ViewModelProvider.Factory }`, com `viewModelFactory { initializer { AppViewModel(this[APPLICATION_KEY]!!) } }`.
  - `MoosclesApplication.services` monta os campos na ordem repository, preferences, timer, updates, a mesma ordem em que o ViewModel de hoje inicializa os `lazy`, com `AndroidBackupDocuments(this)` e `BackupApp(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, "android")`.

- [ ] **Step 1:** Rodar `./gradlew :app:testDebugUnitTest --tests "com.mooscles.AppViewModel*" --tests "com.mooscles.UiFlowHostTest" --tests "com.mooscles.DiscardNavigationHostTest" --tests "com.mooscles.RoutineResumeHostTest" --tests "com.mooscles.WorkoutPresentationTest"`, verde antes da mudança. Esses testes são a rede de segurança desta task e não mudam. O `UiFlowHostTest` sobe a `MainActivity`, então também cobre a `Factory`.
- [ ] **Step 2:** Criar as interfaces, mover e dividir o ViewModel e a apresentação, e implementar as interfaces no `:app`. A diferença entre o `MoosclesViewModel` e o `AppViewModel` antigo tem que ser só substituições: `app.X` vira `services.X`; `Trace`/`Looper` viram `platform`; `BackupDocuments.*(app, …)` vira `documents.*(…)`; o `instanceof IOException` vira `isFileAccessFailure`; `BuildConfig` vira `about`; o `Intent` vira `install()`.
- [ ] **Step 3: Verificar.**
  - os testes do Step 1, agora com `:shared:testAndroidHostTest --tests "*WorkoutPresentation*"`, verdes;
  - `./gradlew :shared:compileCommonMainKotlinMetadata :app:lintDebug :app:assembleDebug :app:assemblePerformance` verde;
  - o grep do código comum puro, vazio.
- [ ] **Step 4:** Rodar o `compat-reviewer` (`SEGURO`; ele confere que o timer e o updater só ganharam `override`), fazer o commit com `app: shared presentation and view model logic, Android services behind interfaces` e o push.

> **Nota de 2026-10-28 (desvio aprovado na revisão da Task 7).** O `AppViewModel` ganhou um `fun <T : Application> getApplication(): T` **público**, que devolve o `MoosclesApplication`, como o `AndroidViewModel.getApplication()` fazia antes. A lista de interfaces acima não o prevê. Ele existe para que o `UiFlowHostTest` continue sem nenhuma mudança, como manda o Step 1: o teste chama `getApplication<MoosclesApplication>()` no ViewModel. O revisor aprovou o desvio. Na mesma revisão, o `compat-reviewer` pediu um teste que prendesse o `install()` ao `UpdateManager.requestInstall()`; ele entrou na Task 10, como `AppViewModelInstallHostTest`.

### Task 8: Entradas iOS para banco e preferências, provadas no simulador

**Files:**
- Create: `shared/src/iosMain/kotlin/com/mooscles/data/WorkoutDatabaseIos.kt`, `shared/src/iosMain/kotlin/com/mooscles/MoosclesStoreIos.kt`
- Modify: `shared/build.gradle.kts` (`iosMain` `implementation("androidx.sqlite:sqlite-bundled:2.6.2")`)
- Test: `shared/src/iosTest/kotlin/com/mooscles/data/IosDatabaseTest.kt`, `shared/src/iosTest/kotlin/com/mooscles/IosPreferencesStoreTest.kt`

**Interfaces:**
- Consumes: `buildMooscles` e `FILE_NAME` (Task 4), `MoosclesStore`, `AppPreferences` e `RestTimerPersistence` (Task 6).
- Produces:
  - `fun openWorkoutDatabase(directory: String): WorkoutDatabase`, que faz `Room.databaseBuilder<WorkoutDatabase>(name = "$directory/${WorkoutDatabase.FILE_NAME}").buildMooscles(BundledSQLiteDriver())`;
  - `fun createMoosclesStore(directory: String, scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())): DataStore<Preferences>`, que faz `PreferenceDataStoreFactory.createWithPath(scope = scope) { "$directory/datastore/${MoosclesStore.FILE_NAME}".toPath() }`. É o mesmo layout relativo do Android.

- [ ] **Step 1: Testes.** Cada teste usa um diretório novo em `NSTemporaryDirectory()`, apagado no fim.
  - `IosDatabaseTest.freshDatabaseHasTheDefaultFolderAndVersionFive`: depois de `WorkoutRepository(db).initialize()`, `data.first()` tem a pasta `RoutineFolder.DEFAULT_ID` com o nome "My routines", e o número de exercícios é `ExerciseCatalog.exercises.size`. `PRAGMA user_version` lido por `useReaderConnection` é 5.
  - `IosDatabaseTest.workoutRoundTripsAfterReopening`: `createRoutine`, `addRoutineExercise`, `startRoutine` e `finishWorkout` com `now` fixo. Depois de fechar e reabrir, `data.first()` tem os mesmos treinos, linhas de exercício e séries.
  - `IosDatabaseTest.failedNestedTransactionRollsBackTheOuterOne`: `writeTransaction { createRoutine(...); runCatching { writeTransaction { error("Nested failure") } } }` lança exceção e não deixa nenhuma rotina nova. É o mesmo cenário do `RoomPersistenceTest.nestedRepositoryCommandsJoinTheOuterTransactionAndFailAllOrNothing`, agora sobre os atomics nativos.
  - `IosPreferencesStoreTest.preferencesAndTimerPersistAcrossStoreInstances`: `AppPreferences.restore(BackupPreferences(true, "dark", false, 150, "default"))` e `RestTimerPersistence(store) { 1_000_000L }.start("Press", 90, "entry")`. Depois de cancelar o escopo, um store novo no mesmo diretório lê os mesmos `snapshot()`, e o arquivo `$dir/datastore/local_preferences.preferences_pb` existe.
- [ ] **Step 2:** Implementar.
- [ ] **Step 3: Verificar.**
  - localmente, `./gradlew :shared:compileCommonMainKotlinMetadata :shared:testAndroidHostTest :app:assembleDebug` verde. O `iosMain` não entra no APK;
  - no CI, depois do push, o job `ios` verde com os quatro testes no resumo. Sem Xcode, este Mac não roda o `iosTest`; isso fica registrado no relatório.
- [ ] **Step 4:** Rodar o `compat-reviewer` (`SEGURO`; ele confere que nada do Android mudou), fazer o commit com `ios: open the database and preferences on iOS, tested on the simulator` e o push.

### Task 9: Teste instrumentado com banco e DataStore criados pela versão anterior

**Files:**
- Create: `app/src/androidTest/assets/legacy-1.4.5/mooscles.db`, `app/src/androidTest/assets/legacy-1.4.5/local_preferences.preferences_pb`, `app/src/androidTest/assets/legacy-1.4.5/README.md` (como os arquivos foram criados: commit, roteiro e data; sem dado pessoal)
- Test: `app/src/androidTest/java/com/mooscles/LegacyInstallOpenTest.kt`

**Interfaces:**
- Consumes: `buildMooscles` (Task 4), `AppPreferences(store)` e `RestTimerPersistence` (Task 6).

- [ ] **Step 1: Fixture**, só no `emulator-5554`.
  1. Montar o APK "antes" num worktree separado, no commit da `main` de onde a branch saiu: `git worktree add <scratchpad>/before <sha>`. Apagar o `app-debug.apk` velho e rodar `./gradlew :app:assembleDebug`.
  2. `adb uninstall com.mooscles` e instalar o APK "antes".
  3. Importar `docs/superpowers/reports/2026-10-27-phase1a-verificacao/export-before-upgrade.json` pela UI. Ele é sintético e já traz as pastas, as rotinas, os treinos com PR e as preferências não padrão.
  4. Começar um treino de rotina e completar duas séries, para o timer de descanso ficar rodando.
  5. `adb shell am force-stop com.mooscles` e `run-as com.mooscles sqlite3 databases/mooscles.db "PRAGMA wal_checkpoint(TRUNCATE)"`.
  6. Copiar com `adb exec-out run-as com.mooscles cat …` o `databases/mooscles.db` e o `files/datastore/local_preferences.preferences_pb`.
  7. Anotar, com `sqlite3` sobre a cópia, as contagens por tabela e o id do treino ativo, e no `.preferences_pb` o `timer_deadline` e o `timer_token`.
- [ ] **Step 2: Teste.** As duas fixtures são copiadas para nomes de teste (`getDatabasePath("legacy-fixture.db")` e um diretório temporário), nunca sobre o `mooscles.db` real, e apagadas no fim.
  - `LegacyInstallOpenTest.legacyDatabaseOpensWithEveryRow`: abre com `Room.databaseBuilder(context, WorkoutDatabase::class.java, "legacy-fixture.db").buildMooscles(AndroidSQLiteDriver())`. Confere `user_version` 5, as contagens anotadas e que `WorkoutRepository(db).data.first().activeWorkout?.id` é o id anotado.
  - `LegacyInstallOpenTest.legacyPreferencesAndTimerReadBack`: com `PreferenceDataStoreFactory.create { arquivo copiado }`, `AppPreferences(store).snapshot()` é igual às preferências do backup (lb, tema escuro, descanso automático, 150 s, a pasta "Upgrade block"). `RestTimerPersistence(store) { capturedAt }.snapshot()` está rodando, com o `deadline`, o `token` e o `workoutId` anotados.
- [ ] **Step 3: Verificar** com `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mooscles.LegacyInstallOpenTest`, verde. Depois, os testes de aparelho das Tasks 2 e 3 (`DecimalTextDeviceTest`, `SearchFoldingDeviceTest`), verdes.
- [ ] **Step 4:** Fazer o commit com `test: open a v1.4.5 database and preferences file on the device` e o push.

### Task 10: Atualização por cima, medições, docs e relatório

**Files:**
- Create: `docs/superpowers/reports/2026-10-27-phase1b-verificacao.md` e a pasta `docs/superpowers/reports/2026-10-27-phase1b-verificacao/` (prints e exports, sem dado pessoal)
- Modify:
  - `AGENTS.md`:
    - em "Ambiente e comandos", os comandos `:shared:testAndroidHostTest`, `:shared:compileCommonMainKotlinMetadata` e `:shared:iosSimulatorArm64Test` (este só no CI ou num Mac com Xcode);
    - em "Arquitetura", os dois módulos, o que fica em cada um, as interfaces da Task 7 e onde ficam o delegate do DataStore e o `open` do banco;
    - em "Compatibilidade", os schemas em `shared/schemas/`, a regra das fachadas, a regra do DataStore único e o `local_preferences` dividido entre preferências e timer.
  - `docs/superpowers/ROADMAP.md`: a etapa 1B marcada, com os links.
  - A spec, só com uma nota datada, se alguma decisão mudou durante a implementação.

- [ ] **Step 1: APKs.** O "antes" é o da Task 9, ou um refeito do mesmo commit. O "depois" é desta branch. Os dois são empacotados do zero (apagar o `app-debug.apk` antes do build, como recomenda a 1A), com a chave debug do Mac.
- [ ] **Step 2: Atualização por cima** (Review Focus 1), só no `emulator-5554`, com o roteiro da 1A:
  1. instalação limpa do "antes" e os mesmos dados da Task 9 (import, treino ativo com duas séries e o timer rodando, o estado real do updater);
  2. export pela UI;
  3. captura: o `.dump` do `sqlite3`, `user_version`, `journal_mode`, `integrity_check`, `foreign_key_check`, os dois `.preferences_pb`, `dumpsys alarm` e `dumpsys dbinfo`;
  4. `adb install -r` do "depois", anotando a hora e o timer;
  5. a mesma captura, os prints e um export novo.

  O resultado esperado:
  - o dump e os dois `.preferences_pb` idênticos;
  - o export idêntico tirando `exportedAt`;
  - o treino retomado ("Pick up where you left off");
  - o timer com o mesmo prazo, até "Rest complete";
  - histórico, recordes e rotinas iguais.

  Uma diferença é motivo para **parar e reportar**.
- [ ] **Step 3: R8.** Instalar o `assemblePerformance` por cima, exportar a base grande (`mooscles-large-208.json`), `pm clear`, importar e exportar de novo. Os dois exports são iguais tirando o `exportedAt`. Isso prova o kotlinx.serialization a partir de um módulo de biblioteca.
- [ ] **Step 4: Desempenho e tamanho.**
  - com a base grande, as medições da 1A em execuções alternadas antes e depois: três aberturas a frio de cada lado, o import e a troca kg/lb, por `atrace` (`Mooscles:present`) e `Displayed`;
  - o tamanho dos APKs debug e R8, antes e depois.

  O orçamento é de cerca de 1 MB no debug.
- [ ] **Step 5:** Escrever o relatório, com o que **não** foi verificado: nada roda num iPhone; o `iosTest` só roda no simulador do CI; não há aparelho físico; o Android 16 e o `PRAGMA synchronous` num aparelho com `FULL` ficam de fora. Atualizar o `AGENTS.md` e o ROADMAP.
- [ ] **Step 6:** Fazer o commit com `docs(phase1b): record upgrade-in-place verification` e o push. Com o CI verde (`host`, `ios` e `release-script`, que roda porque `build.ps1` e `app/build.gradle.kts` mudaram), o controlador roda `/code-review` na branch inteira e faz o merge na `main` do fork.
