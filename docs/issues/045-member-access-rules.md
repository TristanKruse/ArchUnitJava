## Outcome

Constrain member-level calls and accesses between selected callers and targets.

## Acceptance criteria

- Call, constructor, read, and write policies can be selected independently.
- Violations identify caller code unit, target signature, opcode kind, and location.
- Static analysis does not claim runtime dispatch resolution.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/045-member-access-rules.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `rules`
- Type: `feature`
- Depends on: #5, #20, #36, #38, #40
- Blocks: nothing
