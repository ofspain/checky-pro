/// <reference types="node" />
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, type Mock, vi } from 'vitest';
import { en } from '../i18n/catalogue';
import { createI18n } from '../i18n/i18n';
import { AUTHORIZE_STORAGE_KEYS, buildAuthorizeRequest, CLIENT_ID, redirectUri } from './authorizeRequest';
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

type Rendered = {
  container: HTMLElement;
  router: ReturnType<typeof createMemoryRouter>;
  fetchSpy: Mock;
  unmount: () => void;
};

/** Drives the real route table over the real exchange, with only the network stubbed. */
async function arriveAt(url: string, answer: () => Response | Promise<Response>): Promise<Rendered> {
  const fetchSpy = stubFetch(answer);
  const routes = await import('../app/routes');
  const router = createMemoryRouter(routes.appRoutes, {
    basename: routes.APP_BASENAME,
    initialEntries: [`${routes.APP_BASENAME}${url}`],
  });
  const container = document.createElement('div');
  document.body.append(container);
  const root = createRoot(container);
  await act(async () => {
    root.render(
      <I18nextProvider i18n={createI18n()}>
        <RouterProvider router={router} />
      </I18nextProvider>,
    );
  });
  await turn();
  mounted = { root, container, router };
  return {
    container,
    router,
    fetchSpy,
    unmount: () => {
      act(() => root.unmount());
      container.remove();
      mounted = undefined;
    },
  };
}

/** Everything a visitor could tell one failure from another by. L4 requires these equal across causes. */
function outcomeOf({ container, router }: Rendered) {
  return {
    text: (container.textContent ?? '').trim(),
    pathname: router.state.location.pathname,
    search: router.state.location.search,
    action: router.state.historyAction,
  };
}

describe('callbackValidatesNonceAndKeepsAccessTokenInMemory', () => {
  beforeEach(async () => {
    globalThis.sessionStorage.clear();
    globalThis.localStorage.clear();
    exchangeSource ??= await readFile(join(dirname(expect.getState().testPath as string), 'codeExchange.ts'), 'utf8');
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

    it('rejects and makes no request when reading storage THROWS', async () => {
      seedAllThree();
      const spy = stubFetch(() => jsonResponse(tokensBody()));
      // Touching sessionStorage raises where site data is blocked; it does not merely answer null. There is no
      // error boundary anywhere in this application, so an escaping throw here would blank the page at the end of
      // a sign-in, after the visitor had already authenticated.
      vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
        throw new DOMException('denied', 'SecurityError');
      });

      await expect(exchangeAuthorizationCode(GRANT)).rejects.toThrow();

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

    it('carries no client credential of any kind (AC6)', async () => {
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

    /**
     * The rejection from a fault that never reaches the network, used as the shape every other fault must equal.
     *
     * Taken from the code rather than written down here on purpose: hard-coding the message would pin the words
     * instead of the property, and the property is that there is only ONE message. A rejection that carried any
     * server detail at all would differ from this one and fail, whatever the wording.
     */
    async function oneMessage(): Promise<string> {
      globalThis.sessionStorage.clear();
      globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.state, STATE);
      stubFetch(() => jsonResponse(tokensBody()));
      const rejection = await rejectionOf(exchangeAuthorizationCode(GRANT));
      vi.unstubAllGlobals();
      globalThis.sessionStorage.clear();
      return rejection.message;
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
      { label: 'an id_token that is empty', answer: () => jsonResponse(tokensBody({ id_token: '' })) },
      { label: 'an access_token that is empty', answer: () => jsonResponse(tokensBody({ access_token: '' })) },
      { label: 'an id_token that is an object', answer: () => jsonResponse(tokensBody({ id_token: { jwt: ID_TOKEN } })) },
    ];

    it.each(FAULTS)('rejects with the one message for $label', async ({ answer }) => {
      const expected = await oneMessage();
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
      // The premise: these are discards, not four identical successes.
      expect(outcomes[0]?.pathname).toBe('/app/sign-in');
      expect(outcomes[0]?.action).toBe('REPLACE');
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
      expect(takeSignInNotice()).toBeNull();
      expect(storageDump()).not.toContain(ACCESS_TOKEN);
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

    it('goes through the generated client and hand-writes no request', () => {
      const source = exchangeSource ?? '';
      expect(source).toContain('createOidcClient');
      // L6's whole point. The lint rule catches `fetch` and `XMLHttpRequest`; this catches the intent.
      expect(source).not.toMatch(/\bfetch\s*\(/);
      expect(source).not.toContain('XMLHttpRequest');
    });
  });
});
