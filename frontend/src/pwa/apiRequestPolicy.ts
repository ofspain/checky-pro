/**
 * What the service worker may answer from a cache, and what it must always fetch.
 *
 * `agents.md` puts it two ways, and the second is the sharper one: "API routes use NetworkOnly" (line 90), and
 * "Sensitive response bodies (account, token, receipt, evidence) are never cached" (line 85). The Cache Storage API
 * is client-side storage, so an API response sitting in it is a storage violation against L3 and L16 — readable by
 * any later script in scope, outliving the tab that fetched it — not merely a staleness bug.
 *
 * `design.md` §5 states the rule positively: the worker cache holds "static shell and assets only. No API
 * responses." This module is written that way round. Cacheable is the narrow, enumerated case; everything else is
 * NetworkOnly by default, so a thirteenth prefix added at the edge tomorrow is safe without anyone editing this
 * file. `NETWORK_ONLY_PATH_PREFIXES` is published for the task that wires Workbox and for the drift guard against
 * the edge path map — the decision below never consults it.
 *
 * Navigations are not this module's business: `navigationDenylist.ts` (R57, L17) decides those, and an SPA route
 * such as `/app/sign-in` is answered by its navigation fallback rather than from this cache set.
 */

/**
 * The prefixes the edge sends to a service, from the edge path map in `design.md` §4c (2), in that order, minus
 * `/app` (the SPA's own). The map's `/api/v1` is normalised to `/api` so a future `/api/v2` is NetworkOnly by
 * default; that also matches R57's wording, which says `/api/`.
 */
export const NETWORK_ONLY_PATH_PREFIXES = [
  '/oauth2',
  '/login',
  '/logout',
  '/connect',
  '/.well-known',
  '/userinfo',
  '/error',
  '/accounts',
  '/admin',
  '/api-keys',
  '/api',
  '/notifications',
] as const;

/** The shell document itself, the only HTML the worker may hold (design.md §5). */
export const SHELL_DOCUMENT_PATHS = ['/app', '/app/', '/app/index.html'] as const;

/** Built assets live here, hashed, so they are safe to cache (T01 asserts the `/app/assets/<name>-<hash>` shape). */
export const ASSET_PATH_PREFIX = '/app/assets';

/**
 * Matching is by path segment, so `/app/assets` cannot claim `/app/assetsx/evil.js`. A copy of the helper in
 * `navigationDenylist.ts`, which is module-private there; the task that registers the worker can collapse the two.
 */
function segmentPrefixPattern(path: string): RegExp {
  const withoutTrailingSlash = path.endsWith('/') ? path.slice(0, -1) : path;
  const escaped = withoutTrailingSlash.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`^${escaped}(?:/|$)`);
}

const ASSET_PATTERN = segmentPrefixPattern(ASSET_PATH_PREFIX);

/**
 * True only for the SPA's own shell document and its built assets. Nothing else is cacheable — not an API response,
 * not an identity page, not an asset some other service happens to serve.
 */
export function isCacheableRequest(url: URL): boolean {
  if ((SHELL_DOCUMENT_PATHS as readonly string[]).includes(url.pathname)) return true;
  return ASSET_PATTERN.test(url.pathname);
}

/**
 * Everything the worker must fetch and never store. Defined as the negation of the cacheable set rather than as a
 * prefix lookup, so an unrecognised path is NetworkOnly instead of falling through to a cache.
 */
export function isNetworkOnlyRequest(url: URL): boolean {
  return !isCacheableRequest(url);
}
