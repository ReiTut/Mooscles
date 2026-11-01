# Fase 4B: verificação das rotinas

- **Data:** 2026-10-30
- **Branch:** `feat/phase4b-routines`, a partir da `main` do fork. Último commit de código verificado: `a855a9e` (a correção do formulário achada na revisão final; antes, `41a727a`, o merge do equipamento em cobre). Este relatório e os documentos vêm no commit `docs: phase 4B routines verified`
- **Spec:** [fase 4](../specs/2026-10-29-phase4-library-routines-design.md), com as [notas de implementação](../specs/2026-10-29-phase4-library-routines-design.md#notas-de-implementação) · **Plano:** [fase 4B](../plans/2026-10-30-phase4b-routines.md), com as [notas](../plans/2026-10-30-phase4b-routines.md#notas) · **Spikes:** [S2, arrastar e soltar](2026-10-30-phase4b-spike-reordenar.md) e [S4, foto no Android](2026-10-30-phase4b-spike-foto.md) · **Crítica:** [crítica de tela](#crítica-de-tela-task-19) (a preencher)
- **Evidências:** [2026-10-30-phase4b-verificacao/](2026-10-30-phase4b-verificacao/): [suítes de aparelho](2026-10-30-phase4b-verificacao/device-suites.md), [atualização por cima](2026-10-30-phase4b-verificacao/upgrade.md), [sem rede](2026-10-30-phase4b-verificacao/offline.md), [desempenho](2026-10-30-phase4b-verificacao/performance.md), [tamanho do APK](2026-10-30-phase4b-verificacao/apk.md), [diff de dependências](2026-10-30-phase4b-verificacao/deps-diff.txt), [toques de hoje](2026-10-30-phase4b-verificacao/tap-baseline-4b.md) e [figura na pausa](2026-10-30-phase4b-verificacao/figure-hold.md)

## Resumo

- **Entregue:** a aba Treinar nova, em código comum (`:screens`, Android e iOS), com as pastas (criar, renomear, mover rotinas, reordenar, excluir) e os cards de rotina com "Começar"; o editor de rotina (rota `routine/{id}`) com arrastar e soltar, ações de acessibilidade para mover, séries planejadas editáveis, aquecimento planejado, descanso por exercício, duplicar e excluir; a **linha de alvo** ("Alvo 3 séries · 6–8 reps · 93,7–137,79 lb") no card do treino ativo e no editor; a **foto do exercício personalizado** (galeria ou câmera, no Android) com o formulário aberto até o exercício chegar; e o **seletor com seleção múltipla** ("Adicionar 3 exercícios"). As telas antigas de Treinar e de rotina saíram.
- **Dados:** Room na **v6**: a coluna `routine_sets.warmup` (`INTEGER NOT NULL DEFAULT 0`) e a `MIGRATION_5_6`, aditiva. O backup ganhou o campo opcional `warmup` sem mudar a versão do formato. `versionCode` 16 e `versionName` 1.4.5 inalterados; nenhuma chave de DataStore mudou.
- **Pendências da 4A fechadas:** o formulário fica aberto até o detalhe abrir (M5 da crítica), o erro por comando (`errorSubject` e `subjectFailures`), a figura do detalhe sem pedir quadros na pausa (de 60,1 para 0,3 `doFrame` por segundo nas pausas) e as barras de sistema cinzas das janelas no tema claro (um gancho `LocalOverlayWindowSetup`, sem `expect/actual` novo).
- **Portão G1 (4A):** o dono aprovou o piloto em 2026-10-30, com uma mudança: o equipamento das figuras em cobre (laranja), `MoosclesFigureColors.equipment = cobre47 / cobre64`. Os lotes de figuras (Task L) vêm depois da 4B.
- **Toques:** nenhum fluxo medido passa do orçamento; montar uma rotina de 5 exercícios caiu de 13 para 9 toques, e mover o 5º exercício para o topo, de 4 para 2.
- **APK debug: +734.829 bytes** sobre a base da 4A (limite da fase, 4A e 4B juntas: 1.048.576; a 4B sozinha gastou 284.661). Nenhuma dependência nova (o diff é vazio).
- **Não provado:** o desempenho (jank do arrastar, da rolagem do Treinar com 30 rotinas e da rolagem do treino) ficou contaminado pela carga do Mac na medida da Task 18; a nova rodada está na seção [Crítica de tela (Task 19)](#crítica-de-tela-task-19). Tudo num iPhone e num Android físico também não foi verificado. A lista completa está [no fim](#o-que-não-foi-verificado).

## Ambiente

- Emulador `Mooscles_Pixel8_API35` (Android 15, 1080 × 2400), `emulator-5556`, num Mac M4 sem Xcode. O iOS só se prova no job `ios` do CI (`macos-26`, Xcode 26.4). Os spikes S4 usaram também AVDs temporários nas APIs 26 e 28 (`emulator-5560`, apagados no fim).
- Os APKs locais são assinados pela chave debug do Mac, que não é a das releases. A atualização por cima prova a compatibilidade dos dados entre dois builds, não o caminho do APK publicado para este build.
- Tudo o que está marcado "emulador" abaixo foi visto num emulador, nunca num aparelho físico.

## O que foi provado

### Testes

| Conjunto | Onde | Resultado |
| --- | --- | --- |
| Host dos quatro módulos | CI, run [41169992244](https://github.com/pedroyuuki/Mooscles/actions/runs/41169992244), `a855a9e` | **1086** testes, 0 falhas (mínimo 1086) |
| `:shared`, `:designsystem` e `:screens` no simulador iOS | o mesmo run | **275**, **375** e **641**, 0 falhas (mínimos 275, 375 e 641); entre os 275, os **7** testes de entrada do iOS que o `ci.yml` confere pelo nome |
| `release-script` | o mesmo run | verde |
| `:designsystem:connectedAndroidDeviceTest` | emulador, `4694edc` | 373 testes (64 classes), 0 falhas |
| `:screens:connectedAndroidDeviceTest` | emulador, `4694edc` | 640 testes (66 classes), 1 falha de harness, corrigida e provada (ver abaixo), 1 pulado (a captura opt-in da folha do piloto) |
| `:app:connectedDebugAndroidTest` | emulador, `4694edc` | 25 testes (9 classes), 1 falha de corrida de teste, corrigida, 2 pulados (as fixtures de desempenho opt-in) |

Para comparar, na 4A as suítes de aparelho tinham 339, 430 e 25 testes. O run 41157443080 teve um incidente de CI que não é falha de teste: o job `host` bateu o limite de 45 minutos dentro do `:app:testDebugUnitTest`, sem nenhuma linha de falha no log; a nova execução do mesmo job passou em 5 min 53 s (1085/0), e o `ios` já estava verde. Fica como item de observação: se voltar, tirar um dump de threads (um passo com `jstack`) antes de adivinhar a causa.

As duas falhas de aparelho da Task 18 ([detalhes](2026-10-30-phase4b-verificacao/device-suites.md)): `UiFlowTest.routineToWorkoutRestSummaryAndHistory` (o teste rolava para a linha antes de o editor alcançar o repositório; a rolagem passou a ficar dentro de um `waitUntil`, commit `53b380e`) e `CustomExerciseFormPhotoTest` (`performMeasureAndLayout called during measure layout`, causada pelo teste escrever o `UiState` fake direto da thread de teste; as escritas passaram por `runOnUiThread`, commit `4fd2b22`; 8 execuções seguidas da classe, 23/23 verdes cada, contra 1 falha em 4 antes). A prova é de evidência, não de certeza: o crash fatal de uma rodada, em outro teste da mesma classe, não reapareceu em 11 execuções e a causa dele não ficou provada. Nenhum código de produto mudou por isso.

Os testes que cobrem as exigências da spec e do plano:

- **Dados e migração (`:shared` e `:app`, host):** `RoutineWarmupMigrationTest` (a migração v5 → v6, a instalação nova e a migrada com o mesmo `sqlite_master`), `MigrationEquivalenceTest` (com a v6 e as referências da 1.4.5 intactas), `RoutineCommandsPersistenceTest` (24 testes dos comandos de rotina e pasta, provados por mutação), `BackupSampleFileTest` (a versão do formato continua 1 com o campo opcional) e `AppViewModelRoutinesHostTest`.
- **iOS:** `IosDatabaseTest.v5DatabaseMigratesToSixOnTheBundledDriver` migra um banco v5 de verdade no `BundledSQLiteDriver` (a mesma fixture `SchemaV5` do host), e o `RoutinesFlowTest` percorre as rotinas no simulador.
- **Design system:** `ReorderTest`, o spike S2 como componente (ver abaixo), `ExerciseFigureTest.holdRequestsNoFrames`.
- **Telas (`:screens`):** `TrainScreenTest`, `FolderManagerSheetTest`, `RoutineEditorScreenTest`, `RoutineEditorSetsTest`, `RoutineTargetFormatTest`, `ExercisePickerSheetTest`, `CustomExerciseFormPhotoTest`, `RoutinesFlowTest`, `TapBudgetTest` e `RoutinesAccessibilitySweepTest`.
- **Casca e foto (`:app`):** `UiFlowHostTest` e `UiFlowTest`, `ExercisePhotoStoreHostTest`, `AppViewModelPhotosHostTest`, `PhotoManifestHostTest` (o manifesto mesclado não ganha `CAMERA`, `READ_MEDIA_*` nem `READ_EXTERNAL_STORAGE`) e `OverlayWindowBarsHostTest`.

### Spike S2: arrastar e soltar

Aprovado no emulador e no simulador iOS (run 41082118225, `INJECTS_DRAG = true`, 363 testes do `:designsystem`, 0 falhas), sem biblioteca e sem `expect/actual` novo. O único teste que fica só no Android é o cancelamento do gesto, porque o simulador não injeta o cancelamento de toque. Ver o [relatório](2026-10-30-phase4b-spike-reordenar.md). Depois do spike, o componente teve duas correções reais, achadas pelo fluxo a 2x: a vaga que ficava longe do dedo depois de os cards recolherem (rejoin por deslocamento líquido, `1d5ea11` e `4694edc`, com um teste do dedo tremendo) e, só no iOS (slop de toque de 18 dp contra 8 dp), uma troca que o rejoin desfazia (`643fd3a` e `d2fed0f`; CI da lane, run 41153751679, verde).

### Spike S4: foto no Android

Aprovado nas APIs 26, 28 e 35: a foto sai em pé, abaixo de 300 KB, com pico nativo de ~16 MB e sem permissão nova. Um pipeline só em todas as APIs: `BitmapFactory` com `inSampleSize`, `ExifInterface` do framework e uma `Matrix`; o `ImageDecoder` ficou só como reserva na API 29+, porque na 28 ele falha com rotação EXIF (`invalid scale`) e usa ~100 MB. Ver o [relatório](2026-10-30-phase4b-spike-foto.md). Efeito de produto aceito: um HEIC na API 28 pode sair deitado.

### Atualização por cima

`main` em `2359ab6` (fase 3, banco v5) para a branch (`4694edc`), no emulador, com a rede desligada o tempo todo, `versionCode` 16 nos dois e o app aberto com o descanso correndo: `PRAGMA user_version` foi de 5 para **6**, **nenhuma** das 21 séries planejadas virou aquecimento, o `local_preferences.preferences_pb` ficou com o mesmo MD5, o alarme do descanso manteve o horário e o export depois é igual ao de antes, tirando o `exportedAt` e o `"warmup": false` de cada série planejada. As fotos pela galeria (seletor do sistema) e pela câmera (virtual do emulador) funcionaram sem nenhum pedido de permissão. Detalhes e capturas: [`upgrade.md`](2026-10-30-phase4b-verificacao/upgrade.md). Os dois APKs têm a chave debug deste Mac (ver "Ambiente").

### Sem rede

Instalação nova com a rede desligada desde antes: pastas, o editor, o seletor múltiplo (três exercícios entraram com 2 séries planejadas cada), a linha de alvo no treino e o exercício com foto da galeria funcionaram, sem `FATAL EXCEPTION`. O diff da branch não traz nenhum código de rede (as únicas linhas que casam com a busca são `Uri.parse` de uma URI de conteúdo da foto e um `testTag`); o manifesto ganha só o `<queries>` da câmera. [`offline.md`](2026-10-30-phase4b-verificacao/offline.md).

### Orçamento de toques

A contagem de hoje foi feita nas telas antigas, antes da troca ([`tap-baseline-4b.md`](2026-10-30-phase4b-verificacao/tap-baseline-4b.md)). O `TapBudgetTest` conta toques sobre o ViewModel real e reprova acima do orçamento da spec.

| Fluxo | Hoje (medido) | Spec, "Novo" | Medido na tela nova |
| --- | ---: | ---: | ---: |
| Montar uma rotina com 5 exercícios, da aba Treinar ao editor com os 5 | 13 | 9 | 9 |
| Mover o 5º exercício para o topo | 4 | 2 | 2 |
| Acrescentar uma série planejada igual à de cima | 1 | 1 | 1 |
| Marcar uma série planejada como aquecimento | n/a (o editor antigo não tinha séries planejadas) | 2 | 2 |
| Começar o treino de uma rotina | 1 | 1 | 1 |

### Tamanho e dependências

APK debug de 24.387.172 bytes, **+734.829** sobre a base do CI da 4A (23.652.343), das quais a 4A usou 450.168 e a 4B **284.661**; sobram 313.747 dos 1.048.576 da fase. Fontes (645.546 de 921.600) e fontes e ícones (913.826 de 1.048.576) dentro do orçamento. O `app-performance.apk` (R8) tem 3.325.957 bytes (+87.750). As saídas de `dependencies` da branch e da `main` têm o mesmo diff: vazio. Os APKs de comparação foram montados com a chave debug local, então o número do CI é o que vale; este é indicativo. [`apk.md`](2026-10-30-phase4b-verificacao/apk.md).

### Figura do detalhe na pausa

Durante as pausas entre os dois quadros, os `Choreographer#doFrame` caíram de 60,1 para 0,3 por segundo (emulador, `holdRequestsNoFrames` prova no relógio de teste, no Android e no simulador iOS). O que não foi medido é a bateria num aparelho. [`figure-hold.md`](2026-10-30-phase4b-verificacao/figure-hold.md).

### Acessibilidade e fonte grande (nos testes)

O `RoutinesAccessibilitySweepTest` (11 testes) confere rótulo em todo nó clicável, alvos de 48 dp, nenhum número ou botão cortado e as ações de reordenação na alça, em 16 combinações (claro e escuro × 1,0, 1,3, 2,0 e 2,01 × 320 e 412 dp), em até três passadas por estado. A ordem de leitura e o anúncio da reordenação vêm da árvore de acessibilidade e da região viva, não de um leitor de tela ouvido.

## Decisões e desvios registrados

Estão como notas datadas na [spec](../specs/2026-10-29-phase4-library-routines-design.md#notas-de-implementação) e no [plano](../plans/2026-10-30-phase4b-routines.md#notas). Em resumo:

- o pipeline da foto mudou (BitmapFactory + `ExifInterface` do framework em todas as APIs, `ImageDecoder` só como reserva), pelo resultado do S4;
- a comparação do `MigrationEquivalenceTest` mudou, as referências não; o `sqlite_master` da instalação nova e o da migrada saem iguais;
- a 51ª série restaurada sai como `SET_LIMIT`; "Adicionar série" passa a copiar a última linha (D14), o que muda o comportamento de hoje;
- o alvo de uma entrada trocada casa por ocorrência (F16);
- o `subjectFailures` libera as esperas por eco mesmo com dois erros seguidos;
- as barras de sistema cinzas foram corrigidas na 4B por um gancho de composição, e não adiadas (a decisão 19 do plano foi substituída);
- o formulário (criar e editar) espera o eco, com teto de 15 s (`saveEchoWait`), e Cancelar e Voltar são ignorados enquanto ele está ocupado;
- o equipamento das figuras passou a cobre (G1).

## Crítica de tela (Task 19)

Relatório completo: [crítica das rotinas](2026-10-30-phase4b-critica-rotinas.md), com as capturas em [`2026-10-30-phase4b-verificacao/`](2026-10-30-phase4b-verificacao/) e a nova rodada em [`performance.md`](2026-10-30-phase4b-verificacao/performance.md).

- **Corrigidos** (cada um com um teste que falhava antes):
  - **Alta:** a sheet de nome (nova rotina, renomear, pastas, renomear treino) ficava atrás do teclado e "Criar" não aparecia; agora termina acima da área coberta pelo teclado (`TrainScreenTest.nameSheetStandsAboveTheKeyboard`).
  - **Média:** a biblioteca aparecia por cerca de 800 ms entre o formulário salvo e o detalhe; o formulário fica até o detalhe assumir, com teto de 1,5 s (token `timing.formHandoff`).
  - **Média:** carga e repetições das séries planejadas mudavam de coluna com ou sem o ícone de recorde; o lugar do ícone fica reservado.
  - **Média:** a linha de alvo quebrava dentro de "+ 2 aquec." e as linhas ficavam com entrelinhas diferentes; o sufixo não quebra e a linha usa a entrelinha de `num.sm`.
  - **Baixa:** os menus ganharam os ícones Copy, FolderInput, ArrowUpToLine e ArrowDownToLine (Lucide 1.63.0, gerados offline; os 54 ícones existentes saíram idênticos; `IconsTest` com 58 e o orçamento do APK passam).
- **Adiados, com motivo no relatório:** ao erguer o primeiro card do editor, a lista pula uns 40 dp (não aparece com toque injetado; precisa de investigação com o dedo num aparelho); as barras do sistema das sheets e diálogos continuam cinzas no tema claro (os ícones já estão certos; resolver pede janela de diálogo de ponta a ponta, uma decisão de design system); e oito achados baixos de polimento.
- **Desempenho, nova rodada: não provado.** O controle (rolagem da Biblioteca, 0,5–1,5% na 4A) deu 34,3–52,4%, com carga média de 3,3–6,2 e 15,2 GB de swap em uso: Treinar com 30 rotinas 35,2–56,9%, arrastar no editor 27,5–54,8%, rolagem do treino 31,8–46,8%. A gravação depois do arraste está provada (Task 18). Os orçamentos de quadros ficam para uma máquina livre ou um aparelho.

## Pendências que não bloqueiam o merge

- **Desempenho não provado** (ver "O que não foi verificado" e a seção acima).
- **Lacuna da decisão 11 da 4A:** o nome que o `MoosclesViewModel` grava no timer do descanso automático continua o gravado, em inglês, até as mensagens do ViewModel (fase 6).
- **Pulo da lista ao erguer o primeiro card do editor** e **barras cinzas das sheets no tema claro** (crítica, acima).
- **Achados menores estacionados nas revisões** (fora do histórico publicado, em `.superpowers/sdd/2026-10-30-phase4b-routines/`): um erro que chega durante a animação de saída de uma sheet é limpo sem ser visto; durante o arraste o espaçamento do editor desloca um pouco o nome e o "Adicionar"; um erro sem relação mostra por um instante o valor salvo antigo numa linha com escrita em voo (sem perda de dados); o `ContrastTokensTest` encosta em 3:1 no par `borderStrong` em `bgOverlay`.
- **Observação de CI:** o travamento único do `:app:testDebugUnitTest` até o limite de 45 minutos (acima).

## O que não foi verificado

- **Tudo num iPhone:** VoiceOver, o arraste com o dedo, a figura e o desempenho. A foto do exercício personalizado e o seletor de fotos e a câmera nem existem no iOS (a implementação vazia do `ExercisePhotoStore`).
- **Num Android físico:** TalkBack nas ações de reordenação (o anúncio foi provado pela região viva, não ouvido), a câmera real, o seletor de fotos do One UI, a orientação das fotos de outros fabricantes (a prova do S4 usou imagens montadas à mão e o emulador), 120 Hz no arraste, a bateria da figura e Android 8 a 14 fora do 15.
- **Android 8 e 9 com aparelho:** as APIs 26 e 28 foram provadas só em AVDs do spike, com a imagem de teste; uma foto HEIC na API 28 sai deitada e na API 26 não decodifica (vira o aviso de falha), risco aceito.
- **O desempenho:** a rolagem da aba Treinar com 30 rotinas (duas execuções limpas, 2,83% e 4,35%, abaixo de 5%, mas a repetição estável não foi provada), o jank do arrastar e a rolagem dos cards do treino contra a fase 3 (a medida da Task 18 ficou contaminada). A Biblioteca, que a 4B não mudou, passou de 0,5–1,5% na 4A para 36–62% na mesma sessão, o que mostra o ruído do ambiente.
- **A atualização a partir do APK publicado:** a chave de assinatura original não está neste Mac, e a v1.5.0 não foi publicada.
- **A notificação do descanso em segundo plano** depois da troca de APK, e a rotina "Upgrade record" no editor depois da atualização (só a "Upgrade custom" foi aberta).
- **O `present()` completo da base grande depois da 4B:** as amostras de `present()` são de uma série ou de uma ordem, não do histórico inteiro; o limite do `WorkoutPresentationTimingTest` não foi reexecutado na Task 18.
- **A qualidade das figuras, dicas e nomes para quem treina,** além da revisão por conteúdo. O G1 foi aprovado pelo dono sobre a folha do piloto (20 exercícios); o resto das figuras depende dos lotes.
- **As suítes de aparelho depois de `4694edc`:** o merge `95ca601` (a correção do arraste no iOS) e o merge do cobre (`41a727a`) vieram depois das rodadas completas; o `ExerciseFigureTest` com o cobre (10/0) e as classes de arraste (80/80) foram rodados à parte, mas as três suítes inteiras não foram refeitas. Depois da Task 19 rodaram as 14 classes de telas tocadas (254/255, e a classe corrigida 23/23) e, depois da correção da revisão final, as cinco classes do formulário e da biblioteca (91/91).
- **A release com a chave original de assinatura.**
