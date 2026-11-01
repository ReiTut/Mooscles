# Recordes sem travar a tela: plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tirar o travamento de cerca de 48 s, e o ANR, que acontece na abertura e depois do import com um histórico grande, sem mudar nenhum recorde.

**Architecture:** Os recordes de todos os treinos do histórico passam a sair de uma passada cronológica única, em `WorkoutCalculations`, no lugar das N chamadas que hoje custam O(N) cada. `WorkoutPresentation.present` usa o resultado dessa passada. O fluxo `presentation` do `AppViewModel` passa a rodar num dispatcher de fundo com paralelismo 1, o que mantém a ordem e o estado interno do presenter.

**Tech Stack:** Kotlin 2.1.20, coroutines/Flow, JUnit 4 + Robolectric (sdk 35).

**Spec:** [docs/superpowers/specs/2026-10-27-pr-off-main-thread-design.md](../specs/2026-10-27-pr-off-main-thread-design.md)

## Global Constraints

- Branch `fix/pr-off-main-thread`, criada a partir de `feat/export-import`. Nada vai para a `main` sem PR.
- Nenhuma mudança de dados: Room continua na `version` 5, os schemas não mudam, as chaves do DataStore não mudam.
- O resultado tem que ser idêntico ao atual: mesmos `WeightPr`/`RepsPr`, na mesma ordem, com os mesmos `previous*` e `firstRecord`. As tolerâncias `heavier`/`sameLoad` e a cronologia (`startedAt`, depois `endedAt ?: Long.MAX_VALUE`, depois `id`) continuam as mesmas.
- `WorkoutPresentation` nunca roda em duas threads ao mesmo tempo nem dentro de composição. A reutilização de identidade que já existe (`previousActive`, `old?.takeIf { it == entry }`) fica como está.
- O `timerUi` continua fora do `contentUi`.
- Testes: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) ANDROID_HOME=$HOME/Android/sdk` e depois `./gradlew :app:testDebugUnitTest --tests "<classe>" --console=plain`.

## Review Focus

1. **Treinos com o mesmo `startedAt`** (importados ou criados no mesmo instante): a ordem entre eles segue `endedAt` e depois `id`, igual a hoje. Teste na Task 1 (gerador de equivalência).
2. **Cargas com ruído de ponto flutuante** (80.0 e 80.0 + 1 ulp): contam como a mesma carga, igual a hoje. Teste na Task 1. Ruling: o gerador usa ruído de até ±1 ulp em torno de cargas nominais separadas por pelo menos 0,25 kg. Acima disso, `sameLoad` deixa de ser transitiva e a equivalência dependeria da ordem de inserção, um caso que dados reais não produzem.
3. **Treino ativo com histórico grande:** os recordes do treino em andamento continuam sendo calculados contra os treinos finalizados anteriores. Teste na Task 2.
4. **Duas mudanças de dados seguidas** (finalizar um treino enquanto a apresentação anterior ainda roda em segundo plano): o último estado vence e nenhum estado velho sobrescreve um novo. Teste na Task 3.
5. **Troca kg/lb com histórico grande:** um único recálculo, fora da thread principal. Medido na Task 4.

---

### Task 1: Passada única de recordes

**Files:**
- Modify: `app/src/main/java/com/mooscles/domain/WorkoutCalculations.kt`
- Test: `app/src/test/java/com/mooscles/domain/WorkoutCalculationsBatchTest.kt`

**Interfaces:**
- Produces:
  - `fun prsByWorkout(data: AppData): Map<String, List<WeightPr>>`
  - `fun repsPrsByWorkout(data: AppData): Map<String, List<RepsPr>>`

  Cada mapa traz uma entrada para **cada** treino `FINISHED` de `data`, inclusive os que não têm recorde, com lista vazia. O valor é igual a `prsForWorkout(data, id)` e `repsPrsForWorkout(data, id)`. As duas funções por treino continuam públicas e sem mudança.

- [ ] **Step 1: Testes que falham** em `WorkoutCalculationsBatchTest`:
  - `batchMatchesPerWorkoutOnRandomHistories`: 200 históricos com semente fixa (`Random(42)`), cada um com 1 a 40 treinos. Cada histórico mistura:
    - 1 a 4 exercícios e as três convenções, incluindo `ASSISTANCE`;
    - séries de aquecimento, incompletas, com `assistance`, com reps 0 e com peso 0;
    - treinos com `startedAt` repetido e `endedAt` nulo ou diferente;
    - às vezes um treino `ACTIVE`;
    - cargas nominais em múltiplos de 0,25 kg, com ruído de ±1 ulp.

    Para cada treino finalizado, afirma `prsByWorkout(data)[id] == prsForWorkout(data, id)` e o mesmo para as repetições. Afirma também que as chaves do mapa são exatamente os ids `FINISHED`.
  - `batchIgnoresActiveWorkoutsAsBaseline`: um treino `ACTIVE` mais antigo com carga maior não reduz os recordes dos treinos finalizados posteriores (mesmo comportamento das funções por treino).
  - `batchOnLargeHistoryIsFast`: 208 treinos × 61 séries. Afirma que `prsByWorkout` + `repsPrsByWorkout` levam menos de 1500 ms no host e imprime o tempo com `println("batch=…ms")`.
- [ ] **Step 2: Rodar e ver falhar.**
- [ ] **Step 3: Implementar.** Montar o `SnapshotIndex` uma vez e ordenar os finalizados por `chronology`. Para cada treino, nesta ordem:
  1. Calcular os recordes contra as linhas de base acumuladas, reaproveitando a lógica privada que já existe.
  2. Depois, incorporar o treino às linhas de base.

  Na base de peso, o mapa `Key → max`. Na base de repetições, a mesma estrutura de `bestRepetitionSets`, alimentada na ordem em que a função por treino percorre `data.workoutExercises`. Se a equivalência exigir isso, o implementador escolhe como reproduzir essa ordem e explica no relatório.
- [ ] **Step 4: Rodar e ver passar.** Depois, `WorkoutCalculationsTest` e `WorkoutCalculationIndexTest` continuam verdes.
- [ ] **Step 5: Commit** com `perf(records): compute history records in one chronological pass`.

### Task 2: A apresentação usa a passada

**Files:**
- Modify: `app/src/main/java/com/mooscles/WorkoutPresentation.kt`
- Create: `app/src/test/java/com/mooscles/LargeHistoryFixture.kt` (gerador de teste: 208 treinos × 61 séries, extraído ou compartilhado com o de `BackupRepositoryTest.largeArchiveExportAndImportStayFast`, que passa a usá-lo)
- Test: `app/src/test/java/com/mooscles/WorkoutPresentationTest.kt` (acrescentar casos)

**Interfaces:**
- Consumes: `prsByWorkout` e `repsPrsByWorkout` (Task 1).
- Produces: nenhuma assinatura nova. `present(data, prefs)` mantém a assinatura e a saída.

- [ ] **Step 1: Testes que falham:**
  - `largeHistoryPresentsQuickly`: `present()` sobre a `LargeHistoryFixture` termina em menos de 3000 ms no host, e uma segunda chamada com os mesmos dados em menos de 200 ms (cache). Imprime `println("present=…ms")`.
  - `historyRecordsUnchangedByBatch`: compara o `historyUi` (recordes por treino e por exercício) com o que se obtém chamando as funções por treino, num histórico gerado de 30 treinos.
  - `activeWorkoutRecordsStillComputedAgainstHistory`: um treino ativo com carga acima do histórico mostra `RecordUi` de peso com o `previous` certo (Review Focus 3).
- [ ] **Step 2: Rodar e ver falhar** (o teste de tempo falha com o código atual).
- [ ] **Step 3: Implementar.** No ramo `historyChanged`, calcular os dois mapas uma vez sobre `historical` e fazer `workout()` ler os recordes deles. O treino ativo continua com as funções por treino sobre `data`. Se a medição mostrar que `workoutItems` (filtro sobre a lista inteira) ainda pesa, indexar as entradas por treino dentro do `present()` e registrar a medição no relatório.
- [ ] **Step 4: Rodar e ver passar.** Depois, `WorkoutPresentationTest`, `HistoryRecordsHostTest`, `AppViewModelRecordsHostTest` e `AppViewModelPerformanceHostTest` continuam verdes.
- [ ] **Step 5: Commit** com `perf(presentation): reuse one records pass for the history`.

### Task 3: Apresentação fora da thread principal

**Files:**
- Modify: `app/src/main/java/com/mooscles/AppViewModel.kt` (o fluxo `presentation` e o comentário "prepare card details on the UI thread")
- Modify: `AGENTS.md` (Arquitetura: "Roda no fluxo sequencial do ViewModel, na thread de UI" passa a ser "Roda no fluxo sequencial do ViewModel, num executor de fundo de uma thread só (`limitedParallelism(1)`), nunca dentro de composição")
- Test: `app/src/test/java/com/mooscles/AppViewModelPresentationThreadHostTest.kt`

**Interfaces:**
- Produces: `private val presentationDispatcher = Dispatchers.Default.limitedParallelism(1)`, aplicado com `.flowOn(presentationDispatcher)` ao fluxo `presentation`. O implementador escolhe entre manter o `Trace.beginSection("Mooscles:present")` dentro do bloco ou movê-lo junto.

- [ ] **Step 1: Testes que falham:**
  - `presentRunsOffTheMainThread`: prova que `present()` não roda na main thread. O mecanismo fica a critério do implementador, por exemplo um gancho `internal` só para teste no `AppViewModel` que registra `Looper.myLooper() == Looper.getMainLooper()`.
  - `latestDataWinsAfterQuickSuccessiveChanges`: duas escritas seguidas (iniciar e finalizar um treino, por exemplo). O `contentUi` final reflete a última, e nenhum estado intermediário aparece depois dela (Review Focus 4).
  - `firstStateStillLoadingUntilPresented`: antes do primeiro `present()`, o `contentUi` emite `loading = true`.
- [ ] **Step 2: Rodar e ver falhar.**
- [ ] **Step 3: Implementar.** Aplicar o `flowOn`. Ajustar os testes de host que dependiam de o `present` rodar no looper principal (por exemplo, esperar o estado com `contentUi.first { … }` em vez de só dar idle no looper), sem enfraquecer o que eles provam. O `AppViewModelPerformanceHostTest` continua contando as seções `Mooscles:present` ou é adaptado com uma explicação no relatório.
- [ ] **Step 4: Rodar e ver passar.** Depois, a suíte inteira e o `lintDebug`.
- [ ] **Step 5: Commit** com `perf(presentation): present history off the main thread`.

### Task 4: Verificação no emulador e docs

**Files:**
- Create: `docs/superpowers/reports/2026-10-27-pr-off-main-thread-verificacao.md`
- Modify: `docs/superpowers/reports/2026-10-27-export-import-verificacao.md` (o travamento depois do import passa a constar como resolvido, com link)
- Modify: `docs/superpowers/ROADMAP.md` e `README.md` (tirar a ressalva de travamento e marcar o item da fase 0)

- [ ] **Step 1:** Rodar `./gradlew :app:assembleDebug` e instalar só no `emulator-5554`.
- [ ] **Step 2:** Medir antes e depois na base grande (o mesmo gerador da verificação do export/import): abertura a frio, import, troca kg/lb, iniciar e finalizar um treino. Registrar:
  - o tempo até o histórico aparecer;
  - o maior bloqueio da thread principal (logcat `Choreographer` "Skipped N frames", ou um trace);
  - se houve ANR ao tocar na tela durante o carregamento.

  As metas da spec são: nenhum ANR, nenhum bloqueio acima de 700 ms por causa da apresentação e histórico visível em até 3 s depois do primeiro quadro. O "antes" pode vir dos números já registrados no relatório do export/import.
- [ ] **Step 3:** A revisão de compatibilidade (`compat-reviewer`) fica com o controlador.
- [ ] **Step 4:** Escrever o relatório com o que foi e o que **não** foi verificado, e atualizar os docs.
- [ ] **Step 5:** Rodar `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain` e fazer o commit com `docs(records): record main-thread verification`.
