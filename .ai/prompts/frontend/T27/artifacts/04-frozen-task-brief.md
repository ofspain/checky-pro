# frontend · T27 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T27 — SAS MFA challenge resumes at callback |
| **Model** | Human Approval (packet assembled by Claude Opus 5) |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |

**STATUS: FROZEN.** Downstream phases may not renegotiate this brief. A change found necessary in Phase 6
is a finding for the Phase 9 gate, not an edit here.

---

## Decision packet — Phase 3 disposition

Nine findings. **Seven accepted** (two of them restated on measurement, because the recommendation as written
would have been vacuous), **one rejected with evidence**, **one accepted after relocating its target**.

| ID | Severity | Disposition | One-line reason |
|---|---|---|---|
| F1 | Medium | **ACCEPTED, strengthened** | The hole is an *added* navigation by another mechanism, not a renamed one |
| F2 | Medium | **ACCEPTED, strengthened** | An import can only resolve if the package is installed — guard the manifest too |
| F3 | Medium | **ACCEPTED** | Measured: exactly one MFA-shaped identifier survives comment stripping |
| F4 | Medium | **ACCEPTED** | An explicit server-sent MFA marker is the likeliest future addition |
| F5 | Medium | **ACCEPTED** | The response body and decoded claims are where `amr`/`acr` actually live |
| F6 | Low | **REJECTED** | T16:223 already owns the derived comparison, and it is vacuous against an addition |
| F7 | Low | **ACCEPTED, restated** | Measured: no focus management exists to name a target in — AC9 becomes a pin, not an obligation |
| F8 | Low | **ACCEPTED, relocated** | `SignInNotice` is a one-member union, so comparing it to itself proves nothing; the risk is in the catalogue |
| F9 | Low | **ACCEPTED** | The suppression mechanism is what matters, not the absence of one call |

### F1 — the hand-over instrument, accepted and strengthened

Verified: `SignInScreen.tsx:38` is `globalThis.location.assign(url)`. Kimi's stated risk — a future change to
`location.href = url` making the case fail or pass vacuously — understates it. A *replacement* mechanism makes
the case **fail**, which is a correct outcome with a misleading message. The real hole is an **addition**: a
second navigation fired through `location.href` or `window.open` *alongside* the existing `assign` leaves the
`assign` spy reporting exactly one call, and the case passes while the SPA has grown a second hand-over.

**Folded as:** the hand-over case watches `assign`, `replace`, the `href` setter and `window.open` as **one
counter**, and asserts the count is exactly one and its target is the authorize URL. AC1 is reworded from the
method to the event.

### F2 — forbidden imports, accepted and strengthened

Verified against `frontend/package.json`: no `otpauth`, `speakeasy`, `totp-generator`, `qrcode`,
`@simplewebauthn/*` or any sibling in `dependencies` or `devDependencies`. The full dependency list is
thirteen devDependencies and six dependencies, all accounted for.

**Folded as:** a forbidden-import scan over production source, **plus** an assertion over the dependency
manifest itself. The manifest is the stronger instrument of the two, because a bare-specifier import cannot
resolve unless the package is installed — so the manifest check catches the arrival one step before any
module can use it, and catches it even in a file the source walker does not reach.

### F3 — identifier scan, accepted

Measured, comment-stripped, over all of `src/` excluding `*.test.*`:
`grep -niE "mfa|totp|recovery|authenticator|one-time|otp|2fa|webauthn|passkey"` returns **exactly two lines**,
and one of them is a comment that stripping removes:

- `src/app/routes.tsx:39` — `'account/mfa'`, a route path with **no element** (tasks 56–60). *Permitted.*
- `src/auth/SignInScreen.tsx:12` — the comment saying TOTP belongs to the server. *Removed by stripping.*

`challenge` is a separate pattern and belongs to PKCE: `authorizeRequest.ts` (4), `pkce.ts` (9),
`CallbackScreen.tsx` (1). *Permitted, as `code_challenge` / `code_challenge_method`.*

**Folded as:** Required Test 2 gains an identifier scan with exactly those two permitted hits, each carrying
its reason in a table, and a synthetic `function verifyTotp()` as the control that proves it fires.

### F4 — an explicit MFA marker in the fixtures, accepted

**Folded as:** a third fixture carrying `amr: ["pwd","otp"]`, a differing `acr`, and a custom `mfa_method:
"totp"` claim. Asserted indistinguishable in outcome from the single-factor fixture — subject to OQ3, so no
case may assert that any of those claims is *present*, only that its presence changes nothing.

### F5 — sweep the response body and decoded claims, accepted

Kimi is right that AC7 named the request side and omitted the response, which is the only side `amr`/`acr`
can arrive on. One carve-out to record, learned in T26 phase 6: a decoded-claim sweep there collided with
L16's own carve-out, because `nonce` **is** a permitted stored value. **No such collision exists here** — no
MFA-shaped value is permitted in any store, so the sweep needs no exclusion and must not acquire one.

**Folded as:** AC7 extended to "the exchange response body and the decoded ID-token claims".

### F6 — derive the parameter set: REJECTED, on evidence

Kimi recommends deriving the expected set by calling `buildAuthorizeRequest` and reading its search params.
**`signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx:223` already does exactly
that:**

```ts
expect([...fromScreen.keys()].sort()).toEqual([...direct.keys()].sort());
```

That assertion owns a real and different claim — *the screen sends what the builder built* — and it is
**structurally incapable of catching an addition**, because both sides come from the same builder. An
`acr_values` parameter added to `authorizeRequest.ts:85` appears on both sides and the comparison stays
green. Adopting F6 would duplicate T16's line and delete the only thing T27's AC10 contributes.

The premise is also wrong on its own terms. The eight names are **wire-protocol spellings fixed by RFC 6749
§4.1.1 and OIDC Core 3.1.2.1** — `response_type`, `client_id`, `redirect_uri`, `scope`, `state`, `nonce`,
`code_challenge`, `code_challenge_method`. T15 cannot rename one; renaming it would break the contract with
SAS, not the test. The brief's "a literal is a defect" constraint is about **values** that a module owns and
may re-spell, and those still come from their owners (`CLIENT_ID`, `REQUESTED_SCOPES`, `redirectUri()`).

**AC10 keeps its literal set,** with that reasoning recorded in the file so the next reader does not
"improve" it into T16's line.

### F7 — name the focus target: ACCEPTED, restated

Kimi asks the brief to name which element receives focus. **Measured: there is none to name.** Neither
`SignInScreen` nor `CallbackScreen` moves focus anywhere. `SignInScreen.tsx:67-69` says so deliberately —
the pressed button keeps focus because it is the control the visitor needs to retry — and T16:387-392 already
pins that. `CallbackScreen` renders `<main><h1>` and a `role="status" aria-live="polite"` region and moves
nothing. The resumption lands on the return target, and `/app/home` **has no element at all** (T24-D11).

So AC9 as drafted asked for an obligation no production module meets, and meeting it would mean a production
change this task has ruled out. L11's full accessibility obligation is **task 135's**
(`phase1ScreensPassAccessibilityChecks`, `tasks.md:217`), not T27's.

**Folded as:** AC9 is restated to what is true and verifiable today — the resumption status sits in a live
region and is announced rather than only rendered, and the focus *state* across the hand-over and the
resumption is **measured and pinned** so any future change is visible — with the gap recorded as a declared
deviation naming task 135 as its owner.

### F8 — the sign-in notice: ACCEPTED, target relocated

Verified `signInNotice.ts:13`: `export type SignInNotice = 'discarded'` — a **one-member union**, a token
rather than copy. Asserting that the notice after an MFA-refusal equals the notice after a wrong password is
asserting `'discarded' === 'discarded'`: it cannot fail, and a type change is the only way to add a second
value, which is a compile error at every exhaustive site rather than something a runtime case would catch.

The concern is real but lives one layer out. The **copy** is in `src/i18n/catalogue.ts`, which is where a
future `signIn.mfaRequired` or `callback.enterYourCode` would actually appear — and `catalogue.ts:8-9`
already states the rule in its own comment ("nothing promises a password step, because which factors are
required is the server's to decide (L2)").

**Folded as:** AC6 gains a scan of the catalogue for MFA-shaped copy across every user-facing string, with
the one-member union recorded as the reason the notice itself is not separately asserted.

### F9 — the second-click case, accepted

**Folded as:** Required Test 1's second-click row asserts both that the hand-over counter stays at one and
that the store does not grow beyond the inventory T26 pinned — the mechanism matters, and a second
`buildAuthorizeRequest` that overwrote the verifier without navigating is the failure worth naming.

---

## Open Questions — final disposition

1. **OQ1 — the SAS login page has no `mfaCode` field. DEFERRED, owner: backend / project owner.** Measured in
   Phase 0: no custom `loginPage(...)`, no `src/main/resources/templates` directory, no login template anywhere
   in the repository, so SAS serves its default generated page with username and password only, while
   `TotpAuthenticationDetailsSource` accepts `mfaCode` when it arrives and `SasLoginIntegrationTest` posts it
   directly. **A real browser cannot complete an MFA sign-in today.** L2 puts that UI on the server; it is
   neither T27's to build nor the SPA's to work around. Needs a backend task or an accepted limitation
   recorded against R9. **Does not block T27.**
2. **OQ2 — does R9 imply SPA-observable behaviour beyond absence and symmetry? RESOLVED: no.** On the measured
   architecture the second factor is a field in the same request, so the SPA cannot observe the step count.
   The absence of a difference is the claim.
3. **OQ3 — `amr` and `acr` are not in `contracts/api/token-claims.md`. RESOLVED as a constraint.** The paired
   fixtures prove **indifference only**. No case may assert that either claim is present, and the contract
   file is already known to be wrong about `email` and `name` — that is a standing package item, not T27's.

---

## Task

Build `src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx`, the named test for R9. One new
test file; no production change anticipated.

## Purpose

R9 says the second factor runs inside the SAS-hosted flow and the SPA resumes at the callback once it
completes. Both halves are about **restraint**, and that makes the task unusual: there is no feature to build,
and the failure mode is not a bug but a plausible-looking addition. A one-time-code field on the sign-in
screen, an "enter your authenticator code" step before the redirect, a branch in the callback for "MFA was
used" — each would look like progress, each would breach L2, and two of them would breach L4 and R10 as well
by telling an attacker which accounts have a second factor.

**Measured premise (Phase 0, read from `services/auth/`).** The backend's second factor is a **field, not a
step**: `TotpAuthenticationDetailsSource` captures an optional `mfaCode` submitted in the *same request* as the
password — its own comment calls this "task 20's single-request MFA design, O4" — and `SecurityChainsConfig`
registers `TotpAuthenticationProvider` as the only provider that filter's manager can reach, so password alone
cannot authenticate.

So the SPA **cannot observe how many factors were verified**. It hands over control with a full-page
navigation and gets it back at `/app/callback`. That is not a gap in the test's reach; it is the architecture
R9 describes, and it tells us what the verifiable claim is: **the SPA contains no factor, and its resumption
is symmetric.** The fixture differs, the outcome does not.

## Scope

**In**
- One new test file.
- The hand-over: exactly one full-page navigation to the authorize URL **by any mechanism**, and nothing of
  the login rendered here.
- A **structural absence** claim over every production module and over the dependency manifest: no credential
  input, no second-factor logic, no second-factor package.
- **Symmetry**: the same callback under fixtures that differ only in what SAS did behind the redirect,
  including one carrying an explicit server-sent MFA marker.
- The negative paths that must stay uniform: a refusal reaching the SPA as an ordinary discard, with the
  catalogue scanned for MFA-shaped copy.
- A sweep proving no MFA-shaped value passes through the SPA, **including the exchange response body and the
  decoded ID-token claims**.
- The announcement at the point of resumption, and a **pin** on the focus state across the hand-over and the
  resumption.
- Controls that make every instrument fail.

**Out**
- Building, rendering or validating any second factor. L2 puts it on the server; adding one here is the defect
  this task guards against.
- The **SAS login page's own `mfaCode` field**, which does not exist (OQ1). Server-side, and not workable
  around from here.
- **Building focus management.** No production module moves focus today; L11's accessibility gate is task
  135's (`phase1ScreensPassAccessibilityChecks`). T27 measures and pins, it does not build.
- R11's voluntary enrollment wizard and `auth.yaml`'s `.../totp` endpoints — `account/mfa` is a route path
  with no element, owned by tasks 56–60.
- Re-proving T16's authorize request, T19–T22's nonce/exchange/token handling, T23's deletions, T24/T25's
  return target, T26's inventory, or R57's denylist. Each is cited where T27 needs it.
- A real browser round trip. jsdom performs no navigation (T23-D5); `oidcRoundTripEndToEnd` has no suite and
  no owning task.
- Any production change. One found in Phase 6 is a finding for the Phase 9 gate.

## Business Rules

- **R9** — the TOTP or recovery-code challenge runs inside the SAS-hosted flow; the SPA resumes at the
  callback once it completes.
- **R10** (must not be contradicted) — every login failure, including a privileged account with no confirmed
  enrollment, shows the same uniform message; the SPA never says enrollment is required.
- **R57** (cited) — `/login` is never answered from cache and never given the app shell.
- **R6, R7** (boundaries) — the hand-over and the callback machinery T27 rides on.

## Locked Decisions

- **L2** — interactive login is SAS-hosted: password, TOTP and recovery-code steps run in the SAS flow. The
  authority for the prohibition.
- **L5** — SAS *refuses* an unenrolled privileged account with the same error as a wrong password and does not
  enroll it; so the SPA has no enrollment-during-login path at all.
- **L11** — "managed focus across redirect and MFA steps": measured and pinned here, built and gated by task 135.
- **L1, L3, L4, L9, L14, L16, L17** — PKCE and validated `state`/`nonce`; tokens in memory; enumeration-safe UI
  with no public carve-out; single origin including `/login`; the sessionStorage carve-out; `/app/callback` is
  public; the service worker never caches `/login`.
- **`agents.md`** — authoritative: SAS-hosted login, uniform failure copy, no credential in storage or logs.

## Dependencies

`buildAuthorizeRequest`, `consumeAuthorizeState`, `takeReturnTarget`, `rememberReturnTarget`,
`takeSignInNotice`/`setSignInNotice`; `AUTHORIZE_ENDPOINT_PATH`, `CLIENT_ID`, `REQUESTED_SCOPES`,
`AUTHORIZE_STORAGE_KEYS`, `RETURN_TARGET_KEY`, `DENYLISTED_NAVIGATION_PATHS`, `APP_BASENAME`, `appRoutes`,
`en` (the catalogue); `SignInScreen`, `CallbackScreen`, `createI18n()`; vitest, jsdom, `act`, `createRoot`,
`createMemoryRouter`, `node:fs/promises`, `vi.stubGlobal`. No config keys. No contracts.

## Inputs

Not an API. The inputs are: the real sign-in screen driven to its navigation, the real callback screen driven
under paired fixtures, the production source tree, the dependency manifest, the i18n catalogue, and synthetic
violations used as controls.

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
  `accessToken.ts`, `signInNotice.ts`, `returnTarget.ts`, `pkce.ts`; `src/app/routes.tsx`;
  `src/i18n/catalogue.ts`; `src/pwa/navigationDenylist.ts`; `package.json`.
- The neighbouring named tests — read as patterns, cited where they own a claim, never moved or duplicated.
- Anything under `services/auth/`. OQ1 is a backend/owner item.

## Acceptance Criteria

- **AC1** — the sign-in start hands over by **exactly one full-page navigation to the authorize URL, counted
  across every mechanism** (`assign`, `replace`, the `href` setter, `window.open`); nothing of the login is
  rendered here; a second click adds no second navigation. (R9, L2 · F1)
- **AC2** — **no credential input of any kind anywhere in the SPA**: not a password, not a one-time code, not
  a recovery code, proven structurally over production source. (R9, L2, L5)
- **AC3** — no second-factor logic ships and **no second-factor package is installed**: no TOTP generation or
  verification, no recovery-code handling, no WebAuthn, no MFA-specific branch in the sign-in or callback
  path, no forbidden import in source, and no forbidden entry in `package.json`. (L2, L5 · F2, F3)
- **AC4** — **the callback is step-count-agnostic**: fixtures that differ only in what SAS did behind the
  redirect — including one carrying an explicit `amr: ["pwd","otp"]` and a custom `mfa_method` claim — produce
  the same destination, the same rendered text, the same stored state and the same request shape. (R9 · F4)
- **AC5** — resumption needs no session and nothing beyond the three PKCE items. (R9, L14)
- **AC6** — a refusal of an unenrolled privileged account arrives as an ordinary discard: uniform copy, the
  sign-in start, and **no MFA-shaped string anywhere in the i18n catalogue** — no mention of enrollment, MFA,
  an authenticator or a code. (R10, L4, L5 · F8)
- **AC7** — no MFA-shaped value passes through the SPA: not in a request body or URL, **the exchange response
  body, the decoded ID-token claims**, the router location, or either store, under fixtures that carry such
  values. (R9, L3 · F5)
- **AC8** — `/login` is denylisted, cited from the owning module rather than re-proved. (R57, L17)
- **AC9** — the status at the point of resumption is **announced, not only rendered** — it sits in a live
  region — and the **focus state is measured and pinned** across the hand-over and the resumption rather than
  asserted as an obligation: neither screen moves focus today, the sign-in button retains focus (T16 owns
  that), and the resumption's focus outcome is recorded so a change is visible. L11's gate is task 135's.
  (L11 · F7, declared deviation D-1)
- **AC10** — the authorize request carries no MFA negotiation parameter; its parameter set is asserted
  **exactly, as the eight literal wire-protocol names**, so an addition fails. The file must record why a
  derived set is refused here: T16:223 already compares the screen's URL to the builder's and cannot catch an
  addition, because both sides come from the same builder. (R9, L2 · F6 rejected)
- **AC11** — every instrument is proven to report DIRTY first: the scans fail on a synthetic credential input,
  a synthetic `verifyTotp`, a synthetic forbidden import and a synthetic catalogue string; the symmetry
  comparison fails when the fixtures differ in a way the SPA *should* notice. (T22-D14)

## Required Tests

Named test: **`loginChallengeRunsInsideSasFlowAndResumesAtCallback`** → R9 (`package.md:113`).

1. **The hand-over** — exactly one navigation to the authorize URL counted across `assign`, `replace`, the
   `href` setter and `window.open`; no credential field rendered; the second click adding neither a second
   navigation nor a fifth stored key.
2. **Structural absence** — a comment-stripped scan of every production module for (a) credential-collecting
   shapes (`type="password"`, `autoComplete` one-time-code values), (b) MFA-shaped **identifiers**, and (c)
   forbidden **imports**; plus an assertion over `package.json`. Exactly two permitted hits, each named with
   its reason: `account/mfa` (`routes.tsx:39`, a route path with no element) and PKCE's `code_challenge` /
   `code_challenge_method`. Synthetic files carrying a one-time-code input, a `function verifyTotp()` and an
   `import ... from 'otpauth'` must each fail it.
3. **Symmetry** — the same callback under three fixtures differing only in what the server did (`amr: ["pwd"]`;
   `amr: ["pwd","otp"]` with a differing `acr`/`auth_time`; and one adding `mfa_method: "totp"`): identical
   destination, text, stored state and request shape. **Plus the control** that a difference the SPA *is* meant
   to notice — a wrong `nonce` — really does change the outcome, so the comparison is not blind.
4. **No MFA value escapes** — fixtures whose claims, response body and callback query carry one-time-code-shaped
   values; sweep every request body and URL, the exchange response body, the decoded ID-token claims, the
   router location, and both stores. No exclusion: unlike T26's `nonce`, no MFA-shaped value is permitted
   anywhere.
5. **A privileged refusal is an ordinary discard** — `invalid_grant` at the exchange: uniform notice, sign-in
   start, and no MFA-shaped copy in the rendered text or anywhere in `catalogue.ts`.
6. **Resumption from a cold tab** — only the three PKCE items present; no session, no token in memory; the
   exchange completes and the visitor lands.
7. **Announcement and the focus pin** — the resumption status sits in a live region; the focus state across the
   hand-over and the resumption is measured and pinned, with the deviation named in the message.
8. **The authorize parameter set** — exactly the eight literal names, asserted as a set, with no MFA
   parameter, and the reasoning for literals recorded in the file.
9. **`/login` denylisted** — one assertion citing `navigationDenylist.ts`.
10. **Controls** — the scan over an empty tree refused; each synthetic violation failing its own instrument;
    the symmetry helper shown to fail on a real difference; a navigation spy installed late shown to miss the
    hand-over.

## Constraints

- **Security.** The file must not weaken an existing claim to make its own pass. Where a claim exists (T16's no
  password field, T19–T22's exchange, T23's deletions, T26's inventory, R57's denylist), cite it.
- **No MFA-specific production code may be introduced**, including as a test fixture that only works against
  one. The subject is absence.
- **Uniformity.** No case may assert copy that distinguishes an MFA failure from a wrong password — asserting
  such copy would make a future breach of R10 pass.
- **Module boundaries.** Routes, keys, scopes and copy come from the modules that own them. The eight authorize
  **parameter names** are the one deliberate exception (AC10), and the file says why.
- **Instrument ordering.** A `location`, `fetch` or `Storage.prototype` stub must be installed before the code
  under test runs; the file must hold the control that demonstrates it.
- **Comment stripping before any source scan**, and assertions over *files*, not `path:line` — stripping a
  block comment removes its newlines. A file explains the rule it obeys, and `SignInScreen.tsx:12` and
  `catalogue.ts:8-9` both explain this very one.
- **Vacuity.** An empty source tree, a scan whose pattern matches nothing, a symmetry comparison between two
  identical fixtures, and a parameter set derived from the thing it checks each satisfy the claim while proving
  nothing. Each must be refused explicitly.
- **jsdom limits, declared not hidden.** `location.assign` performs no navigation (T23-D5), so the hand-over is
  a spy plus structure. A genuine round trip is `oidcRoundTripEndToEnd`'s, which has no suite and no owning task.
- **Verification.** `npx -y pnpm@12.14.0 exec vitest run …` from `frontend/`; **the exit code is the verdict**.
  One known environmental failure: `lintRejectsHandWrittenBackendFetch.test.ts:118`.
- **Mutation discipline.** Tests committed before any mutation run (the harness reverts with `git checkout`),
  and the harness committed with the artifacts. Mutants should include the plausible additions this task
  forbids: a one-time-code field on the sign-in screen, an MFA branch in the callback, MFA-specific copy in the
  catalogue, an `acr_values` parameter on the authorize request, and **a second hand-over through
  `location.href`** (the mutant F1 exists for).
- **Performance / concurrency / transactions.** Not applicable.

## Declared Deviations

- **D-1 — focus is pinned, not managed.** L11 names "managed focus across redirect and MFA steps". Measured: no
  production module moves focus, and the resumption's destination `/app/home` has no element (T24-D11). AC9
  therefore records the state rather than requiring a change. **Owner: task 135**
  (`phase1ScreensPassAccessibilityChecks`, `tasks.md:217`).

---

**Phase 4 complete. STATUS: FROZEN.** Ready for Phase 5 implementation planning.
