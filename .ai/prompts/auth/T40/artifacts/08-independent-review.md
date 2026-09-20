<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# auth · T40 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T40 — Bump `spec/auth-service/package.md` to READY FOR IMPL; close R43 lock/unlock audit gap |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Reviewed the T40 changes with fresh eyes: the `package.md` header/status bump and §11/§12 edits, the `AccountService.lock`/`unlock` refactor, the new R43 assertions in `LockoutPersistenceIntegrationTest`, and the updated `AccountServiceTest` assertions.

---

## Finding 1 — Escalating re-lock (T11 AC7) is not audited or published

**Issue.** `AccountService.lock(UUID)` guards on `AccountStatus.ACTIVE` and is therefore a no-op when `LockoutService` re-locks an account that is already `LOCKED` after the previous lock expired. The `lockout_state` row is still updated (`lockCount` increments, `lockedUntil` is extended), but no lifecycle event and no audit record are emitted. The Q5 resolution claims events/audit fire "on every real state transition"; an escalating re-lock is a real security-state change even though `Account.status` stays `LOCKED`.

**Evidence.**
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` lines 316-323: `lock(UUID)` only acts when status is `ACTIVE`.
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutStateMachine.java` lines 103-107: `applyFailure` returns `AccountStatusChange.LOCK` for a re-lock that increments `lockCount`.
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutService.java` line 149: `applyStatusChange` maps `LOCK` to `accountService.lock(accountUuid)`.

**Recommendation.** Either (a) emit a distinct `account.lock-extended` / `user.lock-extended` event and audit record when `lockCount` increases on an already-locked account, or (b) explicitly document in `auth-decisions.md`/§11 that escalating re-locks are intentionally unaudited at the `Account` level because only `Account.status` transitions are considered publish-worthy. If (b), update the Q5 wording from "every real state transition" to "every `Account.status` transition" to match.

**Confidence.** Medium.

---

## Finding 2 — `resetLockout` now emits audit/events but the existing test does not assert it

**Issue.** `LockoutService.resetLockout(UUID)` calls the state machine's `reset()` → `AccountStatusChange.UNLOCK` → `accountService.unlock(UUID, null)`, which now records an `account.unlocked` audit entry and publishes a `user.unlocked` lifecycle event. The existing integration test `resetLockoutOnARealLockedAccountClearsTheRowAndTransitionsToActive` verifies the row and status change but does not assert the new audit/event behavior that T40 introduced.

**Evidence.**
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutService.java` lines 109-118: `resetLockout` applies an `UNLOCK` decision.
- `services/auth/src/test/java/com/themistra/auth/authn/LockoutPersistenceIntegrationTest.java` lines 197-211: verifies status and row only.

**Recommendation.** Add assertions to the reset test mirroring the two new T40 assertions: verify `auditService.list(...)` contains `account.unlocked` and, if feasible in this test, that `OutboxPublisher` received `user.unlocked`. This prevents a future regression where the admin/reset unlock path silently loses audit coverage again.

**Confidence.** Medium.

---

## Finding 3 — Q3 defers the API-key maximum-active count without a tracked decision

**Issue.** §11 Q3 states that no maximum active API-key count is implemented and that the limit is "deferred as a future guard if operational need arises." An unbounded number of active merchant API keys is a real operational and security exposure; leaving it as an informal note in a resolved/partially-resolved question makes it easy to forget.

**Evidence.**
- `spec/auth-service/package.md` line 150.
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyService.java`: no active-key quota check exists.

**Recommendation.** Convert the deferral into a tracked decision (e.g., a new `auth-decisions.md` entry D-030 or a referenced issue) and cite it in Q3. If the team wants to keep the acceptance clean, add a configurable high default limit (e.g., 100 active keys per merchant) now so the guard exists even if the final number is later revised.

**Confidence.** Low.

---

## Finding 4 — Q4 boundary hand-off to Notification Service could be more explicit about the contract

**Issue.** §11 Q4 correctly states that link construction is outside `services/auth`'s scope. However, the `EmailRequestedEventPayload` contract only carries `accountUuid`, `purpose`, `token`, and `occurredAt`. The Notification Service will need a base URL and token expiry/TTL to render safe, non-replayable links. The current wording does not record what downstream consumers can rely on versus what they must source themselves.

**Evidence.**
- `spec/auth-service/package.md` line 151.
- `services/auth/src/main/java/com/themistra/auth/account/event/EmailRequestedEventPayload.java`.
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` lines 384-395.

**Recommendation.** Add a sentence to Q4 listing the payload fields the auth service guarantees and noting that base-URL and token-TTL sourcing remain the Notification Service's responsibility. This documents the interface rather than just assigning blame.

**Confidence.** Low.

---

## Finding 5 — §12 test-suite exceptions lack objective acceptance criteria

**Issue.** §12 accepts two failing test groups as environmental/unconfirmed. While the diagnosis is plausible, the spec does not define an objective way to decide whether a future failure belongs to one of these accepted groups or is a new regression. Without reproducibility criteria, the exception can be silently widened.

**Evidence.**
- `spec/auth-service/package.md` lines 157-172.

**Recommendation.** Add a one-line reproducibility criterion for each group, e.g.:
- Group A: tests pass when the Kafka broker configured by `spring.kafka.bootstrap-servers` is reachable from the test JVM; failures occur only when it is not.
- Group B: the `ApiKey*IntegrationTest` pair passes when run in isolation (`mvn -pl services/auth test -Dtest=ApiKeyLifecycleIntegrationTest,ApiKeyExchangeIntegrationTest`) and fails only under full-suite load.

**Confidence.** Low.

---

## Finding 6 — `Implementer | TBD` and `Status | READY FOR IMPL` read inconsistently with §12

**Issue.** The spec header still lists `Implementer | TBD` and `Status | READY FOR IMPL`, while §12 states that all feature work (T01-T39) is complete and reviewed. This is likely intentional for hand-off, but a reader may conclude the document is out of date.

**Evidence.**
- `spec/auth-service/package.md` lines 8-9 and lines 170-172.

**Recommendation.** If the spec is intentionally a "ready for implementation" package despite the work already being done, add a parenthetical note in the header or §12 explaining the status (e.g., "Status bumped to `READY FOR IMPL` as the frozen Phase 1 package; implementation in `services/auth` is already complete and reviewed"). Otherwise update the header to reflect completion.

**Confidence.** Low.

---

## Finding 7 — `AccountService.lock`/`unlock` remain public and trust the L12 social contract

**Issue.** `AccountService.lock(UUID)` and `unlock(UUID)` are public methods intended to be called only by `LockoutService`. The new audit/event paths increase the impact of accidental misuse by another caller. There is no compile-time or ArchUnit enforcement of the L12 boundary mentioned in the Javadoc.

**Evidence.**
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` lines 315-333.
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutService.java` lines 20-21: the only documented sanctioned caller.

**Recommendation.** Longer term, tighten L12 with an ArchUnit rule or package-private/internal interface. Short term, the Javadoc is already explicit and no action is required for T40.

**Confidence.** Low.
