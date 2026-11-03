# Referências de UX e identidade visual em apps de registro de treino (para o redesign do Mooscles)

Nota de método: ~19 chamadas de busca/fetch. A maior parte da cobertura de comparação (Hevy vs Strong etc.) vem de blogs de afiliados ou de concorrentes, então são impressões de revisores, não medições. Não consegui acessar Reddit nem as listagens das lojas diretamente; o sinal "o que os usuários dizem" é indireto. Itens sem fonte confiável estão em Gaps.

## 1. Registro rápido de séries (entrada de números, teclado, passos, gestos) e dicas de desempenho anterior

### Takeaway
Hevy e Strong convergem no mesmo modelo: linha por série com colunas (anterior / carga / reps / check), pré-preenchimento com a sessão anterior e check que dispara o timer de descanso. Revisores veem Strong como marginalmente mais rápido e mais enxuto, mas a diferença é impressão, não medição.

### Cited Findings
- Hevy: cada série tem campos de carga (kg/lb), reps ou faixa de reps, ou duração; coluna RPE opcional — [Hevy: track workouts](https://www.hevyapp.com/features/track-workouts/)
- Hevy: tocar no número da série alterna o tipo (normal, aquecimento, drop, falha) — [Hevy: track workouts](https://www.hevyapp.com/features/track-workouts/)
- Hevy: coluna PREVIOUS mostra o desempenho anterior; ao adicionar um exercício já feito, séries/carga/reps anteriores vêm pré-preenchidos e editáveis; configuração escolhe se o "anterior" vem da última sessão do exercício ou da mesma rotina — [Hevy: track workouts](https://www.hevyapp.com/features/track-workouts/); [Hevy Live Activity (ajuda)](https://help.hevyapp.com/hc/en-us/articles/35649846517399)
- Hevy: botão "+ Add Set" sob cada exercício; deslizar uma série para a esquerda revela Delete — [Hevy: track workouts](https://www.hevyapp.com/features/track-workouts/)
- Hevy: calculadora de anilhas aparece como botão acima do teclado ao digitar a carga em exercícios de barra (precisa ser ativada nas configurações) — [Hevy: track workouts](https://www.hevyapp.com/features/track-workouts/)
- Hevy: cabeçalho da sessão com cronômetro, número de séries concluídas e volume total — [Hevy: track workouts](https://www.hevyapp.com/features/track-workouts/)
- Hevy: superséries via menu de três pontos do exercício ("Add To Superset"), com mais de dois exercícios — [Hevy: track workouts](https://www.hevyapp.com/features/track-workouts/)
- Strong: fluxo começa de template ou treino vazio, carga e reps série a série; tags de aquecimento/falha/drop; RPE opcional de 6 a 10; resumo final com 1RM estimado, PRs e volume — [SensAI: Strong review 2026](https://www.sensai.fit/blog/strong-app-review-2026)
- Strong: calculadora de anilhas e de aquecimento são PRO — [SensAI: Strong review 2026](https://www.sensai.fit/blog/strong-app-review-2026)
- Revisores tendem a chamar o Strong de "mais rápido" e mais compacto; um deles diz que os dois levam praticamente o mesmo número de toques e a diferença é de preferência — [Gainframe](https://gainframe.app/blog/hevy-vs-strong/); [Volm](https://www.volm.app/blog/hevy-vs-strong); [Setgraph/GymNote+ via busca](https://www.gymnoteplus.com/blog/hevy-vs-strong). Todos blogs de produto concorrente; nenhum teste cronometrado encontrado.
- Strong descrito como "abrir, registrar, fechar" (ferramenta quieta e focada); Hevy ganha em recursos e plataforma — [busca agregada, blogs de comparação](https://repreturn.com/strong-app-vs-hevy/)
- Gymshark Training: changelog 2.32.0 diz que o modo "Tracked" passou a mostrar dados do exercício anterior — [APKMirror changelog](https://apkmirror.com/apk/gymshark-ltd/gymshark-training-fitness-app/gymshark-training-fitness-app-2-32-0-release)
- Ladder: registro descrito como funcional (peso e reps, PRs e progressão), secundário ao programa guiado — [Garage Gym Reviews](https://www.garagegymreviews.com/ladder-app-review)

### Inferences
- Copiar: linha por série com [tipo/nº] [anterior] [carga] [reps] [check]; pré-preencher com a última sessão; toque no número da série troca o tipo; swipe para apagar série; "anterior" configurável (última sessão vs. mesma rotina).
- Calculadora de anilhas como atalho acima do teclado é uma ideia pequena e útil, e é gratuita no Hevy mas PRO no Strong; no Mooscles (sem paywall) pode ser gratuita.
- Como a coluna "anterior" e o pré-preenchimento são o padrão, a vantagem de velocidade vem de reduzir toques: check de uma série com valores pré-preenchidos deveria custar 1 toque.
- O Mooscles tem o "snapshot" independente do histórico; "anterior" pode vir do snapshot do último treino do exercício, não da rotina.

### Gaps
- Detalhes finos (teclado numérico customizado vs. do sistema, steppers +/- , avanço automático de campo) não foram encontrados em fonte primária para Hevy/Strong/Fitbod; seria preciso instalar os apps ou ver vídeos.
- Fitbod, Apple Workout, Ladder e os novatos 2025-26 (Gravl, Liftosaur, Setgraph, Boostcamp) sem fonte sobre entrada de números.

## 2. Timer de descanso (posição, controles, notificações, Live Activities/Dynamic Island)

### Takeaway
Timer inicia ao marcar a série, tem ajuste de ±15 s, é por exercício, pode ser desligado, e em 2025-26 o padrão virou Live Activity/Dynamic Island com ação de concluir série direto da tela de bloqueio.

### Cited Findings
- Hevy: timer aparece perto do topo de cada exercício, abaixo das notas; tocar define de 5 s a 5 min; botões −15/+15 durante o descanso; inicia ao concluir a série; um valor por exercício, pode ser desligado (útil para supersets/circuitos); padrão em Perfil > Configurações > Treino — [Hevy: rest timer](https://www.hevyapp.com/features/workout-rest-timer/). A página não menciona som nem háptico.
- Hevy Live Activity: mostra duração do treino, timer de descanso, exercício e série atuais e o desempenho anterior daquela série; permite marcar séries como concluídas pelo widget; ajuste ±15 s ou pular o descanso; notifica ao chegar a zero; disponível no plano gratuito; em Android é acessado puxando a barra de notificações — [Hevy Live Activity](https://www.hevyapp.com/features/live-activity/); [Hevy ajuda](https://help.hevyapp.com/hc/en-us/articles/35649846517399)
- Strong: timer integrado à tela de registro; Live Activity e Dynamic Island só chegaram na v6.5.0 (agosto de 2026); no Apple Watch o timer é uma visão única de série/timer — [SensAI: Strong review](https://www.sensai.fit/blog/strong-app-review-2026)
- Fitbod: o timer inicia ao registrar a série; emite tom e/ou vibração; segundo a ajuda, é preciso ter o app aberto para ouvir o aviso; ajustes no meio do treino só afetam o timer atual, não os demais descansos do exercício — [Fitbod help via busca](https://help.fitbod.me/hc/en-us/articles/360006340194)
- Uma avaliação de usuário do Hevy pedia timer automático após marcar a série (evidência de que a ausência/ocultação é irritante) — citada na busca de Fitbod/Onclarity, [Onclarity Hevy](https://www.onclarity.com/leaderboard/insight/hevy). Fonte fraca, anedota única.
- watchOS 26: redesenho do app Workout (maior mudança de layout e navegação desde o lançamento) com Liquid Glass; um revisor achou mais difícil de usar e os quatro botões de canto, ruído — [Apple Newsroom](https://www.apple.com/newsroom/2025/06/watchos-26-delivers-more-personalized-ways-to-stay-active-and-connected); [GSMArena](https://m.gsmarena.com/apple_introduces_watchos_26_with_liquid_glass_design_aiinfused_workout_buddy-news-68181.php)

### Inferences
- Para Android: notificação em primeiro plano com contagem regressiva, ações "−15 s", "+15 s", "Pular" e "Concluir série" (equivalente à Live Activity) cobre a paridade; no iOS, Live Activity com `Text(timerInterval:)` evita atualizações frequentes (conhecimento geral de ActivityKit, não verificado em fonte; a página da HIG não retornou corpo na busca).
- Timer por exercício com opção "desligado" é essencial para supersets.
- O aviso "só toca com app aberto" do Fitbod é anti-padrão: o Mooscles já usa AlarmManager + notificação, o que resolve isso (ver AGENTS.md); o equivalente iOS precisa de notificação local agendada.

### Gaps
- Sons, padrões de háptico e animação do timer em Strong/Hevy/Fitbod não documentados nas fontes achadas.
- Apple Fitness/Workout: descanso entre séries de força no Watch não pesquisado em profundidade.
- Texto da HIG de Live Activities não obtido (a página não renderizou).

## 3. Detecção e celebração de PR; progresso e gráficos

### Takeaway
PR é tratado como recompensa leve (aviso opcional durante o treino, lista no resumo), com gráficos de volume e 1RM; no Hevy e no Strong a profundidade de gráficos é a principal trava do plano gratuito, o que não se aplica ao Mooscles.

### Cited Findings
- Hevy: notificação de PR ao concluir uma série recordista (1RM, mais reps etc.), opcional nas configurações — [Hevy: track workouts](https://www.hevyapp.com/features/track-workouts/)
- Hevy: PR disponível no plano gratuito e no Pro; compartilhamento de PR fica na camada social, que pode ser ocultada — [SensAI: Hevy review](https://www.sensai.fit/blog/hevy-review-2026)
- Strong: PRs aparecem ao finalizar o treino com 1RM estimado e volume; a fonte não descreve como o PR aparece durante a sessão; gráficos de volume e 1RM, só "alguns" no plano gratuito — [SensAI: Strong review](https://www.sensai.fit/blog/strong-app-review-2026)
- Hevy gratuito: 3 meses de histórico nos gráficos; medidas corporais avançadas só no Pro — [SensAI: Hevy review](https://www.sensai.fit/blog/hevy-review-2026)
- Strong: widgets de calendário e atividade na tela inicial (v6.4.0) — [SensAI: Strong review](https://www.sensai.fit/blog/strong-app-review-2026)
- Crítica geral de gamificação: streaks diários que zeram após um dia, urgência artificial (contagens, barras "sempre atrasado"), leaderboards chamativos e recompensas só por resultado desmotivam; preferir consistência em janela móvel e reconhecer pequenas ações — [Affective: fitness app trends 2026](https://weareaffective.com/learning-centre/fitness-app-trends-2026-what-users-really-want-from-their-workout-apps)

### Inferences
- PR no Mooscles: marcar a série in situ com um selo discreto (ícone + texto, não só cor) e consolidar no resumo; háptico único e curto. Isso é inferência de design; as fontes não descrevem animações de PR.
- Evitar streak que zera; usar "treinos nas últimas 4 semanas".
- Gráficos sem limite de histórico são diferencial natural do app local sem paywall.

### Gaps
- Nenhuma fonte descreve a animação/háptico de celebração de PR de Hevy/Strong/Fitbod/Apple.
- Fitness+/anéis da Apple: não pesquisado.

## 4. Biblioteca de exercícios (mídia), rotinas/pastas, primeira execução e estados vazios

### Takeaway
Limites do plano gratuito do Hevy (4 rotinas, 7 exercícios personalizados) mostram o que *não* copiar; a mídia da biblioteca e as pastas ficaram sem fonte confiável.

### Cited Findings
- Hevy gratuito: 4 rotinas e 7 exercícios personalizados (tabela de ajuda); biblioteca completa, timer e histórico ilimitado de registro no gratuito; histórico de estatísticas de 3 meses — [busca Hevy ajuda/reviews](https://help.hevyapp.com/hc/en-us/articles/35119778922263-Hevy-Pro-Subscription-How-to-get-Pro-and-What-Does-It-Include) (página retornou 403 no fetch; números vêm dos trechos da busca e de [AitoolsBakery](https://aitoolsbakery.com/blog/hevy-review/))
- Hevy Pro: ~US$2,99/mês, US$23,99/ano, US$74,99 vitalício; Strong vitalício US$99,99; Strong gratuito limita a 3 templates — [SensAI: Hevy vs Strong](https://www.sensai.fit/blog/hevy-vs-strong-2026); [SensAI: Strong review](https://www.sensai.fit/blog/strong-app-review-2026)
- Gymshark Training: elogiado pelo design limpo/minimalista e vídeo de demonstração por exercício; criticado pela falta de personalização; versão Android descontinuada segundo uma comparação de 2026 — [Tom's Guide](https://tomsguide.com/wellness/fitness/gymshark-training-app-review-effective-workouts-for-free); [Find Your Edge](https://www.findyouredge.app/news/edge-vs-gymshark-training-uk-2026)
- Onboarding: informar quantos passos tem a configuração e quanto tempo leva reduz abandono inicial (sem quantificar); se usuários somem após a primeira sessão, redesenhar onboarding antes de acrescentar motivação — [Affective](https://weareaffective.com/learning-centre/fitness-app-trends-2026-what-users-really-want-from-their-workout-apps)
- 26% dos apps de fitness são usados uma única vez após o download (Mollee et al., 2017, citado) — [Affective](https://weareaffective.com/learning-centre/fitness-app-trends-2026-what-users-really-want-from-their-workout-apps)

### Inferences
- Fora de escopo para o Mooscles: limites por paywall, feed social, treinador por IA, planos gerados por assinatura (Ladder, Fitbod, Hevy Trainer).
- Mídia: o catálogo embutido do Mooscles não tem fonte de GIF/vídeo avaliada aqui; licenciamento de mídia precisa de pesquisa separada.
- Estado vazio: oferecer iniciar treino vazio e modelos prontos (offline), sem pedir cadastro.

### Gaps
- Mídia da biblioteca (GIF/vídeo/imagem) do Hevy/Strong/Fitbod: sem fonte confiável. Pastas de rotinas do Hevy: sem fonte.
- Primeira execução e estados vazios dos apps de referência: sem fonte (precisaria de capturas de tela/Mobbin).

## 5. Reclamações e elogios dos usuários; legibilidade na academia

### Takeaway
O que a evidência indireta mostra: elogio a apps silenciosos e rápidos (Strong), reclamação de preços/assinaturas e bugs (Fitbod, Strong no Android), e ressalva de que Hevy mistura social. Não achei threads de Reddit.

### Cited Findings
- Strong: nota 4,9 na App Store contra 4,3 no Google Play (42,7 mil avaliações), motivo não explicado; bugs corrigidos: congelamento com históricos grandes (6.3.3) e teclado de RPE no iOS 27 (6.4.2); exportação CSV não pode ser reimportada; Health Connect em beta; sem app Wear OS — [SensAI: Strong review](https://www.sensai.fit/blog/strong-app-review-2026)
- Hevy: 4,9/5 em 92 mil avaliações na App Store dos EUA (um roundup cita ~270 mil, números divergentes); reclamação sobre SKU mensal extra de US$3,99 gerando confusão de preço — [SensAI: Hevy review](https://www.sensai.fit/blog/hevy-review-2026); contraste com roundup em [Jefit](https://www.jefit.com/blog/best-workout-apps-for-2026-top-7-options-tested-and-reviewed) (fonte concorrente)
- Fitbod: reclamações no Trustpilot sobre gestão de assinatura e suporte lento; usuário reclama de aumento de preço sem melhorias; um relato isolado de travamento ao registrar série e falha de sync com Watch — [Indie Hackers](https://www.indiehackers.com/post/fitbod-app-review-2026-honest-take-after-real-testing-45d5f07a1b); [JustUseApp](https://justuseapp.com/en/app/1041517543/fitbod-gym-fitness-planner/reviews); [Onclarity](https://www.onclarity.com/leaderboard/insight/fitbod)
- Ladder: US$29,99/mês ou US$179,99/ano, trial de 7 dias, só iOS; uma fonte de "inteligência" por IA cita logouts e bugs (fonte menos confiável) — [Garage Gym Reviews](https://www.garagegymreviews.com/ladder-app-review); [Marlvel](https://marlvel.ai/intel-report/health-fitness/ladder-strength-training-plans)
- Apple Watch é o principal motivo citado para Strong ser preferido por quem usa iPhone — [AitoolsBakery](https://aitoolsbakery.com/blog/hevy-vs-strong-app/)
- Strong: gratuito sem anúncios; histórico continua acessível se parar de pagar — [SensAI: Strong review](https://www.sensai.fit/blog/strong-app-review-2026)

### Inferences
- A linha de reclamações (preço, assinatura, bugs de teclado, perda de dados/exportação sem reimportação, sincronização) se alinha com os pontos fortes do Mooscles: sem conta, offline, exportável. Exportação reimportável (round-trip) é diferencial.
- Teclado e campos numéricos são fonte recorrente de bugs; testar com fontes grandes e teclados de terceiros.

### Gaps
- Sem acesso a threads do r/Hevy, r/weightroom e avaliações de lojas; as citações acima são de agregadores/blogs. Recomenda-se leitura manual de reviews 1-3 estrelas de Hevy/Strong nas lojas.

## 6. Acessibilidade (fonte grande, contraste, uso com uma mão, luvas/mãos suadas)

### Takeaway
Há diretrizes de design, mas nenhum estudo de usabilidade medindo taxa de erro com mãos suadas; as recomendações são alvos grandes, zona do polegar e alto contraste.

### Cited Findings
- Guia de UX de fitness: apps de academia devem funcionar com luz baixa e uma mão, durante a série; alvos de toque acima do mínimo (referência 44–48 px), os controles do treino ativo maiores; controles principais na zona inferior do polegar — [Made Good Designs](https://madegooddesigns.com/fitness-app-design/)
- Conceito de portfólio: academia barulhenta e clara, mãos suadas, telefone à distância do braço; resposta: telas escuras de alto contraste, alvos enormes, uma ação clara por tela (conceito, sem resultados medidos) — [Contra: G-MAP](https://contra.com/p/O2vZE3qg-g-map-or-sotiris-iliadis)
- Opinião de pesquisador: o sal do suor atrapalha telas capacitivas, e poucos botões grandes superam UI complexa — [Furbo: Nike+iPod](https://furbo.org/?p=81) (opinião, não estudo)
- Cor não deve ser o único indicador de progresso ou aviso; ~12% dos homens e 0,5% das mulheres têm deficiência de visão de cores — [Affective](https://weareaffective.com/learning-centre/fitness-app-trends-2026-what-users-really-want-from-their-workout-apps)
- Texto: preferir quase-branco com leve tom em fundo escuro (não branco puro) e testar todos os pares no WCAG — [Lazyweb/guias de tema](https://www.lazyweb.com/research/what-theme-do-health-fitness-apps-use)
- Háptico (Google): seguir padrões da plataforma, limitar o número de hápticos únicos, só disparar se o sistema permitir — [Material 2: Android haptics](https://m2.material.io/design/platform-guidance/android-haptics.html)

### Inferences
- Para o Mooscles, usar alvos de 56 dp ou mais para check/steppers do treino ativo (valor de design proposto por inferência; o guia só cita 44–48 como mínimo), campos de número com `sp` que escalam até 200% sem truncar, e testar com fonte do sistema grande.
- Evitar gestos de precisão (swipe curto, long-press obrigatório) como única forma de ação; oferecer alternativa por toque.
- Verificar TalkBack/VoiceOver para ordem de leitura da linha de série.

### Gaps
- Valores validados de alvo para luvas/suor: não achados. Guidelines oficiais de Apple/Google sobre tamanho mínimo (44 pt / 48 dp) não foram consultadas nesta rodada.

## 7. Identidade visual 2025–2026, tipografia de números, movimento e o que parece datado

### Takeaway
Treino tende a escuro com um acento enérgico (lima, laranja, azul elétrico); o mercado está dividido quase meio a meio entre escuro e claro por padrão; movimento por molas (Material 3 Expressive) e Liquid Glass (Apple) são as direções das plataformas.

### Cited Findings
- Auditoria da Lazyweb (jul/2026, 71 design systems de Saúde e Fitness): 55% usam escuro por padrão (39 escuro, 32 claro); azul é o matiz primário mais comum (24 de 71) — [Lazyweb](https://www.lazyweb.com/research/what-theme-do-health-fitness-apps-use)
- Apps de treino: fundo escuro e um acento (lima, laranja, azul elétrico); ioga/recuperação vão para sálvia, argila e neutros quentes; vermelho reservado a alertas reais — [Lazyweb](https://www.lazyweb.com/research/what-theme-do-health-fitness-apps-use); [Made Good Designs](https://madegooddesigns.com/fitness-app-design/)
- Modo escuro como sistema completo: no mínimo ~4 níveis de superfície; "um cinza escuro não é modo escuro" — [Moburst](https://www.moburst.com/blog/top-mobile-web-design-trends/) (blog de agência, fonte secundária)
- Tipografia mais ativa, micro-animações que guiam atenção — [Moburst](https://www.moburst.com/blog/top-mobile-web-design-trends/)
- Material 3 Expressive: sistema de movimento por molas; no Compose, o esquema "expressive" é indicado para elementos de destaque e momentos-chave; estreou em Pixels com Android 16 (QPR1, set/2025) — [Kotlin docs: MotionScheme.expressive](https://kotlinlang.org/api/compose-multiplatform/material3/androidx.compose.material3/-motion-scheme/-companion/expressive.html); [9to5Google](https://9to5google.com/2025/05/13/android-16-material-3-expressive-redesign/); [Android Authority](https://www.androidauthority.com/google-material-3-expressive-features-changes-availability-devices-3556392)
- Apple: Liquid Glass aplicado à navegação e controles no watchOS 26, com recepção mista — [Apple Newsroom](https://www.apple.com/newsroom/2025/06/watchos-26-delivers-more-personalized-ways-to-stay-active-and-connected); [GSMArena](https://m.gsmarena.com/apple_introduces_watchos_26_with_liquid_glass_design_aiinfused_workout_buddy-news-68181.php)
- Gymshark Training: visual limpo e minimalista elogiado — [Tom's Guide](https://tomsguide.com/wellness/fitness/gymshark-training-app-review-effective-workouts-for-free)
- Anti-padrões listados: dashboards clínicos só de dados, streaks que zeram, urgência artificial, notificações intrusivas ou irrelevantes, recomendações arbitrárias — [Affective](https://weareaffective.com/learning-centre/fitness-app-trends-2026-what-users-really-want-from-their-workout-apps)
- Anti-padrões de monetização observados: limites de rotinas/exercícios/histórico no gratuito (Hevy, Strong), vários SKUs confusos, cobrança alta com suporte lento (Fitbod, Trustpilot) — ver seções 4 e 5.

### Inferences
- Números do treino: não há fonte sobre fontes específicas; proposta de design é usar numerais tabulares (`tnum`) e tamanho grande para carga/reps, para colunas alinhadas e sem "pulo" quando o valor muda. Verificar suporte de OpenType `tnum` no Compose Multiplatform.
- Identidade: tema escuro por padrão seguindo o sistema, com claro completo; um acento só; estados (PR, descanso, erro) distintos por ícone/forma além de cor; superfícies em 3–4 níveis.
- Movimento: usar molas expressivas só em momentos de destaque (concluir série, PR, fim do treino); transições curtas no resto, para o timer e a lista não distraírem; respeitar "reduzir movimento".
- Parece datado (inferência a partir das fontes): dashboards densos de métricas sem hierarquia, gradientes pesados/neon sobre preto puro, gamificação com streak punitivo, mascotes/ilustrações genéricas de stock. Sem fonte direta para ilustrações.

### Gaps
- Tendências de ilustração e tipografia específicas de apps de força: sem fonte confiável (os resultados eram blogs genéricos de agência).
- Padrões de motion/háptico reais de Hevy/Strong/Fitbod/Apple: não documentados.
- Newcomers 2025–26: só achei Setgraph (logging rápido, só Apple, segundo blogs do próprio fornecedor, com contradição sobre suporte a Watch) e Boostcamp (programas gratuitos, sem comunidade); Gravl e Liftosaur sem resultados — [Setgraph](https://setgraph.app/ai-blog/best-app-for-tracking-workouts); [Garage Gym Reviews: Boostcamp](https://www.garagegymreviews.com/boostcamp-review)

## 8. O que depende de conta, social ou paywall (fora do escopo do Mooscles)

### Takeaway
Feed social, seguir amigos, compartilhamento de PR no feed, treinador/planos por IA, sincronização na nuvem e limites do plano gratuito são do modelo de conta e assinatura; não entram.

### Cited Findings
- Hevy: feed, seguidores e compartilhamento de PR são camada social, que pode ser ocultada; Hevy Trainer é baseado em regras de progressão de carga — [SensAI: Hevy review](https://www.sensai.fit/blog/hevy-review-2026)
- Fitbod e Ladder: planos gerados/guiados sob assinatura (Ladder US$29,99/mês) — [Garage Gym Reviews: Ladder](https://www.garagegymreviews.com/ladder-app-review); [Indie Hackers: Fitbod](https://www.indiehackers.com/post/fitbod-app-review-2026-honest-take-after-real-testing-45d5f07a1b)
- Strong PRO: calculadoras, mais gráficos, templates ilimitados — [SensAI: Strong review](https://www.sensai.fit/blog/strong-app-review-2026)

### Inferences
- Itens que viram gratuitos no Mooscles: calculadora de anilhas e de aquecimento, gráficos completos, templates/rotinas ilimitados, medidas corporais, exportação.
- "Compartilhar cartão do treino" (PNG local, já existe em `sharing/`) é a alternativa offline ao feed.

### Gaps
- Nenhum.
