# Fase 3: redesign do treino ativo

Data: 2026-10-29. Item da fase 3 do [ROADMAP](../ROADMAP.md). Direção visual: pranchas "Treino ativo — escuro", "Treino ativo — claro" e "Descanso e recorde" do canvas da fase 2 (cópia de leitura em `.superpowers/scratch-backup/design-canvas/project/`; publicada em https://claude.ai/artifact/Y4pTUbYqTcCCrznT74raBM). Sistema visual: [spec da fase 2](2026-10-27-phase2-design-system-design.md) ("Giz & Ferro") e as suas [notas de implementação](2026-10-27-phase2-design-system-design.md#notas-de-implementação). Base técnica: [spec da fase 1](2026-10-27-phase1-shared-base-design.md).

O dono delegou todas as decisões desta fase ("pode avançar e fazer as melhores escolhas decidindo por mim, com base nas informações que já tem"). **Todas as decisões abaixo são minhas.** Cada uma aparece como **Dn** com uma linha de motivo. Elas partem do ROADMAP, da [P1](../reports/2026-10-27-referencias-ux-apps-treino.md), da [P3](../reports/2026-10-27-bibliotecas-cmp-graficos-animacao-midia.md), do [relatório de arquitetura](../reports/2026-10-27-arquitetura-android-ios.md), da [verificação](../reports/2026-10-28-phase2-verificacao.md) e da [crítica](../reports/2026-10-28-phase2-critica-configuracoes.md) da fase 2, e do código atual (`WorkoutScreens.kt`, `MoosclesApp.kt`, `Common.kt`, `UiContract.kt`, `WorkoutPresentation.kt`, `MoosclesViewModel.kt`, `RestTimerController.kt` e os componentes do `:designsystem`). As perguntas que só o dono responde estão no fim.

**Desvio registrado:** a P1 pedia capturas reais de Hevy, Strong e Fitbod antes desta spec. Elas não foram coletadas. Com a delegação, as decisões de interação seguem o consenso que a P1 já documenta (linha de série com anterior, pré-preenchimento, check de um toque, ±15 s) e ficam marcadas como minhas. Os pontos que a P1 apontou como frágeis (teclado, avanço de campo, háptico de recorde) têm critério de verificação próprio aqui.

## Por que

O treino ativo é a tela usada todo dia, no meio da série, com uma mão e a mão suada. Hoje ela é a tela mais antiga do app:

- os campos são `TextField` do Material com rótulo flutuante, e cada série tem duas linhas (campos e check, depois tipo e remover);
- não existe coluna "anterior" por série, só uma linha de texto com a sessão passada inteira;
- num exercício sem alvo de rotina, toda série exige digitar carga e repetições;
- o timer é um card Material no rodapé, sempre visível durante o treino, que corta "−0:15" e "+0:15" a 200% (#1 da crítica);
- recordes aparecem como faixas de texto no cabeçalho do exercício, sem reação no momento;
- a casca do app (abas e dock) está em inglês num aparelho em pt-BR (#4), quebra a 200% (#2, #3) e destoa do tema claro (#5).

O ROADMAP diz que esta é a fase que define o produto. O sistema da fase 2 já tem as peças (linha de série, campo numérico, stepper, timer em dock e expandido, selo e aviso de recorde). Falta montar a tela, a casca e o fluxo com elas, e levar tudo para o código comum, para o iOS herdar a mesma tela.

## Objetivos

1. **Registrar uma série custa um toque** quando o valor já é o certo (pré-preenchido pela rotina, pela série de cima ou pela sessão anterior), e nunca mais toques que hoje em nenhum dos fluxos medidos (ver "Orçamento de toques").
2. **Tela do treino ativo nova**, só com o `:designsystem`, em `commonMain`: cabeçalho, cards de exercício com mídia reservada, linhas de série com anterior, stepper, tipos, adicionar e remover com desfazer, trocar e reordenar exercícios.
3. **Descanso redesenhado:** dock com anel, versão expandida, versão compacta acima do teclado, notificação em andamento com ações no Android, e os estados de pausa e fim.
4. **Recorde com reação:** selo na série, aviso no topo e háptico próprio, sem confete.
5. **Fim do treino:** confirmação, resumo com estatísticas e recordes (`spring.finish`) e o novo cartão de compartilhamento.
6. **Casca do app nova:** barra de abas do sistema, barra do treino em andamento, o treino como modo de tela cheia; os itens #1–#5 e #20 da crítica resolvidos.
7. **Bilíngue:** todos os textos novos em inglês e pt-BR, com números no formato do idioma.
8. **iOS provado:** o fluxo inteiro do treino ativo roda no simulador do CI com o ViewModel, o Room e o DataStore reais.
9. **Nada muda nos dados:** sem migração, sem chave nova de DataStore, atualização por cima com treino ativo e timer correndo sem perda.

## Fora de escopo

- **Tela Treinar (lista de rotinas e pastas), editor de rotina, biblioteca, histórico, detalhe de treino e recordes.** Continuam as telas antigas do `:app` até as fases 4 a 6. Esta fase só muda o que acontece depois de "Começar" e a casca em volta delas.
- **Drop e falha como tipos de série.** Os componentes já desenham "D" e "F", mas o banco só guarda `warmup` e `assistance`. Ver D23.
- **Mídia real de exercício.** O espaço de 64 dp fica pronto com o ícone do equipamento (fase 4).
- **Nomes de exercício em pt-BR.** O catálogo embutido continua com os nomes atuais (fase 4, junto com a biblioteca).
- **RPE, notas por série, superséries, calculadora de anilhas e de aquecimento, modo guiado.**
- **Concluir série pela notificação.** A notificação só ajusta e pula o descanso (D16).
- **Live Activity, widgets, relógio, AlarmKit** (fase 9) e o timer por notificação local no iOS (fase 7).
- **App iOS (`iosApp/`)**, TestFlight e revisão visual num iPhone (fase 7).
- **Mensagens do updater e do backup em pt-BR** (D22).
- **NavHost em código comum.** A navegação continua no `:app` (D5).
- **Release publicada.** Como as fases 1 e 2, a fase termina com merge na `main` do fork e CI verde; `versionCode`, `versionName` e as notas de release ficam para o PR final ao repositório original.

## Anti-objetivos (falha mesmo que "funcione")

- **Perder uma série**, um valor digitado ou um treino ativo: por fechar o app, girar a tela, matar o processo, atualizar o APK, apertar voltar ou descartar por engano sem confirmação.
- **Registrar ficar mais lento:** qualquer fluxo do orçamento de toques com mais toques que hoje, ou o check esperar o banco para mudar na tela.
- **Quebrar instalações existentes:** mudar o schema (v5), uma chave ou um tipo do DataStore, o `applicationId`, a assinatura, o `update.json` ou os canais de notificação já criados.
- **Um segundo design para o iOS:** toda tela, componente e texto novo fica em `commonMain`. Só háptico, movimento reduzido, idioma, codificação de PNG e o que é de sistema (notificação, compartilhamento) têm código por plataforma; datas e nomes de mês e de dia da semana não usam formatador da plataforma (ajustado em 2026-10-29).
- **O timer recompor a lista:** um tick de contagem que recompõe um card ou uma linha.
- **Celebração que atrapalha:** animação que segura o toque seguinte, aviso que cobre a série em edição, som ou vibração repetida.
- **Recorde errado:** o aviso dizer "recorde" quando o histórico e o resumo não dizem, ou o contrário.
- **Inglês novo em pt-BR:** um texto desta fase aparecer em inglês com o aparelho em pt-BR (os nomes do catálogo são a exceção conhecida).
- **APK crescer mais de 1 MiB** sobre a `main` de depois da fase 2, contando o `kotlinx-datetime` (ajustado em 2026-10-29).

## Escopo: o que entra

| Superfície | Entra | Observação |
| --- | --- | --- |
| Tela do treino ativo (rota `workout`) | sim, nova | modo de tela cheia, sem abas |
| Seletor de exercício (adicionar e trocar) | sim, novo | sheet com busca; filtros por músculo e equipamento são da fase 4 |
| Timer: dock, compacto, expandido | sim | componentes da fase 2, com ajustes (D15) |
| Notificação do descanso (Android) | sim | em andamento com ações, e a de fim localizada |
| Aviso, selo e háptico de recorde | sim | |
| Confirmação de finalizar e de descartar | sim | |
| Resumo de fim (`workoutDetail/{id}?summary=true`) | sim, novo | o detalhe do histórico (`summary=false`) continua o antigo |
| Cartão de compartilhamento | sim, novo | |
| Barra de abas e barra do treino em andamento | sim, novas | no `:designsystem` e no `:screens` |
| Ícone do launcher | sim | aprovado pelo dono para esta fase (decisão 1 da fase 2) |
| `MTextField` lendo o rótulo antes do valor (#11) | sim | spike S2 |
| Tela Treinar, rotinas, biblioteca, histórico, recordes | não | fases 4 a 6 |

**D1 (minha). O treino ativo vira um modo de tela cheia, sem barra de abas.** *Por quê:* é o padrão das pranchas ("Minimizar treino" no topo, dock embaixo), devolve à lista a altura que as abas tiram (#3) e deixa o polegar só com ações do treino.

**D2 (minha). A aba "Workout" sai; ficam quatro abas: Treinar, Biblioteca, Histórico e Recordes, nesta ordem.** O treino em andamento é alcançado pela barra do treino (D8) e pelo "Retomar" da tela Treinar. *Por quê:* com o treino como modo, a aba virava um atalho duplicado; cinco rótulos são o que mais quebra a 200% (#2), e a ordem das outras quatro não muda.

**D3 (minha). A tela Treinar não é redesenhada nesta fase.** Ela continua a do `:app`, com as mesmas ações; só o destino de "Começar" e "Retomar" passa a ser a tela nova. *Por quê:* a tela é sobretudo gestão de pastas e rotinas, que a fase 4 refaz junto com o editor; refazê-la aqui seria refazê-la duas vezes, contra a regra do ROADMAP.

## Arquitetura

### Onde as telas vivem

**D4 (minha). Um módulo KMP novo, `:screens`** (Android, `iosArm64`, `iosSimulatorArm64`), com as telas redesenhadas a partir desta fase. Depende de `:shared` (estado e ações) e de `:designsystem` (componentes), usa só Compose Multiplatform `foundation`, sem Material 3, e tem os próprios `composeResources` (strings) com `Res` em `com.mooscles.screens.resources`. O `:app` depende do `:screens`. *Por quê:* o `:designsystem` é uma biblioteca de UI pura que não conhece o `:shared` (decisão da fase 2), e o `:shared` não tem dependência de UI; as telas precisam dos dois.

- Pacotes: `com.mooscles.screens.shell`, `com.mooscles.screens.workout`, `com.mooscles.screens.summary`, `com.mooscles.screens.share`. Nenhum arquivo do `:screens` fica nos pacotes `com.mooscles` ou `com.mooscles.ui`, o que evita colisão de fachadas `XxxKt` com o `:app` (regra do AGENTS.md).
- **Estabilidade do Compose:** o `:screens` compila com o mesmo arquivo de configuração de estabilidade que o `:app` usa (`app/compose-stability.conf`, ou um arquivo na raiz lido pelos dois, com o AGENTS.md atualizado). Sem isso, `WorkoutExerciseUi` e `SetUi` contam como instáveis no `:screens` e cada emissão recompõe a lista inteira. Classe nova do contrato entra na lista só se for imutável.
- `tools/check-design-tokens.ps1` passa a cobrir `screens/src/commonMain`.
- Os testes de UI ficam em `screens/src/uiTest`, rodando no emulador (`androidDeviceTest`) e no simulador iOS (`iosTest`), como no `:designsystem`. Testes puros (formatação de textos, regras de sugestão) em `commonTest`.

### Como as telas falam com o ViewModel

- As telas recebem `UiState` (o `contentUi`, sem ticks), `MoosclesActions` e callbacks de navegação. O estado do timer chega como `State<TimerUi>` e só é lido dentro do dock, da barra do treino e do timer expandido, nunca no escopo da lista (regra atual, mantida).
- O relógio do treino (tempo decorrido) é um composable próprio que lê o tempo uma vez por segundo; nenhum card ou linha lê esse estado.
- Rascunhos de digitação e estados de tela (card aberto, linha em edição, sheet aberta, timer expandido aberto) ficam em `rememberSaveable` com a identidade real (`set.id`, `entry.id`), como hoje.
- O `MoosclesViewModel` continua sendo a única porta de escrita: toda mudança passa por `act { }` e pelo `trainingGate` onde já passa hoje.

### Acréscimos ao contrato de UI (`UiContract.kt`, `:shared`)

Todos aditivos, só em memória. Nada disso é gravado. Os campos de texto em inglês que as telas antigas usam (`WorkoutExerciseUi.previous`, `RecordUi.label`, `UiState.error`) ficam como estão até as telas antigas saírem.

| Acréscimo | Para quê | Calculado em |
| --- | --- | --- |
| `WorkoutExerciseUi.previousSets: List<PreviousSetUi>` (`weightKg`, `reps`, `warmup`, `assistance`; só as séries concluídas da sessão anterior, na ordem) e `previousDate: Long?` | coluna "Anterior" por série e "Anterior: 21 out"; o pareamento é por tipo e ordem (D10) (ajustado em 2026-10-29) | `WorkoutPresentation`, da mesma passada que já monta `previous` |
| `SetUi.record: SetRecordUi?` (`kind`; `previousKg` no recorde de carga; `previousReps` só no recorde de repetições) | selo na série e texto do aviso | `WorkoutPresentation`: cada recorde batido (não o primeiro registro) marca **exatamente a série que o `WorkoutCalculations` escolheu** como recorde, nunca outra; para isso `WeightPr` e `RepsPr` ganham o `setId` da série escolhida (acréscimo puro no domínio, ver a regra 2 das decisões de 2026-10-29) (ajustado em 2026-10-29) |
| `UiState.errorCode: UiError?` (enum) ao lado de `error` | erro localizado na tela nova | `MoosclesViewModel`, que mapeia as exceções tipadas do `data/` e o código de erro do `RestTimer` (regra 7 das decisões de 2026-10-29) (ajustado em 2026-10-29) |
| `MoosclesActions.restoreSet(entryId, position, set: SetUi)` | desfazer a remoção de uma série | `WorkoutRepository.restoreSet` |

- Números ficam números no contrato. A tela formata carga, repetições, datas e durações com o `MoosclesNumbers` e o `NumberLocale` do `:designsystem` (`82,5` em pt-BR).
- `UiError` cobre ao menos: falha ao salvar, timer sem acesso ao armazenamento, dados locais indisponíveis, treino bloqueado por uma atualização em instalação, série ou exercício que não existe mais, e o genérico. O VM continua preenchendo `error` com o texto de hoje para as telas antigas. A origem do código é tipada (ajustado em 2026-10-29): o `data/` lança exceções tipadas (mesma mensagem e mesmo comportamento de hoje, só o tipo muda), o `RestTimer` ganha um código de erro em enum ao lado do `error` em texto, e o VM traduz os dois para `UiError`; exceção sem tipo ou desconhecida vira o genérico.
- **`restoreSet`:** numa `writeTransaction`, exige treino `ACTIVE`, o exercício existente, o id ausente e menos de 100 séries (`EditRules.workoutSetCount`); desloca as posições a partir de `position` e insere a série com o mesmo id e os mesmos valores. Valida com `EditRules.workoutSet`. Não captura exceção dentro da transação (AGENTS.md). Se a série não puder voltar (exercício removido, treino finalizado), o toast de desfazer já fechou ou a ação vira erro `UiError` "A série não pôde voltar".
- **Descanso 0:00:** `updateSet` passa a não iniciar o timer quando o exercício tem descanso 0 (hoje `start(…, 0)` terminaria na hora). É o jeito de desligar o descanso de um exercício em supersérie (D14).

### Como o `:app` hospeda

- O `NavHost` continua no `:app`, com as rotas em string atuais. `composable("workout")` passa a chamar `ActiveWorkoutRoute(...)` do `:screens`; `composable("workoutDetail/{id}?summary={summary}")` chama `WorkoutSummaryScreen` quando `summary=true` e a tela antiga quando `false`.
- O `Scaffold` do `MoosclesApp` passa a usar a casca comum (`AppShellBottom` com a barra do treino e as abas). Só na rota `workout` a casca some; a galeria e as licenças mantêm a barra de abas, como hoje (ajustado em 2026-10-29).
- Compartilhar, abrir a notificação no treino e o que for de `Context` ficam no `:app`, entregues às telas como callbacks.

**D5 (minha). A navegação continua com o `androidx.navigation` no `:app`; as telas do `:screens` não conhecem rotas.** *Por quê:* com as telas antigas ainda no `:app`, um NavHost comum exigiria registrar destinos Android num grafo comum e alinhar a versão do Navigation da JetBrains com a 2.10.2 do app, um risco sem ganho nesta fase. A escolha do NavHost comum fica para a fase 6, quando não sobrar tela antiga; as telas já nascem prontas para isso porque só recebem callbacks.

### Como o iOS é provado

O ROADMAP pede "o treino ativo funciona inteiro no iOS (build interno)". Não há iPhone, Xcode local nem `iosApp` (decisão da fase 1). **D6 (minha). A prova é um teste de ponta a ponta no simulador do CI**, `ActiveWorkoutFlowTest` em `screens/src/uiTest`, que monta as telas novas sobre o `MoosclesViewModel` real, o Room real (`openWorkoutDatabase` com `BundledSQLiteDriver`, num diretório temporário) e o DataStore real (`createMoosclesStore`). *Por quê:* é o mesmo desvio que as fases 1 e 2 já fizeram, e prova mais que uma tela de teste: prova os dados e o ViewModel comuns por baixo da tela comum.

- O timer do teste é um `InProcessRestTimer` novo em `shared/src/commonMain/kotlin/com/mooscles/timer/`: implementa `RestTimer` sobre o `RestTimerPersistence` (mesmo formato no DataStore) com relógio injetado e um ticker de corrotina, sem alarme nem notificação. Ele também é a base do timer do `iosApp` na fase 7. Tem testes em `commonTest` com a mesma semântica do `RestTimerController` (iniciar, pausar, retomar, ±15 s com teto de 5:00, pular, recomeçar, limpar no fim da sessão).
- `UpdateCoordinator` e `BackupDocumentStore` entram como implementações vazias de teste.
- O teste percorre: semear uma rotina e um histórico; iniciar pela ação `startWorkout(routineId)` (a tela Treinar não é comum nesta fase); concluir uma série pré-preenchida; ajustar a carga pelo stepper e concluir; concluir por sugestão; bater um recorde semeado e ver o aviso e o selo; ver o dock, ±15 s, abrir o expandido, pular; remover e desfazer; adicionar e trocar um exercício; finalizar; ver o resumo com os números e o recorde. Nos dois temas, nas escalas 1,0 e 2,0, conferindo pela árvore de semântica que todo nó clicável tem rótulo, que os alvos têm 48 dp (56 nas ações do treino) e que nenhum número aparece cortado (`assertShownWhole`).
- Se `captureToImage()` funcionar no simulador com o CMP 1.12, o teste guarda capturas dos estados principais como artefato do job `ios`, para uma revisão visual. Se não funcionar, isso fica registrado como não verificado.
- O job `ios` do CI ganha `:screens:iosSimulatorArm64Test` e um mínimo de testes, como o `MIN_DS_IOS_TESTS`.

## A casca do app

### Barra de abas

**D7 (minha). Um componente novo `MTabBar` no `:designsystem`**, que substitui o `NavigationBar` do Material.

- Quatro destinos (D2), ícone de 24 dp e rótulo em `label.md`; `bg.raised` com `elevation.2`; o selecionado com o ícone preenchido em `accent.text` e uma pílula `accent.subtle` atrás do ícone; o não selecionado em `text.secondary`. Altura mínima de 64 dp e alvos de 48 dp.
- Ícones: Treinar = halter (próprio), Biblioteca = `LayoutGrid` (Lucide), Histórico = `History` (Lucide), Recordes = anilha com seta (próprio).
- **Fonte grande:** o rótulo nunca quebra no meio da palavra. Quando algum rótulo não cabe na largura da sua coluna (medido, como a linha de série) ou a escala passa de 1,5, só a aba selecionada mostra o rótulo e as outras ficam só com o ícone, sem perder o nome acessível. *Por quê (minha):* é o padrão "rótulo só no destino ativo" do Material, mantém o texto do selecionado em 200% e resolve o #2 sem truncar.
- Semântica: cada aba é `Role.Tab`, selecionada ou não, "Treinar, guia 1 de 4".
- **#20:** tocar na aba já selecionada volta à raiz daquela aba, também de dentro da galeria e das licenças, que continuam com a barra de abas (ajustado em 2026-10-29).
- **#5:** com as cores dos tokens, a barra acompanha o tema claro.
- Strings em en e pt-BR: Train / Treinar, Library / Biblioteca, History / Histórico, Records / Recordes (**#4**).

### Barra do treino em andamento

**D8 (minha). Com um treino ativo, uma barra fixa acima das abas** (componente `ActiveWorkoutBar`, `:designsystem`), em todas as rotas com abas. Substitui o dock antigo e o ponto na aba "Workout".

- Uma linha de 56 dp no mínimo, `bg.raised`, `radius.lg` no topo: o nome do treino (`title.sm`, uma linha, com reticências permitidas porque é texto, não número), o tempo decorrido e "9 de 15 séries"; com o descanso correndo, o anel de 32 dp e o tempo do descanso no lugar das séries, e o botão de pular (56 dp).
- Tocar na barra abre o treino. Ela é um botão com o nome acessível "Treino em andamento, Treino A, 42 minutos. Descanso, 1 minuto e 24 segundos" (sem ser anunciada a cada segundo).
- A 200% vira duas linhas (nome em cima; tempo e descanso embaixo), sem esconder o pular.
- A casca reserva o teclado uma vez sem subir as abas, como o `WorkoutBottomBar` faz hoje (`TimerKeyboardInsetsHostTest` continua valendo).

### O topo das telas antigas

As telas antigas mantêm o `TopAppBar` do Material com "mooscles" e o atalho de Configurações até a sua fase. A barra de abas e a do treino são novas em todas elas.

### O ícone do launcher

O ícone vira a marca nova (duas anilhas de canto chanfrado formando o "OO", sobre grafite), como ícone adaptativo com fundo, frente e a camada monocromática do Android 13. O nome do app e o `applicationId` não mudam. Aprovado pelo dono em 2026-10-27 para esta fase.

## Tela do treino ativo

De cima para baixo, como a prancha "Treino ativo".

### Cabeçalho (64 dp no mínimo)

- À esquerda, "Minimizar treino" (`ChevronDown`, 48 dp): volta à tela de onde o treino foi aberto, ou a Treinar. O voltar do sistema faz o mesmo. Nada se perde: o treino já está salvo.
- No meio, o nome do treino (`title.sm`, até 2 linhas) e, embaixo, o tempo decorrido em `num.sm` com "· 9 de 15 séries" em `caption`.
- À direita, "Finalizar" (botão tonal S) e o menu (`EllipsisVertical`): Renomear treino, Descartar treino.
- O cabeçalho fica fixo; a lista rola por baixo dele.

### Lista

`LazyColumn` com margem de 16 dp e cards a 12 dp. **A granularidade continua por série:** cabeçalho do card, cada linha e o rodapé são itens próprios da lista, para um exercício de 100 séries não compor 100 linhas de uma vez (regra atual, `WorkoutLazyRowsHostTest`). Se o `ExerciseCard` do `:designsystem` só aceita as séries num `Column`, ele ganha uma forma segmentada (cabeçalho, linha e rodapé desenhando o mesmo card) em vez de a tela montar um card próprio.

No fim da lista: "Adicionar exercício" (secundário L) e "Finalizar treino" (primário L, o único `accent.solid` da tela), com a nota "Conclua pelo menos uma série para finalizar." enquanto não houver série concluída.

Largura máxima do conteúdo: 600 dp, centrada (paisagem e telas largas funcionam, sem layout próprio).

### Card do exercício

- **Cabeçalho:** a mídia de 64 dp (`ExerciseMedia.None` com o ícone do equipamento: barra = barra, halter = halter, kettlebell = kettlebell, polia = polia, peso corporal = corpo, Smith e as máquinas = máquina, outro = anilha); o nome (`title.md`); o metadado "Barra · Peito · série 4 de 5" (equipamento e músculo traduzidos pelo nome do enum, o progresso só com o card aberto); o menu.
- **Linha de apoio:** à esquerda o chip "Descanso 2:00" (abre a sheet de descanso, D14); à direita "Anterior: 21 out", ou "Primeira vez" sem histórico.
- **Convenção de carga:** para as convenções que não são "Total load", uma legenda de uma linha sob o metadado (por exemplo, "Por mão: registre a carga de um halter."). O texto completo vai para o item "Como registrar" do menu. *Por quê (minha):* hoje o parágrafo aparece em todo exercício, inclusive no caso comum, e empurra as séries para baixo.
- **Cabeçalho das colunas** (`label.caps`, oculto do leitor de tela): Série · Anterior · kg (ou lb; "kg assist." na convenção Assistência) · Reps.
- **Rodapé:** "Adicionar série" (tonal L, largura toda).
- **Menu do exercício:** Histórico do exercício (abre a tela antiga `exercise/{id}`; voltar retorna ao treino), Trocar exercício, Mover para cima, Mover para baixo, Como registrar, Remover exercício.
- **Remover exercício** pede confirmação ("Remover supino reto? As 3 séries dele saem deste treino.", destrutivo preenchido dentro do `MDialog`), como hoje, sem desfazer. **Trocar exercício** com séries concluídas também confirma, porque a troca apaga as séries (`replaceWorkoutExercise`).
- **Reordenar** é só pelo menu (mover para cima e para baixo, como hoje). Arrastar e soltar fica para o editor de rotina da fase 4.

**D9 (minha). Cards recolhem sozinhos.** O "exercício atual" é o primeiro com série pendente; ele fica aberto. Ao concluir a última série pendente de um exercício, depois do check (cerca de 300 ms), o card recolhe para o cabeçalho ("3 de 3 séries" com um check) e o próximo exercício com série pendente abre, com `spring.snappy`, e a lista rola até o cabeçalho dele. Os outros exercícios começam recolhidos ("0 de 3 séries"); tocar no cabeçalho abre ou fecha, e o que a pessoa abre ou fecha à mão não muda mais sozinho. Com movimento reduzido, a troca é instantânea. *Por quê:* é a prancha (o exercício seguinte aparece recolhido) e corta a rolagem num treino de seis exercícios, que hoje mostra todas as séries de todos.

### Linha de série

O `SetRow` da fase 2: número (com a letra do tipo), anterior, carga, repetições e check, com 56 dp no mínimo e duas linhas a partir de 1,5 ou quando não cabe.

**Valores mostrados e sugestões (D10, minha).** Cada campo vazio de uma série pendente mostra uma sugestão em `text.secondary` (o estado "sugerido" do `NumericField`), nesta ordem:

1. o valor salvo da própria série (alvo da rotina ou o que a pessoa já registrou), que não é sugestão;
2. a série imediatamente acima **concluída hoje** no mesmo exercício;
3. a série correspondente da sessão anterior do exercício (`previousSets`), pareada por tipo e depois por ordem: o n-ésimo aquecimento de hoje com o n-ésimo aquecimento concluído da sessão anterior, a n-ésima série de trabalho com a n-ésima de trabalho concluída (a assistida conta como de trabalho). A coluna "Anterior" usa o mesmo pareamento; sem par, mostra "—" (ajustado em 2026-10-29);
4. nada.

"Vazio" é repetições 0. Na carga, a regra depende da convenção do exercício (ajustado em 2026-10-29): nas convenções de carga comum (`TOTAL_LOAD`, `PER_HAND`, `ONE_SIDE`), 0 kg conta como vazio e recebe a sugestão de carga; nas convenções "Added weight" (`BODYWEIGHT`) e "Assistance" (`ASSISTANCE`), 0 é um valor real (peso corporal sem carga extra, ou nenhuma assistência) e não é substituído. Testes em `commonTest`: 0 kg em carga comum recebe a sugestão; 0 kg em "Added weight" e em "Assistance" não recebe; um valor maior que 0 nunca é substituído; em libras, a sugestão grava o kg exato da origem; um campo apagado de propósito fica sem sugestão. Se a pessoa apagar um campo de propósito (por exemplo, para registrar só peso corporal), aquele campo não recebe mais sugestão nesta sessão: a tela guarda os pares (série, campo) tocados em `rememberSaveable`. *Por quê:* a série de cima de hoje captura a progressão (82,5 hoje contra 80 na semana passada), que é o caso comum de séries iguais; a anterior cobre a primeira série do dia; quem faz pirâmide tem o atalho de tocar no "anterior" (abaixo).

**Concluir:**

- **Toque no check** com repetições válidas (salvas ou sugeridas): o disco enche na hora, por estado local otimista, com `spring.set` e o háptico de concluir, sem esperar o Room. A tela envia `updateSet` com os valores efetivos: as sugestões viram valores gravados. A carga em kg vem exata da origem (sem ida e volta por lb).
- **Gesto (D11, minha):** deslizar a linha pendente para a direita além de 35% da largura conclui pelo mesmo caminho do check, com o háptico de passo ao cruzar o limite. Deslizar para a esquerda além de 35% remove a série (com desfazer). Os dois têm alternativa por toque e por ação de acessibilidade (WCAG 2.5.7). *Por quê:* o ROADMAP pede "gesto para concluir"; com o celular no banco, um deslize na linha toda é mais fácil que acertar um alvo, e o desfazer protege o gesto de remover.
- **Sem repetições válidas:** o check não conclui; háptico de ação bloqueada; a linha entra em edição nas repetições com o erro "Informe as repetições" (`NumericField` em erro, com ícone).
- **Desmarcar** uma série concluída volta a pendente (háptico de desmarcar). O descanso que estiver correndo continua.
- Concluir com o descanso automático ligado inicia o timer com o descanso do exercício (regra atual do VM); se já houver um correndo, ele recomeça com o novo tempo.

**Editar (D12, minha).** Tocar na carga ou nas repetições põe a linha em edição (`bg.raised`, `motion.base`): o campo tocado ganha o anel de foco e, sob a linha, aparece o `Stepper` daquele campo ("− 85,0 kg +", "passo de 2,5 kg"; 5 lb em libras; 1 nas repetições). **O teclado não abre no primeiro toque.** Um segundo toque no campo em edição (ou o botão de teclado ao lado do stepper, 48 dp) abre o teclado do sistema, decimal na carga e numérico nas repetições, com a ação "Próximo" da carga para as repetições e "Concluído" nas repetições, que só fecha o teclado (não conclui a série). Tocar fora da linha, no check ou em outra linha sai da edição. *Por quê:* repetir a sessão anterior com ±2,5 kg é o caso comum; o stepper de 56 dp funciona com a mão suada e não cobre metade da tela como o teclado, que continua a dois toques para um salto grande.

- **Gravação:** cada passo do stepper e cada tecla válida gravam na hora, como hoje. Segurar o stepper (repetição a cada 80 ms) grava uma vez ao soltar. Ao sair da edição, ao concluir e quando o app vai para segundo plano, o último valor válido já está gravado.
- **Garantias do `SetEditor` atual que continuam:** um reconhecimento atrasado do banco não apaga um rascunho mais novo; um rascunho inválido não é enviado e não é perdido; a troca kg/lb converte o rascunho a partir do kg exato; a vírgula decimal é aceita. Os testes de hoje (`WorkoutInputHostTest` e os do fluxo) são portados para a tela nova ou aposentados com motivo escrito no plano.
- **Tocar no "anterior"** de uma série pendente copia a carga e as repetições da sessão anterior para a série (háptico de passo). Não conclui. Numa série concluída, o toque não faz nada.
- Com o teclado aberto, a linha em edição fica visível acima do teclado e do dock compacto (`bringIntoView`).

**Tipos de série** (drop e falha ficam de fora, ver D23). Tocar no número da série abre uma sheet "Tipo da série" com `RadioRow`: Normal; Aquecimento ("não conta para recordes"); Assistida ("recordes de repetições à parte", só quando a convenção do exercício não é Assistência). No fim da sheet, "Remover série" (destrutivo contornado). A letra na linha é a do sistema: "A" ou "W" no aquecimento; a assistida ganha a letra "AS" nos dois idiomas, com o nome falado "série assistida" ou "assisted set" (`SetType.Assisted` novo no `:designsystem`; escolha minha: o componente não tinha como mostrar uma série assistida, e "AS" serve às duas línguas). Os tipos "D" e "F" do componente não aparecem nesta fase.

**Adicionar série:** `addSet` (copia a carga e a assistência da última série, como hoje); a linha nova entra com `spring.snappy` e mostra as sugestões. Limite de 100 por exercício (`EditRules`).

**Remover série e desfazer (D13, minha).** Remover (deslize, sheet do tipo ou ação de acessibilidade) apaga a série na hora e mostra o toast "Série 4 removida" com "Desfazer" (6 s, ou o tempo maior que a acessibilidade do sistema pedir). "Desfazer" chama `restoreSet` com o snapshot que a tela guardou (id, posição e valores). Só a última remoção pode ser desfeita; finalizar, descartar ou remover outra série fecha o toast. *Por quê:* apagar de verdade e reinserir com o mesmo id mantém o banco, os recordes e o fim do treino sempre coerentes; esconder a série e apagar depois deixaria um estado escondido para o finalizar e para a morte do processo tratarem.

### Seletor de exercício

Uma `MBottomSheet` de até 90% da altura: campo de busca (`MTextField`, `ExerciseSearch` comum, com a mesma busca de hoje); "Recentes" primeiro (até 8 exercícios dos últimos treinos, sem repetir); depois a lista toda em ordem alfabética, cada linha com o nome, "Peito · Barra" e o ícone do equipamento, 56 dp. Tocar adiciona (ou troca) e fecha. Sem filtros por músculo e equipamento nem criação de exercício personalizado aqui (a biblioteca antiga continua criando). *Por quê (minha):* o seletor antigo é um diálogo Material dentro de uma tela nova; a versão mínima resolve o fluxo do treino sem antecipar a biblioteca da fase 4.

### Sem treino ativo

Se a rota `workout` abrir sem treino ativo (depois de descartar, ou por um estado restaurado), a tela mostra o `EmptyState` "Nenhum treino em andamento" com "Escolher rotina" (vai a Treinar) e "Treino vazio" (`startWorkout(null)`).

### Abrindo o treino

"Começar" na tela Treinar navega na hora para a rota `workout`, que mostra `Skeleton` no formato dos cards (um por exercício da rotina) até o treino salvo chegar à tela. O `startingWorkout` do VM continua segurando o carregamento, como hoje. Não há tela intermediária de prévia da rotina.

## Descanso

### Comportamento

- Começa sozinho ao concluir uma série quando "Descanso automático" está ligado (preferência atual) e o exercício tem descanso maior que 0:00. Com ele desligado, não aparece nada; "Iniciar descanso agora" fica na sheet de descanso.
- ±15 s, pular, pausar, retomar e recomeçar usam as ações atuais (`adjustTimer`, `skipTimer`, `pauseTimer`, `resumeTimer`, `resetTimer`), com o teto atual de 5:00.
- O timer pertence à sessão (regra atual): some ao finalizar ou descartar.

**D14 (minha). Sheet de descanso do exercício.** O chip "Descanso 2:00" abre uma sheet com o nome do exercício, um `Stepper` em passos de 15 s de 0:00 a 5:00, atalhos (1:00, 1:30, 2:00, 3:00), a nota "Vale para os próximos descansos deste exercício; o descanso atual mantém o próprio tempo." e "Iniciar descanso agora". 0:00 desliga o descanso daquele exercício. *Por quê:* hoje a edição é um diálogo com um campo m:ss digitado; o stepper serve a mão suada, e o 0:00 resolve superséries sem uma preferência nova.

### Dock, compacto e expandido

- **Dock** (`RestTimerDock`): só existe com o timer correndo, pausado ou nos 3 s de "Descanso concluído" (`timerDoneHold`); sem timer, não ocupa espaço (hoje fica sempre visível). Entra de baixo com `motion.base` e sai com `motion.fast`. Mostra o anel, "Descanso · Supino reto", o tempo, −15 s, +15 s e pular. **Tocar no corpo do dock abre o expandido** (o componente ganha esse toque e o rótulo "Abrir timer de descanso, 1 minuto e 24 segundos restantes").
- **D15 (minha). Dock compacto:** uma linha de 56 dp com o anel pequeno, o tempo e o pular, sem ±15 s (que ficam no expandido). Ele aparece em dois casos: com o teclado aberto, logo acima do teclado; e com escala de fonte a partir de 1,5 no treino ativo, no lugar do dock empilhado. *Por quê:* a 200% o dock empilhado da fase 2 ocupa um terço da tela (o #3 de novo), e com o teclado aberto o espaço que sobra é para a série em edição. Requisito: a 200%, num Pixel 8 (412 × 915 dp), com o timer correndo, a lista tem pelo menos 60% da altura.
- **Expandido** (`RestTimerExpanded`, prancha "Descanso e recorde"): abre por cima do treino com `motion.slow` e `ease.drawer` (fade com movimento reduzido), fecha com voltar ou com "Fechar timer" (`ChevronDown`) no topo. Mostra "Descanso · Supino reto · série 4 de 5", o anel de 280 dp, "de 2:00", ±15 s, "A seguir: série 5 · 85 × 6" (a próxima série pendente do exercício do timer, ou a primeira pendente do treino; sem nenhuma, a linha some), "Pular descanso" (tonal L), "Pausar"/"Retomar" (secundário) e "Recomeçar" (texto). Os controles ficam na metade de baixo. Ao terminar, mostra "Descanso concluído" com o anel cheio e o check e, depois dos 3 s, fecha sozinho e volta para a série seguinte.
- **Pausado:** o dock mostra o ícone de pausa e o arco em `text.tertiary` (nota da crítica da fase 2); retomar fica no expandido.
- O fim do descanso com o app aberto dá o háptico de fim uma vez e o som curto que já existe.

### Notificação (Android)

**D16 (minha). Notificação em andamento durante o descanso, com ações.** Quando o app vai para segundo plano com o timer correndo ou pausado, o `RestTimerController` posta uma notificação silenciosa num canal novo `rest_progress` (`IMPORTANCE_LOW`, sem som nem vibração): título "Descanso" com a contagem regressiva do próprio sistema (`setUsesChronometer` com `setChronometerCountDown`, ou o tempo fixo quando pausado), texto "Supino reto · a seguir série 5", e as ações "−15 s", "+15 s" e "Pular" por um receiver novo, `exported=false`. Ela é atualizada só quando o estado muda (ajuste, pausa, pular), some quando o app volta ao primeiro plano, ao pular, ao terminar, ao finalizar e ao descartar. As ações passam pela mesma regra do `trainingGate` e do `assertTrainingMayStart` que as ações da tela. *Por quê:* a P1 mostra que o timer saiu do app para a tela de bloqueio; a fase 2 deixou esta notificação para esta fase; o cronômetro do sistema não exige serviço em primeiro plano nem acordar o app a cada segundo. "Concluir série" na notificação fica de fora: gravar dados a partir de um receiver é um risco que esta fase não precisa correr.

- O canal `rest` e a notificação de fim continuam com o mesmo id e o mesmo comportamento; o texto passa a ser localizado: "Descanso acabou. Próxima: supino reto, série 5" / "Rest is over. Next: bench press, set 5" (voz da fase 2). Os nomes dos dois canais também passam a ser localizados.
- Tocar em qualquer notificação do descanso abre o treino (ação nova de intent, tratada como a `OPEN_UPDATES`: só num lançamento novo ou em `onNewIntent`).
- Ids: a de fim continua 1; a em andamento usa 2. Nenhum canal existente muda de id.

### Tela desligada e morte do processo

- O prazo do timer já é um horário de parede gravado no DataStore e o alarme já é do `AlarmManager`: com a tela desligada ou o processo morto, o fim chega pela notificação, e ao voltar o dock mostra o tempo certo. Nada disso muda.
- A notificação em andamento é recriada a partir do estado gravado quando o processo volta, se o app estiver em segundo plano com o timer correndo.
- As séries são gravadas a cada mudança. O que a morte do processo pode levar é só o estado da tela (card aberto, linha em edição, sheet), restaurado pelo `rememberSaveable` quando o sistema restaura a atividade.

## Recordes

**D17 (minha). O recorde é confirmado pelo `WorkoutPresentation`, nunca previsto pela tela.** Quando uma série que a pessoa acabou de concluir chega no estado com `SetUi.record` preenchido:

- o selo `PrBadge` entra na série com `spring.pr` (a linha vira "concluída com recorde");
- o `PrToast` aparece no topo da lista, abaixo do cabeçalho: "Novo recorde de carga" ou "Novo recorde de repetições", com "87,5 kg × 6 · antes 85 kg" no recorde de carga (o domínio guarda só a carga máxima anterior) e "80 kg × 8 · antes 80 kg × 6" no de repetições (mesma carga) (ajustado em 2026-10-29); some em 4 s e é lido de forma educada; não cobre a linha em edição (se ela estiver no topo, o aviso vai para baixo do cabeçalho do card seguinte, ou espera ela sair da edição);
- o háptico de recorde toca.

*Por quê:* a regra de recorde (convenções, aquecimento, assistência, tolerância por `ulp`) já existe num lugar só e é testada; duplicá-la na tela arrisca um aviso que o histórico desmente.

- **D18 (minha). Primeiro registro não comemora no treino.** Um exercício sem histórico não dá aviso, selo nem háptico de recorde; ele aparece no resumo como "Primeiro registro". *Por quê:* num treino com exercícios novos, toda primeira série seria um "recorde", e a fase 2 pede sobriedade.
- **Háptico (desvio da fase 2):** a tabela da fase 2 diz que o háptico de recorde substitui o de concluir. Como o check não pode esperar a confirmação, o de concluir toca no toque e o de recorde toca quando a confirmação chega. Se a confirmação chegar em até 150 ms, o de concluir já tocou e o de recorde toca mesmo assim; essa é a única sequência dupla permitida. Fica registrado na nota de implementação da fase 2.
- Só séries concluídas nesta tela, nesta sessão de uso, disparam aviso. Recordes que já estavam no estado ao abrir (retomar o treino, reiniciar o app) mostram só o selo, sem aviso nem háptico.
- Se um recorde deixa de valer (a pessoa desmarca ou edita a série), o selo sai sem aviso. O selo fica sempre na série que o `WorkoutCalculations` escolhe como recorde (a mais pesada, com empate decidido pelas repetições, e na regra de repetições a de mais repetições naquela carga), nunca noutra com o mesmo valor (ajustado em 2026-10-29). Se uma série posterior bate o recorde de novo, o selo passa para ela e o aviso repete com o novo valor ("antes" continua sendo o recorde do histórico).

## Finalizar, descartar e resumo

### Finalizar

- "Finalizar" no cabeçalho ou "Finalizar treino" no fim da lista abre o `MDialog` "Finalizar treino?" com "9 séries em 4 exercícios · 42 min." e, se houver séries pendentes, "3 séries não concluídas ficam de fora." Ações: "Continuar treinando" e "Finalizar" (primário, na ponta).
- Sem série concluída, "Finalizar" não abre o diálogo: háptico de ação bloqueada e o toast "Conclua pelo menos uma série para finalizar."
- Ao confirmar, o botão mostra o estado de carregamento e a tela espera o treino aparecer no histórico para navegar ao resumo (regra atual do `finishingId`). Um erro devolve o botão ao normal com o toast de erro.

### Descartar

- No menu do cabeçalho. `MDialog` destrutivo: "Descartar treino? As 9 séries registradas serão apagadas." (sem séries: "Descartar treino? Nada foi registrado ainda."). Ações "Continuar treinando" e "Descartar" (perigo, preenchido). A navegação depois do descarte continua pelo `discardNavigationRequest`.

### Resumo (`workoutDetail/{id}?summary=true`)

**D19 (minha). Uma tela nova e curta; o detalhe completo continua no histórico.**

- Entra com `spring.finish` (o card de estatísticas de 0,92 a 1, com opacidade; fade com movimento reduzido) e o háptico de concluir.
- Topo: "Treino concluído", o nome e a data com o horário ("ter., 29 out · 18:02–18:44").
- Estatísticas em `num.lg`, em grade de 2 colunas (1 coluna a partir de 1,5): Duração, Séries, Volume (carga registrada × repetições, sem assistência, regra atual) e Exercícios.
- "Recordes": uma linha por recorde batido, com o `PrBadge`, "Supino reto · 87,5 kg × 6 · antes 85 kg" (ajustado em 2026-10-29); depois "Primeiros registros" com o selo suave. A seção some se não houver nenhum.
- Constância: "12 treinos nas últimas 4 semanas" (contado dos treinos finalizados, janela de 28 dias de calendário no fuso do aparelho, pelo `kotlinx-datetime` (ajustado em 2026-10-29); nunca cobra ausência).
- Por exercício: o nome e a melhor série ("82,5 kg × 8") em `num.sm`.
- Ações: "Concluir" (primário L, volta a Treinar), "Compartilhar" (secundário L, com ícone) e "Ver detalhes" (texto, abre `workoutDetail/{id}?summary=false`, a tela antiga, que edita e apaga).

### Cartão de compartilhamento

**D20 (minha). O cartão é desenhado em código comum, na identidade nova, e o envio continua no `:app`.**

- Formato: PNG de 1080 px de largura, como hoje, com as mesmas páginas quando o treino não cabe numa (regra atual do `WorkoutSharePreview`). Fundo grafite `bg.base` escuro sempre (o cartão é uma peça de marca, não segue o tema), a marca "MOOSCLES" com as anilhas, o nome e a data, Duração · Séries · Volume em Barlow Condensed, os recordes em latão com o selo, e a lista de exercícios com a melhor série. Números no formato do idioma do aparelho. A data e o horário do cartão saem do `kotlinx-datetime` (fuso do aparelho) com os nomes de mês e de dia da semana dos recursos en e pt-BR do `:screens`, sem formatador da plataforma (ajustado em 2026-10-29).
- Desenho: um `ShareCardPainter` em `com.mooscles.screens.share` que desenha num `ImageBitmap` com `CanvasDrawScope` e `TextMeasurer` (APIs comuns), com as fontes do `:designsystem`. A prévia na sheet mostra o mesmo bitmap. O PNG é codificado por plataforma (Android: `Bitmap.compress`; iOS: Skia, na fase 7).
- O `:app` continua dono do arquivo em cache privado, do `FileProvider` com permissão temporária, da limpeza de 7 dias e do atalho do WhatsApp.
- Os dados do cartão (`WorkoutShareCard.from`, melhor série por exercício) vão para `commonMain` se forem Kotlin puro, com teste de equivalência contra a versão atual.
- Isso depende do spike S1. Se ele reprovar, o renderizador Android atual (`WorkoutCardRenderer`) é redesenhado com as cores dos tokens e as fontes do APK, e o cartão comum fica para a fase 5 ou 7, registrado.

## Acessibilidade

### Leitura da linha de série (resolve o M1 da fase 2)

- A linha continua um nó de resumo com as cinco ações (Concluir série, Editar carga, Editar repetições, Mudar tipo, Remover série), o check como alternável próprio ("Concluir série 4", marcado ou não) e os campos focáveis e editáveis.
- Com sugestão, a leitura diz: "Série 2 de 5, anterior 80 quilos por 8, sugestão 82,5 quilos, 8 repetições, não concluída". O `SetRowState` ganha os indicadores de sugestão para carga e repetições (cor `text.secondary` e a palavra "sugestão" na fala).
- **D21 (minha). O toque duplo na linha abre a edição da carga**, sem mudar dado: a linha ganha `onClick` com o rótulo "Editar série", que põe a linha em edição e move o foco de acessibilidade para o stepper da carga ("Editando carga da série 4"). *Por quê:* sem um `onClick`, o TalkBack simula um toque no centro do nó, que cai num campo qualquer (o M1); concluir já tem o próprio alternável e a ação, e um toque duplo que conclui por engano é pior que um que abre a edição.
- As ações "Editar carga" e "Editar repetições" fazem o mesmo para o campo pedido. A ação "Editar repetições" com o teclado: o próprio campo, com toque duplo, abre o teclado (caminho de quem prefere digitar).

### Resto da tela

- Ordem de leitura: cabeçalho (Minimizar, título, tempo e séries, Finalizar, Mais opções), aviso de recorde quando houver, cards na ordem, dock por último. O dock e a barra do treino são grupos de travessia próprios.
- O tempo decorrido tem nome acessível ("Tempo de treino, 42 minutos") e não é anunciado a cada segundo; o timer segue a regra da fase 2 (anuncia ao começar, ao ajustar e ao terminar).
- O expandido e as sheets prendem o foco e devolvem ao gatilho quando fecham.
- O toast de desfazer é anunciado e fica pelo tempo que a acessibilidade do sistema recomenda (`calculateRecommendedTimeoutMillis`, já no `MToast`).
- Deslizar sempre tem alternativa por toque (sheet do tipo) e por ação.
- **#11, `MTextField`:** o campo passa a ser lido pelo rótulo antes do valor ("Descanso padrão, 2:30 min, campo de edição"). Isso vale para Configurações e para o campo de busca do seletor. O caminho técnico é o spike S2.

### Fonte grande, cor e movimento

- Tudo em `sp`; a linha de série em duas linhas a partir de 1,5 ou quando não cabe; o cabeçalho com título em até duas linhas; o resumo em uma coluna a partir de 1,5; o dock compacto (D15); a barra de abas (D7). Nenhum número com reticências ou cortado, provado por `assertShownWhole` a 1,0, 1,3 e 2,0 nas larguras de 320 e 412 dp, no Android e no iOS.
- Os dois temas usam só tokens; nada novo fora dos 50 pares medidos da fase 2 (se um par novo aparecer, ele entra no `ContrastTokensTest`).
- Movimento reduzido (`MTheme.reducedMotion`): molas e deslocamentos viram fade de `motion.fast`; recolher e abrir cards é instantâneo; o anel anda de segundo em segundo; a rolagem até o próximo exercício é instantânea.

## Háptico

O `MoosclesHaptics.perform` da fase 2, com estes momentos nesta fase:

| Momento | Evento |
| --- | --- |
| Concluir série (toque, deslize ou ação) | `SetCompleted` |
| Recorde confirmado | `PersonalRecord` (depois do de concluir, ver Recordes) |
| Desmarcar série | `SetUnchecked` |
| Passo do stepper, ±15 s, cruzar o limite do deslize, copiar o "anterior" | `StepperStep` |
| Concluir sem repetições, finalizar sem série | `Rejected` (o evento de ação bloqueada) |
| Fim do descanso com o app aberto | `RestFinished` (uma vez, regra M5 da fase 2) |
| Resumo aparece | `SetCompleted` |

Nada vibra ao remover, desfazer, abrir sheets ou trocar de aba.

## Textos e idiomas

- Todo texto novo do `:screens` em `screens/src/commonMain/composeResources/values/strings_*.xml` (inglês, idioma base) e `values-pt-rBR/`, com plurais por recurso ("1 série", "3 séries"). Um `StringsParityTest` no `:screens` confere as chaves e os argumentos dos dois idiomas, como no `:designsystem`.
- Textos de componente novos (`MTabBar`, `ActiveWorkoutBar`, dock compacto, `SetType.Assisted`) vão para o `strings_components.xml` do `:designsystem`.
- Textos de notificação e nomes de canal ficam em recursos Android do `:app` (`values` e `values-pt-rBR`), porque o `RestTimerController` não compõe.
- Músculo, equipamento e convenção de carga são traduzidos pela tela a partir do enum (os rótulos em inglês do `:shared` não mudam, porque o backup e as telas antigas usam).
- **Nomes de exercício:** continuam os do catálogo (em inglês) até a fase 4. É a única mistura de idioma conhecida nas telas novas, e fica registrada.
- **Erros:** as telas novas nunca mostram `UiState.error` cru. Elas mostram o texto localizado do `UiError` (vindo das exceções tipadas e do código do timer (ajustado em 2026-10-29)), com o genérico "Não foi possível salvar a última mudança. Seus dados continuam no aparelho." para o que não tiver código.
- **D22 (minha). As mensagens do updater e do backup continuam em inglês até a fase 6** (decisão 11 do plano da fase 2). *Por quê:* elas só aparecem em Configurações, chegam prontas do updater e do backup, e localizá-las agora espalharia o diff desta fase por `updates/` e `backup/`, que ela não precisa tocar.
- **Datas(ajustado em 2026-10-29):** o `org.jetbrains.kotlinx:kotlinx-datetime` (biblioteca de primeira parte do Kotlin) entra no `commonMain` só para fuso e calendário (`TimeZone`, `LocalDate`, `LocalDateTime`); o instante continua `kotlin.time.Instant` (decisão da 1B). Os nomes de mês e de dia da semana vêm dos recursos en e pt-BR do `:screens`, nunca de um formatador da plataforma. Um teste de equivalência contra o `java.time` na JVM (`androidHostTest`), como os helpers da 1B, cobre data local, fuso com horário de verão e a janela de 28 dias. O tamanho dele conta no orçamento de APK.
- Formatação: datas curtas por idioma ("21 out" / "Oct 21"), separador decimal e de milhar por idioma, espaço não separável antes da unidade e `×` entre carga e repetições (regras da fase 2).

Amostras (voz da fase 2: direto, sem exclamação, verbo no botão):

| Situação | pt-BR | en |
| --- | --- | --- |
| Cabeçalho | 42:18 · 9 de 15 séries | 42:18 · 9 of 15 sets |
| Sem histórico | Primeira vez | First time |
| Série removida | Série 4 removida · Desfazer | Set 4 removed · Undo |
| Recorde | Novo recorde de carga · 87,5 kg × 6 · antes 85 kg | New weight record · 87.5 kg × 6 · before 85 kg |
| Finalizar | Finalizar treino? 9 séries em 4 exercícios · 42 min. | Finish workout? 9 sets across 4 exercises · 42 min. |
| Bloqueado | Conclua pelo menos uma série para finalizar. | Complete at least one set to finish. |
| Notificação | Descanso · Supino reto · a seguir série 5 | Rest · Bench press · next set 5 |
| Resumo | Treino concluído | Workout complete |
| Vazio | Nenhum treino em andamento | No workout in progress |

## Dados e compatibilidade

- **Sem mudança de schema.** O Room fica na v5; nenhuma entidade, coluna ou migração nova. Tudo o que a tela precisa já está nas tabelas: valores por série, `warmup`, `assistance`, descanso por exercício e o histórico para "anterior" e recordes. *Por quê não há exceção:* o único candidato a coluna nova (drop e falha) foi deixado de fora (D23).
- **D23 (minha). Drop e falha ficam para uma spec própria**, com a coluna aditiva, a migração v5→v6 com teste nas três listas legadas, o formato de backup (versão nova do JSON, `ImportPlanner`) e a regra de recorde. *Por quê:* é uma mudança de dados e de formato de arquivo com custo em quatro lugares, para um rótulo que não muda nenhum número do app.
- **DataStore sem mudança:** nenhuma chave nova, renomeada ou com outro tipo em `local_preferences` ou `app_updates`. O timer continua com as chaves `timer_*` atuais; o `InProcessRestTimer` usa o mesmo `RestTimerPersistence`. Nenhuma preferência nova nesta fase (o estado das telas é de sessão).
- **Escritas novas:** só `restoreSet`, que grava uma linha de `workout_sets` com as mesmas regras de `EditRules` das outras escritas, numa `writeTransaction` só, sem `catch` dentro.
- **Comportamento novo do VM:** descanso 0:00 não inicia o timer; `errorCode` ao lado de `error`.
- **Erros tipados (ajustado em 2026-10-29):** as exceções do `data/` (`WorkoutRepository`, `EditRules`) passam a ter tipo, com as mesmas mensagens e o mesmo efeito; o `RestTimer` ganha um código de erro em enum, implementado no `RestTimerController` e no `InProcessRestTimer`. Isso toca áreas do `compat-reviewer` e é revisado na implementação.
- **Android:** canal novo `rest_progress` (aditivo; o `rest` não muda), receiver novo `exported=false`, ação nova de intent para abrir o treino, ícone do launcher novo. `applicationId`, assinatura, `minSdk`, `versionCode`, o `update.json` e o `trainingGate` da instalação não mudam: a atualização continua esperando não haver treino ativo nem timer.
- **Histórico:** continua um snapshot independente; nada desta fase muda treinos finalizados.
- O `compat-reviewer` roda antes do merge, porque a fase mexe em `shared/.../data/WorkoutRepository.kt` e `EditRules.kt`, `shared/.../domain/WorkoutCalculations.kt` (o `setId` dos recordes), `shared/.../timer/`, `app/.../timer/` e nos `build.gradle.kts`.

## Desempenho

Base: as medidas da [fase 1B](../reports/2026-10-27-phase1b-verificacao.md) e do [PERFORMANCE.md](../../../PERFORMANCE.md), com a base grande (208 treinos, cerca de 12,7 mil séries) e um treino ativo grande.

| Medida | Orçamento |
| --- | --- |
| Toque no check até o disco cheio | o próximo quadro; nenhum trabalho de banco ou de apresentação no caminho do toque |
| Ticks do timer (dock, barra do treino, expandido) | zero recomposições de cards e linhas, provado por teste com contador de recomposição |
| Relógio do treino | só o composable do relógio recompõe a cada segundo |
| "Começar" até o primeiro quadro com skeleton | o próximo quadro |
| "Começar" até o treino editável (R8, `assemblePerformance`) | sem piora acima do ruído contra a 1B |
| Rolagem rápida do treino ativo grande, p95 do quadro | sem piora acima de 10% contra a base do PERFORMANCE.md, no mesmo emulador |
| `present()` depois de concluir uma série com a base grande | sem piora contra a 1B; o campo `SetUi.record` e `previousSets` não podem refazer as passadas do histórico (usam o que `historyChanged` já guarda) |
| Animações | 60 fps no aparelho de referência (ver as perguntas) |
| APK debug | crescimento até 1 MiB sobre a `main` de depois da fase 2 (`4f2911c`), medido pelo `tools/check-apk-budget.ps1` com empacotamento do zero |

A única dependência nova é o `kotlinx-datetime`, de primeira parte (ajustado em 2026-10-29). Sem biblioteca nova de terceiros: nada de Material 3 no `:screens`, Lottie, Coil, Vico ou Roborazzi nesta fase. Ícones novos (Lucide `History`, `LayoutGrid`, `Search`, `Keyboard`, `Share2`, `Pencil`, `ArrowUp`, `ArrowDown` e o que mais a tela pedir) entram convertidos para `ImageVector`, como na fase 2.

## Spikes, antes de comprometer

1. **S1, cartão em código comum.** Desenhar o cartão com `CanvasDrawScope` e `TextMeasurer` num `ImageBitmap` de 1080 px, fora da thread principal, com as fontes do `:designsystem` (Barlow com `tnum`), e codificar em PNG no Android. Aprova se o PNG sai igual à prévia, em menos de 500 ms no emulador, sem travar a UI, e com o mesmo resultado no simulador iOS (o bitmap; a codificação iOS é da fase 7). Se reprovar: plano B do D20.
2. **S2, rótulo antes do valor no `MTextField` (#11).** Testar as alternativas (por exemplo, o rótulo como parte do nó do campo, ou o valor exposto como `stateDescription`) contra o TalkBack de verdade (ver Verificação), sem quebrar a edição nem o leitor do iOS. Se nenhuma funcionar sem efeito colateral, o #11 fica como está e registrado, de novo, com o motivo.
3. **S3, teclado no iOS.** O dock compacto acima do teclado e o `bringIntoView` da linha em edição, com `WindowInsets.ime`, no simulador (o relatório de arquitetura aponta o teclado do CMP no iOS como o maior risco). Se o simulador do CI não abrir teclado de software, o teste usa o foco e as insets simuladas, e o comportamento real fica como não verificado.

## Como verificar

- **Testes de host e `commonTest`:**
  - `WorkoutPresentationTest`: `previousSets`, `previousDate` e `SetUi.record` (um recorde marca uma série só; aquecimento e assistência; primeiro registro sem marca; tolerância por `ulp`; lb e kg);
  - `WorkoutRepository.restoreSet` (posições, limites, treino finalizado, exercício removido) e o descanso 0:00 sem timer;
  - `InProcessRestTimer` contra a semântica do `RestTimerController`;
  - regras de sugestão (ordem, campo tocado, carga 0) e formatação dos textos da tela nos dois idiomas;
  - `StringsParityTest` do `:screens`;
  - os testes de host atuais do `:app` que cobrem o treino ativo, portados ou aposentados com motivo no plano.
- **`uiTest` no emulador e no simulador iOS:** a linha de série (sugestão, edição, stepper, teclado sob demanda, deslize, tipo, remover e desfazer), cards que recolhem, dock (normal, compacto, com teclado), expandido, aviso e selo de recorde, finalizar e descartar, resumo, barra de abas (a 200%, só o rótulo do selecionado; #20), barra do treino; recomposições zero por tick; o `ActiveWorkoutFlowTest` de ponta a ponta (D6).
- **Orçamento de toques**, como teste de UI que conta toques e gestos, contra a contagem de hoje feita na `main` antes da troca de tela (o plano registra a contagem antiga com o mesmo roteiro):

  | Fluxo | Hoje | Novo |
  | --- | --- | --- |
  | Concluir série pré-preenchida pela rotina | 1 | 1 |
  | Concluir série igual à da semana passada, sem alvo de rotina | 2 toques + digitação (cerca de 6) | 1 |
  | Subir a carga um passo e concluir | cerca de 7 (campo, apagar, digitar, check) | 3 |
  | Uma repetição a menos e concluir | cerca de 4 | 3 |
  | Adicionar série igual à de cima e concluir | cerca de 4 | 2 |
  | Marcar aquecimento | 2 | 2 |
  | Remover série | 1 | 1 (deslize) ou 2 (sheet) |
  | Pular descanso / +15 s | 1 | 1 |
  | Finalizar | 2 (mais a rolagem) | 2 |

  Nenhum fluxo novo pode passar do de hoje. O de remover pela sheet (2) é a alternativa acessível do deslize (1), que empata com o de hoje.
- **Atualização por cima no emulador** (`Mooscles_Pixel8_API35`, rede desligada): com o APK da `main` (`4f2911c`), em pt-BR, em libras, criar um treino a partir de uma rotina com três exercícios, quatro séries concluídas, uma removida e o descanso correndo; exportar; instalar o APK da fase por cima. Conferir: as mesmas séries, o prazo do descanso (o dock chega a "Descanso concluído" no horário salvo), `local_preferences.preferences_pb` com o mesmo MD5, a notificação em andamento ao ir para segundo plano, finalizar e o resumo com os números certos, e o export depois igual ao de antes (tirando `exportedAt` e o treino finalizado).
- **Desempenho:** as medidas da tabela, no build R8, com a base grande, pelo procedimento do PERFORMANCE.md.
- **Capturas e crítica:** capturas do emulador da tela do treino (pendente, em edição, com teclado, com recorde, com dock, expandido, pausado), do resumo, do cartão, da barra de abas e da barra do treino, nos dois temas, em en e pt-BR e a 200%; `critique-screen` (ou `better-interface`) sobre elas, com os achados corrigidos ou adiados com motivo, num relatório `reports/AAAA-MM-DD-phase3-critica-treino.md`.
- **TalkBack e aparelho físico.** O emulador não deixou conduzir o TalkBack na fase 2. **D24 (minha). Um tipo de build `preview`** no `:app` (depurável, `applicationIdSuffix ".preview"`, nome "Mooscles Preview", updater desligado por uma flag do `BuildConfig`), que instala ao lado do app distribuído sem tocar nele nem nos dados dele. O debug distribuído não muda; o `compat-reviewer` confere isso. *Por quê:* é o único jeito seguro de passar TalkBack, háptico, notificação e 60 fps num aparelho real com uma build do Mac, cuja chave não atualiza o app do dono. Com ele, uma pessoa percorre esta lista no aparelho de referência, e o resultado vai para o relatório:
  1. a linha de série lida como um nó, com a sugestão quando houver; o toque duplo abre a edição da carga e anuncia; as cinco ações no menu de ações; o check como alternável;
  2. o stepper diz o passo e lê o valor novo;
  3. o aviso de recorde lido uma vez, de forma educada;
  4. o timer anunciado ao começar, ao ajustar e ao terminar, nunca a cada segundo; o expandido prende e devolve o foco;
  5. o toast de desfazer anunciado e alcançável antes de sumir;
  6. os diálogos de finalizar e descartar com a confirmação por último;
  7. a barra de abas ("Treinar, guia 1 de 4, selecionada") e a barra do treino;
  8. as pendências da fase 2: a barra do topo de Configurações lida primeiro (#10), a linha da versão lida como texto (#8) e o campo de descanso lido pelo rótulo (#11);
  9. o háptico de cada momento da tabela;
  10. a notificação em andamento com as ações e a tela bloqueada;
  11. 60 fps nas molas (concluir, recorde, resumo) e na rolagem, pelo `gfxinfo`.

  Sem uma pessoa com o aparelho, esses itens ficam como não verificados no relatório, como na fase 2.
- **Tokens:** `tools/check-design-tokens.ps1` limpo também no `:screens`.
- **Tamanho:** `tools/check-apk-budget.ps1` dentro do orçamento.
- **`compat-reviewer`** com `SEGURO`, e `/code-review` antes do merge.
- **Relatório:** `reports/AAAA-MM-DD-phase3-verificacao.md`, com o que **não** foi verificado: tudo num iPhone (VoiceOver, háptico, teclado real, gesto de voltar, desempenho), o caminho da release com a chave original, e os itens do TalkBack e do aparelho se ninguém os percorrer.

### Adaptações do "Pronto quando" do ROADMAP

- "Registrar uma série não leva mais toques que hoje": medido pelo orçamento de toques acima.
- "As animações mantêm 60 fps no aparelho de referência": medido com o build `preview` no aparelho do dono. Sem o aparelho, fica medido só no emulador e registrado como não verificado.
- **Entrega "card de exercício com a última performance e o alvo visíveis" (ajustado em 2026-10-29):** o alvo da rotina é o valor pré-preenchido na linha de série, como esta spec já diz. Uma linha de alvo separada ("Alvo 3×8 · 80 kg") vai para a fase 4 (rotinas visuais). Desvio do ROADMAP registrado em 2026-10-29.
- "O treino ativo funciona inteiro no iOS (build interno)": trocado pelo `ActiveWorkoutFlowTest` no simulador do CI (D6), pelo mesmo motivo das fases 1 e 2. O build interno no iPhone fica para a fase 7.

## O que fica para quem implementar

- A divisão em tasks, desde que: os spikes S1 a S3 venham antes das partes que dependem deles; cada task termine com o app compilando, os testes focados verdes e o `:screens` compilando nos dois alvos; a tela antiga só saia do caminho do treino quando a nova cobrir os testes que ela cobria.
- Os nomes exatos das APIs (`ActiveWorkoutRoute`, `AppShellBottom`, `MTabBar`, `ActiveWorkoutBar`, `UiError`, `SetRecordUi`, `PreviousSetUi`), desde que os papéis sejam os desta spec.
- Se a forma segmentada do card fica no `ExerciseCard` (parâmetro) ou em peças irmãs no `:designsystem`.
- O mecanismo do deslize (`anchoredDraggable` do `foundation`, sem Material), com os limites desta spec.
- Os valores dos itens de `UiError` além dos listados.
- A forma do `InProcessRestTimer` por dentro (ticker, relógio), desde que use o `RestTimerPersistence` sem mudar o formato.
- O desenho fino do cartão de compartilhamento dentro das regras do D20 e dos tokens.
- O que sai do `:app`: o caminho do treino ativo antigo (`ActiveWorkoutScreen`, `WorkoutTimerDock`, `TimerPanel`, o `WorkoutBottomBar` antigo e o seletor antigo, se nada mais o usar) sai quando a tela nova entra. O `WorkoutExerciseCard` e o `SetEditor` antigos continuam enquanto a edição de treino finalizado do histórico (fase 5) usar.

## Perguntas que só o dono responde

1. **Qual é o aparelho Android de referência?** (Já está em aberto no ROADMAP.) Sem ele, o "60 fps no aparelho de referência" fica medido só no emulador.
2. **Você topa instalar o "Mooscles Preview" (D24) no seu celular, ao lado do app de verdade, e percorrer a lista de TalkBack e háptico (cerca de 30 minutos)?** Ele não mexe no app instalado nem nos seus dados. Se não, esses itens continuam como não verificados, como na fase 2.

## Decisões após a leitura do construtor (2026-10-29)

Uma leitura da spec contra o código levantou sete perguntas. O dono delegou as decisões ao controlador, que respondeu assim. O texto acima foi ajustado no lugar, com a marca "(ajustado em 2026-10-29)".

1. **Datas e fusos no código comum.** Entra o `org.jetbrains.kotlinx:kotlinx-datetime` (primeira parte do Kotlin), só para as contas de fuso e calendário (`TimeZone`, `LocalDate`, `LocalDateTime`). Os nomes de mês e de dia da semana vêm dos recursos en e pt-BR do próprio app, sem formatador da plataforma. Um teste de equivalência contra o `java.time` na JVM, como os helpers da 1B. O tamanho conta no orçamento de APK da fase. Ajustados: anti-objetivos, D20 (cartão), o resumo (constância), "Textos e idiomas" e "Desempenho".
2. **"Antes" do recorde de carga.** Mostra "antes 85 kg", sem repetições, porque o domínio guarda só a carga máxima anterior (`WeightPr.previousWeightKg`). O selo e o aviso ficam exatamente na série que o `WorkoutCalculations` registra como recorde: no de carga, a de maior carga com empate pelas repetições (`maxWithOrNull`, que fica com a primeira em empate total); no de repetições, a de mais repetições naquela carga (`bestRepetitionSets`, que só troca por uma estritamente maior). Para a apresentação marcar essa série, `WeightPr` e `RepsPr` ganham o `setId` da série escolhida. O `SetRecordUi` não tem `previousReps` no recorde de carga; ele fica no de repetições, porque o domínio tem `RepsPr.previousReps`. Ajustados: o contrato de UI, "Recordes", o resumo e a tabela de voz.
3. **Casca.** Só a rota do treino ativo é tela cheia, sem abas. A galeria e as licenças mantêm a barra de abas, como hoje, e tocar na aba selecionada volta à raiz dela também de lá (é assim que o #20 se resolve). Ajustados: "Como o `:app` hospeda" e a barra de abas.
4. **Pareamento do "Anterior".** A coluna e a terceira fonte de sugestão pareiam por tipo e depois por ordem, entre as séries concluídas da sessão anterior: aquecimentos com aquecimentos, na ordem; séries de trabalho com séries de trabalho, na ordem. Ajustados: o contrato de UI e D10.
5. **"Alvo visível" do ROADMAP.** O alvo da rotina é o valor pré-preenchido na linha de série. Uma linha de alvo separada ("Alvo 3×8 · 80 kg") passa para a fase 4 (rotinas visuais), como desvio datado do ROADMAP. Ajustado: "Adaptações do 'Pronto quando' do ROADMAP".
6. **Carga zero.** Nas convenções de carga comum, 0 kg conta como vazio e recebe a sugestão de carga. Só "Added weight" e "Assistance" tratam 0 como valor real. A regra e os testes estão em D10.
7. **Erros.** O `data/` pode lançar exceções tipadas (sem mudar o comportamento, só o tipo), e a interface `RestTimer` ganha um código de erro em enum. O `MoosclesViewModel` traduz os dois para `UiError`; erro sem tipo ou desconhecido vira a mensagem genérica localizada. Isso toca áreas do `compat-reviewer` e é revisado na implementação. Ajustados: o contrato de UI, "Textos e idiomas" e "Dados e compatibilidade".

## Notas de implementação

Divergências e detalhamentos entre esta spec e o que a fase 3 entregou, uma por linha, todas de 2026-10-29. O texto aprovado acima não foi mudado: as marcas "(ajustado em 2026-10-29)" são das decisões do construtor, anteriores ao plano. Detalhes no [plano](../plans/2026-10-29-phase3-active-workout.md) (decisões travadas e "Notas"), no [relatório dos spikes](../reports/2026-10-29-phase3-spikes.md), na [verificação](../reports/2026-10-29-phase3-verificacao.md) e na [crítica do treino](../reports/2026-10-29-phase3-critica-treino.md).

### Decisões do plano que detalham ou desviam desta spec

- **2026-10-29, bloqueio por atualização tipado (decisão 3 do plano):** "A origem do código é tipada" fala só do `data/` e do `RestTimer`, mas o bloqueio vem do `UpdateCoordinator.assertTrainingMayStart()`. Ele ganhou `TrainingBlockedException` (subclasse de `IllegalArgumentException`, mesma mensagem), que o VM traduz para `UiError.BLOCKED_BY_UPDATE`.
- **2026-10-29, dois recordes na mesma série (decisão 5):** `SetUi.record` fica com o de carga e o aviso diz "recorde de carga"; o resumo lista os dois. Com as regras atuais do domínio isso não acontece, então o caso é testado com dados sintéticos.
- **2026-10-29, fonte 2 da sugestão (decisão 7 e Task 7):** "a série imediatamente acima concluída hoje" é a mais próxima acima **do mesmo grupo** (aquecimento com aquecimento; trabalho, com a assistida, com trabalho). Uma origem que é ela mesma "vazia" (0 kg em carga comum, 0 repetições) não sugere 0: a sugestão passa para a fonte seguinte, a sessão anterior. A spec não tratava esse caso.
- **2026-10-29, horas e durações (decisão 12):** relógio de 24 h nos dois idiomas, porque ler a preferência de 12/24 h do sistema seria código de plataforma. Tempo do treino "42:18" ("1:02:18" a partir de 1 h); duração por extenso "42 min" ("1 h 12 min" a partir de 60 min).
- **2026-10-29, janela de constância (decisão 13):** treinos finalizados cujo início, no fuso do aparelho, cai entre hoje − 27 e hoje, inclusive.
- **2026-10-29, aviso de recorde e edição (decisão 15 e Task 21):** das duas opções do D17, ficou "espera a edição terminar": com uma linha em edição, o aviso ainda não mostrado fica na fila. Um aviso que já apareceu e é interrompido por uma edição é descartado, não volta depois.
- **2026-10-29, skeleton por exercício (decisão 17):** o `:app` guarda a rotina do último "Começar" num decorador do `MoosclesActions` e a rota `workout` desenha um card de skeleton por exercício dela (1 para o treino vazio). Nenhuma tela antiga mudou de assinatura.
- **2026-10-29, erros nas telas novas (decisão 19):** o snackbar global do `MoosclesApp` não aparece nas rotas `workout` e `workoutDetail/{id}?summary=true`; as duas mostram o texto localizado do `UiError` num toast próprio e chamam `clearError()`.
- **2026-10-29, "Descanso concluído" (decisão 21):** o dock, o expandido e a barra do treino só mostram o fim por `timerDoneHold` quando a própria composição viu o timer passar de correndo a terminado. Um descanso que já tinha acabado ao abrir (morte do processo, app reaberto) não mostra nada.
- **2026-10-29, abrir o treino pela notificação (decisão 25 e Task 25):** chega pelo `AppViewModel.workoutRequest` e pelo parâmetro `workoutRequest` do `MoosclesApp`, sem mudar o contrato comum. Os valores são únicos entre processos (um "atendido" restaurado não engole o primeiro pedido), e o intent usa `SINGLE_TOP | CLEAR_TOP` para voltar à `MainActivity` que já está na tarefa.
- **2026-10-29, build `preview` (decisão 26 e Task 27):** `UpdateManager(enabled = BuildConfig.UPDATER_ENABLED)`, `false` só no `preview`, que não agenda, não procura e não baixa nada. O `preview` é `com.mooscles.preview`, "Mooscles Preview", `versionName` `1.4.5-preview`, assinado pela chave debug local, e mantém as permissões de instalação sem usá-las. O ícone do launcher virou vetor adaptativo (fundo, frente e monocromático); os PNGs antigos e o `tools/generate-launcher.ps1` saíram.
- **2026-10-29, `ActiveWorkoutFlowTest` (decisão 27 e D6):** roda duas vezes, claro a 1,0 e escuro a 2,0. O `captureToImage()` funcionou no simulador iOS: o job `ios` publica 28 PNG (14 estados × 2) no artefato `ios-flow-captures`.

### Spikes

- **2026-10-29, S1 (cartão comum):** decidido **A** na Task 3, com a condição de a Task 26 medir o cartão real com a mesma regra (mediana < 500 ms). O cartão real longo (3 páginas, cerca de 4.000 px de altura contra 3.200 do spike) deu mediana de 1.413 ms, o que reabriu a decisão. Medida de base: no mesmo fixture, o renderizador nativo antigo leva 931 ms e o comum 1.143 ms; o custo é a codificação do PNG (cerca de 85%, igual por pixel nos dois), não o desenho, e um cartão de uma página leva 219 ms. A regra de 500 ms tinha sido calibrada num cartão menor; o S1 continua **A**. Tudo roda fora da thread principal.
- **2026-10-29, S2 (`MTextField`, #11):** nenhuma estratégia aprovada, porque o TalkBack do emulador não pôde ser conduzido. O `MTextField` não mudou e o #11 continua aberto, para a sessão com TalkBack num aparelho.
- **2026-10-29, S3 (teclado no iOS):** no simulador, `WindowInsets.ime` fica em 0 com o campo focado. Os testes iOS usam insets injetados (`WindowInsets(bottom = 300.dp)`, decisão 22 do plano), e o teclado real no iOS fica como não verificado.

### Desvios e detalhes encontrados na implementação

- **2026-10-29, id da notificação em andamento (F1):** o D16 dizia id 2, mas o 2 já é a notificação do updater. A notificação em andamento do descanso usa o **id 3**; a de fim continua 1, no canal `rest`.
- **2026-10-29, notificação em andamento (Task 25):** pausada, ela mostra o tempo fixo com "· pausado" no subtexto (string a mais, `rest_progress_paused`). Correndo, some sozinha 2 s depois do fim (`setTimeoutAfter`), para o cronômetro do sistema não contar abaixo de zero quando o alarme inexato atrasa. As ações recusam depois de 8 s esperando o `trainingGate`.
- **2026-10-29, fundo da linha em edição (F38):** o D12 diz `bg.raised`; ficou o `bgInset` que o `SetRow` da fase 2 já desenha em edição, porque é o par de cor medido (o plano só aceita os 50 pares da fase 2).
- **2026-10-29, cards e movimento reduzido (D9, Task 17):** com movimento reduzido, recolher e abrir são instantâneos, mas a espera de 300 ms (`cardAdvance`) depois do check continua: é tempo de leitura do check, não movimento.
- **2026-10-29, títulos das colunas (Tasks 17 e 31):** os títulos Série · Anterior · kg · Reps só aparecem quando a linha de série cabe numa linha (medido pelo `SetRowColumns` do design system); com a linha em duas, somem, para nunca ficarem desalinhados ou cortados.
- **2026-10-29, coluna "Anterior" sem unidade (Task 31, A1 da crítica):** o anterior aparece como na prancha, "80 × 6", sem "kg"; o título da coluna diz kg ou lb, e a leitura falada mantém a unidade ("anterior 80 quilos por 6"). O `SetRow` passou a usar `space.1` antes do número e nada depois do check (antes, `space.2` dos dois lados; a galeria muda junto). Sem isso, a 411 dp e 1,0, qualquer série com anterior ia para duas linhas e os títulos nunca apareciam.
- **2026-10-29, nome do check concluído (Task 31, A3):** "Acessibilidade" diz "o check como alternável próprio ('Concluir série 4', marcado ou não)". Concluído, o check passa a se chamar "Série 4 concluída" ("Set 4 completed"), com a ação de toque duplo "Marcar série como não feita"; pendente, continua "Concluir série 4". Papel, estado e háptico não mudam.
- **2026-10-29, ações da linha de série (Task 12):** além das cinco ações da spec, a linha tem uma sexta, "Copiar anterior", a alternativa ao toque no "anterior".
- **2026-10-29, adicionar série depois da última (Task 29):** o orçamento de toques dá 2 para "adicionar série igual à de cima e concluir" com o card aberto, e isso se cumpre. Depois da última série de um exercício, porém, o D9 já recolheu o card, e o fluxo custa 3 toques (abrir o card, adicionar, concluir), ainda abaixo dos 4 de hoje. Aceito: um "Adicionar série" em todo card recolhido daria a cada cabeçalho um segundo alvo para um fluxo raro. `TapBudgetTest.addSetAfterTheLastSetOpensTheCardFirst` fixa os 3.
- **2026-10-29, leitura com só uma sugestão (Task 12):** a spec só dava o exemplo com as duas sugeridas. Só a carga sugerida lê "sugestão de carga 82,5 quilos, 8 repetições"; só as repetições, "carga 85 quilos, sugestão 8 repetições"; as duas, "sugestão 82,5 quilos, 8 repetições".
- **2026-10-29, série concluída com as repetições apagadas (Task 15):** a série continua concluída, as repetições ficam no último valor válido e a carga nova é gravada; o stepper de repetições de uma série concluída começa em 1. O estado otimista do check não é guardado para a morte do processo, e um erro de gravação o descarta (o disco volta ao que o banco diz).
- **2026-10-29, `Stepper` com fonte grande (Tasks 28 e 29):** os botões ficam sempre com 64 × 56 dp; o valor usa o maior de `num.lg`, `num.md` e `num.sm` que cabe numa linha. Quando nem assim cabe ao lado dos botões, o valor vai para cima deles. A escolha vem do **maior valor possível** daquele stepper e não muda enquanto ele está na tela, para os botões não pularem sob o dedo (de 99,5 para 100, por exemplo) e não cortarem a repetição ao segurar.
- **2026-10-29, fundo dos diálogos e sheets (Task 28):** o fundo que fecha um `MDialog` ou `MBottomSheet` se chama "Dispensar" ("Dismiss") e é lido por último.
- **2026-10-29, timer expandido (Tasks 20 e 31):** dentro do anel aparece só a fase ("Descanso", "Pausado", "Descanso concluído"), quebrando em duas linhas se precisar; o exercício fica no título acima. No fim, o check substitui o "0:00" no anel. O rótulo do dock é "Abrir timer de descanso, tempo restante: 1 minuto e 24 segundos" (pt-BR neutro, sem concordância de "restantes"). Depois dos 3 s de "Descanso concluído", a tela volta à série de "A seguir" (a do exercício do timer primeiro), e "A seguir" não mostra zeros: sem carga, só as repetições; sem repetições, só a série.
- **2026-10-29, timer expandido e a morte do processo (Task 20, M2 da revisão):** "Como as telas falam com o ViewModel" põe "timer expandido aberto" entre os estados em `rememberSaveable`. Ele volta depois de girar a tela, mas **não** depois da morte do processo: a primeira emissão do timer depois da recriação é o `TimerUi()` padrão, que não se distingue de "sem descanso", e o expandido fecha. Manter o "aberto" guardado seria pior: ele abriria de surpresa no próximo descanso. Se o `TimerUi` ganhar um estado "carregado", basta fechar só depois de ver o descanso real ausente.
- **2026-10-29, anúncio do timer (crítica M4 e `2ff31b4`):** o nó escondido que anuncia o timer só tem texto durante um anúncio (começar, ajustar, terminar) e é limpo depois de `timing.timerAnnounceHold`, token novo de 1.500 ms. Assim ele não é uma parada do leitor de tela entre anúncios nem lê um tempo velho. A crítica tinha deixado isso aberto por exigir uma API de anúncio da plataforma; a solução ficou sem código por plataforma. O comportamento com o TalkBack real não foi verificado.
- **2026-10-29, regra do aviso de recorde (D17, Task 21):** o aviso, o háptico de recorde e a reação só vêm do **eco de um check feito nesta tela** (a celebração pendente é consumida por ele), e só se o recorde for estritamente melhor que o melhor já anunciado nesta sessão na mesma categoria: exercício, convenção e tipo, e no de repetições também a carga. Editar ou digitar numa série concluída, ou um recorde herdado do estado ao abrir, mostra só o selo, sem aviso. Fica estacionado um caso raro: a recriação da tela no meio de um check pode tocar o háptico de concluir duas vezes.
- **2026-10-29, números do resumo (D19, Task 22):** as quatro estatísticas usam `num.lg` quando cabem; senão, as quatro descem juntas para `num.md` ou `num.sm`, medidas, para nenhum número ser cortado (a 320 dp, "3.720 kg" não cabe em `num.lg` numa coluna de cerca de 120 dp nem a 1,0). As séries e o volume contam só as séries concluídas válidas, como o cartão. A duração é "42 min" no resumo e "42:00" no cartão.
- **2026-10-29, o resumo fica com a casca (Task 24):** como "Como o `:app` hospeda" já diz, só a rota `workout` perde a casca. O resumo mostra as abas, com a aba de onde o treino foi aberto selecionada; "Concluir" leva a Treinar. Sair do treino para uma aba tira o treino da pilha, e abrir o treino a partir de uma tela aberta por ele volta ao mesmo, nunca empilha um segundo.
- **2026-10-29, cartão de compartilhamento (D20, Tasks 26 e 31):** a data do cartão é o intervalo do treino com o ano e relógio de 24 h ("qui., 22 de out. de 2026 · 20:55–21:39"), porque o cartão fica guardado. A melhor série de um exercício só sai em latão com o ícone de recorde quando ela é a própria série do recorde (anti-objetivo "Recorde errado").
- **2026-10-29, barras do sistema seguem o tema do app (`53b13ba`):** defeito anterior à fase. O `enableEdgeToEdge()` do `androidx.activity` 1.13 reaplica a cor das barras pelo modo noturno do **sistema** numa mudança de configuração que não recria a Activity (girar de 90° para 270°), e o app em escuro com o sistema claro ficava com ícones escuros na barra de status. Agora o estilo das barras lê o tema do app. Na partida a frio, as barras ainda seguem o sistema por 1 a 2 s, até as preferências carregarem (registrado, sem correção).
