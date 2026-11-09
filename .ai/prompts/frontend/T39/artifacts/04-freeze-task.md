<!-- MODEL: Claude Opus 5 — Phase 4 (Freeze Task) -->

# frontend · T39 · Phase 4 — Freeze Task

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T39 — Sign-out propagates to every tab |
| **Consumes** | `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-freeze-task.md` |
| **State** | **BRIEF FROZEN** |

Eighteen findings. **Sixteen accepted**, **two accepted in part with the other part rejected on
measurement**. Every citation was checked. Two of Kimi's premises did not survive checking, and in both
cases the *recommendation* was right while the *reason* was not — which matters, because a criterion
written to the wrong reason tests the wrong thing.

## F3 — the position is right; the mechanism it is argued from does not exist

Kimi makes `markSignedOut()`-first a blocker, and the conclusion is correct. The argument is not:

> *"If `markSignedOut()` is placed at the END, there is a window after `clearAccessToken()` during which
> the tab holds no token but the latch is not yet set; a `renewal:succeeded` arriving in that window would
> be adopted."*

**That window cannot exist.** `signOut()` is synchronous and runs to completion before any task or
microtask; phase 1's **Q2** measured exactly this — channel delivery was never observed before a
synchronous `signOut()` in the same task. Nothing can arrive "between" two statements of a synchronous
function. The recommended test —

> *"add a test that posts `renewal:succeeded` immediately after `markSignedOut()` (before the clears
> finish)"*

— **is unwritable** for the same reason: there is no point at which a test could interleave a post into a
synchronous call.

**But first is still the right position, for the reason `signOut.ts` already gives about its own first
step** (`:62-68`): there is *no try/catch* in `signOut()`, so a throw from any step abandons every step
after it. T35 put `clearAccessToken()` first because its omission is a security defect rather than an
untidy store. `markSignedOut()` has exactly that property — a sign-out that cleared the token but left the
latch unset is a tab that can be re-authenticated by a sibling — and it is the *only* step whose omission
is worse than the clear's, because it governs what happens after the function returns.

So: **first, ahead of `clearAccessToken()`**, and AC9 asserts the position with a test that *is* writable —
make a later step throw, and assert the latch is set anyway. **The window is a throw window, not a
delivery window.**

## F8 — type-exactness accepted; rejecting extra properties rejected

Kimi recommends the parser reject *"objects with additional properties"*. Accepting that would make this
parser stricter than the sibling it mirrors — `parseOutcome` (`crossTabRenewal.ts:50-56`) checks `type` and
`token` and ignores anything else — and strictness is the wrong direction here:

- **The threat model does not justify it.** `BroadcastChannel` is same-origin. A hostile page cannot post
  to this channel; what can reach it is *our own code at a different version*.
- **And that is where strictness bites.** If a later version posts `{ type: 'session:signed-out', reason:
  'idle' }`, a strict parser in an old tab **ignores it** — and an ignored sign-out is a tab that stays
  signed in, which is precisely what R22 forbids. A lenient parser signs that tab out and discards the
  field it does not understand.

A sign-out message is the one message where the failure modes are asymmetric: acting on a slightly-unknown
shape costs a redundant local clear; refusing it costs a live session. **Accepted:** `type` must be exactly
`'session:signed-out'` and every other shape (non-object, null, wrong type, missing type) is ignored.
**Rejected:** additional properties are ignored, not grounds for refusal — and AC11 says so explicitly, so
the leniency is a pinned decision rather than an oversight.

## F13 — accepted, and now measured rather than assumed

Kimi asks for an explicit lifecycle and proposes fire-and-forget for `broadcastSignOut`. Before freezing
that I measured whether a post survives an immediate close, because the whole design rests on it:

| Measured | Result |
|---|---|
| **Q3.** `postMessage` then `close()` on the same tick — is the message delivered? | **Yes**, 1 received; a control post without the close brings the total to 2, so the rig delivers |
| **Q4.** `postMessage` on an already-closed channel | throws a **`DOMException`** |

Q3 makes fire-and-forget safe. Q4 adds a constraint Kimi did not state: **`broadcastSignOut()` must create a
fresh channel on every call and must never cache one**, or a second broadcast would throw on the channel
the first one closed. It is also why `crossTabRenewal.ts:116` guards its own `post()` with `if (!closed)`.

**Frozen:** `broadcastSignOut()` creates, posts, closes. `listenForSignOut()` creates, holds, and its
`close()` closes. Injection is for tests only.

## Accepted as recommended

| | |
|---|---|
| **F1** | **Confirmed at source.** `NOT_SESSION_STATE` (`:198`) carries `file`, `declarations` and a `why` of more than 60 characters, and the scan asserts holders equal cleared ∪ excused. The latch cannot be in *cleared* — `signOut()` **sets** it — so it must be excused, with the `why` saying it is the **inverse** of session state: it records that no session may be adopted, not that one exists. |
| **F2** | The runtime dependency from `accessToken.ts` to `api/crossTabSignOut.ts` is stated in the Files table with the trade-off: the reset sits at the single assignment point (`accessToken.ts:82`) rather than in a caller, so a second sign-in path added later cannot silently skip it. |
| **F4** | The AC9 replacement must prove the **mechanism**, not the outcome: install the listener, install `shareRenewalAcrossTabs`, sign out, assert delivery to a third endpoint, *then* post `renewal:succeeded` and assert the token stays null. A case that only asserts null could pass because the listener was never installed. |
| **F5** | AC2's no-echo proof gets the third-endpoint design, counting messages on `SIGN_OUT_CHANNEL`. |
| **F6** | **OQ-T39-6 promoted to deviation D-e**, with the race documented and a test that pins it as a **failing expectation** for whoever builds renewal — which is exactly what T35 did for this task, and the reason T39 arrived knowing what to do. |
| **F7** | Honest-limit criterion added: the primitives are verified by direct installation in tests, because no module owns app-level listeners. |
| **F9** | Phase-6 instruction: run T35's suite **with** the new call, capture the failure, *then* add `markSignedOut` to `PERMITTED_CALLERS` (only `auth/signOut.ts`) and grow the derived set. The instrument is shown firing before it is updated. |
| **F10** | **AC9b is deleted**, not left stale, and T39's suite asserts the old hazard message no longer appears in the source. |
| **F11** | AC6's design: delete `globalThis.BroadcastChannel`, call both functions, assert no throw and a callable `close()`, restore it. |
| **F12** | `endSession.ts` is in the Files table. |
| **F14** | Source-scan criterion: `crossTabSignOut.ts` declares no `BroadcastChannel` at module scope. |
| **F15** | `markSignedIn()` gets a doc comment saying it resets the latch and is called whenever a token is assigned — it does **not** assert the tab is authenticated. |
| **F16** | The guard's exact shape, at the top of the success branch and before `local.adopt()`, leaving parsing and the `Outcome` type untouched. |
| **F17** | AC8's steps: sign out, assert the latch, `retainAccessToken`, assert the latch is reset, post a success, assert adoption. |
| **F18** | Test placement: `signOutPropagatesToAllTabs.test.ts` owns the new module; T35's suite owns the OQ-T35-3 fix; T36's suite owns "`endSession()` broadcasts". |

## Frozen acceptance criteria

| | |
|---|---|
| **AC1** | A sign-out in one tab is delivered to a sibling, which clears its own token. |
| **AC2** | The receiver does **not** re-broadcast — a third endpoint on `SIGN_OUT_CHANNEL` sees exactly one message. |
| **AC3** | The receiver does **not** navigate to `/connect/logout`; the SAS session ends once, in the acting tab (R19). |
| **AC4** | The message is exactly `{ type: 'session:signed-out' }` — no token, no identifier, no timestamp; asserted on the raw payload. |
| **AC5** | Propagation works with **no Web Locks at all** (the degradation D1 exists to prevent). |
| **AC6** | With `BroadcastChannel` undefined, both functions are no-ops that do not throw and return a callable `close()`. |
| **AC7** | **OQ-T35-3 CLOSED**, proving the mechanism per F4, in both orderings — the post before and after `signOut()`. |
| **AC8** | The latch resets on a real sign-in, per F17's steps. |
| **AC9** | `markSignedOut()` runs **FIRST** in `signOut()`, and the latch is set even when a later step throws. The five existing steps keep their order. |
| **AC10** | Nothing is stored on any path (L16): no `sessionStorage`, `localStorage`, cookie or IndexedDB write. |
| **AC11** | The parser accepts only `type === 'session:signed-out'` and ignores every other shape; **additional properties are ignored rather than refused**, and the case says why. |
| **AC12** | `close()` removes the listener: a message after close does nothing. |
| **AC13** | `api/crossTabSignOut.ts` imports nothing from `auth/` — a source scan, because it is what keeps the layering. |
| **AC14** | T35's AC9 is replaced and **AC9b deleted**; the old hazard message is gone from the source. |
| **AC15** | `crossTabSignOut.ts` is excused in `NOT_SESSION_STATE` with `declarations: 1` and a reason naming the latch as the inverse of session state. |
| **AC16** | `crossTabSignOut.ts` holds no `BroadcastChannel` at module scope. |
| **AC17** | The **stale-adopt race (D-e / OQ-T39-6) is pinned as a failing expectation**, asserted as it is, with a message addressed to whoever builds renewal. |

## Frozen deviations

| | |
|---|---|
| **D-a** | **Nothing installs the listener.** `endSession()` has no caller (OQ-T36-5) and no module owns app-level listeners (`mountShell.tsx` is unowned). T39 is the **ninth** consecutive dormant task; the full cross-tab behaviour is verified by direct installation in tests. |
| **D-b** | Three closed tasks' production files are modified — `crossTabRenewal.ts`, `accessToken.ts`, `endSession.ts` — each with a reason. |
| **D-c** | T36's source-derived `SIGN_OUT_SET` must grow and T35's AC9/AC9b are replaced, as T35's own handoff instructed. The instrument is shown firing first. |
| **D-d** | The hazard being closed is **latent, not live**: no production caller installs the listener and `accessToken.renew()` always rejects, so no `renewal:succeeded` is producible today. |
| **D-e** | **The stale-adopt race is NOT closed.** With the latch reset by a fresh sign-in, a sibling's pre-sign-out `renewal:succeeded` could overwrite a new token with an older one. Closing it needs a generation stamp **on the message**, i.e. changing `crossTabRenewal`'s closed union — which D1 declined for better reasons. Pinned by AC17 as a failing expectation. **OQ-T39-6.** |
| **D-f** | **The parser is deliberately lenient about extra properties** (F8). Refusing an unknown-but-plausible sign-out would leave a tab signed in; accepting one costs a redundant local clear. |

## Phase 5

The implementation plan.
