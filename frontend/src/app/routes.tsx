import { createBrowserRouter, type RouteObject } from 'react-router';
// This import closes a cycle: routes -> SignInScreen -> authorizeRequest -> routes, because authorizeRequest reads
// APP_BASENAME to derive the redirect URI. It resolves because APP_BASENAME is read inside a function there, never
// at module scope, so nothing touches the binding while it is still in its temporal dead zone. That property is
// pinned by a test in the sign-in suite; if it is ever broken the application fails to start, not a test.
// The four imports below it are not part of that cycle (T40 phase 8, I13 - a past review, not this task's):
// the two screens, the T40 guard and the T41 redirect each read what they need inside a component, so none of
// them touches a route constant at module scope. `RedirectToSignIn` is the one that could have: it reads
// SIGN_IN_PATH, declared in this file, and reads it inside the function body for exactly this reason.
import { CallbackScreen } from '../auth/CallbackScreen';
import { SignedOutScreen } from '../auth/SignedOutScreen';
import { SignInScreen } from '../auth/SignInScreen';
import { RedirectToSignIn } from '../auth/RedirectToSignIn';
import { RequireSession } from '../auth/RequireSession';
import { RegisterScreen } from '../features/account/RegisterScreen';
import { RegisterSentScreen } from '../features/account/RegisterSentScreen';
import { PasswordResetRequestScreen } from '../features/account/PasswordResetRequestScreen';
import { ResendVerificationScreen } from '../features/account/ResendVerificationScreen';
import { VerifyScreen } from '../features/account/VerifyScreen';
import { AuthenticatedLayout } from './AuthenticatedLayout';
import { HomeScreen } from './HomeScreen';

/** Every SPA route lives under this prefix, which is disjoint from the backend prefixes the edge sends to the services (L14). */
export const APP_BASENAME = '/app';

/** Where a discarded callback returns the visitor (R8). Published so the callback does not repeat the literal. */
export const SIGN_IN_PATH = 'sign-in';

/** Where the authorization server returns the browser (R8), published for the same reason. */
export const CALLBACK_PATH = 'callback';

/** Where a completed sign-in lands when no allowlisted return target was remembered (L1, T24). One spelling. */
export const HOME_PATH = 'home';

/** Where a visitor creates an account (R1, task 43). Published for the same reason as the four above: the
 * screen, the route table and the suites that pin the set all need one spelling of it. */
export const REGISTER_PATH = 'register';

/** Where a submitted registration lands (R2, task 47). Published for the same reason as the five above, and for
 * one more: the form NAVIGATES here, so the spelling is now in a `navigate()` call as well as in the route
 * table and the suites that pin the set. It was a bare literal in `PUBLIC_ROUTE_PATHS` from task 16 until this
 * task attached an element - which is exactly the state L14's house rule exists to end, since a path can only
 * be attached by matching a constant. */
export const REGISTER_SENT_PATH = 'register/sent';

/** Where an emailed verification link lands (R3, task 48). The token rides a query parameter -
 * `/app/verify?token=<token>` - which is not this task's invention: `design.md:145` is an author-confirmed
 * note fixing that shape, notification-service builds the link from it, and its own integration test asserts
 * the email body contains it. A bare literal here from task 16 until this task attached an element, for the
 * same reason `REGISTER_SENT_PATH` was: L14 attaches a path by matching a constant, and the spelling is now
 * referred to by name from more than one place. */
export const VERIFY_PATH = 'verify';

/** Where the resend-verification form lives (R5, tasks 50/51).
 *
 * **PROMOTED BY TASK 49 WITHOUT AN ELEMENT, AND ATTACHED BY TASK 50** - the reverse of how the five above
 * arrived, each of them promoted by the task that attached its screen. Task 49 needed the spelling by name
 * before the screen existed, because R4's uniform failure carries a resend action: the second half of
 * `VERIFY_PATH`'s own rationale, and the house rule the only other link in the bundle already follows
 * (`SignedOutScreen` links through `SIGN_IN_PATH`, never a literal).
 *
 * This paragraph used to end "the path stays element-less until task 50, so the link really does land on a
 * blank document today; that is a known interim state, recorded at the phase 4 gate". **That is now false,
 * and it was falsified by an edit to this file** - task 50 attached the element forty lines below. It is
 * rewritten rather than deleted because the interim state is the reason this constant reads the way it does,
 * and because task 49's handoff case existed precisely to make someone come back here. */
export const RESEND_VERIFICATION_PATH = 'resend-verification';

/** TASK 52 - the password-reset request form (R14). The path was ALREADY in `PUBLIC_ROUTE_PATHS` below as a
 * bare string literal, declared and public since the list was written and rendering an empty document, which is
 * the state task 49 left `resend-verification` in and task 50 picked up. The difference is that task 49 LINKED
 * to its path before anything rendered there, and nothing in this bundle links here: the screen is reachable by
 * typing the URL only (OQ-T52-3, accepted at the phase 4 gate as a known demo-path hole, not an oversight).
 *
 * Promoted rather than left a literal because `PUBLIC_ROUTE_PATHS` and the element attachment below must
 * agree on one spelling, and `routesResolveUnderAppPrefixOnly` hard-codes it a third time. `'reset'`, the
 * token-bearing confirmation screen, stays a literal: it is task 53's. */
export const PASSWORD_RESET_REQUEST_PATH = 'reset/request';

/** Where SAS returns the browser after the end-session call (R19, task 36). Published in task 37, which is why
 * `endSession.ts` no longer declares it privately: one spelling, and `post_logout_redirect_uri` is built from it. */
export const SIGNED_OUT_PATH = 'signed-out';

/** Routes reachable without a session (design.md §4c), relative to APP_BASENAME. T40 enforces the split: these
 * nine stay direct children of the root, while the authenticated eleven move under `RequireSession` below. */
export const PUBLIC_ROUTE_PATHS = [
  SIGN_IN_PATH,
  CALLBACK_PATH,
  REGISTER_PATH,
  REGISTER_SENT_PATH,
  VERIFY_PATH,
  RESEND_VERIFICATION_PATH,
  PASSWORD_RESET_REQUEST_PATH,
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

/** The single source of the route set. Screens are attached by their own tasks. The catch-all kept React
 * Router's default English 404 from rendering (L11); task 41 gave it the sign-in redirect, which is also what
 * the index route and the gate's negative branch render. */
export const appRoutes: RouteObject[] = [
  {
    path: '/',
    children: [
      // TASK 41 - the bare `/app/`, which matched the root alone and rendered nothing. An INDEX route is what
      // react-router matches for it, and measured at phase 4 it takes `/app` and `/app/` while leaving
      // `sign-in` and the catch-all exactly where they were. It carries no `path`, so the screen-count pins
      // cannot see it - which is why task 40's AC13, the pin for an element on a pathless route, is the
      // instrument that holds it.
      { index: true, element: <RedirectToSignIn /> },
      ...PUBLIC_ROUTE_PATHS.map((path) => {
        if (path === SIGN_IN_PATH) return { path, element: <SignInScreen /> };
        if (path === CALLBACK_PATH) return { path, element: <CallbackScreen /> };
        if (path === SIGNED_OUT_PATH) return { path, element: <SignedOutScreen /> };
        // TASK 43 - the registration screen, the fourth public path to get an element. The conditional is
        // the one that was already here: a path is attached by matching its constant, never by adding one
        // (L14).
        if (path === REGISTER_PATH) return { path, element: <RegisterScreen /> };
        // TASK 47 - the acknowledgement, the fifth, and the end of the sentence above: this file said
        // `register/sent` "stays element-less because task 47 owns the acknowledgement" from task 43 until
        // now. Attaching it is what let task 43's `does not navigate` case be inverted rather than deleted -
        // that case chose against navigating to a path with no element, and this line is the condition it
        // named. It is also why the literal above became a constant: the form navigates to this spelling.
        if (path === REGISTER_SENT_PATH) return { path, element: <RegisterSentScreen /> };
        // TASK 48 - the verification screen, the sixth, and the first element on a path that an EMAIL points
        // at rather than a link inside the application. Nothing in the SPA navigates here; the visitor
        // arrives from their inbox with a one-time token in the query string, which is why this screen reads
        // a search parameter and `RegisterSentScreen` is forbidden to.
        if (path === VERIFY_PATH) return { path, element: <VerifyScreen /> };
        // TASK 50 - the resend form, the seventh, and the only one of them whose PATH was already public and
        // already linked to before anything rendered on it. Task 49 promoted the constant above and
        // deliberately attached nothing, so for one task R4's resend action pointed at a blank document; this
        // line is what task 49's handoff case was written to force a reader back to.
        if (path === RESEND_VERIFICATION_PATH)
          return { path, element: <ResendVerificationScreen /> };
        // TASK 52 - the password-reset request form (R14), the eighth public screen and the second whose
        // PATH was already declared and public for longer than this route table has had an element for it.
        // Unlike `resend-verification`, nothing in the bundle links here: `SignInScreen` has no "forgot
        // password" affordance and `tasks.md` commissions none, so the screen is reachable by typing the URL
        // only. That is OQ-T52-3, accepted at the phase 4 gate as a known hole in the demo path.
        if (path === PASSWORD_RESET_REQUEST_PATH)
          return { path, element: <PasswordResetRequestScreen /> };
        return { path };
      }),
      // TASK 40 - the auth gate (R28's first clause). ONE pathless layout route decides for all eleven, so
      // there is a single place the question is asked and a screen attached later nests inside the gate
      // rather than replacing it. Pathless deliberately: `path: ''` would be a string in the route tree and
      // would change what the bare `/app/` matches.
      // TASK 174 attached the FIRST element behind the gate. The other ten paths still carry none, and
      // leaving them that way is deliberate: each is its own task's to fill, and attaching a placeholder
      // would make the screen-count pins stop meaning anything. The conditional mirrors the public branch
      // above rather than inventing a second shape for the same job.
      {
        element: <RequireSession />,
        children: [
          // TASK 42 - the authenticated layout, one pathless route INSIDE the gate. Pathless for the same
          // reason the gate is: a string path here would be a route in the tree and would change what the
          // bare `/app/` matches. The eleven paths become its children, unchanged in spelling and order, so
          // the screen-count pins see no new path-attached element - and a later task adding a navigation
          // link to its own screen adds a link, not a restructure.
          {
            element: <AuthenticatedLayout />,
            children: AUTHENTICATED_ROUTE_PATHS.map((path) => {
              if (path === HOME_PATH) return { path, element: <HomeScreen /> };
              return { path };
            }),
          },
        ],
      },
      // STILL OUTSIDE the gate, and task 41 is why that mattered. It carries the redirect now, and a
      // catch-all behind the gate could not have redirected the visitor with no session - the only visitor it
      // is for. It is the one redirect of the three that sits on a route WITH a path, so it is the one the
      // screen-count pins see: four elements now, three screens and one redirect, named apart.
      { path: '*', element: <RedirectToSignIn /> },
    ],
  },
];

export function createAppRouter() {
  return createBrowserRouter(appRoutes, { basename: APP_BASENAME });
}
