<!-- MODEL: Claude Opus 5 — Phase 7 (Self-Review). -->

# frontend · T49 · Phase 7 — Self-Review

| | |
|---|---|
| **Consumes** | `artifacts/06-implementation.md` |
| **Produces** | `artifacts/07-self-review.md`, `07-mutation-harness.py`, `07-mutation-run.txt` |

```
baseline whole suite: 0 failed of 2083, exit 0

regressions: 17/17 killed
canaries:    ['TR19'] - none survived
survivors:   ['TR17', 'TR20']   (both declared)
broken:      none
disagreements: none
```

`lint` 0 · `typecheck` 0 · named suite 35 cases · each touched file green alone.

---

## Why the rows look the way they do

**R4 is a uniformity requirement, and uniformity is the hardest kind of claim to mutate against.** The
obvious mutant — *show a different message for an expired token* — **cannot be written**: the backend
collapses invalid, expired, used and deleted-account into one fixed `400` before the SPA sees any of them, so
there is nothing in the response to branch on. The rows therefore cluster around the five ways this screen
could stop being uniform or stop being a failure screen at all: **the failure existing** (TR01–TR05), **the
flash** (TR06–TR07), **the action** (TR08–TR12), **the body** (TR13–TR14), and **the credential** (TR15–TR16).

## Findings

### F1 — TR16 survived 2083 cases and measured nothing · **the row was wrong, not the instrument**

First run: `SURVIVED (0 failed of 2083)`. The row logs the token through an aliased `console` — written to
evade the literal `console.` ban on purpose — and it was anchored to the **tokenless branch**, where

```tsx
const token = params.get('token');
if (token === null || token === '') {   // ← token is null or '' BY DEFINITION here
```

**So the mutant could not leak a credential even in principle**, and the console case, which arrives *with* a
token, never executed that branch. Re-aimed at the failure settle path, where the real token is in closure
scope, and **measured by hand before trusting the harness: it fails in exactly one case.**

The instrument was never the problem. This is *a mutant must isolate the hole it records* in its sharpest
form — the row recorded a hole **that cannot exist**, and its survival looked exactly like a gap.

**Left undetected deliberately, and recorded rather than built for:** an aliased console call placed in the
tokenless branch evades both the scan and the spy, and leaks nothing, because in that branch there is no
token.

### F2 — TR17's "kill" was the FU-T42-1 flake, caught by NAME and not by count · **third sighting**

Run 2 reported `TR17 … KILLED/inherited (1 failed of 2083)`. Taken at face value that is a finding — *the
constant versus the identical literal IS measurable after all*. It is not:

```
TR17  └ in: ['src/auth/signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx']
```

**A sign-in suite, which has nothing to do with a resend link's path constant.** That is the file FU-T42-1 is
already documented in, and the mechanism measured in T44 says the flake *can only invert a row with no
genuine kill of its own* — which is precisely TR17, since the constant and the literal are the same string.
Run 3: `TR17 … SURVIVED, as expected`.

**This is why this project's rule is that a raw failure count is not a basis for comparing two harness runs
and only the named killers are.** Had I read the count, Phase 7 would have shipped a false finding.

### F3 — TR17 needed declaring in **both** places the harness reads a declaration

The docstring said *"DECLARED: survives"*; the harness cannot read prose, and the row was tagged `own`, so
run 1 reported a disagreement and was right to. Adding `TR17` to `UNFALSIFIABLE` fixed **half** of it — the
expectation comes from the row's own owner tag — so run 2 reported the same disagreement for the same reason.
It took a second run to learn that **a declaration has to be made everywhere the harness looks for one.**

### F4 — the harness copy dropped three imports · the same splice class as T48's

My header replacement sliced from the docstring to `import subprocess` and took `os`, `re` and `signal` with
the prose. It crashed in `install_signal_reverts` **before mutating anything**, so the tree stayed clean. T48's
copy lost `FRONTEND = REPO / "frontend"` to the same manoeuvre. **Splicing a file by two string anchors takes
whatever is between them, and in a copied harness that is never only prose.** Worth a note in the next copy.

### F5 — nothing will fail when task 50 attaches the resend element → **for Phase 10**

The resend link is live and its destination is element-less until task 50, which the owner accepted as a
known interim state. But **no instrument anywhere will fail when that element lands**, so nothing prompts
anyone to re-check this screen's link.

Phase 5's measurement is the guide for how to fix that: T48's **labelled** handoff case did not fire, while
two unlabelled **equality** assertions did. So the instrument should be a positive claim that inverts — *the
resend target currently renders no element* — not a sameness. **Recorded for Phase 10 rather than built here**,
because test generation is that phase's job.

### F6 — three claims checked because they would otherwise rest on reasoning

| Claim | How it was checked |
|---|---|
| `setStatus('idle')` is reachable, not redundant | Removed it: the step-5 case fails, **1 of 35** |
| Nothing reads the response body | Added `response.status === 418`: the scan fires, **1 of 35** |
| Every anchor matches real code, not a comment | All 20 verified to resolve **exactly once** before each run |

## Deviations

| ID | Status |
|---|---|
| **D-T49-1** | `routes.tsx` modified for the constant. **Ratified at Phase 4.** |
| **D-T49-2** | A tokenless arrival shows the uniform failure. **Ratified at Phase 4**, reversing Kimi's Phase 3. |
| **D-T49-3** | `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` modified — its catalogue key-set pin fires on any new key, and the freeze's render-site measurement was blind to it in the same file. **Needs ratification at Phase 9.** |
| **D-T49-4** | Catalogue keys flat, not nested: the nested form **crashes** the copy scan. **Needs ratification at Phase 9.** |

## Declared survivors

| Row | |
|---|---|
| **TR17** | The path constant swapped for the identical bare literal. **Unfalsifiable behaviourally** — the same string, so nothing can tell them apart *while they agree*. **TR18, the rename, is what measures what the constant buys**, and it died in three files: the two route-set value pins and task 48's suite. |
| **TR20** | `useEffect` → `useLayoutEffect` for the focus move, inherited from T48. A kill would mean a case had started pinning the hook rather than the behaviour. A **two-part edit**, because one would not compile and a mutant that cannot typecheck measures nothing. |

## Open

**OQ-T49-6** (new, F5) · **OQ-T49-3a** · **OQ-T48-4** · **R-T48-1** (inherited consequence) · **FU-T48-1** ·
**FU-T48-2**.

**Phase 7 complete. Next is Phase 8 — Independent Review (Kimi 2.7). STOP HERE.**
