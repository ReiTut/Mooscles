# Fase 5: progresso e histórico

Data: 2026-10-31. Status: **decisões delegadas pelo dono** ("pode avançar e fazer as melhores escolhas decidindo por mim"); não houve rodada de perguntas. Item da fase 5 do [ROADMAP](../ROADMAP.md). Sistema visual: [spec da fase 2](2026-10-27-phase2-design-system-design.md) ("Giz & Ferro": tokens `chart.*`, regra "gráficos têm uma descrição resumida e uma tabela alternativa", voz sem culpa). Telas e casca: [spec da fase 3](2026-10-29-phase3-active-workout-design.md) (módulo `:screens`, D2 das abas, D17–D20 de recordes, resumo e cartão) e [spec da fase 4](2026-10-29-phase4-library-routines-design.md) (detalhe do exercício, `ExerciseAnatomy`, mapa corporal, D16 da linha de alvo). Pesquisas: [P1, UX de apps de treino](../reports/2026-10-27-referencias-ux-apps-treino.md) e [P3, bibliotecas](../reports/2026-10-27-bibliotecas-cmp-graficos-animacao-midia.md). Código de referência: `main` do fork em `6a4ffa3`.

**Todas as decisões abaixo são minhas.** Cada uma aparece como **Dn (minha)** com uma linha de motivo, e todas são revisáveis. Elas partem do ROADMAP, das pesquisas e do código atual (`HistoryScreens.kt`, `WorkoutScreens.kt`, `LibraryScreens.kt`, `WorkoutSharePreview.kt` e `MoosclesApp.kt` no `:app`; `WorkoutCalculations.kt`, `WorkoutPresentation.kt`, `UiContract.kt`, `ExerciseAnatomy.kt` no `:shared`; `summary/`, `share/`, `shell/`, `format/` e `exercise/` no `:screens`; os componentes, tokens e o `BodyMap` do `:designsystem`).

## Por que

Histórico e recordes são as duas abas que ainda abrem telas antigas do `:app`, em Material 3, com textos fixos em inglês e o nome gravado do exercício (não o do idioma da tela):

- o Histórico é uma lista de cartões com uma busca por texto (que procura também a data em inglês, "2026 October 21") e três números no topo; não há calendário nem filtro;
- o detalhe do treino (`workoutDetail/{id}?summary=false`) é o único lugar que edita ou apaga um treino salvo, com o `WorkoutExerciseCard` e o `SetEditor` antigos;
- Recordes é uma lista alfabética do recorde atual de cada exercício e carga, sem ordem no tempo;
- o "histórico do exercício" (`exercise/{id}`) é uma lista de sessões; **não existe nenhum gráfico no app**, e a linha de progresso é justamente o que a P1 aponta como diferencial ("gráficos completos de graça", que Hevy e Strong trancam);
- a prévia do cartão de compartilhamento ("Workout preview") é a última peça do fluxo de fim de treino que ficou no Material e em inglês (herdada da fase 3).

## Objetivos

1. **Progresso por exercício:** carga, 1RM estimado e volume por sessão, num gráfico de linha com os recordes marcados, períodos e uma tabela alternativa.
2. **Progresso geral:** treinos por semana, volume por semana e distribuição por músculo, sobre o período escolhido.
3. **Calendário** dos treinos, que também serve para achar um treino por data.
4. **Linha do tempo de recordes**, do mais recente ao mais antigo, com o recorde atual marcado.
5. **Histórico redesenhado com filtros** (busca bilíngue, rotina, exercício, "com recorde", dia) e um **detalhe do treino novo** que mantém tudo o que o antigo faz (ver, editar, renomear, excluir, compartilhar).
6. **Compartilhamento alinhado à identidade de ponta a ponta:** a prévia redesenhada e traduzida.
7. **Acessível:** todo gráfico tem resumo falado com a tendência e uma tabela; 200% de fonte sem número cortado; nada só por cor.
8. **Rápido com a base grande** (208 treinos, cerca de 12,7 mil séries): todo cálculo fora da thread principal, com cache, nunca na composição.
9. **Nada muda nos dados:** sem migração, sem chave nova de DataStore, sem campo novo no backup.
10. **iOS provado no simulador do CI**, como nas fases 3 e 4, para tudo o que é comum.

## Fora de escopo

- **Sincronização, conta, nuvem, rede.** Nenhuma tela desta fase faz requisição.
- **Social:** feed, amigos, ranking, curtidas, comparação com outras pessoas.
- **Medidas corporais e peso corporal** (e, por consequência, 1RM de exercício com peso corporal: ver D5). Ficam para uma spec própria, por demanda (fase 9).
- **Metas, prescrição e recomendações** ("faça 10 a 20 séries de peito por semana", "suba a carga"), avisos de treino excessivo ou de músculo esquecido, e qualquer "insight" gerado.
- **Formatos novos de cartão** (cartão de recorde, de progresso, de semana). O cartão do treino da fase 3 continua o único.
- **Exportar gráficos ou CSV.** O export/import em JSON da fase 0 continua o caminho dos dados.
- **Editar horário de início ou fim** de um treino salvo, criar um treino "no passado" pelo calendário, e desfazer a remoção de série num treino salvo (D13).
- **Widgets, Health Connect, HealthKit, relógio** (fase 9).
- **Mensagens do updater e do backup em pt-BR** (fase 6) e o #11 da fase 2 (o `MTextField` lê o valor antes do rótulo; os campos de busca desta fase herdam o problema).
- **App iOS (`iosApp/`)** e qualquer coisa num iPhone (fase 7).
- **Release publicada.** Como nas fases 1 a 4, a fase termina com merge na `main` do fork e CI verde.

## Anti-objetivos (falha mesmo que "funcione")

- **Números que discordam:** o volume, as séries, a melhor série ou um recorde de um treino aparecer diferente no resumo, no detalhe, no cartão, na lista do histórico, no gráfico ou na linha do tempo. Toda regra de contagem vem de um lugar só (D6).
- **Recorde errado:** um ponto marcado como recorde no gráfico que o histórico não reconhece, ou um primeiro registro contado como recorde batido.
- **Culpa e pressão:** contador de sequência que zera, "você perdeu", "meta não batida", comparação negativa com outro período, confete, selo de conquista. Constância só em janela móvel e sempre afirmativa (D17).
- **Gráfico que só funciona com a vista:** um gráfico sem resumo falado, sem tabela ou que depende só da cor.
- **Travar a tela:** cálculo de gráfico, calendário ou filtro na composição ou na thread principal; um quadro longo ao abrir o Histórico ou trocar o período com a base grande.
- **Perder a edição do histórico:** o detalhe novo fazer menos que o antigo (D13), ou editar um treino salvo sem a confirmação de hoje.
- **Mudar dado guardado:** qualquer escrita nova no banco, no DataStore ou no backup.
- **Inglês novo em pt-BR** e nomes de exercício do catálogo no nome gravado em vez do idioma da tela.
- **APK crescer mais que o D18.**
- **Um segundo design para o iOS:** toda tela, componente e texto novo em `commonMain`.

## Etapas

**D1 (minha). Uma spec, duas etapas que entram e são verificadas separadamente: 5A progresso e recordes, 5B histórico.** Cada uma tem branch própria a partir da `main` do fork (`feat/phase5a-progress`, depois `feat/phase5b-history`), `/code-review`, CI verde e relatório de verificação próprio. *Por quê:* a 5A traz a fundação de gráficos (o único risco técnico, com spike) e as telas que só leem; a 5B traz a tela mais usada das duas (o Histórico) e a única com escrita (a edição do treino salvo), que merece revisão isolada. A ordem é essa porque a 5B usa os gráficos da 5A.

| Superfície | Etapa | Substitui |
| --- | --- | --- |
| Componentes de gráfico, sparkline e calendário (`:designsystem`) | 5A (gráficos, S1) e 5B (calendário, S2) | nada (novos) |
| `EstimatedOneRepMax` e o índice de progresso | 5A | nada |
| Progresso do exercício (rota `exercise/{id}`) | 5A | `ExerciseHistoryScreen` |
| Aba Recordes (linha do tempo) | 5A | `RecordsScreen` |
| Card "Seu histórico" do detalhe do exercício (sparkline e "Ver progresso") | 5A | o link para a tela antiga |
| Aba Histórico: Treinos (calendário, filtros, lista) e Progresso (gráficos gerais) | 5B | `HistoryScreen` |
| Detalhe do treino (`workoutDetail/{id}?summary=false`), com edição | 5B | `WorkoutDetailScreen`, `WorkoutExerciseCard`, `SetEditor` |
| Prévia de compartilhamento | 5B | a UI do `WorkoutSharePreview` |

### Herdado

| Pendência | Origem | Nesta fase |
| --- | --- | --- |
| Telas antigas de histórico e recordes em inglês e com o nome gravado | fases 3 e 4 (D11 da fase 4) | sim, todas saem (D19) |
| A prévia antiga do cartão no `:app` | fase 3 (tabela "Herdado" da fase 4) | sim, D14 (5B) |
| "Ver histórico completo" do detalhe do exercício leva à tela antiga | fase 4A | sim, leva ao progresso novo (5A) |
| `WorkoutExerciseCard`, `ExercisePicker`, `CustomExerciseDialog`, `SearchField`, `ChoiceFilter` e `RestDurationField` mantidos para o histórico | plano da 4B | saem quando não tiverem mais chamador (D19) |
| O `present()` completo da base grande não foi remedido depois da 4B | [verificação da 4B](../reports/2026-10-30-phase4b-verificacao.md) | sim, entra na rodada de desempenho do fim da fase (esta fase não muda o `present()`) |
| #11 da fase 2 (`MTextField`), TalkBack, háptico e 120 Hz num aparelho | fases 2 a 4 | não; continuam dependendo do dono (pergunta 1) |
| Trocar só a unidade refaz as passadas de recorde e histórico | fase 0 | não; o índice desta fase é refeito junto (custo medido, D15) |

## Onde cada coisa vive

**D2 (minha). As quatro abas continuam as da fase 3 (Treinar, Biblioteca, Histórico, Recordes); o Histórico ganha um segmento "Progresso" com os gráficos gerais, e o progresso por exercício é a rota `exercise/{id}` redesenhada.**

| Entrega | Onde |
| --- | --- |
| Gráficos por exercício (carga, 1RM estimado, volume) | tela "Progresso do exercício" (rota base `exercise/{id}`, a de hoje, com `?metric=` opcional), aberta pela linha do tempo de recordes, pelo detalhe do exercício (4A), pelo menu do card do treino ativo, pelo detalhe do treino e por "Mais feitos" |
| Gráficos gerais (treinos por semana, volume por semana, músculos) | aba Histórico, segmento **Progresso** |
| Calendário | aba Histórico, segmento **Treinos**, no topo da lista |
| Linha do tempo de recordes | aba Recordes |
| Histórico com filtros | aba Histórico, segmento **Treinos** |
| Detalhe do treino | rota `workoutDetail/{id}?summary=false` |
| Prévia do cartão | sheet sobre o resumo e o detalhe |

*Por quê:* uma quinta aba é o que mais quebra a 200% (#2 da fase 2); renomear Recordes para "Progresso" e pôr os recordes num segmento custaria um toque a mais para achar um recorde, contra a regra do orçamento de toques ("nenhum fluxo novo passa do de hoje"). Os gráficos gerais são novos (não têm contagem de hoje), então o toque no segmento não estoura nada. "O que eu fiz" (lista, calendário) e "como isso evolui" (gráficos do período) ficam na mesma aba.

- **Segmentos do Histórico:** dois `MChip` de `MChipKind.Segmented` ("Treinos", "Progresso"), em largura igual, sob o topo; padrão "Treinos". A escolha fica em `rememberSaveable` (sobrevive a ir ao detalhe e voltar, à troca de aba com `restoreState` e à recriação), não é gravada.
- **Rota do progresso (controlador, 2026-10-31):** a rota base não muda; ganha `?metric=` opcional: `exercise/{id}?metric={metric}`, com `metric` ∈ `load|reps|e1rm|volume` e padrão `load`. `load` abre a métrica de carga da convenção (Carga, Carga adicional ou Assistência); uma métrica que a convenção não tem (por exemplo, `e1rm` em Carga adicional) abre a primeira da convenção (D6). A convenção **não** é argumento de rota: o gráfico usa a convenção atual do exercício (ou, sem exercício atual, a mais recente do histórico). Um recorde de uma convenção antiga (um personalizado cuja convenção mudou) abre o gráfico da convenção atual com uma nota de uma linha ("Este recorde foi registrado como Por mão; o gráfico mostra os treinos como Carga total."); não há um segundo caminho de dados. O `ShellNavigationHostTest` ganha casos com o argumento opcional (com e sem `metric`, e um valor desconhecido caindo no padrão).
- As chaves das abas (`ShellTabs`) não mudam: os testes de navegação e as tags `tab-*` continuam.
- O menu do card do treino ativo troca "Histórico do exercício" por "Progresso do exercício" (mesma rota). O menu e o card do detalhe do exercício trocam "Ver histórico completo" por "Ver progresso".

## Gráficos

### Como desenhar

**D3 (minha). Gráficos próprios em Compose `Canvas` sobre os tokens, no `:designsystem`, sem biblioteca.** *Por quê:*

- **APK:** o APK distribuído é o debug, sem R8; uma biblioteca de gráficos (Vico tem `core` e `compose`) entra inteira no dex, e a P3 não achou medida de tamanho. Nossos gráficos são três formas simples (linha, barras, sparkline), e o projeto já desenha figuras, mapa e cartão em `Canvas`.
- **iOS:** `Canvas`, `drawWithCache` e `TextMeasurer` são comuns e já provados no simulador (figuras, cartão). Não há risco de versão de Kotlin ou CMP (Kotlin 2.4.21, CMP 1.12.2).
- **Estilo:** os tokens `chart.line`, `chart.pr` e `chart.grid` da fase 2, o latão só no recorde, o movimento reduzido e a fonte de números (`num.*`) sem adaptação de tema de terceiros.
- **Acessibilidade:** a P3 registra que o leitor de tela do Vico está num PR aberto (#1623). A regra da fase 2 (resumo e tabela) é cumprida melhor por semântica própria do que por uma biblioteca que ainda não a tem.
- **Manutenção:** cerca de três componentes pequenos, testáveis em `commonTest` (escala, marcas do eixo, redução de pontos) e em `uiTest`; nada a acompanhar de terceiros.

Não há spike comparando o Vico: com o APK e a acessibilidade pesando contra ele, o spike S1 prova a fundação própria e só abre a alternativa se ela reprovar.

**Componentes** (pacote novo `com.mooscles.designsystem.charts`, que não conhece o `:shared` nem `kotlinx-datetime`; os textos e rótulos chegam prontos de quem chama):

- `MLineChart`: pontos com posição no tempo (`x` como número, proporcional), valor, rótulo de eixo opcional e marca de recorde. Linha em `chart.line` (traço do token), pontos pequenos, marca de recorde como anel em `chart.pr` com o ícone `PlateRecord` na legenda. Eixo Y **não** começa em zero (faixa dos dados com folga de 10% e marcas "redondas"), porque é progressão; até 4 marcas de eixo Y e até 5 rótulos de eixo X, que somem quando não cabem (a tabela tem todos).
- `MBarChart`: barras de um valor por balde, **sempre a partir de zero**, cantos `radius.xs`, cor `chart.line`; rótulos de eixo X dos baldes (com o mesmo corte por espaço).
- `MSparkline`: linha sem eixos, 40 dp de altura, decorativa (o texto ao lado é a alternativa).
- **Seleção:** tocar ou arrastar no gráfico escolhe o ponto ou a barra mais perto; a linha de leitura acima do gráfico mostra o valor ("28 out · 92,5 kg · 85 kg × 3"), e o ponto ganha um anel `accent.solid` com uma guia vertical em `chart.grid`. A seleção some ao trocar período ou métrica. A linha de leitura não é anunciada a cada movimento (a tabela é o caminho do leitor de tela).
- **Desenho:** `drawWithCache`; o `Path` e as marcas só são refeitos quando os dados ou o tamanho mudam; nenhum bitmap intermediário. Com mais de 104 pontos no período, o modelo (no `:screens`) reduz para um ponto por semana (o máximo da métrica na semana), e a tabela diz "por semana".
- **Movimento:** trocar período ou métrica faz um cross-fade de `motion.base`; com movimento reduzido, troca na hora. Sem animação de entrada ("movimento explica, não enfeita").
- **Tokens novos** em `tokens/` com o valor fixado em `commonTest`: altura do gráfico (200 dp), altura da sparkline (40 dp), traço da linha, raio do ponto, raio da marca de recorde, vão entre barras, mínimo da célula do calendário (40 dp, D12). O `tools/check-design-tokens.ps1` passa a cobrir `charts/`.
- **Contraste:** os pares já provados (`chart.line` e `chart.pr` sobre `bg.surface`, a 3:1 ou mais) valem; cada par novo (por exemplo, `chart.line` sobre `bg.raised` se o card for elevado, a célula de dia com treino e o seu número, o anel do "hoje") entra no `ComponentColorsTest` nos dois temas.
- **Galeria:** página nova "Gráficos" com linha (com e sem recorde, 2 e 300 pontos), barras (com semana vazia), sparkline e calendário, nos dois temas, a 1,0, 1,3 e 2,0.

### Acessibilidade dos gráficos

**D4 (minha). Cada gráfico é um nó só, com um resumo falado que diz a tendência, e um botão visível "Ver dados" que troca o gráfico por uma tabela.**

- **Resumo** (rótulo do nó, montado pela tela, nos dois idiomas): o que é, o período, o primeiro e o último valor com as datas, o máximo com a data, a tendência (D10) e a contagem. Exemplos: "1RM estimado, últimos 6 meses: de 80 kg em 3 de junho a 92,5 kg em 28 de outubro; máximo 95 kg em 12 de setembro; tendência em alta; 18 treinos." / "Volume por semana, últimas 13 semanas: média de 9.800 kg; maior semana 14.200 kg, a de 6 de outubro; 2 semanas sem treino; tendência estável."
- **Tabela:** "Ver dados" (com o ícone de tabela, 48 dp, alternável com "Ver gráfico") mostra uma linha por ponto ou balde, da mais recente para a mais antiga: data ou "Semana de 6 out", o valor e, quando houver, a série ("85 kg × 3") e "Recorde". Cada linha é um nó só. A escolha fica em `rememberSaveable` por gráfico.
- Unidades por extenso na fala ("quilos", "libras"), como na fase 2; números no formato do idioma.
- O nó do gráfico tem `Role.Image`; a seleção por toque não é exigida para quem usa leitor de tela.
- **VoiceOver:** a mesma semântica do Compose Multiplatform; provada pela árvore de semântica no simulador, não com o VoiceOver de verdade (sem iPhone).

## Regras de cálculo

Tudo em kg exatos; a unidade só entra na exibição (D7). As regras vivem em funções puras com teste em `commonTest`.

**D5 (minha). 1RM estimado pela fórmula de Epley, só com séries de 1 a 10 repetições: `carga × (1 + reps / 30)`, e uma repetição vale a própria carga.**

- Entram só as séries de trabalho que o domínio já usa para o recorde de carga (`WorkoutCalculations.workingSet`: concluída e válida, não aquecimento, não assistida, convenção diferente de Assistência, carga maior que zero) e com até 10 repetições.
- O ponto da sessão é o maior 1RM estimado entre essas séries; a tabela mostra a série que o gerou ("85 kg × 3"). Sessão sem série elegível não tem ponto.
- Exibido arredondado para 0,5 kg ou 1 lb (é uma estimativa; o arredondamento é só na tela).
- **Convenção "Carga adicional" (peso corporal):** sem 1RM. A carga registrada é só o peso somado ao corpo, e o app não sabe o peso corporal; uma estimativa sobre ela seria errada. **Assistência:** sem 1RM. **Por mão** e **Um lado:** o 1RM é dos números registrados (um halter, um lado), e a legenda da convenção já diz isso, como no card do treino.
- Fica em `shared/src/commonMain/kotlin/com/mooscles/domain/EstimatedOneRepMax.kt` (`estimate(weightKg, reps): Double?`, nulo fora das regras), com vetores fixos e as bordas (0, 1, 10, 11 repetições, carga zero, não finita). *Por quê:* Epley é a fórmula mais usada em apps de treino e a mais simples de explicar; acima de cerca de 10 repetições todas as fórmulas divergem e superestimam, e a de Brzycki explode perto de 37. O domínio é o lugar das fórmulas, e o arquivo novo não toca `data/` nem `timer/`.

**D6 (minha). Métricas por convenção, com as regras de contagem que o resumo e o cartão já usam.**

| Convenção | Métricas, nesta ordem (a primeira é a padrão) |
| --- | --- |
| Carga total, Por mão, Um lado | **Carga** (a série de trabalho mais pesada da sessão, escolhida pela regra do domínio: a mais pesada, empate pelas mais repetições, depois a primeira na ordem do treino), **1RM estimado** (D5), **Volume** |
| Carga adicional | **Repetições** (o máximo de repetições numa série de trabalho da sessão), **Carga adicional** (só se alguma sessão tiver carga maior que zero), **Volume** (só se houver carga) |
| Assistência | **Repetições** e **Assistência** (a menor assistência registrada na sessão; a legenda diz "menos assistência, menos ajuda da máquina") |

- **Volume** é o de hoje (`WorkoutCalculations.volume`: carga registrada × repetições das séries concluídas e válidas, aquecimento incluído, assistência e convenção Assistência fora). É o número do resumo e do cartão; o gráfico não inventa outro.
- **Marcas de recorde (controlador, 2026-10-31):** vêm das listas de recordes do `UiState`, nunca de `SetUi.record`, e o `UiContract` e o `present()` não mudam. Um ponto ganha marca quando existe um recorde batido (não primeiro registro) do tipo da métrica para aquele exercício, grupo de convenção e sessão: recordes de carga (`WEIGHT`) no gráfico de **Carga** e no de **Carga adicional** (a convenção `BODYWEIGHT` conta como gráfico de carga para as marcas), recordes de repetições (`REPS`) no gráfico de **Repetições**. O `UiState.records` só traz o recorde vigente de cada chave; os recordes batidos de sessões passadas são os `RecordUi` de `WorkoutUi.records` de cada treino em `UiState.history`, também do `UiState` e com o mesmo formato, e o vigente marca a etiqueta "Atual". **Repetições** ("máximo de repetições numa série") conta só séries de trabalho: aquecimento fora; séries assistidas seguem os grupos de recorde que o `WorkoutCalculations` já usa (exercício, convenção e modo de assistência), então o ponto e a marca saem sempre do mesmo grupo (numa convenção que não é Assistência, o ponto de Repetições usa as séries não assistidas; na Assistência, as assistidas). 1RM e volume não marcam recorde, porque o domínio não tem recorde dessas medidas e um selo que o histórico desmente é anti-objetivo.
- **Identidade da série:** exercício (`exerciseId`) + convenção, a mesma chave dos recordes. **(controlador, 2026-10-31)** O gráfico mostra só a convenção atual do exercício (ou a mais recente do histórico, se o exercício não existe mais); sem chips de convenção. Um personalizado cuja convenção mudou mostra a linha "Os treinos registrados como Por mão não entram neste gráfico." sob o gráfico; as sessões antigas continuam na lista de sessões.
- O treino ativo nunca entra nos gráficos (o `UiState.history` só tem treinos finalizados).
- Testes de equivalência em `commonTest`: o volume de cada sessão do índice é igual à soma dos volumes por exercício do `WorkoutUi`; a soma da semana é a soma dos `WorkoutUi.volumeKg`; **(controlador)** as marcas são comparadas com os recordes do `UiState` (`UiState.records` e os `WorkoutUi.records` de `UiState.history`): toda marca corresponde a um recorde batido do mesmo tipo, exercício, grupo de convenção e sessão, e todo recorde assim tem marca no ponto da sessão.

**D7 (minha). Períodos e baldes.**

- **Gráficos gerais:** chips "4 semanas", "3 meses" (padrão), "6 meses", "1 ano", "Tudo", numa linha rolável.
  - "4 semanas", "3 meses" e "6 meses" são 4, 13 e 26 semanas, alinhadas ao início da semana (D8), terminando na semana atual (incluída, em andamento); balde **semanal**.
  - "1 ano" são os 12 meses de calendário até o mês atual; balde **mensal**.
  - "Tudo" vai do mês do primeiro treino até o mais tarde entre hoje e o último treino; balde **mensal**, ou **trimestral** com mais de 36 meses (rótulos "1º tri 2025" / "Q1 2025" nos recursos de datas).
  - Com balde mensal, os títulos mudam ("Treinos por mês", "Volume por mês"); a média por semana dos números do topo continua por semana.
- **Por exercício:** chips "3 meses", "6 meses", "1 ano", "Tudo" (padrão), contados em meses de calendário a partir de hoje (`DatePeriod(months = n)`); um ponto por sessão, com a redução do D3 acima de 104 pontos. *Por quê o padrão "Tudo":* por exercício há poucas sessões, e o que motiva é a linha longa.
- O balde atual (semana ou mês em andamento) é desenhado igual e chamado de "esta semana (em andamento)" na tabela e no resumo; nunca é comparado como se estivesse completo.
- Um treino entra no dia **local do seu início** (`startedAt`), o mesmo dia que a lista do histórico mostra. (O "nas últimas 4 semanas" do resumo da fase 3 conta pelo fim; a diferença só aparece num treino que atravessa a meia-noite, e o resumo não muda.)
- **Unidade:** kg ou lb pela preferência; o eixo calcula as marcas "redondas" depois da conversão, para que o eixo em lb não mostre 44,09. Volume e eixos de valores a partir de 10.000 usam a forma curta ("42 mil", "42k"); a tabela e o resumo usam o número inteiro com separador ("42.300 kg").

**D8 (minha). A semana começa no primeiro dia da região do aparelho, e o fuso é o do sistema pelo `kotlinx-datetime`.**

- O primeiro dia sai de uma tabela própria em Kotlin puro (`screens/.../format/WeekStart.kt`), por região, a partir do `Locale.current` do Compose (a mesma fonte do `NumberLocale`), com os dados `weekData/firstDay` do CLDR (domingo no Brasil, nos Estados Unidos e no Canadá, por exemplo; segunda na maior parte da Europa; sábado em alguns países do Oriente Médio). Sem região: domingo para `pt`, segunda para o resto. Teste de equivalência em `androidHostTest` contra `java.time.temporal.WeekFields.of(locale).firstDayOfWeek` para cada `Locale.getAvailableLocales()` com país, como os outros substitutos da JVM; uma divergência de versão do CLDR entra numa lista de exceções com o motivo. *Por quê:* o `kotlinx-datetime` não sabe o primeiro dia da semana, o `:screens` não tem `expect/actual` de propósito, e o padrão "ler o idioma por recurso" (`catalog_names_language`) só daria o idioma, não a região.
- O fuso é `TimeZone.currentSystemDefault()`, lido ao entrar na tela e a cada mudança de período; uma troca de fuso com a tela aberta vale na próxima entrada. O horário de verão é tratado pelo `kotlinx-datetime`.
- O calendário e os cabeçalhos de mês usam os nomes dos recursos do `:screens` (ganham os nomes completos dos meses e as iniciais dos dias), nunca um formatador da plataforma.

**D9 (minha). A distribuição por músculo conta séries de trabalho por região do mapa: 1 para cada região principal do exercício e 0,5 para cada secundária.**

- Séries: concluídas e válidas, **sem aquecimento**; séries assistidas entram (são esforço do músculo, ainda que não deem recorde de carga).
- Regiões: `ExerciseAnatomy.forExercise(id, muscle)` (4A) pelo `exerciseId` e o músculo do snapshot do treino. Personalizado: a região do grupo muscular, só como principal. "Corpo inteiro" (sem região) fica numa linha própria, fora do mapa ("Corpo inteiro: 6 séries").
- *Por quê séries e não volume:* volume em kg favorece pernas (cargas muito maiores) e zera o peso corporal; séries por músculo é a medida que quem treina usa. *Por quê 0,5:* conta o trabalho dos secundários sem igualá-lo ao do principal; é o peso mais comum nas contas de "séries por músculo".
- **Mapa** (`BodyMap` da 4A): as regiões com pelo menos metade das séries da região mais treinada ficam no destaque principal; as outras com alguma série, no secundário; as sem série, neutras. A legenda ganha rótulos próprios ("Mais séries", "Menos séries"); o `BodyMapLegend` passa a recebê-los como parâmetro, com os da 4A como padrão. Os pares de cor e de contraste já estão provados.
- **Lista** sob o mapa, que é o texto equivalente: as regiões com série, da mais para a menos treinada, com "Peito · 42 séries · 3,2 por semana" e uma barra horizontal proporcional (decorativa); as 6 primeiras e "Ver todas as 14" (expande no lugar). Frações com uma casa ("12,5"); a lista não mostra faixa ideal nem meta (fora de escopo).

**D10 (minha). A tendência é a inclinação da reta de mínimos quadrados sobre os pontos (ou baldes) do período, comparada à média.** Variação projetada no período (inclinação × duração) acima de +5% da média: "em alta"; abaixo de −5%: "em baixa"; entre os dois: "estável". Com menos de 3 pontos não há frase de tendência. Nos gráficos de barras, o balde em andamento fica fora da conta. A palavra aparece só no resumo falado e na tabela, nunca como selo colorido ou seta vermelha. Testes com séries fixas (subindo, caindo, plana, dois pontos, ponto único).

## Telas da 5A

Pacotes novos no `:screens`: `progress` (modelo, índice e a tela do exercício) e `records` (a aba). Só com o `:designsystem`, como nas fases 3 e 4; o `:app` só hospeda.

### Progresso do exercício (rota `exercise/{id}`)

De cima para baixo, numa `LazyColumn`:

- **Topo:** voltar e o menu ("Sobre o exercício", que abre `exerciseInfo/{id}`; se a tela de baixo na pilha já é esse detalhe, volta para ela em vez de empilhar outro). Primeira linha do conteúdo: o nome no idioma da tela (`title.md`, inteiro em qualquer tamanho, como a nota da Task 17 da 4A) e "Peito · Barra".
- **Convenção:** sem chips; a convenção atual, com a nota de convenção antiga quando houver (D6 e D2).
- **Métrica inicial:** a do argumento `metric` da rota (D2), senão a primeira da convenção.
- **Métrica:** chips `Segmented` com as métricas da convenção (D6), padrão a primeira.
- **Destaque:** o último valor em `num.lg` com a métrica ("92,5 kg · 1RM estimado") e a diferença desde o início do período, como fato: "+12,5 kg desde 3 jun" ou "−5 kg desde 3 jun" (sem cor de perigo, sem seta vermelha).
- **Período:** os chips do D7.
- **Gráfico** (`MLineChart`) com a linha de leitura, a legenda de recorde quando houver marca e "Ver dados" (D4).
- **Recordes deste exercício:** o recorde de carga atual ("100 kg × 3 · 12 set") e os de repetições por carga (até 5, os de carga mais alta, e "Ver todos"), de `UiState.records`.
- **Sessões**, da mais recente: data, nome do treino, as séries concluídas em linha ("A 40 × 10 · 80 × 8 · 80 × 8 · 82,5 × 6", com "A" no aquecimento e o selo de recorde na série que o domínio escolheu); tocar abre o detalhe do treino.
- **Estados:** carregando (o índice ainda não ficou pronto): `Skeleton` do gráfico; sem sessão: `EmptyState` "Você ainda não fez este exercício." com "Ver na biblioteca"; uma sessão: o destaque e a frase "O gráfico aparece a partir de 2 treinos." no lugar do gráfico; métrica sem ponto no período (por exemplo, 1RM com todas as séries acima de 10 repetições): "Sem séries de até 10 repetições para estimar o 1RM neste período."; exercício que não existe mais e sem histórico: `EmptyState` "Exercício não encontrado" (com histórico, a tela funciona com o nome do snapshot).

### Recordes (aba)

- **Topo:** "Recordes" e o atalho de Configurações; à direita, "Como contamos recordes" (ícone `Info`), que abre uma `MBottomSheet` com as regras de hoje traduzidas (carga: série de trabalho mais pesada; repetições: mais repetições na mesma carga, inclusive peso corporal; aquecimento e séries incompletas fora; assistência à parte).
- **Busca:** `MSearchField` "Buscar exercício" (nomes nos dois idiomas, pelo mesmo dobramento da biblioteca), fixa sob o topo.
- **Chips:** "Só atuais" (mostra só os recordes que ainda valem, inclusive os que são primeiros registros nunca batidos, o equivalente da tela de hoje; ignora o outro chip) e "Primeiros registros" (inclui os primeiros registros na linha do tempo, desligado por padrão).
- **Com texto na busca**, os recordes atuais dos exercícios achados aparecem sempre, mesmo os que são primeiros registros; assim "supino" mostra o recorde vigente do supino no topo sem precisar de chip.
- **Linha do tempo** (D11): agrupada por mês com cabeçalho fixo ("outubro de 2026 · 5 recordes"); cada linha com o `PrBadge` (ou o selo suave do primeiro registro), o nome do exercício, o tipo ("Carga" ou "Repetições"), "87,5 kg × 6 · antes 85 kg" ou "80 kg × 8 · antes 6 repetições", a data curta e a etiqueta "Atual" quando é o recorde vigente. Tocar abre o progresso do exercício na métrica do recorde (`exercise/{id}?metric=load` ou `?metric=reps`, D2); a ação de acessibilidade "Abrir treino" abre o detalhe do treino.
- **Estados:** carregando (`Skeleton`); sem recorde: `EmptyState` "Seu primeiro recorde aparece aqui" com "Conclua séries de trabalho para registrar recordes de carga e de repetições."; busca sem resultado: "Nenhum recorde encontrado".

**D11 (minha). A linha do tempo mostra os recordes batidos, do mais recente; o recorde atual tem etiqueta; primeiros registros só com o chip.** *Por quê:* um histórico de 208 treinos começa com dezenas de "primeiros registros", que não são progresso; o recorde atual, que é o que a tela de hoje mostra, continua a um toque ("Só atuais") e marcado na linha do tempo.

- Dados: `WorkoutUi.records` de cada treino do histórico (já com tipo, carga, repetições, o anterior e a data) para os eventos, e `UiState.records` para saber quais ainda valem (mesmo exercício, convenção, tipo, carga e assistência, e o mesmo `workoutId`). Nada novo no contrato.
- Ordem: data decrescente; no mesmo treino, a ordem dos exercícios no treino; carga antes de repetições.
- Os recordes de repetições têm um por carga, então "Só atuais" pode listar centenas de linhas numa base grande, como a tela de hoje; a lista é preguiçosa e a busca resolve o caso comum.

### Card "Seu histórico" do detalhe do exercício (4A)

- Ganha, com 2 sessões ou mais, uma `MSparkline` da primeira métrica da convenção nas últimas 12 sessões, ao lado do texto que já existe (que é a alternativa; a sparkline é decorativa).
- "Ver histórico completo" vira "Ver progresso" (botão do card e item do menu), para a rota `exercise/{id}` (sem `metric`, o padrão).

## Telas da 5B

Pacotes novos no `:screens`: `history` (aba, calendário, filtros, lista, segmento Progresso) e `workoutdetail` (detalhe e edição); a prévia fica em `share`.

### Histórico, segmento Treinos

- **Topo:** "Histórico" e o atalho de Configurações; os segmentos (D2).
- **Calendário** (D12), no topo da lista, rolando com ela.
- **Filtros** (linha rolável de chips, sob o calendário; com "E" entre eles):
  - `MSearchField` "Buscar treino ou exercício": nome do treino e nomes dos exercícios nos dois idiomas (nome mostrado, nome do catálogo em inglês e em pt-BR, pelo `SearchFolding`); todas as palavras precisam estar no mesmo treino. A busca por data em texto de hoje sai: o calendário a substitui;
  - "Rotina" (`MChipKind.Menu`): sheet com as rotinas que existem e têm treino no histórico, na ordem das pastas, mais "Outros treinos" (sem rotina ou com a rotina excluída); seleção múltipla, "OU";
  - "Exercício" (`MChipKind.Menu`): o seletor de exercício (`ExercisePickerSheet` da 4A, seleção única) restrito aos exercícios que aparecem no histórico;
  - "Com recorde" (alternável): treinos com pelo menos um recorde batido (não só primeiro registro);
  - o dia escolhido no calendário aparece como chip "29 out" com o X para limpar;
  - "Limpar filtros" quando há algum.
- **Contagem:** "36 treinos" (`caption`, anunciada de forma educada quando muda).
- **Lista:** `LazyColumn` agrupada por mês, com cabeçalho fixo ("outubro de 2026 · 12 treinos"). Cada treino é um card de um nó só: à esquerda o dia em `num.md` e o dia da semana abreviado; à direita o nome (`title.sm`, até 2 linhas), "52 min · 18 séries · 8.400 kg" (números em `num.sm`), até 3 nomes de exercício (os primeiros com série concluída, no idioma da tela) e, havendo recorde batido, o `PrBadge` pequeno com "2 recordes". Tocar abre o detalhe. A tag `history-list` continua na lista.
- **Estados:** carregando (`Skeleton` de cards); sem treino nenhum: o calendário e os filtros somem e fica o `EmptyState` "Seus treinos aparecem aqui" com "Cada treino finalizado aparece aqui, com as séries e os recordes." e "Ir para Treinar"; filtro sem resultado: "Nenhum treino encontrado" com "Limpar filtros".

**D12 (minha). Calendário mensal, não um mapa de calor anual.** Um card com o mês ("outubro de 2026 · 12 treinos"), os botões de mês anterior e seguinte (48 dp, "Mês anterior" e "Próximo mês"; o seguinte some no mês atual e o anterior some no mês do primeiro treino); abre no mês atual, as iniciais dos dias a partir do primeiro dia da semana (D8) e a grade do mês. Dia com treino: disco `accent.subtle` com o número em negrito e, com 2 ou mais treinos, um ponto abaixo (forma, não só cor); hoje: anel `border.strong`. Deslizar na horizontal também troca o mês. Um alternável "Recolher calendário" reduz o card à linha do mês (em `rememberSaveable`). *Por quê:* o calendário é também a ferramenta de achar um treino por data (substitui a busca por data); um mapa de calor de 52 semanas é pequeno demais para tocar e convida à leitura de "sequência quebrada", que é anti-objetivo; a frequência ao longo do tempo já está no gráfico de treinos por semana.

- **Toque num dia:** com um treino, abre o detalhe direto; com mais de um, filtra a lista por esse dia (chip "29 out"). Dias sem treino não são tocáveis.
- **Alvos:** a célula tem a largura da coluna, de no mínimo 40 dp (7 colunas a 320 dp com as margens de 16 dp). É a **única exceção** ao alvo de 48 dp da fase 2, documentada como tal: cada treino do dia também é alcançável pela lista e pelo filtro, a alternativa equivalente que o WCAG 2.5.8 aceita. A 412 dp a célula passa de 48 dp.
- **Leitura:** o card tem um nó de resumo ("Outubro de 2026: 12 treinos, nos dias 1, 3, 6…"); cada dia com treino é um botão ("29 de outubro, 1 treino: Treino A"); os outros dias não são focáveis.
- **Fonte grande:** os números em `num.sm` escalam; a 2,0 e a 320 dp a célula cresce na altura e nenhum número é cortado (spike S2). O componente fica no `:designsystem` (`charts/MonthCalendar`), recebendo o número de dias, o deslocamento do primeiro dia e as marcas, sem `kotlinx-datetime`.

### Histórico, segmento Progresso

- **Período:** chips do D7.
- **Números do período** (grade 2 × 2, uma coluna a partir de 1,5): Treinos, Por semana (média de treinos), Séries (concluídas e válidas), Volume.
- **Treinos por semana** (`MBarChart`, marcas de eixo só em inteiros) e a frase "11 de 13 semanas com treino" (D17).
- **Volume por semana** (`MBarChart`), com a legenda "carga registrada × repetições".
- **Músculos** (D9): mapa, legenda e lista.
- **Mais feitos:** os 5 exercícios com mais sessões no período ("Supino reto com barra · 12 treinos"; empate pela sessão mais recente); tocar abre o progresso do exercício.
- **Estados:** sem treino nenhum: o mesmo `EmptyState` do segmento Treinos; período sem treino: "Nenhum treino neste período" com o atalho "Ver tudo"; um treino só: os números e as barras com um balde, e a frase de tendência não aparece (D10).

### Detalhe do treino (`workoutDetail/{id}?summary=false`)

**D13 (minha). Uma tela nova de leitura, com um modo de edição que faz exatamente o que o antigo faz, nas peças da fase 3.**

- **Topo:** voltar e o menu ("Renomear", "Compartilhar", "Excluir treino").
- **Cabeçalho:** o nome (`title.md`, primeira linha do conteúdo), a data e o horário ("ter., 29 out · 18:02–18:44", a mesma função do resumo) e, quando o treino veio de uma rotina que ainda existe, o chip "Rotina: Treino A" (abre o editor).
- **Números:** Duração, Séries, Volume e Exercícios, com as regras do resumo (`SummaryStats`).
- **Recordes:** as mesmas seções do resumo ("Recordes" e "Primeiros registros"); a contagem de exercícios com recorde conta cada exercício uma vez, e primeiros registros à parte (as regras que o `HistoryRecordsHostTest` prova hoje).
- **Exercícios:** um card segmentado por exercício (o `ExerciseCard` do design system, sem ações de treino): mídia de 56 dp, nome no idioma da tela, "Peito · Barra", a legenda da convenção quando não for "Carga total", as séries em leitura (rótulo com número ou letra do tipo, carga × repetições, selo de recorde na série escolhida pelo domínio; séries não concluídas apagadas com "não concluída" na fala), o volume do exercício e "Ver progresso".
- **Ações no fim:** "Editar treino" (secundário L), "Compartilhar" (secundário L, com ícone) e "Excluir" (botão de ícone de perigo, 48 dp, com o rótulo "Excluir treino"). *Por quê visíveis e não só no menu:* o orçamento de toques (editar e excluir hoje custam o mesmo número de toques com botões visíveis).
- **Excluir:** `MDialog` destrutivo "Excluir este treino? Ele sai do histórico e os recordes são recalculados." Depois, volta para onde estava.
- **Estados:** treino que não existe mais (excluído noutra tela ou por import): `EmptyState` "Treino não encontrado" com "Voltar ao histórico".
- O resumo de fim (fase 3) não muda; o "Ver detalhes" dele passa a abrir esta tela.

**Modo de edição:**

- Entrar pede a confirmação de hoje, traduzida: `MDialog` "Editar treino salvo?" com "As mudanças são salvas na hora e os recordes deste exercício são recalculados em todo o histórico." e "Editar treino". O topo passa a "Editando" com "Concluir"; o voltar do sistema, no modo de edição, só sai do modo (as mudanças já estão salvas).
- **Paridade com o antigo** (o plano lista cada item e um teste prova cada um): editar carga e repetições (toque põe a linha em edição com o `Stepper`, segundo toque abre o teclado, como o D12 da fase 3), concluir e desfazer a conclusão pelo check, mudar o tipo (Normal, Aquecimento, Assistida, pela sheet de tipo da fase 3), adicionar série (copia a última, o `addSet` de hoje), remover série, mover o exercício para cima e para baixo, trocar e remover o exercício, adicionar exercício (o seletor da 4A, seleção única), renomear e **editar o descanso do exercício**. **(controlador, 2026-10-31)** O detalhe antigo edita o descanso hoje (`HistoryScreens.kt` → `WorkoutExerciseCard` → `WorkoutRestDialog` → `updateWorkoutExerciseRest`), então, por paridade, no modo de edição cada exercício ganha o chip de descanso que abre a `RestSheet` da fase 3 e grava por `updateWorkoutExerciseRest`. Fora do modo de edição o descanso não aparece, e nenhum timer é iniciado.
- **Remover série sem desfazer, com confirmação:** `MDialog` "Remover a série 3? Os recordes deste exercício são recalculados." *Por quê:* o `restoreSet` exige treino ativo (fase 3); estender o desfazer ao histórico mudaria o repositório (`data/`) por um caso raro. Remover exercício também confirma, como no treino.
- **Nada do treino ativo:** sem timer (o `updateSet` só inicia o descanso com o treino `ACTIVE`, regra que ganha um teste com o modo de edição), sem sugestão, sem aviso de recorde nem háptico de recorde (o selo aparece ou some na série conforme o domínio recalcula).
- Erros pelo `UiError` localizado, como o treino ativo (`ScreenErrorToast`); um treino bloqueado por instalação de atualização mostra o texto de hoje.

### Prévia de compartilhamento

**D14 (minha). Só a prévia muda: uma sheet de tela cheia do design system, traduzida, no `:screens`; o cartão, o arquivo e o envio continuam como na fase 3.**

- `SharePreviewSheet` em `com.mooscles.screens.share`, sobre o `MOverlayWindow`: título "Prévia do treino", as páginas do cartão (`ImageBitmap`, com "Página 1 de 2" quando houver mais de uma), a dica "Você escolhe quem recebe no próximo app e confirma o envio.", e os botões: "Compartilhar no WhatsApp" ou "no WhatsApp Business" (primário L, quando instalado), o outro WhatsApp (secundário L, quando os dois estão instalados) e "Escolher app" ou "Outros apps" (secundário L), além de fechar. Estados de carregando ("Criando a imagem do treino…", com "Cancelar"), falha (com "Tentar de novo") e página que não abre — os mesmos de hoje.
- O `:app` continua dono de ler o snapshot, chamar o `ShareCardPainter`, gravar os PNG (`WorkoutCardRenderer.export`), decodificar as páginas, o `FileProvider`, a limpeza de 7 dias e a detecção do WhatsApp (`WorkoutShareIntent`); a sheet recebe o estado e devolve o destino escolhido. As tags de hoje (`share-preview-close`, `share-preview-retry`, `share-whatsapp`, `share-whatsapp-business`, `share-other-apps`) continuam.
- Os textos saem do `res/values/share_strings.xml` do `:app` (que some) para o `strings_share.xml` do `:screens`, em en e pt-BR. O título do seletor do sistema ("Compartilhar treino") também vem do `:screens`, passado pelo `:app`.
- Abre do resumo (como hoje) e do detalhe novo.

## Arquitetura

**D15 (minha). Tudo é derivado em memória do `UiState` que já existe, por um índice do `:screens` construído fora da thread principal, sem mudar o contrato de UI.**

- **`ProgressIndex`** (`com.mooscles.screens.progress`): construído a partir de `UiState.history`, `UiState.records` e `UiState.exercises`, uma vez por **identidade** de `UiState.history` (o `WorkoutPresentation` reaproveita a lista quando o histórico não muda; ela muda ao finalizar, editar, excluir, importar e ao trocar a unidade). Guarda, por exercício e convenção, os pontos por sessão (data, carga, série da carga, 1RM e série do 1RM, repetições, assistência, volume, marca de recorde); por treino, o início em epoch (sem data local: o fuso só entra nas consultas), o volume, as séries concluídas e válidas, as séries por região (D9), a rotina de origem, se tem recorde batido e o texto dobrado de busca.
- **Consultas** (período, balde, métrica, filtros do histórico, linha do tempo) são funções puras sobre o índice, com o fuso e o primeiro dia da semana como parâmetros, executadas em `Dispatchers.Default` e memorizadas pela chave (índice, consulta). A composição só lê o resultado pronto; até ele chegar, a tela mostra o `Skeleton` da seção.
- **Cache único**, no padrão do `rememberLibraryModel` da 4A: um objeto que o `:app` guarda acima do `NavHost` e passa às rotas (Histórico, Recordes, progresso do exercício, detalhe do exercício). Ele começa a construir o índice no fundo, 500 ms depois de cada nova identidade do histórico, para que as abas abram com ele pronto. Durante um treino ativo o histórico não muda, então nada é construído no meio de uma série.
- **Por que não no `WorkoutPresentation`:** os baldes precisam do fuso e do primeiro dia da semana, que são da tela, e do `kotlinx-datetime`, que só o `:screens` tem; pôr isso no `:shared` acrescentaria uma dependência ao `shared/build.gradle.kts` e campos ao `UiState` (com a lista de estabilidade) para algo que só estas telas leem. O `present()` fica como está.
- **Domínio:** só o `EstimatedOneRepMax` entra no `:shared` (`domain/`). As regras de série de trabalho e de volume são as do `WorkoutCalculations` (D6); se o índice precisar de `workingSet`, que é privada, ela passa a pública (o `:screens` não enxerga `internal` do `:shared`) sem mudar o comportamento, com os testes atuais do domínio cobrindo.
- **Rotas no `:app`:** `ShellTabs.HISTORY`, `ShellTabs.RECORDS`, `exercise/{id}` e `workoutDetail/{id}?summary=false` passam a chamar as telas novas; todas entram em `OWN_PLACEHOLDER_ROUTES` (topo, erros e carregamento próprios; o snackbar global e a barra "mooscles" somem nelas). O `MissingScreen` antigo sai com elas.
- **Estado de tela** em `rememberSaveable`: segmento, período, métrica, convenção, filtros, dia, mês do calendário, calendário recolhido, "Ver dados", rolagem. Nada é gravado.
- **Estabilidade do Compose:** nenhuma classe nova do `:shared` vira parâmetro de composable; os modelos novos do `:screens` são imutáveis e anotados ou estáveis por construção (o plano confere com o relatório do compilador).

## Constância sem culpa

**D17 (minha). A única medida de constância é "N de M semanas com treino" no período escolhido, mais a frase do resumo que já existe ("12 treinos nas últimas 4 semanas").** Nada de "sequência de N dias", recorde de sequência, aviso de semana sem treino ou cor de alerta numa semana vazia (a barra vazia é só ausência de barra). *Por quê:* a P1 aponta sequências que zeram como desmotivadoras e recomenda janela móvel; a voz da fase 2 proíbe cobrar ausência.

## Acessibilidade, fonte grande e movimento

- Todo clicável com rótulo e alvo de 48 dp (exceção única: as células do calendário, D12). A varredura de acessibilidade (como `LibraryAccessibilitySweepTest`) ganha as telas novas: Histórico (os dois segmentos), Recordes, progresso do exercício, detalhe do treino (leitura e edição) e a prévia, nos dois temas, a 1,0, 1,3 e 2,0 (mais 2,01 no Android), a 320 e 412 dp, em três posições de rolagem.
- **Ordem de leitura:** topo, segmentos, calendário (resumo, depois os dias), filtros, contagem, lista. No progresso do exercício: nome, convenção, métrica, destaque, período, gráfico (resumo), "Ver dados", recordes, sessões.
- **Linhas:** um nó por card de treino ("ter., 29 de outubro, Treino A, 52 minutos, 18 séries, 8.400 quilos, 2 recordes"), por recorde da linha do tempo e por linha de tabela.
- **Chips:** `Role.Checkbox` (filtros), `Role.Tab` (segmentos e métricas), `Role.RadioButton` (períodos), com estado; as sheets prendem o foco e o devolvem ao chip.
- **Gráficos:** D4. **Mapa:** a lista é o equivalente (D9).
- **Fonte grande:** a 200%, a grade de números vira uma coluna; os chips continuam roláveis; os eixos perdem rótulos antes de sobrepor (a tabela tem todos); o gráfico cresce em altura com a escala da fonte, até 1,5 vez o token; nenhum número cortado (`assertShownWhole`).
- **Movimento reduzido:** troca de período e métrica sem cross-fade; deslizar o calendário troca de mês sem animação; nenhum movimento novo além do cross-fade.
- **Cor nunca sozinha:** recorde = anel com ícone e a palavra na tabela; dia com treino = disco e negrito; mais de um treino = ponto; tendência = palavra.

## Textos e idiomas

- Strings novas no `:screens`, em `values/` (inglês, a base) e `values-pt-rBR/`: `strings_history.xml`, `strings_progress.xml`, `strings_records.xml`, `strings_workout_detail.xml`; acréscimos em `strings_dates.xml` (nomes completos dos meses, iniciais dos dias, "outubro de 2026" / "October 2026", "Semana de 6 out" / "Week of Oct 6", datas por extenso para a fala) e em `strings_share.xml` (a prévia). Plurais por `pluralCount`. Componentes de gráfico e calendário no `strings_components.xml` do `:designsystem` (`ds_chart_*`, `ds_calendar_*`). O `StringsParityTest` dos dois módulos cobre tudo.
- O formatador só troca `%N$s` e `%N$d` (regra do AGENTS.md): números chegam formatados como texto.
- Nomes de exercício pelo `ExerciseNames.display` (catálogo no idioma da tela, personalizado pelo nome digitado), também nos treinos antigos, como a fase 4 fez nas telas novas.
- A legenda de cada convenção é a do card do treino (enum traduzido pela tela).

Amostras (voz da fase 2):

| Situação | pt-BR | en |
| --- | --- | --- |
| Segmentos | Treinos · Progresso | Workouts · Progress |
| Cabeçalho do mês | outubro de 2026 · 12 treinos | October 2026 · 12 workouts |
| Card do treino | 52 min · 18 séries · 8.400 kg | 52 min · 18 sets · 8,400 kg |
| Constância | 11 de 13 semanas com treino | 11 of 13 weeks with a workout |
| Destaque | +12,5 kg desde 3 jun | +12.5 kg since Jun 3 |
| Uma sessão | O gráfico aparece a partir de 2 treinos. | The chart appears after 2 workouts. |
| Sem 1RM | Sem séries de até 10 repetições para estimar o 1RM neste período. | No sets of 10 reps or fewer to estimate a 1RM in this period. |
| Músculo | Peito · 42 séries · 3,2 por semana | Chest · 42 sets · 3.2 per week |
| Recorde | 87,5 kg × 6 · antes 85 kg | 87.5 kg × 6 · was 85 kg |
| Recorde de repetições | 80 kg × 8 · antes 6 repetições | 80 kg × 8 · was 6 reps |
| Editar | Editar treino salvo? | Edit saved workout? |
| Remover série | Remover a série 3? Os recordes deste exercício são recalculados. | Remove set 3? This exercise’s records are recalculated. |
| Vazio | Seus treinos aparecem aqui | Your workouts appear here |
| Prévia | Criando a imagem do treino… | Creating your workout image… |

## Dados e compatibilidade

**D16 (minha). Nenhuma mudança de schema, de DataStore, de backup, de manifesto ou de rede.**

| Candidato | Precisa guardar? | Decisão |
| --- | --- | --- |
| Pontos dos gráficos, 1RM, baldes, distribuição | não | derivados do histórico em memória (D15); recalcular custa milissegundos e nunca fica desatualizado |
| Linha do tempo de recordes | não | os recordes já são recalculados das séries a cada mudança (fase 0) |
| Período, segmento, filtros, calendário recolhido | não | estado de tela em `rememberSaveable`; uma preferência nova não compensa uma chave de DataStore (que é contrato) |
| Primeiro dia da semana | não | da região do aparelho (D8) |

- Room continua na **v6**; nenhum JSON novo em `shared/schemas`; nenhuma migração.
- Nenhuma chave nova ou renomeada em `AppPreferences` nem no `RestTimerPersistence`; `local_preferences.preferences_pb` com o mesmo MD5 depois da atualização.
- A edição do treino salvo usa as ações e o repositório de hoje (`updateSet`, `addSet`, `removeSet`, `addWorkoutExercise`, `replaceWorkoutExercise`, `removeWorkoutExercise`, `moveWorkoutExercise`, `updateWorkoutExerciseRest`, `renameWorkout`, `deleteWorkout`), sem mudança no `data/`.
- O backup não muda (nem o formato, nem o conteúdo).
- Nenhuma permissão, `<queries>` ou caminho de `FileProvider` novo; `applicationId`, assinatura, `versionCode`, `update.json` e `ReleaseUrls.REPOSITORY` não mudam.
- **`compat-reviewer`:** só é obrigatório se a etapa tocar um dos caminhos da regra (`data/`, `timer/`, `shared/src/androidMain`, `updates/`, `AppPreferences.kt`, `shared/build.gradle.kts`, `app/build.gradle.kts`). O esperado é não tocar nenhum (o `EstimatedOneRepMax` e uma eventual `workingSet` pública ficam em `domain/`, fora da lista); o relatório de verificação de cada etapa mostra o `git diff --stat main -- <esses caminhos>` vazio. Se algum entrar, roda o revisor.

## Desempenho

Base: [PERFORMANCE.md](../../../PERFORMANCE.md) e a base grande (208 treinos, 12.688 séries, a variante `LargeHistoryFixture.variedExercises(48)` no host e o arquivo `mooscles-large-208.json` no emulador), medida no build R8 (`assemblePerformance`) no `Mooscles_Pixel8_API35`.

| Medida | Orçamento |
| --- | --- |
| Construir o `ProgressIndex` (base grande, 48 exercícios) | mediana até 60 ms e p95 até 120 ms no emulador, em `Dispatchers.Default`; no host, um teste de tempo com folga larga e mediana de várias rodadas (como o `LibraryModelTimingTest`) |
| Uma consulta (trocar período, métrica, filtro, mês) | mediana até 4 ms e p95 até 8 ms no emulador |
| Uma tecla na busca do histórico ou dos recordes | a mesma da consulta |
| Tocar na aba Histórico ou Recordes até a lista | o segundo quadro (a casca aplica a rota um quadro depois, nota da Task 22 da 4A), com o índice já pronto pelo cache |
| Tocar num recorde até o gráfico do exercício desenhado | até 2 quadros com o índice pronto; com o índice ainda em construção, o `Skeleton` no segundo quadro e o gráfico assim que ele termina |
| Desenhar o gráfico de 300 pontos | sem bitmap intermediário; o `Path` só é refeito quando dados ou tamanho mudam; arrastar a seleção não refaz o `Path` |
| Rolagem rápida do Histórico (208 cards), do segmento Progresso e da linha do tempo | quadros lentos (`gfxinfo` janky) até 5% |
| `present()` | sem mudança (esta fase não toca a apresentação); a rodada final remede o `present()` completo da base grande, pendência da 4B |
| Treino ativo | sem piora contra a fase 3: nada desta fase roda durante uma série (o índice só é refeito quando o histórico muda) |
| Trocar kg/lb | o índice é refeito uma vez (a lista do histórico muda de identidade); o tempo entra no relatório |

**"Os gráficos abrem rápido" (Pronto quando do ROADMAP)** passa a significar as linhas "Construir o índice", "Tocar num recorde até o gráfico" e "Uma consulta" desta tabela, medidas com a base grande.

## Orçamento de APK

**D18 (minha). Até +512 KiB (524.288 bytes) sobre o APK debug do CI verde da `main` no começo da 5A, somando 5A e 5B; a 5A mira até 256 KiB.** O `tools/apk-budget.json` passa a ter essa base (`baseCommit`, `baseApkSource`, `baseApkBytes`) e `maxGrowthBytes` de 524.288; os tetos de fontes e de ícones não mudam. *Por quê:* a fase 4 gastou cerca de 800 KB de 1 MiB com 285 figuras, o mapa e as dicas em JSON; esta fase não traz biblioteca nem mídia, só código de telas, três componentes, cerca de cinco ícones Lucide (`ChevronLeft`, `Calendar`, `Table`, `ChartLine` e o que o plano achar necessário) e strings. Um orçamento menor que o da fase 4 deixa claro que um "Vico só para testar" não cabe.

## Orçamento de toques

Contagens "hoje" pela leitura do código das telas antigas; o plano mede na `main` com o mesmo roteiro antes de trocar cada tela, e o `TapBudgetTest` fixa os números novos. Nenhum fluxo novo pode passar do de hoje.

| Fluxo | Hoje | Novo |
| --- | --- | --- |
| Ver o recorde atual de um exercício | 1 + digitar (aba, busca) | 1 + digitar (aba, busca; o recorde mais recente do exercício, com "Atual", fica no topo) |
| Do recorde ao histórico do exercício | 2 (aba, card) | 2 (aba, linha), já com o gráfico (na 5A, as sessões dele ainda abrem o detalhe antigo) |
| Ver o 1RM estimado do supino | não existe | 3 (aba Recordes, linha, chip "1RM estimado") |
| Ver volume por semana | não existe | 2 (aba Histórico, "Progresso") |
| Abrir o último treino | 2 (aba, card) | 2 (aba, card) |
| Abrir o treino de uma data do mês atual | 2 + digitar a data em inglês (aba, busca, card) | 2 (aba, dia), +1 por mês voltado |
| Ver só os treinos com recorde | não existe | 2 (aba, chip) |
| Editar uma série de um treino salvo | 5 + digitar (aba, card, "Edit session", "Edit workout", campo) | 5 + stepper ou digitar (aba, card, "Editar treino", "Editar", linha) |
| Renomear um treino salvo | 5 + digitar (aba, card, "Edit session", confirmar, "Rename") | 4 + digitar (aba, card, menu, "Renomear") |
| Excluir um treino salvo | 4 (aba, card, lixeira, confirmar) | 4 (aba, card, "Excluir", confirmar) |
| Compartilhar um treino do histórico | 3 + destino (aba, card, "Share workout") | 3 + destino (aba, card, "Compartilhar") |

## Spikes, antes de comprometer

1. **S1, fundação dos gráficos (5A, a primeira task).** `MLineChart`, `MBarChart` e `MSparkline` com os tokens novos, a semântica do D4 (nó de resumo, "Ver dados" e a tabela montada por uma tela de teste) e a página "Gráficos" da galeria. **Aprova** se, no emulador e no simulador iOS: (a) as capturas dos três nos dois temas a 1,0 e 2,0 não mostram texto cortado nem sobreposto; (b) a árvore de semântica tem o resumo no nó do gráfico e a tabela troca e volta pelo botão, nas duas plataformas; (c) a linha de 300 pontos desenha sem quadro lento numa rolagem de teste e o `Path` não é refeito ao mover a seleção (contador no teste); (d) o APK debug cresce até 60 KiB pelos três componentes. **Reprova** em qualquer item; aí o plano mede o Vico na versão compatível com Kotlin 2.4.21 e CMP 1.12.2 (tamanho no APK debug, leitor de tela e os mesmos itens) num branch descartável, e a escolha volta a esta spec com uma nota datada. O spike fica num relatório em `reports/`.
2. **S2, calendário estreito (5B, antes da aba).** O `MonthCalendar` a 320 dp e a 2,0, nos dois temas, nas duas plataformas, com um mês de 6 semanas e um de 4. **Aprova** se nenhum número é cortado, a célula tem pelo menos 40 dp, os dias com treino são botões com o rótulo do D12 e os outros não são focáveis. **Reprova:** a 320 dp ou a partir de 1,5 o calendário vira só visualização (sem toque nos dias, com o nó de resumo), a lista com o filtro de dia (pelo menu do chip "Dia") vira o caminho, e o orçamento de toques da data passa a 3, registrado com nota.

## Como verificar

A cadência é a pedida pelo dono para a fase 5 (2026-10-31).

**Em cada task:**

- testes de host focados no que a task tocou (`:shared:testAndroidHostTest`, `:screens:testAndroidHostTest`, `:designsystem:testAndroidHostTest`, `:app:testDebugUnitTest --tests …`);
- a compilação dos testes iOS dos módulos tocados (`compileTestKotlinIosSimulatorArm64` ou o equivalente que o plano fixar), já que os testes iOS só rodam no CI;
- no emulador, só a classe de UI tocada (`-Pandroid.testInstrumentationRunnerArguments.class=…`), e só quando a task muda comportamento de tela;
- revisão de código por risco, como nas fases anteriores (completa nas tasks de cálculo, navegação e edição do histórico; curta nas de texto e testes);
- `tools/check-design-tokens.ps1` quando a task mexe em tela ou componente.

**O que os testes cobrem** (`commonTest`, salvo indicação):

- `EstimatedOneRepMax` (vetores e bordas, D5);
- o `ProgressIndex` e as consultas: métricas por convenção (D6), equivalência de volume, séries e recordes com o `WorkoutUi` e o `RecordUi` (D6), baldes semanais e mensais com o primeiro dia da semana em domingo, segunda e sábado, a virada do horário de verão (fuso `America/Sao_Paulo` antigo e `Europe/Berlin`) e um treino perto da meia-noite (D7, D8), a redução acima de 104 pontos, a distribuição muscular com principal, secundário, "Corpo inteiro" e personalizado (D9), a tendência (D10), a linha do tempo e "Atual" (D11), os filtros do histórico (busca bilíngue, rotina com rotina excluída, exercício, recorde, dia);
- `WeekStart` contra `WeekFields` da JVM (`androidHostTest`, D8);
- as marcas "redondas" do eixo em kg e lb e a forma curta dos números;
- o tempo do índice e das consultas com a base grande (`androidHostTest`, folga larga);
- `StringsParityTest` dos dois módulos e os tokens novos fixados;
- **`uiTest`** (emulador e simulador): os componentes de gráfico e o calendário (semântica, tabela, seleção, recomposição: a seleção não recompõe a lista, por `RecompositionProbe`), o progresso do exercício (métricas, convenção, períodos, uma sessão, sem 1RM, sem sessão), Recordes (busca, chips, "Atual", "Como contamos recordes"), Histórico (calendário, dia com um e com dois treinos, filtros, vazio), o segmento Progresso, o detalhe (leitura, cada ação de edição do D13, inclusive o descanso, confirmação de remover, excluir, treino que sumiu, o timer nunca inicia ao concluir uma série no modo de edição), a prévia (estados e destinos); o estado de tela de cada rota nova (segmento, período, métrica, filtros, mês, "Ver dados") sobrevive à recriação pelo `ScreenRestoration`, nas duas plataformas; a varredura de acessibilidade com as telas novas; o `TapBudgetTest` com os fluxos da tabela;
- **ponta a ponta no simulador** (como o `LibraryFlowTest`): `ProgressFlowTest` na 5A (histórico semeado no Room real; Recordes, buscar em pt-BR, abrir o exercício, trocar para 1RM, "Ver dados") e `HistoryFlowTest` na 5B (calendário, dia, detalhe, editar uma série e ver o recorde recalculado na linha do tempo, segmento Progresso, compartilhar até a prévia), sobre o `MoosclesViewModel`, o Room e o DataStore reais, com capturas no artefato do job `ios`;
- **host do `:app`:** a navegação nova (aba, detalhe, edição, voltar; Recordes até o exercício e até o treino; dia com um treino abre o detalhe; "Ver detalhes" do resumo abre o detalhe novo; o progresso aberto do treino ativo volta a ele) e a prévia (`WorkoutSharePreviewHostTest` portado).

**Uma vez, no fim da fase** (depois da 5B; as correções entram num bloco só):

- as suítes de dispositivo inteiras do `:screens` e do `:designsystem` no emulador;
- **atualização por cima**, rede desligada: com o APK da `main` do começo da 5A, em pt-BR e em libras, com a base grande importada, um treino ativo com séries concluídas e o descanso correndo; instalar o APK da fase por cima. Conferir: o treino ativo e o timer intactos; o histórico, os recordes e os números iguais aos de antes (contagem de treinos, séries, recordes, volume de três treinos escolhidos); o banco na v6 sem mudança de schema; o `local_preferences.preferences_pb` com o mesmo MD5; o export depois idêntico ao de antes, tirando `exportedAt`;
- **sem rede:** com a rede desligada desde a instalação, todas as telas novas funcionam; uma revisão do diff confirma que não há código de rede novo;
- **desempenho:** a tabela de desempenho no build R8, pelo procedimento do PERFORMANCE.md, mais o `present()` completo da base grande;
- **tamanho:** `tools/check-apk-budget.ps1` dentro do D18, no build limpo do CI;
- **capturas e crítica:** Histórico (os dois segmentos, com e sem filtro, vazio), calendário (mês cheio, 6 semanas), Recordes, progresso do exercício (cada convenção, uma sessão, tabela), detalhe (leitura e edição) e prévia, nos dois temas, em en e pt-BR e a 200%, mais as capturas do iOS do CI; `critique-screen` sobre elas, com os achados corrigidos ou adiados com motivo em `reports/AAAA-MM-DD-phase5-critica.md`;
- **CI:** os mínimos (`MIN_HOST_TESTS`, `MIN_IOS_TESTS`, `MIN_DS_IOS_TESTS`, `MIN_SCREENS_IOS_TESTS`) sobem na mesma mudança que acrescenta testes, e um teste aposentado só desconta com o motivo no commit (D19);
- **relatórios:** `reports/AAAA-MM-DD-phase5a-verificacao.md` (o que a 5A provou por task e no CI) e `reports/AAAA-MM-DD-phase5-verificacao.md` (o fim da fase).

### O que não é verificado (a menos que o dono teste)

- Tudo num iPhone, inclusive o VoiceOver de verdade lendo os resumos dos gráficos.
- Num Android físico: TalkBack, gesto de arrastar no gráfico e deslizar no calendário com o dedo, 120 Hz e o desempenho no S23 Ultra.
- Se os gráficos e a linha do tempo motivam quem treina (só a crítica de tela e a leitura das pesquisas sustentam isso).
- A qualidade do 1RM estimado para quem treina de verdade (a fórmula é a usual, não uma medida).
- A release com a chave original (fora deste Mac).

### Adaptações do "Pronto quando" do ROADMAP

- **"Os gráficos abrem rápido com a base de teste grande":** medido pelas linhas da tabela de desempenho indicadas acima, no emulador e no build R8; o aparelho físico fica não verificado.
- **"Os gráficos são acessíveis, com descrição para leitor de tela":** cada gráfico tem o resumo com a tendência e a tabela (D4), provados pela árvore de semântica nas duas plataformas; uma sessão real de TalkBack ou VoiceOver depende do dono.
- **Entrega "calendário ou heatmap":** calendário mensal (D12), com a frequência no tempo no gráfico de treinos por semana.
- **Entrega "cartão de compartilhamento alinhado à nova identidade":** o cartão já foi feito na fase 3 (D20 dela); esta fase fecha a prévia (D14), a última peça antiga do fluxo.

## Telas antigas e testes do `:app`

**D19 (minha). Cada tela antiga sai na etapa em que a nova cobre o que ela fazia; o que fica sem chamador sai junto, com o teste que só o cobria, e o motivo vai no commit e no plano.**

| Peça antiga (`:app`) | Sai em | Substituta |
| --- | --- | --- |
| `RecordsScreen` | 5A | aba Recordes nova |
| `ExerciseHistoryScreen` | 5A | progresso do exercício |
| `HistoryScreen` e `WorkoutHistoryCard` | 5B | aba Histórico nova |
| `WorkoutDetailScreen`, `RecordMilestone`, `recordAchievementText` | 5B | detalhe do treino novo |
| `WorkoutScreens.kt` inteiro (`WorkoutExerciseCard`, `SetEditor`, `CompletedSetRow`, `WorkoutRestDialog`, `conventionExplanation`) | 5B | cards do detalhe novo e as peças da fase 3 |
| `LibraryScreens.kt` (`SearchField`, `ChoiceFilter`, `ExercisePicker`, `CustomExerciseDialog`) | 5B (o que só o histórico usava) | `MSearchField`, chips e `ExercisePickerSheet` da 4A; o formulário da 4A |
| `Common.kt`: o que ficar sem chamador (`PageHeading`, `Metric`, `SectionTitle`, `ConfirmDialog`, `NameDialog`, `TrainingNumberField`, `RestDurationField`, `EmptyState`, os formatadores) | 5B | design system |
| A UI do `WorkoutSharePreview` e `res/values*/share_strings.xml` | 5B | `SharePreviewSheet` e `strings_share.xml` do `:screens` |
| `MissingScreen` do `MoosclesApp` | 5B | os `EmptyState` das telas novas |

O `WorkoutCardRenderer` (exportação dos PNG) e o `WorkoutShareIntent` ficam.

| Teste do `:app` | Destino |
| --- | --- |
| `HistoryRecordsHostTest` (5 testes, as seções de recorde do detalhe antigo) | **portado**: as regras de contagem (exercício contado uma vez, primeiros registros à parte, os 4 cenários) viram testes em `commonTest` da função que monta as seções e um teste de UI do detalhe novo; o host test sai (5B) |
| `InlineRestEditorHostTest` (2) | **portado** na 5B **(controlador)**: o detalhe antigo edita o descanso; os dois cenários (editar só o descanso do seu exercício, e cancelar mantém o valor salvo; o teto de 5:00) passam a valer para o chip de descanso do modo de edição do detalhe novo |
| `WorkoutInputHostTest` (3, o `RestDurationField`) | aposentado na 5B se o componente ficar sem chamador; senão fica |
| `WorkoutSharePreviewHostTest` (5) | **portado** para a prévia nova (5B), com as mesmas tags e os mesmos cenários |
| `ShellNavigationHostTest` | `summaryTrueOpensTheNewSummaryAndFalseTheOldDetail` passa a esperar o detalhe novo (5B); `exerciseHistoryFromTheWorkoutReturnsToIt` fica (a rota base é a mesma) e passa a esperar a tela nova (5A); **(controlador)** ganha os casos do `?metric=` opcional (D2) |
| `UiFlowHostTest` | o passo do Histórico usa a lista nova (a tag `history-list` continua) |
| `AppViewModelRecordsHostTest` e os do ViewModel | não mudam |

## O que fica para quem implementar

- A divisão em tasks, desde que: o S1 venha primeiro na 5A e o S2 antes da aba Histórico; cada task termine com o app compilando, os testes focados verdes e a compilação iOS dos módulos tocados; a tela antiga só saia quando a nova cobrir os testes que ela cobria (D19).
- Os nomes exatos das APIs, dos pacotes, dos componentes e dos tokens novos, desde que os papéis sejam os desta spec.
- A forma do cache do índice (objeto passado pelo `:app` ou `CompositionLocal`), desde que haja um só, que construa fora da thread principal e que a composição só leia resultados.
- Se `workingSet` do domínio é exposta ou se o índice reaproveita os `RecordUi` e as regras de `SummaryStats`/`BestSets`, desde que o teste de equivalência do D6 passe.
- O desenho fino do card do treino, da linha da linha do tempo e da prévia, dentro do design system e da crítica de tela.
- Os textos finais, dentro das amostras e da voz da fase 2.

## Perguntas que só o dono responde

Nenhuma bloqueia a fase.

1. **(Já aberta desde a fase 3) Aparelho:** topa instalar o `preview` desta fase no S23 Ultra para o TalkBack nos resumos dos gráficos e no calendário, o deslizar do calendário e a fluidez a 120 Hz? Sem isso, esses itens ficam como não verificados, como nas fases anteriores.

## Notas de implementação

- **(2026-10-31, Task 4 da 5A: decisões 5 e 6 do plano, sobre o D5, o D6 e o D11.)** O que o `ProgressIndex` (`screens/.../progress/`) fixa, com teste em `ProgressIndexTest` (menos a redução semanal, que é regra da Task 5 e ainda não tem código nem teste):
  - **Métricas por convenção** (decisão 5): Carga total, Por mão e Um lado → Carga, 1RM, Volume; Carga adicional → Repetições, mais Carga adicional e Volume só se alguma sessão tiver uma série de trabalho com carga maior que zero; Assistência → Repetições e Assistência. Um rótulo de convenção que o `LoadConvention.fromLabelOrName` não conhece segue Carga total. A rota `?metric=` aceita só `load`, `reps`, `e1rm` e `volume` (minúsculas); outra métrica, ou uma que a convenção não tem, abre a primeira da convenção.
  - **1RM** só em Carga total, Por mão e Um lado (D5); na Carga adicional e na Assistência a sessão não tem 1RM, mesmo com carga.
  - **Assistência por sessão** (decisão 6): a menor `weightKg > 0` entre as séries de repetição assistidas da sessão (cargas iguais pelo ruído de unidade: a de mais repetições, depois a primeira); uma sessão só com 0 não tem ponto de Assistência, mas tem o de Repetições. Na redução semanal (decisão 7, Task 5), a semana da Assistência guarda o mínimo, e um ponto semanal tem marca se alguma sessão da semana tem.
  - **Repetições:** o máximo de repetições numa série do grupo da convenção (não assistidas fora da Assistência, assistidas nela), aquecimento fora; num empate, a primeira na ordem do treino.
  - **Volume** é uma soma da sessão, sem série de origem: o valor por série da métrica Volume é nulo e o gráfico lê o volume da sessão. As regras de série (`WorkingSets`) espelham as do `WorkoutCalculations`; o volume da sessão é igual ao `WorkoutUi.volumeKg` para tudo o que a apresentação monta, e só diverge numa série concluída com repetições negativas ou carga negativa ou não finita, que o domínio não conta.
  - **Marcas e "Atual":** o `UiState.records` não traz o valor anterior (o `firstRecord` dele é sempre verdadeiro), então um recorde atual aparece como o evento do histórico correspondente (o `WorkoutUi.records` do mesmo treino, com o mesmo exercício, convenção, tipo, assistência e carga), que tem o anterior; o próprio `RecordUi` do `UiState.records` só entra quando o treino dele não tem esse evento. Um recorde atual cujo treino não está no histórico (as duas listas fora de passo) sai da lista e da busca, como o evento de um treino excluído.
  - **Ordem dos eventos** (D11): data decrescente; num empate exato de data, a ordem do histórico mantém cada treino junto; depois a ordem do exercício no treino, carga antes de repetições e carga decrescente.
- **(2026-10-31, revisão da Task 4.)** Restringe a regra das marcas do D6 e corrige três pontos da nota acima:
  - **Marca só no ponto que a série do recorde desenhou.** Um ponto ganha marca quando a série que gerou o valor dele (a da Carga ou a das Repetições) é a série de um recorde batido do tipo, exercício, grupo de convenção e sessão: mesma carga (ruído de unidade à parte, `BestSets.sameLoad`) e mesmas repetições. Um recorde batido com outra série da sessão (na Assistência, 20 kg × 7 batendo 20 kg × 6 numa sessão cujo ponto de Repetições é 30 kg × 8) não marca o ponto e continua na linha do tempo. A cláusula do D6 "todo recorde assim tem marca no ponto da sessão" passa a valer só para o recorde cuja série é a do ponto. *Por quê:* um selo que o histórico desmente é anti-objetivo (D6). O 1RM e o volume continuam sem marca, como o D6 diz.
  - **Repetições num empate:** entre as séries com o máximo de repetições, a que é a série de um recorde de repetições batido; sem recorde, a primeira na ordem do treino.
  - **Carga com ruído de unidade:** a Carga, como a Assistência, trata como iguais as cargas a até 4 ulp (`BestSets.best`: a de mais repetições, depois a primeira); o `WeightPr` do domínio toma o máximo exato. Nesse caso raro o ponto pode ler "× 8" e o recorde "× 3", e o ponto fica sem marca.
  - **Ordem dos eventos num empate exato de data:** o início do treino, decrescente, e depois o id do treino, decrescente, em vez da ordem do histórico, que o banco não fixa entre treinos de mesmo início.
- **(2026-10-31, Task 5 da 5A: decisão 7 do plano, sobre o D3, o D7 e o D10.)** O que `ExerciseCharts` (`screens/.../progress/ExerciseCharts.kt`) fixa, com teste em `ExerciseChartsTest`:
  - **Período:** o início é `today − DatePeriod(months = n)` (31 de out. − 3 meses = 31 de jul.; 31 de out. − 6 meses = 30 de abr., sem dia 31), inclusive, comparado com o dia local do `startedAt`; um treino que começa às 23:50 e termina depois da meia-noite conta no dia em que começou.
  - **Redução acima de 104 pontos:** um ponto por semana, com a semana começando no primeiro dia da região (`WeekStart`) no fuso da tela. O valor é o melhor da semana: o máximo, e o **mínimo na Carga da Assistência** (menos ajuda é progresso; as Repetições da Assistência guardam o máximo). A **data do ponto semanal é o início da semana** (meia-noite local; num dia sem meia-noite, como 4 de nov. de 2018 em São Paulo, o primeiro instante do dia), com `workoutId = null` e a série do melhor treino da semana; a tela deve chamá-lo "Semana de 6 out". O ponto leva a marca de recorde se **qualquer** sessão da semana tem uma na própria série do ponto dela (o selo pode, então, ficar numa semana cujo melhor valor é de outra sessão). Com 104 pontos ou menos nada se reduz.
  - **Tendência:** `x` em dias locais desde o primeiro ponto; no gráfico semanal a semana em andamento (a de hoje) fica fora antes de `Trends.of`. Na Assistência a palavra não inverte: "em baixa" é progresso. `best` é o máximo, ou o mínimo na Carga da Assistência.
  - **Volume** é a soma da sessão sem série de origem (`set = null`) e sem marca; uma sessão com volume zero não tem ponto de Volume. **Vazio:** sem sessão da série → `NO_SESSIONS`; Métrica 1RM com sessões no período mas nenhuma com série de até 10 repetições → `NO_ESTIMATE`; qualquer outro período sem ponto da métrica → `NO_POINTS`.
- **(2026-10-31, controlador, depois da revisão da Task 5.)** Um ponto semanal tem marca só quando a sessão que deu o valor da semana tem marca na própria série; num empate de valor, vale a sessão com marca, depois (na Carga) a de mais repetições, depois a primeira. Substitui "se alguma sessão da semana tem" (decisão 7 do plano e notas da Task 4 e da Task 5), pelo mesmo motivo da revisão da Task 4: um selo que o histórico desmente é anti-objetivo. Também fixa: o ponto semanal de Volume é o melhor treino da semana, não a soma (a soma é das barras gerais da 5B), e a tela da Task 8 deve dizer "melhor treino da semana" nos dois idiomas; o período não tem fim, então um treino com início depois de hoje (relógio ou backup importado) entra, como na lista do histórico; `NO_ESTIMATE` só vale se alguma sessão do período tem carga (sem carga, `NO_POINTS`); a sparkline toma as últimas 12 sessões que têm a métrica.
- **(2026-10-31, Task 6 da 5A.)** Na aba Recordes, "Só atuais" com texto na busca é a interseção: os recordes atuais dos exercícios achados. A linha do tempo mantém a ordem do `ProgressIndex` (`eventOrder`) também com busca; um recorde atual sem evento no treino entra no lugar dado por essa ordem.
- **(2026-10-31, Task 11 da 5A, revisão.)** Quatro pontos da aba Recordes que a seção "Recordes (aba)" não fixava:
  - **Recorde de repetições assistido.** Vale para uma série assistida ou para qualquer série de um exercício de Assistência (`RecordUi.assistance`). O número é ajuda, não carga, e a linha diz "30 kg de assistência × 8 · antes 6 repetições" / "30 kg assistance × 8 · was 6 reps". A fala diz "30 quilos de assistência por 8". É a mesma forma do resumo (`summary_assistance_load`) e da tela antiga. `recordWasText`, `recordSpoken` e `recordLine` (`ProgressTexts.kt`) recebem `assistance` com padrão `false`, e o progresso do exercício (Task 10) pode passá-lo nos seus recordes.
  - **Só primeiros registros.** Uma pessoa nova costuma ter só primeiros registros, que o D11 esconde sem o chip. Sem busca e sem chip, a aba mostra um terceiro estado em vez de "Nenhum recorde encontrado": `EmptyState` "Nenhum recorde batido ainda" / "No records beaten yet", com "Seus primeiros registros ficam em “Primeiros registros”." e o botão "Ver primeiros registros", que liga o chip.
  - **Falha ao montar o índice ou a consulta:** "Nenhum recorde encontrado", nunca "Seu primeiro recorde aparece aqui". A pessoa pode ter centenas de recordes. Também nunca o `Skeleton`.
  - **Selo do primeiro registro.** Ele diz "Primeiro" / "First" no lugar de "PR", além do preenchimento suave, para não diferir do selo de recorde só pela cor ("Cor nunca sozinha"). Para isso, o `PrBadge` ganhou `label` e `spoken` opcionais (com padrão nos textos de hoje). Os outros usos não mudam.
