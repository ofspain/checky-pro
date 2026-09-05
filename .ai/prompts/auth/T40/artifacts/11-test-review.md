<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# auth · T40 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T40 — Bump `spec/auth-service/package.md` to READY FOR IMPL; close R43 lock/unlock audit gap |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Reviewed the Phase 10 test manifest and the relevant test sources. I could not run `mvn` independently because Maven is not installed in this environment (`mvn: command not found`, no `mvnw` present); the review below is therefore based on source inspection and the Phase 10 verification record.

---

## Gap 1 — Automatic lock/unlock idempotency is not integration-tested

**Why it matters.** `AccountService.lock(UUID)` and `unlock(UUID)` are now guarded, idempotent state transitions. The unit test `adminUnlockCalledTwiceOnlyAuditsAndPublishesOnce` proves idempotency for the admin unlock path, but there is no equivalent test proving that a second automatic `lock` or `unlock` call produces no additional audit rows or lifecycle events. A future regression that removes the status guard could silently re-emit events.

**Suggested test.** Add unit tests `lockCalledTwiceOnlyAuditsAndPublishesOnce` and `unlockCalledTwiceOnlyAuditsAndPublishesOnce` in `AccountServiceTest`, mirroring the existing admin-unlock idempotency test. Alternatively, extend `LockoutPersistenceIntegrationTest` to call `lockoutService.recordFailedAttempt` / `recordSuccessfulAttempt` twice and assert exactly one `account.locked` / `account.unlocked` audit row.

---

## Gap 2 — Integration assertions use `anySatisfy` and do not count events

**Why it matters.** `LockoutPersistenceIntegrationTest` uses `anySatisfy(event -> event.eventType().isEqualTo("account.locked"))` (and `account.unlocked`). This catches the absence of the expected audit row, but it does not catch a double-emission bug or an unexpected extra row of the same type. Today the setup produces exactly one such row, so the test passes, but it is weaker than an exact-count assertion.

**Suggested test.** Replace `anySatisfy` with a filtered count assertion, e.g.:

```java
assertThat(auditService.list(accountUuid, Pageable.unpaged()).getContent())
        .filteredOn(event -> event.eventType().equals("account.locked"))
        .hasSize(1);
```

Apply the same pattern to all three T40 assertions (lock, unlock, resetLockout).

---

## Gap 3 — No test asserts the actorUuid for system-initiated lock/unlock

**Why it matters.** The R43 fix intentionally records a `null` actor for system-initiated lock/unlock (D-022 convention). The integration tests assert the correct `eventType` but do not assert that `actorUuid` is `null`. A regression that accidentally copied the `accountUuid` from self-service calls into the actor field would not be caught.

**Suggested test.** Extend the three `LockoutPersistenceIntegrationTest` assertions to verify `actorUuid()` is `null` for the matched `account.locked` / `account.unlocked` rows.

---

## Gap 4 — No test verifies the `user.locked` lifecycle event payload

**Why it matters.** `AccountServiceTest.shouldUnlockAccountViaAdminEndpoint` already verifies that the `user.unlocked` lifecycle payload carries the post-transition `AccountStatus.ACTIVE`. There is no corresponding test for the `user.locked` payload's status. A bug that published `user.locked` with payload status `ACTIVE` would be caught only indirectly, if at all.

**Suggested test.** Add a unit test `lockTransitionsActiveToLockedAndPublishesLockedEventWithLockedPayload` in `AccountServiceTest` that captures the `UserLifecycleEventPayload` and asserts its `status()` is `AccountStatus.LOCKED`.

---

## Gap 5 — Full-suite verification cannot be independently reproduced here

**Why it matters.** The Phase 10 manifest reports `mvn -pl services/auth verify` as 705 tests, 1 failure, 6 errors (Groups A/B). I could not reproduce this because Maven is not available in the current environment. Future reviewers or CI will need to trust the Phase 10 log, which is fine as long as the CI run is linked or archived.

**Suggested test/action.** Nothing to change in code; ensure the CI run URL or log artifact for T40 is attached to the task record so the full-suite claim remains independently auditable.

---

## Gap 6 — `resetLockout` audit assertion is new but not mentioned as a distinct `@Test`

**Why it matters.** The Phase 10 manifest says "No new `@Test` methods were added — three existing tests gained new assertions." The source shows that `resetLockoutOnARealLockedAccountClearsTheRowAndTransitionsToActive` did gain the audit assertion. This is consistent with the manifest, but a reader might miss that the reset path is also covered. The comment in the test references "Kimi Phase 8 Finding 2," which is helpful traceability.

**Suggested test/action.** No code change required, but consider listing the reset assertion explicitly in the test manifest table (it is currently grouped under "assertion added, Phase 9" without stating which one).

---

## Gap 7 — Documentation changes have no automated traceability test

**Why it matters.** T40 changed `package.md` §11/§12 and added `auth-decisions.md` D-030. There is no test that enforces that Q3's partial resolution is reflected in D-030, or that the spec header/version/status are consistent with the decision log. This is typical for documentation tasks, but it means future edits could silently drift.

**Suggested test/action.** Add a lightweight traceability check (PR checklist or static script) that verifies every `package.md` §11 question marked "Resolved" or "Partially resolved" has a corresponding `auth-decisions.md` D-xxx entry or an explicit unresolved note.
