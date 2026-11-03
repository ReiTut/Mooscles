# Recordes fora da thread principal: verificação no emulador

- **Data:** 2026-10-27
- **Branch:** `fix/pr-off-main-thread`, código verificado em `22bf8d5`
- **Spec:** [2026-10-27-pr-off-main-thread-design.md](../specs/2026-10-27-pr-off-main-thread-design.md)
- **Plano:** [2026-10-27-pr-off-main-thread.md](../plans/2026-10-27-pr-off-main-thread.md)
- **Antes:** números da [verificação do export/import](2026-10-27-export-import-verificacao.md)
- **Prints:** [2026-10-27-pr-off-main-thread-verificacao/](2026-10-27-pr-off-main-thread-verificacao/)

## Resumo

O travamento acabou. Com a base grande, o APK debug não bloqueia mais a thread principal por causa da apresentação: abertura a frio, import, troca kg/lb e um treino iniciado e finalizado rodaram com toques na tela o tempo todo, e **nenhum ANR** apareceu (`dumpsys activity lastanr`: "no ANR has occurred since boot" no fim de todas as execuções). Durante o `present()`, o maior quadro da thread principal ficou entre 75 e 542 ms (os de 509 e 542 ms são o quadro do próprio toque no botão de unidade), e o quadro que desenha o conteúdo pronto ficou entre 212 e 592 ms.

**Uma meta não foi cumprida.** Na abertura a frio do APK debug, o histórico só aparece **15 a 20 s depois do primeiro quadro** (mediana de ~17,5 s em 6 execuções sem toques; 25 a 29 s com toques contínuos). A meta da spec é 3 s. A tela não trava: ela mostra o indicador de carregamento e continua respondendo. Esse tempo vem do próprio `present()`, que no processo recém-aberto leva de 12 a 24 s na thread de fundo (no host, ~86 ms). No mesmo build, com o processo já aquecido, o mesmo cálculo leva de 1,1 a 7 s. No build R8 (`assemblePerformance`), com a base de 48 exercícios, o histórico aparece em **2,3 e 3,1 s**. Não ajustei código: fica para o controlador decidir.

## Metas da spec

| Meta (APK debug, base grande, `Mooscles_Pixel8_API35`) | Resultado |
| --- | --- |
| Nenhum ANR, mesmo tocando na tela durante a abertura e o import | **Cumprida.** 0 ANR em 6 execuções com toques contínuos (2 aberturas com 6 exercícios, 1 com 48, 3 imports) e nas trocas de unidade e no treino, também com toques |
| Nenhum quadro da thread principal bloqueado > 700 ms por causa da apresentação | **Cumprida.** Nenhum quadro acima de 700 ms durante o `present()` em nenhuma execução. O maior foi o quadro que desenha o conteúdo depois do `present()`: 592 ms (abertura com toques). Na abertura ainda há bloqueios de 0,8 a 4,2 s, mas eles acontecem **antes** do `present()` começar (ver [Bloqueios que não são da apresentação](#bloqueios-que-não-são-da-apresentação)) |
| Histórico visível em até 3 s depois do primeiro quadro na abertura a frio | **Não cumprida no debug.** 14,9 a 20,1 s sem toques (6 exercícios), 15,7 a 19,7 s (48 exercícios). No R8, 2,3 e 3,1 s (48 exercícios) |

## Antes e depois

"Antes" vem do relatório do export/import (`feat/export-import`, apresentação na thread principal). "Depois" é este branch. Todos os números são do emulador.

| Cenário | Antes | Depois (debug) |
| --- | --- | --- |
| Abertura a frio, 6 exercícios | thread principal travada 48,7–52,1 s (`Davey!`); ANR ao tocar | thread principal livre durante o `present()` (maior quadro 210–395 ms); histórico visível 14,9–20,1 s depois do primeiro quadro; sem ANR com toques |
| Abertura a frio, 48 exercícios | não medido | histórico visível 15,7–19,7 s depois do primeiro quadro; maior quadro durante o `present()` 237–271 ms; sem ANR com toques |
| Abertura a frio, R8, 48 exercícios | (R8, 6 exercícios: travamento de 9,0 s) | histórico visível 2,3 e 3,1 s depois do primeiro quadro; `present()` 1,7–1,8 s |
| Import da base grande numa instalação limpa | 122–147 s até a mensagem; travamentos de 47–77 s + 65 s; ANR ao tocar | `present()` 1,1 s em segundo plano; histórico apresentado ~6–7 s depois do toque em Import; maior quadro 278–283 ms; sem ANR com 93–127 toques |
| Import da base de 48 exercícios | não medido | `present()` 1,7 s; ~8 s do toque ao histórico; maior quadro 202 ms; sem ANR com 127 toques |
| Troca lb ↔ kg em Configurações | 61,7 s de travamento | `present()` 1,2–6,9 s em segundo plano; maior quadro 79–542 ms (o de 509–542 ms é o próprio toque no botão); sem ANR |
| Iniciar e finalizar um treino curto | não medido | `present()` 1,2 s ao iniciar, 0,85 s ao completar uma série e 3,5 s ao finalizar; maior quadro 75–197 ms durante esses cálculos; nenhum `Skipped`/`Davey!` no logcat do primeiro treino; sem ANR com 46–55 toques |

## Ambiente e método

- **Emulador:** `Mooscles_Pixel8_API35` (`emulator-5554`), Android 15, 1080×2400, 4 núcleos, 2,5 GB de RAM, num Mac com M4. A carga média do emulador variou entre 1 e 8 durante a sessão.
- **APK:** `./gradlew :app:assembleDebug` (versionCode 16, debuggable), instalado com `adb -s emulator-5554 install -r`. Para a comparação, `./gradlew :app:assemblePerformance` (R8, não debuggable) foi instalado por cima com a mesma chave local; no fim o debug foi reinstalado.
- **Base grande, 6 exercícios:** o mesmo arquivo da verificação do export/import (`mooscles-large-208.json`, gerado por `.../scratchpad/gen/make_backups.py`): 208 treinos × 6 exercícios × 61 séries = 12.688 séries, `pounds = true`, tema escuro.
- **Base grande, 48 exercícios:** variante gerada por um script descartável fora do repositório (`.../scratchpad/v4/make_varied.py`), com os mesmos 208 treinos e 12.688 séries, mas sobre os 48 primeiros exercícios do catálogo (todos de barra: `TOTAL_LOAD`, `ONE_SIDE` e `BODYWEIGHT`), igual ao `LargeHistoryFixture.variedExercises(48)` do host. São 9.984 linhas de exercício de treino.
- **Abertura a frio:** `am force-stop com.mooscles`, 2 s de espera, `logcat -c`, `am start`. O **primeiro quadro** é a linha `Displayed com.mooscles/.MainActivity` do `ActivityTaskManager`.
- **Trace:** `atrace --async_start -c -b 131072 -a com.mooscles view input am wm` antes de cada cenário. Ele registra as seções `Mooscles:present` (thread `DefaultDispatch`) e os `Choreographer#doFrame` da thread principal. O parser descartável (`.../scratchpad/v4/parse_trace.py`) converte os tempos do trace para o relógio de parede pelo `trace_event_clock_sync`, e com isso alinha o trace ao `logcat`.
- **Histórico visível:** fim do primeiro quadro da thread principal depois do fim do `present()` (é esse quadro que tira o indicador de carregamento). Para conferir sem trace, um script lia uma linha crua da tela (`screencap` sem PNG, linha 650) a cada ~0,25–0,5 s até o texto da tela inicial aparecer. As duas medidas concordaram dentro da precisão do polling (±0,5 s).
- **Bloqueio da thread principal:** maior fatia de topo da thread principal no trace (`Choreographer#doFrame`, `bindApplication` etc.), mais `Choreographer: Skipped N frames` e `HWUI Davey!` do `logcat`. O `Davey!` mede o quadro inteiro, do vsync à GPU, então inclui RenderThread e GPU emulada; não é só a thread principal.
- **Toques:** uma thread do script tocava o título "mooscles" da barra superior (156, 216), que não é interativo, a cada 0,4 s, do lançamento ou da confirmação até alguns segundos depois do fim do carregamento. Cada toque é um `MotionEvent` real para a janela do app: se a thread principal ficasse 5 s sem atendê-lo, o Android daria ANR.
- **ANR:** `dumpsys activity lastanr` e `ANR in` no `logcat`, depois de cada execução.
- O `Mooscles:present` não é mais exercitado pelos testes de host desde que a apresentação saiu da thread principal: **os traces do aparelho são a verificação dessas seções**. Em todas as execuções acima elas apareceram, sempre na thread `DefaultDispatch` e nunca na thread principal.

## Números por execução

### Abertura a frio, APK debug, 6 exercícios

| Execução | Toques | Primeiro quadro (`Displayed`) | `present()` | Histórico visível depois do primeiro quadro | Maior quadro durante o `present()` | Quadro do conteúdo |
| --- | --- | --- | --- | --- | --- | --- |
| cs2 | não | +7,35 s | 16,95 s | 20,10 s | 237 ms | 212 ms |
| cs3 | não | +4,39 s | 13,99 s | 17,17 s | 210 ms | 379 ms |
| cs4 | não | +4,42 s | 12,25 s | 14,91 s | 395 ms | 395 ms |
| cs5 | não | +4,65 s | 14,57 s | 17,18 s | 339 ms | 339 ms |
| cs9 (sem trace) | não | +4,80 s | — | ~17,6 s (polling, ±0,5 s) | — | — |
| cs11 (sem rede) | não | +6,10 s | 15,51 s | 17,88 s | 352 ms | 352 ms |
| cs7 | 59 toques | +9,30 s | 24,40 s | 29,15 s | 340 ms | 344 ms |
| cs8 | 48 toques | +4,73 s | 19,83 s | 25,29 s | 277 ms | 592 ms |

Sem toques: mínimo 14,9 s, mediana ~17,5 s, máximo 20,1 s. A execução cs6 (14 toques) só vale para o ANR: o detector confundiu a animação de abertura com o conteúdo e o trace parou antes do `present()`. Não houve ANR nela.

A execução cs11 foi feita com Wi-Fi e dados desligados, para descartar a checagem de atualização da abertura (ela baixa e verifica um APK de 20 MB) como causa da lentidão. O `present()` levou 15,5 s mesmo assim. Uma primeira tentativa offline (cs10) não vale: logo depois de desligar a rede o sistema ficou sobrecarregado e o Android matou o processo antes de ele se conectar (`failed to attach`, `start timeout`). O app não chegou a rodar código.

### Abertura a frio, 48 exercícios

| Execução | Build | Toques | Primeiro quadro | `present()` | Histórico visível depois do primeiro quadro | Maior quadro durante o `present()` |
| --- | --- | --- | --- | --- | --- | --- |
| cs48a | debug | 46 | +4,73 s | 10,45 s | 19,69 s | 271 ms |
| cs48b | debug | não | +4,40 s | 8,62 s | 17,47 s | 237 ms |
| cs48c | debug | não | +4,76 s | 8,35 s | 15,73 s | 241 ms |
| r8-48a | R8 | 15 | +2,04 s | 1,78 s | 3,14 s | 185 ms |
| r8-48b | R8 | não | +1,41 s | 1,69 s | 2,33 s | 228 ms |

Com 48 exercícios, o `present()` do debug começou ~8 a 9 s depois do primeiro quadro, contra ~2 a 3 s com 6 exercícios. Essa espera vem antes do `present()` (a primeira leitura do Room e a inicialização do repositório) e não foi investigada.

### Import

| Execução | Base | Toques | `present()` | Maior quadro (execução inteira) | ANR |
| --- | --- | --- | --- | --- | --- |
| imp1 | 6 exercícios | 127 em 60 s | 1,11 s | 283 ms | não |
| imp2 | 6 exercícios | 93 em 45 s | 1,13 s | 278 ms | não |
| imp48 | 48 exercícios | 127 em 60 s | 1,74 s | 202 ms | não |

Cada import gerou dois `present()`: um de 0,3–0,5 ms (estado intermediário) e o completo. Durante o import, a barra de "Importing…" continua animando ([print 02](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-pr-off-main-thread-verificacao/02-importing-ui-alive.png?raw=true)), e a mensagem final aparece com o ícone de check ([print 03](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-pr-off-main-thread-verificacao/03-import-done.png?raw=true)). Depois do import, History mostra 208 treinos, 12.688 séries e 240 PRs de exercício ([print 04](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-pr-off-main-thread-verificacao/04-history-208.png?raw=true)), os mesmos números da verificação do export/import. Records mostra os recordes em lb ([print 05](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-pr-off-main-thread-verificacao/05-records.png?raw=true)).

### Troca de unidade

| Execução | Troca | `present()` | Maior quadro durante o `present()` | ANR |
| --- | --- | --- | --- | --- |
| kg1 | lb → kg | 1,23 s | 79 ms | não |
| kg3 | lb → kg | 5,11 s | 509 ms (quadro do toque no botão) | não |
| kg4 | kg → lb | 6,94 s | 542 ms (quadro do toque no botão) | não |

A execução kg2 foi descartada: o emulador estava sobrecarregado (`surfaceflinger` a 96% e o compositor a 85% de CPU, carga média 7,7), cada `input tap` levou ~4 s, e o trace saiu quase vazio. O `logcat` dela tem `Davey!` de 0,9 a 2,0 s, que não dá para atribuir sem trace. As execuções kg3 e kg4 foram feitas depois que a carga caiu.

### Treino

Com a base de 6 exercícios: iniciar "Verif routine 1", completar uma série e finalizar. No primeiro treino não houve trace, mas o `logcat` (limpo antes de começar) não tem nenhum `Skipped` nem `Davey!` do app. No segundo, com trace: `present()` de 1,18 s ao iniciar, 0,85 s depois de mudar uma série e 3,54 s ao finalizar com "Finish & save", com o maior quadro da thread principal em 84, 75 e 166 ms durante esses cálculos. O maior quadro do trace inteiro foi 472 ms, durante as rolagens até o botão Finish. Os 46 a 55 toques enviados enquanto o treino era salvo não deram ANR, e o resumo "WORKOUT COMPLETE" apareceu.

## Bloqueios que não são da apresentação

Na abertura a frio do debug, todas as execuções têm bloqueios grandes da thread principal **antes** do `present()` começar:

- `bindApplication`: 1,6 a 2,3 s;
- o primeiro `Choreographer#doFrame` (composição e medida da primeira tela): 1,8 a 2,4 s, e 4,2 s na cs7;
- um intervalo de 0,8 a 1,9 s sem seção rastreada logo depois do primeiro quadro;
- `Choreographer: Skipped 119–366 frames` no `logcat` nesse trecho.

Eles vêm antes da primeira seção `Mooscles:present`, então não são causados pela apresentação e não entram na meta. Mas eles existem: na cs7, o primeiro quadro mais o intervalo somaram ~6 s de thread principal ocupada, quase o limite de 5 s do ANR por entrada. Não houve ANR, mas num aparelho mais lento que o emulador isso pode virar ANR. No R8, o primeiro quadro levou 0,8 a 1,1 s.

## Observações

- **O `present()` do debug é muito mais lento no processo recém-aberto.** Com o mesmo histórico, ele levou de 12 a 24 s na abertura e 1,1 s no import e na primeira troca de unidade, num processo já aberto havia minutos. Minha hipótese é o JIT do ART num build debuggable (o código roda interpretado até ser compilado), somada à disputa de CPU com a abertura. A hipótese bate com o R8 (1,7 s na abertura) e com o host (~86 ms), mas **não foi verificada**: não fiz perfil de CPU.
- O indicador de carregamento aparece no tema claro e, quando o estado chega, a tela passa para o tema escuro restaurado ([print 01](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-pr-off-main-thread-verificacao/01-cold-start-loading.png?raw=true)). Isso acontece porque o tema vem do mesmo estado apresentado. Não comparei com o comportamento anterior, mas agora o carregamento dura mais tempo visível no debug.
- No debug, os quadros da animação do indicador ficaram entre 100 e 300 ms durante o `present()`: a animação gira aos trancos, mas responde.

## Riscos conhecidos

- **Start e Discard esperam a apresentação por até 30 s.** Depois de gravar, o `AppViewModel` espera o estado apresentado com `withTimeout(30_000) { contentUi.first { … } }` (Start em `AppViewModel.kt:173`, Discard em `:219`). Como um `present()` a frio no debug leva de 12 a 24 s, um Start logo depois de uma troca kg/lb ou de um import pode chegar perto desse limite. Se o Start estourar o tempo, o treino já está salvo e aparece a mensagem "Your workout was saved, but the screen could not open. Return to Workout to resume it." Nenhum dos dois casos foi provocado no emulador. Pela leitura do código, e também sem teste, o estouro no Discard sai como `TimeoutCancellationException`, que o laço de comandos repassa como cancelamento, e não como mensagem de erro.
- **Hipótese do revisor para a espera de ~8–9 s antes do `present()` com 48 exercícios, não confirmada:** o executor de transações do Room é serial, então o `snapshot()` de `repo.data` espera o `repo.initialize()`, que lê `workout_exercises` duas vezes. Com 9.984 linhas de exercício, isso caberia na espera. Para confirmar, é preciso um perfil no aparelho com seções de `Trace` em `initialize()` e `snapshot()` (ver o acompanhamento no [ROADMAP](../ROADMAP.md)).
- **Depois desta verificação** (2026-10-27, revisão final), a linha de base de repetições do treino ativo passou a ser agrupada por exercício, convenção e assistência (`WorkoutCalculations.repsPrsForWorkout`). No host, com 48 exercícios, o cálculo de um treino ativo caiu de ~81–97 ms para ~11–24 ms, com o mesmo resultado. Isso ainda não foi medido no emulador: os 0,85–1,2 s de `present()` do treino acima são de antes da mudança.

## O que **não** foi verificado

- **Celular físico.** Nada foi testado em aparelho real. Os APKs deste Mac usam a chave debug local, que não atualiza o app instalado com a chave das releases.
- **O R8 só com a base de 48 exercícios**, duas aberturas a frio. Import, troca de unidade e treino não foram medidos no R8, e o R8 com a base de 6 exercícios também não.
- **Abertura a frio com as mesmas 3 execuções em todas as variantes:** o debug com 6 exercícios teve 6 execuções sem toques; o de 48 teve 2 sem toques e 1 com; o R8 teve 1 de cada.
- **Causa da lentidão do `present()` no processo recém-aberto**, e causa da espera de ~8–9 s antes do `present()` com 48 exercícios: nenhuma das duas foi investigada.
- **Abertura a frio depois de um `Close app` no meio do carregamento**, e o caso de matar o processo durante o `present()`.
- **TalkBack** durante o carregamento.
- **Revisão de compatibilidade (`compat-reviewer`):** fica com o controlador, como foi combinado.

## Desvios e incidentes

- Num segundo treino de teste, um toque de "Complete set" por coordenada caiu em "Remove set" (a tela tinha uma linha a menos que no primeiro treino) e removeu a primeira série **desse treino ativo**. O toque seguinte, em "Finish workout", foi feito depois de uma rolagem que ainda não tinha parado e não mudou nada (nenhum `present()` veio depois dele). Parei a automação, conferi a tela e terminei o treino pelo `uiautomator`. Os dois treinos de teste foram salvos (210 finalizados no banco). Depois, a base foi recriada por `pm clear` e import, então o emulador terminou com os mesmos 208 treinos do começo.
- `pm clear com.mooscles` foi usado só no `emulator-5554`, três vezes: antes do import de 48 exercícios, antes do segundo import de 6 exercícios e na primeira rodada de import. A cada vez, a permissão de notificação foi concedida de novo.
- O emulador terminou com o APK debug deste branch e a base grande de 6 exercícios.

## Suíte

`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`. O `testDebugUnitTest` e o `assembleDebug` vieram `UP-TO-DATE`, porque o código não mudou desde a última execução em `22bf8d5`: 477 testes, 0 falhas, 0 erros e 0 ignorados nos resultados dessa execução. O lint deu 0 erros e 21 avisos. Este commit só traz docs.
