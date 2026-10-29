# frontend · T24 · Phase 11 — Test Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T24 — Return target inside allowlist |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Reviewed `frontend/src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` against the frozen brief and the 59-case manifest. The file is comprehensive: every AC has a dedicated case, the Phase 8 findings are answered, the mutation score is 20/20 detected with no survivors, and the T25 partition is visible in the source. The gaps below are strengtheners; none suggests a behaviour defect.

> Note on verification: the local environment does not have `vite-plugin-pwa` installed, so `vitest` cannot start. Findings are from static review of the committed test source.

---

## Gap 1 · No test asserts the fallback navigation also uses `replace: true`

**Why it matters.** The brief requires the callback entry to be replaced, not pushed, for both honoured and fallback destinations — the same reasoning applies to both. The file checks `historyAction` for the honoured target, but the "is indistinguishable from a target the allowlist refused" case compares only pathname, search, and rendered text. A fallback that used `PUSH` would still pass those assertions and leave the callback reachable with the back button.

**Suggested test.** Extend the AC9 indistinguishability case to also assert `rejected.router.state.historyAction).toBe('REPLACE')`, or add a standalone fallback-history case.

---

## Gap 2 · No test asserts `rememberReturnTarget` does not write when `setItem` throws

**Why it matters.** The "never throws when the store itself is unreachable" case asserts both `takeReturnTarget` and `rememberReturnTarget` do not throw, but it does not assert that the producer left nothing behind when the write failed. A buggy implementation that swallowed the throw after partially writing could pass today.

**Suggested test.** Stub `sessionStorage.setItem` to throw, call `rememberReturnTarget(A_REAL_TARGET)`, and assert `sessionStorage.getItem(RETURN_TARGET_KEY)` is null.

---

## Gap 3 · No discard case covers the path where no target was remembered

**Why it matters.** All seven discard rows remember `A_REAL_TARGET` and assert the target survives. A regression in `CallbackScreen.tsx` that accidentally consumed or corrupted the return target only when it was absent would not be caught, because the absent-target path is only exercised on the success side.

**Suggested test.** Add one discard row (or a separate case) that does not call `remember` first, and assert the discard still lands on `SIGN_IN_ROUTE` and that `RETURN_TARGET_KEY` remains absent from the store.

---

## Gap 4 · No test covers `rememberReturnTarget` overwriting an existing target

**Why it matters.** The producer is idempotent only in the sense that storing the same value twice is harmless, but a second call with a different value should overwrite the first. If a future change made the producer append or ignore the second value, the retry-then-honours case might still pass by accident because it uses the same target.

**Suggested test.** Call `rememberReturnTarget('/account')`, then `rememberReturnTarget('/api-keys')`, and assert `takeReturnTarget()` returns `/api-keys`.

---

## Gap 5 · No test covers a target with leading or trailing whitespace

**Why it matters.** The corrupted-value table includes `' '` (space only) and empty string, but not `/home ` or ` /home`. Set membership would reject both, and a caller using `useLocation().pathname` should never produce them. Still, they are realistic hand-edited-store shapes and worth one row to document the behaviour.

**Suggested test.** Add one row to the corrupted-value table: `seedRaw(' /home')` and `seedRaw('/home ')` each return `HOME_PATH` and consume the value.

---

## Gap 6 · No test asserts the `moduleScopeUsesOfTheRouteTable` scan ignores a re-export

**Why it matters.** The scan excludes lines starting with `import`, but a module-scope re-export such as `export { AUTHENTICATED_ROUTE_PATHS } from '../app/routes';` would match the regex at depth 0 and be flagged. That is probably the desired behaviour, but it is not explicitly tested, and a future refactor might need to know whether the scan treats re-exports as module-scope uses.

**Suggested test.** Add a control row to the scan case: a source containing `export { AUTHENTICATED_ROUTE_PATHS };` should be reported as a module-scope use, and a source containing `export function allowed() { return AUTHENTICATED_ROUTE_PATHS; }` should not.

---

## Summary

Gaps 1 and 2 are the most valuable: they close small holes in the navigation and storage contracts. Gap 3 is a coverage completion for the discard path. Gaps 4, 5, and 6 are polish. The suite as written is already a credible instrument; these changes would make it more complete and less brittle.
