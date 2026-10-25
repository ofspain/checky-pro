# frontend · T16 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T16 — No password field |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

---

## Order of work

The order matters once: the catalogue must exist before the component, because a missing key is a **compile
error** (the catalogue's type is threaded into i18next's `CustomTypeOptions`), so `tsc` would fail on a component
written first. Everything else is independent.

| # | Step | Why here |
|---|---|---|
| 1 | `src/i18n/catalogue.ts` — the five `signIn.*` keys | typed keys must exist before any `t('signIn.…')` compiles |
| 2 | `src/auth/SignInScreen.tsx` | the component, per D-1…D-6, D-8 |
| 3 | `src/app/mountShell.tsx` — the `<I18nextProvider>` | the component is useless without it; `useTranslation` would return raw keys |
| 4 | `src/app/routes.tsx` — `element` on `sign-in` | makes the screen reachable, and is what breaks T02 |
| 5 | `src/app/routesResolveUnderAppPrefixOnly.test.tsx` — narrow the absence assertion (D-9) | **immediately after step 4**, so the suite is never left knowingly red |
| 6 | the new test file | the 14 ACs |
| 7 | run T01, T02, `src/auth`, then the whole suite | in that order, so a failure is attributed before the noise of 531 cases |

Steps 4 and 5 are a single commit. Attaching the element without narrowing the guard leaves a red suite whose
redness I already predicted, and a commit that is knowingly broken is worse than either half.

---

## Step detail

### 1. Catalogue

Append a `signIn` block beside `rateLimit`. Order the keys as the screen reads them — heading, explanation,
action, starting, failed — not alphabetically, so a translator sees them in the order a user meets them.

### 2. `SignInScreen.tsx`

Exactly the frozen component. Points to get right:

- `if (inFlight.current) return;` is the **first** statement — before `setStatus`, before anything awaits.
- `inFlight.current = false` appears **only** in the `catch`.
- `catch {` with no binding, so there is no `error` identifier to be tempted into using.
- No absolute address anywhere, including the doc comment. T01 scans every non-test file under `src/**` for
  `https?://`.
- The doc comment states why there is no password field (L2 — SAS owns the credential steps), because that is the
  task's entire reason for existing and the next reader should not have to find R6.

### 3. `mountShell.tsx`

```tsx
<StrictMode>
  <I18nextProvider i18n={createI18n()}>
    <AppShell>
      <RouterProvider router={createAppRouter()} />
    </AppShell>
  </I18nextProvider>
</StrictMode>
```

`createI18n()` is called in the render expression, once per `mountShell` call, which is once per page load.
The provider is **outside** `AppShell` so `AppShell` keeps rendering only `children` and T01's
`innerHTML === ''` stays true.

### 4. `routes.tsx`

```tsx
...PUBLIC_ROUTE_PATHS.map((path) => (path === 'sign-in' ? { path, element: <SignInScreen /> } : { path })),
```

The file becomes `.tsx`-dependent on JSX — it already is. One import added. The comment above `appRoutes`
("Screens are attached by their own tasks") becomes literally true for the first time and is left as is.

### 5. T02's assertion, narrowed

```ts
const SCREENS_ATTACHED_SO_FAR = ['sign-in'] as const;   // task 17 widens this deliberately

function routesWithElements(routes: readonly RouteObject[]): string[] { … }

it('attaches a screen only to the routes whose task has run (L11, L12)', () => {
  expect(routesWithElements(appRoutes)).toEqual([...SCREENS_ATTACHED_SO_FAR]);
  // the predicate counts: two attached elements are both reported
  expect(routesWithElements([{ path: 'a', element: <i /> }, { path: 'b', Component: () => null }])).toEqual(['a', 'b']);
});
```

The named constant is the policy; the predicate is the mechanism; the second call is the proof the predicate is
not vacuous (D-7).

### 6. The test file

`src/auth/signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx`.

Harness, following `rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx:7-17`:

```tsx
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

function renderSignIn(): HTMLElement {
  const container = document.createElement('div');
  document.body.append(container);
  const root = createRoot(container);
  act(() => root.render(<I18nextProvider i18n={createI18n()}><SignInScreen /></I18nextProvider>));
  return container;
}
```

`location` is stubbed per case with `{ origin, assign: vi.fn() }` — `origin` because T15's module reads it from
the same global, `assign` as the spy. `vi.unstubAllGlobals()` in `afterEach`, which T15 measured as restoring
correctly.

Case groups, mapped to the ACs: no credential input (AC1) · starts the flow (AC2, D-4) · inert until acted on
(AC3) · the three L16 items (AC4) · catalogue-only copy (AC5/D-3) · keyboard and the double-click guard
(AC6/D-1) · structure and the live region (AC7/D-8) · fails closed and retryable (AC8/D-2) · T01 and T02 intact
(AC9/D-7) · no Storage or console of its own (AC13) · reachable at `/app/sign-in` (AC14).

### 7. Verification order

1. `vitest run src/app/shellBuildsAndMounts.test.tsx` — T01's seven, which must not move.
2. `vitest run src/app/routesResolveUnderAppPrefixOnly.test.tsx` — T02, with the narrowed guard.
3. `vitest run src/auth` — T14, T15 and the new file.
4. `tsc --noEmit` and `oxlint`.
5. `vitest run` — the whole suite; expect 531 + the new cases, failing only the two known environmental
   `lintRejectsHandWrittenBackendFetch` cases (AC4's oxlint summary line, AC2's missing `pnpm`).

---

## Risks

| Risk | Handling |
|---|---|
| **`useTranslation` without a provider returns the key string**, so a provider mistake shows `signIn.heading` on screen instead of `Sign in`. | AC5 asserts rendered text equals catalogue *values*, so this fails loudly rather than shipping. |
| **StrictMode double-renders**, and double-*invokes* effects in development. | The handler is not an effect, and `useRef` survives a double render. If a double render ever caused two `assign` calls, D-1's ref stops the second. |
| **jsdom logs `Not implemented: navigation`** if a case forgets to stub `location`. | Noise, not failure — so a stubbed `assign` is asserted *called*, which a forgotten stub cannot satisfy. |
| **The new test imports the real `buildAuthorizeRequest`**, so it depends on `crypto.subtle` in jsdom. | T14 measured both `crypto.subtle.digest` and `getRandomValues` as working in jsdom 30.1.3. |
| **An absolute address slipping into a comment** fails a task-1 test. | Checked deliberately before committing, with the whole-suite run. |

---

## Next

Phase 6 — implementation.
