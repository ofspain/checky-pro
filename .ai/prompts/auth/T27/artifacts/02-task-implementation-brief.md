<!-- MODEL: Claude Sonnet — Phase 2 (Task Implementation Brief). -->

# auth · T27 · Phase 2 — Task Implementation Brief (TIB)

## Task

**API-key integration tests.** Test create→exchange→revoke→exchange-fails flow with Testcontainers. *(Verbatim, `spec/auth-service/tasks.md` task 27.)*

## Purpose

Prove, through the real HTTP endpoints and a real running server, that a single API key's full lifecycle behaves correctly end to end — specifically, that a key which successfully authenticates a moment ago is immediately and completely unusable after revocation. No existing test proves this continuity; each of T24/T25/T26's own tests proves one operation in isolation.

## Scope

**In:** one new Testcontainers-backed integration test exercising, against one single key, in order: `POST /api-keys` (create) → `POST /api-keys/token` (exchange, succeeds, JWT claims verified) → `DELETE /api-keys/{keyUuid}` (revoke) → `POST /api-keys/token` again with the same credential (fails, uniform 401).

**Out:** any change to `ApiKeyService`, `ApiKeyController`, `ApiKeyTokenIssuer`, `ApiKeyExceptionHandler`, or any other production file (T24/T25/T26-frozen). Re-testing each operation's own isolated edge cases (already covered by `ApiKeyServiceIntegrationTest`, `ApiKeyExchangeIntegrationTest`, `ApiKeyCrudIntegrationTest`). Any Flyway migration, config key, or contract file.

## Business Rules

- **R30** — create via `POST /api-keys`.
- **R31** — exchange via `POST /api-keys/token`, valid key → 10-minute JWT.
- **R33** — exchange of a revoked key → uniform 401.
- **R35** — revoke via `DELETE /api-keys/{keyUuid}`.

## Locked Decisions

- **L7** — key format / SHA-256-only / plaintext-once. Exercised, not re-verified in isolation (already covered elsewhere).
- **L8** — JWT claim contract. The pre-revocation exchange's JWT is decoded and its `sub`/`scope`/`amr` asserted (AC5) — not a full re-verification of every L9 claim (already exhaustive in `ApiKeyTokenIssuerTest`/`ApiKeyExchangeIntegrationTest`), just enough to prove the exchange step genuinely succeeded rather than merely returning 200.

## Dependencies

`TestcontainersConfiguration`, `TestRestTemplate` + `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `ApiKeyTokenIssuer` (for minting a bearer JWT to authenticate the create/list/revoke calls, T26's established technique — note: this is a *different* JWT than the one the flow test itself exchanges for and asserts on), `AccountService`/`RoleService`/`MfaService` (merchant+MFA seeding, independently reimplemented per this module's established discipline). No new repository method, config key, or migration.

## Inputs

A single merchant account, seeded with `MERCHANT` role and confirmed TOTP MFA (same pattern as every other integration test in this module). No other external input.

## Outputs

No new production output — this task only asserts against the existing, unmodified behavior of `POST /api-keys`, `POST /api-keys/token`, and `DELETE /api-keys/{keyUuid}` in sequence.

## State Changes

None beyond what the existing endpoints already do. No new state.

## Files to Create

- `apikey/ApiKeyLifecycleIntegrationTest.java` — resolves Phase 1's OQ2. A dedicated new file, not an extension of any of the three existing integration test files, since the flow legitimately spans all three (`ApiKeyExchangeIntegrationTest`, `ApiKeyCrudIntegrationTest`, and transitively `ApiKeyServiceIntegrationTest`'s territory) and picking one to host it would misrepresent the test's actual scope. Matches this pipeline's established one-new-file-per-task convention for integration tests (T25, T26 each added their own).

## Files to Modify

None.

## Files NOT to Modify

`apikey/ApiKeyService.java`, `ApiKeyController.java`, `ApiKeyTokenIssuer.java`, `ApiKeyExceptionHandler.java`, `ApiKeyRepository.java`, `ApiKey.java`, `ApiKeyHasher.java`, `ApiKeyProperties.java`, all existing exception classes; `common/**`; `token/**`; every existing test file in `apikey/`; all Flyway migrations; `spec/**`.

## Acceptance Criteria

- **AC1** — Create → exchange succeeds (200, decodable JWT with expected `sub`/`scope`/`amr`) before any revocation. *(R30, R31, L8)*
- **AC2** — The identical key, after revocation, fails the identical exchange call with a uniform 401. *(R33, R35)*
- **AC3** — The post-revocation 401 body matches the shape every other rejection cause produces (`ProblemTypes.API_KEY_EXCHANGE_REJECTED`, no detail) — not a distinguishable "revoked" variant. *(R33)*
- **AC4** — The entire sequence runs against one single key, through real HTTP calls, against a real running server and real Postgres — never a direct `ApiKeyService` method call standing in for an HTTP step. *(Task statement)*
- **AC5** — `last_used_at` reflects the pre-revocation exchange (proves the exchange step actually persisted its effect, not just returned 200). *(R32, referenced)*

## Required Tests

**Resolves Phase 1's OQ1:** one new test method, distinctly named (not reusing any of the four names already implemented elsewhere in this module) — e.g. `shouldSupportFullCreateExchangeRevokeExchangeFailsLifecycle` — composing the sequence described in Scope. This is the task's actual deliverable; none of the four "named tests" listed in this task's own header are literally written as new methods here, since all four already exist, correctly, at other layers (see Phase 1's finding). Javadoc on the new test will explicitly cross-reference the requirement IDs it traces to (R30/R31/R33/R35) and note which existing test files already cover each operation's own isolated edge cases, so a future reader understands why this test exists alongside them rather than assuming duplication.

**Regression:** `ArchitectureTest`, and the three existing `apikey` integration test files, unaffected (no production code changes).

## Constraints

- **Thread-safety** — N/A, a single-threaded sequential test.
- **Transaction** — N/A, no new production transactional boundary; the test itself makes ordinary sequential HTTP calls, each hitting the server's own already-correct transaction handling.
- **Module boundaries** — N/A, test-only, no new production class.
- **Null handling** — N/A.
- **Security** — the test must never log or assert-print the plaintext key or its hash anywhere a real test run's console output would retain it beyond the assertion failure messages AssertJ already produces on failure (consistent with every other test in this module).

## Open Questions

**No blockers.** Both of Phase 1's open items (test naming/count, file placement) are resolved above as this TIB's own recommendation, available for Phase 3/4 to challenge or confirm.

---

**Phase 2 complete — TIB written.** Proceed to Phase 3 (Design Challenge, Kimi 2.7) on approval.
