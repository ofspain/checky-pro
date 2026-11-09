import type { ReactElement } from 'react';
import { Navigate, useLocation } from 'react-router';
import { accessToken } from './accessToken';
import { rememberReturnTarget } from './returnTarget';
import { SIGN_IN_PATH } from '../app/routes';

/**
 * R28's SECOND clause: a visitor with no session who asked for anything but the nine public routes is sent to
 * the sign-in start, keeping the link they came for where the allowlist permits it.
 *
 * Task 40 built the first clause and left every blocked route rendering BLANK, which is not a refusal so much
 * as a dead end: no way forward, and the deep link discarded. One component answers all THREE arrivals R28's
 * "any other route" denominates - a blocked authenticated path (`RequireSession`), a path matching nothing
 * (the catch-all), and the bare `/app/` (the index route) - so they are identical by construction and not by
 * three separate pieces of care. L4 asks for exactly that: a redirect is observable to an unauthenticated
 * prober, so destination, history action and rendered output must not differ between them.
 *
 * **IT RETURNS `null` FOR AN AUTHENTICATED VISITOR.** R28 is conditioned "WHILE a visitor is unauthenticated",
 * so a signed-in visitor at a path that matches nothing is outside the requirement, and `tasks.md:72` says
 * only that a session renders the shell and the authenticated routes. Redirecting them would be a claim this
 * package has not made. For the gate's own arrival the question has already been asked one component up;
 * asking again costs one property read and buys one component instead of two.
 *
 * **THE ARRIVAL IS READ FROM `useLocation()`, WHICH IS BASENAME-RELATIVE.** That is the form the allowlist in
 * `returnTarget.ts` holds (`/account`), so no conversion happens here at all. The body says what was measured
 * and what the first version of this file got wrong about it.
 *
 * **IT CLAIMS ONLY WHAT IT CHECKS.** A held token does not prove the account is usable - a SUSPENDED account
 * keeps a working token, and `/accounts/me` is the only thing that can say otherwise (task 31).
 *
 * **THE RETURN TARGET IS WRITTEN DURING RENDER, AND `StrictMode` WRITES IT TWICE.** Measured at phase 4: the
 * component renders twice under double-invocation, with the same value both times. `rememberReturnTarget` is
 * a single `setItem` of a value derived only from the arrival, so the outcome is identical - which is why
 * every test here asserts the STORED VALUE and none asserts a write count. A `useLayoutEffect` would make it
 * one write and cost more than it bought: the write would then happen after the navigation decision, and the
 * hook is a surface this module's source scan otherwise forbids.
 *
 * **IF YOU ARE ABOUT TO TURN THIS INTO A `loader`, READ THIS FIRST.** All three traps were measured at phase 4:
 *   1. `redirect()` **pushes**. The visitor can press Back and land on the route that just refused them, which
 *      is the trap `returnTargetOutsideAllowlistFallsBackToHome.test.tsx:599` exists to forbid. `replace()` is
 *      the helper that replaces, and nothing but `historyAction` will tell you which one you used.
 *   2. `redirect('/app/sign-in')` double-applies the basename. `/app/app/sign-in` matches nothing, the
 *      catch-all's loader fires again, and the process dies of memory exhaustion. Loader targets are
 *      basename-relative.
 *   3. A loader carries no `element`, so a loader-based redirect is invisible to `routesWithElements`, to
 *      task 37's AC9 and to task 40's AC13 - the pin written one task ago for exactly this hazard. Grow those
 *      three first, or the redirect becomes deletable with no failing test.
 */
export function RedirectToSignIn(): ReactElement | null {
  // FIRST, and the order is load-bearing: everything below is for the unauthenticated visitor only, and a
  // return target written for someone who already has a session would be a stale deep link waiting in their
  // storage for a sign-in that is not coming.
  const hasSession = Boolean(accessToken.current());
  if (hasSession) return null;

  // ALREADY BASENAME-RELATIVE, AND THIS LINE USED TO STRIP THE BASENAME OFF IT (phase 9, Kimi I6).
  //
  // `useLocation().pathname` is relative to the router's basename; it is `router.state.location.pathname` that
  // carries the `/app` prefix. Phase 4 measured the second and the header here claimed the first, so this
  // module shipped a strip that could never fire. Measured again at phase 9, six arrivals through BOTH
  // `createMemoryRouter` and the production `createBrowserRouter`, and they agree exactly:
  //
  //     arrival             useLocation().pathname     router.state.location.pathname
  //     /app/account        /account                   /app/account
  //     /app/  and  /app    /                          /app/  and  /app
  //     /app/no-such-route  /no-such-route             /app/no-such-route
  //     /app/sign-in        /sign-in                   /app/sign-in
  //     /app/app/account    /app/account               /app/app/account
  //
  // The strip was not merely dead: the last row is a path that MATCHES THE CATCH-ALL, and `/app/account`
  // starts with `/app/`, so the strip turned a route the visitor never asked for into a remembered `/account`.
  // Removing it is what makes `rememberReturnTarget`'s allowlist the only filter, which is what the next
  // paragraph claims.
  //
  // WHY THE WRONG PREMISE WAS PLAUSIBLE: `cleanReauth.ts:43`'s `inAppPath()` performs exactly this strip and
  // is RIGHT to, because it reads `globalThis.location.pathname`, which is a real URL and does carry the
  // prefix. Copying a shape from a module that reads a different source carried its premise along with it.
  const { pathname } = useLocation();
  const arrival = pathname;

  // Unconditionally, with the allowlist as the only filter. There is deliberately no branch for the arrival
  // that matched nothing, for the bare `/app/`, or for a parameterised path: `rememberReturnTarget` refuses
  // what it cannot honour and says nothing (`returnTarget.ts:86`), and `invoices/:id` is not in the allowlist
  // at all (T24-D5), so `/invoices/123` redirects and remembers nothing. A branch here would be a second
  // copy of a rule that already has one place to live.
  rememberReturnTarget(arrival);

  // `SIGN_IN_PATH` read INSIDE the function, never at module scope: T16 measured that a module-scope read of
  // a route constant yields `undefined` rather than throwing, and `/undefined` is a destination that matches
  // the catch-all - which would redirect to itself. `replace` so the visitor cannot press Back into the route
  // that refused them.
  return <Navigate to={`/${SIGN_IN_PATH}`} replace />;
}
