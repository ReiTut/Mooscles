# Fase 3: spikes S1, S2 e S3 (cartão comum, leitura do `MTextField`, teclado no iOS)

- **Data:** 2026-10-29 (medições de 2026-10-28, hora local)
- **Branch do spike:** `spike/phase3` (no `origin`, nunca mesclada), a partir da `main` em `51508a5`, com merge de `spike/phase3-s1` (`794a02c`) e `spike/phase3-s2` (`30fa606`). Commits do S3: `c81ab05` (`ImeSpikeTest` e os ajustes do CI só desta branch) e `4ab4ff1` (densidade fixa no cartão do S1, ver "S1"), os dois no `origin`; SHA final `4ab4ff1`
- **CI:** [run 40796513677](https://github.com/pedroyuuki/Mooscles/actions/runs/40796513677) em `c81ab05`, verde em todos os jobs (`host`, `ios`, `changes` e os quatro `release-script`), e [run 40797785393](https://github.com/pedroyuuki/Mooscles/actions/runs/40797785393) em `4ab4ff1`, verde (`host`, `ios`, `changes`; o `release-script` foi pulado porque nada de ferramentas mudou). Nos dois `ios`: Xcode 26.4.1 (build 17E202), 162 testes do `:shared` e 213 do `:designsystem` (mínimo 204), 0 falhas
- **Spec:** [fase 3](../specs/2026-10-29-phase3-active-workout-design.md) · **Plano:** [fase 3, Tasks 1 a 3](../plans/2026-10-29-phase3-active-workout.md)
- **Evidências (na branch do spike):** `spike-results/s1/` (XML e logcat do S1, mais `logcat-SPIKE-fixed-density.txt`), `spike-results/s2/` (linhas `SPIKE`, XML, TalkBack), `spike-results/s3/` (XML e logcat do S3) e `spike-results/ios-run-40796513677/` (os três XML do simulador do primeiro run, com as linhas `SPIKE` no `<system-out>`). Os XML do segundo run (40797785393) estão no artefato `ios-reports` dele (retenção de 14 dias) e numa cópia no workspace de execução (`.superpowers/sdd/2026-10-29-phase3-active-workout/spike-evidence/ios-run-40797785393/`); copie-os para a pasta de evidências do relatório

## Resumo

- **S1: A, cartão comum, com uma condição de tempo.** No Android, o PNG é igual ao bitmap, a thread principal responde e a mediana de desenho + PNG ficou entre 403 e 534 ms (446 ms na Task 1), em volta do limite de 500 ms. No iOS, os três testes comuns passaram, e com a mesma densidade do Android (2,625) a assinatura é a mesma: `pages=3 rows=[2, 1, 5, 2, 2, 1]`. O primeiro run comparou densidades diferentes (1 no simulador, 2,625 no emulador) e não servia; a correção `4ab4ff1` fixou a densidade, e o segundo run decidiu. **Condição do A:** a Task 26 mede de novo o cartão real com a mesma regra (mediana < 500 ms), com o `TextMeasurer` aquecido antes da primeira execução, que é fria.
- **S2: nenhuma estratégia aprovada; o `MTextField` não muda.** O `everyStrategyStaysEditable` passou no Android e no simulador iOS, mas o TalkBack do emulador não pôde ser conduzido (o foco de acessibilidade nunca se moveu). Pela regra, o #11 continua aberto e a melhor estratégia pela árvore (rótulo em `contentDescription`, valor como texto, editável) entra no item 8 da lista de TalkBack da Task 31 como "candidata do S2".
- **S3: insets injetados.** No simulador iOS, `WindowInsets.ime` ficou em 0 depois de focar o campo e esperar 2 s (`ime bottom=0`, `maxBottom=0`): o teste de UI do iOS não abre teclado. As Tasks 15 e 20 usam `WindowInsets(bottom = 300.dp)` injetado no iOS, e o teclado real no iOS fica "não verificado". Os insets injetados posicionam a caixa de 56 dp no lugar exato nas duas plataformas.

## Como foi medido

- **Android:** `./gradlew :designsystem:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.package=com.mooscles.designsystem.spike` (e `class=…` para um teste), no `Mooscles_Pixel8_API35` (emulator-5554, Android 15, 1080 × 2400, densidade 2,625), com `-Dorg.gradle.workers.max=1`. As linhas `SPIKE` saem por `println` (logcat `System.out`) e, no S2, também por `Log.i` com a tag `SPIKE`.
- **iOS:** `:designsystem:iosSimulatorArm64Test` no job `ios` (`macos-26`, Xcode 26.4.1), simulador padrão do Kotlin/Native. Os testes de `src/uiTest` entram no `iosTest` por `srcDir`. A janela do teste tem 1024 × 768 com densidade 1.
- **Ajustes só desta branch (F9):** um passo "Summarize phase 3 spikes" lista o resultado de cada teste do pacote `spike` e as linhas `SPIKE` do `<system-out>` no resumo do job; o upload `ios-reports` roda com `if: ${{ !cancelled() }}`; e o `KotlinNativeTest` do `:designsystem` mostra a saída padrão no log, uma segunda fonte das linhas.

## S1: cartão de compartilhamento em código comum

### Medidas

Densidade do cartão: 2,625 nas duas plataformas (`SPIKE_CARD_DENSITY`, a partir de `4ab4ff1`). O iOS vem do run 40797785393.

| Teste | Android (emulador) | iOS (simulador) |
|---|---|---|
| `paintsOffTheMainThreadAt1080` | passou: 3 páginas, 1080 × 1278, 1080 × 1274, 1080 × 652 | passou: 3 páginas, 1080 × 1060, 1080 × 1272, 1080 × 932 |
| `textIsDrawn` (controle) | passou: 11.362 px diferentes de `bgBase` na faixa da marca (de 86.400) | passou: 11.265 px diferentes (de 86.400) |
| `layoutSignature` | `pages=3 rows=[2, 1, 5, 2, 2, 1]` | `pages=3 rows=[2, 1, 5, 2, 2, 1]`, igual |
| `pngMatchesThePreviewBitmap` | passou: `mismatchedPixels=0` nas 3 páginas (PNG de 140.755, 116.162 e 50.226 bytes) | só Android |
| `paintAndEncodeUnder500ms` | medianas 446 (Task 1), 534, 445, 499 e 403 ms: 4 de 5 abaixo de 500 | só Android |
| `mainThreadStaysResponsive` | passou: a thread do desenho não é a principal, `Runnable` em 0 ms | só Android |

O controle `textIsDrawn` pode reprovar nas duas plataformas: o fundo é pintado com `bgBase` (`drawRect(c.bgBase)`), e a maior parte da faixa bate exatamente com ele (75.038 px no Android, 75.135 no iOS). Um cartão sem texto daria 0.

As alturas das páginas não são as mesmas nas duas plataformas (a soma dá 3.204 px no Android e 3.264 no iOS), embora o número de páginas e as linhas por exercício sejam iguais: a altura de linha e o corte entre páginas variam um pouco com a métrica da fonte de cada plataforma. A regra compara páginas e linhas, então isso não muda a decisão, mas o cartão da Task 26 não pode depender de uma altura de página exata.

### A primeira comparação e a correção

No primeiro run (40796513677, em `c81ab05`), o `captureSpikeInputs()` usava `Density(LocalDensity.current.density, 1f)`, a densidade da tela do teste: 1 no simulador e 2,625 no emulador. O cartão tem 1080 px em qualquer aparelho, mas os estilos estão em sp, então o texto do iOS saiu 2,6 vezes menor em px. O resultado foi `pages=1 rows=[1, 1, 2, 1, 1, 1]` (uma página de 1080 × 1184, 1.940 px de texto na faixa da marca), que não podia dar igual ao Android. A diferença vinha da densidade do teste, não da métrica da fonte, que é o que a regra compara. Pelo Step 3, um teste assim não serve para decidir.

A correção `4ab4ff1` fixa `SPIKE_CARD_DENSITY = 2.625f` (a densidade do emulador) nas duas plataformas. No emulador nada mudou: rodada de novo, a assinatura continuou `pages=3 rows=[2, 1, 5, 2, 2, 1]`, com o PNG igual e a thread principal livre. No iOS, o run 40797785393 deu a mesma assinatura.

### Decisão

**A: o cartão de compartilhamento é desenhado em código comum** (`CanvasDrawScope` + `drawText`, fora da composição), e a Task 26 segue esse caminho, não o plano B do D20. As três condições do Android e as duas do iOS da regra passaram.

**Condição:** o tempo no Android ficou em volta do limite. A Task 1 mediu 446 ms. Nesta task, com outro agente usando o mesmo Mac (carga de ~5,5), a suíte inteira deu 534 ms (o `paintAndEncodeUnder500ms` reprovou nessa rodada), e três rodadas só desse teste deram 445, 499 e 403 ms. A primeira execução de cada rodada, fria, ficou entre 573 e 868 ms. Por isso a Task 26 mede de novo o cartão real com a mesma regra (mediana de 5 execuções de desenho + PNG < 500 ms), aquecendo o `TextMeasurer` (e as fontes) antes da primeira execução. Se o cartão real passar do limite, a Task 26 volta a esta decisão em vez de aceitar um cartão lento.

**2026-10-29, nota da Task 26 (a decisão voltou e ficou A).** O cartão real passou do limite: com o fixture de treino longo (3 páginas, cerca de 4.000 px de altura, contra cerca de 3.200 px do cartão do spike), a mediana de desenho + PNG no emulador foi 1.413 ms (execuções 1.540, 1.390, 1.530, 1.413, 1.440, 1.322 e 1.323 ms). Antes de decidir, uma medida de base no mesmo fixture comparou o renderizador nativo antigo do `:app` com o comum: 931 ms contra 1.143 ms no total (desenho 59 contra 159 ms; PNG 854 contra 964 ms; 9.034 contra 10.696 px de altura). Por pixel, os dois custam o mesmo (0,103 e 0,107 ms/px), e a codificação do PNG é cerca de 85% do tempo nos dois. Um cartão de uma página, o caso comum, leva 219 ms (138 ms no antigo). A regra de 500 ms tinha sido calibrada num cartão menor, e o custo que passa dela é o PNG, que o plano B também pagaria. A decisão continua **A**. O desenho e a codificação rodam fora da thread principal.

## S2: `MTextField` lendo o rótulo antes do valor (#11)

| Estratégia | Nó editável (Android) | Edição no Android | Edição no iOS |
|---|---|---|---|
| `Today` | `EditText text=2:30`, sem `contentDescription`; o rótulo é um `TextView` filho | passou | passou |
| `ContentDescription` | `EditText text=2:30`, `cd=null`; o `cd=label` fica num `View` filho | passou | passou |
| `StateDescription` | igual ao anterior; `stateDescription` não chega ao `EditText` | passou | passou |
| `TextProperty` (a mais) | o nó vira `TextView` (ainda editável) e o rótulo some | passou | passou |
| `MergedParent` (a mais) | `EditText` sem mudança; o rótulo some da árvore | passou | passou |

"Passou" é o `TextFieldEditSpikeTest.everyStrategyStaysEditable`: `SetText` presente, digitar "2:30" muda o valor e a ação de IME existe, para as cinco estratégias. No iOS rodou no run 40796513677.

**TalkBack:** o TalkBack 15 está na imagem do emulador e foi ligado (`touchExplorationEnabled=true`), mas Tab, DPAD_RIGHT, Alt+DPAD_RIGHT e deslizes por `input swipe` nunca moveram o foco de acessibilidade (`isAccessibilityFocused` falso por 60 s), e nenhuma fala foi capturada. Depois, o TalkBack foi desligado de novo.

**Decisão:** nenhuma estratégia é aprovada, porque a regra exige o TalkBack real dizendo o rótulo antes do valor. O `MTextField` não muda, e o #11 fica registrado de novo. A candidata do S2 para o item 8 da Task 31 é `ContentDescription` (rótulo em `contentDescription`, valor como texto, editável). A árvore mostra, porém, que nem `contentDescription` nem `stateDescription` chegam ao `EditText` no Compose 1.12.1: o rótulo continua num filho, depois do valor. Uma correção de verdade provavelmente pede outro mecanismo (o rótulo antes do campo na ordem de travessia, ou alguém testando com TalkBack num aparelho).

## S3: teclado no iOS

| Teste | Android (emulador) | iOS (simulador) |
|---|---|---|
| `focusingAFieldRecordsTheImeInset` | passou (foco). `ime bottom=63` px (24 dp), `maxBottom=63`, `fieldVisible=true` | passou (foco). `ime bottom=0`, `maxBottom=0`, `fieldVisible=true` |
| `injectedInsetsPlaceTheCompactDockAboveThem` | passou: fundo da caixa em 1612, esperado 1612,5 (2400 − 787,5); o campo foi de fora da tela para 1339–1465, logo acima da caixa (1465–1612) | passou: fundo da caixa em 468, esperado 468 (768 − 300); o campo foi para 364–412, logo acima da caixa (412–468) |

- Os dois testes usam uma coluna rolável com 2000 dp de espaço e um `BasicTextField` de 48 dp no fim, e uma caixa de 56 dp presa acima dos insets. A espera de 2 s do primeiro é em tempo real (`TimeSource.Monotonic`), avançando um quadro por volta, porque o teclado do sistema anima no próprio relógio.
- **Controle do segundo teste:** antes do `bringIntoView`, o campo precisa estar fora da tela, senão o teste reprova. Ele reprovou de fato na primeira versão, que contava como visível o retângulo vazio que o `boundsInRoot` devolve para um nó cortado pela rolagem. A versão final exige a altura inteira de 48 dp.
- **Android:** os 63 px (24 dp) não são um teclado: o emulador está com teclado físico (`show_ime_with_hard_keyboard=1`, `mInputShown=false`), e o inset deve ser a barra do IME. O S3 não depende disso.
- **Decisão:** `ime bottom = 0` no simulador, então **os testes iOS das Tasks 15 e 20 usam os insets injetados** (`WindowInsets(bottom = 300.dp)`, decisão 22 do plano), e o teclado real no iOS fica "não verificado".

## O que não foi provado

- **S1:** o tempo do cartão real (o spike mede um cartão aproximado, e a mediana fica perto do limite; a Task 26 mede de novo). A qualidade visual do cartão: o spike desenha só texto (sem anilhas nem selo) e mede pixels, não aparência. O tempo num aparelho real (só emulador) e no iOS (o teste de tempo é só do Android).
- **S2:** a fala real do TalkBack, que não pôde ser conduzido no emulador; nenhuma estratégia foi ouvida.
- **S3:** o teclado num iPhone (não há iPhone) e o teclado real no simulador (o teste de UI do Kotlin/Native não abre um). O teclado de software no emulador Android também não abriu (teclado físico ligado na imagem).
