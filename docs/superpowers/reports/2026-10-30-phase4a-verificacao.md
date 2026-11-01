# Fase 4A: verificação da biblioteca de exercícios

- **Data:** 2026-10-30
- **Branch:** `feat/phase4a-library`, a partir da `main` do fork em `739a800`. Último commit de produto: `1460a58` (a crítica da Task 23); este relatório e os documentos vêm no commit seguinte, `docs: phase 4A library verified`
- **Spec:** [fase 4](../specs/2026-10-29-phase4-library-routines-design.md), com as [notas de implementação](../specs/2026-10-29-phase4-library-routines-design.md#notas-de-implementação) · **Plano:** [fase 4A](../plans/2026-10-29-phase4a-library.md), com as [notas datadas](../plans/2026-10-29-phase4a-library.md#notas) · **Spike do mapa:** [relatório](2026-10-29-phase4a-spike-mapa.md) · **Folha do piloto (G1):** [folha](2026-10-30-phase4a-folha-piloto.md) · **Crítica:** [biblioteca, detalhe, formulário e seletor](2026-10-30-phase4a-critica-biblioteca.md)
- **Evidências:** [2026-10-29-phase4a-verificacao/](2026-10-29-phase4a-verificacao/): [base do APK](2026-10-29-phase4a-verificacao/apk-base.md), [toques de hoje](2026-10-29-phase4a-verificacao/tap-baseline-4a.md), [suítes de aparelho](2026-10-29-phase4a-verificacao/device-suites.md), [atualização por cima](2026-10-29-phase4a-verificacao/upgrade.md), [sem rede](2026-10-29-phase4a-verificacao/offline.md), [desempenho](2026-10-29-phase4a-verificacao/performance.md), [tamanho do APK](2026-10-29-phase4a-verificacao/apk.md), [diff de dependências](2026-10-29-phase4a-verificacao/deps-diff.txt), [nomes em pt-BR](2026-10-29-phase4a-verificacao/nomes-pt-br.md), [tamanho das dicas](2026-10-29-phase4a-verificacao/cues-size.md) e [tamanho das poses](2026-10-29-phase4a-verificacao/figures-size.md)

## Resumo

- **Entregue:** a aba Biblioteca nova, em código comum (`:screens`, Android e iOS): lista com busca bilíngue (inglês e pt-BR, também por apelidos), filtros por músculo e equipamento, "Usados recentemente" e "Meus exercícios"; o detalhe do exercício (rota `exerciseInfo/{id}`) com a figura que alterna os dois quadros, o mapa corporal, as dicas de execução, o resumo do histórico, "Adicionar à rotina" e "Adicionar ao treino"; o formulário de exercício personalizado (sem foto, que fica para a 4B); o seletor do treino com os mesmos filtros e a mesma busca; e "Sobre o exercício" no treino ativo.
- **Dados do catálogo:** os 287 exercícios têm anatomia (principal e secundário), nome em pt-BR, apelidos de busca e quatro dicas de execução em inglês e pt-BR, todos escritos e revisados por conteúdo (sem alegação de saúde). As figuras são o **piloto de 20 exercícios**; o resto mostra o ícone do equipamento até o portão G1.
- **Design system:** o renderizador de figuras por cinemática direta (`illustration/`), o mapa corporal (adaptado do MuscleMap, MIT), `MSearchField`, `MChipKind.Menu`, `MInfoChip`, `MOverlayWindow` (as janelas herdam densidade e escala de fonte do host), tokens novos de ilustração e as licenças MIT e CC0.
- **Dados do usuário intactos:** sem migração (Room continua na v5), sem chave nova de DataStore, `versionCode` 16 e `versionName` 1.4.5 inalterados. A única mudança de contrato no `:shared` é a ação `createCustomExercise(id, …)`, que grava o exercício personalizado pelo mesmo caminho de antes.
- **Toques:** nenhum fluxo medido passa do orçamento: achar por músculo e abrir caiu de 4 para 3 toques; criar um personalizado ficou em 4 toques mais a digitação (hoje, 6 mais a digitação).
- **APK debug: +450.168 bytes** sobre a `main` em `739a800` (limite da 4A: 629.146). Nenhuma dependência nova (o diff é vazio).
- **Não verificado:** tudo num iPhone, TalkBack e 120 Hz num Android físico, a qualidade das figuras para quem treina (G1 pendente do dono), a bateria da figura do detalhe e a release com a chave original. A lista completa está [no fim](#o-que-não-foi-verificado).

## Ambiente

- Emulador `Mooscles_Pixel8_API35` (Android 15, 1080 × 2400, 420 dpi, 411 dp de largura), `emulator-5556`, num Mac M4 sem Xcode. O iOS só se prova no job `ios` do CI (`macos-26`, Xcode 26.4).
- Os APKs locais são assinados pela chave debug do Mac, que não é a das releases. A atualização por cima prova a compatibilidade dos dados entre dois builds, não o caminho do APK publicado para este build.
- O tamanho do APK compara-se ao artefato do CI da `main` em `739a800` (run 40983711502, 23.652.343 bytes): ver [apk-base.md](2026-10-29-phase4a-verificacao/apk-base.md).

## O que foi provado

### Testes

| Conjunto | Onde | Resultado |
| --- | --- | --- |
| Host dos quatro módulos | CI, run [41046481599](https://github.com/pedroyuuki/Mooscles/actions/runs/41046481599), `2784ed1` (checkpoint 3, HEAD da branch) | 935 testes em 160 classes, 0 falhas (mínimo 935) |
| `:shared`, `:designsystem` e `:screens` no simulador iOS | o mesmo run, na primeira tentativa | 259 em 26 classes, 340 em 63 e 433 em 50, 0 falhas |
| `release-script` | o mesmo run | verde |
| Contadores do CI neste HEAD | `ci.yml` | `MIN_HOST_TESTS` 935, `MIN_IOS_TESTS` 259, `MIN_DS_IOS_TESTS` 340, `MIN_SCREENS_IOS_TESTS` 433 (iguais às contagens reais do run) |
| `:designsystem:connectedAndroidDeviceTest` | emulador, `fa996b4` | 339 testes, 1 falha de dependência de ordem entre testes (`FocusRingTest`, modo de toque), corrigida em `fd5a4cc` (só código de teste) e provada com as três classes juntas: 9 de 9 |
| `:screens:connectedAndroidDeviceTest` | emulador, `fa996b4` | 430 testes, 0 falhas, 1 pulado (a captura opt-in da folha do piloto) |
| `:app:connectedDebugAndroidTest` | emulador, `fa996b4` | 25 testes, 0 falhas, 2 pulados (as fixtures de desempenho opt-in) |
| Rodadas por classe depois das correções da crítica (Task 23) | emulador | `ExerciseInfoScreenTest`, `CustomExerciseFormTest`, `LibraryAccessibilitySweepTest` e `LibraryFlowTest` 56/56; `ExercisePickerSheetTest`, `ActiveWorkoutFlowTest`, `ActiveWorkoutScreenTest`, `LibraryAccessibilitySweepTest` 64/64 e `TapBudgetTest` 13/13; cinco testes novos que falharam antes das correções |

**Adendo (2026-10-30): CI do checkpoint 3.** O run [41046481599](https://github.com/pedroyuuki/Mooscles/actions/runs/41046481599) sobre `2784ed1`, que inclui as correções da revisão final, ficou todo verde na primeira tentativa: host 935, iOS `:shared` 259, `:designsystem` 340 e `:screens` 433, e o `release-script` nas quatro combinações. As contagens batem exatamente com os mínimos do `ci.yml`.

Os testes que cobrem as exigências da spec:

- **Dados e busca (`:shared`):** `ExerciseAnatomyTest` (anatomia dos 287, o primeiro músculo principal dentro do conjunto permitido do `MuscleGroup`), `ExerciseNamesTest` e o teste de deriva contra `strings_catalog.xml`, `ExerciseSearchIndexTest` (busca nos dois idiomas, com `ExerciseSearchEquivalenceTest` fixando o `ExerciseSearch.matches` antigo contra a JVM), `CustomExerciseIdTest` e `AppViewModelCustomExerciseHostTest` (a ação `createCustomExercise`).
- **Renderizador e mapa (`:designsystem`):** `ExerciseFigureTest` (pausa fora de primeiro plano, "reduzir movimento" parando no quadro B, recomposição), `FigureHighlightDrawingTest`, `IllustrationTokensTest`, o mapa e a ponte `BodyRegionBridgeTest`, `ComponentColorsTest` (contrastes do mapa), `LicenseTextTest` e `LicensesScreenTest`.
- **Telas (`:screens`):** `LibraryModelTest` (modelo da lista), `LibraryScreenTest` e `LibraryFiltersUiTest`, `CustomExerciseFormTest`, `ExerciseInfoScreenTest`, `AddToRoutineSheetTest`, `ExercisePickerSheetTest`, `ExerciseCuesTest` (todas as 287 com dicas, filtro de alegação de saúde por palavra inteira), `ExerciseFiguresTest`, `LibraryAccessibilitySweepTest` e o `LibraryFlowTest`.
- **Casca (`:app`, host):** `LibraryNavigationHostTest`, `ShellNavigationHostTest` (inclui a aba selecionada que sobrevive à recriação acima da raiz, `a39400e`).

### O fluxo inteiro

O `LibraryFlowTest` (`screens/src/uiTest/.../flow`) percorre a biblioteca sobre o `MoosclesViewModel`, o Room e o DataStore reais: busca do supino inclinado com halter, abrir o detalhe (figura ou ícone, frase dos músculos, dicas e o histórico semeado), "Adicionar à rotina" com o toast só depois do eco gravado, voltar, o chip "Costas", abrir um resultado, uma busca sem resultado ("supino articulado" com "Costas" ligado) que oferece "Criar", o formulário preenchido e salvo e o detalhe do exercício novo. Cada passo é conferido no estado do ViewModel, que vem do banco. Nas duas execuções (claro a 1× em pt-BR e escuro a 2× em inglês) passou no emulador, com `assertInsideWindow` (F24: toasts e quadros dentro da janela). O 2× real das folhas e do formulário só existe desde a Task 21 (as janelas de diálogo tinham a densidade própria do sistema).

**No simulador iOS** o `LibraryFlowTest` passou no run [41046481599](https://github.com/pedroyuuki/Mooscles/actions/runs/41046481599) junto com o resto do `:screens` (433 de 433), e o `captureToImage` funcionou: o artefato `ios-flow-captures` tem 3,99 MB de PNGs dos estados do fluxo (o artefato expira em 14 dias e não foi copiado para o repositório).

### Orçamento de toques

A contagem de hoje foi feita na biblioteca antiga, antes da troca, com o mesmo contador ([`tap-baseline-4a.md`](2026-10-29-phase4a-verificacao/tap-baseline-4a.md)). O `TapBudgetTest` (`:screens`, emulador e simulador) conta toques sobre o ViewModel real e reprova acima da coluna "Novo" da spec ou da de hoje.

| Fluxo | Hoje (medido) | Spec, "Novo" | Medido na tela nova |
| --- | ---: | ---: | ---: |
| Achar um exercício por músculo e abrir | 4 | 3 | 3 |
| Criar um personalizado com nome, músculo e equipamento | 6 + digitação | 4 | 4 + digitação |

(Quem toca no campo do nome antes de digitar faz um toque a mais, nos dois lados da conta: 7 na tela antiga e 5 na nova.)

### Atualização por cima

`main` (`739a800`) para a branch (`f4032f9`), `versionCode` 16 nos dois, com a rede do emulador desligada o tempo todo e uma instalação com duas pastas, três rotinas, um personalizado e dez treinos importados. O banco ficou na v5; o `local_preferences.preferences_pb` ficou com o mesmo MD5; o alarme do descanso manteve o horário; o export depois é igual ao de antes (menos o `exportedAt`); a biblioteca mostrou o personalizado e os nomes em pt-BR; o treino ativo voltou com as mesmas séries. Detalhes e capturas: [`upgrade.md`](2026-10-29-phase4a-verificacao/upgrade.md). Os dois APKs têm a chave debug deste Mac; ver "Ambiente".

### Sem rede

Instalação nova com a rede desligada desde antes: a biblioteca, a busca (em português numa tela em inglês), os filtros, o detalhe (figura, mapa e dicas), o formulário e o seletor funcionaram inteiros, e o logcat não teve exceção de rede. O código acrescentado pela branch não tem nenhuma chamada de rede (busca por `http`, `URL`, `okhttp`, `ktor`, `INTERNET` e afins no diff). [`offline.md`](2026-10-29-phase4a-verificacao/offline.md).

### Desempenho (emulador, build R8, tudo sem rede)

| Medida | Orçamento da spec | Resultado |
| --- | --- | --- |
| Uma tecla na busca (catálogo + 300 personalizados) | mediana 4 ms, p95 8 ms | mediana 0,50 ms, p95 1,25 ms |
| Rolagem rápida da biblioteca com figuras | janky ≤ 5% | 0,50% a 1,47% (mediana 0,61%) |
| Figuras nos cards do treino, na rolagem | sem piora contra a fase 3 | janky 2,31% a 3,85% (mediana 3,09%) |
| Figura do detalhe em segundo plano | para | 0 quadros e 0 `doFrame` em 5 s; volta ao reabrir |
| Tocar na aba Biblioteca até a lista; abrir o detalhe | o próximo quadro | **o segundo quadro** (ver a nota abaixo) |

**O "próximo quadro" é cumprido como "segundo quadro".** O shell do app aplica toda rota um quadro depois do toque, em todo o app (a aba Histórico, sem nada da fase, tem o mesmo padrão: 69 a 72 ms contra 50 a 74 ms da biblioteca). Mudar a navegação da casca está fora do escopo da 4A; o custo é um quadro (cerca de 16 ms) em cada troca de aba. Nenhum SQL na thread principal. Ver [`performance.md`](2026-10-29-phase4a-verificacao/performance.md) e a nota datada na spec.

### Tamanho e dependências

APK debug de 24.102.511 bytes, **+450.168** sobre a base do CI (limite 629.146; sobram 178.978). Fontes e ícones dentro do orçamento (909.113 de 1.048.576). Partes estimadas: dicas 45.541 bytes comprimidos, mapa cerca de 69 mil, figuras cerca de 58 mil. Nenhuma dependência nova: as saídas de `dependencies` da branch e da base têm o mesmo SHA-256. [`apk.md`](2026-10-29-phase4a-verificacao/apk.md), [`deps-diff.txt`](2026-10-29-phase4a-verificacao/deps-diff.txt).

### Acessibilidade e fonte grande (nos testes)

O `LibraryAccessibilitySweepTest` (16 testes) confere rótulo em todo nó clicável, alvos de 48 dp, nenhum número ou botão cortado e a figura como imagem com rótulo, em 1,0×, 1,3×, 2,0× e 2,01×, a 320 e 412 dp, nos dois temas e nos dois idiomas. A ordem de leitura vem da árvore de acessibilidade, não de um leitor de tela real.

### Crítica de tela

A [crítica](2026-10-30-phase4a-critica-biblioteca.md) (Task 23, 69 capturas nas seis combinações de tema, idioma e 200%) achou 1 problema alto (a aba selecionada voltava para Treinar depois de uma recriação, corrigido em `a39400e`), 6 médios (4 corrigidos: margens do detalhe e do "Salvar", campo de busca do seletor, texto da CC0; 2 com motivo escrito: a biblioteca visível por cerca de 250 ms entre o formulário e o detalhe, e as barras de sistema cinzas do formulário no tema claro) e 13 baixos, todos com motivo.

## Decisões e desvios registrados

Estão como notas datadas na [spec](../specs/2026-10-29-phase4-library-routines-design.md#notas-de-implementação) e no [plano](../plans/2026-10-29-phase4a-library.md#notas). Em resumo:

- a elevação lateral mantém a vista de frente, uma exceção à decisão 6 do plano (uma vista de lado não mostra a abdução);
- o formulário fecha ao salvar na 4A (decisão 13); a 4B o mantém aberto até o detalhe abrir, quando a foto tornar o rascunho caro;
- o vão sob a cabeça e o braço da figura usa uma camada de composição própria (`BlendMode.Clear`), não um bitmap guardado, então a regra "sem bitmap intermediário" da D5 continua valendo;
- token novo `farLimb` para o lado de lá do corpo, distinto do equipamento e do corpo da frente;
- o secundário do mapa usa `border.strong` e o contraste exigido é contra o neutro (≥ 3:1), não primário contra secundário: o par é inalcançável com os tokens (melhor 2,15:1), então a distinção é por matiz, legenda e texto;
- o mapa corporal tem procedência registrada: vetores traçados à mão por uma pessoa sobre fotos de referência (MuscleMap, MIT, commit `170cfacd`); só eles entram no app.

## Pendências que não bloqueiam o merge

- **Lacuna da decisão 11:** o nome que o `MoosclesViewModel` grava no timer no descanso automático (e que a notificação mostra) continua o nome gravado, em inglês, porque o ViewModel não conhece o idioma; vai com as mensagens do ViewModel na fase 6. Pelo sheet de descanso, a notificação já mostra o nome em pt-BR.
- **Figura do detalhe e bateria:** durante a pausa de cada quadro, o detalhe mantém o `Choreographer` em cerca de 60 Hz (13 desenhos por segundo, medido no emulador). Medir a bateria num aparelho e considerar pausar os callbacks de quadro durante a pausa (4B).
- **Barras do sistema da janela do formulário** no tema claro (M6 da crítica; precisa de `expect/actual`, junto com o L5 da fase 3) e a biblioteca visível entre o formulário e o detalhe (M5, a 4B resolve mantendo o formulário aberto).
- **Médios da folha do piloto** (L12): agachamento a 64 dp, mãos na frente do rosto no desenvolvimento, "V" na elevação de quadril, junção na mesa flexora. Esperam a resposta do G1.
- **Busca por trecho com falsos positivos** (L6): o resultado procurado fica na primeira tela; ordenar por acerto de palavra é uma sugestão.
- `selectorized_calf_raise_seated` pode nomear máquinas diferentes em inglês ("seated calf press") e em pt-BR ("panturrilha sentado na máquina"); vai para a revisão de nomes do dono.
- Os ids de fixtures antigas dos testes (sem o prefixo `catalog_`) aparecem em inglês e com o ícone nas verificações; não acontece numa instalação real, onde o catálogo tem o prefixo.
- Os achados menores estacionados nas revisões das tasks estão nos arquivos `task-N-review.md` do ledger da fase (`.superpowers/sdd/2026-10-29-phase4a-library/`, fora do histórico publicado).

## O que não foi verificado

- **Tudo num iPhone:** VoiceOver, a figura e o mapa no aparelho, desempenho, o teclado real. O `LibraryFlowTest` e a captura dele ainda não rodaram no simulador do CI (ver acima).
- **Num Android físico:** TalkBack (nenhum dos itens de acessibilidade foi ouvido com um leitor real; a ordem vem da árvore e dos testes), fluidez a 120 Hz, bateria da figura do detalhe, Android 8 a 14 fora do 15.
- **A qualidade das figuras, das dicas e dos nomes para quem treina,** além da crítica de tela e da revisão de conteúdo por IA. O **portão G1** (o dono aprovar ou reprovar as figuras do piloto) está pendente: a folha foi entregue em 2026-10-30. O merge da 4A não espera por ele; a aprovação libera os lotes de figuras, a reprovação tira o piloto do produto (Tasks L e R do plano).
- **O #11 da fase 2** (o `MTextField` lê o valor antes do nome) no campo de busca: o `MSearchField` usa o placeholder como nome acessível, mas isso não foi ouvido com TalkBack.
- **A lacuna da decisão 11** (acima), por desenho.
- **A release com a chave original de assinatura,** que não está neste Mac; a v1.5.0 não foi publicada.
- Os resultados de CI dos commits depois de `5ed5fa7` (ver "Testes").
