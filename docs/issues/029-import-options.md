## Outcome

Provide immutable import options and repository-relative ignore rules shared by directories and archives.

## Acceptance criteria

- Include/exclude precedence and negation are documented and deterministic.
- Ignore files cannot include commands, environment expansion, or paths outside the root.
- Diagnostics explain which rule excluded a resource.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/029-import-options.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `import`
- Type: `feature`
- Depends on: #3, #6, #7
- Blocks: #30
