# frontend · T16 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T16 — No password field |
| **Spec section** | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 16):**
> **No password field.** The sign-in screen renders no password input. Verifies `signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField`. Cites R6.

---

## Locked decisions

### D-1 — A synchronous ref guards the attempt; `aria-disabled` is the visual consequence
*(finding 1, finding 2)*

`disabled` is not used for the in-flight state. A `useRef(false)` is checked and set synchronously at the top of
the handler, so two clicks in one task cannot both start a request. The button carries `aria-disabled` while
in flight and **stays enabled as an element**, so a keyboard user does not lose focus.

The ref is **not** reset on success. The page is being replaced; re-enabling would invite a second navigation
during the redirect. It **is** reset on failure, so the attempt is retryable (AC8).

### D-2 — Focus is not moved
*(finding 3)*

The live region announces; focus stays on the button the visitor pressed. `RateLimitNotice`'s focus-move exists
because dismissing unmounts the focused button — that cause does not apply here, so the mechanism does not
transfer. No `tabIndex`, no ref on the region. This is the frozen answer to phase 1's OQ-3.

### D-3 — The no-hardcoded-copy guard is inverted
*(finding 4)*

The guard reads the catalogue's values and asserts no component source contains any of them as a literal, with a
synthetic source as the negative control. The abandoned form — "the source contains no quoted string that appears
in the rendered text" — was unfailable, the same defect class as T15's two surviving mutants.

### D-4 — T15 is authoritative for the request shape; T16 proves only that it sends what T15 built
*(finding 5)*

T16 asserts the URL reaching `assign` is the one `buildAuthorizeRequest()` produced: identical parameter by
parameter except `state`, `nonce` and `code_challenge`, which are fresh per call by design. The three
R6-demonstrating checks (`response_type=code`, `code_challenge_method=S256`, `scope=openid profile email`) stay,
with a comment stating they demonstrate R6's words on the screen that starts the flow and that T15 owns the shape.
Declared duplication, not re-ownership.

### D-5 — `state` and `nonce` are deliberately discarded
*(finding 6)*

The component destructures `url` only. T15 has already written all three L16 items; re-storing them here would add
nothing and could diverge. A comment says so, so the discard is not "fixed" later.

### D-6 — The caught error is discarded, on the record
*(finding 7)*

No `console`, no telemetry, no error text in the DOM. On a screen whose purpose is to never touch credentials, an
absolute no-logging habit is worth more than a case-by-case judgement (`agents.md:82`). Accepted trade-off:
a developer diagnosing a failure gets nothing from the SPA. Revisit when a real telemetry decision exists.

### D-7 — The route-element guard is a predicate, run twice
*(finding 8)*

`routesWithElements(routes)` returns the paths carrying `element`, `Component` or `errorElement`. Asserted to
equal `['sign-in']` over the real `appRoutes`, and shown to count by running it over a locally built array with
two elements. No mutation of module-scope state.

### D-8 — The in-flight state is announced
*(finding 9)*

A fifth catalogue key, `signIn.starting`, placed in the existing live region while the attempt runs, so the
region earns its place on the success path too. Part of OQ-1 for the owner to reword.

### D-9 — T02's absence assertion is narrowed, not deleted
*(phase 2, OQ-4)*

`routesResolveUnderAppPrefixOnly.test.tsx:144-152` becomes: exactly one route carries an element, it is
`sign-in`, every other route carries none. Task 17 will widen it deliberately. This is T16's only change to
another task's test.

### D-10 — No styling, no new dependency, no new route path

Plain elements, no `className`. Nothing added to `package.json`. `element` on an existing route only.

---

## The component, as frozen

```tsx
export function SignInScreen() {
  const { t } = useTranslation();
  const [status, setStatus] = useState<'idle' | 'starting' | 'failed'>('idle');
  const inFlight = useRef(false);

  async function startSignIn() {
    if (inFlight.current) return;      // synchronous: a re-render is too late (D-1)
    inFlight.current = true;
    setStatus('starting');
    try {
      const { url } = await buildAuthorizeRequest();   // state and nonce are already stored (D-5)
      globalThis.location.assign(url);
    } catch {
      inFlight.current = false;        // retryable
      setStatus('failed');             // the error itself is discarded (D-6)
    }
  }

  return (
    <main>
      <h1>{t('signIn.heading')}</h1>
      <p>{t('signIn.explanation')}</p>
      <button type="button" aria-disabled={status === 'starting'} onClick={() => void startSignIn()}>
        {t('signIn.action')}
      </button>
      <div role="status" aria-live="polite">
        {status === 'starting' ? t('signIn.starting') : null}
        {status === 'failed' ? t('signIn.failed') : null}
      </div>
    </main>
  );
}
```

No `<form>`, no `<input>`, no `<textarea>`, no `<select>`, no `[contenteditable]`. That is AC1, and it is a
property of the markup, not of a filter.

---

## Files

| File | Change |
|---|---|
| `src/auth/SignInScreen.tsx` | **new** |
| `src/auth/signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx` | **new** |
| `src/app/mountShell.tsx` | `<I18nextProvider i18n={createI18n()}>` wrapping `AppShell` |
| `src/app/routes.tsx` | `element: <SignInScreen />` on `sign-in` |
| `src/i18n/catalogue.ts` | five `signIn.*` keys |
| `src/app/routesResolveUnderAppPrefixOnly.test.tsx` | the one absence assertion narrowed (D-9) |

Six files. Nothing else — in particular `src/auth/pkce.ts` and `src/auth/authorizeRequest.ts` are closed.

---

## Acceptance criteria, as frozen

AC1–AC12 stand as phase 1 wrote them, with these amendments:

- **AC2 extended by D-4** — the URL reaching `assign` is compared against a directly-built request, not only
  spot-checked.
- **AC5 replaced by D-3** — the inverted catalogue-value scan.
- **AC6 extended by D-1** — the button is *not* `disabled` at any point; it carries `aria-disabled` while in
  flight and keeps focus. A new case fires two synchronous clicks and asserts `assign` was called once.
- **AC7 extended by D-8** — the live region carries the in-flight string while the attempt runs.
- **AC8 extended by D-2** — focus stays on the button; the error text never appears in the DOM.
- **AC9 extended by D-7** — the predicate, run twice.

Added:

- **AC13** — the component renders no `console` call and no `localStorage`/`sessionStorage` access of its own: a
  runtime spy over `Storage.prototype` and every `console` function finds nothing called during mount or during a
  successful attempt beyond what T15 does (three `setItem` calls, from `authorizeRequest.ts`).
- **AC14** — the screen is reachable in the real app: mounting the shell at `/app/sign-in` renders the heading,
  and mounting it outside `/app` still renders nothing (T01's invariant, asserted here too rather than only
  inherited).

---

## Open questions

**OQ-1 — the five sign-in strings are unspecified by the spec and are the owner's to reword.** Frozen proposal:

| Key | Value |
|---|---|
| `signIn.heading` | `Sign in` |
| `signIn.explanation` | `Continue to the secure sign-in page to enter your details.` |
| `signIn.action` | `Continue to sign in` |
| `signIn.starting` | `Taking you to the sign-in page…` |
| `signIn.failed` | `Sign-in could not be started. Try again.` |

Neutral, no account-state disclosure (L4), no promise about which steps SAS will ask for. One file to change.
**Gates nothing.**

**OQ-2, OQ-3, OQ-4 — resolved** by D-2, D-9 and the phase-2 decision not to mount `RateLimitNotice`.

**Carried, gating nothing:** T13/T15's OQ-1 (`import.meta.env` ban versus §4c's four keys — T16 needs no key
either); T12's OQ-1 (a refresh cannot promote a waiting worker); `design.md` §4c calling auth T19 "not built"
while L5 records it as built.

---

## Next

Phase 5 — implementation plan.
