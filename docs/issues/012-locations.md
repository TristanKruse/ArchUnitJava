## Outcome

Attach honest source and bytecode locations to types, members, and dependency evidence when metadata exists.

## Acceptance criteria

- Missing debug metadata is represented as absent, never fabricated.
- Line tables map offsets deterministically and handle repeated ranges.
- Locations include class resource origin without leaking machine-specific absolute paths.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/012-locations.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `model`
- Type: `feature`
- Depends on: #8
- Blocks: #20, #22, #42, #58, #64
