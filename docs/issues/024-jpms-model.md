## Outcome

Parse explicit JPMS descriptors and represent module directives separately from observed class dependencies.

## Acceptance criteria

- Requires modifiers, qualified exports/opens, uses, and providers are lossless.
- Explicit, automatic, and unnamed module identities remain distinct.
- No runtime ModuleLayer is created and unresolved directives stay inspectable.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/024-jpms-model.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `jpms`
- Type: `feature`
- Depends on: #7, #8, #9, #18
- Blocks: #31, #33, #46, #50
