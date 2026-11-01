# Fase 1B: atualização por cima, desempenho e tamanho no emulador

- **Data:** 2026-10-28
- **Branch:** `chore/phase1b-shared-module`, os dois APKs do depois foram montados a partir de `23825fa`; `b7986de` só acrescenta o teste de host `AppViewModelInstallHostTest`, que não entra no APK
- **Spec:** [fase 1](../specs/2026-10-27-phase1-shared-base-design.md) · **Plano:** [fase 1B](../plans/2026-10-27-phase1b-shared-module.md) · **Etapa anterior:** [verificação da 1A](2026-10-27-phase1a-verificacao.md)
- **Antes:** APK debug e APK R8 da `main` em `72e835f` (o código de antes da 1B), montados num worktree separado
- **Depois:** APK debug desta branch (`./gradlew :app:assembleDebug`) e o APK R8 (`:app:assemblePerformance`)
- **Prints e exports:** [2026-10-27-phase1b-verificacao/](2026-10-27-phase1b-verificacao/)

## Resumo

- **A atualização por cima não mudou nenhum dado.** O APK da 1B, instalado com `adb install -r` sobre o da 1.4.5 de antes da etapa, abriu o mesmo banco e as mesmas preferências:
  - o dump SQL completo do `mooscles.db` ficou idêntico;
  - os dois arquivos DataStore (`local_preferences` e `app_updates`) ficaram idênticos byte a byte;
  - o export feito depois é idêntico byte a byte ao de antes, tirando o `exportedAt`.

  Isso inclui o treino ativo com quatro séries completas e o timer de descanso rodando: o app mostrou "Pick up where you left off", o alarme ficou no mesmo instante, e o dock chegou a "Rest complete" no prazo salvo.
- **Desempenho: nenhuma piora acima do ruído.** Na abertura a frio não houve regressão, mas também não há diferença conclusiva: as execuções são bimodais e as medianas caem em grupos diferentes (histórico visível 1,71 s no APK novo contra 7,44 s no antigo, depois do primeiro quadro). Nas duas pontas as execuções se dividem em dois grupos, um de 1 a 3 s e outro de 7 a 10 s, então a diferença das medianas mostra só em qual grupo caiu cada execução. A troca kg/lb ficou em 0,45 s contra 0,57 s. No import da base grande, o `present()` ficou em 0,86 s contra 1,00 s, e do toque ao fim do `present()` em 3,61 s contra 3,30 s, com as cinco execuções de cada lado entre 2,0 e 5,6 s. Nenhum ANR.
- **APK debug: +236 bytes** (21.796.480 → 21.796.716). As entradas comprimidas cresceram 8.214 bytes, quase tudo dex. O orçamento era de cerca de 1 MB. O APK R8 tem o mesmo tamanho nos dois, 2.246.209 bytes.
- **R8:** o APK `assemblePerformance` exportou a base grande, importou o próprio arquivo numa instalação limpa e exportou de novo. Os dois exports são iguais tirando o `exportedAt`, e também iguais ao export R8 da 1A. O kotlinx.serialization funciona sob o R8 vindo de um módulo de biblioteca.
- **Um teste novo de host**, `AppViewModelInstallHostTest`, prende o botão Install ao `UpdateManager.requestInstall()`, o caminho que espera o treino e o timer terminarem.

## Ambiente

- Emulador `Mooscles_Pixel8_API35` (`emulator-5554`), Android 15 (API 35), 1080×2400, **GPU do host**, num Mac com M4 e 16 GB. A memória estava apertada (13 a 14,7 GB de 16 GB de swap em uso), e a carga do Mac (`vm.loadavg` de 1 min) ficou entre 7 e 21 durante as medições.
- **Rede do emulador desligada** (`svc wifi disable`, `svc data disable`) desde antes da primeira abertura do APK antigo. Com rede, a 1.4.5 acha a release 1.4.6 publicada e começa a baixá-la, e na Task 9 isso derrubou o emulador. Ver a nota de 2026-10-28 na spec.
- Os quatro APKs são assinados com a chave debug do Mac (SHA-256 `dcc5d4ea…ff71`) e têm `versionCode` 16 e `versionName` 1.4.5 (`aapt2 dump badging`). Por isso o `adb install -r` atualiza por cima nos dois sentidos.
- Os dois APKs debug foram empacotados do zero: o `app-debug.apk` foi apagado antes do `assembleDebug`, como recomenda a 1A. O R8 também foi reempacotado depois de apagar o APK. O Gradle foi parado (`./gradlew --stop`) antes de qualquer passo no emulador.

## Atualização por cima

### Roteiro

1. `adb install` do APK antigo (o app não estava instalado) e permissão de notificação concedida.
2. Dados criados com o app antigo, iguais aos da Task 9:
   - import pela UI do backup sintético `../reports/2026-10-27-phase1a-verificacao/export-before-upgrade.json` (Settings → Import data → Downloads): "Imported 11 workouts, 4 routines and 1 custom exercise". Ele traz duas pastas com quatro rotinas, 10 treinos finalizados com 40 PRs de exercício, um exercício próprio, as preferências lb, tema escuro, descanso automático ligado, descanso padrão 2:30 e a pasta "Upgrade block" selecionada;
   - o backup foi exportado no meio de um treino, então o import já traz um treino ativo de rotina, "Upgrade routine 2", com duas séries completas. Nele foram completadas mais duas: a 3ª série do agachamento e a 1ª do levantamento terra. A segunda reiniciou o timer de 5:00 do levantamento terra, que ficou rodando;
   - o estado do updater é o que a 1.4.5 grava sem rede: `phase` IDLE, nenhuma checagem, `lastInstalledVersion` 16.
3. Export pela UI (Settings → Export data → Downloads): "Exported 11 workouts, 4 routines and 1 custom exercise".
4. Captura com `run-as com.mooscles`: o `.dump` do `sqlite3` do aparelho, `PRAGMA user_version`, `journal_mode`, `integrity_check` e `foreign_key_check`, os dois `.preferences_pb`, `dumpsys alarm` e `dumpsys dbinfo`.
5. `adb install -r` do APK novo às 08:40:20,2, com 3:30 de descanso pela frente (prazo às 08:43:50,821).
6. App aberto, a mesma captura repetida, os prints e um export novo.

### Resultado

| Item | Antes | Depois | Igual? |
| --- | --- | --- | --- |
| Dump SQL completo do `mooscles.db` (576 linhas, 77.447 bytes) | — | — | **Idêntico** (`cmp`) |
| `user_version`, `journal_mode`, `integrity_check`, `foreign_key_check` | 5, wal, ok, vazio | 5, wal, ok, vazio | Sim |
| Linhas no banco | 11 treinos (10 finalizados + 1 ativo), 42 exercícios de treino, 166 séries, 292 exercícios, 2 pastas, 4 rotinas, 8 exercícios de rotina, 24 séries de rotina, 4 checkpoints | as mesmas | Sim |
| `local_preferences.preferences_pb` (preferências e timer) | 456 bytes | 456 bytes | **Idêntico byte a byte** |
| `app_updates.preferences_pb` (estado do updater) | 318 bytes | 318 bytes | **Idêntico byte a byte** |
| Export JSON (98.995 bytes) | [`export-before-upgrade.json`](2026-10-27-phase1b-verificacao/export-before-upgrade.json) | [`export-after-upgrade.json`](2026-10-27-phase1b-verificacao/export-after-upgrade.json) | **Idêntico byte a byte**, tirando o `exportedAt` (a versão não mudou) |
| Timer no DataStore | `timer_deadline` 1793187830821, token 1793187530821, levantamento terra, 300 s, não pausado | o mesmo arquivo | Sim |
| Alarme do timer | `RTC_WAKEUP` de `RestAlarmReceiver` em 08:43:50.821 | o processo novo registrou outro alarme no mesmo instante, 08:43:50.821 | Sim |
| Tela | treino ativo com o timer em ~3:50 ([print 01](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-phase1b-verificacao/01-before-workout-timer.png?raw=true)) | "Pick up where you left off · Upgrade routine 2 · 4 sets logged" e o timer em 3:18 contando ([print 02](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-phase1b-verificacao/02-after-train-resume.png?raw=true)); o treino com 4 séries ([print 03](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-phase1b-verificacao/03-after-workout-timer.png?raw=true)) | Sim |
| Fim do descanso | — | às 08:43:49 o dock mostrava 0:01, e às 08:43:55, "Rest complete", 0:00 ([print 07](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-phase1b-verificacao/07-after-rest-complete.png?raw=true)) | — |
| Histórico, recordes, rotinas | 10 treinos, 160 séries, 40 PRs no banco e no export | History com 10 treinos, 160 séries e 40 PRs ([print 04](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-phase1b-verificacao/04-after-history.png?raw=true)), Records ([print 05](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-phase1b-verificacao/05-after-records.png?raw=true)) e as rotinas da pasta "Upgrade block" ([print 06](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-phase1b-verificacao/06-after-routines.png?raw=true)) | Sim |

O export tem as 292 linhas de exercício, as 2 pastas, as 4 rotinas, as 24 séries planejadas, os 11 treinos, as 42 linhas de exercício de treino, as 166 séries e os 4 checkpoints de PR.

Mudanças nos arquivos que não são dados:

- Antes da troca, o banco estava com o WAL cheio (`mooscles.db` com 12.288 bytes e `-wal` com 358.472). Na primeira abertura, o app novo fez o checkpoint: depois, `mooscles.db` tinha 208.896 bytes e o `-wal`, 0. O dump é o mesmo, porque o `sqlite3` lê o WAL.
- O `mooscles.db.lck` já existia antes, criado pelo Room 2.8 da 1A. As permissões do `-wal` e do `-shm` continuaram `rw-------`.

### `PRAGMA synchronous`

Igual à 1A. O `dumpsys dbinfo` mostra `journalMode=WAL, syncMode=NORMAL` na configuração do pool antes e depois. Depois, o log de operações registra o Room executando `PRAGMA synchronous = NORMAL` ao abrir (08:40:21.810). O `busy_timeout` não apareceu na janela do `dbinfo`.

### Telas

Os prints de History, Records e rotinas foram tirados só depois da troca, e não foram comparados pixel a pixel com o antes, como na 1A. A igualdade dessas telas vem dos dados: o dump, as preferências e o export idênticos, e os números que a tela mostra (10 treinos, 160 séries, 40 PRs) são os do export. A única edição em arquivo de tela foi a reescrita de smart-cast em `RoutineScreens.kt:47`, que gera o mesmo texto: nenhuma tela mudou de aparência.

## Desempenho

Base grande: `mooscles-large-208.json` (208 treinos × 6 exercícios, 12.688 séries, lb, tema escuro), a mesma das verificações anteriores. Método igual ao da 1A e ao da [verificação de 2026-10-27](2026-10-27-pr-off-main-thread-verificacao.md): `atrace` com as seções `Mooscles:present` e os `Choreographer#doFrame` da thread principal, e `Displayed` do `logcat` como primeiro quadro. "Histórico visível" é o fim do primeiro quadro da thread principal depois do fim do `present()`. Os scripts descartáveis da 1A ficaram fora do repositório. Antes e depois foram medidos nesta sessão, em execuções alternadas: cada uma instala o APK por cima (os dados ficam), espera 30 s e faz a medida.

### Abertura a frio (APK debug, sem toques)

| Execução | `Displayed` | `present()` | Histórico visível depois do primeiro quadro | Maior quadro durante o `present()` |
| --- | --- | --- | --- | --- |
| depois ab1 | +2,72 s | 1,00 s | 1,26 s | 140 ms |
| antes ab1 | +3,17 s | 2,17 s | 2,51 s | 176 ms |
| depois ab2 | +3,98 s | 1,25 s | 1,55 s | 156 ms |
| antes ab2 | +5,59 s | 8,04 s | 8,97 s | 320 ms |
| depois ab3 | +4,81 s | 7,87 s | 9,08 s | 314 ms |
| antes ab3 | +5,22 s | 6,65 s | 8,12 s | 336 ms |
| depois ab4 | +7,65 s | 9,76 s | 10,14 s | 425 ms |
| antes ab4 | +5,07 s | 6,34 s | 7,44 s | 372 ms |
| depois ab5 | +3,10 s | 1,54 s | 1,71 s | 254 ms |
| antes ab5 | +3,05 s | 2,64 s | 3,17 s | 332 ms |
| **Mediana depois** | **+3,98 s** | **1,54 s** | **1,71 s** | — |
| **Mediana antes** | **+5,07 s** | **6,34 s** | **7,44 s** | — |

O brief pedia três aberturas de cada lado. Com as três primeiras, o depois teve 1,26, 1,55 e 9,08 s e o antes 2,51, 8,97 e 8,12 s, e os dois lados se dividiam em dois grupos (1 a 3 s e 7 a 10 s). Por isso foram feitas mais duas de cada lado. O grupo de cada execução não segue o APK: o depois teve duas execuções lentas, e o antes, duas rápidas. **Conclusão:** nenhuma piora. A melhora nas medianas não é conclusiva com cinco execuções sob esse ruído. Nenhum quadro da thread principal passou de 425 ms durante o `present()`.

### Import da base grande (instalação limpa por `pm clear`, toques contínuos)

| Execução | Do toque em Import ao fim do `present()` | `present()` completo | Maior quadro (execução inteira) |
| --- | --- | --- | --- |
| antes imp1 | 2,00 s | 0,67 s | 72 ms |
| depois imp1 | 2,25 s | 0,60 s | 130 ms |
| antes imp2 | 2,70 s | 0,73 s | 176 ms |
| depois imp2 | 3,46 s | 0,78 s | 193 ms |
| depois imp3 | 5,23 s | 1,04 s | 290 ms |
| antes imp3 | 3,30 s | 1,00 s | 220 ms |
| antes imp4 | 5,63 s | 1,39 s | 330 ms |
| depois imp4 | 4,62 s | 0,86 s | 804 ms (quadro logo depois do toque, antes do trabalho; `Davey!` de 949 ms com o Mac em carga 14) |
| depois imp5 | 3,61 s | 0,99 s | 171 ms |
| antes imp5 | 4,03 s | 1,10 s | 293 ms |
| **Mediana depois** | **3,61 s** | **0,86 s** | — |
| **Mediana antes** | **3,30 s** | **1,00 s** | — |

Cada import gera dois `present()`: um de menos de 7 ms (estado intermediário) e o completo, que é o da tabela. As três primeiras execuções alternaram antes e depois. Como o depois saiu um pouco atrás no tempo do toque ao fim (2,25, 3,46 e 5,23 s contra 2,00, 2,70 e 3,30 s), foram feitas mais duas de cada lado, em ordem ABBA. Nessas, o antes levou 5,63 e 4,03 s e o depois, 4,62 e 3,61 s. Nas dez execuções, a diferença das medianas (+0,31 s) fica bem dentro da faixa de cada lado (2,0 a 5,6 s), e o `present()` não piorou. Durante o `present()` completo, nenhum quadro da thread principal passou de 28 ms. Os 46 a 55 toques por execução não deram ANR.

### Troca de unidade (Settings, processo aberto, 30 s depois da instalação)

| | Rodada 1 (kg, lb, kg, lb) | Rodada 2 (kg, lb, kg, lb) | Mediana | Maior quadro durante o `present()` |
| --- | --- | --- | --- | --- |
| Depois | 0,46 / 0,41 / 0,36 / 0,87 s | 0,72 / 0,58 / 0,37 / 0,44 s | **0,45 s** | 21–150 ms |
| Antes | 0,55 / 0,56 / 0,60 / 0,50 s | 0,58 / 0,56 / 0,57 / 0,60 s | **0,57 s** | 29–106 ms |

Nenhuma piora.

Nenhum ANR em nenhuma execução: nenhum `ANR in` nem `FATAL` nos `logcat` das medições, e o `dumpsys activity lastanr` continuou mostrando só o ANR de 08:08:43, anterior a esta sessão (o monitor de gestos do sistema, durante a Task 9).

## Tamanho do APK

| APK | Antes (`72e835f`) | Depois (build de `23825fa`, o mesmo código de `b7986de`) | Diferença |
| --- | --- | --- | --- |
| debug | 21.796.480 B | 21.796.716 B | **+236 B** |
| performance (R8) | 2.246.209 B | 2.246.209 B | 0 B |

O tamanho do arquivo esconde parte da diferença, porque o zip tem alinhamento e espaço entre as entradas:

- **debug:** a soma das entradas comprimidas foi de 21.696.385 para 21.704.599 bytes (+8.214). É toda dex, agora em 16 arquivos em vez de 12, porque o código do `:shared` entra como biblioteca;
- **R8:** a soma foi de 2.152.830 para 2.159.496 bytes (+6.666). O `classes.dex` cresceu 5.836 bytes (6.516 comprimidos), e o `baseline.prof`, 76 bytes.

Os dois estão muito abaixo do orçamento de cerca de 1 MB.

## APK R8 (`assemblePerformance`): export e import

O APK R8 do depois (não debuggable, minificado, mesma chave) foi instalado por cima do debug, com a base grande importada. O `pm` aceitou sem desinstalar, e History mostrou 208 treinos, 12.688 séries e 240 PRs ([print 08](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-phase1b-verificacao/08-r8-history-208.png?raw=true)).

1. Export pela UI: "Exported 208 workouts, 3 routines and 2 custom exercises", 2.887.123 bytes.
2. `pm clear` (só no emulador), e import desse arquivo pela UI: "Imported 208 workouts, 3 routines and 2 custom exercises".
3. Novo export: 2.887.123 bytes, **igual ao primeiro tirando o `exportedAt`**. Também é igual, tirando o `exportedAt`, ao segundo export R8 da 1A, que ficou no emulador.

Os treinos, os exercícios de treino, as séries, as rotinas e as pastas do arquivo original estão todos no export, com os mesmos valores. As diferenças são as mesmas da 1A: 9 séries de rotina de PR herdado têm o alvo preenchido pelo PR, e nos 9 exercícios de rotina o `targetReps` e o `targetWeightKg` vêm do PR. É o comportamento normal com `inheritPr`, e não vem do R8. O export também tem os 295 exercícios do catálogo, contra os 8 do arquivo, e 6 checkpoints.

## Teste novo: o Install passa pelo portão do treino

O `compat-reviewer` da Task 7 pediu um teste que prendesse o `AppViewModel.install()` ao `UpdateManager.requestInstall()`. O `UpdateCoordinator` comum não expõe a instalação de propósito. Entrou o `app/src/test/java/com/mooscles/AppViewModelInstallHostTest.kt`, com Room, DataStore e `UpdateManager` reais, no estilo dos outros testes de `AppViewModel`:

- `installDuringAnActiveWorkoutIsDeferredByTheTrainingGate`: com um treino ativo e uma atualização pronta (instalação automática desligada, para só o comando mexer nela), o `installUpdate()` deixa o updater em `DEFERRED`, sem pedido de permissão, sem sessão e sem nenhum `Intent` em `installIntents`;
- `installWithoutPermissionHandsTheSettingsIntentToTheActivity`: sem treino e sem a permissão de instalar, o `installUpdate()` entrega o `Intent` de Settings em `installIntents` e marca `sourcePermissionPending`, coisa que só o `requestInstall()` faz.

`./gradlew :app:testDebugUnitTest --tests "com.mooscles.AppViewModelInstallHostTest"`: 2 testes, 0 falhas. Com o `install()` trocado por um corpo vazio, os dois falharam ("2 tests completed, 2 failed"). O código foi restaurado em seguida.

## O que **não** foi verificado

- **iPhone:** nada roda num iPhone. Não há aparelho nem Xcode neste Mac.
- **`iosTest`:** só roda no simulador do CI (job `ios`). Localmente, o iOS não foi compilado nem testado.
- **Celular físico:** nada rodou em aparelho real. Os APKs deste Mac usam a chave debug local, que não atualiza o app instalado com a chave das releases.
- **Android 16:** o emulador só tem Android 15 (API 35).
- **`PRAGMA synchronous` num aparelho cujo framework usa `FULL`**, e o `busy_timeout` efetivo.
- **O updater com rede:** o caminho em que o app antigo acha uma release e a recusa (coberto na 1A) não foi repetido, porque a rede do emulador ficou desligada.
- **A notificação do timer** antes e depois da troca: o `dumpsys notification` não mostrou as linhas do app. A tela e o alarme foram conferidos.
- **History, Records e rotinas antes da troca:** só há prints depois. A comparação dessas telas é pelos dados (ver [Telas](#telas)).
- **A suíte inteira localmente:** fica com o CI. Localmente rodou só o `AppViewModelInstallHostTest`.
- **A abertura a frio do R8**, TalkBack e teclado.
- **`compat-reviewer` na Task 10:** não foi rodado, porque a Task 10 não alterou código de produção (só testes e documentos). Os dados, o updater e o timer foram cobertos pelos revisores das tarefas anteriores.
- **Job `release-script` do CI:** foi pulado no push final (só havia mudança em documentos). A branch inteira foi coberta pela execução manual (`workflow_dispatch`) 40689232664, com os quatro jobs do release-script verdes.

## Incidentes

- **Toques no lugar errado.** Na segunda rodada de troca de unidade do depois, com o Mac em carga 21, a rolagem de Settings não chegou à posição esperada. Os toques nas coordenadas fixas caíram fora dos botões de unidade, e um deles acionou "Check for updates" (sem rede: "Update check queued. A connection is required…"). Nenhum `present()` foi registrado nessas quatro execuções, e a unidade não mudou. Elas foram descartadas e refeitas com as coordenadas lidas do `uiautomator` antes de cada rodada. Isso aconteceu depois da comparação da atualização por cima, então não afeta o resultado dela.
- `pm clear com.mooscles` foi usado só no `emulator-5554`, onze vezes: dez para os imports medidos e uma no teste do R8. A cada vez, a permissão de notificação foi concedida de novo.
- O emulador terminou com o APK R8 do depois e a base grande importada por ele, e com a rede desligada.
