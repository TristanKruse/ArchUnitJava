## Outcome

Validate selected slices/layers against a safe documented PlantUML component subset and export the current architecture.

## Acceptance criteria

- Components, aliases, arrows, and approved stereotypes parse deterministically.
- Missing and forbidden edges produce Java evidence without executing includes or macros.
- Export escapes target strings and is byte-stable.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/054-plantuml.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `rules`
- Type: `feature`
- Depends on: #48, #49
- Blocks: #69
