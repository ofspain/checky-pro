# frontend · T30 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T30 — Profile from userinfo |
| **Model** | Claude Opus 5 |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

Grounding only. No code written, no design, no requirement extraction.

**The headline, stated first because it decides the shape of this task:** nothing in the frontend reads a
profile today, `/userinfo` is **deliberately absent from every contract**, and a previous task **measured**
the live `/userinfo` returning only `{"sub"}`. T30's premise — "profile data is read from `/userinfo`" — may
have no profile data to read, and no sanctioned mechanism to read it with. Details in **Known gaps**, which
is the section of this artifact that matters.

---

## 1 · Architecture summary

A single-origin React 19.3.1 + TypeScript PWA under the `/app` prefix. Not a Spring service, so most of the
phase-0 template's categories (JPA, Flyway, outbox, resource-server security) have no counterpart here.

| Concern | Where it lives |
|---|---|
| **Entry and composition** | `main.tsx` → `app/mountShell.tsx` → `app/AppShell.tsx`, routes in `app/routes.tsx`, all under the `/app` basename |
| **Identity** | `auth/` — `authorizeRequest.ts` (the eight authorize parameters, S256, three scopes), `pkce.ts`, `idTokenNonce.ts`, `codeExchange.ts`, `accessToken.ts` (in-memory only), `returnTarget.ts`, `signInNotice.ts`, and two screens |
| **API access** | `api/client.ts` wraps the **generated** `@themistra/api-client` (from `libs/ts/api-client`, generated in CI from `contracts/api/*.yaml`) |
| **Client boundary** | `api/sessionBoundary.ts` (single-flight 401 renewal), `api/crossTabRenewal.ts` (sibling-tab sharing), `api/rateLimitNotice.ts` (fixed 429 copy) — all middleware on the generated client |
| **Persistence** | None beyond `sessionStorage`, and only the three L16 items. Tokens are in memory only |
| **Service worker** | `pwa/` — NetworkOnly for API routes, a navigation denylist, prompt-to-reload updates |
| **Copy** | `i18n/catalogue.ts` — every user-facing string, no hardcoded copy in components |

There are **24 production modules and 25 test files**. The suite is 1399 cases.

## 2 · Existing code this task touches

### Already exists

| Thing | State |
|---|---|
| `api/client.ts` → `createAuthApi(boundary, rateLimit?)` | Built. Returns the generated client with the rate-limit notice and session boundary registered, in that order |
| `GET /accounts/me` as a **typed operation** | Exists on the generated client — exercised today only from tests (`callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx:1039`, `:1047`, `:1183`) |
| `AccountResponse` schema | `contracts/api/auth.yaml:659-675` — `accountUuid`, `email`, `emailVerified`, `status` (`PENDING_VERIFICATION`/`ACTIVE`/`LOCKED`/`SUSPENDED`/`DELETED`), `createdAt` |
| `auth/accessToken.ts` | In-memory holder; `current()` returns the raw token |
| `i18n/catalogue.ts` | Where any profile copy would have to live |
| `pwa/apiRequestPolicy.ts:30` | Already lists `/userinfo` as never-cached |

### Does not exist

- **Any profile module, store, hook or screen.** `grep` over `frontend/src` finds **no production call** to
  `/accounts/me` or `/userinfo`. The only non-test mentions of `/userinfo` are the service-worker denylist and
  the route test that asserts it is not a SPA route.
- **Any `/userinfo` operation on the generated client** — it cannot exist; see below.
- **The named test** `profileIsReadFromUserinfoAndAccountStatusFromMe` — no file by that name.
- **TanStack Query and Zustand**, which `agents.md` names in the confirmed stack, are not wired anywhere yet.
  Whether T30 introduces either is a Phase 1/2 question, not a fact about today.

## 3 · Established patterns to follow

- **Every backend call goes through the generated client.** `agents.md` §API access, LOCKED `[ALL]`: "No
  hand-written `fetch` to backend routes. The only exception is named in `design.md` L6 (the notification
  stream, R59)." Enforced by `api/lintRejectsHandWrittenBackendFetch.test.ts`.
- **Middleware order is load-bearing.** `client.ts:21-23` registers the rate-limit notice *first* because
  openapi-fetch runs `onResponse` in reverse registration order. Any new client takes the same notice and
  registers it the same way "or it loses R23's copy".
- **Screens never reimplement the boundary.** 401 renewal, 429 copy and network failure states belong to
  `api/`, not to a screen.
- **Errors are RFC 9457 `application/problem+json`**, mapped to copy, never shown as detail.
- **No PII is ever persisted client-side**, and nothing is logged or sent to analytics. This bites T30
  directly: a profile *is* PII, so wherever it is held, it is held in memory.
- **Every string is in the catalogue.**
- **Absence is proven by scanning the comment-stripped tree against an allowlist**, not by a file list — the
  pattern T27–T29 converged on. T29's literal-aware stripper and its `encodings()` leak sweep are the current
  state of that art and are reusable.

## 4 · Testing conventions

`agents.md`'s JUnit, fixed-`Clock`, Testcontainers and ArchUnit clauses are **backend** clauses with no
counterpart here. What applies in `frontend/`:

- **Vitest 5.0.5 + jsdom 30.1.3**, run as `npx -y pnpm@12.14.0 exec vitest run`. Bare `pnpm` is not on this
  host. **The exit code is the verdict, not the pass count.**
- One test file per named test from `package.md` §8, named exactly after it.
- No production import is mocked; the catalogue is the source of all asserted copy.
- `vi.useFakeTimers()` where timing is claimed, with `vi.useRealTimers()` after the unmount block.
- Every absence instrument carries a control proving it reports DIRTY first, and a scan that reads nothing is
  refused rather than reported clean.
- A mutation harness per task under `.ai/prompts/frontend/T<n>/artifacts/07-mutation-harness.py`. It reverts
  with `git checkout -- src/`, so **tests are committed before it runs**, and since T29 it asserts its own
  baseline and exits non-zero on a survivor.
- **One known environmental failure**, pre-existing and unrelated to any task:
  `src/api/lintRejectsHandWrittenBackendFetch.test.ts` — this host's `oxlint@1.87.0` prints no summary line
  for it to parse. The suite is therefore `1398 passed / 1 failed (1399)` on a clean tree.
- Playwright is named in the stack and **has no suite**. `oidcRoundTripEndToEnd` (task 134) is unowned.

## 5 · Known gaps and unknowns

### G1 · `/userinfo` is deliberately absent from every contract, and the sanctioned client therefore cannot call it

`contracts/api/auth.yaml:5-7` says it outright:

> "protocol endpoints (`/oauth2/**`, `/.well-known/**`, `/userinfo`, `/login`) are governed by SAS itself and
> are intentionally not documented here (R47/T33 scope)."

So the generated client has no `/userinfo` operation and cannot be made to have one without documenting the
endpoint. Meanwhile `agents.md` forbids hand-written `fetch` to backend routes with exactly one carve-out,
and that carve-out is the notification stream (L6, R59) — not this.

**R20 requires a call that the only sanctioned mechanism cannot make.** Whether an OIDC protocol endpoint on
SAS even counts as a "backend route" for that rule is arguable and is not argued anywhere I can find. This is
the central question of the task and belongs in Phase 1's extraction and Phase 2's brief, not here.

### G2 · `/userinfo` was MEASURED to return only `{"sub"}`

T19's Phase 12 artifact records this as a measured fact, not a reading:

> "`contracts/api/token-claims.md` says email and name arrive in the `id_token` or from `/userinfo`; the
> id_token carries neither and `GET /userinfo` returns only `{"sub"}`. Discovery advertises
> `scopes_supported: ["openid"]` for a three-scope client. Owner: whoever owns `contracts/`."

If that still holds, **T30 has no profile data to read from the endpoint it is told to read it from**, and
the most likely root cause is the discovery finding in the same sentence: SAS advertising only `openid` means
the `profile` and `email` scopes are very likely not being issued, so `/userinfo` has nothing to return.

**I could not re-measure.** `docker ps` returns nothing — the local stack is down, so this is cited from
T19, not verified today. **Re-measuring it is the first thing Phase 1 or Phase 2 should do**, because
everything about T30's shape depends on the answer, and a measurement from several tasks ago against a stack
that has since changed is exactly the kind of claim this package has been wrong about before.

### G3 · A correction to a carry-forward note I have been repeating

I have been carrying "`contracts/api/token-claims.md` wrong on `email`/`name`, silent on `amr`/`acr`" from
task to task. **Read against the file, that is wrong on both counts.** `token-claims.md` documents `amr` and
`acr` in its 13-claim table *and* per issuance path, and it does not claim the access token carries email or
a name — it says the opposite, explicitly: "No access token issued by this service ever contains an email
address or a name; those live in the `id_token`/`/userinfo` response for interactive logins only."

The real finding is narrower and worse: that last positive claim — email and name live in the `id_token` or
`/userinfo` — is the thing T19 measured **false**. The document is internally coherent and says the right
thing about the access token, which is what R20 cares about; what it asserts about where the data *does* live
is unverified and was contradicted once. The note is corrected here rather than repeated.

### G4 · `email_verified` means something other than what its name says

`token-claims.md` Path 1: `email_verified` "reflects scope authorization, not literally 'has this account
verified its email.'" A real token requesting only `scope=openid` carries `email_verified=false` — confirmed
against a real token. Path 3 hardcodes it `false`. So the claim is actively misleading as account state,
which is why R20 forbids driving verification UI from it. **Task 32 owns that clause** and its named test
`emailVerifiedClaimIsNotAccountState`; T30 should not absorb it.

### G5 · Three tasks share this requirement and two share one named test

| Task | Clause | Named test |
|---|---|---|
| **T30** | profile from `/userinfo` | `profileIsReadFromUserinfoAndAccountStatusFromMe` |
| **T31** | account status from `GET /accounts/me` | **the same test** |
| **T32** | `email_verified` is not account state | `emailVerifiedClaimIsNotAccountState` |

The split precedent in this package is well established — T24 created
`returnTargetOutsideAllowlistFallsBackToHome` at 67 cases and T25 grew it to 184; T29 owns one axis of
`identityErrorsIndistinguishableAcrossAccountStates` while T58 extends it and T136 gates it. T30 creating the
file and T31 extending it would follow that precedent. **Phase 1 should state the split explicitly rather
than leave it to be discovered**, and Phase 4 should freeze it.

### G6 · Open, and not T30's

`oidcRoundTripEndToEnd` (task 134) has no suite and no owning task — the package's largest verification gap.
`mountShell.tsx` is unowned. L16 clause (b) is unbuilt and unowned. **OQ1**: the live SAS `/login` renders no
`mfaCode` field while the backend requires it in the same POST. The phase 13 prompt template still specifies a
`Claude Opus 4.8 (1M context)` trailer — ten consecutive tasks; the fix belongs in `.ai/generate.py`.

### What I do not know

- Whether `/userinfo` returns more than `{"sub"}` **today**. Not measurable with the stack down.
- Whether the SPA is currently authorized the `profile` and `email` scopes in practice, as opposed to
  requesting them. `authorizeRequestsExactlyTheDeclaredScopes` proves the SPA *asks*; nothing proves SAS
  *grants*.
- Whether T30 is meant to **build** a profile reader or only to **verify** one. The task statement says
  "Profile data **is read** from `/userinfo`", which reads as a verification of existing behaviour — but no
  such behaviour exists, which is unlike T29, where the uniformity being verified was already structural.
  Phase 1 resolves this from `design.md`; I will not guess here.

---

**Phase 0 complete.** Ready for Phase 1 — specification extraction.
