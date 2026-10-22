# frontend · T07 — Phase 3 — Design Challenge

## Scope and method

This is an adversarial review of `artifacts/02-task-implementation-brief.md` for **T07 — 429 generic copy**. Findings are against the brief itself; no code was changed.

---

## Findings

### 1. The i18n factory is missing the settings needed for synchronous, non-global use
**Issue:** The brief specifies `createI18n()` with `lng`, `fallbackLng`, `resources`, and `interpolation: { escapeValue: false }`. It does not mention two things required for the intended usage:
- `initReactI18next` must be plugged in for `useTranslation` to read from the instance;
- `initImmediate: false` is needed for `init()` to finish synchronously so the first render has strings.
Without these, `useTranslation` will either bind to the global i18next instance, suspend/return the key, or require an async readiness dance.  
**Severity:** High — the component cannot render the catalogue copy on first paint as specified.  
**Evidence ·** Brief §Behaviour `createI18n()`: only `lng`, `fallbackLng`, `resources`, `interpolation`. Brief §Files to Create `frontend/src/i18n/i18n.ts`.  
**Recommendation:** Amend the factory config to:
```ts
import i18next from 'i18next';
import { initReactI18next } from 'react-i18next';

const instance = i18next.createInstance();
instance.use(initReactI18next).init({
  lng: 'en',
  fallbackLng: 'en',
  resources: { en: { translation: catalogue } },
  interpolation: { escapeValue: false },
  initImmediate: false,
  react: { useSuspense: false },
});
return instance;
```
Also surface the `initReactI18next` import in the dependencies/files sections.

### 2. No mechanism is specified for getting the per-tab i18n instance into the component
**Issue:** `useTranslation()` from `react-i18next` reads from context (`I18nextProvider`) or falls back to the global i18next. The brief says there is no shared global instance, and the component signature is `<RateLimitNotice notice={…} />` with no `i18n` prop. It is undefined how the per-tab `createI18n()` instance reaches the component.  
**Severity:** High — without a provider or prop, the component will use the wrong instance or fail typecheck.  
**Evidence ·** Brief §Files to Create: `frontend/src/app/RateLimitNotice.tsx` uses `useTranslation`; `frontend/src/i18n/i18n.ts` exports `createI18n()`. No `I18nextProvider` or `i18n` prop is mentioned.  
**Recommendation:** Either (a) require tests and future screens to wrap the tree in `<I18nextProvider i18n={i18n}>`, documenting it as the production wiring contract, or (b) add an `i18n` prop to `<RateLimitNotice notice={…} i18n={…} />` and call `useTranslation(undefined, { i18n })`. Option (a) is more idiomatic for react-i18next and keeps the component prop small; document it explicitly.

### 3. The notice instance and the React component share the same name
**Issue:** `frontend/src/api/rateLimitNotice.ts` exports a `RateLimitNotice` instance type/object, and `frontend/src/app/RateLimitNotice.tsx` exports a `RateLimitNotice` component. Any file importing both must alias one of them, which is error-prone and confusing.  
**Severity:** Low — solvable with aliases, but unnecessary friction.  
**Evidence ·** Brief §Files to Create.  
**Recommendation:** Rename the API instance type to `RateLimitNoticeState` or `RateLimitNoticeModel`, and keep the React component as `RateLimitNotice`. Update `createAuthApi(boundary, rateLimit?: RateLimitNoticeState)` accordingly. If renaming is not allowed, at least document the collision in the brief.

### 4. Middleware order wording is ambiguous
**Issue:** The brief says "registers the notice's middleware **before** T05's". Because openapi-fetch runs `onResponse` in reverse registration order, "before" here means "first in registration order" so the notice runs last and sees the post-renewal 429. A future maintainer could read "before" as "closer to the fetch" and reverse the order, breaking AC1's re-send case.  
**Severity:** Low — the probe result is recorded, but the code comment should make it foolproof.  
**Evidence ·** Brief §Dependencies: "Middleware order" probe. Brief §Files to Modify: `createAuthApi(boundary, rateLimit?)` registers the notice first.  
**Recommendation:** Add a normative comment in `client.ts`: `// Register the 429 notice first; openapi-fetch runs onResponse in reverse order, so it sees the final response after T05's renewal/re-send.`

### 5. The live region is always rendered, which could leak into shell assertions if mounted too early
**Issue:** The component must render an empty `<div role="status" aria-live="polite">` even when not shown. The brief defers mounting until the first screen because T01's `shellBuildsAndMounts` asserts the shell renders no markup. If any future wiring accidentally mounts the notice inside the shell, the shell test will fail.  
**Severity:** Low — avoided by scope, but a coupling to remember.  
**Evidence ·** Brief §Scope Out: "Mounting the notice in the running app … comes with the first screen's wiring." Brief §Behaviour: live region always rendered.  
**Recommendation:** Document the constraint in `client.ts` or the component file: the notice must not be rendered inside `AppShell` until the first screen introduces a provider tree. Add a code-level guard comment near the shell mount point if one exists.

### 6. No provision for testing the component without a real i18n instance
**Issue:** Every test that renders `<RateLimitNotice>` must also create and provide an i18n instance. The brief does not list a test helper for this, so each of the 12 cases will repeat boilerplate.  
**Severity:** Low — test maintainability.  
**Evidence ·** Brief §Required Tests: 12 cases render the component.  
**Recommendation:** Add a small test helper (e.g., `renderWithNotice(notice)`) that creates a `createI18n()` instance and wraps the component in `<I18nextProvider>`. Include it in the test file, not as shared module state.

### 7. `dismiss()` is the only resume path, but the brief doesn't say what resumes the *boundary*
**Issue:** AC4 says the notice clears only on dismiss, and "the user's own next action … is how work resumes. The handling never resumes anything itself." This is correct for UI state, but it leaves open whether a dismissed notice also resets any boundary-side state. The notice has no boundary-side state other than the boolean, so the point is moot; however, a future task might add a "retry" action and misread this as needing boundary support.  
**Severity:** Info.  
**Evidence ·** Brief §Behaviour `dismiss()`.  
**Recommendation:** Add a sentence in the locked decisions or behaviour: "The notice instance holds only the shown boolean; it has no retry queue, no request log, and no interaction with the boundary's renewal or fetch state."

### 8. The test harness must simulate T05 renewal to reach the re-send 429 case
**Issue:** AC1 case 3 requires a 429 on the re-send after a renewal. That means the test must wire a T05 `SessionBoundary` with a fake `SessionTokens`, get a 401, renew, re-send, and then 429. This is significantly more complex than the other cases and is easy to get wrong.  
**Severity:** Low — doable, but worth calling out.  
**Evidence ·** Brief §Required Tests case 3.  
**Recommendation:** Provide a helper in the test file, `renewAnd429()`, that sets up the boundary and transport so the re-send returns 429. Reuse the T05 test harness patterns (`installBrowserRequest`, `installTransport`, `fakeSession`) to avoid duplicating T05 internals.

### 9. No mutation break for i18n fallback to raw key
**Issue:** The mutation breaks include "a hardcoded string instead of the catalogue" but not "missing fallback so a missing key renders the raw key". Since the catalogue is tiny and typed, this is unlikely, but a regression that removes `fallbackLng: 'en'` or the resources could show `rateLimit.message`.  
**Severity:** Low.  
**Evidence ·** Brief §Mutation breaks.  
**Recommendation:** Add a mutation break: remove `fallbackLng: 'en'` or the resources and assert the rendered text is not the raw key and the test fails.

### 10. The component's live region may not announce if rendered empty and then populated
**Issue:** jsdom/screen-reader behavior aside, the brief requires the live region to exist before the 429 so the announcement fires when text appears. This is correct. However, if the region is initially absent and then conditionally rendered on `shown`, the announcement would be unreliable. The brief correctly mandates always rendering.  
**Severity:** Info — already handled by the brief.  
**Evidence ·** Brief §Behaviour `<RateLimitNotice notice>`.  
**Recommendation:** No change; ensure the test asserts the region exists before the 429 and contains text only after.

---

## Open Questions

- None blocking. Findings 1 and 2 are the most important: the i18n factory and the instance-to-component wiring must be resolved before implementation starts.
