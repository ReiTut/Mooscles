# Fase 4A: biblioteca de exercícios, plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trocar a aba Biblioteca por telas novas em código comum (lista com busca bilíngue e filtros, detalhe do exercício com figura, mapa corporal, dicas e resumo do histórico, formulário de exercício personalizado sem foto), dar nome em pt-BR, anatomia e dicas aos 287 exercícios do catálogo e entregar o renderizador de figuras com o piloto de 20 para o portão G1, sem mudar schema, chaves, rede nem identidade do app.

**Architecture:** Um spike de meio dia decide como o mapa corporal (adaptado do `Jsplice/MuscleMap`, MIT) entra no código. Em paralelo, o `:shared` ganha as tabelas de dados (anatomia, nomes em pt-BR, apelidos, índice de busca bilíngue) e a ação `createCustomExercise(id, …)`, com testes de `commonTest` e `compat-reviewer`. O `:designsystem` ganha o renderizador de poses (FK em `Canvas`), o mapa corporal, a busca, o chip de menu e as licenças. O `:screens` ganha as poses do piloto (que vão ao dono como folha do G1 sem travar nada), os nomes exibidos, as dicas, a biblioteca, o formulário, o detalhe e o seletor com filtros. Por fim o `:app` hospeda as rotas, e a etapa fecha com o fluxo de ponta a ponta no simulador iOS, o orçamento de toques, a varredura de acessibilidade, uma rodada única das suítes de aparelho, a atualização por cima, o desempenho, a crítica e a documentação.

**Tech Stack:** Kotlin 2.4.21 Multiplatform, AGP 9.3.3, Gradle 9.7.1, Compose Multiplatform 1.12.2 (`foundation`, `ui`, `animation`, `components-resources`; `PathParser` e `drawWithCache` do `ui`; testes com `androidx.compose.ui.test.v2.runComposeUiTest`), Jetpack Compose 1.12.1 no `:app` (sem mudança), Room 2.8.5 (schema v5, sem mudança), `kotlinx-serialization-json` 1.12.0 (já `api` do `:shared`, só para ler as dicas), `kotlinx-datetime` 0.8.0 (já no `:screens`), JUnit 4 + Robolectric 4.17, `kotlin.test`, Python 3 (`-I`) para o conversor do mapa, GitHub Actions (`ubuntu-latest` e `macos-26` com Xcode 26.4). Nenhuma dependência nova.

**Spec:** [docs/superpowers/specs/2026-10-29-phase4-library-routines-design.md](../specs/2026-10-29-phase4-library-routines-design.md) (etapa 4A: D1–D13, D20, D21, o portão G1, "Telas da 4A", "Como verificar" e "Adaptações do 'Pronto quando'"). Pesquisa: [relatório P5](../reports/2026-10-29-figuras-mapa-muscular.md), [medições](../research/2026-10-29-phase4-midia/medicoes.md). Estrutura e convenções: [plano da fase 3](2026-10-29-phase3-active-workout.md).

**Fora deste plano (planos próprios depois):**
- **4B, rotinas:** aba Treinar, gerenciar pastas, editor de rotina, reordenação por arrasto (D15, spike S2), aquecimento na série planejada e schema v6 (D14, D19), linha de alvo (D16), foto do exercício personalizado (D17, D18, spike S4), seleção múltipla no seletor, as ações de rotina e pasta do contrato e a frase da exportação sobre fotos.
- **4M, download de mídia (D10):** só com mídia encomendada.
- **Lotes de figuras depois do piloto:** só com o G1 aprovado. Ficam aqui como a Task L, condicional, que não faz parte do merge da 4A (podem entrar durante a 4B).

## Decisões travadas por este plano

As decisões da spec valem como estão. Estas são as que a spec deixou para quem implementa, ou que este plano precisou fechar. Todas são **minhas**, com a delegação do dono.

1. **Branches e push.** O controlador mescla `docs/phase4-spec` (spec e este plano) na `main` do fork e cria `feat/phase4a-library` a partir dela antes da Task 2. O spike do mapa usa `spike/phase4a-bodymap`, a partir da mesma `main`, nunca mesclada. **Push só em três checkpoints**, marcados **Push** no fim das Tasks 9, 19 e 24. Nas outras, só commit local. Não faça push enquanto o job `ios` do checkpoint anterior estiver na fila ou rodando, a não ser que ele já esteja perdido.
2. **O G1 não trava a 4A.** A folha do piloto vai ao dono na Task 9; as Tasks 10–24 seguem sem esperar a resposta, e o merge da 4A na `main` do fork não depende dela. Com o G1 aprovado, a Task L roda por lote em branch própria; reprovado, a Task R tira o piloto do produto.
3. **Busca bilíngue num índice novo.** `ExerciseSearch.matches` continua exatamente como está (o `ExerciseSearchEquivalenceTest` fixa o resultado dele contra a implementação da JVM, e as telas antigas de histórico e de rotina o usam). A busca nos dois idiomas é a classe nova `ExerciseSearchIndex`, no mesmo pacote, montada uma vez por lista com o mesmo `normalize` e com os candidatos novos; as telas novas usam só o índice. Um teste prova que o índice acha tudo o que o `matches` acha.
4. **Tempo da alternância da figura:** o token já existe desde a fase 2 como `MTheme.timing.illustrationFrame` (900 ms). A spec o chama de `timing.figureFrame`; não se cria outro nem se renomeia.
5. **Página "Figuras" da galeria:** as poses moram no `:screens` (spec, "Onde as figuras vivem"), que o `:designsystem` não enxerga. Por isso o `GalleryScreen` ganha `extraPages: List<GalleryExtraPage> = emptyList()`, e a página "Figuras" é do `:screens`, passada pelo `:app` no `DeveloperGallery`. Nasce com o piloto; os lotes a aumentam.
6. **Ângulos da pose são absolutos** no plano da imagem, em graus, 0° apontando para cima e positivo no sentido horário, com a figura de frente para a direita (lado de cá = lado mais perto de quem olha). *Por quê:* quem escreve e quem revisa 287 poses lê "coxa a 90°" direto, sem somar ângulos de pais. A FK continua acumulando posições do quadril para fora, sobre ossos de comprimento fixo.
7. **Sem interpolação de pose.** A alternância é um cross-fade entre os dois desenhos (D5), então não há `lerp` de ângulos.
8. **Regiões do mapa no design system:** `BodyRegion` (17 valores, os mesmos nomes de `MuscleRegion`) fica no `:designsystem`, que não conhece o `:shared`; o `:screens` faz a ponte por nome, com teste. **Pescoço:** usa os mesmos caminhos do trapézio, a menos que o spike ache uma região de pescoço na fonte.
9. **Destaque na figura só do primeiro músculo principal e dos outros principais**, por segmento (tabela na Task 8); os secundários aparecem só no mapa.
10. **Anatomia:** `primary` é uma lista ordenada; o **primeiro** principal precisa pertencer ao conjunto permitido do `MuscleGroup` (tabela na Task 2), os demais são livres.
11. **Nome exibido:** a regra fica no `:shared`, em `ExerciseNames.display(id, storedName, custom, portuguese)`, e o idioma vem do recurso `catalog_names_language` do `:screens` (D12). Ela vale também nas telas da fase 3 (treino ativo, menu, sheet de descanso, dock, resumo, cartão, seletor). **Lacuna registrada:** o nome que o `MoosclesViewModel` grava no timer no descanso automático (e que a notificação do `:app` mostra) continua o nome gravado, porque o VM não sabe o idioma; vai com as mensagens do VM na fase 6.
12. **Busca e chips:** a busca é um componente novo do design system, `MSearchField` (o `MTextField` tem rótulo em cima e não tem ícone nem botão de limpar). O chip "Equipamento", que abre uma sheet, é `MChipKind.Menu` (papel de botão, seta para baixo, aparência de selecionado com filtro ativo). Os chips de leitura do detalhe são `MInfoChip`, sem papel nem foco.
13. **Criar e abrir:** o id nasce na tela (`newId()`); depois de "Salvar", a biblioteca guarda o id em `rememberSaveable` e abre o detalhe quando ele aparece em `state.exercises`; se chegar um `errorCode` antes, descarta o id e mostra o erro.
14. **Erros nas telas novas:** a biblioteca e o detalhe mostram o texto localizado do `UiError` pelo `ScreenErrorToast` (como o treino, decisão 19 da fase 3), e o snackbar global do `MoosclesApp` não aparece nas rotas `library` e `exerciseInfo/{id}`.
15. **"Em primeiro plano"** chega ao detalhe como parâmetro (`foreground: Boolean`), lido pelo `:app` do ciclo de vida da `MainActivity`; o código comum não ganha `expect/actual`.
16. **Rótulo da figura sem concordância de gênero:** "Ilustração de início e fim: Supino reto com barra" / "Illustration: start and end of Barbell bench press". A forma da spec ("…do supino reto…") exigiria saber o gênero de cada nome.
17. **Licenças:** a entrada CC0 chama-se "Ilustrações, dicas de execução e nomes do Mooscles" (a correção da D9 tirou o mapa dela) e lê `files/licenses/media-CC0.md` (texto puro, sem marcação); o mapa tem a entrada própria "MuscleMap", "MIT License", com `files/licenses/musclemap_mit.txt` e o commit de origem no texto.
18. **Orçamento de APK da 4A:** a base do `tools/apk-budget.json` passa a ser o artefato `app-debug-apk` do CI verde da `main` no commit em que `feat/phase4a-library` nasce, com `maxGrowthBytes: 629146` (0,6 MiB, a parte da 4A no D20). A 4B sobe para 1.048.576 sobre a mesma base.
19. **Resumo do histórico e "Usados recentemente":** funções puras no `:screens` (`ExerciseHistorySummaries`, `LibraryModel.recents`), a partir do `UiState` em memória.
20. **Dicas:** lidas com `Res.readBytes`, uma vez por idioma e por processo, com `Json.parseToJsonElement` (sem plugin de serialização no `:screens`); o leitor recebe a função de leitura, para os testes de `commonTest` passarem bytes falsos.
21. **Sem versão nova:** `versionCode` 16 e `versionName` 1.4.5 não mudam.

## Global Constraints

- **Dados intactos.** Room na v5, sem entidade, coluna nem migração nova (`shared/schemas` sem mudança, conferido pelo CI). Nenhuma chave nova, renomeada ou com outro tipo em `local_preferences` ou `app_updates`. `applicationId = com.mooscles`, `versionCode` 16, `versionName` 1.4.5, a assinatura, o `update.json` e `ReleaseUrls.REPOSITORY` não mudam. `exercises.name` continua recebendo o inglês do catálogo no `initialize()`, e nada toca o `name` de `workout_exercises`. Escrita nova só o `createCustomExercise` com id, numa `writeTransaction`, **sem `catch` dentro**.
- **Rede:** nenhuma requisição nova; nenhuma tela desta etapa usa rede.
- **Dependências:** nenhuma nova; o `debugRuntimeClasspath` do `:app` fica igual (diff na Task 22). Nada de Material 3 no `:screens`, Lottie, Compottie, Rive, Coil.
- **APK debug:** até 629.146 bytes sobre a base da decisão 18; fontes até 900 KiB e fontes + ícones até 1 MiB, como antes. O mapa e as figuras ficam fora de `icons/`.
- **Um design só:** toda tela, componente, figura e texto novo em `commonMain`; nenhum `expect/actual` novo. `grep -rnE "\b(java|javax|android)\.|System\.|String\.format" screens/src/commonMain designsystem/src/commonMain/kotlin/com/mooscles/designsystem/illustration` volta vazio.
- **Tokens:** nenhum literal de cor, medida ou duração fora de `tokens/`; o `tools/check-design-tokens.ps1` passa a cobrir `designsystem/.../illustration` (Task 5). Coordenadas e ângulos de pose são dados sem unidade, não `dp`.
- **Acessibilidade:** alvos de 48 dp com 8 dp entre alvos; nada abaixo de 12 sp; todo botão de ícone com rótulo; nenhum estado só por cor; pares de cor novos no `ComponentColorsTest` (3:1 para não texto, 4,5:1 para texto); nenhum número cortado (`assertShownWhole`) a 1,0, 1,3 e 2,0 em 320 e 412 dp, e a 2,01 no Android.
- **Textos:** inglês base e pt-BR, voz da fase 2 (direto, "você", um verbo no botão, sem exclamação nem emoji). Strings do `:screens` em `screens/src/commonMain/composeResources/values*/strings_{library,exercise,anatomy,catalog,workout}.xml`; de componente no `strings_components.xml` do `:designsystem`; nomes no `ExerciseNames`; dicas nos JSON. O 0 fica no plural (`pluralCount`). Nenhum texto desta etapa em inglês com o aparelho em pt-BR, menos os nomes digitados e a lacuna da decisão 11.
- **Mídia e licença:** nenhuma mídia de terceiros além do MuscleMap (MIT, commit anotado). Referências (Everkinetic, Workout Guide, free-exercise-db) só se leem para saber a posição, nunca se decalcam nem se sobrepõem, e cada consulta entra na tabela "Referências visuais consultadas" das Notas. Nenhum asset gerado por IA.
- **Lições das fases 2 e 3 que viram regra:** `runComposeUiTest` da `v2` (espere explicitamente); `useUnmergedTree = true` nos campos; `assertShownWhole`/`assertNumbersWhole` para números; recriação pelo `ScreenRestoration` do `:screens` (o `StateRestorationTester` não existe no Skiko); contador pelo `RecompositionProbe` (`@NonRestartableComposable`) com controle positivo; gesto cancelado só onde `INJECTS_TOUCH_CANCEL`.
- **Processo (feedback do dono, "fast dev loop"):**
  - ambiente de toda chamada de shell: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) ANDROID_HOME=$HOME/Android/sdk ANDROID_SERIAL=emulator-5556 PATH="$HOME/.local/bin:$PATH"`;
  - **todo comando do Gradle ou do aparelho tem limite de tempo** por `perl -e 'alarm shift; exec @ARGV' <segundos> …` (o macOS não tem `timeout`): 900 s para testes de host focados e builds, 1800 s para uma classe no aparelho, 5400 s para as suítes completas da Task 22;
  - durante a task, o implementador roda **só os testes que tocou**: as classes de host e, no aparelho, só as classes de `uiTest` que criou ou mudou, com `-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>[,<FQCN>…]` (confira o nome da task com `./gradlew :<módulo>:tasks --all | grep -i devicetest`). Compilar não é prova: toda classe de `uiTest` nova ou mudada roda no emulador dentro da própria task. Esperado: `BUILD SUCCESSFUL` e o XML em `<módulo>/build/outputs/androidTest-results/` sem falhas;
  - **as suítes completas de aparelho** (`:designsystem` e `:screens` `connectedAndroidDeviceTest` e `:app:connectedDebugAndroidTest`) **rodam uma vez só**, na Task 22. O CI roda as suítes de host e de iOS nos checkpoints;
  - no máximo **dois implementadores** ao mesmo tempo, em worktrees com branch própria (`lane/phase4a-tN`), e no máximo **um usando o emulador**; nenhum Gradle roda enquanto um agente dirige a UI do emulador por `adb`; com duas lanes, cada Gradle usa `--max-workers=2`;
  - toda task que acrescenta testes sobe o mínimo do conjunto no `ci.yml` pelo número de testes novos, com a linha por classe no comentário: `MIN_HOST_TESTS` (host de `:app`, `:shared`, `:designsystem` e `:screens`, inclusive `commonTest`), `MIN_IOS_TESTS` (`:shared` no simulador), `MIN_DS_IOS_TESTS` (`commonTest` e `uiTest` do design system) e `MIN_SCREENS_IOS_TESTS` (`commonTest` e `uiTest` do `:screens`). Um teste aposentado só desconta com o motivo no commit. Duas lanes que sobem a mesma linha: o controlador soma;
  - o `compat-reviewer` dá `SEGURO` antes do commit nas Tasks 2, 3, 4 e 24;
  - o iOS só se prova no CI; desligue a rede do emulador (`adb shell svc wifi disable; adb shell svc data disable`) antes de instalar APKs; nunca desinstale nada de um aparelho do dono.
- Código, comentários, KDoc e commits em inglês. Plano e relatórios em pt-BR. Commits terminam com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **Evidências:** `docs/superpowers/reports/AAAA-MM-DD-phase4a-verificacao/` (nasce na Task 5, com o tamanho da base). Só entram no git as capturas citadas nos relatórios, em PNG com paleta reduzida quando a crítica não precisar de cor exata (`pngquant --quality 60-80`; sem ele, o PNG original com o tamanho anotado); o resto vai para `…/local/`, que entra no `.gitignore` na Task 5.

## Review Focus

1. **A lista de exercícios muda enquanto a pessoa digita** (um personalizado criado, um backup importado) ou o índice ainda está sendo montado na primeira abertura com 300 personalizados. A busca e os filtros continuam, os resultados passam a refletir a lista nova, nada pisca vazio por engano e nada trava. Coberto na Task 15 por `LibraryScreenTest.listChangeWhileSearchingKeepsTheQueryAndUpdatesResults` e `queryTypedBeforeTheIndexIsReadyIsAppliedWhenItArrives`.
2. **"Salvar" tocado duas vezes, o estado demorando a trazer o exercício novo, ou a gravação falhando; e girar a tela com o formulário aberto.** Um exercício só é criado, o detalhe abre quando ele chega, um erro descarta a espera e mostra o texto, e o rascunho sobrevive à recriação. Coberto na Task 16 por `CustomExerciseFormTest.saveTwiceCreatesOnce` e `draftSurvivesRecreation`, e por `LibraryScreenTest.creatingOpensTheNewExerciseWhenItArrives` e `creationErrorDropsThePendingOpen`.
3. **O exercício some com o detalhe aberto** (import de backup) **ou a rotina escolhida em "Adicionar à rotina" some antes do toque.** O detalhe troca para "Exercício não encontrado" sem fechar sozinho; a adição a uma rotina que não existe mais mostra o erro localizado e nenhum toast de sucesso. Coberto na Task 17 por `ExerciseInfoScreenTest.exerciseThatDisappearsShowsNotFound` e `ExerciseInfoScreenTest.addToARoutineThatIsGoneShowsTheErrorNotTheToast`.
4. **Nomes que confundem:** um treino antigo gravado com um nome em inglês que o catálogo já mudou, e um personalizado com o mesmo nome de um exercício do catálogo em pt-BR. O do catálogo aparece com o nome atual no idioma da tela, o personalizado com o nome digitado, e a busca acha os dois. Coberto na Task 10 por `ExerciseDisplayNamesTest.oldEnglishSnapshotShowsTheCurrentCatalogName` e na Task 3 por `ExerciseSearchIndexTest.customNamedLikeACatalogEntryIsFoundAlongsideIt`.
5. **"Reduzir movimento" ligado, ou o app indo para segundo plano, no meio do cross-fade.** A figura para inteira no quadro B, sem ficar meio transparente, e volta a alternar quando a condição some. Coberto na Task 5 por `ExerciseFigureTest.reducedMotionMidFadeSettlesOnTheEnd` e `backgroundMidFadeSettlesAndResumes`.

## Destino dos testes atuais

Regra: um teste cujo alvo continua no código fica; um cujo alvo sai é portado (nome novo na task indicada) ou aposentado com o motivo abaixo, no mesmo commit em que o código sai.

| Teste atual | Destino |
| --- | --- |
| `ShellNavigationHostTest.assertLibraryRoot` (procura o texto "Exercise library") e os testes que o usam | Task 19: procura a tag `library-screen` |
| `androidTest/UiFeatureSmokeTest` (o campo de busca da biblioteca é o primeiro com `SetText`) | fica; o `MSearchField` tem `SetText`. Roda na Task 22 |
| `DiscardNavigationHostTest` (só toca nas abas) | fica |
| `ExercisePickerTest.recentsAreTheLastWorkoutsExercisesUpToEightWithoutRepeats` | fica (`ExercisePicker.recents` continua) |
| `ExercisePickerTest.searchFoldsAccentsLikeToday`, `allIsAlphabetical` | Task 18: `ExercisePicker.filter` sai; portados para `LibraryModelTest.searchFoldsAccents` e `resultsAreAlphabeticalByShownName` (Task 14) |
| `ExercisePickerSheetTest` (6) | ficam, ajustados na Task 18 (a linha ganha a mídia de 56 dp) |
| `ExerciseSearchTest`, `ExerciseSearchLocaleTest`, `ExerciseSearchEquivalenceTest`, `SearchFoldingJvmEquivalenceTest`, `CatalogTest`, `CatalogUpgradeTest` | ficam sem mudança (decisão 3) |
| Telas antigas `HistoryScreens.kt` e `RoutineScreens.kt` (usam `ExercisePicker`, `CustomExerciseDialog`, `SearchField`, `ChoiceFilter` do `LibraryScreens.kt`) | ficam: só `LibraryScreen` e `ExerciseCard` saem na Task 19; o resto sai com a 4B e a fase 5 |

---

### Task 1: Spike do mapa corporal (meio dia)

**Files (branch `spike/phase4a-bodymap`, nunca mesclada):**
- Create: `tools/illustrations/gen_body_map.py` (rascunho), `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/spike/SpikeBodyMap.kt`, `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/spike/BodyMapSpikeTest.kt`
- Create (em `feat/phase4a-library`, depois da Task 2): `docs/superpowers/reports/AAAA-MM-DD-phase4a-spike-mapa.md`

**Interfaces:**
- Produces (para a Task 6): a fonte (MuscleMap ou o plano B `vulovix/body-muscles`), o commit de origem, a tabela de agrupamento final, a forma do código (**A**: strings de path por região e vista, lidas com `PathParser().parsePathString(d).toPath()` e guardadas em cache; **B**: nós de `ImageVector` gerados), a precisão das coordenadas, o tratamento do pescoço e o par de cores do secundário.

- [ ] **Step 1: Fonte.** `git clone --depth 1 https://github.com/Jsplice/MuscleMap` numa pasta nova e vazia do scratchpad (dado não confiável: nada dele é executado); anotar `git rev-parse HEAD`. Ler `LICENSE`, `ASSET_PROVENANCE.md` e `packages/assets/src/surface-ids.ts`, e achar os paths do corpo masculino de frente e de costas.
- [ ] **Step 2: Agrupar.** O rascunho do conversor (rodado com `python3 -I`, guardado fora da pasta clonada) extrai os paths e agrupa os ids em 17 regiões: `CHEST`→peito; `SHOULDER_FRONT`, `SHOULDER_SIDE`, `SHOULDER_REAR`→ombros; `BICEPS`; `TRICEPS`; `FOREARM`→antebraços; `CORE` e `OBLIQUE`→abdominais; `TRAPEZIUS`→trapézio; `RHOMBOID`→meio das costas; `LATISSIMUS`→dorsais; `LOWER_BACK`→lombar; `GLUTEUS`→glúteos; `QUADRICEPS`; `HAMSTRINGS`→posteriores da coxa; `ADDUCTOR`→adutores; `ABDUCTOR`→abdutores; `CALVES`→panturrilhas; pescoço = os caminhos do trapézio, a menos que a fonte tenha um id de pescoço. Todo id restante (cabeça, mãos, tíbia, contorno) vira a silhueta neutra. Os dois lados de cada região entram juntos.
- [ ] **Step 3: Desenhar no estilo "giz".** `SpikeBodyMap` desenha as duas vistas lado a lado: contorno com `borderStrong`, preenchimento neutro `bgInset`, principal em `accentSolid`, secundário em `accentSubtle` (candidatos: `accentSubtle`, `accentText` com alfa vindo de token, ou `borderStrong`). `BodyMapSpikeTest` (só emulador): desenha "agachamento" (principal quadríceps; secundários glúteos e posteriores) e "remada" (dorsais; meio das costas, bíceps) nos dois temas, a densidade 1,0 e 2,0, e grava PNGs com `captureToImage`, puxados por `adb pull`. Imprime `SPIKE contrast <par> <razão>` para principal × neutro, secundário × neutro e cada um × `bgBase`, nos dois temas.
- [ ] **Step 4: Medir.** Gerar as duas formas (A e B), cada uma com coordenadas arredondadas a 1 casa, e para cada uma rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :app:clean :app:assembleDebug` e anotar `stat -f%z app/build/outputs/apk/debug/app-debug.apk` contra o build sem o mapa. Anotar também se alguma constante passou de 65.535 bytes ou algum método de 64 KB.
- [ ] **Step 5: Regra de decisão.** Fica a forma de menor custo no APK, desde que as capturas das duas sejam iguais; alvo **≤ 120 KiB** no APK. Acima disso, reduzir a precisão para inteiros (num `viewBox` reescalado para 512 × 768) e medir de novo; ainda acima de 160 KiB, ou com região ambígua nas capturas, ou com secundário abaixo de 3:1 contra o neutro em algum tema sem candidato que passe, repetir os Steps 1–4 com o `vulovix/body-muscles` (Apache-2.0, com o NOTICE). Registrar no relatório: commit de origem, tabela final, medidas, capturas (as citadas, no git), a forma e o par de cores escolhidos, e o que não foi provado (o mapa num iPhone).
- [ ] **Step 6:** Commit no spike `spike: body map from MuscleMap in the chalk style` (sem push). Commit do relatório em `feat/phase4a-library`: `docs(report): phase 4A body map spike`.

### Task 2: Regiões musculares e anatomia dos 287 exercícios

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mooscles/data/MuscleRegion.kt`, `shared/src/commonMain/kotlin/com/mooscles/data/ExerciseAnatomy.kt`, `shared/src/commonMain/kotlin/com/mooscles/data/ExerciseAnatomyTable.kt`
- Modify: `.github/workflows/ci.yml`
- Test: `shared/src/commonTest/kotlin/com/mooscles/data/ExerciseAnatomyTest.kt`

**Interfaces:**
- Produces: `enum class MuscleRegion { ABDOMINALS, ABDUCTORS, ADDUCTORS, BICEPS, CALVES, CHEST, FOREARMS, GLUTES, HAMSTRINGS, LATS, LOWER_BACK, MIDDLE_BACK, NECK, QUADRICEPS, SHOULDERS, TRAPS, TRICEPS }`.
- Produces: `data class Anatomy(val primary: List<MuscleRegion>, val secondary: List<MuscleRegion>)`.
- Produces: `object ExerciseAnatomy { val catalog: Map<String, Anatomy>` (chave: `stableId`, sem `catalog_`)`; val allowedPrimary: Map<MuscleGroup, Set<MuscleRegion>>; fun forMuscleGroup(group: MuscleGroup): Anatomy?; fun forExercise(id: String, muscleLabel: String): Anatomy? }`. `forExercise` usa `catalog[id.removePrefix("catalog_")]` quando o id tem o prefixo e está no catálogo; senão `forMuscleGroup` do grupo cujo `label` é `muscleLabel` (desconhecido: `null`).
- Tabela `allowedPrimary`: `CHEST`→{CHEST}; `BACK`→{LATS, MIDDLE_BACK, LOWER_BACK, TRAPS}; `SHOULDERS`→{SHOULDERS}; `BICEPS`→{BICEPS}; `TRICEPS`→{TRICEPS}; `QUADRICEPS`→{QUADRICEPS, ADDUCTORS}; `HAMSTRINGS`→{HAMSTRINGS}; `GLUTES`→{GLUTES, ABDUCTORS}; `CALVES`→{CALVES}; `CORE`→{ABDOMINALS, LOWER_BACK}; `FOREARMS`→{FOREARMS}; `FULL_BODY`→todas.
- `forMuscleGroup` (personalizado, D7): `Anatomy(listOf(<região>), emptyList())` com CHEST→CHEST, BACK→LATS, SHOULDERS→SHOULDERS, BICEPS→BICEPS, TRICEPS→TRICEPS, QUADRICEPS→QUADRICEPS, HAMSTRINGS→HAMSTRINGS, GLUTES→GLUTES, CALVES→CALVES, CORE→ABDOMINALS, FOREARMS→FOREARMS; `FULL_BODY`→`null` ("Corpo inteiro").

- [ ] **Step 1: Testes que falham** (`ExerciseAnatomyTest`): `everyCatalogExerciseHasAnatomy`; `noOrphanKeys`; `primaryIsNeverEmpty`; `noRegionTwiceInAnEntry` (nem repetida numa lista, nem nas duas); `firstPrimaryFitsTheMuscleGroup` (para cada exercício, `primary.first() in allowedPrimary.getValue(exercise.muscle)`); `everyMuscleGroupHasAnAllowedSet`; `customUsesTheMuscleGroupRegion` (`forExercise("x", "Chest") == Anatomy(listOf(CHEST), emptyList())`); `fullBodyCustomHasNoRegion`; `unknownLabelHasNoAnatomy`; amostras: `barbell_back_squat_high_bar` com `primary.first() == QUADRICEPS` e `GLUTES in secondary`, `selectorized_leg_curl_lying` com `HAMSTRINGS` primeiro, `barbell_shrug` com `TRAPS` primeiro, `barbell_bench_flat` com `CHEST` primeiro e `TRICEPS in secondary`.
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :shared:testAndroidHostTest --tests "*ExerciseAnatomyTest*"` e ver falhar.
- [ ] **Step 3: Escrever** `MuscleRegion`, `ExerciseAnatomy` e a tabela dos 287 em `ExerciseAnatomyTable.kt` (`internal val anatomyTable: Map<String, Anatomy>`, uma linha por exercício, na ordem do `ExerciseCatalog`, agrupada por seção com um comentário). Escrita pelo conhecimento geral de exercício, não copiada de dataset (D7).
- [ ] **Step 4:** O comando do Step 2 verde, mais `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :shared:compileCommonMainKotlinMetadata`.
- [ ] **Step 5: Revisão de conteúdo (D21a).** Uma passada de agente independente confere as 287 entradas contra o conhecimento geral de exercício (principal e secundários plausíveis, nada inventado); as correções entram antes do commit.
- [ ] **Step 6:** Subir `MIN_HOST_TESTS` e `MIN_IOS_TESTS`. `compat-reviewer` (`data/`; esperado `SEGURO`: só código, nada gravado). Commit `shared: muscle regions and anatomy for the whole catalog`.

### Task 3: Nomes em pt-BR, apelidos e índice de busca bilíngue

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mooscles/data/ExerciseNames.kt`, `shared/src/commonMain/kotlin/com/mooscles/data/ExerciseAliases.kt`, `shared/src/commonMain/kotlin/com/mooscles/data/SearchTerms.kt`, `shared/src/commonMain/kotlin/com/mooscles/data/ExerciseSearchIndex.kt`
- Modify: `shared/src/commonMain/kotlin/com/mooscles/data/ExerciseSearch.kt` (o mapa `aliases` e `normalize` passam de `private` a `internal`; nenhum comportamento muda), `.github/workflows/ci.yml`
- Test: `shared/src/commonTest/kotlin/com/mooscles/data/ExerciseNamesTest.kt`, `shared/src/commonTest/kotlin/com/mooscles/data/ExerciseSearchIndexTest.kt`

**Interfaces:**
- Produces: `object ExerciseNames { val portuguese: Map<String, String>` (chave: `stableId`)`; fun display(id: String, storedName: String, custom: Boolean, portuguese: Boolean): String }`. Regra (D11): se `!custom` e `id` é `catalog_<stableId>` presente no `ExerciseCatalog`, devolve o nome do catálogo (en) ou `portuguese[stableId]` (pt); senão, `storedName`.
- Produces: `object ExerciseAliases { val portuguese: Map<String, List<String>> }` (chave: o id completo, como o `aliases` atual), com pelo menos os apelidos da D13: supino, voador, peck deck, crucifixo, puxada, pulley, remada baixa, terra, stiff, RDL, cadeira extensora, mesa flexora, cadeira flexora, agachamento, afundo, passada, búlgaro, panturrilha, rosca direta, rosca martelo, rosca scott, tríceps testa, tríceps corda, desenvolvimento, elevação lateral, encolhimento, elevação pélvica, barra fixa, paralelas, flexão, graviton, hack, cada um nos exercícios a que se aplica.
- Produces: `object SearchTerms { fun muscle(group: MuscleGroup): List<String>; fun equipment(equipment: Equipment): List<String> }`, as palavras em pt-BR: Peito/peitoral; Costas; Ombro/ombros/deltoide; Bíceps; Tríceps; Quadríceps/coxa; Posterior/posteriores de coxa; Glúteo/glúteos; Panturrilha; Abdômen/abdominal; Antebraço; Corpo inteiro; Barra; Halter/halteres; Kettlebell; Polia/cabo; Peso corporal; Smith; Máquina de placas/máquina; Máquina de anilhas/máquina; Outro.
- Produces: `data class SearchEntry(val id: String, val name: String, val muscle: String, val equipment: String)` (os labels em inglês do `ExerciseUi`) e `class ExerciseSearchIndex { companion object { fun build(entries: List<SearchEntry>): ExerciseSearchIndex }; fun matching(query: String): List<String>; fun matches(id: String, query: String): Boolean }`. `matching` devolve os ids na ordem das entradas; consulta vazia devolve todos. Na montagem, cada entrada guarda, já dobrados pelo `ExerciseSearch.normalize`, os candidatos: nome gravado, nome do catálogo em inglês, nome em pt-BR, apelidos em inglês e em pt-BR, cada um seguido dos metadados em inglês (labels) e em pt-BR (`SearchTerms`). A regra continua (D13): todas as palavras da consulta precisam estar num mesmo candidato. Por tecla, só a consulta é dobrada.

- [ ] **Step 1: Testes que falham.**
  - `ExerciseNamesTest`: `everyCatalogEntryHasAPortugueseName`; `namesAreNotBlankAndAtMostSixtyCharacters`; `namesAreUniqueAfterFolding` (pelo `SearchFolding.fold`); `noOrphanKeys`; `specExamples` (os 17 pares da D12, por exemplo `barbell_bench_flat`→"Supino reto com barra", `cable_row_close_neutral`→"Remada baixa no triângulo", `selectorized_leg_curl_lying`→"Mesa flexora", `plate_leg_press_45`→"Leg press 45°", `ez_bar_triceps_lying`→"Tríceps testa com barra EZ"); `displayUsesTheCatalogNameInTheScreenLanguage` (en devolve o nome do `ExerciseCatalog` mesmo com `storedName` antigo); `displayKeepsTheTypedNameOfACustomExercise`; `displayKeepsTheStoredNameOfAnUnknownCatalogId`.
  - `ExerciseSearchIndexTest`: `portugueseQueriesFindTheirExercises`, tabela `(consulta, deve conter, não deve conter)`: "supino inclinado halter"→`dumbbell_bench_incline`; "puxada"→`cable_lat_pulldown_wide`; "lat pulldown"→`cable_lat_pulldown_wide`; "mesa flexora"→`selectorized_leg_curl_lying`, sem `selectorized_leg_curl_seated`; "cadeira flexora"→`selectorized_leg_curl_seated`, sem `selectorized_leg_curl_lying`; "eleva"→`dumbbell_raise_lateral`; "terra"→`barbell_deadlift_conventional`; "rdl"→`barbell_deadlift_romanian`; "voador"→`selectorized_pec_fly`; "peito halter"→`dumbbell_bench_flat` (todos com `catalog_`); `wordsOfTwoDifferentAliasesDoNotCombine` ("voador peck" não acha `selectorized_pec_fly`); `metadataWorksInBothLanguages` ("costas polia" e "back cable" acham `cable_row_close_neutral`); `everyLegacyMatchStillMatches` (para todo exercício do catálogo e toda consulta formada por nomes e palavras dos nomes em inglês, `ExerciseSearch.matches(...)` verdadeiro implica `matches(id, consulta)` verdadeiro); `customNamedLikeACatalogEntryIsFoundAlongsideIt` (um personalizado "Supino reto com barra": "supino reto" acha ele e `barbell_bench_flat`); `blankQueryMatchesAll`; `orderFollowsTheEntries`.
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :shared:testAndroidHostTest --tests "*ExerciseNamesTest*" --tests "*ExerciseSearchIndexTest*"` e ver falhar.
- [ ] **Step 3: Escrever** os 287 nomes no estilo da D12 (vocabulário de academia brasileira, movimento + variação + equipamento, de preferência até 40 caracteres, anglicismos correntes mantidos), os apelidos, os termos e o índice.
- [ ] **Step 4:** O comando do Step 2 verde, mais `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :shared:testAndroidHostTest --tests "*ExerciseSearch*" --tests "*SearchFolding*" --tests "*CatalogTest*" :app:testDebugUnitTest --tests "com.mooscles.data.ExerciseSearchEquivalenceTest" :shared:compileCommonMainKotlinMetadata` verde, sem mudança de asserção.
- [ ] **Step 5: Revisão de conteúdo (D21a):** uma passada de agente independente lê os 287 nomes e os apelidos (termo de academia brasileira, sem ambiguidade entre variações próximas, como as duas flexoras). Gravar a lista completa em `docs/superpowers/reports/AAAA-MM-DD-phase4a-verificacao/nomes-pt-br.md` (tabela `stableId` · inglês · pt-BR), para a pergunta 3 ao dono.
- [ ] **Step 6:** Subir `MIN_HOST_TESTS` e `MIN_IOS_TESTS`. `compat-reviewer` (`data/`; esperado `SEGURO`: o `name` gravado e os ids não mudam). Commit `shared: Portuguese catalog names and bilingual search index`.

### Task 4: `createCustomExercise` com o id da tela

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/mooscles/data/WorkoutRepository.kt:382-386`, `shared/src/commonMain/kotlin/com/mooscles/ui/UiContract.kt` (`MoosclesActions`), `shared/src/commonMain/kotlin/com/mooscles/MoosclesViewModel.kt:149-158`, `screens/src/uiTest/kotlin/com/mooscles/screens/RecordingActions.kt`, `.github/workflows/ci.yml`
- Test: `app/src/test/java/com/mooscles/data/CustomExerciseIdTest.kt`, `app/src/test/java/com/mooscles/AppViewModelCustomExerciseHostTest.kt`

**Interfaces:**
- Produces: `suspend fun createCustomExercise(name: String, muscle: MuscleGroup, equipment: Equipment, convention: LoadConvention, id: String = newId()): String`, numa `writeTransaction`: recusa um id que já existe com `DataError.INVALID_VALUE`, "Exercise already exists."; o resto como hoje. Os chamadores atuais não mudam.
- Produces: `fun createCustomExercise(id: String, name: String, muscle: String, equipment: String, convention: String)` em `MoosclesActions`, com o mesmo mapeamento de labels do `saveCustomExercise`. O `saveCustomExercise` continua (editar).
- Produces: `RecordingActions` grava `"createCustomExercise:<id>,<name>,<muscle>,<equipment>,<convention>"`.

- [ ] **Step 1: Testes que falham.** `CustomExerciseIdTest` (Robolectric, Room real como os testes de `data/`): `createsWithTheGivenId`; `refusesAnExistingIdWithoutWriting` (o exercício existente fica igual; `DataRuleException` com `INVALID_VALUE`); `blankNameIsRefusedBeforeWriting`. `AppViewModelCustomExerciseHostTest` (montado como o `AppViewModelCommandLoopHostTest`): `createReachesTheStateWithTheScreenId`; `aRefusedCreateSetsTheErrorCode` (`errorCode == UiError.INVALID_VALUE`).
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :app:testDebugUnitTest --tests "com.mooscles.data.CustomExerciseIdTest" --tests "com.mooscles.AppViewModelCustomExerciseHostTest"` e ver falhar.
- [ ] **Step 3:** Implementar, sem `catch` dentro da transação.
- [ ] **Step 4:** O comando do Step 2 verde, mais `--tests "com.mooscles.AppViewModel*"` e `:screens:compileAndroidDeviceTestKotlinAndroid` (o nome exato da task de compilação dos testes de aparelho pelo `tasks --all`).
- [ ] **Step 5:** Subir `MIN_HOST_TESTS`. `compat-reviewer` (`data/`; esperado `SEGURO`). Commit `shared: create a custom exercise with the screen's id`.

### Task 5: Renderizador de figuras no design system

**Files:**
- Create: `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/illustration/FigureModel.kt`, `.../illustration/FigureGeometry.kt`, `.../illustration/ExerciseFigure.kt`, `.../tokens/IllustrationTokens.kt`
- Modify: `.../components/ExerciseCard.kt` (`ExerciseMedia.Figure` e `ExerciseThumbnail` público), `.../theme/MTheme.kt` e `.../theme/MoosclesDesignTheme.kt` (`MTheme.illustration`), `.../gallery/GalleryScreen.kt` e `.../gallery/GalleryPage.kt` (`extraPages`), `designsystem/src/commonMain/composeResources/values/strings_components.xml` e `values-pt-rBR/strings_components.xml`, `tools/check-design-tokens.ps1` (acrescenta `$base/illustration`), `tools/apk-budget.json`, `.gitignore` (`docs/superpowers/reports/*/local/`), `.github/workflows/ci.yml`
- Test: `designsystem/src/commonTest/kotlin/com/mooscles/designsystem/illustration/FigureGeometryTest.kt`, `.../tokens/IllustrationTokensTest.kt`, `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/illustration/ExerciseFigureTest.kt`, `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/gallery/GallerySmokeTest.kt` (um teste novo)

**Interfaces:**
- Produces (`FigureModel.kt`): `enum class FigureJoint { HIP, SHOULDER, NECK, HEAD, ELBOW_NEAR, HAND_NEAR, ELBOW_FAR, HAND_FAR, KNEE_NEAR, ANKLE_NEAR, TOE_NEAR, KNEE_FAR, ANKLE_FAR, TOE_FAR }`; `enum class FigureSegment { TORSO_FRONT, TORSO_BACK, SHOULDER, UPPER_ARM_FRONT, UPPER_ARM_BACK, FOREARM, HIP, THIGH_FRONT, THIGH_BACK, SHIN_BACK }`; `data class FigurePose(val hipX: Float, val hipY: Float, val trunk: Float, val head: Float, val upperArmNear: Float, val forearmNear: Float, val upperArmFar: Float, val forearmFar: Float, val thighNear: Float, val shinNear: Float, val footNear: Float, val thighFar: Float, val shinFar: Float, val footFar: Float, val floorY: Float? = null, val props: List<FigureProp> = emptyList())` (caixa 100 × 100, y para baixo, ângulos pela decisão 6; `floorY` não nulo diz que os pés estão no chão); `object FigureSkeleton` com os comprimentos (ponto de partida: `TRUNK = 30f`, `NECK = 4f`, `HEAD_RADIUS = 5f`, `UPPER_ARM = 15f`, `FOREARM = 14f`, `THIGH = 21f`, `SHIN = 20f`, `FOOT = 6f`; os finais saem da Task 8 e ficam fixados no teste); `sealed interface FigureProp` com `Barbell(at: FigureJoint)` (anilha de frente na mão), `Dumbbell(at)`, `Kettlebell(at)`, `Bench(x, y, length, angle)`, `Cable(anchorX, anchorY, to: FigureJoint)`, `Seat(x, y, length, angle)`, `Pad(x, y, radius)`, `Bar(x, y, length)` (barra fixa ou paralela), `Box(x, y, width, height)`, `Platform(at: FigureJoint, length, angle)` (leg press) e `Floor(y)`.
- Produces (`FigureGeometry.kt`): `data class FigureGeometry(val joints: Map<FigureJoint, Offset>, val bounds: Rect)` e `object FigureKinematics { fun solve(pose: FigurePose): FigureGeometry }`, função pura (os `bounds` cobrem juntas, cabeça e equipamento).
- Produces (`ExerciseFigure.kt`): `@Composable fun ExerciseFigure(pose: FigurePose, highlights: Set<FigureSegment>, contentDescription: String?, modifier: Modifier = Modifier)`, estático, com `drawWithCache` (geometria refeita só quando o tamanho ou a pose mudam), sem bitmap; figura em `textSecondary`, lado de lá dos membros no token apagado da Task 8 (padrão `textTertiary`), equipamento em `textTertiary`, segmento destacado em `accentSolid`, fundo transparente, traço de ponta redonda.
- Produces: `@Composable fun AnimatedExerciseFigure(start: FigurePose, end: FigurePose, highlights: Set<FigureSegment>, exerciseName: String, modifier: Modifier = Modifier, foreground: Boolean = true)`: alterna os quadros a cada `MTheme.timing.illustrationFrame` com cross-fade de `MTheme.motion.base`; pausa (no quadro B, inteiro) com `MTheme.reducedMotion`, com `foreground == false` e quando os `boundsInRoot` ficam vazios (fora da tela); só a camada da figura recompõe. Com movimento reduzido, um toque alterna, com o rótulo de ação `ds_figure_show_start`/`ds_figure_show_end`; com a alternância automática, não há ação de clique. Rótulo: `ds_figure_label` (decisão 16). `val FigureFrameKey = SemanticsPropertyKey<String>("FigureFrame")` com `"start"`/`"end"`, para os testes.
- Produces: `ExerciseMedia.Figure(val pose: FigurePose, val highlights: Set<FigureSegment>) : ExerciseMedia` e `@Composable fun ExerciseThumbnail(media: ExerciseMedia, equipmentIcon: ImageVector, size: Dp, modifier: Modifier = Modifier)` (o mesmo desenho que o `ExerciseCard` usa hoje; com `Figure`, o `ExerciseFigure` sem rótulo).
- Produces: `data class MoosclesIllustration(val figureStroke: Float, val figureHeadStroke: Float, val figureMargin: Float, val bodyMapStroke: Dp)` (frações da caixa para a figura; valores finais da Task 8, fixados em teste) e `MTheme.illustration`.
- Produces: `class GalleryExtraPage(val key: String, val title: @Composable () -> String, val content: @Composable () -> Unit)` e `GalleryScreen(onBack, state, extraPages: List<GalleryExtraPage> = emptyList())`, com as extras no fim da lista de páginas.
- Strings: `ds_figure_label` "Illustration: start and end of %1$s" / "Ilustração de início e fim: %1$s"; `ds_figure_show_start` "Show the start of the movement" / "Mostrar o início do movimento"; `ds_figure_show_end` "Show the end of the movement" / "Mostrar o fim do movimento".

- [ ] **Step 1: Base do orçamento** (decisão 18). `gh run list -R pedroyuuki/Mooscles --branch main --workflow ci.yml --limit 5 --json databaseId,headSha,conclusion`, achar o run verde do commit em que a branch nasceu, `gh run download <id> -R pedroyuuki/Mooscles -n app-debug-apk -D <scratchpad>`, anotar os bytes. Atualizar `tools/apk-budget.json` (`baseCommit` com o SHA completo, `baseApkSource`, `baseApkBytes`, `maxGrowthBytes: 629146`). Criar a pasta de evidências com `apk-base.md` (run, SHA, bytes).
- [ ] **Step 2: Testes que falham.**
  - `FigureGeometryTest`: `limbLengthsAreTheSkeletons` (para 50 poses de ângulos variados, a distância entre juntas vizinhas é o comprimento do osso, ±0,01); `theHipIsWhereThePoseSaysIt`; `zeroPointsUpAndNinetyPointsForward` (tronco 0° põe o ombro em `(hipX, hipY − TRUNK)`; coxa 90° põe o joelho em `(hipX + THIGH, hipY)`); `boundsCoverEveryJointAndProp`; `handPropsFollowTheHand`.
  - `IllustrationTokensTest.valuesArePinned` (os valores finais) e `strokeIsThickerThanTheBodyMapsAtSixtyFourDp`.
  - `ExerciseFigureTest`: `alternatesEveryIllustrationFrame` (com `mainClock.autoAdvance = false`: `"end"`, avança 900 ms e o fade, `"start"`, avança de novo, `"end"`); `autoAlternationHasNoClickAction`; `reducedMotionShowsTheEndAndATapToggles` (rótulos de ação certos nos dois quadros); `reducedMotionMidFadeSettlesOnTheEnd` (liga no meio do fade: quadro `"end"` com alfa 1 na camada visível); `backgroundMidFadeSettlesAndResumes` (`foreground` falso no meio: para no `"end"`; verdadeiro: volta a alternar); `pausesWhileScrolledOff` (numa coluna rolável, fora da tela o quadro não muda); `onlyTheFigureRecomposesOnEachFrame` (um irmão com contador fica em 1; controle positivo: a camada da figura sobe); `labelNamesTheExercise` (en e pt-BR); `thumbnailShowsTheFigureOrTheEquipmentIcon`.
  - `GallerySmokeTest.extraPagesAreListedAndOpen`.
- [ ] **Step 3:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :designsystem:testAndroidHostTest --tests "*FigureGeometryTest*" --tests "*IllustrationTokensTest*"` e ver falhar.
- [ ] **Step 4:** Implementar. Nenhum literal de cor, medida ou duração fora de `tokens/`.
- [ ] **Step 5: Verificar.** O comando do Step 3 verde; `perl -e 'alarm shift; exec @ARGV' 1800 ./gradlew :designsystem:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.mooscles.designsystem.illustration.ExerciseFigureTest,com.mooscles.designsystem.gallery.GallerySmokeTest,com.mooscles.designsystem.components.ExerciseCardTest` verde; `pwsh tools/check-design-tokens.ps1` sai com 0 e conta os arquivos de `illustration/`; `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :designsystem:compileCommonMainKotlinMetadata` verde (o iOS só se prova no CI).
- [ ] **Step 6:** Subir `MIN_HOST_TESTS` e `MIN_DS_IOS_TESTS`. Commit `designsystem: pose renderer for exercise figures`.

### Task 6: Mapa corporal e licenças

**Files:**
- Create: `designsystem/illustrations/musclemap/body-front.svg`, `body-back.svg` (agrupados em 17 regiões, `id` = nome da região), `LICENSE` (MIT da fonte), `PROVENANCE.txt` (repositório, commit, data, o que mudou: agrupamento, arredondamento, restilo); `tools/illustrations/gen_body_map.py` (lê os SVGs, gera o Kotlin; `python3 -I`); `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/illustration/BodyMapPaths.kt` (gerado, com o aviso "generated by tools/illustrations/gen_body_map.py; do not edit"); `.../illustration/BodyMap.kt`; `designsystem/src/commonMain/composeResources/files/licenses/musclemap_mit.txt`, `media-CC0.md`
- Modify: `.../licenses/LicensesScreen.kt` (duas entradas; o nome da entrada CC0 vem de recurso), `strings_components.xml` (en e pt-BR), `tokens/` só se a Task 1 escolheu um token novo para o secundário, `.github/workflows/ci.yml`
- Test: `designsystem/src/commonTest/kotlin/com/mooscles/designsystem/illustration/BodyMapPathsTest.kt`, `.../components/ComponentColorsTest.kt` (testes novos), `.../licenses/LicenseTextTest.kt` (testes novos), `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/illustration/BodyMapTest.kt`, `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/licenses/LicensesScreenTest.kt`

**Interfaces:**
- Consumes: a decisão da Task 1 (fonte, forma A ou B, pescoço, par do secundário).
- Produces: `enum class BodyRegion` com os 17 nomes de `MuscleRegion` (decisão 8); `@Composable fun BodyMap(primary: Set<BodyRegion>, secondary: Set<BodyRegion>, modifier: Modifier = Modifier)`: as duas vistas lado a lado, uma imagem só, sem rótulo próprio (o texto equivalente fica embaixo, na tela), contorno no traço `MTheme.illustration.bodyMapStroke` em `borderStrong`, neutro, principal e secundário nas cores da Task 1, sem mexer na geometria; `@Composable fun BodyMapLegend(modifier: Modifier = Modifier)`: amostra + "Principais", amostra + "Secundários".
- Produces: as entradas de licença (decisão 17).
- Strings: `ds_body_map_primary` "Primary" / "Principais"; `ds_body_map_secondary` "Secondary" / "Secundários"; `ds_licenses_mooscles_media` "Mooscles illustrations, exercise tips and names" / "Ilustrações, dicas de execução e nomes do Mooscles".

- [ ] **Step 1: Testes que falham.**
  - `BodyMapPathsTest`: `everyRegionHasPathsInSomeView`; `neckFollowsTheSpikeDecision` (os mesmos caminhos do trapézio, ou os próprios); `everyPathParses` (`PathParser().parsePathString(d).toNodes()` não vazio, se a forma for A); `pathsStayInsideTheViewBox`.
  - `ComponentColorsTest`: `bodyMapPrimaryAndSecondaryStandOut` (principal × neutro, secundário × neutro, principal × secundário, e cada um × `bgBase` e × `bgSurface`, ≥ 3:1, claro e escuro).
  - `LicenseTextTest`: `muscleMapLicenseKeepsItsWords` e `mediaLicenseKeepsItsWords` (os arquivos reais viram blocos sem perder palavra).
  - `BodyMapTest`: `drawsBothViewsSideBySide` (largura ≈ 2 × altura × razão da vista, ±2 dp); `isOneImageWithoutItsOwnLabel`; `legendHasSwatchAndText`.
  - `LicensesScreenTest.listsTheMediaAndMuscleMapEntries` (en e pt-BR; abrir a do MuscleMap mostra "MIT" e o commit).
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :designsystem:testAndroidHostTest --tests "*BodyMapPathsTest*" --tests "*ComponentColorsTest*" --tests "*LicenseTextTest*"` e ver falhar.
- [ ] **Step 3:** Copiar a fonte para `designsystem/illustrations/musclemap/` já agrupada (a partir do commit da Task 1, não do `HEAD` do dia), escrever o conversor definitivo, gerar `BodyMapPaths.kt`, o `BodyMap`, as licenças (o `media-CC0.md` com o texto oficial do CC0 1.0 e uma linha dizendo o que ele cobre: figuras, poses, dicas e nomes em pt-BR) e as entradas.
- [ ] **Step 4: Verificar.** O comando do Step 2 verde; `perl -e 'alarm shift; exec @ARGV' 1800 ./gradlew :designsystem:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.mooscles.designsystem.illustration.BodyMapTest,com.mooscles.designsystem.licenses.LicensesScreenTest` verde; `python3 -I tools/illustrations/gen_body_map.py designsystem/illustrations/musclemap <tmp>` gera um arquivo idêntico ao versionado (`cmp`); `check-design-tokens` limpo; `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :app:assembleDebug` e `pwsh tools/check-apk-budget.ps1 -Apk app/build/outputs/apk/debug/app-debug.apk` passa, com o tamanho anotado em `apk-base.md`.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_DS_IOS_TESTS`. Commit `designsystem: body map adapted from MuscleMap, media licenses`.

### Task 7: Busca, chip de menu, chip de leitura, tamanhos e ícones

**Files:**
- Create: `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/components/MSearchField.kt`, `.../components/MInfoChip.kt`
- Modify: `.../components/MChip.kt` (`MChipKind.Menu`), `.../tokens/SpaceTokens.kt` (`MoosclesSizes.mediaList = 56.dp`, `mediaDetailMax = 360.dp`), `tools/icons/gen.py` (`("Settings","settings")`, `("SearchX","search-x")`) e os arquivos gerados em `icons/`, `.../gallery/pages/ChipsPage.kt` e `FieldsPage.kt`, `strings_components.xml` (en e pt-BR), `.github/workflows/ci.yml`
- Test: `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/components/MSearchFieldTest.kt`, `MChipTest.kt` e `MInfoChipTest.kt` (novos testes), `designsystem/src/commonTest/kotlin/com/mooscles/designsystem/tokens/SizesTimingElevationTest.kt`, `.../icons/IconsTest.kt`

**Interfaces:**
- Produces: `@Composable fun MSearchField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, placeholder: String, onClear: () -> Unit, modifier: Modifier = Modifier, focusRequester: FocusRequester? = null)`: ícone `MIcons.Search` à esquerda, o placeholder como rótulo acessível enquanto vazio, botão de limpar (`MIcons.X`, 48 dp, rótulo `ds_search_clear` "Clear search" / "Limpar busca") só com texto, `ImeAction.Search`.
- Produces: `MChipKind.Menu` (papel `Role.Button`, `MIcons.ChevronDown` no fim, aparência de selecionado quando `selected`, sem check).
- Produces: `@Composable fun MInfoChip(label: String, modifier: Modifier = Modifier, icon: ImageVector? = null)` (não clicável, sem foco, 32 dp de altura desenhada, texto inteiro a 200%).
- Produces: `MTheme.sizes.mediaList`, `MTheme.sizes.mediaDetailMax`, `MIcons.Settings`, `MIcons.SearchX`.

- [ ] **Step 1: Testes que falham.** `MSearchFieldTest`: `clearShowsOnlyWithText`; `clearEmptiesAndKeepsFocus`; `placeholderIsTheLabelWhileEmpty`; `hasTheSetTextAction`; `textStaysWholeAtTwoX`. `MChipTest.menuChipIsAButtonAndShowsItsCount`. `MInfoChipTest.isTextOnlyAndNotFocusable`. `SizesTimingElevationTest.libraryMediaSizes` (56 e 360). `IconsTest.newLucideIconsExist`.
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :designsystem:testAndroidHostTest --tests "*SizesTimingElevationTest*" --tests "*IconsTest*"` e ver falhar.
- [ ] **Step 3:** Implementar; gerar os ícones com `python3 -I tools/icons/gen.py <lucide 1.63.0 icons> designsystem/src/commonMain/kotlin/com/mooscles/designsystem/icons` (os SVGs do Lucide numa pasta nova do scratchpad, do pacote npm `lucide-static@1.63.0`).
- [ ] **Step 4: Verificar.** O comando do Step 2 verde; `perl -e 'alarm shift; exec @ARGV' 1800 ./gradlew :designsystem:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.mooscles.designsystem.components.MSearchFieldTest,com.mooscles.designsystem.components.MChipTest,com.mooscles.designsystem.components.MInfoChipTest,com.mooscles.designsystem.gallery.GallerySmokeTest` verde; `check-design-tokens` limpo; o orçamento de fontes + ícones passa.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_DS_IOS_TESTS`. Commit `designsystem: search field, menu and info chips, library sizes and icons`.

### Task 8: As 20 poses do piloto

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/exercise/figures/FigureDsl.kt`, `BarbellFigures.kt`, `DumbbellFigures.kt`, `CableFigures.kt`, `BodyweightFigures.kt`, `MachineFigures.kt`, `ExerciseFigures.kt`, `FiguresGalleryPage.kt`; `screens/src/commonMain/kotlin/com/mooscles/screens/exercise/ExerciseMediaFor.kt`
- Modify: `app/src/main/java/com/mooscles/ui/DeveloperGallery.kt` (`extraPages = listOf(figuresGalleryPage())`), `designsystem/.../illustration/FigureModel.kt` e `.../tokens/IllustrationTokens.kt` só para fixar os comprimentos e traços finais (com os testes da Task 5 atualizados no mesmo commit), `.github/workflows/ci.yml`
- Test: `screens/src/commonTest/kotlin/com/mooscles/screens/exercise/figures/ExerciseFiguresTest.kt`, `.../figures/FigureHighlightsTest.kt`, `screens/src/uiTest/kotlin/com/mooscles/screens/exercise/figures/FiguresGalleryPageTest.kt`

**Interfaces:**
- Consumes: Task 5 (`FigurePose`, `FigureProp`, `FigureSegment`, `FigureKinematics.solve`, `ExerciseMedia.Figure`, `GalleryExtraPage`); Task 2 (`ExerciseAnatomy.forExercise`).
- Produces: `internal data class FigureFrames(val start: FigurePose, val end: FigurePose)`; `internal object ExerciseFigures { val PILOT: List<String>` (os 20 `stableId` da D4)`; fun forExercise(exerciseId: String): FigureFrames?; val stableIds: Set<String> }`, juntando os mapas das cinco famílias (`internal object BarbellFigures { val frames: Map<String, FigureFrames> }` e as outras), escritos com a DSL `figure("barbell_bench_flat") { start { … }; end { … } }`.
- Produces: `internal object FigureHighlights { fun forRegions(primary: List<MuscleRegion>): Set<FigureSegment> }`: CHEST e ABDOMINALS→TORSO_FRONT; LATS, MIDDLE_BACK, LOWER_BACK e TRAPS→TORSO_BACK; SHOULDERS→SHOULDER; BICEPS→UPPER_ARM_FRONT; TRICEPS→UPPER_ARM_BACK; FOREARMS→FOREARM; GLUTES e ABDUCTORS→HIP; QUADRICEPS e ADDUCTORS→THIGH_FRONT; HAMSTRINGS→THIGH_BACK; CALVES→SHIN_BACK; NECK→nenhum.
- Produces: `internal fun exerciseMedia(exercise: ExerciseUi): ExerciseMedia` (`ExerciseMedia.Figure(frames.end, highlights)` para um exercício do catálogo com figura; senão `ExerciseMedia.None`) e `fun figuresGalleryPage(): GalleryExtraPage` (todas as poses, A e B lado a lado, com o nome exibido, nos controles de tema da galeria).

- [ ] **Step 1: Testes que falham.**
  - `ExerciseFiguresTest`: `pilotIsComplete` (os 20 ids da D4, todos com os dois quadros); `everyPoseStaysInsideTheBoxWithMargin` (`FigureKinematics.solve(...).bounds` dentro de `[margin, 100 − margin]`); `groundedFeetTouchTheFloor` (com `floorY`, `ANKLE_NEAR` e `TOE_NEAR` a ±0,5 do chão, e o pé de lá também quando apoiado); `bothFramesHaveTheSameEquipment` (mesmos tipos de `FigureProp` na mesma ordem; os fixos iguais nos dois quadros); `framesDiffer`; `noOrphanKeys` (todo id existe no `ExerciseCatalog`); `noFamilyTableIsTooLarge` (nenhuma família com mais de 60 exercícios, a divisão do aviso de tamanho da spec).
  - `FigureHighlightsTest`: `everyRegionMapsAsTheTableSays`; `benchPressHighlightsTheFrontOfTheTorso`.
  - `FiguresGalleryPageTest.showsEveryPoseTwice`.
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :screens:testAndroidHostTest --tests "*ExerciseFiguresTest*" --tests "*FigureHighlightsTest*"` e ver falhar.
- [ ] **Step 3: Desenhar as 20 poses** à mão, no estilo aprovado (D3): quem escreve consulta referências só para ler a posição e anota cada consulta na tabela "Referências visuais consultadas" das Notas. Ajustar o esqueleto e os traços até as figuras lerem bem a 64 dp; fixar os valores finais nos testes da Task 5.
- [ ] **Step 4: Medir** (aviso de tamanho da spec): `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :screens:compileAndroidMain` (nome exato pelo `tasks --all`) e `javap -c -p` nas classes das famílias: anotar o maior método em bytes; `assembleDebug` limpo com e sem as poses: anotar bytes por pose. **Regra:** se um método passar de 32 KB ou uma pose de 1,5 KB no APK, trocar a DSL por um `FloatArray` por família (os mesmos campos, na ordem de `FigurePose`) e medir de novo. Registrar em `docs/superpowers/reports/AAAA-MM-DD-phase4a-verificacao/figures-size.md`.
- [ ] **Step 5: Verificar.** O comando do Step 2 verde; `perl -e 'alarm shift; exec @ARGV' 1800 ./gradlew :screens:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.mooscles.screens.exercise.figures.FiguresGalleryPageTest` verde; `:app:testDebugUnitTest --tests "com.mooscles.GalleryUnlockHostTest"` verde; `check-design-tokens` limpo.
- [ ] **Step 6:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS` (e `MIN_DS_IOS_TESTS` se mudou teste do DS). Commit `screens: pilot poses for twenty exercises`.

### Task 9: Folha do piloto para o portão G1

**Files:**
- Create: `screens/src/androidDeviceTest/kotlin/com/mooscles/screens/exercise/figures/PilotSheetCaptureTest.kt` (o composable da folha fica no próprio arquivo de teste, fora do produto), `docs/superpowers/reports/AAAA-MM-DD-phase4a-folha-piloto.md` e os PNGs em `docs/superpowers/reports/AAAA-MM-DD-phase4a-verificacao/pilot-{light,dark}-{64,detail}.png`
- Modify: as poses da Task 8, se a crítica pedir (commits próprios `screens: …`)

**Interfaces:**
- Consumes: `ExerciseFigures`, `ExerciseFigure`, `exerciseMedia` (Task 8).
- Produces (para o dono e o controlador): a folha e a pergunta do G1.

- [ ] **Step 1: Capturas.** `PilotSheetCaptureTest` (só Android) monta, para cada tema, uma grade com os 20 exercícios (nome em pt-BR, quadro A e quadro B), uma vez a 64 dp e outra no tamanho do detalhe (a largura útil de um aparelho de 412 dp, até 360 dp), e grava os PNGs por `captureToImage().asAndroidBitmap().compress(PNG)` no `getExternalFilesDir("captures")` do app de teste. Rodar com `perl -e 'alarm shift; exec @ARGV' 1800 ./gradlew :screens:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.mooscles.screens.exercise.figures.PilotSheetCaptureTest` e puxar com `adb pull` (o pacote do app de teste por `adb shell pm list packages | grep mooscles`).
- [ ] **Step 2: Crítica.** `critique-screen` (pelo `kit-design`) nas quatro folhas, com foco em legibilidade: se o movimento se entende a 64 dp, se o destaque se vê, se início e fim diferem. Achado alto de legibilidade é corrigido na pose, com nova captura; médios e baixos ficam no relatório com o motivo.
- [ ] **Step 3: Relatório da folha:** as quatro imagens, a lista dos 20, os achados, o que não foi provado (as figuras num iPhone, a leitura por quem treina) e a pergunta do G1: "Aprova a folha do piloto? Com aprovação, os lotes seguem por equipamento até os 287; sem aprovação, as figuras saem do produto e fica o ícone do equipamento." Commit `docs(report): phase 4A pilot figure sheet for G1`.
- [ ] **Step 4: Push** (checkpoint das Tasks 2–9): `git push origin feat/phase4a-library` e `gh run watch -R pedroyuuki/Mooscles` (sem bloquear: as próximas tasks seguem). Conferir `host` e `ios` verdes antes de entregar a folha.
- [ ] **Step 5: Entrega do G1.** O controlador manda ao dono o link do relatório no fork. **A 4A não espera a resposta** (decisão 2): as Tasks 10–24 seguem. A resposta, quando vier, é registrada nas Notas com a data e dispara a Task L (aprovado) ou a Task R (reprovado).

### Task 10: Nomes exibidos e textos de anatomia, também nas telas da fase 3

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/format/ExerciseDisplayNames.kt`, `.../format/AnatomyTexts.kt`, `screens/src/commonMain/composeResources/values/strings_anatomy.xml` e `values-pt-rBR/strings_anatomy.xml`
- Modify: `strings_catalog.xml` (en e pt-BR: `catalog_names_language` "en"/"pt-BR" e `catalog_default_folder` "My routines"/"Minhas rotinas"); `screens/.../workout/ExerciseItems.kt`, `ExerciseMenu.kt`, `RestSheet.kt` (o nome exibido também no `startTimer`), `WorkoutTimerSurfaces.kt`, `RecordAnnouncer.kt` (`nameOf: (ExerciseUi) -> String`, padrão `{ it.name }`), `ExercisePickerSheet.kt`; `screens/.../summary/WorkoutSummaryScreen.kt`; `screens/.../share/ShareCardData.kt` (`ShareCardTexts.catalogPortuguese: Boolean = false`, último parâmetro) e `app/src/main/java/com/mooscles/ui/WorkoutSharePreview.kt` (passa o valor); `app/compose-stability.conf` (`com.mooscles.data.Anatomy`); `.github/workflows/ci.yml`
- Test: `screens/src/uiTest/kotlin/com/mooscles/screens/format/ExerciseDisplayNamesTest.kt`, `.../format/AnatomyTextsTest.kt`; testes novos em `ActiveWorkoutScreenTest`, `WorkoutSummaryScreenTest` e `screens/src/commonTest/.../share/ShareCardDataTest.kt`

**Interfaces:**
- Consumes: `ExerciseNames.display` (Task 3); `Anatomy`, `MuscleRegion` (Task 2).
- Produces: `@Composable fun catalogInPortuguese(): Boolean` (o recurso `catalog_names_language` é "pt-BR"); `@Composable fun exerciseName(exercise: ExerciseUi): String`; `@Composable fun folderName(folder: RoutineFolderUi): String` (id `default` com o nome gravado igual a `RoutineFolder.DEFAULT_NAME` → `catalog_default_folder`; senão o gravado).
- Produces: `@Composable fun regionName(region: MuscleRegion): String` e `@Composable fun anatomySentence(anatomy: Anatomy?): String`: com secundários, "Principais: quadríceps. Secundários: glúteos, posteriores da coxa." / "Primary: quadriceps. Secondary: glutes, hamstrings."; sem secundários, só a primeira frase; `null`, "Corpo inteiro" / "Full body". Regiões em minúsculas, separadas por ", ": abdominais, abdutores, adutores, bíceps, panturrilhas, peito, antebraços, glúteos, posteriores da coxa, dorsais, lombar, meio das costas, pescoço, quadríceps, ombros, trapézio, tríceps / abdominals, abductors, adductors, biceps, calves, chest, forearms, glutes, hamstrings, lats, lower back, middle back, neck, quadriceps, shoulders, traps, triceps.

- [ ] **Step 1: Testes que falham.** `ExerciseDisplayNamesTest`: `catalogNameFollowsTheScreenLanguage` (en "Barbell bench press", pt-BR "Supino reto com barra"); `customKeepsItsTypedName`; `oldEnglishSnapshotShowsTheCurrentCatalogName`; `defaultFolderIsTranslatedOnlyWhileUnrenamed`. `AnatomyTextsTest`: `sentenceSamplesFromTheSpec` (en e pt-BR); `primaryOnly`; `fullBody`. `ActiveWorkoutScreenTest.catalogNamesFollowPortuguese` (card, menu, sheet de descanso e dock); `WorkoutSummaryScreenTest.catalogNamesFollowPortuguese`; `ShareCardDataTest.portugueseNamesWhenAsked`.
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :screens:testAndroidHostTest --tests "*ShareCardDataTest*" --tests "*StringsParityTest*"` e ver falhar.
- [ ] **Step 3:** Implementar e trocar todo `exercise.name` exibido nas telas da fase 3 por `exerciseName(exercise)`.
- [ ] **Step 4: Verificar.** O comando do Step 2 verde; `perl -e 'alarm shift; exec @ARGV' 1800 ./gradlew :screens:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.mooscles.screens.format.ExerciseDisplayNamesTest,com.mooscles.screens.format.AnatomyTextsTest,com.mooscles.screens.workout.ActiveWorkoutScreenTest,com.mooscles.screens.summary.WorkoutSummaryScreenTest,com.mooscles.screens.workout.RestSheetTest,com.mooscles.screens.workout.ExercisePickerSheetTest` verde; `:app:testDebugUnitTest --tests "com.mooscles.sharing.*"` verde.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS`. Commit `screens: catalog names in the screen's language`.

### Task 11: Dicas de execução: leitor e lote 1 (barra e halter)

**Files:**
- Create: `screens/src/commonMain/composeResources/files/exercise_cues/en.json`, `pt-BR.json`; `screens/src/commonMain/kotlin/com/mooscles/screens/exercise/ExerciseCues.kt`
- Modify: `.github/workflows/ci.yml`
- Test: `screens/src/commonTest/kotlin/com/mooscles/screens/exercise/ExerciseCuesTest.kt`, `screens/src/androidHostTest/kotlin/com/mooscles/screens/exercise/ExerciseCuesFilesTest.kt` (lê os arquivos de `src/commonMain/composeResources/files/exercise_cues/`, como o `StringsParityTest` lê os XML)

**Interfaces:**
- Produces: `internal object ExerciseCues { fun parse(json: String): Map<String, List<String>>; suspend fun load(portuguese: Boolean, read: suspend (String) -> ByteArray = { Res.readBytes(it) }): Map<String, List<String>> }` (cache por idioma, protegido por `Mutex`; uma leitura por idioma por processo; arquivo ilegível vira mapa vazio) e `@Composable internal fun rememberExerciseCues(exerciseId: String): List<String>?` (recebe o id completo e procura pelo `stableId`, sem o `catalog_`; `null` enquanto carrega; vazio sem dicas e para personalizados; segue `catalogInPortuguese()`).
- Formato: `{ "<stableId>": ["…", "…"] }`, até 4 dicas por exercício, cada uma até 110 caracteres (D8).

- [ ] **Step 1: Testes que falham.** `ExerciseCuesTest`: `parsesStableIdToCues`; `loadsOncePerLanguage` (a função de leitura falsa é chamada uma vez por idioma em 10 cargas paralelas); `unreadableFileGivesNoCues`. `ExerciseCuesFilesTest`: `sameKeysAndCountsInBothLanguages`; `atMostFourCuesOfAtMost110Characters`; `noEmptyCueAndNoOrphanKey`; `noExclamationEmojiOrHealthClaims` (nenhum "!", emoji, nem as raízes `cur`, `trat`, `lesã`, `médic`, `cure`, `treat`, `injur`, `medical`); `familiesAreCovered` (todo exercício cujo `stableId` começa por `barbell_`, `ez_bar_` ou `dumbbell_` tem dicas; a lista de prefixos cresce nas Tasks 12 e 13).
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :screens:testAndroidHostTest --tests "*ExerciseCues*"` e ver falhar.
- [ ] **Step 3:** Implementar o leitor e escrever as dicas dos 104 exercícios de barra (inclusive EZ) e halter, em en e pt-BR, próprias, sem copiar instruções de dataset nem linguagem médica (D8).
- [ ] **Step 4:** O comando do Step 2 verde.
- [ ] **Step 5: Revisão de conteúdo (D21a):** passada de agente independente nas dicas do lote, contra a anatomia da Task 2 e o conhecimento geral de exercício, e contra a pose nos exercícios do piloto. Correções antes do commit.
- [ ] **Step 6:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS`. Commit `screens: exercise cues reader and cues for barbell and dumbbell`.

### Task 12: Dicas, lote 2 (kettlebell, polia e peso corporal)

**Files:**
- Modify: `screens/src/commonMain/composeResources/files/exercise_cues/en.json`, `pt-BR.json`, `ExerciseCuesFilesTest.kt` (prefixos `kettlebell_`, `cable_`, `bodyweight_`)

**Interfaces:**
- Consumes: o formato e as regras da Task 11 (até 4 dicas de até 110 caracteres, as mesmas chaves nos dois idiomas) e a anatomia da Task 2.

- [ ] **Step 1:** Acrescentar os prefixos ao `familiesAreCovered` e rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :screens:testAndroidHostTest --tests "*ExerciseCuesFilesTest*"`: falha.
- [ ] **Step 2:** Escrever as dicas dos 114 exercícios, en e pt-BR.
- [ ] **Step 3:** O mesmo comando verde.
- [ ] **Step 4:** Revisão de conteúdo (D21a), como na Task 11.
- [ ] **Step 5:** Commit `screens: cues for kettlebell, cable and bodyweight`.

### Task 13: Dicas, lote 3 (Smith, máquinas e outro) e cobertura total

**Files:**
- Modify: os dois JSON, `ExerciseCuesFilesTest.kt` (`familiesAreCovered` vira `everyCatalogExerciseHasCues`), `.github/workflows/ci.yml` (só se o número de testes mudar)

**Interfaces:**
- Consumes: o formato e as regras da Task 11.
- Produces: dicas para os 287 (compromisso da D21).

- [ ] **Step 1:** Trocar o teste de prefixos por `everyCatalogExerciseHasCues` (os 287) e rodar o comando da Task 12: falha.
- [ ] **Step 2:** Escrever as dicas dos 69 exercícios restantes (`smith_`, `selectorized_`, `plate_`, `trap_bar_`).
- [ ] **Step 3:** O mesmo comando verde. Medir os dois JSON (bytes e comprimidos com `gzip -9 | wc -c`) e anotar em `docs/superpowers/reports/AAAA-MM-DD-phase4a-verificacao/cues-size.md` contra a estimativa da D8 (cerca de 50 KB comprimidos).
- [ ] **Step 4:** Revisão de conteúdo (D21a).
- [ ] **Step 5:** Commit `screens: cues for Smith, machines and trap bar; every catalog exercise has cues`.

### Task 14: Modelo da biblioteca: busca, filtros, ordem e recentes

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/library/LibraryModel.kt`, `.../library/LibraryFilters.kt`
- Modify: `.github/workflows/ci.yml`
- Test: `screens/src/commonTest/kotlin/com/mooscles/screens/library/LibraryModelTest.kt`, `screens/src/androidHostTest/kotlin/com/mooscles/screens/library/LibraryModelTimingTest.kt`

**Interfaces:**
- Consumes: `ExerciseSearchIndex`, `SearchEntry`, `ExerciseNames.display` (Task 3); `ExercisePicker.recents` (fase 3).
- Produces: `@Immutable data class LibraryFilters(val muscles: Set<String> = emptySet(), val equipment: Set<String> = emptySet(), val mineOnly: Boolean = false) { val active: Boolean }` (labels em inglês do `ExerciseUi`; "OU" dentro de uma dimensão, "E" entre elas), com um `Saver` para `rememberSaveable`.
- Produces: `@Immutable class LibraryModel { companion object { fun build(exercises: List<ExerciseUi>, portuguese: Boolean): LibraryModel }; val exercises: List<ExerciseUi>; val portuguese: Boolean; fun shownName(exercise: ExerciseUi): String; fun results(query: String, filters: LibraryFilters): List<ExerciseUi>; fun recents(history: List<WorkoutUi>): List<ExerciseUi> }`. `build` monta o índice e a ordem alfabética pelo nome mostrado dobrado (`SearchFolding.fold`, depois o nome, depois o id) uma vez; `results` filtra mantendo essa ordem; `recents` são até 8 do `ExercisePicker.recents`, com a entrada atual de cada id e sem os que sumiram.
- Produces: `@Composable fun rememberLibraryModel(exercises: List<ExerciseUi>): LibraryModel?`: `null` até o primeiro `build`, que roda em `Dispatchers.Default`; um cache de processo guarda o último par (lista, idioma) e devolve o mesmo modelo enquanto a lista for a mesma instância (o repositório preserva a identidade) e o idioma não mudar.

- [ ] **Step 1: Testes que falham** (`LibraryModelTest`): `musclesCombineWithOrAndDimensionsWithAnd`; `equipmentCombinesWithOr`; `mineOnlyKeepsCustomExercises`; `queryAndFiltersCombine`; `resultsAreAlphabeticalByShownName` (a ordem em pt-BR difere da em inglês); `searchFoldsAccents` (portado do `ExercisePickerTest`); `portugueseNamesAreFoundOnAnEnglishScreen`; `recentsUseTheCurrentEntryAndSkipMissing`; `filtersSaverRoundTrips`.
- [ ] **Step 2:** `LibraryModelTimingTest` (JVM): catálogo + 300 personalizados; `build` uma vez; digitar "supino inclinado" letra a letra, 9 rodadas; mediana por tecla < 20 ms e `build` < 500 ms (folga larga; o alvo de 4 ms e 8 ms é medido no emulador na Task 22). Imprime `TIMING library median=<ms> p95=<ms>`.
- [ ] **Step 3:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :screens:testAndroidHostTest --tests "*LibraryModel*"` e ver falhar; implementar; ver passar.
- [ ] **Step 4:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS`. Commit `screens: library model with bilingual search and filters`.

### Task 15: A tela da biblioteca

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/library/LibraryScreen.kt`, `.../library/LibraryRow.kt`, `.../library/LibraryFiltersUi.kt`, `screens/src/commonMain/composeResources/values/strings_library.xml` e `values-pt-rBR/strings_library.xml`
- Modify: `.github/workflows/ci.yml`
- Test: `screens/src/uiTest/kotlin/com/mooscles/screens/library/LibraryScreenTest.kt`, `.../library/LibraryFiltersUiTest.kt`

**Interfaces:**
- Consumes: Task 14 (`rememberLibraryModel`, `LibraryFilters`); Task 7 (`MSearchField`, `MChipKind.Menu`, `MIcons.Settings`, `MIcons.SearchX`, `sizes.mediaList`); Task 8 (`exerciseMedia`); Task 10 (`exerciseName` via `LibraryModel.shownName`, `muscleLabel`, `equipmentLabel`); `EquipmentIcons`, `ScreenErrorToast` (fase 3).
- Produces: `const val LibraryScreenTag = "library-screen"`; `@Composable fun LibraryRoute(state: UiState, actions: MoosclesActions, onOpenExercise: (String) -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier)`, com o estado de tela (busca, filtros, rolagem) em `rememberSaveable`. A Task 16 acrescenta o formulário dentro dele.
- Produces (para a Task 18): `@Composable internal fun MuscleFilterRow(selected: Set<String>, onToggle: (String) -> Unit, modifier: Modifier = Modifier)` (os 12 `MuscleGroup` na ordem do enum, `MChipKind.Filter`); `@Composable internal fun EquipmentFilterChip(selected: Set<String>, onOpen: () -> Unit)` ("Equipamento" ou "Equipamento · 2"); `@Composable internal fun EquipmentFilterSheet(selected: Set<String>, onChange: (Set<String>) -> Unit, onDismiss: () -> Unit)` (os 9, seleção múltipla, foco preso e devolvido ao chip).
- Tags: `library-row-<id>`, `library-search`, `library-chip-<muscleLabel>`, `library-equipment-chip`, `library-mine-chip`, `library-clear-filters`, `library-count`, `library-create`, `library-settings`.
- Strings (en / pt-BR): `library_title` "Library"/"Biblioteca"; `library_search` "Search by name, muscle or equipment"/"Buscar por nome, músculo ou equipamento"; `library_create` "Create exercise"/"Criar exercício"; `library_settings` "Settings"/"Configurações"; plural `library_count` "%1$d exercise(s)"/"%1$d exercício(s)"; `library_recent` "Recently used"/"Usados recentemente"; `library_all` "All"/"Todos"; `library_equipment` "Equipment"/"Equipamento"; `library_equipment_count` "Equipment · %1$d"/"Equipamento · %1$d"; `library_mine` "My exercises"/"Meus exercícios"; `library_clear_filters` "Clear filters"/"Limpar filtros"; `library_empty` "No exercises found"/"Nenhum exercício encontrado"; `library_create_named` "Create “%1$s”"/"Criar “%1$s”"; `library_custom` "Custom"/"Personalizado"; `library_row` "%1$s, %2$s, %3$s" e `library_row_custom` "%1$s, %2$s, %3$s, custom"/"…, personalizado" (o nó único da linha).

- [ ] **Step 1: Testes que falham.**
  - `LibraryScreenTest`: `topHasTitleSettingsAndCreateAt48`; `rowIsOneNodeWithNameMuscleEquipmentAndCustom` ("Supino reto com barra, Peito, Barra" em pt-BR); `rowsAreAtLeast72WithFigureOrEquipmentIcon`; `recentThenAllWithoutSearchOrFilter`; `searchShowsOnlyResultsAlphabetically`; `countIsAPoliteLiveRegionAndFollowsResults`; `rowTapOpensTheExercise`; `settingsOpensSettings`; `emptyResultOffersClearFiltersAndCreateFromTheQuery`; `loadingShowsSkeletonRows` (antes do modelo); `searchFiltersAndScrollSurviveRecreation` (`ScreenRestoration`); `listChangeWhileSearchingKeepsTheQueryAndUpdatesResults`; `queryTypedBeforeTheIndexIsReadyIsAppliedWhenItArrives`; `nameWrapsToThreeLinesAtTwoX`; `errorShowsTheLocalizedToastAndClears`.
  - `LibraryFiltersUiTest`: `muscleChipsAreCheckboxesInEnumOrder`; `equipmentSheetSelectsSeveralAndTheChipCounts`; `equipmentSheetSelectionSurvivesRecreation`; `equipmentSheetReturnsFocusToTheChip`; `mineOnlyToggles`; `clearFiltersShowsOnlyWithAFilter`.
- [ ] **Step 2:** Rodar as duas classes no emulador (`perl -e 'alarm shift; exec @ARGV' 1800 ./gradlew :screens:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.mooscles.screens.library.LibraryScreenTest,com.mooscles.screens.library.LibraryFiltersUiTest`) e ver falhar.
- [ ] **Step 3:** Implementar conforme "Biblioteca (aba)" da spec: `MSearchField` fixo sob o topo, a lista rolando por baixo, linhas de 72 dp no mínimo com mídia de 56 dp (`ExerciseThumbnail`, quadro B parado), nome até 2 linhas (3 a 200%), "Peito · Barra" e a etiqueta "Personalizado".
- [ ] **Step 4:** O comando do Step 2 verde; `:screens:testAndroidHostTest --tests "*StringsParityTest*"` verde; `check-design-tokens` limpo.
- [ ] **Step 5:** Subir `MIN_SCREENS_IOS_TESTS`. Commit `screens: library tab with search, filters and figures`.

### Task 16: Formulário de exercício personalizado

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/exercise/CustomExerciseForm.kt`, `.../exercise/CustomExerciseRules.kt`, `screens/src/commonMain/composeResources/values/strings_exercise.xml` e `values-pt-rBR/strings_exercise.xml`
- Modify: `screens/.../library/LibraryScreen.kt` (abre o formulário pelo "Criar exercício" e pelo "Criar “…”"; espera o id novo, decisão 13), `.github/workflows/ci.yml`
- Test: `screens/src/commonTest/kotlin/com/mooscles/screens/exercise/CustomExerciseRulesTest.kt`, `screens/src/uiTest/kotlin/com/mooscles/screens/exercise/CustomExerciseFormTest.kt`; testes novos em `LibraryScreenTest`

**Interfaces:**
- Consumes: `MoosclesActions.createCustomExercise` e o formato do `RecordingActions` (Task 4); `newId()`; `ExerciseNames` (Task 3); `MTextField`, `MChip` (`SetType`), `RadioRow`, `MBottomSheet` (tela cheia); `conventionLabel`, `conventionExplanation` (fase 3).
- Produces: `internal object CustomExerciseRules { fun defaultConvention(equipment: Equipment): LoadConvention` (DUMBBELL→PER_HAND, BODYWEIGHT→BODYWEIGHT, o resto→TOTAL_LOAD)`; fun duplicateOf(name: String, exercises: List<ExerciseUi>, exceptId: String?): ExerciseUi?` (o nome dobrado igual ao gravado, ao inglês do catálogo ou ao pt-BR de algum exercício)`; fun hasLoggedSets(exerciseId: String, history: List<WorkoutUi>, active: WorkoutUi?): Boolean }`.
- Produces: `@Composable internal fun CustomExerciseForm(initial: ExerciseUi?, prefillName: String, state: UiState, actions: MoosclesActions, onSaved: (exerciseId: String) -> Unit, onCancel: () -> Unit)`: novo começa sem músculo e sem equipamento (os dois obrigatórios); "Salvar" chama `createCustomExercise(newId(), …)` (novo) ou `saveCustomExercise(id, …)` (editar) uma vez só, e `onSaved(id)`; nome inválido (vazio ou mais de 120) mostra o erro no campo e não fecha. Rascunho (nome, escolhas, id) em `rememberSaveable`.
- Strings (en / pt-BR): `exercise_form_new` "New exercise"/"Novo exercício"; `exercise_form_edit` "Edit exercise"/"Editar exercício"; `exercise_form_name` "Name"/"Nome"; `exercise_form_muscle` "Muscle group"/"Grupo muscular"; `exercise_form_equipment` "Equipment"/"Equipamento"; `exercise_form_convention` "Logging convention"/"Convenção de registro"; `exercise_form_save` "Save"/"Salvar"; `exercise_form_cancel` "Cancel"/"Cancelar"; `exercise_form_duplicate` "“%1$s” already exists. You can save anyway."/"Já existe “%1$s”. Você pode salvar mesmo assim."; `exercise_form_convention_change` "Sets already logged keep the convention they were logged with."/"As séries já registradas continuam com a convenção de quando foram feitas."; `exercise_form_name_required` "Enter a name."/"Digite um nome."; `exercise_form_name_long` "Use 120 characters or fewer."/"Use até 120 caracteres."

- [ ] **Step 1: Testes que falham.** `CustomExerciseRulesTest`: `defaultConventionByEquipment`; `duplicateMatchesStoredEnglishAndPortugueseNames`; `duplicateIgnoresTheExerciseBeingEdited`; `loggedSetsCountHistoryAndTheActiveWorkout`. `CustomExerciseFormTest`: `blankNameShowsTheErrorAndStays`; `nameOver120ShowsTheError`; `duplicateWarningDoesNotBlock`; `equipmentSetsTheDefaultConventionWhichCanChange`; `saveCreatesWithTheScreenId` (`RecordingActions.calls` termina em `"createCustomExercise:<id>,Supino articulado,Chest,Plate-loaded machine,Total load"`); `saveTwiceCreatesOnce`; `editPrefillsAndSaves`; `conventionChangeWarnsWhenSetsExist`; `cancelCallsNothing`; `draftSurvivesRecreation`; `prefillComesFromTheSearch`; `largeFontKeepsEveryLabelWhole`. `LibraryScreenTest`: `creatingOpensTheNewExerciseWhenItArrives`; `creationErrorDropsThePendingOpen`.
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :screens:testAndroidHostTest --tests "*CustomExerciseRulesTest*"` e as classes de UI no emulador (`perl -e 'alarm shift; exec @ARGV' 1800 ./gradlew :screens:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.mooscles.screens.exercise.CustomExerciseFormTest,com.mooscles.screens.library.LibraryScreenTest`); ver falhar.
- [ ] **Step 3:** Implementar conforme "Formulário de exercício personalizado" da spec, sem a linha "Foto" (D2).
- [ ] **Step 4:** Os comandos do Step 2 verdes.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS`. Commit `screens: custom exercise form`.

### Task 17: Detalhe do exercício

**Files:**
- Create: `screens/src/commonMain/kotlin/com/mooscles/screens/exercise/ExerciseInfoScreen.kt`, `.../exercise/ExerciseHistorySummary.kt`, `.../exercise/AddToRoutineSheet.kt`, `.../exercise/BodyRegionBridge.kt`
- Modify: `strings_exercise.xml` (en e pt-BR), `.github/workflows/ci.yml`
- Test: `screens/src/commonTest/kotlin/com/mooscles/screens/exercise/ExerciseHistorySummariesTest.kt`, `.../exercise/BodyRegionBridgeTest.kt`, `screens/src/uiTest/kotlin/com/mooscles/screens/exercise/ExerciseInfoScreenTest.kt`, `.../exercise/AddToRoutineSheetTest.kt`

**Interfaces:**
- Consumes: Task 5 (`AnimatedExerciseFigure`, `ExerciseThumbnail`); Task 6 (`BodyMap`, `BodyMapLegend`, `BodyRegion`); Task 7 (`MInfoChip`, `sizes.mediaDetailMax`); Task 8 (`ExerciseFigures.forExercise`, `FigureHighlights`); Task 10 (`exerciseName`, `anatomySentence`, `folderName`); Task 11 (`rememberExerciseCues`); Task 16 (`CustomExerciseForm`); `ExerciseAnatomy.forExercise` (Task 2); `conventionCaption`, `shortDate`, `RecompositionProbe`, `ScreenErrorToast` (fase 3); `BestSets` e o formato de carga × repetições do resumo de fim.
- Produces: `const val ExerciseInfoScreenTag = "exercise-info-screen"`; `@Composable fun ExerciseInfoRoute(exerciseId: String, state: UiState, actions: MoosclesActions, onBack: () -> Unit, onOpenHistory: (String) -> Unit, onOpenRoutine: (String) -> Unit, onOpenWorkout: () -> Unit, modifier: Modifier = Modifier, foreground: Boolean = true)`.
- Produces: `internal data class HistorySummary(val sessions: Int, val lastDate: Long?, val lastWeightKg: Double?, val lastReps: Int?, val bestWeightKg: Double?, val bestReps: Int?, val bestIsRecord: Boolean)` e `internal object ExerciseHistorySummaries { fun of(exerciseId: String, history: List<WorkoutUi>, records: List<RecordUi>): HistorySummary }`: sessões = treinos finalizados com pelo menos uma série concluída do exercício; "última vez" = a melhor série elegível (`BestSets`) do mais recente deles; "melhor" = o recorde de carga atual do exercício em `records` (maior `weightKg`, empate o mais recente), com `bestIsRecord = true`, ou, sem recorde, a melhor série elegível de todo o histórico.
- Produces: `@Composable internal fun AddToRoutineSheet(exerciseId: String, state: UiState, actions: MoosclesActions, onAdded: (routineId: String, routineName: String) -> Unit, onDismiss: () -> Unit)`: as rotinas agrupadas por pasta, na ordem gravada, com `folderName`; um toque chama `addRoutineExercise` uma vez, fecha e chama `onAdded`; sem rotinas, o texto `exercise_add_no_routines`. O detalhe guarda o pedido e mostra o toast só quando a rotina aparece no estado com o exercício a mais (o eco); um `errorCode` antes do eco descarta o pedido e mostra o erro.
- Produces: `internal fun MuscleRegion.toBodyRegion(): BodyRegion` (por nome, decisão 8).
- Tags: `exercise-info-media`, `exercise-info-meta`, `exercise-info-muscles`, `exercise-info-cues`, `exercise-info-history`, `exercise-info-add-routine`, `exercise-info-add-workout`, `exercise-info-menu`; probe com as chaves `figure` e `info-body`.
- Strings (en / pt-BR): `exercise_muscles` "Muscles"/"Músculos"; `exercise_how_to` "How to do it"/"Como fazer"; `exercise_history` "Your history"/"Seu histórico"; `exercise_history_none` "You haven’t done this exercise yet."/"Você ainda não fez este exercício."; `exercise_history_last` "%1$s · %2$s"; plural `exercise_history_sessions` "%1$d session(s)"/"%1$d sessão/sessões"; `exercise_history_full` "View full history"/"Ver histórico completo"; `exercise_edit` "Edit exercise"/"Editar exercício"; `exercise_add_routine` "Add to routine"/"Adicionar à rotina"; `exercise_add_workout` "Add to workout"/"Adicionar ao treino"; `exercise_added_to` "Added to %1$s"/"Adicionado a %1$s"; `exercise_added_to_workout` "Added to the workout"/"Adicionado ao treino"; `exercise_open` "Open"/"Abrir"; `exercise_add_no_routines` "You have no routines yet. Create one in the Train tab."/"Você ainda não tem rotinas. Crie uma na aba Treinar."; `exercise_not_found` "Exercise not found"/"Exercício não encontrado"; `exercise_back_to_library` "Back to the library"/"Voltar à biblioteca"; `exercise_more` "More options"/"Mais opções".

- [ ] **Step 1: Testes que falham.**
  - `BodyRegionBridgeTest` (`commonTest`): `everyMuscleRegionHasTheBodyRegionOfTheSameName`.
  - `ExerciseHistorySummariesTest`: `noHistory`; `lastSessionBestSetSkipsWarmupsAndAssistance`; `sessionsCountOnlyWorkoutsWithCompletedSets`; `aWeightRecordIsTheBest`; `withoutARecordTheBestSetOfAllHistory`; `otherExercisesDoNotCount`.
  - `ExerciseInfoScreenTest`: `readingOrderIsNameMediaMetaMusclesTextCuesHistoryActions` (o mapa não entra como nó próprio); `pilotExerciseShowsTheAlternatingFigure`; `exerciseWithoutAFigureShowsTheEquipmentPlaceholder`; `reducedMotionStopsOnTheEndAndATapToggles`; `notForegroundStopsTheFigure`; `onlyTheFigureRecomposesOnEachFrame` (`info-body` fica parado, `figure` sobe); `conventionCaptionOnlyWhenNotTotalLoad`; `muscleTextSitsUnderTheMap` (a frase de amostra em pt-BR); `cuesAreNumberedAndTheSectionHidesWithout`; `historyCardWithoutHistory`; `historyCardShowsLastBestAndSessions`; `fullHistoryOpensTheOldScreen`; `editIsOnlyForCustomExercises`; `addToWorkoutOnlyWithAnActiveWorkout` (chama `addWorkoutExercise:<workoutId>,<exerciseId>` e o toast com "Abrir" chama `onOpenWorkout`); `exerciseThatDisappearsShowsNotFound` (o estado perde o exercício com a tela aberta); `notFoundGoesBackToTheLibrary`; `addToRoutineToastsAfterTheEchoWithOpen` ("Adicionado a Treino A"; "Abrir" chama `onOpenRoutine("r1")`); `addToARoutineThatIsGoneShowsTheErrorNotTheToast` (o `errorCode` chega antes do eco); `textsStayWholeAtTwoX`.
  - `AddToRoutineSheetTest`: `routinesAreGroupedByFolderWithTheDefaultTranslated`; `aTapAddsOnceAndCloses` (`RecordingActions.calls` tem um só `"addRoutineExercise:r1,<exerciseId>"` depois de dois toques rápidos); `withoutRoutinesSaysWhereToCreateOne`.
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :screens:testAndroidHostTest --tests "*ExerciseHistorySummariesTest*" --tests "*BodyRegionBridgeTest*"` e, no emulador, `perl -e 'alarm shift; exec @ARGV' 1800 ./gradlew :screens:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.mooscles.screens.exercise.ExerciseInfoScreenTest,com.mooscles.screens.exercise.AddToRoutineSheetTest`; ver falhar.
- [ ] **Step 3:** Implementar conforme "Detalhe do exercício" da spec. A mídia é uma moldura 1:1 com a largura útil, até `sizes.mediaDetailMax`, centrada. O toast de "Adicionado a…" só aparece quando a rotina mostra o exercício a mais no estado (eco), nunca no toque.
- [ ] **Step 4:** Os comandos do Step 2 verdes; `StringsParityTest` verde.
- [ ] **Step 5:** Subir `MIN_HOST_TESTS` e `MIN_SCREENS_IOS_TESTS`. Commit `screens: exercise detail with figure, body map, cues and history`.

### Task 18: Seletor com filtros e mídia; "Sobre o exercício" no treino

**Files:**
- Modify: `screens/.../workout/ExercisePickerSheet.kt` (busca pelo `LibraryModel`, `MuscleFilterRow`, `EquipmentFilterChip`/`EquipmentFilterSheet`, linhas com `ExerciseThumbnail` de 56 dp), `.../workout/ExercisePicker.kt` (sai o `filter`; `recents` fica), `.../workout/ExerciseMenu.kt` (item "Sobre o exercício" antes de "Histórico do exercício", tag `ExerciseMenuTags.ABOUT`), `.../workout/ActiveWorkoutRoute.kt` e `ActiveWorkoutScreen.kt` (`onExerciseInfo: (exerciseId: String) -> Unit = {}`), `strings_workout.xml` (`workout_menu_about` "About the exercise"/"Sobre o exercício"), `.github/workflows/ci.yml`
- Test: `screens/src/uiTest/.../workout/ExercisePickerSheetTest.kt` (testes novos e ajustes), `screens/src/commonTest/.../workout/ExercisePickerTest.kt` (os dois portados saem), `screens/src/uiTest/.../workout/ActiveWorkoutScreenTest.kt` (teste novo)

**Interfaces:**
- Consumes: Task 14 (`rememberLibraryModel`, `LibraryFilters`), Task 15 (`MuscleFilterRow`, `EquipmentFilterChip`, `EquipmentFilterSheet`), Task 8 (`exerciseMedia`).
- Produces (para a Task 19): `ActiveWorkoutRoute(…, onExerciseInfo: (exerciseId: String) -> Unit = {})`, como último parâmetro antes dos que têm valor padrão de teste.

- [ ] **Step 1: Testes que falham.** `ExercisePickerSheetTest`: `muscleChipsFilterTheList`; `equipmentSheetFiltersFromThePicker`; `rowsShowTheFigureOrTheIcon`; `portugueseSearchFindsTheCatalog`. `ActiveWorkoutScreenTest.menuAboutOpensTheExerciseInfo`. Ajustar `rowsAreFiftySixWithMuscleAndEquipment` à linha nova (continua ≥ 56 dp).
- [ ] **Step 2:** Rodar as classes no emulador (`perl -e 'alarm shift; exec @ARGV' 1800 ./gradlew :screens:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.mooscles.screens.workout.ExercisePickerSheetTest,com.mooscles.screens.workout.ActiveWorkoutScreenTest,com.mooscles.screens.workout.ExerciseListTest`) e ver falhar.
- [ ] **Step 3:** Implementar. Apagar `ExercisePickerTest.searchFoldsAccentsLikeToday` e `allIsAlphabetical` com o motivo no commit (portados na Task 14).
- [ ] **Step 4:** O comando do Step 2 verde, mais `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :screens:testAndroidHostTest --tests "*ExercisePickerTest*"`.
- [ ] **Step 5:** Ajustar `MIN_SCREENS_IOS_TESTS` (+5 novos, −2 portados) e `MIN_HOST_TESTS` (−2). Commit `screens: picker filters and media, about the exercise from the workout`.

### Task 19: O `:app` hospeda a biblioteca e o detalhe

**Files:**
- Modify: `app/src/main/java/com/mooscles/ui/MoosclesApp.kt` (`ShellTabs.LIBRARY` → `LibraryRoute`; rota nova `exerciseInfo/{id}` → `ExerciseInfoRoute` com `foreground` do ciclo de vida; `newScreen` inclui `library` e `exerciseInfo/{id}`; `onOpenSettings` navega a `settings`; a rota do treino passa `onExerciseInfo`; constante `EXERCISE_INFO_ROUTE`), `app/src/main/java/com/mooscles/ui/LibraryScreens.kt` (saem `LibraryScreen` e `ExerciseCard`), `app/src/test/java/com/mooscles/ShellNavigationHostTest.kt` (`assertLibraryRoot` pela tag), `.github/workflows/ci.yml`
- Create: `app/src/test/java/com/mooscles/LibraryNavigationHostTest.kt`; `docs/superpowers/reports/AAAA-MM-DD-phase4a-verificacao/tap-baseline-4a.md`; temporário `app/src/test/java/com/mooscles/LibraryTapBaselineHostTest.kt` (apagado no mesmo commit)

**Interfaces:**
- Consumes: `LibraryRoute` (Task 15), `ExerciseInfoRoute` (Task 17), `ActiveWorkoutRoute(onExerciseInfo)` (Task 18), `LibraryScreenTag`, `ExerciseInfoScreenTag`.
- Produces (para as Tasks 21 e 22): a contagem de toques de hoje em `tap-baseline-4a.md`.

- [ ] **Step 1: Contagem de hoje** (antes de trocar a tela). `LibraryTapBaselineHostTest` (Robolectric, sobre o `LibraryScreen` antigo, contando `performClick`): "achar um exercício por músculo e abrir" a partir da aba; "criar exercício personalizado com nome, músculo e equipamento" (o texto digitado não conta). Rodar com `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :app:testDebugUnitTest --tests "com.mooscles.LibraryTapBaselineHostTest"` e registrar os números em `tap-baseline-4a.md`, com o commit.
- [ ] **Step 2: Testes que falham** (`LibraryNavigationHostTest`): `libraryToDetailAndBackKeepsSearchFiltersAndScroll`; `detailFullHistoryOpensTheOldScreenAndBackReturns`; `workoutMenuAboutOpensTheDetailAndBackReturnsToTheWorkout`; `settingsShortcutOpensSettings`; `addToRoutineOpenOpensTheRoutineEditor`; `globalSnackbarNeverShowsOnLibraryOrDetail`; `reselectingTheLibraryTabFromTheDetailReturnsToTheList`. Ajustar `ShellNavigationHostTest.assertLibraryRoot` para `onNodeWithTag(LibraryScreenTag)`.
- [ ] **Step 3:** Rodar `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :app:testDebugUnitTest --tests "com.mooscles.LibraryNavigationHostTest" --tests "com.mooscles.ShellNavigationHostTest"` e ver falhar.
- [ ] **Step 4:** Implementar. Apagar `LibraryScreen`, `ExerciseCard` e o `LibraryTapBaselineHostTest` no mesmo commit (motivo: a tela antiga saiu; os números ficam no relatório).
- [ ] **Step 5: Verificar.** O comando do Step 3 verde, mais `--tests "com.mooscles.DiscardNavigationHostTest" --tests "com.mooscles.GalleryUnlockHostTest" --tests "com.mooscles.SettingsScreenHostTest"` e `:app:lintDebug` e `:app:assembleDebug` verdes; `pwsh tools/check-apk-budget.ps1 -Apk app/build/outputs/apk/debug/app-debug.apk` passa (anotar o crescimento).
- [ ] **Step 6:** Subir `MIN_HOST_TESTS`. Commit `app: host the new library and exercise detail`. **Push** (checkpoint das Tasks 10–19) e `gh run watch -R pedroyuuki/Mooscles` sem bloquear.

### Task 20: Fluxo de ponta a ponta da biblioteca

**Files:**
- Create: `screens/src/uiTest/kotlin/com/mooscles/screens/flow/LibraryFlowTest.kt`
- Modify: `.github/workflows/ci.yml` (`MIN_SCREENS_IOS_TESTS`)

**Interfaces:**
- Consumes: `FlowHarness`, `FlowViewModel`, `openFlowDatabase`, `openFlowStore` (fase 3); `LibraryRoute`, `ExerciseInfoRoute`.

- [ ] **Step 1: Teste.** `libraryFlowPortugueseLightAtOneX` e `libraryFlowEnglishDarkAtTwoX`, cada um sobre o ViewModel, o Room e o DataStore reais, com um seletor de tela simples no teste (sem NavHost): semear a rotina "Treino A" e um treino finalizado com supino; buscar "supino inclinado halter" (pt-BR) ou "incline dumbbell" (en) e abrir; ver a figura ou o ícone, a frase dos músculos, as dicas e o card do histórico; "Adicionar à rotina" → "Treino A" → toast; voltar, filtrar pelo chip "Costas"/"Back", abrir um resultado; buscar "supino articulado", "Criar “supino articulado”", escolher músculo e equipamento, salvar e ver o detalhe do exercício novo. Em cada estado: todo clicável com rótulo, alvos ≥ 48 dp, `assertNumbersWhole()`; `onRoot().captureToImage()` gravado no iOS em `screens/build/ios-captures/library-<estado>.png` (se lançar, imprimir `FLOW capture unsupported` e seguir).
- [ ] **Step 2:** Rodar a classe no emulador até ficar verde (`perl -e 'alarm shift; exec @ARGV' 1800 ./gradlew :screens:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.mooscles.screens.flow.LibraryFlowTest`). Defeito revelado pelo fluxo é corrigido na task dona, com um teste focado lá.
- [ ] **Step 3:** Subir `MIN_SCREENS_IOS_TESTS` (+2). Commit `screens: end-to-end library flow over the real view model, Room and DataStore`.

### Task 21: Orçamento de toques e varredura de acessibilidade

**Files:**
- Create: `screens/src/uiTest/kotlin/com/mooscles/screens/a11y/LibraryAccessibilitySweepTest.kt`
- Modify: `screens/src/uiTest/kotlin/com/mooscles/screens/budget/TapBudgetTest.kt`, `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: os números de `tap-baseline-4a.md` (Task 19), copiados como literais com o link no comentário; `assertClickablesLabeledAndSized`, `assertButtonTextsWhole`, `assertNumbersWhole`, `screenReaderOrder` (fase 3).

- [ ] **Step 1: Testes.**
  - `TapBudgetTest.findByMuscleAndOpen` ≤ 3 e `createCustomExerciseWithNameMuscleAndEquipment` ≤ 4 (o texto digitado não conta), cada um também ≤ a contagem de hoje.
  - `LibraryAccessibilitySweepTest`: biblioteca (sem busca, filtrada, vazia), detalhe (com figura, sem figura, sem dicas, sem histórico, não encontrado) e formulário (novo, editar com aviso) × claro e escuro × 1,0, 1,3 e 2,0 (mais 2,01 no Android) × 320 e 412 dp × topo, meio e fim da rolagem. Em cada combinação: rótulo em todo clicável, alvos de 48 dp, `assertNumbersWhole()`, nenhum texto de botão cortado. Mais `libraryReadingOrder` (topo, busca, filtros, contagem, lista) e `detailReadingOrder` (nome, mídia, metadados, texto dos músculos, dicas, histórico, ações).
- [ ] **Step 2:** Rodar `perl -e 'alarm shift; exec @ARGV' 1800 ./gradlew :screens:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.mooscles.screens.budget.TapBudgetTest,com.mooscles.screens.a11y.LibraryAccessibilitySweepTest` até ficar verde, corrigindo na task dona.
- [ ] **Step 3:** Subir `MIN_SCREENS_IOS_TESTS`. Commit `screens: library tap budget and accessibility sweep`.

### Task 22: Suítes de aparelho, atualização por cima, sem rede, desempenho e tamanho

**Files:**
- Create: em `docs/superpowers/reports/AAAA-MM-DD-phase4a-verificacao/`, `device-suites.md`, `upgrade.md`, `offline.md`, `performance.md`, `apk.md`, `deps-diff.txt`; `screens/src/androidDeviceTest/kotlin/com/mooscles/screens/library/LibrarySearchTimingTest.kt`
- Modify: o que as medidas pedirem, em commits próprios com teste

- [ ] **Step 1: Suítes completas, uma vez** (nenhuma outra lane usa o emulador nem o Gradle): `perl -e 'alarm shift; exec @ARGV' 5400 ./gradlew :designsystem:connectedAndroidDeviceTest`, depois o mesmo para `:screens:connectedAndroidDeviceTest`, depois `:app:connectedDebugAndroidTest` (`UiFlowTest`, `UiFeatureSmokeTest`; o `connectedDebugAndroidTest` desinstala o app no fim, por isso vem antes da atualização por cima). Registrar os totais e as falhas corrigidas em `device-suites.md`. Uma falha é corrigida na task dona, e só a classe dela roda de novo.
- [ ] **Step 2: Atualização por cima** (rede do emulador desligada): com o APK da `main` em `2359ab6` (depois da fase 3), em pt-BR e em libras, criar duas pastas, três rotinas (uma com séries manuais, uma herdando o recorde), um exercício personalizado e um treino ativo a partir de uma rotina, com séries concluídas e o descanso correndo; exportar; `md5` de `files/datastore/local_preferences.preferences_pb` (`run-as com.mooscles`); `adb install -r` do APK da branch. Conferir e registrar em `upgrade.md`: a biblioteca com o personalizado e os nomes em pt-BR; o detalhe do personalizado e de um exercício do piloto; o treino ativo com os nomes traduzidos e as mesmas séries; o banco ainda na v5 (`PRAGMA user_version`); o mesmo MD5; o export depois igual ao de antes, tirando `exportedAt` (`diff` dos JSON).
- [ ] **Step 3: Sem rede:** instalação nova com a rede do emulador desligada desde antes: biblioteca, busca, filtros, detalhe (figura, mapa, dicas), formulário e seletor funcionam inteiros. `git diff main...HEAD` sem código de rede novo (procurar `http`, `URL`, `HttpURLConnection`, `ktor`). Registrar em `offline.md`.
- [ ] **Step 4: Desempenho** pelo procedimento do PERFORMANCE.md, no `assemblePerformance`, com a base grande e mais 300 personalizados (um fixture derivado, gerado por script e guardado em `local/`): `LibrarySearchTimingTest` (aparelho; digitar "supino inclinado" letra a letra em 9 rodadas; imprime e confere mediana ≤ 4 ms e p95 ≤ 8 ms por tecla); tocar na aba até o primeiro quadro da lista (skeleton ou lista, `atrace`); rolagem rápida da biblioteca inteira com figuras: quadros lentos (`gfxinfo` janky) ≤ 5%; abrir o detalhe até a figura e os metadados no quadro seguinte. Registrar tudo em `performance.md`, com o que ficou só no emulador.
- [ ] **Step 5: Tamanho e dependências:** `./gradlew :app:clean :app:assembleDebug` e `pwsh tools/check-apk-budget.ps1 -Apk app/build/outputs/apk/debug/app-debug.apk` (≤ 629.146 bytes sobre a base; anotar figuras, mapa e dicas separados) em `apk.md`; `./gradlew :app:dependencies --configuration debugRuntimeClasspath` contra a `main` em `deps-diff.txt`, vazio.
- [ ] **Step 6:** Commit `docs(report): phase 4A device suites, upgrade, offline, performance and size`.

### Task 23: Capturas e crítica de tela

**Files:**
- Create: `docs/superpowers/reports/AAAA-MM-DD-phase4a-critica-biblioteca.md` e as capturas citadas em `…-phase4a-verificacao/` (`library-{all,filtered,empty}-…`, `detail-{figure,nofigure}-…`, `form-…`, `picker-…`, nos dois temas, em en e pt-BR, e a 200% em pt-BR)
- Modify: o que a crítica pedir, nos arquivos do `:screens` e do `:designsystem`, com teste quando a correção for de comportamento

- [ ] **Step 1: Capturas** com `adb exec-out screencap -p`, nos dois temas, em en e pt-BR (`adb shell cmd locale set-app-locales com.mooscles --locales pt-BR`) e a 200% (`adb shell settings put system font_scale 2.0`), mais as do iOS do `LibraryFlowTest` (artefato do job `ios`).
- [ ] **Step 2: Crítica.** `critique-screen` (pelo `kit-design`) na biblioteca, no detalhe e no formulário. Corrigir as de prioridade alta e média, com nova captura; as baixas ficam no relatório com o motivo.
- [ ] **Step 3: Verificar.** As classes de `uiTest` das telas corrigidas verdes no emulador; `check-design-tokens` limpo.
- [ ] **Step 4:** Commit `docs(report): phase 4A library critique`, com as correções em commits próprios.

### Task 24: Documentação, relatório e revisão final

**Files:**
- Create: `docs/superpowers/reports/AAAA-MM-DD-phase4a-verificacao.md`
- Modify: `AGENTS.md` (Arquitetura: `illustration/` com o renderizador e o mapa no `:designsystem`; `ExerciseAnatomy`, `MuscleRegion`, `ExerciseNames`, `ExerciseAliases`, `SearchTerms` e `ExerciseSearchIndex` no `:shared`; `library/`, `exercise/` e `exercise/figures/` no `:screens`; a rota `exerciseInfo/{id}`; as dicas em `files/exercise_cues/`; as licenças MIT e CC0 e o `tools/illustrations/`; o guard de tokens cobrindo `illustration/`), `VERIFICATION.md`, `docs/superpowers/ROADMAP.md` (fase 4: a 4A com plano, relatórios e status; o G1 com a data em que a folha foi entregue e a resposta, se houver), a spec (seção "Notas de implementação", datada, com as decisões 3, 4, 5, 6, 11, 12, 16, 17 e 18 deste plano e as decisões do spike e da Task 8)

- [ ] **Step 1: Relatório.** **Provado:** testes de host e de `commonTest` com os números do CI; o `ios` verde com o `LibraryFlowTest` e as capturas (ou o registro de que `captureToImage` não funcionou); as suítes de aparelho (Task 22); o orçamento de toques contra `tap-baseline-4a.md`; a atualização por cima; o sem rede; o desempenho; o tamanho; o diff de dependências. **Não verificado:** tudo num iPhone (VoiceOver, a figura e o mapa no aparelho, desempenho); num Android físico (TalkBack, 120 Hz); a qualidade das figuras, das dicas e dos nomes para quem treina, além da crítica e da folha do G1; o #11 da fase 2 no campo de busca; a lacuna da decisão 11 (nome no descanso automático e na notificação); a release com a chave original.
- [ ] **Step 2:** Atualizar os documentos da lista.
- [ ] **Step 3: Verificar.** Os links do ROADMAP e dos relatórios abrem; o último run da branch está verde.
- [ ] **Step 4:** `compat-reviewer` na branch inteira (`git diff origin/main...HEAD`; esperado `SEGURO`) e `/code-review`. Commit `docs: phase 4A library verified`. **Push** e `gh run watch -R pedroyuuki/Mooscles`. O merge na `main` do fork fica com o controlador, depois do CI verde e do `/code-review` sem achado alto. **O merge não espera o G1.**

---

### Task L (condicional, só com o G1 aprovado): um lote de figuras

Uma execução por lote, na ordem da D4 (barra, halter, polia, peso corporal, máquinas, kettlebell, Smith; o `trap_bar_deadlift` entra com a barra), cerca de 50 exercícios cada. Cada lote nasce da `main` do fork (`feat/phase4-figures-<família>`), entra sozinho e pode correr durante a 4B. Não faz parte do merge da 4A.

**Files:**
- Modify ou Create: o arquivo da família em `screens/src/commonMain/kotlin/com/mooscles/screens/exercise/figures/` (`KettlebellFigures.kt`, `SmithFigures.kt` e `OtherFigures.kt` são novos), `ExerciseFigures.kt` (registro), `ExerciseFiguresTest.kt` (`familyIsComplete("<prefixo>")`; no último lote, `everyCatalogExerciseHasAFigure`), `PilotSheetCaptureTest.kt` (recebe o lote como argumento de instrumentação `-Pandroid.testInstrumentationRunnerArguments.lot=<família>`)
- Create: `docs/superpowers/reports/AAAA-MM-DD-phase4-figuras-<família>.md` com a folha do lote

- [ ] **Step 1:** Acrescentar `familyIsComplete("<prefixo>")` e ver falhar com `perl -e 'alarm shift; exec @ARGV' 900 ./gradlew :screens:testAndroidHostTest --tests "*ExerciseFiguresTest*"`.
- [ ] **Step 2:** Escrever as poses do lote, anotando as referências consultadas nas Notas deste plano.
- [ ] **Step 3:** O comando do Step 1 verde; medir o APK (a regra de tamanho da Task 8) e conferir o orçamento da etapa em curso.
- [ ] **Step 4:** Folha do lote (os dois temas, 64 dp e detalhe) e `critique-screen`; revisão de conteúdo (D21a): pose contra as dicas e a anatomia. Corrigir os altos.
- [ ] **Step 5:** Commit `screens: figures for <família>`, push da branch do lote, merge pelo controlador com o CI verde.

### Task R (condicional, só com o G1 reprovado): tirar o piloto do produto

**Files:**
- Delete: `screens/src/commonMain/kotlin/com/mooscles/screens/exercise/figures/` e os testes dele, `designsystem/.../illustration/FigureModel.kt`, `FigureGeometry.kt`, `ExerciseFigure.kt` e os testes; o uso de `ExerciseMedia.Figure` (a variante sai)
- Modify: `exerciseMedia` passa a devolver sempre `ExerciseMedia.None`; o detalhe mostra o placeholder do equipamento; `DeveloperGallery.kt` sem a página "Figuras"; o `media-CC0.md` e a entrada da licença sem "ilustrações"; `ci.yml` (desconta os testes apagados, com o motivo); a spec e o ROADMAP com a nota datada (a encomenda e a 4M vão para uma fase futura)

- [ ] **Step 1:** Remover e ajustar; rodar as classes de host e de aparelho que tocam a mídia (`ExerciseInfoScreenTest`, `LibraryScreenTest`, `ExercisePickerSheetTest`, `ExerciseCardTest`).
- [ ] **Step 2:** Commit `screens: remove the pilot figures after G1`, push, merge pelo controlador com o CI verde.

## Paralelismo

No máximo dois implementadores ao mesmo tempo, em worktrees separadas, e no máximo um deles usando o emulador.

| Par | Por que pode | Cuidado |
| --- | --- | --- |
| Task 1 ∥ Task 2 | branch do spike (DS, emulador) × `:shared` (host) | — |
| Task 3 ∥ Task 5 | `:shared` (host) × `:designsystem` (emulador) | os dois sobem `MIN_HOST_TESTS`: o controlador soma |
| Task 4 ∥ Task 6 | `:shared` e VM (host) × `:designsystem` (emulador) | a Task 4 só compila o `RecordingActions` |
| Task 7 ∥ Task 11 | `:designsystem` (emulador) × dicas (host) | — |
| Task 10 ∥ Task 12 | telas da fase 3 (emulador) × dicas (host) | — |
| Task 14 ∥ Task 13 | modelo (host) × dicas (host) | os dois rodam só host; mínimos em linhas diferentes |
| Task 15, 16, 17 ∥ Tasks 12, 13 (o que sobrar) | telas (emulador) × dicas (host) | — |

Todo o resto é sequencial: a Task 6 precisa da 1; a 8 precisa da 2 e da 5; a 9 da 8; a 7 vem depois da 5 (os dois mexem em `MTheme` e tokens); as Tasks 15–18 usam o emulador e se encadeiam por interfaces (15 → 16 → 17; 18 depois da 15); a 19 precisa da 15–18; a 20–24 precisam de tudo. A Task 22 roda sozinha (suítes completas).

## Pré-voo: arquivos e interfaces compartilhados

| Tasks | O que compartilham | Nomes e assinaturas batem? |
| --- | --- | --- |
| 1 → 6 | fonte, commit, tabela de agrupamento, forma A ou B, pescoço, par do secundário | sim: a 6 só consome a decisão |
| 2 → 8, 10, 17 | `MuscleRegion` (17), `Anatomy(primary, secondary)`, `ExerciseAnatomy.forExercise(id, muscleLabel)` | sim |
| 3 → 10, 14, 16 | `ExerciseNames.display(id, storedName, custom, portuguese)`, `ExerciseSearchIndex.build/matching`, `SearchEntry(id, name, muscle, equipment)` | sim |
| 4 → 16 | `MoosclesActions.createCustomExercise(id, name, muscle, equipment, convention)`, formato `"createCustomExercise:<id>,<name>,<muscle>,<equipment>,<convention>"` | sim |
| 5 → 8, 15, 17 | `FigurePose`, `FigureProp`, `FigureSegment`, `FigureKinematics.solve`, `ExerciseMedia.Figure(pose, highlights)`, `ExerciseThumbnail(media, equipmentIcon, size, modifier)`, `AnimatedExerciseFigure(start, end, highlights, exerciseName, modifier, foreground)`, `GalleryExtraPage(key, title, content)` | sim |
| 5 → 8 | `FigureSkeleton` e `MoosclesIllustration` (a 8 fixa os valores finais e atualiza os testes da 5 no mesmo commit) | sim |
| 6 → 17 | `BodyRegion` (os nomes de `MuscleRegion`), `BodyMap(primary, secondary, modifier)`, `BodyMapLegend()` | sim: a 17 converte por `MuscleRegion.toBodyRegion()`, coberto pelo `BodyRegionBridgeTest` |
| 7 → 15–18 | `MSearchField(value, onValueChange, placeholder, onClear, modifier, focusRequester)`, `MChipKind.Menu`, `MInfoChip(label, modifier, icon)`, `sizes.mediaList`, `sizes.mediaDetailMax`, `MIcons.Settings`, `MIcons.SearchX` | sim |
| 8 → 15, 17, 18 | `exerciseMedia(exercise)`, `ExerciseFigures.forExercise(id)`, `FigureHighlights.forRegions(primary)`, `figuresGalleryPage()` | sim |
| 10 → 14–18 | `catalogInPortuguese()`, `exerciseName(exercise)`, `folderName(folder)`, `regionName(region)`, `anatomySentence(anatomy)` | sim |
| 11 → 17 | `rememberExerciseCues(exerciseId)` | sim |
| 14 → 15, 18 | `LibraryModel.build/shownName/results/recents`, `LibraryFilters(muscles, equipment, mineOnly)`, `rememberLibraryModel(exercises)` | sim |
| 15 → 16, 18 | `LibraryRoute(state, actions, onOpenExercise, onOpenSettings, modifier)`, `MuscleFilterRow`, `EquipmentFilterChip`, `EquipmentFilterSheet` | sim |
| 16 → 17 | `CustomExerciseForm(initial, prefillName, state, actions, onSaved, onCancel)` | sim |
| 17 → 19 | `ExerciseInfoRoute(exerciseId, state, actions, onBack, onOpenHistory, onOpenRoutine, onOpenWorkout, modifier, foreground)` | sim |
| 18 → 19 | `ActiveWorkoutRoute(…, onExerciseInfo)` | sim |
| 3 ∥ 5, 4 ∥ 6, 13 ∥ 14 | `.github/workflows/ci.yml` (mínimos) | conflito de uma linha, resolvido somando |

## Notas

- **Referências visuais consultadas (D3).** Quem escreve uma pose anota aqui cada consulta. Só se lê a posição; nada é decalcado, sobreposto nem copiado.

  | `stableId` | Referência (link) | O que se leu |
  | --- | --- | --- |
  | os 20 do piloto (Task 8, 2026-10-30) | nenhuma: nenhum acervo (Everkinetic, Workout Guide, free-exercise-db) foi aberto | as posições foram escritas a partir de conhecimento geral de execução e das dicas do próprio app (`files/exercise_cues/en.json`: início e fim de cada movimento), e conferidas só nas capturas do nosso renderizador |
  | `barbell_back_squat_high_bar`, `bodyweight_pull_up`, `plate_leg_press_45`, `dumbbell_raise_lateral` (Task 9, 2026-10-30) | nenhuma | ajustes da crítica da folha: cotovelo para trás no agachamento, barra fixa curta e cotovelo à frente no fim da barra, tornozelo do leg press dentro de uns 20° de dorsiflexão (o trilho de 45° deixa de passar pelo quadril), braços e pernas abertos no início da elevação lateral; ângulos por cinemática direta, conferidos nas capturas |
  | os 42 do lote da barra (Task L, 2026-10-31): `barbell_*` e `ez_bar_*` fora do piloto e `trap_bar_deadlift` | nenhuma: nenhum acervo nem imagem foi aberto | posições escritas a partir de conhecimento geral de execução e das dicas do próprio app (`files/exercise_cues/en.json`), com a anatomia da `ExerciseAnatomyTable` para o destaque; conferidas só nas capturas do nosso renderizador (uma prévia local do mesmo cálculo e a folha do emulador). Nenhum asset gerado por IA. Folha: [relatório](../reports/2026-10-31-phase4-figuras-barra.md) |
  | os 51 do lote do halter (Task L, 2026-10-31): `dumbbell_*` fora do piloto | nenhuma: nenhum acervo nem imagem foi aberto | posições escritas a partir de conhecimento geral de execução e das dicas do próprio app (`files/exercise_cues/en.json`), com a anatomia da `ExerciseAnatomyTable` para o destaque; conferidas só nas capturas do nosso renderizador (uma prévia local do mesmo cálculo e a folha do emulador). Nenhum asset gerado por IA. Folha: [relatório](../reports/2026-10-31-phase4-figuras-halter.md) |
  | os 42 do lote da polia (Task L, 2026-10-31): `cable_*` fora do piloto, menos `cable_pallof_press` (sem figura, `NO_FIGURE`) | nenhuma: nenhum acervo nem imagem foi aberto | posições escritas a partir de conhecimento geral de execução e das dicas do próprio app (`files/exercise_cues/en.json`), com a anatomia da `ExerciseAnatomyTable` para o destaque e a polia na altura que a dica pede; conferidas só nas capturas do nosso renderizador (uma prévia local do mesmo cálculo e a folha do emulador). Nenhum asset gerado por IA. Folha: [relatório](../reports/2026-10-31-phase4-figuras-polia.md) |
  | os 44 do lote do peso corporal (Task L, 2026-10-31): `bodyweight_*` fora do piloto | nenhuma: nenhum acervo nem imagem foi aberto | posições escritas a partir de conhecimento geral de execução e das dicas do próprio app (`files/exercise_cues/en.json`), com a anatomia da `ExerciseAnatomyTable` para o destaque; corpo em linha, mãos e pés parados entre os quadros, calculados pela cinemática; conferidas só nas capturas do nosso renderizador (uma prévia local do mesmo cálculo e a folha do emulador). Nenhum asset gerado por IA. Folha: [relatório](../reports/2026-10-31-phase4-figuras-peso-corporal.md) |
  | os 49 do lote das máquinas (Task L, 2026-10-31): `selectorized_*` e `plate_*` fora do piloto, menos `selectorized_torso_rotation` (sem figura, `NO_FIGURE`) | nenhuma: nenhum acervo nem imagem foi aberto | posições escritas a partir de conhecimento geral de execução e das dicas do próprio app (`files/exercise_cues/en.json`), com a anatomia da `ExerciseAnatomyTable` para o destaque; a máquina desenhada pelos pontos de contato (assento, encosto, rolos, plataforma, pegador), com o assento e os rolos onde a dica manda; conferidas só nas capturas do nosso renderizador (uma prévia local do mesmo cálculo e a folha do emulador). Nenhum asset gerado por IA. Folha: [relatório](../reports/2026-10-31-phase4-figuras-maquinas.md) |

- **Nota (2026-10-30, Task 9): tronco mais grosso e vãos no desenho da figura.** A crítica da folha do piloto achou ilegível a 64 dp a figura em pé (um poste com um anel), o braço junto do corpo (some dentro do tronco) e a cabeça sob a anilha. O renderizador passou a desenhar o tronco com um traço próprio, `figureTrunkStroke` (0,09 da caixa, uma vez e meia o dos membros), e a abrir um vão (`figureGap`, 0,012 de cada lado) sob a cabeça e sob o braço da frente (os dois braços na vista de frente), para que leiam na frente do que está atrás. O vão limpa até o fundo com `BlendMode.Clear` dentro de uma camada própria da figura (`CompositingStrategy.Offscreen`): o fundo continua transparente, como pede a D3, e a figura funciona sobre qualquer superfície. Essa camada é um alvo de composição do próprio Compose, refeito a cada desenho, e não um bitmap guardado; a regra "sem bitmap intermediário" da D5 (geometria em `drawWithCache`, nada rasterizado em cache) continua valendo. Os dois tokens estão fixados no `IllustrationTokensTest`, e a ordem e as larguras no `FigureHighlightDrawingTest`. Consequência conhecida: o tronco deitado no banco encosta 1,5 unidade a mais na linha do banco (o `CONTACT_GAP` ainda mede pelo traço dos membros).

- **Nota (2026-10-30, Task 8): a elevação lateral mantém a vista de frente.** Exceção à decisão 6 ("a figura de frente para a direita"): uma vista de lado não mostra a abdução dos braços. `dumbbell_raise_lateral` é desenhada de frente, com os dois braços, e o resto do piloto segue a decisão. Custo: uma figura com ponto de vista diferente.
- **Nota (2026-10-30, Task 8): token `farLimb` (`MoosclesFigureColors`) e ordem de desenho.** O lado de lá do corpo tinha o mesmo tom do equipamento (achado S1 da revisão). Entrou o token de cor da figura `farLimb`, fixado no `IllustrationTokensTest` com contraste, e o equipamento passou a ser desenhado antes dos membros de lá. Os valores iniciais da Task 5 (esqueleto menor, anilha 8, halter 4, pernas do banco 16, faixa do glúteo em toda a largura) mudaram na Task 8 e estão fixados no teste.
- **Nota (2026-10-30, Task 11): `ExerciseCues.load(portuguese)`.** A assinatura não recebe a função de leitura (decisão 20 dizia que recebe); os testes usam o próprio `CueCache(read)`, pela regra F18. O leitor de produção lê com `Res.readBytes`, uma vez por idioma e por processo, com um só `Mutex` para os dois.
- **Nota (2026-10-30, Task 7): três desvios do `MSearchField`.** O placeholder continua sendo o nome acessível com o campo preenchido; o token `infoChipHeight` é 32 dp; o `MInfoChip` não tem borda; e não há parâmetro `focusRequester` (o `Modifier.focusRequester` funciona). Task 15 acrescentou o espaçamento de 8 dp entre o campo e o botão de limpar, com asserção.
- **Nota (2026-10-30, Tasks 15 a 18): textos e comportamento além do previsto.** Os filtros da biblioteca rolam com a lista e uma busca nova leva ao topo; o seletor ganhou "Limpar filtros"; o formulário ganhou `exercise_form_muscle_required`, `exercise_form_equipment_required` e `exercise_form_required_hint`; o detalhe ganhou `exercise_history_last_label`, `exercise_history_best_label` e `exercise_not_found_body`; o seletor ordena "Todos" pelo nome mostrado; o nome do detalhe saiu da barra e é a primeira linha do conteúdo (Task 17). Detalhes e o motivo na [spec](../specs/2026-10-29-phase4-library-routines-design.md#notas-de-implementação).
- **Nota (2026-10-30, Tasks 20 e 22): "supino articulado" e o segundo quadro.** O fluxo de ponta a ponta usa o filtro "Costas" ligado para chegar à busca sem resultado, porque "supino articulado" acha as máquinas de supino em pt-BR sem filtro. Os orçamentos de "o próximo quadro" são cumpridos como "o segundo quadro" (a casca aplica toda rota um quadro depois do toque).
- **Nota (2026-10-30, Task 21): `MOverlayWindow` é público.** As janelas de diálogo (`MDialog`, `MBottomSheet`, o formulário e o timer expandido) passam a herdar a densidade e a escala de fonte do host por `MOverlayWindow`, no `:designsystem`; antes um "2×" de teste rodava a 1× nas janelas. O `MIN_DS_IOS_TESTS` foi de 337 para 339 e o `MIN_SCREENS_IOS_TESTS` de 409 para 427.
- **Nota (2026-10-30, Task 23): a aba selecionada sobrevive à recriação.** A casca (`:app`) voltava para Treinar depois de uma recriação numa tela acima da raiz de outra aba (detalhe aberto pela Biblioteca, detalhe do Histórico); `a39400e` corrige, com teste. O mapa tem a legenda e o texto equivalente; a CC0 mostra os itens um por linha (`7b287a6`).
- **Nota (2026-10-30, Task 24): contadores finais do CI.** `MIN_HOST_TESTS` 928, `MIN_IOS_TESTS` 257 (os 6 testes de entrada do `:shared` seguem), `MIN_DS_IOS_TESTS` 340 e `MIN_SCREENS_IOS_TESTS` 430. O último run verde medido é o de `5ed5fa7` (926, 257, 337 e 407); os commits seguintes são provados no push do checkpoint 3.
- **Nota (2026-10-30, Task 24): ids antigos de fixtures.** Os ids de fixtures de teste sem o prefixo `catalog_` aparecem em inglês e com o ícone; não vale para uma instalação real.

- **Nota (2026-10-31, Task L, lote da barra): 42 figuras, uma pergunta.** A família da barra (barra reta, EZ e `trap_bar_deadlift`) está completa, com 49 figuras; `ExerciseFiguresTest.barbellFamilyIsComplete` a fixa, e o `PilotSheetCaptureTest` recebe o lote (`-e lot barbell`). Sem primitiva nova: a barra do landmine é a primitiva do cabo (do pivô no chão às mãos), o banco da seal e o degrau da panturrilha são caixas. Achado alto não corrigido: o encolhimento tem os dois quadros quase iguais, porque o esqueleto não tem elevação de ombro; corrigir pede um campo novo na `FigurePose`, que mexe na regra de proporções fixas da D3, então fica com o controlador. APK: +16.384 bytes para as 84 poses. [Relatório](../reports/2026-10-31-phase4-figuras-barra.md).

- **Nota (2026-10-31, Task L, lote do halter): 51 figuras, a mesma pergunta.** A família do halter está completa, com 56 figuras; `ExerciseFiguresTest.dumbbellFamilyIsComplete` a fixa, e a folha sai com `-e lot dumbbell`. Sem primitiva nova: a elevação lateral sentado, o afundo lateral e a flexão lateral de tronco usam a vista de frente da elevação lateral do piloto; o banco inclinado de bruços é o assento inclinado sobre um banco reto; o poste da panturrilha unilateral é uma caixa estreita. Achado alto não corrigido: o encolhimento com halteres repete o A1 do lote da barra (sem elevação de ombro no esqueleto), que o controlador aceitou por ora; é o segundo exercício escapular com esse limite. Os crucifixos e os crucifixos inversos ficam como achado médio: o braço que abre para o lado vai na direção de quem olha, e o esqueleto não encurta osso. APK: +16.384 bytes para as 102 poses (ficam 280.979 bytes do orçamento da fase 4). [Relatório](../reports/2026-10-31-phase4-figuras-halter.md).

- **Nota (2026-10-31, Task L, lote da polia): 42 figuras e uma exclusão.** A família da polia tem 45 figuras; `ExerciseFiguresTest.cableFamilyIsComplete` a fixa, e a folha sai com `-e lot cable`. Sem primitiva nova: o cabo vai da polia (alta, média ou baixa, como a dica pede, parada entre os quadros) à mão ou à correia do tornozelo; a coluna da máquina, quando a mão livre a segura, é uma caixa estreita com a polia no pé; oito exercícios do plano frontal usam a vista de frente. O achado alto A2 (o Pallof press lia como o supino em pé, porque nenhuma vista mostra a puxada lateral) foi resolvido pelo controlador tirando a figura: `cable_pallof_press` fica em `ExerciseFigures.NO_FIGURE`, com o motivo, e mostra o ícone do equipamento; o `everyCatalogExerciseHasAFigure` do último lote usa a mesma lista, e `noFigureIdsAreCatalogExercisesWithoutAFigure` impede que ela envelheça. Nenhum encolhimento na família (o A1 não volta). APK: 0 bytes (o lote coube na folga do empacotador), +8.663 bytes de dex comprimido medidos com o Pallof; ficam 280.979 bytes pelo script, 272.316 contando o dex. [Relatório](../reports/2026-10-31-phase4-figuras-polia.md).

- **Nota (2026-10-31, Task L, lote do peso corporal): 44 figuras, nenhuma exclusão.** A família do peso corporal está completa, com 46 figuras; `ExerciseFiguresTest.bodyweightFamilyIsComplete` a fixa, e a folha sai com `-e lot bodyweight`. Sem primitiva nova: a barra fixa e a da remada invertida são linhas curtas nas mãos, as paralelas uma linha comprida ao longo do corpo, a parede, o poste e o encosto da cadeira romana caixas estreitas, e as máquinas da hiperextensão e do glute-ham montadas com assento, rolos, plataforma e banco; deitado de costas a cabeça fica à esquerda, para a frente da figura olhar para cima; só o afundo lateral usa a vista de frente. Nenhum achado alto ficou aberto e nenhum exercício entrou em `NO_FIGURE`; a flexão arqueiro fica como achado médio (o braço esticado para o lado aparece indo para a frente), e o braço ao lado do corpo deitado cobre o destaque do abdômen na elevação de pernas deitado (médio, a dica manda as mãos ali). APK: +16.384 bytes (+8.656 de dex comprimido); ficam 264.595 bytes pelo script, 263.660 na conta conservadora. [Relatório](../reports/2026-10-31-phase4-figuras-peso-corporal.md).

- **Nota (2026-10-31, Task L, lote das máquinas): 49 figuras e uma exclusão.** A família das máquinas (de placas e de anilhas; o Smith fica para o seu lote) tem 52 figuras; `ExerciseFiguresTest.machineFamilyIsComplete` a fixa, e a folha sai com `-e lot machine`. Sem primitiva nova: a máquina é desenhada pelos pontos de contato, como no piloto (assento, encosto ou apoio do peito, rolos que seguem o membro, plataforma e um pegador curto atravessado na mão), sem pilha de placas nem braço de alavanca, que se desenharia como um cabo; as exceções são a remada cavalinho, o agachamento articulado e a panturrilha em pé articulada, com a alavanca do pivô no chão como o landmine do lote da barra, e o belt squat, com a corrente do cinto. O hack e o pêndulo descem ao longo de um trilho fixo paralelo às costas; a elevação lateral, a abdutora e a adutora usam a vista de frente. Achado alto A1: a rotação de tronco na máquina não se desenha em nenhuma vista (o tronco gira em torno do próprio eixo, e os dois quadros seriam iguais), então `selectorized_torso_rotation` entrou em `ExerciseFigures.NO_FIGURE`, com o motivo, pela regra do Pallof; fica para o controlador confirmar. APK: 0 bytes (o lote coube na folga do empacotador), +10.577 bytes de dex comprimido; ficam 264.595 bytes pelo script, 253.083 na conta conservadora. [Relatório](../reports/2026-10-31-phase4-figuras-maquinas.md).

- **G1.** Folha entregue ao dono em 2026-10-30 ([folha](../reports/2026-10-30-phase4a-folha-piloto.md)). Resposta e data da resposta: pendentes (preenchido pelo controlador).
