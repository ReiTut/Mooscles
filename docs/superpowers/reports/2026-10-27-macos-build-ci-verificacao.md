# Build no macOS e CI: verificação

- **Data:** 2026-10-27
- **Branch:** `chore/macos-build-ci` (HEAD `b0c395f` na hora da verificação)
- **Plano:** [2026-10-27-macos-build-ci.md](../plans/2026-10-27-macos-build-ci.md)
- **Workflow:** [`.github/workflows/ci.yml`](../../../.github/workflows/ci.yml)

## Resumo

Os scripts PowerShell agora rodam no macOS com `pwsh` 7, e o GitHub Actions executa testes de host, lint, `assembleDebug` e o `prepare-release.ps1` a cada push. A última execução está verde: [run 40586360013](https://github.com/pedroyuuki/Mooscles/actions/runs/40586360013). No caminho apareceu um defeito real no `prepare-release.ps1`, já corrigido (abaixo).

## Commits

| Commit | O que fez |
| --- | --- |
| `59a988b` | plano |
| `153a9a4` | docs de ambiente: PowerShell no macOS e CI |
| `cb3cd09` | scripts portáveis para macOS |
| `df2527d` | workflow de CI |
| `3bb2336` | CI roda o script de release só com build-tools 35.0.0 (contorno provisório) |
| `b0c395f` | `prepare-release.ps1` lê a saída do apksigner das build-tools 37+ |
| `7f753b7` | parser de assinantes falha fechado (revisão final, ver abaixo) |
| `392f1aa` | CI: dois assinantes, rotação de chave e Windows PowerShell 5.1 |

## Verificado no Mac (pwsh 7.6.9, JDK 21.0.13, build-tools 35.0.0)

- PowerShell 7.6.9 osx-arm64 instalado em `~/.local/share/powershell/7.6.9`; o sha256 do tarball conferiu com o digest do asset da release antes de extrair.
- `pwsh ./build.ps1` (tarefas padrão): Gradle verde (`BUILD SUCCESSFUL in 5m 49s`), depois **sai com código 1** no `prepare-release.ps1`, porque o APK está assinado com a chave debug do Mac e não com a original. É a proteção funcionando; nenhum parâmetro de contorno foi criado.
- Testes de host pelo script: 482 testes, 0 falhas.
- `prepare-release.ps1` com a impressão digital do Mac informada: gerou o APK versionado, o `.sha256` e o `update.json` com exatamente os 7 campos do contrato; o sha256 confere com `shasum -a 256`.
- Casos negativos recusados com a mensagem esperada: chave padrão, diretório de saída dentro do código-fonte, `PreviousVersionCode` igual ao `versionCode`, SDK ausente.
- Caminhos com espaço (raiz do projeto e SDK em `/Volumes/Disco Macbook/...`) funcionam.
- Os 6 scripts passam no parser do PowerShell 7.6.9 (0 erros).
- `generate-launcher.ps1` continua só para Windows (usa `System.Drawing`) e agora falha cedo, com mensagem clara, no macOS e no Linux.
- Apksigner 37 reproduzido localmente num SDK de teste: antes da correção a verificação falhava; depois passa, e um APK com dois assinantes continua recusado nas build-tools 35 e 37.

## Verificado no CI (ubuntu-latest e windows-latest)

Job `host` (Temurin 21, SDK platform 35 e build-tools 35.0.0, cache do Gradle e do Robolectric):

| Execução | Resultado | host | Observação |
| --- | --- | --- | --- |
| 40584063701 | falhou | 7m23s (frio) | `host` verde; `release-script` falhou pelo defeito do apksigner 37 |
| 40585511052 | verde | 4m47s (quente) | com o contorno de `3bb2336` |
| 40586360013 | verde | 4m43s (quente) | com a correção de `b0c395f` |
| 40591041005 (tentativa 1) | falhou | — | teste instável `AppViewModelBackupHostTest.invalidFileShowsReasonAndKeepsData:260`; `release-script` não rodou |
| 40591041005 (tentativa 2) | verde | 3m26s | `7f753b7` + `392f1aa`; os 4 jobs de `release-script` verdes, com os casos novos e o 5.1 |

- 482 testes em 65 classes, 0 falhas, 0 erros, 0 ignorados nas quatro execuções em que o `host` passou; a quinta (40591041005, tentativa 1) falhou só no teste instável `AppViewModelBackupHostTest.invalidFileShowsReasonAndKeepsData:260`. Os outros testes instáveis conhecidos (`DiscardNavigationHostTest`, `AppViewModelFoldersDiscardHostTest:135`) passaram nas cinco; não há retry configurado. Os dois de `AppViewModel*` foram corrigidos depois, em `test: fix two flaky host tests`.
- Job `release-script`, na execução 40586360013: matriz ubuntu e windows, cada um com build-tools 35.0.0 isolada e com as build-tools do próprio runner (37.0.0). Os 4 jobs levaram de 30 a 58 s. Cada um verifica o caso positivo (`update.json` com os 7 campos, `applicationId`, `versionCode`, sha256 e tamanho conferidos contra o APK) e o negativo (chave padrão recusada, em processo e em processo filho, sem gerar `update.json`).
- O fingerprint do CI é lido de forma independente, e o job falha se o APK do CI vier com a chave original.
- Actions fixadas por SHA; `permissions: contents: read`; sem secrets. actionlint 1.7.12: 0 erros (sem shellcheck nem pyflakes).

## Defeito encontrado: apksigner 37

O `prepare-release.ps1` escolhe a build-tools mais nova instalada. A partir da 37.0.0 o `apksigner verify --print-certs` imprime `V2 Signer: certificate SHA-256 digest: ...` em vez de `Signer #1 certificate SHA-256 digest: ...`. O regex antigo não achava nenhum assinante e o script recusava o APK com a mensagem da chave, mesmo com a chave certa. Falhava fechado (seguro), mas bloqueava a release em qualquer máquina com build-tools 37 ou mais nova. A correção aceita os dois formatos, deduplica as impressões digitais e continua exigindo **exatamente um** assinante distinto igual ao esperado. Só o bloco de parse mudou; parâmetros e mensagens são os mesmos.

## O que NÃO foi verificado

- **Windows PowerShell 5.1, fora do `prepare-release.ps1`.** Atualizado em 2026-10-27, na revisão final: o `prepare-release.ps1` agora roda no 5.1 no CI (ver a seção da revisão final). `build.ps1` e os outros scripts continuam verificados no 5.1 só por inspeção.
- **Máquina Windows real.** Só o runner do CI; a equivalência do comportamento no Windows vem de argumento por inspeção, mais o CI.
- **`publish-release.ps1`.** Só passou no parser e no `Get-Help`; nunca foi executado (não há rascunho de release criado por ele).
- **Chave original.** Nunca esteve disponível. O caminho de release foi testado só com chaves descartáveis, a do Mac e a do CI. Nenhum APK gerado aqui atualiza instalações existentes.
- **Esquemas de assinatura v3.1 (rotação) e v3.2 (híbrido).** Atualizado em 2026-10-27, na revisão final: o parser agora falha fechado. Toda linha `… certificate SHA-256 digest: <hex>` que o apksigner imprime conta como assinante, menos a do source stamp, e o APK só passa com exatamente um certificado distinto, igual ao esperado. Rotação v3.1 (dois certificados) é recusada de forma explícita, e o CI prova isso com APKs reais (ver a seção da revisão final). Os APKs reais de dois assinantes e de rotação já eram recusados pelo parser antigo (2 e 0 assinantes reconhecidos). A proteção contra v3.2 híbrido e formatos desconhecidos está provada só pelos casos sintéticos locais, porque não há APK v3.2 real (exige chave pós-quântica).
- **Linux:** a comparação de caminhos `Ordinal` só é exercida no CI (job de release no ubuntu), sem teste dedicado.
- **`compat-reviewer`** não rodou: `tools/` e `.github/` não estão na lista de gatilhos, e nada de app, schema ou `update.json` mudou.
- O `-PreviousVersionCode` do `build.ps1` continua 15 (o último distribuído é 16); fica para o commit de release.

## Revisão final (2026-10-27)

A revisão de toda a branch achou um buraco no parser de `b0c395f`: ele só contava as linhas de assinante que reconhecia. O apksigner 37 imprime assinantes v3.2 como `V3.2 Hybrid Classical Signer: (minSdkVersion=…, maxSdkVersion=…) certificate SHA-256 digest: …`, e um APK com um assinante extra nesse formato seria aceito. `7f753b7` passa a contar toda linha de digest de certificado, menos a do source stamp. Parâmetros, impressão digital padrão e mensagens não mudaram.

Verificado no Mac (pwsh 7.6.9, chaves descartáveis), com o script real nas build-tools 35.0.0 e 37.0.0:

| Caso | 35 | 37 |
| --- | --- | --- |
| uma chave, informada em `-ExpectedSignerSha256` | aceito | aceito |
| impressão digital padrão | recusado | recusado |
| dois assinantes, confiando em qualquer um deles | recusado | recusado |
| rotação v3.1 (lineage, `--rotation-min-sdk-version 33`), confiando em qualquer uma das chaves | recusado | recusado |

Onze saídas sintéticas passaram por um apksigner de teste (com o aapt real). Foram aceitas: um assinante nos formatos 35 e 37, digest em maiúsculas e source stamp nos dois formatos. Foram recusadas: dois assinantes v2, **v3.2 híbrido junto com v3.0**, rotação nos dois formatos, **formato desconhecido** (`V4 Quantum Signer`) e saída sem digest. O script anterior aceitava o v3.2 híbrido e o formato desconhecido.

Verificado no CI ([run 40591041005](https://github.com/pedroyuuki/Mooscles/actions/runs/40591041005), tentativa 2, verde):

- Em ubuntu e windows, com as build-tools 35.0.0 e com as 37.0.0 do runner, o job gera duas chaves descartáveis no diretório temporário do runner. Com elas assina o APK do CI de duas formas: com os dois assinantes e com rotação v3.1. O `prepare-release.ps1` recusa os dois APKs confiando em qualquer uma das chaves, com a mensagem "Do not create a new key" e sem gerar `update.json`.
- Nos jobs windows, o **Windows PowerShell 5.1.26100** roda o script como processo separado (`powershell.exe -File`). O caso positivo gera os 7 campos do `update.json`, com sha256, tamanho e `.sha256` conferidos. Com a impressão digital padrão, o script sai com código diferente de zero e não gera `update.json`. Não apareceu o `NativeCommandError` do `2>&1` com `ErrorActionPreference = 'Stop'`, porque o aapt e o apksigner não escrevem em stderr nesses APKs, e o script não mudou para isso. Se uma versão futura escrever avisos em stderr, o 5.1 pode falhar com outra mensagem, mas falha fechado.
- A tentativa 1 falhou no teste instável `AppViewModelBackupHostTest.invalidFileShowsReasonAndKeepsData:260`. Depois de `clearBackupMessage()`, o `awaitUi` pode aceitar a mensagem anterior. A tentativa 2 foi uma reexecução manual (`gh run rerun --failed`); o workflow continua sem retry. O teste está nas pendências da [v1.5.0](../releases/v1.5.0.md).

