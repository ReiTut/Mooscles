# Fase 3: crítica do treino ativo, do resumo, da casca e do cartão (Task 31)

Data: 2026-10-29. Branch `feat/phase3-active-workout`, a partir de `53b13ba`. Spec: [fase 3](../specs/2026-10-29-phase3-active-workout-design.md) ("Capturas e crítica" e "TalkBack e aparelho físico"). Formato da [crítica da fase 2](2026-10-28-phase2-critica-configuracoes.md).

## Entrada e método

- **Aparelho:** só o `emulator-5556` (AVD `Mooscles_Pixel8_API35`, Android 15, 1080 × 2400, 420 dpi, 411 dp de largura). Nenhum aparelho físico.
- **Dados:** a base grande de desempenho que já estava no emulador (209 treinos), a rotina "Verif routine 1" (supino reto, supino inclinado, remada) e um treino anterior do supino feito nesta tarefa, para a coluna "Anterior" ter valores reais. As cargas das séries foram digitadas (60, 62,5 e 65 kg) porque os alvos da rotina de teste são valores estranhos (272,16 e 40 kg). O recorde de cada captura é de repetições a 60 kg (60 kg × 16 a 21), porque o recorde de carga do supino nessa base é 272,16 kg.
- **Capturas** em [`2026-10-29-phase3-verificacao/`](2026-10-29-phase3-verificacao/), por `adb exec-out screencap -p`:
  - `workout-{pending,editing,keyboard,record,dock,expanded,paused}-{light,dark}-{en,pt}.png` e as mesmas a 200% em pt (`-light-pt-200`, `-dark-pt-200`): 42 capturas, mais `workout-expanded-paused-*` (6);
  - `summary-*` e `share-card-*` (a prévia do cartão) nos dois temas, em en e pt e a 200% em pt (12);
  - `tabs-*` (casca com a barra do treino, sem descanso) e `workout-bar-*` (com o descanso correndo), nas mesmas seis combinações (12);
  - `finish-dialog-light-en.png`, `discard-dialog-dark-pt.png`;
  - `before-*.png` (8): o estado antes das correções, da rodada exploratória (en escuro e pt claro a 200%).
  - Tema pela preferência "Sistema" do app e `cmd uimode night`; idioma por `cmd locale set-app-locales com.mooscles`; 200% por `settings put system font_scale 2.0`. Tudo foi restaurado no fim (ver "Estado do emulador").
- **Skills:** `kit-design` (roteador), depois `critique-screen` (hierarquia, composição, tipografia, cor, affordance e densidade) sobre as capturas do treino e do resumo, e `better-interface` sobre a casca e o cartão. As skills `better-*` de domínio não foram carregadas uma a uma: os seis domínios do `better-interface` foram aplicados direto às capturas e ao código, e este relatório não declara a cobertura de regras específicas delas. Regras que prevalecem: a spec da fase 3, a spec e as notas da fase 2 ("Giz & Ferro": tokens, fontes, ícones Lucide) e o `AGENTS.md`.
- **Prioridade:** P1 (alta) quebra o uso, a acessibilidade, a spec ou o design system; P2 (média) piora a experiência ou cria inconsistência; P3 (baixa) acabamento. P1 e P2 foram corrigidos em commits próprios, com teste quando a correção é de comportamento, e capturados de novo; os que não foram têm o motivo escrito.

## P1: alta

| # | Problema | Dimensão | Princípio | Situação |
| --- | --- | --- | --- | --- |
| A1 | **As colunas da linha de série nunca apareciam no uso normal** (item 1 levado da Task 17). A 411 dp e 1,0, uma série com anterior ("60 kg × 8") já não cabia numa linha (ficavam 59 dp para o anterior, e ele pedia ~62), então toda linha de exercício com histórico ia para duas linhas e os títulos Série · Anterior · kg · Reps sumiam. Captura: `before-workout-pending-dark-en.png`. | Hierarquia, densidade | A prancha "Treino ativo" é uma tabela com cabeçalho; sem ele, os dois campos são números sem nome. A linha dupla também gasta o dobro da altura por série. | **Corrigido** em `0fb1644` (`designsystem:`) e `3f4b876` (`screens:`), seguindo a prancha: o anterior aparece sem a unidade ("60 × 15", o título da coluna diz kg ou lb, e a leitura falada mantém a unidade), e a linha fica com `space.1` antes do número e nada depois do check (o disco de 44 dp já fica 6 dp dentro do alvo de 56), o mesmo que a prancha faz. Os dois juntos dão ~34 dp ao anterior. Teste: `ExerciseListTest.columnTitlesAt412FollowTheRow` reescrito (o caso comum "82,5 × 10" agora mostra os títulos; só um anterior muito largo, "1002,75 × 100", os esconde); os testes de 320 dp e de 1,5 continuam valendo. Capturas: `workout-pending-*.png`. |
| A2 | **O texto dentro do anel do timer expandido passava por cima do traço do anel**: a tela mandava o rótulo do dock ("Descanso · Barbell bench press"), largo demais para o meio do anel a 1,0 e muito pior a 200%. Capturas: `before-workout-expanded-dark-en.png`, `before-workout-expanded-light-pt-200.png`. | Composição, tipografia | Texto sobre o traço é ilegível e quebra o anel; a prancha "Descanso e recorde" mostra só "Descanso" dentro dele (o exercício já está no título). | **Corrigido** em `fb154b6` (`designsystem:`): dentro do anel, só a fase ("Descanso", "Pausado", "Descanso concluído"), em até 70% do meio livre, quebrando em duas linhas quando maior. Teste: `RestTimerExpandedTest.ringShowsOnlyThePhaseInsideItsClearCircle` (en e pt, as três fases, 1,0 e 2,0: os quatro cantos do texto ficam dentro do círculo livre; falhou antes da correção e de novo, a 2,0 em pt com "Descanso concluído", antes do limite de largura). Capturas: `workout-expanded-*.png`. |
| A3 | **O check de uma série concluída continuava "Concluir série 1"** (item 5 levado da Task 30), e o toque duplo desmarcava. | Affordance, acessibilidade | O nome de um controle não pode oferecer a ação que ele já fez; quem ouve "Concluir série 1, marcado" e toca duas vezes desfaz a série. | **Corrigido** em `b05c931` (`designsystem:`): concluída, ela se chama "Série 1 concluída" / "Set 1 completed", e a ação do toque duplo é "Marcar série como não feita"; pendente, "Concluir série 1" com a ação "Concluir série". O papel (caixa de seleção), o estado e o háptico não mudam. Teste: `SetCheckTest.nameAndActionFollowTheState` (en e pt; falhou antes). Dump: `talkback-workout-record-dark-pt.txt`, parada 14. |

## P2: média

| # | Problema | Dimensão | Princípio | Situação |
| --- | --- | --- | --- | --- |
| M1 | **A linha de recorde do resumo quebrava no meio do valor** ("before 60 kg ×" / "11"), e o aviso de recorde podia fazer o mesmo a 200%. Captura: `before-summary-dark-en.png`. | Tipografia | Um valor ("60 kg × 11") é uma unidade de leitura; nenhum número cortado (spec, "Fonte grande"). | **Corrigido** em `42164a1` (`screens:`): as strings de recorde do resumo e do aviso juntam carga, × e repetições com espaços não separáveis, então a linha só quebra entre o exercício, a série e "antes". Os testes de texto (`WorkoutSummaryScreenTest.recordLines`, `firstRecordsSection`, `RecordReactionTest`, `ActiveWorkoutFlowTest`) fixam o texto novo. Capturas: `summary-*.png`, `workout-record-light-pt-200.png`. |
| M2 | **O cartão de compartilhamento pintava a melhor série de latão com o ícone de recorde mesmo quando ela não era o recorde**: com um recorde de repetições a 60 kg, "62,5 kg × 8" (a mais pesada, sem recorde) saía como se fosse um. Captura: `before-share-card-dark-en.png`. | Cor, hierarquia | Anti-objetivo "Recorde errado": a marca de recorde só onde o histórico diz que há recorde. | **Corrigido** em `b097cd0` (`screens:`): a linha do exercício só é marcada quando a melhor série mostrada é a série do recorde; o recorde continua na seção de recordes do cartão. Teste: `ShareCardDataTest.aRecordOnAnotherSetDoesNotMarkTheBestSet` (falhou antes); os testes do cartão no `:app` (`com.mooscles.sharing.*`) passam. Capturas: `share-card-*.png`. |
| M3 | **O diálogo de finalizar emendava as séries deixadas de fora na duração**: "… · 12 min. 7" / "incomplete sets will be left out." lia o 7 como parte do tempo. Captura: `before-finish-dialog-dark-en.png`. | Densidade, tipografia | Duas informações, duas linhas. | **Corrigido** em `a0157db` (`screens:`): as séries pendentes começam uma linha própria. `ActiveWorkoutScreenTest.finishDialogTextsAndOrder` e o caso de uma série fixam o texto com a quebra. Captura: `finish-dialog-light-en.png`. |
| M4 | **O nó de anúncio do dock (3 × 3 px) é uma parada do leitor de tela** (item 6 levado da Task 29) e lê o último anúncio, que pode estar velho ("2 minutos" com 1:20 restando). | Acessibilidade | Um nó sem lugar na tela não deve ser parada; um valor falado não pode estar errado. | **Não corrigido, com motivo.** As saídas baratas não servem: pôr a região viva no corpo do dock anunciaria a cada segundo (o rótulo do corpo tem o tempo), e um rótulo congelado no corpo leria um tempo velho. A correção certa é anunciar por uma API de plataforma (`announceForAccessibility` no Android, `UIAccessibility.post` no iOS) sem nó, o que pede `expect/actual` no `:designsystem`, fora da lista de código por plataforma do anti-objetivo da spec ("só háptico, movimento reduzido, idioma, …"). Fica para a sessão com TalkBack confirmar o efeito real e para uma decisão de spec. Registrado em `talkback.md` (candidata 1). |

## P3: baixa (não corrigidas, com o motivo)

| # | Problema | Dimensão | Motivo |
| --- | --- | --- | --- |
| L1 | **Nada indica que a linha desliza** para concluir ou remover (item 2, Task 12 M6). | Affordance | O deslize é um atalho: concluir tem o check de 56 dp, remover tem a sheet do tipo e as ações de acessibilidade (WCAG 2.5.7), e nenhum fluxo do orçamento de toques depende dele. Uma dica permanente em cada linha somaria ruído à tela mais densa do app; uma dica de primeiro uso é uma decisão de produto para quando houver onboarding. |
| L2 | **O chip "Descanso 2:00" parece um botão** (item 3, Task 17). | Affordance | Ele é um botão: abre a sheet de descanso do exercício (D14). O contorno e o ícone de cronômetro dizem que é tocável, e o texto é uma duração, não um verbo, então não se lê como "começar descanso". Mantido. |
| L3 | **Adicionar série depois da última custa 3 toques** (item 4, Task 29), porque o D9 recolhe o card concluído. | Densidade, affordance | Mantido: continua abaixo dos 4 de hoje, e pôr "Adicionar série" em todo card recolhido daria a cada cabeçalho um segundo alvo para um fluxo raro (lei de Hick, densidade). `TapBudgetTest.addSetAfterTheLastSetOpensTheCardFirst` continua fixando 3. A nota datada na spec é da Task 32. |
| L4 | **Na partida a frio, as barras do sistema seguem o modo do sistema por 1 a 2 s** até as preferências carregarem (item 7). | Cor | Só registro, como pedido: anterior à fase, no `Theme` do `:app`. |
| L5 | **O timer expandido e a prévia do cartão escurecem as barras do sistema** (cinza com ícones brancos no tema claro), porque o diálogo não desenha atrás delas. Capturas: `workout-expanded-light-*.png`, `share-card-light-*.png`. | Cor, composição | Acabamento sem efeito no uso. Desenhar atrás das barras pede a flag de janela do diálogo do Android (`decorFitsSystemWindows`), que o `DialogProperties` comum não expõe: seria código por plataforma no `:screens`. No tema escuro quase não se vê. |
| L6 | **A prévia do cartão continua em inglês com o app em pt-BR** ("Workout preview", "Choose an app"). | Texto | É a tela antiga do `:app`, deixada em inglês de propósito até ser redesenhada (comentário em `app/src/main/res/values/share_strings.xml`, como o D22). |
| L7 | **"A 1 série registrada será apagada."** no diálogo de descartar, com uma série. | Texto | Gramatical e coerente com o plural ("As 3 séries registradas…"); trocar por "A série registrada…" é polimento de redação. |
| L8 | **A barra do treino mostra dois relógios lado a lado** com o descanso correndo ("14:13 ⏱ 1:51"). Captura: `workout-bar-light-en.png`. | Hierarquia | É o que o D8 pede; o anel de 32 dp fica entre os dois e marca qual é o descanso. Se o dono achar confuso, o tempo de treino pode ir para `text.secondary`. |
| L9 | **O aviso de recorde é uma superfície invertida** (clara no tema escuro, escura no claro). | Cor | Escolha do `PrToast` da fase 2, a mesma dos toasts; contraste medido no `ComponentColorsTest`. |
| L10 | **O resumo mantém a aba de onde o treino foi aberto**: aberto de Recordes, o resumo mostra "Recordes" selecionada. | Navegação | Regra da casca (Task 24: o resumo fica com a casca e a aba de origem); "Concluir" leva a Treinar. |
| L11 | **O teclado do Gboard é claro sobre o app escuro.** | Cor | É do sistema. |

## Achados de `better-interface` (casca e cartão)

- **Casca** (`tabs-*`, `workout-bar-*`): quatro abas com rótulo em en e pt, a selecionada com a pílula `accent.subtle`; a 200% só a selecionada mostra o rótulo, sem quebra no meio da palavra (D7); a barra do treino vira duas linhas a 200% sem esconder o pular (D8); no claro, a barra e as abas seguem os tokens (#5 da fase 2). Achados: só o L8. Acessibilidade: a barra é um botão com o nome da spec e o pular é próprio (dump `talkback-shell-dark-pt.txt`).
- **Cartão** (`share-card-*`): o M2 (corrigido). O resto segue o D20: fundo grafite nos dois temas, a marca, números no formato do idioma ("3.690 kg" em pt), recordes em latão com o selo, a melhor série por exercício. A duração do cartão ("15:32") e a do resumo ("15 min") diferem de formato: decisão aceita na Task 22 (M2 dela).

## Os sete itens levados

| # | Item | Veredito |
| --- | --- | --- |
| 1 | Colunas escondidas no uso normal | P1, **corrigido** (A1): `0fb1644`, `3f4b876`. |
| 2 | Sem dica de deslize | P3, mantido (L1). |
| 3 | O chip de descanso parece botão | P3, mantido: é um botão (L2). |
| 4 | Adicionar série depois da última em 3 toques | P3, mantido (L3); o teste de orçamento não muda. |
| 5 | Rótulo do check concluído | P1, **corrigido** (A3): `b05c931`, com teste. |
| 6 | Nó de anúncio do dock como parada | P2, **não corrigido** por exigir código por plataforma fora da lista da spec (M4); pendente da sessão com TalkBack. |
| 7 | Barras do sistema na partida a frio | Registrado (L4), sem correção, como pedido. |

## Notas para a Task 32 (notas datadas na spec)

- Acessibilidade, "o check como alternável próprio ('Concluir série 4', marcado ou não)": concluído, o nome passa a "Série 4 concluída" com a ação "Marcar série como não feita" (A3).
- Linha de série / coluna "Anterior": o valor aparece sem a unidade ("80 × 6"), como na prancha; a leitura mantém "80 quilos por 6" (A1). A linha usa `space.1` antes do número e nada depois do check.
- "Expandido": dentro do anel, só a fase; o exercício fica no título (A2).
- O nó de anúncio do dock (M4) e as barras do sistema no diálogo (L5) ficam como decisões abertas.

## Avaliação geral

A dimensão mais forte é a cor: os dois temas usam só tokens, os estados (pendente, em edição, concluída, recorde, pausado) se distinguem também por forma e texto, e as correções não criaram par novo de texto e fundo. A mais fraca era a densidade da linha de série: a 411 dp, a tabela que a prancha desenha virava duas linhas por série sem cabeçalho, o que dobrava a rolagem e deixava os dois campos sem nome; com o anterior sem unidade e o recuo da prancha, a tela agora é a tabela do desenho. As outras correções eram de detalhe com custo real (um texto sobre o anel, um valor partido, uma marca de recorde enganosa, um nome de check que oferecia a ação errada).

## Verificação

Depois do último commit de correção (`b097cd0`), no `emulator-5556`:

- `:designsystem:connectedAndroidDeviceTest` inteiro: 291/291.
- `:screens:connectedAndroidDeviceTest` inteiro: 288/288.
- Testes de host do `:app`, `:shared`, `:designsystem` e `:screens`: 806, nenhuma falha.
- `:screens:compileTestKotlinIosSimulatorArm64` e `:designsystem:compileTestKotlinIosSimulatorArm64`: compilam. Os testes iOS só rodam no CI.
- `tools/check-design-tokens.ps1`: limpo (92 arquivos).
- Os testes novos falharam antes das correções (`SetCheckTest.nameAndActionFollowTheState`, `RestTimerExpandedTest.ringShowsOnlyThePhaseInsideItsClearCircle`, `ShareCardDataTest.aRecordOnAnotherSetDoesNotMarkTheBestSet`).
- **Não verificado:** TalkBack, háptico e 60 fps num aparelho (ver `talkback.md`); nada em iPhone ou no simulador iOS nesta tarefa.

## Estado do emulador

Restaurado no fim: escala de fonte 1,0, modo noturno do sistema desligado, idioma do app sem escolha própria (segue o sistema), e no app tema Escuro, Libras e descanso automático desligado (o que estava antes), sem treino ativo. Ficaram: dois treinos finalizados e dois descartados a mais no histórico do emulador, e os dados do Gboard apagados (`pm clear`): o Gboard do emulador passa para o modo "teclado físico" depois de um `input keyevent` e para de desenhar o teclado, e só volta limpando os dados.

## Nota posterior

- **2026-10-29, M4 corrigido em `2ff31b4`.** Depois deste relatório, o controlador decidiu o M4 sem código por plataforma: o nó de anúncio do timer só tem texto durante um anúncio e é limpo depois de `timing.timerAnnounceHold` (1.500 ms, token novo com teste). Entre anúncios ele não tem texto, descrição nem foco, então não é uma parada do leitor de tela e não lê um tempo velho. Testes: `RestTimerDockTest.announcerIsNotAStopBetweenAnnouncements` e `announcerClearsAfterAnnouncing`. Conferido só pela semântica; o efeito com o TalkBack real continua para a sessão no aparelho. A tabela acima ficou como estava na hora da crítica.
