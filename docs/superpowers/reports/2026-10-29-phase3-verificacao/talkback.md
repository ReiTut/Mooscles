# Fase 3: TalkBack e aparelho (Task 31, Step 3)

Data: 2026-10-29. Build: o APK debug da branch `feat/phase3-active-workout` com as correções da crítica (até `b097cd0`), no `emulator-5556` (Pixel 8, Android 15, 1080 × 2400, 420 dpi, 411 dp de largura).

## Situação

O dono ainda não respondeu à pergunta 2 da spec (instalar o `preview` no próprio aparelho e percorrer a lista). Por isso **nenhum dos 11 itens foi verificado com TalkBack num aparelho**. Este arquivo registra o caminho de reserva do plano: a ordem dos nós e as ações tiradas do `uiautomator dump` no emulador, com o TalkBack desligado. Nada foi instalado em aparelho físico.

## Os 11 itens da spec

| # | Item | Situação | O que já existe de prova automática |
| --- | --- | --- | --- |
| 1 | Linha de série lida como um nó, com a sugestão; toque duplo abre a edição da carga e anuncia; as cinco ações; o check como alternável | **Não verificado no aparelho, pendente do dono.** O dump mostra a linha como um nó clicável com a leitura completa ("Série 1 de 3, anterior 60 quilos por 21, carga 60 quilos, 22 repetições, concluída, recorde pessoal"), os dois campos como nós próprios e o check como `checkable`. Desde `b05c931`, o check concluído se chama "Série 1 concluída" (antes continuava "Concluir série 1"). | `SetRowTest` (nó único, cinco ações, sugestão falada, `rowClickIsEditSet`), `SetCheckTest.nameAndActionFollowTheState`, `WorkoutAccessibilitySweepTest` |
| 2 | Stepper diz o passo e lê o valor novo | **Não verificado no aparelho, pendente do dono.** O dump da edição (em inglês, na rodada exploratória) mostra "Decrease load by 2.5 kilograms", "Increase load by 2.5 kilograms", o valor "272.16 kg", "step of 2.5 kg" e o grupo "Editing load of set 1". | `StepperTest` |
| 3 | Aviso de recorde lido uma vez, de forma educada | **Não verificado no aparelho, pendente do dono.** O aviso aparece no dump como dois textos ("Novo recorde de repetições", "60 kg × 22 · antes 60 kg × 21"). | `RecordReactionTest`, `PrToastTest` |
| 4 | Timer anunciado ao começar, ajustar e terminar, nunca a cada segundo; o expandido prende e devolve o foco | **Não verificado no aparelho, pendente do dono.** Ver as candidatas 1 e 2 abaixo. | `RestTimerDockTest`, `WorkoutTimerTest` (foco devolvido) |
| 5 | Toast de desfazer anunciado e alcançável antes de sumir | **Não verificado no aparelho, pendente do dono.** | `SetTypeAndRemovalTest`, `MToastTest` |
| 6 | Diálogos de finalizar e descartar com a confirmação por último | **Não verificado no aparelho, pendente do dono.** No dump, os botões vêm em "Continuar treinando", "Finalizar" / "Descartar"; o fundo "Dispensar" aparece antes deles na árvore, mas a travessia o põe por último (Task 28). | `MDialogTest`, `ActiveWorkoutScreenTest.finishDialogTextsAndOrder` |
| 7 | Barra de abas ("Treinar, guia 1 de 4, selecionada") e barra do treino | **Não verificado no aparelho, pendente do dono.** O dump mostra as abas com o rótulo e `selected` na atual, e a barra como um botão: "Treino em andamento, Verif routine 1, 2 minutos. Descanso, 48 segundos" com "Pular descanso" ao lado. "guia 1 de 4" é informação de coleção, que o dump não mostra. | `MTabBarTest`, `ActiveWorkoutBarTest`, `AppShellBottomTest` |
| 8 | Pendências da fase 2: topo de Configurações primeiro (#10), versão lida como texto (#8), campo de descanso pelo rótulo (#11) | **Não verificado no aparelho, pendente do dono.** O dump lista o campo como "2:00 · Duração padrão do descanso · min · …" (o valor antes do rótulo na árvore; o que o TalkBack fala depende do spike S2). "Voltar" e "Configurações" aparecem depois do conteúdo na árvore; a travessia os põe primeiro (#10, fase 2). | `SettingsScreenHostTest.topBarIsReadBeforeTheContent` |
| 9 | Háptico de cada momento da tabela | **Não verificado no aparelho, pendente do dono** (o emulador não vibra). | testes com `RecordingHaptics` em cada componente e tela |
| 10 | Notificação em andamento com as ações e a tela bloqueada | **Não verificado no aparelho, pendente do dono.** A notificação e as ações foram vistas no emulador pela Task 30 (`upgrade.md`). | `RestProgressNotificationHostTest`, `RestTimerActionsHostTest` |
| 11 | 60 fps nas molas e na rolagem (`gfxinfo`) | **Não verificado no aparelho, pendente do dono.** Medido só no emulador pela Task 30 (`performance.md`). | — |

## Como o dump foi tirado e o que ele não mostra

- `adb -s emulator-5556 shell uiautomator dump`, em pt-BR, tema escuro, escala 1,0. O XML de cada tela está nesta pasta (`talkback-<tela>.xml`), e a lista legível ao lado (`talkback-<tela>.txt`), feita assim: cada nó focável, clicável ou marcável é uma parada, com o próprio rótulo ou, sem ele, o texto dos filhos não focáveis; um texto fora desses nós é uma parada de texto.
- **A ordem é a da árvore, não a da travessia.** O Compose ordena a leitura com `traversalIndex` e grupos de travessia (o aviso de recorde antes dos cards, o dock por último, o topo de Configurações primeiro), e o Android passa isso como "traversal before/after", que o dump não mostra. A ordem real está provada por `WorkoutAccessibilitySweepTest.readingOrder` e pelos testes citados acima.
- **As ações personalizadas não aparecem.** O dump só mostra `clickable`, `long-clickable`, `checkable`, `checked`, `selected` e `scrollable`. As cinco ações da linha de série e "Copiar anterior" estão provadas por `SetRowTest.exposesFiveCustomActionsThatCallBack` e `previousIsATargetThatCopies`.
- **Contêineres mesclados aparecem abertos.** Um nó com `mergeDescendants` e sem clique (a célula de estatística do resumo, a linha de recorde do resumo) é focável só para o leitor de tela (`screenReaderFocusable`), atributo que o dump não tem; por isso os textos dele aparecem um a um nas listas `summary`.

## Ordem e ações por tela (resumo das listas)

- **Treino ativo** (`talkback-workout-dark-pt.txt`, 29 paradas): Minimizar treino → título → "Tempo de treino, menos de um minuto" → "0 de 9 séries" → Finalizar → Mais opções → cabeçalho do card (clicável, abre e fecha) → Opções do exercício → "Descanso 2:00" → "Anterior: 29 out" → por série: a linha (clicável, ação "Editar série"), carga (clicável e de toque longo), repetições, o check (`checkable`) → Adicionar série → cards recolhidos (clicáveis) → Adicionar exercício → Finalizar treino.
- **Treino com recorde e descanso** (`talkback-workout-record-dark-pt.txt`, 34 paradas): o check concluído já se chama "Série 1 concluída" (`checked`); o aviso de recorde, o corpo do dock ("Abrir timer de descanso, tempo restante: 1 minuto e 58 segundos"), −15 s, +15 s e "Pular descanso" vêm depois dos cards; a última parada é o nó de anúncio do dock ("Descanso, tempo restante: 2 minutos", 3 × 3 px). Ver a candidata 1.
- **Timer expandido** (`talkback-expanded-dark-pt.txt`, 7 paradas): ver a candidata 2; depois Fechar timer, −15 s, +15 s, Pular descanso, Pausar, Recomeçar.
- **Finalizar** e **Descartar** (`talkback-finish-dialog-dark-pt.txt`, `talkback-discard-dialog-dark-pt.txt`): o texto do diálogo, "Dispensar" (o fundo), "Continuar treinando" e a confirmação.
- **Casca** (`talkback-shell-dark-pt.txt`): a tela Treinar antiga (em inglês, fase 4), a barra do treino como um botão com o nome acessível da spec e as quatro abas, "Treinar" com `selected`.
- **Resumo** (`talkback-summary-dark-pt.txt`): título, nome, data, as quatro estatísticas, "Recordes" e a linha do recorde, a constância, a melhor série por exercício, Concluir, Compartilhar, Ver detalhes e as abas.
- **Configurações** (`talkback-settings-dark-pt.txt`): os rádios de tema e de unidade como `checkable`, o switch do descanso, o campo de descanso, e "Voltar" e "Configurações" no fim da árvore (a travessia os põe primeiro).

## Candidatas para a sessão com TalkBack

1. **O nó de anúncio do dock é uma parada** (item 7 da Task 31). Ele guarda o último anúncio ("Descanso, tempo restante: 2 minutos") e, ao chegar nele com o dedo ou deslizando, o TalkBack lê esse valor, que já pode estar velho. Não foi corrigido: ver a crítica (`2026-10-29-phase3-critica-treino.md`, M4). **2026-10-29, depois:** corrigido em `2ff31b4` (o nó só tem texto durante o anúncio; ver a nota no fim da crítica). Falta ouvir com o TalkBack real.
2. **O expandido tem um nó raiz focável** (o `focusable()` que recebe o "voltar" e o Esc). No dump ele aparece como uma parada com todo o texto da tela ("Descanso · Barbell bench press · série 1 de 3 · Descanso · 1:38 · de 2:00 · A seguir · …"). Pode ser só o dump juntando o texto de um nó sem rótulo; se o TalkBack também parar nele, vale tirá-lo da leitura.
3. **"A 1 série registrada será apagada."** (diálogo de descartar, singular): a frase é lida assim; ver a crítica, L7.
