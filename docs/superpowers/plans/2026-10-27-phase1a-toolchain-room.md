# Fase 1A: ferramentas e Room pronto para KMP — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Atualizar o app (ainda num módulo só) para Kotlin 2.4, AGP 9, Room 2.8 com a API de driver e DataStore 1.3, sem nenhuma mudança visível e sem tocar nos dados de quem já usa o app.

**Architecture:** São três passos que não se misturam. Primeiro sobem as ferramentas e as bibliotecas, sem mudar nada no Room. Depois o Room passa a usar o `AndroidSQLiteDriver`: as migrações são reescritas sobre `SQLiteConnection` e as transações usam a API de conexões, que é a mesma que o código comum vai usar na etapa 1B. No fim, a prova é a atualização por cima no emulador.

**Tech Stack:** Gradle 9, AGP 9, Kotlin 2.4, KSP2, Room 2.8, androidx.sqlite (driver do sistema), DataStore 1.3, Robolectric.

**Spec:** [docs/superpowers/specs/2026-10-27-phase1-shared-base-design.md](../specs/2026-10-27-phase1-shared-base-design.md) (etapa 1A)

## Global Constraints

- Branch `chore/phase1a-toolchain-room`, a partir da `main` do fork. O push vai só para `origin`, sem force. O merge na `main` acontece quando o CI fica verde.
- **Dados intactos.** Continuam iguais:
  - `mooscles.db`, a `version` 5, os nomes de tabelas e colunas e os schemas JSON;
  - os DataStores `local_preferences`, `app_updates` e o do timer, com as mesmas chaves e os mesmos tipos;
  - `applicationId`, `versionCode`, `versionName`, a assinatura e o contrato do `update.json`.
- **Nenhuma mudança perceptível.** Nada muda em tela, texto ou comportamento. O `targetSdk` continua 35; o `compileSdk` pode subir se uma biblioteca exigir, e isso fica registrado. O APK debug não cresce mais que cerca de 1 MB.
- **Driver.** No Android, `AndroidSQLiteDriver`, o SQLite do sistema. Nesta etapa não entra `BundledSQLiteDriver`.
- **Versões.** Usar as estáveis mais recentes e compatíveis entre si no dia, conferidas nas release notes oficiais e registradas no relatório. Pontos de partida: Kotlin 2.4.x, AGP 9.x, Room 2.8.x, DataStore 1.3.x.
- **Processo de velocidade.** Durante cada task, rodar só os testes da área mexida. A suíte inteira roda no CI, depois do push. O `compat-reviewer` é obrigatório nas Tasks 1, 2 e 3.

## Review Focus

1. **Treino ativo com o timer rodando no momento da atualização:** depois da troca de APK, o treino e o timer continuam. Coberto na Task 3 (emulador).
2. **Banco criado em cada versão antiga (v1 a v4) e migrado até a v5 pelas migrações reescritas:** o resultado é idêntico ao das migrações atuais, linha por linha. Coberto na Task 2, nos testes de migração existentes adaptados e num teste de equivalência entre a migração antiga e a nova.
3. **Uma transação que falha no meio, como o import com `beforeCommit` lançando erro, continua desfazendo tudo** com a API nova. Coberto na Task 2 (`BackupRepositoryTest` existente).
4. **A abertura a frio com o histórico grande não piora.** Coberto na Task 3 (medição).
5. **O lint e o R8 (build `performance`) continuam passando** com as versões novas. Coberto na Task 1.

---

### Task 1: Ferramentas e bibliotecas

**Files:**
- Modify: `gradle/wrapper/gradle-wrapper.properties`, `build.gradle.kts`, `app/build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` (só o que a migração exigir), `gradle/gradle-daemon-jvm.properties` (só se o Gradle novo exigir)
- Modify: o código Kotlin só onde a versão nova quebrar a compilação (deprecações viradas erro, APIs renomeadas), sem mudar comportamento.

**Interfaces:**
- Produces: o build com Gradle 9, AGP 9 (Kotlin embutido do AGP, sem o plugin `org.jetbrains.kotlin.android` separado, se o AGP 9 assim exigir), Kotlin 2.4 e o plugin Compose/serialization na mesma versão, KSP2, Room **ainda em 2.7.2**, DataStore 1.3.x, e Lifecycle, Navigation, Activity, Core, Work, Coroutines e Serialization atualizados.

- [ ] **Step 1:** Montar a tabela de versões (versão atual → nova, com a fonte de cada uma) e confirmar a compatibilidade cruzada: Kotlin↔KSP, AGP↔Gradle, Compose BOM↔Kotlin, Robolectric↔compileSdk.
- [ ] **Step 2:** Atualizar, seguindo o guia oficial de migração do AGP 9 e mudando só o necessário.
- [ ] **Step 3:** Verificar, localmente:
  - `./gradlew :app:testDebugUnitTest --tests "com.mooscles.data.*" --tests "com.mooscles.domain.*" --tests "com.mooscles.backup.*"` verde;
  - `./gradlew :app:lintDebug` sem erros novos;
  - `./gradlew :app:assembleDebug :app:assemblePerformance` constroem;
  - `git diff --stat -- app/schemas` vazio;
  - o tamanho do APK debug antes e depois.
- [ ] **Step 4:** Rodar o `compat-reviewer`, que deve dar `SEGURO`.
- [ ] **Step 5:** Fazer o commit com `build: Gradle 9, AGP 9, Kotlin 2.4 and library updates` e o push. O CI roda a suíte inteira.

### Task 2: Room 2.8 com driver e API de conexões

**Files:**
- Modify: `app/build.gradle.kts` (Room 2.8.x, `androidx.sqlite:sqlite-framework` ou o artefato que traz o `AndroidSQLiteDriver`, e `room-testing` 2.8.x)
- Modify: `app/src/main/java/com/mooscles/data/WorkoutDatabase.kt` (migrações e callback de criação)
- Modify: `app/src/main/java/com/mooscles/data/WorkoutRepository.kt` (transações)
- Modify: os testes que usam `openHelper`/`SupportSQLiteDatabase`: `RoutineTargetMigrationTest`, `RoutineFolderPersistenceTest`, `RollbackV142DataCompatibilityTest`, `AppViewModelFoldersDiscardHostTest` e outros que aparecerem
- Test: `app/src/test/java/com/mooscles/data/MigrationEquivalenceTest.kt` (novo)

**Interfaces:**
- Consumes: o build da Task 1.
- Produces:
  - `WorkoutDatabase.open(context)` monta o banco com `.setDriver(AndroidSQLiteDriver())`, com o mesmo nome `mooscles.db` e as mesmas migrações registradas;
  - `MIGRATION_1_2` … `MIGRATION_4_5` implementam `migrate(connection: SQLiteConnection)`, com SQL idêntico ao atual;
  - o callback de criação chama `createDefaultFolder(connection)`;
  - todo `db.withTransaction { … }` do repositório passa a `db.useWriterConnection { it.immediateTransaction { … } }`, ou ao helper equivalente do Room 2.8 disponível em `commonMain`. A semântica fica igual: tudo ou nada, aninhamento e leituras dentro da transação;
  - `WorkoutRepository.data`, a leitura de todas as tabelas numa transação a cada invalidação, mantém a mesma garantia de snapshot consistente.

- [ ] **Step 1: Teste que falha.** `MigrationEquivalenceTest.migrationsProduceTheSameDatabaseAsBefore`:
  - para cada versão inicial de 1 a 4, cria o banco a partir do schema JSON exportado daquela versão, com dados de exemplo representativos (as mesmas fixtures dos testes de migração existentes);
  - migra até a v5 com as migrações novas;
  - compara o dump completo, linha por linha e tabela por tabela, com um dump de referência gerado pelas migrações atuais. A referência é gravada antes da troca, como recurso de teste, e o teste explica como ela foi gerada.
- [ ] **Step 2:** Ver o teste falhar, porque a API nova ainda não existe.
- [ ] **Step 3:** Implementar: migrações e callback sobre `SQLiteConnection`, `setDriver(AndroidSQLiteDriver())`, transações pela API de conexões, e os testes adaptados à API de driver, sem enfraquecer nenhuma asserção.
- [ ] **Step 4:** Rodar, verdes:
  - `MigrationEquivalenceTest`, `RoutineTargetMigrationTest`, `RoutineFolderPersistenceTest`, `RollbackV142DataCompatibilityTest`, `BackupRepositoryTest` (o rollback do Review Focus 3), `RoomPersistenceTest` e `AppViewModelFoldersDiscardHostTest`;
  - `git diff --stat -- app/schemas` vazio.
- [ ] **Step 5:** Rodar o `compat-reviewer`, que deve dar `SEGURO`, e fazer o commit com `data: Room 2.8 with the system SQLite driver and connection transactions` e o push.

### Task 3: Atualização por cima no emulador, medições e docs

**Files:**
- Create: `docs/superpowers/reports/2026-10-27-phase1a-verificacao.md`
- Modify: `AGENTS.md` (versões em "Ambiente e comandos"; em "Compatibilidade", o driver e a API de conexões), `docs/superpowers/ROADMAP.md` (a etapa 1A concluída, com links), `docs/superpowers/releases/v1.5.0.md` (pendências e notas, se houver)

- [ ] **Step 1: Atualização por cima** (Review Focus 1), só no `emulator-5554`:
  1. Instalar o APK da `main` de antes desta branch.
  2. Criar os dados:
     - rotinas em duas pastas;
     - alguns treinos finalizados com recordes;
     - um treino ativo com séries preenchidas e o timer de descanso rodando;
     - preferências diferentes do padrão (lb, tema escuro, descanso padrão mudado);
     - um export JSON.
  3. Instalar o APK desta branch com `adb install -r`, sem limpar os dados.
  4. Conferir:
     - o histórico, os recordes e as rotinas;
     - o treino ativo com o timer retomado;
     - as preferências;
     - um novo export, que tem que ser igual ao anterior, desconsiderando `exportedAt` e `app.versionCode`/`versionName`.
  5. Guardar prints e os dois JSON (em `docs/superpowers/reports/2026-10-27-phase1a-verificacao/`, sem dado pessoal).
- [ ] **Step 2: Desempenho** (Review Focus 4): repetir as medições do [relatório de 2026-10-27](../reports/2026-10-27-pr-off-main-thread-verificacao.md) com a base grande (abertura a frio 3×, import, troca kg/lb) no APK debug, agora com a GPU do host ligada. Comparar com uma medição da `main` feita na mesma sessão, com a mesma GPU.
- [ ] **Step 3: Tamanho do APK** debug, antes e depois.
- [ ] **Step 4:** Escrever o relatório e atualizar os docs, listando o que **não** foi verificado.
- [ ] **Step 5:** Fazer o commit com `docs(phase1a): record upgrade-in-place verification` e o push. Com o CI verde, o controlador faz a revisão final e o merge na `main` do fork.
