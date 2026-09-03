# Release status

**Decision: PUBLISHED PUBLIC BETA.** Version `0.1.0` became publicly available from Maven Central on
2026-09-01.

The immutable coordinates are `io.github.tristankruse:archunitjava:0.1.0`. The protected release
workflow uploaded a signed, user-managed deployment with automatic publishing disabled. Central
validated the deployment, a human reviewed the retained bundle, and publication was confirmed only
after the immutability warning.

The release is deliberately presented as a public beta. Publication proves that installation and
release mechanics work; it does not promote the provisional pre-1.0 Java API to a stable contract.

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
- `examples/basic` tests the current locally installed development candidate. The separate RAG
  repository resolves public `0.1.0` directly from Maven Central in its own Linux and Windows CI;
  library CI overrides that version when exercising the current development candidate.
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

## Publisher integration and v0.1.0 publication

Publisher/account setup was completed on 2026-09-01:

- Central Portal access uses the `TristanKruse` GitHub identity, and
  `io.github.tristankruse` is verified.
- The protected `maven-central` GitHub environment requires `TristanKruse` approval before release
  jobs can access its secrets.
- `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `MAVEN_GPG_PRIVATE_KEY`,
  `MAVEN_GPG_PASSPHRASE`, and `MAVEN_GPG_KEY_FINGERPRINT` are configured as environment secrets.
- The dedicated signing key has fingerprint `8F7C42989C49216FA75523251BB3BFA38C776312`.
  Its public key is available from `keyserver.ubuntu.com`; its private key and passphrase are not
  stored in the repository.
- The Central token expires on 2027-09-01 and the signing key on 2028-08-31. Rotate each credential
  before its expiry without changing published component coordinates.

Publication evidence:

- Signed tag `v0.1.0` resolves to reviewed commit
  `631e4506e99b8604b096971792fadba9e5edb55d`; independent GPG verification returned `GOODSIG` and
  `VALIDSIG` for the dedicated signing-key fingerprint.
- [GitHub Actions staging run 33523489977](https://github.com/TristanKruse/ArchUnitJava/actions/runs/33523489977)
  verified the tag, materialized `0.1.0`, ran 347 tests with zero failures or errors, signed the four
  release artifacts, uploaded the bundle, and waited for Central validation.
- Central deployment `12dfc7c7-4015-4fbf-887e-bc3103ff6560` validated one out of one components and
  reached `PUBLISHED` after explicit human confirmation.
- The retained bundle contains the primary, source, and Javadoc JARs plus the generated POM. All 16
  MD5, SHA-1, SHA-256, and SHA-512 checksum files matched, and all four detached signatures verified
  against fingerprint `8F7C42989C49216FA75523251BB3BFA38C776312`.
- The public POM and artifacts resolve from
  [Maven Central](https://central.sonatype.com/artifact/io.github.tristankruse/archunitjava/0.1.0).

For future releases, `prepare-release.yml` creates and verifies a signed tag without copying the
passphrase to a maintainer workstation. `release.yml` then checks out that immutable tag, verifies
its signature, uploads a user-managed deployment, and waits for validation. Reviewing and publishing
the validated deployment remain separate human decisions because Central versions cannot be
replaced or deleted.

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
impossible. The protected release workflow performs the corresponding credentialed upload with
`autoPublish=false`.
