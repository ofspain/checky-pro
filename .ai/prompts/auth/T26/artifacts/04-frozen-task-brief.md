# auth · T26 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Approved by femi at the Phase 4 human gate, 2026-08-15. Consumes `artifacts/02-task-implementation-brief.md` (TIB) and `artifacts/03-design-challenge.md` (Kimi 2.7, 8 findings). Downstream phases may not renegotiate this brief.

---

## Task

T26 — **API-key CRUD controller.** Add `POST /api-keys`, `GET /api-keys`, `DELETE /api-keys/{keyUuid}`. Ensure responses never include the secret.
(Verbatim, `spec/auth-service/tasks.md` task 26.)

## Purpose

Give merchants self-service HTTP access to the API-key lifecycle `ApiKeyService` (T24) already fully implements — create a key, list their own keys, revoke one — with no operator involvement.

---

## Phase 4 decisions (gate outcomes)

### D1 — Name validation (resolves Kimi #1)

**Decided:** `CreateApiKeyRequest.name` gets `@NotBlank` and `@Size(max = 100)`; the controller's `create` method takes `@Valid @RequestBody CreateApiKeyRequest`. A blank or over-length name now fails via Spring's `MethodArgumentNotValidException`, already handled by the framework-level `ApiExceptionHandler.onValidationFailure` (400, `application/problem+json`, no rejected value echoed) — no new handler code needed. `ApiKeyService.requireValidName`'s own `IllegalArgumentException` check becomes unreachable defense-in-depth via this HTTP path, same as `ApiKeyService.create`'s MERCHANT/MFA re-check already is.

### D2 — `POST /api-keys` response type (resolves Kimi #3, #7)

**Decided:** reuse `ApiKeyService.CreateApiKeyResult` directly as the response body. It already has no hash field by construction (its own Javadoc: "this record cannot leak key material") and already carries exactly the fields the frozen Outputs need (`keyUuid`, `plaintextKey`, `name`, `createdAt`). No new `CreateApiKeyResponse` DTO. `apikey/dto/ApiKeyResponse.java` (originally planned for the create response too) is **not created for this purpose**.

### D3 — `GET /api-keys` response type (extension of D2, same session)

**Decided:** by the identical reasoning as D2, `GET /api-keys` returns `List<ApiKeyService.ApiKeyMetadata>` directly — it already has no hash field and already carries every field this endpoint needs (`keyUuid`, `name`, `scopes`, `createdAt`, `lastUsedAt`, `expiresAt`, `revokedAt`). **`apikey/dto/ApiKeyResponse.java` is dropped entirely — not created.** `design.md` §6's file tree names this file, but that tree is illustrative, not literal (same precedent this pipeline has applied before when a design doc's stale file list conflicted with simpler, already-correct actual code, e.g. T16/T17's file-map deviations).

### D4 — HTTP 201 for create (resolves Kimi #4)

**Decided:** `create` returns `ResponseEntity.status(HttpStatus.CREATED).body(result)`. No `Location` header (see D8).

### D5 — Problem-type naming (resolves Kimi #6)

**Decided:** add two new `ProblemTypes` constants, `API_KEY_NOT_FOUND` and `API_KEY_NOT_AUTHORIZED`, rather than reusing the generic cross-cutting `NOT_FOUND`. Matches the module-specific-constant precedent T25 already set with `API_KEY_EXCHANGE_REJECTED`.

### D6 — `InvalidAccountStateException` on a non-`ACTIVE` account (resolves Kimi #5)

**Decided:** accept the existing global `AccountExceptionHandler` behavior as-is — 409 CONFLICT, `ProblemTypes.INVALID_STATE`, `detail` = the exception's message (which names the account UUID and current status). **No new code.** `AccountExceptionHandler` and `InvalidAccountStateException` are both outside T26's authorized file scope regardless.

**Why this is accepted rather than fixed:** this is pre-existing, already-shipped, service-wide infrastructure — not something T26 introduces. R46's "no internal detail" concern targets enumeration-sensitive *unauthenticated* flows (registration, login, password reset) where revealing state to a caller who isn't yet proven to be the account owner leaks information about a third party. Here the caller is already authenticated as the exact account named in the detail message — being told their own account's own status is not an enumeration leak.

### D7 — Non-UUID `Authentication` principal (resolves Kimi #2)

**Decided:** no guard added. `UUID.fromString(authentication.getName())` is used exactly as `AccountController` already establishes for every other "act on my own resource" endpoint. **Accepted, named, pre-existing limitation** — not a regression T26 introduces, and not this task's to fix service-wide. A client-credentials-grant token whose principal isn't a UUID would 500 on these endpoints, identically to how it would already 500 on `GET /accounts/me` today.

### D8 — `Location` header (resolves Kimi #8)

**Decided:** omit it. No `GET /api-keys/{keyUuid}` endpoint exists for it to correctly resolve to. Required Tests must assert its **absence**, not presence, so a future maintainer doesn't "fix" this as an oversight.

---

## Phase 3 findings — disposition

| # | Finding | Disposition |
|---|---|---|
| 1 | Name validation → 500 | **Accepted → D1.** |
| 2 | Non-UUID principal assumption | **Accepted as a named limitation → D7.** No code change. |
| 3 | POST response type ambiguous | **Accepted → D2.** |
| 4 | No 201 mechanism | **Accepted → D4.** |
| 5 | `InvalidAccountStateException` → 409 with detail | **Accepted as-is → D6.** No code change. |
| 6 | Problem-type naming unresolved | **Accepted → D5.** |
| 7 | No explicit no-hash-leak guarantee | **Accepted, folded into D2/D3** (structural guarantee) plus a new required test. |
| 8 | `Location` header unspecified | **Accepted → D8** (omit). |

No findings rejected. No Open Questions carried forward — all eight were resolved at this gate.

---

## Scope (final)

**In**

- `POST /api-keys`, `GET /api-keys`, `DELETE /api-keys/{keyUuid}` — all authenticated.
- `apikey/dto/CreateApiKeyRequest.java` — the only new DTO.
- RFC 9457 mappings in `apikey/ApiKeyExceptionHandler.java` for `ApiKeyNotFoundException` (404) and `ApiKeyNotAuthorizedException` (403).
- Two new `ProblemTypes` constants (D5).

**Out**

- Any modification to `ApiKeyService`, `ApiKeyRepository`, `ApiKey`, `ApiKeyHasher`, `ApiKeyProperties`, `ApiKeyExchangeRejectedException`, `ApiKeyNotFoundException`, `ApiKeyNotAuthorizedException` (T24-frozen).
- `POST /api-keys/token`, `ApiKeyTokenIssuer` (T25-frozen).
- `AccountExceptionHandler`, `InvalidAccountStateException` (D6 — accepted as-is, not touched).
- A per-merchant key cap or additional scopes (`package.md` §11 Q3, unresolved by the spec author).
- A guard for non-UUID `Authentication` principals (D7).
- Any Flyway migration; `PublicEndpoints.java` (none of these endpoints are public).

## Business Rules

- **R30** — `POST /api-keys` creates a key for a `MERCHANT` caller with confirmed MFA; plaintext returned exactly once.
- **R34** — `GET /api-keys` returns the caller's own keys, metadata only.
- **R35** — `DELETE /api-keys/{keyUuid}` revokes a key the caller owns; audits `api_key.revoked`.
- **R43** *(referenced)* — already satisfied inside `ApiKeyService`.
- **R46** *(referenced)* — 4xx is `application/problem+json`; D6 documents the one pre-existing, accepted, self-account exception to "no detail."

## Locked Decisions

- **L7** — key format / SHA-256-only / plaintext-once. Enforced entirely inside `ApiKeyService`, T24-frozen.
- **L12** *(referenced)* — no cross-module entity import. Satisfied by construction: `CreateApiKeyResult`/`ApiKeyMetadata` are records, not entities.

## Inputs

- `POST /api-keys` — authenticated, body `{ "name": string }` (1–100 chars, non-blank).
- `GET /api-keys` — authenticated, no body.
- `DELETE /api-keys/{keyUuid}` — authenticated, `keyUuid` path variable (Spring-validated as a UUID before the handler runs).

## Outputs

- **201** `POST /api-keys` → `ApiKeyService.CreateApiKeyResult` (`keyUuid`, `plaintextKey`, `name`, `createdAt`). No `Location` header (D8).
- **200** `GET /api-keys` → `List<ApiKeyService.ApiKeyMetadata>` (`keyUuid`, `name`, `scopes`, `createdAt`, `lastUsedAt`, `expiresAt`, `revokedAt`) — empty list if the caller has no keys, never an error.
- **204** `DELETE /api-keys/{keyUuid}` — on success, including when the key was already revoked (idempotent).
- **400** — invalid `CreateApiKeyRequest` body (D1), existing framework handler, no rejected value echoed.
- **403** — `ApiKeyNotAuthorizedException` (`ProblemTypes.API_KEY_NOT_AUTHORIZED`, D5).
- **404** — `ApiKeyNotFoundException` (`ProblemTypes.API_KEY_NOT_FOUND`, D5) — identical body whether the key doesn't exist or exists but isn't owned by the caller.
- **409** — `InvalidAccountStateException` via the existing, unmodified `AccountExceptionHandler` (D6) — accepted with detail.

No response other than the single `POST /api-keys` success body ever contains a hash-shaped or plaintext-key-shaped value.

## State Changes

None beyond what `ApiKeyService.create`/`.revoke` already do (T24-frozen). T26 adds no new state.

## Files to Create

- `apikey/dto/CreateApiKeyRequest.java`

## Files to Modify

- `apikey/ApiKeyController.java` — add `create`, `list`, `revoke` methods to the existing class.
- `apikey/ApiKeyExceptionHandler.java` — add mappings for `ApiKeyNotFoundException` (404) and `ApiKeyNotAuthorizedException` (403).
- `common/ProblemTypes.java` — add `API_KEY_NOT_FOUND`, `API_KEY_NOT_AUTHORIZED`.

## Files NOT to Modify

`apikey/ApiKeyService.java`, `ApiKeyRepository.java`, `ApiKey.java`, `ApiKeyHasher.java`, `ApiKeyProperties.java`, `ApiKeyExchangeRejectedException.java`, `ApiKeyNotFoundException.java`, `ApiKeyNotAuthorizedException.java`, `ApiKeyTokenIssuer.java`; `account/AccountExceptionHandler.java`, `account/InvalidAccountStateException.java`; `common/PublicEndpoints.java`; `token/**`; all Flyway migrations; `spec/**`.

## Acceptance Criteria

- **AC1** — all three endpoints require authentication; none appear in `PublicEndpoints`. *(R30, R34, R35)*
- **AC2** — caller identity comes from `Authentication` only, matching `AccountController`'s established pattern (D7). *(R30, R34)*
- **AC3** — `POST /api-keys` returns 201 and the plaintext key exactly once, via `CreateApiKeyResult` (D2, D4). *(R30, L7)*
- **AC4** — invalid `CreateApiKeyRequest` (blank/>100-char name) returns 400, no rejected value echoed (D1).
- **AC5** — `GET /api-keys` returns only the caller's own keys, via `ApiKeyMetadata`, empty list when none exist (D3). *(R34)*
- **AC6** — no response body from any of the three endpoints ever contains a 64-character-hex-shaped value or the plaintext key, except the single `POST /api-keys` success body's `plaintextKey` field (Kimi #7). *(R34, L7)*
- **AC7** — `DELETE /api-keys/{keyUuid}` for an unowned or nonexistent key returns byte-identical 404 bodies either way. *(R35)*
- **AC8** — revoking an already-revoked key returns 204, not an error. *(R35, idempotency)*
- **AC9** — `ApiKeyNotAuthorizedException` → 403 with `API_KEY_NOT_AUTHORIZED`, no detail (D5). *(R46)*
- **AC10** — a non-`ACTIVE` account on `POST /api-keys` gets the existing 409-with-detail from `AccountExceptionHandler`, unchanged (D6).
- **AC11** — no `Location` header on the 201 response (D8).
- **AC12** — `ArchitectureTest` stays green: no new class imports `PublicEndpoints` or a foreign entity. *(L12)*

## Required Tests

**Named (verbatim):**
1. `shouldCreateApiKeyAndShowPlaintextExactlyOnce` — traces to **R30**.
2. `shouldListAndRevokeOwnApiKeys` — traces to **R34**/**R35**.

**Boundary / supporting:**
3. `POST /api-keys` by a non-`MERCHANT`/unconfirmed-MFA caller → 403, `API_KEY_NOT_AUTHORIZED`, no detail.
4. `POST /api-keys` by a non-`ACTIVE` account → 409, `INVALID_STATE`, with detail (confirms D6's accepted behavior, not a regression).
5. Blank name and a 101-character name on `POST /api-keys` → 400 each, no rejected value in the body.
6. `POST /api-keys` response contains no 64-hex-character string and no field other than `plaintextKey` holding key material.
7. `GET /api-keys` with zero keys → 200, empty array.
8. `GET /api-keys` never returns another account's keys.
9. `GET /api-keys` response items contain no hash-shaped field.
10. `DELETE` of an unowned key vs. a genuinely nonexistent UUID → byte-identical 404.
11. `DELETE` of an already-revoked key → 204.
12. `POST /api-keys` response has no `Location` header.
13. Regression: `ArchitectureTest`, `ApiKeyServiceIntegrationTest`, and every T25-written `apikey`/`token` test.

## Constraints

- **Security** — plaintext key appears in exactly one response, never logged; 403/404 bodies carry no distinguishing detail; the 409 exception is the one documented, accepted departure from "no detail" (D6).
- **Thread-safety** — stateless `@RestController`, no new mutable state.
- **Transaction** — none new; `ApiKeyService`'s existing boundaries are unchanged.
- **Module boundaries** — no `apikey` class references `PublicEndpoints`; no foreign-module entity import.
- **Null handling** — `keyUuid` path variable is framework-validated as a UUID; malformed input 400s before the handler runs.

## Open Questions

**No blockers.** All eight Phase 3 findings resolved at this gate (D1–D8); the D2→D3 extension (dropping `ApiKeyResponse.java`) confirmed in the same gate session.

---

**Phase 4 complete — brief FROZEN.** Proceed to Phase 5 (Implementation Plan).
