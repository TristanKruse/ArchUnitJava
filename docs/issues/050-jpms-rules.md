## Outcome

Enforce architectural rules over JPMS descriptors independently from observed class dependencies.

## Acceptance criteria

- Rules cover requires/transitive/static, qualified exports/opens, uses, and provides.
- Descriptor policy and bytecode-observed dependency policy can be compared without conflation.
- Automatic and unnamed modules require explicit caller policy.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/050-jpms-rules.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `rules`
- Type: `feature`
- Depends on: #5, #24, #35, #40
- Blocks: #53
