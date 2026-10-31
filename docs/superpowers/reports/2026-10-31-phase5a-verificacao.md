# Fase 5A: verificação dos gráficos, do progresso e dos Recordes

- **Data:** 2026-10-31
- **Branch:** `feat/phase5a-progress`, a partir da `main` do fork em `e6ada6a`. Último commit de código verificado por inteiro: `096ec3c` (Push 2, CI verde). Depois dele vieram, sem CI ainda, as Tasks 16 (rodada 2) e 17: `8bddc89`, `d704237`, `17484d6`, `35cd292` e `4d5c529`; o `dbe4d04` é só o relatório da crítica. Este relatório e os documentos vêm no commit `docs: phase 5A charts, progress and records verified`
- **Spec:** [fase 5](../specs/2026-10-31-phase5-progress-history-design.md), com as [notas de implementação](../specs/2026-10-31-phase5-progress-history-design.md#notas-de-implementação) · **Plano:** [fase 5A](../plans/2026-10-31-phase5a-charts-progress.md) · **Spike:** [S1, fundação dos gráficos](2026-10-31-phase5a-spike-graficos.md) · **Crítica:** [crítica de tela](2026-10-31-phase5a-critica.md)
- **Evidências:** [2026-10-31-phase5a-verificacao/](2026-10-31-phase5a-verificacao/): [suítes de aparelho](2026-10-31-phase5a-verificacao/device-suites.md), [atualização por cima](2026-10-31-phase5a-verificacao/upgrade.md), [sem rede](2026-10-31-phase5a-verificacao/offline.md), [desempenho](2026-10-31-phase5a-verificacao/performance.md), [tamanho do APK](2026-10-31-phase5a-verificacao/apk.md), [diff de dependências](2026-10-31-phase5a-verificacao/deps-diff.txt) (vazio) e [toques de hoje](2026-10-31-phase5a-verificacao/tap-baseline-5a.md)

## Resumo

- **Entregue:**
  - Gráficos próprios no `:designsystem` (`charts/`: `MLineChart`, `MBarChart`, `MSparkline`, a tabela "Ver dados"), sem biblioteca, com a página "Gráficos" na galeria.
  - A rota de progresso do exercício (`exercise/{id}?metric=`), com métricas por convenção (Carga, 1RM estimado, Volume, Repetições, Peso adicional, Assistência), períodos, recordes e sessões do exercício, e a tabela de dados.
  - A aba Recordes nova: linha do tempo por mês, "Só atuais", "Primeiros registros", busca e a folha "Como contamos recordes".
  - A sparkline e o botão "Ver progresso" no card "Seu histórico" do detalhe.
  - Um `ProgressIndex` único, construído fora da thread principal e compartilhado (`ProgressIndexCache`, aquecido na abertura). As telas antigas de Recordes e de histórico do exercício saíram.
- **Dados:** nenhuma mudança. Room continua na **v6**, sem migração nova; nenhuma chave de DataStore mudou; `versionCode` 16 e `versionName` 1.4.5 inalterados. O `git diff --stat main...HEAD` dos caminhos de compatibilidade (`data/` e `timer/` do `:shared` e do `:app`, `shared/src/androidMain`, `updates/`, os dois `AppPreferences.kt`, os dois `build.gradle.kts` e `shared/schemas`) está **vazio**, por isso o `compat-reviewer` não foi acionado.
- **Spike S1 aprovado** (Android: a, b e d; iOS no Push 1; c parcial, fechado na Task 16). Os gráficos próprios seguem e o Vico não entra.
- **APK debug: +284.839 bytes** sobre a base do CI da fase 5. Dentro do limite da fase (524.288 para 5A e 5B juntas), mas **22.695 bytes acima da meta só da 5A** (262.144). A 5B fica com 239.449.
- **Crítica de tela (Task 17):** 1 achado alto (a semana começava na segunda no app de verdade) e 7 médios, todos corrigidos; 10 baixos com o motivo escrito.
- **Não provado:** nada num iPhone nem num Android físico; o desempenho só no emulador; a rolagem de controle da Biblioteca deu mais jank que na 4A (sem causa achada na 5A). A lista completa está [no fim](#o-que-não-foi-verificado).

## Ambiente

- Emulador `Mooscles_Pixel8_API35` (Android 15, 1080 × 2400, `emulator-5558`), num Mac M4 sem Xcode. O iOS só se prova no job `ios` do CI (`macos-26`, Xcode 26.4). Nenhum aparelho físico.
- Os APKs locais são assinados pela chave debug do Mac, que não é a das releases. A atualização por cima prova a compatibilidade dos dados entre dois builds, não o caminho do APK publicado.
- O desempenho correu com o swap do Mac quase cheio (17,5 a 18,3 GB usados). Uma primeira rodada, contaminada por um emulador zumbi a 270% de CPU, foi descartada onde a limpa existe (ver [desempenho](2026-10-31-phase5a-verificacao/performance.md)).
- Tudo o que está marcado "emulador" abaixo foi visto num emulador, nunca num aparelho físico.

## O que foi provado

### Testes

| Conjunto | Onde | Resultado |
| --- | --- | --- |
| Host dos quatro módulos | CI, run [41232962795](https://github.com/pedroyuuki/Mooscles/actions/runs/41232962795), `096ec3c` (Push 2) | **1258** testes, 0 falhas |
| `:shared`, `:designsystem` e `:screens` no simulador iOS | o mesmo run | **280**, **405** e **877**, 0 falhas |
| Os mesmos conjuntos no Push 1 | run [41215063507](https://github.com/pedroyuuki/Mooscles/actions/runs/41215063507), `fe6cf43` | host 1212, iOS 280, 404 e 747, tudo verde |
| Mínimos do `ci.yml` em `dbe4d04` | `.github/workflows/ci.yml` | host **1260**, `:shared` iOS 280, `:designsystem` iOS **411**, `:screens` iOS **886**. **Esses mínimos ainda não passaram por um run:** os commits depois de `096ec3c` não foram ao CI. A [rodada final de correções](#rodada-final-de-correções-nota-de-2026-10-31) os subiu para 1264, 414 e 892 |
| `:designsystem:connectedAndroidDeviceTest` | emulador, `a0eab5f` | **405** testes (67 classes), 0 falhas |
| `:screens:connectedAndroidDeviceTest` | emulador, `a0eab5f` | **881** testes (82 classes), 0 falhas, 1 pulado (`PilotSheetCaptureTest`, opt-in) |
| `:app:connectedDebugAndroidTest` | emulador, `a0eab5f` | 25 testes (9 classes); 1 falha de teste (`UiFlowTest`, dependia do descanso automático ligado nos dados que já estavam no emulador), corrigida em `3b07456` e provada em 1 execução |
| Host depois da Task 17 | local, `4d5c529` | `:designsystem:testAndroidHostTest` 118/118 e `:screens:testAndroidHostTest` 292/292, com `--rerun-tasks` |

Para comparar, na 4B as suítes de aparelho tinham 373, 640 e 25 testes. Depois de `a0eab5f`, o aparelho rodou **só as classes tocadas** (a rodada 2 da Task 16 e a Task 17, pelo gate): as suítes inteiras de aparelho não foram refeitas depois do `8bddc89` nem do `d704237` ao `4d5c529`. A suíte inteira do `:app` no host também não foi rodada na Task 17; o CI faz isso no próximo push.

O que cobre as exigências da spec e do plano:

- **Domínio e consultas (host e `commonTest`):** `ProgressIndexTest` (30), `ExerciseChartsTest` (20), `RecordTimelinesTest` (9), `TrendTest`, `WeekStartTest` (com a equivalência contra o `WeekFields` da JVM para todas as localidades), `ProgressIndexCacheTest` e o `ProgressIndexDomainHostTest` no `:app` (o `ProgressIndex` contra o domínio de cálculos do `WorkoutUi`, 2 testes).
- **Gráficos:** `ChartScaleTest`, `ChartGeometryTest`, o contador de construções do `Path` no `ChartsTest`, e a semântica (um nó de imagem com o resumo, a tabela, o selo "Recorde" na linha).
- **Telas e fluxo:** `ProgressFlowTest` (2 testes, sobre o `MoosclesViewModel`, o Room e o DataStore reais, com capturas), `RecordsScreenTest`, `ExerciseProgressScreenTest`, `ExerciseProgressSectionsTest`, `ProgressTextsTest`, `ProgressAccessibilitySweepTest` (15 testes) e `TapBudgetTest`.
- **Casca e hospedagem (`:app`):** `ProgressNavigationHostTest` (a rota com e sem `metric`, o prewarm por identidade e uma construção do índice por histórico novo, kg para lb também).
- **iOS:** `ChartsTest`, `ProgressFlowTest` e o varrimento de acessibilidade rodaram no simulador no CI do Push 2 (877 testes do `:screens`, 405 do `:designsystem`).

### Spike S1: fundação dos gráficos

Aprovado: Android (emulador) nos critérios a, b e d, iOS (Push 1, run 41215063507) com `ChartsTest` verde e capturas sem texto cortado nem sobreposto, e o critério c fechado na Task 16. O Vico (Task 1R) não foi necessário. Detalhes no [relatório do spike](2026-10-31-phase5a-spike-graficos.md):

- a primeira rodada de aparelho teve 10/11, e a falha era do produto (o toque entre duas composições era descartado); corrigida com o `SelectionTracker` e provada (`ChartsTest` 11/11, depois 12 no gate da Task 9);
- os componentes de gráfico somam 58.084 bytes comprimidos no dex, abaixo do limite de 60 KiB (61.440). A página da galeria e os ícones contam no orçamento da fase, não no S1 (decisão do controlador);
- 300 pontos: primeiro desenho em 6,2 ms (build debug), e o `Path` é refeito uma vez enquanto a seleção se move.

### Atualização por cima

`main` em `596c5bd` (banco v6) para a branch, no emulador, com a rede desligada, `versionCode` 16 nos dois e o treino ativo com o descanso correndo: `PRAGMA user_version` 6 antes e depois, schema e `identity_hash` iguais, o `local_preferences.preferences_pb` com o mesmo MD5, o alarme do descanso com o mesmo horário, os mesmos 208 treinos e 12.688 séries concluídas, e o export depois igual ao de antes tirando o `exportedAt`. Os recordes (480 eventos, 246 atuais), contados pelo `UiState` num teste descartável sobre o banco puxado, deram os mesmos números no código da `main` e no da branch; o volume de três treinos conferiu com o `sqlite3` e com a tela. Os dois APKs têm a chave debug do Mac. [`upgrade.md`](2026-10-31-phase5a-verificacao/upgrade.md).

### Sem rede

Instalação nova com a rede desligada e a base grande importada: a aba Recordes, a busca, os chips, o progresso em cada métrica e período, "Ver dados" e o card com a sparkline funcionaram, sem `FATAL EXCEPTION`. O diff da branch não traz código de rede (a única palavra que casa com a busca é "curl", num nome de exercício de teste) e nenhum manifesto mudou. [`offline.md`](2026-10-31-phase5a-verificacao/offline.md).

### Desempenho (só emulador)

Resumo contra os orçamentos da spec, pela rodada limpa quando há duas ([`performance.md`](2026-10-31-phase5a-verificacao/performance.md)):

| Medida | Orçamento | Resultado |
| --- | --- | --- |
| Construir o índice, base grande, **build R8** (seção `Mooscles:progressIndex`) | mediana ≤ 60 ms, p95 ≤ 120 ms | 12 construções: mediana **3,9 ms**; nas 9 trocas kg/lb, p95 70,4 ms e um máximo de 129,9 ms na primeira depois da abertura |
| O mesmo, APK de teste depurável (`ProgressIndexDeviceTimingTest`) | o mesmo; o teste confere 4 × | mediana 202, **37,5** e **31,9** ms em 3 execuções limpas (a 1ª oscilou com o swap cheio) |
| Consulta de gráfico, de linha do tempo e teclas da busca, depurável | mediana ≤ 4 ms, p95 ≤ 8 ms | p95 de 1,4 a 1,6, de 2,4 a 2,6 e de 2,1 a 2,3 ms. Esses três **não foram medidos no R8** |
| Aba Recordes e recorde → gráfico | 2º quadro; gráfico em até 2 quadros | aba no 2º quadro, sem construção do índice; o gráfico entra no 2º quadro **da rota**, que é o 3º contando do toque, como em toda rota da casca (aceito pelo controlador) |
| Rolagem da linha do tempo com "Só atuais" | janky ≤ 5% | 3,21% a 3,70% (mediana 3,33%) |
| Rolagem com o gráfico na tela (S1 c) | sem orçamento próprio | 4,03% a 5,40% (mediana 4,46%) contra 3,44% e 4,55% no controle do mesmo gesto |
| Trocar kg/lb | o índice refeito uma vez | 9 trocas, 9 construções |

O que isso **não** diz, e que o controlador decidiu registrar:

- **A rolagem de controle da Biblioteca deu 1,75% a 2,30%, contra cerca de 1% (0,50% a 1,47%, mediana 0,61%) na 4A.** Isso **não é atribuível à 5A**: o diff da pasta `library/` do `:screens` com a `main` está vazio, e o que a 5A acrescentou ao `:designsystem` não aparece numa linha da biblioteca. O gesto de controle teve de sair do campo de busca (mais curto) e o swap estava quase cheio. É uma suposição compatível, não uma medida: **pede uma nova checagem no build preview do S23**.
- **O gráfico não foi medido de novo depois da `graphicsLayer` da Task 17** (`17484d6`). A camada só evita regravar o desenho, e o contador de construções do `ChartsTest` continua igual, mas isso não é uma medida de quadros.
- O p95 do `present()` completo da base grande (pendência da 4B) foi registrado, não aprovado: 77,9 a 93,7 ms na abertura a frio e 27,7 a 478,4 ms (mediana 46,8) na troca kg/lb.

### Orçamento de toques

A contagem de hoje foi feita nas telas antigas, antes da troca ([`tap-baseline-5a.md`](2026-10-31-phase5a-verificacao/tap-baseline-5a.md)), e o `TapBudgetTest` reprova acima do orçamento sobre o `MoosclesViewModel` real.

| Fluxo | Hoje (medido) | Orçamento do `TapBudgetTest` |
| --- | ---: | ---: |
| Ver o recorde atual de um exercício (`seeTheCurrentRecordOfAnExercise`) | 2 (aba, foco da busca) | 2 (aba, foco da busca) |
| Do recorde ao progresso do exercício | 2 (aba, card) | 2 (aba, linha) |
| Ver o 1RM estimado do supino | não existe | 3 (aba, linha, chip) |

**Desvio da spec, por decisão do controlador:** a tabela da spec escrevia "1 + digitar" para o primeiro fluxo, mas o foco no campo de busca conta como toque, a mesma regra com que a linha de base foi medida. O orçamento é 2, não "1 + digitação" (ver a nota datada na spec). Os fluxos do Histórico (5B) não entram nesta fase.

### Tamanho e dependências

APK debug de 24.754.047 bytes, **+284.839** sobre a base (`e6ada6a`, 24.469.208, artefato do CI), medido em `3b07456`.

- **Dentro do limite da fase** (524.288, 5A e 5B juntas); sobram **239.449** para a 5B.
- **22.695 bytes acima da meta só da 5A** (262.144). Foi o controlador quem aceitou: o limite que vale é o da fase. O risco é a 5B ter de enxugar.
- Fontes (645.546 de 921.600) e fontes e ícones (938.769 de 1.048.576) dentro do orçamento.
- O dex cresceu 283.241 comprimidos: `screens.progress` +136.851, `designsystem.charts` +51.980, `screens.records` +44.288, strings geradas +43.637; as telas antigas que saíram na Task 13 devolveram 29.561.
- O `app-performance.apk` (R8) tem 3.446.765 bytes (+120.808 sobre a 4B).
- As saídas de `dependencies` da branch e da `main` têm o mesmo diff: **vazio** (0 bytes). Nenhuma dependência nova.
- O número foi medido antes da Task 17, e **não foi refeito depois dela**; o do CI do próximo push é o que vale. O APK foi montado aqui, com a chave debug local.

[`apk.md`](2026-10-31-phase5a-verificacao/apk.md).

### Crítica de tela (Task 17)

Relatório completo: [crítica](2026-10-31-phase5a-critica.md), com 85 capturas de antes e as `after-*` de depois em [`2026-10-31-phase5a-verificacao/`](2026-10-31-phase5a-verificacao/). Feita só no emulador, em claro e escuro, inglês e português, 1× e 200%.

- **Alta, corrigida (A1):** no app de verdade, com o idioma do app em `en`, a semana começava na segunda; a correção da Task 16 lia a região no `Resources.getSystem()`, que também recebe o idioma por app. Agora é `LocaleManager.getSystemLocales()` (Android 13 ou mais novo). O teste novo, `CurrentWeekStartDeviceTest.aPerAppLanguageWithoutRegionKeepsTheSystemRegion`, com o idioma por app real, falhou antes ("expected:<US> but was:<null>") e passou depois. É o único teste novo que se provou vermelho no código antigo; os outros conferem textos que o código antigo não produzia, mas não foram rodados nele.
- **Médias, corrigidas (M1 a M7):** datas de mais de um ano sem o ano (M1) e quebradas entre dia e mês a 200% (M2); a mesma medida com três nomes na barra fixa (M3); recordes de assistência lidos como carga (M4); "12 repetições Repetições" (M5); anéis de recorde empilhados em gráfico denso (M6); leituras de peso erradas para o leitor de tela, como "1 quilos" (M7).
- **Baixas (L1 a L10):** documentadas com o motivo. A faixa cinza sob a sheet "Como contamos recordes" no tema claro (L10) é o M5 da 4B, ainda com o controlador.
- **Nomes:** a medida da convenção Carga adicional passou a se chamar **"Peso adicional"** em pt-BR em toda parte, substituindo "Carga adicional" (nota datada no D6 da spec).
- **Os nomes em inglês nas capturas.** A base grande das capturas usa ids que não são do catálogo (sem o prefixo `catalog_`). Por isso algumas capturas mostram o nome gravado, em inglês, também em pt-BR. É o comportamento especificado (um exercício que não é do catálogo mostra o nome gravado), não um defeito. O script de captura acrescentou o prefixo para as capturas da crítica, mas as da Task 16 ficaram como eram.
- **iOS:** três capturas do run verde do Push 2 foram conferidas (hierarquia, cores, anéis e quebra a 2× iguais às do Android); nada pediu correção própria.

### Acessibilidade e fonte grande (nos testes)

O `ProgressAccessibilitySweepTest` (15 testes) confere rótulo em todo nó clicável, alvos de 48 dp, números e botões inteiros, e exige selos de recorde nas passadas do topo, do meio e do fim, em claro e escuro e em escalas de fonte grandes (os valores exatos estão no teste). O resumo do gráfico, a tabela e o selo "Recorde" vêm da árvore de acessibilidade, não de um leitor de tela ouvido. O rótulo do selo (ele é desenhado, não é um layout) **não pode ser medido para corte**: foi conferido só nas capturas a 200% (`records-*-pt-200.png`), onde "PR" e "Primeiro" aparecem inteiros nos dois temas.

## Decisões e desvios registrados

Estão como notas datadas na [spec](../specs/2026-10-31-phase5-progress-history-design.md#notas-de-implementação). Em resumo:

- métricas por convenção, e a marca de recorde só no ponto cuja própria série é a do recorde (D6, Tasks 4 e 5);
- o ponto semanal só leva marca se a sessão que deu o valor da semana a leva (controlador, Task 5);
- o D2 perdeu a frase sobre a convenção do recorde tocado (D2, Task 10); `ChoiceFilter` ficou (Task 13);
- D8: a semana segue a região do aparelho, agora pelo `LocaleManager` (Tasks 16 e 17). Isso acrescentou o **quarto `expect/actual` do design system**, `deviceRegion`, aceito pelo controlador como a única fonte de plataforma da região com idioma por app; o custo, se errado, é um refactor posterior para o `LocalAppLocale`;
- "Peso adicional" no lugar de "Carga adicional" (D6, Task 17);
- o orçamento de toques de "ver o recorde atual" é 2 (Task 14);
- "recorde → gráfico em até 2 quadros" conta da rota (Task 16);
- veredito do S1 (Tasks 1 e 18).

## Rodada final de correções (nota de 2026-10-31)

Depois deste relatório, a revisão final da branch (Opus) e o `/code-review` acharam 13 itens; todos foram corrigidos, menos o FR-M3 (o ano no eixo x), que o controlador deixou como está. O que mudou de comportamento:

- **Recordes:** um chip ou uma busca nova sobe a lista ao topo quando o resultado novo aparece, não antes (antes, um mês mais novo acrescentado ficava acima da tela). O `RecordsTimeline` leva a consulta que responde.
- **Tabela "Ver dados":** cada linha tem a chave do treino (`ChartPoint.rowKey`), e a semana pela data de início; dois treinos que começam no mesmo milissegundo não derrubam mais a lista.
- **Gráfico de linha:** valores iguais a menos de ruído de ponto flutuante (relativo 1e-9, kg para lb) são uma linha plana, sem marcas "100" repetidas. O `MLineChart` recebe o rótulo como `@Composable (Double) -> String` e escreve as próprias marcas; os textos ficam no nó como `ChartAxisTexts` (o `ProgressAxisTexts` da tela saiu).
- **Índice:** uma construção que falha se esquece sozinha, mesmo sem ninguém esperando, e o próximo `get` constrói de novo. A guarda de `UiState.loading` passou para dentro do `rememberProgressIndex` (parâmetro `loading`). O atraso do prewarm virou o token `MoosclesTiming.progressPrewarmDelay` (500 ms).
- **Datas:** o progresso e os Recordes leem "hoje" e o fuso por um `ProgressCalendar` só (`rememberProgressCalendar`, com um `ProgressSeams` só), que se lê de novo à meia-noite local.
- **Navegação:** "Ver progresso" no detalhe aberto por cima do progresso do mesmo exercício volta a esse progresso, em vez de empilhar outro. `MoosclesApp(navController = …)` e `ProgressIndexTrace.builds` agora são `internal`.
- **Código só de teste:** `tableRows`, `recordLine` e `weekOf` saíram; os testes passaram para `tableRow`, `recordWasText` e `chartWeekOf`.

Provado: host dos quatro módulos, **1264** testes, 0 falhas (`:app` 551, `:shared` 300, `:designsystem` 120, `:screens` 293); `lintDebug`, `assembleDebug`, a compilação dos testes iOS do `:screens` e do `:designsystem`, o `check-design-tokens.ps1` e o orçamento do APK (**+284.907** sobre a base, 68 bytes a mais que antes, num build limpo). No emulador, pelo gate: `RecordsScreenTest`, `ExerciseProgressScreenTest`, `ExerciseProgressSectionsTest`, `ExerciseInfoScreenTest`, `ProgressTextsTest`, `ProgressIndexComposeTest` e `ProgressAccessibilitySweepTest` (138 testes), depois o `RecordsScreenTest` de novo (24), e `ChartsTest` com `GallerySmokeTest` (27), todos verdes. Os testes novos de comportamento falharam no código sem a correção (rodados nele de propósito), menos o da guarda de carregamento, que é uma mudança de lugar. Mínimos novos no `ci.yml`: host **1264**, `:designsystem` iOS **414**, `:screens` iOS **892**, ainda sem run de CI. As suítes inteiras de aparelho não foram refeitas, e nada disso rodou num iPhone nem no simulador iOS.

## Pendências que não bloqueiam o merge

- **Rolagem da Biblioteca** a 1,75–2,30% contra cerca de 1% na 4A (acima).
- **Meta de APK da 5A passada** em 22.695 bytes; a 5B tem 239.449.
- **Consultas e teclas da busca** sem medida no R8.
- **Um `present()` completo de até 478 ms** na troca kg/lb, em `Dispatchers.Default`; o índice daquela troca (70,4 ms) correu junto dele.
- **Itens parados nas revisões** (fora do histórico publicado), em `.superpowers/sdd/2026-10-31-phase5a-charts-progress/` (fora do histórico publicado).
- **Cancelamento cooperativo do índice** (pendência da Task 7): não feito, porque no R8 o índice leva poucos milissegundos depois de aquecido.

## O que não foi verificado

- **Tudo num iPhone:** VoiceOver lendo os resumos e as linhas da tabela, o arraste no gráfico com o dedo, o desempenho. O iOS foi provado só no simulador, no CI, e o caminho iOS do `deviceRegion` (`NSLocale`) só foi **compilado**, nunca executado.
- **Num Android físico:** TalkBack, arrastar a seleção do gráfico com o dedo, 120 Hz, o desempenho no S23 Ultra e Android 8 a 14 fora do 15.
- **O D8 abaixo do Android 13:** o caminho antigo do `deviceRegion` (`Resources.getSystem()`) não foi executado; o `CurrentWeekStartDeviceTest` roda só no Android 15.
- **O desempenho no R8 de verdade para o resto:** as consultas, as teclas e a rolagem foram medidas no APK depurável do `:screens` ou no `assemblePerformance` do emulador, não num aparelho. O índice é o único medido no R8.
- **O gráfico depois da `graphicsLayer`** da Task 17, e **o tamanho do APK depois da Task 17**: não foram refeitos.
- **O rótulo desenhado dos selos "PR" e "Primeiro"** quanto a corte: não se mede, só se vê nas capturas de 200%.
- **Os 10 baixos da crítica** e o que a crítica não enxerga: foi um emulador, com uma base de capturas montada por script, em duas línguas, sem usuário real.
- **Se os gráficos motivam** a voltar ao app, e **a qualidade do 1RM estimado** (Epley, até 10 repetições) para quem treina. É a pergunta do dono, e só uma semana de uso responde.
- **As suítes inteiras de aparelho depois de `a0eab5f`:** só as classes tocadas rodaram nas Tasks 16 (rodada 2) e 17. Os commits `8bddc89` e `d704237` a `4d5c529` ainda não passaram pelo CI.
- **A atualização a partir do APK publicado:** a chave original não está neste Mac, e a v1.5.0 não foi publicada.
- **A notificação do fim do descanso em segundo plano** depois da troca de APK (o descanso terminou com o app aberto).
- **Um teclado físico de verdade** com a busca dos Recordes (a checagem foi com eventos de teclado do `adb` no emulador).
