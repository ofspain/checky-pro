# auth · T14 · Phase 3 — Design Challenge

This is an adversarial review of `.ai/prompts/auth/T14/artifacts/02-task-implementation-brief.md` against `spec/auth-service/agents.md`, `spec/auth-service/requirements.md`, `spec/auth-service/design.md`, and the existing `services/auth` production code. No redesign or implementation is proposed; findings only.

## Findings

### 1. Admin unlock omits a lifecycle event without explicitly locking that decision

- **Issue:** The brief records an `account.unlocked` audit event but deliberately does **not** emit an `auth.user.lifecycle` event. Every other admin-driven account transition in the same file (`activate`, `suspend`, `reinstate`, `delete`) publishes both audit and lifecycle events. R20 is a material state change (`LOCKED → ACTIVE`) that downstream consumers could reasonably subscribe to, yet they will not see it.
- **Severity:** Medium
- **Evidence:** The brief's `AccountService.adminUnlock` description only calls `recordAudit`; `publishLifecycleEvent` is absent. `design.md` §5 lists `Account` as the owner of `status`. `package.md` §8 named-test list implies every meaningful transition is observable.
- **Recommended brief amendment:** Explicitly state whether the silence on `auth.user.lifecycle` is a **locked product decision** (because self-service login-driven unlocks must stay silent and admin unlock is piggybacking the same muted semantics) or an oversight. If it is locked, add it to the "Locked Decisions" section and add a named test asserting that no `auth.user.lifecycle` event is published for admin unlock. If it is not locked, require `publishLifecycleEvent` in `adminUnlock` with a distinct event type (e.g., `user.unlocked`).

### 2. Controller orchestration is not atomic across `resetLockout` and `adminUnlock`

- **Issue:** `AdminAccountController.unlock` calls `LockoutService.resetLockout(accountUuid)` and then `AccountService.adminUnlock(...)`. Each service method carries its own `@Transactional` boundary; the controller is not transactional. If `adminUnlock` fails after `resetLockout` commits, the account is already unlocked and `lockout_state` is cleared, but no audit row is written and no `AccountResponse` is returned. Conversely, a process crash between the two calls leaves the same inconsistency. This violates the natural interpretation of AC1+AC3 as a single, observable operation.
- **Severity:** Medium
- **Evidence:** Existing controller methods delegate to a single service call. The brief introduces the first two-service orchestration in this controller without a surrounding transaction. `LockoutService.resetLockout` and `AccountService.adminUnlock` are both annotated `@Transactional` individually.
- **Recommended brief amendment:** Either (a) declare the acceptable eventual-consistency model and, if audit can be lost, specify a compensating requirement (e.g., a background reconciler or a failure alarm); or (b) require a single transactional boundary, such as a controller-level `@Transactional` or a new facade method owned by one module, so that lockout reset and audit are committed together. If option (b) is chosen, L12 must be revisited and documented.

### 3. Self-unlock by an admin/compliance caller is not prohibited

- **Issue:** The brief mentions that `actorUuid` can equal the target account UUID and is "not specially handled or forbidden." An administrator unlocking their own account is a classic conflict-of-interest / privilege-escalation risk, especially when the account was locked due to suspected credential compromise.
- **Severity:** Medium
- **Evidence:** Brief AC3 explicitly acknowledges the edge case and declines to handle it. `agents.md` requires security-relevant actions to be audited but does not prohibit self-targeted admin actions.
- **Recommended brief amendment:** Decide and lock the behavior: (a) forbid self-unlock and return `403 Forbidden` (or a problem detail), requiring another admin to perform the action; or (b) accept the risk and add it to Locked Decisions with a named test proving the behavior. If (a) is chosen, the brief must update AC4 and the controller logic to compare caller and target UUIDs.

### 4. Audit outbox mirror contract is assumed but not verified

- **Issue:** The brief states the outcome includes "New `auth_audit` row + outbox mirror: `eventType = 'account.unlocked'`" and relies on the existing private `recordAudit` helper. It is not verified in the brief that `AuditService.record(...)` emits the outbox mirror, what aggregate/topic it uses, or whether the mirror payload conforms to `contracts/events/auth/security-audit.v1.schema.json`.
- **Severity:** Low–Medium
- **Evidence:** `AccountService.recordAudit` passes only `eventType`, `AuditOutcome.SUCCESS`, `accountUuid`, and `actorUuid`; it passes `null` for correlation id, IP, user agent, and reason. The brief references `security-audit.v1.schema.json` but does not map fields. `agents.md` requires `auth_audit` to be append-only and mirrored to Kafka.
- **Recommended brief amendment:** Add a section mapping the new audit event to the contract schema fields, and require a unit or contract test asserting the outbox mirror is produced with aggregate type `"audit"` (routed to `auth.security.audit` per `design.md` §4c) and the correct payload shape. If the schema requires fields the brief omits (e.g., correlation id), note the gap.

### 5. `contracts/api/auth.yaml` gap is acknowledged but left unresolved

- **Issue:** The brief explicitly excludes `contracts/api/auth.yaml` authorship as out of scope. However, `package.md` §8 requires the named test `shouldConformToAuthOpenApiContract`, and §9's verification checklist states that `contracts/api/auth.yaml` must cover every new non-SAS endpoint.
- **Severity:** Low
- **Evidence:** Brief "Scope > Out" and "Files NOT to Modify" exclude the contract. T11–T13 are cited as sharing the same gap. This task adds a new admin endpoint, widening the contract deficit by one more route.
- **Recommended brief amendment:** Keep the exclusion if that is the project's current convention, but add a concrete follow-up task or ticket reference in the frozen brief so Phase 12 specification verification does not silently fail. Do not allow an endless "same already-logged gap" reference without a traceable artifact.

### 6. The term "idempotent" is misleading given non-idempotent audit rows

- **Issue:** The brief's Constraints section calls the endpoint "idempotent" but immediately clarifies that a second call still appends a second audit row. From an HTTP/REST semantics and observability standpoint, repeating the request creates new state (a new audit entry), so "idempotent" applies only to the account status transition, not the operation as a whole.
- **Severity:** Low
- **Evidence:** Brief Constraints: "Idempotency: calling the endpoint twice in a row is safe — the second call is a no-op on `Account.status` but still records a second audit event." This directly contradicts the usual definition of idempotency for side effects.
- **Recommended brief amendment:** Replace "idempotency" with "status-transition idempotency" or "safe to retry," and explicitly state that every successful invocation appends a distinct audit row. Add a named test for the duplicate-audit behavior to prevent future regressions where someone "optimizes away" the second audit.

### 7. Response gives no indication that a state change occurred

- **Issue:** AC2 requires a non-`LOCKED` account to return a safe no-op with the same `AccountResponse` shape. The brief does not say whether the response should distinguish an actual unlock from a no-op. An admin operator may be unable to tell whether their action had effect.
- **Severity:** Low
- **Evidence:** AC1 and AC2 both describe behavior but the Outputs section only says "`AccountResponse` — same shape every sibling method returns." No boolean, status code, or metadata differentiates changed-vs-unchanged.
- **Recommended brief amendment:** Either explicitly confirm that the uniform response is deliberate (matching the no-op philosophy of `LockoutService.resetLockout`) or require a response field/body that indicates whether a transition occurred. If deliberate, add a named test proving the response is identical for both cases.

### 8. Active sessions / refresh-token families are not addressed after unlock

- **Issue:** R20 restores an account to `ACTIVE` and clears lockout counters. If the lockout was triggered by an attack, the attacker may still hold valid refresh tokens or active sessions; the brief does not state whether unlock should revoke them.
- **Severity:** Low
- **Evidence:** `refresh_token_family` and SAS authorizations exist in the schema. The brief mentions no revocation. Password reset (`AccountService.resetPassword`) explicitly revokes all refresh-token families, suggesting the codebase already has a precedent for post-security-event revocation.
- **Recommended brief amendment:** Explicitly state whether admin unlock (a) does nothing to sessions, (b) revokes all active sessions, or (c) optionally revokes them. If (a), document it as an accepted limitation. If (b) or (c), add the requirement and corresponding tests.

### 9. `COMPLIANCE` role can unlock `ADMIN` accounts without matching MFA strength

- **Issue:** `agents.md` L10 makes MFA mandatory for `ADMIN` and optional for `COMPLIANCE`. The endpoint grants unlock to either role. A `COMPLIANCE` user without MFA could unlock a locked `ADMIN` account, effectively bypassing the stronger MFA posture of the target account's role.
- **Severity:** Low
- **Evidence:** Brief `@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")`; `agents.md` L10 MFA role rules. The JWT will be valid for either role regardless of the target account's MFA status.
- **Recommended brief amendment:** Clarify whether `COMPLIANCE` callers of `/unlock` must themselves be MFA-enrolled, or whether the existing role check is sufficient. If MFA is required for this sensitive action, update AC4 and add a test verifying rejection when the caller lacks MFA.

## Open Questions (for the author / human approval gate)

1. Is the absence of a `user.unlocked` lifecycle event a locked product decision or an oversight?
2. Is the two-transaction orchestration in the controller acceptable, or should the brief require atomicity between lockout reset and audit?
3. Should self-unlock be forbidden, accepted, or deferred to a separate authorization policy task?
4. Does the security-audit contract require fields (correlation id, IP, user agent, reason) that the existing `recordAudit` helper does not populate for this event?
