## Outcome

Build immutable Java type descriptions with binary/source names, kind, ownership, flags, and origin.

## Acceptance criteria

- Top-level type kinds and JVM access flags map without losing unsupported bits.
- Binary names remain canonical while readable names are derived.
- Malformed and unsupported class versions remain explicit diagnostics.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/009-type-model.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `model`
- Type: `feature`
- Depends on: #8
- Blocks: #10, #11, #13, #14, #15, #16, #17, #18, #24, #27, #28, #35, #64
