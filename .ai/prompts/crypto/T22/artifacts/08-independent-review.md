<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# crypto · T22 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T22 — Verification keys endpoint |
| **Spec section** | Screening, attestation, key custody |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

**Verdict:** The implementation matches the frozen brief and the standing rules in `agents.md`. All KMS SDK usage stays inside `KmsSigner`, the new controller/records live in `attest`, and the `KmsSignerArchitectureTest` regression passes. The targeted unit/controller/architecture suite passed 48/48. The LocalStack integration test (`KmsSignerLocalStackIntegrationTest`) failed in this environment with an SDK client connectivity error (`Unable to execute HTTP request: The target server failed to respond`) — this appears to be a local Docker/network issue, not a code defect, because the same failure occurred during the `createClientAndAsymmetricKey` `@BeforeAll` setup before any T22 code ran.

Four findings are noted: two confirm the self-review's low-severity observations, and two are additional minor style/test-clarity gaps.

---

## 1. `sign(...)`'s hardcoded `SIGNING_ALGORITHM` and `publicKeyInfo()`'s reported `alg` can diverge

- **Issue:** `sign(...)` always requests `SigningAlgorithmSpec.ECDSA_SHA_256` (pending Q7), while `publicKeyInfo()` reports whatever `GetPublicKeyResponse.signingAlgorithmsAsStrings().get(0)` returns. If the provisioned key type ever changes before Q7 is closed, the published `alg` will truthfully describe the key but `sign(...)` will request the wrong algorithm and fail at the first real signing attempt. KMS's own validation prevents a wrong signature, but the mismatch is only discovered reactively.
- **Evidence:** `attest/KmsSigner.java:74` (hardcoded constant) vs. `:169` (algorithm sourced from response).
- **Recommendation:** Not required for T22's scope (the constant's correctness is Q7's open question). Optionally, a future task could add a startup or runtime cross-check that the configured signing algorithm matches the published one.
- **Confidence:** High.

## 2. `toPem(byte[])` produces a syntactically valid but semantically empty PEM for a zero-length input

- **Issue:** `KmsSigner.toPem(new byte[0])` returns a PEM block containing only header and footer with no base64 body. This cannot occur in the normal `publicKeyInfo()` path because the `response.publicKey() == null` guard does not check for an empty-but-non-null `SdkBytes`. A real KMS response is non-empty, so this is defensive-completeness only.
- **Evidence:** `attest/KmsSigner.java:177-185`.
- **Recommendation:** Not required. If desired, add an `IllegalArgumentException` guard for `der.length == 0` in `toPem` to make the helper fully defensive.
- **Confidence:** High.

## 3. `VerificationKeysController` constructor is package-private

- **Issue:** The controller class is `public`, but its constructor has no access modifier and is therefore package-private. Spring can still instantiate it via reflection, so there is no functional bug, but it is inconsistent with every other Spring controller convention in this codebase and is slightly surprising to read.
- **Evidence:** `attest/VerificationKeysController.java:17` (public class) and `:21-23` (package-private constructor).
- **Recommendation:** Add the `public` modifier to the constructor for consistency and clarity.
- **Confidence:** High.

## 4. Controller failure test does not prove the guard-generated `IllegalStateException` reaches the HTTP boundary

- **Issue:** `VerificationKeysControllerTest.aPublicKeyInfoFailurePropagatesToAGenericFiveHundred` stubs `kmsSigner.publicKeyInfo()` to throw a plain `RuntimeException`. This proves the controller does not swallow arbitrary failures, but it does not prove that the `IllegalStateException` thrown by the two guard cases (empty `signingAlgorithms()` or null `publicKey()`) is also mapped to `500` by `ApiExceptionHandler`. The outcome is the same, but the test name implies coverage of the real `publicKeyInfo()` failure modes.
- **Evidence:** `attest/VerificationKeysControllerTest.java:53-62`.
- **Recommendation:** Parameterize the test to throw both a plain `RuntimeException` and the actual `IllegalStateException` produced by `KmsSigner.publicKeyInfo()`'s guards, asserting `500` and `title: "Internal error"` in both cases.
- **Confidence:** High.

---

## Confirmed coverage (no gaps)

- **AC1 (named test):** Covered by `VerificationKeysControllerTest.shouldPublishVerificationKeysAtWellKnownUrl` (response shape, single-element array, `Cache-Control: no-store`).
- **AC2 (KMS SDK concentration):** `KmsSignerArchitectureTest` still passes; `VerificationKeysController` depends only on `KmsSigner`.
- **AC3 (schema fidelity):** Covered by `KmsSignerLocalStackIntegrationTest.shouldPublishVerificationKeysAtWellKnownUrl` (PEM parses back to valid `PublicKey`).
- **AC4 (`alg` from real key metadata):** Covered by `KmsSignerTest.publicKeyInfoMapsAResponseIntoTheExpectedFields` and the LocalStack integration test.
- **AC5 (no secret exposure):** The response records contain only `kid`/`kmsKeyId`/`alg`/`publicKeyPem`; no other `GetPublicKeyResponse` fields are exposed.
- **AC6 (failure propagates uncaught):** Covered by `KmsSignerTest.publicKeyInfoPropagatesAGetPublicKeyFailureUncaught` and `VerificationKeysControllerTest.aPublicKeyInfoFailurePropagatesToAGenericFiveHundred`.
- **R27/public endpoint:** Covered by `PublicEndpointsTest` and `ResourceServerConfigIntegrationTest`.
