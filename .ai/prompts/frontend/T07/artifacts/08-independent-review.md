# frontend · T07 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T07 — 429 generic copy |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | Current `frontend/src/api/rateLimitNotice.ts`, `frontend/src/app/RateLimitNotice.tsx`, `frontend/src/i18n/i18n.ts`, `frontend/src/i18n/catalogue.ts`, `frontend/src/api/client.ts`, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, `spec/frontend/agents.md` |
| **Produces** | `artifacts/08-independent-review.md` |

**Task statement (verbatim):**
> **429 generic copy.** An SPA-originated 429 shows fixed copy with no countdown and no interval. Verifies `rateLimitedResponseShowsGenericCopyWithoutReplay`. Cites R23.

---

## Review scope

Reviewed the Phase 6 T07 implementation against the frozen brief, `agents.md`, and the Phase 7 self-review. No code was rewritten.

The implementation closely follows the frozen brief. The self-review found five issues (one low, four info/secondary). This review corroborates them and adds two fresh low-severity observations.

---

## New findings

### 1. Multiple `createRateLimitNotice()` instances per tab are not prevented
**Issue ·** `createRateLimitNotice()` returns a new instance on every call. If production wiring accidentally creates two notice instances and passes one to `createAuthApi` while rendering the other, the middleware will update one boolean and the component will subscribe to the other. The notice will never appear. The frozen brief says "one per tab" but the API does not enforce it.  
**Severity ·** Low — production wiring is responsible, but the mistake is silent and hard to debug.  
**Evidence ·** `frontend/src/api/rateLimitNotice.ts:16` (`export function createRateLimitNotice(): RateLimitNoticeState` with no singleton guard).  
**Recommendation ·** Either (a) document loudly that the wiring must create exactly one notice per tab and pass the same instance to both `createAuthApi` and the component, or (b) provide a small factory helper such as `createTabRateLimitKit()` that returns `{ notice, i18n }` from one call. A dev-mode warning when two instances share a `BroadcastChannel`/client would be overkill for T07 but fits the pattern later.

### 2. Future service clients must remember to register the same middleware
**Issue ·** The 429 notice middleware is registered only inside `createAuthApi`. The frozen brief notes that the middleware is client-agnostic and that other service clients don't exist yet. A future payments/notifications client could be built without the notice and silently lose R23 coverage.  
**Severity ·** Low — latent, out of T07 scope.  
**Evidence ·** `frontend/src/api/client.ts:18-23` (registration only in auth client).  
**Recommendation ·** Record a constraint in `design.md` or a code comment in `client.ts` that any future generated client must accept and register the tab's `RateLimitNoticeState` middleware first, matching `createAuthApi`. No T07 code change.

---

## Corroborated findings (from Phase 7 self-review)

| # | Self-review finding | Independent view |
|---|---|---|
| 1 | Dismissing drops keyboard focus to `<body>` | **Confirmed.** The dismiss button is unmounted on click. The brief's "no focus theft" refers to the notice not stealing focus on show, but L11 also requires managed focus after user action. The wrapper `tabIndex={-1}` fix suggested in the self-review is the minimal acceptable fix for T07; a better long-term fix is to return focus to the element that had focus before the notice appeared, which will need wiring support from the first screen. |
| 2 | Optional `rateLimit` argument lets wiring forget it | **Confirmed.** `createAuthApi(boundary)` without a notice is valid and loses R23 copy. This is intentional for T05/T06 compatibility, but the wiring task must assert end-to-end 429 behavior. |
| 3 | A second 429 while shown isn't re-announced | **Confirmed, acceptable.** The brief requires one notification per burst; the visible notice remains. A later 429 after dismissal will announce again because `shown` flips from false → true. |
| 4 | `subscribe` unsubscribe returns a boolean | **Confirmed, acceptable.** `listeners.delete(listener)` returns boolean, but the function is typed `() => void` and `useSyncExternalStore` ignores the cleanup return value. |
| 5 | A rejected `init` would be unhandled | **Confirmed, low risk.** With inline resources and `initAsync: false` the init is synchronous and cannot reject; an unexpected rejection surfaces as an unhandled rejection rather than being swallowed. |

---

## Non-gaps (acknowledged, no change needed)

- **i18n factory.** `initAsync: false` is the correct i18next 26 option for synchronous init with inline resources, and omitting `initReactI18next` avoids a global instance. The `<I18nextProvider>` contract is documented in the component comment.
- **Middleware order.** The notice middleware is registered first and the code comment explains the reverse `onResponse` ordering.
- **No body/header read.** The middleware checks `response.status === 429` only.
- **No replay / no timer.** The middleware returns `undefined`; there is no `setTimeout`/`setInterval` in the notice path.
- **Catalogue and typed keys.** `en` is `as const`, the type augmentation is in `i18n.ts`, and the copy contains no digit.
- **Pass-through responses.** Non-429 responses are untouched; 429 responses are passed through unchanged.
- **No module-level mutable state.** Both the notice state and the i18n instance are created per call/tab.
- **T05/T06 untouched.** `client.ts` still works without a notice; `sessionBoundary.ts` and `crossTabRenewal.ts` are unchanged.
- **No storage, logs, cookies, URL use.** The implementation keeps only the `shown` boolean in memory.

---

## Summary

The T07 implementation is clean and aligns with the frozen brief: status-only 429 detection, one-shot notification, catalogue-backed live region, no timers, no replay, and correct middleware ordering. The only significant behavioral defect is the focus loss on dismiss (self-review finding 1), which should be fixed in Phase 9 by making the wrapper focusable and focusing it when the dismiss button had focus. The two fresh findings are forward-looking API-contract concerns rather than bugs.
