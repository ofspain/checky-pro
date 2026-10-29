# frontend · T28 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T28 — Unenrolled privileged refusal copy |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

---

## Scoped requirements

### R10 (`requirements.md:29-33`) — the task's own requirement

> *WHEN the SAS login fails for any reason, including a MERCHANT or ADMIN account without a confirmed
> enrollment, THEN the SPA SHALL show the same uniform sign-in failure message, with copy identical to a wrong
> password. The SPA SHALL NOT tell the user that enrollment is required.*

Two obligations, and they are not the same kind of claim:

1. **Uniformity** — one message for every failure, identical to a wrong password's.
2. **Silence** — the SPA never says enrollment is required.

Phase 0 measured that obligation (1)'s literal subject is **mostly unreachable from the SPA**: the refusal is
a `BadCredentialsException` at the login step, so the visitor stays on SAS's page and the SPA renders nothing.
The one path by which a refusal *can* present itself to the SPA is an **authorization error response** at
`/app/callback?error=…`, which `CallbackScreen.tsx:101-104` already routes into the single `discard` closure —
and whose comment already names this task. Obligation (2) is **fully** the SPA's and is a structural claim
over the bundle.

R10 also carries its resolution: *"**Resolved (2026-10-08, Q13):** the bootstrap path is the normal USER-level
self-service MFA wizard (auth T19, built) — an account enrolls before an admin grants it MERCHANT/ADMIN, now
enforced server-side (`auth-decisions.md` D-031)."* That clause is **task 73's**, not this task's — see the
boundary below.

### R30 (`requirements.md:104-107`) — cited, not re-proved

> *WHEN an identity-related screen the SPA renders shows an error, THEN the copy, redirect target, and visual
> treatment SHALL be identical across unknown or missing, locked, suspended, and deleted accounts, and across
> bad, expired, and already-used tokens. The requirement covers SPA-observable copy, redirects, and styling
> only. Server response timing is out of SPA scope.*

**Tasks 58 and 59 own this**, by name: task 58 → `identityErrorsIndistinguishableAcrossAccountStates`, task 59
→ `identityErrorsIdenticalAcrossTokenStates`. T28 cites R30 as the sibling rule its own uniformity claim must
not contradict, and does not enumerate account or token states.

Note R30's own last sentence, which bounds every timing argument: **the SPA cannot control server response
timing**, and `agents.md:44` repeats it — *"The timing requirement applies to the SPA's own rendering only."*

## Scoped LOCKED decisions

### L5 (`design.md:27-33`) — the authority for the refusal

> *SAS refuses authorization to MERCHANT or ADMIN accounts without a confirmed enrollment, with the same error
> as a wrong password (backend T20). It does not enroll them.*

"It does not enroll them" is the clause that makes obligation (2) absolute: there is **no
enrollment-during-login path to build**, so any such path in the SPA is a defect rather than an unfinished
feature. The rest of L5 describes the bootstrap — enroll while holding only `USER`, then an admin grants the
role, enforced in `RoleService.assignRole` — which is **task 73's** subject.

### L4 (`design.md:21-26`) — the uniformity authority

> *Enumeration-safe UI for every unauthenticated or low-trust caller, without carve-out there. Copy,
> redirects, styling, and timing are identical… on every public or self-service surface. No lockout timers.*

Its one carve-out is irrelevant here and must not be widened by accident: the ADMIN-only `adminGetAccount`
status view (R27, Q14, D-033) is *"a deliberate disclosure to a trusted operator role, not the
anonymous-probing scenario this rule exists to close."* `/app/sign-in` and `/app/callback` are public, so no
carve-out applies.

### L2 — why the refusal's copy is not the SPA's

Interactive login is SAS-hosted. The sentence a refused visitor reads is rendered by the server. T28 may
verify that the SPA **cannot** render it and **cannot** answer `/login`; it may not assert anything about its
wording.

### Also binding, cited: L1, L3, L14, L16, L17

The callback machinery T28 rides on, unchanged from T27.

### `agents.md:40-44` — authoritative standing rule

> *Copy, redirect targets, visual error styling, and response timing are identical for unknown, missing,
> locked, suspended, and deleted accounts, and for bad, expired, and already-used tokens… **No client-side
> account-state gating. No lockout timers. Uniform copy only.** Confirmed by the author.*

"No client-side account-state gating" is a second structural claim T28 can verify directly: no production
module may branch on an account state or a role to decide what a failed sign-in shows.

## Named test

`sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy` → R10 (`package.md:114`).
**No file of that name exists** (measured). It is T28's to create.

## Task boundary — R10 is shared with task 73, and the split is clean

| | Task 28 (this) | Task 73 |
|---|---|---|
| Named test | `sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy` | `privilegedBootstrapPathEnrollsWithoutEnrollmentHint` |
| Subject | the **refusal**: what a failed privileged sign-in shows | the **remedy**: the bootstrap path working, with no enrollment hint |
| Cites | R10, L5 | R10, L5 |
| Depends on | nothing unbuilt | the MFA wizard (tasks 66–72), none of which exists |

T28 must not build or assert the bootstrap path, the enrollment wizard, or `account/mfa` — that route is a
path with no element and tasks 56–60 and 66–73 own what goes there. The two tasks meet at one point: **neither
may let an enrollment hint exist**, and T28 proves that for the bundle as it stands today.

## Dependencies — all built

`CallbackScreen` (`:81-104` error branch, `:89-99` the single `discard` closure), `SignInScreen`,
`setSignInNotice`/`takeSignInNotice`, `en` (the catalogue), `DENYLISTED_NAVIGATION_PATHS`, `appRoutes`,
`AUTHORIZE_STORAGE_KEYS`, `createI18n()`. No config keys. No contracts: **the refusal never reaches an API
the SPA calls**, so `auth.yaml` is out of scope for it.

## Contract check

`contracts/api/auth.yaml` describes the TOTP self-service endpoints (L5's bootstrap). None is called on the
sign-in path, and T28 calls none of them. `contracts/api/token-claims.md` remains known-wrong about `email`
and `name` — out of scope here, no claim in this task depends on it.

## What is verifiable, and what is not

**Verifiable (T28's):**
1. Every way a refusal can reach the SPA ends in the one `discard` closure, with `signIn.discarded`, at
   `/app/sign-in`, with a replaced history entry.
2. An `error=` authorization response is **indistinguishable in outcome** from a mismatched `state`, a refused
   exchange and a bad `nonce` — including when the error code itself names the cause
   (`access_denied`, `invalid_request`, a vendor code hinting at enrollment).
3. The error **code and description are never surfaced** anywhere: not rendered, not stored, not in the
   router location, not logged.
4. No production module names enrollment, nor gates on an account state or a role to decide failure copy.
5. No catalogue string mentions enrollment, MFA, an authenticator or a code.
6. `/login` can never be answered by the SPA or its worker (cited).

**Not verifiable here, and must be declared rather than implied:**
- **SAS's own refusal copy.** Server-rendered (L2). The SPA cannot read it, and T28 asserts nothing about it.
- **Response timing.** R30 and `agents.md:44` both exclude it from SPA scope explicitly.
- **That the backend's failure modes really are identical.** That is `services/auth/`'s own test's claim;
  Phase 0 cites `TotpAuthenticationProvider`'s identical `BadCredentialsException` as the measured premise.
- **A real browser round trip.** `oidcRoundTripEndToEnd`, no suite, no owning task.

## Open questions for Phase 2

1. **Is obligation (1) satisfiable enough to be worth a file?** Phase 1's reading: yes, via the `error=` path,
   which is the one place a refusal becomes SPA-observable and which no existing case tests for uniformity.
2. **Does T28 need any production change?** Expected no — `CallbackScreen:101-104` already exists and already
   cites task 28. To be settled in Phase 2.
3. **Visual treatment.** R30 names "visual error styling" as part of uniformity. There is **no styling in the
   SPA at all** (measured 2026-10-29: no CSS file, no Tailwind config, no `className` in any production
   component; tasks 173/174 added for it). So "identical visual treatment" is trivially true today and must be
   recorded as such rather than claimed as verified.

---

**Phase 1 complete.**
