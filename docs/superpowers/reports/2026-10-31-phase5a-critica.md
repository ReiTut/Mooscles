# Fase 5A: crítica dos Recordes, do progresso do exercício e do card com a sparkline (Task 17)

Data: 2026-10-31. Branch `feat/phase5a-progress`, a partir de `77d3895`. Spec: [fase 5](../specs/2026-10-31-phase5-progress-history-design.md). Plano: [fase 5A](../plans/2026-10-31-phase5a-charts-progress.md), Task 17. Formato das críticas da [4A](2026-10-30-phase4a-critica-biblioteca.md) e da [4B](2026-10-30-phase4b-critica-rotinas.md).

## Entrada e método

- **Aparelho:** só o `emulator-5558` (AVD `Mooscles_Pixel8_API35`, Android 15, `en-US`, 1080 × 2400, 420 dpi, 411 dp de largura). Nenhum aparelho físico.
- **Build:** o APK debug de `77d3895`, instalado por `:app:installDebug` sobre o build R8 de desempenho da Task 16. Depois das correções, o debug da branch foi instalado de novo por cima, com os mesmos dados.
- **Dados:** `pm clear com.mooscles`, depois a base grande importada pela tela de Configurações, com acréscimos feitos por script (fora do git):
  - os 208 treinos de nov. 2023 a jun. 2024 (seis exercícios com barra, convenção Carga total);
  - 18 treinos de jul. a out. 2026 com a barra fixa pronada (Carga adicional: repetições de 5 a 12 e, nas últimas sessões, carga de 2,5 a 10 kg), a barra fixa assistida na máquina (Assistência: de 45 a 22,5 kg) e uma sessão só de rosca direta com halteres (Por mão);
  - a base usava ids de catálogo sem o prefixo `catalog_`, e o app os tratava como outros exercícios, com o nome gravado em inglês também em pt-BR (é o que aparece nas capturas da Task 16). O script acrescenta o prefixo; com ele, os nomes aparecem traduzidos;
  - "Imported 226 workouts, 3 routines and 2 custom exercises". As preferências vieram do arquivo: kg e tema do sistema.
  - As capturas `records-empty-*` e `progress-empty-*` (o supino Arnold, nunca feito) são de antes da importação.
- **Capturas** em [`2026-10-31-phase5a-verificacao/`](2026-10-31-phase5a-verificacao/), por `adb exec-out screencap -p`, em PNG original:
  - Recordes: `records-timeline-…`, `records-current-…` ("Só atuais"), `records-search-…` ("pull"), `records-empty-…` e `records-rules-…` (a sheet "Como contamos recordes");
  - progresso: `progress-load-…` (supino, Carga, período Tudo, um ponto por semana), `progress-e1rm-…`, `progress-table-…` ("Ver dados"), `progress-bodyweight-…` (barra fixa, Repetições), `progress-assistance-…` (barra assistida, métrica Assistência), `progress-one-session-…` (rosca) e `progress-empty-…`;
  - `info-sparkline-…`: o card "Seu histórico" do supino, com a sparkline;
  - `gallery-charts-…`: a página "Gráficos" da galeria, e `gallery-charts-300-light-en.png`, a amostra de 300 pontos;
  - cada uma nas seis combinações `-light-en`, `-dark-en`, `-light-pt`, `-dark-pt`, `-light-pt-200` e `-dark-pt-200` (84 capturas, mais a de 300 pontos);
  - **as 85 capturas são de antes das correções.** As de depois são as `after-*.png`, citadas em cada achado;
  - tema por `cmd uimode night` (o app no tema "Sistema"), idioma por `cmd locale set-app-locales com.mooscles`, 200% por `settings put system font_scale 2.0`. Cada troca recria a atividade; a rolagem foi refeita por script quando a troca de idioma a perdia.
  - Em `records-search-*`, o botão de Configurações aparece com o anel de foco. É efeito do `adb shell input keyevent 4` usado para fechar o teclado: uma tecla tira o app do modo de toque. Com o gesto de voltar não acontece.
- **iOS:** as capturas do `ProgressFlowTest` e do `ChartsTest` vêm do artefato `ios-flow-captures` do run verde do Push 2, o [41232962795](https://github.com/pedroyuuki/Mooscles/actions/runs/41232962795). Copiei três para a pasta: `ios-progress-pt-light-1x-load.png`, `ios-progress-en-dark-2x-records.png` e `ios-charts-light-2.0.png`. A hierarquia, as cores, os anéis e a quebra a 2× são os mesmos do Android; nada nelas pediu correção própria.
- **Skills:** `kit-design` (o roteador) e depois `critique-screen` (hierarquia, composição, tipografia, cor, affordance e densidade), sobre os Recordes, o progresso (cada convenção e a tabela) e o card com a sparkline. Prevalecem a spec da fase 5, as decisões do plano e do controlador, os tokens e o `AGENTS.md`. Nenhuma fonte, paleta ou biblioteca de ícones nova.
- **Prioridade:**
  - P1 (alta): quebra o uso, a acessibilidade, a spec ou o design system;
  - P2 (média): piora a experiência ou cria inconsistência;
  - P3 (baixa): acabamento.
  - P1 e P2 foram corrigidos em commits próprios, com teste, e capturados de novo. Os P3 têm o motivo escrito.

## P1: alta

| # | Problema | Dimensão | Princípio | Situação |
| --- | --- | --- | --- | --- |
| A1 | **A semana começava na segunda no app de verdade, com o idioma do app em `en`.** O emulador é `en-US`, cuja semana começa no domingo. A correção da Task 16 (rodada 2, `8bddc89`) lia a região do aparelho no `Resources.getSystem()`, mas o idioma por app chega também aos recursos do sistema do processo, que passaram a dizer `en` sem região. Em inglês, o primeiro ponto semanal do supino era "week of Nov 13" e o destaque dizia "+5 kg since the week of Nov 13". Em pt-BR, "semana de 12 nov" e "+7,5 kg". A mesma pessoa via números diferentes conforme o idioma. Com `--locales en-US`, o app mostrava 12 de novembro. O `CurrentWeekStartDeviceTest` passava porque só trocava o `Locale.setDefault`. Captura: `progress-load-light-en.png`. | Spec (D8), consistência | A semana começa no primeiro dia da região do aparelho (D8). | **Corrigido** em `d704237` (`designsystem:`). No Android 13 ou mais novo, o `deviceRegion()` lê o `LocaleManager.getSystemLocales()`, que o idioma por app não muda. Ele passou a ser `@Composable` para ter o contexto; antes do 13 não há idioma por app na plataforma, e ele continua no `Resources.getSystem()`. No iOS, nada muda. Teste novo: `CurrentWeekStartDeviceTest.aPerAppLanguageWithoutRegionKeepsTheSystemRegion`, que liga um idioma por app real (`LocaleManager.applicationLocales = en`) no APK de teste. Antes da correção, ele falhou com "expected:<US> but was:<null>". Nota datada na spec, sob o D8. Captura: `after-progress-load-light-en.png` ("week of Nov 12"). |

## P2: média

| # | Problema | Dimensão | Princípio | Situação |
| --- | --- | --- | --- | --- |
| M1 | **Datas de mais de um ano sem o ano** no "desde", na linha de leitura, na tabela e na "Última vez" do card. Com a base de 2023 e 2024, o destaque dizia "+5 kg since the week of Nov 13", a tabela "Week of Jun 3" e o card "Última vez 8 jun", que leem como datas deste ano. As sessões e os recordes do progresso já levavam o ano (Task 10). É o item levado 6. Capturas: `progress-load-*`, `progress-table-*`, `info-sparkline-*`. | Tipografia, texto | Uma data só leva dia e mês quando não há dúvida sobre o ano. | **Corrigido** em `4d5c529` (`screens:`). O "desde", a linha de leitura e a tabela usam `chartDate`/`chartWeekOf` (`ScreenDates.kt`), que é o `shortDateAged`. O card do detalhe usa o `shortDateAged`. Os rótulos do eixo X continuam curtos (L3). Testes: `ProgressTextsTest.datesAYearOrMoreAgoCarryTheYearAndDoNotBreak` e `ExerciseInfoScreenTest.anOldLastTimeHasItsYear`. Capturas: `after-progress-table-light-pt-200.png` e `after-info-sparkline-light-pt.png`. |
| M2 | **A 200%, a data quebrava entre o dia e o mês**: "Semana de 26" numa linha e "mai" na outra, na tabela, e "+7,5 kg desde a semana de 12" e "nov" no destaque. Capturas: `progress-table-light-pt-200.png`, `progress-load-dark-pt-200.png`. | Tipografia | Um número não se separa da palavra que ele data (o mesmo princípio da 4B, M3). | **Corrigido** junto com a M1: o `chartDate` escreve a data com espaços que não quebram, então a data inteira desce de linha. Os testes de texto esperam o U+00A0 dentro das datas. Captura: `after-progress-table-light-pt-200.png`. |
| M3 | **A mesma medida tinha três nomes na barra fixa.** Em pt-BR, a convenção dizia "Peso adicional" (o nome da fase 3), a métrica e a seção de recordes diziam "Carga adicional", e a aba Recordes dizia "Carga" ao lado de "10 kg × 5". Esse 10 kg é o peso somado ao corpo, e "Carga" lê como carga total. Capturas: `progress-bodyweight-light-pt.png`, `records-timeline-light-pt.png`. | Texto, consistência | Um conceito, um nome; o recorde e o gráfico que ele abre usam a mesma palavra. | **Corrigido** em `4d5c529`. A métrica em pt-BR passou a "Peso adicional", o nome da convenção (o inglês já era "Added weight" nos dois lugares). Na aba Recordes, um recorde de carga de um exercício de Carga adicional diz "Peso adicional", na linha e na fala (`recordKindLabel(kind, convention)`). Nota no fim deste relatório para a spec (a tabela do D6 diz "Carga adicional"). Teste: `ProgressTextsTest.spokenWeightsAddedWeightAndBodyweightRecords` e `metricLabelFollowsTheConvention`. Captura: `after-records-timeline-light-pt.png`. |
| M4 | **No progresso de um exercício de Assistência, os recordes de repetições liam "22,5 kg × 10"** ("22,5 quilos por 10" na fala), como se fossem carga. A aba Recordes já escreve "22,5 kg de assistência × 10" (Task 11). Captura: `progress-assistance-*` (a seção "Recordes deste exercício", abaixo da dobra). | Texto, consistência | O número de ajuda nunca se lê como carga, e as duas telas dizem a mesma coisa do mesmo recorde. | **Corrigido** em `4d5c529`. Num exercício de Assistência, os recordes de repetições usam a forma de assistência, na linha e na fala. As séries assistidas de outros exercícios continuam sob "Repetições assistidas", com "série assistida" na fala (Task 10, I1). Teste: `ExerciseProgressSectionsTest.assistanceRepsRecordsSayAssistance`. Captura: `after-progress-assistance-records-light-pt.png`. |
| M5 | **"12 repetições Repetições" no destaque.** Na barra fixa e na barra assistida, a métrica Repetições escrevia o nome ao lado de um valor que já o diz. Captura: `progress-bodyweight-light-pt.png`. | Densidade, texto | Não dizer duas vezes a mesma coisa lado a lado. | **Corrigido** em `4d5c529`: na métrica Repetições o destaque não repete o nome. As outras métricas continuam com ele ("92,5 kg Carga", como a spec pede). Teste: `ExerciseProgressScreenTest.repsHighlightDoesNotRepeatTheMetric`. Captura: `after-progress-bodyweight-light-pt.png`. |
| M6 | **Anéis de recorde empilhados numa faixa de latão.** Na amostra de 300 pontos da galeria, os anéis de 6 dp ficavam a cerca de 1 dp um do outro e cobriam a linha. No app, o mesmo acontece com uma sequência de recordes num gráfico denso (até 104 pontos, D3). É o item levado 1, Minor 4 da Task 1. Captura: `gallery-charts-300-light-en.png`. | Cor, composição | Uma marca de exceção só funciona se for exceção; um selo que cobre a linha esconde o dado. | **Corrigido** em `17484d6` (`designsystem:`). O `ChartGeometry.ringIndices` mantém, do recorde mais novo para trás, só os anéis que não encostam no último mantido. O mais novo, o que ainda vale, sempre tem anel, e a tabela lista todos os recordes. Teste: `ChartGeometryTest.ringsThatWouldTouchAreSkippedKeepingTheNewest`. Captura: `after-gallery-charts-300-light-en.png`. |
| M7 | **Leituras de peso erradas para o leitor de tela.** "1 quilos" e "1 kilograms" (item levado 2); "Peso corporal × 12" lido com o "×" na aba Recordes, enquanto toda outra série é lida com "por"; e um recorde assistido sem assistência registrada (0 kg) sairia "0 kg de assistência" (item levado 4). | Acessibilidade, texto | A fala usa as palavras da língua, no número certo, e o mesmo padrão em toda série. | **Corrigido** em `35cd292` (as unidades) e em `4d5c529` (o resto). As unidades faladas são plurais (`spokenWeight` e `pluralCount`): só exatamente 1 fica no singular, e decimais e 0 ficam no plural. Todos os pontos que montam o peso falado passam por ele. Uma série sem carga é lida "peso corporal por 12", e um recorde assistido de 0 kg sai como peso corporal, na linha e na fala. Testes: `SpokenWeightTest` (os dois idiomas, com 1, 2, 1,5 e 0, em kg e lb) e `ProgressTextsTest.spokenWeightsAddedWeightAndBodyweightRecords`. Sem captura: é fala. |

## P3: baixa

| # | Problema | Dimensão | Situação e motivo |
| --- | --- | --- | --- |
| L1 | **Um vão de cerca de 60 dp entre os chips de período e o gráfico** (`progress-load-*`). | Composição | É a linha de leitura, que guarda o lugar mesmo sem seleção, para o gráfico não pular quando um ponto é tocado (decisão da Task 9). |
| L2 | **Na tabela da Carga, o valor se repete na série** ("37,5 kg" e, embaixo, "37,5 kg × 11"). | Densidade | A spec (D4) pede o valor e a série em cada linha, e no 1RM a série é diferente do valor. Tirar a série só da Carga deixaria a coluna irregular entre métricas. |
| L3 | **Os rótulos do eixo X no período Tudo não têm ano** ("Nov 13 … Jun 3", atravessando 2023 e 2024). | Texto | O eixo tem até 5 rótulos e pouco espaço; a tabela e o resumo falado têm as datas com o ano (M1). |
| L4 | **O eixo Y às vezes tem só 2 marcas** (30 e 40 na assistência, 50 e 100 na carga), com a linha passando acima da marca de cima. | Composição | É a regra da escala (D3: folga de 10% e marcas redondas, até 4). Funciona; mais marcas é ajuste fino da `ChartScale`. |
| L5 | **A redundância do cabeçalho do progresso**: "Por mão" em negrito e, embaixo, "Por mão: registre a carga de um halter." | Densidade | A legenda é a mesma do card do treino e do detalhe do exercício (enum traduzido pela tela, spec "Textos"). Mudar a frase muda três telas. |
| L6 | **Os chips "Só atuais" e "Primeiros registros" aparecem na aba vazia** (`records-empty-*`), sem efeito. | Affordance | O estado vazio diz o que fazer; esconder os chips mudaria a altura do topo entre os estados. |
| L7 | **Os chips de período aparecem com uma sessão só** (`progress-one-session-*`). | Densidade | Trocar o período ainda muda o destaque; esconder os chips é mudança de spec. |
| L8 | **As linhas de sessão não têm chevron** (item levado 8). | Affordance | Decidido sem chevron: as linhas da biblioteca e da aba Recordes também não têm, a linha inteira é o alvo, tem `Role.Button` e o estado pressionado. Um chevron só nas sessões seria a exceção. |
| L9 | **Os botões de texto "Ver todos os 40" e "Ver progresso" ficam recuados** em relação à margem da lista. | Composição | É o preenchimento interno do `MButton` de texto, igual em todo o app; alinhar só aqui criaria outra regra. |
| L10 | **A faixa cinza sob a sheet "Como contamos recordes"** no tema claro (`records-rules-light-*`). | Cor | É o M5 da 4B (janela de ponta a ponta), que pede um `expect/actual` no `MOverlayWindow`; continua com o controlador. |

## Itens levados

| Item | Origem | Veredito |
| --- | --- | --- |
| 1. Minors da Task 1 (gráficos) | `task-1-review.md` | **Corrigidos** em `17484d6`: (1) o desenho em cache numa `graphicsLayer` própria; (3) `chartDotSpacing` virou token, fixado em `chartTokens`; (4) os anéis (M6); (5) KDoc da seleção síncrona, do `onSelect(null)` e dos rótulos de X; (6) um `uiTest` com o gráfico dentro de `verticalScroll` (`verticalSwipeScrollsAndHorizontalSwipeSelects`); (7) o teste renomeado para `chartColorsMapToTheirTokens`; (8) os contadores conferem as diferenças depois de um primeiro build de ao menos 1; (9) a cópia da captura no Android avisa quando falha; (10) o comentário do `ci.yml` diz onde ficam os PNGs no artefato. **Não corrigido:** (2), as alocações pequenas por quadro de arraste (um `Stroke` e um iterador). Com a camada de (1), o arraste já não regrava o desenho, e a medida da Task 16 deixou o gráfico dentro do orçamento (3,6% a 5,4%). |
| 2. Unidade falada no plural | Task 8 | **Corrigido** (M7), em `35cd292`. |
| 3. Teste do rótulo do `PrBadge` | Task 11 (a) | **Corrigido** em `35cd292`: `PrBadgeTest.customLabelIsDrawnAndSpoken` e `customLabelAloneKeepsTheSpokenDefault`. |
| 4. Recorde assistido de 0 kg | Task 11 (b) | **Corrigido** (M7), em `4d5c529`: sai como peso corporal. |
| 5. A borda de um ano | Task 10 | **Corrigido** em `4d5c529`: `datesAYearOrMoreAgoShowTheYear` ganhou 31 out 2025 (com o ano) e 1 nov 2025 (curto). |
| 6. Ano na tabela e no "desde" | Task 10 | **Corrigido** (M1). |
| 7. O estado vazio pisca num índice velho | Task 10, Minor 4 | **Não era coberto** pela M1 da Task 13, que só trata o `state.loading`. **Corrigido** em `4d5c529`: o `ProgressIndex` sabe de que listas foi feito (`isOf`), e o progresso só conclui "Você ainda não fez este exercício" com o índice do histórico atual; antes disso, o `Skeleton`. Teste: `ExerciseProgressSectionsTest.firstWorkoutNeverFlashesTheEmptyState`. |
| 8. Chevron na linha de sessão | Task 10, Minor 9 | Decidido sem chevron (L8). |
| 9. Ordem dos imports e testes da sparkline | Task 12 | **Corrigido** em `4d5c529`: os imports do `ExerciseInfoScreen.kt` em ordem (e sem o `Saver` que não era usado); o teste da sparkline confere o próprio nó (sem texto, descrição, papel nem filhos); `noSparklineForSessionsOfAnotherConvention` e `noSparklineNorSkeletonWhileTheIndexBuildsOrAfterItFails`. |
| 10. Rótulo do selo cortado a 200% | Tasks 14 e 15 | **Conferido nas capturas:** em `records-*-pt-200.png`, "PR" e "Primeiro" aparecem inteiros dentro do selo, nos dois temas. |

## Notas para o fim da fase (spec e plano)

- **D6 (a tabela de métricas):** a métrica da convenção Carga adicional se chama "Peso adicional" em pt-BR, o nome que a convenção já tinha (M3). Na aba Recordes, o recorde de carga desse exercício diz "Peso adicional".
- **D8:** a região do aparelho vem do `LocaleManager.getSystemLocales()` no Android 13 ou mais novo (A1); a nota datada já está na spec.
- **Progresso do exercício:** o destaque da métrica Repetições não repete o nome (M5); o "desde", a leitura e a tabela levam o ano numa data de mais de um ano e não quebram a data (M1, M2); num exercício de Assistência os recordes dizem "de assistência" (M4).
- **Gráfico de linha:** anéis de recorde que encostariam no anterior não são desenhados; o mais novo sempre é (M6).

## Avaliação geral

A dimensão mais forte é a hierarquia do progresso. O nome lidera, a métrica escolhida aparece num chip só cheio, o valor em `num.lg` é o ponto de entrada, e o gráfico vem depois dos chips de período, sem cor de alerta nem seta. Nos dois temas só há tokens: o verde da linha, o latão do recorde e o cinza da grade. A 200%, nada fica cortado: os chips rolam, o gráfico cresce até 1,5 vez e os selos "PR" e "Primeiro" ficam inteiros.

A mais fraca era o texto: a mesma medida com três nomes, a ajuda da máquina lida como carga, datas de 2023 lidas como deste ano e a fala com "1 quilos". O defeito mais sério não aparecia em teste. Em inglês, a semana do app começava num dia diferente do português, e o destaque mudava de número com o idioma. Tudo isso foi corrigido.

## Verificação

No `emulator-5558` e no host, depois das correções (commits `d704237`, `17484d6`, `35cd292` e `4d5c529`):

- **Vermelho antes da correção:** só o `CurrentWeekStartDeviceTest.aPerAppLanguageWithoutRegionKeepsTheSystemRegion` foi rodado no código antigo, com o `DeviceRegion` de `77d3895`: falhou com "expected:<US> but was:<null>". Os outros testes novos conferem textos e estados que o código antigo não produzia ("Peso adicional", "de assistência", o ano, o U+00A0, o `Skeleton` no lugar do vazio), mas **não foram rodados no código antigo**.
- **`:designsystem:connectedAndroidDeviceTest`, pelo gate:** `ChartsTest`, `PrBadgeTest`, `SpokenWeightTest`, `GallerySmokeTest`, `ChartGeometryTest` e `ComponentColorsTest`. A primeira rodada pegou o teste novo de rolagem: o gesto vertical vinha antes e o fling tirava o gráfico da tela. Reordenado (horizontal antes), o `ChartsTest` passou inteiro.
- **`:screens:connectedAndroidDeviceTest`, pelo gate:**
  - primeira rodada: `ProgressTextsTest`, `ExerciseProgressScreenTest`, `ExerciseProgressSectionsTest`, `ExerciseInfoScreenTest`, `RecordsScreenTest`, `CurrentWeekStartDeviceTest`, `ExerciseListTest`, `SetEditingTest`, `WorkoutSetRowTest` e `ProgressFlowTest`. Deram 172 de 173. A que falhou foi `selectingAPointShowsTheReadout`, que ainda esperava "3 jun" com espaço comum;
  - segunda rodada, depois do ajuste: `ExerciseProgressScreenTest`, `ProgressAccessibilitySweepTest`, `TapBudgetTest` e `RoutineEditorSetsTest`, 94 de 94.
- **Host:** `:designsystem:testAndroidHostTest` deu 118 de 118 e `:screens:testAndroidHostTest` deu 292 de 292, com `--rerun-tasks`.
- `:designsystem:compileTestKotlinIosSimulatorArm64`, `:screens:compileTestKotlinIosSimulatorArm64` e `:app:compileDebugKotlin`: compilam. Os testes iOS só rodam no CI.
- `tools/check-design-tokens.ps1`: limpo (167 arquivos).
- **Mínimos do CI:**
  - `MIN_HOST_TESTS` 1259 → 1260;
  - `MIN_DS_IOS_TESTS` 405 → 411;
  - `MIN_SCREENS_IOS_TESTS` 878 → 886;
  - uma linha por classe no comentário de cada um.
- **Capturas de depois** (`after-*.png`, no APK debug de `4d5c529`, com os mesmos dados):
  - `after-progress-load-light-en.png` ("since the week of Nov 12, 2023");
  - `after-progress-table-light-pt-200.png` ("Semana de" e, inteira na linha de baixo, "2 jun 2024");
  - `after-info-sparkline-light-pt.png` ("8 jun 2024 · 37,5 kg × 11");
  - `after-progress-bodyweight-light-pt.png` ("Peso adicional" no chip e na seção, e "12 repetições" sem o nome repetido);
  - `after-progress-assistance-records-light-pt.png` ("22,5 kg de assistência × 10");
  - `after-records-timeline-light-pt.png` ("Peso adicional" ao lado de "10 kg × 5");
  - `after-gallery-charts-300-light-en.png` (os anéis separados).
- **Não verificado:**
  - TalkBack e aparelho físico (a fala foi conferida pela árvore de semântica e pelos testes);
  - nada em iPhone nem no simulador iOS nesta tarefa, além de compilar;
  - as suítes inteiras de aparelho e de host do `:app`: o ciclo rápido roda só as classes tocadas, e a suíte inteira roda no CI;
  - o desempenho do gráfico depois da `graphicsLayer` (Task 1, Minor 1): não medi de novo; o contador de builds do `ChartsTest` continua igual;
  - `CurrentWeekStartDeviceTest` só no Android 15; antes do Android 13 o caminho antigo (`Resources.getSystem()`) não foi executado.

## Estado do emulador

Restaurado no fim:

- escala de fonte 1,0;
- modo noturno do sistema desligado (como encontrei);
- idioma do app sem override (`[]`; encontrei `[en]`, e a tarefa pedia para limpar);
- as escalas de animação em 0 (como encontrei).

Ficaram:

- o APK debug de `4d5c529`, que trocou o build R8 de desempenho;
- os dados da base de capturas (226 treinos) e o arquivo `mooscles-5a-capture.json` em `/sdcard/Download`;
- a atualização 1.4.6 (build 17) baixada pelo updater do debug e recusada na verificação ("different signer"): o app no emulador é assinado pela chave debug do Mac, como esperado.
