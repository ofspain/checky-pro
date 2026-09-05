# auth · T14 — Phase 8: Independent Code Review

Reviewed Phase 6 implementation (`AccountService.adminUnlock`, `AdminAccountController.unlock`) and the Phase 7 self-review with fresh, adversarial eyes. The implementation matches the frozen brief (`04-frozen-task-brief.md`) and `agents.md` on every explicit, locked decision (L12 module boundary, role check, self-unlock, no controller-level transaction, no session revocation). The issues below concern semantic correctness and documentation that the frozen brief either left open or did not anticipate.

---

## Finding 1 — Stale Javadoc on `LockoutService.resetLockout` now contradicts production usage

- **Issue:** The implementation deliberately places `LockoutService.resetLockout(accountUuid)` in `AdminAccountController.unlock` (T14's requested entry point), yet the method's Javadoc still claims it is "for T14's future use; not called by anything in this task." This drift is confusing for future maintainers and undermines the Phase 6/7 claim that the module-boundary precedent was updated to reflect real usage.
- **Evidence:** `LockoutService.java:98-101` states "Unconditionally clears any lockout state for the account (R20 admin unlock, and any future password-reset-driven unlock) — for T14's future use; not called by anything in this task." `AdminAccountController.java:76` calls `lockoutService.resetLockout(accountUuid)` as the first statement in the T14 endpoint.
- **Recommendation:** Update the Javadoc to state that `resetLockout` is called by `AdminAccountController.unlock` for the R20 admin-unlock path, and that it is also intended for future password-reset-driven unlock. No code change needed.
- **Confidence:** High — pure documentation drift.

---

## Finding 2 — `user.unlocked`/`account.unlocked` events assert an unlock even when `Account.status` was never `LOCKED`

- **Issue:** The frozen brief (AC2/AC3/AC4/AC7) explicitly required unconditional audit/lifecycle recording on every successful call, including the no-op branch. The self-review correctly flagged the mismatch between that framing and the guard actually being "status != LOCKED" rather than "status == ACTIVE." Independently confirmed: calling the admin-unlock endpoint on an account that is `SUSPENDED`, `DELETED`, or `PENDING_VERIFICATION` emits an event whose name claims the account is now unlocked/usable, even though `Account.status` remains unchanged. The `UserLifecycleEventPayload` additionally carries the current status (e.g., `SUSPENDED`), making the contradiction observable in the event body itself.
- **Evidence:**
  - `AccountService.java:325-329` — `unlock(UUID)` is a no-op for any status other than `LOCKED`.
  - `AccountService.java:343-349` — `adminUnlock` calls `publishLifecycleEvent(account, "user.unlocked")` and `recordAudit("account.unlocked", ...)` without inspecting the pre-call status.
  - `UserLifecycleEventPayload.java:12-16` includes `AccountStatus status`, so a `user.unlocked` event can be published with `status: SUSPENDED` or `status: DELETED`.
- **Recommendation:** Phase 9 should either (a) tighten the frozen brief to state that the endpoint is only guaranteed to be semantically meaningful for `ACTIVE` and `LOCKED` accounts, and that calls against `SUSPENDED`/`DELETED`/`PENDING_VERIFICATION` are logged attempts; or (b) add a before/after status snapshot to `details` so the audit trail can distinguish real transitions from attempts; or (c) condition the lifecycle event on an actual `LOCKED → ACTIVE` transition, accepting that this reopens AC4/AC7 and requires frozen-brief amendment.
- **Confidence:** Medium — behavior is exactly what the frozen brief mandated, but the event semantics are materially misleading and were not explicitly considered for non-`ACTIVE` non-`LOCKED` statuses during the Phase 3/4 decisions.

---

## Finding 3 — Two-transaction split widens the race window for concurrent admin lifecycle operations

- **Issue:** The frozen brief accepted the lack of a controller-level transaction around `resetLockout` + `adminUnlock` as a low-probability crash/DB-partition risk (Finding 2). A separate, adversarial timing concern is that because `Account.status` transitions in transaction 1 (`resetLockout`) while the lifecycle event and audit append in transaction 2 (`adminUnlock`), a concurrent `suspend` or `delete` can commit between the two transactions. The result is a `user.unlocked`/`account.unlocked` pair (and possibly an `ACTIVE`-status payload) emitted for an account that the database now shows as `SUSPENDED` or `DELETED`. This is not fully covered by the accepted "no audit row after a crash" risk; it is an interleaving risk even when both transactions commit normally.
- **Evidence:**
  - `LockoutService.java:107-116` loads `lockout_state` with `FOR UPDATE` but never locks the `accounts` row.
  - `AccountService.java:325-329` and `AccountService.java:275-281` load the account without `FOR UPDATE` and rely on JPA dirty-check at commit.
  - `Account.java` has no `@Version` column, so there is no optimistic-locking guard against two concurrent status writes.
  - `AdminAccountController.java:75-78` sequences `resetLockout` then `adminUnlock`, with no shared lock spanning both calls.
- **Recommendation:** Phase 9 should evaluate whether to (a) move lifecycle-event publication into the same `@Transactional` method that performs the `Account.status` transition so the event and state change cannot be separated by a concurrent mutation; or (b) accept and document the interleaving risk explicitly in the frozen brief's Constraints section; or (c) introduce row-level locking/optimistic locking on `accounts` for all admin lifecycle transitions — note that this would be a cross-cutting change affecting `suspend`/`reinstate`/`delete`/`activateEmail` too, not just T14.
- **Confidence:** Medium — the race requires two privileged admin operations to coincide, but the outcome (unlocked event for a suspended/deleted account) is a real observability/correctness fault, and the codebase's existing transition methods share the same no-locking pattern.

---

## Non-findings (verified independently)

- **Module boundary (L12):** `AccountService.java` has zero imports from `com.themistra.auth.authn`; only `AdminAccountController` gains the `LockoutService` dependency, matching `AccountUserDetailsService`'s established precedent.
- **Role check (R20):** `@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")` on `AdminAccountController.unlock` matches the frozen brief and sibling admin methods exactly.
- **Atomicity of side effects within `adminUnlock`:** `publishLifecycleEvent` + `recordAudit` run inside `adminUnlock`'s own `@Transactional` boundary and therefore share a single transaction with the second `unlock(accountUuid)` call.
- **No account-existence leak:** a missing `accountUuid` surfaces via `AccountNotFoundException` (404), consistent with `suspend`/`reinstate`/`delete`; no distinguishing information beyond existence is returned.
- **Self-unlock / COMPLIANCE-unlocking-ADMIN:** both match the frozen brief's explicit dispositions (Findings 3 and 9) and the existing precedent set by `suspend`/`reinstate`.

## Specification references

- Frozen brief: `04-frozen-task-brief.md` — AC1-AC7, Findings 1/2/6/7 dispositions.
- `agents.md`: module boundaries, outbox-in-same-transaction rule, audit append-only discipline.
- `LockoutService.java`, `AdminAccountController.java`, `AccountService.java` (Phase 6 source).
