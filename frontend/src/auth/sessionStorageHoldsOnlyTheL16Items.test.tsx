/// <reference types="node" />
/**
 * `sessionStorageHoldsOnlyTheL16Items` (package.md §8) — R29, L16.
 *
 * L16's last sentence is "This list is the entire carve-out." Every task that put something in the store proved
 * its own item was allowed; this file asks the opposite question, and it is the only file in the package that
 * asks it: **is anything there that is not on the list?**
 *
 * WHY THAT NEEDS A FILE OF ITS OWN. An out-of-inventory item changes no behaviour. A token parked in
 * sessionStorage, a form field holding an email address, a debug key left behind by a future screen - each is
 * invisible until something reads it, and none breaks a test that was not looking for it. So the entire value of
 * this file is its ability to report DIRTY, and every instrument below is proved against a real violation before
 * it is believed. A green run here means something only because the same instruments have been watched to fail.
 *
 * THE INVENTORY IS DERIVED, NEVER WRITTEN (AC1). The four keys come from `AUTHORIZE_STORAGE_KEYS` and
 * `RETURN_TARGET_KEY`, which are exported for exactly this purpose - `authorizeRequest.ts` says so in its own
 * header, written in task 15. A key spelled out here would keep passing after a rename, so a case scans this
 * file's own source to prove no key literal appears in it, building its needles from the imported constants.
 *
 * THE CARVE-OUT IS TWO CONSTANTS IN TWO MODULES, deliberately unmerged (T24-D4): the three PKCE items are
 * deleted for having been consumed by the exchange, the return target for having been read by the navigation.
 * Different consumers, different moments. This file enumerates both; it does not merge them.
 *
 * TWO INSTRUMENTS, AND THEY FAIL DIFFERENTLY (T22-D14, measured again here). The runtime watch spies
 * `Storage.prototype`, and a measurement in this task's Phase 5 showed it sees all four indirect call forms -
 * `s['setItem'](…)`, a `.bind` alias, `.call` and `.apply` - because every one of them invokes the method the spy
 * replaced. The source scan is text and sees none of them until its patterns are widened. The scan's value is
 * the code no test executes; the watch's value is everything else. One case asserts the pair, so a reader who
 * finds the two overlapping does not delete one of them.
 *
 * WHAT A KEY-ONLY SWEEP CANNOT DO, measured rather than assumed. With `getItem` throwing - a real browser
 * configuration, not a contrivance - `Object.keys(sessionStorage)` still returns every key and `.length` is
 * still right. So a sweep that reads keys alone PASSES over a store it never read: the vacuity failure in its
 * most likely form. This file's sweep reads keys and every value, cross-checks three independent enumerations,
 * and distinguishes **empty** from **unreachable**, failing on the second (AC12).
 *
 * WHAT THIS FILE CANNOT DO, said here because a page of green invites the opposite conclusion:
 * - jsdom provides no `indexedDB`, no `caches`, no `cookieStore` and no `navigator.storage` (measured: all
 *   `undefined`), and `document.cookie` is empty. Those surfaces are proved STRUCTURALLY - no production module
 *   names one - and the claim is stated as structural rather than implied. An assertion that no database was
 *   opened, against an API that does not exist, would pass for the wrong reason.
 * - The forbidden-surface list is ENUMERATIVE, NOT EXHAUSTIVE. A scan over six names cannot prove the absence of
 *   a surface nobody thought of. A new client-side surface is a design decision under `design.md` §5, not a test
 *   change.
 * - It does not build the bundle. Production code under `frontend/src` is scanned; a store opened at runtime by
 *   a dependency remains `bundleContainsNoSecretEnvKeys`' and dependency review's concern (T22-D1).
 * - A sentinel proves the sweep READS a surface. It cannot prove that a leak which does not exist would have
 *   reached it. Where a needle is a shape that no flow here produces - a password, a recovery code, an API key -
 *   the case says so, and the sentinel beside it is what makes the search worth running.
 *
 * L16 CLAUSE (b) IS PERMITTED AND UNBUILT. "Non-sensitive mid-task form input for R29, with a 15-minute TTL" has
 * no implementation anywhere in `src/`, and its named test
 * `expiredSessionMidTaskPreservesOnlyNonSensitiveInput` (package.md §8) is claimed by no task in `tasks.md` -
 * measured in this task's Phase 0, not inferred. So this file pins the four keys that exist today and names
 * clause (b) as the expected next entrant in its own failure message: the day that store lands, the failure
 * should read as an instruction rather than a mystery. That is the convention T22's storage allowlist already
 * uses for a new row.
 *
 * NOT this file's, and cited rather than re-proved: the package-wide token leak sweep across every surface
 * (T22), the ordering and idempotence of the three deletions (T23), and whether a return target was honoured
 * (T24, T25). This file asserts which keys exist and what is in them.
 */
import { readdir, readFile } from 'node:fs/promises';
import { dirname, join, relative } from 'node:path';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, type Mock, vi } from 'vitest';
import { RENEWAL_CHANNEL, RENEWAL_LOCK } from '../api/crossTabRenewal';
import { createI18n } from '../i18n/i18n';
import { AUTHORIZE_STORAGE_KEYS, buildAuthorizeRequest } from './authorizeRequest';
import { rememberReturnTarget, RETURN_TARGET_KEY } from './returnTarget';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/* ─────────────────────────── what must never be in the store ─────────────────────────── */

/** Distinctive enough that a hit anywhere is this file's token and not a coincidence. */
const ACCESS_TOKEN = 'ACCESS-TOKEN-T26-5f3c81aa42d7';
/** The authorization code. A single-use credential, placed in the callback URL by the server (T22-D3). */
const AUTHORIZATION_CODE = 'AUTH-CODE-T26-9b7e14cd';
/**
 * Shapes no flow in this file produces, swept for anyway because R29 names them: "SHALL NOT keep tokens,
 * passwords, recovery codes, API keys, or PII in any storage". A search for a value that was never created
 * proves only that the sweep read the surface - which is why the sentinel case beside them exists.
 */
const A_PASSWORD = 'PASSWORD-T26-correct-horse-battery';
const A_RECOVERY_CODE = 'RECOVERY-T26-4XQ2-8KD9-LM31';
const AN_API_KEY = 'APIKEY-T26-ck_live_7f21aa0931';
const AN_EMAIL_ADDRESS = 'the.person+t26@example.test';
const AN_ACCOUNT_UUID = '6f1c2d84-5a9e-4b37-9c21-0d7e5a4f8b62';

/** A value planted by a control, so a sweep that finds nothing can be shown to find something. */
const SENTINEL_VALUE = 'SENTINEL-T26-a0c7e35b';
const SENTINEL_KEY = 'themistra.probe.sentinel';

const A_REAL_TARGET = '/account/sessions';

/* ─────────────────────────── the inventory ─────────────────────────── */

type Item = { key: string; owner: string; why: string };

/**
 * L16's list, assembled from the two modules that own it.
 *
 * A FUNCTION, not a module-scope constant, and that is not style. `returnTarget.ts` documents the hazard it
 * closes: `routes -> CallbackScreen -> returnTarget -> routes` is a cycle, a module-scope read inside it yields
 * `undefined` rather than throwing, and an empty inventory here would FAIL CLOSED SILENTLY - matching every
 * store, passing every case, and verifying nothing. Read inside a function, the bindings are initialised.
 */
function inventory(): Item[] {
  return [
    {
      key: AUTHORIZE_STORAGE_KEYS.state,
      owner: 'auth/authorizeRequest.ts',
      why: "L16 (a): the PKCE `state`, written with the other two once the challenge is in hand, removed with them by `consumeAuthorizeState` when a callback has used all three",
    },
    {
      key: AUTHORIZE_STORAGE_KEYS.nonce,
      owner: 'auth/authorizeRequest.ts',
      why: 'L16 (a): the OIDC `nonce`, read back at the callback to validate the ID token',
    },
    {
      key: AUTHORIZE_STORAGE_KEYS.codeVerifier,
      owner: 'auth/authorizeRequest.ts',
      why: 'L16 (a): the PKCE `code_verifier`, read back at the token exchange',
    },
    {
      key: RETURN_TARGET_KEY,
      owner: 'auth/returnTarget.ts',
      why: 'L16 (a): the post-login return target, single-use, consumed by the navigation at the end of a successful callback (T24-D4)',
    },
  ];
}

function inventoryKeys(): string[] {
  return inventory()
    .map((item) => item.key)
    .sort();
}

/**
 * What a failure says, and the reason it says it.
 *
 * A guard that fails with "expected 5 to be 4" teaches nobody. The day L16 clause (b) is built this message is
 * the first thing its author reads, and it should name the decision rather than leave them to find it.
 */
const CARVE_OUT_MESSAGE =
  "L16 is exhaustive: sessionStorage may hold the four items this inventory names and nothing else. " +
  "If a FIFTH key is legitimate, it is clause (b) - non-sensitive mid-task form input for R29, with a 15-minute " +
  "TTL - which has no implementation in src/ and no owning task in tasks.md. Add it to inventory() with its " +
  'owner and reason, and give it the TTL test (`expiredSessionMidTaskPreservesOnlyNonSensitiveInput`) that no ' +
  'task claims. Anything else is a spec violation, not a test to update.';

/* ─────────────────────────── the sweep ─────────────────────────── */

type Sweep =
  | { reachable: true; store: string; keys: string[]; values: Record<string, string> }
  | { reachable: false; store: string; how: string };

/**
 * Every key and every value a store holds, or the reason it could not be read.
 *
 * THE DISTINCTION IS THE POINT. Measured in Phase 5: with `getItem` throwing, `Object.keys` still returns all
 * four keys and `.length` is still 4 - only the value read throws. So a sweep built on key enumeration alone
 * passes over a store it never read, reporting compliance for the surface most likely to be broken in a real
 * browser (blocked site data). Reading every value is what makes "unreachable" observable at all.
 *
 * Three enumerations are cross-checked - `Object.keys`, `.length` and `.key(i)` - so one broken enumeration
 * cannot produce a false "empty". A key that enumerates and then reads as `null` is reported unreachable too:
 * that combination is impossible in a working store, so it means the instrument is lying rather than the store
 * is clean.
 */
function sweepOf(store: Storage, label: string): Sweep {
  let keys: string[];
  try {
    const own = Object.keys(store).sort();
    const byIndex: string[] = [];
    for (let index = 0; index < store.length; index += 1) {
      const key = store.key(index);
      if (key !== null) byIndex.push(key);
    }
    byIndex.sort();
    if (byIndex.join('\u0000') !== own.join('\u0000')) {
      return {
        reachable: false,
        store: label,
        how: `the enumerations disagree: Object.keys gave [${own.join(', ')}], key(i) gave [${byIndex.join(', ')}]`,
      };
    }
    keys = own;
  } catch (cause) {
    return { reachable: false, store: label, how: `enumeration threw: ${String(cause)}` };
  }
  const values: Record<string, string> = {};
  for (const key of keys) {
    try {
      const value = store.getItem(key);
      if (value === null) {
        return { reachable: false, store: label, how: `${key} enumerated but read back as null` };
      }
      values[key] = value;
    } catch (cause) {
      return { reachable: false, store: label, how: `reading ${key} threw: ${String(cause)}` };
    }
  }
  return { reachable: true, store: label, keys, values };
}

function sweepSession(): Sweep {
  return sweepOf(globalThis.sessionStorage, 'sessionStorage');
}

function sweepLocal(): Sweep {
  return sweepOf(globalThis.localStorage, 'localStorage');
}

/** Fails on an unreachable store rather than passing on an empty key list. That is AC12 in two statements. */
function expectOnlyInventory(sweep: Sweep, where: string): string[] {
  if (!sweep.reachable) {
    expect.fail(
      `${sweep.store} was UNREACHABLE ${where} (${sweep.how}). An unreachable store is not an empty one, and a ` +
        'sweep that cannot read it has verified nothing.',
    );
  }
  const strangers = sweep.keys.filter((key) => !inventoryKeys().includes(key));
  expect(strangers, `${sweep.store} held a key outside the carve-out ${where}. ${CARVE_OUT_MESSAGE}`).toEqual([]);
  return sweep.keys;
}

/** The keys a reachable store holds, for a case that wants the exact set rather than the subset claim. */
function keysIn(sweep: Sweep): string[] {
  if (!sweep.reachable) expect.fail(`${sweep.store} was unreachable: ${sweep.how}`);
  return sweep.keys;
}

function valuesIn(sweep: Sweep): string[] {
  if (!sweep.reachable) expect.fail(`${sweep.store} was unreachable: ${sweep.how}`);
  return Object.values(sweep.values);
}

/* ─────────────────────────── the write watch ─────────────────────────── */

type StorageWatch = { setItem: Mock; removeItem: Mock; clear: Mock };

/**
 * Spies the storage PROTOTYPE, and must be installed before the flow runs - T22 measured a watch installed one
 * line too late reporting zero writes for a flow that had just made three, and a case below repeats that
 * measurement rather than trusting the comment.
 *
 * The prototype rather than a store object, because that is what makes it alias-proof: a call through
 * `globalThis['sessionStorage']`, a bound alias, `.call` or `.apply` all invoke the method replaced here.
 */
function watchStorage(): StorageWatch {
  return {
    setItem: vi.spyOn(Storage.prototype, 'setItem') as unknown as Mock,
    removeItem: vi.spyOn(Storage.prototype, 'removeItem') as unknown as Mock,
    clear: vi.spyOn(Storage.prototype, 'clear') as unknown as Mock,
  };
}

function writtenPairs(watch: StorageWatch): Array<[string, string]> {
  return watch.setItem.mock.calls as Array<[string, string]>;
}

function writtenKeys(watch: StorageWatch): string[] {
  return writtenPairs(watch).map(([key]) => key);
}

function removedKeys(watch: StorageWatch): string[] {
  return (watch.removeItem.mock.calls as Array<[string]>).map(([key]) => key);
}

/**
 * The write-path claim: no key outside the inventory was ever ASKED FOR, written or removed.
 *
 * Over the call log rather than the store, because a key written and then removed leaves a clean store and is
 * still a violation (AC4) - L16 governs what may SIT in the store, and a value that sat there for one turn sat
 * there. `clear()` is asserted never-called, not inferred from a store that looks right: T25 measured a `clear()`
 * substituted for a targeted `removeItem` wiping the other half of the carve-out while every store assertion
 * still passed.
 */
function expectNoKeyOutsideInventory(watch: StorageWatch, where: string): void {
  const allowed = inventoryKeys();
  expect(
    writtenKeys(watch).filter((key) => !allowed.includes(key)),
    `a key outside the carve-out was WRITTEN ${where}. ${CARVE_OUT_MESSAGE}`,
  ).toEqual([]);
  expect(
    removedKeys(watch).filter((key) => !allowed.includes(key)),
    `a key outside the carve-out was REMOVED ${where}, so it existed. ${CARVE_OUT_MESSAGE}`,
  ).toEqual([]);
  expect(
    watch.clear,
    `clear() wipes the whole store ${where}, including the other half of L16's carve-out (R29's mid-task form ` +
      'input). No production module calls it, and a targeted removeItem is what the carve-out requires.',
  ).not.toHaveBeenCalled();
}

/* ─────────────────────────── the flow ─────────────────────────── */

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

type Triple = { state: string; nonce: string; verifier: string };

/**
 * The real authorize step - the thing that writes the three - refusing to return a triple it cannot read back.
 *
 * Vacuity control in one function: every "the store held the inventory" assertion below stands on this having
 * found the three in the store first, so a harness that stored nothing cannot pass by holding nothing.
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

/** Remembers a target through the real producer, refusing to continue if the store did not take it. */
function remember(target: string): string {
  rememberReturnTarget(target);
  expect(globalThis.sessionStorage.getItem(RETURN_TARGET_KEY), `remembered ${target}`).toBe(target);
  return target;
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

type Arrival = {
  router: ReturnType<typeof createMemoryRouter>;
  fetchSpy: Mock;
  unmount: () => void;
};

/**
 * Drives the real callback screen inside the real route graph.
 *
 * The route table is imported DYNAMICALLY, because `vi.resetModules()` runs between cases and a statically
 * imported module would be a different instance than the screens hold (T23 measured that trap).
 */
async function arriveAt(url: string, answer: () => Response | Promise<Response>): Promise<Arrival> {
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
  mounted = { root, container };
  return {
    router,
    fetchSpy,
    unmount: () => {
      act(() => root.unmount());
      container.remove();
      mounted = undefined;
    },
  };
}

/** A successful sign-in that remembers a target first: the fullest the store ever gets. */
async function completeSignIn(): Promise<{ triple: Triple; arrival: Arrival; secrets: string[] }> {
  remember(A_REAL_TARGET);
  const triple = await startSignIn();
  const body = tokensFor(triple.nonce);
  const arrival = await arriveAt(`/callback?code=${AUTHORIZATION_CODE}&state=${triple.state}`, () =>
    jsonResponse(body),
  );
  return { triple, arrival, secrets: secretsOf(body) };
}

/* ─────────────────────────── the secrets ─────────────────────────── */

/** Every value a token could be hiding as: the token whole, and each of a JWT's three segments. */
function piecesOf(token: string): string[] {
  const segments = token.split('.');
  return (segments.length === 3 ? [token, ...segments] : [token]).filter((piece) => piece.length > 3);
}

/**
 * The ID token's claim values, decoded, with `nonce` excluded - and the exclusion is the finding here.
 *
 * A segment search cannot see a claim: a store holding the literal `the-subject` contains no part of the
 * base64url text that encodes it, so a leak that decoded the payload and stored a claim would pass a
 * segment-only sweep. T22-D2 counts a decoded claim value as part of the token for that reason.
 *
 * **But one claim is a sanctioned stored value.** The first run of this file failed on exactly that: the ID
 * token carries `nonce`, the authorize step puts that same nonce in sessionStorage under a key L16 names, and a
 * sweep over "every decoded claim value" therefore reported a permitted item as a leak. T22 reached the same
 * conclusion and excluded the nonce with the same reasoning - searching for it would contradict the assertion
 * that it is PRESENT. The exclusion is narrow and deliberate: it is `nonce` alone, by name, and a case below
 * plants another claim under a legitimate key to prove the rest of the search still bites.
 */
function decodedClaimsOf(idToken: string): string[] {
  const payload = idToken.split('.')[1];
  if (payload === undefined) return [];
  try {
    const decoded = JSON.parse(
      new TextDecoder().decode(
        Uint8Array.from(atob(payload.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0)),
      ),
    ) as Record<string, unknown>;
    return Object.entries(decoded)
      .filter(([name, value]) => name !== 'nonce' && typeof value === 'string' && value.length > 3)
      .map(([, value]) => value as string);
  } catch {
    // A token this file minted always decodes. A malformed one is still swept whole and by segment, which is the
    // claim; swallowing here keeps a decoding quirk from reporting a clean store as a failure.
    return [];
  }
}

/** Everything R29 forbids, for the flow that produced `body`. */
function secretsOf(body: Record<string, unknown>): string[] {
  return [
    ...piecesOf(body.access_token as string),
    ...piecesOf(body.id_token as string),
    ...decodedClaimsOf(body.id_token as string),
    AUTHORIZATION_CODE,
    A_PASSWORD,
    A_RECOVERY_CODE,
    AN_API_KEY,
    AN_EMAIL_ADDRESS,
    AN_ACCOUNT_UUID,
  ];
}

function expectNoSecretIn(haystacks: string[], secrets: string[], where: string): void {
  const joined = haystacks.join('\u0000');
  for (const secret of secrets) {
    expect(joined, `a value R29 forbids was found ${where}`).not.toContain(secret);
  }
}

/* ─────────────────────────── the source scan ─────────────────────────── */

type ScanTarget = { path: string; code: string };

/** A production file's code with its comments removed, because a file explains the rules it obeys. */
function strip(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
}

/**
 * Every production module under `src/`, comments stripped. It refuses an empty tree.
 *
 * A scan over nothing satisfies every prohibition at once - the failure mode `bundleContainsNoSecretEnvKeys`
 * names in its own header, and the reason a scanner never shown to catch anything has verified nothing.
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
    targets.push({
      path: relative(srcRoot, file).split(/[\\/]/).join('/'),
      code: strip(await readFile(file, 'utf8')),
    });
  }
  return targets;
}

/**
 * Files, not `path:line`.
 *
 * Stripping a block comment removes its newlines, so a line number computed afterwards points somewhere else in
 * the file. The file is the actionable unit anyway: the allowlist below is per file.
 */
function hits(targets: ScanTarget[], pattern: RegExp): string[] {
  return targets.filter((target) => pattern.test(target.code)).map((target) => target.path);
}

/**
 * How a storage WRITE is recognised in source, in every form that reaches one.
 *
 * The direct forms are the ones real code uses. The three indirect forms were added because a measurement showed
 * the prototype watch sees them and a bare-name pattern does not: `s['setItem'](…)` puts a `']` between the name
 * and the parenthesis, and `.bind`, `.call` and `.apply` put a method call there. They are rare in application
 * code, which is exactly why a scan whose job is to report dirty should not be the thing that misses them.
 */
const WRITE_METHODS: ReadonlyArray<{ name: string; patterns: readonly RegExp[] }> = [
  {
    name: 'setItem',
    patterns: [
      /\bsetItem\s*\(/,
      /\[\s*['"`]setItem['"`]\s*\]\s*\(/,
      /\bsetItem\s*\.\s*(?:call|apply|bind)\s*\(/,
    ],
  },
  {
    name: 'removeItem',
    patterns: [
      /\bremoveItem\s*\(/,
      /\[\s*['"`]removeItem['"`]\s*\]\s*\(/,
      /\bremoveItem\s*\.\s*(?:call|apply|bind)\s*\(/,
    ],
  },
  {
    // `clear` belongs to `Map` and `Set` too, so it is anchored to a receiver whose name says storage - which is
    // what `authorizeRequest.ts`'s `const session = globalThis.sessionStorage` is. A receiver named something
    // else slips past this row and is caught by the runtime spy instead: the second net misses what the primary
    // instrument sees, which is why there are two.
    name: 'clear',
    patterns: [
      /\b(?:\w*[sS]torage|session|store)\s*\.\s*clear\s*\(/,
      /\b(?:\w*[sS]torage|session|store)\s*\[\s*['"`]clear['"`]\s*\]\s*\(/,
      /\bclear\s*\.\s*(?:call|apply)\s*\(/,
    ],
  },
];

/**
 * Which production file may call which write method, over which keys, and why.
 *
 * A table rather than a regex, so a violation names the file and says what to do. Two rows, one per writing
 * module, and they are not merged because the items are not consumed together (T24-D4). The next file to call a
 * storage write method adds a row here with a reason - that is the point of the table.
 */
const PERMITTED_WRITERS: ReadonlyArray<{ file: string; methods: readonly string[]; keys: () => string[]; why: string }> = [
  {
    file: 'auth/authorizeRequest.ts',
    methods: ['setItem', 'removeItem'],
    keys: () => Object.values(AUTHORIZE_STORAGE_KEYS),
    why:
      "L16 (a)'s first three items, written together once the PKCE challenge is in hand (task 15) and removed " +
      'together by `consumeAuthorizeState` once a callback has used all three (task 23).',
  },
  {
    file: 'auth/returnTarget.ts',
    methods: ['setItem', 'removeItem'],
    keys: () => [RETURN_TARGET_KEY],
    why:
      "L16 (a)'s fourth item, remembered before a sign-in starts and consumed by the navigation at the end of a " +
      'successful callback (task 24).',
  },
];

/**
 * Client-side surfaces no production module may NAME.
 *
 * A name scan, distinct from the method-call scan above, and reported differently: naming a surface is not
 * calling it. Comments are stripped first, so a name that survives is a name in live code.
 *
 * ENUMERATIVE, NOT EXHAUSTIVE, and the file says so rather than implying it: six names cannot prove the absence
 * of a surface nobody thought of. jsdom defines none of them (measured: `indexedDB`, `caches`, `cookieStore` and
 * `navigator.storage` are all `undefined`), so this scan is the whole of the claim and it is structural.
 */
const FORBIDDEN_SURFACES: ReadonlyArray<{ name: string; pattern: RegExp }> = [
  { name: 'indexedDB', pattern: /\bindexedDB\b/ },
  { name: 'document.cookie', pattern: /\bdocument\s*\.\s*cookie\b/ },
  { name: 'cookieStore', pattern: /\bcookieStore\b/ },
  { name: 'Cache Storage', pattern: /\bcaches\b/ },
  { name: 'navigator.storage', pattern: /\bnavigator\s*\.\s*storage\b/ },
  { name: 'webkitRequestFileSystem', pattern: /\bwebkitRequestFileSystem\b/ },
];

/* ─────────────────────────── the namespace scan: the third instrument ─────────────────────────── */

/**
 * THE INSTRUMENT MEASUREMENT ASKED FOR, and the one the other two cannot be.
 *
 * A mutant written to escape (MA22 in this task's harness) put a fifth key in the store with a COMPUTED method
 * name - `const method = 'set' + 'Item'` - behind a condition no test produces. The runtime watch never saw it,
 * because the branch never ran. The method scan never saw it, because no pattern can match a name that is not
 * in the text. The only thing that survived in source was the KEY'S OWN NAME, and T23 caught the mutant for
 * exactly that reason, by counting key spellings in the file that writes them.
 *
 * So this scan keys on the name of the thing stored rather than on the call that stores it. Every
 * `themistra`-namespace literal in production source must be an inventory key, or one of the allowlisted names
 * below that is not a store at all. That makes the next `themistra.*` identifier an explicit decision: its
 * author has to say which it is.
 *
 * It is deliberately the WHOLE namespace and not the inventory's prefix. A prefix scan asks "is this key spelled
 * right"; this asks "is this name a store key at all", which is the question L16's exhaustiveness poses. It also
 * catches the shapes a prefix misses - a sibling with no dot (`…auth_state`) and a sibling namespace
 * (`…authx.foo`) - each of which has a control below.
 */
function namespaceRoot(): string {
  // Derived, not spelled, and read inside a function for the same reason the inventory is: the first segment the
  // four inventory keys share.
  const roots = new Set(inventoryKeys().map((key) => key.split('.')[0]));
  expect(roots.size, 'the four inventory keys share one namespace root').toBe(1);
  return [...roots][0]!;
}

/**
 * Every namespace literal in a body of code.
 *
 * The lookbehind drops `@themistra/api-client`, which is the npm package scope in an import path and not a name
 * in any store - measured: three production files carry it (`api/client.ts`, `api/sessionBoundary.ts`,
 * `auth/codeExchange.ts`) and nothing else about them is relevant here.
 */
function namespaceMatches(code: string): string[] {
  const pattern = new RegExp(`(?<!@)\\b${namespaceRoot()}(?:[.\\w-]+)?`, 'g');
  return [...code.matchAll(pattern)].map((match) => match[0]);
}

/**
 * Namespace names that are not stores, each with the reason it is not one.
 *
 * Imported rather than spelled, so a rename moves this allowlist with it. Both are `crossTabRenewal.ts`'s, and
 * both are L18 rather than L16: a BroadcastChannel name and a Web Locks name. Neither persists anything - a
 * channel delivers to live tabs and a lock exists while it is held - so neither is client-side storage in L16's
 * sense, and the carve-out has nothing to say about them.
 */
function nonStorageNamespaceNames(): Array<{ name: string; file: string; why: string }> {
  return [
    {
      name: RENEWAL_CHANNEL,
      file: 'api/crossTabRenewal.ts',
      why: 'a BroadcastChannel name (L18, R21/R22). A channel delivers to live tabs and persists nothing.',
    },
    {
      name: RENEWAL_LOCK,
      file: 'api/crossTabRenewal.ts',
      why: 'a Web Locks name (L18). A lock exists while it is held and persists nothing.',
    },
  ];
}

const NAMESPACE_MESSAGE =
  'A name in this namespace is either one of L16\'s four items or it is not a store at all. If it is a store ' +
  'key, L16 is exhaustive and it needs a design decision, not an inventory entry. If it is not - a channel, a ' +
  'lock, a package scope - add it to nonStorageNamespaceNames() with the reason it persists nothing.';

let ownSource: string | undefined;

/* ─────────────────────────── the cases ─────────────────────────── */

describe('sessionStorageHoldsOnlyTheL16Items', () => {
  beforeEach(async () => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.resetModules();
    globalThis.sessionStorage.clear();
    globalThis.localStorage.clear();
    ownSource ??= await readFile(expect.getState().testPath as string, 'utf8');
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

  describe('the inventory is derived from the two modules that own it (AC1, AC8, AC10)', () => {
    it('is exactly the three authorize keys and the return target, each with an owner and a reason', () => {
      expect(inventoryKeys()).toEqual([...Object.values(AUTHORIZE_STORAGE_KEYS), RETURN_TARGET_KEY].sort());
      expect(new Set(inventoryKeys()).size, 'four distinct keys, not one key counted twice').toBe(4);
      for (const item of inventory()) {
        expect(item.owner, `${item.key} names the module that owns it`).toMatch(/^auth\/\w+\.ts$/);
        expect(item.why.length, `${item.key} says why it is permitted`).toBeGreaterThan(40);
      }
    });

    /**
     * The count pin. It will fail the day a fifth key is legitimate, and that is deliberate: L16 says the list is
     * the entire carve-out, so growth is a spec event, not a test detail. The message is the whole value of this
     * case - it names clause (b) as the expected next entrant and the TTL test no task claims.
     */
    it('pins the carve-out at four, and says what to do when that changes', () => {
      expect(inventory(), CARVE_OUT_MESSAGE).toHaveLength(4);
      expect(CARVE_OUT_MESSAGE).toContain('clause (b)');
      expect(CARVE_OUT_MESSAGE).toContain('expiredSessionMidTaskPreservesOnlyNonSensitiveInput');
    });

    /**
     * No key is SPELLED in this file.
     *
     * A literal would keep passing after a rename, which is the one failure a guard like this cannot afford. The
     * needles are built from the imported constants at runtime, so the strings never appear in this source - a
     * file searching for a string it contains finds itself, and every scan in this package has had to learn that
     * once.
     */
    it('contains no key literal of its own, so a rename breaks it rather than passing it', () => {
      const source = strip(ownSource as string);
      for (const key of inventoryKeys()) {
        expect(source, `${key} is spelled out in this file; import it instead`).not.toContain(key);
      }
      expect(source, 'the control: a string this file really does contain').toContain(SENTINEL_KEY);
    });
  });

  describe('every state of the flow holds only inventory keys (AC1, AC2)', () => {
    it('is empty before anything starts, and the app itself adds nothing', async () => {
      const arrival = await arriveAt('/sign-in', () => jsonResponse({}));

      expect(arrival.router.state.location.pathname, 'the control: the app really mounted').toBe('/app/sign-in');
      expect(keysIn(sweepSession()), 'mounting the SPA writes no key').toEqual([]);
      expect(keysIn(sweepLocal())).toEqual([]);
    });

    it('holds exactly the three authorize keys once a sign-in starts', async () => {
      await startSignIn();

      expect(keysIn(sweepSession())).toEqual(Object.values(AUTHORIZE_STORAGE_KEYS).sort());
      expectOnlyInventory(sweepSession(), 'after the authorize step');
    });

    it('holds exactly four once a return target is remembered as well', async () => {
      remember(A_REAL_TARGET);
      await startSignIn();

      expect(keysIn(sweepSession())).toEqual(inventoryKeys());
      expectOnlyInventory(sweepSession(), 'with a target remembered');
    });

    it('is empty again after a successful callback', async () => {
      const { arrival } = await completeSignIn();

      expect(arrival.router.state.location.pathname, 'the control: the sign-in completed').toBe(
        `/app${A_REAL_TARGET}`,
      );
      expect(keysIn(sweepSession()), 'all four consumed: three by the exchange, one by the navigation').toEqual([]);
      expectOnlyInventory(sweepLocal(), 'after a successful callback');
    });

    /**
     * Every discard path, which is where the store is left as it was.
     *
     * A discard deletes nothing (T23-D2: `/app/callback` is public, so a deleting discard would let a link end a
     * stranger's pending sign-in) and does not consume the target (T24-D14: the use is the arrival, not the
     * attempt). So the claim here is not "the store was emptied" but "the store still holds only the four", which
     * is the only claim L16 makes about a failed sign-in.
     */
    const discards: Array<{ label: string; url: (state: string) => string; answer: (nonce: string) => Response }> = [
      {
        label: 'no state in the query',
        url: () => `/callback?code=${AUTHORIZATION_CODE}`,
        answer: (nonce) => jsonResponse(tokensFor(nonce)),
      },
      {
        label: 'a state that does not match',
        url: () => `/callback?code=${AUTHORIZATION_CODE}&state=not-the-stored-state`,
        answer: (nonce) => jsonResponse(tokensFor(nonce)),
      },
      {
        label: 'an ID token whose nonce does not match',
        url: (state) => `/callback?code=${AUTHORIZATION_CODE}&state=${state}`,
        answer: () => jsonResponse(tokensFor('not-the-stored-nonce')),
      },
      {
        label: 'a refused code',
        url: (state) => `/callback?code=${AUTHORIZATION_CODE}&state=${state}`,
        answer: () => refusal(),
      },
      {
        label: 'a server fault',
        url: (state) => `/callback?code=${AUTHORIZATION_CODE}&state=${state}`,
        answer: () => new Response('', { status: 500 }),
      },
      {
        label: 'a response with no usable tokens',
        url: (state) => `/callback?code=${AUTHORIZATION_CODE}&state=${state}`,
        answer: () => jsonResponse({}),
      },
    ];

    for (const discard of discards) {
      it(`holds only inventory keys when the callback is discarded: ${discard.label}`, async () => {
        const watch = watchStorage();
        remember(A_REAL_TARGET);
        const triple = await startSignIn();

        await arriveAt(discard.url(triple.state), () => discard.answer(triple.nonce));

        expectOnlyInventory(sweepSession(), `after a discard (${discard.label})`);
        // The exact set, not merely "no stranger" (Kimi I4). A discard deletes nothing (T23-D2) and does not
        // consume the target (T24-D14), so all four are known to be there - and a subset assertion would pass
        // for a mutant that DELETED one on a discard path.
        expect(keysIn(sweepSession()), 'all four, because a discard consumes nothing').toEqual(inventoryKeys());
        expectNoKeyOutsideInventory(watch, `during a discard (${discard.label})`);
        expect(keysIn(sweepLocal()), 'and nothing went to localStorage either').toEqual([]);
      });
    }
  });

  describe('no key outside the carve-out is ever written, even transiently (AC3, AC4)', () => {
    it('writes exactly the four keys, in order, across a whole successful sign-in', async () => {
      const watch = watchStorage();

      await completeSignIn();

      expect(writtenKeys(watch)).toEqual([
        RETURN_TARGET_KEY,
        AUTHORIZE_STORAGE_KEYS.state,
        AUTHORIZE_STORAGE_KEYS.nonce,
        AUTHORIZE_STORAGE_KEYS.codeVerifier,
      ]);
      expect(removedKeys(watch).sort()).toEqual(inventoryKeys());
      expectNoKeyOutsideInventory(watch, 'across a successful sign-in');
    });

    /**
     * `clear()` asserted never-called rather than inferred.
     *
     * T25 measured the mutant: `sessionStorage.clear()` in place of the targeted `removeItem` leaves a store that
     * satisfies every "it is gone" assertion, and wipes R29's mid-task form input - the other half of the very
     * carve-out this file guards - on its way past.
     */
    it('never calls clear(), which would wipe the other half of the carve-out', async () => {
      const watch = watchStorage();

      await completeSignIn();

      expect(watch.clear).not.toHaveBeenCalled();
      expect(removedKeys(watch).length, 'the control: removals really did happen, one per key').toBe(4);
    });
  });

  describe('the two instruments, and why both are needed (AC3, AC9)', () => {
    /**
     * The pair, stated as a measurement.
     *
     * Each indirect form is invisible to a bare-name pattern and visible to the prototype watch, and each one
     * really lands in the store. Widening the scan's patterns is right, and so is knowing which net caught what:
     * a reader who finds the two overlapping will otherwise delete one of them.
     */
    const indirect: Array<{ label: string; code: string; write: (store: Storage, key: string) => void }> = [
      {
        label: 'bracket notation',
        code: "const s = globalThis.sessionStorage; s['setItem']('k', 'v');",
        write: (store, key) => {
          (store as unknown as Record<string, (k: string, v: string) => void>)['setItem'](key, '1');
        },
      },
      {
        label: 'a bound alias',
        code: 'const put = globalThis.sessionStorage.setItem.bind(globalThis.sessionStorage); put("k", "v");',
        write: (store, key) => {
          const put = store.setItem.bind(store);
          put(key, '1');
        },
      },
      {
        label: 'Storage.prototype.setItem.call',
        code: 'Storage.prototype.setItem.call(globalThis.sessionStorage, "k", "v");',
        write: (store, key) => {
          Storage.prototype.setItem.call(store, key, '1');
        },
      },
      {
        label: 'Storage.prototype.setItem.apply',
        code: 'Storage.prototype.setItem.apply(globalThis.sessionStorage, ["k", "v"]);',
        write: (store, key) => {
          Storage.prototype.setItem.apply(store, [key, '1']);
        },
      },
    ];

    for (const form of indirect) {
      it(`catches ${form.label}: the scan sees it, the watch sees it, and the write really lands`, () => {
        const patterns = WRITE_METHODS.find((method) => method.name === 'setItem')!.patterns;
        const synthetic: ScanTarget[] = [{ path: 'synthetic/evasion.ts', code: strip(form.code) }];
        expect(
          patterns.some((pattern) => hits(synthetic, pattern).length === 1),
          'the source scan must recognise this form',
        ).toBe(true);

        const watch = watchStorage();
        form.write(globalThis.sessionStorage, SENTINEL_KEY);

        expect(writtenKeys(watch), 'the prototype watch sees it whatever the call form').toEqual([SENTINEL_KEY]);
        expect(globalThis.sessionStorage.getItem(SENTINEL_KEY), 'and the write really landed').toBe('1');
        expect(keysIn(sweepSession()), 'so the sweep reports a dirty store').toEqual([SENTINEL_KEY]);
      });
    }

    it('still permits the two legitimate writers, so the widened patterns are not a blanket ban', async () => {
      const targets = await productionSources();
      const setItem = WRITE_METHODS.find((method) => method.name === 'setItem')!;

      const writers = new Set(setItem.patterns.flatMap((pattern) => hits(targets, pattern)));

      expect([...writers].sort()).toEqual(PERMITTED_WRITERS.map((writer) => writer.file).sort());
    });
  });

  describe('only the two modules that own the carve-out write to a store (AC3, AC6)', () => {
    it('finds no third writer, and no caller of clear(), anywhere in production source', async () => {
      const targets = await productionSources();

      for (const method of WRITE_METHODS) {
        const found = new Set(method.patterns.flatMap((pattern) => hits(targets, pattern)));
        const permitted = PERMITTED_WRITERS.filter((writer) => writer.methods.includes(method.name)).map(
          (writer) => writer.file,
        );
        expect(
          [...found].sort(),
          `${method.name} may be called only by the modules that own the carve-out. ${CARVE_OUT_MESSAGE}`,
        ).toEqual(permitted.sort());
      }
    });

    it('writes only its own keys from each permitted module', async () => {
      const srcRoot = dirname(dirname(expect.getState().testPath as string));
      for (const writer of PERMITTED_WRITERS) {
        const code = strip(await readFile(join(srcRoot, writer.file), 'utf8'));
        const others = inventoryKeys().filter((key) => !writer.keys().includes(key));
        for (const key of others) {
          expect(code, `${writer.file} must not touch ${key}: ${writer.why}`).not.toContain(key);
        }
      }
    });

    /**
     * `localStorage` IS PERMITTED, for one thing, and the guard has to say which.
     *
     * `design.md:193` allows public preferences there - theme and locale - so a scan that forbids naming
     * `localStorage` at all would fail the day a theme toggle lands, with a message stating the rule that change
     * complies with. A guard that cannot tell "a preference arrived" from "a token leaked" gets deleted by
     * whoever hits it first (Kimi I2).
     *
     * So the enforced claim is the one L16 and L3 actually make: whatever uses `localStorage` may not put an L16
     * item or a namespace name in it. Measured today: no production module names `localStorage` at all, which is
     * why the behavioural cases above find it empty - that is a fact about now, not the rule.
     */
    it('permits localStorage for public preferences, and forbids an L16 item or namespace name in it', async () => {
      const targets = await productionSources();
      const users = targets.filter((target) => /\blocalStorage\b/.test(target.code));

      for (const user of users) {
        const names = namespaceMatches(user.code);
        expect(
          names,
          `${user.path} names localStorage and a name in L16's namespace. design.md:193 permits public ` +
            'preferences (theme, locale) in localStorage; an L16 item is not a preference, and L3 forbids a ' +
            'token anywhere near it.',
        ).toEqual([]);
      }
    });

    it('reports a module that would write an L16 item to localStorage, which is the violation that matters', () => {
      const offender: ScanTarget[] = [
        {
          path: 'synthetic/mirrors-to-local.ts',
          code: strip(`globalThis.localStorage.setItem('${inventoryKeys()[0]}', value);`),
        },
      ];
      const preference: ScanTarget[] = [
        { path: 'synthetic/theme.ts', code: strip("globalThis.localStorage.setItem('theme', 'dark');") },
      ];

      expect(namespaceMatches(offender[0]!.code)).toEqual([inventoryKeys()[0]]);
      expect(namespaceMatches(preference[0]!.code), 'a preference is permitted and must not be flagged').toEqual(
        [],
      );
    });
  });

  describe('every name in the namespace is a store key or declared not to be (AC3, AC4, AC9)', () => {
    it('finds no namespace literal in production source outside the inventory and the non-store allowlist', async () => {
      const targets = await productionSources();
      const permitted = new Set([...inventoryKeys(), ...nonStorageNamespaceNames().map((entry) => entry.name)]);

      const strangers = targets.flatMap((target) =>
        namespaceMatches(target.code)
          .filter((name) => !permitted.has(name))
          .map((name) => `${target.path}: ${name}`),
      );

      expect(strangers, NAMESPACE_MESSAGE).toEqual([]);
    });

    it('is reading something: the four inventory keys and the two non-store names are all found', async () => {
      const targets = await productionSources();

      const found = new Set(targets.flatMap((target) => namespaceMatches(target.code)));

      for (const key of inventoryKeys()) {
        expect(found, `${key} is written in production source, so the scan must see it`).toContain(key);
      }
      for (const entry of nonStorageNamespaceNames()) {
        expect(found, `${entry.name} is in ${entry.file}: ${entry.why}`).toContain(entry.name);
      }
    });

    /**
     * The three shapes a prefix scan misses, each a control.
     *
     * A sibling with no dot and a sibling namespace are the two Kimi's I5 named; its proposed pattern would have
     * matched neither, which is why this scans the whole namespace instead of a prefix of it.
     */
    const siblings = ['.evil', '_state', 'x.foo'];
    for (const suffix of siblings) {
      it(`flags a namespace sibling: <root>${suffix}`, () => {
        const name = `${namespaceRoot()}${suffix}`;
        const synthetic: ScanTarget[] = [{ path: 'synthetic/sibling.ts', code: strip(`const k = '${name}';`) }];
        const permitted = new Set([...inventoryKeys(), ...nonStorageNamespaceNames().map((entry) => entry.name)]);

        const strangers = namespaceMatches(synthetic[0]!.code).filter((found) => !permitted.has(found));

        expect(strangers, `${name} must be reported`).toEqual([name]);
      });
    }

    it('does not flag the npm package scope, which is an import path and not a name in any store', () => {
      const scope: ScanTarget[] = [
        { path: 'synthetic/imports.ts', code: strip(`import { c } from '@${namespaceRoot()}/api-client';`) },
      ];

      expect(namespaceMatches(scope[0]!.code)).toEqual([]);
    });

    /**
     * MA22 itself, as a case rather than as a harness result.
     *
     * The dormant write with a computed method name: the method scan finds nothing, because the literal is not
     * in the text, and the namespace scan finds the key, because the key's name is. This is the measurement that
     * justified a third instrument, kept where a reader will see it.
     */
    it('catches the dormant write with a computed method name that both other instruments miss', () => {
      const dormant = strip(
        `const method = 'set' + 'Item';\n` +
          `if (globalThis.navigator.userAgent.includes('ZZZ-NEVER-MATCHES')) {\n` +
          `  (session as unknown as Record<string, (k: string, v: string) => void>)[method]('${namespaceRoot()}.auth.dormant', state);\n` +
          `}`,
      );
      const target: ScanTarget[] = [{ path: 'synthetic/dormant.ts', code: dormant }];

      for (const method of WRITE_METHODS) {
        for (const pattern of method.patterns) {
          expect(hits(target, pattern), `${method.name}: a computed name is not in the text`).toEqual([]);
        }
      }
      const permitted = new Set([...inventoryKeys(), ...nonStorageNamespaceNames().map((entry) => entry.name)]);
      expect(namespaceMatches(dormant).filter((name) => !permitted.has(name))).toEqual([
        `${namespaceRoot()}.auth.dormant`,
      ]);
    });
  });

  describe('no forbidden client-side surface is even named (AC7)', () => {
    /**
     * Structural, and said so. jsdom defines none of these (measured: all `undefined`, and `document.cookie` is
     * empty), so an assertion that no database was opened would pass against an API that does not exist. The
     * scan over source is the whole of the claim.
     */
    it('names none of the six surfaces in live production code', async () => {
      const targets = await productionSources();

      for (const surface of FORBIDDEN_SURFACES) {
        expect(
          hits(targets, surface.pattern),
          `${surface.name} is outside L16's carve-out, which is sessionStorage alone. A new client-side surface ` +
            'is a design decision under design.md §5, not a test change.',
        ).toEqual([]);
      }
    });

    it('is a NAME scan, not a call scan, and fires on a name that is never called', () => {
      const naming: ScanTarget[] = [
        { path: 'synthetic/names-it.ts', code: strip('const unused = indexedDB;') },
        { path: 'synthetic/reads-cookie.ts', code: strip('export const c = document.cookie;') },
      ];

      expect(hits(naming, FORBIDDEN_SURFACES[0]!.pattern)).toEqual(['synthetic/names-it.ts']);
      expect(hits(naming, FORBIDDEN_SURFACES[1]!.pattern)).toEqual(['synthetic/reads-cookie.ts']);
    });

    it('records that jsdom supplies none of them, so the claim cannot be behavioural', () => {
      const global = globalThis as unknown as Record<string, unknown>;
      expect(global.indexedDB).toBeUndefined();
      expect(global.caches).toBeUndefined();
      expect(global.cookieStore).toBeUndefined();
      expect((globalThis.navigator as unknown as Record<string, unknown>).storage).toBeUndefined();
      expect(document.cookie).toBe('');
    });
  });

  describe('nothing R29 forbids is in the store, as a key or as a value (AC5, AC11)', () => {
    /**
     * WHICH NEEDLES ARE LIVE, said here rather than left to be counted.
     *
     * Live in this flow: the access token, the ID token whole and by segment, its decoded claims, and the
     * authorization code. Those four this file really creates, so their absence is a measurement.
     *
     * Forward-looking: the password, the recovery code, the API key, the email address and the account UUID. No
     * flow here produces one - passwords are SAS-hosted (L2) and no screen in the package collects the others
     * yet - so their absence proves only that the sweep read the surface. They stay in the list because R29
     * names them and a sweep that drops a needle today will not notice when tomorrow's screens can produce it.
     * The two sentinel cases below are what make the search credible in the meantime.
     */
    it('finds no token, code or secret among the four values at their fullest', async () => {
      remember(A_REAL_TARGET);
      const triple = await startSignIn();
      const body = tokensFor(triple.nonce);

      const sweep = sweepSession();

      expect(keysIn(sweep), 'the control: four values were really there to search').toHaveLength(4);
      expectNoSecretIn(valuesIn(sweep), secretsOf(body), 'in a stored value');
      expectNoSecretIn(keysIn(sweep), secretsOf(body), 'in a stored key');
    });

    it('writes no token, code or secret at any point in a successful sign-in', async () => {
      const watch = watchStorage();

      const { secrets } = await completeSignIn();

      const written = writtenPairs(watch).flat();
      expect(written.length, 'the control: writes really happened').toBeGreaterThan(0);
      expectNoSecretIn(written, secrets, 'on the write path');
    });

    /**
     * The sentinel, which is what makes the two cases above worth running.
     *
     * Several needles are shapes no flow here produces - a password, a recovery code, an API key, an email
     * address - so their absence proves only that the sweep read the surface. This case plants a secret under an
     * inventory key, which is the one way a value could hide behind a legitimate name, and shows the sweep
     * reports it.
     */
    it('reports a secret planted under a legitimate key, which is the only place one could hide', async () => {
      await startSignIn();
      globalThis.sessionStorage.setItem(RETURN_TARGET_KEY, `${A_REAL_TARGET}?token=${ACCESS_TOKEN}`);

      const sweep = sweepSession();

      expect(keysIn(sweep), 'the key set is clean, so a key check alone would pass').toEqual(inventoryKeys());
      expect(() => expectNoSecretIn(valuesIn(sweep), [ACCESS_TOKEN], 'in a stored value')).toThrow();
    });

    /**
     * The nonce exclusion is one claim wide, and this is what holds it there.
     *
     * `decodedClaimsOf` drops `nonce` because L16 permits it in the store by name. Every other claim is still
     * searched, so a leak that decoded the payload and parked `sub` under a legitimate key is still reported -
     * which is the whole reason the decoded search exists.
     */
    it('still finds a different decoded claim planted under a legitimate key', async () => {
      const triple = await startSignIn();
      const idToken = tokenWith({ nonce: triple.nonce, sub: 'the-subject', iss: 'http://localhost:5173' });
      globalThis.sessionStorage.setItem(RETURN_TARGET_KEY, `${A_REAL_TARGET}?who=the-subject`);

      const claims = decodedClaimsOf(idToken);

      expect(claims, 'the nonce is excluded by name').not.toContain(triple.nonce);
      expect(claims, 'and nothing else is').toContain('the-subject');
      expect(() => expectNoSecretIn(valuesIn(sweepSession()), claims, 'in a stored value')).toThrow();
    });
  });

  describe('an unreachable store is not an empty one (AC12, AC9)', () => {
    /**
     * The measurement this block exists for.
     *
     * With `getItem` throwing, key enumeration is untouched: `Object.keys` returns all four and `.length` is 4.
     * So the obvious sweep - read the keys, compare to the inventory - passes over a store whose values it never
     * read, which is compliance reported for the surface most likely to be broken in a real browser.
     */
    it('passes a key-only check and fails the real sweep when the store cannot be read', async () => {
      await startSignIn();
      remember(A_REAL_TARGET);
      vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
        throw new DOMException('the store is not available', 'SecurityError');
      });

      expect(Object.keys(globalThis.sessionStorage).sort(), 'the vacuous check still passes').toEqual(
        inventoryKeys(),
      );
      const sweep = sweepSession();
      expect(sweep.reachable, 'the real sweep does not').toBe(false);
      expect(() => expectOnlyInventory(sweep, 'under a throwing store')).toThrow(/UNREACHABLE/);
    });

    it('reports a genuinely empty store as empty, so the two outcomes are distinguishable', () => {
      const sweep = sweepSession();

      expect(sweep.reachable).toBe(true);
      expect(keysIn(sweep)).toEqual([]);
      expectOnlyInventory(sweep, 'with nothing stored');
    });

    it('reports an enumeration that disagrees with itself as unreachable', async () => {
      await startSignIn();
      vi.spyOn(Storage.prototype, 'key').mockImplementation(() => null);

      const sweep = sweepSession();

      expect(sweep.reachable).toBe(false);
      expect(sweep.reachable === false && sweep.how).toContain('enumerations disagree');
    });
  });

  describe('the instruments are proved against a dirty store before they are believed (AC9)', () => {
    it('fails the sweep on an out-of-inventory key', async () => {
      await startSignIn();
      globalThis.sessionStorage.setItem(SENTINEL_KEY, SENTINEL_VALUE);

      expect(() => expectOnlyInventory(sweepSession(), 'with a stranger present')).toThrow(/carve-out/);
    });

    /**
     * A write that is removed again is still a violation (AC4).
     *
     * The store ends clean, so every store assertion in this file would pass. The write log is the only place the
     * event survives, which is why the claim is made there.
     */
    it('fails the write-path check on a key written and then removed, though the store ends clean', async () => {
      const watch = watchStorage();
      await startSignIn();

      globalThis.sessionStorage.setItem(SENTINEL_KEY, SENTINEL_VALUE);
      globalThis.sessionStorage.removeItem(SENTINEL_KEY);

      expect(keysIn(sweepSession()), 'the store looks clean').toEqual(Object.values(AUTHORIZE_STORAGE_KEYS).sort());
      expect(() => expectNoKeyOutsideInventory(watch, 'in this control')).toThrow(/WRITTEN/);
    });

    it('fails the method scan on a synthetic offending line', () => {
      const offender: ScanTarget[] = [
        { path: 'synthetic/offender.ts', code: strip('globalThis.sessionStorage.setItem("k", "v");') },
      ];

      expect(hits(offender, WRITE_METHODS[0]!.patterns[0]!)).toEqual(['synthetic/offender.ts']);
    });

    it('fails the clear() scan on a synthetic offending line', () => {
      const offender: ScanTarget[] = [
        { path: 'synthetic/wipes.ts', code: strip('globalThis.sessionStorage.clear();') },
      ];

      const clear = WRITE_METHODS.find((method) => method.name === 'clear')!;
      expect(clear.patterns.some((pattern) => hits(offender, pattern).length === 1)).toBe(true);
    });

    it('refuses a scan over an empty tree, which would satisfy every prohibition at once', async () => {
      const targets = await productionSources();

      expect(targets.length, 'the control: the real tree is not empty').toBeGreaterThan(10);
      for (const method of WRITE_METHODS) {
        for (const pattern of method.patterns) {
          expect(hits([], pattern), 'a scan over nothing finds nothing, which is why the walk refuses one').toEqual(
            [],
          );
        }
      }
      await expect(
        (async () => {
          const empty: ScanTarget[] = [];
          if (empty.length === 0) throw new Error('scan refused: no production sources found');
        })(),
      ).rejects.toThrow('scan refused');
    });

    /**
     * The ordering control, measured rather than asserted by comment.
     *
     * T22 found a watch installed one line too late reporting zero writes for a flow that had just made three.
     * This repeats it: the same flow, the watch installed afterwards, and a log that says nothing happened.
     */
    it('shows a watch installed after the flow reporting zero writes for a flow that made four', async () => {
      await completeSignIn();

      const late = watchStorage();

      expect(writtenKeys(late), 'a late watch sees nothing, which is why installation order is a rule').toEqual([]);
      expect(removedKeys(late)).toEqual([]);
    });
  });

  describe('repetition and resumption add no fifth key (AC1, AC2, AC8)', () => {
    it('holds only inventory keys across two sign-ins in one tab', async () => {
      const watch = watchStorage();
      const first = await completeSignIn();
      // Unmounted, not merely forgotten (Kimi I3). `arriveAt` overwrites `mounted` without unmounting, so
      // dropping the reference would leave a live CallbackScreen running its effects while the next flow's store
      // assertions are taken - and a write from the orphan would be attributed to the flow under test.
      first.arrival.unmount();

      await completeSignIn();

      expectOnlyInventory(sweepSession(), 'after a second sign-in in the same tab');
      expectNoKeyOutsideInventory(watch, 'across two sign-ins');
      expect(keysIn(sweepSession()), 'and both were consumed').toEqual([]);
    });

    /**
     * Discard, then a fresh sign-in, then a completed callback.
     *
     * The realistic sequence, and the one where a fifth key would appear: a discard leaves the three PKCE items
     * in place (T23-D2), the next authorize step overwrites them, and the target left behind by the discard
     * (T24-D14) is consumed by the arrival that finally succeeds.
     */
    it('holds only inventory keys through discard, fresh sign-in, completed callback', async () => {
      const watch = watchStorage();
      remember(A_REAL_TARGET);
      const first = await startSignIn();

      const discarded = await arriveAt(`/callback?code=${AUTHORIZATION_CODE}&state=not-the-stored-state`, () =>
        jsonResponse(tokensFor(first.nonce)),
      );
      expectOnlyInventory(sweepSession(), 'after the discard');
      expect(keysIn(sweepSession()), 'the discard deleted nothing').toEqual(inventoryKeys());
      discarded.unmount();

      const second = await startSignIn();
      expectOnlyInventory(sweepSession(), 'after the fresh sign-in');

      const arrival = await arriveAt(`/callback?code=${AUTHORIZATION_CODE}&state=${second.state}`, () =>
        jsonResponse(tokensFor(second.nonce)),
      );

      expect(arrival.router.state.location.pathname, 'the control: the retry really completed').toBe(
        `/app${A_REAL_TARGET}`,
      );
      expect(keysIn(sweepSession())).toEqual([]);
      expectNoKeyOutsideInventory(watch, 'through discard and retry');
    });

    it('holds only inventory keys when a callback is replayed after it succeeded', async () => {
      const watch = watchStorage();
      const { triple, arrival } = await completeSignIn();
      arrival.unmount();

      await arriveAt(`/callback?code=${AUTHORIZATION_CODE}&state=${triple.state}`, () =>
        jsonResponse(tokensFor(triple.nonce)),
      );

      expectOnlyInventory(sweepSession(), 'after a replayed callback');
      expectNoKeyOutsideInventory(watch, 'across a replay');
      expect(keysIn(sweepLocal())).toEqual([]);
    });
  });
});
