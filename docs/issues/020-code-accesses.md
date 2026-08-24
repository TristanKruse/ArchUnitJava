## Outcome

Walk Code attributes and emit member-level access evidence with caller, target owner, descriptor, and location.

## Acceptance criteria

- Invocation opcodes, constructor calls, field reads, and field writes have distinct kinds.
- Interface, virtual, special, and static dispatch evidence is preserved without pretending to resolve runtime dispatch.
- Malformed code and missing line data do not discard valid surrounding evidence.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/020-code-accesses.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `extract`
- Type: `feature`
- Depends on: #10, #11, #12
- Blocks: #21, #22, #28, #31, #33, #45, #51
