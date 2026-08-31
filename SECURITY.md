# Security policy

## Supported versions

No public version has been published yet. Security fixes currently target `main` and will be
included in the first non-snapshot release. After publication, this table will identify supported
release lines explicitly.

## Reporting a vulnerability

Please use GitHub's private
[security-advisory form](https://github.com/TristanKruse/ArchUnitJava/security/advisories/new).
Do not include exploit details, malicious bytecode, credentials, or private repository content in a
public issue.

Include the affected commit or version, operating system and JDK, the smallest safe reproduction,
the expected trust boundary, and the observed impact. Reports involving target-code execution,
approved-root escapes, archive/resource-limit bypasses, cache poisoning, output injection, baseline
parser bypasses, or unexpected remote publication are especially important.

Receipt and remediation timing depend on severity and maintainer availability. Acknowledgement,
triage status, and coordinated-disclosure timing will be communicated through the private advisory.
Published artifacts are immutable, so a released vulnerability is fixed in a new version rather
than by replacing an existing Maven Central component.

The current security boundary and accepted residual risks are documented in
[`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md).
