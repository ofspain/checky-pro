# crypto · T22 · Phase 7 — Self Review

Self-review of the Phase 6 diff against the frozen brief (`artifacts/04-frozen-task-brief.md`) and
`agents.md`. Findings only — no fixes applied here (Phase 9), per this phase's own rule. No critical or
build-breaking defects were found this time (unlike T20's constructor-wiring bug or T21's persistence-
ordering bug) — this task's implementation matched its own plan on first attempt.

## 1. `sign(...)`'s hardcoded `SIGNING_ALGORITHM` and `publicKeyInfo()`'s reported `alg` have no
   cross-consistency check, and could diverge for a real key

- **Issue:** `sign(...)` always requests `SigningAlgorithmSpec.ECDSA_SHA_256` (a fixed constant,
  documented as "pending Q7"). `publicKeyInfo()`'s `alg` field, by contrast, is sourced from the real
  key's own `GetPublicKeyResponse.signingAlgorithms()` — genuinely authoritative, not hardcoded. If the
  attestation key is ever provisioned as a different key type (e.g. RSA, still possible since Q7 remains
  open), `publicKeyInfo()` would correctly publish the key's *actual* algorithm, while `sign(...)` would
  still request `ECDSA_SHA_256` against it — KMS would reject that `Sign` call (a validation error,
  surfacing as an uncaught exception, not a silent wrong-signature risk), but the inconsistency between
  what this service *publishes* as its verification algorithm and what it *actually tries to sign with*
  would only be caught at the first real signing attempt, not at publish time or at startup.
- **Severity:** Low (KMS's own validation prevents an actual wrong-algorithm signature; this is a
  time-of-discovery/operational-clarity concern, not a correctness defect in what either method itself
  returns)
- **Evidence:** `attest/KmsSigner.java:72` (`SIGNING_ALGORITHM` constant) vs. `:161` (`alg` sourced from
  the response); no shared reference or cross-check between the two.
- **Recommendation:** Not required for this task's own scope (the constant's correctness is already
  Q7's own disclosed, accepted open question from T20). Optionally, a future task could have
  `publicKeyInfo()` assert (or log a warning) if the reported `alg` doesn't match `SIGNING_ALGORITHM`,
  surfacing a provisioning mismatch before the first real `/attest` call ever hits it.

## 2. `toPem(byte[])` produces a header-and-footer-only PEM for an empty input, with no guard

- **Issue:** `toPem(new byte[0])` returns `"-----BEGIN PUBLIC KEY-----\n-----END PUBLIC KEY-----\n"` —
  a syntactically well-formed but semantically empty/useless PEM, with no exception thrown. This
  situation cannot occur through the normal `publicKeyInfo()` call path today (the `response.publicKey()
  == null` guard, Finding #6 from Phase 3, only checks for `null`, not for a non-null-but-empty
  `SdkBytes`), but `toPem` itself is a general-purpose, package-private helper with no such guard of its
  own.
- **Severity:** Low
- **Evidence:** `attest/KmsSigner.java:174-182` (`toPem`); the `null`-only guard at `:157-160`.
- **Recommendation:** Not required — an empty-but-non-null `publicKey()` from a genuinely successful
  `GetPublicKey` call is not a realistic KMS response shape (confirmed by the LocalStack integration
  test's own real, non-empty DER bytes). Purely a defensive-completeness observation, not a real gap.

## 3. Confirmed, not a defect: `kid == kmsKeyId` is a real design choice, not a placeholder

- **Observation:** `PublicKeyInfo`'s `kid` and `kmsKeyId` fields always hold the identical value by
  construction (`new PublicKeyInfo(kmsKeyId, kmsKeyId, alg, publicKeyPem)`). This is Phase 2's own
  disclosed, Phase 3-unchallenged design proposal, not an oversight — recorded here only to confirm the
  implementation matches that decision exactly, with no drift.
- No action needed.

## 4. Confirmed, not a defect: no caching means every request incurs one live KMS round-trip

- **Observation:** `publicKeyInfo()` issues a fresh `GetPublicKey` call on every invocation, with no
  memoization. This is Phase 2's own disclosed, Phase 3-unchallenged decision (simplicity, always
  reflects a key rotation, no stated traffic-volume concern in this spec package) — confirmed
  implemented exactly as designed, not an unintentional gap.
- No action needed.

---

No thread-safety, null-handling (beyond the two already-guarded cases), or module-boundary defects were
found. `KmsSignerArchitectureTest`'s existing rules were verified to still pass with all three new files
present, confirming the KMS-SDK-concentration design held exactly as planned.
