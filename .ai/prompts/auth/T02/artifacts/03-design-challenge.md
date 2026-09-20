# auth · T02 · Phase 3 — Design Challenge Findings

**Task:** T02 — Resolve Q1 (TOTP encryption)  
**Consumed brief:** `.ai/prompts/auth/T02/artifacts/02-task-implementation-brief.md`  
**Artifact produced:** `artifacts/03-design-challenge.md`  
**Review scope:** R22, L6, L13, D-010, O1, and the T02 implementation brief.

---

## Findings against the Phase 2 TIB

### 1. Option (c) "Crypto Service" is an unstated dependency

**Issue:** The brief accepts the three O1 options from `design.md`, but option (c) refers to "a synchronous call to a Crypto Service attestation-style endpoint" that does not exist anywhere in the spec package, architecture docs, or service map. Listing it as a peer option without a contract makes the decision menu illusory.

**Severity:** High

**Evidence:** `spec/auth-service/design.md` §4b O1 describes option (c) only as "a synchronous call to a Crypto Service attestation-style endpoint." No Crypto Service is listed in `ARCHITECTURE.md`, `target-design.md` §2 module table, `package.md` §6/§7, or the contracts directory. The task dependency list in the brief only mentions ESO and the existing config placeholder.

**Recommended brief amendment:** Either remove option (c) or make it contingent on another team delivering a Crypto Service contract first. If kept, attach a concrete API contract, latency SLO, availability SLO, and auth model so the author decision is informed.

---

### 2. Option (b) is framed as a peer option despite directly violating a LOCKED decision

**Issue:** Option (b) "relax D-010 for a narrow KMS envelope-encryption call" contradicts a standing, LOCKED decision. Presenting it without an explicit escalation/ADR requirement risks the author selecting it casually and silently overriding D-010.

**Severity:** High

**Evidence:** `spec/auth-service/design.md` O1 lists option (b) as a valid choice. `auth-decisions.md` D-010: "No AWS SDK code in the service." `spec/auth-service/design.md` L13 and `agents.md` repeat "no AWS SDK secret-retrieval in application code." The brief's AC6 only requires that a D-010 relaxation be "explicit and scoped," not that it follow the ADR process or re-lock the decision.

**Recommended brief amendment:** Add a rule: selecting option (b) triggers both a new ADR in `docs/adr/` and an update to L13/agents.md with author sign-off. Do not allow it to be recorded solely inside the new `D-01N` entry.

---

### 3. No ciphertext structure or key-rotation requirement is imposed on the decision

**Issue:** The brief asks the author to pick an encryption mechanism but does not require the decision to define the serialized ciphertext layout, nonce handling, or KEK/data-key rotation. Without these, the author cannot actually unblock `mfa/` implementation.

**Severity:** High

**Evidence:** `02-task-implementation-brief.md` Outputs: "One recorded decision, with rationale, in three places." It does not list ciphertext format, nonce strategy, or key-rotation mechanics as required outputs. `mfa_enrollments.secret_encrypted BYTEA` has no companion `kek_version`/`data_key_id` column. `design.md` §4c does not define the serialized form of an encrypted seed.

**Recommended brief amendment:** Make the author decision include: (a) a VERBATIM ciphertext byte layout; (b) how the nonce/tag are stored; (c) how KEK rotation is detected and how existing seeds are re-encrypted; (d) whether a new column (e.g. `kek_version`) is needed in a future migration. Do not treat the decision as merely "pick (a/b/c)."

---

### 4. The decision output does not include the configuration surface

**Issue:** The brief notes that `MFA_SEED_KEK_ARN` "may need renaming/re-scoping" but does not require the recorded decision to specify the final configuration keys. The chosen option cannot be implemented without this.

**Severity:** Medium

**Evidence:** `02-task-implementation-brief.md` Dependencies: "Config placeholder `themistra.auth.mfa.seed-kek-arn=${MFA_SEED_KEK_ARN:}` ... may need renaming/re-scoping depending on the option selected (flagged, not decided, here)." Acceptance criteria do not mention finalizing the config shape.

**Recommended brief amendment:** Add AC: "The recorded decision specifies the exact `@ConfigurationProperties` key(s), their semantics, and their local-profile fallback (or the absence of one)." Update the verbatim config snippet in `design.md` §4c accordingly.

---

### 5. The V1 inline-comment staleness is acknowledged but not mitigated

**Issue:** The brief says the V1 migration's inline comment describing `secret_encrypted` may become stale but will not be edited. An immutable comment that contradicts the implemented behavior is a long-term maintainability hazard.

**Severity:** Low

**Evidence:** `02-task-implementation-brief.md` Files NOT to Modify: "`V1__auth_baseline_schema.sql` (LOCKED, L1) — even though its comment may become stale relative to the chosen option, it is not edited by this task." Open Questions: "V1 comment staleness — non-blocking, but should be acknowledged in the `auth-decisions.md` entry."

**Recommended brief amendment:** In addition to acknowledging the staleness, add the correct description to the new `D-01N` entry and consider adding an explanatory comment to the next migration file (`V5`) or a `README.md` in `db/migration/` so future maintainers do not rely on the stale V1 comment.

---

### 6. No local-development KEK story is required

**Issue:** L13 allows ESO injection only; local dev cannot use ESO. The brief does not require the author decision to specify how developers obtain a valid KEK, leaving a boot-time gap in `local` profile.

**Severity:** Low

**Evidence:** `02-task-implementation-brief.md` Constraints: "whichever option is selected must keep TOTP seeds encrypted at rest and never expose key material in code, logs, or committed config (L13)." It does not address the legitimate local-dev need for a non-ESO key. `design.md` §4c has `${MFA_SEED_KEK_ARN:}` defaulting to empty.

**Recommended brief amendment:** Require the decision to state the local-profile source (e.g., `MFA_SEED_ENCRYPTION_KEY` env var checked only in `local`, or a boot-time generator with a loud warning) and the startup-guard rule that refuses empty values in `dev/staging/prod`.

---

### 7. The task mischaracterizes R22 as being about secret generation

**Issue:** The brief's Business Rules section says "R22 — TOTP secret generation must 'encrypt it' before persisting; this task determines the mechanism, not the endpoint behavior." R22 is actually a user-facing endpoint acceptance criterion, not just a secret-generation rule. Framing it narrowly may cause the future implementer to miss R22's full scope.

**Severity:** Low

**Evidence:** `spec/auth-service/requirements.md` R22: "WHEN an authenticated user without a confirmed TOTP enrollment calls `POST /accounts/me/mfa/totp`, THEN the system SHALL generate a random TOTP secret, encrypt it, persist it as unconfirmed, and return an `otpauth://` provisioning URI." This includes endpoint, persistence state, and response, not just generation.

**Recommended brief amendment:** Reword the Business Rules bullet to: "R22 requires that the `POST /accounts/me/mfa/totp` endpoint generate, encrypt, persist, and expose a TOTP seed. This task determines only the encryption mechanism; endpoint behavior remains out of scope."

---

### 8. No hint of how the chosen option will be verified later

**Issue:** The brief produces no code and no tests, but the acceptance of the eventual MFA implementation will depend on verifying that seeds are actually encrypted at rest. The brief could usefully signal what future tests are expected.

**Severity:** Low

**Evidence:** `02-task-implementation-brief.md` Required Tests: "None — no code is authorized." Acceptance criteria do not mention future verification.

**Recommended brief amendment:** Add a non-binding note to the TIB or the new `D-01N` entry stating the future security-regression tests that should validate the chosen mechanism (e.g. column does not contain raw seed, decrypt round-trip works, wrong key fails in an expected way).

---

### 9. The guardrail conflict is correctly flagged but not escalated

**Issue:** The brief correctly notes the tension between the task requirement to edit `spec/` files and the Phase 3 guardrail "never modify the specification files under `spec/`". However, it leaves the resolution to Phase 4 without identifying the actual risk of inconsistent edits.

**Severity:** Low (process note)

**Evidence:** `02-task-implementation-brief.md` Files to Modify note: "This brief lists them because the task statement requires it; whether this guardrail yields to the task statement is the human decision this brief flags for Phase 4 — not assumed here."

**Recommended brief amendment:** Recommend that the human decide a single source-of-truth workflow: either (a) the author updates `spec/` files in Phase 4 and subsequent phases treat them as read-only, or (b) all spec edits are batched in a single author PR and later model phases are forbidden from touching them. State this explicitly so Phase 4 does not produce a half-resolved spec.

---

### 10. No fallback if the author cannot decide

**Issue:** The brief states Q1 is a blocker and the decision is human-only, but it provides no fallback if the author is unavailable. This could indefinitely stall Phase 1.

**Severity:** Low

**Evidence:** `02-task-implementation-brief.md` Constraints: "the decision must be genuinely made by the author (human), not inferred or defaulted by the model." No alternative is offered.

**Recommended brief amendment:** Add an explicit fallback: if the author cannot respond, the default is option (a) local AES-GCM with an ESO-injected key, because it is the only option that respects all existing LOCKED decisions without adding new dependencies. The author may still override this at Phase 4.

---

## Findings the TIB inherited from the spec (still unblocked by the decision)

The TIB is mostly sound, but it cannot itself fix the underlying specification gaps. These should be addressed either in the TIB's amended acceptance criteria or in the Phase 4 author update:

### 11. Seed entropy is still undefined

**Issue:** RFC 6238 does not mandate TOTP secret size. The spec says "random TOTP secret" without length, risking weak implementations.

**Severity:** Medium

**Evidence:** `spec/auth-service/requirements.md` R22. `target-design.md` §4.

**Recommended brief amendment:** Require the decision to fix the seed length, e.g. 20 bytes (160 bits) for HMAC-SHA1, matching common authenticator practice.

---

### 12. Recovery-code format is still undefined

**Issue:** L6 says recovery codes are "random single-use values" but does not specify length, encoding, or entropy.

**Severity:** Medium

**Evidence:** `spec/auth-service/design.md` L6. `requirements.md` R23.

**Recommended brief amendment:** Define recovery codes as, e.g., 10 random bytes Base32-encoded to 16 characters (80 bits), and require constant-time hash comparison.

---

### 13. MFA failures and brute-force lockout are not connected

**Issue:** The 5-attempt lockout rule applies to password failures but the spec does not state whether failed TOTP/recovery-code attempts also increment the lockout counter. This leaves a brute-force channel against the second factor.

**Severity:** High

**Evidence:** `spec/auth-service/requirements.md` R16–R21 (lockout) versus R29 (MFA failure audit only).

**Recommended brief amendment:** The author decision should not address this directly, but the TIB should flag it for the next MFA implementation task: add a rule that failed MFA attempts contribute to the per-account lockout/rate-limit bucket, or document a separate MFA-specific rate limit with rationale.

---

## Summary

The Phase 2 TIB correctly identifies Q1 as a blocker and correctly avoids writing code. Its main weaknesses are:

1. **Option (c) is not a real option** — no Crypto Service exists.
2. **Option (b) needs stronger guardrails** — selecting it requires an ADR and a L13/agents.md update, not just a note.
3. **The decision must include ciphertext format and rotation** — otherwise it does not unblock implementation.
4. **The decision must fix the config surface** — including local-dev key provisioning.
5. **Inherited spec gaps** (seed/recovery-code entropy, MFA failures vs. lockout) remain and should be folded into the Phase 4 author update.

Once these findings are addressed, the brief can be frozen for Phase 4 author approval.
