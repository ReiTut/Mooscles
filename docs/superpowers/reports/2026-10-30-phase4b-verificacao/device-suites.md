# Suítes de aparelho da fase 4B

Task 18, Step 1. Rodadas em 2026-10-30 no `emulator-5556` (AVD `Mooscles_Pixel8_API35`, Android 15, 1080 × 2400, GPU do host), na `feat/phase4b-routines` em `4694edc` (código do app e do design system; os dois commits de teste abaixo vieram depois, e um merge de outra lane, `95ca601`, entrou na branch quando a task já tinha rodado tudo, então não foi testado aqui). Animações em 0. Cada comando foi limitado a 5.400 s (`perl -e 'alarm shift; exec @ARGV' 5400 ./gradlew --max-workers=2 …`), em sequência, com o emulador reservado por um lock. Durante a task apareceu outra lane (`r2x-*`) pedindo o mesmo lock (a task dizia que não havia nenhuma); o lock evitou rodadas simultâneas, mas ela rodou duas vezes entre as minhas (ver "Rodadas descartadas").

| Suíte | Duração | Testes | Falhas | Pulados | XML (fora do git) |
| --- | ---: | ---: | ---: | ---: | --- |
| `:designsystem:connectedAndroidDeviceTest` | 10 min 17 s | **373** (64 classes) | 0 | 0 | `local/designsystem.xml` |
| `:screens:connectedAndroidDeviceTest` (rodada completa) | 22 min 19 s (com o Mac sob carga) | **640** (66 classes) | **1** (flaky de harness, corrigida e provada, abaixo) | 1 (`PilotSheetCaptureTest`, opt-in) | `local/screens-run2.xml` |
| `:app:connectedDebugAndroidTest` | 1 min 50 s | **25** (9 classes) | **1** (corrida de teste, corrigida, abaixo) | 2 (`ThreadComparisonFixtureTest`, `WorkoutPerformanceFixtureTest`, opt-in) | `local/app.xml` |

Para comparar: na 4A as suítes tinham 339 / 430 / 25 testes.

## As duas falhas e o que foi feito

1. **`UiFlowTest.routineToWorkoutRestSummaryAndHistory`** (`:app`): `No node found that matches TestTag = 'routine-set-<id>-reps' in scrollable container`. O teste esperava o repositório ter duas séries planejadas e rolava logo para a linha; o estado do editor pode ficar um quadro atrás do repositório. Na segunda rodada a classe passou (`local/rerun-uiflow.log`). Correção só de teste, `53b380e`: a rolagem fica dentro de um `waitUntil` que tenta de novo. Rodei de novo só a classe (1 teste, verde, com a correção no `local/uiflow-fix.xml`).
2. **`CustomExerciseFormPhotoTest`** (`:screens`): `IllegalArgumentException: performMeasureAndLayout called during measure layout`, sempre dentro de `waitForIdle` ou do desenho, nunca no código do produto. É a falha que a Task 10 já tinha anotado como "flake, olhar na Task 18" (M8 da revisão). Frequência medida:
   - 1ª rodada completa: `photoDraftSurvivesRecreation` derrubou o processo de instrumentação (exceção fatal no `dispatchDraw` da thread principal): **só 72 de 640 testes rodaram**, e essa rodada foi descartada;
   - 2ª rodada completa: `failedEditKeepsTheFormAndThePhoto` falhou (uma falha, a rodada seguiu até o fim, 640 testes);
   - a classe sozinha, sem a correção, 4 vezes: 1 falha (a mesma `failedEdit…`);
   - **Causa mais provável:** o teste escrevia o `UiState` fake direto da thread de teste (`outcome.state.value = …`), e o quadro de teste seguinte (`measureAndLayoutForTest`) corre contra um passe real de layout da thread principal. Correção só de teste, `4fd2b22`: as duas escritas desse teste passam por `runOnUiThread`. Com ela, **8 execuções seguidas da classe, 23/23 verdes cada** (`local/photo-fix-*.xml`).
   - **Limite da prova:** 8 execuções sem falha contra 1 em 4 antes dá evidência, não certeza. O crash fatal da 1ª rodada foi em outro teste da classe (`photoDraftSurvivesRecreation`, sem escrita de estado pela thread de teste) e não reapareceu em nenhuma das 11 execuções seguintes; a causa dele não ficou provada. Se voltar, o primeiro suspeito é o mesmo choque entre o quadro de teste e o layout real. Nenhum código de produto mudou, então os mínimos do CI não mudam.

## Rodadas descartadas

- `:screens`, 1ª rodada completa: abortada no teste 72 (processo de instrumentação morto). Refeita inteira (a de cima).
- A `r2x-lane` (outra lane, não minha) usou o emulador duas vezes: uma vez entre a minha rodada do `:screens` e a seguinte (pediu o lock e esperou), e outra durante a Step 3, quando eu ainda mexia na tela sem segurar o lock; desde então segurei o lock em todo o trabalho manual. Nada do que vi nas capturas dessa janela entrou em medida.

## Só no emulador

Tudo acima rodou num emulador Android 15. Aparelho físico e Android 8 a 14: não verificados. As suítes de iOS (`:designsystem:iosSimulatorArm64Test`, `:screens:iosSimulatorArm64Test`) só rodam no CI; esta task não as rodou. O `runOnUiThread` usado na correção é API comum do `ComposeUiTest` e o arquivo é do `uiTest` compartilhado com o iOS, mas a compilação para iOS não foi feita aqui.
