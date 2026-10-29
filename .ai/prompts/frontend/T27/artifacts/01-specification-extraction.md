# frontend · T27 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T27 — SAS MFA challenge resumes at callback |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

---

## Business Rules

| ID | Rule |
|---|---|
| **R9** | When the SAS login requires a second factor, the TOTP or recovery-code challenge **runs inside the SAS-hosted flow** (backend T20, built; backend R25), and **the SPA resumes at the callback once it completes** (`requirements.md`, R9). |
| **R6** (boundary) | The sign-in start hands the visitor to the SAS flow — the navigation T27's first half depends on. Owned by T16/T17. |
| **R7** (boundary) | The callback validates `nonce`, exchanges the code with the verifier, holds the token in memory — the machinery T27's second half rides on. Owned by T19–T22. |
| **R10** (boundary, must not be contradicted) | Every SAS login failure, including a privileged account with no confirmed enrollment, shows the **same uniform message**; the SPA never says enrollment is required. Owned by T28/T29 — T27 must not add a code path that distinguishes an MFA failure. |
| **R57** (boundary) | `/login` is a denylisted navigation: never answered from cache, never given the app shell. Owned by T09/T10. The MFA step happens at `/login`, so this is what makes R9 reachable from an installed PWA. |

R9 is the only requirement in scope. It has **two halves**, and only the second is observable in the SPA:
*where the challenge runs* (nowhere in this bundle) and *where the SPA picks up again* (`/app/callback`).

## Locked Decisions

The phase header cites none inline and says to derive them from §4a. Derived:

| ID | Constraint on this task |
|---|---|
| **L2** | **Interactive login is SAS-hosted.** "Password, TOTP, and recovery-code steps run in the Spring Authorization Server flow. The SAS MFA step is built (auth T20, `TotpAuthenticationProvider`). Confirmed by the author." This is R9 as a locked decision, and it is the authority for a **prohibition**: no credential input of any kind in this bundle. |
| **L5** | **MFA-first for MERCHANT and ADMIN.** SAS *refuses* a privileged account without a confirmed enrollment, with the same error as a wrong password, and **does not enroll it**. So the SPA must have no enrollment-during-login path at all; the only enrollment surface is the authenticated voluntary wizard (R11, tasks 56–60). |
| **L1** | Authorization code with PKCE, `state` and `nonce` validated, exact-match redirect URIs. The resumption T27 asserts is this flow's own return leg. |
| **L3** | Tokens in memory only — true of a two-factor sign-in exactly as of a one-factor one. |
| **L4** | Enumeration-safe UI with no carve-out on a public surface. An MFA-specific message or code path on the sign-in or callback screen would breach it (and R10). |
| **L9** | **Single origin.** SAS endpoints, `/login` among them, sit on this origin through the edge path map. There is no second-origin hop for the MFA step to get lost in. |
| **L11** | **"Managed focus across redirect and MFA steps."** The one LOCKED decision that names MFA and imposes a *positive* SPA obligation: focus must be managed where the visitor lands after the redirect. |
| **L14** | `/app/callback` is a **public** route. The resumption must work with no session, which is why the callback cannot be gated. |
| **L17** | The service worker never answers `/login` from cache and never gives it the app shell (R57). |
| **`agents.md`** | Authoritative and not to be restated in code: SAS-hosted login, uniform failure copy, no PII or credential in storage or logs. |

**Measured premise, carried from Phase 0:** the backend's second factor is a **field, not a step** —
`TotpAuthenticationDetailsSource` captures an optional `mfaCode` in the *same request* as the password ("task
20's single-request MFA design, O4"), and `SasLoginIntegrationTest` posts it as one more form field. So R9's
"once it completes" is not a second SPA-observable transition; it is inside one HTTP exchange the SPA never sees.

## Files involved

**Read / extend (all existing):**

| File | Why |
|---|---|
| `src/auth/SignInScreen.tsx` | the hand-over: `buildAuthorizeRequest()` then `globalThis.location.assign(url)`, guarded by an `inFlight` ref |
| `src/auth/CallbackScreen.tsx` | where the SPA resumes; the one screen R9's second half is about |
| `src/auth/authorizeRequest.ts` | the authorize URL and the three stored items; `prompt`/`acr_values`-free by design |
| `src/auth/codeExchange.ts`, `idTokenNonce.ts`, `accessToken.ts` | the exchange that must behave identically however many factors SAS verified |
| `src/pwa/navigationDenylist.ts` | `/login` is denylisted — the structural reason an installed PWA can reach the MFA step |
| `src/app/routes.tsx` | `account/mfa` is a **route path with no element** (tasks 56–60); `callback` and `sign-in` are public |
| `src/auth/signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx` | T16's "no password field" — the nearest claim, which T27 widens to any credential input, including a second factor |
| `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx` | T26's three-instrument pattern, including the source-scan and namespace-scan idioms a structural absence claim needs |

**New:** `src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` — the named test
(`package.md:113`). No file of that name exists.

**No production change is expected.** One found in Phase 6 is a finding for the Phase 9 gate, not a licence.

## Dependencies

- **Exported functions:** `buildAuthorizeRequest`, `consumeAuthorizeState`, `takeReturnTarget`,
  `rememberReturnTarget`, `takeSignInNotice`, `setSignInNotice`.
- **Exported constants:** `AUTHORIZE_ENDPOINT_PATH`, `CLIENT_ID`, `REQUESTED_SCOPES`, `AUTHORIZE_STORAGE_KEYS`,
  `RETURN_TARGET_KEY`, `NAVIGATION_DENYLIST` / `DENYLISTED_NAVIGATION_PATHS`, `APP_BASENAME`, `appRoutes`.
- **Screens:** `SignInScreen`, `CallbackScreen`; i18n through `createI18n()` and the catalogue.
- **Test infrastructure:** vitest, jsdom, `act`, `createRoot`, `createMemoryRouter`/`RouterProvider`,
  `node:fs/promises` for a source scan, `vi.stubGlobal` for `location` and `fetch`.
- **Config keys:** none. **Contracts:** none — the token exchange is a SAS endpoint, not a `contracts/api/*` path,
  and `auth.yaml`'s MFA endpoints (`.../totp`, `.../confirm`, `.../recovery-codes`) belong to R11's wizard, not here.

## Acceptance Criteria

| # | Criterion | Maps to |
|---|---|---|
| AC1 | The sign-in start hands the visitor to the SAS flow by a **full-page navigation** to the authorize URL, and the SPA renders nothing further of the login itself. | R9, L2 |
| AC2 | **No credential input of any kind exists anywhere in the SPA** — not a password, not a one-time code, not a recovery code — proven structurally over production source, not just on the sign-in screen. | R9, L2, L5 |
| AC3 | The SPA ships **no second-factor logic**: no TOTP generation or verification, no recovery-code handling, no MFA-specific branch in the sign-in or callback path. | L2, L5 |
| AC4 | **The callback is step-count-agnostic.** A callback that followed a two-factor login and one that followed a one-factor login are indistinguishable to the SPA: same exchange, same destination, same copy, same stored state afterwards. The *fixture* differs; the *outcome* does not. | R9 |
| AC5 | Resumption works with **no session and no SPA state beyond the three PKCE items** — `/app/callback` is public (L14), and the only thing carried across the redirect is what L16 permits. | R9, L14 |
| AC6 | A SAS refusal of a privileged account with no confirmed enrollment reaches the SPA as **an ordinary discard**: uniform copy, the sign-in start, no mention of enrollment, and no distinguishable code path. | R10, L4, L5 |
| AC7 | **No MFA value can pass through the SPA**: nothing resembling a one-time code or recovery code appears in any request the SPA makes, any URL it builds, any store, or any log — asserted over a flow whose fixtures carry such values. | R9, L3, `agents.md` |
| AC8 | `/login` is a denylisted navigation, so the MFA step is never answered from cache nor given the app shell — cited from the module that owns it, not re-proved. | R57, L17 |
| AC9 | **Focus is managed where the visitor lands after the redirect** (L11's "managed focus across redirect and MFA steps"), and the callback screen's status is announced rather than only rendered. | L11 |
| AC10 | The authorize request carries **no MFA-related parameter** — no `acr_values`, no `amr`, no `prompt` asking for a factor: the SPA does not negotiate the second factor, it merely does not interfere with it. | R9, L2 |
| AC11 | Every instrument is proven to report DIRTY before it is believed: the source scan fails on a synthetic credential input, the indistinguishability comparison fails when the two fixtures really differ in an observable way. | T22-D14 pattern |

## Tests required

**Named test:** `loginChallengeRunsInsideSasFlowAndResumesAtCallback` → R9 (`package.md:113`). One file.

**Boundary and implied cases:**

1. **The hand-over** — clicking sign-in calls `location.assign` exactly once with the authorize URL; the screen
   renders no credential field before or after; a second click does not fire a second navigation.
2. **Structural absence** — a comment-stripped scan over every production module for input types and names that
   would collect a credential (`type="password"`, `autoComplete="one-time-code"`, `otp`, `mfaCode`, `totp`,
   `recovery`), with the route path `account/mfa` and PKCE's `code_challenge` as the two **permitted** hits, each
   with a reason. Controls: a synthetic file with a one-time-code input fails the scan.
3. **Step-count agnosticism** — the same callback driven twice with fixtures that differ only in what SAS did
   behind the redirect (an ID token carrying `amr: ["pwd"]` versus `amr: ["pwd","otp"]`, and an `acr`/`auth_time`
   difference), asserting identical destination, identical rendered text, identical stored state, identical
   request shape. Plus the control: a fixture difference the SPA *is* supposed to notice (a wrong `nonce`) really
   does change the outcome, so the comparison is not blind.
4. **No MFA value escapes** — a fixture whose ID token claims and whose callback query carry one-time-code-shaped
   values; sweep every request body and URL the SPA built, the router location, and both stores.
5. **Privileged refusal is an ordinary discard** — SAS answers the exchange with `invalid_grant`; assert the
   uniform notice, the sign-in start, and that no copy mentions enrollment, MFA or a code.
6. **Resumption from a cold tab** — the three PKCE items present and nothing else; no session, no token in
   memory; the exchange still completes.
7. **Focus and announcement** — after resumption, focus is somewhere deliberate and the status is in a live
   region (L11).
8. **The authorize URL's parameter set** — exactly the eight T15 owns, no MFA negotiation parameter, asserted as
   an exact set so an addition fails.
9. **`/login` denylisted** — cited from `navigationDenylist.ts`, one assertion, not a re-proof of R57.

## Open Questions

- **OQ1 — The SAS-hosted login page has no field for the second factor.** Measured in Phase 0: no custom
  `loginPage`, no `templates` directory, no login template anywhere in the repository, so SAS serves its default
  generated page with `username` and `password` only; the backend reads `mfaCode` when it arrives and its own
  integration test supplies it by posting the field. **A real browser cannot submit a second factor today.** L2
  puts that UI on the server, so it is neither T27's to build nor the SPA's to work around. **Not a blocker** for
  T27, whose claims are all about the SPA's own absence of a factor and the symmetry of its resumption. **Owner
  action:** decide where the SAS login page's `mfaCode` field is owned — a backend task, or an accepted
  limitation recorded against R9.
- **OQ2 — Does R9 imply any SPA-observable behaviour beyond absence and symmetry?** On the measured
  architecture the SPA cannot see how many factors were verified, so the absence of a difference *is* the claim.
  **Not a blocker**: Phase 2 proceeds on that reading, and the gate can say otherwise.
- **OQ3 — `amr` / `acr` are not in a contract.** The step-count fixtures use `amr` and `acr`, which
  `contracts/api/token-claims.md` does not document (it is already known to be wrong about `email` and `name`).
  The fixtures therefore prove *indifference*, not that SAS emits those claims. **Not a blocker** — indifference
  is the claim — but it is why no case may assert that a claim is present.
- **No `package.md` §11 question blocks this task.** Q1–Q12 checked; Q5 (refresh-token issuance) touches
  renewal, not the login leg.

---

**Phase 1 complete.** No design, no implementation.
