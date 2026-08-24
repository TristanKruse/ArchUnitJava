## Outcome

Implement stable identifiers, dependency kinds, evidence, nodes, edges, and an immutable deterministic graph.

## Acceptance criteria

- Package, type, member, module, and location identifiers reject invalid or ambiguous values.
- Parallel edges merge evidence without duplicates and all iteration is stable.
- Graph construction rejects unknown endpoints and preserves isolated nodes.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/002-graph-kernel.json`
- Executable contract overlay: `.archunitdev/contracts/002-graph-kernel/`
- Phase: `foundation`
- Type: `feature`
- Depends on: #1
- Blocks: #5, #33, #34, #65
