# Sem rede, na fase 5A

Task 16, Step 3. Feito em 2026-10-31 no `emulator-5558`, numa instalação nova: `adb uninstall com.mooscles` e `adb install -g` do APK debug da branch (o mesmo da [atualização](upgrade.md)). A rede ficou desligada desde antes da instalação (`wifi_on = 0`, `mobile_data = 0`, "Active default network: none"), porque já estava assim desde o passo da atualização. O idioma do app estava em inglês. Os dados vieram da base grande (`mooscles-large-208.json`), importada pela tela de Configurações: "Imported 208 workouts, 3 routines and 2 custom exercises". Nenhum aparelho físico foi usado.

**Resultado: passou, em tudo o que foi percorrido.**

| Parte | O que foi feito na tela | Resultado |
| --- | --- | --- |
| Recordes | Aba Records | A linha do tempo aparece, e a primeira linha é "PR · Barbell bench press · 259.04 lb × 11 · was 253.53 lb · Load · Dec 23 · Current" ([print](offline-01-records.png)) |
| Busca | "press" e "row" digitados | Só os recordes que casam com a busca, sob o cabeçalho do mês: "December 2023 · 138 records" com "press" e "December 2023 · 46 records" com "row". Os detalhes estão no [device-suites.md](device-suites.md), na checagem da Task 13 |
| Chips | "Current only", depois também "First records", depois os dois desligados | Os chips ficam marcados, com a lista refeita ([print 1](offline-02-current-only.png), [print 2](offline-03-current-and-first.png)) |
| Progresso, cada métrica | A linha do supino, depois Estimated 1RM, Volume e Load | Um gráfico por métrica, cada um com o resumo: 1RM "from 93 pounds … highest 337 pounds … 208 workouts"; Volume "from 6,376.87 pounds … best workout of the week"; Load "from 71.65 … highest 259.04 pounds" ([1RM](offline-04-progress-e1rm.png), [Volume](offline-05-progress-volume.png), [Load](offline-06-progress-load.png)) |
| Progresso, cada período | 3 months, 6 months, 1 year e All | Nos três primeiros, "No workouts with this measure in this period.": a base é de nov. 2023 a jun. 2024, e hoje é 31 out. 2026. Em All, o gráfico volta ([print](offline-07-progress-1-year-empty.png)) |
| "View data" | Em All, na Carga | A tabela substitui o gráfico ("View chart"), com a semana mais nova primeiro: "Week of Jun 3 · 82.67 lb · 82.67 lb × 11", depois May 27 e May 20 ([print](offline-08-view-data.png)) |
| Card com a sparkline | Menu ⋮ → "About the exercise" → "Your history" | "Last time Jun 8 · 82.67 lb × 11", "Best 259.04 lb × 11" com o selo PR, "208 sessions", a sparkline e "View progress" ([print](offline-09-history-card-sparkline.png)) |

Não houve nenhum `FATAL EXCEPTION` no logcat.

Observação, sem correção nesta task: em inglês, o início da semana veio segunda-feira ("week of Nov 13", 13 nov 2023 é uma segunda). Na atualização, em pt-BR, ele veio domingo ("semana de 12 nov").

- O aparelho está em `en-US`, cuja semana começa no domingo.
- O `currentWeekStart()` lê o `Locale.current` do app, e o `cmd locale set-app-locales … en` dá um idioma sem região. Sem região, o CLDR manda começar na segunda.
- A spec (D8) diz "a semana começa no primeiro dia da região do aparelho".
- Com um idioma do app sem região, a região do sistema se perde. Se isso é o esperado é uma decisão de spec, e fica para o controlador. Pela tela de idiomas do Android, normalmente se escolhe um idioma com região, como "English (United States)".

## Código de rede na branch

`git diff main...HEAD` (105 arquivos; `main` em `596c5bd`): procurei nas linhas acrescentadas `https?://`, `URL`, `HttpURLConnection`, `ktor`, `okhttp`, `java.net.`, `INTERNET`, `WebView`, `Uri.parse`, `CAMERA`, `READ_MEDIA` e `READ_EXTERNAL`, e depois `http` e `url` sem diferenciar maiúsculas.

- **Nenhum código novo de rede.** O único acerto foi a palavra "curl" num nome de exercício de teste ("Old cable curl", `ProgressNavigationHostTest`).
- Nenhum manifesto mudou na branch.
- O [deps-diff.txt](deps-diff.txt) está vazio (ver o [apk.md](apk.md)): nenhuma biblioteca nova.
- O `git diff --stat main -- ` dos caminhos de compatibilidade (`data/`, `timer/`, `shared/src/androidMain`, `updates/`, os `AppPreferences.kt`, `shared/build.gradle.kts`, `app/build.gradle.kts`, `shared/schemas`) está vazio.

## Não verificado

Aparelho físico e outras versões do Android.
