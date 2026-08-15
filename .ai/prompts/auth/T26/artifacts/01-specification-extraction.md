<!-- MODEL: Claude Sonnet — Phase 1 (Specification Extraction). -->

# auth · T26 · Phase 1 — Specification Extraction

## Business Rules

- **R30** — An authenticated user with `MERCHANT` role and confirmed MFA calling `POST /api-keys` with a name gets a new key created (prefix `ck_live_`, SHA-256 hash stored, plaintext returned exactly once, `api_key.created` audited). *(Already fully implemented by `ApiKeyService.create`, T24 — this task's job is the HTTP entry point.)*
- **R34** — An authenticated user calling `GET /api-keys` gets their own keys back, metadata only, no secret material.
- **R35** — An authenticated user calling `DELETE /api-keys/{keyUuid}` gets that key revoked and an `api_key.revoked` audit event recorded.
- **R43** *(cross-cutting, referenced)* — Every security-relevant action is audited with a row + outbox mirror. Already satisfied inside `ApiKeyService` for all three operations; the controller layer records nothing itself.
- **R46** *(cross-cutting, referenced)* — Any 4xx response is RFC 9457 `application/problem+json`, no stack trace, no internal detail, no existence/state hint.

## Locked Decisions

- **L7** — API key format: `ck_live_<24-char alnum>.<32-char secret>`; SHA-256 only at rest; plaintext returned exactly once. *(Already enforced entirely inside `ApiKeyService`/`ApiKey`/`ApiKeyHasher`, T24-frozen — this task must not weaken or duplicate that logic, only expose it over HTTP.)*
- **L12** *(cross-cutting)* — No cross-module entity imports; shared plumbing only in `common`. `ApiKeyService`'s public methods already return DTOs/records, never the `ApiKey` entity — the controller has no reason to import anything beyond `ApiKeyService` itself.
- **L13** *(cross-cutting)* — No secret/key material logged. The response for `POST /api-keys` legitimately contains the plaintext key (R30 requires it, exactly once) — this is not a violation of L13 (which governs *logging*, not the one-time API response L7 itself mandates); no controller/handler code may log it.

## Files Involved

**Existing, to read/extend (not to be re-implemented):**
- `apikey/ApiKeyService.java` — `create`, `list`, `revoke`, plus the `CreateApiKeyResult`, `ApiKeyMetadata` records already returned by them (T24, frozen).
- `apikey/ApiKeyNotFoundException.java`, `apikey/ApiKeyNotAuthorizedException.java` — exist, currently unmapped to any HTTP response (T24, frozen).
- `apikey/ApiKeyController.java` — exists, currently scoped to only `POST /api-keys/token` (T25). Whether T26 extends this class or adds a new one is an open design question (Phase 2).
- `apikey/ApiKeyExceptionHandler.java` — exists, currently maps only `ApiKeyExchangeRejectedException` → 401 (T25). Needs new mappings.
- `common/ProblemTypes.java` — may already have a reusable `NOT_FOUND` constant; whether `ApiKeyNotAuthorizedException` needs a new constant or reuses an existing one (e.g. a 403-shaped type) is a Phase 2 decision.
- `account/AccountController.java` — the established pattern for deriving the caller from `Authentication` (`UUID.fromString(authentication.getName())`) on a "my own resource" endpoint.

**New, expected by `design.md` §6's file map:**
- `apikey/dto/CreateApiKeyRequest.java` — request body for `POST /api-keys` (a `name` field, per R30).
- `apikey/dto/ApiKeyResponse.java` — response shape for `GET /api-keys`'s list items (and possibly `POST /api-keys`'s metadata portion) — no hash/secret field, per R34/L7.

## Dependencies

- **Services:** `ApiKeyService` (the only collaborator the controller needs — `create`/`list`/`revoke` are already complete).
- **Exceptions to map:** `ApiKeyNotFoundException`, `ApiKeyNotAuthorizedException` (both pre-existing).
- **No new repository method anticipated** — `ApiKeyRepository` already has everything `create`/`list`/`revoke` use.
- **No new config key anticipated.**
- **No new migration anticipated** (L1 — `V1`–`V4` immutable; nothing here needs schema beyond what already exists).
- **Contracts:** `contracts/api/auth.yaml` and `contracts/api/token-claims.md` still do not exist in this repo (confirmed at Phase 0, same gap as T25) — contract conformance cannot be verified for these endpoints either; deferred to the same owner as T25's equivalent gap (tasks 33/34).

## Acceptance Criteria (mapped)

- **AC1** — `POST /api-keys` requires authentication (not in `PublicEndpoints`); derives the caller from `Authentication`, never a body-supplied account id. *(R30)*
- **AC2** — A successful `POST /api-keys` returns the plaintext key exactly once, plus whatever metadata the response DTO carries; `ApiKeyService.create`'s own role/status/MFA checks surface as the correct HTTP status (not silently swallowed or mis-mapped). *(R30, L7)*
- **AC3** — `GET /api-keys` requires authentication; returns only the caller's own keys; response items carry no `keyHash`/plaintext field of any kind. *(R34, L7)*
- **AC4** — `DELETE /api-keys/{keyUuid}` requires authentication; revoking an unowned or nonexistent key returns a uniform not-found response (no enumeration oracle distinguishing "doesn't exist" from "exists but isn't yours" — already guaranteed by `ApiKeyService.revoke`'s single `ApiKeyNotFoundException`, the controller must not add a distinguishing wrapper). *(R35)*
- **AC5** — Revoking an already-revoked key is idempotent at the HTTP layer too (matches `ApiKeyService.revoke`'s idempotent no-op) — almost certainly a `204`/success on the second call, not a new error, since the service itself doesn't throw in that case.
- **AC6** — `ApiKeyNotAuthorizedException` and `ApiKeyNotFoundException` both map to RFC 9457 `application/problem+json` bodies with no internal detail. *(R46)*
- **AC7** — No controller/handler code logs or otherwise persists the plaintext key beyond the single response it's legitimately returned in. *(L7, L13)*
- **AC8** — `ArchitectureTest` stays green: no new class references `PublicEndpoints`, no foreign-module entity import. *(L12)*

## Tests Required

**Named (verbatim method names, per this task's header — note both are mismapped in `package.md` §8 itself, see Open Questions):**
1. `shouldCreateApiKeyAndShowPlaintextExactlyOnce` — traces to **R30**.
2. `shouldListAndRevokeOwnApiKeys` — traces to **R34**/**R35**.

**Boundary / supporting (implied, not yet decided how to split by phase — Phase 5 will plan):**
3. `POST /api-keys` by a non-`MERCHANT` or MFA-unconfirmed caller surfaces `ApiKeyNotAuthorizedException` as the correct HTTP status, not a 500.
4. `POST /api-keys` by a non-`ACTIVE` account surfaces `InvalidAccountStateException` — **note:** this exception is from the `account` module, not `apikey`; whether the `apikey` module's own exception handler should map a foreign-module exception, or whether this is expected to already be handled by a different `@RestControllerAdvice`, is worth checking at Phase 2 (the existing `AccountExceptionHandler` already maps `InvalidAccountStateException`, so this may already work with zero new code — to be confirmed, not assumed).
5. `POST /api-keys` with a blank or over-length name → validation error (`ApiKeyService.create` already throws `IllegalArgumentException` for this — needs a mapping, or `@Valid` at the DTO level pre-empting it entirely).
6. `GET /api-keys` returns an empty list for a caller with no keys (not an error).
7. `GET /api-keys` never returns another account's keys.
8. `GET /api-keys` response items contain no field resembling a hash or the plaintext key.
9. `DELETE /api-keys/{keyUuid}` for a key owned by a different account → the same uniform not-found response as a genuinely nonexistent UUID.
10. `DELETE /api-keys/{keyUuid}` on an already-revoked key → success (idempotent), not an error.
11. `ArchitectureTest`, `ApiKeyServiceIntegrationTest`, and every T25-written `apikey`/`token` test stay green (regression).

## Open Questions

- **`package.md` §8's traceability table is wrong for both of this task's named tests** (same recurring bug flagged at nearly every prior task): it maps `shouldCreateApiKeyAndShowPlaintextExactlyOnce` → R27 and `shouldListAndRevokeOwnApiKeys` → "R30 / R31" — neither is correct by content. This task's own header already states the correct R30/R34/R35, so **not a blocker**, just noted per standing practice; `spec/` is immutable, flagged for the spec author.
- **`package.md` §11 Q3 (unresolved):** "Should there be a maximum number of active API keys per merchant? Is the only scope at launch `merchant.api`, or are additional scopes needed?" `ApiKeyService.create` currently enforces no per-account cap. **Not blocking for T26** — R30 doesn't require a cap, and adding one would be scope creep into `ApiKeyService` (T24-frozen) for a question the spec author hasn't answered. Deferred, same as T25 treated its own analogous open scope question.
- **Whether T26 extends the existing `ApiKeyController` (T25) or introduces a second controller class** — a genuine, real decision, not yet made. Surfaced at Phase 0, restated here, to be resolved at Phase 2 (TIB) with Phase 3/4 available to weigh in.
- **Whether `POST /api-keys`'s non-`MERCHANT`/unconfirmed-MFA/non-`ACTIVE` rejection paths need new exception-handler mappings in `apikey`, or already resolve correctly via `account`'s existing `AccountExceptionHandler`/`ApiKeyNotAuthorizedException`'s (currently unmapped) status** — needs verification at Phase 2, not assumed.

---

**Phase 1 complete — specification extracted.** Proceed to Phase 2 (Task Implementation Brief) on approval.
