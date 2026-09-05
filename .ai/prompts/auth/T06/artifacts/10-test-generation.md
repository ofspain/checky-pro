# auth · T06 — Phase 10: Test Generation

No production code changed in this phase. One new test file created, three existing test files
extended (two of which — `AccountServiceTest`, `AccountControllerTest` — already got their Phase 9
regression fix in the prior turn; this phase adds the remaining required coverage).

- `services/auth/src/test/java/com/themistra/auth/account/AccountExceptionHandlerTest.java` (new, 2 tests)
- `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java` (extended, +4 tests)
- `services/auth/src/test/java/com/themistra/auth/account/AccountControllerTest.java` (extended, +3 tests)
- `services/auth/src/test/java/com/themistra/auth/events/EventTopicsTest.java` (extended, +1 test)

**All 26 tests verified passing** (~600ms, no Spring context, no database). This closes Kimi's
Phase 8 Finding 7 (T06-specific test coverage).

---

## Test manifest

### `AccountServiceTest` (15 tests total; 4 new this phase, beyond the Phase 9 fix)

| Test | Verifies |
|---|---|
| `shouldActivateAccountWithValidVerificationToken` | Named test — `consume` resolves a `PENDING_VERIFICATION` account; it activates, `user.registered` publishes, audit records with `actorUuid` = the account's own UUID (Finding 5). |
| `shouldRejectVerificationWhenTokenConsumeReturnsEmpty` | `consume` returning empty → `VerificationTokenRejectedException`; no account lookup, no event, no audit. |
| `shouldRejectVerificationWhenAccountIsNotPendingVerification` | Finding 2's specific case — `consume` resolves an already-`ACTIVE` account → uniform rejection, `isNotInstanceOf(InvalidAccountStateException.class)`, account status unchanged, no event/audit. |
| `shouldResendVerificationOnlyForPending accounts` *(exact spec spelling preserved via `@DisplayName`; method named `shouldResendVerificationOnlyForPendingAccounts` since Java identifiers can't contain spaces)* | A `PENDING_VERIFICATION` match issues+emits exactly once; an `ACTIVE` match and an unknown email both produce zero `VerificationTokenService`/`OutboxPublisher` interactions. |

Plus, from the Phase 9 fix (carried forward, already passing): `shouldEmitVerifyEmailEventOnRegistration`
now also asserts `EmailRequestedEventPayload.toString()` never contains the raw token (Finding 1's
mitigation).

### `AccountControllerTest` (6 tests total; 3 new)

| Test | Verifies |
|---|---|
| `verifyEmailReturnsNoContentOnSuccess` | `204` on success. |
| `verifyEmailPropagatesRejectionForTheExceptionHandlerToTranslate` | No local catch — the rejection exception propagates uncaught from the controller method itself (this test file is plain Mockito, not `MockMvc`, so it cannot observe the actual HTTP translation — that's `AccountExceptionHandlerTest`'s job). |
| `resendVerificationAlwaysReturnsTheSameAcknowledgementRegardlessOfMatch` | Both a "match" and "no-match" email produce the byte-identical `RegistrationAcknowledgement` — nothing in the controller to branch on, since `resendVerificationIfPending` is `void`. |

### `AccountExceptionHandlerTest` (new, 2 tests)

Direct unit test of the handler method itself — the only place the actual `400`/`INVALID_TOKEN`
HTTP mapping is verifiable, since `AccountControllerTest`'s established style doesn't go through
Spring's dispatcher.

| Test | Verifies |
|---|---|
| `onVerificationTokenRejectedReturnsUniformBadRequest` | `400`, `ProblemTypes.INVALID_TOKEN`, fixed title, **no detail** (nothing that could vary by rejection reason — R5). |
| `onVerificationTokenRejectedResponseIsIdenticalRegardlessOfConstructionSite` | Two independently-constructed exceptions produce byte-for-byte identical problem bodies. |

### `EventTopicsTest` (3 tests total; 1 new)

| Test | Verifies |
|---|---|
| `shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic` | Named test — `EventTopics.forAggregateType("verification-token")` equals `"auth.email.requested"`. |

---

## Notable implementation choices in these tests

- **`@DisplayName` for the space-containing named test.** `package.md`'s literal test name
  `shouldResendVerificationOnlyForPending accounts` contains a space, which is not a legal Java
  identifier. The method is named `shouldResendVerificationOnlyForPendingAccounts` (valid Java,
  same words) with `@DisplayName("shouldResendVerificationOnlyForPending accounts")` carrying the
  exact spec string — searchable/traceable without requiring invalid syntax.
- **No `contracts/events/auth/email-requested.v1.schema.json` contract test.** Confirmed via
  `find contracts/events/auth/` that only `user-lifecycle.v1.schema.json` exists — the
  email-requested schema is task 33's, not yet authored (consistent with Phase 1's finding). The
  existing `UserLifecycleEventPayloadContractTest` pattern was not replicated for
  `EmailRequestedEventPayload` because there is nothing yet to validate against; the toString/
  no-leak assertion was folded into `AccountServiceTest` instead (mirrors T05's precedent for
  `VerificationTokenResult`, tested alongside the service that creates it, not in a separate file).
- **`AccountExceptionHandlerTest` is a new file not listed in the frozen brief's Files to Create.**
  Authorized under Phase 10's broader mandate ("Cover every acceptance criterion... Generate ONLY
  tests") — it's a test file, not production code, and Phase 5's plan explicitly flagged that this
  coverage requires a file that didn't exist yet.

---

## Verification

Same method as every prior task: `mvn -pl services/auth test` still cannot run to completion due
to the pre-existing, unrelated `token` package compile failure. Verified via targeted `javac`
against the module's resolved test-scope classpath, then executed via the JUnit Platform
`Launcher` API (`junit-platform-launcher:1.12.2`, matching the project's resolved
`junit-platform-engine` version).

**Result: 26/26 tests successful, 0 failed, 0 skipped, ~600ms.** No bugs were found while writing
these tests (unlike T05's episode) — likely because Phase 9's fix to `AccountServiceTest` already
established the correct mocking pattern (shared `lenient()` stub for
`verificationTokenService.issue(...)`) that every subsequent test in this phase reused.

---

## Addendum: Phase 11 gap fixes

Kimi's Phase 11 review (`11-test-review.md`) found 6 gaps. One was factually incorrect (checked
against the actual code, not accepted at face value); one production defect was fixed live (a
reversal of an earlier explicit deferral, by human decision); the rest were straightforward test
additions.

- **Gap 1 (HIGH, production fix + test).** The Phase 9-deferred `AccountNotFoundException` leak in
  `activateFromVerificationToken` was fixed for real this time: `getAccount(...)` (which throws the
  distinguishing exception) replaced with `accountRepository.findByAccountUuid(...)
  .orElseThrow(VerificationTokenRejectedException::new)`. New test
  `shouldRejectVerificationWhenAccountDisappearsAfterConsume` proves it.
- **Gap 2 (MEDIUM) — REJECTED, factually inaccurate.** Kimi claimed
  `shouldResendVerificationOnlyForPendingAccounts` stubs `findByEmail` three times with "the same
  email argument." Checked directly against the file: it already uses three distinct emails
  (`pending@example.com`, `active@example.com`, `unknown@example.com`) — exactly what Kimi's own
  suggested fix recommends. No change made.
- **Gap 3 (MEDIUM) — deferred, not built.** A real `MockMvc`-based end-to-end test of the
  `400`/`INVALID_TOKEN` response would close the gap between `AccountControllerTest` (propagation
  only) and `AccountExceptionHandlerTest` (direct handler call), but this module has no `MockMvc`
  precedent anywhere yet. Deferred to a future integration-test pass rather than introducing a new
  testing style for this one case.
- **Gap 4 (LOW).** New test `resendVerificationNormalizesEmailBeforeLookup` — whitespace/case
  variants resolve to the same normalized lookup.
- **Gap 5 (LOW).** `registerRejectsKnownDuplicateWithoutTouchingEncoder` now also asserts
  `verificationTokenService`/`outboxPublisher` are never touched on a duplicate.
- **Gap 6 (LOW).** `shouldRejectVerificationWhenAccountIsNotPendingVerification` now uses a
  `Mockito.spy` on the `Account` and asserts `activateEmail()` was invoked exactly once (the setup
  call), proving the rejected attempt never reached it — not just that no event/audit followed.

**A second instance of the nested-stubbing gotcha** (first found in T05): the spy-based Gap 6 test
initially called `account.getAccountUuid()` *inside* an open `when(...).thenReturn(...)` call —
any Mockito-managed object (mock or spy) triggers the same "unfinished stubbing" failure when
touched mid-stub, not just plain mocks. Fixed the same way: extract to a local variable first.

**Final count: 28 tests, all passing** (17 `AccountServiceTest` + 6 `AccountControllerTest` + 2
`AccountExceptionHandlerTest` + 3 `EventTopicsTest`), verified the same way as above.
