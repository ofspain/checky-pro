# frontend · T28 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T28 — Unenrolled privileged refusal copy |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

> This brief is the only specification the implementation and review phases use. It is frozen at Phase 4.

---

## Task

Build `src/auth/sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx`, the named test for
R10. One new test file; no production change anticipated.

## Purpose

R10 forbids the SPA from telling a visitor that enrollment is required, and requires every sign-in failure to
read the same. The measured architecture makes those two obligations very different in kind.

**Measured premise (Phase 0, read from `services/auth/`).** The refusal is a **login failure, not an
authorization error**. `TotpAuthenticationProvider:100-101` throws `BadCredentialsException("Bad
credentials")` for "MERCHANT/ADMIN with no confirmed enrollment", and the identical exception with the
identical message for an unknown user, a wrong password and a bad or missing TOTP code. Its own comment
records that a distinct "please enroll" signal **was considered and rejected**, because reachable only after a
correct password it would work as a password-correctness oracle. So the refused visitor stays on the
SAS-hosted login page and **never reaches `/app/callback`**.

That makes the sentence the task names — "the wrong-password copy" — **SAS's own page copy**, which L2 puts on
the server. T28 cannot assert its wording, and must not pretend to.

**So what is this file for?** Two things, and the second is the one with teeth.

1. **The one path by which a refusal CAN become SPA-observable**: an authorization error response at
   `/app/callback?error=…`. `CallbackScreen.tsx:101-104` already routes it into the single `discard` closure,
   and its comment already names this task — *"task 28 maps even a SAS refusal onto the generic wrong-password
   copy."* Five existing files mention `error=`, but every one uses it merely as a way to reach the discard;
   **none asserts that the outcome is indistinguishable from the other failures.** That claim is unowned, and
   it is R10's.
2. **Silence, proven structurally over the bundle.** No enrollment copy, no enrollment-during-login path, no
   client-side gating on an account state or a role. This is absolute — L5 says SAS "does not enroll them", so
   there is no feature pending and any such surface is a defect.

**The failure mode to guard against is a helpful addition.** "Your account needs two-factor authentication
set up before you can sign in" is the single most natural thing a developer would write when they see a
privileged account bounce, and it breaks R10, L4 and the backend's deliberate uniformity in one sentence.

## Scope

**In**
- One new test file.
- **Outcome-equivalence**: an `error=` callback produces an outcome identical to a mismatched `state`, a
  refused exchange and a bad `nonce` — compared as one struct, not field by field.
- Error codes that **name the cause**, including a vendor code hinting at enrollment, changing nothing.
- The error code and description **never surfacing**: not rendered, not stored, not in the router location,
  not logged.
- **Structural silence** over every production module: no enrollment vocabulary, no account-state or role
  gating of failure copy.
- A **catalogue scan** for enrollment-shaped copy across every user-facing string.
- `/login` unanswerable by the SPA or its worker — cited from the owning module.
- Controls that make every instrument fail.

**Out**
- **SAS's refusal copy.** Server-rendered (L2); unreadable and unasserted here.
- **The bootstrap path and the enrollment wizard** — `privilegedBootstrapPathEnrollsWithoutEnrollmentHint` is
  **task 73's**, and depends on tasks 66–72, none of which exists. T28 shares R10 with it and stops at the
  refusal.
- **Enumerating account and token states** — tasks 58 and 59 own R30's two lists.
- **Response timing.** R30 and `agents.md:44` both put it out of SPA scope in terms.
- Re-proving T16's authorize request, T17/T18's state checks, T19's nonce, T20's exchange, T21's in-memory
  token, T22's leak sweep, T23's deletions, T24/T25's return target, T26's inventory, T27's MFA absence.
  Each is cited where T28 needs it.
- A real browser round trip. `oidcRoundTripEndToEnd` has no suite and no owning task.
- Any production change. One found in Phase 6 is a finding for the Phase 9 gate.

## Business Rules

- **R10** — a SAS login failure, including an unenrolled MERCHANT or ADMIN, shows the same uniform message as
  a wrong password; the SPA never says enrollment is required.
- **R30** (must not be contradicted) — identity-error copy, redirect target and visual treatment are
  identical across account and token states. Owned by tasks 58/59.
- **R57** (cited) — `/login` is never cached and never given the app shell.
- **R6, R7, R8** (boundaries) — the hand-over and the callback machinery this rides on.

## Locked Decisions

- **L5** — SAS refuses unenrolled MERCHANT/ADMIN with the same error as a wrong password, **and does not
  enroll them**. The authority for absolute silence.
- **L4** — enumeration-safe UI with no carve-out on a public surface. Its one carve-out (`adminGetAccount`,
  R27, D-033) is ADMIN-only and authenticated, and must not be widened by accident.
- **L2** — interactive login is SAS-hosted, so the refusal's sentence is the server's.
- **L1, L3, L14, L16, L17** — PKCE and validated `state`/`nonce`; tokens in memory; the sessionStorage
  carve-out; `/app/callback` is public; the worker never caches `/login`.
- **`agents.md:40-44`** — authoritative: *"No client-side account-state gating. No lockout timers. Uniform
  copy only."*

## Dependencies

`CallbackScreen`, `SignInScreen`, `setSignInNotice`/`takeSignInNotice`, `en`, `DENYLISTED_NAVIGATION_PATHS`,
`appRoutes`, `APP_BASENAME`, `AUTHORIZE_STORAGE_KEYS`, `createI18n()`; vitest, jsdom, `act`, `createRoot`,
`createMemoryRouter`, `node:fs/promises`, `vi.stubGlobal`. All built. No config keys. **No contracts** — the
refusal reaches no API the SPA calls.

## Inputs

Not an API. The real callback screen driven under paired arrivals that differ only in how the sign-in failed;
the production source tree; the i18n catalogue; and synthetic violations used as controls.

## Outputs

A test file whose failures name the file, the forbidden shape, and which half of R10 it breaks.

## State Changes

None in production. Within a test: the three PKCE items are seeded and read by the real code; `location`,
`fetch`, `Storage.prototype` and the console are stubbed or spied and restored.

## Files to Create

- `src/auth/sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx`

## Files to Modify

None anticipated.

## Files NOT to Modify

- Anything under `spec/`.
- `src/auth/CallbackScreen.tsx`, `SignInScreen.tsx`, `signInNotice.ts`, `authorizeRequest.ts`,
  `codeExchange.ts`, `idTokenNonce.ts`, `accessToken.ts`, `returnTarget.ts`, `pkce.ts`; `src/app/routes.tsx`;
  `src/i18n/catalogue.ts`; `src/pwa/navigationDenylist.ts`; `package.json`.
- The neighbouring named tests — read as patterns, cited where they own a claim, never moved or duplicated.
- Anything under `services/auth/`.

## Acceptance Criteria

- **AC1** — an `error=` callback ends in the single `discard` closure: `signIn.discarded`, `/app/sign-in`, a
  replaced history entry, no token held. (R10, R8)
- **AC2** — **outcome equivalence**: an `error=` arrival, a mismatched `state`, a refused exchange and a bad
  `nonce` produce an **identical outcome struct** (destination, rendered text, notice, stored keys, live-region
  count). (R10, L4)
- **AC3** — an error code that **names the cause** changes nothing: `access_denied`, `invalid_request`,
  `unauthorized_client`, `login_required`, and a vendor code spelling enrollment all produce the AC2 outcome.
  (R10, L4)
- **AC4** — the error **code and description never surface**: absent from the rendered document, both stores,
  the router location, and every console channel. (R10, L3, L4)
- **AC5** — **no production module names enrollment** or any enrollment-shaped synonym, proven structurally
  over the comment-stripped tree, with every permitted hit named with its reason. (R10, L5)
- **AC6** — **no production module gates on an account state or a role** to decide what a failed sign-in
  shows. (`agents.md:40-44`)
- **AC7** — **no catalogue string** mentions enrollment, MFA, an authenticator, a code, or a lockout, across
  every user-facing string. (R10, L4)
- **AC8** — the SPA renders **one** failure sentence for a failed sign-in, and `signIn.failed` (a hand-over
  that never left) is held distinct from `signIn.discarded` **without either naming a cause**. (R10, L4)
- **AC9** — `/login` is denylisted and unanswerable by the SPA, cited from the owning module. (R57, L17, L2)
- **AC10** — every instrument is proven to report DIRTY first: the scans fail on a synthetic enrollment
  string, a synthetic role gate and a synthetic catalogue entry; the equivalence comparison fails when two
  arrivals differ in a way the SPA *should* distinguish. (T22-D14)

## Required Tests

Named test: **`sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy`** → R10 (`package.md:114`).

1. **The refusal arrives and is discarded** — `/app/callback?error=access_denied&state=…` ends at
   `/app/sign-in` with `signIn.discarded`, no token held, history replaced.
2. **Outcome equivalence** — the four failure kinds compared as one struct, `toEqual` over the whole thing.
   **Plus the control** that the comparison fails on a difference the SPA *is* meant to make — a *successful*
   callback — so it is not blind.
3. **Named causes change nothing** — a table of error codes including one spelling enrollment.
4. **Nothing of the error escapes** — sweep the document, both stores, the router location and every console
   channel for the code and description, with a sentinel proving each surface is read.
5. **Structural silence** — a comment-stripped scan for enrollment vocabulary (`enroll`, `enrolment`,
   `set up two-factor`, `register your authenticator`, `activate MFA`, and the T27 synonym set), with the
   permitted-hit table and a synthetic violation that must fail it.
6. **No account-state or role gating** — a scan for a branch on a role or account state in the sign-in and
   callback path, with a synthetic `if (role === 'MERCHANT')` as its control.
7. **The catalogue is silent** — every user-facing string, with a synthetic entry as the control.
8. **One sentence per outcome** — `signIn.discarded` and `signIn.failed` are distinct, neither names a cause,
   and no third failure sentence exists.
9. **`/login` denylisted** — one assertion citing `navigationDenylist.ts`.
10. **Controls** — the scan over an empty tree refused; an empty catalogue refused; each synthetic violation
    failing its own instrument; the equivalence helper shown to fail on a success.

## Constraints

- **Security.** The file must not weaken an existing claim to make its own pass. Where a claim exists (T17/T18
  on `state`, T20 on the exchange, T22 on leaks, T23 on deletions, T26 on the inventory, T27 on MFA absence,
  R57's task on the denylist), cite it.
- **No enrollment-aware production code may be introduced**, including as a fixture that only works against
  one. The subject is silence.
- **Uniformity is the thing under test, so no case may assert copy that distinguishes one failure from
  another.** A case that pinned a refusal-specific sentence would make a future breach of R10 pass.
- **The notice is a token, not copy** (T27's measurement): `signInNotice.ts:13` is a one-member union, so
  asserting one notice equals another cannot fail. The copy claim belongs to the catalogue and the rendered
  document.
- **Module boundaries.** Routes, keys and copy come from the modules that own them; a literal here is a defect
  because it would keep passing after a rename.
- **Instrument ordering.** A `location`, `fetch`, `Storage.prototype` or console spy must be installed before
  the code under test runs; the file must hold the control that demonstrates it.
- **Comment stripping before any source scan**, and assertions over *files*, not `path:line`.
  `CallbackScreen.tsx:84-85` explains this very rule and names task 28, so an unstripped scan reads its own
  justification as the defect.
- **Vacuity.** An empty source tree, a pattern that matches nothing, an equivalence comparison between two
  identical arrivals, and a struct whose fields are all empty each satisfy the claim while proving nothing.
  Each must be refused explicitly. **Note the measured trap:** on the discard path the document *does* render
  copy, unlike T27's success path — so the text field is non-empty here and the comparison is stronger.
- **jsdom limits, declared not hidden.** No navigation is performed (T23-D5); `document.cookie` is empty and
  there is no `indexedDB`. A real refusal through SAS is `oidcRoundTripEndToEnd`'s.
- **Verification.** `npx -y pnpm@12.14.0 exec vitest run …` from `frontend/`; **the exit code is the verdict**.
  One known environmental failure: `lintRejectsHandWrittenBackendFetch.test.ts:118`. Suite baseline at T27
  close: 1295 passed / 1 failed.
- **Mutation discipline.** Tests committed before any run (the harness reverts with `git checkout`), and the
  harness committed with the artifacts. Mutants must include: surfacing the error code; a refusal-specific
  sentence; a role gate on failure copy; an enrollment string in the catalogue; and **an error-code branch
  that only fires for a code no test uses** (the escape mutant).
- **Performance / concurrency / transactions.** Not applicable.

## Open Questions

**No blockers.** Three items for the Phase 4 gate, each with the reading this brief proceeds under:

1. **R10's uniformity obligation is largely unreachable from the SPA** (OQ1). The refusal is a login failure,
   so its copy is SAS's. T28 verifies the one reachable path — the `error=` response — plus absolute silence,
   and declares the rest out of SPA scope by L2. If the owner reads R10 as requiring a browser-level check,
   that is `oidcRoundTripEndToEnd`'s, which has no suite and no owning task.
2. **"Identical visual treatment" is trivially true today** (OQ2). Measured 2026-10-29: the SPA has no CSS
   file, no Tailwind config and no `className` in any production component. So R30's styling clause cannot be
   meaningfully verified until task 173 lands. Recorded, not claimed.
3. **The backend's accepted rollout gap** (OQ3): `TotpAuthenticationProvider`'s comment records that a correct
   password followed by an enrollment block is still a password-correctness oracle at the *timing* level. It is
   server-side, already a recorded decision, and R30 puts timing out of SPA scope. No T28 case touches it.

---

**Phase 2 complete.** Brief ready for the Phase 3 adversarial review.
