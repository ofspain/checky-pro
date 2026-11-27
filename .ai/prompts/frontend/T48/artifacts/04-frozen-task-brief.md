<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). -->

# frontend · T48 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T48 — Verify success state (`verifyFromLinkShowsSuccessState`, cites **R3**) |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |
| **Status** | **FROZEN** — 2026-11-27, owner-approved. Phases 5–13 use this document and not Phase 2 or Phase 3. |

---

## Phase 3 disposition

Kimi's design challenge is **accepted in all three decisions**, and this is the first challenge in several
tasks whose structural citations were all accurate on checking: `handled` really is at
`CallbackScreen.tsx:48` and set at `:75` **before** the work begins, and `StatusRegion`'s header really does
mandate unconditional mounting, in those words, for the reason Kimi gives — *"a region that is mounted at the
same moment its text arrives is a new element with content already in it, and a screen reader announces
nothing."* Its union arithmetic (`toBe(22)` → `toBe(23)`) is also right: `PRIMITIVE_FILES` is every
`app/ui/*.tsx` and no render site lives there, so the two lists are disjoint and one new site moves the union
by exactly one.

| Claim | Verdict |
|---|---|
| **OQ-T48-1** — one shared `VerifyScreen.tsx`, task 49 extends it | **ACCEPTED.** Two files cannot own one route element, and the alternative makes task 49 a destructive edit to a closed task. |
| **OQ-T48-2** — POST on arrival | **DECISION ACCEPTED, RATIONALE REJECTED.** See below. |
| **OQ-T48-3** — no token ⇒ no POST, screen stays in its initial state | **ACCEPTED**, and strengthened: the state is *pinned by a case* so task 49 cannot close it silently. |
| `handled` ref set before the POST | **ACCEPTED** — cited correctly. |
| `StatusRegion` mounted unconditionally | **ACCEPTED** — it is the primitive's written contract, not a preference. |
| `RENDER_SITES` 9 → 10, union 22 → 23 | **ACCEPTED** — arithmetic verified, not taken on trust. |
| *"`features/account/` imports nothing from `auth/`"* | **TRUE OF PRODUCTION, IMPRECISE AS WRITTEN.** `registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:31` imports `accessToken` from `auth/`. No production file in `features/account/` does, which is the boundary that matters — but the flat claim would be falsified by a grep, so it is restated as a production-code boundary. |

### OQ-T48-2 — the decision is right and the reason given for it is false

Kimi wrote that a confirm control *"would still be consumed by anything that executes JavaScript, so the
confirm control does not materially reduce the attack surface."* **That does not follow.** A link previewer
or email-scanning gateway fetches and may render, but it does **not click**. A confirm control therefore
removes exactly the exposure in question. The reasoning is rejected so it is not reused.

**The consequence was then measured rather than argued.** `VerificationTokenService.consumeForPurpose` calls
`markConsumed(tokenHash, clock.instant())` at `:133`, and a second attempt finds `updated == 0` and returns
empty, which `AccountService:120` turns into `VerificationTokenRejectedException`. So the token is **durably
burned by the first POST**. If a gateway executes the SPA's JavaScript, the sequence is:

> token spent → **the account really is verified** → the human clicks → task 49's uniform failure.

The worst available combination, and silent. R4 forbids task 49 from softening it, because one uniform
failure message cannot say "you may already be verified".

**Owner's decision (2026-11-27): POST on arrival.** R3 is unambiguous — *"WHEN a visitor opens a
verification link and the token is valid, THEN the SPA SHALL show the verified success state"* — and a
confirm control means opening the link shows a button instead, which is a deviation from the requirement this
task exists to verify. Deviating would need a spec amendment, and `spec/**` is not this task's to change. The
hazard is therefore recorded as **R-T48-1**, a named risk, not an accepted silence; the mitigation is
localized to one screen if the owner later amends R3.

`bin/smoke:142-145` was checked for a prior constraint and imposes none: it parses the token out of the
link's query string and POSTs straight to the API, never loading the screen. It does independently confirm
the parameter name a third time.

---

## Scope

**In:** an element on `/app/verify` attached by constant; the `token` query parameter; one
`POST /accounts/verify-email` through `createPublicAuthApi()`; the success state for `204`; the inherited
pins that a new render site moves.

**Out:** everything a non-`204` does (**R4, task 49**); the resend form (**R5**); `/app/reset`; any change to
`spec/**`, `contracts/**`, the backend, or `api/client.ts`.

## Business rule

**R3** — a valid token shows the verified success state.

## Locked decisions

**L14** (the path is declared; `?token=<token>` is author-confirmed at `design.md:145`; attach by constant) ·
**L11** (WCAG AA, managed focus, catalogue-only copy) · **L6** (`createPublicAuthApi()`, no bearer, no
renewal) · **L4** (uniform copy **among failure reasons**, not between success and failure) · **L16** (the
token is stored nowhere) · **L9** · **L17** (`/app/verify` correctly absent from the bypass list) · **L3**.

## Design, frozen

```tsx
<Screen>
  <Heading ref={headingRef}>{t('verify.heading')}</Heading>
  <StatusRegion>{status === 'success' ? t('verify.success') : null}</StatusRegion>
</Screen>
```

```ts
type VerifyStatus = 'idle' | 'success';   // task 49 extends this union
verify: { heading: 'Verify email', success: 'Your email has been verified.' }
```

1. On mount, read `token` from `useSearchParams()`.
2. Empty or absent token ⇒ **return without posting**.
3. Set a `handled` ref **before** the request starts (`CallbackScreen.tsx:48,75`).
4. `createPublicAuthApi().POST('/accounts/verify-email', { body: { token } })`.
5. On `204`, set `status` to `'success'`.
6. No retry, no navigation, no storage write.

## Files to create

| | |
|---|---|
| `frontend/src/features/account/VerifyScreen.tsx` | Shared with task 49 by decision OQ-T48-1. |
| `frontend/src/features/account/verifyFromLinkShowsSuccessState.test.tsx` | `package.md:107`. |

## Files to modify

`app/routes.tsx` (add `VERIFY_PATH`, substitute it for the existing literal, attach) · `i18n/catalogue.ts`
(`verify` group) · `app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` (`RENDER_SITES` 9 → 10, its
length pin `:267`, the union `:305` **22 → 23**, the catalogue-key pin) ·
`app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` (`:711`, six screens → seven) ·
`app/routesResolveUnderAppPrefixOnly.test.tsx` (`SCREENS_ATTACHED_SO_FAR` `:18`, **route order**) ·
`auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` (`:292`, four → **three**).

## Files NOT to modify

`spec/**` · `contracts/**` · every backend service · `api/client.ts` · `auth/CallbackScreen.tsx` (template,
read only) · the T43/T44/T45/T47 registration suites.

**This last line is a prediction and T47 broke the same one.** Attaching an element there made thirteen
cases in a closed suite crash. If a pin in a closed suite moves, report it and have it ratified; do not edit
it quietly.

## Acceptance criteria

| AC | Criterion |
|---|---|
| **AC1** | `/app/verify` resolves to an element; a cold arrival with a token renders a non-empty document. |
| **AC2** | The token comes from the `token` query parameter. |
| **AC3** | One `POST /accounts/verify-email`, body `{ token }`, via the generated public client, **no `Authorization` header**. |
| **AC4** | On `204`, the verified success state shows. |
| **AC5** | Every user-facing string from the catalogue; no literal, nothing from the response. |
| **AC6** | Focus moves to the heading on arrival, and the success sentence is **announced** — which requires `StatusRegion` mounted before it is filled. |
| **AC7** | **Exactly one request per arrival**, including under StrictMode, with the `handled` ref set before the request. |
| **AC8** | The token reaches no storage and appears in no sessionStorage key. |
| **AC9** | `PUBLIC_ROUTE_PATHS` unchanged; attached by constant. |
| **AC10** | Primitives only; `RENDER_SITES` grows deliberately to 10 and the union to 23. |
| **AC11** | The four inherited suites stay green. |
| **AC12** *(new at the freeze)* | **With no token, and with an empty token, no request is sent** and the screen renders its `idle` structure. Pinned by a case, so task 49 must come here to change it. |

## Required tests

The nine from Phase 2, plus, from this freeze:

10. A source scan for what this screen must not reach: `sessionStorage`, `localStorage`, a bare `fetch(`,
    `.status`, and the response body. **Scoped to what task 48 owns** — it must not forbid `useNavigate`,
    because R4 gives task 49 "a neutral resend action" and a pin written here would box in a task that has
    not been designed yet.
11. The `handled` ref is set **before** the request, not after — the ordering, not merely its presence.
12. AC12's two cases: absent token, and `?token=` empty.
13. A control proving the focus assertion can fail, and a control proving the storage spy intercepts
    (`Storage.prototype`, never an instance — T42).

## Constraints

The token is a bearer-equivalent possession credential in a URL: not stored, not logged, not sent to
analytics, not rendered. The call carries no session, so `createPublicAuthApi` is required. The token is
one-time and durably burned, so a second POST for one arrival is a **defect, not a retry**, and nothing here
retries.

## Deviations

| ID | |
|---|---|
| **D1** | **A file created by task 48 and extended by task 49.** Chosen over task 49 replacing a closed task's file. |
| **D2** | **POST on arrival**, with Kimi's rationale rejected and the measured consequence recorded as **R-T48-1**. Owner-decided. |
| **D3** | **No working-state message.** While the request is in flight the screen shows the heading and an empty live region. Accepted: R3 covers the success state only, and `StatusRegion` must be mounted and empty anyway for the announcement to work — so the absence of a `verifying` key costs nothing structurally. |

## Risks

| ID | |
|---|---|
| **R-T48-1** | A JavaScript-executing email gateway burns the token before the human clicks; the account verifies, the human is shown task 49's uniform failure, and R4 forbids explaining it. Measured at `VerificationTokenService.java:133`. Mitigation is a confirm control, localized to this screen, and needs an R3 amendment. |

## Open questions

| ID | |
|---|---|
| **OQ-T48-4** | `auth.yaml` declares no failure response for `verifyEmail`. Blocks **task 49**, not this task. |
| **OQ-T48-5** *(new)* | **The heading still reads "Verify email" after the email is verified.** A screen whose heading is an instruction the visitor has already completed. Copy, not behaviour; raised because focus lands on that heading, so it is the first thing announced. Owner's call. |

**Phase 4 complete. Next is Phase 5 — Implementation Plan.**
