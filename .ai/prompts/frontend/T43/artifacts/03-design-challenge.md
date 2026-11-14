<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) -->

# frontend · T43 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T43 — Registration accepts 12 characters |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Findings

### F1 — OQ-T43-5 must be settled now; the only viable answer is a neutral live-region announcement
· **Severity:** **Blocker**
· **Evidence:** `artifacts/02-task-implementation-brief.md §Open Questions` presents three readings for a successful `POST /accounts`: (i) navigate to `register/sent` (blank page today), (ii) announce a neutral result in a `StatusRegion` on the form, (iii) leave the result invisible. Task 47 owns the acknowledgement screen and `register/sent`.
· **Assessment:** Reading (i) ships a blank page, which is worse than the form. Reading (iii) fails L11's *"live regions for async results"*. Reading (ii) satisfies L11, does not steal task 47's route, and gives task 47 a place to supersede the copy later. The announcement must be neutral and identical regardless of response (R2, AC11).
· **Recommended brief amendment:** State explicitly: "After a successful submit, the form fills its pre-mounted `StatusRegion` with a neutral catalogue message (e.g., `register.success`). It does not navigate. Task 47 will later replace or extend this behavior with the `register/sent` acknowledgement."

### F2 — The screen needs at least four new primitives
· **Severity:** **Blocker/Major**
· **Evidence:** AC1 requires a form with an email field, a password field, and a submit control. AC7 requires every element to come from `app/ui/` primitives, and the existing nine primitives (`ActionButton`, `Banner`, `FocusHolder`, `Heading`, `Screen`, `StatusRegion`, `Surface`, `Text`, `TextLink`) render no `<form>`, `<input>`, `<label>`, or submit `<button>`.
· **Assessment:** Four new primitives are the minimum: `Form` (`<form>`), `EmailField` (`<label>` + `<input type="email">`), `PasswordField` (`<label>` + `<input type="password">`), and `SubmitButton` (`<button type="submit">`). `ActionButton` cannot be reused because it sets `type="button"` internally (rule 2).
· **Recommended brief amendment:** Add to the scope: "Create `Form`, `EmailField`, `PasswordField`, and `SubmitButton` primitives in `app/ui/`, exported from `app/ui/index.ts`, and added to the pinned `PRIMITIVES` list."

### F3 — Rule 2 forbids a generic `type` prop on input primitives; use separate field primitives
· **Severity:** **Major/Blocker**
· **Evidence:** `frontend/src/app/ui/index.ts:14-18` lists `type="button"` as a contract attribute that is set inside. `frontend/src/app/ui/ActionButton.tsx:16-22` does exactly that. The brief states OQ-T43-2 resolves to "our own primitives ... written under the same three rules as the nine."
· **Assessment:** A single `TextField` with a `type` prop would violate rule 2 for the same reason `ActionButton` does not accept `type`. The clean design is two primitives: `EmailField` and `PasswordField`. They may share a private implementation helper, but their public types expose no `type` prop.
· **Recommended brief amendment:** Require `EmailField` and `PasswordField` as separate primitives. Do not allow a `type` or `kind` prop that selects the input mode.

### F4 — The registration screen must obtain a generated client without importing from `auth/`
· **Severity:** **Blocker/Major**
· **Evidence:** AC5 requires the submit to go through the generated client (`POST /accounts`). The brief's module boundaries allow imports from `app/ui`, `react`, `react-router`, `react-i18next`, `i18n/catalogue`, and `api/client`. They forbid imports from `auth/`. `createAuthApi` (`frontend/src/api/client.ts:33`) requires a `SessionBoundary`.
· **Assessment:** `SessionBoundary` lives in `api/sessionBoundary.ts`, which is not `auth/`, so it is importable. For a public-only operation, the screen can create a minimal no-op boundary whose middleware does nothing and whose `renew()` rejects. Creating one boundary per screen is not ideal — the design says "one per tab" — but public operations do not use the boundary's renewal logic, so the lifetime does not matter functionally. Alternatively, the screen could call `createAuthClient()` directly from `@themistra/api-client`, but the brief's dependencies point to `api/client.ts` → `createAuthApi`.
· **Recommended brief amendment:** Decide between (a) a module-level no-op `SessionBoundary` inside the registration screen, or (b) a new `createPublicAuthApi()` helper in `api/client.ts` (which would require modifying a file the brief currently marks read-only). Recommendation: (a), because it respects the read-only constraint and the no-`auth/` boundary.

### F5 — Form state should be local `useState`; no TanStack Query or Zustand
· **Severity:** **Major**
· **Evidence:** OQ-T43-4 notes neither library is installed and the owner's answer to OQ-T43-2 excludes new runtime dependencies. AC10 forbids storage writes. R29/L16 forbid persistence.
· **Assessment:** A one-shot public mutation has no cache to invalidate and no shared state. Local `useState` for `email`, `password`, and submission status is the right choice. The decision must be recorded because tasks 44–47 will inherit it.
· **Recommended brief amendment:** State: "The registration screen holds email and password in local `useState`. No external state library is used."

### F6 — The code-point count must be advisory, Unicode-correct, and non-blocking
· **Severity:** **Major**
· **Evidence:** AC2 requires exactly 12 code points to be accepted. AC3 requires astral characters to count as one code point each. AC4 says the check is advisory and gates nothing the server decides.
· **Assessment:** Use `[...password].length` or `Array.from(password).length` to count code points. This correctly handles astral characters (24 UTF-16 units = 12 code points). The check must not throw on empty strings, lone surrogates, or very long pastes. Because it is advisory, the submit button remains enabled and the form submits regardless of length — the only testable claim here is that 12 code points do not block or show an advisory message.
· **Recommended brief amendment:** Specify the helper: "Use `[...password].length` to count Unicode code points. The function returns a boolean (`true` when length is 12) and is used only to decide whether to show an advisory indicator; it never disables submission."

### F7 — Focus management: heading on arrival, status region after submit
· **Severity:** **Major**
· **Evidence:** L11 requires managed focus. T174 established the pattern of focusing the heading on arrival (`HomeScreen.tsx`). `SignInScreen.tsx` uses `StatusRegion` for async results.
· **Assessment:** On arrival, focus should move to the `<h1>` (via `Heading`'s built-in `tabIndex={-1}`), following the T174 precedent. After a successful submit, focus should move to the pre-mounted `StatusRegion` so a screen reader hears the neutral result. The `StatusRegion` must be rendered unconditionally and filled after the response.
· **Recommended brief amendment:** Add: "The screen focuses its heading on mount. After a successful submit, it moves focus to the `StatusRegion` that holds the neutral result."

### F8 — Catalogue keys should be minimal and leave advisory copy to task 45
· **Severity:** **Minor/Major**
· **Evidence:** AC6 requires every string to come from the catalogue. OQ-T43-3 recommends no advisory copy here.
· **Assessment:** Suggested keys: `register.heading`, `register.emailLabel`, `register.passwordLabel`, `register.submit`, `register.submitting`, `register.success`. No password hint, no error message, no strength indicator — those belong to task 45.
· **Recommended brief amendment:** Freeze the key set and note that task 45 may add advisory/rejection copy.

### F9 — Attach the screen in the public route map and grow route-order pins
· **Severity:** **Major**
· **Evidence:** `frontend/src/app/routes.tsx:76-80` maps `PUBLIC_ROUTE_PATHS` to elements. `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx:18` pins `SCREENS_ATTACHED_SO_FAR` in route order.
· **Assessment:** The screen attaches in the existing public `.map()` conditional when `path === REGISTER_PATH` (a new constant). `SCREENS_ATTACHED_SO_FAR` grows from 4 to 5 entries, with `register` sitting third: `['sign-in', 'callback', 'register', 'signed-out', 'home']`.
· **Recommended brief amendment:** Add `REGISTER_PATH = 'register'` to `routes.tsx`, attach `RegisterScreen` in the public map, and update `SCREENS_ATTACHED_SO_FAR`.

### F10 — Grow the design-system instruments for four new primitives
· **Severity:** **Major**
· **Evidence:** `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` pins `PRIMITIVES`, `RENDER_SITES`, the union count, `REQUIRED_UTILITIES`, the ref-target map, and the catalogue key set.
· **Assessment:** Changes needed:
  - `PRIMITIVES`: 9 → 13 (add `Form`, `EmailField`, `PasswordField`, `SubmitButton`)
  - `RENDER_SITES`: 7 → 8 (add `auth/RegisterScreen.tsx`)
  - Union count: 16 → 21 (13 primitives + 8 render sites)
  - Lowercase-tag bearer count: 8 → 12 (TextLink still delegates)
  - `REQUIRED_UTILITIES`: add entries for the four new primitives
  - Ref-target map: add expected tag names (`FORM`, `INPUT`? or per-primitive tags)
  - Catalogue key set: add `register.*`
· **Recommended brief amendment:** Provide a checklist with the expected new values.

### F11 — The new primitives must follow the three rules and forward refs
· **Severity:** **Major**
· **Evidence:** `frontend/src/app/ui/index.ts:12-23` states the three rules. `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:603-641` asserts every primitive forwards its ref.
· **Assessment:** Each new primitive must: (1) accept no `className`/`style`, (2) set its contract attribute inside, (3) carry no user-facing copy, and (4) forward its ref to the rendered element. `EmailField` and `PasswordField` need `useId()` to link `<label htmlFor>` and `<input id>` without exposing `id`/`htmlFor` as props.
· **Recommended brief amendment:** Add a props table for each new primitive. Example: `EmailField: { label: ReactNode; ref?: Ref<HTMLInputElement> }`.

### F12 — The submit test must prove the request goes through the generated client
· **Severity:** **Major**
· **Evidence:** AC5 says "no hand-written `fetch`, no hand-built URL, and exactly one path to the backend."
· **Assessment:** The named test should mock `@themistra/api-client` (or spy on the returned client's `registerAccount`) and assert it is called exactly once with `{ email, password }`. Alternatively, spy on `globalThis.fetch` and assert the request URL and JSON body, but that is weaker because it does not distinguish generated-client usage from hand-written fetch.
· **Recommended brief amendment:** Specify: "Mock the generated auth client and assert `registerAccount` is called once with the submitted email and password."

### F13 — Security: the password is transient and never persisted or logged
· **Severity:** **Major**
· **Evidence:** The brief's constraints state: "No password in any storage, any log, any URL, any analytics, or any DOM attribute that survives the keystroke."
· **Assessment:** The password lives in the `<input>` value while the visitor types and in the local state until the submit completes. It must not be written to `sessionStorage`/`localStorage`, URL, `console.log`, or any attribute like `defaultValue` that survives unmount. The named test should assert no storage write occurred during submission.
· **Recommended brief amendment:** Add an AC10 test that spies on `sessionStorage.setItem`, `localStorage.setItem`, and `fetch`/`console.log` and asserts none are called with password-shaped data.

### F14 — No branching on the response and no artificial delay
· **Severity:** **Major**
· **Evidence:** AC11 cites R2 and L4. The contract returns 202 always.
· **Assessment:** The success path shows the same neutral message regardless of whether the email already exists. There is no error branch, no retry, no spinner delay, and no navigation. The `StatusRegion` simply shows `register.success`.
· **Recommended brief amendment:** Add: "The submit handler awaits the generated client's `registerAccount` and then sets the status to 'success'. It does not inspect the response body, does not branch on status, and does not delay."

### F15 — Update T174's "no OTHER screen takes focus" guard
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx:422-454` loops over `SignInScreen`, `SignedOutScreen`, `CallbackScreen` and asserts each leaves focus on `<body>`.
· **Assessment:** `RegisterScreen` is a new screen that should not take focus on mount (only HomeScreen does). The guard should include it. Otherwise, a future `.focus()` call in the registration screen would not be caught.
· **Recommended brief amendment:** Add `RegisterScreen` to the I9 guard loop.

### F16 — The form should submit on Enter in fields
· **Severity:** **Minor**
· **Evidence:** AC9 requires keyboard-operability.
· **Assessment:** Using a native `<form onSubmit={...}>` with `<button type="submit">` gives Enter-key submission for free. If the design uses `onClick` on the button instead, Enter in the fields would not submit. The `Form` primitive should forward an `onSubmit` handler.
· **Recommended brief amendment:** State that `Form` accepts `onSubmit` and the submit button is `type="submit"`.

### F17 — Test boundary cases for code points
· **Severity:** **Minor**
· **Evidence:** AC2/AC3 require 12 code points and astral-character handling.
· **Assessment:** Named test cases should include: 12 ASCII characters pass; 12 astral characters (e.g., emoji) pass despite 24 UTF-16 units; 11 characters do not pass the advisory check (but still submit, per AC4); an empty string does not crash. The 11 case is the boundary that task 45 will extend.
· **Recommended brief amendment:** List the exact test strings, e.g., `'a'.repeat(12)`, `'👍'.repeat(12)`.

### F18 — A mutation harness must cover all eleven ACs
· **Severity:** **Minor**
· **Evidence:** The brief requires a mutation harness with at least one row per AC.
· **Assessment:** Candidate mutants:
  - Remove the `Form` primitive → AC1 fails.
  - Make `EmailField` render `<input type="text">` → AC1 fails.
  - Make `PasswordField` render `<input type="text">` → AC1 fails.
  - Remove the 12-code-point check → AC2 fails.
  - Use `.length` instead of code points → AC3 fails.
  - Block submit when length ≠ 12 → AC4 fails.
  - Replace generated-client call with `fetch` → AC5 fails.
  - Hardcode English label → AC6 fails.
  - Render a `<form>` tag in the screen → AC7 fails.
  - Add `register` to `AUTHENTICATED_ROUTE_PATHS` → AC8 fails.
  - Remove focus ring from `SubmitButton` → AC9 fails.
  - Write email/password to storage → AC10 fails.
  - Branch on response status → AC11 fails.
· **Recommended brief amendment:** Include these rows in the harness plan.

---

## Summary

T43's design challenge is dominated by **F1** (what a successful submit does) and **F2/F3** (which primitives to add and why they must be separate). The recommended design is: a **neutral live-region announcement on the form** for OQ-T43-5; **four new primitives** (`Form`, `EmailField`, `PasswordField`, `SubmitButton`) following the three rules; a **module-level no-op public boundary** for the generated client; **local `useState`** for form state; **code-point counting via spread**; and **managed focus** to the heading on arrival and the status region after submit. The remaining findings are about instrument growth (**F10**), route attachment (**F9**), security (**F13**), testing (**F12**, **F17**), and the I9 guard update (**F15**). Once OQ-T43-5 is frozen, implementation can proceed.

**Phase 3 complete.** Eighteen findings returned; no code or contract changed.
