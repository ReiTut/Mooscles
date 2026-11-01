# Documentos de desenvolvimento

Esta pasta guarda o rastro de cada mudança relevante do Mooscles, da pesquisa até a release. Os nomes de arquivo começam com a data (`AAAA-MM-DD`) e um slug curto em minúsculas com hífens, para ordenar cronologicamente e ligar as etapas de um mesmo assunto.

| Pasta | O que guarda | Quem gera | Nome |
| --- | --- | --- | --- |
| `research/` | Notas brutas de pesquisa, uma pasta por estudo | skill `deep-research` (researchers) | `research/AAAA-MM-DD-slug/<tema>.md` |
| `reports/` | Relatórios finais: pesquisas, auditorias de UI, análises de desempenho | `deep-research` (report writer), auditorias e críticas | `reports/AAAA-MM-DD-slug.md` |
| `specs/` | O desenho aprovado de uma feature ou mudança: objetivo, decisões, fora de escopo | `superpowers:brainstorming` | `specs/AAAA-MM-DD-slug-design.md` |
| `plans/` | Plano de implementação passo a passo de uma spec | `superpowers:writing-plans` | `plans/AAAA-MM-DD-slug.md` |
| `releases/` | Notas e checklist de cada versão publicada | etapa de release | `releases/vX.Y.Z.md` (ou `vX.Y.Z-buildN.md`) |

[HANDOFF.md](HANDOFF.md) é a porta de entrada para quem continua o trabalho: o estado atual, a próxima etapa, o ambiente da máquina e as armadilhas já pagas.

[ROADMAP.md](ROADMAP.md) é o mapa das fases do produto. Cada item do roadmap aponta para a spec, o plano e a release que o entregaram.

O fluxo completo, as regras de quando cada etapa é obrigatória e os critérios de pronto estão na seção "Fluxo de desenvolvimento" do [AGENTS.md](../../AGENTS.md).
