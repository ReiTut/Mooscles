# Tamanho do APK e dependências da fase 4A

Task 22, Step 5. Medido em 2026-10-30 no Mac, na `feat/phase4a-library` em `f4032f9`.

## APK debug

`./gradlew :app:clean :app:assembleDebug`, depois `pwsh tools/check-apk-budget.ps1 -Apk app/build/outputs/apk/debug/app-debug.apk`:

| Checagem | Bytes | Limite | Resultado |
| --- | ---: | ---: | --- |
| APK | 24.102.511 | — | — |
| Crescimento sobre a base (`739a800`, 23.652.343, ver [apk-base.md](apk-base.md)) | **450.168** | 629.146 | ok |
| Fontes (comprimidas) | 645.546 | 921.600 | ok |
| Fontes e ícones (classes de ícone como teto) | 909.113 | 1.048.576 | ok |

Sobram 178.978 bytes da parte da 4A. Indicativo: o APK foi assinado pela chave debug deste Mac, não pela do CI. O APK da `main` em `739a800` montado aqui, no mesmo dia, saiu com 23.652.343 bytes, exatamente o tamanho do artefato do CI que serve de base, então a assinatura local não mudou o tamanho neste caso.

### Figuras, mapa e dicas, separados

| Parte | Como medi | Bytes |
| --- | --- | ---: |
| Dicas (`exercise_cues/en.json` e `pt-BR.json`) | entradas do APK, comprimidas (`unzip -v`) | **45.541** (22.620 + 22.921; 172.481 sem compressão) |
| Mapa corporal (as 9 classes `BodyMap*` do `:designsystem`: caminhos, geometria, paleta e a view) | `d8 --release --min-api 26` só dessas classes, depois `gzip -9` do `.dex` | **≈ 69.000** (dex de 166.816) |
| Figuras (as classes `*Figure*` do `:designsystem` mais o pacote `screens/exercise/figures`, com as 20 poses, a DSL e a página da galeria) | o mesmo | **≈ 57.800** (dex de 145.952) |

As figuras e o mapa ficam no `classes*.dex`, junto com o resto do código, e o APK debug não é minificado, então o tamanho exato dentro dele não se separa. O `d8` isolado dá uma estimativa: o dex sem as referências ao resto do app, comprimido com `gzip -9` (o APK usa deflate com nível próprio). Juntas, as três partes somam perto de 172 KB dos 450 KB de crescimento. O resto é a tela da biblioteca, o detalhe, o formulário, o seletor novo, os textos (`strings_library.xml`, `strings_catalog.xml` com os 287 nomes em pt-BR) e os ícones novos.

## Dependências

`./gradlew :app:dependencies --configuration debugRuntimeClasspath` na branch e na `main` em `739a800` (num worktree descartável). As duas saídas têm 1.216 linhas e o mesmo SHA-256 (`46377b46…50d9`); o `diff` está em [deps-diff.txt](deps-diff.txt), **vazio**. As saídas inteiras ficaram em `local/deps-main.txt` e `local/deps-branch.txt`.
