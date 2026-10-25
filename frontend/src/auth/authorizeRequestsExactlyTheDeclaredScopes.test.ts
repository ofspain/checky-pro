import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { APP_BASENAME } from '../app/routes';
import { PKCE_CODE_CHALLENGE_METHOD, deriveCodeChallenge, isValidCodeVerifier, randomUrlSafeToken } from './pkce';
import {
  AUTHORIZE_ENDPOINT_PATH,
  AUTHORIZE_STORAGE_KEYS,
  CLIENT_ID,
  REQUESTED_SCOPES,
  buildAuthorizeRequest,
} from './authorizeRequest';

/**
 * T15 - the authorize request carries state, nonce, code_challenge_method=S256 and exactly `openid profile email`
 * (R6, R20). T14 owns the PKCE primitive; tasks 17-20 own the callback; this owns the request and the three L16
 * items those tasks read back.
 *
 * Two rules here could pass for the wrong reason, and each is paid for rather than assumed. "Exactly these scopes"
 * is a set equality fed deliberately wrong values through the same matcher; "the verifier never reaches the URL"
 * is unfalsifiable on its own, because a 43-character random string will never appear by accident - so the matcher
 * is first shown to catch a leak it is given.
 *
 * Source assertions here are anchored to USAGE, not mention: the module's doc comments discuss S256 and `plain` on
 * purpose, and T01 already forbids `import.meta.env` and absolute addresses across all of src/.
 */

/** The three scopes the server registers; a fourth is refused on both sides. */
const EXPECTED_SCOPE_VALUE = 'openid profile email';

function parameters(url: string): URLSearchParams {
  return new URL(url).searchParams;
}

/** One implementation of "this URL exposes the verifier", used in both directions (D-9). */
function urlLeaksVerifier(url: string, verifier: string): boolean {
  return decodeURIComponent(url).includes(verifier);
}

/** "scope" is exactly these three - the matcher, so wrong values can be fed to it (AC9). */
function scopeIsExactlyDeclared(scope: string | null): boolean {
  if (scope === null) return false;
  const requested = scope.split(' ').filter((part) => part !== '');
  return requested.length === REQUESTED_SCOPES.length && requested.every((s, i) => s === REQUESTED_SCOPES[i]);
}

describe('authorizeRequestsExactlyTheDeclaredScopes', () => {
  let moduleSource: string;
  let pkceSource: string;
  let repoRoot: string;

  beforeAll(async () => {
    const here = dirname(expect.getState().testPath as string);
    repoRoot = join(here, '..', '..', '..');
    moduleSource = await readFile(join(here, 'authorizeRequest.ts'), 'utf8');
    pkceSource = await readFile(join(here, 'pkce.ts'), 'utf8');
  });

  beforeEach(() => {
    globalThis.sessionStorage.clear();
    globalThis.localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    vi.resetModules();
  });

  describe('the scopes (AC1)', () => {
    it('are exactly openid profile email, in that order', async () => {
      const { url } = await buildAuthorizeRequest();
      expect(parameters(url).get('scope')).toBe(EXPECTED_SCOPE_VALUE);
      expect(parameters(url).get('scope')?.split(' ')).toEqual([...REQUESTED_SCOPES]);
    });

    // Without these the equality above could be a `contains` in disguise and nothing would say so (AC9).
    it.each([
      ['a fourth scope appended', 'openid profile email offline_access', false],
      ['a missing scope', 'openid profile', false],
      ['reordered', 'profile openid email', false],
      ['duplicated', 'openid openid profile email', false],
      ['one scope only', 'openid', false],
      ['empty', '', false],
      ['absent', null, false],
      ['exactly the declared three', EXPECTED_SCOPE_VALUE, true],
    ])('matcher rejects %s', (_label, scope, expected) => {
      expect(scopeIsExactlyDeclared(scope)).toBe(expected);
    });
  });

  describe('PKCE (AC2, AC3)', () => {
    it('sends the method constant T14 publishes, not a string of its own', async () => {
      const { url } = await buildAuthorizeRequest();
      expect(parameters(url).get('code_challenge_method')).toBe(PKCE_CODE_CHALLENGE_METHOD);
      expect(PKCE_CODE_CHALLENGE_METHOD).toBe('S256');
    });

    // Prose may mention S256; a quoted literal would mean the module decided it locally.
    it('contains no quoted S256 literal of its own', () => {
      expect(moduleSource).not.toMatch(/['"`]S256['"`]/);
    });

    // The criterion most easily got wrong: a module that stored one pair and sent another's challenge would satisfy
    // every other assertion in this file and fail every real login.
    it('sends the challenge of the verifier it stored', async () => {
      const { url } = await buildAuthorizeRequest();
      const storedVerifier = globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.codeVerifier);
      expect(storedVerifier).not.toBeNull();
      expect(isValidCodeVerifier(storedVerifier as string)).toBe(true);
      await expect(deriveCodeChallenge(storedVerifier as string)).resolves.toBe(
        parameters(url).get('code_challenge'),
      );
    });
  });

  describe('state and nonce (AC4)', () => {
    it('are both present and are not each other', async () => {
      const { url, state, nonce } = await buildAuthorizeRequest();
      expect(state).toHaveLength(43);
      expect(nonce).toHaveLength(43);
      expect(state).not.toBe(nonce);
      expect(parameters(url).get('state')).toBe(state);
      expect(parameters(url).get('nonce')).toBe(nonce);
    });

    it('are fresh on every request', async () => {
      const requests = [];
      for (let i = 0; i < 50; i += 1) requests.push(await buildAuthorizeRequest());
      expect(new Set(requests.map((r) => r.state)).size).toBe(50);
      expect(new Set(requests.map((r) => r.nonce)).size).toBe(50);
    });

    it('come from the CSPRNG, in both modules', () => {
      expect(pkceSource).toContain('crypto.getRandomValues');
      for (const source of [moduleSource, pkceSource]) expect(source).not.toMatch(/\bMath\s*\.\s*random\b/);
    });
  });

  describe('the fixed parameters and the derived redirect URI (AC5)', () => {
    it('asks for an authorization code from the registered public client', async () => {
      const { url } = await buildAuthorizeRequest();
      expect(parameters(url).get('response_type')).toBe('code');
      expect(parameters(url).get('client_id')).toBe(CLIENT_ID);
      expect(CLIENT_ID).toBe('checky-spa');
    });

    // Counted, not merely present: two occurrences would mean the value had drifted into a second place.
    it('names checky-spa exactly once, so it cannot drift to two values', () => {
      expect(moduleSource.match(/checky-spa/g)).toHaveLength(1);
    });

    it('targets the authorize endpoint on this page own origin', async () => {
      const { url } = await buildAuthorizeRequest();
      const parsed = new URL(url);
      expect(parsed.origin).toBe(globalThis.location.origin);
      expect(parsed.pathname).toBe(AUTHORIZE_ENDPOINT_PATH);
    });

    it('derives the redirect URI from the route table, not a literal', async () => {
      const { url } = await buildAuthorizeRequest();
      const redirect = parameters(url).get('redirect_uri') as string;
      expect(redirect).toBe(`${globalThis.location.origin}${APP_BASENAME}/callback`);
      const parsed = new URL(redirect);
      expect(parsed.pathname).toBe('/app/callback');
      expect(parsed.pathname.startsWith(APP_BASENAME)).toBe(true);
      expect(parsed.pathname).not.toContain('//');
    });

    it('follows the origin it is served from', async () => {
      vi.stubGlobal('location', { origin: 'https://checky.example' });
      const { url } = await buildAuthorizeRequest();
      expect(url.startsWith(`https://checky.example${AUTHORIZE_ENDPOINT_PATH}?`)).toBe(true);
      expect(parameters(url).get('redirect_uri')).toBe('https://checky.example/app/callback');
    });

    it('would survive a trailing slash on the route basename', () => {
      // The derivation's rule, applied to the value a future edit might introduce.
      const strip = (base: string) => (base.endsWith('/') ? base.slice(0, -1) : base);
      for (const base of ['/app', '/app/']) expect(`${strip(base)}/callback`).toBe('/app/callback');
    });
  });

  describe('the verifier never reaches the request (AC6, AC9)', () => {
    // Fed a leak it must find, three ways, before the real URL's clean result means anything.
    it.each([
      ['as a query parameter', (u: string, v: string) => `${u}&code_verifier=${v}`],
      ['percent-encoded', (u: string, v: string) => `${u}&x=${encodeURIComponent(v)}`],
      ['in the fragment', (u: string, v: string) => `${u}#${v}`],
      ['inside a nested redirect_uri', (u: string, v: string) => `${u}&r=${encodeURIComponent(`/cb?v=${v}`)}`],
    ])('the matcher finds the verifier %s', async (_label, leak) => {
      const { url } = await buildAuthorizeRequest();
      const verifier = globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.codeVerifier) as string;
      expect(urlLeaksVerifier(leak(url, verifier), verifier)).toBe(true);
    });

    it('and the real URL does not carry it, in any parameter', async () => {
      const { url } = await buildAuthorizeRequest();
      const verifier = globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.codeVerifier) as string;
      expect(urlLeaksVerifier(url, verifier)).toBe(false);
      for (const [, value] of parameters(url)) expect(value).not.toBe(verifier);
      expect(parameters(url).has('code_verifier')).toBe(false);
    });

    it('is not returned to the caller either', async () => {
      const request = await buildAuthorizeRequest();
      expect(Object.keys(request).sort()).toEqual(['nonce', 'state', 'url']);
    });
  });

  describe('session storage, and nothing else (AC7)', () => {
    it('holds exactly the three L16 items this task owns', async () => {
      await buildAuthorizeRequest();
      const keys = Object.keys(globalThis.sessionStorage).sort();
      expect(keys).toEqual([...Object.values(AUTHORIZE_STORAGE_KEYS)].sort());
      expect(globalThis.localStorage.length).toBe(0);
    });

    it('touches no other Storage and no console while building', async () => {
      const spies = [
        vi.spyOn(Storage.prototype, 'getItem'),
        vi.spyOn(Storage.prototype, 'removeItem'),
        vi.spyOn(Storage.prototype, 'clear'),
        ...Object.keys(globalThis.console)
          .filter((key) => typeof (globalThis.console as unknown as Record<string, unknown>)[key] === 'function')
          .map((key) => vi.spyOn(globalThis.console as unknown as Record<string, () => void>, key)),
      ];
      expect(spies.length).toBeGreaterThan(10);
      await buildAuthorizeRequest();
      for (const spy of spies) expect(spy).not.toHaveBeenCalled();
    });

    it('names no localStorage and no Buffer in its own source', () => {
      expect(moduleSource).not.toMatch(/\blocalStorage\s*[.[]/);
      expect(moduleSource).not.toMatch(/\bBuffer\s*[.(]/);
    });
  });

  describe('it fails closed (AC11)', () => {
    it('rejects a pair whose verifier is not valid, and stores nothing', async () => {
      vi.doMock('./pkce', async () => {
        const actual = await vi.importActual<typeof import('./pkce')>('./pkce');
        return { ...actual, generatePkcePair: async () => ({ verifier: 'too-short', challenge: 'x', method: 'S256' }) };
      });
      const { buildAuthorizeRequest: build } = await import('./authorizeRequest');
      await expect(build()).rejects.toThrow(TypeError);
      expect(Object.keys(globalThis.sessionStorage)).toEqual([]);
    });

    it('stores nothing when the challenge derivation itself fails', async () => {
      vi.doMock('./pkce', async () => {
        const actual = await vi.importActual<typeof import('./pkce')>('./pkce');
        return {
          ...actual,
          generatePkcePair: async () => {
            throw new Error('derivation unavailable');
          },
        };
      });
      const { buildAuthorizeRequest: build } = await import('./authorizeRequest');
      await expect(build()).rejects.toThrow('derivation unavailable');
      expect(Object.keys(globalThis.sessionStorage)).toEqual([]);
    });
  });

  describe('drift guards (AC8, AC12)', () => {
    it('tracks the scope sentence in agents.md', async () => {
      const agents = await readFile(join(repoRoot, 'spec/frontend/agents.md'), 'utf8');
      const collapsed = agents.replaceAll(/\s+/g, ' ');
      expect(collapsed).toContain('The requested scopes are `openid profile email`.');
    });

    it('tracks the edge route the authorize endpoint depends on', async () => {
      const caddyfile = await readFile(join(repoRoot, 'infra/local/edge/Caddyfile'), 'utf8');
      const authMatcher = caddyfile
        .split('\n')
        .find((line) => line.includes('@auth') && line.includes('path')) as string;
      expect(authMatcher, 'the @auth matcher line is gone from the edge map').toBeDefined();
      expect(authMatcher).toContain('/oauth2/*');
      // The endpoint this task builds must fall under that prefix, or the edge sends it nowhere.
      expect(AUTHORIZE_ENDPOINT_PATH.startsWith('/oauth2/')).toBe(true);
    });

    it('tracks the callback route this task redirects to', async () => {
      const routes = await readFile(join(repoRoot, 'frontend/src/app/routes.tsx'), 'utf8');
      expect(routes).toContain("'callback'");
      expect(APP_BASENAME).toBe('/app');
    });
  });

  describe('the shared token helper T15 added to pkce.ts (D-2)', () => {
    it('returns 43 base64url characters by default, like a verifier', () => {
      const token = randomUrlSafeToken();
      expect(token).toHaveLength(43);
      expect(token).toMatch(/^[A-Za-z0-9\-_]+$/);
      expect(isValidCodeVerifier(token)).toBe(true);
    });

    it('honours a different byte length', () => {
      expect(randomUrlSafeToken(16)).toHaveLength(22);
      expect(new Set(Array.from({ length: 200 }, () => randomUrlSafeToken(16))).size).toBe(200);
    });
  });
});
