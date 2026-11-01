# Mídia de exercícios para o Mooscles: fontes, licenças, formatos e hospedagem

Pesquisa em 2026-10-27, com ~20 chamadas. Vários pontos ficaram sem confirmação direta (ver Gaps). Nada aqui é aconselhamento jurídico.

## 1. Fontes de mídia abertas ou licenciáveis (licença, cobertura, qualidade)

### Takeaway
A única base com licença permissiva clara e mídia é a free-exercise-db (Unlicense, 800+ exercícios, JPG estáticos em 2 quadros, estilo fotográfico heterogêneo). wger tem licença Creative Commons por entrada (exige atribuição por item, possível share-alike). ExerciseDB e MuscleWiki não oferecem licença de redistribuição/cache da mídia, portanto não servem para empacotar ou hospedar por conta própria. Lottie gratuito tem licença que permite uso em app, mas proíbe redistribuir os arquivos soltos, o que conflita com repositório público.

### Cited Findings
- free-exercise-db (yuhonas): licença Unlicense, descrita como "Open Public Domain Exercise Dataset"; mais de 800 exercícios; imagens JPG numeradas por exercício (ex.: `Air_Bike/0.jpg`); sem exigência de atribuição declarada na página — [GitHub yuhonas/free-exercise-db](https://github.com/yuhonas/free-exercise-db)
- wger: código AGPL-3.0-or-later, documentação CC-BY-SA-4.0, e "Exercise/Ingredient Data: Creative Commons (see individual entries)", ou seja, licença por entrada; o README não trata imagens separadamente — [README wger](https://raw.githubusercontent.com/wger-project/wger/master/README.md)
- Docs antigas do wger (2.4) dizem que os dados iniciais são adicionalmente CC-BY-SA 3.0 e que algumas imagens vieram da Wikipedia (ver arquivos SOURCES nas pastas) — [wger 2.4 docs](https://wger.readthedocs.io/en/2.4/) (conteúdo obtido via resumo de busca; versão 3.0 vs 4.0 conflita entre fontes)
- Scrapers de terceiros indicam campos `licenseName`, `licenseUrl`, `licenseAuthor` por item no API do wger; a prática é exibir autor + licença + link junto de cada exercício — [Apify wger scraper](https://apify.com/crawlerbros/wger-exercise-scraper) (fonte secundária)
- ExerciseDB (exercisedb-api): repositório sob AGPL-3.0; anuncia 11.000+ exercícios, 15.000+ vídeos, 20.000+ imagens, 5.000+ GIFs; playground "não recomendado para produção", limites rígidos; link de Terms of Use externo; a página não declara licença da mídia — [GitHub ExerciseDB/exercisedb-api](https://github.com/ExerciseDB/exercisedb-api). O ToS não pôde ser lido (HTTP 429).
- MuscleWiki API: termos de 2026-01-28 licenciam chamar a API e exibir metadados, proíbem criar dataset/API concorrente e treinar IA, exigem crédito "Powered by MuscleWiki"; versão anterior (2025-11-14) proibia revender/redistribuir e armazenar/cachear mídia de longo prazo. Nenhuma concede direitos comerciais claros sobre os vídeos; licenciamento de conteúdo via contato — [MuscleWiki API Terms](https://api.musclewiki.com/api-terms), [versão 2025-11-14](https://musclewiki.com/api-terms)
- LottieFiles (Lottie Simple License): uso comercial em apps permitido, atribuição opcional; proíbe redistribuir como arquivos de animação avulsos, revender ou compilar para serviço concorrente; derivados mantêm a mesma licença; animações Pro têm licença premium separada; checar licença de cada animação — [Help LottieFiles](https://help.lottiefiles.com/hc/en-us/articles/900002438343-Can-I-use-a-free-animation-on-Lottiefiles-for-commercial-business-use)
- Bibliotecas pagas: não achei preços/termos do "Gym Visual". Referências análogas: pacote de 100+ animações 3D no Upwork com licença não exclusiva para uso em app por US$ 95; outro com MP4 1080p e licença comercial a partir de US$ 35; RepDB (dataset de exercícios) US$ 299–499 compra única, com hospedagem própria como alternativa — [Upwork 100 animações](https://www.upwork.com/services/product/video-audio-100-workout-and-exercise-animation-library-for-apps-coaches-2011072366940827674), [Upwork 3D](https://www.upwork.com/services/product/video-audio-premium-3d-workout-and-exercise-animations-with-anatomy-highlights-2011080495145043325), [RepDB](https://alternativeto.net/software/repdb/about/)

### Inferences
- Para um repositório público e app open-source, a free-exercise-db é o único caminho de custo zero com risco jurídico baixo, mas é foto estática (sem animação) e estilo inconsistente. Pode servir de ponto de partida/fallback.
- wger exige tratar cada imagem separadamente (autor, licença) e uma tela de créditos; CC-BY-SA pode impor share-alike às imagens (não ao código do app), o que é gerenciável mas trabalhoso.
- Lottie "free" e MuscleWiki/ExerciseDB são incompatíveis com "baixar sob demanda de um repositório público nosso", pois isso é redistribuição.
- Pacotes licenciados de US$ 35–500 são viáveis, mas a licença "uso em app" provavelmente não cobre publicar os arquivos em repositório público; precisaria de cláusula explícita (não verificado).

### Gaps
- Termos completos do ExerciseDB (429) e licença da mídia dele.
- Licença efetiva das imagens do wger (3.0 vs 4.0, por imagem) e proporção de imagens com autor próprio; não consegui consultar o API.
- Origem/licença das fotos da free-exercise-db (o Unlicense do repositório pode não cobrir direitos de terceiros sobre as fotos originais; não verificado).
- Gym Visual (preço, licença), Openverse/Wikimedia Commons (cobertura e consistência), e outros vendors 3D: não pesquisados a fundo.

## 2. Gerar estilo próprio (IA ou encomenda): aspectos legais e custo

### Takeaway
Saída 100% gerada por IA não é protegida por copyright nos EUA; é seguro usar, mas não dá para impedir cópia. Obra com edição/arranjo humano significativo pode ter proteção parcial. Custos não foram levantados com fonte sólida.

### Cited Findings
- Relatório Part 2 do US Copyright Office (29/01/2025): exige autoria humana; material totalmente gerado por IA não é protegível; prompts sozinhos não bastam; entradas humanas perceptíveis na saída, seleção/arranjo criativo e modificações humanas originais podem ser protegidas, caso a caso — [Copyright Alliance](https://copyrightalliance.org/ai-report-part-2-copyrightability/), [Dykema](https://dykema.com/news-insights/the-future-of-creativity-us-copyright-office-clarifies-copyrightability-of-ai-generated-works.html)
- Faixa de preço de mercado para encomenda: pacotes prontos de 100 animações 3D por US$ 35–95 (licença não exclusiva) — [Upwork](https://www.upwork.com/services/product/video-audio-100-workout-and-exercise-animation-library-for-apps-coaches-2011072366940827674)

### Inferences
- Para um app open-source, falta de copyright sobre as imagens não é problema (queremos distribuir livremente); o risco real é os termos de uso do gerador escolhido e a possibilidade de a saída reproduzir material de terceiros.
- Encomenda a ilustrador/animador com cessão ou licença CC0/CC-BY explícita dá o resultado mais limpo e consistente de estilo.

### Gaps
- Termos de uso comercial dos geradores específicos (Higgsfield, etc.), regras da UE/Brasil, preços de ilustrador por exercício (~300 exercícios): não pesquisados.
- Políticas das lojas (Play/App Store) sobre conteúdo gerado por IA: não verificadas.

## 3. Formatos: WebP animado vs MP4 vs Lottie vs sprite

### Takeaway
No Android, WebP animado exige API 28+ via `AnimatedImageDecoder` (Coil); GIF funciona em todas as versões, porém mais lento. O suporte do Coil a animação é só Android, então Compose Multiplatform/iOS precisa de outra solução (não confirmada). Tamanhos por exercício não foram medidos com fonte.

### Cited Findings
- `ImageDecoder` (API 28) devolve `AnimatedImageDrawable` para WebP/GIF animados — [Android Developers](https://developer.android.com/reference/kotlin/android/graphics/ImageDecoder)
- Coil: `AnimatedImageDecoder` (API 28+) lida com GIF, WebP animado e HEIF animado; `GifDecoder` funciona em todas as APIs, mais lento; "esta feature só está disponível no Android"; dependência `coil-gif:3.6.3` — [Coil GIFs](https://coil-kt.github.io/coil/gifs/)

### Inferences
- Se o minSdk do Mooscles for menor que 28, é preciso fallback (GIF ou quadros estáticos). Verificar o minSdk no `build.gradle.kts` antes de decidir.
- Alternativa multiplataforma plausível: 2 quadros estáticos (como a free-exercise-db) alternados com animação Compose, que não depende de decoder nativo; ou vídeo curto em loop via player por plataforma. Ambas são hipóteses não verificadas aqui.
- Lottie só serve se o estilo for vetorial e feito sob medida; as gratuitas conflitam com redistribuição (seção 1).

### Gaps
- Tamanho típico por exercício em WebP/MP4/AV1/Lottie, suporte de iOS (WebP animado em ImageIO/UIImage), e bibliotecas Compose Multiplatform (Lottie compose-multiplatform, Coil 3 em iOS, players de vídeo): não pesquisados.

## 4. Hospedagem gratuita/barata sob demanda com integridade

### Takeaway
Melhor custo-benefício sem recorrente: GitHub Releases (sem limite declarado de banda/total, 2 GiB por arquivo, até 1000 assets) com hash sha256 num manifesto, o que combina com o fluxo de `update.json` que o app já usa. Cloudflare R2 (egress grátis, 10 GB grátis) é a reserva. GitHub Pages tem teto suave de 100 GB/mês e restrições de uso comercial. Bunny é pago (mínimo US$ 1/mês).

### Cited Findings
- GitHub Releases: cada arquivo < 2 GiB; até 1000 assets por release; sem limite de tamanho total nem de banda segundo a documentação — [GitHub Docs about releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases) (resumo de busca; discussão: [community #61733](https://github.com/orgs/community/discussions/61733))
- GitHub Pages: site publicado até 1 GB, repositório recomendado 1 GB, banda com limite suave de 100 GB/mês, 10 builds/hora (suave), pode retornar 429; não permitido como hospedagem gratuita de negócios online/e-commerce/SaaS comercial — [GitHub Pages limits](https://docs.github.com/en/pages/getting-started-with-github-pages/github-pages-limits)
- jsDelivr: serviço gratuito, "sem limites de banda"; arquivos do GitHub > 20 MB e pacotes > 150 MB não são suportados por padrão; conteúdo é cacheado permanentemente (remoção só por razões graves; purge limitado e por e-mail); não ficou confirmado suporte a release assets nem os termos comerciais — [jsDelivr README](https://github.com/jsdelivr/jsdelivr)
- Cloudflare R2: Standard US$ 0,015/GB-mês; free tier 10 GB-mês, 1 M ops Classe A, 10 M ops Classe B por mês; egress gratuito — [Cloudflare R2 pricing](https://developers.cloudflare.com/r2/pricing/)
- Bunny CDN: US$ 0,01/GB (Europa e América do Norte), 0,03 Ásia/Oceania, 0,045 América do Sul, 0,06 Oriente Médio/África; mínimo mensal US$ 1; teste de 14 dias — [Bunny pricing](https://www.bunny.net/pricing/cdn/)

### Inferences
- Para 300 exercícios, o conjunto cabe folgado em qualquer free tier: 300 × 100 KB–1 MB = 30–300 MB (estimativa minha, sem fonte de tamanho real). R2 grátis (10 GB) comporta isso; Releases também.
- Integridade: publicar `media-manifest.json` com sha256 e tamanho por arquivo (ou um pacote zip por grupo muscular), verificando no app como o `ApkVerifier` já faz para o APK; manifesto versionado por tag evita cache incorreto.
- Cache: baixar sob demanda para armazenamento privado do app, nunca re-baixar se o sha256 bate; offline mostra o que já foi baixado e um placeholder para o resto; pacote opcional "baixar tudo no Wi-Fi".
- GitHub Releases por URL de download tem redirecionamento e cabeçalhos de cache próprios do GitHub (não verificado), e a ToS sobre uso como CDN de assets de app não foi lida; risco baixo para app pequeno, mas R2 dá mais controle de cache headers.

### Gaps
- Cabeçalhos de cache/redirecionamento de `github.com/.../releases/download`, e se a ToS do GitHub restringe uso como CDN de app.
- Termos de uso do jsDelivr para release assets e o limite de 20 MB aplicado a assets.
- Exigência de cartão para ativar R2 e política atual de r2.dev (limitação de taxa em domínio de desenvolvimento): não verificada.

## 5. Estimativa de download e recomendação

### Takeaway
Sem medições reais, a estimativa é de dezenas a poucas centenas de MB para ~300 exercícios, dependendo do formato; recomendação provisória abaixo, a validar com um piloto.

### Cited Findings
- (Sem dados citáveis de tamanho; ver Gaps.)

### Inferences
- Recomendação provisória, em ordem: (1) começar com a free-exercise-db (Unlicense) como cobertura imediata, mapeando os exercícios do catálogo por nome; (2) para qualidade e consistência, encomendar ou gerar um conjunto próprio em estilo único (ilustração/WebP animado curto), com licença CC0/CC-BY explícita e registrando o processo; (3) hospedar em GitHub Releases com manifesto sha256 (alinhado ao fluxo atual de atualizações) e R2 como alternativa se houver limites; (4) evitar ExerciseDB, MuscleWiki e Lottie gratuito por restrições de redistribuição/cache.
- Em app open-source, assets licenciados com restrição não devem entrar no repositório público; manter os arquivos fora do git e só o manifesto dentro.

### Gaps
- Medição de tamanho real por formato; cobertura por equipamento/músculo da free-exercise-db versus o catálogo do Mooscles; custo efetivo de encomenda para ~300 exercícios.
