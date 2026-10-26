# frontend · T18 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T18 — Mismatched state discarded |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

## Task

Implement R8's second half: a callback whose `state` does not match the value stored when the authorization
request was built is discarded, with no exchange, returning the visitor to the sign-in start with neutral copy.
Verifies `callbackWithMismatchedStateIsDiscarded`.

## Purpose

The stored `state` is the only evidence that a callback belongs to a request **this tab** made. Without the
comparison, anyone who can cause the victim's browser to open `/app/callback?code=…&state=…` can have the SPA
exchange an authorization code of their choosing — authorization-code injection. T17 made the callback refuse a
*malformed* response; T18 makes it refuse a **well-formed response that this tab did not ask for**.

## Scope

**In** — reading the stored `state`; comparing it to the callback's; discarding on any mismatch, including when
nothing is stored; extending the existing suite.

**Out** — deleting or otherwise consuming the stored items (task 23); the nonce (task 19); the token exchange
and the verifier (task 20); anything displayed about an authorization-server `error` beyond T17's existing
discard (no task owns it; L4 forbids the distinguishable alternative).

## Business Rules

- **R8** — IF the callback `state` is missing **or does not match**, THEN discard the attempt, do not complete
  the exchange, and return the visitor to the sign-in start with neutral copy. T18 owns "does not match".

## Locked Decisions

**Package:** **L4** (identical copy, redirect, styling and timing across failures) · **L9** (single origin, no
absolute address) · **L11** (one `main`, one `h1`, live region) · **L16** (sessionStorage carve-out closed;
read only) · `agents.md:97` (catalogue-only copy).

**Inherited from T17:** **D-1/D-2** (the seam exists, unreached on a mismatch, asserted with `objectContaining`)
· **D-3** (notice is module state; drain in `afterEach`) · **D-5** (`` `/${SIGN_IN_PATH}` ``, leading slash,
`replace`) · **D-7** (a discard does **not** delete the stored items) · **D-8** (StrictMode asserted) · **D-9**
(source scans) · **D-12** (`error` + `code` discarded before any comparison).

**D-10 resolved:** a mismatch **reuses** `signIn.discarded`. L4 forbids copy distinguishing the two cases, so a
second `SignInNotice` member could only ever map to the same string.

## Dependencies

`AUTHORIZE_STORAGE_KEYS.state` (`authorizeRequest.ts:36`) — imported, never re-spelled · `discard()`
(`CallbackScreen.tsx:47`) · `exchangeAuthorizationCode` (`codeExchange.ts:18`) · `SIGN_IN_PATH`
(`routes.tsx:13`) · `en.signIn.discarded`. No config key, no contract, no backend call.

## Inputs

The callback URL's `state` and `code` query parameters, already trimmed by T17, and the `state` value in
`sessionStorage` under `themistra.auth.state` — absent, empty, or a 43-character base64url token.

## Outputs

Either a discard — notice set, `replace` navigation to `/app/sign-in` — or a single call to the exchange seam
with `{ code, state }`. Nothing else.

## State Changes

**None.** T18 adds one `sessionStorage` **read**. It writes nothing, deletes nothing, and adds no module state.

## Files to Create

**None.**

## Files to Modify

| File | Change |
|---|---|
| `src/auth/CallbackScreen.tsx` | the comparison at the marked line `:67`, calling the existing `discard()`; **and** correct the false "Task 19 owns…" claim at `:43` (phase 1) |
| `src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` | extend; **narrow** the blanket no-read assertion at `:566-583` to "reads `state` and nothing else" |

## Files NOT to Modify

`authorizeRequest.ts` (import only) · `codeExchange.ts` · `signInNotice.ts` · `routes.tsx` · `catalogue.ts` ·
`SignInScreen.tsx` · every other task's suite · anything under `spec/`.

## Acceptance Criteria

**AC1**–**AC15** exactly as `artifacts/01-specification-extraction.md` states them. In summary: mismatch
discards (AC1); nothing stored discards (AC2); an empty stored value discards (AC3); a **positive control**
proves a match reaches the seam (AC4); the comparison is shown to be **against the stored value, not a
constant**, by varying only the stored value (AC5); mismatch is exact across one-character, case, whitespace,
prefix and suffix differences (AC6); the outcome is **byte-identical** to T17's missing-state outcome, compared
shape-to-shape (AC7); the three items survive (AC8); only the `state` key is read (AC9); nothing is written
(AC10); StrictMode runs it once (AC11); accessibility unchanged (AC12); source scans still pass with synthetic
violations (AC13); the suite is measured against 634/635 (AC14); every matcher is fed a wrong input and every
fix is mutation-verified (AC15).

## Required Tests

`callbackWithMismatchedStateIsDiscarded` — the existing 606-line, 73-case suite, **extended**. It is named for
this task as much as for T17 and was built to be extended. No new test file.

`stateSurvivesRedirectAndIsConsumedOnce`, also listed in this task's generated header, is **task 23's**
(`tasks.md:23`) and is not implemented here.

## Constraints

- **Security** — the comparison must be exact: no normalisation, no case folding, no trimming of the *stored*
  value, no prefix matching. Anything that widens equality widens the injection surface. Absent storage must
  never compare equal to a supplied state, which is the trap `null == ''`-style coercion would set.
- **Enumeration safety (L4)** — identical copy, redirect target and styling for a mismatch and a missing state.
  Timing is identical by construction: one string comparison, no I/O, no backend call.
- **Module boundaries** — the screen may read `sessionStorage` directly, as T15 writes it directly; the key
  comes from `AUTHORIZE_STORAGE_KEYS`, never a re-spelled literal.
- **Null handling** — `getItem` returns `string | null`. `null` and `''` are both "nothing stored" and both
  discard. The callback-side `state` is already non-empty when the comparison is reached, because T17's guard
  returned before it otherwise.
- **Idempotency / re-entrancy** — T17's `handled` ref makes the effect single-shot; the comparison inherits it
  and must not add module state.
- **Performance, thread-safety, transactions** — not applicable: one synchronous string comparison in a browser
  with no shared mutable state.
- **Known tripwire** — `callbackWithMismatchedStateIsDiscarded.test.tsx:566-583` asserts the callback reads
  **none** of the three keys. T18 is the authorised change, and must **narrow** it to `state`-only rather than
  delete it, or the boundary stops being guarded for tasks 19–20.

## Open Questions

**No blockers.** Three non-blocking items carried from phase 1: **OQ-1**, a callback carrying a state when
nothing is stored, claimed for T18 as AC2 and reversible in one branch if the owner places it elsewhere;
**OQ-2**, that "single-use" deletion stays task 23's, largely defused because an authorization code is
single-use at the token endpoint; **OQ-3**, the unreviewed copy, to which T18 adds no string.

---

## Next

**Phase 3 — design challenge. This is Kimi 2.7's phase, and I am stopping here for it.**
