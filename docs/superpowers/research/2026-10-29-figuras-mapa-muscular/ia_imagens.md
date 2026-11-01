# Imagens geradas por IA para as ilustrações de exercícios (viabilidade jurídica e técnica)

Escopo: ~287 exercícios x 2 quadros (início e fim) = ~574 imagens, distribuídas num app livre e gratuito com repositório público no GitHub. Comparação com figuras desenhadas por código (procedurais). Estado em 2026-10-29. **Isto é pesquisa, não parecer jurídico.** Os pontos marcados "(advogado)" pedem consulta a um profissional antes de decidir.

Notas de confiabilidade: as páginas oficiais da OpenAI e da Midjourney devolveram 403 ao WebFetch. Os trechos desses dois termos vêm de rastreadores de termos de terceiros (ConductAtlas, terms.law) e precisam ser conferidos na página oficial. A página de termos da API Gemini e a central de ajuda da BFL foram lidas direto.

## 1. Direito autoral: EUA, Brasil, UE, CC0 e semelhança com material de treino

### Takeaway
Nos EUA, na UE (pelo critério do TJUE) e, ao que tudo indica, no Brasil (Lei 9.610/98, art. 11), uma imagem gerada só por prompt não tem autor humano e, portanto, não tem direito autoral. Na prática ela fica como domínio público, o que não impede a redistribuição, mas impede o projeto de "licenciar" essas imagens no sentido forte. Um CC0 continua útil como renúncia geral a qualquer direito que exista (contribuições humanas, direitos cedidos pelo fornecedor). O risco residual é a imagem se parecer com uma obra protegida, o que é baixo para figuras genéricas de exercício, mas não é zero e os fornecedores não dão garantia.

### Cited Findings
**EUA**
- O relatório Parte 2 do US Copyright Office (Copyrightability, 29/01/2025) conclui que, "com a tecnologia disponível hoje, prompts sozinhos não dão controle humano suficiente para tornar o usuário autor do resultado". O mesmo prompt gera resultados diferentes, o que reforça a falta de controle. — [Copyright Alliance, resumo](https://copyrightalliance.org/ai-report-part-2-copyrightability/); [Lowenstein Sandler](https://www.lowenstein.com/news-insights/publications/client-alerts/us-copyright-office-issues-report-on-copyrightability-of-generative-ai-outputs-ai); [Akin Gump](https://www.akingump.com/en/insights/ai-law-and-regulation-tracker/us-copyright-office-releases-part-2-of-ai-report)
- O relatório mantém a proteção para a expressão humana: seleção e arranjo criativos, modificações humanas perceptíveis e material próprio do usuário que continue perceptível no resultado. Um trabalho humano maior que inclui partes geradas continua protegido como um todo. — [Copyright Alliance](https://copyrightalliance.org/ai-report-part-2-copyrightability/); [Dorsey](https://www.dorsey.com/insights/generative-ai-and-the-copyright-office-part-2-of-long-awaited-guidance-but-will-it-continue)
- O Office deixou aberta a possibilidade de, no futuro, prompts darem controle suficiente; o relatório é uma declaração de política, não jurisprudência vinculante. — [Copyright Alliance](https://copyrightalliance.org/ai-report-part-2-copyrightability/)
- A Suprema Corte negou certiorari em *Thaler v. Perlmutter* (nº 25-449) em 02/03/2026; fica valendo a decisão do Circuito de D.C. de que a lei exige autoria humana. A quantidade de participação humana suficiente não foi decidida; o caso a acompanhar é *Allen v. Perlmutter*. — [Baker Donelson](https://www.bakerdonelson.com/supreme-court-denies-certiorari-in-thaler-v-perlmutter-ai-cannot-be-an-author-under-the-copyright-act); [Jones Day](https://www.jonesday.com/de/insights/2026/03/jones-day-talks-women-in-ip--supreme-court-denies-review-of-thaler-v-perlmutter-ai-authorship-questions-remain); [Mondaq](https://www.mondaq.com/unitedstates/copyright/1756930/supreme-court-declines-to-hear-thaler-v-perlmutter-leaving-human-authorship-requirement-intact)

**Brasil**
- Pela Lei 9.610/98 (art. 7º, obras como "criações do espírito"; art. 11, autor é a "pessoa física criadora"), a doutrina conclui que não há proteção para obras criadas pela IA no Brasil. — [Migalhas](https://www.migalhas.com.br/depeso/397458/inteligencia-artificial-e-autoria-analise-da-legislacao-internacional)
- Comentário de especialista: quando a pessoa usa a IA como ferramenta e faz intervenções criativas relevantes, a obra tende a ser considerada dela. — [Jornal Opção](https://www.jornalopcao.com.br/tecnologia/inteligencia-artificial-cria-conteudo-mas-quem-fica-com-os-direitos-autorais-especialista-explica-845643/)
- A literatura acadêmica aponta lacunas normativas e insegurança interpretativa. — [Revista UFPR](https://revistas.ufpr.br/rrddis/article/view/103445)
- O PL 2338/2023 (marco da IA) foi aprovado no Senado em dez/2024 e segue na Comissão Especial da Câmara, sem data de votação confirmada; reportagem de março de 2026 dizia que a votação antes das eleições era improvável. A seção de direitos autorais trata sobretudo do uso de obras no **treino** (remuneração, direito de o titular proibir o uso) e há pressão para retirá-la. — [Teletime, 25/03/2026](https://teletime.com.br/25/03/2026/votacao-do-pl-de-ia-antes-das-eleicoes-e-improvavel/); [Brasil de Fato](https://www.brasildefato.com.br/2024/12/10/senado-aprova-pl-da-inteligencia-artificial); [Aos Fatos](https://www.aosfatos.org/noticias/convidados-comissao-pl-ia-divergem-direitos-autorais-orgao-governanca/)
- O PL 3656/2024 propõe alterar a Lei 9.610 para tratar de obras geradas por IA (inclusive as autônomas) e exigir identificação delas nos registros. Status de tramitação não verificado. — [InternetLab, texto do PL](https://internetlab.org.br/wp-content/uploads/2024/09/PL-3656-2024.pdf)
- O Secretário de Direitos Autorais do MinC, em audiência na Câmara (02/09/2025), apontou a falta de regras claras como fonte de judicialização. — [Apresentação, Câmara](https://www2.camara.leg.br/atividade-legislativa/comissoes/comissoes-temporarias/especiais/57a-legislatura/comissao-especial-sobre-inteligencia-artificial-pl-2338-23/apresentacoes-em-eventos/MARCOSALVESDESOUZASecretriodeDireitosAutoraiseIntelectuaisdoMINCCmaradosDeputados02.09.25.pdf)

**UE**
- Tribunal Municipal de Praga (10 C 13/2023, 11/10/2023): imagem feita com DALL-E não é obra protegida, porque o autor tem que ser pessoa natural; o prompt foi tratado como ideia ou tema, não protegidos. Comentaristas ligam a decisão ao critério do TJUE (*Painer*, C-145/10: "criação intelectual do autor, que reflete sua personalidade e suas escolhas livres e criativas"). — [Bird & Bird](https://www.twobirds.com/en/insights/2024/czech-republic/czech-court-denies-copyright-protection-of-ai-generated-work-in-first-ever-ruling); [Novagraaf](https://www.novagraaf.com/en/insights/ai-and-copyright-first-ruling-european-court/); [heise](https://heise.de/-9689971)

**Semelhança com material protegido**
- O termo da API Gemini avisa que o Google "pode gerar o mesmo conteúdo ou conteúdo semelhante para outros" e que o usuário é responsável pelo uso do que gerou e pelo uso por quem recebe. — [Gemini API Additional Terms](https://ai.google.dev/gemini-api/terms)
- A cessão da OpenAI não alcança resultados de outros usuários nem "Third Party Output". — [ConductAtlas, OpenAI ToU](https://conductatlas.com/platform/openai/terms-of-use-row/output-ownership-and-content-license/)

### Inferences
- Para o Mooscles, a falta de direito autoral não é um bloqueio: o app pode redistribuir as imagens. O que muda é que o projeto não consegue impedir ninguém de copiá-las, o que, para um app livre, é aceitável.
- Uma licença CC0 (ou a mesma licença do repositório) para a pasta de imagens é a forma honesta de documentar: renuncia ao que existir (retoques humanos, seleção, o que o fornecedor cedeu) sem afirmar uma autoria que talvez não exista. Convém um `NOTICE`/README dizendo que as imagens foram geradas por IA, com qual modelo e em que data. (advogado, se quiserem uma redação formal)
- O risco de semelhança é baixo para silhuetas genéricas de exercícios (as poses são funcionais, não expressivas), mas aumenta se o prompt pedir o estilo de um artista ou de um app concorrente. Não pedir estilo de terceiros e não usar imagens de terceiros como referência reduz muito o risco.
- Retoques humanos substanciais (vetorizar à mão, redesenhar) aumentam a chance de proteção, mas também o custo: nesse ponto a vantagem da IA sobre o desenho procedural diminui.

### Gaps
- Não encontrei decisão de tribunal brasileiro nem posição da ANPD ou do INPI sobre a titularidade de imagens geradas por IA. A ANPD trata de dados pessoais e, até onde pesquisei, não se pronunciou sobre autoria de resultados.
- Não li o texto atual do PL 2338 na Câmara para confirmar se ele diz algo sobre a titularidade do **resultado** (as fontes falam só de treino). Não verifiquei o status do PL 3656/2024.
- Não pesquisei *Getty v. Stability* (Reino Unido) nem *Allen v. Perlmutter*; podem mudar o quadro de risco de semelhança e de autoria assistida.

## 2. Termos de uso dos geradores (2026)

### Takeaway
Todos os grandes fornecedores pagos cedem ao usuário os direitos que tiverem sobre o resultado e permitem uso comercial; nenhum dos termos lidos proíbe redistribuir as imagens num repositório aberto. As armadilhas são: plano gratuito da Midjourney (licença CC BY-NC, incompatível com redistribuição livre), pesos "dev" não comerciais da FLUX se rodados localmente, e a proibição de usar resultados para treinar modelos concorrentes (irrelevante para o app, mas relevante se o conjunto virar um "dataset aberto"). OpenAI e Google embutem C2PA e SynthID; nenhum termo lido obriga a manter o metadado, mas a obrigação de rotular pode vir da lei (AI Act).

### Cited Findings
- **OpenAI (ChatGPT, API gpt-image):** "We hereby assign to you all our right, title, and interest, if any, in and to Output" (versão de 01/01/2026 segundo o rastreador; a versão ROW tem redação um pouco diferente). A cessão não cobre resultados de outros usuários; a OpenAI pode usar o conteúdo para melhorar os serviços. **Não verificado na página oficial (403).** — [ConductAtlas, ToU](https://conductatlas.com/platform/openai/openai-terms-of-use/output-ownership-assignment/); [ConductAtlas, ROW](https://conductatlas.com/platform/openai/terms-of-use-row/output-ownership-and-content-license/)
- **OpenAI, proveniência:** imagens do ChatGPT, do Codex e da API levam metadado C2PA e marca d'água SynthID; o verificador em openai.com/verify detecta os dois. — [OpenAI Help Center](https://help-lb.openai.com/en/articles/8912793-c2pa-and-synthid-in-openai-generated-images); [The Next Web](https://thenextweb.com/news/openai-c2pa-synthid-ai-image-detection-watermark)
- **Google (Gemini API / Nano Banana), termos atualizados em 28/04/2026:** "Google won't claim ownership over that content"; pode gerar conteúdo igual para outros; proibido usar o serviço para desenvolver modelos concorrentes; idade mínima de 18 anos; no nível gratuito, prompts e respostas podem ser usados para melhorar produtos e lidos por revisores humanos; no pago, não. A página não menciona SynthID nem proíbe redistribuir. — [Gemini API Additional Terms](https://ai.google.dev/gemini-api/terms)
- **Google, SynthID/C2PA no Nano Banana:** só fontes secundárias; um experimento relata manifesto C2PA em resultado do Nano Banana Pro. — [ndurner substack](https://ndurner.substack.com/p/limits-in-ai-imagery-authorship-and)
- **Midjourney:** membros pagos "own" os assets na medida permitida pela lei; empresas com mais de US$ 1 milhão/ano de receita precisam do plano Pro ou Mega; quem **não paga** não é dono e recebe licença **Creative Commons Noncommercial 4.0 Attribution**. A licença que o usuário dá à Midjourney sobrevive ao cancelamento; imagens ficam públicas na galeria fora do modo Stealth. **Não verificado na página oficial (403).** — [terms.law, 2026](https://terms.law/2026/01/15/midjourney-commercial-use-rights-complete-2026-guide/); [Midjourney ToS (URL oficial)](https://docs.midjourney.com/docs/terms-of-service); [Midjourney Plans](https://docs.midjourney.com/docs/plans)
- **Black Forest Labs (FLUX):** "All images generated through the BFL API include full commercial usage rights" (também no Playground). Pesos locais: FLUX.2 [klein] 4B é Apache 2.0; FLUX.2 [klein] 9B, FLUX.1 Kontext [dev] e FLUX.2 [dev] estão na FLUX Non-Commercial License e precisam de licença comercial. FLUX.1 [schnell] é Apache 2.0. — [BFL Help Center](https://help.bfl.ai/articles/4375863104-can-i-use-the-api-for-a-commercial-application); [innfactory](https://innfactory.ai/en/ai-models/flux/)
- **Stability AI:** Community License permite uso comercial para quem fatura menos de US$ 1 milhão/ano; acima disso, licença Enterprise. O usuário é dono dos resultados na medida permitida pela lei. — [Stability AI, licenças](https://stability.ai/membership); [Stability AI, License Update](https://stability.ai/news/license-update)
- **Adobe Firefly:** "In general, you may use outputs from generative AI features commercially", exceto recursos beta marcados como não comerciais; a Adobe pode anexar Content Credentials (C2PA). — [Adobe Generative AI User Guidelines](https://www.adobe.com/uk/legal/licenses-terms/adobe-gen-ai-user-guidelines.html); [Adobe Firefly FAQ](https://helpx.adobe.com/firefly/get-set-up/learn-the-basics/adobe-firefly-faq.html)
- **EU AI Act, art. 50:** a marcação legível por máquina (art. 50(2)) é obrigação do **fornecedor** do sistema gerador; o **implantador** tem dever de rotular deepfakes. Pelo acordo do Digital Omnibus (07/05/2026), o prazo da marcação para sistemas já no mercado passou de 02/08/2026 para 02/12/2026. O Código de Prática sobre transparência é voluntário e estava em finalização em 2026. — [Mishcon de Reya](https://www.mishcon.com/news/ai-act-transparency-obligations-code-of-practice-and-draft-guidelines); [Kennedys](https://kennedyslaw.com/en/thought-leadership/article/2026/the-eu-ai-act-s-draft-code-of-practice-on-marking-and-labelling-of-ai-generated-content-what-providers-and-deployers-need-to-know/); [Dudkowiak](https://www.dudkowiak.com/blog/ai-act-article-50-ai-content-labeling-and-code-of-practice-from-august-2-2026/)

### Inferences
- Caminhos limpos para o Mooscles: API paga da OpenAI, do Google (nível pago, que também evita uso dos prompts para treino), API da BFL, Firefly fora de beta, ou FLUX.2 [klein] 4B / FLUX.1 [schnell] locais (Apache 2.0). **Evitar** Midjourney gratuito (CC BY-NC conflita com redistribuição livre e com forks comerciais) e pesos FLUX "dev" rodados localmente sem licença.
- Ilustrações de exercício não são deepfakes de pessoas reais, então o dever de rótulo do implantador no AI Act provavelmente não se aplica; a marcação é responsabilidade do fornecedor. Mesmo assim, manter um aviso no README/licenças ("ilustrações geradas por IA com X") é barato e honesto. (advogado, se o app for distribuído formalmente na UE)
- Redimensionar, vetorizar ou converter para WebP tende a remover o C2PA; o SynthID pode sobreviver parcialmente. Nenhum termo lido proíbe isso, mas confirme na página oficial da OpenAI antes de remover metadados de forma deliberada.
- O usuário (titular no Brasil) precisa ter 18 anos para a API Gemini; não é problema aqui, mas vale para colaboradores.

### Gaps
- Textos oficiais atuais da OpenAI e da Midjourney não foram lidos diretamente (403); a redação exata precisa ser conferida.
- Não confirmei se os termos da OpenAI ou da Midjourney proíbem remover C2PA/marca, nem se algum fornecedor proíbe publicar os resultados como "dataset aberto" que terceiros usem para treino. O Google e a OpenAI restringem o **usuário** de treinar concorrentes; se as imagens ficarem em CC0, terceiros não estão vinculados a esses termos (inferência, não verificada).
- Não li o texto do FLUX.2 [dev] License (o arquivo no Hugging Face deu 404) para ver a cláusula sobre resultados.

## 3. Viabilidade técnica: consistência, anatomia, tamanho e custo

### Takeaway
Manter um personagem e um estilo em ~574 imagens é possível, mas não automático: exige imagens de referência (Nano Banana Pro aceita até 14), ou um pipeline local com ControlNet de pose + IP-Adapter + LoRA do personagem, e mesmo assim há deriva em ângulos difíceis e oclusões. Correção anatômica do movimento é o ponto fraco: estudos em ilustração anatômica acham erros estruturais em todos os geradores testados, e não há estudo específico de exercícios. Cada imagem precisaria de revisão humana por alguém que conhece a execução. O custo de geração é baixo (dezenas a poucas centenas de dólares); o custo real é o tempo de curadoria.

### Cited Findings
**Consistência de personagem e estilo**
- Nano Banana Pro aceita até 14 imagens de referência e até 5 personagens consistentes, segundo a central de ajuda da Higgsfield; outros guias recomendam cerca de 6 a 8 referências e relatam que a consistência estabiliza perto de 6 e pode piorar acima de 10. Detalhes finos (logos, acessórios) derivam. — [Higgsfield Help Center](https://higgsfield.ai/creator-hub/help-center/ai-models/how-do-i-use-nano-banana); [flick.art](https://flick.art/blog/img2img-consistent-character/nano-banana); [laozhang.ai](https://blog.laozhang.ai/en/posts/nano-banana-pro-face-consistency-guide)
- Não pedir um estilo diferente do das referências (por exemplo, referências fotográficas e saída em traço) piora a consistência. — [ai-video-bootcamp](https://ai-video-bootcamp.pages.dev/blog/ai-video-visuals-character-consistency)
- Números como "90%+ de consistência" vêm de blogs de marketing, sem fonte primária; relatos no Reddit indicam resultados irregulares. — [selfielabstudio](https://selfielabstudio.com/blog/nano-banana-pro-perfect-character-consistency-guide-20260212); [Reddit snapshot](https://reddit.sentinel-team.org/posts/1rz4igb/snapshots/2026-03-28T05%3A33%3A01.316841Z)

**Pose por ControlNet / esqueleto**
- Condicionamento de pose 2D (OpenPose) não tem geometria 3D e falha com oclusão; o artigo PersonaCraft propõe guias de profundidade e SMPL 3D como correção (métodos de pesquisa, não o fluxo padrão). — [arXiv 2411.18068](https://arxiv.org/html/2411.18068v2)
- Peso do ControlNet alto demais deixa a figura rígida e perde o estilo da LoRA; baixo demais e a pose se perde. — [DEV Community, ComfyUI](https://dev.to/rashid_inmarketing_19a21d/maintaining-character-consistency-in-comfyui-lora-controlnet-ipadapter-5883)
- IP-Adapter substitui só em parte uma LoRA de personagem; os IP-Adapters de FLUX.1-dev (InstantX, XLabs) não foram feitos para rostos humanos. — [DEV Community, qcrao](https://dev.to/qcrao/character-consistency-in-ai-comics-3-tricks-that-beat-lora-training-for-me-3ad7); [arXiv 2503.16418](https://arxiv.org/pdf/2503.16418)
- Relato único (não é benchmark): num teste de 600 quadros de quadrinho, adicionar ControlNet OpenPose subiu a consistência em ângulos dramáticos de 71% para 83%. — [DEV Community, qcrao](https://dev.to/qcrao/character-consistency-in-ai-comics-3-tricks-that-beat-lora-training-for-me-3ad7)

**Anatomia**
- Três geradores testados para ilustração anatômica: boa qualidade visual, mas nenhum produziu estruturas anatomicamente corretas. — [European Journal of Anatomy](https://eurjanat.com/articles/a-comparative-analysis-to-assess-anatomical-illustrations-via-three-ai-driven-text-to-image-generators/); [Hacettepe](https://avesis.hacettepe.edu.tr/yayin/168355d1-79a8-4910-bb82-d1de73e3bd86/text-to-image-generators-for-anatomical-illustrations-potential-and-limitations)
- Ponto de vista em JMIR Medical Education (2026): erros típicos são estruturas fora do lugar, relações espaciais erradas e elementos inventados, porque os modelos casam padrões sem entender anatomia funcional nem relações 3D. — [JMIR Med Educ 2026](https://mededu.jmir.org/2026/1/e92700)
- Estudo de pés com ChatGPT: ossos errados ou ausentes e rótulos fora do lugar. — [Journal of Orthopaedic... (scholastica)](https://journaloei.scholasticahq.com/article/128540)

**Custo por imagem (fontes de terceiros; conferir nas páginas oficiais)**
- Gemini 3 Pro Image (Nano Banana Pro): ~US$ 0,134 por imagem em 1K/2K, ~US$ 0,24 em 4K; batch por cerca de metade. — [BenchLM](https://benchlm.ai/md/media-pricing/nano-banana.md); [IntuitionLabs](https://intuitionlabs.ai/articles/ai-image-generation-pricing-google-openai)
- Nano Banana 2 (Gemini 3.1 Flash Image): ~US$ 0,034 por imagem 1K. — [laozhang.ai](https://blog.laozhang.ai/en/posts/nano-banana-2-vs-gpt-image-15)
- GPT Image 1.5: cobrado por token; ~US$ 0,01 (low), ~0,04 (medium), ~0,17 (high) por imagem quadrada. Um agregador diz que o gpt-image-1 sai do ar em 23/10/2026. — [EvoLink](https://evolink.ai/blog/nano-banana-2-vs-gpt-image-1-5); [IntuitionLabs](https://intuitionlabs.ai/articles/ai-image-generation-pricing-google-openai)

### Inferences
- **Custo de geração:** 574 imagens x 3 a 5 tentativas por imagem (estimativa minha para pose e consistência) ≈ 1.700 a 2.900 gerações. A US$ 0,03 a 0,13 cada, dá algo entre ~US$ 50 e ~US$ 380; em modo batch, cerca de metade. Rodar FLUX.2 [klein] 4B localmente custa só hardware e tempo.
- **Tempo de curadoria:** cada par precisa de revisão de execução (amplitude, coluna, joelho sobre o pé, pegada, número de dedos). Com ~574 imagens, isso é o custo dominante e exige alguém que conhece o movimento.
- **Dois quadros coerentes** (início e fim com o mesmo personagem, mesmo equipamento, mesmo enquadramento) são mais difíceis que uma imagem isolada; edição a partir do primeiro quadro (Nano Banana, gpt-image em modo edição) ou pose por esqueleto são os mecanismos indicados, ainda com deriva.
- **Tamanho (estimativa minha, não pesquisada):** um PNG 1024x1024 sai da API com ~1 a 2 MB. Reduzido a ~256 px em WebP com perdas, uma figura de fundo liso deve ficar na faixa de 10 a 30 KB; 574 x ~20 KB ≈ 11 MB no APK. Isso esbarra no orçamento do APK (`tools/check-apk-budget.ps1`) e precisa ser medido. Vetorizar (traço) reduziria muito, mas a vetorização automática de imagens geradas costuma exigir limpeza manual.
- **Comparação com desenho procedural:** figuras desenhadas por código a partir de um esqueleto (ângulos de articulação por exercício) são consistentes por construção, ocupam poucos KB no total, são de autoria humana (o código e os dados de pose são protegidos e licenciáveis), cabem no design system (cores e tokens, tema escuro) e não dependem de fornecedor. O custo é escrever o renderizador e os ~574 conjuntos de pose; a fidelidade visual é menor. Uma via híbrida possível: usar a IA só para rascunhos de referência de pose e desenhar o resultado final por código (a IA não entra no asset distribuído).

### Gaps
- Não achei estudo revisado por pares sobre erros de forma em imagens de exercícios geradas por difusão; a evidência é sobre anatomia estática.
- Não achei números públicos de precisão de pose do FLUX com ControlNet, nem medições de consistência do Nano Banana Pro ou do gpt-image em centenas de imagens.
- Os preços são de agregadores; não li as páginas oficiais de preço da OpenAI e do Google.
- Tamanho por arquivo é estimativa minha, sem fonte; precisa de teste com amostras reais.

## 4. Políticas das lojas (Google Play, App Store)

### Takeaway
A política de conteúdo gerado por IA do Google Play trata de apps que **geram** conteúdo para o usuário (com prompts); um app que só exibe ilustrações estáticas pré-geradas fica fora do escopo. Não encontrei regra da App Store que restrinja assets gerados por IA. O Mooscles também é distribuído por APK fora das lojas, então o efeito prático hoje é pequeno.

### Cited Findings
- A política "AI-Generated Content" do Google Play cobre apps cuja saída vem de prompts de texto, voz ou imagem; exige impedir conteúdo ofensivo e oferecer denúncia. Não cobre apps que só hospedam conteúdo de IA, que só resumem conteúdo não gerado por IA, ou de produtividade em que a IA só melhora um recurso existente. — [Google Play Console Help](https://support.google.com/googleplay/android-developer/answer/14094294); [TechCrunch, 2024](https://techcrunch.com/2024/06/06/google-play-cracks-down-on-ai-apps-after-circulation-of-apps-for-making-deepfake-nudes)
- Em agosto de 2026 o Google endureceu sanções contra apps que tentam gerar ou monetizar imagens íntimas não consentidas. — [Android Developers Blog, 08/2026](https://android-developers.googleblog.com/2026/08/ensuring-safety-genai-preventing-non-consensual-intimate-content.html)

### Inferences
- Ilustrações pré-geradas embutidas no APK não fazem do Mooscles um "app de IA generativa" para a política do Play; não há exigência de botão de denúncia nem de rótulo por causa delas.

### Gaps
- Não pesquisei as App Store Review Guidelines de 2026 sobre conteúdo gerado por IA; não posso afirmar que não há exigência.
- Não confirmei, em fonte oficial do Google, as mudanças de política de abril de 2026 citadas por sites de terceiros.
