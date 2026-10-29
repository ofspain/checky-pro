/// <reference types="node" />
/**
 * `returnTargetOutsideAllowlistFallsBackToHome` (package.md §8) — R7, L1.
 *
 * L1 and `agents.md:21` are one sentence between them: a completed sign-in goes to an **allowlisted** return
 * target, falling back to the home route, "never to an arbitrary URL". The last clause is what makes this a
 * security control rather than a convenience. An open redirect at the end of an OIDC flow is how a phishing chain
 * ends, because the victim really did sign in - so whatever page they land on inherits the trust of a completed
 * login, and the browser's address bar agrees with it.
 *
 * THIS FILE IS PARTITIONED (F7). T24 owns the mechanism and the honoured path: a target on the allowlist is
 * navigated to, an absent one goes home, the target is single-use, the allowlist is DERIVED from the route table,
 * no discard honours a target, and the read is total. **T25 owns the rejection families** - absolute URLs,
 * protocol-relative hosts, `javascript:`, backslash paths, traversal, encoded traversal, prefix matches, public
 * routes, nonexistent routes, parameterised routes and targets carrying a query or fragment - and they are
 * `it.todo` rows at the foot of this file so that the partition is readable in the source rather than only in an
 * artifact. T24 proves the fallback with ONE representative: `/app/home`, the basename-carrying form, which is
 * T24-D8's own decision and belongs to no T25 family.
 *
 * THE HOUSE RISK IS A TEST THAT PASSES AGAINST NO NAVIGATION AT ALL. "Landed on home" is satisfied by a flow
 * that never navigated anywhere if home happens to be where it started, and "landed on the target" is satisfied
 * by a harness that cannot reach any other route. So every destination case asserts the destination **and** that
 * the other destination was reachable from the same harness; the honoured case watches the router sit on
 * `/app/callback` while the exchange is in flight, which is the "would otherwise have stayed" control stated as
 * a measurement; and the derivation case proves a path ADDED to the route table becomes legal, not merely that
 * today's list matches today's list.
 *
 * WHERE THE VISITOR ACTUALLY LANDS (T24-D11). `/app/home` is registered with **no element**, and no task in the
 * 142-task package builds a home screen. The navigation is the whole of the verifiable claim; nothing here may
 * assert what renders afterwards, because the answer today is nothing.
 *
 * WHAT IS NOT HONOURED, said out loud: parameterised routes (T24-D5 - which costs the two routes notification
 * emails deep-link to) and targets carrying a query or fragment (T24-D6).
 */
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { act, StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, type Mock, vi } from 'vitest';
import { AUTHENTICATED_ROUTE_PATHS, HOME_PATH } from '../app/routes';
import { createI18n } from '../i18n/i18n';
import { AUTHORIZE_STORAGE_KEYS, buildAuthorizeRequest } from './authorizeRequest';
import {
  isAllowlistedTarget,
  rememberReturnTarget,
  RETURN_TARGET_KEY,
  takeReturnTarget,
} from './returnTarget';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/**
 * The static imports above are the right instance for what this file asks of them, and that is not automatic.
 * `vi.resetModules()` runs between cases, so a statically imported module is a DIFFERENT object than the one the
 * screens hold - T23 measured that trap. It does not bite here: `takeReturnTarget` and `rememberReturnTarget`
 * reach `globalThis.sessionStorage`, which is one store shared by every instance, and `isAllowlistedTarget`
 * derives from the real route table, which no case mutates. The one case where instance identity IS the point -
 * the derivation, which needs a route table this file invented - imports `./returnTarget` dynamically and
 * asserts it got a genuinely new instance before it believes anything.
 */

const ACCESS_TOKEN = 'ACCESS-TOKEN-91c4e7f20b3d';
/** A second token, so a success asserted twice cannot be a constant that happened to match (T22-D13). */
const SECOND_ACCESS_TOKEN = 'ACCESS-TOKEN-4b08da37f6e1';

const CALLBACK_ROUTE = '/app/callback';
const SIGN_IN_ROUTE = '/app/sign-in';
const HOME_ROUTE = `/app/${HOME_PATH}`;

/** A member of the allowlist that is NOT home, so "honoured" and "fell back" can never be the same assertion. */
const A_REAL_TARGET = '/account/sessions';
const A_REAL_TARGET_ROUTE = `/app${A_REAL_TARGET}`;

/**
 * The representative rejection T24 owns (T24-D8). The stored form is router-relative - `/account/sessions` - and
 * `/app/home` carries the basename, so it is simply not a member. No stripping, no normalising, no second code
 * path: the allowlist IS the rule. Every other rejection shape is T25's, listed at the foot of this file.
 */
const A_TARGET_CARRYING_THE_BASENAME = '/app/home';

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

/** What a refused exchange really returns: HTTP 400 and an OAuth2 error object. */
function refusal(): Response {
  return jsonResponse({ error: 'invalid_grant', error_description: 'the code verifier did not match' }, 400);
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

/* ─────────────────────────── the instruments ─────────────────────────── */

type StorageWatch = { setItem: Mock; removeItem: Mock; clear: Mock };

/**
 * Spies the storage PROTOTYPE, and must be installed before the flow runs - T22 measured a watch installed one
 * line too late reporting zero writes for a flow that had just made three. The prototype rather than a store
 * object, so a call through `globalThis['sessionStorage']` or a bound alias is still seen.
 */
function watchStorage(): StorageWatch {
  return {
    setItem: vi.spyOn(Storage.prototype, 'setItem') as unknown as Mock,
    removeItem: vi.spyOn(Storage.prototype, 'removeItem') as unknown as Mock,
    clear: vi.spyOn(Storage.prototype, 'clear') as unknown as Mock,
  };
}

function removedKeys(watch: StorageWatch): string[] {
  return (watch.removeItem.mock.calls as Array<[string]>).map(([key]) => key);
}

function writtenKeys(watch: StorageWatch): string[] {
  return (watch.setItem.mock.calls as Array<[string, string]>).map(([key]) => key);
}

/** Every key the store holds, sorted. A whole-store read, so an extra item cannot hide behind named gets. */
function storedKeys(): string[] {
  return Object.keys(globalThis.sessionStorage).sort();
}

/**
 * One ordered log of every read and removal OF THE RETURN KEY, which is what makes AC7 behavioural rather than
 * structural. Calls through to the real methods - an ordering case over a store that no longer works would prove
 * nothing - and logs only this key, because the router and the notice module touch storage too and their traffic
 * is not what the claim is about.
 */
function logReturnKeyOrder(): string[] {
  const log: string[] = [];
  const realGet = Storage.prototype.getItem;
  const realRemove = Storage.prototype.removeItem;
  vi.spyOn(Storage.prototype, 'getItem').mockImplementation(function (this: Storage, key: string) {
    if (key === RETURN_TARGET_KEY) log.push('get');
    return realGet.call(this, key);
  });
  vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(function (this: Storage, key: string) {
    if (key === RETURN_TARGET_KEY) log.push('remove');
    realRemove.call(this, key);
  });
  return log;
}

/** A store that refuses to remove ONE key, which is the shape T24-D9 is about. */
function denyRemovalOf(only: string): Mock {
  const realRemove = Storage.prototype.removeItem;
  return vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(function (this: Storage, key: string) {
    if (key === only) throw new DOMException('the store is not available', 'SecurityError');
    realRemove.call(this, key);
  }) as unknown as Mock;
}

/** A store that refuses to READ one key. The rest of the sign-in must be untouched by it (AC10). */
function denyReadOf(only: string): Mock {
  const realGet = Storage.prototype.getItem;
  return vi.spyOn(Storage.prototype, 'getItem').mockImplementation(function (this: Storage, key: string) {
    if (key === only) throw new DOMException('the store is not available', 'SecurityError');
    return realGet.call(this, key);
  }) as unknown as Mock;
}

/* ─────────────────────────── the flow ─────────────────────────── */

type Arrival = {
  container: HTMLElement;
  router: ReturnType<typeof createMemoryRouter>;
  fetchSpy: Mock;
  /**
   * The holder THE SCREEN USED. `arriveAt` imports the route graph dynamically and `vi.resetModules()` runs
   * between cases, so asserting on a static `accessToken` import would read an untouched module and pass
   * whatever production did. This import happens in the same registry as the routes import, so it is the
   * instance the screen wrote to.
   */
  holder: typeof import('./accessToken');
  /** Unmounts WITHOUT disposing the router, so a case can still read where it did or did not go afterwards. */
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
      mounted = undefined;
    },
  };
}

type Triple = { state: string; nonce: string; verifier: string };

/**
 * The real authorize step - the thing that writes the three - refusing to return a triple it cannot read back.
 * Vacuity control in one function: every "the sign-in completed" assertion below stands on this having found the
 * three in the store first.
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
  return triple;
}

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

/**
 * Remembers a target through the REAL producer, and refuses to continue if the store did not take it.
 *
 * Two jobs in one helper. It is the vacuity control for every honoured case - "we landed on the target" means
 * nothing if nothing was ever remembered - and it is the observation that `rememberReturnTarget` filters on
 * write (T24-D15), because `seedRaw` exists precisely for the values this one will not store.
 */
function remember(target: string): string {
  rememberReturnTarget(target);
  expect(globalThis.sessionStorage.getItem(RETURN_TARGET_KEY), `remembered ${target}`).toBe(target);
  return target;
}

/** Puts a value in the store by hand, for the shapes the producer refuses and a hand-edited store can still hold. */
function seedRaw(value: string): string {
  globalThis.sessionStorage.setItem(RETURN_TARGET_KEY, value);
  expect(globalThis.sessionStorage.getItem(RETURN_TARGET_KEY)).toBe(value);
  return value;
}

/** A `fetch` answer that has not come back yet, and the lever that lets it. */
function deferredAnswer(response: () => Response): { answer: () => Promise<Response>; release: () => void } {
  let release: () => void = () => undefined;
  const pending = new Promise<Response>((resolve) => {
    release = () => resolve(response());
  });
  return { answer: () => pending, release: () => release() };
}

/** Signs a visitor in, all the way, and reports where the callback left them. */
async function signInAndLand(options: { url?: (state: string) => string } = {}): Promise<Arrival> {
  const started = await startSignIn();
  const url = options.url ?? ((state: string) => `/callback?code=THE-CODE&state=${state}`);
  return arriveAt(url(started.state), () => jsonResponse(tokensFor(started.nonce)));
}

/** A production file's code with its comments removed, because a file explains the rules it obeys (T22's trap). */
function strip(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
}

/**
 * Lines of `returnTarget.ts` that name the route table OUTSIDE a function body.
 *
 * The file's own comment promises a test for this, and the promise is worth keeping: the route table imports
 * `CallbackScreen` before it declares `AUTHENTICATED_ROUTE_PATHS`, so a module-scope read here touches the
 * binding inside its temporal dead zone, and T16 measured that Vite yields `undefined` rather than throwing.
 * `undefined` is the worst answer available, because an empty allowlist **fails closed silently**: every target
 * would be rejected, the SPA would look like it worked, and nothing would ever be honoured again. The import
 * line is excluded; a declaration at module scope is unindented, and one inside a function is not.
 */
function moduleScopeUsesOfTheRouteTable(source: string): string[] {
  const found: string[] = [];
  let depth = 0;
  for (const line of strip(source).split('\n')) {
    const trimmed = line.trimStart();
    // An import and a RE-EXPORT both name the binding without reading it: `export { X } from './y'` and
    // `export { X }` are wired up by the module graph and never evaluated, so neither can touch the temporal
    // dead zone this scan exists to keep out of. `export const EAGER = X` IS a read, and is reported. Kimi's
    // Gap 6 asked for the opposite - that a re-export be reported too - and it is declined: a guard that fires
    // on something harmless is a guard the next person in a hurry loosens.
    const namesWithoutReading =
      trimmed.startsWith('import') || /^export\s*\{[^}]*\}\s*(?:from\s*'[^']*')?;?$/.test(trimmed);
    if (depth === 0 && !namesWithoutReading && /\b(?:AUTHENTICATED_ROUTE_PATHS|HOME_PATH)\b/.test(line)) {
      found.push(line.trim());
    }
    // Brace depth, not indentation. The first version of this scan tested indentation and MISSED the mutant that
    // matters most - a module-scope `new Set(` whose continuation line is indented like a function body - so the
    // multi-line control below is the one that found the hole, and it stays to keep it closed.
    for (const character of line) {
      if (character === '{') depth += 1;
      else if (character === '}') depth -= 1;
    }
  }
  return found;
}

/** The allowlist as the route table says it should be, computed here independently of the module under test. */
const EXPECTED_ALLOWLIST = AUTHENTICATED_ROUTE_PATHS.filter(
  (path) => !path.split('/').some((segment) => segment.startsWith(':')),
).map((path) => `/${path}`);

let returnTargetSource: string | undefined;

describe('returnTargetOutsideAllowlistFallsBackToHome', () => {
  beforeEach(async () => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.resetModules();
    globalThis.sessionStorage.clear();
    globalThis.localStorage.clear();
    returnTargetSource ??= await readFile(
      join(dirname(expect.getState().testPath as string), 'returnTarget.ts'),
      'utf8',
    );
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

  describe('an allowlisted target is honoured (AC1, R7, L1)', () => {
    /**
     * The honoured path and its control in one case, because they are the same measurement taken twice.
     *
     * While the exchange is in flight the router sits on `/app/callback` - that is where the visitor WOULD have
     * stayed, and it is measured rather than asserted from memory of a previous task. Releasing the response
     * moves them, and the destination is the remembered target rather than home, so neither the navigation nor
     * the allowlist can be the thing that did nothing.
     */
    it('navigates to the remembered target, from a callback it would otherwise have stayed on', async () => {
      remember(A_REAL_TARGET);
      const started = await startSignIn();
      const { answer, release } = deferredAnswer(() => jsonResponse(tokensFor(started.nonce)));

      const arrival = await arriveAt(`/callback?code=THE-CODE&state=${started.state}`, answer);

      expect(arrival.router.state.location.pathname, 'the control: still on the callback').toBe(CALLBACK_ROUTE);
      release();
      await turn();

      expect(arrival.router.state.location.pathname).toBe(A_REAL_TARGET_ROUTE);
      expect(A_REAL_TARGET_ROUTE, 'and that is not where a fallback would have gone').not.toBe(HOME_ROUTE);
      expect(arrival.holder.accessToken.current(), 'a real completed sign-in, not an early exit').toBe(
        ACCESS_TOKEN,
      );
    });

    /**
     * Every member of the derived allowlist, honoured end to end. Not a table of paths written out by hand - that
     * would be the second list AC6 forbids - but the derivation itself, driven through the whole flow. A rule
     * that admitted only the one target the case above uses would pass that case and fail here.
     */
    it.each(EXPECTED_ALLOWLIST)('honours %s, the whole way through a sign-in', async (target) => {
      remember(target);

      const arrival = await signInAndLand();

      expect(arrival.router.state.location.pathname).toBe(`/app${target}`);
      expect(arrival.holder.accessToken.current()).toBe(ACCESS_TOKEN);
      expect(storedKeys(), 'and the target did not outlive the navigation').not.toContain(RETURN_TARGET_KEY);
    });

    /** The destination is replaced, not pushed: a completed callback must not be reachable with the back button. */
    it('replaces the callback entry rather than pushing the target on top of it', async () => {
      remember(A_REAL_TARGET);

      const arrival = await signInAndLand();

      expect(arrival.router.state.location.pathname).toBe(A_REAL_TARGET_ROUTE);
      expect(
        arrival.router.state.historyAction,
        'REPLACE, so the back button cannot return to a consumed callback',
      ).toBe('REPLACE');
    });
  });

  describe('no target at all goes home (AC3)', () => {
    /**
     * The common case, and the one that pays for a conditional removal. An unconditional `removeItem` would make
     * a FOURTH removal on every successful sign-in for a value that was never written - which three inherited
     * assertions in T23's named test are right to object to, and which this case pins from the other side.
     */
    it('goes home, and removes nothing on the way', async () => {
      const watch = watchStorage();

      const arrival = await signInAndLand();

      expect(arrival.router.state.location.pathname).toBe(HOME_ROUTE);
      expect(removedKeys(watch), 'no removal for a target that was never remembered').not.toContain(
        RETURN_TARGET_KEY,
      );
      expect(watch.clear, 'and nothing reached for the whole store').not.toHaveBeenCalled();
    });

    /**
     * AC3's real content: an absent target and a rejected one are the same journey. Not "both end on home" -
     * that is one assertion made twice - but the same rendered outcome from the same harness, which is also AC9:
     * there is no "your destination was rejected" anywhere, because there is no second outcome to describe.
     */
    it('is indistinguishable from a target the allowlist refused (AC9)', async () => {
      const absent = await signInAndLand();
      const withoutATarget = {
        pathname: absent.router.state.location.pathname,
        search: absent.router.state.location.search,
        text: (absent.container.textContent ?? '').trim(),
        // `historyAction` is in the comparison because `replace` is part of the outcome, not a detail of the
        // honoured path: a fallback that PUSHED would leave a consumed callback reachable with the back button,
        // and pathname, search and text would all still agree (Kimi Gap 1).
        historyAction: absent.router.state.historyAction,
      };
      absent.unmount();
      vi.resetModules();
      globalThis.sessionStorage.clear();

      seedRaw(A_TARGET_CARRYING_THE_BASENAME);
      const rejected = await signInAndLand();

      expect({
        pathname: rejected.router.state.location.pathname,
        search: rejected.router.state.location.search,
        text: (rejected.container.textContent ?? '').trim(),
        historyAction: rejected.router.state.historyAction,
      }).toEqual(withoutATarget);
      expect(withoutATarget.pathname, 'the control: home was where both went').toBe(HOME_ROUTE);
      expect(withoutATarget.historyAction, 'and both replaced the callback entry').toBe('REPLACE');
    });

    /**
     * T24-D8 as a case. The stored form is router-relative, so a value carrying the basename is not a member -
     * and that is the whole mechanism, not a special rule: nothing strips `/app`, nothing normalises, and the
     * near-miss is refused for exactly the same reason a hostile URL is. (The hostile shapes are T25's rows.)
     */
    it('refuses a target carrying the basename, by membership and nothing else (AC2, T24-D8)', () => {
      expect(isAllowlistedTarget(A_TARGET_CARRYING_THE_BASENAME)).toBe(false);
      expect(isAllowlistedTarget(HOME_ROUTE)).toBe(false);
      expect(isAllowlistedTarget(`/${HOME_PATH}`), 'the control: the router-relative form IS a member').toBe(
        true,
      );
    });
  });

  describe('the target is single-use (AC7, L16)', () => {
    /**
     * Read before delete, as an ORDER rather than as a structure. Both mistakes are invisible in an end state -
     * the store is empty either way - so the log is the instrument: a deletion moved one line earlier would
     * read `['remove', 'get']` here and still leave the visitor on home, which is where a broken implementation
     * and a working one look identical.
     */
    it('reads the target before deleting it, and deletes it exactly once', async () => {
      remember(A_REAL_TARGET);
      const log = logReturnKeyOrder();

      const arrival = await signInAndLand();

      expect(log).toEqual(['get', 'remove']);
      expect(arrival.router.state.location.pathname, 'and the read was the one that counted').toBe(
        A_REAL_TARGET_ROUTE,
      );
      expect(globalThis.sessionStorage.getItem(RETURN_TARGET_KEY)).toBeNull();
    });

    /**
     * What "single-use" actually defends against: the second sign-in in the same tab inheriting the first one's
     * destination. The first flow is honoured, the second remembers nothing, and the second lands on home - so
     * the deletion is load-bearing rather than tidy.
     */
    it('does not hand the target to the next sign-in', async () => {
      remember(A_REAL_TARGET);
      const first = await signInAndLand();
      expect(first.router.state.location.pathname).toBe(A_REAL_TARGET_ROUTE);
      first.unmount();
      vi.resetModules();

      const started = await startSignIn();
      const second = await arriveAt(`/callback?code=THE-SECOND-CODE&state=${started.state}`, () =>
        jsonResponse(tokensFor(started.nonce, { access_token: SECOND_ACCESS_TOKEN })),
      );

      // A different token, so neither sign-in can be a constant that happened to match (T22-D13).
      expect(second.holder.accessToken.current()).toBe(SECOND_ACCESS_TOKEN);
      expect(second.router.state.location.pathname).toBe(HOME_ROUTE);
    });

    /**
     * T24-D9, recorded as behaviour. A store that answers the read and then refuses the removal still sends the
     * visitor where they were going - the alternative is a failed sign-in for a storage quirk - and the item
     * stays behind, which L16 would rather it did not. The next `rememberReturnTarget` overwrites it, and the
     * visitor's outcome does not change.
     */
    it('still honours the target when the removal throws, and says so about what is left behind (AC16)', async () => {
      remember(A_REAL_TARGET);
      const denied = denyRemovalOf(RETURN_TARGET_KEY);

      const arrival = await signInAndLand();

      expect(denied, 'the control: the removal really was attempted').toHaveBeenCalledWith(RETURN_TARGET_KEY);
      expect(arrival.router.state.location.pathname).toBe(A_REAL_TARGET_ROUTE);
      expect(arrival.holder.accessToken.current()).toBe(ACCESS_TOKEN);
      expect(globalThis.sessionStorage.getItem(RETURN_TARGET_KEY), 'T24-D9: it survives').toBe(A_REAL_TARGET);
    });

    /**
     * A rejected target is CONSUMED, not merely ignored. Leaving it would hand the next sign-in a target that can
     * neither be honoured nor got rid of, so the removal happens whether or not the value survives validation.
     * Two assertions in one case on purpose: either alone is satisfied by a change that breaks the other.
     */
    it('consumes a target it refuses, rather than leaving it for the next attempt', async () => {
      seedRaw(A_TARGET_CARRYING_THE_BASENAME);

      const arrival = await signInAndLand();

      expect(arrival.router.state.location.pathname).toBe(HOME_ROUTE);
      expect(globalThis.sessionStorage.getItem(RETURN_TARGET_KEY)).toBeNull();
    });

    /** Direct, because the ordering claim should not depend on a screen being mounted to be true. */
    it('returns the target and empties the store in one call', () => {
      remember(A_REAL_TARGET);

      expect(takeReturnTarget()).toBe(A_REAL_TARGET);

      expect(globalThis.sessionStorage.getItem(RETURN_TARGET_KEY)).toBeNull();
      expect(takeReturnTarget(), 'and a second call gets the fallback, not the target again').toBe(
        `/${HOME_PATH}`,
      );
    });
  });

  describe('no discard honours a target (AC4, T24-D14)', () => {
    const FAULTS: ReadonlyArray<{
      label: string;
      url: (state: string) => string;
      answer: (nonce: string) => Response;
    }> = [
      { label: 'no state in the query', url: () => '/callback?code=THE-CODE', answer: (n) => jsonResponse(tokensFor(n)) },
      { label: 'a state that does not match', url: () => '/callback?code=THE-CODE&state=not-the-stored-state', answer: (n) => jsonResponse(tokensFor(n)) },
      { label: 'no code', url: (s) => `/callback?state=${s}`, answer: (n) => jsonResponse(tokensFor(n)) },
      { label: 'an authorization error', url: (s) => `/callback?error=access_denied&state=${s}`, answer: (n) => jsonResponse(tokensFor(n)) },
      { label: 'a refused exchange', url: (s) => `/callback?code=THE-CODE&state=${s}`, answer: () => refusal() },
      {
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

    /**
     * Every failure shape goes to sign-in and leaves the target alone. The second half is the part worth having:
     * a discard that consumed the target would quietly cost the visitor their destination on the retry, and
     * nothing the visitor could see would differ.
     */
    it.each(FAULTS)('sends $label to sign-in and leaves the target where it is', async ({ url, answer }) => {
      remember(A_REAL_TARGET);
      const watch = watchStorage();
      const started = await startSignIn();

      const arrival = await arriveAt(url(started.state), () => answer(started.nonce));

      expect(arrival.router.state.location.pathname, 'the control: it really was discarded').toBe(SIGN_IN_ROUTE);
      expect(arrival.holder.accessToken.current()).toBeNull();
      expect(globalThis.sessionStorage.getItem(RETURN_TARGET_KEY)).toBe(A_REAL_TARGET);
      expect(removedKeys(watch)).not.toContain(RETURN_TARGET_KEY);
    });

    /**
     * The absent-target path on the DISCARD side, which the seven rows above cannot reach because they all
     * remember one first (Kimi Gap 3). A discard that reached for the target when there was none - a removal for
     * a key that was never written, or worse a write - would be invisible to every other case in this file.
     */
    it('leaves the store untouched when a discard had no target to begin with', async () => {
      const watch = watchStorage();
      const started = await startSignIn();

      const arrival = await arriveAt(`/callback?code=THE-CODE&state=${started.state}`, () => refusal());

      expect(arrival.router.state.location.pathname, 'the control: it really was discarded').toBe(SIGN_IN_ROUTE);
      expect(storedKeys(), 'the target was never written').not.toContain(RETURN_TARGET_KEY);
      expect(writtenKeys(watch)).not.toContain(RETURN_TARGET_KEY);
      expect(removedKeys(watch)).not.toContain(RETURN_TARGET_KEY);
    });

    /**
     * T24-D14 stated as the thing it is for. "Single-use" and "survives a failed attempt" are only in tension
     * until the use is named: the use is the ARRIVAL, not the attempt. So a visitor whose first callback was
     * refused retries and still gets where they were going - which is the entire reason to remember it.
     */
    it('and the retry then honours it', async () => {
      remember(A_REAL_TARGET);
      const first = await startSignIn();
      const refused = await arriveAt(`/callback?code=THE-CODE&state=${first.state}`, () => refusal());
      expect(refused.router.state.location.pathname, 'the control: the first attempt failed').toBe(SIGN_IN_ROUTE);
      refused.unmount();
      vi.resetModules();

      const started = await startSignIn();
      const retry = await arriveAt(`/callback?code=A-FRESH-CODE&state=${started.state}`, () =>
        jsonResponse(tokensFor(started.nonce, { access_token: SECOND_ACCESS_TOKEN })),
      );

      expect(retry.holder.accessToken.current()).toBe(SECOND_ACCESS_TOKEN);
      expect(retry.router.state.location.pathname).toBe(A_REAL_TARGET_ROUTE);
    });
  });

  describe('the allowlist is derived, not written (AC6, AC17)', () => {
    /** Forward direction: the predicate's answer for every entry in the route table is the derivation rule. */
    it.each(AUTHENTICATED_ROUTE_PATHS)('answers for %s exactly as the route table implies', (path) => {
      const hasAParameterSegment = path.split('/').some((segment) => segment.startsWith(':'));

      expect(isAllowlistedTarget(`/${path}`)).toBe(!hasAParameterSegment);
    });

    /**
     * The other direction, and the one that makes AC6 a claim rather than a tautology: a path ADDED to the route
     * table becomes legal with no second edit. The route table is replaced for this case alone, and the fresh
     * `returnTarget` instance is checked for being genuinely fresh before anything it says is believed - because
     * a mock that silently failed to apply would make this case pass against a hard-coded list.
     */
    it('admits a path added to the route table, with no edit of its own', async () => {
      vi.doMock('../app/routes', () => ({
        AUTHENTICATED_ROUTE_PATHS: ['home', 'reports'] as const,
        HOME_PATH: 'home',
      }));
      const fresh = await import('./returnTarget');

      expect(fresh.isAllowlistedTarget, 'the control: a genuinely new instance').not.toBe(isAllowlistedTarget);
      expect(fresh.isAllowlistedTarget('/reports'), 'legal in the invented table').toBe(true);
      expect(isAllowlistedTarget('/reports'), 'and not legal in the real one, which has no such route').toBe(
        false,
      );
      vi.doUnmock('../app/routes');
    });

    /**
     * T24-D5's limit, with its plain parent as the control, and the grammar rule the exclusion actually uses.
     * React Router calls a segment dynamic when it BEGINS with a colon, so a literal `time/12:30` is a static
     * path and stays legal - the exclusion matches the grammar rather than hunting for a colon anywhere (Kimi
     * I4). `invoices/:id` and `receipts/:id` are the two routes notification emails deep-link to, and this case
     * is where that cost is visible.
     */
    it('refuses a parameterised route while its plain parent stays legal (T24-D5)', async () => {
      expect(isAllowlistedTarget('/invoices/:id')).toBe(false);
      expect(isAllowlistedTarget('/receipts/:id')).toBe(false);
      expect(isAllowlistedTarget('/invoices'), 'the control: the parent is a route of its own').toBe(true);

      vi.doMock('../app/routes', () => ({
        AUTHENTICATED_ROUTE_PATHS: ['home', 'time/12:30', 'reports/:id', ':secret'] as const,
        HOME_PATH: 'home',
      }));
      const fresh = await import('./returnTarget');

      expect(fresh.isAllowlistedTarget('/time/12:30'), 'a literal colon is not a parameter').toBe(true);
      expect(fresh.isAllowlistedTarget('/reports/:id')).toBe(false);
      expect(fresh.isAllowlistedTarget('/:secret')).toBe(false);
      vi.doUnmock('../app/routes');
    });

    /** No second list: the derivation this file computed independently is exactly what the module admits. */
    it('admits exactly the derived set and nothing beside it', () => {
      const admitted = EXPECTED_ALLOWLIST.filter((target) => isAllowlistedTarget(target));

      expect(admitted).toEqual(EXPECTED_ALLOWLIST);
      expect(EXPECTED_ALLOWLIST.length, 'the control: the derivation is not empty').toBeGreaterThan(1);
      expect(EXPECTED_ALLOWLIST).toContain(`/${HOME_PATH}`);
    });

    /**
     * The scan the module's own comment promises. A module-scope read of the route table resolves to `undefined`
     * under Vite rather than throwing, and an `undefined` allowlist fails closed SILENTLY - every target
     * rejected, the application apparently fine. The synthetic violation is there because a scan that cannot
     * fail proves nothing.
     */
    it('reads the route table only inside a function, never at module scope', () => {
      expect(moduleScopeUsesOfTheRouteTable(returnTargetSource!)).toEqual([]);

      expect(
        moduleScopeUsesOfTheRouteTable(
          "import { AUTHENTICATED_ROUTE_PATHS } from '../app/routes';\nconst eager = new Set(AUTHENTICATED_ROUTE_PATHS);\n",
        ),
        'the control: the scan does catch one',
      ).toEqual(['const eager = new Set(AUTHENTICATED_ROUTE_PATHS);']);

      // The control that earned its place. A module-scope use split over two lines is indented exactly like a
      // function body, so an indentation-based scan reports nothing - which is what the first version of this
      // test did, against a mutant that made the module graph fail to load at all.
      expect(
        moduleScopeUsesOfTheRouteTable(
          'const eager = new Set(\n  AUTHENTICATED_ROUTE_PATHS.map((path) => `/${path}`),\n);\n',
        ),
        'the control: and one that hides behind an indent',
      ).toEqual(['AUTHENTICATED_ROUTE_PATHS.map((path) => `/${path}`),']);

      // And a function body, which must NOT be reported, or the scan is a prohibition on using the table at all.
      expect(
        moduleScopeUsesOfTheRouteTable(
          'function allowed(): string[] {\n  return AUTHENTICATED_ROUTE_PATHS.map((path) => path);\n}\n',
        ),
        'the control: a use inside a function is the correct shape',
      ).toEqual([]);

      // A re-export names the binding and never evaluates it, so it is not a module-scope READ (Kimi Gap 6,
      // declined as proposed). The pair below is what keeps that distinction from drifting: `export { X } from`
      // passes, and a module-scope `export const` does not - which is the shape that would actually fail
      // closed and silently.
      expect(
        moduleScopeUsesOfTheRouteTable(
          "export { AUTHENTICATED_ROUTE_PATHS } from '../app/routes';\nexport { HOME_PATH };\n",
        ),
        'a re-export reads nothing',
      ).toEqual([]);
      expect(
        moduleScopeUsesOfTheRouteTable('export const EAGER = new Set(AUTHENTICATED_ROUTE_PATHS);\n'),
        'but an exported module-scope read is still a read',
      ).toEqual(['export const EAGER = new Set(AUTHENTICATED_ROUTE_PATHS);']);
    });
  });

  describe('when the store will not cooperate (AC10, AC15, T24-D10)', () => {
    /**
     * Why `takeReturnTarget` is a total function rather than a carefully guarded one, measured here rather than
     * cited: `.then(onFulfilled, onRejected)` does NOT route a synchronous throw from `onFulfilled` to
     * `onRejected`. `CallbackScreen.tsx:132` is exactly that shape, so a throw from this module at the end of a
     * successful sign-in would reach nothing and blank the page - after the visitor has already authenticated,
     * which is the worst moment this application has to offer.
     */
    it('is guarded because the discard handler could not catch it anyway', async () => {
      const discard = vi.fn();
      let escaped: unknown;

      await Promise.resolve('a token response')
        .then(() => {
          throw new Error('thrown from the fulfilment handler');
        }, discard)
        .catch((error: unknown) => {
          escaped = error;
        });

      expect(discard, 'the second argument never sees it').not.toHaveBeenCalled();
      expect((escaped as Error).message).toBe('thrown from the fulfilment handler');
    });

    /**
     * A corrupted value is just a non-member: there is nothing to decode and nothing to repair (AC15). Each row
     * asserts the RETURN VALUE and not merely the absence of a throw, because "it did not throw" is satisfied by
     * a function that returns the empty string - and the empty string is a member of every prefix-flavoured
     * allowlist anyone might refactor this into.
     */
    it.each([
      '{}',
      '[object Object]',
      '',
      ' ',
      'null',
      'undefined',
      '/home\u0000',
      '/HOME',
      '🙂',
      // Leading and trailing space, which no `useLocation().pathname` produces and a hand-edited store can
      // (Kimi Gap 5). Nothing trims them, and nothing should: a trim is a normalisation, and the one thing the
      // membership rule buys is that there is no normalisation to get wrong.
      ' /home',
      '/home ',
    ])(
      'returns the home path for the stored value %j without throwing',
      (value) => {
        seedRaw(value);

        let returned: string | undefined;
        expect(() => {
          returned = takeReturnTarget();
        }).not.toThrow();

        expect(returned).toBe(`/${HOME_PATH}`);
        expect(globalThis.sessionStorage.getItem(RETURN_TARGET_KEY), 'and consumes it').toBeNull();
      },
    );

    /** A store whose every access throws - blocked site data, some private modes - is treated as no target. */
    it('never throws when the store itself is unreachable', () => {
      vi.stubGlobal(
        'sessionStorage',
        new Proxy(
          {},
          {
            get() {
              throw new DOMException('the store is not available', 'SecurityError');
            },
          },
        ),
      );

      expect(() => takeReturnTarget()).not.toThrow();
      expect(takeReturnTarget()).toBe(`/${HOME_PATH}`);
      expect(() => rememberReturnTarget(A_REAL_TARGET), 'and neither does the producer').not.toThrow();
    });

    /**
     * AC10 end to end: a read that throws is treated as no target and never reaches the visitor as an error. Only
     * the return key is denied, so the rest of the sign-in is untouched and the case can tell the difference
     * between "the target was unreadable" and "the sign-in broke".
     */
    it('completes the sign-in and goes home when the target cannot be read', async () => {
      remember(A_REAL_TARGET);
      const denied = denyReadOf(RETURN_TARGET_KEY);

      const arrival = await signInAndLand();

      expect(denied, 'the control: the read really was attempted').toHaveBeenCalledWith(RETURN_TARGET_KEY);
      expect(arrival.router.state.location.pathname).toBe(HOME_ROUTE);
      expect(arrival.holder.accessToken.current(), 'the sign-in itself was not harmed').toBe(ACCESS_TOKEN);
    });
  });

  describe('a visitor who left is not navigated (AC14, T24-D7)', () => {
    /**
     * Two promises that look like one and are not. T24-D7 says a visitor who has left the callback screen is not
     * moved; L16 says the target is single-use and deleted at callback. A guard around the whole block keeps the
     * first and drops the second, and the cost is invisible: the next sign-in in that tab would be sent to a
     * destination from a journey the visitor abandoned. So the target is TAKEN before the guard and the
     * navigation is what the guard withholds (Kimi I1).
     */
    it('does not navigate after the screen unmounts, and consumes the target anyway', async () => {
      remember(A_REAL_TARGET);
      const started = await startSignIn();
      const { answer, release } = deferredAnswer(() => jsonResponse(tokensFor(started.nonce)));

      const arrival = await arriveAt(`/callback?code=THE-CODE&state=${started.state}`, answer);
      expect(arrival.fetchSpy).toHaveBeenCalledTimes(1);
      const before = arrival.router.state.location.pathname;
      arrival.unmount();
      release();
      await turn();

      expect(before, 'the control: it was on the callback when they left').toBe(CALLBACK_ROUTE);
      expect(arrival.router.state.location.pathname, 'no navigation yanked out from under them').toBe(before);
      expect(arrival.holder.accessToken.current(), 'the exchange they started still completed').toBe(
        ACCESS_TOKEN,
      );
      expect(globalThis.sessionStorage.getItem(RETURN_TARGET_KEY), 'and the target was used up').toBeNull();
    });

    /**
     * StrictMode runs the effect's cleanup BETWEEN the two passes, so a mount flag set once would be false by
     * the time the exchange resolved and a StrictMode sign-in would never navigate at all - in development only,
     * with every other assertion in this file still green. The flag is therefore set on every invocation.
     */
    it('still navigates under StrictMode, where the cleanup runs between the passes', async () => {
      remember(A_REAL_TARGET);
      const started = await startSignIn();

      const arrival = await arriveAt(
        `/callback?code=THE-CODE&state=${started.state}`,
        () => jsonResponse(tokensFor(started.nonce)),
        { strict: true },
      );

      expect(arrival.fetchSpy, 'one exchange, not two').toHaveBeenCalledTimes(1);
      expect(arrival.router.state.location.pathname).toBe(A_REAL_TARGET_ROUTE);
    });
  });

  describe('nothing else enters storage (AC8)', () => {
    /**
     * The return target is the FOURTH item in L16's list and the only thing this task adds to the store. T22 owns
     * the whole-package leak sweep; this is the local claim, over a flow that remembers a target: four keys, the
     * three PKCE items and this one, and no token or code among their values.
     */
    it('adds one key and no secret to a flow that remembers a target', async () => {
      const watch = watchStorage();
      remember(A_REAL_TARGET);
      const started = await startSignIn();

      const arrival = await arriveAt(`/callback?code=THE-CODE&state=${started.state}`, () =>
        jsonResponse(tokensFor(started.nonce)),
      );

      expect(arrival.router.state.location.pathname, 'the control: a completed sign-in').toBe(A_REAL_TARGET_ROUTE);
      expect([...new Set(writtenKeys(watch))].sort()).toEqual(
        [
          RETURN_TARGET_KEY,
          AUTHORIZE_STORAGE_KEYS.state,
          AUTHORIZE_STORAGE_KEYS.nonce,
          AUTHORIZE_STORAGE_KEYS.codeVerifier,
        ].sort(),
      );
      const written = (watch.setItem.mock.calls as Array<[string, string]>).map(([, value]) => value).join('\u0000');
      expect(written).not.toContain(ACCESS_TOKEN);
      expect(written).not.toContain('THE-CODE');
      expect(storedKeys(), 'and the store is empty after it all').toEqual([]);
    });

    /**
     * T24-D15: a target that could not be honoured is not stored at all. Authority still belongs to the read -
     * the route table can change between the write and the read - but authority and filtering are not the same
     * thing, and L3 and L16 are about what may SIT in storage, not only about what may be acted on (Kimi I2).
     */
    it('does not store a target the allowlist would refuse', () => {
      const watch = watchStorage();

      rememberReturnTarget(A_TARGET_CARRYING_THE_BASENAME);

      expect(globalThis.sessionStorage.getItem(RETURN_TARGET_KEY)).toBeNull();
      expect(writtenKeys(watch), 'the store was never asked').not.toContain(RETURN_TARGET_KEY);
      rememberReturnTarget(A_REAL_TARGET);
      expect(writtenKeys(watch), 'the control: a legal one does get written').toContain(RETURN_TARGET_KEY);
    });

    /**
     * A write that throws leaves NOTHING, which is a stronger claim than "it did not throw" (Kimi Gap 2). The
     * guard exists so a storage quirk cannot end a journey before it starts; it must not also become a way for a
     * half-written value to sit in the store, because the read would then be deciding about a value no caller
     * ever chose.
     */
    it('leaves nothing behind when the write itself throws', () => {
      const denied = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
        throw new DOMException('the store is not available', 'SecurityError');
      });

      expect(() => rememberReturnTarget(A_REAL_TARGET)).not.toThrow();

      expect(denied, 'the control: the write really was attempted').toHaveBeenCalledWith(
        RETURN_TARGET_KEY,
        A_REAL_TARGET,
      );
      denied.mockRestore();
      expect(globalThis.sessionStorage.getItem(RETURN_TARGET_KEY)).toBeNull();
    });

    /**
     * The second call wins (Kimi Gap 4). `setItem` overwrites by definition, so this is not a test of the
     * platform - it is the contract T41 depends on when a visitor abandons one journey and begins another in the
     * same tab, and the reason the retry case above cannot pass by accident for using the same target twice.
     */
    it('replaces an earlier target rather than keeping or appending to it', () => {
      rememberReturnTarget('/account');
      rememberReturnTarget('/api-keys');

      expect(takeReturnTarget()).toBe('/api-keys');
      expect(globalThis.sessionStorage.getItem(RETURN_TARGET_KEY), 'and only one was ever stored').toBeNull();
    });

    /** The round trip, through both real functions, which is the contract T41 will call. */
    it('round-trips a legal target through the producer and the consumer', () => {
      rememberReturnTarget(A_REAL_TARGET);

      expect(takeReturnTarget()).toBe(A_REAL_TARGET);
    });
  });

  /**
   * T25's half (F7). The rejection families, one row each, deliberately left as `it.todo` so that the partition
   * is visible in the source: T24 built the mechanism and proved the honoured path, T25 proves "never an
   * arbitrary URL" family by family. Every row below is a target that must land on home, and none of them is
   * special-cased in the implementation - set membership refuses them all for the same reason, which is what
   * makes the table worth writing.
   */
  describe('a target outside the allowlist falls back to home (T25)', () => {
    it.todo('an absolute URL on another origin');
    it.todo('an absolute URL on this origin');
    it.todo('a protocol-relative host');
    it.todo('a javascript: target');
    it.todo('a data: target');
    it.todo('a backslash path');
    it.todo('a traversal');
    it.todo('an encoded traversal');
    it.todo('a prefix match of an allowlisted path');
    it.todo('a public route');
    it.todo('a route that does not exist');
    it.todo('a parameterised route with a real id substituted (T24-D5)');
    it.todo('an allowlisted path carrying a query string (T24-D6)');
    it.todo('an allowlisted path carrying a fragment (T24-D6)');
    it.todo('and every row above leaves the visitor with the same copy as an honoured target (AC9)');
  });
});
