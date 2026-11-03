# Fase 5A: spike S1, fundação dos gráficos (Task 1)

- **Data:** 2026-10-31
- **Branch:** `feat/phase5a-progress`, a partir de `e6ada6a`. O spike é o componente de verdade (decisão 2 do plano): o código e os testes ficam.
- **Spec:** [fase 5, D3, D4 e S1](../specs/2026-10-31-phase5-progress-history-design.md) · **Plano:** [5A, Task 1 e decisões 2, 14 e 15](../plans/2026-10-31-phase5a-charts-progress.md)
- **Código:** `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/charts/` (`ChartData`, `ChartScale`, `ChartGeometry`, `ChartFrame`, `MLineChart`, `MBarChart`, `MSparkline`, `ChartTable`, `ChartProbe`); página "Gráficos" da galeria (`gallery/pages/ChartsPage.kt`); testes em `designsystem/src/commonTest/.../charts/` e `designsystem/src/uiTest/.../charts/ChartsTest.kt` (com `ChartsTestScreen.kt`).

## Resumo

- **Veredito: Android aprovado em (a), (b) e (d), e parcial em (c), cujo `gfxinfo` vai para a Task 16; iOS aprovado no Push 1 (2026-10-31, run 41215063507: `ChartsTest` verde no simulador, 404 testes do design system, capturas `charts-{light,dark}-{1.0,2.0}.png` no artefato `ios-flow-captures`, sem texto cortado nem sobreposto). **S1 aprovado: os gráficos próprios seguem; o Vico (Task 1R) não entra.** O veredito final é do controlador (decisão 2).
- **Host (JVM): aprovado.** `ChartScaleTest` (7), `ChartGeometryTest` (3), `SizesTimingElevationTest.chartTokens`, `ComponentColorsTest` (+2) e `IconsTest` (60 ícones) verdes; o `:designsystem:testAndroidHostTest` inteiro passa (117 testes, 0 falhas), com o `StringsParityTest`.
- **Compilação:** `:designsystem:compileAndroidDeviceTest` e `:designsystem:compileTestKotlinIosSimulatorArm64` compilam. `tools/check-design-tokens.ps1` limpo, agora com `charts/` (152 arquivos).
- **Android (emulador `Mooscles_Pixel8_API35`, Android 15): aprovado.** `ChartsTest` 11/11 e `GallerySmokeTest` 13/13. Na primeira tentativa o emulador estava `offline`; o controlador o reiniciou (`emulator-5558`) e endureceu a trava, que agora sai com 5 quando o `adb` não alcança o aparelho.
- **Rodada de correção 1.** A primeira rodada no aparelho teve 10/11: `tapSelectsTheNearestPointAndDragMovesIt` parou em "0" no arraste, onde esperava "1". A causa estava no produto. O `chartSelection` só chamava `onSelect` quando o índice era diferente da seleção *composta*, e vários eventos de toque chegam antes da próxima composição: o arraste começou em 0 (reportado) e voltou para 1, que ainda era a seleção composta, então a volta foi descartada e a tela ficou em 0. Agora a comparação é com a última seleção conhecida (`SelectionTracker`: a composta, gravada a cada composição, ou a última reportada entre duas composições). Com o deduplicador exato, o `pathIsBuiltOnceWhileTheSelectionMoves` contou 19 mudanças, não 20: dois dos toques caíam na faixa dos rótulos do eixo, os dois no ponto 0 (antes passavam porque o código antigo reportava o mesmo índice duas vezes). O teste passou a tocar só dentro do gráfico.
- **iOS (simulador): aprovado no Push 1 (run 41215063507).** As capturas vão para `designsystem/build/ios-captures/charts-<tema>-<escala>.png`, no artefato `ios-flow-captures`.
- **Sem biblioteca** (D3): só `foundation` e `ui` (`drawWithCache`, `drawWithContent`, `TextMeasurer`, `detectTapGestures`, `detectHorizontalDragGestures`). Nenhum `expect/actual` novo (continuam 3 no `:designsystem`); nenhuma dependência nova.

## Critérios do S1

| Critério | Android (emulador) | iOS (simulador) | Prova |
|---|---|---|---|
| (a) Capturas dos três nos dois temas a 1,0 e 2,0 sem texto cortado nem sobreposto | aprovado: `charts-{light,dark}-{1.0,2.0}.png` gravadas e conferidas, sem texto cortado nem sobreposto | aprovado (Push 1, run 41215063507) | `capturesBothThemesAtOneAndTwo`: linha (com seleção e legenda), barras (com a semana vazia e seleção) e sparkline, claro e escuro, a 1,0 e a 2,01 (linear nas duas plataformas). Confere a altura de cada um e, quando `captureToImage` funciona, que a imagem tem a cor `chart.line`. No iOS grava o PNG; no Android grava em `/data/local/tmp/charts-<tema>-<escala>.png` (para `adb pull`). O texto dos eixos é desenhado no `Canvas`, então "não cortado" se prova olhando as capturas |
| (b) Resumo no nó do gráfico; a tabela troca e volta pelo botão | aprovado: os testes de semântica passam | aprovado (Push 1, run 41215063507) | `lineChartIsOneImageNodeWithTheSummary`, `barChartIsOneImageNodeWithTheSummary` (um nó `Role.Image` com o resumo, sem filhos, nenhum rótulo de eixo na árvore), `dataButtonSwapsChartAndTableAndBack`, `tableRowIsOneNodeAndSaysRecord`, `sparklineHasNoSemantics`. Os testes imprimem a árvore (`CHARTS semantics …`) no log |
| (c) 300 pontos sem quadro lento; o `Path` não é refeito ao mover a seleção | parcial (2026-10-31, controlador): o contador está aprovado e o `gfxinfo` fica para a Task 16, com o controle na Biblioteca; `pathIsBuiltOnceWhileTheSelectionMoves` e `resizingRebuildsThePathOnce` passam; primeiro desenho dos 300 pontos em 6,2 ms (6.151 µs, build debug). `gfxinfo` no `assemblePerformance`: não medido nesta task | contador: aprovado no contador (Push 1, run 41215063507); `gfxinfo` não se mede no simulador | `pathIsBuiltOnceWhileTheSelectionMoves`: 300 pontos, 10 toques e 10 movimentos de arraste, `LocalChartPathBuilds` conta `line` uma vez; trocar os dados conta mais uma (controle positivo). `resizingRebuildsThePathOnce`: largura nova reconstrói linha e barras uma vez cada; a seleção nas barras não reconstrói. O primeiro desenho dos 300 pontos é medido e impresso (`CHARTS first draw of 300 points: … us`), sem limite no teste (P19) |
| (d) APK debug cresce até 60 KiB pelos três componentes | aprovado: 58.084 bytes pelos componentes (ver "Tamanho") | — | `stat -f%z` do `app-debug.apk` na base e nesta task, os dois empacotados do zero na mesma máquina |

## Tamanho (d)

| APK debug | Bytes |
|---|---:|
| `e6ada6a` (base da branch, worktree limpo) | 24.469.208 |
| esta task (empacotamento do zero) | 24.535.396 |
| **crescimento do arquivo** | **66.188** |

O arquivo inteiro passa do limite de 61.440 por 4.748 bytes, mas o crescimento não é só dos três componentes. Divisão das entradas do APK (tamanho comprimido, `zipfile`):

| Parte | Bytes comprimidos |
|---|---:|
| dex, total | +77.369 |
| strings (`.cvr`, 4 arquivos) | +652 |
| **entradas, total** | **+78.021** |

E o dex de cada parte compilado sozinho (`d8 --debug --min-api 26`, zip -9; cada um com o próprio pool de strings, então é teto, não soma exata):

| Classes | dex | comprimido |
|---|---:|---:|
| `charts/` (os três componentes, a tabela, o botão, a legenda, a escala e a geometria) | 134.804 | **58.084** |
| `ChartsPage` + `ChartSamples` (galeria) + `LucideTable` + `LucideChartLine` | 44.352 | 20.528 |

**Decisão do controlador (2026-10-31):** o limite de 60 KiB do S1 conta os componentes de gráfico, 58.084 bytes, abaixo de 61.440. A página da galeria e os dois ícones são uma página de desenvolvedor e entram no orçamento da fase (D18), não no S1. Num build limpo o arquivo cresce menos que a soma das entradas (o alinhamento e os cabeçalhos mudam pouco), então a parte dos componentes no arquivo fica por volta de 48 a 58 KB. O que leva o total a 66.188 é a página da galeria (cerca de 20 KB). Um empacotamento incremental por cima do anterior chegou a 24.730.607 (+261.399): o `assembleDebug` incremental deixa espaço livre dentro do zip, então só a medida feita do zero vale.

## Como funciona

- **Escala** (`ChartScale`). Linha: a faixa dos dados com 10% de folga de cada lado (um valor só ganha ±10% dele, ou ±1), nunca abaixo de zero quando todos os valores são zero ou mais, e as marcas no passo redondo mais denso (1, 2, 2,5 ou 5 × 10ᵏ) que dá até 4 marcas dentro da faixa. As marcas são `k × passo`, com divisão por potência exata de 10 nos expoentes negativos, então em lb (decisão 15) ficam inteiras no passo. Barras: de zero até a primeira marca redonda acima do máximo, até 4 marcas, só inteiros com `integerTicks`. `labelIndices` espalha até 5 índices com o primeiro e o último; `fitLabels` tira os rótulos que se sobrepõem, sempre com o primeiro.
- **Desenho.** Cada gráfico é um `Spacer` com `drawWithCache`: eixos, rótulos medidos pelo `TextMeasurer`, `Path` da linha (ou das barras, num `Path` só) e as posições dos pontos. O cache é guardado num `remember` com os dados, os tokens e o medidor como chaves, então só é refeito quando eles ou o tamanho mudam. A seleção é desenhada por cima, num `drawWithContent` no mesmo nó, lendo as posições que o cache guardou (`PlotPositions`, sem estado); mudar a seleção recompõe o gráfico, mas não troca o modificador do cache. Nenhum bitmap intermediário.
- **Pontos.** Os pontos de 3 dp só aparecem quando ficam a pelo menos 4 raios um do outro (com 300 pontos num telefone, a linha basta); o anel de recorde aparece sempre.
- **Seleção.** Toque ou arraste horizontal escolhe o ponto ou a barra mais perto (`ChartGeometry.nearest`, empate para a esquerda) e só chama `onSelect` quando o índice muda; o arraste vertical continua rolando a tela. Nas barras, o anel fica logo acima do topo da barra, porque `accent.solid` e `chart.line` são o mesmo verde.
- **Cross-fade** (`ChartCrossfade`). Próprio, pela chave: quando `contentKey` muda, o quadro anterior fica por cima e some em `motion.base` enquanto o novo aparece; o que sai não tem semântica (só a tag `chart-fading`) nem toque. Dados novos na mesma chave só redesenham. Com movimento reduzido, troca na hora. Não usa o `Crossfade` do Compose, para que um dado novo na mesma chave não faça o conteúdo piscar.
- **Altura.** `chartHeight × fontScale`, entre 1 e `chartMaxScale` (1,5): 200, 260 e 300 dp a 1,0, 1,3 e 2,0 (`heightGrowsWithFontScaleUpToOneAndAHalf`).
- **Acessibilidade.** O gráfico é um nó `Role.Image` com o resumo (`clearAndSetSemantics`); a seleção não é anunciada. O `ChartDataButton` é um `MButton` secundário de 48 dp ("Ver dados" com `Table`, "Ver gráfico" com `ChartLine`). Cada `ChartTableRow` é um nó só (data, valor, série e "Recorde"). A sparkline não tem semântica.
- **Cores.** `ChartColors.of`: linha `chart.line`, recorde `chart.pr`, grade e guia `chart.grid`, seleção `accent.solid`, rótulos `text.secondary`. `accent.solid` sobre `bg.surface` e `bg.raised` já são os pares 53 e 54 do `ContrastTokensTest`, e `chart.line` é o mesmo primitivo que `accent.solid` nos dois temas; o par novo é `chart.pr` sobre `bg.raised` (`chartLineOnRaised`).

## O que não foi verificado

- O `gfxinfo` na rolagem da página "Gráficos" com o `assemblePerformance` e o controle da decisão 2 na Biblioteca: exigem instalar um APK no emulador, o que o implementador não faz.
- iOS inteiro (Push 1).
- Aparelho físico, TalkBack e VoiceOver de verdade.
