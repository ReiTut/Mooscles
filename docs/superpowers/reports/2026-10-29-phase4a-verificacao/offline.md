# Sem rede

Task 22, Step 3. Feito em 2026-10-30 no `emulator-5556` (Android 15), com o APK debug da branch em `f4032f9` (`d730718d…79e8`).

**Resultado: passou.** Com a rede desligada desde antes da instalação, a biblioteca, a busca, os filtros, o detalhe (figura, mapa e dicas), o formulário e o seletor funcionaram inteiros, e a branch não traz código de rede novo.

## No emulador

A rede estava desligada antes da instalação e ficou assim até o fim: `settings get global wifi_on` = 0, `mobile_data` = 0, `dumpsys connectivity` com "Active default network: none" e `ping 8.8.8.8` com "Network is unreachable". `adb uninstall com.mooscles` e `adb install` do APK da branch: uma instalação nova, sem dados, em inglês.

| Tela | O que fiz | Resultado |
| --- | --- | --- |
| Biblioteca | Aba Library | "287 exercises", "All", lista com mídia e "músculo · equipamento" ([print](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/offline-01-library.png?raw=true)) |
| Busca | "supino inclinado", em português numa tela em inglês | "5 exercises": Incline barbell bench press, Incline dumbbell bench press, Plate-loaded incline chest press… ([print](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/offline-02-search.png?raw=true)) |
| Filtros | Chip "Back" e, na folha de equipamento, "Barbell" | "Equipment · 1", "Clear filters", "9 exercises": Barbell seal row, Barbell shrug… ([print](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/offline-03-filters.png?raw=true)) |
| Detalhe | "Barbell bench press" pela busca "bench" | Figura do piloto desenhada, chips Barbell, Chest e "Total load", mapa com o peito em destaque ([print](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/offline-04-detail.png?raw=true)); "Primary: chest. Secondary: triceps, shoulders.", as 4 dicas de "How to do it", "You haven't done this exercise yet." e "Add to routine" ([print](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/offline-05-detail-map-cues.png?raw=true)) |
| Formulário | "Create exercise": nome "Offline cable fly", Chest, Cable, Save | Salvou e abriu o detalhe do exercício novo ([form](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/offline-06-form.png?raw=true), [salvo](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/offline-07-form-saved.png?raw=true)) |
| Seletor | Treinar → "Start empty workout" → "Add exercise", busca "offline" | O seletor achou "Offline cable fly, Chest, Cable, custom" ([print](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/offline-08-picker.png?raw=true)); escolhido, entrou no treino com 2 séries ([print](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/offline-09-picked.png?raw=true)) |

O logcat (zerado antes da abertura) não teve `FATAL EXCEPTION`, `UnknownHostException`, `SocketException` nem `ConnectException`. Depois, o app foi desinstalado para a instalação do build R8 da medida de desempenho.

## No código

`git diff main...HEAD` (`739a800...fa996b4`), linhas acrescentadas, procurando `https?://`, `URL`, `URL(`, `HttpURLConnection`, `ktor`, `okhttp`, `java.net.`, `android.permission.INTERNET`, `URLConnection`, `WebView` e `Uri.parse` (saída em `local/network-scan.txt`, fora do git):

- Sete ocorrências, nenhuma é código de rede: o endereço do repositório do MuscleMap no `PROVENANCE.txt`, no texto da licença (`musclemap_mit.txt`) e num teste que confere esse texto (`LicenseFilesTest`); o namespace `http://www.w3.org/2000/svg` nos dois SVG de origem e no gerador `tools/illustrations/gen_body_map.py`; e o link do run do CI no `apk-base.md`.
- Nenhum manifesto mudou.
- O `debugRuntimeClasspath` do `:app` é idêntico ao da `main` ([deps-diff.txt](deps-diff.txt), vazio): nenhuma biblioteca nova, de rede ou não.

## Não verificado

- Aparelho físico e Android 8 a 14.
- O modo avião do sistema (desliguei Wi-Fi e dados com `svc`, que dá o mesmo "sem rede" ao app).
- A instalação nova em pt-BR sem rede: a nova foi em inglês. O pt-BR foi visto na atualização por cima, também sem rede ([upgrade.md](upgrade.md)).
