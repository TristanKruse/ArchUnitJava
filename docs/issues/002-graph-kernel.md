## Outcome

Implement stable identifiers, dependency kinds, evidence, nodes, edges, and an immutable deterministic graph.

## Acceptance criteria

- Package, type, module, and location identifiers use documented Java forms; member names and descriptors obey JVMS 4.2-4.3 without rejecting legal non-Java unqualified names.
- Parallel edges merge evidence without duplicates and all iteration is stable.
- Graph construction rejects unknown endpoints and preserves isolated nodes.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.
- Do not build the reusable typed JVM descriptor vocabulary reserved for issue #11; validate identity grammar only.

## Workflow

- Task specification: `.archunitdev/tasks/002-graph-kernel.json`
- Executable contract overlay: `.archunitdev/contracts/002-graph-kernel/`
- Phase: `foundation`
- Type: `feature`
- Depends on: #1
- Blocks: #5, #33, #34, #65
