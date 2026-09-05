<!-- MODEL: Claude Sonnet — Phase 1 (Specification Extraction). -->

# auth · T27 · Phase 1 — Specification Extraction

## Business Rules

- **R30** — `POST /api-keys` (authenticated `MERCHANT` + confirmed MFA) creates a key, returns plaintext once.
- **R31** — `POST /api-keys/token` with a valid, non-expired, non-revoked key issues a 10-minute JWT (`sub` = merchant account UUID, `scope` ⊇ `merchant.api`, `amr` ∋ `api_key`).
- **R33** — A revoked, expired, malformed, or hash-mismatched key at exchange returns a uniform 401.
- **R35** — `DELETE /api-keys/{keyUuid}` (authenticated owner) revokes the key.

*(All four already fully implemented across T24/T25/T26; T27 adds no new behavior — it proves the sequence.)*

## Locked Decisions

- **L7** — key format `ck_live_<24 alnum>.<32 alnum>`; SHA-256 only at rest; plaintext returned exactly once. Already enforced entirely inside `ApiKeyService`/`ApiKey`/`ApiKeyHasher` (T24-frozen).
- **L8** — the exchanged JWT's claim contract (10-minute RS256, `sub`/`scope`/`amr`/`roles`/`client_id`). Already enforced entirely inside `ApiKeyTokenIssuer` (T25-frozen).

## Files Involved

**Existing, to read/exercise only (no production code changes — this is a test-only task):**
- `apikey/ApiKeyController.java` — all four HTTP operations (T25 `exchange`, T26 `create`/`list`/`revoke`).
- `apikey/ApiKeyService.java` — the underlying operations (T24), unmodified.
- `apikey/ApiKeyTokenIssuer.java` — mints the exchange JWT (T25), unmodified.
- `apikey/ApiKeyExceptionHandler.java` — maps rejection exceptions to RFC 9457 bodies, unmodified.

**Existing test files this task must not duplicate coverage from, only build on:**
- `ApiKeyServiceIntegrationTest` (T24, service-layer only).
- `ApiKeyExchangeIntegrationTest` (T25, HTTP-layer, exchange-only).
- `ApiKeyCrudIntegrationTest` (T26, HTTP-layer, CRUD-only).

**New:** exactly one new test method (location — new file vs. an existing one — is a Phase 2 decision, not resolved here). No new production file; `design.md` §6's file map names no new file for this task either (it's a `tasks.md`-only entry, not reflected in the file map at all, consistent with being test-only).

## Dependencies

- `TestcontainersConfiguration` (real Postgres + Kafka).
- `TestRestTemplate` + `@SpringBootTest(webEnvironment = RANDOM_PORT)` — the only way to exercise the real filter chain in this module (no `MockMvc` precedent).
- `ApiKeyTokenIssuer` bean — for minting a bearer JWT to authenticate the CRUD calls (T26's established technique).
- Account/role/MFA seeding collaborators: `AccountService`, `RoleService`, `MfaService` — each existing integration test in this module reimplements its own `seedMerchantWithConfirmedMfa` + reference TOTP generator rather than sharing one; T27 should follow the same discipline.
- No new repository method, config key, or migration.

## Acceptance Criteria

- **AC1** — A key created via `POST /api-keys` successfully exchanges via `POST /api-keys/token` (200, valid JWT) before any revocation. *(R30, R31)*
- **AC2** — The same key, after `DELETE /api-keys/{keyUuid}`, fails the identical exchange call with a uniform 401. *(R33, R35)*
- **AC3** — The 401 after revocation is indistinguishable in shape from any other uniform rejection (same `ProblemTypes.API_KEY_EXCHANGE_REJECTED`, no detail) — not a special "revoked" variant. *(R33)*
- **AC4** — The whole sequence runs through real HTTP calls against a real running server and real Postgres (Testcontainers), not direct service-method calls. *(Task statement: "with Testcontainers")*
- **AC5** — The test asserts the JWT issued *before* revocation actually decodes to the expected claims (`sub`, `scope`, `amr`) — not just a 200 status — so a regression that returned 200 with a garbage body wouldn't silently pass. *(R31, L8)*

## Tests Required

**Named (verbatim, per this task's header):** `shouldCreateApiKeyAndShowPlaintextExactlyOnce`, `shouldExchangeValidApiKeyForMerchantJwt`, `shouldRejectRevokedOrUnknownApiKeyWithUniform401`, `shouldListAndRevokeOwnApiKeys`.

**These four names already exist, each covering their own isolated operation, in T24/T25/T26's own test files** (`ApiKeyServiceIntegrationTest`, `ApiKeyExchangeIntegrationTest` ×2, `ApiKeyCrudIntegrationTest`). T27's task statement — "Test create→exchange→revoke→exchange-fails **flow**" — describes a single continuous sequence, not four independent tests. **Reconciling this is a genuine, real decision for Phase 2, not resolved here:** the four named tests may be intended as the four checkpoints *within* one flow test (each named test asserting one stage), or the task may be satisfied by one new sequence test that doesn't literally carry any of these four names (since all four names are already spoken for elsewhere). Flagged as Open Question #1 below.

**Boundary / supporting (implied by the task statement, not yet decided how to structure):**
1. Exchange succeeds before revocation — decode the JWT, assert key claims (not just status 200).
2. `last_used_at` reflects the pre-revocation exchange.
3. Revoke succeeds (204).
4. The identical key/credential, re-presented after revocation, fails exchange with the same uniform 401 shape every other rejection cause produces (cross-reference against `ApiKeyExchangeIntegrationTest`'s existing malformed/unknown-prefix rejection bodies, if convenient, to prove byte-identical shape — not required, but strengthens AC3).
5. The full sequence runs against one single key end to end (not a fresh key per stage) — the point of a "flow" test is exactly that continuity.

## Open Questions

- **OQ1 (structural, needs a Phase 2 decision, not a blocker):** how to reconcile the task's four named tests (already implemented elsewhere, at other layers) with the task statement's singular "flow" framing. No spec text resolves this explicitly — `package.md` §8 doesn't have a distinct row for a T27-specific test name at all (searched, no match), reinforcing that these four names were simply carried over from the requirements they trace to, not literally four new test methods to write. Proposed default (for Phase 2 to ratify or override): write **one new sequence test**, distinctly named (e.g. `shouldSupportFullCreateExchangeRevokeExchangeFailsLifecycle`), that internally exercises all four operations in order; do not attempt to create four more test methods bearing names already used in other files.
- **OQ2 (non-blocking):** new file vs. extending an existing one (carried from Phase 0, restated here for Phase 2 to resolve).
- **No true blockers.** All four underlying HTTP operations are complete and already individually tested; this task only needs to compose them.

---

**Phase 1 complete — specification extracted.** Proceed to Phase 2 (Task Implementation Brief) on approval.
