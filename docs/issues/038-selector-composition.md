## Outcome

Compose selectors and exclusions without mutating reusable base scopes or changing semantics between modules.

## Acceptance criteria

- Boolean composition has typed grouping and readable deterministic descriptions.
- Exclusions apply consistently to rules, reports, metrics, layers, and slices.
- Empty and universal selectors cannot be confused accidentally.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/038-selector-composition.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `api`
- Type: `feature`
- Depends on: #3, #35, #36, #37
- Blocks: #41, #45, #48, #49, #52, #55
