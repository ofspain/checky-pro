# frontend · T29 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T29 — Uniform sign-in failure |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

---

## Task

Prove that every SAS sign-in failure the SPA can observe produces one message, one redirect target, one visual
treatment and **one rendering timing** — identical across unknown or missing, locked, suspended and deleted
accounts. Verification-only: one new test file, `identityErrorsIndistinguishableAcrossAccountStates`.

## Purpose

R30 is the enumeration lock expressed as a UI property, and L4 states it `[ALL]` with no carve-out on a public
surface. The failure mode is not a bug but a sympathetic addition: *"That account has been suspended — contact
support"*, a lockout countdown, a three-second pause before the error so a locked account feels different. Each
would look like courtesy and each would hand an anonymous prober a test for whether an email exists and what
state it is in. The backend already refuses all five states with the identical
`BadCredentialsException("Bad credentials")` and records that a friendlier signal was considered and rejected
as an oracle; this task is the client-side half of the same decision.

## Scope

**In**

- The sign-in start and the callback — the only identity routes with an element today.
- Copy, redirect target, visual treatment and **the SPA's own rendering timing**, across the five account
  states and across the observable failure causes.
- Structural absence of account-state vocabulary and of any timer on the identity path.
- The `signIn.*` key-set pin, cited to T28 rather than rewritten.

**Out**

- The five account states driven through a real SAS login. A login failure never returns to the SPA — it
  re-renders the server's `/login` (L2). `oidcRoundTripEndToEnd` (task 134) has no suite and no owning task.
- Account-state rendering on registration, verification and password-reset screens — **task 58**. The Phase 1
  enumeration gate — **task 136**. Both extend this file later; see Open Questions.
- The 401/renewal and 429 surfaces. `createSessionBoundary` and `createRateLimitNotice` exist and are **never
  called** by `mountShell.tsx`, so there is no production path to verify.
- Styling. None exists; **task 173** owns it.
- Re-proving T17–T28. Each is cited where T29 needs it.
- Any production change. One found in Phase 6 is a finding for the Phase 9 gate.

## Business Rules

- **R10** — every SAS login failure shows the same uniform message, copy identical to a wrong password, and
  never says enrollment is required.
- **R30** — copy, redirect target and visual treatment are identical across the five account states and the
  three token states; SPA-observable only, server timing out of scope.
- **R8** (boundary) — a discarded callback returns to the sign-in start with neutral copy. T17/T18 own it.
- **R28** (must not be contradicted) — unauthenticated routes redirect to the sign-in start. Task 41 owns it.
- **R21** (must not be contradicted) — one shared renewal with `RENEWAL_TIMEOUT_MS`. A timer scan that forbade
  it would fail specified code.

## Locked Decisions

- **L4** — enumeration-safe UI, no carve-out on a public surface, **timing included**; no lockout timers. Its
  one carve-out (`adminGetAccount`, R27, D-033) is authenticated and ADMIN-only and must not be widened.
- **`agents.md:40-44`** — authoritative, and the reconciliation of L4's timing clause with R30's exclusion:
  *"The SPA cannot control server response timing. The timing requirement applies to the SPA's own rendering
  only."*
- **L2** — interactive login is SAS-hosted, so the refused visitor's sentence is the server's.
- **L11** — live regions for async results; catalogue-only strings.
- **L1, L3, L14, L16, L17** — PKCE and validated `state`/`nonce`; tokens in memory; the `/app` route set; the
  sessionStorage carve-out; `/login` never cached.

## Dependencies

`SignInScreen`, `CallbackScreen`, `setSignInNotice`/`takeSignInNotice`, `en`, `appRoutes`, `APP_BASENAME`,
`SIGN_IN_PATH`, `PUBLIC_ROUTE_PATHS`, `AUTHENTICATED_ROUTE_PATHS`, `AUTHORIZE_STORAGE_KEYS`, `accessToken`,
`createI18n()`; vitest, jsdom, `act`, `createRoot`, `createMemoryRouter`, `node:fs/promises`, `vi.stubGlobal`,
`vi.useFakeTimers`. All built. **No contracts** — a login failure reaches no API the SPA calls. No config keys.

## Inputs

Not an API. The real sign-in and callback screens driven under arrivals that differ only in how the sign-in
failed; the production source tree, comment-stripped; the i18n catalogue; synthetic violations as controls.

## Outputs

A test file whose failures name the file, the forbidden shape, and which clause of R30 it breaks — copy,
redirect, treatment or timing.

## State Changes

None in production. Within a test: the three PKCE items are seeded and read by real code; `location`, `fetch`,
`Storage.prototype`, the console and the clock are stubbed or faked and restored.

## Files to Create

- `src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx`

## Files to Modify

None anticipated.

## Files NOT to Modify

- Anything under `spec/`.
- `src/auth/CallbackScreen.tsx`, `SignInScreen.tsx`, `signInNotice.ts`, `authorizeRequest.ts`,
  `codeExchange.ts`, `idTokenNonce.ts`, `accessToken.ts`, `returnTarget.ts`, `pkce.ts`; `src/app/routes.tsx`,
  `mountShell.tsx`; `src/i18n/catalogue.ts`; `src/api/*`; `src/pwa/*`; `package.json`.
- The neighbouring named tests — read as patterns, cited where they own a claim, never moved or duplicated.
- Anything under `services/auth/`.

## Acceptance Criteria

- **AC1** — every observable SAS failure ends in **one** rendered sentence, from the catalogue, naming no
  cause. (R10, R30)
- **AC2** — the copy is identical across the five account states, satisfied by **absence**: no production
  module and no catalogue string carries account-state vocabulary on a sign-in-scoped surface. (R30, L4)
- **AC3** — every observable failure redirects to `/${SIGN_IN_PATH}` with `historyAction` `REPLACE`, taken
  from the owning module and never from a literal. (R30)
- **AC4** — visual treatment is identical, and **trivially**: no styling exists. Declared, not hidden. (R30)
- **AC5** — **the SPA's own rendering is not timed by cause.** No `setTimeout`, `setInterval`,
  `requestIdleCallback`, `requestAnimationFrame` or awaited delay stands between a failure and its sentence on
  the identity path, and no lockout timer or countdown exists. Proven structurally *and* behaviourally, under
  fake timers. (L4, `agents.md:44`)
- **AC6** — nothing gates what a failed sign-in renders on an account state or a role. Instrument inherited
  from T28, re-run rather than rewritten. (`agents.md:42-43`)
- **AC7** — `signIn.failed` and `signIn.discarded` remain the only two failure sentences and the `signIn.*`
  key set is exactly the six that exist. (R10, L4)
- **AC8** — the account-state vocabulary is forbidden on the **sign-in-scoped** surface only and permitted
  elsewhere, because tasks 81–89 are specified to write `adminSuspendAccount`, `adminUnlockAccount`,
  `assignRole` and `getEffectiveRoles` copy. The split is held from **both** directions. (L4, Q14)
- **AC9** — every instrument is shown to report DIRTY first, and a scan over an empty tree is refused rather
  than reported clean. (T22-D14)

## Required Tests

Named test: `identityErrorsIndistinguishableAcrossAccountStates`.

1. **AC1/AC3** — the observable failure causes, each driven to its sentence and destination, compared as one
   struct.
2. **AC2/AC8** — a comment-stripped source scan and a catalogue scan for the state vocabulary, with the
   permitted-elsewhere control and the permitted-hit accounting.
3. **AC5 structural** — a timer scan over the identity path, with a synthetic control.
4. **AC5 behavioural** — under `vi.useFakeTimers()`, the sentence is present **without advancing the clock**,
   for every cause.
5. **AC7** — the key-set pin, cited to T28.
6. **AC9** — the DIRTY control per instrument, and the empty-tree refusal through the one shared guard.

## Constraints

- **Security.** The file must not weaken an existing claim to make its own pass. Where a claim exists — T17/T18
  on `state`, T20 on the exchange, T22 on leaks, T23 on deletions, T26 on the inventory, T27 on MFA absence,
  T28 on the four failure kinds and the error surfaces, R57's task on the denylist — cite it.
- **Uniformity is under test, so no case may assert copy that distinguishes one cause from another.** A case
  pinning a state-specific sentence would make a future breach of R30 pass.
- **The timer scan must be SCOPED to the identity path, and this is measured, not cautious.** Comment-stripped,
  production source holds timers in exactly three modules, all under `api/`:
  `crossTabRenewal.ts` (`setTimeout`×2, `queueMicrotask`), `sessionBoundary.ts` (`setTimeout`×2,
  `queueMicrotask`), `rateLimitNotice.ts` (`queueMicrotask`). `RENEWAL_TIMEOUT_MS` is **specified** by R21 and
  L18. A whole-tree timer scan would fail on code the spec requires, while reporting an L4 breach.
- **The state-word pattern must not key on `unknown`, `missing`, `pending` or bare `disabled`, and this is
  measured.** Comment-stripped hits: `unknown` **14** (the TypeScript type, in `crossTabRenewal`,
  `sessionBoundary`, `codeExchange`, `idTokenNonce`), `missing` **2** (`idTokenNonce`'s `'claim-missing'`
  reason code), `pending` **9** (`sessionBoundary`'s in-flight renewal and `signInNotice`'s module variable),
  `disabled` **1** (`aria-disabled` in `SignInScreen`, an accessibility attribute L11 requires), `not found`
  **1** (`mountShell`'s developer-facing throw). Each needs either exclusion from the pattern or a permitted
  entry carrying its reason.
- **The words that ARE safe to forbid outright, measured at zero comment-stripped hits:** `locked`, `lockout`,
  `suspended`, `deleted`, `deactivat`, `inactive`, `no such`.
- **Comment stripping before any source scan.** `CallbackScreen.tsx:35` contains the sentence *"A missing
  `state`, a mismatched `state`…"* while explaining this very property, so an unstripped scan reads the
  package's own justification as the defect. Assert over *files*, not `path:line`.
- **Module boundaries.** Routes, keys and copy come from the modules that own them; a literal here is a defect.
- **Instrument ordering.** A `location`, `fetch`, `Storage.prototype`, console or clock stub must be installed
  before the code under test runs; the file must hold the control that demonstrates it.
- **Vacuity.** An empty tree, a pattern matching nothing, a comparison between two identical arrivals, and a
  struct of empty fields each satisfy the claim while proving nothing. Each must be refused.
- **Fake timers are themselves a vacuity risk.** `vi.useFakeTimers()` with nothing to advance passes whether
  or not the code is synchronous. The control is a *synthetic* delayed render that the same case catches.
- **Null handling.** `takeSignInNotice()` returns `null` on the ordinary path and is single-read; a second read
  in the same case sees `null` and is not evidence of anything.
- **Performance / concurrency / transactions.** Not applicable.
- **Verification.** `npx -y pnpm@12.14.0 exec vitest run` from `frontend/`; **the exit code is the verdict**.
  One known environmental failure: `src/api/lintRejectsHandWrittenBackendFetch.test.ts:118`. Suite baseline at
  T28 close: **1342 passed / 1 failed**.
- **Mutation discipline.** Tests committed before any run (the harness reverts with `git checkout`), harness
  committed with the artifacts. Mutants must include: a state-specific sentence in the catalogue; a lockout
  countdown; a cause-keyed `setTimeout` before the notice; an account-state gate on the render; a sixth-state
  word in a component; a divergent redirect target for one cause; and an escape mutant — a delay that fires
  only for a cause no test drives.

## Open Questions

**One, and it blocks the Phase 4 freeze rather than Phase 5.**

`identityErrorsIndistinguishableAcrossAccountStates` is cited by **three** tasks — **29** (this one, under
*"OIDC and session"*), **58** (*"Identical across account states"*, under *"enumeration safety"*) and **136**
(*"Enumeration gate"*). `package.md` §8 lists the test once. The split this brief assumes is: T29 owns the
sign-in and callback axis, the only identity screens with an element today; T58 extends the file to
registration, verification and password reset when those screens exist; T136 is the gate that re-runs the
property across all of Phase 1. That matches the precedent of `returnTargetOutsideAllowlistFallsBackToHome`,
created by T24 at 67 cases and grown by T25 to 184. **The owner should confirm it at the Phase 4 gate**, since
the alternative is three tasks writing one file with no stated split.

Everything else is a stated limitation rather than a blocker: the composition root does not exist (so the
renewal and rate-limit surfaces have no production path), and `oidcRoundTripEndToEnd` has no suite (so R10's
*"copy identical to a wrong password"* is verifiable on the client path only, as T28 declared in T28-D1).

---

**Phase 2 complete.** Ready for Phase 3 — Kimi's design challenge.
