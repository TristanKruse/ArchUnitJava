## Outcome

Extract visible and invisible annotations, nested values, defaults, targets, and retention evidence from class files.

## Acceptance criteria

- Class, member, parameter, record-component, and type-use locations remain distinguishable.
- Primitive, enum, class, annotation, and array element values are lossless.
- Meta-annotation traversal is bounded and explicit about missing annotation types.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/014-annotations.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `model`
- Type: `feature`
- Depends on: #9, #10, #11
- Blocks: #16, #18, #19, #31, #37, #44
