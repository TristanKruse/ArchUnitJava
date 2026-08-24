## Outcome

Offer transparent composable Java presets that expand to ordinary inspectable rules.

## Acceptance criteria

- Presets never silently choose project package names.
- Expanded layers and rules can be renamed, explained, excluded, and extended.
- Clean dependency direction and provider-facing SDK boundaries have executable examples.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/053-architecture-presets.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `rules`
- Type: `feature`
- Depends on: #41, #46, #48, #49, #50, #52
- Blocks: #69
