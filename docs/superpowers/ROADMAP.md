# Roadmap do Mooscles

> **Phase 5B release.** The owner authorized an Android-only full release for [1.5.4 / Build 26](releases/v1.5.4.md). Shared History and overall progress, saved detail and editing, and sharing preview are implemented. The approved phone runs meet the scrolling target after the scoped muscle-map fix. Offline compatibility and the standalone index check pass. The live unit-switch goal and remaining visible-frame measurements stay open. See the [plan](plans/2026-11-04-phase5b-history.md), [verification](reports/2026-11-05-phase5b-verificacao.md) and [performance results](reports/2026-11-05-phase5b-performance.md). Phase 6/7 remain outside the release scope.

> **2026-11-04 follow-up:** [v1.5.3 / Build 25](releases/v1.5.3.md) combines the startup beta and workout PR statistic as a full release. This is a localized follow-up, not progress on Phase 5/5B; those phases remain paused.

Atualizado em 2026-10-29. Este é o mapa de onde o produto vai. Cada fase vira uma ou mais specs (`specs/`), planos (`plans/`) e releases (`releases/`), seguindo o fluxo do [AGENTS.md](../../AGENTS.md). Quando uma fase termina, marque-a aqui e ligue os documentos que a entregaram.

> **Para continuar o desenvolvimento:** comece pelo [HANDOFF.md](HANDOFF.md), com o estado atual, a próxima etapa (5B) e como trabalhar nesta máquina. Desde 2026-11-01 (release 1.5.0), o desenvolvimento segue direto neste repositório (`ReiTut/Mooscles`).

## Aonde queremos chegar

Um diário de treino para Android e iOS que seja o mais prático de usar no meio da série e o mais bonito de abrir no dia seguinte:

- **Prático:** registrar uma série leva um ou dois toques, com uma mão, com a mão suada e o celular no banco.
- **Visual:** cada exercício tem imagem e animação de execução, o progresso aparece em gráficos e as conquistas (PR, sequência de treinos) têm uma reação visível.
- **Moderno:** movimento suave e com propósito, identidade visual própria e consistente em todas as telas.
- **Privado como hoje:** sem conta, sem servidor, sem analytics. O backup é feito na nuvem do próprio usuário.

### Princípios de UX que valem para todas as fases

1. **O treino ativo é sagrado.** Nenhuma animação, carregamento ou mídia pode atrasar o registro de uma série ou o timer. Se precisar escolher, a velocidade ganha.
2. **Feito para a academia.** Alvos de toque grandes (mínimo de 48 dp, e maiores nas ações do treino), contraste alto, números legíveis a um braço de distância e feedback tátil nas ações principais.
3. **Movimento explica, não enfeita.** Toda animação mostra de onde algo veio, o que mudou ou que algo foi concluído. O app respeita a opção de reduzir movimento do sistema.
4. **Nunca perder dado.** Isso vale para as regras de compatibilidade do AGENTS.md, para o export e o backup, e para estados de rascunho que sobrevivem quando o app é fechado.
5. **Funciona offline.** As mídias ficam em cache local, e nenhuma tela depende de rede para funcionar.

### Anti-objetivos

O projeto falhou, mesmo que "funcione", se acontecer alguma destas coisas:

- o app passar a exigir conta;
- o registro de séries ficar mais lento que hoje;
- o APK ou app iOS inchar com centenas de MB de vídeo embutido;
- a UI do iOS virar um segundo design que diverge do Android.

## Onde estamos (diagnóstico de 2026-10-27)

- **Arquitetura e dados:** sólidos. Fluxo unidirecional, migrações aditivas testadas contra bancos reais e 363 testes de host. Detalhes no [relatório de arquitetura](reports/2026-10-27-arquitetura-android-ios.md).
- **Riscos abertos:**
  - não existe export nem backup (`allowBackup=false`);
  - travamento na abertura por causa do cálculo de PR na thread principal (resolvido na branch `fix/pr-off-main-thread`, ainda sem release);
  - a chave de assinatura só existe numa máquina;
  - ~~os scripts rodam só no Windows~~ e ~~não há CI~~: resolvidos em 2026-10-27 (ver a fase 0).
- **Design system:** inexistente.
  - O tema é apenas um esquema de cores Material 3 (verde e laranja) com a fonte padrão do sistema.
  - Não há escala de espaçamento nem de formas: são 233 medidas soltas em `dp` com 35 valores diferentes e 9 raios de borda distintos.
  - `ui/Common.kt` tem só 10 componentes compartilhados.
  - **2026-10-28:** com merge na `main` do fork depois do CI verde (host, iOS e release), a fase 2 entregou o design system "Giz & Ferro" no módulo `:designsystem` (Compose Multiplatform, Android e iOS): tokens de cor, tipo, espaço, raio, elevação, movimento e háptico; 28 arquivos de componentes; Inter e Barlow Condensed com algarismos tabulares; 38 ícones; a galeria escondida; e Configurações refeita só com ele. As outras telas continuam no Material 3 antigo, com as mesmas cores e textos, até a sua fase. Ver a [verificação](reports/2026-10-28-phase2-verificacao.md).
- **Movimento e mídia:** nenhuma animação, nenhum gráfico, uma única imagem na UI, e os 287 exercícios do catálogo não têm mídia.
- **Textos:** 144 textos fixos no código, sem recursos de string nem tradução. A UI está toda em inglês. **2026-10-28:** os textos dos componentes e de Configurações estão em recursos, em inglês e pt-BR; as outras telas migram quando forem redesenhadas. **2026-10-29:** o treino ativo, o resumo de fim, o cartão de compartilhamento, a casca (abas e barra do treino) e as notificações do descanso também estão em inglês e pt-BR; os nomes do catálogo continuam em inglês até a fase 4. **2026-10-30:** a biblioteca, o detalhe, o formulário e o seletor também estão em inglês e pt-BR, e os 287 nomes do catálogo têm tradução (a notificação do descanso automático ainda mostra o nome gravado, ver a verificação da 4A).

## Decisões tomadas (chamadas minhas, revisáveis)

- **Caminho multiplataforma:** Kotlin Multiplatform com Compose Multiplatform, uma UI só para Android e iOS, em fases, como recomendado no relatório de arquitetura.
- **O redesign nasce no código compartilhado.** A base multiplataforma vem antes do design system, para que tokens, componentes e telas novas sejam escritos uma vez só em `commonMain`. As telas atuais ficam no app Android e são substituídas uma a uma pelas novas. Nada de mover para o código comum uma tela que vai ser jogada fora.
- **Design system próprio sobre Material 3.** Usamos os componentes e a acessibilidade do Material 3, com tokens, tipografia, formas e movimento próprios. A identidade é do Mooscles, não do Material padrão.
- **Bilíngue: pt-BR e inglês.** Os textos vão para recursos durante o design system. O pt-BR passa a ser o idioma completo, e o inglês continua disponível.
- **Mídia de exercícios fora do pacote do app:** thumbnails leves embutidos e animações ou vídeos baixados sob demanda, com cache offline. A fonte e a licença da mídia são decididas por pesquisa (P2) antes da fase 4.
- **Distribuição:** o Android continua no GitHub Releases com o autoatualizador, sem Google Play por enquanto (decisão do dono, 2026-10-27). O iOS vai para TestFlight e App Store.
- **Gratuito:** o app é e continua gratuito (decisão do dono, 2026-10-27). A pesquisa P2 só considera mídia com licença que permita uso gratuito e redistribuição, sem custo recorrente.
- **Backup:** primeiro export/import em arquivo (fase 0). Depois, Google Drive (`appDataFolder`) nos dois sistemas e iCloud no iOS (fase 8). Servidor de sincronização só com demanda real.

## Pesquisas previstas

Cada uma gera `research/` + `reports/` antes da spec da fase que depende dela.

| # | Pergunta | Necessária antes de |
| --- | --- | --- |
| P1 | Referências de UX e identidade em apps de treino (Hevy, Strong, Fitbod, Ladder, Apple Fitness, Gymshark): padrões de registro rápido, timer, PR, progresso; o que copiar e o que evitar | Fase 2 **Feita em 2026-10-27:** [relatório](reports/2026-10-27-referencias-ux-apps-treino.md) (parcial: faltam avaliações reais e detalhes finos; coletar capturas antes da spec da fase 3) |
| P2 | Fonte de mídia dos exercícios: bancos abertos ou licenciáveis de imagens, GIFs e vídeos, licença comercial e de redistribuição, formato e tamanho (WebP animado, MP4 curto, Lottie), hospedagem gratuita para download sob demanda | Fase 4 **Feita em 2026-10-27:** [relatório](reports/2026-10-27-midia-exercicios.md) |
| P3 | Bibliotecas multiplataforma para gráficos, animações (Lottie no Compose Multiplatform), carregamento de imagens com cache e players de vídeo | Fases 2 e 4 **Feita em 2026-10-27:** [relatório](reports/2026-10-27-bibliotecas-cmp-graficos-animacao-midia.md) (5 spikes antes da spec) |
| P4 | Fontes e ícones com licença livre (OFL) que funcionem bem com números grandes e tabulares | Fase 2 **Feita em 2026-10-27:** [relatório](reports/2026-10-27-fontes-icones-livres.md) (confirmar `tnum` no iOS antes de escolher a fonte dos números) |
| P5 | Figuras de exercício e mapa muscular: bibliotecas, acervos, poses e IA | Fase 4 **Feita em 2026-10-29:** [relatório](reports/2026-10-29-figuras-mapa-muscular.md) |

## Fases

As durações são estimativas para um desenvolvedor sozinho e servem para ordenar o trabalho, não como prazo. Toda fase termina com uma release Android verificada, sem regressão e com `releases/vX.Y.Z.md`.

### Fase 0 — Proteger os dados e o projeto · v1.5 · 2–4 semanas

**Objetivo:** resolver os riscos que existem hoje, independentemente do iOS e do redesign.

**Entregas:**

- Export/import de todos os dados em JSON com versão, pelo seletor de arquivos do sistema ([spec](specs/2026-10-27-export-import-design.md)). **Concluído (2026-10-27)** na branch `feat/export-import`, ainda sem release: [plano](plans/2026-10-27-export-import.md), [verificação no emulador](reports/2026-10-27-export-import-verificacao.md). Com a base grande, o import dispara o travamento já conhecido do cálculo de PR na thread principal, e um toque durante o travamento abre o diálogo de ANR. Decisão de 2026-10-27: isso não é corrigido nesta branch, e sim pelo item abaixo. O relatório traz os números. **Resolvido** pelo item abaixo (ver a [verificação](reports/2026-10-27-pr-off-main-thread-verificacao.md)).
- Cópia de segurança da chave de assinatura, guardada fora da máquina, no 1Password. **Pendente (2026-10-27):** a chave das releases está com o dono do repositório original (`ReiTut/Mooscles`), não no Mac deste fork. O trabalho segue em branches e o assunto é resolvido na release final, quando o PR for aberto para o repositório original. Até lá, builds feitos aqui servem só para emulador e testes.
- Cálculo de PR fora da thread principal, para acabar com o travamento na abertura e depois de um import. **Concluído (2026-10-27)** na branch `fix/pr-off-main-thread`, ainda sem release: [spec](specs/2026-10-27-pr-off-main-thread-design.md), [plano](plans/2026-10-27-pr-off-main-thread.md), [verificação no emulador](reports/2026-10-27-pr-off-main-thread-verificacao.md). Os recordes do histórico saem de uma passada cronológica só, calculados a partir das séries salvas (nada fica gravado no banco), e a apresentação roda numa thread de fundo. Com a base grande, o APK debug não trava nem dá ANR na abertura, no import, na troca kg/lb e ao finalizar um treino. **Meta não cumprida:** na abertura a frio do debug, o histórico aparece 15 a 20 s depois do primeiro quadro (a spec pede 3 s); a tela mostra o carregamento e continua respondendo. No build R8 foram 2,3 e 3,1 s. A branch fecha assim, pela [nota datada de 2026-10-27 na spec](specs/2026-10-27-pr-off-main-thread-design.md#notas-de-implementação). Acompanhamentos:
  - medir no aparelho o caminho a frio do build debuggable, com seções de `Trace` em `repo.initialize()` e `snapshot()`, para testar a hipótese de que a espera de ~8–9 s antes do `present()` vem da serialização das transações do Room;
  - distribuir um build não debuggable assinado com a chave original (depende do item da chave acima);
  - trocar só a unidade (kg/lb) refaz as passadas de recordes e de histórico, que não dependem dela; dá para reaproveitar esses resultados.
- Build e release rodando no macOS. **Concluído (2026-10-27)**: [plano](plans/2026-10-27-macos-build-ci.md), [verificação](reports/2026-10-27-macos-build-ci-verificacao.md). Os scripts PowerShell rodam no `pwsh` 7 do macOS; o `prepare-release.ps1` também passou a ler a saída do apksigner das build-tools 37 ou mais novas e falha fechado diante de uma linha de assinante que não conhece. Não verificado: Windows real e a publicação de release (ver o relatório).
- CI no GitHub Actions com testes de host e lint. **Concluído (2026-10-27)**: [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml), [plano](plans/2026-10-27-macos-build-ci.md), [verificação](reports/2026-10-27-macos-build-ci-verificacao.md), primeira execução verde: [run 40585511052](https://github.com/pedroyuuki/Mooscles/actions/runs/40585511052), com o contorno que limitava o script de release às build-tools 35; primeira verde com a correção do parser do apksigner: [run 40586360013](https://github.com/pedroyuuki/Mooscles/actions/runs/40586360013) (482 testes, host em 4m43s); primeira verde com o parser que falha fechado e os casos de dois assinantes, rotação de chave e Windows PowerShell 5.1: [run 40591041005](https://github.com/pedroyuuki/Mooscles/actions/runs/40591041005) (tentativa 2; a tentativa 1 caiu num teste instável, ver as [pendências da v1.5.0](releases/v1.5.0.md#pendências-conhecidas-que-não-bloqueiam-o-pr)).

**Pronto quando:**

- um export feito num aparelho é importado em outro sem diferença nos dados;
- o app abre sem travar com a base de teste grande;
- o CI está verde.

### Fase 1 — Base multiplataforma, ainda só Android · v1.6 · 2–5 semanas

**Objetivo:** preparar a estrutura sem mudar nada que o usuário vê.

**Entregas:**

- Kotlin 2.4, AGP 9, Room 2.8 com o driver SQLite embutido e migrações reescritas na API nova.
- Módulo `:shared` com Room (e `schemas/`), DataStore no **mesmo caminho de arquivo**, domínio, repositório e ViewModels.
- `:androidApp` com as telas atuais.
- ~~Esqueleto do app iOS rodando uma tela de teste num iPhone real~~ — trocado em 2026-10-27, porque não há iPhone nem Xcode local: o `:shared` compila para iOS e roda `commonTest` no simulador num job macOS do CI. O app iOS e o teste de teclado, insets e gesto de voltar ficam para quando houver um aparelho. Spec: [fase 1](specs/2026-10-27-phase1-shared-base-design.md), em duas etapas (1A ferramentas e Room KMP-ready; 1B módulo `:shared`).

**Andamento:**

- [x] **Etapa 1A, ferramentas e Room KMP-ready** (concluída e na `main` do fork em 2026-10-27, CI verde: run 40616854260): Gradle 9.7.1, AGP 9.3.3 com Kotlin embutido, Kotlin 2.4.21, KSP 2.3.12, Compose BOM 2026.09.00, `compileSdk` 37; Room 2.8.5 com `AndroidSQLiteDriver`, migrações na API `SQLiteConnection` e transações por `WorkoutDatabase.writeTransaction`. Atualização por cima no emulador sem perda de dado. [Plano](plans/2026-10-27-phase1a-toolchain-room.md), [verificação](reports/2026-10-27-phase1a-verificacao.md). O driver ficou no SQLite do sistema (`AndroidSQLiteDriver`), e não no SQLite embutido que a entrega acima previa: ver a spec.
- [x] **Etapa 1B, módulo `:shared`** (verificada no emulador e com merge na `main` do fork em 2026-10-28, depois da revisão final da branch e do CI verde): dados, domínio, backup, preferências, persistência do timer, contrato de UI, apresentação e a lógica do ViewModel no `:shared` (Android, `iosArm64`, `iosSimulatorArm64`), com Room KMP e os schemas em `shared/schemas/`; job `ios` no CI rodando `commonTest` no simulador; teste instrumentado que abre um banco e um DataStore gravados pela 1.4.5. Atualização por cima no emulador sem nenhuma diferença, APK debug +236 bytes. [Plano](plans/2026-10-27-phase1b-shared-module.md), [verificação](reports/2026-10-27-phase1b-verificacao.md).

**Pronto quando:**

- o `compat-reviewer` dá `SEGURO`;
- um banco e as preferências da v1.5 abrem intactos;
- o comportamento é idêntico ao da v1.5;
- o job iOS do CI (compilação do `:shared` e `commonTest` no simulador) está verde, e os relatórios das etapas 1A e 1B estão em `reports/`.

### Fase 2 — Identidade visual e design system · v1.7 · 3–5 semanas

**Objetivo:** criar a linguagem visual do Mooscles e as peças com que todas as telas novas serão montadas. A pesquisa P1 e a parte de identidade da spec podem começar durante a fase 1.

**Entregas:**

- **Identidade:** paleta (claro e escuro), tipografia própria com números tabulares, iconografia, estilo de ilustração e tom de voz. O ícone verde atual é o ponto de partida.
- **Tokens em `commonMain`:** cor, tipo, espaçamento (escala de 4 pt), raio, elevação e movimento (durações e curvas nomeadas).
- **Biblioteca de componentes:**
  - botões;
  - campo numérico de treino com stepper;
  - cards de exercício e de série;
  - chips;
  - bottom sheets;
  - timer;
  - estados vazios, carregamento (skeleton) e erro;
  - diálogos;
  - celebração de PR;
  - feedback tátil.
- Galeria de componentes acessível num modo de desenvolvimento.
- Textos movidos para recursos, com tradução para pt-BR.
- Uma tela piloto (Configurações) refeita com o sistema, para validar.

**Pronto quando:**

- nenhum componente novo usa cor, medida ou duração fora dos tokens;
- o contraste passa no WCAG AA;
- a fonte grande do sistema (200%) não quebra os componentes;
- a galeria roda no Android e no iOS.

**Andamento (2026-10-28):** implementada na branch `feat/phase2-design-system`, verificada no emulador e no host, à espera do job `ios` do CI e do merge na `main` do fork. [Spec](specs/2026-10-27-phase2-design-system-design.md) (com as [notas de implementação](specs/2026-10-27-phase2-design-system-design.md#notas-de-implementação)), [plano](plans/2026-10-27-phase2-design-system.md), [spike de `tnum`](reports/2026-10-28-phase2-tnum-spike.md), [verificação](reports/2026-10-28-phase2-verificacao.md), [crítica de Configurações e da galeria](reports/2026-10-28-phase2-critica-configuracoes.md).

- Tokens: `tools/check-design-tokens.ps1` limpo nos componentes, na galeria, nas licenças e em Configurações, e no CI.
- Contraste: os 50 pares da spec passam no WCAG AA nos dois temas, por teste.
- 200%: os testes de dispositivo montam cada página da galeria a 2,0 nos dois temas, e as capturas do emulador não mostram corte no conteúdo de Configurações. A casca do app (abas e dock antigo) quebra a 200%; isso foi para a fase 3.
- Galeria: no Android, atrás de 7 toques na versão, em Configurações. No iOS, como teste (`GallerySmokeTest` no simulador do CI), porque não há `iosApp`; a revisão visual no iOS fica para a fase 7.
- Não verificado: uma sessão real de TalkBack, qualquer coisa num iPhone ou num Android físico, e as mensagens do updater e do backup em pt-BR (continuam em inglês até a fase 6). Lista completa na verificação.

### Fase 3 — Redesign do treino ativo · v2.0 · 3–5 semanas

**Objetivo:** o fluxo usado todo dia fica o mais rápido e agradável possível. Esta é a fase que define o produto.

**Entregas:**

- Iniciar o treino a partir da rotina.
- Card de exercício com a última performance e o alvo visíveis. **2026-10-29:** o alvo da rotina aparece como o valor pré-preenchido na linha de série; uma linha de alvo separada ("Alvo 3×8 · 80 kg") foi para a fase 4, por decisão da [spec](specs/2026-10-29-phase3-active-workout-design.md) ("Adaptações do 'Pronto quando' do ROADMAP").
- Registro de série em um toque, com stepper e gesto para concluir.
- Timer redesenhado, com anel animado, vibração e posição inteligente acima do teclado.
- Reação animada ao bater um PR.
- Resumo de fim de treino com estatísticas e o novo cartão de compartilhamento.
- Espaço reservado para a mídia do exercício (thumbnail), pronto para a fase 4.
- Herdado da crítica da fase 2 ([relatório](reports/2026-10-28-phase2-critica-configuracoes.md#fase-3)), na casca do app que a fase 2 não reconstruiu:
  - #1: o dock antigo do timer corta "−0:15", "+0:15" e o nome do exercício a 200%;
  - #2: os rótulos das abas quebram no meio da palavra a 200%;
  - #3: a 200%, com treino ativo, sobra cerca de 45% da altura para o conteúdo;
  - #4: idiomas misturados em pt-BR (abas e dock em inglês);
  - #5: no tema claro, a barra de abas lavanda do Material não combina com a página;
  - #20: tocar na aba atual de dentro da galeria não volta à raiz da aba.
- Também da fase 2: o campo lê o valor antes do nome ("2:30, Default rest duration, …", #11), o que exige mudar o `MTextField`; e uma sessão real de TalkBack em Configurações e na linha de série, que o emulador não permitiu.

**Pronto quando:**

- registrar uma série não leva mais toques que hoje, o que se mede comparando os fluxos;
- as animações mantêm 60 fps no aparelho de referência;
- o treino ativo funciona inteiro no iOS (build interno).

**Andamento (2026-10-29):** implementada e verificada na branch `feat/phase3-active-workout`, a partir da `main` do fork em `51508a5`. Antes do merge na `main` do fork faltam: corrigir um teste do design system que falha só no simulador iOS (o texto do anel do timer expandido a 200%, ver a verificação), o `compat-reviewer` da branch inteira e o `/code-review`. [Spec](specs/2026-10-29-phase3-active-workout-design.md) (com as [notas de implementação](specs/2026-10-29-phase3-active-workout-design.md#notas-de-implementação)), [plano](plans/2026-10-29-phase3-active-workout.md), [spikes](reports/2026-10-29-phase3-spikes.md), [verificação](reports/2026-10-29-phase3-verificacao.md), [crítica do treino](reports/2026-10-29-phase3-critica-treino.md).

- Entregue: o módulo `:screens` (Compose Multiplatform, Android e iOS) com o treino ativo novo, o resumo de fim, o cartão de compartilhamento em código comum e a casca do app com quatro abas e a barra do treino; a notificação em andamento do descanso com ações no Android; o ícone novo; e o build `preview`, que instala ao lado do app. Os itens #1–#5 e #20 herdados da fase 2 foram resolvidos na casca nova. O #11 (o `MTextField` lê o valor antes do nome) continua aberto: o spike S2 não achou uma estratégia que se pudesse provar sem o TalkBack real.
- Registrar uma série: nenhum fluxo medido leva mais toques que hoje (concluir uma série igual à da semana passada foi de 6 para 1; subir a carga um passo, de 8 para 3), provado por teste contra a contagem feita na tela antiga.
- 60 fps: medido só no emulador. O aparelho de referência ainda não foi definido (ver as perguntas em aberto).
- iOS: em vez de um build interno, o `ActiveWorkoutFlowTest` percorre o treino inteiro no simulador do CI sobre o ViewModel, o Room e o DataStore reais, e guarda capturas. O build interno no iPhone fica para a fase 7.
- Não verificado: TalkBack, háptico e desempenho num aparelho (pendentes do dono instalar o `preview`), qualquer coisa num iPhone e a release, que depende da chave original de assinatura.

### Fase 4 — Biblioteca de exercícios e rotinas visuais · v2.1 · 3–5 semanas

**Objetivo:** cada exercício passa a ter cara, e montar uma rotina fica visual e rápido.

**Entregas:**

- Mídia dos exercícios conforme a P2: thumbnails embutidos e animação ou vídeo sob demanda, com cache offline.
- Tela de detalhe com execução animada, músculos trabalhados num mapa corporal e instruções.
- Busca e filtros por músculo e equipamento.
- Editor de rotina com arrastar e soltar, pastas e prescrição por série.
- Exercícios personalizados com foto própria.
- Linha de alvo da rotina no card do treino ("Alvo 3×8 · 80 kg"), vinda da fase 3 (2026-10-29).
- Nomes dos exercícios do catálogo em pt-BR (a única mistura de idioma conhecida nas telas da fase 3).

**Pronto quando:**

- a biblioteca funciona sem rede depois do primeiro uso;
- o app base continua abaixo do limite de tamanho definido na spec;
- todas as mídias têm licença registrada.

**Andamento (2026-10-30):** a fase se divide em 4A (biblioteca), 4B (rotinas) e 4M (download de mídia, só se houver mídia encomendada). A **4A está implementada e verificada** na branch `feat/phase4a-library`, a partir da `main` do fork em `739a800`; `compat-reviewer` SEGURO, `/code-review` com as correções aplicadas e CI verde ([41046481599](https://github.com/pedroyuuki/Mooscles/actions/runs/41046481599)); merge na `main` do fork em 2026-10-30, sem esperar o portão G1. Entregue: a aba Biblioteca nova (busca em inglês e pt-BR, filtros por músculo e equipamento, recentes, "Meus exercícios"), o detalhe do exercício com figura, mapa corporal, dicas de execução e resumo do histórico, o formulário de exercício personalizado (sem foto), o seletor do treino com os mesmos filtros e "Sobre o exercício" no treino ativo; os 287 exercícios com nome em pt-BR, anatomia e dicas. As figuras são o **piloto de 20 exercícios**. [Spec](specs/2026-10-29-phase4-library-routines-design.md) (com as [notas de implementação](specs/2026-10-29-phase4-library-routines-design.md#notas-de-implementação)), [plano da 4A](plans/2026-10-29-phase4a-library.md), [spike do mapa](reports/2026-10-29-phase4a-spike-mapa.md), [folha do piloto](reports/2026-10-30-phase4a-folha-piloto.md), [crítica](reports/2026-10-30-phase4a-critica-biblioteca.md) e [verificação](reports/2026-10-30-phase4a-verificacao.md). Nenhum fluxo medido passa do orçamento de toques e o APK cresceu 450.168 bytes (limite da 4A: 629.146). Não verificado: iPhone, TalkBack e 120 Hz num aparelho físico, a bateria da figura do detalhe, a qualidade das figuras e dicas para quem treina e a release com a chave original.

- **Portão G1 (figuras próprias):** a folha do piloto foi entregue ao dono em 2026-10-30 e ele a **aprovou em 2026-10-30**, com uma mudança de cor: o equipamento das figuras passou a cobre (laranja), para se destacar do corpo e do músculo (`cobre47` no claro, `cobre64` no escuro; [spec](specs/2026-10-29-phase4-library-routines-design.md)); a folha foi refeita. Os lotes de figuras seguem. Aprovado: lotes de figuras por família (Task L do plano, cerca de 50 exercícios cada, em branch própria, podendo correr durante a 4B). Reprovado: o piloto sai do produto e o ícone do equipamento fica (Task R), e a encomenda de figuras vai para uma fase futura.
- **4B (rotinas): implementada e verificada em 2026-10-30; mesclada na `main` do fork em 2026-10-31 (`c436639`).** Branch `feat/phase4b-routines`, CI verde ([41169992244](https://github.com/pedroyuuki/Mooscles/actions/runs/41169992244), `a855a9e`: host 1086, iOS 275, 375 e 641). Entregue: a aba Treinar nova com pastas, o editor de rotina com arrastar e soltar, aquecimento planejado e a **migração do Room para a v6** (`routine_sets.warmup`, `MIGRATION_5_6`), a linha de alvo no card do treino, a foto do exercício personalizado (galeria e câmera no Android; o formulário fica aberto até o detalhe abrir) e a seleção múltipla no seletor; as telas antigas de Treinar e de rotina saíram. Fecha as pendências da 4A: erro por comando e figura sem quadros na pausa; as barras do sistema das sheets e diálogos no tema claro ficam com os ícones certos, mas o fundo continua cinza (adiado na crítica). O APK cresceu 284.661 bytes na 4B (734.829 com a 4A, limite 1.048.576), sem dependência nova. [Plano da 4B](plans/2026-10-30-phase4b-routines.md), [verificação](reports/2026-10-30-phase4b-verificacao.md), [spike do arrastar](reports/2026-10-30-phase4b-spike-reordenar.md) e [spike da foto](reports/2026-10-30-phase4b-spike-foto.md). Não provado: o desempenho (a medida da Task 18 foi contaminada pela carga do Mac; a nova rodada está na verificação); não verificado: iPhone, TalkBack, câmera real e 120 Hz num aparelho físico, e a release com a chave original.
- **Figuras (Task L do plano da 4A): concluídas em 2026-10-31.** 285 dos 287 exercícios do catálogo têm figura, em seis lotes mesclados um a um na `main` do fork: [barra](reports/2026-10-31-phase4-figuras-barra.md) (49), [halter](reports/2026-10-31-phase4-figuras-halter.md) (56), [polia](reports/2026-10-31-phase4-figuras-polia.md) (45), [peso corporal](reports/2026-10-31-phase4-figuras-peso-corporal.md) (46), [máquinas](reports/2026-10-31-phase4-figuras-maquinas.md) (52), [kettlebell e Smith](reports/2026-10-31-phase4-figuras-final.md) (22 e 15). O Pallof press e a rotação de tronco na máquina ficam com o ícone do equipamento (`ExerciseFigures.NO_FIGURE`: a vista de lado não mostra uma rotação), e `everyCatalogExerciseHasAFigure` confere o resto. O renderizador ganhou o trilho do Smith, o kettlebell na mão de perto desenhado por cima do braço e a elevação de ombro opcional (padrão 0) para os encolhimentos. O APK da fase 4 inteira ficou em cerca de 800 KB de crescimento, dentro do limite de 1 MiB.
- **Próximo:** a fase 5 (a 5A está pronta na branch `feat/phase5a-progress`; a 5B segue), pelo plano abaixo. O app iOS ficou para depois, por decisão do dono. A 4M (download de mídia) só entra se houver mídia encomendada.

### Fase 5 — Progresso e histórico · v2.2 · 2–4 semanas

**Objetivo:** mostrar a evolução de forma que motive a voltar.

**Entregas:**

- Gráficos por exercício (carga, 1RM estimado, volume) e gerais (volume por semana, frequência, distribuição por músculo).
- Calendário ou heatmap de treinos.
- Linha do tempo de recordes.
- Histórico redesenhado com filtros.
- Cartão de compartilhamento alinhado à nova identidade.

**Pronto quando:**

- os gráficos abrem rápido com a base de teste grande (208 treinos e cerca de 12,7 mil séries);
- os gráficos são acessíveis, com descrição para leitor de tela.

**Andamento (2026-10-31):** a fase se divide em **5A** (gráficos por exercício, progresso do exercício e aba Recordes) e **5B** (histórico redesenhado, calendário, gráficos gerais e prévia de compartilhamento). Spec: [fase 5](specs/2026-10-31-phase5-progress-history-design.md), com as [notas de implementação](specs/2026-10-31-phase5-progress-history-design.md#notas-de-implementação). Plano: [5A](plans/2026-10-31-phase5a-charts-progress.md).

- **5A: implementada, verificada e mesclada na `main` do fork em 2026-10-31.** O CI final da branch ([41252863227](https://github.com/pedroyuuki/Mooscles/actions/runs/41252863227), `8462c70`) passou com host 1264 e iOS 280, 414 e 892, depois da revisão final (Opus) e do `/code-review` com as correções aplicadas. Entregue: os gráficos próprios no `:designsystem` (linha, barras, sparkline e a tabela "Ver dados"), o progresso do exercício (`exercise/{id}?metric=`) com métricas por convenção, a aba Recordes como linha do tempo (busca, "Só atuais", "Primeiros registros") e a sparkline no card do detalhe do exercício; um `ProgressIndex` único, aquecido na abertura. Sem mudança de dados: Room continua na v6, `versionCode` 16. O spike S1 aprovou os gráficos próprios (o Vico não entra). O APK cresceu 284.839 bytes (22.695 acima da meta da 5A; limite da fase 524.288, e a 5B fica com 239.449), sem dependência nova. [Verificação](reports/2026-10-31-phase5a-verificacao.md), [spike S1](reports/2026-10-31-phase5a-spike-graficos.md) e [crítica](reports/2026-10-31-phase5a-critica.md). Não provado: iPhone, TalkBack e desempenho num Android físico; a rolagem de controle da Biblioteca deu 1,75–2,3%, contra cerca de 1% na 4A, sem causa na 5A (rechecar no preview do S23); o gráfico não foi medido de novo depois da `graphicsLayer`.
- **5B implementation checkpoint (2026-11-05).** History/calendar, overall progress, saved detail/editing and common preview are implemented on `codex/phase5b-history`. Android functional checks, offline compatibility and clean APK checks pass. The phone met the scrolling goal with the local muscle-map fix. Live unit switching remains above its goal, and visible-frame timing remains open. Final checks are Android-only. [Plan](plans/2026-11-04-phase5b-history.md), [verification](reports/2026-11-05-phase5b-verificacao.md), [performance](reports/2026-11-05-phase5b-performance.md).

### Fase 6 — Telas restantes e paridade · v2.3 · 1–3 semanas

**Objetivo:** não sobra nenhuma tela antiga.

**Entregas:**

- Configurações completas.
- Tela de atualizações (Android).
- Onboarding curto no primeiro uso.
- Remoção das telas e componentes antigos do `:androidApp`.

**Pronto quando:** todas as telas são do `:shared` e não existe mais medida solta em `dp` fora dos componentes.

### Fase 7 — Lançamento no iOS · 2–4 semanas

**Objetivo:** o Mooscles na App Store.

**Entregas:**

- Timer por notificação local `timeSensitive` com som próprio.
- Compartilhamento pelo `UIActivityViewController`.
- Import do arquivo exportado no Android, para a migração de quem troca de aparelho.
  - Antes, um teste de ida e volta do backup entre plataformas: um arquivo exportado no Android é importado no iOS e exportado de novo, e o inverso, com o mesmo conteúdo. A revisão final da 1B (2026-10-28) deixou isso de fora: a formatação de `Double` no Kotlin/Native e na JVM pode diferir no JSON do backup.
- Política de privacidade e privacy manifest.
- Apple Developer Program, TestFlight e App Store.

**Pronto quando:**

- o app foi aprovado na App Store;
- um usuário migra do Android para o iPhone pelo arquivo sem perder nada.

### Fase 8 — Backup na nuvem do usuário · 2–3 semanas

**Objetivo:** backup automático sem servidor próprio.

**Entregas:**

- Google Drive `appDataFolder` no Android e no iOS, com backup periódico e restauração.
- iCloud no iOS.
- Tela de backup com a data do último backup e o tamanho.

**Pronto quando:** restaurar num aparelho novo, inclusive de outro sistema pelo Drive, recupera tudo, e nenhum dado passa por servidor do projeto.

### Fase 9 — Extras, por demanda

Só entram com motivo claro, cada uma com sua própria spec:

- Live Activity e AlarmKit no iOS;
- widgets;
- Health Connect e HealthKit;
- relógio;
- modo de treino guiado;
- servidor de sincronização entre aparelhos (PowerSync + Supabase), que tem custo mensal e traz obrigações de LGPD.

## Perguntas em aberto

- ~~Qual é o modelo exato do aparelho Android do dono, que será o aparelho de referência para desempenho?~~ Respondida em 2026-10-29: Samsung Galaxy S23 Ultra (Snapdragon 8 Gen 2, tela de 120 Hz, One UI com o teclado Samsung). É o aparelho de referência para desempenho e para o que o emulador não prova; os testes automatizados continuam no emulador do Mac.
- O dono topa instalar o "Mooscles Preview" (build `preview`, ao lado do app de verdade, sem tocar nos dados dele) no próprio aparelho e percorrer a lista de TalkBack e háptico da fase 3 (cerca de 30 minutos)? Sem isso, esses itens continuam não verificados, como nas fases 2 e 3. **2026-10-29:** o dono tem o APK preview da fase 3 (`2359ab6`) e deixou os testes no S23 Ultra como pendentes por ora, para seguir com a fase 4; a lista (teclado Samsung, notificação na gaveta e na tela de bloqueio, vibração, fluidez a 120 Hz, fonte no máximo e TalkBack) continua não verificada.
- ~~Haverá um iPhone físico para testes antes da fase 1 terminar?~~ Respondida em 2026-10-27: não há iPhone. O iOS fica restrito ao simulador do CI até haver um aparelho; teclado, vibração e desempenho reais no iPhone continuam sem verificação.

Respondidas em 2026-10-27: sem Google Play por enquanto e app gratuito (ver Decisões).

## Andamento

| Fase | Status | Spec | Plano | Release |
| --- | --- | --- | --- | --- |
| 0 | concluída: release [v1.5.0](https://github.com/ReiTut/Mooscles/releases/tag/v1.5.0) publicada em 2026-11-01 no original, com a chave original; o "Pronto quando" ainda pede o export/import verificado entre aparelhos reais | [export/import](specs/2026-10-27-export-import-design.md) · [PR fora da main thread](specs/2026-10-27-pr-off-main-thread-design.md) | [export/import](plans/2026-10-27-export-import.md) · [PR fora da main thread](plans/2026-10-27-pr-off-main-thread.md) · [build no macOS e CI](plans/2026-10-27-macos-build-ci.md) | [v1.5.0 (rascunho e instruções do PR)](releases/v1.5.0.md) |
| 1 | concluída: 1A e 1B na `main` do fork (2026-10-27 e 2026-10-28), sem release | [base multiplataforma](specs/2026-10-27-phase1-shared-base-design.md) | [1A](plans/2026-10-27-phase1a-toolchain-room.md) · [1B](plans/2026-10-27-phase1b-shared-module.md) | — |
| 2 | concluída: implementada e verificada em 2026-10-28 ([verificação](reports/2026-10-28-phase2-verificacao.md), [spike](reports/2026-10-28-phase2-tnum-spike.md), [crítica](reports/2026-10-28-phase2-critica-configuracoes.md)), com merge na `main` do fork em `4f2911c`; sem release (entra no PR final, ver a [v1.5.0](releases/v1.5.0.md)) | [design system](specs/2026-10-27-phase2-design-system-design.md) · [direção visual](https://claude.ai/artifact/Y4pTUbYqTcCCrznT74raBM) | [design system](plans/2026-10-27-phase2-design-system.md) | — |
| 3 | concluída: implementada e verificada em 2026-10-29 ([verificação](reports/2026-10-29-phase3-verificacao.md), com o [adendo da revisão final](reports/2026-10-29-phase3-verificacao.md#adendo-2026-10-29-revisão-final); [spikes](reports/2026-10-29-phase3-spikes.md), [crítica](reports/2026-10-29-phase3-critica-treino.md)); `compat-reviewer` SEGURO e `/code-review` com as correções aplicadas; merge na `main` do fork em `2359ab6`, CI verde ([40959862910](https://github.com/pedroyuuki/Mooscles/actions/runs/40959862910)); sem release (entra no PR final) | [treino ativo](specs/2026-10-29-phase3-active-workout-design.md) | [treino ativo](plans/2026-10-29-phase3-active-workout.md) | [v1.5.0](releases/v1.5.0.md): release pendente da chave original |
| 4 | 4A concluída (implementada e verificada em 2026-10-30, merge na `main` do fork; [verificação](reports/2026-10-30-phase4a-verificacao.md), [spike do mapa](reports/2026-10-29-phase4a-spike-mapa.md), [folha do piloto](reports/2026-10-30-phase4a-folha-piloto.md), [crítica](reports/2026-10-30-phase4a-critica-biblioteca.md)); G1 (figuras) aprovado pelo dono em 2026-10-30, com o equipamento em cobre; 4B implementada, verificada e mesclada (2026-10-31; [verificação](reports/2026-10-30-phase4b-verificacao.md), [spike do arrastar](reports/2026-10-30-phase4b-spike-reordenar.md), [spike da foto](reports/2026-10-30-phase4b-spike-foto.md)); lotes de figuras (Task L) a seguir; sem release | [biblioteca e rotinas](specs/2026-10-29-phase4-library-routines-design.md) | [4A](plans/2026-10-29-phase4a-library.md) · [4B](plans/2026-10-30-phase4b-routines.md) | — |
| 5 | 5A merged into the fork's `main` on 2026-10-31. 5B implemented on `codex/phase5b-history`, with Android-only final verification and open performance criteria. [5A verification](reports/2026-10-31-phase5a-verificacao.md) · [5B verification](reports/2026-11-05-phase5b-verificacao.md) | [progresso e histórico](specs/2026-10-31-phase5-progress-history-design.md) | [5A](plans/2026-10-31-phase5a-charts-progress.md) · [5B](plans/2026-11-04-phase5b-history.md) | — |
| 6 | não iniciada | — | — | — |
| 7 | não iniciada | — | — | — |
| 8 | não iniciada | — | — | — |
