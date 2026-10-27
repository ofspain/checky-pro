# frontend · T21 · Phase 8 — Independent Code Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T21 — Access token in memory only |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Reviewed the Phase 6 implementation (`frontend/src/auth/accessToken.ts`, `frontend/src/auth/CallbackScreen.tsx`) and the Phase 7 self-review with fresh eyes. No logic bug was found that would change behaviour today, but the implementation is incomplete without its verification half and leaves a few structural assumptions undocumented.

> Note on verification: the local environment does not have `vite-plugin-pwa` installed, so `vitest` cannot start. Findings are from static review of source.

---

## I1 · The named test's in-memory half is not committed

**Issue.** The frozen task brief and the T21 TIB both state that T21 adds the in-memory half to `callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx`. The file on disk contains only T20's verifier half; there are no cases for `current()`, `retainAccessToken`, `clearAccessToken`, `adopt()`, or the boundary conformance tests the brief requires (AC1–AC7, AC9–AC11).

**Evidence.**
- `frontend/src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` is 918 lines and contains no import of `./accessToken`, `retainAccessToken`, `clearAccessToken`, or the `accessToken` object.
- `grep` for `accessToken`, `retainAccessToken`, `clearAccessToken`, or `current()` in the test file finds only the fields of `TokenResponse` (`accessToken: ACCESS_TOKEN`), not the holder.
- The file header still says "This file shares its name with task 21, which adds the in-memory half" — but that addition is absent.

**Recommendation.** Add the in-memory half before treating the implementation as complete. The minimum set: retention over two unrelated token values; `current()` null before and after every failure path; `adopt()` over a held token and with none held; the real holder passed through the real `createSessionBoundary` to assert `Authorization: Bearer <token>` on the wire; the real holder passed through the real `shareRenewalAcrossTabs`; non-leakage sweeps; and source scans for the holder file.

**Confidence.** High — the absence is objective and the brief explicitly requires it.

---

## I2 · AC9 as written is literally false

**Issue.** The frozen brief's AC9 says "The token is held **once**: no second copy in React state, a prop, a `globalThis` property, another module's closure, or a thrown message." The built boundary already holds transient copies: `sessionBoundary.ts:100` reads `current()` into `tokenUsed`, `:108` stores it in a `Map`, and `:104` clones a request that now carries `Authorization: Bearer <token>`. These copies are transient and deleted at `:114`/`:134`, but they exist.

**Evidence.**
- `sessionBoundary.ts:88-91` sets `Authorization: Bearer ${token}`.
- `sessionBoundary.ts:100-108` stores `tokenUsed` in `pending` and clones the request.
- `sessionBoundary.ts` is on the brief's **NOT to modify** list.
- AC5 *requires* the token to reach the `Authorization` header, which is by definition a copy.

**Recommendation.** Adopt the self-review's proposed rewrite and freeze it as T21-D2: "T21 introduces no copy of its own and holds no durable second copy anywhere." Phase 10 should assert this narrower form, not the literal one.

**Confidence.** High — the self-review identified the same issue, and the evidence is in built code.

---

## I3 · `adopt` and `retainAccessToken` both assign `held`

**Issue.** `accessToken.ts:55-57` and `:76-78` are two assignment sites for the same variable. The self-review F3 noted this and proposed that `adopt` delegate to `retainAccessToken` so there is exactly one place where a non-null token is written.

**Evidence.**
- `adopt(token: string) { held = token; }`
- `retainAccessToken(token: string) { held = token; }`
- Both are identical writes; the only difference is the caller's intent.

**Recommendation.** Make `adopt` call `retainAccessToken(token)`, leaving one assignment site. This is a one-line change and reduces the places a future bug can be introduced. I agree with the self-review's disposition to fix this in Phase 9.

**Confidence.** Medium — maintainability, not a behaviour defect.

---

## I4 · `retainAccessToken` accepts any string with no validation

**Issue.** `retainAccessToken(token: string)` writes whatever string it receives. The self-review F7 observes that both production callers already validate — `codeExchange.ts:74-81` rejects missing/empty/non-string `access_token`, and `crossTabRenewal.ts:54` checks `typeof message.token === 'string' && message.token !== ''` before calling `adopt`. But the holder is a public module, and a third caller could pass an empty string or other invalid value.

**Evidence.**
- `accessToken.ts:76-78` assigns `held = token` unconditionally.
- The two current callers guard, but the function itself does not.

**Recommendation.** Either keep the current design (callers validate) and document it as a deliberate constraint in the function comment, or add a lightweight guard that ignores empty strings. If the guard is added, ensure it does not break the `LocalSession` contract or the cross-tab path. I lean toward documenting it, because both callers already have context-specific validation and a third check here would be a second opinion with no new information.

**Confidence.** Low — the current callers are correct; this is defense-in-depth.

---

## I5 · `accessToken.renew()` can become an unhandled rejection

**Issue.** `renew()` returns `Promise.reject(new Error(RENEWAL_NOT_BUILT))`. The built boundary catches it everywhere it calls it (`sessionBoundary.ts:59`, `:120-124`, `:72`), so nothing today produces an unhandled rejection. But `accessToken` is exported, and a future caller outside the boundary could call `renew()` without catching it.

**Evidence.**
- `accessToken.ts:59-63` returns a rejected promise.
- `sessionBoundary.ts` is the only current caller and handles rejection.
- The function is part of the public `LocalSession` object.

**Recommendation.** No production change is needed; the boundary is the intended caller. Phase 10 should include one case that drives `accessToken.renew()` through the real boundary and asserts the rejection is handled (no unhandled rejection, `onRenewalFailed` called once with the fixed message).

**Confidence.** Low — the current code is safe; this is a durability note.

---

## I6 · The composition root remains unwired

**Issue.** `mountShell.tsx` still does not construct `createSessionBoundary`, `createAuthApi`, or `shareRenewalAcrossTabs`. The holder is therefore only used by `CallbackScreen.tsx`; the built boundary and cross-tab modules have no production consumer. This is the same gap the design challenge (Phase 3) identified as OQ-3.

**Evidence.**
- `mountShell.tsx:12-30` builds i18n, `AppShell`, and the router only.
- `client.ts`, `sessionBoundary.ts`, and `crossTabRenewal.ts` describe "production wiring" that does not exist.
- No task in `tasks.md` 1-34 is visibly the wiring task.

**Recommendation.** Decide and freeze whether T21 wires the composition root. If yes, add `mountShell.tsx` to Files to Modify and require the holder be passed into `createSessionBoundary` and `shareRenewalAcrossTabs`. If no, record explicitly that the wiring is deferred to a follow-up task, and that T21's AC5/AC6 acceptance is proven by isolated use of the real consumers rather than end-to-end app wiring.

**Confidence.** Medium — the holder works in isolation, but the app does not yet use it for its intended purpose.

---

## I7 · Forged callback links clear an existing session

**Issue.** `CallbackScreen.tsx:69-79` defines `discard()` to call `clearAccessToken()` for all five failure paths. If a visitor is already signed in and follows `/app/callback?code=1&state=2`, the screen discards and clears the held token, ending that tab's session. The self-review F2 correctly identified this as the least-bad option: not clearing would leave a half-authenticated tab, which R22 forbids.

**Evidence.**
- `CallbackScreen.tsx:74` calls `clearAccessToken()` inside the shared discard closure.
- The route is public, so an attacker can get a signed-in visitor to visit it.

**Recommendation.** No code change; the security/availability tradeoff is correct. Pin it as deliberate behaviour in Phase 10 — add a case that an already-signed-in tab arriving at a forged callback ends with `current()` null and the neutral discard outcome. Record it in the PR notes so it is not reported as a bug.

**Confidence.** Medium — correct behaviour, but easily mistaken for a regression if undocumented.

---

## Summary

The implementation in `accessToken.ts` and `CallbackScreen.tsx` is consistent with the frozen brief's core decisions: one module-scoped variable, no storage, retention only after nonce check, and a defined seam for `renew()`. The strongest issue is the missing in-memory half of the named test (I1). I2 is a brief wording correction that Phase 9 should record. I3 is a small structural cleanup. I4–I7 are observations or handoffs that should be pinned in tests or documentation rather than changed in code.
