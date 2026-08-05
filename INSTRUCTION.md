# INSTRUCTION: Agent Navigation Index

Version: 2026-08-05
Mode: multi-file agent basis
Project: Zapret Mobile
Root: D:\Android\VPN_app

This file is the lightweight entrypoint for any LLM agent working in this folder. Read it first, then open only the files required for the current task.

## Read Order

1. AGENT.md - role, behavior, hard boundaries.
2. MEMORY.md - current project state and durable facts.
3. TASKS.md - active task queue and completion criteria.
4. WORKFLOW.md - local working process and verification rules.
5. START_PROMPT.md - development launch prompt, Windows checks, Rust-first override.
6. BLACKBOX.md - reserved private/provider-specific instruction area.
7. PROJECT_BRIEF.md - full technical project brief; read only when needed.
8. .vscode/TASKS.md - chronological work journal.

## Routing Rules

- For a status question, read MEMORY.md, TASKS.md, and .vscode/TASKS.md.
- For role or behavior questions, read AGENT.md and WORKFLOW.md.
- For development startup, read START_PROMPT.md before PROJECT_BRIEF.md.
- For implementation planning, read START_PROMPT.md, PROJECT_BRIEF.md, TASKS.md, and WORKFLOW.md.
- For security, licensing, Android VPN behavior, DPI, build, test, or release questions, read PROJECT_BRIEF.md before answering.
- For BLACKBOX-specific work, read BLACKBOX.md; do not duplicate its contents into public files.

## Hard Rule

Do not treat this index as proof that code, tests, APKs, licenses, or builds exist. Verify the filesystem and commands before claiming completion.
