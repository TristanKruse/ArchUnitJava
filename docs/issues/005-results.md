## Outcome

Represent rule outcomes, subjects, evidence, severity, and diagnostics independently from rendering.

## Acceptance criteria

- Violations contain structured data and stable identities, not preformatted prose.
- Equivalent results compare and iterate deterministically.
- Pass, fail, skipped, and incomplete-analysis states cannot be conflated.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/005-results.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `foundation`
- Type: `feature`
- Depends on: #2, #4
- Blocks: #39, #40, #41, #42, #43, #44, #45, #46, #47, #48, #49, #50, #51, #52, #58, #59, #60
