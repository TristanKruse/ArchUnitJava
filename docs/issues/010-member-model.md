## Outcome

Represent declared members and code units with stable JVM signatures and ownership.

## Acceptance criteria

- Overloads and constructors have unambiguous identifiers.
- Static initializers and compiler-created members remain representable.
- Member ordering is independent of class-file encounter order.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/010-member-model.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `model`
- Type: `feature`
- Depends on: #9
- Blocks: #14, #15, #16, #17, #19, #20, #21, #22, #23, #28, #36, #64
