/**
 * R19's landing half and L14's public route — **TASK 37 OWNS ONE ROUTE OF NINE.**
 *
 * `tasks.md:65` gives task 37 this file's name, and `tasks.md:71` gives the same name to **task 40** ("Public
 * route set is exact… Cites R28, L14"). The two claims are not the same size, so what each owns is written
 * here rather than left to a reader to infer:
 *
 * - **Task 37 (this file today):** `/app/signed-out` is declared, is public, resolves to a screen, and that
 *   screen says the right thing in every way it can be arrived at.
 * - **Task 40 (to be added here):** that these nine paths **and only** these nine are reachable without a
 *   session. That claim needs the auth gate, and no route requires a session yet, so it is not merely
 *   unwritten - it is currently untestable. Task 40 extends this file; it does not create a second one.
 *
 * Every string the screen renders is asserted as LITERAL ENGLISH as well as against the catalogue, because
 * `t()` returns the key when an entry is missing - so a case comparing `t('signedOut.heading')` to
 * `en.signedOut.heading` alone would compare a key to a key and pass against a deleted catalogue section.
 */
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router';
import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { accessToken, clearAccessToken, retainAccessToken } from '../auth/accessToken';
import { createI18n } from '../i18n/i18n';
import { en } from '../i18n/catalogue';
import { APP_BASENAME, PUBLIC_ROUTE_PATHS, SIGNED_OUT_PATH, SIGN_IN_PATH, appRoutes } from './routes';

/** The words, spelled once here so a silent catalogue edit fails rather than passes. */
const COPY = {
  heading: 'Signed out',
  explanation: 'Sign in again whenever you are ready.',
  action: 'Sign in',
} as const;

let mounted: { root: Root; container: HTMLElement; router: ReturnType<typeof createMemoryRouter> } | undefined;

/**
 * Mounts the REAL route table at the signed-out route.
 *
 * Through the router rather than rendering the component directly, which is what makes AC1 a claim about
 * ROUTING and not about React: it proves the declared path resolves to this screen. `initialEntries` carry the
 * basename, as `callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx:205-207` does.
 */
async function arriveAt(search = '', options: { strict?: boolean } = {}) {
  const router = createMemoryRouter(appRoutes, {
    basename: APP_BASENAME,
    initialEntries: [`${APP_BASENAME}/${SIGNED_OUT_PATH}${search}`],
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
  mounted = { root, container, router };
  return { container, router, main: container.querySelector('main') };
}

let screenSource: string | undefined;

/** The component's own source, comments stripped, for the scans. */
async function source(): Promise<string> {
  screenSource ??= (await readFile(join(dirname(dirname(expect.getState().testPath as string)), 'auth', 'SignedOutScreen.tsx'), 'utf8'))
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .split('\n')
    .map((line) => {
      const at = line.indexOf('//');
      return at >= 0 && !/['"`]/.test(line.slice(0, at)) ? line.slice(0, at) : line;
    })
    .join('\n');
  return screenSource;
}

function watchStorage(): Record<'getItem' | 'setItem' | 'removeItem' | 'clear', Mock> {
  return {
    getItem: vi.spyOn(Storage.prototype, 'getItem') as unknown as Mock,
    setItem: vi.spyOn(Storage.prototype, 'setItem') as unknown as Mock,
    removeItem: vi.spyOn(Storage.prototype, 'removeItem') as unknown as Mock,
    clear: vi.spyOn(Storage.prototype, 'clear') as unknown as Mock,
  };
}

describe('publicRouteSetIsExactlyTheDeclaredSet', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    globalThis.sessionStorage.clear();
    globalThis.localStorage.clear();
    clearAccessToken();
  });

  afterEach(() => {
    if (mounted) {
      const { root, container, router } = mounted;
      act(() => root.unmount());
      container.remove();
      router.dispose();
      mounted = undefined;
    }
    clearAccessToken();
    vi.restoreAllMocks();
  });

  describe('AC1 the declared route resolves to a screen', () => {
    it('renders the signed-out screen at its declared path', async () => {
      const { main, router } = await arriveAt();

      expect(main, 'the route resolved to nothing - before task 37 it had no element').not.toBeNull();
      expect(main!.querySelector('h1')?.textContent).toBe(COPY.heading);
      // The basename is part of the memory router's location, measured here rather than assumed: it reports
      // `/app/signed-out`, not `/signed-out`. Asserting the short form passed vacuously on nothing.
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGNED_OUT_PATH}`);
    });

    it('renders the same under StrictMode double-invocation', async () => {
      const { main } = await arriveAt('', { strict: true });
      expect(main!.querySelector('h1')?.textContent).toBe(COPY.heading);
      expect(main!.querySelectorAll('a')).toHaveLength(1);
    });
  });

  describe('AC2 reachable with no session at all', () => {
    it('renders with no token held and nothing in storage, and leaves both that way', async () => {
      const watch = watchStorage();

      const { main } = await arriveAt();

      expect(main!.textContent).toContain(COPY.heading);
      expect(accessToken.current(), 'the screen acquired a token from somewhere').toBeNull();
      expect(globalThis.sessionStorage.length).toBe(0);
      expect(globalThis.localStorage.length).toBe(0);
      expect(watch.setItem, 'the screen wrote to a store').not.toHaveBeenCalled();
      expect(watch.removeItem, 'the screen removed something - clearing is task 35, not this screen').not.toHaveBeenCalled();
      expect(watch.clear).not.toHaveBeenCalled();
    });

    it('is declared PUBLIC, in the route table rather than in this file', () => {
      expect(PUBLIC_ROUTE_PATHS as readonly string[]).toContain(SIGNED_OUT_PATH);
      expect(PUBLIC_ROUTE_PATHS, 'task 37 added or removed a public route').toHaveLength(9);
    });
  });

  describe('AC3 every string comes from the catalogue', () => {
    it('renders exactly the three catalogue strings', async () => {
      const { main } = await arriveAt();

      expect(main!.querySelector('h1')?.textContent).toBe(en.signedOut.heading);
      expect(main!.querySelector('p')?.textContent).toBe(en.signedOut.explanation);
      expect(main!.querySelector('a')?.textContent).toBe(en.signedOut.action);
      // And the catalogue holds the literal English, so a deleted section cannot make the three assertions
      // above compare a key to a key.
      expect(en.signedOut).toEqual(COPY);
    });

    it('holds no user-facing literal in its own source', async () => {
      const code = await source();
      for (const word of Object.values(COPY)) {
        expect(code, `SignedOutScreen.tsx spells the copy "${word}" instead of reading it from the catalogue`)
          .not.toContain(word);
      }
      // Lower bound: it really does read all three keys, so the bans are not passing over a blank component.
      for (const key of ['signedOut.heading', 'signedOut.explanation', 'signedOut.action']) {
        expect(code, `SignedOutScreen.tsx does not read ${key}`).toContain(key);
      }
    });

    it('adds no catalogue key outside its own section', () => {
      expect(Object.keys(en.signedOut).sort()).toEqual(['action', 'explanation', 'heading']);
      // Task 33's and task 28's suites pin these two; repeated here only to prove TASK 37 changed neither.
      expect(Object.keys(en.signIn).sort()).toEqual(
        ['action', 'discarded', 'explanation', 'failed', 'heading', 'sessionEnded', 'starting'].sort(),
      );
      expect(Object.keys(en.callback).sort()).toEqual(['heading', 'working'].sort());
    });
  });

  describe('AC4 the copy names no cause', () => {
    it('contains none of the words that would disclose why the session ended', async () => {
      const { main } = await arriveAt();
      const text = (main!.textContent ?? '').toLowerCase();
      // L4: unknown, locked, suspended and deleted accounts, and bad, expired and used tokens, must all read
      // the same. Any of these words would make one of those cases distinguishable from the others.
      const DISCLOSING = ['lock', 'suspend', 'delet', 'disabl', 'expir', 'invalid', 'revok', 'password',
        'incorrect', 'not found', 'unknown', 'fail', 'error', 'denied'];
      for (const word of DISCLOSING) {
        expect(text, `the copy contains "${word}", which names a cause`).not.toContain(word);
      }
    });

    it('CONTROL: the vocabulary scan fires on copy that would disclose a cause', () => {
      const DISCLOSING = ['lock', 'suspend', 'delet', 'expir', 'revok'];
      for (const bad of ['Your account is locked.', 'This session expired.', 'Access revoked.',
        'Account suspended.', 'Your account was deleted.']) {
        expect(DISCLOSING.some((word) => bad.toLowerCase().includes(word)), `the scan is blind to "${bad}"`)
          .toBe(true);
      }
      expect(DISCLOSING.some((word) => COPY.explanation.toLowerCase().includes(word)),
        'the scan flags the real copy, so it is a ban on working').toBe(false);
    });
  });

  describe('AC5 one way back, and only one', () => {
    it('offers a link to sign-in and no other interactive element', async () => {
      const { main } = await arriveAt();

      const links = [...main!.querySelectorAll('a')];
      expect(links, 'the screen offers more than one way out, or none').toHaveLength(1);
      expect(links[0]!.getAttribute('href')).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      expect(links[0]!.textContent).toBe(COPY.action);
      // The "only action" half, which is what makes the criterion more than decorative.
      expect(main!.querySelectorAll('button'), 'a button appeared beside the link').toHaveLength(0);
      expect(main!.querySelectorAll('input, select, textarea, form'), 'the landing grew a control').toHaveLength(0);
    });

    it('is a real anchor, not a button wired to navigate', async () => {
      const code = await source();
      expect(code, 'the way back is no longer a router Link').toMatch(/<Link\s/);
      // A `navigate()` hand-over would remove middle-click, open-in-new-tab, copy-link and keyboard
      // activation, all of which a real anchor gives for nothing.
      for (const forbidden of [/useNavigate/, /\bnavigate\s*\(/, /location\s*\.\s*assign/, /<button/]) {
        expect(code, `SignedOutScreen.tsx reaches for ${forbidden.source}`).not.toMatch(forbidden);
      }
    });
  });

  describe('AC6 indifferent to the query string', () => {
    it('renders byte-identical output for every way it can be arrived at', async () => {
      const shapes = ['', '?state=opaque-correlation-value', '?unexpected=1', '?state=a&unexpected=1'];
      const rendered: string[] = [];
      for (const search of shapes) {
        const { main } = await arriveAt(search);
        // The heading as well as the markup: without it, four empty renders would be identical and the case
        // would pass on a route that resolved to nothing.
        expect(main!.textContent, `nothing rendered for "${search}"`).toContain(COPY.heading);
        rendered.push(main!.innerHTML);
        act(() => mounted!.root.unmount());
        mounted!.container.remove();
        mounted!.router.dispose();
        mounted = undefined;
      }
      expect(new Set(rendered).size, `the query string changed what rendered: ${shapes.join(' | ')}`).toBe(1);
    });

    it('cannot be reading the query, by source', async () => {
      const code = await source();
      // The structural half of AC6. T36 sends no `state`; `design.md:167` describes the backend test's URL,
      // which supplies one. Either way this screen must not display an opaque correlation value.
      for (const forbidden of [/useSearchParams/, /useLocation/, /location\s*\.\s*search/, /URLSearchParams/]) {
        expect(code, `SignedOutScreen.tsx reads the query (${forbidden.source})`).not.toMatch(forbidden);
      }
    });
  });

  describe('AC7 it does nothing', () => {
    it('sends no request and starts no timer', async () => {
      const fetchSpy = vi.fn();
      vi.stubGlobal('fetch', fetchSpy);
      const timer = vi.spyOn(globalThis, 'setTimeout');

      await arriveAt();

      expect(fetchSpy, 'the landing screen called the backend').not.toHaveBeenCalled();
      expect(timer, 'the landing screen started a timer').not.toHaveBeenCalled();
      vi.unstubAllGlobals();
    });

    it('reaches for nothing it should not, by source', async () => {
      const code = await source();
      for (const forbidden of [/\bfetch\s*\(/, /XMLHttpRequest/, /sessionStorage/, /localStorage/,
        /document\.cookie/, /indexedDB/, /setTimeout/, /setInterval/, /useEffect/, /useState/, /useRef/,
        /console\./, /sendBeacon/]) {
        expect(code, `SignedOutScreen.tsx reaches for ${forbidden.source}`).not.toMatch(forbidden);
      }
    });

    it('CONTROL: the source scan fires on a planted reach', async () => {
      const code = await source();
      const planted = `${code}\nvoid globalThis.sessionStorage.getItem('x');`;
      expect(/sessionStorage/.test(planted), 'the scan would not see a planted store access').toBe(true);
      expect(/sessionStorage/.test(code), 'the real source already trips the scan').toBe(false);
    });
  });

  describe('AC8 it clears nothing', () => {
    it('leaves a held token exactly where it was', async () => {
      // Clearing here was rejected by design: it would make a URL into a sign-out affordance that any
      // cross-site link could fire. R19 clause 1 is task 35's, and by the time SAS redirects here it has
      // already run.
      retainAccessToken('T37-ACCESS-TOKEN-should-survive');

      await arriveAt();

      expect(accessToken.current(), 'the landing screen cleared the session - a URL must not sign anyone out')
        .toBe('T37-ACCESS-TOKEN-should-survive');
    });
  });

  describe('AC9 exactly three screens are attached', () => {
    it('attaches an element to sign-in, callback and signed-out, and to nothing else', () => {
      // A deliberate COUNT PIN, and it will fail the day the next screen is attached - which is the handoff
      // working, exactly as task 16's did for this task. Whoever attaches the next one extends this list and
      // task 16's SCREENS_ATTACHED_SO_FAR together.
      //
      // WHY TWO PINS, decided in phase 7. `routesResolveUnderAppPrefixOnly.test.tsx:161` already pins the same
      // total against a hand-edited list, and against every mutant in T37's harness this case is a STRICT
      // SUBSET of it: task 16's compares to the LITERAL 'signed-out', so it also fails when the published
      // constant is renamed, while this one spells both sides with SIGNED_OUT_PATH and is blind to a rename by
      // construction (AC10 is what covers the spelling). The redundancy is kept anyway, for one reason: task
      // 40 extends THIS file with the exhaustive public-route claim, and this case is the seed it grows from -
      // the total expressed in the route table's own vocabulary. The day task 40 writes that claim, the pin to
      // retire is task 16's narrowing one, not this one.
      const walk = (route: RouteObject): string[] => {
        const has = route.element !== undefined || route.Component !== undefined || route.errorElement !== undefined;
        return [...(has && route.path !== undefined ? [route.path] : []),
          ...(route.children ?? []).flatMap(walk)];
      };
      const attached = appRoutes.flatMap(walk);
      expect(attached).toEqual(['sign-in', 'callback', SIGNED_OUT_PATH]);

      // CONTROL, the one task 16's case has and this one was missing: `walk` must COUNT, rather than return a
      // list that happens to match. Given two attached routes - one nested, one carrying an errorElement
      // instead of an element - it reports both; given a bare path it reports none.
      const planted: RouteObject[] = [
        { path: 'a', errorElement: <i /> },
        { path: 'b', children: [{ path: 'c', Component: () => null }] },
      ];
      expect(planted.flatMap(walk)).toEqual(['a', 'c']);
      expect([{ path: 'plain' }].flatMap(walk)).toEqual([]);
    });
  });

  describe('AC10 task 37 adds nothing it was not authorized to add', () => {
    it('adds no route and no storage key', async () => {
      expect(PUBLIC_ROUTE_PATHS).toHaveLength(9);
      const watch = watchStorage();
      await arriveAt();
      expect(watch.setItem).not.toHaveBeenCalled();
      // L16's carve-out is task 22's to police; this only proves task 37 contributed nothing to it.
      expect(Object.keys(globalThis.sessionStorage)).toEqual([]);
    });

    it('publishes ONE spelling of the route, which task 36 now imports', async () => {
      // Task 37 retires T36-D2: `endSession.ts` declared `SIGNED_OUT_PATH` privately because `routes.tsx` was
      // closed to it. There is now a single constant, so there is nothing to drift and no drift test to write.
      const endSession = await readFile(
        join(dirname(dirname(expect.getState().testPath as string)), 'auth', 'endSession.ts'), 'utf8');
      expect(endSession, 'endSession.ts still declares its own copy of the route').not.toMatch(/const SIGNED_OUT_PATH\s*=/);
      expect(endSession, 'endSession.ts does not import the published constant').toMatch(/SIGNED_OUT_PATH.*from '\.\.\/app\/routes'/);
      expect(SIGNED_OUT_PATH).toBe('signed-out');
    });
  });

  describe('AC11 nothing identifying is rendered', () => {
    it('renders no address, no identifier and no token-shaped string', async () => {
      retainAccessToken('T37-ACCESS-TOKEN-should-survive');
      const { main } = await arriveAt();
      const text = main!.textContent ?? '';

      expect(text, 'an email address reached the landing screen').not.toContain('@');
      expect(text, 'the held token reached the landing screen').not.toContain('T37-ACCESS-TOKEN');
      expect(text, 'something UUID-shaped reached the landing screen')
        .not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i);
      // The whole rendered text is three known strings and nothing else.
      expect(text).toBe(`${COPY.heading}${COPY.explanation}${COPY.action}`);
    });
  });

  describe('AC12 direct arrival, with no sign-out having happened', () => {
    it('renders and stays put rather than redirecting', async () => {
      // The only way anyone can reach this screen today: nothing in the SPA calls `endSession()` (OQ-T36-5),
      // so every real arrival is someone typing the URL.
      const { main, router } = await arriveAt();

      expect(main!.textContent).toContain(COPY.heading);
      expect(router.state.location.pathname, 'the landing screen redirected somewhere')
        .toBe(`${APP_BASENAME}/${SIGNED_OUT_PATH}`);
      expect(router.state.location.search).toBe('');
    });
  });

  describe('AC13 nothing asserts the visitor session state', () => {
    it('renders byte-identical output whether or not a token is held', async () => {
      // THE CLAUSE THAT SHAPED THE COPY. This route is reachable by typing it, so it can be read in a tab that
      // still holds a token - and this screen cannot tell, nor may it look. So "Signed out" is a page label and
      // the explanation is an invitation; neither is false in either arrival. If a future edit makes the copy
      // assert state, it will still pass this case - but the catalogue comment and AC4 are what stop that, and
      // this case stops the screen from BRANCHING on the token it must not read.
      const { main: withoutToken } = await arriveAt();
      const before = withoutToken!.innerHTML;
      act(() => mounted!.root.unmount());
      mounted!.container.remove();
      mounted!.router.dispose();
      mounted = undefined;

      retainAccessToken('T37-ACCESS-TOKEN-should-survive');
      const { main: withToken } = await arriveAt();

      expect(withToken!.innerHTML, 'the screen rendered differently for a tab holding a token').toBe(before);
    });

    it('reads SIGN_IN_PATH inside the component, never at module scope', async () => {
      const code = await source();
      const body = code.slice(code.indexOf('export function SignedOutScreen'));
      expect(body, 'the component body was not found, so this scan reads the whole file').toContain('SIGN_IN_PATH');
      const beforeComponent = code.slice(0, code.indexOf('export function SignedOutScreen'));
      // `routes.tsx:2-5`: this module is imported BY the route table, so reading an imported route constant
      // while the graph is initialising would touch a binding in its temporal dead zone and the APPLICATION
      // would fail to start, not a test. The import itself is fine - ES bindings are hoisted - so the scan is
      // on reads outside the component, with the import line excluded by name.
      const readsOutside = beforeComponent
        .split('\n')
        .filter((line) => line.includes('SIGN_IN_PATH') && !line.trimStart().startsWith('import'));
      expect(readsOutside, 'SIGN_IN_PATH is read at module scope').toEqual([]);
    });

    it('CONTROL: the module-scope scan fires on a planted read', () => {
      const planted = ["import { SIGN_IN_PATH } from '../app/routes';", "const where = `/${SIGN_IN_PATH}`;",
        'export function SignedOutScreen() {'].join('\n');
      const before = planted.slice(0, planted.indexOf('export function SignedOutScreen'));
      const readsOutside = before.split('\n')
        .filter((line) => line.includes('SIGN_IN_PATH') && !line.trimStart().startsWith('import'));
      expect(readsOutside, 'the scan is blind to a module-scope read').toHaveLength(1);
    });
  });
});
