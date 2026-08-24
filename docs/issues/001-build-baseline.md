## Outcome

Establish the smallest cross-platform build that compiles, tests, and packages the initial ArchUnitJava project reproducibly.

## Acceptance criteria

- The Maven Wrapper is pinned with checksum verification and works on Windows and Linux.
- JDK and Maven versions are enforced and JUnit runs the smoke contract.
- CI verifies the initial project without publishing artifacts.

## Non-goals

- Do not weaken protected contracts, workflow configuration, or unrelated capabilities.
- Do not load or execute target classes, builds, plugins, annotation processors, or code.
- Do not copy implementation code from the upstream ArchUnit project.

## Workflow

- Task specification: `.archunitdev/tasks/001-build-baseline.json`
- Executable contract overlay: `.archunitdev/contracts/001-build-baseline/`
- Phase: `foundation`
- Type: `infrastructure`
- Depends on: nothing
- Blocks: #2, #3, #4, #6
