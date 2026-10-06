# notification · T19 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

`notification-service T19: prove the delivery log is append-only and reconstructable`

## Commit message

```
notification-service T19: prove the delivery log is append-only and reconstructable

Adds DeliveryLogDisputeGradeIntegrationTest, four live checks for the
"was the merchant notified?" evidence in delivery_log. No production code
changed.

- AC1: the notification_app role is rejected with SQLState 42501 for UPDATE,
  DELETE, and TRUNCATE. The TRUNCATE check was added in the Phase 10 audit: a
  role that can truncate can empty the whole log without issuing a DELETE.
  Connects as notification_app, not the migration owner, since owners bypass
  grants.
- AC2: a real transient failure followed by a replay leaves FAILED and SENT
  EMAIL rows sharing one source_event_key, with attempts exactly {1, 2}.
  Replay goes through RetryScheduler.processOne on a real delivery_retry row,
  not sweep(), which is ShedLock-guarded.
- AC3: every rendered row, EMAIL and IN_APP, records a template version.
- AC4: an opted-out channel leaves a SUPPRESSED row with no template version,
  and no email is sent.
- AC5: a static scan of src/main finds no UPDATE/DELETE against delivery_log and
  no delete calls on its repository. This is textual and best-effort; the
  database grant in AC1 is the authoritative guarantee.

Every assertion was shown live: each was broken on purpose, shown to fail for
its stated reason, and reverted. AC5 was shown against a planted violation.

Verified in this environment with a fresh full run: 373 tests, 0 failures, 0
errors.

Disclosed follow-ups, outside this task's scope:
- PreferenceResolverIntegrationTest (T08) compares a DB-written timestamp to the
  JVM clock with no tolerance and failed once intermittently from Docker clock
  skew. Not edited here.
- Phase 3 (Kimi's design challenge) ran before the Phase 2 brief existed; it
  consumed the Phase 1 extraction only. Disclosed at Phase 4.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/notification/src/test/java/com/themistra/notification/delivery/DeliveryLogDisputeGradeIntegrationTest.java`

**Modified**

None.

**Deleted**

None.

**Process artifacts**
- `.ai/prompts/notification/T19/artifacts/00-13-*.md`: the full 14-phase pipeline record. This
  file completes it.

## Summary

Turns the static picture of an append-only dispute log into live proof. Before this task, the
property rested on code reading and a grant that no test exercised. Now a real database role is
checked for UPDATE, DELETE, and TRUNCATE, a real retry chain is checked for shared keys and attempt
sequence, and suppression is checked for its own row. The TRUNCATE check came from the audit: the
original proof would have passed while a role could still empty the whole log.

## Testing performed

- `DeliveryLogDisputeGradeIntegrationTest`: 4/4 pass. Each assertion was broken on purpose in
  isolation, shown to fail for its stated reason, and then reverted. AC5 was shown against a
  temporary production-source violation, which was removed afterwards.
- `mvn -pl services/notification clean verify`: 373 tests, 0 failures, 0 errors, exit 0. Run fresh
  after the Phase 10 change and again in Phase 11.
- `git diff --stat 840d5c7^..HEAD -- services/notification/src/main`: empty. No production code changed.
- `git diff --stat 840d5c7^..HEAD -- services/auth services/crypto services/payment spec/`: empty.

## Specification references

- **Task:** `spec/notification-service/tasks.md`, task 19 ("Dispute-log check").
- **Locked decisions:** L3 (append-only dispute-grade log), L9 (template version recorded).
- **Requirements:** R10, R11, R12, R13 (R13's dead-letter path is covered by T14's own tests, not
  re-proven here).
- **Rationale:** `ARCHITECTURE.md` §3.5, "did the merchant get notified?" disputes.

## Known, deliberate gaps (not this task's scope)

- **AC5 static scan is textual.** It would miss `EntityManager` or dynamically built SQL. The grant
  in AC1 still rejects those, so the property holds, but the static evidence is narrower than the
  claim.
- **Failure paths beyond the chain tested here** (for example, R13's dead-letter path) are not
  re-proven. T14 covers them.
- **Template version is absent on rows written before rendering** (SUPPRESSED, no-recipient FAILED).
  Correct per R11, scoped and disclosed.

## Reviewer notes

- **Kimi's Phase 8 review (6 findings)** found a real gap, the IN_APP template-version check, which is
  now fixed, plus three cheap strengthenings that were applied. Its verification-environment concern
  was closed by a fresh run.
- **Kimi's Phase 11 review (4 gaps)** all checked out against source, and its grant-list claim matched
  V2 exactly.
- **A process irregularity is disclosed**: Kimi's Phase 3 review consumed only the Phase 1
  extraction, because it was committed before the Phase 2 brief existed.
- **A test flake in another task is disclosed**, not fixed here: `PreferenceResolverIntegrationTest`
  (T08) compares a DB-written timestamp to the JVM clock.

---

**Phase 13 complete. PR description drafted. All phases 0-12 closed for notification-service T19.**
