# Frameworks multiplataforma para levar o Mooscles ao iOS (estado em outubro de 2026)

Escopo: comparar (a) apps nativos separados (Kotlin/Jetpack Compose + Swift/SwiftUI), (b) KMP + Compose Multiplatform (CMP), (c) KMP com lógica compartilhada e UI nativa em SwiftUI, (d) Flutter, (e) React Native + Expo, para equipe de 1–2 pessoas, app de diário de treino offline-first (banco relacional local, timer de descanso com notificação/alarme, geração de imagem para compartilhar, UI estilo Material, sem backend).

Contexto do código atual (levantado no repositório em 27/10/2026, por grep no módulo `app`): ~102 arquivos `.kt`; usa `androidx.room` (40 ocorrências), `androidx.datastore` (57), `androidx.work` (14), `AlarmManager` (10, incluindo `setExactAndAllowWhileIdle`), `java.time` (9), `android.graphics.Canvas`/`Bitmap.createBitmap` (geração de imagem de compartilhamento). Isso importa para a questão de reuso: Room, DataStore e ViewModel já têm versões KMP oficiais; WorkManager, AlarmManager, `java.time` e `android.graphics` são APIs só de Android/JVM e teriam de ir para `expect/actual` ou ser substituídos.

Observação de método: várias páginas primárias da JetBrains (blog.jetbrains.com) devolveram HTTP 403 ao fetch; nesses casos usei o título/resumo da busca ou resumos de terceiros e marquei. Itens marcados "(conhecimento prévio, não verificado nesta sessão)" vêm do meu treinamento (corte em jun/2026) e precisam de checagem.

## 1. Maturidade e estabilidade de cada opção em 2026; adotantes em produção

### Takeaway
As cinco opções estão em estado "estável/produção" em out/2026. CMP para iOS é estável desde a versão 1.8.0 (maio/2025) e está na 1.11 (maio/2026); a ponte Kotlin→Swift ("Swift Export") ainda é Alpha. Flutter está na 3.47 (ago/2026), com Material/Cupertino separados em pacotes próprios. React Native roda só na New Architecture desde a 0.82 (out/2025) e está na 0.88 (out/2026); o Expo SDK 58 acompanha a RN 0.88 e o iOS 27.

### Cited Findings
**Compose Multiplatform / KMP**
- CMP 1.8.0, lançado em maio/2025, declarou o iOS "Stable and Production-Ready" (título do post oficial) — [JetBrains blog, mai/2025](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/); repetido por [Neowin](https://www.neowin.net/news/jetbrains-brings-compose-for-ios-to-stable-with-compose-multiplatform-180-release/).
- A partir do 1.8.0, klibs nativos/web que dependem de CMP só podem ser consumidos com Kotlin 2.1.0+; o código do CMP migrou para o compilador K2 — [kotlinlang.org, What's new 1.8.2](https://kotlinlang.org/docs/multiplatform/whats-new-compose-180.html).
- No 1.8.2 vieram melhorias de iOS: anúncios de rolagem no VoiceOver, drag-and-drop e renderização concorrente — [kotlinlang.org](https://kotlinlang.org/docs/multiplatform/whats-new-compose-180.html).
- CMP 1.9.0 adicionou `PlatformImeOptions` para configurar atributos de entrada de texto nativos do UIKit — [kotlinlang.org, What's new 1.9.3](https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html) (via resumo de busca).
- CMP 1.11.0 (maio/2026) trouxe melhorias de iOS e web, uma nova abordagem de testes de UI e, como destaque no iOS, uma implementação experimental de entrada de texto nativa construída sobre UIView — [JetBrains blog, mai/2026](https://blog.jetbrains.com/kotlin/2026/05/compose-multiplatform-1-11-0/) (página deu 403; conteúdo vem do snippet da busca).
- Na KotlinConf 2026 (maio): CMP estável em mobile e desktop, Beta na web; Swift Export em **Alpha** (suspend → async do Swift, Flow → AsyncSequence); importação de dependências SPM em Alpha/Experimental; Kotlin Toolchain e Kotlin Language Server em Alpha — [Hiflylabs, jul/2026, resumo da KotlinConf](https://hiflylabs.com/blog/2026/7/9/kmp-kotlinconf-2026-native-where-it-matters-shared-where-it-counts); a página oficial de destaques do keynote ([JetBrains](https://blog.jetbrains.com/kotlin/2026/05/kotlinconf26-keynote-highlights/)) deu 403; a busca indica Swift Export indo para Alpha no Kotlin 2.4. Uma palestra da KotlinConf 2026 descreve SPM import como "Experimental as of Kotlin 2.4.0 RC" — [Speaker Deck, wisemuji](https://speakerdeck.com/wisemuji/kotlinconf-2026-what-nobody-told-us-about-shipping-kotlin-to-ios).
- O mesmo resumo cita SKIE (Touchlab) como "the safer bet" para interop iOS em produção hoje — [Hiflylabs](https://hiflylabs.com/blog/2026/7/9/kmp-kotlinconf-2026-native-where-it-matters-shared-where-it-counts).
- O Google diz que KMP para compartilhar lógica de negócio entre Android e iOS é "stable and production-ready" e oficialmente suportado. A página **não** declara suporte oficial do Google ao Compose Multiplatform (produto da JetBrains), mas o Android Studio tem Live Edit e Previews para CMP no `commonMain` — [developer.android.com/kotlin/multiplatform](https://developer.android.com/kotlin/multiplatform).
- Adotantes citados pelo Google: Blinkit, Cash App, Duolingo, Forbes, Google Docs, JioHotstar, Stone, Swiggy, Ultrahuman, Wrike, Zomato — [developer.android.com](https://developer.android.com/kotlin/multiplatform). Na KotlinConf 2026: McDonald's, Booking, Duolingo, Sony, Google (Google Docs em web e iOS com KMP) — [Hiflylabs](https://hiflylabs.com/blog/2026/7/9/kmp-kotlinconf-2026-native-where-it-matters-shared-where-it-counts).
- Números da JetBrains (KotlinConf 2026): uso de KMP entre os 10.000 maiores apps mobile "aproximadamente dobrou" desde mar/2025; klibs.io lista mais de 3.600 bibliotecas; builds Kotlin/Native no código do Google Docs 25% mais rápidos e com menos da metade da RAM em relação a um ano antes — [Hiflylabs](https://hiflylabs.com/blog/2026/7/9/kmp-kotlinconf-2026-native-where-it-matters-shared-where-it-counts) (números autodeclarados pela JetBrains).

**Flutter**
- Flutter 3.47 (post de 12/ago/2026): `material_ui` e `cupertino_ui` chegam a 1.0 no pub.dev como pacotes separados (opt-in); migração com `dart fix --apply --code=migrate_design_widgets`; as bibliotecas embutidas serão depreciadas no stable de novembro/2026; Widget Previews estáveis; Impeller vira padrão no desktop; mínimo iOS sobe de 13 para 15 (para suportar Xcode 27); suporte a Macs Intel sendo encerrado (avisos que virarão erro); 92 dos 100 principais plugins iOS já migraram para Swift Package Manager e CocoaPods está em modo de manutenção — [flutter.dev, What's new in Flutter 3.47](https://flutter.dev/blog/whats-new-in-flutter-3-47).
- Remoção das bibliotecas Material/Cupertino embutidas prevista para uma versão de 2027 — [freeCodeCamp/daily.dev](https://daily.dev/posts/how-to-work-with-material-and-cupertino-decoupling-in-flutter-full-handbook--r3kt6va11) (fonte secundária).
- Impeller como padrão no Android com Skia removido na 3.44 (Google I/O 2026) — [CMARIX](https://www.cmarix.com/blog/latest-flutter-version/) (fonte secundária, não confirmada na fonte oficial nesta sessão). Impeller é o renderizador padrão no iOS desde o Flutter 3.10 (2023) (conhecimento prévio, não verificado nesta sessão).

**React Native + Expo**
- RN 0.82 (8/out/2025) é a primeira versão que roda inteiramente na New Architecture; `newArchEnabled=false` e `RCT_NEW_ARCH_ENABLED=0` são ignorados; RN 0.81 / Expo SDK 54 foram as últimas versões a permitir a arquitetura legada — [reactnative.dev, RN 0.82](https://reactnative.dev/blog/2025/10/08/react-native-0.82); confirmado em [docs.expo.dev, New Architecture](https://docs.expo.dev/guides/new-architecture.md).
- Expo SDK 56 (21/mai/2026) com RN 0.85 e Hermes v1 como motor JS padrão; SDK 57 com RN 0.86 — [Expo changelog SDK 56](https://expo.dev/changelog/sdk-56), [SDK 57](https://expo.dev/changelog/sdk-57) (datas e detalhes via snippets de busca).
- Expo SDK 58 beta (15/set/2026): RN 0.88 RC, feito para iOS 27 (ciclo de vida UIScene; apps redimensionáveis), Xcode 27 troca o Simulator.app por "Device Hub", imagens do EAS Build com Xcode 27 "em breve", R8 ligado por padrão no Android, `expo-modules-core` com binários pré-compilados — [Expo changelog SDK 58 beta](https://expo.dev/changelog/sdk-58-beta).
- RN 0.88.0 estável em 15/out/2026, segundo a Wikipedia; newsletter de ~24/out/2026 relata Expo SDK 58 lançado com RN 0.88 e Expo Router com data loaders/SSR/middleware estáveis — [Wikipedia](https://en.wikipedia.org/wiki/React_Native), [This Week In React #302](https://dev.to/sebastienlorber/this-week-in-react-302-nextjs-react-router-devtools-security-rn-088-expo-58-navigation-1dk0). Fontes divergem sobre 0.88 ser final ou RC; não confirmei o post oficial de lançamento do SDK 58 estável.
- RN 0.87 tornou a "Strict TypeScript API" o padrão (muda tipos de refs e deep imports) — [This Week In React #302](https://dev.to/sebastienlorber/this-week-in-react-302-nextjs-react-router-devtools-security-rn-088-expo-58-navigation-1dk0).
- Expo UI: componentes SwiftUI (no SDK 58 ganharam `NavigationStack`, `Toolbar`, `NavigationSplitView`) e Jetpack Compose (`Image`, `DateRangePicker`, `VerticalSlider`) expostos para RN — [Expo SDK 58 beta](https://expo.dev/changelog/sdk-58-beta).

**Nativo (Kotlin/Compose + Swift/SwiftUI)**
- Não pesquisei a maturidade de Jetpack Compose e SwiftUI em si: são os toolkits de primeira parte do Google e da Apple; o projeto atual já está em Compose.

### Inferences
- Todas as opções são "maduras o suficiente" para um app de diário de treino. A diferença está no custo de migração, no ajuste de UI ao iOS e na ponte para APIs nativas, não na estabilidade básica.
- Para KMP, o elo mais fraco em 2026 continua sendo a interop com Swift (Swift Export em Alpha, SPM import experimental). Isso pesa mais na opção (c), em que a UI SwiftUI consome muitas APIs Kotlin, do que na (b), em que quase todo o código fica em Kotlin.
- O Flutter está no meio de uma transição (Material/Cupertino desacoplados, fim do Intel Mac, iOS mínimo 15), o que gera trabalho de migração em 2026–2027 para quem começar agora.

### Gaps
- Não consegui ler as notas oficiais completas do CMP 1.11 (403/404) nem confirmar se já saiu uma versão 1.12 entre maio e outubro de 2026.
- Não confirmei a data oficial do Expo SDK 58 estável nem o post de lançamento do RN 0.88 em reactnative.dev.
- Não achei números de 2025–2026 de pesquisas (Stack Overflow 2025, JetBrains State of Developer Ecosystem 2025) para participação de Flutter/RN/KMP; só achei Dart com 5,9% entre todos os respondentes do SO 2025 via [Quash (secundário)](https://quashbugs.com/blog/flutter-vs-react-native-statistics), e números de 2024 (Flutter 12,64% vs RN 12,57% entre todos) não verificados no site da Stack Overflow.

## 2. Esforço de desenvolvimento, tamanho de equipe, ferramentas (Mac/Xcode), build/CI

### Takeaway
Para 1–2 pessoas, as opções de UI única (b, d, e) cortam pela metade o trabalho de UI; (a) e (c) exigem saber SwiftUI e manter duas UIs. Em todas as opções é preciso Xcode (e, portanto, macOS) para compilar, assinar e depurar o app iOS; só o Expo/EAS (e serviços de CI na nuvem) permite gerar o binário iOS sem um Mac local.

### Cited Findings
- O plugin Kotlin Multiplatform (JetBrains) é o recomendado no Android Studio: wizard de projeto, checagem de ambiente, configurações de run/debug/test para iOS e Android e suporte básico a Swift (depuração entre linguagens, navegação, quick docs) — [developer.android.com](https://developer.android.com/kotlin/multiplatform).
- O plugin KMP roda em todos os sistemas (IntelliJ IDEA e Android Studio), com tooling de Compose, recursos de Swift e suporte ao AGP 9.0; o template padrão novo separa `shared` de `androidApp`/`iosApp`/`desktopApp`/`webApp` — [Hiflylabs](https://hiflylabs.com/blog/2026/7/9/kmp-kotlinconf-2026-native-where-it-matters-shared-where-it-counts).
- Mesmo com KMP, "multiplatform work still requires iOS expertise because Swift interop is still in progress" — [Hiflylabs](https://hiflylabs.com/blog/2026/7/9/kmp-kotlinconf-2026-native-where-it-matters-shared-where-it-counts) (opinião do autor).
- Flutter 3.47 está encerrando o suporte a Macs Intel como host e exige Xcode 27 para o iOS 27 — [flutter.dev](https://flutter.dev/blog/whats-new-in-flutter-3-47). Com Xcode 27, o Simulator.app é substituído por "Device Hub" — [Expo SDK 58 beta](https://expo.dev/changelog/sdk-58-beta).
- Expo: Expo Go para SDK 58 disponível via CLI e `eas go`; imagens do EAS Build com Xcode 27 "coming soon" (a imagem `latest` tinha Xcode 26.6 em set/2026) — [Expo SDK 58 beta](https://expo.dev/changelog/sdk-58-beta). Ou seja, o EAS compila iOS na nuvem, mas fica algumas semanas atrás do Xcode novo.
- Kotlin/Native (iOS) historicamente tem builds lentos; a JetBrains relata 25% mais rapidez e menos da metade da RAM no código do Google Docs em um ano — [Hiflylabs](https://hiflylabs.com/blog/2026/7/9/kmp-kotlinconf-2026-native-where-it-matters-shared-where-it-counts).
- Compilar para iOS exige Xcode, que só roda em macOS. No CI, isso significa runners macOS (GitHub Actions, Bitrise, Codemagic) ou o EAS Build da Expo — (conhecimento prévio, não verificado nesta sessão).

### Inferences
- O dono do projeto usa um MacBook (o caminho `/Volumes/Disco Macbook` indica isso), então a exigência de Mac já está atendida. O ganho do EAS de "não precisar de Mac" pesa pouco aqui.
- Esforço relativo para levar o app atual ao iOS (estimativa qualitativa): (b) é o menor, porque mantém Kotlin e Compose e porta a lógica para `commonMain`. (c) fica no médio: reaproveita a lógica, mas a UI iOS é escrita de novo em SwiftUI. (a) é o maior das opções com Kotlin, porque iOS vira um segundo app inteiro. (d) e (e) significam reescrever as duas plataformas.
- Para 1–2 pessoas, manter duas UIs (a, c) dobra o custo de cada recurso novo de tela; as opções de UI única (b, d, e) não têm esse custo.

### Gaps
- Não encontrei estudos de esforço quantificado (horas, linhas) comparando as cinco opções; o benchmark da Software Mansion diz explicitamente que não mediu esforço.
- Não verifiquei tempos de build de CI comparativos em 2026.

## 3. Desempenho, tamanho do app, inicialização, visual nativo no iOS, acessibilidade

### Takeaway
CMP e Flutter desenham a própria UI (Skia/Impeller), o que soma cerca de 9–11 MB ao app iOS e usa mais memória que UIKit/SwiftUI; RN e SwiftUI usam componentes nativos. A inicialização no iOS fica praticamente empatada entre CMP e RN; no Android, KMP/Compose ganha com folga de RN. Em CMP e Flutter, um visual Material no iOS sai "de graça", e um visual de iOS dá trabalho manual. Com RN/Expo UI e SwiftUI, o padrão de iOS sai de graça.

### Cited Findings
- JetBrains (CMP 1.8.0, mai/2025): inicialização próxima da nativa, rolagem no mesmo nível do SwiftUI, cerca de 9 MB a mais no tamanho do app em relação a um SwiftUI nativo (benchmark da própria JetBrains) — [JetBrains blog](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/) (via resumo da busca).
- Software Mansion (2/jul/2026), mesmo app em KMP (CMP 1.10.0 nas duas plataformas, render Skia no iOS) e em RN 0.81.5/Expo 54, builds de release, 3 Androids + 3 iPhones:
  - Tamanho: Android KMP 2,0 MB de download / 3,9 MB instalado contra RN 15,8 / 33,8 MB; iOS KMP 11,2 / 32,1 MB contra RN 9,9 / 29,7 MB.
  - Inicialização (TTID/TTFD, ms): Android KMP 158–245 / 617–750 contra RN 410–838 / 985–1535; iOS KMP 802–870 / 1203–1364 contra RN 822–1041 / 1209–1555.
  - Memória: Android KMP 59,7–121 MB contra RN 175,6–232,2 MB; iOS KMP 157–251,4 MB contra RN 44,7–131,4 MB (RN menor, atribuído ao render UIKit nativo).
  - FPS no Android: equivalente (56,7–58,7 contra 58,1–59,3); CPU inconclusiva.
  - Conclusão: nenhum é universalmente melhor. Viés: a Software Mansion vende consultoria de RN — [Software Mansion](https://swmansion.com/blog/we-built-the-same-app-in-kmp-and-react-native-here-s-what-we-found).
- Benchmark independente (2023, versões antigas): app simples com 1,7 MB em SwiftUI nativo, 17,9 MB em Flutter e 24,8 MB (CMP 1.4) / 32,2 MB (CMP 1.5) em CMP no iOS; o excesso é atribuído a empacotar o Skia no iOS — [Jacob Ras, Medium](https://medium.com/@jacobras/android-ios-native-vs-flutter-vs-compose-multiplatform-7ef3d5ec2a56), [GitHub jacobras/flutter-vs-native-vs-kmp](https://github.com/jacobras/flutter-vs-native-vs-kmp). **Dado antigo (2023)**; os números de 2026 da Software Mansion indicam um custo bem menor.
- Profiling individual: CMP no iOS usou quase o dobro de memória persistente de Swift (55,75 MiB contra 29,10 MiB); o inicializador do runtime Compose aparece como o símbolo mais pesado na inicialização — [Medium, zhuravl321](https://medium.com/@zhuravl321/swift-vs-compose-multiplatform-a-real-world-performance-comparison-dff90b0f8522) (data/versão não verificadas).
- Visual iOS no CMP: a interop melhorada permite usar elementos de Liquid Glass (iOS 26) — [zsmb.co, talk "What's new in CMP 2026"](https://zsmb.co/talks/whats-new-in-cmp-2026/) (via snippet); a entrada de texto nativa via UIView é experimental no 1.11 — [JetBrains blog 1.11](https://blog.jetbrains.com/kotlin/2026/05/compose-multiplatform-1-11-0/) (via snippet).
- Acessibilidade no CMP: VoiceOver anunciando rolagem e outras melhorias no 1.8.2 — [kotlinlang.org](https://kotlinlang.org/docs/multiplatform/whats-new-compose-180.html).
- Acessibilidade no Flutter 3.47: detecção automática de alto contraste e inversão de cores no Android, melhor ordenação semântica de `Text.rich` e bloqueio de foco com `BlockSemantics` — [flutter.dev](https://flutter.dev/blog/whats-new-in-flutter-3-47). O post não menciona iOS 26/Liquid Glass — [flutter.dev](https://flutter.dev/blog/whats-new-in-flutter-3-47).
- RN/Expo: Expo UI permite usar componentes SwiftUI reais (`NavigationStack`, `Toolbar`) e Jetpack Compose a partir de JS — [Expo SDK 58 beta](https://expo.dev/changelog/sdk-58-beta).

### Inferences
- Para um app com "UI estilo Material" como requisito, a desvantagem de CMP e Flutter (UI não nativa no iOS) quase não pesa: o produto *quer* o mesmo visual Material nas duas plataformas. O que ainda conta é o comportamento esperado no iOS: gesto de voltar pela borda, campos de texto, teclado, Dynamic Type, VoiceOver e haptics. É nesses pontos que CMP ainda tem partes experimentais.
- Um diário de treino não tem carga gráfica pesada. Em CMP e Flutter, 10 MB a mais e um uso de memória maior no iOS são aceitáveis; em RN, o custo de tamanho, inicialização e memória cai no Android, a plataforma onde o app já existe.

### Gaps
- Não encontrei benchmark independente de 2026 que inclua as cinco opções juntas (Flutter 3.4x, CMP 1.10+, RN 0.8x, nativo).
- Não verifiquei o estado de suporte a iOS 26 Liquid Glass no Flutter (o post da 3.47 não menciona).
- Não verifiquei auditorias de acessibilidade (VoiceOver/TalkBack) independentes para CMP no iOS.

## 4. Banco de dados local e notificações/timers em segundo plano por stack

### Takeaway
Banco relacional local está resolvido em todas as opções: Room KMP (oficial, iOS Tier 1) e SQLDelight no KMP, Drift/sqflite no Flutter, expo-sqlite/op-sqlite no RN. O ponto difícil é o timer de descanso: o iOS não deixa código rodar em segundo plano por tempo arbitrário, então o timer precisa virar uma notificação local agendada, ou um alarme via AlarmKit (iOS 26+) com Live Activity. Isso pede código nativo em Swift em todas as opções. O Flutter tem wrappers de AlarmKit no pub.dev; para RN e KMP não achei nenhum.

### Cited Findings
**Banco local**
- Jetpack com suporte KMP oficial do Google: Room (Android, iOS, JVM; sem Web), SQLite, DataStore, Lifecycle, ViewModel, Paging, SavedState, Collection, Annotation. Navigation, Navigation3, viewModel-compose e compose-runtime para iOS são da JetBrains. Na política de estabilidade, iOS é Tier 1: testado em CI inclusive em dispositivo, com compatibilidade de fonte e binária sob o versionamento semântico do Jetpack, na mesma barra de qualidade do Android — [developer.android.com/kotlin/multiplatform](https://developer.android.com/kotlin/multiplatform).
- SQLDelight (Cash App) é a alternativa KMP clássica, gerando Kotlin a partir de SQL; Drift e sqflite são as opções SQLite comuns em Flutter; Isar perdeu manutenção ativa do autor original; expo-sqlite é o módulo oficial da Expo; op-sqlite (JSI) e WatermelonDB são alternativas de desempenho em RN — (conhecimento prévio, não verificado nesta sessão; conferir status do Isar em especial).
- Expo SDK 58: o suporte a libSQL foi removido do expo-sqlite (`syncLibSQL()` e `libSQLOptions` saíram; `useLibSQL` depreciado) — [Expo SDK 58 beta](https://expo.dev/changelog/sdk-58-beta).

**Notificações e alarmes**
- Expo SDK 58: no Android, nova opção `delivery: 'alarmClock'` nos gatilhos de data/repetição, usando `AlarmManager.setAlarmClock()`, que as políticas de bateria dos fabricantes não adiam. Notificações em primeiro plano aparecem por padrão. Agrupamento via `threadIdentifier`. No iOS, coexistência com outras bibliotecas que registram `UNUserNotificationCenterDelegate` — [Expo SDK 58 beta](https://expo.dev/changelog/sdk-58-beta).
- Flutter: pacotes `alarm_kit` (v0.1.1) e `flutter_alarmkit` (v0.4.1) encapsulam o AlarmKit do iOS 26 (alarmes/timers que tocam no modo silencioso e com Focus ativo, Live Activity de contagem regressiva do sistema, UI customizável). Ambos são só iOS. Passando do limite de alarmes, o agendamento lança erro e é preciso cair para uma notificação. Depois de reiniciar o aparelho, as Live Activities só voltam após o primeiro desbloqueio — [pub.dev alarm_kit](https://pub.dev/packages/alarm_kit), [pub.dev flutter_alarmkit](https://pub.dev/documentation/flutter_alarmkit/0.4.1/).
- A busca não retornou wrappers de AlarmKit para React Native ou KMP — busca sem resultados nesta sessão.
- iOS: AlarmKit (iOS 26), ActivityKit/Live Activities e widgets são frameworks Swift-only. Em qualquer stack, a extensão de Live Activity/Widget precisa ser escrita em SwiftUI num target separado no Xcode. Em KMP, notificações locais podem ser agendadas a partir do Kotlin via `platform.UserNotifications` (interop Obj-C) ou em Swift — (conhecimento prévio, não verificado nesta sessão).

### Inferences
- O código atual usa `AlarmManager.setExactAndAllowWhileIdle` e WorkManager. No iOS o equivalente não é "rodar um timer em segundo plano": é agendar uma `UNNotificationRequest` para a hora em que o descanso termina (ou um alarme AlarmKit no iOS 26+) e, opcionalmente, mostrar uma Live Activity com contagem regressiva. Nas opções b/c/d/e isso vira uma interface comum (`RestTimerScheduler`) com implementação nativa em cada plataforma.
- Para o Mooscles, Room KMP é o caminho de menor atrito em (b) e (c): o esquema, os DAOs e as migrações atuais continuam praticamente iguais. Em (d) e (e), o esquema e as migrações teriam de ser reescritos em Drift ou SQL puro, com risco de divergir na migração de dados de usuários que já existem.
- Geração de imagem para compartilhar: hoje usa `android.graphics.Canvas`/`Bitmap`. Em CMP dá para renderizar um composable fora da tela para bitmap (GraphicsLayer/ImageBitmap, multiplataforma via Skia), e o share sheet nativo (`UIActivityViewController`) fica num `actual`. Em Flutter, `RepaintBoundary.toImage`; em RN, `react-native-view-shot` — (conhecimento prévio, não verificado nesta sessão).

### Gaps
- Não verifiquei a versão atual do Room KMP (2.7.x/2.8.x) nem limitações específicas no iOS (por exemplo, driver `BundledSQLiteDriver`).
- Não encontrei biblioteca KMP madura de notificações locais com suporte a AlarmKit/Live Activities; também não confirmei se o `expo-notifications` ou algum módulo Expo já expõe AlarmKit/Live Activities oficialmente.
- Não verifiquei o estado de manutenção do Isar e do WatermelonDB em 2026.

## 5. Risco de longo prazo: quem banca, saúde do ecossistema, contratação

### Takeaway
KMP agora conta com JetBrains e Google: o Google suporta oficialmente KMP para lógica e porta bibliotecas Jetpack com iOS em Tier 1, mas não declara suporte oficial ao Compose Multiplatform, que continua sendo da JetBrains. Flutter depende só do Google. RN tem Meta mais Expo, Microsoft e Software Mansion. O nativo (a) tem o menor risco de plataforma e o maior custo de equipe.

### Cited Findings
- O Google declara KMP para lógica de negócio "stable and production-ready" e suportado, com várias bibliotecas Jetpack mantidas pelo time Android para iOS — [developer.android.com](https://developer.android.com/kotlin/multiplatform). O CMP é produto da JetBrains; a página não diz que o Google o suporta oficialmente — [developer.android.com](https://developer.android.com/kotlin/multiplatform).
- O Google Docs roda em web e iOS com KMP — [Hiflylabs](https://hiflylabs.com/blog/2026/7/9/kmp-kotlinconf-2026-native-where-it-matters-shared-where-it-counts).
- Ecossistema KMP: mais de 3.600 bibliotecas no klibs.io, contra algumas centenas em 2022 — [Hiflylabs](https://hiflylabs.com/blog/2026/7/9/kmp-kotlinconf-2026-native-where-it-matters-shared-where-it-counts).
- Flutter: ritmo trimestral de releases estáveis; a separação de Material e Cupertino é justificada pela vontade de lançar esses pacotes fora do ciclo trimestral — [daily.dev](https://daily.dev/posts/material-and-cupertino-decoupling-are-here-nsjlzky7x) (secundário). 92 dos 100 principais plugins iOS já usam SwiftPM — [flutter.dev](https://flutter.dev/blog/whats-new-in-flutter-3-47).
- RN: a Microsoft mantém RN Windows (0.82 removeu a arquitetura Paper) — [Microsoft devblogs](https://devblogs.microsoft.com/react-native/?p=444); a Expo lança SDKs novos com cadência de poucos meses (SDK 56 em mai/2026, SDK 58 em out/2026) — [Expo changelog](https://expo.dev/changelog/sdk-56), [SDK 58 beta](https://expo.dev/changelog/sdk-58-beta).
- Contratação/popularidade: Dart com 5,9% entre todos os respondentes do Stack Overflow 2025, contra JavaScript com 66% e TypeScript com 43,6% — [Quash (secundário)](https://quashbugs.com/blog/flutter-vs-react-native-statistics). Dados de 2024 (não verificados na fonte): Flutter 12,64% e RN 12,57% entre todos; RN 13,62% e Flutter 12,56% entre profissionais — [Quash](https://quashbugs.com/blog/flutter-vs-react-native-statistics).

### Inferences
- Para uma equipe de 1–2 pessoas sem plano de contratar, o risco de contratação pesa menos que o risco de reescrita e de manter duas bases. Kotlin é a competência que a equipe já tem.
- O ritmo de mudanças incompatíveis é alto no RN/Expo (um SDK novo a cada ~3–4 meses, cada um com breaking changes como Strict TS e remoção de libSQL) e médio no Flutter (decoupling, iOS mínimo 15). No KMP/CMP, a mudança pesada passada foi a migração para K2/Kotlin 2.1 no CMP 1.8. Isso é custo recorrente para uma equipe pequena.
- O risco do CMP é de "segunda parte": se a JetBrains desacelerar o investimento no iOS, a lógica em KMP (com suporte do Google) sobrevive e a UI poderia migrar para SwiftUI, ou seja, a opção (b) se degrada para a (c). Esse caminho de saída é uma vantagem estrutural de (b) e (c) sobre (d) e (e).

### Gaps
- Não achei dados de 2025–2026 sobre vagas/contratação por framework de fontes primárias.
- Não achei números oficiais de 2026 de apps ou downloads de Flutter e RN (Google I/O 2026, React Conf 2025/2026).

## 6. Custo de cada opção para uma equipe com app já ~100% Kotlin/Compose (reescrita vs. reuso)

### Takeaway
(b) KMP+CMP reaproveita mais: banco Room, DataStore, ViewModels, regras de domínio e a maior parte das telas Compose, trocando só APIs Android-only. (c) reaproveita a lógica e exige uma UI SwiftUI nova. (a) é uma reescrita total no iOS sem compartilhar nada. (d) e (e) jogam fora o app Android atual e reescrevem as duas plataformas em Dart ou TypeScript.

### Cited Findings
- KMP "no complete rewrite required": dá para migrar aos poucos com o wizard de módulo compartilhado do Android Studio, que aplica o plugin Android-KMP — [developer.android.com](https://developer.android.com/kotlin/multiplatform).
- Room, DataStore, ViewModel, Lifecycle, SavedState, Paging e SQLite têm artefatos KMP oficiais com iOS Tier 1; Navigation/Navigation3 e compose-runtime para iOS vêm da JetBrains — [developer.android.com](https://developer.android.com/kotlin/multiplatform).
- O Android Studio oferece Previews do Compose a partir do `commonMain` e Live Edit em código compartilhado — [developer.android.com](https://developer.android.com/kotlin/multiplatform).
- O código atual usa Room (40), DataStore (57), WorkManager (14), AlarmManager (10), `java.time` (9) e `android.graphics.Canvas`/`Bitmap` — grep no repositório em 27/10/2026 (`/Volumes/Disco Macbook/Projetos-Github/Mooscles/app`).

### Inferences
- **(b) KMP + CMP**: mover entidades/DAOs Room, DataStore, ViewModels e domínio para `commonMain`; trocar `java.time` por `kotlinx-datetime`; isolar WorkManager/AlarmManager, notificações, share sheet e geração de bitmap atrás de `expect/actual` ou interfaces; telas Compose migram com troca de imports (Material3 do CMP) e de recursos (`Res` do CMP no lugar de `R`). Swift necessário só para o shell iOS, notificações/AlarmKit e Live Activity. Menor custo inicial e menor custo por recurso novo (uma UI).
- **(c) KMP + SwiftUI**: mesma migração da camada de dados/domínio, mais uma UI completa em SwiftUI e uma camada de interop (SKIE ou KMP-NativeCoroutines enquanto o Swift Export está em Alpha). Dá o melhor resultado de iOS nativo, mas cada tela nova é feita duas vezes; para 1–2 pessoas, pesado. Também contraria o requisito de "UI Material-style".
- **(a) Nativo separado**: o Android fica como está; o iOS é escrito do zero (SwiftUI + SwiftData/GRDB/Core Data, UserNotifications/AlarmKit), com regras de negócio, esquema e migrações reimplementados à mão. Maior custo e risco de divergência de regras entre as plataformas.
- **(d) Flutter / (e) RN+Expo**: reescrita total das duas plataformas, troca de linguagem (Dart/TS), reescrita do esquema e das migrações, e a migração de dados dos usuários Android atuais para o novo banco precisa ser tratada (ler o arquivo Room/SQLite existente). Só faz sentido se houver outros motivos (por exemplo, equipe JS/Dart, web).
- Ordem provável de custo total para o Mooscles: (b) < (c) < (a) ≈ (e) ≈ (d), sendo (d) e (e) os únicos que também mexem no app Android que já funciona.

### Gaps
- Não encontrei estudo de caso publicado de migração "app Android Compose → KMP+CMP" com métricas de reuso (% de código compartilhado), só afirmações gerais. Vale procurar estudos de caso no site da JetBrains (kotlinlang.org/case-studies), que não consegui acessar nesta sessão.
- Não verifiquei a compatibilidade de bibliotecas de terceiros específicas que o Mooscles possa usar (DI, imagens, gráficos) com KMP/iOS.
