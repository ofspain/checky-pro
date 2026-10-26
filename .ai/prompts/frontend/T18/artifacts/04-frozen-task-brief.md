# frontend · T18 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T18 — Mismatched state discarded |
| **Model** | Human Approval gate — assembled by Claude Opus 5 |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` (Kimi 2.7) |
| **Produces** | `artifacts/04-frozen-task-brief.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 18):**
> **Mismatched state discarded.** A callback whose `state` differs from the stored value is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

**Numbering note.** T18's own decisions are labelled **T18-D1…T18-D11**. T17's are referred to by their plain
`D-n` names, because T18 inherits several of them (D-7, D-10, D-12) and reusing the numbers would make the prose
ambiguous.

---

## Disposition of Kimi's ten findings

**All ten accepted in substance. Two had incorrect premises, corrected below; one is accepted only in part.**

| # | Finding | Disposition |
|---|---|---|
| 1 | "timing identical by construction" is false | **Accepted** → T18-D8 |
| 2 | AC5's invariant is ambiguous | **Accepted as a clarification**, not a correction → T18-D6 |
| 3 | AC6 needs a stated test-injection mechanism | **Accepted** → T18-D7 |
| 4 | the `null`/`''` rule is only stated negatively | **Accepted** → T18-D2 |
| 5 | a non-string stored value is unaddressed | **Accepted; premise wrong** → T18-D5 |
| 6 | the AC9 narrowing has a path hazard | **Accepted — the best of the ten; premise partly wrong** → T18-D4 |
| 7 | no guard against a mutable intermediate | **Accepted in part** → T18-D3 |
| 8 | AC7 ordering: code before assertions | **Accepted** → T18-D10 |
| 9 | the 43-character shape is unnecessary for prefix/suffix | **Accepted** → T18-D7 |
| 10 | the comment correction gives no replacement text | **Accepted** → T18-D9 |

### Finding 5's premise is wrong, and the conclusion is stronger than Kimi argued

Kimi reasons that a non-string could reach the `state` key "if a serialized object lands there from a bug in
another task, or if `sessionStorage` is manipulated by a browser extension", and that `===` returning `false`
is safe but unconsidered.

**`sessionStorage` cannot return a non-string.** `Storage.setItem` coerces its value to a string on write, so an
object stored there is read back as the string `"[object Object]"`. The Web Storage API's value type is
`DOMString`. So the scenario is not unlikely — it is **impossible**, and no `typeof` guard could ever fire.
Locked as T18-D5 with that reasoning, which is a firmer footing than "safe default".

### Finding 6 is the best of the ten, and its premise is partly wrong

The hazard is real and I had understated it. The case at `callbackWithMismatchedStateIsDiscarded.test.tsx:570`
seeds `state: 'stored-state'` and arrives with `state=xyz`. **After T18 that case breaks in two ways at once:**
the arrival becomes a *mismatch*, so `expect(calls).toHaveLength(1)` fails, **and** the comparison reads the
`state` key, so the no-read loop fails. My brief said "narrow it" as though one assertion needed loosening.

But Kimi's recommended split says "on the mismatch path with nothing stored, no key is read (the null/empty
check fires first)". **That is wrong: the null check *is* the read.** There is no way to discover that nothing
is stored without calling `getItem`. The correct split is by *whether the comparison is reached*, not by whether
anything was found — locked as T18-D4.

### Finding 7 is accepted only in part

The substance — no normalisation, no transformation, compare the stored value byte-for-byte — is locked as
T18-D3. The specific recommendation "no intermediate variable that outlives the comparison" is **rejected**:
JavaScript strings are immutable and a `const` binding cannot be mutated, so the hazard described does not
exist, and a named `const stored = …` reads better than inlining a `getItem` call inside a comparison. The real
rule is about transformation, not about binding.

### Finding 2 is a clarification, not a correction

"Varies *only* the stored value between two otherwise identical arrivals" already fixes the URL as invariant, on
a careful reading. Kimi is right that the wording invites a careless one, and the amendment costs nothing, so it
is taken — but the original AC was not defective.

---

## Locked decisions

### T18-D1 — the comparison is inline in the existing effect; no new module

T17 created `codeExchange.ts` because its central assertion was an *absence* and an absence needs a present
collaborator to be observable. **T18 needs no such seam:** "the exchange was not reached" is already observable
through the existing seam spy, and "the visitor was returned" through the router. The comparison is four lines
at `CallbackScreen.tsx:67`, calling the `discard()` closure that already exists. A module would add a file, a
set of source scans and an import for no new observable.

### T18-D2 — nothing stored is checked first, positively
*(finding 4)*

```ts
const stored = globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state);
if (stored === null || stored === '' || stored !== state) {
  discard();
  return;
}
```

`null` (absent) and `''` (explicitly empty) are both "nothing stored" and both discard. They are tested
explicitly rather than folded into the inequality, so no reader has to reason about whether `null !== 'xyz'`
happens to be true for the right reason. **`==` is forbidden anywhere in this comparison.**

### T18-D3 — the comparison is exact, with no transformation on either side
*(finding 7, in part)*

`===` between the stored value and the callback's `state`. No `trim` on the stored value, no `toLowerCase`, no
`normalize`, no prefix or suffix matching, no length check standing in for equality. Anything that widens
equality widens the injection surface. A named `const` holding the stored value is fine — strings are immutable.

Note the asymmetry, which is deliberate: T17 **trims the callback's** `state` (its D-3/F3), because a mangled
query is a client-side artefact; the **stored** value is never trimmed, because T15 wrote it and padding there
would mean something is wrong.

### T18-D4 — the read-path split, by whether the comparison is reached
*(finding 6, premise corrected)*

| Path | Keys read |
|---|---|
| no usable `state` or `code` in the URL; `error` present | **none** — the effect returns before the comparison |
| the comparison is reached — **match or mismatch, stored or not** | **exactly one: `state`** |
| any path | `nonce` and `code_verifier`: **never**, by any task before 19 and 20 |

The existing blanket no-read case is **split, not loosened**: it is retained verbatim for the discard shapes
that return early, and a second case covers the comparison path allowing `state` and only `state`. Deleting the
blanket assertion, or widening it to allow `state` on every path, would stop guarding the boundary for tasks 19
and 20 — which is the whole reason it exists.

### T18-D5 — the stored value is trusted to be a string, because it cannot be anything else
*(finding 5, premise corrected)*

No `typeof` guard. `Storage.setItem` coerces to `DOMString` on write, so `getItem` returns `string | null` and
nothing else is reachable. Recorded so a later reviewer does not add a dead branch.

### T18-D6 — AC5's invariant, stated exactly
*(finding 2)*

One case performs **two navigations carrying the same callback URL `state` and `code`**, changing **only** the
value under `AUTHORIZE_STORAGE_KEYS.state` between them, and asserts the outcome flips: discard when the stored
value differs, seam-call when it is changed to match. That is what makes the comparison provably against the
stored value rather than against a constant — the defect class this package has shipped eight times.

### T18-D7 — the test writes the stored value directly, and prefix/suffix cases use short literals
*(findings 3 and 9)*

T15's writer produces only a 43-character base64url token, which contains no whitespace and cannot be a prefix
of anything the test supplies. **The test therefore writes the stored value itself** via
`sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.state, …)`, which is the only way to produce the
case-differing, whitespace-padded, prefix and suffix shapes AC6 requires. This is stated here so the deliberate
injection is visible to a reviewer rather than looking like a shortcut.

Those shapes use **short literals** (`'abc'` stored against `'abcd'` supplied, and so on). Replicating the
43-character production shape would couple the test to T15's implementation and prove nothing extra. The
43-character fact is removed from the brief's Inputs section as a result.

### T18-D8 — the L4 timing claim, corrected
*(finding 1)*

The frozen wording: **"No network I/O, no backend call and no asynchronous work distinguishes a mismatch from a
missing state. The two paths differ by one synchronous `sessionStorage.getItem`, which is not observable to a
remote party and is not the enumeration channel L4 exists to close."** My "identical by construction" was
false; the mismatch path reaches the comparison, the missing-state path returns at `:63` before it.

### T18-D9 — the exact replacement text for the false comment
*(finding 10)*

`CallbackScreen.tsx:43` currently reads "Task 19 owns telling the visitor anything about the error itself; T17's
only outcome is the discard." Task 19 is *nonce validation*, and **no task in the package displays an
authorization-server error detail**. Frozen replacement:

```
// Nothing in the package surfaces the error itself: L4 forbids copy that distinguishes one failure from
// another, and task 28 maps even a SAS refusal onto the generic wrong-password copy. The discard is the
// whole outcome.
```

### T18-D10 — the comparison lands before the assertions that measure it
*(finding 8)*

Phase 6 adds the comparison to `CallbackScreen.tsx` first, then extends the suite. AC7 compares the mismatch
outcome against the missing-state outcome, and that code path must run before it can be measured.

### T18-D11 — the regression obligation, with the one expected break named

`vitest run` over the whole suite, measured against **634/635**, the only acceptable failure being
`lintRejectsHandWrittenBackendFetch` **AC4** (this host's `oxlint@1.87.0` prints no summary line). **Exactly one
existing case is expected to change:** `callbackWithMismatchedStateIsDiscarded.test.tsx:570`, "reads none of the
three stored items, even on the success path", split per T18-D4. Any other change to an existing case is a
defect in this task, not a consequence of it.

---

## Acceptance criteria, as frozen

AC1–AC15 stand as `artifacts/01-specification-extraction.md` wrote them, with:

- **AC5 sharpened by T18-D6** — two navigations, same URL, only the stored value varied.
- **AC6 sharpened by T18-D7** — the test writes the stored value; prefix/suffix use short literals.
- **AC9 replaced by T18-D4** — the read-path split, not a narrowing.

Added:

- **AC16** — the comparison contains no `==`, no `trim` applied to the stored value, and no `toLowerCase`,
  `normalize` or prefix/suffix matching; asserted by a source scan over `CallbackScreen.tsx` with a synthetic
  violation as a negative control (T18-D3).
- **AC17** — the corrected comment at `CallbackScreen.tsx:43` names no task number, since the claim it replaces
  was false precisely because of one (T18-D9).

---

## Files

| File | Change |
|---|---|
| `src/auth/CallbackScreen.tsx` | the comparison at `:67`; the comment correction at `:43` |
| `src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` | extended; the `:570` case split per T18-D4 |

`authorizeRequest.ts` is imported, not modified. Nothing else is touched.

---

## Open questions

**No blockers.** **OQ-1** (a state supplied with nothing stored) is claimed for T18 as AC2 and is reversible in
one branch. **OQ-2** ("single-use" deletion stays task 23's) is recorded for task 23 to inherit. **OQ-3** (the
unreviewed copy) is unchanged — T18 adds no string.

---

## Next

Phase 5 — implementation plan.
