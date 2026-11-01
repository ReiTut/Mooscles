# Desempenho da fase 4A (biblioteca)

Task 22, Step 4. Medido em 2026-10-30 no `emulator-5556` (AVD `Mooscles_Pixel8_API35`, Android 15, `sdk_gphone64_arm64`, 1080 × 2400 a 60 Hz, GPU do host, Mac M4), com a rede desligada, animações na escala padrão, tema escuro e libras. **Tudo aqui é emulador.** Nenhum número vale como medida de aparelho físico (ver o [PERFORMANCE.md](../../../../PERFORMANCE.md)).

## Resumo contra os orçamentos da spec

| Medida (spec, "Desempenho") | Orçamento | Resultado | Situação |
| --- | --- | --- | --- |
| Uma tecla na busca, catálogo + 300 personalizados, build R8 | mediana ≤ 4 ms, p95 ≤ 8 ms | `LibraryModel.results` por tecla, amostrado com `simpleperf`: **mediana 0,50 ms, p95 1,25 ms**, máximo 2,25 ms (144 teclas) | passou |
| A mesma, no APK de teste depurável (`LibrarySearchTimingTest`) | limite folgado (F32): mediana < 20 ms, p95 < 40 ms | **mediana 0,71 ms, p95 1,39 ms**, máximo 5,53 ms; montar o modelo, 58,8 ms | passou |
| Tocar na aba Biblioteca até a lista | o próximo quadro | O quadro seguinte ao toque (fim a 33 a 39 ms) só tem a mudança da aba. A rota da biblioteca é composta no quadro depois dele: fim a 50 a 74 ms com o modelo pronto (mediana 67), 70 a 129 ms na primeira abertura do processo (mediana 108). A aba Histórico, sem nada da fase, tem o mesmo padrão (69 a 72 ms). Nenhum SQL na thread principal | parcial (ver abaixo) |
| Rolagem rápida da biblioteca inteira com figuras, R8 | janky ≤ 5% | **0,50% a 1,47%** (mediana 0,61%) em 5 execuções, cada uma do primeiro ao último exercício e de volta | passou |
| Abrir o detalhe | o próximo quadro com a figura e os metadados | Mesmo padrão das abas: a rota do detalhe é composta no segundo quadro depois do toque, 52 a 100 ms depois dele; a figura e os metadados entram na mesma composição (síncronos no código), as dicas chegam depois | parcial (ver abaixo) |
| Figuras dos cards do treino na rolagem (pendência da Task 18) | sem piora contra a fase 3 | janky **2,31% a 3,85%** (mediana 3,09%), p95 mediano 19 ms (fase 3: 25 ms, outra lista) | passou |
| A figura do detalhe para em segundo plano e volta (pendência da Task 19) | para fora do primeiro plano | Em segundo plano, 0 quadros e 0 `doFrame` em 5 s; de volta, a figura se move de novo | passou |

Nenhuma medida reprovou no que é da biblioteca, e nenhuma correção de produto foi feita. Os dois "parcial" são da navegação da casca, comum a todas as rotas (abaixo).

## Builds e dados

- **R8:** `:app:assemblePerformance` da branch em `f4032f9`, `app-performance.apk` de 3.238.207 bytes, SHA-256 `147f364a…b79a`, `com.mooscles` 16 / 1.4.5, não depurável e `profileable`, assinado pela chave debug local. O `mapping.txt` ficou fora do git (usado para achar o nome do `LibraryModel.results`: `sa1.b`).
- **Base:** `local/mooscles-large-208-plus-300-custom.json` (fora do git), gerada por `local/make_perf_fixture.py` (fora do git): a base grande de sempre (`mooscles-large-208.json`, 208 treinos, 12.688 séries) mais 300 personalizados com nomes como "Supino variação 0 inclinado" (os mesmos do teste). Import pela tela depois de uma instalação limpa: "Imported 208 workouts, 3 routines and 302 custom exercises". A biblioteca mostrou "595 exercises" (287 do catálogo, 302 personalizados e 6 linhas não personalizadas com ids sem o prefixo `catalog_`, herdadas da base sintética; ver a nota do detalhe).
- `cmd package compile -m speed -f com.mooscles` e 30 s depois de abrir, antes das medidas.

## Busca por tecla

**No build R8.** O teste de aparelho roda no APK de teste depurável (F32), então a medida no R8 foi por amostragem: `simpleperf record --app com.mooscles -e cpu-clock -f 4000 --call-graph dwarf` enquanto "supino inclinado" era digitado letra a letra (`input text`, uma letra a cada ~0,3 s) no campo da biblioteca, 9 rodadas, com a busca limpa entre elas. O `local/sp_parse.py` (fora do git) junta as amostras da thread principal cuja pilha passa por `sa1.b` (o `LibraryModel.results`) em rajadas separadas por mais de 120 ms. Cada rodada deu exatamente 16 rajadas, uma por tecla, e todas na thread principal. Tempo por tecla = amostras × 0,25 ms de CPU, então a resolução é de 0,25 ms e o desenrolar `dwarf` acrescenta um pouco. Resultados por rodada: [`perf/search-simpleperf-rounds.txt`](perf/search-simpleperf-rounds.txt).

| | Teclas | Mediana | p95 | Máximo | Média |
| --- | ---: | ---: | ---: | ---: | ---: |
| R8, `simpleperf` | 144 | 0,50 ms | 1,25 ms | 2,25 ms | 0,67 ms |
| APK de teste depurável, `LibrarySearchTimingTest` (`SystemClock` em volta de `results`) | 144 | 0,71 ms | 1,39 ms | 5,53 ms | — |

A primeira tecla de cada rodada ("s", a que mais casa) é a mais cara nos dois. As medianas por rodada do teste foram de 0,68 a 1,28 ms. O log está em `local/library-search-timing-logcat.txt`. O teste só confere os limites folgados (mediana < 20 ms, p95 < 40 ms); a meta de 4 / 8 ms é lida daqui.

## Tocar na aba

`atrace -a com.mooscles` (categorias `gfx input view sched am wm database dalvik`), tocando na aba Biblioteca a partir de Treinar, 5 vezes com o modelo já montado no processo ("quente") e 5 vezes logo depois de `am force-stop` e da abertura ("frio": o índice de busca é montado fora da thread principal e a tela mostra o skeleton). O `local/trace_parse.py` (fora do git) toma o último `deliverInputEvent` do app como o toque e lista os `Choreographer#doFrame` seguintes que aplicam mudanças de composição.

| | Fim do 1º quadro depois do toque | Fim do quadro que compõe a rota | Maior quadro até 300 ms |
| --- | --- | --- | --- |
| Biblioteca, quente (5) | 32,6 a 39,2 ms | 49,5 / 54,1 / 66,9 / 69,5 / 73,7 ms | 10 a 34 ms |
| Biblioteca, frio (5) | 35,0 a 47,8 ms | 69,5 / 97,5 / 108,3 / 114,3 / 128,5 ms | 34 a 86 ms |
| Histórico, quente (3), para comparar | 33,9 a 51,0 ms | 69,3 / 71,5 / 71,8 ms | 22 a 45 ms |

- O primeiro quadro depois do toque tem uma recomposição pequena (a aba selecionada, 0,3 a 1 ms de `applyChanges`) e, na maioria das vezes, chega depois de um "resynced … in 22 a 29 ms" do Choreographer do emulador. A rota nova (a recomposição grande, com medida e layout de texto) entra no quadro seguinte. É o caminho da navegação da casca (o `NavHost` aplica a troca de rota depois do clique), e a aba Histórico, que esta fase não mudou, faz igual. Por isso marquei "parcial": o orçamento literal ("o próximo quadro") não vale para nenhuma aba hoje, e a biblioteca não acrescenta um quadro.
- Na primeira abertura, o quadro da rota é o maior (31 a 86 ms) e o modelo chega alguns quadros depois. Que esse quadro mostra o skeleton e não uma tela vazia não foi visto na tela (o código e o `LibraryScreenTest` provam o skeleton).
- Nenhum SQL na thread principal depois do toque, em nenhuma execução.
- Resumos: [`perf/tab-applied.summary`](perf/tab-applied.summary), [`perf/hist.summary`](perf/hist.summary). Traces de amostra: [`perf/tab-warm-1.atrace.txt.gz`](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/perf/tab-warm-1.atrace.txt.gz), [`perf/tab-cold-1.atrace.txt.gz`](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/perf/tab-cold-1.atrace.txt.gz).

## Rolagem rápida da biblioteca

Cada execução: `gfxinfo` zerado, 30 arrastos rápidos para baixo (de y = 1900 a 400 em 100 ms, 0,35 s entre eles), que chegam ao último exercício ("Zottman curl"), e 30 para cima, até o topo. Linhas de 189 px, com a figura (piloto) ou o ícone do equipamento.

| Execução | Quadros | Janky | Janky (legacy) | p50 / p95 / p99 |
| --- | ---: | ---: | ---: | --- |
| 1 | 1.771 | 26 (1,47%) | 541 (30,6%) | 17 / 23 / 31 ms |
| 2 | 1.794 | 10 (0,56%) | 0 | 17 / 19 / 21 ms |
| 3 | 1.794 | 11 (0,61%) | 143 (8,0%) | 17 / 21 / 23 ms |
| 4 | 1.799 | 9 (0,50%) | 134 (7,5%) | 17 / 21 / 23 ms |
| 5 | 1.792 | 15 (0,84%) | 388 (21,7%) | 17 / 22 / 27 ms |

O orçamento usa o "Janky frames" atual do `gfxinfo` (prazo perdido), o mesmo da fase 3. O "legacy" (quadro acima de 16 ms) varia muito de uma execução para outra neste emulador e fica só como registro. Arquivos: `perf/library-scroll-*.gfxinfo.txt`.

## Abrir o detalhe

5 execuções e mais uma, abrindo "Barbell bench press" (o do catálogo, com figura) pela lista de busca, cada uma conferida na tela depois (o menu "More options" do detalhe presente). O primeiro quadro depois do toque termina 7 a 47 ms depois dele, e a rota do detalhe é composta no quadro seguinte, que termina 52 a 100 ms depois do toque, com o maior quadro de 10 a 52 ms ([`perf/dfig.summary`](perf/dfig.summary), trace de amostra [`perf/detail-5.atrace.txt.gz`](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/perf/detail-5.atrace.txt.gz)).

- A figura (tabela no código) e os metadados (do estado) entram na composição sem espera; só as dicas (`rememberExerciseCues`) e o histórico chegam depois. Ver na tela que a figura está no primeiro quadro da rota: não verificado (a captura do emulador é lenta demais).
- **Nota:** na base grande, o "Barbell bench press" de "Usados recentemente" é uma das linhas sintéticas com id sem `catalog_`. O detalhe dele mostra o ícone da barra no lugar da figura ([print](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/perf-detail-r8.png?raw=true)), o comportamento esperado para um id fora do catálogo. O do catálogo mostra a figura no build R8 ([print](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/perf-detail-figure-r8.png?raw=true)). As primeiras 5 medidas abriram a linha sintética por engano e foram descartadas.

## Figuras dos cards na rolagem do treino

Treino vazio com 9 exercícios do piloto adicionados pelo seletor (desenvolvimento, terra romeno, elevação de quadril, barra fixa, puxada, remada na polia, elevação lateral, rosca e cadeira extensora; 18 séries), cards fechados com a figura parada ([print](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/perf-workout-cards-r8.png?raw=true); o desenvolvimento escolhido foi a linha sintética, por isso o ícone). Cada execução: 4 arrastos para baixo e 4 para cima (y 1900 → 400 em 120 ms), como na fase 3.

| Execução | Quadros | Janky | p50 / p95 / p99 |
| --- | ---: | ---: | --- |
| 1 | 260 | 10 (3,85%) | 17 / 28 / 34 ms |
| 2 | 259 | 8 (3,09%) | 17 / 19 / 20 ms |
| 3 | 259 | 8 (3,09%) | 17 / 19 / 23 ms |
| 4 | 261 | 8 (3,07%) | 17 / 20 / 22 ms |
| 5 | 260 | 6 (2,31%) | 17 / 19 / 21 ms |

A fase 3 mediu p95 mediano de 25 ms no treino "Perf 6x5" sem figuras. A lista não é a mesma, então a comparação é só de ordem de grandeza: nada indica piora. Arquivos: `perf/workout-scroll-*.gfxinfo.txt`.

## A figura do detalhe em segundo plano

Detalhe de "Barbell hip thrust" aberto, sem treino ativo ([`perf/foreground.txt`](perf/foreground.txt)):

| Momento | Medida | Resultado |
| --- | --- | --- |
| Primeiro plano, parado, 5 s | quadros desenhados (`gfxinfo`); `doFrame` em 4 s (`atrace`) | 66; 240 |
| HOME, 2 s depois, 5 s em segundo plano | quadros desenhados; `doFrame` e `Recomposer:recompose` da thread principal | 0; 0 e 0 (processo vivo) |
| De volta (`am start`), 5 s | quadros desenhados; duas capturas a 0,7 s | 65; diferentes (a figura se move) |

- Numa primeira tentativa, com um treino ativo, o segundo plano teve 15 `doFrame` em 5 s, uma recomposição com layout de texto por segundo: o relógio do treino, não a figura ([`perf/foreground-with-workout.txt`](perf/foreground-with-workout.txt)). Sem treino, zero.
- O `foreground` da tela vem do ciclo de vida da `MainActivity` (STARTED). Parar em segundo plano também é o que o próprio Compose faz quando a janela para; o que esta medida prova a mais é que a animação volta depois.
- **Observação:** em primeiro plano, parada na tela, a figura mantém o Choreographer pedindo quadro a cada vsync (240 `doFrame` em 4 s, ~60 Hz) e desenha ~13 quadros por segundo. Cada `doFrame` desses custa menos de 1 ms, mas o laço não para enquanto o detalhe está aberto. Não é orçamento da spec; fica registrado para quem olhar bateria no aparelho físico.

## Só no emulador / não verificado

- Todos os números acima são de emulador, com a CPU dividida com o Mac. Nada foi medido em aparelho físico (o aparelho de referência do ROADMAP).
- Que o quadro que compõe a rota mostra a lista ou o skeleton, e que o do detalhe mostra a figura: não visto na tela; a prova é o trace mais o código e os testes de UI.
- "Alternância da figura: só a figura recompõe" e "arrastar no editor": não medidos aqui (o primeiro é provado por contador de recomposição no `ExerciseFigureTest` e no `ExerciseInfoScreenTest`, que passaram na suíte de aparelho; o editor é da 4B).
- `present()` e o treino ativo contra a fase 3 com o mesmo roteiro (começar, check): não refeitos; a 4A não mexe na apresentação nem no caminho do check.
- Memória (PSS): não medida.
