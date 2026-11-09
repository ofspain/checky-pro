import { createBrowserRouter, type RouteObject } from 'react-router';
// This import closes a cycle: routes -> SignInScreen -> authorizeRequest -> routes, because authorizeRequest reads
// APP_BASENAME to derive the redirect URI. It resolves because APP_BASENAME is read inside a function there, never
// at module scope, so nothing touches the binding while it is still in its temporal dead zone. That property is
// pinned by a test in the sign-in suite; if it is ever broken the application fails to start, not a test.
// The three imports below it are not part of that cycle (phase 8, I13): the two screens and the T40 guard
// each read what they need inside a component, so none of them touches a route constant at module scope.
import { CallbackScreen } from '../auth/CallbackScreen';
import { SignedOutScreen } from '../auth/SignedOutScreen';
import { SignInScreen } from '../auth/SignInScreen';
import { RequireSession } from '../auth/RequireSession';

/** Every SPA route lives under this prefix, which is disjoint from the backend prefixes the edge sends to the services (L14). */
export const APP_BASENAME = '/app';

/** Where a discarded callback returns the visitor (R8). Published so the callback does not repeat the literal. */
export const SIGN_IN_PATH = 'sign-in';

/** Where the authorization server returns the browser (R8), published for the same reason. */
export const CALLBACK_PATH = 'callback';

/** Where a completed sign-in lands when no allowlisted return target was remembered (L1, T24). One spelling. */
export const HOME_PATH = 'home';

/** Where SAS returns the browser after the end-session call (R19, task 36). Published in task 37, which is why
 * `endSession.ts` no longer declares it privately: one spelling, and `post_logout_redirect_uri` is built from it. */
export const SIGNED_OUT_PATH = 'signed-out';

/** Routes reachable without a session (design.md §4c), relative to APP_BASENAME. T40 enforces the split: these
 * nine stay direct children of the root, while the authenticated eleven move under `RequireSession` below. */
export const PUBLIC_ROUTE_PATHS = [
  SIGN_IN_PATH,
  CALLBACK_PATH,
  'register',
  'register/sent',
  'verify',
  'resend-verification',
  'reset/request',
  'reset',
  SIGNED_OUT_PATH,
] as const;

/** Routes that need a session (design.md §4c), relative to APP_BASENAME. */
export const AUTHENTICATED_ROUTE_PATHS = [
  HOME_PATH,
  'account',
  'account/sessions',
  'account/mfa',
  'api-keys',
  'admin',
  'invoices',
  'invoices/:id',
  'payments',
  'notifications',
  'receipts/:id',
] as const;

/** The single source of the route set. Screens are attached by their own tasks. The catch-all keeps
 * React Router's default English 404 from rendering (L11); T41 replaces it with the sign-in redirect. */
export const appRoutes: RouteObject[] = [
  {
    path: '/',
    children: [
      ...PUBLIC_ROUTE_PATHS.map((path) => {
        if (path === SIGN_IN_PATH) return { path, element: <SignInScreen /> };
        if (path === CALLBACK_PATH) return { path, element: <CallbackScreen /> };
        if (path === SIGNED_OUT_PATH) return { path, element: <SignedOutScreen /> };
        return { path };
      }),
      // TASK 40 - the auth gate (R28's first clause). ONE pathless layout route decides for all eleven, so
      // there is a single place the question is asked and a screen attached later nests inside the gate
      // rather than replacing it. Pathless deliberately: `path: ''` would be a string in the route tree and
      // would change what the bare `/app/` matches.
      { element: <RequireSession />, children: AUTHENTICATED_ROUTE_PATHS.map((path) => ({ path })) },
      // OUTSIDE the gate, on purpose. The catch-all must answer without a session, because task 41 replaces
      // it with the redirect to the sign-in start - and a catch-all behind the gate could not redirect the
      // visitor who has no session, which is the only visitor it is for.
      { path: '*' },
    ],
  },
];

export function createAppRouter() {
  return createBrowserRouter(appRoutes, { basename: APP_BASENAME });
}
