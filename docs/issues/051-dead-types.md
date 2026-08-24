## Outcome

Detect bounded graph regions unreachable from selected entry points without claiming whole-program liveness.

## Acceptance criteria

- Roots, external consumers, reflection-sensitive types, and ignored subjects are explicit inputs.
- Unreachable strongly connected regions produce stable bounded findings.
- Public libraries default to conservative behavior.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/051-dead-types.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `rules`
- Type: `feature`
- Depends on: #5, #13, #20, #35, #40
- Blocks: nothing
