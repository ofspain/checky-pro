<!-- MODEL: Claude Sonnet — Phase 12 (Specification Verification). -->

# auth · T28 · Phase 12 — Specification Verification

Compares the final implementation and tests (Phases 6–11) against `requirements.md`, `design.md`, `tasks.md`, and the frozen brief for **T28 only**. `spec/auth-service/` confirmed byte-for-byte unchanged since T28 began (`git diff 0261819...HEAD -- spec/auth-service/` — empty, `0261819` being T27's final commit).

---

## Traceability Matrix — Requirements

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R36** — list active sessions with device label, created, last-rotated | Yes | `SessionService.java:58-62` (`list`); `AccountController.java:143-147` (`listSessions`) | `SessionServiceTest` (4 tests, executed, green); `SessionIntegrationTest.shouldListActiveSessions` (named, written, unexecuted — Docker) | No | No |
| **R37** — revoke one family; remove its live SAS authorization | Yes | `SessionService.java:73-77,102-107,112-117` (`revokeOne`, `revokeFamily`, `removeSasAuthorizationIfPresent`); `AccountController.java:155-160` | `SessionServiceTest` (6 tests, executed, green); `SessionIntegrationTest.shouldRevokeSingleSessionFamily` (named, written, unexecuted) — includes a **real** `OAuth2Authorization` removal proof, not just an inference | No | No |
| **R38** — revoke all families; remove all their authorizations | Yes | `SessionService.java:90-100` (`revokeAll`); `AccountController.java:167-171` | `SessionServiceTest` (4 tests including both new failure-mode tests, executed, green); `SessionIntegrationTest.shouldRevokeAllSessionFamilies` (named, written, unexecuted) | No | No |
| **R43** *(referenced)* — every revoke audited | Yes | `SessionService.java:106,119-132` (`recordAudit`, called once per family) | `SessionServiceTest.revokeOneRevokesFamilyRemovesAuthorizationAndAudits`/`revokeAllAuditsEachFamilyIndependently` (executed, green); `SessionIntegrationTest`'s two named tests now assert exact `auth_audit` row counts (written, unexecuted) | No | No |
| **R46** *(referenced)* — 4xx is `application/problem+json`, no internal detail | Yes | `SessionExceptionHandler.java` (`onNotFound`, no `detail`) | `SessionExceptionHandlerTest` (2, executed, green); `SessionIntegrationTest.revokeOfUnownedAndNonexistentFamilyAreByteIdentical` (written, unexecuted, now a true byte-for-byte comparison) | No | No |

## Traceability Matrix — Locked Decisions

**None constrain this task** (confirmed at Phase 0/1/2 and unchanged through implementation) — the only task in this recent sequence (T25–T28) with no LOCKED decision at all. All design latitude was resolved through this task's own Phase 3/4 gate (D1–D6, below).

## This Task's Own Design Decisions — honored?

| Decision | Honored? | Evidence |
|---|---|---|
| **D1** — no `revokedAt` filter on the single-revoke lookup (idempotent 204) | Yes | `RefreshTokenFamilyRepository.java:21`; `SessionServiceTest.revokeOneOnAlreadyRevokedFamilyDoesNotThrow` |
| **D2** — null `OAuth2AuthorizationService.findById` result is a no-op | Yes | `SessionService.java:112-117`; `SessionServiceTest.revokeOneTreatsNullAuthorizationAsNoOp`; `SessionIntegrationTest.revokeWhenAuthorizationAlreadyGoneSucceeds` |
| **D3** — bulk revoke is best-effort per family | Yes | `SessionService.java:90-100` (deliberately not `@Transactional`); `SessionServiceTest` now covers all three named failure modes (authorization-lookup, save, audit) per Phase 11's Gap 1 |
| **D4** — active-session definition stays simple (`revokedAt IS NULL`, no cross-table join) | Yes | `SessionService.java:59` (unchanged existing query) |
| **D5** — non-UUID principal is a named, accepted limitation | Yes, undisturbed | No guard added anywhere in `AccountController`'s new methods |
| **D6** — `deviceLabel` is `null` today, documented not fixed | Yes | `SessionResponse.java`'s own Javadoc; `SessionService`/`ReuseDetectingAuthorizationService.trackRefreshTokenIfPresent` (unmodified) |
| *(Implementation-time, Phase 5/6)* Authorization removed **before** marking the family revoked | Yes | `SessionService.java:102-107` (`revokeFamily`'s exact order); `SessionServiceTest.revokeOneRemovesAuthorizationBeforeMarkingFamilyRevoked`/`revokeOneDoesNotMarkFamilyRevokedWhenAuthorizationRemovalFails` (both executed, green) |

## Acceptance Criteria

| AC | Status | Evidence |
|---|---|---|
| AC1 | **Met** | `listSessions`/`revokeSession`/`revokeAllSessions` are not registered in `PublicEndpoints`; sit behind the `@Order(2)` chain's default `authenticated()` rule |
| AC2 | **Met** | All three derive `accountUuid` via `UUID.fromString(authentication.getName())` only; `SessionResponse` carries all four required fields, now exhaustively asserted (Phase 11 Gap 2) |
| AC3 | **Met** | `findByFamilyIdAndPrincipalName` (no filter, D1) + `RefreshTokenFamily.revoke`'s idempotency → 204 on an already-revoked-but-owned family; `SessionNotFoundException` for anything else, byte-identical 404 (Phase 11 Gap 7) |
| AC4 | **Met** | `removeSasAuthorizationIfPresent` runs before marking revoked; proven against a **real** `OAuth2Authorization` in `SessionIntegrationTest`, and directly against the family row via `entityManager` reload (Phase 11 Gap 5) |
| AC5 | **Met** | `revokeAll` independent per-family, all three failure modes now covered (Phase 11 Gap 1) |
| AC6 | **Met** | One `session.revoked` audit row per family, exact row counts asserted (Phase 11 Gap 6) |
| AC7 | **Met** | `SessionExceptionHandler.onNotFound` sets no `detail` |
| AC8 | **Expected, not independently re-run this session** | No new class imports `PublicEndpoints` or a foreign entity; `SessionService`/`SessionResponse` reference only `RefreshTokenFamily` (same module); `ArchitectureTest` needs Docker in this sandbox's Surefire wiring to report non-zero test counts (same pre-existing quirk since T16 Phase 12) |

---

## Principal-Engineer Assessment

**(1) Is the task fully complete?** Yes, within T28's scope. Every file the frozen brief authorized was created or modified exactly as planned; every Phase 3/8/11 review finding was triaged and either fixed or explicitly, on-the-record deferred (Kimi Phase 8's Finding 4, a hallucinated claim, was rejected outright after verification; Phase 11's Gap 3 was rejected for consistency with established codebase convention; Phase 11's Gap 4 was a deliberate, logged scope decision, not an oversight). As with every task since T25, the one category of incompleteness is environmental: Docker remains unavailable this entire multi-day session, so `SessionIntegrationTest` (8 tests, including all three named tests) is written and compiles cleanly but has never executed against a real server. A genuine, pre-existing bug in `ReuseDetectingAuthorizationServiceTest` (predating T28 entirely) was discovered and fixed as a side effect of this task's review process.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC8 all have direct code evidence, and the test coverage backing them was strengthened twice over the course of review (Phase 9's already-applied fix, Phase 11's six accepted gaps).

**(3) Does it violate any LOCKED decision?** No — there were none to violate, the first task in this sequence without any. The task's own six design decisions (D1–D6) plus the implementation-time ordering discovery are all honored exactly as decided, with no silent deviation anywhere.

**(4) Remaining risks:**
- **Unexecuted integration suite (highest-priority residual, now spanning four consecutive tasks — T25, T26, T27, T28).** `SessionIntegrationTest`'s 8 tests, including all three named tests and the real-`OAuth2Authorization`-removal proof, have never run against a real server this session. Whoever picks this up should run the full `apikey`/`token`-related integration suite together, in dependency order (T25's `SasLoginIntegrationTest` and `ApiKeyExchangeIntegrationTest` first, since T26/T27/T28 all build on that infrastructure working correctly).
- **The `revokeAll` per-family non-atomicity (self-review Finding 1, Phase 7)** remains an accepted, documented residual — the safe-failure-direction tradeoff of the deliberately-simple best-effort design (D3), not a defect.
- **`deviceLabel` will be `null` for every real session** until `design.md`'s O3 is resolved by the spec author and issuance-time code (`ReuseDetectingAuthorizationService.trackRefreshTokenIfPresent`, out of this task's scope) is updated to populate it. Documented (D6), not a T28 defect.
- **Contract files still don't exist** (`contracts/api/auth.yaml`/`token-claims.md`) — same gap noted at every task since T25; not blocking for a behavioral task.

---

## Verdict

**PASS** — every requirement, design decision, and acceptance criterion has direct code evidence and either an executed passing test or a written-but-Docker-blocked test with a clear, honest account of why it hasn't run; every review finding across three rounds (Phase 3, 8, 11) was genuinely triaged — accepted, fixed, or rejected with stated reasoning — rather than rubber-stamped, including correctly identifying and rejecting one fabricated claim.

---

**Phase 12 complete — PASS.** Proceed to Phase 13 (PR / Commit Preparation).
