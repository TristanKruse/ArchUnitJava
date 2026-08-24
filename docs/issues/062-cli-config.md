## Outcome

Run the same library rules from a bounded declarative configuration and stable command-line interface.

## Acceptance criteria

- Check, graph, explain, and config-validation commands have documented exit codes.
- Configuration cannot execute commands, instantiate arbitrary classes, or escape approved roots.
- CLI and Java API results are equivalent for the supported rule subset.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.

## Workflow

- Task specification: `.archunitdev/tasks/062-cli-config.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `integration`
- Type: `feature`
- Depends on: #41, #56, #58
- Blocks: #63, #69
