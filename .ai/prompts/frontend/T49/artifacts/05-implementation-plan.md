<!-- MODEL: Claude Opus 5 — Phase 5 (Implementation Plan). No production code written; a probe was applied, measured and reverted. -->

# frontend · T49 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md` (**FROZEN**) |
| **Produces** | `artifacts/05-implementation-plan.md` |

---

## The survey was RUN, not read

The frozen brief's own *Files NOT to Modify* list said measurement 2 was *"a reading of those assertions, not
a run"*, and that Phase 6 must confirm it. I brought that forward: the frozen design was applied as a
throwaway probe, the whole suite run, the failures recorded, and the probe reverted (`git checkout --
frontend/src`, tree clean).

**It found two things the freeze got wrong and one thing it predicted correctly.**

```
PROBE, Kimi's nested catalogue shape:  4 failed of 2048, in 2 files
PROBE, flat catalogue keys:            3 failed of 2048, in 2 files
baseline after revert:                 2048 / 2048
```

### Measured failure 1 — the design-system file is in scope after all · **D-T49-3**

```
FAIL  screensRenderThroughDesignSystemPrimitivesOnly.test.tsx
      > keeps the catalogue's key set exactly as it was (AC11)
      expected [ 'callback.heading', …(27) ] to deeply equal [ …(26) ]
```

The freeze listed this file as **NOT to modify**, on a measurement that was *correct about `RENDER_SITES`* —
it is a list of files, `VerifyScreen.tsx` is already on it, and the union is a file-set union of 23 — **and
blind to the catalogue key-set pin living in the same file.** Two new keys fire it immediately.

**This is the identical trap T48's Phase 5 hit, one task later**: *"the catalogue step was predicted green —
the key-set pin is set equality against the catalogue ITSELF, so it fires the moment a key exists, not when
one is rendered."* I reasoned about the render-site pins, found them genuinely out of scope, and stopped
there. **Knowing a file holds four pins is not knowing which one your change trips.**

`screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` **moves to Files to Modify. D-T49-3.**

### Measured failure 2 — the frozen catalogue shape breaks an instrument · **D-T49-4**

```
FAIL  screensRenderThroughDesignSystemPrimitivesOnly.test.tsx
      > holds no user-facing copy in any primitive (L11)
      TypeError: sentence.replace is not a function
        at spelledOut (…:645:28)   ← called from …:987:24
```

**That is a CRASH, not a failure**, and the distinction is T47's lesson: a crash says the *instrument* is
broken where a failure says the *screen* is. The scan flattens the catalogue **one level** and assumes every
value is a string. Kimi's `verify.failure: { message, action }` is the first **second-level** group in the
catalogue, so `spelledOut()` receives an object.

Measured alternative: flat `verify.failureMessage` / `verify.failureAction` → **the crash disappears and only
the key-set pin fires** (1 failure in that file instead of 2).

So the frozen catalogue shape changes to the flat keys. **This keeps Kimi's OQ-T49-3 intent exactly** —
grouped, reusable failure copy that task 54 can render without a new group — and gives up nothing but visual
nesting. The alternative was teaching a closed task's flattener *and* its scanner to recurse, which is a
larger edit to a file this task should barely touch, for a cosmetic gain. **D-T49-4.**

### Predicted correctly — **T48's handoff case did not do its job**

`verifyFromLinkShowsSuccessState.test.tsx:267`, `PINS A LIMIT`, says in its own comment: *"When task 49
lands, this case MUST FAIL — that is its purpose."*

**It did not fail.** Under D-T49-2 a tokenless arrival and a rejected token both render the uniform failure,
so its assertion — that the two are identical — **is true again, for a new reason**, and its second assertion
(the rejected arm really sent a request) still holds too.

The freeze's AC11 called this exactly: *"the case passes vacuously on two empty screens exactly as it did
before."* It is now passing on two **identical non-empty** screens, which is better, but it is still not
measuring the thing it was written to catch.

**The instruments that actually caught this task were two cases that carried no handoff label at all:**

```
FAIL  verifyFromLinkShowsSuccessState.test.tsx > AC12 …
      > sends nothing when the link carries no token at all
      > sends nothing when the token is present but empty
      expected 'Verify emailThis verification link is…' to be 'Verify email'
```

T48's AC12 pair pinned `textContent` **exactly equal to the heading** — i.e. that a tokenless arrival renders
nothing else. D-T49-2 reverses precisely that, so they fail, correctly and loudly. **A case labelled as a
handoff was weaker than two cases that were not.**

## Files to create

| Path | |
|---|---|
| `frontend/src/features/account/verifyFailureShowsOneUniformMessage.test.tsx` | The named suite (`package.md:108`). |

## Files to modify

| Path | Change | Authorised by |
|---|---|---|
| `frontend/src/app/routes.tsx` | `export const RESEND_VERIFICATION_PATH = 'resend-verification'`, substituted into `PUBLIC_ROUTE_PATHS`. No path added, renamed or attached. | freeze, **D-T49-1** |
| `frontend/src/i18n/catalogue.ts` | `verify.failureMessage`, `verify.failureAction` — **flat**. | freeze, **D-T49-4** |
| `frontend/src/features/account/VerifyScreen.tsx` | The three-state machine and the failure branch. | freeze |
| `frontend/src/features/account/verifyFromLinkShowsSuccessState.test.tsx` | Rewrite the two AC12 cases and `PINS A LIMIT`. | freeze AC11, AC15 |
| `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | Add the two keys to the key-set pin — **list entries only**, no logic. | **D-T49-3** |

## Files NOT to modify — now measured by running

`spec/**` · `contracts/**` · `frontend/src/app/ui/**` · `frontend/src/auth/**` ·
`homeRouteRendersLandingScreenAndManagesFocus.test.tsx` · `unauthenticatedRoutesRedirectToSignIn.test.tsx` ·
`registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` ·
`publicRouteSetIsExactlyTheDeclaredSet.test.tsx` · `routesResolveUnderAppPrefixOnly.test.tsx`.

**All nine stayed green under the probe.** The freeze's measurement 2 is now confirmed by a run rather than a
reading: the route-set pins compare **string values**, so a constant holding the same value leaves them
untouched. `typecheck` was also clean under the probe.

## Public surface

| | |
|---|---|
| `export const RESEND_VERIFICATION_PATH: 'resend-verification'` | New, in `routes.tsx`. The only new export. |
| `export function VerifyScreen(): ReactElement` | Unchanged signature; no props (L14 — a route element takes none). |

## Private surface, inside `VerifyScreen`

| | |
|---|---|
| `status: 'idle' \| 'success' \| 'failure'` | `useState`. `'idle'` is now the **in-flight state only**. |
| `handled: RefObject<boolean>` | Unchanged: set **after** the no-token return and **before** the request. |
| `headingRef` | Unchanged; focus is moved once, on arrival, and never again (OQ-T49-4). |
| effect 1 | Focus the heading, unconditionally. |
| effect 2 | Read the token; **no token → `'failure'`, no request**; otherwise guard, **`setStatus('idle')`**, POST, then `'success'` on `response.ok` and `'failure'` on either an HTTP non-success or a transport rejection. |

**No entities, repositories or services** — this is a browser screen with no persistence. The one dependency
is `createPublicAuthApi()`.

## Tests required

**New suite — `verifyFailureShowsOneUniformMessage.test.tsx`:** the freeze's fifteen mapped rows, plus the
three it added — the tokenless arrival sends nothing (AC15), the **tokenless → real-token transition** (AC6
step 5), and the rendered `href` being `/app/resend-verification` so the basename is applied by the router.

**Rewrites in `verifyFromLinkShowsSuccessState.test.tsx`:**

| Case | Rewrite |
|---|---|
| `sends nothing when the link carries no token at all` | Keep the request assertion, which is still the point, and change the DOM expectation to the uniform failure. |
| `sends nothing when the token is present but empty` | Same. |
| `PINS A LIMIT` | **Must gain an assertion that both arms contain the failure message**, or it goes on passing without measuring anything (see above). Rewritten, not deleted, per its own instruction and AC11. |

## Execution order

1. **`routes.tsx`** — the constant first, because the screen imports it.
2. **`catalogue.ts`** — the two flat keys.
3. **`VerifyScreen.tsx`** — the state machine, then the render branch.
4. **`screensRenderThroughDesignSystemPrimitivesOnly.test.tsx`** — the two key-set entries (D-T49-3).
5. **The three rewrites** in `verifyFromLinkShowsSuccessState.test.tsx`.
6. **The named suite.**
7. `pnpm lint`, `pnpm typecheck`, `pnpm test`, **then each touched file alone** (AC14 — the whole-suite run
   masks a file-level unhandled rejection).
8. **Commit** — the mutation harness reverts with `git checkout`, so uncommitted test edits are destroyed.
9. Build the harness (Phase 6/7) and run it **with the local Docker stack stopped** (FU-T42-1 is
   load-sensitive).

## Deviations raised by this phase

| ID | | Needs |
|---|---|---|
| **D-T49-3** | `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` moves to *Files to Modify*: its catalogue key-set pin fires on any new key, and the freeze's render-site measurement was blind to it. List entries only. | ratification |
| **D-T49-4** | The catalogue shape changes from Kimi's nested `verify.failure.{message,action}` to flat `verify.failureMessage` / `verify.failureAction`, because the nested form **crashes** the copy scan. Preserves OQ-T49-3's intent; costs only visual nesting. | ratification |

**Phase 5 complete. Next is Phase 6 — Implementation.**
