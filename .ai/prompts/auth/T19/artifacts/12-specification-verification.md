# auth · T19 — Phase 12: Specification Verification

Consumes all prior T19 artifacts (Phases 0–11). Compares the final implementation and tests
against `requirements.md`, `design.md`, `package.md`, and `tasks.md` for this task only (R22, R23,
R28, R29, R49, L6, L11, L12). Also disposes Kimi's 5 Phase 11 test-review findings, each
independently re-verified against source first, since this pipeline has no separate numbered
"test review resolution" phase between 11 and 12 (confirmed against T18's own precedent — its
Phase 11 findings were folded directly into its Phase 12 artifact, not a standalone file).

---

## Phase 11 (Kimi Test Review) — dispositions

| # | Gap | Verified | Disposition | Resolution |
|---|---|---|---|---|
| 1 | `auth.yaml` still doesn't document error responses | Re-parsed live: still 0/30 pre-existing, non-MFA operations document any 4xx/5xx. Kimi's own evidence text claims "the frozen brief required explicit error-response documentation" — **this is now stale/inaccurate**: `04-frozen-task-brief.md` (the artifact Kimi cites) explicitly overrode that requirement at its own Finding 5 disposition ("document only each endpoint's real success response... No error schema is introduced"), *within the same frozen document* Kimi is quoting. | **REJECTED, third restatement** | This is Kimi's own Phase 3 Finding 5, rejected at Phase 4; restated as Phase 8 Finding 2b, rejected again at Phase 9 (same grounds, re-verified); restated a third time here. A finding restated three times with no new evidence, citing a requirement its own source document already overrode, is not a signal to reconsider. |
| 2 | No test verifies `security: [bearerAuth]` presence | Genuinely new, not previously raised — accurate. Kimi's own suggested action also proposed a service-wide version ("every non-public controller route"), which is out of this task's scope. | **ACCEPTED (scoped)** | Added `AuthOpenApiContractTest.mfaOperationsDeclareExplicitBearerAuthSecurity` — guards only the four MFA routes T19 owns, not a service-wide rule. Negative-proofed (temporarily removed one route's `security:` block, confirmed the test fails with a clear message, reverted, `git diff` empty). 11/11 `AuthOpenApiContractTest` tests green. |
| 3 | Frozen brief vs. implemented password-mismatch status still in contradiction | Re-read `04-frozen-task-brief.md` directly: **already resolved** — the Phase 9 addendum at the top of that exact file records the correction, cross-referencing `09-review-resolution.md`. Confirmed still present, unchanged, not reverted. | **ALREADY RESOLVED, Kimi's Phase 11 review did not pick up the Phase 9 addendum** | No new action. The addendum predates this Phase 11 review's own commit (`ab4cd14b` Phase 9 landed before `0f80c377` Phase 11) — the restatement reflects a review gap on Kimi's side, not an unresolved issue on this side. |
| 4 | "Maven verification claim cannot be confirmed in this environment" | Kimi self-disclosed its own sandbox lacks `mvn` (honest limitation, same pattern as T40's own self-disclosed Kimi gap). This session's environment has both Maven and Docker and has run the full suite live, repeatedly, throughout Phases 6–11. | **ADDRESSED WITH DIRECT EVIDENCE** | Re-ran the exact Phase 10 command live, again, for this phase (see Verification below) — 89/89 plus the new security-regression test, all pass. |
| 5 | `AuthOpenApiContractTest` doesn't cover `security`/error responses (Kimi's own assessment: "not a T19-specific gap... acceptable") | Accurate, and Kimi itself recommends no action for T19. | **ACKNOWLEDGED, no disagreement** | No action; Gap 2's scoped fix above already closes the `security` half for this task's own four routes. |

### A finding of my own, surfaced while verifying Kimi's Phase 11 review against the real spec

Kimi's **Phase 8** "Cross-check against acceptance criteria" table (not a Phase 11 finding, but
caught only now while assembling this phase's own real traceability matrix) lists AC1–AC10 with
content that **does not match** `01-specification-extraction.md`'s actual AC1–AC10 text at all —
e.g. Kimi's "AC6 — RFC 9457 error responses" vs. the real AC6 ("a wrong password or wrong code on
disable maps to a real response, nothing removed"); Kimi's "AC9 — contract updated" vs. the real
AC9 ("none of the four endpoints appear in `PublicEndpoints.java`", L11). Every one of Kimi's 10
rows is checking a *plausible-sounding but fabricated* AC list, not the task's own real one. This
did not affect Phase 9's dispositions (those addressed Findings 1–8's actual text, each verified
independently against source), but it means that table's ✅/⚠️ marks themselves carry no evidential
weight and should not be read as a real cross-check. **The real cross-check is built fresh below.**

## Requirement Traceability Matrix

| Requirement | Implemented? | Evidence | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R22** — begin-enroll: unenrolled caller → generate/encrypt/persist unconfirmed, return provisioning URI | Yes | `MfaController.beginEnroll` → `MfaService.beginEnroll` (unchanged, T16) | Yes — `MfaControllerTest` (4), `MfaControllerIntegrationTest.shouldReturnTotpProvisioningUriOnEnrollmentBegin` (named) + `beginEnrollRejectsWhenAlreadyEnrolled` | No | None |
| **R23** — confirm: correct code → confirm enrollment, generate 10 hashed single-use codes, return raw once | Yes | `MfaController.confirm` → `MfaService.confirm` (unchanged, T17) | Yes — `MfaControllerTest` (3), `MfaControllerIntegrationTest.shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` (named) + `confirmRejectsWrongCode` | No | None |
| **R28** — disable: password + TOTP → remove enrollment, invalidate all codes, audit | Yes | `MfaController.disable` → `MfaService.disable` (unchanged, T18) | Yes — `MfaControllerTest` (3), `MfaControllerIntegrationTest.shouldRequirePasswordAndTotpToDisableMfa` (named) + 2 failure paths | No | None |
| **R29** — any TOTP/recovery-code verification failure records `mfa.failed`, denies | Yes | `MfaService.confirm`/`disable`/`regenerateRecoveryCodes`'s shared wrong-code branch (unchanged pattern, T16–T18, extended to the new method) | Yes — covered per-method across `MfaServiceTest`, plus the integration wrong-code tests for confirm/disable/regenerate | No | None |
| **R49** — regenerate: password + TOTP → invalidate every existing code, generate+return 10 new ones once, audit; wrong credential → audit failure, mutate nothing | Yes | `MfaService.regenerateRecoveryCodes` (new, T19), `MfaController.regenerateRecoveryCodes` (new, T19) | Yes — 7 unit tests, 1 controller test set (3), `MfaControllerIntegrationTest.shouldRegenerateRecoveryCodesWithPasswordAndTotp` (named) + 2 failure paths, 2 persistence-level tests (old-code invalidation proven, concurrent-call behavior characterized) | No | None |
| **L6** — RFC 6238 algorithm; recovery codes random, SHA-256-hashed only | Yes (unchanged — T19 reuses `TotpVerifier`/`Hashing.sha256` as-is) | `MfaService.regenerateRecoveryCodes` calls the same `totpVerifier.verify`/`generateRawRecoveryCode`/`Hashing.sha256` primitives `confirm` already uses | Yes — covered by `TotpVerifierTest` (T16, untouched) plus this task's own format/entropy assertions on regenerated codes | No | None |
| **L11** — public-endpoint discipline; new public path must be added to `PublicEndpoints.java` | Yes — none of the four MFA endpoints were added | `PublicEndpoints.METHOD_SCOPED` unmodified by this task | Yes — `MfaControllerIntegrationTest.noneOfTheFourEndpointsArePublic` (direct assertion), `ArchitectureTest.shouldEnforcePublicEndpointAllowlist` (static, re-verified Phase 7/9 via the standalone runner) | No | None |
| **L12** — no cross-module entity imports; module boundaries ArchUnit-enforced | Yes | `MfaController.java` imports only `MfaService` + framework/DTO types; `MfaExceptionHandler.java` imports only `common.ProblemTypes` + framework types | Yes — `ArchitectureTest.shouldPreventCrossModuleEntityImports` + `controllersDependOnlyOnTheirOwnModuleServices`, both re-verified passing with `MfaController` in the graph (Phase 7, standalone runner, Surefire's own `@ArchTest`-field bug bypassed) | No | None |

## Acceptance Criteria — real cross-check (`01-specification-extraction.md`'s actual AC1–AC10)

| AC | Text | Status | Evidence |
|---|---|---|---|
| AC1 (R22) | `POST /totp` authenticated, calls `beginEnroll`, returns provisioning URI, status pinned | ✅ | `201`, no `Location`; `MfaControllerTest.beginEnrollReturns201WithProvisioningUriOnly` + integration |
| AC2 (R22) | Existing confirmed enrollment → real HTTP response, not 500 | ✅ | `409`, `MFA_ALREADY_ENROLLED`; `MfaExceptionHandlerTest.onAlreadyEnrolledReturnsUniform409` + integration |
| AC3 (R23) | `POST /totp/confirm` calls `confirm`, returns 10 codes | ✅ | `MfaControllerTest.confirmReturnsRecoveryCodesResponse` + named integration test |
| AC4 (R23, R29) | Wrong code → real response, no codes returned | ✅ | `401`, `MFA_INVALID_CODE`; integration `confirmRejectsWrongCode` |
| AC5 (R28) | `DELETE /totp` calls `disable` with password+code, returns 204 | ✅ | `MfaControllerTest.disableReturns204` + named integration test |
| AC6 (R28, R29) | Wrong password/code on disable → real response, nothing removed | ✅ | `400` (password, post-correction)/`401` (code); integration `disableRejectsWrongPassword` |
| AC7 (R49) | `POST /recovery-codes` requires password+TOTP, invalidates existing, returns 10 new once, audits `mfa.recovery_codes_regenerated` | ✅ | Named integration test + `MfaServicePersistenceIntegrationTest`'s real-invalidation proof |
| AC8 (R49) | Wrong password/code on regenerate → mutate nothing, audit failure, reuse existing exception types | ✅ | `MfaServiceTest`'s two failure-path tests + integration `regenerateRejectsWhenNotEnrolled`/`regenerateRejectsWrongCode` — confirmed `MfaCurrentPasswordMismatchException`/`InvalidTotpCodeException`/`MfaNotEnrolledException` reused, no new exception types invented |
| AC9 (L11) | None of the four endpoints in `PublicEndpoints.java` | ✅ | Direct assertion + ArchUnit, both re-verified |
| AC10 (R47) | `auth.yaml` documents all four endpoints, **and the generated TS client compiles against them** | ⚠️ **Partially satisfied, disclosed not glossed over** | `auth.yaml` half: ✅ (4 paths, 4 schemas, `security:`, `AuthOpenApiContractTest` 11/11). **TS-client half: not checkable today** — `libs/ts/api-client/` is an empty placeholder directory (only a `.gitkeep`), with no generator, no generation script, and no CI step referencing it anywhere in the repo. This predates T19 entirely (confirmed: the directory has held only `.gitkeep` since its creation, unrelated to any work this task touched) — none of the other 30 pre-existing endpoints have ever had this half of their own equivalent AC verified either. Stated plainly rather than rounded up to "satisfied": this half of AC10 is blocked on tooling that does not exist yet anywhere in this repository, a pre-existing gap for a future, dedicated task (frontend/tooling), not something T19 could have closed. |

## Named Tests (`package.md` §8) — final confirmation

| Named test | Maps to | Status |
|---|---|---|
| `shouldReturnTotpProvisioningUriOnEnrollmentBegin` → R22 | `MfaControllerIntegrationTest.shouldReturnTotpProvisioningUriOnEnrollmentBegin` | ✅ passing |
| `shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` → R23 | `MfaControllerIntegrationTest.shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` | ✅ passing |
| `shouldRequirePasswordAndTotpToDisableMfa` → R28 | `MfaControllerIntegrationTest.shouldRequirePasswordAndTotpToDisableMfa` | ✅ passing |
| `shouldRegenerateRecoveryCodesWithPasswordAndTotp` → R49 (new) | `MfaControllerIntegrationTest.shouldRegenerateRecoveryCodesWithPasswordAndTotp` | ✅ passing |

All four are proven at the real-HTTP, real-Postgres-and-Kafka integration level — not just unit
mocks — matching Phase 5's own plan for this task.

## Answers

**(1) Is the task fully complete?**
Yes, for the scope T19 actually owns: all five requirement IDs (R22/R23/R28/R29/R49) and all three
LOCKED decisions (L6/L11/L12) this task touches are implemented, self-consistent, and covered by
passing tests — unlike T18, every test here (unit, controller, handler, HTTP integration,
persistence) actually executes against real Docker in this environment; nothing is "written but
blocked."

**(2) Does it satisfy every acceptance criterion?**
9 of 10 fully (AC1–AC9). AC10 is satisfied for its `auth.yaml`-documentation half; its
generated-TS-client half cannot be checked because no such generator or client exists anywhere in
this repository yet — a pre-existing, repo-wide tooling gap, not a defect this task introduced or
could have closed within its own scope.

**(3) Does it violate any LOCKED decision?**
No. L6, L11, L12 are each implemented exactly and mechanically verified (reused crypto primitives,
direct `PublicEndpoints` assertion + ArchUnit, module-boundary ArchUnit rules re-confirmed with
`MfaController` in the graph).

**(4) Remaining risks?**
- **AC10's TS-client half** (above) — tracked, not a T19-introduced gap.
- **The disclosed, twice-rejected `auth.yaml` error-response-documentation gap** — a deliberate,
  three-times-confirmed scope boundary (Phase 4, Phase 9, Phase 11 dispositions), not an open
  defect; would require a repo-wide convention decision outside any single task's authority.
- **The disclosed, low-severity concurrent-regenerate race** (Phase 7/9) — safe failure mode
  (opaque 500, no data corruption), accepted as a known limitation, not fixed further.
- **The disclosed, wider `package.md` §8 test-mapping drift** beyond this task's own 4 lines
  (Phase 6) — flagged in-file for a future dedicated audit task.
- **Unrelated, pre-existing Kafka-outbox/audit-mirror test flakiness** (Phase 6) — reconfirmed not
  touching any MFA/contract code, not this task's to fix.

None of these are defects in what T19 itself built; all are explicitly disclosed, not silently
glossed over, matching this pipeline's own established discipline throughout every phase of this
task.

## Verification Run (live, this phase)

```
mvn -pl services/auth test -Dtest=MfaServiceTest,MfaControllerTest,MfaExceptionHandlerTest,\
MfaControllerIntegrationTest,MfaServicePersistenceIntegrationTest,AuthOpenApiContractTest
```

| Class | Tests | Result |
|---|---|---|
| `MfaServiceTest` | 36 | ✅ |
| `MfaControllerTest` | 13 | ✅ |
| `MfaExceptionHandlerTest` | 6 | ✅ |
| `MfaControllerIntegrationTest` | 14 | ✅ |
| `MfaServicePersistenceIntegrationTest` | 10 | ✅ |
| `AuthOpenApiContractTest` | **11** (10 + 1 new this phase) | ✅ |
| **Total** | **90** | **✅ all pass** |

All 11 ArchUnit rules independently re-confirmed passing (standalone runner, Surefire's known
0-tests bug bypassed, same technique as Phases 7/9).

## Files changed this phase

- `services/auth/src/test/java/com/themistra/auth/common/AuthOpenApiContractTest.java` — added
  `mfaOperationsDeclareExplicitBearerAuthSecurity` (Gap 2's scoped fix), negative-proofed.

## Open Questions

None. Task is ready for Phase 13 (PR Preparation).
