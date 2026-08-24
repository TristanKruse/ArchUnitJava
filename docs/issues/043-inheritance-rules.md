## Outcome

Constrain extending, implementing, assignability, and sealed-hierarchy relationships.

## Acceptance criteria

- Direct and transitive relationships are explicit.
- Unknown external ancestors cannot silently pass strict rules.
- Violations show the relevant hierarchy path or permitted-subclass evidence.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/043-inheritance-rules.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `rules`
- Type: `feature`
- Depends on: #5, #13, #16, #37, #40
- Blocks: nothing
