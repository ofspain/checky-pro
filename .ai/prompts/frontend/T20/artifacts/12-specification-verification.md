# frontend · T20 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T20 — Code exchange sends the verifier |
| **Model** | Claude Opus 5 |
| **Consumes** | every prior artifact, the implementation and the tests |
| **Produces** | `artifacts/12-specification-verification.md` |

Every `file:line` below was read before it was written down.

---

## 1 · Phase 11 ledger — all seven gaps accepted

Kimi reviewed the 64-case suite statically (its environment lacks `vite-plugin-pwa`, so it could not run the
suite; it said so). All seven gaps were real and all seven were implemented.

| # | Gap | Where it landed |
|---|---|---|
| 1 | StrictMode never run against the real exchange | `T:778` — one request, over a real transport |
| 2 | The `===` content-type trap never demonstrated | `generatedClientCompiles.test.ts:138` — a charset'd header provably yields JSON |
| 3 | Nothing banned middleware on the OIDC client | `T:902` — no `.use(`, no `middleware`, no `sessionBoundary`, no `createAuthClient` |
| 4 | `access_token: null` missing from the matrix | the mirror row in `FAULTS` |
| 5 | Rejection after unmount uncovered | `T:812` |
| 6 | The serialized body only compared parsed | `T:439` — the raw bytes, in order |
| 7 | AC6's name overclaimed | `T:459` renamed, plus URL userinfo assertions |

**Gap 3 is the most valuable of the seven.** It turns Phase 7's F4 — the one finding whose cost is highest if
learned late — from a note in an artifact into a test. Mutants M22 and M23 confirm it bites: attaching middleware,
or swapping in the auth-service client, is **behaviourally invisible** (both still POST the same bytes to the same
path), so that scan is the only thing in the suite that can catch either.

**Gap 5's probe found a defect in my own tests.** Three `takeSignInNotice()` assertions were **inert**: `arriveAt`
imports the route graph dynamically and `vi.resetModules()` runs between cases, so the screens hold a *different
instance* of `signInNotice.ts` than the test file does. Those assertions would have passed whatever production
did. They are removed, and the notice is now proven where a visitor would see it — the rendered page
(`T:724`, asserting `en.signIn.discarded` in the discard outcome).

**Gap 5 also recorded a real behaviour rather than tidying it.** When the exchange rejects *after* the screen
unmounts, `discard` still runs: the visitor is pulled to the sign-in start. T20 did not introduce this — before
task 20 the seam rejected unconditionally, so it was the *only* path through it — and where a late failure should
land a visitor is task 24's question, since it owns the return target. Pinned at `T:812`, not changed.

## 2 · Traceability matrix

Key: `T` = `frontend/src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx`,
`E` = `frontend/src/auth/codeExchange.ts`, `G` = `frontend/src/api/generatedClientCompiles.test.ts`.

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R7** · exchange the code at `/oauth2/token` with the PKCE verifier | Yes | `E:113` the call, `E:125` the verifier, `E:105` the refusal without one | `T:254`, `T:349`, `T:384` | No | No |
| **R7** · validate `nonce` | Yes (T19), intact over a real transport | `CallbackScreen.tsx:124` | `T:713` | No | No |
| **R7** · hold the access token in memory only | **Out of scope — task 21.** T20 returns it and stores nothing. | `E:133`, `E:35` | `T:635` (nothing written), `T:756` (nowhere visible) | No | No — `tasks.md` assigns it to 21 |
| **R8** · a bad `state` completes no exchange | Yes, and newly falsifiable | `CallbackScreen.tsx:107-110` | `T:658` ×4 + its control | No | No |
| **L1** public client, PKCE, exact-match redirect | Yes | `E:123` one derivation | `T:424`, `T:459`, `T:884` | No | No |
| **L3** tokens never stored; no refresh token | Yes | `E:35`, contract defines none | `T:620`, `T:635`, `T:756` | No | No |
| **L4** the visitor learns nothing | Yes | `E:91` one fixed message, contract's 400 has no schema | `T:487` ×20, `T:724` | No | No |
| **L6** generated client, no hand-written call | Yes | `oidc.yaml`, `index.ts:23`, `E:1` | `T:840`-block, `G:90`, `oxlint` 0 | No | No — satisfied as written (T20-D1) |
| **L9** single origin, relative paths | Yes | `E:113` relative, `index.ts:23` no base URL | `T:349`, `G:110` | No | No |
| **L10** no secret, no non-public env key | Yes | `E` names neither | `T:840`-block | No | No |
| **L11** catalogue-only strings | Yes | no catalogue key added | `T:724` reuses `signIn.discarded` | No | No |
| **L13** no new runtime dependency | Yes | `package.json` unchanged but for the `generate` script | `G:73` | No | No |
| **L14** SPA routes under `/app` | Yes | `/oauth2/token` is a backend path, not a route | `T:349` | No | No |
| **L16** the three items survive | Yes | `E` reads one, writes none | `T:635` ×2 | No | No |
| **L17** API routes NetworkOnly | Yes, by construction | `apiRequestPolicy.ts:25,76-78` | `serviceWorkerNeverCachesApiResponses.test.ts:169-175` | No | No |
| **L18** one renewal per tab; reusable exchange | Yes | `E:103` callable anywhere; `T20-D8` | `T:902` (no middleware), every direct case | No | No |

## 3 · Acceptance criteria

| AC | Verdict | Evidence |
|---|---|---|
| AC1 stored verifier travels | **PASS** | `T:254` |
| AC2 two unrelated values | **PASS** | `T:265` ×2 — mutant M1 killed |
| AC3 POST, relative path | **PASS** | `T:349` — M19, M20 killed |
| AC4 form body, exactly five fields | **PASS** | `T:384`, `T:399`, `T:439` raw bytes — M7, M9, M24 killed |
| AC5 `redirect_uri` identical, derived once | **PASS** | `T:424` against the real authorize request, `T:884` structural — M10 killed |
| AC6 no SPA-added credential | **PASS** | `T:459` |
| AC7 through the generated client | **PASS** | `T:840`-block, `oxlint` exit 0 — M22, M23 killed |
| AC8 the admitted code | **PASS** | `T:658` ×4 + control — M21 killed |
| AC9 nothing written/deleted/cleared | **PASS** | `T:635` ×2 — M17 killed |
| AC10 no token anywhere visible | **PASS** | `T:756` — M16, M18 killed |
| AC11 one outcome per failure | **PASS** | `T:487` ×20, `T:724` — M15 killed |
| AC12 bad tokens reject | **PASS** | the matrix, both tokens — M11, M12, M13 killed |
| AC13 no refresh token | **PASS** | `T:620`, the scan, the contract — three pins |
| AC14 the nonce check still runs | **PASS** | `T:713` |
| AC15 no new visible string | **PASS** | catalogue untouched |
| AC16 no env key, no absolute address | **PASS** | `T:840`-block, each scan self-proving |
| AC17 exit code, not counts | **PASS** | below |
| AC18 callable outside the screen | **PASS** | every direct case; T20-D8 |
| AC19 `/oauth2/token` NetworkOnly | **PASS** | pre-existing case cited, no duplicate written |
| AC20 no server detail escapes | **PASS** | `T:561` and `T:576` |
| AC21 guarded storage read | **PASS** | `T:303` — M5 killed, after surviving once |
| AC22 the client form-encodes | **PASS** | `G:110`, and `G:138` proves the trap |
| AC23 `oidc.ts` drift | **PASS** | `G:90`, `G:103` |

**23 of 23.**

## 4 · The approving principal engineer's answers

**(1) Is the task fully complete?** Yes. The task statement has two halves — the request carries the stored
verifier, and the named test verifies it — and both are built. The exchange is real, the verifier is on the wire,
every failure has one outcome, and the named test is 69 cases. What the named test does *not* yet contain is task
21's half (the access token in memory); the file is shared by design and `tasks.md` assigns that half to 21.

**(2) Does it satisfy every acceptance criterion?** Yes, 23 of 23, each with evidence above. Thirteen of them are
additionally pinned by a killed mutant.

**(3) Does it violate any LOCKED decision?** No. L6 was the task's one blocker and it is satisfied **as written**:
`contracts/api/*.yaml` is a glob, the token endpoint now has a contract under that glob, and the call goes through
a client generated from it. No ADR was needed, no second exception to L6 was created, no lint suppression exists,
and `auth.yaml`'s scope note is untouched — so `package.md` Q12(c) still stands.

**(4) Remaining risks.**

- **The contract is the first frontend-authored file under `contracts/`**, which `.github/CODEOWNERS` maps to
  `@ofspain`. It needs owner review like any other contract. Flagged, not a defect.
- **`expires_in` is dropped at the boundary** and L18's silent renewal will need it. Task 21's, recorded in
  Phase 7 F3 and carried into Phase 13.
- **The OIDC client must never carry the session boundary's renewal middleware**, or a failed exchange triggers a
  renewal that performs another exchange. Now enforced by `T:902`, but task 21 is the task that will be tempted.
- **`fetch` sends same-origin cookies**, so the httpOnly SAS session cookie travels with the token request. It is
  a user session rather than a client credential, the server already holds it, and the SPA cannot read it — but
  the request does not strictly need it, and `credentials: 'omit'` would be a defensible tightening. Out of scope
  here: the brief is frozen and names no AC for it. Carried to Phase 13.
- **The known environmental `oxlint` failure** is still asserted from a single host. Unchanged from T18 Gap 6 and
  still the one claim in this package resting on one machine.

## 5 · Verification evidence

- Whole suite: **892 passed / 1 failed of 893**. The failure is `lintRejectsHandWrittenBackendFetch` AC4 at
  `:113` — this host's `oxlint@1.87.0` prints no summary line — **and no other**, read from the failure list.
- `vitest` exit **1**: that single failure, and **no `Errors` line**, so no unhandled rejection. AC17 exists
  because T19 found an unhandled rejection reported *below* a line saying every test passed.
- `tsc --noEmit` clean for `frontend` and for `libs/ts/api-client`. `oxlint` exit **0**.
- **Mutation: 24 mutants, 24 killed, no survivors**, baseline 0 failed of 326. Two of the 24 exist because they
  survived a first run (M5) or because no behavioural test could ever catch them (M10, M22, M23).
- The harness was **corrected mid-phase**: a mutant whose run failed to parse was being scored `KILLED` because
  its total differed from the baseline. It now scores `BROKEN` and reports as a non-kill. One mutant (M22) had
  been flattered by that bug and is genuinely killed after the fix.

---

## Verdict — **PASS**

The token request carries the stored verifier through a client generated from a contract, 23 of 23 acceptance
criteria hold with evidence, no LOCKED decision is violated, and 24 of 24 mutants die.
