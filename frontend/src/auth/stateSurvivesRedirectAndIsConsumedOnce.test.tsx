/// <reference types="node" />
/**
 * `stateSurvivesRedirectAndIsConsumedOnce` (package.md §8) — R8, L16.
 *
 * Two claims, and the file is built around the fact that they pull in opposite directions. The three items
 * `state`, `nonce` and `code_verifier` must SURVIVE long enough to be used - they are written before a full-page
 * redirect to the authorization server and read back after it - and then be CONSUMED, because L16 calls them
 * "single-use and deleted at callback" and a `state` that is still readable is a `state` that can be matched
 * twice.
 *
 * The deletion therefore has exactly one correct moment: after every consumer has read, and on the one path that
 * did the reading. A line too early empties the verifier the exchange needs (`codeExchange.ts:59` reads it
 * INSIDE the request) or the nonce `idTokenNonce.ts` needs; a deletion on a failure path turns a public route
 * into a way to end a stranger's pending sign-in. Both mistakes are invisible in an end state - the store is
 * empty either way - so most of this file is about making the ORDER and the PATH observable.
 *
 * WHAT "SURVIVES THE REDIRECT" CAN MEAN HERE (T23-D5). jsdom performs no document navigation, so no test in this
 * repository can watch a real round trip through the authorization server. What is measured instead is the
 * mechanism that makes the survival necessary: `state` and `nonce` travel in the authorize URL and come back
 * from the server, while the verifier travels NOWHERE and can only come from the store - so an implementation
 * that kept the three in a module variable would fail the exchange after a real redirect, and the three being in
 * `sessionStorage` is what makes the flow work at all. The second case rebuilds the module graph and reads the
 * three back through a fresh instance, which is the nearest observable thing to a reloaded document. It is said
 * here rather than implied: a real-browser end-to-end check is owned by no task in this package.
 *
 * SINGLE-USE IS NARROWER THAN THE PHRASE (T23-D2), and deliberately. Only the path that CONSUMED the three
 * removes them. A callback whose exchange failed, or whose state did not match, leaves all three in place, so the
 * visitor can try again - and so that anyone who can get a signed-in visitor to open `/app/callback?state=x`
 * cannot end that tab's pending sign-in. The replay that single-use really defends against is a callback that
 * SUCCEEDED, and the case below shows that one being refused with no network call at all. The authorization
 * code is single-use at the token endpoint as well (T20 measured `400 invalid_grant` on a replay); that is the
 * outer defence, and this file is the SPA no longer depending on it.
 *
 * VACUITY IS THE STANDING RISK (T23-D13). "The three are gone" is satisfied by a flow that never wrote them, so
 * every consumed-path and replay case asserts all three are PRESENT immediately before the deletion point, and
 * `startSignIn` refuses to return a triple it did not find in the store.
 */
import { readdir, readFile } from 'node:fs/promises';
import { dirname, join, relative } from 'node:path';
import { act, StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, type Mock, vi } from 'vitest';
import { en } from '../i18n/catalogue';
import { createI18n } from '../i18n/i18n';
import { AUTHORIZE_STORAGE_KEYS, buildAuthorizeRequest, consumeAuthorizeState } from './authorizeRequest';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/**
 * The static imports above are the RIGHT instance for what this file asks of them, which is not true of every
 * file in this package. `buildAuthorizeRequest` and `consumeAuthorizeState` reach `globalThis.sessionStorage`,
 * one store shared by every module instance, so `vi.resetModules()` between cases cannot make either of them
 * read a different store. Where instance identity DOES matter - the token the screen held - `arriveAt` returns
 * the holder the screen itself used, for the reason written there.
 */

const ACCESS_TOKEN = 'ACCESS-TOKEN-5c71b8a3f204';
/** A second token, so a success asserted twice cannot be a constant that happened to match (T22-D13). */
const SECOND_ACCESS_TOKEN = 'ACCESS-TOKEN-0e92d4176bca';
/** The route a discard lands on, basename included, as the router reports it. */
const SIGN_IN_ROUTE = '/app/sign-in';
/** Where a consumed callback leaves the visitor today: task 24 owns moving them on (T23-D16). */
const CALLBACK_ROUTE = '/app/callback';

type Mounted = { root: Root; container: HTMLElement };
let mounted: Mounted | undefined;

async function turn(): Promise<void> {
  await act(async () => {
    await new Promise((resolve) => {
      setTimeout(resolve, 0);
    });
  });
}

/** Mints an ID token. Encoded through TextEncoder rather than `btoa(JSON.stringify())`, which throws above U+00FF. */
function tokenWith(claims: unknown): string {
  const segment = (value: unknown) => {
    const bytes = new TextEncoder().encode(JSON.stringify(value));
    let binary = '';
    for (const byte of bytes) binary += String.fromCharCode(byte);
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  };
  return `${segment({ alg: 'RS256', kid: 'test' })}.${segment(claims)}.c2lnbmF0dXJl`;
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

/** What a replayed or mismatched code really gets back from the token endpoint: HTTP 400, an OAuth2 error object. */
function refusal(): Response {
  return jsonResponse(
    { error: 'invalid_grant', error_description: 'the code verifier did not match the challenge' },
    400,
  );
}

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

/**
 * The form fields the exchange really sent, parsed back out of the serialized body.
 *
 * This is how AC6 is proven behaviourally rather than by shape: a deletion moved one line earlier leaves
 * `code_verifier` empty here, and no end-state assertion anywhere could tell the difference.
 */
async function sentFields(spy: Mock): Promise<Record<string, string>> {
  expect(spy, 'exactly one request reached the network').toHaveBeenCalledTimes(1);
  const request = spy.mock.calls[0]![0] as Request;
  return Object.fromEntries(new URLSearchParams(await request.clone().text()));
}

/* ─────────────────────────── the instruments ─────────────────────────── */

type StorageWatch = { setItem: Mock; removeItem: Mock; clear: Mock };

/**
 * Spies the STORAGE PROTOTYPE, and must be installed before the flow runs.
 *
 * Measured in T22: a watch installed after `buildAuthorizeRequest` reported zero writes for a flow that had just
 * made three, which is how an "exactly three" assertion silently becomes "exactly zero". The prototype rather
 * than a store object, so a call through `globalThis['sessionStorage']` or a bound alias is still seen.
 */
function watchStorage(): StorageWatch {
  return {
    setItem: vi.spyOn(Storage.prototype, 'setItem') as unknown as Mock,
    removeItem: vi.spyOn(Storage.prototype, 'removeItem') as unknown as Mock,
    clear: vi.spyOn(Storage.prototype, 'clear') as unknown as Mock,
  };
}

/** The keys a watch saw removed, in the order they were removed. */
function removedKeys(watch: StorageWatch): string[] {
  return (watch.removeItem.mock.calls as Array<[string]>).map(([key]) => key);
}

/** The three keys, in the order the authorize step writes them - the order AC5 and AC6 are stated in. */
const THREE_KEYS = [
  AUTHORIZE_STORAGE_KEYS.state,
  AUTHORIZE_STORAGE_KEYS.nonce,
  AUTHORIZE_STORAGE_KEYS.codeVerifier,
] as const;

/** Every key the store holds, sorted. A whole-store read, so a fourth item cannot hide behind three named gets. */
function storedKeys(): string[] {
  return Object.keys(globalThis.sessionStorage).sort();
}

/** Which of the three are present, by name, so a failure message says WHICH one went missing. */
function presentItems(): string[] {
  return THREE_KEYS.filter((key) => globalThis.sessionStorage.getItem(key) !== null);
}

/**
 * One ordered log of every read and removal, which is what makes AC6 behavioural rather than structural.
 *
 * Installed through the prototype like the watch, and it calls through to the real method - an ordering case over
 * a store that no longer works would prove nothing. Only the three keys are logged: the notice module and the
 * router touch storage too, and their traffic is not what the ordering claim is about.
 */
function logStorageOrder(): string[] {
  const log: string[] = [];
  const realGet = Storage.prototype.getItem;
  const realRemove = Storage.prototype.removeItem;
  vi.spyOn(Storage.prototype, 'getItem').mockImplementation(function (this: Storage, key: string) {
    if ((THREE_KEYS as readonly string[]).includes(key)) log.push(`get ${key}`);
    return realGet.call(this, key);
  });
  vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(function (this: Storage, key: string) {
    if ((THREE_KEYS as readonly string[]).includes(key)) log.push(`remove ${key}`);
    realRemove.call(this, key);
  });
  return log;
}

/** A store that refuses to remove, which is the realistic failure: site data blocked, some private modes. */
function denyRemoval(only?: string): Mock {
  const realRemove = Storage.prototype.removeItem;
  return vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(function (this: Storage, key: string) {
    if (only === undefined || key === only) throw new DOMException('the store is not available', 'SecurityError');
    realRemove.call(this, key);
  }) as unknown as Mock;
}

/* ─────────────────────────── the flow ─────────────────────────── */

type Arrival = {
  container: HTMLElement;
  router: ReturnType<typeof createMemoryRouter>;
  fetchSpy: Mock;
  /**
   * The holder THE SCREEN USED. `arriveAt` imports the route graph dynamically and `vi.resetModules()` runs
   * between cases, so the screen gets a fresh `accessToken.ts`; asserting on a static import would read an
   * untouched module and pass whatever production did. This import happens after the routes import, in the same
   * registry, so it is the instance the screen wrote to - and the first case below requires a token to be THERE.
   */
  holder: typeof import('./accessToken');
  unmount: () => void;
};

async function arriveAt(
  url: string,
  answer: () => Response | Promise<Response>,
  options: { strict?: boolean } = {},
): Promise<Arrival> {
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
  mounted = { root, container };
  return {
    container,
    router,
    fetchSpy,
    holder,
    unmount: () => {
      act(() => root.unmount());
      container.remove();
      router.dispose();
      mounted = undefined;
    },
  };
}

type Triple = { state: string; nonce: string; verifier: string };

/**
 * The real authorize step - the thing that writes the three - and it refuses to return a triple it cannot read
 * back. T23-D13 in one function: every "they are gone" assertion in this file stands on this one having found
 * them there first.
 */
async function startSignIn(): Promise<Triple> {
  const request = await buildAuthorizeRequest();
  const read = (key: string) => {
    const value = globalThis.sessionStorage.getItem(key);
    expect(value, `the authorize step wrote ${key}`).toBeTruthy();
    return value!;
  };
  const triple = {
    state: read(AUTHORIZE_STORAGE_KEYS.state),
    nonce: read(AUTHORIZE_STORAGE_KEYS.nonce),
    verifier: read(AUTHORIZE_STORAGE_KEYS.codeVerifier),
  };
  expect(triple.state, 'the authorize URL carries the state it stored').toBe(request.state);
  expect(triple.nonce, 'the authorize URL carries the nonce it stored').toBe(request.nonce);
  return triple;
}

/** The response a successful exchange returns, built from the nonce the authorize step really generated. */
function tokensFor(nonce: string, overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    access_token: ACCESS_TOKEN,
    id_token: tokenWith({ nonce, sub: 'the-subject', iss: 'http://localhost:5173' }),
    token_type: 'Bearer',
    expires_in: 299,
    scope: 'openid profile email',
    ...overrides,
  };
}

/** Everything a visitor could tell one outcome from another by. L4 requires these equal across causes. */
function outcomeOf({ container, router }: Arrival) {
  return {
    text: (container.textContent ?? '').trim(),
    pathname: router.state.location.pathname,
    search: router.state.location.search,
  };
}

let authorizeSource: string | undefined;
let callbackSource: string | undefined;

/** A production file's code with its comments removed, because a file explains the rules it obeys (T22's trap). */
function strip(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
}

/**
 * Every production module under `src/`, comments stripped. Refuses an empty tree: a scan over nothing satisfies
 * every prohibition at once, which is the one way a source scan lies.
 */
async function productionSources(): Promise<Array<{ path: string; code: string }>> {
  const srcRoot = dirname(dirname(expect.getState().testPath as string));
  async function walk(dir: string): Promise<string[]> {
    const found: string[] = [];
    for (const entry of await readdir(dir, { withFileTypes: true })) {
      const full = join(dir, entry.name);
      if (entry.isDirectory()) found.push(...(await walk(full)));
      else if (/\.tsx?$/.test(entry.name) && !/\.test\.tsx?$/.test(entry.name)) found.push(full);
    }
    return found;
  }
  const files = (await walk(srcRoot)).sort();
  if (files.length === 0) throw new Error('scan refused: no production sources found');
  const targets: Array<{ path: string; code: string }> = [];
  for (const file of files) {
    targets.push({
      path: relative(srcRoot, file).split(/[\\/]/).join('/'),
      code: strip(await readFile(file, 'utf8')),
    });
  }
  return targets;
}

describe('stateSurvivesRedirectAndIsConsumedOnce', () => {
  beforeEach(async () => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.resetModules();
    globalThis.sessionStorage.clear();
    globalThis.localStorage.clear();
    const here = dirname(expect.getState().testPath as string);
    authorizeSource ??= await readFile(join(here, 'authorizeRequest.ts'), 'utf8');
    callbackSource ??= await readFile(join(here, 'CallbackScreen.tsx'), 'utf8');
  });

  afterEach(() => {
    if (mounted) {
      act(() => mounted!.root.unmount());
      mounted.container.remove();
      mounted = undefined;
    }
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  describe('the three survive the journey (AC1, T23-D5)', () => {
    /**
     * The mechanism, and the reason the three cannot live in a module variable. `state` and `nonce` are sent to
     * the authorization server and come back in the callback; the VERIFIER is sent nowhere - only its challenge
     * is - so after a real full-page redirect the store is the only place it could come from.
     */
    it('sends state and nonce to the server and keeps the verifier out of the URL', async () => {
      const request = await buildAuthorizeRequest();

      const parameters = new URL(request.url).searchParams;
      expect(parameters.get('state')).toBe(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state));
      expect(parameters.get('nonce')).toBe(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.nonce));
      const verifier = globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.codeVerifier)!;
      expect(verifier.length).toBeGreaterThan(42);
      expect(request.url, 'the verifier travels in no URL - only its challenge does').not.toContain(verifier);
      expect(parameters.get('code_challenge')).toBeTruthy();
      expect(parameters.get('code_challenge')).not.toBe(verifier);
    });

    /**
     * The nearest observable thing to a reloaded document that this environment allows (T23-D5). jsdom performs
     * no navigation, so the module graph is torn down instead: a fresh `authorizeRequest.ts` - a different object
     * identity, which the control asserts - reads the same three values back, because the store is not part of
     * the graph. What this does NOT prove is a real browser round trip; no task in this package owns that.
     */
    it('reads all three back through a module graph that no longer shares an instance', async () => {
      const started = await startSignIn();

      vi.resetModules();
      const fresh = await import('./authorizeRequest');

      expect(fresh.AUTHORIZE_STORAGE_KEYS, 'the control: a genuinely new instance').not.toBe(
        AUTHORIZE_STORAGE_KEYS,
      );
      expect(fresh.AUTHORIZE_STORAGE_KEYS).toEqual(AUTHORIZE_STORAGE_KEYS);
      expect(globalThis.sessionStorage.getItem(fresh.AUTHORIZE_STORAGE_KEYS.state)).toBe(started.state);
      expect(globalThis.sessionStorage.getItem(fresh.AUTHORIZE_STORAGE_KEYS.nonce)).toBe(started.nonce);
      expect(globalThis.sessionStorage.getItem(fresh.AUTHORIZE_STORAGE_KEYS.codeVerifier)).toBe(started.verifier);
    });

    it('puts the three in session storage and nothing in local storage (AC9, L16)', async () => {
      const started = await startSignIn();

      expect(storedKeys()).toEqual([...THREE_KEYS].sort());
      expect(Object.keys(globalThis.localStorage)).toEqual([]);
      expect(started.verifier).not.toBe(started.nonce);
    });
  });

  describe('a consumed callback takes all three with it (AC2, AC5)', () => {
    it('removes all three once the token is held', async () => {
      const watch = watchStorage();
      const started = await startSignIn();
      // T23-D13's presence control. Without it every assertion below holds for a flow that wrote nothing.
      expect(presentItems()).toEqual([...THREE_KEYS]);

      const arrival = await arriveAt(`/callback?code=THE-CODE&state=${started.state}`, () =>
        jsonResponse(tokensFor(started.nonce)),
      );

      // The second control: the callback really was consumed. A discarded callback would also end with the token
      // absent, and then "the three are gone" would be measuring the wrong path entirely.
      expect(arrival.holder.accessToken.current()).toBe(ACCESS_TOKEN);
      expect(arrival.router.state.location.pathname).toBe(CALLBACK_ROUTE);
      expect(presentItems()).toEqual([]);
      expect(storedKeys()).toEqual([]);
      expect(watch.clear, 'the store is never cleared - other keys are not this task to delete').not.toHaveBeenCalled();
    });

    it('removes exactly the three keys, once each, in the order they were written (AC5)', async () => {
      const watch = watchStorage();
      const started = await startSignIn();

      const arrival = await arriveAt(`/callback?code=THE-CODE&state=${started.state}`, () =>
        jsonResponse(tokensFor(started.nonce)),
      );

      expect(arrival.holder.accessToken.current()).toBe(ACCESS_TOKEN);
      // Once each: a loop that removed a key twice, or a second call site, shows up here and nowhere else.
      expect(removedKeys(watch)).toEqual([...THREE_KEYS]);
      expect((watch.setItem.mock.calls as Array<[string, string]>).map(([key]) => key)).toEqual([...THREE_KEYS]);
    });

    /**
     * The deletion is three named removals, not a cleanup: an unrelated key written by anything else in the tab
     * survives it. This is the case a `sessionStorage.clear()` implementation fails, and the only one - the end
     * state of `clear()` and of three removals is identical as far as the three items go.
     */
    it('leaves a key that is not its own alone', async () => {
      globalThis.sessionStorage.setItem('ui.theme', 'dark');
      const started = await startSignIn();

      await arriveAt(`/callback?code=THE-CODE&state=${started.state}`, () =>
        jsonResponse(tokensFor(started.nonce)),
      );

      expect(storedKeys()).toEqual(['ui.theme']);
      expect(globalThis.sessionStorage.getItem('ui.theme')).toBe('dark');
    });
  });

  describe('and only once (AC3, AC10, AC11)', () => {
    /**
     * What single-use is FOR. The same callback URL arriving a second time has no stored state to match, so it
     * is discarded before any network call - `fetch` is never reached, which is the assertion that distinguishes
     * this from the pre-T23 behaviour, where the replay was exchanged and refused by the token endpoint instead.
     */
    it('refuses the same callback a second time, with no network call at all', async () => {
      const started = await startSignIn();
      const url = `/callback?code=THE-CODE&state=${started.state}`;
      const first = await arriveAt(url, () => jsonResponse(tokensFor(started.nonce)));
      expect(first.holder.accessToken.current()).toBe(ACCESS_TOKEN);
      expect(first.fetchSpy).toHaveBeenCalledTimes(1);
      expect(presentItems()).toEqual([]);
      first.unmount();

      const replay = await arriveAt(url, () => jsonResponse(tokensFor(started.nonce)));

      expect(replay.fetchSpy, 'the replay reached no token endpoint').not.toHaveBeenCalled();
      expect(replay.router.state.location.pathname).toBe(SIGN_IN_ROUTE);
      replay.unmount();

      // THE CAUSE, not just the outcome. Re-seed the three with the values the first attempt had, and the very
      // same URL reaches the network again - so "no network call" above is the deleted state doing the work, not
      // something else about a second arrival. This is also exactly what the pre-T23 replay did: it exchanged,
      // and the token endpoint refused the spent code (`400 invalid_grant`, measured in T20).
      globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.state, started.state);
      globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.nonce, started.nonce);
      globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.codeVerifier, started.verifier);
      const reseeded = await arriveAt(url, () => refusal());

      expect(reseeded.fetchSpy, 'with the state back, the same URL is exchanged again').toHaveBeenCalledTimes(1);
      expect(reseeded.router.state.location.pathname).toBe(SIGN_IN_ROUTE);
      // And the tab is left holding nothing: a replayed callback is a discard, and a discard clears the holder
      // (R22 - no half-authenticated tab). Task 24 navigating away from this route is what makes it unreachable
      // (T23-D16); until then this is the honest consequence, stated rather than discovered.
      expect(replay.holder.accessToken.current()).toBeNull();
    });

    it('is indistinguishable from any other discard (AC11, L4)', async () => {
      const started = await startSignIn();
      const url = `/callback?code=THE-CODE&state=${started.state}`;
      const first = await arriveAt(url, () => jsonResponse(tokensFor(started.nonce)));
      first.unmount();
      const replay = await arriveAt(url, () => jsonResponse(tokensFor(started.nonce)));
      const replayed = outcomeOf(replay);
      replay.unmount();

      const second = await startSignIn();
      const mismatch = await arriveAt('/callback?code=THE-CODE&state=not-the-stored-state', () =>
        jsonResponse(tokensFor(second.nonce)),
      );

      expect(replayed.text, 'the control: there IS copy to compare').toContain(en.signIn.discarded);
      expect(replayed).toEqual(outcomeOf(mismatch));
    });

    it('does not stop the next sign-in: a fresh attempt completes end to end (AC10)', async () => {
      const first = await startSignIn();
      const consumed = await arriveAt(`/callback?code=THE-CODE&state=${first.state}`, () =>
        jsonResponse(tokensFor(first.nonce)),
      );
      expect(consumed.holder.accessToken.current()).toBe(ACCESS_TOKEN);
      consumed.unmount();

      const second = await startSignIn();
      expect(second.state).not.toBe(first.state);
      const arrival = await arriveAt(`/callback?code=THE-SECOND-CODE&state=${second.state}`, () =>
        jsonResponse(tokensFor(second.nonce, { access_token: SECOND_ACCESS_TOKEN })),
      );

      // A different token, so neither success can be a constant that happened to match (T22-D13).
      expect(arrival.holder.accessToken.current()).toBe(SECOND_ACCESS_TOKEN);
      expect((await sentFields(arrival.fetchSpy)).code_verifier).toBe(second.verifier);
      expect(presentItems()).toEqual([]);
    });
  });

  describe('a discarded callback leaves all three where they are (AC4, T23-D2)', () => {
    const FAULTS: ReadonlyArray<{
      label: string;
      url: (state: string) => string;
      // `never` is a valid Response here: a row whose answer only throws is a `fetch` that never resolved.
      answer: (nonce: string) => Response;
    }> = [
      { label: 'no state in the query', url: () => '/callback?code=THE-CODE', answer: (n) => jsonResponse(tokensFor(n)) },
      { label: 'a state that does not match', url: () => '/callback?code=THE-CODE&state=not-the-stored-state', answer: (n) => jsonResponse(tokensFor(n)) },
      { label: 'no code', url: (s) => `/callback?state=${s}`, answer: (n) => jsonResponse(tokensFor(n)) },
      { label: 'an authorization error', url: (s) => `/callback?error=access_denied&state=${s}`, answer: (n) => jsonResponse(tokensFor(n)) },
      { label: 'a refused exchange', url: (s) => `/callback?code=THE-CODE&state=${s}`, answer: () => refusal() },
      {
        // A different fault from the refusal above with the same outcome, and listed rather than subsumed: the
        // brief names five shapes, and a reader counting rows should find the dropped connection among them.
        label: 'a dropped connection',
        url: (s) => `/callback?code=THE-CODE&state=${s}`,
        answer: () => {
          throw new TypeError('Failed to fetch');
        },
      },
      {
        label: 'a nonce that is not the stored one',
        url: (s) => `/callback?code=THE-CODE&state=${s}`,
        answer: () => jsonResponse(tokensFor('a-nonce-this-tab-never-generated')),
      },
    ];

    it.each(FAULTS)('keeps all three after $label', async ({ url, answer }) => {
      const watch = watchStorage();
      const started = await startSignIn();

      const arrival = await arriveAt(url(started.state), () => answer(started.nonce));

      expect(arrival.router.state.location.pathname, 'the control: it really was discarded').toBe(SIGN_IN_ROUTE);
      expect(arrival.holder.accessToken.current()).toBeNull();
      expect(presentItems()).toEqual([...THREE_KEYS]);
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state)).toBe(started.state);
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.nonce)).toBe(started.nonce);
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.codeVerifier)).toBe(started.verifier);
      expect(watch.removeItem).not.toHaveBeenCalled();
      expect(watch.clear).not.toHaveBeenCalled();
    });

    /**
     * Why T23-D2 is a decision and not an oversight, as one case. `/app/callback` is public: anyone who can get
     * a visitor to open it with a `state` of their choosing reaches the discard path. If that path deleted, the
     * visitor's pending sign-in would be over - a denial of service on every sign-in in progress, triggered by a
     * link. So the stranger's arrival is discarded, the three survive, and the real callback still completes.
     */
    it('cannot be used to end a pending sign-in from outside (T23-D2)', async () => {
      const started = await startSignIn();

      const stranger = await arriveAt('/callback?code=A-CODE-OF-THEIR-CHOOSING&state=the-stranger-state', () =>
        jsonResponse(tokensFor(started.nonce)),
      );
      expect(stranger.router.state.location.pathname).toBe(SIGN_IN_ROUTE);
      expect(presentItems()).toEqual([...THREE_KEYS]);
      stranger.unmount();

      const real = await arriveAt(`/callback?code=THE-CODE&state=${started.state}`, () =>
        jsonResponse(tokensFor(started.nonce)),
      );

      expect(real.holder.accessToken.current(), 'the sign-in in progress still completes').toBe(ACCESS_TOKEN);
      expect((await sentFields(real.fetchSpy)).code_verifier).toBe(started.verifier);
      expect(presentItems()).toEqual([]);
    });
  });

  describe('when the store will not cooperate (AC5, AC8, T23-D4)', () => {
    /**
     * `removeItem` can THROW rather than fail quietly - a context with site data blocked raises on every call -
     * and there is no error boundary in this application, so an uncaught throw here would blank the page at the
     * end of a sign-in, after the visitor has already authenticated. The guard's whole job is that this case
     * looks like a normal success to the visitor.
     *
     * Three ATTEMPTS are asserted, not just the outcome (R-I2). Without that, a flow that never tried to delete
     * at all - a missing `sessionStorage`, a call site removed - passes every other assertion here.
     */
    it('still signs the visitor in, and leaves all three, when every removal throws', async () => {
      const denied = denyRemoval();
      const started = await startSignIn();

      const arrival = await arriveAt(`/callback?code=THE-CODE&state=${started.state}`, () =>
        jsonResponse(tokensFor(started.nonce)),
      );

      expect(arrival.holder.accessToken.current(), 'the outcome is unchanged (T23-D4)').toBe(ACCESS_TOKEN);
      expect(arrival.router.state.location.pathname).toBe(CALLBACK_ROUTE);
      expect((denied.mock.calls as Array<[string]>).map(([key]) => key)).toEqual([...THREE_KEYS]);
      expect(denied.mock.results.every((result) => result.type === 'throw')).toBe(true);
      expect(presentItems()).toEqual([...THREE_KEYS]);
    });

    /**
     * The honest shape of "all or nothing" (T23-D3, Kimi F5). `sessionStorage` has no transaction, so a guard per
     * key means a store that refuses ONE key removes the other two. The realistic failure denies all three
     * together - the case above - and this one pins what the implementation really does rather than letting the
     * phrase imply atomicity the platform cannot provide.
     */
    it('removes the two it can when the store refuses exactly one', async () => {
      denyRemoval(AUTHORIZE_STORAGE_KEYS.state);
      const started = await startSignIn();

      const arrival = await arriveAt(`/callback?code=THE-CODE&state=${started.state}`, () =>
        jsonResponse(tokensFor(started.nonce)),
      );

      expect(arrival.holder.accessToken.current()).toBe(ACCESS_TOKEN);
      expect(presentItems()).toEqual([AUTHORIZE_STORAGE_KEYS.state]);
      // Which leaves a `state` that could match a second time - so the next arrival is NOT refused by the store
      // being empty. It is refused by the token endpoint, which is why that outer defence still matters.
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state)).toBe(started.state);
    });
  });

  describe('under StrictMode (AC7, F4)', () => {
    /**
     * StrictMode invokes effects twice in development. The screen's `handled` ref is what makes one arrival one
     * outcome, and T23's deletion is the first thing that makes the second pass OBSERVABLE: a second pass that
     * re-ran the handler would find an empty store, fail the state comparison, and discard a sign-in that had
     * already succeeded. So the route is asserted too, exactly as Kimi F4 asked - one deletion is not enough on
     * its own, because six removals and a discard would both be wrong in different ways.
     */
    it('handles once, deletes once, and does not end on the discard route', async () => {
      const watch = watchStorage();
      const started = await startSignIn();

      const arrival = await arriveAt(
        `/callback?code=THE-CODE&state=${started.state}`,
        () => jsonResponse(tokensFor(started.nonce)),
        { strict: true },
      );

      expect(arrival.fetchSpy, 'one handling').toHaveBeenCalledTimes(1);
      expect(removedKeys(watch), 'one deletion, not two passes of three').toEqual([...THREE_KEYS]);
      expect(arrival.router.state.location.pathname, 'the second pass did not discard').toBe(CALLBACK_ROUTE);
      expect(arrival.holder.accessToken.current()).toBe(ACCESS_TOKEN);
      expect(presentItems()).toEqual([]);
    });
  });

  describe('the order is not a coincidence (AC6, F6)', () => {
    /*
     * WHAT THIS BLOCK DOES NOT ASSERT, and why, so that nobody adds it later: the order between
     * `retainAccessToken` and `consumeAuthorizeState` is deliberately not pinned. Nothing between those two
     * statements reads the three items, so both positions satisfy AC6 and the mutation harness measured no
     * observable difference either way (M16, 439 tests, 0 failed, exit 0 in both orders). Pinning it would be a
     * structural assertion about statement order, which is what AC6 was corrected AWAY from in the frozen brief.
     *
     * The boundaries that are real, and that the two cases below guard, are the exchange - which reads the
     * verifier inside the request - and the nonce check, which reads the nonce after it resolves. Moving the
     * deletion above either of those kills 43 and 36 cases respectively.
     */
    /**
     * AC6 is BEHAVIOURAL, and the Phase 2 brief's hedge that it "may have to be structural" was wrong: a
     * deletion one line earlier does not produce the same end state, it produces a failed sign-in. The verifier
     * is read inside the exchange and the nonce after it resolves, so this case is the ordering claim stated
     * directly - every read of the three happens before the first removal of any of them.
     */
    it('reads all three before it removes any of them', async () => {
      const started = await startSignIn();
      const log = logStorageOrder();

      const arrival = await arriveAt(`/callback?code=THE-CODE&state=${started.state}`, () =>
        jsonResponse(tokensFor(started.nonce)),
      );

      expect(arrival.holder.accessToken.current()).toBe(ACCESS_TOKEN);
      // The control: the three readers really ran. An ordering assertion over a log with no reads in it holds
      // trivially, and that is precisely the mutant it has to kill.
      expect(log).toContain(`get ${AUTHORIZE_STORAGE_KEYS.state}`);
      expect(log).toContain(`get ${AUTHORIZE_STORAGE_KEYS.nonce}`);
      expect(log).toContain(`get ${AUTHORIZE_STORAGE_KEYS.codeVerifier}`);
      const lastRead = log.findLastIndex((event) => event.startsWith('get '));
      const firstRemoval = log.findIndex((event) => event.startsWith('remove '));
      expect(firstRemoval, 'the control: a removal really happened').toBeGreaterThan(-1);
      expect(lastRead).toBeLessThan(firstRemoval);
      expect(log.slice(firstRemoval)).toEqual(THREE_KEYS.map((key) => `remove ${key}`));
    });

    /**
     * The same claim from the other side, and the one a mutation harness kills most directly: the exchange
     * RECEIVED the verifier. A `consumeAuthorizeState()` moved above `exchangeAuthorizationCode` leaves this
     * field missing, and the sign-in fails - which is why the ordering is not a matter of taste.
     */
    it('hands the exchange the verifier the authorize step stored', async () => {
      const started = await startSignIn();

      const arrival = await arriveAt(`/callback?code=THE-CODE&state=${started.state}`, () =>
        jsonResponse(tokensFor(started.nonce)),
      );

      const fields = await sentFields(arrival.fetchSpy);
      expect(fields.code_verifier).toBe(started.verifier);
      expect(fields.code).toBe('THE-CODE');
      expect(arrival.holder.accessToken.current()).toBe(ACCESS_TOKEN);
    });
  });

  describe('consumeAuthorizeState on its own (R-I9, AC9)', () => {
    it('removes exactly the three keys it owns', () => {
      globalThis.sessionStorage.setItem('ui.theme', 'dark');
      for (const key of THREE_KEYS) globalThis.sessionStorage.setItem(key, `value-for-${key}`);
      expect(presentItems()).toEqual([...THREE_KEYS]);

      consumeAuthorizeState();

      expect(storedKeys()).toEqual(['ui.theme']);
    });

    it('is idempotent, and does not mind an empty store', () => {
      const watch = watchStorage();
      for (const key of THREE_KEYS) globalThis.sessionStorage.setItem(key, 'x');

      consumeAuthorizeState();
      consumeAuthorizeState();

      expect(storedKeys()).toEqual([]);
      // Six attempts, three keys, no throw: idempotence here is "calling it again is harmless", not "it
      // remembers it has run" - there is no state to remember, which is why it can be called from one place
      // without a latch of its own.
      expect(removedKeys(watch)).toEqual([...THREE_KEYS, ...THREE_KEYS]);
      expect(watch.clear).not.toHaveBeenCalled();
    });

    it('never throws when the store refuses, and leaves what it could not remove', () => {
      for (const key of THREE_KEYS) globalThis.sessionStorage.setItem(key, 'x');
      const denied = denyRemoval();

      expect(() => consumeAuthorizeState()).not.toThrow();

      expect(denied).toHaveBeenCalledTimes(3);
      expect(presentItems()).toEqual([...THREE_KEYS]);
    });

    /**
     * A partially populated store, which is neither of the two cases above. A regression that iterated its own
     * array of three names, or that read a key before removing it, would pass both a full store and an empty one
     * and fail here - and `removeItem` on an absent key is a no-op by specification, which is what makes the
     * loop safe to run over a store that has already lost one.
     */
    it('removes what is there when only some of the three are', () => {
      const watch = watchStorage();
      globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.state, 'the-state');
      globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.nonce, 'the-nonce');
      globalThis.sessionStorage.setItem('ui.theme', 'dark');

      expect(() => consumeAuthorizeState()).not.toThrow();

      expect(storedKeys()).toEqual(['ui.theme']);
      // All three were attempted, including the one that was not there: the function does not look first.
      expect(removedKeys(watch)).toEqual([...THREE_KEYS]);
    });

    /**
     * The "one call site" claim over the whole package, rather than over the one file expected to hold it.
     *
     * The case below it reads `CallbackScreen.tsx` and counts one call - which is true and insufficient: a
     * second caller would most likely be a NEW file, and T23-D2's whole argument depends on no discard path
     * anywhere calling this. So the net is the package, with the definition and the single call named, and a
     * synthetic file proving the scan can report.
     */
    it('is called from exactly one place in the package (AC5, T23-D2)', async () => {
      const targets = await productionSources();
      expect(targets.length).toBeGreaterThan(20);

      const callers = targets
        .filter(({ code }) => code.includes('consumeAuthorizeState'))
        .map(({ path }) => path)
        .sort();

      expect(callers).toEqual(['auth/CallbackScreen.tsx', 'auth/authorizeRequest.ts']);
      // And the scan really reports: the same filter over a synthetic module finds it.
      expect(
        [{ path: 'api/somewhere.ts', code: 'consumeAuthorizeState();' }]
          .filter(({ code }) => code.includes('consumeAuthorizeState'))
          .map(({ path }) => path),
      ).toEqual(['api/somewhere.ts']);
    });

    /**
     * The pin Kimi I1 asked for, and the message is the point of it. `consumeAuthorizeState` iterates the
     * constant, which is deliberate - a fourth SINGLE-USE key cannot then be left behind - but L16's fourth item
     * is the post-login return target, and task 24 is the obvious author of it. If task 24 adds its key to this
     * constant, the deletion at callback starts taking it too. This case is where that decision gets made, out
     * loud, instead of being inherited.
     */
    it('deletes the set this constant names, which is exactly three keys today (R-I1)', () => {
      expect(
        Object.keys(AUTHORIZE_STORAGE_KEYS),
        'consumeAuthorizeState() deletes every value of AUTHORIZE_STORAGE_KEYS. If you are adding a fourth ' +
          'key (task 24s return target?), decide whether it is single-use at callback: if it is, add it here; ' +
          'if it is not, keep it in its own constant so this deletion cannot reach it.',
      ).toEqual(['state', 'nonce', 'codeVerifier']);
    });

    /**
     * AC9's last clause: no key is hand-spelled. The three literals exist in exactly one place - the constant -
     * and neither the deleter nor the screen writes one, which is what keeps the writer and the deleter unable
     * to drift apart. A second spelling is also what T22's source scan forbids.
     */
    it('spells no storage key of its own (AC9)', () => {
      const source = authorizeSource ?? '';
      expect(source.match(/themistra\.auth\./g) ?? []).toHaveLength(3);
      const body = source.slice(source.indexOf('export function consumeAuthorizeState'));
      expect(body).not.toContain('themistra');
      expect(body).toContain('Object.values(AUTHORIZE_STORAGE_KEYS)');
      expect(callbackSource ?? '').not.toContain('themistra');
      // One call site, which is what makes "all three or none" true of the flow (T23-D3).
      expect((callbackSource ?? '').match(/consumeAuthorizeState\(\)/g) ?? []).toHaveLength(1);
    });
  });
});
