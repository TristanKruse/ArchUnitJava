## Outcome

Implement deterministic strongly connected components and optional representative cycle enumeration.

## Acceptance criteria

- Self loops, disconnected nodes, parallel evidence, and stable ordering have fixtures.
- Default diagnostics remain bounded on dense graphs.
- Cycle computation is pure and independent from rule wording.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/034-cycle-algorithms.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `projection`
- Type: `feature`
- Depends on: #2, #33
- Blocks: #47
