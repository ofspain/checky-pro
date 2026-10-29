/// <reference types="node" />
/**
 * `loginChallengeRunsInsideSasFlowAndResumesAtCallback` (package.md §8) — R9, L2, L5, L11.
 *
 * R9 has two halves and both are about RESTRAINT: the TOTP or recovery-code challenge runs inside the
 * SAS-hosted flow, and the SPA resumes at the callback once it completes. So there is no feature here to
 * verify. What this file verifies is that a feature has not appeared.
 *
 * WHY THAT IS A FILE RATHER THAN A PARAGRAPH. The failure mode this task guards against is not a bug; it is a
 * plausible-looking addition. A one-time-code field beside the sign-in button, an "enter your authenticator
 * code" step before the redirect, a branch in the callback for "MFA was used" - each would look like progress,
 * each would breach L2, and two of them would breach L4 and R10 as well, because a screen that asks for a
 * second factor has already told the visitor the first one was right.
 *
 * THE MEASURED PREMISE, READ FROM THE BACKEND IN PHASE 0, AND IT IS STRONGER THAN R9 IMPLIES. The second
 * factor is a FIELD, NOT A STEP: `TotpAuthenticationDetailsSource` captures an optional `mfaCode` submitted in
 * the SAME REQUEST as the password - its own comment calls this "task 20's single-request MFA design, O4" -
 * and `SecurityChainsConfig` registers `TotpAuthenticationProvider` as the only provider that filter's manager
 * can reach, so a password alone cannot authenticate. One request, both factors, one response.
 *
 * So the SPA cannot observe how many factors were verified. It hands over control with a full-page navigation
 * and gets it back at `/app/callback`, and nothing in between is visible to it. That is not a gap in this
 * file's reach; it is the architecture R9 describes. It is also what tells us what the verifiable claim is:
 *
 *     **the SPA contains no factor, and its resumption is symmetric.**
 *     **The fixture differs; the outcome does not.**
 *
 * ABSENCE IS PROVED BY INSTRUMENTS, AND EVERY INSTRUMENT IS WATCHED TO FAIL FIRST (AC11, T22-D14). A file whose
 * subject is absence passes by default, which makes vacuity the dominant risk here rather than incorrectness.
 * Four instruments, each paired in this file with the violation that must break it:
 *   1. a HAND-OVER COUNTER over every navigation mechanism (not just `assign`);
 *   2. a comment-stripped SOURCE SCAN for credential shapes, MFA identifiers and forbidden imports;
 *   3. a DEPENDENCY MANIFEST check, which catches a second-factor package one step before any module can
 *      import it - and catches it even in a file the source walker never reaches;
 *   4. a CATALOGUE SCAN over every user-facing string, because copy is where an MFA disclosure would surface.
 *
 * WHY THE COUNTER WATCHES FOUR MECHANISMS AND NOT `assign` ALONE (Kimi F1, accepted and strengthened). A future
 * change that REPLACED `assign` with `location.href = url` would make an `assign`-only case fail, which is a
 * correct outcome with a misleading message. The real hole is an ADDITION: a second navigation fired through
 * `href` or `window.open` ALONGSIDE the existing `assign` leaves an `assign`-only spy reporting exactly one
 * call, and the case passes while the SPA has grown a second hand-over. Measured in Phase 6: all four
 * mechanisms are observable on a stubbed `location`, so the count is of HAND-OVERS, not of one method.
 * (The real jsdom `location.href` descriptor is `configurable: false` and cannot be patched in place; this
 * file replaces the whole object, as T16 does, which is why the setter is reachable at all.)
 *
 * WHY THE PARAMETER SET IS EIGHT LITERALS, WHICH LOOKS LIKE THE DEFECT THIS PACKAGE FORBIDS (AC10, Kimi F6
 * REJECTED). `signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx:223` already
 * compares the screen's URL to a directly built one:
 *
 *     expect([...fromScreen.keys()].sort()).toEqual([...direct.keys()].sort());
 *
 * That owns a real and different claim - the screen sends what the builder built - and it is STRUCTURALLY
 * INCAPABLE of catching an addition, because both sides come from the same builder. An `acr_values` parameter
 * added to `authorizeRequest.ts` appears on both sides and the comparison stays green. Deriving the expected
 * set here would duplicate that line and delete the only thing this file contributes. The eight names are also
 * not T15's to rename: they are wire spellings fixed by RFC 6749 §4.1.1 and OIDC Core §3.1.2.1, and renaming
 * one would break the contract with SAS rather than this test. The package's "no literal" rule is about VALUES
 * a module owns and may re-spell, and those still come from their owners.
 *
 * WHAT THIS FILE CANNOT DO, said here because a page of green invites the opposite conclusion:
 * - **jsdom performs no navigation** (T23-D5). The hand-over is a counter plus structure. A genuine round trip
 *   through the SAS login page is `oidcRoundTripEndToEnd`'s, which has no suite and no owning task - the
 *   largest verification gap in the package.
 * - **It cannot see inside the SAS flow at all**, by R9's own design. It cannot tell a sign-in that used one
 *   factor from one that used two, which is the point: neither can the SPA.
 * - **The forbidden-package and identifier lists are ENUMERATIVE, NOT EXHAUSTIVE.** A scan over named patterns
 *   cannot prove the absence of a library nobody thought of. A new client-side credential surface is a design
 *   decision under L2, not a test change.
 * - **`amr` and `acr` are not in `contracts/api/token-claims.md`** (OQ3), which is already known to be wrong
 *   about `email` and `name`. So the paired fixtures prove INDIFFERENCE only: no case may assert that either
 *   claim is present, and the SPA's indifference is visible in `codeExchange.ts:76`, which destructures
 *   `id_token` and `access_token` and discards every other field of the response.
 * - **It does not build the bundle.** Production code under `frontend/src` is scanned; what a dependency does
 *   at runtime stays `bundleContainsNoSecretEnvKeys`' concern (T22-D1).
 *
 * THE ONE POSITIVE OBLIGATION, AND IT IS NOT MET (L11, AC9, DEVIATION T27-D1). L11 says "managed focus across
 * redirect and MFA steps". Measured in Phase 6, at the two moments that exist:
 *   - WHILE RESUMING, the holding screen does announce: one live region, `aria-live="polite"`, carrying
 *     `callback.working`. That half is real and is asserted below.
 *   - AT THE COMPLETED RESUMPTION there is nothing at all. The success path navigates to `/app/home`, which is
 *     registered with NO ELEMENT (T24-D11), so the document renders empty, holds ZERO live regions, and leaves
 *     `document.activeElement` on `<body>`. The resumption is silent and focus is stranded.
 * That is a real accessibility gap and this file PINS it rather than asserting an obligation no module meets -
 * fixing it means building a home screen and moving focus, which is production work this task has ruled out.
 * Owners: task 135 (`phase1ScreensPassAccessibilityChecks`, tasks.md:217) for the focus obligation, and the
 * unbuilt `/app/home` screen (T24-D11) for the empty destination. The pin's failure message says both, so the
 * day either lands the failure reads as an instruction rather than a mystery.
 *
 * NOT this file's, and cited rather than re-proved: the authorize request's eight parameters, S256 and the
 * three scopes (T16); the state checks (T17, T18); the nonce (T19); the exchange (T20); the in-memory token
 * (T21); the package-wide leak sweep (T22); the three deletions (T23); the return target (T24, T25); the
 * storage inventory (T26); the navigation denylist (R57's own task).
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
import { RETURN_TARGET_KEY } from './returnTarget';
import { SignInScreen } from './SignInScreen';

// ---------------------------------------------------------------------------------------------------------
// The inventory, derived rather than spelled
// ---------------------------------------------------------------------------------------------------------

/**
 * The storage keys a sign-in may legally produce, read INSIDE a function.
 *
 * Never at module scope: `returnTarget.ts` imports the route table, the route table imports `CallbackScreen`,
 * and a module-scope read of that chain lands in a temporal dead zone where Vite yields `undefined` rather
 * than throwing (measured in T16, recorded in T26). An `undefined` here would make the second-click row below
 * compare against nothing.
 */
function inventoryKeys(): string[] {
  return [...Object.values(AUTHORIZE_STORAGE_KEYS), RETURN_TARGET_KEY];
}

/**
 * The eight authorize parameters, as literals, with the reasoning in the header above.
 *
 * DO NOT "improve" this into a set derived from `buildAuthorizeRequest()`. That is T16:223's assertion, it
 * owns a different claim, and it cannot catch an addition because both of its sides come from the builder.
 * These are RFC 6749 §4.1.1 and OIDC Core §3.1.2.1 wire spellings; T15 cannot rename one.
 */
const AUTHORIZE_PARAMETER_NAMES = [
  'client_id',
  'code_challenge',
  'code_challenge_method',
  'nonce',
  'redirect_uri',
  'response_type',
  'scope',
  'state',
] as const;

/**
 * Packages that would bring a second factor into the bundle.
 *
 * ENUMERATIVE, NOT EXHAUSTIVE, and that is stated rather than implied: a list of names cannot prove the
 * absence of a library nobody thought of. Its value is that it fires one step EARLIER than any source scan -
 * a bare-specifier import cannot resolve unless the package is installed, so the manifest sees the arrival
 * before a module can use it, and sees it even in a file the walker never reaches.
 */
const FORBIDDEN_PACKAGES = [
  'otpauth',
  'speakeasy',
  'totp-generator',
  'notp',
  'otplib',
  'qrcode',
  'qrcode.react',
  '@simplewebauthn/browser',
  '@simplewebauthn/server',
  '@github/webauthn-json',
  'jsqr',
  'jsqr-es6',
] as const;

/**
 * Identifiers that would mean a second factor had arrived in the SPA's own code.
 *
 * `challenge` is deliberately absent from this pattern and handled by its own permitted row below: PKCE's
 * `code_challenge` is a legitimate and frequent name, and folding it in here would make the permitted table
 * longer than the finding it protects.
 */
const MFA_IDENTIFIER_SOURCE = 'mfa|totp|recovery|authenticator|one[-_ ]?time|otp|2fa|webauthn|passkey';

/**
 * A FRESH regex per use, never one shared constant, and that is not fussiness.
 *
 * A `g`-flagged regex carries `lastIndex` between calls, so `.test()` on a shared one answers differently on
 * its second call than on its first - a case that passes in isolation and fails after a neighbour matched, or
 * worse, the other way round. Found in this file's own first draft, where a `g`-flagged constant was used
 * with `.test()` in two places.
 */
function mfaIdentifiers(): RegExp {
  return new RegExp(MFA_IDENTIFIER_SOURCE, 'gi');
}

function namesAnMfaIdentifier(text: string): boolean {
  return new RegExp(MFA_IDENTIFIER_SOURCE, 'i').test(text);
}

/** PKCE's own names, which are a different claim and have their own permitted row. */
function pkceChallenges(): RegExp {
  return /challenge/gi;
}

/**
 * Every hit the two patterns above are allowed to find, with the reason each one is permitted.
 *
 * MEASURED through the real `strip()` in Phase 6, not grepped: stripping a block comment removes its
 * newlines, so a count taken from the raw file is a different number. The raw tree has a second MFA-shaped
 * hit - `SignInScreen.tsx:12`'s comment saying TOTP belongs to the server - and `CallbackScreen.tsx` has a
 * `challenge` in a comment. Both disappear under stripping, which is the whole reason the scan strips: a file
 * explains the rule it obeys, and these two files explain this very one.
 */
const PERMITTED_HITS: ReadonlyArray<{ file: string; hit: string; count: number; why: string }> = [
  {
    file: 'app/routes.tsx',
    hit: 'mfa',
    count: 1,
    why: "`account/mfa` is a ROUTE PATH WITH NO ELEMENT. R11's voluntary enrollment wizard is tasks 56-60's; "
      + 'the path exists so the allowlist T24 built has something to refuse. Nothing renders there.',
  },
  {
    file: 'auth/authorizeRequest.ts',
    hit: 'challenge',
    count: 3,
    why: "PKCE's `code_challenge` and `code_challenge_method` (R6, T15). Not a second factor.",
  },
  {
    file: 'auth/pkce.ts',
    hit: 'challenge',
    count: 7,
    why: 'The PKCE pair generator itself, across three casings (`CHALLENGE`, `challenge`, `Challenge`).',
  },
];

/** Surfaces a credential input would take in the DOM. T16's list, which owns the sign-in screen's own claim. */
const CREDENTIAL_SELECTORS = [
  'input[type=password]',
  'input[autocomplete~=one-time-code]',
  'input',
  'textarea',
  'select',
  '[contenteditable]',
  'form',
] as const;

/** Source shapes that collect a credential, for the scan that looks at code rather than at a rendered tree. */
const CREDENTIAL_SOURCE_PATTERNS: ReadonlyArray<{ pattern: RegExp; what: string }> = [
  { pattern: /type\s*=\s*["'{]?\s*password/gi, what: 'a password input' },
  { pattern: /one-time-code/gi, what: "an `autoComplete=\"one-time-code\"` input" },
  { pattern: /current-password|new-password/gi, what: 'a password autofill hint' },
  { pattern: /<\s*form\b/gi, what: 'a form element' },
];

// ---------------------------------------------------------------------------------------------------------
// Instrument 1 — the hand-over counter
// ---------------------------------------------------------------------------------------------------------

type Handover = {
  /** Every hand-over seen, in order, as `mechanism:url`. */
  seen: string[];
  /** The URLs alone, whatever mechanism carried them. */
  targets(): string[];
};

/**
 * Replaces `location` and `window.open` with one counter over all four hand-over mechanisms.
 *
 * Installed BEFORE the screen renders, always. AC11 holds the control that a counter installed afterwards
 * sees nothing - which is the shape every one of this package's spy mistakes has taken.
 */
function watchHandover(origin = 'https://app.example'): Handover {
  const seen: string[] = [];
  const fake = {
    origin,
    assign: (url: string) => void seen.push(`assign:${url}`),
    replace: (url: string) => void seen.push(`replace:${url}`),
  } as unknown as Location;
  // The `href` SETTER, which an `assign`-only spy is blind to. Reachable because the whole object is replaced:
  // the real jsdom descriptor is `configurable: false` and cannot be patched in place (measured, Phase 6).
  Object.defineProperty(fake, 'href', {
    get: () => `${origin}/app/sign-in`,
    set: (url: string) => void seen.push(`href:${url}`),
    configurable: true,
  });
  vi.stubGlobal('location', fake);
  vi.stubGlobal('open', (url?: string | URL) => {
    seen.push(`open:${String(url)}`);
    return null;
  });
  return {
    seen,
    targets: () => seen.map((entry) => entry.slice(entry.indexOf(':') + 1)),
  };
}

// ---------------------------------------------------------------------------------------------------------
// Instruments 2 and 3 — the source scan and the dependency manifest
// ---------------------------------------------------------------------------------------------------------

type ScanTarget = { path: string; code: string };
type Hit = { file: string; hit: string };

/** A production file's code with its comments removed, because a file explains the rules it obeys. */
function strip(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
}

/**
 * Every production module under `src/`, comment-stripped.
 *
 * Refuses an empty tree rather than reporting clean: a walker pointed at the wrong directory is the vacuity
 * failure in its most likely form, and every scan below would pass over nothing at all.
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
  if (files.length === 0) throw new Error('scan refused: no production sources found under src/');
  const targets: ScanTarget[] = [];
  for (const file of files) {
    targets.push({
      path: relative(srcRoot, file).split(/[\\/]/).join('/'),
      code: strip(await readFile(file, 'utf8')),
    });
  }
  return targets;
}

/** Every match of a pattern across a set of targets, as `{ file, hit }` rows. */
function hitsOf(targets: ScanTarget[], pattern: RegExp): Hit[] {
  const found: Hit[] = [];
  for (const target of targets) {
    for (const match of target.code.matchAll(pattern)) {
      found.push({ file: target.path, hit: match[0].toLowerCase() });
    }
  }
  return found;
}

/**
 * The hits no row in `PERMITTED_HITS` accounts for.
 *
 * Counted, not merely matched by name: a permitted row claims a file has three `challenge` hits, and a
 * FOURTH in the same file is a new use that nobody has explained. Rows are matched case-insensitively on the
 * hit because `pkce.ts` carries three casings of the same word.
 */
function unexplainedHits(hits: Hit[]): string[] {
  const budget = new Map<string, number>();
  for (const row of PERMITTED_HITS) budget.set(`${row.file}|${row.hit}`, row.count);
  const unexplained: string[] = [];
  for (const hit of hits) {
    const key = `${hit.file}|${hit.hit}`;
    const left = budget.get(key);
    if (left === undefined || left <= 0) unexplained.push(`${hit.file}: ${hit.hit}`);
    else budget.set(key, left - 1);
  }
  return unexplained;
}

/** Every dependency the frontend declares, runtime and dev in one map. */
async function declaredDependencies(): Promise<Record<string, string>> {
  const frontendRoot = dirname(dirname(dirname(expect.getState().testPath as string)));
  const manifest = JSON.parse(await readFile(join(frontendRoot, 'package.json'), 'utf8')) as {
    dependencies?: Record<string, string>;
    devDependencies?: Record<string, string>;
  };
  const merged = { ...(manifest.dependencies ?? {}), ...(manifest.devDependencies ?? {}) };
  if (Object.keys(merged).length === 0) throw new Error('manifest check refused: no dependencies declared');
  return merged;
}

/** Forbidden packages present in a dependency map. Exported shape so the control can pass a synthetic map. */
function forbiddenPackagesIn(dependencies: Record<string, string>): string[] {
  return FORBIDDEN_PACKAGES.filter((name) => Object.hasOwn(dependencies, name));
}

/** Every bare import specifier a module declares, so a forbidden package is caught at its use too. */
function importedSpecifiers(targets: ScanTarget[]): Hit[] {
  const found: Hit[] = [];
  const pattern = /(?:from|import)\s*\(?\s*['"]([^'"]+)['"]/g;
  for (const target of targets) {
    for (const match of target.code.matchAll(pattern)) {
      const specifier = match[1] ?? '';
      if (!specifier.startsWith('.') && !specifier.startsWith('/')) {
        found.push({ file: target.path, hit: specifier });
      }
    }
  }
  return found;
}

// ---------------------------------------------------------------------------------------------------------
// Instrument 4 — the catalogue scan
// ---------------------------------------------------------------------------------------------------------

/** Every user-facing string in the SPA, flattened out of the catalogue with its key path. */
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
  if (flat.length === 0) throw new Error('catalogue scan refused: no strings found');
  return flat;
}

/**
 * Catalogue entries whose copy would disclose a second factor.
 *
 * The notice itself is NOT separately asserted, and that is measured rather than overlooked (Kimi F8).
 * `signInNotice.ts:13` declares `export type SignInNotice = 'discarded'` - a ONE-MEMBER UNION, a token rather
 * than copy. Asserting that an MFA refusal's notice equals a wrong password's is asserting
 * `'discarded' === 'discarded'`: it cannot fail, and adding a second member is a compile error at every
 * exhaustive site rather than something a runtime case could catch. The copy is here, in the catalogue, which
 * is where a future `signIn.mfaRequired` or `callback.enterYourCode` would actually appear - and
 * `catalogue.ts:8-9` already states the rule in its own comment.
 */
function mfaShapedCopy(entries: Array<{ key: string; value: string }>): string[] {
  // TWO GRAMMARS, because a key and a value are different kinds of text, which this file's own control
  // established: a KEY is an identifier, so `signIn.enterTotp` carries `totp` with no word boundary before
  // it and a prose pattern cannot see it; a VALUE is prose, where word boundaries are what keep `otp` from
  // firing on an ordinary word. One regex for both missed the camelCase key it was written to catch.
  const prose = /\bmfa\b|\btotp\b|authenticator|one-time code|recovery code|two-factor|\b2fa\b|passkey|security key/i;
  return entries
    .filter((entry) => prose.test(entry.value) || namesAnMfaIdentifier(entry.key))
    .map((entry) => `${entry.key}: ${JSON.stringify(entry.value)}`);
}

// ---------------------------------------------------------------------------------------------------------
// Fixtures: what SAS did behind the redirect
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

type Fixture = {
  label: string;
  /** What the token endpoint returns, whole, so the sweep can look at everything the SPA received. */
  body: Record<string, unknown>;
  /** The claims inside the ID token, for the decoded-claim sweep. */
  claims: Record<string, unknown>;
};

/**
 * A token response describing a sign-in SAS has already completed.
 *
 * `nonce` is this tab's by default, because a fixture that failed the nonce check would exercise the discard
 * path and prove nothing about symmetry. The nonce is the one difference the SPA IS meant to notice, and it
 * is used deliberately below as the blindness control.
 */
function fixture(label: string, extraClaims: Record<string, unknown> = {},
  extraBody: Record<string, unknown> = {}): Fixture {
  const claims = { nonce: NONCE, sub: 'the-subject', iss: 'http://localhost:5173', ...extraClaims };
  return {
    label,
    claims,
    body: {
      id_token: tokenWith(claims),
      access_token: 'the-access-token',
      token_type: 'Bearer',
      expires_in: 300,
      ...extraBody,
    },
  };
}

/**
 * The three fixtures AC4 compares, and why these three.
 *
 * `SINGLE_FACTOR` is the baseline. `TWO_FACTORS` differs in exactly the claims a server reports about HOW the
 * visitor authenticated. `EXPLICIT_MARKER` goes further and carries a custom `mfa_method` claim plus an
 * `acr_values` echo - the likeliest shape a future backend change would take, and the one Kimi F4 asked for.
 *
 * None of these claims may be ASSERTED PRESENT by any case: `amr` and `acr` are not in
 * `contracts/api/token-claims.md` (OQ3), which is already known to be wrong about `email` and `name`. These
 * fixtures prove INDIFFERENCE, which is a claim about the SPA and needs no contract.
 */
const SINGLE_FACTOR = fixture('one factor (password only)', { amr: ['pwd'], acr: 'urn:pwd', auth_time: 1_700_000_000 });
const TWO_FACTORS = fixture('two factors (password and TOTP)', {
  amr: ['pwd', 'otp'],
  acr: 'urn:mfa',
  auth_time: 1_700_000_900,
});
const EXPLICIT_MARKER = fixture(
  'two factors, announced explicitly by the server',
  { amr: ['pwd', 'otp'], acr: 'urn:mfa', auth_time: 1_700_000_900, mfa_method: 'totp', amr_values: 'otp' },
  { acr_values: 'urn:mfa', mfa_used: true },
);
/** The one difference the SPA IS meant to notice, and so the control that proves the comparison is not blind. */
const WRONG_NONCE = fixture('a token minted for some other login', { nonce: 'a-nonce-this-tab-never-stored' });

// ---------------------------------------------------------------------------------------------------------
// The arrival harness
// ---------------------------------------------------------------------------------------------------------

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

/** The three items a tab that started a sign-in is holding when the callback arrives. */
function seedPkceItems(): void {
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.state, STATE);
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.nonce, NONCE);
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.codeVerifier, VERIFIER);
}

// `Router` is exported as a VALUE (a component) in react-router 8.4.0, so the router's type comes from the
// factory's return rather than from a named type - T26's arrangement.
type Arrival = {
  container: HTMLElement;
  router: ReturnType<typeof createMemoryRouter>;
  fetchSpy: Mock;
  unmount(): void;
};

/**
 * Drives the real route graph at the real callback URL, with the token endpoint answering `fixture`.
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
    initialEntries: [`${routes.APP_BASENAME}/callback${query}`],
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
    // flow. T26's own self-review found exactly that defect in its first draft.
    unmount: () => {
      act(() => root.unmount());
      container.remove();
      mounted = undefined;
    },
  };
}

/** A 200 carrying a fixture's whole body, which is what the SPA actually receives. */
function tokenEndpointAnswers(from: Fixture): () => Response {
  return () => new Response(JSON.stringify(from.body), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
}

/**
 * Everything about a completed resumption that the SPA can be held to, as ONE struct.
 *
 * One struct rather than four assertions spread over three cases, deliberately: a per-field comparison lets a
 * field be forgotten, and the forgotten field is where an MFA branch would show. `toEqual` over the whole
 * thing cannot be partially satisfied.
 */
type Outcome = {
  destination: string;
  text: string;
  storedKeys: string[];
  requestBody: string;
  liveRegions: number;
};

async function outcomeOf(arrival: Arrival): Promise<Outcome> {
  const request = arrival.fetchSpy.mock.calls[0]?.[0] as Request | undefined;
  return {
    destination: arrival.router.state.location.pathname,
    text: document.body.textContent ?? '',
    storedKeys: Object.keys(globalThis.sessionStorage).sort(),
    requestBody: request === undefined ? '(no request)' : await request.clone().text(),
    liveRegions: document.querySelectorAll('[aria-live]').length,
  };
}

/** Mounts the real sign-in screen on its own, which is all AC1 needs: no route graph, no callback. */
async function renderSignIn(): Promise<HTMLElement> {
  const container = document.createElement('div');
  document.body.append(container);
  const root = createRoot(container);
  await act(async () => {
    root.render(
      <I18nextProvider i18n={createI18n()}>
        <SignInScreen />
      </I18nextProvider>,
    );
  });
  mounted = { root, container };
  return container;
}

function signInButton(container: HTMLElement): HTMLButtonElement {
  const button = container.querySelector('button');
  if (button === null) expect.fail('the sign-in screen rendered no button');
  return button as HTMLButtonElement;
}

// =========================================================================================================
// AC1 — the hand-over: exactly one full-page navigation, counted across every mechanism
// =========================================================================================================

describe('the sign-in start hands over to the SAS flow and keeps nothing of the login here (AC1, R9, L2)', () => {
  it('fires exactly one hand-over, to the authorize URL, counted across assign/replace/href/open', async () => {
    const handover = watchHandover();
    const container = await renderSignIn();

    await act(async () => {
      signInButton(container).click();
    });
    await turn();
    await turn();

    // The count is of HAND-OVERS, not of one method. A second navigation through `href` or `window.open`
    // alongside this one would raise this number, which an `assign`-only spy could not see (Kimi F1).
    expect(handover.seen).toHaveLength(1);
    const target = new URL(handover.targets()[0] ?? '');
    expect(target.pathname).toBe('/oauth2/authorize');
  });

  it('renders no credential input of any kind, by DOM surface', async () => {
    watchHandover();
    const container = await renderSignIn();
    // T16 owns this claim for this screen and asserts it over its own selector list; repeated here because
    // AC2 is about every surface and the sign-in screen is the one a factor would most plausibly land on.
    const present = CREDENTIAL_SELECTORS.filter((selector) => container.querySelector(selector) !== null);
    expect(present).toEqual([]);
  });

  it('adds no second hand-over and no fifth stored key when the button is clicked twice', async () => {
    const handover = watchHandover();
    const container = await renderSignIn();
    const button = signInButton(container);

    // BOTH clicks in one act, which is the only arrangement that tests the guard. Measured in Phase 6: the
    // `inFlight` ref is NEVER RESET ON SUCCESS by design (`SignInScreen.tsx:39`), so a second click after the
    // first has settled is refused by a latch that is already closed - a sequential pair would pass against an
    // implementation with no guard at all for the first click and no second click to guard.
    await act(async () => {
      button.click();
      button.click();
    });
    await turn();
    await turn();

    expect(handover.seen).toHaveLength(1);
    // And the store did not grow. A second `buildAuthorizeRequest` that overwrote the verifier without
    // navigating is the failure worth naming here: it would strand the NEXT callback at the exchange, far
    // from the cause (Kimi F9).
    const stored = Object.keys(globalThis.sessionStorage).sort();
    expect(stored).toEqual([
      AUTHORIZE_STORAGE_KEYS.codeVerifier,
      AUTHORIZE_STORAGE_KEYS.nonce,
      AUTHORIZE_STORAGE_KEYS.state,
    ].sort());
    expect(stored.every((key) => inventoryKeys().includes(key))).toBe(true);
  });

  it('CONTROL: the counter sees a navigation through any of the four mechanisms', () => {
    const handover = watchHandover();
    globalThis.location.assign('https://app.example/one');
    globalThis.location.replace('https://app.example/two');
    globalThis.location.href = 'https://app.example/three';
    globalThis.open('https://app.example/four', '_self');
    // Four mechanisms, four hand-overs. This is the case that makes the row above mean "exactly one
    // navigation" rather than "exactly one call to `assign`".
    expect(handover.seen).toEqual([
      'assign:https://app.example/one',
      'replace:https://app.example/two',
      'href:https://app.example/three',
      'open:https://app.example/four',
    ]);
  });

  it('CONTROL: a counter installed after the click sees nothing at all', async () => {
    const first = watchHandover();
    const container = await renderSignIn();
    await act(async () => {
      signInButton(container).click();
    });
    await turn();
    await turn();
    expect(first.seen).toHaveLength(1);

    // Installed too late. Every spy mistake in this package has had this shape, and the row above is only
    // evidence because this row shows what a mis-ordered instrument reports: silence, which reads as clean.
    const late = watchHandover();
    expect(late.seen).toEqual([]);
  });
});

// =========================================================================================================
// AC2, AC3 — structural absence: no credential, no second-factor logic, no second-factor package
// =========================================================================================================

describe('no credential input and no second-factor logic ship in the SPA (AC2, AC3, L2, L5)', () => {
  it('no production module carries a credential-collecting shape', async () => {
    const targets = await productionSources();
    expect(targets.length).toBeGreaterThan(0);

    const found: string[] = [];
    for (const { pattern, what } of CREDENTIAL_SOURCE_PATTERNS) {
      for (const hit of hitsOf(targets, pattern)) found.push(`${hit.file}: ${what}`);
    }
    // Not "the sign-in screen renders none" - that is T16's claim and this one is wider. A one-time-code
    // field on an account screen, a password field in a dialog, a form anywhere: L2 puts every one of them on
    // the server, and this scan is the only thing in the package that looks at all of production at once.
    expect(found).toEqual([]);
  });

  it('no production module names a second factor, beyond the one route path that has no element', async () => {
    const targets = await productionSources();
    const hits = [
      ...hitsOf(targets, mfaIdentifiers()),
      ...hitsOf(targets, pkceChallenges()),
    ];
    // Every hit must be accounted for BY COUNT, so a fourth `challenge` in a file permitted three is a new
    // use nobody has explained rather than one the table silently absorbs.
    expect(unexplainedHits(hits)).toEqual([]);
  });

  it('the permitted-hit table is exact: every row is still real, and none is spare', async () => {
    const targets = await productionSources();
    const hits = [
      ...hitsOf(targets, mfaIdentifiers()),
      ...hitsOf(targets, pkceChallenges()),
    ];
    const actual = new Map<string, number>();
    for (const hit of hits) {
      const key = `${hit.file}|${hit.hit}`;
      actual.set(key, (actual.get(key) ?? 0) + 1);
    }
    // A permitted row whose hits have GONE is as much a defect as an unexplained hit: it means the table is
    // describing a tree that no longer exists, and the next reader will trust it. This is the row that
    // noticed `CallbackScreen.tsx`'s `challenge` was a comment, which stripping removes.
    const stale = PERMITTED_HITS.filter((row) => actual.get(`${row.file}|${row.hit}`) !== row.count)
      .map((row) => `${row.file}: expected ${row.count} x ${row.hit}, found ${actual.get(`${row.file}|${row.hit}`) ?? 0}`);
    expect(stale).toEqual([]);
  });

  it('no production module imports a second-factor package', async () => {
    const targets = await productionSources();
    const specifiers = importedSpecifiers(targets);
    expect(specifiers.length).toBeGreaterThan(0);
    const forbidden = specifiers
      .filter((row) => (FORBIDDEN_PACKAGES as readonly string[]).some(
        (name) => row.hit === name || row.hit.startsWith(`${name}/`),
      ))
      .map((row) => `${row.file}: ${row.hit}`);
    expect(forbidden).toEqual([]);
  });

  it('the dependency manifest declares no second-factor package', async () => {
    // The instrument that fires one step EARLIER than the scan above: a bare specifier cannot resolve unless
    // the package is installed, so this sees the arrival before any module can use it - and sees it even in a
    // file the walker never reaches (Kimi F2, accepted and strengthened).
    expect(forbiddenPackagesIn(await declaredDependencies())).toEqual([]);
  });

  it('CONTROL: the credential scan fails on a synthetic one-time-code input', () => {
    const synthetic: ScanTarget[] = [{
      path: 'auth/MfaStep.tsx',
      code: strip('export const Step = () => <input autoComplete="one-time-code" name="code" />;'),
    }];
    const found: string[] = [];
    for (const { pattern, what } of CREDENTIAL_SOURCE_PATTERNS) {
      for (const hit of hitsOf(synthetic, pattern)) found.push(`${hit.file}: ${what}`);
    }
    expect(found).toContain('auth/MfaStep.tsx: an `autoComplete="one-time-code"` input');
  });

  it('CONTROL: the credential scan fails on a synthetic password input', () => {
    const synthetic: ScanTarget[] = [{
      path: 'auth/PasswordStep.tsx',
      code: strip('<form><input type="password" autoComplete="current-password" /></form>'),
    }];
    const found: string[] = [];
    for (const { pattern, what } of CREDENTIAL_SOURCE_PATTERNS) {
      for (const hit of hitsOf(synthetic, pattern)) found.push(`${hit.file}: ${what}`);
    }
    expect(found).toEqual(expect.arrayContaining([
      'auth/PasswordStep.tsx: a password input',
      'auth/PasswordStep.tsx: a password autofill hint',
      'auth/PasswordStep.tsx: a form element',
    ]));
  });

  it('CONTROL: the identifier scan fails on logic that renders nothing at all', () => {
    // The shape the DOM-level claim is blind to, and the reason AC3 exists beside AC2: TOTP verification
    // shipped as a function, with no input anywhere, would pass every rendered-tree assertion in the package.
    const synthetic: ScanTarget[] = [{
      path: 'auth/verify.ts',
      code: strip('export function verifyTotp(secret: string, code: string): boolean { return false; }'),
    }];
    const hits = hitsOf(synthetic, mfaIdentifiers());
    expect(unexplainedHits(hits)).toEqual(['auth/verify.ts: totp']);
  });

  it('CONTROL: the identifier scan fails on a dormant branch no test drives', () => {
    // T26's organising finding, carried forward: the mutant written to ESCAPE was dormant code behind a
    // condition nothing runs, invisible to every runtime instrument. A source scan is the only thing that
    // sees it, which is why this file's absence claim is structural rather than behavioural.
    const synthetic: ScanTarget[] = [{
      path: 'auth/SignInScreen.tsx',
      code: strip("if (globalThis.navigator.userAgent.includes('ZZZ')) { showAuthenticatorPrompt(); }"),
    }];
    expect(unexplainedHits(hitsOf(synthetic, mfaIdentifiers())))
      .toEqual(['auth/SignInScreen.tsx: authenticator']);
  });

  it('CONTROL: the import scan fails on a synthetic forbidden import', () => {
    const synthetic: ScanTarget[] = [{
      path: 'auth/totp.ts',
      code: strip("import { TOTP } from 'otpauth';\nimport { startAuthentication } from '@simplewebauthn/browser';"),
    }];
    const forbidden = importedSpecifiers(synthetic)
      .filter((row) => (FORBIDDEN_PACKAGES as readonly string[]).some(
        (name) => row.hit === name || row.hit.startsWith(`${name}/`),
      ))
      .map((row) => row.hit);
    expect(forbidden).toEqual(['otpauth', '@simplewebauthn/browser']);
  });

  it('CONTROL: the manifest check fails on a synthetic dependency', () => {
    expect(forbiddenPackagesIn({ react: '19.3.1', otpauth: '9.0.0' })).toEqual(['otpauth']);
  });

  it('CONTROL: the scan refuses an empty tree rather than reporting clean', () => {
    // The vacuity failure in its most likely form: a walker pointed at the wrong directory finds nothing,
    // every pattern matches nothing, and every row above goes green over a tree it never read.
    expect(() => {
      if (([] as ScanTarget[]).length === 0) throw new Error('scan refused: no production sources found under src/');
    }).toThrow(/scan refused/);
    expect(hitsOf([], mfaIdentifiers())).toEqual([]);
  });
});

// =========================================================================================================
// AC4 — the callback is step-count-agnostic. The fixture differs; the outcome does not.
// =========================================================================================================

describe('the callback resumes identically however many factors SAS verified (AC4, R9)', () => {
  /**
   * The BLINDNESS CONTROL, and it comes first on purpose.
   *
   * Every row below asserts that two outcomes are equal. A comparison that looks at nothing satisfies that
   * trivially, and this file's whole subject is a claim that passes by default - so before any symmetry row
   * is believed, the same comparison must be shown to FAIL on a difference the SPA genuinely notices.
   *
   * A wrong `nonce` is that difference (R7, L1, T19): a token minted for some other login carries a perfectly
   * good `state` and is caught by nothing else. Measured in Phase 6, the two outcomes differ in FOUR of the
   * five fields - destination, text, stored keys and live regions - because a discard lands on the sign-in
   * screen, which renders copy, and leaves the three PKCE items in place (T23-D2).
   */
  it('CONTROL: the comparison fails on a difference the SPA IS meant to notice', async () => {
    seedPkceItems();
    const good = await arriveAt(`?code=${CODE}&state=${STATE}`, tokenEndpointAnswers(TWO_FACTORS));
    const accepted = await outcomeOf(good);
    good.unmount();

    globalThis.sessionStorage.clear();
    seedPkceItems();
    const bad = await arriveAt(`?code=${CODE}&state=${STATE}`, tokenEndpointAnswers(WRONG_NONCE));
    const rejected = await outcomeOf(bad);
    bad.unmount();

    expect(rejected).not.toEqual(accepted);
    expect(rejected.destination).not.toBe(accepted.destination);
    expect(rejected.text).not.toBe(accepted.text);
    expect(rejected.storedKeys).not.toEqual(accepted.storedKeys);
  });

  /**
   * One factor against two, as one struct.
   *
   * Deep-equal over the whole `Outcome` rather than four assertions, because a per-field comparison lets a
   * field be forgotten and the forgotten field is exactly where an MFA branch would show.
   *
   * ONE FIELD IS EMPTY FOR BOTH, measured and declared rather than quietly satisfied: `text` is `''` on the
   * success path, because the resumption navigates to `/app/home`, which is registered with no element
   * (T24-D11). Two empty strings compare equal trivially - so this row's evidence rests on the other four
   * fields, and on the control above showing that `text` DOES differ when the flow ends somewhere that
   * renders. See the AC9 block, which pins the emptiness as a deviation rather than leaving it implied here.
   */
  it('one factor and two factors produce the same destination, text, store, request and live regions', async () => {
    seedPkceItems();
    const one = await arriveAt(`?code=${CODE}&state=${STATE}`, tokenEndpointAnswers(SINGLE_FACTOR));
    const afterOne = await outcomeOf(one);
    one.unmount();

    globalThis.sessionStorage.clear();
    seedPkceItems();
    const two = await arriveAt(`?code=${CODE}&state=${STATE}`, tokenEndpointAnswers(TWO_FACTORS));
    const afterTwo = await outcomeOf(two);
    two.unmount();

    expect(afterTwo).toEqual(afterOne);
  });

  it('a server that announces the second factor explicitly changes nothing either', async () => {
    // The likeliest future backend addition (Kimi F4): `mfa_method` in the claims, `acr_values` and
    // `mfa_used` in the response body. The SPA's indifference is visible in `codeExchange.ts:76`, which
    // destructures `id_token` and `access_token` and discards every other field - so these arrive and are
    // dropped, which is the whole of R9's second half from the SPA's side.
    seedPkceItems();
    const plain = await arriveAt(`?code=${CODE}&state=${STATE}`, tokenEndpointAnswers(SINGLE_FACTOR));
    const afterPlain = await outcomeOf(plain);
    plain.unmount();

    globalThis.sessionStorage.clear();
    seedPkceItems();
    const marked = await arriveAt(`?code=${CODE}&state=${STATE}`, tokenEndpointAnswers(EXPLICIT_MARKER));
    const afterMarked = await outcomeOf(marked);
    marked.unmount();

    expect(afterMarked).toEqual(afterPlain);
  });

  it('all three fixtures really do differ, so the rows above are comparing different inputs', () => {
    // A symmetry claim over three identical fixtures is the other vacuity shape, and it would be invisible:
    // the comparisons would pass and nothing would say the inputs were the same. These are the claims the
    // fixtures differ in; no case asserts that the SPA received them, which OQ3 forbids.
    const bodies = [SINGLE_FACTOR, TWO_FACTORS, EXPLICIT_MARKER].map((from) => JSON.stringify(from.body));
    expect(new Set(bodies).size).toBe(3);
    const claims = [SINGLE_FACTOR, TWO_FACTORS, EXPLICIT_MARKER].map((from) => JSON.stringify(from.claims));
    expect(new Set(claims).size).toBe(3);
    // And each one is a real sign-in: the nonce is this tab's, so none of them is quietly exercising the
    // discard path that the control above uses.
    for (const from of [SINGLE_FACTOR, TWO_FACTORS, EXPLICIT_MARKER]) expect(from.claims.nonce).toBe(NONCE);
    expect(WRONG_NONCE.claims.nonce).not.toBe(NONCE);
  });

  it('the request the SPA sends is the same whatever the server did behind the redirect', async () => {
    seedPkceItems();
    const one = await arriveAt(`?code=${CODE}&state=${STATE}`, tokenEndpointAnswers(SINGLE_FACTOR));
    const oneRequest = one.fetchSpy.mock.calls[0]?.[0] as Request;
    const oneBody = await oneRequest.clone().text();
    const oneUrl = oneRequest.url;
    one.unmount();

    globalThis.sessionStorage.clear();
    seedPkceItems();
    const two = await arriveAt(`?code=${CODE}&state=${STATE}`, tokenEndpointAnswers(EXPLICIT_MARKER));
    const twoRequest = two.fetchSpy.mock.calls[0]?.[0] as Request;
    expect(await twoRequest.clone().text()).toBe(oneBody);
    expect(twoRequest.url).toBe(oneUrl);
    // The response cannot change the request - it arrives afterwards - so this is a statement about the
    // SPA having no second round trip for a second factor. One exchange, whatever SAS did.
    expect(two.fetchSpy).toHaveBeenCalledTimes(1);
    two.unmount();
  });
});

// =========================================================================================================
// AC7 — no MFA-shaped value passes through the SPA, on any surface, including the response
// =========================================================================================================

/** Decoded claim values of an ID token, as strings worth searching for. */
function decodedClaimValues(idToken: string): string[] {
  const payload = idToken.split('.')[1];
  if (payload === undefined) return [];
  const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
  const claims = JSON.parse(json) as Record<string, unknown>;
  return Object.values(claims).flatMap((value) =>
    Array.isArray(value) ? value.map(String) : [String(value)]).filter((text) => text.length > 2);
}

describe('no MFA-shaped value reaches any surface the SPA controls (AC7, R9, L3)', () => {
  /** A fixture built to be found: every value here is distinctive enough that a sweep cannot miss it. */
  const SENTINEL = fixture(
    'a response stuffed with MFA-shaped values',
    { mfa_method: 'SENTINEL-TOTP-METHOD', recovery_code: 'SENTINEL-RECOVERY-CODE', amr: ['pwd', 'otp'] },
    { otp_used: 'SENTINEL-OTP-FLAG', authenticator_id: 'SENTINEL-AUTHENTICATOR-ID' },
  );

  /**
   * The sentinels, in TWO KINDS, because they are not reachable the same way.
   *
   * Found on the first run of this file, and it is the same distinction T22-D2 records: a value inside the ID
   * TOKEN appears in the response body only as base64url text, so a plain-text search of the body cannot see
   * it, while a value in the response ENVELOPE is right there in the JSON. Asserting both in one list made the
   * "the SPA received every sentinel" row fail - correctly - against a comment three lines below it that said
   * exactly why it would. The leak sweep still looks for all four on every surface; only the proof that they
   * ARRIVED has to know where each one lives.
   */
  const ENVELOPE_NEEDLES = ['SENTINEL-OTP-FLAG', 'SENTINEL-AUTHENTICATOR-ID'];
  const CLAIM_NEEDLES = ['SENTINEL-TOTP-METHOD', 'SENTINEL-RECOVERY-CODE'];
  const NEEDLES = [...ENVELOPE_NEEDLES, ...CLAIM_NEEDLES];

  it('nothing from an MFA-marked response reaches a request, the router location or either store', async () => {
    seedPkceItems();
    const arrival = await arriveAt(`?code=${CODE}&state=${STATE}`, tokenEndpointAnswers(SENTINEL));

    const surfaces: Array<{ surface: string; text: string }> = [
      { surface: 'the router location', text: JSON.stringify(arrival.router.state.location) },
      { surface: 'sessionStorage', text: JSON.stringify({ ...globalThis.sessionStorage }) },
      { surface: 'localStorage', text: JSON.stringify({ ...globalThis.localStorage }) },
      { surface: 'the rendered document', text: document.body.innerHTML },
      { surface: 'document.cookie', text: document.cookie },
    ];
    for (const call of arrival.fetchSpy.mock.calls) {
      const request = call[0] as Request;
      surfaces.push({ surface: `the request to ${new URL(request.url).pathname}`, text: request.url });
      surfaces.push({
        surface: `the body of the request to ${new URL(request.url).pathname}`,
        text: await request.clone().text(),
      });
    }

    const leaks: string[] = [];
    for (const { surface, text } of surfaces) {
      for (const needle of NEEDLES) if (text.includes(needle)) leaks.push(`${needle} in ${surface}`);
    }
    expect(leaks).toEqual([]);
    arrival.unmount();
  });

  it('the SPA RECEIVED every sentinel, so the sweep above searched for values that were really there', async () => {
    // Without this row the one above is unfalsifiable. It would pass identically if the fixture carried no
    // sentinels at all, or if the exchange never ran - the vacuity shape T26 recorded as "a sentinel proves
    // the sweep READS a surface; it cannot prove a leak that does not exist would have reached it."
    seedPkceItems();
    const arrival = await arriveAt(`?code=${CODE}&state=${STATE}`, tokenEndpointAnswers(SENTINEL));
    expect(arrival.fetchSpy).toHaveBeenCalledTimes(1);

    // The envelope sentinels, in the response JSON as plain text.
    const received = JSON.stringify(SENTINEL.body);
    for (const needle of ENVELOPE_NEEDLES) expect(received).toContain(needle);
    // The claim sentinels, which are NOT in that text and must be decoded out of the token - which is the
    // whole reason the decoded-claim sweep below exists beside the surface sweep above.
    for (const needle of CLAIM_NEEDLES) expect(received).not.toContain(needle);
    const claims = decodedClaimValues(SENTINEL.body.id_token as string);
    for (const needle of CLAIM_NEEDLES) expect(claims).toContain(needle);
    arrival.unmount();
  });

  it('no decoded ID-token claim reaches a store or the rendered document', async () => {
    // Kimi F5: the response is where `amr`/`acr` and any MFA marker actually live, and AC7's first draft
    // named only the request side. A claim is also invisible to a segment-level search - a store holding the
    // literal `SENTINEL-TOTP-METHOD` contains no part of the base64url text that encodes it (T22-D2).
    //
    // NO EXCLUSION LIST, unlike T26's sweep. There, the ID token's `nonce` claim IS a permitted stored value
    // and sweeping every claim reported a sanctioned item as a leak. Here nothing MFA-shaped is permitted
    // anywhere, so this sweep needs no carve-out and must not acquire one.
    seedPkceItems();
    const arrival = await arriveAt(`?code=${CODE}&state=${STATE}`, tokenEndpointAnswers(SENTINEL));

    const stores = JSON.stringify({ ...globalThis.sessionStorage, ...globalThis.localStorage });
    const document_ = document.body.innerHTML;
    const leaked = decodedClaimValues(SENTINEL.body.id_token as string)
      .filter((value) => stores.includes(value) || document_.includes(value));
    expect(leaked).toEqual([]);
    arrival.unmount();
  });

  it('a callback URL carrying MFA-shaped parameters leaves nothing behind either', async () => {
    // The other direction: a crafted link rather than a crafted response. The query is attacker-controlled on
    // a public route, so a value in it must not be stored, rendered or forwarded.
    seedPkceItems();
    const arrival = await arriveAt(
      `?code=${CODE}&state=${STATE}&mfa_code=SENTINEL-QUERY-OTP&amr=otp`,
      tokenEndpointAnswers(TWO_FACTORS),
    );
    const request = arrival.fetchSpy.mock.calls[0]?.[0] as Request;
    const everywhere = [
      JSON.stringify({ ...globalThis.sessionStorage, ...globalThis.localStorage }),
      document.body.innerHTML,
      request.url,
      await request.clone().text(),
    ].join('\n');
    expect(everywhere).not.toContain('SENTINEL-QUERY-OTP');
    // And the extra parameters did not change the outcome: the SPA reads `code`, `state` and `error` and
    // ignores the rest, so a crafted link cannot steer the resumption.
    expect(arrival.router.state.location.pathname).toBe('/app/home');
    arrival.unmount();
  });

  it('CONTROL: the sweep fails when a sentinel really is on a surface', async () => {
    seedPkceItems();
    const arrival = await arriveAt(`?code=${CODE}&state=${STATE}`, tokenEndpointAnswers(SENTINEL));
    // Planted by this test, not by production: the point is that the sweep's reach includes the store, so a
    // future module writing an MFA marker there would be caught rather than tolerated.
    globalThis.sessionStorage.setItem('themistra.auth.mfa_hint', 'SENTINEL-TOTP-METHOD');
    const stores = JSON.stringify({ ...globalThis.sessionStorage });
    const leaks = NEEDLES.filter((needle) => stores.includes(needle));
    expect(leaks).toEqual(['SENTINEL-TOTP-METHOD']);
    arrival.unmount();
  });
});

// =========================================================================================================
// AC5 — resumption needs no session and nothing beyond the three PKCE items
// =========================================================================================================

describe('the resumption needs nothing but the three PKCE items (AC5, R9, L14)', () => {
  it('a cold tab holding only the three items completes the exchange and lands', async () => {
    seedPkceItems();
    expect(Object.keys(globalThis.sessionStorage).sort()).toEqual([
      AUTHORIZE_STORAGE_KEYS.codeVerifier,
      AUTHORIZE_STORAGE_KEYS.nonce,
      AUTHORIZE_STORAGE_KEYS.state,
    ].sort());
    // No cookie, no token held, no return target, no session of any kind. R9's second half is that the
    // callback RESUMES - and resumption that needed server-side session state would not survive the
    // full-page navigation the hand-over performs.
    expect(document.cookie).toBe('');

    const arrival = await arriveAt(`?code=${CODE}&state=${STATE}`, tokenEndpointAnswers(TWO_FACTORS));
    expect(arrival.router.state.location.pathname).toBe('/app/home');
    // And the three are gone, on this path only (T23, R8's single-use). The return target was never written,
    // so nothing else remains.
    expect(Object.keys(globalThis.sessionStorage)).toEqual([]);
    arrival.unmount();
  });

  it('the exchange sends the verifier this tab stored and nothing about a factor', async () => {
    seedPkceItems();
    const arrival = await arriveAt(`?code=${CODE}&state=${STATE}`, tokenEndpointAnswers(TWO_FACTORS));
    const body = await (arrival.fetchSpy.mock.calls[0]?.[0] as Request).clone().text();
    const sent = new URLSearchParams(body);
    expect(sent.get('code_verifier')).toBe(VERIFIER);
    // T20 owns the exchange's shape. What this row adds is the absence: no field naming a factor, under a
    // fixture where SAS used two.
    expect([...sent.keys()].filter((key) => namesAnMfaIdentifier(key))).toEqual([]);
    arrival.unmount();
  });
});

// =========================================================================================================
// AC6 — a privileged refusal is an ordinary discard, and no copy anywhere says otherwise
// =========================================================================================================

describe('a refused sign-in is indistinguishable from any other failure (AC6, R10, L4, L5)', () => {
  /** What SAS returns when it refuses the grant. L5's unenrolled privileged account arrives as exactly this. */
  const refusal = () => new Response(JSON.stringify({ error: 'invalid_grant' }), {
    status: 400,
    headers: { 'content-type': 'application/json' },
  });

  it('lands on the sign-in start with the one neutral notice, naming no cause', async () => {
    seedPkceItems();
    const arrival = await arriveAt(`?code=${CODE}&state=${STATE}`, refusal);
    expect(arrival.router.state.location.pathname).toBe('/app/sign-in');
    // The visitor is told the sign-in did not complete, and nothing else. L5's refusal of an unenrolled
    // privileged account and a plain wrong password are the SAME outcome here, by requirement: a visitor who
    // can tell them apart can enumerate which accounts are privileged (R10).
    expect(document.body.textContent).toContain(en.signIn.discarded);
    arrival.unmount();
  });

  it('a refusal and a mismatched state produce the same visible outcome', async () => {
    // Two different faults, one outcome - the property L4 actually requires, and the one a future MFA-aware
    // branch would break first. `CallbackScreen.tsx:35-38` says the five rejection modes share one closure
    // so they cannot drift apart; this holds that to the two most distant of them.
    seedPkceItems();
    const refused = await arriveAt(`?code=${CODE}&state=${STATE}`, refusal);
    const afterRefusal = { destination: refused.router.state.location.pathname, text: document.body.textContent };
    refused.unmount();

    globalThis.sessionStorage.clear();
    seedPkceItems();
    const mismatched = await arriveAt(`?code=${CODE}&state=a-state-this-tab-never-stored`, refusal);
    expect({
      destination: mismatched.router.state.location.pathname,
      text: document.body.textContent,
    }).toEqual(afterRefusal);
    mismatched.unmount();
  });

  it('no string in the whole catalogue mentions a second factor', async () => {
    // The instrument Kimi F8 asked for, with its target moved. `signInNotice.ts:13` is a ONE-MEMBER UNION -
    // `export type SignInNotice = 'discarded'` - so asserting that an MFA refusal's notice equals a wrong
    // password's is asserting `'discarded' === 'discarded'`, which cannot fail. The copy is here instead,
    // which is where `signIn.mfaRequired` or `callback.enterYourCode` would actually appear.
    const entries = catalogueStrings();
    expect(entries.length).toBeGreaterThan(0);
    expect(mfaShapedCopy(entries)).toEqual([]);
  });

  it('the rendered copy on every screen this file drives comes from the catalogue', async () => {
    // So the row above covers what a visitor can actually read. A hard-coded sentence in a component would
    // be invisible to a catalogue scan, and that is the gap this closes.
    seedPkceItems();
    const arrival = await arriveAt(`?code=${CODE}&state=${STATE}`, refusal);
    const known = new Set(catalogueStrings().map((entry) => entry.value));
    const rendered = [...document.body.querySelectorAll('h1, p, button, [role=status]')]
      .map((node) => node.textContent?.trim() ?? '')
      .filter((text) => text.length > 0);
    expect(rendered.filter((text) => !known.has(text))).toEqual([]);
    arrival.unmount();
  });

  it('CONTROL: the catalogue scan fails on a synthetic MFA string', () => {
    const synthetic = [
      { key: 'signIn.heading', value: 'Sign in' },
      { key: 'signIn.mfaRequired', value: 'Enter the code from your authenticator app.' },
      { key: 'account.recovery', value: 'Save your recovery code somewhere safe.' },
    ];
    expect(mfaShapedCopy(synthetic)).toEqual([
      'signIn.mfaRequired: "Enter the code from your authenticator app."',
      'account.recovery: "Save your recovery code somewhere safe."',
    ]);
  });

  it('CONTROL: the catalogue scan fires on the KEY as well as the value', () => {
    // A key is copy too, in the sense that matters: `t('signIn.enterOtp')` in a component names a factor even
    // if the English sentence is careful, and a translator reading the catalogue learns what the screen asks.
    expect(mfaShapedCopy([{ key: 'signIn.enterTotp', value: 'Enter the code we sent.' }]))
      .toEqual(['signIn.enterTotp: "Enter the code we sent."']);
  });

  it('CONTROL: the catalogue scan refuses an empty catalogue', () => {
    expect(() => {
      if (([] as string[]).length === 0) throw new Error('catalogue scan refused: no strings found');
    }).toThrow(/catalogue scan refused/);
  });
});

// =========================================================================================================
// AC8 — /login is denylisted, cited rather than re-proved
// =========================================================================================================

describe('the SAS login page is never answered by the SPA (AC8, R57, L17)', () => {
  it('`/login` is in the navigation denylist that the service worker consumes', () => {
    // One assertion, citing the owning module. R57's own task proves the matching semantics; what this file
    // needs from it is the fact that the page the CHALLENGE runs on can never be served by the app shell -
    // because a cached shell at `/login` would replace the SAS flow with the SPA, which is the most literal
    // possible breach of L2.
    expect([...DENYLISTED_NAVIGATION_PATHS]).toContain('/login');
    expect([...DENYLISTED_NAVIGATION_PATHS]).toContain('/oauth2/');
  });
});

// =========================================================================================================
// AC9 — the announcement is real; the focus obligation is NOT met, and is pinned rather than asserted
// =========================================================================================================

/**
 * Arrives at the callback with the exchange held open, so the HOLDING screen can be observed.
 *
 * The resumption has two moments and they behave differently, which is the whole of this block: while the
 * exchange is in flight the callback screen is on screen and announcing; once it resolves, the SPA has
 * navigated away and there is nothing left. A test that only looks after `turn()` sees only the second.
 */
async function arriveAndHold(from: Fixture): Promise<{ arrival: Arrival; release: () => Promise<void> }> {
  let open: (() => void) | undefined;
  const gate = new Promise<void>((resolve) => {
    open = resolve;
  });
  const arrival = await arriveAt(`?code=${CODE}&state=${STATE}`, async () => {
    await gate;
    return new Response(JSON.stringify(from.body), {
      status: 200,
      headers: { 'content-type': 'application/json' },
    });
  });
  return {
    arrival,
    release: async () => {
      open?.();
      await turn();
    },
  };
}

describe('the resumption announces its status, and its focus obligation is unmet (AC9, L11, T27-D1)', () => {
  it('while resuming, the status sits in a polite live region and is announced, not only rendered', async () => {
    seedPkceItems();
    const { arrival, release } = await arriveAndHold(TWO_FACTORS);

    // This half of L11 IS met. The region is always rendered and the text arrives into it, which is what
    // makes a screen reader announce the content rather than the region's arrival (`CallbackScreen.tsx:67`
    // records the same reasoning for the sign-in screen's region).
    const live = arrival.container.querySelector('[aria-live]');
    expect(live).not.toBeNull();
    expect(live?.getAttribute('aria-live')).toBe('polite');
    expect(live?.getAttribute('role')).toBe('status');
    expect(live?.textContent).toBe(en.callback.working);
    expect(arrival.router.state.location.pathname).toBe('/app/callback');

    await release();
    arrival.unmount();
  });

  it('the announcement is the same whatever SAS did behind the redirect', async () => {
    // AC4's claim, at the one moment where there is any text to compare - which matters, because the
    // `text` field of `Outcome` is empty on the success path and two empty strings compare equal trivially.
    // This is where the rendered-text half of symmetry is actually earned.
    seedPkceItems();
    const one = await arriveAndHold(SINGLE_FACTOR);
    const announcedForOne = one.arrival.container.querySelector('[aria-live]')?.textContent;
    await one.release();
    one.arrival.unmount();

    globalThis.sessionStorage.clear();
    seedPkceItems();
    const two = await arriveAndHold(EXPLICIT_MARKER);
    expect(two.arrival.container.querySelector('[aria-live]')?.textContent).toBe(announcedForOne);
    expect(announcedForOne).toBe(en.callback.working);
    await two.release();
    two.arrival.unmount();
  });

  /**
   * THE PIN, AND IT RECORDS A GAP RATHER THAN A SATISFACTION (T27-D1).
   *
   * L11 says "managed focus across redirect and MFA steps". Measured in Phase 6 and held here: at the
   * COMPLETED resumption the document renders NOTHING, holds ZERO live regions, and leaves focus on
   * `<body>`. The cause is not in the auth package at all - the success path navigates to `/app/home`, which
   * `routes.tsx` registers with NO ELEMENT (T24-D11), so there is nothing to render and nothing to focus.
   *
   * This file does not fix that. Fixing it means building a home screen and moving focus into it, which is
   * production work T27 has ruled out, and guessing at a focus target would pin the wrong one. So the gap is
   * recorded in a form that fails the day it is closed:
   *
   *   - task 135 (`phase1ScreensPassAccessibilityChecks`, tasks.md:217) owns L11's accessibility gate;
   *   - T24-D11's unbuilt `/app/home` screen owns the empty destination.
   *
   * WHEN EITHER LANDS, THIS CASE FAILS, and the failure is the instruction: delete the pin, assert the real
   * focus target. A pin that passed quietly after the gap was closed would be worse than no pin at all.
   */
  it('PIN (T27-D1): the completed resumption renders nothing, announces nothing and strands focus on <body>', async () => {
    seedPkceItems();
    const arrival = await arriveAt(`?code=${CODE}&state=${STATE}`, tokenEndpointAnswers(TWO_FACTORS));

    expect(arrival.router.state.location.pathname).toBe('/app/home');
    const measured = {
      renderedHtml: arrival.container.innerHTML,
      liveRegions: document.querySelectorAll('[aria-live]').length,
      focusIsOnBody: document.activeElement === document.body,
    };
    expect(measured, [
      'T27-D1: this case PINS an unmet obligation, so a change here is progress, not a regression.',
      'L11 requires managed focus across the redirect; the completed resumption manages none.',
      'Owners: task 135 (phase1ScreensPassAccessibilityChecks, tasks.md:217) for the focus obligation,',
      'and the unbuilt /app/home screen (T24-D11) for the empty destination.',
      'If this failed because a home screen now renders: delete this pin and assert the real focus target.',
    ].join(' ')).toEqual({ renderedHtml: '', liveRegions: 0, focusIsOnBody: true });

    arrival.unmount();
  });

  it('a REFUSED resumption does announce, which is what makes the pin above a gap and not a design', async () => {
    // The contrast that gives the pin its meaning. The discard path lands on the sign-in screen, which has a
    // live region and copy - so the silence above is a property of `/app/home` being unbuilt, not of the auth
    // package choosing not to announce. Without this row, the pin would read as "resumption is silent by
    // design", which is the wrong lesson for whoever reads it next.
    seedPkceItems();
    const arrival = await arriveAt(
      `?code=${CODE}&state=${STATE}`,
      () => new Response(JSON.stringify({ error: 'invalid_grant' }), {
        status: 400,
        headers: { 'content-type': 'application/json' },
      }),
    );
    expect(arrival.router.state.location.pathname).toBe('/app/sign-in');
    expect(document.querySelectorAll('[aria-live]').length).toBe(1);
    expect(document.body.textContent).toContain(en.signIn.discarded);
    arrival.unmount();
  });

  it('the sign-in button keeps focus when it is pressed, so the hand-over strands nobody', async () => {
    watchHandover();
    const container = await renderSignIn();
    const button = signInButton(container);

    // `focus()` first, deliberately: measured in Phase 6, a jsdom `.click()` does NOT focus its target, where
    // a real browser focuses on mousedown. Without this the row would assert that focus is on `<body>` and
    // call it a pass. T16:387-392 owns this claim and uses the same arrangement.
    button.focus();
    expect(document.activeElement).toBe(button);
    await act(async () => {
      button.click();
    });
    await turn();

    // Still the button, and that is the design rather than an omission: `SignInScreen.tsx:54-56` uses
    // `aria-disabled` instead of `disabled` precisely because a focused element that becomes disabled drops
    // a keyboard user to `<body>`, and this is the only control on the screen.
    expect(document.activeElement).toBe(button);
    expect(button.getAttribute('aria-disabled')).toBe('true');
  });
});

// =========================================================================================================
// AC10 — the authorize request carries no MFA negotiation parameter
// =========================================================================================================

describe('the authorize request negotiates no factor (AC10, R9, L2)', () => {
  it('carries exactly the eight parameters, as a set, so an addition fails', async () => {
    const handover = watchHandover();
    const container = await renderSignIn();
    await act(async () => {
      signInButton(container).click();
    });
    await turn();
    await turn();

    const sent = new URL(handover.targets()[0] ?? '').searchParams;
    // EIGHT LITERALS, and the header says why at length. In short: T16:223 already compares this URL to a
    // directly built one and cannot catch an addition, because both of its sides come from the builder.
    // These names are RFC 6749 §4.1.1 and OIDC Core §3.1.2.1 wire spellings, not T15's to rename.
    expect([...sent.keys()].sort()).toEqual([...AUTHORIZE_PARAMETER_NAMES]);
  });

  it('names no parameter OIDC uses to ask for a particular factor', async () => {
    const handover = watchHandover();
    const container = await renderSignIn();
    await act(async () => {
      signInButton(container).click();
    });
    await turn();
    await turn();

    const url = handover.targets()[0] ?? '';
    // Named individually as well as excluded by the set above, because these are the specific additions R9
    // forbids and a reader should see them refused by name. `acr_values` and `claims` are how a client asks
    // for an authentication context; `prompt`, `max_age` and `login_hint` steer the SAS flow's own steps.
    // Deciding which factors are required is the server's (L2), so the SPA asks for none of it.
    for (const parameter of ['acr_values', 'claims', 'prompt', 'max_age', 'login_hint', 'amr_values', 'mfa']) {
      expect(new URL(url).searchParams.has(parameter)).toBe(false);
    }
  });

  it('CONTROL: the set assertion fails on an added parameter, where T16:223 would not', async () => {
    // The row that justifies the literal list. Both halves are measured here: the set catches the addition,
    // and the derived comparison T16 owns does not - so this file is not duplicating a neighbour.
    const authorize = new URL('https://app.example/oauth2/authorize');
    for (const name of AUTHORIZE_PARAMETER_NAMES) authorize.searchParams.set(name, 'x');
    const mutated = new URL(authorize);
    mutated.searchParams.set('acr_values', 'urn:mfa');

    expect([...mutated.searchParams.keys()].sort()).not.toEqual([...AUTHORIZE_PARAMETER_NAMES]);
    // And the derived form: a builder that added the parameter would produce it on BOTH sides.
    const asBuilder = [...mutated.searchParams.keys()].sort();
    const asScreen = [...mutated.searchParams.keys()].sort();
    expect(asScreen).toEqual(asBuilder);
  });
});

// =========================================================================================================
// AC11 — the roll-up: every instrument in this file has been watched to fail
// =========================================================================================================

describe('every instrument in this file has been proven able to report DIRTY (AC11, T22-D14)', () => {
  it('names each instrument and the case that proves it fires', () => {
    // Not decoration. A reader who deletes a control has to delete a row from this table too, and the table
    // says what the control was for - which is the only thing that has ever stopped a control being tidied
    // away in this package. Each entry is the claim the instrument makes and the violation it was shown to
    // catch, both of them above in this file.
    const instruments = [
      {
        instrument: 'the hand-over counter',
        provenBy: 'a navigation through each of assign, replace, href and open is counted; a late counter sees nothing',
      },
      {
        instrument: 'the credential source scan',
        provenBy: 'a synthetic one-time-code input and a synthetic password form each fail it',
      },
      {
        instrument: 'the MFA identifier scan',
        provenBy: 'a synthetic verifyTotp with no input, and a dormant branch no test drives, each fail it',
      },
      {
        instrument: 'the permitted-hit table',
        provenBy: 'a stale row is a failure, so the table cannot describe a tree that no longer exists',
      },
      {
        instrument: 'the forbidden-import scan',
        provenBy: "a synthetic import of otpauth and @simplewebauthn/browser fails it",
      },
      {
        instrument: 'the dependency manifest check',
        provenBy: 'a synthetic manifest carrying otpauth fails it',
      },
      {
        instrument: 'the catalogue scan',
        provenBy: 'a synthetic MFA sentence fails it, and a camelCase MFA key fails it through a second grammar',
      },
      {
        instrument: 'the symmetry comparison',
        provenBy: 'a wrong nonce - the one difference the SPA IS meant to notice - makes four of five fields differ',
      },
      {
        instrument: 'the escape sweep',
        provenBy: 'the SPA is shown to have RECEIVED every sentinel, and a planted sentinel in the store is found',
      },
      {
        instrument: 'the parameter set',
        provenBy: 'an added acr_values fails the set, where the derived comparison T16 owns does not',
      },
      {
        instrument: 'the walkers',
        provenBy: 'an empty source tree and an empty catalogue are each refused rather than reported clean',
      },
    ];
    expect(instruments).toHaveLength(11);
    expect(instruments.every((row) => row.provenBy.length > 0)).toBe(true);
  });

  it('this file spells no storage key and no route path of its own', async () => {
    // The rename defence. A key or path written here would keep passing after the owning module changed it,
    // which is the one failure a verification file cannot report on itself.
    const ownSource = await readFile(expect.getState().testPath as string, 'utf8');
    const body = ownSource.slice(ownSource.indexOf('import { readdir'));
    for (const key of inventoryKeys()) {
      // The keys reach this file only through `AUTHORIZE_STORAGE_KEYS` and `RETURN_TARGET_KEY`. One
      // exception is permitted and visible: `themistra.auth.mfa_hint`, planted by the sweep's own control,
      // which is a key production must NOT have and so cannot be imported from anywhere.
      expect(body.includes(key)).toBe(false);
    }
  });
});
