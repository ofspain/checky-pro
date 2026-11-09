import type { ReactElement } from 'react';
import { Outlet } from 'react-router';
import { accessToken } from './accessToken';

/**
 * The auth gate: R28's first clause, and the first thing in `src/` that makes a route require a session.
 *
 * Applied ONCE, as the pathless layout route whose children are `AUTHENTICATED_ROUTE_PATHS`
 * (`app/routes.tsx`). One instance decides for all eleven, so there is a single place the question is asked,
 * and a screen attached later by task 42 nests INSIDE the gate rather than replacing it - which is why this
 * is a layout route and not eleven copies of an element on eleven paths. The eleven-copy shape would also
 * have taken the element slot task 42 needs, and driven both screen-count pins from three to fourteen on
 * the day it landed.
 *
 * **IT RENDERS NOTHING RATHER THAN REDIRECTING.** R28's second clause - send anything else to the sign-in
 * start - is TASK 41's, and rendering nothing silently is the established behaviour here for a route that
 * must not render: the catch-all has done exactly this since task 1 (L11, and
 * `app/routesResolveUnderAppPrefixOnly.test.tsx:124-137`). Taking the redirect here would also take the
 * return target and the replace-vs-push decision with it, neither of which this task owns.
 *
 * **IT CLAIMS ONLY WHAT IT CHECKS.** A held token does NOT prove the account is usable: a SUSPENDED account
 * keeps a working token, and `/accounts/me` is the only thing that can say otherwise (task 31). This gate
 * answers one question - is a token held - and nothing downstream may read more into a rendered outlet than
 * that.
 *
 * **IT IS A READ AT RENDER, NOT A SUBSCRIPTION (OQ-T40-1).** The session owner publishes no subscribe API -
 * `accessToken.ts` exports `current`, `adopt`, `renew`, `retainAccessToken` and `clearAccessToken`, and the
 * only `useSyncExternalStore` in `src/` is `app/RateLimitNotice.tsx`. So a token arriving with NO navigation
 * would leave an already-mounted blocked route blocked. Nothing does that today: the callback screen
 * navigates after retaining, and a navigation re-renders the matched tree. Adding a subscription would be a
 * new surface on the session owner, which this task is not authorized to touch.
 */
// The return type is STATED rather than inferred (phase 8, I2): the one thing a reader of a route guard
// needs to know is that it may render nothing, and inference puts that behind a compiler run.
export function RequireSession(): ReactElement | null {
  // `Boolean(...)` rather than `!== null`, because `retainAccessToken` assigns whatever string it is given
  // (`accessToken.ts:90` is `held = token;`, with no guard), so `current()` can return the empty string -
  // and an empty token is not a session. The brief's constraint says so; this is where it is enforced.
  // Named rather than inlined into the ternary, so `Boolean` is a conversion and not a cast oxlint would
  // strip as redundant in a condition position.
  const hasSession = Boolean(accessToken.current());
  return hasSession ? <Outlet /> : null;
}
