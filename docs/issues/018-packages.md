## Outcome

Aggregate types into Java packages while preserving package-info metadata and origin boundaries.

## Acceptance criteria

- Package identifiers distinguish named and unnamed packages.
- Package annotations and documentation-carrier classes are exposed without ordinary-type noise.
- Split packages across inputs retain every origin for later JPMS policy checks.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/018-packages.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `model`
- Type: `feature`
- Depends on: #9, #14
- Blocks: #19, #24, #35, #46
