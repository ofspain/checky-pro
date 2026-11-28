<!-- MODEL: Claude Opus 5 — Phase 2 (Task Implementation Brief). -->

# frontend · T52 — Task Implementation Brief

## Task

**T52 — Reset request is uniform.** Build the password-reset **request** screen at `/app/reset/request` so that
submitting **any** email calls `POST /accounts/password-reset-request` and shows **one** acknowledgement.
Verifies `passwordResetRequestAcknowledgementIsUniform`. Cites **R14**.

## Purpose

A visitor who has lost their password needs a way to ask for a reset link. The screen must not reveal whether
the address belongs to an account, to anyone, at any point — which is why R14 specifies *the same* uniform
acknowledgement and L4 makes copy, redirects, styling and rendering timing identical across account states on
every public surface.

## Scope

**In**
- A new screen at `/app/reset/request`: one email field, one submit control, one acknowledgement.
- One call: `POST /accounts/password-reset-request`, public, body `{ email }`, reply body never read.
- A new catalogue section for every string on the screen.
- Promoting `'reset/request'` to a named `*_PATH` constant and attaching the element in `routes.tsx`.
- Adding the new call's row to T175's scan census.

**Out**
- `/app/reset?token=…`, the reset **confirmation** screen — **task 53** (R15).
- Any "forgot password" link from `SignInScreen` — **OQ-T52-3**, unresolved.
- Password policy, strength UI, or any password field (this screen takes an email only).
- Rate-limit copy, `Retry-After`, 429 handling — not in the contract (blocked on Q12).
- Reading or rendering the reply's `message`.

## Business Rules

| ID | |
|---|---|
| **R14** | Submitting any email to the reset-request form SHALL call `POST /accounts/password-reset-request` and SHALL show the same uniform acknowledgement. |

**R4 is not cited by R14** (R15 cites it; R14 does not) — see Open Questions.

## Locked Decisions

| ID | |
|---|---|
| **L4** | Copy, redirects, styling and **timing** identical across unknown/missing, locked, suspended, deleted accounts and bad/expired/used tokens, on every public surface. No carve-out applies. `agents.md` bounds timing to **the SPA's own rendering**. |
| **L6** | Generated client only; no hand-written `fetch`. |
| **L9** | Relative path, single origin, no base URL. |
| **L10** | No env key, no secret. |
| **L11** | WCAG AA, keyboard operation, managed focus, live region for the async result, **catalogue-only strings**. |
| **L13** | Confirmed stack; TanStack Query and Zustand are not installed, so local `useState`. |
| **L14** | The `/app` route set; `reset/request` is already in the public list. |
| **L16** | The sessionStorage carve-out is exhaustive. **The submitted email may not be stored.** |

## Dependencies

- `createPublicAuthApi()` — `src/api/client.ts`.
- `POST /accounts/password-reset-request` (`requestPasswordReset`), **`security: []`**, request
  `PasswordResetRequest` `required: [email]` / `format: email`, single `200` *"Always returned regardless of
  whether the email matches an account"*, reply `RegistrationAcknowledgement` `required: [message]`.
- Primitives: `Screen`, `Heading`, `Text`, `Form`, `EmailField`, `SubmitButton`, `StatusRegion`.
- `react-i18next` `useTranslation()`; `src/i18n/catalogue.ts`.
- No config keys.

## Inputs

One email string from the visitor. No route params; no query parameters; no session.

## Outputs

- One HTTP request: `POST /accounts/password-reset-request`, body `{ email }`, **no `Authorization` header**.
- A rendered acknowledgement, from the catalogue, identical for every outcome.

## State Changes

**None persisted.** Local component state only (the field value and a request status). **Nothing is written to
`sessionStorage`, `localStorage`, the URL, or any log** (L16, L4).

## Files to Create

| | |
|---|---|
| `frontend/src/features/account/PasswordResetRequestScreen.tsx` | the screen |
| `frontend/src/features/account/passwordResetRequestAcknowledgementIsUniform.test.tsx` | the named test |

## Files to Modify

| | |
|---|---|
| `frontend/src/app/routes.tsx` | promote `'reset/request'` to a named constant (**same string value**); attach the element with the house `// TASK 52` comment |
| `frontend/src/i18n/catalogue.ts` | one new section |
| `frontend/src/api/generatedClientCallsDoNotParseBodiesTheyNeverRead.test.ts` | add the new call's census row |

## Files NOT to Modify

- `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx` — it **hard-codes** the public spellings at
  `:242`, so it is an **independent guard** on `'reset/request'`. Promoting the literal to a constant of the
  same value leaves it green; changing the spelling must break it.
- `frontend/src/features/account/VerifyScreen.tsx`, `RegisterScreen.tsx`,
  `ResendVerificationScreen.tsx`, and their suites — siblings, not this task's.
- `frontend/src/app/ui/**` — the primitives are complete for this screen (T173).
- Anything under `spec/`, `contracts/`, or `libs/`.

## Acceptance Criteria

| AC | | Traces |
|---|---|---|
| **AC1** | Submitting calls the endpoint **exactly once**, with the email in the body | R14 |
| **AC2** | **No `Authorization` header**, and the screen works with no session | R14, `security: []` |
| **AC3** | The acknowledgement is **non-empty** and comes from the **catalogue** | R14, L11 |
| **AC4** | The acknowledgement is **byte-identical across every outcome the server can return** | **R14**, L4 |
| **AC5** | **Nothing the server sent is rendered** — the required `message` is never shown | L11 |
| **AC6** | The email never reaches the URL, `sessionStorage` or `localStorage` | L4, L16 |
| **AC7** | The call passes **`parseAs: 'stream'`**; T175's census is updated | T175 |
| **AC8** | **No delay on this screen varies by outcome** | L4 (as `agents.md` bounds it) |
| **AC9** | One `<h1>`; focus managed on the state change; in-flight announced in a live region | L11 |
| **AC10** | Design-system primitives only, no bare elements | L11, T173 |
| **AC11** | `reset/request` resolves under `/app` **and only** under `/app`; stays in the public set | L14 |
| **AC12** | Whole suite, `oxlint`, `tsc --noEmit` green | — |
| **AC13** | **Every new case fails before the screen exists** | house rule |

## Required Tests

- **`passwordResetRequestAcknowledgementIsUniform`** — the named test (`package.md` §8).
- **The uniformity set.** The contract declares one `200`, so the arms that can differ are what a proxy or
  edge returns: valid JSON, empty body, an **unparseable** body, other statuses. T51's multi-arm
  byte-comparison is the established technique, **with its baseline guarded** — non-empty, carries the
  expected text, and has no `<form` — because a uniformly broken screen also renders identically.
- **Provenance.** The acknowledgement must be shown to come from the catalogue, not from a literal that
  happens to match (T45).
- **The email.** Query string, hash, full URL, `sessionStorage`, `localStorage`.
- **The public claim.** No `Authorization` header; a case with no session at all.
- **Must not regress:** `routesResolveUnderAppPrefixOnly`, `screensRenderThroughDesignSystemPrimitivesOnly`,
  the design-system `h1` pins, the timer scan in
  `auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx`, and **T175's scan**.

## Constraints

| | |
|---|---|
| **Security** | **Enumeration safety is the point.** No branch, no copy difference, no redirect difference, no styling difference and no rendering-time difference may depend on the server's answer. The email is **not** stored anywhere (L16) and must not reach the URL. |
| **Performance** | No artificial delay of any kind; L4's timing clause forbids one that varies by outcome, and the simplest compliance is to have none. |
| **Module boundaries** | Screen lives in `features/account/`; the client is reached only through `src/api/client.ts` (L6); strings only through the catalogue (L11); no bare elements (T173). |
| **Null handling** | The reply body is **never read**, so no field of it can be null-dereferenced. `parseAs: 'stream'` makes that structural rather than a convention (T175). |
| **Thread-safety / transactions** | Not applicable — no persistence, no concurrency beyond one in-flight request. A second submit while one is in flight is a design question for Phase 3, not a correctness constraint here. |

## Open Questions

**Three, none of which block starting the screen; the first two must be settled before the acknowledgement's
shape and the failure arm are written.**

1. **OQ-T52-1 — route or in-place?** L14's prose says *"password-reset request **and its acknowledgement**"*
   (ten items); §4c's confirmed list has **nine** paths and **no** reset acknowledgement. **L14 defers to §4c
   by its own words**, and **Q6 is resolved "confirmed as proposed"**. Reading: **in place**, as resend does
   (D-T50-2). Phase 3 decides.
2. **OQ-T52-2 — what does a dropped connection show?** R14 names no failure behaviour; R15, one line below,
   cites R4's uniform failure. The siblings chose **opposite** things — `RegisterScreen` shows nothing
   (**OQ-T43-9** is open against exactly that), `ResendVerificationScreen` shows a neutral retry line
   (**D-T50-1**, a deliberate reversal). A transport failure is orthogonal to L4, so both are
   enumeration-safe. **Owner decision.**
3. **OQ-T52-3 — nothing links to the screen.** `SignInScreen` has no "forgot password" affordance and no task
   in `tasks.md` adds one, so the screen is reachable only by typing a URL. T49 set the opposite precedent.
   **Owner decision**; it bears on the demo path.

---

**Phase 2 complete. Next is Phase 3 — Design Challenge (Kimi 2.7). STOP HERE.**
