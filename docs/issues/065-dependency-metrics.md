## Outcome

Calculate architecture metrics from explicit projections with documented edge and grouping semantics.

## Acceptance criteria

- Afferent/efferent coupling, instability, abstractness, distance, CCD, ACD, RACD, and NCCD define empty cases.
- Filters affect subjects and coupling inputs consistently.
- Cycles, disconnected graphs, split packages, and module projections have formula fixtures.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/065-dependency-metrics.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `metrics`
- Type: `research`
- Depends on: #2, #33, #64
- Blocks: #67, #69
