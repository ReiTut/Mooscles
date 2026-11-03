# Recordes sem travar a tela

Data: 2026-10-27. Branch: `fix/pr-off-main-thread` (a partir de `feat/export-import`). Item da fase 0 do [ROADMAP](../ROADMAP.md): "Cálculo de PR fora da thread principal". O usuário delegou as decisões ("decide por mim"), então tudo abaixo é escolha minha, salvo quando indicado.

## Por que

Com um histórico de cerca de 208 treinos e 12,7 mil séries, o APK de debug (o que os usuários recebem) congela a tela por cerca de 48 s ao abrir o app e depois de um import. Se a pessoa toca na tela nesse tempo, o Android mostra "Mooscles isn't responding" ([verificação do export/import](../reports/2026-10-27-export-import-verificacao.md)). O usuário decidiu que isso precisa estar resolvido antes do PR para o repositório original, para o import não terminar num aviso de travamento justamente para quem tem mais histórico.

A causa está em `WorkoutPresentation.present`. Ao montar o histórico, ele chama `WorkoutCalculations.prsForWorkout` e `repsPrsForWorkout` para cada treino finalizado. Cada chamada reconstrói o `SnapshotIndex` inteiro e varre todos os treinos anteriores, o que dá custo quadrático. E tudo isso roda na thread principal, no fluxo `presentation` do `AppViewModel`.

## Objetivos

1. **O mesmo resultado, muito mais rápido.** Os recordes de peso e de repetições de todos os treinos do histórico saem de uma única passada em ordem cronológica, com o índice montado uma vez só. O resultado é idêntico ao das funções atuais por treino: mesmos `WeightPr`/`RepsPr`, mesma ordem, mesmos `previous`/`firstRecord`, mesmas tolerâncias de ponto flutuante (`heavier`, `sameLoad`) e mesma ordem cronológica (`startedAt`, `endedAt ?: MAX`, `id`).
2. **A apresentação sai da thread principal.** `present()` passa a rodar num executor de fundo de uma thread só, que preserva a ordem sequencial e o estado interno do `WorkoutPresentation`. Os estados continuam chegando à UI pelo mesmo `contentUi`. Enquanto o primeiro estado não chega, vale o `loading = true` que já existe.
3. **Meta medida:** na base grande, no emulador de referência (`Mooscles_Pixel8_API35`) com o APK de debug:
   - nenhum ANR, mesmo tocando na tela durante a abertura e durante o import;
   - nenhum quadro da thread principal bloqueado por mais de 700 ms por causa da apresentação;
   - o histórico aparece em até 3 s depois do primeiro quadro na abertura a frio.

   No host, um teste de desempenho com a mesma base mede `present()` e falha acima de um teto folgado (o número real vai para o relatório).

## Fora de escopo

- Mudar o que é recorde ou como ele aparece na tela.
- Mudar o banco, as migrações ou as preferências.
- Otimizar o `synchronizeRoutineTargets` do repositório e outros cálculos fora de `present()`, a menos que a medição mostre que eles também bloqueiam a thread principal. Nesse caso, isso vira uma nota datada aqui antes de entrar no plano.
- Paginação ou carregamento preguiçoso do histórico na UI.

## Anti-objetivos

- Recordes diferentes dos de hoje em qualquer histórico. Uma divergência é bug, mesmo que o número novo pareça "mais certo".
- Telas piscando ou listas recriadas sem necessidade. A reutilização de identidade que o `present()` já faz (`previousActive`, `old?.takeIf { it == entry }`) continua.
- Condição de corrida no estado do `WorkoutPresentation`: ele nunca roda em duas threads ao mesmo tempo.
- Ticks do timer recompondo as telas. O `timerUi` continua separado.

## Decisões de implementação

- **O treino ativo** continua com o cálculo por treino. É uma chamada só, de custo linear. O histórico usa o resultado da passada.
- **Onde fica a passada:** em `WorkoutCalculations`, como funções puras novas, por exemplo `prsByWorkout(data): Map<String, List<WeightPr>>` e o equivalente para repetições. As funções por treino continuam existindo para o resumo e para o treino ativo. Os nomes exatos ficam para o plano.
- **O executor:** um dispatcher de fundo com paralelismo 1 (`Dispatchers.Default.limitedParallelism(1)`) aplicado ao fluxo `presentation` (`flowOn`). O comentário do `AppViewModel` e a regra do AGENTS.md que diz "roda na thread de UI" são atualizados para "roda num executor sequencial de fundo, nunca em composição".
- **A prova de equivalência:** um teste gera históricos aleatórios com semente fixa e compara, treino a treino, o resultado da passada com o das funções atuais. Os históricos incluem empates de carga, pesos com ruído de ponto flutuante, assistência, aquecimento, séries incompletas, treinos com o mesmo `startedAt` e treinos ativos misturados. Os testes de recordes e de apresentação que já existem continuam verdes sem mudança.

## Como verificar

- Suíte de host completa, `lintDebug`, `assembleDebug` e `compat-reviewer` (deve dar SEGURO: nada de dados muda).
- No emulador, com a base grande (o gerador da verificação do export/import): abertura a frio, import, troca kg/lb e um treino iniciado e finalizado. Medir o tempo até o histórico aparecer e o maior bloqueio da thread principal (logcat `Choreographer`/`Skipped frames` ou um trace), e provocar toques durante o carregamento para confirmar que o ANR não aparece. Registrar em `docs/superpowers/reports/2026-10-27-pr-off-main-thread-verificacao.md`, com o antes e o depois.
- Atualizar o relatório de verificação do export/import e o ROADMAP: o travamento depois do import passa a constar como resolvido, com o link.

## O que fica para quem implementar

- Os nomes das funções novas e a estrutura interna da passada (mapas por `Key`, ordem de atualização das linhas de base).
- Se `workoutItems`, que filtra a lista inteira, também precisa de índice dentro de `present()`, decidido pela medição.
- O teto exato do teste de desempenho no host.

## Notas de implementação

- **2026-10-27, resultado no emulador** ([verificação](../reports/2026-10-27-pr-off-main-thread-verificacao.md)):
  - **Metas cumpridas:** nenhum ANR e nenhum bloqueio da thread principal acima de 700 ms causado pela apresentação. A abertura a frio, que travava 48–52 s, agora deixa a tela responsiva com o indicador de carregamento. Depois do import, o maior quadro fica entre 202 e 283 ms (antes eram 47–77 s congelados). Na troca kg/lb, o maior quadro fica entre 79 e 542 ms (antes eram 61,7 s).
  - **Meta não cumprida:** a de "histórico em até 3 s depois do primeiro quadro". No APK de debug, que é o distribuído, o histórico leva 15–20 s para aparecer na abertura a frio. No build com R8 leva 2,3–3,1 s.
  - **Por que fica assim:** a diferença vem do processo debuggable recém-aberto, onde o `present()` leva 12–24 s; com o processo já aquecido, leva 1–7 s. Há ainda uma espera de cerca de 8–9 s antes do `present()` começar quando a base tem 48 exercícios, que não foi investigada. Decidi encerrar a branch assim: o travamento e o ANR, que eram o problema do usuário, acabaram. O tempo até o histórico aparecer no debug fica registrado como pendência, junto com duas saídas possíveis: investigar esse custo no aparelho, ou distribuir um build não debuggable assinado com a chave original, assunto que já depende da chave pendente no ROADMAP.
- **2026-10-27, por que a decisão de 23/10 é revertida:** em 23/10 a apresentação voltou para a thread principal (ver [PERFORMANCE.md](../../../PERFORMANCE.md)) para evitar passadas redundantes. Essas passadas continuam evitadas pelo `distinctUntilChanged()` das preferências e pelo `conflate()` do fluxo de apresentação. Já o custo quadrático dos recordes fazia da apresentação na thread principal uma fonte de ANR, por isso ela volta para o fundo.
