/// <reference types="node" />
/**
 * `sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy` (package.md §8) — R10, L5, L4, L2.
 *
 * R10 says a SAS refusal for an unenrolled MERCHANT or ADMIN shows the wrong-password copy. The premise this
 * file was built on was MEASURED IN THE BACKEND in Phase 0, and it changes what there is to verify:
 *
 *     `TotpAuthenticationProvider.java:100-101` throws `BadCredentialsException("Bad credentials")` for a
 *     MERCHANT or ADMIN with no confirmed enrollment - the SAME exception with the SAME message as an unknown
 *     user (`:118-119`), a wrong password (`:125`) and a bad TOTP (`:132`). Its class comment (`:49-56`)
 *     records that a distinct "please enroll" signal was CONSIDERED AND REJECTED, because a reply that said
 *     "your password was right but you are not enrolled" is a password-correctness oracle.
 *
 * SO THE REFUSAL IS A LOGIN FAILURE, NOT AN AUTHORIZATION ERROR, and the wrong-password copy R10 names is
 * SAS's own login page - server-rendered, on the identity origin, and not this bundle's (L2). The visitor
 * never leaves it, so no SPA screen is reached at all on that path. This file does not get to assert the
 * refusal sentence, because the refusal sentence is not ours to assert.
 *
 * WHAT IS OURS, AND IT IS THE WHOLE OF R10's OBLIGATION ON THE CLIENT:
 *
 *     **the SPA knows nothing about enrollment, and every sign-in failure it can observe ends the same way.**
 *
 * Both halves matter and they fail differently. A SPA that LEARNED about enrollment - a branch, a sentence, a
 * module, a catalogue key - would be able to leak what the backend deliberately does not say. A SPA whose
 * failures DIVERGED would turn the authorization error response into the oracle the backend refused to be: a
 * callback is reachable with a chosen `error=` code, so a screen that said something different for one code
 * than another would answer questions about an account to anyone who can put a URL in front of a visitor.
 *
 * WHY THIS IS A FILE RATHER THAN A PARAGRAPH. Both halves are ABSENCE claims, so they pass by default, and
 * vacuity rather than incorrectness is the dominant risk. Four instruments, and every one of them is shown to
 * report DIRTY before it is trusted (AC11, T22-D14):
 *   1. a CONTENT SCAN over the comment-stripped tree for enrollment vocabulary, including `two-factor`;
 *   2. a PATH SCAN over the production file names, which catches an `EnrollMfaScreen.tsx` that says nothing
 *      incriminating inside itself;
 *   3. a GATING SCAN for account-state-shaped and role-shaped names, which is the shape a divergence would
 *      take BEFORE any copy existed to notice;
 *   4. a CATALOGUE SCAN over every user-facing string, with the `signIn.*` key set pinned to exactly six.
 * And one comparison - the OUTCOME STRUCT - which carries the uniformity half.
 *
 * WHY `two-factor` IS IN THE CONTENT PATTERN AND T27's SCAN WAS NOT ENOUGH (Kimi F3, accepted in part).
 * Measured: T27's `MFA_IDENTIFIER_SOURCE` does NOT match `"two-factor authentication"`, because its alternative
 * is `multi[-_ ]?factor`. So Kimi's own example sentence -
 *
 *     "Your account needs two-factor authentication set up before you can sign in"
 *
 * - would have passed every scan in this package. That is the hole, and it is the reason this file's content
 * pattern exists at all rather than deferring to T27's.
 *
 * WHY `required`, `incomplete` AND `please` ARE NOT IN IT (Kimi F3, the rejected half, and the important one).
 * Kimi asked for them. They would forbid the ordinary form-validation copy that tasks 43-57 are specified to
 * write - "Email is required", "Please enter an amount" - so the scan would block a permitted feature while
 * reporting an R10 breach. That is T26's lesson in its exact form: a guard must not forbid what the spec
 * permits. The pattern below names enrollment, not politeness.
 *
 * WHY THE GATING SCAN NEVER LOOKS FOR `state ===` (Kimi F6, accepted, suggested pattern refused). Kimi's
 * `state === 'PENDING_ENROLLMENT'` suggestion generalises to a bare `state ===`, which was MEASURED to have
 * three legitimate hits: `CallbackScreen.tsx`'s two OAuth `state` comparisons - the very checks R8 requires -
 * and `registerServiceWorker.ts:72`'s `installing.state === 'installed'`, a Workbox lifecycle read. A scan
 * that fired on those would be deleted by the next person to see it fail. It keys on the NAME OF THE THING
 * BEING ASKED ABOUT instead, which is T26's namespace lesson: `accountState`, `.mfaEnrolled`, `requiresMfa`,
 * `enrollmentState`, `mfaRequired`, `PENDING_ENROLLMENT`, and a role comparison. All measured to zero hits, so
 * none of them fires on anything that exists, and a case below proves the scan stays SILENT on
 * `CallbackScreen`'s own comparisons.
 *
 * WHY THE NOTICE IS NOT IN THE OUTCOME STRUCT, which the Phase 4 brief asked for and reality refused.
 * MEASURED: `SignInScreen.tsx:22` takes the notice in a `useState` initialiser, and `takeSignInNotice()` CLEARS
 * on read (`signInNotice.ts:22-26`). By the time a test can look, the screen has already consumed it, so the
 * taker returns `null` on every path - after a discard and after a success alike. A struct field reading it
 * would have been identical for the right and the wrong reason, and a test that TOOK it before the screen
 * rendered would have changed the copy it was also measuring. The notice is observed HERE THROUGH THE RENDERED
 * DOCUMENT, which is where it reaches the visitor anyway. Recorded as T28-D3.
 *
 * WHY THE CONSOLE SWEEP LOOKS FOR THE NEEDLE AND NOT FOR SILENCE. MEASURED: this suite's jsdom environment
 * emits React's own "testing environment is not configured to support act(...)" warning through
 * `console.error`, so a sweep asserting NOTHING was written would fail on ambient noise unrelated to T28. It
 * asserts instead that nothing the SPA wrote to any channel CONTAINS the error code, description or URI, which
 * is also the claim R10 actually makes. A sentinel per channel proves each one is read.
 *
 * WHAT THIS FILE CANNOT DO, said plainly because a page of green invites the opposite conclusion:
 *   - It cannot verify the refusal copy. That is SAS's server-rendered login page (L2, T28-D1). The only test
 *     that could is `oidcRoundTripEndToEnd`, which has no suite and no owning task.
 *   - It cannot prove the SPA is enrollment-blind for libraries nobody named. The content and path scans are
 *     enumerative; T27's dependency-manifest check is the one instrument in this package that fires on an
 *     arrival rather than on a word.
 *   - It cannot see a divergence introduced in a screen that does not exist yet. Only two routes have
 *     elements; the scans cover the tree, so a new screen is scanned, but no struct comparison reaches it.
 *   - It performs no navigation (T23-D5). `document.cookie` is empty and there is no `indexedDB` in jsdom, so
 *     those two surfaces are declared out of reach rather than swept and reported clean.
 */
import { readdir, readFile } from 'node:fs/promises';
import { dirname, join, relative } from 'node:path';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, describe, expect, it, type Mock, vi } from 'vitest';
import { en } from '../i18n/catalogue';
import { createI18n } from '../i18n/i18n';
import { DENYLISTED_NAVIGATION_PATHS } from '../pwa/navigationDenylist';
import { AUTHORIZE_STORAGE_KEYS } from './authorizeRequest';

// ---------------------------------------------------------------------------------------------------------
// The vocabulary, and why each pattern is drawn where it is
// ---------------------------------------------------------------------------------------------------------

/**
 * Enrollment, as a word and as an intent.
 *
 * `two-factor` is the addition this task exists for (see the header). `second factor`, `set up two` and
 * `register your authenticator` are the phrasings a helpful sentence takes when it avoids the word "enroll"
 * altogether - which is exactly how the breach would arrive, since nobody writes "enroll" in product copy.
 *
 * DELIBERATELY ABSENT: `required`, `incomplete`, `please`. Kimi asked for them and they were refused at the
 * Phase 4 gate, because they forbid the form-validation copy tasks 43-57 are specified to write. Do not add
 * them. If a future reader wants to, the reason they are gone belongs in this comment rather than in a commit
 * message nobody will find.
 */
const ENROLLMENT_SOURCE =
  'enroll|enrolment|two[-_ ]?factor|second[-_ ]?factor|set\\s+up\\s+two|register\\s+your\\s+authenticator'
  + '|activate\\s+mfa|pending[-_ ]?enrollment';

/**
 * A FRESH regex per use. A `g`-flagged constant carries `lastIndex` between calls, so a shared one answers
 * differently on its second `.test()` than on its first - found in T27's own first draft, where it made a case
 * pass in isolation and fail after a neighbour matched.
 */
function enrollmentWords(): RegExp {
  return new RegExp(ENROLLMENT_SOURCE, 'gi');
}

function namesEnrollment(text: string): boolean {
  return new RegExp(ENROLLMENT_SOURCE, 'i').test(text);
}

/**
 * The shape a divergence takes in code, keyed on the NAME OF THE THING BEING ASKED ABOUT.
 *
 * Never `state ===`: measured, three legitimate hits, two of them the OAuth comparisons R8 requires. A case
 * below asserts this scan finds none of them, because an instrument that fires on the code it is protecting
 * gets deleted rather than fixed.
 */
const ACCOUNT_STATE_SOURCE =
  'accountState|account_state|\\.mfaEnrolled|mfa_enrolled|requiresMfa|requires_mfa|enrollmentState'
  + '|enrollment_state|mfaRequired|mfa_required|PENDING_ENROLLMENT'
  + "|role\\s*[=!]==|\\.role\\s*[=!]==|=== *'(?:MERCHANT|ADMIN)'|=== *\"(?:MERCHANT|ADMIN)\"";

function accountStateGates(): RegExp {
  return new RegExp(ACCOUNT_STATE_SOURCE, 'g');
}

/**
 * Enrollment in a FILE OR DIRECTORY NAME (Kimi F5).
 *
 * The content scan cannot see this: a module named `EnrollMfaScreen.tsx` whose body spells nothing out would
 * pass it, and T27's import scan only catches it once something imports it. `mfa`, `totp` and `authenticator`
 * are included here although they are not enrollment words, because a file is named for what it does and this
 * is the one scan where the broader net costs nothing - measured zero hits across all 24 production files.
 */
function enrollmentShapedPath(): RegExp {
  return /enroll|enrolment|two[-_]?factor|second[-_]?factor|\bmfa\b|mfa[A-Z_-]|totp|authenticator/i;
}

/**
 * What a user-facing string may never mention.
 *
 * Broader than the source pattern, and it can afford to be: a catalogue has ten values, every one of them is
 * read by a visitor, and `code`, `locked` and `merchant` in copy are all ways of telling someone something
 * about their account that the backend refused to tell them. Measured: zero hits against all ten values
 * today, so this pattern forbids nothing that exists.
 */
const FORBIDDEN_COPY_SOURCE =
  ENROLLMENT_SOURCE
  + '|\\bmfa\\b|authenticator|\\botp\\b|\\btotp\\b|one[-_ ]?time|recovery|backup\\s+code|security\\s+key'
  + '|passkey|webauthn|\\bcode\\b|locked|lockout|suspended|not\\s+enrolled|\\brole\\b|merchant|administrator';

function forbiddenCopy(): RegExp {
  return new RegExp(FORBIDDEN_COPY_SOURCE, 'gi');
}

/**
 * The `signIn.*` keys that exist, pinned as an exact set (Kimi F7, reformulated).
 *
 * Kimi asked for an assertion that the failed-sign-in screen renders exactly `signIn.failed` and
 * `signIn.discarded`. That enumeration is factually wrong and would fail against correct code: the screen
 * renders `heading`, `explanation` and `action` as well, always. The defensible pin is the KEY SET - a seventh
 * `signIn.*` key is a new sentence somebody added, and this fails until they justify it here.
 */
const SIGN_IN_KEYS = ['action', 'discarded', 'explanation', 'failed', 'heading', 'starting'] as const;

// ---------------------------------------------------------------------------------------------------------
// Instrument 1 and 2 — the source and path scans
// ---------------------------------------------------------------------------------------------------------

type ScanTarget = { path: string; code: string };
type Hit = { file: string; hit: string };

/** A production file's code with its comments removed, because a file explains the rules it obeys. */
function strip(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
}

/**
 * Every production file under `src/`, comment-stripped, with the test files excluded.
 *
 * STRIPPING IS LOAD-BEARING HERE IN A WAY IT IS NOWHERE ELSE IN THE PACKAGE. `CallbackScreen.tsx:84-85` says
 * "task 28 maps even a SAS refusal onto the generic wrong-password copy" - it names this task, in a comment,
 * while explaining the rule this scan enforces. An unstripped scan reads its own justification as the defect,
 * and this file's header does the same thing four times over.
 *
 * The empty case THROWS rather than returning `[]`: a scan over nothing reports clean, which is the single
 * most likely way for this file to pass while proving nothing.
 */
async function productionSources(): Promise<ScanTarget[]> {
  const files = await productionFiles();
  const srcRoot = sourceRoot();
  const targets: ScanTarget[] = [];
  for (const file of files) {
    targets.push({ path: rel(srcRoot, file), code: strip(await readFile(file, 'utf8')) });
  }
  return targets;
}

/** The same tree as PATHS, which is a different claim: a name can incriminate a file whose body does not. */
async function productionPaths(): Promise<string[]> {
  const srcRoot = sourceRoot();
  return (await productionFiles()).map((file) => rel(srcRoot, file));
}

function sourceRoot(): string {
  // Two levels up from this file: `src/auth/<this>` -> `src`. Derived from the runner rather than written, so
  // moving the file does not silently narrow the scan to a directory that no longer exists.
  return dirname(dirname(expect.getState().testPath as string));
}

function rel(root: string, file: string): string {
  return relative(root, file).split(/[\\/]/).join('/');
}

/**
 * The emptiness guard, factored out so the CONTROL cases can call the REAL one.
 *
 * Its first draft was inlined at each call site, and the controls that proved it worked re-implemented the
 * same three lines - so they tested a copy of the logic and would have stayed green if the real guard were
 * deleted. A guard against vacuity that is itself vacuously tested is the joke this file cannot afford.
 */
function refuseEmpty<T>(items: T[], refusal: string): T[] {
  if (items.length === 0) throw new Error(refusal);
  return items;
}

const NO_SOURCES = 'scan refused: no production sources found under src/';
const NO_STRINGS = 'catalogue scan refused: no strings found';

async function productionFiles(): Promise<string[]> {
  async function walk(dir: string): Promise<string[]> {
    const found: string[] = [];
    for (const entry of await readdir(dir, { withFileTypes: true })) {
      const full = join(dir, entry.name);
      if (entry.isDirectory()) found.push(...(await walk(full)));
      else if (/\.tsx?$/.test(entry.name) && !/\.test\.tsx?$/.test(entry.name)) found.push(full);
    }
    return found;
  }
  return refuseEmpty((await walk(sourceRoot())).sort(), NO_SOURCES);
}

type PermittedHit = { file: string; hit: string; count: number; why: string };

/**
 * The hits a scan found that no row in the permitted table accounts for.
 *
 * COUNTED, not merely matched: a row says how many times a file may legally say the word, so a second
 * occurrence appearing in a file that was allowed one is reported. Factored out of the case that uses it so
 * the control can drive it with synthetic rows - the real table is empty today, which would otherwise leave
 * this accounting entirely untested.
 */
function unexplained(hits: Hit[], permittedHits: readonly PermittedHit[]): Hit[] {
  const budget = new Map(permittedHits.map((row) => [`${row.file}:${row.hit}`, row.count]));
  return hits.filter((hit) => {
    const key = `${hit.file}:${hit.hit}`;
    const allowed = budget.get(key) ?? 0;
    if (allowed === 0) return true;
    budget.set(key, allowed - 1);
    return false;
  });
}

function hitsOf(targets: ScanTarget[], pattern: RegExp): Hit[] {
  const found: Hit[] = [];
  for (const target of targets) {
    for (const match of target.code.matchAll(pattern)) {
      found.push({ file: target.path, hit: match[0].toLowerCase() });
    }
  }
  return found;
}

/** A hand-written target, for the synthetic violations AC11 requires. Never read from disk. */
function synthetic(path: string, code: string): ScanTarget {
  return { path, code: strip(code) };
}

// ---------------------------------------------------------------------------------------------------------
// Instrument 3 — the catalogue scan
// ---------------------------------------------------------------------------------------------------------

function catalogueStrings(): Array<{ key: string; value: string }> {
  const flat: Array<{ key: string; value: string }> = [];
  const visit = (node: unknown, path: string[]): void => {
    if (typeof node === 'string') {
      flat.push({ key: path.join('.'), value: node });
      return;
    }
    if (typeof node === 'object' && node !== null) {
      for (const [key, value] of Object.entries(node)) visit(value, [...path, key]);
    }
  };
  visit(en, []);
  // Same reason `productionFiles` refuses: an empty catalogue satisfies every claim below.
  return refuseEmpty(flat, NO_STRINGS);
}

/**
 * Two grammars, which T27 learned the hard way.
 *
 * A VALUE is prose, so a word-bounded regex is right for it. A KEY is an identifier, so `signIn.mfaRequired`
 * has no word boundary before `mfa` and a prose pattern misses it - T27's first draft failed to flag
 * `signIn.enterTotp` for exactly that reason. Keys are matched unbounded; values are matched as prose.
 */
function forbiddenCopyEntries(entries: Array<{ key: string; value: string }>): string[] {
  const flagged: string[] = [];
  for (const entry of entries) {
    if (forbiddenCopy().test(entry.value)) flagged.push(`${entry.key} (value)`);
    else if (new RegExp(FORBIDDEN_COPY_SOURCE.replace(/\\b/g, ''), 'i').test(entry.key)) {
      flagged.push(`${entry.key} (key)`);
    }
  }
  return flagged;
}

// ---------------------------------------------------------------------------------------------------------
// Instrument 4 — the console watcher
// ---------------------------------------------------------------------------------------------------------

const CONSOLE_CHANNELS = ['log', 'info', 'warn', 'error', 'debug', 'trace'] as const;

type ConsoleChannel = (typeof CONSOLE_CHANNELS)[number];

type ConsoleWatch = { lines: string[]; channels: readonly ConsoleChannel[] };

/**
 * Collects everything written to every console channel.
 *
 * Installed BEFORE the code under test runs, always. AC11 holds the control that a watcher installed
 * afterwards reports nothing - the shape every spy mistake in this package has taken.
 */
function watchConsole(): ConsoleWatch {
  const lines: string[] = [];
  for (const channel of CONSOLE_CHANNELS) {
    vi.spyOn(console, channel).mockImplementation((...args: unknown[]) => {
      lines.push(`${channel}: ${args.map((arg) => String(arg)).join(' ')}`);
    });
  }
  return { lines, channels: CONSOLE_CHANNELS };
}

// ---------------------------------------------------------------------------------------------------------
// The arrival harness
// ---------------------------------------------------------------------------------------------------------

const STATE = 'the-state-this-tab-stored';
const NONCE = 'the-nonce-this-tab-stored';
const CODE = 'THE-AUTHORIZATION-CODE';
const VERIFIER = 'v'.repeat(64);

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

function tokenEndpointAnswers(claims: Record<string, unknown> = {}): () => Response {
  return () =>
    new Response(
      JSON.stringify({
        id_token: tokenWith({ nonce: NONCE, sub: 'the-subject', iss: 'http://localhost:5173', ...claims }),
        access_token: 'the-access-token',
        token_type: 'Bearer',
        expires_in: 300,
      }),
      { status: 200, headers: { 'content-type': 'application/json' } },
    );
}

function tokenEndpointRefuses(): () => Response {
  return () =>
    new Response(JSON.stringify({ error: 'invalid_grant' }), {
      status: 400,
      headers: { 'content-type': 'application/json' },
    });
}

let mounted: { root: Root; container: HTMLElement } | undefined;

afterEach(() => {
  if (mounted) {
    act(() => mounted?.root.unmount());
    mounted.container.remove();
    mounted = undefined;
  }
  document.body.innerHTML = '';
  globalThis.sessionStorage.clear();
  globalThis.localStorage.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  vi.resetModules();
});

/** One macrotask turn inside act, because the real `crypto.subtle` settles later than act's microtask flush. */
async function turn(): Promise<void> {
  await act(async () => {
    await new Promise((resolve) => {
      setTimeout(resolve, 0);
    });
  });
}

function stubFetch(answer: () => Response | Promise<Response>): Mock {
  const NodeRequest = globalThis.Request;
  vi.stubGlobal(
    'Request',
    class extends NodeRequest {
      constructor(input: RequestInfo | URL, init?: RequestInit) {
        super(typeof input === 'string' ? new URL(input, 'http://localhost:3000') : input, init);
      }
    },
  );
  const spy = vi.fn(async () => answer());
  vi.stubGlobal('fetch', spy);
  return spy;
}

/** The three items T15 writes and the callback reads back (L16). Seeded so a callback can be admitted at all. */
function seedPkceItems(): void {
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.state, STATE);
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.nonce, NONCE);
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.codeVerifier, VERIFIER);
}

type Arrival = {
  container: HTMLElement;
  router: ReturnType<typeof createMemoryRouter>;
  fetchSpy: Mock;
  unmount(): void;
};

/**
 * Drives the real route graph at the real callback URL, with the token endpoint answering `answer`.
 *
 * The route graph is imported DYNAMICALLY because `vi.resetModules()` runs in `afterEach`: a statically
 * imported module would be a different instance than the screens hold (recorded in T26). A dynamic import is
 * still compile-checked, so a renamed export is a type error rather than a silent `undefined`.
 */
async function arriveAt(query: string, answer: () => Response | Promise<Response>): Promise<Arrival> {
  const fetchSpy = stubFetch(answer);
  const routes = await import('../app/routes');
  const router = createMemoryRouter(routes.appRoutes, {
    basename: routes.APP_BASENAME,
    initialEntries: [`${routes.APP_BASENAME}/${routes.CALLBACK_PATH}${query}`],
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
    // `arrival.unmount()`, never `mounted = undefined` - abandoning a root without unmounting leaves its
    // effects live, and a second `CallbackScreen` still running would have its writes attributed to the next
    // arrival. T26's own self-review found exactly that defect in its first draft.
    unmount: () => {
      act(() => root.unmount());
      container.remove();
      mounted = undefined;
    },
  };
}

// ---------------------------------------------------------------------------------------------------------
// The outcome struct — the uniformity half of the claim
// ---------------------------------------------------------------------------------------------------------

type Outcome = {
  destination: string;
  historyAction: string;
  search: string;
  hash: string;
  text: string;
  liveText: string[];
  liveRegions: number;
  storedKeys: string[];
  tokenHeld: boolean;
};

/**
 * ONE comparison unit for all four failure kinds, compared with a single `toEqual` over the whole struct.
 *
 * Not a field-by-field comparison spread across cases, and that is the design rather than a shortcut: a
 * forgotten field is exactly where a refusal-specific branch would hide, and a whole-struct `toEqual` cannot
 * forget one. `historyAction` is in it at Kimi's F4 request - a `push` instead of a `replace` would leave a
 * discarded callback reachable with the back button, breaking R8's discard semantics while every other field
 * stayed identical.
 *
 * THE NOTICE IS NOT A FIELD. It is already consumed by the time this runs (see the header, T28-D3); it is
 * observed through `liveText`, which is where it reaches the visitor.
 *
 * `accessToken` is imported DYNAMICALLY, inside this function, in the same module generation the screens hold.
 * A static import would be a stale instance from the generation before `vi.resetModules()` ran, and
 * `tokenHeld` would then read `false` for the right answer by accident.
 */
async function outcomeOf(arrival: Arrival): Promise<Outcome> {
  const { accessToken } = await import('./accessToken');
  const regions = [...document.querySelectorAll('[aria-live], [role=status], [role=alert]')];
  return {
    destination: arrival.router.state.location.pathname,
    historyAction: arrival.router.state.historyAction,
    search: arrival.router.state.location.search,
    hash: arrival.router.state.location.hash,
    text: document.body.textContent ?? '',
    liveText: regions.map((node) => node.textContent ?? ''),
    liveRegions: regions.length,
    storedKeys: Object.keys({ ...globalThis.sessionStorage }).sort(),
    tokenHeld: accessToken.current() !== null,
  };
}

type FailureKind = 'error-response' | 'mismatched-state' | 'refused-exchange' | 'bad-nonce';

/** Every way a sign-in can fail where the SPA is the one that notices. R8's four faults, plus R10's refusal. */
const FAILURE_KINDS: ReadonlyArray<{ kind: FailureKind; why: string }> = [
  { kind: 'error-response', why: "SAS refused - R10's path, including the unenrolled MERCHANT or ADMIN" },
  { kind: 'mismatched-state', why: "the callback is not this tab's (R8, T18)" },
  { kind: 'refused-exchange', why: 'the token endpoint said no (R8, T20)' },
  { kind: 'bad-nonce', why: 'the token was minted for another login (R7, T19)' },
];

function queryFor(kind: FailureKind): string {
  if (kind === 'error-response') return `?error=access_denied&state=${STATE}`;
  if (kind === 'mismatched-state') return `?code=${CODE}&state=a-state-this-tab-never-stored`;
  return `?code=${CODE}&state=${STATE}`;
}

function answerFor(kind: FailureKind): () => Response | Promise<Response> {
  if (kind === 'refused-exchange') return tokenEndpointRefuses();
  if (kind === 'bad-nonce') return tokenEndpointAnswers({ nonce: 'a-nonce-from-another-login' });
  return tokenEndpointAnswers();
}

/**
 * Arrives, measures and cleans up, so four kinds can be compared inside one case.
 *
 * The cleanup is not `afterEach`'s: within one case the module registry is shared, so a held token or a
 * pending notice from the previous arrival would be read as this one's. Everything stateful is reset through
 * the SAME generation the arrival used.
 */
async function measure(query: string, answer: () => Response | Promise<Response>): Promise<Outcome> {
  seedPkceItems();
  const arrival = await arriveAt(query, answer);
  await turn();
  const outcome = await outcomeOf(arrival);
  arrival.unmount();
  const { clearAccessToken } = await import('./accessToken');
  const { takeSignInNotice } = await import('./signInNotice');
  clearAccessToken();
  takeSignInNotice();
  document.body.innerHTML = '';
  globalThis.sessionStorage.clear();
  globalThis.localStorage.clear();
  return outcome;
}

function measureFailure(kind: FailureKind): Promise<Outcome> {
  return measure(queryFor(kind), answerFor(kind));
}

// ---------------------------------------------------------------------------------------------------------
// The leak sweep
// ---------------------------------------------------------------------------------------------------------

type Surface = { name: string; text: string };

/**
 * Every surface a value could survive on, after the discard.
 *
 * `document.cookie` and `indexedDB` are NOT here, and that is declared rather than quietly omitted: jsdom
 * gives an empty cookie jar and no `indexedDB`, so sweeping them would report clean for an environmental
 * reason and read as evidence. T22 owns the leak claim across the application; this sweep is R10's slice of it.
 */
function surfacesAfter(arrival: Arrival, consoleWatch: ConsoleWatch): Surface[] {
  return [
    { name: 'document', text: `${document.body.textContent ?? ''} ${document.body.innerHTML}` },
    { name: 'sessionStorage', text: JSON.stringify({ ...globalThis.sessionStorage }) },
    { name: 'localStorage', text: JSON.stringify({ ...globalThis.localStorage }) },
    { name: 'router location', text: JSON.stringify(arrival.router.state.location) },
    { name: 'console', text: consoleWatch.lines.join('\n') },
  ];
}

/** Which surfaces carry which needle. Returns `surface/needle` pairs so a failure names both. */
function leaks(surfaces: Surface[], needles: string[]): string[] {
  const found: string[] = [];
  for (const surface of surfaces) {
    for (const needle of needles) {
      if (surface.text.toLowerCase().includes(needle.toLowerCase())) found.push(`${surface.name}/${needle}`);
    }
  }
  return found;
}

// ---------------------------------------------------------------------------------------------------------
// AC1 — the refusal is discarded
// ---------------------------------------------------------------------------------------------------------

describe('AC1: a SAS refusal ends in the one discard closure', () => {
  it('routes to the sign-in start with the discarded copy, replacing history, holding no token', async () => {
    const outcome = await measureFailure('error-response');
    expect(outcome.destination).toBe('/app/sign-in');
    expect(outcome.historyAction).toBe('REPLACE');
    expect(outcome.liveText).toEqual([en.signIn.discarded]);
    expect(outcome.tokenHeld).toBe(false);
  });

  it('never exchanges the code, because an error response carries none (RFC 6749 §4.1.2.1)', async () => {
    seedPkceItems();
    const arrival = await arriveAt(`?error=access_denied&state=${STATE}`, tokenEndpointAnswers());
    await turn();
    expect(arrival.fetchSpy).not.toHaveBeenCalled();
  });

  it('shows the visitor the same sentence the catalogue owns, not a literal written here', async () => {
    const outcome = await measureFailure('error-response');
    // Both halves matter: the sentence is the catalogue's, and it names no cause. A refusal-specific sentence
    // would satisfy the first and breach R10 through the second.
    expect(outcome.text).toContain(en.signIn.discarded);
    expect(namesEnrollment(outcome.text)).toBe(false);
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC1/AC2 — the error branch precedes the state check (Kimi F1)
// ---------------------------------------------------------------------------------------------------------

describe('AC2: an error response is never a state oracle', () => {
  /**
   * Kimi F1, accepted - and it pins an ORDERING property rather than filling a gap.
   *
   * `CallbackScreen.tsx:101-104` tests `error` BEFORE the state comparison at `:138`. If those were swapped, a
   * refusal carrying no `state` and a refusal carrying a matching one would still both discard, but they would
   * discard for different reasons - and anything that later distinguished the reasons would hand an attacker a
   * test for "did this tab start a sign-in". Three `state` shapes, one outcome, is what forecloses it.
   */
  const stateShapes = [
    { label: 'no state parameter at all', query: '?error=access_denied' },
    { label: 'a state this tab never stored', query: '?error=access_denied&state=not-the-stored-one' },
    { label: 'the state this tab did store', query: `?error=access_denied&state=${STATE}` },
  ];

  it('treats all three state shapes identically when an error is present', async () => {
    const outcomes: Array<{ label: string; outcome: Outcome }> = [];
    for (const shape of stateShapes) {
      outcomes.push({ label: shape.label, outcome: await measure(shape.query, tokenEndpointAnswers()) });
    }
    expect(outcomes).toHaveLength(3);
    for (const { label, outcome } of outcomes.slice(1)) {
      expect(outcome, `${label} diverged from the no-state arrival`).toEqual(outcomes[0].outcome);
    }
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC2 — outcome equivalence, and the control that it is not blind
// ---------------------------------------------------------------------------------------------------------

describe('AC2: four failure kinds, one outcome', () => {
  it('produces an identical struct for the refusal, a bad state, a refused exchange and a bad nonce', async () => {
    const measured: Array<{ kind: FailureKind; outcome: Outcome }> = [];
    for (const { kind } of FAILURE_KINDS) measured.push({ kind, outcome: await measureFailure(kind) });
    // Guards the loop itself. An absolute four, not `FAILURE_KINDS.length`: both sides of that comparison come
    // from the same array, so it would stay green if the list were emptied and every assertion below became
    // vacuous. R8's four faults are the four this file is for.
    expect(measured).toHaveLength(4);
    expect(measured.map((entry) => entry.kind)).toEqual(FAILURE_KINDS.map((entry) => entry.kind));
    const reference = measured[0];
    for (const { kind, outcome } of measured.slice(1)) {
      expect(outcome, `${kind} diverged from ${reference.kind}`).toEqual(reference.outcome);
    }
  });

  it('CONTROL: the comparison fails on a successful callback, so it is not blind', async () => {
    const failure = await measureFailure('error-response');
    const success = await measure(`?code=${CODE}&state=${STATE}`, tokenEndpointAnswers());
    expect(success).not.toEqual(failure);
    // Named rather than left to `not.toEqual`, which would also pass if only one field differed. A struct
    // comparison is only as strong as the number of fields that actually move, so the four that move are
    // asserted: measured in Phase 6, a success lands on /app/home with no copy, no live region and no items.
    expect(success.destination).toBe('/app/home');
    expect(success.text).toBe('');
    expect(success.liveRegions).toBe(0);
    expect(success.storedKeys).toEqual([]);
    expect(failure.storedKeys.length).toBeGreaterThan(0);
  });

  it('CONTROL: the struct is not a shell of empty fields', async () => {
    const outcome = await measureFailure('error-response');
    // Every assertion in this file rests on these fields carrying something. T27's equivalent struct had an
    // empty `text` on its success path, which is why that file could not use this control; here the discard
    // renders copy, so it can.
    expect(outcome.text.length).toBeGreaterThan(0);
    expect(outcome.liveText.join('').length).toBeGreaterThan(0);
    expect(outcome.storedKeys).toEqual(
      [AUTHORIZE_STORAGE_KEYS.state, AUTHORIZE_STORAGE_KEYS.nonce, AUTHORIZE_STORAGE_KEYS.codeVerifier].sort(),
    );
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC3 — an error that names its cause changes nothing
// ---------------------------------------------------------------------------------------------------------

describe('AC3: naming the cause changes nothing', () => {
  /**
   * The vendor row and the two OIDC description fields are Kimi F2, accepted.
   *
   * The point is not that SAS sends these - it sends `BadCredentialsException` and nothing else, measured in
   * Phase 0. It is that `/app/callback` is a PUBLIC ROUTE and anyone can put any of these in front of a
   * visitor. A screen that behaved differently for `mfa_enrollment_required` than for `access_denied` would be
   * an oracle reachable by URL, which is a worse leak than the one the backend refused to make.
   */
  const NAMED_CAUSES: ReadonlyArray<{ label: string; query: string }> = [
    { label: 'access_denied', query: `?error=access_denied&state=${STATE}` },
    { label: 'invalid_request', query: `?error=invalid_request&state=${STATE}` },
    { label: 'unauthorized_client', query: `?error=unauthorized_client&state=${STATE}` },
    { label: 'login_required', query: `?error=login_required&state=${STATE}` },
    { label: 'interaction_required', query: `?error=interaction_required&state=${STATE}` },
    {
      label: 'a vendor code spelling enrollment',
      query: `?error=mfa_enrollment_required&state=${STATE}`,
    },
    {
      label: 'a description naming two-factor enrollment',
      query:
        `?error=access_denied&state=${STATE}`
        + `&error_description=${encodeURIComponent('Two-factor authentication must be set up before you can sign in')}`,
    },
    {
      label: 'a URI pointing at an enrollment page',
      query:
        `?error=access_denied&state=${STATE}`
        + `&error_uri=${encodeURIComponent('https://id.example/help/enroll-your-authenticator')}`,
    },
  ];

  it('produces the same outcome for every named cause', async () => {
    const measured: Array<{ label: string; outcome: Outcome }> = [];
    for (const cause of NAMED_CAUSES) {
      measured.push({ label: cause.label, outcome: await measure(cause.query, tokenEndpointAnswers()) });
    }
    // Absolute, for the reason spelled out in AC2's loop: a length read from the array under test proves
    // nothing about it.
    expect(measured).toHaveLength(8);
    for (const { label, outcome } of measured.slice(1)) {
      expect(outcome, `${label} produced a different outcome`).toEqual(measured[0].outcome);
    }
  });

  it('shows the enrollment-naming description none of its own words', async () => {
    const DESCRIPTION = 'Two-factor authentication must be set up before you can sign in';
    const outcome = await measure(
      `?error=access_denied&state=${STATE}&error_description=${encodeURIComponent(DESCRIPTION)}`,
      tokenEndpointAnswers(),
    );
    expect(namesEnrollment(outcome.text)).toBe(false);
    expect(outcome.text).not.toContain('Two-factor');
    // The control for the line above: the needle IS enrollment-shaped, so a scan blind to it would pass the
    // assertion for the wrong reason.
    expect(namesEnrollment(DESCRIPTION)).toBe(true);
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC4 — nothing of the error survives the discard
// ---------------------------------------------------------------------------------------------------------

describe('AC4: the code, description and URI do not survive the discard', () => {
  const CODE_NEEDLE = 'mfa_enrollment_required';
  const DESCRIPTION_NEEDLE = 'Two-factor authentication must be set up';
  const URI_NEEDLE = 'https://id.example/help/enroll-your-authenticator';

  function loudQuery(): string {
    return (
      `?error=${CODE_NEEDLE}&state=${STATE}`
      + `&error_description=${encodeURIComponent(DESCRIPTION_NEEDLE)}`
      + `&error_uri=${encodeURIComponent(URI_NEEDLE)}`
    );
  }

  it('leaves none of the three on the document, either store, the location or any console channel', async () => {
    const watch = watchConsole();
    seedPkceItems();
    const arrival = await arriveAt(loudQuery(), tokenEndpointAnswers());
    await turn();
    const swept = surfacesAfter(arrival, watch);
    // The arrival URL carried all three, because SAS put them there. The claim is about what survives.
    expect(arrival.router.state.location.pathname).toBe('/app/sign-in');
    expect(leaks(swept, [CODE_NEEDLE, DESCRIPTION_NEEDLE, URI_NEEDLE])).toEqual([]);
  });

  it('CONTROL: every swept surface is actually read, proven with a sentinel on each', async () => {
    const watch = watchConsole();
    seedPkceItems();
    const arrival = await arriveAt(loudQuery(), tokenEndpointAnswers());
    await turn();
    // One sentinel per surface, planted by this test rather than by the SPA. If `leaks` does not report all
    // five, a surface in `surfacesAfter` is being collected and never searched - which is how a sweep passes
    // while reading four things and claiming five.
    document.body.append(Object.assign(document.createElement('p'), { textContent: 'SENTINEL-DOCUMENT' }));
    globalThis.sessionStorage.setItem('sentinel-session', 'SENTINEL-SESSION');
    globalThis.localStorage.setItem('sentinel-local', 'SENTINEL-LOCAL');
    console.warn('SENTINEL-CONSOLE');
    const swept = surfacesAfter(arrival, watch);
    expect(leaks(swept, ['SENTINEL-DOCUMENT'])).toEqual(['document/SENTINEL-DOCUMENT']);
    expect(leaks(swept, ['SENTINEL-SESSION'])).toEqual(['sessionStorage/SENTINEL-SESSION']);
    expect(leaks(swept, ['SENTINEL-LOCAL'])).toEqual(['localStorage/SENTINEL-LOCAL']);
    expect(leaks(swept, ['SENTINEL-CONSOLE'])).toEqual(['console/SENTINEL-CONSOLE']);
    // The router location is the one surface a test cannot plant into, so it is asserted by content: the
    // sweep reads the real location object, which carries the path it navigated to.
    expect(leaks(swept, ['/app/sign-in'])).toEqual(['router location//app/sign-in']);
  });

  it('CONTROL: every console channel is watched, not only the ones the flow happens to use', async () => {
    const watch = watchConsole();
    for (const channel of watch.channels) {
      console[channel](`SENTINEL-${channel.toUpperCase()}`);
    }
    expect(watch.lines).toEqual(
      watch.channels.map((channel) => `${channel}: SENTINEL-${channel.toUpperCase()}`),
    );
  });

  it('CONTROL: a console watcher installed after the arrival sees nothing that was written before it', async () => {
    const early = watchConsole();
    console.warn('WRITTEN-BEFORE-THE-SECOND-WATCHER');
    vi.restoreAllMocks();
    const late = watchConsole();
    expect(early.lines).toContain('warn: WRITTEN-BEFORE-THE-SECOND-WATCHER');
    expect(late.lines).toEqual([]);
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC5 — structural silence, by content
// ---------------------------------------------------------------------------------------------------------

describe('AC5: no production module names enrollment', () => {
  /**
   * Every hit the pattern is allowed to find, with the reason. Empty TODAY, and that is the measured fact
   * rather than an unfilled table: zero hits across all 24 production files, comment-stripped. A future
   * permitted hit belongs here with its reason, which is the friction this table exists to create.
   */
  const PERMITTED_HITS: readonly PermittedHit[] = [];

  it('finds no enrollment vocabulary outside the permitted table', async () => {
    const targets = await productionSources();
    expect(targets.length).toBeGreaterThanOrEqual(20);
    expect(unexplained(hitsOf(targets, enrollmentWords()), PERMITTED_HITS)).toEqual([]);
  });

  it("CONTROL: the scan flags Kimi's own example sentence, which T27's pattern missed", async () => {
    // The sentence is quoted verbatim from `artifacts/03-design-challenge.md` F3, because it is the exact
    // phrasing that would have escaped this package before T28: T27's `MFA_IDENTIFIER_SOURCE` has
    // `multi[-_ ]?factor` and no `two[-_ ]?factor`, so it does not match this.
    const offender = synthetic(
      'auth/HelpfulSignInScreen.tsx',
      `export function Hint() {
         return <p>Your account needs two-factor authentication set up before you can sign in</p>;
       }`,
    );
    expect(hitsOf([offender], enrollmentWords()).length).toBeGreaterThan(0);
  });

  it('CONTROL: the scan flags each enrollment phrasing on its own', () => {
    const phrasings = [
      'please enroll in MFA',
      'complete your enrolment',
      'two-factor authentication',
      'two factor setup',
      'a second factor is needed',
      'set up two steps',
      'register your authenticator app',
      'activate MFA now',
      "state === 'PENDING_ENROLLMENT'",
    ];
    const missed = phrasings.filter((phrasing) => !namesEnrollment(phrasing));
    expect(missed).toEqual([]);
  });

  it('CONTROL: the scan does NOT flag the form-validation copy tasks 43-57 are specified to write', () => {
    // Kimi F3's rejected half, held as a case so it cannot be re-accepted quietly. These are permitted copy;
    // a scan that flagged them would block a specified feature while reporting an R10 breach.
    const permitted = [
      'Email is required',
      'Please enter an amount',
      'This field is incomplete',
      'Please try again',
      'Enter the invoice reference',
    ];
    const wronglyFlagged = permitted.filter((sentence) => namesEnrollment(sentence));
    expect(wronglyFlagged).toEqual([]);
  });

  it('CONTROL: a scan over an empty tree is refused rather than reported clean', () => {
    // The vacuity that matters most: `hitsOf([], …)` returns `[]`, which satisfies the claim above while
    // reading nothing at all. The guard below is the REAL one the walker calls, not a copy of it.
    expect(hitsOf([], enrollmentWords())).toEqual([]);
    expect(() => refuseEmpty([], NO_SOURCES)).toThrow(NO_SOURCES);
    expect(refuseEmpty(['one'], NO_SOURCES)).toEqual(['one']);
  });

  it('CONTROL: the permitted-hit accounting admits an explained hit and no more', async () => {
    // `PERMITTED_HITS` is EMPTY today, which means the accounting below is never exercised by the real tree -
    // so a wrong count in a future row would go unnoticed. This case exercises it against synthetic rows.
    const offender = synthetic('auth/Hint.tsx', 'const a = "enroll"; const b = "enroll";');
    const hits = hitsOf([offender], enrollmentWords());
    expect(hits).toHaveLength(2);
    expect(unexplained(hits, [{ file: 'auth/Hint.tsx', hit: 'enroll', count: 2, why: 'synthetic' }])).toEqual([]);
    // One short: the second hit is reported, which is what a wrong count looks like.
    expect(
      unexplained(hits, [{ file: 'auth/Hint.tsx', hit: 'enroll', count: 1, why: 'synthetic' }]),
    ).toEqual([{ file: 'auth/Hint.tsx', hit: 'enroll' }]);
    // A row for another file explains nothing here.
    expect(
      unexplained(hits, [{ file: 'auth/Other.tsx', hit: 'enroll', count: 2, why: 'synthetic' }]),
    ).toHaveLength(2);
  });

  it('CONTROL: comment stripping is what makes this scan survivable at all', async () => {
    // `CallbackScreen.tsx:84-85` names task 28 inside a comment while explaining this very rule, and this
    // file's own header spells `two-factor` several times. An unstripped scan reads its justification as the
    // defect - so the stripper is asserted against the real file rather than a fixture.
    const srcRoot = sourceRoot();
    const raw = await readFile(join(srcRoot, 'auth', 'CallbackScreen.tsx'), 'utf8');
    expect(raw).toContain('task 28');
    expect(strip(raw)).not.toContain('task 28');
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC6 — structural silence, by path
// ---------------------------------------------------------------------------------------------------------

describe('AC6: no production file or directory is named for enrollment', () => {
  it('finds no enrollment-shaped path in the production tree', async () => {
    const paths = await productionPaths();
    expect(paths.length).toBeGreaterThan(0);
    expect(paths.filter((path) => enrollmentShapedPath().test(path))).toEqual([]);
  });

  it('CONTROL: the scan flags a file whose NAME incriminates it and whose body does not', () => {
    // Kimi F5's case, and the reason this scan is separate from AC5's: this module would pass the content
    // scan, pass T27's identifier scan, and pass T27's import scan until something imported it.
    const innocent = 'export function Screen() { return <p>Follow the steps on the next page.</p>; }';
    expect(hitsOf([synthetic('auth/EnrollMfaScreen.tsx', innocent)], enrollmentWords())).toEqual([]);
    expect(enrollmentShapedPath().test('auth/EnrollMfaScreen.tsx')).toBe(true);
    expect(enrollmentShapedPath().test('auth/enrollment/index.ts')).toBe(true);
    expect(enrollmentShapedPath().test('account/two-factor/Setup.tsx')).toBe(true);
  });

  it('CONTROL: the scan does not fire on the paths that exist today', async () => {
    // A path pattern is easy to draw too wide - `mfa` unbounded would hit `mfaFree.ts`, and a bare `code`
    // would hit `codeExchange.ts`, which is PKCE's and permitted. Asserted against the real tree so the
    // pattern's width is measured rather than assumed.
    const paths = await productionPaths();
    expect(paths).toContain('auth/codeExchange.ts');
    expect(paths).toContain('auth/pkce.ts');
    expect(enrollmentShapedPath().test('auth/codeExchange.ts')).toBe(false);
    expect(enrollmentShapedPath().test('auth/pkce.ts')).toBe(false);
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC7 — no account-state or role gating
// ---------------------------------------------------------------------------------------------------------

describe('AC7: nothing gates a failed sign-in on an account state or a role', () => {
  it('finds no account-state-shaped or role-shaped comparison in production source', async () => {
    const targets = await productionSources();
    expect(targets.length).toBeGreaterThan(0);
    expect(hitsOf(targets, accountStateGates())).toEqual([]);
  });

  it('CONTROL: the scan flags a role gate', () => {
    const offender = synthetic(
      'auth/SignInScreen.tsx',
      "if (account.role === 'MERCHANT') { return <p>Set up your authenticator first.</p>; }",
    );
    expect(hitsOf([offender], accountStateGates()).length).toBeGreaterThan(0);
  });

  it('CONTROL: the scan flags an account-state gate', () => {
    // Kimi F6's substance: a gate on enrollment state breaks the same rule as a role gate and the role-only
    // control would not see it.
    const offenders = [
      synthetic('auth/A.tsx', 'if (account.mfaEnrolled === false) discard();'),
      synthetic('auth/B.tsx', 'if (user.requiresMfa) showEnrollmentHint();'),
      synthetic('auth/C.tsx', "if (accountState === 'PENDING_ENROLLMENT') return null;"),
      synthetic('auth/D.tsx', 'const { enrollmentState } = session;'),
      synthetic('auth/E.tsx', 'if (mfaRequired) navigate(SETUP);'),
    ];
    const unflagged = offenders.filter((target) => hitsOf([target], accountStateGates()).length === 0);
    expect(unflagged.map((target) => target.path)).toEqual([]);
  });

  it("CONTROL: the scan stays SILENT on CallbackScreen's own OAuth state comparisons", async () => {
    // Kimi F6's suggested pattern - `state === 'PENDING_ENROLLMENT'` generalised to a bare `state ===` - was
    // measured to have THREE legitimate hits: the two comparisons R8 requires, and Workbox's
    // `installing.state === 'installed'`. This case is why the pattern keys on the name of the thing being
    // asked about. An instrument that fails on the code it protects gets deleted, not fixed.
    const targets = await productionSources();
    const bareState = targets.flatMap((target) =>
      [...target.code.matchAll(/state\s*===/g)].map(() => target.path),
    );
    expect(bareState.length).toBeGreaterThanOrEqual(3);
    expect(bareState).toContain('auth/CallbackScreen.tsx');
    // The same files, under this file's actual pattern: nothing.
    expect(hitsOf(targets, accountStateGates())).toEqual([]);
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC8 / AC9 — the catalogue
// ---------------------------------------------------------------------------------------------------------

describe('AC8: no user-facing string mentions enrollment, a factor, a code or a lockout', () => {
  it('scans every string in the catalogue and finds none', () => {
    const entries = catalogueStrings();
    expect(entries.length).toBeGreaterThan(0);
    expect(forbiddenCopyEntries(entries)).toEqual([]);
  });

  it('CONTROL: a synthetic enrollment sentence is flagged by its VALUE', () => {
    expect(
      forbiddenCopyEntries([
        { key: 'signIn.hint', value: 'Set up two-factor authentication to continue.' },
      ]),
    ).toEqual(['signIn.hint (value)']);
  });

  it('CONTROL: a synthetic key is flagged by its NAME even when its value is innocent', () => {
    // T27's lesson: `signIn.mfaRequired` has no word boundary before `mfa`, so a prose pattern misses it.
    // Two grammars, and this case is the one that proves the second exists.
    expect(forbiddenCopyEntries([{ key: 'signIn.mfaRequired', value: 'Try again.' }])).toEqual([
      'signIn.mfaRequired (key)',
    ]);
    expect(forbiddenCopyEntries([{ key: 'signIn.enterTotp', value: 'Try again.' }])).toEqual([
      'signIn.enterTotp (key)',
    ]);
  });

  it('CONTROL: an empty catalogue is refused rather than reported clean', () => {
    expect(forbiddenCopyEntries([])).toEqual([]);
    expect(() => refuseEmpty([], NO_STRINGS)).toThrow(NO_STRINGS);
    expect(catalogueStrings().length).toBeGreaterThanOrEqual(10);
  });

  it('pins the signIn key set to exactly the six that exist, so a seventh fails', () => {
    // Kimi F7, reformulated: Kimi asked for "exactly signIn.failed and signIn.discarded", which is wrong -
    // the screen renders heading, explanation and action as well. The defensible pin is the key set.
    expect(Object.keys(en.signIn).sort()).toEqual([...SIGN_IN_KEYS]);
  });

  it("CONTROL: the key-set pin fails when a seventh key arrives, whatever it is called", () => {
    const withSeventh = { ...en.signIn, needsEnrollment: 'Set up your authenticator.' };
    expect(Object.keys(withSeventh).sort()).not.toEqual([...SIGN_IN_KEYS]);
    // And its value would be caught by the copy scan too, which is the belt to the key set's braces.
    expect(
      forbiddenCopyEntries([{ key: 'signIn.needsEnrollment', value: withSeventh.needsEnrollment }]).length,
    ).toBeGreaterThan(0);
  });
});

describe('AC9: one sentence per outcome, and no sentence names a cause', () => {
  it('keeps the failed and discarded sentences distinct', () => {
    expect(en.signIn.failed).not.toBe(en.signIn.discarded);
    expect(en.signIn.failed.length).toBeGreaterThan(0);
    expect(en.signIn.discarded.length).toBeGreaterThan(0);
  });

  it('names no cause in either', () => {
    for (const sentence of [en.signIn.failed, en.signIn.discarded]) {
      expect(namesEnrollment(sentence)).toBe(false);
      expect(forbiddenCopyEntries([{ key: 'probe', value: sentence }])).toEqual([]);
    }
  });

  it('renders exactly one of them at a time, so no screen shows two epitaphs at once', async () => {
    const outcome = await measureFailure('error-response');
    expect(outcome.liveText).toEqual([en.signIn.discarded]);
    expect(outcome.text).not.toContain(en.signIn.failed);
    expect(outcome.text).not.toContain(en.signIn.starting);
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC10 — /login is not ours to answer
// ---------------------------------------------------------------------------------------------------------

describe('AC10: the SAS login page is unanswerable by the SPA', () => {
  it('denylists /login, cited from the module that owns the list', () => {
    // One assertion, from `navigationDenylist.ts`, which copies the list verbatim from `agents.md:88-89`.
    // R10's copy lives on that page (L2), so the worker handing it a cached shell would replace the only
    // screen that can show the refusal with one that cannot.
    expect(DENYLISTED_NAVIGATION_PATHS).toContain('/login');
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC11 — the instruments, rolled up
// ---------------------------------------------------------------------------------------------------------

describe('AC11: every instrument has been shown to report dirty', () => {
  /**
   * A SIGNPOST, NOT EVIDENCE, and said so because T27's Kimi review found a maintainer could read its
   * equivalent as proof (Gap 6). This table cannot fail for the right reason - it fails only if a row is
   * deleted without updating the count. The real proof is the CONTROL cases named in each row, which fail if
   * their instrument stops working.
   */
  const INSTRUMENTS = [
    { instrument: 'content scan', dirtiedBy: "Kimi's own two-factor sentence (AC5)" },
    { instrument: 'path scan', dirtiedBy: 'auth/EnrollMfaScreen.tsx with an innocent body (AC6)' },
    { instrument: 'gating scan', dirtiedBy: 'a role gate and five account-state gates (AC7)' },
    { instrument: 'catalogue scan', dirtiedBy: 'a value and two keys (AC8)' },
    { instrument: 'key-set pin', dirtiedBy: 'a seventh signIn key (AC8)' },
    { instrument: 'leak sweep', dirtiedBy: 'a sentinel on each of five surfaces (AC4)' },
    { instrument: 'console watcher', dirtiedBy: 'a sentinel on each of six channels (AC4)' },
    { instrument: 'outcome struct', dirtiedBy: 'a successful callback (AC2)' },
  ];

  it('names one dirtying violation per instrument', () => {
    expect(INSTRUMENTS).toHaveLength(8);
    expect(INSTRUMENTS.filter((row) => row.dirtiedBy.length === 0)).toEqual([]);
  });

  it('refuses a scan that reads nothing, through the one guard both walkers call', async () => {
    // The one case in this block that is evidence rather than signposting. It calls the REAL guard, which is
    // the whole reason `refuseEmpty` was factored out of the two walkers.
    expect(() => refuseEmpty([], NO_SOURCES)).toThrow(NO_SOURCES);
    expect(() => refuseEmpty([], NO_STRINGS)).toThrow(NO_STRINGS);
    expect((await productionSources()).length).toBeGreaterThanOrEqual(20);
    expect((await productionPaths()).length).toBeGreaterThanOrEqual(20);
    expect(catalogueStrings().length).toBeGreaterThanOrEqual(10);
  });
});
