# frontend · T25 · Phase 11 — Test Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T25 — Return target outside allowlist |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Reviewed the Phase 10 test generation manifest and the updated `returnTargetOutsideAllowlistFallsBackToHome.test.tsx` against the frozen brief. The T25 section is now very strong: 109 cases, every Phase 8 finding addressed, 25 mutants killed with no survivors, and the escaped M8 mutant was turned into a new family. The gaps below are minor: one process/reproducibility item, one coupling risk, and a few coverage completions.

> Note on verification: the local environment does not have `vite-plugin-pwa` installed, so `vitest` cannot start. Findings are from static review of the committed source.

---

## Gap 1 · The mutation harness script is not committed to the repository

**Why it matters.** Phase 10 reports 25 mutants killed by a Python script at `/private/tmp/claude-501/…/mutate-t25.py`. A `glob` for the script in the working directory finds nothing. Without the script in version control, the mutation results are not reproducible by the next reviewer or CI. The manifest is detailed, but the instrument that produced it is not.

**Suggested test.** Commit the mutation harness under a tooling directory (e.g., `frontend/scripts/mutate-t25.py` or `.ai/prompts/frontend/T25/tools/`) with a short README explaining how to run it. If the script is intentionally ephemeral, record that decision in the Phase 10 artifact.

---

## Gap 2 · `routerOutcomeFor` dynamically imports `../app/routes` by exported name

**Why it matters.** The counterfactual instrument relies on `routes.appRoutes` and `routes.APP_BASENAME`. If the production module renames either export, `routerOutcomeFor` fails at runtime while the test file still compiles. Every control measurement then becomes wrong or absent.

**Suggested test.** Add a small compile-time guard: import the routes module with TypeScript at the top of the test file and assert that `appRoutes` and `APP_BASENAME` are present, or type `routerOutcomeFor`'s dynamic import so a rename breaks `tsc`. Alternatively, add a dedicated case that asserts `routerOutcomeFor('/home')` reaches `HOME_ROUTE`, which would fail loudly if the dynamic import broke.

---

## Gap 3 · No end-to-end case for a single-segment leading-whitespace target

**Why it matters.** The near-miss table has `  ${A_REAL_TARGET}` (two segments) and there is an end-to-end case for it. A single-segment variant `  /home` is not exercised end-to-end. The router resolves both relative to the current route, so the shape is covered in principle, but a single-segment witness would catch a trim implementation that handled one segment differently.

**Suggested test.** Add one row to the whitespace-padded near-miss case, or extend the existing end-to-end whitespace case to assert both `  /home` and `  /account/sessions` behave the same way.

---

## Gap 4 · No case for a relative-path target without a leading slash

**Why it matters.** The table now covers `home` and `account/sessions` (no leading slash) as `miss` shapes. It does not cover `./home`, which is another way a caller might construct a relative path. The router would resolve it against `/app/callback` and land on the catch-all, but the predicate should still refuse it.

**Suggested test.** Add one row: `./home` is refused by `isAllowlistedTarget`, is not storable, and the flow ends on `HOME_ROUTE`.

---

## Gap 5 · The counterfactual claim still says "what the screen would do" in places

**Why it matters.** `routerOutcomeFor` uses `router.navigate` directly, while the screen uses `useNavigate`, which resolves relative paths against the current route. For leading-`/` shapes the answers coincide, but the wording in comments (e.g., line 1349: "the router would have done") could be read as claiming equivalence to the screen.

**Suggested test.** No test change. Narrow the comments to say the measurement is the bare router's answer, and that the screen's `useNavigate` is only guaranteed to agree for absolute router paths. This is the self-review S4 / Kimi I4 finding; it was not addressed in Phase 10.

---

## Gap 6 · No case verifies the producer refuses a no-leading-slash target

**Why it matters.** The "is not even storable" `it.each(FAMILIES)` covers the no-leading-slash rows, so this is technically present. But the FAMILIES rows for `home` and `account/sessions` are classified as `miss` (router lands on catch-all) rather than as a distinct risk class. A dedicated producer case would make the T24-D8 contract — stored form MUST have a leading slash — explicit.

**Suggested test.** Add a standalone case: `rememberReturnTarget('home')` writes nothing, `rememberReturnTarget('/home')` writes the target, and `takeReturnTarget()` returns `/home` only for the second.

---

## Gap 7 · The completeness pin comment says what to do but not why the number is 23

**Why it matters.** The comment at lines 1561-1563 explains how to update the pin, but it does not list the four families added beyond the original 14 todo items. A future maintainer has to reverse-engineer the additions from the table.

**Suggested test.** No test change. Add a comment naming the four additions: trailing slash (one and two segments), wrong case, encoded separator, no leading slash (one and two segments), leading whitespace, and the two combination rows. This makes the 23 explicit.

---

## Summary

Gap 1 is the most important because it affects reproducibility. Gap 2 is a structural coupling risk. Gaps 3, 4, and 6 are small coverage completions. Gap 5 and Gap 7 are documentation. The suite is already credible; these changes would make it more maintainable and auditable.
