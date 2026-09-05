<!-- MODEL: Claude Sonnet — Phase 2 (Task Implementation Brief). -->

# auth · T26 · Phase 2 — Task Implementation Brief (TIB)

## Task

**API-key CRUD controller.** Add `POST /api-keys`, `GET /api-keys`, `DELETE /api-keys/{keyUuid}`. Ensure responses never include the secret. *(Verbatim, `spec/auth-service/tasks.md` task 26.)*

## Purpose

Give merchants a self-service HTTP surface over the API-key lifecycle `ApiKeyService` (T24) already fully implements — create a key, list their own keys, revoke one — so a merchant never needs an operator to mint or rotate their machine credentials.

## Scope

**In:** `POST /api-keys`, `GET /api-keys`, `DELETE /api-keys/{keyUuid}` — all authenticated. Request/response DTOs (`CreateApiKeyRequest`, `ApiKeyResponse`). RFC 9457 mappings for `ApiKeyNotFoundException` and `ApiKeyNotAuthorizedException` (both exist, currently unmapped to any HTTP response).

**Out:** Any change to `ApiKeyService`, `ApiKeyRepository`, `ApiKey`, `ApiKeyHasher`, `ApiKeyProperties`, or any exception's *logic* (T24-frozen). `POST /api-keys/token`, `ApiKeyTokenIssuer`, and `ApiKeyExchangeRejectedException`'s mapping (T25-frozen). A per-merchant key cap or additional scopes (`package.md` §11 Q3, unresolved by the spec author — not this task's to decide). Any Flyway migration.

## Business Rules

- **R30** — `POST /api-keys` with a name creates a key for a `MERCHANT` caller with confirmed MFA; returns the plaintext key exactly once.
- **R34** — `GET /api-keys` returns the caller's own keys, metadata only, no secret material.
- **R35** — `DELETE /api-keys/{keyUuid}` revokes a key the caller owns; records `api_key.revoked`.
- **R43** *(referenced)* — every action already audited inside `ApiKeyService`; no controller-level audit call needed.
- **R46** *(referenced)* — every 4xx is `application/problem+json`, no internal detail, no existence hint.

## Locked Decisions

- **L7** — key format / SHA-256-only-at-rest / plaintext-once. Fully enforced inside `ApiKeyService`; T26 must not duplicate, weaken, or bypass it.
- **L12** *(referenced)* — no cross-module entity imports; `ApiKeyService`'s methods already return DTOs/records, never the `ApiKey` entity, so this is satisfied by construction as long as the controller stays a thin pass-through.

## Dependencies

`ApiKeyService.create(UUID, String)` / `.list(UUID)` / `.revoke(UUID, UUID)` (all complete, T24). `ApiKeyNotFoundException`, `ApiKeyNotAuthorizedException` (exist, T24, unmapped). `Authentication` → `UUID.fromString(authentication.getName())` (established pattern, `AccountController`). No new repository method, config key, or migration.

## Inputs

- `POST /api-keys` — authenticated, body `{ "name": string }`.
- `GET /api-keys` — authenticated, no body.
- `DELETE /api-keys/{keyUuid}` — authenticated, `keyUuid` path variable.

## Outputs

- `POST /api-keys` → **201**, body: plaintext key (once), key UUID, name, created-at. *(Exact field list is a Phase 5 plan-level detail, not fixed here — must include the plaintext key and must not include the hash.)*
- `GET /api-keys` → **200**, array of `ApiKeyResponse` (UUID, name, scopes, created/last-used/expires/revoked timestamps) — no hash, no plaintext, ever.
- `DELETE /api-keys/{keyUuid}` → **204** on success, including when the key was already revoked (idempotent, matches `ApiKeyService.revoke`).
- **404** — `ApiKeyNotFoundException` (unowned or nonexistent key on `DELETE`) — one uniform body, no enumeration hint between the two causes (already guaranteed by the single exception type).
- **403** — `ApiKeyNotAuthorizedException` (`POST` by a non-`MERCHANT` or MFA-unconfirmed caller).
- Any account-state rejection (`InvalidAccountStateException`, e.g. a non-`ACTIVE` account) is a foreign-module exception already mapped globally by `AccountExceptionHandler` — Spring's `@RestControllerAdvice` resolution is global, not package-scoped, so this is expected to already work with zero new `apikey`-module code. **To be confirmed at Phase 3/4, not assumed.**

## State Changes

None beyond what `ApiKeyService.create`/`.revoke` already do (T24-frozen: a new `api_keys` row on create; `revoked_at` set on revoke; one audit row + outbox mirror per operation). T26 adds no new state.

## Files to Create

- `apikey/dto/CreateApiKeyRequest.java`
- `apikey/dto/ApiKeyResponse.java`

## Files to Modify

- `apikey/ApiKeyController.java` — add `create`/`list`/`revoke` endpoints to the existing class (T25 already scoped it to `/api-keys` + `POST /token`; `design.md` §6 names a single `ApiKeyController.java` for the whole module, so extending is the file map's own intent, not a new design call).
- `apikey/ApiKeyExceptionHandler.java` — add mappings for `ApiKeyNotFoundException` (404) and `ApiKeyNotAuthorizedException` (403).
- `common/ProblemTypes.java` — add one new URI constant for the not-authorized case (`ApiKeyNotFoundException` can likely reuse the existing generic `NOT_FOUND` constant — to be confirmed at Phase 5, not a blocker).

## Files NOT to Modify

`apikey/ApiKeyService.java`, `ApiKeyRepository.java`, `ApiKey.java`, `ApiKeyHasher.java`, `ApiKeyProperties.java`, `ApiKeyExchangeRejectedException.java`, `ApiKeyNotFoundException.java`, `ApiKeyNotAuthorizedException.java`, `ApiKeyTokenIssuer.java`; `common/PublicEndpoints.java` (none of T26's endpoints are public); `token/**`; all Flyway migrations; `spec/**`.

## Acceptance Criteria

- **AC1** — All three endpoints require authentication; none appear in `PublicEndpoints`. *(R30, R34, R35)*
- **AC2** — Caller identity for all three endpoints comes from `Authentication`, never a body/path-supplied account id. *(R30, R34)*
- **AC3** — `POST /api-keys` returns the plaintext key exactly once, on success only. *(R30, L7)*
- **AC4** — `GET /api-keys` returns only the authenticated caller's own keys. *(R34)*
- **AC5** — No response from any of the three endpoints ever contains a key hash or plaintext secret except the single `POST /api-keys` success body's plaintext field. *(R34, L7)*
- **AC6** — `DELETE /api-keys/{keyUuid}` for an unowned or nonexistent key returns the identical 404 body either way. *(R35)*
- **AC7** — Revoking an already-revoked key succeeds (204), not an error. *(R35, idempotency)*
- **AC8** — `ApiKeyNotAuthorizedException` → 403, `application/problem+json`, no internal detail. *(R46)*
- **AC9** — `ArchitectureTest` stays green: no new class imports `PublicEndpoints` or a foreign entity. *(L12)*

## Required Tests

**Named (verbatim):**
1. `shouldCreateApiKeyAndShowPlaintextExactlyOnce` — traces to **R30**.
2. `shouldListAndRevokeOwnApiKeys` — traces to **R34**/**R35**.

**Boundary / supporting:**
3. Non-`MERCHANT`/unconfirmed-MFA caller on `POST /api-keys` → 403, uniform body.
4. Non-`ACTIVE` account on `POST /api-keys` → whatever `AccountExceptionHandler` already produces for `InvalidAccountStateException` (verify, don't assume).
5. Blank or >100-character name on `POST /api-keys` → 400 validation error.
6. `GET /api-keys` with zero keys → empty array, 200 (not an error).
7. `GET /api-keys` never leaks another account's keys.
8. `GET /api-keys` response contains no hash-shaped or plaintext-shaped field.
9. `DELETE` of an unowned key vs. a nonexistent key → byte-identical 404.
10. `DELETE` of an already-revoked key → 204, not an error.
11. Regression: `ArchitectureTest`, `ApiKeyServiceIntegrationTest`, and every T25 `apikey`/`token` test.

## Constraints

- **Security** — the plaintext key appears in exactly one response body, never logged, never in any `GET`/list response. 403/404 bodies carry no distinguishing detail.
- **Thread-safety** — a stateless `@RestController`; no new mutable state.
- **Transaction** — none new; `ApiKeyService`'s existing `@Transactional` boundaries are unchanged and untouched.
- **Module boundaries** — no `apikey` class references `PublicEndpoints`; no foreign-module entity import (only DTOs/records/public service methods, matching T25's precedent).
- **Null handling** — `keyUuid` path variable is framework-validated as a UUID by Spring's converter (a malformed UUID 400s before the controller runs); no explicit null check needed inside the handler method itself.

## Open Questions

**No blockers.** Two items flagged for Phase 3/4 confirmation rather than silently assumed: (1) whether `InvalidAccountStateException` really does resolve correctly with zero new code via the existing global `AccountExceptionHandler` (near-certain given Spring's global `@RestControllerAdvice` resolution, but not yet empirically confirmed); (2) whether `ApiKeyNotFoundException` should reuse `ProblemTypes.NOT_FOUND` or get its own dedicated constant (a naming/consistency call, not a functional one).

---

**Phase 2 complete — TIB written.** Proceed to Phase 3 (Design Challenge, Kimi 2.7) on approval.
