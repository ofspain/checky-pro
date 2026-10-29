/// <reference types="node" />
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { act, StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, type Mock, vi } from 'vitest';
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

/** Every value `NonceRejection` can take. L4 forbids all of them from reaching the visitor by ANY route. */
const REJECTION_REASONS: NonceRejection[] = [
  'malformed-token',
  'claim-missing',
  'claim-not-a-string',
  'nothing-stored',
  'mismatch',
];

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
 *
 * The header and the signature can be overridden, which is how the cases below prove that neither is read.
 */
function tokenWith(claims: unknown, parts: { header?: unknown; signature?: string } = {}): string {
  const segment = (value: unknown) => {
    const bytes = new TextEncoder().encode(JSON.stringify(value));
    let binary = '';
    for (const byte of bytes) binary += String.fromCharCode(byte);
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  };
  const header = 'header' in parts ? parts.header : { alg: 'RS256', kid: 'test' };
  return `${segment(header)}.${segment(claims)}.${parts.signature ?? 'c2lnbmF0dXJl'}`;
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

type Rendered = {
  container: HTMLElement;
  router: ReturnType<typeof createMemoryRouter>;
  /** The seam itself, so a case can assert the screen really went THROUGH it, and with what. */
  exchange: Mock;
};

/**
 * Replaces the seam with one that resolves the given ID token, then drives the real route table.
 *
 * `idToken` is taken as `unknown` on purpose: the point of several cases below is that the value crossing this
 * boundary is whatever a token endpoint sent, not whatever the type says.
 *
 * `catchEscaping` is for exactly one case. The call site uses `.then(onFulfilled, discard)`, so a throw INSIDE
 * the fulfilment handler rejects a promise the screen does not keep. That is the production behaviour and it is
 * the better of the two available - a trailing `.catch(discard)` would instead run the one outcome twice - but
 * nothing handles that rejection, and vitest counts an unhandled rejection as an error and FAILS THE RUN on it
 * even when every case passes. With this flag the harness keeps the derived promise and handles it, so the one
 * case that throws on purpose stays a test about `discard` running once rather than a broken run.
 */
async function arriveWithToken(
  url: string,
  idToken: unknown,
  options: { strict?: boolean; catchEscaping?: boolean } = {},
): Promise<Rendered> {
  const exchange = vi.fn(async (_grant: { code: string; state: string }) => ({ idToken }));
  const seam =
    options.catchEscaping === true
      ? (grant: { code: string; state: string }) => {
          const settled = exchange(grant);
          return {
            then: (
              onFulfilled?: (value: { idToken: unknown }) => unknown,
              onRejected?: (reason: unknown) => unknown,
            ) => {
              const derived = settled.then(onFulfilled, onRejected);
              derived.catch(() => undefined);
              return derived;
            },
          };
        }
      : exchange;
  vi.doMock('./codeExchange', async () => {
    const actual = await vi.importActual<typeof import('./codeExchange')>('./codeExchange');
    return { ...actual, exchangeAuthorizationCode: seam };
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
  return { container, router, exchange };
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

    /**
     * The coercion trap the table above only claimed to carry.
     *
     * One row used to be labelled "a number that reads like the stored value" and passed `Number.NaN`, which
     * reads like nothing - and arrives as `null`, because JSON cannot carry it. This is the case that label
     * described: a stored nonce of digits, and a claim that is the same digits as a NUMBER. `'1234567890' ==
     * 1234567890` is true, so a comparison written with `==` accepts it, and the loose-equality scan below is
     * the only other thing standing in the way.
     */
    it('rejects a numeric claim whose text is exactly the stored value', () => {
      storeNonce('1234567890');
      expect(String(1234567890), 'the premise of this case').toBe('1234567890');
      expect(idTokenNonceMatchesStoredValue(tokenWith({ nonce: 1234567890 }))).toEqual({
        ok: false,
        reason: 'claim-not-a-string',
      });
    });

    // Why there are no rows for NaN or the infinities: JSON.stringify folds all three to `null`, so each would
    // be the `null` row above wearing a misleading label. The test review asked for them; this is the answer.
    it('cannot be handed NaN or an infinity at all, because JSON folds them to null', () => {
      for (const value of [Number.NaN, Number.POSITIVE_INFINITY, Number.NEGATIVE_INFINITY]) {
        expect(JSON.parse(JSON.stringify({ nonce: value })).nonce, `JSON folds ${String(value)}`).toBeNull();
      }
      storeNonce(STORED);
      expect(idTokenNonceMatchesStoredValue(tokenWith({ nonce: Number.NaN }))).toEqual({
        ok: false,
        reason: 'claim-not-a-string',
      });
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

    /**
     * The case that actually exercises the decode, as a mutant proved.
     *
     * The first version of this block carried non-ASCII in a NEIGHBOURING claim and an ASCII nonce - and a
     * mutant that dropped TextDecoder and read the bytes as latin-1 survived it. Mangling `name` changes
     * nothing the comparison looks at: the payload still parses, and an ASCII nonce still matches. The
     * non-ASCII has to be IN the nonce for the decode to be load-bearing.
     */
    it.each([
      ['a greek nonce', 'Ωμέγα-nonce'],
      ['a cjk nonce', '日本語のノンス'],
      ['an accented nonce', 'nonce-avec-accents-éàü'],
      ['an emoji nonce', 'nonce-🔐-value'],
    ])('matches %s only when the payload is decoded as UTF-8', (_label, value) => {
      storeNonce(value);
      expect(idTokenNonceMatchesStoredValue(tokenWith({ nonce: value }))).toEqual({ ok: true });
    });

    it('rejects a non-ASCII payload whose nonce does not match, so the case above is not passing on length', () => {
      storeNonce(STORED);
      expect(idTokenNonceMatchesStoredValue(tokenWith({ nonce: 'Ωther', name: 'Zoë' }))).toEqual({
        ok: false,
        reason: 'mismatch',
      });
    });
  });

  /**
   * What the decision deliberately does NOT look at.
   *
   * The ID token is handed over by the token endpoint on the edge's own origin, and OIDC Core 3.1.3.7 lets a
   * client that receives a token that way rely on the channel instead of verifying the signature. This one
   * does, so the header and the signature segment are never read - and the way to say that in a test is to
   * ACCEPT tokens differing only there. The test review asked for an `alg: 'none'` case that REJECTS; that
   * would demand a check this design omits on purpose, and reading `alg` adds nothing while no signature is
   * checked anyway. Rejecting a two-SEGMENT token is a different matter and is covered above: that shape has no
   * signature slot at all.
   */
  describe('the header and the signature are not read (OQ-2)', () => {
    const unread: [string, { header?: unknown; signature?: string }][] = [
      ['an alg:none header', { header: { alg: 'none' } }],
      ['no header claims at all', { header: {} }],
      ['a header that is not an object', { header: 'nonsense' }],
      ['a different signature', { signature: 'c29tZXRoaW5nLWVsc2U' }],
      ['an empty signature', { signature: '' }],
    ];

    it.each(unread)('accepts a matching nonce despite %s', (_label, parts) => {
      storeNonce(STORED);
      expect(idTokenNonceMatchesStoredValue(tokenWith({ nonce: STORED }, parts))).toEqual({ ok: true });
    });

    // The control. The rows above must pass because those segments are ignored, not because a fixture built
    // this way stopped carrying a nonce the comparison could act on at all.
    it.each(unread)('and still rejects a mismatched nonce with %s', (_label, parts) => {
      storeNonce(STORED);
      expect(idTokenNonceMatchesStoredValue(tokenWith({ nonce: 'not-the-one' }, parts))).toEqual({
        ok: false,
        reason: 'mismatch',
      });
    });
  });

  /**
   * The one fixture in this file that this file did not build.
   *
   * Every other token here comes from `tokenWith`. An encoder bug there would be caught - the positive controls
   * would stop passing - but the whole suite would still be arguing with a shape no server emits. This token was
   * minted by the running Spring Authorization Server for a real verified account, and the nonce below is the
   * value actually sent on its authorize request. It is a development-key token, long expired, and nothing here
   * reads its `exp`: task 19 validates the nonce and nothing else (AC19).
   */
  describe('a token the authorization server really minted', () => {
    const REAL_NONCE = 'TzjsI5FNAeMhC04qnYCpZQ';
    const REAL_ID_TOKEN =
      'eyJraWQiOiJkZXYtNjI2MTYxMzMiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIyZDQ5ZWMzMy03MGQzLTQ2ZGEtOGQxOC03MGE2YjVlMDhmMDkiLCJhdWQiOiJjaGVja3ktc3BhIiwiYXpwIjoiY2hlY2t5LXNwYSIsImF1dGhfdGltZSI6MTc5MzAzMTM0NywiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo1MTczIiwiZXhwIjoxNzkzMDMzMTQ3LCJpYXQiOjE3OTMwMzEzNDcsIm5vbmNlIjoiVHpqc0k1Rk5BZU1oQzA0cW5ZQ3BaUSIsImp0aSI6Ijk5NGU0NjNhLTA1YjItNDMzNS1hYWNhLWE2ZDg5ZGE5NGEzMCIsInNpZCI6IkNoM0EwRmNySDNmQWhwZUlmLXRZS3lMdzNxUkRCanN4NlY5UXhtT2s2RmsifQ.YmVQtueJhDv6NUa_d2G2hsfqiGq6gSOZYHP2NfD0ECcHffnSmqO1lM4u8LOmalz72OdXZ-ViylUE-e8az5ECWMAXt9hGYXHZwjxTEj3JDReMiLRdN-QFXLO9GpyUAJF9cH_b53osCbDGqXqkt9lCMdm-kUrh0_lnDpD74D-K6EawJA6MhmBUO5Mednmq8kEFiCBRAOnmDAVV3V3cYJg2sechY2li5y5vKl9zDk0_57RBRcsfWksVCiT3ppRgrlkHn37OvEyjL6LQngEllKuJqV56esK24-JVY_QydjljZMWOZc0nGvjO4fqkKDE95GxNcs-8z6xG7WZqD7BZMI6M2zE1YoN5BJ1eWnEZDhC0f1FqNn87dgiWyIlaDaJo-pVih9j4Y3nPy-B8_5TceR-3ApHJ2nL2XHfcetuzjutE5ZCq9bZ4DrHzTSV6ISVYKraCCyWioxJt0Bc-IdMIfPvu7ZR1kaEts-hcKxDC8IoBjpQUN9S9dgEspmVhd4-G10QS';

    it('accepts it when its nonce is the value this tab stored', () => {
      storeNonce(REAL_NONCE);
      expect(idTokenNonceMatchesStoredValue(REAL_ID_TOKEN)).toEqual({ ok: true });
    });

    it.each([
      ['one character longer', `${REAL_NONCE}x`],
      ['one character shorter', REAL_NONCE.slice(0, -1)],
      ['a different value entirely', STORED],
    ])('rejects it against a stored value that is %s', (_label, stored) => {
      storeNonce(stored);
      expect(idTokenNonceMatchesStoredValue(REAL_ID_TOKEN)).toEqual({ ok: false, reason: 'mismatch' });
    });

    // The helper is held against the real token's shape rather than round-tripped through its own encoder,
    // which would only prove it agrees with itself.
    it('and the minted fixtures have the same shape as it', () => {
      const built = tokenWith({ nonce: STORED, name: 'Zoë' });
      expect(built.split('.')).toHaveLength(REAL_ID_TOKEN.split('.').length);
      for (const token of [built, REAL_ID_TOKEN]) {
        for (const char of ['=', '+', '/']) {
          expect(token, 'base64url carries no padding, and neither + nor /').not.toContain(char);
        }
      }
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
      // Survival, not just restraint, and it survived task 23 unchanged: the PURE CHECK still touches no
      // storage. Deletion is the screen's, called after this function has read the nonce (T23-D1) - which is
      // what keeps the two readers testable without a store and why this case did not have to change.
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
    it('leaves the callback screen for the post-login route when the nonce agrees', async () => {
      seedAllThree('xyz', STORED);
      const rendered = await arriveWithToken('/callback?code=abc&state=xyz', tokenWith({ nonce: STORED }));

      // T19 asserted the screen HELD - it stayed on `/app/callback` and showed the holding copy - because a
      // passing nonce had nowhere to lead. Task 24 gave it somewhere, so the same property is now read as a
      // departure: an agreeing nonce leaves for the post-login route, a disagreeing one goes to sign-in, and the
      // two destinations are still what tells the decision apart. The holding copy is gone with the screen.
      expect(rendered.router.state.location.pathname).toBe('/app/home');
      expect(rendered.router.state.location.pathname).not.toBe('/app/sign-in');
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

    /**
     * The seam is the only route a token has to the comparison.
     *
     * The T18 suite pins the call; this is the same guard on THIS file's harness, which replaces the module. A
     * screen that validated a token it got from anywhere else would still satisfy the outcome cases above, so
     * without this the harness could drift away from what it claims to stand in for.
     */
    it('reaches the decision through the exchange, called once with this callback\'s grant', async () => {
      seedAllThree('xyz', STORED);
      const rendered = await arriveWithToken('/callback?code=abc&state=xyz', tokenWith({ nonce: STORED }));

      expect(rendered.exchange).toHaveBeenCalledTimes(1);
      expect(rendered.exchange).toHaveBeenCalledWith(expect.objectContaining({ code: 'abc', state: 'xyz' }));
      expect(
        rendered.router.state.location.pathname,
        'the control: it really did complete (task 24 moved the end point off the callback route)',
      ).toBe('/app/home');
    });

    // R8 again, from the other side: a callback that never matched must not even be exchanged (T17's property,
    // re-asserted here because this file's harness is the one that would hide a regression in it).
    it('does not reach the exchange at all when the state never matched', async () => {
      seedAllThree('the-real-state', STORED);
      const rendered = await arriveWithToken('/callback?code=abc&state=forged', tokenWith({ nonce: STORED }));

      expect(rendered.router.state.location.pathname, 'the control: it really did discard').toBe('/app/sign-in');
      expect(rendered.exchange).not.toHaveBeenCalled();
    });

    // The pure function leaves storage alone; this is the same property for the WIRING. A mutant that cleared
    // sessionStorage on a failed check survived until this case existed, because nothing downstream of the
    // screen was asserting survival. Deletion is task 23's, and task 23 runs it only on the consumed path.
    it('leaves all three stored items in place when the nonce fails (AC12)', async () => {
      seedAllThree('xyz', STORED);
      const rendered = await arriveWithToken('/callback?code=abc&state=xyz', tokenWith({ nonce: 'wrong' }));

      expect(rendered.router.state.location.pathname, 'the control: it really did reject').toBe('/app/sign-in');
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state)).toBe('xyz');
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.nonce)).toBe(STORED);
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.codeVerifier)).toBe('stored-verifier');
    });

    /**
     * The screen's one outcome runs once, even if it fails.
     *
     * The call site uses `.then(onFulfilled, discard)` rather than `.then(onFulfilled).catch(discard)`, so a
     * failure INSIDE the fulfilment handler is not then handed to `discard` a second time. The two forms are
     * indistinguishable until something in the discard path throws, which is why this case makes one: without
     * it the defensive form is unfalsifiable, and a mutant reverting it survived.
     */
    it('runs the discard path once even when it fails (AC10)', async () => {
      seedAllThree('xyz', STORED);
      const notices: unknown[] = [];
      vi.doMock('./signInNotice', async () => {
        const actual = await vi.importActual<typeof import('./signInNotice')>('./signInNotice');
        return {
          ...actual,
          setSignInNotice: (notice: unknown) => {
            notices.push(notice);
            throw new Error('the notice could not be set');
          },
        };
      });

      await arriveWithToken('/callback?code=abc&state=xyz', tokenWith({ nonce: 'wrong' }), {
        catchEscaping: true,
      });

      expect(notices, 'one failed arrival must attempt one discard, not two').toHaveLength(1);
      vi.doUnmock('./signInNotice');
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
      // The control for the sweep itself: with no entries the concatenation is empty and every absence below
      // holds vacuously. Asserting a seeded key proves both stores were read and that there was something there.
      expect(stored, 'storage really is not empty, so the absences are not vacuous').toContain(
        AUTHORIZE_STORAGE_KEYS.codeVerifier,
      );
      for (const part of token.split('.')) expect(stored).not.toContain(part);
      expect(stored).not.toContain('a-recognisable-subject-value');
      expect(rendered.router.state.location.search).toBe('');
    });

    it('renders none of the rejection reasons, and no claim from the token', async () => {
      seedAllThree('xyz', STORED);
      const token = tokenWith({ nonce: 'wrong', error_description: 'the nonce did not match' });
      const rendered = await arriveWithToken('/callback?code=abc&state=xyz', token);
      const text = rendered.container.textContent ?? '';

      // Checked against the whole document and the tab title, not only this container: a mutant that wrote the
      // reason into document.title survived a container-only scan, and a tab title is as visible as copy.
      for (const reason of REJECTION_REASONS) {
        expect(text).not.toContain(reason);
        expect(document.title).not.toContain(reason);
        expect(document.body.textContent ?? '').not.toContain(reason);
      }
      expect(text).not.toContain('nonce');
      expect(text).not.toContain('the nonce did not match');
      // The control: the screen really did render the neutral copy, so the absences above are about this screen
      // and not about an empty container.
      expect(text).toContain(en.signIn.discarded);
    });

    /**
     * AC11's third surface. Storage and the URL are swept above; the console is the one a reviewer cannot see.
     *
     * The source scans forbid a `console.` in the two modules, which is the durable guard. This is the runtime
     * half, and it covers the whole arrival - the router and i18n included - because a token logged by anything
     * on this path is just as exposed. React's own warnings are allowed through: what may not appear is a
     * rejection reason, or any piece of the token.
     */
    it('writes neither a rejection reason nor any part of the token to the console', async () => {
      const written: string[] = [];
      for (const method of ['log', 'info', 'warn', 'error', 'debug'] as const) {
        vi.spyOn(console, method).mockImplementation((...args: unknown[]) => {
          written.push(args.map((arg) => String(arg)).join(' '));
        });
      }

      seedAllThree('xyz', STORED);
      const token = tokenWith({ nonce: 'wrong', sub: 'a-recognisable-subject-value' });
      const rendered = await arriveWithToken('/callback?code=abc&state=xyz', token);

      const logged = written.join('|');
      for (const reason of REJECTION_REASONS) expect(logged).not.toContain(reason);
      for (const part of token.split('.')) expect(logged).not.toContain(part);
      expect(logged).not.toContain('a-recognisable-subject-value');
      expect(rendered.router.state.location.pathname, 'the control: it really did reject').toBe('/app/sign-in');
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
      ['prefix or partial matching', /startsWith|endsWith|includes|indexOf|slice\(|substring/],
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
        'prefix or partial matching': "return a['includes'](b) || a.startsWith(b);",
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

    const sources: [string, () => string | undefined][] = [
      ['idTokenNonce.ts', () => nonceSource],
      ['CallbackScreen.tsx', () => callbackSource],
    ];

    /**
     * AC11 forbids a token in a log as firmly as in storage, and nothing about BEHAVIOUR would notice one.
     *
     * A `console.error(payload)` left behind after a debugging session hands the whole token to anyone with the
     * devtools open, and every outcome assertion in this file would still pass. `debugger` is in the same scan
     * because it stops a production build in front of the visitor, holding the token in scope.
     */
    it.each(sources)('%s writes nothing to the console', (_label, read) => {
      expect(codeOf(read() ?? '')).not.toMatch(/console\.|debugger/);
    });

    it('and the console scan would catch one', () => {
      expect('console.error(payload);').toMatch(/console\.|debugger/);
    });

    /**
     * AC11's other half: nothing may be RETAINED.
     *
     * Both modules keep every value in function scope, so the shape to forbid is a top-level declaration - an
     * unindented `let` or `var` IS module state, and a module variable holding the last token outlives the
     * arrival that produced it. The two existing `let`s, one per module, are indented inside a function.
     */
    it.each(sources)('%s holds no module-level mutable state', (_label, read) => {
      expect(codeOf(read() ?? '')).not.toMatch(/^(let|var)\s/m);
    });

    it('and the module-state scan would catch one', () => {
      expect('let lastToken: string | null = null;\n').toMatch(/^(let|var)\s/m);
      expect('  let stored: string | null = null;\n', 'and allows a function-scoped one').not.toMatch(
        /^(let|var)\s/m,
      );
    });

    /**
     * The two-argument `.then(onFulfilled, discard)` is load-bearing, and only observable when something in the
     * discard path throws.
     *
     * A trailing `.catch(discard)` would hand a failure inside the fulfilment handler to the one outcome a
     * SECOND time. The behavioural case for that is above and has to manufacture a throw to see it; this is the
     * durable half, which a later tidy-up into a chained `.catch` trips over immediately.
     */
    it('routes the exchange through then, not through a trailing catch', () => {
      expect(codeOf(callbackSource ?? '')).not.toMatch(/\.catch\(/);
      expect(codeOf(callbackSource ?? ''), 'and really does use the two-argument form').toMatch(/\}, discard\);/);
    });

    it('reads the nonce through the published constant, and the screen passes the decision through', () => {
      expect(nonceSource ?? '').toContain('AUTHORIZE_STORAGE_KEYS.nonce');
      expect(callbackSource ?? '').toContain('idTokenNonceMatchesStoredValue');
    });
  });
});
