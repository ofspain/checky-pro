<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# auth · T40 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T40 — Bump spec status to READY FOR IMPL / version 0.2 |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

**Task statement (verbatim from `spec/auth-service/tasks.md`, task 40):**
> **Bump spec status.** Once §11 questions are closed and tests pass, change this spec from `DRAFT` to `READY FOR IMPL` and version to `0.2`.

Below are adversarial findings on the Phase 2 TIB. Each finding is presented as **Issue · Severity · Evidence · Recommended brief amendment**. No redesign or implementation is proposed.

---

## Finding 1 — Q5 is a real R43 defect, not merely an unresolved design question

**Issue.** The brief frames Q5 as an open question about whether lock/unlock should be published as audit/lifecycle events. In reality, the code already publishes `account.unlocked` and an audit event for *admin* unlock, but the automatic lock/unlock path (`AccountService.lock`/`unlock` called by `LockoutService`) emits neither. This is a concrete violation of R43, which explicitly lists "lock, unlock" as security-relevant actions that must append an audit row and mirror an event.

**Severity.** High — this is a genuine defect that the spec cannot honestly call "closed" without either a fix or an explicit exception.

**Evidence.**
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` lines 316-329: `lock(UUID)` and `unlock(UUID)` call `account.lock()`/`account.unlock()` but do not call `recordAudit`.
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` lines 346-352: `adminUnlock(UUID, UUID)` *does* call `recordAudit("account.unlocked", ...)` and `publishLifecycleEvent(..., "user.unlocked")`.
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutService.java` lines 147-155: `applyStatusChange` calls `accountService.lock(accountUuid)` / `accountService.unlock(accountUuid)` for automatic lock/unlock.
- `spec/auth-service/requirements.md` (R43): "every security-relevant action (login success/failure, lock/unlock, MFA events, password/key changes, token reuse, API-key operations) appends an `auth_audit` row and mirrors a reduced event to `auth.security.audit`."

**Recommended brief amendment.** Update the Q5 assessment in AC1 and Open Questions to:

> "Q5 is a genuine R43 gap: automatic lock/unlock via `LockoutService` does not currently audit or emit lifecycle events, while admin unlock does. Either (a) fix `AccountService.lock`/`unlock` to call `recordAudit`/`publishLifecycleEvent` within T40's scope if judged cheap, or (b) explicitly record Q5 as an accepted open gap with a follow-up task, before bumping status."

---

## Finding 2 — Q3's "maximum active API keys" limit is not implemented

**Issue.** Q3 asks two things: (a) maximum number of active API keys per merchant, and (b) whether additional scopes beyond `merchant.api` are needed. The code shows `ApiKeyService.DEFAULT_SCOPES = List.of("merchant.api")` and no enforcement of a maximum. The brief should not mark Q3 "resolved" without explicitly stating that no maximum was chosen.

**Severity.** Medium — partial resolution can be misrepresented as full resolution.

**Evidence.**
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyService.java` line 38: `DEFAULT_SCOPES = List.of("merchant.api")`.
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyService.java` lines 82-99: `create` method has no max-active-keys check.
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyProperties.java`: no max-active-keys property.

**Recommended brief amendment.** For Q3, explicitly record:

> "Scope vocabulary: only `merchant.api` at launch (`ApiKeyService.DEFAULT_SCOPES`). Maximum active keys per merchant: no limit implemented; deferred as a future guard if operational need arises."

---

## Finding 3 — Q4's boundary with the Notification Service is unclear

**Issue.** Q4 asks whether the email link base URL should come from `SPA_REDIRECT_URI`/`AUTH_ISSUER_URI` or a new Notification Service secret. The brief notes Q4 may be out of auth-service scope, but it does not verify whether `EmailRequestedEventPayload` carries enough information for the Notification Service to construct links without auth-service changes.

**Severity.** Medium — if the payload is insufficient, auth-service may need a change before the spec is "ready."

**Evidence.**
- `services/auth/src/main/java/com/themistra/auth/account/event/EmailRequestedEventPayload.java`: carries `accountUuid`, `purpose`, `token`, `occurredAt` — no base URL or link template.
- `spec/auth-service/design.md` §4b O4: asks about the base URL source.

**Recommended brief amendment.** Inspect the Notification Service spec/contracts (if available) or add a note:

> "Q4 deferred/out-of-scope: `EmailRequestedEventPayload` provides the raw token and purpose; link construction is the Notification Service's responsibility. If that service requires a base URL from auth, it is a cross-service contract question outside this spec's scope."

---

## Finding 4 — AC2's test-suite precondition is not literally satisfied

**Issue.** The task statement says the status bump is conditional on "tests pass." The brief correctly notes that `mvn verify` is not literally passing due to Groups A/B. The T37 precedent accepted an AC1a/AC1b framing, but T40 is the final spec-status gate. Bumping to `READY FOR IMPL` while the suite is red is a significant judgment call that should be explicitly documented.

**Severity.** High — this is the core precondition of the task.

**Evidence.**
- TIB §74: "AC2 — the test-suite precondition is addressed explicitly: either genuinely passing, or an explicit, human-approved acceptance of the already-logged Groups A/B as a named exception."
- T37 Phase 10 manifest: full suite post-fix had 1 failure, 6 errors (Groups A/B).

**Recommended brief amendment.** Make the AC2 decision criteria explicit for the Phase 4 gate:

> "AC2 will be considered satisfied if either (a) `mvn -pl services/auth verify` passes with zero failures/errors, or (b) the Phase 4 human gate explicitly waives Groups A/B as environmental/out-of-code-scope exceptions, documented in `package.md` §11 or the status-bump commit message."

---

## Finding 5 — The R43 gap may be cheap enough to fix within T40

**Issue.** The brief scopes T40 as primarily a status bump and explicitly leaves the R43 gap as a separate scope question. However, adding `recordAudit` and `publishLifecycleEvent` calls to `AccountService.lock`/`unlock` is a small, low-risk change that would honestly close Q5 and remove a known defect before declaring the spec ready.

**Severity.** Medium — scope expansion should not be assumed, but the cost/benefit should be presented.

**Evidence.**
- `AccountService.java` lines 316-329: `lock`/`unlock` already have the account loaded and could call the same `recordAudit`/`publishLifecycleEvent` helpers used by `adminUnlock`.
- `AccountService.java` lines 388-403: existing `recordAudit` and `publishLifecycleEvent` helpers.

**Recommended brief amendment.** Add to Open Questions:

> "Scope option: fix the R43 lock/unlock audit gap in T40 if judged cheap. The change is two additional `recordAudit`/`publishLifecycleEvent` calls in `AccountService.lock`/`unlock`. If deferred, Q5 must be recorded as an accepted open gap."

---

## Finding 6 — Q2 is correctly resolved but the citation should be precise

**Issue.** AC1 says Q2 should be corrected to resolved, citing D-026. The brief should ensure the §11 update references the exact decision log entry and the concrete values.

**Severity.** Low.

**Evidence.**
- `auth-decisions.md` D-026: records `login-per-minute=10`, `password-reset-per-minute=5`, `oauth-token-per-minute=30` (refresh-token grant only), MFA folded into login bucket.
- `application.properties` lines 104-106 confirms the values.

**Recommended brief amendment.** Draft the Q2 §11 update as:

> "**Resolved:** thresholds are 10 login attempts/minute (covers MFA verify via the same `/login` request), 5 password-reset confirmations/minute, and 30 refresh-token `/oauth2/token` requests/minute. See `auth-decisions.md` D-026."

---

## Finding 7 — The status bump with known gaps should not set an unreviewed precedent

**Issue.** If T40 bumps the spec to `READY FOR IMPL` while Q5 (R43 gap) and Groups A/B remain open, future specs may infer that incomplete suites and known defects can be waved through without explicit documentation. The brief must ensure every gap is named, accepted, and assigned a follow-up.

**Severity.** Medium — process risk.

**Evidence.**
- TIB §84-86: "Honesty over completeness" constraint.
- T39 precedent: D-027 records an unresolved item explicitly rather than fabricating closure.

**Recommended brief amendment.** Require that `package.md` §11, the status-bump commit message, and the final task artifact all list the same set of accepted open items (e.g., Groups A/B, Q5 if deferred, Q3 max-keys if deferred, Q4 if out-of-scope). Do not allow the status bump to occur with gaps only recorded in one place.

---

## Finding 8 — Q6's repo-root agents.md follow-up is still open

**Issue.** Q6 is marked resolved for the per-service `agents.md`, but the "open follow-up" about a repo-root `agents.md` is still pending. This is not blocking for auth-service readiness, but it should be tracked somewhere so it is not lost.

**Severity.** Low.

**Evidence.** `package.md` line 153: "Open follow-up: whether to also seed a single repo-root `agents.md` ..."

**Recommended brief amendment.** In §11, update Q6 to:

> "**Resolved (per-service):** `spec/auth-service/agents.md` exists and is authoritative. **Open follow-up (non-blocking):** consider a single repo-root `agents.md` for platform-common rules; tracked outside this spec."

---

## Summary

T40 is the final gate and must not approve an inaccurate "all clear." The highest-priority issues are:
1. Q5 is a real R43 defect, not just an open question (Finding 1).
2. AC2's test-suite precondition is not literally met and needs explicit waiver criteria (Finding 4).
3. Q3 is only partially resolved (Finding 2).

The Phase 4 gate should decide, for each open item, whether to fix cheaply (Q5 especially), accept explicitly, or defer — and only then bump the status.

(End of Phase 3 design challenge.)
