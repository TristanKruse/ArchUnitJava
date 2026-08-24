## Outcome

Render graph snapshots as safely escaped DOT and Mermaid diagrams.

## Acceptance criteria

- Hostile names cannot inject syntax, links, HTML, or directives.
- Equivalent snapshots produce byte-identical output across platforms.
- Truncation and aggregation remain visible in output metadata.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/056-diagram-renderers.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `reporting`
- Type: `feature`
- Depends on: #55
- Blocks: #62, #68
