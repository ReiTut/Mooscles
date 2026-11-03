# Base do orçamento de APK da fase 4A

Decisão 18 do [plano da 4A](../../plans/2026-10-29-phase4a-library.md) e achado F22 do pré-voo.

| Campo | Valor |
| --- | --- |
| Commit da base | `739a80039b61cb7edd2c09eeca6320518a44b4c1` (`main`, onde `feat/phase4a-library` nasce) |
| Run do CI | [`40983711502`](https://github.com/pedroyuuki/Mooscles/actions/runs/40983711502), `ci.yml`, push na `main`, `conclusion: success` (conferido com `gh run view` em 2026-10-29) |
| Artefato | `app-debug-apk` (`app-debug.apk`), baixado com `gh run download 40983711502 -R pedroyuuki/Mooscles -n app-debug-apk` |
| Tamanho | **23.652.343 bytes** |
| SHA-256 do APK baixado | `2663d31e3c3c9bdc744c4e1dc4bf68f072e7661bd76ebcf71fd0af6f3bcdc7aa` |
| Crescimento permitido na 4A | `maxGrowthBytes: 629146` (0,6 MiB, a parte da 4A no D20); a 4B sobe para 1.048.576 sobre a mesma base |

O run de `739a800` estava verde, então a alternativa do F22 (`4f41792`, run `40966539081`) não foi usada.

**O que se compara é o tamanho, não a assinatura.** A base foi assinada no runner do CI; um APK montado no Mac é assinado por outra chave debug. A diferença de tamanho entre as duas assinaturas é pequena, mas existe: o número que vale para o orçamento é o do APK do CI. Checagens locais (Tasks 6, 19 e 22) são indicativas.

## Medições locais

| Task | Commit medido | APK debug local (`:app:clean :app:assembleDebug`, Mac) | Crescimento sobre a base do CI | Parte da task |
| --- | --- | ---: | ---: | ---: |
| 6 (mapa corporal e licenças) | `lane/phase4a-t6` sobre `d4c77c7` | 23.821.426 bytes | 169.083 (limite 629.146, `check-apk-budget.ps1` ok) | +86.995 bytes sobre `d4c77c7` medido do mesmo jeito (23.734.431) |
| 22 (verificação final) | `feat/phase4a-library` em `f4032f9` | 24.102.511 bytes | 450.168 (limite 629.146, `check-apk-budget.ps1` ok) | a 4A inteira; ver [apk.md](apk.md) |

Indicativo: assinatura local, não a do CI (ver acima).
