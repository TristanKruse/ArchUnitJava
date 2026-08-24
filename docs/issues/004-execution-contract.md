## Outcome

Define technical versus user errors, immutable check options, and the shared terminal contract used by every rule.

## Acceptance criteria

- Rule failures are values rather than exceptions.
- Technical and user errors retain causes and actionable context.
- Options have deterministic defaults and can evolve without positional parameters.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/004-execution-contract.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `foundation`
- Type: `feature`
- Depends on: #1
- Blocks: #5
