# frontend · T07 · Phase 4 — Frozen Task Brief (Human Approval gate)

STATUS: FROZEN (2026-10-22)

Consumes `02-task-implementation-brief.md` and `03-design-challenge.md` (Kimi 2.7: 10 findings; 2 High, 6 Low, 2
Info). Each was checked against the brief and against the packed `i18next@26.4.2` / `react-i18next@17.0.16` sources,
unpacked in the container's `/tmp`, outside the repo. None needs an owner trade-off, so they are settled here, as at
the earlier gates.

## Dispositions

| # | Finding | Disposition |
|---|---|---|
| 1 | The factory lacks synchronous init and `initReactI18next` | **PARTLY ACCEPTED.** Synchronous initialisation is needed, and the brief named it but not the option. **Kimi's option name is wrong for this version:** i18next 26's `InitOptions` has `initAsync?: boolean` (`typescript/options.d.ts:736`) and no `initImmediate`, so the snippet would fail `typecheck`. The brief now says `initAsync: false`. **`initReactI18next` is rejected:** it registers a module-global instance (`setI18n`), the shared state the brief avoids. `useTranslation` resolves `props.i18n`, then the context, then `getI18n()` (`useTranslation.js:28`), and creates `reportNamespaces` itself (`:29`), so the provider alone is enough. **`useSuspense: false` is rejected:** a ready instance never suspends, and with `false` a not-ready instance renders keys with only a warning (`:143`), the raw-key outcome O3 forbids. With the default, a wiring mistake fails loudly instead. |
| 2 | How the per-tab instance reaches the component | **ACCEPTED, option (a).** `<I18nextProvider i18n={…}>` is the wiring contract, one instance per tab. The component takes no `i18n` prop. |
| 3 | The type and component share a name | **ACCEPTED.** The state type is `RateLimitNoticeState`, and the component stays `RateLimitNotice`. |
| 4 | "Before" is ambiguous | **ACCEPTED.** It now reads "first in registration order, runs `onResponse` last", with a comment in `client.ts`. The re-send case and the "registered after" mutation break guard it. |
| 5 | An always-present region could break the shell test if mounted early | **ACCEPTED as documentation.** The component's doc comment states it. No shell file is touched (L12; `AppShell.tsx` is in Files NOT to Modify). |
| 6 | Test boilerplate | **ACCEPTED.** A `renderNotice(state)` helper, local to the test file. |
| 7 | What dismiss resets | **ACCEPTED.** A sentence states that the state is the boolean only. |
| 8 | The re-send case is complex | **ACCEPTED.** `renewThen429()` uses the T05 patterns, copied locally (the T05 helpers aren't exported, and T05's test is not modified). |
| 9 | No raw-key mutation break | **ACCEPTED.** Added: remove the catalogue resources. The catalogue case asserts the text equals the catalogue value and isn't the key. |
| 10 | Live-region timing | **No change; the assertion is made explicit.** The region is empty before the 429 and holds the copy after. |

Open Questions: none. The three Phase 2 interpretations stand:
- T07 sets up the catalogue;
- the notice clears only on dismiss;
- it is not mounted until the first screen.

---

## Frozen brief

### Task
T07 — **429 generic copy.** An SPA-originated 429 shows fixed copy with no countdown and no interval. Verifies
`rateLimitedResponseShowsGenericCopyWithoutReplay`. Cites R23.

### Purpose
Give the client boundary one place that recognises a 429 on any SPA call and raises a single, fixed "please wait" notice
from the catalogue, with no timer, no number and no retry. Screens never handle 429 themselves (`agents.md`).

### Scope
**In**
- **`createRateLimitNotice()`** (type `RateLimitNoticeState`, finding 3): a per-tab instance that holds one boolean, "shown". It exposes an openapi-fetch
  middleware that sets "shown" on any 429, `subscribe`/`isShown` for React, and `dismiss()`.
- **`createAuthApi(boundary, rateLimit?)`:** registers the notice's middleware **first in registration order**, so
  it runs `onResponse` **last** and sees the final response (see Probes; finding 4).
- **The i18n catalogue (O3):**
  - `react-i18next`, English only;
  - a `createI18n()` factory returning an initialised instance (no shared global instance);
  - typed keys, so a missing key fails `typecheck`;
  - the first two strings.
- **`<RateLimitNotice notice={…} />`**, under an `<I18nextProvider i18n={createI18n()}>` (finding 2): a live region showing the catalogue copy, plus a dismiss button.
- **`rateLimitedResponseShowsGenericCopyWithoutReplay`:** the T07 cases.

**Out**
- **The POST no-replay guarantee and its cases:** task 8, same named test. T07 adds no replay of its own.
- **Mounting the notice in the running app:** T01's `shellBuildsAndMounts` asserts the shell renders no markup at all,
  and no screen or provider tree exists yet. Mounting comes with the first screen's wiring, alongside the real
  `SessionTokens` (auth tasks 14–23 / the sign-in screen, task 16).
- **Styling.** Tailwind and Radix (O2) arrive with the first screen.
- **Other service clients** (they don't exist yet). The notice's middleware is client-agnostic.
- **SAS-hosted page 429s** (R23: out of SPA scope).
- **Any change to T05/T06 behaviour.**

### Business Rules
- **R23, this task:** an SPA-originated 429 shows fixed, generic waiting copy, with no countdown or interval, and
  resumes only on user action.
- **R23, task 8:** no automatic replay of a non-idempotent POST. T07 must not add a replay.

### Locked Decisions
- **`agents.md`, API access:** the boundary owns fixed generic 429 handling; no countdown; screens don't reimplement it.
- **`agents.md`, errors:** problem types map to copy; server text is never shown.
- **L6:** generated client only.
- **L11 / `agents.md`, i18n:** catalogue-only strings and a live region.
- **O3:** react-i18next, English only, fall back to English and never a raw key.
- **L4:** no lockout timers on public surfaces. The one live 429 is password reset, a public operation.
- **L12:** T05/T06 untouched in behaviour, and their suites unchanged.
- **§5:** nothing persisted.
- **Q12(b):** no dependence on the 429 body or `Retry-After`. Detection is by **status alone**.

### Dependencies
- **New, exact pins, each at least 2 weeks old:**
  - `i18next@26.4.2` (published 2026-09-03; no peers);
  - `react-i18next@17.0.16` (published 2026-10-06; peers `react >= 16.8`, `i18next >= 26.2.0`,
    `typescript ^5 || ^6 || ^7`, all met).

  Install in the container, then restart it.
- **Existing:** `@themistra/api-client` (`createAuthClient`), the T05 `Middleware` type and `SessionBoundary`.

**Probed 2026-10-22:**
- **The live 429** (through the edge, junk token, so no real account was affected): `POST /accounts/password-reset`
  gave 400 ×5, then `429` `application/problem+json`, type `…/rate-limit-exceeded`, title `Too many requests`,
  `Retry-After: 11`. It is the only SPA-originated 429 source today. `/login` is SAS-hosted, and no refresh grant is
  used. R42's edge limiter may add others on any route, with any body.
- **Middleware order** (a scratch Vitest file, deleted afterwards). The first request got 401; after the T05 renewal,
  the re-send got 429:
  - a middleware registered **before** T05's saw `[429]`, the final response;
  - one registered **after** saw only `[401]`.

  openapi-fetch runs `onResponse` in reverse registration order, and T05's re-send bypasses middleware. **So the notice
  middleware must be registered first.**

### Inputs
HTTP responses from calls made through a client that carries the notice's middleware.

### Outputs
- `notice.isShown()` becomes `true`, and subscribers are notified, when a response has status 429.
- `<RateLimitNotice>` renders the catalogue copy while shown.
- The `Response` itself is passed through unchanged: the middleware returns `undefined`.

### State Changes
In memory only: one `shown` boolean per notice instance. Nothing reaches storage, cookies, the URL or logs.

### Behaviour (normative)
- **Middleware `onResponse`:**
  - `if (response.status === 429) show()`, and return `undefined`;
  - no `onRequest`, no `onError`, no re-send, no reading of the body or headers.
- **`show()`:** if not already shown, set `shown = true` and notify the subscribers. A burst of 429s gives one notice
  and one notification.
- **`dismiss()`:** if shown, set `shown = false` and notify. This is the only way the notice clears ("resume only on
  user action"). It sets no timer and never clears by itself, including after a later successful response.
  - The user's own next action, such as resubmitting a form, is how work resumes. The handling never resumes anything
    itself.
  - A 429 after a dismissal shows the notice again.
- **Subscriber notification** is synchronous. A throwing subscriber is rethrown in a microtask, as T05 does with its
  failure hook, so it doesn't affect the response chain or other subscribers.
- **`<RateLimitNotice notice>`:**
  - a wrapper with a live region, `<div role="status" aria-live="polite">`, **always rendered**, so the announcement
    fires when its text appears;
  - while shown, the region contains `t('rateLimit.message')`, and a `<button type="button">` labelled
    `t('rateLimit.dismiss')` follows it, outside the region, calling `dismiss()`;
  - no focus theft, and no text when not shown.
- **Catalogue (en):**
  - `rateLimit.message`: "Too many requests right now. Wait a moment, then try again."
  - `rateLimit.dismiss`: "Dismiss"

  The copy contains no digit and no time unit.
- **`createI18n()`:** `i18next.createInstance()`, then `init` with:
  - `{ lng: 'en', fallbackLng: 'en', resources: { en: { translation } }, interpolation: { escapeValue: false },
    initAsync: false }`;
  - `initAsync: false` is i18next 26's name for synchronous initialisation (finding 1), so the instance is ready on
    the first render;
  - **no `initReactI18next`**: that plugin registers a global instance. The instance reaches components only through
    `<I18nextProvider>` (finding 2).

  The type augmentation of `react-i18next`'s `CustomTypeOptions` types the keys against the catalogue.
- **Provider contract (finding 2):** every tree that renders catalogue copy is wrapped in
  `<I18nextProvider i18n={…}>`, with one instance per tab, created in wiring. Tests do the same through a local helper.
- **Scope of the notice state (finding 7):** the instance holds only the `shown` boolean. It has no retry queue and no
  request log, and it doesn't interact with the boundary's renewal or the transport.
- **Mounting (finding 5):** the component's doc comment says it must not be rendered inside `AppShell` until the first
  screen adds the provider tree, because T01 asserts an empty shell.
- **Order comment (finding 4):** `client.ts` states why the notice middleware is registered first.

### Files to Create
- `frontend/src/api/rateLimitNotice.ts`: `RateLimitNoticeState` and `createRateLimitNotice()`.
- `frontend/src/i18n/catalogue.ts`: the English resources, `as const`.
- `frontend/src/i18n/i18n.ts`: `createI18n()` (no `initReactI18next`) and the type augmentation.
- `frontend/src/app/RateLimitNotice.tsx`: the component, using `useSyncExternalStore` and `useTranslation`.
- `frontend/src/api/rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx`.

### Files to Modify
- `frontend/src/api/client.ts`: `createAuthApi(boundary, rateLimit?: RateLimitNoticeState)` registers
  `rateLimit.middleware` first. It is optional so the T05/T06 callers stay unchanged, and production wiring passes it.
- `frontend/package.json` and `pnpm-lock.yaml`: the two dependencies.

### Files NOT to Modify
- `sessionBoundary.ts`, `crossTabRenewal.ts`, `AppShell.tsx`, `mountShell.tsx`, `routes.tsx`, `main.tsx`.
- Every T01–T06 test.
- `.oxlintrc.json`.
- `libs/**`, `contracts/**`, `spec/**`, `services/**`, `infra/**`.
- `prompt`.

### Acceptance Criteria
- **AC1:** a 429 on a protected operation, on a public operation (`POST /accounts/password-reset`), and on T05's
  re-send after a renewal each set the notice shown, and the component renders the copy.
- **AC2:** the copy is identical for `Retry-After` 1 / 3600 / absent / an HTTP date, for different problem
  titles/details, and for an empty or non-JSON body. No server text appears.
- **AC3:** no digit appears in the rendered notice. Handling a 429 calls neither `setTimeout` nor `setInterval`.
- **AC4:**
  - the notice stays shown across time and later 200s, and clears only on the dismiss button (a keyboard-activatable
    `<button>`);
  - a later 429 re-shows it;
  - a burst of N 429s gives one notification.
- **AC5:**
  - the copy sits in a `role="status"` live region that exists before the 429;
  - the strings come from the catalogue: the rendered text equals `catalogue.rateLimit.message`, with no raw key.
- **AC6:** each 429 reaches the caller unchanged (`response.status === 429`), and the transport is called once per
  call (no replay added).
- **AC7:**
  - 200, 400, 401 (renewed or not), 403 and 503 don't show the notice;
  - `createAuthApi(boundary)` without a notice behaves exactly as in T05;
  - the T05/T06 suites are unchanged and green.
- **AC8:** nothing is written to storage or cookies, and nothing is logged.

### Required Tests
**`rateLimitedResponseShowsGenericCopyWithoutReplay`**, in Vitest and jsdom. It renders through `react-dom/client` +
`act`, as T01 does, with the transport stubbed via `vi.stubGlobal('fetch')` and browser-style `Request` resolution as
in T05. Cases:
1. AC1, protected;
2. AC1, public;
3. AC1, the re-send after a renewal;
4. AC2, the copy-invariance table;
5. AC3, no digits and no timers;
6. AC4, persistence, dismiss, re-show and the burst;
7. AC5, the live region and the catalogue;
8. AC6;
9. AC7, non-429 statuses;
10. AC7, no notice passed;
11. AC8;
12. a throwing subscriber doesn't break the response.

**Test helpers, local to the file** (findings 6, 8):
- `renderNotice(state)`: a fresh `createI18n()`, `<I18nextProvider>`, a `react-dom/client` root and `act`;
- T05-style `installBrowserRequest` / `installTransport` / `fakeSession`, copied locally rather than imported, plus
  `renewThen429()`, which wires a real `createSessionBoundary` so the first send gets 401 and the re-send gets 429.

The live-region case asserts that the region exists and is empty **before** the 429, and holds the copy after
(finding 10).

**Mutation breaks:**
- the middleware registered after T05's;
- the copy taken from the problem `title`;
- `Retry-After` interpolated into the copy;
- a `setTimeout` auto-dismiss;
- no live region;
- notify on every 429;
- a replay on 429;
- matching `>= 429` / `5xx`;
- a hardcoded string instead of the catalogue;
- the catalogue resources removed, so a raw key renders (finding 9).

### Constraints
- `strict` TypeScript.
- No module-level mutable state: the notice and the i18n instance are created per tab.
- No storage, logging or URL use. No reading of the 429 body or headers.
- No global `fetch`, so the lint stays green.
- The two dependencies only, pinned exactly.
- The build stays green. `shellBuildsAndMounts` is unchanged, so the shell still renders no markup.

### Open Questions
No blockers. These interpretations stand (Kimi raised no objection to them):
1. **T07 sets up the catalogue** (O3), because no task owns it and this is the first user-facing string.
2. **"Resume only on user action"** is read as: the notice clears only on dismiss, and the user's own next action
   resumes work. It doesn't clear on a later success.
3. **The notice isn't mounted in the running app yet.** Mounting needs a provider tree, and T01's test asserts an
   empty shell. It comes with the first screen.
