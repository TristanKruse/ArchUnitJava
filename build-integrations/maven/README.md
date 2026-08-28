# Maven integration contract

Bind a thin Mojo around `MavenBuildIntegration.verify(...)` to Maven's `verify` phase. The goal
must run after every relevant module has completed `compile` or `test-compile`; an empty output
directory fails with an ordering-specific message.

Pass the reactor root, the reviewed ArchUnitJava configuration, and every compiled output directory
directly. The bridge sorts and validates those directories, requires an exact match with the
configuration's `inputs`, and then calls the shared CLI/library contract. It never starts Maven,
invokes another lifecycle, loads project classes, or runs project plugins or annotation processors.

Map a check result to the documented CLI exit codes. In particular, code `5` means architecture
policy failure, while code `4` means incomplete analysis. Result-format overrides map one-to-one to
`CliResultFormat`; graph-format overrides are accepted only for graph operations.
