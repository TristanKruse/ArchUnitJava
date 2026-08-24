## Outcome

Require every selected subject to belong to an approved package, layer, slice, or module policy exactly as configured.

## Acceptance criteria

- Unassigned and multiply assigned subjects are distinct violations.
- Coverage operates on types, packages, or modules with consistent exclusions.
- Generated/external subjects are excluded only by explicit policy.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/052-exhaustive-policies.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `rules`
- Type: `feature`
- Depends on: #5, #35, #38, #40, #41
- Blocks: #53
