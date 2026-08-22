<!-- MODEL: Claude Sonnet — Phase 13 (PR / Commit Preparation). -->

# auth · T36 · Phase 13 — PR / Commit Preparation

Phase 12 verdict was **PASS**. This task is ready for merge. Branches off `main`; `main` stays
deployable throughout.

---

## Commit title

```
auth: add end-to-end merchant identity lifecycle integration test (T36)
```

## Commit message

```
auth: add end-to-end merchant identity lifecycle integration test (T36)

Adds the one integration test this module didn't have: the full identity
lifecycle as a single, continuous, real-HTTP flow against Testcontainers
Postgres + Kafka - register, verify email, login, get promoted to MERCHANT,
get blocked pending MFA, enroll TOTP, log back in, create an API key,
exchange it for a JWT, list sessions, revoke one. Every prior test in this
module proves its own feature in isolation; this one proves they actually
compose.

TOTP enrollment has no HTTP surface anywhere in this codebase (Phase 0
finding, no Controller exists) - the test calls MfaService directly for
that one step, matching SasLoginIntegrationTest's already-accepted
precedent, a decision made explicitly at this task's Phase 4 human gate
rather than assumed.

This task's own process caught two real problems before either reached
review: a genuine CSRF-handling bug (POST /accounts and
/accounts/verify-email require a real session-scoped CSRF token neither
this task's plan nor any obvious first draft would have anticipated, since
every other call in the test is Bearer-authenticated and CSRF-exempt), and
a materially incomplete first draft - the task statement's own "login
(password)" step was implemented, reviewed twice, and only actually written
in during Kimi's Phase 11 test review, having been silently skipped from
verify-email straight to MERCHANT assignment until then.

A pre-existing, environment-level Kafka producer connectivity issue blocks
a full local green run in this environment right now - independently
reproduced on AccountPersistenceIntegrationTest (already-merged, unrelated,
previously-passing), confirming it predates and is external to this task.
Logged rather than fixed in-scope, per human-gate decision.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

## Files changed

**Tests only**
- `services/auth/src/test/java/com/themistra/auth/EndToEndLifecycleIntegrationTest.java` (new,
  719 lines — one `@Test` method, 11 flow steps, plus ported/adapted HTTP, authorize-flow, and
  Kafka-correlation helpers)

No production code changed. No `spec/` file touched. No migration.

## Summary

Implements the task statement's full flow — register → verify email → login (password) → admin
assigns MERCHANT → next login requires MFA enrollment → enroll TOTP → login with TOTP → create API
key → exchange key for JWT → call session list → revoke session — as one composed, real-HTTP
integration test (matching this session's established "one lifecycle, one test" pattern from T27).
Two flow steps deliberately don't use HTTP: TOTP enrollment (no endpoint exists anywhere in this
codebase, a genuine Phase 0/4-gated finding) and the admin identity's own bootstrap registration
(plumbing, not a tested step) — both explicitly named and justified in-code, not silently assumed.

This task's own review process (2 self-review findings, 11 independent-review findings, 8 test-review
findings — 21 total) drove two categories of real improvement beyond the task's own literal scope:
a genuine implementation bug (CSRF handling on the two anonymous endpoints, caught by this task's
own first negative-proof run) and a genuine completeness gap (the missing "login (password)" step,
caught only by Kimi's Phase 11 adversarial re-reading of the task statement against the actual code
— every earlier phase, including two rounds of Claude's own review, missed it). Several Kimi findings
were verified against source and explicitly rejected with re-derivable evidence rather than
reflexively applied.

## Testing performed

- `mvn -pl services/auth clean test-compile` — clean, no errors, at every phase.
- `mvn -pl services/auth test -Dtest=EndToEndLifecycleIntegrationTest` — run repeatedly against real
  Docker Testcontainers (Postgres + Kafka) across Phases 6, 9, and 11. Registration (including the
  CSRF fix) consistently passes; the run is consistently blocked at the same point by a
  pre-existing, environment-level Kafka producer→broker connectivity issue, independently reproduced
  on `AccountPersistenceIntegrationTest` (already-merged, unrelated, previously-passing) — confirming
  the blocker predates this task and is not a regression it introduced. No change across Phases 9/11
  altered the failure point or introduced a new one.
- Every helper method not exercised by this environment's blocked live run is a direct or
  near-verbatim port of a technique already proven correct in an already-passing, already-reviewed
  sibling file (`SasLoginIntegrationTest`'s authorize flow/PKCE/CSRF machinery,
  `ApiKeyLifecycleIntegrationTest`'s API-key HTTP calls, `SessionIntegrationTest`'s session
  list/revoke, `AccountPersistenceIntegrationTest`'s Kafka-consumer correlation pattern) — not novel,
  unverified logic.

## Specification references

- **Task:** T36 — End-to-end integration test (`spec/auth-service/tasks.md`, task 36)
- **Requirements:** R1, R4, R24, R30, R31, R36, R37 (widened at Phase 1, justified — the task
  statement's own final step)
- **LOCKED decisions:** L6, L8, L9, L10, L11, L12 (all honored, none required deviation)
- **Named tests (`package.md` §8):** none valid for this task (confirmed at Phase 1 and again at
  Phase 10 — the header's `shouldConformToAuthOpenApiContract` belongs to T33's unrelated OpenAPI
  contract test; no entry in package.md's actual list describes an end-to-end lifecycle scenario).
  This test's name, `shouldCompleteFullMerchantIdentityLifecycle`, was chosen in Phase 5 to match
  this module's `*IntegrationTest` naming convention.

## Known, logged, out-of-scope follow-up

A local Docker/Testcontainers Kafka producer connectivity issue
(`Bootstrap broker localhost:9094 ... could not be established`) blocks a full green run of both
this new test and at least one pre-existing, unrelated one in this environment right now. Confirmed:
no lingering/stale containers, no hardcoded `bootstrap-servers` override anywhere in
`application.properties`. Logged per the Phase 6 human-gate decision as a separate environment issue,
not fixed in-scope — the same disposition this session gave the ArchUnit/Surefire non-execution
issue found during T32.

---

**Phase 13 complete — PR preparation written. T36 is ready for merge.**
