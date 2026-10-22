import { createBrowserRouter, type RouteObject } from 'react-router';

/** Every SPA route lives under this prefix, which is disjoint from the backend prefixes the edge sends to the services (L14). */
export const APP_BASENAME = '/app';

/** Routes reachable without a session (design.md §4c), relative to APP_BASENAME. T40 enforces the split. */
export const PUBLIC_ROUTE_PATHS = [
  'sign-in',
  'callback',
  'register',
  'register/sent',
  'verify',
  'resend-verification',
  'reset/request',
  'reset',
  'signed-out',
] as const;

/** Routes that need a session (design.md §4c), relative to APP_BASENAME. */
export const AUTHENTICATED_ROUTE_PATHS = [
  'home',
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
      ...PUBLIC_ROUTE_PATHS.map((path) => ({ path })),
      ...AUTHENTICATED_ROUTE_PATHS.map((path) => ({ path })),
      { path: '*' },
    ],
  },
];

export function createAppRouter() {
  return createBrowserRouter(appRoutes, { basename: APP_BASENAME });
}
