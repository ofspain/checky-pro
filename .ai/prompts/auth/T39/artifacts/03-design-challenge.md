<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# auth · T39 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T39 — Update auth-decisions.md with Q1/O2-O5 resolutions |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

**Task statement (verbatim from `spec/auth-service/tasks.md`, task 39):**
> **Update `auth-decisions.md`.** Record decisions made while implementing (especially the resolution of Q1 and any O2–O5 choices).

Below are adversarial findings on the Phase 2 TIB. Each finding is presented as **Issue · Severity · Evidence · Recommended brief amendment**. No redesign or implementation is proposed.

---

## Finding 1 — AC2's concrete threshold values are not stated in the brief

**Issue.** AC2 requires a new entry with "the three concrete threshold values," but the brief does not list them. A reader cannot verify the entry's accuracy without opening `application.properties`. The brief should make the values explicit.

**Severity.** Low — the values exist in source, but the brief should be self-contained.

**Evidence.**
- TIB §72: "AC2 — O2: new entry with the three concrete threshold values and the MFA-folded-into-login-bucket design note."
- `services/auth/src/main/resources/application.properties` lines 104-106:
  - `themistra.auth.rate-limit.login-per-minute=10`
  - `themistra.auth.rate-limit.password-reset-per-minute=5`
  - `themistra.auth.rate-limit.oauth-token-per-minute=30`

**Recommended brief amendment.** Update AC2 to state the values explicitly:

> "AC2 — O2: new entry recording thresholds of 10 login attempts/minute, 5 password-reset confirmations/minute, and 30 OAuth2 token requests/minute, with MFA verification folded into the `/login` bucket."

---

## Finding 2 — The "MFA folded into login bucket" claim should be traced to code

**Issue.** The brief asserts that MFA verification shares the `/login` rate-limit bucket, but it does not cite the code that implements this coupling. Without a citation, the decision log entry could be vague or future readers might not understand why no separate MFA threshold property exists.

**Severity.** Low.

**Evidence.**
- TIB §72: "MFA-folded-into-login-bucket design note."
- `services/auth/src/main/java/com/themistra/auth/ratelimit/RateLimitFilter.java` lines 24-26: "`/login` (which also covers MFA verification — it happens inside the same request via `TotpAuthenticationProvider`)."
- `services/auth/src/main/resources/application.properties` lines 104-106: only three rate-limit properties exist; no MFA-specific property.

**Recommended brief amendment.** Add to AC2:

> "Cross-reference `RateLimitFilter` (lines 24-26) and `TotpAuthenticationProvider` as the mechanism that folds MFA code submission into the same `/login` request, justifying a single bucket."

---

## Finding 3 — O3's "still open" entry may be unusual for a decision log

**Issue.** AC3 explicitly requires an entry stating that the device-label source "remains unresolved." This is honest, but a decision log normally records decisions, not open questions. The brief should justify why recording an unresolved item is appropriate, or decide whether O3 should instead make a concrete choice now that implementation is complete.

**Severity.** Medium — could create confusion about whether the decision log is the right place for unresolved items.

**Evidence.**
- TIB §75-76: "AC3 — O3: new entry honestly stating the device-label source remains unresolved — `deviceLabel` is always `null` in production."
- `services/auth/src/main/java/com/themistra/auth/token/ReuseDetectingAuthorizationService.java` line 95: `tracker.trackIssuance(..., null, hash)` — confirms device label is always `null`.
- `spec/auth-service/design.md` §4b O3: still lists the choice among (a) client-supplied label, (b) User-Agent hash, (c) generic default.

**Recommended brief amendment.** Either:
- Add a note explaining that decision logs may record explicit non-decisions when a question was intentionally deferred, or
- If the human gate prefers closure, select option (c) "generic default/null" and record that as the decision, since the implementation already behaves that way.

---

## Finding 4 — O4's "by-omission" choice should explicitly note the default form's TOTP/recovery-code support

**Issue.** AC4 records the choice of the default Spring Security form login page with no custom template. The brief does not note that the default form still supports the password + TOTP/recovery-code single-request flow (via `mfaCode` form field). This is relevant because design.md O4 framed the choice around whether a custom template was needed for TOTP/recovery-code fields.

**Severity.** Low.

**Evidence.**
- TIB §77-78: "AC4 — O4: new entry recording the by-omission choice of the default Spring Security form login page, no custom template built."
- `services/auth/src/main/java/com/themistra/auth/authn/TotpAuthenticationProvider.java`: implements the single-request password+TOTP flow.
- `services/auth/src/main/java/com/themistra/auth/authn/SasLoginIntegrationTest.java` lines 476-494: posts `mfaCode` as a form field to `/login`.
- No custom Thymeleaf/html templates exist under `services/auth/src/main/resources`.

**Recommended brief amendment.** Add to AC4:

> "The default Spring Security login form is sufficient because the custom `TotpAuthenticationProvider` consumes `mfaCode` as an additional form parameter in the same `/login` POST; no custom template was required."

---

## Finding 5 — AC5 should cite the recovery-code hashing implementation directly

**Issue.** AC5 records SHA-256 as the selected recovery-code hashing primitive. The brief should cite the implementation file that confirms this, both for traceability and to ensure the decision log entry is grounded in code.

**Severity.** Low.

**Evidence.**
- TIB §79-80: "AC5 — O5: new entry recording SHA-256 as the selected recovery-code hashing primitive, matching the spec's own suggested default."
- `services/auth/src/main/java/com/themistra/auth/mfa/RecoveryCode.java` lines 15-17 and 39-41: `codeHash` is a SHA-256 hex digest, persisted as `CHAR(64)`.

**Recommended brief amendment.** Add to AC5:

> "Evidence: `RecoveryCode.codeHash` stores a SHA-256 hex digest (`RecoveryCode.java` lines 15-17, 39-41)."

---

## Finding 6 — The broader stage-level retrospective open question is unresolved

**Issue.** The brief's Open Questions section asks whether to add broader retrospective entries covering tasks #17-#38's other undocumented decisions, beyond the four named O-items. This is a genuine scope decision that the Phase 4 gate must resolve. If left unaddressed, the decision log may miss other important decisions made during implementation.

**Severity.** Medium — could result in an incomplete decision log.

**Evidence.** TIB §96-98: "Blocker for Phase 4, not for implementation planning. Whether to also add a broader stage-level retrospective entry ... genuinely undecided, carried from Phase 1."

**Recommended brief amendment.** Add a concrete recommendation or decision criteria for the Phase 4 gate, e.g.:

> "Recommendation: limit T39 to the four named O-items plus Q1 confirmation. Broader retrospective entries, if any, should be authored as a separate documentation task with its own scope, so this task remains bounded and verifiable."

---

## Finding 7 — Q1 confirmation should cite D-025 explicitly

**Issue.** AC1 requires confirming Q1 is already resolved and recorded as D-025. The brief states this but does not cite the D-025 heading or summarize its content. The new entries should explicitly reference D-025 when discussing Q1/O1.

**Severity.** Low.

**Evidence.**
- TIB §70-71: "AC1 — Q1 confirmed already resolved and recorded (D-025); explicitly stated in the new content, not silently skipped."
- `services/auth/docs/architecture/auth-decisions.md` lines 212-219: D-025 entry.

**Recommended brief amendment.** In the implementation notes or AC1, add:

> "D-025 (lines 212-219) already records the narrow KMS envelope-encryption exception for `MfaSeedEncryption`; no edit needed."

---

## Finding 8 — New entries must follow the document's full format, including verdict

**Issue.** The brief requires every new entry to match the document's established structure (Decision · Context · Alternatives · Selected Approach · Trade-offs · Impact · Reference-Project Influence). The format also includes "Accept/Modify/Reject Reason" in the header. The brief does not explicitly state whether new entries need a verdict line, which existing entries include.

**Severity.** Low.

**Evidence.**
- `services/auth/docs/architecture/auth-decisions.md` line 4: "Format: Decision · Context · Alternatives · Selected Approach · Trade-offs · Impact · Reference-Project Influence · Accept/Modify/Reject Reason."
- TIB §88-89: "every new entry must match the document's existing Decision · Context · Alternatives · Selected Approach · Trade-offs · Impact · Reference-Project Influence structure exactly."

**Recommended brief amendment.** Add "Accept/Modify/Reject Reason" to the list of required sections in the Constraints, so entries are consistent with the existing format.

---

## Summary

The brief is clear and bounded. The main amendments needed before freezing are: making O2's threshold values explicit (Finding 1), deciding how to handle the O3 unresolved item (Finding 3), and adding code citations for O4/O5 (Findings 4-5). The broader retrospective question (Finding 6) should be resolved at the Phase 4 gate.

(End of Phase 3 design challenge.)
