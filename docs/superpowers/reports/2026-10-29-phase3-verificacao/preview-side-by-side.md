# `preview` lado a lado com o debug

Task 30, Step 3. Feito em 2026-10-29 no `emulator-5556` (Android 15), logo depois do Step 2, com o APK debug da branch (`e482e1d`) já instalado e com dados (11 treinos, 4 rotinas, o treino do Step 2 finalizado). A rede do emulador continuou desligada.

**Resultado: passou.**

## APK

`./gradlew :app:assemblePreview` em `e482e1d`: `com.mooscles.preview`, `versionCode` 16, `versionName` `1.4.5-preview`, rótulo "Mooscles Preview", SHA-256 `5a03533d…f3a4`. É assinado pela chave debug local, como o debug. Foi instalado com `adb install` (sem `-r`, é outro pacote), e depois `POST_NOTIFICATIONS` foi concedida.

## Conferências

| Pedido | Resultado |
| --- | --- |
| Dois ícones | `cmd package query-activities -a MAIN -c LAUNCHER` lista `com.mooscles/.MainActivity` e `com.mooscles.preview/com.mooscles.MainActivity`. A gaveta de apps mostra "Mooscles" e "Mooscles Preview" lado a lado ([print 01](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/preview-01-two-icons.png?raw=true)). |
| "Mooscles Preview" abre vazio | Abriu em Treinar com "No routines in My routines" ([print 02](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase3-verificacao/preview-02-opens-empty.png?raw=true)). No banco dele (`run-as com.mooscles.preview sqlite3`): 0 treinos, 0 rotinas e só os 287 exercícios do catálogo. O banco do `com.mooscles` continuou com 11 treinos e 4 rotinas. |
| O `md5` do `local_preferences` do `com.mooscles` não muda | `16a12f082f4290c83e72b484629ac435` antes de instalar o preview e de novo depois de abri-lo e esperar 20 s. O preview nem criou `files/datastore/` próprio. |
| O `logcat` do preview não mostra checagem de atualização | Veja abaixo. |

## Nenhuma checagem de atualização

- O [`preview-logcat.txt`](preview-logcat.txt) (`logcat -d --pid` do processo do preview, 34 linhas, da abertura até 20 s depois) não tem nenhuma linha de `MoosclesUpdater`, `UpdateCheckWorker`, GitHub, release, HTTP, socket ou DNS. As únicas linhas do WorkManager são a inicialização padrão da biblioteca (`WM-WrkMgrInitializer`, `WM-Schedulers`, `WM-ForceStopRunnable`), que roda pelo `androidx.startup` em qualquer app que tenha a biblioteca. Nenhum trabalho foi enfileirado.
- `dumpsys jobscheduler`: o `com.mooscles` (uid 10259) tem 2 jobs do `SystemJobService` do WorkManager, a checagem periódica e a única. O preview (uid 10260) não tem nenhum.
- O preview não tem `no_backup/androidx.work.workdb` (a pasta `no_backup/` está vazia), nem `files/datastore/app_updates.preferences_pb`. O debug tem os dois.

A rede estava desligada, então uma checagem que tentasse sair teria falhado. A prova de que ela nem foi tentada é a ausência de trabalho agendado, de banco do WorkManager e do estado do updater, além do `logcat` limpo.

## Não verificado

- O preview com a rede ligada.
- Num aparelho físico.
