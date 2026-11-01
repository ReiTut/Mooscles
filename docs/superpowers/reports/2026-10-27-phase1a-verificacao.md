# Fase 1A: atualização por cima, desempenho e tamanho no emulador

- **Data:** 2026-10-27
- **Branch:** `chore/phase1a-toolchain-room`, código verificado em `6718ce3`
- **Spec:** [fase 1](../specs/2026-10-27-phase1-shared-base-design.md) · **Plano:** [fase 1A](../plans/2026-10-27-phase1a-toolchain-room.md)
- **Antes:** APK debug da `main` em `7795a51` (o código de antes da 1A), montado num worktree separado
- **Depois:** APK debug desta branch (`./gradlew :app:assembleDebug`) e o APK R8 (`:app:assemblePerformance`)
- **Prints e exports:** [2026-10-27-phase1a-verificacao/](2026-10-27-phase1a-verificacao/)

## Resumo

- **A atualização por cima não mudou nenhum dado.** O APK novo, instalado com `adb install -r` sobre o antigo, abriu o mesmo banco e as mesmas preferências. O dump SQL completo do `mooscles.db` ficou idêntico, os dois arquivos DataStore (`local_preferences` e `app_updates`) ficaram idênticos byte a byte, e o export feito depois da atualização é idêntico byte a byte ao de antes, tirando o `exportedAt`. Isso inclui o treino ativo com séries completas e o timer de descanso rodando: o timer voltou com o mesmo prazo e terminou na hora certa ("Rest complete").
- **Desempenho: nenhuma piora acima do ruído, e a apresentação ficou mais rápida com o processo já aberto.** O `present()` caiu de 0,76–1,51 s para 0,27–0,42 s na troca kg/lb, e de 1,99 s para 0,31 s no import da base grande. Na abertura a frio, o ruído da máquina dominou a medida (detalhes abaixo). Nas execuções alternadas antes/depois, o histórico apareceu de 5,6 a 7,3 s depois do primeiro quadro no APK novo e de 7,5 a 8,8 s no antigo. Nenhum ANR.
- **APK debug: +1,78 MiB** (19.927.075 → 21.796.480 bytes, +9,4%). Passa do orçamento de cerca de 1 MB da spec. A Task 1 tinha medido +1,32 MiB contra um APK antigo que carregava ~0,47 MB de espaço morto. O crescimento (`androidx.window` via Compose UI 1.12, necessário para a fase 3) foi aceito com o número corrigido.
- **Uma diferença visual:** o indicador do item selecionado na barra de navegação inferior ficou mais estreito, de 168 para 147 px (64 → 56 dp), por causa do Material 3 novo. Fora isso, as telas comparadas são iguais pixel a pixel, tirando o relógio e a contagem do timer. O gesto de voltar e o edge-to-edge se comportam igual no Android 15.
- **R8:** o APK `assemblePerformance` exportou a base grande, importou o próprio arquivo numa instalação limpa e exportou de novo. O segundo export é igual ao primeiro, tirando o `exportedAt`.

## Ambiente

- Emulador `Mooscles_Pixel8_API35` (`emulator-5554`), Android 15 (API 35), 1080×2400, 4 GB, **GPU do host** (`-gpu host`), num Mac com M4.
- Os dois APKs são assinados com a chave debug do Mac (SHA-256 `dcc5d4ea…ff71`), `versionCode` 16 e `versionName` 1.4.5 nos dois. O depois tem `compileSdk` 37 e `targetSdk` 35. Por isso o `adb install -r` atualiza por cima nos dois sentidos.
- Durante a sessão, a carga do Mac variou muito por causa de outros programas (Chrome, IDE). `vm.loadavg` foi de ~1 a 28, e o `qemu` chegou a 250% de CPU. Isso afetou principalmente as aberturas a frio (ver [Desempenho](#desempenho)).

## Atualização por cima

### Roteiro

1. `adb uninstall` e instalação do APK antigo; permissão de notificação concedida.
2. Dados criados com o app antigo:
   - import de um backup sintético, gerado por um script descartável fora do repositório e sem dado pessoal: duas pastas ("My routines" e "Upgrade block") com duas rotinas cada, 10 treinos finalizados com recordes (40 PRs de exercício), 1 exercício próprio, e as preferências lb, tema escuro, descanso automático ligado, descanso padrão 2:30 e a pasta "Upgrade block" selecionada;
   - um treino ativo iniciado pela UI a partir de "Upgrade routine 2", com duas séries completas. A segunda série reiniciou o timer de descanso de 5:00, que ficou rodando;
   - o estado do updater é o real: o app encontrou a release 1.4.6 (build 17) em `ReiTut/Mooscles` e a recusou, porque a assinatura é outra ("Update could not finish… different signing identity");
   - export pela UI (Settings → Export data → Downloads).
3. Captura do estado com `run-as com.mooscles`: o dump `.dump` do `sqlite3` do aparelho, `PRAGMA user_version`, `journal_mode`, `integrity_check` e `foreign_key_check`, os dois `.preferences_pb`, `dumpsys alarm` e `dumpsys dbinfo`.
4. `adb install -r` do APK novo às 21:39:08, com o timer marcando ~3:37.
5. App aberto, a mesma captura repetida, os prints e um novo export.

### Resultado

| Item | Antes | Depois | Igual? |
| --- | --- | --- | --- |
| Dump SQL completo do `mooscles.db` (576 linhas) | — | — | **Idêntico** (`diff` vazio) |
| `user_version`, `journal_mode`, `integrity_check`, `foreign_key_check` | 5, wal, ok, vazio | 5, wal, ok, vazio | Sim |
| Treinos / séries no banco | 11 (10 finalizados + 1 ativo) / 166 | 11 / 166 | Sim |
| `local_preferences.preferences_pb` (preferências e timer) | 446 bytes | 446 bytes | **Idêntico byte a byte** |
| `app_updates.preferences_pb` (estado do updater) | 169.681 bytes | 169.681 bytes | **Idêntico byte a byte** |
| Export JSON (98.997 bytes) | `export-before-upgrade.json` | `export-after-upgrade.json` | **Idêntico byte a byte**, tirando o `exportedAt` (o `versionCode`/`versionName` não mudaram) |
| Alarme do timer | `RTC_WAKEUP` em 21:42:45.143 | o processo novo registrou outro alarme no mesmo instante, 21:42:45.143 | Sim |
| Tela | treino ativo, timer em 4:57 | "Pick up where you left off · 2 sets logged" e o timer em 3:28 contando ([print 02](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-phase1a-verificacao/02-after-train-resume.png?raw=true), [print 03](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-phase1a-verificacao/03-after-workout-timer.png?raw=true)) | Sim |
| Fim do descanso | — | às 21:43:01 o dock mostrava "Rest complete", 0:00 ([print 10](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-phase1a-verificacao/10-after-rest-complete.png?raw=true)) | — |
| Histórico, recordes, rotinas | 10 treinos, 160 séries, 40 PRs | iguais ([print 04](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-phase1a-verificacao/04-before-history.png?raw=true), [print 05](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-phase1a-verificacao/05-after-history.png?raw=true)) | Sim |

O export inclui as 292 linhas de exercício, as 2 pastas, as 4 rotinas, as 24 séries planejadas, os 11 treinos, as 42 linhas de exercício de treino, as 166 séries e os 4 checkpoints de PR.

Mudanças nos arquivos que não são dados:

- O Room 2.8 criou um arquivo novo, `databases/mooscles.db.lck` (0 bytes). É o arquivo de trava que o Room usa ao configurar o banco. Ele fica para trás se o app voltar para a versão antiga, sem efeito (ver abaixo).
- Depois da abertura pelo app novo, o `-wal` e o `-shm` ficaram com permissão `rw-rw----` em vez de `rw-------`. O dono continua sendo o app (`u0_a208`).

**Volta para a versão antiga, testada por acaso:** para comparar a navegação, o APK antigo foi instalado de novo por cima do novo, com os mesmos dados. O dump SQL continuou idêntico ao de antes da atualização, e o app antigo abriu normalmente. Depois o APK novo foi reinstalado, e o dump continuou idêntico. Esse caminho não é suportado nem foi pedido, e só foi conferido nesse ponto.

### `PRAGMA synchronous`

- **Antes (framework, SupportSQLite):** o `dumpsys dbinfo` mostra a configuração do pool `journalMode=WAL, syncMode=NORMAL`. O framework do Android 15 deste emulador já usa `NORMAL` com WAL.
- **Depois (Room 2.8 + `AndroidSQLiteDriver`):** a mesma configuração do pool (`journalMode=WAL, syncMode=NORMAL`). Além disso, o `dbinfo` registra o Room executando `PRAGMA synchronous = NORMAL` numa conexão logo ao abrir. No bytecode do `BaseRoomConnectionManager` 2.8.5, o Room executa `PRAGMA journal_mode = WAL`, `PRAGMA synchronous = NORMAL` (ou `FULL` sem WAL) e `PRAGMA busy_timeout = 3000` quando o valor atual é menor. O `busy_timeout` não apareceu na janela de 20 operações do `dbinfo`, então **não foi medido**.
- **O que muda para o usuário:** neste emulador, nada, porque os dois ficam em `NORMAL`. Num aparelho cujo framework usa `FULL` com WAL (o `config_db_wal_sync_mode` varia por fabricante e versão), o Room passa a forçar `NORMAL`. Com WAL, `NORMAL` não corrompe o banco, mas uma queda de energia pode perder a última transação confirmada. Isso depende do aparelho e **não foi verificado em aparelho físico**.
- O valor foi lido pela configuração do pool e pelo log de operações do `dbinfo`. Um `sqlite3` externo abriria outra conexão, com o próprio padrão, e não mostraria o valor das conexões do app.

### Telas, gesto de voltar e edge-to-edge (Android 15)

Os mesmos prints foram tirados antes e depois e comparados pixel a pixel, por um decodificador PNG descartável:

| Tela | Linhas diferentes | O que muda |
| --- | --- | --- |
| Treino ativo | status bar, linha de estatísticas (tempo decorrido), dock do timer, barra inferior | relógio e contagem; barra inferior (abaixo) |
| History | status bar, dock do timer, barra inferior | relógio, contagem, barra inferior |
| Records | idem | idem |
| Detalhe do treino | só status bar e dock do timer | — |
| History depois do gesto de voltar (borda esquerda → centro) a partir do detalhe | status bar, dock do timer, barra inferior | o gesto voltou para a lista nos dois ([print 06](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-phase1a-verificacao/06-before-back-gesture.png?raw=true), [print 07](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-phase1a-verificacao/07-after-back-gesture.png?raw=true)) |

- **Edge-to-edge:** a status bar, a barra de gestos e o espaço do conteúdo atrás delas ficaram nas mesmas linhas de pixel. Só os ícones do relógio mudam.
- **Barra de navegação inferior:** com a tela parada (4 s depois do toque), o indicador do item selecionado tem 168 px de largura antes e 147 px depois, na mesma posição ([print 08](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-phase1a-verificacao/08-before-records-nav.png?raw=true), [print 09](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-phase1a-verificacao/09-after-records-nav.png?raw=true)). Os pixels do rótulo também mudam um pouco (linhas 2260–2284). Vem do Material 3 do BOM 2026.09.00. É uma mudança perceptível, embora pequena, que contradiz o antiobjetivo da spec. Aceita, porque a fase 2 redesenha a navegação; registrado na spec.
- **Comportamento igual nos dois, registrado para não ser confundido com regressão:** estando em Settings, aberto a partir de History, tocar em History na barra inferior deixa o app em Settings, porque o `restoreState` restaura a pilha salva. Antes e depois fazem o mesmo.

## Desempenho

Base grande: `mooscles-large-208.json` (208 treinos × 6 exercícios, 12.688 séries, lb, tema escuro), a mesma das verificações anteriores, importada pela UI. Método igual ao da [verificação de 2026-10-27](2026-10-27-pr-off-main-thread-verificacao.md): `atrace` com as seções `Mooscles:present` e os `Choreographer#doFrame` da thread principal, e `Displayed` do `logcat` como primeiro quadro. Abertura a frio sem toques. A comparação daquele relatório usava renderização por software. Aqui o antes e o depois foram medidos nesta sessão, com a GPU do host.

### Abertura a frio (APK debug)

| Execução | Carga do Mac (1 min) | `Displayed` | `present()` | Histórico visível depois do primeiro quadro | Maior quadro durante o `present()` |
| --- | --- | --- | --- | --- | --- |
| antes cs1 | baixa | +4,72 s | 4,34 s | 7,41 s | 283 ms |
| antes cs2 | baixa | +4,52 s | 5,26 s | 7,94 s | 318 ms |
| antes cs3 | baixa | +4,62 s | 4,53 s | 7,29 s | 324 ms |
| depois cs1 | média (load do emulador 0,8–3,8) | +5,90 s | 10,46 s | 12,62 s | 250 ms |
| depois cs2 | média | +5,68 s | 9,37 s | 10,95 s | 321 ms |
| depois cs3 | média | +6,19 s | 11,08 s | 12,78 s | 255 ms |
| antes cs4 | alta (load do emulador 4,7–6) | +7,25 s | 14,39 s | 17,01 s | 1.098 ms |
| antes cs5 | alta | +5,35 s | 12,01 s | 14,30 s | 244 ms |
| antes cs6 | alta | +6,73 s | 6,33 s | 7,66 s | 249 ms |
| alternado: depois ab1 | 7,8 | +29,75 s | não registrado | — | — |
| alternado: antes ab1 | 28,3 | +5,16 s | 6,76 s | 8,81 s | 337 ms |
| alternado: depois ab2 | 15,1 | +4,83 s | 3,52 s | 5,63 s | 323 ms |
| alternado: antes ab2 | 11,1 | +5,85 s | 6,41 s | 7,47 s | 308 ms |
| alternado: depois ab3 | 9,1 | +4,64 s | 5,23 s | 7,27 s | 391 ms |
| alternado: antes ab3 | 7,2 | +4,59 s | 4,28 s | 7,79 s | 296 ms |

O primeiro bloco do depois (10,9 a 12,8 s) parecia uma piora. Repetido com o APK antigo logo em seguida, sobre os mesmos dados e com a mesma carga, o antes deu 7,7 a 17,0 s. A piora era da máquina. Para separar as duas coisas, as últimas seis execuções alternam os APKs (instalação por cima, 30 s de espera, uma abertura a frio). Nelas o depois ficou entre 5,6 e 7,3 s e o antes entre 7,5 e 8,8 s. A execução "depois ab1" pegou o Mac sobrecarregado: `Displayed` em 29,8 s, `Davey!` de 17 e 23 s, e o trace terminou antes do `present()`. Ela é descartada como ruído. **Conclusão:** nenhuma piora visível acima do ruído. Com 2 execuções válidas do depois no bloco alternado, não dá para afirmar uma melhora na abertura a frio.

Nenhum quadro da thread principal passou de 700 ms durante o `present()`, exceto um de 1.098 ms no "antes cs4", do APK antigo, sob carga alta. Nenhum ANR em nenhuma execução (`dumpsys activity lastanr`: "no ANR has occurred since boot", e nenhum `ANR in` no `logcat`).

### Import da base grande (instalação limpa)

| | `present()` completo | Do toque em Import ao fim do `present()` | Maior quadro (execução inteira) | ANR com toques contínuos |
| --- | --- | --- | --- | --- |
| Antes | 1,99 s | 4,4–5,4 s (o toque foi registrado só com precisão de segundo) | 174 ms | não (92 toques) |
| Depois | 0,31 s | 0,82 s | 59 ms | não (94 toques) |

Foi uma execução de cada lado. O processo do depois estava aberto havia ~1 min, e o do antes havia ~2 min.

### Troca de unidade (Settings, processo aberto)

| | `present()` | Maior quadro durante o `present()` |
| --- | --- | --- |
| Antes, 1ª rodada (kg, lb, kg, lb) | 1,05 / 0,97 / 1,06 / 1,51 s | 45–157 ms |
| Depois (kg, lb, kg, lb) | 0,38 / 0,42 / 0,33 / 0,27 s | 20–55 ms |
| Antes, 2ª rodada, nas mesmas condições do depois (reinstalado por cima, 30 s de espera) | 1,04 / 0,76 / 1,04 / 0,99 s | 31–61 ms |

O `present()` com o processo aberto ficou 2,5 a 3,5 vezes mais rápido no APK novo, com o mesmo resultado na tela. A causa não foi investigada. As hipóteses são o código gerado pelo Kotlin 2.4 e as bibliotecas novas.

## Tamanho do APK

| APK | Antes (`7795a51`) | Depois (`6718ce3`) | Diferença |
| --- | --- | --- | --- |
| debug | 19.927.075 B | 21.796.480 B | **+1.869.405 B (+1,78 MiB, +9,4%)** |
| performance (R8) | 2.105.700 B (medido pela Task 1) | 2.246.209 B | +140.509 B (+137 KiB) |

Os dois APKs debug foram empacotados do zero. O `app-debug.apk` que estava em `app/build/` tinha **33,4 MB**, porque o empacotamento incremental do AGP deixa espaço morto no zip: ~11,7 MB de buracos entre as entradas. Ele foi apagado e empacotado de novo, e caiu para 21,8 MB. Quase todo o crescimento é dex: 19,05 → 20,86 MB comprimidos.

**Risco para a release, que já existia antes:** o `build.ps1` e o `tools/publish-release.ps1` exigem um commit limpo no git, mas não rodam `gradle clean` antes do `assembleDebug`. Se a pasta `app/build/` tiver builds incrementais anteriores, o APK publicado pode sair maior que o necessário. O conteúdo e a assinatura continuam válidos. Fica como recomendação: apagar `app/build/outputs/apk/debug/app-debug.apk`, ou rodar `clean`, antes do build da release.

## APK R8 (`assemblePerformance`): export e import

O APK R8 (não debuggable, minificado, mesma chave) foi instalado por cima do debug. O `pm` aceitou sem desinstalar. Com a base grande:

1. Export pela UI: "Exported 208 workouts, 3 routines and 2 custom exercises", 2.887.123 bytes.
2. `pm clear` (só no emulador), import desse arquivo pela UI: "Imported 208 workouts, 3 routines and 2 custom exercises".
3. Novo export: igual ao primeiro, tirando o `exportedAt` e a versão. Tem as mesmas chaves no topo, em `data` e nas séries que o export do APK debug.

Os treinos, os exercícios de treino, as séries, as rotinas e as pastas do arquivo original estão todos no export do R8, com os mesmos valores. Em 9 das 27 séries de rotina, as de PR herdado, o alvo foi preenchido com o PR. É o comportamento normal do app com séries `inheritPr`, igual no debug, e não vem do R8.

Assim, o kotlinx.serialization funciona sob o R8 com Kotlin 2.4 e KSP2 na ida e na volta. A abertura a frio e as telas do R8 não foram medidas.

## O que **não** foi verificado

- **Android 16:** o emulador só tem Android 15 (API 35). O gesto de voltar preditivo e o edge-to-edge no 16 não foram vistos.
- **Celular físico:** nada rodou em aparelho real. Os APKs deste Mac usam a chave debug local, que não atualiza o app instalado com a chave das releases.
- **`PRAGMA synchronous` em aparelho cujo framework usa `FULL`** e o `busy_timeout` efetivo (só no bytecode).
- **A notificação do timer** antes e depois da atualização: o filtro do `dumpsys notification` não pegou as linhas. A tela e o alarme foram conferidos, a notificação não.
- **A suíte inteira localmente:** fica com o CI, como combinado. Este commit só tem docs e assets.
- **O import com mais de uma execução de cada lado**, a abertura a frio do R8 e o treino iniciado e finalizado com a base grande (que a verificação anterior mediu) não foram repetidos.
- **TalkBack e teclado.**
- **`connectedDebugAndroidTest` não rodou:** o `androidTest` só foi compilado (`assembleDebugAndroidTest`).
- **Um teste instrumentado** que abre um banco v5 e um DataStore criados pela versão anterior (a spec pede). Adiado para a etapa 1B, quando o DataStore passa para o código comum. A atualização por cima no emulador cobre o mesmo caminho na prática, mas sem teste automatizado.

## Incidentes

- Numa rodada da troca de unidade, a tela de Settings estava com outra rolagem, e quatro toques caíram em textos sem ação (a nota do updater), não nos botões de unidade. O `uiautomator` confirmou que nada mudou. As execuções foram apagadas e refeitas com as coordenadas certas.
- `pm clear com.mooscles` foi usado só no `emulator-5554`, três vezes (antes do fixture da atualização, antes do import da base no APK novo e no teste do R8). A cada vez, a permissão de notificação foi concedida de novo.
- O emulador terminou com o APK debug desta branch e a base grande importada pelo APK R8.

## Avisos para a 1B

- `MigrationEquivalenceTest` e `RollbackV142DataCompatibilityTest` procuram `schemas/` ou `app/schemas/`; os schemas passam para `shared/schemas/`.
- `.setDriver(AndroidSQLiteDriver())` se repete em ~15 construtores de banco nos testes; um helper facilita a troca.
- `@Volatile` e `@Synchronized` em `WorkoutDatabase.kt` resolvem para `kotlin.jvm.*`. No `commonMain`, use `kotlin.concurrent.Volatile` ou tire a sincronização (as chamadas aninhadas rodam em sequência numa só corrotina).
- A semântica de exceção de DAO difere entre os drivers (ver a regra de transação de escrita no `AGENTS.md`).
