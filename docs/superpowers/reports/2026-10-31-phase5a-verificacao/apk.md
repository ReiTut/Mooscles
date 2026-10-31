# Tamanho do APK e dependências da fase 5A

Task 16, Step 5. Medido em 2026-10-31 no Mac, na `feat/phase5a-progress` em `3b07456` (o código do app é o de `a0eab5f`).

## APK debug

Comandos: `./gradlew :app:clean :app:assembleDebug`, depois `pwsh tools/check-apk-budget.ps1 -Apk app/build/outputs/apk/debug/app-debug.apk`.

| Checagem | Bytes | Limite | Resultado |
| --- | ---: | ---: | --- |
| APK | 24.754.047 | n/a | n/a |
| Crescimento sobre a base (`e6ada6a`, 24.469.208, artefato do CI; `tools/apk-budget.json`), 5A e 5B juntas | **284.839** | 524.288 | ok (sobram 239.449 para a 5B) |
| Fontes (comprimidas) | 645.546 | 921.600 | ok |
| Fontes e ícones (classes de ícone como teto; 65 arquivos) | 938.769 | 1.048.576 | ok |

**A meta da 5A (262.144) foi passada** em 22.695 bytes (8,7%). O limite que o script confere é o de 524.288, das duas etapas juntas, e esse passou. Já a meta só da 5A, que a spec põe em "até 256 KiB" (D18), não passou. A Task 13 já tinha apontado esse número (+284.839 no Push 2), e a seção de trace desta task não o mudou de forma visível. Fica para o controlador decidir se a meta muda ou se algo sai na 5B.

O APK da `main` em `596c5bd`, montado aqui no worktree, tem exatamente os 24.469.208 bytes da base do CI, então as duas comparações abaixo usam a mesma base.

### O que cada parte somou

Entradas do APK, comprimidas (o que o orçamento mede):

| Entrada | `main` | Branch | Diferença |
| --- | ---: | ---: | ---: |
| `classes*.dex` | 23.004.168 | 23.287.409 | **+283.241** |
| `assets/composeResources/com.mooscles.screens.resources` (strings) | 62.983 | 67.512 | +4.529 |
| `assets/composeResources/com.mooscles.designsystem.resources` | 670.908 | 671.560 | +652 |

Dex por pacote (bytes definidos, sem compressão, pelo `apkanalyzer dex packages --defined-only`; o total do dex cresce 285.010):

| Pacote | Diferença | O que é |
| --- | ---: | --- |
| `com.mooscles.screens.progress` | +136.851 | índice, consultas, gráficos, a tela de progresso e as seções (novo) |
| `com.mooscles.designsystem.charts` | +51.980 | `LineChart`, `MSparkline`, a tabela e as marcas (novo) |
| `com.mooscles.screens.records` | +44.288 | a aba Recordes, a linha, a folha de regras e as consultas (novo) |
| `com.mooscles.screens.resources` | +43.637 | os acessores gerados de `strings_progress.xml` e `strings_records.xml` |
| `com.mooscles.designsystem.gallery` | +13.100 | a página "Gráficos" da galeria |
| `com.mooscles.screens.format` | +6.731 | `WeekStart` (a tabela do CLDR) e as datas |
| `com.mooscles.designsystem.resources` | +5.070 | as strings do design system |
| `com.mooscles.designsystem.icons` | +3.410 | os ícones Lucide novos |
| `com.mooscles.designsystem.tokens` | +2.034 | os tokens dos gráficos |
| `com.mooscles.screens.exercise` | +1.038 | a sparkline no card do histórico |
| `com.mooscles` (só o pacote raiz, no `:app`) | +2.191 | hospedagem das rotas e o `ProgressIndexTrace` |
| `com.mooscles.ui` (no `:app`) | **−29.561** | as telas antigas de Recordes e do histórico do exercício, que saíram na Task 13 |
| Outros de `com.mooscles` (`components`, `domain`, `theme` e o resto) | +937 | o `EstimatedOneRepMax` (+321) e ajustes pequenos |
| Fora de `com.mooscles` | +3.304 | código de biblioteca que passou a ser usado (sem dependência nova; ver abaixo) |

`app-performance.apk` (R8, `assemblePerformance`): 3.446.765 bytes. A 4B tinha 3.325.957, então a 5A acrescentou +120.808.

**Ressalva:** o APK desta medida foi montado aqui, com a chave debug local, e o tamanho pode diferir um pouco do artefato do CI, que é a base oficial. O número que vale para o orçamento é o do CI. A base, porém, é a mesma: o APK da `main` montado aqui tem o mesmo tamanho do artefato.

## Dependências

`./gradlew :app:dependencies --configuration debugRuntimeClasspath` na branch e na `main` (`596c5bd`, num worktree descartável, já apagado). As duas saídas têm 1.216 linhas (`local/deps-*.txt`). O `diff` é o [deps-diff.txt](deps-diff.txt), **vazio** (0 bytes): a 5A não traz nenhuma biblioteca nova.
