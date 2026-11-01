# Suítes de aparelho da fase 4A

Task 22, Step 1. Rodadas uma vez, em 2026-10-30, no `emulator-5556` (AVD `Mooscles_Pixel8_API35`, Android 15, `sdk_gphone64_arm64`, 1080 × 2400, GPU do host), na `feat/phase4a-library` em `fa996b4` (Tasks 1 a 21 integradas) mais o `LibrarySearchTimingTest` novo. Nenhuma outra lane usava o emulador nem o Gradle. Cada comando foi limitado a 5.400 s (`perl -e 'alarm shift; exec @ARGV' 5400 ./gradlew …`), em sequência e nesta ordem.

| Suíte | Duração | Testes | Falhas | Pulados | XML |
| --- | ---: | ---: | ---: | ---: | --- |
| `:designsystem:connectedAndroidDeviceTest` | 13 min 28 s | 339 | **1** (corrigida, abaixo) | 0 | `local/designsystem-connectedAndroidDeviceTest.xml` |
| `:screens:connectedAndroidDeviceTest` | 14 min 58 s | 430 | 0 | 1 | `local/screens-connectedAndroidDeviceTest.xml` |
| `:app:connectedDebugAndroidTest` | 54 s | 25 | 0 | 2 | `local/app-connectedDebugAndroidTest.xml` |

Os pulados são os opt-in de sempre: `PilotSheetCaptureTest.capturePilotSheet` (captura da folha do piloto, Task 8) no `:screens`, e `ThreadComparisonFixtureTest` e `WorkoutPerformanceFixtureTest` (fixtures de desempenho) no `:app`.

## A falha e a correção

`FocusRingTest.focusedControlShowsTheRingAndBlurredDoesNot` falhou na última asserção: depois de `clearFocus(force = true)`, o controle continuava com `Focused = true` e o anel visível.

- **Causa:** dependência de ordem. `MChipTest.menuChipIsAButtonAndShowsItsCount` e `MSearchFieldTest.clearEmptiesAndKeepsFocus` (Task 7, novos nesta fase) pedem `InputMode.Keyboard` e não devolvem o modo de toque. O modo de toque é do display, não do teste: no modo teclado, o Android devolve o foco ao primeiro focável quando o foco é limpo. O `FocusRingTest` (pacote `interaction`, roda depois de `components`) herdava esse estado. O código do anel não mudou desde a fase 2.
- **Prova:** as três classes juntas, na ordem `MChipTest`, `MSearchFieldTest`, `FocusRingTest`, sem a correção: 9 testes, **1 falha** (a mesma). Com a correção: 9 testes, 0 falhas (`local/designsystem-rerun-after-fix.xml`).
- **Correção** (`fd5a4cc`, só código de teste): `restoreTouchMode()` no `TestPlatform.kt` do `androidDeviceTest` (`Instrumentation.setInTouchMode(true)`, o mesmo que o `:screens` já faz) e no do `iosTest` (sem efeito), chamado por um `@AfterTest` nas duas classes. `:designsystem:compileTestKotlinIosSimulatorArm64` compila. Nenhum teste novo, então o `MIN_DS_IOS_TESTS` não muda.

Só essas três classes rodaram de novo, como pede a regra da task.

## Pendências de tasks anteriores conferidas nesta rodada

| Pendência | Resultado |
| --- | --- |
| `LibraryFlowTest` a 2x real depois da Task 21 (as folhas herdam a escala do host) | `libraryFlowEnglishDarkAtTwoX` passou (7,3 s), e `libraryFlowPortugueseLightAtOneX` (8,0 s) |
| `ActiveWorkoutFlowTest`, editado na Task 18 e ainda não rodado, com a varredura a 2x sobre os chips do seletor | `flowDarkAtTwoX` (7,8 s) e `flowLightAtOneX` (7,5 s) passaram |
| `ExercisePickerSheetTest` com o teste renomeado na Task 19 | `rowsHoldTheMediaWithMuscleAndEquipment` passou (3,5 s), com as outras 9 da classe |
| `UiFlowTest` e `UiFeatureSmokeTest` (`:app`) | `routineToWorkoutRestSummaryAndHistory` e os dois do smoke passaram |
| `LibrarySearchTimingTest` (novo, Step 4) | passou; números no [performance.md](performance.md) |

O `connectedDebugAndroidTest` desinstala o app no fim, por isso rodou antes da atualização por cima ([upgrade.md](upgrade.md)).

## Só no emulador

Tudo acima rodou num emulador Android 15. Aparelho físico e Android 8 a 14: não verificados. As suítes de iOS (`:designsystem:iosSimulatorArm64Test`, `:screens:iosSimulatorArm64Test`) só rodam no CI; esta task não as rodou.
