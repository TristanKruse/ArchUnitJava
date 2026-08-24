## Outcome

Calculate deterministic package/type/member/source counts and documented Java class-cohesion metrics.

## Acceptance criteria

- Counts define treatment of synthetic members, records, generated classes, blanks, comments, and missing source.
- LCOM variants state formulas and operate only where required member evidence exists.
- Thresholds use typed units and report every violating subject.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/064-source-metrics.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `metrics`
- Type: `research`
- Depends on: #9, #10, #12, #33, #35
- Blocks: #65, #67
