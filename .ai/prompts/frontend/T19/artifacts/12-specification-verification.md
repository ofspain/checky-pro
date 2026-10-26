# frontend · T19 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T19 — Nonce validated |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/11-test-review.md` and every prior artifact |
| **Produces** | `artifacts/12-specification-verification.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 19):**
> **Nonce validated.** The ID token's `nonce` must equal the stored value, or the exchange is rejected. Verifies `callbackValidatesNonce`. Cites R7.

---

## Part 1 — Resolution of the Phase 11 test review

Twelve gaps, none rated high by the reviewer. **Nine accepted, two rejected on the source, one accepted only after
correcting what it asked for.** The suite went from 97 cases to **123**, and the mutant set from 15 to 19.

| Gap | Disposition | What was done, or why not |
|---|---|---|
| 1 · no `alg:'none'` case | **CORRECTED, then accepted** | The review asked for a case asserting **rejection**, and gave its own expected result as `malformed-token` — which is wrong: a three-segment token with a valid payload and a matching nonce is accepted, header regardless. Demanding rejection would have required reading `alg`, a check this design omits deliberately: reading it buys nothing while no signature is verified at all (OIDC Core §3.1.3.7). The honest case **accepts**, pinning the header as unread — `callbackValidatesNonce.test.tsx:445-470`. Rejecting a two-**segment** token is a different matter and was already covered (`:357`): that shape has no signature slot. |
| 2 · signature not pinned as ignored | **ACCEPTED** | `tokenWith` takes a signature override; a different and an empty signature are both accepted, each with a mismatch control — `:445-470`. |
| 3 · `tokenWith` self-untested | **REJECTED, with a narrower piece accepted** | The premise is false: the positive control at `:209` asserts `{ ok: true }` for a minted token, so an encoding bug in the helper fails it — and every other accept case. The suite is not blind to it. What was genuinely missing is that every fixture came from this file, so a self-consistent helper could agree with a shape no server emits. Closed with a **real ID token minted by the running authorization server**, nonce and all — `:479-509` — and the helper held against its shape rather than round-tripped through its own encoder. |
| 4 · the seam is not asserted called | **ACCEPTED** | `arriveWithToken` now exposes the mock; one case asserts a single call carrying this callback's `code` and `state`, another asserts it is never reached when the state did not match — `:664-683`. The review called it the most substantive gap; it is covered transitively (a screen that skipped the exchange fails the rejection cases) but the explicit guard is right, because this file's harness is what would hide a drift. |
| 5 · module-variable retention | **ACCEPTED** | Source scan over both modules forbidding a top-level `let`/`var`, with a control proving it catches one and allows a function-scoped one — `:903-918`. |
| 6 · `includes\(` too narrow | **ACCEPTED** | Correct as stated: `x['includes'](y)` and `String.prototype.includes.call` both slip past. Broadened to the bare word, and the negative control now uses the computed form — `:836`, `:863`. |
| 7 · no `Infinity` rows | **CORRECTED, then accepted** | Adding them would have added two more aliases of the `null` row: `JSON.stringify` folds `NaN` and both infinities to `null`. Worse, the finding exposed one of my own: the row labelled *"a number that reads like the stored value"* was passing `Number.NaN`, which reads like nothing and arrives as `null` — **a row testing a neighbouring row while claiming to test a shape JSON cannot carry.** Replaced by the case that label described — a numeric claim whose text is exactly the stored value, which `==` would accept — plus a case pinning the JSON fold so the absence is documented, not forgotten (`:333-354`). |
| 8 · storage sweep could be vacuous | **ACCEPTED** | The sweep now asserts a seeded key is present before asserting the token's absence — `:752-756`. |
| 9 · no scan for logging | **ACCEPTED** | `console.`/`debugger` scan over both modules, with a control — `:889-901`. |
| 10 · assert the 43-character shape | **REJECTED** | It tests `pkce.ts`'s contract, which has its own suite, from a file that owns the nonce comparison. And it is already caught here: a generator returning a short or empty value fails the AC6 row that stores it. Asserting another module's constant here adds coupling and no coverage. |
| 11 · the reason-leak scan misses the console | **ACCEPTED** | Runtime sweep over `log/info/warn/error/debug` across a whole rejected arrival, covering the router and i18n too — `:790-807`. |
| 12 · no "nonce equals the stored state" case | **REJECTED** | It is already there, at `:275-287`: `state` and `codeVerifier` are both seeded with the value the token claims while the nonce holds something else, so a read of the wrong key **matches** — the only way that case can fail. Strictly stronger than what the review proposed, since it covers the verifier too. |

### A finding of my own, and a correction to the Phase 10 report

Re-running the suite surfaced an **unhandled rejection** that Phase 10 had already produced and that Phase 10's
artifact did not mention: the AC10 case which makes `setSignInNotice` throw leaves the promise derived from
`.then(onFulfilled, discard)` rejected with nothing to handle it. Vitest counts that as an error and **fails the
run's exit code even though every case passes**. Phase 10 reported the run as clean; it was exiting 1, for two
reasons rather than the one known environmental failure I named.

Production is unchanged, and deliberately. A throw out of `discard` has nowhere good to go: `navigate` is what
would have failed, and the notice is what would have failed, so there is nothing left to tell the visitor with.
The alternative — a trailing `.catch(discard)` — runs the one outcome **twice**, which is the defect Phase 9
fixed on both reviews' advice. What changed is the harness: for that one case it keeps the derived promise and
handles it (`:111-135`), and a source scan now forbids a trailing `.catch(` on the call site (`:921-932`) so the
two-argument form cannot be tidied away.

---

## Part 2 — Traceability matrix

`N` = `frontend/src/auth/idTokenNonce.ts` · `C` = `frontend/src/auth/CallbackScreen.tsx` ·
`T` = `frontend/src/auth/callbackValidatesNonce.test.tsx` · `T18` = `callbackWithMismatchedStateIsDiscarded.test.tsx`

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R7** — validate `nonce` on a state-matched callback | **Yes**, the nonce clause only | `N:136` compares; `C:124` acts on it | `T:209`, `T:588`, `T:596` | The exchange at `/oauth2/token` and the in-memory access token are **tasks 20–22**, named as such in `tasks.md` | No |
| **R8** — a bad `state` discards before anything else | **Unchanged**, re-asserted | `C:107-110` precedes `C:116` | `T:637`, `T:675`, `T18:698` | No | No |
| **AC1** — a match is accepted and the flow continues | Yes | `N:136` | `T:209`, `T:588` | No | No |
| **AC2** — any difference is rejected | Yes | `N:136` | `T:214-234` (10 rows: character, case, padding, prefix, suffix, empty) | No | No |
| **AC3** — an absent claim is rejected | Yes | `N:126` | `T:292`, `T:604` | No | No |
| **AC4** — a non-string claim is rejected, not coerced | Yes | `N:131` | `T:300-316` (7 rows), `T:318` coercion premise, `T:333` numeric text, `T:344` the JSON fold | No | No |
| **AC5** — the comparison is exact | Yes | `N:136`, strict `===` | `T:236-246` (the stored side is not trimmed either), `T:847` eight source scans each with a control at `T:859` | No | No |
| **AC6** — bound to the stored value, no shared literal | Yes | `N:97`, `N:136` | `T:255-273`: four rows sharing no literal, each flipping the store and requiring the outcome to flip | No | No |
| **AC7** — reads `AUTHORIZE_STORAGE_KEYS.nonce` | Yes | `N:97` | `T:275` (both other keys hold the claimed value, so a wrong read matches), `T:934` | No | No |
| **AC8** — nothing stored, or empty, rejects | Yes | `N:101` | `T:512`, `T:519` | No | No |
| **AC9** — a throwing read is "nothing stored" | Yes | `N:96-100` | `T:529` | No | No |
| **AC10** — indistinguishable from R8's discard | Yes | one `discard` closure, `C:63-68` | `T:620` compares text, path, query and history action byte for byte | No | Network timing excluded by `agents.md:44`, as the frozen brief states |
| **AC11** — no token retained anywhere T19 controls | Yes | no module state in `N` or `C`; nothing written | `T:741` storage and URL (with a non-vacuity control), `T:790` the console, `T:895`/`T:910` the two scans | No | Task 20's transport and task 21's hold answer for their own, per the brief |
| **AC12** — writes, deletes and clears nothing | Yes | `N` and `C` call no mutator | `T:557-577` both paths, `T:686` through the screen | No | No |
| **AC13** — no new user-visible string | Yes | `signIn.discarded` reused, `catalogue.ts:18` | `T:809` pins both key sets exactly | No | No |
| **AC14** — no address, no env read, no new dependency | Yes | scans at `T:836`; `frontend/package.json` untouched across all six T19 commits | `T:847` | No | No |
| **AC15** — R8 holds before any nonce read | Yes | `C:107-110` before `C:116`; `C:121-123` says it may not be hoisted | `T:637` and its ordering control `T:646`; `T:542`/`T:548` the same property inside the module | No | No |
| **AC16** — T17/T18 assertions split, not rewritten | Yes | `T18:698` keeps the no-nonce-read assertion on the discard row; `T18:716` is the new match row | `T18`, 134 cases | No | No |
| **AC17** — the suite passes but for the one known failure | **Yes, now** | `vitest run`: **818 passed / 1 failed of 819** | the one failure is `lintRejectsHandWrittenBackendFetch` AC4 | No | The unhandled rejection described in Part 1 was a **second** cause of a failing run in Phase 10. It is gone: exit 0 for the named test file, and the only remaining failure is the environmental one |
| **AC18** — a malformed token is rejected, never a throw | Yes | `N:58`, `N:65`, `N:77` | `T:357-379` (10 shapes), `T:381-399` (6 non-string inputs, each asserted `not.toThrow`) | No | No |
| **AC19** — payload only; no claim but `nonce` | Yes | nothing reads `segments[0]` or `segments[2]` | `T:445-470` header and signature accepted unread; `T:875` seven other claim names absent from the source | No | No |

### Named tests

`package.md:175` maps **`callbackValidatesNonce` → R7**. The file exists at
`frontend/src/auth/callbackValidatesNonce.test.tsx`, 123 cases, all passing. The other three names in this task's
prompt header belong to tasks 20–22 (`callbackValidatesNonceAndKeepsAccessTokenInMemory`,
`tokensAreNeverWrittenToAnyStorage`) and task 24 (`returnTargetOutsideAllowlistFallsBackToHome`); none is T19's.

### Mutation

**19 mutants, 19 killed, no survivors** (baseline 0 failed of 257 collected across both suites). The four added
this phase for the new guards — a header read insisting on RS256, a module variable retaining the token, a log of
the claim, a log of the reason — are killed at 7, 1, 1 and 2 failures. M13, the revert of the two-argument `.then`,
now dies twice over: behaviourally and by the new source scan.

---

## Part 3 — The approving engineer's answers

**1 · Is the task fully complete?** **Yes.** R7's nonce clause is implemented in a module of its own, acted on at
the one call site, and verified by a named test of 123 cases with a 19-of-19 mutation score. The exchange and the
access-token hold are tasks 20–22 by `tasks.md`'s own division, not omissions here.

**2 · Does it satisfy every acceptance criterion?** **Yes, all nineteen.** AC17 is satisfied as of this phase; in
Phase 10 it was not, for the reason given in Part 1.

**3 · Does it violate any LOCKED decision?** **No.** L1 (the SPA is the OIDC client and does its own validation) is
what this task implements. L4 (no copy distinguishing failures) is held by construction — four faults share one
`discard` closure — and asserted three ways: rendered text, the tab title, and now the console. L9 (one origin at
the edge) and L10 (no bundled issuer) are untouched: no address and no `import.meta.env` read, both scanned. L13
(no JWT library) holds — the payload is decoded with platform primitives and the signature is not verified, which
OIDC Core §3.1.3.7 permits for a token taken directly from the token endpoint. L16's three stored items all
survive both paths. **L6** (no hand-written backend fetch) is not reached: T19 adds no transport.

**4 · Remaining risks.**

- **The environmental `oxlint` failure.** `lintRejectsHandWrittenBackendFetch` AC4 expects the summary line
  `Found 0 warnings and 0 errors`; this host's `oxlint@1.87.0` prints nothing at all, so stdout is empty. `oxlint`
  itself exits 0 over `src/`. **Unverified on CI** — the frozen brief's carry-forward from T18's Gap 6 asks for CI
  to enforce both the suite total and this failure's identity, and it is still open. It is the one thing in this
  package whose status I am asserting from one host.
- **AC2 of the same test** fails under a bare `pnpm` spawn (`spawn pnpm ENOENT`) and passes under
  `npx -y pnpm@12.14.0`. **These are two different faults** and have been conflated four times in review; they are
  separate here for the fifth.
- **The suite total is prose.** 819 is asserted in artifacts, not in CI. A deleted test file would not be noticed.
- **`refresh_token`'s absence is pinned by a comment**, `codeExchange.ts:19-25`, not by a test — Kimi's I5 from
  Phase 8, carried to **task 20**, which performs the real exchange and can assert it.
- **Two documented facts are measured FALSE and neither is T19's to fix.** `contracts/api/token-claims.md` says
  email and name arrive in the `id_token` or from `/userinfo`; the id_token carries neither and `GET /userinfo`
  returns only `{"sub"}`. Discovery advertises `scopes_supported: ["openid"]` for a three-scope client. Owner:
  whoever owns `contracts/`.
- **The real-token fixture will age.** It is a development-key token, long expired, and nothing reads its `exp` —
  by AC19, deliberately. If task 20 or a later task starts validating `exp`, that fixture must be re-minted or
  moved behind a clock stub. Flagged here so it is not discovered as a mystery failure.

### Open questions carried forward

- **OQ-2** — whether to verify the signature. Answered within T19 by OIDC Core §3.1.3.7 and now pinned by tests
  that accept a token differing only in header and signature. Revisit only if a token ever reaches the SPA by a
  route other than the token endpoint.
- **OQ-3** — should a rejected token be revoked at `/oauth2/revoke`? **Owner: the author.** Not blocking.
- **OQ-4** — the ID token has no contract document. **Owner: `contracts/`.**
- **OQ-5** — L6 versus the ungenerated `/oauth2/token`, which is not in `auth.yaml` (`design.md:133`: OIDC
  endpoints are SAS-owned) while `.oxlintrc.json` bans `fetch` through every global alias. **Owner: task 20.**
- Long-standing, gating nothing: T16's OQ-1 and T12's OQ-1 (unreviewed copy); `design.md` §4c's stale
  "PROPOSED, pending sign-off (Q6)" heading and its "auth T19 is not built" line, now false; `package.md:172`
  mapping `serviceWorkerUpdateWaitsForReload` to R57.

---

**Verdict: PASS** — R7's nonce clause is implemented, exact, bound to storage, indistinguishable in failure, and
verified by 123 cases and 19 of 19 killed mutants, with the only suite failure the known environmental `oxlint`
summary-line fault.

## Next — Phase 13 — PR preparation.
