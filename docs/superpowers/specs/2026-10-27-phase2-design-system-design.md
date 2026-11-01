# Fase 2: identidade visual e design system

Data: 2026-10-27. Item da fase 2 do [ROADMAP](../ROADMAP.md). Direção visual: https://claude.ai/artifact/Y4pTUbYqTcCCrznT74raBM (6 pranchas: identidade e tokens, componentes, treino ativo escuro e claro, descanso com recorde, progresso).

O dono delegou a identidade inteira ("pode escolher, me surpreenda"). **Todas as decisões de estética e de sistema abaixo são minhas**, tomadas a partir das pesquisas [P1](../reports/2026-10-27-referencias-ux-apps-treino.md), [P2](../reports/2026-10-27-midia-exercicios.md), [P3](../reports/2026-10-27-bibliotecas-cmp-graficos-animacao-midia.md) e [P4](../reports/2026-10-27-fontes-icones-livres.md). A seção "Decisões para o dono revisar", no fim, lista o que ele deve confirmar antes do plano.

## Por que

Hoje o app usa o Material 3 com uma paleta verde genérica (`ui/Theme.kt`), a fonte do sistema e nenhum token de espaço, raio ou movimento. Cada tela decide medidas e cores por conta própria. As fases 3 a 6 refazem todas as telas, e a fase 7 leva o app ao iOS. Sem um sistema antes disso:

- cada tela nova inventa o próprio botão, campo e espaçamento, e o iOS herdaria essa divergência;
- o requisito de academia do ROADMAP (alvos grandes, números legíveis a um braço de distância, contraste alto) ficaria a critério de cada tela, sem nada que o verifique;
- não haveria onde medir contraste, fonte a 200% e leitor de tela uma vez só, para todas as telas.

## A direção: Giz & Ferro

Um diário de treino que parece equipamento de academia, não painel de dados. O nome resume as três ideias:

- **Ferro:** o chão é grafite de anilha (neutros quase pretos com um toque mínimo do verde da marca), com superfícies grossas e calmas, cantos de 12 e 20 dp e nada de vidro, brilho ou gradiente. O círculo da anilha é o motivo gráfico do sistema: o anel do timer tem as marcas de borda de uma anilha, o check da série é um disco, o selo de recorde é uma anilha com uma seta. Só a marca foge do círculo: o "OO" do nome são duas anilhas de canto chanfrado (octógono com os vértices levemente arredondados), esticadas só na vertical até ocupar exatamente da linha de base à altura das maiúsculas da Barlow Condensed, lado a lado e alinhadas pela borda, com aro interno, furo central e uma pegada com colares no vão entre elas, para que o par só possa ser lido como um peso.
- **Giz:** o texto principal é um branco de giz (não branco puro), e as ilustrações de exercício são de traço grosso, como giz no quadro da academia.
- **Placar:** os números (carga, reps, timer, recordes) usam Barlow Condensed com algarismos tabulares, como um placar. O texto da interface usa Inter.

Três cores com papéis exclusivos: **verde** (derivado do ícone atual) para ação e progresso, **latão** só para recorde, **vermelho brasa** só para ação destrutiva e erro. Escuro é o tema de marca (as pranchas partem dele), mas o tema claro é completo e todos os pares foram medidos nos dois.

Por que essa direção e não outra: a P1 mostra que o mercado já é "escuro com um acento" e que o diferencial do Mooscles é ser calmo, legível com uma mão e livre de gamificação. Uma linguagem de equipamento (anilha, giz, placar) dá personalidade sem decorar a tela: cada motivo é também um componente funcional (timer, check, selo, número). O que fica de fora de propósito: neon sobre preto puro, confete, sequência que zera, vidro à la Liquid Glass e dashboard clínico.

## Objetivos

1. **Tokens em `commonMain`** para cor (claro e escuro), tipografia, espaço (escala de 4), raio, elevação, movimento, tamanhos de alvo e háptico, com os valores exatos das tabelas abaixo. Nenhum componente usa um literal de cor, medida ou duração fora deles.
2. **Biblioteca de componentes** em `commonMain` com os estados e tamanhos do inventário abaixo, incluindo campo numérico com stepper, linha de série, timer (dock e expandido), selo e aviso de recorde, sheets, diálogos, toasts, estados vazio, de carregamento e de erro.
3. **Contraste WCAG AA provado por teste:** todos os pares de texto a 4,5:1 ou mais, e bordas de componente, foco e gráficos a 3:1 ou mais, nos dois temas.
4. **Fonte do sistema a 200%** sem texto cortado, sobreposto ou com reticências em número.
5. **Galeria de componentes** num modo de desenvolvimento, com troca de tema, de escala de fonte, de idioma e de "reduzir movimento".
6. **Textos em recursos**, com inglês (idioma base) e pt-BR.
7. **Tela piloto: Configurações**, refeita só com o sistema, com o mesmo comportamento de hoje.
8. **Spike de `tnum`** decidido no CI, no Android e no simulador de iOS, antes de fixar a fonte dos números.

## Fora de escopo

- Redesenhar o treino ativo, o histórico, a biblioteca ou as rotinas. As pranchas dessas telas são a **direção** para as fases 3 a 5, não uma entrega desta fase. O fluxo fino do treino ativo (teclado, avanço de campo, gesto de concluir) continua dependendo das capturas reais pedidas na P1.
- Gráficos de progresso (fase 5), mídia de exercício, Coil e player de vídeo (fase 4) e a notificação com ações do timer (fase 3). Aqui ficam só os tokens e as regras que eles vão seguir.
- O app iOS (`iosApp/`). Sem iPhone nem Xcode local, o iOS desta fase é o simulador do CI, como na fase 1.
- Trocar o ícone do launcher. A marca nova está desenhada na prancha, mas a troca é uma decisão do dono (ver o fim).
- Navegação nova, barra inferior ou mudança de arquitetura da informação.

## Anti-objetivos (falha mesmo que "funcione")

- Registrar uma série ficar mais lento ou exigir mais toques do que hoje.
- Uma cor, medida ou duração escrita à mão num componente ou na tela piloto.
- Um estado comunicado só por cor (série concluída, recorde, erro, seleção).
- Algum par de texto abaixo de 4,5:1 em qualquer tema, ou número cortado a 200%.
- Animação que atrasa input ou o timer, ou que ignora "reduzir movimento".
- O APK crescer mais de 1 MB por causa de fontes e ícones.
- Mudança de comportamento na tela piloto: as mesmas preferências, com as mesmas chaves e os mesmos efeitos.
- Um segundo design para o iOS: os componentes são os mesmos nas duas plataformas, e só o háptico e a leitura de "reduzir movimento" têm `expect/actual`.

## Restrições técnicas

### Depende da fase 1B

Tokens e componentes vivem em `commonMain` e precisam de Compose Multiplatform com alvos iOS. Isso só existe depois da etapa 1B (módulo `:shared` e job iOS no CI). **Até a 1B entrar na `main`, nada desta spec é implementado.** O que pode andar antes é o que não é código do app: baixar as fontes e conferir a licença, escolher os SVGs do Lucide, desenhar os ícones próprios e escrever o briefing de ilustração.

**Módulo (decisão minha):** um módulo KMP novo, `:designsystem`, com alvos Android, `iosArm64` e `iosSimulatorArm64`, depende do Compose Multiplatform e de `compose.components.resources`. Ele guarda tokens, componentes, fontes, ícones, strings e a galeria. O `:shared` continua sem dependência de UI. O `:app` (telas atuais, Jetpack Compose) depende do `:designsystem`. No Android, o Compose Multiplatform usa os mesmos artefatos do Jetpack Compose, então as telas antigas e os componentes novos convivem na mesma árvore. Se a 1B decidir colocar a UI comum dentro do `:shared`, o plano segue essa estrutura, e a regra que vale é só esta: tudo fica em `commonMain`.

### Cor

Primitivas são nomeadas por matiz e luminosidade OKLCH (`ferro.19` = ferro com L 0,19) e nunca aparecem em componente. Componentes só usam os tokens semânticos. Os valores foram gerados em OKLCH e convertidos para sRGB, e os contrastes foram calculados pela fórmula de luminância relativa do WCAG 2.2 a partir dos hex abaixo (script em JS, sem estimativa). Matizes: ferro 165° (croma 0,008, e 0,004 a partir de L 0,90), verde 160°, latão 78°, brasa 27°.

**Primitivas**

| Primitiva | OKLCH | sRGB |
| --- | --- | --- |
| `branco` | `oklch(1 0 0)` | `#FFFFFF` |
| `brasa.18` | `oklch(0.18 0.03 27)` | `#1D0C0A` |
| `brasa.30` | `oklch(0.3 0.065 27)` | `#491F1B` |
| `brasa.50` | `oklch(0.5 0.17 27)` | `#B02B27` |
| `brasa.52` | `oklch(0.52 0.18 27)` | `#BA2B28` |
| `brasa.68` | `oklch(0.68 0.165 27)` | `#ED695E` |
| `brasa.74` | `oklch(0.74 0.145 27)` | `#F98478` |
| `brasa.94` | `oklch(0.94 0.03 27)` | `#FFE4E0` |
| `ferro.16` | `oklch(0.16 0.008 165)` | `#0A0E0C` |
| `ferro.19` | `oklch(0.19 0.008 165)` | `#111513` |
| `ferro.21` | `oklch(0.21 0.008 165)` | `#151917` |
| `ferro.23` | `oklch(0.23 0.008 165)` | `#1A1E1C` |
| `ferro.25` | `oklch(0.25 0.008 165)` | `#1E2321` |
| `ferro.27` | `oklch(0.27 0.008 165)` | `#232825` |
| `ferro.30` | `oklch(0.3 0.008 165)` | `#2A2F2D` |
| `ferro.35` | `oklch(0.35 0.008 165)` | `#373C39` |
| `ferro.42` | `oklch(0.42 0.008 165)` | `#494E4C` |
| `ferro.50` | `oklch(0.5 0.008 165)` | `#5F6562` |
| `ferro.57` | `oklch(0.57 0.008 165)` | `#737976` |
| `ferro.70` | `oklch(0.7 0.008 165)` | `#9AA09D` |
| `ferro.80` | `oklch(0.8 0.008 165)` | `#B9BFBC` |
| `ferro.89` | `oklch(0.89 0.008 165)` | `#D6DCD9` |
| `ferro.92` | `oklch(0.92 0.004 165)` | `#E2E5E4` |
| `ferro.93` | `oklch(0.93 0.004 165)` | `#E5E9E7` |
| `ferro.95` | `oklch(0.95 0.004 165)` | `#ECEFEE` |
| `ferro.96` | `oklch(0.96 0.004 165)` | `#EFF3F1` |
| `ferro.99` | `oklch(0.99 0.004 165)` | `#F9FDFB` |
| `latao.22` | `oklch(0.22 0.04 78)` | `#251803` |
| `latao.31` | `oklch(0.31 0.055 78)` | `#3F2C0B` |
| `latao.40` | `oklch(0.4 0.09 78)` | `#604102` |
| `latao.50` | `oklch(0.5 0.11 78)` | `#835A01` |
| `latao.87` | `oklch(0.87 0.17 78)` | `#FFCB77` |
| `latao.88` | `oklch(0.88 0.12 78)` | `#FFCF83` |
| `latao.94` | `oklch(0.94 0.05 78)` | `#FEE8C6` |
| `verde.20` | `oklch(0.2 0.04 160)` | `#031B10` |
| `verde.30` | `oklch(0.3 0.05 160)` | `#143525` |
| `verde.44` | `oklch(0.44 0.1 160)` | `#04623F` |
| `verde.47` | `oklch(0.47 0.11 160)` | `#016C46` |
| `verde.50` | `oklch(0.5 0.115 160)` | `#00764C` |
| `verde.70` | `oklch(0.7 0.145 160)` | `#2EB97F` |
| `verde.76` | `oklch(0.76 0.155 160)` | `#39CE8E` |
| `verde.83` | `oklch(0.83 0.14 160)` | `#68E3A8` |
| `verde.93` | `oklch(0.93 0.045 160)` | `#CFF2DE` |

**Tokens semânticos** (claro · escuro)

| Token | Claro | Escuro |
| --- | --- | --- |
| `bg.base` | `ferro.96` #EFF3F1 | `ferro.19` #111513 |
| `bg.surface` | `ferro.99` #F9FDFB | `ferro.23` #1A1E1C |
| `bg.raised` | `branco` #FFFFFF | `ferro.27` #232825 |
| `bg.overlay` | `branco` #FFFFFF | `ferro.30` #2A2F2D |
| `bg.inset` | `ferro.93` #E5E9E7 | `ferro.16` #0A0E0C |
| `bg.inverse` | `ferro.25` #1E2321 | `ferro.92` #E2E5E4 |
| `border.subtle` | `ferro.89` #D6DCD9 | `ferro.35` #373C39 |
| `border.strong` | `ferro.57` #737976 | `ferro.57` #737976 |
| `text.primary` | `ferro.21` #151917 | `ferro.95` #ECEFEE |
| `text.secondary` | `ferro.42` #494E4C | `ferro.80` #B9BFBC |
| `text.tertiary` | `ferro.50` #5F6562 | `ferro.70` #9AA09D |
| `text.disabled` | `ferro.70` #9AA09D | `ferro.50` #5F6562 |
| `text.inverse` | `ferro.96` #EFF3F1 | `ferro.19` #111513 |
| `accent.solid` | `verde.50` #00764C | `verde.76` #39CE8E |
| `accent.solidPressed` | `verde.44` #04623F | `verde.70` #2EB97F |
| `accent.on` | `branco` #FFFFFF | `verde.20` #031B10 |
| `accent.text` | `verde.47` #016C46 | `verde.83` #68E3A8 |
| `accent.subtle` | `verde.93` #CFF2DE | `verde.30` #143525 |
| `pr.solid` | `latao.87` #FFCB77 | `latao.87` #FFCB77 |
| `pr.on` | `latao.22` #251803 | `latao.22` #251803 |
| `pr.text` | `latao.50` #835A01 | `latao.88` #FFCF83 |
| `pr.subtle` | `latao.94` #FEE8C6 | `latao.31` #3F2C0B |
| `danger.solid` | `brasa.52` #BA2B28 | `brasa.68` #ED695E |
| `danger.on` | `branco` #FFFFFF | `brasa.18` #1D0C0A |
| `danger.text` | `brasa.50` #B02B27 | `brasa.74` #F98478 |
| `danger.subtle` | `brasa.94` #FFE4E0 | `brasa.30` #491F1B |
| `focus.ring` | `verde.47` #016C46 | `verde.83` #68E3A8 |
| `accent.inverseText` | `verde.83` #68E3A8 | `verde.47` #016C46 |
| `chart.line` | `verde.50` #00764C | `verde.76` #39CE8E |
| `chart.pr` | `latao.40` #604102 | `latao.87` #FFCB77 |
| `chart.grid` | `ferro.89` #D6DCD9 | `ferro.35` #373C39 |
| `timer.track` | `ferro.89` #D6DCD9 | `ferro.35` #373C39 |
| `timer.progress` | `verde.50` #00764C | `verde.76` #39CE8E |

Papéis:

- `bg.base` é o fundo da tela; `bg.surface`, o card; `bg.raised`, o dock do timer, a linha em edição e o botão de ±15 s; `bg.overlay`, sheet e diálogo; `bg.inset`, o fundo de mídia e o campo desabilitado; `bg.inverse`, o toast. No escuro, a elevação é feita só por esses tons de superfície.
- `border.subtle` é decorativa (divisórias, trilho do timer, grade do gráfico) e nunca é a única borda de um controle. `border.strong` é a borda de campo, botão secundário e check vazio.
- `accent.*` marca ação e progresso: o botão primário, o check concluído, o anel do timer e a linha do gráfico. Só um botão `accent.solid` por tela. `accent.subtle` é o fundo da série concluída, do chip selecionado e do botão tonal.
- `pr.*` aparece **só** em recorde: o selo, o aviso e o marcador no gráfico.
- `danger.*` aparece só em ação destrutiva e erro. O botão destrutivo é contornado (`danger.text`) nas telas e preenchido (`danger.solid`) só dentro do diálogo de confirmação.
- `focus.ring` é um anel de 2 dp, afastado 2 dp do controle.
- Verde e latão diferem também em luminosidade (escuro: L 0,76 contra 0,87; gráfico claro: L 0,50 contra 0,40), e o recorde sempre vem com o ícone de anilha e o texto "PR". Assim ninguém precisa distinguir a cor.
- Scrim: `rgba(0,0,0,0.56)` no escuro e `rgba(21,25,23,0.40)` no claro.

**Pares medidos** (razão de contraste WCAG; mínimo 4,5 para texto, 3,0 para não-texto)

| Par | Tipo | Claro | Escuro |
| --- | --- | --- | --- |
| `text.primary` sobre `bg.base` | texto | 15,85 | 15,91 |
| `text.primary` sobre `bg.surface` | texto | 17,30 | 14,56 |
| `text.primary` sobre `bg.raised` | texto | 17,75 | 12,95 |
| `text.primary` sobre `bg.overlay` | texto | 17,75 | 11,76 |
| `text.primary` sobre `bg.inset` | texto | 14,49 | 16,79 |
| `text.secondary` sobre `bg.base` | texto | 7,57 | 9,86 |
| `text.secondary` sobre `bg.surface` | texto | 8,26 | 9,02 |
| `text.secondary` sobre `bg.raised` | texto | 8,48 | 8,02 |
| `text.secondary` sobre `bg.overlay` | texto | 8,48 | 7,29 |
| `text.secondary` sobre `bg.inset` | texto | 6,92 | 10,40 |
| `text.tertiary` sobre `bg.base` | texto | 5,32 | 6,92 |
| `text.tertiary` sobre `bg.surface` | texto | 5,81 | 6,33 |
| `text.tertiary` sobre `bg.raised` | texto | 5,96 | 5,63 |
| `text.tertiary` sobre `bg.overlay` | texto | 5,96 | 5,11 |
| `text.tertiary` sobre `bg.inset` | texto | 4,86 | 7,30 |
| `text.inverse` sobre `bg.inverse` | texto | 14,24 | 14,52 |
| `accent.inverseText` sobre `bg.inverse` | texto | 9,97 | 5,12 |
| `text.primary` sobre `accent.subtle` | texto | 14,71 | 11,59 |
| `text.secondary` sobre `accent.subtle` | texto | 7,03 | 7,18 |
| `accent.text` sobre `accent.subtle` | texto | 5,38 | 8,39 |
| `accent.on` sobre `accent.solid` | texto | 5,68 | 8,90 |
| `accent.on` sobre `accent.solidPressed` | texto | 7,44 | 7,16 |
| `accent.text` sobre `bg.base` | texto | 5,80 | 11,52 |
| `accent.text` sobre `bg.surface` | texto | 6,33 | 10,54 |
| `accent.text` sobre `bg.raised` | texto | 6,49 | 9,38 |
| `accent.text` sobre `bg.overlay` | texto | 6,49 | 8,52 |
| `accent.solid` sobre `bg.surface` | não-texto | 5,54 | 8,34 |
| `accent.solid` sobre `bg.raised` | não-texto | 5,68 | 7,41 |
| `pr.on` sobre `pr.solid` | texto | 11,61 | 11,61 |
| `pr.text` sobre `bg.surface` | texto | 5,97 | 11,64 |
| `pr.text` sobre `bg.raised` | texto | 6,13 | 10,35 |
| `pr.text` sobre `pr.subtle` | texto | 5,13 | 9,20 |
| `text.primary` sobre `pr.subtle` | texto | 14,85 | 11,51 |
| `danger.on` sobre `danger.solid` | texto | 6,06 | 6,12 |
| `danger.text` sobre `bg.surface` | texto | 6,36 | 6,90 |
| `danger.text` sobre `bg.overlay` | texto | 6,52 | 5,57 |
| `danger.text` sobre `danger.subtle` | texto | 5,41 | 5,75 |
| `text.primary` sobre `danger.subtle` | texto | 14,73 | 12,14 |
| `border.strong` sobre `bg.base` | não-texto | 3,97 | 4,14 |
| `border.strong` sobre `bg.surface` | não-texto | 4,33 | 3,79 |
| `border.strong` sobre `bg.raised` | não-texto | 4,44 | 3,37 |
| `border.strong` sobre `bg.inset` | não-texto | 3,63 | 4,37 |
| `focus.ring` sobre `bg.base` | não-texto | 5,80 | 11,52 |
| `focus.ring` sobre `bg.surface` | não-texto | 6,33 | 10,54 |
| `focus.ring` sobre `bg.raised` | não-texto | 6,49 | 9,38 |
| `chart.line` sobre `bg.surface` | não-texto | 5,54 | 8,34 |
| `chart.pr` sobre `bg.surface` | não-texto | 9,08 | 11,28 |
| `timer.progress` sobre `bg.raised` | não-texto | 5,68 | 7,41 |
| `text.disabled` sobre `bg.surface` | isento | 2,59 | 2,83 |
| `border.subtle` sobre `bg.surface` | decorativo | 1,36 | 1,50 |

Esta tabela vira um teste (`ContrastTokensTest`, em `commonTest`), que calcula as razões a partir dos tokens e falha abaixo do mínimo. O pior caso de `border.strong` é 3,37:1 (sobre `bg.raised` no escuro), e o do texto terciário é 4,86:1 (sobre `bg.inset` no claro). `text.disabled` é isento (WCAG 1.4.3), e o estado desabilitado também muda o fundo do controle.

### Tipografia

Inter (OFL, variável, eixos `opsz` e `wght`) para a interface. Barlow Condensed (OFL, estática: 500, 600 e 700) para números de carga, reps, timer e estatísticas. Todo número que muda ou que fica em coluna usa `fontFeatureSettings = "tnum"`, inclusive em Inter (datas, durações, contagens). Tamanhos em `sp`, altura de linha em `sp` proporcional, espaçamento em `em`.

| Token | Família | Peso | Tamanho / linha (sp) | Espaçamento | Uso |
| --- | --- | --- | --- | --- | --- |
| `num.display` | Barlow Condensed | 600 | 88 / 88 | −0,01em | timer expandido |
| `num.lg` | Barlow Condensed | 600 | 44 / 46 | 0 | valor no stepper, estatística principal |
| `num.md` | Barlow Condensed | 600 | 28 / 32 | 0 | carga e reps na linha de série, timer no dock (32/34) |
| `num.sm` | Barlow Condensed | 500 | 20 / 24 | 0 | coluna "anterior", número da série (22/24) |
| `title.lg` | Inter | 700 | 24 / 30 | −0,01em | título de tela |
| `title.md` | Inter | 600 | 18 / 24 | 0 | nome do exercício |
| `title.sm` | Inter | 600 | 16 / 22 | 0 | título de seção e de card |
| `body.lg` | Inter | 400 | 16 / 24 | 0 | texto corrido, diálogo |
| `body.md` | Inter | 400 | 14 / 20 | 0 | texto de apoio |
| `label.lg` | Inter | 600 | 16 / 20 | 0 | botões L e M |
| `label.md` | Inter | 600 | 14 / 18 | 0 | botão S, chip |
| `label.caps` | Inter | 600 | 12 / 16 | +0,06em, caixa alta via estilo | cabeçalho de coluna, rótulo de seção |
| `caption` | Inter | 400 | 13 / 18 | 0 | legenda, passo do stepper |

Regras:

- Nada abaixo de 12 sp. Nenhum peso abaixo de 400.
- A unidade ao lado do número (kg, lb) é Inter 600 em `text.secondary`, um passo menor que o número.
- Número nunca tem reticências. Se não couber, o layout quebra em duas linhas (ver Acessibilidade).
- Separador decimal e de milhar seguem o idioma (`82,5` e `6.840` em pt-BR; `82.5` e `6,840` em inglês), com espaço não separável antes da unidade e `×` entre carga e reps.

**Plano B do `tnum`:** se o spike reprovar a Barlow Condensed no iOS (ou nas duas plataformas), a opção B1 é congelar os tabulares como glifos padrão com o `pyftfeatfreeze` do fonttools e embutir a fonte gerada com outro nome se a licença tiver Reserved Font Name (conferir o `OFL.txt` da Barlow). A opção B2 é usar Geist Mono (OFL) só nos timers e manter a Barlow nos números estáticos. Prefiro B1, porque mantém a identidade. Se a Inter variável não aplicar o eixo de peso no iOS, a saída é embutir instâncias estáticas 400, 500, 600 e 700.

### Espaço, raio, elevação e alvos

| Token | dp | Uso típico |
| --- | --- | --- |
| `space.1` | 4 | entre linhas de série, ícone e texto |
| `space.2` | 8 | padding do card de séries, entre botões |
| `space.3` | 12 | entre cards, ícone e título |
| `space.4` | 16 | margem lateral da tela, padding de card de conteúdo |
| `space.5` | 20 | padding de sheet |
| `space.6` | 24 | padding de diálogo, entre seções |
| `space.8` | 32 | entre grupos grandes |
| `space.10` | 40 | — |
| `space.12` | 48 | — |
| `space.16` | 64 | topo de estado vazio |

| Token | dp | Uso |
| --- | --- | --- |
| `radius.xs` | 4 | skeleton, barra de gráfico |
| `radius.sm` | 8 | selo de tipo de série |
| `radius.md` | 12 | botão, campo, linha de série, mídia |
| `radius.lg` | 20 | card (12 da linha + 8 de padding) |
| `radius.xl` | 28 | sheet (topo) e diálogo |
| `radius.full` | 50% | check, chip, pílula, selo PR, botões de ±15 s do timer expandido |

| Token | Escuro | Claro | Compose |
| --- | --- | --- | --- |
| `elevation.0` | `bg.base` | `bg.base` | — |
| `elevation.1` (card) | `bg.surface`, sem sombra | `bg.surface` + `0 1 2 rgba(21,25,23,.06), 0 1 3 rgba(21,25,23,.08)` | `shadowElevation` 1 dp no claro |
| `elevation.2` (dock, barras fixas) | `bg.raised` + filete superior `rgba(255,255,255,.06)` | `bg.raised` + `0 -8 24 rgba(21,25,23,.10)` | 6 dp no claro |
| `elevation.3` (sheet, diálogo, toast) | `bg.overlay` + scrim | `bg.overlay` + `0 12 32 rgba(21,25,23,.18)` + scrim | 12 dp no claro |

| Token | dp | Onde |
| --- | --- | --- |
| `target.min` | 48 | todo controle do app |
| `target.workout` | 56 | check, stepper, ±15 s, pular, adicionar série, ações do dock |
| `target.gap` | 8 | distância mínima entre alvos vizinhos |

Um controle pode desenhar menos que o alvo (o círculo do check tem 44 dp num alvo de 56; o botão S tem 40 dp num alvo de 48), mas a área de toque nunca é menor.

### Movimento

| Token | Valor | Uso |
| --- | --- | --- |
| `motion.instant` | 0 ms | digitar, passo do stepper, troca de aba: sem animação |
| `motion.press` | 120 ms, `ease.out` | escala 0,96 ao pressionar (desabilitado não escala) |
| `motion.fast` | 150 ms, `ease.out` | cor, opacidade, preenchimento do check, saídas pequenas |
| `motion.base` | 220 ms, `ease.out` | expandir linha em edição, entrada de toast, chip |
| `motion.slow` | 300 ms, `ease.drawer` | entrada de sheet e diálogo (saída em 200 ms, `ease.out`) |
| `ease.out` | `cubic-bezier(0.23, 1, 0.32, 1)` | entradas e resposta ao toque |
| `ease.inOut` | `cubic-bezier(0.77, 0, 0.175, 1)` | algo que se move dentro da tela |
| `ease.drawer` | `cubic-bezier(0.32, 0.72, 0, 1)` | sheet e diálogo |
| `linear` | — | progresso do anel do timer |
| `spring.snappy` | `dampingRatio` 1,0, `stiffness` 700 | reordenar, mudanças de layout, sem quique |
| `spring.set` | 0,6 · 600 | concluir série: o disco do check vai de 0,92 a 1 |
| `spring.pr` | 0,5 · 400 | selo de recorde: de 0,9 a 1 e 8 dp para cima, com opacidade |
| `spring.finish` | 0,7 · 300 | cartão de resumo do fim do treino (fase 3) |

Regras: mola com quique só nos três momentos-chave (concluir série, recorde, fim do treino). Nunca animar de escala 0. Saída mais curta que entrada. Nada de keyframes para estado interativo, porque transições e molas podem ser interrompidas. Toda mudança animada também tem uma pista estática (ícone, cor ou texto).

**Reduzir movimento:** lido do sistema por um `expect/actual` `rememberReducedMotion()` (Android: escala de animação do sistema igual a 0, por `Settings.Global.ANIMATOR_DURATION_SCALE`; iOS: `UIAccessibility.isReduceMotionEnabled`). Com ele ativo, molas e deslocamentos viram um fade de `motion.fast`, o skeleton fica parado, a mídia de exercício mostra só o quadro B, e o anel do timer avança de segundo em segundo, sem varredura.

### Háptico

| Momento | Android (`HapticFeedbackType` comum) | iOS (`actual`) |
| --- | --- | --- |
| Concluir série | `Confirm` | impacto médio |
| Recorde (substitui o háptico de concluir) | `Confirm` duas vezes, 80 ms de intervalo | notificação de sucesso |
| Fim do descanso, app aberto | `Confirm` | notificação de aviso |
| Fim do descanso, app fechado | vibração do canal da notificação (já existe) | notificação local (fase 7) |
| Passo do stepper, ±15 s | `SegmentTick` | seleção |
| Desmarcar série | `SegmentFrequentTick` | seleção |
| Ação bloqueada ou inválida | `Reject` | notificação de erro |
| Pressionar e segurar (menu) | `LongPress` | impacto leve |
| Interruptor | `ToggleOn` / `ToggleOff` | seleção |

Um único ponto de entrada, `MoosclesHaptics.perform(HapticEvent)`, implementado com `LocalHapticFeedback` e um `expect/actual` para o que o comum não cobre. Quais tipos o CMP 1.12 implementa no iOS não foi verificado: isso entra no spike S3. Sem opção no app nesta fase: o sistema decide se vibra.

### Ícones

- **Lucide** (ISC), 24 dp de grade, traço de 2 dp, pontas e junções redondas, sempre `currentColor`. Tamanhos: 16 e 20 em linha com texto, 24 por padrão, 28 nas ações do treino. O traço acompanha o texto ao lado (2 dp junto de 400–600).
- Os SVGs do repositório oficial são convertidos uma vez para `ImageVector` e o código gerado entra no repositório (por exemplo, com o Valkyrie). Não há biblioteca de ícones em tempo de execução, então só entram no binário os ícones usados. O `LICENSE` do Lucide vai para a tela de licenças.
- **Ícones próprios**, desenhados na mesma grade e no mesmo traço: anilha, recorde (anilha com seta), halter, barra, kettlebell, banco, polia, máquina, corda e corpo. Os grupos musculares ficam para a fase 4.
- Contorno é o padrão, e preenchimento indica estado ativo. Botão só de ícone sempre tem rótulo acessível.

### Ilustração de exercício

O formato segue a P2: **dois quadros estáticos** (início e fim do movimento) alternados por animação Compose. Isso funciona igual no Android 8 e no iOS, sem GIF nem WebP animado.

- Estilo "giz": figura de traço grosso e uniforme, sem rosto nem gênero marcado, com cabeça em círculo e membros em cápsulas, em `text.secondary`; equipamento em `text.tertiary`; a anilha aparece de frente (disco com furo), como no sistema; o músculo trabalhado em `accent.solid`. Fundo transparente, para funcionar nos dois temas. O exemplo está na prancha (agachamento e supino).
- Moldura 1:1, entregue em vetor (SVG convertido para `ImageVector`) ou WebP estático com 2x de densidade. A miniatura tem 64 dp e o detalhe ocupa a largura útil.
- A alternância troca o quadro a cada 900 ms com fade de `motion.base`. Ela pausa fora da tela e com "reduzir movimento", quando o quadro B fica fixo e um toque alterna os quadros.
- Enquanto um exercício não tiver ilustração, aparece um placeholder com o ícone do equipamento e o texto "Imagem ainda não baixada" (offline) ou só o ícone (sem mídia).
- A produção do conjunto (encomenda com licença explícita ou desenho próprio) é da fase 4. Esta spec só fixa o estilo, para que as primeiras peças já nasçam coerentes.

### Inventário de componentes

Todos com tamanhos em tokens, estados de foco visíveis e rótulo acessível.

| Componente | Variantes e tamanhos | Estados |
| --- | --- | --- |
| `MButton` | primário (`accent.solid`), secundário (contorno `border.strong`), tonal (`accent.subtle`), texto, destrutivo (contorno `danger.text`), perigo (preenchido, só em diálogo); L 56, M 48, S 40 com alvo de 48; com ícone à esquerda | normal, pressionado (0,96 e `solidPressed`), foco, desabilitado, carregando (spinner no lugar do texto, mesma largura) |
| `MIconButton` | 48 e 56; neutro, contornado, tonal | normal, pressionado, foco, desabilitado, alternado (`aria-pressed`) |
| `NumericField` | carga (decimal) e reps (inteiro); 72 × 48 | sugerido (valor da última sessão em `text.secondary`), preenchido, foco, erro (borda de 2 dp `danger.text` e mensagem com ícone), desabilitado |
| `Stepper` | − e + de 64 × 56 com o valor em `num.lg`; passo de carga 2,5 kg ou 5 lb, de reps 1 | normal, no mínimo (− desabilitado), repetição ao segurar (após 400 ms, a cada 80 ms) |
| `SetRow` | colunas série, anterior, carga, reps e check; altura mínima de 56 | pendente, editando, concluída, concluída com recorde, aquecimento (A), drop (D), falha (F), removida (toast com desfazer) |
| `SetCheck` | disco de 44 num alvo de 56 | vazio, concluído (`accent.solid` e check), desabilitado |
| `ExerciseCard` | cabeçalho com mídia de 64, nome, metadado e menu; tabela de séries; "Adicionar série"; versão recolhida | normal, mídia ausente, mídia offline |
| `RestTimerDock` | anel de 48, rótulo, tempo em `num.md`, −15, +15, pular | correndo, pausado, últimos 10 s (só o háptico no fim), concluído ("Descanso concluído" por 3 s) |
| `RestTimerExpanded` | anel-anilha de 280 com 60 marcas, `num.display`, ±15 s em discos de 80, "A seguir", "Pular descanso" | mesmos do dock |
| `PrBadge` | pílula `pr.solid` com ícone e "PR"; variante suave (`pr.subtle`) para listas | — |
| `PrToast` | aviso no topo com ícone, título e "carga × reps · antes …" | entrada com `spring.pr`; some em 4 s; sem ação |
| `Chip` | filtro, tipo de série, segmentado (período) | normal, selecionado (ícone de check + `accent.subtle`), desabilitado |
| `BottomSheet` | alça de 32 × 4, topo `radius.xl`, altura máxima de 90% | aberto, arrastando, fechando; fecha com voltar, toque no scrim e arrasto |
| `Dialog` | título, texto, ações à direita (confirmação na ponta) | normal, destrutivo |
| `Toast` | `bg.inverse`, ação em `accent.inverseText` | informativo (4 s), com ação (6 s ou até fechar), erro (até fechar) |
| `EmptyState` | ilustração de giz, título, texto, uma ação primária e uma secundária opcional | — |
| `Skeleton` | blocos `bg.raised` com `radius.xs` ou `md` | pulso de opacidade de 1,2 s; parado com reduzir movimento |
| `ErrorBanner` | `danger.subtle`, ícone de alerta, texto e ação | — |
| `SettingsRow`, `RadioRow`, `SwitchRow`, `SectionHeader`, `TopBar` | linha de 56 dp no mínimo | normal, foco, desabilitado |

### Acessibilidade

- **Contraste:** WCAG 2.2 AA pela tabela de pares, verificada por teste. Números grandes também passam de 4,5:1, embora 3:1 bastasse.
- **Cor nunca sozinha:** concluído = check; recorde = anilha com seta e "PR"; erro = ícone e mensagem; selecionado = ícone de check; tipo de série = letra.
- **Alvos:** 48 dp em todo o app, 56 dp nas ações do treino, 8 dp entre alvos. Deslizar para remover sempre tem alternativa no menu da série (WCAG 2.5.7).
- **Fonte a 200%:** tudo em `sp`, contêineres com altura mínima e não fixa. Com escala de 1,5 ou mais, a linha de série vira duas linhas (anterior em cima; carga, reps e check embaixo), e o dock do timer empilha os botões. Número nunca é truncado. A galeria testa as escalas 1,0, 1,3 e 2,0.
- **TalkBack e VoiceOver:**
  - A linha de série é lida como um nó só: "Série 4 de 5, anterior 80 quilos por 6, carga 85 quilos, 6 repetições, não concluída". Ela tem ações personalizadas: "Concluir série", "Editar carga", "Editar repetições", "Mudar tipo" e "Remover série".
  - O check é um alternável ("Concluir série 4", marcado ou não).
  - Os botões do stepper dizem o passo: "Aumentar carga em 2,5 quilos".
  - O timer é anunciado de forma educada só ao começar, ao ser ajustado e ao terminar, nunca a cada segundo: "Descanso, 1 minuto e 24 segundos restantes".
  - O selo de recorde é lido como "recorde pessoal".
  - Unidades são lidas por extenso conforme o idioma.
  - Gráficos (fase 5) têm uma descrição resumida e uma tabela alternativa.
- **Reduzir movimento:** conforme a seção Movimento.
- **Foco:** anel `focus.ring` em todo controle focável (teclado físico e navegação por switch). A ordem de leitura segue a ordem visual. Sheet e diálogo prendem o foco e devolvem ao gatilho quando fecham.

### Voz e tom

Direto, calmo, de quem está do lado na academia, sem torcida e sem culpa. Trata por "você" em pt-BR e "you" em inglês. Frases curtas, o número antes do adjetivo, um verbo no botão ("Descartar", não "OK"). Sem exclamação, emoji ou caixa alta no texto (a caixa alta dos rótulos vem só do estilo). Recordes são ditos com o número; ausência nunca é cobrada ("12 treinos nas últimas 4 semanas", nunca "você perdeu sua sequência").

| Situação | pt-BR | en |
| --- | --- | --- |
| Recorde | Novo recorde: 87,5 kg × 6 | New record: 87.5 kg × 6 |
| Fim do descanso | Descanso acabou. Próxima: supino reto, série 5 | Rest is over. Next: bench press, set 5 |
| Descartar | Descartar treino? As 9 séries registradas serão apagadas. | Discard workout? The 9 logged sets will be deleted. |
| Constância | 12 treinos nas últimas 4 semanas | 12 workouts in the last 4 weeks |
| Vazio | Nenhum treino ainda. O primeiro aparece aqui quando você finalizar. | No workouts yet. Your first one shows up here when you finish. |
| Erro | Não foi possível exportar. Seus dados continuam no aparelho. | Couldn't export. Your data is still on this device. |

### Textos e idiomas

- As strings ficam em `composeResources/values/strings.xml` (inglês, idioma base) e `composeResources/values-pt-rBR/strings.xml` no `:designsystem`. A classe `Res` é pública, para o `:app` usá-la. Quantidades usam `plurals` ("1 série", "3 séries").
- Nesta fase passam para recursos as strings dos componentes e de toda a tela piloto. As demais telas migram quando forem redesenhadas (fases 3 a 6), e não duas vezes.
- A formatação de números e unidades por idioma ganha um formatador em `commonMain`, com testes nos dois idiomas. Isso é uma mudança visível: em pt-BR, `82.5` passa a `82,5`.
- O `AGENTS.md` diz hoje que a UI é em inglês. Ao fim da fase, ele passa a dizer "UI com inglês como idioma base e tradução pt-BR por recursos".

### Dependências e spikes

| Item | Escolha | Origem | Spike antes de comprometer |
| --- | --- | --- | --- |
| Fontes | Inter variável + Barlow Condensed 500/600/700, em `composeResources/font` | P4 | **S1 `tnum`** e **S2 eixo variável no iOS** |
| Ícones | SVGs do Lucide convertidos para `ImageVector` no repositório, mais os próprios | P4 | — (sem dependência em tempo de execução) |
| Animação | só a do Compose (`animate*AsState`, `AnimatedContent`, molas) | P3 | — |
| Háptico | `LocalHapticFeedback` + `expect/actual` | P3 | **S3 tipos no iOS** (compilação e mapeamento; a sensação real só num iPhone) |
| Lottie | não entra nesta fase; a celebração de recorde é feita em Compose. Compottie lite fica reservado para a fase 4 ou 5 | P3 | medir o binário quando entrar |
| Gráficos | tokens e regras aqui; a escolha entre Vico e Canvas próprio é da fase 5 | P3 | spike de leitor de tela na fase 5 |
| Imagens e vídeo | Coil 3 e um player próprio por `expect/actual` na fase 4 | P2, P3 | spikes da P3 na fase 4 |
| Capturas de tela | recomendo Roborazzi para a galeria no Android; quem implementar decide | — | — |

**S1, spike de `tnum`** (primeira task do plano, antes de qualquer componente):

- **Onde:** um teste de UI em `commonTest` do `:designsystem` (`runComposeUiTest` com `rememberTextMeasurer`), com as fontes reais em `composeResources/font`. Ele roda em `iosSimulatorArm64Test` no job macOS do CI e como teste instrumentado no emulador Android. O Robolectric não vale como prova.
- **O que mede**, com tolerância de 0,5 px:
  - largura de "1111" contra "8888", com `tnum` e sem ele, em Inter e em Barlow Condensed;
  - "0:00" contra "9:59" na Barlow com `tnum`, que é o caso do timer;
  - para o S2, "8888" com peso 400 contra 700 na Inter variável.
- **O controle:** Inter sem `tnum` tem de dar larguras diferentes, senão o teste não é capaz de reprovar e não vale.
- **Resultados possíveis da Barlow:**
  - igual com `tnum` nas duas plataformas: aprovada;
  - igual já sem `tnum`: os algarismos são tabulares por padrão, o que também aprova, e o resultado é registrado;
  - diferente com `tnum` em alguma plataforma: aciona o plano B.
- **O que fica registrado:** o resultado vai para `reports/AAAA-MM-DD-phase2-tnum-spike.md`, com o que foi e o que não foi provado. A qualidade visual e a desambiguação de 1/l/I e 0/O dependem de olhar a tela e não são provadas pelo teste.

**Orçamento de tamanho:** o APK de debug pode crescer no máximo 1 MB com fontes e ícones, medido antes e depois. Se a Inter variável estourar o orçamento, entram instâncias estáticas com subconjunto latin e latin-ext.

**Licenças:** OFL (Inter, Barlow Condensed) e ISC (Lucide) exigem manter os avisos. A tela piloto ganha a entrada "Licenças de código aberto", que lista os textos. É a única adição visível à tela, e é uma obrigação de licença.

### Galeria

- `GalleryScreen` em `commonMain` do `:designsystem`, com uma página por componente mostrando todos os estados e tamanhos do inventário.
- Controles no topo: tema claro ou escuro, escala de fonte (1,0, 1,3 e 2,0, por um `LocalDensity` com `fontScale` substituído), "reduzir movimento" forçado e idioma (en ou pt-BR).
- **Android:** fica numa rota `gallery`. Ela não pode depender de `BuildConfig.DEBUG`, porque o APK distribuído aos usuários é o **debug** (AGENTS.md). A entrada aparece só depois de 7 toques na versão do app, em Configurações, como nas opções de desenvolvedor do Android, vale só até o app fechar e não grava nenhuma preferência.
- **iOS:** como não há app iOS, a galeria "roda no iOS" como teste: `iosSimulatorArm64Test` monta cada página nos dois temas e na escala 2,0 e verifica que nada quebra, que todo nó clicável tem rótulo e que todo alvo tem pelo menos 48 dp, pela árvore de semântica. A revisão visual no iOS fica para quando existir o `iosApp` (fase 7). Isso é uma adaptação do "Pronto quando" do ROADMAP, e fica registrado como não verificado.

### Tela piloto: Configurações

- A tela `settings` atual é refeita só com o sistema:
  - `TopBar`;
  - seções com `SectionHeader`;
  - "Aparência" (sistema, claro ou escuro, em `RadioRow`);
  - "Unidades" (kg ou lb);
  - "Descanso automático" (`SwitchRow`);
  - "Seus dados" (exportar e importar, com o `Dialog` de confirmação e o `ErrorBanner`);
  - "Atualizações do app" (todos os estados atuais do updater, com botões, progresso e erro);
  - o texto de privacidade;
  - "Licenças de código aberto".
- O comportamento é idêntico ao de hoje: as mesmas preferências, com as mesmas chaves do DataStore e os mesmos efeitos.
- A tela continua no `:app` até a fase 3, usando os componentes do `:designsystem`. Todas as strings dela vão para recursos, em inglês e pt-BR.
- Ela serve de validação: qualquer coisa que a tela precise e o sistema não tenha vira componente ou token, não código local.

## Como verificar

- **Testes de host e de `commonTest`, nos dois alvos:**
  - `ContrastTokensTest`: todos os pares da tabela, nos dois temas;
  - teste do formatador de números (en e pt-BR);
  - teste das strings: toda chave do inglês existe em pt-BR e não há chave sobrando.
- **Spike S1/S2:** o teste de `tnum` e de eixo variável fica verde no `iosSimulatorArm64Test` e no emulador, ou o plano B entra e é registrado.
- **Tokens obrigatórios:** uma checagem no CI (script em `tools/`) falha se encontrar, nos arquivos de componentes do `:designsystem` e na tela piloto, um destes literais fora dos arquivos de tokens:
  - `Color(0x`;
  - número seguido de `.dp` ou `.sp`;
  - `tween(` ou `spring(` com número.
- **Galeria no Android:** no emulador `Mooscles_Pixel8_API35`, cada página é vista nos dois temas e a 200%, sem texto cortado ou sobreposto. Também há capturas de tela se o Roborazzi entrar.
- **Galeria no iOS:** o teste de semântica descrito acima fica verde no simulador do CI.
- **TalkBack** no emulador, na tela piloto e na página da linha de série da galeria, com a ordem de leitura e as ações personalizadas.
- **Reduzir movimento:** com a escala de animação 0 no emulador, conferir que mola e deslocamento viraram fade.
- **Tamanho do APK:** o debug, antes e depois. O crescimento tem de ficar em até 1 MB.
- **Crítica de tela** (`critique-screen`) da tela piloto, e o `compat-reviewer`, porque a tela piloto mexe em `AppPreferences` (só leitura e escrita das mesmas chaves).
- **Relatório:** `reports/AAAA-MM-DD-phase2-verificacao.md`, com o que **não** foi verificado: VoiceOver, háptico e renderização num iPhone real, háptico num aparelho Android físico e o contraste sob luz forte de academia.

## O que fica para quem implementar

- O nome e a forma exatos da API de tema (sugestão: `MoosclesTheme.colors.bgBase`, `.type.numMd`, `.space.s4`, `.motion.fast`, por `CompositionLocal`), desde que os nomes dos tokens sejam os desta spec, trocando o ponto por camelCase.
- Se o `MaterialTheme` continua por baixo (mapeando os tokens para o `ColorScheme` do M3, para os componentes do Material que ainda forem usados) ou se os componentes são desenhados do zero. Minha preferência: componentes próprios sobre `foundation`, com o `ColorScheme` mapeado só para as telas antigas não ficarem com cores trocadas até serem redesenhadas.
- A ferramenta de conversão de SVG para `ImageVector`, e a lista final de ícones do Lucide (a prancha mostra 12 como amostra).
- A divisão em tasks, desde que o spike S1 venha primeiro e cada task termine com a galeria compilando nos dois alvos.
- O desenho exato dos ícones próprios e das primeiras ilustrações, seguindo as regras de traço e de cor desta spec.

## Decisões para o dono revisar

1. **Trocar o ícone do launcher** pela marca nova (duas anilhas quadradas de canto chanfrado, verdes, formando o "OO", sobre grafite). Recomendo trocar junto com a fase 3, quando o app inteiro muda de cara. É uma mudança visível para todo usuário que atualiza.
2. **Tema padrão:** mantive "Sistema" como padrão, como hoje, e desenhei o escuro como tema de marca. A alternativa é abrir no escuro por padrão.
3. **Separador decimal por idioma:** em pt-BR, as cargas passam a aparecer com vírgula (`82,5`).
4. **Produção das ilustrações (fase 4):** encomendar a um ilustrador com licença explícita, seguindo este estilo, ou desenhar internamente um piloto de 20 exercícios. Custo e prazo não foram pesquisados.
5. **Háptico sem opção no app:** segue o ajuste do sistema. Se ele quiser um interruptor "Vibração" em Configurações, isso entra na tela piloto como preferência nova (chave nova no DataStore, aditiva).

## Aprovação

- **2026-10-27:** o dono aprovou o design system ("gostei do designe system, ta aprovado"). Nas cinco escolhas em aberto, valem os padrões propostos nesta spec:
  1. o ícone novo (duas anilhas quadradas de canto chanfrado) entra na fase 3;
  2. o tema padrão continua "Sistema";
  3. as cargas aparecem com vírgula em pt-BR (`82,5`);
  4. as ilustrações começam com um piloto próprio de 20 exercícios;
  5. a vibração segue o ajuste do sistema, sem interruptor por enquanto.

  Qualquer uma pode ser revista depois.
- **2026-10-27:** o dono pediu outra forma para a marca: as anilhas redondas do "OO" lembravam um peito ("os dois OO do nome da app tá parecendo um peito; talvez deixar mais parecido com aqueles pesos mais quadrados com canto chanfrado"). A marca passou a usar anilhas quadradas de canto chanfrado, separadas por uma pegada com colares, no logotipo e nas propostas de ícone. Paleta, tipografia e os motivos redondos funcionais (anel do timer, disco do check, selo de recorde) não mudaram.
- **2026-10-27:** ajuste pedido pelo dono ("dá uma distorcida na vertical, aumentando só na altura, pra alinhar com os outros caracteres, e acho que faltou um pequeno abaulado também nos cantos"): as anilhas da marca foram esticadas só na vertical (cerca de 1,17×, com a mesma largura) para ir da linha de base à altura das maiúsculas, com o aro e o furo na mesma proporção. Cada vértice do chanfro ganhou um raio pequeno.

## Notas da crítica de componentes (Task 16)

- **2026-10-28:** a crítica sobre as capturas do emulador mudou quatro escolhas de cor desta spec. Os contrastes vêm dos hex da tabela de tokens (fórmula do WCAG 2.2) e estão no `ComponentColorsTest`.
  - **Toast de erro:** em vez de `bg.inverse`, usa a superfície de erro do `ErrorBanner` (`danger.subtle`, ícone `CircleAlert` em `danger.text`, texto e ação em `text.primary`). Nenhum token `danger.*` chega a 3:1 sobre `bg.inverse` (`danger.text` 2,44 no claro e 1,93 no escuro), e o toast de erro precisava se distinguir do informativo por ícone e cor. Medidas: texto 14,73 · 12,14; ícone 5,41 · 5,75.
  - **Ação do `ErrorBanner`:** botão secundário (`text.primary` com contorno `border.strong`) em vez de texto `accent.text`, que punha verde sobre vermelho. Texto 14,73 · 12,14; contorno 3,69 · 3,16. A mensagem não repete a ação ("Erro: Não foi possível salvar." + "Tentar de novo").
  - **Skeleton:** blocos `border.subtle` em vez de `bg.raised`. No claro, `bg.raised` é branco: 1,03:1 sobre `bg.surface` e 1,12:1 sobre `bg.base`, invisível. `border.subtle` dá 1,36 e 1,24 (escuro: 1,50 e 1,64). Fica abaixo de 3:1 de propósito: o skeleton é decorativo, e o único token neutro com 3:1 (`border.strong`) desenharia barras cinza-escuras sobre a página.
  - **Timer pausado:** o arco do anel passa a `text.tertiary` (5,96 · 5,63 sobre `bg.raised`; 4,28 · 4,22 sobre o trilho), o tempo vai para `text.secondary` e o dock mostra o ícone de pausa. `border.strong` ficaria em 2,53 contra o trilho no escuro.
- **2026-10-28:** valores vazios (coluna "anterior" sem série anterior) usam travessão "—". O meia-risca "–" da Barlow Condensed tem 375 unidades contra 320 do hífen, e lia como hífen.
- **2026-10-28:** a linha de série vira duas linhas também quando a linha única medida não cabe na largura, além da escala de 1,5. Com "80 kg × 6" a linha única precisa de cerca de 360 dp a 100% (estimativa pelas larguras dos glifos), então dentro de um `ExerciseCard` num aparelho de 360 a 412 dp ela já aparece em duas linhas. Antes, o valor anterior quebrava no meio ("55 kg × / 10").

## Notas de implementação

Divergências entre esta spec e o que a fase 2 entregou, uma por linha. O texto aprovado acima não foi mudado. As quatro escolhas de cor da crítica (toast de erro, ação do `ErrorBanner`, skeleton e timer pausado), o travessão dos valores vazios e as duas linhas da linha de série por largura estão na seção anterior, "Notas da crítica de componentes", e não se repetem aqui. Detalhes no [plano](../plans/2026-10-27-phase2-design-system.md) e no [relatório de verificação](../reports/2026-10-28-phase2-verificacao.md).

- **2026-10-28, orçamento de APK (F2):** "Como verificar" pede crescimento de até 1 MB. Valem três limites, checados no CI por `tools/check-apk-budget.ps1`: fontes até 900 KiB, fontes e ícones juntos até 1 MiB (o número desta spec) e o APK debug inteiro crescendo até 1,5 MiB (decisão 15 do plano), porque o runtime de recursos do CMP e o código do sistema, sem R8 no debug, não são fontes nem ícones. Medido no fim: 630 KiB, 816 KiB e 1,26 MiB.
- **2026-10-28, spike S1/S2 (F28):** rodou num módulo descartável, `:fontspike`, na branch `spike/phase2-tnum`, e não no `commonTest` do `:designsystem`. O que ficou na branch da fase é o `TnumRegressionTest`, em `designsystem/src/uiTest` (emulador e simulador), mais o `tools/check_fonts.py` no CI.
- **2026-10-28, strings em três arquivos (F28):** em vez de um `strings.xml`, `strings_components.xml`, `strings_gallery.xml` e `strings_settings.xml`, em `values/` e `values-pt-rBR/` do `:designsystem`.
- **2026-10-28, reduzir movimento (F28):** o `expect/actual` se chama `rememberSystemReducedMotion()`, e não `rememberReducedMotion()`. O valor efetivo é `MTheme.reducedMotion`, que a galeria pode forçar.
- **2026-10-28, háptico no iOS (decisão 8 do plano):** o `actual` do iOS chama direto os geradores do UIKit (`UIImpactFeedbackGenerator`, `UINotificationFeedbackGenerator`, `UISelectionFeedbackGenerator`), sem passar pelo `LocalHapticFeedback`. O S3 ficou reduzido a compilar e testar o mapeamento.
- **2026-10-28, tokens além das tabelas:** as medidas e os tempos do inventário viraram tokens nomeados (`MoosclesSizes` e `MoosclesTiming`, decisão 7 do plano). Entraram também, durante a implementação: `progressBarHeight` 8 dp, `progressIndeterminateFraction` 0,3 e `skeletonMinAlpha` 0,5 (barra de progresso e skeleton), `dialogMaxWidth` 360 dp, `borderSelected` 2 dp (anel do rádio selecionado) e `textCursor` 2 dp (cursor do campo numérico). Todos têm o valor fixado em teste.
- **2026-10-28, checagem de tokens:** além dos literais desta spec, o script reprova `delay(` com número, as cores nomeadas (`Color.White`, `Color.Red`…; `Transparent` e `Unspecified` passam) e `Primitives.` fora de `tokens/` (F16, F17).
- **2026-10-28, `SectionHeader` (F4):** usa `title.sm`, sem caixa alta. O `label.caps` fica para cabeçalho de coluna, e o texto da semântica é sempre o original.
- **2026-10-28, um `accent.solid` por tela (F5):** em Configurações, Exportar e Importar são `Secondary`. A ação da fase do updater (Baixar, Instalar, Tentar de novo…) é a única `Primary`.
- **2026-10-28, "OK" (F27):** o botão que fecha o resultado do import continua "OK" em inglês, idêntico ao de hoje, porque os testes do `:app` o exigem. Em pt-BR é "Fechar", um verbo.
- **2026-10-28, mensagens do updater e do backup em inglês (decisão 11 do plano):** `UpdateUi.message`, `BackupUi.message` e `BackupUi.working` chegam prontas do `MoosclesViewModel` (`:shared`, F26) e ficam em inglês até a fase 6. Com o aparelho em pt-BR, Configurações fica em português com essas mensagens em inglês.
- **2026-10-28, "W" no aquecimento:** em inglês o tipo de série é "W" (*warm-up*); o "A" desta spec vale para o pt-BR. Drop e falha são "D" e "F" nos dois.
- **2026-10-28, ordem das seções e rota (decisão 12 do plano):** a ordem é a desta spec, com "Atualizações do app" depois de "Seus dados" (antes vinha primeiro). Por isso a rota virou `settings?section={section}`, e o pedido do updater (notificação ou `updateSettingsRequest`) abre `settings?section=updates` já na seção. As licenças abrem na rota `licenses`. Saíram o sobretítulo "Make it yours" e o subtítulo, porque o `TopBar` já diz "Configurações".
- **2026-10-28, linha da versão e galeria:** a linha da versão instalada ("Instalada: 1.4.5 · build 16") é texto, sem papel de botão; os 7 toques chegam por `pointerInput`. Assim o TalkBack não oferece uma ação sem resultado, e a galeria, que é ferramenta de desenvolvimento, não abre por leitor de tela.
- **2026-10-28, idioma da galeria:** ao sair da galeria, o idioma do sistema é restaurado antes de a tela seguinte compor (um `OnDestinationChangedListener` restaura de forma síncrona), em vez de reconstruir a árvore. A reconstrução pelo `LocaleEpoch` fica só para quando a galeria sai da composição sem navegar, porque reconstruir o `MoosclesTheme` recriaria o `NavController` e perderia a posição na navegação.
- **2026-10-28, controle de movimento da galeria:** em vez de um "reduzir movimento" forçado liga/desliga, são três opções (Sistema, Reduzido, Completo), e a galeria começa em Sistema. O liga/desliga começava desligado e escondia o ajuste do sistema.
- **2026-10-28, `EmptyState` (F28):** a ação primária é opcional (`primaryAction` anulável), e a "ilustração de giz" é por ora o ícone próprio a 64 dp em `text.tertiary`, até as ilustrações da fase 4.
- **2026-10-28, "Descanso concluído" (F28):** o dock e o expandido mostram o estado `Done` e disparam o háptico uma vez; os 3 s de exibição ficam com quem chama (fase 3), com o token `timerDoneHold`.
- **2026-10-28, timer na fase `Done`:** o anel fica cheio e mostra um check, além do texto "Descanso concluído". A spec só define o texto.
- **2026-10-28, ±15 s:** o rótulo visível é "−15 s" e "+15 s", e o acessível é por extenso ("Menos 15 segundos", "Mais 15 segundos"). Os dois disparam o háptico do passo do stepper (F7).
- **2026-10-28, dock e timer expandido com fonte grande:** o dock empilha os botões também quando a linha não cabe na largura medida, além da escala de 1,5, e o tempo fica numa linha só. No expandido, o tempo diminui até caber no anel, nunca abaixo de `num.lg`.
- **2026-10-28, leitura da linha de série:** a linha continua sendo um nó de resumo com as cinco ações personalizadas, mas os campos de carga e repetições seguem focáveis e editáveis pelo leitor de tela, e o check é um alternável próprio ("Concluir série 4", marcado ou não), como esta spec pede. A primeira versão limpava a semântica dos filhos e escondia os campos.
- **2026-10-28, stepper ao segurar (F18):** um toque curto dá um passo ao soltar; segurar dá um passo aos 400 ms e depois um a cada 80 ms, e soltar não acrescenta nada. Enter, Enter do teclado numérico e Espaço também dão um passo.
- **2026-10-28, mola do fim do treino:** `spring.finish` parte da mesma escala do check (0,92), que esta spec não define.
- **2026-10-28, `MTextField`:** ganhou `suffix` (a unidade "min" do campo de descanso de Configurações) e `singleLine`. A seleção de texto só aparece com o campo focado.
- **2026-10-28, botões e campos na largura dada (N4 da crítica):** um `MButton` ou `MTextField` com `fillMaxWidth()` ou largura fixa desenha o fundo e o contorno na largura toda, e não só em volta do texto. O botão S continua com 40 dp de altura centrado no alvo de 48 dp.
- **2026-10-28, ícone do botão com fonte grande:** o ícone do `MButton` cresce com a escala da fonte (`icon20 × fontScale`), para acompanhar o rótulo a 200%.
- **2026-10-29, háptico de recorde depois do de concluir (fase 3):** a tabela de háptico desta spec diz que o de recorde substitui o de concluir. Na fase 3 o check não pode esperar a confirmação do recorde (a regra mora no `WorkoutPresentation`), então o de concluir toca no toque e o de recorde toca quando a confirmação chega, mesmo que ela venha em menos de 150 ms. É a única sequência dupla permitida. Por isso, no treino ativo, o `SetRow` recebe `recordHaptic = false` e a tela toca o `PersonalRecord` uma vez por aviso. Ver o D17 e as [notas de implementação da fase 3](2026-10-29-phase3-active-workout-design.md#notas-de-implementação).
