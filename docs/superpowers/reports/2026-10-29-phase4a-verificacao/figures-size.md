# Tamanho das poses do piloto (Task 8)

Aviso de tamanho da spec (D3, "uma única tabela Kotlin assim pode encostar no limite de 64 KB de bytecode por método") e Step 4 da Task 8 do [plano da 4A](../../plans/2026-10-29-phase4a-library.md). Medido em 2026-10-30 na `lane/phase4a-t8` (base `8ab64a4`), no Mac.

## Forma da tabela

Uma tabela por família (`BarbellFigures`, `DumbbellFigures`, `CableFigures`, `BodyweightFigures`, `MachineFigures`), escrita com a DSL `figure("…") { start { … }; end { … } }` de `FigureDsl.kt`. As funções da DSL que recebem blocos são `inline`, então cada tabela vira chamadas diretas no inicializador estático do `object` da família, sem uma classe por lambda (conferido: nenhuma classe `*Figures$…` no `bundleLibRuntimeToDirAndroidMain`).

## Bytecode (`:screens:compileAndroidMain`, `javap -c -p`)

| Família | Exercícios | Maior método | Tamanho do método (último offset de bytecode) | `.class` |
| --- | ---: | --- | ---: | ---: |
| Barbell | 7 | `static {}` | 2.230 bytes | 11.222 bytes |
| Dumbbell | 5 | `static {}` | 1.717 bytes | 9.469 bytes |
| Cable | 3 | `static {}` | 1.003 bytes | 7.279 bytes |
| Bodyweight | 2 | `static {}` | 552 bytes | 5.831 bytes |
| Machine | 3 | `static {}` | 820 bytes | 6.869 bytes |

Cerca de 320 bytes de bytecode por exercício (dois quadros). Uma família com o teto do teste (`noFamilyTableIsTooLarge`, 60 exercícios) ficaria perto de 19 KB, abaixo dos 32 KB da regra e dos 64 KB da JVM.

## APK debug

- `clean :app:assembleDebug` com as poses: **23.918.111 bytes**, crescimento de **265.768** sobre a base do CI (limite 629.146; `tools/check-apk-budget.ps1` ok nas três linhas). Indicativo: assinatura local (ver [apk-base.md](apk-base.md)).
- Com e sem as poses: as cinco tabelas trocadas por `emptyMap()` (o resto igual: DSL, `ExerciseFigures`, a página da galeria), `:app:assembleDebug` incremental. Só o `classes5.dex` muda:

| Build | `classes5.dex` sem compressão | comprimido no APK |
| --- | ---: | ---: |
| Sem as poses | 56.152 bytes | 25.841 bytes |
| Com as 20 poses | 74.816 bytes | 32.330 bytes |
| Diferença | 18.664 | **6.489** |

**Cerca de 325 bytes comprimidos por exercício, ou 162 por pose**, contra o limite de 1,5 KB por pose da regra. O tamanho total do APK não serve para essa comparação: dois builds com o mesmo `classes5.dex` (mesmo CRC) saíram com 23.918.111 e 23.950.483 bytes, só pela ordem das entradas no zip; por isso a medida é a da entrada do dex.

**Regra do plano:** nenhum método passa de 32 KB e nenhuma pose de 1,5 KB, então a DSL fica; o `FloatArray` por família não foi necessário. Os 287 exercícios, no mesmo ritmo, somariam perto de 93 KB comprimidos no APK.
