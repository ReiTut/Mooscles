# Atualização por cima: `main` em `2359ab6` (fase 3, banco v5) para `feat/phase4b-routines` (`4694edc`)

Task 18, Step 2. Feito em 2026-10-30 no `emulator-5556` (AVD `Mooscles_Pixel8_API35`, Android 15), com a rede do emulador desligada desde antes de instalar o APK antigo (`wifi_on = 0`, `mobile_data = 0`, "Active default network: none"). Nenhum aparelho físico foi usado.

**Resultado: passou.** `PRAGMA user_version` foi de 5 para **6**, nenhuma série planejada virou aquecimento (**`warmup != 0`: 0 de 21**), o `local_preferences.preferences_pb` ficou com o **mesmo MD5**, o alarme do descanso manteve o horário, e o export depois da troca é **igual ao de antes** tirando o `exportedAt` e o `"warmup": false` que aparece em cada série planejada.

## APKs

| | Origem | Pacote, `versionCode` / `versionName` | Tamanho | SHA-256 |
| --- | --- | --- | ---: | --- |
| Antes | worktree descartável em `2359ab6` (`:app:assembleDebug`) | `com.mooscles`, 16 / 1.4.5 | 23.652.343 | `0d4aec92…902b` (o mesmo APK que a 4A usou) |
| Depois | `feat/phase4b-routines`, `:app:clean :app:assembleDebug` (22:18; o HEAD de então, `4694edc`, mais os commits só de teste) | `com.mooscles`, 16 / 1.4.5 | 24.387.172 | `be88a0cf…4aa54` |

Os dois foram assinados pela chave debug local deste Mac, que **não** é a de distribuição: a atualização prova a compatibilidade dos dados entre estes dois builds, não que um celular com o APK publicado atualize para este. Mesmo `versionCode`; o `adb install -r` trocou o APK sobre os dados, com o app aberto e o descanso correndo, sem desinstalar nem `pm clear`. O worktree foi apagado no fim.

## Roteiro e como cada coisa foi montada

1. APK antigo instalado, `POST_NOTIFICATIONS` concedida, idioma do app em pt-BR (`cmd locale set-app-locales`).
2. **Dados por fixture, não digitados.** O mesmo `upgrade-fixture.json` da 4A (fora do git) foi importado pela tela de Configurações do app antigo, pelo seletor de documentos real: "Imported 10 workouts, 3 routines and 1 custom exercise". Ele traz **duas pastas** ("My routines" e "Upgrade block"), **três rotinas** ("Upgrade manual", todas as séries digitadas, `inheritPr = false`; "Upgrade record", todas herdando o recorde; e "Upgrade custom", com o exercício personalizado "Upgrade cable fly"), 10 treinos, 4 checkpoints, libras, tema escuro. Só o treino ativo foi feito pela tela: Treinar → "Upgrade manual" → Start, séries 1 (137,79 lb × 8) e 2 (93,7 lb × 7) concluídas, descanso de 5:00 correndo ("2 de 9 séries"). As duas rotinas com herança do recorde e as manuais cobrem a distinção que a 4B toca; "Upgrade record" não foi aberta depois.
3. Export pelo seletor de documentos (SAVE, nome padrão): [`export-before-upgrade.json`](export-before-upgrade.json), "Exported 11 workouts, 3 routines and 1 custom exercise".
4. `run-as … md5sum files/datastore/local_preferences.preferences_pb` e o banco puxado (`adb exec-out run-as com.mooscles cat databases/mooscles.db`, `-wal`, `-shm`), lido com `sqlite3` no Mac.
5. `adb install -r`, abrir o app, repetir tudo, export de novo ([`export-after-upgrade.json`](export-after-upgrade.json)).

## Conferências

| Conferência | Resultado |
| --- | --- |
| `PRAGMA user_version` | 5 antes; **6** depois da primeira abertura (`integrity_check: ok`) |
| `SELECT count(*) FROM routine_sets WHERE warmup != 0` | **0** (de 21 séries planejadas; `warmup IS NULL`: 0) |
| `workout_sets` | 169 antes e depois |
| MD5 de `local_preferences.preferences_pb` | **`77290d7676c0329aa84869c63650938b`** antes, logo depois do `install -r` e 6 s depois da abertura (`local/md5-*.txt`) |
| Alarme do descanso | `RestAlarmReceiver`, `origWhen 2026-10-30 22:21:52.982` antes e depois da troca |
| Treinar | Duas pastas ("Minhas rotinas", "Upgrade block"), "Upgrade manual" (3 exercícios · 9 séries, "Retomar treino") e "Upgrade custom" (2 exercícios · 6 séries), a barra do treino "Treino em andamento, Upgrade manual … Descanso" ([print 01](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-30-phase4b-verificacao/upgrade-01-train-after.png?raw=true)) |
| Editor ("Upgrade custom") | Mesmos 3 conjuntos: série 1 "segue o recorde" sem carga, 93,7 × 7, 99,21 × 6, descanso 5:00, linha "Alvo 3 séries · 6–7 reps · 93,7–99,21 lb" ([print 03](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-30-phase4b-verificacao/upgrade-03-editor.png?raw=true)) |
| Linha de alvo no card do treino | "Alvo 3 séries · 6–8 reps · 93,7–137,79 lb" no "Supino reto com barra", com as séries 1 e 2 ainda concluídas e o descanso correndo ([print 02](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-30-phase4b-verificacao/upgrade-02-workout-target-line.png?raw=true)) |
| Export depois contra antes | `exportedAt` tirado e, de cada uma das 21 séries planejadas, `"warmup": false` tirado: **iguais**, comparados com Python (igualdade de objetos) e com `jq -S` + `diff` (0 linhas) |
| Erros | Nenhum `FATAL EXCEPTION` no logcat |

## Foto do exercício personalizado

No "Upgrade cable fly" (Biblioteca → Meus exercícios → Editar):

- **Galeria.** "Escolher da galeria" abriu o seletor de fotos do sistema (`PhotoPickerActivity`, "This app can only access the photos you select"), com uma imagem enviada por `adb push` + varredura de mídia; escolhida, o formulário mostra a prévia e "Remover foto" ([print 04](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-30-phase4b-verificacao/upgrade-04-photo-gallery-staged.png?raw=true)). Depois de Salvar: `files/exercise_photos/<id>.jpg` de 52.775 bytes, e o detalhe mostra "Foto de Upgrade cable fly" ([print 05](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-30-phase4b-verificacao/upgrade-05-detail-with-photo.png?raw=true)).
- **Câmera.** "Tirar foto" abriu o `com.android.camera2` (câmera virtual do emulador, `CaptureActivity`); obturador e "Done" voltaram ao formulário com a prévia ([print 06](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-30-phase4b-verificacao/upgrade-06-photo-camera-staged.png?raw=true)). Salvar trocou o arquivo (18.352 bytes), `drafts/` vazio.
- **Permissão.** Nenhum pedido de permissão apareceu em nenhum dos dois caminhos, e `CAMERA`, `READ_MEDIA_*` e `READ_EXTERNAL_STORAGE` não estão entre as permissões do pacote.

## Não verificado

- Aparelho físico, Android 8 a 14, e a atualização a partir do APK publicado (chave de distribuição ausente neste Mac).
- A notificação do descanso em segundo plano e o fim do descanso depois da troca (o fim do descanso caiu durante a conferência do editor, sem registro).
- "Upgrade record" (herdando o recorde) não foi aberta no editor depois da troca; só a de "Upgrade custom", que tem uma série que segue o recorde.
- A pasta e o export foram montados por fixture e importação, não digitados na tela do app antigo (ver o roteiro). Os dados do treino ativo, sim, foram feitos na tela.
