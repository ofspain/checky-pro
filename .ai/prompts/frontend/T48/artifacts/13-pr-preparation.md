<!-- MODEL: Claude Opus 5 — Phase 13 (PR Preparation). No code. -->

# frontend · T48 · Phase 13 — PR Preparation

| | |
|---|---|
| **Consumes** | `artifacts/12-specification-verification.md` — **PASS** |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12 returned **PASS**, so this phase proceeds. `main` stays deployable.

> **Trailer note.** The phase prompt asks for a `Claude Opus 4.8 (1M context)` trailer. That name is stale —
> every commit in this task uses `Claude Opus 5`, and writing the prompt's literal string would be a false
> attribution. `.ai/` is generated, so the fix belongs in `.ai/generate.py`, not a hand-edit of the prompt.
> Raised identically at T47's Phase 13; **two tasks running now**, which is the argument for fixing the
> generator.

---

## Commit title

```
feat(frontend): verify an email from its link, and show the verified state (T48, R3)
```

## Commit message

```
feat(frontend): verify an email from its link, and show the verified state (T48, R3)

Attach a screen to `/app/verify`, a public path declared since task 16 with no
element, so every verification email the platform has sent has landed on a blank
document. The demo rehearsal found exactly that.

Read the one-time token from the `?token` query parameter, POST it once to
`/accounts/verify-email` through the generated public client, and show the verified
state on any 2xx. The token is a bearer-equivalent credential that happens to arrive
in a URL, so it is stored nowhere, logged nowhere, and rendered nowhere.

Guard the request with a ref set BEFORE the call and AFTER the empty-token return.
Before the call because StrictMode invokes effects twice and the token is burned on
first use, so a second POST turns a successful verification into a failure. After
the empty-token return because the guard must mean "a request has been made", not
"this effect has run" - otherwise a tokenless arrival latches it and a token
arriving later does nothing.

Move focus and verify in two separate effects. Focus is unconditional (L11);
verifying is not. A visitor who follows a broken link must still be told where they
are.

Leave every failure state to task 49. `idle` is deliberately one rendering for three
situations - in flight, no token, rejected - because R4 requires ONE uniform failure
message and inventing its copy here would pre-empt it. A case pins that collapse and
is written to fail when task 49 lands.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

---

## Files changed

**Production (3)**

| File | |
|---|---|
| `frontend/src/features/account/VerifyScreen.tsx` | **New, 88 lines** — ~55 of header over ~30 of code. Shared with task 49 by design. |
| `frontend/src/app/routes.tsx` | `VERIFY_PATH` added and substituted for the identical literal; the attach branch. |
| `frontend/src/i18n/catalogue.ts` | The `verify` group, with the announcement mechanism and OQ-T48-5 recorded. |

**Tests (7)**

| File | |
|---|---|
| `…/verifyFromLinkShowsSuccessState.test.tsx` | **New, 32 cases, 576 lines** — the named suite `package.md` §8 requires. |
| `app/routesResolveUnderAppPrefixOnly.test.tsx` | `SCREENS_ATTACHED_SO_FAR` in **route order**; a transport stub for the emailed-link case; `vi.unstubAllGlobals()` in teardown. |
| `app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | Four pins: render sites 9→10, the length, the union 22→23, the catalogue keys. |
| `app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | Six attached screens → seven. |
| `app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` | A source-text pin on the union. |
| `auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` | Two source-text pins, and the "N of the eight have no element" count → three. |
| `features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | Two source-text pins. **D-T48-1** — a T43 suite the freeze forbade; ratified. |

**Artifacts (18)** — `.ai/prompts/frontend/T48/artifacts/00` … `13`, including four mutation-run logs and the
harness.

`frontend/: 10 files changed, 773 insertions(+), 17 deletions(-)` over `63ac0e20..HEAD`.

---

## Summary

`/app/verify` has been a declared public path with no element since task 16. This task fills it, which makes
the verification email in the platform's own smoke test end in a screen rather than a blank page.

The screen is small. What took the work was that **almost everything about it is a guard**, and three of
those guards were being asserted by prose rather than by tests until the mutation harness said so:

1. **The one-request guard had a comment where it needed a case.** `VerifyScreen`'s header states the guard
   means *"a request has been made, not this effect has run."* Nothing measured that, because every case
   started in its final state — the difference only appears on a screen that goes from no token to a token
   **without remounting**. A mutant that latched the guard on a tokenless arrival survived all 2042 tests.
2. **The brief was doing an assertion's job.** Its Constraints said the token is *"not stored, not logged,
   not sent to analytics, not rendered"* — and **only the first had an instrument**. The token was absent
   from the success DOM only *incidentally*; `idle`, where a visitor waits while the request is in flight,
   was unchecked.
3. **A behavioural check was narrower than its own claim.** The log assertion named five `console` methods
   while nine exist, so `console.table(token)` would have leaked a credential while the case passed. The spy
   list is now derived from `console` at runtime, **and the derivation is itself asserted**, because a
   reflection that comes back empty makes a negative assertion vacuous.

A fourth thing is worth a reviewer's attention more than the screen is. **R-T48-1:** the POST runs on
arrival, which R3's wording requires, and `markConsumed` is durable — so an email gateway that executes
JavaScript spends the token before the human clicks. The account really is verified; the human sees task 49's
uniform failure; and **R4 forbids task 49 from explaining it.** Measured against the backend rather than
assumed, decided by the owner at the Phase 4 gate, and documented in the one effect a mitigation would
change.

## Testing performed

| | |
|---|---|
| Whole suite | **2048 / 2048**, 39 files |
| Named suite | **32 / 32** |
| `oxlint` / `tsc --noEmit` | 0 / 0 |
| Mutation harness | **22/22 regressions killed**, 2/2 canaries dead, `survivors: ['TR20']`, **0 broken, 0 disagreements** |
| Per-file runs | all seven touched files green **alone** — see below |
| Independent | Kimi 2.7 re-ran the harness at Phase 8 and Phase 11; its first attempt was **refused by the baseline guard** over the FU-T42-1 flake, which is the guard working on another machine |
| Runs | Harness executed four times, across Phases 7, 9, 10 and 11; each run changed something |

**The per-file runs are not ceremony.** Phase 5 measured an unhandled rejection that **fails a whole file
while every assertion in it passes**, and that the whole-suite run **masks**, because another file's global
stub leaks into the shared environment. `pnpm test` cannot see it.

**Declared unfalsifiable, at the point of use rather than hidden in a score:** `TR20`
(`useEffect` → `useLayoutEffect`, no observable difference) and the `POST` assertion (openapi-fetch exposes
the verb as the method name, so a mutant changing it cannot typecheck — and a mutant that cannot compile
measures nothing). No analytics assertion was written at all, because the SPA has no analytics client.

## Specification references

| | |
|---|---|
| **Task** | `spec/frontend/tasks.md` **T48** — verify success state |
| **Requirement** | **R3** (`requirements.md:14-15`; backend R4) |
| **LOCKED** | **L4** enumeration-safe UI *(among failure reasons)* · **L6** generated client only · **L9** single origin · **L11** accessibility, i18n, managed focus, catalogue-only copy · **L14** SPA route set under `/app`, `?token=<token>` author-confirmed at `design.md:145` · **L16** the only sessionStorage carve-out · **L17** service-worker boundaries |
| **Design** | **D7** every rendered catalogue key is measured |
| **Deviations** | D1, D2, D3, D-T48-1, D-T48-2 — all ratified; see Phase 9 and Phase 12 |
| **Blocks** | **task 49** is blocked on **OQ-T48-4**: `auth.yaml` declares `204` and nothing else for `verifyEmail`, so R4's uniform failure cannot be specified from the contract |
| **Open for the owner** | **R-T48-1** (the gateway hazard), **OQ-T48-5** (the heading after success), **FU-T48-1** (closed-task count pins), **FU-T48-2** (a false comment in `tokensAreNeverWrittenToAnyStorage`), **FU-T43-2** (no shared test utils) |

**Phase 13 complete. T48 is ready for merge.**
