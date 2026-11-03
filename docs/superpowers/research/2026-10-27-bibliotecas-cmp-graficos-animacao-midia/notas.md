# Bibliotecas Kotlin/Compose Multiplatform (Android + iOS) para gráficos, animação, imagens e vídeo — Mooscles

Data da pesquisa: 2026-10-27. Metadados do GitHub (licença, estrelas, issues, pushed_at) vieram da API pública do GitHub nessa data. Tamanho binário: não consegui medir nenhum número em fonte confiável (ver Gaps).

## Gráficos (linha/barra/progressão, acessibilidade, animação)

### Takeaway
Vico (Apache-2.0) é o candidato mais ativo e já é KMP com iOS, tem animações nativas e acessibilidade para leitor de tela em desenvolvimento (PR aberto). KoalaPlot (MIT) está atualizado para Kotlin 2.4.10/CMP 1.11.1 e é a alternativa; compose-charts (MIT) é leve, mas sem acessibilidade documentada. Canvas próprio é a opção de maior controle sobre acessibilidade.

### Cited Findings
- Vico: "biblioteca de gráficos poderosa e extensível para Compose Multiplatform"; Apache-2.0; 3.199 estrelas; 5 issues abertas; último push 2026-10-26; tópicos incluem ios e compose-multiplatform — [GitHub API](https://api.github.com/repos/patrykandpatrick/vico)
- Vico: o módulo `vico/compose` é KMP com `iosArm64()` e `iosSimulatorArm64()`, mais Android, desktop JVM, js e wasmJs — [build.gradle.kts do módulo compose](https://api.github.com/repos/patrykandpatrick/vico/contents/vico/compose/build.gradle.kts)
- Vico releases: v3.3.1 (2026-08-28, fix de crash no Android 10), v3.3.0 (2026-08-16: animação de "line reveal", snap scrolling, tamanho por área de conteúdo, títulos de eixo), v3.3.0-next.2 (08-08), v3.3.0-next.1 (06-27), v2.5.2 (06-21). Notas dizem "Android is now Compose-first" e que o módulo `views` só recebe correções críticas — [Releases (API)](https://api.github.com/repos/patrykandpatrick/vico/releases?per_page=5)
- Vico: PR #1623 "add screen reader accessibility to Cartesian charts" está ABERTO (criado 2026-10-02, atualizado 2026-10-27); PR #1069 "Initial accessibility support in Compose" foi mergeado (2025-09-16) — [busca de issues](https://api.github.com/search/issues?q=repo:patrykandpatrick/vico+accessibility+OR+talkback+OR+semantics)
- KoalaPlot (koalaplot-core): MIT; 784 estrelas; 15 issues; push 2026-09-19. v0.12.0 (2026-07-25) atualizou para Compose 1.11.1, Kotlin 2.4.10, Coroutines 1.11.0; v0.12.1 (2026-08-08) — [Releases](https://api.github.com/repos/KoalaPlot/koalaplot-core/releases?per_page=4); [repo](https://api.github.com/repos/KoalaPlot/koalaplot-core)
- compose-charts (ehsannarmani/ComposeCharts): Apache-2.0; 915 estrelas; 30 issues; push 2026-07-30. 1.0.0 em 2026-07-21 (corrige `ClassCastException` no iOS em gradiente de linha); 0.2.5 (2026-02-12) usa Compose 1.10.1/Kotlin 2.3.10, adiciona opção de desligar animações; README lista Android, Desktop, iOS, WasmJS e não menciona acessibilidade — [Releases](https://api.github.com/repos/ehsannarmani/ComposeCharts/releases?per_page=4); [repo](https://api.github.com/repos/ehsannarmani/ComposeCharts); [README](https://api.github.com/repos/ehsannarmani/ComposeCharts/contents/README.md)
- Kandy (Kotlin/kandy): notas de release (v0.8.3, 2025-12-12) falam só de Kotlin Notebook, Jupyter e Gradle com DataFrame; nenhuma menção a iOS/mobile — não serve para app mobile — [Releases](https://api.github.com/repos/Kotlin/kandy/releases?per_page=3)
- AAY-chart: não consegui localizar o repositório (a API devolveu 404 para o caminho tentado) — [tentativa](https://api.github.com/repos/AAY-Chart/AAY-chart)
- CMP 1.12.2 é a versão atual; suportado iOS 14+, Android API 21+; CMP 1.8+ usa K2, exige Kotlin >= 2.1.0 e recomenda 2.2.20+ para iOS/web — [JetBrains: compatibilidade](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)

### Inferences
- Para o Mooscles (linha de progressão por exercício, barras de volume), Vico cobre o caso e anima; o risco é a acessibilidade ainda em PR aberto. Se o requisito de leitor de tela/texto grande for obrigatório hoje, é preciso desenhar em Canvas (com `semantics`/`contentDescription` por ponto e uma tabela/resumo textual alternativa) ou aguardar/contribuir com o PR #1623.
- Não verifiquei qual Kotlin/CMP o Vico 3.3.x compila (notas não dizem); precisa checar o `libs.versions.toml` do repo antes de fixar versão com Kotlin 2.4.x.
- KoalaPlot é o único gráfico com compatibilidade Kotlin 2.4.10 explícita nas notas.

### Gaps
- Acessibilidade real (TalkBack/VoiceOver, fonte grande) de KoalaPlot, compose-charts e Vico (hoje): não encontrei documentação; só o PR do Vico.
- Tamanho em KB/MB de cada biblioteca e issues específicas de iOS do Vico/KoalaPlot: não levantados.
- AAY-chart: repositório não encontrado.
- Versão exata do Vico compatível com Kotlin 2.4 / CMP 1.12: não confirmada.

## Animação (Compose nativo, Lottie, Rive, háptico, navegação)

### Takeaway
Para o app, animações do Compose comum (animate*AsState, AnimatedContent, etc.) bastam; Compottie (MIT, KMP, renderer puro Kotlin) é a escolha de Lottie e já acompanha CMP 1.12. Rive não tem runtime KMP oficial confirmado. Háptico: API comum `LocalHapticFeedback` existe, efeitos ricos exigem expect/actual ou biblioteca.

### Cited Findings
- Compottie: renderiza Lottie em Compose Multiplatform (Android, iOS, JVM, macOS, Web) com renderer próprio em Kotlin puro desde a v2.0; variante `compottie-lite` sem expressões do After Effects, com binário menor; módulo `compottie-network` — [klibs.io / busca](https://klibs.io/project/alexzhirkevich/compottie); [Maven Central](https://central.sonatype.com/artifact/io.github.alexzhirkevich/compottie/2.1.0)
- Compottie releases: 2.3.3 (05 Oct), 2.3.2 (27 Sep), 2.3.1 (03 Sep), 2.3.0 (26 Aug, "Compose 1.12"), 2.2.4 (24 Jun) (a página não mostra o ano; datas coerentes com 2026) — [Releases](https://github.com/alexzhirkevich/compottie/releases)
- Compottie: MIT; 698 estrelas; 3 issues abertas; push 2026-10-09; `libs.versions.toml` usa Kotlin 2.4.0 e Compose 1.12.1 — [repo](https://api.github.com/repos/alexzhirkevich/compottie); [versions.toml](https://api.github.com/repos/alexzhirkevich/compottie/contents/gradle/libs.versions.toml)
- Rive (rive-android): releases 11.15.0 (2026-10-22), 11.14.3, 11.14.2; notas não mencionam Kotlin Multiplatform — [Releases](https://api.github.com/repos/rive-app/rive-android/releases?per_page=3)
- Rive KMP: única opção encontrada é o KrossRive, 8 estrelas, sem licença, último push 2025-08-31 (não confiável para produção) — [busca GitHub](https://api.github.com/search/repositories?q=rive+kotlin+multiplatform&sort=updated)
- Háptico: `LocalHapticFeedback` (interface `HapticFeedback.performHapticFeedback(HapticFeedbackType)`) está no source set common do Compose UI — [composables.com](https://composables.com/jetpack-compose/androidx.compose.ui/ui/properties/LocalHapticFeedback/api). Um relato no Kotlin Slack usa `LongPress` no iOS e migra para Core Haptics para padrões customizados — [Kotlin Slack](https://slack-chats.kotlinlang.org/t/18928995/CHHapticPattern: 0x2822f5900)
- Bibliotecas terceiras de háptico: Jindong (DSL, iOS 13+), multihaptic, Pulsar (Software Mansion; iOS, Android, KMP) — [klibs jindong](https://klibs.io/project/compose-jindong/jindong); [klibs multihaptic](https://klibs.io/project/xfqwdsj/multihaptic); [Pulsar](https://docsearch.algolia.com/mcp/docs/repo/software-mansion/pulsar). Não verifiquei maturidade/licença.
- Navegação/shared elements/predictive back: em CMP, bibliotecas terceiras (Eungabi, Quo Vadis) descrevem predictive back no Android e swipe-back no iOS, com shared element comum — [Eungabi](https://klibs.io/project/euuungb/eungabi); [Quo Vadis](https://klibs.io/project/jermeyyy/quo-vadis). Sem confirmação oficial JetBrains.
- CMP 1.12 (lançado 2026-08-26, atual 1.12.2): Skia M144 -> M150, scroll de lazy layouts do iOS mais suave; nada sobre háptico/predictive back/shared elements nas notas do 1.12.2 — [What's new 1.12](https://kotlinlang.org/docs/multiplatform/whats-new-compose-112.html); [InfoWorld](https://www.infoworld.com/article/4216661/compose-multiplatform-1-12-0-welcomes-coding-agents-with-mcp-server.html)

### Inferences
- Sem número oficial, a economia de Compottie-lite vs completo só pode ser medida no próprio app.
- A API `animate*`/`AnimatedContent`/`SharedTransitionLayout` é parte do Compose comum, mas o suporte de shared elements com navegação no iOS e o predictive back iOS não foram confirmados em fonte oficial; tratar como "verificar com protótipo".

### Gaps
- Estado oficial de shared element transitions e predictive back em CMP 1.12 (iOS): não confirmado.
- Se existe runtime Rive KMP oficial: não encontrado; checar docs da Rive.
- Quais `HapticFeedbackType` o iOS do CMP 1.12 implementa: não verificado.

## Carregamento de imagens com cache em disco

### Takeaway
Coil 3 (Apache-2.0) é a escolha: muito ativo, KMP, alinhado a Kotlin 2.4.10/CMP 1.12. Limite importante: GIF/WebP animado só funciona no Android; no iOS aparece só o primeiro frame (issue aberta).

### Cited Findings
- Coil 3: Apache-2.0; 11.951 estrelas; 69 issues abertas; push 2026-10-25 — [repo](https://api.github.com/repos/coil-kt/coil)
- Changelog: 3.6.3 (2026-09-18), 3.6.2 (09-04), 3.6.1 (09-01), 3.6.0 (2026-08-26; Kotlin 2.4.10, Compose 1.12.0), 3.5.0 (2026-06-10; Kotlin 2.4.0, Compose 1.11.1; removeu alvos iosX64 e macosX64; minSdk 23), 3.4.0 (2026-02-24; Kotlin 2.3.10, Compose 1.9.3; passou a cachear respostas não-2xx elegíveis) — [CHANGELOG.md](https://raw.githubusercontent.com/coil-kt/coil/main/CHANGELOG.md)
- Docs de GIF do Coil: `coil-gif` "só disponível no Android"; `AnimatedImageDecoder` (API 28+) cobre WebP animado/HEIF; página não trata iOS — [Coil GIFs](https://coil-kt.github.io/coil/gifs/)
- Issue #2347 "Support animated image on iOS": ABERTA, labels enhancement/help wanted, atualizada 2026-03-05, 15 comentários; relata só o primeiro frame no iOS — [Issue API](https://api.github.com/repos/coil-kt/coil/issues/2347)
- Kamel: última release estável v1.0.9 (2026-01-22; Kotlin 2.3.0, Compose 1.10.0), prerelease com AVIF experimental (2026-01-27); sem release desde então nos 3 mais recentes — [Releases](https://api.github.com/repos/Kamel-Media/Kamel/releases?per_page=3)
- Landscapist (skydoves): 2.13.2 (2026-09-23) corrige crash de despejo do cache de disco no Kotlin/Native (iOS) e atualiza Coil 3 para 3.6.3; 2.13.0 (2026-09-09) reconstrói em `Modifier.Node`, adiciona `noDiskCache()` — [Releases](https://api.github.com/repos/skydoves/landscapist/releases?per_page=3)
- Alternativa de terceiros para animado: KiteImage (interop Coil; GIF/APNG/WebP animado em alvos CMP), v0.1.0 — maturidade baixa — [klibs.io](https://klibs.io/package/io.github.yuroyami/kiteimage-coil)

### Inferences
- Se o Mooscles precisar de demonstração animada de exercícios, não use GIF/WebP animado via Coil no iOS; use vídeo curto em loop ou Lottie.
- Landscapist é camada sobre Coil/outros; só vale se precisar de plugins (placeholder/shimmer); o bug de cache em disco no iOS corrigido em 2.13.2 mostra que cache em disco no Kotlin/Native já deu crash em outro wrapper — convém testar o cache do Coil em iOS.
- Offline: Coil usa `DiskCache` e cabeçalhos HTTP; comportamento offline não verificado em fonte.

### Gaps
- Licença do Kamel e Landscapist: não consultada (suponho Apache-2.0, não verificado).
- Controle de cache (tamanho máx., políticas) e comportamento offline em iOS: não levantado nas docs.
- Tamanho binário: não medido.

## Vídeo (clipes curtos em loop, autoplay mudo, memória)

### Takeaway
ComposeMediaPlayer (kdroidFilter, MIT) é a opção KMP mais completa (Media3 no Android, AVPlayer no iOS, loop, autoplay, cache de URI), mas é um projeto pequeno (506 estrelas) e não tem API de mute documentada (volume=0). Alternativa de menor risco: wrapper próprio com expect/actual sobre Media3/AVPlayer.

### Cited Findings
- ComposeMediaPlayer: MIT; 506 estrelas; 35 issues abertas; push 2026-08-28 — [repo](https://api.github.com/repos/kdroidFilter/ComposeMediaPlayer)
- Releases: v0.11.4 (2026-08-07, Kotlin 2.4.10), v0.11.2 (07-16), v0.11.1 (07-12), v0.11.0 (07-11; áudio; reescreve pipeline macOS; remove iosX64 do player de áudio) — [Releases](https://api.github.com/repos/kdroidFilter/ComposeMediaPlayer/releases?per_page=4)
- Documentação do vídeo: Android/iOS via Media3/AVPlayer (iOS/macOS via AVPlayer); `playerState.loop = true`; `openUri()` autoplay por padrão e `InitialPlayerState.PAUSE` para iniciar pausado; volume 0..1 (sem função mute documentada); cache opcional `CacheConfig(enabled=true, maxCacheSizeBytes=...)` padrão 100 MB LRU, só Android/iOS e só para `openUri` (não arquivos locais); HLS suportado; conteúdo/escala, fullscreen e metadados experimentais; sem orientação sobre memória — [README_VIDEO](https://raw.githubusercontent.com/kdroidFilter/ComposeMediaPlayer/master/README_VIDEO.MD)
- Outras: Chaintech ComposeMultiplatformMediaPlayer (v1.0.51, HLS, reels, YouTube), cmp-videoplayer (0.0.1, 2026-04-05, nova), Ruby KMP Player (sem cache/PiP na v1), mediaplayer-kmp (foco em YouTube) — [busca](https://github.com/Chaintech-Network/ComposeMultiplatformMediaPlayer); [klibs cmp-videoplayer](https://klibs.io/package/io.github.aryapreetam/cmp-videoplayer); [klibs ruby](https://klibs.io/project/rubywai/ruby_kmp_video_player)
- Observação: o README principal do ComposeMediaPlayer diz que iOS usa AVPlayer e a página de vídeo cita "via JNI" para iOS/macOS; essa frase é do resumo da ferramenta e parece inconsistente para iOS nativo, não confiar sem conferir o código.

### Inferences
- Para clipes curtos mudos em loop (demonstração de exercício), o requisito é simples: AVPlayerLayer/AVQueuePlayer com looper + Media3 `ExoPlayer` com `REPEAT_MODE_ALL` e volume 0. Um expect/actual próprio de ~100 linhas pode ser mais seguro que depender de lib pequena; decisão fica para a spec.
- Um clipe por tela com liberação do player em `DisposableEffect` limita memória; cache próprio dos MP4 é preciso (arquivos locais não entram no cache da lib).

### Gaps
- Chaintech: licença, última release e issues de iOS não verificados.
- Consumo de memória real, autoplay mudo em iOS (política de áudio com `playsInSilentMode`) e issues específicas de iOS do ComposeMediaPlayer: não levantados.
- Tamanho binário: não medido.
