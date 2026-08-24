## Outcome

Select members by owner, name, descriptor, parameters, return type, and declaring context.

## Acceptance criteria

- Overloaded members are selectable without ambiguous source rendering.
- Constructors and static initializers have explicit vocabulary.
- Member selectors compose with containing type and package selectors.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/036-member-selectors.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `api`
- Type: `feature`
- Depends on: #10, #35
- Blocks: #37, #38, #42, #45
