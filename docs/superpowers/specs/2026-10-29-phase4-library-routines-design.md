# Fase 4: biblioteca de exercícios e rotinas visuais

Data: 2026-10-29. Status: **aprovada (2026-10-29): D3 aceita pelo dono; licenças pela pesquisa de 2026-10-29**. Item da fase 4 do [ROADMAP](../ROADMAP.md). Sistema visual: [spec da fase 2](2026-10-27-phase2-design-system-design.md) ("Giz & Ferro", seção "Ilustração de exercício") e as suas notas de implementação. Telas e casca: [spec da fase 3](2026-10-29-phase3-active-workout-design.md) (módulo `:screens`, D4 e D5) e as suas [notas de implementação](2026-10-29-phase3-active-workout-design.md#notas-de-implementação). Pesquisas: [P2, mídia](../reports/2026-10-27-midia-exercicios.md), [P3, bibliotecas](../reports/2026-10-27-bibliotecas-cmp-graficos-animacao-midia.md). Medições desta spec: [notas](../research/2026-10-29-phase4-midia/medicoes.md) e [mapeamento](../research/2026-10-29-phase4-midia/mapeamento.tsv).

As decisões vêm de duas fontes. As marcadas **(controlador)** foram passadas pelo controlador da fase, que fala pelo dono; as marcadas **(minha)** são de quem escreveu a spec, a partir delas, do código em `4f41792` e das medições. Todas são revisáveis. Cada uma aparece como **Dn** com uma linha de motivo.

> **Bloqueio encontrado na medição (2026-10-29).** O controlador decidiu usar os quadros de início e fim da free-exercise-db (Unlicense). A medição mostrou que o Unlicense cobre o JSON e o código, mas **não as fotos**: o autor do dataset de origem escreve que elas foram "scrapped off the internet", que ele não tem os direitos e que desaconselha o uso comercial, e o mantenedor da free-exercise-db diz não saber de onde vêm ("usage would be at your own risk"). Republicar essas fotos numa release nossa é redistribuição, que a regra da P2 e do ROADMAP exclui. Além disso, a fase 2 aprovou com o dono um estilo de ilustração próprio ("giz") e um piloto próprio de 20 exercícios (decisão 4 da aprovação). Por isso esta spec **não usa as fotos nem as instruções da free-exercise-db** e propõe, como D3, ilustrações próprias no estilo aprovado. A parte do download (release, manifesto, verificação, Wi-Fi) fica especificada como uma etapa condicional, 4M, que só é construída se um conjunto de mídia que não caiba no APK for encomendado. Evidências e números em [medições](../research/2026-10-29-phase4-midia/medicoes.md#licença-o-unlicense-não-cobre-as-fotos). A primeira pergunta ao dono, no fim, é sobre isso.

## Por que

A biblioteca e as rotinas são as telas mais antigas que sobraram depois da fase 3, e as que mais pesam no dia a dia fora do treino:

- a biblioteca é uma lista de cartões do Material com dois menus suspensos e o nome em inglês; nenhum exercício tem imagem, e tocar num deles abre só o histórico;
- não há tela de detalhe: nada diz que músculos o exercício trabalha nem como executar;
- montar uma rotina exige escolher um exercício por vez, num diálogo; reordenar é "Move up" e "Move down" pelo menu, dois toques por posição;
- a prescrição por série existe nos dados (`routine_sets`), mas não dá para marcar uma série planejada como aquecimento;
- as pastas existem, mas não dá para excluir nem reordenar uma pasta, e a pasta padrão aparece como "My routines" em pt-BR;
- o card do treino ativo mostra o alvo só como valor pré-preenchido; a linha "Alvo 3×8 · 80 kg" ficou para esta fase (desvio datado da fase 3);
- os nomes do catálogo são a única mistura de idioma conhecida nas telas novas em pt-BR.

## Objetivos

1. **Cada exercício do catálogo tem cara:** uma ilustração própria em dois quadros, alternados por animação Compose, no estilo "giz" da fase 2, embutida no app e disponível sem rede.
2. **Tela de detalhe** com a execução, o mapa corporal dos músculos trabalhados, dicas curtas de execução e o resumo do histórico da pessoa.
3. **Achar um exercício rápido:** busca que entende pt-BR e inglês (nomes, apelidos de academia, músculo e equipamento) e filtros por grupo muscular e equipamento.
4. **Nomes do catálogo em pt-BR**, sem mudar a identidade de nenhum exercício nem nenhum snapshot do histórico.
5. **Montar uma rotina de forma visual e rápida:** seleção múltipla de exercícios, arrastar e soltar para reordenar, prescrição por série com aquecimento, descanso por exercício.
6. **Pastas completas:** criar, renomear, excluir (sem perder rotina) e reordenar.
7. **Linha de alvo no card do treino**, vinda da fase 3.
8. **Exercício personalizado com foto própria**, tirada ou escolhida na galeria, guardada só no aparelho.
9. **Bilíngue e acessível** como a fase 3: todos os textos em en e pt-BR, 200% de fonte sem corte, alternativas por toque e por ação de acessibilidade para todo gesto.
10. **iOS provado no simulador do CI**, como na fase 3, para tudo o que é comum.

## Fora de escopo

- **Fotos, vídeos ou GIFs de terceiros**, inclusive os da free-exercise-db (ver o bloqueio acima). Vídeo e Lottie também ficam fora (a P3 deixa os dois como risco no iOS, e o estilo aprovado é de dois quadros).
- **Construir a etapa 4M** (download de mídia) sem um conjunto de mídia encomendado. Ela fica especificada, não implementada (D10).
- **Histórico do exercício, gráficos e recordes redesenhados** (fase 5). O detalhe mostra um resumo e leva à tela antiga `exercise/{id}`.
- **Excluir exercício personalizado** e mesclar exercícios duplicados.
- **Faixa de repetições** ("8–12"), RPE, tempo sob tensão, superséries e prescrição por porcentagem.
- **Drop e falha como tipos de série** (D23 da fase 3, spec própria). O aquecimento já existe no treino; aqui ele só passa a existir também na rotina.
- **Foto do exercício personalizado no backup** (D18) e qualquer sincronização.
- **App iOS (`iosApp/`)**, seletor de fotos e câmera do iOS (fase 7). O código comum fica pronto; a implementação iOS da foto entra com o `iosApp`.
- **Mensagens do updater e do backup em pt-BR** (D22 da fase 3, fase 6), exceto a frase nova da exportação sobre fotos (ver "Textos").
- **NavHost comum** (D5 da fase 3, fase 6).
- **Release publicada.** Como nas fases 1 a 3, a fase termina com merge na `main` do fork e CI verde.

## Anti-objetivos (falha mesmo que "funcione")

- **Perder dado:** uma rotina, uma série planejada, um treino, uma foto ou um exercício personalizado por excluir uma pasta, reordenar, desfazer, girar a tela, matar o processo ou atualizar o APK por cima.
- **Mudar a identidade de um exercício:** traduzir, renomear ou remapear o `id` de um exercício do catálogo, ou alterar o `name` gravado em `workout_exercises` (o histórico é um snapshot).
- **Mídia sem licença registrada:** qualquer imagem, ilustração ou texto de execução no app ou numa release cuja origem e licença não estejam escritas no repositório e na tela de licenças.
- **A biblioteca depender de rede:** nenhuma tela desta fase faz requisição de rede; o app continua falando só com o GitHub do updater.
- **Quebrar instalações existentes:** migração que não seja aditiva, chave do DataStore renomeada, campo obrigatório novo no backup, mudança de `applicationId`, assinatura, `update.json` ou da descoberta de atualizações.
- **Registrar ficar mais lento:** nada desta fase pode pôr trabalho no caminho do check da série; a linha de alvo é calculada na apresentação, nunca no toque.
- **APK crescer mais de 1 MiB** sobre a `main` em `4f41792` somando 4A e 4B (D20).
- **Um segundo design para o iOS:** toda tela, componente, figura e texto novo em `commonMain`; só a foto (seletor, câmera, arquivo) tem código por plataforma.
- **Inglês novo em pt-BR:** um texto ou nome desta fase em inglês com o aparelho em pt-BR. Os nomes digitados pela pessoa são a exceção óbvia.

## Etapas

**D1 (controlador). Uma spec, duas etapas que entram e são verificadas separadamente: 4A biblioteca e 4B rotinas.** Cada uma tem branch própria a partir da `main` (`feat/phase4a-library`, depois `feat/phase4b-routines`), `compat-reviewer`, `/code-review`, CI verde e relatório de verificação próprio. *Por quê:* as duas mexem em telas diferentes; a 4B é a única com mudança de schema e merece revisão isolada.

| Superfície | Etapa | Observação |
| --- | --- | --- |
| Aba Biblioteca (lista, busca, filtros) | 4A | nova, no `:screens` |
| Detalhe do exercício (rota nova) | 4A | nova |
| Figuras próprias (renderizador + piloto de 20 + lotes) | 4A | D3–D5; os lotes depois do piloto podem continuar durante a 4B |
| Mapa corporal | 4A | D6 |
| Nomes do catálogo em pt-BR e busca bilíngue | 4A | D11, D12 |
| Formulário de exercício personalizado (sem foto) | 4A | substitui o diálogo antigo |
| Seletor de exercício do treino com filtros | 4A | o da fase 3, com os filtros |
| Aba Treinar (rotinas e pastas) | 4B | nova; a fase 3 a deixou de propósito (D3 da fase 3) |
| Gerenciar pastas | 4B | sheet nova |
| Editor de rotina | 4B | substitui `RoutineEditor` e `RoutineSetEditor` |
| Aquecimento na série planejada (schema v6) | 4B | D14 |
| Linha de alvo no card do treino | 4B | D16 |
| Foto do exercício personalizado | 4B | D17, D18 |
| Download de mídia (4M) | condicional | D10; não entra sem mídia encomendada |

**D2 (minha). O formulário de exercício personalizado entra na 4A, sem a foto; a foto entra na 4B.** *Por quê:* a biblioteca nova precisa do "Criar exercício" para não abrir um diálogo do Material dentro de uma tela nova; a foto traz o código de plataforma, a câmera e o `FileProvider`, e fica com o resto do que o controlador pôs na 4B.

**Chamada do controlador (2026-10-29), sequência do G1: a 4A não espera o G1.** A biblioteca, o detalhe (com o ícone do equipamento onde não há figura), a busca e os filtros, os nomes em pt-BR, o mapa corporal, a tabela de anatomia e o renderizador de poses com o piloto de 20 são construídos e entram na `main` do fork; a folha do piloto vai para o dono. Os lotes depois do piloto (e a página "Figuras" da galeria para eles) só começam depois do G1 aprovado. Se o dono reprovar o piloto, a encomenda e a 4M passam para uma fase futura (não a 4); a fase 4 fecha com o ícone do equipamento e o piloto removido do produto. *Por quê:* o portão G1 depende do dono e não pode travar o resto da 4A.

### Herdado da fase 3

Da [verificação](../reports/2026-10-29-phase3-verificacao.md) e do [adendo da revisão final](../reports/2026-10-29-phase3-verificacao.md#adendo-2026-10-29-revisão-final):

| Pendência | Nesta fase |
| --- | --- |
| Linha "Alvo 3×8 · 80 kg" no card do treino | sim, D16 (4B) |
| Nomes do catálogo em inglês com o aparelho em pt-BR | sim, D11–D13 (4A) |
| Telas antigas Treinar e Biblioteca em inglês | sim (4B e 4A); histórico e recordes ficam para a fase 5 |
| #11 da fase 2 (`MTextField` lê o valor antes do rótulo) | não; continua aberto até uma sessão com TalkBack, e o campo de busca herda o problema |
| TalkBack, háptico e 60 fps num aparelho | não, depende do dono (pergunta 4) |
| A notificação do descanso lê todas as séries do treino | não; é do treino ativo, sem relação com esta fase |
| A prévia antiga do cartão no `:app` ("Workout preview") | não; fase 5, com o cartão |
| Capturas do iOS sem revisão visual | sim: as capturas do teste de ponta a ponta desta fase entram na crítica de cada etapa |
| Cerca de 46 MB de PNG das fases 2 e 3 no histórico do git | **(minha)** Nesta fase, só entram no git as capturas citadas nos relatórios, em PNG com a paleta reduzida quando a crítica não precisar de cor exata; o resto fica como artefato do CI ou na pasta local do relatório, fora do git. *Por quê:* o item 2 de "Antes de abrir o PR" da [v1.5.0](../releases/v1.5.0.md) já conta o peso das fases 2 e 3 |

## Mídia do exercício

### O que o controlador pediu, e o que a medição mudou

- **Controlador:** quadros de início e fim da free-exercise-db; "animação" como alternância com cross-fade em Compose, igual no Android e no iOS, respeitando o movimento reduzido, sem GIF, vídeo nem Lottie; download sob demanda de uma release do GitHub com `media-manifest.json` (sha256 e tamanho por arquivo, versionado por tag), cache offline e verificação como a do `ApkVerifier`; miniaturas embutidas só se couberem no orçamento; entrada de créditos na tela de licenças; o ícone do equipamento para os exercícios sem par; decidir onde mora a release sem mexer na descoberta de atualizações; decidir a política de Wi-Fi.
- **Medição** ([notas](../research/2026-10-29-phase4-midia/medicoes.md)):
  - cobertura por nome: 248 de 287 entradas do catálogo (187 exatas, 61 prováveis); com as duas imagens, **246 de 287 (85,7%)**, usando 216 ids do dataset; 41 sem mídia;
  - tamanho: 432 quadros, 25,93 MiB em JPG; **7,56 MiB em WebP de 640 px** (cerca de 36 KB por exercício); miniaturas quadradas de 160 px somam **734 KiB** e de 192 px, 943 KiB;
  - as miniaturas na densidade da moldura de 64 dp ocupam de 72% a 92% do orçamento de 1 MiB, que a fase também precisa para as telas: **não cabem**;
  - **licença: as fotos não são livres** (bloqueio acima).

**D3 (minha, substitui a fonte da decisão do controlador). As ilustrações são próprias, no estilo "giz" aprovado na fase 2, desenhadas por código a partir de poses.** Cada exercício ganha dois quadros (início e fim), e cada quadro é uma **pose**: a posição do quadril e os ângulos de tronco, cabeça, braços, antebraços, coxas, pernas e pés, sobre um corpo de proporções fixas, mais o equipamento em primitivas (barra com anilhas de frente, halter, kettlebell, banco reto, inclinado ou declinado, polia com o cabo até a mão, assento e apoio de máquina, barra fixa, caixa, plataforma do leg press, chão). Um componente comum desenha a pose com traço grosso de ponta redonda: cabeça em círculo, membros em cápsulas, sem rosto nem gênero, a figura em `text.secondary`, o equipamento em `text.tertiary`, o segmento do músculo principal em `accent.solid`, fundo transparente. *Por quê:*

- é exatamente o estilo aprovado ("cabeça em círculo e membros em cápsulas", dois quadros, vetor) e a decisão 4 da aprovação da fase 2 ("as ilustrações começam com um piloto próprio de 20 exercícios");
- é obra própria, com licença que o projeto escolhe (pergunta 2 ao dono);
- pesa quase nada (uma pose são cerca de 20 números): o catálogo inteiro cabe no APK, funciona sem rede desde a instalação e não precisa de hospedagem, manifesto nem política de Wi-Fi;
- os ângulos sobre ossos de comprimento fixo garantem, por construção, que a figura não muda de tamanho entre os quadros, e isso se testa.

**Verificação (2026-10-29):** o `CONTRIBUTING.md` do autor original (wrkout/exercises.json) diz que as imagens foram "scrapped off the internet… I do not own the copy right… would advise against using them in comercial projects", e o mantenedor da free-exercise-db escreveu na issue #2 "no idea where the images are from or if they are royalty free so usage would be at your own risk". **Aprovada pelo dono em 2026-10-29** (figuras próprias "giz", piloto de 20 exercícios e portão G1). **Chamada do controlador (2026-10-29):** o dono aprovou a D3 e pediu ao controlador que decidisse as bibliotecas; a pesquisa P5 ([relatório P5](../reports/2026-10-29-figuras-mapa-muscular.md)) decidiu: renderizador FK próprio em Compose `Canvas` puro, sem Compottie, Rive, Spine nem DragonBones (o Compottie lite custa cerca de 0,55 MB de dex comprimido e o Rive passa de 2 MB, além das licenças). As poses são escritas à mão, sem datasets de pose (licenças de pesquisa); o esqueleto de 13 juntas do Penn Action pode inspirar o esqueleto mínimo (somando pescoço e tronco). Acervos prontos em CC BY-SA (Everkinetic, Workout Guide) servem só de referência visual de posição, nunca decalcados nem copiados, e as consultas ficam registradas no plano. Nenhum asset gerado por IA.

**Aviso de tamanho (P5, inferência a medir no piloto):** 287 exercícios × 2 poses × cerca de 20 números dão perto de 11.500 constantes; uma única tabela Kotlin assim pode encostar no limite de 64 KB de bytecode por método da JVM (inicializador estático) e inflar o dex do APK debug, que não passa por R8. Divida a tabela por família ou por lote, ou guarde as poses num formato compacto codificado, e meça no piloto (S1).

A free-exercise-db pode servir, com cuidado, de referência visual de quem desenha as poses (que posição o movimento tem), nunca de fonte copiada.

**D4 (minha). Primeiro um piloto de 20 exercícios, com portão de qualidade; depois lotes por equipamento até o catálogo inteiro.**

- Piloto (spike S1): `barbell_bench_flat`, `barbell_back_squat_high_bar`, `barbell_deadlift_conventional`, `barbell_overhead_press`, `barbell_row_overhand`, `barbell_deadlift_romanian`, `barbell_hip_thrust`, `dumbbell_bench_flat`, `dumbbell_press_seated`, `dumbbell_raise_lateral`, `dumbbell_curl_standing`, `dumbbell_squat_bulgarian`, `cable_lat_pulldown_wide`, `cable_row_close_neutral`, `cable_triceps_pushdown_rope`, `bodyweight_pull_up`, `bodyweight_push_up`, `plate_leg_press_45`, `selectorized_leg_extension`, `selectorized_leg_curl_lying` (todos com o prefixo `catalog_`). Cobre barra, halter, polia, peso corporal e as duas famílias de máquina, em pé, deitado, sentado e pendurado; kettlebell, Smith e "Outro" entram nos lotes.
- **Portão G1:** uma folha de capturas com os 20 (quadros A e B, nos dois temas, a 64 dp e no tamanho do detalhe), `critique-screen` sem achado alto de legibilidade, e o dono aprova a folha (pergunta 1). Se reprovar, as figuras não entram: o detalhe e a lista ficam com o ícone do equipamento, e a fase segue com o resto; a encomenda de ilustrações e a 4M (D10) passam para uma fase futura, e o piloto sai do produto (chamada do controlador, 2026-10-29, ver D2). *Por quê:* a fase 4 não pode esperar uma encomenda.
- **(2026-10-30, portão G1)** O dono aprovou o piloto de 20 exercícios com uma mudança: o equipamento (barra, anilhas, máquinas, bancos e cabos) passa a ser **cobre**, um laranja, para se destacar do corpo cinza e do músculo verde; antes era `text.tertiary` (D3). O controlador comparou os candidatos em contraste e em separação para daltônicos e decidiu: nova primitiva `cobre` (`cobre47` = `#B4531A` no tema claro, `cobre64` = `#E8813E` no escuro) e o token `equipment` em `MoosclesFigureColors`. Nos quatro fundos de página e de peça, o claro tem no mínimo 4,09:1 e o escuro 5,8:1; sob simulação de deuteranopia mantém ΔE de cerca de 38 a 39 do verde do destaque. O latão ficou de fora porque já significa recorde pessoal, e a brasa porque significa perigo (e é a pior na deuteranopia). A folha do piloto foi refeita com o equipamento em cobre. Os lotes (Task L) seguem.
- Depois do G1, os lotes seguem por equipamento (barra, halter, polia, peso corporal, máquinas, kettlebell, Smith), cada um com a sua folha de capturas no relatório. São só dados e testes, sem schema; podem entrar em commits próprios durante a 4A e a 4B.
- **Meta da fase:** as 287 entradas do catálogo com figura **se o G1 for aprovado** (lotes de cerca de 50, cada um entra na `main` do fork); caso contrário, o ícone do equipamento. **Chamada do controlador (2026-10-29):** o compromisso de conclusão está em D21.

**D5 (minha). Comportamento da figura.**

- **No detalhe:** os dois quadros alternam a cada `timing.figureFrame` (token novo, 900 ms, como a fase 2 fixou) com cross-fade de `motion.base`. A alternância pausa quando a figura sai da tela, quando o app vai para segundo plano e com "reduzir movimento"; aí fica o quadro B (fim do movimento) e um toque alterna os quadros. Rótulo acessível: "Ilustração: início e fim do supino reto com barra"; tocar não é anunciado como ação quando a alternância é automática.
- **Na lista, no seletor, no editor e no card do treino:** só o quadro B, parado, a 56 ou 64 dp. Nada anima numa lista.
- Desenho no `Canvas` com `drawWithCache` (a geometria só é recalculada quando o tamanho ou a pose mudam); nenhum bitmap intermediário.
- **Sem figura** (exercício personalizado sem foto, ou do catálogo ainda sem pose): o `ExerciseMedia.None` atual, com o ícone do equipamento. O texto "Imagem ainda não baixada" da fase 2 não aparece, porque nada é baixado.
- **Exercício personalizado com foto** (4B): a foto no lugar da figura, recortada em 1:1, sem alternância.

### Onde as figuras vivem

- **Renderizador** no `:designsystem` (`illustration/ExerciseFigure.kt`): recebe a pose (modelo próprio do design system, que não conhece o `:shared`), o quadro, as regiões em destaque e o tamanho. O `ExerciseMedia` ganha a variante de figura. A galeria ganha a página "Figuras", com todas as poses lado a lado (A e B), nos dois temas, para revisão.
- **Poses** no `:screens` (`com.mooscles.screens.exercise.figures`), numa tabela Kotlin chaveada pelo `stableId` do catálogo (o `id` sem o prefixo `catalog_`). Ficam fora do `:shared` porque são apresentação.
- **Esqueleto:** comprimentos fixos numa caixa de 100 × 100 (por exemplo: tronco 30, braço 15, antebraço 14, coxa 21, perna 20, pé 6, raio da cabeça 5; os valores finais saem do S1). O lado de lá dos membros fica atrás e num token mais apagado, escolhido no S1 entre os pares já medidos; nenhum literal de cor.
  - *Nota (2026-10-31, elevação do ombro):* elevação de ombro opcional, padrão 0, sem mudar as proporções. A `FigurePose` ganha `shoulderLift`, em unidades da caixa: a junta do ombro (onde os braços nascem) sobe ao longo da linha do tronco, na direção do pescoço, e o resto fica onde estava (quadril, o topo do tronco, o pescoço, a cabeça e os comprimentos dos braços). O valor é limitado a `FigureSkeleton.SHOULDER_LIFT_MAX` (3, o comprimento do pescoço, cerca de um sexto do tronco), para o ombro nunca passar da base da cabeça. Com 0, toda pose existente sai idêntica bit a bit. *Por quê:* os três encolhimentos (`barbell_shrug`, `dumbbell_shrug`, `smith_shrug`) tinham os quadros A e B quase iguais, porque o ombro era um ponto preso ao tronco (achado A1 do [lote da barra](../reports/2026-10-31-phase4-figuras-barra.md)); o controlador decidiu por esse ajuste em vez de mexer nas proporções. Capturas no [relatório final das figuras](../reports/2026-10-31-phase4-figuras-final.md#elevação-do-ombro-nos-encolhimentos-2026-10-31).
- **Destaque do músculo na figura:** grosseiro, por segmento (tronco na frente para peito e abdômen, tronco atrás para costas, ombro, braço na frente ou atrás, antebraço, coxa na frente ou atrás, quadril para glúteos, perna atrás para panturrilha). O detalhe fino é do mapa corporal (D6).
- Os valores de traço, de margem e o tempo de alternância são tokens novos em `tokens/` com teste em `commonTest`, como a regra exige. As coordenadas da pose são dados sem unidade, não medidas em `dp`.

## Mapa corporal

**D6 (controlador). Mapa corporal próprio, de frente e de costas, desenhado como `ImageVector`, com as regiões dos músculos principais e secundários em destaque.**

- **Chamada do controlador (2026-10-29), fonte do desenho:** o mapa é **adaptado do `Jsplice/MuscleMap` (`@musclemap/assets`, MIT, paths traçados à mão, com regiões que incluem ombro frontal, lateral e traseiro)**, em vez de desenhado do zero; plano B, `vulovix/body-muscles` (Apache-2.0, com o NOTICE junto). Pesquisa: [relatório P5](../reports/2026-10-29-figuras-mapa-muscular.md). Mantém-se o restilo "giz" (contorno e preenchimento pelos tokens) **sem mexer na geometria**. Pescoço: fundido ao trapézio, a menos que o spike mostre outra coisa (o builder pode rever). O hash do commit de origem é anotado no momento da importação. Guardar as strings de path e convertê-las em tempo de execução (`PathParser`) ou gerar nós de `ImageVector` fica com o builder, **medindo no spike** (tamanho em dex). O primeiro trabalho do mapa é um **spike de meio dia**: renderizar o mapa no nosso estilo, nos dois temas, a 1,0 e a 2,0 de densidade, e medir o tamanho em dex.
- **Desenho (minha):** duas silhuetas lado a lado (frente e costas), no mesmo traço "giz", com 17 regiões como caminhos separados (agrupadas a partir das regiões da fonte; os três deltoides viram "ombros" e `RHOMBOID` vira "meio das costas"): abdominais, abdutores, adutores, bíceps, panturrilhas, peito, antebraços, glúteos, posteriores da coxa, dorsais, lombar, meio das costas, pescoço, quadríceps, ombros, trapézio e tríceps (o vocabulário anatômico comum, que também é o da free-exercise-db). O SVG fonte fica em `designsystem/illustrations/` e é convertido por um script em `tools/illustrations/` (como o `tools/icons/gen.py`), num pacote fora de `icons/` (`com.mooscles.designsystem.illustration`).
- **Cores:** principal em `accent.solid`, secundário em `accent.subtle` ou o token que o S3 escolher, o resto no preenchimento neutro. Os pares novos (principal e secundário contra o neutro, e cada um contra o fundo) entram no `ComponentColorsTest` com contraste não textual de pelo menos 3:1 (WCAG 1.4.11), nos dois temas.
- **Acessibilidade:** o mapa é uma imagem com equivalente em texto logo abaixo, sempre visível: "Principais: quadríceps. Secundários: glúteos, posteriores da coxa." A cor nunca é o único sinal (a legenda tem amostra e texto).
- **Licença:** **não** é obra própria em CC0: o mapa derivado do MuscleMap segue sob MIT, com o aviso na tela de licenças (D9).

**D7 (minha). Os músculos de cada exercício são uma tabela própria no `:shared`, `ExerciseAnatomy`**, com principal e secundários por `stableId` do catálogo, numa enumeração nova `MuscleRegion` com as 17 regiões. *Por quê:* a fase 5 vai precisar da mesma tabela para "distribuição por músculo"; e os músculos são escritos pelo projeto, não copiados de um dataset cuja origem não está clara.

- Cobertura: as 287 entradas na 4A (é entrada de dados, não depende das poses).
- Testes em `commonTest`: toda entrada do catálogo tem anatomia; principal não vazio; principal e secundário sem interseção; nenhuma chave órfã; o principal é coerente com o `MuscleGroup` do exercício (uma tabela fixa diz que regiões cada grupo pode ter; por exemplo, `BACK` admite dorsais, meio das costas, lombar e trapézio).
- **Exercício personalizado:** a região vem do `MuscleGroup` pela mesma tabela fixa, só como principal (por exemplo, `CHEST` destaca o peito). `FULL_BODY` não destaca nenhuma região e o texto diz "Corpo inteiro".

## Dicas de execução

**D8 (minha). Dicas curtas próprias, em vez de instruções copiadas.** Até 4 dicas por exercício, de no máximo 110 caracteres cada, em en e pt-BR, escritas para o Mooscles junto com a pose de cada exercício (por exemplo, "Escápulas juntas e para baixo durante toda a série."). Sem promessa de saúde nem linguagem médica. *Por quê:* as instruções da free-exercise-db têm a mesma origem não documentada das fotos; e quatro dicas leem melhor no meio da série que um parágrafo.

- Ficam em `screens/src/commonMain/composeResources/files/exercise_cues/en.json` e `pt-BR.json` (`stableId` → lista de textos), lidas uma vez, de forma assíncrona, e guardadas em memória. O idioma é o mesmo das outras strings (ver D12).
- Teste de paridade: as mesmas chaves e o mesmo número de dicas nos dois idiomas; nenhuma dica acima de 110 caracteres; nenhuma chave órfã.
- Exercício sem dicas: a seção some.
- Tamanho estimado: cerca de 2 × 90 KB de JSON, por volta de 50 KB comprimidos no APK.

**D21 (controlador). Quem escreve e quem revisa o conteúdo.** **Chamada do controlador (2026-10-29):** os agentes que implementam escrevem poses, anatomia, dicas, nomes em pt-BR e apelidos. Cada lote passa por (a) uma passada de agente independente, que confere anatomia e dicas contra o conhecimento geral de exercício e a pose contra as dicas, e (b) a folha de capturas com `critique-screen`. O dono revisa só a folha do piloto (G1) e, se quiser, os nomes em pt-BR. **Compromisso:** a fase 4 está pronta quando os 287 exercícios do catálogo têm anatomia, nome em pt-BR e dicas; figuras: os 287 se o G1 for aprovado, senão o ícone do equipamento. *Por quê:* o dono não é o revisor de conteúdo de 287 exercícios; o portão humano fica onde a decisão é dele.

## Licenças e créditos

**D9 (minha). As figuras, as poses, as dicas e os nomes em pt-BR são registrados como obra própria** (o mapa corporal tem regime à parte, abaixo), com um arquivo de licença em `designsystem/src/commonMain/composeResources/files/licenses/` e uma entrada nova na tela de licenças ("Ilustrações, mapa corporal e dicas de execução do Mooscles"). A licença é a CC0 1.0, registrada no arquivo `designsystem/src/commonMain/composeResources/files/licenses/media-CC0.md` (o mecanismo que a tela de licenças já lê) e na entrada nova da tela, no fork. **Chamada do controlador (2026-10-29):** a concordância do dono do repositório original (ReiTut) é tratada no PR final para o upstream (o plano da fase: PR só no fim de todas as fases) e não bloqueia os merges na `main` do fork; "licença CC0?" fica como pergunta leve ao dono (pergunta 2), com CC0 como padrão sem resposta. *Por quê:* o fork é do dono do projeto e o upstream só entra no fim. A free-exercise-db não aparece nos créditos, porque nada dela é distribuído. **Correção (2026-10-29, [relatório P5](../reports/2026-10-29-figuras-mapa-muscular.md)):** o mapa corporal, derivado do `Jsplice/MuscleMap`, **não** é obra nossa em CC0: ele sai sob MIT, com o texto da licença em `composeResources/files/licenses/`, uma entrada própria na tela de licenças e o commit de origem anotado. Figuras, poses, dicas e nomes em pt-BR continuam CC0. O repositório ainda não tem arquivo LICENSE e passa a ter vários regimes de licença de mídia (OFL, ISC, MIT, CC0): a licença do projeto é decisão do dono antes do PR para o upstream (pergunta ao dono, não bloqueante). Se a 4M for ativada, cada fonte de mídia de terceiros ganha a sua entrada, com autor, licença e link, antes da primeira release que a use.

## Etapa 4M (condicional): download de mídia

Só é construída se o dono encomendar um conjunto de mídia raster (ou de outro formato pesado) que não caiba no APK. Fica especificada para que um plano possa ser escrito sem novas perguntas.

**D10 (minha). Regras da 4M.**

- **Onde mora (minha):** num repositório público próprio para mídia (proposta: `ReiTut/Mooscles-media` na release final; `pedroyuuki/Mooscles-media` durante o trabalho no fork), nunca em `ReiTut/Mooscles`. *Por quê:* o updater lista as 100 releases mais recentes de `ReiTut/Mooscles` e trata uma release sem `update.json` como inválida: com uma release de mídia lá, todo app instalado passaria a mostrar "No compatible update found. Some releases lack valid update.json metadata." (`UpdateManager.kt`, linha 142) e perderia uma vaga da janela de 100. `ReleaseUrls.REPOSITORY` não muda.
- **Versão e confiança:** cada versão do app fixa uma tag de pacote (`media-1`, `media-2`…), e **o manifesto vai embutido no app** (arquivo, tamanho e sha256 de cada quadro), gerado no build a partir do `media-manifest.json` publicado na release. O app baixa `https://github.com/<repo>/releases/download/<tag>/<arquivo>` direto, sem chamar `api.github.com`. Uma tag publicada nunca é substituída nem apagada (é contrato com os apps instalados, como as releases do app).
- **Rede:** lista de hosts permitida própria (o `github.com` só no caminho `/<repo>/releases/download/<tag>/`, mais os hosts de redirecionamento que o `ReleaseUrls` já aceita), só HTTPS.
- **Verificação:** tamanho e sha256 conferidos contra o manifesto embutido antes de qualquer uso; um SHA-256 em Kotlin puro no `commonMain`, com teste de equivalência contra o `MessageDigest` da JVM (`androidHostTest`) e os vetores do NIST (`commonTest`). Arquivo que não confere é apagado e nunca aparece; a tela mostra "Não foi possível verificar a imagem" com "Tentar de novo".
- **Armazenamento:** em `files/exercise_media/<tag>/` (não no cache, que o sistema pode limpar e quebraria o offline); grava num temporário e renomeia. Pastas de outras tags são apagadas na partida.
- **Wi-Fi e dados (minha):** baixar os dois quadros de um exercício ao abrir o detalhe acontece em qualquer rede, porque custa cerca de 36 KB (medido na 640 px); um pacote único de miniaturas (cerca de 0,73 MiB a 160 px) é baixado uma vez ao abrir a biblioteca, também em qualquer rede; "Baixar todas as imagens (7,6 MB)" é explícito e, por padrão, só em rede sem cobrança (Wi-Fi), com confirmação para usar dados móveis. Com a Economia de dados do Android ligada, nada é baixado sozinho, só pelo toque em "Baixar (36 KB)". Uma preferência nova, "Baixar imagens automaticamente" (chave nova `media_auto_download`, booleana, padrão verdadeiro, opcional no backup), desliga os downloads automáticos. *Por quê:* offline primeiro (cache permanente), custo de dados medido e pequeno, e a pessoa que não quer nenhuma rede além do updater tem um interruptor.
- **iOS:** a interface do download fica no `commonMain`; a implementação iOS entra com o `iosApp` (fase 7).
- **Sem Coil:** as imagens locais são decodificadas pelo `decodeToImageBitmap` do Compose Multiplatform, fora da thread principal, com cache em memória; o APK debug não passa por R8, e uma biblioteca de imagem inteira não cabe no orçamento para esse uso.

## Nomes em pt-BR e busca

**D11 (controlador). Os nomes do catálogo ganham pt-BR por `stableId`, sem mudar a identidade de nenhum exercício nem o histórico.**

- **Onde (minha):** numa tabela Kotlin no `:shared`, `ExerciseNames` (`stableId` → nome em pt-BR), ao lado do `ExerciseCatalog`, que continua com o nome em inglês. *Por quê:* o nome é dado do catálogo e a busca (no `:shared`) precisa dos dois idiomas qualquer que seja o idioma da tela; recurso de string só existe no idioma resolvido.
- **O que não muda:** o `id`; a coluna `exercises.name` continua recebendo o nome em inglês do catálogo no `initialize()`; o `name` gravado em `workout_exercises` não é tocado; o backup exporta o que está gravado.
- **Exibição:** as telas novas mostram, para um exercício do catálogo (`custom = false` e `id` com o prefixo `catalog_` presente no `ExerciseCatalog`), o nome no idioma da tela: inglês do `ExerciseCatalog` ou pt-BR do `ExerciseNames`. Isso vale também para os treinos já gravados nas telas novas (treino ativo, resumo, cartão de compartilhamento), porque o `exerciseId` é a identidade e o nome é rótulo. Exercício personalizado mostra sempre o nome digitado. As telas antigas (histórico e recordes) continuam com o nome gravado até a fase 5.
- **Pasta padrão:** a pasta de id `default` com o nome gravado igual a `RoutineFolder.DEFAULT_NAME` ("My routines") aparece como "Minhas rotinas" em pt-BR. Se a pessoa renomear, vale o nome dela. O valor gravado não muda.

**D12 (minha). O idioma do nome segue o das strings.** Um recurso de string do `:screens` (`catalog_names_language`, "en" em `values` e "pt-BR" em `values-pt-rBR`) diz qual tabela usar. Assim, o nome do exercício sai sempre no mesmo idioma que o resto da tela, inclusive na galeria e nos testes com `LocalAppLocale`.

- **Estilo dos nomes (minha):** vocabulário de academia brasileira, movimento + variação + equipamento, de preferência até 40 caracteres, únicos depois do `SearchFolding`. Anglicismos de uso corrente ficam ("leg press", "hack", "crossover", "face pull", "pullover"). Exemplos: Barbell bench press → Supino reto com barra; High-bar back squat → Agachamento livre (barra alta); Conventional barbell deadlift → Levantamento terra convencional; Wide-grip lat pulldown → Puxada aberta na polia; Close neutral-grip seated cable row → Remada baixa no triângulo; Rope triceps pushdown → Tríceps na polia com corda; Pec deck fly → Voador; Selectorized leg extension → Cadeira extensora; Selectorized lying leg curl → Mesa flexora; Selectorized seated leg curl → Cadeira flexora; 45-degree plate-loaded leg press → Leg press 45°; Dumbbell lateral raise → Elevação lateral com halteres; Standing barbell curl → Rosca direta com barra; Barbell hip thrust → Elevação pélvica com barra; Overhand pull-up → Barra fixa pronada; Push-up → Flexão de braço; Lying EZ-bar triceps extension → Tríceps testa com barra EZ.
- Testes em `commonTest`: toda entrada do catálogo tem nome em pt-BR, não vazio, até 60 caracteres; nomes únicos depois do `SearchFolding`; nenhuma chave órfã. O dono pode revisar a lista (pergunta 3), mas ela não espera por isso.

**D13 (controlador e minha). A busca encontra nos dois idiomas.**

- O `ExerciseSearch` passa a comparar, para cada exercício, os candidatos: nome gravado, nome do catálogo em inglês, nome em pt-BR, apelidos em inglês (os atuais) e **apelidos em pt-BR** novos (por exemplo: "supino", "voador", "peck deck", "crucifixo", "puxada", "pulley", "remada baixa", "terra", "stiff", "RDL", "cadeira extensora", "mesa flexora", "cadeira flexora", "agachamento", "afundo", "passada", "búlgaro", "panturrilha", "rosca direta", "rosca martelo", "rosca scott", "tríceps testa", "tríceps corda", "desenvolvimento", "elevação lateral", "encolhimento", "elevação pélvica", "barra fixa", "paralelas", "flexão", "graviton", "hack"), e o músculo e o equipamento nos dois idiomas ("peito", "costas", "ombro", "posterior", "glúteo", "halter", "barra", "polia", "cabo", "máquina", "peso corporal").
- A regra atual continua: todas as palavras da busca precisam descrever **um** mesmo candidato (nome ou apelido), em qualquer ordem; apelidos nunca criam identidade. Acentos e caixa saem pelo `SearchFolding`, então "eleva" acha "Elevação".
- **Desempenho:** o texto dobrado de cada candidato é calculado uma vez por lista de exercícios (fora da thread principal, ver "Desempenho"), não a cada tecla.
- Testes: uma tabela de consultas nos dois idiomas com os ids esperados (por exemplo, "supino inclinado halter" acha `dumbbell_bench_incline`; "lat pulldown" e "puxada" acham as puxadas; "mesa flexora" acha `selectorized_leg_curl_lying` e não a cadeira flexora; palavras de dois apelidos diferentes não se combinam num resultado), e os testes atuais do `ExerciseSearch` continuam passando.

## Telas da 4A

As telas ficam no `:screens`, em `com.mooscles.screens.library` e `com.mooscles.screens.exercise`, só com o `:designsystem`, como na fase 3. O `:app` só hospeda (rotas, `Context`).

### Biblioteca (aba)

- **Topo:** "Biblioteca" (`title.lg`), o atalho de Configurações (como as telas antigas) e "Criar exercício" (`Plus`, 48 dp).
- **Busca:** `MTextField` fixo sob o topo, com o ícone de busca e o botão de limpar; a lista rola por baixo. Placeholder "Buscar por nome, músculo ou equipamento".
- **Filtros:**
  - uma linha rolável de chips de grupo muscular (os 12 do `MuscleGroup`, na ordem do enum, traduzidos), seleção múltipla, com "OU" entre eles;
  - um chip "Equipamento" que abre uma sheet com os 9 equipamentos (seleção múltipla, "OU");
  - um chip alternável "Meus exercícios";
  - entre dimensões vale "E". Filtros ativos aparecem destacados, com a contagem no chip de equipamento ("Equipamento · 2"), e "Limpar filtros" aparece quando há algum.
  - O filtro de músculo usa o `MuscleGroup` do exercício (o principal, que todo exercício tem, inclusive os personalizados), não a tabela de anatomia. *Por quê (minha):* é o dado que existe para todos, e o filtro continua previsível.
- **Contagem:** "214 exercícios" (`caption`, anunciada de forma educada quando muda).
- **Lista:** `LazyColumn` com linhas de 72 dp no mínimo: mídia de 56 dp (figura parada, foto ou ícone do equipamento), nome no idioma da tela (`title.sm`, até 2 linhas), "Peito · Barra" e, nos personalizados, a etiqueta "Personalizado". Tocar abre o detalhe.
  - Sem busca nem filtro: "Usados recentemente" (até 8, dos últimos treinos, sem repetir) e depois "Todos", em ordem alfabética pelo nome mostrado (comparação pelo texto dobrado).
  - Com busca ou filtro: só os resultados, em ordem alfabética.
- **Estados:** carregando (`Skeleton` de linhas); sem resultado (`EmptyState` "Nenhum exercício encontrado", com "Limpar filtros" quando há filtro e "Criar “supino articulado”" com o texto da busca como nome); catálogo vazio não acontece (o catálogo é semeado), mas a tela mostra o `EmptyState` genérico se acontecer.
- **Estado de tela** (busca, filtros, rolagem) em `rememberSaveable`, que sobrevive a ir ao detalhe e voltar; não é gravado.

### Detalhe do exercício (rota nova `exerciseInfo/{id}`)

De cima para baixo:

- **Topo:** voltar; o nome (`title.md`, até 2 linhas); menu com "Editar exercício" (só personalizado) e "Ver histórico completo".
  - *Nota (2026-10-30, Task 17, correção 1):* o nome saiu da barra fixa e virou a primeira linha do conteúdo que rola (`title.md`, cabeçalho, inteiro em qualquer tamanho). A barra fica só com voltar e o menu (o `TopBar` do design system aceita título nulo). *Por quê:* um nome personalizado de 120 caracteres a 200% numa tela de 320 dp ocupava cerca de 600 dp de uma janela de 640 dp na barra fixa e deixava as ações fora de alcance; cortar em 2 linhas com reticências esconderia parte do nome. A intenção do "até 2 linhas" (a barra não cresce) continua.
- **Mídia:** moldura 1:1 com a largura útil, até 360 dp, centrada: a figura alternando (D5), a foto (personalizado, 4B) ou o placeholder do equipamento.
- **Metadados:** chips de leitura com o equipamento, o grupo muscular e a convenção de registro, e a legenda de uma linha da convenção quando não for "Carga total" (o mesmo texto do card do treino da fase 3).
- **Músculos:** o mapa corporal (D6) e o texto equivalente.
- **Como fazer:** as dicas numeradas (D8), quando houver.
- **Seu histórico:** um card com a última vez ("21 out · 80 kg × 8"), a melhor série ou o recorde (com o selo de recorde em latão) e quantas sessões; sem histórico, "Você ainda não fez este exercício." "Ver histórico completo" abre a tela antiga `exercise/{id}`; voltar retorna ao detalhe. Calculado na tela a partir do `UiState.history` e `UiState.records` já em memória, com uma função pura testada em `commonTest`.
- **Ações no fim:** "Adicionar à rotina" (secundário L), que abre uma sheet com as rotinas agrupadas por pasta; tocar adiciona e mostra o toast "Adicionado a Treino A" com "Abrir". Com um treino ativo, também "Adicionar ao treino" (secundário L), que usa `addWorkoutExercise`.
- **Estados:** exercício que não existe mais (por exemplo, depois de importar um backup) mostra o `EmptyState` "Exercício não encontrado" com "Voltar à biblioteca".
- Abre também pelo card do treino ativo: o menu do exercício ganha "Sobre o exercício" (o "Histórico do exercício" atual continua).

### Formulário de exercício personalizado

Uma tela cheia por cima da biblioteca (sheet de 100% da altura), com "Cancelar" e "Salvar":

- **Nome** (`MTextField`), até 120 caracteres (`EditRules.name`). Se o nome dobrado já existir na lista (em qualquer idioma), aparece o aviso não bloqueante "Já existe “Supino reto com barra”. Você pode salvar mesmo assim."
- **Grupo muscular:** os 12 chips, seleção única.
- **Equipamento:** os 9 chips, seleção única. Ao escolher, a convenção recebe um padrão (halter → Por mão; peso corporal → Carga adicional; o resto → Carga total), que a pessoa pode trocar.
- **Convenção de registro:** `RadioRow` com as 5 convenções, cada uma com a explicação de uma linha (traduzida pela tela a partir do enum, como na fase 3).
- **Editar** um personalizado: tudo pré-preenchido; se ele já tem séries no histórico e a convenção muda, aviso "As séries já registradas continuam com a convenção de quando foram feitas."
- Salvar com nome inválido não fecha; o campo mostra o erro.
- **Foto (4B):** a linha "Foto" com "Escolher da galeria", "Tirar foto" e, havendo foto, "Remover foto". Ver D17.
- O id de um exercício novo nasce na tela (`newId()`) e vai na ação nova `createCustomExercise(id, …)`, para a foto (4B) e a navegação ao detalhe saberem o id antes de o estado voltar. O `saveCustomExercise(id, …)` atual continua para editar.

### Seletor de exercício (treino e rotina)

O `ExercisePickerSheet` da fase 3 ganha a mesma busca e os mesmos filtros da biblioteca (a linha de chips de músculo e o chip de equipamento) e a mídia de 56 dp. No treino continua de seleção única; no editor de rotina (4B) ganha seleção múltipla (D15).

## Telas da 4B

Pacote `com.mooscles.screens.routines`.

### Treinar (aba)

- **Topo:** "Treinar" e o atalho de Configurações.
- **Pastas:** uma linha rolável de chips, uma por pasta, na ordem gravada, com a selecionada destacada; a escolha continua na preferência atual (`selected_routine_folder`). No fim da linha, "Pastas" abre o gerenciador.
- **Rotinas da pasta:** cards na ordem gravada. Cada card mostra o nome (`title.md`), "5 exercícios · 17 séries", até três grupos musculares (os mais frequentes), "Última vez: há 3 dias" (do treino finalizado mais recente com `sourceRoutineId` igual; "Nunca feita" sem nenhum), uma faixa com as figuras paradas dos 4 primeiros exercícios, "Começar" (primário, 56 dp) e o menu (Editar, Renomear, Duplicar, Mover para pasta, Excluir).
- **Reordenar rotinas:** arrastar pela alça do card, dentro da pasta (D15). Ações de acessibilidade "Mover para cima", "Mover para baixo", "Mover para o topo", "Mover para o fim".
- **Fim da lista:** "Nova rotina" (secundário L) e "Treino vazio" (texto).
- **Estados:** pasta vazia (`EmptyState` "Nenhuma rotina nesta pasta" com "Nova rotina"); carregando (`Skeleton` de cards).
- **Começar com um treino já ativo:** `MDialog` "Já existe um treino em andamento" com "Retomar treino" e "Cancelar", como hoje. O "Começar" continua passando pelo decorador de skeleton da fase 3.
- **Excluir rotina:** `MDialog` destrutivo "Excluir Treino A? Os treinos já feitos com ela continuam no histórico."
- **Nova rotina:** uma sheet com o campo de nome pré-preenchido com "Nova rotina" (selecionado, teclado aberto) e "Criar"; cria na pasta selecionada e abre o editor. O id nasce na tela (`createRoutine(name, folderId, id)`).

### Gerenciar pastas

`MBottomSheet` com as pastas na ordem, cada linha com a alça de arrastar, o nome, a contagem de rotinas e o menu (Renomear, Excluir). "Nova pasta" no fim.

- **Excluir** (não existe na pasta padrão): `MDialog` "Excluir a pasta Hipertrofia? As 3 rotinas dela vão para Minhas rotinas." Nenhuma rotina é apagada. Se a pasta excluída era a selecionada, a seleção volta para a padrão.
- **Renomear:** sheet com o campo e "Salvar"; a padrão também pode ser renomeada.
- **Reordenar:** arrastar, com as mesmas ações de acessibilidade.

### Editor de rotina (rota `routine/{id}`, a mesma de hoje)

- **Topo:** voltar; o nome da rotina (tocar abre a sheet de renomear); menu com Renomear, Duplicar, Mover para pasta e Excluir rotina.
- **Lista** (`LazyColumn`, granularidade por série como no treino ativo): um card por exercício, na forma segmentada do `ExerciseCard`:
  - **cabeçalho:** alça de arrastar (48 dp), mídia de 56 dp, nome, "Peito · Barra" e o menu (Trocar exercício, Sobre o exercício, Mover para cima, Mover para baixo, Mover para o topo, Remover da rotina);
  - **linha de apoio:** o chip "Descanso 2:00", que abre a mesma sheet de descanso da fase 3 (passos de 15 s, 0:00 a 5:00, `updateRoutineExerciseRest`), e a linha de resumo "Alvo 3×8 · 80 kg" com as regras do D16;
  - **séries planejadas**, uma linha cada: o rótulo (número, ou "A"/"W" no aquecimento), a carga planejada e as repetições planejadas em `NumericField`, e, quando a série segue o recorde (`inheritPr`), o ícone de recorde pequeno com o nome falado "segue o recorde". Vazio é um valor válido: carga vazia ("sem carga" ou peso corporal, conforme a convenção) e repetições vazias;
  - **editar uma série:** o mesmo padrão do D12 da fase 3: o toque põe a linha em edição e mostra o `Stepper` do campo (2,5 kg ou 5 lb; 1 repetição), o segundo toque abre o teclado. A série editada passa a manual (`inheritPr = false`), como hoje;
  - **menu da série** (toque no rótulo): "Tipo da série" (Normal, Aquecimento), "Seguir o recorde" (volta a `inheritPr`, `inheritRoutineSet`), "Remover série";
    - *Nota (2026-10-31, pedido do dono):* o menu ganha "Copiar para todas as séries", só numa série de trabalho cujo exercício tem outra série de trabalho. Ele copia a carga e as repetições dessa série (nula continua nula: sem carga ou peso corporal) para todas as outras séries de trabalho da mesma entrada, que passam a manuais (`inheritPr = false`), como numa edição; os aquecimentos e a própria série ficam como estão. Uma série de origem que segue o recorde copia os valores que mostra (os do `RoutineSetUi`), e as cópias ficam manuais. É um comando só, `copyRoutineSetToAll(sourceSetId)` no repositório, numa `writeTransaction` sem `catch` dentro, exposto como a ação `copyRoutineSetToAll(entryId, sourceSetId)`, que passa pelo `act` com o id da entrada como assunto. Depois vem o toast "Copiado para 2 séries" (plural por `pluralCount`) com "Desfazer"; o desfazer manda uma ação só, `restoreRoutineSetTargets(entryId, previous)`, também atômica, com cada série alterada como estava (repetições, carga e modo; a herdada volta a seguir o recorde atual, como no `restoreRoutineSet`). O snapshot é o do `PlannedSetDrafts.snapshot` (com as escritas em voo), e os rascunhos das séries alteradas saem na cópia e no desfazer, para nenhum rascunho velho passar por cima do valor copiado. Se a entrada mudou enquanto o toast estava aberto (série removida ou adicionada, uma que virou aquecimento, ou o exercício trocado), o desfazer não manda nada e mostra "A série não pôde voltar." (`SET_NOT_RESTORED`); o repositório também recusa com `RESTORE_UNAVAILABLE` uma série que não é mais uma série do mesmo tipo da entrada, ou a entrada que sumiu. Sem mudança de schema nem de DataStore. *Por quê:* montar 4 séries iguais era editar cada uma; o "Adicionar série" já copia a última, e faltava igualar as que já existem.
      - *Nota (2026-10-31, revisão da cópia):* o desfazer nunca apaga em silêncio uma edição feita depois da cópia. Assim que uma série alterada pela cópia é editada (`updateRoutineSet`) ou volta a seguir o recorde, a oferta sai e o toast fecha; editar a própria série de origem não tira a oferta, porque o desfazer não mexe nela. Trocar o exercício também retira a oferta, como no desfazer da remoção, e o repositório recusa o `restoreRoutineSetTargets(entryId, exerciseId, previous)` de uma entrada que passou a ter outro exercício. Um segundo toque em "Copiar para todas as séries" antes de a folha sair não faz nada. *Por quê:* com TalkBack ou acesso por interruptor o toast com ação fica até ser fechado, e o desfazer voltaria a série ao valor de antes da cópia por cima da edição.
  - **rodapé:** "Adicionar série" (tonal L).
- **No fim da lista:** "Adicionar exercícios" (secundário L), que abre o seletor em seleção múltipla.
- **Fixo embaixo:** "Começar treino" (primário L).
- **Gravação:** cada mudança grava na hora, como hoje; não há "Salvar".
- **Remover série:** apaga e mostra o toast "Série 3 removida" com "Desfazer" (6 s ou o tempo da acessibilidade); o desfazer chama a ação nova `restoreRoutineSet` com o snapshot (posição e valores). Uma rotina precisa de pelo menos uma série por exercício (regra atual): na última, "Remover série" fica desabilitado com a explicação.
- **Remover exercício:** `MDialog` "Remover supino reto da rotina? As 4 séries planejadas saem." (sem desfazer, como no treino).
- **Trocar exercício:** o seletor de seleção única; a troca zera os alvos, como o `replaceRoutineExercise` já faz, então pede confirmação quando há alvo manual.
- **Estados:** rotina vazia (`EmptyState` "Rotina sem exercícios" com "Adicionar exercícios"); rotina que não existe mais (excluída noutra tela) volta para Treinar.

**D14 (minha). A série planejada pode ser de aquecimento.** Coluna nova `routine_sets.warmup` (ver "Dados e compatibilidade"). Marcar como aquecimento deixa a série manual (`inheritPr = false`), porque herdar o recorde num aquecimento não faz sentido; a sincronização de alvos herdados ignora aquecimentos. Ao começar o treino, a série nasce com `warmup = true`. *Por quê:* é a parte da "prescrição por série" que falta nos dados; o aquecimento já existe no treino e nos recordes, então nenhuma regra nova de domínio aparece.

- **Adicionar série (minha, muda o comportamento atual):** a série nova copia a última série do exercício (valores, tipo e `inheritPr`). Hoje ela sempre herda o recorde. *Por quê:* é o que o treino ativo faz (`addSet` copia a última) e o que a pessoa espera ao montar 4 séries iguais.

**D15 (minha). Arrastar e soltar próprio, no `:designsystem`, sem biblioteca.** Um modificador de reordenação para `LazyColumn` (`detectDragGesturesAfterLongPress` na alça, deslocamento do item, rolagem automática perto das bordas), usado no editor, nas rotinas e nas pastas.

- Erguer: elevação do token de arraste e escala 1,02 (só a elevação com movimento reduzido); háptico de passo a cada posição cruzada; os vizinhos deslizam com `spring.snappy` (instantâneo com movimento reduzido).
- **Grava uma vez, ao soltar:** `reorderRoutineExercises(routineId, ids)` (que já existe no repositório), `reorderRoutines(ids)` (já existe; o VM monta a ordem completa trocando só as da pasta) ou a nova `reorderRoutineFolders(ids)`. Voltar ou cancelar o gesto devolve o item ao lugar.
- **Alternativas (WCAG 2.5.7):** as ações de acessibilidade "Mover para cima", "Mover para baixo", "Mover para o topo" e "Mover para o fim" em cada item, e os mesmos itens no menu. Depois de mover, o anúncio "Supino reto movido para a posição 1 de 5".
- *Por quê:* a P3 não achou biblioteca madura de reordenação para Compose Multiplatform com acessibilidade; o comportamento precisa das ações de acessibilidade de qualquer jeito; e cada biblioteca entra inteira no APK debug. O spike S2 prova no Android e no simulador iOS.
- **Seleção múltipla no seletor (minha):** no editor, o seletor mostra uma caixa em cada linha e, embaixo, "Adicionar 5 exercícios"; a ação nova `addRoutineExercises(routineId, ids)` grava tudo numa transação, na ordem em que foram tocados.

### Linha de alvo no card do treino

**D16 (minha). "Alvo 3×8 · 80 kg" sai da rotina de origem, calculado na apresentação, sem coluna nova.**

- `WorkoutExerciseUi.target: RoutineTargetUi?` é preenchido pelo `WorkoutPresentation` quando o treino tem `sourceRoutineId`, a rotina ainda existe e tem o mesmo exercício: o n-ésimo `workout_exercise` com um `exerciseId` casa com a n-ésima ocorrência dele na rotina (ordem de posição). Exercício adicionado ou trocado no treino não tem alvo. A rotina apagada some com a linha.
- **Formato** (só as séries de trabalho; aquecimentos contados à parte):
  - todas iguais: "Alvo 3×8 · 80 kg";
  - mesmas repetições, cargas diferentes: "Alvo 3×8 · 70–80 kg";
  - repetições diferentes: "Alvo 3 séries · 6–10 reps · 70–80 kg";
  - carga vazia em todas: sem a parte da carga ("Alvo 3×12"); repetições vazias em todas: "Alvo 3 séries · 80 kg";
  - com aquecimento: "+ 2 aquec." no fim;
  - carga na unidade escolhida (lb converte do kg exato), números no formato do idioma.
- Onde aparece: no card do treino, sob o metadado, em `caption` com os números em `num.sm`; e no editor de rotina, com as mesmas regras (uma função de formatação só, em `commonTest` nos dois idiomas).
- **Chamada do controlador (2026-10-29):** a linha é calculada ao vivo da rotina de origem; editar a rotina durante um treino ativo atualiza a linha na hora. É comportamento aceito: o alvo é o plano atual, e as séries registradas não são tocadas. Sem snapshot e sem coluna nova.
- *Por quê sem coluna:* o alvo é um rótulo do plano atual, e uma coluna de alvo em `workout_sets` mexeria no histórico e no backup por um rótulo de tela. (A frase anterior, "a rotina não muda no meio do treino", estava errada: a rotina pode ser editada durante o treino; só a sincronização de alvos herdados roda apenas no `initialize`, no import e no `finishWorkout`.)

### Foto do exercício personalizado

**D17 (controlador e minha). Foto da galeria ou da câmera, guardada no armazenamento privado, redimensionada.**

- **Android:** "Escolher da galeria" usa o seletor de fotos do sistema (`PickVisualMedia`, sem permissão de armazenamento); "Tirar foto" usa `TakePicture` gravando num arquivo temporário do cache exposto pelo `FileProvider` existente (caminho novo no XML de caminhos), **sem declarar a permissão de câmera**. O manifesto ganha só a consulta (`<queries>`) da ação de captura de imagem, para saber se há um app de câmera; sem app, a opção some.
- **Processamento:** fora da thread principal; decodifica com amostragem, aplica a orientação EXIF (o `ImageDecoder` faz isso a partir da API 28; nas APIs 26 e 27, o `androidx.exifinterface`, de primeira parte, com o tamanho contado no orçamento), redimensiona o lado maior para 1080 px e grava JPEG com qualidade 85 (cerca de 150 a 250 KB); grava também uma miniatura quadrada de 192 px.
- **Arquivos:** `files/exercise_photos/<exerciseId>.jpg` e `<exerciseId>.thumb.jpg`, gravados num temporário e renomeados (trocar a foto nunca deixa meia foto). Sem coluna no banco: a foto existe se o arquivo existe.
- **Exercício novo:** a foto escolhida fica num temporário até o exercício ser salvo; ao aparecer no estado com o id gerado pela tela, a foto é movida para o lugar. Cancelar apaga o temporário.
- **Limpeza:** na partida, depois do `initialize()`, fotos cujo id não é de um exercício personalizado existente são apagadas (por exemplo, depois de importar um backup que não tem aquele exercício).
- **Exibição:** a interface `ExercisePhotoStore` (comum) entrega os bytes; a tela decodifica com `decodeToImageBitmap` fora da thread principal, com cache em memória por id e versão (data de modificação do arquivo). A lista usa a miniatura; o detalhe, a foto.
- **iOS:** a interface fica no `commonMain`; a implementação (PHPicker, câmera, arquivos) entra com o `iosApp` na fase 7. Nos testes do simulador, uma implementação falsa em memória.
- **Erros:** foto que não decodifica ou disco cheio: toast "Não foi possível salvar a foto." e o exercício continua salvo sem ela.

**D18 (controlador e minha). A foto não entra no backup nesta versão.** O formato do backup é um contrato e é JSON de texto; uma foto em base64 multiplicaria o arquivo e o tempo do import. A exportação passa a dizer, na confirmação, "As fotos dos exercícios personalizados não entram no arquivo." Depois de importar num aparelho novo, o exercício aparece com o ícone do equipamento. *Nota datada (2026-10-29):* incluir fotos é trabalho de uma versão nova do formato (arquivo compactado com o JSON e as fotos, ou um campo opcional), a decidir com o backup na nuvem da fase 8.

## Arquitetura

- **Módulos:** as telas no `:screens` (pacotes `library`, `exercise`, `exercise.figures`, `routines`); renderizador de figura, mapa corporal, reordenação e componentes novos no `:designsystem`; `ExerciseAnatomy`, `MuscleRegion`, `ExerciseNames`, a busca, as ações e as regras de dados no `:shared`; rotas, foto (Android) e hospedagem no `:app`. Nenhum arquivo do `:screens` nos pacotes `com.mooscles` ou `com.mooscles.ui` (regra das fachadas).
- **Rotas no `:app`:** uma nova, `exerciseInfo/{id}` (detalhe). As rotas das abas (`ShellTabs.LIBRARY` na 4A, `ShellTabs.TRAIN` na 4B) e `routine/{id}` (4B) passam a chamar as telas novas. A tela antiga `exercise/{id}` (histórico do exercício) continua.
- **Contrato de UI (`UiContract.kt`, aditivo, só em memória):**

  | Acréscimo | Para quê |
  | --- | --- |
  | `RoutineSetUi.warmup: Boolean` | rótulo e tipo da série planejada (4B) |
  | `RoutineUi.lastFinishedAt: Long?` | "Última vez: há 3 dias" (4B) |
  | `WorkoutExerciseUi.target: RoutineTargetUi?` (séries de trabalho com reps e kg, número de aquecimentos) | linha de alvo (4B) |
  | `MoosclesActions.createCustomExercise(id, name, muscle, equipment, convention)` | id nascido na tela (4A) |
  | `MoosclesActions.createRoutine(name, folderId, id)` | id nascido na tela (4B); a forma atual continua |
  | `MoosclesActions.addRoutineExercises(routineId, exerciseIds)` | seleção múltipla (4B) |
  | `MoosclesActions.reorderRoutineExercises(routineId, ids)`, `reorderRoutines(folderId, ids)`, `reorderRoutineFolders(ids)` | arrastar e soltar (4B) |
  | `MoosclesActions.deleteRoutineFolder(id)` | excluir pasta (4B) |
  | `MoosclesActions.setRoutineSetWarmup(setId, warmup)`, `restoreRoutineSet(entryId, position, reps, weightKg, warmup, inheritPr)` | aquecimento e desfazer (4B) |

  Classes novas usadas como parâmetro de composable entram no `app/compose-stability.conf` só se forem imutáveis.
- **Repositório (todas em `writeTransaction`, sem `catch` dentro):** `addRoutineExercises`, `deleteRoutineFolder` (move as rotinas para a padrão, nunca apaga rotina; recusa a padrão), `reorderRoutineFolders`, `setRoutineSetWarmup`, `restoreRoutineSet` (exige a entrada existente, menos de 50 séries, valida com `EditRules`), `createRoutine` com id opcional, o `startRoutine` copiando o `warmup`, o `synchronizeRoutineTargets` ignorando aquecimentos e o `addRoutineSet` copiando a última série.
- **Dados das telas:** o resumo do histórico do detalhe, as rotinas "Usados recentemente" e "Última vez" saem de funções puras no `:screens` ou no `WorkoutPresentation` (o plano escolhe), sempre a partir do que o estado já tem, sem consulta nova ao banco no caminho de uma tela.

## Acessibilidade, fonte grande e movimento

- Todo clicável com rótulo e alvo de 48 dp (56 dp em "Começar" e "Começar treino"); a varredura de acessibilidade da fase 3 (`WorkoutAccessibilitySweepTest`) ganha as telas novas: biblioteca, detalhe, formulário, Treinar, pastas e editor, nos dois temas, a 1,0, 1,3 e 2,0 (mais 2,01 no Android), a 320 e 412 dp, em três posições de rolagem.
- Ordem de leitura: topo, busca, filtros, contagem, lista. No detalhe: nome, mídia, metadados, músculos (o texto, não o mapa), dicas, histórico, ações.
- Linhas da lista: um nó só ("Supino reto com barra, Peito, Barra, personalizado"). Cards de rotina: o nome e o resumo num nó; "Começar" e o menu como botões próprios.
- Arrastar sempre com as ações de acessibilidade e o menu (D15). A alça tem o rótulo "Reordenar Supino reto"; no TalkBack, a ação de toque duplo e segurar não é exigida.
- Chips de filtro: `Role.Checkbox` com estado; a sheet de equipamento prende o foco e devolve ao chip.
- A figura: imagem com rótulo (D5); a alternância automática não é anunciada; com movimento reduzido, o toque que alterna tem rótulo "Mostrar o início do movimento" / "Mostrar o fim".
- **Fonte grande:** a 200%, as linhas da lista crescem em altura e o nome quebra em até 3 linhas (texto, não número); os chips de músculo continuam roláveis; o editor põe carga e repetições em duas linhas como o `SetRow` da fase 3; nenhum número cortado (`assertShownWhole`).
- **Movimento reduzido:** a figura para no quadro B; o arraste sem escala; os vizinhos se reposicionam sem mola; nenhum outro movimento novo.
- **#11 da fase 2** (o `MTextField` lê o valor antes do rótulo) continua aberto: o campo de busca usa o `MTextField` e herda o problema até a sessão com TalkBack num aparelho.

## Textos e idiomas

- Strings novas no `:screens` (`strings_library.xml`, `strings_exercise.xml`, `strings_routines.xml`, `strings_anatomy.xml` com as 17 regiões) em `values/` (inglês) e `values-pt-rBR/`, com plurais por recurso. Componentes novos (figura, mapa, reordenação) no `strings_components.xml` do `:designsystem`. O `StringsParityTest` dos dois módulos cobre tudo.
- Nomes do catálogo: `ExerciseNames` (D11); dicas: arquivos JSON (D8).
- A frase da exportação sobre fotos (D18) é a única mudança de texto em `backup/`; ela fica em recurso do `:designsystem` na tela de Configurações, que já é traduzida, não na mensagem pronta do `BackupUi`.

Amostras (voz da fase 2):

| Situação | pt-BR | en |
| --- | --- | --- |
| Contagem | 214 exercícios | 214 exercises |
| Busca vazia | Nenhum exercício encontrado | No exercises found |
| Criar pela busca | Criar “supino articulado” | Create “hammer strength press” |
| Músculos | Principais: quadríceps. Secundários: glúteos, posteriores da coxa. | Primary: quadriceps. Secondary: glutes, hamstrings. |
| Histórico | Você ainda não fez este exercício. | You haven’t done this exercise yet. |
| Adicionado | Adicionado a Treino A · Abrir | Added to Workout A · Open |
| Alvo | Alvo 3×8 · 80 kg + 2 aquec. | Target 3×8 · 80 kg + 2 warm-up |
| Última vez | Última vez: há 3 dias | Last done: 3 days ago |
| Excluir pasta | Excluir a pasta Hipertrofia? As 3 rotinas dela vão para Minhas rotinas. | Delete the Hypertrophy folder? Its 3 routines move to My routines. |
| Movido | Supino reto movido para a posição 1 de 5 | Bench press moved to position 1 of 5 |
| Foto no backup | As fotos dos exercícios personalizados não entram no arquivo. | Photos of custom exercises are not included in the file. |

## Dados e compatibilidade

**D19 (minha). Uma única mudança de schema na fase, na 4B: v5 → v6, aditiva.** Cada candidato que o controlador listou foi avaliado:

| Candidato | Precisa de schema? | Decisão |
| --- | --- | --- |
| Pastas | não | `routine_folders` existe desde a v5 (com `position`); excluir e reordenar são só código |
| Prescrição por série | **sim, uma coluna** | `routine_sets` existe desde a v3 com carga, repetições e `inheritPr`; falta o aquecimento: `ALTER TABLE routine_sets ADD COLUMN warmup INTEGER NOT NULL DEFAULT 0`, com `@ColumnInfo(defaultValue = "0")` na entidade |
| Foto do personalizado | não | arquivo por id no armazenamento privado (D17) |
| Id do catálogo e chave de mídia | não | o `id` do catálogo (`catalog_<stableId>`) já é estável e explícito; figura, anatomia, nome e dicas são tabelas de código chaveadas por ele |
| Alvo no card do treino | não | calculado da rotina (D16) |

- **Migração** `MIGRATION_5_6` registrada em `buildMooscles()`; `version = 6`; o KSP gera `shared/schemas/com.mooscles.data.WorkoutDatabase/6.json` (nunca editado à mão; o hook bloqueia).
- **`onCreate` e migração chegam ao mesmo estado:** a coluna vem da entidade numa instalação nova e do `ALTER` numa atualização; o teste compara o `sqlite_master` normalizado das duas.
- **Testes:** a migração nova entra nas listas montadas à mão de `RoutineTargetMigrationTest`, `RoutineFolderPersistenceTest` e `RollbackV142DataCompatibilityTest`; teste próprio que cria um banco v5 a partir do `5.json` com rotinas, séries planejadas (herdadas e manuais), pastas e treinos, migra e confere que tudo continua igual e que toda série planejada tem `warmup = 0`; o mesmo teste em `commonTest` roda no simulador iOS com o `BundledSQLiteDriver`. O `MigrationEquivalenceTest` (dumps da 1.4.5) não muda: os dumps de referência nunca são regenerados.
- **Backup:** `RoutineSetRow` ganha `warmup: Boolean = false`, opcional. Pela regra do `BackupFormat.kt`, campo opcional com padrão não sobe o `FORMAT_VERSION`. Um app antigo que importar um arquivo novo ignora o campo e as séries de aquecimento planejadas viram séries normais (registrado; nenhum treino feito muda). Um arquivo antigo importado no app novo vira `warmup = false`. O `ImportPlanner` e o teste de ida e volta cobrem os dois sentidos.
- **DataStore:** nenhuma chave nova nem renomeada na 4A e na 4B. A pasta selecionada continua em `selected_routine_folder`. A chave `media_auto_download` só existe se a 4M for construída.
- **Arquivos novos:** `files/exercise_photos/` (4B). O `allowBackup=false` continua.
- **Android:** `<queries>` para a captura de imagem e um caminho novo no XML do `FileProvider` (4B), os dois aditivos; nenhuma permissão nova; `applicationId`, assinatura, `minSdk`, `versionCode`, `update.json`, `ReleaseUrls.REPOSITORY` e a descoberta de atualizações não mudam.
- **Rede:** nenhuma requisição nova na 4A e na 4B.
- **Histórico:** continua um snapshot. Nada desta fase muda treinos finalizados; os nomes traduzidos são só exibição.
- **Notas do PR final:** o item 5 de "Antes de abrir o PR" da [v1.5.0](../releases/v1.5.0.md) espera o Room na `version` 5; com a 4B, passa a esperar a 6 com o `6.json` novo e a migração aditiva. A 4B atualiza esse texto.
- **`compat-reviewer`** antes do merge da 4A (toca `shared/.../data/` com `ExerciseNames`, `ExerciseAnatomy`, `ExerciseSearch` e uma ação nova no repositório) e da 4B (schema v6, migração, `WorkoutRepository`, backup, manifesto, `FileProvider`, `build.gradle.kts` se o `exifinterface` entrar).

## Desempenho

Base: [PERFORMANCE.md](../../../PERFORMANCE.md), as medidas da [fase 1B](../reports/2026-10-27-phase1b-verificacao.md) e da [fase 3](../reports/2026-10-29-phase3-verificacao.md), com a base grande (208 treinos, cerca de 12,7 mil séries) e mais 300 exercícios personalizados semeados para o teste da biblioteca.

| Medida | Orçamento |
| --- | --- |
| Tocar na aba Biblioteca até a lista | o próximo quadro; o índice de busca (texto dobrado de todos os candidatos) é montado fora da thread principal e reaproveitado enquanto a lista de exercícios não muda |
| Uma tecla na busca, catálogo + 300 personalizados | filtrar e ordenar em até 4 ms na mediana e 8 ms no p95 no emulador; no host, um teste de tempo com folga larga e mediana de várias rodadas (o `WorkoutCalculationsBatchTimingTest` já mostrou que limite de relógio apertado é instável no CI) |
| Rolagem rápida da biblioteca inteira com figuras | quadros lentos (`gfxinfo` janky) até 5% no emulador de referência, no build R8 (`assemblePerformance`) |
| Desenhar uma figura | sem bitmap intermediário; a geometria só é refeita quando o tamanho ou a pose mudam (`drawWithCache`) |
| Abrir o detalhe | o próximo quadro com a figura e os metadados; as dicas carregam de forma assíncrona (uma vez por processo) |
| Alternância da figura | só a figura recompõe a cada troca, provado por contador de recomposição |
| Arrastar no editor, 10 exercícios × 5 séries | 60 fps no emulador; uma gravação só, ao soltar |
| `present()` depois de reordenar ou editar uma série planejada | sem piora contra a 1B; a linha de alvo usa o que a apresentação já tem da rotina |
| Treino ativo | sem piora contra a fase 3 em nenhuma das medidas dela (a linha de alvo não entra no caminho do check) |
| APK debug | ver D20 |

**D20 (minha). Orçamento de APK da fase: até +1 MiB sobre a `main` em `4f41792`, somando 4A e 4B.** O `tools/apk-budget.json` passa a ter como base o APK debug do CI verde da `main` no começo da 4A; a 4A mira até 0,6 MiB e a 4B o resto. As figuras e o mapa ficam fora do pacote `icons/`, então não entram no teto "fontes e ícones" (que tem 161 KiB de folga), mas entram no total. *Por quê:* é a regra das fases anteriores; a fase não traz biblioteca de terceiros (nada de Coil, Lottie, reordenação), e a mídia embutida é código e dados pequenos. As miniaturas raster medidas (734 a 943 KiB) é que não caberiam.

## Orçamento de toques

Contagens "hoje" estimadas pela leitura do código; o plano mede na `main` com o mesmo roteiro antes de trocar a tela, e um `TapBudgetTest` (como o da fase 3) fixa os números novos.

| Fluxo | Hoje | Novo |
| --- | --- | --- |
| Montar uma rotina nova com 5 exercícios (da aba Treinar até o editor com os 5) | cerca de 12 toques + digitar o nome (criar, nome, confirmar, e 5 × "Add exercise" + escolher) | até 9, sem digitar: Nova rotina, Criar (nome padrão), Adicionar exercícios, 5 toques, Adicionar 5 |
| Achar um exercício por músculo e abrir | 4 (aba, abrir o filtro, escolher, tocar) | 3 (aba, chip do músculo, tocar) |
| Reordenar: o 5º exercício para o 1º lugar | 8 (4 × menu + "Move up") | 1 gesto (arrastar) ou 2 (menu, "Mover para o topo") |
| Criar exercício personalizado com nome, músculo e equipamento | cerca de 8 + digitar (3 menus suspensos) | 4 + digitar (criar, chip de músculo, chip de equipamento, salvar; convenção com padrão) |
| Adicionar uma série planejada igual à de cima | 1 (mas herda o recorde) | 1 (copia a de cima) |
| Marcar uma série planejada como aquecimento | não existe | 2 (rótulo, "Aquecimento") |
| Começar o treino de uma rotina | 1 | 1 |

Nenhum fluxo novo pode passar do de hoje.

## Spikes, antes de comprometer

1. **S1, figuras (4A, o primeiro).** O renderizador com o esqueleto, as primitivas de equipamento e as 20 poses do piloto; a página "Figuras" da galeria; a folha de capturas. Aprova pelo portão G1 (D4). Mede de passagem o tamanho no APK por pose. Se reprovar, a 4A segue sem figuras e a pergunta 1 volta ao dono com a alternativa de encomenda (4M).
2. **S2, arrastar e soltar (antes do editor).** O modificador de reordenação numa `LazyColumn` com itens de alturas diferentes, rolagem automática e as ações de acessibilidade, no emulador e no simulador iOS (onde o arraste é injetado por `performTouchInput`; no S3 da fase 3, o Skiko não entregou o cancelamento de toque). Aprova se reordena sem saltos, a rolagem da lista não briga com o gesto, grava uma vez e as ações funcionam nos dois. Se reprovar no iOS, o arraste fica só no Android e o iOS usa as ações e o menu, registrado.
3. **S3, mapa corporal.** Começa pelo spike de meio dia da D6 (render no nosso estilo a 1,0 e 2,0, tamanho em dex, `PathParser` ou `ImageVector`). As duas silhuetas com as 17 regiões, a conversão do SVG e o contraste não textual dos pares novos nos dois temas. Aprova com os pares no `ComponentColorsTest` e uma captura sem região ambígua.
4. **S4, foto no Android (antes da foto na 4B).** `PickVisualMedia` e `TakePicture` sem permissão de câmera nas APIs 26, 28 e 35 do emulador; orientação EXIF; memória ao decodificar uma foto de 12 MP; o arquivo temporário. Aprova se a foto sai em pé e abaixo de 300 KB, sem `OutOfMemoryError`, e sem permissão nova no manifesto.

## Como verificar

- **Testes de host e `commonTest`:**
  - `ExerciseNames` (completo, único, sem órfão), `ExerciseAnatomy` (completo, coerente com o `MuscleGroup`), as poses (todas as juntas e o equipamento dentro da caixa com margem; os pés no chão quando a pose diz que estão; o mesmo equipamento nos dois quadros; nenhuma chave órfã; o piloto completo), a paridade das dicas;
  - a busca nos dois idiomas (tabela de consultas) e os testes atuais do `ExerciseSearch`;
  - o formato da linha de alvo nos dois idiomas, em kg e lb, com aquecimento, cargas e repetições variadas;
  - editar a rotina de origem no meio do treino atualiza a linha de alvo e nunca altera as séries registradas (D16);
  - o resumo do histórico do detalhe, "Usados recentemente" e "Última vez";
  - o repositório: `addRoutineExercises`, `deleteRoutineFolder` (rotinas movidas, padrão recusada, seleção), `reorderRoutineFolders`, `setRoutineSetWarmup` (vira manual), `restoreRoutineSet`, `addRoutineSet` copiando a última, `startRoutine` com aquecimento, a sincronização ignorando aquecimentos;
  - a migração v5 → v6 (ver "Dados"), nas três listas legadas e no simulador iOS;
  - o backup: ida e volta com `warmup`, arquivo antigo sem o campo, e um JSON novo lido pelo `BackupCodec` com o campo desconhecido ignorado (o comportamento de um app antigo);
  - a foto (Robolectric): redimensionar, orientação, troca atômica, limpeza de órfãs, temporário apagado ao cancelar;
  - `StringsParityTest` do `:screens` e do `:designsystem`.
- **`uiTest` no emulador e no simulador iOS:** biblioteca (busca, chips, sheet de equipamento, vazio, criar pela busca), detalhe (figura alternando, movimento reduzido, placeholder, foto, sem dicas, exercício que sumiu), formulário (validação, aviso de duplicado, padrão da convenção), seletor em seleção única e múltipla, Treinar (pastas, cards, começar, treino ativo), pastas (excluir, renomear, reordenar), editor (editar série com stepper, aquecimento, seguir o recorde, remover e desfazer, arrastar por toque injetado e pelas ações), linha de alvo no card do treino; contador de recomposição na alternância; a varredura de acessibilidade com as telas novas. Um teste de ponta a ponta no simulador, como o `ActiveWorkoutFlowTest`: buscar em pt-BR, abrir o detalhe, adicionar à rotina, montar a rotina com seleção múltipla, reordenar, marcar um aquecimento, começar o treino e ver a linha de alvo, sobre o ViewModel, o Room e o DataStore reais, com capturas como artefato do job `ios`.
- **Orçamento de toques:** o `TapBudgetTest` contra a contagem medida na `main`.
- **Atualização por cima no emulador** (`Mooscles_Pixel8_API35`, rede desligada), para cada etapa: com o APK da `main` de depois da fase 3 (`2359ab6`; a `4f41792` só acrescenta documentação), em pt-BR e em libras, criar duas pastas, três rotinas (uma com séries manuais, uma herdando o recorde), um exercício personalizado, um treino ativo a partir de uma rotina com séries concluídas e o descanso correndo; exportar; instalar o APK da etapa por cima. Conferir: tudo igual nas telas novas, o banco na v6 (só na 4B) com `warmup = 0` em todas as séries planejadas, a linha de alvo no card, o `local_preferences.preferences_pb` com o mesmo MD5, e o export depois igual ao de antes, tirando `exportedAt` e o campo `warmup: false` novo em cada série planejada (a diferença esperada, conferida campo a campo).
- **Sem rede:** com a rede do emulador desligada desde a instalação nova, a biblioteca, o detalhe (figura, mapa, dicas), o formulário, Treinar, as pastas e o editor funcionam inteiros. Uma revisão do diff confirma que não há código de rede novo (sem a 4M).
- **Se a 4M for construída:** um transporte falso que serve um arquivo corrompido e um com tamanho errado (rejeitados, apagados, nunca mostrados, "Tentar de novo"), sem rede depois do cache, a Economia de dados, a preferência desligada, a pasta de outra tag apagada, e nenhuma requisição a `api.github.com`.
- **Desempenho:** as medidas da tabela no build R8, pelo procedimento do PERFORMANCE.md.
- **Capturas e crítica:** capturas do emulador da biblioteca (com e sem filtro, vazia), do detalhe (com figura, com foto, sem figura), do formulário, de Treinar, das pastas e do editor (com aquecimento, arrastando), nos dois temas, em en e pt-BR e a 200%; a folha de figuras de cada lote; `critique-screen` sobre elas, com os achados corrigidos ou adiados com motivo, em `reports/AAAA-MM-DD-phase4a-critica-biblioteca.md` e `reports/AAAA-MM-DD-phase4b-critica-rotinas.md`.
- **Aparelho e TalkBack:** pelo build `preview` da fase 3, se o dono topar (pergunta 4): leitura da linha da biblioteca, do mapa e do texto equivalente, das ações de reordenação, a câmera de verdade, o seletor de fotos do One UI, 60 fps na rolagem e no arraste a 120 Hz.
- **Tokens:** `tools/check-design-tokens.ps1` limpo no `:screens` e no `:designsystem` (com `illustration/`).
- **Tamanho:** `tools/check-apk-budget.ps1` dentro do D20, no build limpo do CI.
- **CI:** os mínimos (`MIN_HOST_TESTS`, `MIN_IOS_TESTS`, `MIN_DS_IOS_TESTS`, `MIN_SCREENS_IOS_TESTS`) sobem na mesma mudança que acrescenta testes.
- **`compat-reviewer`** com `SEGURO` e `/code-review` antes de cada merge.
- **Relatórios:** `reports/AAAA-MM-DD-phase4a-verificacao.md` e `reports/AAAA-MM-DD-phase4b-verificacao.md`.

### O que não é verificado (a menos que o dono teste)

- Tudo num iPhone: VoiceOver, arraste com o dedo de verdade, a figura e o mapa no aparelho, desempenho. O seletor de fotos e a câmera do iOS nem existem nesta fase.
- Num Android físico: TalkBack, câmera real, o seletor de fotos do One UI, a orientação de fotos de outros fabricantes, 120 Hz.
- A qualidade das figuras e das dicas para quem treina de verdade, além da crítica de tela e da revisão do dono na folha do piloto.
- As traduções do catálogo com quem treina em outras regiões do Brasil (os apelidos ajudam, mas não cobrem tudo).
- A release com a chave original (fora deste Mac).

### Adaptações do "Pronto quando" do ROADMAP

- **"A biblioteca funciona sem rede depois do primeiro uso":** fica mais forte. Sem a 4M, nenhuma tela usa rede, desde a instalação; provado com a rede desligada no emulador.
- **"O app base continua abaixo do limite de tamanho definido na spec":** o limite é o D20 (+1 MiB sobre `4f41792`, 4A e 4B somadas), conferido pelo script no CI.
- **"Todas as mídias têm licença registrada":** as figuras, o mapa e as dicas são obra própria, com a licença na tela de licenças e o arquivo em `files/licenses/` (D9); nenhuma mídia de terceiros entra.
- **Entrega "thumbnails embutidos e animação ou vídeo sob demanda" (decisão do ROADMAP):** trocada por figuras próprias embutidas (miniatura e animação do mesmo desenho). O download sob demanda fica para a 4M, se houver mídia encomendada. Desvio datado de 2026-10-29, pelo bloqueio de licença e pela medição.
- **Conclusão da fase (chamada do controlador, 2026-10-29):** 287 exercícios com anatomia, nome em pt-BR e dicas; figuras nos 287 se o G1 for aprovado, senão o ícone do equipamento (D21). O G1 não bloqueia o merge da 4A.
- **Entrega "execução animada":** é a alternância dos dois quadros do estilo aprovado na fase 2, não vídeo.

## O que fica para quem implementar

- A divisão em tasks, desde que: o S1 venha primeiro na 4A e o S2 antes do editor; o S4 antes da foto; cada task termine com o app compilando, os testes focados verdes e o `:screens` e o `:designsystem` compilando nos dois alvos; a tela antiga só saia quando a nova cobrir os testes que ela cobria (os testes de host do `:app` que cobrem a biblioteca, as rotinas e o editor são portados ou aposentados com motivo escrito no plano).
- Os nomes exatos das APIs, dos pacotes e dos tokens novos, desde que os papéis sejam os desta spec.
- Os comprimentos finais do esqueleto, as primitivas exatas de equipamento e a forma da tabela de poses (DSL Kotlin, por ângulos), dentro do D3 e do D5.
- O texto de cada dica e de cada nome em pt-BR, dentro das regras de D8 e D12.
- Onde ficam as funções puras de resumo (no `:screens` ou no `WorkoutPresentation`).
- **Chamada do controlador (2026-10-29), itens que o builder decide no plano:** a ordem das operações ao excluir uma pasta (mover as rotinas e apagar a pasta, tudo na mesma `writeTransaction`); a posição da pasta padrão na lista; a assinatura de `reorderRoutines` sobre a ordem global (o D15 descreve o VM montando a ordem completa trocando só as da pasta); e a frase do D18 como legenda perto do botão de exportar.
- O que sai do `:app`: `LibraryScreen`, `ExerciseCard` antigo, `ExercisePicker` antigo, `CustomExerciseDialog`, `RoutinesScreen`, `RoutineEditor`, `RoutineSetEditor` e o diálogo de mover para pasta, quando as telas novas entrarem e nada mais os usar. A tela antiga `exercise/{id}` continua (fase 5).

## Perguntas que só o dono responde

1. ~~Mídia~~ **Resolvida em 2026-10-29:** o dono aprovou a D3 (figuras próprias "giz", piloto de 20 e portão G1). A folha de capturas do G1 ainda vai ao dono para aprovar. Ilustrador contratado seguiria ativando a 4M.
2. **(Leve) Licença das figuras, das poses e das dicas:** o dono não respondeu; **vale o padrão CC0 1.0 (aplicado, D9)**. O mapa corporal fica sob MIT (derivado do MuscleMap). Além disso, com OFL, ISC, MIT e CC0 no repositório e nenhum LICENSE, a escolha da licença do projeto cabe ao dono antes do PR para o upstream (não bloqueia). O repositório ainda não declara licença nenhuma; a concordância do dono do original (`ReiTut/Mooscles`) é tratada no PR final para o upstream e não bloqueia o fork. Quer outra licença?
3. **(Opcional) Revisar os nomes em pt-BR:** a lista dos 287 nomes vai num arquivo só; se quiser, você confere se são os nomes que se usam na sua academia. A fase não espera por isso.
4. **Aparelho:** topa instalar o `preview` desta fase no S23 Ultra para a câmera, o seletor de fotos, o TalkBack e a fluidez a 120 Hz? Sem isso, esses itens ficam como não verificados, como nas fases 2 e 3.
5. **Só se a 4M for ativada:** criar o repositório público de mídia (`ReiTut/Mooscles-media`) junto com o PR final, já que a URL vira contrato com os apps instalados.

## Notas de implementação

Notas datadas da 4A (2026-10-30). O texto aprovado acima não foi reescrito; onde a implementação mudou ou fechou uma decisão, está aqui, com o motivo. Os números das decisões são os do [plano da 4A](../plans/2026-10-29-phase4a-library.md#decisões-travadas-por-este-plano). A verificação está no [relatório](../reports/2026-10-30-phase4a-verificacao.md).

- **Nome do exercício no detalhe (2026-10-30, Task 17):** ver a nota no próprio "Detalhe do exercício": o nome é a primeira linha do conteúdo que rola, não da barra fixa.
- **Busca e chips (decisão 12 do plano):** a busca é o `MSearchField` do design system e o chip "Equipamento" é um `MChipKind.Menu`; os chips de leitura do detalhe são `MInfoChip`. O seletor do treino usa o mesmo campo (a crítica da Task 23 corrigiu o `MTextField` que ele tinha).
- **Seletor: "Limpar filtros" (Task 18):** o seletor mostra também o botão "Limpar filtros" (a barra de filtros vem da biblioteca como está); "Meus exercícios" fica de fora, como a spec diz.
- **Filtros rolam com a lista (Task 15):** os filtros da biblioteca (chips de músculo e o chip de equipamento) rolam junto com a lista; só o campo de busca e a contagem ficam fixos. Uma busca nova leva a lista de volta ao topo.
- **Exemplo da busca sem resultado (Task 20):** "supino articulado" não é uma busca sem resultado em pt-BR sem filtro (acha as máquinas de supino com anilhas). O estado vazio com "Criar" só aparece quando nada é achado; o teste de fluxo usa o filtro "Costas" ligado para chegar lá.
- **Strings além das previstas (Tasks 15, 16 e 17):** `exercise_form_muscle_required` e `exercise_form_equipment_required` (o formulário recusa o Salvar sem músculo ou equipamento escolhido, com o motivo escrito, em vez de um botão desativado sem explicação) e `exercise_form_required_hint`; `exercise_history_last_label` e `exercise_history_best_label` (sem eles "100 kg × 3" não diz o que é) e `exercise_not_found_body` (o corpo do "Exercício não encontrado", pedido pelo pré-voo F28). Todas existem nos dois idiomas, cobertas pelo `StringsParityTest`.
- **Formulário fecha ao salvar (decisão 13, Task 16):** na 4A o formulário fecha ao salvar e a biblioteca abre o detalhe quando o exercício chega (cerca de 250 ms com a biblioteca visível no meio, M5 da crítica). Na 4B, quando a foto tornar o rascunho caro, o formulário fica aberto, com o Salvar ocupado, até o id chegar ou um erro voltar. Custo se a escolha estiver errada: uma falha rara de gravação perde um rascunho curto.
- **Orçamento de "o próximo quadro" (seção "Desempenho", Task 22):** os orçamentos "da aba até a lista" e "abrir o detalhe" são cumpridos como "o segundo quadro". O shell aplica toda rota um quadro depois do toque, no app inteiro (o Histórico tem o mesmo padrão); mudar a navegação da casca está fora da 4A. Custo: um quadro (cerca de 16 ms) em cada troca de aba.
- **Sem bitmap intermediário (D5, Task 9):** o vão limpo sob a cabeça e sob o braço da frente da figura usa `BlendMode.Clear` dentro de uma camada de composição própria (`CompositingStrategy.Offscreen`), refeita a cada desenho. Não é um bitmap guardado nem uma imagem em cache, então a D5 continua valendo; o fundo segue transparente (D3). Se isso for lido como violação, a alternativa é pintar os vãos na cor do fundo, sem camada.
- **Token do lado de lá do corpo (Task 8):** a revisão achou o lado de lá do corpo com o mesmo tom do equipamento. Entrou o token `MoosclesFigureColors.farLimb`, distinto do equipamento e do corpo da frente, com o contraste fixado em teste.
- **Mapa: contraste e procedência (Tasks 1 e 6):** o par principal × secundário a 3:1 é inalcançável com `bgInset` e `accentSolid` (o melhor é cerca de 2,15:1). Vale principal contra o neutro e secundário contra o neutro, ambos a 3:1 ou mais, nos dois temas; o principal se distingue do secundário por matiz, pela legenda e pelo texto equivalente, nunca só por cor. O secundário em `border.strong` engole os contornos internos da região (L2 da crítica); um traço próprio pede um token novo e fica para uma rodada do design system. A procedência do mapa está registrada: os vetores do MuscleMap (MIT, commit `170cfacd`) foram traçados à mão por uma pessoa sobre fotos de referência geradas por IA; só os vetores traçados entram no app, então o mapa não é um "ativo gerado por IA" da D3 e da D9. Se a regra for lida de outro modo, o plano B é o `vulovix/body-muscles`.
- **Pré-carga do mapa (Task 1):** o primeiro mapa de um processo leva de 119 a 159 ms para montar a geometria. O detalhe chama `prepareBodyMap()` cedo; o mapa aparece vazio por cerca de 0,1 s no primeiro uso.
- **Lacuna da decisão 11 (Task 10):** o nome que o `MoosclesViewModel` grava no timer no descanso automático (e que a notificação do `:app` mostra) continua o nome gravado, em inglês, porque o ViewModel não conhece o idioma. Pelo sheet de descanso, a notificação já mostra o nome em pt-BR. Fecha com as mensagens do ViewModel, na fase 6.
- **Portão G1 (Task 9):** a folha do piloto foi entregue ao dono em 2026-10-30 ([folha](../reports/2026-10-30-phase4a-folha-piloto.md)); a resposta está pendente, e a 4A não espera por ela (decisão 2 do plano).
- **Barras do sistema do formulário (Task 23):** no tema claro, a janela do formulário (um diálogo de tela cheia) mostra as barras do sistema em cinza. Corrigir pede um `expect/actual` para controlar a janela; vai com o L5 da fase 3, na 4B.
- **Fixtures de verificação (Task 22):** ids de fixtures antigas (sem o prefixo `catalog_`) aparecem em inglês e com o ícone nas verificações; não acontece numa instalação real.

Notas datadas da 4B. Os números das tasks são os do [plano da 4B](../plans/2026-10-30-phase4b-routines.md).

- **`MigrationEquivalenceTest` com a v6 (2026-10-30, Task 1):** a seção "Dados e compatibilidade" diz que o teste "não muda". Com a `version` 6 isso é impossível, porque o `WorkoutDatabase.open` leva todo banco à v6. Mudou a comparação, não as referências (que continuam as da 1.4.5, nunca regeneradas): o dump tira exatamente as adições da v6 (a definição `` `warmup` INTEGER NOT NULL DEFAULT 0 `` de `routine_sets` no `sqlite_master`, o `warmup=integer:0` de cada série planejada, o `user_version` 6 e o `identity_hash` do `6.json`, trocado pelo do `5.json`), e o teste novo `v6AdditionsAreOnlyTheWarmupColumnAtZero` faz o caminho inverso: cada referência com as adições postas é o dump inteiro.
- **`sqlite_master` da instalação nova e da migrada (2026-10-30, Task 1):** conferido, não suposto: o texto é igual. O SQLite grava a coluna do `ALTER` depois da última coluna e antes das restrições da tabela, onde o `CREATE` do Room a põe. O `RoutineWarmupMigrationTest.freshInstallAndMigrationReachTheSameSchema` compara o `sqlite_master` direto, além de `table_info`, `foreign_key_list`, `index_list` e `index_info`. (O plano dizia que o texto diferia pela ordem da coluna.)
- **O teste da migração no iOS (2026-10-30, Task 1):** "o mesmo teste em `commonTest`" ficou como `IosDatabaseTest.v5DatabaseMigratesToSixOnTheBundledDriver`, no `iosTest`, porque quem abre o banco é de cada plataforma (`WorkoutDatabase.open(context)` no Android, `openWorkoutDatabase(directory)` no iOS). O que é o mesmo é o banco v5 e as conferências: `SchemaV5` (`shared/src/testFixtures`) traz o schema do `5.json` (o `SchemaV5FixtureTest` prova que continua igual ao arquivo), as linhas (duas pastas, três rotinas, séries herdadas, manuais e vazias, um treino finalizado e um ativo com `sourceRoutineId`, checkpoints) e as consultas, usadas pelo `RoutineWarmupMigrationTest` no host e pelo teste do simulador.
- **Versão do formato do backup (2026-10-30, Task 1):** o teste que só repetiria a constante (`formatVersionIsUnchanged`) virou `BackupSampleFileTest.formatVersionStaysOneForAnOptionalField`, preso ao arquivo `v1-sample.json` já versionado; o `olderReaderIgnoresWarmup` lê o arquivo novo com o próprio `Json` do codec (agora `internal`), numa classe com a forma antiga de `RoutineSetRow`.
- **Aquecimento planejado e desfazer no repositório (2026-10-30, Task 2):** a 51ª série restaurada sai como `SET_LIMIT`, o texto do limite de séries, e não como "não foi possível restaurar" (a decisão 5 do plano dizia o contrário); "não foi possível restaurar" fica para a entrada que sumiu e o segundo desfazer. Trocar o exercício de uma entrada mantém os aquecimentos como aquecimentos, vazios e manuais (a seção do editor não falava da troca, e o texto da confirmação diz só que os alvos manuais voltam a seguir o recorde). As colunas antigas de `routine_exercises`, que o resumo do plano mostra, espelham a primeira série de trabalho, e não a primeira linha, que costuma ser um aquecimento.
- **Alvo de uma entrada trocada (2026-10-30, Task 3, pré-voo F16):** o D16 diz ao mesmo tempo "casa por ocorrência" e "trocado no treino não tem alvo", e as duas regras se contradizem porque o treino não guarda de que entrada da rotina veio. Vale a ocorrência: a n-ésima entrada de um exercício no treino casa com a n-ésima dele na rotina, por posição, e "trocado ou adicionado" quer dizer "sem ocorrência correspondente". Uma entrada trocada por um exercício de fora da rotina, ou um exercício a mais, fica sem alvo. Com a rotina A, B, A e o B trocado por A no treino (A, A, A), o A do meio, que é o trocado, pega o alvo do segundo A da rotina, e o terceiro A, o original, fica sem: comportamento aceito, fixado em `WorkoutPresentationTest.replacedOrAddedEntryHasNoTarget`. (Exemplo corrigido em 2026-10-30, na revisão da Task 3: o anterior descrevia o caso intuitivo, em que o trocado é que fica sem alvo.)
- **Espera pelo eco com dois erros seguidos (2026-10-30, Task 3, pré-voo F10):** o `errorSubject` é o assunto do erro mostrado, e um segundo erro antes de a tela ver o primeiro o substitui. Por isso o `UiState` também traz `subjectFailures: Map<String, Int>`, quantos comandos sobre cada id falharam enquanto o ViewModel vive, que nunca é zerado (nem pelo `clearError`). Uma tela que espera o eco de um id guarda a contagem dele ao mandar o comando e libera a espera quando ela cresce; o `errorSubject` continua servindo para o texto do erro. Testado em `AppViewModelRoutinesHostTest.aSecondFailureStillReleasesTheFirstWait`.
- **Rotina de uma pasta que não existe (2026-10-30, Task 3, M3 da revisão da Task 2):** a apresentação mostra na pasta padrão uma rotina cujo `folderId` aponta para uma pasta que sumiu (`RoutineUi.folderId` é sempre uma pasta existente), e o `reorderFolderRoutines` da pasta padrão a conta entre as rotinas dela, sem regravar o `folderId`. Hoje nenhum caminho cria uma rotina assim (o import remapeia e a exclusão de pasta move as rotinas).
- **Foto do exercício: o pipeline mudou (2026-10-30, Task 7, spike S4):** a D17 (e a decisão 11 do plano) previa o `ImageDecoder` a partir da API 28 e o `androidx.exifinterface`. O spike mostrou que na 28 ele falha com `setTargetSize` num JPEG ou HEIC de 12 MP com rotação EXIF (`invalid scale`) e usa ~100 MB com amostra potência de 2. O pipeline ficou único em todas as APIs: `BitmapFactory` com `inSampleSize`, `android.media.ExifInterface` lido do stream e uma `Matrix`; o `ImageDecoder` é só reserva na API 29+, para um formato que o `BitmapFactory` não lê. Risco aceito, para as notas de release: um HEIC na API 28 sai deitado (o `ExifInterface` do framework devolve orientação 0 para HEIF), e na API 26 não decodifica (a tela mostra a falha). Ver o [relatório do spike](../reports/2026-10-30-phase4b-spike-foto.md). O `androidx.exifinterface` previsto na D17 não entrou: o pipeline usa o `android.media.ExifInterface` do framework, lido do stream, e nenhuma dependência nova é adicionada. **Layout em disco (muda a D17 e a decisão 11 do plano):** `files/exercise_photos/<id>.jpg` e `files/exercise_photos/thumbs/<id>.jpg` (a D17 dizia `<id>.thumb.jpg`; com esse sufixo a regra das órfãs, que tira o `.jpg` e compara o resto com o id, apagaria toda miniatura), e rascunhos em `files/exercise_photos/drafts/{<chave>.jpg, thumbs/<chave>.jpg}` (a decisão 11 dizia `cache/exercise_photo_drafts/`; o sistema pode limpar o cache, e o rascunho precisa sobreviver à morte do processo, então quem os apaga é a limpeza de 24 h). A foto é a verdade e a miniatura deriva dela: `commit` renomeia a foto primeiro, a miniatura depois, desfaz tudo e mantém o rascunho se o segundo rename falha, `read` da miniatura dá nulo sem a foto e `removeOrphans` apaga a miniatura sem foto.
- **Barras cinzas das janelas de sobreposição (2026-10-30, Task 10, pré-voo F17):** a nota da 4A ("Barras do sistema do formulário") e a decisão 19 do plano da 4B, que estacionavam o problema por exigir um quarto `expect/actual`, ficam substituídas. `MOverlayWindow` roda, dentro da própria composição da janela, o gancho opcional `LocalOverlayWindowSetup` (um `CompositionLocal` de lambda composable, sem comportamento por padrão); o `:app` o provê em `MoosclesTheme` e, pelo `DialogWindowProvider` da `LocalView`, põe ícones claros ou escuros nas duas barras conforme o tema do app. O `:designsystem` continua com três `expect/actual`. Provado em `OverlayWindowBarsHostTest` (os dois temas); a aparência no aparelho (inclusive a cor ou o scrim da barra de navegação com 3 botões) fica para a verificação da Task 19.
- **`PendingAdd` do detalhe deixa de ser salvo (2026-10-30, Task 10):** a espera do "Adicionado a …" leva a contagem de falhas do assunto, e uma contagem restaurada depois da morte do processo (mapa vazio, comando perdido) nunca liberaria a espera. Por isso `PendingAdd` fica em `remember`, não em `rememberSaveable`: uma recriação dentro da janela do eco perde o aviso "Adicionado a …". A espera termina só quando `subjectFailures[assunto]` cresce, não mais com qualquer `errorCode`.
- **Editar também espera o eco (2026-10-30, Task 10, revisão):** a primeira versão fechava o formulário de edição ao mandar o comando. Como na criação, o Salvar fica ocupado até o estado trazer o exercício com os campos salvos (nome aparado, grupo, equipamento e convenção), ou até `subjectFailures[id]` passar da contagem de quando o comando saiu, ou até o teto; a foto do rascunho só é confirmada (ou a salva, removida) depois do eco, e uma falha deixa o formulário aberto com o rascunho e a foto intactos. Um Salvar de exercício novo cujo id já está no estado não reenvia a criação: só termina.
- **Cancelar e Voltar ignorados com o formulário ocupado (2026-10-30, Task 10, revisão):** com o comando já na fila, fechar descartaria o rascunho e deixaria o exercício criado sem foto. Enquanto há uma espera (ou a foto está sendo aplicada), Cancelar, Voltar e Esc não fazem nada; o teto de `saveEchoWait` limita a espera.
- **O aviso de foto não salva aparece no detalhe (2026-10-30, Task 10, revisão):** um `commit` que falha depois do eco não é mostrado pelo formulário nem pela biblioteca, que saem da composição quando o detalhe abre. O formulário registra o id em `ExercisePhotos.failedCommits` antes de avisar `onSaved`, e `ExerciseInfoRoute` pega o aviso (uma vez) e mostra "Não foi possível salvar a foto." no detalhe, tanto depois de criar como depois de editar. O `onPhotoFailed` do formulário deixou de existir.
- **Teto da espera do eco (2026-10-30, Task 10):** novo token `MoosclesTiming.saveEchoWait` = 15 000 ms (fixado em `SizesTimingElevationTest.saveEchoWaitIsFifteenSeconds`). Passado esse tempo sem eco nem falha, o formulário volta a ficar livre, com o texto de erro de salvar, para que um comando perdido não o prenda.
- **Editor de rotina (2026-10-30, Task 13):** a linha de alvo fica na `subline` do cabeçalho, sob o metadado, como no card do treino (D16, "as mesmas regras"); a linha de apoio guarda só o chip de descanso. Assim o alvo continua visível com os cards recolhidos durante o arraste. A mídia do cabeçalho tem o tamanho do `ExerciseCardHeader` compartilhado (o mesmo do card do treino), e não os 56 dp da linha do editor, por haver um componente de cabeçalho só. Os textos do editor ficam em `strings_editor.xml` (não em `strings_routines.xml`). O toast "Rotina duplicada" tem "Abrir" por um `onOpenRoutine` opcional do `RoutineEditorRoute`, que a Task 15 liga a `routine/{id}`. Trocar o exercício pergunta também quando um aquecimento tem valores, porque a troca esvazia os aquecimentos. A sheet de descanso do editor tem nota própria ("Vale para os treinos que você começar com esta rotina").

Fechamento da 4B (2026-10-30, Task 20). As notas acima são as que as tasks escreveram ao mudar uma decisão (pré-voo F27); esta parte só junta o que faltava, na ordem das decisões do [plano da 4B](../plans/2026-10-30-phase4b-routines.md#decisões-travadas-por-este-plano). O que já tem nota acima fica lá: decisão 5 (nota da Task 2), 6 (Task 2), 7 (Tasks 3 e 10), 8 (Task 3), 11 (Task 7, o pipeline e o layout em disco), 12 (Task 10, o formulário e o teto de 15 s) e 13 (Task 13).

- **Decisão 2 e o S2, arrastar e soltar (2026-10-30, Tasks 4 e 16/17):** o spike é o componente, `interaction/Reorder.kt` no `:designsystem`, só com `foundation` e sem `expect/actual`. Aprovado no emulador e no simulador iOS (run 41082118225): `INJECTS_DRAG` ficou `true` também no iOS, então os testes de arraste rodam nas duas plataformas, e só o cancelamento do gesto fica no Android (`INJECTS_TOUCH_CANCEL = false` no simulador). Ver o [relatório](../reports/2026-10-30-phase4b-spike-reordenar.md). Duas correções vieram depois, achadas pelo fluxo a 2x: com os cards recolhendo ao erguer, a vaga do item podia ficar longe do dedo, e o estado passou a reencontrá-la por deslocamento líquido de duas vezes a folga de toque, e não por trajeto somado (um dedo tremendo não reordena, `aTremblingFingerDoesNotPullAFarSlotToIt`); no iOS (folga de 18 dp, contra 8 dp) uma troca feita enquanto a vaga estava solta era desfeita pelo reencontro, então não há trocas enquanto a vaga está solta. No caso comum do editor a 1x, ao erguer um card que não é o primeiro, o item termina onde está desenhado sob o dedo (`ReorderTest` fixa isso).
- **Decisão 3, reordenar rotinas numa transação (Task 2):** como o plano previa: `reorderRoutines(folderId, ids)` chama o `reorderFolderRoutines`, que põe os ids nas posições globais que as rotinas da pasta já ocupavam, dentro de uma `writeTransaction`, e não mexe nas outras. Os caminhos antigos `moveRoutine`, `moveRoutineExercise` e `updateRoutineExercise` não tinham mais chamador depois das telas antigas e saíram do contrato (a reordenação passa por `reorderRoutines` e `reorderRoutineExercises`); o `reorderRoutines(ids)` antigo ficou. Efeito de produto: o "Adicionar série" passa a copiar a última série (D14).
- **Decisão 9, "Começar" é `Tonal` L e não `Primary` (Task 11):** o `MButton` do design system admite um só `Primary` por tela, e a lista tem um "Começar" por card; a spec dizia "primário". No editor, "Começar treino" é o `Primary` L fixo, e o card da rotina do treino ativo mostra "Retomar treino".
- **Decisão 14, o arraste recolhe os cards (Tasks 13, 16/17):** ao erguer a alça, todos os cards do editor mostram só o cabeçalho e o item continua sob o dedo; ao soltar, voltam como estavam. A linha de alvo fica visível no cabeçalho recolhido (ver a nota da Task 13). A correção do reencontro da vaga (acima) é consequência direta dessa decisão a 2x, onde os cabeçalhos recolhidos têm cerca de 190 dp.
- **Decisão 17, duplicar com nome no idioma da tela (Task 11):** `duplicateRoutine(id, copyName, newId)`: a tela manda "Treino A (cópia)" ou "Push day (copy)", cortado em 120 caracteres sem partir um par substituto, e o id da cópia; o `duplicateRoutine(id)` antigo, que acrescenta " (copy)" em inglês, ficou só para quem já o chama. Depois do eco, o toast "Rotina duplicada" tem "Abrir" (`onOpenRoutine`, ligado à rota `routine/{id}` na Task 15, que troca o editor em vez de empilhar).
- **Decisão 18, "movido para a posição" sem gênero:** o anúncio é "Supino reto agora na posição 1 de 5" e "Bench press moved to position 1 of 5", e não "movido", que erra o gênero de "Remada".
- **Decisão 19 e a decisão 11 da 4A, barras do sistema (Task 10):** a decisão 19 do plano (estacionar as barras cinzas do formulário com um quarto `expect/actual`) foi substituída: ver a nota "Barras cinzas das janelas de sobreposição" acima, que as corrige com um `CompositionLocal` provido pelo `:app`. A lacuna da decisão 11 da 4A (o nome gravado no timer do descanso automático continua em inglês) segue estacionada para a fase 6.
- **Spike S4, foto no Android (Task 6):** aprovado nas APIs 26, 28 e 35 (a foto sai em pé, abaixo de 300 KB, ~16 MB nativos, sem permissão nova). A mudança de pipeline e do layout em disco está na nota da Task 7 acima; o [relatório](../reports/2026-10-30-phase4b-spike-foto.md) tem as medidas. Aceitos para as notas de release: um HEIC na API 28 sai deitado e na 26 não decodifica; o `addRoutineSet` mudou de comportamento (D14); um backup novo importado por um build antigo perde o aquecimento planejado.
- **`MigrationEquivalenceTest` (Task 1):** a mudança de comparação, e não de referências, está na primeira nota da 4B acima; o [relatório de verificação](../reports/2026-10-30-phase4b-verificacao.md) a lista entre os desvios.
- **Portão G1 aprovado, equipamento em cobre (2026-10-30, pós-Task 18):** o dono aprovou a folha do piloto da 4A com uma mudança: o equipamento das figuras passa de cinza-azulado a laranja. O latão já é o recorde e a brasa é o perigo (e a pior sob deuteranopia), então entrou uma primitiva nova, o cobre: `MoosclesFigureColors.equipment = cobre47` (`#B4531A`) no claro e `cobre64` (`#E8813E`) no escuro, com contraste fixado no `ComponentColorsTest` (≥ 4,09:1 no claro e ≥ 5,8:1 no escuro nas páginas e no tile). A folha do piloto foi recapturada e o dono a aprovou de novo. Os lotes de figuras (Task L) vêm depois da 4B, em branches próprias.
