<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). -->

# auth · T28 · Phase 5 — Implementation Plan

Consumes `artifacts/04-frozen-task-brief.md` (FROZEN, approved 2026-08-16). No code written in this phase.

---

## A load-bearing sequencing detail the frozen brief didn't pin down (flagged here, not silently decided)

Tracing `ReuseDetectingAuthorizationService.findByToken`'s actual logic: once `RefreshTokenFamily.revokedAt` is set, `checkAndRegisterPresentation` no longer matches it via `findByCurrentTokenHashAndRevokedAtIsNull` (revoked rows are excluded) — but it's also not in the archive table (it's the *current*, never-superseded token), so the reuse check returns `unknown()`. An `unknown()` outcome is **not blocked** — `findByToken` falls through to `delegate.findByToken(...)` regardless. **In other words: marking `revoked_at` alone does not stop the token from working if the raw `oauth2_authorization` row is still present.** This is exactly why the task statement calls out authorization removal as a distinct, required step — it isn't a nice-to-have alongside marking the row revoked, it's the *only* thing that actually stops the token.

**Plan decision:** for both `revokeOne` and `revokeAll`'s per-family unit, **remove the SAS authorization first, then mark the family revoked, then audit** — not the reverse. If authorization removal fails, the family must **not** be marked revoked (a family marked revoked with a live, still-working authorization is a worse, misleading state than an unrevoked family that failed to revoke cleanly and can be retried). This ordering isn't explicitly written into the frozen brief's Constraints, but it's required for AC4/AC5 to mean what they say; flagged here for Phase 7/8 to double-check rather than assumed silently correct.

---

## Files to Create

### `token/dto/SessionResponse.java`
```java
public record SessionResponse(UUID familyId, String deviceLabel, Instant createdAt, Instant rotatedAt) {
    public static SessionResponse from(RefreshTokenFamily family) { ... }
}
```
Mirrors `account/dto/AccountResponse`'s established `from(Entity)` static-factory-in-a-`dto`-subpackage precedent exactly — a `dto` subpackage importing its parent package's entity is already normal in this codebase, not a new pattern.

### `token/SessionNotFoundException.java`
```java
public class SessionNotFoundException extends RuntimeException {}
```
Mirrors `ApiKeyNotFoundException` exactly — no state, single cause, no enumeration hint between "doesn't exist" and "not yours."

### `token/SessionExceptionHandler.java`
```java
@RestControllerAdvice
public class SessionExceptionHandler {
    @ExceptionHandler(SessionNotFoundException.class)
    ProblemDetail onNotFound(SessionNotFoundException e) { ... }  // 404, SESSION_NOT_FOUND, no detail
}
```

### `token/SessionService.java`

**Public methods (signatures):**
```java
public SessionService(RefreshTokenFamilyRepository familyRepository,
                       OAuth2AuthorizationService authorizationService,
                       AuditService auditService, Clock clock)

@Transactional(readOnly = true)
public List<SessionResponse> list(UUID accountUuid)

@Transactional
public void revokeOne(UUID accountUuid, UUID familyId)   // throws SessionNotFoundException

public void revokeAll(UUID accountUuid)   // deliberately NOT @Transactional - see below
```

**Private methods:**
```java
private void revokeFamily(RefreshTokenFamily family, String reason, Instant now)
    // 1. removeSasAuthorizationIfPresent(family.getAuthorizationId())
    // 2. family.revoke(reason, now); familyRepository.save(family)
    // 3. recordAudit(family)

private void removeSasAuthorizationIfPresent(String authorizationId)
    // OAuth2Authorization auth = authorizationService.findById(authorizationId);
    // if (auth != null) authorizationService.remove(auth);   -- D2: null is a no-op, not an error

private void recordAudit(RefreshTokenFamily family)
    // auditService.record(new RecordAuditEventRequest("session.revoked", SUCCESS,
    //     accountUuidFrom(family.getPrincipalName()), same, null, null, null, null))
    // principalName -> UUID: same non-UUID-defensive parsing precedent as
    // ReuseDetectingAuthorizationService.auditReuseDetected (D5-adjacent) - if it doesn't parse,
    // audit with a null accountUuid rather than throwing, since a non-interactive principal is
    // already an accepted, out-of-scope edge case for this task.
```

**Why `revokeAll` must NOT be `@Transactional` (critical, easy to get wrong):** D3 requires each family's outcome to be independent — a failure on family #3 must not undo #1 or #2. If `revokeAll` were annotated `@Transactional`, Spring would wrap the *entire loop* in one transaction, and an exception on any iteration would roll back every JPA-side change made earlier in the same call, directly defeating D3 regardless of the `try/catch` around each iteration. Correctness here relies on **not** having an enclosing transaction: each `familyRepository.save(family)` call inside `revokeFamily` gets its own implicit transaction from Spring Data's own `@Transactional`-annotated `SimpleJpaRepository.save(...)`, which is exactly the per-family atomicity D3 needs — no explicit transaction management (no `TransactionTemplate`, no self-invocation tricks) required, as long as `revokeAll` itself stays free of `@Transactional`.

```java
public void revokeAll(UUID accountUuid) {
    Instant now = clock.instant();
    for (RefreshTokenFamily family : familyRepository.findByPrincipalNameAndRevokedAtIsNull(accountUuid.toString())) {
        try {
            revokeFamily(family, "USER_REVOKED_ALL", now);
        } catch (Exception e) {
            log.error("Failed to revoke session family {} during bulk revoke", family.getFamilyId(), e);
        }
    }
}
```

`revokeOne` stays a single `@Transactional` method (matches `ApiKeyService.revoke`'s established shape for a single-item revoke) — a failure anywhere inside it should fail the whole call, not partially succeed:
```java
@Transactional
public void revokeOne(UUID accountUuid, UUID familyId) {
    RefreshTokenFamily family = familyRepository.findByFamilyIdAndPrincipalName(familyId, accountUuid.toString())
            .orElseThrow(SessionNotFoundException::new);
    revokeFamily(family, "USER_REVOKED", clock.instant());
}
```
(`revokeFamily`'s internal `familyRepository.save(family)` call, when invoked from within `revokeOne`'s already-open transaction, simply joins it — Spring Data's default `REQUIRED` propagation — so this composes correctly with both callers without any special-casing.)

---

## Files to Modify

### `token/RefreshTokenFamilyRepository.java`
Add one method (D1 — deliberately no `revokedAt` filter, so an already-revoked-but-owned family is still found and `revoke()`'s existing idempotency takes over):
```java
Optional<RefreshTokenFamily> findByFamilyIdAndPrincipalName(UUID familyId, String principalName);
```

### `common/ProblemTypes.java`
Add:
```java
public static final URI SESSION_NOT_FOUND = URI.create(BASE + "session-not-found");
```

### `account/AccountController.java`
Add a new constructor dependency (`SessionService`) and three methods:
```java
@GetMapping("/me/sessions")
public List<SessionResponse> listSessions(Authentication authentication)

@DeleteMapping("/me/sessions/{familyId}")
public ResponseEntity<Void> revokeSession(Authentication authentication, @PathVariable UUID familyId)

@DeleteMapping("/me/sessions")
public ResponseEntity<Void> revokeAllSessions(Authentication authentication)
```
All three: `UUID.fromString(authentication.getName())` inline, matching `me()`/`changePassword()`'s existing style — no new private helper for a one-line extraction, consistent with T26's own precedent.

---

## Entities Used

`RefreshTokenFamily` (read + `revoke()` mutator, both pre-existing). No new entity.

## Repositories Used

`RefreshTokenFamilyRepository` (existing `findByPrincipalNameAndRevokedAtIsNull` for `list`/`revokeAll`'s fetch step; new `findByFamilyIdAndPrincipalName` for `revokeOne`).

## Services Used

`OAuth2AuthorizationService` (the single bean in the context, transparently the `ReuseDetectingAuthorizationService` decorator), `AuditService`, `Clock`.

## Tests Required

Per the frozen brief's Required Tests — Phase 10 will detail the full split, but noting here for planning purposes:
- Unit-level (Mockito): `SessionService.list/revokeOne/revokeAll` argument-wiring and the D2/D3 behaviors specifically — a null `findById` result doesn't throw; a `revokeAll` iteration exception doesn't stop subsequent iterations.
- Unit-level: `AccountController`'s three new methods (caller-derivation, status codes) mirroring `ApiKeyControllerTest`'s established style.
- Unit-level: `SessionExceptionHandler` — status/type/title/no-detail, mirroring `ApiKeyExceptionHandlerTest`.
- Integration-level (Testcontainers + real filter chain): the three named tests plus cross-account isolation, idempotent re-revoke, authorization-actually-removed verification (query `OAuth2AuthorizationService.findById` post-revoke), and a genuine bulk-partial-failure scenario if one is feasible to construct realistically.

## Execution Order

1. **`common/ProblemTypes.java`** — add `SESSION_NOT_FOUND` first; nothing downstream needs it yet but it's a zero-dependency addition.
2. **`token/RefreshTokenFamilyRepository.java`** — add `findByFamilyIdAndPrincipalName`.
3. **`token/dto/SessionResponse.java`** — no dependencies beyond `RefreshTokenFamily` (already exists).
4. **`token/SessionNotFoundException.java`** — no dependencies.
5. **`token/SessionExceptionHandler.java`** — depends on steps 1 and 4.
6. **`token/SessionService.java`** — depends on steps 2–4 (and the pre-existing `OAuth2AuthorizationService`/`AuditService`/`Clock` beans).
7. **`account/AccountController.java`** — depends on step 6.
8. **Tests**, in the order: `SessionExceptionHandlerTest` (after 5) → `SessionServiceTest` if a unit-level one is written, or fold its coverage into the controller/integration layers (Phase 10's call) → `AccountControllerTest` additions (after 7) → new integration test class (after 7, full stack).
9. **Full suite run** (Docker permitting; otherwise Docker-independent subset only, per this session's established constraint) before declaring Phase 6 complete.

---

## Traceability Check

Every file above appears in the frozen brief's Files to Create / Files to Modify lists exactly. No file outside that set is planned.

---

**Phase 5 complete — plan written.** Proceed to Phase 6 (Implementation) on approval.
