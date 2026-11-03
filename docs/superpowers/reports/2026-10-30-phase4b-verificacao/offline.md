# Sem rede, na fase 4B

Task 18, Step 3. Feito em 2026-10-30 no `emulator-5556`, instalação nova (`adb uninstall` e `adb install -g` do APK de `4694edc`), com `wifi_on = 0` e `mobile_data = 0` desde antes da instalação (a rede ficou desligada desde o passo da atualização). Idioma do app em inglês. Nenhum aparelho físico.

**Resultado: passou, em tudo o que foi percorrido.**

| Parte | O que foi feito na tela | Resultado |
| --- | --- | --- |
| Treinar, pastas | Aba Treinar vazia ("No routines in this folder"), Folders → New folder → "Offline1" → Create | pasta criada e selecionável |
| Editor | New routine "Offline2" dentro da pasta, abrir o editor | editor abriu vazio ("Routine without exercises") |
| Seletor múltiplo | "Add exercises" → três linhas marcadas → botão "Add 3 exercises" ([print](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-30-phase4b-verificacao/offline-01-picker-multi.png?raw=true)) | os três entraram, cada um com 2 séries planejadas e descanso 2:00 ([print](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-30-phase4b-verificacao/offline-02-editor-three.png?raw=true)) |
| Séries planejadas | reps da série 1 do primeiro exercício → 10 | linha "Target 2 sets · 10 reps" |
| Treino com a linha de alvo | Start workout | "0 of 6 sets", e no card "Target 2 sets · 10 reps" ([print](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-30-phase4b-verificacao/offline-03-workout-target-line.png?raw=true)); depois, Discard workout |
| Foto | Biblioteca → Create exercise → nome, "Choose from gallery" (seletor do sistema, sem pedido de permissão), Chest, Cable, Save | `files/exercise_photos/<id>.jpg` de 52.775 bytes; o detalhe mostra "Photo of …" ([print antes de salvar](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-30-phase4b-verificacao/offline-04-new-exercise-photo.png?raw=true)) |

A câmera não depende de rede e já foi percorrida na [atualização por cima](upgrade.md). Sem `FATAL EXCEPTION` no logcat.

Nota de execução: o teclado do emulador (Gboard) corrige o texto digitado por `adb shell input text`, por isso os nomes ficaram "Offline1", "Offline2" e "Offline3m" (um "m" sobrou da correção): não é um defeito do app.

## Código de rede na branch

`git diff main...HEAD` (183 arquivos, `main` em `1580143`, que já tem a 4A), só as linhas acrescentadas e o texto `https?://`, `URL`, `HttpURLConnection`, `ktor`, `okhttp`, `java.net.`, `INTERNET`, `WebView`, `Uri.parse`, `CAMERA`, `READ_MEDIA`, `READ_EXTERNAL`:

- **Nenhum código novo de rede.** Os únicos acertos de código são `Uri.parse(uri)` em `app/src/main/java/com/mooscles/photos/AndroidPhotoCapture.kt` (URI `content://` ou `file:` da foto, não de rede) e o teste que o cobre, mais `testTag`s com a palavra "CAMERA" (`PhotoRowTags.CAMERA`).
- Manifest: uma linha só, `<queries><intent><action android:name="android.media.action.IMAGE_CAPTURE" />`, para a câmera, sem permissão nova. `PhotoManifestHostTest` vigia que `CAMERA`, `READ_MEDIA_*` e `READ_EXTERNAL_STORAGE` não apareçam no manifesto mesclado.
- `deps-diff.txt` vazio (ver [apk.md](apk.md)): nenhuma biblioteca nova.

## Não verificado

Aparelho físico e outras versões do Android. O fluxo da câmera sem rede foi percorrido só na atualização por cima (câmera virtual do emulador).
