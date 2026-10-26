# frontend · T18 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T18 — Mismatched state discarded |
| **Model** | Human Approval gate — assembled and applied by Claude Opus 5 |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` (Kimi 2.7) |
| **Produces** | `artifacts/09-review-resolution.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 18):**
> **Mismatched state discarded.** A callback whose `state` differs from the stored value is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

---

## Disposition

**Nine of eleven accepted. Two rejected, both on verified grounds.** Kimi's review corroborated all six of
phase 7's findings and added five of its own, one of which turned out to be far more important than the severity
it was given.

| # | Finding | Kimi's severity | Disposition | Mutant |
|---|---|---|---|---|
| I1 | uncaught `sessionStorage.getItem` | High | **ACCEPTED** (= G1) | P2, P3 **killed** |
| I2 | unencoded URL interpolation | Medium | **ACCEPTED** (= G6) | — |
| I3 | `outcomeOf` hides a `clear()` | Medium | **ACCEPTED** (= G3) | — |
| I4 | missing `stored === ''` + whitespace URL | Medium | **REJECTED — already covered** | — |
| I5 | AC5 hand-rolls teardown | Medium | **ACCEPTED** (= G2) | — |
| I6 | double-writes the state key | Low | **ACCEPTED** (= G5) | — |
| I7 | whitespace table is one-directional | Low | **RECOMMENDATION REJECTED**, observation recorded | — |
| I8 | early-return survival unasserted | Medium | **ACCEPTED** | P4 **killed** |
| I9 | the environmental failure as a blanket excuse | Medium | **ACCEPTED as a note** | — |
| I10 | success path uses one matching value | **Low** | **ACCEPTED and UPGRADED** | P1 **killed** |
| I11 | class doc should note the guarded read | Low | **ACCEPTED** | — |

---

## I10 — rated Low, measured as the real anti-tautology gap

Kimi called this "belt-and-suspenders" on the grounds that AC5 already kills the constant mutant with 30
failures. **I tested it instead of accepting that, and the conclusion is the opposite.**

AC5 killed `state !== state` — a constant on the **supplied** side. It did **not** kill a constant on the
**stored** side. Mutating the guard to `stored !== 'xyz'` left **125 of 126 cases passing**, and the single
failure was incidental: `uses the first state when the query carries two` seeds `'first'` and so happened to
trip.

The reason is exactly what Kimi identified: every success-path case seeded the same literal `MATCHING_STATE`,
so a comparison of storage against that literal satisfied all of them. AC5 varied the stored value but used
`MATCHING_STATE` as the matching one, so it varied the wrong axis to catch this.

AC5 is now an `it.each` over **two unrelated matching values**. The mutant is dead at 2 failures, and the
anti-tautology criterion finally means what it claimed to.

This is the eighth-and-a-half appearance of this package's recurring defect, and the most instructive: the
assertion was not compared against its own constant, it was compared against *a different constant that every
fixture happened to share*.

## I1 — the material defect, confirmed independently

Both reviews reached it separately, which is why it was the first thing fixed. The read is now wrapped, and a
denial is treated as nothing stored:

```ts
let stored: string | null = null;
try {
  stored = globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state);
} catch {
  stored = null;
}
```

Two mutants pin it: **P2** restores the unguarded read, **P3** swallows the denial as a *match* rather than a
discard. Both die against the new case, which stubs `Storage.prototype.getItem` to throw a `SecurityError` and
asserts the visitor lands on the sign-in screen with a rendered `<main>` and the neutral sentence — not a blank
page.

## The two rejections

### I4 — factually wrong

Kimi writes: "The test covers `storedValue = '   '` + URL `%20%20%20`, but not `storedValue = ''` + URL `%20`."

**The third row of that very block is `['stored empty, supplied whitespace', '', '/callback?code=abc&state=%09']`
— stored `''` with a whitespace-only supplied state.** Kimi's recommended addition is the same shape with a
different whitespace character. No change made.

### I7 — the recommendation would assert the opposite of T17's behaviour

Kimi recommends adding `['abc', ' abc ']` to `MISMATCH_SHAPES` "for symmetry". **That row would fail**, and
should: T17 trims the *supplied* value before the comparison runs, so `' abc '` becomes `'abc'`, matches the
stored `'abc'`, and is **exchanged**. It is a success case, not a mismatch.

Kimi half-noticed this — it says the inverse "is already covered by the success-path trim test" — and then
recommended adding the row anyway. The asymmetry is deliberate and locked as T18-D3: the supplied value is
trimmed because a mangled query is a client-side artefact; the stored value is never trimmed because T15 wrote
it. The observation is recorded as a comment on the row naming where the other direction lives.

## I9 — the environmental failure, with the guard that already exists

Accepted as a process note. The concern is fair: a permanently-failing case can hide a new one. **What prevents
that here is that every phase asserts an exact total**, not "one known failure" — 634/635 at T17, 687/688 at
phase 7, **689/690** now. A new failure changes the number and is visible immediately.

Recommendation carried to phase 12: confirm the failure is identical on CI, where `oxlint`'s output may differ
from this host's. It is `lintRejectsHandWrittenBackendFetch` **AC4** — the assertion wants a summary line this
host's `oxlint@1.87.0` never prints. Note that **both of Kimi's reviews of T17 misattributed this to `pnpm` on
`PATH`**, which is AC2 of the same file and passes under the `npx` invocation.

## The rest

**I2** — a `arrivalWith(state, code)` helper encodes by default, so a future row carrying `&`, `#` or `=`
reshapes the value rather than the query. Rows that deliberately exercise percent-encoded whitespace still pass
their URL literally.
**I3** — `outcomeOf` → `outcomeAfterReset`, with the teardown named rather than buried.
**I5** — one `resetBetweenArrivals()` used by both multi-arrival cases, so it cannot drift from `afterEach`.
**I6** — `seedNonceAndVerifier()` leaves `storeState` the only writer of the state key.
**I8** — the early-return cases now assert the three items **survive**, not merely that nothing was read or
written. Before, only the missing-state shape was covered for survival; a discard that deleted storage on an
error-only arrival would have passed. Mutant **P4** dies at 10 failures.
**I11** — the class doc records that the read is guarded and a denial discards.

---

## Verification

**Suite: 689 passed · 1 failed (690)**, +2 against phase 7's 688. T18's file **128/128**. `tsc --noEmit` clean,
`oxlint` clean. The one failure is the known environmental AC4.

| Mutant | Undoes | Result |
|---|---|---|
| P1 | compares storage to a hardcoded literal | **killed** — 2 failed (**survived 125/126 before this phase**) |
| P2 | unguards the storage read | **killed** — 1 failed |
| P3 | treats a denial as a match | **killed** — 1 failed |
| P4 | deletes storage on an early-return discard | **killed** — 10 failed |

Phase 6's N1, N4–N8 remain killed; N2 and N3 remain equivalent mutants, documented in the code.

---

## Open questions

**OQ-2** — T18 reads without consuming; a correct callback URL still compares equal more than once until task
23 deletes the items. Recommend it stays task 23's. **OQ-1** implemented as AC2. **OQ-3** unchanged, T18 adds
no string.

---

## Next

Phase 10 — test generation.
