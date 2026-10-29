# frontend · T26 · Phase 8 — Independent Code Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T26 — Session storage holds only L16 items |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Independent review of the T26 test file against the frozen brief. The file is comprehensive and well-instrumented: it derives the inventory, sweeps every state, watches the write path, scans production source, proves the instruments against synthetic violations, and distinguishes an unreachable store from an empty one. The self-review's two high-severity findings are real, and I add a couple of smaller ones below.

> Note on verification: the local environment does not have `vite-plugin-pwa` installed, so `vitest` cannot start. Findings are from static review of the committed source.

---

## I1 · A dormant write with a computed method name is invisible to both instruments

**Severity.** High — the one hole measurement found.

**Evidence.**
- The self-review reports mutant MA22: a fifth-key write behind `navigator.userAgent.includes('ZZZ-NEVER-MATCHES')`.
- The runtime watch never sees the write because the branch never runs.
- The source scan looks for `setItem`, bracket-`setItem`, and `.call/.apply/.bind` forms, but MA22 computes the method name (`const method = 'set' + 'Item'`), so no literal `setItem` appears.
- The mutant is killed only by T23's `stateSurvivesRedirectAndIsConsumedOnce.test.tsx:851`, which counts key-prefix occurrences in `authorizeRequest.ts`.

**Why it matters.** A guard whose job is the package-level inventory claim should not depend on a neighbour to catch the one family it cannot see. Dormant code is realistic: feature flags, user-agent branches, error paths no test drives. The key name survives in source even when the method name and the execution path do not.

**Recommendation.** Add the third instrument the self-review proposes: derive the common prefix `themistra.auth.` from the inventory constants, scan production source for every key-shaped literal carrying that prefix, and require each one to be an inventory key. Include MA22 as the control that proves it. This keys on the *name of the thing stored* rather than the call that stores it, so it sees dormant and computed writes alike.

**Confidence.** High — measured, with the failing neighbour case named.

---

## I2 · The `localStorage` scan forbids something `design.md` permits

**Severity.** Medium.

**Evidence.**
- Line 917 asserts `hits(targets, /\blocalStorage\b/)` is empty, with the message "localStorage holds public preferences only (design.md §5)".
- `design.md:193` explicitly permits localStorage for "public preferences (theme, locale) only".

**Why it matters.** The day a theme toggle lands, this case fails, and its message states the rule the change complies with rather than the rule it broke. A guard that cannot distinguish "a preference arrived" from "a token leaked" will be edited by whoever hits it first, and the edit will likely weaken the guard.

**Recommendation.**
- Keep the behavioural claim: `localStorage` is empty after every flow the file drives.
- Narrow the structural claim to what L16 and L3 actually forbid: **no inventory key and no secret-shaped value in `localStorage`**, with a message naming public preferences as the permitted entrant.

**Confidence.** High — the spec text is explicit.

---

## I3 · Two arrivals in one case leave the first screen mounted

**Severity.** Medium.

**Evidence.**
- `arriveAt` assigns `mounted = { root, container }` (line 419), overwriting the previous entry without unmounting it.
- `afterEach` unmounts only the current `mounted`.
- Lines 1148, 1174, and 1193 set `mounted = undefined` between arrivals, which hides the first root from cleanup without unmounting it.
- T24 and T25 use `arrival.unmount()` between arrivals.

**Why it matters.** The abandoned screen's effects stay live. A second `CallbackScreen` can still be running while the next case's store assertions are taken, and a storage write from the orphan would be attributed to the flow under test. No case fails today, which is exactly why it should be fixed now.

**Recommendation.** Replace each `mounted = undefined` with `arrival.unmount()` on the arrival being abandoned, and reset `mounted` only after unmount. This mirrors the idiom in neighbouring files.

**Confidence.** High — structural, and the correct idiom is already in the package.

---

## I4 · The discard rows assert a subset where they could assert the exact set

**Severity.** Low.

**Evidence.**
- Each discard case calls `expectOnlyInventory(sweepSession(), ...)` (line 782), which asserts no stranger is present.
- A discard deletes nothing (T23-D2) and does not consume the target (T24-D14), so the exact set is known: all four inventory keys.
- Only the discard-then-retry case (line 1173) asserts the exact set.

**Why it matters.** A mutant that deleted a key on a discard path would pass all six subset rows. T23 owns the deletion claim, so this is not T26's hole — but the stronger assertion costs one line and makes each row say what it measured rather than what it tolerated.

**Recommendation.** Assert `keysIn(sweepSession()).toEqual(inventoryKeys())` in the six discard rows, keeping `expectNoKeyOutsideInventory(watch, ...)` for the write-path claim.

**Confidence.** Medium.

---

## I5 · The key-prefix scan should also catch near-prefix keys

**Severity.** Low.

**Evidence.**
- I1 proposes scanning for key-shaped literals carrying the `themistra.auth.` prefix.
- A computed key like `themistra.auth.state_backup` would be caught because it contains the literal `themistra.auth.state_backup`.
- But a key like `themistra.auth_state` (prefix without dot) or `themistra.authx.foo` would not match the prefix literal `themistra.auth.`.

**Why it matters.** The prefix `themistra.auth.` is the namespace L16's carve-out uses. A key in a sibling namespace is arguably outside the carve-out and should be flagged. A regex that matches `themistra\.auth` followed by a word boundary or dot would catch both legitimate inventory keys and suspicious siblings.

**Recommendation.** When implementing the third instrument, use a pattern like `/\bthemistra\.auth(?:\.\w+)+\b/` or similar, and assert that every match is one of the four inventory keys. Include a synthetic control with `themistra.auth.evil` to prove it fires.

**Confidence.** Low — refinement of I1.

---

## I6 · The forbidden-surface list is enumerative and said so, but could name the decision boundary

**Severity.** Low.

**Evidence.**
- `FORBIDDEN_SURFACES` has six entries and its comment says the list is "enumerative, not exhaustive".
- The case that scans it carries a design-decision message.

**Why it matters.** The standing claim "nothing else is stored client-side" (L16) is broader than any six names. A reader may take the green case for the whole of it. The file already says this in the header; making the case message repeat it would reduce the chance of misreading.

**Recommendation.** No code change. Record in the Phase 9 resolution that the structural claim is bounded by the list, that `design.md` §5 governs additions, and that this is an explicit design boundary rather than a coverage gap.

**Confidence.** Low — documentation.

---

## I7 · The mutation harness is committed; its path should be recorded

**Severity.** Low.

**Evidence.**
- The self-review header says it produced `artifacts/07-mutation-harness.py` and committed it.
- A `glob` for `**/mutate-t26.py` or similar would confirm it.

**Why it matters.** Committing the harness is the right thing (and addresses a gap noted in T25's Phase 11 review). The Phase 10/12 artifacts should explicitly state where the harness lives and how to run it, so the 22/22 killed claim is reproducible.

**Recommendation.** Ensure the Phase 10 manifest includes the harness path and a one-line run command. No code change.

**Confidence.** Low — reproducibility.

---

## Summary

I1 is the critical finding: a computed-method-name dormant write escapes both the runtime watch and the source scan. The third instrument (key-prefix scan) closes it. I2 and I3 are medium-severity and should be fixed in Phase 10. I4 is a one-line strengthening. I5-I7 are refinements and documentation. No production change is needed.
