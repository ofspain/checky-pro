# frontend · T28 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T28 — Unenrolled privileged refusal copy |
| **Model** | Claude Opus 5 |
| **Consumes** | all prior artifacts, Phase 11 included |
| **Produces** | `artifacts/12-specification-verification.md` |

**Task (verbatim, `spec/frontend/tasks.md` task 28):** *Unenrolled privileged refusal copy. A SAS refusal for
an unenrolled MERCHANT or ADMIN shows the wrong-password copy. Verifies
`sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy`. Cites R10, L5.*

Every figure below was re-measured for this phase, not carried over from Phase 10.

---

## Traceability matrix

| Requirement | Implemented? | Evidence (`file:line`) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R10** first half — a SAS login failure shows the same message as a wrong password | Yes, by one `discard` closure shared across every failure mode | `CallbackScreen.tsx:89-99` (the closure), `:101-104`, `:109-112`, `:138-141`, `:156-159`, `:200` (its five call sites) | `…WrongPasswordCopy.test.tsx:831` (four kinds, one struct), `:789`, `:957` | No | **T28-D1** — the refusal's own sentence is SAS's (L2) |
| **R10** second half — the SPA never says enrollment is required | Yes, by absence: no module, path, catalogue string or branch names it | measured: 24 production modules, **zero** enrollment hits, zero enrollment-shaped paths, zero account-state gates | `:1086`, `:1179`, `:1212`, `:1286` + 27 controls | No | No |
| **R30** (must not be contradicted) — identical copy, redirect target and visual treatment across account and token states | Copy and redirect: yes. Visual treatment: vacuously | `catalogue.ts:16-18`, `CallbackScreen.tsx:95-98` | `:831` (destination + text in the struct) | No | **T28-D2** — no styling exists; task 173 owns it |
| **R57** (cited) — `/login` is never cached and never given the app shell | Yes, cited not re-proved | `navigationDenylist.ts:17-20`, `:47` | `:1424` | No | No |
| **R6, R7, R8** (boundaries) — the hand-over and callback machinery | Yes, cited to T15–T23 | `CallbackScreen.tsx:66-205`, `authorizeRequest.ts`, `codeExchange.ts`, `idTokenNonce.ts` | `:755`, `:763` | No | No |
| **L5** — SAS refuses unenrolled MERCHANT/ADMIN with the same error, **and does not enroll them** | Yes — no enrollment-during-login path exists to be reached | the four absence scans above; `TotpAuthenticationProvider.java:100-101` throws the same `BadCredentialsException` as `:118`, `:125`, `:132` | `:1086`, `:1179` | No | No |
| **L4** — enumeration-safe UI, no carve-out on a public surface | Yes | `catalogue.ts:16-18` (the comment states the obligation), `CallbackScreen.tsx:88` | `:831` (two faults → one outcome), `:1404` (neither sentence names a cause) | No | No |
| **L2** — interactive login is SAS-hosted, so the refusal's sentence is the server's | Yes, and that is why **T28-D1** exists | `navigationDenylist.ts:20` | `:1424` | No | No |
| **L3** — tokens in memory only | Yes, cited to T21 | `accessToken.ts`, `CallbackScreen.tsx:94` | `:831` (`tokenHeld` in the struct), `:1001` (both stores swept) | No | No |
| **L14, L16** — resumption needs only the three PKCE items; the carve-out is exhaustive | Yes, cited to T23/T26 | `authorizeRequest.ts:35` | `:831` (`storedKeys` in the struct) | No | No |
| **L17** — the worker never caches `/login` | Yes, cited | `navigationDenylist.ts:17-47` | `:1424` | No | No |
| **L1** — PKCE with validated `state`/`nonce` | Yes, cited to T16–T19 | `CallbackScreen.tsx:123-141`, `:156-159` | `:789`, `:831` | No | No |
| **`agents.md:40-44`** — *"No client-side account-state gating. No lockout timers. Uniform copy only."* | Yes — the one instrument that can prove it | `spec/frontend/agents.md:42-43` | `:1212` + five controls (`:1218`, `:1226`, `:1240`, `:1256`, `:1265`) | No | No |

No contract applies: the refusal reaches no API the SPA calls.

## Acceptance criteria

Twelve `describe` blocks cover the eleven ACs. AC2 has two blocks — one for the three `state` shapes under an
error, one for the four-kind equivalence — because they are different claims over different fixtures, and the
Phase 10 rewording of the first (I6) made the split explicit rather than incidental.

| AC | Block | Verdict |
|---|---|---|
| AC1 the refusal ends in the single `discard` closure | `:754` | PASS — `search` and `hash` asserted absolutely at Phase 11 (Gap 5); **MB29** |
| AC2 the three `state` shapes are indistinguishable under an error | `:788` | PASS — ordering **inherited** from T18, and the block says so |
| AC2 four failure kinds, one outcome struct | `:830` | PASS — plus the per-kind exchange count (I3) |
| AC3 naming the cause changes nothing | `:924` | PASS — incl. `error_description` and `error_uri` (F2) |
| AC4 the code, description and URI do not survive the discard | `:988` | PASS — six surfaces incl. `document.title`, `head`, `window.name` (I1, I4); **MB04 now killed** |
| AC5 no production module names enrollment | `:1078` | PASS — seven controls, incl. the permitted-hit accounting; `multi-factor` added at Phase 11 (Gap 1) |
| AC6 no production file or directory is named for enrollment | `:1178` | PASS — the only instrument in the package that can see MB18 |
| AC7 nothing gates a failed sign-in on an account state or a role | `:1211` | PASS — membership tests added at Phase 11 (Gap 4); silent on `CallbackScreen`'s three legitimate `state ===` |
| AC8 no catalogue string mentions enrollment, a factor, a code or a lockout | `:1285` | PASS — the vocabulary **split** (I5) held from both directions |
| AC9 one sentence per outcome, neither naming a cause | `:1397` | PASS |
| AC10 `/login` is denylisted and unanswerable | `:1423` | PASS |
| AC11 every instrument proven to report DIRTY | `:1436` + the 27 control cases it rests on | PASS |

## Measured verification

Re-measured after the Phase 11 fixes, not carried over.

| Measurement | Command | Result |
|---|---|---|
| Suite | `npx -y pnpm@12.14.0 exec vitest run` from `frontend/` | **1342 passed / 1 failed** (24 files). Exit 1 **only** from the known environmental failure `src/api/lintRejectsHandWrittenBackendFetch.test.ts` — this host's `oxlint@1.87.0` prints no summary line. Baseline at T27 close was 1295/1; T28 adds 47. |
| Types | `tsc --noEmit` | **exit 0** |
| Lint | `oxlint src` | clean |
| Mutation | `python3 .ai/prompts/frontend/T28/artifacts/07-mutation-harness.py` | **29/29 killed**, 27 by T28's own file. MB12 and MB23 are `INHERITED-ONLY BY DESIGN` — T18's `getItem` spy owns the ordering. |
| Production change | `git diff --name-only 21e727a4~4..HEAD -- frontend/src` | **one file**: the test file. No production module was touched. |

The file is 1475 lines and **47 cases**.

Two of the Phase 11 assertions were checked for vacuity by hand, because a new assertion that no mutant can
reach is decoration:

- **Gap 5.** With `expect(outcome.search).toBe('')` and its `hash` partner deleted, **MB29 survives T28's
  entire file** — 47 of 47 pass. The two lines are the only thing in the bundle that sees a uniform query
  string on the sign-in URL. Measured, not argued.
- **Gap 2.** With `document.title` and `window.name` removed from `clearArrivalSurfaces`, the sentinel control
  fails and nothing else does. The clearing is load-bearing and the control is the thing holding it.

## Phase 11 disposition

Kimi's test review arrived during this phase, after the Phase 10 close. Seven gaps; each was checked against
source before it was accepted, and two were larger or smaller than stated.

| Gap | Verdict | What it changed |
|---|---|---|
| 1 · `multi-factor` not matched | **Accepted** | `ENROLLMENT_SOURCE` had `two-factor` and no `multi-factor`; T27's pattern has the reverse, so each file was blind to the phrasing the other caught — the same hole MB17 came through, from the other side. Three control rows, and **MB24**/**MB25**. |
| 2 · `measure()` does not reset the globals it sweeps | **Accepted, and wider** | `afterEach` is too late: AC2 measures four arrivals **inside one case**. The deeper defect was two cleanup lists that had drifted — the exchange-count loop never cleared `localStorage` either, which the finding did not catch. One `clearArrivalSurfaces()` now, plus a sentinel control. |
| 3 · exchange count misses two `error=` shapes | **Accepted** | The table reached only the matching-`state` shape; the other two were assumed rather than measured. Six rows, and **MB28**. |
| 4 · gating scan misses membership and type gates | **Accepted in part** | `roles.includes('MERCHANT')` was a real hole — a role gate naming no `role ===`. `account.type === 'MERCHANT'` was **already caught** by the existing `=== 'MERCHANT'` alternatives; asserted in a control so a later tidy-up cannot narrow the scan silently. Plus a control that the new alternative does not fire on ordinary `includes()` calls. **MB26**. |
| 5 · `search` and `hash` not asserted absolutely | **Accepted** | They are in the struct, so AC2 catches a *divergence* — but four kinds that all kept their query string compare equal and pass. **MB29** is the mutant that isolates it, and the vacuity check above shows the assertion is the only thing that sees it. |
| 6 · catalogue scan skips arrays | **Rejected on the facts** | An array **is** flattened: `typeof [] === 'object'`, so `Object.entries` walks it and every element is visited. `catalogueStrings` now takes an optional root and a control measures the keys that come back, so the rejection is evidence rather than an opinion in a comment. |
| 7 · harness not named at the mutation claim | **Accepted** | Path, command and score repeated at the AC11 block. |

One correction of my own, found while writing the mutants: **MB27's first draft read
`globalThis.location.search` and survived.** It was not an escape — it was inert. The suite drives a
`createMemoryRouter`, so the arrival's query lives on the router and the jsdom window's `location.search` is
empty; the mutant appended nothing and changed no behaviour. A mutant that does not express its own defect is
no evidence about the assertion meant to catch it. It now reads from `params`, as the screen does.

## The approving principal engineer's four questions

**1. Is the task fully complete?** Yes. One new file,
`frontend/src/auth/sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx`. The frozen brief
anticipated no production change and both reviewers independently stated none was indicated; the diff confirms
none was made. The named test the task statement cites now exists and runs.

**2. Does it satisfy every acceptance criterion?** Yes, all eleven, with two of them resting on scope
statements the Phase 4 gate froze (**T28-D1**, **T28-D2**) rather than on assertions this bundle can make.
Three adversarial reviews ran — Phases 3, 8 and 11 — producing twenty-two findings in total. Twenty were acted
on; one (I6) was a wording correction the file now carries, and one (Gap 6) was rejected on a measurement and
left a control behind. Three findings were larger or smaller than stated: I3 could not go in the struct
without breaking AC2 by construction, so it became a per-kind table; I5 named one over-broad term when there
were seven, six of which would have blocked copy tasks 81–89 are specified to write; Gap 4's second half was
already satisfied.

**3. Does it violate any LOCKED decision?** No. It introduces no enrollment-aware code, no account-state gate,
no role gate, no copy that distinguishes one failure from another, and no assertion that would let a future
breach of R10 or L4 pass. Nothing under `spec/` was modified.

**4. Remaining risks?**

- **The refusal itself is never driven through SAS.** `oidcRoundTripEndToEnd` has no suite and no owning task,
  so the server half of R10 is unverified by any test in this repository. This is T28-D1's substance and it
  predates T28.
- **OQ1 is still open and is not T28's.** The live SAS `/login` form carries no `mfaCode` field while
  `TotpAuthenticationDetailsSource` requires it in the same POST, so no real browser can complete an MFA
  sign-in. R9's end-to-end claim rests on that gap. Needs a backend task or an accepted limitation.
- **The absence scans are enumerative.** Every one is a named vocabulary, so a phrasing nobody listed passes.
  MB17 — Kimi F3's own sentence — demonstrated this: it passed every scan in the package before T28, because
  T27's pattern has `multi-factor` and not `two-factor`. The mitigation is the DIRTY controls, not the lists.
- **The Phase 11 fixes themselves were not independently reviewed.** They are the last change to the file and
  no phase follows that would look at them. Mitigated the only way available: every one of them is either
  killed by a new mutant (Gap 1, 3, 4, 5) or shown load-bearing by a hand vacuity check (Gap 2, 5).

## Declared deviations

- **T28-D1 — R10's uniformity is verified on the client path, not on the refusal itself.** The refusal is a
  login failure and its copy is SAS's server-rendered page (L2). Scope statement, frozen at Phase 4.
- **T28-D2 — "identical visual treatment" is trivially true.** No styling exists in the SPA; **task 173** owns
  it. Frozen at Phase 4.
- **T28-D3 — the notice is not a field of the `Outcome` struct.** `signInNotice.ts:13` is a one-member union,
  so asserting one notice equals another cannot fail, and the value is single-read — already consumed by
  `SignInScreen`'s `useState` initialiser by the time the struct is built. The copy claim is carried by the
  struct's `text` field and by AC8's catalogue scan instead.
- **T28-D4 — Phase 11 arrived after Phase 10 closed, not at the gate.** Two cues passed with no artifact; both
  times origin was pulled first and `artifacts/` listed, and `11-test-review.md` existed only as the *prompt*.
  The owner then directed the pipeline to move on, and Kimi's review landed as `d854a7d3` while this artifact
  was being written — which is why the Phase 12 commit was unwound and the fixes committed ahead of it.
  Nothing was self-produced in the interval; the standing rule is that phases 3, 8 and 11 are Kimi's, and
  writing one as a fallback would have turned an independent review into a second self-review wearing its
  label. Recorded because the same wait happened at T27's Phase 8 (**T27-D2**): the lag is real and the
  pipeline should expect it rather than treat it as absence.

## Carry-forward (not T28's)

- The Phase 13 template still carries a stale `Claude Opus 4.8 (1M context)` trailer — **nine** consecutive
  tasks now. The fix belongs in `.ai/generate.py`, not in any task's artifacts.
- `oidcRoundTripEndToEnd` has no suite and no owning task.
- **OQ1** — the `/login` form has no `mfaCode` field (above).
- T27-D1's focus obligation (L11) — owners are task 135 and task 174.
- L16 clause (b) is unbuilt and unowned.
- `contracts/api/token-claims.md` is wrong on `email`/`name` and silent on `amr`/`acr`.

---

**VERDICT: PASS** — the named test exists, all eleven acceptance criteria hold with four declared deviations,
no LOCKED decision is violated, no production code changed, and the suite, types, lint and a **29/29** mutation
score were re-measured in this phase, after all three adversarial reviews had been dispositioned.
