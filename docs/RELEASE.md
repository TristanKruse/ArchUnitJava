# Release readiness

**Decision: NO-GO** for public `0.1.0` publication.

The repository can produce a reviewable `dev.archunitjava:archunitjava:0.1.0-SNAPSHOT` candidate,
but packaging completeness is not the same as product readiness. No credentials or remote repository
are configured and nothing is published by the dry run.

## Evidence available

- The current local audit runs 339 tests with zero failures or errors; two platform/opt-in cases are
  intentionally skipped in the ordinary suite.
- Windows and Linux CI run the full JDK 25 Maven verification.
- The primary JAR has a fixed output timestamp and is rebuilt byte-for-byte in CI.
- The release-candidate profile attaches source and Javadoc JARs.
- Package checks verify the CLI entry point, JUnit Platform service entry, and automatic module name.
- `examples/basic` is compiled and tested as a separate Maven consumer after local installation.
- A release-sign profile wires Maven GPG; CI activates it with signing skipped because credentials are
  intentionally absent.
- Maven deploy is exercised only against a temporary local `file:` repository supplied explicitly by
  `altDeploymentRepository`.
- Performance inputs are version/digest pinned and semantic snapshots guard optimization work.
- The adversarial corpus verifies target-code non-execution, containment, resource limits, opaque
  caches, output escaping, and baseline value invariants.

## Blocking findings

1. Persisted baseline JSON has a renderer but no bounded reader, so the documented migration cycle
   cannot span processes or CI runs.
2. Real open-source bytecode exposed that `package-info.class` imports but is rejected by some
   type-rule and component-composition graph-identity paths.
3. Regex selectors have no evaluation budget. They must remain trusted configuration or gain a safe
   subset/time budget before analysis of fully untrusted repositories is claimed.
4. CSV quoting does not neutralize spreadsheet formulas; either harden the format or prominently
   separate machine interchange from spreadsheet-safe export.
5. The current pinned performance corpus is representative but modest (383 combined classes). A
   larger application and modular multi-JAR corpus is needed before making scalability claims.
6. Real signing and a remote staging repository have not been exercised because the task explicitly
   excludes publishing credentials.
7. Javadocs are generated successfully, but the current run reports missing parameter documentation
   across public record-based APIs. The API-documentation warning backlog should be cleared before a
   polished public release.

The no-go decision can change only when the relevant fixes, regression tests, threat-model updates,
and a fresh review are committed. It must not be changed merely because the package command passes.

## Local review commands

```shell
./mvnw --batch-mode --no-transfer-progress verify
./mvnw --batch-mode --no-transfer-progress -Prelease-candidate verify
./mvnw --batch-mode --no-transfer-progress -Prelease-candidate install
./mvnw --batch-mode --no-transfer-progress -f examples/basic/pom.xml test
```

To exercise publishing mechanics without a remote system, use a disposable directory and keep GPG
disabled. For example on a Unix-like shell:

```shell
stage="$(mktemp -d)"
./mvnw --batch-mode --no-transfer-progress -Prelease-candidate,release-sign \
  -Dgpg.skip=true \
  -DaltDeploymentRepository=release-dry-run::file:"$stage" deploy
```

This writes Maven repository metadata only below the explicitly supplied local directory. A real
release requires a reviewed tag, non-snapshot version, protected signing key, approved remote staging
target, checksum/signature verification, and a separate human authorization to publish.
