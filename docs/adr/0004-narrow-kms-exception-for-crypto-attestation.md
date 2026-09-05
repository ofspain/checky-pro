# ADR-0004: Narrow KMS exception for crypto-service attestation signing

- **Status:** accepted · 2026-08-25 · operationalizes `spec/crypto-service/design.md` L11 for `services/crypto`

## Decision

AWS KMS client use in `services/crypto` is permitted **only** inside the future
`com.themistra.crypto.attest` package, for exactly one call: `kms:Sign` on the attestation key, at
the point a proven-final, quorum-agreed, screening-cleared observation is signed into a verification
receipt (R20–R22). No other package in `services/crypto` may reference the KMS SDK or its signing
API — enforced first narrowly by task T20's own `KmsSigner`-reference ArchUnit rule, then
consolidated into the module's full boundary suite in task T25.

Unlike ADR-0003 (auth-service), this exception does not narrow a prior blanket AWS-SDK prohibition —
crypto-service carries no equivalent of auth's D-010. It exists to make explicit, at the pom-dependency
level and ahead of any code that uses it, the same discipline D-010/ADR-0003 established: an AWS SDK
dependency for signing is not a general-purpose capability of the service, it is scoped to one module,
one call, one purpose, and that scope is named in a decision record rather than left implicit in the
pom's dependency list.

The `attest` module's `KmsSigner` will call `kms:Sign` directly on ciphertext-free, already-hashed
receipt data (the KMS key never leaves KMS; no envelope-encryption pattern is needed here, unlike
ADR-0003's `GenerateDataKey`/`Decrypt` pair — attestation is a signing operation, not an
encrypt-at-rest one). Exact request shape, key algorithm, and error handling are T20's own scope, not
this ADR's.

## Context

`spec/crypto-service/agents.md` L11 states: "`kms:Sign` on the attestation key is reachable only from
the `attest` module — enforced by ArchUnit (package ban) and by IAM." Task T01 (this service's
Maven skeleton) is the first point at which the `software.amazon.awssdk:kms` dependency enters
`services/crypto/pom.xml`, several tasks before the `attest` module or its ArchUnit rule exist
(T20/T25). Adding the dependency without a named decision record would repeat the exact problem
ADR-0003 solved for auth-service: a KMS dependency present in the pom with no documented boundary,
inviting a future contributor to reach for it from wherever is convenient.

Two alternatives were considered and rejected at this task's Phase 3/4 design review (mirroring
ADR-0003's own review structure):
- **No ADR — rely on `agents.md` L11 alone.** L11 already states the rule; a dedicated ADR is
  arguably redundant. Rejected because auth-service's own precedent (ADR-0003/D-025) treats an
  AWS-SDK exception as decision-record-worthy regardless of whether a standing-rules doc already
  states the constraint — the ADR is the durable, dated record of *why*, the standing rule is the
  durable statement of *what*.
- **Defer the ADR to T20**, when `KmsSigner` and its ArchUnit rule actually exist. Rejected because
  the pom-level dependency lands in T01; leaving it uncommented and undocumented for 19 tasks invites
  exactly the "looks unprincipled" confusion Kimi's Phase 3 review (Finding 3) flagged.

## Consequences

- The crypto-service IRSA role will need `kms:Sign` scoped to the single attestation-key ARN only —
  no wildcard resource, no other KMS actions (`kms:GenerateDataKey`, `kms:Decrypt`, etc. are auth-
  service's own exception under ADR-0003 and confer no privilege here).
- `services/crypto/pom.xml`'s KMS dependency carries a comment citing this ADR, matching the pattern
  auth's pom uses for ADR-0003.
- No migration, schema, or runtime consequence yet — no `attest` module code exists until T20/T21.
- **Testing implications (for T20/T25, not this task):** the ArchUnit rule must assert no package
  outside `attest` references `KmsSigner` or `software.amazon.awssdk.services.kms.*`; T26's
  end-to-end test should assert the sign path is only reachable via the `AGREED`+finality+`CLEARED`
  gate `AttestationService` enforces.
