## Outcome

Traverse constant-pool and bootstrap references needed for complete static dependency evidence.

## Acceptance criteria

- Class literals and descriptor-bearing constants emit bounded typed evidence.
- Method handles preserve reference kind and target member signature.
- Dynamic constants retain bootstrap provenance without executing bootstrap code.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/023-constants-handles.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `extract`
- Type: `feature`
- Depends on: #8, #10, #11
- Blocks: #31
