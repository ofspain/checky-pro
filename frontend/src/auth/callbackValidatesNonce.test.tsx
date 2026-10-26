/// <reference types="node" />
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { act, StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { en } from '../i18n/catalogue';
import { createI18n } from '../i18n/i18n';
import { AUTHORIZE_STORAGE_KEYS } from './authorizeRequest';
import { idTokenNonceMatchesStoredValue, type NonceRejection } from './idTokenNonce';
import { randomUrlSafeToken } from './pkce';
import { takeSignInNotice } from './signInNotice';

/**
 * T19 - the ID token's `nonce` must equal the value this tab stored, or the sign-in is rejected (R7, L1).
 *
 * `state` ties the callback URL back to this tab; tasks 17 and 18 built that, in `callbackWithMismatchedState
 * IsDiscarded`. The `nonce` ties the ID TOKEN back to it, which is a different hop: a token minted legitimately
 * for some other login and substituted into this callback carries a perfectly good `state`, and nothing but this
 * check notices.
 *
 * Two layers, deliberately. The decision is exercised DIRECTLY for the shape matrix, where a component harness
 * would add noise and hide which input produced which outcome; and it is exercised THROUGH the screen for the
 * outcome, because task 19's phase 6 found that the check can be skipped entirely by a test double that resolves
 * the wrong shape. Before the seam was corrected, `response.idToken` threw a TypeError before the comparison ran:
 * the nonce assertions passed, having tested nothing. Every block below therefore carries a control proving its
 * assertion is REACHED, not merely satisfied.
 */

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

type Mounted = { root: Root; container: HTMLElement; router: { dispose(): void } };
let mounted: Mounted | undefined;
let nonceSource: string | undefined;
let callbackSource: string | undefined;

const STORED = 'the-stored-nonce';

/** One macrotask turn inside act: the seam is async, and act alone does not await the microtask queue. */
async function turn(): Promise<void> {
  await act(async () => {
    await new Promise((resolve) => {
      setTimeout(resolve, 0);
    });
  });
}

/**
 * Mints an ID token carrying the given claims.
 *
 * Encoded through TextEncoder rather than `btoa(JSON.stringify(...))`: btoa throws above U+00FF, and a helper
 * that cannot build a non-ASCII payload cannot reach the decoder's TextDecoder step at all. The signature
 * segment is filler - nothing in the SPA reads or verifies it (OIDC Core 3.1.3.7), and a test that signed it
 * would assert a property the production code deliberately does not have.
 */
function tokenWith(claims: unknown): string {
  const segment = (value: unknown) => {
    const bytes = new TextEncoder().encode(JSON.stringify(value));
    let binary = '';
    for (const byte of bytes) binary += String.fromCharCode(byte);
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  };
  return `${segment({ alg: 'RS256', kid: 'test' })}.${segment(claims)}.c2lnbmF0dXJl`;
}

function storeNonce(value: string): void {
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.nonce, value);
}

/** The three items as task 15 writes them, so a case starts from a state a real browser can be in. */
function seedAllThree(state: string, nonce: string): void {
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.state, state);
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.nonce, nonce);
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.codeVerifier, 'stored-verifier');
}

type Rendered = { container: HTMLElement; router: ReturnType<typeof createMemoryRouter> };

/**
 * Replaces the seam with one that resolves the given ID token, then drives the real route table.
 *
 * `idToken` is taken as `unknown` on purpose: the point of several cases below is that the value crossing this
 * boundary is whatever a token endpoint sent, not whatever the type says.
 */
async function arriveWithToken(url: string, idToken: unknown, options: { strict?: boolean } = {}): Promise<Rendered> {
  vi.doMock('./codeExchange', async () => {
    const actual = await vi.importActual<typeof import('./codeExchange')>('./codeExchange');
    return { ...actual, exchangeAuthorizationCode: async () => ({ idToken }) };
  });
  const routes = await import('../app/routes');
  const router = createMemoryRouter(routes.appRoutes, {
    basename: routes.APP_BASENAME,
    initialEntries: [`${routes.APP_BASENAME}${url}`],
  });
  const container = document.createElement('div');
  document.body.append(container);
  const root = createRoot(container);
  const tree = (
    <I18nextProvider i18n={createI18n()}>
      <RouterProvider router={router} />
    </I18nextProvider>
  );
  await act(async () => {
    root.render(options.strict === true ? <StrictMode>{tree}</StrictMode> : tree);
  });
  await turn();
  mounted = { root, container, router };
  return { container, router };
}

/** Everything a visitor could tell one failure from another by. L4 requires these to be equal across causes. */
function outcomeOf({ container, router }: Rendered) {
  return {
    text: (container.textContent ?? '').trim(),
    pathname: router.state.location.pathname,
    search: router.state.location.search,
    action: router.state.historyAction,
  };
}

/** Lets one test mount a second screen after unmounting the first, with module state reset between them. */
function resetBetweenArrivals(): void {
  if (mounted) {
    const { root, container, router } = mounted;
    act(() => root.unmount());
    container.remove();
    router.dispose();
    mounted = undefined;
  }
  takeSignInNotice();
  vi.resetModules();
  vi.doUnmock('./codeExchange');
}

describe('callbackValidatesNonce', () => {
  beforeEach(async () => {
    globalThis.sessionStorage.clear();
    globalThis.localStorage.clear();
    const here = dirname(expect.getState().testPath as string);
    nonceSource ??= await readFile(join(here, 'idTokenNonce.ts'), 'utf8');
    callbackSource ??= await readFile(join(here, 'CallbackScreen.tsx'), 'utf8');
  });

  afterEach(() => {
    if (mounted) {
      const { root, container, router } = mounted;
      act(() => root.unmount());
      container.remove();
      router.dispose();
      mounted = undefined;
    }
    takeSignInNotice();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    vi.resetModules();
    vi.doUnmock('./codeExchange');
  });

  describe('the comparison is exact (AC1, AC2, AC5)', () => {
    // The positive control for this whole block. Without it every rejection below would also hold for a function
    // that can never accept anything, and the file would be asserting the absence of code.
    it('accepts a claim identical to the stored value', () => {
      storeNonce(STORED);
      expect(idTokenNonceMatchesStoredValue(tokenWith({ nonce: STORED }))).toEqual({ ok: true });
    });

    it.each([
      ['one character different', `${STORED}x`],
      ['one character missing', STORED.slice(0, -1)],
      ['case flipped', STORED.toUpperCase()],
      ['a leading space on the claim', ` ${STORED}`],
      ['a trailing space on the claim', `${STORED} `],
      ['a tab on the claim', `\t${STORED}`],
      ['a proper prefix', STORED.slice(0, 5)],
      ['a proper suffix', STORED.slice(5)],
      ['the empty string', ''],
      ['an unrelated value', 'something-else-entirely'],
    ])('rejects a claim that %s', (_label, claimed) => {
      storeNonce(STORED);
      expect(idTokenNonceMatchesStoredValue(tokenWith({ nonce: claimed }))).toEqual({
        ok: false,
        reason: 'mismatch',
      });
    });

    // The mirror of the whitespace rows above. Task 17 trims the SUPPLIED `state`, because query padding is a
    // client-side artefact; nothing is trimmed here, because a claim comes out of a JSON document and whatever
    // whitespace it carries was put there by whoever minted the token - exactly who this check does not trust.
    it.each([
      ['a leading space', ` ${STORED}`],
      ['a trailing space', `${STORED} `],
    ])('does not trim the stored side either: %s', (_label, stored) => {
      storeNonce(stored);
      expect(idTokenNonceMatchesStoredValue(tokenWith({ nonce: STORED }))).toEqual({
        ok: false,
        reason: 'mismatch',
      });
    });
  });

  describe('the comparison is bound to storage, not to a constant (AC6)', () => {
    /**
     * The case this task most needs, and the shape that has now failed nine times in this package: an assertion
     * compared against a constant rather than against the value under test. In task 18 the mutant that compared
     * storage against a hardcoded literal survived 125 of 126 cases, because EVERY success fixture seeded the
     * same string. So these rows share no literal with each other or with STORED.
     */
    it.each([
      ['a short value', 'q'],
      ['a long unrelated value', 'a-completely-different-nonce-value-entirely'],
      ['a production-shaped token', randomUrlSafeToken()],
      ['a value that looks like a state', 'themistra-state-like-0123456789'],
    ])('accepts only the value actually stored: %s', (_label, value) => {
      storeNonce(value);
      expect(idTokenNonceMatchesStoredValue(tokenWith({ nonce: value })), 'the stored value must match').toEqual({
        ok: true,
      });
      // Same token, different store: the outcome must flip. A comparison against any constant passes the line
      // above for at least one row and fails here for all of them.
      globalThis.sessionStorage.clear();
      storeNonce(`${value}-but-not-quite`);
      expect(idTokenNonceMatchesStoredValue(tokenWith({ nonce: value })), 'any other stored value must not').toEqual({
        ok: false,
        reason: 'mismatch',
      });
    });

    it('reads the nonce key, not the state or the verifier (AC7)', () => {
      // Each of the other two holds the value the token claims. A read of the wrong key would therefore MATCH,
      // which is the only way this case can fail - and is exactly the regression it exists to catch.
      globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.state, STORED);
      globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.codeVerifier, STORED);
      storeNonce('something-the-token-does-not-carry');
      expect(idTokenNonceMatchesStoredValue(tokenWith({ nonce: STORED }))).toEqual({
        ok: false,
        reason: 'mismatch',
      });
    });
  });

  describe('the claim must be a string that is there (AC3, AC4)', () => {
    // Measured against the running auth service, not assumed: with no `nonce` on the authorize request, Spring
    // Authorization Server OMITS the claim rather than emitting null or ''. This SPA always sends one, so an
    // absent claim means the token was not minted for this flow.
    it('rejects a payload with no nonce claim at all', () => {
      storeNonce(STORED);
      expect(idTokenNonceMatchesStoredValue(tokenWith({ sub: 'someone', aud: 'checky-spa' }))).toEqual({
        ok: false,
        reason: 'claim-missing',
      });
    });

    it.each([
      ['null', null],
      ['a number', 42],
      ['a number that reads like the stored value', Number.NaN],
      ['false', false],
      ['true', true],
      ['an object', { toString: 'the-stored-nonce' }],
      ['an array', [STORED]],
      ['an array of one empty string', ['']],
    ])('rejects a nonce claim that is %s rather than coercing it', (_label, claimed) => {
      storeNonce(STORED);
      expect(idTokenNonceMatchesStoredValue(tokenWith({ nonce: claimed }))).toEqual({
        ok: false,
        reason: 'claim-not-a-string',
      });
    });

    // The reason these are checked rather than coerced. String([x]) is 'the-stored-nonce' for an array of one,
    // so a comparison written with == or String(...) would ACCEPT a crafted array claim.
    it('would have accepted an array claim under coercion, and does not', () => {
      expect(String([STORED]), 'the premise of this case').toBe(STORED);
      storeNonce(STORED);
      expect(idTokenNonceMatchesStoredValue(tokenWith({ nonce: [STORED] })).ok).toBe(false);
    });
  });

  describe('the token itself must be readable (AC18)', () => {
    it.each([
      ['an empty string', ''],
      ['one segment', 'aGVsbG8'],
      ['two segments, an unsecured JWT shape', 'aGVhZGVy.eyJub25jZSI6ImEifQ'],
      ['four segments, a JWE shape', 'a.b.c.d'],
      ['a payload that is not base64url', 'aGVhZGVy.!!!not-base64!!!.c2ln'],
      ['a payload that decodes to something that is not JSON', `aGVhZGVy.${btoa('not json at all')}.c2ln`],
      ['a payload that is JSON but a string', `aGVhZGVy.${btoa('"a string"')}.c2ln`],
      ['a payload that is JSON but a number', `aGVhZGVy.${btoa('42')}.c2ln`],
      ['a payload that is JSON null', `aGVhZGVy.${btoa('null')}.c2ln`],
      ['a payload that is a JSON array', `aGVhZGVy.${btoa('[{"nonce":"the-stored-nonce"}]')}.c2ln`],
    ])('rejects %s as a malformed token', (_label, token) => {
      storeNonce(STORED);
      expect(idTokenNonceMatchesStoredValue(token)).toEqual({ ok: false, reason: 'malformed-token' });
    });

    /**
     * The finding the independent review and the self-review both landed on, pinned.
     *
     * The value crosses a network boundary the compiler cannot see: task 20 builds it from a token response, and
     * an endpoint returns no `id_token` whenever `openid` is not among the authorized scopes. Before this was
     * fixed, `.split` ran before the guard and threw a TypeError out of a function documented as never throwing
     * - the visitor outcome stayed correct only because a `.catch` written for network failures rescued it.
     */
    it.each([
      ['undefined', undefined],
      ['null', null],
      ['a number', 42],
      ['an object', {}],
      ['an array', []],
      ['a function', () => 'nope'],
    ])('rejects %s instead of throwing', (_label, value) => {
      storeNonce(STORED);
      expect(() => idTokenNonceMatchesStoredValue(value)).not.toThrow();
      expect(idTokenNonceMatchesStoredValue(value)).toEqual({ ok: false, reason: 'malformed-token' });
    });

    /**
     * The case the old helper could not build at all.
     *
     * `btoa(JSON.stringify(...))` throws above U+00FF, so a payload with a non-ASCII claim was unreachable and
     * the decoder's TextDecoder step was unfalsifiable - a plain `atob` read would have passed every test. This
     * is the control for that line: it fails if the decode stops handling UTF-8.
     */
    it('reads a payload carrying non-ASCII claims', () => {
      storeNonce(STORED);
      const token = tokenWith({ nonce: STORED, name: 'Zoë Ωmega 日本語', note: '→ ✓' });
      expect(idTokenNonceMatchesStoredValue(token)).toEqual({ ok: true });
    });

    it('rejects a non-ASCII payload whose nonce does not match, so the case above is not passing on length', () => {
      storeNonce(STORED);
      expect(idTokenNonceMatchesStoredValue(tokenWith({ nonce: 'Ωther', name: 'Zoë' }))).toEqual({
        ok: false,
        reason: 'mismatch',
      });
    });
  });

  describe('there must be something stored to compare against (AC8, AC9)', () => {
    it('rejects when nothing is stored', () => {
      expect(idTokenNonceMatchesStoredValue(tokenWith({ nonce: STORED }))).toEqual({
        ok: false,
        reason: 'nothing-stored',
      });
    });

    it('rejects when the stored value is empty, including against an empty claim', () => {
      storeNonce('');
      expect(idTokenNonceMatchesStoredValue(tokenWith({ nonce: '' }))).toEqual({
        ok: false,
        reason: 'nothing-stored',
      });
    });

    // Storage can THROW, not merely return null - blocked site data, some webviews. There is no error boundary
    // anywhere in production source, so an escaping throw blanks the screen at the very end of a sign-in.
    it('rejects, without throwing, when reading storage throws', () => {
      vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
        throw new DOMException('denied', 'SecurityError');
      });
      expect(() => idTokenNonceMatchesStoredValue(tokenWith({ nonce: STORED }))).not.toThrow();
      expect(idTokenNonceMatchesStoredValue(tokenWith({ nonce: STORED }))).toEqual({
        ok: false,
        reason: 'nothing-stored',
      });
    });

    // The ordering recorded in phase 9: storage is deliberately not read for a token already known to be
    // unusable. Asserting it here stops a later reader from "fixing" the order and doing pointless work.
    it('does not read storage at all when the token cannot be decoded', () => {
      const getItem = vi.spyOn(Storage.prototype, 'getItem');
      expect(idTokenNonceMatchesStoredValue('not.a.valid-payload!!!').ok).toBe(false);
      expect(getItem.mock.calls.map(([key]) => key)).not.toContain(AUTHORIZE_STORAGE_KEYS.nonce);
    });

    it('does read storage once the claim is usable, so the case above is about ordering and not about silence', () => {
      storeNonce(STORED);
      const getItem = vi.spyOn(Storage.prototype, 'getItem');
      expect(idTokenNonceMatchesStoredValue(tokenWith({ nonce: STORED })).ok).toBe(true);
      expect(getItem.mock.calls.map(([key]) => key)).toContain(AUTHORIZE_STORAGE_KEYS.nonce);
    });
  });

  describe('nothing stored is written, removed or cleared (AC12)', () => {
    it.each([
      ['a match', STORED, true],
      ['a mismatch', 'a-different-claim', false],
    ])('leaves all three items untouched on %s', (_label, claimed, expected) => {
      seedAllThree('stored-state', STORED);
      const setItem = vi.spyOn(Storage.prototype, 'setItem');
      const removeItem = vi.spyOn(Storage.prototype, 'removeItem');
      const clear = vi.spyOn(Storage.prototype, 'clear');

      expect(idTokenNonceMatchesStoredValue(tokenWith({ nonce: claimed })).ok).toBe(expected);

      expect(setItem).not.toHaveBeenCalled();
      expect(removeItem).not.toHaveBeenCalled();
      expect(clear).not.toHaveBeenCalled();
      // Survival, not just restraint. Deleting the three is task 23's, and task 18 left a case that fails by
      // design the moment task 23 lands.
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state)).toBe('stored-state');
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.nonce)).toBe(STORED);
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.codeVerifier)).toBe('stored-verifier');
    });
  });

  describe('the screen acts on the decision (AC1, AC10, AC15)', () => {
    /**
     * The control for the entire wiring, and the lesson of this task's phase 6.
     *
     * The check can be skipped in a way that leaves every assertion green: before the seam was corrected, the
     * mocked exchange resolved `undefined`, so `response.idToken` threw before the comparison ran. This case
     * fails if the screen ever stops consulting the decision, because the ONLY difference between it and the
     * next case is the nonce inside the token.
     */
    it('holds on the callback screen when the nonce agrees', async () => {
      seedAllThree('xyz', STORED);
      const rendered = await arriveWithToken('/callback?code=abc&state=xyz', tokenWith({ nonce: STORED }));

      expect(rendered.router.state.location.pathname).toBe('/app/callback');
      expect(rendered.container.textContent ?? '').toContain(en.callback.working);
    });

    it('returns the visitor to sign-in when the nonce does not', async () => {
      seedAllThree('xyz', STORED);
      const rendered = await arriveWithToken('/callback?code=abc&state=xyz', tokenWith({ nonce: 'not-the-one' }));

      expect(rendered.router.state.location.pathname).toBe('/app/sign-in');
      expect(rendered.container.textContent ?? '').toContain(en.signIn.discarded);
    });

    it.each([
      ['an absent claim', tokenWith({ sub: 'x' })],
      ['a non-string claim', tokenWith({ nonce: 42 })],
      ['a malformed token', 'not-a-jwt'],
      ['no token at all', undefined],
    ])('returns the visitor to sign-in for %s', async (_label, token) => {
      seedAllThree('xyz', STORED);
      const rendered = await arriveWithToken('/callback?code=abc&state=xyz', token);
      expect(rendered.router.state.location.pathname).toBe('/app/sign-in');
    });

    /**
     * L4: a visitor cannot tell WHICH check failed. The nonce failure is compared byte for byte against task
     * 17's missing-state discard - not merely "contains the same words", because copy that differed in markup,
     * in the query string or in the history action would still disclose the cause.
     */
    it('produces an outcome identical to a missing state (AC10)', async () => {
      seedAllThree('xyz', STORED);
      const badNonce = outcomeOf(await arriveWithToken('/callback?code=abc&state=xyz', tokenWith({ nonce: 'no' })));

      resetBetweenArrivals();

      seedAllThree('xyz', STORED);
      const noState = outcomeOf(await arriveWithToken('/callback?code=abc', tokenWith({ nonce: STORED })));

      expect(badNonce).toEqual(noState);
      expect(badNonce.pathname, 'and both really did discard').toBe('/app/sign-in');
      expect(badNonce.search, 'the authorization code must not survive into history').toBe('');
      expect(badNonce.action).toBe('REPLACE');
    });

    // R8 runs first and is not weakened. A callback whose state never matched must discard before any nonce work
    // - otherwise a forged callback would reach the stored nonce.
    it('discards a mismatched state without reading the nonce (AC15)', async () => {
      seedAllThree('the-real-state', STORED);
      const getItem = vi.spyOn(Storage.prototype, 'getItem');
      const rendered = await arriveWithToken('/callback?code=abc&state=forged', tokenWith({ nonce: STORED }));

      expect(rendered.router.state.location.pathname).toBe('/app/sign-in');
      expect(getItem.mock.calls.map(([key]) => key)).not.toContain(AUTHORIZE_STORAGE_KEYS.nonce);
    });

    it('reads the nonce once the state did match, so the case above is about ordering (AC15)', async () => {
      seedAllThree('xyz', STORED);
      const getItem = vi.spyOn(Storage.prototype, 'getItem');
      await arriveWithToken('/callback?code=abc&state=xyz', tokenWith({ nonce: STORED }));

      const read = getItem.mock.calls.map(([key]) => key);
      expect(read).toContain(AUTHORIZE_STORAGE_KEYS.nonce);
      // Still task 20's, and this is the assertion that catches task 20 being written here by mistake.
      expect(read).not.toContain(AUTHORIZE_STORAGE_KEYS.codeVerifier);
    });

    // StrictMode double-invokes effects in development. One arrival must still produce one outcome.
    it('produces one outcome under StrictMode', async () => {
      seedAllThree('xyz', STORED);
      const rendered = await arriveWithToken('/callback?code=abc&state=xyz', tokenWith({ nonce: 'wrong' }), {
        strict: true,
      });

      expect(rendered.router.state.location.pathname).toBe('/app/sign-in');
      expect(rendered.container.querySelectorAll('h1')).toHaveLength(1);
      expect(rendered.container.querySelectorAll('[role="status"]')).toHaveLength(1);
      expect(rendered.container.querySelectorAll('main')).toHaveLength(1);
    });
  });

  describe('a rejected sign-in keeps no token and discloses no cause (AC11, AC13)', () => {
    it('leaves no part of the token in storage or in the url', async () => {
      seedAllThree('xyz', STORED);
      const token = tokenWith({ nonce: 'wrong', sub: 'a-recognisable-subject-value' });
      const rendered = await arriveWithToken('/callback?code=abc&state=xyz', token);

      const stored = [
        ...Object.entries({ ...globalThis.sessionStorage }),
        ...Object.entries({ ...globalThis.localStorage }),
      ]
        .map(([key, value]) => `${key}=${String(value)}`)
        .join('|');
      for (const part of token.split('.')) expect(stored).not.toContain(part);
      expect(stored).not.toContain('a-recognisable-subject-value');
      expect(rendered.router.state.location.search).toBe('');
    });

    it('renders none of the rejection reasons, and no claim from the token', async () => {
      seedAllThree('xyz', STORED);
      const token = tokenWith({ nonce: 'wrong', error_description: 'the nonce did not match' });
      const rendered = await arriveWithToken('/callback?code=abc&state=xyz', token);
      const text = rendered.container.textContent ?? '';

      const reasons: NonceRejection[] = [
        'malformed-token',
        'claim-missing',
        'claim-not-a-string',
        'nothing-stored',
        'mismatch',
      ];
      for (const reason of reasons) expect(text).not.toContain(reason);
      expect(text).not.toContain('nonce');
      expect(text).not.toContain('the nonce did not match');
      // The control: the screen really did render the neutral copy, so the absences above are about this screen
      // and not about an empty container.
      expect(text).toContain(en.signIn.discarded);
    });

    it('adds no catalogue string (AC13)', () => {
      expect(Object.keys(en.signIn).sort()).toEqual(
        ['action', 'discarded', 'explanation', 'failed', 'heading', 'starting'].sort(),
      );
      expect(Object.keys(en.callback).sort()).toEqual(['heading', 'working'].sort());
    });
  });

  describe('what the source may not contain (AC5, AC14, AC19)', () => {
    /**
     * Comments are stripped before scanning.
     *
     * Not a convenience: the module's own comment explains that the payload is decoded and NOT verified, and a
     * scan for the word "verify" over the raw text therefore fails on the sentence promising the opposite of
     * the defect. The first version of these scans did exactly that, and the tempting repair was to reword the
     * comment - which would have weakened the documentation to satisfy a test that was itself wrong.
     */
    const codeOf = (source: string) =>
      source
        .replaceAll(/\/\*[\s\S]*?\*\//g, ' ')
        .split('\n')
        .filter((line) => !line.trimStart().startsWith('//'))
        .join('\n');

    const forbidden: [string, RegExp][] = [
      ['loose equality', /[^=!]==[^=]/],
      ['case folding', /toLowerCase|toUpperCase|localeCompare/],
      ['prefix or partial matching', /startsWith|endsWith|includes\(|indexOf|slice\(|substring/],
      // Narrowed to the VALUES. `segments.length !== 3` is a legitimate segment count, and forbidding every
      // `.length` comparison would have meant deleting the check that rejects an unsecured two-segment JWT.
      // What must never happen is comparing the two nonces by length instead of by value.
      ['a length comparison of the nonces', /\b(stored|claimed|nonce)\w*\.length/i],
      ['signature verification', /jwks|JWKS|crypto\.subtle|importKey|createVerify/],
      ['an absolute address', /https?:\/\//],
      ['the compile-time environment', /import\.meta\.env/],
      ['a hand-spelled storage key', /themistra\.auth\./],
    ];

    it.each(forbidden)('idTokenNonce.ts contains no %s', (_label, pattern) => {
      expect(codeOf(nonceSource ?? '')).not.toMatch(pattern);
    });

    // The control for the stripping itself: the comment the scan must look past is really there.
    it('and the comment the verify scan would have tripped on is still in the source', () => {
      expect(nonceSource ?? '').toContain('not verified');
      expect(codeOf(nonceSource ?? '')).not.toContain('not verified');
    });

    // Each pattern is fed a source that DOES violate it. A scan is sometimes the only thing standing between a
    // defect and production, so it needs its own proof that it can fail.
    it.each(forbidden)('and the scan for %s would catch one', (_label, pattern) => {
      const violations: Record<string, string> = {
        'loose equality': 'if (a == b) return;',
        'case folding': 'return a.toLowerCase() === b;',
        'prefix or partial matching': 'return a.startsWith(b);',
        'a length comparison of the nonces': 'if (storedNonce.length === claimed.length) return;',
        'signature verification': 'await crypto.subtle.importKey(jwks);',
        'an absolute address': "fetchFrom('https://auth.example.com');",
        'the compile-time environment': 'const issuer = import.meta.env.VITE_OIDC_ISSUER;',
        'a hand-spelled storage key': "getItem('themistra.auth.nonce');",
      };
      expect(violations[_label]).toMatch(pattern);
    });

    // AC19: only `nonce` is validated. An `iss` or `exp` check would be a different task's, and `iss` would need
    // an issuer the bundle may not carry (L10).
    it.each(['iss', 'aud', 'exp', 'iat', 'azp', 'sid', 'auth_time'])(
      'names no claim but nonce: %s is absent from the source',
      (claim) => {
        expect(nonceSource ?? '').not.toContain(`'${claim}'`);
        expect(nonceSource ?? '').not.toContain(`"${claim}"`);
      },
    );

    it('reads the nonce through the published constant, and the screen passes the decision through', () => {
      expect(nonceSource ?? '').toContain('AUTHORIZE_STORAGE_KEYS.nonce');
      expect(callbackSource ?? '').toContain('idTokenNonceMatchesStoredValue');
    });
  });
});
