# frontend · T18 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T18 — Mismatched state discarded |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 18):**
> **Mismatched state discarded.** A callback whose `state` differs from the stored value is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

---

## Business rules

- **R8** (`requirements.md:25-26`) — "IF the callback `state` is missing **or does not match**, THEN the SPA
  SHALL discard the attempt, SHALL NOT complete the exchange, and SHALL return the visitor to the sign-in start
  with neutral copy." **T18 owns the "does not match" half.** T17 built the "missing" half and the shared
  machinery; this task supplies the comparison R8 names and nothing else.

R8 is the only requirement in scope. R7 (nonce, verifier, token handling) belongs to tasks 19–22; L16's deletion
clause belongs to task 23.

**Why this task exists at all, stated plainly:** without the comparison, any party who can cause the victim's
browser to visit `/app/callback?code=…&state=…` can have the SPA exchange a code **of the attacker's
choosing** — authorization-code injection. T17 made the callback refuse a malformed response; T18 is what makes
it refuse a *well-formed response that this tab did not ask for*. The stored state is the only evidence that the
callback belongs to a request this tab made.

---

## Locked decisions that constrain this task

| ID | Constraint on T18 |
|---|---|
| **L4** (`design.md:21-26`, `agents.md:41-43`) | Copy, redirect target, styling **and timing** identical across failures. A mismatched state must be **indistinguishable** from a missing one. This decides D-10 below. |
| **L9** | Single origin; no absolute address in any source file. |
| **L11** | One `main`, one `h1`, the notice in a live region; no default English 404. |
| **L16** (`design.md:58-60`) | sessionStorage may hold only the four listed items. T18 **reads** `state`; it adds nothing and removes nothing. |
| **agents.md:79-80** | The only sanctioned client-side storage for sensitive data is sessionStorage, for the PKCE and resume state only. |
| **agents.md:97** | Every user-facing string in the catalogue; no hardcoded copy. |

### T17's decisions that T18 inherits

| ID | Effect on T18 |
|---|---|
| **D-1, D-2** | The exchange seam exists and must stay unreached on a mismatch; assert calls with `objectContaining`, never by exact shape. |
| **D-3** | `signInNotice` is module state: every case either renders the sign-in screen or drains it in `afterEach`. |
| **D-5** | The return target is `` `/${SIGN_IN_PATH}` `` with `replace`; the leading slash is load-bearing and measured. |
| **D-7** | **A discard does not delete the stored items.** Task 23 owns deletion by name. T18 reads without consuming. |
| **D-8** | The StrictMode latch is asserted, not assumed. |
| **D-9** | Source scans on the changed files: no `localStorage`, no `history.state`, no `sessionStorage.setItem`, no absolute address, no `import.meta.env`. |
| **D-10** | `SignInNotice` was left open: T18 "may add a value for a mismatched state, or may reuse this one". **Resolved here as reuse** — L4 forbids copy that distinguishes the two cases, so a second member could only ever map to the same string. |
| **D-12** | A response carrying both `error` and `code` is discarded before any state comparison runs. |

---

## Files involved

### Existing, to extend

| File | Change |
|---|---|
| `src/auth/CallbackScreen.tsx` | the comparison at the marked line `:67`, calling the existing `discard()` |
| `src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` | **extend** — the suite is named for this task and was built to be extended |
| `src/auth/authorizeRequest.ts` | **read only** — import `AUTHORIZE_STORAGE_KEYS`; nothing in it changes |

### New

**The spec expects none.** Whether the comparison earns its own module is phase 2's call.

### A correction T18 must carry into phase 6

`CallbackScreen.tsx:43` says "Task 19 owns telling the visitor anything about the error itself." **That is
wrong, and I wrote it in T17.** Task 19 is "Nonce validated" (`tasks.md:19`). Checked: **no task displays an
authorization-server error detail at all.** The nearest is task 28, which maps a SAS refusal for an unenrolled
privileged account onto *the wrong-password copy* — i.e. generic copy, not the error. L4 forbids the
distinguishable alternative.

So the *behaviour* T17 shipped is right and is better supported than my comment claimed; only the attribution is
false. The same wrong claim appears in T17's phase 10 and 12 artifacts. **Phase 6 must correct the comment**
when it edits this file. Logged here so it cannot be lost, since those artifacts are committed history.

---

## Dependencies

| Dependency | Source | Use |
|---|---|---|
| `AUTHORIZE_STORAGE_KEYS.state` | `authorizeRequest.ts:36` (`'themistra.auth.state'`) | the key to read; imported, never re-spelled |
| `setSignInNotice` | `signInNotice.ts:17` | via the existing `discard()` |
| `exchangeAuthorizationCode` | `codeExchange.ts:18` | must stay unreached on a mismatch |
| `SIGN_IN_PATH` | `routes.tsx:13` | the return target, read inside a function only (AC12 of T17) |
| `en.signIn.discarded` | `catalogue.ts` | the neutral copy, reused unchanged |
| `globalThis.sessionStorage` | platform | the only read T18 adds |

**No config key, no contract, no backend call.** T18 touches no API; `contracts/api/*.yaml` is not in scope. The
one cross-service fact involved is auth's registered redirect URI `/app/callback`, already pinned by T17's AC17
and measured against the live stack.

---

## Acceptance criteria

| AC | Criterion | Requirement |
|---|---|---|
| **AC1** | A callback whose `state` differs from the stored value is discarded: the seam is not called, the visitor lands on `/app/sign-in`, and `signIn.discarded` is shown. | R8 |
| **AC2** | A callback carrying a `state` when **nothing is stored** is discarded. **Claimed for T18** — see Open Questions; it is the strict reading of "differs from the stored value" and the likeliest real attack shape. | R8 |
| **AC3** | A stored `state` that is the **empty string** counts as nothing stored, and the callback is discarded. | R8 |
| **AC4** | **Positive control:** when the callback `state` equals the stored value, the seam **is** called once, with the code and state. Without this, every assertion above would hold for a screen that can never exchange. | R8, D-1 |
| **AC5** | **The comparison is against the stored value, not a constant.** One case varies *only* the stored value between two otherwise identical arrivals and shows the outcome flips. This is the anti-tautology criterion: this package has shipped the "assertion compared against its own constant" defect eight times. | R8 |
| **AC6** | Mismatch is **exact**: differing by one character, by case, by surrounding whitespace in the stored value, or by being a prefix/suffix of the stored value, all discard. | R8, L4 |
| **AC7** | The copy, the landed path, the history action and the absence of a query string are **byte-identical** to T17's missing-state outcome — compared mismatch-shape to missing-shape, not each to the catalogue. | L4 |
| **AC8** | The three stored items are **still present and unchanged** after a mismatch discard. | D-7, L16 |
| **AC9** | The callback reads **the `state` key only**: `nonce` and `code_verifier` remain unread on every path. T17's blanket no-read assertion is **narrowed, not deleted**. | L16 |
| **AC10** | No `sessionStorage` key is added, and nothing is written or removed. | L16, D-9 |
| **AC11** | Under `StrictMode` the comparison runs once: one navigation on a mismatch, one seam call on a match. | D-8 |
| **AC12** | Accessibility unchanged: one `main`, one `h1`, one `role="status"` live region on both screens. | L11 |
| **AC13** | Source scans still pass on the changed file, each fed a synthetic violation. | D-9 |
| **AC14** | Whole-suite regression measured against **634/635**, the only expected failure being `lintRejectsHandWrittenBackendFetch` AC4. T17's 73 cases still pass, with AC9's narrowing the only intended change. | D-11 |
| **AC15** | **Non-vacuity sweep:** every matcher fed a deliberately wrong input, and every fix mutation-verified. | standing rule |

---

## Tests required

**Named test: `callbackWithMismatchedStateIsDiscarded`** (`package.md:112` → R8). It already exists at
`src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` with 73 cases, named for this task as much as for
T17. T18 **extends it** rather than adding a file.

**Boundary tests implied by the ACs:**

- the stored value absent, empty, and present-but-different (AC1–AC3);
- off-by-one-character, case-differing, prefix, suffix, and whitespace-padded stored values (AC6);
- the match case, as a positive control (AC4);
- the stored value varied between two identical arrivals (AC5);
- `getItem` call-key assertions proving only `state` is read (AC9);
- a mismatch under `StrictMode` (AC11);
- the mismatch outcome compared to the missing-state outcome (AC7).

**`stateSurvivesRedirectAndIsConsumedOnce`** is listed in this prompt's header but is **task 23's** by name
(`tasks.md:23`) and is **not** implemented here — the same inherited-test noise T17 logged as OQ-5, because
both named tests map to R8 (`package.md:112`, `package.md:159`).

---

## Open questions

**OQ-1 — a callback carrying a `state` when nothing is stored. CLAIMED FOR T18 (AC2), not blocking.**
Phase 0 flagged that this is owned by no task explicitly: T17 owns a callback *without* a state in the URL,
task 23 owns deletion. Claimed here on three grounds. It is the strict reading of the task statement, since a
present value does "differ from" an absent one. It is the **likeliest real-world instance of the attack R8
exists to stop** — a link sent to a victim who has no attempt in flight. And leaving it unclaimed would let a
forged callback through to the exchange, which no reading of R8 permits. If the owner prefers it elsewhere, the
change is one branch and AC2/AC3 move with it.

**OQ-2 — "single-use" versus T18's read-without-consume. Not blocking; recommend leaving it with task 23.**
L16 says the four items are "single-use and **deleted at callback**", while T17's **D-7** deferred deletion to
task 23 by name. Between T18 and task 23, a correct state therefore compares equal more than once, so the same
callback URL could be replayed. **Largely defused by the backend:** an OAuth authorization code is single-use at
the token endpoint, so a replayed callback would reach the exchange and be refused there — and until task 20 the
exchange does nothing at all. Recorded so task 23 inherits a stated position; phase 3 may overturn it.

**OQ-3 — the copy is unreviewed**, carried from T17. T18 adds no string: L4 makes a mismatch reuse
`signIn.discarded`. The owner's to reword in one file. **Gates nothing.**

**No `package.md` §11 question blocks this task.** Q2 and Q11 concern the payments contract and the payer
invoice view; neither touches R8.

---

## Next

Phase 2 — task implementation brief.
