# WORKFLOW: Agent Operating Rules

Updated: 2026-08-05 23:26:00 +10:00

## Startup

1. Confirm current folder.
2. Read INSTRUCTION.md.
3. Read MEMORY.md and TASKS.md.
4. For development startup, read START_PROMPT.md.
5. Read only the additional files needed for the current task.
6. Check whether .git exists before using git commands or making git claims.

## File Selection

- Use AGENT.md for behavior and role.
- Use MEMORY.md for current state and prior local decisions.
- Use TASKS.md for what is active or next.
- Use START_PROMPT.md before actual development work.
- Use PROJECT_BRIEF.md for full Android/VpnService/DPI technical requirements.
- Use BLACKBOX.md only when private/provider-specific instructions are relevant.
- Use .vscode/TASKS.md for chronological work history.

## Work Rules

- Do exactly the user's requested task first.
- Do not perform code implementation during documentation-only tasks.
- Before Android development, verify the actual Windows toolchain and installed resources.
- Use Rust as the current primary implementation language. Do not use Kotlin as the main implementation language without explicit user approval.
- Before code changes, establish a baseline with relevant local commands.
- After documentation changes, re-read the changed files or search for key markers.
- After code changes, run the narrowest meaningful tests first, then broader checks if risk requires it.
- If tests or builds fail for unclear reasons after the agent's own code changes, stop and report the exact failure instead of piling on unrelated fixes.

## Git Rules

- If no .git folder exists, skip branch, commit, push, and PR operations.
- If .git exists, inspect status and branches before editing.
- Do not commit, push, or open PR without explicit user request.
- Keep .vscode/TASKS.md updated after locally verified work.

## Reporting

Every final report should include:

- changed files;
- what was verified;
- what was not run;
- blockers or missing prerequisites, if any.
