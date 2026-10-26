/**
 * The one fact a discarded callback carries to the sign-in screen (R8: "return the visitor to the sign-in start
 * with neutral copy").
 *
 * In memory, module-scoped, and single-read. It is NOT in session storage, because L16's carve-out is exhaustive
 * and names four items; this is not one of them. It is not in history state either, which the browser persists
 * across a reload - wrong for a notice that should die with the navigation that caused it. Taking it clears it,
 * so a refreshed sign-in page does not keep scolding the visitor.
 *
 * The union has one member today. It is a forward-compatibility hook, not a commitment: task 18 may add a value
 * for a mismatched state, or may reuse this one, since L4 forbids copy that distinguishes the two cases.
 */
export type SignInNotice = 'discarded';

let pending: SignInNotice | null = null;

export function setSignInNotice(notice: SignInNotice): void {
  pending = notice;
}

/** Returns the pending notice and clears it. Returns null when there is none, which is the ordinary case. */
export function takeSignInNotice(): SignInNotice | null {
  const taken = pending;
  pending = null;
  return taken;
}
