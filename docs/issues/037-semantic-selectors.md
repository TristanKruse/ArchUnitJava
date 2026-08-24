## Outcome

Expose Java semantic predicates over types and members while retaining unknown hierarchy states.

## Acceptance criteria

- Visibility, static/final/abstract/synthetic, annotation, assignability, record, and sealed predicates compose.
- Direct, meta, inherited, and type-use annotation matching are explicit choices.
- Missing external hierarchy evidence follows caller-selected unknown policy.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/037-semantic-selectors.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `api`
- Type: `feature`
- Depends on: #13, #14, #16, #17, #35, #36
- Blocks: #38, #43, #44, #46
