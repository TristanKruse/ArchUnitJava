## Outcome

Define and enforce resource limits and containment at every importer boundary.

## Acceptance criteria

- Archive entry count, compression ratio, bytes, nesting, class size, and diagnostic volume have configurable bounds.
- ZIP slip, symlink, malformed UTF-8, hostile names, and cache poisoning fixtures fail closed.
- The threat model distinguishes trusted configuration from untrusted target artifacts.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/032-input-security.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `security`
- Type: `research`
- Depends on: #7, #25, #26, #30
- Blocks: #68
