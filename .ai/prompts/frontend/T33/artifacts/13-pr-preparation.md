<!-- MODEL: Claude Opus 5 — Phase 13 (PR / Commit Preparation) -->

# frontend · T33 · Phase 13 — PR / Commit Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T33 — Clean re-auth on failed renewal |
| **Consumes** | `artifacts/12-specification-verification.md` — verdict **PASS** |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12's verdict is **PASS**, so this phase proceeds. Branch: `spec/service-specs-and-ai-framework`
(the package's working branch, cut from `main`); `main` stays deployable.

## Commit title

```
feat(frontend): clean re-auth on failed renewal, with the dormancy pinned (T33)
```

## Commit message

```
feat(frontend): clean re-auth on failed renewal, with the dormancy pinned (T33)

Give the session boundary a renewal-failure handler that leaves no
half-session: drop the held token first, remember where the visitor was
if the allowlist can honour it, set a neutral notice, and navigate to the
public sign-in route. The order is load-bearing - a notice set or a
navigation started while a token is still held is the half-authenticated
tab R22 forbids - so the clear comes before any step that can throw, and
two cases prove it with a throwing remember and a throwing setter.

Land on /app/sign-in rather than /app/signed-out or an automatic
/oauth2/authorize. /app/signed-out is R19's deliberate-sign-out landing
and would state something false; re-entering the authorize endpoint is
the error loop R21 forbids, and without prompt=none it would drop the
visitor into the hosted login page mid-task. Re-auth is offered, by
consent, on a screen that can start it.

Add a sentence rather than reuse the discarded one. "Sign-in did not
complete" is false for a visitor whose session ended hours after signing
in, and L4 does not require the reuse: L4 makes copy identical across
account states and token conditions, and the visitor's own session having
ended discloses no account. The notice union and the table keyed by it
live in one module, so a third member is a compile error there rather
than a missing branch in a component.

The failure reason is unreachable by construction. The handler takes no
parameter, so there is nothing to branch on; a local failure and a
sibling tab's failure give byte-identical outcomes; and source scans
forbid naming a reason, binding a caught error, importing a router, or
assigning location, so the injected navigate cannot be quietly bypassed.

What this commit cannot do is make the behaviour run. Nothing in the SPA
constructs the session boundary, and wiring this task would not change
that - no authenticated request is issued anywhere in src/, so no 401
arrives and no renewal is attempted. Tasks 5, 6, 30, 31 and 33 are built
and dormant together, and no unit of tasks.md owns the wiring. AC12 pins
that fact: the day a production module constructs the boundary, a case
fails and hands its author the instruction.

Three closed suites' signIn key-set pins were extended, each where its
own comment asks for that justification, and two of their no-cause
checks were strengthened to hold the new sentence rather than exempt it.

Verifies failedRenewalTriggersCleanReauthWithoutHalfSession. Cites R22.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

> **Trailer note, carried forward for the sixteenth consecutive task.** The phase 13 prompt mandates the
> `Claude Opus 4.8 (1M context)` trailer above, and it is reproduced verbatim because the prompt is the
> contract. It is stale: this work ran on Opus 5, and the repository's commits use
> `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`. The single source is `.ai/generate.py:476`;
> fixing it there corrects every task's phase 13 at once. Unchanged because the generator is not T33's to
> edit.

## Files changed

**Production (4 files, one new):**

| File | Change |
|---|---|
| `frontend/src/auth/cleanReauth.ts` | **new**, 90 lines — `createRenewalFailureHandler`, `inAppPath` |
| `frontend/src/auth/signInNotice.ts` | the union gains `'session-ended'`; `NOTICE_COPY_KEY` added with a `satisfies` pin and an `AssertNever` guard from the other direction |
| `frontend/src/i18n/catalogue.ts` | `signIn.sessionEnded` added, with why it names the event and not the cause |
| `frontend/src/auth/SignInScreen.tsx` | one line — `t('signIn.discarded')` becomes `t(NOTICE_COPY_KEY[notice])` |

**Tests (4 files, one new):**

| File | Change |
|---|---|
| `frontend/src/auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx` | **new**, 815 lines, 37 cases over AC1–AC12 |
| `frontend/src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx` | key-set pin extended to seven; the no-cause check strengthened to hold the new sentence |
| `frontend/src/auth/sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx` | same two changes |
| `frontend/src/auth/callbackValidatesNonce.test.tsx` | inline key enumeration extended |

**Pipeline artifacts:** `.ai/prompts/frontend/T33/artifacts/00`–`13`, including
`07-mutation-harness.py` (24 mutants). No file under `spec/` was modified.

## Summary

A failed renewal now ends in a tab that holds no token, shows a neutral sentence naming only the event, and
sits on the public sign-in route with its pre-interruption location remembered if the allowlist can honour
it. The handler is injected into the existing session boundary rather than reaching for the router, takes no
failure reason, and is proven identical for a local failure and a sibling tab's.

The one thing a reviewer should not miss: **this is correct and unreachable.** The task is complete at the
module level and dormant in the application, for reasons outside its own reach, and that is disclosed in the
module header, in the test file, in a pinned canary, and in phase 12's deviation D6.

## Testing performed

| Gate | Result |
|---|---|
| T33 cases | **37 passed**, exit 0 |
| whole suite | **1574 passed / 1 failed of 1575** — the failure is `src/api/lintRejectsHandWrittenBackendFetch.test.ts`, pre-existing and environmental on this host (`oxlint@1.87.0` prints no summary line for the assertion that parses it) |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0**, no output |
| mutation | **22/22 regressions killed · 2/2 canaries killed · 0 survivors · 0 broken** |

Each mutant's failure count is recorded, not just its verdict, so a kill through the wrong instrument is
visible. The two canaries — an authenticated route gaining an element, and a production module constructing
the boundary — describe changes elsewhere and are scored apart from the regression count.

Adversarial review: **Kimi 2.7** at phases 3, 8 and 11, with 12 findings at phase 11 of which 8 went into
code (F1–F7, F9), one was answered with a correction (F10 — the `finally` does restore, and a test error
propagates normally), one was already satisfied by phase 10's static assertion (F8), and two were held as
recorded debt (F11, F12). Phases 4 and 9 passed the Human Approval gates.

## Specification references

- **Task:** `spec/frontend/tasks.md` task **33** — "Clean re-auth on failed renewal."
- **Requirement:** **R22** (`requirements.md:74-76`); its refresh-reuse half was removed by Q5, 2026-10-10.
- **Adjacent, not regressed:** **R21** (one renewal per tab, no error loop), **R19** (deliberate sign-out
  landing), **R8** (the discarded-callback notice this task sits beside).
- **LOCKED decisions** (`design.md` §4a, derived at phase 1): **L1** allowlisted return target · **L3** and
  **L16** nothing stored outside the carve-out · **L4** enumeration-safe copy · **L11** catalogue-only
  strings · **L13** no secret in the bundle · **L14** the declared route set · **L18** cross-tab coherence.
- **Named test:** `failedRenewalTriggersCleanReauthWithoutHalfSession` (`package.md` §8).
- **Not this task:** `signOutPropagatesToAllTabs` is task 39 (`tasks.md:67`);
  `reusedRefreshOrFailedRenewalTriggersCleanReauth` belongs to the struck-out task at `tasks.md:62` —
  `package.md:171` still maps it to R22, which is spec drift recorded at phase 12 and not fixed here.

## Open questions carried out of T33

- **OQ-T33-2 (Blocker, owner's).** Tasks 5, 6, 30, 31 and 33 are all built and nothing calls any of them.
  Is the dormancy intended until tasks 173/174, or is a wiring task missing from `tasks.md`?
- **OQ-T32-4.** Helper duplication, now the most-raised item in the package: the boundary harness is a third
  copy, the `signIn` key-set enumeration lives in three closed suites, and the scan helpers are duplicated
  per instrument file. Raised independently by six reviews across two tasks.
- **Spec drift**, recorded not fixed: `package.md:171`'s R22 mapping for a struck-out task, and
  `package.md` §11's Q5 not struck through although `requirements.md:69-76` records it resolved.

**Phase 13 complete. T33 closed.**
