## Outcome

Model exception-related dependencies from Exceptions attributes and bytecode exception tables.

## Acceptance criteria

- Declared throws and caught handler types have distinct evidence kinds.
- Catch-all/finally handlers do not manufacture a Throwable target.
- Instruction-level throw operations are reported honestly without inferring runtime types.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/022-exceptions.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `extract`
- Type: `feature`
- Depends on: #10, #11, #12, #20
- Blocks: #31
