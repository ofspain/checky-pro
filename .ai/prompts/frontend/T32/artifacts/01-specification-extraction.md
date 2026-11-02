<!-- MODEL: Claude Opus 5 — Phase 1 (Specification Extraction) -->

# frontend · T32 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T32 — `email_verified` is not account state |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

## Business rules

| ID | One sentence |
|---|---|
| **R20** | Profile data comes from `/userinfo` and account status from `GET /accounts/me`, and **PII and verification state are never read from the access token** — T32 is the third clause. |
| **R21** | A 401 runs at most one renewal per tab, shared across parallel 401s and sibling tabs — constrains T32 only in that nothing here may inspect a token to decide a renewal. |
| **R3–R5** | The email-verification **link** flow: a valid token shows a success state, any failure shows one uniform message with a neutral resend, and the resend acknowledgement is identical regardless of outcome. **Not T32's to build** (tasks 48–51) but it is the flow T32's prohibition must not be confused with. |
| **R2** | Registration shows the same "check your email" screen whether or not the email already existed — the other place verification is spoken about without any claim being involved. |

### The standing rule, which is the authoritative form of this task

`spec/frontend/agents.md:37-38`, under **identity rules (LOCKED)**:

> The requested scopes are `openid profile email`. Account status is read from `GET /accounts/me`. The
> access-token `email_verified` claim is never treated as account verification state.

`agents.md` is authoritative and must never be restated or violated, so T32 is not inventing a rule — it is
the task that **instruments** one.

### Why the rule exists, from the contract rather than from reasoning

`contracts/api/token-claims.md:51` (Path 1, interactive login):

> `email_verified` — `true` if and only if the `email` OIDC scope was authorized for this specific request —
> reflects **scope authorization**, not literally "has this account verified its email." … a real,
> currently-issued token requesting only `scope=openid` carries `email_verified=false` — confirmed directly
> against a real token, not assumed.

This is a sharper hazard than T31's. `status` on a token would at least be a **stale** answer to the right
question. `email_verified` answers a **different question entirely** — what the client asked for at
`/oauth2/authorize` — while carrying the name of the question a verification surface would want to ask. For
this client, which requests `email` on every authorize (`src/auth/authorizeRequest.ts:29`), it would read
`true` for an account that has never verified anything. A reader who trusts the name gets a confident wrong
answer in the dangerous direction: **unverified shown as verified.**

`token-claims.md:56,67` also records it **absent** on `client_credentials` tokens, and `:31` records it as
the single exception to "no PII in an access token".

## Locked decisions that constrain this task

| ID | Constraint on T32 |
|---|---|
| **L3** | Tokens live in memory only — nothing T32 writes may put a token or a claim anywhere else. |
| **L6 / L10** | The generated client is the only transport; verification state, when read, is read through it. |
| **L11** | Every visitor-facing string is catalogue-owned; a verification message is copy, not a literal. |
| **L14** | The route set under `/app` is fixed and listed in `design.md` §4c — T32 adds no route. |
| **L16** | The only `sessionStorage` carve-out is `state`, `nonce`, `code_verifier`. A cached verification flag is not on that list. |
| **L18** | Single-flight renewal and cross-tab coherence — untouched by T32. |
| **L4** (enumeration safety) | Copy and redirects are identical across account states; a verification surface must not become a state oracle. |

## Files involved

**To read and extend:**

| File | Why |
|---|---|
| `src/auth/idTokenNonce.ts` | the only JWT-decoding file; the existing claim scans are bounded to it |
| `src/profile/readAccount.ts` | the **truthful** source: `emailVerified: boolean` and `PENDING_VERIFICATION` |
| `src/auth/authorizeRequest.ts` | `REQUESTED_SCOPES`, which is what the claim actually reports on |
| `src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx` | holds T30's `FORBIDDEN_TOKEN_CLAIM` (`:190`) and T31's `T31_FORBIDDEN_TOKEN_CLAIM` (`:1711`), both bounded to the decoding file |
| `src/i18n/catalogue.ts` | has **no** key matching `verif` today |

**New, expected by the spec:** the named test `emailVerifiedClaimIsNotAccountState`
(`spec/frontend/package.md:180` → R20). No file of that name exists.

**Production code:** **I expect none.** The rule is a prohibition, the truthful source already exists (T31),
and there is no commissioned consumer. Phase 2 must justify any production file rather than assume one.

## Dependencies

| | |
|---|---|
| Contracts | `contracts/api/token-claims.md` (the claim's semantics, all three paths); `contracts/api/auth.yaml` → `AccountResponse.emailVerified`, `AccountStatus.PENDING_VERIFICATION` |
| Modules | `profile/readAccount.ts`, `auth/idTokenNonce.ts`, `auth/accessToken.ts`, `auth/authorizeRequest.ts`, `i18n/catalogue.ts` |
| Config keys | none |
| Named tests | `emailVerifiedClaimIsNotAccountState` (this task); `profileIsReadFromUserinfoAndAccountStatusFromMe`, `generatedClientCompiles`, `authorizeRequestsExactlyTheDeclaredScopes` (existing, not to be broken) |

## Acceptance criteria

| | Criterion | Requirement |
|---|---|---|
| **AC1** | No module in the production tree reads `email_verified` off a token — re-asserted, not inherited, with the decoding-file bound re-proven as T31 does. | R20, `agents.md:38` |
| **AC2** | The prohibition is **tree-wide on consumers**, not bounded to the decoding file: no component, route, store, hook or copy key is keyed on a token-borne verification flag. This is the half T30 and T31 do not hold, and Kimi's T31 A3 is the precedent — a bounded scan proves nothing about the next caller. | R20 |
| **AC3** | The **truthful sources are named and pinned**: `AccountResponse.emailVerified` and `PENDING_VERIFICATION`, both reachable only through `GET /accounts/me`. A positive pin, so a future surface finds the right source recorded rather than inferring one. | R20, R3–R5 |
| **AC4** | The claim's real semantics are pinned against the contract: `email_verified` reports **scope authorization**, the SPA requests `openid profile email`, and nothing in the tree treats the claim as state. If `token-claims.md` or `REQUESTED_SCOPES` changes, this must fail. | `token-claims.md:51`, `authorizeRequest.ts:29` |
| **AC5** | The verification **link** flow (R3–R5, tasks 48–51) is explicitly out of scope and is not blocked by this prohibition: a token in a verification URL is not an access-token claim, and the instrument must not fire on it. An over-broad scan here would be an instrument defect that lands on someone else. | R3–R5 |
| **AC6** | Every instrument has a dirty-proof, refuses an empty tree, and is bounded as both an upper and a lower bound. | `agents.md`, package convention |
| **AC7** | No `sessionStorage`, `localStorage`, console or DOM surface holds a verification flag; the seven-surface sweep covers it. | L3, L16, L18 |

## Open questions

- **OQ-T32-1 (the real gap, and the reason this task is at risk of being vacuous).** **No task commissions a
  signed-in "your email is not verified" surface.** Tasks 48–51 build the *link* flow — a visitor clicks a
  link, with no session and no claim. Nothing in `tasks.md` reads `PENDING_VERIFICATION` or
  `AccountResponse.emailVerified` to show a signed-in user that their email is unverified, and a search of
  `tasks.md` for `PENDING_VERIFICATION`, `unverified` and `verified` returns only tasks 32, 48 and 153. So
  T32 forbids a wrong source for a UI no one is building. **This is logged, not answered**: the owner decides
  whether a signed-in verification surface is commissioned, and T32 proceeds either way as a standing
  prohibition plus a positive pin, since the prohibition is cheapest to install before the consumer exists.
- **OQ-T32-2.** `/userinfo` also carries `email_verified` for interactive logins. R20's clause names the
  **access token**, and `agents.md:38` says "the access-token `email_verified` claim". Reading the rule
  literally, the `/userinfo` copy is *permitted* as profile data — and T30 measured the live server returning
  only `sub` anyway (the O6 blocker), so it is unavailable in practice. **Phase 2 must freeze a reading**: I
  recommend that verification STATE comes from `/accounts/me` regardless of which transport carries a
  same-named field, because the hazard is the name, not the channel.
- **Inherited and not blocking:** OQ-T31-3 (nothing owns acting on a non-ACTIVE account) and **OQ-T31-4**
  (`type-fest`'s `/// <reference lib="esnext"/>` lifts the declared ES2022 ceiling for all of `src/`).

**Phase 1 complete.** Seven acceptance criteria, two open questions, no design and no code.
