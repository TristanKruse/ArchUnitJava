## Outcome

Create stable report snapshots with filtering, grouping, collapse, and evidence drill-down independent from renderers.

## Acceptance criteria

- Nodes and edges have stable IDs and snapshots are detached immutable values.
- Package, type, member, layer, slice, artifact, and module collapse retain counts.
- All renderers consume exactly the same queried snapshot.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/055-graph-snapshot.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `reporting`
- Type: `feature`
- Depends on: #33, #38
- Blocks: #56, #57, #67
