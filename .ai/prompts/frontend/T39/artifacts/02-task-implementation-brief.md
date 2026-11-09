<!-- MODEL: Claude Opus 5 — Phase 2 (Task Implementation Brief) -->

# frontend · T39 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T39 — Sign-out propagates to every tab |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |
| **State** | **PROPOSED** — phase 3 challenges it, phase 4 freezes it. Every open question from phase 1 is answered here with a position, not deferred. |

## A severity correction, measured, before anything is designed

Phase 1 established that T39 **can** close OQ-T35-3. Phase 2 measured how urgent that is, and the answer
changes how the hazard should be described — though not whether to fix it.

| Measured | Result |
|---|---|
| Production callers of `shareRenewalAcrossTabs` | **none.** Every call site is a test (`siblingTabsShareOneRenewal`, and three auth suites). |
| Who can post `renewal:succeeded` | only the lock leader at `crossTabRenewal.ts:135`, **after `local.renew()` resolves** |
| What `accessToken.renew()` does | **always rejects** with `RENEWAL_NOT_BUILT` — `accessToken.ts` says so and records that *no task in `tasks.md` builds renewal* |

So the hazard is **doubly unreachable today**: nothing installs the listener, and nothing can produce the
success it would react to. T35's framing — *"a signed-out tab is re-authenticated by a sibling, and this
case proves it"* — is true of the module's behaviour and overstates the product's exposure.

**This does not argue for skipping the fix.** R21 and L18 commit the SPA to renewal, so the hazard becomes
live the day renewal is built, and it is far cheaper to close it in the task that owns coherence than in
the task that owns renewal. But the brief should not claim to be closing an exploitable hole.

## Decisions

### D1 — **Two channels, not a third message type** (OQ-T39-1)

`crossTabRenewal.ts:78-86` constructs its channel **only when Web Locks are available**:

```ts
const channel = options.channel ?? (locks && typeof BroadcastChannel === 'function' ? new BroadcastChannel(RENEWAL_CHANNEL) : undefined);
if (!locks || !channel) { channel?.close(); return { /* local-only shim, NO listener */ }; }
```

Its header accepts the consequence: *"Without BroadcastChannel or Web Locks, renewal stays per tab."* That
is a fine degradation for renewal, which is an efficiency and single-flight concern. **It is not a fine
degradation for sign-out.** "Renewal stays per tab" costs a duplicate request; "a sibling tab stays signed
in" is the thing R22 forbids. Riding that listener would make propagation depend on Web Locks for no
reason — sign-out needs no leader election, only a broadcast.

So: a **new channel**, `'themistra.session.signout'`, whose only dependency is `BroadcastChannel`.

Rejected alternative, and why it was tempting: a third `Outcome` variant would put sign-out in the exact
listener that performs the adopt, which looks like the natural place to refuse one. But the refusal needs a
**latch**, not a message — and a latch works whether or not that listener exists.

### D2 — **The latch is set inside `signOut()`; the broadcast is not** (OQ-T39-2, OQ-T39-4)

Two different questions with two different answers, and the split is what makes the echo problem vanish.

- **`markSignedOut()` goes INSIDE `signOut()`.** It must cover *every* sign-out path, and `endSession()`
  calls `signOut()` — a latch set in a wrapper would be bypassed by the SAS path, which is the one real
  sign-out there is. It is local state, so it cannot echo.
- **`broadcastSignOut()` stays OUTSIDE**, called by `endSession()`. A tab that *receives* a sign-out signs
  out by calling `signOut()`, which does not broadcast — **so it cannot echo, structurally.** No sender
  id, no message flag, no "ignore my own message" logic, and nothing to get wrong.

It also lands R19 correctly: the tab the user acted in ends the SAS session **once**; receivers only clear
local state. N tabs navigating to `/connect/logout` would be N navigations for one intent.

**Cost, recorded honestly:** `signOut()` gains a sixth call, and T36 derived `SIGN_OUT_SET` **from
`signOut.ts`'s source** precisely so that growth would be noticed. That derivation will fire. Under the
rule T36's phase 9 wrote, an instrument may be edited when it *must grow to admit new state* — this is
that case, and phase 6 must show the instrument firing before it is updated, not after.

### D3 — **A new module in `api/`, taking the local action as a callback** (OQ-T39-3)

`src/api/crossTabSignOut.ts`, beside `crossTabRenewal.ts`: same layer, same vocabulary, same lifecycle
shape (`{ close() }`).

The layering matters. `api/` imports **nothing** from `auth/` today; `auth/accessToken.ts` imports a
**type** from `api/crossTabRenewal` (erased at compile). A module in `auth/` would force
`crossTabRenewal.ts` to import upward to read the latch. So the new module **takes the local sign-out
action as a parameter** and never imports `auth/` — `auth/` does the wiring, as it already does.

Proposed surface:

```ts
export const SIGN_OUT_CHANNEL = 'themistra.session.signout';
export function markSignedOut(): void;            // the latch, set by signOut()
export function markSignedIn(): void;             // cleared by the one place a token is assigned
export function hasSignedOut(): boolean;          // read by crossTabRenewal's onMessage
export function broadcastSignOut(channel?: BroadcastChannel): void;
export function listenForSignOut(onSignOut: () => void, options?: { channel?: BroadcastChannel }): { close(): void };
```

### D4 — **The latch resets where the token is assigned** (OQ-T39-5)

`accessToken.ts:82` states that `retainAccessToken` is *"the only place a non-null token is assigned"* —
`adopt()` delegates to it. That single assignment point is the right hook, and there is exactly one.

Note what this does **not** do: `adopt()` reaching `retainAccessToken` cannot reset the latch in practice,
because a set latch makes the adopt refuse before it gets there. The reset fires on a real sign-in — which
is `CallbackScreen`'s `retainAccessToken(response.accessToken)`.

**Cost:** this edits `accessToken.ts`, task 21's closed module. Precedent exists (T36 edited task 17–20's
`CallbackScreen.tsx` with justification). The alternative — resetting from `CallbackScreen` — was rejected
because it puts the reset in a *caller* rather than at the single assignment point, so a second sign-in
path added later would silently not reset.

### D5 — **The message carries nothing** (L3)

`{ type: 'session:signed-out' }`. No token, no subject, no tab id, no timestamp. `renewal:succeeded` must
carry a token; sign-out has nothing to say, and a broadcast is a surface like any other. A parser rejecting
every other shape, mirroring `parseOutcome`.

### D6 — **OQ-T39-6 is named, not closed**

With the latch reset by a fresh sign-in, a `renewal:succeeded` from a sibling's *pre-sign-out* renewal
could arrive afterwards and overwrite a new token with an older one. That is token **freshness**, not
sign-out propagation, and closing it needs a generation stamp **on the message** — which means changing
`crossTabRenewal`'s closed union, the thing D1 declined to do for better reasons. It is also unreachable
today for the reasons measured above. Carried as **OQ-T39-6** for whoever builds renewal.

## Files

| File | |
|---|---|
| `src/api/crossTabSignOut.ts` | **NEW** — channel name, latch, broadcast, listener |
| `src/api/crossTabRenewal.ts` | **MODIFY** — `onMessage` refuses `renewal:succeeded` when `hasSignedOut()` |
| `src/auth/signOut.ts` | **MODIFY** — `markSignedOut()`, and where in the order it goes |
| `src/auth/accessToken.ts` | **MODIFY** — `markSignedIn()` in `retainAccessToken` |
| `src/auth/endSession.ts` | **MODIFY** — `broadcastSignOut()` |
| `src/api/signOutPropagatesToAllTabs.test.ts` | **NEW** — the named test |
| `src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | **MODIFY** — AC9 replaced, AC9b retired, `SIGN_OUT_SET` grown |

## Proposed acceptance criteria

| | |
|---|---|
| **AC1** | A sign-out in one tab is delivered to a sibling tab, which clears its own token. |
| **AC2** | The receiving tab does **not** re-broadcast — proven by a third endpoint observing exactly one message. |
| **AC3** | The receiving tab does **not** navigate to `/connect/logout`: the SAS session is ended once, by the acting tab (R19). |
| **AC4** | The broadcast carries no token, no identifier and no timestamp — the raw message is asserted. |
| **AC5** | Propagation works with **no Web Locks at all** — the degradation D1 exists to prevent. |
| **AC6** | Propagation is a no-op, not a throw, where `BroadcastChannel` is undefined. |
| **AC7** | **OQ-T35-3 CLOSED:** a signed-out tab handed `renewal:succeeded` keeps a null token — asserted in both orderings, the post before and after `signOut()`. |
| **AC8** | A tab that signs out and signs in **again** adopts a sibling renewal normally — the latch resets. |
| **AC9** | `signOut()`'s five-step order is unchanged; `markSignedOut()` is additive and its position is justified. |
| **AC10** | Nothing is stored: no `sessionStorage`, `localStorage`, cookie or IndexedDB write on any path (L16). |
| **AC11** | A malformed or hostile message on the sign-out channel is ignored, not acted on. |
| **AC12** | `close()` removes the listener: a message after close does nothing. |
| **AC13** | `api/crossTabSignOut.ts` imports nothing from `auth/` — asserted by source scan, because it is the property that keeps the layering. |
| **AC14** | T35's AC9 is replaced and AC9b retired, with the replacement asserting the fixed behaviour. |

## Deviations already visible

| | |
|---|---|
| **D-a** | **Nothing installs the listener.** `endSession()` has no caller (OQ-T36-5), and no module owns app-level listeners — `mountShell.tsx` is unowned. T39 ships the primitives and the wiring point; "signing out in one tab signs out all open tabs" is therefore **not reachable in production**, making T39 the **ninth** consecutive dormant task. |
| **D-b** | Three closed tasks' production files are modified (`crossTabRenewal.ts`, `accessToken.ts`, `endSession.ts`), each with a reason above. |
| **D-c** | T36's source-derived `SIGN_OUT_SET` must grow, and T35's AC9/AC9b are replaced — instructed by T35's own handoff. |
| **D-d** | The hazard being closed is latent, not live (measured above). |

## Phase 3

Kimi 2.7's design challenge.
