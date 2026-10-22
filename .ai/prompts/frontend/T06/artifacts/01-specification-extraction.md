# frontend · T06 · Phase 1 — Specification Extraction

Consumes `00-repository-understanding.md`. Date: 2026-10-22.

## Business Rules
- **R21 (the share in scope):** "…the client boundary SHALL run at most one renewal per tab, SHALL share that renewal
  among parallel 401s **and sibling tabs**, and on failure SHALL route to clean re-auth without an error loop."
  - T05 delivered the per-tab half. T06 is the sibling-tab half: when a renewal is already running in one tab, a
    sibling tab that needs one waits for that renewal's outcome and uses it instead of running its own.
  - The renewal mechanism is silent re-auth on the shared SAS session cookie (Q5).
- **Context only, not implemented here:**
  - R22 ("Sign-out SHALL propagate to every open tab") is task 39, which will share the same cross-tab channel.
  - The clean re-auth transition is task 33.

## Locked Decisions
- **L18 [ALL]:** "At most one renewal runs per tab. Tabs share renewal and sign-out through BroadcastChannel or a Web
  Locks leader (R21, R22)." The mechanism is limited to those two browser primitives.
- **L3 [ALL]:** tokens are in memory only and never written to storage, logs or analytics, or placed in a URL.
  `agents.md` repeats this: "never written to `localStorage`, logs, or analytics, and are never placed in a URL".
  - **Interpretation, recorded for Phase 2–4:** sending the renewed token to sibling tabs over a same-origin
    BroadcastChannel is in-memory messaging between the app's own tabs. It writes nothing to storage, logs or a URL.
  - L18 names BroadcastChannel as the sharing mechanism. "Wait on the one renewal" can't be met unless the outcome,
    the token itself, reaches the waiting tab.
- **L16 / design.md §5:** the only client-side storage is the L16 sessionStorage items and public localStorage
  preferences. Coordination may not use storage for any session or renewal state.
- **L6:** no global `fetch` (lint). The renewal transport itself belongs to the auth module.
- **L12:** extend T05; don't rewrite it.
- **L13:** TypeScript and Vitest.

## Files involved
- **Read / extend:** `frontend/src/api/sessionBoundary.ts` (the `SessionTokens` seam and `createSessionBoundary`) and
  `frontend/src/api/client.ts`.
- **New, expected by the spec:**
  - a cross-tab renewal coordinator. Its home is the client boundary (`src/api/`, "401/429 … L18").
  - the named test `frontend/src/api/siblingTabsShareOneRenewal.test.ts`.
- **Not in T06:** `src/auth/` (the token holder and real silent re-auth), and sign-out propagation (task 39).

## Dependencies
- **Browser APIs:** `BroadcastChannel`, and optionally `navigator.locks` (Web Locks). No package is needed.
- **T05:** `SessionTokens`, consumed by `createSessionBoundary`.
- **From the future auth module:** the local `renew()` (silent re-auth), `current()`, and a way to install a token
  renewed by a sibling tab. That last one is new and must be part of the seam.
- **No config keys, env or contracts.** No HTTP is involved.

## Acceptance Criteria

| # | Criterion | Source |
|---|---|---|
| AC1 | When tab A is renewing and sibling tab B needs a renewal, B does not call its own local renewal. It waits for A's. | task 6, R21, L18 |
| AC2 | When A's renewal succeeds, B's waiting renewal resolves with that token, and B's requests then carry it. | R21 ("share that renewal") |
| AC3 | When A's renewal fails, B's waiting renewal fails too, with no renewal of its own for that episode. B's own boundary then reports once, so each tab can route to clean re-auth without a loop. | R21, R22 boundary |
| AC4 | Tabs renewing at nearly the same moment produce one renewal, not one per tab. | L18, R21 |
| AC5 | If the renewing tab goes away mid-renewal, a waiting sibling doesn't hang. It renews itself, or fails within the boundary timeout. | R21 ("without an error loop"); inferred robustness |
| AC6 | The per-tab guarantees of T05 still hold, with `parallel401sTriggerExactlyOneRenewal` green. | L12 |
| AC7 | Nothing about the renewal reaches storage, logs or a URL. A shared token travels only over the same-origin channel. | L3, L16, §5 |

## Tests required
- **Named:** `siblingTabsShareOneRenewal` (`package.md` §8 → R21).
- **Implied:**
  - two simulated tabs (two coordinator instances sharing a channel name and a lock manager) on success and failure;
  - simultaneous starts;
  - the renewing tab disappearing;
  - malformed or foreign channel messages ignored;
  - a second renewal episode after the first settles still works;
  - no storage writes;
  - the T05 suite unchanged.

## Open Questions
None are blockers; `package.md` §11 has nothing on cross-tab coordination. Left as Phase 2 defaults:
1. **The mechanism:** Web Locks for mutual exclusion plus BroadcastChannel for the outcome. A lock queues siblings and
   is released automatically if the holder closes (AC4, AC5). The channel carries the result (AC2, AC3). This is the
   "Web Locks leader" form L18 allows, using the BroadcastChannel it also names.
2. **Sharing the token over the channel** is consistent with L3 (see Locked Decisions). It is restated in the brief so
   Phase 3/4 can challenge it.
3. **Seam extension:** the local session gains `adopt(token)`, so a sibling's token can be installed. The auth module
   implements it.
4. **Without Web Locks** (only jsdom, in this codebase): an injected lock manager in tests. Production assumes
   `navigator.locks`, which all supported browsers have. Whether to fall back to per-tab renewal is left to Phase 2.
