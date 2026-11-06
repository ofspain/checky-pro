import { clearAccessToken } from './accessToken';
import { consumeAuthorizeState } from './authorizeRequest';
import { takeIdToken } from './idToken';
import { clearReturnTarget } from './returnTarget';
import { takeSignInNotice } from './signInNotice';

/**
 * Sign-out, the clearing half (R19 clause 1, task 35).
 *
 * **What this module is NOT.** R19 has two clauses and this is the first: "clear in-memory token state". The
 * second - ending the SAS session through `/connect/logout` with an allowlisted `post_logout_redirect_uri` - is
 * task 36, and it will call this before it navigates. Broadcasting to sibling tabs is task 39 (R22, L18). The
 * `/app/signed-out` screen and its copy are task 37. None of those are started here, and this file renders
 * nothing and says nothing, so L11 has no string to govern.
 *
 * **It composes; it does not reach.** Every piece of session state the SPA holds already has a module that owns
 * it and a primitive that clears it, and each of those primitives is already idempotent and already guarded
 * against a storage surface that throws. So this function assigns nothing and calls no storage API itself: the
 * knowledge of WHICH key or WHICH variable holds a thing stays in the module that writes it, and a second
 * spelling of a key here is exactly what task 22's scan forbids.
 *
 * **It takes nothing and returns nothing**, and both are load-bearing. There is no session identifier to pass,
 * because the state is module-scoped; and with no parameter there is no reason, status or outcome to branch on,
 * which makes L4's indifference a property of the signature rather than a discipline someone must maintain. It
 * reports nothing back either - not what was held, not whether anything was, not whether a tidy-up was refused.
 *
 * **It is synchronous, and that is a requirement rather than a convenience.** Task 36's step is a full-page
 * navigation to an identity path (R57, and `/connect/logout` is on the service-worker navigation denylist), and
 * a full-page navigation does not wait for a microtask. Everything here must therefore be done by the time the
 * call returns. Nothing is deferred, awaited or scheduled.
 *
 * **NOTHING IN THE SPA CALLS THIS, AND NOTHING CAN.** No authenticated route has an element, `AppShell.tsx`
 * renders a bare `<div>`, and no task in `tasks.md` owns a sign-out affordance - so there is no button, menu or
 * keyboard path to reach it. Tasks 5, 6, 30, 31 and 33 are in the same position, and tasks 36 to 39 will join
 * them; this is the sixth. Recorded rather than papered over, as OQ-T35-2. The acceptance criteria are verified
 * by direct invocation, which is honest about what they prove: how this behaves when it is called, not that it
 * is. What the suite does guard is the shape of the eventual wiring - no other module may call these primitives
 * outside a measured allowlist, so a partial sign-out hand-rolled beside this one fails a test rather than
 * shipping.
 *
 * **A HAZARD THIS FUNCTION CANNOT CLOSE (task 39 owns it, OQ-T35-3).** A tab that has signed out can be handed a
 * live token again by a sibling. `crossTabRenewal.ts`'s channel listener calls `local.adopt(token)` on any
 * `renewal:succeeded` message and does not ask whether this tab signed out, so: this tab signs out; a sibling
 * that has not processed any sign-out completes a renewal and posts; this tab adopts. Task 39's propagation
 * narrows the window without closing it, because a sibling whose renewal resolves before it handles the
 * broadcast still posts a success and the channel orders nothing. It is not fixable from here: this function
 * cannot reach the `SharedRenewal` closure, and the alternative - a latch that makes `adopt` refuse - means
 * ADDING a piece of state in the task whose whole statement is removing it. The suite pins the sequence instead,
 * so whoever builds task 39 meets it as a failing expectation rather than a paragraph.
 *
 * **TWO MORE THINGS THIS FUNCTION DOES NOT DO, named so nobody has to wonder.** It does not abort requests
 * already in flight: `sessionBoundary.ts` reads `current()` as each request is built and never caches it, so
 * every request built after this call carries no token, but one already on the wire outlives the sign-out by
 * its own round trip. And it does not clear the boundary's own pending-renewal map, which lives in a factory
 * closure a zero-argument function cannot reach - so a tab that signs out mid-renewal clears its token while
 * the boundary may still settle calls that were waiting. Both belong with whoever wires the boundary up;
 * neither is reachable from here, and neither is a thing a parameter would fix, because the parameter would
 * have to be the boundary itself.
 */
export function signOut(): void {
  // FIRST, and the only one of the four whose position matters.
  //
  // Precisely why: there is NO try/catch here, so a throw from any step would abandon the steps after it.
  // None of the three below can throw today - each is guarded inside its own module - so the question does
  // not arise in practice; but "cannot throw" is a property of code that may be edited, and the clear is the
  // one step whose omission is a security defect rather than an untidy store. With it first, the worst a
  // later failure can leave behind is residue: no token, which is what R22 actually forbids. A guard around
  // each call would be dead code for a condition that cannot occur, and would hide the one that can.
  // Task 33's handler clears first for the same reason.
  clearAccessToken();

  // SECOND, and for the first step's reason rather than for a reason of its own (task 36). The ID token is a
  // credential too - a signed assertion of who signed in - so L3 governs it as it governs the access token, and
  // it belongs with the clear above rather than among the three stores below. It is second rather than first
  // because, between two credentials, the bearer token is the one an attacker can spend.
  //
  // The value is discarded here, as `takeSignInNotice()`'s is. `endSession()` calls this same primitive first,
  // for the `id_token_hint` it is about to send, so on that path this call finds `null` and clears nothing.
  // That is what makes the ID token gone whether sign-out was entered through `endSession()` or through this
  // function alone, without either having to know about the other (`idToken.ts:58-70`).
  takeIdToken();

  // Discards a notice `cleanReauth` may have queued. Without this, a visitor who signs out deliberately and
  // then returns to sign in reads "Your session has ended" - true of a renewal that failed, false and faintly
  // alarming for someone who chose to leave. Task 37's screen will say what a deliberate sign-out means.
  takeSignInNotice();

  // The three PKCE items, for a sign-out while a second sign-in is mid-flight: `state`, `nonce` and
  // `code_verifier` are written together by the authorize step and are useless to anyone once this tab has no
  // session. Its header frames it as the callback's cleanup, because the callback was its only caller until
  // now - it is called here for the contract it states (exactly its three keys, idempotent, never throws, never
  // clears the store), not as a general tidy-up, which that header is right to forbid.
  consumeAuthorizeState();

  // Last, and not because anything depends on it being last. Removing rather than consuming: sign-out has no
  // navigation to feed, so `takeReturnTarget`'s destination would be a value with nowhere to go (T24-D4). Left
  // behind, this would send the NEXT sign-in - possibly a different person on a shared machine - to wherever
  // this visitor happened to be.
  clearReturnTarget();
}
