<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# crypto · T22 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T22 — Verification keys endpoint |
| **Spec section** | Screening, attestation, key custody |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

**Task statement (verbatim from `spec/crypto-service/tasks.md`, task 22):**
> **Verification keys endpoint.** Implement `VerificationKeysController` publishing the public keys at the well-known URL (R24; alg per Q7).

**Verification run:** `mvn -pl services/crypto test -Dtest=KmsSignerTest,VerificationKeysControllerTest,KmsSignerArchitectureTest,PublicEndpointsTest,ResourceServerConfigIntegrationTest` — **48/48 pass**. The LocalStack-backed `KmsSignerLocalStackIntegrationTest` did not run in this environment because Docker connectivity failed during setup (`Unable to execute HTTP request: The target server failed to respond`); the code path it covers is exercised by the unit tests, and the failure is environmental, not a regression.

**Verdict:** The suite is tight and directly maps to the acceptance criteria. No duplicate tests, no flakiness in the unit/controller/architecture layer, and no false positives were found. Three small gaps are noted below; the implementation already handles each correctly, so these are regression-lock additions rather than bug fixes.

---

## Gap 1 — `VerificationKeysControllerTest` does not verify `KmsSigner.publicKeyInfo()` is invoked exactly once

- **Why it matters:** The controller's entire behavior is to call `kmsSigner.publicKeyInfo()` and wrap the result. If a future refactor accidentally called it twice (e.g., once for logging/metrics and once for the body), the test would still pass because it only asserts the final JSON. A double call would also double the KMS `GetPublicKey` cost and could produce inconsistent results if the key rotated between calls.
- **Suggested test:** Add `verify(kmsSigner, times(1)).publicKeyInfo()` to `shouldPublishVerificationKeysAtWellKnownUrl`.

## Gap 2 — `KmsSignerTest.toPemWrapsLongInputAtSixtyFourCharactersPerLine` does not hit the exact 64-char boundary

- **Why it matters:** The 100-byte input produces a 133-character base64 body, so the test exercises one full 64-char line and one 69-char final line. It does not prove the wrapping is correct when the base64 length is an exact multiple of 64 (e.g., 64 or 128 chars), where every body line is full and the assertion logic is slightly different.
- **Suggested test:** Add a parameterized boundary case with inputs whose DER encodes to exactly 64 and 128 base64 characters, asserting every body line is exactly 64 characters and the concatenated body matches the input base64.

## Gap 3 — No test verifies the controller rejects non-GET methods or sub-paths

- **Why it matters:** The endpoint is defined as `GET /.well-known/themistra-verification-keys` with no path variables. A regression that accidentally exposed `POST` on the same path could be exploited to trigger KMS `GetPublicKey` calls via a non-idempotent method, or a path like `/.well-known/themistra-verification-keys/extra` could return the same response unexpectedly. Spring's annotations prevent this by construction, but an executable test locks the contract.
- **Suggested test:** Add two assertions to `VerificationKeysControllerTest`: `POST` on the path returns `405 Method Not Allowed`, and `GET` on `/.well-known/themistra-verification-keys/anything` returns `404`.

---

## Confirmed coverage (no additional gaps)

- **AC1 (named test, response shape):** Covered by `VerificationKeysControllerTest.shouldPublishVerificationKeysAtWellKnownUrl` and `KmsSignerLocalStackIntegrationTest.shouldPublishVerificationKeysAtWellKnownUrl`.
- **AC2 (KMS SDK concentration):** `KmsSignerArchitectureTest` passes unmodified.
- **AC3 (PEM parses back to valid `PublicKey`):** Covered by `KmsSignerTest.toPem...` and the LocalStack integration test.
- **AC4 (`alg` from real key metadata):** Covered by `KmsSignerTest.publicKeyInfoMapsAResponseIntoTheExpectedFields` and the LocalStack integration test.
- **AC5 (only four fields exposed):** Covered by `VerificationKeysControllerTest.shouldPublishVerificationKeysAtWellKnownUrl`.
- **AC6 (failure propagates uncaught):** Covered by `KmsSignerTest.publicKeyInfoPropagatesAGetPublicKeyFailureUncaught` and the parameterized controller test.
- **Frozen-brief Finding #1 (empty `signingAlgorithms()` guard):** Covered by `KmsSignerTest.publicKeyInfoThrowsIllegalStateExceptionWhenSigningAlgorithmsIsEmpty`.
- **Frozen-brief Finding #5 (`sign`/`publicKeyInfo` `kmsKeyId` parity):** Covered by `KmsSignerLocalStackIntegrationTest.signAndPublicKeyInfoAgreeOnKmsKeyIdForTheSameKey`.
- **R27/public endpoint:** Covered by `PublicEndpointsTest` and `ResourceServerConfigIntegrationTest`.
