## Outcome

Apply deterministic cycle checks to selected type and package projections.

## Acceptance criteria

- Selectors and exclusions affect nodes and edges consistently.
- Diagnostics report stable representative cycles without combinatorial output.
- Type-use and synthetic-edge inclusion can be configured explicitly.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/047-cycle-rules.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `rules`
- Type: `feature`
- Depends on: #5, #34, #35, #40
- Blocks: #48
