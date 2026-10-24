/// <reference types="node" />
import { existsSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import {
  ASSET_PATH_PREFIX,
  NETWORK_ONLY_PATH_PREFIXES,
  SHELL_DOCUMENT_PATHS,
  isCacheableRequest,
  isNetworkOnlyRequest,
} from './apiRequestPolicy';
import { isDenylistedNavigation, isShellNavigation } from './navigationDenylist';

/**
 * `agents.md:90` says API routes use NetworkOnly, and `:85` says the account, token, receipt and evidence bodies
 * are never cached at all. Cache Storage is client-side storage, so this is an L3/L16 rule, not a freshness one.
 *
 * `simulateRequestRoute` stands in for Workbox's two request strategies, as the harnesses in
 * `sasNavigationBypassesServiceWorker.test.ts` and `endSessionNavigationBypassesServiceWorker.test.ts` stand in for
 * its navigation fallback: a NetworkOnly decision returns without touching the cache, while a cacheable path reads
 * the cache and writes a miss back. **Task 12 must wire the real `NetworkOnly` strategy** — a pure policy cannot
 * stop a cache write at runtime, so what is proven here is the decision, not the plumbing.
 *
 * The `method` parameter exists so a mutant that consults the HTTP method has somewhere to consult it: the
 * predicates themselves take only a URL, which is the strongest form of the method-independence guarantee.
 */

const repoRoot = dirname(dirname(dirname(dirname(expect.getState().testPath as string))));

function repoFile(relativePath: string): string {
  const path = join(repoRoot, relativePath);
  if (!existsSync(path)) {
    throw new Error(
      `${relativePath} is not under the repository root computed from Vitest's testPath (${repoRoot}). ` +
        'If this test file moved, the number of dirname() calls above has to move with it.',
    );
  }

  return path;
}

const ORIGIN = 'https://app.example.test';

interface FakeCache {
  match: Mock;
  put: Mock;
}

let cache: FakeCache;
let keys: string[];

beforeEach(() => {
  keys = [];
  cache = {
    match: vi.fn((key: string) => {
      keys.push(key);
      return Promise.resolve(undefined);
    }),
    put: vi.fn((key: string) => {
      keys.push(key);
      return Promise.resolve();
    }),
  };
});

type Outcome = 'network-only' | 'from-cache' | 'network-then-cached';

async function simulateRequestRoute(path: string, target: FakeCache, method = 'GET'): Promise<Outcome> {
  const url = new URL(path, ORIGIN);
  if (isNetworkOnlyRequest(url)) return 'network-only';

  const hit = await target.match(url.pathname);
  if (hit !== undefined) return 'from-cache';
  await target.put(url.pathname, `body of ${method} ${url.pathname}`);
  return 'network-then-cached';
}

/** A representative sub-path per prefix, so a dropped prefix cannot hide behind the bare path. */
const subPaths: Record<string, string> = {
  '/oauth2': '/oauth2/authorize',
  '/login': '/login?error',
  '/logout': '/logout/done',
  '/connect': '/connect/logout',
  '/.well-known': '/.well-known/openid-configuration',
  '/userinfo': '/userinfo',
  '/error': '/error/500',
  '/accounts': '/accounts/me',
  '/admin': '/admin/accounts/01HX',
  '/api-keys': '/api-keys/01HX',
  '/api': '/api/v1/invoices/01HX/payments',
  '/notifications': '/notifications/stream',
};

describe('serviceWorkerNeverCachesApiResponses', () => {
  // AC1 — every backend prefix, bare and with a sub-path, is fetched and never cached (agents.md:90, L17).
  it.each(NETWORK_ONLY_PATH_PREFIXES)('never caches a request to %s (AC1)', async (prefix) => {
    await expect(simulateRequestRoute(prefix, cache)).resolves.toBe('network-only');
    expect(isNetworkOnlyRequest(new URL(prefix, ORIGIN))).toBe(true);
    expect(isCacheableRequest(new URL(prefix, ORIGIN))).toBe(false);
    expect(keys).toEqual([]);
  });

  it.each(Object.entries(subPaths))('never caches a request under %s (%s) (AC1)', async (_prefix, path) => {
    await expect(simulateRequestRoute(path, cache)).resolves.toBe('network-only');
    expect(cache.match).not.toHaveBeenCalled();
    expect(cache.put).not.toHaveBeenCalled();
  });

  it('covers a sub-path for every prefix, so AC1 cannot thin out (AC1)', () => {
    expect(Object.keys(subPaths)).toEqual([...NETWORK_ONLY_PATH_PREFIXES]);
  });

  // AC2 — the shell and its built assets ARE cacheable, so AC1 is not vacuously true (design.md §5).
  it.each([...SHELL_DOCUMENT_PATHS, '/app/assets/main-abc123.js', '/app/assets/style-abc123.css'])(
    'caches the shell or asset at %s (AC2)',
    async (path) => {
      await expect(simulateRequestRoute(path, cache)).resolves.toBe('network-then-cached');
      expect(isCacheableRequest(new URL(path, ORIGIN))).toBe(true);
      expect(cache.match).toHaveBeenCalledWith(path);
    },
  );

  it('serves a cached asset from the cache on a hit (AC2)', async () => {
    cache.match.mockImplementation((key: string) => {
      keys.push(key);
      return Promise.resolve('cached asset');
    });

    await expect(simulateRequestRoute('/app/assets/main-abc123.js', cache)).resolves.toBe('from-cache');
    expect(cache.put).not.toHaveBeenCalled();
  });

  // AC3 — the four body kinds agents.md:85 names, by name.
  it.each([
    ['account', '/accounts/me'],
    ['token', '/oauth2/token'],
    ['receipt', '/api/v1/receipts/0b7f6a9e-1c2d-4e5f-8a9b-0c1d2e3f4a5b'],
    ['evidence', '/api/v1/invoices/0b7f6a9e-1c2d-4e5f-8a9b-0c1d2e3f4a5b/evidence'],
  ])('never caches a %s response (%s) (AC3)', async (_kind, path) => {
    await expect(simulateRequestRoute(path, cache)).resolves.toBe('network-only');
    expect(keys).toEqual([]);
  });

  // AC4 — the method cannot change the answer; the predicates cannot even see one (D6).
  it.each(['GET', 'POST', 'PUT', 'DELETE'])('never caches %s /accounts/password-reset (AC4)', async (method) => {
    await expect(simulateRequestRoute('/accounts/password-reset', cache, method)).resolves.toBe('network-only');
    expect(keys).toEqual([]);
  });

  it('answers a GET and a POST to the same API path identically (AC4)', async () => {
    const get = await simulateRequestRoute('/accounts/me', cache, 'GET');
    const post = await simulateRequestRoute('/accounts/me', cache, 'POST');

    expect(get).toBe(post);
    expect(isNetworkOnlyRequest(new URL('/accounts/me', ORIGIN))).toBe(true);
  });

  // AC5 — the two policies compose: T09 owns navigations, this module owns requests (D8).
  it.each(['/accounts', '/admin', '/api-keys', '/userinfo', '/api/v1/invoices'])(
    'declines %s as a navigation (T09) and as a request (T11) (AC5)',
    (path) => {
      const url = new URL(path, ORIGIN);
      expect(isDenylistedNavigation(url, 'navigate')).toBe(true);
      expect(isShellNavigation(url, 'navigate')).toBe(false);
      expect(isNetworkOnlyRequest(url)).toBe(true);
    },
  );

  it('leaves an SPA route to the navigation fallback, not to this cache set (AC5)', () => {
    const url = new URL('/app/sign-in', ORIGIN);

    // T09 answers it with the shell as a navigation; design.md §5 holds the shell document and assets only, so a
    // non-navigation request for the same path is NetworkOnly here. The two modules answer different questions.
    expect(isShellNavigation(url, 'navigate')).toBe(true);
    expect(isCacheableRequest(url)).toBe(false);
    expect(isNetworkOnlyRequest(url)).toBe(true);
  });

  // AC6 — default-deny: a path nobody enumerated is NetworkOnly (D1, D9).
  it.each([
    '/thirteenth-service',
    '/default-ui.css',
    '/favicon.ico',
    '/',
    '/accountsxyz',
  ])('treats the unenumerated path %s as NetworkOnly (AC6)', async (path) => {
    await expect(simulateRequestRoute(path, cache)).resolves.toBe('network-only');
    expect(isCacheableRequest(new URL(path, ORIGIN))).toBe(false);
  });

  // AC7 — the cacheable set is bounded by path segment (D5).
  it.each(['/application', '/app2', '/appfoo', '/app/assetsx/evil.js', '/app-assets/main.js'])(
    'does not treat %s as the shell or an asset (AC7)',
    (path) => {
      expect(isCacheableRequest(new URL(path, ORIGIN))).toBe(false);
      expect(isNetworkOnlyRequest(new URL(path, ORIGIN))).toBe(true);
    },
  );

  it('treats the asset prefix itself and its sub-paths as cacheable (AC7)', () => {
    expect(isCacheableRequest(new URL(ASSET_PATH_PREFIX, ORIGIN))).toBe(true);
    expect(isCacheableRequest(new URL(`${ASSET_PATH_PREFIX}/deep/chunk-abc123.js`, ORIGIN))).toBe(true);
  });

  // AC8 — the notification stream, with the reason: a buffered or replayed SSE stream is broken, not stale.
  it.each(['/notifications', '/notifications/stream', '/notifications/stream?since=42'])(
    'never caches the notification stream at %s (AC8)',
    async (path) => {
      await expect(simulateRequestRoute(path, cache)).resolves.toBe('network-only');
      expect(keys).toEqual([]);
    },
  );

  // AC9 — the list is the edge path map's, in order; a divergence between spec and worker fails here (D7).
  it('matches the edge path map in design.md, in order (AC9)', async () => {
    const design = await readFile(repoFile('spec/frontend/design.md'), 'utf8');
    const start = design.indexOf('**Edge path map**');
    expect(start).toBeGreaterThan(-1);

    // The map is one sentence inside a longer blockquote, so it is bounded explicitly: from the heading to its
    // last entry, `/app` -> the SPA. Taking the whole blockquote would also sweep up the local-development
    // paragraph that follows it, which names other paths.
    const end = design.indexOf('\u2192 the SPA', start);
    expect(end).toBeGreaterThan(start);
    const paragraph = design.slice(start, end).replaceAll('\n>', ' ');
    const declared = [...paragraph.matchAll(/`(\/[^`]*)`/g)]
      .map((match) => match[1]!)
      .filter((path) => path !== '/app')
      .map((path) => (path === '/api/v1' ? '/api' : path));

    expect(declared).toHaveLength(12);
    expect(declared).toEqual([...NETWORK_ONLY_PATH_PREFIXES]);
  });

  // AC10 — the two predicates are one decision, and the module stays pure.
  it('keeps isNetworkOnlyRequest the exact negation of isCacheableRequest (AC10)', () => {
    const everyPath = [
      ...NETWORK_ONLY_PATH_PREFIXES,
      ...Object.values(subPaths),
      ...SHELL_DOCUMENT_PATHS,
      '/app/assets/main-abc123.js',
      '/application',
      '/app/sign-in',
      '/thirteenth-service',
      '/',
    ];

    for (const path of everyPath) {
      const url = new URL(path, ORIGIN);
      expect(isNetworkOnlyRequest(url)).toBe(!isCacheableRequest(url));
    }
  });

  it('uses no network, cache, storage or cross-tab global (AC10)', async () => {
    const source = await readFile(repoFile('frontend/src/pwa/apiRequestPolicy.ts'), 'utf8');
    const code = source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
    expect(code).toContain('NETWORK_ONLY_PATH_PREFIXES');

    for (const banned of [
      'caches',
      'fetch',
      'self',
      'BroadcastChannel',
      'navigator',
      'localStorage',
      'sessionStorage',
      'document',
      'import.meta',
      'http://',
      'https://',
    ]) {
      expect(code).not.toContain(banned);
    }
  });
});
