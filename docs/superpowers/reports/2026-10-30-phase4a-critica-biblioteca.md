# Fase 4A: crítica da biblioteca, do detalhe, do formulário e do seletor (Task 23)

Data: 2026-10-30. Branch `feat/phase4a-library`, a partir de `8d487a9`. Spec: [fase 4](../specs/2026-10-29-phase4-library-routines-design.md). Plano: [fase 4A](../plans/2026-10-29-phase4a-library.md), Task 23. Formato da [crítica da fase 3](2026-10-29-phase3-critica-treino.md).

## Entrada e método

- **Aparelho:** só o `emulator-5556` (AVD `Mooscles_Pixel8_API35`, Android 15, 1080 × 2400, 420 dpi, 411 dp de largura). Nenhum aparelho físico.
- **Build:** o APK debug da branch, instalado limpo (`adb uninstall` e `adb install`) em cima do build R8 de desempenho que a Task 22 tinha deixado, e reinstalado por cima (`install -r`) depois de cada rodada de correções.
- **Dados:** um treino de supino feito nesta tarefa (60 kg × 8, que vira o recorde e o "Usados recentemente"), e um exercício personalizado criado pelo formulário ("Cable crossover high to low", Peito, Polia), que é o detalhe sem figura.
- **Capturas** em [`2026-10-29-phase4a-verificacao/`](2026-10-29-phase4a-verificacao/), por `adb exec-out screencap -p`, PNG original (o `pngquant` não está instalado nesta máquina):
  - biblioteca: `library-{all,filtered,empty}-…` (todos, filtro "Costas", busca sem resultado com "Criar");
  - detalhe: `detail-figure-…` (supino, topo), `detail-figure-history-…` (dicas e histórico) e `detail-nofigure-…` (o personalizado);
  - formulário: `form-…` (vazio) e `form-errors-…` (Salvar recusado);
  - seletor do treino: `picker-…`;
  - cada uma nas seis combinações `-light-en`, `-dark-en`, `-light-pt`, `-dark-pt`, `-light-pt-200` e `-dark-pt-200` (60 capturas);
  - o formulário a 320 dp: `form-320-light-en`, `form-errors-320-light-en`, `form-320-dark-pt-200`, `form-errors-320-dark-pt-200`;
  - `before-*.png` (5): o estado antes das correções, da rodada exploratória;
  - `tab-after-recreation-dark-pt-200.png`, `licenses-cc0-light-en.png`, `search-{chin-up,elevacao-lateral,barra-ez}-light-en.png` e `form-save-to-detail-frames.png` (quadros de 50 ms do Salvar até o detalhe).
  - Tema pela preferência "Sistema" do app e `cmd uimode night`; idioma por `cmd locale set-app-locales com.mooscles`; 200% por `settings put system font_scale 2.0`; 320 dp por `wm density 540`. Tudo restaurado no fim (ver "Estado do emulador").
  - O que não é citado (rodada exploratória, vídeo do Salvar) ficou em `local/task23/`, fora do git.
- **iOS:** as capturas do `LibraryFlowTest` saem como artefato do job `ios`, mas nenhum run do CI tem esse teste ainda: ele entrou em `545c933`, depois do último push (checkpoint 2, `5ed5fa7`, run 41014962533). Ficam para o push da Task 24.
- **Skills:** `kit-design` (roteador, workflow A), depois `critique-screen` (hierarquia, composição, tipografia, cor, affordance e densidade) sobre as capturas da biblioteca, do detalhe e do formulário, e a mesma leitura no seletor. Regras que prevalecem: a spec da fase 4, as decisões do plano e do controlador (no ledger da fase), a spec e as notas da fase 2 ("Giz & Ferro": tokens, fontes, ícones Lucide) e o `AGENTS.md`.
- **Prioridade:** P1 (alta) quebra o uso, a acessibilidade, a spec ou o design system; P2 (média) piora a experiência ou cria inconsistência; P3 (baixa) acabamento. P1 e P2 foram corrigidos em commits próprios, com teste, e capturados de novo; os que não foram têm o motivo escrito.

## P1: alta

| # | Problema | Dimensão | Princípio | Situação |
| --- | --- | --- | --- | --- |
| A1 | **Depois de uma recriação, a aba selecionada voltava para Treinar** numa tela acima da raiz de outra aba: no detalhe aberto pela Biblioteca, trocar o idioma, o tema, o tamanho do texto ou a densidade deixava "Treinar" selecionada embaixo do detalhe. Captura: `before-tab-recreation-dark-pt-200.png`. Vale também para o detalhe de treino do Histórico (fase 3). | Hierarquia, navegação | A aba selecionada diz onde a pessoa está; marcar outra aba engana, e tocar nela leva para outro lugar. | **Corrigido** em `a39400e` (`app:`). No primeiro quadro depois da recriação, o back stack ainda não tem entrada, e a rota de reserva (`routines`) sobrescrevia a aba restaurada pelo `rememberSaveable`. Agora só uma entrada real atualiza a aba. Teste: `ShellNavigationHostTest.aRecreationAboveATabsRootKeepsThatTabSelected` (Biblioteca → Configurações e Histórico → detalhe do treino, recriação, a aba continua; falhou antes). Captura: `tab-after-recreation-dark-pt-200.png`. |

## P2: média

| # | Problema | Dimensão | Princípio | Situação |
| --- | --- | --- | --- | --- |
| M1 | **Os títulos das seções do detalhe ficavam a 32 dp**, enquanto o nome, os chips, o mapa e os cards ficam a 16 dp: o `SectionHeader` do design system tem os próprios 16 dp de lado, dentro de uma coluna que já tem os seus. Captura: `before-detail-headings-light-en.png`. | Composição | Uma margem só por coluna; o título de seção alinha com o conteúdo que ele nomeia (proximidade e alinhamento). | **Corrigido** em `5e9912b` (`screens:`): um título próprio do detalhe, com o mesmo estilo e o mesmo espaço vertical do `SectionHeader`, sem os lados. A lista da biblioteca e o seletor continuam com o `SectionHeader`, onde as linhas ocupam a largura toda. Teste: `ExerciseInfoScreenTest.sectionTitlesLineUpWithTheContent` (falhou antes: "Músculos starts at 32 dp, the content at 16 dp"). Capturas: `detail-figure-*.png`, `detail-figure-history-*.png`. |
| M2 | **O "Salvar" do formulário ficava a 8 dp da borda**, e o conteúdo da página a 16 dp: o botão cheio, o elemento mais forte da tela, saía da margem. Captura: `before-form-light-en.png`. | Composição | A ação principal alinha com a margem do conteúdo. | **Corrigido** em `9a6bce6` (`screens:`): a linha de cima tem 8 dp no início (o "Cancelar", botão de texto, mantém o recuo próprio) e 16 dp no fim. Teste: `CustomExerciseFormTest.saveEndsWhereTheContentEnds`, a 400 e a 320 dp (falhou antes: 403,4 contra 395,4 dp). Capturas: `form-*.png`, `form-320-*.png`. |
| M3 | **O seletor do treino buscava com outro campo**: um `MTextField` com rótulo em cima, sem o ícone de busca e sem o botão de limpar, enquanto a spec dá a ele "a mesma busca … da biblioteca". Quem digitou tinha de apagar letra por letra. Captura: `before-picker-light-en.png`. | Affordance, consistência | Mesma tarefa, mesmo controle (lei da similaridade); limpar uma busca é um toque. | **Corrigido** em `f915092` (`screens:`): o `MSearchField` da biblioteca, com "Buscar exercício" como placeholder e nome, e "Limpar busca". Teste: `ExercisePickerSheetTest.searchIsTheLibrarysFieldWithClear` (falhou antes); os testes que já buscavam no seletor (`ExercisePickerSheetTest`, `ActiveWorkoutFlowTest`, `ActiveWorkoutScreenTest`, `TapBudgetTest`, a varredura de acessibilidade) passam. Capturas: `picker-*.png`. |
| M4 | **O texto da CC0 mostrava as listas i–vii e a–d como um parágrafo só** (item levado da Task 6). Captura: `before-licenses-cc0-light-en.png`. | Tipografia | Uma lista numerada é lida item por item; num bloco corrido, "b." some no meio da linha. | **Corrigido** em `7b287a6` (`designsystem:`): no `licenseBlocks`, uma linha que começa com marcador de item ("iv.", "b.") começa uma linha nova dentro do parágrafo; as palavras não mudam. Só a CC0 tem essas linhas (conferido nos cinco arquivos). Teste: `LicenseTextTest.listItemsStartTheirOwnLines` (com "i.e." e "a. b. c." no meio da linha, que não quebram; falhou antes); `LicenseFilesTest` e `LicensesScreenTest` passam. Captura: `licenses-cc0-light-en.png`. |
| M5 | **A biblioteca aparece por uns 250 ms entre o formulário fechar e o detalhe abrir** (item levado da Task 16). Nos quadros de 50 ms (`form-save-to-detail-frames.png`): o formulário some em 2 quadros, a biblioteca fica inteira em 4 e o detalhe entra com o fade da navegação. | Composição, movimento | Uma transição deve ir de onde a pessoa estava para onde ela vai, sem passar por uma terceira tela. | **Não corrigido, com motivo.** A causa é a decisão 13 como aprovada: o Salvar fecha o formulário na hora, e o detalhe só abre quando o estado traz o exercício. Manter o formulário até o detalhe abrir é a mudança que o controlador já decidiu para a 4B ("o formulário fica aberto até salvar", junto com a foto), e o `LibraryScreenTest.creatingOpensTheNewExerciseWhenItArrives` fixa o comportamento atual ("The form closes at once"). Mudar aqui seria reverter uma decisão em silêncio. Fica para a 4B, que resolve as duas coisas juntas. |
| M6 | **Com o tema claro, o formulário mostra as barras do sistema em cinza** (a janela do diálogo não desenha atrás delas, e o escurecimento da janela de baixo aparece em volta). Capturas: `form-light-*.png`. No escuro quase não se vê. | Cor, composição | Uma página inteira deve parecer uma página, não um diálogo com moldura. | **Não corrigido, com motivo.** O `DialogProperties` comum só expõe três parâmetros; desenhar atrás das barras pede `decorFitsSystemWindows` e o controle do escurecimento, que são da janela do Android: seria um `expect/actual` novo no `MOverlayWindow`, que o escopo desta tarefa não permite. É o mesmo L5 da fase 3 (timer expandido e prévia do cartão), mais visível porque aqui a página cobre a tela toda. Decisão para o controlador ou para a fase em que as janelas do design system forem revistas. **O teclado e as margens funcionam:** o campo de nome sobe acima do teclado, a página rola até o fim sem nada coberto pela barra de navegação, e o erro recusado traz o primeiro problema para a vista (capturas `form-errors-*`, também a 320 dp). |

## P3: baixa (não corrigidas, com o motivo)

| # | Problema | Dimensão | Motivo |
| --- | --- | --- | --- |
| L1 | **A 200%, o placeholder da busca da biblioteca quebra em três linhas** e o campo fixo ocupa um quarto da tela (`library-all-*-pt-200.png`). | Densidade | Decisão do `MSearchField` (KDoc: "um placeholder longo a 200% quebra em vez de ser cortado"): o placeholder é o único rótulo visível do campo. Com texto digitado ele volta a uma linha, e os filtros e a lista rolam juntos. |
| L2 | **O secundário do mapa (`border.strong`) engole os contornos internos da região** (item levado da Task 1): o deltoide e o tríceps viram uma mancha cinza. Capturas: `detail-figure-light-*.png`. | Cor | A região inteira continua legível como "secundária", a legenda e o texto ("Secundários: tríceps, ombros.") dizem o mesmo, e os pares de contraste estão no `ComponentColorsTest`. Mostrar os contornos pede um traço próprio para a região secundária (um token novo, com teste de contraste), uma mudança do design system que vale decidir junto com a resposta do dono no portão G1. |
| L3 | **A legenda mostra "Secundários" num exercício sem secundários** (o personalizado: `detail-nofigure-*.png`). | Densidade | A legenda explica as duas cores do mapa, que são fixas; o texto embaixo diz só "Principais: peito.". Esconder um item da legenda por exercício é polimento. |
| L4 | **O quadro sem figura do personalizado é um quadrado grande, quase vazio**, e empurra o mapa para baixo da dobra. | Densidade | É o que a spec pede (D5, "Sem figura": `ExerciseMedia.None` com o ícone do equipamento) e é o lugar da foto do personalizado na 4B ("a foto no lugar da figura, recortada em 1:1"). |
| L5 | **A 200%, o rádio da convenção fica no meio de um bloco de sete linhas**, longe do título da opção (`form-*-pt-200`, rolado). | Affordance | É o `RadioRow` do design system, o mesmo das Configurações; alinhar ao topo muda as duas telas e pede a crítica das Configurações junto. A linha inteira é o alvo do toque. |
| L6 | **A busca por trecho dá falsos positivos** (Task 3, M1): "chin up" traz 15 (máquinas com "machine" ⊃ "chin"), "elevacao lateral" traz a elevação frontal unilateral ("unilateral" ⊃ "lateral"), "barra ez" traz o encolhimento com barra. Capturas: `search-*-light-en.png`. | Densidade | Nos três casos o exercício procurado está na primeira tela (Chin-up é o 3º de 15). O casamento por trecho é do `ExerciseSearchIndex` no `:shared` (`data/`, fora do escopo desta tarefa) e é o que garante o superconjunto da busca antiga (D13); a ordem alfabética é da spec. Se incomodar, o barato é ordenar primeiro os acertos de palavra inteira ou de início de palavra, uma mudança de spec. |
| L7 | **O formulário abre e fecha com o fade da janela do sistema** (item levado da Task 16; `before-form`, quadros em `form-save-to-detail-frames.png`). | Movimento | Curto (≈ 100 a 150 ms), segue a escala de animação do sistema (e o "remover animações"), e é o mesmo de todo `MOverlayWindow`. Sem achado. |
| L8 | **`selectorized_calf_raise_seated` pode nomear máquinas diferentes**: em inglês "Selectorized seated calf press" (pés numa plataforma à frente), em pt-BR "Panturrilha sentado na máquina" (a de apoio nos joelhos) (item levado da Task 13). | Texto | Os dois nomes são dados do catálogo no `:shared` (o inglês já está em aparelhos instalados), fora do escopo desta tarefa. As dicas foram escritas para servir às duas máquinas. Vai para a revisão de nomes pt-BR do dono (nota para a Task 24). |
| L9 | **O seletor diz "Recentes"/"Recent" e a biblioteca "Usados recentemente"/"Recently used"** para a mesma lista. | Texto | Polimento de redação; mudar uma das duas mexe em strings e testes de duas telas sem ganho de uso. |
| L10 | **O "Ver histórico completo" tem o texto a 32 dp** (o recuo do botão de texto). | Composição | É o botão de texto do design system, com o mesmo recuo do "Cancelar" do formulário; a área de toque começa a 16 dp. |
| L11 | **A busca sem resultado mostra "0 exercícios" acima de "Nenhum exercício encontrado"** (`library-empty-*`). | Densidade | A contagem é a região viva que anuncia o resultado a cada busca; escondê-la no zero tiraria o anúncio. |
| L12 | **Os médios da folha piloto da Task 9**: o agachamento a 64 dp continua o mais carregado, o desenvolvimento e o desenvolvimento sentado põem as mãos na frente do rosto no quadro A, os braços da elevação de quadril fazem um "V", e cabeça, braço e rolo se juntam na mesa flexora. | Cor, composição | Mantidos: a folha está com o dono no portão G1, que decide as figuras. A lista e o seletor mostram só o quadro B, onde o rosto não fica coberto. Nas capturas desta tarefa as miniaturas da lista leem bem a 64 dp. |
| L13 | **A tela Treinar (antiga) fica em inglês com o app em pt-BR** ("Resume workout"). | Texto | Tela ainda não redesenhada; entra na 4B. |

## Itens levados

| Item | Origem | Veredito |
| --- | --- | --- |
| Secundário em `border.strong` some com o contorno dentro da região | Task 1 | P3 (L2), mantido; decisão de design system junto com o G1. |
| Texto da CC0 i–vii / a–d num parágrafo só | Task 6 | P2 (M4), **corrigido** em `7b287a6`. |
| Barra de status, margens e teclado da janela do formulário | Task 16 | Barras: P2 (M6), não corrigido (pede `expect/actual`). Margens e teclado: sem achado. |
| A biblioteca aparece entre fechar o formulário e abrir o detalhe | Task 16 | P2 (M5), não corrigido: decisão 13 e a decisão do controlador para a 4B. |
| Animação de abrir e fechar o formulário | Task 16 | P3 (L7), sem achado. |
| Nomes en/pt de `selectorized_calf_raise_seated` | Task 13 | P3 (L8), para a revisão de nomes (fora do escopo: `:shared`). |
| Falsos positivos da busca por trecho | Task 3 (M1) | P3 (L6), o resultado procurado fica na primeira tela; ordenação por acerto de palavra como sugestão. |
| Médios da folha piloto | Task 9 | P3 (L12), mantidos até a resposta do G1. |

## Notas para a Task 24 (notas datadas na spec e no plano)

- **Seletor:** a busca é o `MSearchField` da biblioteca ("Buscar exercício" como placeholder e nome, com "Limpar busca"), como a spec já pedia ("a mesma busca").
- **Detalhe:** os títulos de seção ("Músculos", "Como fazer", "Seu histórico") alinham com o conteúdo, a 16 dp.
- **Formulário:** o "Salvar" termina a 16 dp da borda, como o conteúdo.
- **Licenças:** os itens de lista da CC0 ficam um por linha.
- **Casca:** a aba selecionada sobrevive à recriação numa tela acima da raiz (`a39400e`, no `:app`).
- **4B:** manter o formulário aberto até o detalhe abrir (M5) e decidir as barras do sistema das janelas cheias (M6, também o L5 da fase 3).
- **Nomes:** `selectorized_calf_raise_seated` en/pt (L8).

## Avaliação geral

A dimensão mais forte é a cor: os dois temas usam só tokens, o filtro ativo se diz pelo check e pelo texto além da cor, os erros do formulário têm ícone, e o mapa tem legenda e texto equivalente. A mais fraca era a composição: duas margens diferentes no detalhe e na linha de cima do formulário, e um campo de busca diferente para a mesma tarefa no seletor. O achado mais grave estava fora das telas novas: a casca perdia a aba numa recriação, o que qualquer troca de idioma, tema ou tamanho do texto no detalhe mostrava. A 200% e a 320 dp nada fica cortado: os nomes quebram em até três linhas, os chips quebram em linhas novas, e o formulário rola até o fim com o teclado aberto.

## Verificação

No `emulator-5556`, depois das correções:

- `:screens:connectedAndroidDeviceTest`, por classe: `ExerciseInfoScreenTest`, `CustomExerciseFormTest`, `LibraryAccessibilitySweepTest` e `LibraryFlowTest` 56/56 (depois de M1 e M2); `ExercisePickerSheetTest`, `ActiveWorkoutFlowTest`, `ActiveWorkoutScreenTest` e `LibraryAccessibilitySweepTest` 64/64 e `TapBudgetTest` 13/13 (depois de M3).
- `:designsystem:connectedAndroidDeviceTest`: `LicensesScreenTest` e `LicenseTextTest` 7/7.
- Host: `:app` `ShellNavigationHostTest` 20/20, `LibraryNavigationHostTest` 8/8, `MainActivityOpenWorkoutHostTest` 4/4, `DiscardNavigationHostTest` 3/3, `GalleryUnlockHostTest` 3/3, `UpdateNotificationRecreationHostTest` 1/1, `SystemBarsThemeHostTest` 1/1; `:designsystem` `LicenseTextTest` 6/6, `LicenseFilesTest` 2/2.
- Os cinco testes novos falharam antes das correções, pelo motivo certo.
- `:screens:compileTestKotlinIosSimulatorArm64` e `:designsystem:compileTestKotlinIosSimulatorArm64`: compilam. Os testes iOS só rodam no CI.
- `tools/check-design-tokens.ps1`: limpo (123 arquivos).
- Mínimos do CI: `MIN_HOST_TESTS` 926 → 928, `MIN_DS_IOS_TESTS` 339 → 340, `MIN_SCREENS_IOS_TESTS` 427 → 430; `MIN_IOS_TESTS` (shared) continua 257.
- **Não verificado:** TalkBack e aparelho físico; nada em iPhone ou no simulador iOS nesta tarefa (as capturas do `LibraryFlowTest` esperam o próximo push); as suítes inteiras de aparelho e de host (o ciclo rápido roda só as classes tocadas; a suíte inteira roda no CI).

## Estado do emulador

Restaurado no fim: escala de fonte 1,0, modo noturno do sistema desligado, idioma do app sem escolha própria (`[]`), densidade 420 (`wm density reset`), rotação como estava. A rede continuou como encontrei (Wi-Fi e dados desligados, deixados assim pela Task 22). Ficaram: o APK debug da branch no lugar do build R8 de desempenho, um treino de supino finalizado e um descartado, e o exercício "Cable crossover high to low".
