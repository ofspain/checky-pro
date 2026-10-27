# frontend · T21 · Phase 3 — Design Challenge

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T21 — Access token in memory only |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the Phase 2 task implementation brief against the spec, the built consumers, and the standing rules. Findings only — accepted items are folded into the frozen brief by the human gate.

---

## F1 · `renew()` is required by `LocalSession` but no task owns the mechanism

**Severity.** Blocker.

**Evidence.**
- `crossTabRenewal.ts:4-6` defines `LocalSession extends SessionTokens { adopt(token: string): void }`, where `SessionTokens` (`sessionBoundary.ts:11-14`) requires `renew(): Promise<string>`.
- The brief §Open Questions calls this the "real scope question" and lists three exits, but explicitly says "This brief does not choose."
- `tasks.md` 1-34 contains no task that implements silent re-authentication via `/oauth2/authorize` in a hidden iframe or background navigation, despite `agents.md:25-34` and R21 making it the chosen model.
- Without a decision, Phase 5 cannot size the task and Phase 6 cannot implement it.

**Recommended brief amendment.** Choose **Exit 1: a throwing seam** for `renew()`, matching the `codeExchange.ts` pattern from T17–T20. Freeze it as a numbered decision:
- `renew()` throws a fixed `Error` with a fixed message that names no server detail, no token, and no status.
- The boundary's existing `onRenewalFailed` path routes to clean re-auth (task 33), so the seam does not break R21's single-flight wiring.
- The brief records that the real renewal mechanism is intentionally unimplemented in T21 and is tracked as a follow-up task, not an open question.

---

## F2 · `LocalSession` has no way to clear the token

**Severity.** High.

**Evidence.**
- `LocalSession` exposes only `current()`, `renew()`, and `adopt(token: string)`.
- `adopt()` takes `string`, not `string | null`, so it cannot be used to remove a token.
- Sign-out (R19) must "clear in-memory token state" (`agents.md` sign-out rule), and R22's failed renewal must route to clean re-auth with "no half-authenticated screen" — which implies the holder is empty.
- The brief's state changes say "One module-scoped variable" but never say how it becomes `null` again.

**Recommended brief amendment.** Add an explicit clearing mechanism. Either:
- Export a `clearAccessToken(): void` function beyond `LocalSession` (the holder still satisfies `LocalSession`, but sign-out and the renewal-failure callback call `clearAccessToken`), or
- Widen `adopt(token: string | null)` and document that `adopt(null)` clears the token.

The chosen shape must be frozen before implementation, because tests for sign-out and failed-renewal paths depend on it.

---

## F3 · The composition root is still unwired

**Severity.** High.

**Evidence.**
- `mountShell.tsx:12-30` builds i18n, the shell and the router and never constructs `createSessionBoundary`, `createAuthApi`, or `shareRenewalAcrossTabs`.
- `client.ts`, `sessionBoundary.ts`, and `crossTabRenewal.ts` all describe "production wiring" that does not exist.
- The brief §Open Questions notes that T21 is the first task with something to wire, then says wiring is "not necessarily T21's" and that if T21 declines, "the boundary stays unwired for a task that does not exist yet."
- AC5 and AC6 demand the holder be accepted by the real boundary and cross-tab modules "by use, not by a type assertion." That is testable in isolation, but the app still has no working session boundary if nothing wires it.

**Recommended brief amendment.** Decide and freeze whether T21 wires the composition root. If yes, add `mountShell.tsx` to Files to Modify and require that the holder is passed into `createSessionBoundary` and `shareRenewalAcrossTabs`. If no, name the follow-up task that owns wiring and state explicitly that T21's acceptance is proven by isolated use of the real consumers, not by end-to-end app wiring.

---

## F4 · AC3 omits the post-failure null state

**Severity.** Medium.

**Evidence.**
- The spec extraction AC3 reads: "`current()` returns `null` before any exchange, **and after a failed one**."
- The brief's AC3 reads only: "`current()` is `null` before any exchange."
- R22 requires "no half-authenticated screen" — a failed exchange or bad nonce must leave `current()` null, not leave a stale token from a previous sign-in.

**Recommended brief amendment.** Restore the post-failure clause: "`current()` returns `null` before any exchange **and remains `null` after a discarded callback, a refused exchange, a bad nonce, or any other failure path** (R22)."

---

## F5 · The expiry/ID-token question is carried but not decided

**Severity.** Medium.

**Evidence.**
- `codeExchange.ts:35-38` drops `expires_in`, `scope`, and `token_type`; T20 recorded expiry as "T21's question" (Phase 7 F3).
- `LocalSession.adopt(token: string)` and `current(): string | null` model a bare string, so any expiry or ID-token storage would widen an interface that tasks 5 and 6 already depend on.
- Sign-out (R19) may need the ID token as `id_token_hint` for the end-session endpoint; the brief says the ID token is dropped after T19's nonce check.
- OQ-4 is listed as "non-blocking, carried for Phase 3" but Phase 3 must resolve it before Phase 4 freezes the brief.

**Recommended brief amendment.** Freeze T21-D1: the holder keeps **only the access token as a bare string**. `expires_in` is not held; the renewal mechanism task will address timing when it is built. The ID token is not held in the access-token holder; if a later task needs it for end-session, that task owns deciding where it lives. Document that this is a deliberate handoff, not an oversight.

---

## F6 · Case-count citation for T20 is inconsistent with the Phase 10 manifest

**Severity.** Low.

**Evidence.**
- The brief says "T20's 69 cases" and "T20 built the verifier half (69 cases)".
- `artifacts/10-test-generation.md` states: "`frontend/src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` — **64** (new)" and "68 new cases" total across three files.
- The mismatch suggests the brief was written against an earlier count and may mis-size T21's "added to T20's 69 cases" expectation.

**Recommended brief amendment.** Update the count to 64 cases in the named file, and phrase T21's addition as "the in-memory half added to the same named file" without implying a total.

---

## F7 · The brief does not say how `adopt()` interacts with an in-flight callback exchange

**Severity.** Low.

**Evidence.**
- `shareRenewalAcrossTabs` can call `local.adopt(token)` at any time while the channel listener is active (`crossTabRenewal.ts:95-110`).
- `CallbackScreen.tsx` sets the token inside the exchange fulfillment, after the nonce check. JavaScript is single-threaded, but the brief never states the intended precedence: does a sibling's adopted token win over a token about to be set by the callback screen, or vice versa?
- Without a stated rule, tests cannot assert the correct behaviour for the race-shaped case.

**Recommended brief amendment.** Add T21-D2: `adopt()` unconditionally replaces the held token. The callback screen's retention is just another `set` of the same variable; whichever runs last wins. Document that this is safe because a sibling renewal and a callback exchange for the same tab are mutually exclusive in normal flow, and because `current()` is always read at request time, not cached.

---

## Summary

Resolve F1, F2, and F3 before Phase 4; they determine what T21 actually builds and what it leaves for follow-up tasks. F4 and F5 should be folded into the acceptance criteria as explicit decisions. F6 and F7 are polish but should be corrected to keep the brief self-consistent.
