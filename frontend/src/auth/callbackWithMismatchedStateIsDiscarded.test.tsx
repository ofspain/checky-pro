/// <reference types="node" />
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { act, StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { APP_BASENAME, appRoutes, CALLBACK_PATH, PUBLIC_ROUTE_PATHS, SIGN_IN_PATH } from '../app/routes';
import { en } from '../i18n/catalogue';
import { createI18n } from '../i18n/i18n';
import { AUTHORIZE_STORAGE_KEYS } from './authorizeRequest';
import { randomUrlSafeToken } from './pkce';
import { setSignInNotice, takeSignInNotice } from './signInNotice';

/**
 * T17 - a callback that arrives with no `state` is discarded, nothing is exchanged, and the visitor is returned
 * to the sign-in start with neutral copy (R8). **Task 18 extends this file** with the mismatch cases; the two
 * tasks share one named test in `package.md` §8.
 *
 * The assertion this task exists to make - "no exchange happened" - has nothing to observe on its own, because
 * the exchange is task 20's and does not exist yet. It would pass against an empty file and keep passing once
 * task 20 wired an exchange that fired unconditionally. So `codeExchange.ts` is a real collaborator with a real
 * call site, every negative case spies on it, and one POSITIVE case drives the path that does call it. Without
 * that positive case the whole arrangement would be decorative.
 *
 * The seam's grant type is expected to grow - task 20 needs the stored verifier at least - so calls are asserted
 * with `objectContaining` and never by exact shape.
 */

// Lets act() flush React 19 renders synchronously in this test environment.
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

type Mounted = { root: Root; container: HTMLElement; router: { dispose(): void } };

let mounted: Mounted | undefined;

/** One macrotask turn inside act: the seam is async, and act alone does not await work leaving the microtask queue. */
async function turn(): Promise<void> {
  await act(async () => {
    await new Promise((resolve) => {
      setTimeout(resolve, 0);
    });
  });
}

type Rendered = { container: HTMLElement; router: ReturnType<typeof createMemoryRouter> };

/** Drives the real route table from a given URL, the way the browser would arrive from the authorization server. */
async function arriveAt(url: string, options: { strict?: boolean } = {}): Promise<Rendered> {
  const router = createMemoryRouter(appRoutes, { basename: APP_BASENAME, initialEntries: [`${APP_BASENAME}${url}`] });
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

/** Replaces the seam with a spy. Returns the spy and the freshly imported screen's route table. */
async function spyOnExchange(options: { rejects?: boolean } = {}) {
  const calls: unknown[] = [];
  vi.doMock('./codeExchange', async () => {
    const actual = await vi.importActual<typeof import('./codeExchange')>('./codeExchange');
    return {
      ...actual,
      exchangeAuthorizationCode: async (grant: unknown) => {
        calls.push(grant);
        // What task 20's network call will do on a 400, a revoked verifier or an offline tab.
        if (options.rejects === true) throw new Error('the token endpoint refused the grant');
      },
    };
  });
  const routes = await import('../app/routes');
  return { calls, routes };
}

/** Same as arriveAt, but against a route table imported after the seam was mocked. */
async function arriveAtMocked(
  routes: typeof import('../app/routes'),
  url: string,
  options: { strict?: boolean } = {},
): Promise<Rendered> {
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

/**
 * The scan AC12 rests on: every use of `name` that is not a comment or an import must be indented, which at
 * these files' top level means it sits inside a block. Extracted from the assertion so the scan itself can be
 * fed a known violation - it is the only thing in this suite that can catch the TDZ defect, so an unproven
 * regex here would be a silent hole rather than a weak test.
 */
function moduleScopeUses(source: string, name: string): string[] {
  return [...source.matchAll(new RegExp(String.raw`^(\s*).*\b${name}\b.*$`, 'gm'))]
    .filter((match) => !match[0].trimStart().startsWith('*') && !match[0].trimStart().startsWith('//'))
    .filter((match) => !match[0].startsWith('import'))
    .filter((match) => match[1] === '')
    .map((match) => match[0].trim());
}

/**
 * The state that makes a callback reach the exchange. Since T18 the success path needs TWO things to agree: the
 * value in the URL and the value T15 stored. Every case that wants the exchange reached must seed the stored
 * side, which is why this is a named pair rather than a bare literal in each arrival.
 */
const MATCHING_STATE = 'xyz';

/** Seeds all three L16 items, so a no-read assertion is about restraint rather than about an empty store. */
function seedAllThree(): void {
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.state, 'stored-state');
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.nonce, 'stored-nonce');
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.codeVerifier, 'stored-verifier');
}

/** Writes the stored side of the comparison. The test writes it directly - see T18-D7 for why. */
function storeState(value: string): void {
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.state, value);
}

/** Seeds the two items T18 must never read, leaving storeState the only writer of the state key. */
function seedNonceAndVerifier(): void {
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.nonce, 'stored-nonce');
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.codeVerifier, 'stored-verifier');
}

/**
 * A callback arrival, with the state encoded. Encoding by default so a row carrying `&`, `#` or `=` reshapes
 * the VALUE and not the query - a row like that would otherwise appear to pass for the wrong reason. Cases
 * that deliberately exercise percent-encoded whitespace pass their URL literally instead.
 */
function arrivalWith(state: string): string {
  return `/callback?code=abc&state=${encodeURIComponent(state)}`;
}

/**
 * The teardown two multi-arrival cases need between their arrivals. One definition, so it cannot drift out of
 * step with afterEach the way an inlined copy would.
 */
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

/** The three URL shapes R8's "missing" antecedent covers, plus the no-query case a bookmark produces. */
const DISCARD_SHAPES: readonly [string, string][] = [
  ['no state parameter at all', '/callback?code=abc'],
  ['an empty state parameter', '/callback?code=abc&state='],
  ['no query string at all', '/callback'],
  ['a state but no code', '/callback?state=xyz'],
  ['a state of nothing but whitespace', '/callback?code=abc&state=%20%20'],
  ['a code of nothing but whitespace', '/callback?state=xyz&code=%09'],
];

describe('callbackWithMismatchedStateIsDiscarded', () => {
  let callbackSource: string;
  let noticeSource: string;
  let exchangeSource: string;

  beforeEach(async () => {
    globalThis.sessionStorage.clear();
    globalThis.localStorage.clear();
    const here = dirname(expect.getState().testPath as string);
    callbackSource ??= await readFile(join(here, 'CallbackScreen.tsx'), 'utf8');
    noticeSource ??= await readFile(join(here, 'signInNotice.ts'), 'utf8');
    exchangeSource ??= await readFile(join(here, 'codeExchange.ts'), 'utf8');
  });

  afterEach(() => {
    if (mounted) {
      const { root, container, router } = mounted;
      act(() => root.unmount());
      container.remove();
      router.dispose();
      mounted = undefined;
    }
    // Unconditionally, because signInNotice holds module state and Vitest reuses the module graph: a case that
    // sets a notice without rendering the sign-in screen would otherwise leak it into the next one.
    takeSignInNotice();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    vi.resetModules();
    vi.doUnmock('./codeExchange');
  });

  describe('a callback with no usable state is discarded (AC1, AC2, AC3)', () => {
    it.each(DISCARD_SHAPES)('returns the visitor to the sign-in start: %s', async (_label, url) => {
      const { router } = await arriveAt(url);

      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
    });

    it.each(DISCARD_SHAPES)('shows the same neutral copy: %s', async (_label, url) => {
      const { container } = await arriveAt(url);

      expect(container.textContent ?? '').toContain(en.signIn.discarded);
    });

    // The copy must be identical across every discard shape, or it would tell the visitor which case occurred,
    // which L4 forbids. Compared shape-to-shape rather than each to the catalogue.
    it('renders text that does not vary between the discard shapes (L4)', async () => {
      const texts: string[] = [];
      for (const [, url] of DISCARD_SHAPES) {
        const { container } = await arriveAt(url);
        texts.push(container.textContent ?? '');
        const { root, container: node, router } = mounted as Mounted;
        act(() => root.unmount());
        node.remove();
        router.dispose();
        mounted = undefined;
        takeSignInNotice();
      }
      expect(new Set(texts).size).toBe(1);
    });
  });

  describe('nothing is exchanged (AC4)', () => {
    it.each(DISCARD_SHAPES)('the seam is not called: %s', async (_label, url) => {
      const { calls, routes } = await spyOnExchange();
      await arriveAtMocked(routes, url);

      expect(calls).toEqual([]);
    });

    // The positive control. Without it every assertion above would hold for a screen that can never exchange at
    // all, and the negative cases would be about absent code rather than about a collaborator that was not used.
    it('but the seam IS reached when the callback carries a state and a code (AC4, AC14)', async () => {
      storeState(MATCHING_STATE);
      const { calls, routes } = await spyOnExchange();
      await arriveAtMocked(routes, '/callback?code=abc&state=xyz');

      expect(calls).toHaveLength(1);
      // objectContaining, never exact: task 20 will add the stored verifier and more to this grant, and that
      // must not break a T17 test.
      expect(calls[0]).toEqual(expect.objectContaining({ code: 'abc', state: 'xyz' }));
    });

    // The seam cannot reject today, so this case is about the handler rather than about the seam: without the
    // .catch the visitor would sit on the holding screen for good once task 20 fills the body with network I/O,
    // and nothing in this suite would have noticed the regression landing there.
    it('returns the visitor to the start when the exchange rejects (F1)', async () => {
      globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.codeVerifier, 'stored-verifier');
      storeState(MATCHING_STATE);
      const { calls, routes } = await spyOnExchange({ rejects: true });
      const { router, container } = await arriveAtMocked(routes, '/callback?code=abc&state=xyz');
      await turn();

      expect(calls).toHaveLength(1);
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      expect(container.textContent ?? '').toContain(en.signIn.discarded);
      // A failed exchange is not task 23's deletion either.
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.codeVerifier)).toBe('stored-verifier');
    });

    // The effect's dependency list is empty on purpose. This pins the behaviour that makes that correct, so a
    // later edit cannot reintroduce `params` as a dependency and quietly exchange a second grant.
    it('handles one callback per mount, ignoring a second set of parameters (F2)', async () => {
      storeState(MATCHING_STATE);
      const { calls, routes } = await spyOnExchange();
      const { router } = await arriveAtMocked(routes, '/callback?code=abc&state=xyz');
      expect(calls).toHaveLength(1);

      await act(async () => {
        await router.navigate('/callback?code=second&state=second');
      });
      await turn();

      expect(calls).toHaveLength(1);
      expect(calls[0]).toEqual(expect.objectContaining({ code: 'abc', state: 'xyz' }));
    });

    it('and the reached callback stays put rather than returning to sign-in', async () => {
      storeState(MATCHING_STATE);
      const { routes } = await spyOnExchange();
      const { router, container } = await arriveAtMocked(routes, '/callback?code=abc&state=xyz');

      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/callback`);
      expect(container.textContent ?? '').toContain(en.callback.working);
    });
  });

  describe('the return navigation (AC5)', () => {
    it('replaces rather than pushes, so the back button cannot revisit the dead callback', async () => {
      const { router } = await arriveAt('/callback?code=abc');

      expect(router.state.historyAction).toBe('REPLACE');
    });

    // Measured, not assumed: under basename /app a leading-slash target resolves to /app/sign-in, while a bare
    // relative one resolves to /app/callback/sign-in and renders the catch-all - silently, with no error and no
    // 404. The exact pathname is the assertion for that reason.
    it('lands on the exact path, not merely on something that is not the callback', async () => {
      const { router } = await arriveAt('/callback?code=abc');

      expect(router.state.location.pathname).toBe('/app/sign-in');
      expect(router.state.location.pathname).not.toBe('/sign-in');
      expect(router.state.location.pathname).not.toContain('/callback/');
    });

    // Privacy: an authorization code carried onto the sign-in URL would sit in browser history, and in any
    // referrer the sign-in page later sends. The pathname assertions above would not notice.
    it.each(DISCARD_SHAPES)('carries no part of the callback query onward: %s', async (_label, url) => {
      const { router } = await arriveAt(url);

      expect(router.state.location.search).toBe('');
      expect(router.state.location.hash).toBe('');
    });

    // Against hard-coded literals, deliberately. PUBLIC_ROUTE_PATHS is now built FROM these two constants, so
    // asserting the list contains them would compare each constant with itself - the defect this suite has
    // caught seven times in other tasks. What is actually at stake is external: auth registers the SPA's
    // redirect URI as /app/callback, measured against the live stack, so both spellings are a contract with
    // another service and renaming either breaks the deployed flow.
    it('keeps the two path spellings the authorization server has registered (AC17)', () => {
      expect(SIGN_IN_PATH).toBe('sign-in');
      expect(CALLBACK_PATH).toBe('callback');
      expect([...PUBLIC_ROUTE_PATHS]).toContain('callback');
      expect([...PUBLIC_ROUTE_PATHS]).toContain('sign-in');
    });
  });

  describe('session storage is left exactly as T15 wrote it (AC7, AC8)', () => {
    it('adds no key, and does not consume the three items', async () => {
      globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.state, 'stored-state');
      globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.nonce, 'stored-nonce');
      globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.codeVerifier, 'stored-verifier');
      const before = Object.keys(globalThis.sessionStorage).sort();

      await arriveAt('/callback?code=abc');

      // Deleting them is task 23's, by name. Pinned here so task 23 inherits a stated position rather than a
      // surprise, and so a discarded callback is distinguishable from one that arrived with no attempt at all.
      expect(Object.keys(globalThis.sessionStorage).sort()).toEqual(before);
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.codeVerifier)).toBe('stored-verifier');
    });

    it('writes nothing at all while handling a callback', async () => {
      const setItem = vi.spyOn(Storage.prototype, 'setItem');
      const removeItem = vi.spyOn(Storage.prototype, 'removeItem');
      const clear = vi.spyOn(Storage.prototype, 'clear');

      await arriveAt('/callback?code=abc');

      expect(setItem).not.toHaveBeenCalled();
      expect(removeItem).not.toHaveBeenCalled();
      expect(clear).not.toHaveBeenCalled();
    });
  });

  describe('the notice is in memory and single-read (AC16)', () => {
    it('is consumed by the first sign-in screen and not by a second', async () => {
      const first = await arriveAt('/callback?code=abc');
      expect(first.container.textContent ?? '').toContain(en.signIn.discarded);

      const { root, container, router } = mounted as Mounted;
      act(() => root.unmount());
      container.remove();
      router.dispose();
      mounted = undefined;

      // Arriving at sign-in directly, the way a visitor would after the notice has been shown once.
      const second = await arriveAt(`/${SIGN_IN_PATH}`);
      expect(second.container.textContent ?? '').not.toContain(en.signIn.discarded);
      expect(second.container.textContent ?? '').toContain(en.signIn.heading);
    });

    it('is absent for a visitor who simply opens the sign-in screen', async () => {
      const { container } = await arriveAt(`/${SIGN_IN_PATH}`);

      expect(container.textContent ?? '').not.toContain(en.signIn.discarded);
    });
  });

  describe('it handles the callback once, under StrictMode (AC14, D-8)', () => {
    it('reaches the seam exactly once although the effect is invoked twice', async () => {
      storeState(MATCHING_STATE);
      const { calls, routes } = await spyOnExchange();
      await arriveAtMocked(routes, '/callback?code=abc&state=xyz', { strict: true });

      expect(calls).toHaveLength(1);
    });

    it('does not reach the seam on the discard path under StrictMode either (F4)', async () => {
      const { calls, routes } = await spyOnExchange();
      await arriveAtMocked(routes, '/callback?code=abc', { strict: true });

      expect(calls).toEqual([]);
    });

    it('navigates once on the discard path under StrictMode too', async () => {
      const { router } = await arriveAt('/callback?code=abc', { strict: true });

      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      expect(router.state.historyAction).toBe('REPLACE');
    });
  });

  describe('accessibility and copy (AC9, AC10)', () => {
    it('gives the callback screen one h1, one main and one live region', async () => {
      const { routes } = await spyOnExchange();
      const { container } = await arriveAtMocked(routes, '/callback?code=abc&state=xyz');

      expect(container.querySelectorAll('h1')).toHaveLength(1);
      expect(container.querySelectorAll('main')).toHaveLength(1);
      expect(container.querySelectorAll('[role="status"]')).toHaveLength(1);
      expect(container.querySelector('[role="status"]')?.getAttribute('aria-live')).toBe('polite');
    });

    it('leaves the landed sign-in screen with one h1, one main and one live region', async () => {
      const { container } = await arriveAt('/callback?code=abc');

      expect(container.querySelectorAll('h1')).toHaveLength(1);
      expect(container.querySelectorAll('main')).toHaveLength(1);
      expect(container.querySelectorAll('[role="status"]')).toHaveLength(1);
    });

    it('renders no credential input on either screen', async () => {
      const { container } = await arriveAt('/callback?code=abc');

      expect(container.querySelector('input')).toBeNull();
      expect(container.querySelector('form')).toBeNull();
    });

    // The inverted scan below proves the screen does not inline the copy. It would pass just as well against
    // keys whose values were empty, where i18next renders nothing and the live region is silent.
    it('has real wording behind every key the callback screen renders', () => {
      for (const [key, value] of Object.entries(en.callback)) {
        expect(value, key).toBeTruthy();
        expect(value.trim().length, key).toBeGreaterThan(0);
      }
      expect(en.signIn.discarded.trim().length).toBeGreaterThan(0);
    });

    it('holds none of the catalogue values as a literal in its own source', () => {
      const values = [...Object.values(en.callback), en.signIn.discarded];
      expect(values.length).toBeGreaterThanOrEqual(3);
      expect(values.filter((value) => callbackSource.includes(value))).toEqual([]);
    });

    // The wording is the owner's; the properties are not. Neither screen may name a cause, a credential type or
    // an account state (R8's "neutral", L4).
    it('says nothing about a cause, a credential or an account state', () => {
      const copy = [...Object.values(en.callback), en.signIn.discarded].join(' ').toLowerCase();
      for (const forbidden of ['password', 'token', 'state', 'parameter', 'expired', 'invalid', 'forged']) {
        expect(copy, forbidden).not.toContain(forbidden);
      }
      for (const forbidden of ['account', 'locked', 'suspended', 'not found', 'no such']) {
        expect(copy, forbidden).not.toContain(forbidden);
      }
      // The matcher has reach.
      expect('Your state parameter was invalid'.toLowerCase()).toContain('parameter');
    });
  });

  describe('the new files touch nothing they must not (AC15)', () => {
    // Each pattern carries its own negative control, so inserting a row cannot misalign the two lists.
    const forbidden: readonly { label: string; pattern: RegExp; violation: string }[] = [
      { label: 'localStorage', pattern: /\blocalStorage\b/, violation: 'localStorage.setItem("k", "v");' },
      { label: 'history.state', pattern: /\bhistory\s*\.\s*state\b/, violation: 'const s = history.state;' },
      {
        label: 'a sessionStorage write',
        pattern: /sessionStorage\s*\.\s*setItem/,
        violation: 'sessionStorage.setItem("k", "v");',
      },
      {
        label: 'an absolute address',
        pattern: /https?:\/\//,
        violation: 'const docs = "https://example.test/docs";',
      },
      {
        label: 'the compile-time environment object',
        pattern: /import\.meta\s*\.\s*env/,
        violation: 'const mode = import.meta.env.MODE;',
      },
    ];

    it.each(forbidden)('CallbackScreen.tsx contains no $label', ({ pattern }) => {
      expect(callbackSource).not.toMatch(pattern);
    });

    it.each(forbidden)('signInNotice.ts contains no $label', ({ pattern }) => {
      expect(noticeSource).not.toMatch(pattern);
    });

    it.each(forbidden)('codeExchange.ts contains no $label', ({ pattern }) => {
      expect(exchangeSource).not.toMatch(pattern);
    });

    // Without this the five patterns could all be broken and every row above would still pass.
    it('and each pattern finds its own violation when one is present (AC14)', () => {
      for (const { label, pattern, violation } of forbidden) {
        expect(violation, label).toMatch(pattern);
      }
    });
  });

  // T16 closed one cycle: routes -> SignInScreen -> authorizeRequest -> routes. T17 adds a SECOND strand,
  // routes -> CallbackScreen -> routes, because the screen imports SIGN_IN_PATH to know where to send a
  // discarded callback. routes.tsx imports CallbackScreen (line 6) BEFORE it declares SIGN_IN_PATH (line 13),
  // so a module-scope read here would touch the binding inside its temporal dead zone. Measured in T16: Vite
  // yields `undefined` rather than throwing, so the target would silently become '/undefined' and land on the
  // catch-all. T16's guard covers authorizeRequest only; this covers the strand T17 introduced.
  describe('the second strand of the circular import stays safe (AC12)', () => {
    it('reads SIGN_IN_PATH only inside a function body, never at module scope', () => {
      // Non-vacuity: the screen must actually use the name, or an empty result would prove nothing.
      expect(callbackSource).toContain('SIGN_IN_PATH');

      expect(moduleScopeUses(callbackSource, 'SIGN_IN_PATH')).toEqual([]);
    });

    // The scan is the ONLY thing that catches this defect class: hoisting the read fails nothing else in the
    // suite, because Vitest's module resolution does not reproduce the TDZ failure Vite produces. So the scan
    // is itself fed a violation, the same way every other matcher here is.
    it('and the scan detects a module-scope use when one is present', () => {
      const hoisted = [
        "import { SIGN_IN_PATH } from '../app/routes';",
        '// SIGN_IN_PATH in a comment must not count',
        ' * SIGN_IN_PATH in a doc block must not count',
        'const DISCARD_TARGET = "/" + SIGN_IN_PATH;',
        'function f() {',
        '  const fine = SIGN_IN_PATH;',
        '}',
      ].join('\n');

      expect(moduleScopeUses(hoisted, 'SIGN_IN_PATH')).toEqual(['const DISCARD_TARGET = "/" + SIGN_IN_PATH;']);
    });

    it('resolves under the order the application actually loads it in', async () => {
      // routes.tsx is what the shell imports first, which is the order that puts CallbackScreen in the middle
      // of the cycle. If the dead zone were ever touched this import would throw, or SIGN_IN_PATH would arrive
      // undefined and the navigation target below would read '/undefined'.
      const routes = await import('../app/routes');

      expect(routes.SIGN_IN_PATH).toBe('sign-in');
      expect(`/${routes.SIGN_IN_PATH}`).not.toContain('undefined');
    });
  });

  describe('the notice drains exactly once, as a unit (AC16, D-3)', () => {
    // The rendered cases above prove the screen consumes it. This proves the primitive itself, which is what
    // D-3 asked for: a second take must not re-deliver a notice the first take already returned.
    it('returns the notice once and null afterwards', () => {
      setSignInNotice('discarded');

      expect(takeSignInNotice()).toBe('discarded');
      expect(takeSignInNotice()).toBeNull();
      expect(takeSignInNotice()).toBeNull();
    });

    it('returns null when nothing set one', () => {
      expect(takeSignInNotice()).toBeNull();
    });
  });

  describe('the shapes a real authorization server can send (R8 boundaries)', () => {
    // The documented OAuth failure response: the server returns `error` INSTEAD of `code`, keeping `state`.
    // T17 discards it for want of a code, which is the right outcome, and this records that it is deliberate
    // rather than a side effect - task 19 owns showing anything about the error itself.
    it('discards an authorization error response, which carries a state but no code', async () => {
      const { calls, routes } = await spyOnExchange();
      const { router, container } = await arriveAtMocked(
        routes,
        '/callback?error=access_denied&error_description=user%20said%20no&state=xyz',
      );

      expect(calls).toEqual([]);
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      expect(container.textContent ?? '').toContain(en.signIn.discarded);
      // Neutral: the server's own error_description must not reach the visitor (L4).
      expect(container.textContent ?? '').not.toContain('user said no');
      expect(container.textContent ?? '').not.toContain('access_denied');
    });

    // The trim must apply on the way THROUGH, not only in the discard test. A regression that trimmed only
    // for the emptiness check would hand task 20 a padded code the authorization server never issued.
    it('trims padding off a usable code and state before the exchange sees them', async () => {
      storeState(MATCHING_STATE);
      const { calls, routes } = await spyOnExchange();
      await arriveAtMocked(routes, '/callback?code=%20abc%20&state=%09xyz%0A');

      expect(calls).toHaveLength(1);
      expect(calls[0]).toEqual(expect.objectContaining({ code: 'abc', state: 'xyz' }));
    });

    // Both `error` and `code` is malformed: the server sends one or the other. Locked as a discard (D-12).
    it('discards a response carrying BOTH an error and a code', async () => {
      const { calls, routes } = await spyOnExchange();
      const { router } = await arriveAtMocked(routes, '/callback?error=access_denied&code=abc&state=xyz');

      expect(calls).toEqual([]);
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
    });

    // T18-D4 split this in two rather than loosening it. The boundary is still guarded for tasks 19 and 20:
    // what changed is that reaching the COMPARISON now reads one key, so the split is by whether the effect got
    // that far - not by whether anything was found, since discovering that nothing is stored IS the read.
    it.each(DISCARD_SHAPES)('reads no stored key at all when it returns early: %s', async (_label, url) => {
      seedAllThree();
      const getItem = vi.spyOn(Storage.prototype, 'getItem');
      const setItem = vi.spyOn(Storage.prototype, 'setItem');
      const { calls, routes } = await spyOnExchange();
      await arriveAtMocked(routes, url);

      expect(calls).toEqual([]);
      const read = getItem.mock.calls.map(([key]) => key);
      for (const key of Object.values(AUTHORIZE_STORAGE_KEYS)) expect(read, key).not.toContain(key);
      expect(setItem).not.toHaveBeenCalled();
      // Survival, not just restraint: nothing here may pre-empt task 23's deletion either.
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state)).toBe('stored-state');
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.nonce)).toBe('stored-nonce');
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.codeVerifier)).toBe('stored-verifier');
    });

    // Reaching the comparison reads the state and NOTHING else - identically whether it matches or not, because
    // the read happens before the outcome is known. The nonce is task 19's and the verifier task 20's.
    it.each([
      ['a match', MATCHING_STATE],
      ['a mismatch', 'a-different-stored-value'],
    ])('reads the state key and no other once the comparison is reached: %s', async (_label, storedValue) => {
      seedNonceAndVerifier();
      storeState(storedValue);
      const getItem = vi.spyOn(Storage.prototype, 'getItem');
      const setItem = vi.spyOn(Storage.prototype, 'setItem');
      const { routes } = await spyOnExchange();
      await arriveAtMocked(routes, `/callback?code=abc&state=${MATCHING_STATE}`);

      const read = getItem.mock.calls.map(([key]) => key);
      expect(read).toContain(AUTHORIZE_STORAGE_KEYS.state);
      expect(read).not.toContain(AUTHORIZE_STORAGE_KEYS.nonce);
      expect(read).not.toContain(AUTHORIZE_STORAGE_KEYS.codeVerifier);
      expect(setItem).not.toHaveBeenCalled();
    });

    // A duplicated security parameter is a parameter-pollution shape. URLSearchParams.get returns the FIRST,
    // so that is what the exchange receives and what task 18 will compare against the stored value.
    it('uses the first state when the query carries two', async () => {
      storeState('first');
      const { calls, routes } = await spyOnExchange();
      await arriveAtMocked(routes, '/callback?code=abc&state=first&state=second');

      expect(calls).toHaveLength(1);
      expect(calls[0]).toEqual(expect.objectContaining({ state: 'first' }));
    });
  });

  // ----- T18: a state that does not match the stored value (R8's other half) -----

  /**
   * Every pair here is a discard. The stored side is written by the test, which is the only way to produce the
   * case-differing, padded, prefix and suffix shapes: T15's writer emits a 43-character base64url token and
   * nothing else (T18-D7). Short literals on purpose - replicating the production length would couple this to
   * T15's implementation and prove nothing extra.
   */
  const MISMATCH_SHAPES: readonly [string, string | null, string][] = [
    ['a wholly different stored value', 'stored-state', 'xyz'],
    ['nothing stored at all', null, 'xyz'],
    ['an empty stored value', '', 'xyz'],
    ['one character different', 'abc', 'abd'],
    ['a difference of case only', 'abc', 'ABC'],
    ['the stored value a prefix of the supplied one', 'abc', 'abcd'],
    ['the supplied value a prefix of the stored one', 'abcd', 'abc'],
    // Only this direction belongs here. The inverse - stored 'abc', supplied ' abc ' - is a MATCH by design,
    // because T17 trims the supplied value before the comparison ever runs; it is covered as a success case by
    // 'trims padding off a usable code and state'. Adding it as a mismatch row would assert the opposite.
    ['the stored value padded with whitespace', ' abc ', 'abc'],
    ['the supplied value a suffix of the stored one', 'abc', 'bc'],
  ];

  describe('a callback whose state does not match the stored value is discarded (AC1, AC2, AC3, AC6)', () => {
    it.each(MISMATCH_SHAPES)('does not exchange: %s', async (_label, storedValue, supplied) => {
      if (storedValue !== null) storeState(storedValue);
      const { calls, routes } = await spyOnExchange();
      await arriveAtMocked(routes, arrivalWith(supplied));

      expect(calls).toEqual([]);
    });

    it.each(MISMATCH_SHAPES)('returns the visitor to the sign-in start: %s', async (_label, storedValue, supplied) => {
      if (storedValue !== null) storeState(storedValue);
      const { container } = await arriveAt(arrivalWith(supplied));

      expect(container.textContent ?? '').toContain(en.signIn.discarded);
    });

    // The comparison is exact (T18-D3). A mutant that trims the stored value, folds case, or matches on a
    // prefix would turn one of these rows into an exchange.
    it.each(MISMATCH_SHAPES)('lands exactly on the sign-in route: %s', async (_label, storedValue, supplied) => {
      if (storedValue !== null) storeState(storedValue);
      const { router } = await arriveAt(arrivalWith(supplied));

      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      expect(router.state.location.search).toBe('');
    });
  });

  describe('an empty state never exchanges, whichever guard catches it (AC3, defence in depth)', () => {
    /**
     * Found by measurement, not by review. Relaxing T17's empty-`state` guard AND folding away the explicit
     * `stored === ''` test is caught by only two cases in this file, and by no assertion about the OUTCOME -
     * because with nothing stored, `null !== ''` still discards. The uncovered pair is an empty state on BOTH
     * sides: `'' !== ''` is false, so the comparison would pass and the seam would be reached.
     *
     * These assert the security property rather than the path, so they hold whichever guard fires and would
     * survive a refactor that moved the responsibility between them.
     */
    it.each([
      ['both sides empty', '', '/callback?code=abc&state='],
      ['both sides whitespace', '   ', '/callback?code=abc&state=%20%20%20'],
      ['stored empty, supplied whitespace', '', '/callback?code=abc&state=%09'],
    ])('does not exchange when the state is empty: %s', async (_label, storedValue, url) => {
      storeState(storedValue);
      const { calls, routes } = await spyOnExchange();
      const { router } = await arriveAtMocked(routes, url);

      expect(calls).toEqual([]);
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
    });
  });

  describe('the error check still precedes the comparison (D-12 ordering)', () => {
    // The early-return no-read sweep runs over DISCARD_SHAPES, which carries no `error` row - so until now
    // nothing proved an error response short-circuits BEFORE the state is consulted. With a matching stored
    // state in place, a regression that reordered the two guards would exchange an error response.
    it.each([
      ['an error with a matching code and state', '/callback?error=access_denied&code=abc&state='],
      ['an error alone', '/callback?error=access_denied&state='],
    ])('discards without reading the stored state: %s', async (_label, prefix) => {
      seedNonceAndVerifier();
      storeState(MATCHING_STATE);
      const getItem = vi.spyOn(Storage.prototype, 'getItem');
      const { calls, routes } = await spyOnExchange();
      const { router } = await arriveAtMocked(routes, `${prefix}${MATCHING_STATE}`);

      expect(calls).toEqual([]);
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      const read = getItem.mock.calls.map(([key]) => key);
      expect(read).not.toContain(AUTHORIZE_STORAGE_KEYS.state);
    });
  });

  describe('the comparison holds at the production shape and through decoding (Gaps 3, 5)', () => {
    // Generated, not a hardcoded 43-character literal: T18-D7 kept the production length out of the fixtures
    // to avoid coupling them to T15, and using the real generator gets the shape without the coupling.
    it('matches a real randomUrlSafeToken end to end', async () => {
      const token = randomUrlSafeToken();
      expect(token.length).toBeGreaterThan(20);
      storeState(token);
      const { calls, routes } = await spyOnExchange();
      await arriveAtMocked(routes, arrivalWith(token));

      expect(calls).toHaveLength(1);
      expect(calls[0]).toEqual(expect.objectContaining({ state: token }));
    });

    /**
     * Kimi left the expected behaviour of a literal percent-encoded state open. It is a MATCH, and deliberately
     * so: URLSearchParams decodes `xyz%20` to `'xyz '`, and T17 trims the SUPPLIED value because a padded query
     * is a client-side artefact (its F3). The stored value is still never trimmed - that asymmetry is T18-D3,
     * and the mismatch table's padded row pins the other direction.
     */
    it('treats a trailing percent-encoded space in the url as padding, and still matches', async () => {
      storeState('xyz');
      const { calls, routes } = await spyOnExchange();
      await arriveAtMocked(routes, '/callback?code=abc&state=xyz%20');

      expect(calls).toHaveLength(1);
      expect(calls[0]).toEqual(expect.objectContaining({ state: 'xyz' }));
    });

    it('but does not match when the padding is in the STORED value', async () => {
      storeState('xyz ');
      const { calls, routes } = await spyOnExchange();
      const { router } = await arriveAtMocked(routes, '/callback?code=abc&state=xyz%20');

      expect(calls).toEqual([]);
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
    });
  });

  describe('OQ-2: the stored state is compared but not consumed (task 23 will change this)', () => {
    /**
     * A TRIPWIRE, deliberately, in the shape T17 used for the storage boundary. L16 calls the four items
     * "single-use and deleted at callback", but D-7 placed deletion with task 23 by name, so until then the
     * same callback URL compares equal every time it arrives.
     *
     * Largely defused by the backend - an authorization code is single-use at the token endpoint, and until
     * task 20 the exchange does nothing at all - but it is real, and this pins it so task 23 inherits a stated
     * position and a failing test rather than a surprise. **Task 23 is expected to invert this case.**
     */
    it('lets the same callback match twice, because deletion is task 23\'s', async () => {
      const url = arrivalWith(MATCHING_STATE);

      seedNonceAndVerifier();
      storeState(MATCHING_STATE);
      const first = await spyOnExchange();
      await arriveAtMocked(first.routes, url);
      expect(first.calls).toHaveLength(1);

      resetBetweenArrivals();

      // Nothing re-seeds storage here: the point is that the first arrival left it intact.
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state)).toBe(MATCHING_STATE);
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.nonce)).toBe('stored-nonce');
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.codeVerifier)).toBe('stored-verifier');
      const second = await spyOnExchange();
      await arriveAtMocked(second.routes, url);
      expect(second.calls, 'task 23 will make this an empty array').toHaveLength(1);
    });
  });

  describe('storage denial does not strand the visitor (I1)', () => {
    /**
     * Touching sessionStorage can throw rather than answer - site data blocked, some embedded webviews. There
     * is no error boundary in this application, so an uncaught throw would blank the page at the end of a
     * sign-in. A denial is treated as nothing stored, which is also the honest security reading.
     */
    it('discards when reading the stored state throws, rather than blanking the page', async () => {
      // Narrowed to the one key under test: a broader stub would also throw for any harness read and would
      // make this case brittle to changes that have nothing to do with the guard.
      const real = Storage.prototype.getItem;
      vi.spyOn(Storage.prototype, 'getItem').mockImplementation(function (this: Storage, key: string) {
        if (key === AUTHORIZE_STORAGE_KEYS.state) {
          throw new DOMException('The operation is insecure.', 'SecurityError');
        }
        return real.call(this, key);
      });
      const { calls, routes } = await spyOnExchange();
      const { router, container } = await arriveAtMocked(routes, arrivalWith(MATCHING_STATE));

      expect(calls).toEqual([]);
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      // Still a rendered screen, and the same neutral sentence as every other discard (L4).
      expect(container.querySelectorAll('main')).toHaveLength(1);
      expect(container.textContent ?? '').toContain(en.signIn.discarded);
    });
  });

  describe('the comparison is against the stored value, not a constant (AC5)', () => {
    /**
     * The case this task most needs. Two navigations carry the SAME url state and code; only the stored value
     * changes between them, and the outcome flips. A comparison against a hardcoded constant would satisfy
     * every other assertion in this file - that defect has shipped eight times in this package - and would fail
     * only here.
     */
    it.each([
      ['the usual pair', MATCHING_STATE],
      // A second, unrelated value. Without it every success-path case seeds the same literal, and a comparison
      // of the STORED value against that hardcoded literal passes the whole file - measured at 125 of 126.
      ['an unrelated matching value', 'a-quite-different-state-value'],
    ])('flips from discard to exchange when only the stored value is changed to match: %s', async (_label, matching) => {
      const url = arrivalWith(matching);

      storeState('not-the-supplied-value');
      const first = await spyOnExchange();
      const mismatched = await arriveAtMocked(first.routes, url);
      expect(first.calls, 'a stored value that differs must not exchange').toEqual([]);
      expect(mismatched.router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);

      resetBetweenArrivals();

      storeState(matching);
      const second = await spyOnExchange();
      const matched = await arriveAtMocked(second.routes, url);
      expect(second.calls, 'the same url must exchange once the stored value agrees').toHaveLength(1);
      expect(matched.router.state.location.pathname).toBe(`${APP_BASENAME}/callback`);
    });
  });

  describe('a mismatch is indistinguishable from a missing state (AC7, L4)', () => {
    /** Collected from the real screens and compared to EACH OTHER, never to the catalogue. */
    async function outcomeAfterReset(url: string, storedValue?: string) {
      if (storedValue !== undefined) storeState(storedValue);
      const { container, router } = await arriveAt(url);
      const outcome = {
        text: (container.textContent ?? '').replace(/\s+/g, ' ').trim(),
        pathname: router.state.location.pathname,
        search: router.state.location.search,
        action: router.state.historyAction,
      };
      // The reset is in the name: three of these run back to back and must not see each other's state.
      resetBetweenArrivals();
      globalThis.sessionStorage.clear();
      return outcome;
    }

    it('produces a byte-identical outcome for a mismatch and for a missing state', async () => {
      const missing = await outcomeAfterReset('/callback?code=abc');
      const mismatched = await outcomeAfterReset(arrivalWith(MATCHING_STATE), 'something-else');
      const unstored = await outcomeAfterReset(arrivalWith(MATCHING_STATE));

      expect(mismatched).toEqual(missing);
      expect(unstored).toEqual(missing);
      // Non-vacuity: the collected outcome must carry something, or three empty objects would compare equal.
      expect(missing.text).toContain(en.signIn.discarded);
      expect(missing.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
    });
  });

  describe('a mismatch leaves storage and the page as T17 does (AC8, AC10, AC11, AC12)', () => {
    it('leaves all three stored items present and unchanged', async () => {
      seedAllThree();
      const before = Object.keys(globalThis.sessionStorage).sort();

      await arriveAt(`/callback?code=abc&state=${MATCHING_STATE}`);

      // Deleting them is task 23's by name (D-7), and a failed comparison is not a reason to pre-empt it.
      expect(Object.keys(globalThis.sessionStorage).sort()).toEqual(before);
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state)).toBe('stored-state');
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.nonce)).toBe('stored-nonce');
      expect(globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.codeVerifier)).toBe('stored-verifier');
    });

    it('writes and removes nothing while discarding a mismatch', async () => {
      storeState('stored-state');
      const setItem = vi.spyOn(Storage.prototype, 'setItem');
      const removeItem = vi.spyOn(Storage.prototype, 'removeItem');
      const clear = vi.spyOn(Storage.prototype, 'clear');

      await arriveAt(`/callback?code=abc&state=${MATCHING_STATE}`);

      expect(setItem).not.toHaveBeenCalled();
      expect(removeItem).not.toHaveBeenCalled();
      expect(clear).not.toHaveBeenCalled();
    });

    it('compares once under StrictMode, navigating once and never exchanging', async () => {
      storeState('stored-state');
      const { calls, routes } = await spyOnExchange();
      const { router } = await arriveAtMocked(routes, `/callback?code=abc&state=${MATCHING_STATE}`, {
        strict: true,
      });

      expect(calls).toEqual([]);
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      expect(router.state.historyAction).toBe('REPLACE');
    });

    it('leaves the landed screen with one h1, one main and one live region', async () => {
      storeState('stored-state');
      const { container } = await arriveAt(`/callback?code=abc&state=${MATCHING_STATE}`);

      expect(container.querySelectorAll('h1')).toHaveLength(1);
      expect(container.querySelectorAll('main')).toHaveLength(1);
      expect(container.querySelectorAll('[role="status"]')).toHaveLength(1);
      expect(container.querySelector('input')).toBeNull();
    });
  });

  describe('the comparison is not widened in source (AC16, AC17)', () => {
    // Each pattern carries its own negative control, so inserting a row cannot misalign the two lists.
    const widening: readonly { label: string; pattern: RegExp; violation: string }[] = [
      { label: 'loose equality', pattern: /[^=!<>]==[^=]/, violation: 'if (stored == state) {' },
      { label: 'case folding', pattern: /\.toLowerCase\s*\(|\.toUpperCase\s*\(/, violation: 'stored.toLowerCase()' },
      { label: 'unicode normalisation', pattern: /\.normalize\s*\(/, violation: 'stored.normalize()' },
      { label: 'a prefix or suffix match', pattern: /\.startsWith\s*\(|\.endsWith\s*\(/, violation: 'stored.startsWith(state)' },
      { label: 'a substring match on the state', pattern: /\bstored\s*\.\s*includes\s*\(/, violation: 'stored.includes(state)' },
      { label: 'a trim of the stored value', pattern: /\bstored\s*(\?\.)?\s*\.\s*trim\s*\(/, violation: 'stored.trim()' },
    ];

    it.each(widening)('CallbackScreen.tsx contains no $label', ({ pattern }) => {
      expect(callbackSource).not.toMatch(pattern);
    });

    it('and each pattern finds its own violation when one is present', () => {
      for (const { label, pattern, violation } of widening) {
        expect(violation, label).toMatch(pattern);
      }
    });

    it('reads the stored state through the published key, never a re-spelled literal', () => {
      expect(callbackSource).toContain('AUTHORIZE_STORAGE_KEYS.state');
      expect(callbackSource).not.toContain('themistra.auth.');
    });

    // The claim it replaces was false precisely because it named a task, so the replacement names none.
    it('carries no task-number claim in the error-response comment (AC17)', () => {
      const line = callbackSource
        .split('\n')
        .find((candidate) => candidate.includes('surfaces the error itself'));

      expect(line).toBeDefined();
      expect(callbackSource).not.toContain('Task 19 owns');
    });

    it('reads AUTHORIZE_STORAGE_KEYS only inside a function body, never at module scope', () => {
      expect(callbackSource).toContain('AUTHORIZE_STORAGE_KEYS');

      expect(moduleScopeUses(callbackSource, 'AUTHORIZE_STORAGE_KEYS')).toEqual([]);
    });
  });

  describe('the seam is honest about being unfinished (AC4)', () => {
    it('names task 20 in its own source, so it is not mistaken for a finished exchange', () => {
      expect(exchangeSource).toContain('task 20');
    });

    it('declares a grant carrying the code and state the callback read', () => {
      expect(exchangeSource).toMatch(/code\s*:\s*string/);
      expect(exchangeSource).toMatch(/state\s*:\s*string/);
    });
  });
});
