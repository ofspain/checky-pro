<!-- MODEL: Human Approval — Phase 4 (Freeze). The owner decided; the model only assembled the packet. -->

# frontend · T49 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN** · owner-approved 2026-11-28 · downstream phases may not renegotiate this.

| | |
|---|---|
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |

---

## Owner decisions at this gate

### 1 · A tokenless arrival DOES show the uniform failure — **this reverses Kimi's OQ-T49-2**

Kimi decided a tokenless or empty-token arrival *"stays in the `idle` state and renders only the heading…
That is a product gap, but it is outside R4's literal trigger and is left for future ownership to close."*

**The owner chose the opposite:** a missing or empty token renders the same uniform failure message and the
same resend action as a rejected token.

**Why the owner's reading wins on the merits, not just by authority:**

- A **truncated or wrapped URL is the most likely real-world broken link**, and it is precisely the case
  Kimi's reading leaves as a bare heading with no way forward.
- The visitor's **remedy is identical** in both cases — resend — so withholding the action helps nobody and
  distinguishes two situations that call for the same next step.
- It **closes the blank screen the demo rehearsal flagged**, rather than leaving a gap no task in the package
  owns.
- It is the reading **T48's own OQ-T48-3 recorded** (*"task 49 will render that state as the uniform failure
  state"*), so it keeps the handoff intact.

**Recorded as a deviation, because R4's literal trigger does not reach it:** R4 fires *"IF the verification
**request** fails"*, and with no token no request is made. **D-T49-2.**

### 2 · The resend path is promoted to a constant — **D-T49-1**

`'resend-verification'` is a bare literal at `routes.tsx:64`. The **only** existing `TextLink` in the
codebase, `SignedOutScreen.tsx:49`, is `to={`/${SIGN_IN_PATH}`}` — an imported constant. And `routes.tsx`'s
own comment on `VERIFY_PATH` states the house rule: a path gets a constant once *"the spelling is referred to
by name from more than one place"*, which is exactly this task.

So **`RESEND_VERIFICATION_PATH` is added to `routes.tsx` and imported by the screen**, and Kimi's bare literal
`to="/resend-verification"` is **rejected**.

**This moves `routes.tsx` from *Files NOT to Modify* to *Files to Modify*,** against the Phase 2 brief.
Ratified by the owner. **D-T49-1.**

## Phase 3 amendments folded in

| OQ | Kimi's decision | |
|---|---|---|
| **OQ-T49-1** | A neutral `TextLink` to the resend route; the action navigates, it does not re-POST | **ACCEPTED**, with the target changed to the constant (D-T49-1) |
| **OQ-T49-2** | Tokenless arrival renders only the heading | **REJECTED — reversed by the owner.** See above. |
| **OQ-T49-3** | Copy at `verify.failure.message` / `verify.failure.action`, nested so task 54 can reuse the message | **ACCEPTED** |
| **OQ-T49-4** | Focus stays on the heading; the failure is announced through the live region, focus is not moved a second time | **ACCEPTED** — moving focus on an async result would interrupt a screen-reader user mid-sentence, and nothing in the package asks for it |
| **OQ-T49-5** | "Neutral" means the message and action name or imply no reason; the source scan forbids reads of the response body | **ACCEPTED** |

**Two structural points of Kimi's design were checked rather than taken on trust, and both hold:**

- `StatusRegion` renders `{children}` inside `role="status" aria-live="polite"`, so the proposed
  two-conditional form leaves it **mounted and empty** in the in-flight state — which is the primitive's own
  stated requirement (mounted first, filled later, or nothing is announced).
- **The `TextLink` sits OUTSIDE the live region, and that is correct**, not an oversight: an interactive
  control inside a live region is re-announced every time the region changes.

## Frozen specification

### Screen structure

```tsx
<Screen>
  <Heading ref={headingRef}>{t('verify.heading')}</Heading>
  <StatusRegion>
    {status === 'success' ? t('verify.success') : null}
    {status === 'failure' ? t('verify.failure.message') : null}
  </StatusRegion>
  {status === 'failure' ? (
    <TextLink to={`/${RESEND_VERIFICATION_PATH}`}>{t('verify.failure.action')}</TextLink>
  ) : null}
</Screen>
```

### State machine

```ts
type VerifyStatus = 'idle' | 'success' | 'failure';
```

1. Focus the heading on arrival, unconditionally (unchanged from T48).
2. Read `token` from `useSearchParams`.
3. **No token or empty token → `status = 'failure'`, and NO request is sent** (D-T49-2; T48's AC12 still
   holds — the guard is still set after this return, so a token arriving later is still posted).
4. Set the `handled` guard, then POST `{ token }` through `createPublicAuthApi()`.
5. **When the request starts, set `status = 'idle'`.** See the note below — this is a frozen requirement, not
   an implementation detail.
6. `response.ok` → `'success'`. HTTP non-success **or transport rejection** → `'failure'`.
7. No retry, no navigation, no storage write, no second request.

> **Why step 5 is frozen.** T48's measured F1 case drives *tokenless arrival → navigate with a real token*.
> Under D-T49-2 the screen is in `'failure'` when that token arrives, so without step 5 the failure copy
> would stay on screen for the whole round trip of a verification that is about to succeed. Step 5 makes the
> in-flight state **always** neutral, which is what makes **AC6 unconditional** instead of carrying an
> exception.

### Catalogue

```ts
verify: {
  heading: 'Verify email',
  success: 'Your email has been verified.',
  failure: {
    message: 'This verification link is not valid.',
    action: 'Resend verification email',
  },
}
```

No `verifying` key (D3 stands). The message names no reason (OQ-T49-5).

### Files to create

`frontend/src/features/account/verifyFailureShowsOneUniformMessage.test.tsx` — the named suite.

### Files to modify

| | |
|---|---|
| `frontend/src/features/account/VerifyScreen.tsx` | The state machine and the failure branch. |
| `frontend/src/i18n/catalogue.ts` | `verify.failure.message`, `verify.failure.action`. |
| **`frontend/src/app/routes.tsx`** | **`RESEND_VERIFICATION_PATH` — D-T49-1.** No path is added, renamed or attached. |
| `frontend/src/features/account/verifyFromLinkShowsSuccessState.test.tsx` | Replace the `PINS A LIMIT` case — see AC11. |

### Files NOT to modify

`spec/**` · `contracts/**` · `frontend/src/app/ui/**` (no new primitive) · `frontend/src/auth/**` ·
`screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` ·
`homeRouteRendersLandingScreenAndManagesFocus.test.tsx` · `unauthenticatedRoutesRedirectToSignIn.test.tsx` ·
`registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` · `publicRouteSetIsExactlyTheDeclaredSet.test.tsx` ·
`routesResolveUnderAppPrefixOnly.test.tsx`.

**The two measurements behind that list, done up front — this is the FU-T48-1 fix:**

1. `RENDER_SITES` is a list of **files**; `features/account/VerifyScreen.tsx` is already on it, and the pinned
   union is a **file-set** union of 23. A new primitive **call inside an existing file** changes neither. The
   four design-system pin files therefore stay out of scope.
2. The suites that pin the route set compare **string values** (`routesResolveUnderAppPrefixOnly:236`,
   `verifyFromLinkShowsSuccessState:413`, `registrationAcceptsTwelveAndOneTwentyEightCodePoints:694`), so a
   constant holding the **same value** leaves them green.

> **The one thing that would widen this, stated so it is not a surprise:** measurement 2 is a reading of those
> assertions, not a run. **Phase 6 must confirm it by running them**, and if any of those three files turns
> out to pin the *spelling* rather than the value, that is a new deviation to raise, not a quiet edit.
> (Measurement 1 is already settled: it was verified against the pin's own source in Phase 2.)

### Acceptance criteria — final

| AC | |
|---|---|
| **AC1** | A rejected token (`400`) renders `verify.failure.message` from the catalogue. |
| **AC2** | `400`, `404`, `410`, `429`, `500`, with and without a problem body, render **identical DOM**. |
| **AC3** | A **transport rejection** renders the same uniform failure (the arm T48 left as `() => {}`). |
| **AC4** | The failure state carries the resend `TextLink` to `/app/resend-verification`. |
| **AC5** | The action's presence, label and target do not vary by failure reason. |
| **AC6** | **A valid token never renders the failure copy, not even for one frame — unconditionally**, including after a tokenless arrival (step 5). |
| **AC7** | The failure sentence is announced via `StatusRegion`, mounted before it has content; focus stays on the heading and is not moved a second time. |
| **AC8** | Both new keys render as literal English and are measured; no untranslated key reaches the DOM. |
| **AC9** | One call through `createPublicAuthApi()`; no `Authorization`; no bare `fetch`; the screen reads **no** part of the response body — enforced by a source scan forbidding `error`, `.title`, `.type`, `.detail`, `.status`. |
| **AC10** | **In the failure state** the token appears in no DOM, no storage and no console. |
| **AC11** | T48's `PINS A LIMIT` case is **rewritten, not deleted**. Its assertion — tokenless and rejected render identically — becomes **true again for a different reason**: both now show the uniform failure. The rewrite must therefore also assert that both arms **contain the failure message**, or the case passes vacuously on two empty screens exactly as it did before. |
| **AC12** | Primitives only; no lowercase tag, no `className`/`style`. |
| **AC13** | A `429` renders the same uniform failure as a `400`, so R4 does not depend on the rate-limit path staying dormant. |
| **AC14** | Whole suite green **and every touched file green when run alone** (T48's Phase 5 found an unhandled rejection that fails a file while every assertion passes and the whole-suite run masks). |
| **AC15** | A tokenless arrival renders the uniform failure and sends **no request** (D-T49-2 + T48's AC12). |

### Required tests

The fifteen rows of Kimi's mapping table, with row 5's target changed to the constant, plus:

- a tokenless arrival shows the failure **and sends nothing** (AC15);
- **the tokenless → real-token transition**: the failure copy is gone while the valid token is in flight, and
  success follows (AC6 step 5 — the case that would otherwise be left to inference);
- the rendered `href` is `/app/resend-verification`, i.e. the basename is applied by the router rather than
  by the caller.

### Constraints

Unchanged from the Phase 2 brief: the token is not stored, logged, sent to analytics or **rendered** — and
this task adds DOM in the state where a credential is most likely to be echoed, so AC10 needs its own
instrument. Nothing on screen may vary with the failure reason, and the structural guarantee is that the
screen **never reads the response body**. `features/account/` imports nothing from `auth/`.

### Open questions carried forward

| ID | | Owner |
|---|---|---|
| **OQ-T49-3a** | If the catalogue ever grows a shared auth group, `verify.failure.message` moves. Task 54 is the first consumer that would feel it. | package owner |
| **OQ-T48-4** | `auth.yaml` declares `204` and nothing else for `verifyEmail` — a contract-accuracy defect, **not** a blocker here. | contract owner |
| **R-T48-1** | The gateway hazard. **T49 inherits its consequence**: a visitor whose token was burned by a link previewer is shown *this task's* failure message, and R4 forbids explaining it. Not solvable with copy. | owner, decided |
| **FU-T48-1** | The process fix for closed-task count pins. Not triggered by this task — see the two measurements above. | package owner |
| **FU-T48-2** | The false comment in `tokensAreNeverWrittenToAnyStorage.test.tsx:155-157`. | T24/T174 |

**FROZEN. Next is Phase 5 — Implementation Plan.**
