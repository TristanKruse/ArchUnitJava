## Outcome

Parse optional Signature attributes without confusing generic references with erased JVM descriptors.

## Acceptance criteria

- Parameterized types, bounds, wildcards, arrays, and nested owners are represented.
- Malformed or absent signatures fall back to erased types with diagnostics where appropriate.
- Rules can later choose erased, generic, or combined dependency evidence.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/015-generic-signatures.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `model`
- Type: `research`
- Depends on: #9, #10, #11
- Blocks: #16, #19
