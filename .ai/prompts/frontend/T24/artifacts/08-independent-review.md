# frontend · T24 · Phase 8 — Independent Code Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T24 — Return target inside allowlist |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Reviewed the Phase 6 diff (`returnTarget.ts`, `CallbackScreen.tsx`, `routes.tsx`, and the inherited-assertion updates) with fresh eyes. The allowlist design is tight: set membership, derived from the route table, read inside a function to close the import-cycle strand, and total. The strongest issues are two behaviours the code chose without the brief explicitly deciding: the target is not consumed when the visitor leaves, and the write side stores values the read side will reject.

> Note on verification: the local environment does not have `vite-plugin-pwa` installed, so `vitest` cannot start. Findings are from static review of source.

---

## I1 · A visitor who leaves the callback screen keeps their return target forever

**Issue.** `CallbackScreen.tsx:192` places `takeReturnTarget()` inside `if (onScreen.current)`. When the visitor leaves before the exchange resolves, `onScreen.current` is false, `takeReturnTarget()` never runs, and the stored target is never removed. L16 calls the item "single-use and deleted at callback"; here a callback completed (token held, PKCE items consumed) but the fourth item survived it.

**Evidence.**
- `CallbackScreen.tsx:188-192`: the navigation-and-consumption block is guarded by `onScreen.current`.
- The `onScreen` ref is set to `false` in the effect cleanup at `:199-201`.
- The self-review S1 identifies the same issue.

**Recommendation.** Consume unconditionally and guard only the navigation:

```tsx
const target = takeReturnTarget();
if (onScreen.current) navigate(target, { replace: true });
```

This keeps T24-D7's promise (do not move someone who left) while keeping L16's promise (single-use and deleted at callback). Add a test case that unmounts the screen before the exchange resolves and asserts `sessionStorage` no longer contains `RETURN_TARGET_KEY` after the promise settles.

**Confidence.** High — a clear mismatch with L16 and a one-line fix.

---

## I2 · `rememberReturnTarget` will store a value that can never be honoured

**Issue.** `returnTarget.ts:78-85` stores whatever string it receives. T24-D3 correctly puts the authority on the read, but authority and filtering are not the same. A future caller could store `/app/home?token=eyJ…` or `javascript:alert(1)`, where it sits until the callback rejects it. AC8 is about what may be in storage, not only what may be acted on.

**Evidence.**
- `returnTarget.ts:78-85`: `rememberReturnTarget` writes to storage unconditionally.
- `returnTarget.ts:66-68`: `isAllowlistedTarget` already exists and is exported.
- The self-review S2 identifies the same issue.

**Recommendation.** Filter on write using the same predicate:

```ts
export function rememberReturnTarget(target: string): void {
  if (!isAllowlistedTarget(target)) return;
  try { globalThis.sessionStorage.setItem(RETURN_TARGET_KEY, target); } catch { /* ignore */ }
}
```

This does not create a second decision point — the read still decides, because the route table can change between write and read — but it narrows what can ever sit in the store. Update the doc comment to say the function silently ignores non-allowlisted targets, and add a test that stores a rejected shape, calls `takeReturnTarget`, and asserts it falls back to home because nothing was stored.

**Confidence.** High — defense-in-depth for a security boundary.

---

## I3 · A discarded callback leaves the target stored, and the decision is unstated

**Issue.** Both navigation and consumption live on the success path only. Every discard leaves the target in storage. This is almost certainly correct — the visitor retries and still gets where they were going — but it is the mirror of T23-D2 and should be recorded rather than inferred.

**Evidence.**
- `CallbackScreen.tsx:148-197`: `takeReturnTarget` is called only in the success `.then` handler.
- The self-review S3 identifies the same issue.

**Recommendation.** Record the decision in the artifact and add a test case: a discard leaves the target in storage, and a subsequent successful callback honours it. This makes the "single-use" / "survives a failed attempt" tension explicit and resolves it.

**Confidence.** Medium — behaviour is likely correct, but undocumented.

---

## I4 · The parameterized-route exclusion relies on a `:` heuristic

**Issue.** `allowlistedTargets()` at `returnTarget.ts:51-55` filters out any path containing `:`. This correctly excludes `invoices/:id` and `receipts/:id` (T24-D5), but it would also exclude a hypothetical future route with a literal colon in its path, such as `time/12:30`. The brief does not state the rule explicitly.

**Evidence.**
- `returnTarget.ts:51-55`: `.filter((path) => !path.includes(':'))`.
- `routes.tsx:43,46`: the two parameterized routes.

**Recommendation.** Document the heuristic in the brief as the chosen mechanism for excluding parameterized routes, and add a test case that proves `invoices/:id` is not honoured while `invoices` (the non-parameterized parent) is. If the route table ever gains a literal colon, the rule will need revisiting.

**Confidence.** Low — no such route exists today, but the rule should be explicit.

---

## I5 · Five acceptance criteria are not verified by the committed suite

**Issue.** The named test `returnTargetOutsideAllowlistFallsBackToHome.test.tsx` does not exist yet. The suite proves AC11, AC12, AC13, AC14, and structurally AC4, but nothing about AC1 (honoured target), AC6/AC17 (allowlist derivation in either direction), AC15 (corrupted value, throwing store), AC16 (throwing removal), or AC7's read-before-delete ordering as a behaviour.

**Evidence.**
- `glob` for `frontend/src/auth/returnTargetOutsideAllowlistFallsBackToHome*` returns no file.
- The self-review S5 identifies the same issue.

**Recommendation.** Carry these into Phase 10 as the named test's spine. Do not accept the inherited assertion updates as evidence for the allowlist mechanism or the throw-safety contract.

**Confidence.** High — a phase-boundary status finding.

---

## I6 · `takeReturnTarget()` removes before validating, which is correct but worth pinning

**Issue.** `returnTarget.ts:117-124` removes the stored value, then checks `isAllowlistedTarget`. A rejected target is therefore still consumed. This is the right behaviour — a rejected target should not be handed to the next sign-in — but it is the kind of detail a future refactor could reverse.

**Evidence.**
- `returnTarget.ts:117-124`: `removeItem` precedes the `isAllowlistedTarget` check.
- The comment at `:122-123` already explains why.

**Recommendation.** Add a direct test in Phase 10: store a non-allowlisted target, call `takeReturnTarget`, assert it returns home, and assert `sessionStorage` no longer contains `RETURN_TARGET_KEY`. This makes the "consume rejected targets" rule observable.

**Confidence.** Low — already documented, but a test would make it durable.

---

## I7 · T26's carve-out guard must enumerate both constants

**Issue.** `authorizeRequest.ts:32-33` says `AUTHORIZE_STORAGE_KEYS` is published so task 26's carve-out guard enumerates the same keys. T24 adds `RETURN_TARGET_KEY` in `returnTarget.ts`. Task 26 must now read both constants; a guard that enumerates only the first would report the return target as a violation of the carve-out that names it.

**Evidence.**
- `authorizeRequest.ts:32-33`: the comment about task 26.
- `returnTarget.ts:39`: the new key.
- The self-review S4 identifies the same issue.

**Recommendation.** Record the handoff in the artifact and name both constants. No code change: combining them would put the key back in the module whose deletion must not reach it (T24-D4).

**Confidence.** Low — handoff, not defect.

---

## Summary

I1 and I2 are the most important: they close two holes in the security/storage boundary. I3, I4, I6, and I7 are smaller but should be pinned before Phase 4. I5 is the expected Phase 10 boundary. The allowlist itself is as tight as the brief asked; the issues are all at the edges of when and whether the target is written, read, and removed.
