# frontend · T17 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T17 — Missing state discarded |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 17):**
> **Missing state discarded.** A callback without `state` is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

---

## The shape of the problem

A callback screen is not really a screen. It is a side effect with a holding message: read the URL, decide, go
somewhere. Three consequences for the design.

1. **The decision belongs in an effect, not in render.** It navigates and reads storage. `StrictMode`
   double-invokes effects in development, so it needs a `useRef` latch — the same lesson D-1 taught in T16, in a
   different costume.
2. **The URL must be read through the router, not through `location`.** `useSearchParams()` resolves against the
   router's location, which means a memory router can drive every case in the test without stubbing a global.
   T16 stubbed `location` because T15's module reads `location.origin`; nothing here needs that.
3. **There is no success path yet.** Tasks 19–25 build it. T17's "success" is reaching the seam, and the screen's
   honest resting state is a holding message — not a claim that sign-in completed.

---

## What gets built

### `src/auth/codeExchange.ts` — new. The seam that makes AC4 real

```ts
export type AuthorizationCodeGrant = { code: string; state: string };

/**
 * The token exchange. **Task 20 implements this**; T17 only needs it to exist, so that "the callback did not
 * exchange" is a fact about a call site rather than about absent code.
 */
export async function exchangeAuthorizationCode(_grant: AuthorizationCodeGrant): Promise<void> {
  // Deliberately empty until task 20. See 01-specification-extraction.md, AC4.
}
```

This is the whole answer to phase 0's vacuity problem. Without it, "no exchange occurred" is an assertion with
nothing to observe; with it, the callback has a real call site that a spy can watch, and task 20's job becomes
filling in a function that is already wired rather than inventing the wiring.

**It also means T17 ships no vulnerability.** The strict reading of the task — check only for a *missing*
`state`, leave *mismatch* to task 18 — would normally mean shipping a callback that exchanges on an unvalidated
`state`. It does not, because the seam is empty: there is nothing to exchange with until task 20, and task 18
lands first. Worth stating plainly rather than leaving as luck.

### `src/auth/signInNotice.ts` — new. How the notice crosses the navigation (OQ-1)

```ts
export type SignInNotice = 'discarded';

let pending: SignInNotice | null = null;

export function setSignInNotice(notice: SignInNotice): void { pending = notice; }

/** Single-read: taking it clears it, so a refresh or a second visit does not re-scold the visitor. */
export function takeSignInNotice(): SignInNotice | null {
  const taken = pending;
  pending = null;
  return taken;
}
```

Module-scoped and in memory. It satisfies R8's "with neutral copy" without adding a `sessionStorage` key (L16's
carve-out is exhaustive) and without relying on `history.state`, whose persistence makes it a poor fit for a
notice that should not survive a reload. It follows L3's house pattern for in-memory state.

The union type exists so task 18's mismatch notice — if the owner wants a distinct one — is a type change rather
than a string convention. **Today both shapes produce the same `'discarded'` value**, because L4 forbids copy
that reveals which case occurred.

### `src/auth/CallbackScreen.tsx` — new

```tsx
export function CallbackScreen() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const handled = useRef(false);

  useEffect(() => {
    if (handled.current) return;    // StrictMode invokes effects twice in development
    handled.current = true;

    const state = params.get('state');
    const code = params.get('code');

    // R8's antecedent, in T17's half: no state at all. Task 18 adds the comparison with the stored value.
    if (state === null || state === '' || code === null || code === '') {
      setSignInNotice('discarded');
      navigate(`/${SIGN_IN_PATH}`, { replace: true });
      return;
    }

    void exchangeAuthorizationCode({ code, state });
  }, [params, navigate]);

  return (
    <main>
      <h1>{t('callback.heading')}</h1>
      <div role="status" aria-live="polite">{t('callback.working')}</div>
    </main>
  );
}
```

| Decision | Reason |
|---|---|
| `replace: true` | AC5. A discarded callback must not be reachable with the back button; leaving it in history would let a visitor re-trigger a dead URL. |
| Treats an **empty** `state` as missing | AC2. `?state=` is what a stripped or truncated query produces, and it carries no CSRF proof. |
| Treats a **missing `code`** as discard too | Beyond R8's literal words, and flagged as such. There is nothing to exchange without a code, so the alternative is calling the seam with `code: null` and pushing the problem into task 20. Phase 3 should challenge this. |
| Does **not** compare `state` to the stored value | Task 18's. Stated as a comment in the code, so the gap is deliberate rather than forgotten. |
| Does **not** delete the stored items | OQ-2's lean, asserted explicitly so task 23 inherits a stated position. |
| Reads `useSearchParams`, not `location.search` | Testable through a memory router; no global stubbing. |
| An effect with a ref latch | It navigates and sets a module variable — both side effects. The latch survives StrictMode's double invocation. |
| A holding message, not a success claim | The flow is genuinely incomplete until task 20. Saying "signed in" would be a lie. |

### `src/auth/SignInScreen.tsx` — modified

R8 requires the visitor to arrive at the sign-in start **with neutral copy**, so T16's screen renders the taken
notice in the live region it already has:

```tsx
const [notice] = useState(() => takeSignInNotice());
…
<div role="status" aria-live="polite">
  {notice !== null && status === 'idle' ? t('signIn.discarded') : null}
  {status === 'starting' ? t('signIn.starting') : null}
  {status === 'failed' ? t('signIn.failed') : null}
</div>
```

`useState` with an initializer so the take happens **once per mount**, not on every render. The notice yields to
the in-flight and failed states, because a fresh attempt's status is more current than the previous attempt's
epitaph.

### `src/app/routes.tsx` — modified

`element: <CallbackScreen />` on `callback`, and a named export the callback navigates to rather than hardcoding:

```ts
export const SIGN_IN_PATH = 'sign-in';
```

with a test asserting `PUBLIC_ROUTE_PATHS` contains it — so the constant and the route table cannot drift apart,
and the callback's target is not a literal repeated in two files.

### `src/i18n/catalogue.ts` — modified

```ts
callback: {
  heading: 'Finishing sign-in',
  working: 'Checking your sign-in…',
},
signIn: { …, discarded: 'Your sign-in did not complete. Try again.' },
```

**OQ-3: the owner's to reword.** Neutral — it names no cause, so the three discard shapes are indistinguishable
(L4), and it promises nothing about what went wrong.

### `src/app/routesResolveUnderAppPrefixOnly.test.tsx` — modified

`SCREENS_ATTACHED_SO_FAR` becomes `['sign-in', 'callback']`. T16 built that constant precisely so this change is
deliberate, and the predicate is still shown to count.

---

## Required tests

New file `src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` — named for `package.md` §8, and **the file
task 18 will extend** with the mismatch cases.

Harness: `createMemoryRouter` over the real `appRoutes` with `basename: APP_BASENAME`, driven by
`initialEntries`, rendered through `createRoot` + `act()` inside an `<I18nextProvider>`. Routers are disposed in
`afterEach`, following T02's convention — the one T16's AC14 case could not follow and recorded as a trade-off.

| AC | Cases |
|---|---|
| AC1 | `/callback?code=abc` → ends at `/app/sign-in`; seam not called |
| AC2 | `/callback?code=abc&state=` → same |
| AC3 | `/callback` → same |
| AC4 | the seam is spied: **not called** for AC1–AC3, **and called once** for `/callback?code=abc&state=xyz`, which proves the negative assertions are about a reachable collaborator. Non-vacuity. |
| AC5 | the navigation **replaces**: the router's history does not retain the callback entry |
| AC6 | the sign-in screen shows `signIn.discarded`; identical text for all three discard shapes; the notice is single-read, so a second mount does not show it |
| AC7 | no `sessionStorage` key added — key set before equals key set after, and a `setItem` spy sees nothing from T17 |
| AC8 | pre-seeded `state`/`nonce`/`code_verifier` are **still present** after a discard (OQ-2's lean, pinned) |
| AC9 | one `h1`, one `main`, one live region, on both the callback and the landed sign-in screen |
| AC10 | the inverted catalogue scan plus the copy-property guard (no cause named, no account state, no credential type) |
| AC11 | T01, T02, T13, T16 all green; T02's widening shown to count |
| AC12 | the cycle guard still holds with the second strand |
| AC14 | decoys: the seam spy proved to register a call; the discard matcher fed a callback that should *not* discard |

---

## Constraints

- No absolute address and no `import.meta.env` in new source, comments included (T01's scan).
- No new dependency. `react-router`'s `useSearchParams`/`useNavigate` are already present.
- No new `sessionStorage` key (L16); no deletion of existing ones (OQ-2).
- `pkce.ts` and `authorizeRequest.ts` stay closed.
- No styling.
- No `state` comparison — task 18.

## Null handling

`params.get()` returns `string | null`; both `null` and `''` are discards. `takeSignInNotice()` returns `null`
when there is nothing pending, which is the normal case for a visitor arriving at sign-in directly.

---

## Open questions

**OQ-1 — resolved here as the in-memory module variable.** Carried into phase 3 for challenge: the objection is
that a notice lost on reload is a notice the visitor can miss if the SPA re-bootstraps during the navigation. My
answer is that an in-SPA `navigate` does not re-bootstrap, so the only way to lose it is a deliberate reload, at
which point it should be gone.

**OQ-2 — still open.** The brief leans to *not* deleting and pinning that with AC8. Phase 3 or the owner should
settle it; either answer is a small, well-tested change.

**OQ-4 (new) — treating a missing `code` as a discard goes beyond R8's literal words.** R8 speaks only of
`state`. The alternative is to call the seam with no code and let task 20 reject it, which seems worse. Phase 3
should challenge it.

**OQ-3 — the copy is unreviewed**, as T16's is. Gates nothing.

---

## Next

**Phase 3 — design challenge. This is Kimi 2.7's phase, and I am stopping here for it.** The T16 waiver was
explicit and scoped to that task; this one has had no such instruction.
