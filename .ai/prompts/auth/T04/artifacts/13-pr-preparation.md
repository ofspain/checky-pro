# auth · T04 — Closure Note (task skipped, satisfied by T03)

**Status: CLOSED — no code change. Satisfied by T03.**
**Decided by:** femi, 2026-07-24.

This task did not go through Phases 1–12. Phase 0 (`artifacts/00-repository-understanding.md`)
found that T04's entire scope — task statement, R10, the named test
`shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure`, and L2 — was already implemented and
tested as part of T03, by explicit human decision at T03's own Phase 4 approval gate
(`.ai/prompts/auth/T03/artifacts/04-frozen-task-brief.md`, Finding 10 disposition). Continuing
Phases 1–12 here would have produced a design brief, reviews, and a verification pass over zero
new code — process theater, not engineering.

## Why

- `tasks.md` task 4's one-line statement — "wire `AuditService.record(...)` for
  `password.breach_check_failed` and unit-test the fail-open path" — describes a narrow follow-up
  to task 3, assuming task 3 would build the domain/breach-check logic *without* the audit wiring.
- `package.md` §8, the authoritative named-test list, assigned T04's own test
  (`shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure`) to *T03's* header instead, making it
  impossible for T03 to pass its own required tests without doing task 4's work too.
- T03 absorbed it accordingly, decided explicitly and documented at the human-approval gate, not
  silently.
- R43 (the general "every security-relevant action is audited" rule) is scoped into T04's header
  only because this one event is an instance of it — R43 gets re-scoped into each later task that
  adds its own audited action (lockout, MFA, API keys, sessions), not expanded here.

## Evidence this is actually done

- `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicy.java` —
  `recordBreachCheckFailedAudit()` calls `AuditService.record(...)` with
  `eventType="password.breach_check_failed"`, `outcome=AuditOutcome.FAILURE`, on the fail-open path
  (`validateNotBreached()`'s catch of `BreachCheckUnavailableException`).
- `services/auth/src/test/java/com/themistra/auth/account/PasswordPolicyTest.java` —
  `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure()` (T04's exact named test) and
  `shouldNotPropagateWhenAuditServiceThrowsDuringFailOpen()` (beyond T04's bare statement) both
  pass, verified in T03's Phase 10/11 test runs (27/27 passing).
- Committed at `3688da1` ("Add password policy domain and HIBP breach-check (T03)") on
  `spec/service-specs-and-ai-framework`.

## Outstanding, not part of this closure

`spec/auth-service/tasks.md` itself still lists task 4 as a distinct line item. Updating the spec
to reflect this closure is a spec-authoring change outside this task's (and this pipeline's)
permission to edit `spec/` — flagged for the spec author, not actioned here.

**No files changed. No commit produced by this task.**
