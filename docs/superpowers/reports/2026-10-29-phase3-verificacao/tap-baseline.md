# Contagem de toques de hoje (antes da troca da tela do treino)

Medida na Task 24, Step 1, sobre a tela antiga do treino (`ActiveWorkoutScreen` do `:app`, com o dock antigo), na SHA `bb3d633` (`lane/phase3-t24`, antes de qualquer mudança da task). A Task 29 copia estes números como literais no `TapBudgetTest`, com o link para este arquivo.

Prova: `app/src/test/java/com/mooscles/TapBudgetBaselineHostTest.kt` (Robolectric, sdk 35, tela de 411 × 891 dp), `./gradlew :app:testDebugUnitTest --tests "com.mooscles.TapBudgetBaselineHostTest"`: 9 testes, 0 falhas. Cada teste afirma a contagem que registra e confere a chamada que o fluxo fez (por exemplo `updateSet:set-0,8,82.5,true,false,false`). O teste é apagado no Step 4, junto com a tela antiga, como o plano pede.

## Contador

- Um toque (`performClick`) ou um gesto conta 1.
- Cada caractere digitado conta 1, e cada caractere apagado (backspace) também.
- Mudar um valor é apagar o valor antigo inteiro e digitar o novo (o roteiro "campo, apagar, digitar, check" da spec).
- A rolagem até o controle não conta (o "mais a rolagem" da spec), nem abrir o treino.

## Roteiro e contagens

| Fluxo (teste) | Roteiro na tela antiga | Hoje (medido) | Spec, "Hoje" |
| --- | --- | --- | --- |
| Concluir série pré-preenchida pela rotina (`prefilledSet`) | série 80 kg × 8: check | **1** | 1 |
| Concluir série igual à da semana passada, sem alvo (`sameAsLastWeekWithoutTarget`) | série vazia, "Previous: 80 kg × 8": campo de carga, "8", "0", campo de repetições, "8", check | **6** | 2 toques + digitação (cerca de 6) |
| Subir a carga um passo e concluir (`oneLoadStepUpAndComplete`) | 80 → 82,5 kg: campo de carga, 2 backspaces, "8", "2", ".", "5", check | **8** | cerca de 7 |
| Uma repetição a menos e concluir (`oneRepLessAndComplete`) | 8 → 7: campo de repetições, 1 backspace, "7", check | **4** | cerca de 4 |
| Adicionar série igual à de cima e concluir (`addSetLikeAboveAndComplete`) | "Add set" (a série nova herda a carga, sem repetições, como o `addSet` do repositório), campo de repetições, "8", check | **4** | cerca de 4 |
| Marcar aquecimento (`markWarmup`) | "Working set", "Warm-up · excluded from PRs" | **2** | 2 |
| Remover série (`removeSet`) | "Remove set 1" (o X da linha) | **1** | 1 |
| Pular descanso / +15 s (`skipRestAndPlusFifteen`) | dock: "Skip rest timer"; dock: "+0:15" | **1** / **1** | 1 |
| Finalizar (`finish`) | (rolagem até "Finish workout", não contada), "Finish workout", "Finish & save" | **2** | 2 (mais a rolagem) |

As contagens exatas da spec (1, 2, 1, 1, 2) se confirmam. As aproximadas ficaram em 6, 8, 4 e 4.

## Notas

- **Subir a carga:** com o roteiro "apagar o valor inteiro e digitar o novo", são 8. Quem só apaga o "0" e digita "2.5" faz 6. O orçamento novo (3) fica abaixo dos dois.
- O teclado na tela antiga abre no primeiro toque do campo; o teste não conta o fechamento do teclado (a tela antiga conclui a série com o teclado aberto).
