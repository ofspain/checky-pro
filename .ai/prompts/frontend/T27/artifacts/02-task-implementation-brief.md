# frontend · T27 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T27 — SAS MFA challenge resumes at callback |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

> This brief is the only specification the implementation and review phases use. It is frozen at Phase 4.

---

## Task

Build `src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx`, the named test for R9. One new
test file; no production change anticipated.

## Purpose

R9 says the second factor runs inside the SAS-hosted flow and the SPA resumes at the callback once it completes.
Both halves are about **restraint**, and that makes the task unusual: there is no feature to build, and the
failure mode is not a bug but a plausible-looking addition. A one-time-code field on the sign-in screen, an
"enter your authenticator code" step before the redirect, a branch in the callback for "MFA was used" — each
would look like progress and each would breach L2, and two of them would breach L4 and R10 as well by telling an
attacker which accounts have a second factor.

**Measured premise (Phase 0, read from `services/auth/`).** The backend's second factor is a **field, not a
step**: `TotpAuthenticationDetailsSource` captures an optional `mfaCode` submitted in the *same request* as the
password — its own comment calls this "task 20's single-request MFA design, O4" — and `SecurityChainsConfig`
registers `TotpAuthenticationProvider` as the only provider that filter's manager can reach, so password alone
cannot authenticate. `SasLoginIntegrationTest` posts `mfaCode` as one more form field.

So the SPA **cannot observe how many factors were verified**. It hands over control with a full-page navigation
and gets it back at `/app/callback`. That is not a gap in the test's reach; it is the architecture R9 describes,
and it tells us what the verifiable claim is: **the SPA contains no factor, and its resumption is symmetric.**
The fixture differs, the outcome does not.

**Measured starting position.** `grep -niE "mfa|totp|recovery|challenge"` over production source returns, in
full: `app/routes.tsx:39`'s `'account/mfa'` **route path with no element** (tasks 56–60), one comment in
`SignInScreen.tsx` saying the factor belongs to the server, and eight hits on PKCE's own `code_challenge`.

## Scope

**In**
- One new test file.
- The hand-over: one full-page navigation to the authorize URL, and nothing of the login rendered here.
- A **structural absence** claim over every production module: no credential input, no second-factor logic.
- **Symmetry**: the same callback under fixtures that differ only in what SAS did behind the redirect.
- The negative paths that must stay uniform: a refusal reaching the SPA as an ordinary discard.
- A sweep proving no MFA-shaped value can pass through the SPA.
- L11's focus obligation at the point of resumption.
- Controls that make every instrument fail.

**Out**
- Building, rendering or validating any second factor. L2 puts it on the server; adding one here is the defect
  this task guards against.
- The **SAS login page's own `mfaCode` field**, which does not exist (OQ1). Server-side, not the SPA's, and not
  workable around from here.
- R11's voluntary enrollment wizard and `auth.yaml`'s `.../totp` endpoints — `account/mfa` is a route path with
  no element, owned by tasks 56–60.
- Re-proving T16's authorize request, T19–T22's nonce/exchange/token handling, T23's deletions, T24/T25's return
  target, T26's inventory, or R57's denylist. Each is cited where T27 needs it.
- A real browser round trip. jsdom performs no navigation (T23-D5); `oidcRoundTripEndToEnd` has no suite and no
  owning task.
- Any production change. One found in Phase 6 is a finding for the Phase 9 gate.

## Business Rules

- **R9** — the TOTP or recovery-code challenge runs inside the SAS-hosted flow; the SPA resumes at the callback
  once it completes.
- **R10** (must not be contradicted) — every login failure, including a privileged account with no confirmed
  enrollment, shows the same uniform message; the SPA never says enrollment is required.
- **R57** (cited) — `/login` is never answered from cache and never given the app shell.
- **R6, R7** (boundaries) — the hand-over and the callback machinery T27 rides on.

## Locked Decisions

- **L2** — interactive login is SAS-hosted: password, TOTP and recovery-code steps run in the SAS flow. The
  authority for the prohibition.
- **L5** — SAS *refuses* an unenrolled privileged account with the same error as a wrong password and does not
  enroll it; so the SPA has no enrollment-during-login path at all.
- **L11** — "managed focus across redirect and MFA steps": the one positive obligation.
- **L1, L3, L4, L9, L14, L17** — PKCE and validated `state`/`nonce`; tokens in memory; enumeration-safe UI with
  no public carve-out; single origin including `/login`; `/app/callback` is public; the service worker never
  caches `/login`.
- **`agents.md`** — authoritative: SAS-hosted login, uniform failure copy, no credential in storage or logs.

## Dependencies

`buildAuthorizeRequest`, `consumeAuthorizeState`, `takeReturnTarget`, `rememberReturnTarget`,
`takeSignInNotice`/`setSignInNotice`; `AUTHORIZE_ENDPOINT_PATH`, `CLIENT_ID`, `REQUESTED_SCOPES`,
`AUTHORIZE_STORAGE_KEYS`, `RETURN_TARGET_KEY`, `DENYLISTED_NAVIGATION_PATHS`, `APP_BASENAME`, `appRoutes`;
`SignInScreen`, `CallbackScreen`, `createI18n()`; vitest, jsdom, `act`, `createRoot`, `createMemoryRouter`,
`node:fs/promises`, `vi.stubGlobal`. No config keys. No contracts.

## Inputs

Not an API. The inputs are: the real sign-in screen driven to its navigation, the real callback screen driven
under paired fixtures, the production source tree, and synthetic violations used as controls.

## Outputs

A test file whose failures name the file, the forbidden shape, and which half of R9 it breaks.

## State Changes

None in production. Within a test: the three PKCE items and the return target are written by the real code;
`location`, `fetch` and `Storage.prototype` are stubbed or spied and restored.

## Files to Create

- `src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx`

## Files to Modify

None anticipated.

## Files NOT to Modify

- Anything under `spec/`.
- `src/auth/SignInScreen.tsx`, `CallbackScreen.tsx`, `authorizeRequest.ts`, `codeExchange.ts`, `idTokenNonce.ts`,
  `accessToken.ts`, `signInNotice.ts`, `returnTarget.ts`; `src/app/routes.tsx`; `src/pwa/navigationDenylist.ts`.
- The neighbouring named tests — read as patterns, cited where they own a claim, never moved or duplicated.
- Anything under `services/auth/`. OQ1 is a backend/owner item.

## Acceptance Criteria

- **AC1** — the sign-in start hands over by one full-page navigation to the authorize URL; nothing of the login
  is rendered here; a second click fires no second navigation. (R9, L2)
- **AC2** — **no credential input of any kind anywhere in the SPA**: not a password, not a one-time code, not a
  recovery code, proven structurally over production source. (R9, L2, L5)
- **AC3** — no second-factor logic ships: no TOTP generation or verification, no recovery-code handling, no
  MFA-specific branch in the sign-in or callback path. (L2, L5)
- **AC4** — **the callback is step-count-agnostic**: fixtures that differ only in what SAS did behind the
  redirect produce the same destination, the same rendered text, the same stored state and the same request
  shape. (R9)
- **AC5** — resumption needs no session and nothing beyond the three PKCE items. (R9, L14)
- **AC6** — a refusal of an unenrolled privileged account arrives as an ordinary discard: uniform copy, the
  sign-in start, no mention of enrollment, MFA or a code. (R10, L4, L5)
- **AC7** — no MFA-shaped value passes through the SPA: not in a request body or URL, the router location, or
  either store, under fixtures that carry such values. (R9, L3)
- **AC8** — `/login` is denylisted, cited from the owning module rather than re-proved. (R57, L17)
- **AC9** — focus is managed at the point of resumption and the status is announced, not only rendered. (L11)
- **AC10** — the authorize request carries no MFA negotiation parameter; its parameter set is asserted **exactly**
  so an addition fails. (R9, L2)
- **AC11** — every instrument is proven to report DIRTY first: the scan fails on a synthetic credential input,
  and the symmetry comparison fails when the fixtures differ in a way the SPA *should* notice. (T22-D14)

## Required Tests

Named test: **`loginChallengeRunsInsideSasFlowAndResumesAtCallback`** → R9 (`package.md:113`).

1. **The hand-over** — one `location.assign` with the authorize URL; no credential field rendered; the second
   click suppressed by the `inFlight` ref.
2. **Structural absence** — a comment-stripped scan of every production module for credential-collecting shapes
   (`type="password"`, `autoComplete` one-time-code values, and the words for a one-time or recovery code), with
   exactly two permitted hits, each named with its reason: `account/mfa` (a route path, no element) and PKCE's
   `code_challenge`. A synthetic file carrying a one-time-code input must fail it.
3. **Symmetry** — the same callback under paired fixtures differing only in what the server did
   (`amr: ["pwd"]` vs `["pwd","otp"]`, differing `acr`/`auth_time`): identical destination, text, stored state and
   request shape. **Plus the control** that a difference the SPA *is* meant to notice — a wrong `nonce` — really
   does change the outcome, so the comparison is not blind.
4. **No MFA value escapes** — fixtures whose claims and callback query carry one-time-code-shaped values; sweep
   every request body and URL, the router location, and both stores.
5. **A privileged refusal is an ordinary discard** — `invalid_grant` at the exchange: uniform notice, sign-in
   start, and no copy anywhere mentioning enrollment, MFA, authenticator or a code.
6. **Resumption from a cold tab** — only the three PKCE items present; no session, no token in memory; the
   exchange completes and the visitor lands.
7. **Focus and announcement** — after resumption, focus is deliberate and the status sits in a live region.
8. **The authorize parameter set** — exactly the eight T15 owns, asserted as a set, with no MFA parameter.
9. **`/login` denylisted** — one assertion citing `navigationDenylist.ts`.
10. **Controls** — the scan over an empty tree refused; a synthetic credential input failing it; the symmetry
    helper shown to fail on a real difference; a `location.assign` spy installed late shown to miss the
    navigation.

## Constraints

- **Security.** The file must not weaken an existing claim to make its own pass. Where a claim exists (T16's no
  password field, T19–T22's exchange, T23's deletions, T26's inventory, R57's denylist), cite it.
- **No MFA-specific production code may be introduced**, including as a test fixture that only works against one.
  The subject is absence.
- **Uniformity.** No case may assert copy that distinguishes an MFA failure from a wrong password — asserting
  such copy would make a future breach of R10 pass.
- **Module boundaries.** Routes, keys, scopes and copy come from the modules that own them; a literal in this
  file is a defect, because it would keep passing after a rename.
- **Instrument ordering.** A `location`, `fetch` or `Storage.prototype` stub must be installed before the code
  under test runs; the file must hold the control that demonstrates it.
- **Comment stripping before any source scan**, and assertions over *files*, not `path:line` — stripping a block
  comment removes its newlines. A file explains the rule it obeys, and `SignInScreen.tsx` explains this very one.
- **Vacuity.** An empty source tree, a scan whose pattern matches nothing, and a symmetry comparison between two
  identical fixtures each satisfy the claim while proving nothing. Each must be refused explicitly.
- **jsdom limits, declared not hidden.** `location.assign` performs no navigation (T23-D5), so the hand-over is a
  spy plus structure. A genuine round trip is `oidcRoundTripEndToEnd`'s, which has no suite and no owning task.
- **Verification.** `npx -y pnpm@12.14.0 exec vitest run …` from `frontend/`; **the exit code is the verdict**.
  One known environmental failure: `lintRejectsHandWrittenBackendFetch.test.ts:118`.
- **Mutation discipline.** Tests committed before any mutation run (the harness reverts with `git checkout`), and
  the harness committed with the artifacts. Mutants should include the plausible additions this task forbids: a
  one-time-code field on the sign-in screen, an MFA branch in the callback, MFA-specific copy, and an
  `acr_values` parameter on the authorize request.
- **Performance / concurrency / transactions.** Not applicable.

## Open Questions

**No blockers.** Three items for the Phase 4 gate, each with the reading this brief proceeds under:

1. **The SAS login page has no `mfaCode` field** (OQ1). Measured: no custom `loginPage`, no templates directory,
   no login template in the repository, so SAS serves its default generated page with username and password
   only, while the backend accepts `mfaCode` when it arrives. A real browser cannot submit a second factor today.
   Out of T27's scope by L2; proceeding on the SPA's own claims. **Owner action:** assign the field to a backend
   task or record it as an accepted limitation against R9.
2. **Whether R9 implies any SPA-observable behaviour beyond absence and symmetry** (OQ2). On the measured
   architecture there is none, and the absence of a difference is the claim.
3. **`amr` and `acr` are not in `contracts/api/token-claims.md`** (OQ3), which is already known to be wrong about
   `email` and `name`. The paired fixtures therefore prove **indifference**, and no case may assert that either
   claim is present.

---

**Phase 2 complete.** Brief ready for the Phase 3 adversarial review.
