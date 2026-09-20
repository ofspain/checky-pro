<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# crypto · T22 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T22 — Verification keys endpoint |
| **Spec section** | Screening, attestation, key custody |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

**Task statement (verbatim from `spec/crypto-service/tasks.md`, task 22):**
> **Verification keys endpoint.** Implement `VerificationKeysController` publishing the public keys at the well-known URL (R24; alg per Q7).

**Spec package:** `spec/crypto-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

---

## Findings

### 1. `alg` derived from first element of `signingAlgorithms()` with no empty-list guard

- **Issue:** The brief proposes `alg = response.signingAlgorithmsAsStrings().get(0)`. If KMS ever returns an empty list (anomalous key state, future key type, or SDK behavior change), the call will throw `IndexOutOfBoundsException` and surface as a `500` with no actionable message. The brief already documents the single-entry simplification, but it does not state what should happen if the invariant is violated.
- **Severity:** Medium
- **Evidence:** `02-task-implementation-brief.md` lines 23-27.
- **Recommended brief amendment:** Add a constraint: "If `signingAlgorithms()` is empty, throw `IllegalStateException` with a clear message naming the configured key id, then propagate uncaught. Add a unit test that stubs an empty list and asserts the exception type/message."

### 2. Private-static PEM helper conflicts with the required isolated unit test

- **Issue:** The brief mandates "A unit test for the PEM-encoding helper in isolation" but specifies the helper as `private static` inside `KmsSigner`. A test outside `KmsSigner` cannot call a private helper without reflection, which makes the mandated isolated test awkward and brittle.
- **Severity:** Low
- **Evidence:** `02-task-implementation-brief.md` lines 32-35 and 131-132.
- **Recommended brief amendment:** Change the helper visibility to package-private (`static String toPem(byte[] der)`) or extract it to a small package-private `PemEncoder` class in `attest`. The test can then live in the same package and exercise it directly.

### 3. No explicit failure-mode contract for `GetPublicKey` in the acceptance criteria

- **Issue:** The brief correctly states in the Constraints section that `GetPublicKey` failures propagate uncaught (mirroring `sign(...)`). However, this is not reflected in the AC list or Required Tests. Without an AC or test, a later refactor could add a catch-and-map that turns a KMS outage into a misleading `200` with an empty `keys` array, or a `409`, or a cached stale key.
- **Severity:** Low
- **Evidence:** `02-task-implementation-brief.md` lines 113-125 (ACs/tests) vs. lines 151-153 (constraints).
- **Recommended brief amendment:** Add **AC6** (R24 failure contract): "Any `GetPublicKey` failure propagates uncaught and results in `500`; the endpoint never returns a partial or stale `keys` array." Add a required unit test: "when `kmsClient.getPublicKey(...)` throws, the exception reaches the caller unmodified."

### 4. Response content-type and cache semantics are unspecified

- **Issue:** The wire contract is JSON, but the brief never states the response `Content-Type` should be `application/json`, nor whether cache-control headers should be present. A public well-known endpoint that performs a synchronous AWS call on every request is a natural target for caching; the brief's no-caching decision applies to server-side caching, but it does not say whether HTTP cache headers are allowed/prohibited. Ambiguity here could lead to CDN or browser caching a rotated key.
- **Severity:** Low
- **Evidence:** `02-task-implementation-brief.md` lines 47-51 and 86-87.
- **Recommended brief amendment:** Add to AC1: "Response `Content-Type` is `application/json`. The endpoint returns explicit cache-prevention headers (`Cache-Control: no-store` or equivalent) because the key may rotate and the response must always reflect the current KMS key."

### 5. `kid`/`kmsKeyId` derivation assumes `GetPublicKeyResponse.keyId()` is the canonical, stable identifier

- **Issue:** The brief sets `kid = kmsKeyId = response.keyId()`. For alias-based configurations (`alias/themistra-attestation`), KMS may return either the alias or the underlying key ARN depending on SDK version and call type. If `response.keyId()` differs from what `SignResponse.keyId()` returns on `kms:Sign`, a verifier using the receipt's `kmsKeyId` to pick an entry from the well-known array may fail to find a match. The brief does not verify this parity, even though the sign and get-public-key paths are tested separately.
- **Severity:** Low
- **Evidence:** `02-task-implementation-brief.md` lines 21-23 and 29-31.
- **Recommended brief amendment:** Add to AC1 or AC5: "`kmsKeyId` returned by `publicKeyInfo()` must match the `kmsKeyId` returned by `sign(...)` for the same configured `KmsProperties.keyId()`." Add a LocalStack integration assertion comparing `KmsSigner.sign(...).kmsKeyId()` and `KmsSigner.publicKeyInfo().kmsKeyId()` from the same key.

### 6. `publicKeyPem` nullability is not addressed

- **Issue:** The brief assumes `response.publicKey()` is always non-null. If KMS returns a response with a null public key (corrupted key, permissions issue, or future key type), the PEM helper will throw a `NullPointerException`. A brief that claims "no secret exposure" should also guard against accidental exposure of a malformed/empty payload.
- **Severity:** Low
- **Evidence:** `02-task-implementation-brief.md` lines 32-35.
- **Recommended brief amendment:** Add a constraint: "`response.publicKey()` being null is treated as an infrastructure fault: throw `IllegalStateException` (or propagate the SDK's own exception) rather than producing an empty or invalid PEM."

---

## Confirmed safe/resolved

- **L11 / KMS SDK concentration:** Extending `KmsSigner` keeps all KMS SDK usage inside the ArchUnit-permitted class; `VerificationKeysController` depends only on `KmsSigner`.
- **No caching:** The disclosed decision is consistent with the no-stated-traffic-volume premise and with always reflecting the current key.
- **Single-element array:** Honoring the wire contract's plural shape without inventing multi-key config is the correct scope control.
- **Public endpoint scope:** Reusing the existing `PublicEndpoints` allowlist matches `agents.md` and `ResourceServerConfig`.
- **Q7 not closed:** The brief correctly states it sidesteps Q7 by sourcing `alg` from KMS metadata without claiming to resolve the open question.
