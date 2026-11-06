<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification) -->

# frontend · T36 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T36 — Sign-out ends the SAS session |
| **Consumes** | `artifacts/11-review-resolution.md` |
| **Produces** | `artifacts/12-specification-verification.md` |

Every clause below was read from `spec/frontend/` during this phase and quoted from source, not from the
earlier artifacts. T33's phase 12 was written partly from memory and carried stale case references; this one
cites line numbers checked while writing.

## Verdict: **PASS**, with eight deviations recorded and one clause reassigned.

---

## The requirement, verbatim

`spec/frontend/requirements.md:62-65`:

> **R19.** WHEN the user signs out, THEN the SPA SHALL clear in-memory token state and end the SAS session
> through the OIDC end-session endpoint with a `post_logout_redirect_uri` from the allowlist. **Revised
> (2026-10-10, Q5 resolved):** no `/oauth2/revoke` call — the SPA holds no refresh token to revoke for this
> client. The end-session navigation is served by SAS, not the SPA shell (R57).

| R19 clause | Owner | Verified by |
|---|---|---|
| "clear in-memory token state" | **task 35** | T35's AC1–AC6, 38 cases, unchanged by T36 |
| …including the ID token | **task 36** | AC9 `:1482` — three cases, incl. `signOut()` called alone |
| "end the SAS session through the OIDC end-session endpoint" | **task 36** | AC2 `:1181`, AC3 `:1221`, AC7 `:1421` |
| "with a `post_logout_redirect_uri` from the allowlist" | **task 36** | AC4 `:1254` — six cases |
| "no `/oauth2/revoke`" | **task 36**, by absence | AC6 `:1401` pins the parameter set at two; no revoke call exists in `src/` |
| "served by SAS, not the SPA shell (R57)" | **task 10** built it; T36 depends on it | AC16 `:1763` — `DENYLISTED_NAVIGATION_PATHS` contains `END_SESSION_PATH` |

## The clause T36 does **not** own, and `package.md` says it does

`spec/frontend/package.md:123-124` maps **two** test names to R19:

```
- `signOutRevokesTokensClearsMemoryAndEndsSasSession` → R19
- `signOutThenSignInRequiresCredentials`              → R19
```

Checked both:

- **`signOutRevokesTokensClearsMemoryAndEndsSasSession`** — this name has `Revokes` in it, which **Q5 removed
  from R19 on 2026-10-10**. `tasks.md:63-64` gives the real name,
  `signOutClearsMemoryAndEndsSasSession`, for both tasks 35 and 36. This is **drift in `package.md`, not in
  the code** — fourth instance, with `:171`'s mapping for struck-out task 34 and §11's unstruck Q5.
  Unchanged: **`spec/` is not this task's to edit.**
- **`signOutThenSignInRequiresCredentials`** — measured: it exists **nowhere** in `frontend/src`. It is
  `tasks.md:66`, **task 38**, *"After sign-out, sign-in shows the password step… **Blocked on Q15**."* So
  R19's third practical consequence — that a signed-out visitor is actually asked for credentials again — is
  **not verified by T36 and cannot be**, because the proof is an end-to-end assertion against a live SAS
  session and task 38 is blocked. T36 ends the session; whether SAS then demands a password is task 38's
  measurement.

This matters more than a naming nit: **the only end-to-end proof that R19 works is task 38's, and it is
blocked.** T36's 46 cases prove the SPA sends the right request. Nothing in this repository yet proves SAS
honours it.

## Locked decisions

| Decision | Clause | Held by |
|---|---|---|
| **L2** | interactive login is SAS-hosted | the hand-over is to SAS, as login is — AC3 `:1221` |
| **L3** | *"Access tokens are never written to storage, logs, or analytics"* (`design.md:13`) | AC8 `:1432` — fourteen surfaces swept in both new modules. **See D1.** |
| **L4** | nothing names a cause | AC6 `:1401` — no failure detail in the URL; no `catch` in either module |
| **L9** | single origin | AC3 `:1221` — derived from `location.origin`; no absolute host in production source |
| **L10 / R58** | no secret in the bundle | AC3's package-wide host scan `:1232` |
| **L11** | catalogue-only strings | AC11 `:1608` — neither module imports `useTranslation` or `t(` |
| **L12** | phase additivity | T36 adds two modules and consumes an existing route; no route added |
| **L14** | *"The route paths are listed verbatim in §4c"* | AC4 `:1278` — the target is in `PUBLIC_ROUTE_PATHS`. **See D2.** |
| **L16** | the sessionStorage carve-out is exhaustive | **task 22's** `sessionStorageHoldsOnlyTheL16Items`, green at four keys; T36 adds none |
| **L17 / R57** | service-worker boundaries | AC16 `:1763` |

## Deviations

| # | Deviation |
|---|---|
| **D1** | **L3 names *access tokens* only.** The ID token is held in memory and swept for fourteen surfaces because an ID token is a signed identity assertion and the same reasoning applies — but **the rule as written does not name it**, so this is an *extension by interpretation*, not compliance with a clause. It is the stricter reading; recorded because a reader checking L3 literally would find T36 doing more than it requires, and the next task should know it is a choice rather than an obligation. |
| **D2** | **AC4 is "checked against" the route table, not "derived from" it.** `routes.tsx` publishes `SIGN_IN_PATH`, `CALLBACK_PATH` and `HOME_PATH` but **no `SIGNED_OUT_PATH`** — the route is a bare literal at `routes.tsx:31`. `routes.tsx` is closed to T36, so `endSession.ts` declares the spelling privately and the suite asserts `PUBLIC_ROUTE_PATHS` contains it. One derivation step short. |
| **D3** | **AC10 permits two production modules, not one.** `pwa/navigationDenylist.ts:19` names the path as an R57 denylist entry, which is not a way of reaching the endpoint. Both named with reasons; a third fails. |
| **D4** | **AC2's source scan bans `open(` outright** in `endSession.ts`, which would also ban an unrelated legitimate `open`. None exists in 120 lines. |
| **D5** | **AC3's host scan is package-wide**, broader than the criterion, because L9/R58's hazard is about the bundle rather than this task's two files. |
| **D6** | **AC14's "exactly one place" scan excludes `auth/idToken.ts` by path**, since `export function retainIdToken(` matches a call-shaped needle. T35's `DEFINERS` draws the same distinction. Found by failing. |
| **D7** | **`endSession()` is not idempotent** (AC13 `:1671`), pinned rather than latched: a latch is the module-level mutable state AC15 forbids, traded for a second navigation that cannot happen after the page has left. |
| **D8** | **`CLIENT_ID` is unused**, though phase 2's dependency table listed it. The end-session endpoint takes no client id and AC6 freezes the parameter set at two. |

## What this task does **not** verify

Stated plainly, because a traceability matrix that lists only what passed is not a completeness claim:

- **Nothing calls `endSession()`.** AC1–AC16 are verified **by direct invocation only**. `PERMITTED_IMPORTERS`
  at `:1497` is empty and asserted empty. **OQ-T36-5**, seventh consecutive task.
- **No backend measurement.** AC4 pins the *shape* the SPA sends;
  `SasLoginIntegrationTest:475`/`:502` pin that the registered default is honoured and a non-allowlisted
  target refused. Neither is a runtime check of the other, and **OQ-T36-4** — whether SAS 1.5.1 honours a
  `post_logout_redirect_uri` with no `id_token_hint` — remains unmeasured; both backend tests supply one.
- **R19 end-to-end** is task 38's, blocked on Q15, as above.
- **The cross-tab hazard.** `crossTabRenewal.ts:95-111` adopts a sibling's renewal without asking whether
  this tab signed out. **T36 makes that producible in AC12's exact shape** — an access token with no ID token
  — which phase 2 had recorded as unreachable. **OQ-T35-3**, task 39's.

## Gates

| | |
|---|---|
| own suite | **84 passed (84)** |
| whole suite | **1662 passed / 1 failed of 1663** — the known `oxlint` host case |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| mutation | **33/33 killed · 0 survivors · 0 broken · 0 canaries · exit 0** |

**Phase 12 complete. Verdict: PASS.**
