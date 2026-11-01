# Fase 2: verificação do design system "Giz & Ferro"

- **Data:** 2026-10-28
- **Branch:** `feat/phase2-design-system`, a partir da `main` do fork em `4f698a8` (depois da 1B). Último commit de produto: `562d8d0`; `04093bf` acrescenta só o relatório da crítica
- **Spec:** [fase 2](../specs/2026-10-27-phase2-design-system-design.md) · **Plano:** [fase 2](../plans/2026-10-27-phase2-design-system.md) · **Spike:** [`tnum` e eixo de peso](2026-10-28-phase2-tnum-spike.md) · **Crítica:** [Configurações e galeria](2026-10-28-phase2-critica-configuracoes.md)
- **Evidências:** [2026-10-28-phase2-verificacao/](2026-10-28-phase2-verificacao/): [atualização por cima](2026-10-28-phase2-verificacao/upgrade.md), [notas de TalkBack](2026-10-28-phase2-verificacao/talkback.md), [reduzir movimento](2026-10-28-phase2-verificacao/animator-0.txt), [o que o emulador mostrou](2026-10-28-phase2-verificacao/findings.md), [diff de dependências](2026-10-28-phase2-verificacao/deps-diff.txt) e as capturas

## Resumo

- **Entregue:** o módulo KMP `:designsystem` (Android, `iosArm64`, `iosSimulatorArm64`) com tokens, 28 arquivos de componentes, fontes (Inter variável e Barlow Condensed), 38 ícones (28 do Lucide 1.63.0 e 10 próprios), strings em inglês e pt-BR, a galeria escondida atrás de 7 toques e a tela de licenças. A tela de Configurações foi refeita só com o sistema, com as mesmas ações, chaves e valores.
- **Testes:** `:app` 377 de host, `:designsystem` 66 de host e 189 de dispositivo no emulador, todos verdes. O contraste dos 50 pares da spec é provado por teste.
- **Dados intactos:** a atualização por cima da `main` (`4f698a8`) manteve o arquivo de preferências idêntico byte a byte, o treino ativo e o prazo do timer.
- **APK debug: +1,26 MiB** sobre a base, dentro do 1,5 MiB do plano. Fontes 630 KiB (limite 900 KiB). Fontes e ícones 816 KiB, abaixo do 1 MiB da spec.
- **iOS:** só compila e roda testes no CI. Este relatório fecha antes da última execução do job `ios` (ver [CI](#ci)).
- **Não verificado:** uma sessão real de TalkBack, qualquer coisa num iPhone ou num Android físico e as mensagens do updater e do backup em pt-BR, que continuam em inglês por decisão. A lista completa está [no fim](#o-que-não-foi-verificado).

## Ambiente

- Emulador `Mooscles_Pixel8_API35` (`emulator-5554`), Android 15, 1080×2400, num Mac M4 sem Xcode. O iOS só se prova no job `ios` do CI (`macos-26`, Xcode 26.4).
- Os APKs locais são assinados com a chave debug do Mac, que não é a das releases. A atualização por cima prova a compatibilidade dos dados entre dois builds, não o caminho da release publicada para este build.

## O que foi provado

### Testes

| Conjunto | Onde | Resultado |
| --- | --- | --- |
| `:app:testDebugUnitTest` (JUnit 4 + Robolectric) | Mac, árvore de `562d8d0` | 377 testes, 0 falhas |
| `:designsystem:testAndroidHostTest` (`commonTest` na JVM e paridade de strings) | Mac, `562d8d0` | 66 testes, 0 falhas |
| `:designsystem:connectedAndroidDeviceTest` (`uiTest` e `commonTest`) | emulador, `562d8d0` | 189 de 189 (o [XML guardado](2026-10-28-phase2-verificacao/task16-connectedAndroidDeviceTest.xml) é o da rodada de evidências da Task 16, com 158 de 158) |
| Host no CI (`:app`, `:shared` e `:designsystem`) | run [40756095229](https://github.com/pedroyuuki/Mooscles/actions/runs/40756095229), `e119c35` | 611 testes em 107 classes, 0 falhas (mínimo 487). Foi o último `host` verde antes das rodadas da crítica |
| `:shared` no simulador iOS | CI | 162 testes no último `ios` verde ([40703723716](https://github.com/pedroyuuki/Mooscles/actions/runs/40703723716), `fe5d211`) |
| `:designsystem` no simulador iOS | CI | 30 testes em `fe5d211`; a contagem atual (186, `MIN_DS_IOS_TESTS`) espera a próxima execução |

Os testes que cobrem as exigências da spec:

- **Contraste:** `ContrastTokensTest` (`commonTest`) calcula as 50 razões da tabela "Pares medidos" a partir dos tokens, nos dois temas, e falha abaixo de 4,5:1 para texto e 3:1 para não-texto. `ComponentColorsTest` mede as combinações que a crítica mudou (toast de erro, ação do `ErrorBanner`, skeleton e timer pausado). Os 66 hex das primitivas e dos tokens foram recalculados contra a spec antes da Task 2.
- **Strings:** `StringsParityTest` confere que toda chave do inglês existe em pt-BR com os mesmos argumentos, e `SettingsTextsHostTest.everySettingsKeyIsUsed` que nenhuma chave `settings_*` sobra.
- **Fonte a 200%:** `GallerySmokeTest` monta cada página da galeria nos dois temas a 2,0 e confere rótulo e alvo de 48 dp em todo nó clicável. `SetRowTest`, `RestTimerDockTest` e `RestTimerExpandedTest` medem os números em larguras de 320 e 360 dp com escalas até 2,0 (`assertShownWhole`: uma linha, sem corte).
- **`tnum`:** `TnumRegressionTest` compara `0000`…`9999` em cada peso da Inter e da Barlow, no emulador e no simulador. O `check_fonts.py` molda os algarismos com HarfBuzz em todo push.
- **Tela piloto:** os testes de host que já existiam para Configurações (`UpdateSectionHostTest`, `DataSectionHostTest`, `RestTimerHostTest` e os do backup) passaram sem mudar as asserções em inglês, junto com os novos (`SettingsScreenHostTest`, `SettingsTextsHostTest`, `GalleryUnlock*`, `GalleryLocaleRestoreHostTest`, `LicensesScreenHostTest`).

### Tokens e plataforma

- `tools/check-design-tokens.ps1` limpo nos `components/`, `gallery/` e `licenses/` do design system e nos cinco arquivos da tela piloto. O script reprova `Color(0x`, cores nomeadas, número com `.dp` ou `.sp`, `tween(`, `spring(` ou `delay(` com número e `Primitives.` fora de `tokens/`. As duas fixtures do CI provam que ele acha e que não acusa à toa.
- `grep -rnE "\b(java|javax|android)\.|System\.|String\.format" designsystem/src/commonMain`: vazio. Os únicos `expect/actual` são háptico, reduzir movimento e o idioma da galeria.

### Spike de `tnum` (S1 e S2)

Ver o [relatório do spike](2026-10-28-phase2-tnum-spike.md). A Barlow Condensed 500, 600 e 700 tem algarismos de largura igual com `tnum` no emulador e no simulador iOS, e o relógio `0:00`/`9:59` não muda de largura. O eixo `wght` da Inter variável se aplica nas duas plataformas. As fontes entraram sem modificação, do `google/fonts` em `9862abc`, e são OFL sem Reserved Font Name. O plano B não foi necessário.

### Tamanho do APK

Medido agora com `./gradlew :app:clean :app:assembleDebug` e `pwsh tools/check-apk-budget.ps1 -Apk app/build/outputs/apk/debug/app-debug.apk`, sobre a base do CI da `main` em `4f698a8` (21.796.716 bytes):

| Checagem | Bytes | Limite | Folga |
| --- | ---: | ---: | ---: |
| Crescimento do APK debug | 1.323.135 (1,26 MiB) | 1.572.864 (1,5 MiB) | 249.729 |
| Fontes (comprimidas) | 645.546 (630 KiB) | 921.600 (900 KiB) | 276.054 |
| Fontes e ícones (classes dos ícones como teto) | 835.411 (816 KiB) | 1.048.576 (1 MiB) | 213.165 |

- APK de 23.119.851 bytes. Os ícones são 43 classes com 189.865 bytes, um teto do tamanho no dex.
- Um APK incremental, sem o `clean`, mediu 23.298.243 bytes e crescimento de 1.501.527, por causa do espaço morto do empacotamento incremental que a 1A já tinha visto. O número da Task 15 (1.487.483) era desse tipo. O CI empacota do zero e mediu 1.290.139 em `e119c35`.
- O limite de 1,5 MiB do total é do plano (decisão 15). O 1 MiB da spec vale para fontes e ícones juntos (F2, ver a nota na spec).

### Dependências

O diff do `debugRuntimeClasspath` do `:app`, antes e depois da Task 2, está em [`deps-diff.txt`](2026-10-28-phase2-verificacao/deps-diff.txt). Nenhuma versão existente mudou e nenhuma coordenada sumiu. Entraram 31 coordenadas: o Compose Multiplatform 1.12.2 (`runtime`, `foundation`, `ui`, `animation`, `components-resources` e os `-android`, que dependem do Jetpack Compose 1.12.1 que o app já usava) e os invólucros `org.jetbrains.androidx.lifecycle` 2.9.6 e `savedstate` 1.3.6, que resolvem para as versões AndroidX do app. Fora do APK, o CI ganhou `fonttools==4.68.0` e `uharfbuzz==0.57.0`, e o `setup-python` fixado por SHA. Sem Material 3 no `:designsystem`, sem Lottie, Coil, Vico ou Roborazzi.

O `compat-reviewer` deu `SEGURO` na Task 2 (o manifesto ganhou o provider `com.mooscles.resources.AndroidContextProvider`, `exported=false`) e na Task 15 (as mesmas ações e valores; a instalação continua passando pelo `requestInstall`).

### Atualização por cima

Ver [`upgrade.md`](2026-10-28-phase2-verificacao/upgrade.md). O APK da `main` em `4f698a8` com um treino ativo, quatro séries feitas e o timer de descanso correndo foi substituído por `adb install -r` pelo APK da branch em `4d33c32` (a tela piloto já refeita):

- `local_preferences.preferences_pb` com o mesmo MD5 antes e depois, e as chaves `pounds`, `theme`, `auto_rest`, `default_rest`, `selected_routine_folder` e `timer_*` iguais;
- Configurações mostrou os mesmos valores (Escuro, libras, descanso automático ligado, 2:30);
- o treino ativo voltou com as quatro séries, e o timer manteve o prazo: o alarme ficou no mesmo instante e o dock chegou a "Rest complete" no horário salvo.

Nenhum commit da fase mexe no `:shared` nem em `data/`, `timer/`, `updates/` ou `AppPreferences.kt` do `:app`: `git diff 4f698a8..HEAD` nesses caminhos sai vazio. Os commits depois de `4d33c32` mudaram só telas, componentes e a `MainActivity` (a correção abaixo).

### Emulador

- **Galeria:** cada página nos dois temas e a 200% (capturas `gallery-*`), e as capturas "depois" da crítica (`*-after.png`).
- **Configurações:** en e pt-BR, claro e escuro, 200% em pt-BR (capturas `settings-*`) e a tela de licenças.
- **Reduzir movimento** ([`animator-0.txt`](2026-10-28-phase2-verificacao/animator-0.txt)): com o controle da galeria em "reduzir", o check da série, o aviso de recorde e o toast viram fade de cerca de 150 ms, o skeleton para e o anel do timer pula para o valor novo. Com `animator_duration_scale 0`, o controle "Sistema" da galeria passa a ler `reducedMotion: true` ([captura](https://github.com/pedroyuuki/Mooscles/blob/a672fda3dc/docs/superpowers/reports/2026-10-28-phase2-verificacao/animator-0-foundations-motion-after.png?raw=true)).
- **Árvore de acessibilidade** ([`talkback.md`](2026-10-28-phase2-verificacao/talkback.md)): a ordem dos nós de Configurações e a leitura da linha de série em en e pt-BR, tiradas do `uiautomator dump`. Não é uma sessão de TalkBack (ver abaixo).

### Crítica de tela

Ver o [relatório da crítica](2026-10-28-phase2-critica-configuracoes.md). Dos 42 achados (38 do emulador e N1–N4 da crítica), 31 foram corrigidos (o #9 na Task 15, 12 nos componentes e o resto na galeria e em Configurações), 5 ficaram de fora com motivo (#11, #12, N1, N2, N3) e 6 foram para a fase 3 (#1–#5 e #20, da casca do app). O 189/189 no emulador é posterior a todas as correções.

### Correção de um defeito anterior à fase

Girar a tela depois de abrir o app pela notificação do updater derrubava o app com "Navigation graph has not been set": o efeito que navega até a seção de atualizações rodava antes de o `NavHost` definir o grafo. Desde `8f3f87a`, a `MainActivity` só repassa o pedido quando `savedInstanceState == null`, o pedido atendido fica num `rememberSaveable` e a navegação espera o grafo. O `compat-reviewer` deu `SEGURO`.

### Formatação

- Em pt-BR, Configurações usa vírgula decimal e plurais por recurso, com o 0 no plural ("0 exercícios personalizados").
- O tamanho do download do updater passou a ser formatado pelo `NumberLocale` (en ou pt-BR) em vez do `Locale.getDefault()`. Num aparelho em de-DE ele mostra "12.5 MB" em vez de "12,5 MB". Aceito, porque o app só tem os dois idiomas (F38).

## CI

- Host verde até `e119c35` (611 testes). As duas execuções seguintes do `host` caíram no `WorkoutCalculationsBatchTimingTest.activeWorkoutRepsPrsOnLargeHistoryAreFast` do `:shared`, um limite de tempo de relógio que já falhou antes em runners compartilhados e que esta fase não toca (ver as pendências da [v1.5.0](../releases/v1.5.0.md)).
- O job `ios` ficou sem rodar até o fim entre `fe5d211` (Task 3) e o fim da fase: na fila de um runner `macos-26`, ele era cancelado pela regra de concorrência no push seguinte ou pelo próprio GitHub, por falta de capacidade de runners macOS arm64 ("The job was not acquired by Runner of type hosted even after multiple attempts").
- A primeira execução completa (40770015169, em `d6de221`) passou 188 de 189 testes do design system no simulador e achou um bug que o Android escondia: na linha de série, a 200%, o "187.5" ficava cortado no iOS. O iOS escala o texto de forma linear, e o Android, abaixo de 2x, não. Corrigido em `372a12f` (a linha mede a largura de que cada número precisa), com um teste a 2,01x no Android que reproduz o caso.
- **CI ios: verde em 2026-10-28, run [40782682160](https://github.com/pedroyuuki/Mooscles/actions/runs/40782682160) em `372a12f`:** `:shared` 162/162 e `:designsystem` 207/207 no `iosSimulatorArm64Test`, incluindo o `GallerySmokeTest` (a galeria no iOS). Na mesma execução, o `host` passou com 624 testes (mínimo 600) e o `release-script` nas quatro combinações.

## O que **não** foi verificado

- **TalkBack de verdade.** O emulador não deixou conduzir o TalkBack: gestos injetados por `adb` não passam pela exploração por toque, e o atalho de teclado não moveu o foco. A ordem de leitura, os papéis e as ações vêm da árvore de acessibilidade e dos testes (`SettingsScreenHostTest.topBarIsReadBeforeTheContent`, `SetRowTest.exposesFiveCustomActionsThatCallBack`). Falta uma pessoa percorrer Configurações e a página da linha de série com o TalkBack, para confirmar a barra do topo lida primeiro, a linha da versão lida como texto e a leitura do campo de descanso (#11).
- **iPhone:** VoiceOver, háptico, renderização e fontes num aparelho real. O iOS só roda no simulador do CI.
- **Háptico num Android físico.** O mapeamento é testado (`HapticsMappingTest`), a sensação não.
- **Contraste sob luz forte de academia.** As razões são as do WCAG; o uso real não foi visto.
- **Molas a 60 fps num aparelho de referência.** As medidas de movimento são do emulador e da gravação de tela.
- **Revisão visual da galeria no iOS:** não há `iosApp` (fase 7). O iOS da galeria é o `GallerySmokeTest` no simulador, que ainda espera o CI.
- **Mensagens do updater e do backup em pt-BR.** `UpdateUi.message`, `BackupUi.message` e `BackupUi.working` chegam prontas do `MoosclesViewModel` (`:shared`) e continuam em inglês até a fase 6 (decisão 11 do plano). Com o aparelho em pt-BR, Configurações fica em português com essas mensagens em inglês, e as outras telas, as abas e o dock antigo do timer continuam em inglês.
- **O caminho da release:** um APK publicado com a chave original atualizando para este build. A chave não está neste Mac.
- **Notificação de fim do descanso** com o app em primeiro plano, na atualização por cima. Não muda com esta fase.
- **As páginas da galeria no tema escuro e a 200% depois das correções da crítica:** as capturas "depois" são do tema claro. Os testes de 200% e dos dois temas passaram no emulador.
- **Um passo por segundo do anel com reduzir movimento** num timer correndo: as amostras da galeria são estáticas. Só o `RestTimerRingMathTest` cobre isso.
- **O spike não provou** a qualidade visual das fontes, a desambiguação de 1/l/I e 0/O, nem um aparelho físico (ver o [relatório](2026-10-28-phase2-tnum-spike.md#o-que-não-foi-provado)).

## Rodada final de correções

Feita depois da [revisão final da branch](../../../.superpowers/sdd/2026-10-27-phase2-design-system/final-review.md) (3 Important, 9 Minor), em `37e3a9a`..`1f486b9`. Rodado no Mac e no emulador sobre `1f486b9`:

- `:designsystem:connectedAndroidDeviceTest`: 203 de 203. Eram 189; os 14 novos são do Stepper (+4), do toast (+2), do diálogo (+2), do timer (+3) e o `TextFitTest` (3).
- `:designsystem:testAndroidHostTest`: 66 de 66.
- `:app:testDebugUnitTest`: 377 de 377. O `:app:assembleDebug` compilou.
- `tools/check-design-tokens.ps1`: limpo, 51 arquivos.

O `MIN_DS_IOS_TESTS` subiu de 186 para 200. O iOS continua sem prova até o job `ios` do CI.

Os três testes de arrasto do Stepper e os dois de acessibilidade do toast falham com o código anterior às correções. Isso foi conferido no emulador.

### Corrigido

- **I1, Stepper** (`37e3a9a`). O toque nos botões −/+ agora é cancelado, sem passo e sem repetição, em três casos:
  - o dedo passa do `touchSlop`;
  - o dedo sai do botão;
  - uma rolagem consome o movimento, o que é conferido no passe Final.

  Um flick rápido ou um arrasto lento que começa num botão dentro de `verticalScroll` não muda mais o valor. Mexer o dedo durante a repetição para a repetição, e um tremor abaixo do slop ainda conta como toque. Toque curto, repetição (400/80 ms), teclado e a ação de semântica não mudaram: os 7 testes antigos do `StepperTest` continuam passando.
- **I2, toast** (`7b5884b`). O tempo vem de `LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(…)`, com controles só quando há ação. Sem serviço de acessibilidade, continua 4 s ou 6 s. O teste usa um `AccessibilityManager` falso, e a toast continua visível depois dos 6 s.
- **M2, `MDialog`** (`d2c4e86`). O cartão rola quando passa da altura da janela, e as ações ficam num `FlowRow` alinhado ao fim. Os testes, a 200%:
  - com texto longo, o botão de confirmar é alcançado por rolagem e confirma;
  - com os rótulos "Continue training" e "Discard workout", os dois botões ficam inteiros e o de confirmar continua por último.
- **M3, `restore()` no Android** (`c8d4dea`). Agora devolve só o locale sobre a configuração atual. Não há teste novo: os testes do `AppLocaleTest` cobrem a volta do idioma.
- **M5, háptico do timer** (`a83f9b6`). O `RestFinished` só toca na passagem para Done. Isso vale para o dock e para o expandido (`RestFinishedHaptic`): trocar de vista durante a espera de 3 s não vibra de novo.
- **M6, discos de ±15 s** (`a83f9b6`). O mínimo continua 80 dp, e o disco alarga com texto grande. A 200%, "−15 s" e "+15 s" aparecem inteiros (`assertShownWhole`).
- **M9, `assertShownWhole`** (`b633250`). Ganhou testes negativos: ele falha para um texto de uma linha mais estreito do que precisa e para um texto que quebra em duas linhas. Há também um controle positivo.
- **Recomendação 5, `Skeleton`** (`b633250`). O `mutableStateOf(1f)` com movimento reduzido ficou dentro de `remember`.

### Adiado

- **M1, clique simulado pelo TalkBack na linha de série.** Para corrigir, é preciso escolher qual ação o toque duplo faz, ou se ele não faz nada, e conferir numa sessão real de TalkBack, que continua pendente. Fica para a fase 3, junto com a tela de treino.
- **M4, idioma persistente no iOS.** O app iOS ainda não existe (fase 7), e o `actual` de iOS só se prova no CI. Fica registrado para quando houver `iosApp`.
- **M7, regex e escopo do guard de tokens.** Hoje nada escapa: o grep da revisão achou só `Dp(0f)` e o viewport de `MIconBuilder`. Ampliar o escopo para `theme/` e `icons/` exige isenções para os próprios tokens. Isso é trabalho de ferramenta com risco de falso positivo, melhor feito numa mudança própria com as fixtures novas.
- **M8, `MIN_HOST_TESTS`.** Está no CI, que fica com o controlador nesta rodada. Só o `MIN_DS_IOS_TESTS` foi mexido.
- **I3, CI ios.** É do controlador. O placeholder "CI ios" acima continua pendente.
