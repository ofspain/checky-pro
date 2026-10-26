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
      const uses = [...callbackSource.matchAll(/^(\s*).*\bSIGN_IN_PATH\b.*$/gm)]
        .filter((match) => !match[0].trimStart().startsWith('*') && !match[0].trimStart().startsWith('//'))
        .filter((match) => !match[0].startsWith('import'));

      expect(uses.length).toBeGreaterThan(0);
      // Every remaining use is indented, which at this file's top level means it sits inside a block.
      for (const match of uses) expect(match[1], match[0].trim()).not.toBe('');
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

    // A duplicated security parameter is a parameter-pollution shape. URLSearchParams.get returns the FIRST,
    // so that is what the exchange receives and what task 18 will compare against the stored value.
    it('uses the first state when the query carries two', async () => {
      const { calls, routes } = await spyOnExchange();
      await arriveAtMocked(routes, '/callback?code=abc&state=first&state=second');

      expect(calls).toHaveLength(1);
      expect(calls[0]).toEqual(expect.objectContaining({ state: 'first' }));
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
