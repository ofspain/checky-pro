# frontend · T07 · Phase 7 — Self-Review

Reviews the phase 6 diff (`fe68f3d1`) against the frozen brief and `spec/frontend/agents.md`. Findings only; fixes are
applied in phase 9.

## Findings

### 1. Dismissing drops keyboard focus to `<body>`
- **Issue:** the dismiss button is removed from the DOM when it is activated (`RateLimitNotice.tsx`, the `shown ?
  <button>` branch). A keyboard user who pressed it loses their focus position: the browser moves focus to `<body>`.
  L11 / `agents.md` require managed focus.
- **Severity:** Low. The notice is not mounted anywhere yet, but it will be once the first screen wires it.
- **Evidence:** `RateLimitNotice.tsx`, the conditional button; the probe's after-dismiss markup has no button.
- **Recommendation:** return focus to a sensible target. The component can't know the screen's previous focus, so the
  minimal fix is to make the notice wrapper focusable (`tabIndex={-1}`) and focus it on dismiss when the button had
  focus. Alternatively, record it for the first screen's wiring. Decide at phase 9.

### 2. The `rateLimit` argument is optional, so wiring could forget it
- **Issue:** `createAuthApi(boundary)` without a notice silently loses R23's copy.
- **Severity:** Low (latent until wiring).
- **Evidence:** `client.ts`, `rateLimit?: RateLimitNoticeState`.
- **Recommendation:** no change in T07. Optional keeps T05/T06's tests untouched (frozen brief). The wiring task must
  pass it, and its test should assert a 429 shows the notice end to end. Carry it forward in phase 13.

### 3. A second 429 while the notice is shown isn't re-announced
- **Issue:** `setShown(true)` is a no-op when already shown, so a screen reader hears the copy once per episode, even
  if the user retries and gets another 429.
- **Severity:** Info.
- **Evidence:** `rateLimitNotice.ts`, `if (shown === next) return`.
- **Recommendation:** none. The copy is identical, and re-announcing on every 429 of a burst would be noisy (the brief
  requires one notification per burst). The visible notice stays on screen.

### 4. `subscribe`'s unsubscribe returns a boolean
- **Issue:** `() => listeners.delete(listener)` returns `boolean`, typed as `() => void`.
- **Severity:** Info. It is assignable, and `useSyncExternalStore` ignores the value.
- **Recommendation:** none.

### 5. A rejected `init` would be unhandled
- **Issue:** `void instance.init(...)` discards the promise. With inline resources and `initAsync: false` it can't
  reject (no backend, no plugins), and an unexpected rejection would surface as an unhandled rejection, not be
  swallowed.
- **Severity:** Info.
- **Recommendation:** none.

## Checklist (no finding)
- **Correctness:**
  - status 429 only (`===`);
  - `onResponse` returns `undefined`, so the response passes through unchanged;
  - there is no `onRequest`/`onError`, so nothing is re-sent;
  - the middleware is registered first, so the re-send 429 is seen (probed).
- **R23:**
  - no body or header read;
  - no timer or interval in the notice path (the probe's one `setTimeout` was T05's renewal timeout);
  - the copy has no digit;
  - cleared only by `dismiss()`.
- **L11 / O3:**
  - catalogue-only strings, with typed keys (a missing key fails `tsc`, probed);
  - the live region exists before the content;
  - a native `<button>`, so it is keyboard-operable;
  - focus after dismiss: finding 1.
- **Module boundaries:**
  - no module-level mutable state: the notice and the i18n instance are per call;
  - no `initReactI18next` global;
  - T05/T06 untouched;
  - no global `fetch`.
- **Secrets / privacy:** nothing stored or logged.
- **Not applicable:** transactions, idempotency, money, enumeration safety (the copy is identical for every caller and
  endpoint).
