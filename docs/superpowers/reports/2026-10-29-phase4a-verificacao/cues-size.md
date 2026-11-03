# Tamanho dos arquivos de dicas da fase 4A

Task 13, Step 3 do [plano da 4A](../../plans/2026-10-29-phase4a-library.md). Comparação com a estimativa da D8 da [spec](../../specs/2026-10-29-phase4-library-routines-design.md): "cerca de 2 × 90 KB de JSON, por volta de 50 KB comprimidos no APK".

Medido em 2026-10-30, com os 287 exercícios do catálogo cobertos (4 dicas cada, em en e pt-BR), nos arquivos de `screens/src/commonMain/composeResources/files/exercise_cues/`:

| Arquivo | Bytes | `gzip -9 \| wc -c` |
| --- | ---: | ---: |
| `en.json` | 84.901 | 16.124 |
| `pt-BR.json` | 87.551 | 16.166 |
| **Total** | **172.452** | **32.290** |

Comandos: `wc -c < arquivo` e `gzip -9 -c arquivo | wc -c`.

## Leitura

- O JSON ficou perto da estimativa (172 KB contra cerca de 180 KB) e o comprimido ficou abaixo dela (32 KB contra cerca de 50 KB). As dicas repetem muitas expressões entre exercícios da mesma família, o que comprime bem.
- O `gzip -9` é uma aproximação do que o APK guarda: o empacotamento usa deflate, com nível próprio, e o tamanho real no APK só aparece na medição do `check-apk-budget.ps1` (Tasks 19 e 22). A diferença esperada é de poucos KB.
- Os dois arquivos entram inteiros no APK; a leitura é feita uma vez por idioma e processo (`ExerciseCues`, Task 11).
