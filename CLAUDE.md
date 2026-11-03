# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

As regras do projeto ficam no `AGENTS.md`, compartilhado com o Codex. Edite lá, não aqui.

@AGENTS.md

## Só no Claude Code

- Um hook PreToolUse em `.claude/settings.json` bloqueia Edit/Write em `app/schemas/**/*.json`, `shared/schemas/**/*.json`, `*.jks`, `*.keystore` e `local.properties`. Se ele bloquear, a correção é subir a `version` e deixar o KSP gerar o schema, não contornar o hook.
- Antes de commitar ou lançar mudanças em `data/` ou `timer/` (em `shared/src/commonMain` ou no `:app`), `shared/src/androidMain`, `updates/`, `AppPreferences.kt` (em qualquer dos dois módulos), `shared/build.gradle.kts` ou `app/build.gradle.kts`, rode o subagente `compat-reviewer` (`.claude/agents/compat-reviewer.md`).
