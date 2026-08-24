## Outcome

Represent Record and PermittedSubclasses attributes as first-class Java semantics.

## Acceptance criteria

- Record components retain names, descriptors, signatures, and annotations.
- Permitted subclasses are distinct from observed direct subclasses.
- Malformed or incomplete sealed hierarchies remain queryable with diagnostics.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/016-records-sealed.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `model`
- Type: `feature`
- Depends on: #9, #10, #13, #14, #15
- Blocks: #19, #31, #37, #43
