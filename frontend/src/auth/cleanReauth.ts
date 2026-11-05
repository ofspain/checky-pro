import { APP_BASENAME, SIGN_IN_PATH } from '../app/routes';
import { clearAccessToken } from './accessToken';
import { rememberReturnTarget } from './returnTarget';
import { setSignInNotice } from './signInNotice';

/**
 * What a tab does when its session renewal fails: clean re-auth, with no half-session left behind (R22).
 *
 * WHERE THIS IS CALLED FROM, AND THE HONEST STATE OF THAT. `createSessionBoundary` takes an
 * `onRenewalFailed` hook (`api/sessionBoundary.ts:50`) and runs it once per failed renewal, in a microtask so a
 * throwing hook cannot affect the calls waiting on that renewal (`:69-74`). This module is that hook's body.
 *
 * **Nothing in production constructs the boundary today.** Every non-test reference to
 * `createSessionBoundary` in `src/` is a doc comment: task 1 mounts "an empty shell", tasks 5 and 6 built the
 * boundary and the cross-tab renewal, and no task in `tasks.md` wires them into the app. Nor does any task
 * build silent re-authentication itself - `accessToken.ts` says so in its own words, and `accessToken.renew()`
 * therefore always rejects. So three tasks of renewal machinery exist with no wiring to run in and no renewal
 * to run, and this module is correct code that is not yet reached (OQ-T33-2). That is written here rather than
 * implied, because the alternative is a later reader assuming this runs.
 *
 * WHY THE VISITOR LANDS ON THE SIGN-IN SCREEN rather than being sent straight back to the authorization server.
 * Silent re-authentication IS the renewal (L3's Q5 resolution): re-entering it automatically, on its own
 * failure, is the error loop R21 forbids. And `authorizeRequest.ts` sets no `prompt=none`, so an automatic
 * navigation would drop the visitor into the server-hosted login page with no action of their own, mid-task.
 * The sign-in screen is the start of the flow - it builds the authorize request when the visitor chooses - so
 * landing there is a transition to re-auth that the visitor consents to. `/app/signed-out` was the other
 * candidate and is wrong: it was built for a DELIBERATE sign-out (R19), and would state something false.
 */

/**
 * The tab's path inside the SPA, or `null` when the tab is not inside it.
 *
 * The trailing slash is stripped first, so `/app` and a future `/app/` behave alike - the same handling as
 * `authorizeRequest.ts:58-61`, for the same reason.
 */
function inAppPath(): string | null {
  const base = APP_BASENAME.endsWith('/') ? APP_BASENAME.slice(0, -1) : APP_BASENAME;
  const path = globalThis.location.pathname;
  if (path === base) return '/';
  return path.startsWith(`${base}/`) ? path.slice(base.length) : null;
}

/**
 * Builds the renewal-failure handler for one tab.
 *
 * **The handler takes no parameter.** `onRenewalFailed` is typed `(reason: unknown) => void`, and a
 * zero-argument function satisfies it, so "this module never reads the failure reason" is enforced by the type
 * system rather than by a scan hoping nobody adds a branch. L4 is why it must not: a locked, suspended or
 * deleted account, an expired token and an unreachable server must be indistinguishable to the visitor, and a
 * handler that cannot see the reason cannot leak it or branch on it. Do not add the parameter back.
 *
 * `navigate` is injected because the hook runs OUTSIDE React - the boundary is per tab, created at wiring time,
 * not in a component - so there is no `useNavigate` to call. It also keeps this module from importing the
 * router and closing a cycle; `routes.tsx:2-5` documents one such cycle already.
 *
 * Safe to run repeatedly. A tab can fail several renewals over its life and each is its own transition, so the
 * side effects are all idempotent: clearing is idempotent by `clearAccessToken`'s own contract, the notice is
 * single-read and overwritten, a non-allowlisted return target is silently ignored, and navigating to the
 * screen the tab is already on is a no-op in the router. Nothing here needs to ask where the tab is.
 */
export function createRenewalFailureHandler(navigate: (to: string) => void): () => void {
  return () => {
    // FIRST, and the order is load-bearing. Everything below can throw - a navigate supplied by the wiring, a
    // storage write - and a notice set or a navigation started while a token is still held is exactly the
    // half-authenticated tab R22 forbids. Clearing first means the worst case is a cleared session that did
    // not announce itself, never a screen that looks signed in.
    clearAccessToken();

    // Where the visitor was, so re-auth returns them there (L1). `rememberReturnTarget` applies the allowlist
    // itself and silently ignores a target it could not honour, so there is no branch here for the tab that is
    // already on a public route. It preserves the LOCATION, not the state of whatever was interrupted - what a
    // screen shows after the return is that screen's business.
    const here = inAppPath();
    if (here !== null) rememberReturnTarget(here);

    setSignInNotice('session-ended');

    // Read inside the function, never at module scope. `returnTarget.ts:40-49` measured in T16 that a
    // module-scope read of a route constant yields `undefined` rather than throwing, which is the worst of
    // both worlds: it fails closed SILENTLY. Basename-relative, which is what the router's navigate takes.
    navigate(`/${SIGN_IN_PATH}`);
  };
}
