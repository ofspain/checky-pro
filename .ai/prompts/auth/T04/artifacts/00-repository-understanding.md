# auth · T04 — Phase 0: Repository Understanding

## 1. Architecture summary

Same service as T03: `services/auth`, Spring Boot 3.5.4 / Java 21, package-by-feature under
`com.themistra.auth`, one Postgres schema (`auth`), Flyway-owned DDL, package-private repositories.
Relevant subset for T04:

- **`audit` module** — `AuditService.record(RecordAuditEventRequest)` is the sole sanctioned write
  path to `auth_audit`: it inserts the durable, append-only row and, in the same transaction,
  mirrors a reduced payload to Kafka via `OutboxPublisher` (topic `auth.security.audit`, event type
  prefixed `"security." + eventType`). This module predates T03/T04 entirely — it was built out
  earlier in the spec's Foundation work, not by either of these tasks.
- **`account` module** — `PasswordPolicy` (added by T03) is the only caller in the codebase that
  invokes `AuditService.record(...)` for the `password.breach_check_failed` event type.
- **`authn` module** — `BreachCheckClient` (added by T03) is the HIBP range-check client whose
  failure triggers the audit call.

## 2. Existing code this task touches

T04's task statement, verbatim: *"Wire `AuditService.record(...)` for
`password.breach_check_failed` and unit-test the fail-open path."*

**This already exists, in full, as of the T03 commit (`3688da1`, current `HEAD`):**

- `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicy.java` —
  `recordBreachCheckFailedAudit()` (private method) calls:
  ```java
  auditService.record(new RecordAuditEventRequest(
          "password.breach_check_failed", AuditOutcome.FAILURE, null, null, null, null, null, null));
  ```
  wrapped in a try/catch that logs and swallows any failure from `AuditService.record` itself, so
  an audit-write failure can never block the password from being allowed. This is called from
  `validateNotBreached()` exactly when `BreachCheckClient.isBreached(...)` throws
  `BreachCheckUnavailableException` — i.e., exactly the fail-open path T04 describes.
- `services/auth/src/test/java/com/themistra/auth/account/PasswordPolicyTest.java` —
  `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure()` is present, passing, and is the *exact*
  named test T04's header lists. It asserts: `validate(...)` does not throw when the range check is
  unavailable, and `auditService.record(...)` was called exactly once with
  `eventType="password.breach_check_failed"`, `outcome=AuditOutcome.FAILURE`, and null
  `accountUuid`/`actorUuid`.
- The same test class also has `shouldNotPropagateWhenAuditServiceThrowsDuringFailOpen()`, which
  goes beyond T04's bare statement — it proves the audit call's own failure doesn't propagate
  either.

**Nothing new exists to build for T04 as currently scoped against this codebase state** — see
§5 below.

**Pre-existing, unrelated to either T03 or T04:** `AuditService`, `AuditEvent`,
`RecordAuditEventRequest`, `AuditOutcome`, `OutboxPublisher`, `auth_audit` table — all built before
this spec's Foundation tasks began.

## 3. Established patterns to follow

Same as documented in T03's Phase 0: constructor-injected `@Service`/`@Component` beans, `Clock`
injected (not used directly by the audit call itself — `RecordAuditEventRequest` carries no
timestamp field; `AuditService` stamps `Instant` internally), outbox-in-same-transaction for
anything Kafka-bound, package-private repositories, ArchUnit-enforced module boundaries. Not
restated further here per the "reference, don't restate" convention.

## 4. Testing conventions

Same as T03: plain JUnit 5 + Mockito, no Spring context for unit tests (`agents.md`). The relevant
test already follows this exactly (`PasswordPolicyTest`, `@ExtendWith(MockitoExtension.class)`,
mocked `AuditService`/`BreachCheckClient`).

## 5. Known gaps / unknowns

**This is not a gap — it is the central finding of this phase, and it changes what T04 is.**

T04's task statement, scoped requirements (R10, R43), scoped LOCKED decision (L2), and named test
(`shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure`) are *identical in substance* to what T03
already delivered. This is not a coincidence: T03's own Phase 4 frozen brief
(`.ai/prompts/auth/T03/artifacts/04-frozen-task-brief.md`, Scope §In and the Phase 3/Kimi Finding
10 disposition) explicitly decided:

> "T03's implementation fully satisfies `tasks.md` task 4's intent... Owner: project tracking —
> mark task 4 satisfied by T03 rather than duplicating the work."

That decision was made by a human (femi) at T03's Phase 4 approval gate, precisely because T03's
own named-test list included `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure` and couldn't
be satisfied without implementing the audit call — so it was pulled forward into T03 rather than
left for a separate task.

I do not know whether `tasks.md`'s task 4 entry should now be:
(a) marked complete/removed as a standalone task (spec-authoring change, outside this task's or my
own permission to edit `spec/`), or
(b) reinterpreted as covering something T04's current one-line statement doesn't actually capture
(e.g., R43's *general* audit requirement — "every security-relevant action" — extended to some
other event type not yet wired, which T03 did not touch).

Re-reading R43 (`requirements.md`): "WHEN any security-relevant action occurs (login
success/failure, lock, unlock, MFA events, password/key changes, token reuse, API-key operations),
THEN the system SHALL append an `auth_audit` row and mirror a reduced event..." — this is a
service-wide requirement spanning many other tasks (lockout, MFA, API keys, sessions), none of
which exist yet. T04's own scope, as literally written in its Phase 0 header, only names
`password.breach_check_failed` — R43 is listed as a *scoped requirement ID* for T04, but T04's task
statement doesn't ask for anything beyond the one event type T03 already wired. Whether R43 was
included in T04's header just because `password.breach_check_failed` is one instance of the
general audit rule (in which case T04 is fully done) or because task 4 was meant to cover more
ground than its one-line statement literally says (in which case there's missing scope) is a
genuine ambiguity I cannot resolve by reading code — this is a question for the human before
proceeding further.
