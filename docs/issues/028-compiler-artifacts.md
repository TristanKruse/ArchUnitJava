## Outcome

Preserve compiler-created artifacts as evidence while enabling explicit filtering and source-level presentation.

## Acceptance criteria

- Bridge and synthetic flags are retained on types, members, parameters, and accesses.
- Filtering an artifact does not silently remove the only evidence for a dependency.
- Generated-code heuristics are opt-in and never based on one annotation alone.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/028-compiler-artifacts.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `extract`
- Type: `feature`
- Depends on: #9, #10, #20
- Blocks: #31
