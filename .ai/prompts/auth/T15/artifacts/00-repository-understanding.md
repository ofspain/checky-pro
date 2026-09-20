# auth · T15 — Phase 0: Repository Understanding

Grounding only — no design, no requirements extraction. Read: `spec/auth-service/{package.md,
requirements.md, design.md, tasks.md, agents.md}` plus the actual repository state. T15 is unusual
in this chain: it is entirely a testing task (`tasks.md` item 15's own text: "Add a security
test..."), so this phase's normal "what production code does this task touch" framing inverts —
the deliverable *is* the test.

## 1. Architecture summary

Unchanged from T11-T14 — no new architecture. The relevant machinery (SAS login flow,
`LoginFailureHandler`, `AccountUserDetailsService`) was fully built by T13; T15 only adds
verification of a property that machinery was already built to guarantee.

## 2. Existing code this task touches

**Read-only — this task's own guardrails forbid touching production code (test-only task):**
- `LoginFailureHandler.java` (T13) — the actual mechanism R21 depends on: never branches on
  account status or exception subclass when producing the redirect response.
- `AccountUserDetailsService.java` (T13) — `loadUserByUsername`, the point where `LOCKED`/
  `SUSPENDED`/`PENDING_VERIFICATION`/unknown accounts diverge internally (different
  `AuthenticationException` subclasses) before `LoginFailureHandler` normalizes them back to one
  response. `DELETED` accounts are filtered by `AccountService.findLoginView` to look identical to
  unknown emails *before* even reaching this class (confirmed: `AccountService.java:339`,
  `.filter(account -> account.getStatus() != AccountStatus.DELETED)`) — so "`DELETED`" and
  "non-existent" are already the same code path today, not two paths that happen to converge.

**A precise, well-evidenced gap — no existing test proves R21's actual claim:**
- `LoginFailureHandlerTest.everyExceptionSubclassProducesTheSameRedirect` (T13) varies the
  `AuthenticationException` *object* passed to `onAuthenticationFailure` while `findLoginView` is
  stubbed to return a fixed `ACTIVE` account throughout — it proves "whatever exception Spring
  hands us, we redirect the same way," not "a `LOCKED` account attempt produces the same response
  as a `SUSPENDED` one." Different property.
- `pendingVerificationAccountAuditsOnlyNeverCallsLockoutService`/
  `suspendedAccountAuditsOnlyNeverCallsLockoutService`/
  `stillLockedAccountAuditsOnlyNeverCallsRecordFailedAttempt` (T13) *do* vary the stubbed account
  status, but only assert audit/`LockoutService` side effects — none of them capture or compare
  the actual `response.sendRedirect(...)` argument across statuses.
- `SasLoginIntegrationTest.unknownEmailProducesTheSameResponseShapeAsAKnownAccountFailure` (T13,
  Testcontainers, unexecuted in this environment per every prior task's own disclosure) compares
  exactly two cases — unknown email vs. a known account with a bad password — not the full
  `LOCKED`/`SUSPENDED`/`DELETED`/non-existent matrix R21 and this task's own header name.

**No single existing test varies account status across all of `LOCKED`, `SUSPENDED`, `DELETED`,
and non-existent, and then compares the resulting response.** This is the real gap T15 exists to
close, confirmed by reading every candidate test file directly, not assumed from the task title.

## 3. Established patterns to follow

- **Response-shape comparison, unit level:** `everyExceptionSubclassProducesTheSameRedirect`
  already establishes the technique — capture `response.sendRedirect(...)`'s argument via
  Mockito, compare across cases. No Testcontainers/real-HTTP-server needed to prove this property;
  T13 already chose this approach for the adjacent "exception subclass" axis.
- **Response-shape comparison, integration level:** `SasLoginIntegrationTest` establishes the
  alternative — real HTTP via `TestRestTemplate`, comparing `ResponseEntity` status/`Location`
  header. Requires Docker; unexecuted in this sandbox, same as every Testcontainers test since T12.
- **Test-only task precedent:** none exists yet in this chain — T09-T14 all touched production
  code. `06-implementation.md`'s own guardrail text ("Do NOT write tests here... unless the task
  itself is test-only") anticipates this exact case; T15 is the first task where Phase 6 *is*
  test-writing.

## 4. Testing conventions

- Unchanged from T13/T14. Plain JUnit 5 + Mockito for unit-level; `@SpringBootTest` +
  Testcontainers for integration-level. No `MockMvc`/`WebTestClient` precedent anywhere in this
  module (confirmed again, unchanged since T13 Phase 0).

## 5. Known gaps / unknowns

- **I do not know** whether T15's test is meant to live at the unit level (extending
  `LoginFailureHandlerTest.java`, matching its own existing `everyExceptionSubclassProducesTheSameRedirect`
  technique but varying account *status* instead of exception *type*) or the integration level
  (extending `SasLoginIntegrationTest.java`'s real-HTTP comparison to the full four-status matrix).
  Both are legitimate interpretations of "a security test"; the task statement doesn't specify.
  Flagged for Phase 1/2 — not decided here.
- **Confirmed, not a gap, but a major constraint on Phase 2's decision above:** if T15's test is
  written as a Testcontainers integration test, it will be **unexecuted in this environment**
  (no Docker daemon here, the same limitation every integration test since T12 has carried) — and
  unlike every prior task, T15 has no production-code "at least it compiles clean" consolation;
  the deliverable itself would be unverified. A unit-level test avoids this entirely, matching
  T13's own established preference for proving this exact class of property without Testcontainers
  where possible.
- **Confirmed, not a gap:** `contracts/api/auth.yaml` still doesn't exist — same gap tracked since
  T11, irrelevant to this task's actual scope (a security *test*, not contract authorship).
