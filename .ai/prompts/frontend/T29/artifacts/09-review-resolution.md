# frontend · T29 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T29 — Uniform sign-in failure |
| **Model** | Human Approval (packet assembled by Claude Opus 5) |
| **Consumes** | `artifacts/07-self-review.md` + `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

Seven Kimi findings, I1–I7. **Four accepted outright** (I2, I3, I5, I7), **two accepted in substance but not
in the form recommended** (I1, I4), **one rejected on a measurement** (I6). Every cited `file:line` was
checked against source before disposition, and the two that turn on a claim about the *production* tree —
I1's suggested patterns and I6's five role variants — were settled by running the scan, not by reading it.

**The fixes are already applied, committed and pushed** (`6de82f4a`, 46 → **53 cases**), and four mutants
were added for them (`83ac84e8`, MC19–MC22). That is a departure from T28's ordering, where Phase 9 decided
and Phase 10 applied — recorded below as **T29-D4**. Phase 10 therefore verifies rather than implements.

Kimi's own note is worth carrying: it could not run `vitest` (no `vite-plugin-pwa` in its environment), so
**all seven findings are static**. Two of them were wrong in exactly the way a static reading goes wrong — by
naming a pattern without checking what else in the tree it would match.

---

## I3 — ACCEPTED. The one real instrument defect, and it was found by reading

Kimi is right, and this is the most valuable finding in the review. `strip()` was two regexes:

```ts
return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
```

The second deletes from the first `//` to end of line **regardless of context**. So the literal
`'https://id.example/help/locked-account'` became `'https:` — and the word `locked` left the source before
any scan could see it. Every source scan in the file (AC6's timer sweep, AC7's state sweep, AC9's gating
sweep) runs on stripped code, so a help URL carrying a state word in its path was a hole in all three.

What makes it serious is the *shape*: a URL is the one place `//` appears in ordinary code, and a help URL
naming the account state is precisely how this leak would most plausibly arrive. The AC7 control proved only
that a state word in a **comment** was removed; nothing proved a word in a **literal** was kept.

**Fixed** with a single-pass scanner (`:353`) that walks the source and copies string and template literals
through verbatim, escapes included, removing only comments found outside them. **Measured against all 24
production modules: byte-identical output.** No current scan result changes — the fix is for the file not yet
written. `MC19` is the regression guard: a state word inside a URL string literal, which the old stripper ate
and the new one surfaces. A second control shows comments are still removed, including ones containing a
quote, because a literal-aware scanner is exactly where that regression would hide.

## I1 — ACCEPTED IN SUBSTANCE, REFUSED IN FORM

The gap is real: `account.state === 'LOCKED'` is at least as natural as `accountState`, and nothing caught
it. But **the patterns Kimi recommended cannot be adopted as written.** `\.state\s*[=!]==` and
`\.status\s*[=!]==` were run against the production tree:

| Hit | File | Why it is legitimate |
|---|---|---|
| `installing.state === 'installed'` | `api/registerServiceWorker.ts` | Workbox's own service-worker lifecycle |
| `response.status === 429` | `api/rateLimitNotice.ts` | an HTTP status — **R23 requires reading it** |
| `response.status === 401` | `api/sessionBoundary.ts` | an HTTP status — **R21 requires reading it** |

Adopted as recommended, AC9 would have failed on the day it landed, on three files the spec commissions. So
the alternatives key on the **receiver** as well as the property (`:301`):

```
|(?:account|user|session)\.(?:state|status)\s*[=!]==
|(?:account|user|session)\.locked\b
```

It is *the account's* state that may not be branched on, not every object's. Two of Kimi's five shapes needed
nothing: `switch (accountState)` was already caught by the bare `accountState` alternative, and
`AccountState.Locked` by `isLocked`-adjacent alternatives — controls now say so explicitly rather than
leaving a future reader to re-derive it. `MC20` gates on `account.status` and is killed by its own file.

## I2 — ACCEPTED

Accurate as cited: AC5's first case checked the sentence at clock zero for all five states, the second
checked the *redirect* at clock zero for `locked` only. The hazard Kimi describes is real and specific —
`discard()` sets the notice and navigates in one closure, so a `setTimeout(navigate, …)` would leave the
sentence at zero for all five and defer only the navigation, which no case would have seen for four of them.
Both halves now loop the five states. `MC21` defers the navigation alone, for one state only, and is killed.

## I5 — ACCEPTED

A catalogue key naming the *concept* with an innocent value today is a slot waiting to be filled tomorrow.
Added to `FORBIDDEN_STATE_KEY` (`:239`): `accountState`, `accountStatus`, `account_status`, `lockoutReason`,
`lockedReason`, `suspensionReason`, `deletionReason`. Deliberately **not** a bare `status`, which would flag
`invoice.status` — a commissioned, non-sign-in surface. `MC22` plants `signIn.accountState` with a uniform
value and is killed.

## I7 — ACCEPTED

Correct that the absolute `outcome.search === ''` was load-bearing with nothing pinning it. A control now
builds five outcomes carrying the **same** non-empty query string and shows the struct comparison passes
while the absolute assertion fails. The assertion can no longer be deleted as redundant. This is T28's MB29
lesson, pinned in T29's own file rather than cited from T28's.

## I4 — ACCEPTED IN PART, SPLIT ACROSS THE TWO TIERS

Nine words proposed. Two are globally safe and went into `ALWAYS_FORBIDDEN_STATE` (`:191`): `nonexistent`,
`unrecogni[sz]ed`. Five went into `SIGN_IN_SCOPED_STATE` (`:207`) instead, because each is commissioned copy
somewhere else in the package:

| Word | Why it cannot be global |
|---|---|
| `limited` | rate-limit copy (**R23**) |
| `flagged`, `restricted` | the `HELD` compliance path (**R56**) |
| `breached`, `compromised` | password vocabulary — R17 forbids the *client* doing the lookup, not the server saying so |

All are word-bounded, so `unlimited` and `unrestricted` do not match. **`shadow` stays rejected**: it is a
word with too many innocent uses (shadow DOM, box-shadow) to carry a one-word pattern, and the concept it
names would arrive spelled `banned` or `suspended`, both of which are already global.

## I6 — REJECTED, on a measurement

Kimi lists five role-gate variants as uncovered. **Four of the five are already caught**, which running the
scan shows and reading the pattern does not:

- `roles.some((r) => r === 'MERCHANT')` and `roles.find(…)` — the `=== 'MERCHANT'` alternative matches
  **inside the arrow body**. The array method is irrelevant.
- `user.permissions.includes('MERCHANT')` — `\.includes\s*\( *['"](?:MERCHANT|ADMIN)` does not care what the
  array is called.
- `account.type === 'MERCHANT'` — same `=== 'MERCHANT'` alternative.
- `account.roleName === 'MERCHANT'` — caught twice over, by `role` and by the literal.

And the recommended fix, a bare `\.some\s*\(|\.find\s*\(`, would fail on ordinary array code anywhere in the
tree — it is not a gate pattern at all. A control now records the measurement, so the next reviewer who
reaches for this finding finds the answer instead of re-opening it.

---

## What the review did not find, and the harness did

Running the mutants for I1/I2/I5/I3 exposed a defect in the **harness**, not in the test file. One run read
`baseline whole suite: 3 failed of 1396` instead of 1. Because every verdict is `failed > base_failed`, the
two mutants that produce exactly three failures — **MC13** (a timer inside an auth module) and **MC14** (the
description written to `document.title`) — were printed as **SURVIVORS**. They are not. Re-measured against a
correct baseline, both are KILLED, and the clean baseline of `1 failed of 1396` was reproduced four times
(two direct suite runs, two baseline-only harness runs) on a `git status --porcelain` clean tree.

A harness whose floor can drift silently converts a false negative into a published mutation score, which is
worse than no score. It now asserts `EXPECTED_BASELINE_FAILURES = 1`, asserts T29's own file is green, and
**refuses to run** rather than report against a wrong floor. It also exits non-zero when any mutant survives,
so a survivor cannot be missed in scrollback.

**Final score, against the guarded baseline: 22 mutants, 22 killed, no survivors** — all 22 by T29's own
file, none inherited-only.

## Verification at the gate

| | |
|---|---|
| `tsc --noEmit` | exit 0 |
| `oxlint src` | clean |
| The file alone | **53 / 53**, exit 0 |
| Whole suite | **1395 passed / 1 failed (1396)** — `src/api/lintRejectsHandWrittenBackendFetch.test.ts`, pre-existing and environmental |
| Mutation | **22 / 22 killed** |
| `git diff --name-only` production | **empty — no production change** |

The file is 1730 lines, 53 cases, thirteen `describe` blocks. Both reviewers state independently that no
production change is indicated, and none was made.

## Deviations

Unchanged from the frozen brief: **T29-D1** (the five states are driven as adversarial SPA-boundary fixtures,
not through SAS, which has no suite — `oidcRoundTripEndToEnd`, task 134, is unowned); **T29-D2** (the three
token states are out of scope — task 59 owns them, and the composition root mounts neither `sessionBoundary`
nor `rateLimitNotice`); **T29-D3** (the visual-treatment claim is a pin for task 173).

**T29-D4, new.** The Phase 8 fixes were applied, committed and pushed *during* Phase 9 rather than in
Phase 10, so this artifact records decisions already carried out. Nothing is resolved here that the committed
file does not already reflect; Phase 10 verifies instead of implementing.

## Scope confirmation

- **Files to create:** none.
- **Files modified:** `frontend/src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx` and
  `.ai/prompts/frontend/T29/artifacts/07-mutation-harness.py`.
- **Files NOT to modify:** unchanged from the frozen brief. No production change, confirmed by both reviews
  and by `git diff`.

---

**Phase 9 complete. Approved to proceed.** Four findings accepted, two accepted with their recommended form
refused on measurement, one rejected on measurement; all dispositions are in the committed file. Phase 10
verifies the full instrument set against the guarded harness.
