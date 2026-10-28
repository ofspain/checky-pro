/// <reference types="node" />
/**
 * `tokensAreNeverWrittenToAnyStorage` (package.md §8, line 157) — R7, L3.
 *
 * Tasks 15 to 21 each built a step that could leak a token: the authorize request that writes three items to
 * sessionStorage, the exchange that receives two tokens, the screen that handles them, the holder that keeps one,
 * the boundary that puts it on a header, the channel that sends it to sibling tabs. Each checked its own surface.
 * This file checks the package.
 *
 * A leak is invisible by nature - a token in `localStorage` changes no behaviour until something reads it - so
 * the whole value of a sweep is its ability to report DIRTY. Every block below therefore proves its own
 * instrument before it is believed, and the cases that look redundant are the ones doing that work.
 *
 * WHAT "A TOKEN" MEANS HERE (T22-D2): the access token, the ID token - whole, each of its three segments, and any
 * decoded claim value - and any `refresh_token`, should one ever appear; L3 records that none is issued to this
 * client. NOT `state`, `nonce` or `code_verifier`: L16 permits those three in sessionStorage by name, so
 * forbidding them would contradict the spec. They are asserted PRESENT here, not absent.
 *
 * TWO INSTRUMENTS, AND THEY FAIL DIFFERENTLY (T22-D14). The runtime sweep spies `Storage.prototype` and all six
 * console channels, which makes it alias-proof by construction: a write through `globalThis['localStorage']`
 * still calls `Storage.prototype.setItem`, and `c.log` where `c === console` is still the spied method. That is
 * the primary instrument, and two controls below prove it. The source scan is text, so it catches the shapes
 * someone thought of over code no test executes - the second net, not the first.
 *
 * WHAT THIS FILE CANNOT DO, said here because a page of green invites the opposite conclusion:
 * - It does not build the bundle (T22-D1). The source scan covers this repository's own production code under
 *   `frontend/src`; the runtime sweep covers the sign-in -> callback -> exchange flow it drives and that flow's
 *   failure paths. A leak introduced at RUNTIME BY A DEPENDENCY - an import side effect, an unexercised error
 *   path, telemetry inside minified code - is covered by neither, and remains `bundleContainsNoSecretEnvKeys`'
 *   and dependency review's concern.
 * - jsdom provides neither `indexedDB` nor `caches` nor `cookieStore` (measured: all three `undefined`). An
 *   assertion that no database was opened, against an API that does not exist, would pass for the wrong reason.
 *   Those two surfaces are proven STRUCTURALLY - the source names neither - and said so rather than implied.
 * - The cookie claim is a sweep of `document.cookie`. It is not proof against a `Set-Cookie` response header or
 *   an `httpOnly` cookie, neither of which a jsdom document can see. The SAS session cookie is `httpOnly` and
 *   outside SPA control by design.
 * - A sentinel proves the sweep READS a surface. It cannot prove that a leak which does not exist would have
 *   reached that surface. That is the honest maximum and the cases say which of the two they are doing.
 *
 * NOT this task's, and asserted nowhere here: deleting the three L16 items at callback (task 23, which is also
 * the expected next entrant in the storage allowlist below), the full L16 inventory and R29's TTL (task 26),
 * recovery codes (69), receipts and the Cache Storage runtime (116), and the authorization `code` SAS leaves in
 * the callback URL - which is a one-time credential rather than a token, was placed there by the authorization
 * server rather than by the SPA, and belongs to task 24, which owns the post-login destination (T22-D3, T22-D9).
 */
import { readdir, readFile } from 'node:fs/promises';
import { dirname, join, relative } from 'node:path';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, type Mock, vi } from 'vitest';
import { createI18n } from '../i18n/i18n';
import { shareRenewalAcrossTabs, type TabLocks } from '../api/crossTabRenewal';
import { AUTHORIZE_STORAGE_KEYS, buildAuthorizeRequest } from './authorizeRequest';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/** Distinctive enough that a hit anywhere is this file's token and not a coincidence. */
const ACCESS_TOKEN = 'ACCESS-TOKEN-a41f7c92e5b8';
/** A second, unrelated value: a sweep that passes against one constant proves nothing (T22-D13). */
const OTHER_ACCESS_TOKEN = 'ACCESS-TOKEN-6b03d8e17fa4';
/** What a sweep says when a surface REFUSED to be read. Never an empty result, which would look clean. */
const UNREADABLE = '<<surface-unreadable>>';

type Mounted = { root: Root; container: HTMLElement };
let mounted: Mounted | undefined;

async function turn(): Promise<void> {
  await act(async () => {
    await new Promise((resolve) => {
      setTimeout(resolve, 0);
    });
  });
}

/**
 * Mints an ID token carrying the given claims. Encoded through TextEncoder rather than `btoa(JSON.stringify())`,
 * as T19's suite does: `btoa` throws above U+00FF. The signature is filler because nothing in the SPA verifies it.
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

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

/** What a replayed or mismatched code really gets back: HTTP 400 with an OAuth2 error object. */
function refusal(): Response {
  return jsonResponse({ error: 'invalid_grant', error_description: 'the code verifier did not match the challenge' }, 400);
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
 * Spies on the STORAGE PROTOTYPE, not on a store object, and must be installed before the flow begins.
 *
 * Measured in Phase 6: a probe that installed this after `buildAuthorizeRequest` reported ZERO `setItem` calls
 * for a flow that had just written three items, which is how an "exactly three writes" assertion becomes an
 * "exactly zero" assertion that keeps passing when a fourth write appears. Hence the explicit call order in
 * every case below, and `assertThreeAuthorizeWrites` reading it back.
 */
function watchStorage(): StorageWatch {
  return {
    setItem: vi.spyOn(Storage.prototype, 'setItem') as unknown as Mock,
    removeItem: vi.spyOn(Storage.prototype, 'removeItem') as unknown as Mock,
    clear: vi.spyOn(Storage.prototype, 'clear') as unknown as Mock,
  };
}

const CONSOLE_CHANNELS = ['log', 'info', 'warn', 'error', 'debug', 'trace'] as const;

function watchConsole(): Record<string, Mock> {
  const spies: Record<string, Mock> = {};
  for (const channel of CONSOLE_CHANNELS) {
    spies[channel] = vi.spyOn(console, channel).mockImplementation(() => undefined) as unknown as Mock;
  }
  return spies;
}

function loggedText(spies: Record<string, Mock>): string {
  return Object.values(spies)
    .flatMap((spy) => spy.mock.calls.flat())
    .map((argument) => String(argument))
    .join('\n');
}

/**
 * Every key and value in a store, or `UNREADABLE` if touching it threw.
 *
 * The distinction is the point (AC11): `sessionStorage.getItem` can THROW rather than return null - blocked site
 * data, some private modes - and a sweep that swallowed that would hand back an empty list, which reads exactly
 * like a clean store. A refusal has to look different from an absence or the sweep can be silenced.
 */
function storeEntries(store: Storage): Array<[string, string]> {
  try {
    const entries: Array<[string, string]> = [];
    for (let index = 0; index < store.length; index += 1) {
      const key = store.key(index);
      if (key === null) continue;
      entries.push([key, store.getItem(key) ?? '']);
    }
    return entries;
  } catch {
    return [[UNREADABLE, UNREADABLE]];
  }
}

type Surfaces = {
  session: Array<[string, string]>;
  local: Array<[string, string]>;
  cookie: string;
  title: string;
  windowName: string;
  historyState: string;
  documentUrl: string;
  routerLocation: string;
  routerNavigateState: string;
  document: string;
};

/**
 * Every surface the task statement names, plus T22-D8's two, as one structure.
 *
 * `routerLocation` is here because of the sharpest measurement in Phase 6: `createMemoryRouter` keeps its
 * location in router state and NEVER writes `window.location` or `history`. A sweep of `location.href` under this
 * harness reads the document's own untouched URL - not even the authorization `code` is in it - so "no token in a
 * URL", which is `agents.md:79`'s own clause, is INERT against `location.href` alone. The router's location is
 * where this SPA's navigation actually lands, and the control below proves it carries the callback query.
 *
 * The document is read whole, `documentElement.outerHTML`, rather than the mounted container: a portal, a
 * body-level node or an attribute would be outside a container read, and a reader will assume the document.
 */
function readSurfaces(router?: ReturnType<typeof createMemoryRouter>): Surfaces {
  const location = router?.state.location;
  return {
    session: storeEntries(globalThis.sessionStorage),
    local: storeEntries(globalThis.localStorage),
    cookie: document.cookie,
    title: document.title,
    windowName: window.name,
    historyState: JSON.stringify(globalThis.history.state ?? null),
    documentUrl: globalThis.location.href,
    routerLocation: location ? `${location.pathname}${location.search}${location.hash}` : '',
    // What `navigate(..., { state })` carries. In a real browser this IS `history.state`; under a memory router
    // it lives in router state instead, so sweeping only `history.state` would miss a token passed this way.
    routerNavigateState: JSON.stringify(location?.state ?? null),
    document: document.documentElement.outerHTML,
  };
}

/** One searchable text, so a case asks "is this value anywhere" rather than naming a place to look. */
function searchable(surfaces: Surfaces): string {
  return JSON.stringify(surfaces);
}

/** Every value a token could be hiding as: the token, and each of a JWT's three segments. */
function piecesOf(token: string): string[] {
  const segments = token.split('.');
  return segments.length === 3 ? [token, ...segments] : [token];
}

/* ─────────────────────────── the flow ─────────────────────────── */

type Arrival = {
  container: HTMLElement;
  router: ReturnType<typeof createMemoryRouter>;
  fetchSpy: Mock;
  holder: typeof import('./accessToken');
  unmount: () => void;
};

/**
 * Drives the real route table over the real exchange, with only the network stubbed.
 *
 * `holder` is the module instance THE SCREEN USED. `arriveAt` imports the route graph dynamically and
 * `vi.resetModules()` runs between cases, so the static import at the top of a test file is a different instance
 * from the one the screen wrote to. T20 lost three assertions to that and only a probe revealed it.
 */
async function arriveAt(url: string, answer: () => Response | Promise<Response>): Promise<Arrival> {
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
  await act(async () => {
    root.render(
      <I18nextProvider i18n={createI18n()}>
        <RouterProvider router={router} />
      </I18nextProvider>,
    );
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

/** The real authorize step, which is what writes the three L16 items. Returns what it generated. */
async function startSignIn(): Promise<{ state: string; nonce: string; verifier: string }> {
  await buildAuthorizeRequest();
  const read = (key: string) => {
    const value = globalThis.sessionStorage.getItem(key);
    expect(value, `the authorize step wrote ${key}`).toBeTruthy();
    return value!;
  };
  return {
    state: read(AUTHORIZE_STORAGE_KEYS.state),
    nonce: read(AUTHORIZE_STORAGE_KEYS.nonce),
    verifier: read(AUTHORIZE_STORAGE_KEYS.codeVerifier),
  };
}

/**
 * The response body, built from the nonce the authorize step really generated.
 *
 * This is T22-D13 in one function. Phase 6's probe searched for an ID token built from a FILE CONSTANT while the
 * flow carried one built from the live nonce - two different strings - so "the ID token is in no surface" was a
 * search for a token that never existed. Two of three segment searches survived only because the header and the
 * filler signature happened to match. Every case here searches for what `tokensFor` returned.
 */
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

function expectThreeAuthorizeWrites(watch: StorageWatch, started: { state: string; nonce: string; verifier: string }): void {
  const calls = watch.setItem.mock.calls as Array<[string, string]>;
  expect(calls.map(([key]) => key)).toEqual([
    AUTHORIZE_STORAGE_KEYS.state,
    AUTHORIZE_STORAGE_KEYS.nonce,
    AUTHORIZE_STORAGE_KEYS.codeVerifier,
  ]);
  expect(calls.map(([, value]) => value)).toEqual([started.state, started.nonce, started.verifier]);
  expect(watch.removeItem).not.toHaveBeenCalled();
  expect(watch.clear).not.toHaveBeenCalled();
}

/* ─────────────────────────── the source scan ─────────────────────────── */

/**
 * The storage allowlist (T22-D11, T22-D15): which production file may call which storage method, and why.
 *
 * A table rather than a regex, so a violation names the file and the method and says what to do. **Task 23 is
 * the expected next entrant**: L16 calls the three items single-use and deleted at callback, nothing deletes them
 * today, and `stateSurvivesRedirectAndIsConsumedOnce` is that task's. Whoever adds `removeItem` adds a row here
 * with a reason - that is the point of the table.
 */
const STORAGE_ALLOWLIST: ReadonlyArray<{ file: string; methods: readonly string[]; why: string }> = [
  {
    file: 'auth/authorizeRequest.ts',
    methods: ['setItem'],
    why: "L16's three items - state, nonce, code_verifier - written together once the PKCE challenge is in hand (task 15).",
  },
];

const STORAGE_METHODS = ['setItem', 'removeItem', 'clear'] as const;

type ScanTarget = { path: string; code: string };

/** A production file's code with its comments removed, because a file explains the rules it obeys. */
function strip(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
}

/**
 * Reads every production module under `src/`, comments stripped.
 *
 * It refuses an empty tree. A scan over nothing satisfies every prohibition at once, which is the failure mode
 * `bundleContainsNoSecretEnvKeys` names in its own header: a scanner never shown to catch a leak has verified
 * nothing.
 */
async function productionSources(): Promise<ScanTarget[]> {
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
  const targets: ScanTarget[] = [];
  for (const file of files) {
    const code = strip(await readFile(file, 'utf8'));
    targets.push({ path: relative(srcRoot, file).split(/[\\/]/).join('/'), code });
  }
  return targets;
}

/** Hits for one pattern, as `path:line`, over whatever targets it is given - including synthetic ones. */
function scanFor(targets: readonly ScanTarget[], pattern: RegExp): string[] {
  if (targets.length === 0) throw new Error('scan refused: nothing to scan');
  const hits: string[] = [];
  for (const { path, code } of targets) {
    code.split('\n').forEach((line, index) => {
      if (pattern.test(line)) hits.push(`${path}:${index + 1}`);
    });
  }
  return hits;
}

/**
 * Patterns no production file may contain, each with a violation that must match - the self-proof. A pattern that
 * matched nothing would pass every file in the repository and look like a result.
 */
const FORBIDDEN: ReadonlyArray<{ label: string; pattern: RegExp; violation: string }> = [
  { label: 'local storage', pattern: /localStorage/, violation: "localStorage.setItem('t', token);" },
  { label: 'local storage by bracket', pattern: /\[['"](?:local|session)Storage['"]\]/, violation: "globalThis['localStorage'].setItem('t', token);" },
  { label: 'IndexedDB', pattern: /indexedDB|openDatabase/, violation: "indexedDB.open('tokens');" },
  { label: 'a cookie', pattern: /document\.cookie/, violation: "document.cookie = `t=${token}`;" },
  { label: 'a console call', pattern: /console\./, violation: 'console.warn(token);' },
  { label: 'a console by bracket', pattern: /\[['"]console['"]\]/, violation: "globalThis['console'].log(token);" },
  { label: 'a beacon', pattern: /sendBeacon/, violation: "navigator.sendBeacon('/t', token);" },
  { label: 'an image or audio request', pattern: /new\s+(?:Image|Audio)\s*\(/, violation: 'new Image().src = url + token;' },
  { label: 'a src assignment', pattern: /\.src\s*=/, violation: 'element.src = url + token;' },
  { label: 'an absolute address', pattern: /https?:\/\//, violation: "void fetch('https://example.com/' + token);" },
  { label: 'the Cache Storage API', pattern: /caches\./, violation: "await caches.open('tokens');" },
  {
    // `this.name =` is three Error subclasses naming themselves (crossTabRenewal.ts, sessionBoundary.ts), which
    // is why this pattern is anchored to the two globals rather than to `.name =`.
    label: 'a write to window.name',
    pattern: /(?:window|globalThis|self)\.name\s*=/,
    violation: 'window.name = token;',
  },
];

describe('tokensAreNeverWrittenToAnyStorage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.resetModules();
    globalThis.sessionStorage.clear();
    globalThis.localStorage.clear();
    document.title = '';
    window.name = '';
    globalThis.history.replaceState(null, '');
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

  describe('a completed sign-in leaves no token behind (AC1, AC2, AC3, AC5, AC7)', () => {
    it('puts no part of either token in any surface, and holds the token it was given', async () => {
      const watch = watchStorage();
      const logs = watchConsole();
      const started = await startSignIn();
      const body = tokensFor(started.nonce);

      const arrival = await arriveAt(`/callback?code=THE-CODE&state=${started.state}`, () => jsonResponse(body));

      // The control, and it is not decoration: a flow that never reached the holder would satisfy every
      // absence assertion below it. T22-D13's guard comes with it - the value swept for is the value sent.
      expect(arrival.holder.accessToken.current()).toBe(body.access_token);
      expect(body.access_token).toBe(ACCESS_TOKEN);

      const text = searchable(readSurfaces(arrival.router));
      for (const piece of piecesOf(body.access_token as string)) expect(text).not.toContain(piece);
      for (const piece of piecesOf(body.id_token as string)) expect(text).not.toContain(piece);
      expect(text).not.toContain(UNREADABLE);
      expect(loggedText(logs)).toBe('');
      expectThreeAuthorizeWrites(watch, started);
    });

    it('sweeps a SECOND, unrelated token the same way, so no constant can pass (AC1)', async () => {
      watchStorage();
      const started = await startSignIn();
      const body = tokensFor(started.nonce, { access_token: OTHER_ACCESS_TOKEN });

      const arrival = await arriveAt(`/callback?code=THE-CODE&state=${started.state}`, () => jsonResponse(body));

      expect(arrival.holder.accessToken.current()).toBe(OTHER_ACCESS_TOKEN);
      const text = searchable(readSurfaces(arrival.router));
      for (const piece of piecesOf(OTHER_ACCESS_TOKEN)) expect(text).not.toContain(piece);
      for (const piece of piecesOf(body.id_token as string)) expect(text).not.toContain(piece);
    });

    it("leaves session storage holding exactly L16's three items, with the values the authorize step generated (AC2)", async () => {
      watchStorage();
      const started = await startSignIn();
      const body = tokensFor(started.nonce);

      await arriveAt(`/callback?code=THE-CODE&state=${started.state}`, () => jsonResponse(body));

      // Set equality by construction: the whole store is compared, so a fourth item fails here. The three
      // values are asserted PRESENT - L16 names them, so absence would be the defect (task 23 owns deleting
      // them, and this assertion is what that task will have to change).
      expect(storeEntries(globalThis.sessionStorage).sort()).toEqual(
        [
          [AUTHORIZE_STORAGE_KEYS.state, started.state],
          [AUTHORIZE_STORAGE_KEYS.nonce, started.nonce],
          [AUTHORIZE_STORAGE_KEYS.codeVerifier, started.verifier],
        ].sort(),
      );
      expect(storeEntries(globalThis.localStorage)).toEqual([]);
    });

    it('adds nothing to the cookie jar (AC3)', async () => {
      const before = document.cookie;
      watchStorage();
      const started = await startSignIn();

      await arriveAt(`/callback?code=THE-CODE&state=${started.state}`, () =>
        jsonResponse(tokensFor(started.nonce)),
      );

      // A sweep of `document.cookie` only. It is not proof against a Set-Cookie header or an httpOnly cookie,
      // neither of which this document can see; the header says so, and the SAS session cookie is deliberately
      // one of those.
      expect(document.cookie).toBe(before);
    });

    it('leaves a token nowhere in the URL the SPA navigated to (AC5)', async () => {
      watchStorage();
      const started = await startSignIn();
      const body = tokensFor(started.nonce);

      const arrival = await arriveAt(`/callback?code=THE-CODE&state=${started.state}`, () => jsonResponse(body));

      // The control that makes this a measurement. `createMemoryRouter` never writes `window.location`, so a
      // sweep of `location.href` reads an untouched document and passes against anything; the router's location
      // is the surface that moves. Proving it carries the callback query is proving the sweep looks somewhere.
      const swept = readSurfaces(arrival.router);
      expect(swept.routerLocation).toContain('code=THE-CODE');
      expect(swept.routerLocation).toContain(`state=${started.state}`);

      for (const piece of piecesOf(body.access_token as string)) expect(swept.routerLocation).not.toContain(piece);
      for (const piece of piecesOf(body.id_token as string)) expect(swept.routerLocation).not.toContain(piece);
      expect(swept.historyState).toBe('null');
      expect(swept.documentUrl).not.toContain(body.access_token as string);
    });
  });

  describe('every failure path leaves no token behind (AC8)', () => {
    /**
     * Each row's failure is the ONLY reason nothing is held.
     *
     * Phase 6's probe sent an ID token built from a file constant while the live nonce was random, so every row
     * failed twice - at its own fault and again at the nonce - and measured neither. Here `answer` receives the
     * live nonce, and only the last row sends a mismatched one.
     */
    const FAULTS: ReadonlyArray<{
      label: string;
      url: (state: string) => string;
      answer: (nonce: string) => Response | Promise<Response>;
    }> = [
      { label: 'no state in the query', url: () => '/callback?code=THE-CODE', answer: (n) => jsonResponse(tokensFor(n)) },
      { label: 'a state that does not match', url: () => '/callback?code=THE-CODE&state=not-the-stored-state', answer: (n) => jsonResponse(tokensFor(n)) },
      { label: 'no code', url: (s) => `/callback?state=${s}`, answer: (n) => jsonResponse(tokensFor(n)) },
      { label: 'an authorization error', url: (s) => `/callback?error=access_denied&state=${s}`, answer: (n) => jsonResponse(tokensFor(n)) },
      { label: 'a refused code', url: (s) => `/callback?code=THE-CODE&state=${s}`, answer: () => refusal() },
      { label: 'a server fault', url: (s) => `/callback?code=THE-CODE&state=${s}`, answer: () => new Response('', { status: 500 }) },
      {
        label: 'a dropped connection',
        url: (s) => `/callback?code=THE-CODE&state=${s}`,
        answer: () => {
          throw new TypeError('Failed to fetch');
        },
      },
      { label: 'a response with no usable tokens', url: (s) => `/callback?code=THE-CODE&state=${s}`, answer: () => jsonResponse({}) },
      {
        label: 'a nonce that is not the stored one',
        url: (s) => `/callback?code=THE-CODE&state=${s}`,
        answer: () => jsonResponse(tokensFor('a-nonce-this-tab-never-generated')),
      },
    ];

    it.each(FAULTS)('holds nothing and leaks nothing after $label', async ({ url, answer }) => {
      const watch = watchStorage();
      const logs = watchConsole();
      const started = await startSignIn();
      const body = tokensFor(started.nonce);

      const arrival = await arriveAt(url(started.state), () => answer(started.nonce));

      expect(arrival.holder.accessToken.current()).toBeNull();
      const text = searchable(readSurfaces(arrival.router));
      for (const piece of piecesOf(body.access_token as string)) expect(text).not.toContain(piece);
      for (const piece of piecesOf(body.id_token as string)) expect(text).not.toContain(piece);
      // Not even the authorization server's own words, which the refusal row carries.
      expect(text).not.toContain('the code verifier did not match the challenge');
      expect(loggedText(logs)).toBe('');
      // A failure writes nothing and deletes nothing: the three items are task 23's to consume.
      expectThreeAuthorizeWrites(watch, started);
    });

    it('leaves nothing from a FIRST sign-in behind when a second one runs in the same tab (AC1)', async () => {
      watchStorage();
      const first = await startSignIn();
      const firstBody = tokensFor(first.nonce);
      const one = await arriveAt(`/callback?code=THE-CODE&state=${first.state}`, () => jsonResponse(firstBody));
      expect(one.holder.accessToken.current()).toBe(ACCESS_TOKEN);
      one.unmount();
      vi.unstubAllGlobals();

      // A second sign-in in the same tab OVERWRITES the three items - which is the one path where a value from
      // the first attempt could survive into the second - and this one is then discarded.
      const second = await startSignIn();
      expect(second.state).not.toBe(first.state);
      const arrival = await arriveAt('/callback?code=THE-CODE&state=not-the-stored-state', () =>
        jsonResponse(tokensFor(second.nonce)),
      );

      const text = searchable(readSurfaces(arrival.router));
      for (const piece of piecesOf(firstBody.access_token as string)) expect(text).not.toContain(piece);
      for (const piece of piecesOf(firstBody.id_token as string)) expect(text).not.toContain(piece);
      expect(storeEntries(globalThis.sessionStorage).map(([, value]) => value).sort()).toEqual(
        [second.state, second.nonce, second.verifier].sort(),
      );
    });

    it('leaves no residue in any surface once the holder is cleared', async () => {
      watchStorage();
      const started = await startSignIn();
      const body = tokensFor(started.nonce);
      const arrival = await arriveAt(`/callback?code=THE-CODE&state=${started.state}`, () => jsonResponse(body));
      expect(arrival.holder.accessToken.current()).toBe(ACCESS_TOKEN);

      arrival.holder.clearAccessToken();

      expect(arrival.holder.accessToken.current()).toBeNull();
      const text = searchable(readSurfaces(arrival.router));
      for (const piece of piecesOf(body.access_token as string)) expect(text).not.toContain(piece);
    });
  });

  describe("the instruments catch what they are looking for (AC10, AC11, AC15)", () => {
    it('finds a token written to session storage under an innocuous key (AC10)', () => {
      globalThis.sessionStorage.setItem('ui.theme', `dark|${ACCESS_TOKEN}`);

      // T22-D5 in one assertion: the sweep searches VALUES. A key-list check would have called this store clean,
      // and a token hidden under a plausible key is the shape a real leak takes.
      expect(searchable(readSurfaces())).toContain(ACCESS_TOKEN);
    });

    it('finds a token written to local storage, through a bracket alias (AC10, T22-D14)', () => {
      const aliased = (globalThis as unknown as Record<string, Storage>)['localStorage'];
      const watch = watchStorage();

      aliased.setItem('anything', ACCESS_TOKEN);

      // Both instruments, on the shape a source scan cannot see. The prototype spy is alias-proof because the
      // alias resolves to the same method - which is why the runtime sweep is the primary instrument and the
      // text scan is the second net.
      expect(searchable(readSurfaces())).toContain(ACCESS_TOKEN);
      expect(watch.setItem).toHaveBeenCalledWith('anything', ACCESS_TOKEN);
    });

    it('finds a token leaked as a bearer header value (AC15)', () => {
      globalThis.sessionStorage.setItem('last.request', `Authorization: Bearer ${ACCESS_TOKEN}`);

      // The substring search covers a header-shaped leak with no extra search term: `Bearer <token>` contains
      // the token. Proven rather than asserted in prose.
      expect(searchable(readSurfaces())).toContain(ACCESS_TOKEN);
    });

    it('finds a token in each of the three surfaces that are otherwise empty boxes (AC10)', () => {
      // `title`, `window.name` and `history.state` come back empty in every flow above - because nothing in this
      // package writes them, not because anything cleaned them. A sentinel is what makes those assertions worth
      // reading. It proves the sweep READS the surface; it cannot prove a leak that does not exist would have
      // landed there, and the header says so.
      document.title = `Checky ${ACCESS_TOKEN}`;
      expect(searchable(readSurfaces())).toContain(ACCESS_TOKEN);
      document.title = '';

      window.name = ACCESS_TOKEN;
      expect(searchable(readSurfaces())).toContain(ACCESS_TOKEN);
      window.name = '';

      globalThis.history.replaceState({ token: ACCESS_TOKEN }, '');
      expect(searchable(readSurfaces())).toContain(ACCESS_TOKEN);
      globalThis.history.replaceState(null, '');
    });

    it('reads the whole document, not the mounted container (AC10, I4)', async () => {
      const outside = document.createElement('div');
      outside.dataset.leak = ACCESS_TOKEN;
      document.body.append(outside);
      try {
        expect(searchable(readSurfaces())).toContain(ACCESS_TOKEN);
      } finally {
        outside.remove();
      }
    });

    it('finds a token handed to a console channel, including through an alias (AC10, T22-D14)', () => {
      const logs = watchConsole();
      const aliased = (globalThis as unknown as Record<string, Console>)['console'];

      aliased.log(`sending ${ACCESS_TOKEN}`);
      console.error(new Error(`failed with ${OTHER_ACCESS_TOKEN}`));

      expect(loggedText(logs)).toContain(ACCESS_TOKEN);
      // A thrown Error's message is a log the moment anything prints it, which is why T22-D4 counts it.
      expect(loggedText(logs)).toContain(OTHER_ACCESS_TOKEN);
    });

    it('says a surface was UNREADABLE rather than reporting it clean when touching it throws (AC11)', () => {
      globalThis.sessionStorage.setItem('ui.theme', ACCESS_TOKEN);
      vi.spyOn(Storage.prototype, 'key').mockImplementation(() => {
        throw new DOMException('The operation is insecure.', 'SecurityError');
      });

      const text = searchable(readSurfaces());

      // The failure mode this guards: a sweep that swallowed the throw would return an empty list, which reads
      // exactly like a clean store - and the token above would be invisible. A refusal has to look different
      // from an absence, or the instrument can be silenced by blocking site data.
      expect(text).toContain(UNREADABLE);
      expect(text).not.toContain(ACCESS_TOKEN);
    });

    it('refuses to scan nothing (AC10)', () => {
      // A scan over an empty tree satisfies every prohibition at once. `bundleContainsNoSecretEnvKeys` refuses
      // one for the same reason, and this is the same instrument's second net.
      expect(() => scanFor([], /localStorage/)).toThrow(/refused/);
    });
  });

  describe('the package itself names no leak (AC9, AC13)', () => {
    it.each(FORBIDDEN)('contains no $label, in any production file', async ({ pattern, violation }) => {
      const targets = await productionSources();
      expect(targets.length).toBeGreaterThan(20);

      expect(scanFor(targets, pattern)).toEqual([]);
      // The scan proves itself twice: against a synthetic file through the SAME function, and by refusing to
      // report a result for a pattern that matches nothing.
      expect(scanFor([{ path: 'synthetic.ts', code: violation }], pattern)).toEqual(['synthetic.ts:1']);
    });

    it('calls a storage method only where the allowlist permits it (AC9, T22-D11, T22-D15)', async () => {
      const targets = await productionSources();

      const offences: string[] = [];
      for (const { path, code } of targets) {
        for (const method of STORAGE_METHODS) {
          if (!new RegExp(`\\b${method}\\s*\\(`).test(code)) continue;
          const entry = STORAGE_ALLOWLIST.find((row) => row.file === path);
          if (entry === undefined || !entry.methods.includes(method)) {
            offences.push(
              `${path} calls ${method}(), which the storage allowlist does not permit. Add a row to ` +
                'STORAGE_ALLOWLIST naming the file, the method and why it is sanctioned - or do not write to storage.',
            );
          }
        }
      }
      expect(offences).toEqual([]);

      // Every allowlisted file exists and really makes the call it is permitted - a stale row would quietly
      // widen the permission for a file that no longer needs it. Task 23 is the expected next entrant.
      for (const row of STORAGE_ALLOWLIST) {
        const target = targets.find(({ path }) => path === row.file);
        expect(target, `${row.file} is on the allowlist but not in the tree`).toBeDefined();
        for (const method of row.methods) expect(target!.code).toMatch(new RegExp(`\\b${method}\\s*\\(`));
      }
      // And the same scan, over a synthetic file, does report an offence - so the empty list above is a result.
      expect(new RegExp('\\bsetItem\\s*\\(').test("sessionStorage.setItem('t', token);")).toBe(true);
    });

    it('touches session storage in only the four files that read it (AC9)', async () => {
      const targets = await productionSources();

      // Files, not `path:line`. Stripping a block comment removes its newlines, so a line number in the
      // stripped text is not the line number in the file - it would be a brittle assertion about a number no
      // reader could look up. The file set is the claim worth making: four readers, one of them the writer.
      const files = [...new Set(scanFor(targets, /sessionStorage/).map((hit) => hit.split(':')[0]))].sort();
      expect(files).toEqual([
        'auth/CallbackScreen.tsx',
        'auth/authorizeRequest.ts',
        'auth/codeExchange.ts',
        'auth/idTokenNonce.ts',
      ]);
    });

    it('leaves the three Error subclasses that name themselves alone (AC9)', async () => {
      const targets = await productionSources();

      // The window.name pattern is anchored to the globals for this reason: `this.name = 'RenewalTimeoutError'`
      // is three classes naming themselves, and a scan for `/\.name\s*=/` would have condemned all three.
      expect(scanFor(targets, /this\.name\s*=/).length).toBe(3);
      expect(scanFor(targets, /(?:window|globalThis|self)\.name\s*=/)).toEqual([]);
    });

    it('strips comments without emptying any file (AC9, AC10)', async () => {
      const targets = await productionSources();

      // A strip that emptied a file would make every scan over it vacuous - and one file here is 89% comment.
      for (const { path, code } of targets) {
        expect(code.trim().length, `${path} survived comment stripping`).toBeGreaterThan(100);
      }
      // And the strip really removes prose: `accessToken.ts:12` names sessionStorage in order to FORBID it, which
      // is how T21's first scan read a rule as the violation it warns about.
      const holder = targets.find(({ path }) => path === 'auth/accessToken.ts');
      expect(holder?.code).not.toContain('sessionStorage');
      expect(holder?.code).toContain('let held: string | null = null;');
    });
  });

  describe("what this file cannot prove here, said rather than implied (AC4)", () => {
    it('has no IndexedDB and no Cache Storage to observe in this environment', () => {
      // Measured, not assumed. An `expect(indexedDB.databases()).toEqual([])` would throw here, and a
      // `typeof indexedDB === 'undefined'` assertion proves the ENVIRONMENT, not the code - so AC4 is carried by
      // the source scan above, and this case exists to keep the limitation visible to a reader.
      expect(typeof (globalThis as Record<string, unknown>).indexedDB).toBe('undefined');
      expect(typeof (globalThis as Record<string, unknown>).caches).toBe('undefined');
      expect(typeof (globalThis as Record<string, unknown>).cookieStore).toBe('undefined');
    });

    it('leaves the Cache Storage policy to the module that owns it (AC4)', async () => {
      // R57's own named test owns the runtime behaviour. Referenced, not duplicated: `/oauth2/token` is already
      // NetworkOnly there, and `apiRequestPolicy.ts` states why the Cache Storage API counts as storage at all.
      const policy = await import('../pwa/apiRequestPolicy');

      expect(policy.NETWORK_ONLY_PATH_PREFIXES).toContain('/oauth2');
      expect(policy.NETWORK_ONLY_PATH_PREFIXES).toContain('/accounts');
    });
  });

  describe("a sibling tab's token is no different (AC12)", () => {
    it('keeps an adopted token out of every surface, through the real cross-tab listener', async () => {
      const holder = await import('./accessToken');
      const watch = watchStorage();
      const logs = watchConsole();
      const listeners: Array<(event: MessageEvent) => void> = [];
      const channel = {
        addEventListener: (_type: string, listener: (event: MessageEvent) => void) => listeners.push(listener),
        removeEventListener: () => undefined,
        postMessage: () => undefined,
        close: () => undefined,
      } as unknown as BroadcastChannel;
      // Injected because jsdom has no `navigator.locks`: with no options the module takes its degraded per-tab
      // branch and the production listener never runs, which is how T21's first version of this case passed
      // without exercising anything.
      const locks = { request: () => Promise.reject(new Error('this case does not renew')) } as unknown as TabLocks;

      const shared = shareRenewalAcrossTabs(holder.accessToken, { channel, locks });
      try {
        expect(listeners).toHaveLength(1);
        listeners[0]?.({ data: { type: 'renewal:succeeded', token: OTHER_ACCESS_TOKEN } } as MessageEvent);

        expect(holder.accessToken.current()).toBe(OTHER_ACCESS_TOKEN);
        const text = searchable(readSurfaces());
        expect(text).not.toContain(OTHER_ACCESS_TOKEN);
        expect(loggedText(logs)).toBe('');
        expect(watch.setItem).not.toHaveBeenCalled();
      } finally {
        shared.close();
        holder.clearAccessToken();
      }
    });
  });
});
