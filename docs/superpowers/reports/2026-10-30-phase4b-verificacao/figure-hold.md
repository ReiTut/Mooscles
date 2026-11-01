# Figura do detalhe: quadros durante a pausa (4B, Task 5)

Pendência da [verificação da 4A](../2026-10-30-phase4a-verificacao.md) ("Figura do detalhe e bateria"): durante a pausa de cada quadro, a figura mantinha o `Choreographer` em cerca de 60 Hz sem nada para desenhar. Task 5 do [plano da 4B](../../plans/2026-10-30-phase4b-routines.md).

## O que mudou

A alternância do `AnimatedExerciseFigure` deixou de ser uma transição infinita (`rememberInfiniteTransition` com `keyframes`, que pede um quadro a cada vsync, também nas pausas, em que o valor não muda). Agora é um `LaunchedEffect` que espera a pausa com `delay(illustrationFrame)` e anima só o fade (`Animatable.animateTo` com `tween(motion.base, motion.easeInOut)`). Cada espera e cada fade roda dentro da `InfiniteAnimationPolicy` do contexto (decisão F5 do preflight): o relógio de teste continua cancelando a alternância com `autoAdvance` ligado, e os testes que mostram a figura ficam ociosos como antes.

## Resultado

Medido no emulador `Mooscles_Pixel8_API35` (Android 15, 60 Hz, `animator_duration_scale` 1), em 2026-10-30. Mesmo teste temporário antes (base `6cebb47`) e depois da mudança.

| | Antes | Depois |
| --- | --- | --- |
| `Choreographer#doFrame` por segundo durante as pausas | **60,1** (433 em 7,21 s, 9 pausas) | **0,3** (2 em 5,94 s, 7 pausas) |
| `Choreographer#doFrame` por segundo, 10 s inteiros | 60,2 (602) | 12,4 (124) |
| Desenhos (`DrawFrame` do RenderThread) por segundo | 12,5 (125) | 9,7 (97) |
| `dumpsys gfxinfo`: Total frames rendered em 10 s | 129 | 107 |

Os quadros que sobram são os dos fades: 2 fades de 220 ms a cada ciclo de 2,24 s, cerca de 13 quadros por fade. Os 2 `doFrame` "na pausa" do depois ficam na borda de uma pausa (a margem de 50 ms em cada ponta não pega todos). O número de desenhos caiu um pouco porque o primeiro quadro de cada fade (o tempo zero do `Animatable`) não muda a opacidade.

## Como foi medido

Um teste de aparelho temporário, fora do commit ([fonte](FigureHoldMeasurement.kt.txt)), em `designsystem/src/androidDeviceTest`: uma `ComponentActivity` real (sem o relógio de teste do Compose, no `Choreographer` de verdade) com um `ComposeView` que mostra o `AnimatedExerciseFigure` a 200 dp, tema claro, sem movimento reduzido. Depois de 1,5 s, ele zera o `gfxinfo`, roda `atrace --async_start -b 16384 -a com.mooscles.designsystem.test gfx view` por 10 s (pelo `UiAutomation`), e conta no trace do processo os `Choreographer#doFrame` e os `DrawFrame`. Uma pausa é o intervalo entre dois desenhos separados por mais de 300 ms; contam os `doFrame` a mais de 50 ms das duas pontas. O teste termina com `AssertionError` carregando o relatório, para que ele apareça na saída do Gradle.

```sh
./gradlew :designsystem:connectedAndroidDeviceTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.mooscles.designsystem.illustration.FigureHoldMeasurement
```

**Diferença do plano:** o Step 4 pedia o detalhe de um exercício do piloto no app, com `dumpsys gfxinfo com.mooscles framestats`. O `gfxinfo` conta só os quadros desenhados, e o problema eram os `doFrame` sem desenho (antes e depois o `gfxinfo` mostra cerca de 10 a 13 quadros por segundo); por isso a contagem vem do trace. E a medida roda na atividade de teste do design system, não no app, porque o emulador só é usado pelo portão (uma execução do Gradle por vez), sem instalar o app e navegar à mão. A figura é o mesmo componente que o detalhe usa; o que não foi medido é o detalhe inteiro aberto no app.

## Prova em teste

`ExerciseFigureTest.holdRequestsNoFrames` (`designsystem/src/uiTest`, emulador e simulador iOS): com o relógio parado, avança quadro a quadro e conta os quadros depois dos quais algo da composição ainda espera o próximo (`BroadcastFrameClock.hasAwaiters`, o relógio do recomposer). Na pausa do quadro final (900 − 220 ms) e na do inicial: 0. No fade (controle positivo): pelo menos metade dos quadros. Antes da mudança falhou com 43 quadros pedidos na primeira pausa; depois, a classe inteira (10 testes) passou no emulador. Não verificado: o simulador iOS (só no CI).
