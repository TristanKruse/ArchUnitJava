## Outcome

Test the full threat model against hostile bytecode, archives, paths, metadata, selectors, caches, and outputs.

## Acceptance criteria

- No target code, bootstrap method, build script, plugin, annotation processor, or static initializer executes.
- Containment, resource budgets, parser failures, output escaping, and baseline parsing fail closed.
- Residual denial-of-service and static-analysis blind spots are documented honestly.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/068-adversarial-corpus.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `hardening`
- Type: `research`
- Depends on: #32, #56, #57, #58, #59
- Blocks: #69
