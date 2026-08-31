# Release readiness

**Decision: GO FOR USER-MANAGED STAGING** of public `0.1.0`; publication remains a separate manual
decision.

The repository can produce and locally validate `io.github.tristankruse:archunitjava:0.1.0`. The
tag-gated release workflow uploads a signed bundle to the Maven Central Portal with automatic
publishing disabled. No artifact has been published, and a validated deployment must still be
reviewed and explicitly released by a human because Maven Central artifacts are immutable.

## Evidence available

- The local audit runs 347 ordinary tests with zero failures or errors; two platform/opt-in cases
  are intentionally skipped in that suite.
- Windows and Linux CI run the full JDK 25 Maven verification and an independent RAG consumer.
- The primary JAR has a fixed output timestamp and is rebuilt byte-for-byte in CI.
- Source and Javadoc JARs are attached. Javadoc doclint enforces every category except missing
  record-component tags; Central requires real Javadoc content but does not require tag-complete
  prose.
- Package checks verify the CLI entry point, JUnit Platform service entry, and automatic module
  name.
- SpotBugs reports no analysis errors, missing classes, mutable-input exposure, null-path, resource,
  or constructor-safety findings. CI rejects every category outside the reviewed immutable-collection
  accessor and intentional natural-order baseline.
- `examples/basic` and the separate RAG repository compile and test against a locally installed
  candidate.
- The official Central Portal Maven plugin creates a user-managed bundle. GPG uses best-practices
  mode, and release CI obtains credentials and passphrases only through environment-backed GitHub
  secrets.
- Coordinates use the GitHub-verifiable `io.github.tristankruse` namespace and include license,
  developer, SCM, project, and dependency metadata required by Central.
- Performance inputs are version/digest pinned and semantic snapshots cover real open-source
  bytecode, including `package-info.class` through rules and metrics.
- The adversarial corpus verifies target-code non-execution, containment, resource limits, opaque
  caches, output escaping, baseline ingestion, and value invariants.

## Findings resolved after the first no-go review

1. Canonical baseline JSON now has a bounded strict reader for UTF-8 text, bytes, and regular files.
   It rejects unknown and duplicate fields, unknown schemas, invalid Unicode and dates, excessive
   bytes/depth/counts/strings, malformed fingerprints, and non-regular paths.
2. `package-info.class` has a valid stable type identity and now flows through the real-bytecode
   type-rule and component-metric pipeline without benchmark exclusions.
3. `JavaPattern.regex` accepts a conservative bounded subset. Unrestricted JDK regex is available
   only through the explicitly named `trustedRegex` API; CLI configuration still permits exact and
   bounded glob patterns only.
4. CSV is spreadsheet-safe by default. Lossless, non-neutralized interchange requires the explicit
   `renderMachineReadable` API and is documented as unsuitable for direct spreadsheet opening.
5. Maven metadata now uses a verifiable namespace and the build contains an official Central Portal
   staging configuration plus a tag- and confirmation-gated workflow.
6. Javadoc generation now fails on actionable doclint warnings while treating missing
   record-component tags as a documentation backlog rather than a semantic release failure.

## External prerequisites before the first staging run

These are publisher/account operations and cannot be proven by source code alone:

1. Sign in to the Central Portal through the `TristanKruse` GitHub identity and confirm that the
   automatically provisioned `io.github.tristankruse` namespace is verified.
2. Create a dedicated signing key, publish its public key, and configure the protected
   `maven-central` GitHub environment with `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`,
   `MAVEN_GPG_PRIVATE_KEY`, `MAVEN_GPG_PASSPHRASE`, and `MAVEN_GPG_KEY_FINGERPRINT` secrets.
3. Review the exact commit, create signed tag `v0.1.0`, and dispatch `release.yml` from that tag with
   version `0.1.0` and confirmation `stage`.
4. Review Central's validation result, downloaded signatures/checksums, generated POM, and consumer
   smoke test. Publishing the validated deployment requires a separate human action in Central.

## Known limitations that remain part of the 0.1.0 contract

- JDK 25 is required at runtime. Java 8–25 describes analyzed `javac` bytecode, not the runtime
  requirement.
- The lower-level public API is provisional before 1.0 and is not compatible with ArchUnit's API.
- The pinned performance corpus is modest. The project makes no absolute throughput or heap claim.
- `trustedRegex` and machine-readable CSV are intentionally sharp tools whose names and Javadocs
  expose the trust boundary.
- Static analysis cannot observe reflection-only, native, generated-at-runtime, or string-assembled
  dependencies.

## Local review commands

```shell
./mvnw --batch-mode --no-transfer-progress verify
./mvnw --batch-mode --no-transfer-progress -Prelease-candidate verify
./mvnw --batch-mode --no-transfer-progress -Prelease-candidate install
./mvnw --batch-mode --no-transfer-progress -f examples/basic/pom.xml test
```

The `release-dry-run` CI job materializes a non-snapshot version, creates a disposable signing key,
and runs the real Central plugin against an unreachable loopback endpoint. The expected upload
failure occurs only after bundle creation; CI then verifies the signed primary, source, Javadoc, and
POM entries in `central-bundle.zip`. This exercises the bundler while making a remote upload
impossible. The tag-gated release workflow performs the corresponding credentialed upload with
`autoPublish=false`.
