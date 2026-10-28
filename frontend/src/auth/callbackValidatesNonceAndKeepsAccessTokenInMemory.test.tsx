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
import { AUTHORIZE_STORAGE_KEYS, buildAuthorizeRequest, CLIENT_ID, redirectUri } from './authorizeRequest';
import { createAuthApi } from '../api/client';
import { shareRenewalAcrossTabs } from '../api/crossTabRenewal';
import { createSessionBoundary } from '../api/sessionBoundary';
import { accessToken, clearAccessToken, retainAccessToken } from './accessToken';
import { exchangeAuthorizationCode } from './codeExchange';
import { takeSignInNotice } from './signInNotice';

/**
 * T20 - the token request carries the STORED `code_verifier` (R7).
 *
 * `checky-spa` is registered with `ClientAuthenticationMethod.NONE`. There is no client secret, no client
 * assertion and no credential of any kind on this request, so the stored verifier is the ONLY evidence that the
 * code is being redeemed by the tab that asked for it. A request that sent a freshly generated verifier, or one
 * read from the wrong key, would be an unauthenticated code redemption that the authorization server cannot tell
 * from the real thing - which is why the first block below proves the value travels, over two unrelated stored
 * values, rather than merely proving that some verifier field is present.
 *
 * This file shares its name with task 21, which adds the in-memory half. T20 owns the verifier, the request
 * shape, the single failure outcome and the absence of a refresh token.
 *
 * Two layers, as in T19. The exchange is driven DIRECTLY for the wire and the failure matrix, where a component
 * harness would hide which input produced which outcome; and THROUGH the real screen for the outcome and the
 * ordering, because the properties R8 and L4 describe are properties of the screen, not of this function. Every
 * block carries a control proving its assertion is REACHED and not merely satisfied: this package's recurring
 * defect is an assertion compared against the constant it is testing.
 */

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

type Mounted = { root: Root; container: HTMLElement; router: { dispose(): void } };
let mounted: Mounted | undefined;
let exchangeSource: string | undefined;

const STATE = 'the-stored-state';
const STORED_NONCE = 'the-stored-nonce';
/** Two unrelated values, so no case can pass against a constant the production code happens to carry (AC2). */
const STORED_VERIFIER = 'Vq7xV3nBu2mK9pLdR4sTaYwZcE6hJ1gN8oF5iU0bX2';
const OTHER_VERIFIER = 'Zm4kP8wQ1yT6rC3vB7nL5sD9fH2jG0xA4eI6uO8aK1';
const ACCESS_TOKEN = 'ACCESS-TOKEN-7f3a9c2e5b1d';
/** The authorization server's own words about a failure. L4 forbids every one of them reaching the SPA. */
const SERVER_DETAIL = 'the code verifier did not match the challenge';

/** One macrotask turn inside act: the exchange is async, and act alone does not drain the microtask queue. */
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
 * Encoded through TextEncoder rather than `btoa(JSON.stringify(...))`, exactly as T19's suite does: `btoa` throws
 * above U+00FF, and the signature segment is filler because nothing in the SPA verifies it (OIDC Core 3.1.3.7).
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

const ID_TOKEN = tokenWith({ nonce: STORED_NONCE, sub: 'the-subject', iss: 'http://localhost:5173' });

/** The response shape measured against the running auth service: five fields, and no refresh token among them. */
function tokensBody(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    access_token: ACCESS_TOKEN,
    id_token: ID_TOKEN,
    token_type: 'Bearer',
    expires_in: 299,
    scope: 'openid profile email',
    ...overrides,
  };
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

/** What a replayed or mismatched code really gets back, measured: HTTP 400 with an OAuth2 error object. */
function refusal(): Response {
  return jsonResponse({ error: 'invalid_grant', error_description: SERVER_DETAIL }, 400);
}

/**
 * Browser-style URL resolution plus a stubbed fetch, the pattern `generatedClientCompiles.test.ts:26-44`
 * established and the L6 lint test's ALLOWED table sanctions. Node's `Request` rejects a relative URL; a browser
 * resolves it against the document, which is the whole point of L9 and must not be papered over with an absolute
 * address in the test.
 */
function stubFetch(answer: () => Response | Promise<Response>): Mock {
  const NodeRequest = globalThis.Request;
  vi.stubGlobal(
    'Request',
    class extends NodeRequest {
      constructor(input: RequestInfo | URL, init?: RequestInit) {
        super(typeof input === 'string' ? new URL(input, window.location.href) : input, init);
      }
    },
  );
  const spy = vi.fn(async () => answer());
  vi.stubGlobal('fetch', spy);
  return spy;
}

function sentRequest(spy: Mock): Request {
  expect(spy, 'exactly one request reached the network').toHaveBeenCalledTimes(1);
  return spy.mock.calls[0]![0] as Request;
}

/** The serialized body, parsed back. Asserting the OBJECT handed to the client would not catch a serializer bug. */
async function sentFields(spy: Mock): Promise<Record<string, string>> {
  const text = await sentRequest(spy).clone().text();
  return Object.fromEntries(new URLSearchParams(text));
}

/** The three items as task 15 writes them, so every case starts from a state a real browser can be in. */
function seedAllThree(verifier: string = STORED_VERIFIER): void {
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.state, STATE);
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.nonce, STORED_NONCE);
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.codeVerifier, verifier);
}

const GRANT = { code: 'THE-AUTHORIZATION-CODE', state: STATE };

/** Returns the rejection, or fails loudly rather than letting a resolved promise pass as a rejection. */
async function rejectionOf(promise: Promise<unknown>): Promise<Error> {
  try {
    await promise;
  } catch (error) {
    return error as Error;
  }
  throw new Error('the exchange resolved where this case required a rejection');
}

/**
 * The one message every failure produces, taken from a fault that never reaches the network.
 *
 * Read out of the code rather than written down here on purpose: hard-coding it would pin the wording instead of
 * the property, and the property is that there is only ONE message. Anything carrying a status, a server phrase or
 * an exception's own name differs from this and fails, whatever the words.
 */
async function theOneMessage(): Promise<string> {
  globalThis.sessionStorage.clear();
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.state, STATE);
  stubFetch(() => jsonResponse(tokensBody()));
  const rejection = await rejectionOf(exchangeAuthorizationCode(GRANT));
  vi.unstubAllGlobals();
  globalThis.sessionStorage.clear();
  return rejection.message;
}

type Rendered = {
  container: HTMLElement;
  router: ReturnType<typeof createMemoryRouter>;
  fetchSpy: Mock;
  unmount: () => void;
  /**
   * The holder THE SCREEN USED, not the one this file imported at the top.
   *
   * `arriveAt` imports the route graph dynamically and `vi.resetModules()` runs between cases, so the screen gets
   * a fresh instance of every module it reaches - `accessToken.ts` included. Asserting on the static import after
   * driving the screen would read an untouched module and pass whatever production did. T20's Phase 11 lost three
   * assertions to exactly this, on `signInNotice`, and only a probe showed they were inert. This import happens
   * after the routes import, inside the same registry, so it is the same instance the screen wrote to - and the
   * control case below proves it by requiring a token to be THERE.
   */
  holder: typeof import('./accessToken');
};

/** Drives the real route table over the real exchange, with only the network stubbed. */
async function arriveAt(
  url: string,
  answer: () => Response | Promise<Response>,
  options: { strict?: boolean } = {},
): Promise<Rendered> {
  const fetchSpy = stubFetch(answer);
  const routes = await import('../app/routes');
  const holder = await import('./accessToken');
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
  return {
    container,
    router,
    holder,
    fetchSpy,
    unmount: () => {
      act(() => root.unmount());
      container.remove();
      mounted = undefined;
    },
  };
}

/**
 * The sign-in notice is asserted through the RENDERED PAGE and never by calling `takeSignInNotice()` here.
 *
 * `arriveAt` imports the route graph dynamically and `vi.resetModules()` runs between cases, so the screens hold
 * a DIFFERENT instance of `signInNotice.ts` from the one this file imports. A `takeSignInNotice()` assertion in a
 * case would therefore read an always-empty module and pass whatever production did - which is this package's
 * recurring defect exactly. Three such assertions were written and removed once a probe showed them inert. The
 * import that remains is used only to clear state between cases, where being the wrong instance is harmless.
 */

/** Everything a visitor could tell one failure from another by. L4 requires these equal across causes. */
function outcomeOf({ container, router }: Rendered) {
  return {
    text: (container.textContent ?? '').trim(),
    pathname: router.state.location.pathname,
    search: router.state.location.search,
    action: router.state.historyAction,
  };
}

let callbackSource: string | undefined;

/** The callback screen's own source, for the two claims that can only be made structurally. */
function callbackSourceFor(): string {
  return callbackSource ?? '';
}

describe('callbackValidatesNonceAndKeepsAccessTokenInMemory', () => {
  beforeEach(async () => {
    globalThis.sessionStorage.clear();
    globalThis.localStorage.clear();
    clearAccessToken();
    const here = dirname(expect.getState().testPath as string);
    exchangeSource ??= await readFile(join(here, 'codeExchange.ts'), 'utf8');
    callbackSource ??= await readFile(join(here, 'CallbackScreen.tsx'), 'utf8');
  });

  afterEach(() => {
    clearAccessToken();
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
  });

  describe('the stored verifier is what travels (AC1, AC2, AC21)', () => {
    // The positive control for this whole block. Without it, every rejection below would also hold for a function
    // that can never send anything at all, and the block would be asserting the absence of code.
    it('sends the stored verifier', async () => {
      seedAllThree();
      const spy = stubFetch(() => jsonResponse(tokensBody()));

      await exchangeAuthorizationCode(GRANT);

      expect((await sentFields(spy)).code_verifier).toBe(STORED_VERIFIER);
    });

    // AC2. One stored value proves nothing: a function that shipped the verifier as a hard-coded constant would
    // pass the case above. Two unrelated values cannot both be a constant.
    it.each([
      ['the first', STORED_VERIFIER],
      ['the second', OTHER_VERIFIER],
    ])('sends %s stored value, so no constant can pass', async (_which, verifier) => {
      seedAllThree(verifier);
      const spy = stubFetch(() => jsonResponse(tokensBody()));

      await exchangeAuthorizationCode(GRANT);

      expect((await sentFields(spy)).code_verifier).toBe(verifier);
    });

    it('sends the stored value verbatim, neither trimmed nor re-encoded', async () => {
      seedAllThree('  padded-verifier  ');
      const spy = stubFetch(() => jsonResponse(tokensBody()));

      await exchangeAuthorizationCode(GRANT);

      // Task 15 wrote this value, so padding in it means something is already wrong. Trimming here would hide
      // that from the one party able to detect it - the authorization server, which compares the challenge.
      expect((await sentFields(spy)).code_verifier).toBe('  padded-verifier  ');
    });

    it.each([
      ['nothing is stored', () => undefined],
      ['the stored value is empty', () => globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.codeVerifier, '')],
    ])('makes no request at all when %s', async (_case, seed) => {
      globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.state, STATE);
      seed();
      const spy = stubFetch(() => jsonResponse(tokensBody()));

      await expect(exchangeAuthorizationCode(GRANT)).rejects.toThrow();

      // Not merely a rejection: a code presented without the verifier is an unauthenticated redemption attempt,
      // and the server would be right to count it. The request must not leave at all.
      expect(spy).not.toHaveBeenCalled();
    });

    it('turns a storage read that THROWS into the ordinary rejection, and makes no request', async () => {
      const expected = await theOneMessage();
      seedAllThree();
      const spy = stubFetch(() => jsonResponse(tokensBody()));
      // Touching sessionStorage raises where site data is blocked; it does not merely answer null. There is no
      // error boundary anywhere in this application, so an escaping throw here would blank the page at the end of
      // a sign-in, after the visitor had already authenticated.
      vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
        throw new DOMException('denied', 'SecurityError');
      });

      const rejection = await rejectionOf(exchangeAuthorizationCode(GRANT));

      // Asserting only that it rejects cannot see the guard at all: the function is `async`, so an UNGUARDED read
      // rejects too, with the DOMException. A mutation run proved exactly that - removing the try/catch survived
      // every other assertion in this file. Only the message tells the two apart.
      expect(rejection.message).toBe(expected);
      expect(rejection.name).toBe('Error');
      expect(rejection.message).not.toContain('denied');
      expect(spy).not.toHaveBeenCalled();
    });

    it('reads the verifier through the published key and no other', async () => {
      globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.state, STATE);
      globalThis.sessionStorage.setItem('themistra.auth.verifier', STORED_VERIFIER);
      globalThis.sessionStorage.setItem('code_verifier', STORED_VERIFIER);
      const spy = stubFetch(() => jsonResponse(tokensBody()));

      // The value is in storage twice, under two plausible near-miss keys, and still must not be found.
      await expect(exchangeAuthorizationCode(GRANT)).rejects.toThrow();

      expect(spy).not.toHaveBeenCalled();
    });

    it('asks storage for exactly the published key', async () => {
      seedAllThree();
      stubFetch(() => jsonResponse(tokensBody()));
      const getItem = vi.spyOn(Storage.prototype, 'getItem');

      await exchangeAuthorizationCode(GRANT);

      expect(getItem.mock.calls.map(([key]) => key)).toContain(AUTHORIZE_STORAGE_KEYS.codeVerifier);
    });
  });

  describe('the wire (AC3, AC4, AC5, AC6)', () => {
    it('POSTs to the relative token path, resolved against the page origin', async () => {
      seedAllThree();
      const spy = stubFetch(() => jsonResponse(tokensBody()));

      await exchangeAuthorizationCode(GRANT);

      const sent = sentRequest(spy);
      expect(sent.method).toBe('POST');
      // L9: one origin, relative paths. The edge routes /oauth2/* to the auth service; the SPA names no host.
      expect(sent.url).toBe(`${window.location.origin}/oauth2/token`);
    });

    it('sends the exact form content type the serializer requires', async () => {
      seedAllThree();
      const spy = stubFetch(() => jsonResponse(tokensBody()));

      await exchangeAuthorizationCode(GRANT);

      // Exactly this string, with no charset parameter and no difference of case: openapi-fetch compares with
      // `===` before form-encoding, so anything else falls through to JSON.stringify and the verifier never
      // arrives in a form the token endpoint reads.
      expect(sentRequest(spy).headers.get('Content-Type')).toBe('application/x-www-form-urlencoded');
    });

    it('sends a form-encoded body, not JSON', async () => {
      seedAllThree();
      const spy = stubFetch(() => jsonResponse(tokensBody()));

      await exchangeAuthorizationCode(GRANT);

      const text = await sentRequest(spy).clone().text();
      expect(text).toContain('grant_type=authorization_code');
      expect(text.startsWith('{')).toBe(false);
    });

    it('sends exactly the five fields, and no sixth', async () => {
      seedAllThree();
      const spy = stubFetch(() => jsonResponse(tokensBody()));

      await exchangeAuthorizationCode(GRANT);

      expect(Object.keys(await sentFields(spy)).sort()).toEqual([
        'client_id',
        'code',
        'code_verifier',
        'grant_type',
        'redirect_uri',
      ]);
    });

    it('does not send `state`, which the grant carries and the endpoint has no parameter for', async () => {
      seedAllThree();
      const spy = stubFetch(() => jsonResponse(tokensBody()));

      await exchangeAuthorizationCode(GRANT);

      const fields = await sentFields(spy);
      expect(fields.state).toBeUndefined();
      // The premise: the grant really did carry a state, so its absence on the wire is a choice and not an
      // accident of the fixture.
      expect(GRANT.state).toBe(STATE);
    });

    it('sends the callback query values and the published client id', async () => {
      seedAllThree();
      const spy = stubFetch(() => jsonResponse(tokensBody()));

      await exchangeAuthorizationCode(GRANT);

      const fields = await sentFields(spy);
      expect(fields.grant_type).toBe('authorization_code');
      expect(fields.code).toBe(GRANT.code);
      expect(fields.client_id).toBe(CLIENT_ID);
    });

    it('sends a redirect_uri byte-identical to the authorize request (AC5)', async () => {
      seedAllThree();
      const spy = stubFetch(() => jsonResponse(tokensBody()));
      // Built by the real authorize path, not reconstructed here, so the two values are compared as the
      // authorization server compares them: exact match against one registered string.
      const authorize = await buildAuthorizeRequest();
      const authorizeRedirect = new URL(authorize.url).searchParams.get('redirect_uri');
      seedAllThree();

      await exchangeAuthorizationCode(GRANT);

      expect((await sentFields(spy)).redirect_uri).toBe(authorizeRedirect);
      expect((await sentFields(spy)).redirect_uri).toBe(redirectUri());
    });

    it('serializes the body to exactly these bytes, in this order (AC4)', async () => {
      seedAllThree();
      const spy = stubFetch(() => jsonResponse(tokensBody()));

      await exchangeAuthorizationCode(GRANT);

      // Stronger than the parsed comparison above, which cannot see field order or percent-encoding. What the
      // authorization server reads is bytes, so this pins bytes - including that `redirect_uri` arrives encoded
      // rather than raw, which is the difference between an exact match and a 400.
      expect(await sentRequest(spy).clone().text()).toBe(
        [
          'grant_type=authorization_code',
          `code=${GRANT.code}`,
          `redirect_uri=${encodeURIComponent(redirectUri())}`,
          `client_id=${CLIENT_ID}`,
          `code_verifier=${STORED_VERIFIER}`,
        ].join('&'),
      );
    });

    it('carries no credential the SPA could have added (AC6)', async () => {
      seedAllThree();
      const spy = stubFetch(() => jsonResponse(tokensBody()));

      await exchangeAuthorizationCode(GRANT);

      const sent = sentRequest(spy);
      // The whole security story of a public client: there is no secret, so there is none to steal. A request
      // that authenticated itself would mean one existed.
      expect(sent.headers.get('Authorization')).toBeNull();
      expect(sent.headers.get('Proxy-Authorization')).toBeNull();
      const fields = await sentFields(spy);
      expect(fields.client_secret).toBeUndefined();
      expect(fields.client_assertion).toBeUndefined();
      expect(fields.client_assertion_type).toBeUndefined();
      expect(JSON.stringify(fields).toLowerCase()).not.toContain('secret');
      // No credentials smuggled into the URL's userinfo component either.
      const parsed = new URL(sent.url);
      expect(parsed.username).toBe('');
      expect(parsed.password).toBe('');
      // The name of this case is deliberately narrower than "no credential of any kind". `fetch` sends
      // same-origin cookies by default, so the httpOnly SAS SESSION cookie travels with this request. That is a
      // user session, not a client credential, it is outside the SPA's reach by design (the SPA cannot read it),
      // and the authorization server already holds it. What T20 must not add is a credential of its OWN, and
      // that is what is asserted here.
    });
  });

  describe('every failure is the same rejection (AC11, AC20, T20-D9)', () => {
    /** Every key in both stores, so a case can prove a value reached neither. */
    function storageDump(): string {
      const lines: string[] = [];
      for (const store of [globalThis.sessionStorage, globalThis.localStorage]) {
        for (let index = 0; index < store.length; index += 1) {
          const key = store.key(index) ?? '';
          lines.push(`${key}=${store.getItem(key) ?? ''}`);
        }
      }
      return lines.join('\n');
    }

    const FAULTS: ReadonlyArray<{ label: string; answer: () => Response | Promise<Response> }> = [
      { label: 'a refused code (400 invalid_grant)', answer: refusal },
      { label: 'an unauthorized response (401)', answer: () => jsonResponse({ error: 'invalid_client' }, 401) },
      { label: 'a rate limit (429)', answer: () => jsonResponse({ error: 'slow_down' }, 429) },
      { label: 'a server fault (500)', answer: () => new Response('', { status: 500 }) },
      { label: 'no content (204)', answer: () => new Response(null, { status: 204 }) },
      {
        label: 'a dropped connection',
        answer: () => {
          throw new TypeError('Failed to fetch');
        },
      },
      {
        label: 'a body that is not JSON',
        answer: () => new Response('<html>nope</html>', { status: 200, headers: { 'Content-Type': 'text/html' } }),
      },
      {
        label: 'a body that claims JSON and is not',
        answer: () => new Response('{not json', { status: 200, headers: { 'Content-Type': 'application/json' } }),
      },
      { label: 'an empty object', answer: () => jsonResponse({}) },
      { label: 'a null body', answer: () => jsonResponse(null) },
      // The array case pins Phase 8's I6 by behaviour rather than by an inert guard: an array reaches the cast in
      // `usableTokens` and must still be refused.
      { label: 'an array body', answer: () => jsonResponse([tokensBody()]) },
      { label: 'no id_token', answer: () => jsonResponse(tokensBody({ id_token: undefined })) },
      { label: 'no access_token', answer: () => jsonResponse(tokensBody({ access_token: undefined })) },
      { label: 'an id_token that is a number', answer: () => jsonResponse(tokensBody({ id_token: 1234 })) },
      { label: 'an access_token that is a number', answer: () => jsonResponse(tokensBody({ access_token: 1234 })) },
      { label: 'an id_token that is null', answer: () => jsonResponse(tokensBody({ id_token: null })) },
      // The mirror of the row above. The guard treats every non-string alike today, so this passes without it -
      // it is here because the matrix is AC12's durable pin, and a refactor that special-cased `null` on one
      // token and not the other would otherwise find a gap to walk through.
      { label: 'an access_token that is null', answer: () => jsonResponse(tokensBody({ access_token: null })) },
      { label: 'an id_token that is empty', answer: () => jsonResponse(tokensBody({ id_token: '' })) },
      { label: 'an access_token that is empty', answer: () => jsonResponse(tokensBody({ access_token: '' })) },
      { label: 'an id_token that is an object', answer: () => jsonResponse(tokensBody({ id_token: { jwt: ID_TOKEN } })) },
    ];

    it.each(FAULTS)('rejects with the one message for $label', async ({ answer }) => {
      const expected = await theOneMessage();
      seedAllThree();
      stubFetch(answer);

      const rejection = await rejectionOf(exchangeAuthorizationCode(GRANT));

      expect(rejection.message).toBe(expected);
    });

    // The control for the whole matrix. Without it, every case above would also hold for a function that can only
    // ever reject, and the block would be asserting the absence of an exchange.
    it('resolves on the measured success response, so the matrix is not vacuous', async () => {
      seedAllThree();
      stubFetch(() => jsonResponse(tokensBody()));

      await expect(exchangeAuthorizationCode(GRANT)).resolves.toEqual({
        idToken: ID_TOKEN,
        accessToken: ACCESS_TOKEN,
      });
    });

    it("lets none of the authorization server's own words reach the SPA (AC20, L4)", async () => {
      // The premise, checked rather than assumed: the response really does carry the detail being searched for.
      expect(await refusal().clone().text()).toContain(SERVER_DETAIL);
      seedAllThree();
      stubFetch(refusal);

      const rejection = await rejectionOf(exchangeAuthorizationCode(GRANT));

      expect(rejection.message).not.toContain(SERVER_DETAIL);
      expect(rejection.message).not.toContain('invalid_grant');
      expect(rejection.message).not.toContain('400');
      expect(rejection.stack ?? '').not.toContain(SERVER_DETAIL);
      expect(storageDump()).not.toContain(SERVER_DETAIL);
    });

    it('writes the server detail to no console channel', async () => {
      const channels = (['log', 'info', 'warn', 'error', 'debug'] as const).map((method) =>
        vi.spyOn(console, method).mockImplementation(() => undefined),
      );
      seedAllThree();
      stubFetch(refusal);

      await rejectionOf(exchangeAuthorizationCode(GRANT));

      const logged = channels
        .flatMap((channel) => channel.mock.calls.flat())
        .map((argument) => String(argument))
        .join('\n');
      expect(logged).not.toContain(SERVER_DETAIL);
      expect(logged).not.toContain('invalid_grant');
    });
  });

  describe('what comes back (AC12, AC13)', () => {
    it('resolves to exactly the two tokens the SPA uses, and nothing else', async () => {
      seedAllThree();
      stubFetch(() => jsonResponse(tokensBody()));

      const tokens = await exchangeAuthorizationCode(GRANT);

      expect(Object.keys(tokens).sort()).toEqual(['accessToken', 'idToken']);
      expect(tokens.idToken).toBe(ID_TOKEN);
      expect(tokens.accessToken).toBe(ACCESS_TOKEN);
    });

    it.each([['expires_in'], ['scope'], ['token_type']])(
      'does not carry %s through, which no task needs yet',
      async (field) => {
        seedAllThree();
        stubFetch(() => jsonResponse(tokensBody()));

        const tokens = await exchangeAuthorizationCode(GRANT);

        // The premise: the field really was on the wire, so its absence here is a boundary and not a gap.
        expect(Object.keys(tokensBody())).toContain(field);
        expect(JSON.stringify(tokens)).not.toContain(field);
      },
    );

    it('carries no refresh token through even when the server sends one (AC13, L3)', async () => {
      const planted = 'REFRESH-TOKEN-THAT-MUST-NOT-SURVIVE';
      seedAllThree();
      stubFetch(() => jsonResponse(tokensBody({ refresh_token: planted })));

      const tokens = await exchangeAuthorizationCode(GRANT);

      // It resolves, because the response is otherwise usable - the point is that the extra field is dropped on
      // the floor rather than carried, stored or forwarded.
      expect(Object.keys(tokens).sort()).toEqual(['accessToken', 'idToken']);
      expect(JSON.stringify(tokens)).not.toContain(planted);
      expect(JSON.stringify(tokens)).not.toContain('refresh');
    });
  });

  describe('nothing is written, deleted or cleared (AC9, L16)', () => {
    it.each([
      ['a success', () => jsonResponse(tokensBody())],
      ['a refusal', refusal],
    ])('leaves all three stored items untouched after %s', async (_case, answer) => {
      seedAllThree();
      const setItem = vi.spyOn(Storage.prototype, 'setItem');
      const removeItem = vi.spyOn(Storage.prototype, 'removeItem');
      const clear = vi.spyOn(Storage.prototype, 'clear');
      stubFetch(answer);

      await exchangeAuthorizationCode(GRANT).catch(() => undefined);

      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state)).toBe(STATE);
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.nonce)).toBe(STORED_NONCE);
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.codeVerifier)).toBe(STORED_VERIFIER);
      // Deleting the three is task 23's, and a task that deleted them here would strand silent renewal.
      expect(setItem).not.toHaveBeenCalled();
      expect(removeItem).not.toHaveBeenCalled();
      expect(clear).not.toHaveBeenCalled();
    });
  });

  describe('through the screen (AC8, AC10, AC11, AC14)', () => {
    function storageDump(): string {
      const lines: string[] = [];
      for (const store of [globalThis.sessionStorage, globalThis.localStorage]) {
        for (let index = 0; index < store.length; index += 1) {
          const key = store.key(index) ?? '';
          lines.push(`${key}=${store.getItem(key) ?? ''}`);
        }
      }
      return lines.join('\n');
    }

    /** Lets one case mount several screens in turn, with module state reset between them. */
    function resetBetweenArrivals(): void {
      if (mounted) {
        const { root, container, router } = mounted;
        act(() => root.unmount());
        container.remove();
        router.dispose();
        mounted = undefined;
      }
      takeSignInNotice();
      vi.unstubAllGlobals();
      vi.resetModules();
    }

    const wrongNonceToken = () => jsonResponse(tokensBody({ id_token: tokenWith({ nonce: 'a-different-nonce' }) }));

    it.each([
      ['no state in the query', '/callback?code=abc'],
      ['a state that does not match the stored one', '/callback?code=abc&state=not-the-stored-state'],
      ['no code', `/callback?state=${STATE}`],
      ['an authorization error instead of a code', `/callback?error=access_denied&state=${STATE}`],
    ])('completes no exchange at all when the callback has %s (R8, AC8)', async (_case, url) => {
      seedAllThree();

      const rendered = await arriveAt(url, () => jsonResponse(tokensBody()));

      // R8 with a real transport behind it at last: before task 20 this assertion watched a seam that could not
      // have sent anything anyway.
      expect(rendered.fetchSpy).not.toHaveBeenCalled();
      expect(rendered.router.state.location.pathname).toBe('/app/sign-in');
    });

    // The control for the block above.
    it('completes the exchange when the callback is admitted, so those cases are not vacuous', async () => {
      seedAllThree();

      const rendered = await arriveAt(`/callback?code=abc&state=${STATE}`, () => jsonResponse(tokensBody()));

      expect(rendered.fetchSpy).toHaveBeenCalledTimes(1);
      expect(rendered.router.state.location.pathname).toBe('/app/callback');
      expect(rendered.container.textContent).toContain(en.callback.working);
    });

    it('still discards a token whose nonce is not the stored value, now over a real transport (AC14)', async () => {
      seedAllThree();

      const rendered = await arriveAt(`/callback?code=abc&state=${STATE}`, wrongNonceToken);

      // The exchange DID happen - this is the nonce check acting on a real response, not the state check acting
      // earlier.
      expect(rendered.fetchSpy).toHaveBeenCalledTimes(1);
      expect(rendered.router.state.location.pathname).toBe('/app/sign-in');
    });

    it('gives four different faults one identical outcome (AC11, L4)', async () => {
      const outcomes: ReturnType<typeof outcomeOf>[] = [];

      seedAllThree();
      outcomes.push(outcomeOf(await arriveAt(`/callback?code=abc&state=${STATE}`, refusal)));
      resetBetweenArrivals();

      seedAllThree();
      outcomes.push(outcomeOf(await arriveAt(`/callback?code=abc&state=${STATE}`, wrongNonceToken)));
      resetBetweenArrivals();

      seedAllThree();
      outcomes.push(outcomeOf(await arriveAt('/callback?code=abc', () => jsonResponse(tokensBody()))));
      resetBetweenArrivals();

      seedAllThree();
      outcomes.push(
        outcomeOf(await arriveAt('/callback?code=abc&state=not-the-stored-state', () => jsonResponse(tokensBody()))),
      );

      // A refused code, a substituted token, a missing state and a mismatched state: four faults, one sentence,
      // one path, one history action, one query.
      expect(outcomes[1]).toEqual(outcomes[0]);
      expect(outcomes[2]).toEqual(outcomes[0]);
      expect(outcomes[3]).toEqual(outcomes[0]);
      // The premise: these are discards, not four identical successes - and the neutral notice really did travel
      // from the callback screen to the sign-in screen, rather than all four landing on a blank sign-in page.
      expect(outcomes[0]?.pathname).toBe('/app/sign-in');
      expect(outcomes[0]?.action).toBe('REPLACE');
      expect(outcomes[0]?.text).toContain(en.signIn.discarded);
    });

    it('leaves no part of either token in storage, the DOM, the title or the URL (AC10)', async () => {
      const channels = (['log', 'info', 'warn', 'error', 'debug'] as const).map((method) =>
        vi.spyOn(console, method).mockImplementation(() => undefined),
      );
      seedAllThree();

      const rendered = await arriveAt(`/callback?code=abc&state=${STATE}`, () => jsonResponse(tokensBody()));

      const { pathname, search } = rendered.router.state.location;
      const visible = [storageDump(), rendered.container.innerHTML, document.title, pathname, search].join('\n');
      expect(visible).not.toContain(ACCESS_TOKEN);
      expect(visible).not.toContain(ID_TOKEN);
      // Segment by segment, because a token split across a header and a claim set is still a token.
      for (const segment of ID_TOKEN.split('.')) expect(visible).not.toContain(segment);
      const logged = channels
        .flatMap((channel) => channel.mock.calls.flat())
        .map((argument) => String(argument))
        .join('\n');
      expect(logged).not.toContain(ACCESS_TOKEN);
      expect(logged).not.toContain(ID_TOKEN);
    });

    it('fires exactly one request under StrictMode (the latch, over a real transport)', async () => {
      seedAllThree();

      // The first place the latch can be watched against a real network stub: T18's and T19's StrictMode cases
      // both replace `./codeExchange` wholesale, so a handler that fired twice would have been invisible to them.
      const rendered = await arriveAt(`/callback?code=abc&state=${STATE}`, () => jsonResponse(tokensBody()), {
        strict: true,
      });

      expect(rendered.fetchSpy).toHaveBeenCalledTimes(1);
      expect(rendered.router.state.location.pathname).toBe('/app/callback');
      expect(rendered.container.querySelectorAll('main')).toHaveLength(1);
    });

    it('survives the screen unmounting while the exchange is still in flight', async () => {
      seedAllThree();
      let release: (() => void) | undefined;
      const pending = new Promise<Response>((resolve) => {
        release = () => resolve(jsonResponse(tokensBody()));
      });

      const rendered = await arriveAt(`/callback?code=abc&state=${STATE}`, () => pending);
      expect(rendered.fetchSpy).toHaveBeenCalledTimes(1);
      const before = rendered.router.state.location.pathname;
      rendered.unmount();
      release?.();
      await turn();

      // A visitor who leaves the callback screen mid-exchange gets no navigation yanked out from under them, no
      // notice they did not earn, and no token anywhere.
      expect(rendered.router.state.location.pathname).toBe(before);
      expect(storageDump()).not.toContain(ACCESS_TOKEN);
    });

    it('survives the exchange REJECTING after the screen is gone', async () => {
      seedAllThree();
      let refuse: (() => void) | undefined;
      const pending = new Promise<Response>((resolve) => {
        refuse = () => resolve(refusal());
      });

      const rendered = await arriveAt(`/callback?code=abc&state=${STATE}`, () => pending);
      rendered.unmount();
      refuse?.();
      await turn();

      // This records what HAPPENS, not what would be tidy. The sibling case above releases a success after
      // unmount and nothing moves, because a successful exchange has nowhere to go until task 24. A refusal is
      // different: `discard` runs, and it navigates and sets module state even though the screen is gone, so a
      // visitor who clicked away mid-exchange is pulled to the sign-in start when the exchange later fails.
      //
      // T20 did not introduce this. Before task 20 the seam rejected unconditionally, so this was the ONLY path
      // through it. It is pinned here rather than changed because where a late failure should land a visitor is
      // task 24's question - it owns the post-login return target - and the frozen brief gives T20 no AC for it.
      // Reaching these assertions at all is the first half of the point: nothing throws, and no React or router
      // error escapes a disposed tree.
      expect(rendered.router.state.location.pathname).toBe('/app/sign-in');
      expect(storageDump()).not.toContain(ACCESS_TOKEN);
      expect(storageDump()).not.toContain(SERVER_DETAIL);
    });
  });


  describe('the token is held in memory (AC1, AC2, AC3, AC4, AC10)', () => {
    let holderSource: string | undefined;

    beforeEach(async () => {
      holderSource ??= await readFile(join(dirname(expect.getState().testPath as string), 'accessToken.ts'), 'utf8');
    });

    /**
     * The holder's source with its comments taken out.
     *
     * A whole-file scan reads a file's own explanation as the defect it warns about: the header comment in
     * `accessToken.ts` names `sessionStorage` in order to say the token never goes there, and `/sessionStorage/`
     * cannot tell the rule from its violation. T20 hit the same thing with `charset` and fixed it by scanning one
     * line; here the patterns are several, so the prose comes out instead and what is left is only code.
     */
    function holderCode(): string {
      return (holderSource ?? '').replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
    }

    /** Every key in both stores, so a case can prove a value reached neither. */
    function storageDump(): string {
      const lines: string[] = [];
      for (const store of [globalThis.sessionStorage, globalThis.localStorage]) {
        for (let index = 0; index < store.length; index += 1) {
          const key = store.key(index) ?? '';
          lines.push(`${key}=${store.getItem(key) ?? ''}`);
        }
      }
      return lines.join('\n');
    }

    // THE CONTROL FOR EVERY SCREEN-LAYER CASE IN THIS FILE. If `rendered.holder` were the wrong module instance -
    // the mistake T20's Phase 11 made with `signInNotice` - this case fails, because it requires the token to be
    // present rather than absent. Every "holds nothing" assertion below is only worth something because this one
    // passes.
    it('holds the access token after a callback that completes', async () => {
      seedAllThree();

      const rendered = await arriveAt(`/callback?code=abc&state=${STATE}`, () => jsonResponse(tokensBody()));

      expect(rendered.holder.accessToken.current()).toBe(ACCESS_TOKEN);
    });

    it.each([
      ['the first', 'ACCESS-TOKEN-ALPHA-4f91c2'],
      ['the second', 'ACCESS-TOKEN-BRAVO-7b3e88'],
    ])('holds %s token the server sent, so no constant can pass (AC2)', async (_which, token) => {
      seedAllThree();

      const rendered = await arriveAt(`/callback?code=abc&state=${STATE}`, () =>
        jsonResponse(tokensBody({ access_token: token })),
      );

      expect(rendered.holder.accessToken.current()).toBe(token);
    });

    it('returns the same value on repeated reads (AC4)', () => {
      retainAccessToken('ACCESS-TOKEN-ALPHA-4f91c2');

      expect(accessToken.current()).toBe('ACCESS-TOKEN-ALPHA-4f91c2');
      expect(accessToken.current()).toBe('ACCESS-TOKEN-ALPHA-4f91c2');
      expect(accessToken.current()).toBe(accessToken.current());
    });

    it('holds nothing before any exchange (AC3)', () => {
      expect(accessToken.current()).toBeNull();
    });

    it.each([
      ['no state in the query', '/callback?code=abc', () => jsonResponse(tokensBody())],
      ['a state that does not match', '/callback?code=abc&state=not-the-stored-state', () => jsonResponse(tokensBody())],
      ['no code', `/callback?state=${STATE}`, () => jsonResponse(tokensBody())],
      ['an authorization error', `/callback?error=access_denied&state=${STATE}`, () => jsonResponse(tokensBody())],
      ['a refused code', `/callback?code=abc&state=${STATE}`, refusal],
      ['a server fault', `/callback?code=abc&state=${STATE}`, () => new Response('', { status: 500 })],
      [
        'a dropped connection',
        `/callback?code=abc&state=${STATE}`,
        () => {
          throw new TypeError('Failed to fetch');
        },
      ],
      ['no usable tokens', `/callback?code=abc&state=${STATE}`, () => jsonResponse({})],
      [
        'a nonce that is not the stored one',
        `/callback?code=abc&state=${STATE}`,
        () => jsonResponse(tokensBody({ id_token: tokenWith({ nonce: 'a-different-nonce' }) })),
      ],
    ])('holds nothing after %s (AC3, AC10, R22)', async (_case, url, answer) => {
      seedAllThree();

      const rendered = await arriveAt(url, answer);

      expect(rendered.holder.accessToken.current()).toBeNull();
    });

    it('CLEARS a token from an earlier sign-in when a later callback is discarded (AC3, AC10, R22)', async () => {
      seedAllThree();
      const first = await arriveAt(`/callback?code=abc&state=${STATE}`, () => jsonResponse(tokensBody()));
      // The premise: this tab really is signed in before the second attempt.
      expect(first.holder.accessToken.current()).toBe(ACCESS_TOKEN);

      // A forged or stale callback arriving in that same tab. The holder is the SAME instance - no reset between
      // these two arrivals - so a clear that did not happen would be visible.
      first.unmount();
      takeSignInNotice();
      vi.unstubAllGlobals();
      const second = await arriveAt('/callback?code=abc&state=not-the-stored-state', () =>
        jsonResponse(tokensBody()),
      );

      // Failing closed: an attacker who can get a signed-in visitor to open a forged callback ends that tab's
      // session rather than leaving it half-authenticated (R22). An annoyance, deliberately chosen over a stale
      // credential, and pinned here so it is not later read as a regression.
      expect(second.holder.accessToken.current()).toBeNull();
      expect(second.router.state.location.pathname).toBe('/app/sign-in');
    });

    it('holds the token only AFTER the nonce check, never before it (AC14)', () => {
      // Structural, because it has to be: a token retained before the check and discarded after would leave
      // `current()` null at the end of the case, exactly as the correct order does. The two are distinguishable
      // only in the source.
      const screen = callbackSourceFor();
      const nonceAt = screen.indexOf('idTokenNonceMatchesStoredValue(response.idToken)');
      const retainAt = screen.indexOf('retainAccessToken(response.accessToken)');
      expect(nonceAt).toBeGreaterThan(0);
      expect(retainAt).toBeGreaterThan(nonceAt);
      // And the early return between them, which is what stops the two being reordered by accident.
      expect(screen.slice(nonceAt, retainAt)).toContain('return;');
    });

    describe('the holder satisfies what tasks 5 and 6 already consume (AC5, AC6, AC7, AC16)', () => {
      it('puts the held token on the wire as a bearer, through the REAL boundary (AC5)', async () => {
        retainAccessToken('ACCESS-TOKEN-ALPHA-4f91c2');
        const spy = stubFetch(() => jsonResponse({}));

        // The real createSessionBoundary and the real generated client. A type assertion here would only re-prove
        // what the compiler already checked; this proves the token actually arrives.
        await createAuthApi(createSessionBoundary(accessToken, () => undefined)).GET('/accounts/me');

        expect(sentRequest(spy).headers.get('Authorization')).toBe('Bearer ACCESS-TOKEN-ALPHA-4f91c2');
      });

      it('sends no bearer at all when nothing is held (AC3, AC5)', async () => {
        const spy = stubFetch(() => jsonResponse({}));

        await createAuthApi(createSessionBoundary(accessToken, () => undefined)).GET('/accounts/me');

        expect(sentRequest(spy).headers.get('Authorization')).toBeNull();
      });

      it('is accepted by the REAL cross-tab renewal, and adopt reaches it (AC6, AC7)', () => {
        retainAccessToken('ACCESS-TOKEN-ALPHA-4f91c2');

        const shared = shareRenewalAcrossTabs(accessToken);
        try {
          expect(shared.current()).toBe('ACCESS-TOKEN-ALPHA-4f91c2');
          accessToken.adopt('ACCESS-TOKEN-BRAVO-7b3e88');
          expect(shared.current()).toBe('ACCESS-TOKEN-BRAVO-7b3e88');
        } finally {
          shared.close();
        }
      });

      it('adopts with nothing held (AC7)', () => {
        expect(accessToken.current()).toBeNull();

        accessToken.adopt('ACCESS-TOKEN-BRAVO-7b3e88');

        expect(accessToken.current()).toBe('ACCESS-TOKEN-BRAVO-7b3e88');
      });

      it.each([
        ['adopt runs last', 'ACCESS-TOKEN-BRAVO-7b3e88'],
        ['retention runs last', 'ACCESS-TOKEN-ALPHA-4f91c2'],
      ])('lets the last writer win when %s (AC7)', (order, expected) => {
        if (order === 'adopt runs last') {
          retainAccessToken('ACCESS-TOKEN-ALPHA-4f91c2');
          accessToken.adopt('ACCESS-TOKEN-BRAVO-7b3e88');
        } else {
          accessToken.adopt('ACCESS-TOKEN-BRAVO-7b3e88');
          retainAccessToken('ACCESS-TOKEN-ALPHA-4f91c2');
        }

        expect(accessToken.current()).toBe(expected);
      });

      it('rejects renewal with one fixed message carrying nothing (AC16)', async () => {
        retainAccessToken('ACCESS-TOKEN-ALPHA-4f91c2');

        const rejection = await rejectionOf(accessToken.renew());

        expect(rejection.message).toBe('Silent re-authentication is not built; this tab cannot renew its session.');
        expect(rejection.message).not.toContain('ACCESS-TOKEN');
        expect(rejection.message).not.toMatch(/\d{3}/);
        // The seam does not pretend to work, and does not destroy what is held either.
        expect(accessToken.current()).toBe('ACCESS-TOKEN-ALPHA-4f91c2');
      });

      it('makes a 401 a defined outcome through the real boundary: one failure hook, original 401 (AC16)', async () => {
        retainAccessToken('ACCESS-TOKEN-ALPHA-4f91c2');
        stubFetch(() => new Response('{}', { status: 401, headers: { 'Content-Type': 'application/json' } }));
        const onRenewalFailed = vi.fn();

        const { response } = await createAuthApi(
          createSessionBoundary(accessToken, onRenewalFailed),
        ).GET('/accounts/me');
        await turn();

        // Not a crash and not a retry loop: the caller keeps its own 401 and the boundary reports the failure
        // exactly once, which is the behaviour task 33 will route to clean re-auth.
        expect(response.status).toBe(401);
        expect(onRenewalFailed).toHaveBeenCalledTimes(1);
        expect((onRenewalFailed.mock.calls[0]?.[0] as Error).message).toBe(
          'Silent re-authentication is not built; this tab cannot renew its session.',
        );
      });
    });

    describe('one copy, and nowhere else (AC8, AC9 as T21-D8, AC11, AC13, AC17)', () => {
      it('puts no part of the token in storage, the DOM, the title or the URL (AC8)', async () => {
        seedAllThree();

        const rendered = await arriveAt(`/callback?code=abc&state=${STATE}`, () => jsonResponse(tokensBody()));

        // The premise first: it IS held, so this is a leak sweep rather than a sweep of nothing.
        expect(rendered.holder.accessToken.current()).toBe(ACCESS_TOKEN);
        const { pathname, search } = rendered.router.state.location;
        const visible = [
          storageDump(),
          rendered.container.innerHTML,
          document.title,
          document.cookie,
          pathname,
          search,
        ].join('\n');
        expect(visible).not.toContain(ACCESS_TOKEN);
        for (const segment of ACCESS_TOKEN.split('-')) {
          if (segment.length >= 6) expect(visible).not.toContain(segment);
        }
      });

      it('leaves the three L16 items exactly as task 15 wrote them (AC11)', async () => {
        seedAllThree();

        const rendered = await arriveAt(`/callback?code=abc&state=${STATE}`, () => jsonResponse(tokensBody()));

        expect(rendered.holder.accessToken.current()).toBe(ACCESS_TOKEN);
        expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state)).toBe(STATE);
        expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.nonce)).toBe(STORED_NONCE);
        expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.codeVerifier)).toBe(STORED_VERIFIER);
      });

      it('writes the token to no console channel (AC8)', async () => {
        const channels = (['log', 'info', 'warn', 'error', 'debug'] as const).map((method) =>
          vi.spyOn(console, method).mockImplementation(() => undefined),
        );
        seedAllThree();

        const rendered = await arriveAt(`/callback?code=abc&state=${STATE}`, () => jsonResponse(tokensBody()));

        expect(rendered.holder.accessToken.current()).toBe(ACCESS_TOKEN);
        const logged = channels
          .flatMap((channel) => channel.mock.calls.flat())
          .map((argument) => String(argument))
          .join('\n');
        expect(logged).not.toContain(ACCESS_TOKEN);
      });

      it('hangs the token off no global property (AC9)', () => {
        retainAccessToken('ACCESS-TOKEN-ALPHA-4f91c2');

        const named = Object.getOwnPropertyNames(globalThis).filter((key) => {
          try {
            return (globalThis as unknown as Record<string, unknown>)[key] === 'ACCESS-TOKEN-ALPHA-4f91c2';
          } catch {
            return false;
          }
        });
        expect(named).toEqual([]);
      });

      it('keeps exactly one mutable binding, and it is the token (AC9)', () => {
        const source = holderCode();
        const mutable = source.split('\n').filter((line) => /^(?:let|var) /.test(line));
        expect(mutable).toEqual(['let held: string | null = null;']);
        // One assignment of a non-null token, one clear - `adopt` delegates rather than writing again.
        expect(source.split('held = ').length - 1).toBe(2);
        expect(source).toContain('retainAccessToken(token);');
      });

      it.each([
        { label: 'a storage API', pattern: /sessionStorage|localStorage|indexedDB|openDatabase|document\.cookie/, violation: "sessionStorage.setItem('t', held ?? '');" },
        { label: 'a console call', pattern: /console\./, violation: 'console.warn(held);' },
        { label: 'an environment key', pattern: /import\.meta\.env/, violation: 'const k = import.meta.env.VITE_TOKEN;' },
        { label: 'an absolute address', pattern: /https?:\/\//, violation: "void fetch('https://example.com/' + held);" },
        { label: 'a token in a thrown message', pattern: /new Error\([^)]*\$\{/, violation: 'throw new Error(`token ${held}`);' },
      ])('names no $label (AC8, AC13)', ({ pattern, violation }) => {
        const code = holderCode();
        // The stripping proves itself too: comments out, the holder's own body is still there to be scanned, so
        // a pass below is the absence of the pattern rather than the absence of a file.
        expect(code).toContain('let held: string | null = null;');
        expect(code).toContain('export function retainAccessToken(token: string): void {');
        expect(code).not.toMatch(pattern);
        // The scan proves itself: a pattern matching nothing would pass the assertion above against any file.
        expect(violation).toMatch(pattern);
      });

      it('leaves the cross-tab interface untouched, rather than widening it (AC17)', async () => {
        const crossTab = await readFile(
          join(dirname(dirname(expect.getState().testPath as string)), 'api/crossTabRenewal.ts'),
          'utf8',
        );

        // T21-D3 put the clear in the holder precisely so this line did not have to change. Widening `adopt` to
        // `string | null` would have edited code tasks 5 and 6 are built on.
        expect(crossTab).toContain('export interface LocalSession extends SessionTokens {\n  adopt(token: string): void;\n}');
        expect(crossTab).not.toContain('adopt(token: string | null)');
      });
    });
  });

  describe('the source itself (AC10, AC13, AC16)', () => {
    const FORBIDDEN: ReadonlyArray<{ label: string; pattern: RegExp; violation: string }> = [
      {
        label: 'an absolute address',
        pattern: /https?:\/\//,
        violation: "await fetch('https://auth.example.com/oauth2/token');",
      },
      {
        label: 'an environment key',
        pattern: /import\.meta\.env/,
        violation: 'const base = import.meta.env.VITE_AUTH_ORIGIN;',
      },
      {
        label: 'a hand-spelled storage key',
        pattern: /themistra\.auth\./,
        violation: "sessionStorage.getItem('themistra.auth.code_verifier');",
      },
      {
        label: 'a refresh token by either spelling',
        pattern: /refresh_token|refreshToken/,
        violation: 'const renewal = payload.refresh_token;',
      },
      { label: 'a console call', pattern: /console\./, violation: 'console.warn(payload);' },
      { label: 'module-level mutable state', pattern: /^(?:let|var) /m, violation: 'let lastTokens = null;' },
    ];

    it.each(FORBIDDEN)('contains no $label', ({ pattern, violation }) => {
      expect(exchangeSource).not.toMatch(pattern);
      // The scan proves itself: a pattern that matched nothing would pass the assertion above against any file.
      expect(violation).toMatch(pattern);
    });

    it('names the content type exactly once, as a constant, with no charset parameter', () => {
      const source = exchangeSource ?? '';
      // Scanned line by line rather than across the whole file: the comment ABOVE the constant explains why a
      // charset parameter would break the comparison, and a whole-file scan for `charset` reads that explanation
      // as the defect it warns about. The constant's own line is the thing that has to be exact.
      const declared = source.split('\n').filter((line) => line.includes("'application/x-www-form-urlencoded'"));
      expect(declared).toHaveLength(1);
      expect(declared[0]).toBe("const FORM_CONTENT_TYPE = 'application/x-www-form-urlencoded';");
      // And the header is set from the constant, not from a second literal that could drift from it.
      expect(source).toContain("headers: { 'Content-Type': FORM_CONTENT_TYPE }");
    });

    it('derives the redirect URI from the one exported helper, not a second spelling (AC5, T20-D7)', () => {
      const source = exchangeSource ?? '';
      expect(source).toContain('redirect_uri: redirectUri()');
      // This one has to be structural. A second derivation of the same value produces the same bytes until the
      // day one of them changes, so no assertion about the request can tell the two apart - and the day it can
      // tell them apart is the day sign-in is already broken against an exact-match registration.
      expect(source).not.toContain('/callback');
      expect(source).not.toContain('APP_BASENAME');
    });

    it('goes through the generated client and hand-writes no request', () => {
      const source = exchangeSource ?? '';
      expect(source).toContain('createOidcClient');
      // L6's whole point. The lint rule catches `fetch` and `XMLHttpRequest`; this catches the intent.
      expect(source).not.toMatch(/\bfetch\s*\(/);
      expect(source).not.toContain('XMLHttpRequest');
    });

    it('puts no middleware and no session boundary on the OIDC client (L18)', () => {
      const source = exchangeSource ?? '';
      // The boundary owns single-flight 401 renewal, and renewal IS silent re-authentication ending in a token
      // exchange. A renewal hook on THIS client would make a failed exchange trigger a renewal that performs
      // another exchange, and so on. Nothing about the loop is visible from a passing request, so the ban has to
      // be structural - and task 21, which wires the boundary, is the task most likely to attach it to every
      // client it finds.
      expect(source).not.toMatch(/\.use\(/);
      expect(source).not.toContain('middleware');
      expect(source).not.toContain('sessionBoundary');
      // And the auth-service client is not a substitute for the OIDC one: it is generated from a contract that
      // does not describe this endpoint.
      expect(source).not.toContain('createAuthClient');
      expect(source).not.toContain('createAuthApi');
    });
  });
});
