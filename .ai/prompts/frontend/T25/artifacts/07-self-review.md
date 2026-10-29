# frontend · T25 · Phase 7 — Self-Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T25 — Return target outside allowlist |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md` |

Reviewing my own Phase 6 against the frozen brief, with the bias stated up front: **everything passed on the first
run**, which is exactly when a rejection table is most likely to be asserting that nothing happened. One mutant was
already applied in Phase 6 for that reason; two more were applied for this review, and **one of them escaped**.

**Seven findings. S1 and S2 are the ones worth acting on**, and S1 is a gap against the frozen brief's own Required
Tests list rather than a quality opinion.

---

## S1 · A rejection under a throwing store and a throwing removal was dropped between phases

**Severity.** Medium — a frozen-brief requirement that no phase picked up.

Phase 2's Required Tests included "a rejection under a **throwing store** and under a **throwing removal**, so
totality is not re-proven only on the honoured path", and Phase 4 §7 kept Phase 2's list in full ("Phase 2's list,
plus: …"). Phase 5 then split the authoring between Phase 6 and Phase 10 and **named these rows in neither**. Phase
6 did not write them.

**Evidence.** `grep` for `denyReadOf` / `denyRemovalOf` in the T25 block returns nothing; both helpers are used
only by T24's cases, which exercise them on the **honoured** path (`:563`, `:890`).

**Why it matters.** T24-D10 makes `takeReturnTarget` total, and the reason is specific: a synchronous throw from it
reaches `.then(onFulfilled, discard)`'s first argument, where the second cannot catch it, and blanks the page after
a successful sign-in. The validation branch for a refused value is a *different branch* from the honoured one — it
calls `homeTarget()` rather than returning `stored` — and nothing proves that branch survives a store that throws
on removal.

**Recommendation.** Two cases in Phase 10: a refused target under `denyRemovalOf(RETURN_TARGET_KEY)` (the visitor
still reaches home, and the refused value stays, per T24-D9) and a refused target under `denyReadOf` (treated as no
target). Not a production change.

## S2 · Every T25 row would pass against an implementation that wiped the whole store

**Severity.** Medium.

The rows assert `flows.storeAfterRefusal` is `[]` — the whole store, which is a good assertion — but an empty store
is also what `sessionStorage.clear()` produces. No T25 row asserts that `clear` was not called.

**Evidence, measured.** I replaced `removeItem(RETURN_TARGET_KEY)` with `clear()` in `takeReturnTarget` and ran the
file: **2 failed, 122 passed**, and both failures are **T24's** cases — `reads the target before deleting it` and
the AC16 removal-throws case. **Not one of the 57 T25 cases noticed.**

**Why it matters.** It is not a hole in the suite, because T24 catches it. It is a hole in the section that is
supposed to own refused values: if the T24 ordering case were ever relaxed, nothing in T25 would object, and a
`clear()` is a realistic mistake — it is the one-line "just make sure it's gone" change, and it would destroy
R29's mid-task form input, which is the other half of L16's carve-out.

**Recommendation.** Add `expect(watch.clear).not.toHaveBeenCalled()` to the per-row assertions, which means
`threeFlows` must install the watch and report it. One helper change, no production change.

## S3 · AC9 is proven by a conjunction, not by a direct assertion — and that is defensible, but it should be said

**Severity.** Low.

Kimi's F1 asked for a direct assertion that `navigate()` was never called with an external shape. What Phase 6
delivered is the conjunction: the counterfactual control proves the shape **would** have thrown, and the flow row
proves the visitor **reached `/app/home`** — which is unreachable if the throw had happened, because the screen
would have been left on `/app/callback`.

That is a sound proof and arguably better than spying on `navigate`, because it measures the outcome rather than
the call. **The part that needs stating:** it leans on the suite being read **by exit code**, since an unhandled
rejection from inside the effect prints *below* the all-passed line. Exit code 0 was recorded for both full runs,
so the premise holds — but a reader checking pass counts would not know that.

**Recommendation.** No change; record the reasoning in the Phase 9 resolution so the proof's shape is explicit.

## S4 · The counterfactual uses `router.navigate`, while the screen uses `useNavigate`

**Severity.** Low.

`routerOutcomeFor` calls `router.navigate(target, { replace: true })`. `CallbackScreen` calls the `navigate`
returned by `useNavigate()` from inside a route. They resolve to the same router, and `useNavigate` adds relative
resolution against the current route — which is *why* `home` and `./home` behave as they do — so the two are not
identical instruments.

**Why it matters, mildly.** Every `refuse` and `reach` answer in the table is `router.navigate`'s. For the shapes
with a leading `/` the distinction cannot matter, and all eighteen have one except the external ones, which throw
either way. So the measurement is sound; it is the *claim* that should be narrower than "what the screen would do".

**Recommendation.** Say so in a comment on `routerOutcomeFor`. No test change.

## S5 · `/account/sessions/` has the trailing-slash property and is not a row

**Severity.** Low.

The gate upgraded F4 because react-router ignores a trailing slash, and `/home/` became a row. Measured,
`/account/sessions/` behaves the same way — it reaches route `account/sessions`. The property therefore has two
witnesses and the table has one.

**Why it matters.** Only slightly: the rule is per-path, not per-depth. But a multi-segment witness is the one that
would catch a fix written as "strip a trailing slash from a single-segment path".

**Recommendation.** One row in Phase 10, beside the near-miss pairs.

## S6 · The completeness pin hard-codes 18, which will be friction

**Severity.** Low — intentional, but worth recording as intentional.

`expect(labels).toHaveLength(18)` fails the moment anyone adds a family. That is the point: the pin exists so a
family cannot be *dropped*, and a count that adjusted itself would not pin anything. But the failure message will
not say that.

**Recommendation.** Nothing in code beyond a comment; recorded here so the next person does not "fix" it.

## S7 · The three-kinds split is asserted by count, not by membership

**Severity.** Low.

`it('is three kinds of risk…')` asserts 7 / 7 / 4. A row reclassified from `reach` to `miss`, paired with another
moved the other way, would keep the counts and weaken an assertion. The per-row control would then fail — `miss`
asserts the catch-all and `reach` asserts a named route — so the escape is narrow, but the pin is weaker than it
looks.

**Recommendation.** Leave it. The per-row cases are the real guard, and the count pin is a readability aid; said
here so it is not mistaken for the guard.

---

## What I checked and found sound

- **The two rows the gate upgraded are the two that earn it.** Case-folding + trailing-slash strip → **5 failures**,
  and they are `/home/`, `/HOME`, both producer rows, and T24's `/HOME` corrupted-value row.
- **The T24 cases pass unchanged.** 67 before the `visibleOutcome` lift, 67 after; the lift changed a body and no
  assertion.
- **`storeAfterRefusal` reads the whole store**, not a named key, so an extra item cannot hide — subject to S2.
- **`threeFlows` builds its baseline inside each case**, so no order dependence between cases (T25-D2, and T23's
  scar).
- **Every row ties to the real exported predicate** (AC2), so no row can pass against a list rebuilt in the test.
- **No production change**, no new dependency, no catalogue string, `spec/` untouched.
- **Runtime recorded rather than traded away:** 54 mounts, 1.18s.

## Carried to Phase 9

| Finding | Disposition I would propose |
|---|---|
| **S1** | accept — two Phase 10 cases; it is a frozen-brief requirement, not an extra |
| **S2** | accept — `clear` into the per-row assertions, via `threeFlows` |
| **S3** | record the reasoning; no change |
| **S4** | a comment narrowing the claim |
| **S5** | one Phase 10 row |
| **S6**, **S7** | record as intentional; no change |

---

## Next — Phase 8 — independent review (Kimi 2.7).
