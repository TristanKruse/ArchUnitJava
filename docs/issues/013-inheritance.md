## Outcome

Resolve superclass and interface relationships across imported and external type stubs without class loading.

## Acceptance criteria

- Direct and transitive hierarchy queries terminate in cycles and missing-class cases.
- Interface extension and class implementation remain distinguishable.
- Assignability reports unknown when evidence is incomplete instead of guessing.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/013-inheritance.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `model`
- Type: `feature`
- Depends on: #9, #11
- Blocks: #16, #17, #19, #37, #43, #46, #51
