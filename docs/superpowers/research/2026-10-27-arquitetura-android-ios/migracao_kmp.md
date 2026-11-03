# Migração do Mooscles (Android, Kotlin) para Kotlin Multiplatform com alvo iOS: caminho concreto (estado em outubro de 2026)

App de origem: Jetpack Compose + Material 3, Room 2.7 (schemas exportados, migrations manuais), DataStore Preferences, Navigation Compose, kotlinx-coroutines, WorkManager, timer via AlarmManager e testes Robolectric.

Pesquisa feita em 27/10/2026. Versões conferidas nas páginas oficiais nessa data.

---

## 1. Room KMP: versão com iOS, BundledSQLiteDriver, migração sem perder dados, restrições e testes

### Takeaway
O Room roda em iOS desde a 2.7.0 (abril de 2025). A linha estável 2.x está na 2.8.5 (09/09/2026). Em julho de 2026 saiu o Room 3.0, num pacote novo (`androidx.room3`): só KSP, só código Kotlin, sem SupportSQLite, DAOs com `suspend` ou `Flow`. A estável atual é a 3.0.3, e a 3.1.0-alpha03 exige Kotlin 2.2. Para o Mooscles, o caminho de menor risco tem dois passos. Primeiro, subir para Room 2.8.x e levar entidades, DAOs e database para `commonMain`, com o mesmo nome de arquivo, a mesma `version` e o mesmo diretório de schemas. Depois, num passo separado e opcional, ir para o Room 3. Os testes de migração devem continuar no lado Android por enquanto, porque não achei um caminho documentado e estável para `MigrationTestHelper` em `commonTest`/iOS.

### Cited Findings
**Versões e linha do tempo**
- No Room 2.7.0-alpha01 (01/05/2024) o Room foi refatorado para KMP. A 2.7.0 (09/04/2025) trata isso como "Important change". Plataformas: Android, iOS, JVM (Desktop), Mac nativo e Linux nativo. A geração de código Kotlin via KSP vem ligada por padrão, e o Room 2.7.0 exige Kotlin 2.0 — [Room release notes](https://developer.android.com/jetpack/androidx/releases/room)
- Linha 2.x: 2.8.0 (10/09/2025), 2.8.4 (19/11/2025), 2.8.5 (09/09/2026, a mais recente) — [Room release notes](https://developer.android.com/jetpack/androidx/releases/room)
- O Room 2.8.0 subiu o minSdk de 21 para 23 e adicionou `androidx.room:room-sqlite-wrapper` com `RoomDatabase.getSupportWrapper()`, para manter código que usa `SupportSQLiteDatabase` funcionando com um `SQLiteDriver` — [Room release notes](https://developer.android.com/jetpack/androidx/releases/room)
- O `room-ktx` foi incorporado ao `room-runtime` na 2.7.0-alpha01 e virou artefato vazio, que pode ser removido — [Room release notes](https://developer.android.com/jetpack/androidx/releases/room)
- Na 2.7.0-alpha06, `instantiateImpl()` deu lugar a `@ConstructedBy` + `expect object ...: RoomDatabaseConstructor<...>` — [Room release notes](https://developer.android.com/jetpack/androidx/releases/room)
- A tabela de bibliotecas Jetpack com KMP lista **room 2.8.5** (Android, iOS, JVM; Web não) e **sqlite 2.7.1 / 2.8.0-alpha03** — [Android KMP overview](https://developer.android.com/kotlin/multiplatform)

**Room 3 (pacote novo)**
- Versões: 3.0.0-alpha01 (11/03/2026), 3.0.0 (01/07/2026), 3.0.3 (09/09/2026, estável mais recente), 3.1.0-alpha01 (09/09/2026), 3.1.0-alpha03 (21/10/2026) — [Room3 release notes](https://developer.android.com/jetpack/androidx/releases/room3)
- Pacote novo para não conflitar com o 2.x: `androidx.room:room-runtime` vira `androidx.room3:room3-runtime` e `androidx.room.RoomDatabase` vira `androidx.room3.RoomDatabase` — [Room3 release notes](https://developer.android.com/jetpack/androidx/releases/room3)
- "Room 3.0 is fully backed by the SQLiteDriver APIs and no longer references SupportSQLite types". O suporte a SupportSQLite fica só em `androidx.room3:room3-sqlite-wrapper`. `runInTransaction` vira `withWriteTransaction`, e `query(...)` com cursor vira `useReaderConnection { it.usePrepared(...) }` — [Room3 release notes](https://developer.android.com/jetpack/androidx/releases/room3)
- "Room 3.0 only generates Kotlin code and is only a Kotlin Symbol Processor (KSP)". KAPT e JavaAP não são mais suportados — [Room3 release notes](https://developer.android.com/jetpack/androidx/releases/room3)
- "DAO functions have to be suspending unless they are returning a reactive type, such as a Flow". Executors dão lugar a `CoroutineContext`, e `InvalidationTracker.Observer` sai em favor de `createFlow()` — [Room3 release notes](https://developer.android.com/jetpack/androidx/releases/room3)
- LiveData, RxJava, Paging e Guava passam a usar `@DaoReturnTypeConverter`, em artefatos separados: `room3-livedata`, `room3-paging`, `room3-rxjava3` e `room3-guava` — [Room3 release notes](https://developer.android.com/jetpack/androidx/releases/room3)
- O Room 3 acrescenta os alvos JS/WasmJs (`WebWorkerSQLiteDriver`, OPFS), `@Fts5`, `withoutRowId` e chaves compostas em `@Relation`. A 3.1.0-alpha03 diz: "Libraries now target Kotlin 2.2 and require kotlinc 2.2" — [Room3 release notes](https://developer.android.com/jetpack/androidx/releases/room3)
- A página oficial de Room KMP já usa nos exemplos `room3 = "3.1.0-alpha03"`, `sqlite = "2.7.1"` e o plugin `androidx.room3` — [Room KMP doc](https://developer.android.com/kotlin/multiplatform/room)

**Setup (do doc oficial)**
- Em `commonMain`: `room3-runtime` + `androidx.sqlite:sqlite-bundled`. O KSP precisa ser declarado por alvo: `add("kspAndroid", ...)`, `kspIosSimulatorArm64`, `kspIosX64`, `kspIosArm64` — [Room KMP doc](https://developer.android.com/kotlin/multiplatform/room)
- Instanciação comum: `builder.setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()`. O `BundledSQLiteDriver` é o recomendado, porque compila o SQLite a partir do código-fonte e dá o mesmo comportamento em todas as plataformas. As alternativas são `AndroidSQLiteDriver` e `NativeSQLiteDriver` (este exige `linkerOpts.add("-lsqlite3")`) — [Room KMP doc](https://developer.android.com/kotlin/multiplatform/room)
- iOS: `Room.databaseBuilder<AppDatabase>(name = documentDirectory() + "/my_room.db")`, com `NSFileManager...URLForDirectory(NSDocumentDirectory, NSUserDomainMask, ...)` — [Room KMP doc](https://developer.android.com/kotlin/multiplatform/room)
- Com Kotlin 1.9.x é preciso `kotlin.native.disableCompilerDaemon = true`. Com Kotlin 2.0 ou mais novo, não — [Room KMP doc](https://developer.android.com/kotlin/multiplatform/room)
- ProGuard/R8: `-keep class * extends androidx.room3.RoomDatabase { <init>(); }` — [Room KMP doc](https://developer.android.com/kotlin/multiplatform/room)

**Restrições em código comum**
- Fora do Android, todo DAO precisa ser `suspend`. DAOs bloqueantes só são aceitos em `androidMain`. `LiveData` não é compatível com KMP; use `Flow` — [Room KMP doc](https://developer.android.com/kotlin/multiplatform/room)
- `setQueryExecutor` não existe em `commonMain` (use `setQueryCoroutineContext`). `androidx.sqlite.db` (`SupportSQLiteDatabase`, `SupportSQLiteQuery`, `SimpleSQLiteQuery`) é só Android. `@RawQuery` fora do Android recebe `RoomRawQuery` — [Room KMP doc](https://developer.android.com/kotlin/multiplatform/room)
- `withTransaction {}` é só Android. Em código comum use `useWriterConnection { it.immediateTransaction { } }` ou `useReaderConnection { it.deferredTransaction { } }` — [Room KMP doc](https://developer.android.com/kotlin/multiplatform/room)
- Não existem em KMP: `setQueryCallback`/`QueryCallback`, `setAutoCloseTimeout`, `createFromAsset/File/InputStream`, `PrepackagedDatabaseCallback` e `enableMultiInstanceInvalidation` — [Room KMP doc](https://developer.android.com/kotlin/multiplatform/room)

**Migrations e schemas**
- O diretório de schemas é configurado com `room { schemaDirectory("$projectDir/schemas") }`. Em KMP, as migrations recebem `SQLiteConnection` (`object Migration_1_2 : Migration(1, 2) { override fun migrate(connection: SQLiteConnection) }`). `AutoMigrationSpec.onPostMigrate(connection: SQLiteConnection)` e os callbacks `onCreate`/`onOpen`/`onDestructiveMigration` também recebem `SQLiteConnection` — [Room KMP doc](https://developer.android.com/kotlin/multiplatform/room)
- "Compatibility mode": enquanto o `RoomDatabase` usar um `SupportSQLiteOpenHelper.Factory` (sem driver definido), as duas APIs funcionam. Isso permite migrar aos poucos — [Room KMP doc](https://developer.android.com/kotlin/multiplatform/room#migrate)
- O codelab oficial mostra o roteiro: (1) trocar para `BundledSQLiteDriver` ainda no app Android; (2) mover a pasta `schemas` do `androidApp` para a raiz do `:shared`; (3) mover entidades, DAOs e `AppDatabase` para `commonMain` com Refactor → Move; (4) manter o mesmo nome de arquivo (`sharedfruits.db`) e a mesma `version = 1`; (5) colocar o builder Android em `androidMain` e deixar Hilt no app; (6) usar `@ObjCName` nas entidades para evitar conflito de nomes no Swift. O codelab avisa: "Using the BundledSQLiteDriver will increase the binary size on both Android and iOS" — [Codelab Migrate existing apps to Room KMP](https://developer.android.com/codelabs/kmp-migrate-room)
- Desde a 2.7.0-alpha07, o Room Gradle Plugin copia os schemas exportados para os recursos de teste instrumentado, para o `MigrationTestHelper` poder lê-los — [Room release notes](https://developer.android.com/jetpack/androidx/releases/room)

**Testes**
- Um relato no Kotlin Slack, de quem já levou DAOs para `commonMain`: os testes de migração ainda dependem de `AndroidJUnit4`, `MigrationTestHelper` e `InstrumentationRegistry`. A recomendação na conversa foi manter esses testes no Android por ora. É um relato informal, não uma posição oficial — [Kotlin Slack](https://slack-chats.kotlinlang.org/t/33234604/how-to-handle-room-migration-tests-in-kmp-i-am-currently-mig)
- A página de Room KMP não tem uma seção de testes e só aponta para o codelab — [Room KMP doc](https://developer.android.com/kotlin/multiplatform/room)

### Inferences
- **Dados existentes:** o Room confere a `version` e o identity hash do schema guardados no próprio arquivo SQLite. Se o caminho do arquivo (`getDatabasePath("<nome>.db")`), a `version` e a definição das entidades não mudarem, o banco Android abre sem passar por migration. É o que o codelab faz ao manter nome e versão. Mudar só o driver (framework → bundled) não altera o formato do arquivo, porque os dois são SQLite. Ainda assim, recomendo um teste instrumentado que abra um banco antigo de verdade.
- **Riscos do Mooscles:**
  - Migrations manuais que usam `SupportSQLiteDatabase.execSQL` precisam ser reescritas para `SQLiteConnection.execSQL`, ou usar o wrapper no Android durante a transição.
  - `@TypeConverter` com `java.time.*`, `java.util.Date` ou `UUID` tem de virar `kotlinx-datetime`/`kotlin.time.Instant` e `kotlin.uuid.Uuid` (ou Long/String), sem mudar o tipo da coluna SQL. Se a coluna mudar, o identity hash muda e o Room exige migration.
  - DAOs que retornam `List` de forma síncrona, `LiveData` ou `Cursor` precisam virar `suspend`/`Flow`.
  - `withTransaction` precisa ser trocado.
- **Ordem sugerida:** (a) Room 2.7 → 2.8.5, ainda só Android, com o driver bundled ligado e os testes de migração verdes; (b) mover para `commonMain` (Room 2.8.x ainda tem KMP completo); (c) só depois, se valer a pena, ir para Room 3, que é praticamente uma troca de pacote e artefatos, mais `@DaoReturnTypeConverter` onde houver LiveData. A 3.1 exige kotlinc 2.2.
- O Robolectric continua servindo para os testes host do Android que dependem de `Context`. Testes de DAO/repositório podem ir para `commonTest` com o `BundledSQLiteDriver` e um banco em arquivo temporário ou em memória, e rodar também no simulador iOS.

### Gaps
- Não achei uma afirmação oficial e explícita de que Room 2 → Room 3 preserva o arquivo e o identity hash. Parece provável, porque o schema SQL não muda, mas precisa ser validado num teste.
- Não confirmei se `MigrationTestHelper` tem uma variante oficial para native/`commonTest` (o construtor com `schemaDirectoryPath` + `driver`). A página de referência não carregou.
- Não achei a política oficial de manutenção do Room 2.x depois do Room 3.

---

## 2. DataStore KMP, Lifecycle/ViewModel KMP e Navigation Compose multiplatform

### Takeaway
Os três têm versões estáveis para iOS: DataStore 1.3.0 (só Preferences em KMP), Lifecycle/ViewModel 2.11.0 e Navigation 2.10.x com rotas type-safe via `@Serializable`, que no iOS vem do fork JetBrains `org.jetbrains.androidx.navigation`. O Navigation 3 tem estável 1.2.0. Os pontos de cuidado são dois: manter o caminho do arquivo `.preferences_pb` do Android e alinhar as versões dos artefatos JetBrains com o Compose Multiplatform.

### Cited Findings
**DataStore**
- O DataStore tem suporte a KMP a partir da 1.1.0, e os exemplos usam a 1.3.0. "Only DataStore Preferences is supported in KMP projects". Em `commonMain` entram `androidx.datastore:datastore-core:1.3.0` e `datastore-preferences-core:1.3.0` — [DataStore KMP doc](https://developer.android.com/kotlin/multiplatform/datastore)
- `DataStoreFactory.create(storage)` fica no código comum. No Android usa `FileStorage` com `context.filesDir.resolve(nome)`. No iOS usa `OkioStorage(FileSystem.SYSTEM, PreferencesSerializer, producePath = { NSDocumentDirectory + "/$nome" .toPath() })` — [DataStore KMP doc](https://developer.android.com/kotlin/multiplatform/datastore)
- A tabela oficial lista datastore 1.3.0 estável para Android, iOS, JVM e Web — [Android KMP overview](https://developer.android.com/kotlin/multiplatform)
- A DataStore já era estável em KMP em maio de 2024 — [Android Developers Blog, mai/2024](https://android-developers.googleblog.com/2024/05/android-support-for-kotlin-multiplatform-to-share-business-logic-across-mobile-web-server-desktop.html)

**Lifecycle / ViewModel**
- lifecycle 2.11.0 e viewModel 2.11.0 estáveis (2.12.0-alpha05 em pré-release). viewModel-compose 2.11.0, com a parte não-Android publicada pela JetBrains. savedstate 1.5.0 — [Android KMP overview](https://developer.android.com/kotlin/multiplatform)
- O Compose Multiplatform 1.12.2 traz `org.jetbrains.androidx.lifecycle:lifecycle-*:2.11.0` e Savedstate 1.4.0 — [What's new CMP 1.12](https://kotlinlang.org/docs/multiplatform/whats-new-compose-112.html)
- No iOS, desde o CMP 1.12.0 o "ViewModel receives onCleared when the Compose container is deallocated" — [CMP CHANGELOG](https://raw.githubusercontent.com/JetBrains/compose-multiplatform/master/CHANGELOG.md)

**Navigation**
- Navigation 2.10.2 estável no Android, com iOS, JVM e Web via JetBrains. navigation3 1.2.0 estável (1.3.0-alpha03 em pré-release). navigationevent 1.2.0 — [Android KMP overview](https://developer.android.com/kotlin/multiplatform)
- O CMP 1.12.2 traz `org.jetbrains.androidx.navigation:navigation-*:2.10.0-rc01` (baseado no Navigation 2.10.0) e `org.jetbrains.androidx.navigation3:navigation3-*:1.2.0-rc01` — [What's new CMP 1.12](https://kotlinlang.org/docs/multiplatform/whats-new-compose-112.html). Há divergência com a tabela do Google, que lista 2.10.2 estável: o fork JetBrains anda atrás do AndroidX.
- Rotas type-safe: `@Serializable object/data class`, `composable<Rota>`, `backStackEntry.toRoute()`, `navController.navigate(Profile("Alice"))`. A recomendação é passar só IDs pela rota — [Compose navigation (kotlinlang)](https://kotlinlang.org/docs/multiplatform/compose-navigation.html)
- No iOS o gesto de voltar (swipe) vem ligado por padrão (`ComposeUIViewController(configure = { enableBackGesture = false })` desliga). **Se o `NavHost` tiver `enterTransition`/`exitTransition` customizados, a animação nativa de swipe do iOS some.** Deep links precisam de URI scheme registrado por plataforma. Para efeitos do sistema iOS como Liquid Glass, a sugestão é fazer a navegação em SwiftUI e deixar o Compose no conteúdo — [Compose navigation (kotlinlang)](https://kotlinlang.org/docs/multiplatform/compose-navigation.html)
- Alternativas citadas: Voyager, Decompose, Circuit, Appyx e PreCompose — [Compose navigation (kotlinlang)](https://kotlinlang.org/docs/multiplatform/compose-navigation.html)

### Inferences
- **Dados existentes no DataStore:** o delegate Android `preferencesDataStore(name = "x")` guarda o arquivo em `filesDir/datastore/x.preferences_pb`. O exemplo KMP usa `filesDir/x.preferences_pb`, sem a subpasta. Para não zerar as preferências dos usuários Android, a `actual` Android precisa apontar para o mesmo arquivo de hoje (`context.preferencesDataStoreFile("x")` ou `filesDir.resolve("datastore/x.preferences_pb")`). Esse caminho é o padrão conhecido do AndroidX, mas não conferi nesta pesquisa.
- Precisa existir uma única instância de DataStore por arquivo no processo: singleton no DI.
- Se o Mooscles já usa rotas `@Serializable` no Navigation 2.8+, levar o grafo para `commonMain` é praticamente trocar a dependência. Rotas com String ou argumentos via `Bundle`/`Parcelable` precisam ser convertidas. Navigation 3 é uma opção para depois, não um requisito.
- Os ViewModels que dependem só de repositórios e Flows vão para `commonMain` sem mudança. `AndroidViewModel`/`Application`, `SavedStateHandle` com `Parcelable` e `viewModelScope` com `Dispatchers.Main` funcionam, mas o primeiro precisa ser removido.

### Gaps
- Não conferi a política de desvio de versão entre o fork JetBrains do Navigation (2.10.0-rc01 no CMP 1.12.2) e o AndroidX (2.10.2), nem se já existe 2.10.x final da JetBrains.

---

## 3. Compose Multiplatform no iOS: Material 3, recursos, teclado e insets, acessibilidade, scroll e interop com SwiftUI/UIKit

### Takeaway
O Compose Multiplatform para iOS é estável desde a 1.8.0 (maio de 2025), e a versão atual é a 1.12.2 (outubro de 2026, iOS mínimo 14). Material 3 funciona, mas o artefato JetBrains de Material3 está em `1.12.0-alpha03`. A entrada de texto nativa (cursor, lupa, autocorreção, autofill) existe desde a 1.11 como opt-in. A acessibilidade tem VoiceOver, Full Keyboard Access e AssistiveTouch, com correções contínuas. Para um app de treino com muitos `TextField` numéricos e o teclado sobre o timer, o risco está em insets, IME e comportamento de teclado. Isso pede testes em aparelho real.

### Cited Findings
- Blog JetBrains (maio/2025): "Compose Multiplatform 1.8.0 Released: Compose Multiplatform for iOS Is Stable and Production-Ready", com paridade com Jetpack Compose, navegação type-safe com deep links e acessibilidade (VoiceOver, AssistiveTouch, Full Keyboard Access) — [JetBrains Blog](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/). Detalhes lidos via snippets e via a versão chinesa do post.
- Versão atual: CMP **1.12.2**, mapeado para Jetpack Compose 1.12.1. iOS mínimo 14. Kotlin mínimo 2.1.0, com recomendação de "upgrade to Kotlin 2.2.20 for projects targeting... iOS and web" — [Compatibility and versioning](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)
- Dependências do CMP 1.12.2: Material3 `org.jetbrains.compose.material3:material3*:1.12.0-alpha03` (baseado no Material3 1.5.0-alpha22) e Material3 Adaptive 1.3.0. No iOS, o scroll de lazy layouts ficou mais rápido porque a desativação de itens saiu da fase de desenho. Skia M150. `LocalSystemTheme` foi depreciado — [What's new CMP 1.12](https://kotlinlang.org/docs/multiplatform/whats-new-compose-112.html)
- **CMP 1.11.0 (maio/2026):** entrada de texto nativa opt-in via `PlatformImeOptions.usingNativeTextInput(enabled)`, com cursor e seleção nativos, lupa, autocorreção e autofill. Nova API `ComposeUIView` para embutir Compose numa hierarquia de views, e auto-sizing com Auto Layout (`rememberUIKitInteropRemeasureRequester()`). `parallelRendering` ligado por padrão. **Os alvos Apple x86_64 deixaram de ser suportados.** Breaking change: Dialog e Popup passam a ficar numa transition view acima do root VC. Na acessibilidade, entraram link semantics e uma correção da ordem de navegação — [CMP CHANGELOG](https://raw.githubusercontent.com/JetBrains/compose-multiplatform/master/CHANGELOG.md)
- **CMP 1.12.0 (agosto/2026):** alças de seleção no estilo iOS e item "Select" no menu de contexto. Correções no swipe-back (salto de conteúdo, conflito com `HorizontalPager`, RTL) e quedas de frame durante drag. Correções de escala de `UIKitView`/`UIKitViewController` e de `ComposeUIViewController` dentro de SwiftUI. `LiveRegion` e TTS verbatim no VoiceOver. A 1.12.1 corrige a toolbar no iOS 16 ou anterior. A 1.12.2 (outubro/2026) corrige um crash quando um `TextField` perde o foco porque um `Dialog` abriu — [CMP CHANGELOG](https://raw.githubusercontent.com/JetBrains/compose-multiplatform/master/CHANGELOG.md)
- Acessibilidade: a semântica do Compose é mapeada para objetos de acessibilidade do iOS, automaticamente na maioria dos widgets Material. `testTag` vira `accessibilityIdentifier`, e `performAccessibilityAudit()` funciona via XCTest. Alto contraste precisa ser montado à mão (`UIAccessibilityDarkerSystemColorsEnabled`). `AccessibilitySyncOptions` é `@ExperimentalComposeApi`, e a página diz que desde a 1.8.0 a árvore é sincronizada sob demanda — [iOS accessibility (kotlinlang)](https://kotlinlang.org/docs/multiplatform/compose-ios-accessibility.html)
- Insets e teclado (relatos da comunidade, não documentação oficial): um bug em que o iOS ignorava `consumeWindowInsets` (CMP-9390) foi corrigido na 1.11.0-alpha02. Há relatos de `WindowInsets.ime.getBottom` instável conforme o layout e de padding horizontal inesperado em `WindowInsets.systemBars` — [Slack 1](https://slack-chats.kotlinlang.org/t/33214431/when-using-windowinsetspadding-windowinsets-safedrawing-only), [Slack 2](https://slack-chats.kotlinlang.org/t/33110830/is-there-a-compose-multiplatform-way-to-detect-soft-keyboard)
- Interop: `ComposeUIViewController` (Compose dentro de UIKit/SwiftUI) e `UIKitView`/`UIKitViewController` (UIKit dentro de Compose) são as pontes nos dois sentidos — [issoh.co.jp, guia de ago/2026](https://www.issoh.co.jp/tech/details/16032/), confirmado pelos itens do CHANGELOG acima. Uma palestra de 2026 cita renderização mais rápida no iOS, input melhor e interop com elementos Liquid Glass — [Speaker Deck, zsmb, droidcon USA 2026](https://speakerdeck.com/zsmb/whats-new-in-compose-multiplatform-droidcon-usa-2026)

### Inferences
- **Material 3 no iOS:** o app vai parecer Android/Material, não nativo de iOS. Para um app de treino com identidade própria isso costuma ser aceitável. O que mais se sente são a navegação (swipe-back) e o teclado. O swipe-back nativo some se houver transições customizadas no `NavHost`.
- **Recursos:** `painterResource(R.drawable...)`/`stringResource(R.string...)` têm de migrar para Compose Multiplatform Resources (`Res.drawable.*`, `Res.string.*` em `composeResources/`). É um esforço mecânico, médio, proporcional ao número de strings e ícones. Não pesquisei esse ponto nesta rodada; vem do conhecimento geral sobre o CMP.
- **Ligar `usingNativeTextInput` nos campos de peso e repetições** deve aproximar a edição do comportamento iOS. Por ser opt-in recente, convém testar em aparelho (iOS 16, 17 e 26).
- **Pontos de risco do Mooscles:** o posicionamento do timer acima do teclado, já um tema na v1.4.5, e o uso de `WindowInsets.ime`. Prever `expect/actual` ou ajustes por plataforma.

### Gaps
- Não tenho números oficiais de desempenho de scroll e startup no iOS, nem comparação com SwiftUI.
- Não achei documentação oficial de insets de IME específica do iOS na 1.12, nem do suporte a Dynamic Type.
- Não conferi o que o CHANGELOG diz sobre Liquid Glass e iOS 26.

---

## 4. Substitutos para as partes só-Android: timer de descanso, imagem do card de compartilhamento, share sheet e agendamento em segundo plano

### Takeaway
No iOS, o app é suspenso pouco depois de ir para o segundo plano e os timers param. O timer de descanso tem de ser recriado como estado derivado de um timestamp de fim, guardado no código comum, com uma notificação local (`UNTimeIntervalNotificationTrigger`) agendada para esse instante. Na volta ao primeiro plano, o app recalcula o tempo restante. O `BGTaskScheduler` é oportunista: o sistema decide quando roda, então não substitui o WorkManager para nada com horário marcado. O card de compartilhamento pode ser desenhado em Compose/Skia comum (`ImageBitmap`) e passar por uma `expect/actual` de share que no iOS usa `UIActivityViewController`.

### Cited Findings
- iOS: "iOS will suspend your app shortly after the user moves it to the background… your timers won't run". A recomendação do DTS da Apple é agendar uma notificação local: o sistema a apresenta no horário mesmo com o app suspenso. Com `UNTimeIntervalNotificationTrigger` dá para ler `nextTriggerDate` e ressincronizar o timer na volta — [Apple Developer Forums 133640](https://developer.apple.com/forums/thread/133640), [thread 697842](https://developer.apple.com/forums/thread/697842)
- Notificações tocam som, mas com o app suspenso não executam código nem fazem vibração com padrão próprio — [Apple Developer Forums 127444](https://developer.apple.com/forums/thread/127444)
- `BGAppRefreshTask` serve para atualizar conteúdo, e no `BGProcessingTask` "the system decides the best time to launch your background task" — [Apple: Choosing background strategies](https://developer.apple.com/documentation/backgroundtasks/choosing-background-strategies-for-your-app.md)
- DTS da Apple: o sistema "at its discretion, dynamically determine[s] when it is appropriate to run your task"; o horário "is not deterministic". `earliestBeginDate` é só um piso. `getPendingTaskRequests` lista o que está pendente — [Apple Developer Forums 770479](https://developer.apple.com/forums/thread/770479); análise complementar em [Mert Bulan](https://mertbulan.com/dont-rely-on-bgapprefreshtask-for-your-apps-business-logic/)
- Captura de composable para `ImageBitmap`/ByteArray em KMP: bibliotecas de terceiros Capturable e KMP-Capturable-Compose, esta última com helper de share sheet — [Capturable (klibs)](https://klibs.io/project/jmseb3/Capturable), [KMP Capturable Compose](https://klibs.io/project/suwasto/KMP-Capturable-Compose)
- Share multiplataforma: a `kmp-sharing` da Software Mansion usa `UIActivityViewController` no iOS e recebe uma âncora para posicionar a folha, necessária no iPad — [kmp-sharing (klibs)](https://klibs.io/project/software-mansion/kmp-sharing)
- A biblioteca WorkManager não aparece na lista oficial de bibliotecas Jetpack com KMP — [Android KMP overview](https://developer.android.com/kotlin/multiplatform)

### Inferences
- **Timer de descanso (esforço médio, risco médio):**
  - `commonMain`: a máquina de estados do timer (`endsAt: Instant`, pausa e retomada, `Flow` de ticks com `delay`/`kotlin.time`) e a persistência do `endsAt` no DataStore ou Room.
  - `expect interface RestTimerScheduler { schedule(endsAt); cancel() }`.
  - `androidMain`: o AlarmManager/foreground service atual.
  - `iosMain`: `UNUserNotificationCenter.add(UNNotificationRequest(trigger = UNTimeIntervalNotificationTrigger(...)))`, pedido de permissão (`requestAuthorization`) e `removePendingNotificationRequests` ao cancelar ou pausar.
  - Pausar significa cancelar a notificação e guardar o tempo restante.
- **Live Activity / Dynamic Island** seria o equivalente iOS da notificação persistente com contagem regressiva. Isso exige uma extensão de widget em Swift (ActivityKit), fora do KMP. Não pesquisei; fica como item opcional.
- **WorkManager:** no iOS não há equivalente garantido. Tarefas deferíveis (backup, limpeza, sync) podem usar `BGAppRefreshTask`/`BGProcessingTask`, registrados no `Info.plist` (`BGTaskSchedulerPermittedIdentifiers`) e em `AppDelegate`. O ideal é executá-las de forma idempotente também ao abrir o app. A lógica do worker vai para `commonMain` como `suspend fun`; o agendamento fica por plataforma.
- **Card de compartilhamento:** se hoje ele usa `android.graphics.Bitmap`/`Canvas`, dá para reescrever o desenho em `androidx.compose.ui.graphics.Canvas`/`DrawScope` sobre um `ImageBitmap`, ou capturar um composable fora da tela via `rememberGraphicsLayer()` + `toImageBitmap()`. A codificação para PNG e o compartilhamento ficam em `expect/actual`: no iOS, `ImageBitmap` → `toSkiaImage().encodeToData()` → `NSData` → `UIImage` → `UIActivityViewController`; no Android, o fluxo atual com `FileProvider`. Não conferi na documentação oficial a disponibilidade de `GraphicsLayer.toImageBitmap()` no iOS.
- **Permissões:** a permissão de notificação no iOS também é `expect/actual`. O Android 13+ já pede `POST_NOTIFICATIONS`, então a UX de pedir permissão pode ser compartilhada.

### Gaps
- Não achei documentação oficial confirmando `GraphicsLayer.toImageBitmap()` e a API de encode no iOS dentro do CMP 1.12.
- Não pesquisei o comportamento de Live Activities para timers, nem os limites de notificações pendentes (o teto conhecido é 64; não verificado).

---

## 5. Estrutura recomendada do projeto, versões de Gradle/AGP/Kotlin, integração com Xcode e testes em commonTest

### Takeaway
Com AGP 9 a estrutura é obrigatória: módulo `:shared` com o plugin `com.android.kotlin.multiplatform.library`, `:androidApp` com `com.android.application` e `iosApp/` como projeto Xcode. O plugin novo não tem build variants. O AGP 10, previsto para o segundo semestre de 2026, remove as APIs legadas. Kotlin atual: 2.4.20 (07/09/2026). O Swift export ainda é pré-estável, alpha desde a 2.4.0. Para expor Flows e `suspend` ao Swift, o caminho de produção hoje é o framework Obj-C mais SKIE, ou o framework direto se a UI iOS for Compose, porque aí quase nada de Kotlin chega ao Swift.

### Cited Findings
- "When used along with Android Gradle plugin 9.0 or newer, the Kotlin Multiplatform Gradle plugin stops being compatible with the com.android.application and the com.android.library plugins". O ponto de entrada Android precisa ir para um módulo próprio (`androidApp`). O módulo compartilhado usa `kotlin { androidLibrary { namespace, compileSdk, androidResources { enable = true } } }`. Sem build variants: `debugImplementation(...)` vira `androidRuntimeClasspath(...)`. `android.enableLegacyVariantApi=true` é um paliativo, e "legacy APIs are going to be removed completely in AGP 10, which is likely to be released in the second half of 2026". O Android Studio suporta AGP 9 a partir do Otter 3 Feature Drop 2025.2.3 — [Kotlin: AGP 9 migration](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html)
- O Kotlin 2.3.0 já mostra aviso de depreciação para `com.android.library` em módulos KMP — [Kotlin Slack](https://slack-chats.kotlinlang.org/t/32890391/after-updating-to-kotlin-2-3-0-i-m-getting-the-message-warni)
- Para criar o módulo compartilhado, a recomendação oficial é o wizard de módulo KMP do Android Studio, que já aplica o plugin Android-KMP. Previews do Compose funcionam em `commonMain` — [Android KMP overview](https://developer.android.com/kotlin/multiplatform)
- **Kotlin 2.4.20** saiu em 07/09/2026 e é compatível com Gradle 7.6.3 a 9.7.0. Novidades no Swift export: sealed classes viram enums Swift e há herança entre linguagens. `assembleSharedXCFramework` gera `Package.swift` para distribuir via SwiftPM. A compilação incremental de klib está em Beta (`kotlin.incremental.native=true`). `watchosArm32` foi depreciado, com remoção prevista no Kotlin 2.5.0, que mira compatibilidade com o Xcode 27. A página põe o Swift export entre os recursos "pre-stable" — [What's new in Kotlin 2.4.20](https://kotlinlang.org/docs/whatsnew2420.html)
- O Swift export chegou a Alpha no Kotlin 2.4.0, com `suspend` → `async` e `Flow` → `AsyncSequence`. As fontes discordam sobre a data de lançamento da 2.4.0 (03/06 ou 01/07/2026) — [youngju.dev](https://www.youngju.dev/blog/2026-07-16-kotlin-swift-export-alpha.en), [alternativeto](https://alternativeto.net/news/2026/9/kotlin-2-4-20-brings-swift-export-and-gradle-9-7-support/). São fontes secundárias.
- SKIE (Touchlab, open source): `Flow` → `AsyncSequence` com o tipo preservado, `suspend` → `async` com cancelamento, enums e sealed classes exaustivos em `switch`, overloads para argumentos default — [SKIE](https://skie.touchlab.co/). O guia de migração avisa que combinar SKIE com KMP-NativeCoroutines tende a quebrar — [SKIE Migration Guide](https://touchlab.co/skie-migration)
- Para um módulo com Compose como UI: `kotlin.androidLibrary {}` substitui `androidTarget {}`, e o app Android depende de `projects.composeApp`/`shared` — [Kotlin: AGP 9 migration](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html)

### Inferences
- **Estrutura sugerida para o Mooscles:**
  ```
  settings.gradle.kts
  shared/            (com.android.kotlin.multiplatform.library + KSP + room plugin + compose MP)
    src/commonMain   (domínio, repositórios, Room, DataStore, ViewModels, UI Compose MP na fase 2)
    src/androidMain  (builders de DB/DataStore com Context, AlarmManager, WorkManager, share)
    src/iosMain      (paths NSDocumentDirectory, UNUserNotificationCenter, BGTaskScheduler, UIActivityViewController, MainViewController.kt)
    src/commonTest   (kotlin.test + kotlinx-coroutines-test + Turbine; DAO tests com BundledSQLiteDriver)
    src/androidHostTest (Robolectric) / androidDeviceTest (MigrationTestHelper)
    schemas/         (movidos de app/schemas, mantendo o histórico JSON)
  androidApp/        (com.android.application: Application, MainActivity, Hilt/Koin, manifest, flavors/variants se houver)
  iosApp/            (projeto Xcode; SwiftUI App que hospeda ComposeUIViewController ou telas SwiftUI)
  ```
- **Versões-alvo em outubro de 2026, pelas fontes acima:** Kotlin 2.4.20 (K2 é o padrão desde o 2.0), CMP 1.12.2, AGP 9.x, Gradle ≤ 9.7, KSP compatível com 2.4.20, Room 2.8.5 ou room3 3.0.3, DataStore 1.3.0, Lifecycle 2.11.0, Navigation JetBrains 2.10.x. **O Room 3.1 exige kotlinc 2.2+.**
- Se o app Android depende de **build variants/flavors** (debug/release com configs diferentes), essas configurações têm de ficar no `:androidApp`, porque o plugin Android-KMP não tem variants. É um custo de reorganização baixo a médio.
- **Integração com o Xcode:**
  - (a) Para UI 100% Compose MP, o framework estático via `embedAndSignAppleFrameworkForXcode` numa Run Script phase basta. O Swift só chama `MainViewControllerKt.MainViewController()`. É a opção mais simples.
  - (b) Para telas SwiftUI que consomem ViewModels Kotlin, use SKIE, maduro e já usado em produção.
  - (c) Swift export ainda é alpha/pré-estável: avaliar, não adotar.
  - (d) SwiftPM export/XCFramework só faz sentido se o shared for distribuído como pacote para outro repositório.
- A CocoaPods não é necessária para esse arranjo. Não verifiquei se ela foi formalmente depreciada.
- **Testes:**
  - O que hoje usa Robolectric só por causa de `Context` (DataStore, Room in-memory) pode ir para `commonTest` com implementações por arquivo temporário.
  - O `kotlinx-coroutines-test` (`runTest`) é multiplataforma.
  - `iosSimulatorArm64Test` roda os testes comuns no simulador; o Mac da máquina de CI precisa ter Xcode.
  - Testes de UI Compose em `commonTest` com `runComposeUiTest` existem no CMP. Não verifiquei nesta rodada.

### Gaps
- Não conferi a versão atual do SKIE nem sua compatibilidade declarada com Kotlin 2.4.x (um post sem data falava de 2.0.0–2.3.10). **Antes de subir para Kotlin 2.4.20 com SKIE, confirmar nas release notes do SKIE.**
- Não conferi a versão exata do AGP 9.x atual nem o status do AGP 10.
- Não verifiquei a documentação oficial dos source sets `androidHostTest`/`androidDeviceTest` e do Robolectric com o plugin Android-KMP (`withHostTest { isIncludeAndroidResources = true }`).

---

## 6. Estratégias incrementais e casos reais

### Takeaway
O padrão mais documentado é compartilhar primeiro a lógica (dados e domínio) mantendo a UI nativa e depois, se fizer sentido, levar a UI para Compose Multiplatform. É o caminho de Google Docs, McDonald's, Memrise, Careem e Bitkey (este chegou a 95% de código compartilhado). Os prazos públicos são longos e vagos: Kuaishou 2 anos, Philo e Prezzee 3 anos, Cash App desde 2018. Não há casos públicos de um app pequeno e solo; o Mooscles tem a vantagem de já ser Kotlin, Compose e Room, então a UI pode ir para o CMP quase sem reescrever.

### Cited Findings
- Google Docs: "the rollout of the Google Docs app for Android, iOS, and Web, which leverages KMP for shared business logic, validating its readiness for production use at Google scale". O ganho de 18% de desempenho e até 2x no build são benchmarks do compilador Kotlin/Native, não números do Docs — [Android Developers Blog, mai/2024](https://android-developers.googleblog.com/2024/05/android-support-for-kotlin-multiplatform-to-share-business-logic-across-mobile-web-server-desktop.html)
- Apps citados pelo Google como usuários de KMP: Blinkit, Cash App, Duolingo, Forbes, Google Docs, JioHotstar, Stone, Swiggy, Ultrahuman, Wrike e Zomato — [Android KMP overview](https://developer.android.com/kotlin/multiplatform)
- Casos JetBrains — [Case studies (kotlinlang)](https://kotlinlang.org/lp/multiplatform/case-studies/):
  - **McDonald's** (via Umain) começou por um teste num fluxo de pagamentos, depois expandiu para o app inteiro e uniu os times Android e iOS. Relata menos crashes.
  - **Bolt** começou pelo reconnect do chat e depois levou o motor de chat inteiro.
  - **Memrise** migrou a lógica aos poucos e manteve a UI nativa ([post](https://engineering.memrise.com/kotlin-multiplatform-memrise-3764b5a4a0db)).
  - **Bitkey (Block)** compartilha 95% do código mobile e depois migrou a UI para CMP ([post](https://engineering.block.xyz/blog/how-bitkey-uses-cross-platform-development)).
  - **Careem** adotou KMP durante a migração do Android para Kotlin.
  - **Forbes** compartilha mais de 80% da lógica ([post](https://www.forbes.com/sites/forbes-engineering/2023/11/13/forbes-mobile-app-shifts-to-kotlin-multiplatform/)).
  - **Cash App** migra aos poucos desde 2018.
  - Durações explícitas: Kuaishou 2 anos, Philo 3 anos, Prezzee 3 anos.
  - **Markaz**, 100% CMP: app com menos de 10 MB no Android e 137 MB no iOS.
  - **Perk** fez o caminho inverso: começou por um componente de UI compartilhado e depois levou o design system ([post](https://builders.travelperk.com/compose-multiplatform-at-perk-a-pragmatic-look-at-our-journey-so-far-fedd666e9726)).
- Os números dos casos JetBrains são autodeclarados pelas empresas — [Case studies (kotlinlang)](https://kotlinlang.org/lp/multiplatform/case-studies/)
- Metodologia incremental (Theodo): migrar uma camada ou uma feature por vez, para limitar riscos — [Theodo](https://apps.theodo.com/en/article/migration-dune-application-ios-android-vers-kotlin-multiplatform-methodologie-et-etapes-cles)
- Não há fonte pública sobre KMP no iOS do Duolingo. O post que encontrei trata da migração Java → Kotlin no Android (2 anos, cerca de 30% menos linhas) — [Duolingo blog](https://blog.duolingo.com/migrating-duolingos-android-app-to-100-kotlin)

### Inferences
**Plano incremental para o Mooscles.** As estimativas são para 1 dev e não vêm de fonte.

- **Fase 0, preparação, só Android: dias a 1–2 semanas. Risco baixo.**
  - Subir para Kotlin 2.4.x, AGP 9 e Room 2.8.5.
  - Ligar `BundledSQLiteDriver` no Android.
  - Converter DAOs para `suspend`/`Flow`, `withTransaction` para a API de conexões e as migrations para `SQLiteConnection`.
  - Trocar `java.time`/`java.util` por `kotlinx-datetime`/`kotlin.time`/`kotlin.uuid`, sem mudar as colunas.
  - Garantir que os testes de migração com os schemas exportados continuam verdes.
  - Publicar essa versão Android antes de mexer na estrutura.
- **Fase 1, `:shared` com dados e domínio, Android ainda é o único app: 1–3 semanas. Risco médio, por causa da integridade dos dados.**
  - Criar `:shared` com o plugin Android-KMP e mover Room (com `schemas/`), DataStore (no mesmo caminho de arquivo), repositórios, casos de uso, a lógica do timer e os ViewModels.
  - O `:androidApp` mantém a UI Compose, o DI, o WorkManager e o AlarmManager.
  - Validar com um teste instrumentado que abre um banco e um DataStore criados pela versão anterior.
- **Fase 2, UI no iOS.**
  - **Opção A, recomendada para um dev só e um app já em Compose M3:** levar as telas para `commonMain` com Compose Multiplatform, trocando `R.*` por `Res.*` e as APIs Android por `expect/actual`, e hospedar num `ComposeUIViewController` dentro de um `App` SwiftUI mínimo. Esforço médio (2–6 semanas, conforme o número de telas). Riscos: teclado e insets, swipe-back, sensação "não nativa".
  - **Opção B:** UI em SwiftUI consumindo o `:shared` via SKIE. Uma segunda UI inteira para manter, com esforço alto e contínuo.
  - **Opção C, híbrida:** CMP nas telas e SwiftUI para a navegação de nível superior e a tab bar (Liquid Glass), como sugere a própria documentação de navegação.
- **Fase 3, plataforma iOS: 1–3 semanas. Risco médio.**
  - Timer via `UNUserNotificationCenter`, tarefas deferíveis via `BGTaskScheduler`, card de compartilhamento via `ImageBitmap` + `UIActivityViewController`, ícones, permissões, App Store e TestFlight.
  - Opcional: Live Activity em Swift.

**Classificação resumida dos componentes**

| Componente atual | Destino | Esforço / risco |
|---|---|---|
| Entidades, DAOs e AppDatabase do Room (schemas exportados) | `commonMain` (o builder vai para `expect/actual` por causa do caminho) | Médio / médio-alto (migrations para `SQLiteConnection`, converters `java.*`, DAOs bloqueantes) |
| Migrations manuais | `commonMain` (`Migration.migrate(SQLiteConnection)`) | Médio / alto: testar com bancos reais |
| DataStore Preferences | `commonMain` (factory) + `actual` do caminho | Baixo / médio: manter o caminho `datastore/*.preferences_pb` |
| ViewModels, Flows, coroutines | `commonMain` sem mudança, a não ser `AndroidViewModel`/`Context` | Baixo |
| Navigation Compose type-safe | `commonMain` (`org.jetbrains.androidx.navigation`) | Baixo a médio; atenção a transições versus swipe-back |
| UI Compose + Material 3 | `commonMain` via CMP | Médio: recursos, insets, `LocalContext`, `BackHandler`, previews |
| Timer via AlarmManager | Lógica em `commonMain`; agendamento em `expect/actual` (AlarmManager / UNUserNotificationCenter) | Médio / médio |
| WorkManager | `actual` Android; no iOS, `BGTaskScheduler` (oportunista) mais execução ao abrir o app | Médio / médio |
| Card de compartilhamento (Bitmap/Canvas) | Desenho em Compose `ImageBitmap` comum; encode e share em `expect/actual` | Médio / baixo-médio |
| Testes Robolectric | Lógica pura vai para `commonTest`; o que depende de `Context` fica em `androidHostTest`; migrations em `androidDeviceTest` | Médio |
| Hilt (se usado) | Fica no `androidApp`; para compartilhar DI, Koin ou injeção manual | Médio |

### Gaps
- Não achei casos públicos com prazo e tamanho de equipe para apps pequenos ou de um dev só. As estimativas acima são inferência.
- Não encontrei o case study detalhado do Google Docs/Workspace com métricas no iOS, nem dados públicos de Netflix ou Duolingo sobre KMP no iOS.
