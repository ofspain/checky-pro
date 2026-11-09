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
 *   session. Task 40 extends this file; it does not create a second one.
 *
 *   THAT SENTENCE USED TO END "it is currently untestable", and task 40 made it false (phase 8, I12):
 *   `auth/RequireSession.tsx` now gates the eleven authenticated paths as the pathless layout route in
 *   `routes.tsx`, so the gate exists and the claim can be measured. What is left is writing it.
 *
 * Every string the screen renders is asserted as LITERAL ENGLISH as well as against the catalogue, because
 * `t()` returns the key when an entry is missing - so a case comparing `t('signedOut.heading')` to
 * `en.signedOut.heading` alone would compare a key to a key and pass against a deleted catalogue section.
 */
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { StrictMode, type ReactElement } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, Outlet, RouterProvider, type RouteObject } from 'react-router';
import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { accessToken, clearAccessToken, retainAccessToken } from '../auth/accessToken';
import { retainIdToken, takeIdToken } from '../auth/idToken';
import { createI18n } from '../i18n/i18n';
import { en } from '../i18n/catalogue';
import { RedirectToSignIn } from '../auth/RedirectToSignIn';
import { RequireSession } from '../auth/RequireSession';
import { APP_BASENAME, AUTHENTICATED_ROUTE_PATHS, HOME_PATH, PUBLIC_ROUTE_PATHS, SIGNED_OUT_PATH,
  SIGN_IN_PATH, appRoutes } from './routes';

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

/**
 * Every surface this screen must not reach, each paired with a snippet that WOULD trip it.
 *
 * The PAIRING is the point. Phase 8's I6 observed that a single planted `sessionStorage` proved the scan
 * could see one surface and said nothing about the other twelve, so a typo in any other pattern - a
 * lower-case `sendbeacon`, a dropped word boundary - was unmeasured. The scan and its control iterate THIS
 * table, so a pattern cannot arrive without a probe that proves it fires.
 *
 * The token entries are phase 8's I1, and they are the ones that matter most. AC6 and AC13 compare RENDERED
 * OUTPUT, so they can only see a read that CHANGES what renders; a module that calls `accessToken()` and
 * branches to the same markup is invisible to them. This screen's central constraint is that it makes no
 * claim about the visitor's session, and the first step toward breaking it is reading the session at all.
 */
const FORBIDDEN_REACHES: readonly (readonly [RegExp, string])[] = [
  [/\bfetch\s*\(/, "void fetch('/x');"],
  [/XMLHttpRequest/, 'void new XMLHttpRequest();'],
  [/sessionStorage/, "void globalThis.sessionStorage.getItem('x');"],
  [/localStorage/, "void globalThis.localStorage.getItem('x');"],
  [/document\.cookie/, 'void document.cookie;'],
  [/indexedDB/, "void indexedDB.open('x');"],
  [/setTimeout/, 'setTimeout(() => undefined, 0);'],
  [/setInterval/, 'setInterval(() => undefined, 0);'],
  [/useEffect/, 'useEffect(() => undefined, []);'],
  [/useState/, 'const [x] = useState(0);'],
  [/useRef/, 'const r = useRef(null);'],
  // PHASE 11 (F3). This screen is deliberately static, so any hook beyond `useTranslation` is a deviation:
  // `useReducer` is state, `useMemo`/`useCallback` can cache a read or a side effect, `useContext` can pull
  // session state out of a provider without naming it. `useId` is left out on purpose - it is genuinely
  // inert, and forbidding it would make this a style rule rather than a claim about behaviour.
  [/\buseReducer\b/, 'const [s] = useReducer((x) => x, 0);'],
  [/\buseMemo\b/, 'const m = useMemo(() => 0, []);'],
  [/\buseCallback\b/, 'const c = useCallback(() => undefined, []);'],
  [/\buseContext\b/, 'const v = useContext(Ctx);'],
  [/console\./, "console.log('x');"],
  [/sendBeacon/, "navigator.sendBeacon('/x');"],
  // TASK 37 PHASE 8 (I1) - reading the session, which no render comparison can see.
  //
  // PHASE 11 (F1) CORRECTED THIS, and it was worse than the finding said. The pattern was
  // `/\baccessToken\s*\(/`, and `accessToken` is not a function: `accessToken.ts:45` exports it as a
  // `LocalSession` OBJECT with `current()`, `adopt()` and `renew()`. So the entry forbade `accessToken(` -
  // a shape that cannot legally exist anywhere - and allowed every shape that can. The identifier itself
  // is what is forbidden now, which covers all three methods and anything added later. `clearAccessToken`
  // and `retainAccessToken` keep their own rows: neither contains a lower-case `accessToken`.
  [/\baccessToken\b/, 'void accessToken.current();'],
  [/\bclearAccessToken\b/, 'clearAccessToken();'],
  [/\bretainAccessToken\b/, "retainAccessToken('x');"],
  [/\btakeIdToken\b/, 'void takeIdToken();'],
  [/\bretainIdToken\b/, "retainIdToken('x');"],
  // PHASE 11 (F2): quote-agnostic. oxlint enforces single quotes, but a scan for a token read must not
  // rest on formatter discipline - the probe is deliberately double-quoted to prove it does not.
  [/from ["']\.\/(?:accessToken|idToken)["']/, 'import { accessToken } from "./accessToken";'],
  // TASK 37 PHASE 8 (I1) - the surfaces T36's AC8 sweeps, for the same reason it sweeps them.
  [/document\.title/, "document.title = 'x';"],
  [/window\.name/, "window.name = 'x';"],
  [/history\.state/, 'void history.state;'],
] as const;

function watchStorage(): Record<'getItem' | 'setItem' | 'removeItem' | 'clear', Mock> {
  return {
    getItem: vi.spyOn(Storage.prototype, 'getItem') as unknown as Mock,
    setItem: vi.spyOn(Storage.prototype, 'setItem') as unknown as Mock,
    removeItem: vi.spyOn(Storage.prototype, 'removeItem') as unknown as Mock,
    clear: vi.spyOn(Storage.prototype, 'clear') as unknown as Mock,
  };
}

// ===========================================================================================================
// TASK 40'S INSTRUMENT. One derived table, probes on every declared path, and the gate as the only variable.
// ===========================================================================================================

/** Fills every `:id` segment, as task 16's suite does. The router does not validate it. */
const SAMPLE_UUID = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11';

function concrete(path: string): string {
  return path.replaceAll(':id', SAMPLE_UUID);
}

/** Stands in for whatever screen a later task attaches, so a route that renders has something to render. */
function Probe({ path }: { path: string }) {
  return <i data-probe={path}>{`probe:${path}`}</i>;
}

/** The control's gate: the same shape as `RequireSession`, with the question removed. */
function PassThrough() {
  return <Outlet />;
}

/** Every route in the tree, flattened, so a claim about the WHOLE table cannot be made against one level. */
function everyRoute(routes: readonly RouteObject[]): RouteObject[] {
  return routes.flatMap((route) => [route, ...everyRoute(route.children ?? [])]);
}

/**
 * `appRoutes`, with a probe on every declared path and on the catch-all, and `gate` on the pathless route.
 *
 * IT ASSERTS ITS OWN BASELINE before returning. A derived table that found no gate to replace, or probed the
 * wrong number of paths, would hand every arm below a green result that measured nothing - which is how a
 * harness lies. The counts are DERIVED from the exported arrays, never written down (AC5).
 */
function withProbes(gate: ReactElement): RouteObject[] {
  let gatesFound = 0;
  let pathlessLeaves = 0;
  let probesAttached = 0;

  // The derivation copies THREE keys, so it states which three and fails if the real table ever grows a
  // fourth. A spread would have carried a `loader` or a `lazy` along silently; naming the keys means a later
  // task that adds one finds out here, in a case about the instrument, rather than in an arm that quietly
  // measured a route the derivation had flattened.
  const copied = new Set(['path', 'element', 'children']);
  // TASK 41 added the index route, whose keys are `index` and `element`. Its allowance is SEPARATE rather
  // than merged into the set above, because `index` on a route that the derivation copied as a `path` child
  // would change which arrival matches, and only the pathless leaf is allowed to carry it.
  const copiedIndex = new Set(['index', 'element']);
  const assertCopyable = (route: RouteObject, allowed: Set<string> = copied): void => {
    for (const key of Object.keys(route)) {
      expect(allowed.has(key), `the derived table would silently drop \`${key}\``).toBe(true);
    }
  };

  const table: RouteObject[] = appRoutes.map((root): RouteObject => {
    assertCopyable(root);
    return {
      path: root.path,
      children: (root.children ?? []).map((child): RouteObject => {
        // THE GATE IS THE PATHLESS CHILD WITH CHILDREN, and task 41 is why that is now spelled out. Until
        // task 41 the gate was the only pathless route in the table, so `path === undefined` identified it;
        // task 41 added a second - the index route for the bare `/app/` - and with the old test the derived
        // table found TWO gates, failed its own baseline, and took every arm in this suite down with it,
        // including the four that should have passed untouched. Replacing the index route with the gate
        // element would also have put a `children`-less layout where a redirect belongs.
        if (child.path === undefined && child.children === undefined) {
          assertCopyable(child, copiedIndex);
          pathlessLeaves += 1;
          // Carried through UNCHANGED, element and all. The arms below arrive at a named path, so the index
          // route never matches and never varies; copying it keeps the derived table the same shape as the
          // real one, which is what `assertCopyable` exists to notice.
          return { index: true, element: child.element };
        }
        assertCopyable(child);
        if (child.path === undefined) {
          gatesFound += 1;
          return {
            element: gate,
            children: (child.children ?? []).map((nested): RouteObject => {
              assertCopyable(nested);
              probesAttached += 1;
              return { path: nested.path, element: <Probe path={nested.path as string} /> };
            }),
          };
        }
        probesAttached += 1;
        return { path: child.path, element: <Probe path={child.path} /> };
      }),
    };
  });

  expect(gatesFound, 'the derived table found no pathless gate to replace').toBe(1);
  // One pathless LEAF, task 41's index route. Asserted for the same reason as the gate count: if a later task
  // adds a second, every arm here would be measuring a table whose bare-path arrival had changed, and the
  // baseline is the only place that can say so.
  expect(pathlessLeaves, 'the derived table did not find exactly task 41\'s index route').toBe(1);
  expect(probesAttached, 'the derived table did not probe every declared path and the catch-all')
    .toBe(PUBLIC_ROUTE_PATHS.length + AUTHENTICATED_ROUTE_PATHS.length + 1);
  return table;
}

/**
 * Mounts a table at an `/app` location, WITHOUT `AppShell`.
 *
 * The shell is not part of the route table - `AppShell.tsx` is a bare `<div>` that task 16's suite wraps
 * around the provider - so this container IS the outlet's subtree, and `container.innerHTML === ''` is the
 * exact claim AC8 makes with no scoping to get wrong. The i18n provider is here so that an arm which renders
 * a real screen is not a second variable; the probes themselves hold no copy.
 */
async function arriveWithTable(table: RouteObject[], path: string, options: { strict?: boolean } = {}) {
  const router = createMemoryRouter(table, {
    basename: APP_BASENAME,
    initialEntries: [`${APP_BASENAME}/${concrete(path)}`],
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
  return { container, router };
}

/** Which probe rendered, or `null`. Names the route, so a case cannot pass on the WRONG screen appearing. */
function probeIn(container: HTMLElement): string | null {
  return container.querySelector('[data-probe]')?.getAttribute('data-probe') ?? null;
}

/** Unmounts between iterations of a loop, since `afterEach` only runs between cases. */
function release(): void {
  if (mounted) {
    const { root, container, router } = mounted;
    act(() => root.unmount());
    container.remove();
    router.dispose();
    mounted = undefined;
  }
  clearAccessToken();
}

let guardCode: string | undefined;

/**
 * `RequireSession.tsx`'s own source, comments stripped.
 *
 * A SECOND READER beside `source()` rather than a rewrite of it: `source()` has eleven call sites, all
 * task 37's, and the abstraction that serves both is not worth editing them for. Duplication is cheaper
 * than an abstraction until it isn't (T39 F12 settled this the same way).
 *
 * Stripping is not optional. The guard's header NAMES the surfaces it does not touch - `useSyncExternalStore`
 * and `RateLimitNotice` among them - so a scan that read prose would fail on the comment that explains why
 * the code is clean. T39's AC2 scan passed on prose its own phase-9 fix had written.
 */
async function guardSource(): Promise<string> {
  guardCode ??= (await readFile(
    join(dirname(dirname(expect.getState().testPath as string)), 'auth', 'RequireSession.tsx'), 'utf8'))
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .split('\n')
    .map((line) => {
      const at = line.indexOf('//');
      return at >= 0 && !/['"`]/.test(line.slice(0, at)) ? line.slice(0, at) : line;
    })
    .join('\n');
  return guardCode;
}

/**
 * Every surface the gate must not reach, each paired with a snippet that WOULD trip it.
 *
 * The session READER is deliberately absent, and its writers are deliberately present: asking
 * `accessToken.current()` is this module's entire job, while writing the session from inside a render would
 * make the gate a cause rather than a question. The navigation rows are what keep R28's second clause with
 * task 41, and the route-reading rows are what stop one gate from becoming eleven policies no spec
 * authorizes - measured at phase 7, where exactly that mutant survived all 1725 tests.
 */
const GUARD_FORBIDDEN: readonly (readonly [RegExp, string])[] = [
  [/\bfetch\s*\(/, "void fetch('/x');"],
  [/XMLHttpRequest/, 'void new XMLHttpRequest();'],
  [/sessionStorage/, "void globalThis.sessionStorage.getItem('x');"],
  [/localStorage/, "void globalThis.localStorage.getItem('x');"],
  [/document\.cookie/, 'void document.cookie;'],
  [/indexedDB/, "void indexedDB.open('x');"],
  [/sendBeacon/, "navigator.sendBeacon('/x');"],
  [/console\./, "console.log('x');"],
  [/setTimeout/, 'setTimeout(() => undefined, 0);'],
  [/setInterval/, 'setInterval(() => undefined, 0);'],
  // Hooks. The gate is a synchronous read; state or an effect would make it a component with a history, and
  // StrictMode double-invocation would start to matter.
  [/\buseState\b/, 'const [x] = useState(0);'],
  [/\buseEffect\b/, 'useEffect(() => undefined, []);'],
  [/\buseLayoutEffect\b/, 'useLayoutEffect(() => undefined, []);'],
  [/\buseRef\b/, 'const r = useRef(null);'],
  [/\buseMemo\b/, 'const m = useMemo(() => 0, []);'],
  [/\buseCallback\b/, 'const c = useCallback(() => undefined, []);'],
  [/\buseReducer\b/, 'const [s] = useReducer((x) => x, 0);'],
  [/\buseSyncExternalStore\b/, 'const v = useSyncExternalStore(sub, get);'],
  [/\buseContext\b/, 'const v = useContext(Ctx);'],
  // Navigation - R28's second clause, which is task 41's.
  [/\buseNavigate\b/, 'const go = useNavigate();'],
  [/\buseHref\b/, "const h = useHref('/x');"],
  [/<Navigate\b/, 'return <Navigate to="/sign-in" replace />;'],
  [/\bredirect\s*\(/, "return redirect('/sign-in');"],
  [/location\s*\.\s*assign/, "globalThis.location.assign('/x');"],
  [/location\s*\.\s*replace/, "globalThis.location.replace('/x');"],
  [/location\s*\.\s*href/, "globalThis.location.href = '/x';"],
  // Reading the route or the query, which is where per-path policy starts.
  [/\buseLocation\b/, 'const l = useLocation();'],
  [/\buseParams\b/, 'const p = useParams();'],
  [/\buseSearchParams\b/, 'const [q] = useSearchParams();'],
  [/URLSearchParams/, "new URLSearchParams('a=1');"],
  [/globalThis\s*\.\s*location/, 'void globalThis.location.pathname;'],
  [/window\s*\.\s*location/, 'void window.location.pathname;'],
  // The session WRITERS. The reader is the job; a write is not.
  [/\bretainAccessToken\b/, "retainAccessToken('x');"],
  [/\bclearAccessToken\b/, 'clearAccessToken();'],
  [/\bretainIdToken\b/, "retainIdToken('x');"],
  [/\btakeIdToken\b/, 'void takeIdToken();'],
  // PHASE 11 (F1). The four rows above forbid the standalone writers and say NOTHING about the methods on
  // the object this module already imports: `accessToken.adopt('x')` sets the token - it delegates to
  // `retainAccessToken` - and would have passed the sweep untouched. `renew()` is an action on the session
  // too, and it always rejects, so a render that called it would produce an unhandled rejection per paint.
  // The identifiers are forbidden rather than the call shapes, so an alias - `const { adopt } = accessToken`
  // - is caught as well.
  [/\badopt\b/, "accessToken.adopt('x');"],
  [/\brenew\b/, 'void accessToken.renew();'],
  // PHASE 11 (F1), the cross-tab primitives. These are the surfaces where a side effect would not stay in
  // this tab: the sign-out latch decides whether a SIBLING's renewal may be adopted, and a broadcast reaches
  // every open tab. A gate that set the latch while deciding what to render would sign other tabs out.
  [/\bmarkSignedOut\b/, 'markSignedOut();'],
  [/\bmarkSignedIn\b/, 'markSignedIn();'],
  [/\bbroadcastSignOut\b/, 'broadcastSignOut();'],
  [/\blistenForSignOut\b/, 'listenForSignOut(() => undefined);'],
  [/\bsignOut\s*\(/, 'signOut();'],
  [/\bendSession\s*\(/, 'endSession();'],
  // PHASE 11 (F7), for parity with T37's privacy sweep. The gate renders nothing, so a leak here would be
  // deliberate rather than incidental - but they are still places a render can leave state behind.
  [/document\.title/, "document.title = 'x';"],
  [/window\.name/, "window.name = 'x';"],
  [/history\.state/, 'void history.state;'],
  // PHASE 11 (F10). The layering row below is a STATIC-import pattern, and a dynamic one would walk past it.
  [/import\s*\(\s*['"]\.\.\/app\//, "void import('../app/routes');"],
  [/require\s*\(\s*['"]\.\.\/app\//, "require('../app/routes');"],
  // The module boundary, as source rather than as a convention (AC6).
  [/from ['"]\.\.\/app\//, "import { APP_BASENAME } from '../app/routes';"],
] as const;

// THE NAME OF THIS FILE DESCRIBES TASK 40'S CLAIM, NOT TASK 37'S. Task 37 owns ONE of the nine public
// routes; the exhaustive "these nine and only these nine" claim is task 40's, which extends this file. It
// said "needs an auth gate that does not exist yet" until task 40 built one (phase 8, I12).
//
// The shared name was raised three times (phase 3 F6, phase 8 I4, phase 11 F5) and kept three times, because `tasks.md:65` and `:71` name the SAME test for both tasks - renaming would
// either break that convention or leave task 40 deleting a file. Repeated here, in the describe block, so
// a reader who opens the suite rather than the header still sees it.
describe('publicRouteSetIsExactlyTheDeclaredSet', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    globalThis.sessionStorage.clear();
    globalThis.localStorage.clear();
    clearAccessToken();
    takeIdToken();
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
    takeIdToken();
    vi.restoreAllMocks();
  });

  describe('AC1 the declared route resolves to a screen', () => {
    it('renders the signed-out screen at its declared path', async () => {
      const { main, router, container } = await arriveAt();

      // PHASE 8 (I10) split this in two, because one assertion was carrying two different failures and
      // naming only the first. SL19 - the landmark replaced by a plain div - failed here with "the route
      // resolved to nothing", which is false: the route resolved, and the LANDMARK is what went missing.
      expect(container.querySelector('*'), 'the route resolved to nothing - before task 37 it had no element')
        .not.toBeNull();
      expect(main, 'the screen rendered, but with no <main> landmark - every case below dereferences it')
        .not.toBeNull();
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
      // PHASE 8 (I5): a styled anchor behaving like a button would be counted as a link above and satisfy
      // "only one way out" while losing the affordances a real anchor is chosen for.
      expect(links[0]!.hasAttribute('href'), 'the way back is an anchor with no href').toBe(true);
      expect(links[0]!.getAttribute('role'), 'the way back is an anchor dressed as a button').toBeNull();

      // PHASE 7. Every assertion above - and in AC6, AC11 and AC13 - reaches through `main!`. A mutant that
      // rendered a second anchor as a SIBLING of <main>, inside a fragment, survived all 25 cases: not one of
      // them looked at the container outside the landmark. So the counts are repeated at the container, which
      // is the boundary that actually matters for "the only action on the screen".
      const { container } = mounted!;
      expect([...container.querySelectorAll('a')], 'a way out was rendered OUTSIDE <main>').toHaveLength(1);
      expect(container.querySelectorAll('button, input, select, textarea, form'),
        'a control was rendered OUTSIDE <main>').toHaveLength(0);
      expect([...container.querySelectorAll('*')].filter((node) => node.parentElement === container)
        .map((node) => node.tagName), 'the screen renders something beside its landmark').toEqual(['MAIN']);
    });

    it('is a real anchor, not a button wired to navigate', async () => {
      const code = await source();
      expect(code, 'the way back is no longer a router Link').toMatch(/<Link\s/);
      // A `navigate()` hand-over would remove middle-click, open-in-new-tab, copy-link and keyboard
      // activation, all of which a real anchor gives for nothing.
      for (const forbidden of [/useNavigate/, /\bnavigate\s*\(/, /location\s*\.\s*assign/, /<button/,
        /role\s*=/]) {
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
      for (const [forbidden] of FORBIDDEN_REACHES) {
        expect(code, `SignedOutScreen.tsx reaches for ${forbidden.source}`).not.toMatch(forbidden);
      }
    });

    it('CONTROL: EVERY pattern in the sweep fires on a planted reach', async () => {
      const code = await source();
      for (const [forbidden, planted] of FORBIDDEN_REACHES) {
        expect(forbidden.test(`${code}\n${planted}`),
          `the scan cannot see a planted ${forbidden.source} - the pattern or the probe is wrong`).toBe(true);
        expect(forbidden.test(code),
          `the real source already trips ${forbidden.source}, so that entry proves nothing`).toBe(false);
      }
      // The sweep cannot shrink unnoticed: a deleted row would leave the loop above passing on fewer surfaces.
      expect(FORBIDDEN_REACHES, 'the forbidden-surface sweep shrank').toHaveLength(26);
      // PHASE 11 (F11): a count cannot see a DUPLICATED row, which would mean one surface was swept twice
      // and another not at all while the length still looked right.
      expect(new Set(FORBIDDEN_REACHES.map(([pattern]) => pattern.source)),
        'two rows of the sweep hold the same pattern').toHaveLength(FORBIDDEN_REACHES.length);
    });
  });

  describe('AC8 it clears nothing', () => {
    it('leaves a held token exactly where it was', async () => {
      // Clearing here was rejected by design: it would make a URL into a sign-out affordance that any
      // cross-site link could fire. R19 clause 1 is task 35's, and by the time SAS redirects here it has
      // already run.
      retainAccessToken('T37-ACCESS-TOKEN-should-survive');
      // PHASE 8 (I2): BOTH tokens. Task 36 introduced `idToken.ts` so `endSession()` can send an
      // `id_token_hint`, which makes the ID token session state this screen must leave alone too - and
      // nothing here covered it, so a mutant calling `takeIdToken()` passed.
      retainIdToken('T37-ID-TOKEN-should-survive');

      await arriveAt();

      expect(accessToken.current(), 'the landing screen cleared the session - a URL must not sign anyone out')
        .toBe('T37-ACCESS-TOKEN-should-survive');
      // `takeIdToken` is the ONLY reader `idToken.ts` publishes - T36 deliberately shipped no plain one, to
      // foreclose a read-before-clear window - so reading it here also clears it. It is therefore read last,
      // and `afterEach` drains it for the cases that do not.
      expect(takeIdToken(), 'the landing screen took the ID token task 36 holds for the end-session hint')
        .toBe('T37-ID-TOKEN-should-survive');
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
      // GROWN BY TASK 41, and the growth is the handoff working on the very next task. The fourth entry is
      // the CATCH-ALL, and it is named as a redirect rather than appended to the screen list, because this
      // case's own title is a claim about screens: a reader who found four entries and three screen names
      // would have to guess which was which. Task 41's other two redirects hang on routes with no `path`,
      // and `walk` records a route only when it has one - which is the hole T40's AC13 below exists for,
      // and the reason the number here went up by one rather than by three.
      const attached = appRoutes.flatMap(walk);
      expect(attached).toEqual(['sign-in', 'callback', SIGNED_OUT_PATH, '*']);

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

      // PHASE 8 (I3): importing it is not using it. A module could import the constant to satisfy the two
      // assertions above and still spell the route literally where the URL is actually built - the exact
      // drift this case exists to prevent, passing. Scoped to the function, because the file's prose
      // legitimately mentions `/app/signed-out` while explaining itself.
      const builder = /function postLogoutRedirectUri\(\)[\s\S]*?\n}/.exec(endSession)?.[0] ?? '';
      expect(builder, 'postLogoutRedirectUri was not found, so this scan reads nothing').toContain('return');
      expect(builder, 'the post-logout URL is not built from the published constant')
        .toMatch(/\$\{SIGNED_OUT_PATH\}/);
      expect(builder, 'the post-logout URL spells the route literally beside the constant it imports')
        .not.toMatch(/signed-out/);
    });
  });

  describe('AC11 nothing identifying is rendered', () => {
    it('renders no address, no identifier and no token-shaped string', async () => {
      retainAccessToken('T37-ACCESS-TOKEN-should-survive');
      // PHASE 10: the ID token too. AC8 now proves this screen does not TAKE it; this proves it does not
      // DISPLAY it. They are different failures - T36's whole reason for `idToken.ts` is that the ID token
      // is identity, so L3 governs it, and a screen that rendered it would leak a subject claim into a
      // public page reachable by typing its URL.
      retainIdToken('T37-ID-TOKEN-should-not-render');
      const { main } = await arriveAt();
      const text = main!.textContent ?? '';

      expect(text, 'an email address reached the landing screen').not.toContain('@');
      expect(text, 'the held access token reached the landing screen').not.toContain('T37-ACCESS-TOKEN');
      expect(text, 'the held ID token reached the landing screen').not.toContain('T37-ID-TOKEN');
      expect(text, 'something UUID-shaped reached the landing screen')
        .not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i);
      // The whole rendered text is three known strings and nothing else. PHASE 7: asserted on the CONTAINER
      // rather than on `main`, because a mutant that rendered a fourth string beside the landmark passed this
      // case while `main` stayed byte-identical. `main` is still checked, so a string moving OUT of the
      // landmark fails here too rather than merely relocating.
      // PHASE 8 (I8): this is deliberately whitespace-exact. Three strings, concatenated, no separators.
      // A `<br>`, a wrapping element that introduces a newline, or a space added for styling WILL fail this
      // case - that is the strength, not a defect, and whoever changes the markup updates `whole` on purpose.
      const whole = `${COPY.heading}${COPY.explanation}${COPY.action}`;
      expect(text, 'the landmark no longer holds exactly the three strings').toBe(whole);
      expect(mounted!.container.textContent, 'text was rendered outside <main>').toBe(whole);
    });

    it('CONTROL: the container-scoped sweep sees what a main-scoped one cannot', async () => {
      // Proves the two assertions above are not the same assertion twice. A node appended as a sibling of
      // <main> leaves `main.textContent` untouched and changes the container's.
      // NOTHING here is pinned to what the screen renders. Three earlier versions of this control each
      // reached for a number or a string belonging to the thing under test, and each failed alongside the
      // case it exists to validate: first the container's anchor count, then the landmark's measured against
      // the container's, then the whole-text equality - which made the control fail on SL21, a mutant that
      // only REORDERS the markup. A control must measure its own claim: here, that a container-scoped sweep
      // sees what a `main`-scoped one cannot.
      const { main, container } = await arriveAt();
      expect(main, 'no landmark, so there are not two scopes to tell apart').not.toBeNull();
      const textBefore = main!.textContent;
      const insideBefore = main!.querySelectorAll('a').length;
      const containerBefore = container.querySelectorAll('a').length;

      const planted = document.createElement('a');
      planted.href = '/elsewhere';
      planted.textContent = 'Elsewhere';
      container.append(planted);

      expect(main!.textContent, 'the planted node changed the landmark, so this control proves nothing')
        .toBe(textBefore);
      expect(main!.querySelectorAll('a'), 'the planted node landed inside the landmark')
        .toHaveLength(insideBefore);
      expect(container.textContent, 'the container-scoped sweep cannot see the planted node')
        .toContain('Elsewhere');
      expect(container.querySelectorAll('a')).toHaveLength(containerBefore + 1);
    });
  });

  // NOT one of the thirteen frozen criteria. Added in PHASE 10 to close FU-T37-1, which phase 7 opened
  // after noticing that three structural regressions all died INCIDENTALLY: SL19 (the landmark becomes a
  // div) on AC1's assertion about routing, SL20 (`h1` drops to `h2`) on AC3's heading READ, SL21 (the
  // action rendered above the heading) on AC11's whole-text ordering. Nothing in T37's criteria, in L4 or
  // in R19 says a word about landmarks or heading level, so each of those kills was a side effect of a
  // case about something else - and a side effect is not a contract. This states the shape directly.
  describe('PHASE 10 the screen has the structure its kills were only implying (FU-T37-1)', () => {
    it('is one landmark holding a level-one heading, a paragraph and the way back, in that order', async () => {
      const { main, container } = await arriveAt();

      expect(main, 'the screen renders no <main> landmark').not.toBeNull();
      expect(main!.parentElement, 'the landmark is nested rather than at the top of the screen').toBe(container);
      expect(main!.querySelectorAll('h1'), 'the screen has no single level-one heading').toHaveLength(1);
      expect(main!.querySelectorAll('h2, h3, h4, h5, h6'), 'the screen added a subheading').toHaveLength(0);
      // PHASE 11 (F4): the tags above are the markup; these are the SEMANTICS. `<main role="region">` is no
      // longer a main landmark, and `<h1 aria-level="2">` is no longer a level-one heading, and neither
      // changes a tag name.
      expect(main!.getAttribute('role'), 'the landmark is overridden to another role').toBeNull();
      const heading = main!.querySelector('h1');
      expect(heading!.getAttribute('aria-level'), 'the level-one heading is re-levelled').toBeNull();
      expect(heading!.getAttribute('role'), 'the heading is overridden to another role').toBeNull();

      // The reading order, as tags rather than as text, so this case fails for a STRUCTURAL change even
      // when the rendered text happens to be unchanged - which is the half AC11's equality cannot give.
      expect([...main!.children].map((node) => node.tagName))
        .toEqual(['H1', 'P', 'A']);
    });

    it('CONTROL: the order assertion is sensitive to order, not just to membership', () => {
      // Without this, `toEqual` on a three-element array could be mistaken for a set comparison. A
      // hand-built landmark with the same three tags in a different order must not satisfy it.
      const probe = document.createElement('main');
      for (const tag of ['a', 'h1', 'p']) probe.append(document.createElement(tag));
      const tags = [...probe.children].map((node) => node.tagName);

      expect(tags, 'the probe was built wrong, so this control proves nothing').toHaveLength(3);
      expect(tags).not.toEqual(['H1', 'P', 'A']);
      expect([...tags].sort(), 'the three tags are the same three, so only ORDER separates them')
        .toEqual(['A', 'H1', 'P']);
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

    it('builds the way back from SIGN_IN_PATH, in the `to` prop itself (F8)', async () => {
      const code = await source();
      // AC13's scan proves the constant is read inside the component, and AC5 proves the rendered href is
      // right. PHASE 11 (F8) makes the link between them explicit: the constant must appear in the `to`
      // expression, so a component that read it for some other purpose and hardcoded the target is caught
      // by a case about the LINK rather than only by an href comparison.
      expect(code, 'the Link\'s target is not built from SIGN_IN_PATH')
        .toMatch(/to=\{`\/\$\{SIGN_IN_PATH\}`\}/);
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

  // =========================================================================================================
  // TASK 40 - the exhaustive claim this file is named for. Everything above belongs to task 37 and is
  // unchanged.
  //
  // WHY A PROBE TABLE AND NOT THE REAL ONE. Eleven of the eleven authenticated routes are `{ path }` with no
  // element, so "an authenticated path serves no content" is TRUE TODAY FOR THE WRONG REASON - there is
  // nothing there to serve. A suite written against the real table would pass with the guard deleted, which
  // is the defect AC3 exists to forbid. So these cases derive a table from `appRoutes`, attach a probe
  // element to every declared path and to the catch-all, and vary ONE value: the element on the gate. The
  // production arm and the control arm differ in nothing else, which is what makes "nothing rendered" a
  // measurement rather than an absence.
  //
  // PHASE 9 REJECTED THE OTHER CANDIDATE, by measuring it: an isolation mount of `<RequireSession />` as a
  // route's own element renders "" in BOTH arms, because its `<Outlet />` has no child route and `null`
  // renders nothing either. That case would pass with the guard inverted, deleted or hard-wired to false.
  // EVERY DESCRIBE BELOW IS PREFIXED `T40`, and that is not decoration. Task 37's own blocks in this file are
  // numbered AC1-AC13 and so are task 40's, against a DIFFERENT frozen brief - so without the prefix this
  // file would hold two AC10s, two AC12s and two AC13s, and a failure naming "AC12" would not say whose. The
  // same collision bit task 39, which renumbered two criteria after its matrix caught them colliding with
  // its own frozen brief; this is the file-level version of it, caught at phase 12.
  // =========================================================================================================

  describe('T40 AC1, AC2, AC3, AC4 the split, measured against a control', () => {
    // ALL THREE BLOCKED ARMS WERE GROWN BY TASK 41, and the grown form is stronger than what it replaced.
    // Each used to assert `container.innerHTML === ''` - the gate's negative branch rendered nothing. That
    // was task 40's whole claim and the comment it carried said why emptiness and not emptiness of text:
    // an empty `<div />` renders no text, so a `textContent` assertion passed a mutant that survived all
    // 1725 tests. What changed is the gate's negative branch, which now renders task 41's redirect, so the
    // sign-in probe arrives and the container is not empty. The claim that survives intact is the one that
    // mattered: the AUTHENTICATED PATH'S OWN probe never renders. `probeIn` names the route, so a case
    // cannot pass on the wrong one appearing, and asserting the destination is a tighter pin than emptiness
    // ever was - emptiness could not tell a block from a crash.
    it('serves the sign-in start, not the path asked for, on all eleven without a session (AC2, AC8, R28)', async () => {
      for (const path of AUTHENTICATED_ROUTE_PATHS) {
        const { container, router } = await arriveWithTable(withProbes(<RequireSession />), path);
        expect(probeIn(container), `${path} served its own content without a session`).toBe(SIGN_IN_PATH);
        expect(router.state.location.pathname, path).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
        release();
      }
    });

    it('serves the sign-in start under StrictMode too, where every component renders twice (AC2)', async () => {
      const { container } = await arriveWithTable(withProbes(<RequireSession />), HOME_PATH, { strict: true });
      expect(probeIn(container)).toBe(SIGN_IN_PATH);
    });

    it('does not count an empty-string token as a session (AC2)', async () => {
      // `retainAccessToken` assigns whatever string it is given - `accessToken.ts:90` is `held = token;`,
      // with no guard - so `current()` can return `''`, and `!== null` would call that a session.
      retainAccessToken('');
      expect(accessToken.current()).toBe('');
      const { container } = await arriveWithTable(withProbes(<RequireSession />), HOME_PATH);
      expect(probeIn(container), 'an empty token opened the gate').toBe(SIGN_IN_PATH);
    });

    it('serves every one of the eleven WITH a session, so the gate is a split and not a deny (AC4)', async () => {
      for (const path of AUTHENTICATED_ROUTE_PATHS) {
        retainAccessToken('a-token-this-tab-holds');
        const { container } = await arriveWithTable(withProbes(<RequireSession />), path);
        expect(probeIn(container), `${path} was refused to a visitor holding a token`).toBe(path);
        release();
      }
    });

    it('serves an authenticated path under StrictMode WITH a session too (AC4, phase 11 F5)', async () => {
      // The gate is a pure read, so double-invocation should not matter - and "should not" is the reason to
      // measure it. The no-session arm has had a StrictMode case since this suite was written; this is the
      // other half, so both outcomes are pinned under the mode every suite here mounts in.
      retainAccessToken('a-token-this-tab-holds');
      const { container } = await arriveWithTable(withProbes(<RequireSession />), HOME_PATH, { strict: true });
      expect(probeIn(container)).toBe(HOME_PATH);
    });

    it('CONTROL: the same table, the same paths, a pass-through gate - and the content arrives (AC3)', async () => {
      // THIS IS THE CASE THAT MAKES AC2 FALSIFIABLE. It differs from the AC2 case in exactly one value: the
      // element handed to `withProbes`. If it rendered nothing too, AC2 would be measuring the absence of a
      // screen rather than the presence of a gate.
      for (const path of AUTHENTICATED_ROUTE_PATHS) {
        const { container } = await arriveWithTable(withProbes(<PassThrough />), path);
        expect(probeIn(container), `${path} renders nothing even with the gate removed, so AC2 proves nothing`)
          .toBe(path);
        release();
      }
    });

    it('serves every one of the nine public paths without a session (AC1)', async () => {
      for (const path of PUBLIC_ROUTE_PATHS) {
        const { container } = await arriveWithTable(withProbes(<RequireSession />), path);
        expect(probeIn(container), `${path} was blocked, and it is public`).toBe(path);
        release();
      }
    });

    it('serves the catch-all without a session, which is what task 41 will need (AC1)', async () => {
      // The catch-all is a SIBLING of the gate, not a descendant. A catch-all behind the gate would still
      // have no element of its own, so "the catch-all has no element" cannot see the difference - but task
      // 41 HAS NOW replaced it with the redirect to sign-in, and a guarded catch-all could not redirect the
      // only visitor it exists for. What this case still measures is that the catch-all RESOLVES without a
      // session: the derived table puts a probe where task 41's redirect is, so the arm is unchanged and
      // the redirect itself is claimed in `unauthenticatedRoutesRedirectToSignIn.test.tsx`.
      const { container } = await arriveWithTable(withProbes(<RequireSession />), 'no-such-route');
      expect(probeIn(container)).toBe('*');
    });
  });

  describe('T40 AC10 a sessionless visit is served, not bounced', () => {
    it('leaves the location where it was on every public path, and performs no navigation', async () => {
      for (const path of PUBLIC_ROUTE_PATHS) {
        const { router } = await arriveWithTable(withProbes(<RequireSession />), path);
        expect(router.state.location.pathname, path).toBe(`${APP_BASENAME}/${path}`);
        // 'POP' is the initial entry. A `<Navigate>` or a `redirect()` anywhere in the tree would make this
        // 'REPLACE' or 'PUSH', so this is the assertion that keeps R28's SECOND clause out of task 40.
        expect(router.state.historyAction, path).toBe('POP');
        release();
      }
    });

    // GROWN BY TASK 41, AND INVERTED BY IT. The case was "leaves the location where it was on a BLOCKED
    // path too, rather than redirecting (R28, task 41)" - and its own parenthesis was the handoff. Task 40
    // kept R28's second clause out of itself by pinning `historyAction` to 'POP' on a blocked path; task 41
    // is that clause, so the pin becomes its opposite. It is not loosened: the arm still asserts an exact
    // history action and an exact destination, and REPLACE rather than PUSH is the half that matters, since
    // a pushed redirect leaves the blocked path on the back stack for the Back button to bounce off.
    //
    // The public arm above is UNCHANGED and is what keeps the two apart: if task 41's redirect leaked onto a
    // public path, that case fails on the same assertion this one now expects.
    it('REPLACES the location on a BLOCKED path, sending the visitor to the sign-in start (R28, task 41)', async () => {
      for (const path of AUTHENTICATED_ROUTE_PATHS) {
        const { router, container } = await arriveWithTable(withProbes(<RequireSession />), path);
        expect(probeIn(container), path).toBe(SIGN_IN_PATH);
        expect(router.state.location.pathname, path).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
        expect(router.state.historyAction, `${path} pushed, so Back would bounce off the blocked path`)
          .toBe('REPLACE');
        release();
      }
    });
  });

  describe('T40 AC12 the gate reaches exactly the eleven', () => {
    it('is one pathless route holding exactly AUTHENTICATED_ROUTE_PATHS, with the public nine and the catch-all beside it', () => {
      // WHAT THIS ADDS, AND WHAT IT DELIBERATELY DOES NOT. `routesResolveUnderAppPrefixOnly.test.tsx:159`
      // already pins the whole tree's SHAPE, and phase 7 measured it killing five of the six structural
      // mutants on its own - including the one that moves the public nine inside the gate, which changes no
      // behaviour at all because they match at the root first. What that pin cannot see is `element`: it maps
      // `path`. So this case claims the one thing left - WHICH component the gate is - and the derivation.
      // TASK 41 made `path === undefined` ambiguous here: the index route for the bare `/app/` is pathless
      // too. The filter is NARROWED rather than the count raised, because this case is about the gate, and a
      // length of two would pass whether the second pathless route were an index redirect or a second gate.
      // What identifies the gate is that it has children - it is a layout route - and task 41's index route
      // has none, which is also how `withProbes` tells them apart.
      const children = appRoutes[0].children ?? [];
      const pathless = children.filter((route) => route.path === undefined && route.children !== undefined);
      expect(children.filter((route) => route.path === undefined),
        'the root has some pathless route beyond the gate and task 41\'s index redirect').toHaveLength(2);

      expect(pathless, 'the gate is not one pathless route').toHaveLength(1);
      expect((pathless[0].element as ReactElement).type,
        'the pathless route carries some other component').toBe(RequireSession);
      expect((pathless[0].children ?? []).map((route) => route.path),
        "the gate's children are not exactly the declared authenticated set").toEqual([...AUTHENTICATED_ROUTE_PATHS]);
      expect(children.filter((route) => route.path !== undefined).map((route) => route.path),
        'the public nine and the catch-all are no longer siblings of the gate')
        .toEqual([...PUBLIC_ROUTE_PATHS, '*']);
    });
  });

  describe('T40 AC13 no element hides where the count pins cannot look', () => {
    it('is exactly one pathless route carrying exactly one element, and it is the gate', () => {
      // THE HOLE THIS TASK OPENS, and the reason it must be pinned here. Both screen-count pins -
      // `routesResolveUnderAppPrefixOnly.test.tsx:161` and AC9 above - record a route only when
      // `route.path !== undefined`. Task 40 is the first task to attach an element to a route with no path,
      // which is exactly what lets those pins keep their three; the cost is that an element on a pathless
      // route is invisible to both. `pathTree` cannot see it either - it maps `path`.
      //
      // MEASURED AT PHASE 9: an `errorElement` added to this very route SURVIVES all 1725 tests without this
      // case. The assertions below are the only thing in the repository that holds it.
      //
      // GROWN BY TASK 41, WHICH IS THE FIRST THING THIS CASE EVER CAUGHT - one task after it was written.
      // Task 41 attached an element to a second pathless route, the index route for the bare `/app/`, and
      // nothing else in the repository can see it: both screen-count pins and `pathTree` record a route only
      // when it has a `path`. So the count goes from one to two and the second is NAMED, which is the whole
      // difference between growing this pin and loosening it: a third pathless element still fails here, and
      // so does an `errorElement` on either of these two.
      const pathless = everyRoute(appRoutes).filter((route) => route.path === undefined);
      const carrying = pathless.filter((route) => route.element !== undefined
        || route.Component !== undefined || route.errorElement !== undefined);

      expect(carrying, 'a pathless route beyond the gate and task 41\'s index redirect carries an element')
        .toHaveLength(2);
      expect(carrying.map((route) => (route.element as ReactElement).type))
        .toEqual([RedirectToSignIn, RequireSession]);
      for (const route of carrying) {
        expect(route.errorElement, 'a pathless route carries a second element the count pins cannot see')
          .toBeUndefined();
        expect(route.Component, 'a pathless route carries a second element the count pins cannot see')
          .toBeUndefined();
      }
    });

    it('CONTROL: the sweep counts pathless elements, rather than returning a list that happens to match', () => {
      const planted: RouteObject[] = [
        { path: 'a', children: [{ element: <i />, children: [{ path: 'b' }] }] },
        { element: <i /> },
        { children: [{ path: 'c' }] },
      ];
      const pathless = everyRoute(planted).filter((route) => route.path === undefined);
      expect(pathless, 'a pathless route with no element is not even seen').toHaveLength(3);
      expect(pathless.filter((route) => route.element !== undefined),
        'the sweep does not count what it finds').toHaveLength(2);
    });
  });

  describe('T40 AC6, AC7 the gate asks one question and reaches for nothing else', () => {
    it('reaches for nothing it should not, by source', async () => {
      const code = await guardSource();
      for (const [forbidden] of GUARD_FORBIDDEN) {
        expect(code, `RequireSession.tsx reaches for ${forbidden.source}`).not.toMatch(forbidden);
      }
    });

    it('CONTROL: EVERY pattern in the sweep fires on a planted reach, and the sweep cannot shrink', async () => {
      // The PAIRING is the point, and it is T37's AC7 lesson: a single planted `sessionStorage` proves the
      // scan can see one surface and says nothing about the other forty-nine, so a typo in any other pattern
      // is unmeasured. A pattern cannot arrive here without a snippet that proves it fires.
      //
      // PHASE 11 (F2) BROUGHT THIS UP TO T37'S CONTROL, which had three things this one did not. F2 named
      // the duplicate check; reading that control to apply it found the other two.
      const code = await guardSource();
      for (const [forbidden, probe] of GUARD_FORBIDDEN) {
        // (1) Fired against the REAL SOURCE plus the probe, not against the probe alone. A pattern anchored
        // in a way that only works on a bare line - `^` without `m`, say - passes the isolated check and
        // then never fires on a file.
        expect(forbidden.test(`${code}\n${probe}`),
          `the scan cannot see a planted ${forbidden.source} - the pattern or the probe is wrong`).toBe(true);
        // (2) And the real source must not ALREADY trip it, or the row in the scan above proves nothing.
        expect(forbidden.test(code),
          `the real source already trips ${forbidden.source}, so that entry proves nothing`).toBe(false);
      }
      // (3) The sweep cannot shrink unnoticed: a deleted row would leave the loop passing on fewer surfaces.
      expect(GUARD_FORBIDDEN, 'the forbidden-surface sweep shrank').toHaveLength(50);
      // And a count cannot see a DUPLICATED row, which would mean one surface swept twice and another not at
      // all while the length still looked right (phase 11, F2).
      expect(new Set(GUARD_FORBIDDEN.map(([pattern]) => pattern.source)),
        'two rows of the sweep hold the same pattern').toHaveLength(GUARD_FORBIDDEN.length);
    });

    it('reads the session owner, which is the one thing it is allowed to reach for (AC7)', async () => {
      // The scan above forbids the session WRITERS and says nothing about the reader, because reading the
      // session is this module's entire job. This is the other half: it must actually ask, and ask THAT.
      const code = await guardSource();
      expect(code, 'the gate does not read the session at all').toMatch(/accessToken\s*\.\s*current\s*\(\s*\)/);
      expect(code, 'the gate decides on something other than truthiness').toMatch(/Boolean\s*\(\s*accessToken/);
    });

    it('lives in auth/ and imports nothing from app/ (AC6)', async () => {
      const code = await guardSource();
      expect(code, 'the guard reaches back into app/').not.toMatch(/from ['"]\.\.\/app\//);
      // And the direction that IS permitted, from the other side: `app/` names it.
      const routesCode = await readFile(join(dirname(expect.getState().testPath as string), 'routes.tsx'), 'utf8');
      expect(routesCode).toMatch(/import \{ RequireSession \} from '\.\.\/auth\/RequireSession';/);
    });
  });

  describe('T40 AC1 boundaries: the shapes a public link really arrives in', () => {
    it('serves a public path with a trailing slash', async () => {
      // MEASURED, not assumed. React Router normalises the trailing slash before matching, so `/app/verify/`
      // resolves to `verify` and is served without a session like any other public path. Pinned so a router
      // upgrade that changed it would be a failure rather than a surprise.
      const { container, router } = await arriveWithTable(withProbes(<RequireSession />), 'verify/');
      expect(probeIn(container)).toBe('verify');
      expect(router.state.historyAction).toBe('POP');
    });

    it('BLOCKS an authenticated path arriving with a query string or a trailing slash (AC2, phase 11 F4)', async () => {
      // The gate asks the session owner and reads no part of the URL - AC7's scan is what holds that - so
      // these two should be blocked exactly like the bare path. They are here because that is an inference
      // from a source scan, and a route-reading regression is precisely what would break it while leaving
      // the bare-path cases green.
      for (const arrival of [`${HOME_PATH}?foo=bar`, `${HOME_PATH}/`]) {
        const { container } = await arriveWithTable(withProbes(<RequireSession />), arrival);
        // Grown with the AC2 arms above: blocked now means the sign-in probe, not an empty container.
        expect(probeIn(container), `${arrival} was served without a session`).toBe(SIGN_IN_PATH);
        release();
      }
    });

    it('serves a public path with a query string and keeps the query (AC1)', async () => {
      // The resolution half is task 16's, at `routesResolveUnderAppPrefixOnly.test.tsx:102`. What is added
      // here is that the GATE does not touch it: an emailed verification link arrives with a token on it.
      const { container, router } = await arriveWithTable(withProbes(<RequireSession />), 'verify?token=abc');
      expect(probeIn(container)).toBe('verify');
      expect(router.state.location.search).toBe('?token=abc');
    });
  });

});
