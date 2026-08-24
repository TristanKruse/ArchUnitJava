## Outcome

Emit typed dependency evidence from super types, interfaces, fields, methods, records, annotations, and generic signatures.

## Acceptance criteria

- Every edge records its exact class-file source and owning declaration.
- Erased and generic-only dependencies remain distinguishable.
- Self, duplicate, primitive, array, and external-target handling is explicit.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/019-declaration-dependencies.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `extract`
- Type: `feature`
- Depends on: #10, #11, #13, #14, #15, #16, #18
- Blocks: #31, #33
