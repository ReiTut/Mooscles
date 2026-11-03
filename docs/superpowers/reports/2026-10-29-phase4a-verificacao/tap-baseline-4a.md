# Contagem de toques de hoje na biblioteca (antes da troca da tela)

Medida na Task 19, Step 1, do [plano da 4A](../../plans/2026-10-29-phase4a-library.md), sobre a biblioteca antiga (`LibraryScreen` e `CustomExerciseDialog` do `:app`, dentro do `MoosclesApp` com o grafo de navegação real e o catálogo inteiro, 287 exercícios), na SHA `9112a7d` (`feat/phase4a-library`, antes de qualquer mudança da task). A Task 21 copia estes números como literais no `TapBudgetTest`, com o link para este arquivo.

Prova, em testes temporários apagados no commit que tira a tela antiga (como o plano pede):

- `app/src/test/java/com/mooscles/LibraryTapBaselineHostTest.kt` (Robolectric, sdk 35, tela de 411 × 891 dp): `./gradlew :app:testDebugUnitTest --tests "com.mooscles.LibraryTapBaselineHostTest"`, 1 teste, 0 falhas (`TAP baseline findByMuscleAndOpen: 4`).
- `app/src/androidTest/java/com/mooscles/LibraryTapBaselineDeviceTest.kt` (emulador `Mooscles_Pixel8_API35`, Android 15): `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mooscles.LibraryTapBaselineDeviceTest`, 2 testes, 0 falhas.

Cada teste afirma a contagem que registra (`assertEquals`) e o fim do fluxo: a tela "Movement history" aberta, ou a chamada `saveCustomExercise:null,Machine row,Chest,Dumbbell,<convenção inicial>`.

**Por que o fluxo de criar foi para o emulador:** o diálogo antigo (um `OutlinedTextField` dentro de um `AlertDialog`) nunca fica ocioso no Robolectric ("Compose did not get idle", também com o relógio parado). Um teste-sonda isolou a causa: o `AlertDialog` sozinho, com um `Column` rolável ou com o `ChoiceFilter`, abre; com um `OutlinedTextField` dentro, não. O fluxo de achar por músculo deu 4 nos dois ambientes.

## Contador

- Um toque (`performClick`) conta 1.
- O texto digitado não conta, nem o toque que põe o foco no campo (o `performTextInput` foca o campo sozinho). Quem toca no campo do nome antes de digitar faz 1 a mais no fluxo de criar (7).
- A rolagem até o controle não conta.

## Roteiro e contagens

| Fluxo (teste) | Roteiro na tela antiga | Hoje (medido) | Spec, "Hoje" | Spec, "Novo" |
| --- | --- | --- | --- | --- |
| Achar um exercício por músculo e abrir (`findByMuscleAndOpen`) | da aba Treinar: aba Biblioteca, menu "Muscle", "Back", o primeiro exercício de costas (abre "Movement history") | **4** | 4 (aba, abrir o filtro, escolher, tocar) | 3 (aba, chip do músculo, tocar) |
| Criar exercício personalizado com nome, músculo e equipamento (`createCustomExerciseWithNameMuscleAndEquipment`) | na biblioteca: "Create custom exercise" (+), o nome digitado, menu "Muscle" (começa em "Back"), "Chest", menu "Equipment" (começa em "Barbell"), "Dumbbell", a convenção como começa, "Save exercise" | **6** + digitar | cerca de 8 + digitar (3 menus suspensos) | 4 + digitar |

## Notas

- **Ponto de partida:** o fluxo de achar começa em outra aba e conta o toque na aba Biblioteca, como a spec ("aba, …"); o de criar começa com a biblioteca aberta, como o "Novo" da spec ("criar, chip de músculo, chip de equipamento, salvar").
- **Criar:** os menus do diálogo antigo começam no primeiro valor em ordem alfabética ("Back" e "Barbell"). Um exercício de costas com barra custaria 2 toques (criar e salvar); o roteiro escolhe um músculo e um equipamento que não são os iniciais, como no "Novo". A spec estimou 8 contando o menu da convenção; com a convenção inicial (o "Novo" também usa o padrão), são 6.
- O orçamento novo fica abaixo dos dois números de hoje: 3 contra 4, e 4 contra 6.
