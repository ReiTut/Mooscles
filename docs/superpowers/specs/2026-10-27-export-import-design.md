# Export e import dos dados — design

- **Data:** 2026-10-27
- **Fase do roadmap:** 0 ([ROADMAP](../ROADMAP.md))
- **Branch:** `feat/export-import`
- **Status:** rascunho para aprovação

## Por que

Hoje os treinos só existem dentro do celular. O backup do Android está desligado (`allowBackup="false"` em `AndroidManifest.xml`) e o app não tem nenhuma forma de tirar os dados de lá. Perder, trocar ou resetar o aparelho apaga todo o histórico. Além de proteger contra isso, um arquivo de backup é a ponte prevista no roadmap para levar os dados para outro Android e, na fase 7, para o iPhone. Também atende ao direito de portabilidade da LGPD (Art. 18, V), sem servidor nenhum.

## Objetivos

1. O usuário exporta **todos** os seus dados num único arquivo, salvo onde ele escolher pelo seletor de arquivos do sistema (Downloads, Google Drive ou qualquer provedor instalado).
2. O usuário importa esse arquivo em qualquer instalação do Mooscles, nova ou com dados, sem perder nada do que já existe no aparelho.
3. O formato do arquivo é estável e tem versão própria, independente da versão do banco Room. Versões futuras do app, inclusive a de iOS, leem arquivos antigos.

## Fora de escopo

- Backup automático ou agendado, e integração direta com o Google Drive. Isso fica para a fase 8.
- Ligar o Auto Backup do Android (`allowBackup`). O dono desligou de propósito, e a decisão volta na fase 8.
- Opção "substituir tudo" ou apagar dados durante o import.
- Exportar só parte dos dados, como uma rotina avulsa para compartilhar.
- Exportar pelo menu de compartilhamento do sistema (escolha minha). Isso exigiria um arquivo temporário sem criptografia no cache, passado por `FileProvider`. O seletor de arquivos já cobre salvar localmente e no Google Drive. O ROADMAP foi ajustado.
- Criptografia do arquivo.
- Exportar o estado do timer de descanso, das atualizações ou caches.

## Anti-objetivos

O trabalho falhou, mesmo que funcione, se:

- um import apagar, sobrescrever ou corromper qualquer dado que já estava no aparelho;
- um arquivo inválido gravar **qualquer** coisa: a regra é tudo ou nada;
- o export ou o import travar a UI;
- importar o mesmo arquivo duas vezes duplicar registros.

## Como o usuário usa

Em Configurações, uma seção nova **"Your data"** entra logo abaixo das seções existentes. A UI continua em inglês até a fase 2. A seção tem dois botões e uma linha de explicação.

### Export

1. Ao tocar em **Export data**, o seletor de arquivos abre para salvar um novo documento (`ACTION_CREATE_DOCUMENT`, tipo `application/json`).
2. O nome sugerido é `mooscles-backup-AAAA-MM-DD.json`, com a data local.
3. Depois de salvar, aparece uma confirmação curta com a contagem: "Exported 208 workouts, 12 routines and 15 custom exercises".
4. Se o usuário cancelar o seletor, nada acontece.
5. Uma linha fixa avisa que o arquivo contém o histórico de treinos sem criptografia e que o usuário decide onde guardá-lo.

### Import

1. Ao tocar em **Import data**, o seletor abre para escolher um arquivo (`ACTION_OPEN_DOCUMENT`). Aceita `application/json` e também `*/*`, porque alguns provedores não informam o tipo.
2. O app lê e valida o arquivo **inteiro** antes de gravar qualquer coisa.
3. Se o arquivo for válido, aparece um resumo antes de confirmar: "Add 180 workouts, 4 routines and 3 custom exercises. 28 workouts are already on this phone and will be kept as they are." A partir daí, o usuário confirma ou cancela.
4. Depois de confirmar, tudo é gravado numa única transação, e o resultado aparece com as contagens.
5. Se o arquivo for inválido, aparece uma mensagem clara com o motivo e nada é gravado. Os motivos possíveis:
   - não é um backup do Mooscles;
   - foi criado por uma versão mais nova do app;
   - está corrompido ou incompleto.
6. Durante um treino em andamento, **Import data** fica desabilitado, com a explicação "Finish or discard the active workout to import". **Export data** continua sempre disponível.

## Regra do import: só acrescenta

Cada registro tem um ID estável (UUID em texto, ou a chave composta no caso dos checkpoints). A comparação é feita **por agregado**, não linha por linha, para nunca alterar um treino ou uma rotina que já existe no aparelho:

- **Treino:** o `Workout` com seus `WorkoutExercise` e `WorkoutSet`. Se o ID do treino já existe no aparelho, o treino inteiro do arquivo é ignorado, com todos os filhos, mesmo que o arquivo tenha séries que o aparelho não tem.
- **Rotina:** a `Routine` com seus `RoutineExercise` e `RoutineSet`, com a mesma regra.
- **Exercício, pasta e checkpoint** são comparados um a um.

| Situação | Comportamento |
| --- | --- |
| O ID não existe no aparelho | O registro, ou o agregado inteiro, é inserido como está no arquivo |
| O ID já existe no aparelho | O registro ou agregado do aparelho é mantido e o do arquivo é ignorado por completo, mesmo que o conteúdo seja diferente |
| Treino ou rotina apagado no aparelho depois do export | Volta com o import. É a consequência aceita de "só acrescenta": o import é uma ação explícita, e o resumo antes de confirmar mostra o que vai entrar. Escolha minha. |
| Importar o mesmo arquivo de novo | Nada muda, e o resumo informa que tudo já existe |
| Rotina importada cuja pasta não existe no arquivo nem no aparelho | A rotina vai para a pasta padrão (`RoutineFolder.DEFAULT_ID`) |
| Exercício de catálogo (`custom = false`) que já existe | Mantém a versão local, que é a do catálogo atual |

Escolhas minhas:

- **Preferências** (unidade, tema, descanso automático, descanso padrão, pasta selecionada) só são aplicadas se o aparelho **não tiver nenhum treino nem nenhuma rotina do usuário** antes do import, ou seja, numa instalação nova. Caso contrário, as preferências locais ficam.
- **Treino em andamento no arquivo**: como o import exige que não haja treino ativo no aparelho, um treino com status `ACTIVE` no arquivo é importado como ativo e vira a sessão em andamento. O timer não é restaurado.
- **Ordem das rotinas**: as rotinas importadas para uma pasta que já tem rotinas entram depois das existentes, mantendo entre si a ordem do arquivo.
- **Alvos herdados de PR são uma exceção aceita.** As séries de rotina com `inheritPr = true`, as colunas espelho de `RoutineExercise` e os `routine_target_checkpoints` são valores **derivados do histórico**: o app já os recalcula em `synchronizeRoutineTargets()` a cada abertura e a cada treino finalizado. Se o histórico importado tiver um PR maior, esses alvos mudam, que é exatamente o que o usuário pediu ao marcar a série como herdada. Para o resultado aparecer na hora, o import chama a mesma sincronização no fim da sua transação. Séries com alvo fixo (`inheritPr = false`) nunca mudam. Os checkpoints continuam no arquivo, para a ida e volta ser fiel.

## Formato do arquivo (versão 1)

JSON UTF-8, com um objeto raiz:

```json
{
  "format": "mooscles-backup",
  "formatVersion": 1,
  "exportedAt": "2026-10-27T14:03:00Z",
  "app": { "versionName": "1.5.0", "versionCode": 17, "platform": "android" },
  "preferences": { "pounds": false, "theme": "system", "autoRest": true, "defaultRestSeconds": 90, "selectedRoutineFolderId": "default" },
  "data": {
    "exercises": [], "routineFolders": [], "routines": [], "routineExercises": [], "routineSets": [],
    "workouts": [], "workoutExercises": [], "workoutSets": [], "routineTargetCheckpoints": []
  }
}
```

Regras do formato:

- Cada lista em `data` traz todos os campos da entidade correspondente em `data/Models.kt` e `data/RoutineTargetCheckpoint.kt`, com os mesmos nomes de campo.
- Enums (`MuscleGroup`, `Equipment`, `LoadConvention`, `WorkoutStatus`) são gravados pelo nome da constante.
- Pesos são sempre em kg (`Double`) e horários em milissegundos epoch (`Long`), como no banco.
- O formato é definido por **classes próprias de transferência**, separadas das entidades Room. Uma mudança futura no banco não pode mudar o arquivo sem querer: mudar o formato exige subir `formatVersion` e escrever a conversão da versão anterior.
- Ao ler, campos desconhecidos são ignorados, para manter compatibilidade com arquivos de versões futuras que só acrescentem campos.
- Um `formatVersion` maior que o suportado é recusado com a mensagem de "versão mais nova".
- O export inclui todo o catálogo de exercícios usado, e não só os personalizados. Assim, um arquivo vindo de um app com catálogo diferente continua consistente.

## Validação (antes de gravar)

O arquivo é recusado inteiro se:

- não for JSON válido, se `format` não for `mooscles-backup` ou se faltar alguma lista obrigatória;
- houver IDs duplicados dentro de uma mesma lista;
- alguma referência não fechar considerando o arquivo **mais** o aparelho, com uma exceção: rotina sem pasta vai para a pasta padrão, como diz a tabela acima. As referências conferidas são:
  - `routineExercises.routineId` → rotina;
  - `routineExercises.exerciseId` → exercício;
  - `routineSets.routineExerciseId` → item de rotina;
  - `workoutExercises.workoutId` → treino;
  - `workoutSets.workoutExerciseId` → exercício do treino;
- houver mais de um treino `ACTIVE` no arquivo;
- algum valor violar as regras **estruturais**: IDs e nomes em branco, números negativos ou não finitos, enum desconhecido.

As regras de edição atuais (limites de repetições e carga, descanso até `RestDuration.MAX_SECONDS`) valem só para **rotinas, pastas, exercícios personalizados e o treino ativo**, que são os dados que o usuário ainda edita. O histórico de treinos finalizados (`FINISHED`) segue só as regras estruturais. Versões antigas aceitavam valores que hoje o editor recusa: o descanso, por exemplo, só ganhou limite de 300 s na v1.1.0, e `RestDuration.kt` documenta que o histórico pode ter descansos maiores. Esse histórico é importado **sem ajuste**, porque o histórico nunca é alterado. Escolha minha.

Os registros e agregados do arquivo cujo ID já existe no aparelho são ignorados e **não são validados**: um registro que vai ser descartado não pode derrubar o arquivo.

## Restrições técnicas

- **Threads:** ler, validar, serializar e gravar acontece fora da thread principal. A gravação usa uma única transação do Room.
- **Concorrência:** o import segura o `trainingGate` (`MoosclesApplication`) enquanto grava, para o atualizador não instalar no meio. Ele também passa pelo canal de comandos do `AppViewModel`, ou garante a mesma serialização.
- **Desempenho:** com a base de teste grande (cerca de 208 treinos e 12,7 mil séries), o export e o import terminam em poucos segundos no emulador de referência e a UI não congela. O número exato é medido e registrado no plano.
- **Biblioteca de JSON:** `kotlinx.serialization` (escolha minha). É Kotlin puro e funciona em Kotlin Multiplatform, então o mesmo código serve ao iOS na fase 1. A alternativa, o `org.json` que o updater já usa, só existe no Android. A versão do plugin acompanha o Kotlin do projeto.
- **Separação para a fase 1:** o formato, a validação e o merge ficam em código Kotlin puro, sem `android.*`. Só a parte de abrir o seletor e ler ou gravar o `Uri` depende do Android.
- **Sem mudança de schema:** não há nova versão do Room. O `compat-reviewer` tem que dar `SEGURO`.

## Como verificar

- **Ida e volta:** exportar → importar num banco vazio → os dados (`AppData` + checkpoints + preferências) são idênticos ao original.
- **Idempotência:** importar o mesmo arquivo duas vezes não muda nada na segunda vez.
- **Merge:** com dados locais diferentes no aparelho e no arquivo, depois do import todo registro local continua igual e todo agregado novo do arquivo está presente. A exceção são os alvos herdados de PR, que seguem a sincronização normal. Isso inclui os casos:
  - treino que já existe com séries diferentes no arquivo: nenhuma série é acrescentada;
  - rotina que já existe com itens a mais no arquivo: nenhum item é acrescentado.
- **Histórico antigo:** um arquivo com treino finalizado cujo descanso passa de 300 s é importado sem mudar o valor. Uma rotina com descanso acima do limite é recusada.
- **Arquivos inválidos:** JSON quebrado, `format` errado, versão futura, ID duplicado, referência faltando e dois treinos ativos são todos recusados, e o banco fica byte a byte igual ao de antes.
- **Compatibilidade:** um arquivo da versão 1 gravado como fixture de teste continua sendo lido pelas versões seguintes. Esse teste nunca é apagado.
- **Desempenho:** export e import da base grande, medidos e registrados.
- **No emulador:** fazer o fluxo completo pelo seletor de arquivos (Maestro ou mobile-mcp), inclusive exportar → limpar os dados do app → importar → conferir o histórico.

## O que fica para quem implementar

- Onde ficam as classes. A sugestão é um pacote `com.mooscles.backup` com o codec e o merge, e a integração em `WorkoutRepository` e `AppViewModel`.
- O texto exato das mensagens, seguindo o tom das mensagens existentes.
- Como mostrar o resumo antes de confirmar: diálogo ou bottom sheet, usando os componentes atuais de `ui/Common.kt`.
- O layout da seção em Configurações, no padrão dos cards atuais. O redesign vem na fase 2.

## Notas de implementação

- **2026-10-27, desempenho:** com a base grande, a gravação do import termina em menos de cerca de 8 s no emulador, mas a UI fica parada depois disso. Foram 53 a 77 s duas vezes no APK debug e 25 s no build com R8. A causa é anterior a esta feature: `WorkoutPresentation.present` recalcula os PRs de todo o histórico na thread principal (`prsForWorkout`/`repsPrsForWorkout`, custo quadrático). A mesma base já trava a abertura do app sem nenhum import (52 s no debug). O import só dispara esse recálculo mais uma vez. Decidi não corrigir aqui: a correção é o item "Cálculo de PR fora da thread principal" da fase 0 do ROADMAP, que passa a ser o próximo trabalho e bloqueia a release da v1.5. A restrição "a UI não congela" continua valendo e só fica cumprida com aquele item. Números e método em [reports/2026-10-27-export-import-verificacao.md](../reports/2026-10-27-export-import-verificacao.md).
- **2026-10-27, resumo sem nada novo:** quando o arquivo não acrescenta treinos, rotinas, exercícios personalizados nem preferências, o diálogo mostra "Everything in this backup is already on this phone." com um único botão OK, e nenhum import roda. Antes ele oferecia um Import sem efeito, que terminava em "Imported 0 …". Isso detalha a linha "Importar o mesmo arquivo de novo" da tabela acima. O caso com treinos já existentes e algo novo continua com o texto do passo 3.
- **2026-10-27, feedback no card:** o diálogo fecha ao confirmar e o progresso aparece no card com o nome da operação (Exporting…, Checking backup…, Importing…). Erro e sucesso têm cor e ícone diferentes. A crítica de tela que motivou essas mudanças está no mesmo relatório.
- **2026-10-27, revisão final da branch:** (1) "nada novo" passa a ser decidido pelo planejador (`ImportSummary.nothingToAdd`): só vale quando o plano não insere nenhuma linha de nenhum tipo, inclusive pasta, exercício do catálogo ou checkpoint, e não aplica preferências. Antes, um arquivo que só trazia uma pasta nova caía no "Everything … already on this phone" e a pasta nunca era importada. Isso corrige a nota "resumo sem nada novo" acima. (2) As preferências passam a ser gravadas como último passo dentro da transação do import, e não logo depois dela: dados e preferências entram juntos ou nenhum entra, e uma morte do processo entre os dois não perde mais as preferências. (3) O arquivo exportado passa a ser JSON compacto, sem indentação; o contrato continua sendo JSON e a fixture v1 continua sendo lida.
- **2026-10-27, limites antigos de rotina:** as rotinas do arquivo passam pelas regras de edição atuais. Um alvo de rotina acima de 100000 kg, digitado numa versão de 1.0 a 1.3 (quando esse limite ainda não valia para rotinas), faz o próprio backup do usuário ser recusado inteiro numa instalação nova. Isso segue a regra de validação desta spec e é improvável, então fica como está. Se aparecer um caso real, a saída é limitar esses valores em `initialize()`, como já se faz com o descanso acima de 300 s.
- **2026-10-27, atomicidade das preferências:** as preferências são gravadas como último passo dentro da transação do import. Uma falha na gravação delas desfaz os dados. O DataStore não participa da transação do Room, então uma falha no commit depois da gravação deixaria as preferências aplicadas sem os dados. O caso é raro e foi aceito.
