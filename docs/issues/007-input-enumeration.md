## Outcome

Turn caller-approved locations into a bounded deterministic stream of class-file resources.

## Acceptance criteria

- Directories, JARs, and optional JRT modules retain their origin and precedence.
- Traversal does not follow symlinks or escape approved roots by default.
- Unreadable, missing, duplicate, and unsupported inputs yield typed diagnostics.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/007-input-enumeration.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `import`
- Type: `feature`
- Depends on: #6
- Blocks: #8, #24, #25, #29, #32
