# frontend · T28 · Phase 8 — Independent Code Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T28 — Unenrolled privileged refusal copy |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Independent review of the T28 test file against the frozen brief. The file is comprehensive and well-argued: it pins the `signIn.*` key set, sweeps four surfaces for leaks, compares failure outcomes as one struct, and adds a path scan that catches files whose bodies are innocent. The self-review's high-severity finding is real, and I independently confirm the two medium findings. A couple of smaller items are worth noting.

> Note on verification: the local environment does not have `vite-plugin-pwa` installed, so `vitest` cannot start. Findings are from static review of the committed source.

---

## I1 · `document.title` and `document.head` are not swept by AC4

**Severity.** High — the one surviving mutant.

**Evidence.**
- `surfacesAfter()` at line 613 builds the `document` surface from `document.body.textContent` and `document.body.innerHTML`.
- `document.title` and `document.head.innerHTML` are not included.
- The self-review reports MB04 — `document.title = params.get('error_description') ?? document.title` in `CallbackScreen.tsx`'s discard closure — survived the whole suite.

**Why it matters.** The tab title is read by the visitor, announced by screen readers on navigation, and persisted in browser history and session restore. An enrollment description written there would breach R10 in exactly the way the backend refused to. AC4 says "absent from the rendered document", and the title is part of the rendered document; narrowing it to `body` silently weakens the claim.

**Recommendation.**
Extend `surfacesAfter()` to include `document.title` and `document.head.innerHTML`, and extend the AC4 sentinel control to plant a sentinel into the title so the surface is proved read. Then re-run MB04; it must be killed by T28's own file.

**Confidence.** High — measured mutant, named in the self-review.

---

## I2 · The key-matching regex is derived by stripping `\b` from the prose pattern

**Severity.** Medium.

**Evidence.**
- `forbiddenCopyEntries()` at line 334 matches catalogue keys with `new RegExp(FORBIDDEN_COPY_SOURCE.replace(/\\b/g, ''), 'i')`.
- The prose pattern uses `\b` for word boundaries; the key pattern removes them to catch camelCase identifiers like `signIn.mfaRequired`.

**Why it matters.** It works today and the controls prove it, but the two grammars are coupled through a textual rewrite. A future pattern alternative whose meaning depends on a `\b` would be silently changed rather than reported. T27 solved the same problem with two explicit constants.

**Recommendation.**
Replace the `.replace(/\\b/g, '')` derivation with a second named constant for the key grammar, plus a comment explaining why keys have no word boundaries. Keep both controls.

**Confidence.** Medium — a fragility, not a current defect.

---

## I3 · The `Outcome` struct does not include the exchange-request count

**Severity.** Medium.

**Evidence.**
- AC1's second case asserts `fetchSpy` was not called for the `error=` path.
- The other three failure kinds are expected to call the exchange exactly once.
- The `Outcome` struct includes `tokenHeld`, `destination`, `historyAction`, `text`, etc., but not `fetchCount`.

**Why it matters.** An implementation that retried a refused exchange would still pass the equivalence comparison, because all four failure kinds would presumably retry the same way — but a retry on one kind and not another would be a uniformity breach, which is this file's subject. More importantly, the `error=` path is the only one asserted to make no exchange; adding the count to the struct would make the equivalence claim cover this explicitly.

**Recommendation.**
Add `fetchCount` to `Outcome` and assert it is `0` for `error-response` and `1` for the other three failure kinds within the AC2 equivalence case. Alternatively, record in the file header that T20 owns exchange replay behaviour and that this file does not re-prove it.

**Confidence.** Medium — gap in uniformity reach.

---

## I4 · The leak sweep does not include `window.name`

**Severity.** Low.

**Evidence.**
- `surfacesAfter()` includes document, sessionStorage, localStorage, router location and console.
- It does not include `window.name`.

**Why it matters.** `window.name` is a client-side surface that persists across navigations and could be used to smuggle a value. jsdom allows setting it. The brief's AC4 says "absent from the rendered document, both stores, the router location, and every console channel", so `window.name` is technically out of scope — but it is also a surface a determined leak could use.

**Recommended brief amendment.**
Either add `window.name` to the sweep with a sentinel control, or explicitly list it as out of scope in the "what this file cannot do" header. Given that `document.cookie` and `indexedDB` are already declared out of scope for environmental reasons, `window.name` should at least be named.

**Confidence.** Low — completeness.

---

## I5 · The `forbiddenCopy` pattern includes `\bcode\b`, which may catch legitimate copy

**Severity.** Low.

**Evidence.**
- `FORBIDDEN_COPY_SOURCE` at line 171 includes `|\bcode\b|`.
- The rationale is that "code" in copy could disclose a one-time code or recovery code.

**Why it matters.** Legitimate future copy such as "Enter the postal code" or "Reference code" would be flagged. The test passes today because no such copy exists, but the pattern's width should be acknowledged so a future feature is not blocked unexpectedly.

**Recommendation.**
Document the broad `\bcode\b` rule in the pattern's comment, and note that any legitimate use of the word "code" in user-facing copy will require an explicit allowlist entry or a narrower pattern. Alternatively, restrict the pattern to MFA-shaped contexts (`one-time code`, `recovery code`, `backup code`, `security code`) and rely on the existing `one[-_ ]?time`, `recovery` and `backup` terms for the rest.

**Confidence.** Low — future-friction note.

---

## I6 · The AC2 ordering claim is over-stated

**Severity.** Low.

**Evidence.**
- The AC2 block at line 667 is titled "an error response is never a state oracle" and explains that `CallbackScreen.tsx:101-104` tests `error` before the state comparison.
- The self-review S2 notes that MB12 and MB23 are killed only by T18's inherited case, not by T28's own file.
- T28's case compares outcomes, and the three `state` shapes discard identically under either order.

**Why it matters.** A reader would conclude T28 pins the ordering, but it does not. The case still adds value: it proves the three `state` shapes are indistinguishable in outcome, which forecloses a future divergence. The comment should say that, and cite T18 as the owner of the ordering.

**Recommendation.**
Rename the block's claim to "the three state shapes are indistinguishable when an error is present" and cite `callbackWithMismatchedStateIsDiscarded.test.tsx` as the ordering owner. This matches the self-review's S2 recommendation.

**Confidence.** Low — wording precision.

---

## I7 · The mutation harness path should be recorded in the file header

**Severity.** Low.

**Evidence.**
- The self-review says the harness is committed at `.ai/prompts/frontend/T28/artifacts/07-mutation-harness.py`.
- The test file header does not mention this path.

**Why it matters.** Reproducibility. A reviewer running the file alone sees 39 green cases but may not know where to find the mutation harness that produced the 23-mutant score.

**Recommendation.**
Add the harness path and run command to the file header or to a comment near the AC11 block. No test change.

**Confidence.** Low — documentation.

---

## Summary

I1 is the critical finding and should be fixed in Phase 10. I2 and I3 are medium-severity and worth acting on. I4–I7 are smaller completeness, documentation and future-friction items. No production change is indicated; all recommendations are test additions or refinements.
