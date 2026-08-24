## Outcome

Assemble imported resources using explicit deterministic Java lookup precedence without loading classes.

## Acceptance criteria

- Each selected class records its winning input and shadowed alternatives.
- Classpath and module-path modes have separate documented rules.
- Manifest Class-Path entries are opt-in, bounded, and contained.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/025-classpath.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `packaging`
- Type: `feature`
- Depends on: #6, #7, #8
- Blocks: #26, #27, #30, #32
