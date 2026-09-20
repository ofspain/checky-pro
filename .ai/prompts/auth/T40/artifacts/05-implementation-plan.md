<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). -->

# auth · T40 · Phase 5 — Implementation Plan

Two changes: a small production fix (`AccountService.lock`/`unlock`) and a `spec/` documentation edit
(`package.md`).

## Files to create

None.

## Files to modify

- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java`
- `spec/auth-service/package.md`

## `AccountService.lock`/`unlock` — exact planned change

Current (`lines 315-329`): both methods guard the `Account`-side transition (`if status == ACTIVE`
for lock, `if status == LOCKED` for unlock) but never call `recordAudit`/`publishLifecycleEvent`.

**Planned**: mirror `adminUnlock`'s already-correct, already-human-approved pattern (Phase 9's own
prior fix, per its own Javadoc: "conditioned on a real transition having actually occurred... not
unconditional as first implemented") — fire the audit/event call only inside the same `if` branch
that performs the real transition, using the existing private helpers, no new mechanism:

```java
@Transactional
public void lock(UUID accountUuid) {
    Account account = getAccount(accountUuid);
    if (account.getStatus() == AccountStatus.ACTIVE) {
        account.lock();
        publishLifecycleEvent(account, "user.locked");
        recordAudit("account.locked", accountUuid, null);
    }
}

@Transactional
public void unlock(UUID accountUuid) {
    Account account = getAccount(accountUuid);
    if (account.getStatus() == AccountStatus.LOCKED) {
        account.unlock();
        publishLifecycleEvent(account, "user.unlocked");
        recordAudit("account.unlocked", accountUuid, null);
    }
}
```

`actorUuid = null` — this is a system-initiated transition (triggered by `LockoutService` after
failed-attempt evaluation, not an authenticated caller), matching D-022's own established convention
for recording "unknown actor" honestly rather than fabricating one. `"user.locked"`/`"user.unlocked"`
match `AccountStatus`'s existing enum values and the naming convention every other
`publishLifecycleEvent` call site already uses (`"user.registered"`, `"user.suspended"`, etc.).
`"account.locked"`/`"account.unlocked"` match the existing audit event-type naming
(`"account.activated"`, `"account.unlocked"` already used by `adminUnlock`) — reusing the identical
`"account.unlocked"` string for both the automatic and admin-initiated unlock paths is intentional:
same real-world event, same audit vocabulary, distinguished by `actorUuid` (null vs. the real admin
UUID) not by a different event-type string.

**Existing tests to check for impact**: any test asserting `LockoutService`'s automatic lock/unlock
behavior in isolation (mocked `AccountService`) is unaffected — the mock doesn't care what the real
method does internally. Any test asserting `AccountService.lock`/`unlock` directly against a real
`AuditService`/outbox (Testcontainers) may need a new assertion added, or may already incidentally
observe the new calls — checked at Phase 6.

## `package.md` §11 — planned §11 text (header bump deferred to the end, after both files verified)

- Q2: mark resolved, cite D-026, state the three values.
- Q3: mark partially resolved — scope (`merchant.api` only) yes, max-key limit no (deferred, no
  operational need demonstrated).
- Q4: mark out-of-scope for this spec — link construction belongs to the Notification Service;
  `EmailRequestedEventPayload` provides only the raw token/purpose.
- Q5: mark resolved once the `AccountService` fix lands, citing the fix directly.
- Q6: preserve as-is (already correctly resolved + non-blocking follow-up noted).

## Header bump (last step, after §11 and the code fix are both verified)

`Status: DRAFT` → `READY FOR IMPL`; `Version: 0.1` → `0.2`. Groups A/B named explicitly in §11 as
accepted test-suite exceptions (per the Phase 4 gate decision), not silently omitted.

## Tests required

New test(s) proving `AccountService.lock`/`unlock` now audit/publish on a real transition, and do
NOT do so on the no-op path (mirroring `adminUnlock`'s own already-tested guard behavior) — exact
test authored/verified at Phase 6/10.

## Execution order

1. Apply the `AccountService.lock`/`unlock` fix.
2. `mvn -pl services/auth test-compile` — confirm clean compile.
3. Check existing tests referencing `AccountService.lock`/`.unlock`/`LockoutService` for any that now
   need updating (a previously-silent no-audit path now audits — could affect a strict-count
   assertion elsewhere).
4. Run the affected test classes directly (Testcontainers) to confirm the fix behaves as intended —
   real negative-proof, not assumed.
5. Update `package.md` §11 per the plan above.
6. Bump `package.md`'s header (Status/Version) — the final step, only after 1-5 are verified.
7. Full-suite re-run to confirm the same, already-known Groups A/B failure signature (no new
   regressions from the `AccountService` fix).

---

**Phase 5 complete — implementation plan written.** Proceed to Phase 6 (Implementation) on approval.
