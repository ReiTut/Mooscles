# Handoff: como continuar o Mooscles

**2026-11-05 Phase 5B checkpoint:** the owner's instruction resumes Phase 5B only, superseding the pause notices below. Branch `codex/phase5b-history` starts at `c9ba9b1` (v1.5.3 / Build 25); implementation is in `9a0740a`, metadata reuse in `26a8889`, and the narrow/large-font saved-editor correction in `7fc1b2e`. Shared History/calendar/overall progress, saved detail/editing and the common share-preview interface are implemented. Read the [plan](plans/2026-11-04-phase5b-history.md), [verification](reports/2026-11-05-phase5b-verificacao.md), [performance evidence](reports/2026-11-05-phase5b-performance.md) and [critique](reports/2026-11-05-phase5b-critique.md).

Final clean debug/preview APKs retain the original signer and Build 25. The isolated offline Build 16 → 25 upgrade preserves actual encoded data/preferences, exact values and the running timer. CI on `26a8889` passed 1,337 host and 283/428/1,006 iOS tests; the final editor-fix Linux host also passed 1,337 tests, and its 29 affected Android cases passed. Two iOS attempts never acquired hosted runners (macOS arm64 capacity); final native execution is blocked, not green. The controlled standalone index benchmark meets its CPU budget (58.5 ms median), but real in-app unit switches measured 77.1 ms median and **the scrolling criterion remains unmet** on this host-GPU emulator. Most traced slow frames wait on the graphics handoff; some History layout/prefetch outliers also exist. Do not mark all performance acceptance complete or claim a general stutter fix. First-frame navigation budgets remain unverified.

No version change, merge, tag, release or Phase 6/7 work is authorized. Preserve the unrelated `gradle.properties` edit and permanent PC-only backup. Physical-phone testing requires a concrete approval request and a complete stop of all work while awaiting approval; an authorized connection alone is not consent. The next development work stays within closing Phase 5B verification, especially the graphics/frame limitation.

**2026-11-04 full-release follow-up:** [v1.5.3 / Build 25](releases/v1.5.3.md) prepares the beta startup changes and PR-statistic follow-up for the stable channel through the normal PR-to-main process. The original debug signer, schema 6 and updater contract remain unchanged. Local source/APK and published beta assets were verified in a permanent PC-only backup outside the project; it is not a Git or release asset. The unrelated local `gradle.properties` edit remains excluded. Phase 5/5B stays paused, including planning.

**2026-11-04 local follow-up:** `codex/workout-pr-stat` adds the existing workout-wide distinct PR-exercise count beside Sets in the completion summary, saved detail and shared PNG header/preview. See the [design](specs/2026-11-04-workout-pr-statistic-design.md), [plan](plans/2026-11-04-workout-pr-statistic.md) and [verification](reports/2026-11-04-workout-pr-statistic-verification.md). No version, schema, signing or release change. Phase 5/5B remains paused, and the unrelated local `gradle.properties` edit stays separate.

**2026-11-03 follow-up:** [v1.5.3-beta.1 / Build 24](releases/v1.5.3-beta.1.md) prepares the localized startup PR fast path and forward-entry fade removal from `codex/workout-startup-latency`, through the existing PR-to-Test prerelease process. The [investigation](reports/2026-11-03-routine-startup-latency.md) records 70 focused host checks and physical-phone evidence, including mixed overall startup/scrolling results. Stable v1.5.2 remains Latest; discovering this beta requires Include prereleases. No storage, signing or updater contract change. The unrelated local `gradle.properties` edit is preserved outside the release commit. Phase 5/5B remains paused, including planning; Phase 5A is unchanged.

**2026-11-02 follow-up:** the shared workout wordmark is prepared for [v1.5.2 / Build 23](releases/v1.5.2.md), from local branch `codex/share-wordmark`, using the existing debug distribution signer and updater metadata contract. [Verification](reports/2026-11-02-share-wordmark-verification.md) covers the 22 host and 6 emulator checks plus actual PNG/preview/chooser observations. The launcher icon, workout data and sharing behavior remain unchanged. Phase 5/5B stays paused, including planning; the older roadmap continuation instructions below do not authorize it.

**2026-11-01 follow-up (user instruction takes precedence): Phase 5/5B is paused, including planning.** The local `codex/language-launcher-icon` follow-up prepares [v1.5.1 / Build 22](releases/v1.5.1.md) for the original `ReiTut/Mooscles` repository: Settings language selection, localized legacy labels, exact-weight/draft retention across language changes and new launcher assets. Existing Phase 5A features remain unchanged. Read the [focused verification](reports/2026-11-01-language-launcher-verification.md) and the current release notes before the older state below. The original repository has CI enabled; the older statement below that it has no Actions runs is historical and superseded. This Windows checkout uses `origin` for `ReiTut/Mooscles`; the Mac-specific remote/account commands below do not describe it.

Atualizado em 2026-11-01, depois da release 1.5.0 (fases 0 a 5A) no repositório original. Este arquivo é a porta de entrada para o agente (Claude Code, Codex ou outro) que vai continuar o desenvolvimento. Ele não repete o que já está no [AGENTS.md](../../AGENTS.md) nem no [ROADMAP](ROADMAP.md): diz onde as coisas estão, o que falta e como trabalhar sem repetir os erros de antes.

Quando uma etapa terminar, atualize a seção "Estado" e a "Próxima etapa" deste arquivo junto com o ROADMAP.

## Estado

- **Repositório de trabalho:** desde 2026-11-01 é o original, `ReiTut/Mooscles` (remote `upstream` neste clone), onde o dono do fork (`pedroyuuki`) é colaborador com permissão de push. O fork `pedroyuuki/Mooscles` (`origin`) virou arquivo: a `main` dele guarda o histórico completo das fases 0 a 5A (510 commits) e as capturas de verificação, que não entram no original.
- **`main` do original:** `96336a6`, o merge squash do PR [#4](https://github.com/ReiTut/Mooscles/pull/4) com tudo das fases 0 a 5A. O CI dessa árvore rodou no fork ([41258967331](https://github.com/pedroyuuki/Mooscles/actions/runs/41258967331)): host 1264 testes; simulador iOS com `:shared` 280, `:designsystem` 414 e `:screens` 892. **O original não tem GitHub Actions rodando** (nenhum run até hoje): para ter CI, dê push da branch também no fork, ou peça ao dono do original para ligar o Actions.
- **Fases prontas e na `main` do original:** 0 (proteger os dados), 1 (base KMP), 2 (design system), 3 (treino ativo), 4 (biblioteca, rotinas e 285 figuras) e 5A (gráficos, progresso do exercício e aba Recordes).
- **Release publicada:** [v1.5.0](https://github.com/ReiTut/Mooscles/releases/tag/v1.5.0) (`versionCode` 21, Latest), assinada com a chave original, acima das pré-releases 1.4.7-beta.1 a beta.3 (`versionCode` 18 a 20) que o dono do original tinha publicado de outras branches (`codex/*`, que continuam lá). Quem estava na 1.4.6 ou nas betas atualiza sozinho.
- **Próxima etapa:** a **5B** (histórico redesenhado, calendário, gráficos gerais, detalhe do treino com edição e a prévia de compartilhamento). A spec já está aprovada; falta o plano.
- **Nada em andamento.** As branches antigas do fork já estão mescladas.
- **Próxima release:** `versionCode` 22 ou mais (nunca reutilizar) e `-PreviousVersionCode 21` no `build.ps1`.

## Antes de começar, leia nesta ordem

1. [AGENTS.md](../../AGENTS.md), inteiro. Ele traz a arquitetura, os comandos, as regras de compatibilidade (que protegem os dados de quem já usa o app) e o fluxo de desenvolvimento. O `CLAUDE.md` só importa o AGENTS.md e acrescenta o `compat-reviewer` e o hook que protege os schemas.
2. [ROADMAP.md](ROADMAP.md): as fases, os objetivos e a tabela de andamento.
3. A spec da fase 5, [specs/2026-10-31-phase5-progress-history-design.md](specs/2026-10-31-phase5-progress-history-design.md). Leia as decisões D1 a D19 e as "Notas de implementação" no fim, que trazem as mudanças feitas durante a 5A, com data.
4. O relatório da 5A, [reports/2026-10-31-phase5a-verificacao.md](reports/2026-10-31-phase5a-verificacao.md): o que foi provado, o que não foi e as pendências.
5. O plano da 5A, [plans/2026-10-31-phase5a-charts-progress.md](plans/2026-10-31-phase5a-charts-progress.md), como modelo de tamanho e de forma para o plano da 5B.

## Regras que o dono já decidiu

- **Idioma:** responda ao dono em português do Brasil. O código, os comentários e os KDocs são em inglês; os documentos em `docs/superpowers/`, em português.
- **Autonomia:** o dono delegou as decisões de produto e de técnica ("pode fazer as melhores escolhas decidindo por mim"). Decida, registre a decisão e o motivo no documento da etapa e siga. Pare só para o que é irreversível, de segurança ou fora do repositório.
- **Branches e merge:** cada etapa tem a sua branch a partir de `upstream/main` (`git fetch upstream && git switch -c feat/slug upstream/main`). Ao terminar (revisão final e CI verde), abra um PR para `ReiTut/Mooscles:main` e faça o merge **squash** (o dono do fork tem permissão; o dono do original já concordou em que a `main` siga este desenvolvimento). Nunca use force push. Não use a `main` do fork como base: a história dela diverge da do original (o PR #4 foi squash).
- **Capturas e evidências pesadas** (PNG, traces) não entram no original: deixe em `docs/superpowers/reports/<pasta>/local/` (ignorado pelo git). Se um relatório precisar citar uma captura, publique-a num commit só de capturas na `main` do fork e use a URL absoluta, como os relatórios das fases 2 a 5A já fazem.
- **Ritmo pedido pelo dono (da fase 5 em diante):** para cada task, rode os testes de host, a compilação dos testes iOS e, no emulador, só a classe de UI tocada (e só quando a task muda comportamento). As suítes completas de aparelho, a atualização por cima, o teste sem rede, o desempenho e o tamanho rodam **uma vez, no fim da fase**, e as correções entram num bloco. O push para o CI acontece só nos checkpoints.
- **Revisões proporcionais ao risco:** use revisão completa (o modelo mais forte) para dados, navegação, concorrência e telas com comportamento novo. Para texto, testes e documentos, basta uma revisão curta ou uma checagem do controlador. Toda branch passa por uma revisão final da branch inteira e pelo `/code-review` antes do merge.
- **iOS:** não há iPhone nem Xcode completo no Mac. O iOS só compila e roda testes no CI (job `ios`, `macos-26`). O app iOS em si (fase 7) ficou para depois, a pedido do dono ("deixa o ios pra outra hr").
- **Celular do dono:** é um Galaxy S23 Ultra. **Nunca** instale nem desinstale nada nele: só o dono instala, e só o build `preview` (`com.mooscles.preview`, que fica ao lado do app de verdade). Entregue o preview copiando o APK para `~/Desktop/` e mandando para o dono.
- **Segredos:** ficam no 1Password, cofre "Agentes" (veja `~/.config/op/agentes.env`). Nunca escreva o valor de um segredo em comando, arquivo, commit ou memória.

## Ambiente da máquina (Mac do dono)

- **Hardware e disco:** Apple M4 com 16 GB. O repositório está num **disco externo**, em `/Volumes/Disco Macbook/Projetos-Github/Mooscles`.
  - O disco às vezes desconecta. Os sinais: o Bash diz que o diretório "não existe mais", o emulador morre e o `adb devices` fica vazio.
  - Nesse caso, pare tudo e não escreva nada em `/Volumes/...`: isso criaria uma pasta falsa no disco do sistema. Espere o dono reconectar o disco e depois confira `git status` e `git fsck`.
- **JDK:** 21, por `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
- **Android SDK e AVD:** ficam no disco externo, pelo link `~/Android`. Use `ANDROID_HOME=$HOME/Android/sdk` e `ANDROID_AVD_HOME=$HOME/Android/avd`. Um shell não interativo pode não ler o `.zshrc`, então exporte essas variáveis em cada comando.
- **pwsh:** fica em `~/.local/bin/pwsh`. É o que roda `tools/check-design-tokens.ps1`, `tools/check-apk-budget.ps1` e os scripts de release.
- **Emulador:** AVD `Mooscles_Pixel8_API35` (Android 15), na porta **5558**.
  - Para subir:
    ```sh
    export ANDROID_AVD_HOME=$HOME/Android/avd
    nohup $HOME/Android/sdk/emulator/emulator -avd Mooscles_Pixel8_API35 -port 5558 -gpu host -read-only -no-snapshot-save -no-boot-anim -no-audio &
    ```
  - Espere o `sys.boot_completed` e zere as três escalas de animação (`settings put global window_animation_scale 0`, `transition_animation_scale 0` e `animator_duration_scale 0`).
  - `emulator-5554` e `emulator-5556` podem aparecer como `offline`: são zumbis de quedas anteriores. Ignore-os. Se o `crashpad_handler` ou o `netsimd` de um deles estiver com CPU alta, mate o processo (é nosso). Na 5A, um zumbi a 270% de CPU estragou as medidas de desempenho.
- **Memória:** a swap costuma ficar quase cheia por causa de outros apps do dono (navegadores, IDEs, às vezes o GeForce NOW). Anote isso junto de toda medida de desempenho. Não rode Gradle enquanto algum agente estiver dirigindo a UI do emulador.
- **Comando sem prazo:** o macOS não tem `timeout`. Limite todo Gradle com `perl -e 'alarm shift; exec @ARGV' <segundos> ./gradlew --max-workers=2 …`: um lint já travou a 0% de CPU.
- **Uso do emulador por mais de um agente:** passe tudo o que usa o emulador pelo script `.superpowers/tools/gated-run-lock.sh <log> <args do gradle>`, com `GATE_DIR=<pasta do checkout ou worktree>`. O script fica fora do git, nesta máquina.
  - Ele pega uma trava entre agentes e espera 60 s de emulador ocioso antes de rodar.
  - Sai com 5 ("DEVICE UNREACHABLE") quando o emulador não está online.
  - Exemplo de uma classe só: `:screens:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.mooscles.screens.records.RecordsScreenTest`.
- **`gh`:** está logado como `pedroyuuki`. Passe sempre `-R pedroyuuki/Mooscles`. Quando um job iOS é cancelado por falta de máquina macOS, rode de novo com `gh run rerun <id> --failed -R pedroyuuki/Mooscles`.
- **Fixtures locais (fora do git):** a base grande fica em `.superpowers/scratch-backup/files/mooscles-large-208.json`: 208 treinos e cerca de 12,7 mil séries.
  - Os ids de exercício dela não têm o prefixo `catalog_`, então alguns nomes aparecem em inglês, que é o nome gravado (o comportamento especificado).
  - Para testar atualização ou fixtures, desligue a rede do emulador antes (`svc wifi disable` e `svc data disable`). Sem isso, o app 1.4.5 acha a 1.4.6 publicada no original e começa a baixá-la.
- **Worktree velho:** `git worktree list` pode mostrar um worktree de `spike/phase4b-photo` numa pasta temporária que já não existe. `git worktree prune` limpa o registro.

## Como trabalhar (o fluxo que funcionou nas fases 2 a 5A)

1. **Spec → plano → execução**, com um arquivo de cada etapa em `docs/superpowers/` (ver o README da pasta). Para a 5B, a spec já está aprovada: comece pelo plano.
2. **Plano:** tasks pequenas e verificáveis, com arquivos, interfaces, testes nomeados e valores exatos, no tamanho do plano da 5A.
   - Antes de executar, faça uma varredura de conflitos do plano (o "pré-voo"): pares de tasks que dividem arquivo ou interface, e testes que não provam o que dizem. Na 5A ela achou 25 problemas antes de começar.
3. **Execução por subagentes**, com um ledger em `.superpowers/sdd/<plano>/progress.md` que diz o que está pronto, cada decisão tomada e cada commit. Depois de uma compactação de contexto, confie no ledger e no `git log`, não na memória.
   - Um implementador por task e uma revisão por task.
   - A rodada de correção volta ao mesmo implementador.
   - Tasks independentes podem rodar em paralelo em worktrees separados (`lane/...`), com no máximo um usando o emulador. O controlador junta as lanes e **soma** os contadores do CI.
4. **Contadores do CI:** o `ci.yml` tem `MIN_HOST_TESTS`, `MIN_IOS_TESTS`, `MIN_DS_IOS_TESTS` e `MIN_SCREENS_IOS_TESTS`, cada um com uma linha de comentário por classe. Quem acrescenta testes sobe o mínimo na mesma mudança. Um teste aposentado só desconta com o motivo escrito.
5. **Fim da fase:**
   - as suítes completas de aparelho;
   - a atualização por cima a partir do APK da `main`, com a base grande, um treino ativo e o descanso correndo;
   - o teste sem rede;
   - o desempenho pelo [PERFORMANCE.md](../../PERFORMANCE.md);
   - o tamanho do APK e o diff de dependências;
   - a crítica de tela (`critique-screen` pelo `kit-design`), nos dois temas, em en e pt-BR e com fonte a 200%;
   - a revisão final da branch inteira, o `/code-review`, o relatório em `reports/` e o `VERIFICATION.md` honesto (o que não foi verificado aparece como não verificado).
6. **Depois do merge:** atualize o ROADMAP (a seção da fase e a tabela de andamento), o `releases/v1.5.0.md` (o item no PR e as pendências) e este HANDOFF. Mande ao dono um APK `preview` (`./gradlew :app:assemblePreview`).

## Próxima etapa: fase 5B

**O quê:** histórico redesenhado (aba Histórico com os segmentos Treinos e Progresso), calendário mensal, filtros, gráficos gerais, um detalhe do treino novo com modo de edição, e a prévia de compartilhamento no design system. Tudo está na spec, seção "Telas da 5B" e decisões D2, D12, D13, D14 e D17. Branch: `feat/phase5b-history`, a partir de `upstream/main` (o `ReiTut/Mooscles`).

### O que a spec já decide

- **D2, D12:** a aba Histórico ganha os segmentos "Treinos" e "Progresso". O calendário é mensal, não um mapa de calor anual: `MonthCalendar` em `designsystem/.../charts/`, sem `kotlinx-datetime` (recebe o número de dias, o deslocamento do primeiro dia e as marcas). A célula tem no mínimo 40 dp, que é a única exceção documentada ao alvo de 48 dp.
- **Filtros:** busca bilíngue (todas as palavras no mesmo treino), "Rotina" (seleção múltipla, com "Outros treinos"), "Exercício" (o `ExercisePickerSheet` da 4A em seleção única), "Com recorde", o dia do calendário como chip, e "Limpar filtros". A lista é agrupada por mês, com cabeçalho fixo.
- **Segmento Progresso:**
  - os números do período (Treinos, Por semana, Séries, Volume);
  - treinos por semana e volume por semana (`MBarChart`, que já existe);
  - a frase "N de M semanas com treino" (D17, a única medida de constância, sem "sequência");
  - os músculos (D9: 1 por região principal e 0,5 por secundária, no `BodyMap` da 4A);
  - os 5 exercícios mais feitos, que abrem o progresso do exercício.
- **D13, detalhe do treino** (`workoutDetail/{id}?summary=false`): uma tela de leitura nova, com um modo de edição que faz **exatamente** o que o antigo faz. Isso inclui editar o descanso do exercício (nota do controlador na spec). O que a edição não pode ter:
  - não há desfazer ao remover uma série: o `restoreSet` exige treino ativo, então a remoção pede confirmação;
  - não roda timer, sugestão nem háptico de recorde;
  - o `updateSet` só inicia o descanso com o treino `ACTIVE`, regra que ganha um teste.
- **D14, prévia de compartilhamento:** só a prévia muda. A `SharePreviewSheet` vai para `com.mooscles.screens.share`, e os textos saem de `res/values*/share_strings.xml` do `:app` para o `strings_share.xml` do `:screens`. O cartão, os PNG, o `FileProvider` e a detecção do WhatsApp continuam no `:app`, com as mesmas tags de teste.
- **D15, D16:** tudo é derivado em memória do `UiState`, pelo `ProgressIndex` e pelo `ProgressIndexCache` que a 5A criou (um só, aquecido na abertura). **Nenhuma** mudança de schema, de DataStore, de backup, de manifesto ou de rede.
- **D19, telas antigas que saem:** a tabela "Telas antigas e testes do `:app`" da spec lista o que sai:
  - `HistoryScreen` e `WorkoutHistoryCard`;
  - `WorkoutDetailScreen`, `RecordMilestone` e `recordAchievementText`;
  - o `WorkoutScreens.kt` inteiro;
  - o que só o histórico usava em `LibraryScreens.kt` (o `ChoiceFilter` sai quando o `ExercisePicker` sair);
  - o que ficar sem chamador no `Common.kt`;
  - a UI do `WorkoutSharePreview` e o `MissingScreen`.

  Os testes que só cobriam essas telas são **portados**, não apagados (`HistoryRecordsHostTest`, `InlineRestEditorHostTest` e outros), como a tabela diz.

### Ordem sugerida

1. **Spike S2 primeiro:** o `MonthCalendar` a 320 dp e com fonte a 2,0, nos dois temas e nas duas plataformas (iOS pelo CI), com um mês de 6 semanas e um de 4. O critério de aprovar e reprovar está na seção "Spikes" da spec. Se reprovar, o calendário vira só visualização e o filtro de dia passa a ser o caminho.
2. Antes de mexer nas telas, conte os toques nas telas antigas: ver um treino de uma data, editar uma série de um treino salvo, excluir um treino e compartilhar do histórico. Registre a contagem como `tap-baseline-5b.md`, do mesmo jeito que o `reports/2026-10-31-phase5a-verificacao/tap-baseline-5a.md`. A regra é que focar o campo de busca conta como toque e o texto digitado não conta.
3. Depois: as consultas puras do histórico (filtros, agrupamento por mês e números do período, sobre o `ProgressIndex`), o calendário e os gráficos gerais, a aba nova, o detalhe com o modo de edição (com um teste por item de paridade), a prévia, a troca no `:app` e a saída das telas antigas, o fluxo de ponta a ponta, o orçamento de toques e a varredura de acessibilidade. Por fim, a rodada de fim de fase (ver "Como trabalhar").

### Limites

- **APK:** a fase inteira tem 524.288 bytes sobre a base de `tools/apk-budget.json`. A 5A gastou **284.907**, então sobram cerca de **239 KB** para a 5B. Confira com `pwsh tools/check-apk-budget.ps1 -Apk app/build/outputs/apk/debug/app-debug.apk` depois de um `:app:clean`, porque um build incremental dá falso "OVER".
- **Desempenho:** veja a seção "Desempenho" da spec. O índice no build R8 leva cerca de 4 ms aquecido. Um `present()` completo chega a 478 ms na troca de kg para lb, mas roda em `Dispatchers.Default`.
- **Toques:** cada fluxo novo fica ≤ à contagem das telas antigas, provado no `TapBudgetTest`.

### Pendências da 5A que vão para a 5B

- Reler "hoje" também quando o app volta para a frente (`ON_START`) no `rememberProgressCalendar` (em `screens/.../progress/ProgressCalendar.kt`). Hoje ele relê só à meia-noite, por `delay`, que segue o tempo acordado do aparelho; depois de um sono longo, a data pode atrasar.
- Criar um helper `assertAtTop()` no `RecordsScreenTest`, que compare o topo do primeiro cabeçalho com o topo da lista, e usá-lo nas checagens de "está no topo" (linhas ~589, 618, 740 e 747). Hoje elas não distinguem uma linha posta no padding do topo.
- `ProgressIndexCache.start` roda com a trava presa: escrever no KDoc que o `context` precisa despachar (um dispatcher imediato travaria), ou chamar `start()` depois de soltar a trava.
- Mostrar a convenção na linha do recorde (o M3 da Task 11, adiado): hoje a linha não diz se foi "Por mão" ou "Peso adicional".
- Medir de novo a rolagem do gráfico de progresso depois da `graphicsLayer` da Task 17, e a rolagem de controle da Biblioteca. A Biblioteca deu 1,75–2,3% no emulador contra cerca de 1% na 4A. A 5A não mexeu nela, e a causa provável é a memória cheia. Confira no S23 se o dono testar.
- As consultas dos Recordes e as teclas da busca não foram medidas no build R8, só no APK de teste (p95 de 1,4 a 2,6 ms).
- O ano não aparece nos rótulos do eixo x (deixado de propósito: a tabela, o "desde" e a leitura mostram o ano). Reavalie se o gráfico de período "Tudo" confundir.
- Fixar o comportamento abaixo do Android 13 do `deviceRegion()` (o `Resources.getSystem()`), que nunca rodou num aparelho. O lado iOS (`NSLocale`) também só compilou.

## Depois da 5B

As fases estão descritas no [ROADMAP](ROADMAP.md). O que importa para planejar:

- **Fase 6, telas restantes e paridade:**
  - Configurações completas e a tela de atualizações. As Configurações já foram redesenhadas como piloto na fase 2; falta o que sobrou.
  - Um onboarding curto.
  - A saída de todo componente antigo do `:app`, inclusive o `ui/Theme.kt` com o `MaterialTheme` de antes.
  - As mensagens prontas do `MoosclesViewModel` (`UpdateUi.message`, `BackupUi.message`, `BackupUi.working`) e o nome gravado no timer do descanso, que continuam em inglês e passam para os recursos.
  - O item #11 da fase 2: o `MTextField` lê o valor antes do nome no TalkBack.
  - "Pronto quando": não sobra tela antiga, e não há medida solta em `dp` fora dos componentes.
- **Fase 7, iOS na App Store:** precisa de iPhone, Xcode e Apple Developer Program, decisões e custo do dono.
  - O código comum já compila e testa no simulador.
  - Falta o projeto do app, o timer por notificação local `timeSensitive`, o compartilhamento por `UIActivityViewController`, o import do backup do Android e o privacy manifest.
  - Antes do import, faça um teste de ida e volta do backup entre plataformas: a formatação de `Double` pode diferir entre Kotlin/Native e a JVM.
- **Fase 8, backup na nuvem do usuário:** Google Drive `appDataFolder` e iCloud, sem servidor do projeto.
- **Fase 9, extras:** só com motivo claro e spec própria (widgets, Health Connect e HealthKit, Live Activity, relógio, treino guiado, sincronização).
- **Cada release** segue a seção "Releases" do AGENTS.md e o `UPDATES.md`, como a [v1.5.0](releases/v1.5.0.md) fez (ver "Como publicar uma release" abaixo).

## Como publicar uma release

1. Num PR: subir `versionCode` e `versionName` em `app/build.gradle.kts`, `-PreviousVersionCode` no `build.ps1` (o último código distribuído) e atualizar `README.md` (Download), `VERIFICATION.md` e, se o fluxo mudar, `UPDATES.md`. Merge squash na `main` do original.
2. Criar a tag anotada `vX.Y.Z` no commit do merge e dar push para `upstream` (sem force).
3. Num worktree limpo e destacado nesse commit (copie o `local.properties`), com o emulador ligado e o `com.mooscles` desinstalado dele, rodar o `publish-release.ps1` assinando com a chave original pelas propriedades injetadas do Gradle (o `signingConfig` do projeto não muda):

   ```sh
   env "ORG_GRADLE_PROJECT_android.injected.signing.store.file=/Volumes/Disco Macbook/Projetos-Github/Mooscles/docs/private/debug.keystore" \
       "ORG_GRADLE_PROJECT_android.injected.signing.store.password=android" \
       "ORG_GRADLE_PROJECT_android.injected.signing.key.alias=androiddebugkey" \
       "ORG_GRADLE_PROJECT_android.injected.signing.key.password=android" \
       pwsh -NoProfile ./tools/publish-release.ps1 -Tag vX.Y.Z -NotesFile <notas em inglês> -PreviousVersionCode <último> -Remote upstream
   ```

   Sem `-Publish` ele só cria um rascunho. Confira o assinante (`apksigner verify --print-certs`, SHA-256 `0cd093b2…bb35`), o `update.json` e, de preferência, instale a release anterior oficial no emulador e o APK novo por cima (`adb install -r`), conferindo `PRAGMA user_version` e que o app abre. Depois rode de novo com `-Publish`.
4. As notas da release são só o texto para o usuário, em inglês (na v1.5.0, a seção "Notas para o usuário" de `releases/v1.5.0.md`). Atualize o ROADMAP e este HANDOFF.

## Pendências com o dono (não bloqueiam o trabalho)

- **Chave de assinatura:** resolvida em 2026-10-31. O dono do original mandou uma cópia do `debug.keystore`, que está neste Mac em `docs/private/` (ignorado pelo git; nunca dê commit nem a mova para dentro de outro caminho versionado). Falta guardar uma cópia fora da máquina (o dono do fork tem 1Password, cofre "Agentes").
- **Testes no S23 Ultra com o `preview`:** os APKs de preview das fases 3, 4B, a cópia de série e a 5A foram entregues; o último é `~/Desktop/Mooscles-Preview-1aa69bd.apk`, da 5A. Nada disso foi verificado num aparelho físico:
  - TalkBack (resumos dos gráficos, o calendário da 5B, o item #11);
  - háptico e a notificação na gaveta e na tela de bloqueio;
  - teclado Samsung;
  - fluidez a 120 Hz e o arraste no gráfico.

  Registre o que o dono relatar no `VERIFICATION.md`.
- **Decisões de custo:** iPhone e Xcode completo (cerca de 10 GB) para a fase 7, e o Apple Developer Program.

## Armadilhas já pagas (não repita)

- **Testes:**
  - Nunca role uma lista lazy com o relógio de teste parado: o teste trava para sempre.
  - Anote o estado do teste onde a composição lê, não num `SideEffect` não rastreado: isso já fez uma asserção passar sobre uma lista vazia.
  - Uma linha posta no padding do topo de uma lista conta como "posta", então "a primeira linha está no topo" precisa comparar posições na tela.
  - O Robolectric roda fora do modo de toque, e lá o `clearFocus()` entrega o foco à primeira linha focável, o que causa uma rolagem falsa. Ponha a janela em modo de toque nos testes de host que simulam toques.
  - Para fonte a 2,0 no iOS, use `LINEAR_TWO_X_FONT_SCALE`. No simulador iOS o teclado não abre: injete `WindowInsets(bottom = 300.dp)`.
  - Os testes de recriação usam `locale = null` (limite do `LocaleEpoch` do harness).
  - Use `kotlin.concurrent.Volatile`, nunca o `kotlin.jvm`.
- **Idioma:** o idioma por app reescreve também o `Resources.getSystem()`. A região do aparelho vem do `LocaleManager.getSystemLocales()` (API 33+).
- **Estado:**
  - Um composable que continua na tela enquanto quem o hospeda sai da composição vaza o seu `rememberSaveable` para a próxima sessão. Use um `SaveableStateHolder` por sessão.
  - Caches por identidade (`===`) precisam de chaves por identidade nos `LaunchedEffect`: o `equals` de lista não basta.
- **Duas lanes no mesmo emulador** derrubam os testes uma da outra, porque um reinstala o APK do outro. Use sempre o script com trava. Uma rodada morta pode deixar um "run started" no logcat que mantém a trava fechada: rode `adb logcat -c`.
- **Dados:** nunca capture exceção dentro de `writeTransaction` (ver o AGENTS.md). Nunca edite os JSON de schema à mão: o hook bloqueia, e a correção é subir a `version`.
- **Resultados de terceiros:** as fotos do free-exercise-db não têm direitos; nunca use. As figuras são próprias (cinemática em `Canvas`) e o mapa corporal vem do MuscleMap (MIT).

## Onde estão as coisas

| O quê | Onde |
| --- | --- |
| Regras do projeto | [AGENTS.md](../../AGENTS.md) (Claude e Codex) |
| Fases e andamento | [ROADMAP.md](ROADMAP.md) |
| Specs, planos, relatórios, releases | `docs/superpowers/{specs,plans,reports,releases}/` |
| O que foi e o que não foi verificado | [VERIFICATION.md](../../VERIFICATION.md) |
| Notas do PR final e pendências por fase | [releases/v1.5.0.md](releases/v1.5.0.md) |
| Ledgers de execução (fora do git) | `.superpowers/sdd/2026-10-31-phase5a-charts-progress/progress.md` (5A) e `.superpowers/scratch-backup/sdd-archive/` (fases anteriores) |
| Script de trava do emulador (fora do git) | `.superpowers/tools/gated-run-lock.sh` |
| Bases de teste (fora do git) | `.superpowers/scratch-backup/files/` |
| Revisor de compatibilidade | `.claude/agents/compat-reviewer.md` (rodar ao mexer em `data/`, `timer/`, `updates/`, `AppPreferences.kt` ou nos `build.gradle.kts`) |
