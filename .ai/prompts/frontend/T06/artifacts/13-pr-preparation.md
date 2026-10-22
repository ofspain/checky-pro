# frontend · T06 · Phase 13 — PR / Commit Preparation

Gate: the Phase 12 verdict is **PASS**.

Branch note: work lands on `spec/service-specs-and-ai-framework` and is pushed after each phase. For `main`, merge or
squash the branch's final tree. The repository's standing attribution (`Claude Opus 5.5`) is used, as in T01–T05.

## Commit title
`frontend: sibling tabs share one session renewal (T06)`

## Commit message
```
frontend: sibling tabs share one session renewal (T06)

Extends T05's per-tab single flight across tabs (R21, L18):

- shareRenewalAcrossTabs(local, { channel, locks, timeoutMs, graceMs })
  wraps the tab's LocalSession. A tab that gets the Web Lock
  'themistra.session.renewal' with ifAvailable leads: it renews and
  posts renewal:succeeded { token } or renewal:failed while still
  holding the lock. Other tabs queue for the lock and use the outcome
  that arrived since they asked, or within a 1 s grace (lock grant and
  message delivery aren't ordered across tabs). If none comes, the
  leader went away, and they renew themselves.
- Every listening tab adopts a shared token. renewal:failed carries no
  reason. Malformed or foreign messages are ignored. A throwing adopt
  surfaces uncaught without losing the outcome.
- One deadline (RENEWAL_TIMEOUT_MS) bounds both a follower's wait and a
  leader's hold, so a hung renewal can't keep the lock. close() keeps
  that guarantee.
- Without Web Locks or BroadcastChannel it is local-only (T05 still
  holds per tab).
- siblingTabsShareOneRenewal (22 cases).

The real LocalSession comes with the auth tasks (14-23). Sign-out
propagation (task 39) uses its own message types.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

## Files changed
- `frontend/src/api/crossTabRenewal.ts` (new).
- `frontend/src/api/siblingTabsShareOneRenewal.test.ts` (new).

There are no dependency, `libs/`, `spec/`, `contracts/`, `services/`, `infra/` or CI changes. T05's
`sessionBoundary.ts` and `client.ts` are untouched.

## Summary
The sixth frontend task makes several open tabs act as one when the session needs renewing. If a user has the app open
in three tabs and the session expires, only one tab renews it. The others wait for that result and use the new
session, rather than each signing in again. If the renewing tab is closed halfway through, another tab takes over. If
renewal fails, every tab learns it failed, without the reason being passed around. Nothing on screen changes yet.

## For the owner: one interpretation of a locked decision (ADR offer)
L3 says tokens live in memory and are never stored, logged or put in a URL. To meet L18 ("wait on the one renewal"),
**a renewed access token is sent from the renewing tab to its sibling tabs as a BroadcastChannel message.** That is
in-memory, same-origin and never persisted, so the frozen brief reads it as within L3. It was flagged at phase 2 and
recorded at phase 4. `spec/` is read-only for frontend tasks, so it isn't written into the spec. **If you want it on
record, I can draft an ADR.** If you'd rather tokens never leave the tab that fetched them, the alternative is that
siblings wait for the leader's *signal* and then each renew themselves. That costs one silent re-auth per tab instead
of one in total.

## Carried forward
- **Service worker (tasks 9–12):** the worker must never open `RENEWAL_CHANNEL`, or it would receive tokens. Add a
  guard test there (P7-2).
- **Real-browser check:** jsdom has no Web Locks, so the cross-tab path is tested against a fake lock manager with
  Web Locks semantics. Two real tabs belong in the E2E tasks.

## Testing performed
In the `infra/local` `frontend` container:
- `pnpm -C frontend test`: **99/99** (22 new), 3 runs in a row green. The new file ran 10 times in a row green.
- `typecheck`, `lint` (0 errors, 2 rules) and `build`: clean.

Phase 2 probe: the naive design (every tab queues for the lock and renews in turn) gave **2** local renewals in every
scenario. The leader/follower design with a grace wait gives 1.

Mutation breaks: **15, each caught**. Each was applied to a backup and restored from it.
- the naive design (no `ifAvailable`);
- the grace wait removed;
- `adopt` not called;
- failure not broadcast;
- a reason on the wire;
- validation removed;
- the leader not bounded by the deadline;
- no post guard after `close()`;
- the grace wait ignoring the deadline;
- a follower leading after its deadline;
- adopt before counting;
- reusing a stale outcome;
- posting after the lock is released;
- `current()` broken;
- a throwing `adopt` detaching the listener.

## Specification references
- **Task:** `spec/frontend/tasks.md` task 6, "Shared renewal across tabs".
- **Requirement:** R21 (the sibling-tab half).
- **Locked decisions:**
  - L18: a Web Locks leader with a BroadcastChannel outcome;
  - L3, as interpreted above;
  - L16 / §5: no storage for coordination;
  - L12: T05 untouched;
  - L6: no global `fetch`.
- **Named test:** `package.md` §8, `siblingTabsShareOneRenewal` → R21.
