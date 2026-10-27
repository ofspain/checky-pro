# frontend · T21 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T21 — Access token in memory only |
| **Model** | Human Approval (packet assembled by Claude Opus 5) |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |

**STATUS: FROZEN.** Downstream phases may not renegotiate this brief. Where this document and the Phase 2 brief
differ, **this one governs**.

**Task statement (verbatim from `spec/frontend/tasks.md`, task 21):**
> **Access token in memory only.** The token is held in a module-scoped variable and nowhere else. Verifies
> `callbackValidatesNonceAndKeepsAccessTokenInMemory`. Cites R7, L3.

---

## 1 · Frozen decisions

**T21-D1 · `renew()` is a throwing seam.** `LocalSession` requires it; the mechanism it names — silent
re-authentication by re-opening `/oauth2/authorize` on the SAS session cookie (`agents.md:25-34`, R21) — is
implemented by **no task in `tasks.md` 1-34**. T21 supplies a `renew()` that throws one fixed `Error` naming no
token, no status and no server detail, exactly as `codeExchange.ts` did from T17 until T20 filled it. The house
pattern, now third use. The boundary's existing failure path is unaffected: `createSessionBoundary` catches a
rejected renewal, hands each waiting call its original 401 and runs `onRenewalFailed` once
(`sessionBoundary.ts:70-77`, `:120-124`), so a seam that rejects is a *defined* outcome rather than a crash.
**Both Kimi (F1) and Phase 2's own recommendation reached this independently.** The unbuilt mechanism is recorded
in §6 as work the spec never assigned — a gap for the author, not an open question for this task.

**T21-D2 · The holder keeps the access token, as a bare string, and nothing else.** No `expires_in` — it is
dropped at `codeExchange.ts:133` and the task that builds renewal owns timing. No ID token. No scope, no token
type. The reason is not minimalism for its own sake: `LocalSession.adopt(token: string)` and
`current(): string | null` already model a bare string, and tasks 5 and 6 are built against those signatures, so
holding more would mean either widening an interface two finished tasks depend on or keeping a second shape the
consumers cannot see. (Kimi F5, accepted as recommended.)

**T21-D3 · Clearing is a separate exported function; `LocalSession` is NOT widened.** Kimi's F2 is right that the
interface has no way to clear: `adopt` takes `string`, not `string | null`. Of its two shapes, T21 takes the
first — the holder satisfies `LocalSession` *and* exports a clear of its own. Widening `adopt` would edit
`crossTabRenewal.ts`, which this brief forbids, and would give the cross-tab path a capability it has no use for.

**Clearing is in T21's scope, and AC10 is what forces it.** A tab that signs in successfully holds a token; if the
visitor then starts a second sign-in in that same tab and the callback is discarded, the first token is still held
unless something clears it. That is a half-authenticated state, which R22 forbids. So the discard path clears.

**T21-D4 · `adopt()` replaces the held token unconditionally; the last writer wins.** `shareRenewalAcrossTabs`
calls `local.adopt(outcome.token)` from its channel listener (`crossTabRenewal.ts:103`), which can fire at any
time. The callback screen's retention is just another write to the same variable. This is safe because `current()`
is read at request time and never cached (`sessionBoundary.ts:100`, `:118`, `:127`), so whichever token is current
when a request is built is the one sent. (Kimi F7, accepted as recommended.)

**T21-D5 · T21 does NOT wire the composition root.** Nothing in the app yet makes an authenticated backend call:
the first are task 30 (`/userinfo`) and task 31 (`GET /accounts/me`). Wiring `createSessionBoundary`,
`createAuthApi` and `shareRenewalAcrossTabs` into `mountShell.tsx` now would build a structure nothing consumes,
on a task whose statement is about where one token lives. **The owner of the wiring is the first task that makes
an authenticated call.** T21's conformance is proven by *isolated use of the real consumers* (AC5, AC6) rather
than by end-to-end wiring — which is the strongest claim available without a consumer. Like T21-D1's mechanism,
the wiring is assigned to no task statement; §6 records it. (Kimi F3, decided in the negative with its owner
named, as F3 required.)

**T21-D6 · Where T21 stops and task 22 begins.** T21 owns **singleness**: one copy of the token, in one module
variable, with no second home inside the app — not React state, not a prop, not a `globalThis` property, not
another module's closure, not a thrown message. Task 22 owns the **systematic storage sweep**
(`tokensAreNeverWrittenToAnyStorage`, `tasks.md:34`). T21 still asserts non-leakage, because a retention test that
did not would pass against a holder that also wrote to `localStorage` — but T21's sweep is incidental and T22's is
the exhaustive one. T21 does not pre-empt T22's named test.

**T21-D7 · Absence is the default state and survives every failure.** Phase 2's AC3 lost a clause Phase 1 had.
Restored and strengthened below: `current()` is `null` before any exchange **and after every failure path**,
including the stale-token case T21-D3 describes. (Kimi F4, accepted.)

---

## 2 · Phase 3 ledger

| # | Finding | Decision |
|---|---|---|
| F1 | `renew()` required, no task owns the mechanism | **Accepted** → T21-D1 |
| F2 | `LocalSession` cannot clear the token | **Accepted**, first shape → T21-D3 |
| F3 | The composition root is unwired | **Accepted as a question, decided in the negative** → T21-D5 |
| F4 | AC3 lost its post-failure clause | **Accepted** → T21-D7, AC3 |
| F5 | Expiry / ID-token question undecided | **Accepted as recommended** → T21-D2 |
| F6 | T20's case count is 64, not 69 | **REJECTED on the facts** — see below |
| F7 | `adopt()` precedence unstated | **Accepted as recommended** → T21-D4 |

**F6 is wrong, and the brief is right.** `artifacts/10-test-generation.md` said **64** and was accurate when
written; Phase 11 then added five cases to that file (StrictMode over the real transport, rejection after unmount,
the raw serialized body, the `access_token: null` mirror row, and the redirect-URI scan). The file holds **69**
today — verified by running it, not by arithmetic. The Phase 10 manifest is a snapshot of its own phase, not a
later count, so there is no inconsistency to fix. Kimi compared the brief against an intermediate artifact rather
than against the file.

**On F1 and F3 together.** Both name work the specification never assigned, and that is the substantive finding of
this review: **two load-bearing pieces of the auth gate have no task.** Neither blocks T21 under the decisions
above, and neither is a `package.md` §11 open question — Phase 1a's own blockers are all closed, and Q5 is where
the owner *chose* this renewal model. They are simply missing from `tasks.md`. §6 states them for the author.

---

## 3 · Scope

Unchanged from Phase 2 §Scope, with one addition to **In** — the clear function and the discard path that calls it
(T21-D3) — and one clarification to **Out**: the renewal *mechanism* stays out, while a throwing `renew()` seam is
in (T21-D1).

## 4 · Files

**Create**
- One module under `frontend/src/auth/` holding the access token. `package.md` §6 does not name it; the name is
  the implementer's.

**Modify**
- `frontend/src/auth/CallbackScreen.tsx` — retain after the nonce check passes; clear on the discard path.
- `frontend/src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` — the in-memory half, added to
  the 69 cases now in that file.

**NOT to modify**
- Anything under `spec/`.
- `frontend/src/api/sessionBoundary.ts`, `crossTabRenewal.ts`, `client.ts` — built and tested in tasks 5 and 6;
  they define the contract T21 fills. **Widening either interface is rewriting those tasks** (this is what rules
  out Kimi F2's second shape).
- `frontend/src/auth/idTokenNonce.ts` — T19 settled.
- `frontend/src/auth/codeExchange.ts` — T21-D2 means it needs no change; Phase 2's conditional is withdrawn.
- `frontend/src/app/mountShell.tsx` — T21-D5.
- `contracts/**`, `libs/ts/api-client/**` — T21 engages no endpoint.
- `frontend/.oxlintrc.json` — the module-level `let` is deliberate and is justified in source, not excused by a
  rule change.

## 5 · Acceptance criteria

**AC1** The access token from a successful exchange is retained in a module-scoped variable. · **AC2** Retention
proven over **two unrelated token values**, so nothing passes against a constant. · **AC3** `current()` is `null`
before any exchange, **and `null` again after every failure path** — a missing `state`, a mismatched `state`, a
bad nonce, a refused code, a 5xx, a dropped connection, an unusable response — **including when a token from an
earlier successful sign-in was already held** (T21-D3, T21-D7, R22). · **AC4** `current()` returns the token after
a success, and the same value on repeated reads. · **AC5** The **real** holder is accepted by the **real**
`createSessionBoundary` and its token reaches the `Authorization` header as `Bearer <token>`; conformance proven
by use, not by a type assertion. · **AC6** The real holder is accepted by the real `shareRenewalAcrossTabs`, and a
sibling's broadcast token becomes this tab's `current()`. · **AC7** `adopt()` replaces a held token and works with
none held; the last writer wins (T21-D4). · **AC8** No token value or segment reaches `sessionStorage`,
`localStorage`, IndexedDB, `document.cookie`, the URL, the DOM, the tab title or any console channel. · **AC9**
The token is held **once**: no second copy in React state, a prop, a `globalThis` property, another module's
closure, or a thrown message (T21-D6). · **AC10** A discarded callback holds nothing, and clears anything held. ·
**AC11** The three L16 items are unaffected; T21 writes, deletes and clears nothing in any storage. · **AC12** No
new user-visible string and no catalogue key. · **AC13** No `import.meta.env`, no absolute address, no new
dependency, no state library. · **AC14** T19's nonce check and R8's ordering unchanged; a token is held only after
both pass. · **AC15** The suite passes with the one known environmental failure and no other, **verified by the
run's exit code**. · **AC16 — new.** `renew()` rejects with one fixed message carrying no token, no status and no
server detail, and the holder is still accepted by `createSessionBoundary`, whose `onRenewalFailed` runs exactly
once for that rejection (T21-D1). · **AC17 — new.** The holder exports a clear, `LocalSession` is **not** widened,
and `crossTabRenewal.ts` is byte-identical to its committed state (T21-D3).

## 6 · Work the specification never assigned

Neither item blocks T21, and neither is a `package.md` §11 question. Both need an author decision.

1. **No task implements silent re-authentication.** Task 5 built single-flight 401 handling, task 6 cross-tab
   sharing, task 33 the *failed*-renewal route — all three against an injected session. The mechanism the owner
   chose in Q5 is built by nothing. T21 ships a seam for it (T21-D1).
2. **No task wires the composition root.** `createSessionBoundary`, `createAuthApi` and `shareRenewalAcrossTabs`
   are built, tested and unreferenced in production. T21-D5 assigns the wiring to the first task that makes an
   authenticated call (task 30 or 31), but no task statement says so.

## 7 · Required tests, constraints

Phase 2 §Required Tests and §Constraints carry over in full, extended by AC16 and AC17. Added by this phase: a
case proving `onRenewalFailed` runs exactly once when the seam rejects through the real boundary; a case proving
`adopt()` wins when it runs after retention and loses when it runs before; and the stale-token case in AC3. Every
block carries a control proving its assertion is reached. AC15 stands: the **exit code** is the verdict.

## 8 · Open questions

**None.** OQ-1 → T21-D6. OQ-2 → T21-D1. OQ-3 → T21-D5. OQ-4 → T21-D2. The two unassigned-task gaps in §6 are
recorded for the author and gate nothing.

**Scope, files and acceptance criteria are final.**

---

## Next — Phase 5 — implementation plan.
