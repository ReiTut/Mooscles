# Atualização por cima: `main` em `596c5bd` para `feat/phase5a-progress`

Task 16, Step 2. Feito em 2026-10-31 no `emulator-5558` (AVD `Mooscles_Pixel8_API35`, Android 15). A rede do emulador ficou desligada desde antes de instalar o APK antigo (`svc wifi disable`, `svc data disable`: `wifi_on = 0`, `mobile_data = 0`, "Active default network: none"). Nenhum aparelho físico foi usado.

**Resultado: passou.**

- `PRAGMA user_version` ficou em **6** antes e depois, e o schema não mudou (a 5A não tem migração).
- O `local_preferences.preferences_pb` ficou com o **mesmo MD5**.
- O alarme do descanso manteve o horário, e o treino ativo continuou com as duas séries concluídas.
- As contagens e os volumes são os mesmos.
- O export depois da troca é **igual ao de antes**, tirando o `exportedAt`.
- A aba Recordes e o progresso do supino abriram com os números do histórico.

## APKs

| | Origem | Pacote, `versionCode` / `versionName` | Tamanho | SHA-256 |
| --- | --- | --- | ---: | --- |
| Antes | worktree descartável em `596c5bd` (`git rev-parse main`; o `03b43ad`, de onde a branch saiu, mais só o ajuste de um teste de tempo de host), `:app:assembleDebug` | `com.mooscles`, 16 / 1.4.5 | 24.469.208 | `b765f87c…e784` |
| Depois | `feat/phase5a-progress`, o `app-debug.apk` da suíte do `:app` (código de `a0eab5f`; o `3b07456` só muda um teste de `androidTest`) | `com.mooscles`, 16 / 1.4.5 | 25.312.408 (build incremental; o limpo está no [apk.md](apk.md)) | `b87e0c2f…69fd` |

- Os dois APKs foram assinados pela chave debug local deste Mac, que **não** é a de distribuição. A atualização prova que os dados são compatíveis entre estes dois builds, não que um celular com o APK publicado atualize para este.
- O `versionCode` é o mesmo nos dois. O `adb install -r` trocou o APK sobre os dados, com o treino aberto e o descanso correndo, sem desinstalar e sem `pm clear`.
- O worktree foi apagado no fim.

## Roteiro

1. APK antigo instalado com `install -g` e idioma do app em pt-BR (`cmd locale set-app-locales com.mooscles --locales pt-BR`).
2. **Base grande importada pela tela.** Configurações → "Importar dados" → seletor de documentos → `mooscles-large-208.json`. O app mostrou "Imported 208 workouts, 3 routines and 2 custom exercises".
   - O arquivo traz libras e tema escuro, conferidos na tela depois.
   - O descanso automático vem desligado no arquivo, e eu o liguei em Configurações antes do treino.
3. **Treino ativo feito na tela.**
   - Treinar → "Verif routine 1" → Começar. O descanso do supino foi para 5:00, pela folha de descanso, com o stepper.
   - As séries 1 (259,04 lb × 11) e 2 (88,18 lb × 8) foram concluídas: "2 de 9 séries", e o descanso começou ("Descanso · Barbell bench press 4:56").
4. Export pelo seletor de documentos (SAVE, nome padrão): "Exported 209 workouts, 3 routines and 2 custom exercises". O arquivo ficou em `local/upgrade/export-before.json` (2,9 MB, fora do git, SHA-256 `ac74a395…2a78`).
5. `run-as com.mooscles md5sum files/datastore/local_preferences.preferences_pb`, e o banco puxado (`adb exec-out run-as com.mooscles cat databases/mooscles.db`, `-wal` e `-shm`), lido com `sqlite3` no Mac.
6. `adb install -r` do APK da branch às 17:09:47, com o descanso correndo. Depois: o MD5 de novo, a abertura do app, as conferências, o export de novo (`local/upgrade/export-after.json`, SHA-256 `6cf9a34b…0586`) e o banco puxado de novo.

## Conferências

| Conferência | Antes | Depois |
| --- | --- | --- |
| `PRAGMA user_version` / `integrity_check` | 6 / ok | **6** / ok |
| Schema (`.schema` sem as tabelas internas) e `identity_hash` | `70e5fedd…0a66` | **igual** (`diff` vazio; `local/upgrade/schema-*.sql`) |
| `workouts` (finalizados + ativo) | 209 (208 + 1) | **209 (208 + 1)** |
| `workout_sets` (concluídas) | 12.697 (12.690) | **12.697 (12.690)** |
| MD5 de `local_preferences.preferences_pb` | `ba0a258a4e04ac4d9fa3bc675ab582c6` | **o mesmo** logo depois do `install -r`, depois da abertura e depois do export (`local/upgrade/md5-*.txt`) |
| Alarme do descanso (`RestAlarmReceiver`) | `origWhen 2026-10-31 17:13:15.790` | **o mesmo** depois da troca. O descanso terminou com o app aberto, e o alarme foi cancelado às 17:13:15.801 (fim tratado no processo) |
| Treino ativo | "Verif routine 1", 2 de 9 séries, descanso correndo | **o mesmo**: "Retomar treino" no card e a barra "Treino em andamento, Verif routine 1 … Descanso". No treino, as séries 1 e 2 concluídas e "3:06" restando às 17:10:09 ([print 01](upgrade-01-train-after.png), [print 02](upgrade-02-workout-after.png)) |
| Export depois contra antes | | `exportedAt` tirado: **iguais**, por `jq -S` + `diff` (0 linhas) e por igualdade de objetos em Python |
| Erros | | Nenhum `FATAL EXCEPTION` no logcat |

### Recordes e volumes, pela mesma apresentação

Os recordes não ficam no banco: são recalculados das séries pela apresentação. Para contá-los antes e depois, um teste de host descartável (Robolectric, fora do git) abriu o banco puxado com o `AppViewModel` real e contou o `UiState`. Para o banco de antes, ele rodou no código da `main` (no worktree); para o de depois, no código da branch.

| Medida (`UiState`) | Antes (código da `main`) | Depois (código da branch) |
| --- | ---: | ---: |
| Treinos no histórico | 208 | **208** |
| Séries (concluídas) | 12.688 (12.688) | **12.688 (12.688)** |
| Eventos de recorde (`WorkoutUi.records`, somados) | 480 | **480** |
| Recordes atuais (`UiState.records`) | 246 | **246** |
| Treino ativo, séries (concluídas) | 9 (2) | **9 (2)** |

O volume de três treinos, calculado pelo `sqlite3` nos dois bancos (carga × repetições das séries concluídas) e lido na tela do histórico depois da troca ([print do detalhe](upgrade-06-history-detail-after.png)):

| Treino | `sqlite3`, antes | `sqlite3`, depois | Na tela, depois |
| --- | ---: | ---: | ---: |
| Session 207 (8 jun 2024, o mais novo) | 19.162,5 kg = 42.246,08 lb | **o mesmo** | 42246.08 lb |
| Session 206 (7 jun 2024) | 17.885 kg = 39.429,68 lb | **o mesmo** | 39429.68 lb |
| Session 100 (22 fev 2024) | 35.770 kg = 78.859,35 lb | **o mesmo** | 78859.35 lb |

O cabeçalho do histórico, depois da troca, diz "208 Workouts · 12688 Completed sets · 240 Exercise PRs" ([print](upgrade-05-history-after.png)). Os 240 são a soma de exercícios com recorde por treino (`prExerciseCount`), que não é a mesma medida dos 246 recordes atuais.

### Recordes e progresso na tela, depois da troca

- **Aba Recordes** ([print 03](upgrade-03-records-after.png)): no topo, "Barbell bench press · 259,04 lb × 11 · antes 253,53 lb · Carga · 23 dez · Atual". No banco, a maior série de trabalho do supino é 117,5 kg × 11 (= 259,04 lb), registrada pela primeira vez em 23 dez 2023.
- **Progresso do supino**, aberto pela linha ([print 04](upgrade-04-progress-after.png)):
  - Carga total, Carga, Tudo.
  - Destaque de 82,67 lb, que é a maior série do treino mais novo no banco.
  - O resumo do gráfico diz "máximo 259,04 libras na semana de 17 de dezembro de 2023; tendência em alta; 208 treinos". No banco, o supino está nos 208 treinos finalizados.

## Não verificado

- Aparelho físico, Android 8 a 14, e a atualização a partir do APK publicado (a chave de distribuição não está neste Mac).
- A notificação do fim do descanso com o app em segundo plano depois da troca. O descanso terminou com o app em primeiro plano.
- As contagens de antes vieram de um teste de host sobre o banco puxado, com o código da `main`, não da tela do app antigo. O app antigo não mostra o total de recordes. Na tela antiga, nada foi contado a mais, além do que está na tabela de conferências.
- O treino ativo foi feito na tela. A base veio de importação, não foi digitada.
