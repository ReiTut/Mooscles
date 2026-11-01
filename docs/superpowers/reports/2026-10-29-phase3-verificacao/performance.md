# Desempenho da fase 3 (tela do treino ativo)

Task 30, Step 4. Medido em 2026-10-29 no `emulator-5556` (AVD `Mooscles_Pixel8_API35`, Android 15, `sdk_gphone64_arm64`, 1080 × 2400 a 60 Hz, GPU do host, Mac M4 com carga média de 4 a 6), com a rede do emulador desligada, animações na escala padrão, tema escuro e libras. **Tudo aqui é emulador.** Nenhum número vale como medida de aparelho físico (ver o [PERFORMANCE.md](../../../../PERFORMANCE.md)).

## Resumo contra os orçamentos da spec

| Medida (spec, "Desempenho") | Orçamento | Resultado | Situação |
| --- | --- | --- | --- |
| Toque no check até o disco cheio | o próximo quadro, sem trabalho de banco ou de apresentação no caminho do toque | 5 de 5 toques: o primeiro quadro depois do toque terminou 20,9 a 43,4 ms (mediana 32,4) depois do início do evento de toque, com uma recomposição e sem `Mooscles:present` nem SQL na thread principal. O `present()` começou depois desse quadro (54 a 90 ms depois do toque), numa thread de fundo | passou, com a ressalva abaixo |
| Ticks do timer | zero recomposições de cards e linhas | `WorkoutTimerTest.timerTicksRecomposeNoCardOrRow` e `AppShellBottomTest.timerTicksDoNotRecomposeTheTabs` passaram no emulador em `e482e1d` ([XML](recomposition-per-tick.xml)) | passou |
| "Começar" até o primeiro quadro com skeleton | o próximo quadro | O primeiro quadro depois do toque terminou 31,6 a 101 ms depois do toque (mediana 61,5; a `main` teve 59,8). Que esse quadro já mostra o skeleton **não foi provado na tela**: a captura de tela mais rápida do emulador leva ~200 ms | parcial |
| "Começar" até o treino editável (R8) | sem piora acima do ruído contra a 1B | mediana 306 ms na branch e 252 ms na `main`, 10 execuções de cada, em ordem ABBA. A diferença, +54 ms, fica dentro do ruído (teste de permutação das medianas, p ≈ 0,23; faixas de 182 a 535 e de 179 a 354 ms) | passou, com ressalva |
| Rolagem rápida do treino grande, p95 do quadro | até 1,10 × a base, no mesmo emulador | p95 mediano de 25 ms na branch e de 26 ms na `main` (limite 28,6 ms). A base antiga do PERFORMANCE.md (p95 de 42,9 a 47,2 ms) era de outro emulador, com renderização por software | passou |
| `present()` depois de concluir uma série | sem piora contra a 1B | mediana 6,0 ms na branch (5,4 a 13,3) e 14,1 ms na `main` (8,3 a 16,8) | passou |
| Animações (molas) | 60 fps no aparelho de referência | só no emulador: veja `gfxinfo` abaixo | não verificado no aparelho |

Nenhuma medida reprovou, e nenhuma correção foi feita.

## Builds e dados

- **Antes ("1B"):** `assemblePerformance` da `main` em `4f2911c` (o merge da fase 2, que já contém a 1B), SHA-256 `4fc7cb31…b774`. A spec pede a comparação "contra a 1B". A tela do treino da `main` é a mesma da 1B (a fase 2 não a mudou), então a `4f2911c` serve de base e tem o mesmo código de dados e de apresentação que esta branch herdou.
- **Depois:** `assemblePerformance` da branch em `e482e1d`, SHA-256 `a7eaf25c…c7c4`.
- Os dois são `com.mooscles` 16 / 1.4.5, R8, não debuggable e `profileable`, assinados pela chave debug local. Foram trocados com `install -r`, sem limpar os dados. Antes de cada bloco rodei `cmd package compile -m speed -f com.mooscles` e esperei 30 s depois de abrir.
- **Base grande:** `mooscles-large-208.json` (de `.superpowers/scratch-backup/files/`, gerado pelo `make_backups.py` de 2026-10-27: 208 treinos × 6 exercícios, 12.688 séries, lb, tema escuro, descanso automático **desligado**), mais uma rotina "Perf 6x5" (6 exercícios × 5 séries, descanso de 120 s) acrescentada por um script fora do repositório. O import foi feito pela UI, depois de um `pm clear` (só no emulador): "Imported 208 workouts, 4 routines and 2 custom exercises".
- **Ordem:** a `main` mediu Começar 1 a 5, rolagem e checks. Depois a branch mediu Começar 1 a 5, rolagem, checks, recorde e resumo, e Começar 6 a 10. Por fim, a `main` mediu Começar 6 a 10. Com isso o histórico tinha um treino a mais (o "Perf 6x5" finalizado pela branch) nos dois últimos blocos, que são um de cada lado.

## Método

- `atrace -a com.mooscles` com as categorias `gfx input view sched am wm database dalvik`, em execuções curtas, lido por um script descartável que monta as fatias de cada thread.
  - O toque é o evento assíncrono `deliverInputEvent` do app (o último antes da ação).
  - O "próximo quadro" é o primeiro `Choreographer#doFrame` da thread principal que começa depois do fim desse evento.
  - O trabalho de banco são as fatias `prepare …` e `execute…` do SQLite do framework, categoria `database`.
  - O "treino editável" é o fim do primeiro quadro da thread principal que começa depois do fim do `Mooscles:startSession`. O comando só termina quando o treino salvo chega ao estado da tela (`contentUi.first { it.active?.id == id }`), então esse quadro é o primeiro que pode mostrar o treino pronto.
- `dumpsys gfxinfo com.mooscles` zerado antes de cada interação e lido depois. Os percentis são do tempo de quadro do próprio Android.
- Resultados por execução: [`perf/trace-results.json`](perf/trace-results.json) e `perf/*.gfxinfo.txt`. Traces de amostra (comprimidos): [`perf/main-start-3.atrace.txt.gz`](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/perf/main-start-3.atrace.txt.gz), [`perf/branch-start-3.atrace.txt.gz`](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/perf/branch-start-3.atrace.txt.gz), [`perf/main-check-2.atrace.txt.gz`](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/perf/main-check-2.atrace.txt.gz) e [`perf/branch-check-6.atrace.txt.gz`](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/perf/branch-check-6.atrace.txt.gz). Os 24 traces completos (265 MB) ficaram fora do git, em `app/build/outputs/phase3-verificacao-20261029/`.

## Toque no check

5 toques em séries pendentes do treino "Perf 6x5" (na branch, as séries 1, 1, 1, 2 e 3 do supino; ver a nota), com o treino e a base grande carregados.

| Execução | Fim do próximo quadro (ms depois do toque) | Duração do quadro | `present` / SQL na thread principal nesse quadro | Primeiro SQL (ms, thread) | `present()`: começo, duração (ms) |
| --- | --- | --- | --- | --- | --- |
| branch 1 | 43,4 | 35,6 | 0 / 0 | 32,0, `arch_disk_io_0` | 89,8, 13,3 |
| branch 3 | 20,9 | 5,6 | 0 / 0 | 3,6, `arch_disk_io_2` | 53,9, 5,5 |
| branch 5 | 32,4 | 14,4 | 0 / 0 | 2,0, `arch_disk_io_0` | 53,0, 5,4 |
| branch 6 | 37,8 | 23,0 | 0 / 0 | 3,1, `arch_disk_io_3` | 84,9, 6,0 |
| branch 7 | 30,7 | 26,7 | 0 / 0 | 3,9, `arch_disk_io_1` | 90,1, 7,0 |
| **branch, mediana** | **32,4** | | | | **—, 6,0** |
| `main` (5) | 22,1 / 24,5 / 26,8 / 44,2 / 27,1 | 5,0 a 30,0 | 0 / 0 | 1,5 a 23,9 | 64 a 149, 8,3 a 16,8 (mediana 14,1) |

- Em todos, o quadro seguinte tem um `Recomposer:recompose`. Nenhum SQL rodou na thread principal em nenhum momento depois do toque, e o `present()` rodou sempre em `DefaultDispatch`, depois do quadro seguinte.
- O disco cheio nesse quadro vem do estado otimista da tela (`ActiveWorkoutState.optimistic`, gravado no clique, e `completedOf`), e o `SetCheck` já desenha o disco cheio na primeira composição, na escala inicial da mola. A recomposição no quadro seguinte é a prova no trace. Não há prova na tela, quadro a quadro: o emulador não captura rápido o bastante.
- **Ressalva:** a escrita no banco começa numa thread de IO 2 a 32 ms depois do toque e, nos 5 toques da branch, corre ao mesmo tempo que o quadro seguinte, em outra thread. Em 3 deles, a leitura completa do repositório depois da escrita também. Nada disso está na thread principal nem atrasa o quadro, mas disputa CPU com ele. Leio o orçamento da spec ("no caminho do toque") como a thread principal.
- **Nota:** os toques 2 e 4 da branch acertaram o check de uma série já concluída (a descrição "Complete set 1" não muda quando a série é concluída) e desmarcaram a série. Ficaram fora da tabela e estão em `uncheck_excluded` no JSON. Os toques 6 e 7 os substituíram, escolhendo só séries cuja linha diz "not completed".

## Começar

"Start" da rotina "Perf 6x5" em Treinar, com a lista já rolada até ela, 10 vezes por build. Entre uma vez e outra, o treino foi descartado.

| | Fim do 1º quadro depois do toque | `startSession` | Treino editável | Maior quadro até lá | `present()` |
| --- | --- | --- | --- | --- | --- |
| branch, mediana (mín.–máx.) | 61,5 (31,6–101,0) | 257 (151–404) | **306 (182–535)** | 39,8 (26,6–125,5) | 13,5 |
| `main`, mediana (mín.–máx.) | 59,8 (42,5–79,6) | 225 (164–303) | **252 (179–354)** | 40,1 (22,3–58,4) | 12,4 |

- Teste de permutação da diferença das medianas: treino editável +54 ms, p ≈ 0,23. `startSession` +32 ms, p ≈ 0,20. A branch ficou acima em 69 dos 100 pares. Dentro do ruído deste emulador, mas a direção é de piora. Vale repetir num aparelho físico, com mais execuções.
- A execução mais lenta da branch (535 ms, a 6ª, primeira depois da troca de APK e da compilação) teve um `present()` de 47 ms e um quadro de 125 ms. A 5ª teve um `present()` de 41 ms. Os outros oito `present()` da branch ficaram entre 10 e 21 ms.
- Nenhum SQL na thread principal antes do treino editável, nos dois builds.
- O skeleton: o código navega no próprio clique (`onWorkout()` junto do `startWorkout`) e a rota mostra `WorkoutSkeleton` enquanto `startingWorkout` está ligado, o que o `ShellNavigationHostTest.startNavigatesAtOnceAndShowsOneSkeletonPerRoutineExercise` prova no host. No emulador eu só medi o tempo até o primeiro quadro depois do toque, que ficou igual ao da `main`. Não vi o skeleton nesse quadro.

## Rolagem rápida

Treino "Perf 6x5" ativo e aberto. Cada execução: `gfxinfo` zerado, 4 arrastos rápidos para baixo (de y = 1900 a 400 em 120 ms) e 4 para cima, com 0,4 s entre eles.

| Execução | `main`: quadros, janky, p95 | branch: quadros, janky, p95 |
| --- | --- | --- |
| 1 | 132, 10 (7,6%), 28 ms | 240, 14 (5,8%), 30 ms |
| 2 | 130, 9 (6,9%), 26 ms | 266, 10 (3,8%), 25 ms |
| 3 | 128, 11 (8,6%), 34 ms | 264, 15 (5,7%), 24 ms |
| 4 | 130, 9 (6,9%), 21 ms | 265, 10 (3,8%), 22 ms |
| 5 | 130, 9 (6,9%), 26 ms | 265, 12 (4,5%), 26 ms |
| **Mediana** | **p95 26 ms** | **p95 25 ms** (limite 1,10 × 26 = 28,6 ms) |

O p50 foi de 17 a 18 ms nos dois, o ritmo de 60 Hz. A branch desenha o dobro de quadros pelos mesmos gestos, porque a lista nova é mais longa (cards com linhas mais altas) e o fling anda mais. O conteúdo rolado não é o mesmo nos dois builds: a tela mudou. Comparar com o PERFORMANCE.md não serve, porque aquelas medidas (p95 de 42,9 a 47,2 ms) foram feitas em outro emulador, outro computador e com renderização por software. Por isso a base é a `main` medida agora, no mesmo emulador.

## Molas (`gfxinfo`)

Só a branch tem as molas novas. Valores do emulador:

| Interação | Quadros | Janky | p50 / p90 / p95 / p99 |
| --- | --- | --- | --- |
| Concluir série (5 toques, `gfxinfo` de 3 s cada) | 11 a 16 | 0 a 2 por toque (5 em 70 no total) | p95 de 13 a 69 ms (mediana 34) |
| Recorde: série de 600 lb × 8, com o selo "PR" ([print](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/perf-branch-pr.png?raw=true)) | 59 | 2 (3,4%) | 17 / 21 / 25 / 26 ms |
| Resumo: "Finish" no diálogo até a tela "Workout complete" ([print](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/perf-branch-summary.png?raw=true)) | 68 | 4 (5,9%) | 21 / 29 / 32 / 48 ms |

O resumo mostrou 4 séries, 1 exercício e 7.682,54 lb, que bate com a soma das quatro séries concluídas (132,28 × 8 + 137,79 × 7 + 143,3 × 6 + 600 × 8 = 7.682,57 com os valores arredondados da tela; o app soma em kg). Os 60 fps no aparelho de referência não foram verificados: o emulador divide a CPU com o Mac.

## Recomposições por tick

`./gradlew :screens:connectedAndroidDeviceTest` filtrado pelos dois testes da Task 20 e da Task 23, no `emulator-5556`, em `e482e1d`: 2 testes, 0 falhas ([XML](recomposition-per-tick.xml)).

- `WorkoutTimerTest.timerTicksRecomposeNoCardOrRow`: depois de 10 emissões do timer, as contagens de `card-*` e `row-*` não mudam, e a do `dock` sobe.
- `AppShellBottomTest.timerTicksDoNotRecomposeTheTabs`: a contagem das abas não muda, e a da barra sobe pelo menos 10.

## Só no emulador / não verificado

- Todos os números acima são de emulador, com a CPU dividida com o Mac. Nada foi medido em aparelho físico.
- Que o quadro seguinte ao Começar mostra o skeleton e que o quadro seguinte ao check mostra o disco cheio, vistos na tela: não verificado. A prova é o trace (o que roda nesse quadro), mais o código e os testes de host.
- Memória (PSS) e ciclos repetidos de começar e descartar: não medidos nesta task.
- A rolagem com o timer contando: não medida (a base grande tem o descanso automático desligado).
- O crescimento do APK debug é do `tools/check-apk-budget.ps1` no CI, não desta task.
