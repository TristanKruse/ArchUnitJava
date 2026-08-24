## Outcome

Parse untrusted class bytes through `java.lang.classfile` behind a backend-neutral adapter.

## Acceptance criteria

- No target class is loaded, linked, initialized, or reflected upon.
- Lazy parser failures are caught with resource and traversal context.
- Parser-specific model objects never escape the importer boundary.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/008-classfile-reader.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `import`
- Type: `research`
- Depends on: #7
- Blocks: #9, #11, #12, #21, #23, #24, #25, #30
