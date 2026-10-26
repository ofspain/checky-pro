# frontend · T18 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T18 — Mismatched state discarded |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 18):**
> **Mismatched state discarded.** A callback whose `state` differs from the stored value is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

---

## What was built

| File | Change |
|---|---|
| `src/auth/CallbackScreen.tsx` | the comparison (82 → **102** lines); the T18-D9 comment correction; the class doc rewritten, because it claimed the screen "reads nothing from storage" and that is no longer true |
| `src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` | 606 → **882** lines, 73 → **126** cases |

The production change is four lines:

```ts
const stored = globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state);
if (stored === null || stored === '' || stored !== state) {
  discard();
  return;
}
```

All eleven locked decisions T18-D1…T18-D11 are implemented as frozen, except that **T18-D11's prediction was
wrong** — see below. Nothing else deviated.

## T18-D11 was wrong, and the artifact says so

T18-D11 froze the regression obligation as "**exactly one** existing case is expected to change: `:570`… Any
other change to an existing case is a defect in this task."

**Eight existing cases failed.** They were:

| Case | Why |
|---|---|
| `but the seam IS reached when the callback carries a state and a code` | drives the success path |
| `returns the visitor to the start when the exchange rejects (F1)` | drives the success path |
| `handles one callback per mount, ignoring a second set of parameters (F2)` | drives the success path |
| `and the reached callback stays put rather than returning to sign-in` | drives the success path |
| `reaches the seam exactly once although the effect is invoked twice` | drives the success path |
| `trims padding off a usable code and state before the exchange sees them` | drives the success path |
| `uses the first state when the query carries two` | drives the success path |
| `reads none of the three stored items, even on the success path` | the `:570` case, as predicted |

Every one of them arrives at `/callback?code=abc&state=xyz` **without seeding a stored state**. That was a valid
success path before T18 and is a *mismatch* after it, which is the task working correctly. My decision named the
wrong count because I reasoned about which assertion I was going to edit rather than about which arrivals change
meaning. The fix was mechanical, not a redesign: each case now seeds the stored side through a named
`MATCHING_STATE` pair and a `storeState()` helper, so the agreement the success path now requires is visible at
each call site instead of being implied by a bare literal.

## The split, not a loosening (T18-D4)

The blanket no-read case became two:

- **early-return shapes** — `it.each(DISCARD_SHAPES)`, seeded storage, and **no** stored key touched, because
  the effect returns before the comparison;
- **the comparison path** — run twice, once on a match and once on a mismatch, asserting `state` is read and
  `nonce` and `code_verifier` are not. Identical either way, because the read precedes the outcome.

Kimi's recommendation would have had the "nothing stored" case read no key. That is impossible: discovering that
nothing is stored *is* the read.

## The finding that came out of checking my own comment

The mutation sweep left **N2** (drop `stored === null`) and **N3** (drop `stored === ''`) alive. They are
**equivalent mutants given T17's guard**: neither `null` nor `''` can equal a `state` that the guard above has
already proved non-empty, so no test can separate them from the inequality.

T18-D2 kept those tests for reader clarity, which is a fair reason on its own. But writing the comment that says
so made a sharper question obvious: *what exactly do they defend?* The answer is a **coupling across two
guards** — if T17's empty-`state` test were ever relaxed, `'' !== ''` is false, so an empty state would match an
empty stored value and be exchanged.

**I applied that two-part mutant rather than asserting the hazard, and found a hole.** The combination was
caught by only two cases, and by **no assertion about the outcome** — because with nothing stored, `null !== ''`
still discards. The genuinely uncovered pair was an empty state on *both* sides.

Three cases now assert the **security property instead of the path**: an empty or whitespace state never
exchanges, whichever guard catches it. They hold under a refactor that moves the responsibility between the two
guards, which is exactly what the original pair of assertions could not survive. With them in place the two-part
mutant fails **4** cases, two of which are outcome assertions.

The code carries a comment saying the two tests are redundant today, what they defend, and not to fold them into
the inequality to tidy it.

## Verification

- **T18's file: 126 cases, all passing.** Whole suite **687 passed, 1 failed (688)** — delta **+54** against
  T17's 634/635, the only failure being the known environmental `lintRejectsHandWrittenBackendFetch` AC4 (this
  host's `oxlint@1.87.0` prints no summary line).
- **`tsc --noEmit` clean. `oxlint` clean.**
- **Mutation sweep, step 4 of the plan:**

| Mutant | Result |
|---|---|
| N1 — drop the inequality entirely | **killed** — 24 failed |
| N2 — drop `stored === null` | **survived — equivalent mutant**, see above |
| N3 — drop `stored === ''` | **survived — equivalent mutant**, see above |
| N4 — fold case before comparing | **killed** — 4 failed |
| N5 — trim the stored value | **killed** — 4 failed |
| N6 — match on a prefix | **killed** — 4 failed |
| **N7 — compare against a constant, not storage** | **killed — 30 failed.** The one the plan named as mattering most: AC5 is not vacuous |
| N8 — break one widening pattern, control left | **killed** — 1 failed |
| N9 — the two-part empty-state hazard | **killed — 4 failed**, after the three new cases |

## Open questions

**OQ-2 gains a concrete note.** T18 reads the stored state without consuming it, so a correct callback URL still
compares equal more than once until task 23 deletes the items. Unchanged in substance, but now observable: the
comparison is live, where before it did not exist.

**OQ-1** (a state supplied with nothing stored) is implemented as AC2 and is the second row of
`MISMATCH_SHAPES`. **OQ-3** (the unreviewed copy) is unchanged — T18 added no string.

---

## Next

Phase 7 — self review.
