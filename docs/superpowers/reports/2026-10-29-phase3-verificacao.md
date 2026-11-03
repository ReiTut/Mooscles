# Fase 3: verificação do redesign do treino ativo

- **Data:** 2026-10-29
- **Branch:** `feat/phase3-active-workout`, a partir da `main` do fork em `51508a5` (spec e plano mesclados). Último commit de produto: `2ff31b4`; este relatório e os documentos vêm no commit seguinte, `docs: phase 3 active workout verified`
- **Spec:** [fase 3](../specs/2026-10-29-phase3-active-workout-design.md), com as [notas de implementação](../specs/2026-10-29-phase3-active-workout-design.md#notas-de-implementação) · **Plano:** [fase 3](../plans/2026-10-29-phase3-active-workout.md) · **Spikes:** [S1, S2 e S3](2026-10-29-phase3-spikes.md) · **Crítica:** [treino, resumo, casca e cartão](2026-10-29-phase3-critica-treino.md)
- **Evidências:** [2026-10-29-phase3-verificacao/](2026-10-29-phase3-verificacao/): [contagem de toques de hoje](2026-10-29-phase3-verificacao/tap-baseline.md), [atualização por cima](2026-10-29-phase3-verificacao/upgrade.md), [`preview` lado a lado](2026-10-29-phase3-verificacao/preview-side-by-side.md), [desempenho](2026-10-29-phase3-verificacao/performance.md), [TalkBack (caminho de reserva)](2026-10-29-phase3-verificacao/talkback.md), [diff de dependências](2026-10-29-phase3-verificacao/deps-diff-final.txt), as [capturas do fluxo no simulador iOS](2026-10-29-phase3-verificacao/ios-flow/) e as capturas do emulador

## Resumo

- **Entregue:** o módulo KMP `:screens` (Android, `iosArm64`, `iosSimulatorArm64`) com o treino ativo novo, o resumo de fim, o cartão de compartilhamento desenhado em código comum e a casca do app (quatro abas e a barra do treino em andamento), tudo em `commonMain`, só com o `:designsystem`, em inglês e pt-BR. No `:shared`, os acréscimos do contrato (erros tipados e `UiError`, recorde por série, "anterior" por série, `restoreSet`, descanso 0:00, regras de sugestão, `InProcessRestTimer`). No `:app`, a hospedagem das telas novas, a notificação em andamento do descanso com ações, o ícone novo e o build `preview`.
- **Testes:** no CI final ([40958575207](https://github.com/pedroyuuki/Mooscles/actions/runs/40958575207), `f646dfd`), 815 de host, e no simulador iOS `:shared` 224, `:screens` 293 e `:designsystem` 295, todos verdes, incluindo o `ActiveWorkoutFlowTest` sobre o ViewModel, o Room e o DataStore reais. No emulador, uma rodada completa no fim: `:screens` 294 de 294, `:designsystem` 295 de 295 e `:app` 25 sem falhas. A falha do anel do timer no iOS, aberta quando este relatório foi escrito, foi corrigida (ver o [adendo](#adendo-2026-10-29-revisão-final)).
- **Toques:** nenhum fluxo medido passou do de hoje. Concluir uma série igual à da semana passada foi de 6 toques para 1, e subir a carga um passo, de 8 para 3.
- **Dados intactos:** sem migração, sem chave nova de DataStore. A atualização por cima da `main` (`4f2911c`) com o treino ativo e o descanso correndo manteve as séries, o arquivo de preferências com o mesmo MD5, o prazo do descanso e o export.
- **APK debug: +532.492 bytes** (0,51 MiB) sobre a `main` de depois da fase 2, dentro de 1 MiB. Única dependência nova: `kotlinx-datetime` 0.8.0.
- **Não verificado:** TalkBack, háptico e 60 fps num aparelho (o dono ainda não instalou o `preview`), qualquer coisa num iPhone ou num Android físico, e a release com a chave original, que não está neste Mac. A lista completa está [no fim](#o-que-não-foi-verificado).

## Ambiente

- Emulador `Mooscles_Pixel8_API35` (Android 15, 1080 × 2400, 420 dpi, 411 dp de largura), `emulator-5554` até a queda do disco externo e `emulator-5556` depois, num Mac M4 sem Xcode. O iOS só se prova no job `ios` do CI (`macos-26`, Xcode 26.4).
- Os APKs locais são assinados pela chave debug do Mac, que não é a das releases. A atualização por cima prova a compatibilidade dos dados entre dois builds, não o caminho do APK publicado para este build.

## O que foi provado

### Testes

| Conjunto | Onde | Resultado |
| --- | --- | --- |
| Host dos quatro módulos (`:app` JUnit 4 + Robolectric, `commonTest` e `androidHostTest` dos KMP) | CI, run [40937989893](https://github.com/pedroyuuki/Mooscles/actions/runs/40937989893), `5d94a7f` | 806 testes em 137 classes, 0 falhas (mínimo 805) |
| Os mesmos | Mac, `2ff31b4` (o último commit de produto) | 807: `:app` 416, `:shared` 240, `:designsystem` 70, `:screens` 81; 0 falhas. O `MIN_HOST_TESTS` foi para 807 |
| `:screens:connectedAndroidDeviceTest` (`uiTest` e `commonTest`) | emulador, `b097cd0` (depois das correções da crítica) | 288 de 288 |
| `:designsystem:connectedAndroidDeviceTest` | emulador, `b097cd0` | 291 de 291; depois de `2ff31b4`, as classes do timer (35) e o `WorkoutTimerTest` (20) de novo, verdes |
| `:app:connectedDebugAndroidTest` (`UiFlowTest`, `UiFeatureSmokeTest`, `LegacyInstallOpenTest` e os de dados) | emulador, `e482e1d` | 25 testes, 0 falhas, 2 pulados (os ajudantes opt-in de fixture de desempenho) ([XML](2026-10-29-phase3-verificacao/connectedDebugAndroidTest.xml)). O `:app` mudou depois disso só em `53b13ba` (barras do sistema), coberto por teste de host |
| `:shared`, `:designsystem` e `:screens` no simulador iOS | CI | ver [CI](#ci) |

Os testes que cobrem as exigências da spec:

- **Contrato e regras (`:shared`, host e simulador):** `WorkoutPresentationTest` (`previousSets`, `previousDate`, `SetUi.record` numa série só, aquecimento e assistência, primeiro registro, tolerância por `ulp`, lb e kg, o mesmo exercício duas vezes), `WorkoutSetRestoreTest` e `AppViewModelRestoreHostTest` (`restoreSet` e o descanso 0:00), `SetSuggestionsTest` (ordem das fontes, campo apagado, carga 0 por convenção, lb), `InProcessRestTimerTest` (a semântica do `RestTimerController`, com o mesmo formato no DataStore), `DataErrorsTest`, `UiErrorsTest` e `WorkoutRepositoryErrorTypesTest` (as mesmas mensagens, só o tipo muda).
- **Telas (`:screens`, emulador e simulador):** a linha de série (`WorkoutSetRowTest`, `SetEditingTest`, `SetTypeAndRemovalTest`), os cards (`ExerciseListTest`, `CardExpansionTest`), o timer (`WorkoutTimerTest`, com `timerTicksRecomposeNoCardOrRow`), o recorde (`RecordReactionTest`, com `scrollingARecordRowAwayAndBackDoesNotReplay`), a tela inteira (`ActiveWorkoutScreenTest`), o resumo (`WorkoutSummaryScreenTest`), o cartão (`ShareCardDataTest`, `ShareCardPainterTest`, `ShareCardTextsTest`), a casca (`AppShellBottomTest`, com `timerTicksDoNotRecomposeTheTabs`), as datas (`ScreenDatesTest` e a equivalência com o `java.time` em `ScreenDatesJvmEquivalenceTest`) e o `StringsParityTest`.
- **Os cinco pontos do "Review Focus" do plano:** `SetEditingTest.editingDraftAndClearedFieldsSurviveRecreation` e `SetTypeAndRemovalTest.undoSurvivesRecreation` (recriação com edição e desfazer abertos), `RecordReactionTest.scrollingARecordRowAwayAndBackDoesNotReplay`, `WorkoutPresentationTest.recordMarksOneSetAcrossTwoEntriesOfTheSameExercise` e `ExerciseListTest.repeatedInstancesKeepTheirOwnValues`, `ActiveWorkoutScreenTest.emptyActiveWorkoutShowsOnlyAddExerciseAndBlocksFinish`, e `RestTimerActionsHostTest.notificationActionIsRefusedWhileAnUpdateInstalls` com `RestProgressNotificationHostTest.recreatedWhenTheProcessReturnsInTheBackground`.
- **Casca e notificação (`:app`, host):** `ShellNavigationHostTest` (sem abas no treino, a aba selecionada volta à raiz também da galeria e das licenças, um treino só na pilha, o skeleton por exercício, o resumo com a casca), `RestProgressNotificationHostTest`, `RestTimerActionsHostTest`, `MainActivityOpenWorkoutHostTest`, `UpdaterDisabledHostTest`, `LauncherIconHostTest` e `SystemBarsThemeHostTest`.

### O fluxo inteiro no simulador iOS (D6)

O `ActiveWorkoutFlowTest` (`screens/src/uiTest`) monta o `ActiveWorkoutRoute` e o `WorkoutSummaryScreen` sobre o `MoosclesViewModel` real, o Room real (`openWorkoutDatabase`, `BundledSQLiteDriver`, num diretório temporário), o DataStore real (`createMoosclesStore`) e o `InProcessRestTimer`. Cada execução semeia uma rotina e um histórico (supino 85 kg × 6), começa pela ação `startWorkout(routineId)` e percorre: concluir uma série pré-preenchida; subir a carga pelo stepper e concluir, batendo o recorde (aviso "Novo recorde de carga", selo numa série só); concluir por sugestão; o dock (ou o compacto a 2,0), +15 s, o expandido, pular; remover por deslize e desfazer; adicionar e trocar um exercício pelo seletor; finalizar; o resumo com os números e o recorde ("87,5 kg × 6 · antes 85 kg"). Cada passo espera o estado do ViewModel, que vem do Room, e não a tela. Em cada um dos 10 estados: todo nó clicável com rótulo, alvos de 48 dp (56 nas ações do treino) e nenhum número cortado.

- Roda duas vezes (decisão 27 do plano): claro a 1,0 e escuro a 2,0. Verde no emulador (5 execuções seguidas) e no simulador do CI desde o run [40882509181](https://github.com/pedroyuuki/Mooscles/actions/runs/40882509181) (`a88028f`). O `Dispatchers.Main` do ViewModel funciona no simulador sem `setMain`.
- **Capturas:** o `captureToImage()` funciona no simulador. O job `ios` publica 28 PNG (14 estados × 2 execuções, 1024 × 768) no artefato `ios-flow-captures`; uma cópia do run [40937989893](https://github.com/pedroyuuki/Mooscles/actions/runs/40937989893) (`5d94a7f`, com as correções da crítica) está em [`ios-flow/`](2026-10-29-phase3-verificacao/ios-flow/), porque o artefato expira em 14 dias. São capturas de teste, tiradas no meio do fluxo. Só algumas foram abertas, e uma delas mostra algo a investigar: na `light-1x-record` (a mesma imagem no run de `53b13ba`), sob a série 2 aparece um bloco cinza do tamanho do painel de edição, com o selo de recorde meio desenhado. Pode ser o painel recolhendo no meio da animação, com o relógio do teste parado para o aviso de recorde; não foi investigado. Uma revisão visual delas, tela a tela, não foi feita; a revisão visual no iOS é da fase 7.

### Orçamento de toques

A contagem de hoje foi medida na tela antiga, antes da troca, com o mesmo contador ([`tap-baseline.md`](2026-10-29-phase3-verificacao/tap-baseline.md)). O `TapBudgetTest` (`:screens`, emulador e simulador) conta toques e gestos na tela nova sobre o ViewModel real, confere o que foi gravado e reprova acima da coluna "Novo" da spec ou da de hoje.

| Fluxo | Hoje (medido) | Spec, "Novo" | Medido na tela nova |
| --- | ---: | ---: | ---: |
| Concluir série pré-preenchida pela rotina | 1 | 1 | 1 |
| Concluir série igual à da semana passada, sem alvo | 6 | 1 | 1 |
| Subir a carga um passo e concluir | 8 | 3 | 3 |
| Uma repetição a menos e concluir | 4 | 3 | 3 |
| Adicionar série igual à de cima e concluir (card aberto) | 4 | 2 | 2 |
| Adicionar série depois da última do exercício (card recolhido, D9) | 4 | — | 3 |
| Marcar aquecimento | 2 | 2 | 2 |
| Remover série: deslize / sheet | 1 | 1 / 2 | 1 / 2 |
| Pular descanso / +15 s | 1 / 1 | 1 / 1 | 1 / 1 |
| Finalizar | 2 (mais a rolagem) | 2 | 2 |

O de remover pela sheet (2) é a alternativa acessível do deslize (1), como a spec prevê. O de adicionar depois da última série tem nota datada na spec.

### Acessibilidade e fonte grande

- `WorkoutAccessibilitySweepTest` percorre os estados pendente, em edição, com recorde, com dock, expandido, pausado, resumo, barra de abas e barra do treino, nos dois temas, a 1,0, 1,3 e 2,0 (mais 2,01 no Android, que escala a fonte de forma não linear abaixo de 2x), em 320 e 412 dp. Em cada combinação: rótulo em todo clicável, alvos de 48 dp e 56 nas ações do treino, nenhum número cortado e nenhum texto de botão cortado, em três posições de rolagem (a varredura exige achar os controles do fim da lista). Também a ordem de leitura (cabeçalho, aviso de recorde, cards, dock por último) e os grupos de travessia do dock e da barra do treino.
- A varredura achou e a fase corrigiu: o "+" do stepper com 0 dp de largura a 200% (`60040a7`), o fundo de diálogos e sheets sem nome (`60040a7`), o valor do stepper cortado a 320 dp e 1,0 (`bf9c56d`) e o stepper trocando de arranjo sob o dedo (`e482e1d`).
- Contraste: as cores novas são os pares já medidos da fase 2 (`ContrastTokensTest`, `ComponentColorsTest`); a linha em edição ficou no `bgInset` medido (nota F38 na spec).
- O caminho de reserva do TalkBack (o `uiautomator dump` em pt-BR, com a ordem da árvore e as marcas de cada nó) está em [`talkback.md`](2026-10-29-phase3-verificacao/talkback.md). A ordem real de travessia e as ações personalizadas não aparecem no dump: estão provadas pelos testes citados lá.

### Atualização por cima

Ver [`upgrade.md`](2026-10-29-phase3-verificacao/upgrade.md). Do APK da `main` (`4f2911c`) para o da branch (`e482e1d`), com a rede do emulador desligada, em pt-BR e em libras, com um treino de três exercícios, quatro séries concluídas, uma removida e o descanso de 5:00 correndo:

- as mesmas séries, sem a removida; o `local_preferences.preferences_pb` com o mesmo MD5 (`5ef9f420…`) antes, logo depois do `install -r` e depois da primeira abertura;
- o dock chegou a "Descanso concluído" 117 ms depois do prazo salvo (uma amostra a cada ~2,6 s);
- ao ir para segundo plano, a notificação em andamento (id 3, canal `rest_progress`, importância baixa, cronômetro do sistema contando até o prazo salvo) com −15 s, +15 s e Pular; tocar nela abriu o treino com uma só `MainActivity`;
- finalizar mostrou o diálogo e o resumo com os números certos (volume 4.800,57 lb, o mesmo que o build antigo mostrava);
- o export depois igual ao de antes, campo a campo, tirando `exportedAt`, o treino finalizado e o efeito já conhecido do `inheritPr` (o recorde novo sobe os alvos herdados).

O checklist de aparelho das Tasks 24 e 25 também rodou no emulador: o cronômetro e o alerta de fim, as ações com o processo morto, a notificação pausada, os textos e o canal `rest` renomeado em pt-BR numa instalação existente, a permissão de notificação negada, arrastar a notificação para tirar, e a paisagem com navegação de 3 botões. Os itens que só um aparelho ou uma atualização de verdade provam estão em [não verificado](#o-que-não-foi-verificado).

### `preview` lado a lado

Ver [`preview-side-by-side.md`](2026-10-29-phase3-verificacao/preview-side-by-side.md). Instalado com o debug já no emulador: dois ícones, "Mooscles Preview" abre vazio (0 treinos, só o catálogo), o MD5 do `local_preferences` do `com.mooscles` não mudou, e o preview não agendou nenhum trabalho do WorkManager nem criou o estado do updater. Com a rede desligada, a prova é a ausência de trabalho agendado, não uma tentativa barrada.

### Desempenho

Ver [`performance.md`](2026-10-29-phase3-verificacao/performance.md). No `assemblePerformance` (R8) da branch e da `main` em `4f2911c`, com a base grande (208 treinos, 12.688 séries), no mesmo emulador:

| Medida | Orçamento | Resultado |
| --- | --- | --- |
| Toque no check até o disco cheio | o próximo quadro, sem banco nem apresentação na thread principal | passou: o quadro seguinte terminou 20,9 a 43,4 ms depois do toque, com uma recomposição, sem SQL nem `present()` na thread principal. O `present()` roda depois, em fundo |
| Ticks do timer | zero recomposições de cards, linhas e abas | passou (testes com contador, controle positivo) |
| "Começar" até o skeleton | o próximo quadro | parcial: o quadro seguinte ao toque é igual ao da `main` (mediana 61,5 contra 59,8 ms); que ele já mostra o skeleton está provado no host, não na tela |
| "Começar" até o treino editável | sem piora acima do ruído | mediana 306 ms contra 252 ms na `main` (+54 ms, p ≈ 0,23 num teste de permutação, 10 execuções de cada): dentro do ruído do emulador, mas na direção de piora. Vale medir num aparelho |
| Rolagem rápida do treino grande, p95 | até 1,10 × a base | 25 ms contra 26 ms na `main` (limite 28,6) |
| `present()` depois de concluir | sem piora | mediana 6,0 ms contra 14,1 ms |
| Molas | 60 fps no aparelho de referência | só no emulador: recorde p95 25 ms, resumo p95 32 ms |

### Tamanho do APK

Do job `host` do run [40937989893](https://github.com/pedroyuuki/Mooscles/actions/runs/40937989893) (`5d94a7f`, build limpo do CI), por `tools/check-apk-budget.ps1`, sobre a base do CI da `main` em `4f2911c` (23.119.851 bytes):

| Checagem | Bytes | Limite | Folga |
| --- | ---: | ---: | ---: |
| Crescimento do APK debug | 532.492 (0,51 MiB) | 1.048.576 (1 MiB) | 516.084 |
| Fontes (comprimidas) | 645.546 (630 KiB) | 921.600 (900 KiB) | 276.054 |
| Fontes e ícones (classes dos ícones como teto) | 886.883 (866 KiB) | 1.048.576 (1 MiB) | 161.693 |

O APK debug ficou com 23.652.343 bytes. Nenhuma fonte nova; 55 classes de ícone (241.337 bytes, teto do tamanho no dex).

### Dependências

`./gradlew :app:dependencies --configuration debugRuntimeClasspath` em `2ff31b4`, comparado com o da `main` antes da fase ([`deps-diff-final.txt`](2026-10-29-phase3-verificacao/deps-diff-final.txt)): só entram o `project ':screens'` e o `org.jetbrains.kotlinx:kotlinx-datetime:0.8.0` (com o `kotlinx-datetime-jvm`). Nenhuma versão existente mudou. Nada de Material 3 no `:screens`, Lottie, Coil, Vico ou Roborazzi.

### Tokens, plataforma e dados

- `pwsh tools/check-design-tokens.ps1`: limpo, 92 arquivos, agora incluindo o `commonMain` inteiro do `:screens`.
- `grep -rnE "\b(java|javax|android)\.|System\.|String\.format" screens/src/commonMain`: vazio. O `:screens` não tem `expect/actual`.
- `shared/schemas` sem mudança na branch (`git diff 51508a5..HEAD -- shared/schemas` vazio; o CI confere a cada push). Room na v5, nenhuma chave nova de DataStore, `applicationId`, `versionCode` 16, `versionName` 1.4.5 e a assinatura sem mudança.
- `compat-reviewer` com `SEGURO` nas Tasks 4, 5, 6, 8, 9, 10, 25 e 27. A revisão da branch inteira (`git diff origin/main...HEAD`) e o `/code-review` são do controlador, antes do merge.

### Crítica de tela

Ver a [crítica](2026-10-29-phase3-critica-treino.md): capturas do emulador do treino (pendente, em edição, com teclado, com recorde, com dock, expandido, pausado), do resumo, do cartão, das abas e da barra do treino, nos dois temas, em en e pt-BR e a 200% em pt. Três achados altos e três médios foram corrigidos com teste; o médio que faltava (o nó de anúncio do timer como parada do leitor de tela) foi corrigido depois, em `2ff31b4` (nota datada na crítica). Os baixos ficaram com o motivo.

### Defeitos anteriores à fase, achados e corrigidos

- **Barras do sistema claras sobre o tema escuro** depois de girar entre as duas paisagens (`53b13ba`): o `enableEdgeToEdge()` do `androidx.activity` 1.13 reaplica a cor pelo modo noturno do sistema numa mudança de configuração que não recria a Activity. Reproduzido também na `main`. Agora o estilo lê o tema do app (`SystemBarsThemeHostTest`, que falhava antes).

## CI

| Run | Commit | `host` | `ios` |
| --- | --- | --- | --- |
| [40799908563](https://github.com/pedroyuuki/Mooscles/actions/runs/40799908563) | `597384d` (Tasks 4–8) | 706 | `:shared` 221, `:designsystem` 207 |
| [40882509181](https://github.com/pedroyuuki/Mooscles/actions/runs/40882509181) | `a88028f` (Task 28) | 781 | primeiro `ActiveWorkoutFlowTest` verde no simulador: `:screens` 262, `:designsystem` 285 |
| [40910486507](https://github.com/pedroyuuki/Mooscles/actions/runs/40910486507) | `c64900f` (lane da correção iOS da Task 29) | verde | `:screens` 286, `:designsystem` 289, `:shared` 221 |
| [40924139844](https://github.com/pedroyuuki/Mooscles/actions/runs/40924139844) | `53b13ba` (barras do sistema) | **falhou**: 805 testes, 1 falha, a instável `WorkoutCalculationsBatchTimingTest.activeWorkoutRepsPrsOnLargeHistoryAreFast` (limite de tempo de relógio, já registrada na fase 2) | verde: `:shared` 221, `:designsystem` 289, `:screens` 286 |
| [40937989893](https://github.com/pedroyuuki/Mooscles/actions/runs/40937989893) | `5d94a7f` (crítica) | verde: 806 em 137 classes | **falhou** (tentativa 2; a 1 foi cancelada): `:shared` 221 e `:screens` 287 verdes, `:designsystem` 291 com 1 falha, `RestTimerExpandedTest.ringShowsOnlyThePhaseInsideItsClearCircle`: "en Paused 2.0: corner (123.0, 134.0) of Rect.fromLTRB(123.0, 134.0, 238.0, 173.0) outside the clear circle 116.0". O teste é da correção A2 da crítica (`fb154b6`) e rodou no iOS pela primeira vez aqui; no emulador ele passa. O texto "Paused" a 2,0 passa do círculo livre do anel no simulador, onde a fonte escala de forma linear |
| [40939226431](https://github.com/pedroyuuki/Mooscles/actions/runs/40939226431) | `2ff31b4` (anúncio do timer) | cancelado pela regra de concorrência quando o run de `5d94a7f` foi rodado de novo | cancelado |

- **O `ios` não está verde no último commit de produto.** Além da falha de `5d94a7f` acima, o run de `2ff31b4` foi cancelado antes de rodar. A correção do `RestTimerExpandedTest` no iOS (o texto do anel a 2,0 com a escala linear do iOS) fica com o controlador, antes do merge; este commit só traz documentação.
- Os mínimos no fim: `MIN_HOST_TESTS` 807 (a contagem real em `2ff31b4`, medida no Mac), `MIN_IOS_TESTS` 221, `MIN_DS_IOS_TESTS` 294 e `MIN_SCREENS_IOS_TESTS` 287. Os três do iOS são o número de `@Test` em `commonTest`, `uiTest` e `iosTest` de cada módulo, a mesma conta que bateu com o run de `53b13ba` (289, 286 e 221). O commit desta documentação dispara o run que prova `2ff31b4` e esses mínimos.
- Falhas de runs intermediários, todas corrigidas na própria branch: um teste de reticências que no iOS não reticenciava (dados do teste), o arrasto cancelado que o Skiko não entrega (teste condicionado), o `StateRestorationTester` que não existe no iOS (o `ScreenRestoration` do `:screens`) e, na varredura de acessibilidade, a checagem do texto dos botões com 1 px de diferença no iOS e o aviso de recorde fora da janela do simulador. O runner `macos-26` faltou duas vezes (`40894140041`).

## O que não foi verificado

- **iPhone:** nada rodou num iPhone. VoiceOver, háptico, teclado real (o simulador não abre teclado; os testes iOS usam insets injetados, spike S3), o gesto de voltar, como um `touchesCancelled` real chega no deslize, desempenho e revisão visual ficam para a fase 7.
- **Android físico:** nada rodou num aparelho. O dono ainda não respondeu à pergunta 2 da spec (instalar o `preview` e percorrer a lista). Por isso:
  - **TalkBack:** nenhum dos 11 itens da spec foi ouvido com TalkBack, inclusive as pendências da fase 2 (#8, #10 e #11). O que existe é a árvore de acessibilidade e os testes ([`talkback.md`](2026-10-29-phase3-verificacao/talkback.md)). O nó de anúncio do timer sem texto entre anúncios (`2ff31b4`) foi conferido só pela semântica, não com o TalkBack real nem pelo `AccessibilityNodeInfo`. Candidata para a sessão: o nó raiz focável do timer expandido, que no dump junta o texto da tela inteira;
  - **háptico** de cada momento da tabela da spec (o emulador não vibra);
  - **60 fps** nas molas e na rolagem num aparelho de referência (pergunta 1 da spec, ainda sem resposta): só emulador;
  - "Começar" até o treino editável ficou 54 ms acima da `main` na mediana, dentro do ruído do emulador; num aparelho, não medido;
  - a notificação pela tela de bloqueio, Android 8 a 14, e a ação da notificação durante uma instalação de atualização de verdade.
- **Release:** o caminho do APK publicado, assinado com a chave original, para este build. A chave não está neste Mac; a v1.5.0 não foi publicada.
- **Barras do sistema na partida a frio:** por 1 a 2 s, até as preferências carregarem, as barras seguem o modo do sistema. Anterior à fase, registrado sem correção.
- **Textos em inglês com o aparelho em pt-BR (conhecidos):** os nomes de exercício do catálogo (fase 4), as mensagens do updater e do backup (D22, fase 6), as telas antigas (Treinar, rotinas, biblioteca, histórico, recordes) e a prévia antiga do cartão no `:app` ("Workout preview").
- **Capturas do iOS:** geradas pelo teste, sem revisão visual tela a tela; o bloco cinza da `light-1x-record` não foi investigado.
- **O texto do anel do timer expandido no iOS a 2,0:** o teste que prova que ele fica dentro do círculo livre falha no simulador (ver [CI](#ci)). Até a correção, o "Pausado"/"Paused" a 200% pode encostar no traço do anel no iOS.
- **Skeleton e disco cheio na tela, quadro a quadro:** provados pelo trace e pelos testes de host, não vistos na tela (o emulador captura devagar demais).

## Pendências que não bloqueiam o merge

- Achados baixos da crítica (L1–L11), como a falta de dica de deslize, o diálogo que escurece as barras do sistema e a barra do treino com dois relógios: [crítica](2026-10-29-phase3-critica-treino.md#p3-baixa-não-corrigidas-com-o-motivo).
- Minors estacionados nas revisões das tasks, para uma rodada de acabamento ou a fase indicada:
  - `:shared`: um erro sem mensagem cai no texto de `SAVE_FAILED` com o código genérico; os caminhos de `LOCAL_DATA_UNAVAILABLE` do VM sem teste de ponta a ponta; casos de borda do `restoreSet` (posição negativa, série concluída com 0 repetições) sem teste;
  - `InProcessRestTimer`: sem reconciliação na partida nem observador da sessão (exigidos na fase 7, quando ele for o timer do iOS);
  - `:designsystem`: a ordem de leitura conteúdo-depois-fundo de diálogos e sheets só testada pelo `traversalIndex`; o stepper corta o valor quando nem `num.sm` cabe; a tecla de retorno do teclado numérico do iOS (fase 7); conflitos de gesto do deslize com a rolagem para a lista de checagem de aparelho;
  - `:screens`: a recriação da tela no meio de um check pode tocar o háptico de concluir duas vezes (raro); a explicação da convenção de carga duplicada entre `CatalogLabels` e `LoadConvention.explanation`;
  - `preview`: a seção de atualizações de Configurações aparece e não faz nada.
- `WorkoutCalculationsBatchTimingTest` (limite de tempo de relógio) falhou de novo num runner do CI (`40924139844`), sem mudança no código que mede.
- Capturas no histórico do git: a fase acrescenta cerca de 21 MB de PNG em `2026-10-29-phase3-verificacao/` (107 capturas do emulador, 19,2 MB, mais as 28 do simulador iOS, cerca de 2 MB), além de cerca de 3 MB de traces, XML e JSON. Somam-se aos 25 MB da fase 2 no mesmo item da v1.5.0 ("Antes de abrir o PR", item 2).

## Adendo (2026-10-29): revisão final

Depois deste relatório, a branch passou por um `/code-review` (nível alto) contra a `main` e por mais duas correções que só o CI do iOS e a revisão de compatibilidade pegaram. Tudo entrou antes do merge:

- **Revisão de código, 8 correções**, cada uma com um teste que falhava antes: desfazer a remoção de uma série depois de trocar o exercício do card recolocava a série no exercício novo (agora a troca ou a remoção do exercício fecha o desfazer); um erro que chegava depois de o treino já estar finalizado impedia o resumo de abrir; recordes de repetições com e sem assistência dividiam o mesmo aviso; minimizar ou voltar com o Back perdia o estado da tela do treino (campos esvaziados, rascunhos, cards abertos à mão, desfazer pendente); a escolha de descanso podia ficar "pendente" para sempre; as séries anteriores eram montadas para todo o histórico; um parâmetro morto no detalhe do treino; o timer do iOS tentava concluir o descanso a cada tick. Fica para depois: a notificação lê todas as séries do treino para achar a próxima de um exercício (pede uma consulta nova no repositório).
- **Anel do timer expandido no iOS:** com o texto a 200% do iOS (que cresce de forma linear), a palavra da fase encostava no traço do anel. Agora o anel mede o conteúdo e escolhe o maior par de estilos que cabe no círculo; no Android, a 1,0, 1,3 e 2,0, nada muda. A ver numa captura do iOS: a 1,3x o tempo pode descer direto de `num.display` para `num.lg`.
- **Conclusão do descanso no iOS:** uma gravação que falhasse deixava o descanso parado em 0; agora a conclusão é tentada de novo a cada 2 s enquanto o mesmo descanso estiver correndo.
- **Compatibilidade:** `compat-reviewer` na branch inteira e nas correções finais, **SEGURO** (sem mudança de Room, chaves do DataStore, backup, updater ou identidade do app).
- **Verificação final:** CI [40958575207](https://github.com/pedroyuuki/Mooscles/actions/runs/40958575207) todo verde (host 815; iOS `:shared` 224, `:screens` 293, `:designsystem` 295; script de release nas quatro combinações). No emulador, as suítes completas uma vez no fim. O emulador caiu uma vez no meio da suíte do design system (threads da CPU do QEMU sem resposta); depois de religado, a suíte passou inteira. No Mac, o orçamento do APK apareceu acima do limite por causa do dex do build incremental; no build limpo do CI, ficou dentro.
