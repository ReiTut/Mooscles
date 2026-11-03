# Figuras de mapa muscular (vetores open source para front/back com ~17 regiões)

Pesquisa feita em 2026-10-29. As licenças foram lidas no arquivo LICENSE/CREDITS de cada repositório (via `raw.githubusercontent.com` e API do GitHub), salvo quando marcado como "não verificado". Datas de "pushed" vêm da API do GitHub no dia da pesquisa.

Regiões-alvo do Mooscles (17): peito, deltoide anterior, lateral e posterior, bíceps, tríceps, antebraço, abdômen, oblíquos, trapézio, dorsais (lats), lombar, glúteos, quadríceps, posteriores de coxa, adutores/abdutores, panturrilhas.

## Quais projetos existem, com que licença, procedência, regiões, tamanho, estilo e manutenção?

### Takeaway
Dois projetos têm licença permissiva, procedência declarada como trabalho original e cobrem quase exatamente as 17 regiões, inclusive deltoide anterior/lateral/posterior separado: **Jsplice/MuscleMap (`@musclemap/assets`, MIT)** e **vulovix/body-muscles (Apache-2.0)**. O conhecido `react-native-body-highlighter` (MIT) tem só um "deltoids" e não separa as dorsais. O conjunto realista mais detalhado (Ryan Graves, Figma, CC BY 4.0) também é aceitável, desde que se dê atribuição.

### Cited Findings

**1. Jsplice/MuscleMap (`@musclemap/assets`): MIT, o melhor encaixe nas 17 regiões**
- O LICENSE é MIT ("Copyright (c) 2026 Jsplice / MuscleMap contributors"). Tem um adendo dizendo que o MIT cobre "EVERYTHING in this repository", incluindo "the SVG muscle path data in `packages/assets` (original traced vector work)" e as fotos de corpo. A API do GitHub classifica a licença como `NOASSERTION`/"Other" por causa desse adendo, mas o texto é MIT puro mais uma nota de escopo — [LICENSE](https://raw.githubusercontent.com/Jsplice/MuscleMap/HEAD/LICENSE); [repo](https://github.com/Jsplice/MuscleMap)
- Procedência: os paths são "original vector work authored for this project", traçados à mão no Inkscape. As fotos de referência de fundo foram "generated with OpenAI image tooling from the maintainer's own prompts and then manually edited/traced over" — [ASSET_PROVENANCE.md](https://raw.githubusercontent.com/Jsplice/MuscleMap/HEAD/ASSET_PROVENANCE.md)
- Os IDs de superfície cobrem: `CHEST`, `SHOULDER_FRONT`, `SHOULDER_SIDE`, `SHOULDER_REAR`, `BICEPS`, `TRICEPS`, `FOREARM`, `CORE`, `OBLIQUE`, `TRAPEZIUS`, `RHOMBOID`, `LATISSIMUS`, `LOWER_BACK`, `GLUTEUS`, `QUADRICEPS`, `HAMSTRINGS`, `ADDUCTOR`, `ABDUCTOR` e `CALVES`, cada um com `_LEFT`/`_RIGHT` — [surface-ids.ts](https://raw.githubusercontent.com/Jsplice/MuscleMap/HEAD/packages/assets/src/surface-ids.ts)
- Há corpos masculino e feminino, de frente e de costas, em TS com path data absoluto e `viewBox: "0 0 1024 1536"`, mais um contorno `BODY`. Os arquivos têm `male-front.ts` 56,8 KB, `male-back.ts` 75,4 KB, `female-front.ts` 58,4 KB e `female-back.ts` 52,8 KB — [árvore do repo](https://github.com/Jsplice/MuscleMap/tree/HEAD/packages/assets/src); [male-front.ts](https://raw.githubusercontent.com/Jsplice/MuscleMap/HEAD/packages/assets/src/male-front.ts)
- O estilo é vetor chapado (preenchimento por região) pensado para ficar sobre uma foto "photoreal hybrid", mas os paths funcionam sozinhos — [README](https://github.com/Jsplice/MuscleMap)
- Manutenção: repo criado em 2026-06-03, último push em 2026-10-23, 13 estrelas. O README declara "Status — `1.0.0`, stable". O npm `@musclemap/assets` está em 1.1.0 (2026-09-15), com licença MIT — [repo](https://github.com/Jsplice/MuscleMap); [npm registry](https://registry.npmjs.org/@musclemap/assets)

**2. vulovix/body-muscles: Apache-2.0, granular (70+ regiões)**
- O LICENSE é Apache-2.0 e o NOTICE diz "Copyright 2024 Ivan Vulović". O NOTICE precisa acompanhar a redistribuição (Apache §4d) — [LICENSE](https://raw.githubusercontent.com/vulovix/body-muscles/HEAD/LICENSE); [NOTICE](https://raw.githubusercontent.com/vulovix/body-muscles/HEAD/NOTICE)
- O projeto tem 70+ regiões com IDs e lado. A frente inclui `shoulder-front-*`, `shoulder-side-*`, `chest-upper/lower-*`, `abs-upper/lower-*`, `obliques-*`, `serratus-anterior-*`, `biceps-*`, `forearm-*`, `quads-*`, `adductors-*`, `hip-flexor-*` e `tibialis-anterior-*`. As costas incluem `deltoid-rear-*`, `traps-upper/mid/lower-*`, `lats-upper/mid/lower-*`, `triceps-long/lateral-*`, `lower-back-erectors/ql-*`, `gluteus-maximus/medius-*` (o medius serve de "abdutor"), `hamstrings-medial/lateral-*` e `calves-gastroc-*/soleus-*`. Também há `MUSCLE_GROUPS` prontos — [muscle-groups.ts](https://raw.githubusercontent.com/vulovix/body-muscles/HEAD/src/data/muscle-groups.ts); [muscles.front.ts](https://raw.githubusercontent.com/vulovix/body-muscles/HEAD/src/data/muscles.front.ts); [muscles.back.ts](https://raw.githubusercontent.com/vulovix/body-muscles/HEAD/src/data/muscles.back.ts)
- O formato é path com coordenadas relativas no estilo Inkscape (`m 11.67,6.35 ...`), num sistema minúsculo: `viewBox` "0 0 35 93" para a frente e "37 0 35 93" para as costas, na mesma prancheta. Os dados somam cerca de 12,8 KB (frente) e 11,9 KB (costas), ou seja, são leves — [BodyChart.ts](https://raw.githubusercontent.com/vulovix/body-muscles/HEAD/src/BodyChart.ts); [árvore](https://github.com/vulovix/body-muscles)
- Manutenção: criado em 2026-04-14, último push em 2026-04-16 (só commits "init"), 36 estrelas, versão 1.0.0 no npm — [repo](https://github.com/vulovix/body-muscles); [CHANGELOG](https://raw.githubusercontent.com/vulovix/body-muscles/HEAD/CHANGELOG.md)
- O README não diz de onde vieram os paths ("70+ Anatomical Regions — Granular muscle mapping with accurate SVG paths") — [README](https://github.com/vulovix/body-muscles)

**3. HichamELBSI/react-native-body-highlighter: MIT, o mais usado**
- O LICENSE é MIT ("Copyright (c) 2022 ELABBASSI Hicham"). A licença só foi adicionada em 2022 depois de um pedido no issue #20 — [LICENSE](https://raw.githubusercontent.com/HichamELBSI/react-native-body-highlighter/HEAD/LICENSE); [issue #20](https://github.com/HichamELBSI/react-native-body-highlighter/issues/20)
- Na frente há `chest`, `deltoids`, `biceps`, `triceps`, `forearm`, `abs`, `obliques`, `trapezius`, `quadriceps`, `adductors`, `calves`, `tibialis`, mais cabeça, mãos, pés etc. Nas costas há `upper-back`, `lower-back`, `gluteal`, `hamstring`, `deltoids`, `trapezius`, `triceps` etc. Não há deltoide anterior, lateral e posterior separados nem `lats` próprio (só `upper-back`) — [bodyFront.ts](https://raw.githubusercontent.com/HichamELBSI/react-native-body-highlighter/HEAD/assets/bodyFront.ts); [bodyBack.ts](https://raw.githubusercontent.com/HichamELBSI/react-native-body-highlighter/HEAD/assets/bodyBack.ts)
- Tem versões masculina e feminina, com paths separados em `left`/`right`. Os arquivos têm 25,2 KB (frente M), 21,5 KB (costas M), 32,3 KB (frente F) e 22,7 KB (costas F) — [árvore do repo](https://github.com/HichamELBSI/react-native-body-highlighter)
- Manutenção: último push em 2026-10-18, cerca de 300 estrelas, npm 3.2.0, cerca de 60,8 mil downloads entre 2026-09-29 e 2026-10-28 — [repo](https://github.com/HichamELBSI/react-native-body-highlighter); [npm downloads API](https://api.npmjs.org/downloads/point/last-month/react-native-body-highlighter)
- Nem README nem LICENSE dizem quem desenhou os paths, nem a partir de quê — [README](https://github.com/HichamELBSI/react-native-body-highlighter)

**4. Derivados do react-native-body-highlighter**
- `giavinh79/react-body-highlighter` (MIT): "The SVG polygons were leveraged from the React Native package react-native-body-highlighter". São polígonos de baixa resolução (`svgPoints`, cerca de 6 KB no total) com `front-deltoids`, `back-deltoids`, `upper-back`, `abductors`, `adductor` etc. O estilo é low-poly e o último push foi em 2026-09-20 — [README](https://github.com/giavinh79/react-body-highlighter); [metadata.ts](https://raw.githubusercontent.com/giavinh79/react-body-highlighter/HEAD/src/component/metadata.ts); [assets/index.ts](https://raw.githubusercontent.com/giavinh79/react-body-highlighter/HEAD/src/assets/index.ts)
- `gossamr/swift-body-highlighter` (MIT, SwiftUI) foi "ported from the React Native body highlighter" e diz: "All SVG paths used herein are copyright its author". Também admite que a arte é "limited by the licensable artistic resources available" — [README](https://github.com/gossamr/swift-body-highlighter)
- `lahaxearnaud/body-highlighter` (MIT) é fork DOM do react-body-highlighter — [repo](https://github.com/lahaxearnaud/body-highlighter)

**5. Arte de Ryan Graves (Figma Community, CC BY 4.0): realista e muito detalhada**
- `kit-g/flutter-body-atlas` (BSD-3-Clause, pub `flutter_body_atlas` 0.2.3) diz que os SVGs "are based on work by Ryan Graves", com Source https://www.figma.com/community/file/1320468164820924031 e licença CC BY 4.0. Os IDs foram modificados — [CREDITS.md](https://raw.githubusercontent.com/kit-g/flutter-body-atlas/HEAD/CREDITS.md); [repo](https://github.com/kit-g/flutter-body-atlas)
- `suryamolly/muscle_mapper` (MIT, pub 1.2.2) usa a mesma arte no estilo "advanced". Pede que os apps derivados "provide clear attribution to Ryan Graves in their application" — [CREDITS.md](https://raw.githubusercontent.com/suryamolly/muscle_mapper/HEAD/CREDITS.md)
- O SVG do flutter-body-atlas tem `viewBox` 0 0 587 1137 e 126 `<path>` na frente, com IDs anatômicos finos: `anterior_deltoid_l/r`, `lateral_deltoid_*`, `posterior_deltoid_*`, `pectoralis_major_*`, `latissimus_dorsi_*`, `trapezius_upper/middle/lower_*`, `rectus_abdominis_1..4`, `external_oblique_1..8`, `vastus_*`, `rectus_femoris_*`, `biceps_femoris_*`, `semitendinosus_*`, `gluteus_maximus/medius_*`, `adductor_longus/magnus_*`, `gastrocnemius_*`. Os grupos (`chest`, `shoulders`, `back`, `core`, `arms`, `legs`, `glutes`, `adductors`, `hamstrings`) também estão no arquivo. Tamanho: 186,4 KB (frente) e 132,6 KB (costas) — [muscle_layer_front.svg](https://raw.githubusercontent.com/kit-g/flutter-body-atlas/HEAD/assets/svg/muscle_layer_front.svg); [muscle_layer_back.svg](https://raw.githubusercontent.com/kit-g/flutter-body-atlas/HEAD/assets/svg/muscle_layer_back.svg)
- A política do Figma diz: "Free files are published under an Attribution 4.0 International (CC BY 4.0) license" e "can be shared and adapted for any purpose, including commercial purposes". Também diz que é preciso dar crédito e que "This isn't a public domain license" — [Figma Help: Community copyright and licensing](https://help.figma.com/hc/en-us/articles/360042296374-Figma-Community-copyright-and-licensing)

**6. Pacotes Flutter com arte própria ou de origem incerta**
- `EmilCes/muscle_selector` (MIT, pub 1.0.4): o LICENSE ficou com o modelo sem preencher ("Copyright (c) [year] [fullname]"). É um SVG único de 59,9 KB, com frente e costas no mesmo arquivo, e IDs `chest1/2`, `shoulder1..4`, `biceps`, `triceps`, `forearm1..4`, `abs1..8`, `obliques`, `lats`, `upper_back`, `lower_back`, `trapezius1..5`, `glutes`, `harmstrings` (com erro de grafia no ID), `quads1..4`, `adductors`, `abductor`, `calves1..4` e `neck`. Os deltoides não estão identificados como anterior, lateral e posterior; só se sabe que dois IDs ficam na frente e dois nas costas. Nada diz de onde veio a arte, e a classe CSS `.land` sugere que o arquivo partiu de um modelo de mapa de países. Último push em 2025-08-24 — [LICENSE](https://raw.githubusercontent.com/EmilCes/muscle_selector/HEAD/LICENSE); [human_body.svg](https://raw.githubusercontent.com/EmilCes/muscle_selector/HEAD/assets/maps/human_body.svg); [CHANGELOG](https://raw.githubusercontent.com/EmilCes/muscle_selector/HEAD/CHANGELOG.md)
- `SquashVash/muscle-map` (MIT, pub `muscle_map` 1.3.0) é fork do muscle_selector e acrescenta `front_body.svg` (33 KB) e `back_body.svg` (31 KB) — [repo](https://github.com/SquashVash/muscle-map); [pub.dev](https://pub.dev/packages/muscle_map)
- `angujo/flutter_muscle_anatomy` (BSD-3-Clause, pub 1.3.0, push em 2026-10-29): os SVGs foram feitos no Inkscape e há fontes `source-*.svg`. Os IDs são anatômicos, com lado (`left_deltoid`, `left_pectoralis_major`, `left_latissimus_dorsi`, `left_rectus_abdominis`, `left_vastus_lateralis`…), mas o deltoide fica numa peça só. O README não credita autor externo — [repo](https://github.com/angujo/flutter_muscle_anatomy); [male_front.svg](https://raw.githubusercontent.com/angujo/flutter_muscle_anatomy/HEAD/assets/male_front.svg)
- `Lanhubs/body_heatmap` (MIT): diz "Code and original vector artwork: MIT". A arte é gerada por script Python (`tool/generate_anatomy.py`) com `viewBox` 0 0 240 640 e regiões `data-region` como `right.pectoralis_major`, `right.deltoid`, `right.latissimus_dorsi`, `right.gluteus_medius`… É bem estilizada e leve: 19 KB (frente) e 15 KB (costas). O pacote 3D opcional é CC BY-SA 4.0 e deve ser evitado — [README](https://github.com/Lanhubs/body_heatmap); [artwork/front.svg](https://raw.githubusercontent.com/Lanhubs/body_heatmap/HEAD/artwork/front.svg); [NOTICE 3D](https://raw.githubusercontent.com/Lanhubs/body_heatmap/HEAD/packages/bodychart_heatmap_3d/assets/NOTICE.md)
- Outros encontrados e não verificados a fundo: `flutter_body_heatmap` (pub 0.1.0, "23 anatomical muscle regions", MIT no GitHub, sem SVG no repo); `flutter_body_part_selector` (sem licença detectada no GitHub, então trate como todos os direitos reservados) — [pub flutter_body_heatmap](https://pub.dev/packages/flutter_body_heatmap); [repo flutter_body_part_selector](https://github.com/Amrut-03/flutter_body_part_selector)

**7. Fontes a evitar (copyleft ou sem licença)**
- wger: código AGPL-3.0, documentação CC-BY-SA-4.0. Os SVGs de músculos (`muscular_system_front/back.svg`) vêm do Wikimedia, segundo o arquivo SOURCES — [README wger](https://github.com/wger-project/wger); [SOURCES](https://github.com/wger-project/wger/tree/master/wger/core/static/images/muscles)
- Wikimedia "File:Muscular system.svg": de Termininja (2012), licença CC BY-SA 3.0, derivado de "Pectoralis major.png" e "Tibial anterior.png" — [Commons](https://commons.wikimedia.org/wiki/File:Muscular_system.svg)
- `crmapache/js-rich-body-highlighter` (MIT): usa ilustrações `.webp` com máscaras e 14 grupos. A arte de corpo é raster — [repo](https://github.com/crmapache/js-rich-body-highlighter)
- `VVGowda/muscle-svg` (MIT) é só um pipeline que vetoriza line art. A arte de entrada não vem junto (`input/README.md`) — [repo](https://github.com/VVGowda/muscle-svg)

### Inferences
- Para as 17 regiões do Mooscles, `@musclemap/assets` mapeia quase 1:1 (`CORE`→abdômen, `ABDUCTOR`+`ADDUCTOR`, `SHOULDER_FRONT/SIDE/REAR`). `RHOMBOID` pode ser fundido em trapézio ou em costas superiores. O body-muscles exigiria agrupar 70+ peças em 17, o que é fácil porque os IDs já são semânticos.
- A arte de Ryan Graves é a de melhor qualidade visual realista, mas tem 2 a 3 vezes mais bytes e exige atribuição visível no app (a tela de Licenças já existe no `:designsystem`). Para um diário de treino com mapa pequeno, esse detalhe tende a virar ruído visual.
- O `react-native-body-highlighter` é o mais popular, mas não serve sem redesenhar os deltoides e as dorsais, e a procedência dos paths não está documentada.

### Gaps
- Não consegui abrir a página do Figma de Ryan Graves (HTTP 403), nem pela API pública. A licença CC BY 4.0 vem só dos CREDITS de dois pacotes Flutter e da regra padrão do Figma. O autor pode ter adicionado outra licença por cima ("additional license types"), o que exige conferir manualmente, logado, em https://www.figma.com/community/file/1320468164820924031.
- O body-muscles não diz de onde vieram os paths. O "Copyright 2024" no NOTICE de um repo criado em 2026 sugere trabalho anterior do autor, mas isso não está confirmado.
- Não comparei visualmente os paths do muscle_selector, do body-muscles e do react-native-body-highlighter com ilustrações comerciais (Freepik, Shutterstock etc.) para descartar cópia não declarada.

## Os paths são extraíveis como SVG puro? Há disputas de licença ou riscos de procedência?

### Takeaway
Todos os candidatos vetoriais guardam os paths como strings `d` em texto, em `.ts`, `.swift` ou `.svg`, sem nada embutido em imagem, e podem ser extraídos por script. Não achei nenhuma disputa de licença pública. Os riscos são de procedência: paths sem autor declarado (react-native-body-highlighter, body-muscles, muscle_selector) e o traçado sobre fotos geradas por IA no MuscleMap.

### Cited Findings
- No react-native-body-highlighter, os paths ficam em arrays TS `path.left[]`/`path.right[]` com strings `d` absolutas (`M272.91 422.84c…`) — [bodyFront.ts](https://raw.githubusercontent.com/HichamELBSI/react-native-body-highlighter/HEAD/assets/bodyFront.ts)
- No MuscleMap, os objetos `{ id, side, d }` usam coordenadas absolutas ("flatten the labelled paths to absolute coordinates and emit a BodyDiagram"). O README documenta o fluxo Inkscape → `inkscape:label` → importação — [README seção Assets](https://github.com/Jsplice/MuscleMap); [male-front.ts](https://raw.githubusercontent.com/Jsplice/MuscleMap/HEAD/packages/assets/src/male-front.ts)
- No body-muscles, os objetos `{ id, name, view, path }` usam path relativo (`m`, minúsculo) — [muscles.front.ts](https://raw.githubusercontent.com/vulovix/body-muscles/HEAD/src/data/muscles.front.ts)
- O flutter-body-atlas e o muscle_selector usam SVG puro com `id` por `<path>`, e o flutter-body-atlas tem `fill-rule="evenodd"` — [muscle_layer_front.svg](https://raw.githubusercontent.com/kit-g/flutter-body-atlas/HEAD/assets/svg/muscle_layer_front.svg); [human_body.svg](https://raw.githubusercontent.com/EmilCes/muscle_selector/HEAD/assets/maps/human_body.svg)
- O único histórico de licença encontrado no react-native-body-highlighter é o issue #20 ("there's no license found"), fechado em 2022 com a inclusão do MIT pelo PR #23. Não houve disputa — [issue #20](https://github.com/HichamELBSI/react-native-body-highlighter/issues/20)
- O swift-body-highlighter atribui o copyright dos paths ao autor do RN e reconhece limitação de "licensable artistic resources" — [README](https://github.com/gossamr/swift-body-highlighter)
- O MuscleMap avisa: "under OpenAI's terms the maintainer owns the output to the extent permitted by law. Generated output is not guaranteed to be unique". Isso se refere às fotos. Os paths são traçado manual do mantenedor — [ASSET_PROVENANCE.md](https://raw.githubusercontent.com/Jsplice/MuscleMap/HEAD/ASSET_PROVENANCE.md)
- O LICENSE do muscle_selector tem placeholders sem preencher ("[year] [fullname]"), uma fragilidade formal — [LICENSE](https://raw.githubusercontent.com/EmilCes/muscle_selector/HEAD/LICENSE)

### Inferences
- No MuscleMap, só os paths entrariam no Mooscles, sem as fotos `.webp`. O risco da imagem de IA fica fora, e o que resta é o traçado declarado original sob MIT.
- O formato relativo do body-muscles não atrapalha: o `PathParser` do Compose (`addPathNodes`/`PathParser().parsePathString`) e o `tools/icons/gen.py` aceitam comandos relativos. Mesmo assim, normalizar para absoluto facilita revisar diffs.
- Os SVGs de Ryan Graves usam `fill-rule="evenodd"`, então o `ImageVector` gerado precisa de `pathFillType = PathFillType.EvenOdd` nessas peças.

### Gaps
- Não rodei nenhum conversor (`svg2compose`, Android Studio Vector Asset ou o `gen.py` do projeto) nesses arquivos para medir o tamanho final em Kotlin nem a fidelidade.
- Não verifiquei se há sobreposição ou lacunas entre regiões vizinhas (importante para hit-test e para pintar a intensidade).

## Existem bibliotecas Kotlin/Compose ou Compose Multiplatform de mapa muscular?

### Takeaway
Não encontrei biblioteca Kotlin, Jetpack Compose nem Compose Multiplatform de mapa muscular com licença e manutenção razoáveis. O ecossistema está em React/React Native, Flutter e SwiftUI, então o caminho no Mooscles é portar os paths para `ImageVector` no `:designsystem`.

### Cited Findings
- `gh search repos` por "muscle map compose", "body highlighter kotlin", "muscle compose multiplatform" e "muscle map android" retornou só `dimavrem22/Muscle-Map-Android-App` (sem licença, último update em 2022-09-03) — [GitHub search](https://github.com/search?q=muscle+map+android&type=repositories); [repo](https://github.com/dimavrem22/Muscle-Map-Android-App)
- Uma busca web por "Jetpack Compose muscle map body highlighter library" trouxe só bibliotecas web e React Native (body-highlighter, MuscleMap, react-native-body-highlighter) e bibliotecas Compose de mapas geográficos, sem relação com o assunto — [lahaxearnaud/body-highlighter](https://github.com/lahaxearnaud/body-highlighter); [Jsplice/MuscleMap](https://github.com/Jsplice/MuscleMap)
- Os ports existentes são SwiftUI (`gossamr/swift-body-highlighter`, que guarda paths em `BodyData.swift`, 167 KB) e Flutter (vários no pub.dev) — [swift-body-highlighter](https://github.com/gossamr/swift-body-highlighter); [pub.dev muscle_map](https://pub.dev/packages/muscle_map)

### Inferences
- O `:designsystem` já gera `ImageVector` a partir de SVG em `tools/icons/gen.py` (Lucide). Dá para estender o mesmo gerador a um "mapa muscular", com um `ImageVector` por vista e grupos nomeados, ou com uma lista de `(regionId, PathData)` desenhada num `Canvas` para colorir por intensidade e fazer hit-test por `Path.contains` ou por região.
- Como não há dependência externa, não há risco de manutenção de terceiros: os paths viram dados versionados no repo, com o aviso de licença em `composeResources/files/licenses/`.

### Gaps
- Não procurei em klibs.io nem no Maven Central por artefatos KMP sem repo no GitHub. A busca web genérica não achou nenhum, mas a busca não foi exaustiva.

## Quanto esforço para adaptar um deles a ~17 regiões versus desenhar um mapa próprio simples?

### Takeaway
Adaptar o `@musclemap/assets` (MIT) ou o body-muscles (Apache-2.0) é a opção mais barata. Os IDs já são semânticos, e o trabalho vira um script de mapeamento ID→região, mais conversão para Kotlin e testes, provavelmente de 1 a 2 dias. Desenhar um mapa próprio só compensa se o visual precisar seguir o design system à risca, e custa bem mais, porque exige ilustrar com coerência anatômica.

### Cited Findings
- O MuscleMap já tem 19 grupos lógicos com lados que cobrem as 17 regiões-alvo (lista de IDs acima). Trocar a arte, segundo o próprio projeto, é traçar no Inkscape, nomear por `inkscape:label` e importar — [surface-ids.ts](https://raw.githubusercontent.com/Jsplice/MuscleMap/HEAD/packages/assets/src/surface-ids.ts); [README](https://github.com/Jsplice/MuscleMap)
- O body-muscles traz `MUSCLE_GROUPS` (Shoulders, Arms, Chest, Back, Abdominals, Legs…) e IDs granulares, o que permite agrupar por prefixo (`lats-*`→dorsais, `traps-*`→trapézio, `gluteus-medius-*`→abdutores) — [muscle-groups.ts](https://raw.githubusercontent.com/vulovix/body-muscles/HEAD/src/data/muscle-groups.ts)
- O peso dos dados varia muito: o body-muscles tem cerca de 25 KB para as duas vistas, o MuscleMap tem cerca de 132 KB (masculino, frente + costas, com contorno) e o Ryan Graves tem cerca de 319 KB (masculino, frente + costas) — [body-muscles](https://github.com/vulovix/body-muscles); [MuscleMap](https://github.com/Jsplice/MuscleMap/tree/HEAD/packages/assets/src); [flutter-body-atlas](https://github.com/kit-g/flutter-body-atlas/tree/HEAD/assets/svg)
- Um mapa próprio, simples e gerado por código é viável, como mostra o `Lanhubs/body_heatmap`, que gera frente e costas (cerca de 19 KB e 15 KB) com um script Python e licencia como "original vector artwork: MIT" — [README](https://github.com/Lanhubs/body_heatmap); [artwork/front.svg](https://raw.githubusercontent.com/Lanhubs/body_heatmap/HEAD/artwork/front.svg)

### Inferences
- Estimativa de esforço (inferência, não medida) para adaptar: (1) script que lê os `.ts` ou `.svg` e emite Kotlin com `enum MuscleRegion` (17 valores) → `List<PathData>` por vista, em cerca de meio dia; (2) silhueta e cores por token (`MTheme.colors…`), sem literais fora de `tokens/`, por causa do `check-design-tokens.ps1`; (3) testes de cobertura (toda região tem ao menos um path em alguma vista, sem ID órfão) e hit-test; (4) aviso de licença na tela de Licenças (MIT/Apache NOTICE/CC BY). No total, de 1 a 2 dias, mais a revisão visual.
- Para desenhar um mapa próprio estilizado com ~17 regiões nas duas vistas: com proporções e encaixes bons, provavelmente vários dias de ilustração no Inkscape ou Figma, mais iterações de crítica visual. A vantagem é o controle total do estilo e da procedência.
- O orçamento de APK (`tools/check-apk-budget.ps1`) favorece o body-muscles ou o MuscleMap simplificado. Também dá para reduzir a precisão decimal dos paths, com SVGO ou arredondamento, antes de gerar o Kotlin.
- Recomendação provisória: começar pelo `@musclemap/assets` (MIT, procedência original declarada, mapeamento 1:1). O body-muscles (Apache-2.0) é o plano B se o tamanho pesar ou se o estilo do MuscleMap não combinar. Em qualquer caso, conferir visualmente as peças e guardar o hash do commit de origem no aviso de licença.

### Gaps
- Não renderizei nenhum dos candidatos lado a lado no estilo do Mooscles, então a adequação estética não foi avaliada.
- As estimativas de esforço são inferências sem medição, e o tamanho final em bytes do Kotlin gerado não foi medido.
