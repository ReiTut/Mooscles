# Atualização por cima: `main` (`739a800`) para `feat/phase4a-library` (`f4032f9`)

Task 22, Step 2. Feito em 2026-10-30 no `emulator-5556` (AVD `Mooscles_Pixel8_API35`, Android 15), com a rede do emulador desligada o tempo todo (`wifi_on = 0`, `mobile_data = 0`, "Active default network: none", `ping` sem rota). Nenhum aparelho físico foi usado.

**Resultado: passou.** O banco continuou na v5, o `local_preferences.preferences_pb` ficou com o mesmo MD5 (e os mesmos bytes), o alarme do descanso manteve o horário, e o export depois da troca é igual ao de antes, tirando o `exportedAt`. A biblioteca mostrou o personalizado e os nomes em pt-BR, e o treino ativo voltou com as mesmas séries e os nomes traduzidos.

## APKs

| | Commit | Pacote, `versionCode` / `versionName` | Tamanho | SHA-256 |
| --- | --- | --- | ---: | --- |
| Antes | `main` em `739a800` (num worktree descartável; o app é o mesmo do merge da fase 3, `2359ab6`: `git diff 2359ab6 739a800` não muda `app`, `shared`, `designsystem` nem `screens`) | `com.mooscles`, 16 / 1.4.5 | 23.652.343 | `0d4aec92…902b` |
| Depois | `feat/phase4a-library` em `f4032f9` (`:app:clean :app:assembleDebug`) | `com.mooscles`, 16 / 1.4.5 | 24.102.511 | `d730718d…79e8` |

Os dois foram assinados pela chave debug local deste Mac, que **não** é a de distribuição. A atualização prova a compatibilidade dos dados entre os dois builds, não que um celular com o APK publicado consiga atualizar para este. Com o mesmo `versionCode`, o `adb install -r` trocou o APK sobre os dados, sem desinstalar nem `pm clear`.

## Roteiro

1. Instalação do APK de antes, `POST_NOTIFICATIONS` concedida, idioma do app em pt-BR (`cmd locale set-app-locales com.mooscles --locales pt-BR`).
2. Import de `local/upgrade-fixture.json` (fora do git), gerado por `local/make_upgrade_fixture.py` (fora do git) a partir do export de antes da fase 3: o treino ativo tirado, **duas pastas** ("My routines" e "Upgrade block"), **três rotinas** ("Upgrade manual", com todas as séries digitadas, `inheritPr = false`; "Upgrade record", na outra pasta, com todas herdando o recorde, `inheritPr = true`; e "Upgrade custom", com o exercício personalizado), **um exercício personalizado** ("Upgrade cable fly", peito, polia), 10 treinos finalizados, 4 checkpoints de alvo, libras, tema escuro e descanso automático de 150 s. O app de antes mostrou "Imported 10 workouts, 3 routines and 1 custom exercise". As pastas, as rotinas e o personalizado vieram do import, não foram digitados na tela.
3. Treinar → "Upgrade manual" → Start. Concluí as séries 1 (137,79 lb × 8) e 2 (93,7 lb × 7) do supino. O descanso de 5:00 ficou correndo ([print 01](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/upgrade-01-before-workout.png?raw=true): "2 de 9 séries"; no build de antes os nomes dos exercícios ainda saem em inglês).
4. Export pelo seletor de documentos: [`export-before-upgrade.json`](export-before-upgrade.json) ("Exported 11 workouts, 3 routines and 1 custom exercise").
5. `run-as com.mooscles md5sum files/datastore/local_preferences.preferences_pb` e `sqlite3 databases/mooscles.db 'PRAGMA user_version;'`, depois `adb install -r` do APK de depois com o app aberto e o descanso correndo, e a abertura do app.

**Primeira tentativa descartada.** Na primeira rodada usei o fixture sem ajuste. A base sintética da 1A traz, além dos ids `catalog_…`, quatro cópias não personalizadas com ids sem o prefixo (`barbell_bench_flat` e outras três), e as rotinas apontavam para elas. Desde a v1.0.0 o catálogo só grava ids `catalog_…`, então uma instalação real não tem essas linhas. O build novo mostra o nome gravado para um id fora do catálogo (de propósito, `ExerciseNames.display`), e o treino saiu em inglês. Os dados também chegaram iguais nessa rodada (mesmo MD5 `46cda2ab…c26c`, banco v5), mas ela não servia para conferir os nomes. Refiz tudo com o fixture apontando para os ids do catálogo (o script tem as duas etapas). As capturas dessa rodada ficaram em `local/upgrade-run1-legacy-ids/`.

## Conferências depois da troca

| Conferência | Resultado |
| --- | --- |
| `PRAGMA user_version` | **5** antes, logo depois do `install -r` e depois da primeira abertura |
| MD5 de `local_preferences.preferences_pb` | **`94d143d2a2201fe6648d989e690d675a`** antes, logo depois do `install -r`, 5 s depois da abertura e no fim do roteiro. Os bytes antes e depois são idênticos (`cmp`). Chaves presentes: `pounds`, `theme`, `auto_rest`, `default_rest`, `selected_routine_folder` e as `timer_*` (`timer_name` "Barbell bench press", `timer_workout` e `timer_workout_exercise` do treino ativo) |
| Alarme do descanso | `RestAlarmReceiver` com `origWhen 2026-10-30 05:33:14.811` antes e depois da troca |
| Treinar | "PICK UP WHERE YOU LEFT OFF · Upgrade manual · 2 sets logged" e a barra do treino: "Treino em andamento, Upgrade manual, 2 minutos. Descanso, 3 minutos e 17 segundos" ([print 02](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/upgrade-02-after-launch.png?raw=true)). A aba Treinar ainda é a tela antiga, em inglês (fase 4B) |
| Biblioteca, nomes em pt-BR | "Biblioteca", "288 exercícios" (287 do catálogo e o personalizado). "Usados recentemente": Supino reto com barra, Remada curvada pronada com barra, Agachamento livre (barra alta), Levantamento terra convencional; "Todos" começa em "Abdominal ajoelhado na polia" ([print 03](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/upgrade-03-library.png?raw=true)) |
| Biblioteca, o personalizado | "Meus exercícios": "1 exercício", "Upgrade cable fly", "Peito · Polia", selo "Personalizado" ([print 04](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/upgrade-04-library-mine.png?raw=true)) |
| Detalhe do personalizado | Título "Upgrade cable fly", chips Polia, Peito e "Carga total", seção Músculos ([print 05](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/upgrade-05-custom-detail.png?raw=true)) |
| Detalhe de um exercício do piloto | "Supino reto com barra": ilustração de início e fim, chips Barra, Peito e "Carga total" ([print 06](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/upgrade-06-pilot-detail.png?raw=true)); mapa com "Principais: peito. Secundários: tríceps, ombros.", as 4 dicas em pt-BR em "Como fazer", e "Seu histórico" com "Última vez 9 out · 137,79 lb × 8", "Melhor 137,79 lb × 8" e "10 sessões" ([print 07](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/upgrade-07-pilot-detail-map-cues.png?raw=true)) |
| Treino ativo | Mesmo treino, "2 de 9 séries", "Supino reto com barra", série 1 (137,79 lb × 8) e 2 (93,7 lb × 7) concluídas, série 3 (99,21 × 6) pendente; o dock: "Descanso · Supino reto com barra", ainda correndo ([print 08](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-29-phase4a-verificacao/upgrade-08-after-workout.png?raw=true)) |
| Export depois | [`export-after-upgrade.json`](export-after-upgrade.json). Sem o `exportedAt`, o JSON é igual ao de antes (comparação de objetos e `diff` do JSON ordenado, sem diferenças): 288 exercícios, 2 pastas, 3 rotinas, 7 exercícios e 21 séries de rotina, 11 treinos, 43 exercícios e 169 séries de treino, 4 checkpoints, as mesmas preferências |
| Erros | Nenhum `FATAL EXCEPTION` no logcat |

## Não verificado

- Aparelho físico, Android 8 a 14, e a atualização a partir do APK publicado, assinado com a chave de distribuição (este Mac não tem essa chave).
- A notificação do descanso em segundo plano e o fim do descanso depois da troca: a fase 4A não mexe no timer, e a fase 3 verificou esse caminho.
