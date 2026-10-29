# frontend · T25 · Phase 8 — Independent Code Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T25 — Return target outside allowlist |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Independent review of the T25 implementation against the frozen brief. The production code (`returnTarget.ts`) is unchanged from T24; T25 is verification-only. I agree with the self-review's two high-severity findings and add a few fresh ones about the counterfactual instrument, the discard witness, and table completeness.

> Note on verification: the local environment does not have `vite-plugin-pwa` installed, so `vitest` cannot start. Findings are from static review of the committed source.

---

## I1 · Throwing store and throwing removal are not exercised on the refused branch

**Severity.** Medium — a frozen-brief requirement that was dropped during phasing.

**Evidence.**
- Phase 2's Required Tests list included "a rejection under a throwing store and under a throwing removal, so totality is not re-proven only on the honoured path."
- `grep` for `denyReadOf` / `denyRemovalOf` in the T25 block returns no matches. Both helpers are used only by T24's honoured-path cases (`:563`, `:890`).
- `takeReturnTarget` has a different code path for refused values (`homeTarget()` rather than returning `stored`), and a different code path for unreadable stores. Neither is tested for a refused input.

**Why it matters.** T24-D10 makes `takeReturnTarget` total because a synchronous throw from the exchange's `onFulfilled` handler is not caught by `.then(onFulfilled, discard)`. The refused branch is the one most likely to interact with a store that throws on removal, and a bug there would blank the page after a successful sign-in.

**Recommendation.** Add two Phase 10 cases:
1. A refused target under `denyRemovalOf(RETURN_TARGET_KEY)`: visitor still reaches home, and the refused value stays in storage (T24-D9).
2. A refused target under `denyReadOf(RETURN_TARGET_KEY)` or an unreachable `sessionStorage`: treated as no target.

**Confidence.** High — directly matches a frozen-brief requirement.

---

## I2 · Every T25 rejection row would pass if `takeReturnTarget` called `sessionStorage.clear()`

**Severity.** Medium.

**Evidence.**
- Each rejection row asserts `flows.storeAfterRefusal` is `[]`.
- No row asserts `watch.clear` was not called.
- The self-review measured the mutant: replacing `removeItem(RETURN_TARGET_KEY)` with `clear()` produced **2 failed, 122 passed**, and both failures were T24 cases — no T25 case noticed.

**Why it matters.** A `clear()` is a realistic "just make sure it's gone" mistake, and it would wipe R29's mid-task form input, the other half of L16's carve-out. T24 catches it today, but T25 is the section that owns refused values and should catch it independently.

**Recommendation.** Install a `watchStorage()` in `threeFlows`, include `clear` in the returned data, and assert `expect(watch.clear).not.toHaveBeenCalled()` in every rejection row. One helper change, no production change.

**Confidence.** High — measured gap with an escaped mutant.

---

## I3 · AC9 is proven by a conjunction, and the reasoning should be recorded

**Severity.** Low.

**Evidence.**
- The counterfactual block (`routerOutcomeFor`) proves that external shapes throw `External navigation is not allowed`.
- The flow block proves the visitor reaches `/app/home`.
- Together, these prove `navigate()` was never called with the external shape, because if it had been, the component would have thrown and the screen would still be on `/app/callback`.

**Why it matters.** This is a sound proof, but it relies on the reader understanding that an unhandled throw inside the effect would leave the test failing in a way that is not counted as a passed assertion. The Phase 9 resolution should state this explicitly.

**Recommendation.** Record the reasoning in the Phase 9 resolution. No code change. Optionally add a comment at the AC9 row noting that the assertion is the conjunction of "throws in counterfactual" and "reaches home in flow".

**Confidence.** Low — documentation, not defect.

---

## I4 · The counterfactual uses `router.navigate`, not the screen's `useNavigate`

**Severity.** Low.

**Evidence.**
- `routerOutcomeFor` calls `router.navigate(target, { replace: true })` directly.
- `CallbackScreen` calls the `navigate` returned by `useNavigate()`.
- `useNavigate` resolves relative paths against the current route, which is why `home` and `./home` differ from `router.navigate`'s behaviour.

**Why it matters.** Every measurement in the table is the bare router's answer. For leading-`/` shapes (all but the external ones) the distinction cannot matter, and the external ones throw either way. The conclusion is sound, but the claim should be narrower than "what the screen would do".

**Recommendation.** Narrow the comment on `routerOutcomeFor` to say it measures the bare router's answer, not the screen's. No test change.

**Confidence.** Low — wording precision.

---

## I5 · `/account/sessions/` is missing as a trailing-slash witness

**Severity.** Low.

**Evidence.**
- The table has `/home/` as the trailing-slash row (line 1238).
- `/account/sessions/` would also be normalised by react-router to the matching route, so it is a second witness of the same property.

**Why it matters.** A single-segment witness might let a buggy fix pass that only handles one segment. A multi-segment witness rules out "strip trailing slash only from single-segment paths".

**Recommendation.** Add `/account/sessions/` as a near-miss row in Phase 10, asserting the router reaches `/app/account/sessions/` and the allowlist refuses it.

**Confidence.** Low — coverage strengthener.

---

## I6 · The AC20 discard case uses only one refused shape

**Severity.** Low.

**Evidence.**
- The AC20 case at line 1338 seeds `https://evil.example/app/home`, a `refuse` shape.
- The principle being tested is "the discard path does not inspect the target, so its content cannot matter".

**Why it matters.** A single shape is enough to prove the code path does not read the target, but the strongest witness would be a `reach` shape — the family the allowlist exists to stop. If a future change accidentally normalised or inspected the target before deciding to discard, a traversal like `/home/../admin` is the most likely shape to be accidentally honoured.

**Recommendation.** Parameterise the AC20 case over one representative from each `reach` family, or at least add one row for `/home/../admin` in addition to the existing external-URL row.

**Confidence.** Low — defence against future regressions.

---

## I7 · No mixed shape in the `reach` category is tested

**Severity.** Low.

**Evidence.**
- The table includes one mixed shape: `https://evil.example/../admin?x=1#fragment` (line 1256), which is a `refuse`.
- There is no shape combining two `reach` families, such as `/home?x=1#fragment` or `/home/../admin?x=1`.

**Why it matters.** The mixed shape proves that combinations are not special-cased. A `refuse` combination is the easy case because the external URL dominates. A `reach` combination would prove that query/fragment/traversal logic does not interact to produce an unexpected match.

**Recommendation.** Add one `reach`-category mixed row, e.g., `/home?x=1#fragment`, and assert the router reaches `HOME_ROUTE` while the allowlist falls back to `HOME_ROUTE`.

**Confidence.** Low — completeness.

---

## I8 · `routerOutcomeFor` dynamically imports `../app/routes` by name

**Severity.** Low.

**Evidence.**
- `routerOutcomeFor` does `const routes = await import('../app/routes')` and then uses `routes.appRoutes` and `routes.APP_BASENAME`.
- If the production module changes its export names, this helper fails at runtime while the test file still compiles.

**Why it matters.** The counterfactual instrument is only as good as its coupling to the real route table. A rename of `appRoutes` would silently make every control measurement wrong or fail outright.

**Recommendation.** Add a TypeScript-typed re-export test or a small compile-time check that `appRoutes` and `APP_BASENAME` exist on the routes module. Alternatively, import the types explicitly so a rename breaks compilation.

**Confidence.** Low — structural coupling.

---

## I9 · The completeness pin hard-codes 18

**Severity.** Low — intentional, but worth recording.

**Evidence.**
- `expect(labels).toHaveLength(18)` at line 1381.
- The comment at line 1353 explains why.

**Why it matters.** The pin is correct: it prevents a family from being dropped. But the failure message will not explain that, so a future maintainer might "fix" it to be dynamic.

**Recommendation.** Leave the code as-is and record the intent in the Phase 9 resolution, as the self-review suggests.

**Confidence.** Low — documentation.

---

## I10 · The three-kinds split is asserted by count, not membership

**Severity.** Low.

**Evidence.**
- The test at line 1284 asserts 7 `refuse`, 7 `reach`, 4 `miss`.
- It does not assert which labels belong to which kind.

**Why it matters.** Reclassifying one `reach` to `miss` and one `miss` to `reach` would keep the counts and weaken the assertion. However, the per-row controls catch this: `miss` asserts the catch-all and `reach` asserts a named route.

**Recommendation.** Leave it. The count pin is a readability aid; record that it is not the primary guard.

**Confidence.** Low — documentation.

---

## Summary

I1 and I2 are the important findings: both are gaps in the section that owns refused values, and I2 had a measured escaped mutant. I3, I4, and I9 are about recording intent precisely. I5, I6, I7, and I8 are smaller coverage and coupling strengtheners. No production change is needed; all recommendations are test additions or comments.
