# frontend · T06 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T06 — Shared renewal across tabs |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | Current `frontend/src/api/crossTabRenewal.ts`, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, `spec/frontend/agents.md` |
| **Produces** | `artifacts/08-independent-review.md` |

**Task statement (verbatim):**
> **Shared renewal across tabs.** Sibling tabs wait on the one renewal instead of starting their own. Verifies `siblingTabsShareOneRenewal`. Cites R21, L18.

---

## Review scope

Reviewed the Phase 6 T06 implementation (`frontend/src/api/crossTabRenewal.ts`) against the frozen brief, `agents.md`, and the Phase 7 self-review. No code was rewritten.

The self-review identified six issues, all low severity or informational. This review corroborates them and adds three fresh findings: one medium (the follower's grace wait ignores the deadline) and two low/secondary (`close()` does not clear per-renewal deadline timers; a throwing `local.adopt` can block waiters).

---

## New findings

### 1. The follower's grace wait is not bounded by the overall deadline
**Issue ·** In `renew()`, once the follower is granted the lock it calls `outcomeSince(mark)`, which waits up to `graceMs` regardless of whether the `deadline` AbortSignal has already fired. If the follower gets the lock just before the deadline expires, it still waits the full grace period, then calls `lead(deadline.signal)` with an already-aborted signal and eventually rejects with `RenewalTimeoutError` *after* the deadline. AC5 says "a follower's wait past the deadline rejects"; the current implementation rejects late.  
**Evidence ·** `frontend/src/api/crossTabRenewal.ts:164-168` (follower callback awaits `outcomeSince(mark)` without racing the signal). `outcomeSince` at `:134-148` uses a `setTimeout(graceMs)` and has no `AbortSignal` parameter.  
**Recommendation ·** Race `outcomeSince(mark)` against `deadline.signal` inside the follower callback, resolving `null` immediately when the signal aborts. Alternatively, pass `deadline.signal` into `outcomeSince` and have the grace timer reject/clear on abort. This keeps the follower's total wait bounded by `timeoutMs`.  
**Confidence ·** High — observable whenever lock acquisition time + grace exceeds the deadline.

### 2. `close()` does not clear the per-renewal deadline timer
**Issue ·** `close()` removes the BroadcastChannel listener and closes the channel, but it does not touch the `timer` created in `renew()` (`:158`). If `close()` is called while a renewal is in flight, the timer continues and aborts the `deadline` controller at `timeoutMs`. The leader's `untilAborted` then rejects, so the in-flight `renew()` rejects with `RenewalTimeoutError` instead of settling locally. This contradicts the frozen brief §close: "An in-flight `renew()` still settles locally."  
**Evidence ·** `:155-175` (each `renew()` creates a `timer` referenced only in the function's `finally`); `:180-184` (`close()` has no access to active timers/controllers).  
**Recommendation ·** Track active deadline `AbortController`s or timer handles in instance-level state and clear/cancel them in `close()`. To satisfy the brief's "settles locally" requirement, clear the timers without aborting the underlying `local.renew()`; if the goal is only teardown, aborting waiters with a `CoordinatorClosedError` is also acceptable, but then the brief wording should be updated.  
**Confidence ·** Medium — the behavior depends on timing relative to `timeoutMs`, but AC7 explicitly tests the mid-renewal close case.

### 3. A throwing `local.adopt` drops the outcome and blocks grace waiters
**Issue ·** `onMessage` calls `local.adopt(outcome.token)` before incrementing `outcomeCount` and waking waiters (`:98-101`). If `adopt` throws, the message is effectively discarded: the outcome is not counted, `lastOutcome` is not updated, and the `waiters` set is not iterated. A follower waiting on that message will sit until its grace timer expires and then start its own redundant renewal.  
**Evidence ·** `:95-102`.  
**Recommendation ·** Wrap `local.adopt` in a try/catch. If it throws, still count the outcome, store it, and wake waiters so the follower can return the token from the message. Surface the adopt error via `console.error` or a dev-only warning, but do not let it suppress the broadcast outcome. Alternatively, document that `LocalSession.adopt` must never throw and add a test that guards the contract.  
**Confidence ·** Low — `adopt` is expected to be a simple in-memory assignment, but the contract is not enforced.

---

## Corroborated findings (from Phase 7 self-review)

| # | Self-review finding | Independent view |
|---|---|---|
| 1 | Leader whose renewal finishes after deadline never shares token | Confirmed. The design choice is internally consistent (`renewal:failed` was already posted), but it should be documented in the code comment for `untilAborted`/late result. |
| 2 | Service worker could receive token if it listens on `RENEWAL_CHANNEL` | Confirmed. The channel name is exported, so task 9–12 must include a guard that the worker never opens it. |
| 3 | Residual extra renewal between leader attempt and in-transit message | Confirmed. This is the documented residual case; no code change needed. |
| 4 | `close()` closes an injected channel | Confirmed. Acceptable because each coordinator owns the channel instance it uses; task 39 should use a distinct channel. |
| 5 | Back/forward-cache tab keeps old token | Confirmed. Browser behavior, self-healing on next 401. |
| 6 | `renew()` is dense | Confirmed. The prose doc comment and the inline comment at `:166` are sufficient for now. |

---

## Non-gaps (acknowledged, no change needed)

- **Token over BroadcastChannel and L3.** The frozen brief records the interpretation that same-origin BroadcastChannel is not storage/log/URL/analytics. `agents.md` L3 is read-only for frontend tasks; the interpretation is flagged for an ADR in Phase 13.
- **Double timeout layering.** The frozen brief rejected the concern; T06's deadline is needed to release the Web Lock if the boundary timeout fires first. The implementation correctly posts `renewal:failed` on timeout.
- **No module-level mutable state.** All state is instance-level (`outcomeCount`, `lastOutcome`, `waiters`, `closed`).
- **Fallback to local-only.** When `navigator.locks` or `BroadcastChannel` is missing, the function returns a local-only wrapper and closes any default channel. Good.
- **Message validation.** Unknown types, non-object data, missing or empty tokens are ignored. `renewal:failed` carries no reason.
- **Leader posts while holding the lock.** `lead()` posts inside the lock callback before returning, so followers cannot acquire the lock before the message is sent.
- **Synchronous throw from `local.renew()`.** Wrapped in `Promise.resolve().then(...)`. Good.

---

## Summary

The T06 implementation correctly realizes the Web Locks + BroadcastChannel protocol from the frozen brief. The most important fresh issue is **finding 1**: the follower's grace wait can overrun the deadline, making the promise reject later than specified. **Finding 2** is also material for the AC7 mid-renewal `close()` case. **Finding 3** is a defensive-contract concern. The remaining behavior matches the brief and the Phase 7 self-review.

Recommended Phase 9 fixes:
1. Race `outcomeSince` against the deadline signal in the follower path.
2. Clear or detach active deadline timers/controllers in `close()` so in-flight leaders settle locally.
3. Harden `onMessage` against a throwing `local.adopt`.
