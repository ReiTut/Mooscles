# Fase 1: base multiplataforma, ainda só Android

Data: 2026-10-27. Item da fase 1 do [ROADMAP](../ROADMAP.md). O usuário aprovou seguir a recomendação da [pesquisa de arquitetura](../reports/2026-10-27-arquitetura-android-ios.md) (Kotlin Multiplatform com Compose Multiplatform, em fases) e fazer esta base antes do redesign visual. Ele não tem iPhone. As decisões abaixo são minhas ("pode seguir com sua recomendação"), salvo quando indicado.

## Por que

O produto vai para o iOS e passa por um redesign completo. Se o redesign viesse primeiro, as telas novas seriam escritas no módulo Android e reescritas depois. Fazendo a base primeiro:

- dados, regras e estado das telas passam a ficar em código comum, uma vez só;
- a fase 2 (design system) e a fase 3 (treino ativo) já nascem compartilháveis;
- o risco mais irreversível, que é o banco e as preferências de quem já usa o app, fica isolado numa fase sem nenhuma mudança visível.

## Objetivos

1. **Ferramentas atualizadas** para as versões estáveis exigidas pelo Room, DataStore e Lifecycle multiplataforma:
   - Kotlin 2.4.x, AGP 9 com o Gradle que ele exigir, KSP2;
   - Room 2.8.x, DataStore 1.3.x, Lifecycle/ViewModel e Navigation em versões com suporte KMP;
   - BOM do Compose atual.

   O plano confere as versões no dia, a partir das release notes oficiais.
2. **Módulo `:shared`** em Kotlin Multiplatform, com alvos Android e iOS (`iosArm64` e `iosSimulatorArm64`). Em `commonMain` ficam:
   - entidades, DAOs, `WorkoutDatabase`, as migrações e os `schemas/` do Room;
   - `AppPreferences` e a persistência do timer (DataStore Preferences);
   - `domain/`, o repositório, `backup/` (menos `BackupDocuments`) e o catálogo de exercícios;
   - `UiContract`, `WorkoutPresentation` e a lógica do `AppViewModel`.
3. **`:app` continua sendo o app Android**, com o mesmo nome de módulo. Nele ficam as telas Compose atuais, `MainActivity` e `MoosclesApplication` (que monta os singletons), o updater, o WorkManager, o `AlarmManager`/`RestAlarmReceiver`/notificações do timer, o renderizador e o envio do cartão, `BackupDocuments` (SAF) e tudo o que depende de `Context`.
4. **O iOS compila no CI.** Um job novo do GitHub Actions, num runner macOS que já tem Xcode, compila o `:shared` para os alvos iOS e roda `commonTest` no simulador (`iosSimulatorArm64Test`). É a prova de que o código comum é portável de verdade, sem precisar de iPhone nem de Xcode neste Mac.

## Fora de escopo

- Telas em Compose Multiplatform e qualquer mudança visual. As telas continuam em `:app`, com Jetpack Compose; vão para o código comum na fase 3.
- Um app iOS (`iosApp/`, projeto Xcode, `ComposeUIViewController`): sem iPhone e sem Xcode local, não daria para rodar nem verificar. Fica para quando houver como testar.
- Room 3 (`androidx.room3`), SKIE e Swift Export.
- Release v1.6. Pela decisão do usuário, o PR para o original só acontece no fim de todas as fases; esta fase termina com merge na `main` do fork e CI verde.

## Anti-objetivos (falha mesmo que "funcione")

- Perder, duplicar ou alterar dado de quem já tem o app. Isso inclui o banco `mooscles.db`, a `version` 5 do Room, os nomes de tabelas e colunas, e os arquivos DataStore `local_preferences`, `app_updates` e o do timer, com as mesmas chaves e os mesmos tipos.
- Qualquer mudança perceptível para o usuário: tela, texto, comportamento, desempenho pior que o medido em 2026-10-27, ou APK muito maior (mais de cerca de 1 MB).
- Quebrar o autoatualizador: `applicationId`, assinatura, `versionCode`, o contrato do `update.json` e `ReleaseUrls` ficam intactos.
- Recordes diferentes dos de hoje.
- Uma fase que fica semanas sem nada funcionando. Cada etapa termina com o app inteiro rodando e todos os testes verdes.

## Restrições técnicas

- **Duas etapas, cada uma com plano e branch próprios e com merge na `main` do fork quando ficar verde:**
  - **1A, ferramentas e Room KMP-ready**, ainda num módulo só:
    - atualizar Kotlin, AGP, Gradle, KSP e as bibliotecas;
    - reescrever as migrações para a API `SQLiteConnection`;
    - trocar `withTransaction` pela API de conexões do Room 2.8;
    - definir o driver.

    É a etapa de risco para os dados, então sai sozinha.
  - **1B, módulo `:shared`**: mover o código, trocar as APIs que só existem na JVM e criar o job iOS do CI.
- **Driver SQLite (minha escolha):** no Android, `AndroidSQLiteDriver`, que usa o SQLite do sistema como hoje. Assim o APK não cresce e o motor que abre o banco dos usuários não muda. No iOS, `BundledSQLiteDriver`. A alternativa de usar o `BundledSQLiteDriver` também no Android deixaria o SQLite igual nas duas plataformas, mas somaria cerca de 1 MB por ABI ao APK universal. Fica descartada por enquanto e pode ser revista quando houver build por ABI.
- **APIs que só existem na JVM e saem do código comum:**
  - `java.time` vira `kotlinx-datetime`;
  - `java.util.UUID` vira `kotlin.uuid.Uuid`, com o mesmo formato de texto nas colunas;
  - `String.format` e `Math.ulp` passam a ter equivalentes em Kotlin puro, com testes que provam a mesma saída, porque a tolerância dos recordes depende de `ulp`;
  - `java.text.Normalizer` (busca de exercícios) passa a ter um equivalente comum, com os mesmos resultados de busca.
- **Mesmos arquivos:** o DataStore no código comum abre o mesmo caminho que o `preferencesDataStore(name = …)` usa hoje (`files/datastore/<nome>.preferences_pb`). O Room abre o mesmo `mooscles.db` no mesmo lugar.
- **Testes:**
  - os testes de migração (`RoutineTargetMigrationTest`, `RoutineFolderPersistenceTest`, `RollbackV142DataCompatibilityTest`) continuam no lado Android, porque o `MigrationTestHelper` não tem caminho estável em `commonTest`;
  - os testes puros (cálculos, backup, planner, textos) vão para `commonTest` e rodam nos dois alvos;
  - a suíte de host continua com os 482 testes ou mais, todos verdes.
- **Repositório e ferramentas:**
  - os schemas vão para `shared/schemas/`;
  - o hook do `.claude/settings.json` que protege `app/schemas` passa a proteger os dois caminhos;
  - os scripts (`build.ps1`, `prepare-release.ps1`) continuam achando o APK em `app/build/outputs/apk/debug/`, porque o módulo `:app` não muda de nome;
  - o `AGENTS.md` (Arquitetura, Compatibilidade) é atualizado no fim de cada etapa.
- **Processo:** regras de velocidade de 2026-10-27. Cada task roda só os testes da parte que mexeu, a suíte inteira roda no CI e a profundidade da revisão segue o risco. O `compat-reviewer` é obrigatório nas duas etapas, porque as duas mexem em `data/`, em `AppPreferences` e no `build.gradle.kts`.

## Como verificar

- **CI verde:** host, lint, build e o script de release, e na etapa 1B também o job iOS.
- **Atualização por cima no emulador** (a prova principal), feita nas duas etapas:
  1. instalar o APK da `main` de antes da etapa e criar dados (rotinas em pastas, treinos com PR, um treino ativo com timer rodando, preferências diferentes do padrão e o estado do updater);
  2. instalar o APK novo por cima, com o mesmo debug key do Mac;
  3. conferir que tudo está igual: histórico, recordes, rotinas, o timer retomado, as preferências e o export, que deve ser idêntico ao exportado antes da atualização.

  Além disso, um teste instrumentado abre um banco v5 e um DataStore criados pela versão anterior.
- **Desempenho:** as mesmas medições da [verificação de 2026-10-27](../reports/2026-10-27-pr-off-main-thread-verificacao.md), com a base grande. Nenhuma piora acima do ruído.
- **Tamanho do APK:** o debug, antes e depois de cada etapa.
- **Relatórios:** `docs/superpowers/reports/2026-10-27-phase1a-verificacao.md` e `…-phase1b-verificacao.md`, cada um com o que foi e o que **não** foi verificado (por exemplo: o iOS só compila e roda testes no simulador do CI; nada roda num iPhone).

## O que fica para quem implementar

- As versões exatas, que devem ser as estáveis mais recentes compatíveis entre si no dia; os passos de migração do AGP 9 (o plugin Kotlin embutido e o plugin `com.android.kotlin.multiplatform.library`).
- Como dividir o `AppViewModel`: a lógica e o estado no código comum, e o que depende de `Application`, `Trace` ou `Build` entrando por interfaces ou por `expect/actual`.
- Onde ficam os helpers que substituem `ulp`/`format`/`Normalizer`, desde que cada um tenha um teste de equivalência contra a implementação da JVM.
- A ordem dos arquivos movidos, desde que cada commit compile e mantenha os testes verdes.

## Notas

- **2026-10-27, etapa 1A, tamanho do APK.** O APK debug passou do orçamento de "cerca de 1 MB". Medido com os dois APKs empacotados do zero (sem as sobras do empacotamento incremental do AGP), ele foi de 19.927.075 para 21.796.480 bytes: **+1,78 MiB (+9,4%)**. A Task 1 tinha medido +1,32 MiB contra um APK da `main` que ainda carregava ~0,47 MB de espaço morto (20.397.633 bytes) do empacotamento incremental. Quase todo o crescimento é dex das bibliotecas: o Compose UI 1.12 passou a depender de `androidx.window`, e o core 1.19, as coroutines 1.11 e os artefatos KMP novos somam o resto. O build R8 (`assemblePerformance`) cresceu só ~137 KiB. **Decisão do controlador: aceito**, porque o crescimento vem do Compose atual, que a fase 3 precisa de qualquer jeito. O usuário baixa ~1,9 MB a mais. Decisão confirmada com o número corrigido (+1,78 MiB); detalhes no [relatório da 1A](../reports/2026-10-27-phase1a-verificacao.md).
- **2026-10-27, etapa 1A, barra de navegação.** O indicador do item selecionado na barra inferior ficou mais estreito (64 → 56 dp) por causa da atualização do Material 3. Aceito, porque a fase 2 redesenha a navegação. Isso contradiz o antiobjetivo de "nenhuma mudança perceptível"; fica registrado.
- **2026-10-27, etapa 1A, tamanho do APK.** O APK debug cresceu +1,78 MiB (19,9 → 21,8 MB). O +1,32 MiB medido antes estava distorcido pelo espaço morto do empacotamento incremental. Aceito: o crescimento vem do `androidx.window`, trazido pelo Compose UI 1.12, que a fase 3 precisa.
- **2026-10-27, etapa 1A, teste instrumentado de atualização.** O teste instrumentado que abre um banco v5 e um DataStore criados pela versão anterior fica adiado para a etapa 1B, quando o DataStore passa para o código comum e o teste mais importa. A 1A se apoiou na atualização por cima no emulador e no `MigrationEquivalenceTest`.
- **2026-10-27, decisões do plano da 1B** ([plano](../plans/2026-10-27-phase1b-shared-module.md)). Três escolhas mudam o texto acima sem mudar o objetivo:
  1. **Datas:** usar `kotlin.time.Clock`/`Instant` em vez de `kotlinx-datetime`. É o mesmo tipo desde o kotlinx-datetime 0.7, com uma dependência a menos.
  2. **Busca de exercícios:** em vez de `expect/actual` sobre o `Normalizer`, o dobramento de acentos é uma tabela em Kotlin puro gerada da JVM 21 e conferida para todos os code points. Ela congela o comportamento atual (Unicode 15.0) e não depende do ICU de cada aparelho.
  3. **Código com `Context` no `:shared`:**
     - `WorkoutDatabase.open(context)` fica em `shared/src/androidMain`;
     - o delegate `preferencesDataStore` continua no `:app`, e o código comum recebe o `DataStore` pronto, para manter uma única instância por processo e o mesmo caminho de arquivo.
- **2026-10-28, etapa 1B, estado do updater na atualização por cima.** A verificação pede "o estado do updater" entre os dados criados com o APK antigo. Na 1B, a rede do emulador ficou desligada antes da primeira abertura do APK antigo, porque a 1.4.5 acha a release 1.4.6 publicada e começa a baixá-la, o que derrubou o emulador sob falta de memória na Task 9. O estado comparado é o que a 1.4.5 grava sem rede (`phase` IDLE, `lastInstalledVersion` 16). Ele ficou idêntico byte a byte depois da troca, mas o caminho com uma release encontrada e recusada, que a 1A cobriu, não foi repetido. Decisão do controlador; detalhes no [relatório da 1B](../reports/2026-10-27-phase1b-verificacao.md).
