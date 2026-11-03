# Tamanho do APK e dependências da fase 4B

Task 18, Step 5. Medido em 2026-10-30 no Mac, na `feat/phase4b-routines` (código de `4694edc`).

## APK debug

`./gradlew :app:clean :app:assembleDebug`, depois `pwsh tools/check-apk-budget.ps1 -Apk app/build/outputs/apk/debug/app-debug.apk`:

| Checagem | Bytes | Limite | Resultado |
| --- | ---: | ---: | --- |
| APK | 24.387.172 | n/a | n/a |
| Crescimento sobre a base (`739a800`, 23.652.343, do CI; ver o [apk-base.md da 4A](../2026-10-29-phase4a-verificacao/apk-base.md)), **4A e 4B juntas** | **734.829** | 1.048.576 | ok (sobram 313.747) |
| Fontes (comprimidas) | 645.546 | 921.600 | ok |
| Fontes e ícones (classes de ícone como teto) | 913.826 | 1.048.576 | ok |

**4A e 4B separadas.** A 4A medida no mesmo Mac fechou em 24.102.511 (450.168 sobre a base, limite da parte dela 629.146). Logo a **4B acrescenta 284.661 bytes** sobre a 4A (24.387.172 − 24.102.511). Como o orçamento total da 4B é 1.048.576 sobre a mesma base, e a 4A já usou 450.168, sobram 598.408 para a 4B e ela gastou 284.661.

Ressalva: os dois APKs de comparação foram montados aqui (chave debug local, que dá um tamanho levemente diferente do artefato do CI, que é a base oficial). O número que vale para o orçamento é o do CI; este é indicativo. Os 4A de 450.168 e os desta rodada têm a mesma máquina e o mesmo JDK.

`app-performance.apk` (R8, `assemblePerformance`): 3.325.957 bytes (a 4A tinha 3.238.207, +87.750).

## Dependências

`./gradlew :app:dependencies --configuration debugRuntimeClasspath` na branch e na `main` atual (`1580143`, num worktree descartável, já apagado). As duas saídas têm 1.216 linhas e o `diff` é [deps-diff.txt](deps-diff.txt): **vazio** (0 bytes). A 4B não traz biblioteca nova.
