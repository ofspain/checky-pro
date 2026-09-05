# auth · T27 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Approved by femi at the Phase 4 human gate, 2026-08-15. Consumes `artifacts/02-task-implementation-brief.md` (TIB) and `artifacts/03-design-challenge.md` (Kimi 2.7, 5 findings). Downstream phases may not renegotiate this brief.

---

## Task

T27 — **API-key integration tests.** Test create→exchange→revoke→exchange-fails flow with Testcontainers.
(Verbatim, `spec/auth-service/tasks.md` task 27.)

## Purpose

Prove, through the real HTTP endpoints and a real running server, that a single API key's full lifecycle behaves correctly end to end — a key that authenticates successfully is immediately and completely unusable after revocation, with every state transition observable at the HTTP boundary, not merely inferred.

---

## Phase 4 decisions (gate outcomes)

### D1 — `last_used_at`/`revoked_at` verification requires `GET /api-keys` in the sequence (resolves Kimi #1, #3)

**Decided:** the flow gains two `GET /api-keys` steps: one immediately after create (assert `last_used_at` is null), one after the pre-revocation exchange (assert `last_used_at` is non-null) and again after revoke (assert `revoked_at` is non-null, in the same or an additional `GET` call). This is the only HTTP-observable way to prove AC5 — the exchange endpoint's 200 body is only the JWT, and `DELETE` returns 204 with no body.

### D2 — Uniform-401 proof by comparison (resolves Kimi #2)

**Decided:** the post-revocation 401 is compared byte-for-byte, within the same test, against the body of a second, independent rejection cause (a deliberately malformed key presented on the same endpoint). This proves uniformity directly rather than inferring it from the handler's structure alone.

### D3 — Document the T25 dependency (resolves Kimi #4)

**Decided:** the frozen brief's Dependencies section states explicitly that T27's ability to authenticate its own `POST`/`DELETE` calls depends on T25's already-implemented `ApiKeyTokenIssuer`/`JwtEncoder` path — if T27 fails on first execution, that infrastructure is a plausible root cause, not necessarily T27's own test logic.

### D4 — API-key JWT authorized to manage further API keys (resolves Kimi #5)

**Decided:** confirmed as intended platform behavior, not scope creep. The exchanged JWT carries the account's real, freshly-resolved `roles` claim (not a scope-restricted capability), so a `MERCHANT` account's own API-key-exchanged JWT genuinely and correctly passes `ApiKeyService.create`'s own MERCHANT+MFA re-check — this is consistent design, not a loophole. No restriction added. Matches T26's own established test-authentication technique (minting via `ApiKeyTokenIssuer` directly rather than a full interactive-login flow).

---

## Phase 3 findings — disposition

| # | Finding | Disposition |
|---|---|---|
| 1 | `last_used_at` unverifiable without `GET /api-keys` | **Accepted → D1.** |
| 2 | Uniform-401 asserted in isolation | **Accepted → D2.** |
| 3 | `revoked_at` never asserted | **Accepted → D1** (folded in). |
| 4 | Hidden T25 dependency undocumented | **Accepted → D3.** |
| 5 | API-key JWT authorizing further API-key management | **Accepted, confirmed intended → D4.** |

No findings rejected.

---

## Scope (final)

**In:** one new Testcontainers-backed integration test, exercising against a single key, in order:
1. `POST /api-keys` (create).
2. `GET /api-keys` — assert `last_used_at` is null.
3. `POST /api-keys/token` (exchange) — 200, decode JWT, assert `sub`/`scope`/`amr`.
4. `GET /api-keys` — assert `last_used_at` is now non-null.
5. `DELETE /api-keys/{keyUuid}` (revoke) — 204.
6. `GET /api-keys` — assert `revoked_at` is now non-null.
7. `POST /api-keys/token` again, same credential — 401, uniform body.
8. A second, independent rejection (malformed key) on the same test's server — 401, body compared byte-for-byte against step 7's body.

**Out:** any production code change (T24/T25/T26-frozen). Re-testing each operation's own isolated edge cases (covered elsewhere). Any Flyway migration, config key, or contract file. Restricting API-key JWTs from managing further API keys (D4 — confirmed as intended, not a gap to close).

## Business Rules

- **R30** — create via `POST /api-keys`.
- **R31** — exchange via `POST /api-keys/token`.
- **R32** *(referenced, via D1)* — `last_used_at` updates on successful exchange.
- **R33** — exchange of a revoked key → uniform 401.
- **R34** *(referenced, via D1)* — `GET /api-keys` used as the observation point.
- **R35** — revoke via `DELETE /api-keys/{keyUuid}`.

## Locked Decisions

- **L7** — key format / SHA-256-only / plaintext-once. Exercised, not re-verified in isolation.
- **L8** — JWT claim contract. `sub`/`scope`/`amr` asserted on the pre-revocation exchange (not exhaustive L9 coverage — already exhaustive elsewhere).

## Dependencies

`TestcontainersConfiguration`, `TestRestTemplate` + `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `ApiKeyTokenIssuer` (mints the bearer JWT authenticating the CRUD calls — **D3: T27's own execution depends on this T25 component working correctly**), `AccountService`/`RoleService`/`MfaService` (merchant+MFA seeding, independently reimplemented per this module's convention). No new repository method, config key, or migration.

## Inputs

A single merchant account, seeded with `MERCHANT` role and confirmed TOTP MFA.

## Outputs

No new production output. All assertions are against existing, unmodified endpoint behavior.

## State Changes

None beyond what the existing endpoints already do.

## Files to Create

- `apikey/ApiKeyLifecycleIntegrationTest.java`

## Files to Modify

None.

## Files NOT to Modify

`apikey/ApiKeyService.java`, `ApiKeyController.java`, `ApiKeyTokenIssuer.java`, `ApiKeyExceptionHandler.java`, `ApiKeyRepository.java`, `ApiKey.java`, `ApiKeyHasher.java`, `ApiKeyProperties.java`, all existing exception classes; `common/**`; `token/**`; every existing test file in `apikey/`; all Flyway migrations; `spec/**`.

## Acceptance Criteria

- **AC1** — Create → exchange succeeds (200, decodable JWT with expected `sub`/`scope`/`amr`) before any revocation. *(R30, R31, L8)*
- **AC2** — The identical key, after revocation, fails the identical exchange call with a uniform 401. *(R33, R35)*
- **AC3** — The post-revocation 401 body is byte-for-byte identical to a second, independently-triggered rejection cause within the same test (D2). *(R33)*
- **AC4** — The entire sequence runs through real HTTP calls against a real running server and real Postgres — never a direct `ApiKeyService` method call standing in for an HTTP step. *(Task statement)*
- **AC5** — `GET /api-keys` confirms `last_used_at` is null after create, non-null after the successful exchange, and `revoked_at` is non-null after revoke (D1). *(R32, R34, referenced)*

## Required Tests

**One new test method** (resolves Phase 1's OQ1): `shouldSupportFullCreateExchangeRevokeExchangeFailsLifecycle` — composing the eight-step sequence in Scope. None of this task's four header-listed named tests are written as new methods here; each already exists, correctly, at another layer (Phase 1 finding, unchanged).

**Regression:** `ArchitectureTest` and the three existing `apikey` integration test files — unaffected, no production code changes.

## Constraints

- **Security** — never log or print the plaintext key or its hash beyond ordinary AssertJ failure-message conventions already used throughout this module.
- **Thread-safety / Transaction / Module boundaries / Null handling** — N/A (sequential test, no new production class).

## Open Questions

**No blockers.** All five Phase 3 findings resolved at this gate (D1–D4; finding #3 folded into D1).

---

**Phase 4 complete — brief FROZEN.** Proceed to Phase 5 (Implementation Plan).
