# crypto · T22 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

```
Publish crypto-service attestation verification keys at a well-known URL (T22)
```

## Commit message

```
Publish crypto-service attestation verification keys at a well-known
URL (T22)

Complete the attest package: KmsSigner gains publicKeyInfo(), the
only new KMS SDK call this task introduces, kept inside the one class
KmsSignerArchitectureTest's existing, negative-proof-tested ArchUnit
rule permits to touch the KMS SDK at all. VerificationKeysController
publishes { keys: [ { kid, kmsKeyId, alg, publicKeyPem } ] } at
GET /.well-known/themistra-verification-keys, already public via a
PublicEndpoints entry pre-provisioned in T03. Cache-Control: no-store
ensures a key rotation is never masked by an intermediary cache.

alg is sourced from the real key's own GetPublicKeyResponse
.signingAlgorithms(), not a hardcoded literal - the most defensible
answer available while Q7 (KMS key type/algorithm) remains formally
open. kid is set equal to kmsKeyId, the simplest single-source-of-
truth answer to a spec field no document otherwise defines. Two
guards turn anomalous-but-technically-successful KMS responses (a
null public key, an empty signing-algorithms list) into named,
actionable IllegalStateExceptions instead of a raw NPE or
IndexOutOfBoundsException; a genuine GetPublicKey call failure itself
still propagates uncaught, surfacing as 500, mirroring sign()'s own
established posture.

Verified with real infrastructure, not just mocks: a LocalStack-backed
test confirms the published PEM parses back to the exact real public
key, the reported alg is genuinely "ECDSA_SHA_256" from the key's own
metadata, and sign()'s and publicKeyInfo()'s kmsKeyId values agree for
the same key - a verifier-facing correctness property a receipt's
kmsKeyId lookup depends on.

Writing this task's own HTTP-contract tests (wrong method, unmatched
sub-path) surfaced a genuine, pre-existing defect in shared code:
common.ApiExceptionHandler's catch-all converts every unmapped
framework exception - including HttpRequestMethodNotSupportedException
and NoResourceFoundException, on any controller in this service, not
just this one - into a generic 500 instead of the correct 405/404.
That file is out of this task's scope to modify; the new tests lock
in the actual current behavior with the discovery documented inline,
rather than asserting an aspirational one or silently patching shared
code.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created:**
- `services/crypto/src/main/java/com/themistra/crypto/attest/PublicKeyInfo.java`
- `services/crypto/src/main/java/com/themistra/crypto/attest/VerificationKeysResponse.java`
- `services/crypto/src/main/java/com/themistra/crypto/attest/VerificationKeysController.java`
- `services/crypto/src/test/java/com/themistra/crypto/attest/VerificationKeysControllerTest.java`

**Modified:**
- `services/crypto/src/main/java/com/themistra/crypto/attest/KmsSigner.java` — new `publicKeyInfo()`
  and `toPem(byte[])`.
- `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerTest.java` — 11 new tests
  (field-mapping, both guards, the uncaught-failure case, `toPem`'s shape and both line-wrap boundary
  cases).
- `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerLocalStackIntegrationTest.java` —
  2 new tests (the named test's real round-trip, the `kmsKeyId` parity test).

7 files changed (4 created, 3 modified), +394 lines. No migration, no schema change, no persistence —
this task reads only, never writes.

## Summary

Completes the `attest` package's third and final file per `design.md`'s own package map: a public,
unauthenticated endpoint publishing the attestation key's public component, closing the loop T20/T21
opened — a Themistra receipt is only as verifiable as its public key is discoverable. All new KMS SDK
usage stays concentrated in `KmsSigner`, verified against the pre-existing, negative-proof-tested
ArchUnit rule with zero rule changes needed.

This was the cleanest implementation phase of the four `attest`-package tasks completed so far
(T19-T22): every detail in the implementation plan (the guard order, the PEM line-wrap algorithm, the
Spring `CacheControl` builder shape) matched the real APIs on first attempt, with no mid-implementation
correction needed — a contrast with T20's constructor-wiring bug and T21's persistence-ordering/JPA-
type-mapping bugs.

Design review caught two real, if low-severity, robustness gaps (an unguarded empty algorithm list, an
unguarded null public key) and fixed both with named, actionable exceptions rather than raw NPEs. Test
review's own contract-locking suggestion (assert the endpoint's exact HTTP method/path behavior)
surfaced something genuinely valuable beyond this task's own code: `common.ApiExceptionHandler`'s
catch-all masks every framework-level routing exception into a generic `500` across the entire service,
not just this endpoint — correctly disclosed as an out-of-scope, pre-existing defect rather than
silently patched or silently ignored.

## Testing performed

- `mvn -pl services/crypto test-compile` — clean.
- `mvn -pl services/crypto test -Dtest=KmsSignerTest,VerificationKeysControllerTest,KmsSignerLocalStackIntegrationTest,KmsSignerArchitectureTest,PublicEndpointsTest,ResourceServerConfigIntegrationTest`
  — 56/56 pass.
- `mvn -pl services/crypto -am test` (full module regression) — 720 tests, 6 failures, all pre-existing
  and unrelated to this task (disclosed since T18/T19/T20/T21). Zero regressions.
- Full traceability matrix against `requirements.md`/`design.md`/`tasks.md`:
  `artifacts/12-specification-verification.md` — verdict **PASS**.

## Specification references

- **Task:** `spec/crypto-service/tasks.md`, task 22 ("Verification keys endpoint").
- **Requirements:** R24 (publish verification keys, with `kid`/`kmsKeyId`, at a well-known URL).
- **LOCKED decisions:** L11 (the clause this task enacts — verification public keys published at a
  well-known URL; `kms:Sign` itself untouched, unmodified from T20).
- **Flagged, not fixed, for a future task:** `common.ApiExceptionHandler`'s catch-all converts
  framework-level routing exceptions (wrong method, unmatched path) into `500` instead of `405`/`404`,
  across every controller in this service.
