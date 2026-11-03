# Fase 4B: spike S4, foto do exercício personalizado no Android

- **Data:** 2026-10-30
- **Branch do spike:** `spike/phase4b-photo` (local, nunca mesclada nem enviada), saída de `66a3b72`. Commit: `a6250ca` (`PhotoSpikeActivity`, os candidatos do pipeline, o teste instrumentado e o teste Robolectric)
- **Spec:** [fase 4, D17 e S4](../specs/2026-10-29-phase4-library-routines-design.md) · **Plano:** [fase 4B, Task 6](../plans/2026-10-30-phase4b-routines.md) (decisão 11; achados F12, F13, F24 e F25 da varredura prévia)
- **Evidências:** [2026-10-30-phase4b-spike-foto/](2026-10-30-phase4b-spike-foto/) (linhas `SPIKE` de cada API, saída da instrumentação, Robolectric, seletor e câmera, capturas de tela)
- **Aparelhos:** três AVDs temporários `google_apis` arm64 (APIs 26, 28 e 35, perfil `pixel_2`, 2 GiB, câmera traseira `emulated`), um de cada vez em `emulator-5560`, com Wi-Fi e dados desligados (`svc wifi/data disable`, `ping` sem rede). O `emulator-5556` não foi usado. Os AVDs foram apagados no fim; as imagens 26 e 28 ficaram no SDK.

## Resumo

- **S4 aprovado nas três APIs.** A foto sai em pé, abaixo de 300 KB, sem `OutOfMemoryError` e sem permissão nova (`aapt2 dump permissions` sem `CAMERA`, `READ_MEDIA_IMAGES` ou `READ_EXTERNAL_STORAGE`; nenhum pedido de permissão do Mooscles apareceu).
- **Pipeline escolhido: um caminho só, em todas as APIs.** `BitmapFactory` com `inSampleSize` + `android.media.ExifInterface` lido do `InputStream` + uma `Matrix` (orientação e escala). Foi o único candidato em pé, pequeno e com ~16 MB de pico nas APIs 26, 28 e 35. O `ImageDecoder` fica só como reserva na API 29+, para um formato que o `BitmapFactory` não lê, numa função `@RequiresApi(29)` chamada sob `Build.VERSION.SDK_INT >= Q` (F13).
- **Isso muda o plano.** A decisão 11 e a Tech Stack previam o `ImageDecoder` a partir da API 28. Na 28 ele falha: com `setTargetSize`, um JPEG ou HEIC de 12 MP com rotação EXIF lança `getPixels failed with error invalid scale`; com uma amostra potência de 2 (`setTargetSampleSize`), ele decodifica em pé, mas com **~98 a 102 MB** de memória nativa no pico (contra ~16 MB do `BitmapFactory`). A Task 7 escreve a nota datada na spec e no plano (F27).
- **JPEG 85 com degraus.** Um conteúdo extremo (ruído total, 1440 × 1080) dá 466 KB em 85; o pipeline desce para 75, 65 e 55 só quando passa de 300 KB, e esse caso fecha em 65 com 298,5 KB. Fotos com detalhe ficam entre 187 e 241 KB em 85.
- **F12 provado nas três APIs:** com o processo do app morto enquanto a câmera está aberta, a `Activity` volta restaurada, o `TakePicture` entrega `true` e a URI guardada em `rememberSaveable` está lá; a mesma URI num `remember` comum volta `null`.
- **F25 provado:** Robolectric com `sdk = 26` e `@GraphicsMode(NATIVE)` roda o pipeline inteiro no projeto (as 8 orientações em pé, miniatura 192 × 192). O `android-all-instrumented-8.0.0_r4-robolectric-r1-i7` tem 96 MB e baixa na primeira execução. Aceitável, com um ajuste no cache do CI (ver abaixo).
- **HEIC:** em pé na 35; **deitado na 28** (o `ExifInterface` do framework devolve orientação 0 para HEIF, por stream e por descritor); não decodifica na 26 (vira o toast de falha). Fica registrado como risco aceito.

## O pipeline para a Task 7

Em todas as APIs (26+), com a origem reaberta a cada passagem (`() -> InputStream`, como uma URI de conteúdo):

1. **Limites:** `BitmapFactory.decodeStream` com `inJustDecodeBounds`. Largura ou altura ≤ 0 → falha (nada é gravado).
2. **Orientação:** `ExifInterface(inputStream).getAttributeInt(TAG_ORIENTATION, ORIENTATION_NORMAL)`; `IOException` → normal. Do stream, **não** do descritor: o descritor não lê nada a mais na 28 e quebra no Robolectric (`Os.dup` devolve `null`, `NullPointerException` no construtor).
3. **Amostragem:** a maior potência de 2 com `max(w, h) / amostra ≥ 1080` (12 MP → 2, decodificado em 2000 × 1500). `inPreferredConfig = ARGB_8888`.
4. **Orientação e escala numa `Matrix` só**, com `createBitmap(…, matrix, filter = true)`. A tabela das 8 orientações:

   | EXIF | Matriz |
   |---|---|
   | 2 `FLIP_HORIZONTAL` | `setScale(-1, 1)` |
   | 3 `ROTATE_180` | `setRotate(180)` |
   | 4 `FLIP_VERTICAL` | `setRotate(180); postScale(-1, 1)` |
   | 5 `TRANSPOSE` | `setRotate(90); postScale(-1, 1)` |
   | 6 `ROTATE_90` | `setRotate(90)` |
   | 7 `TRANSVERSE` | `setRotate(-90); postScale(-1, 1)` |
   | 8 `ROTATE_270` | `setRotate(-90)` |

   Depois `postScale(s, s)` com `s = min(1, 1080 / max(w, h))` (nunca amplia). As 8 foram provadas com imagens montadas por uma tabela feita à mão (cores por quadrante), sem usar a matriz do pipeline para gerar a entrada.
5. **Alfa:** se `hasAlpha()`, desenhar sobre branco antes do JPEG (o PNG com faixa transparente sai `255,255,255` no centro nas três APIs; sem isso ficaria preto).
6. **Foto:** JPEG 85; se passar de 300 KB (307.200 bytes), 75, 65 e 55.
7. **Miniatura:** recorte quadrado central da foto já em pé, reduzida por metades com filtro até ficar a menos de 2× de 192 e depois um último passo para 192 × 192; JPEG 85. Sai entre 2,8 e 14 KB.
8. **Reserva (29+):** se o passo 1 falhar, `ImageDecoder.decodeBitmap(createSource(ByteBuffer))` com `setTargetSize` calculado do `info.size` (que já vem orientado: `3000x4000` para um 4000 × 3000 com EXIF 6) e `ALLOCATOR_SOFTWARE`, depois os passos 5 a 7. Na 26–28, falha → `null`.

A saída não leva EXIF (`outExif=0`): os bytes já estão em pé.

## Medidas

Pipeline escolhido, aparelho (emulador, sem rede). "Pico nativo" é `Debug.getNativeHeapAllocatedSize()` amostrado a cada 1 ms durante o processamento, menos a base depois de 3 GCs; os pixels de `Bitmap` ficam no heap nativo desde a API 26. "Java" é o equivalente no heap Java.

| Caso (entrada) | API | Saída | Bytes | Miniatura | Pico nativo | Java | Tempo | Em pé |
|---|---|---|---:|---:|---:|---:|---:|---|
| JPEG 4000 × 3000, EXIF 6 (3,6 MB) | 26 / 28 / 35 | 810 × 1080 | 236.266 / 236.172 / 236.768 | 13,5–13,9 KB | 15.952 / 15.906 / 15.159 KB | 0,8–0,9 MB | 85 / 87 / 120 ms | sim |
| JPEG 4000 × 3000, EXIF 8 | 26 / 28 / 35 | 810 × 1080 | 238.336 / 238.292 / 238.852 | 13,5–14,0 KB | 15.954 / 15.898 / 15.159 KB | 0,8–0,9 MB | 89 / 79 / 163 ms | sim |
| JPEG 4000 × 3000, sem EXIF | 26 / 28 / 35 | 1080 × 810 | 240.267 / 240.273 / 240.773 | 13,6–14,1 KB | 15.952 / 15.924 / 15.160 KB | 0,8–0,9 MB | 89 / 66 / 112 ms | sim |
| PNG 4000 × 3000 com faixa transparente (9,5 MB) | 26 / 28 / 35 | 1080 × 810 | 221.326 / 221.312 / 221.762 | 12,8–13,2 KB | 15.962 / 15.938 / 15.160 KB | 0,8 / 0,8 / 19,5 MB | 435 / 340 / 338 ms | sim, centro branco |
| JPEG 4000 × 3000 com ruído ±60, EXIF 6 (9,0 MB) | 26 / 28 / 35 | 810 × 1080 | 187.372 / 187.203 / 187.884 | 2,8–3,3 KB | 15.985 / 15.922 / 15.171 KB | 0,8 MB | 216 / 199 / 313 ms | sim |
| JPEG 1440 × 1080 de ruído total (pior caso de bytes) | 26 / 28 / 35 | 1080 × 810 | 298.501 / 298.501 / 298.603 (em 65) | 12,1–12,6 KB | 9.801 / 9.718 / 9.516 KB | 3,0–3,8 MB | 158 / 78 / 89 ms | sim |
| HEIC 4000 × 3000 do `sips` (`irot` + EXIF 6, 55 KB) | 26 / 28 / 35 | — / 1080 × 810 / 810 × 1080 | — / 79.655 / 82.609 | — | 7 / 16.496 / 15.866 KB | — | 1 / 438 / 673 ms | não decodifica / **deitado** / sim |
| 8 orientações, JPEG 2400 × 1800 | 26 / 28 / 35 | 1080 × 810 ou 810 × 1080 | — | — | — | — | — | 8/8 em cada API |
| Arquivo que não é imagem | 26 / 28 / 35 | `null` | — | — | — | — | — | — |

Qualidade contra bytes no pior caso (ruído total, 1080 × 810): q85 466.428, q75 362.440, q65 273.910, q55 250.540 (os mesmos nas três APIs, ±0,1%). Com ruído ±60 a partir de 12 MP: q85 187 KB, q75 155 KB.

**Memória máxima medida do pipeline escolhido: 16.496 KB (~16,1 MiB) nativos** (HEIC na 28); para JPEG e PNG de 12 MP, 15,2 a 16,0 MiB, que é a conta esperada: 2000 × 1500 × 4 (11,4 MiB, a decodificação amostrada) + 1080 × 810 × 4 (3,3 MiB, a saída). O Java fica abaixo de 1 MB nos casos de 12 MP e em até 3,8 MB nos degraus de qualidade do pior caso, exceto no PNG da API 35 (19,5 MB, medido; nas APIs 26 e 28 o mesmo PNG fica em 0,8 MB).

### Os candidatos que ficaram de fora

12 MP JPEG com EXIF 6, na mesma execução:

| Candidato | API 28 | API 35 |
|---|---|---|
| `BitmapFactory` + `ExifInterface` + `Matrix` (escolhido) | 236.172 B, 15.906 KB, em pé | 236.768 B, 15.159 KB, em pé |
| `ImageDecoder` + `setTargetSampleSize` (potência de 2) + escala | 234.566 B, **98.392 KB**, em pé | 236.717 B, 23.531 KB, em pé |
| `ImageDecoder` + `setTargetSize` direto | **falha**: `IOException: getPixels failed with error invalid scale` (EXIF 6 e 8; sem EXIF funciona) | 230.776 B, 8.823 KB, em pé |

- Na 28, o mesmo vale para o HEIC: amostra potência de 2 em pé com 102.471 KB; direto falha.
- O direto na 35 é o mais econômico em memória, mas guarda mais detalhe: no JPEG com ruído ±60 dá 293.517 B em 85, colado no limite (o escolhido dá 187.884 B).
- Nas APIs 29 a 34 nada foi medido (fora do escopo das imagens do spike). Como o comportamento do `ImageDecoder` mudou entre a 28 e a 35, ele fica só como reserva.

## Seletor e câmera

| | API 26 | API 28 | API 35 |
|---|---|---|---|
| `PickVisualMedia.isPhotoPickerAvailable` | `false` | `false` | `true` |
| O que abriu | `ACTION_OPEN_DOCUMENT` de reserva: DocumentsUI (`com.android.documentsui/.picker.PickActivity`) | o mesmo DocumentsUI | seletor do sistema (`com.google.android.providers.media.module/…PhotoPickerActivity`, "This app can only access the photos you select") |
| URI devolvida | `content://com.android.providers.media.documents/document/image%3A32` | `…/document/image%3A20` | `content://media/picker/0/com.android.providers.media.photopicker/media/1000000018` |
| EXIF preservado na URI | sim (6) | sim (6) | sim (6) |
| Stream | `ParcelFileDescriptor$AutoCloseInputStream` | o mesmo | o mesmo |
| Foto do seletor (4000 × 3000, EXIF 6) | 810 × 1080, 81.664 B, em pé | 810 × 1080, 81.319 B, em pé | 810 × 1080, 82.138 B, em pé |
| Câmera resolvida (`resolveActivity`) | `com.android.camera2/com.android.camera.CaptureActivity` | o mesmo | o mesmo |
| Intent da câmera | `flg=0x3` (leitura e escrita concedidas) e `clip={… content://com.mooscles.workout-shares/photo_capture/…}`, postos pelo framework | igual | igual |
| Primeira abertura da câmera | o app Camera2 pede **a própria** permissão de localização e mostra "Remember photo locations?"; a captura volta `ok=false` | igual | sem diálogos |
| Captura gravada em `cache/photo-capture/` | 480 × 640, 19.036 B | 720 × 1280, 67.608 B | 1392 × 1856, 75.263 B |
| Processo morto com a câmera aberta | `am kill` não matou ("Invalid packageName"); `kill -9` como root: restaurado, `ok=true`, URI salva presente, `remember` comum `null` | `am kill`: restaurado, `ok=true`, URI salva presente, `remember` `null` | `am kill`: restaurado, `ok=true`, URI salva presente, `remember` `null` |
| Câmera desativada (`pm disable-user com.android.camera2`) | `resolveActivity` → `null`, botão desativado | `null` | `null` (com o `<queries>` de `IMAGE_CAPTURE` no manifesto) |

Para a Task 15:

- `ok=false` acontece mesmo com câmera boa (primeira execução do Camera2, ou erro da câmera); trate como cancelado e apague o arquivo de captura se existir.
- Apague o arquivo de `cache/photo-capture/` depois do processamento (o spike apaga; a pasta ficou vazia nas três APIs).
- O framework já põe a concessão de escrita e o `ClipData` na intent de `IMAGE_CAPTURE`; não é preciso `grantUriPermission` manual.
- No emulador API 28, a câmera `emulated` só capturou com `-gpu host`; com `-gpu swiftshader_indirect` o `FakeCamera3` estoura o tempo ("Can't connect to the camera"). É do emulador, não do app; mesmo assim, a `Activity` restaurada recebeu `ok=false` com a URI salva.

## Achados da varredura prévia

- **F12:** provado como descrito acima. A URI pendente vai em `rememberSaveable` (ou no estado salvo do formulário); um `remember` comum perde a URI na morte do processo.
- **F13:** o `:app:lintDebug` com o código do spike (que chama `ImageDecoder` em funções `@RequiresApi(28)`, sob `Build.VERSION.SDK_INT`) terminou com 0 erros e nenhum `NewApi`. O lint dá o aviso `ExifInterface` ("use androidx.exifinterface") em cada uso do `android.media.ExifInterface`; é aviso, não quebra o build, e a Task 7 pode suprimi-lo no uso com `@SuppressLint("ExifInterface")` e o motivo (decisão 11: nenhuma dependência nova). Há também avisos `UseKtx` (`createBitmap`, `Bitmap.scale`) que o `core-ktx`, já dependência, resolve.
- **F24:** o spike não tocou na versão da foto. Sugestão barata para a Task 7: `version` = CRC32 dos bytes lidos (300 KB custam menos de 1 ms), ou um contador no nome do arquivo; nunca `lastModified`.
- **F25:** `PhotoPipelineRobolectricSpikeTest`, `@Config(sdk = [26, 35])`, `@GraphicsMode(NATIVE)`: 6/6 (3 por SDK). Na sdk 26 roda o caminho escolhido; na 35 também os dois do `ImageDecoder`. Os bytes batem com o aparelho (sdk 35: 236.768 B no Robolectric e no emulador API 35 para o mesmo JPEG). Custos:
  - o `android-all-instrumented-8.0.0_r4-robolectric-r1-i7.jar` tem **96 MB** e baixa na primeira execução (o `~/.m2/repository/org/robolectric/android-all-instrumented` foi de 526 para 622 MB);
  - no CI, a chave do `actions/cache` é `robolectric-<os>-<hash do app/build.gradle.kts>`: se a Task 7 não mexer nesse arquivo, a chave exata já existe, o cache restaurado não tem a sdk 26 e **não é salvo de novo**, então todo run baixaria os 96 MB. A Task 7 deve mudar a chave (por exemplo, acrescentar `-sdk26`) no mesmo commit;
  - imagens de 4000 × 3000 geradas no teste levam ~13 s por SDK no host; use 2400 × 1800 (ou menos) nos testes de host e deixe 12 MP para o aparelho.

## O que não foi provado

- **Aparelho físico** de qualquer fabricante: câmera real, fotos reais de 12 a 200 MP, seletor e câmera do One UI e de outros fabricantes, apps de câmera que ignoram `EXTRA_OUTPUT` ou devolvem só a miniatura.
- **APIs 27 e 29 a 34** (sem imagem instalada, fora do escopo do passo 1), em especial o `ImageDecoder` de reserva nessas APIs.
- **O seletor do Google Play services** nas APIs 26 a 28 (o backport do `PickVisualMedia`): nos três emuladores sem rede `isPhotoPickerAvailable` deu `false` na 26 e na 28 e quem abriu foi o DocumentsUI.
- **Fotos de verdade:** as entradas de 12 MP são sintéticas (quadrantes, formas e ruído); as câmeras do emulador dão no máximo 1392 × 1856. Os bytes de uma foto real com muito detalhe podem passar dos 241 KB medidos; os degraus de qualidade cobrem isso.
- **HEIC de câmera Android** (o HEIC do teste foi feito pelo `sips` do macOS, no estilo do iPhone). Na API 28 ele sai deitado; não há mitigação sem os ~100 MB do `ImageDecoder` com amostra. Formatos AVIF, DNG e WebP animado não foram testados.
- **Fotos só na nuvem** no seletor (a rede estava desligada).
- **Memória em aparelho com pouca RAM:** o pico foi medido no emulador (2 GiB).
- **O emulador principal (`emulator-5556`)**: não foi usado; tudo rodou em AVDs temporários das mesmas imagens `google_apis` arm64.
