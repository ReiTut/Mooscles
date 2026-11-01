# Restrições da plataforma iOS para portar o Mooscles (diário de treino Android) para iPhone — situação em out/2026

Contexto do app Android hoje: APK assinado com chave debug no GitHub Releases + autoatualizador interno (baixa APK, verifica assinatura, instala via PackageInstaller); timer de descanso com alarme exato + notificação + som/vibração funcionando em segundo plano; compartilhar imagem do treino no WhatsApp/share sheet; dados só locais, sem conta.

Convenção: "[não verificado nesta sessão]" = informação do conhecimento prévio do pesquisador que não foi confirmada numa fonte primária durante esta pesquisa. Datas antigas estão marcadas.

## 1. Distribuição (App Store, TestFlight, ad hoc, DMA/UE, Brasil/CADE) e autoatualizador

### Takeaway
No iPhone não existe equivalente a "APK no GitHub + instalador próprio": todo app precisa ser assinado por certificado da Apple, e o caminho realista é o Apple Developer Program (US$ 99/ano) com App Store e/ou TestFlight. No Brasil, desde o iOS 26.5 (jun/2026, acordo com o CADE) existem lojas alternativas, mas **não** distribuição direta pelo site do desenvolvedor (isso só existe na UE), e mesmo nas lojas alternativas o app passa pela notarização da Apple. O autoatualizador interno é proibido pela regra 2.5.2 e não tem API equivalente ao PackageInstaller.

### Cited Findings
**Custo e requisitos básicos**
- Apple Developer Program custa 99 USD por ano de assinatura (preço pode variar por região e aparece em moeda local na inscrição). Exige Apple Account com autenticação de dois fatores e maioridade; pessoa física/MEI compra direto na inscrição; empresas aguardam verificação. — [Apple Developer – Enrollment](https://developer.apple.com/support/enrollment/) / [Program enrollment help](https://developer.apple.com/help/account/membership/program-enrollment/)
- Conta Apple gratuita (Personal Team no Xcode) permite instalar no próprio aparelho, mas: até 10 App IDs, até 3 dispositivos, até 3 apps por aparelho, e perfis de provisionamento expiram em **7 dias** (é preciso recompilar e reinstalar). Recursos avançados (capabilities) exigem associação paga. — [Apple – Compare memberships](https://developer.apple.com/support/compare-memberships/)
- Requisito vigente desde 28/abr/2026: apps enviados ao App Store Connect devem ser compilados com **Xcode 26 ou superior** e SDK iOS 26. — [Apple – Upcoming requirements](https://developer.apple.com/news/upcoming-requirements/)
- Desde 9/set/2026: apps enviados devem ter alvo mínimo iOS 13 ou posterior. — [Apple – Upcoming requirements](https://developer.apple.com/news/upcoming-requirements/)
- Desde 31/jan/2026: desenvolvedores precisam responder ao novo questionário de classificação etária no App Store Connect para não ter envio de atualizações interrompido. — [Apple – Upcoming requirements](https://developer.apple.com/news/upcoming-requirements/)
- Desde 1/mai/2024 (informação antiga, ainda vigente): para enviar app novo ou atualização é obrigatório declarar no *privacy manifest* (PrivacyInfo.xcprivacy) os "motivos aprovados" para cada *required-reason API* usada pelo código, inclusive SDKs de terceiros. — [Apple – Upcoming requirements](https://developer.apple.com/news/upcoming-requirements/)
- Diretriz 5.1.1(i): **todo app** precisa de link para política de privacidade no App Store Connect e dentro do app, explicando quais dados coleta (se algum), uso, retenção/exclusão e como revogar consentimento. Vale mesmo para app sem coleta. — [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)
- Diretriz 4.2: o app precisa ter utilidade e ser "app-like" (mínimo de funcionalidade). — [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)

**Autoatualizador**
- Diretriz 2.5.2: apps devem ser autocontidos e "não podem baixar, instalar ou executar código que introduza ou altere recursos ou funcionalidades do app, incluindo outros apps". — [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)
- Diretriz 4.2.3(i): o app deve funcionar sem exigir instalação de outro app. — [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)
- (Mac App Store, por analogia) 2.4.5(vii): updates devem vir pela loja; "outros mecanismos de atualização não são permitidos". — [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)

**TestFlight**
- Até 10.000 testadores externos por e-mail ou link público; builds ficam ativos por **90 dias** após upload; o primeiro build de cada versão enviado a grupo externo passa pela TestFlight App Review (builds seguintes podem não precisar de revisão completa); até 100 testadores internos (membros da equipe com papéis Admin/Developer etc.). — [Apple TestFlight](https://developer.apple.com/testflight/) (resumo via busca; guias de terceiros que citam 25 internos estão desatualizados, ex. [BuildFire](https://help.buildfire.com/how-to-test-your-app-with-apple-testflight-before-it-goes-live)); 90 dias confirmado também por [Thunkable docs](https://docs.thunkable.com/publish-to-app-store-ios/testflight-overview)

**UE / DMA (contexto, não se aplica a usuário no Brasil)**
- Na UE: lojas alternativas em iPhone e iPad **e** instalação direta pelo site do desenvolvedor (Web Distribution). Exige Apple Account com país/região da UE e estar fisicamente na UE. — [Apple Support 118110 – About alternative app distribution](https://support.apple.com/en-us/118110)
- 18/ago/2026: Apple anunciou novos termos na UE, em vigor a partir de 1/out/2026: troca a Core Technology Fee (por instalação) por uma **Core Technology Commission de 5%** sobre transações digitais em apps distribuídos fora da App Store; elimina a taxa de aquisição inicial e de "store services"; IAP padrão 26%, pagamento alternativo 20%, link para web 15%; **amplia quem pode operar loja alternativa ou distribuir pela web**; notarização continua obrigatória para todo app distribuído alternativamente. — [Michael Tsai](https://mjtsai.com/blog/2026/08/18/new-eu-app-store-terms-to-comply-with-dma/), [Anadolu Agency](https://aa.com.tr/en/science-technology/apple-revamps-eu-app-store-fees-to-resolve-regulatory-dispute/4031035), [Apple Newsroom (UE, ago/2026)](https://www.apple.com/newsroom/2026/08/apple-announces-changes-for-apps-in-the-european-union/), [Apple – Apps in the EU](https://developer.apple.com/support/apps-in-the-eu/)
- Crítica: carta aberta da Article 19 diz que os 5% continuam sendo um custo permanente sobre canais alternativos. — [Article 19 (set/2026)](https://www.article19.org/wp-content/uploads/2026/09/Open-letter-Apples-new-EU-business-terms-FINAL.pdf)

**Brasil / CADE**
- Dez/2025: acordo (settlement) de Apple com o CADE previa prazo de conformidade de 105 dias (início de abril/2026). — [NeonPay (dez/2025)](https://www.neonpay.com/blog/apples-brazil-antitrust-settlement-whats-going-to-change-for-mobile-game-developers)
- 18/jun/2026: Apple anunciou as mudanças no Brasil, disponíveis a partir do **iOS 26.5**: distribuição por **lojas alternativas** (que precisam ser autorizadas pela Apple e cumprir requisitos contínuos) e pagamentos fora do IAP. **Notarização se aplica a todos os apps iOS no Brasil, inclusive fora da App Store** (checagens automáticas + revisão humana, focada em funcionalidade básica e ameaças graves). Core Technology Commission de 5% sobre bens digitais vendidos por apps distribuídos fora da App Store; comissão App Store 10% (Small Business Program e assinaturas após 1º ano) ou 21%, +5% se usar o IAP da Apple; link para web: 15% (ou 10%). Apple diz que quem não vende bens digitais não paga nada. — [Apple Newsroom – Changes to iOS in Brazil](https://www.apple.com/newsroom/2026/06/apple-announces-changes-to-ios-in-brazil/)
- Prazo para desenvolvedores aceitarem os novos termos: 6/jul/2026. — [Gadget Review](https://www.gadgetreview.com/apple-opens-brazils-app-store-to-new-competition); TechCrunch citado via busca ([TechCrunch](https://techcrunch.com/2026/06/18/apple-opens-up-app-store-to-new-competition-in-brazil/), página bloqueou acesso direto)
- **Brasil e Japão: só lojas alternativas, em iPhone. Instalação direta pelo site do desenvolvedor (web distribution) não existe para o Brasil** — só na UE. Exige Apple Account com país Brasil e estar fisicamente no Brasil; quem sai do país mantém apps e pode atualizá-los por até 90 dias. Cada instalação mostra uma folha de instalação do sistema. — [Apple Support 118110](https://support.apple.com/en-us/118110)
- Cobertura da imprensa: [AppleInsider](https://appleinsider.com/articles/26/06/18/eu-style-app-store-rules-reach-brazil-with-new-app-stores-payments), [Engadget](https://www.engadget.com/2197562/apple-opens-up-third-party-app-stores-in-brazil/), [The Next Web](https://thenextweb.com/news/apple-brazil-cade-sideloading-alternative-app-stores)
- Lojas alternativas disponíveis no Brasil: um guia secundário (out/2026) marca a AltStore PAL como disponível no Brasil; Epic, Aptoide e Onside aparecem só para UE/Japão. Fonte única e secundária — tratar com cautela. — [GeekBlog](https://geekblog.net/tech-news/move-over-apple-meet-the-alternative-app-stores-available-in-the-eu-and-elsewhere/)

### Inferences
- **Autoatualizador: impossível no iOS.** Não há API pública para um app instalar outro binário (nada equivalente ao PackageInstaller); o sistema só instala apps assinados pela Apple/distribuídos por canais Apple; e a 2.5.2 proíbe baixar código que mude funcionalidade. Updates chegam pela App Store (com atualização automática do sistema), pelo TestFlight ou, no Brasil, pela loja alternativa que distribuiu o app. O que é permitido: avisar "nova versão disponível" e abrir a página da App Store/TestFlight.
- **Fim do modelo "GitHub Releases".** Não há como publicar um .ipa no GitHub para usuários comuns instalarem. Mesmo a rota de lojas alternativas no Brasil exige conta paga, notarização e aceitação dos termos de distribuição alternativa; não há web distribution no Brasil.
- **Caminho prático para uso pessoal / grupo pequeno:** (a) conta gratuita + Xcode: só no próprio aparelho, reinstalar a cada 7 dias; (b) conta paga + TestFlight: até 10.000 pessoas, mas cada build expira em 90 dias (é preciso subir build novo periodicamente) e o primeiro build de cada versão passa por revisão beta; (c) App Store pública: revisão completa, política de privacidade, rótulo de privacidade e privacy manifest.
- Como o Mooscles não vende bens digitais, a Core Technology Commission de 5% (Brasil ou UE) não geraria custo; a barreira real é a anuidade + processos de revisão/notarização.
- Para um app 100% local sem coleta, o rótulo de privacidade ("nutrition label") pode ser "Data Not Collected", mas a política de privacidade continua obrigatória (5.1.1). O privacy manifest provavelmente precisará declarar ao menos UserDefaults (e possivelmente APIs de timestamp de arquivo/espaço em disco usadas por bibliotecas de SQLite ou frameworks) [lista de categorias não verificada nesta sessão].

### Gaps
- Não foi possível ler a página da Apple sobre o TestFlight diretamente (dados vieram do resumo da busca, consistente com documentação da Apple).
- Ad hoc (limite de 100 dispositivos por família por ano) e Enterprise Program (só uso interno de organizações; não serve para distribuição pública) — **[não verificado nesta sessão]**.
- Critérios exatos de elegibilidade de web distribution na UE após a mudança de out/2026 não foram encontrados (antes exigia, segundo conhecimento prévio, 2 anos de associação e >1 milhão de instalações anuais na UE — [não verificado]).
- Se apps distribuídos via loja alternativa no Brasil podem ter mecanismo próprio de atualização: não encontrado; pela estrutura do sistema, a atualização é gerenciada pela loja (MarketplaceKit) [não verificado].
- Lista atualizada de lojas alternativas efetivamente ativas no Brasil em out/2026: só uma fonte secundária.
- Preço do Developer Program em reais em 2026: não encontrado.

## 2. Timer de descanso em segundo plano

### Takeaway
O iOS não deixa o app "rodar" um timer em segundo plano como o Android com alarme exato + serviço; o app é suspenso. O padrão é agendar uma notificação local para o instante final e mostrar a contagem com uma Live Activity (tela bloqueada + Dynamic Island), que o sistema anima sozinho. Desde o iOS 26 existe o **AlarmKit**, que permite um alerta de contagem regressiva que fura o modo silencioso e o Foco — é o mais próximo do comportamento Android, mas só no iOS 26+ e com permissão do usuário.

### Cited Findings
- **AlarmKit (iOS/iPadOS 26):** framework para alarmes e timers "proeminentes", por agenda (despertador) ou contagem regressiva (ex.: timer de cozinha). Os alertas **furam o modo silencioso e o Foco atual**; mostram título personalizado, nome do app, botão parar, soneca opcional e botão customizado via App Intent; aparecem em StandBy e no Apple Watch pareado. — [WWDC25 sessão 230 – Wake up to the AlarmKit API](https://developer.apple.com/wwdc25/230)
- AlarmKit exige `NSAlarmKitUsageDescription` no Info.plist e autorização (`AlarmManager.requestAuthorization` ou prompt automático no primeiro alarme); o usuário pode revogar em Ajustes. — [WWDC25/230](https://developer.apple.com/wwdc25/230)
- Todo alarme com contagem regressiva **deve** implementar uma Live Activity numa widget extension (estados contagem e pausado); antes do primeiro desbloqueio após reinício, o sistema usa a apresentação padrão. Som customizado via `AlertConfiguration.AlertSound` (arquivo no bundle ou em Library/Sounds); sem som definido, toca o padrão. Apple diz que AlarmKit não substitui critical alerts nem notificações time-sensitive e orienta incluir na Live Activity tempo restante, botão de dispensar e pausar/retomar. — [WWDC25/230](https://developer.apple.com/wwdc25/230)
- Sem entitlement especial para AlarmKit (só a string de uso + widget extension); sem a Live Activity o sistema pode descartar o alarme — segundo documentação de plugin Flutter (fonte secundária). — [pub.dev alarm_kit](https://pub.dev/packages/alarm_kit)
- **Níveis de interrupção de notificação (iOS 15+):** `passive` (sem som/tela), `active` (padrão, acende tela, pode tocar som), `timeSensitive` (imediata e "fura os controles de notificação do sistema", ex. Resumo/Foco), `critical` (imediata e **ignora a chave de silencioso** para tocar som). — [Apple – UNNotificationInterruptionLevel](https://developer.apple.com/tutorials/data/documentation/usernotifications/unnotificationinterruptionlevel.md)
- Critical alerts exigem entitlement especial concedido pela Apple, limitado a saúde (medicação, dispositivos médicos) e segurança (clima severo, emergência); usuário pode desligar time-sensitive por app. — fonte secundária: [Pushwoosh](https://www.pushwoosh.com/ar/blog/ios-push-notifications/)
- Sons customizados de notificação: formatos Linear PCM, IMA4, µLaw, aLaw em aiff/wav/caf; devem ter **menos de 30 s** (senão toca o som padrão); procurados no bundle ou em Library/Sounds. — [Apple – UNNotificationSound](https://developer.apple.com/tutorials/data/documentation/usernotifications/unnotificationsound.md)
- Limite de **64 notificações locais pendentes** por app (o sistema mantém as 64 mais próximas e descarta o resto). — [Apple – UILocalNotification (legado)](https://developer.apple.com/documentation/uikit/uilocalnotification.md); [Apple Developer Forums](https://developer.apple.com/forums/thread/811171)
- Diretriz 2.5.4: apps só podem usar serviços de segundo plano para o fim pretendido (VoIP, reprodução de áudio, localização, conclusão de tarefa, notificações locais etc.) — tocar áudio silencioso para manter o app vivo viola isso. — [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)
- **Live Activities:** ficam ativas por até 8 horas; depois o sistema encerra e remove da Dynamic Island, podendo permanecer na tela bloqueada por mais até 4 horas. — [Apple – Displaying live data with Live Activities](https://developer.apple.com/documentation/activitykit/displaying-live-data-with-live-activities.md) (via busca; há discussão em fórum sobre leitura "4h vs 8+4h": [Apple Forums ActivityKit](https://developer.apple.com/forums/tags/activitykit?page=3))
- **Hevy:** o timer de descanso aparece na Live Activity ao concluir uma série, visível em outros apps e com o aparelho bloqueado; permite ±15 s ou pular direto no widget; ao zerar, o Hevy notifica. Exige ativar Live Activities (inclusive "Permitir acesso bloqueado") e notificações. — [Hevy Help Center](https://help.hevyapp.com/hc/en-us/articles/35649846517399), [Hevy – Live Activity](https://www.hevyapp.com/features/live-activity/)
- **Stronglifts:** usa Dynamic Island para mostrar o timer em andamento ao trocar de app. — [Stronglifts Support](https://support.stronglifts.com/article/126-apple-live-activities-and-dynamic-island)

### Inferences
- Arquitetura recomendada: (1) ao concluir série, gravar `endDate` persistido; (2) agendar `UNNotificationRequest` com `UNTimeIntervalNotificationTrigger` para o fim, nível `timeSensitive` e som .caf < 30 s; (3) iniciar Live Activity com `Text(timerInterval:)`/`ProgressView(timerInterval:)`, que o sistema anima sem o app rodar [comportamento de `Text(timerInterval:)` não verificado em fonte primária nesta sessão]; (4) em iOS 26+, opcionalmente usar AlarmKit para alerta que fura silencioso/Foco; (5) ao voltar ao primeiro plano, recalcular pelo `endDate` (nunca por contador em memória).
- Diferenças em relação ao Android: notificação comum **não toca som com a chave de silencioso ligada** (só critical alerts, inacessíveis para app de academia, ou AlarmKit no iOS 26+); vibração customizada não é controlável pela notificação local [não verificado]; "+15 s" na Live Activity precisa de App Intents (iOS 17+) [não verificado] e de reagendar a notificação; não há loop de som longo (limite de 30 s).
- Time-sensitive provavelmente exige habilitar a capability "Time Sensitive Notifications" no Xcode [não verificado — página do entitlement deu 404].
- Live Activity com limite de 8 h é suficiente para uma sessão de treino, mas a Live Activity normalmente precisa ser iniciada com o app em primeiro plano [não verificado em fonte primária].

### Gaps
- Página da documentação do AlarmKit e do entitlement time-sensitive não renderizou; detalhes vieram da sessão WWDC25 e de fontes secundárias.
- Não foram encontrados posts de engenharia do Strong/Hevy descrevendo a implementação interna (notificação vs AlarmKit). Se o Hevy já adotou AlarmKit: não confirmado.
- Se a Apple aceita AlarmKit para timer de descanso de academia na revisão (orientação oficial cita "cooking timer"; uso análogo parece adequado, mas não confirmado).

## 3. Compartilhar imagem no WhatsApp

### Takeaway
No iOS o caminho padrão é `UIActivityViewController` (share sheet) com a imagem renderizada; o WhatsApp aparece como share extension. Não há equivalente a um Intent explícito para "abrir direto o WhatsApp com esta imagem" de forma oficial e estável; o esquema `whatsapp://` serve basicamente para texto.

### Cited Findings
- FAQ do WhatsApp (iPhone): a share extension, acessada via `UIActivityViewController`, é "agora a forma preferida" de compartilhar conteúdo no WhatsApp; aceita imagens (UTI `public.image`), até 30 arquivos de mídia por compartilhamento. O uso de `canOpenURL` com o esquema exige listar `whatsapp` em `LSApplicationQueriesSchemes`. — [WhatsApp FAQ – How to link to WhatsApp from a different app](https://whatsapp.com/faq/iphone/23559013) (conteúdo via busca; link alternativo da FAQ retornou 404)
- O esquema `whatsapp://send?text=` permite só texto, não imagens. — [LiveCode forum](https://forums.livecode.com/viewtopic.php?p=174352) (secundária)
- Rota legada via `UIDocumentInteractionController` com UTI exclusivo `net.whatsapp.image` / extensão `.wai` (a FAQ antiga citava isso; amostras de código de terceiros usam `net.whatsapp.images`) — não confirmado em documentação atual. — [TechNetExperts](https://www.technetexperts.com/interact-with-whatsapp-from-ios-application/) (secundária)
- Relato de dificuldade em compartilhar texto + imagem juntos via `UIActivityViewController`. — [LiveCode forum](https://forums.livecode.com/viewtopic.php?p=174413) (secundária)

### Inferences
- Para o Mooscles: renderizar a imagem (ex. `ImageRenderer` do SwiftUI ou `UIGraphicsImageRenderer`), salvar em arquivo temporário (tmp/Caches) e passar `URL` ou `UIImage` ao share sheet (ou `ShareLink` em SwiftUI). Um botão "WhatsApp" dedicado só daria para abrir a share sheet ou usar a rota legada de document interaction, sem garantia de estabilidade.
- Diferença em relação ao Android: no Android dá para direcionar o Intent para o pacote `com.whatsapp`; no iOS o usuário escolhe o WhatsApp e depois o contato na share sheet (um toque a mais). Salvar na galeria exige permissão `NSPhotoLibraryAddUsageDescription` [não verificado nesta sessão].

### Gaps
- Não consegui acessar a versão atual da FAQ do WhatsApp diretamente; status atual do UTI `net.whatsapp.image` não confirmado.

## 4. Dados locais, backup do iCloud, perda de dados e HealthKit

### Takeaway
SQLite dentro do container do app funciona normalmente; por padrão Documents e Library/Application Support entram no backup do iCloud/computador, e tmp e Library/Caches não. Ao apagar o app, o container é apagado junto — sem backup do iCloud ativo ou exportação, os dados se perdem. HealthKit é uma oportunidade opcional para registrar treinos de força.

### Cited Findings
- Com o Backup do iCloud ativado, o sistema faz backups periódicos do dispositivo, **incluindo os dados do app**. `/tmp` e `/Library/Caches` são purgáveis e excluídos do backup por padrão; não devem ser usados para dados não-recriáveis. — [Apple – Optimizing your app's data for iCloud Backup](https://developer.apple.com/tutorials/data/documentation/foundation/optimizing-your-app-s-data-for-icloud-backup.md)
- Dados criados pelo usuário não devem ser excluídos do backup; `isExcludedFromBackup` é só uma indicação ao sistema, "não um mecanismo que garanta" que o item nunca apareça num backup; certas operações de arquivo podem resetar esse valor. — [Apple – Optimizing your app's data for iCloud Backup](https://developer.apple.com/tutorials/data/documentation/foundation/optimizing-your-app-s-data-for-icloud-backup.md)
- Diretriz 2.5.2: apps não podem ler/gravar fora da área de container designada. — [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)

### Inferences
- Guardar o banco SQLite em `Library/Application Support` (incluído no backup, não visível ao usuário). Isso dá ao iPhone uma vantagem sobre o Android do Mooscles: se o usuário tiver Backup do iCloud ligado, restaurar o aparelho recupera o histórico. Mas desinstalar o app apaga o container imediatamente; reinstalar não traz os dados de volta, a menos que se restaure um backup do aparelho inteiro [comportamento de exclusão na desinstalação não verificado em fonte primária nesta sessão].
- "Descarregar app" (Offload Unused Apps) remove o binário e mantém os dados [não verificado nesta sessão].
- Atenção: apps instalados pelo TestFlight ou com conta gratuita (7 dias) podem precisar de reinstalação; se o bundle identifier e a equipe forem os mesmos, os dados persistem ao atualizar por cima, mas apagar e reinstalar perde tudo [não verificado].
- Recomenda-se exportação/importação manual (arquivo JSON/CSV/SQLite via share sheet ou app Arquivos) e, opcionalmente, sync via CloudKit/iCloud Drive — sem criar conta própria.
- HealthKit (opcional): gravar `HKWorkout` do tipo musculação tradicional, com duração e calorias estimadas, aparecendo no app Saúde/Fitness. Exige descrições de uso, capability HealthKit e política de privacidade específica [detalhes não verificados nesta sessão]. No iOS 26 a Apple trouxe sessões de treino (`HKWorkoutSession`) para iPhone [não verificado].

### Gaps
- Não foram buscadas fontes primárias sobre HealthKit (tipos de atividade, regras de revisão 5.1.3 para dados de saúde) — prioridade menor por ser opcional.
- Comportamento exato de dados na desinstalação/reinstalação não confirmado em documentação da Apple nesta sessão.

## 5. Mac/Xcode, assinatura e versão mínima do iOS no Brasil

### Takeaway
É necessário um Mac com Xcode 26+ para compilar, assinar e enviar (requisito desde 28/abr/2026). A base de iPhones está muito concentrada nas versões mais recentes: segundo a Apple, 79% de todos os iPhones ativos estavam no iOS 26 em junho/2026, e no Brasil (StatCounter, set/2026) o iOS 26.x domina, com o iOS 27 começando a subir. Mirar iOS 17 ou 18 como mínimo cobre quase todos; recursos como AlarmKit ficam condicionados a iOS 26+.

### Cited Findings
- Builds para o App Store Connect devem usar Xcode 26+ e SDK iOS 26 (desde 28/abr/2026); alvo mínimo iOS 13+ (desde 9/set/2026). — [Apple – Upcoming requirements](https://developer.apple.com/news/upcoming-requirements/)
- Adoção oficial (aparelhos que fizeram transação na App Store em 7/jun/2026): iPhone com iOS 26 = **79% de todos** os aparelhos e **86%** dos lançados nos últimos 4 anos; iPad com iPadOS 26 = 68% / 79%. — [Apple Developer – App Store support (iOS/iPadOS usage)](https://developer.apple.com/support/app-store/)
- StatCounter Brasil (mobile+tablet), **set/2026**: iOS 26.6 57,88%; iOS 26.5 8,41%; iOS 18.7 8,18%; iOS 27 3,76%; iOS 27.0 2,46%; iOS 18.6 2,21%. A própria página avisa que mudanças anti-fingerprinting do Safari fazem parte do iOS 26 ser reportado como 18.7/18.6 — então "18.x" está superestimado. — [StatCounter – iOS version market share Brazil](https://gs.statcounter.com/ios-version-market-share/mobile-tablet/brazil)
- Meses anteriores do StatCounter Brasil mostram dados inconsistentes (ex. iOS 11.0 com 16–32% em maio/junho de 2026), provavelmente ruído de tráfego de bots/user agents — usar com cautela. — [StatCounter Brazil (all)](https://gs.statcounter.com/ios-version-market-share/all/brazil)
- Conta gratuita permite testar no próprio aparelho com perfis de 7 dias; distribuição exige associação paga. — [Apple – Compare memberships](https://developer.apple.com/support/compare-memberships/)

### Inferences
- O dono tem um MacBook (o projeto está num volume "Disco Macbook"), então o requisito de Mac está atendido; falta verificar se a versão do macOS roda Xcode 26 (Xcode 26 requer macOS Sequoia 15.x ou posterior [versão exata não verificada]).
- Alternativas sem Mac local existem (Mac na nuvem, runners macOS de CI, builds em nuvem de frameworks cross-platform como Expo EAS), mas a assinatura continua exigindo certificados do Apple Developer Program [não verificado nesta sessão].
- Recomendação de alvo mínimo: iOS 17 (App Intents interativos em Live Activities/widgets, SwiftData) ou iOS 18; usar `if #available(iOS 26, *)` para AlarmKit. Diferente do Android, não há chave de assinatura "debug" compartilhável: cada build distribuído é assinado com certificado/perfil emitido pela Apple para o time do desenvolvedor.
- Live Activities exigem iOS 16.1+ e a chave `NSSupportsLiveActivities` no Info.plist [versão/chave vindas de resumo sem página renderizada — não verificado em texto primário].

### Gaps
- Não encontrei dado oficial da Apple específico do Brasil; StatCounter mede tráfego web, não base instalada.
- Requisito exato de versão do macOS para Xcode 26 não verificado.
- Participação do iOS (vs Android) no Brasil em 2026 não pesquisada (fora do escopo direto, mas relevante para o tamanho do público).
