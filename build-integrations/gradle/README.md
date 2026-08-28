# Gradle integration contract

Wrap `GradleBuildIntegration.check(...)` in a cacheable `check`-lifecycle task whose declared task
dependencies are `classes` and, when analyzed, `testClasses`. An empty output directory fails with a
clear ordering-specific message.

Pass the project root, the reviewed ArchUnitJava configuration, and source-set output directories
directly. The bridge sorts and validates those directories, requires an exact match with the
configuration's `inputs`, and then calls the shared CLI/library contract. It never starts Gradle,
runs a nested build, loads project classes, or executes project plugins or annotation processors.

Treat all supplied paths and formats as task inputs. Result-format overrides map one-to-one to
`CliResultFormat`; graph-format overrides are accepted only for graph operations. Preserve the CLI
exit-code distinction between policy failures (`5`) and incomplete analysis (`4`).
