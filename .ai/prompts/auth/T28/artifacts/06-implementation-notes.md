<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). -->

# auth · T28 · Phase 6 — Implementation Notes

Implements `artifacts/05-implementation-plan.md` against the frozen brief (`artifacts/04-frozen-task-brief.md`). No tests written in this phase (Phase 10). No file outside the plan's authorized set was touched, with one forced exception noted under Deviations.

---

## Files Created

### `token/dto/SessionResponse.java`
`record SessionResponse(UUID familyId, String deviceLabel, Instant createdAt, Instant rotatedAt)` with a `from(RefreshTokenFamily)` static factory — mirrors `account/dto/AccountResponse`'s established `from(Entity)`-in-a-`dto`-subpackage precedent exactly.

### `token/SessionNotFoundException.java`
No state, mirrors `ApiKeyNotFoundException` exactly.

### `token/SessionExceptionHandler.java`
Single mapping, `SessionNotFoundException` → 404 `ProblemTypes.SESSION_NOT_FOUND`, no detail. Kept in `token` per D-C, not folded into `account.AccountExceptionHandler`.

### `token/SessionService.java`
`list`, `revokeOne`, `revokeAll` exactly per the plan. Implements the load-bearing ordering the plan called out: `revokeFamily` removes the SAS authorization **first** (null-safely, D2), then marks the family revoked, then audits — never the reverse. `revokeAll` is deliberately **not** `@Transactional` (per-family independence, D3); `revokeOne` is a single `@Transactional` method (a failure anywhere fails the whole call, matching `ApiKeyService.revoke`'s established shape for a single-item operation).

---

## Files Modified

- **`common/ProblemTypes.java`** — added `SESSION_NOT_FOUND`.
- **`token/RefreshTokenFamilyRepository.java`** — added `findByFamilyIdAndPrincipalName` (D1, no `revokedAt` filter).
- **`account/AccountController.java`** — added `listSessions`, `revokeSession`, `revokeAllSessions`; constructor now also takes `SessionService`.

---

## Deviations Forced by Reality

**`AccountController`'s constructor signature change rippled into `AccountControllerTest.java`.** Adding the required `SessionService` constructor parameter broke all 14 existing `new AccountController(accountService)` call sites in the pre-existing test file (compile error, confirmed via a clean `mvn clean test-compile` run before this fix — an earlier incremental-only compile had stale class files that masked the break). Fixed by adding a `@Mock private SessionService sessionService` field and updating all 14 call sites to `new AccountController(accountService, sessionService)`. This is **not new test content** — no new test method, assertion, or coverage was added to that file; it is the minimum change required to keep an existing, unrelated test suite compiling against a production constructor this task legitimately needed to change. Flagged here rather than silently done, per the guardrail against unrequested changes — but keeping the build green is not optional, and this was the only way to do it without reverting the constructor change itself.

No other deviation. Implementation otherwise matches the plan and frozen brief exactly.

---

## Acceptance Criteria — mapping

| AC | Status | Evidence |
|---|---|---|
| AC1 | Done | `SessionService.list` filters to `findByPrincipalNameAndRevokedAtIsNull` (D4, active-only); `AccountController.listSessions` derives the caller from `Authentication` only |
| AC2 | Done | `SessionResponse.from` maps all four fields; `deviceLabel` is `null` today by design (D6) |
| AC3 | Done | `findByFamilyIdAndPrincipalName` (no `revokedAt` filter, D1) + `family.revoke`'s pre-existing idempotency → 204 on an already-revoked-but-owned family; `SessionNotFoundException` for anything else |
| AC4 | Done | `revokeFamily`'s `removeSasAuthorizationIfPresent` runs before marking the family revoked; a `null` `findById` result is a no-op (D2) |
| AC5 | Done | `revokeAll` iterates every active family independently, catches per-family exceptions, continues (D3) |
| AC6 | Done | `recordAudit` called once per family revoked, both call paths |
| AC7 | Done | `SessionExceptionHandler.onNotFound` sets no `detail` |
| AC8 | Expected, not independently re-run this session | No new class imports `PublicEndpoints` or a foreign entity (`SessionResponse`/`SessionService` reference only `RefreshTokenFamily`, same module); `ArchitectureTest` needs Docker in this sandbox's Surefire wiring to report non-zero test counts (same pre-existing quirk noted since T16 Phase 12) |

---

## Build Verification

`mvn -q -pl services/auth -am clean compile test-compile` — clean, exit 0 (a clean build was specifically required to surface and then confirm the fix for the `AccountControllerTest` deviation above; incremental compilation alone had masked it).

Docker-independent regression, run together: **70/70 pass** — `AccountControllerTest` (14, including the 14 updated call sites), `ApiKeyControllerTest` (26), `ApiKeyExceptionHandlerTest` (5), `ApiKeyTokenIssuerTest` (7), `ApiKeyPropertiesTest` (6), `JwksConfigTest` (1), `TokenClaimsCustomizerTest` (8), `ApiKeyHasherTest` (3).

**Not verified this session (needs Docker):** the full request/response cycle for `listSessions`/`revokeSession`/`revokeAllSessions` through the real filter chain and real Postgres, `ArchitectureTest`, and whether the load-bearing removal-before-marking-revoked ordering actually behaves as reasoned against a real `JdbcOAuth2AuthorizationService`/`ReuseDetectingAuthorizationService`. Phase 10 will write the integration test; it will compile but not execute until Docker is available.

---

**Phase 6 complete — implementation written.** Proceed to Phase 7 (Self Review) on approval.
