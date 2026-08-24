## Outcome

Relabel and aggregate raw dependency evidence without losing contributing edges or isolated nodes.

## Acceptance criteria

- Projection functions are pure values and can filter, relabel, or drop edges.
- Parallel projected edges retain stable complete evidence sets.
- Package, type, member, classpath, and module projections share one deterministic contract.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/033-projection.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `projection`
- Type: `feature`
- Depends on: #2, #19, #20, #24
- Blocks: #34, #35, #41, #48, #49, #55, #64, #65
