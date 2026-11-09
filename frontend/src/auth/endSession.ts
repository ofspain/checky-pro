import { broadcastSignOut } from '../api/crossTabSignOut';
import { APP_BASENAME, SIGNED_OUT_PATH } from '../app/routes';
import { takeIdToken } from './idToken';
import { signOut } from './signOut';

/**
 * Sign-out, the hand-over half (R19 clause 2, task 36).
 *
 * Clause 1 cleared this tab (`signOut.ts`, task 35). On its own that is cosmetic: the SAS session cookie the
 * browser still holds survives, and the next `/oauth2/authorize` returns a code without asking for anything -
 * so a visitor who believed they signed out would be signed back in by the act of visiting. This module is
 * what makes the sign-out real, by giving the browser to the authorization server's end-session endpoint.
 *
 * **Why this is a full-page navigation, and why that is the opposite of two neighbouring rules.** Task 33's
 * `cleanReauth.ts` and task 35's `signOut.ts` are both forbidden to touch `globalThis.location`, each pinned by
 * a scan over its own source. Those rules are right for those modules and wrong for this one. SAS serves the
 * end-session response itself (R57 puts `/connect/logout` on the service-worker navigation denylist precisely
 * so the worker cannot answer it from cache), and a router navigation cannot leave the SPA, so there is no
 * version of this module that satisfies clause 2 without handing the browser away. Said out loud because a
 * reader who knows the other two rules would otherwise read this as a violation of them.
 *
 * It also means **this cannot live in `signOut.ts`**: that file's own scan forbids `navigate` in it
 * (`signOutClearsMemoryAndEndsSasSession.test.tsx:888`). Two modules, not one, for that reason.
 *
 * **What is NOT here.** No `/oauth2/revoke` - Q5 removed it from R19 on 2026-10-10, and no refresh token is
 * issued to this client, so there is nothing to revoke. No `state` parameter: SAS does not require one, the
 * allowlisted `post_logout_redirect_uri` is the security control (the backend answers `400` to anything
 * outside the allowlist, `SasLoginIntegrationTest:502`), and the only thing `state` would buy is correlation
 * that `/app/signed-out` - a public route with neutral copy and no action to authorize - has no use for. No
 * copy and no screen: what renders at `/app/signed-out` is task 37's. No propagation to sibling tabs: task 39's.
 *
 * **Nothing calls this.** The sign-out affordance is unowned - no task in `tasks.md` builds a button, menu or
 * route that would reach it - so like tasks 5, 6, 30, 31, 33 and 35 this is built and dormant, and its
 * criteria are verified by direct invocation only (OQ-T36-5).
 */

/**
 * Spring Authorization Server's end-session endpoint, reached through the edge on this origin.
 *
 * Published so there is one spelling. The second one already exists at `pwa/navigationDenylist.ts:19`, where
 * this path is a denylist entry rather than a destination; that module is task 10's and deliberately holds a
 * literal list, and making it import from `auth/` would create a dependency from the service-worker layer to
 * the auth layer for the sake of a string. The two are held equal by a test instead, which is the cheaper of
 * the two and the one that edits no closed module.
 */
export const END_SESSION_PATH = '/connect/logout';

/**
 * The post-logout target, derived rather than configured.
 *
 * The same derivation `authorizeRequest.ts:59-62` performs for the callback, including the de-slash:
 * `APP_BASENAME` is `/app` today, but a future `/app/` would otherwise produce `/app//signed-out`, which an
 * exact-match comparison against the registered allowlist rejects - and the rejection would surface as a `400`
 * at sign-out with nothing failing here first.
 *
 * `APP_BASENAME` is read INSIDE the function, never at module scope. `routes.tsx:2-5` documents the import
 * cycle that discipline exists for; this module is not in that cycle today, and reading it here is what keeps
 * it from joining one.
 *
 * The frontend cannot check this against the backend's registered allowlist - it has no access to it at
 * runtime. This derives the shape; `SasLoginIntegrationTest:475` and `:502` pin the registration and the
 * refusal. Two halves, in the two places that can each hold one.
 */
function postLogoutRedirectUri(): string {
  const base = APP_BASENAME.endsWith('/') ? APP_BASENAME.slice(0, -1) : APP_BASENAME;
  return `${globalThis.location.origin}${base}/${SIGNED_OUT_PATH}`;
}

/**
 * The end-session URL: exactly two parameters, and one of them conditional.
 *
 * When `hint` is `null` the parameter is **absent**, not empty. The difference matters: `URLSearchParams` would
 * serialise `''` into `id_token_hint=`, which asks SAS to end the session identified by the empty string, and a
 * `?? ''` is the shape that mistake takes. Whether SAS honours a `post_logout_redirect_uri` with no hint at all
 * is OQ-T36-4, unmeasured - both backend tests supply one. Navigating anyway is still right: a failed
 * end-session leaves the visitor locally signed out and confused, while skipping the hand-over would leave them
 * signed in and reassured.
 */
function endSessionUrl(hint: string | null): string {
  const parameters = new URLSearchParams();
  if (hint !== null) parameters.set('id_token_hint', hint);
  parameters.set('post_logout_redirect_uri', postLogoutRedirectUri());
  return `${globalThis.location.origin}${END_SESSION_PATH}?${parameters.toString()}`;
}

/**
 * Clears this tab, then hands the browser to the authorization server.
 *
 * **The order is the criterion.** The token is taken first and `signOut()` runs second, so by the time the
 * browser leaves, this tab holds nothing - a hand-over that happened first would leave a tab navigating away
 * while still holding a credential. The URL is built last, after the clearing is complete, so that nothing
 * derived from the route table can fail while state is still held.
 *
 * `takeIdToken()` here rather than reading and letting `signOut()` clear: there is no reader to read with
 * (`idToken.ts` exports none, deliberately), so the read and the clear cannot come apart. `signOut()`'s own
 * call to the same primitive then finds `null`.
 *
 * No parameter and no return value, both load-bearing. There is no session identifier to pass and nothing to
 * branch on; and after the navigation there is no caller left to act on a result. `assign` rather than
 * `replace`, so the SPA stays in history - a visitor who presses Back lands on a route that will send them to
 * sign-in rather than on a blank entry.
 *
 * Not idempotent, and deliberately not made so: a second call clears nothing further, builds a URL with no
 * `id_token_hint` (the first consumed it) and hands over again. Unreachable in a real browser, where the first
 * navigation has already left the page. A latch would be module-level mutable state - the one thing this module
 * must not hold - traded for a second navigation that cannot happen.
 */
export function endSession(): void {
  const hint = takeIdToken();
  signOut();
  // TASK 39 (R22, L18). AFTER the local sign-out, so this tab is already clean before it tells anyone, and
  // BEFORE the navigation, because `assign` may unload the page - a message posted and then closed on the
  // same tick is still delivered, which is measured in `crossTabSignOut.ts`, so the post is safely queued
  // by the time the unload begins.
  //
  // It is HERE and not inside `signOut()`, and that placement is what makes a receiving tab unable to echo:
  // a tab that handles a sign-out broadcast signs out by calling `signOut()`, which sets the latch and
  // broadcasts nothing. No sender id, no "ignore my own message" flag, nothing to get wrong. It also lands
  // R19 correctly - the SAS session is ended ONCE, by the tab the visitor acted in, rather than by N tabs
  // navigating to `/connect/logout` for one intent.
  broadcastSignOut();
  globalThis.location.assign(endSessionUrl(hint));
}
