# auth · T22 · Phase 12 — Specification Verification

**Process note:** this phase's header lists `artifacts/11-test-review.md` as the input, but T22 has no Phase 10/11 artifacts — the task is test-only ("Add Testcontainers tests"), so test generation and test review were absorbed into Phase 6 (implementation) and Phase 7/8/9 (self-review, independent review, resolution) instead, the same adaptation the whole pipeline already made explicit at Phase 6. This phase consumes `artifacts/09-review-resolution.md` in practice. Noted per the guardrail to flag rather than silently paper over a mismatch.

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R24** — merchant without MFA cannot finish authorize flow | N/A — this task adds tests, doesn't implement R24 (T20 did) | `authn/TotpAuthenticationProvider.java` (T20, unchanged) | `authn/SasLoginIntegrationTest.java:350-364` (`merchantWithoutEnrollmentCannotFinishAuthorizeFlow`) — full `/oauth2/authorize` flow, asserts no code ever issued | No | None |
| **R25** — confirmed MFA requires code | N/A — same | `authn/TotpAuthenticationProvider.java` (T20, unchanged) | `:372-397` (`confirmedMfaRequiresCodeToFinishAuthorizeFlow`) — password-only fails (with the R24-matching `/login?error` assertion added in Phase 9), valid TOTP succeeds | TOTP branch only, deliberately (frozen brief disposition #7, reiterated in the test's own comment) | Recovery-code branch not re-proven at this layer — already covered at `/login` by T20; recorded as a deliberate scope decision, not an oversight |
| **R26** — correct code produces `amr: [pwd, otp]` | N/A — same | `token/TokenClaimsCustomizer.java` (T20, unchanged) | `:399-421` (`issuedTokenHasOtpAmrAndAcrAfterMfa`) — the genuinely new capability: an *actually-issued* JWT, obtained through a real `/oauth2/token` exchange, its claims parsed and asserted directly | No | None |
| L9 (claim set, consumed not re-verified) | N/A | — | Tests assert only `amr`/`acr`, per frozen brief scope reduction (contract file doesn't exist) | No | None — scope reduction was explicit and approved |
| L10 (MFA mandatory for MERCHANT/ADMIN) | N/A | — | R24/R25/R26 tests all use MERCHANT; positive control (`issuedTokenHasPwdOnlyAmrThroughFullFlowWhenMfaNotRequired`, `:424-439`) uses no role, correctly getting `amr: ["pwd"]` | No | None |

Every row is "N/A" for *implementation* because T22 implements nothing — R24/R25/R26 were implemented by T20; T22's entire contribution is proof, at a layer T20 explicitly declined to attempt, that T20's implementation actually works end-to-end.

## Beyond the three requirements: what this task actually delivered

- **Two real, previously-invisible production bugs found and fixed**, both required to make any of these tests pass at all:
  1. `oauth2_authorization` table missing 8 columns `JdbcOAuth2AuthorizationService` references unconditionally (`V6__oauth2_authorization_device_and_user_code_columns.sql`) — broke *every* query against the table, not exercised until this task drove `/oauth2/authorize` to completion for the first time.
  2. Confirmed (not assumed) that `LoginSuccessHandler`'s saved-request resume doesn't apply to SAS's authorization endpoint — a real integration fact now documented in `attemptFullAuthorizeFlow`'s Javadoc, resolved via the frozen brief's own pre-authorized contingency plan.
- **Both authorized through the pipeline's own gates**, not done silently: the schema fix was raised via `AskUserQuestion` and approved before being written; the flow-mechanism correction was inside the frozen brief's own documented contingency, not a new deviation.
- **Two independent adversarial reviews** (Phase 3 design-challenge, Phase 8 independent review) both caught real issues before/after implementation — the flow-mechanism assumption (Phase 3 finding #1) and seven additional correctness/rigor gaps (Phase 8), all resolved.

## Answers

**1. Is the task fully complete?**
Yes. All four planned tests (three named + one positive control) exist, pass individually and together, and were verified against real Postgres/Kafka/HTTP via Testcontainers — not written-but-unexecuted, which is what distinguishes this from every prior attempt at this category of test in this codebase.

**2. Does it satisfy every acceptance criterion?**
Yes — R24, R25 (TOTP branch, by deliberate, recorded scope decision), and R26 are all proven end-to-end through the real SAS interactive flow, closing the exact gap `auth-decisions.md` D-023 and T20 both declined to attempt without the ability to run it.

**3. Does it violate any LOCKED decision?**
No. L9 and L10 were both checked against the final test code, not assumed — see the matrix above.

**4. Remaining risks**
- The recovery-code branch of R25 is not proven at the full-authorize-flow layer — a deliberate, twice-recorded scope decision (frozen brief + test comment), not a gap that was missed.
- `V6`'s schema fix, while verified extensively in this environment, has not been reviewed by whoever owns this service's schema-change process outside this pipeline (if such a process exists) — worth a heads-up before merge given it's a production migration added mid-task.
- The six pre-existing, already-documented test failures elsewhere in the suite (Kafka timing, the breach-check/audit-FK ordering bug ×3, Mockito strict-stubbing ×2) remain unfixed — confirmed unrelated to this task at every checkpoint, not this task's responsibility to close.
- `contracts/api/token-claims.md` still doesn't exist — R26's test asserts `amr`/`acr` only, not a full claim-set contract that isn't authored yet. Unchanged from T20's own finding; not re-litigated.

## Verdict

**PASS** — all three scoped requirements are proven through the real, fully-wired SAS flow for the first time in this project's history; two genuine production bugs were found and fixed along the way, both explicitly authorized rather than silently introduced; no LOCKED-decision violations; the one recorded scope reduction (recovery-code branch) was a deliberate, documented call, not an oversight.
