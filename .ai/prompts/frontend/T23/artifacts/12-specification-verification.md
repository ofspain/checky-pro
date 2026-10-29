# frontend · T23 · Phase 12 — Specification Verification

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T23 — PKCE state single-use |
| **Model** | Claude Opus 5, as approving principal engineer |
| **Consumes** | every prior artifact |
| **Produces** | `artifacts/12-specification-verification.md` |

**Task statement** (`spec/frontend/tasks.md:35`): *`state`, `nonce`, and `code_verifier` survive the full-page
redirect in session storage and are deleted at callback. Verifies `stateSurvivesRedirectAndIsConsumedOnce`.
Cites R8, L16.*

---

## 1 · Traceability matrix

| Requirement | Implemented? | Evidence (`file:line`) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R8** callback `state` missing or mismatched → discard, no exchange, back to sign-in with neutral copy (`requirements.md:25-26`) | **Yes**, T17/T18's, unchanged by T23 | `CallbackScreen.tsx:122-125` (the comparison and the early return); `:73-83` (the one `discard` closure) | `stateSurvives…:535-603` seven discard rows + the public-route case; T18's own file entire | no | **none** |
| **R8** "single-use" as the spec's reason for deleting the state | **Yes** | `authorizeRequest.ts:120-128`; called at `CallbackScreen.tsx:165` | `stateSurvives…:460-487` replay refused with no network call, **plus its cause control** | no | **declared, see §2.1** — single-use is enforced on the consumed path only (T23-D2) |
| **L16(a)** sessionStorage may hold `state`, `nonce`, `code_verifier` and the return target, **single-use and deleted at callback** (`design.md:58-60`) | **Yes** for the three that exist | the three keys at `authorizeRequest.ts:35-39`; the deletion iterating them at `:121` | `stateSurvives…:406-456` consumption with T23-D13's presence control; `:736+` five isolation cases | the **return target** does not exist yet — task 24 (T23-D6) | **declared, see §2.1** |
| **L16** "nothing else is stored client-side" | **Yes**, unchanged | one writer, one deleter, both in `authorizeRequest.ts` | T22's allowlist row (`setItem`, `removeItem`) and its package-wide scan; `stateSurvives…` key-set pin | no | none |
| **tasks.md:35** the three **survive the full-page redirect** | **Yes**, structurally | `sessionStorage` is the store, by T15's design | `stateSurvives…:357-404`: state and nonce travel in the authorize URL, the verifier travels **nowhere**; the three read back through a rebuilt module graph | a **real** browser redirect is unobservable here | **declared, see §2.2** (T23-D5) |
| **tasks.md:35** named test `stateSurvivesRedirectAndIsConsumedOnce` | **Yes** | `frontend/src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx`, 29 cases | itself | no | none |
| **agents.md** no token in storage, URL or log | **Yes**, unchanged by T23 | T22's instruments still green | T22's 47 cases, four of them updated for this task | no | none |
| **L4** no copy distinguishes one failure from another | **Yes** | T23 adds nothing to any failure path | `stateSurvives…:489-508` full `outcomeOf` equality, replay vs. mismatch, with the copy asserted present | no | none |
| **R22** no half-authenticated tab | **Yes**, unchanged | `clearAccessToken()` in `discard` (`CallbackScreen.tsx:78`) | the replay case asserts the holder is null afterwards | no | none — but see §4, risk 1 |

### Acceptance criteria

All fourteen verified; the five that Phase 9 recorded as unverified (**AC5, AC6, AC7, AC8, AC10**) each have their
own case in the named test, and **none of them is satisfied by an inherited assertion**, which is what R-I5
required of this phase. Phase 10 §2 holds the case-by-case mapping; Phase 11 added three more cases and one
control. AC13 is read **by exit code**: `vitest` exit 1 with `1008 passed of 1009`, the single failure being the
known environmental `oxlint`-summary case; `tsc --noEmit` exit 0.

## 2 · Declared deviations

### 2.1 · L16 says "deleted at callback"; T23 deletes on the **consumed** path only

`design.md:58-60` calls the items "single-use and deleted at callback" without qualifying which callbacks. T23
deletes only when a callback has used all three (**T23-D2**). The argument, which the frozen brief made before any
code was written and which the test file now carries:

- `/app/callback` is a **public route**. A deleting discard would mean anyone who can get a visitor to open
  `/app/callback?state=anything` ends that tab's pending sign-in — a denial of service on every sign-in in
  progress, triggered by a link. `stateSurvives…:580-602` is that case, and it shows the real callback still
  completing afterwards.
- The replay that single-use defends against is a callback that **succeeded**, and that one is refused with no
  network call at all (`:460-487`).
- A callback whose exchange failed can be retried with the same `state`. The authorization code behind it is
  already spent server-side — `400 invalid_grant`, measured in T20 — so the retry fails at the token endpoint
  rather than succeeding twice.

**This is narrower than L16's sentence, and it is the owner's to overturn, not mine.** The frozen brief §8 says so
and prices it: the strict reading is a one-line move of the call site plus the seven AC4 rows. I am recording it as
a deviation rather than reading L16 as if it already said "consumed", because a future reader comparing the two
should find the difference stated.

### 2.2 · "Survive the full-page redirect" is verified structurally, not end to end

jsdom performs no document navigation, so no test in this repository can watch the real round trip through the
authorization server (**T23-D5**). What is verified instead is the mechanism that makes the survival necessary and
the nearest observable consequence: `state` and `nonce` travel in the authorize URL and come back from the server
while the verifier travels nowhere, so after a real redirect the store is the only place it could come from; and
the three read back through a module graph that no longer shares an instance. **No task in this package owns a
real-browser end-to-end check** — a carry-forward, not a T23 omission.

## 3 · The four principal-engineer questions

**(1) Is the task fully complete?** Yes, for the scope the spec gives task 23. The three items are written by one
module, deleted by one function in that same module, called from one place in the package — proven by a
package-wide scan, not by reading the one file expected to hold the call — after every consumer has read. The
fourth L16 item is task 24's by name and does not exist; `tasks.md:36-37` assigns it there.

**(2) Does it satisfy every acceptance criterion?** Yes, all fourteen, with the two declared deviations above
stated against AC1 and the R8/L16 rows rather than quietly absorbed.

**(3) Does it violate any LOCKED decision?** **No.** L16 is honoured for every item that exists; §2.1 is a
narrowing of *when* the deletion fires, decided and argued in the frozen brief, not a decision to keep something
L16 forbids. L4 is unaffected — T23 adds no copy. L3 and L1 are untouched: no token moved. L6 is untouched; the
`.oxlintrc.json` stays as T22-D12 left it.

**(4) Remaining risks.**

| Risk | Severity | Status |
|---|---|---|
| A replayed callback clears a token the tab legitimately holds (`CallbackScreen.tsx:78`) | Low | Pre-existing in T17's discard design; the outcome is unchanged from before T23, which reached it via `400 invalid_grant` instead. **T23-D16** hands task 24 the fix: navigate away from `/app/callback` once the token is held. |
| Task 24 adding its return target to `AUTHORIZE_STORAGE_KEYS` would enrol it in the deletion | Medium → Low | Now a **failing test with a message that says what to decide** (the key-set pin, R-I1), plus Gap 5's handoff sentence. |
| A store that denies *access* rather than removal | Low | The guard covers it by shape; a jsdom store cannot deny access, so it is unprovable here. Stated in the test header. |
| No real-browser check | Medium | Unowned by any task — the single largest verification gap in this package, carried forward since T20. |
| The suite's green depends on one environmental failure being recognised by hand | Low | T18 Gap 6, still open: CI enforces neither the suite total nor the identity of the `oxlint` failure. |

## 4 · Evidence of rigour, for the record

29 cases in the named test; **16 of 16 mutants killed** on ordering and scope, with one survivor (M16) excluded
because it is semantically identical and argued in both the manifest and the test file; four of T22's assertions,
two of T18's and one of T21's inverted in the same commit as the production change, and **four more deliberately
left alone** because they assert the purity T23-D1 depends on. Nine review findings from Kimi's Phase 8 and six
gaps from its Phase 11 were accepted, each one checked against source first; two of its claims were corrected on
the facts and the corrections are in the artifacts.

---

## **VERDICT: PASS**

**Reason:** R8's single-use and L16's three items are implemented, deleted exactly once from one call site after
every consumer has read, and verified by 29 cases and a 16/16 ordering-and-scope mutation score — with the two
limits of that claim (deletion on the consumed path only, and a structurally rather than end-to-end verified
redirect) declared as deviations rather than absorbed.

## Next — Phase 13 — PR preparation.
