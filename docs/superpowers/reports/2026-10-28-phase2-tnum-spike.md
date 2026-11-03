# Fase 2: spike de `tnum` e do eixo de peso da Inter (S1/S2)

- **Data:** 2026-10-28
- **Branch do spike:** `spike/phase2-tnum` (no `origin`, nunca mesclada), saída de `origin/main` em `4f698a8`. Commits: `7649e0b` (módulo `:fontspike`, fontes, testes, `tools/check_fonts.py` e CI) e `df64ffd` (formato do `--lock`, igual ao do futuro `designsystem/font-sources.txt`)
- **CI:** [run 40694319851](https://github.com/pedroyuuki/Mooscles/actions/runs/40694319851) em `df64ffd`, verde em todos os jobs (`host`, `ios`, `changes` e os quatro `release-script`). O run de `7649e0b` ([40693720631](https://github.com/pedroyuuki/Mooscles/actions/runs/40693720631)) foi cancelado pelo push seguinte, pela regra de concorrência do workflow
- **Spec:** [fase 2](../specs/2026-10-27-phase2-design-system-design.md) · **Plano:** [fase 2, Task 1](../plans/2026-10-27-phase2-design-system.md) · **Pesquisa de fontes:** [P4](2026-10-27-fontes-icones-livres.md)
- **Evidências:** [2026-10-28-phase2-tnum-spike/](2026-10-28-phase2-tnum-spike/) (XML dos dois testes, linhas `SPIKE` do logcat e a saída do HarfBuzz no CI)

## Resumo

- **Decisão de fontes: A, aprovada como está.** A Barlow Condensed 500, 600 e 700 tem algarismos de largura igual com `tnum` no emulador Android e no simulador iOS, e o relógio `0:00`/`9:59` não muda de largura. Sem `tnum` os algarismos são proporcionais nas duas plataformas, então não é A'. O B1 (`pyftfeatfreeze`) não é necessário.
- **S2 aprovado:** o eixo `wght` da Inter variável se aplica nas duas plataformas (`8888` a 400 contra 700: 236 → 248 px no Android, 238 → 251 px no iOS). Não precisa da Inter estática.
- **Os dois controles passaram nas duas plataformas:** a Inter sem `tnum` é proporcional, e a Barlow carregou de verdade (é mais estreita que 0,85 × a Inter e diferente de `FontFamily.Default`).
- **HarfBuzz no host concorda:** `tools/check_fonts.py` mostra os mesmos avanços localmente e no job `host`, com `tnum` igual em todas as fontes e pesos e algarismos proporcionais sem `tnum`.
- **Licenças:** as duas são SIL OFL 1.1 **sem Reserved Font Name**.
- **O plugin `org.jetbrains.compose` 1.12.2 no build raiz não quebrou nada no `host`:** 520 testes de host (`:app` e `:shared`) passaram, mais o lint e o `assembleDebug`. No `ios`, os 162 testes do `:shared` passaram junto com os 7 do spike, no Xcode 26.4.1.

## Fontes

Todas baixadas por URL raw do `google/fonts` no commit fixo **`9862abc066c9b83bf84c3e8ed1fc2d8d48f9bbf0`** (HEAD da `main` em 2026-10-28T11:26:44Z), primeiro num diretório vazio fora do repositório, conferidas com `file` (todas "TrueType Font data") e com SHA-256. Depois do commit, as quatro URLs foram baixadas de novo e deram os mesmos hashes.

| Arquivo no `composeResources` | Origem (`ofl/…` em `9862abc…`) | Bytes | SHA-256 |
|---|---|---:|---|
| `inter_variable.ttf` | `inter/Inter[opsz,wght].ttf` | 876.576 | `29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031` |
| `barlow_condensed_medium.ttf` | `barlowcondensed/BarlowCondensed-Medium.ttf` | 103.360 | `262bd143292ce479ee0cd09a42b47ab173fca8e9c6eb5ed0b5c8a845bc371d17` |
| `barlow_condensed_semibold.ttf` | `barlowcondensed/BarlowCondensed-SemiBold.ttf` | 109.428 | `7b619d14bc2327509a9ef32b0890f709626f7ecc9ff61191c2a4314c5499d2d9` |
| `barlow_condensed_bold.ttf` | `barlowcondensed/BarlowCondensed-Bold.ttf` | 109.912 | `e476562ec9c1e16cf16475895b511f08c804f438cc9a9f80a44ea50a0eeb5b65` |
| (licença) | `inter/OFL.txt` | 4.377 | `5b9321a4298cfeb6b34354164a1c3afc3db114569984c502b9b35d988fd58c57` |
| (licença) | `barlowcondensed/OFL.txt` | 4.377 | `186d750eb496a4c17a76385f82be6aea2ac1cf2de074a811d63786cf374ea73f` |

URL base: `https://raw.githubusercontent.com/google/fonts/9862abc066c9b83bf84c3e8ed1fc2d8d48f9bbf0/ofl/`. O nome da Inter vai codificado na URL (`Inter%5Bopsz,wght%5D.ttf`). Na branch do spike, o arquivo `fontspike/font-sources.txt` já tem as quatro linhas no formato `<arquivo> <URL> <sha256>` que a Task 2 usa em `designsystem/font-sources.txt`.

Upstream registrado no `METADATA.pb` do `google/fonts`: Inter de `rsms/inter` no commit `66647c0bbbe41a850d79d9c76fb13add3378940f`; Barlow de `jpt/barlow` no commit `b4726ddf91525818e85e5fce111c285b9273d764`.

**Tamanho no APK:** no APK de teste do spike, as quatro fontes ocupam 645.546 bytes comprimidos (630 KiB): Inter 477.930, Barlow 54.318 + 56.579 + 56.719. Ficam dentro dos 900 KiB da decisão 15 do plano, com ~270 KiB de folga.

## Licenças

- **Inter:** "Copyright 2020 The Inter Project Authors (https://github.com/rsms/inter)", SIL Open Font License 1.1.
- **Barlow Condensed:** "Copyright 2017 The Barlow Project Authors (https://github.com/jpt/barlow)", SIL Open Font License 1.1.
- **Nenhuma das duas declara Reserved Font Name.** A linha de copyright de cada `OFL.txt` não tem o trecho "with Reserved Font Name …". As únicas ocorrências de "Reserved Font Name" são o texto padrão da licença (definição e condição 3). O corpo da licença é o mesmo nos dois arquivos, tirando `https` contra `http` no link do FAQ.
- Consequência: se um dia for preciso modificar uma fonte (por exemplo o B1), não há nome reservado a trocar. Isso não vale para esta decisão, porque as fontes entram sem modificação. O OFL pede que o texto da licença acompanhe as fontes, e isso fica para a tela de licenças da Task 2 em diante.

## Como foi medido

- **Módulo `:fontspike`**, com o mesmo formato da decisão 3 do plano (`com.android.kotlin.multiplatform.library`, namespace `com.mooscles.fontspike`, `androidResources`, `withDeviceTestBuilder`, `iosArm64`/`iosSimulatorArm64`) e as dependências `runtime`, `foundation`, `ui` e `components-resources` do CMP 1.12.2. `spikeInter(weight)` usa `Font(Res.font.inter_variable, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))`, e `spikeBarlow()` junta os três arquivos estáticos.
- **`TnumSpikeTest`**, em `fontspike/src/spikeTest/kotlin`, entra por `srcDir` no `iosTest` e no `androidDeviceTest`. Cada teste abre um `runComposeUiTest`, mede todos os textos numa composição com `rememberTextMeasurer()`, a 48 sp e `Density(2f)`, e lê `multiParagraph.intrinsics.maxIntrinsicWidth` em px. A tolerância é 0,5 px.
- No iOS, o `Font(resource, …)` do CMP 1.12.2 carrega de forma bloqueante (`rememberResourceState` só é assíncrono na web), então a primeira composição já mede com a fonte certa. O controle `barlowIsReallyLoaded` confirma que não houve fallback.
- **Android:** `ANDROID_SERIAL=emulator-5554 ./gradlew :fontspike:connectedAndroidDeviceTest` (o nome real da task), no `Mooscles_Pixel8_API35`, Android 15 (`google/sdk_gphone64_arm64/emu64a:15/AE3A.240806.043/12960925`), em `7649e0b`. Os testes e as fontes não mudaram em `df64ffd`, que só troca o formato do lock, o script e uma linha do CI.
- **iOS:** `:fontspike:iosSimulatorArm64Test` no job `ios` (`macos-26`, Xcode 26.4.1, build 17E202), depois dos testes do `:shared`, no simulador padrão do Kotlin/Native.
- **HarfBuzz:** `tools/check_fonts.py` com `fonttools==4.68.0` e `uharfbuzz==0.57.0` (as estáveis de 2026-10-28), localmente num venv com Python 3.14 e no job `host` com Python 3.13. O CI roda com `--lock fontspike/font-sources.txt --require-proportional '*.ttf' --require-tabular '*.ttf'`. As três saídas com erro foram conferidas localmente: hash divergente, fonte fora do lock e linha mal formada no lock saem com 1. Uma fonte sem `tnum` com `--require-tabular` e uma fonte monoespaçada com `--require-proportional` também saem com 1.

## Medidas

Larguras em px, a 48 sp com `Density(2f)`. O Compose arredonda a largura intrínseca para cima, então os valores são px inteiros nas duas plataformas.

### Testes com asserção

| Teste | Medida | Android (emulador) | iOS (simulador) | Resultado |
|---|---|---|---|---|
| `interWithoutTnumIsProportional` (controle) | Inter 400 `1111` / `8888` | 156 / 236 | 157 / 238 | passou nos dois |
| `barlowIsReallyLoaded` (controle) | `8888` Barlow 600 / Inter 600 / `Default` 600 | 168 / 244 / 220 | 167 / 246 / 259 | passou nos dois (Barlow < 0,85 × Inter e ≠ `Default`) |
| `interWithTnumIsTabular` | Inter `tnum` `1111` / `8888`, a 400, 600 e 700 | 248 / 248 nos três | 249 / 249 nos três | passou nos dois |
| `barlowWithTnumIsTabular` | Barlow `tnum` `1111` / `8888`: 500 · 600 · 700 | 176/176 · 184/184 · 192/192 | 176/176 · 183/183 · 192/192 | passou nos dois |
| `barlowTimerKeepsWidthWithTnum` | Barlow 600 `tnum` `0:00` / `9:59` | 162 / 162 | 161 / 161 | passou nos dois |
| `interWeightAxisApplies` (S2) | Inter `8888` a 400 / 700 | 236 / 248 | 238 / 251 | passou nos dois |

O `interWithTnumIsTabular` mede a 400, 600 e 700, não só a 400 como no texto do plano: os pesos a mais custam pouco e mostram que o `tnum` vale junto com o eixo de peso.

### Barlow sem `tnum` (`barlowDefaultFiguresAreRecorded`, sem asserção)

| Peso | Plataforma | `1111` | `8888` | `0:00` | `9:59` | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 500 | Android | 100 | 164 | 151 | 142 | 43 | 26 | 40 | 40 | 42 | 40 | 40 | 37 | 41 | 40 |
| 500 | iOS | 98 | 165 | 152 | 143 | 43 | 26 | 40 | 41 | 43 | 41 | 41 | 37 | 42 | 40 |
| 600 | Android | 101 | 168 | 153 | 147 | 43 | 26 | 41 | 41 | 44 | 41 | 41 | 38 | 42 | 41 |
| 600 | iOS | 102 | 167 | 154 | 147 | 44 | 27 | 41 | 41 | 45 | 42 | 42 | 38 | 42 | 41 |
| 700 | Android | 106 | 168 | 155 | 152 | 43 | 27 | 42 | 42 | 46 | 42 | 42 | 39 | 42 | 42 |
| 700 | iOS | 107 | 169 | 157 | 153 | 44 | 28 | 43 | 42 | 47 | 43 | 43 | 40 | 43 | 42 |

Sem `tnum`, o relógio da Barlow 600 varia 6 px entre `0:00` e `9:59`, e o `1` tem cerca de 60% da largura do `8`. Por isso o `tnum` é obrigatório nos estilos numéricos (`num.*`), como a spec já prevê.

### Diferença entre as plataformas

As larguras diferem em até 1 a 3 px entre Android e iOS, e o padrão bate com o HarfBuzz:

- **Android** arredonda o avanço de cada glifo para px inteiro antes de somar. Inter `tnum` a 400: 1328/2048 × 96 = 62,25 px → 62 × 4 = 248. Barlow 600 `tnum`: 475/1000 × 96 = 45,6 → 46 × 4 = 184.
- **iOS** (Skia) soma os avanços fracionários e o Compose arredonda o total para cima: 4 × 62,25 = 249,0 → 249; 4 × 45,6 = 182,4 → 183.

Isso não afeta a decisão: com `tnum` todos os algarismos têm o mesmo glifo de avanço, então a largura é igual nas duas plataformas para qualquer número de mesmo comprimento. O valor absoluto pode diferir em 1 a 2 px entre Android e iOS. O `FontFamily.Default` difere mais (220 contra 259), porque é a Roboto no Android e a San Francisco no iOS.

### HarfBuzz (`tools/check_fonts.py`, job `host`)

Avanços de `0`–`9` em unidades da fonte (upem 1000 na Barlow e 2048 na Inter). A saída completa está em [check_fonts-ci.txt](2026-10-28-phase2-tnum-spike/check_fonts-ci.txt) e é idêntica à local.

| Fonte | Sem features | Com `tnum` |
|---|---|---|
| Barlow Condensed 500 | 447, 266, 415, 418, 440, 420, 420, 381, 429, 414 | 457 × 10 |
| Barlow Condensed 600 | 450, 274, 425, 426, 459, 428, 429, 392, 434, 423 | 475 × 10 |
| Barlow Condensed 700 | 453, 284, 438, 436, 484, 439, 440, 407, 440, 435 | 498 × 10 |
| Inter, `wght` 400 (padrão) | 1292, 833, 1249, 1265, 1323, 1215, 1270, 1159, 1267, 1270 | 1328 × 10 |
| Inter, `wght` 500 | 1322, 850, 1262, 1284, 1344, 1235, 1290, 1170, 1289, 1290 | 1327 × 10 |
| Inter, `wght` 600 | 1351, 866, 1276, 1303, 1364, 1254, 1310, 1180, 1311, 1310 | 1325 × 10 |
| Inter, `wght` 700 | 1381, 883, 1290, 1322, 1385, 1274, 1330, 1191, 1333, 1330 | 1324 × 10 |

Na Inter, o avanço tabular muda um pouco com o peso (1328 → 1324, menos de 0,2 px por algarismo a 96 px). Um número tabular da Inter pode então mudar de largura em menos de 1 px se o peso mudar, o que não importa para o sistema.

O HarfBuzz é um indicador de host: o Minikin do Android e o SkParagraph do CMP moldam texto com ele, e os números acima batem com as medidas nos aparelhos. Ele não substitui as duas provas de plataforma.

## Decisão de fontes para a Task 2

**A, aprovada como está.** A Task 2 usa os quatro arquivos da tabela de fontes, sem modificação, do `google/fonts` em `9862abc066c9b83bf84c3e8ed1fc2d8d48f9bbf0`, com os mesmos nomes no `composeResources` e os mesmos SHA-256. Os estilos numéricos da Barlow levam `fontFeatureSettings = "tnum"`. A Inter variável fica com o eixo `wght` por peso, como na decisão 7 do plano.

## Notas para as próximas tasks

- **`runComposeUiTest` está depreciado no CMP 1.12.2,** no Android e no iOS: "Use `androidx.compose.ui.test.v2.runComposeUiTest` instead". É só um aviso, e o spike usou a API antiga. Os testes de UI do `:designsystem` (Task 3 em diante) devem usar a `v2`, que enfileira as tarefas em vez de executá-las na hora, e conferir se ela existe nos dois alvos.
- No `androidDeviceTest`, bastaram `androidx.test:runner:1.7.0`, `androidx.test.ext:junit:1.3.0`, `androidx.compose.ui:ui-test-junit4:1.12.1` e `androidx.compose.ui:ui-test-manifest:1.12.1`, mais o `org.jetbrains.compose.ui:ui-test:1.12.2` e o `kotlin("test-junit")`. Os recursos do CMP chegaram ao APK de teste da biblioteca (`assets/composeResources/com.mooscles.fontspike.resources/font/…`) sem configuração extra.
- O passo `setup-python` do CI está fixado por SHA (`actions/setup-python@6d4fd22b20465ccc0f7354476588e8df7c9ca0e0`, v7.1.0), e o `pip install` usa versões exatas. A v7.1.0 saiu em 2026-10-22, e o fonttools 4.68.0 em 2026-10-27.

## O que **não** foi provado

- **A qualidade visual** das fontes na tela: só larguras foram medidas, sem captura nem crítica de tela.
- **A desambiguação de 1/l/I e 0/O** na Inter e na Barlow.
- **Um iPhone real:** o iOS foi medido só no simulador do CI. Também não houve teste num aparelho Android físico, só no emulador.
- **O `opsz` da Inter:** o spike não mexeu no eixo de tamanho óptico, que fica no padrão do arquivo, como manda a decisão 7.
- **Larguras subpixel:** o Compose devolve a largura intrínseca arredondada para cima, então a tolerância de 0,5 px na prática exige somas iguais depois do arredondamento. Para os algarismos tabulares isso basta, porque os avanços são idênticos nas unidades da fonte.
