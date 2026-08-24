## Outcome

Implement positive and negative dependency policies over selected Java types and packages.

## Acceptance criteria

- Rules support no, only, any, and required dependencies with explicit self/external policy.
- Every violation carries representative declaration or code evidence.
- Positive and negated moods share one assertion implementation.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/041-dependency-rules.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `rules`
- Type: `feature`
- Depends on: #5, #33, #35, #38, #40
- Blocks: #46, #52, #53, #60, #62, #66
