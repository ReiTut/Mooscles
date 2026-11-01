# Medições para a fase 4: free-exercise-db contra o catálogo do Mooscles

Data: 2026-10-29. Notas brutas que alimentam a [spec da fase 4](../../specs/2026-10-29-phase4-library-routines-design.md). Mapeamento completo, linha a linha: [`mapeamento.tsv`](mapeamento.tsv).

## Fonte e método

- Dataset: [yuhonas/free-exercise-db](https://github.com/yuhonas/free-exercise-db), commit `f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5` (último push em 2026-09-27; 2.064 estrelas; não arquivado). Baixados `dist/exercises.json` (1.005.327 bytes, 876 exercícios), `LICENSE.md` e `README.md` para o scratchpad da sessão. Imagens baixadas de `raw.githubusercontent.com/yuhonas/free-exercise-db/<commit>/exercises/<id>/<n>.jpg`, presas ao commit.
- Os arquivos baixados foram tratados como dados não confiáveis: nada deles foi executado; o JSON foi lido com `python3 -I` por scripts guardados em outra pasta.
- Catálogo do Mooscles: as 287 entradas de `shared/src/commonMain/kotlin/com/mooscles/data/ExerciseCatalog.kt` na `main` em `4f41792` (ids `catalog_<stableId>`, nome em inglês, músculo, equipamento da seção).
- Casamento por nome em duas passadas:
  1. automática: nome normalizado (minúsculas, hífen e barra viram espaço, pontuação removida, plurais e sinônimos comuns como `db`/`dumbbell`, `flyes`/`fly`, `pullups`/`pull up`, palavras vazias como `with` e `the` removidas), comparado como conjunto de palavras; 61 entradas casaram exatamente. Para as outras, os 3 candidatos de maior Jaccard entre conjuntos de palavras, com bônus quando o equipamento é compatível;
  2. manual: revisei os candidatos das 287 linhas, procurei por palavra-chave nos 876 ids e li a primeira instrução dos casos ambíguos (por exemplo, `Split_Squat_with_Dumbbells` é com o pé de trás elevado, então é o búlgaro; `Preacher_Curl` usa barra EZ; `Torso_Rotation` é com bola, não máquina).
- Classes:
  - **exato:** o mesmo movimento com o mesmo implemento (diferenças só de nome ou de pegada padrão);
  - **provável:** o mesmo padrão de movimento com uma variação pequena (pegada, banco, tipo de máquina, implemento parecido), cujas fotos ilustram o movimento mas não a variação exata;
  - **sem par:** nada no dataset serve.

## Cobertura

| Classe | Entradas do catálogo | Com 2 imagens |
| --- | ---: | ---: |
| exato | 187 | 185 |
| provável | 61 | 61 |
| sem par | 39 | 0 |
| **total** | **287** | **246 (85,7%)** |

- Casam por nome (exato + provável): **248 de 287 (86,4%)**. Com mídia utilizável: **246 de 287 (85,7%)**. Só exatos com mídia: **185 de 287 (64,5%)**.
- Duas entradas casam de forma exata mas o dataset não tem imagem para elas: `catalog_kettlebell_halo` (`Kettlebell_Halo`) e `catalog_kettlebell_triceps_overhead` (`Kettlebell_Overhead_Triceps_Extension`). O dataset tem 3 exercícios sem imagem; 873 têm exatamente 2.
- As 246 entradas com mídia usam **216 ids distintos** do dataset (algumas variações dividem as mesmas fotos, por exemplo as três remadas sentadas na polia usam `Seated_Cable_Rows`).

Por equipamento (exato / provável / sem par, contando as 2 sem imagem como sem par):

| Equipamento | Exato | Provável | Sem par |
| --- | ---: | ---: | ---: |
| Barra (48) | 36 | 10 | 2 |
| Halter (56) | 40 | 11 | 5 |
| Kettlebell (22) | 11 | 5 | 6 |
| Polia (46) | 37 | 6 | 3 |
| Peso corporal (46) | 25 | 11 | 10 |
| Smith (15) | 10 | 2 | 3 |
| Máquina de pino (28) | 16 | 6 | 6 |
| Máquina de anilha (25) | 9 | 10 | 6 |
| Outro (1) | 1 | 0 | 0 |

As 41 sem mídia: `barbell_landmine_press`, `barbell_landmine_squat`, `dumbbell_squat_front`, `dumbbell_lunge_lateral`, `dumbbell_deadlift_sumo`, `dumbbell_hip_thrust`, `dumbbell_calf_raise_one_leg`, `kettlebell_lunge_reverse_racked`, `kettlebell_deadlift`, `kettlebell_deadlift_romanian`, `kettlebell_halo` (sem imagem), `kettlebell_curl`, `kettlebell_triceps_overhead` (sem imagem), `cable_curl_behind_body`, `cable_triceps_cross_body`, `cable_hip_abduction`, `bodyweight_push_up_archer`, `bodyweight_push_up_pike`, `bodyweight_bulgarian_split_squat`, `bodyweight_lunge_lateral`, `bodyweight_reverse_nordic`, `bodyweight_hip_thrust`, `bodyweight_hamstring_walkout`, `bodyweight_calf_raise`, `bodyweight_calf_raise_one_leg`, `bodyweight_bird_dog`, `smith_front_squat`, `smith_lunge_reverse`, `smith_hip_thrust`, `selectorized_lateral_raise`, `selectorized_pullover`, `selectorized_assisted_pull_up`, `selectorized_glute_kickback`, `selectorized_back_extension`, `selectorized_torso_rotation`, `plate_lat_pulldown`, `plate_pendulum_squat`, `plate_belt_squat`, `plate_leverage_squat`, `plate_leg_press_horizontal`, `plate_hip_thrust` (todos com o prefixo `catalog_`).

A classificação manual é minha e pode errar em casos de fronteira (por exemplo, `barbell_bench_spoto` como provável de `Barbell_Bench_Press_-_Medium_Grip`, ou as máquinas "de pino" casadas com máquinas "leverage" do dataset). Ela serve para dimensionar, não como tabela de produção.

## Tamanho

Todas as 432 imagens (216 ids × 2 quadros) do conjunto com mídia foram baixadas, não amostradas. Dimensões: 336 com 850×567, 32 com 850×569 e o resto variado (850×1275, 750×500, 500×750, 1280×720, 850×478, 760×760). Recodificação com `cwebp` 1.6.0 (libsharpyuv 0.4.2, `-m 6`); Pillow não está instalado e não foi preciso.

| Variante (432 quadros, 2 por exercício) | Total | Média por quadro | Mediana | p95 | Máximo |
| --- | ---: | ---: | ---: | ---: | ---: |
| JPG original | 27.186.238 B (25,93 MiB) | 62.931 B | 61.900 B | 91.286 B | 143.547 B |
| WebP tamanho original, q75 | 14.313.492 B (13,65 MiB) | 33.133 B | 31.752 B | 53.628 B | 75.830 B |
| WebP 640 px de largura, q70 | 7.924.404 B (7,56 MiB) | 18.344 B | 17.509 B | 28.880 B | 43.662 B |
| WebP 480 px de largura, q70 | 5.482.312 B (5,23 MiB) | 12.691 B | 12.036 B | 19.748 B | 30.494 B |
| WebP 160 px de largura, q70 | 1.201.646 B (1,15 MiB) | 2.782 B | 2.682 B | 4.066 B | 7.072 B |
| WebP 128 px de largura, q60 | 788.768 B (0,75 MiB) | 1.826 B | 1.753 B | 2.550 B | 4.556 B |

- **Dois quadros para as 246 entradas com mídia (216 ids):** 25,93 MiB em JPG; **7,56 MiB em WebP de 640 px q70** (cerca de 36 KB por exercício); 5,23 MiB a 480 px. Só os 185 exatos: 22,6 MiB em JPG e 6,58 MiB em WebP de 640 px.
- **Miniaturas** (só o quadro 0, recorte quadrado central, para a moldura 1:1 de 64 dp da fase 2):

  | Miniatura quadrada, 216 ids | Total | Média |
  | --- | ---: | ---: |
  | 128 px, q70 | 551.350 B (538 KiB) | 2.553 B |
  | 160 px, q70 | 751.422 B (734 KiB) | 3.479 B |
  | 192 px, q60 | 860.044 B (840 KiB) | 3.982 B |
  | 192 px, q70 | 965.784 B (943 KiB) | 4.471 B |
  | 192 px, q80 | 1.234.606 B (1.206 KiB) | 5.716 B |

  Sem recorte (o quadro inteiro, 160 px de largura), o quadro 0 dos 216 soma 600.142 B, mas a moldura é quadrada e 160×107 px recortado num quadrado vira 107 px.
- Para referência de densidade: 64 dp são 168 px num Pixel 8 (2,625x) e cerca de 180 px no S23 Ultra na resolução padrão FHD+ (cerca de 2,8x). 128 px fica abaixo disso; 160 px fica perto; 192 px cobre.

### Cabem no orçamento de APK?

- Orçamento atual (`tools/apk-budget.json`): o APK debug pode crescer até **1.048.576 B (1 MiB)** sobre a base da fase; a base ainda é a `main` de depois da fase 2 (`4f2911c`, 23.119.851 B). A fase 3 sozinha cresceu 532.492 B (0,51 MiB) com o `kotlinx-datetime` e as telas novas; o APK debug distribuído não passa por R8, então código de tela entra inteiro.
- Miniaturas embutidas custariam de 538 KiB (128 px, abaixo da densidade da moldura) a 943 KiB (192 px), mais cerca de 150 B de cabeçalho de zip por arquivo (cerca de 32 KB para 216 arquivos; um arquivo único evita isso).
- **Conclusão da medição:** as miniaturas na resolução certa (160 a 192 px) ocupam de 72% a 92% do orçamento de 1 MiB, e a fase ainda precisa de espaço para as telas de biblioteca, detalhe, editor, pastas e formulário (a fase 3 gastou 0,51 MiB só de código). **Não cabem** junto com o código da fase. Só a 128 px caberiam, e aí ficam moles na moldura de 64 dp.

## Campos do dataset úteis para o detalhe

Chaves de cada exercício: `id`, `name`, `force`, `level`, `mechanic`, `equipment`, `primaryMuscles`, `secondaryMuscles`, `instructions`, `category`, `images`.

- `primaryMuscles` / `secondaryMuscles`: 17 valores (`abdominals`, `abductors`, `adductors`, `biceps`, `calves`, `chest`, `forearms`, `glutes`, `hamstrings`, `lats`, `lower back`, `middle back`, `neck`, `quadriceps`, `shoulders`, `traps`, `triceps`). Mais comuns como primários: quadríceps 148, ombros 129, abdominais 93.
- `instructions`: lista de passos em inglês; 5 exercícios sem instrução; a maioria tem 3 a 6 passos. Nos 187 exatos: 148.984 B de JSON (30.127 B com gzip), média de 4,7 passos.
- `level`: beginner 525, intermediate 294, expert 57. `mechanic`: compound 491, isolation 298, nulo 87. `force`: push 371, pull 371, static 104, nulo 30. `equipment`: 13 valores, 77 nulos. `category`: strength 584, stretching 123 e outros.
- Não há tradução (a issue [#9 "Translations"](https://github.com/yuhonas/free-exercise-db/issues/9) está aberta).

## Licença: o Unlicense não cobre as fotos

- O repositório declara `Unlicense` (SPDX `Unlicense`, arquivo `LICENSE.md`), e o projeto de origem, [wrkout/exercises.json](https://github.com/wrkout/exercises.json), também.
- **Mas as fotos não são dos autores.** O `CONTRIBUTING.md` do projeto de origem diz, na seção "Exercise Images": "Currently all exercises have two images, these have been scrapped off the internet, therefore l do not own the copy right for these images and would advise against using them in comercial projects." ([CONTRIBUTING.md](https://github.com/wrkout/exercises.json/blob/master/CONTRIBUTING.md#exercise-images), conferido em 2026-10-29).
- O mantenedor da free-exercise-db respondeu na issue [#2 "License of Images?"](https://github.com/yuhonas/free-exercise-db/issues/2) (2023-06-27): "I actually have no idea where the images are from or if they are royalty free so usage would be at your own risk". A issue [#12](https://github.com/yuhonas/free-exercise-db/issues/12) repete a resposta. Na issue [#305 do projeto de origem](https://github.com/wrkout/exercises.json/issues/305), um comentário de 2024-06-05 aponta, por busca reversa de imagem, o bodybuilding.com como uma das fontes (não conferi essa busca).
- As fotos que vi (por exemplo, `Barbell_Deadlift/1.jpg`) têm o mesmo estúdio, o mesmo fundo vermelho e camiseta com logo, o que é coerente com uma origem comercial única.
- As instruções em texto seguem o mesmo estilo editorial ("This will be your starting position."), e a origem delas também não está documentada. Trato o texto com a mesma cautela das fotos.
- A P2 já registrava a ressalva ("o Unlicense do repositório pode não cobrir direitos de terceiros sobre as fotos originais, e isso não foi verificado"). Esta medição verificou: **o próprio autor do dataset de origem diz que não tem os direitos das fotos e desaconselha o uso comercial**. Republicá-las numa release nossa seria redistribuição, que o critério do ROADMAP ("licença que permita uso gratuito e redistribuição") exclui.
- Não é aconselhamento jurídico.

## O que estas medições ainda servem para decidir

- O tamanho de um par de quadros raster nessa resolução (cerca de 36 KB por exercício a 640 px) e de uma miniatura (3,5 a 4,5 KB a 160–192 px) é um teto razoável para qualquer conjunto fotográfico futuro. Ilustração de traço com poucas cores tende a sair menor.
- A lista dos 41 sem par mostra onde o catálogo do Mooscles é mais específico que bancos abertos (máquinas de anilha, variações de kettlebell e de peso corporal): qualquer fonte externa vai deixar buracos parecidos.
- Os 17 nomes de região muscular são vocabulário anatômico comum e servem de lista de regiões para o mapa corporal próprio.

## Comandos (resumo)

- Download: `curl` do `exercises.json`, do `README.md` e do `LICENSE.md` presos ao commit; as 432 imagens por um script Python com `urllib` (12 em paralelo), sem erro.
- Recodificação: `cwebp -q 75 -m 6` (original), `-resize 640 0 -q 70`, `-resize 480 0 -q 70`, `-resize 160 0 -q 70`, `-resize 128 0 -q 60`; miniaturas com `-crop x y s s -resize N N` centrado no menor lado (dimensões lidas com `sips`).
- Somas por um script Python que percorre as pastas. Scripts e arquivos ficaram no scratchpad da sessão e não entram no repositório.
