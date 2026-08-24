## Outcome

Compile user patterns once and match paths, packages, binary names, and source-style type names with explicit semantics.

## Acceptance criteria

- Path and qualified-name separators have distinct documented glob rules.
- Regex and exact patterns share immutable matcher descriptions.
- Malformed patterns fail as user errors before analysis starts.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/003-patterns.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `foundation`
- Type: `feature`
- Depends on: #1
- Blocks: #29, #35, #38
