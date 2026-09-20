<!-- MODEL: Claude Sonnet — Phase 0 (Repository Understanding). -->

# auth · T40 · Phase 0 — Repository Understanding

Task statement: once `package.md` §11 questions are closed AND tests pass, bump `package.md`'s
status from `DRAFT` to `READY FOR IMPL` and version to `0.2`.

**This is the one task in the whole sequence authorized to edit a `spec/auth-service/` file** —
`package.md`'s own header (Status/Version fields) is this task's actual deliverable, not an
out-of-bounds file per the standing "never modify spec files" guardrail.

---

## 1. Architecture summary

Unchanged from every prior task — this task's own content is entirely about `package.md`'s header
and §11, not the service's runtime architecture.

## 2. Existing code this task touches

None directly — this task edits `spec/auth-service/package.md` only. But its two stated
*preconditions* ("§11 questions are closed", "tests pass") required checking real, current source
and test state, performed as this phase's own grounding work — not deferred.

**`package.md`'s current header**: `Status: DRAFT`, `Version: 0.1` (`package.md:1-9`) — the values
this task would change.

### Precondition 1 — "§11 questions are closed": **NOT currently true**

`package.md` §11 lists Q1-Q6. Checked each against actual current implementation state:

| Q | Topic | Status in `package.md` | Actual state, verified this phase |
|---|---|---|---|
| Q1 | TOTP seed encryption KMS approach | **Marked resolved** (2026-07-22, D-025) | Genuinely resolved and correctly marked. |
| Q2 | Per-account rate-limit thresholds | **Not marked resolved** (no strikethrough) | Actually resolved — `application.properties:104-106` (10/5/30 per minute), now recorded in `auth-decisions.md` D-026 (T39, this session). `package.md` itself was simply never updated to reflect this — the same staleness pattern found in `auth-decisions.md` before T39. |
| Q3 | API-key limits and scopes | **Not marked resolved** | Partially answered by implementation, never confirmed as deliberate: `ApiKeyProperties` (`prefix`, `tokenTtlMinutes`) has no max-active-key-count field — no limit is enforced; `ApiKeyService.DEFAULT_SCOPES = List.of("merchant.api")` — confirmed the only scope. Whether "no limit" was ever a conscious answer to Q3, versus simply never built, is genuinely unclear from source alone. |
| Q4 | Email link base URL | **Not marked resolved** | **Likely not `services/auth`'s question to close at all.** `EmailRequestedEventPayload` (the only artifact `services/auth` emits toward this) carries `accountUuid`, `purpose`, `token`, `occurredAt` — no URL or link field. Link construction (base URL + path) appears to happen entirely inside the Notification Service, a different service with its own spec package this task has no visibility into. |
| Q5 | Lockout event publication | **Not marked resolved** | **Confirmed genuinely unresolved, and reveals a real R43 gap.** `AccountService.lock(UUID)` (`AccountService.java:316-321`, called by `LockoutService` after 5 failed attempts) never calls `AuditService.record(...)` — no audit row, no `auth.security.audit` mirror, for the automatic lock event. `AccountService.unlock(UUID)` (`AccountService.java:325-330`, the automatic/reset path) is likewise unaudited. Only the **separate**, admin-initiated unlock method calls `recordAudit("account.unlocked", ...)` (`AccountService.java:352`). R43 explicitly names "lock, unlock" among audited security-relevant actions — the automatic lock/unlock path (the common case) currently does not satisfy this. |
| Q6 | Agents/standing-rules file | **Marked resolved** (2026-07-20) | Genuinely resolved and correctly marked; its own noted "open follow-up" (repo-root `agents.md` dedup) is a separate, smaller, explicitly-non-blocking item. |

**Net: 2 of 6 questions (Q1, Q6) are genuinely resolved and correctly marked. Q2 is resolved but not
marked. Q3/Q4 have no clear resolution recorded anywhere. Q5 is genuinely unresolved and its
investigation surfaced a real, previously-undiscovered audit-coverage gap against R43.**

### Precondition 2 — "tests pass": **NOT currently true**

`mvn -pl services/auth verify` does not currently exit zero. Per T37/T38's own findings this session
(re-confirmed, not assumed stale): 1 failure + 6 errors remain, split into two already-diagnosed,
already-logged groups — Group A (Kafka producer→broker environment connectivity, no known
code-level fix) and Group B (null-response flakiness under full-suite load, no confirmed root
cause). Both were explicitly deferred at prior human gates (T36/T37), not fixed.

## 3. Established patterns to follow

- **`AccountService.lock`/`AccountService.unlock`'s missing audit calls** would, if fixed, follow the
  exact same pattern already used by the admin-unlock method and by `AccountService.suspend/reinstate/delete`
  (D-022's own precedent: `recordAudit(eventType, accountUuid, actorUuid)`) — no new mechanism needed,
  just two missing call sites. **Whether fixing this is in T40's own scope is a genuine Phase 1/4
  question, not decided here** — T40's task statement is "bump status," not "fix R43 gaps," but the
  gap directly bears on whether "§11 questions are closed" can honestly be asserted for Q5.

## 4. Testing conventions

Unchanged. Not directly relevant — this task's own deliverable has no test surface (a `package.md`
header edit), but its *preconditions* are test-outcome-dependent, as established above.

## 5. Known gaps / unknowns

- **Both of T40's own stated preconditions are currently false.** This is the central finding of
  this phase, not a minor caveat: §11 has 3-4 genuinely open/unclear questions (Q2 fixable by a
  package.md edit alone; Q3 ambiguous; Q4 likely out of this service's own scope; Q5 genuinely open
  and revealing a real gap), and `mvn -pl services/auth verify` does not pass.
- **I do not know whether Q4 is answerable from within `spec/auth-service/` at all** — it may
  require checking `spec/notification-service/` (out of this task's stated file scope) or may simply
  remain permanently cross-service and un-closeable from here.
- **I do not know whether the automatic lock/unlock audit gap (Q5) is severe enough to block bumping
  spec status**, or whether it should be logged as a known, accepted gap (matching the pattern this
  whole session has used for Groups A/B) and the spec bumped anyway with an honest caveat. This is a
  genuine Phase 4 human-gate decision, not decided here.

---

**Phase 0 complete — repository understanding written.** Proceed to Phase 1 (Specification
Extraction) on approval.
