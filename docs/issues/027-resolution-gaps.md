## Outcome

Resolve references to imported types or deterministic external stubs while surfacing classpath gaps and conflicts.

## Acceptance criteria

- Missing targets never crash graph construction or masquerade as imported types.
- Duplicate definitions preserve precedence and conflict diagnostics.
- Unsupported class versions and damaged archives fail according to caller policy.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/027-resolution-gaps.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `packaging`
- Type: `feature`
- Depends on: #9, #25, #26
- Blocks: #30
