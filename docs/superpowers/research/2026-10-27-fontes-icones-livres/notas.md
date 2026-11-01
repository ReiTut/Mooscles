# Fontes e ícones livres para a nova identidade visual do Mooscles (Android + iOS via Compose Multiplatform)

Pesquisa feita em 2026-10-27 com ~25 chamadas de busca/fetch. Páginas do Google Fonts e da referência de API do Compose são renderizadas por JS e não puderam ser lidas; usei os METADATA.pb do repositório google/fonts, READMEs e sites dos projetos. Tudo o que não tem link está em "Lacunas".

## Fontes de exibição/UI com bons numerais, licença e suporte a `tnum` no Compose (Android e CMP/iOS)

### Takeaway
Inter (OFL, variável, `tnum` e zero cortado documentados, 147 idiomas) e Barlow/Barlow Condensed (OFL, latin-ext, estáticas) são os candidatos mais bem fundamentados. Space Grotesk tem tabulares documentados. Que o Compose honra `fontFeatureSettings = "tnum"` no Android é esperado, mas eu NÃO consegui confirmar por fonte primária, e no iOS (CMP/Skia) não encontrei nenhuma confirmação: precisa de teste em aparelho.

### Cited Findings
- Inter: licença OFL 1.1; mais de 2.000 glifos, 147 idiomas; pesos 100–900, tamanhos ópticos "text" e "display"; recursos OpenType incluem zero cortado, números tabulares, conjuntos estilísticos para desambiguar caracteres; x-height alta nos desenhos de texto — [rsms.me/inter](https://rsms.me/inter/)
- Inter no Google Fonts: OFL; subsets cyrillic, greek, latin, latin-ext, vietnamese; eixos variáveis `opsz` 14–32 e `wght` 100–900 — [google/fonts METADATA Inter](https://raw.githubusercontent.com/google/fonts/main/ofl/inter/METADATA.pb)
- Barlow Condensed: OFL; subsets latin, latin-ext, vietnamese; normal e itálico em 9 pesos (100–900), listado sem eixos variáveis — [METADATA Barlow Condensed](https://raw.githubusercontent.com/google/fonts/main/ofl/barlowcondensed/METADATA.pb)
- Barlow: OFL; latin, latin-ext, vietnamese; 18 arquivos estáticos (9 pesos x 2 estilos), aparentemente sem arquivo variável — [METADATA Barlow](https://raw.githubusercontent.com/google/fonts/main/ofl/barlow/METADATA.pb)
- Space Grotesk: o repositório do projeto lista figuras tabulares entre os recursos OpenType (derivada de Space Mono) — [fk0/font-SpaceGrotesk](https://github.com/fk0/font-SpaceGrotesk) (espelho, não o repositório oficial)
- Geist (Vercel): licença OFL 1.1, há Geist Mono; o README não informa recursos OpenType nem cobertura de idiomas — [vercel/geist-font](https://github.com/vercel/geist-font)
- Um guia observa que nem todas as Google Fonts trazem numerais tabulares; só conferindo a lista de recursos de cada fonte — [fontalternatives.com](https://fontalternatives.com/blog/best-fonts-legible-numerals-ui/)
- Compose (Android): `FontVariation` configura eixos padrão (peso, largura, inclinação) de qualquer fonte variável; fontes variáveis só funcionam no Android O (API 26) ou superior; fontes baixáveis não suportam variáveis; a doc recomenda fallback estático abaixo da API 26 — [developer.android.com/develop/ui/compose/text/fonts](https://developer.android.com/develop/ui/compose/text/fonts)
- Essa mesma página de doc não menciona `fontFeatureSettings` nem `tnum` — mesma fonte acima.
- CMP: fontes ficam em `composeResources/font`; TTF, OTF, TTC e formatos variáveis são suportados em todas as plataformas (WOFF/WOFF2 só web e macOS); acesso por `Font(Res.font.x, weight, style)` — [kotlinlang.org CMP resources](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html)
- A doc de CMP não descreve como escolher eixos variáveis nem recursos OpenType — mesma fonte.
- Busca específica por `fontFeatureSettings` + iOS no CMP não retornou nenhum issue ou doc; só analogias de outros frameworks (React Native ignora no iOS) — resultado de busca, sem fonte de confirmação.

### Inferences
- Para minimizar risco no iOS, prefira fontes com **numerais tabulares por padrão** (ou uma versão mono/“tabular” de arquivo próprio) em vez de depender de `tnum`. Fontes monoespaçadas (Geist Mono, JetBrains Mono, IBM Plex Mono) resolvem o problema do timer sem recurso OpenType, ao custo de aparência mais "técnica".
- Barlow Condensed estático (poucos pesos, ex.: Medium/SemiBold/Bold) mantém o peso do APK baixo; fonte variável embutida em CMP exige validar `FontVariation` no iOS e fallback abaixo da API 26 (verifique o minSdk atual do Mooscles).
- Inter com `opsz` (display) é boa para números grandes de peso/reps, mas o uso do eixo `opsz` no CMP/iOS precisa de teste.

### Gaps
- Confirmação oficial de que `TextStyle.fontFeatureSettings` está implementado no Compose Android e no CMP/iOS (Skia/HarfBuzz): não encontrei texto da referência de API (página truncada) nem issue do CMP. Recomendo um spike: renderizar "1111" vs "8888" com e sem `tnum` em ambas plataformas.
- Suporte a `tnum` em Manrope, Rubik, Figtree, DM Sans, Sora, Outfit, Archivo, Oswald, Bebas Neue, Barlow, IBM Plex, JetBrains Mono: não verificado por fonte. (Bebas Neue é só maiúsculas e tem uma única espessura; não confirmei por fonte.)
- Cobertura pt-BR de cada fonte além de Inter e Barlow (latin-ext): não verificada; todas as famílias do Google Fonts costumam ter latin/latin-ext, mas só medi as duas acima.
- Tamanhos em KB dos arquivos TTF/variável: não obtidos.
- Zero cortado/desambiguação 1/l/I: documentado só para Inter (recursos OpenType, e precisa ser ativado por feature, o que reintroduz a dúvida do `fontFeatureSettings`); demais fontes não avaliadas.
- Fontes com licença própria, não OFL (ex.: Oswald é OFL no Google Fonts, mas não conferi): não verificado.
- Texto da OFL sobre embutir em app: não lido nesta rodada. (Do conhecimento geral, e NÃO verificado aqui: a OFL permite embutir/redistribuir em apps gratuitos ou pagos, exige manter o aviso de copyright e a licença, e proíbe vender a fonte isoladamente.)

## Conjuntos de ícones: licença, ícones de fitness e uso em CMP

### Takeaway
Todos os candidatos têm licença permissiva (Apache 2.0, MIT ou ISC) compatível com app gratuito em APK e App Store. Para CMP, a rota mais prática é ImageVector (Phosphor, Lucide via bibliotecas KMP de terceiros) ou fonte variável (Material Symbols). Ícones de fitness confirmados: `dumbbell` (Lucide) e `barbell` (Phosphor).

### Cited Findings
- Material Symbols/Icons: Apache 2.0; atribuição bem-vinda mas não obrigatória; fontes variáveis Outlined, Rounded e Sharp com eixos opsz 20–48, wght 100–700, GRAD -50–200, FILL 0–100 — [google/material-design-icons](https://github.com/google/material-design-icons)
- Material Symbols em KMP: pacote `com.composables:icons-material-symbols-*-cmp` (outlined/rounded/sharp, v2.2.1 segundo a listagem), HLCaptain/symbols (fonte variável + ImageVector) e ClementVicart/compose-material-symbols (baseado em fonte variável) — [resultado de busca klibs.io](https://klibs.io/package/com.composables/icons-material-symbols-rounded-filled-cmp), [composables.com](https://composables.com/icons/icon-libraries/material-symbols)
- Phosphor: licença MIT; ~1.240+ ícones; pesos por pasta (inclui duotone); SVG e metadados com codepoint para fonte — [phosphor-icons/core](https://github.com/phosphor-icons/core)
- Phosphor tem o ícone `barbell` — [composables.com/phosphor](https://composables.com/icons/icon-libraries/phosphor/barbell)
- Phosphor em KMP (Android, iOS, Desktop, Web): `com.adamglin:phosphor-icon` 1.0.0 e `cortenaui/phosphor_icons` — [klibs.io adamglin](https://klibs.io/project/adamglin0/compose-phosphor-icon)
- Lucide: licença ISC; mais de 1.600 SVGs; fork do Feather — [lucide-icons/lucide](https://github.com/lucide-icons/lucide)
- Lucide tem o ícone `dumbbell` (tags barbell, weight, workout, gym); nenhum `kettlebell` encontrado — [lucide.dev/icons/dumbbell](https://lucide.dev/icons/dumbbell)
- Lucide em Compose/KMP: `com.composables:icons-lucide`, `lucide-compose` (ImageVector, CMP), `lucide-icon-kmp` (snapshot) — [klibs.io](https://klibs.io/package/com.composables/icons-lucide), [lucide-compose](https://klibs.io/project/thelacspace/lucide-compose)
- Tabler: licença MIT; 6.257 ícones (5.203 outline, 1.054 filled); SVG, webfont, PNG, componentes de frameworks web — [tabler/tabler-icons](https://github.com/tabler/tabler-icons)
- Existe um kettlebell em Hugeicons (não é Lucide); licença do Hugeicons não verificada — [shadcn.io hugeicons-kettlebell](https://www.shadcn.io/icon/hugeicons-kettlebell)
- Composables.com distribui bibliotecas de ícones (Material Symbols, Lucide, Phosphor e outras, 17.043 ícones no total) como ImageVector para Compose — [composables.com/blog/icons-for-android-and-compose](https://composables.com/blog/icons-for-android-and-compose)

### Inferences
- ImageVector gerado só inclui os ícones usados se o código usar referências individuais (R8/linker removem o resto); fonte variável de Material Symbols é um arquivo único grande (centenas de KB a MB; tamanho não verificado). Para um app com ~30–60 ícones, ImageVector individual tende a ser mais leve e previsível no iOS.
- Cobertura de fitness é fraca em todos os sets genéricos (halter/barra sim; kettlebell, anilha, grupos musculares geralmente não): provavelmente será preciso desenhar 5–15 ícones próprios (kettlebell, anilha, máquina, cabo) no mesmo estilo de traço do set escolhido.
- Lucide (ISC) e Phosphor (MIT) com pesos variados são os de estilo mais coeso para "traço" consistente; Phosphor oferece pesos (thin a bold, fill, duotone) que combinam com tipografia de pesos variados.

### Gaps
- Lista exata de ícones de fitness em Phosphor (dumbbell, timer, heartbeat), Tabler (dumbbell/barbell), Material Symbols (`fitness_center`, `timer`, `exercise`/`sports_gymnastics` etc.), Remix e Fluent: não confirmados por fonte. Phosphor `dumbbell`/`timer` não apareceram nas buscas; ausência não prova inexistência.
- Licença e conteúdo de Remix Icon e Fluent UI System Icons: não pesquisados nesta rodada (do conhecimento geral, não verificado: Remix tem licença própria "Remix Icon License v1.0"/Apache em versões recentes; Fluent é MIT).
- Licença exata das bibliotecas KMP de terceiros (wrappers) e se herdam a licença dos ícones: não verificado.
- Tamanho real (KB) por abordagem (fonte variável vs ImageVector): não medido.
- Material Symbols em CMP: se `FILL`/`wght` variáveis funcionam via FontVariation no iOS: não verificado.

## Ilustrações e mapas musculares/corporais com licença permissiva

### Takeaway
Existem mapas musculares SVG open source (família react-native-body-highlighter, MIT), mas a origem da arte vetorial não está documentada nos READMEs lidos; é preciso auditar antes de reutilizar. Não achei set de ilustrações de anatomia fitness com licença claramente livre (CC0/OFL-like).

### Cited Findings
- react-native-body-highlighter (HichamELBSI): licença MIT; grupos: frente (tibialis, oblíquos, peito, bíceps, abdômen, quadríceps, joelhos), costas (abdutores, dorsal superior, lombar, posterior da coxa, glúteo), ambos (trapézio, tríceps, antebraço, adutores, panturrilha, pescoço, deltoides, mãos, pés) — [HichamELBSI/react-native-body-highlighter](https://github.com/HichamELBSI/react-native-body-highlighter)
- O README não diz de onde vêm os caminhos SVG — mesma fonte.
- react-body-highlighter (giavinh79) diz que seus polígonos SVG vieram do react-native-body-highlighter; licença não confirmada nos resultados — [giavinh79/react-body-highlighter](https://github.com/giavinh79/react-body-highlighter)
- Fork teambuildr listado como MIT no jsDelivr; react-body-map também MIT — [jsdelivr teambuildr](https://www.jsdelivr.com/package/npm/@teambuildr/react-native-body-highlighter), [jsdelivr react-body-map](https://www.jsdelivr.com/package/npm/react-body-map)

### Inferences
- Como os paths são SVG, dá para converter para ImageVector/Compose `Path` e colorir por grupo muscular; custo é mapear ids de músculos do catálogo do Mooscles para os slugs acima.
- Cadeia de proveniência (MIT sobre arte de origem desconhecida) é um risco para publicação na App Store; alternativa mais segura é redesenhar um mapa simplificado próprio inspirado na estrutura.

### Gaps
- Origem/licença real da arte dos mapas musculares, se foi derivada de material com copyright: não verificada.
- Outros sets (ex.: ilustrações de exercícios em domínio público/CC0, wger, free-exercise-db): não pesquisados.
- Pt-BR: não há rótulos de músculos nesses pacotes; seriam do app.

## Acessibilidade, legibilidade e recomendação de par tipográfico

### Takeaway
Pareamento sugerido, sujeito a spike de `tnum` no iOS: **Barlow Condensed** (números grandes de timer/peso, estática, OFL) + **Inter** (UI e texto, variável ou estática, OFL, 147 idiomas, zero cortado e desambiguação via features), com ícones **Lucide (ISC)** ou **Phosphor (MIT)** como ImageVector e ícones próprios para itens ausentes. A recomendação é uma inferência: acessibilidade de caracteres (1/l/I, 0/O) só foi documentada para Inter.

### Cited Findings
- Inter tem x-height maior nos desenhos de texto, ink traps, desenhos "display" para títulos grandes, zero cortado e conjuntos estilísticos para desambiguação — [rsms.me/inter](https://rsms.me/inter/)
- Barlow Condensed cobre latin-ext (inclui acentos do pt-BR) e vai de Thin a Black — [METADATA Barlow Condensed](https://raw.githubusercontent.com/google/fonts/main/ofl/barlowcondensed/METADATA.pb)
- Recursos OpenType (zero cortado, tabulares, desambiguação) exigem ativação por feature; no Compose isso depende de `fontFeatureSettings` (não confirmado, ver primeira seção) — [rsms.me/inter](https://rsms.me/inter/)
- Material Symbols: Apache 2.0, atribuição opcional — [google/material-design-icons](https://github.com/google/material-design-icons)

### Inferences
- Plano B se `tnum` falhar no iOS: usar Geist Mono ou outra mono (OFL, Geist) só nos timers; ou subir um subconjunto da fonte com tabulares como padrão (pyftsubset/fonttools) — a OFL costuma exigir renomear a fonte modificada se tiver Reserved Font Name (não verificado).
- Fontes embutidas e licenças OFL/Apache/MIT/ISC: exigir apenas manter avisos de copyright/licença; criar tela "Licenças de código aberto" no app para cumprir (do conhecimento geral; não verificado por fonte nesta rodada).

### Gaps
- Testes de legibilidade reais (1/l/I, 0/O em Barlow Condensed) não feitos; é preciso renderizar em aparelho.
- Contraste/WCAG não avaliado (depende de cores, fora do escopo).
- Comparação visual com Oswald, Bebas Neue, Rubik, Manrope, Sora, Outfit, DM Sans, Figtree, Archivo, IBM Plex, JetBrains Mono: não pesquisada; não há dados nas notas para recomendá-las ou descartá-las.
