## Outcome

Attach `as`, `because`, tags, and severity to every rule as stable result metadata.

## Acceptance criteria

- Metadata changes return new rule values and leave reusable builders untouched.
- Rationale appears in human and machine reports.
- Stable rule identities distinguish semantic changes from presentation changes.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/039-rule-metadata.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `rules`
- Type: `feature`
- Depends on: #5
- Blocks: #58, #59, #60
