/// <reference types="node" />
import { existsSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import {
  DENYLISTED_NAVIGATION_PATHS,
  NAVIGATION_DENYLIST,
  SHELL_NAVIGATION_PREFIX,
  isDenylistedNavigation,
  isShellNavigation,
} from './navigationDenylist';

// repoRoot is computed from Vitest's testPath, as T01, T05 and T08 do, because jsdom gives import.meta.url a
// non-file URL and the working directory depends on how the runner was started. That couples the path to this
// file's depth in the tree, so repoFile() checks the target exists and names the computed root when it does not:
// a moved file or a different runner then fails with that sentence instead of an ENOENT from the middle of a case.
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
const SHELL = '/app/index.html';

interface FakeCache {
  match: Mock;
  put: Mock;
}

/**
 * Workbox's navigation-fallback semantics in ten lines, over the real exported policy: a denylisted navigation is
 * handed back to the network without the cache being touched, a shell navigation is answered from the cache, and
 * anything else is not the worker's business. The task that registers the worker (task 12) wires the genuine
 * `NavigationRoute` against the same exports; this harness exists so "never answered from cache or with the shell"
 * is asserted observably while the policy module itself stays pure and holds no cache reference.
 */
async function simulateNavigationFallback(
  path: string,
  mode: string,
  cache: FakeCache,
): Promise<string | undefined> {
  const url = new URL(path, ORIGIN);
  if (isDenylistedNavigation(url, mode)) return undefined;
  if (isShellNavigation(url, mode)) return (await cache.match(SHELL)) as string | undefined;
  return undefined;
}

let cache: FakeCache;

beforeEach(() => {
  cache = { match: vi.fn().mockResolvedValue(SHELL), put: vi.fn() };
});

describe('sasNavigationBypassesServiceWorker', () => {
  // AC1 — every denylisted path, and a sub-path of each, reaches the network (R57, L17).
  const subPaths: Record<string, string> = {
    '/oauth2/': '/oauth2/authorize',
    '/connect/logout': '/connect/logout/confirm',
    '/login': '/login/step-two',
    '/logout': '/logout/done',
    '/error': '/error/500',
    '/.well-known/': '/.well-known/openid-configuration',
    '/userinfo': '/userinfo/claims',
    '/accounts': '/accounts/me',
    '/api-keys': '/api-keys/01HX',
    '/admin': '/admin/users',
    '/api/': '/api/v1/accounts',
  };

  it.each(DENYLISTED_NAVIGATION_PATHS)(
    'leaves a navigation to %s to the network, unanswered by the worker (AC1)',
    async (path) => {
      await expect(simulateNavigationFallback(path, 'navigate', cache)).resolves.toBeUndefined();
      expect(isDenylistedNavigation(new URL(path, ORIGIN), 'navigate')).toBe(true);
      expect(isShellNavigation(new URL(path, ORIGIN), 'navigate')).toBe(false);
    },
  );

  it.each(Object.entries(subPaths))(
    'leaves a navigation under %s (%s) to the network (AC1)',
    async (_path, subPath) => {
      await expect(simulateNavigationFallback(subPath, 'navigate', cache)).resolves.toBeUndefined();
      expect(isDenylistedNavigation(new URL(subPath, ORIGIN), 'navigate')).toBe(true);
    },
  );

  it('covers a sub-path for every denylisted entry, so AC1 cannot thin out (AC1)', () => {
    expect(Object.keys(subPaths)).toEqual([...DENYLISTED_NAVIGATION_PATHS]);
  });

  // AC2 — the shell still answers SPA navigations, so AC1 is not vacuously true (R57, L17, L14).
  it.each(['/app', '/app/', '/app/sign-in', '/app/disputes/01HX/evidence'])(
    'answers a navigation to %s with the application shell (AC2)',
    async (path) => {
      await expect(simulateNavigationFallback(path, 'navigate', cache)).resolves.toBe(SHELL);
      expect(cache.match).toHaveBeenCalledWith(SHELL);
      expect(isShellNavigation(new URL(path, ORIGIN), 'navigate')).toBe(true);
    },
  );

  // AC3 — a denylisted navigation causes no cache lookup at all, not even a miss (R57).
  it('never reads the cache for a denylisted navigation (AC3)', async () => {
    for (const path of [...DENYLISTED_NAVIGATION_PATHS, ...Object.values(subPaths)]) {
      await simulateNavigationFallback(path, 'navigate', cache);
    }

    expect(cache.match).not.toHaveBeenCalled();
  });

  // AC4 — matching is by path segment, both ways (R57 verbatim).
  const boundaries: [string, boolean, boolean][] = [
    // path, denylisted, shell
    ['/api', true, false],
    ['/api/v1/accounts', true, false],
    ['/apikeys', false, false],
    ['/api-keys', true, false],
    ['/accounts/me', true, false],
    ['/accountsxyz', false, false],
    ['/logouts', false, false],
    ['/connect/logout', true, false],
    ['/connect/register', false, false],
    ['/.well-known/jwks.json', true, false],
    ['/xwell-known', false, false],
    ['/application', false, false],
    ['/app/accounts', false, true],
    ['/app/login', false, true],
  ];

  it.each(boundaries)('classifies %s by path segment (AC4)', (path, denylisted, shell) => {
    const url = new URL(path, ORIGIN);
    expect(isDenylistedNavigation(url, 'navigate')).toBe(denylisted);
    expect(isShellNavigation(url, 'navigate')).toBe(shell);
  });

  it('ignores the query string and the hash (AC4)', () => {
    expect(isDenylistedNavigation(new URL('/login?redirect=/app/x#frag', ORIGIN), 'navigate')).toBe(true);
    expect(isShellNavigation(new URL('/app/sign-in?next=/app/home#top', ORIGIN), 'navigate')).toBe(true);
    expect(isDenylistedNavigation(new URL('/app/home?path=/login', ORIGIN), 'navigate')).toBe(false);
  });

  it('never classifies one navigation as both denylisted and shell (AC4)', () => {
    const paths = [...DENYLISTED_NAVIGATION_PATHS, ...Object.values(subPaths), ...boundaries.map(([p]) => p)];
    for (const path of paths) {
      const url = new URL(path, ORIGIN);
      expect(isDenylistedNavigation(url, 'navigate') && isShellNavigation(url, 'navigate')).toBe(false);
    }
  });

  // AC5 — nothing identity-related is written to a cache (L17, design.md §5).
  it('caches nothing for a denylisted navigation (AC5)', async () => {
    for (const path of DENYLISTED_NAVIGATION_PATHS) {
      await simulateNavigationFallback(path, 'navigate', cache);
    }

    expect(cache.put).not.toHaveBeenCalled();
  });

  // AC6 — the policy is navigation-only; API request caching is task 11's NetworkOnly rule, not this module's.
  it.each(['cors', 'same-origin', 'no-cors', ''])('declines a %s request, denylisted or not (AC6)', (mode) => {
    for (const path of [...DENYLISTED_NAVIGATION_PATHS, '/app/', '/app/sign-in']) {
      const url = new URL(path, ORIGIN);
      expect(isDenylistedNavigation(url, mode)).toBe(false);
      expect(isShellNavigation(url, mode)).toBe(false);
    }
  });

  it('answers no non-navigation request from the cache (AC6)', async () => {
    await expect(simulateNavigationFallback('/app/sign-in', 'cors', cache)).resolves.toBeUndefined();
    expect(cache.match).not.toHaveBeenCalled();
  });

  // AC7 — the list is the one in agents.md, in that order; a change to the standing rule fails here.
  it('matches the service-worker rule in agents.md exactly, in order (AC7)', async () => {
    const agents = await readFile(repoFile('spec/frontend/agents.md'), 'utf8');
    expect(agents).toContain('**Service worker (LOCKED');

    const start = agents.indexOf('Navigation requests to these paths are never answered from cache');
    expect(start).toBeGreaterThan(-1);
    const rest = agents.slice(start);
    const end = rest.indexOf('\n- ');
    const block = end === -1 ? rest : rest.slice(0, end);
    const declared = [...block.matchAll(/`(\/[^`]*)`/g)].map((match) => match[1]!);

    expect(declared).toHaveLength(11);
    expect(declared).toEqual([...DENYLISTED_NAVIGATION_PATHS]);
  });

  it('derives one segment-anchored pattern per declared path (AC7)', () => {
    // Asserted through each pattern's behaviour rather than its `source`, which V8 renders with `/` escaped.
    expect(NAVIGATION_DENYLIST).toHaveLength(DENYLISTED_NAVIGATION_PATHS.length);
    for (const [index, pattern] of NAVIGATION_DENYLIST.entries()) {
      const path = DENYLISTED_NAVIGATION_PATHS[index]!;
      const base = path.endsWith('/') ? path.slice(0, -1) : path;
      expect(pattern.test(base)).toBe(true);
      expect(pattern.test(`${base}/sub`)).toBe(true);
      expect(pattern.test(`${base}x`)).toBe(false);
      expect(pattern.test(`/app${base}`)).toBe(false);
    }
  });

  // AC8 — the verbatim list stays cited, so a later reader knows where it came from and what owns it.
  it('cites R57 and agents.md in the module source (AC8)', async () => {
    const source = await readFile(repoFile('frontend/src/pwa/navigationDenylist.ts'), 'utf8');
    expect(source).toContain('R57');
    expect(source).toContain('agents.md');
    expect(source).toContain('L17');
  });

  // AC9 — the policy module stays a pure decision: no worker globals, no storage, no session coupling.
  it('uses no network, cache, storage or cross-tab global (AC9)', async () => {
    const source = await readFile(repoFile('frontend/src/pwa/navigationDenylist.ts'), 'utf8');
    const code = source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
    expect(code).toContain('DENYLISTED_NAVIGATION_PATHS');

    for (const banned of [
      'caches',
      'fetch',
      'self',
      'BroadcastChannel',
      'RENEWAL_CHANNEL',
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

  it('keeps the shell prefix to the one the SPA owns (AC9, L14)', () => {
    expect(SHELL_NAVIGATION_PREFIX).toBe('/app');
  });
});
