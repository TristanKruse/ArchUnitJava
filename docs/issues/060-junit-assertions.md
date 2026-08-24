## Outcome

Expose framework-light assertion helpers and idiomatic JUnit Jupiter usage over the shared Checkable contract.

## Acceptance criteria

- Passing rules are silent and failing rules throw one assertion failure with all bounded violations.
- Analysis errors remain distinguishable from policy failures.
- Dynamic tests and ordinary `@Test` examples work without global mutable state.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.

## Workflow

- Task specification: `.archunitdev/tasks/060-junit-assertions.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `integration`
- Type: `feature`
- Depends on: #5, #39, #40, #41
- Blocks: #61, #63, #66
