## Outcome

Select imported Java types and packages by name, location, kind, and package patterns.

## Acceptance criteria

- Selectors are immutable, reusable, and carry readable descriptions.
- Binary, canonical, simple, package, and path matching are not conflated.
- Selection order is stable and incomplete imports remain diagnosable.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/035-type-package-selectors.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `api`
- Type: `feature`
- Depends on: #3, #9, #18, #33
- Blocks: #36, #37, #38, #40, #41, #42, #47, #50, #51, #52, #64
