## Outcome

Require consumers to cross package or module boundaries only through approved visible API types and members.

## Acceptance criteria

- Java visibility, package access, protected access, nestmates, and JPMS exports are not conflated.
- Violations identify the internal target and approved entry point when unambiguous.
- Reflection and runtime `--add-exports` remain documented blind spots.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/046-public-interface.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `rules`
- Type: `feature`
- Depends on: #5, #13, #18, #24, #37, #41
- Blocks: #53
