## Outcome

Establish reproducible time and memory baselines before optimization across import, cache, rules, metrics, and reports.

## Acceptance criteria

- Benchmarks report classes, members, edges, bytes, wall time, allocations or peak memory, and environment.
- Cold and warm cache results use real representative open-source corpora with recorded versions.
- Optimizations must preserve model and diagnostic snapshots.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.

## Workflow

- Task specification: `.archunitdev/tasks/067-performance.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `hardening`
- Type: `research`
- Depends on: #30, #31, #55, #64, #65
- Blocks: #69
