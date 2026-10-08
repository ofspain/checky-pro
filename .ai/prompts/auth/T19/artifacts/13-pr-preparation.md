# auth · T19 — Phase 13: PR / Commit Preparation

Consumes `artifacts/12-specification-verification.md` — all requirements/LOCKED decisions
satisfied, 90/90 tests green, 11/11 ArchUnit rules passing. **PASS.** T19 is DONE.

No `gh` CLI available in this environment (no GitHub remote PR to open) — this task's own 13 real
commits already landed directly on `spec/service-specs-and-ai-framework`, one per phase, each
pushed to `origin` as it completed. This artifact is the squash-equivalent summary a PR description
would carry, for whoever next reviews this branch's full diff.

---

## Commit Title

`auth T19: MFA self-service HTTP surface + recovery-code regeneration`

## Commit Message (squash-equivalent)

```
auth T19: MFA self-service HTTP surface + recovery-code regeneration

T16-T18 built the complete MFA business logic (MfaService: beginEnroll,
confirm, disable) with no way to reach it over HTTP. This task adds the
thin translation layer plus one genuinely new piece of business logic:

- MfaController (/accounts/me/mfa): POST /totp (begin), POST
  /totp/confirm, DELETE /totp (disable), POST /recovery-codes
  (regenerate, new).
- MfaService.regenerateRecoveryCodes (R49, new requirement - T19's own
  task statement named a fourth endpoint no existing requirement
  covered; added R49 at Phase 0 after confirming the gap): requires
  current password + valid TOTP code, invalidates every existing
  recovery code, generates and returns 10 new ones exactly once, audits
  mfa.recovery_codes_regenerated (or a failure event, mutating nothing,
  on a wrong credential).
- MfaExceptionHandler: MfaAlreadyEnrolledException->409,
  MfaNotEnrolledException->404, InvalidTotpCodeException->401,
  MfaCurrentPasswordMismatchException->400 (reuses the EXISTING
  ProblemTypes.CURRENT_PASSWORD_MISMATCH AccountExceptionHandler
  already established at 400 for the identical semantic - a stable,
  published problem type can't mean 400 in one place and 403 in
  another; this deviates from this task's own original Phase 2/4 plan,
  which assumed 403 by analogy with a different exception, caught
  before any code was written).
- contracts/api/auth.yaml: four new paths, four new schemas, explicit
  security: [bearerAuth], success-response documentation only (matches
  this file's own 100%-consistent existing convention - 0 of the other
  30 endpoints document error responses either).
- spec/auth-service/package.md Sec8: fixed three pre-existing mismapped
  named-test lines, added the new R49 line, disclosed (not fixed - out
  of scope) a wider version of the same mismapping pattern found
  elsewhere in the same table.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

## Files Changed (full diff across all 13 phase commits)

**Created:**
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaController.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaExceptionHandler.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/dto/BeginEnrollResponse.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/dto/TotpCodeRequest.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/dto/PasswordAndTotpRequest.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/dto/RecoveryCodesResponse.java`
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaControllerTest.java` (13 tests)
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaExceptionHandlerTest.java` (6 tests)
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaControllerIntegrationTest.java` (14 tests)

**Modified:**
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaService.java` — added
  `regenerateRecoveryCodes` + `RegenerateRecoveryCodesResult`.
- `services/auth/src/main/java/com/themistra/auth/common/ProblemTypes.java` — added
  `MFA_ALREADY_ENROLLED`, `MFA_NOT_ENROLLED`, `MFA_INVALID_CODE` (3, not 4 — reuses
  `CURRENT_PASSWORD_MISMATCH` for the fourth case).
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaServiceTest.java` — +7 tests
  (`regenerateRecoveryCodes`).
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaServicePersistenceIntegrationTest.java` —
  +2 tests (old-code invalidation proof; concurrent-regenerate characterization), 1 stale-Javadoc
  correction (unrelated staleness this task's own work happened to touch).
- `services/auth/src/test/java/com/themistra/auth/common/AuthOpenApiContractTest.java` — added
  `MfaController` + 4 routes to its hand-maintained tables (a dependency this task's own AC10
  implied but no phase's file list named until Phase 6 found it); +1 test
  (`mfaOperationsDeclareExplicitBearerAuthSecurity`, Phase 12, Kimi Phase 11 Gap 2).
- `contracts/api/auth.yaml` — 4 new paths, 4 new schemas, `security: [bearerAuth]` on each.
- `spec/auth-service/package.md` §8 — 3 mismapped lines fixed, 1 new R49 line, 1 disclosure note
  for a wider, out-of-scope version of the same drift.
- `.ai/prompts/auth/T19/artifacts/04-frozen-task-brief.md` — Phase 9 addendum (original frozen
  text untouched).

**16 files changed, 1432 insertions(+), 15 deletions(-)** (production + test + contract + spec,
excluding this pipeline's own 13 `.ai/prompts/auth/T19/artifacts/*.md` phase artifacts).

## Summary

Implements task 19 in full: the HTTP surface for T16–T18's already-built MFA service, plus
recovery-code regeneration (R49) — a fourth endpoint the task's own statement named but no prior
requirement, service method, or test ever covered, discovered and closed at Phase 0 before any
design work began. Went through the complete 14-phase pipeline, including two independent Kimi
reviews (Phase 3 design challenge, Phase 8 code review) and one Kimi test review (Phase 11), each
with every finding re-verified against actual source before being accepted or rejected — never on
citation alone. Three real corrections were self-caught during implementation/self-review before
Kimi ever saw them (the 400-not-403 status code, a `AuthOpenApiContractTest` dependency no phase's
plan named, a stale test-class Javadoc); one real empirical finding reversed its own initial,
wrong hypothesis (the concurrent-regenerate race was assumed to fail silently — it actually fails
safely, via `ObjectOptimisticLockingFailureException`, proven by running the test, not by
inspection alone).

## Testing Performed

- **Unit** (plain JUnit + Mockito, no Spring context): `MfaServiceTest` (+7, 36 total),
  `MfaControllerTest` (13, new), `MfaExceptionHandlerTest` (6, new).
- **HTTP integration** (`@SpringBootTest(RANDOM_PORT)` + Testcontainers Postgres+Kafka,
  real signed JWT, real security filter chain): `MfaControllerIntegrationTest` (14, new) — all
  four named tests from `package.md` §8, every failure path's real RFC 9457 body, DTO-validation
  400s, and a direct unauthenticated-caller 401 check for all four endpoints.
- **Persistence integration** (real Postgres): `MfaServicePersistenceIntegrationTest` (+2, 10
  total) — a real proof that old recovery codes are genuinely rejected after regenerate (not just
  absent from the response), and a genuine concurrent-transactions test characterizing (not
  assuming) the actual behavior of two simultaneous regenerate calls.
- **Contract**: `AuthOpenApiContractTest` (+1, 11 total) — completeness, schema-shape, and (new)
  `security:`-presence checks for all four MFA routes.
- **ArchUnit**: all 11 rules in `ArchitectureTest` independently re-verified passing (standalone
  runner, bypassing Surefire's own known `@ArchTest`-field non-execution bug — not assumed safe
  from a green `mvn test` that silently runs 0 of these tests).
- **Full suite regression check**: `mvn -pl services/auth test` run multiple times across this
  task's lifetime (748 tests). Every MFA/contract-related class green every time. A shifting
  subset of unrelated Kafka-outbox/audit-mirror integration tests flaked across different runs —
  confirmed pre-existing (none touch MFA/`ProblemTypes`/`auth.yaml`/`package.md`), not a
  regression introduced by this task.
- **Negative-proofed** (not just written-and-assumed-correct): the new `security:`-presence
  contract test (temporarily removed one route's `security:` block, confirmed real failure,
  reverted, `git diff` empty).

## Specification References

- **Task:** `spec/auth-service/tasks.md`, task 19.
- **Requirements:** R22, R23, R28, R29 (existing, now reachable over HTTP), R49 (new, added this
  task).
- **LOCKED decisions:** L6 (TOTP/recovery-code format, reused unchanged), L11 (public-endpoint
  discipline — none of the four endpoints added), L12 (module boundaries — `MfaController`
  depends only on `MfaService`).

## Open Items Carried Forward (not blocking this task)

Recorded in `artifacts/09-review-resolution.md` and `artifacts/12-specification-verification.md`:

1. **`auth.yaml`'s repo-wide missing error-response documentation** — a deliberate, now
   three-times-confirmed scope boundary (Phase 4, Phase 9, Phase 11), not a T19 defect; would need
   a dedicated, repo-wide documentation-convention decision.
2. **The wider `package.md` §8 test-mapping drift** beyond this task's own 4 corrected lines —
   disclosed in-file, needs a dedicated audit task.
3. **AC10's generated-TS-client half** — `libs/ts/api-client/` is an empty, repo-wide placeholder
   predating T19 entirely; no generator exists yet for any of this service's 34 endpoints, not
   just this task's 4.
4. **The disclosed, low-severity concurrent-regenerate race** — safe failure mode (opaque 500, no
   data corruption), accepted as a known limitation; a future retry/backoff enhancement is
   possible but out of this task's scope.
5. **Unrelated, pre-existing Kafka-outbox/audit-mirror test flakiness** — not investigated
   further, matches already-logged Group A/B flakiness from T31/T36/T37.

None of these are regressions or scope gaps in T19 itself — each was surfaced during review,
independently verified, and knowingly disclosed rather than silently fixed or silently ignored.

---

**T19 is DONE.** This closes the only backend task the frontend spec investigation (earlier this
session) identified as the most concrete, smallest unblockable real gap. `spec/auth-service`'s own
task sequence (T01–T40) is now, for the second time this session, genuinely complete — this time
with no undisclosed exceptions.
