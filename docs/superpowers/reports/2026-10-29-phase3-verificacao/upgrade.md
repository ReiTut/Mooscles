# Atualização por cima: `main` (`4f2911c`) para `feat/phase3-active-workout` (`e482e1d`)

Task 30, Steps 1 e 2, mais o checklist de aparelho deixado pelas Tasks 24 e 25. Feito em 2026-10-29 no `emulator-5556` (AVD `Mooscles_Pixel8_API35`, Android 15, `sdk_gphone64_arm64`, 1080 × 2400, GPU do host). Nenhum aparelho físico foi usado.

**Resultado: passou.** As séries, o `local_preferences.preferences_pb` (mesmo MD5), o prazo do descanso e o export chegaram iguais ao build novo. A notificação de descanso em andamento apareceu ao ir para segundo plano, com as três ações, e o resumo do treino mostrou os números certos.

## Step 1: testes instrumentados do `:app`

`./gradlew :app:connectedDebugAndroidTest` no `emulator-5556`, antes de tudo (a task desinstala o app no fim): BUILD SUCCESSFUL. O XML ([`connectedDebugAndroidTest.xml`](connectedDebugAndroidTest.xml)) tem 25 testes, 0 falhas, 0 erros e 2 pulados, que são os dois ajudantes opt-in de fixture de desempenho (`ThreadComparisonFixtureTest` e `WorkoutPerformanceFixtureTest`).

- `UiFlowTest.routineToWorkoutRestSummaryAndHistory`: passou (7,4 s).
- `UiFeatureSmokeTest`: os 2 passaram (`realKeyboardKeepsTimerAndFocusedInputAccessibleWithoutChangingItsSession` 6,1 s e `foldersAndRepeatedDiscardUseTheCorrectPageAndDoNotRestoreDiscardedSessions` 4,7 s).
- Também passaram `LegacyInstallOpenTest` (2), `WorkoutRepositoryTest` (8), `RestTimerPersistenceTest` (8), `DecimalTextDeviceTest` e `SearchFoldingDeviceTest`.

## APKs

| | Commit | Pacote, `versionCode` / `versionName` | SHA-256 |
| --- | --- | --- | --- |
| Antes | `main` em `4f2911c`, num worktree descartável no scratchpad (`./gradlew :app:assembleDebug`) | `com.mooscles`, 16 / 1.4.5 | `8053cce8…5b28` |
| Depois | `feat/phase3-active-workout` em `e482e1d` (`./gradlew :app:assembleDebug`) | `com.mooscles`, 16 / 1.4.5 | `297b26a0…b52d` |

Os dois foram assinados pela chave debug local deste Mac (`DC:C5:D4:EA…`), que **não** é a chave de distribuição. A atualização prova a compatibilidade dos dados entre os dois builds, não que um celular com o APK publicado consiga atualizar para este. O `versionCode` é o mesmo, então o `adb install -r` substituiu o APK sobre os dados existentes, sem desinstalar nem `pm clear`.

## Roteiro

1. `svc wifi disable` e `svc data disable` antes da primeira abertura. A rede ficou desligada até o fim dos Steps 2 a 4.
2. Instalação do APK de antes, `POST_NOTIFICATIONS` concedida e idioma do app em pt-BR (`cmd locale set-app-locales com.mooscles --locales pt-BR`). As Configurações apareceram em português.
3. Import de uma base sintética, `upgrade-fixture.json`. Ela é o export da 1A (`2026-10-27-phase1a-verificacao/export-before-upgrade.json`) sem o treino ativo, com a rotina 1 renomeada para "Upgrade three", um terceiro exercício (agachamento com barra alta, 3 séries de 100/90/80 kg, descanso de 90 s), libras e a pasta padrão selecionada. O script que a gerou ficou fora do repositório. O app mostrou "Imported 10 workouts, 4 routines and 1 custom exercise".
4. Treinar → "Upgrade three" → Start. Concluí 4 séries: supino 1 (137,79 lb × 8), remada 1 (159,84 lb × 8), agachamento 1 (220,46 lb × 8) e, por último, supino 2 (93,7 lb × 7). Removi a série 3 do agachamento. O descanso de 5:00 do supino ficou correndo ([print 01](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/upgrade-01-before-workout.png?raw=true): "4 Sets done", "4800.57 lb").
5. Export pelo seletor de documentos: [`export-before-upgrade.json`](export-before-upgrade.json).
6. `run-as com.mooscles md5sum files/datastore/local_preferences.preferences_pb`, depois `adb install -r` do APK de depois, com o app aberto e o descanso correndo. Depois abri o app.

## Preferências (`local_preferences.preferences_pb`)

O MD5 foi **`5ef9f420de4cdb11f72557b620433965`** antes da troca, logo depois do `install -r` e de novo 4 s depois da primeira abertura do build novo.

| Chave | Valor (igual antes e depois) |
| --- | --- |
| `pounds` / `theme` / `auto_rest` / `default_rest` | true / dark / true / 150 |
| `selected_routine_folder` | `default` |
| `timer_deadline` | 1793290072186 (13:07:52.186) |
| `timer_duration` / `timer_interval_duration` | 300000 / 300000 |
| `timer_token` | 1793289772186 |
| `timer_paused` / `timer_finished` | 0 / false |
| `timer_name` | Barbell bench press |
| `timer_workout` / `timer_workout_exercise` | `a880653a-…` / `eddd235c-…` |

O `dumpsys alarm` mostrava o alarme `RestAlarmReceiver` em `origWhen 2026-10-29 13:07:52.186`, inexato (`window=+3m44s`), porque o emulador nega `SCHEDULE_EXACT_ALARM`.

## O que o build novo mostrou

- **Treinar** ([print 02](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/upgrade-02-after-launch.png?raw=true)): "Pick up where you left off: Upgrade three, 4 sets logged". A barra do treino ficou acima das abas: "Treino em andamento, Upgrade three, 2 minutos. Descanso, 3 minutos e …", com "Pular descanso".
- **As mesmas séries** ([print 03](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/upgrade-03-after-workout.png?raw=true), [07](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/upgrade-07-after-cards.png?raw=true) e [08](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/upgrade-08-after-squat-card.png?raw=true)): o cabeçalho mostra "4 de 8 séries" (9 planejadas menos a removida). Pela árvore de acessibilidade:
  - supino: série 1 "carga 137,79 libras, 8 repetições, concluída", série 2 "carga 93,7 libras, 7 repetições, concluída", série 3 não concluída;
  - remada: série 1 "carga 159,84 libras, 8 repetições, concluída", séries 2 e 3 não concluídas;
  - agachamento: "1 de 2 séries", com a série 1 "carga 220,46 libras, 8 repetições, concluída, recorde pessoal". A série removida não voltou.
- **O dock chegou a "Descanso concluído" no horário salvo** ([print 06](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/upgrade-06-rest-complete.png?raw=true)). Amostras do relógio do emulador: às 1793290069643 o dock mostrava 0:02, e às 1793290072303 (117 ms depois do `timer_deadline` salvo) mostrava "Descanso concluído 0:00". As amostras ficaram cerca de 2,6 s uma da outra, então a precisão desta prova é de uma amostra. Depois do fim, o store tinha `timer_finished = true` e `timer_deadline = 0`, com as outras chaves iguais.
- **A notificação em andamento ao ir para segundo plano**, logo depois do primeiro uso do build novo:
  - `dumpsys notification`: id 3, canal `rest_progress`, importância 2 (baixa), `ONGOING_EVENT|ONLY_ALERT_ONCE`, `when=1793290072186` (o prazo salvo), `showChronometer=true`, `chronometerCountDown=true`, `timeout=PT3M10.916S` (o restante mais a margem de 2 s), título "Descanso", texto "Barbell bench press · a seguir série 3";
  - 3 ações: "−15 s", "+15 s" e "Pular" ([print 04](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/upgrade-04-notification-shade.png?raw=true) e [05](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/upgrade-05-notification-actions.png?raw=true)). No print 04, às 13:04:51, o cronômetro mostrava 03:01, o mesmo que faltava para 13:07:52;
  - tocar na notificação abriu a rota do treino ([print](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/upgrade-05-notification-tap-opens-workout.png?raw=true)) com uma só `MainActivity` na pilha. Com o app na frente, a notificação sumiu (0 registros `com.mooscles`).
- **Finalizar e o resumo**: o diálogo mostrou "4 séries em 3 exercícios · 7 min. 4 séries não concluídas ficam de fora." ([print 09](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/upgrade-09-finish-dialog.png?raw=true)). O resumo ([print 10](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/upgrade-10-summary.png?raw=true)) mostrou "Treino concluído", "Upgrade three", "qui., 29 out · 13:01–13:09", Duração 8 min, Séries 4, Volume 4.800,57 lb (o mesmo "4800.57 lb" que o build antigo mostrava antes da troca), Exercícios 3. Recorde: "High-bar back squat · 220,46 lb × 8 · antes 181,88 lb". Também apareceu em "Primeiros registros" a linha "High-bar back squat · 220,46 lb × 8", que é o primeiro recorde de repetições nessa carga. A melhor série por exercício: supino 137,79 × 8, remada 159,84 × 8, agachamento 220,46 × 8.

## Export depois, comparado ao de antes

[`export-after-upgrade.json`](export-after-upgrade.json), exportado pelo build novo depois de finalizar ("Exported 11 workouts, 4 routines and 1 custom exercise"). Comparação linha a linha por id com o [`export-before-upgrade.json`](export-before-upgrade.json):

- `app` (1.4.5, 16), `preferences`, `exercises` (292), `routineFolders` (2), `routines` (4), `workoutExercises` (43) e `workoutSets` (168 = 160 do histórico + 8): **iguais**, campo a campo.
- `exportedAt`: diferente (16:03:48 e 16:10:37 UTC), como previsto.
- `workouts`: só o treino finalizado mudou (`status` ACTIVE → FINISHED, `endedAt` null → 1793290173915).
- Efeito do treino finalizado no PR herdado: o agachamento de 100 kg × 8 virou o novo recorde. Os 2 itens de rotina e as 2 séries de rotina com `inheritPr = true` desse exercício ("Upgrade routine 2" e "Upgrade routine 4") passaram o alvo de 82,5 para 100 kg, e o checkpoint de alvo do agachamento foi de 82,5 kg × 8 para 100 kg × 8. É o mesmo comportamento de `inheritPr` registrado na 1A e na 1B, e vem do treino finalizado, não da troca de APK.

Nenhuma outra diferença.

## Checklist de aparelho (Tasks 24 e 25), no emulador

Feito com o APK debug da branch depois do Step 2 (o mesmo emulador, Android 15, pt-BR, libras). Num treino novo de "Upgrade three", com descanso de 5:00. "Verificado" aqui quer dizer verificado **só no emulador, Android 15**.

| # | Item (revisão da Task 25) | Resultado |
| --- | --- | --- |
| 1 | Cronômetro do sistema conta para baixo e chega a 0:00 junto do alerta de fim | Verificado no Android 15. Na gaveta, o cronômetro bate com o prazo salvo (print 04). Com o processo vivo e o app em segundo plano, prazo 1793290776046: a id 3 estava lá às …775518 e tinha sumido às …776895, e a id 1 ("Descanso acabou", "Próxima: Barbell bench press, série 2", canal `rest`) estava postada às …778098, com amostras a cada ~1,3 s. Android 8 e 12: não verificado. |
| 2 | Alarmes exatos negados, processo morto (`am kill`), depois do prazo | Verificado. O `SCHEDULE_EXACT_ALARM` já vem negado (alarme com `window=+41s`). Prazo …867093, processo morto antes. A id 3 estava lá 2,2 s antes do prazo e tinha sumido 2,4 s depois (o `timeout` dela era o restante + 2 s), então o cronômetro não chegou a contar abaixo de zero. Nada ficou na gaveta até o alarme inexato disparar, 42,2 s depois do prazo. O processo voltou e postou a id 1 aos 44,5 s. |
| 3 | −15 s, +15 s e Pular pela gaveta, e com o processo morto | Verificado na gaveta, sem tela de bloqueio (o emulador não tem bloqueio configurado). Com o processo morto por `am kill`, +15 s subiu o `timer_deadline`, o `when` da notificação e o `origWhen` do alarme juntos (…791046 → …806046, 13:19:51 → 13:20:06), e o processo voltou. Dois −15 s levaram tudo para …776046. Pausado, +15 s e −15 s mudaram o `timer_paused` (278396 → 293396 → 278396) e o subtexto ("4:54 · pausado" → "4:39 · pausado"). Pular limpou o prazo e o alarme e removeu a notificação. Pela tela de bloqueio: não verificado. |
| 4 | ±15 s durante a instalação de uma atualização (`INSTALLING`) | Não verificado: precisa de uma atualização de verdade sendo instalada. |
| 5 | Ação numa notificação de uma sessão já encerrada | Não verificado: finalizar e descartar removem a notificação, e não montei uma notificação órfã. |
| 6 | Tocar na notificação: (a) app em segundo plano, (b) processo morto com a tarefa nos recentes, (c) sem tarefa | Os três verificados. Em todos abriu a rota do treino com uma só `MainActivity` na pilha (`Hist #0`), e Voltar levou à casca (Treinar com a barra do treino). (a) print 05 do Step 2; (b) `am kill` e toque ([print](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/device-05-tap-after-kill-opens-workout.png?raw=true)); (c) tarefa removida (`am stack remove`), processo morto, toque abriu uma tarefa nova ([print](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/device-06-tap-no-task-opens-workout.png?raw=true)). |
| 7 | Rotação com descanso correndo: nada pisca na gaveta | Verificado com amostragem. Durante 4 rotações (90°, 0°, 270°, 0°) com o app na frente e o descanso correndo, 184 leituras do `dumpsys notification` em 15 s (uma a cada ~82 ms) nunca viram a id 3. Um post e um cancelamento em menos de ~80 ms passariam despercebidos. |
| 8 | Saída para uma tela do sistema aberta pelo próprio app (seletor do SAF, instalador) | Não verificado. |
| 9 | Descanso pausado: "1:08 · pausado" no cabeçalho, sem horário | Verificado: subtexto "4:39 · pausado", `showChronometer=false`, `timeout=PT0S` ([print](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/device-02-paused-notification.png?raw=true)). |
| 10 | Ações sem ícone viram botões de texto | Verificado no Android 15 (print 05). Android 8 a 14: não verificado. |
| 11 | pt-BR: textos, nomes dos canais e o canal `rest` renomeado numa instalação existente | Verificado. Textos em pt-BR (título, texto, ações, "pausado", "Descanso acabou"). O canal `rest` foi criado pelo build da `main` como "Rest timer" e, depois da troca, aparece como "Timer de descanso" com importância 4 e vibração, sem mudar. O `rest_progress` é "Descanso em andamento", importância 2, sem vibração. |
| 12 | Android 13+ com a permissão de notificação negada | Verificado. Com a permissão revogada e negada no pedido do app, um descanso em segundo plano até depois do prazo não postou nada. O timer terminou normalmente (`timer_finished = true`) e o logcat não teve nenhum `FATAL EXCEPTION`. |
| 13 | Android 14+: arrastar para tirar a notificação em andamento | Verificado. Arrastada, ela some e não volta sozinha (0 registros depois de 5 s em segundo plano). Na próxima mudança, que foi voltar ao app e sair de novo, ela voltou. |

Itens das Tasks 24 e 25 que não são da lista acima:

- **Paisagem com navegação de 3 botões, 90° e 270°** (Task 24): verificado com a navegação de 3 botões ligada por overlay. A 90° ([casca](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/landscape-90-shell.png?raw=true), [treino](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/landscape-90-workout.png?raw=true)), a barra do treino com o botão de pular e as quatro abas terminam em x = 2274, onde começa a barra de navegação do sistema, e a faixa do lado do recorte (x < 132) sai na cor da barra, `bgRaised` (35, 40, 37). A 270° ([casca](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/landscape-270-shell.png?raw=true), [treino](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/landscape-270-workout.png?raw=true)), as abas e a barra começam em x = 126, depois da barra de navegação à esquerda. **Achado, não reproduzido depois:** nessa sessão do APK debug, em 3 de cerca de 10 rotações (as duas de 270° e uma de 90°, com descanso correndo e depois de aberturas pela notificação), a janela ficou com `LIGHT_STATUS_BARS LIGHT_NAVIGATION_BARS` sobre o tema escuro. Os ícones da barra de status saem escuros sobre o fundo escuro (dá para ver nos prints de 270°), e a barra de 3 botões fica clara. Não reproduzi em mais 28 rotações (APK R8 e debug da branch, na casca e no treino, com navegação por gestos e de 3 botões), nem em 6 rotações do APK R8 da `main`. O código que define a aparência (`app/.../ui/Theme.kt`, `SideEffect` com `isAppearanceLightStatusBars = !dark`) não mudou nesta fase. Fica como item para investigar, sem correção nesta task.
- **Paisagem num aparelho com recorte lateral:** o AVD tem recorte, e a faixa do lado do recorte saiu em `bgRaised` a 90°. Num aparelho de verdade: não verificado.
- **CLEAR_TOP com outra Activity por cima (folha de compartilhar, instalador)** e **o tempo limite do receiver contra um import de backup segurando o portão**: não verificados.

## Não verificado

- Aparelho físico, Android 8 a 14, tela de bloqueio com PIN e a atualização a partir do APK publicado, assinado com a chave de distribuição. Este Mac não tem essa chave.
- Os itens marcados como não verificados na tabela acima.
- O tempo do alarme de fim com alarmes exatos permitidos (o emulador os nega por padrão).
