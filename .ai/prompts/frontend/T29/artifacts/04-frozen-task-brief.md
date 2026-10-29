# frontend · T29 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T29 — Uniform sign-in failure |
| **Model** | Claude Opus 5 (Human Approval gate) |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |

**STATUS: FROZEN.** Nine findings dispositioned. Two were factually wrong in their citations and are corrected
below; one is accepted in a form that **replaces** another's recommendation; one is accepted only in part,
because the measurement says the rest would block specified work.

---

## Decision packet — Phase 3 disposition

### F1 — accepted, and the citation is wrong in a way worth fixing

Kimi writes *"R30 says 'the five account states'"*. **It does not.** `requirements.md:104-106` reads *"identical
across unknown or missing, locked, suspended, and deleted accounts"* — four clauses, with `unknown or missing`
as one. The number **five** comes from `tasks.md:95`, task 58: *"Unknown, missing, locked, suspended, and
deleted accounts."*

The count is right and the source is not, which matters because the two readings differ on whether *unknown*
and *missing* are one state or two. **Frozen: five, taken from task 58's enumeration**, since task 58 is the
unit that will extend this file and a split count between the two would be worse than either.

Required Test 1 now enumerates them.

### F2 — accepted, and it is the finding that decides the task

This is the strongest item in the review and Kimi gets the vacuity exactly right: *"If all five states look
identical at the SPA boundary, the test's only claim is that the SPA is indifferent to indistinguishable
inputs… the comparison is between identical inputs, which proves nothing."*

That is correct, and it is Phase 0's measurement turned into a design problem. `AccountUserDetailsService.java:43-58`
collapses all five states into two `UserDetails` flags and `TotpAuthenticationProvider` throws the identical
`BadCredentialsException("Bad credentials")` for every one. A login failure then re-renders SAS's own `/login`
(L2) and never returns to the SPA at all.

**Kimi's Option A is rejected and Option B is frozen.** Option A — one fixture for all five — is the vacuous
comparison Kimi itself names. Option B is strictly stronger and it is what the instrument must be:

> Each of the five states is represented by a **distinct** arrival at the callback, carrying that state's own
> vocabulary in `error`, `error_description` and `error_uri`. The claim is then two things at once: the five
> arrivals produce **one** outcome struct, **and** none of the five state words reaches any swept surface.

The fixtures are therefore more hostile than anything SAS would send, which is the point — the assertion is
that the SPA is indifferent *by construction*, not that it happens to receive uniform input. This also keeps
T29 distinct from T28, which drove failure *kinds* with one enrollment-naming description; T29's axis is the
five state words, and its second half is a leak claim T28 does not make.

**The honest consequence is declared, not hidden:** these arrivals do not occur in production, because SAS
refuses before redirecting. The fixtures are adversarial by design. R10's *"copy identical to a wrong
password"* remains verifiable on the client path only — see **T29-D1**.

### F3 — accepted, and the `setTimeout(…, 0)` case is the right test of the wording

Kimi is right that *"present"* was undefined and that `setTimeout(…, 0)` is the shape that exposes it. Frozen
wording, which the case must assert literally:

> With `vi.useFakeTimers()` installed **before** the arrival and the clock never advanced, after `act` has
> flushed, `document.body.textContent` contains `en.signIn.discarded` **and** the live region's text is
> exactly that sentence. No `vi.advanceTimersByTime`, no `runAllTimers`, no `await` on a timer.

A `setTimeout(…, 0)` fails this, because a fake clock does not run a zero-delay callback until it is advanced.

### F4 — accepted in substance, and its recommendation is REPLACED by F5's

F4 asks for an explicit identity-path file list and then defines it as *"and any file they import that
participates in failure rendering"* — which is the vagueness F4 is complaining about. F5 identifies why a file
list is the wrong shape at all. So:

**Frozen: there is no identity-path file list.** The timer scan runs over **every** production file, and the
three permitted timers are an **allowlist carrying reasons**, in exactly T28's permitted-hit form:

| File | Timers | Why permitted |
|---|---|---|
| `api/sessionBoundary.ts` | `setTimeout`×2, `queueMicrotask` | `RENEWAL_TIMEOUT_MS`, **specified** by R21 and L18 |
| `api/crossTabRenewal.ts` | `setTimeout`×2, `queueMicrotask` | the tab-lock grace and renewal deadline, same requirement |
| `api/rateLimitNotice.ts` | `queueMicrotask` | re-throwing a broken listener outside the response chain (R23) |

Measured comment-stripped: those are the only three, and `auth/`, `i18n/`, `app/` and `pwa/` hold **zero**.
The allowlist's accounting must itself be exercised — a row that explains nothing, and a row one short — the
same control T28 needed because its permitted table was empty.

### F5 — accepted, and it is why F4's file list was dropped

A `setTimeout` in a helper imported by `SignInScreen` is invisible to a file-scoped scan, and the structural
claim is about the path, not the file. The whole-tree scan plus a reasoned allowlist closes it without import
tracing. Any new timer anywhere now requires a row with a reason, which is the behaviour we want from a guard.

### F6 — accepted, and the measurement makes it cheap

Measured: **no production file contains `className`, `style=`, `styled` or `.css`.** So the pin is honest
today and the synthetic is what gives it teeth. Frozen as AC4's case: every rendered node on the failure
screens carries no `class` and no `style` attribute, with a synthetic control in which one failure renders
`className="locked"` and the case fails. Declared as a **pin for task 173**, not a claim that styling will
never exist.

### F7 — accepted IN PART, and the rejected part is the important part

Kimi proposes adding `banned|terminated|archived|expired|revoked|dormant` and mentions `invalid`. Measured,
comment-stripped, **every one of those is at zero hits today** — so "does it fire on existing code" is not the
question. The question is whether the spec *requires* the word later, and for four of them it does:

| Word | Disposition | Why |
|---|---|---|
| `banned`, `terminated`, `archived`, `dormant` | **forbidden everywhere** | zero hits, and no spec surface uses them |
| `expired` | **sign-in-scoped only** | `EXPIRED` is a **specified invoice state** (`design.md` O1: OPEN, PAID, UNDERPAID, OVERPAID, EXPIRED, HELD). A global forbid would block every invoice screen |
| `revoked`, `revoke` | **sign-in-scoped only** | session revocation is specified with four named tests — `revokeOneSessionRemovesIt`, `revokeAllSessionsRemovesAll` (R18, R19) |
| `invalid` | **sign-in-scoped only** | form-validation copy, tasks 43–57 |
| `denied`, `forbidden`, `blocked`, `frozen`, `rejected` | **sign-in-scoped only** | `HELD` is entered on a compliance block (R56, L8); 403 copy exists |
| `closed` | **rejected outright** | three legitimate hits in `crossTabRenewal.ts` — a channel being closed |
| `shadow` | **rejected** | not an account state in this system |
| `disabled` | **already excluded** | `aria-disabled` in `SignInScreen.tsx:61`, which L11 requires |

**This is the same error class T28 rejected half of its own F3 for, and then committed anyway at Kimi I5** —
six terms that would each have blocked copy tasks 81–89 are specified to write. The lesson is now a rule in
this brief: *a word's measured absence today is not permission to forbid it globally; the test is whether the
spec commissions it later.*

### F8 — accepted as a scope statement, and the owner it names is wrong

Kimi recommends deferring the three token states *"to task 58 or task 136"*. Neither owns them. **Task 59**
does, by name: *"Identical across token states. Bad, expired, and already-used tokens render identical error
copy and treatment. Verifies `identityErrorsIdenticalAcrossTokenStates`."* That is a **different named test**
with its own entry in `package.md` §8 under Phase 1a.

Frozen: the three token states are **out of T29's scope**, owned by task 59, and additionally unverifiable
today because `mountShell.tsx:20-28` never calls `createSessionBoundary` or `createRateLimitNotice`, so
`onRenewalFailed` has no production caller. Recorded as **T29-D2**.

### F9 — accepted, and it is F3's necessary partner

A fake-timer assertion with nothing to advance passes whether or not the clock is fake. Frozen as a control: a
synthetic component that renders the sentence inside `setTimeout(…, 100)` is driven through the same helper and
the same assertion, and **must fail**; the real screens must pass. Without it, AC5's behavioural half is
decoration.

---

## Open Questions — final disposition

**OQ1 — three tasks, one named test. FROZEN BY DEFAULT, reversible by the owner.**

`identityErrorsIndistinguishableAcrossAccountStates` is cited by tasks **29**, **58** and **136**, and
`package.md` §8 lists it once. The split frozen here:

- **T29** creates the file and owns the **sign-in and callback axis** — the only identity routes with an
  element today.
- **T58** extends the same file to registration, verification and password reset once those screens exist.
- **T136** is the Phase 1 **gate** that re-runs the property across every Phase 1 screen.

Precedent: `returnTargetOutsideAllowlistFallsBackToHome` was created by T24 at 67 cases and grown by T25 to
184, in one file, with each task's cases labelled. The alternative — three tasks writing one file with no
stated split — is how one silently overwrites another. **The owner can overturn this at any point; it is
recorded here so T58 and T136 inherit a decision rather than a collision.**

**OQ2 — the composition root. NOT A BLOCKER, declared as T29-D2.** See F8.

**OQ3 — `oidcRoundTripEndToEnd` has no suite. NOT A BLOCKER, declared as T29-D1.** The same limitation T28
declared as T28-D1; not reopened.

---

## Task

Prove that every SAS sign-in failure the SPA can observe produces one message, one redirect target, one visual
treatment and one rendering timing — identical across the five account states. Verification-only: one new test
file.

## Purpose

R30 is the enumeration lock as a UI property and L4 states it `[ALL]` with no carve-out on a public surface.
The failure mode is a sympathetic addition: *"That account has been suspended — contact support"*, a lockout
countdown, a pause before the error so a locked account feels different. Each looks like courtesy; each hands
an anonymous prober a test for whether an email exists and what state it is in. The backend already refuses all
five states with the identical exception and message, and records that a friendlier signal was considered and
rejected as an oracle. This is the client-side half of that decision.

## Scope

**In** — the sign-in start and the callback; copy, redirect target, visual treatment and the SPA's own
rendering timing across the five account states; structural absence of account-state vocabulary and of any
unpermitted timer; the `signIn.*` key-set pin, cited to T28.

**Out** — the five states through a real SAS login (F2, T29-D1); the three token states (task 59, T29-D2);
account-state rendering on registration, verification and reset screens (task 58); the Phase 1 enumeration
gate (task 136); the 401/renewal and 429 surfaces (no production path); styling (task 173); re-proving T17–T28;
any production change — one found in Phase 6 is a finding for the Phase 9 gate.

## Business Rules

- **R10** — every SAS login failure shows the same uniform message, copy identical to a wrong password, and
  never says enrollment is required.
- **R30** — copy, redirect target and visual treatment identical across the account states and the token
  states; SPA-observable only, server timing out of scope.
- **R21** (must not be contradicted) — one shared renewal with `RENEWAL_TIMEOUT_MS`. A timer scan that forbade
  it would fail specified code (F4).
- **R18, R19, R23, R29, R56** (must not be contradicted) — session revocation, the rate-limit notice, mid-task
  input, the compliance `HELD` block. Each commissions a word F7 wanted forbidden globally.
- **R8, R28** (boundaries) — the discard and the unauthenticated redirect. T17/T18 and task 41 own them.

## Locked Decisions

- **L4** — enumeration-safe UI, no carve-out on a public surface, **timing included**; no lockout timers. Its
  one carve-out (`adminGetAccount`, R27, D-033) is authenticated and ADMIN-only and must not be widened.
- **`agents.md:40-44`** — authoritative, and the reconciliation of L4's timing clause with R30's exclusion:
  *"The timing requirement applies to the SPA's own rendering only."*
- **L2** — interactive login is SAS-hosted, so the refused visitor's sentence is the server's.
- **L8** — the truthful state machine; `HELD` may be entered on a compliance block (F7).
- **L11** — live regions for async results; catalogue-only strings; `aria-disabled` over `disabled` (F7).
- **L1, L3, L14, L16, L17** — PKCE and validated `state`/`nonce`; tokens in memory; the `/app` route set; the
  sessionStorage carve-out; `/login` never cached.

## Dependencies

`SignInScreen`, `CallbackScreen`, `setSignInNotice`/`takeSignInNotice`, `en`, `appRoutes`, `APP_BASENAME`,
`SIGN_IN_PATH`, `PUBLIC_ROUTE_PATHS`, `AUTHENTICATED_ROUTE_PATHS`, `AUTHORIZE_STORAGE_KEYS`, `accessToken`,
`createI18n()`; vitest, jsdom, `act`, `createRoot`, `createMemoryRouter`, `node:fs/promises`, `vi.stubGlobal`,
`vi.useFakeTimers`. All built. **No contracts** — a login failure reaches no API the SPA calls. No config keys.

## Inputs

Not an API. The real sign-in and callback screens driven under five arrivals that differ only in which account
state their `error`, `error_description` and `error_uri` name; the production source tree, comment-stripped;
the i18n catalogue; synthetic violations as controls.

## Outputs

A test file whose failures name the file, the forbidden shape, and which clause of R30 it breaks — copy,
redirect, treatment or timing.

## State Changes

None in production. Within a test: the three PKCE items are seeded and read by real code; `location`, `fetch`,
`Storage.prototype`, the console and the clock are stubbed or faked and restored.

## Files to Create

- `src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx`

## Files to Modify

None anticipated.

## Files NOT to Modify

- Anything under `spec/`.
- `src/auth/CallbackScreen.tsx`, `SignInScreen.tsx`, `signInNotice.ts`, `authorizeRequest.ts`,
  `codeExchange.ts`, `idTokenNonce.ts`, `accessToken.ts`, `returnTarget.ts`, `pkce.ts`; `src/app/routes.tsx`,
  `mountShell.tsx`; `src/i18n/catalogue.ts`; `src/api/*`; `src/pwa/*`; `package.json`.
- The neighbouring named tests — read as patterns, cited where they own a claim, never moved or duplicated.
- Anything under `services/auth/`.

## Acceptance Criteria

- **AC1** — the five account-state arrivals each end in the one `discard` closure: `signIn.discarded`,
  `/app/sign-in`, `historyAction` `REPLACE`, empty `search` and `hash`, no token held. (R10, R30, R8)
- **AC2** — **outcome equivalence across the five states**: the five arrivals produce an **identical outcome
  struct**. (R30, L4 · F1, F2)
- **AC3** — **none of the five state words survives the discard**: absent from the rendered document — body,
  title, head — both stores, `window.name`, the router location, and every console channel. The arrival URL
  carries them because the fixture put them there; the claim is about what survives. (R30, L4 · F2)
- **AC4** — **visual treatment is identical, and pinned**: every rendered node on the failure screens carries
  no `class` and no `style` attribute, with a synthetic control in which one state renders `className="locked"`
  and the case fails. A pin for **task 173**, not a claim that styling will never exist. (R30 · F6)
- **AC5** — **the SPA's own rendering is not timed by cause.** With `vi.useFakeTimers()` installed before the
  arrival and **the clock never advanced**, after `act` has flushed, `document.body.textContent` contains
  `en.signIn.discarded` and the live region's text is exactly that sentence, for every one of the five states.
  (L4, `agents.md:44` · F3)
- **AC6** — **no unpermitted timer exists in any production module**, proven over the whole comment-stripped
  tree with a three-row allowlist carrying reasons, and the allowlist's accounting exercised. (L4, R21 · F4, F5)
- **AC7** — **no production module names an account state**, over the comment-stripped tree, keyed on the
  globally-forbidden set and never on `unknown`, `missing`, `pending`, `closed` or bare `disabled`. (R30, L4)
- **AC8** — **no catalogue string names an account state**, with the vocabulary **split**: the globally
  forbidden words anywhere, the sign-in-scoped words only on `signIn.*` and `callback.*` keys, and the split
  held from **both** directions. (R30, L4, Q14 · F7)
- **AC9** — nothing gates what a failed sign-in renders on an account state or a role. Instrument inherited
  from T28, re-run rather than rewritten. (`agents.md:42-43`)
- **AC10** — `signIn.failed` and `signIn.discarded` remain the only two failure sentences and the `signIn.*`
  key set is **exactly** the six that exist. (R10, L4)
- **AC11** — every instrument is proven to report DIRTY first: the scans fail on a synthetic state word, a
  synthetic unpermitted timer, a synthetic class attribute and a synthetic catalogue entry; the equivalence
  comparison fails on a **successful** callback; the fake-timer assertion fails on a **synthetic delayed
  render** (F9); and a scan over an empty tree is refused rather than reported clean. (T22-D14)

## Required Tests

Named test: `identityErrorsIndistinguishableAcrossAccountStates`.

1. **AC1/AC2** — the five states, enumerated as `unknown`, `missing`, `locked`, `suspended`, `deleted` (F1),
   each with its own `error`, `error_description` and `error_uri` fixture (F2), compared as one struct; plus
   the control that the comparison fails on a successful callback.
2. **AC3** — the leak sweep over six surfaces and six console channels, with a sentinel control per surface.
3. **AC4** — the class and style pin, with the `className="locked"` control (F6).
4. **AC5** — the clock-at-zero assertion, for all five states (F3).
5. **AC5/AC11** — the synthetic `setTimeout(…, 100)` render, which must fail the same assertion (F9).
6. **AC6** — the whole-tree timer scan, the three-row allowlist, and the accounting controls (F4, F5).
7. **AC7/AC8** — the source and catalogue scans, with the split held from both directions and the
   permitted-elsewhere control (F7).
8. **AC9/AC10** — the gating scan and the key-set pin, cited to T28.
9. **AC11** — the DIRTY control per instrument, and the empty-tree refusal through the one shared guard.

## Constraints

- **Security.** The file must not weaken an existing claim to make its own pass. Where a claim exists — T17/T18
  on `state`, T20 on the exchange, T22 on leaks, T23 on deletions, T26 on the inventory, T27 on MFA absence,
  T28 on the four failure kinds, the error surfaces and the `signIn.*` key set, R57's task on the denylist —
  cite it.
- **Uniformity is under test, so no case may assert copy that distinguishes one cause from another.** A case
  pinning a state-specific sentence would make a future breach of R30 pass.
- **A word's measured absence today is not permission to forbid it globally.** The test is whether the spec
  commissions it later. `EXPIRED` is an invoice state, `revoke` a session action with four named tests,
  `invalid` form-validation copy, `blocked`/`denied` the `HELD` compliance path. Sign-in-scoped or not at all.
  (F7, and T28's Kimi I5.)
- **A guard must not forbid what the spec permits.** `RENEWAL_TIMEOUT_MS` is required by R21 and L18; a
  whole-tree timer scan without the allowlist fails specified code while reporting an L4 breach. (F4)
- **The timer allowlist is measured, not assumed.** Comment-stripped: `api/sessionBoundary.ts`
  (`setTimeout`×2, `queueMicrotask`), `api/crossTabRenewal.ts` (`setTimeout`×2, `queueMicrotask`),
  `api/rateLimitNotice.ts` (`queueMicrotask`). `auth/`, `i18n/`, `app/` and `pwa/` hold **zero**.
- **The state-word pattern must not key on these, and the counts are measured.** `unknown` **14** (the
  TypeScript type), `missing` **2** (`idTokenNonce`'s `'claim-missing'`), `pending` **9**
  (`sessionBoundary`'s in-flight renewal, `signInNotice`'s module variable), `closed` **3**
  (`crossTabRenewal`'s channel), `disabled` **1** (`aria-disabled`, which L11 requires), `not found` **1**
  (`mountShell`'s developer throw).
- **Words measured at zero and safe to forbid everywhere:** `locked`, `lockout`, `suspended`, `deleted`,
  `deactivat`, `inactive`, `no such`, `banned`, `terminated`, `archived`, `dormant`.
- **Comment stripping before any source scan.** `CallbackScreen.tsx:35` contains *"A missing `state`, a
  mismatched `state`…"* while explaining this very property, so an unstripped scan reads the package's own
  justification as the defect. Assert over *files*, not `path:line`.
- **Module boundaries.** Routes, keys and copy come from the modules that own them; a literal here is a defect.
- **Instrument ordering.** A `location`, `fetch`, `Storage.prototype`, console or clock stub must be installed
  before the code under test runs; the file must hold the control that demonstrates it.
- **Fake timers are themselves a vacuity risk**, which is why F9's synthetic is required rather than optional.
- **Global surfaces leak between cases.** `document.title` and `window.name` outlive a document reset; T28's
  Kimi Gap 2 was exactly this. One cleanup list, called from the arrival helper, not only from `afterEach`.
- **Vacuity.** An empty tree, a pattern matching nothing, a comparison between two identical arrivals, and a
  struct of empty fields each satisfy the claim while proving nothing. Each must be refused. **The dominant
  risk here is F2's**: five fixtures that are actually the same fixture.
- **Null handling.** `takeSignInNotice()` is single-read and returns `null` on the ordinary path; a second read
  in the same case is not evidence.
- **jsdom limits, declared not hidden.** No navigation is performed (T23-D5); `document.cookie` is empty and
  there is no `indexedDB`.
- **Performance / concurrency / transactions.** Not applicable.
- **Verification.** `npx -y pnpm@12.14.0 exec vitest run` from `frontend/`; **the exit code is the verdict**.
  One known environmental failure: `src/api/lintRejectsHandWrittenBackendFetch.test.ts:118`. Suite baseline at
  T28 close: **1342 passed / 1 failed**.
- **Mutation discipline.** Tests committed before any run (the harness reverts with `git checkout`), harness
  committed with the artifacts. Mutants must include: a state-specific sentence in the catalogue; a lockout
  countdown; a cause-keyed `setTimeout` before the notice; a `setTimeout(…, 0)` before the notice (F3's
  subject); an account-state gate on the render; a state word in a component; a `className` keyed on the
  cause (F6); a divergent redirect target for one state; a timer in an `auth/` module (F5's subject); and an
  escape mutant — a delay that fires only for a state no test drives.

## Declared Deviations

- **T29-D1 (anticipated) — the five states are driven as adversarial fixtures, not through SAS.** Measured:
  the backend refuses all five with the identical `BadCredentialsException("Bad credentials")` before any
  redirect, so the arrivals this file drives do not occur in production. They are *more* hostile than reality,
  which is what makes the indifference claim non-vacuous (F2). R10's *"copy identical to a wrong password"*
  stays verifiable on the client path only; the refusal's own sentence is SAS's (L2) and belongs to
  `oidcRoundTripEndToEnd`, which has no suite and no owning task. Same substance as T28-D1.
- **T29-D2 (anticipated) — the three token states and the renewal/rate-limit surfaces are out of scope.**
  Task 59 owns `identityErrorsIdenticalAcrossTokenStates` by name. Independently, `mountShell.tsx:20-28` never
  calls `createSessionBoundary` or `createRateLimitNotice`, so `onRenewalFailed` has no production caller and
  `<RateLimitNotice>` is never mounted — there is no production path to verify. The composition root is unowned
  by any task.
- **T29-D3 (anticipated) — "identical visual treatment" is a pin, not a proof.** No styling exists in the SPA;
  measured, no production file contains `className`, `style=`, `styled` or `.css`. **Task 173** owns making
  AC4 non-trivial. Same substance as T28-D2.

---

**Phase 4 complete. STATUS: FROZEN.** Ready for Phase 5 implementation planning.
