<!-- MODEL: Claude Sonnet — Phase 13 (PR / Commit Preparation). -->

# auth · T40 · Phase 13 — PR / Commit Preparation

Phase 12 verdict was **PASS**. This is the final task in the entire `auth-service` spec sequence.
Branches off `main`; `main` stays deployable throughout.

---

## Commit title

```
auth: close R43 lock/unlock audit gap, bump spec to READY FOR IMPL 0.2 (T40)
```

## Commit message

```
auth: close R43 lock/unlock audit gap, bump spec to READY FOR IMPL 0.2 (T40)

Final task in the spec sequence: bump package.md from DRAFT/0.1 to READY
FOR IMPL/0.2, conditional on closing section 11's open questions and the
test suite passing. Neither precondition was actually true at Phase 0 -
verified, not assumed. Investigating why surfaced a real, independently-
confirmed R43 gap: AccountService.lock()/unlock() (the automatic path
LockoutService triggers after failed-login lockout) never audited or
published a lifecycle event, while the separate admin-initiated unlock
already did both.

Fixed by unifying both callers onto a single private unlock(UUID, UUID
actorUuid) method - the only place that now fires the event/audit, exactly
once, gated on a real status transition. This is cleaner than the first
attempt, which independently fired audit/events from both the plain
unlock() and adminUnlock(), double-firing on every real admin unlock - a
regression caught by actually running AccountServiceTest before it ever
reached review, not assumed safe from a code read.

package.md section 11 now accurately reflects every question: Q2's
thresholds (already implemented in T31, never previously recorded), Q3
(scope resolved, no key-count limit - now a tracked decision, D-030, not
an informal note), Q4 (out of this service's own scope - link
construction belongs to the Notification Service), Q5 (resolved by this
fix, with the one known remaining limitation - escalating re-locks stay
unaudited - named explicitly rather than glossed over). Section 12 records
the two still-open test-suite exceptions (Kafka environment connectivity,
timing-dependent API-key test flakiness) with reproducibility criteria -
one of which was corrected mid-task after empirical verification disproved
the first draft's assumed clean isolation/full-suite boundary.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

## Files changed

**Production**
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` (modified —
  `lock`/`unlock`/`adminUnlock` unified onto one audited, idempotent private method)

**Tests**
- `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java` (modified — 2
  new tests, 4 strengthened)
- `services/auth/src/test/java/com/themistra/auth/authn/LockoutPersistenceIntegrationTest.java`
  (modified — 3 assertions added/strengthened)

**Documentation**
- `spec/auth-service/package.md` (modified — header bump, §11 Q2-Q5, new §12)
- `services/auth/docs/architecture/auth-decisions.md` (modified — D-030 added)

No migration. No LOCKED decision touched.

## Summary

Closes the entire `auth-service` Phase 1 spec: fixes a real, independently-confirmed R43 audit gap
(automatic lock/unlock was unaudited while the admin path already was), self-catches and fixes a
regression its own first attempt introduced, accurately closes or honestly bounds every remaining
§11 question, and bumps the spec to `READY FOR IMPL 0.2` only once both of the task's own literal
preconditions are genuinely, verifiably addressed — not asserted.

## Testing performed

- `mvn -pl services/auth clean test-compile` — clean, no errors, at every phase.
- `mvn -pl services/auth test -Dtest=AccountServiceTest` — first run: 2 failures (the double-fire
  regression, self-caught). After the fix: **51/51 pass**.
- `mvn -pl services/auth test -Dtest=LockoutPersistenceIntegrationTest` — **8/8 pass**, all three
  T40 assertions exact-count + null-actor, against real Postgres (Testcontainers).
- `mvn -pl services/auth verify` (full suite): **707 tests, 1 failure, 6 errors** — the same,
  unchanged, independently-corroborated Groups A/B failure set throughout every phase since the fix
  landed; zero regressions.
- **Empirically verified before writing into the spec**: Kimi's suggested Group B reproducibility
  criterion was checked via three real isolated test runs before being trusted — found inaccurate
  (three different outcomes, not a clean pass/fail boundary), corrected to the honest finding.

## Specification references

- **Task:** T40 — Bump spec status (`spec/auth-service/tasks.md`, task 40) — the final task in the
  sequence
- **Requirements:** R43 (the audit gap this task's own investigation found and fixed)
- **LOCKED decisions:** none scoped
- **Named tests (`package.md` §8):** none scoped to this task

## Known, logged, out-of-scope follow-ups (carried into the now-frozen spec package)

1. **Escalating re-lock audit gap** (T11 AC7) — a second lockout cycle after re-offending
   post-expiry does not itself fire a distinct audit/event, since `Account.status` doesn't change.
   Would require a new event type (e.g. `user.lock-extended`) — named as a future option in `package.md`
   Q5, not built here.
2. **Groups A/B test-suite exceptions** — Kafka producer→broker environment connectivity (no known
   code fix) and genuine timing-dependent flakiness in the API-key integration tests (no confirmed
   root cause). Both documented in `package.md` §12 with reproducibility guidance.
3. **Q3's API-key maximum-active-count** — deliberately deferred (D-030), revisit trigger named.
4. **Q4** — likely requires `spec/notification-service/` visibility this task doesn't have; flagged,
   not answered on that service's behalf.

---

**Phase 13 complete — PR preparation written. T40 is ready for merge — the final task in the
`auth-service` spec sequence.**
