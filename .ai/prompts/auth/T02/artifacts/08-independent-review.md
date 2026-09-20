# auth · T02 · Phase 8 — Independent Code Review Findings

**Task:** T02 — Resolve Q1 (TOTP encryption)  
**Consumed:** `artifacts/04-frozen-task-brief.md`, `artifacts/05-implementation-plan.md`, `artifacts/06-implementation-notes.md`, `artifacts/07-self-review.md`  
**Files reviewed:** `docs/adr/0003-narrow-kms-exception-for-totp-seed-encryption.md`, `services/auth/docs/architecture/auth-decisions.md`, `spec/auth-service/design.md`, `spec/auth-service/package.md`, `spec/auth-service/agents.md`  
**Artifact produced:** `.ai/prompts/auth/T02/artifacts/08-independent-review.md`

---

## Confirmed findings (from Phase 7 self-review, independently reproduced)

### 1. `agents.md` has redundant wording

**Issue:** The edited line reads "no AWS SDK code in application code, **except** ..." — "code" is duplicated.

**Evidence:** `spec/auth-service/agents.md:46`

**Recommendation:** Reword to: "no AWS SDK code in the service, **except** a single scoped KMS `GenerateDataKey`/`Decrypt` call inside `mfa.MfaSeedEncryption` for TOTP-seed envelope encryption ..."

**Confidence:** High.

---

### 2. `package.md` Q1 strikes the bold label, diverging from the Q6 precedent

**Issue:** In the same §11 list, Q6 keeps its bold topic label unstruck and only strikes the stale body text. Q1 instead strikes the entire bold label, reducing scannability.

**Evidence:** `spec/auth-service/package.md:148` vs. `spec/auth-service/package.md:153`

**Recommendation:** Restructure to: `Q1. **TOTP seed encryption KMS approach.** ~~<original question text>~~ **Resolved (2026-07-22):** ...`

**Confidence:** High.

---

### 3. `design.md` O1 has the same label-strikethrough inconsistency

**Issue:** The original bold label "TOTP seed encryption implementation." is struck along with the rest of the line, instead of being kept as an unstruck heading with only the stale body struck.

**Evidence:** `spec/auth-service/design.md:22`

**Recommendation:** Restructure to: `O1. **TOTP seed encryption implementation.** ~~<original proposal text>~~ **Resolved (2026-07-22):** see L14 / D-025 / ADR-0003.`

**Confidence:** High.

---

## New findings

### 4. Local-dev fallback key source is underspecified in the ADR

**Issue:** The ADR defines envelope version `0x00` as "local-profile only, no KMS call" but does not state what AES-256 key is used for that version. The implementation plan mentions a "fixed, clearly-labeled local-only AES-256 constant," but this is not carried into the ADR itself, which is the durable record.

**Evidence:** `docs/adr/0003-narrow-kms-exception-for-totp-seed-encryption.md:18` and `artifacts/05-implementation-plan.md:33-38`.

**Recommendation:** Add a paragraph in the ADR Consequences section specifying the local-dev key source (e.g. a hardcoded, clearly-documented 32-byte constant usable only in `local` profile) and noting that `dev/staging/prod` must never produce version-`0x00` envelopes. This closes a spec gap before task #16 implements it.

**Confidence:** Medium.

---

### 5. `L14` does not mention the local-dev fallback

**Issue:** `design.md` L14 states the KMS-specific mechanism as the decision, but does not mention that a local-profile version-`0x00` fallback exists per ADR-0003. A future implementer reading only L14 may assume KMS is required in every profile.

**Evidence:** `spec/auth-service/design.md:18`

**Recommendation:** Append to L14: "A `local`-profile fallback using version `0x00` envelopes (no KMS call, local-only key) is allowed for development; see ADR-0003."

**Confidence:** Medium.

---

### 6. ADR does not specify the KMS `GenerateDataKey` key spec

**Issue:** The ADR says the data key is used for "AES-256-GCM" but does not explicitly state that `GenerateDataKey` must request a 256-bit AES data key (`KeySpec AES_256` in the AWS SDK). A missing or incorrect key spec could result in a 128-bit data key while the code assumes 256 bits.

**Evidence:** `docs/adr/0003-narrow-kms-exception-for-totp-seed-encryption.md:13`, `21`.

**Recommendation:** Add one sentence in the Decision or Consequences: "`GenerateDataKey` is called with a 256-bit AES key spec; the plaintext data key must be 32 bytes."

**Confidence:** Medium.

---

### 7. AES-GCM envelope does not bind ciphertext to any context (no AAD)

**Issue:** The envelope has no associated-data field and does not bind the ciphertext to the account UUID or row identity. An attacker with write access to `mfa_enrollments` could swap `secret_encrypted` values between accounts or replay an old envelope without detection by the application (assuming they also tamper with related rows).

**Evidence:** ADR-0003 envelope layout table.

**Recommendation:** Consider whether this service's threat model requires AAD. If the platform's threat model includes a compromised-but-not-readable DB (where row values can be modified), add AAD bound to the account UUID or `mfa_enrollment.id` to prevent ciphertext swapping. If not, document the explicit threat-model assumption that DB write tampering is out of scope for this encryption layer.

**Confidence:** Low-Medium.

---

### 8. Forward test guidance from the frozen brief is not echoed in the durable records

**Issue:** The frozen brief's Required Tests section carries a non-binding note that task #16/#22 should include security-regression tests asserting the column never contains a raw seed, decrypt round-trips, and wrong/rotated keys fail correctly. This note is not present in ADR-0003 or D-025, so implementers may miss it.

**Evidence:** `artifacts/04-frozen-task-brief.md:169-174`.

**Recommendation:** Add a short "Testing implications" bullet to the ADR Consequences (or D-025 Impact) restating the future regression tests. This keeps the guidance durable after the brief is archived.

**Confidence:** Low.

---

## Verified and correct (no issue)

- **Cross-reference consistency:** `ADR-0003`, `D-025`, and `L14` resolve consistently across all five files. No stale `D-015` references remain.
- **Decision numbering:** `D-025` is the correct next id — `auth-decisions.md` already extends to `D-024`. The deviation from the Phase 5 plan's `D-015` was a factual correction, justified in the implementation notes.
- **ADR numbering:** `0003` is the correct next number after existing `0001`/`0002`.
- **Scope discipline:** No file under `services/auth/src/main/java/com/themistra/auth/mfa/` was created or modified; only the pre-existing `package-info.java` exists. `V1__auth_baseline_schema.sql` was not touched.
- **agents.md modification:** Frozen brief explicitly authorized editing `spec/auth-service/agents.md` (Phase 4, Human decision #3).
- **Config key:** `themistra.auth.mfa.seed-kek-arn` correctly maps to KMS `KeyId` and was retained unchanged as planned.
- **Secret discipline:** No literal secret or credential value was introduced.
- **Ciphertext envelope layout:** The byte-level layout (version, wrapped-key length, wrapped key, nonce, ciphertext+tag) is clearly specified.
- **KMS rotation story:** Automatic annual CMK rotation is a valid model for KMS envelope encryption and is correctly described.

---

## Summary

The T02 deliverable is technically sound and the critical Q1 blocker is resolved. The only required action is to fix the three presentation/consistency issues identified in Phase 7 (redundant wording, Q1/O1 label strikethrough). Recommended but non-blocking improvements are:

1. Document the local-dev key source in the ADR and L14.
2. Add the KMS `GenerateDataKey` key spec to the ADR.
3. Decide whether to add AAD or document the threat-model assumption.
4. Carry the forward test guidance into the ADR or D-025.

Once 1–3 above and the confirmed presentation issues are addressed, the implementation is ready for Phase 9 approval.
