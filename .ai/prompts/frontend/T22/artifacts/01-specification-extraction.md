# frontend · T22 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T22 — No token in any storage |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

---

## Business Rules

| ID | One sentence |
|---|---|
| **R7** (scoped) | On a matching `state`, the SPA validates `nonce`, exchanges the code, and **holds the access token in memory only** (`requirements.md:23-24`). |
| **R29** (boundary only) | Only non-sensitive form input may sit in `sessionStorage`, with a 15-minute TTL, and **no tokens, passwords, recovery codes, API keys or PII in any storage** (`requirements.md:101-103`). Cited here because its prohibition is the same one T22 measures; its TTL half is task 26's. |
| **R57** (adjacent) | API routes are NetworkOnly and identity navigations are never answered from cache — the Cache Storage half of "any storage", already built. |
| **R58** (adjacent) | The production bundle carries no secret value — the precedent instrument, not a rule T22 verifies. |

`agents.md` is the sharpest statement of the obligation and is authoritative:

- **`agents.md:79`** — "Tokens are never written to `localStorage`, logs, or analytics, and are **never placed in a
  URL**." This is where "a URL" in the task statement comes from; `requirements.md` does not say it.
- **`agents.md:80-81`** — sessionStorage is the only sanctioned client-side storage for sensitive data, and only
  for the L16 list. Nothing else is persisted client-side.
- **`agents.md:82`** — never log or send to analytics: tokens, PINs, recovery codes, API keys, passwords, PII.
- **`agents.md:85`** — sensitive response bodies, token among them, are never cached.

## Locked Decisions

| ID | What it constrains here |
|---|---|
| **L3** (scoped) | `design.md:13` — access tokens are never written to storage, logs or analytics. Also settles the refresh-token question: **no `refresh_token` is issued to `checky-spa` at all**, measured against the running service, so a stored refresh token is not a thing that can exist. |
| **L16** | `design.md:55-57` — sessionStorage may hold, and only hold, (a) `state`, `nonce`, `code_verifier` and the post-login return target, single-use and deleted at callback, and (b) non-sensitive mid-task form input with a 15-minute TTL. **This list is the entire carve-out.** Decisive for T22's scope: `code_verifier` is a credential the spec *permits* in sessionStorage, so it cannot be one of the things T22 forbids. |
| **L17** | Service-worker boundaries; API routes NetworkOnly. The Cache Storage surface. |
| **L13** | No new dependency and no state library — a sweep needs neither. |
| **L4** | Neutral copy; no test may require copy that distinguishes failures. |
| **L9** | One origin, so "a cookie" means a cookie on `localhost:5173` — and the SPA writes none. |

**Q9 (`package.md`) — analytics: none at launch, resolved 2026-10-10.** L3's "or analytics" clause therefore has
no mechanism in the package to violate, by decision rather than by omission.

## Files involved

**To read (the sweep's subject):** every `.ts`/`.tsx` under `frontend/src` — 25 production modules across
`api/`, `app/`, `auth/`, `i18n/`, `pwa/`. The ones that touch a token or a store:

| File | Why |
|---|---|
| `auth/authorizeRequest.ts:81-84` | the only storage writer in the service |
| `auth/accessToken.ts:24` | the access token's one home |
| `auth/codeExchange.ts` | both tokens pass through; `:59` a guarded read |
| `auth/idTokenNonce.ts` | decodes the ID token; `:97` a guarded read |
| `auth/CallbackScreen.tsx` | the one screen a token reaches; `:105` a guarded read |
| `api/sessionBoundary.ts:88-108` | the bearer header and its transient copies |
| `api/crossTabRenewal.ts:116` | posts a renewed token between tabs |
| `pwa/apiRequestPolicy.ts` | the Cache Storage policy, already built |

**New:** `frontend/src/auth/tokensAreNeverWrittenToAnyStorage.test.ts` — the named test (`package.md:157`).
`src/auth/` because the subject is the auth flow and every token in the package originates there, even though the
sweep reads the whole tree.

**Expected to change:** nothing in production. If the sweep finds a leak, the fix is in scope; the measurement in
Phase 0 says it will not.

## Dependencies

No new ones (L13). The sweep uses what the suite already has: `vitest`, `jsdom`, and Node's `fs`/`path` through
`/// <reference types="node" />`. The real consumers it drives are `buildAuthorizeRequest`,
`exchangeAuthorizationCode`, `CallbackScreen`, `accessToken`, `createSessionBoundary`, `createAuthApi` and
`shareRenewalAcrossTabs` — all built. `Storage.prototype` spies for the write surface. No config key, no contract.

## Acceptance Criteria

Mapped to R7/L3 unless stated. The **scope of "a token"** is AC0 and everything else depends on it.

| AC | Criterion |
|---|---|
| **AC0** | The sweep's definition of "a token" is stated in the file and is: the **access token**, the **ID token** (whole, and any of its three segments or decoded claim values), and any **`refresh_token`** should one ever appear. **Not** `state`, `nonce` or `code_verifier` — L16 permits those in sessionStorage, so forbidding them would contradict the spec. |
| **AC1** | After a **complete, successful** sign-in → callback → exchange, driven through the real modules, neither `sessionStorage` nor `localStorage` contains any in-scope token value or segment. Proven by dumping every key and value, not by checking expected keys. |
| **AC2** | After the same success, `sessionStorage` contains **exactly** the three L16 items and nothing else, each matched to the value the authorize step generated. |
| **AC3** | `document.cookie` gains nothing across the whole flow. |
| **AC4** | No `indexedDB` database is opened or created by the flow. |
| **AC5** | No token value or segment appears in `location.href`, `location.search`, `location.hash`, `document.title`, or the rendered DOM at any point in the flow. |
| **AC6** | No token value or segment reaches any of the four `console` channels (`log`, `info`, `warn`, `error`), nor a thrown `Error`'s message, across the whole flow including every failure path. |
| **AC7** | `Storage.prototype.setItem` is called **exactly three times** in a successful flow, all three from the authorize step, none with a token value. `removeItem` and `clear` are not called at all (which also records, without fixing, that nothing deletes the L16 items — task 23's). |
| **AC8** | The same sweep run over **every failure path** (no `state`, mismatched `state`, no `code`, an authorization error, a refused code, a 5xx, a dropped connection, an unusable response, a bad nonce) finds nothing in any surface. |
| **AC9** | A **package-wide source scan** over every production `.ts`/`.tsx` under `src/` finds no `localStorage`, no `indexedDB`, no `document.cookie`, no `console.*`, and no `setItem` outside `auth/authorizeRequest.ts`. Comments stripped before scanning, and the stripped body asserted non-empty. |
| **AC10** | Each scan and each sweep **proves its own instrument**: a synthetic violation fed to the same function is caught, and an empty input is refused rather than passing. |
| **AC11** | The sweep survives a **throwing** store (blocked site data): touching `sessionStorage` can throw rather than return `null`, and the sweep must report honestly rather than pass vacuously. |
| **AC12** | A token that reaches a sibling tab through `shareRenewalAcrossTabs` is still in no storage surface afterwards — the one path that moves a token between contexts. |
| **AC13** | No new user-visible string, no catalogue key, no new dependency, no production change (or, if the sweep finds a real leak, the minimal fix plus the case that caught it). |
| **AC14** | The suite passes with the one known environmental failure and no other, **verified by the run's exit code**. |

## Tests required

**The named test:** `tokensAreNeverWrittenToAnyStorage` → R7 (`package.md:157`). New file, and the whole
deliverable.

**Named tests in the header that are NOT T22's**, each with its owner, so the line is explicit:

| Named test | Owner |
|---|---|
| `callbackValidatesNonceAndKeepsAccessTokenInMemory` | T19 + **T21**, built, 108 cases |
| `callbackValidatesNonce` | T19, built |
| `returnTargetOutsideAllowlistFallsBackToHome` | tasks **24, 25** (`tasks.md:36-37`) |
| `stateSurvivesRedirectAndIsConsumedOnce` | task **23** (`tasks.md:35`) — owns single-use deletion |
| `sessionStorageHoldsOnlyTheL16Items` | task **26** (`tasks.md:38`) — owns the R29/L16 inventory |
| `recoveryCodesNeverPersistedClientSide` | task **69** |
| `receiptNeverCachedClientSide` | task **116** (blocked on Q2) |
| `serviceWorkerNeverCachesApiResponses` | task owning R57, built — the Cache Storage surface |

**Boundary tests implied** beyond the ACs: the flow run twice in one tab (a second sign-in leaves no residue from
the first); a sweep after `clearAccessToken()`; and a case proving the sweep reads **values**, not just keys, since
a token written under an innocuous key is the leak a key-list check would miss.

## Open Questions

**OQ-1 — Does the sweep build the real bundle?** `bundleContainsNoSecretEnvKeys.test.ts` builds it, and a built
bundle is the only place a leak introduced by a *dependency* would show (react-router writing a URL to
`localStorage`, say). It is also the slowest file in the suite. Not a blocker: the sweep is complete without it,
and Phase 2 can scope it in or out. **Recommendation: out**, with the reason recorded — the dependency surface is
better covered by the source scan plus the runtime sweep, and a second full build doubles the suite's slowest
cost for a class of leak that T01's build-time instrument already watches.

**OQ-2 — Is the authorization `code` in the URL in scope?** After a successful callback the visitor sits on
`/app/callback?code=…&state=…` until the task that owns the post-login destination (24) moves them. The code is a
one-time credential, not a token, and the SPA did not place it there — SAS redirected to it. `agents.md:79`
forbids placing *tokens* in a URL. **Recommendation: out of scope, recorded as an observation**, and named as a
question for task 24 which owns the navigation. Not a blocker either way.

**OQ-3 — Does "a log" include a thrown `Error`'s message?** T21 forbids it inside `accessToken.ts` only. A thrown
message can reach a console through an uncaught rejection, so the obligation is at least adjacent.
**Recommendation: in scope**, as AC6 states — it costs one scan and closes the one route by which a token could
reach a console in a package that makes no `console` call.

**No blockers.** All three are scoping choices with a recommendation attached, and Phase 2 freezes them.

---

## Next — Phase 2 — task implementation brief.
