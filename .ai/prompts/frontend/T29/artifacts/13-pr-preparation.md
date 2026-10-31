# frontend · T29 · Phase 13 — PR Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T29 — Uniform sign-in failure |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/12-specification-verification.md` (**PASS**) |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12 returned **PASS**. T29 is ready to merge.

---

## Commit title

```
test(frontend): prove every sign-in failure is indistinguishable across account states (T29)
```

## Commit message

```
test(frontend): prove every sign-in failure is indistinguishable across account states (T29)

Verification-only. No production file changes, and that is the finding:
the uniformity R30 demands is already structural - CallbackScreen funnels
all five account states through a single discard() closure - so T29's job
was to prove it and to pin it against the day someone adds a branch.

Five account states are driven as hostile fixtures at the SPA boundary,
each with its own error code, description and help URI, and every
SPA-observable consequence is compared as one struct: destination,
history action, query, fragment, rendered text, live-region text, live
region count and SHAPE, keyboard focus, DOM skeleton, stored keys and
whether a token is held. The distinctness block runs first, because five
fixtures that were secretly one would satisfy everything after it while
proving nothing.

Four clauses needed instruments rather than assertions:

- The sentence must be present with a FAKE CLOCK AT ZERO, for every
  state. No earlier file in the package asserts the SPA's own rendering
  is untimed by cause, and a delay keyed on the failure code is the one
  way five outcomes stay structurally identical while remaining
  distinguishable to a watching user.
- No production module may hold an unexplained timer, proven over the
  whole comment-stripped tree against a three-row allowlist that is an
  upper AND lower bound, so a deleted timer cannot leave budget behind.
- No production module and no catalogue string may name an account
  state, with the vocabulary split into a globally forbidden tier and a
  sign-in-scoped tier, and the split held from both directions.
- Nothing may gate what a failed sign-in renders on an account state or
  a role.

Several things were learned the hard way and are worth carrying:

- A regex comment-stripper eats string literals. The old one deleted
  from the first // to end of line regardless of context, so
  'https://id.example/help/locked-account' became 'https:' and the word
  left the source before any scan saw it. A URL is the one shape where
  this happens, and a help URL naming the state is the most plausible
  leak. Replaced with a literal-aware scanner, measured byte-identical
  on all 24 production modules.
- URLSearchParams encodes a space as '+', so a leak sweep comparing raw
  text to raw needle reads CLEAN on a description surviving in a URL -
  which is where the description arrives in the first place. Every
  surface is now searched raw and decoded.
- A struct field catches five outcomes that DIFFER and nothing else.
  Five that moved together compare equal and pass, so search, hash,
  live-region shape, focus and the DOM skeleton are each ALSO asserted
  absolutely. Three mutants were unkillable until those pins existed.
- A mutation harness must assert its own baseline. One run read 3
  failures instead of 1 and printed the two mutants producing exactly
  three failures as SURVIVORS. The harness now refuses to run on an
  unexpected floor and exits non-zero on any survivor.

56 cases, 32 of them controls - every instrument is shown to report
DIRTY before it is trusted to report clean, and a scan that reads
nothing is refused rather than reported clean. 28 mutants, 28 killed,
all by this file.

Spec: spec/frontend/ task 29. Requirements R10, R30. LOCKED decisions
L4, L11, L16. agents.md enumeration rule.

Deviations: T29-D1 the five states are driven at the SPA boundary, not
through SAS, whose /login page no jsdom suite can reach; T29-D2 the
three token states are task 59's, and the composition root mounts
neither sessionBoundary nor rateLimitNotice; T29-D3 the visual-treatment
claim is a pin for task 173; T29-D4 the phase 8 fixes landed in phase 9
rather than phase 10.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

> **Template drift, tenth consecutive task.** `13-pr-preparation.md` still specifies a
> `Claude Opus 4.8 (1M context)` trailer. The trailer above follows the session's standing attribution rule
> instead. The fix belongs in `.ai/generate.py`, not in any task's artifacts.

## Files changed

| File | Change | Lines |
|---|---|---|
| `frontend/src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx` | **new** | 1921 |
| `.ai/prompts/frontend/T29/artifacts/*` | **new** — fourteen pipeline artifacts | 2652 |

**No production file is modified.** `git diff --name-only` over `frontend/src` excluding tests is empty.

## Summary

T29 verifies `identityErrorsIndistinguishableAcrossAccountStates` on the **sign-in/callback axis**. Five
account states — `unknown`, `missing`, `locked`, `suspended`, `deleted` — are driven as adversarial fixtures
carrying five distinct error codes, descriptions and help URIs, and are proven to produce one identical
outcome, one sentence, one destination, one announcement, one element tree, and no surviving trace of the
cause on any of six surfaces or six console channels.

R30 names four groups; T29 drives five, splitting "unknown or missing" in two. That is stricter than the
requirement on purpose: the two reach the backend by different routes and collapse to the same refusal, and
proving they are indistinguishable is worth more than assuming it.

Eleven acceptance criteria, each with at least one control proving its instrument reports DIRTY first. The
file is 32 controls to 21 claims, which is the shape an absence task takes.

## Testing performed

| | |
|---|---|
| `tsc --noEmit` | exit 0 |
| `oxlint src` | clean |
| `vitest run` on the file | **56 / 56**, exit 0 |
| `vitest run` whole suite | **1398 passed / 1 failed (1399)** |
| The one failure | `src/api/lintRejectsHandWrittenBackendFetch.test.ts` — pre-existing and environmental; this host's `oxlint@1.87.0` prints no summary line for it to parse |
| Mutation | `python3 -I .ai/prompts/frontend/T29/artifacts/07-mutation-harness.py` — **28 mutants, 28 killed, no survivors**, all by T29's own file, against a guarded baseline of `1 failed of 1399` |

Three mutants deserve naming. **MC11** is the subtlest in the package: `setTimeout(discard, 0)`, identical on
a real clock and different only under a fake one held at zero. **MC15** is written to escape, in T26's, T27's
and T28's tradition — a delay that fires only for an error code no test drives, killed by the enumeration
rather than by any single case. **MC18** leaks one fixture's description, percent-encoded, to `window.name`,
and is the only mutant that isolates the encoding normalisation; reaching it took two wrong claims of mine,
both corrected in the harness header.

## Specification references

| | |
|---|---|
| **Task** | `spec/frontend/tasks.md` task 29 — *Uniform sign-in failure* |
| **Requirements** | **R10** (one uniform message for any SAS login failure, including an unenrolled MERCHANT/ADMIN, with copy identical to a wrong password, never naming enrollment); **R30** (copy, redirect target and visual treatment identical across account states and token states) |
| **LOCKED decisions** | **L4** (no copy distinguishes one failure from another), **L11** (keyboard operation and a live region), **L16** (the three stored items are read back and cleared) |
| **Standing rules** | `spec/frontend/agents.md` — the enumeration rule |
| **Named test** | `identityErrorsIndistinguishableAcrossAccountStates` |

## Branch

Work is on `spec/service-specs-and-ai-framework`, branched off `main`. `main` stays deployable — nothing in
T29 can affect it, since no production file changes.

## Carry-forward, not T29's

`oidcRoundTripEndToEnd` (task 134) has no suite and no owning task — the largest verification gap in the
package, and the reason T29-D1 and T28-D1 both exist. The composition root `mountShell.tsx` is unowned. L16
clause (b) is unbuilt and unowned. `contracts/api/token-claims.md` is wrong on `email`/`name` and silent on
`amr`/`acr`. **OQ1** stands: the live SAS `/login` renders no `mfaCode` field while the backend requires it
in the same POST. The phase 13 template's stale trailer, as above.

---

**T29 COMPLETE.** Phase 12 **PASS**, ready to merge.
