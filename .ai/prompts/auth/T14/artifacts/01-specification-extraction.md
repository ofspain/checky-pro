# auth · T14 — Phase 1: Specification Extraction

Extraction only — no design, no implementation. Scope: T14 ("Admin unlock endpoint") exactly as
stated in `tasks.md` item 14. Starting ID set is the header's (R20, L4), widened by one ID (R43)
since the task statement's own "and audit" clause is governed by it.

## Business Rules

- **R20.** WHEN an admin calls `POST /admin/accounts/{accountUuid}/unlock`, THEN the system SHALL
  transition the account to `ACTIVE` and clear the failed-attempt counter and `lock_count`.
  *(Already fully implemented by `LockoutService.resetLockout(UUID)`, T12 — confirmed at Phase 0.
  This task's job: expose it through an authenticated admin endpoint.)*
- **R43.** WHEN any security-relevant action occurs (explicitly listing "unlock" among its
  examples), THEN the system SHALL append an `auth_audit` row and mirror a reduced event to
  `auth.security.audit` via the outbox. *(Widened into scope: the task statement's "and audit"
  clause has no other governing rule; `AuditService.record(...)` — pre-existing, unchanged —
  already implements the mechanics. This task's job is the one call site.)*

## Locked Decisions

- **L4.** 5/30-min/15-min lockout, doubling via `lock_count`. Unchanged — this task adds no
  lockout arithmetic; `resetLockout` already exists and is frozen (T12).
- **L12.** Module boundaries, ArchUnit-enforced. Confirmed at Phase 0: `AccountService` has never
  depended on `authn` (`LockoutService`); the established direction is `authn → account` only.
  Whatever this task's design turns out to be, it must not introduce a reverse dependency
  (`AccountService` importing/injecting `LockoutService`) — that would be this codebase's first
  module-dependency cycle. Directly constrains where the audit call and the `resetLockout` call
  can live.

## Files involved

**Existing, to read/extend:**
- `services/auth/src/main/java/com/themistra/auth/account/AdminAccountController.java` — the file
  this task adds one method to, following the exact shape of its existing `suspend`/`reinstate`
  methods (confirmed at Phase 0: per-method `@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")`,
  `Authentication authentication` resolved via the file's own private `actorUuid(...)` helper).
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutService.java` — `resetLockout(UUID)`
  (T12), read-only dependency for this task, not modified unless Phase 2/3 design determines audit
  needs to live inside it (an open question below).
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` — `getByUuid(UUID)`
  (existing), likely needed to return a fresh `AccountResponse` after the unlock, matching every
  sibling admin endpoint's response shape. Possibly also needs a new audit-emitting method — open
  question below.
- `services/auth/src/main/java/com/themistra/auth/audit/AuditService.java` — `record(
  RecordAuditEventRequest)` (pre-existing, unchanged), the only sanctioned audit path.

**New:** none expected — this task's scope (per Phase 0's grounding) is wiring existing pieces
together behind one new endpoint method, not new classes.

## Dependencies

- `LockoutService.resetLockout(UUID accountUuid)` — T12, unchanged, returns
  `LockoutStateMachine.LockoutDecision`.
- `AccountService.getByUuid(UUID)` — pre-existing, returns `AccountResponse`.
- `AuditService.record(RecordAuditEventRequest)` — pre-existing.
- No new config keys — `LockoutProperties` (T12) already covers everything this task depends on.

## Acceptance Criteria

- **AC1 (→ R20).** `POST /admin/accounts/{accountUuid}/unlock` by a caller with `ADMIN` or
  `COMPLIANCE` role transitions a `LOCKED` account to `ACTIVE` and zeroes `lockout_state`'s
  `failed_attempts`/`lock_count`/`locked_until`.
- **AC2 (→ R20).** The same call against an account that is not currently `LOCKED` (e.g. already
  `ACTIVE`) is a safe no-op on `Account.status` (matching `AccountService.unlock`'s existing
  guard, T13) — does not throw, does not corrupt state.
- **AC3 (→ R43).** The call records an `auth_audit` row (and outbox mirror) with an
  `account.unlocked`-shaped event, `accountUuid` = the target account, `actorUuid` = the
  authenticated admin/compliance caller (not the target account — distinguishing this from every
  self-service audit call site, which uses the same UUID for both).
- **AC4 (→ security).** A caller without `ADMIN` or `COMPLIANCE` role is rejected before the
  handler body runs (`@PreAuthorize`, matching every sibling endpoint's convention).
- **AC5 (→ L12).** No new dependency from `AccountService`/`account` package onto `LockoutService`/
  `authn` package — whatever orchestrates the two calls (controller vs. a new method) must respect
  the established one-way `authn → account` direction.

## Tests required

- `shouldUnlockAccountViaAdminEndpoint` (named, `package.md` §8 — maps to R17 there, the same
  pre-existing numbering drift confirmed at every prior task in this chain; the real match is R20,
  confirmed at Phase 0 via the task's own header and `requirements.md`'s exact text).
- Boundary: unlock on an already-`ACTIVE` account is a safe no-op (AC2).
- Boundary: unlock on an account with no `lockout_state` row (never failed) is a safe no-op
  (matches `resetLockout`'s existing T12 behavior for a missing row).
- Authorization: a caller with neither `ADMIN` nor `COMPLIANCE` is rejected (AC4).
- Audit: the recorded event's `actorUuid` is the caller, not the target account (AC3) — the one
  assertion that most needs its own dedicated test, since it's the one place this task's shape
  differs from every existing self-service audit call site in the codebase.

## Open Questions

- **Q1 (blocker for design).** Where does the audit call live? Confirmed at Phase 0: neither
  `LockoutService.resetLockout` nor `AccountService.unlock` currently audits, and adding it inside
  either would incorrectly fire for non-admin callers too (`LockoutService`'s own login-flow-driven
  unlock path, T13). Candidate resolutions, none decided here: (a) the controller calls
  `AuditService.record(...)` directly (no precedent — every existing admin controller method
  delegates audit entirely to its `AccountService` call); (b) a new `AccountService` method wraps
  the `resetLockout` result and audits, but `AccountService` calling into `LockoutService` would
  invert the established module-dependency direction (Q1/AC5's L12 concern); (c) the controller
  calls both `LockoutService.resetLockout(...)` and a new, narrowly-scoped `AccountService` method
  that only audits (no `LockoutService` dependency added to `AccountService` itself). A genuine
  design decision for Phase 2/3, not resolvable from the spec text alone.
- **Q2.** Does this task need a full Testcontainers integration test, or is a unit-level proof
  (mocked `LockoutService`/`AccountService`/`AuditService` at the controller layer, matching
  `AdminAccountControllerTest`'s existing convention) sufficient given the task's small surface?
  Not a blocker — resolvable as an implementer/Phase 5 scoping decision.
