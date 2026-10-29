# frontend · T27 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T27 — SAS MFA challenge resumes at callback |
| **Model** | Claude Opus 5 |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

T27 is the task where the SPA's job is to have no job. R9 puts the second factor inside the Spring
Authorization Server flow, so the thing to verify is that the SPA hands over control, keeps none of it, and
resumes identically however many steps the server took.

---

## 1. Architecture summary

React 19.3.1 / TypeScript 7.0.2 SPA under `frontend/`, Vite 8.3.5, Vitest 5.0.5 on jsdom 30.1.3, served under
the `/app` basename. No server, no persistence beyond the four L16 items in `sessionStorage`, no outbox.

The modules this task touches are the sign-in boundary:

- **`src/auth/SignInScreen.tsx`** — offers the choice and nothing else. Its header already states T27's own
  premise: "it renders no password field, and no credential input of any kind… the password, TOTP and
  recovery-code steps belong to the server and never to this bundle (L2, `agents.md`)". It calls
  `buildAuthorizeRequest()` and then `globalThis.location.assign(url)` — a **full-page navigation**, with an
  `inFlight` ref rather than a state flag so two clicks in one task cannot fire two navigations.
- **`src/auth/authorizeRequest.ts`** — the eight parameters, S256, exactly three scopes, and the three
  sessionStorage items the callback reads back.
- **`src/auth/CallbackScreen.tsx`** — validates `state`, exchanges the code, holds the token in memory,
  navigates to the return target. It is where "resumes at the callback" actually happens.
- **`src/auth/codeExchange.ts`**, **`idTokenNonce.ts`**, **`accessToken.ts`** — the exchange, the nonce check,
  the in-memory holder (L3).
- **`src/pwa/navigationDenylist.ts`** — the eleven paths the service worker never answers from cache, `/login`
  among them (L17, R57). That denylist is what makes a SAS-hosted login reachable at all from an installed PWA.

**Security posture.** Interactive login is SAS-hosted (L2). The SPA is a public client with PKCE and no secret
(L1). Tokens live in memory only (L3). Every sign-in failure shows one uniform message (R10, L4).

## 2. Existing code this task touches

Everything exists. On today's reading **T27 adds one test file and no production code.**

**Measured — the SPA contains no MFA surface at all.** `grep -niE "mfa|totp|recovery|challenge"` over
`frontend/src/**/*.ts{,x}` excluding tests returns, in full:

| Hit | What it is |
|---|---|
| `app/routes.tsx:39` — `'account/mfa'` | a **route path** in the authenticated route table, for the voluntary-enrollment wizard R11 specifies. No element, no screen; tasks 56–60's job, not this one. |
| `auth/SignInScreen.tsx:12` | a comment saying TOTP and recovery codes belong to the server |
| `auth/CallbackScreen.tsx:32`, `authorizeRequest.ts:8,67,93,94`, `pkce.ts:4,6,9` | the **PKCE** code challenge — an unrelated use of the word |

So there is no MFA input, no TOTP library, no recovery-code handling, and no second-step screen in the bundle.
That is the claim T27 has to turn into a test rather than a comment.

**New in this task:** `src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` — the named test
(`package.md` §8). No file of that name exists.

**The nearest existing claim** is T16's
`signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx`: the sign-in screen renders no
password field. T27's claim is the same shape one step wider — no credential input of *any* kind, including a
second factor, anywhere in the SPA — plus the resumption half, which T16 does not touch.

## 3. What the backend actually does, measured

R9's premise is the backend's, so it was read rather than assumed (`services/auth/`):

- **The second factor is a FIELD, not a STEP.** `TotpAuthenticationDetailsSource` captures an optional `mfaCode`
  request parameter submitted *alongside* `username` and `password` — its own comment calls this "task 20's
  single-request MFA design, O4". `SecurityChainsConfig:99-102` registers it on `formLogin` together with
  `TotpAuthenticationProvider`, which is deliberately the only provider that filter's manager can reach, "or a
  co-existing `DaoAuthenticationProvider` could authenticate password alone and skip MFA" (D-014).
- **So there is no separate challenge page.** `SasLoginIntegrationTest:561-581` posts `mfaCode` as one more form
  field in the same request as the password.
- **Consequence for R9's wording.** "The challenge runs inside the SAS-hosted flow" is satisfied more strongly
  than the requirement implies: it is not merely server-side, it is the same HTTP request as the password. And
  "the SPA resumes at the callback once it completes" involves **no SPA-observable step at all** — the SPA cannot
  see how many factors were verified, because it handed over control with a full-page navigation and gets it back
  only at `/app/callback`.

## 4. Established patterns to follow

- **Two instruments that fail differently** (T22-D14), now three in T26: a runtime instrument, a comment-stripped
  source scan with a file → *why* table, and a namespace scan keyed on names rather than calls. A structural
  claim like "the bundle contains no MFA input" is a source-scan claim, and the scan must be shown to report
  DIRTY before it is believed.
- **Measure the counterfactual first.** T25's organising move was measuring what a bare react-router would do
  before asserting what the SPA does. T27's equivalent is measuring that the callback behaves identically for a
  one-factor and a two-factor sign-in — which can only be shown by making the *fixture* differ and the *outcome*
  not.
- **`arriveAt` imports the route graph dynamically** because `vi.resetModules()` runs between cases.
- **A full-page navigation is `globalThis.location.assign`, and jsdom performs none.** T23-D5 already declares
  that "survives the full-page redirect" is structural for this reason; T27 inherits that limit.
- Keys, routes and copy come from the module that owns them — never a literal in a test.

## 5. Testing conventions

- `npx -y pnpm@12.14.0 exec vitest run …` from `frontend/`; bare `pnpm` is not installed. **The exit code is the
  verdict, not the pass count.**
- jsdom: `IS_REACT_ACT_ENVIRONMENT`, `createRoot` + `act`, `createMemoryRouter`, `I18nextProvider` with
  `createI18n()`, `fetch` stubbed per test.
- Structural equivalents of ArchUnit: the source scanners (`tokensAreNeverWrittenToAnyStorage`,
  `sessionStorageHoldsOnlyTheL16Items`, `bundleContainsNoSecretEnvKeys`, `lintRejectsHandWrittenBackendFetch`)
  and `tsc --noEmit`.
- Known environmental failure, pre-existing and unrelated: `lintRejectsHandWrittenBackendFetch.test.ts:118`.
- Tests are committed before any mutation run; the harness reverts with `git checkout`.

## 6. Known gaps / unknowns

1. **The SAS-hosted login page has no field for the second factor.** Measured: the auth service registers no
   custom `loginPage`, has no `src/main/resources/templates` directory at all, and no login template exists
   anywhere in the repository — so SAS serves its **default generated login page**, which renders `username` and
   `password` only. The backend reads `mfaCode` if it arrives, and its own integration test supplies it by
   posting the field directly. **A real browser cannot submit a second factor today**, which is why driving a
   real sign-in in this repo has required injecting the field by hand. This is squarely outside the SPA (L2 puts
   the login UI on the server) and T27 cannot fix it, but R9's end-to-end story depends on it. It is an owner /
   backend item and will be logged as an open question, not worked around.
2. **What T27 can verify is bounded by jsdom.** `location.assign` performs no navigation there, so "the visitor
   really went to SAS and came back" is structural plus a spy on the assignment — the same limit T23-D5 declared.
   A genuine round trip belongs to `oidcRoundTripEndToEnd`, which has no suite and no owning task: the largest
   verification gap in the package.
3. **Whether R9 implies any SPA-observable behaviour at all** beyond "no MFA surface" and "the callback is
   step-count-agnostic" is a Phase 1/2 question. I do not know whether the author expects a test that asserts a
   *difference* somewhere; on the measured architecture there is none to assert, and the absence of a difference
   is itself the claim.
4. **Carried forward, not this task's:** L16 clause (b) unbuilt and unowned (T26); `/app/home` has no element
   (T24-D11); `rememberReturnTarget` has no production caller until task 41; `account/mfa` has a route path and
   no screen (tasks 56–60).

---

**Phase 0 complete.** No code written, no requirements extracted, no design proposed.
