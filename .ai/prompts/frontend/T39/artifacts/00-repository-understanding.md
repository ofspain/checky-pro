<!-- MODEL: Claude Opus 5 — Phase 0 (Repository Understanding) -->

# frontend · T39 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T39 — Sign-out propagates to every tab |
| **Produces** | `artifacts/00-repository-understanding.md` |

> **Task 38 was skipped, not forgotten.** `tasks.md:66` marks it "Blocked on Q15". T37's phase 12 recorded
> that the block looks mis-assigned — `package.md:475` lists Q15 as resolved (2026-10-10, session
> revocation) and `:421-430` shows it is about revocation semantics, not sign-out. `spec/` is not mine to
> edit and a blocked task is not mine to unblock, so it is flagged for the owner and T39 proceeds.

## The task

> **39. Sign-out propagates to every tab.** Signing out in one tab signs out all open tabs. Verifies
> `signOutPropagatesToAllTabs`. Cites R22, L18.

- **R22** (`requirements.md:72-75`): *"IF renewal fails or the session is no longer valid, THEN the SPA
  SHALL transition to clean re-auth, SHALL NOT show a half-authenticated screen, and SHALL show neutral
  copy. **Sign-out SHALL propagate to every open tab.**"*
- **L18** (`design.md:63-64`): *"At most one renewal runs per tab. Tabs share renewal **and sign-out**
  through BroadcastChannel or a Web Locks leader (R21, R22)."*

L18 names the mechanism and it is already in the repository, built for renewal.

## This task has been written down in advance, twice, in code

T39 is the first task in this spec I have reached that arrives with an **inherited failing expectation**
rather than only a prose handoff. Two closed tasks left it instructions.

### 1. `signOut.ts:41-49` — the hazard, named and assigned

> *"**A HAZARD THIS FUNCTION CANNOT CLOSE (task 39 owns it, OQ-T35-3).** A tab that has signed out can be
> handed a live token again by a sibling. `crossTabRenewal.ts`'s channel listener calls
> `local.adopt(token)` on any `renewal:succeeded` message and does not ask whether this tab signed out …
> **Task 39's propagation narrows the window without closing it**, because a sibling whose renewal
> resolves before it handles the broadcast still posts a success and the channel orders nothing."*

The last sentence is the important one and I will come back to it in phase 1: **T39's own deliverable is
not sufficient to close the hazard T39 was assigned.**

### 2. `signOutClearsMemoryAndEndsSasSession.test.tsx:755-845` — AC9, a test that must be replaced

T35 pinned the hazard **as it is** rather than as it should be, with the reasoning that *"a test asserting
the fix would have to be added broken"*. Its assertion message is addressed to me:

> *"GOOD NEWS, AND THIS CASE MUST CHANGE: a signed-out tab no longer adopts a sibling renewal. If you are
> building task 39, that is the fix OQ-T35-3 asked for — replace this case with the assertion that the
> token stays null and delete OQ-T35-3."*

And **AC9b** (`:834-845`) scans that case's own source for `task 39`, `OQ-T35-3`, `replace this case` and
`stays null`, so the handoff cannot rot into a green test beside a real defect with nobody told.

**Consequence for T39:** editing a closed task's test is not merely permitted here, it is *instructed*.
Under the rule T36's phase 9 wrote — an instrument may be edited when it has become **false** — AC9
becomes false the moment the propagation lands, and AC9b becomes vacuous with it. Both are T39's to
replace, and phase 2 must say what replaces them.

## What exists

### `src/api/crossTabRenewal.ts` — 200 lines, the mechanism L18 names

| | |
|---|---|
| `RENEWAL_CHANNEL` | `'themistra.session.renewal'` — a `BroadcastChannel` name |
| `RENEWAL_LOCK` | `'themistra.session.renewal'` — the Web Locks name |
| `shareRenewalAcrossTabs(local, { channel?, locks? })` | returns a `SharedRenewal`; **degrades to a local-only shim** when `locks` is absent or `BroadcastChannel` is undefined (`:85`) |
| `Outcome` | `{ type: 'renewal:succeeded'; token } \| { type: 'renewal:failed' }` — a closed union, parsed by `parseOutcome` (`:50-56`) |
| `onMessage` (`:95-110`) | counts the outcome, wakes waiters, and on success calls `local.adopt(outcome.token)` **unconditionally** |
| `SIBLING_GRACE_MS` | `1_000` |

`onMessage` is the whole of OQ-T35-3: it asks nothing about this tab's state before adopting.

### `src/auth/signOut.ts` — 100 lines, five local steps and no broadcast

`clearAccessToken()` first (and the header explains at length why first), then `takeIdToken()`,
`takeSignInNotice()`, `consumeAuthorizeState()`, `clearReturnTarget()`. `:12` says plainly:
*"Broadcasting to sibling tabs is task 39 (R22, L18)."*

### `src/auth/accessToken.ts:45` — the object T37 phase 11 corrected a scan about

`accessToken: LocalSession` with `current()`, `adopt()` and `renew()`. **`adopt()` is the method the
hazard goes through**, and `renew()` rejects with `RENEWAL_NOT_BUILT` because no task in `tasks.md` builds
renewal — so every `renewal:succeeded` message in production today could only come from a tab that got a
token some other way. That is worth stating precisely in phase 1 rather than assuming.

### Already-closed neighbours T39 must not re-assert

| Task | Owns |
|---|---|
| 21 | `accessToken.ts` and the in-memory-only rule |
| 33 | `cleanReauth.ts`, the failed-renewal path; its handler also clears first |
| 35 | `signOut.ts`, `clearReturnTarget`, and the five-step order |
| 36 | `endSession.ts`, the SAS hand-over; `endSession.ts:29` — *"No propagation to sibling tabs: task 39's."* |
| 37 | `/app/signed-out` and its screen |
| R21 / renewal single-flight | `siblingTabsShareOneRenewal.test.ts` |

## Existing suites that will have an opinion

| Suite | Why it may react |
|---|---|
| `signOutClearsMemoryAndEndsSasSession.test.tsx` | AC9 **must go red** when the fix lands; AC9b goes vacuous with it. Also pins `signOut`'s five-step order and the set of primitives it may call — a broadcast added to `signOut` is a sixth thing it does, and T36 derived `SIGN_OUT_SET` **from source**, so that derivation will see it. |
| `siblingTabsShareOneRenewal.test.ts` | owns the renewal channel's behaviour; a new message type on the same channel is its business |
| `tokensAreNeverWrittenToAnyStorage.test.tsx` | sweeps for token writes; a broadcast carries no token, which must stay true |
| `sessionStorageHoldsOnlyTheL16Items.test.tsx` | L16's carve-out is a closed list; a propagation that stored anything would break it |

## The decisions phase 1 and 2 must take, listed now so they are not taken by accident

1. **One channel or two.** Reuse `RENEWAL_CHANNEL` with a third `Outcome` variant, or open a sign-out
   channel of its own. The union is closed and `parseOutcome` rejects anything unknown, so a third variant
   is a change to a closed task's contract; a separate channel is additive but means two channels to
   coordinate, and `shareRenewalAcrossTabs` is where the listener lives.
2. **Whether `signOut()` broadcasts, or a caller does.** `signOut()` is deliberately zero-argument and
   reaches no closure; `signOut.ts:46-48` says the hazard "is not fixable from here".
3. **How a receiving tab signs out without echoing.** A tab that handles a sign-out broadcast by calling
   `signOut()` must not re-broadcast, or two tabs ping-pong.
4. **What "signs out all open tabs" means for a tab showing an authenticated screen.** No authenticated
   route has an element (T37 measured this), so there is no screen to navigate away from — the same
   measurement that killed T37's redirect option.
5. **Whether T39 can close OQ-T35-3 at all,** given `signOut.ts:44-46` says propagation narrows the window
   without closing it. If the honest answer is "narrows", then replacing AC9 with "the token stays null"
   would be asserting something the design does not guarantee — and the handoff message would be wrong
   about its own fix. **This is the question phase 1 has to settle first**, because every other decision
   depends on it.

## Phase 1

Specification extraction.
