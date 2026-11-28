<!-- MODEL: Claude Opus 5 — Phase 2 (Task Implementation Brief). No code. -->

# frontend · T50 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

## Task

**T50 — Resend is public.** *"The resend form works without a session."* Verifies
`resendVerificationAcknowledgementIsUniformAndNeedsNoSession`. Cites **R5**.

## Purpose

`/app/resend-verification` is a declared public route with **no element**: T49 added the path constant and
deliberately not the screen, so R4's resend link currently lands on a blank document. Build the form, attach
it, and prove it needs no session.

## Scope

**In** — the screen; the element on the existing path; its catalogue copy; the `POST` through the public
generated client; an acknowledgement after the call settles; the proof that no session is needed; the seven
measured pin updates, including **T49's handoff case**.

**Out** — *that the acknowledgement is identical across outcomes* (**T51**, same named suite); any new route
path; rendering the response body; client-side account-state gating; the dormant rate-limit notice;
password-reset (T52–T54).

## Business rules

| ID | |
|---|---|
| **R5** | A visitor's email on the resend form calls public `POST /accounts/resend-verification`, the acknowledgement is the same regardless of outcome, and **the form requires no signed-in session**. T50 owns the call and the no-session half. |

## Locked decisions

| ID | |
|---|---|
| **L14** | Route paths verbatim (`design.md:138-139`). The list has `/app/register` **and** `/app/register/sent`, but for resend **only `/app/resend-verification`** — so **the acknowledgement renders in place**; a `/sent` sibling needs an ADR. |
| **L4** | Enumeration-safe on every public surface — copy, redirects, styling, timing. `agents.md:44` narrows timing to *"the SPA's own rendering only."* |
| **L6** | Generated client only; no bare `fetch`. |
| **L11** | WCAG AA, keyboard operation, managed focus, live regions for async results, **catalogue-only strings**. |
| **L16** | `sessionStorage` holds only the five OIDC/session items. |
| **L2 / L5** | Every login step is the server's; no credential input and no second-factor logic ship in the SPA. **This task touches the scan that enforces it** (see *Files to Modify*). |
| **L12** | Phase additivity; *"Nothing outside the public routes is reachable without a valid session"* — and its converse is this task. |
| **D7** | Every rendered catalogue key is measured. |

## Dependencies

| | |
|---|---|
| **Contract** | `auth.yaml:61-78` — `POST /accounts/resend-verification`, **`security: []`**, body `{email}` (`format: email`, required), one response **`200`**, *"Always returned regardless of whether the email matches an account"*, schema `RegistrationAcknowledgement` (`required: [message]`). |
| **Client** | `createPublicAuthApi()` — `api/client.ts:61`. |
| **Primitives** | `Screen`, `Heading`, `Form`, `EmailField`, `SubmitButton`, `StatusRegion`, `Text` — all exist. |
| **Constant** | `RESEND_VERIFICATION_PATH` — `routes.tsx:60`, already in `PUBLIC_ROUTE_PATHS`. |
| **Deliberately unconsumed** | `RegistrationAcknowledgement.message`. **Required by the contract and not to be rendered** (T47 **D2**): a server sentence has no catalogue key, so no `t()`, no D7 case, and no owner control without a server deploy. |

## Inputs

The visitor's typed email. Nothing else — no route param, no query string, no stored value.

## Outputs

`POST /accounts/resend-verification` with body `{email}`, no `Authorization` header; and on screen, a catalogue
acknowledgement.

## State changes

**None persisted.** Component state only. **No `sessionStorage` write, no token read.**

## Files to create

| | |
|---|---|
| `src/features/account/ResendVerificationScreen.tsx` | The screen. |
| `src/features/account/resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx` | The named suite (`package.md:109`), **shared with T51**, which will extend it. |

## Files to modify

**Measured, not inherited.** T49's TR23 named three files; a throwaway probe of this task's real shape — a
screen, its keys and the attachment — produced **six failures across five files**, reverted immediately
(`2086/2086` restored). The two TR23 could not see are the last two rows, because that mutant added **no
catalogue keys and no component file**.

| File | Why, and what |
|---|---|
| `src/app/routes.tsx` | Attach the element to the existing constant; import the screen. **No path added, renamed or reordered.** |
| `src/i18n/catalogue.ts` | A new `resend` group. **Flat keys** (T49 **D-T49-4**: a nested group crashes an existing copy scan). |
| `src/app/routesResolveUnderAppPrefixOnly.test.tsx` | `'resend-verification'` into `SCREENS_ATTACHED_SO_FAR` — **sixth, in ROUTE order, directly after `'verify'`.** The list's comments warn a task-ordered entry *"would fail while nothing was wrong."* |
| `src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | `AC9 exactly seven screens are attached` → **eight**. The count is in the `describe` **name** and in the case name's list, so both change. |
| `src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | **Two pins, one file**: `RENDER_SITES` (*"a file consumes the design system and is not a pinned render site"*) **and** the catalogue key-set equality. T49's **D-T49-3** is the precedent; the freeze there wrongly listed this file as untouchable. |
| `src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` | **The surprise, and a security pin.** `PERMITTED_CREDENTIAL_HITS` must gain one row: `features/account/ResendVerificationScreen.tsx`, `'a form element'`, `count: 1`. The scan's pattern is **case-insensitive**, so rendering `<Form>` counts as a form — which is why `RegisterScreen.tsx` already has that exact row. A sibling case asserts *"the permitted credential table is exact: every row is still real, and none is spare"*, so the row must be right in **both** directions. **Table entry only — no logic, no selector, no pattern.** |
| `src/features/account/verifyFailureShowsOneUniformMessage.test.tsx` | **T49's handoff case, written to fail here** (*"task 50 has landed, so re-check this screen's link"*). **Replace** it with an assertion that the resend form renders, and confirm `verify.failureAction`'s wording still describes what the visitor finds. |

## Files NOT to modify

`src/app/ui/**` (no primitive needs changing — every one this task renders exists) · `src/api/client.ts` ·
`src/auth/**` **except** the one `PERMITTED_CREDENTIAL_HITS` table entry above · `src/features/account/RegisterScreen.tsx`
· `RegisterSentScreen.tsx` · `VerifyScreen.tsx` · any other task's named suite · `spec/**` · `contracts/**` ·
`services/**`.

> **Why this list is shorter than it looks:** in T49 the freeze named the design-system suite untouchable and
> a pin inside it fired anyway. The lists above are what a probe measured, so a file that is absent here is
> absent because it stayed green, not because it was assumed to.

## Acceptance criteria

**AC1** form with an email field and a submit control at `/app/resend-verification` · **AC2** renders with **no
session** — no token in memory, nothing in `sessionStorage` · **AC3** **not** behind `RequireSession` · **AC4**
submit calls `POST /accounts/resend-verification` **exactly once** through the generated public client ·
**AC5** **no `Authorization` header**, even if a session exists · **AC6** body is `{email}` and carries the
typed value and nothing else · **AC7** an acknowledgement shows after the call settles · **AC8** it renders
**in place**; no new route declared · **AC9** catalogue-only strings and **the response body never read** ·
**AC10** announced accessibly, keyboard-operable · **AC11** no `sessionStorage` write, no token read · **AC12**
design-system primitives only — no lowercase tags, no `className`/`style` · **AC13** T49's handoff case
**replaced**, not deleted · **AC14** all seven measured pins updated, `SCREENS_ATTACHED_SO_FAR` in route order
· **AC15** whole suite, `oxlint`, `tsc --noEmit` green.

**Explicitly not an AC:** that the acknowledgement is identical across outcomes. **That is T51**, on this same
suite. T50 renders one acknowledgement and leaves the per-outcome proof alone.

## Required tests

**Named:** `resendVerificationAcknowledgementIsUniformAndNeedsNoSession` — T50 builds the `…NeedsNoSession`
half.

**Implied:** reachable with no session · no bearer sent **when a session does exist** · exactly one call · body
shape · the response body unread (a source scan, per T49's precedent) · the acknowledgement announced · no
storage write (an instance spy does **not** intercept jsdom storage — use `Storage.prototype`, **with a
positive control**, per T42's vacuous case) · in place rather than a redirect · keyboard operation ·
primitives-only · the replaced T49 handoff.

## Constraints

| | |
|---|---|
| **Security** | **L2/L5:** no password field, no one-time-code input, no credential beyond the email. The email is an identifier, not a credential; the permitted-table row says so and is the only change to that scan. **L4:** no client-side branch on who the email belongs to. |
| **Privacy** | The email must not reach `sessionStorage`, a URL, or the console. |
| **Module boundaries** | Screens compose primitives; the barrel is the published surface (a deep import is a violation pinned elsewhere). |
| **Null handling** | An empty email field is an input state, not an outcome — Phase 3 decides whether submitting one posts. |
| **Performance / thread safety / transactions** | Not applicable (SPA, no persistence). |

## Open questions

**No blockers.** Four decisions for Phase 3, none of which stops implementation:

| ID | |
|---|---|
| **OQ-T50-1** | Must a **transport failure** also show the acknowledgement? R5 says *"regardless of outcome"*; `RegisterScreen.tsx:96` currently shows **nothing** there, while T49 took the opposite view for verify. Arguably T51's, but T50 cannot write the state machine without a position. |
| **OQ-T50-2** | Is **client-side email validation** permitted? Under L4 as narrowed by `agents.md:44`, a local rejection is precisely *"the SPA's own rendering"* differing by input. |
| **OQ-T50-3** | Does the acknowledgement **replace** the form or sit beside it? Replacing prevents a second send; keeping invites one. R5 is silent. |
| **OQ-T50-4** | Is the acknowledgement announced by a **live region** (`RegisterScreen`'s choice, and T49's) or a **managed focus move** (`RegisterSentScreen`'s, *"THE FOCUS MOVE IS THE ANNOUNCEMENT"*)? Both are established here; in place and on one path argues for the region. |

**Phase 2 complete. Next is Phase 3 — Design Challenge (Kimi 2.7). STOP HERE.**
