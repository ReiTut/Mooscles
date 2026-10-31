# Desempenho da fase 5A

Task 16, Step 4. Medido em 2026-10-31 no `emulator-5558` (AVD `Mooscles_Pixel8_API35`, Android 15, `sdk_gphone64_arm64`, 1080 × 2400 a 60 Hz, GPU do host, Mac M4), com a rede desligada, animações na escala 1, inglês, libras e tema escuro (as preferências da base). **Tudo aqui é emulador.** Nenhum número vale como medida de aparelho físico (ver o [PERFORMANCE.md](../../../../PERFORMANCE.md)).

## Resumo contra os orçamentos da spec

| Medida (spec, "Desempenho") | Orçamento | Resultado | Situação |
| --- | --- | --- | --- |
| Construir o `ProgressIndex`, base grande, **build R8** (seção `Mooscles:progressIndex`) | mediana ≤ 60 ms, p95 ≤ 120 ms | 12 construções: **mediana 3,9 ms**. Nas 9 da troca kg/lb, p95 70,4 ms (pela regra do teste, a 8ª de 9), com máximo de 129,9 ms na primeira depois da abertura | passou (um caso único acima de 120 ms, ver abaixo) |
| O mesmo, no APK de teste depurável (`ProgressIndexDeviceTimingTest`, `LargeProgressHistory`, 9 rodadas) | o mesmo; o teste confere um limite folgado de 4 × | mediana **109 ms**, p95 346 ms sozinho; 126 / 168 ms na suíte inteira | acima da meta, no APK depurável (ver abaixo) |
| Consulta de gráfico, APK depurável | mediana ≤ 4 ms, p95 ≤ 8 ms | mediana 0,94 a 0,98 ms, p95 2,2 a 3,2 ms (180 consultas) | passou |
| Consulta da linha do tempo, APK depurável | o mesmo | mediana 3,2 ms; p95 8,7 ms sozinho e 13,9 ms na suíte | mediana passou, p95 acima (no APK depurável) |
| Teclas de "supino" na busca dos Recordes, APK depurável | o mesmo | mediana 3,0 a 3,1 ms; p95 7,6 ms sozinho e 14,8 ms na suíte (54 teclas) | sozinho passou; na suíte, p95 acima |
| Tocar na aba Recordes até a lista | o segundo quadro, com o índice pronto | Rota composta no **2º quadro** depois do toque, que termina 42 a 65 ms depois dele (mediana 48). Nenhuma construção do índice depois do toque: ele veio pronto do prewarm | passou (pelo trace) |
| Tocar num recorde até o gráfico | ≤ 2 quadros com o índice pronto | A rota é composta no 2º quadro depois do toque (fim a 27 a 102 ms), e o gráfico entra no quadro seguinte, o 2º da rota (fim a 51 a 108 ms) | passou contando da rota; 3 quadros contando do toque (ver abaixo) |
| Rolagem rápida da linha do tempo com "Só atuais" | janky ≤ 5% | **3,69% a 4,42%** (mediana 3,90%), 5 execuções | passou |
| Rolagem com o gráfico na tela (S1, parte c) | sem orçamento próprio (registro) | **3,58% a 3,88%** numa janela limpa; 5,14% a 6,52% numa janela que piorava | registrado |
| Trocar kg/lb | o índice refeito uma vez, com o tempo | 9 trocas, **9 construções** (uma por troca), de 2,95 a 129,9 ms | passou |
| `present()` completo da base grande (pendência da 4B) | sem mudança | Na abertura a frio, **77,9 / 93,1 / 93,7 ms**. Na troca kg/lb, 27,7 a 478,4 ms (mediana 46,8) | registrado (ver abaixo) |
| Treino ativo sem piora contra a fase 3 | nada desta fase durante uma série | Concluir duas séries: 2 `present()` (18,4 e 11,4 ms) e **nenhuma** construção do índice. A rolagem não é comparável com a da fase 3 | passou na parte que a fase toca; rolagem sem comparação |

## O ambiente

- **O que rodava ao lado.**
  - Durante toda a medida, o `crashpad_handler` do emulador zumbi `emulator-5556` gastou de 266% a 287% de CPU, ou seja, quase três núcleos. Ele não é desta lane, e a task mandou ignorar esse emulador, então não o encerrei.
  - O swap estava em 21 a 22,5 GB usados, de 22,5 a 23,5 GB.
  - A carga média ficou entre 4,3 e 9,4.
- **O controle.** É a rolagem rápida da biblioteca: 30 arrastos para baixo e 30 para cima, de y = 1900 a 550 em 100 ms, com 0,35 s entre eles. A 4A mediu de 0,50% a 1,47%.
  - Aqui deu de **2,06% a 3,29%** no começo (17:32 e 17:33) e 2,74% às 17:46, com cerca de 1.310 quadros contra os 1.790 da 4A.
  - Às 17:41 deu 23,80%. Entre 17:36 e 17:41 o ambiente piorou, e as medidas dessa janela estão marcadas abaixo.
  - Na rodada nova, às 17:56, deu 3,11% antes e 2,87% depois.
- **Medidas descartadas.** As três primeiras execuções do controle começavam o arrasto de volta em y = 400, que fica em cima do campo de busca, e a lista não voltava. Elas foram para `local/perf-invalid/` e não entram em nenhuma tabela.

## Builds e dados

- **R8:** `:app:assemblePerformance` em `3b07456` (código do app igual ao de `a0eab5f`).
  - `app-performance.apk` de **3.446.765** bytes, SHA-256 `0c3901df…a84d`. A 4B tinha 3.325.957 bytes, então a 5A acrescentou 120.808.
  - Não depurável e `profileable`, assinado pela chave debug local.
  - Antes das medidas: `cmd package compile -m speed -f com.mooscles`, e 20 a 25 s depois de abrir o app.
- **Base:** o `perf-fixture.json` da 4B, copiado para `local/` (fora do git). É a base grande (208 treinos, 12.688 séries), mais 300 personalizados e 34 rotinas, entre elas a "Drag 10x5". Importado pela tela: "Imported 208 workouts, 34 routines and 302 custom exercises".
- **Seção de trace nova** (`a0eab5f`): o cache do `MoosclesApplication` constrói o índice dentro de `Mooscles:progressIndex` (`ProgressIndexTrace`).
  - Sem ela, o tempo do índice no R8 não aparece no `atrace`, e a spec mede o orçamento no R8.
  - O `ProgressNavigationHostTest` confere, pelo contador do `ProgressIndexTrace`, que cada histórico novo é construído uma vez, kg → lb também.
  - Os traces foram lidos com `local/slices.py` (as seções) e `local/trace_parse.py` (os quadros depois do toque, o mesmo da 4A).

## O índice

### No build R8, pela seção `Mooscles:progressIndex`

| Origem | Construções (ms) |
| --- | --- |
| Abertura a frio, o prewarm 0,6 s depois do primeiro `present()` (3 aberturas) | 6,69 / 3,90 / 7,95 |
| Troca kg/lb em Configurações, 9 trocas a cada 3,8 s (`perf/units-toggle.atrace.txt.gz`) | 129,93 / 70,40 / 3,50 / 3,34 / 3,00 / 3,59 / 6,42 / 2,95 / 3,88 |

- **As 12 juntas:** mediana de 3,9 ms e máximo de 129,9 ms.
  - A única acima de 120 ms é a primeira da troca, logo depois de abrir o app. Ela coincidiu com um `present()` de 46,8 ms.
  - A segunda, de 70,4 ms, correu junto de um `present()` de 478 ms. Os dois rodam em `Dispatchers.Default`.
  - Da terceira em diante, todas ficaram entre 3 e 7 ms.
- **É o índice do histórico carregado, e não de um vazio.**
  - O prewarm só roda com o estado carregado (o `MoosclesApp` pula o `loading`).
  - Ao tocar na aba Recordes depois do prewarm, o trace não tem nenhuma outra construção, e a lista aparece cheia.
  - O número de eventos desse índice no R8 não foi lido.
- **Pendência da Task 7** ("cancelamento cooperativo se passar de ~100 ms"): no R8 o índice leva poucos milissegundos depois de aquecido. Fica sem cancelamento, e a decisão é do controlador.

### No APK de teste depurável (`ProgressIndexDeviceTimingTest`)

O teste usa o `LargeProgressHistory` (208 treinos × 61 séries, 48 exercícios, 8.616 eventos e 1.843 recordes atuais) e roda no APK de teste do módulo `:screens`, **depurável e sem R8**. Ele imprime `PROGRESS_INDEX_TIMING` e confere limites de 4 × a meta.

| Rodada | Construção (9): mediana / p95 / máx. | Gráfico (180): mediana / p95 | Linha do tempo (180): mediana / p95 | Teclas de "supino" (54): mediana / p95 |
| --- | --- | --- | --- | --- |
| Sozinho, antes das suítes (16:21) | 109,1 / 346,0 / 390,0 ms (as duas primeiras frias: 346 e 390) | 0,94 / 2,18 ms | 3,26 / 8,69 ms | 3,09 / 7,63 ms |
| Na suíte do `:screens` (16:44) | 125,8 / 167,9 / 176,3 ms | 0,98 / 3,23 ms | 3,21 / 13,92 ms | 2,98 / 14,76 ms |

- No depurável, o índice ficou acima da meta (mediana de 60 ms), e o p95 das consultas da linha do tempo também (meta de 8 ms). No R8, a mesma construção levou 30 vezes menos.
- O orçamento da spec é medido no R8 e passou lá. As consultas e as teclas **não** foram medidas no R8; no depurável, as medianas ficaram dentro da meta.
- **Conclusão honesta:** o p95 das consultas da linha do tempo e das teclas no build distribuído não está medido. Pela razão entre os dois builds no índice, ele deve ficar bem abaixo de 8 ms, mas isso é uma estimativa, não uma medida.

## Tocar na aba Recordes e num recorde

Usei `atrace -a com.mooscles` (categorias `view gfx input am sched`) e tomei o último `deliverInputEvent` do app como o toque. Os traces de amostra são [`perf/tab-records-1.atrace.txt.gz`](perf/tab-records-1.atrace.txt.gz) e [`perf/record-to-chart-5.atrace.txt.gz`](perf/record-to-chart-5.atrace.txt.gz).

| | 1º quadro (fim) | Quadro da rota (fim / duração) | Quadro seguinte (fim) |
| --- | --- | --- | --- |
| Aba Recordes, a partir de Treinar, quente (5) | 14,6 a 19,4 ms | 42,2 / 47,6 / 45,8 / 63,6 / 64,6 ms (12 a 32 ms) | 48,6 a 69,2 ms |
| Recorde → progresso, 5 linhas diferentes | 4,7 a 16,6 ms | 27,3 a 101,6 ms (6 a 74 ms; o maior foi o primeiro do processo) | **53,8 / 50,8 / 63,8 / 77,9 / 108,2 ms** |

- **Aba:** é o padrão de todas as abas desde a 4A. O primeiro quadro só marca a aba, e a rota entra no segundo. Não houve construção do índice: ele veio pronto do prewarm, que também calcula a consulta padrão da aba.
- **Recorde → gráfico:**
  - A rota é composta no segundo quadro depois do toque.
  - O quadro seguinte aplica a mudança da consulta pronta (1,3 a 3,2 ms de `applyChanges` nas linhas 1, 2 e 5). É o "gráfico no 2º quadro" da revisão da Task 9.
  - Contando do toque, são três quadros. O orçamento ("até 2 quadros") passa se a contagem começa na rota, que é como a casca conta desde a 4A.
- **Não visto na tela:** que o quadro da rota já mostra a lista, e que o seguinte mostra o gráfico. A prova é o trace, o código e os testes de UI.

## Rolagens (`gfxinfo`)

Arquivos: `perf/*.gfxinfo.txt`, cada um com a hora, a carga e o swap.

| Medida | Gesto | Execuções: janky (quadros) | p50 / p95 |
| --- | --- | --- | --- |
| Linha do tempo, "Só atuais" (até o recorde mais antigo, de 14 nov 2023, e de volta) | 20 arrastos rápidos para baixo e 20 para cima, y 1900 → 700 em 100 ms | 3,69% (840), 4,42% (837), 3,74% (856), 3,90% (847), 4,12% (849) | 17 a 19 / 21 a 26 ms |
| Progresso do supino, gráfico na tela (S1 c), **janela que piorava** (17:36 e 17:37) | 12 pares de arrastos que vão e voltam, y 1700 ↔ 1100 em 250 ms | 5,14%, 5,65%, 5,93%, 6,52%, 5,77% (~800) | 17 a 19 / 23 a 27 ms |
| O mesmo, **rodada nova** (17:56, controle a 3,11% antes) | o mesmo | **3,79%, 3,88%, 3,58%** (809 a 825) | 17 / 19 ms |
| Progresso, sessões (sem o gráfico), janela que piorava | o mesmo | 7,53%, 8,63%, 9,10% | 21 / 36 ms |
| O mesmo, rodada nova | o mesmo | **4,26%, 3,77%** | 17 / 19 a 20 ms |
| Biblioteca com o mesmo gesto de ida e volta (controle do gesto) | o mesmo | janela que piorava: 13,09% e 15,38%; Histórico: 15,95% e 17,49%. Rodada nova: **3,64%** | — |

- **S1 (c), o gráfico durante a rolagem:**
  - Na janela limpa, com o gráfico na tela, deu de 3,6% a 3,9%. É o mesmo nível do controle com o mesmo gesto (3,64%) e das sessões da mesma tela sem o gráfico (3,8% a 4,3%), ou seja, o gráfico não acrescenta jank que dê para medir aqui.
  - Na janela que piorava, ele também ficou abaixo das sessões e do controle do mesmo gesto.
- **Linha do tempo:** ficou abaixo de 5% nas 5 execuções, com o controle de 2,1% a 3,3% logo antes.

## `present()` e o treino ativo

- **`present()` completo da base grande** (pendência da 4B):
  - Na abertura a frio, o primeiro `Mooscles:present` leva **77,9 a 93,7 ms** (3 aberturas, `local/cold-*.atrace.txt`). Ele roda em `DefaultDispatch`, fora da thread principal.
  - Na troca kg/lb, que refaz a apresentação inteira, levou de 27,7 a 478,4 ms (9 trocas, mediana de 46,8 ms). Os maiores (478, 343, 255 e 148 ms) vieram da 2ª à 5ª troca, e os seguintes ficaram entre 28 e 41 ms.
  - A 1B mediu o `present()` do import da base grande (0,86 s), que não é esta medida. A fase 5A não muda a apresentação (o `git diff main...HEAD -- shared/` só acrescenta o `EstimatedOneRepMax`).
- **Treino ativo:** em "Drag 10x5", concluir duas séries com um trace aberto gerou dois `present()` (18,4 e 11,4 ms) e **nenhum** `Mooscles:progressIndex`. O índice só é refeito quando o histórico muda, e é isso que a spec pede ("nada desta fase roda durante uma série").
- **Rolagem do treino:** 4 arrastos para baixo e 4 para cima, y 1700 → 500 em 120 ms.
  - Deu 12,64% a 14,94% de janky, mas sobre só 86 a 93 quadros. O p50 ficou em 17 ms e o p95 entre 18 e 23 ms.
  - No começo do treino só o primeiro card está aberto, então a lista é curta e os arrastos chegam logo ao fim. Poucos quadros inflam a porcentagem.
  - **Não é comparável** com a fase 3 (2,3% a 3,9%, outra lista com mais quadros). Não afirmo "sem piora" pela rolagem. O que vale aqui é o trace acima.

## O que ficou só no emulador ou sem medir

- Todo número é do emulador, no Mac com o zumbi da `5556` gastando quase três núcleos e o swap quase cheio. Nada foi medido em aparelho físico.
- O teste de tempo do índice roda no APK de teste depurável do `:screens`, não no R8. O R8 foi medido só pela seção de trace do índice.
- As consultas e as teclas da busca dos Recordes **não** foram medidas no R8 (o `simpleperf` da 4A não foi repetido).
- "O quadro da rota mostra a lista" e "o quadro seguinte mostra o gráfico" não foram vistos na tela; a prova é o trace.
- A rolagem do treino contra a fase 3 não tem comparação válida.
- Memória (PSS): não medida.
