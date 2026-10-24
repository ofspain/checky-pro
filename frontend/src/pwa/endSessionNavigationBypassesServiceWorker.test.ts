/// <reference types="node" />
import { existsSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { APP_BASENAME, PUBLIC_ROUTE_PATHS } from '../app/routes';
import { DENYLISTED_NAVIGATION_PATHS, isDenylistedNavigation, isShellNavigation } from './navigationDenylist';

/**
 * R19 ends the SAS session by navigating the browser to the OIDC end-session endpoint, and says so in its own
 * words: "The end-session navigation is served by SAS, not the SPA shell (R57)." Two consequences are this file's
 * subject, neither of them covered by T09's general denylist:
 *
 *   1. The end-session URL carries an ID token in `id_token_hint`. A worker that answered that navigation from a
 *      cache would also have written a token into Cache Storage, which outlives the tab that owns it (L3,
 *      design.md §5).
 *   2. A cached end-session response is the one worker failure that lies to the user: the signed-out page renders
 *      while the SAS session is still alive (R22's half-authenticated state).
 *
 * The URL shape is the backend's, not invented here: SasLoginIntegrationTest:519-527 builds
 * `/connect/logout?id_token_hint=…&post_logout_redirect_uri=…&state=…` and :488 asserts the 302 to
 * `<post_logout_redirect_uri>?state=…`, whose allowlisted default is the SPA's `/app/signed-out`.
 *
 * `simulateNavigationFallback` below is a copy of the harness in `sasNavigationBypassesServiceWorker.test.ts`.
 * Both stand in for Workbox's navigation fallback, and **task 12 replaces both with the real `NavigationRoute`**;
 * until then, keep the two copies in step. jsdom has no `FetchEvent`, so the second call in the two-step case
 * stands in for the browser following SAS's 302 — the redirect chain itself is task 12's integration concern.
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
const SHELL = '/app/index.html';
const END_SESSION_PATH = '/connect/logout';
const SIGNED_OUT_PATH = '/app/signed-out';

/** Obviously synthetic, in JWT shape only, so nothing resembling a credential is committed. */
const ID_TOKEN = 'fake.eyJzdWIiOiJ1c2VyLWVuZC1zZXNzaW9uIn0.not-a-real-signature';

const POST_LOGOUT = `${ORIGIN}${SIGNED_OUT_PATH}`;

/** The six shapes fixed by the frozen brief (D2), taken from the backend's own end-session test. */
const endSessionUrls: [string, string][] = [
  [
    'all three parameters',
    `${END_SESSION_PATH}?id_token_hint=${ID_TOKEN}&post_logout_redirect_uri=${encodeURIComponent(POST_LOGOUT)}&state=s1`,
  ],
  ['no parameters', END_SESSION_PATH],
  ['id_token_hint only', `${END_SESSION_PATH}?id_token_hint=${ID_TOKEN}`],
  ['a trailing slash', `${END_SESSION_PATH}/?id_token_hint=${ID_TOKEN}`],
  [
    'a percent-encoded post_logout_redirect_uri',
    `${END_SESSION_PATH}?id_token_hint=${ID_TOKEN}&post_logout_redirect_uri=${encodeURIComponent(POST_LOGOUT)}`,
  ],
  [
    'a foreign-origin post_logout_redirect_uri (the 400 case)',
    `${END_SESSION_PATH}?id_token_hint=${ID_TOKEN}&post_logout_redirect_uri=${encodeURIComponent('https://evil.example/signed-out')}&state=s2`,
  ],
];

interface FakeCache {
  match: Mock;
  put: Mock;
}

let cache: FakeCache;
/** Every key the worker asked the cache about, across a case, so AC2 and AC5 can assert over all of them. */
let keys: string[];

beforeEach(() => {
  keys = [];
  cache = {
    match: vi.fn((key: string) => {
      keys.push(key);
      return Promise.resolve(SHELL);
    }),
    put: vi.fn((key: string) => {
      keys.push(key);
      return Promise.resolve();
    }),
  };
});

async function simulateNavigationFallback(path: string, mode: string, target: FakeCache): Promise<string | undefined> {
  const url = new URL(path, ORIGIN);
  if (isDenylistedNavigation(url, mode)) return undefined;
  if (isShellNavigation(url, mode)) return (await target.match(SHELL)) as string | undefined;
  return undefined;
}

describe('endSessionNavigationBypassesServiceWorker', () => {
  // AC1 — the end-session navigation reaches SAS, not the cache and not the shell (R57, R19).
  it('leaves the end-session navigation to the network (AC1)', async () => {
    const [, fullUrl] = endSessionUrls[0]!;

    await expect(simulateNavigationFallback(fullUrl, 'navigate', cache)).resolves.toBeUndefined();

    expect(isDenylistedNavigation(new URL(fullUrl, ORIGIN), 'navigate')).toBe(true);
    expect(isShellNavigation(new URL(fullUrl, ORIGIN), 'navigate')).toBe(false);
    expect(cache.match).not.toHaveBeenCalled();
    expect(cache.put).not.toHaveBeenCalled();
  });

  // AC2 — the ID token in the query string cannot reach Cache Storage (L3, design.md §5).
  it('never lets the ID token near a cache, in any shape (AC2)', async () => {
    for (const [, url] of endSessionUrls) {
      await simulateNavigationFallback(url, 'navigate', cache);
    }

    // The assertion is that the cache was never touched at all: a token cannot reach a cache that saw no key.
    // Asserting the recorded key list rather than only the call counts means a mutant that reaches the cache by
    // some other route still fails here. (A per-key scan would be dead code under this assertion, so AC5 — where
    // a key does exist — carries the "no key holds the token or a query string" check instead.)
    expect(cache.match).not.toHaveBeenCalled();
    expect(cache.put).not.toHaveBeenCalled();
    expect(keys).toEqual([]);
    expect(keys.join('|')).not.toContain('id_token_hint');
  });

  // AC3 — the other half of the flow: the 302's target is the shell's to answer, so sign-out still renders (R19).
  it.each([SIGNED_OUT_PATH, `${SIGNED_OUT_PATH}?state=s1`])(
    'answers the post-logout target %s with the shell (AC3)',
    async (path) => {
      await expect(simulateNavigationFallback(path, 'navigate', cache)).resolves.toBe(SHELL);
      expect(isShellNavigation(new URL(path, ORIGIN), 'navigate')).toBe(true);
      expect(isDenylistedNavigation(new URL(path, ORIGIN), 'navigate')).toBe(false);
    },
  );

  // AC4 — no query string, slash or redirect target changes the decision (R57, R19).
  it.each(endSessionUrls)('declines the end-session navigation with %s (AC4)', async (_name, url) => {
    await expect(simulateNavigationFallback(url, 'navigate', cache)).resolves.toBeUndefined();
    expect(isDenylistedNavigation(new URL(url, ORIGIN), 'navigate')).toBe(true);
    expect(keys).toEqual([]);
  });

  // AC5 — the whole sign-out flow, in order, against one cache (R19, D7).
  it('bypasses the worker for end-session, then serves the signed-out route from the shell (AC5)', async () => {
    const [, fullUrl] = endSessionUrls[0]!;

    const endSession = await simulateNavigationFallback(fullUrl, 'navigate', cache);
    expect(endSession).toBeUndefined();
    expect(cache.match).not.toHaveBeenCalled();

    // The browser follows SAS's 302 to the post-logout target, which is a fresh navigation (see the file header).
    const signedOut = await simulateNavigationFallback(`${SIGNED_OUT_PATH}?state=s1`, 'navigate', cache);
    expect(signedOut).toBe(SHELL);

    expect(cache.match).toHaveBeenCalledTimes(1);
    expect(cache.match).toHaveBeenCalledWith(SHELL);
    expect(keys).toEqual([SHELL]);
    for (const key of keys) {
      expect(key).not.toContain('?');
      expect(key).not.toContain(ID_TOKEN);
    }
  });

  // AC6 — sign-out propagates to every tab (R22, L18), so a repeat must not find a cached answer waiting.
  it('declines a repeated end-session navigation identically (AC6)', async () => {
    const [, fullUrl] = endSessionUrls[0]!;

    for (let attempt = 0; attempt < 3; attempt += 1) {
      await expect(simulateNavigationFallback(fullUrl, 'navigate', cache)).resolves.toBeUndefined();
    }

    expect(cache.match).not.toHaveBeenCalled();
    expect(cache.put).not.toHaveBeenCalled();
  });

  // AC7 — the two paths this task depends on are still the ones the spec and the route table declare.
  it('still finds /connect/logout in the denylist and in agents.md (AC7)', async () => {
    expect(DENYLISTED_NAVIGATION_PATHS).toContain(END_SESSION_PATH);

    const agents = await readFile(repoFile('spec/frontend/agents.md'), 'utf8');
    const start = agents.indexOf('Navigation requests to these paths are never answered from cache');
    expect(start).toBeGreaterThan(-1);
    const rest = agents.slice(start);
    const end = rest.indexOf('\n- ');
    expect(end === -1 ? rest : rest.slice(0, end)).toContain(`\`${END_SESSION_PATH}\``);
  });

  it('still finds the signed-out route under the app prefix (AC7)', () => {
    expect(APP_BASENAME).toBe('/app');
    expect(PUBLIC_ROUTE_PATHS).toContain('signed-out');
    expect(`${APP_BASENAME}/signed-out`).toBe(SIGNED_OUT_PATH);
  });
});
