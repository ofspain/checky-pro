<!-- MODEL: Claude Opus 5 — Phase 6 (Implementation) -->

# frontend · T33 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T33 — Clean re-auth on failed renewal |
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |
| **Commit** | `f01b250a` |

## What was built

| File | Change |
|---|---|
| `src/auth/cleanReauth.ts` | **new** — `createRenewalFailureHandler(navigate)` |
| `src/auth/signInNotice.ts` | the union gains `'session-ended'`; `NOTICE_COPY_KEY` added with a two-directional completeness pin |
| `src/i18n/catalogue.ts` | `signIn.sessionEnded` = "Your session has ended. Sign in again." |
| `src/auth/SignInScreen.tsx` | renders the sentence for the notice that was set, instead of always rendering `discarded` |
| `src/auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.ts` | **new** — 27 cases |
| three closed suites | the `signIn` key-set pins updated, with justification |

## Four things the plan did not predict

**1. The key-set pin exists three times, not once.** The plan found one — T28's
`sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx:254` — and said so confidently.
There are **three**: T31's `identityErrorsIndistinguishableAcrossAccountStates.test.tsx:369` keeps its own
copy with a `toHaveLength(6)` beside it, and `callbackValidatesNonce.test.tsx:818` inlines the same six
names with no constant at all. One catalogue line broke all three.

**The cause was my own survey, not the repository.** The phase 5 plan's grep ended in `head -12` and the
third hit fell past the cut, and I wrote "nothing else in `src/` pins the catalogue's shape" from a
truncated list. A survey that is used as a completeness claim cannot be truncated. Each pin is now updated
where it lives, with the justification its own comment demands, and **T28's and T31's no-cause checks now
hold the new sentence to the same bar** rather than exempting it — strengthening what I had to touch.

This is also the sharpest measurement yet of the duplication OQ-T32-4 records: three independent copies of
one six-name enumeration across three closed tasks.

**2. The AC8 case left an uncaught exception, and that is the boundary's documented behaviour.** Driving a
throwing hook made the runner report "1 error" beside 26 passes.`sessionBoundary.ts:69-71` says it exactly:
the hook is scheduled in a microtask "so a throwing hook can't affect the waiters; **its error still
surfaces as uncaught**". A microtask throw reaches `uncaughtException`, which the runner attributes to the
file.

It would have been easy to delete the case or to stop throwing. Instead the case now **catches and counts**
the escape, after taking the runner's own handlers off for the duration and restoring them in a `finally`.
Both halves of that comment in `sessionBoundary.ts` are now assertions, so a change that swallowed the
error would fail here. A second case then proves the real handler never needs that containment: all three
locations a tab can die at — inside the SPA, already at the destination, outside the SPA — complete without
throwing.

**3. `SignInScreen` had to change, or AC5 would have been false.** The screen rendered
`t('signIn.discarded')` for **any** notice, so a `'session-ended'` notice would have displayed the sentence
G3 rejected as untrue. One line, and the mapping went into `signInNotice.ts` beside the union rather than
into the component — T31's A2 lesson applied before a reviewer had to ask.

**4. Nothing needed a branch for "already at the destination".** `rememberReturnTarget` applies the
allowlist itself and silently ignores a target it cannot honour (`returnTarget.ts:88-90`), and the
allowlist is the non-parameterised authenticated paths. So a tab already at `/app/sign-in` remembers
nothing, with no special case — and a tab on `/app/invoices/123` remembers nothing either, because T24-D5
excludes parameterised routes. Both are measured rather than assumed.

## Decisions taken while writing, with reasons

- **`clearAccessToken()` runs first.** Everything after it can throw. A notice set or a navigation begun
  while a token is still held is the half-authenticated tab R22 forbids, so the worst case is a cleared
  session that did not announce itself. A case drives a throwing `navigate` and asserts the token is gone.
- **The handler takes no parameter.** G11 becomes a type-level guarantee; a case asserts the arity so a
  later maintainer "fixing" the signature fails rather than silently removing the guarantee. Source cases
  also forbid `reason`, `catch` and `instanceof` in the module.
- **Route constants are read inside the function**, and a case asserts every use is indented. This is not
  style: `returnTarget.ts:40-49` measured in T16 that a module-scope read yields `undefined` rather than
  throwing, and an `undefined` allowlist **fails closed silently**.
- **The test drives the real boundary**, not a stub. AC2, AC3, AC7 and AC8 are statements about how the
  hook is *called*; a stub would prove only that the module can be called the way the test chose to.
- **The harness is the third copy** of the fake-transport/fake-session shape from tasks 5 and 6. Said out
  loud in the file rather than quietly repeated (Kimi F11).

## Gates

| Gate | Result |
|---|---|
| T33 cases | **27 passed** |
| `src/auth` + `src/i18n` | 1012 passed — the three closed suites green again |
| Whole suite | **1564 passed / 1 failed of 1565** — the known `oxlint` environmental case |
| `tsc --noEmit` | exit 0 |
| `oxlint src` | exit 0 |

**Phase 6 complete.** Phase 7 self-reviews and builds the mutation harness.
