/**
 * Which navigations the service worker may answer.
 *
 * R57 and L17: a navigation to an identity or backend path is never answered from cache and never receives the
 * application shell. Without this, the worker's navigation fallback would hand `index.html` to the browser on its
 * way to `/oauth2/authorize` or `/connect/logout`, and no one could sign in or out.
 *
 * The list below is copied verbatim, and in order, from the service-worker rule in `spec/frontend/agents.md`
 * (lines 88-89). It is not extended here: API routes such as `/notifications` are covered by the worker's
 * NetworkOnly policy, not by this navigation denylist.
 *
 * This module is a pure decision over a path and a request mode. It opens no cache and sends no request, so it can
 * be unit-tested without a service-worker scope, and the task that registers the worker consumes it in either shape
 * Workbox accepts: `NAVIGATION_DENYLIST` as a `navigateFallbackDenylist` / `NavigationRoute` denylist, or the two
 * predicates as a `matchCallback`.
 */
export const DENYLISTED_NAVIGATION_PATHS = [
  '/oauth2/',
  '/connect/logout',
  '/login',
  '/logout',
  '/error',
  '/.well-known/',
  '/userinfo',
  '/accounts',
  '/api-keys',
  '/admin',
  '/api/',
] as const;

/** Every SPA route lives under this prefix (L14), so it is the only prefix the shell may answer. */
export const SHELL_NAVIGATION_PREFIX = '/app';

/**
 * Matching is by path segment, not by raw string prefix: `/accounts` must cover `/accounts/me` without also
 * claiming `/accountsxyz`, and `/app` must cover `/app` itself as well as `/app/sign-in` without claiming
 * `/application`. A trailing slash in the list carries no extra meaning, so it is dropped before the boundary is
 * added.
 */
function segmentPrefixPattern(path: string): RegExp {
  const withoutTrailingSlash = path.endsWith('/') ? path.slice(0, -1) : path;
  const escaped = withoutTrailingSlash.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`^${escaped}(?:/|$)`);
}

/** The denylist in the form Workbox takes for a navigation route. Derived, so it cannot drift from the list above. */
export const NAVIGATION_DENYLIST: readonly RegExp[] = DENYLISTED_NAVIGATION_PATHS.map(segmentPrefixPattern);

const SHELL_PATTERN = segmentPrefixPattern(SHELL_NAVIGATION_PREFIX);

/**
 * True when the worker must leave this navigation alone. Only the path and the mode are read: no header, no cookie,
 * no body, and nothing is cached either way.
 */
export function isDenylistedNavigation(url: URL, mode: string): boolean {
  if (mode !== 'navigate') return false;
  return NAVIGATION_DENYLIST.some((pattern) => pattern.test(url.pathname));
}

/**
 * True when the worker may answer this navigation with the application shell. The denylist wins, so the two
 * predicates are never both true for one request. A non-navigation is never the shell's business: request caching
 * is the NetworkOnly policy's concern, not this module's.
 */
export function isShellNavigation(url: URL, mode: string): boolean {
  if (mode !== 'navigate') return false;
  if (isDenylistedNavigation(url, mode)) return false;
  return SHELL_PATTERN.test(url.pathname);
}
