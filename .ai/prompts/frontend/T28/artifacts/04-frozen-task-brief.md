# frontend · T28 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T28 — Unenrolled privileged refusal copy |
| **Model** | Human Approval (packet assembled by Claude Opus 5) |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |

**STATUS: FROZEN.** Downstream phases may not renegotiate this brief. A change found necessary in Phase 6 is
a finding for the Phase 9 gate, not an edit here.

---

## Decision packet — Phase 3 disposition

**Eight findings, all eight accepted** — but three carry corrections from measurement, and in two of those the
recommendation as written would have introduced a defect rather than closed one.

| ID | Severity | Disposition | One-line reason |
|---|---|---|---|
| F1 | Medium | **ACCEPTED** | Measured: the error branch precedes the state check, so the rows pin a real ordering |
| F2 | Medium | **ACCEPTED** | And the SPA already passes it — the `replace` to `/sign-in` destroys the query entirely |
| F3 | Medium | **ACCEPTED in part** | `two-factor` is a real hole; `required`/`incomplete`/`please` would forbid permitted copy |
| F4 | Low | **ACCEPTED** | `router.state.historyAction` is available and reads `REPLACE` |
| F5 | Low | **ACCEPTED** | A path scan is a genuinely different instrument from a content scan |
| F6 | Low | **ACCEPTED, scoped** | A bare `state ===` scan would fire on the OAuth state checks — 3 legitimate hits |
| F7 | Low | **ACCEPTED, reformulated** | Kimi's enumeration is wrong about what a discard screen renders |
| F8 | Low | **ACCEPTED** | Follows from F2 |

### F1 — accepted, and it pins an ordering property

Measured against the real route graph, all three shapes:

| Arrival | Destination | History |
|---|---|---|
| `?error=access_denied` (no `state`) | `/app/sign-in` | `REPLACE` |
| `?error=access_denied&state=WRONG` | `/app/sign-in` | `REPLACE` |
| `?error=access_denied&state=S` (matching) | `/app/sign-in` | `REPLACE` |

Identical, because `CallbackScreen.tsx:101-104` returns **before** the `state` checks at `:109` and `:138`.
That ordering is the substance: an error response is refused on its own terms and never has its `state`
consulted, so no error-handling path can become a state oracle. Folded as two more rows on the `error=` table.

### F2 — accepted, and the measurement is better news than the finding

Measured with `error_description=Two-factor+authentication+required` on the arrival URL. **After the
discard:** `location.search` is `""`; the description is absent from the location object, the rendered
document and both stores. The `replace` navigation to `/sign-in` destroys the whole query.

So AC4 is satisfiable exactly as drafted, with one wording precision folded in: the claim is **after the
discard completes**. While the tab momentarily sits at `/app/callback` the description *is* in the URL,
because SAS put it there — that is the arrival, not a leak, and a claim that pretended otherwise would be
false. `error_description` and `error_uri` rows are added to the table and to the sweep.

### F3 — accepted in part, and the rejected half is the important half

**The hole is real.** Measured: T27's MFA pattern does **not** match `"two-factor authentication"` — it carries
`2fa` and `multi-factor` but not `two-factor`. So Kimi's example sentence, *"Your account needs two-factor
authentication set up before you can sign in"*, would have passed every scan in the package. Added:
`two-factor`, `enroll`/`enrolment`, `set up two-factor`, `register your authenticator`, `activate mfa`.

**Rejected: `required`, `incomplete`, and `please` as bare words.** Each measured to zero hits today, so
neither would false-positive *now* — and that is exactly why adding them is a trap. "This field is required"
is ordinary form-validation copy that tasks 43–57 will certainly write; "please" is ordinary politeness. A
scan firing on them would report an L2/R10 breach against permitted copy, which is T26's lesson in its exact
form: **a guard must not forbid what the spec permits.** The enrollment-intent phrases above carry the
meaning without the collision.

### F6 — accepted, but the suggested pattern must not be used

Kimi suggests scanning for `state === 'PENDING_ENROLLMENT'`-shaped code. **A bare `state ===` scan has three
legitimate hits**, measured: two in `CallbackScreen.tsx` (the OAuth `state` comparisons that T17 and T18 own,
the security control at the heart of R8) and one in `registerServiceWorker.ts:72`
(`installing.state === 'installed'`, the Workbox lifecycle). A scan on that pattern would flag the very checks
this package exists to protect.

**Folded as:** the scan keys on **account-state-shaped names** — `accountState`, `.mfaEnrolled`,
`requiresMfa`, `enrollmentState`, `mfaRequired` — each measured to zero hits, and never on the bare word
`state`. The synthetic control is `if (account.mfaEnrolled === false)`, alongside the role gate.

### F7 — accepted, reformulated, because Kimi's enumeration is factually wrong

Kimi asks the test to collect "every catalogue key whose value appears on a failed sign-in screen" and assert
the set is `{signIn.failed, signIn.discarded}`. Measured: the discard screen renders **four** `signIn.*`
values — `heading`, `explanation`, `action` and `discarded` — so that assertion would fail against correct
code.

**Folded as:** the `signIn.*` key set is derived from the catalogue and asserted to be **exactly** the six
that exist (`heading`, `explanation`, `action`, `starting`, `failed`, `discarded`), so a seventh — a future
`signIn.mfaRequired` — fails. That is the claim Kimi was reaching for, and it is the one that actually catches
the addition.

### F4, F5, F8 — accepted as written

- **F4:** `historyAction` joins the `Outcome` struct; measured `REPLACE` on every failure path.
- **F5:** a **path scan** over the 24 production files' relative paths, keyed on the name of the file rather
  than its contents — the same insight as T26's namespace scan, and it catches `EnrollMfaScreen.tsx` or
  `src/auth/enrollment/` carrying no enrollment vocabulary inside. Measured: no production path matches today,
  so the instrument needs a synthetic control to be worth anything.
- **F8:** a mutant that copies `error_description` into the document, a store, the console or the notice.

---

## Open Questions — final disposition

1. **OQ1 — R10's uniformity obligation is largely unreachable from the SPA. DEFERRED as a scope statement,
   owner: none needed.** The refusal is a login failure (`TotpAuthenticationProvider:100-101`), so its copy is
   SAS's and L2 puts it on the server. T28 verifies the one reachable path — the `error=` response — plus
   absolute silence. A browser-level check is `oidcRoundTripEndToEnd`'s, which has no suite and no owning task.
2. **OQ2 — "identical visual treatment" is trivially true today. RESOLVED as a recorded limitation.** The SPA
   has no CSS file, no Tailwind config and no `className` in any production component (measured 2026-10-29).
   R30's styling clause cannot be meaningfully verified until **task 173** lands. Recorded, not claimed.
3. **OQ3 — the backend's accepted rollout gap. RESOLVED: out of scope.** `TotpAuthenticationProvider`'s own
   comment records that a correct password followed by an enrollment block remains a password-correctness
   oracle at the timing level, accepted rather than fixed. R30 and `agents.md:44` both put response timing
   outside SPA scope in terms. No T28 case touches it.

---

## Task

Build `src/auth/sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx`, the named test for
R10. One new test file; no production change anticipated.

## Purpose

R10 forbids the SPA from telling a visitor that enrollment is required, and requires every sign-in failure to
read the same. The measured architecture makes those two obligations different in kind.

**Measured premise (Phase 0, read from `services/auth/`).** The refusal is a **login failure, not an
authorization error**. `TotpAuthenticationProvider:100-101` throws `BadCredentialsException("Bad
credentials")` for "MERCHANT/ADMIN with no confirmed enrollment", and the identical exception with the
identical message for an unknown user, a wrong password, and a bad or missing TOTP code. Its own comment
records that a distinct "please enroll" signal **was considered and rejected**, because reachable only after a
correct password it would work as a password-correctness oracle. So the refused visitor stays on the
SAS-hosted login page and **never reaches `/app/callback`**, and the sentence the task names is SAS's own.

**What this file is for, then.** Two things, and the second has the teeth:

1. **The one path by which a refusal CAN become SPA-observable**: an authorization error response at
   `/app/callback?error=…`. `CallbackScreen.tsx:101-104` already routes it into the single `discard` closure
   and its comment already names this task. Five existing files mention `error=`; every one uses it merely to
   reach the discard. **None asserts the outcome is indistinguishable from the other failures.** That claim is
   unowned and it is R10's.
2. **Silence, proven structurally over the bundle.** No enrollment copy, no enrollment-shaped file name, no
   enrollment-during-login path, no client-side gating on an account state or a role. L5 says SAS "does not
   enroll them", so nothing is pending and any such surface is a defect.

**The failure mode is a helpful addition.** *"Your account needs two-factor authentication set up before you
can sign in"* is the most natural sentence a developer would write on seeing a privileged account bounce. It
breaks R10, L4 and the backend's deliberate uniformity at once — and, measured, it would have passed every
scan this package had before T28.

## Scope

**In**
- One new test file.
- **Outcome equivalence** across the four failure kinds, compared as one struct including `historyAction`.
- The `error=` table: no `state`, mismatched `state`, matching `state`; codes that name the cause;
  `error_description` and `error_uri` that mention enrollment.
- The error code, description and URI **never surfacing after the discard**.
- **Structural silence**: content scan, **path scan**, and an account-state/role gating scan.
- A **catalogue scan**, plus a derived pin on the exact `signIn.*` key set.
- `/login` unanswerable by the SPA — cited from the owning module.
- Controls that make every instrument fail.

**Out**
- **SAS's refusal copy** — server-rendered (L2), unreadable and unasserted.
- **The bootstrap path and the enrollment wizard** — `privilegedBootstrapPathEnrollsWithoutEnrollmentHint` is
  **task 73's** and depends on tasks 66–72, none of which exists.
- **Enumerating account and token states** — tasks 58 and 59 own R30's two lists.
- **Response timing** — out of SPA scope by R30 and `agents.md:44`.
- **Visual treatment** — no styling exists; task 173.
- Re-proving T16–T27. Each is cited where T28 needs it.
- A real browser round trip. `oidcRoundTripEndToEnd` has no suite and no owning task.
- Any production change. One found in Phase 6 is a finding for the Phase 9 gate.

## Business Rules

- **R10** — a SAS login failure, including an unenrolled MERCHANT or ADMIN, shows the same uniform message as
  a wrong password; the SPA never says enrollment is required.
- **R30** (must not be contradicted) — identity-error copy, redirect target and visual treatment are identical
  across account and token states. Owned by tasks 58/59.
- **R57** (cited) — `/login` is never cached and never given the app shell.
- **R6, R7, R8** (boundaries) — the hand-over and callback machinery this rides on.

## Locked Decisions

- **L5** — SAS refuses unenrolled MERCHANT/ADMIN with the same error as a wrong password, **and does not
  enroll them**. The authority for absolute silence.
- **L4** — enumeration-safe UI, no carve-out on a public surface. Its one carve-out (`adminGetAccount`, R27,
  D-033) is ADMIN-only and authenticated and must not be widened by accident.
- **L2** — interactive login is SAS-hosted, so the refusal's sentence is the server's.
- **L1, L3, L14, L16, L17** — PKCE and validated `state`/`nonce`; tokens in memory; the sessionStorage
  carve-out; `/app/callback` is public; the worker never caches `/login`.
- **`agents.md:40-44`** — authoritative: *"No client-side account-state gating. No lockout timers. Uniform
  copy only."*

## Dependencies

`CallbackScreen`, `SignInScreen`, `setSignInNotice`/`takeSignInNotice`, `en`,
`DENYLISTED_NAVIGATION_PATHS`, `appRoutes`, `APP_BASENAME`, `AUTHORIZE_STORAGE_KEYS`, `createI18n()`; vitest,
jsdom, `act`, `createRoot`, `createMemoryRouter`, `node:fs/promises`, `vi.stubGlobal`. All built. No config
keys. **No contracts** — the refusal reaches no API the SPA calls.

## Inputs

Not an API. The real callback screen driven under paired arrivals differing only in how the sign-in failed;
the production source tree and its file paths; the i18n catalogue; synthetic violations as controls.

## Outputs

A test file whose failures name the file, the forbidden shape, and which half of R10 it breaks.

## State Changes

None in production. Within a test: the three PKCE items are seeded and read by real code; `location`,
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

- **AC1** — an `error=` callback ends in the single `discard` closure: `signIn.discarded`, `/app/sign-in`,
  `historyAction` `REPLACE`, no token held. (R10, R8)
- **AC2** — **outcome equivalence**: an `error=` arrival, a mismatched `state`, a refused exchange and a bad
  `nonce` produce an **identical outcome struct** — destination, `historyAction`, rendered text, notice,
  stored keys, live-region count. (R10, L4 · F4)
- **AC3** — an error that **names the cause** changes nothing: `access_denied`, `invalid_request`,
  `unauthorized_client`, `login_required`, a vendor code spelling enrollment, **and an `error_description` or
  `error_uri` mentioning two-factor enrollment**, all produce the AC2 outcome. (R10, L4 · F2)
- **AC4** — the error **code, description and URI never surface after the discard**: absent from the rendered
  document, both stores, the router location, and every console channel. The arrival URL carries them because
  SAS put them there; the claim is about what survives the discard. (R10, L3, L4 · F2)
- **AC5** — **no production module names enrollment**, including `two-factor` and the enrollment-intent
  phrases, proven over the comment-stripped tree, with every permitted hit named with its reason. (R10, L5 · F3)
- **AC6** — **no production file or directory is named** for enrollment, proven by a scan over paths rather
  than contents. (R10, L5 · F5)
- **AC7** — **no production module gates on an account state or a role** to decide what a failed sign-in
  shows, keyed on account-state-shaped names and never on the bare word `state`. (`agents.md:40-44` · F6)
- **AC8** — **no catalogue string** mentions enrollment, MFA, an authenticator, a code or a lockout, and the
  `signIn.*` key set is **exactly** the six that exist, so a seventh fails. (R10, L4 · F7)
- **AC9** — `signIn.failed` and `signIn.discarded` are distinct sentences and **neither names a cause**; no
  third failure sentence exists. (R10, L4)
- **AC10** — `/login` is denylisted and unanswerable by the SPA, cited from the owning module. (R57, L17, L2)
- **AC11** — every instrument is proven to report DIRTY first: the scans fail on a synthetic enrollment
  string, a synthetic enrollment-shaped path, a synthetic role gate, a synthetic account-state gate and a
  synthetic catalogue entry; the equivalence comparison fails on a **successful** callback. (T22-D14)

## Required Tests

Named test: **`sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy`** → R10 (`package.md:114`).

1. **The refusal is discarded** — `?error=access_denied&state=…` ends at `/app/sign-in` with
   `signIn.discarded`, `REPLACE`, no token held.
2. **The error branch precedes the state check** — no `state`, mismatched `state`, matching `state`: one
   outcome, so no error path can become a state oracle (F1).
3. **Outcome equivalence** — the four failure kinds as one struct, `toEqual` over the whole thing. **Plus the
   control** that it fails on a *successful* callback, so the comparison is not blind.
4. **Named causes change nothing** — a table of codes, plus `error_description` and `error_uri` mentioning
   two-factor enrollment (F2).
5. **Nothing of the error survives the discard** — sweep document, both stores, router location and every
   console channel for code, description and URI, with a sentinel proving each surface is read.
6. **Structural silence, by content** — comment-stripped scan for the enrollment vocabulary including
   `two-factor`, with the permitted-hit table and a synthetic violation (F3).
7. **Structural silence, by path** — a scan over the production file paths, with a synthetic
   `auth/EnrollMfaScreen.tsx` as its control (F5).
8. **No account-state or role gating** — keyed on account-state-shaped names, with synthetic
   `if (role === 'MERCHANT')` and `if (account.mfaEnrolled === false)` controls, and a case proving the scan
   does **not** fire on `CallbackScreen`'s own OAuth `state` comparisons (F6).
9. **The catalogue is silent, and its key set is pinned** — every user-facing string scanned; the `signIn.*`
   keys asserted to be exactly six; a synthetic `signIn.mfaRequired` fails (F7).
10. **One sentence per outcome** — `signIn.failed` and `signIn.discarded` distinct, neither naming a cause.
11. **`/login` denylisted** — one assertion citing `navigationDenylist.ts`.
12. **Controls** — an empty tree and an empty catalogue each refused; each synthetic violation failing its own
    instrument; the equivalence helper shown to fail on a success; a console spy installed late shown to miss.

## Constraints

- **Security.** The file must not weaken an existing claim to make its own pass. Where a claim exists (T17/T18
  on `state`, T20 on the exchange, T22 on leaks, T23 on deletions, T26 on the inventory, T27 on MFA absence,
  R57's task on the denylist), cite it.
- **No enrollment-aware production code may be introduced**, including as a fixture that only works against
  one. The subject is silence.
- **Uniformity is under test, so no case may assert copy that distinguishes one failure from another.** A case
  pinning a refusal-specific sentence would make a future breach of R10 pass.
- **The notice is a token, not copy** (T27's measurement): `signInNotice.ts:13` is a one-member union, so
  asserting one notice equals another cannot fail. The copy claim belongs to the catalogue and the document.
- **A scan must not fire on the controls this package depends on.** Measured: a bare `state ===` scan has
  three legitimate hits — two OAuth comparisons in `CallbackScreen` and the Workbox lifecycle in
  `registerServiceWorker.ts:72`. Key on account-state-shaped names only, and hold that with a case.
- **Module boundaries.** Routes, keys and copy come from the modules that own them; a literal here is a defect.
- **Instrument ordering.** A `location`, `fetch`, `Storage.prototype` or console spy must be installed before
  the code under test runs; the file must hold the control that demonstrates it.
- **Comment stripping before any source scan**, and assertions over *files*, not `path:line`.
  `CallbackScreen.tsx:84-85` explains this very rule and names task 28, so an unstripped scan reads its own
  justification as the defect.
- **Vacuity.** An empty tree, a pattern matching nothing, an equivalence comparison between two identical
  arrivals, and a struct of empty fields each satisfy the claim while proving nothing. Each must be refused.
  **Measured advantage over T27:** the discard path *does* render copy, so the struct's text field is
  non-empty and carries real weight here.
- **jsdom limits, declared not hidden.** No navigation is performed (T23-D5); `document.cookie` is empty and
  there is no `indexedDB`. A real refusal through SAS is `oidcRoundTripEndToEnd`'s.
- **Verification.** `npx -y pnpm@12.14.0 exec vitest run …` from `frontend/`; **the exit code is the verdict**.
  One known environmental failure: `lintRejectsHandWrittenBackendFetch.test.ts:118`. Suite baseline at T27
  close: 1295 passed / 1 failed.
- **Mutation discipline.** Tests committed before any run (the harness reverts with `git checkout`), harness
  committed with the artifacts. Mutants must include: surfacing the error code; **copying `error_description`
  into the document, a store, the console or the notice** (F8); a refusal-specific sentence; a role gate; an
  account-state gate; an enrollment string in the catalogue; a `push` instead of a `replace`; and **an
  error-code branch that only fires for a code no test uses** (the escape mutant).
- **Performance / concurrency / transactions.** Not applicable.

## Declared Deviations

- **T28-D1 (anticipated) — R10's uniformity obligation is verified on one path, not on the refusal itself.**
  The refusal is a login failure and its copy is SAS's (L2). Recorded as a scope statement, not a gap in the
  file.
- **T28-D2 (anticipated) — "identical visual treatment" is trivially true.** No styling exists in the SPA;
  **task 173** owns it.

---

**Phase 4 complete. STATUS: FROZEN.** Ready for Phase 5 implementation planning.
