# Suítes de aparelho da fase 5A

Task 16, Step 1. Rodadas em 2026-10-31 no `emulator-5558` (AVD `Mooscles_Pixel8_API35`, Android 15, `sdk_gphone64_arm64`, 1080 × 2400, GPU do host), na `feat/phase5a-progress` em `a0eab5f` (o `096ec3c` do Push 2 mais os três commits desta task: as correções de teste das Tasks 14–15, o `ProgressIndexDeviceTimingTest` e a seção de trace do índice). Animações em 0. Cada suíte foi limitada a 5.400 s (`perl -e 'alarm shift; exec @ARGV' 5400 ./gradlew --max-workers=2 …`), uma depois da outra, pela trava entre lanes (`gated-run-lock.sh`, numa cópia que só troca o limite do Gradle de 1.800 s para 5.400 s). Nenhuma outra lane rodou. Nenhum aparelho físico foi usado; o `emulator-5556` (zumbi offline) ficou de fora.

| Suíte | Duração | Testes | Falhas | Pulados | XML (fora do git) |
| --- | ---: | ---: | ---: | ---: | --- |
| `:designsystem:connectedAndroidDeviceTest` | 6 min (422 s com a trava) | **405** (67 classes) | 0 | 0 | `local/designsystem.xml` |
| `:screens:connectedAndroidDeviceTest` | 22 min 30 s | **881** (82 classes) | 0 | 1 (`PilotSheetCaptureTest`, opt-in) | `local/screens.xml` |
| `:app:connectedDebugAndroidTest` | 1 min 50 s | **25** (9 classes) | **1** (`UiFlowTest`, corrigida abaixo) | 2 (`ThreadComparisonFixtureTest`, `WorkoutPerformanceFixtureTest`, opt-in) | `local/app.xml`, `local/app.log` |

Para comparar: na 4B as suítes tinham 373 / 640 / 25 testes. O `:app` inclui o `UiFlowTest`, o `UiFeatureSmokeTest` (os dois testes passaram) e o `LegacyInstallOpenTest` (os dois passaram).

Na suíte do `:screens` estão as classes da fase: `ProgressAccessibilitySweepTest` (15/15, 134 s), `ProgressFlowTest` (2/2), `ProgressIndexDeviceTimingTest` (1/1; os números estão no [performance.md](performance.md)), `RecordsScreenTest`, `ExerciseProgressSectionsTest`, `TapBudgetTest` e as demais. As correções de teste herdadas da revisão das Tasks 14–15 (`83d4e4f`) rodaram nesta suíte, verdes:

- `assertBadgesInsideTheirRows` falha numa passada sobre linhas de recorde que não acha nenhum selo (`Pass.badges`). Ele é exigido no topo, no meio e no fim da linha do tempo, em "Só atuais" e na busca.
- Passada "meio" explícita em `progressPasses` (uma sessão entre a mais nova e a mais antiga: `u1` e `a1`), no card do histórico ("topo", com o título e a figura, e "meio", com os músculos, além de "card" e "fim") e na busca (a linha do meio do supino).
- O passo 5 do `ProgressFlowTest` lê o conteúdo das linhas da tabela, e não só confere que elas existem. No 1RM estimado, a linha 0 tem a série do treino mais novo, "80 kg × 8" (Epley 101,3, acima de 85 × 5 com 99,2). A última linha tem a do mais antigo, "60 kg × 5".

## A falha e o que foi feito

**`UiFlowTest.routineToWorkoutRestSummaryAndHistory`** (`:app`): `ComposeTimeoutException` em `UiFlowTest.kt:132`, a espera pelo descanso de 2:00 depois de concluir a série.

- **Causa:** o `connectedDebugAndroidTest` instala por cima do app que já estava no emulador, sem apagar os dados, e o teste roda no processo do app sobre o DataStore real. O app que estava lá (de rodadas anteriores de outras lanes) tinha o descanso automático desligado, e o teste supõe que ele está ligado.
- **Prova da causa:**
  - O teste sozinho, numa instalação limpa, passou (1/1, `local/uiflow-fresh.log`).
  - Com o app instalado e "Automatic rest" desligado pela tela de Configurações, ele falhou na mesma linha 132 (`local/uiflow-autorest-off.log`).
- **Correção, só de teste** (`3b07456`): o `UiFlowTest` liga o descanso automático (`app.preferences.autoRest(true)`) antes do fluxo e devolve o valor que encontrou no fim.
- **Prova da correção:** de novo com o descanso automático desligado antes, o teste passou (1/1, `local/uiflow-fix.xml`).
- Só essa classe rodou de novo. Nenhum código de produto mudou por causa dela, e os testes de `androidTest` não entram em nenhum mínimo do CI.

## Checagem à mão herdada da Task 13: Recordes com a busca focada

Feita na instalação nova sem rede (ver [offline.md](offline.md)), no build debug da branch, com a base grande importada (208 treinos), em inglês. O teclado do emulador é o Gboard.

| Caminho | Roteiro | Resultado |
| --- | --- | --- |
| Teclado na tela | Busca "press" e a lista filtrada rolada para baixo. Toque no campo, que ganha o foco e abre o teclado; um espaço digitado e apagado. "Voltar" fecha o teclado (o teclado cobre as abas), toque em Library e toque em Records | **igual**: o mesmo texto na busca, as mesmas linhas e os mesmos limites na tela (o `uiautomator dump` antes e depois é idêntico, `diff` vazio). A primeira linha visível ficou em `[0,573][1080,798]` ([antes](t13-A-1-focused-typed.png), [teclado fechado](t13-A-2-keyboard-hidden.png), [de volta](t13-A-3-back-on-records.png)) |
| Teclado físico (eventos de teclado do `adb`) | A busca limpa e a lista rolada. Toque no campo e "row" digitado com `adb shell input keyboard text`, o que tira a janela do modo de toque (`mInTouchMode=false`). `ESC` e sete `TAB` com `input keyboard keyevent` levam o foco do teclado para dentro da lista, que rola (o foco foi parar numa linha em `[0,1252][1080,1517]`). Depois, toque em Library e toque em Records | **igual**: `diff` vazio entre o antes e o depois ([foco na lista](t13-H-2-hw-before-leaving.png), [de volta](t13-H-3-hw-back-on-records.png)) |

Nota do teclado físico: no emulador, o teclado na tela continua aparecendo quando o foco do teclado volta ao campo de busca (os sete `TAB` dão a volta). O primeiro toque em Library caiu no teclado aberto, não na aba, e por isso foi preciso fechar o teclado antes. Isso é do emulador, que não tem um teclado físico de verdade conectado: com um teclado físico, o Android não mostra o teclado na tela. Um teclado físico de verdade num aparelho não foi testado.

## Só no emulador

Tudo acima rodou num emulador Android 15. Aparelho físico e Android 8 a 14: não verificados. As suítes de iOS (`:designsystem:iosSimulatorArm64Test`, `:screens:iosSimulatorArm64Test`) só rodam no CI. Esta task compilou os testes do `:screens` para iOS (`compileTestKotlinIosSimulatorArm64`, verde depois das mudanças nos `uiTest`), mas não os rodou.
