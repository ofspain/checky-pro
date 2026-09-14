# crypto · T22 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R24 — publish the verification public keys (with `kid`/`kmsKeyId`) at a well-known URL | Yes | `VerificationKeysController.verificationKeys` `attest/VerificationKeysController.java:25-30`; `KmsSigner.publicKeyInfo` `attest/KmsSigner.java:141-163` | `VerificationKeysControllerTest.shouldPublishVerificationKeysAtWellKnownUrl` (named test), `KmsSignerLocalStackIntegrationTest.shouldPublishVerificationKeysAtWellKnownUrl` (named test, real infra) | No | None. |
| L11 — "Receipts embed the key id; verification public keys are published at a well-known URL" (the clause this task enacts) | Yes | `attest/KmsSigner.java:141-163`; `KmsSignerArchitectureTest`'s pre-existing rules unmodified and still passing with the three new `attest` files present | `KmsSignerArchitectureTest` (both tests, re-run) | No | None. |
| Named test `shouldPublishVerificationKeysAtWellKnownUrl` | Yes | — | Owned by both `VerificationKeysControllerTest` (mocked) and `KmsSignerLocalStackIntegrationTest` (real infra) | No | None. |
| Frozen brief AC2 (KMS SDK concentration) | Yes | Only `KmsSigner` touches `software.amazon.awssdk.services.kms..`; `VerificationKeysController`/`PublicKeyInfo`/`VerificationKeysResponse` depend only on `KmsSigner` | `KmsSignerArchitectureTest` regression | No | None. |
| Frozen brief AC3 (PEM parses back to a valid `PublicKey`) | Yes | `KmsSigner.toPem` `attest/KmsSigner.java:174-182` | `KmsSignerTest.toPemProducesTheExpectedShapeForFixedInputBytes`, `.toPemWrapsLongInputAtSixtyFourCharactersPerLine`, `.toPemWrapsExactMultiplesOfSixtyFourCharactersWithNoPartialLine` (Phase 11); real proof in `KmsSignerLocalStackIntegrationTest` | No | None. |
| Frozen brief AC4 (`alg` from real key metadata, not hardcoded) | Yes | `attest/KmsSigner.java:161` (`response.signingAlgorithmsAsStrings().get(0)`) | `KmsSignerTest.publicKeyInfoMapsAResponseIntoTheExpectedFields`; real proof (`"ECDSA_SHA_256"` from the actual key) in `KmsSignerLocalStackIntegrationTest` | No | None. |
| Frozen brief AC5 (no secret/extra field exposure) | Yes | `PublicKeyInfo`'s fixed 4-field shape `attest/PublicKeyInfo.java` | `VerificationKeysControllerTest.shouldPublishVerificationKeysAtWellKnownUrl` | No | None. |
| Frozen brief AC6 (a `GetPublicKey` failure propagates uncaught, `500`, no partial/stale response) | Yes | No try/catch around the `GetPublicKey` call `attest/KmsSigner.java:148` | `KmsSignerTest.publicKeyInfoPropagatesAGetPublicKeyFailureUncaught`; `VerificationKeysControllerTest.aPublicKeyInfoFailurePropagatesToAGenericFiveHundred` (parameterized, Phase 9) | No | None. |
| Frozen brief Finding #1 (empty `signingAlgorithms()` guard) | Yes | `attest/KmsSigner.java:154-156` | `KmsSignerTest.publicKeyInfoThrowsIllegalStateExceptionWhenSigningAlgorithmsIsEmpty` | No | None. |
| Frozen brief Finding #5 (`sign`/`publicKeyInfo` `kmsKeyId` parity) | Yes | Both derive `kmsKeyId` from their own respective KMS response's `keyId()` | `KmsSignerLocalStackIntegrationTest.signAndPublicKeyInfoAgreeOnKmsKeyIdForTheSameKey` | No | None. |
| Frozen brief Finding #6 (null `publicKey()` guard) | Yes | `attest/KmsSigner.java:150-153` | `KmsSignerTest.publicKeyInfoThrowsIllegalStateExceptionWhenPublicKeyIsNull` | No | None. |
| R27 (unmodified) — the endpoint is genuinely public, no `internal.crypto:write` required | Yes | Already covered by the pre-existing `common.PublicEndpoints` entry; no change made | `PublicEndpointsTest.declaredPublicPathsAreNotBlockedBySecurity` (already parameterized over this exact path), `ResourceServerConfigIntegrationTest` | No | None. |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. Every file the frozen brief (Phase 4) authorized exists and is
wired in: `PublicKeyInfo`, `VerificationKeysResponse`, `VerificationKeysController`, and `KmsSigner`'s
own `publicKeyInfo()`/`toPem()` extensions. Phase 3's 6 findings (5 fully accepted, 1 partially),
Phase 9's 4 findings (1 accepted, 2 document-only, 1 rejected as factually incorrect after direct source
verification), and Phase 11's 3 gaps (all 3 accepted, one surfacing a genuine out-of-scope defect
correctly disclosed rather than silently fixed) are all resolved with cited, verified reasoning.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC6 (frozen brief) all have
implementation evidence and test coverage, including two real-infrastructure proofs
(`KmsSignerLocalStackIntegrationTest`) that go beyond mocked assertions for the criteria that most
benefit from them (AC3's PEM validity, AC4's real algorithm value, Finding #5's cross-method parity).

**(3) Does it violate any LOCKED decision?** No. L11's own clause for this task — publishing verification
keys at a well-known URL — is implemented exactly as the spec's wire shape names it. The pre-existing
`KmsSignerArchitectureTest` rule (KMS SDK usage concentrated in `KmsSigner`) was verified, not
re-litigated, to still hold with this task's three new files present. No frozen file from a prior task
(`attest/SignatureResult.java`, `attest/AttestationService.java`, `attest/AttestController.java`,
`common/PublicEndpoints.java`, `common/ResourceServerConfig.java`) was modified.

**(4) Remaining risks?**
- **Q7 (KMS key type/algorithm) remains formally open** — `KmsSigner.SIGNING_ALGORITHM`'s hardcoded
  value and `publicKeyInfo()`'s dynamically-sourced `alg` could theoretically diverge if the provisioned
  key type ever changes before Q7 closes (Self-Review/Phase 8 Finding #1, disclosed, document-only —
  KMS's own validation prevents an actual wrong-algorithm signature even if this happens).
- **No caching** — every request to the well-known endpoint issues a live `GetPublicKey` call (Phase 2's
  own disclosed, unchallenged design choice, appropriate for this endpoint's expected traffic profile).
- **A genuine, pre-existing defect in shared code was discovered, not fixed:** `common.ApiExceptionHandler`'s
  catch-all converts *every* unmapped framework exception — including a wrong HTTP method or an
  unmatched path, on *any* controller in this service, not just this task's own — into a generic `500`
  instead of the correct `405`/`404`. Out of this task's scope to fix (`common/ApiExceptionHandler.java`
  is explicitly listed as Files NOT to Modify); the new tests that discovered it lock in the actual
  current behavior rather than asserting an aspirational one, and the finding is flagged for a dedicated
  follow-up task.

## Verdict

**PASS** — every in-scope requirement, acceptance criterion, and the one relevant LOCKED decision are
implemented, tested (including real-infrastructure proofs beyond mocks for the criteria that most
benefit from them), and traced to evidence; all three review phases' findings are fully resolved,
including one rejection verified factually incorrect against source and one genuine out-of-scope defect
correctly disclosed rather than silently patched; the full module regression (720 tests) shows zero
regressions and only the same 6 pre-existing, disclosed, unrelated failing tests.
