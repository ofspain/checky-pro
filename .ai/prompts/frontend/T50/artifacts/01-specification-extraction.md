<!-- MODEL: Claude Opus 5 — Phase 1 (Specification Extraction). No design, no code. -->

# frontend · T50 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

---

## Business rules

| ID | |
|---|---|
| **R5** | *"WHEN a visitor enters an email on the resend-verification form, THEN the SPA SHALL call the public `POST /accounts/resend-verification` and SHALL show the same acknowledgement regardless of outcome. The form requires no signed-in session, as `auth.yaml` declares."* (`requirements.md:18-20`) |
| **R5's own third sentence** | *"The divergence from backend R6, which requires a signed-in caller, is Q12."* — **stale, and Q12 says so**: the divergence was resolved by fixing backend R6's text. This client's publicness is what the contract and the server already are. |

**T50 is the first of R5's two halves.** R5 carries three claims: the call is made, the acknowledgement is
uniform, and **no session is required**. `tasks.md:84-85` splits them — T50 *"the resend form works without a
session"*, T51 *"the resend ack is identical whatever the outcome"* — and both verify the same named test.

**Where I will put the line, for Phase 2 to freeze:** T50 owns **the screen existing and being public** —
reachable with no session, posting to the public operation with no bearer, and showing *an* acknowledgement.
T51 owns **the acknowledgement being the same across outcomes**. T50 therefore needs the acknowledgement to
exist, and must not claim its uniformity; it will render one and leave the per-outcome proof to T51.

## Locked decisions that constrain this task

| ID | Constraint | Consequence here |
|---|---|---|
| **L14** (`design.md:53-57`, **§4c:138-139**) | The route paths are listed **verbatim**, resend verification among the public ones. *"A new public route … requires an ADR."* | **This settles where the acknowledgement renders.** The verbatim list has `/app/register` **and** `/app/register/sent`, but for resend it has **only `/app/resend-verification`**. Registration's two-path shape is in the spec; resend's one-path shape is too. **The acknowledgement renders in place**, and a `/sent` sibling would need an ADR. |
| **L4** (`design.md:21-26`) | Copy, redirects, styling **and timing** identical across unknown/missing, locked, suspended, deleted accounts and bad/expired/used tokens on **every public surface**. | The resend form is exactly the surface this exists for. `agents.md:44` narrows the timing clause usefully: *"The SPA cannot control server response timing. The timing requirement applies to the SPA's own rendering only."* |
| **L6** (`design.md:38-39`) | Generated client only; the sole exception is the notification stream. | `createPublicAuthApi()`; a bare `fetch` is forbidden. |
| **L11** (`design.md:47-48`) | WCAG AA, keyboard operation, managed focus, **live regions for async results**, **catalogue-only strings**. | The ack is an async result. Every string from `catalogue.ts`. **This is why the server's `message` cannot be rendered** (§Dependencies). |
| **L16** | `sessionStorage` holds only the five listed OIDC/session items. | A resend attempt stores nothing. |
| **L12 / phase additivity** (`agents.md:100-101`) | Later phases consume earlier ones; *"Nothing outside the public routes is reachable without a valid session."* | The converse is this task: a **public** route **is** reachable without one, and T40's gate must not cover it. |
| **D7** | Every rendered catalogue key is measured. | New keys must be added to the design-system key-set pin (T49 **D-T49-3** precedent). |
| **agents.md:40-43** | *"No client-side account-state gating. No lockout timers. Uniform copy only."* | No client-side branch on who the email belongs to. |

## Files involved

### Existing, to extend

| File | |
|---|---|
| `src/app/routes.tsx` | Attach the element to the **already-declared** `RESEND_VERIFICATION_PATH` (`:60`). No path added, renamed or reordered. |
| `src/i18n/catalogue.ts` | A new `resend` group — flat keys (T49 **D-T49-4**: the nested shape crashes an existing copy scan). |
| `src/app/routesResolveUnderAppPrefixOnly.test.tsx` | `'resend-verification'` into `SCREENS_ATTACHED_SO_FAR` **sixth, in route order after `'verify'`**. |
| `src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | Fires when a declared path gains an element (measured by T49's TR23). |
| `src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | The catalogue key-set pin and the render-site sweeps. |
| `src/features/account/verifyFailureShowsOneUniformMessage.test.tsx` | **T49's handoff case, written to fail here.** Replace it with an assertion that the form renders. |

### New

| File | |
|---|---|
| `src/features/account/ResendVerificationScreen.tsx` | The screen. |
| `src/features/account/resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx` | The named suite (`package.md:109`), **shared with T51**. |

## Dependencies

| | |
|---|---|
| **Contract** | `contracts/api/auth.yaml:61-78` — `POST /accounts/resend-verification`, **`security: []`**, `operationId: resendVerification`, body `ResendVerificationRequest` = `{email: string, format: email}` **required**, one response: **`200`**, described *"Always returned regardless of whether the email matches an account"*, schema `RegistrationAcknowledgement` (`required: [message]`). |
| **Client** | `createPublicAuthApi()` (`api/client.ts:61`). Its optional `RateLimitNoticeState` has **no production consumer**. |
| **Primitives** | `Screen`, `Heading`, `Form`, `EmailField`, `SubmitButton`, `StatusRegion`, `Text`, `FocusHolder` — all exist; `className`/`style` are **absent from their types**. |
| **Backend (read, not assumed)** | `AccountController.java:97-103` — `@PreAuthorize("permitAll()")`, **no `Authentication` parameter**, `RegistrationAcknowledgement.standard()`; `resendVerificationIfPending` *"never throws for a non-match."* |
| **Config** | None new. |

### The one dependency that is deliberately not consumed

`RegistrationAcknowledgement.message` is **required by the contract and must not be rendered**, per T47's **D2**
(`RegisterSentScreen.tsx:22-30`): a server-supplied sentence has no catalogue key, so no `t()` call, no D7
case, and no way to change the words without a server deploy — *"Do not 'fix' this by rendering the body."*
Under **L11** this is not a preference; a rendered server string violates catalogue-only strings.

## Acceptance criteria

| # | Criterion | From |
|---|---|---|
| **AC1** | `/app/resend-verification` renders a form with an email field and a submit control. | R5, L14 |
| **AC2** | It renders **with no session established** — no token in memory, nothing in `sessionStorage`. | **R5**, L12 |
| **AC3** | It is **not** behind `RequireSession`: the route stays a direct child of the root. | R5, L14, T40 |
| **AC4** | Submitting calls **`POST /accounts/resend-verification`** exactly once, through the generated public client. | R5, L6 |
| **AC5** | The request carries **no `Authorization` header**, even if a session happens to exist. | R5 |
| **AC6** | The request body is `{email}` and carries the typed email and nothing else. | R5, contract |
| **AC7** | An acknowledgement is shown after the call settles. | R5 |
| **AC8** | The acknowledgement renders **in place**, on the same path; no new route is declared. | **L14 §4c** |
| **AC9** | Every string comes from the catalogue; the response body is **never read**. | L11, T47 **D2** |
| **AC10** | The acknowledgement is announced accessibly (a live region or a managed focus move), and the form is keyboard-operable. | L11 |
| **AC11** | Nothing is written to `sessionStorage` and no token is read. | L16 |
| **AC12** | The screen composes design-system primitives only — no lowercase tags, no `className`/`style`. | T173, D7 |
| **AC13** | **T49's handoff case is replaced**, not deleted, and `verify.failureAction`'s wording still describes what the visitor finds. | T49 OQ-T49-6 |
| **AC14** | The three pins T49's TR23 named are updated — `SCREENS_ATTACHED_SO_FAR` **in route order**. | measured |
| **AC15** | The whole suite, `oxlint` and `tsc --noEmit` stay green. | — |

**Deliberately not an acceptance criterion:** that the acknowledgement is identical across outcomes. **That is
T51.** T50 must not consume it — writing those cases here would leave T51 with nothing to verify, exactly as
T49 declined to invent R15's copy for task 54.

## Tests required

| | |
|---|---|
| **Named** | `resendVerificationAcknowledgementIsUniformAndNeedsNoSession` (`package.md:109` → R5). **New file, shared with T51.** T50 builds the `…NeedsNoSession` half. |
| **Boundary / implied** | reachable with no session · no bearer sent when a session *does* exist · exactly one call · body shape · the response body unread (a source scan, per T49's precedent) · the ack announced · no storage write · in place rather than a redirect · keyboard operation · primitives-only · the replaced T49 handoff |

## Open questions

| ID | | Blocker? |
|---|---|---|
| **OQ-T50-1** | **Must a transport failure also show the acknowledgement?** R5 says *"regardless of outcome"* and a connection that never answered is an outcome; `RegisterScreen.tsx:96` currently answers the analogous question with `() => setStatus('idle')`, showing **nothing**, while T49 took the opposite view for verify. **Arguably T51's**, but T50 cannot build the state machine without a position. | **No** — Phase 3 decides; T50 can implement the settle path and leave the per-outcome proof to T51. |
| **OQ-T50-2** | **Is client-side email validation permitted?** `format: email` is in the contract. Rejecting locally before posting would make the SPA's own rendering differ by input — and under **L4** as narrowed by `agents.md:44` (*"applies to the SPA's own rendering only"*), a client-side rejection is precisely the SPA's own rendering. | **No**, but it is the sharpest L4 question in the task. |
| **OQ-T50-3** | **Is `EmailField` enough, or does the form need its own validation state?** T47's registration form is the precedent to read first. | No |
| **OQ-T50-4** | **Does an acknowledgement in place replace the form, or sit beside it?** Replacing it prevents a second send; keeping it invites one. R5 is silent. | No |
| **Q12** | Closed (`package.md:387-393`). Recorded because **R5's own text still cites it as open** — a stale cross-reference in `requirements.md`, not a blocker, and `spec/` is not ours to edit. | No |

**No genuine blockers. Phase 1 complete. Next is Phase 2 — Task Implementation Brief.**
