# Animação e desenho de figuras de exercício em Compose Multiplatform (Android + iOS)

Contexto do Mooscles usado nesta análise (lido do repositório em 2026-10-29, não da web):

- Orçamento de APK: `tools/apk-budget.json` tem `maxGrowthBytes = 1048576` (1 MiB) sobre `baseApkBytes = 23119851` (APK debug depois da fase 2).
- O APK distribuído é o **debug**, sem R8 (`isMinifyEnabled = true` em `app/build.gradle.kts` vale só para o build type de performance). `minSdk = 26`, e no APK debug local os `classes*.dex` ficam comprimidos (Deflate), conferido com `unzip -v`.
- O projeto já usa `kotlinx-serialization-json` 1.12.0 (no `:shared`) e já tem `rememberSystemReducedMotion()` no `:designsystem` (`theme/ReducedMotion.kt`, com `actual` que lê `ANIMATOR_DURATION_SCALE` no Android e `UIAccessibilityIsReduceMotionEnabled()` mais a notificação de mudança no iOS).
- Alvo: Compose Multiplatform 1.12.x, Kotlin 2.4.21.

## Q1. Runtimes de animação (Compottie/Lottie, Rive, Spine, DragonBones, bibliotecas Kotlin de rig/IK): licença, suporte CMP/iOS, manutenção, tamanho, autoria e desempenho

### Takeaway
Nenhum runtime cabe bem. O Compottie é o único em Kotlin puro e já acompanha o CMP 1.12. Mesmo a variante `lite` custaria cerca de metade do orçamento de 1 MiB (eu mesmo medi uns 0,55 MB de dex comprimido), e ainda exigiria autoria em After Effects ou num editor Lottie, ou então gerar JSON Lottie à mão. O Rive só chega ao CMP por um wrapper comunitário experimental, com binário nativo de vários MB, bem acima do orçamento. O Spine exige licença paga do editor, e o DragonBones não tem runtime para CMP. Também não achei biblioteca Kotlin de rig/IK para Compose.

### Cited Findings

**Compottie (Lottie para Compose Multiplatform)**
- Licença MIT. Plataformas: Android, iOS, JVM desktop, macOS e Web. Desde a v2.0 tem "its own multiplatform rendering engine without any platform delegates" (renderizador em Kotlin puro) — [GitHub alexzhirkevich/compottie](https://github.com/alexzhirkevich/compottie)
- Módulos: `compottie` (motor + `JsonString`), `compottie-lite` (sem expressões, "binary about 2x smaller"), `compottie-dot` (dotLottie/ZIP), `compottie-network`, `compottie-network-core` e `compottie-resources` (Compose resources). Os módulos `dot` e `network` pedem desugaring abaixo do minSdk 26 — [README](https://github.com/alexzhirkevich/compottie)
- Propriedades dinâmicas (`rememberLottieDynamicProperties`) alteram camadas existentes em tempo de execução: fills, cores, alpha, rotação, transforms e shapes, com curingas. Os builders rodam a cada frame. O README não descreve criar animações do zero em código — [README](https://github.com/alexzhirkevich/compottie)
- Recursos suportados: shape layers, grupos, trim path, repeater, gradientes, stroke (cap, join, dashes), transform (position, anchor, scale, opacity) com **parenting**, interpolação linear/bezier/hold, máscaras, mattes alpha, blur, texto, expressões, precomps, time remap. Sem suporte: luma mattes, vários efeitos e text path. Merge paths só sob flag experimental — [supported_features.md](https://github.com/alexzhirkevich/compottie/blob/master/supported_features.md)
- Releases: 2.3.0 (26 ago) "Compose updated to 1.12". A mais recente é a 2.3.3 (05 out), com correções de keyframes nas bordas da reprodução. Entre 2.2.x e 2.3.x houve várias correções de renderização (paths corrompidos desde a 2.2.0, gradientes, fill type, escala negativa). A 2.2.0 dependia por acidente do Skiko no Android, corrigido na 2.2.1 — [Releases](https://github.com/alexzhirkevich/compottie/releases)
- Sinais de manutenção: cerca de 698 stars, 409 commits, 3 issues abertas no momento da consulta — [GitHub](https://github.com/alexzhirkevich/compottie)
- Maven Central confirma a 2.3.3 como `latest`/`release` de `compottie-android`, `compottie-lite-android`, `compottie-core-android`, `compottie-dot-android` e `compottie-resources-android` — [maven-metadata compottie-android](https://repo1.maven.org/maven2/io/github/alexzhirkevich/compottie-android/maven-metadata.xml)
- O POM da 2.3.3 mostra que `compottie-lite-android` depende de `compottie-core-android` (AAR de 1.757.859 bytes, `classes.jar` com 829 entradas e 4,7 MB descomprimido). O core puxa `keight-core-android` 0.0.07 (88.575 bytes), `kotlinx-serialization-json` 1.9.0, `okio` 3.16.4, `atomicfu` e `androidx.collection`. O `compottie-android` (completo) soma ainda `keight-android` 0.0.07 (1.691.415 bytes, o motor de expressões) — [POM compottie-core-android 2.3.3](https://repo1.maven.org/maven2/io/github/alexzhirkevich/compottie-core-android/2.3.3/compottie-core-android-2.3.3.pom); [POM compottie-android 2.3.3](https://repo1.maven.org/maven2/io/github/alexzhirkevich/compottie-android/2.3.3/compottie-android-2.3.3.pom)
- **Medição própria** (d8 do build-tools 36.0.0, `--debug --min-api 26`, só os jars do Compottie e do keight, sem as dependências transitivas que o app já tem): `core + keight-core + lite` gera 1.661.224 bytes de dex, 562.651 bytes com zip -9. `core + keight-core + full + keight` gera 3.205.920 bytes de dex, 1.086.074 bytes com zip -9. São estimativas: o empacotamento do AGP comprime com outro nível e divide o dex de outro jeito. Os arquivos baixados de [Maven Central](https://repo1.maven.org/maven2/io/github/alexzhirkevich/) ficaram no scratchpad da sessão.
- Não há números oficiais de tamanho além do "2x smaller" do `lite` — [README](https://github.com/alexzhirkevich/compottie)

**Rive**
- Não existe runtime oficial do Rive para CMP. A documentação do Rive lista o Rive-CMP como runtime comunitário e diz que ele "not a Rive owned or maintained project". O wrapper embrulha rive-android, rive-ios e `@rive-app/canvas` — [Rive docs: Rive-CMP](https://rive.app/docs/runtimes/community-runtimes/rive-cmp.md)
- Rive-CMP (muazkadan): Apache-2.0, versão 0.6.0 pelos snippets de instalação. Exige CMP 1.12+, Kotlin 2.4+, AGP 9.3.2+ e Gradle 9.7+. No iOS integra o rive-ios por SPM (spm4kmp), pede Xcode 26.3+ e às vezes adicionar o RiveRuntime à mão no Xcode. O README diz "currently in an experimental state", com APIs que podem mudar, e avisa que nem todos os recursos nativos funcionam em todas as plataformas. Na consulta tinha cerca de 119 stars e 6 issues abertas — [GitHub muazkadan/Rive-CMP](https://github.com/muazkadan/Rive-CMP)
- Pelo mvnrepository a 0.4.0 é de 19 mar 2026 (CMP 1.10.3). Há relato de que o `UIKitView` no iOS não suporta fundo transparente, com correção citada na issue #42. Não confirmei se ela entrou na versão atual — [mvnrepository rive-cmp 0.4.0](https://mvnrepository.com/artifact/dev.muazkadan/rive-cmp/0.4.0)
- Tamanho oficial (jan 2026, app vazio): no Android, ARM-v8a tem 2,40 MB de download e 7,03 MB instalado, e ARM-v7a tem 2,32 MB e 6,00 MB. Entram o `librive-android.so` (bindings C++, runtime, Rive Renderer), o dex e as dependências (Compose, Lifecycle, Startup, ReLinker, Volley). A libc++ conta à parte: 394 KB de download e 1,2 MB instalado em ARM-v8a. No iOS universal são cerca de 1,67 MB de download e 4,66 MB instalado — [Rive runtime sizes](https://rive.app/docs/runtimes/runtime-sizes.md)
- O tamanho do APK com Rive depende muito da versão do NDK: há relato de diferença de dezenas de MB no Flutter. É anedótico — [busca: itnext/Flutter Rive size](https://itnext.io/how-i-drastically-reduced-my-flutter-app-size-by-80mb-with-just-one-line-of-code-967ba19cef98)
- Autoria só no editor Rive. O plano Free exporta `.riv` com splash screen do Rive ("Free exports play a Rive splash screen"). O Cadet, a US$ 9 por seat por mês, tira o splash — [Rive pricing](https://rive.app/pricing)

**Spine (Esoteric Software)**
- A licença dos runtimes permite integrá-los em software nos termos da Seção 2 da Spine Editor License Agreement. Fora disso, "each user of the Products must obtain their own Spine Editor license" — [Spine Runtimes License](https://esotericsoftware.com/spine-runtimes-license)
- Preços: Essential US$ 69 (normal US$ 99; sem meshes nem recursos avançados), Professional US$ 379 (normal US$ 449) e Enterprise US$ 2.499 + US$ 379 por usuário por ano (obrigatória acima de US$ 500 mil de receita ou financiamento). Com a licença vencida, pode-se continuar distribuindo produtos já feitos, mas não integrar os runtimes em produtos novos — [Spine purchase](https://esotericsoftware.com/spine-purchase)

**DragonBones e outros**
- Não achei runtime DragonBones para Compose ou CMP. O que existe em Kotlin é um port para o motor KorGE (artefato `korge-dragonbones`), feito pelo autor do KorGE a partir do runtime TypeScript+PIXI — [soywiz: Ported DragonBones to Kotlin and Korge](https://soywiz.com/ported-dragonbones-to-korge/); [Maven Central korge-dragonbones](https://central.sonatype.com/artifact/com.soywiz.korlibs.korge2/korge-dragonbones-iosarm32/)
- Os outros runtimes DragonBones encontrados são Dart (pub.dev `dragonbones`, StageXL) — [pub.dev dragonbones](https://pub.dev/packages/dragonbones); [StageXL_DragonBones](https://github.com/bp74/StageXL_DragonBones)
- A busca por "Compose skeletal animation" só devolveu bibliotecas de skeleton loading (shimmer) e o Compottie — [klibs.io compose-skeleton-shimmer](https://klibs.io/package/io.github.ebinjoy999/compose-skeleton-shimmer)

### Inferences
- **Compottie lite**: a estimativa de cerca de 0,55 MB comprimido gasta mais ou menos metade do orçamento de 1 MiB antes de qualquer arte. O `full` (cerca de 1,04 MB) sozinho já estoura o orçamento. O okio e o serialization transitivos provavelmente se sobrepõem ao que o app já tem (DataStore e `:shared`), mas não medi a sobreposição. Num app distribuído sem R8 não há tree-shaking: entra a biblioteca inteira.
- Com Compottie, o "dado" seria JSON Lottie. Dá para gerar JSON Lottie por código (é um formato JSON documentado), mas montar um rig com parenting e keyframes à mão em Lottie é mais trabalhoso e mais frágil do que um modelo próprio de ângulos de junta. As propriedades dinâmicas mudam valores de camadas que já existem, não constroem esqueletos.
- **Rive**: o binário nativo (vários MB instalado, cerca de 2,4 MB de download só no ARM64) está muito acima do orçamento. Somam-se o wrapper comunitário experimental, a dependência de SPM no iOS e o editor obrigatório, pago para tirar o splash. Fica descartado.
- **Spine**: a licença paga por usuário do editor e a falta de runtime CMP o descartam. Para dois keyframes de um rig simples, é um exagero.
- Não existe hoje (out 2026) runtime de esqueleto 2D maduro para CMP. A menor opção funcional é escrever o FK em Compose.

### Gaps
- Não achei número publicado de impacto do Compottie no APK. O número acima é medição própria aproximada (d8 isolado, sem build real do app). Para confirmar, seria preciso montar um APK com a dependência e rodar `tools/check-apk-budget.ps1`.
- Não medi o impacto do Compottie no binário iOS (klib/framework).
- Não achei runtime oficial `spine-android`/CMP nem o estado do Spine em Kotlin Multiplatform nas fontes consultadas.
- Não achei biblioteca Kotlin/KMP de cinemática inversa 2D (busca sem resultado relevante). Também não verifiquei o desempenho medido de Compottie ou Rive em aparelhos.
- Não há data exata do último commit do Rive-CMP. O estado da issue #42 (fundo transparente no iOS) na 0.6.0 não foi verificado.

## Q2. Compose puro (Canvas/DrawScope + Path + animateFloat/Transition) para uma figura FK de 2 keyframes: o que oferece e quanto custa

### Takeaway
O Compose comum já tem tudo de que a figura precisa. `rememberInfiniteTransition` + `animateFloat` com `infiniteRepeatable(..., RepeatMode.Reverse)` dá o vaivém início→fim→início, e um único progresso `t` interpola todos os ângulos. O `Canvas`/`DrawScope` desenha segmentos e formas, e com o modo reduzido basta mostrar uma pose estática ou trocar sem interpolar. O custo extra de APK é praticamente zero: só código próprio, algumas centenas de linhas. Não achei exemplo publicado de figura FK em Compose; a técnica é FK padrão.

### Cited Findings
- `rememberInfiniteTransition` cria animações filhas que começam ao entrar na composição e rodam até sair. `animateFloat` com `infiniteRepeatable(animation = tween(...), repeatMode = RepeatMode.Reverse)` vai e volta a cada ciclo — [Android Developers: Value-based animations](https://developer.android.com/develop/ui/compose/animation/value-based)
- `Animatable` é a API de corrotina por trás do `animate*AsState`. `animateTo`/`snapTo` rodam num `LaunchedEffect`, e `snapTo` pula direto para um valor, o que serve ao modo reduzido. `updateTransition` anima vários valores juntos entre estados (por exemplo, um enum `Start`/`End`), com `transitionSpec` por mudança de estado — [Android Developers: Value-based animations](https://developer.android.com/develop/ui/compose/animation/value-based)
- `animate*AsState` recompõe a cada frame durante a animação. A página não fala de leitura do valor só na fase de desenho — [Android Developers: Value-based animations](https://developer.android.com/develop/ui/compose/animation/value-based)
- O `Canvas` do Compose serve para desenhar e animar direto na UI, e há exemplos que animam paths no Canvas com transição infinita (bússola) — [ProAndroidDev: Exploring Canvas in Jetpack Compose](https://proandroiddev.com/exploring-canvas-in-jetpack-compose-crafting-graphics-animations-and-game-experiences-b0aa31160bff); [fabirt/jetpack-compose-lab](https://github.com/fabirt/jetpack-compose-lab)
- A busca por "Compose Canvas stick figure forward kinematics" não achou fonte específica — [mesma busca, resultados genéricos](https://canopas.com/jetpack-compose-custom-animations-550dcdcded83)
- No CMP para iOS, o "Reduce Motion" do sistema passou a ser aplicado a partir do 1.8.0, segundo um único relato informal no Slack do Kotlin. Não confirmei nas notas oficiais — [Kotlin Slack compose-ios](https://slack-chats.kotlinlang.org/t/28522057/hey-all-i-was-attempting-to-update-our-project-from-1-7-3-to)
- Não existe API cross-platform de "duration scale" no CMP. No Android a escala é configuração global do sistema — [Kotlin Slack](https://slack-chats.kotlinlang.org/t/27151778/has-anyone-found-a-reliable-way-to-slow-down-all-animations-); [dev.to: Android animations and reduced motion](https://dev.to/eevajonnapanula/android-animations-and-reduced-motion-2em2)

### Inferences
- **Desenho de um renderer FK mínimo** (técnica padrão, sem fonte específica):
  - dados: um esqueleto fixo, com ossos de comprimento proporcional (tronco, cabeça, braço, antebraço, coxa, perna, pé) e pai por osso, e uma `Pose` com raiz (posição + ângulo) e um ângulo relativo por junta;
  - FK: percorrer em ordem de pai para filho acumulando ângulo e posição (`x += len*cos(a)`, `y += len*sin(a)`);
  - interpolação: `lerp(start[j], end[j], t)` por junta, com o ângulo pelo caminho mais curto (atenção ao wrap de ±180°);
  - desenho: `drawLine` com `StrokeCap.Round`, ou um `Path` por membro com espessura que afina na ponta, para o visual "giz", mais um círculo para a cabeça;
  - equipamento: primitivas presas a uma junta (barra e halter seguem a mão; banco, polia e máquina ficam fixos no mundo; cabo é uma reta da polia até a mão).
  
  Isso cabe na ordem de 200 a 400 linhas de Kotlin comum no `:designsystem` ou num módulo de UI, sem `expect/actual`.
- **Desempenho**: ler o `t` animado só dentro do lambda de desenho (`Canvas { … }` ou `drawWithCache`) evita recompor a tela a cada frame, o mesmo princípio de isolar `timerUi` que o app já segue. Recomendação de prática geral, não verificada numa fonte nesta pesquisa (ver a documentação de fases do Compose).
- **Movimento reduzido**: o `rememberSystemReducedMotion()` do projeto já cobre Android e iOS. Com ele ligado, mostrar a pose final (ou início e fim lado a lado) sem animação, ou trocar as poses com `snapTo` em intervalo longo. Os tokens de duração ficam em `MoosclesTiming` (regra do projeto: nada de `tween(` com número fora de `tokens/`).
- **Testabilidade**: a FK é função pura (pose + t → pontos), testável em `commonTest` na JVM e no iOS sem UI. Fica alinhado ao padrão do repositório.
- **Autoria como dado**: as poses ficam como tabelas de ângulos em Kotlin ou JSON (o `kotlinx-serialization` já está no projeto), versionáveis e revisáveis em diff, sem editor externo.

### Gaps
- Não achei repositório público com figura FK animada em Compose para servir de referência direta.
- Não verifiquei nas notas oficiais do CMP que o Reduce Motion do iOS afeta as animações do Compose automaticamente. O projeto já lê o sinal por conta própria, então isso importa pouco.
- Não medi o custo de frame de um Canvas com cerca de 15 segmentos. A expectativa é que seja trivial, mas não foi medido.

## Q3. Projetos abertos que renderizam demonstrações de exercício a partir de dados de pose (qualquer plataforma) e cuja abordagem ou código possa ser reaproveitado

### Takeaway
Não achei nenhum projeto aberto que gere a figura a partir de **ângulos de junta**. Os acervos abertos de exercícios guardam imagens prontas, em pares início/fim: o Everkinetic em SVG CC BY-SA, e o free-exercise-db em JPG sob Unlicense. Esses acervos servem de referência visual e de metadados (músculos, equipamento), não de dados de pose. Os editores de stick figure com keyframes são GPL ou de licença incerta, e nenhum é de exercício.

### Cited Findings
- **everkinetic/data**: dados abertos de fitness baseados no everkinetic.com, de Greg Priday, licença **CC-BY-SA-4.0**. Os arquivos visíveis são JSON (`exercises.json`, `map.json`), com pastas `src/`, `dist/` e `scripts/`. A página não mostra SVGs nem a contagem de exercícios — [GitHub everkinetic/data](https://github.com/everkinetic/data)
- Projetos que reusam a arte do Everkinetic citam pares `NNNN-relaxation.svg` / `NNNN-tension.svg`, que seriam as poses de início e fim. Há conflito de versão de licença: GymMane cita CC BY-SA 4.0, opentraining-exercises cita CC BY-SA 3.0 Unported — [GymMane CREDITS.md](https://raw.githubusercontent.com/InlitX/GymMane/master/CREDITS.md); [chaosbastler/opentraining-exercises](https://github.com/chaosbastler/opentraining-exercises)
- **yuhonas/free-exercise-db**: **Unlicense**, mais de 800 exercícios. Cada um tem `0.jpg` e `1.jpg` e os campos `force`, `level`, `mechanic`, `equipment`, `primaryMuscles`, `secondaryMuscles`, `instructions` e `category`. Nenhum dado de junta ou pose — [GitHub yuhonas/free-exercise-db](https://github.com/yuhonas/free-exercise-db)
- Animadores de stick figure com keyframes: StickyPy (Python/PyGame, GPLv2, interpolação de keyframes), Flipsticks (atividade Sugar de poses com keyframes), AniMan (Scratch, interpola até 10 keyframes e exporta SVG). Nenhum é de exercício — [SourceForge StickyPy](https://sourceforge.net/p/stickypy/); [Fedora sugar-flipsticks](https://packages.fedoraproject.org/pkgs/sugar-flipsticks/sugar-flipsticks); [Scratch AniMan](https://scratch.mit.edu/discuss/topic/389152)
- OpenMontage tem um sistema de "ink puppet" com poses determinísticas por clipes nomeados. Só vi um resumo secundário, não o repositório — [instagit: OpenMontage InkPuppet](https://instagit.com/calesthio/OpenMontage/what-is-the-ink-puppet-mocap-system-and-how-is-inkpuppet-choreograph-used.md)
- O LottieFiles tem animações gratuitas de "exercise pose", mas são arte pronta, não dados de pose, e não verifiquei a licença de cada uma — [LottieFiles exercise pose](https://lottiefiles.com/free-animations/exercise-pose)

### Inferences
- **Reuso de código**: nada encontrado vale a pena portar. O StickyPy é GPLv2, incompatível com copiar para um app que não é GPL sem avaliar a licença. Um FK próprio é menor do que adaptar qualquer um deles.
- **Reuso de dados**: os pares início/fim do Everkinetic são boas referências para *posar à mão* os ângulos de cada exercício: observar a imagem e anotar os ângulos, o que é um trabalho derivado de ideia, não de arte. Copiar os SVGs para o APK, porém, traria CC BY-SA (share-alike e atribuição) e peso em arquivos. O free-exercise-db (Unlicense) é a fonte mais livre de metadados de músculos e equipamento, mas são JPGs, e não verifiquei a proveniência das fotos.
- O modelo "2 poses por exercício (início/fim da repetição)" é exatamente a forma desses acervos (relaxation/tension, 0.jpg/1.jpg). Isso valida o recorte de 2 keyframes.

### Gaps
- Não confirmei o conteúdo do `exercises.json` do Everkinetic (contagem, campos de músculo ou equipamento) nem se os SVGs ainda estão no repositório (só vi `src/`/`dist/` sem listar).
- Não verifiquei a proveniência e os direitos reais das fotos do free-exercise-db: Unlicense no repositório não prova que as imagens são livres.
- Não achei nenhum conjunto aberto de **ângulos de junta** por exercício (tipo "pose library" de treino).
