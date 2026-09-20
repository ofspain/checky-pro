STATUS: FROZEN

# auth · T14 — Phase 4: Frozen Task Brief

Human-approved. Folds every Phase 3 (Kimi) finding into a final decision. Downstream phases may
not renegotiate anything below.

## Phase 3 findings — disposition

| # | Sev | Finding | Disposition |
|---|---|---|---|
| 1 | Medium | Admin unlock omits `auth.user.lifecycle` while every sibling admin action (`activate`/`suspend`/`reinstate`/`delete`) publishes both audit and lifecycle | **ACCEPTED, human-approved: emit it.** Verified: all four sibling methods call `publishLifecycleEvent(account, "user.<x>")` immediately after their mutation (`AccountService.java:262,278,287,296`). `adminUnlock` now does the same — `publishLifecycleEvent(account, "user.unlocked")` — closing the inconsistency rather than introducing a new exception to the established pattern. |
| 2 | Medium | Controller orchestrates two independently-`@Transactional` calls (`LockoutService.resetLockout`, `AccountService.adminUnlock`) with no wrapping transaction — a failure between them leaves the account unlocked with no audit row | **Human-approved: accept as a documented risk, no architectural change.** Both calls are simple, fast, same-database operations; the failure window requires a mid-request crash or DB partition between two adjacent calls, both against the same Postgres instance. Not fixed with a controller-level `@Transactional` or a new facade method — that would reopen L12's module-boundary question T12 already settled, disproportionate to the risk. Documented explicitly in Constraints below, not silently accepted. |
| 3 | Medium | Self-unlock by the acting admin/compliance caller is not prohibited | **Human-approved: allow it.** No other admin action in this controller (`suspend`, `reinstate`, `delete`) forbids self-targeting either — T14 doesn't introduce a new asymmetry by staying consistent. Fully audited with the real `actorUuid` either way, so a self-unlock remains visible after the fact. |
| 4 | Low-Med | Audit outbox mirror contract (aggregate/topic, field mapping) not verified in the brief | **ACCEPTED, verified and documented.** `AccountService.recordAudit`'s existing call (reused unchanged by `adminUnlock`) already resolves `EventTopics.forAggregateType("audit")` → `auth.security.audit` internally (confirmed at T12/T13 Phase 0 research, unchanged). `ip`/`rawUserAgent`/`traceId`/`details` are `null` for this event — **identical to every other admin-action audit call** (`activate`/`suspend`/`reinstate`/`delete` all pass the same nulls) — not a new gap this task introduces, just the established, uniform pattern applied consistently. |
| 5 | Low | `contracts/api/auth.yaml` gap acknowledged but not tracked | **ACCEPTED, tracked explicitly.** Same pre-existing gap as T11-T13 (confirmed, file doesn't exist anywhere in the repo). Phase 12 of this task will explicitly re-confirm and re-log this gap rather than silently reference "same as before" — see Constraints. Not authored here; out of scope for a task this size. |
| 6 | Low | "Idempotent" is the wrong word — a second call still appends a second audit row | **ACCEPTED, amended.** Replaced with "status-transition idempotent / safe to retry" throughout; explicitly stated that every successful invocation appends a distinct audit row regardless of whether `Account.status` actually changed. A dedicated named test now proves the duplicate-audit behavior (Required Tests). |
| 7 | Low | Response doesn't distinguish an actual unlock from a no-op | **ACCEPTED, confirmed as deliberate, not changed.** Uniform `AccountResponse` in both cases, matching `LockoutService.resetLockout`'s own no-op-not-throw philosophy (T12). Adding a distinguishing field/flag would be scope beyond R20's literal text — explicitly locked here as intentional, with a named test proving response-shape identity across both cases. |
| 8 | Low | Active sessions/refresh-token families not addressed after unlock | **Human-approved: leave untouched.** An account locked by L4 (5 failed login attempts) never had a successful attacker session to begin with — unlike `resetPassword`'s scenario (a credential-compromise recovery flow where an attacker may already hold a valid session). Not analogous enough to warrant the same revocation; explicitly out of scope. |
| 9 | Low | `COMPLIANCE` (MFA-optional, L10) can unlock `ADMIN` accounts (MFA-mandatory) | **CONFIRMED, not a new gap — pre-existing system posture.** Verified: `suspend`/`reinstate` already use the identical `@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")` and already carry the same property today. T14 extends an already-accepted pattern; changing it would mean revisiting `suspend`/`reinstate` too, well beyond this task's scope. Not decided here. |

All Phase 1 Open Questions are resolved: Q1 (audit call site) resolved at Phase 2, reinforced by
Finding 1's lifecycle-event addition; Q2 (integration test scope) resolved at Phase 2, unchanged.

---

## Task

Add `AdminAccountController.unlock(UUID, Authentication)` — `POST /admin/accounts/{accountUuid}/
unlock`, `ADMIN` or `COMPLIANCE` — plus `AccountService.adminUnlock(UUID, UUID)`, which clears
lockout state (via the existing `LockoutService.resetLockout`), audits, and publishes a lifecycle
event, orchestrated by the controller.

## Purpose

Exposes T12's `LockoutService.resetLockout` (built anticipating this exact task) through an
authenticated admin endpoint, closing the loop: R20's mutation already exists; this task adds the
entry point, the audit trail, and (per Finding 1) the lifecycle event every sibling admin action
already publishes.

## Scope

**In:**
- `AdminAccountController.unlock(UUID accountUuid, Authentication authentication)` — same shape
  as `suspend`/`reinstate`: `@PostMapping("/{accountUuid}/unlock")`,
  `@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")`, actor via the file's existing
  `actorUuid(...)` helper. Calls `lockoutService.resetLockout(accountUuid)` then
  `accountService.adminUnlock(accountUuid, actorUuid)`.
- `AccountService.adminUnlock(UUID accountUuid, UUID actorUuid)` (new) — reuses the existing
  `unlock(UUID)` guard (`LOCKED → ACTIVE` only if currently `LOCKED`), calls
  `publishLifecycleEvent(account, "user.unlocked")` (Finding 1) and `recordAudit(
  "account.unlocked", accountUuid, actorUuid)` unconditionally (even on the no-op branch — Finding
  6/7), returns `AccountResponse.from(account)`.
- `AdminAccountController`'s constructor gains a `LockoutService` dependency.

**Out:**
- Any transactional-boundary change spanning `resetLockout`/`adminUnlock` (Finding 2, accepted
  risk).
- Self-unlock prevention (Finding 3, allowed).
- Session/refresh-token revocation (Finding 8, out of scope).
- Any Testcontainers integration test (Phase 2 scoping, unchanged) — unit-level only, matching
  `AdminAccountControllerTest`'s existing convention.
- `contracts/api/auth.yaml` authorship (Finding 5, tracked not authored).
- Any change to `LockoutService.resetLockout`'s or `AccountService.unlock`'s existing behavior.
- Any change to `suspend`/`reinstate`'s role-check posture (Finding 9, out of scope).

## Business Rules

- R20 — admin unlock transitions `LOCKED → ACTIVE`, clears the counter and `lock_count`. Fully
  covered by `LockoutService.resetLockout` (T12, unchanged); this task exposes, audits, and now
  (Finding 1) publishes the lifecycle event for it.
- R43 — security-relevant actions are audited via the outbox. Covered by the existing
  `recordAudit` helper, unchanged mechanics, confirmed mapping (Finding 4).

## Locked Decisions

- L4 — unchanged.
- L10 — `COMPLIANCE`/`ADMIN` MFA asymmetry confirmed as pre-existing, unchanged system posture
  (Finding 9) — not revisited by this task.
- L12 — `AccountService` gains no dependency on `authn`/`LockoutService`. `adminUnlock` only
  touches existing `AccountService`-internal helpers (`recordAudit`, `publishLifecycleEvent`, the
  existing `unlock` guard). The controller (already depending on both packages' services) is the
  orchestration point — confirmed as the same shape `AccountUserDetailsService` already
  established (T12 Phase 12 correction).

## Dependencies

- `LockoutService.resetLockout(UUID accountUuid)` — T12, unchanged.
- `AccountService.adminUnlock(UUID accountUuid, UUID actorUuid)` — new, this task.
- Existing private `AccountService` helpers: `getAccount`, `recordAudit`, `publishLifecycleEvent`
  — reused, not exposed further.

## Inputs

- `accountUuid` — path variable, the target account.
- `authentication` — the authenticated admin/compliance caller; `actorUuid` via the controller's
  existing helper.

## Outputs

- `AccountResponse` — identical shape whether the account was actually `LOCKED` or already
  `ACTIVE` (Finding 7, confirmed deliberate).

## State Changes

- `lockout_state` zeroed (via `LockoutService.resetLockout`, unchanged).
- `accounts.status` transitions `LOCKED → ACTIVE` if currently `LOCKED` (guarded, reached
  twice — once inside `resetLockout`, once inside `adminUnlock` — both idempotent).
- New `auth_audit` row + outbox mirror: `eventType = "account.unlocked"`, target/actor UUIDs per
  Finding 4's confirmed mapping — recorded unconditionally, even on the no-op branch.
- New `auth.user.lifecycle` event (Finding 1): `eventType = "user.unlocked"`, published
  unconditionally alongside the audit call.

## Files to Create

None.

## Files to Modify

- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` — add
  `adminUnlock(UUID, UUID)`.
- `services/auth/src/main/java/com/themistra/auth/account/AdminAccountController.java` — add
  `unlock(UUID, Authentication)`; constructor gains `LockoutService`.

## Files NOT to Modify

- `LockoutService.java`, `LockoutStateMachine.java` (T11/T12, frozen).
- `Account.java`, `AccountStatus.java`.
- Every other `AdminAccountController` method — unchanged.
- Anything under `spec/`.

## Acceptance Criteria

- **AC1 (→ R20).** `POST /admin/accounts/{accountUuid}/unlock` on a `LOCKED` account by an
  `ADMIN`/`COMPLIANCE` caller transitions it to `ACTIVE` and zeroes `lockout_state`.
- **AC2 (→ R20).** Same call on a non-`LOCKED` account is a safe no-op on `Account.status` — no
  exception.
- **AC3 (→ R43).** Records `auth_audit`/outbox with `eventType = "account.unlocked"`, `accountUuid`
  = target, `actorUuid` = caller — recorded on **both** AC1's and AC2's branches (Finding 6/7).
- **AC4 (→ Finding 1).** Publishes `auth.user.lifecycle` with `eventType = "user.unlocked"` on
  both branches, matching AC3's unconditional-recording shape.
- **AC5 (→ security).** A caller without `ADMIN` or `COMPLIANCE` role is rejected by
  `@PreAuthorize` before the handler body runs.
- **AC6 (→ L12).** `AccountService.java` gains no import from `com.themistra.auth.authn`.
- **AC7 (→ Finding 6/7).** Calling the endpoint twice produces the identical `AccountResponse`
  both times, but two distinct audit rows and two distinct lifecycle events — proving
  "status-transition idempotent, not side-effect idempotent" is the correct characterization.

## Required Tests

- `shouldUnlockAccountViaAdminEndpoint` (named).
- Non-`LOCKED`-account no-op boundary, still audited/published (AC2/AC3/AC4).
- Missing-`lockout_state`-row boundary (matches `resetLockout`'s existing T12 no-op behavior).
- Authorization rejection for a caller with neither role (AC5).
- Audit shape: `actorUuid` is the caller, not the target (AC3).
- Lifecycle event shape: `eventType = "user.unlocked"` (AC4).
- Duplicate-call test: two invocations produce identical responses but two audit rows and two
  lifecycle events (AC7, Finding 6).

## Constraints

- **Security:** `@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")`, matching R20's header and
  this file's own precedent. Self-unlock explicitly allowed (Finding 3). `COMPLIANCE`/`ADMIN` MFA
  asymmetry explicitly confirmed as pre-existing, unchanged (Finding 9).
- **Consistency (retry semantics):** "status-transition idempotent," not side-effect idempotent —
  every successful call appends a new audit row and lifecycle event regardless of whether
  `Account.status` actually changed (Finding 6).
- **Atomicity (accepted risk, Finding 2):** `resetLockout` and `adminUnlock` are two independent
  `@Transactional` calls, not one. A failure between them (crash or DB partition mid-request)
  could leave the account unlocked/counters-cleared with no audit/lifecycle record. Accepted as
  low-probability given both are fast, same-database operations; not engineered around.
- **Module boundaries (L12):** as stated above — verified by `grep` in Phase 6/7, not just
  asserted.
- **Contract tracking (Finding 5):** `contracts/api/auth.yaml` still does not exist; Phase 12 of
  this task must explicitly re-confirm this gap rather than silently reference "same as T11-T13."
- **Null handling:** `accountUuid`/`actorUuid` never null at any new method boundary.

## Open Questions

No blockers. All four human-escalated decisions (lifecycle event, atomicity, self-unlock, session
revocation) resolved above by explicit human approval.
