<!-- MODEL: Claude Opus 5 — Phase 0 (Repository Understanding) -->

# frontend · T32 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T32 — `email_verified` is not account state |
| **Produces** | `artifacts/00-repository-understanding.md` |
| **Head** | `7d78d404` (T31 CLOSED PASS) |

**Task statement (verbatim, `spec/frontend/tasks.md:60`):**
> **`email_verified` is not account state.** The access-token `email_verified` claim never drives verification
> UI. Verifies `emailVerifiedClaimIsNotAccountState`. Cites R20.

No code was written in this phase.

---

## 1. Architecture summary

This is a React 19.3.1 + TypeScript SPA built with Vite, served under the `/app` basename behind a
single-origin edge path map (L9). There is no persistence layer, no outbox and no event bus in this service —
those words in the phase template belong to the Java services. The frontend's equivalents are:

| Concern | Where it lives |
|---|---|
| **Transport** | one generated client per contract — `createAuthApi` / `createOidcApi` in `src/api/client.ts` (L6, L10) |
| **Session** | `src/api/sessionBoundary.ts` — attaches the bearer, owns the 401, single-flight renewal (R21, L18) |
| **Token custody** | `src/auth/accessToken.ts` — in memory only, never storage (L3) |
| **Storage carve-out** | `state`, `nonce`, `code_verifier` in `sessionStorage` and nothing else (L16) |
| **Copy** | `src/i18n/catalogue.ts` owns every visitor-facing string (L11) |
| **Routes** | `src/app/routes.tsx`, the fixed set under `/app` (L14) |
| **Account reads** | `src/profile/readProfile.ts` (T30, `/userinfo`) and `src/profile/readAccount.ts` (T31, `/accounts/me`) |

Security posture relevant to this task: the SPA is a public OIDC client with PKCE and no client secret (L1),
interactive login is SAS-hosted (L2), and the access token is a bearer the SPA holds but **does not inspect**.

## 2. Existing code this task touches

**Already exists:**

| File | What it is | Relevance to T32 |
|---|---|---|
| `src/auth/idTokenNonce.ts` | **the only file in the tree that decodes a JWT** — `atob` + `TextDecoder`, and it reads `nonce` and nothing else | the whole surface on which a claim could be read |
| `src/auth/accessToken.ts` | in-memory custody of the access token | the token T32 says must not be inspected |
| `src/auth/authorizeRequest.ts:29` | `REQUESTED_SCOPES = ['openid', 'profile', 'email']` | decides what `email_verified` will even say |
| `src/profile/readAccount.ts` | T31's read of `GET /accounts/me`, returning `emailVerified: boolean` and `status` | **the truthful source** T32 must point at |
| `src/profile/readProfile.ts` | T30's read of `/userinfo` | the other non-token source |
| `src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx` | T30 + T31, 107 cases | already holds two claim scans, below |
| `src/i18n/catalogue.ts` | the copy catalogue | **contains no verification key at all today** |

**New in T32:** the named test `emailVerifiedClaimIsNotAccountState` has **no file yet** — `find` over
`src/` returns nothing for that name. Whether T32 produces a new test file or extends the shared one is a
phase 1/2 decision, not this phase's.

**What T30 and T31 already prove, and what they do not.** Both already forbid the claim by name:

```ts
const FORBIDDEN_TOKEN_CLAIM =                      // T30, :190
  '\\bemail_verified\\b|\\bgiven_name\\b|…|\\bsub\\b';
const T31_FORBIDDEN_TOKEN_CLAIM = '\\bstatus\\b|\\bemailVerified\\b';   // T31, :1711
```

Both scans are **bounded to the one file that decodes a token**, and each re-asserts that bound rather than
inheriting it. So "no module reads `email_verified` off a token" is already held. What is **not** held, and
is what T32's title actually says, is the second half: that no **UI** is driven by it. That is a claim about
consumers, and today there are no consumers at all — which is both why the task is cheap and why it is easy
to make it vacuous.

## 3. Established patterns to follow

- **Absence is asserted, never assumed.** Every scan in this package has (a) a bound on the file set, (b) a
  dirty-proof that it reports when the violation is synthesised, and (c) a refusal to report clean on an empty
  tree (`refuseEmpty`, T22-D14).
- **An allowlist is both an upper and a lower bound.** The permitted set must be proven load-bearing, not just
  permitted — T31's phase 11 A2 added the carrier form of this.
- **Comment-stripping with string literals KEPT.** A package whose own headers explain the forbidden thing a
  dozen times cannot be scanned naively; `strip()` removes comments and preserves literals.
- **`satisfies` + `AssertNever<Exclude<…>>`** as two-directional compile-time pins on a runtime list.
- **Failure types are closed sets**, and their messages are internal — never catalogue copy (L11).
- **The mutation harness asserts its own baseline** (by failing-file **name**, Kimi A7), its own anchors
  (exactly once), and fails the run on a `BROKEN` mutant as well as a survivor.

## 4. Testing conventions

Vitest 5.0.5 with jsdom 30.1.3. The phase template's JUnit / Testcontainers / fixed-`Clock` / ArchUnit
paragraph is the Java convention and does not apply here; the frontend equivalents are:

| | |
|---|---|
| Runner | `npx -y pnpm@12.14.0 exec vitest run` — **bare `pnpm` is not on this host** |
| Verdict | the **exit code**, never the pass count |
| Static gates | `tsc --noEmit` and `oxlint src`, both expected to exit 0 |
| Named tests | one file per named test from `package.md` §8; T30 and T31 share one file by ownership blocks |
| Mutation | a per-task Python harness in the task's `artifacts/`, which reverts with `git checkout -- src/` — **tests must be committed before it runs** |
| Known failure | `src/api/lintRejectsHandWrittenBackendFetch.test.ts` fails on this host; `oxlint@1.87.0` prints no summary line. Pre-existing, environmental, unrelated |

## 5. Known gaps and unknowns

**The fact that makes this task worth doing, from `contracts/api/token-claims.md:51`:**

> `email_verified` — `true` if and only if the `email` OIDC scope was authorized for this specific request —
> reflects **scope authorization**, not literally "has this account verified its email." … a real,
> currently-issued token requesting only `scope=openid` carries `email_verified=false` — confirmed directly
> against a real token, not assumed.

So the claim is not merely a stale copy of account state, as `status` was for T31 — **it does not mean what
its name says at all.** It answers a question about the request, and the SPA requests `email` on every
authorize (`authorizeRequest.ts:29`), so for this client it would read `true` whenever the scope is granted,
including for an account that has never verified anything. Same name, different question.

The truthful sources both exist: `AccountResponse.emailVerified` and the `PENDING_VERIFICATION` status, both
read by T31.

**Open, and not to be speculated on here:**

- **There is no verification UI, and no copy for one.** `catalogue.ts` has no key matching `verif`, and the
  tree has three components (`AppShell`, `RateLimitNotice`, `SignInScreen`, `CallbackScreen`). So "never
  drives verification UI" has no present consumer. **The central risk of this task is a vacuous assertion**,
  and phase 1 has to decide what a non-vacuous form looks like with no UI to point at. I do not know yet which
  task owns building that UI.
- **`/userinfo` also carries `email_verified`** for interactive logins. T30 measured the live server returning
  **only `sub`** (the O6 blocker in `spec/auth-service/design.md`), so that field is unavailable in practice
  today. Whether T32's claim covers the `/userinfo` copy of the field as well as the token's is a phase 1
  reading of R20, not mine to settle here.
- **`token-claims.md` Path 1 vs Path 3.** The claim is present on paths 1 and 3 and absent on path 2
  (`client_credentials`). The SPA only ever holds a path-1 token. Whether the scan should care about the other
  paths: I do not know, and it may be out of scope.
- **Inherited from T31, still open:** OQ-T31-3 (nothing owns *acting* on a non-ACTIVE account) and
  **OQ-T31-4** (`type-fest` declares `/// <reference lib="esnext"/>` via `workbox-build` → `tempy`, lifting
  the declared ES2022 ceiling for all of `src/`). Neither blocks T32.

**Phase 0 complete.** No code written, no requirements extracted, no design proposed.
