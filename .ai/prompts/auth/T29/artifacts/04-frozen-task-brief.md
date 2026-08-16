<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). -->

# auth · T29 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Consumes `artifacts/02-task-implementation-brief.md` and `artifacts/03-design-challenge.md` (Kimi,
8 findings). All 8 findings verified against actual source before disposition (per this pipeline's
standing verification discipline) — none were misreadings; all 8 held up. femi decided the three
findings with genuine design weight via human gate; the remaining five are mechanical amendments
folded in directly.

## Findings disposition

| # | Finding | Severity | Disposition |
|---|---|---|---|
| 1 | SAS invalidation and family revocation are not atomic | Medium | **Accepted as documented residual** (femi's gate decision) — same failure-direction philosophy as T28's own accepted residual: token unusable even if the row lags is the safe failure mode. |
| 2 | No-op-if-already-revoked must also suppress the audit call | Medium | **Accepted, folded in** — `revokeForAuthorization` checks `family.isRevoked()` before revoking AND before auditing. |
| 3 | `actorUuid` unspecified on the audit call | Low | **Accepted, folded in** — verified against `SessionService.recordAudit` (T28): both `accountUuid` and `actorUuid` are set to the same parsed account UUID. T29 follows the identical pattern. |
| 4 | `save()` vs. dirty-checking ambiguous | Low | **Accepted, folded in** — `revokeForAuthorization` calls `familyRepository.save(family)` explicitly, matching `SessionService.revokeOne`'s (T28) more recent explicit-save style over `revokeAllForPrincipal`'s older dirty-checking style. |
| 5 | Audit failure can roll back an already-correct revoke | Medium | **Accepted, folded in** (femi's gate decision) — verified `AuditService.record` is `@Transactional(REQUIRES_NEW)`, but an uncaught exception from it still propagates out of `revokeForAuthorization` and rolls back *that* method's own transaction, undoing the family revoke despite SAS's token invalidation having already committed separately. Fix: `revokeForAuthorization` wraps the audit call in try/catch, logs on failure, does not rethrow. |
| 6 | Missing test for non-UUID `principalName` audit fallback | Low | **Accepted, folded in** — added to Required Tests. |
| 7 | Integration test's client-auth plumbing is underspecified | Low | **Accepted, folded in** (femi's gate decision) — integration test scoped down to a Spring-context-level test calling `OAuth2AuthorizationService.save(...)` directly with an invalidated refresh token, not a full `/oauth2/revoke` HTTP round-trip (which would need SAS client credentials the brief never specified, and which Docker can't run anyway this session). |
| 8 | "Mirrors `revokeAllForPrincipal`" is imprecise wording | Low | **Accepted, folded in** — brief now says `revokeForAuthorization` *follows the same lookup/revoke pattern* as `revokeAllForPrincipal` but additionally owns the `session.revoked` audit responsibility, since it is the SAS-revocation-to-audit-log integration point. |

## Task

SAS revoke integration — ensure `ReuseDetectingAuthorizationService` revokes the associated
`RefreshTokenFamily` when SAS's `/oauth2/revoke` endpoint is called with a refresh token.

## Purpose

Unchanged from Phase 2: today, revoking a refresh token via RFC 7009's `/oauth2/revoke` only marks
the token invalidated inside SAS's own store — it never updates `refresh_token_family`, leaving
`GET /accounts/me/sessions` (T28) showing a stale "active" session and leaving reuse-detection state
out of sync with reality.

## Scope

**In:** the internal logic change inside `ReuseDetectingAuthorizationService.save(...)` and a new
`RefreshTokenTracker.revokeForAuthorization(...)` method.

**Out:** any new HTTP endpoint, DTO, or exception handler; changes to `RefreshTokenFamilyRepository`'s
query surface; changes to `findByToken`'s existing reuse-detection behavior; T30 (scheduled
cleanup), T31 (rate limiting), T33/T34 (contracts).

## Business Rules

- **R39.** A refresh-token invalidation via `/oauth2/revoke` SHALL also revoke the family.

## Locked Decisions

None — confirmed at Phase 0, 1, and 2; no `L`-numbered decision constrains this task.

## This Task's Own Design Decisions (D1-D3, decided at this gate)

- **D1 (Finding 1).** The SAS-side token invalidation and the family-row revocation are best-effort
  and not atomic with each other. Accepted failure direction: if the family-revoke step fails after
  SAS's own invalidation has already committed, the token is genuinely dead but the family row may
  still show as active until a retry or manual reconciliation — the safe-side failure, not the
  dangerous one (a row that looks revoked while its token still works). Documented as an accepted
  residual, no additional hardening in this task.
- **D2 (Finding 5).** `revokeForAuthorization`'s audit call (`AuditService.record(...)`, which runs
  in its own `REQUIRES_NEW` transaction) is wrapped in try/catch inside `revokeForAuthorization`. On
  failure, log the error and return normally — do NOT let the exception propagate and roll back the
  family revoke that already succeeded. This is the same safe-failure-direction philosophy as D1:
  the revoke having happened matters more than the audit row existing.
- **D3 (Finding 7).** The integration-level test for this task is a Spring-context-level test that
  calls the real `OAuth2AuthorizationService` bean's `save(...)` directly with a manually-invalidated
  refresh token (real JDBC-backed delegate, Testcontainers Postgres) and asserts the family row is
  revoked — NOT a full `POST /oauth2/revoke` HTTP round-trip. This proves the decorator's actual new
  logic without needing to also configure/authenticate a SAS client for the test, which the brief
  never specified and which isn't this task's concern to newly design.

## Dependencies

`OAuth2Authorization` / `OAuth2Authorization.Token` (`getRefreshToken()`, `isInvalidated()`),
`RefreshTokenTracker`, `RefreshTokenFamilyRepository.findByAuthorizationId` (existing), `Clock`
(existing), `AuditService` / `RecordAuditEventRequest` (existing).

## Inputs

The `OAuth2Authorization` instance passed into `ReuseDetectingAuthorizationService.save(...)` by
SAS's `OAuth2TokenRevocationAuthenticationProvider`, with its refresh token's invalidated metadata
flag set to `true`.

## Outputs

None (no HTTP response surface — SAS's own `/oauth2/revoke` response is untouched).

## State Changes

- `RefreshTokenFamily` matched by `authorization.getId()` gets `revoked_at`/`revoked_reason` set
  (reason: `"OAUTH2_REVOKE"`) via explicit `familyRepository.save(family)` (D-Finding-4) — only when
  `authorization.getRefreshToken() != null && authorization.getRefreshToken().isInvalidated()`, and
  only when the family is not already revoked (Finding 2 — the `isRevoked()` check gates BOTH the
  revoke and the audit call).
- One `session.revoked` audit row (event type reused from T28, per Phase 2's OQ3 resolution) with
  `accountUuid` AND `actorUuid` both set to the account UUID parsed from `principalName` (Finding 3;
  non-UUID principal → both `null`, same fallback as `auditReuseDetected`), wrapped in try/catch so
  an audit failure cannot roll back an already-successful revoke (D2).
- No change to `trackIssuance`/`trackRotation` — they continue to run unconditionally in
  `trackRefreshTokenIfPresent` on every `save()`, and their own same-hash-is-a-no-op guard already
  makes a revoke-triggered save harmless to that path (verified at Phase 2, re-confirmed here).
- No change to `findByToken`'s existing reuse-detection behavior (Kimi's "No Conflict" confirmation).

## Files to Create

None.

## Files to Modify

- `services/auth/src/main/java/com/themistra/auth/token/ReuseDetectingAuthorizationService.java`
- `services/auth/src/main/java/com/themistra/auth/token/RefreshTokenTracker.java`

## Files NOT to Modify

- `RefreshTokenFamily.java`, `RefreshTokenFamilyRepository.java` (both reused unchanged).
- Any controller, DTO, or exception handler (T28's files included).
- `SecurityChainsConfig.java` / SAS configuration.

## Acceptance Criteria

- **AC1 (R39).** A `/oauth2/revoke`-style save (refresh token invalidated) on an authorization with
  an active family revokes that family with reason `"OAUTH2_REVOKE"`.
- **AC2 (R39).** Re-processing an already-revoked family via this path is a no-op: no exception, no
  reason/timestamp overwrite, and — per Finding 2 — no second audit row.
- **AC3 (R39).** A save where only the access token is invalidated (refresh token untouched, or no
  refresh token at all) does NOT revoke the family and does not throw.
- **AC4 (non-regression).** Ordinary rotation saves are unaffected.
- **AC5 (non-regression).** Ordinary first-issuance saves are unaffected.
- **AC6 (non-regression).** `findByToken`'s reuse-detection path and its existing 8-test suite stay
  green, untouched.
- **AC7.** Exactly one `session.revoked` audit row per family revoked via this path, with
  `accountUuid` and `actorUuid` both equal to the parsed account UUID (Finding 3), or both `null` on
  a non-UUID principal (Finding 6).
- **AC8 (D2).** An audit failure during `revokeForAuthorization` does not undo the family revoke
  that already happened in the same call.

## Required Tests

Extend `ReuseDetectingAuthorizationServiceTest`:
1. Save with refresh token invalidated + active family → family revoked, reason `"OAUTH2_REVOKE"`,
   exactly one audit call with `accountUuid = actorUuid = <parsed UUID>`.
2. Save with refresh token invalidated + already-revoked family → no exception, no second audit
   call (Finding 2).
3. Save with only the access token invalidated → family untouched, no audit call.
4. Save with no refresh token on the authorization → no `NullPointerException`, no family
   interaction.
5. Ordinary rotation save (new refresh token, not invalidated) → `trackRotation` behavior unchanged.
6. Ordinary first-issuance save → `trackIssuance` behavior unchanged.
7. Save with refresh token invalidated + family whose `principalName` is not a UUID → revoke
   succeeds, audit row has `accountUuid = actorUuid = null`, no exception (Finding 6).
8. Audit call throws inside `revokeForAuthorization` → family is still revoked (verify via the
   family's own state, not just "no exception escapes") — proves D2.
9. Full existing 8-test suite stays green (regression).
10. Spring-context-level integration test (Testcontainers Postgres, Docker-permitting): call the
    real `OAuth2AuthorizationService` bean's `save(...)` directly with a manually-built,
    refresh-token-invalidated `OAuth2Authorization` against a seeded `RefreshTokenFamily`, then
    assert the family row's `revoked_at`/`revoked_reason` via `EntityManager` (D3 — replaces the
    original full-HTTP-round-trip proposal).

## Constraints

- **Transaction:** `revokeForAuthorization` is `@Transactional`; the audit call inside it is
  wrapped in try/catch per D2 so its own `REQUIRES_NEW` transaction's failure cannot roll back the
  outer one.
- **Null handling:** guard `authorization.getRefreshToken()` before calling `.isInvalidated()`.
- **Thread-safety:** no new shared mutable state.
- **Module boundaries (L12):** all changes stay within `token`.
- **Security:** `findByToken`'s existing reuse-detection logic is untouched.
- **Idempotency:** re-invoking against an already-revoked family is a safe no-op, including no
  duplicate audit (Finding 2).
- **Performance:** negligible — one additional conditional repository lookup only on the rare
  revoke-save path.

## Open Questions

No blockers. All Phase 1 OQs (1-4) and all 8 Phase 3 findings are resolved above.

---

**Phase 4 complete — brief FROZEN.** Proceed to Phase 5 (Implementation Plan).
