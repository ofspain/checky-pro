<!-- MODEL: Claude Opus 5 — Phase 5 (Implementation Plan). No production code written yet. -->

# frontend · T48 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T48 — Verify success state |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

---

## The survey was run, not grepped — and it found something a grep could not

T47's Phase 5 learned that counting call sites by `grep` undercounts. So before planning, a throwaway
`ProbeVerify` screen — `useSearchParams`, a `POST` on mount, nothing else — was attached to `verify`, the
suite was run, and the probe was reverted. `git status` is clean and
`routesResolveUnderAppPrefixOnly` is back to 10/10.

### Result 1 · exactly three pins fail, and all three were already on the list

```
× publicRouteSetIsExactlyTheDeclaredSet  > attaches an element to sign-in, callback, register,
                                           register/sent, signed-out and home, and to nothing else
× routesResolveUnderAppPrefixOnly        > attaches a screen only to the routes whose task has run (L11, L12)
× screensRenderThroughDesignSystemPrimitivesOnly
                                         > names every file that imports the primitive barrel, tags or not (task 174)
Test Files  3 failed | 35 passed (38)      Tests  3 failed | 2013 passed (2016)
```

**No closed suite crashed**, which is the outcome T47 did not get. Nothing in the T43/T44/T45/T47
registration suites moved, so the freeze's *Files NOT to Modify* line should hold this time — a prediction
again, not a guarantee, and it will be re-checked in Phase 6.

### Result 2 · an unhandled rejection that fails a file, fails no test, and is invisible in the full run

Run **alone**, `routesResolveUnderAppPrefixOnly.test.tsx` reports:

```
× attaches a screen only to the routes whose task has run (L11, L12)
⎯⎯ Unhandled Rejection ⎯⎯
Serialized Error: { code: 'ERR_INVALID_URL', input: '/accounts/verify-email' }
Tests  1 failed | 9 passed (10)        Errors  1 error
```

Three things about this are worth stating exactly, because each one is a trap:

1. **The failing *test* is the list pin. The `ERR_INVALID_URL` is not a test failure at all** — it is an
   unhandled rejection, counted under `Errors`, which fails the *file* while every assertion in it passes.
   A reviewer reading "1 failed" and fixing the list pin would leave this behind.
2. **Its cause is the environment, not the screen.** `renderAt` (`:94-95`) drives the **real** `appRoutes`,
   and that suite stubs nothing — no `fetch`, no `Request`. A relative URL cannot be resolved without a
   base, so `new Request('/accounts/verify-email')` throws before any network call is attempted. The
   registration suites already solve this with a `Request` subclass that resolves against
   `window.location.href`.
3. **It does not appear in the full-suite run.** With the probe attached, `vitest run` reported the three
   pins and **no unhandled rejection** — another file's global stub leaks into the shared environment and
   masks it. So `pnpm test` would go green on this while a single-file run goes red. **The verification for
   this task must include running the touched files individually**; the whole-suite number cannot see it.

This is the same load-sensitivity class as FU-T42-1, and it is why the plan below stubs the transport in
that one case rather than trusting the aggregate.

---

## Order of work

Each step ends green, so a failure is attributable to the step that caused it.

**1 · The catalogue.** Add the `verify` group (`heading`, `success`) with a comment recording that the
heading is shown in all states and that the success sentence is announced because `StatusRegion` is mounted
empty first. *Expected: still 2016/2016 — a key nothing renders yet changes nothing.*

**2 · `VerifyScreen.tsx`.** The frozen structure, with a header comment covering: why the token comes from
`useSearchParams` **and that T47 forbade exactly that one screen away, for the opposite reason** (an
acknowledgement with inputs cannot be identical; a verification screen with no input cannot verify);
why `handled` is set before the request and not after; that the token is stored nowhere (L16); and
**R-T48-1** — the gateway hazard — in the file where someone would come to change it.

**3 · `routes.tsx`.** `export const VERIFY_PATH = 'verify';`, substituted for the existing literal in
`PUBLIC_ROUTE_PATHS` so **the strings are identical and every membership pin holds**, plus the attach
branch. *Expected after this step: the three pins from Result 1 fail. Nothing else.*

**4 · The three pins, and the fourth file's comment.**

| File | Edit |
|---|---|
| `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | `RENDER_SITES` += `'features/account/VerifyScreen.tsx'`; length `9` → `10`; union `22` → `23`; catalogue-key pin += `verify.heading`, `verify.success`. |
| `publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | The attached list and the case title: six screens → seven. |
| `routesResolveUnderAppPrefixOnly.test.tsx` | `SCREENS_ATTACHED_SO_FAR` += `'verify'` — **in route order**, fifth, after `register/sent`. The list's own comment warns a task-ordered entry fails while nothing is wrong. |
| `unauthenticatedRoutesRedirectToSignIn.test.tsx` | Comment only: `:287-292`, four of the eight → **three**. No assertion changes. |

**5 · The unhandled rejection from Result 2.** In `routesResolveUnderAppPrefixOnly.test.tsx`, give the
emailed-link case (`:134-140`) a stubbed transport and the `Request` base the registration suites use, with
a comment saying **why it is needed and why the full-suite run cannot prove it is needed**. *Verified by
running that file alone, not by the aggregate.*

**6 · The named suite**, `verifyFromLinkShowsSuccessState.test.tsx` — the thirteen required tests from the
freeze.

**7 · Verify.** `pnpm test`, `lint`, `typecheck`, **plus each touched file run individually.**

---

## Implementation sketch

```tsx
export function VerifyScreen(): ReactElement {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const headingRef = useRef<HTMLHeadingElement>(null);
  const handled = useRef(false);
  const [status, setStatus] = useState<'idle' | 'success'>('idle');

  useEffect(() => { headingRef.current?.focus(); }, []);

  useEffect(() => {
    if (handled.current) return;
    const token = params.get('token');
    if (token === null || token === '') return;   // AC12: no request, no invented copy
    handled.current = true;                       // BEFORE the request (CallbackScreen:48,75)
    void createPublicAuthApi()
      .POST('/accounts/verify-email', { body: { token } })
      .then(({ response }) => { if (response.ok) setStatus('success'); }, () => {});
  }, [params]);

  return (
    <Screen>
      <Heading ref={headingRef}>{t('verify.heading')}</Heading>
      <StatusRegion>{status === 'success' ? t('verify.success') : null}</StatusRegion>
    </Screen>
  );
}
```

**Four points where this sketch is a decision, not a formality:**

- **`handled.current = true` sits after the empty-token return.** If it were set first, an arrival with no
  token would latch the guard, and a later `params` change carrying a real token would be ignored. The guard
  must cover *a request having been made*, not *the effect having run*.
- **Two effects, not one.** Focus is unconditional and must not be entangled with the request's guard;
  combining them would make focus depend on whether a token was present.
- **`response.ok` rather than `response.status === 204`.** The contract declares only 204, and AC4 is about
  the success state. Phase 7 will put a mutant on this to see whether anything can tell the difference —
  if nothing can, that is a gap to record, not to paper over.
- **The rejection handler is empty and that is the whole of task 48.** A non-204 and a transport failure
  both leave `status` at `'idle'`. Task 49 owns what the visitor then sees. The empty handler exists so a
  failure does not become an unhandled rejection — exactly the class of defect Result 2 found.

## Risks carried into Phase 6

| | |
|---|---|
| **The empty rejection handler is a silent catch.** | It is required (an unhandled rejection fails a file, per Result 2) but it also swallows real errors. The named suite must prove `idle` is reached *by the handler running*, not by the request never being sent, or AC12 and the failure path become indistinguishable. |
| **`idle` is the same rendering for three different situations** — in flight, no token, and failed. T48 cannot distinguish them, by design, and AC12 pins only the no-token case. Phase 7 should check whether any mutant can tell these apart; if none can, that is a recorded limit for task 49 rather than a defect here. |
| **R-T48-1** | Unchanged and owner-accepted; the comment in `VerifyScreen.tsx` is the mitigation's entry point. |

**Phase 5 complete. Next is Phase 6 — Implementation.**
