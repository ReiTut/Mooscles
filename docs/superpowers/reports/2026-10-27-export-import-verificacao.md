# Export e import: verificação no emulador

- **Data:** 2026-10-27
- **Branch:** `feat/export-import`. A primeira rodada verificou o código em `776bb90`. A [segunda rodada](#segunda-rodada-depois-de-217a9a9) verificou `217a9a9`, que corrige os itens 1 a 7 da UI critique.
- **Spec:** [2026-10-27-export-import-design.md](../specs/2026-10-27-export-import-design.md)
- **Plano:** [2026-10-27-export-import.md](../plans/2026-10-27-export-import.md)
- **Prints:** [2026-10-27-export-import-verificacao/](2026-10-27-export-import-verificacao/)

## Resumo

O fluxo funciona de ponta a ponta no emulador, pelo seletor de arquivos do sistema. Export, `pm clear`, import, conferência dos dados, reimport idempotente, arquivos inválidos e o bloqueio durante um treino ativo se comportaram como a spec descreve. O build R8 (`assemblePerformance`) fez a ida e volta completa, e o `kotlinx.serialization` funciona minificado.

**O problema encontrado:** com a base grande (208 treinos, 12.688 séries), o import termina de gravar em poucos segundos, mas em seguida a thread principal fica travada por **~142 s no build debug** e **~25 s no build R8**. A causa não está no código de backup. É o recálculo de PR do histórico em `WorkoutPresentation.present()`, na thread principal, que já trava a abertura do app e a troca de unidade com a mesma base (a fase 0 do ROADMAP já prevê tirar esse cálculo da thread principal). O import dispara esse recálculo uma ou duas vezes. Na prática, isso viola o anti-objetivo da spec "o export ou o import travar a UI" para a base grande. Com qualquer toque durante o travamento, o Android mostra o diálogo **"Mooscles isn't responding" (ANR)**. Isso foi reproduzido na segunda rodada, depois do import, e também aconteceu numa abertura a frio. **Decisão:** o travamento não é corrigido nesta branch. A correção é o item "Cálculo de PR fora da thread principal", que passa a ser o próximo trabalho da fase 0 e bloqueia a release da v1.5 (ver [Decisão](#decisão) e a nota de 2026-10-27 na spec). Até lá, quem tiver um histórico grande e usar o APK debug distribuído vai ver esse diálogo depois de importar, e possivelmente ao abrir o app.

> **Atualização de 2026-10-27:** o travamento e o ANR depois do import foram resolvidos no branch `fix/pr-off-main-thread` ([verificação](2026-10-27-pr-off-main-thread-verificacao.md)). No mesmo emulador, com a mesma base e o APK debug, o recálculo agora roda fora da thread principal: o `present()` leva ~1,1 s depois do import, o histórico é apresentado ~6–7 s depois do toque em Import, o maior quadro da thread principal fica abaixo de 300 ms e nenhum ANR apareceu com 93 a 127 toques durante o import. A troca lb ↔ kg também deixou de travar. Os números abaixo continuam como registro do "antes". Uma meta daquele branch não foi cumprida: na abertura a frio do debug, o histórico ainda leva 15 a 20 s para aparecer, mas com a tela respondendo (detalhes no relatório).

## Ambiente

- Emulador `Mooscles_Pixel8_API35` (`emulator-5554`), Android 15, 1080×2400, imagem `sdk_gphone64_arm64-userdebug`, 4 núcleos e **2,5 GB de RAM** com swap em uso. Host: Mac com Apple M4. Durante as medições a carga média do emulador ficou entre 4 e 6, e um `screencap` levava de 2 a 5 s, então o emulador estava lento.
- APKs: `app-debug.apk` (debug, assinado com a chave debug local do Mac, que **não** é a chave das releases) e `app-performance.apk` (R8, não debuggable, mesma chave local).
- Base grande: arquivo de backup v1 gerado no host por um script descartável fora do repositório (`.../scratchpad/gen/make_backups.py`), no mesmo formato da fixture `app/src/test/resources/backup/v1-sample.json`. São 208 treinos finalizados × 6 exercícios de catálogo (`TOTAL_LOAD`) × 61 séries = 12.688 séries, as mesmas contas do teste `largeArchiveExportAndImportStayFast`. O arquivo também tem 2 exercícios personalizados, 2 pastas (`default` e uma nova), 3 rotinas com 9 itens e 27 séries (9 com `inheritPr = true`), além de preferências diferentes do padrão (`pounds = true`, `theme = "dark"`, `autoRest = false`, `defaultRestSeconds = 120`). Tamanho: 4,18 MB, com indentação de 2 espaços.
- Também foram gerados um JSON qualquer (`random-notes.json`) e uma cópia com `"formatVersion": 2` (`mooscles-future-v2.json`). Todos os arquivos foram enviados para `/sdcard/Download` por `adb push`.
- Toda a automação foi feita com `adb -s emulator-5554` (`input tap`, `uiautomator dump`, `screencap`, `dumpsys`, `logcat`) e com scripts Python no scratchpad. Para amostrar pilhas e ler o banco, o `adbd` do emulador rodou como root (`adb root`, desfeito no fim com `adb unroot`).

## Tempos

### Host (teste `BackupRepositoryTest.largeArchiveExportAndImportStayFast`, Robolectric)

Medidos só em codec e repositório, sem UI e sem seletor:

| | Faixa observada nas execuções da branch | Última execução (`776bb90`) |
| --- | --- | --- |
| Export (montar + `BackupCodec.encode`) | 126–1020 ms | 185 ms |
| Import (`decode` + `importBackup`) | 957–4286 ms | 589 ms |

### Emulador

Os tempos vão do toque até o resultado na tela e incluem a animação do seletor (DocumentsUI). Na mesma linha, o tempo de travamento da thread principal vem do `logcat`.

| Cenário | Build | Tempo total | Travamento da thread principal (maior quadro) | Como foi medido |
| --- | --- | --- | --- | --- |
| Escolher o arquivo gerado (4,18 MB) → diálogo de resumo, base vazia | debug | 5,4 s do retorno do seletor ao diálogo | < 1 s | `logcat`: transição CLOSE do PickActivity às 12:32:33,29 → janela do diálogo às 12:32:38,15 |
| Confirmar o import → mensagem "Imported…", base vazia (execução 1) | debug | **122,4 s** | **53,1 s + 65,4 s** | polling de `dumpsys window` até o diálogo fechar; `Davey!` e `Choreographer: Skipped` no `logcat` |
| Export da base importada → arquivo completo | debug | 6,2 s do toque em SAVE | 0,75 s | polling de `stat` no arquivo; o seletor ainda aparecia aos 2,7 s |
| `pm clear` → escolher o export (5,35 MB) → diálogo | debug | 8,9 s do toque | 1,4 s | polling de `dumpsys window` |
| Confirmar o import → mensagem, base vazia (execução 2) | debug | **147,0 s** | **77,5 s + 65,3 s** | idem, com amostragem de pilha |
| Reimportar o mesmo arquivo → diálogo "already on this phone" | debug | 13,6 s do toque | 2,8 s | polling de `dumpsys window` |
| Confirmar o reimport (nada a acrescentar) | debug | 2,2 s | < 1 s | polling de `dumpsys window` |
| Export da base grande | R8 | 8,5 s do toque em SAVE: 4,3 s dentro do DocumentsUI e 4,3 s do app | 1,5 s | `logcat` (CLOSE às 13:04:41,47) + `stat` (mtime às 13:04:45,76) |
| `pm clear` → escolher o export → diálogo | R8 | 9,9 s do toque | 1,1 s | polling de `dumpsys window` |
| Confirmar o import → mensagem, base vazia | R8 | **30,8 s** | **24,9 s** | polling de `dumpsys window` + `logcat` |

Comparação sem import, com a mesma base:

| Cenário | Build | Travamento |
| --- | --- | --- |
| Abertura a frio | debug | 52,1 s (`Davey! duration=52066ms`) |
| Abertura a frio | R8 | 9,0 s |
| Trocar lb → kg em Configurações | debug | 61,7 s |

**Precisão:** o polling de `dumpsys window windows` leva de 0,1 a 0,3 s por ciclo, então a abertura e o fechamento do diálogo têm precisão de ±0,3 s. O polling de `stat` leva ~0,1 s. As durações de travamento vêm do próprio Android (`Davey! duration`, em ms). O `uiautomator dump` não serve para cronometrar, porque espera a UI ficar ociosa e leva de 2 a 5 s, ou falha durante o travamento. Cada número é de uma única execução, num emulador lento: servem como ordem de grandeza, não como benchmark.

### Por que o import trava

Durante o import da execução 2, a pilha do processo foi amostrada a cada ~3 s com `debuggerd -j`, num total de 46 amostras (`.../scratchpad/pulled/samples/`):

- 1 s depois do toque, a thread principal estava no `UpdateStore` (`org.json`, cache de release do atualizador). Isso não tem relação com o import.
- 4 s depois, a thread de transação do Room estava em `insertSets` (gravação do import).
- A partir de ~8 s e até o fim (amostras 3 a 45), a thread principal estava **sempre** em `WorkoutPresentation.present` → `present$workout` → `WorkoutCalculations.prsForWorkout` / `repsPrsForWorkout` → `SnapshotIndex.<init>`, `bestRepetitionSets`, `comparable` e `sameLoad`.

Portanto a gravação termina em menos de ~8 s. O tempo restante vai para o `historyUi = finished.map { workout(it, historical) }`, que para **cada** treino reconstrói um `SnapshotIndex` e compara com todos os treinos anteriores. O custo é quadrático no número de treinos, e o cálculo roda na thread principal, como o AGENTS.md descreve. No build debug o import provoca dois recálculos: um pela mudança dos dados e outro porque a restauração das preferências muda `pounds` (`historyPounds`). No R8 o arquivo tinha `pounds = false`, igual ao padrão, e houve um recálculo só.

**ANR:** na primeira rodada o `logcat` não teve `ANR in com.mooscles`, só porque nenhum toque foi enviado durante os travamentos. Na segunda rodada houve toques, e o ANR **apareceu duas vezes**. Os detalhes estão em [ANR observado](#anr-observado).

**Indicador de `busy`:** durante a leitura e a validação (antes do diálogo), a barra de progresso aparece no card e os dois botões ficam desabilitados ([print 02](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-export-import-verificacao/02-busy-progress.png?raw=true)). Durante a gravação, porém, o diálogo "Import backup?" **continua aberto**, com Cancel e Import habilitados, e a barra fica atrás do scrim. Durante o travamento nada anima. Isso é coerente com o código: `confirmImport` só limpa `pendingImport` no `finally`. **Corrigido no `217a9a9`:** o diálogo fecha 0,5 s depois de confirmar, e o card mostra "Importing…" com a barra ([print 02](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-export-import-verificacao/02-busy-progress.png?raw=true)).

## O que foi verificado no emulador

1. **Build e instalação:** `./gradlew :app:assembleDebug` e `adb -s emulator-5554 install -r` (versionCode 16, 1.4.5).
2. **Import pelo seletor numa base vazia** (`pm clear` antes). O resumo diz "Add 208 workouts, 3 routines and 2 custom exercises. Your units, theme and rest settings will also be restored." O resultado foi "Imported 208 workouts, 3 routines and 2 custom exercises" (na segunda rodada, com ícone de check: [print 05](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-export-import-verificacao/05-imported-done.png?raw=true)). O print deste diálogo é da primeira rodada e saiu da pasta, porque o fundo mostrava o card antigo. O texto do diálogo não mudou no `217a9a9`.
3. **Export pelo seletor** (`ACTION_CREATE_DOCUMENT`): nome sugerido `mooscles-backup-2026-10-27.json`, pasta Downloads. Mensagem "Exported 208 workouts, 3 routines and 2 custom exercises". O arquivo foi baixado e comparado com o de origem: as 208 linhas de treino, as 1.248 de exercício de treino, as 12.688 de série, as 2 pastas e as 3 rotinas são idênticas. As 27 séries e os 9 itens de rotina têm a mesma identidade, e só os 9 alvos com `inheritPr = true` e as colunas espelho mudaram, como a spec prevê para alvos herdados de PR. A sincronização gerou 6 checkpoints, e o export levou o catálogo inteiro (295 exercícios), como a spec pede.
4. **Fluxo completo da spec:** export → `pm clear com.mooscles` (**só no emulador**) → import do arquivo exportado. Depois disso:
   - **Banco:** contagens conferidas por `sqlite3` no emulador (`workouts` 208, `workout_exercises` 1248, `workout_sets` 12688, `routines` 3, `routine_exercises` 9, `routine_sets` 27, `routine_folders` 2, `routine_target_checkpoints` 6, exercícios personalizados 2, `user_version` 5).
   - **Histórico:** History mostra 208 treinos, 12688 séries concluídas e 240 PRs de exercício, do "Session 207" (SAT, JUN 8) para trás.
   - **Rotinas:** "Verif routine 1" em My routines, com 3 exercícios e 9 séries planejadas.
   - **Recordes:** Records lista Weight PR e Reps PR de "Barbell bench press", exibidos em **lb**.
   - **Preferências restauradas na instalação nova:** tema escuro, libras e descanso automático desligado. O descanso padrão de 2:00 do arquivo é igual ao valor padrão do app, então esse campo não prova a restauração.
5. **Reimport do mesmo arquivo:** o resumo diz "Add 0 workouts, 0 routines and 0 custom exercises. 208 workouts are already on this phone and will be kept as they are." Depois de confirmar, a mensagem era "Imported 0 workouts, 0 routines and 0 custom exercises", e as contagens de todas as tabelas ficaram iguais. **Mudou no `217a9a9`:** agora o diálogo diz "Everything in this backup is already on this phone." e só oferece OK ([print 04](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-export-import-verificacao/04-nothing-new.png?raw=true)). Depois do OK não aparece mensagem nenhuma.
6. **Arquivos inválidos**, com o hash MD5 do `sqlite3 .dump` e do `local_preferences.preferences_pb` conferido antes e depois:
   - `random-notes.json`: "This file is not a Mooscles backup." (só na primeira rodada; o print saiu da pasta);
   - `"formatVersion": 2`: "This backup was made by a newer version of Mooscles. Update the app and try again." (refeito na segunda rodada: texto e ícone na cor de erro, [print 07](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-export-import-verificacao/07-error-newer-version.png?raw=true));
   - nenhum diálogo de resumo abriu, e os dois hashes ficaram idênticos (`25d968ee…` e `4de1f925…`).
7. **Cancelamentos:** voltar do seletor de import e do de export não faz nada. Não aparece mensagem, e o hash do banco não muda. Cancel no diálogo de resumo também não grava nada (208 treinos antes e depois).
8. **Treino ativo:** com um treino vazio iniciado, **Import data** fica desabilitado (`enabled="false"` no `uiautomator`) e aparece o texto "Finish or discard the active workout to import". **Export data** continua habilitado ([print 08](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-export-import-verificacao/08-active-workout-block.png?raw=true)). Depois, o treino foi descartado (208 finalizados, nenhum ativo).
9. **R8 (`./gradlew :app:assemblePerformance`):** instalado com `install -r` sobre o debug, sem erro de assinatura, porque usa a mesma chave local. Ida e volta completa: export (5.352.603 bytes) → `pm clear` → import → mensagem "Imported 208 workouts, 3 routines and 2 custom exercises" e as mesmas contagens no banco. O export R8 foi comparado com o export debug: as 9 listas são idênticas linha por linha, os nomes de campo estão preservados (`id`, `name`, `startedAt`…), não há crash e não aparece `SerializationException` no `logcat`. **O `kotlinx.serialization` funciona sob R8 sem regras de keep adicionais.**
10. **Temas:** o card foi visto no claro (prints 02 e 03, durante o import numa instalação nova) e no escuro (prints 01 e 04 a 08).

No fim, o emulador ficou de novo com o build debug (`install -r`) e o `adbd` sem root.

## O que **não** foi verificado

- **Celular físico.** Nada foi testado em aparelho real. Os APKs deste Mac usam a chave debug local, que não atualiza o app instalado com a chave das releases.
- **Google Drive e outros provedores SAF.** Só foi usado o Downloads local pelo DocumentsUI do emulador. Não foram testados o Drive real, provedores de terceiros nem provedores que não informam o tipo MIME.
- **Sobrescrever um arquivo existente** (modo `"wt"`, truncar). Cada export criou um arquivo novo. Não foi verificado se um provedor que ignora o truncamento deixa lixo no fim de um arquivo maior.
- **Mensagens de falha de arquivo** ("Couldn't save the backup…" / "Couldn't open the file…"), que não foram provocadas no emulador.
- **JSON corrompido, referência faltando, ID duplicado, dois treinos ativos e treino ativo vindo do arquivo:** cobertos só por testes de host.
- **Preferências mantidas quando o aparelho já tem dados.** No reimport, o arquivo e o aparelho tinham as mesmas preferências, então o caso não dá para distinguir no emulador. Isso só está coberto em host.
- **TalkBack:** o anúncio da mensagem (`liveRegion`) e a descrição "Working…" da barra não foram ouvidos.
- Fonte grande, paisagem e tablets.
- **Fluxo Maestro.** Não foi criado `.maestro/export-import.yaml`. O seletor foi controlado por `adb` e `uiautomator` e funcionou, mas não com estabilidade suficiente para virar um fluxo versionado: a lista "Recent" do DocumentsUI mostra entradas antigas de arquivos apagados com o mesmo nome, a grade e a lista trocam de posição, o tutorial de caneta do Gboard apareceu e engoliu o texto digitado no nome do arquivo, e o seletor demora de 3 a 4 s para fechar depois de SAVE.
- **Revisão de compatibilidade (`compat-reviewer`):** fica com o controlador, como foi combinado.
- **Depois do `217a9a9`:** o build R8 e o export não foram refeitos. A mensagem de sucesso só foi vista depois de um import, não depois de um export. O diálogo de resumo com dados novos não foi fotografado de novo, porque o texto não mudou.
- **Medida de contraste no aparelho:** não foi feita. O contraste do texto de treino ativo vem do teste de host do `217a9a9`.

## UI critique

Feita com a skill `critique-screen` sobre os prints da primeira rodada (card "Your data" e diálogo de resumo). Os itens 1 a 7 foram corrigidos no `217a9a9` e conferidos no emulador na segunda rodada. Os itens 8 a 10 ficaram para depois.

### P1 — Crítico

1. **Affordance:** o diálogo de confirmação continua aberto durante a gravação, com Import e Cancel habilitados, e a barra de progresso fica escondida atrás do scrim. Com a base grande isso dura de 30 a 147 s, e Cancel não interrompe nada (o comando entra na fila e não tem efeito). **Correção:** fechar o diálogo ao confirmar (limpar `pendingImport` antes do trabalho) e mostrar o progresso no card, ou manter o diálogo com um progresso próprio e os botões desabilitados. **Corrigido (`217a9a9`):** o diálogo some 0,47 s depois do toque em Import (polling de `dumpsys window`), e o card mostra "Importing…" com a barra (print 02).
2. **Cor:** mensagens de erro e de sucesso têm o mesmo estilo (`bodyMedium`, `onSurface`, sem ícone), como mostram os prints 05 e 07. Quem só passa os olhos não sabe se deu certo. **Correção:** erro com `colorScheme.error` ou `errorContainer` e ícone; sucesso com ícone de check. Assim a informação não depende só do texto. **Corrigido:** o erro aparece na cor de erro, com ícone de alerta (print 07), e o sucesso com um check na cor primária (prints 05 e 08).
3. **Densidade e affordance:** quando não há nada a acrescentar, o diálogo diz "Add 0 workouts, 0 routines and 0 custom exercises…" e ainda oferece **Import**, que não faz nada. Em seguida aparece "Imported 0 workouts…". **Correção:** com tudo já no aparelho, dizer "Everything in this backup is already on this phone." com um único botão OK, e não usar "Imported 0…" como resultado. **Corrigido:** o diálogo agora diz "Everything in this backup is already on this phone." e tem só OK (print 04). Depois do OK não aparece mensagem.

### P2 — Importante

4. **Affordance:** a barra indeterminada não diz qual operação está rodando, e os dois botões ficam desabilitados sem explicação. **Correção:** um rótulo ao lado da barra ("Exporting…", "Checking backup…", "Importing…"). **Corrigido:** vi "Checking backup…" (print 03) e "Importing…" (print 02). "Exporting…" não foi visto no emulador.
5. **Hierarquia:** a primeira coisa que o card diz é o aviso de criptografia. Não há uma linha dizendo para que o card serve. **Correção:** abrir com o propósito ("Save a copy of your workouts, routines and settings to a file, or restore one.") e deixar o aviso de privacidade como texto secundário depois dos botões. **Corrigido:** o card abre com essa frase, e o aviso fica no fim (print 01).
6. **Composição:** a linha de mensagem divide a largura com um TextButton "Dismiss" de ~25%, e a mensagem quebra em 2 ou 3 linhas (prints 05 e 07). **Correção:** mensagem na largura toda, com o Dismiss abaixo, alinhado ao fim, ou um ícone de fechar de 48 dp. **Corrigido:** a mensagem ocupa a largura toda, e o Dismiss fica abaixo, à direita (prints 05 e 07).
7. **Cor:** no tema escuro, o botão **Import data** desabilitado e o texto "Finish or discard…" ficam com contraste muito baixo (print 08). Botões desabilitados são exceção no WCAG, mas a explicação não é, e tem que passar de 4,5:1. **Correção:** manter a explicação em `onSurfaceVariant` com contraste medido, ou levá-la para perto do botão com um ícone de informação. **Resolvido:** a cor não mudou, mas o teste de host do `217a9a9` (`activeWorkoutExplanationMeetsContrastInDarkTheme`) mediu 7,3:1 para o texto no tema escuro. Minha leitura visual estava errada quanto ao texto. O que tem pouco contraste é o botão desabilitado, que é exceção no WCAG (print 08).

### P3 — Polimento (fica para depois)

8. **Tipografia:** os números do resumo ficam diluídos numa frase longa. **Correção:** dar destaque às contagens ou usar uma lista curta, uma linha por tipo.
9. **Densidade:** o card de privacidade logo abaixo ("Workout data is excluded from automatic cloud backups") não menciona o export manual. **Correção:** acrescentar "unless you export it" ou um link para "Your data".
10. **Fora do escopo, já existia:** o campo "2:00" do descanso padrão fica com o destaque de seleção sempre visível em todos os prints. Isso não é do export/import.

**Avaliação geral:** a dimensão mais forte é a composição. O card segue o padrão dos cards de Configurações, a ação segura (Export) é o botão cheio e o import é o contorno, e o bloqueio do treino ativo fica explicado no lugar certo. A mais fraca é a visibilidade de estado (affordance e cor): o diálogo fica aberto durante a gravação, erro e sucesso têm a mesma aparência e o caso "nada a acrescentar" pede uma confirmação sem efeito.

## Decisão

**Resolvido em 2026-10-27** pelo branch `fix/pr-off-main-thread`: ver a [verificação](2026-10-27-pr-off-main-thread-verificacao.md). O texto abaixo é a decisão original.

Decisão do controlador em 2026-10-27, registrada na nota de desempenho da [spec](../specs/2026-10-27-export-import-design.md#notas-de-implementação):

- **O travamento depois do import não é corrigido nesta branch.** A correção é o item "Cálculo de PR fora da thread principal" da fase 0 do [ROADMAP](../ROADMAP.md), que passa a ser o próximo trabalho e **bloqueia a release da v1.5**. A restrição da spec "a UI não congela" continua valendo e só fica cumprida com esse item.
- **Consequência até lá:** quem tiver um histórico grande e usar o APK debug distribuído vai ver o diálogo "Mooscles isn't responding" depois de importar, se tocar na tela durante o travamento, e possivelmente ao abrir o app. Escolher "Close app" mata o processo. Os dados importados já estão gravados nesse momento, mas isso não foi conferido depois de um "Close app" no meio do travamento pós-import (ver a segunda rodada).
- Os itens P1 e P2 da UI critique foram corrigidos no `217a9a9`.

## Segunda rodada (depois de `217a9a9`)

Feita em 2026-10-27 entre 13:25 e 13:40, no mesmo emulador, com `./gradlew :app:assembleDebug` e `adb -s emulator-5554 install -r`. A base no aparelho era a importada na primeira rodada. Os prints da pasta foram substituídos pelos desta rodada.

| Verificação | Resultado |
| --- | --- |
| Card parado (tema escuro) | Abre com "Save a copy of your workouts, routines and settings to a file, or restore one.", seguido dos botões, com o aviso de criptografia no fim ([print 01](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-export-import-verificacao/01-card-idle.png?raw=true)) |
| Arquivo com `formatVersion: 2` | Ícone de alerta e texto na cor de erro; Dismiss abaixo, à direita ([print 07](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-export-import-verificacao/07-error-newer-version.png?raw=true)) |
| Reimport do arquivo grande (mesmos IDs) | Durante a leitura aparece "Checking backup…" com a barra e os dois botões desabilitados ([print 03](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-export-import-verificacao/03-busy-checking.png?raw=true)). Depois, "Everything in this backup is already on this phone." só com OK ([print 04](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-export-import-verificacao/04-nothing-new.png?raw=true)). Depois do OK não aparece nenhuma mensagem: o `uiautomator` não encontrou "Imported" nem "Dismiss" |
| Import grande numa base vazia (`pm clear` só no emulador) | O diálogo de resumo levou 8,8 s para abrir depois do toque no arquivo. Ao confirmar, o diálogo fechou em 0,47 s e o card mostrou "Importing…" com a barra, no tema claro da instalação nova ([print 02](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-export-import-verificacao/02-busy-progress.png?raw=true)). A mensagem de sucesso e o tema escuro restaurado já aparecem por trás do diálogo de ANR (print 06), e depois a mensagem aparece com o ícone de check ([print 05](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-export-import-verificacao/05-imported-done.png?raw=true)) |
| Treino ativo no tema escuro | Import data desabilitado (`enabled="false"`), explicação abaixo dos botões e Export habilitado ([print 08](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-export-import-verificacao/08-active-workout-block.png?raw=true)). Depois o treino foi descartado |

### ANR observado

**1. Depois do import (provocado de propósito).** O import foi confirmado às 13:33:59. A gravação e a mensagem terminaram em poucos segundos, e a thread principal ficou travada de ~13:34:03,6 a 13:34:51,3 (`Davey! duration=47675ms`, `Skipped 2845 frames`). Desta vez houve só um recálculo, não dois como na primeira rodada. Às 13:34:14 um toque foi injetado sobre o título do card, como faria um usuário. O `logcat` registrou:

```
13:34:19.794 I/WindowManager: ANR in Window{3cd0f3a u0 com.mooscles/com.mooscles.MainActivity}. Reason:Input dispatching timed out
13:34:21.279 E/ActivityManager: ANR in com.mooscles (com.mooscles/.MainActivity)
13:34:21.279 E/ActivityManager: Reason: Input dispatching timed out (... is not responding. Waited 5011ms for MotionEvent(deviceId=-1, ...
```

O `dumpsys activity lastanr` confirma: "ANR time: Oct 27, 2026 1:34:19 PM". O diálogo "Mooscles isn't responding", com "Close app" e "Wait", foi capturado às 13:34:44 ([print 06](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-27-export-import-verificacao/06-anr-after-import.png?raw=true)). Ele fechou sozinho às 13:34:51,3, quando a thread principal voltou. O toque em "Wait" que eu enviei às 13:34:59 chegou depois disso: o `uiautomator` não encontrou "Wait", e o toque de reserva por coordenada caiu em **Export data** e abriu o seletor de export. Voltei sem salvar, e nada foi gravado. O processo seguiu vivo (mesmo PID 24297), e o card mostrava "Imported 208 workouts, 3 routines and 2 custom exercises". As contagens no banco não foram conferidas por `sqlite3` nesta rodada, porque o `adbd` estava sem root.

**2. Numa abertura a frio (sem querer).** Às 13:25:4x o app foi aberto com a base grande. Meu script de navegação continuou depois de um `uiautomator dump` falhar e mandou swipes durante o travamento da abertura. O resultado foi um ANR às 13:26:01,971 (`ANR in com.mooscles ... Input dispatching timed out`) e o diálogo às 13:26:03,7. Às 13:26:23,7 o diálogo foi fechado com **"Close app"** (`Killing 23041:com.mooscles/u0a207 (adj 0): user request after error`). Esse toque não saiu dos meus scripts; provavelmente foi de quem acompanhava o emulador. Depois disso, o meu próximo toque, procurando "Settings" no Mooscles, caiu no ícone do app Configurações do Android, que foi fechado com HOME. O script foi corrigido para parar quando o dump falha. Ao reabrir sem tocar na tela, a abertura travou 48,7 s (`Skipped 2920 frames`) e não houve ANR.

## Suíte final

`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`. Foram 449 testes, 0 falhas, 0 erros e 0 ignorados, e o lint deu 0 erros e 21 avisos. O `testDebugUnitTest` e o `assembleDebug` vieram `UP-TO-DATE` da execução anterior no mesmo `776bb90`, porque o código não mudou e este commit só traz docs. Para a segunda rodada (`217a9a9`) só rodei `:app:assembleDebug`. A suíte completa não foi rodada de novo por mim, porque o commit desta rodada também só traz docs. O resultado do `DataSectionHostTest` citado acima vem da execução do controlador.
