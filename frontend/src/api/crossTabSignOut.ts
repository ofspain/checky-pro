/**
 * Sign-out propagation between the app's own same-origin tabs (R22, L18).
 *
 * R22: "Sign-out SHALL propagate to every open tab." L18 names the mechanism - "Tabs share renewal and
 * sign-out through BroadcastChannel or a Web Locks leader" - and renewal's half is already built in
 * `crossTabRenewal.ts`. This module is the sign-out half, and it is deliberately NOT part of that one.
 *
 * **WHY A SECOND CHANNEL RATHER THAN A THIRD MESSAGE TYPE.** `crossTabRenewal.ts:78-86` constructs its
 * channel only when Web Locks are available, and its header accepts the consequence: "Without
 * BroadcastChannel or Web Locks, renewal stays per tab." That is a fine degradation for renewal, where the
 * cost is a duplicate request. It is not a fine degradation for sign-out, where the cost is a sibling tab
 * left signed in - the thing R22 forbids. Sign-out needs no leader election, only a broadcast, so its only
 * dependency is `BroadcastChannel`.
 *
 * **THE LATCH IS THE INVERSE OF SESSION STATE.** `signedOut` does not record that a session exists; it
 * records that no session may be ADOPTED. That is why it is excused in T35's `NOT_SESSION_STATE` rather
 * than cleared by `signOut()` - `signOut()` is what SETS it. Task 35's own objection to a latch
 * (`signOut.ts:46-48`) was that it "means ADDING a piece of state in the task whose whole statement is
 * removing it", which is true of task 35 and does not transfer: L18 calls this task's subject cross-tab
 * COHERENCE, and coherence between tabs is inherently stateful.
 *
 * **WHAT IT CLOSES, AND WHY THAT WAS THOUGHT IMPOSSIBLE.** `signOut.ts:41-48` handed task 39 the hazard
 * OQ-T35-3 with the conclusion that propagation "narrows the window without closing it, because a sibling
 * whose renewal resolves before it handles the broadcast still posts a success and the channel orders
 * nothing". Measured, that conclusion is wrong, and for a precise reason: it reasons about when the sibling
 * POSTS, and what decides this tab's token is when this tab HANDLES. Only the second is ordered - channel
 * delivery is never observed before a synchronous `signOut()` in the same task - so a latch set inside
 * `signOut()` is already set whenever a `renewal:succeeded` is handled, whatever order the posts happened
 * in. The broadcast cannot stop a sibling from posting; this tab does not need it to. It only needs to
 * refuse the adopt.
 *
 * No token, no identifier and no timestamp crosses this channel: a sign-out has nothing to say, and a
 * broadcast is a surface like any other (L3). Nothing is stored anywhere (L16).
 */

/** The channel name. Distinct from `RENEWAL_CHANNEL`, for the reason in this module's header. */
export const SIGN_OUT_CHANNEL = 'themistra.session.signout';

/**
 * The message's whole content, as a STRING rather than as the object it travels in.
 *
 * Not a style choice. T35's module-scope-mutable scan is
 * `/^(?:let|var)\s|^const\s+\w+\s*(?::[^=\n]*)?=\s*(?:new\s+(?:Map|Set|WeakMap|WeakSet)\s*\(|\[|\{)/m`,
 * and it matches `= {` at column 0 whatever follows - a trailing `as const` included. I first wrote this
 * message as an object constant, on the strength of a neighbouring comment in `NOT_SESSION_STATE` rather
 * than of the regex, and the scan counted TWO declarations in this file. A file-level excusal covers every
 * declaration in the file, so keeping it at one keeps it specific to the only thing here that is state.
 */
export const SIGN_OUT_TYPE = 'session:signed-out';

/**
 * **The one piece of module-scope state in this file**, excused in T35's `NOT_SESSION_STATE` as the inverse
 * of session state. It is per tab, never stored, and never crosses the channel.
 */
let signedOut = false;

/** Records that this tab has signed out. Called FIRST in `signOut()`, so every sign-out path sets it. */
export function markSignedOut(): void {
  signedOut = true;
}

/**
 * Releases the latch so a sibling's renewal may be adopted again.
 *
 * It does NOT assert that the tab is authenticated, and the name should not be read that way. It is called
 * from `retainAccessToken`, which `accessToken.ts:82` describes as "the only place a non-null token is
 * assigned" - so the reset sits at that single point rather than in a caller, and a second sign-in path
 * added later cannot silently skip it.
 */
export function markSignedIn(): void {
  signedOut = false;
}

/** Whether this tab has signed out and not since been handed a token. Read by `crossTabRenewal`'s listener. */
export function hasSignedOut(): boolean {
  return signedOut;
}

/**
 * Whether `data` is a sign-out.
 *
 * **Deliberately lenient about additional properties**, which is stricter-looking's opposite and is the
 * safer direction here. `BroadcastChannel` is same-origin, so what reaches this channel is not a hostile
 * page but our own code at a different version. If a later version posts `{ type, reason }`, a parser that
 * refused unknown properties would make an old tab IGNORE a sign-out - and an ignored sign-out is a tab
 * that stays signed in, which is exactly what R22 forbids. The failure modes are asymmetric: acting on a
 * slightly-unknown shape costs a redundant local clear; refusing one costs a live session.
 */
function isSignOut(data: unknown): boolean {
  return typeof data === 'object' && data !== null
    && (data as { type?: unknown }).type === SIGN_OUT_TYPE;
}

/**
 * Tells the app's other tabs that this one signed out. Fire-and-forget.
 *
 * A FRESH channel every call, closed immediately. Both halves are measured rather than assumed: a message
 * posted and then closed on the same tick is still delivered, and `postMessage` on an already-closed
 * channel throws a `DOMException` - which is why a cached channel would break the second broadcast, and
 * why `crossTabRenewal.ts:116` guards its own post with `if (!closed)`.
 *
 * An injected channel is the caller's and is NOT closed here.
 */
export function broadcastSignOut(channel?: BroadcastChannel): void {
  if (channel) {
    channel.postMessage({ type: SIGN_OUT_TYPE });
    return;
  }
  if (typeof BroadcastChannel !== 'function') return;
  const own = new BroadcastChannel(SIGN_OUT_CHANNEL);
  try {
    own.postMessage({ type: SIGN_OUT_TYPE });
  } finally {
    own.close();
  }
}

/**
 * Runs `onSignOut` when a sibling tab signs out.
 *
 * `onSignOut` is a parameter rather than an import, and that is what keeps the layering: this module is in
 * `api/`, which imports nothing from `auth/`, so the local sign-out action is handed in by whoever wires it
 * up. Signing out here must NOT broadcast, or two tabs would ping-pong - which is why `signOut()` sets the
 * latch and `endSession()` does the broadcasting, and why a receiver structurally cannot echo.
 *
 * `close()` is idempotent and closes only a channel this function created.
 */
export function listenForSignOut(
  onSignOut: () => void,
  options: { channel?: BroadcastChannel } = {},
): { close(): void } {
  const injected = options.channel;
  if (!injected && typeof BroadcastChannel !== 'function') return { close: () => undefined };
  const channel = injected ?? new BroadcastChannel(SIGN_OUT_CHANNEL);
  const onMessage = (event: MessageEvent) => {
    if (isSignOut(event.data)) onSignOut();
  };
  channel.addEventListener('message', onMessage);
  let closed = false;
  return {
    close() {
      if (closed) return;
      closed = true;
      channel.removeEventListener('message', onMessage);
      if (!injected) channel.close();
    },
  };
}
