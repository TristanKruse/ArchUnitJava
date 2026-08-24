## Outcome

Interpret bounded bootstrap-method patterns to expose Java lambda and method-reference dependencies without compiler equivalence claims.

## Acceptance criteria

- LambdaMetafactory targets retain implementation handle and functional-interface evidence.
- Unknown bootstrap methods remain generic invokedynamic evidence.
- String concatenation and unrelated dynamic call sites are not mislabeled as lambdas.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/021-invokedynamic.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `extract`
- Type: `research`
- Depends on: #8, #10, #11, #20
- Blocks: #31
