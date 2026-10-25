# frontend · T16 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T16 — No password field |
| **Spec section** | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 16):**
> **No password field.** The sign-in screen renders no password input. Verifies `signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField`. Cites R6.

---

## Measured before designing

Three things were probed on this host rather than assumed. The third changes the task's scope.

### 1. T01 survives a real screen — re-measured, not taken on trust

Phase 0 said it would re-measure the readiness note's claim. Done: a `<Probe />` element attached to the
`sign-in` route, `shellBuildsAndMounts.test.tsx` run, **7/7 pass**. The note is correct, and the reason is the one
it gives — T01 mounts at jsdom's default location (`http://localhost:3000/`), which is outside the `/app`
basename, so the router matches nothing and the shell stays empty. Probe reverted.

### 2. jsdom neither throws nor navigates

`globalThis.location.assign(url)` and `location.href = url` both **return normally** and emit
`Not implemented: navigation to another Document` on jsdom's virtual console. Neither throws, and neither changes
`location.pathname`. So an unstubbed test would pass while printing noise, and would prove nothing about the
navigation. **The test must stub `location`**, and the stub must also carry `origin`, because T15's
`authorizeRequest.ts` reads `globalThis.location.origin` from the same global. T15 already proved
`vi.stubGlobal('location', …)` works and `vi.unstubAllGlobals()` restores it.

### 3. **T02 holds an absence assertion that T16 must retire**

Running the whole suite with the probe in place turned up a third failure neither phase 0 nor phase 1 predicted:

```
FAIL src/app/routesResolveUnderAppPrefixOnly.test.tsx
  > attaches no screen to any route yet: the paths carry no element or component (L11)
```

`routesResolveUnderAppPrefixOnly.test.tsx:144-152` walks every route and asserts `element`, `Component` and
`errorElement` are all `undefined`. It is the route-table analogue of T01's three "shell renders nothing"
assertions, and T12 faced exactly this situation when it registered the service worker.

**It is retired by narrowing, not by deletion.** Deleting it would throw away a real invariant — that no screen is
attached except by the task that owns it. The replacement asserts the same thing with one exception named:

> exactly one route carries an element, it is `sign-in`, and every other route still carries none.

That keeps the guard working for tasks 17 onward: task 17 attaching the callback screen will have to come back and
widen it deliberately, which is the point. The edit is listed in Files to Modify and is the only change T16 makes
to another task's test.

---

## What gets built

### `src/auth/SignInScreen.tsx` — new

Placed in `src/auth/` per `design.md` §6 ("OIDC client, session owner, guards"), beside `pkce.ts` and
`authorizeRequest.ts`.

```tsx
export function SignInScreen() {
  const { t } = useTranslation();
  const [status, setStatus] = useState<'idle' | 'starting' | 'failed'>('idle');
  const liveRegion = useRef<HTMLDivElement>(null);

  async function startSignIn() {
    setStatus('starting');
    try {
      const { url } = await buildAuthorizeRequest();
      globalThis.location.assign(url);
    } catch {
      setStatus('failed');
      liveRegion.current?.focus();
    }
  }

  return (
    <main>
      <h1>{t('signIn.heading')}</h1>
      <p>{t('signIn.explanation')}</p>
      <button type="button" onClick={() => void startSignIn()} disabled={status === 'starting'}>
        {t('signIn.action')}
      </button>
      <div ref={liveRegion} tabIndex={-1} role="status" aria-live="polite">
        {status === 'failed' ? t('signIn.failed') : null}
      </div>
    </main>
  );
}
```

Each decision and why:

| Decision | Reason |
|---|---|
| **A `<button type="button">`, not a link or an auto-redirect** | R6's "WHEN a visitor chooses sign-in". `type="button"` because there is no form — a bare `<button>` inside one defaults to submit. Keyboard operation comes free from a real button (L11, AC6); a `<div onClick>` would not. |
| **No `<form>`, no `<input>` at all** | AC1. Not merely no `type="password"`: the SPA collects no credential of any kind, because SAS does (L2, `agents.md:23-24`). |
| **`globalThis.location.assign(url)`** | A full-page navigation, because the destination leaves the SPA. A router navigation would try to resolve `/oauth2/authorize` inside the `/app` basename and render the catch-all. `globalThis.` prefix matches T15's idiom. `assign` over `href =` because it is a method, so a stub's call can be asserted with its argument. |
| **`status` as a three-state value, not two booleans** | `idle`/`starting`/`failed` cannot represent "starting and failed at once". The button is disabled only while `starting`. |
| **The button is disabled during the attempt** | A second click would build a second authorize request, overwrite the first attempt's three L16 items, and fire a second navigation. T15's suite proves the overwrite is clean, but firing two navigations is still wrong. |
| **Failure leaves `status === 'failed'`, not `'starting'`** | The action must be retryable (AC8). |
| **The live region is always rendered, with the message conditional inside it** | `RateLimitNotice.tsx:33-35` establishes this: a live region added to the DOM at the same moment as its text may not be announced. The region exists from first paint; only its content changes. |
| **`tabIndex={-1}` on the region, focused on failure** | OQ-3's answer. Programmatically focusable only, so it never joins the tab order. On failure a keyboard user is moved to the message instead of left on a button that appeared to do nothing. Mirrors `RateLimitNotice`'s focus handling. |
| **`<main>` as the wrapper** | A landmark, so a screen-reader user can jump to content (L11, WCAG AA). |
| **One `<h1>`** | AC7. |
| **No `className`, no styling** | There is no CSS system in this package yet and no task has introduced one. `RateLimitNotice` is the precedent: plain elements, no classes. T16 introduces no styling vocabulary it would have to defend. WCAG AA contrast is therefore the browser default, which passes. |
| **`catch` with no binding** | The rejection's content is deliberately unused: T15 can reject with a `TypeError` naming the verifier contract, and that must not reach the screen or the console. The uniform-copy habit (L4) starts here even though this is T16's own failure, not a SAS one. |

### `src/app/mountShell.tsx` — modified

```tsx
const i18n = createI18n();
root.render(
  <StrictMode>
    <I18nextProvider i18n={i18n}>
      <AppShell>
        <RouterProvider router={createAppRouter()} />
      </AppShell>
    </I18nextProvider>
  </StrictMode>,
);
```

`createI18n()` is called **inside** `mountShell`, once per mount, matching its contract ("one i18n instance per
tab… reaches components only through `<I18nextProvider>`; there is no global instance"). The provider wraps
`AppShell` rather than sitting inside it, so `AppShell` keeps rendering only its children and T01's
`innerHTML === ''` assertion is untouched.

### `src/app/routes.tsx` — modified

The `sign-in` entry gains `element: <SignInScreen />`. Every other path is unchanged. The file becomes the one
place that knows which screens exist, which is what T02 designed it for ("Screens are attached by their own
tasks").

### `src/i18n/catalogue.ts` — modified

```ts
signIn: {
  heading: 'Sign in',
  explanation: 'Continue to the secure sign-in page to enter your details.',
  action: 'Continue to sign in',
  failed: 'Sign-in could not be started. Try again.',
},
```

**This copy is OQ-1 and is the owner's to change.** It is neutral, says nothing about account state (L4), and
promises nothing about what SAS will ask for. Because the catalogue is typed `as const` and threaded into
i18next's `CustomTypeOptions`, a missing key is a compile error — so the keys are safe to reference before the
wording is final.

### `src/app/routesResolveUnderAppPrefixOnly.test.tsx` — modified

The one absence assertion narrowed, as set out above.

### `src/auth/signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx` — new

Named for `package.md` §8's test name.

---

## Required tests

Rendering uses `createRoot` + `act()` with `IS_REACT_ACT_ENVIRONMENT = true`, inside an `<I18nextProvider>` —
the pattern from `rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx:7-17`. **No new dependency**; there is
no `@testing-library/react` in this package.

**AC1 — no password field, and no credential input at all.**
- `container.querySelector('input[type=password]')` is `null`.
- `container.querySelectorAll('input')` is empty, and so are `textarea`, `select` and `[contenteditable]`.
- `container.querySelector('form')` is `null`.
- **Non-vacuity (AC12):** the same matchers, run against a fragment that *does* contain a password input, find it.
  Otherwise a test that rendered nothing at all would pass.

**AC2 — choosing sign-in starts the flow.**
- With `location` stubbed, clicking the button calls `assign` exactly once.
- The URL it was called with parses, and carries `response_type=code`, `code_challenge_method=S256`, and
  `scope=openid profile email`. T15 owns the full shape; T16 asserts the three that make this "the authorization
  code flow with PKCE" in R6's words, against the real `buildAuthorizeRequest`, not a mock.
- The URL's path is `/oauth2/authorize` and its origin is the stub's origin.

**AC3 — nothing happens until the visitor acts.**
- On mount: `assign` not called, `sessionStorage` empty, and a `Storage.prototype.setItem` spy not called.

**AC4 — the three L16 items are present after the action, and nothing else.**
- `Object.keys(sessionStorage)` sorted equals the three `AUTHORIZE_STORAGE_KEYS` values.
- Written by T15: asserted by checking the stored verifier derives the challenge that went into the URL —
  the same correspondence T15 uses, which no fourth key can fake.

**AC5 — catalogue-only copy.**
- Every visible string equals its `en` catalogue value, read from `catalogue.ts` rather than retyped.
- The component source contains no quoted string that appears in the rendered text.
- **Non-vacuity:** the assertion is fed a string that is *not* in the catalogue and reports it.

**AC6 — keyboard operable.**
- The action is a `<button>` with `type="button"`.
- It has an accessible name equal to the catalogue's `signIn.action`.
- It is not `disabled` at rest, and it is reachable: `tabIndex` is not negative.
- A keyboard activation path works: dispatching `click` (what both Enter and Space produce on a button) starts
  the flow.

**AC7 — structure.**
- Exactly one `<h1>`, with the catalogue heading.
- A `<main>` landmark.
- A region with `role="status"` and `aria-live="polite"`, present on first render while empty.

**AC8 — failure stays, announces, does not navigate.**
- With `buildAuthorizeRequest` mocked to reject: `assign` not called, the live region holds the catalogue's
  `signIn.failed`, `sessionStorage` is empty, the button is enabled again, and a second click can succeed.
- The rejection's own message does not appear anywhere in the DOM.

**AC9 — T01 and T02 stay honest.**
- T01's seven cases pass untouched (verified by running them).
- T02's narrowed assertion: exactly one route has an element, it is `sign-in`, all others have none —
  and the negative control is that a second attached element fails it.

**AC10 — nothing else modified.** `git diff --stat` shows only the six files.

**AC11 — measured suite delta.**

**AC12 — the vacuity sweep.** Each of AC1, AC5 and AC9's matchers fed a deliberately wrong input.

---

## Constraints

- **No absolute address anywhere in the new source, including comments and any `xmlns`.** T01 scans every
  non-test file under `src/**` for `https?://`. This is the trap phase 0 flagged for a UI task.
- **No `import.meta.env`.** Same scan.
- **No new dependency.** No component library, no CSS framework, no `@testing-library/react`.
- **`src/auth/pkce.ts` and `src/auth/authorizeRequest.ts` are closed.** T16 imports from them and changes neither.
- **No `sessionStorage` write by T16.** T15 writes the three items; T16 adds no key (L16).
- **No new route path.** Only an `element` on an existing one (L14).
- **No styling vocabulary.** Deferred until a task owns it.

## Null handling

`buildAuthorizeRequest()` either resolves with a complete request or rejects, and T15's suite proves it leaves
`sessionStorage` empty when it rejects — so there is no partial state for T16 to clean up. The `catch` therefore
only updates the screen. `liveRegion.current` may be `null` under React's rules, so the focus call is optional-
chained; the announcement does not depend on the focus succeeding.

---

## Open questions

**OQ-1 (carried from phase 1) — the sign-in copy is unspecified.** Four strings are proposed above. Neutral, no
account-state disclosure, no promise about SAS's steps. **Owner's to reword in one file; gates nothing.**

**OQ-2 (resolved here) — `RateLimitNotice` is not mounted.** T16 provides the `<I18nextProvider>` the component
has been waiting for, which is what T07's note assigns to task 16. Mounting the notice itself needs a notice
*state*, which is created per API-calling surface; the sign-in screen makes no API call. The first screen that
does will mount it.

**OQ-3 (resolved here) — focus across the redirect.** Not implementable within one document and not claimed. What
is implemented: focus moves to the live region when the attempt fails, so the keyboard user lands on the
explanation.

**OQ-4 (new) — T02's absence assertion is narrowed, not deleted.** Flagged because T16 edits another task's test.
The alternative — deleting it — would retire a live invariant for every later screen task. If the owner prefers
deletion, that is a one-line change; the narrowing is the conservative reading.

---

## Next

Phase 3 — design challenge. **Self-produced**, at the owner's explicit instruction for this task; Kimi 2.7 is not
waiting on it. Marked as such in the artifact so the record stays accurate.
