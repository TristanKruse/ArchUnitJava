## Outcome

Decode field and method descriptors into a lossless immutable JVM type vocabulary.

## Acceptance criteria

- Primitive, void, reference, and multidimensional array types round-trip.
- Method parameters and returns retain exact descriptor order.
- Invalid descriptors fail locally without partial model corruption.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.
- Do not add dependencies or change Maven configuration.

## Workflow

- Task specification: `.archunitdev/tasks/011-jvm-types.json`
- Executable contract overlay: not authored; do not schedule
- Phase: `model`
- Type: `feature`
- Depends on: #8, #9
- Blocks: #13, #14, #15, #19, #20, #21, #22, #23
