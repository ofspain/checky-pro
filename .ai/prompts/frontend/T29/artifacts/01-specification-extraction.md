# frontend · T29 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T29 — Uniform sign-in failure |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

---

## Business Rules

- **R10** (scoped) — when the SAS login fails **for any reason**, the SPA shows the same uniform sign-in
  failure message, with copy identical to a wrong password, and never says enrollment is required.
- **R30** (scoped) — when an identity-related screen the SPA renders shows an error, the **copy, redirect
  target and visual treatment** are identical across unknown or missing, locked, suspended and deleted
  accounts, and across bad, expired and already-used tokens. *"The requirement covers SPA-observable copy,
  redirects, and styling only. Server response timing is out of SPA scope."*
- **R8** (boundary) — a callback with no usable or mismatched `state` is discarded and returns the visitor to
  the sign-in start with neutral copy. T17/T18 own it; T29 rides on it.
- **R28** (boundary, not contradicted) — unauthenticated routes redirect to the sign-in start. Task 41 owns
  the redirect; T29 must not assert a redirect target that would contradict it.

## Locked Decisions

- **L4** — *"Enumeration-safe UI for every unauthenticated or low-trust caller, without carve-out there. Copy,
  redirects, styling, **and timing** are identical across unknown or missing, locked, suspended, and deleted
  accounts and across bad, expired, and used tokens, on every public or self-service surface. No lockout
  timers."* The one carve-out (`adminGetAccount`, R27, D-033) is authenticated and ADMIN-only and must not be
  widened by accident.
- **L2** — interactive login is SAS-hosted, so the sentence a refused visitor reads on `/login` is the
  server's, not this bundle's.
- **L11** — live regions for async results; catalogue-only strings.
- **L1, L3, L14, L16, L17** — PKCE with validated `state`/`nonce`; tokens in memory; the route set under
  `/app`; the sessionStorage carve-out; `/login` never cached.
- **`agents.md:40-44`** — authoritative, and the **reconciliation** of the one apparent conflict in the
  package: *"Copy, redirect targets, visual error styling, and response timing are identical for unknown,
  missing, locked, suspended, and deleted accounts… No client-side account-state gating. No lockout timers.
  Uniform copy only… **The SPA cannot control server response timing. The timing requirement applies to the
  SPA's own rendering only.**"*

**The timing clause is T29's one piece of genuinely new ground.** L4 says timing is identical; R30 says server
timing is out of SPA scope; `agents.md:44` resolves it by keeping the obligation and moving its subject — the
SPA's **own rendering** must not be timed differently for different causes. Nothing in T16–T28 asserts that,
and it is assertable: a cause-keyed `setTimeout`, a delay before a notice, or a lockout countdown would each
breach it while leaving every copy and redirect assertion in the package green.

## Files involved

**To read, and not to modify:**

| File | Why |
|---|---|
| `src/auth/SignInScreen.tsx` | the three-state machine and the single live region; `:41-47` is the local failure path |
| `src/auth/CallbackScreen.tsx` | the one `discard` closure and its five call sites |
| `src/auth/signInNotice.ts` | the one-member union the discard carries |
| `src/i18n/catalogue.ts` | the six `signIn.*` keys; `failed` and `discarded` are the two failure sentences |
| `src/app/routes.tsx` | `SIGN_IN_PATH`, the public/authenticated split, the two routes with an element |
| `src/api/sessionBoundary.ts`, `src/api/rateLimitNotice.ts` | the two identity-adjacent error surfaces that exist but are **unwired** |
| `services/auth/.../AccountUserDetailsService.java`, `.../TotpAuthenticationProvider.java` | the measured premise: five account states, one exception, one message |

**New file the spec expects:**

- `src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx` — the named test from `package.md`
  §8.

**No production file is expected to change.** The same reading held for T25, T26, T27 and T28, and in all four
cases it was correct; it must still be re-checked in Phase 6 rather than assumed.

## Dependencies

`SignInScreen`, `CallbackScreen`, `setSignInNotice`/`takeSignInNotice`, `en`, `appRoutes`, `APP_BASENAME`,
`SIGN_IN_PATH`, `PUBLIC_ROUTE_PATHS`, `AUTHENTICATED_ROUTE_PATHS`, `AUTHORIZE_STORAGE_KEYS`, `accessToken`,
`createI18n()`; vitest, jsdom, `act`, `createRoot`, `createMemoryRouter`, `node:fs/promises`, `vi.stubGlobal`,
`vi.useFakeTimers`. All built.

**No contracts.** A login failure reaches no API the SPA calls — it re-renders SAS's own `/login`. The
`contracts/api/*.yaml` line in the prompt header is the generated default and does not apply.

**No config keys.**

## Acceptance Criteria

- **AC1 (R10, R30)** — every SAS failure the SPA can observe ends in **one** rendered sentence, from the
  catalogue, naming no cause.
- **AC2 (R30 copy)** — the copy is identical across the five account states. Since the states are not
  observable from the SPA (Phase 0 §2), this is satisfied by **absence**: no production module holds
  account-state vocabulary on a sign-in-scoped surface, and no catalogue string does.
- **AC3 (R30 redirect target)** — every observable sign-in failure redirects to the same place,
  `/${SIGN_IN_PATH}`, with `historyAction` `REPLACE`, taken from the owning module rather than a literal.
- **AC4 (R30 visual treatment)** — identical, and **trivially so**: no styling exists. Must be declared, not
  hidden (task 173 owns making it non-trivial).
- **AC5 (L4 timing, `agents.md:44`)** — **the SPA's own rendering is not timed differently for different
  causes.** No `setTimeout`, `setInterval`, `requestIdleCallback`, `requestAnimationFrame` or awaited delay
  stands between a failure and its sentence; no lockout timer or countdown exists anywhere in the bundle. To
  be proven both structurally (a source scan over the identity path) and behaviourally (fake timers: the
  notice is present without advancing the clock).
- **AC6 (L4)** — nothing gates what a failed sign-in renders on an account state or a role. Inherited from
  T28's instrument, cited and re-run rather than rewritten.
- **AC7 (R10)** — the two failure sentences `signIn.failed` and `signIn.discarded` remain the **only** two,
  and the `signIn.*` key set is exactly the six that exist.
- **AC8 (L4, scope)** — the account-state vocabulary is forbidden **on the sign-in-scoped surface only**, and
  permitted elsewhere, because tasks 81–89 are specified to write `adminSuspendAccount`, `adminUnlockAccount`,
  `assignRole` and `getEffectiveRoles` copy. T28's split pattern is the mechanism; T29 extends its
  *account-state* half with the five state words and must hold the split from both directions.
- **AC9 (vacuity, T22-D14)** — every instrument is shown to report DIRTY first, and a scan over an empty tree
  is refused rather than reported clean.

## Tests required

**Named test (`package.md` §8):** `identityErrorsIndistinguishableAcrossAccountStates` → R30.

**Boundary tests implied, beyond the named one:**

1. The five account states, driven as the only axis that varies, produce one outcome — as far as the SPA can
   be made to observe them at all. Phase 2 must settle *how*, since the backend refuses all five before the
   SPA is involved.
2. Fake timers: the failure sentence is rendered without advancing the clock, for every cause (AC5's
   behavioural half).
3. A source scan for timer calls on the identity path, with a synthetic control (AC5's structural half).
4. A catalogue scan for the five state words on sign-in-scoped keys, with the permitted-elsewhere control
   (AC8).
5. The `signIn.*` key-set pin, cited to T28 rather than duplicated (AC7).

**Already owned elsewhere, to cite and not re-prove:** the four failure kinds collapsing to one struct (T28),
the error code/description/URI not surviving the discard (T28), `state` handling (T17/T18), the exchange
(T20), leaks (T22), deletions (T23), the storage inventory (T26), MFA absence (T27), the `/login` denylist
(R57's task).

## Open Questions

- **OQ1 (the real one, and it is a scoping blocker for Phase 4, not for Phase 2).**
  `identityErrorsIndistinguishableAcrossAccountStates` is cited by **three** tasks: **29** (this one, under
  *"Phase 1 — Auth & account (gatekeeper): OIDC and session"*), **58** (*"Identical across account states"*,
  under *"Phase 1 — Auth & account: enumeration safety"*) and **136** (*"Enumeration gate"*, under the Phase 1
  gates). One file name, three owners. `package.md` §8 lists the test once.

  **Recommended reading, from the section headings and from what exists:** T29 owns the **sign-in and callback
  axis** — the only identity screens that have an element today. T58 owns the account-state rendering on the
  identity screens it will have by then (registration, verify, password reset), and T136 is a **gate** that
  re-runs the property across every Phase 1 screen once they all exist. On that reading T29 creates the file
  and T58/T136 extend it, which is the pattern T24/T25 already used for
  `returnTargetOutsideAllowlistFallsBackToHome` (T24 created it at 67 cases, T25 grew it to 184). **This
  should be frozen explicitly at the Phase 4 gate**, because the alternative — three tasks writing the same
  file without a stated split — is how one of them silently overwrites another's cases.

- **OQ2 (not a blocker; a stated limitation).** The composition root does not exist. `mountShell.tsx:20-28`
  never calls `createSessionBoundary` or `createRateLimitNotice`, so `onRenewalFailed` has no production caller
  and `<RateLimitNotice>` is never mounted. Any part of R30 that depends on what the SPA renders when a session
  cannot be resumed has **no production code to verify**, and T29 must declare that rather than assert around
  it. Carried from Phase 0 §5.2; the root is unowned by any task.

- **OQ3 (not a blocker; inherited).** `oidcRoundTripEndToEnd` (task 134) has no suite, so no test in this
  repository reads the page a refused visitor actually sees. R10's *"copy identical to a wrong password"* is
  therefore verifiable on the client path only — the same limitation T28 declared as T28-D1. **Not reopened
  here**; recorded so Phase 4 can carry it forward as T29's equivalent deviation.

- **No `package.md` §11 question blocks this task.** Q10 (the L5 consequence) and Q13 (the privileged
  bootstrap) are both closed, and Q14 fixes L4's one carve-out as authenticated and ADMIN-only.

---

**Phase 1 complete.** Ready for Phase 2 — task implementation brief.
