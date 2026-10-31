import { readdir, readFile } from 'node:fs/promises';
import { dirname, join, relative } from 'node:path';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { OIDC_PUBLIC_OPERATIONS, createOidcApi } from '../api/client';
import { createRateLimitNotice } from '../api/rateLimitNotice';
import type { RateLimitNoticeState } from '../api/rateLimitNotice';
import { createSessionBoundary } from '../api/sessionBoundary';
import type { SessionTokens } from '../api/sessionBoundary';
import { ReadProfileError, readProfile } from './readProfile';
import type { Profile } from './readProfile';

/**
 * R20: profile data is read from `/userinfo`, account status from `GET /accounts/me`, and **neither PII nor
 * verification state is ever read from the access token**.
 *
 * OWNERSHIP. This file is shared. **T30 owns the imports, the setup, every helper below, and every
 * `describe('T30: …')` block.** T31 adds `describe('T31: …')` blocks for the `GET /accounts/me` axis and may
 * touch a T30 block only to repair a compilation error caused by a renamed export (Kimi F6, frozen in Phase
 * 4). T32 owns `emailVerifiedClaimIsNotAccountState` and does not appear here at all.
 *
 * WHAT THE THIRD CLAUSE IS FOR, because it is the one an implementation can break invisibly. A token's claims
 * are whatever the issuer put there for its own purposes. `contracts/api/token-claims.md` documents the
 * access token's `email_verified` as reflecting *which scopes were authorized for one request* - a real token
 * asking only for `openid` carries `false` - and as hardcoded `false` on the API-key path. So reading
 * verification state from the token is not a cheaper route to the same answer; it is a different answer that
 * looks right. Everything in `T30: AC5` exists to make that unwritable rather than merely discouraged.
 *
 * WHAT IS MEASURED AND NOT ASSUMED. Three facts below came from the running platform on 2026-10-31
 * (`infra/local/bin/up`, `status --wait`, `dev-account USER`, then curl), not from reading SAS:
 *
 *   1. `GET /userinfo` with a real interactive token returns `200 {"sub":"<uuid>"}` and NOTHING else, while
 *      that token carries `scope: ["openid","profile","email"]`. The scopes are granted; nothing maps them.
 *      This is `spec/auth-service/design.md` O6 and it is why AC9 treats a `sub`-only body as a SUCCESS.
 *   2. With a malformed bearer: `401`, **empty body**, and the detail in a header -
 *      `WWW-Authenticate: Bearer error="invalid_token", error_description="An error occurred while attempting
 *      to decode the Jwt: Malformed token"`. AC8 covers headers because of this; a rule about bodies misses it.
 *   3. With NO bearer: `302 Found, Location: /login, Set-Cookie: JSESSIONID`. Followed as `fetch` follows by
 *      default, that becomes `200 OK, text/html`, 1103 bytes of the login page. There is no 401 anywhere in
 *      that sequence, so the boundary's renewal never runs, and a client would parse HTML as a profile. AC12
 *      is entirely about this.
 *
 * VACUITY IS THE RISK, as in every absence file in this package (T22-D14). Every instrument here is shown to
 * report DIRTY before it is trusted to report clean, and a scan that reads nothing is refused rather than
 * reported clean. `T30: AC10` is the roll-up.
 */

// ---------------------------------------------------------------------------------------------------------
// Source scanning
// ---------------------------------------------------------------------------------------------------------

type ScanTarget = { path: string; code: string };
type Hit = { file: string; hit: string };

const NO_SOURCES = 'scan refused: no production sources found under src/';

/**
 * Comments removed, string and template literals KEPT.
 *
 * Carried from T29, where the two-regex version it replaced was a real defect Kimi found by reading: deleting
 * from the first `//` to end of line turns `'https://id.example/help/locked-account'` into `'https:` and the
 * word leaves the source before any scan sees it. Here the same bug would eat the second half of any URL and,
 * worse, could swallow a claim name sitting in a literal.
 */
function strip(source: string): string {
  let out = '';
  let index = 0;
  while (index < source.length) {
    const char = source[index];
    const next = source[index + 1];
    if (char === '/' && next === '/') {
      while (index < source.length && source[index] !== '\n') index += 1;
      continue;
    }
    if (char === '/' && next === '*') {
      index += 2;
      while (index < source.length && !(source[index] === '*' && source[index + 1] === '/')) index += 1;
      index += 2;
      continue;
    }
    if (char === '"' || char === "'" || char === '`') {
      const quote = char;
      out += char;
      index += 1;
      while (index < source.length) {
        if (source[index] === '\\') {
          out += source[index] + (source[index + 1] ?? '');
          index += 2;
          continue;
        }
        if (source[index] === quote) {
          out += quote;
          index += 1;
          break;
        }
        out += source[index];
        index += 1;
      }
      continue;
    }
    out += char;
    index += 1;
  }
  return out;
}

function refuseEmpty<T>(items: T[], refusal: string): T[] {
  if (items.length === 0) throw new Error(refusal);
  return items;
}

function sourceRoot(): string {
  // `src/profile/<this>` -> `src`. Derived from the runner so moving this file cannot silently narrow the
  // scan to a directory that no longer exists.
  return dirname(dirname(expect.getState().testPath as string));
}

function rel(root: string, file: string): string {
  return relative(root, file).split(/[\\/]/).join('/');
}

/** One stanza of a YAML file, by its opening key and the key that follows it. */
function stanza(text: string, from: string, to: string): string {
  const start = text.indexOf(from);
  const end = text.indexOf(to, start + from.length);
  return text.slice(start, end === -1 ? undefined : end);
}

/** `contracts/api/oidc.yaml`, two levels up from `src`: `src` -> `frontend` -> the repository root. */
function oidcContract(): Promise<string> {
  return readFile(join(dirname(dirname(sourceRoot())), 'contracts/api/oidc.yaml'), 'utf8');
}

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

async function productionSources(): Promise<ScanTarget[]> {
  const srcRoot = sourceRoot();
  const targets: ScanTarget[] = [];
  for (const file of await productionFiles()) {
    targets.push({ path: rel(srcRoot, file), code: strip(await readFile(file, 'utf8')) });
  }
  return targets;
}

function hitsOf(targets: ScanTarget[], pattern: RegExp): Hit[] {
  const found: Hit[] = [];
  for (const target of targets) {
    for (const match of target.code.matchAll(pattern)) {
      found.push({ file: target.path, hit: match[0] });
    }
  }
  return found;
}

/** A hand-written target, for the synthetic violations AC10 requires. Never read from disk. */
function synthetic(path: string, code: string): ScanTarget {
  return { path, code: strip(code) };
}

// ---------------------------------------------------------------------------------------------------------
// AC5's two instruments — the heart of the file
// ---------------------------------------------------------------------------------------------------------

/**
 * The claims that may not be read off a token, word-bounded because they are identifiers in prose-free code.
 *
 * `sub` is on the list, which is Kimi's F4 accepted in substance. Its proposed carve-out - permitted for
 * "transport-bound signing or audience checks performed by the boundary" - was REFUSED, because measurement
 * showed it describes machinery that does not exist: `sessionBoundary.ts` passes the token through as an
 * opaque string and decodes nothing. Forbidding `sub` outright is both simpler and stronger than an exception
 * for a caller nobody has written.
 */
const FORBIDDEN_TOKEN_CLAIM =
  '\\bemail_verified\\b|\\bgiven_name\\b|\\bfamily_name\\b|\\bpreferred_username\\b'
  + '|\\bemail\\b|\\bname\\b|\\bpicture\\b|\\bsub\\b';

/**
 * Reading a claim off a JWT, as opposed to naming one.
 *
 * NARROW ON PURPOSE, and this is the hazard the plan called M2. The obvious pattern is the five decode forms -
 * `split`, `atob`, `TextDecoder`, `Uint8Array.from`, `JSON.parse` - but `split` alone hits ordinary string
 * work anywhere in the tree, and `JSON.parse` is how any JSON is read. A scan that reports those as token
 * decodes fails on commissioned code the first day somebody splits a path, which is exactly T29's Kimi I1
 * lesson: a pattern right about intent and wrong about breadth is not an instrument.
 *
 * So the tree-wide scan keys on what a JWT decode cannot be written without: `atob`, or indexing segment `1`
 * of a split, or `jwtDecode`-style helpers. The five-form list is applied to the ALLOWLISTED FILE's
 * accounting instead, where breadth is safe because the file is named.
 */
const TOKEN_DECODE = '\\batob\\b|\\bjwt[Dd]ecode\\b|\\bdecodeJwt\\b|split\\([\'"]\\.[\'"]\\)';

/** The five forms `idTokenNonce.ts` actually uses. Kimi's F1 row listed three; the code uses all five. */
const DECODE_FORMS = ['atob', 'TextDecoder', 'Uint8Array.from', 'JSON.parse', "split('.')"] as const;

/**
 * The one production module permitted to decode a token, with what it reads and why.
 *
 * AN UPPER AND A LOWER BOUND, as T29's timer allowlist is. A row for a decode since deleted leaves budget
 * behind, which is how an allowlist stops being evidence - so a case asserts the row count is exactly one and
 * that the named file still contains every form the row claims.
 *
 * MEASURED, not assumed: `grep` for `atob`, `JSON.parse`, `.sub` and `split('.')` over all production source
 * returns `auth/idTokenNonce.ts` and nothing else.
 */
const PERMITTED_TOKEN_READS: ReadonlyArray<{
  file: string;
  forms: readonly string[];
  claim: string;
  why: string;
}> = [
  {
    file: 'auth/idTokenNonce.ts',
    forms: DECODE_FORMS,
    claim: 'nonce',
    why: 'R7 nonce validation only. It reads the nonce and no PII and no verification state (T19).',
  },
];

function tokenDecodes(): RegExp {
  return new RegExp(TOKEN_DECODE, 'g');
}

function forbiddenClaims(): RegExp {
  return new RegExp(FORBIDDEN_TOKEN_CLAIM, 'g');
}

/** Hits in files no allowlist row names. */
function unpermitted(hits: Hit[]): Hit[] {
  const allowed = new Set(PERMITTED_TOKEN_READS.map((row) => row.file));
  return hits.filter((hit) => !allowed.has(hit.file));
}

// ---------------------------------------------------------------------------------------------------------
// The leak sweep
// ---------------------------------------------------------------------------------------------------------

type Surface = { name: string; text: string };

/**
 * Every form a value could survive in. Carried from T29, where the raw-to-raw comparison it replaced was a
 * genuine hole: `URLSearchParams` encodes a space as `+`, so a value that survived into a URL read CLEAN.
 */
function encodings(text: string): string[] {
  const forms = new Set([text]);
  try {
    forms.add(decodeURIComponent(text.replace(/\+/g, ' ')));
  } catch {
    /* a malformed escape must not stop the raw sweep */
  }
  return [...forms];
}

/**
 * `document.cookie` and `indexedDB` are NOT in this list, declared rather than quietly omitted - the same
 * declaration T29 makes at its own sweep, restated here because Kimi's phase 11 is right that inheriting a
 * surface list without inheriting its caveat reads as a complete sweep to the next maintainer, and a profile
 * is PII. jsdom gives an empty cookie jar and has no `indexedDB`, so sweeping either would report clean for
 * an environmental reason and be worse than not sweeping it. Recorded as T30-D7.
 */
function surfaces(): Surface[] {
  return [
    { name: 'body', text: document.body.innerHTML },
    { name: 'title', text: document.title },
    { name: 'head', text: document.head.innerHTML },
    { name: 'window.name', text: window.name },
    { name: 'url', text: `${globalThis.location.search}${globalThis.location.hash}` },
    { name: 'sessionStorage', text: JSON.stringify({ ...globalThis.sessionStorage }) },
    { name: 'localStorage', text: JSON.stringify({ ...globalThis.localStorage }) },
  ];
}

function leaks(found: Surface[], needles: string[]): string[] {
  const hits: string[] = [];
  for (const surface of found) {
    const forms = encodings(surface.text).map((form) => form.toLowerCase());
    for (const needle of needles) {
      if (forms.some((form) => form.includes(needle.toLowerCase()))) hits.push(`${surface.name}/${needle}`);
    }
  }
  return hits;
}

const CONSOLE_CHANNELS = ['log', 'info', 'warn', 'error', 'debug', 'trace'] as const;

type ConsoleChannel = (typeof CONSOLE_CHANNELS)[number];
type ConsoleWatch = { lines: string[]; channels: readonly ConsoleChannel[] };

/** Installed BEFORE the code under test runs, always. AC10 holds the control that proves it matters. */
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
// The request harness
// ---------------------------------------------------------------------------------------------------------

const TOKEN = 'the-access-token-this-tab-holds';

/**
 * A full profile, the shape O6 will produce once a user-info mapper exists. Every value is distinctive, so a
 * leak can be attributed to the claim it came from rather than merely detected.
 */
const FULL_PROFILE: Profile = {
  sub: '7b1f0a4e-2c3d-4e5f-8a9b-0c1d2e3f4a5b',
  email: 'glenda.fixture@example.test',
  email_verified: true,
  name: 'Glenda Fixture',
  given_name: 'Glenda',
  family_name: 'Fixture',
  preferred_username: 'glenda.fixture',
  picture: 'https://id.example/avatars/glenda-fixture.png',
};

/** What the LIVE server returns today, under O6. Not a degenerate case - the normal one (AC9). */
const SUB_ONLY: Profile = { sub: FULL_PROFILE.sub };

const PROFILE_VALUES = [
  FULL_PROFILE.email,
  FULL_PROFILE.name,
  FULL_PROFILE.given_name,
  FULL_PROFILE.family_name,
  FULL_PROFILE.preferred_username,
  FULL_PROFILE.picture,
  FULL_PROFILE.sub,
].filter((value): value is string => typeof value === 'string');

type Answer = {
  status?: number;
  body?: unknown;
  /** The body VERBATIM, for shapes `JSON.stringify` cannot express - a malformed document, most of all. */
  rawBody?: string;
  headers?: Record<string, string>;
  redirected?: boolean;
  /** `redirect: 'manual'` in a real browser: `type: 'opaqueredirect'`, `status: 0`, no body (Kimi phase 11). */
  opaque?: boolean;
};

type Call = { path: string; method: string; auth: string | null; redirect: RequestRedirect };

/** Browser-style relative URL resolution: Node's `Request` rejects a relative URL, a browser resolves it.
 * The idiom is `api/parallel401sTriggerExactlyOneRenewal.test.ts:30`, reused rather than reinvented. */
function installBrowserRequest(): void {
  const NodeRequest = globalThis.Request;
  vi.stubGlobal(
    'Request',
    class extends NodeRequest {
      constructor(input: RequestInfo | URL, init?: RequestInit) {
        super(typeof input === 'string' ? new URL(input, window.location.href) : input, init);
      }
    },
  );
}

type Harness = {
  calls: Call[];
  api: ReturnType<typeof createOidcApi>;
  tokens: SessionTokens;
  renewals: number;
  rateLimit: RateLimitNoticeState;
};

/**
 * One tab: a stubbed transport, a real session boundary, and the real `createOidcApi`.
 *
 * The boundary is REAL rather than stubbed, which is the only way AC4 means anything: the claim is that a 401
 * is handled by the boundary's single-flight renewal and not by the reader, and a stubbed boundary would make
 * that true by construction.
 */
function harness(
  answers: Answer[],
  options: { token?: string | null; throws?: unknown; tokenSequence?: Array<string | null> } = {},
): Harness {
  installBrowserRequest();
  const calls: Call[] = [];
  let index = 0;
  const fetchStub = vi.fn(async (request: Request) => {
    calls.push({
      path: new URL(request.url).pathname,
      method: request.method,
      auth: request.headers.get('Authorization'),
      redirect: request.redirect,
    });
    const answer = answers[Math.min(index, answers.length - 1)];
    index += 1;
    // Kimi's phase 8, I1: `openapi-fetch` resolves an HTTP error status but REJECTS when the transport
    // itself throws, and nothing was driving that path.
    if (options.throws !== undefined) throw options.throws;
    const status = answer.status ?? 200;
    const serialised = answer.rawBody ?? (answer.body === undefined ? null : JSON.stringify(answer.body));
    const response = new Response(serialised, {
      status: status >= 300 && status < 400 ? 200 : status,
      headers: { 'content-type': 'application/json', ...answer.headers },
    });
    // jsdom's Response cannot be constructed with a 3xx status and does not model redirect modes, so the
    // no-session shapes are presented the way a transport would hand them over. Phase 5's hazard M3: this is
    // the stub half of AC12, and the case says so rather than pretending a real redirect happened.
    if (status >= 300 && status < 400) {
      Object.defineProperty(response, 'status', { value: status, configurable: true });
    }
    if (answer.redirected) {
      Object.defineProperty(response, 'redirected', { value: true, configurable: true });
    }
    // What `redirect: 'manual'` actually produces in a browser, and what jsdom will not produce on its own:
    // an opaque response with no status to read and no body to parse. The reader has a clause for it; nothing
    // was driving that clause (Kimi phase 11).
    if (answer.opaque) {
      Object.defineProperty(response, 'type', { value: 'opaqueredirect', configurable: true });
      Object.defineProperty(response, 'status', { value: 0, configurable: true });
    }
    return response;
  });
  vi.stubGlobal('fetch', fetchStub);

  let current = options.token === undefined ? TOKEN : options.token;
  let renewals = 0;
  // Kimi's phase 8, I6: a token that changes BETWEEN the reader's check and the boundary's read.
  const sequence = options.tokenSequence ? [...options.tokenSequence] : null;
  const tokens: SessionTokens = {
    current: () => (sequence && sequence.length > 0 ? (sequence.shift() ?? null) : current),
    renew: async () => {
      renewals += 1;
      current = `${TOKEN}-renewed`;
      return current;
    },
  };
  const boundary = createSessionBoundary(tokens, () => undefined);
  const rateLimit = createRateLimitNotice();
  return {
    calls,
    // The real notice, wired as production would wire it, so I5's 429 claim is about the factory rather
    // than about a stub.
    api: createOidcApi(boundary, rateLimit),
    tokens,
    rateLimit,
    get renewals() {
      return renewals;
    },
  };
}

beforeEach(() => {
  document.body.innerHTML = '';
  document.title = '';
  window.name = '';
  globalThis.sessionStorage.clear();
  globalThis.localStorage.clear();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------------------------------------
// AC1 / AC2 — the contract declares it, and the generated client exposes it
// ---------------------------------------------------------------------------------------------------------

describe('T30: AC1/AC2 the contract declares /userinfo and the generated client exposes it', () => {
  it('declares GET /userinfo as AUTHENTICATED, unlike POST /oauth2/token', async () => {
    const contract = await oidcContract();
    // Not parsed as YAML: pyyaml-style parsing is unavailable here and the claim is about two specific
    // stanzas, so the file is read as text and the stanzas are isolated by their own path keys.
    const userinfo = stanza(contract, '  /userinfo:', 'components:');
    const token = stanza(contract, '  /oauth2/token:', '  /userinfo:');
    /**
     * Asserted LINE-WISE, and not as a substring, because the first version of this case failed on the
     * contract's own prose: the `/userinfo` description explains why it is NOT `security: []` and therefore
     * contains those characters. A substring search cannot tell a declaration from a sentence about one.
     */
    const lines = (text: string): string[] => text.split('\n').map((line) => line.trim());
    expect(lines(token)).toContain('security: []');
    expect(lines(userinfo)).not.toContain('security: []');
    /**
     * And a POSITIVE requirement, which is Kimi's phase 8 I4 and the half that actually carries meaning.
     *
     * The first version asserted only that `security: []` was ABSENT from `/userinfo`, and treated that as
     * "authenticated". In OpenAPI 3 it is not: an operation with no `security` inherits the global one, this
     * file declares no global one, and the result is no requirement at all - semantically identical to the
     * `security: []` it was being contrasted with. The difference existed only in the text. So the contract
     * now declares `bearerAuth`, `/userinfo` requires it, and this asserts the requirement rather than the
     * absence of its opposite.
     */
    expect(lines(userinfo)).toContain('- bearerAuth: []');
    expect(lines(contract)).toContain('securitySchemes:');
    expect(userinfo).toContain('operationId: getUserInfo');
  });

  it('requires only sub, so the live sub-only response is a valid one', async () => {
    const contract = await oidcContract();
    const schema = contract.slice(contract.indexOf('    UserInfoResponse:'));
    expect(schema).toContain('required: [sub]');
    // Every other claim is named and none is required. A schema demanding more would make the running
    // server's correct answer a validation failure (O6).
    for (const claim of ['email', 'email_verified', 'name', 'given_name', 'family_name', 'picture']) {
      expect(schema).toContain(`        ${claim}:`);
    }
  });

  it('documents the 302 and the 401, because neither is what a reader would assume', async () => {
    const contract = await oidcContract();
    const userinfo = stanza(contract, '  /userinfo:', 'components:');
    expect(userinfo).toContain("'302':");
    expect(userinfo).toContain("'401':");
    // Neither carries a schema: L4 forbids the visitor learning which check failed, and a generated client
    // that offers no typed way to read the detail is the enforcement rather than a rule to remember.
    expect(userinfo.slice(userinfo.indexOf("'302':"))).not.toContain('$ref');
  });

  it('exposes the operation on the generated client, typed', async () => {
    const { api } = harness([{ body: SUB_ONLY }]);
    // If the path were absent from the generated `paths`, this would not compile - which is the assertion.
    const { data } = await api.GET('/userinfo', {});
    expect(data).toEqual(SUB_ONLY);
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC3 — only through the generated client
// ---------------------------------------------------------------------------------------------------------

describe('T30: AC3 the reader reaches /userinfo only through the generated client', () => {
  it('names no transport of its own', async () => {
    const source = strip(await readFile(join(sourceRoot(), 'profile/readProfile.ts'), 'utf8'));
    for (const banned of ['fetch(', 'XMLHttpRequest', 'new Request(', 'EventSource', 'sendBeacon']) {
      expect(source, banned).not.toContain(banned);
    }
    /**
     * `/userinfo` DOES appear, and must: openapi-fetch takes the schema path as its first argument, and that
     * is precisely what makes the call typed and the operation checkable against the contract. My first
     * version of this case asserted the string was absent and was simply wrong about the library.
     *
     * So the claim is the SHAPE. The path occurs exactly once, as a quoted literal handed to the generated
     * client's `GET`, and never assembled - no concatenation onto it, no template literal, no `URL`
     * construction. That is the difference between naming an operation and building a request.
     */
    expect(source.match(/\/userinfo/g)).toHaveLength(1);
    expect(source).toContain("api.GET('/userinfo'");
    for (const built of ["'/userinfo' +", "+ '/userinfo", '`/userinfo', 'new URL(']) {
      expect(source, built).not.toContain(built);
    }
  });

  it('CONTROL: the L6 rule bans the IDENTIFIER, not a list of routes - so /userinfo needs no entry', async () => {
    /**
     * Kimi's F7, REJECTED on fact, recorded here so the finding is not re-opened.
     *
     * F7 reasoned that `lintRejectsHandWrittenBackendFetch` "scans production source for backend route
     * prefixes" and that `/userinfo` might be missing from its denylist, and recommended adding
     * `/.well-known/`, `/oauth2/` and `/userinfo` to a "SAS-surface prefix list". There is no such list.
     * `.oxlintrc.json` restricts the GLOBALS `fetch` and `XMLHttpRequest` and the same properties on seven
     * window objects. The URL is never examined, so a hand-written `fetch('/userinfo')` is already rejected -
     * as is a hand-written fetch to anything at all - and the recommendation would have added prefixes to a
     * list that does not exist.
     */
    const config = JSON.parse(
      await readFile(join(dirname(sourceRoot()), '.oxlintrc.json'), 'utf8'),
    ) as { rules: Record<string, unknown[]> };
    const globals = config.rules['no-restricted-globals'];
    const serialized = JSON.stringify(config.rules);
    expect(globals.some((entry) => JSON.stringify(entry).includes('"fetch"'))).toBe(true);
    expect(globals.some((entry) => JSON.stringify(entry).includes('"XMLHttpRequest"'))).toBe(true);
    // The decisive half: no route, prefix or path appears anywhere in the rule configuration.
    for (const route of ['/userinfo', '/oauth2', '/accounts', '/.well-known']) {
      expect(serialized, route).not.toContain(route);
    }
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC4 — the bearer, and whose job the 401 is
// ---------------------------------------------------------------------------------------------------------

describe('T30: AC4 the boundary supplies the bearer and owns the 401', () => {
  it('sends the tab in-memory token as a bearer', async () => {
    const tab = harness([{ body: FULL_PROFILE }]);
    await readProfile(tab.api, tab.tokens);
    expect(tab.calls).toHaveLength(1);
    expect(tab.calls[0].path).toBe('/userinfo');
    expect(tab.calls[0].auth).toBe(`Bearer ${TOKEN}`);
  });

  it('puts the access token itself on no surface and in no console channel', async () => {
    /**
     * Kimi's phase 11, and the reason it is T30's to answer rather than already covered.
     *
     * `auth/tokensAreNeverWrittenToAnyStorage.test.tsx` owns this claim for the sign-in and callback flows
     * and sweeps six console channels while doing it - but it never calls `readProfile`, because the profile
     * read did not exist when it was written. T30 introduces a new flow that holds the token and attaches it
     * to a request, so a regression logging the `Authorization` header here would be caught by nothing: the
     * AC6 sweep looks for PROFILE_VALUES, and the token is not one of them (L3).
     */
    const watch = watchConsole();
    const tab = harness([{ status: 401 }, { body: FULL_PROFILE }]);
    await readProfile(tab.api, tab.tokens);
    // The renewed token too: the boundary mints it, so it is a value this flow produced rather than received.
    expect(leaks(surfaces(), [TOKEN, `${TOKEN}-renewed`])).toEqual([]);
    expect(watch.lines).toEqual([]);
  });

  it('lets the BOUNDARY renew on 401 and re-send, which the reader never sees', async () => {
    // A real boundary, so the renewal is the real single-flight one (R21, L18).
    const tab = harness([{ status: 401 }, { body: FULL_PROFILE }]);
    const profile = await readProfile(tab.api, tab.tokens);
    expect(profile).toEqual(FULL_PROFILE);
    expect(tab.renewals).toBe(1);
    // Two calls: the 401 and the boundary's re-send, the second carrying the renewed token.
    expect(tab.calls).toHaveLength(2);
    expect(tab.calls[1].auth).toBe(`Bearer ${TOKEN}-renewed`);
  });

  it('surfaces a FINAL 401 as unauthorized, after the boundary has already tried', async () => {
    const tab = harness([{ status: 401 }, { status: 401 }]);
    await expect(readProfile(tab.api, tab.tokens)).rejects.toMatchObject({ failure: 'unauthorized' });
    expect(tab.renewals).toBe(1);
  });

  it('surfaces a 403 as unauthorized too, and renews for neither reason twice', async () => {
    /**
     * Added because MP21 SURVIVED the first mutation run: reporting a 403 as `unavailable` instead of
     * `unauthorized` changed nothing, because no case drove a 403 at all. The reader had always handled it -
     * `response.status === 401 || response.status === 403` - and nothing held it to that.
     *
     * The distinction is worth keeping rather than collapsing. A 401 means the credential was not accepted;
     * a 403 means it was, and the account may not have this. A caller that cannot tell them apart cannot
     * decide between re-authenticating and telling the visitor their account lacks something.
     */
    const tab = harness([{ status: 403 }, { status: 403 }]);
    await expect(readProfile(tab.api, tab.tokens)).rejects.toMatchObject({ failure: 'unauthorized' });
  });

  it('preserves R23 for the first authenticated OIDC call: a 429 shows the notice', async () => {
    /**
     * Kimi's phase 8 I5. `createOidcApi` mirrors `createAuthApi` down to registering the rate-limit notice
     * FIRST - openapi-fetch runs `onResponse` in reverse registration order, so the notice runs last and
     * sees the final response, including a 429 on the boundary's re-send after a renewal. That ordering is
     * load-bearing and nothing in this file was holding the new factory to it.
     */
    const tab = harness([{ status: 429 }, { status: 429 }]);
    expect(tab.rateLimit.isShown()).toBe(false);
    await expect(readProfile(tab.api, tab.tokens)).rejects.toBeInstanceOf(ReadProfileError);
    expect(tab.rateLimit.isShown()).toBe(true);
  });

  it('shows the notice for a 429 on the RE-SEND, which is what the registration order is for', async () => {
    /**
     * Added because MP27 SURVIVED the first run with the stronger claim unproven.
     *
     * MP27 swaps the registration order so the notice runs FIRST instead of last. With a 429 on the very
     * first call, both orders show the notice - nothing has intervened, so there is only one response to
     * see, and the case above cannot tell them apart. `client.ts` says exactly which case can: "a 429 on the
     * session boundary's re-send after a renewal".
     *
     * So: 401, then 429 on the re-send. openapi-fetch runs `onResponse` in reverse registration order, so a
     * notice registered first runs LAST and sees the final 429. Registered after the boundary it runs first,
     * sees only the original 401, and the visitor is rate-limited with no copy saying so.
     */
    const tab = harness([{ status: 401 }, { status: 429 }]);
    await expect(readProfile(tab.api, tab.tokens)).rejects.toBeInstanceOf(ReadProfileError);
    expect(tab.renewals).toBe(1);
    expect(tab.rateLimit.isShown()).toBe(true);
  });

  it('CONTROL: a 200 leaves the rate-limit notice alone, so the check is not always true', async () => {
    const tab = harness([{ body: FULL_PROFILE }]);
    await readProfile(tab.api, tab.tokens);
    expect(tab.rateLimit.isShown()).toBe(false);
  });

  it('holds exactly POST /oauth2/token public, and not /userinfo', () => {
    // The asymmetry this task introduced. `POST /oauth2/token` carries no bearer because the PKCE verifier is
    // the client's only proof and renewing on its 401 would be circular - renewal IS an exchange. `GET
    // /userinfo` is the first authenticated operation in that contract.
    expect([...OIDC_PUBLIC_OPERATIONS]).toEqual(['POST /oauth2/token']);
    expect(OIDC_PUBLIC_OPERATIONS.has('GET /userinfo')).toBe(false);
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC5 — R20's prohibition. The clause an implementation can break invisibly.
// ---------------------------------------------------------------------------------------------------------

describe('T30: AC5 no production module reads PII or verification state from a token', () => {
  it('decodes a token in exactly one place, and that place is allowlisted with a reason', async () => {
    const sources = await productionSources();
    expect(sources.length).toBeGreaterThan(20);
    const offenders = unpermitted(hitsOf(sources, tokenDecodes()));
    expect(offenders).toEqual([]);
  });

  it('reads no forbidden claim off a decoded token, anywhere', async () => {
    const sources = await productionSources();
    /**
     * Scoped to the files that DECODE, which is the whole reason the decode scan comes first.
     *
     * `email`, `name` and `sub` are ordinary words that appear in this package constantly - `auth.yaml` has
     * an `email` property, the catalogue has copy about email verification, `AccountResponse` carries both.
     * A tree-wide scan for the claim names would fail on every one of them and prove nothing. What R20
     * forbids is not naming a claim; it is reading one OFF A TOKEN. So the claim scan runs only where a token
     * is decoded, and the decode scan above is what bounds that set to one file.
     */
    const decoding = sources.filter((target) => hitsOf([target], tokenDecodes()).length > 0);
    expect(decoding.map((target) => target.path)).toEqual(['auth/idTokenNonce.ts']);
    const claims = hitsOf(decoding, forbiddenClaims());
    expect(claims).toEqual([]);
  });

  it('the allowlist is an upper AND a lower bound: one row, and its forms all still exist', async () => {
    /**
     * Both directions, because an allowlist that is only an upper bound stops being evidence. A row for a
     * decode since deleted leaves budget behind for a decode somebody adds later, and nothing would report
     * it - which is how T29's timer allowlist was built and why it is built the same way here.
     */
    expect(PERMITTED_TOKEN_READS).toHaveLength(1);
    const row = PERMITTED_TOKEN_READS[0];
    const code = strip(await readFile(join(sourceRoot(), row.file), 'utf8'));
    // Kimi's F1 row named three forms. The code uses five, and a row that under-claims leaves two decode
    // forms unaccounted for.
    for (const form of row.forms) expect(code, form).toContain(form);
    expect(row.forms).toHaveLength(5);
    // And it reads the claim it says it reads, and no other.
    expect(code).toContain(row.claim);
    expect(row.why).not.toBe('');
  });

  it('CONTROL: removing the row makes the scan report the file it protects', async () => {
    const sources = await productionSources();
    const hits = hitsOf(sources, tokenDecodes());
    /**
     * The two halves together are the evidence. The RAW hits must contain the allowlisted file - if they do
     * not, the scan is not seeing the one decode that certainly exists, and its clean verdict above means
     * nothing. And `unpermitted` must then remove exactly that file and leave nothing. An allowlist that
     * cannot be shown to be load-bearing is indistinguishable from a scan that matches nothing.
     */
    expect(hits.map((hit) => hit.file)).toContain('auth/idTokenNonce.ts');
    expect(hits.length).toBeGreaterThan(0);
    expect(unpermitted(hits)).toEqual([]);
  });

  it('CONTROL: the scan flags every shape of reading a claim off a token', () => {
    const offenders = [
      synthetic('profile/A.ts', 'const p = JSON.parse(atob(token.split(".")[1])); return p.email;'),
      synthetic('profile/B.ts', "const claims = jwtDecode(accessToken); return claims.email_verified;"),
      synthetic('profile/C.ts', "const claims = decodeJwt(accessToken); return claims.name;"),
      synthetic('profile/D.ts', "const payload = token.split('.')[1];"),
      // `sub` as a cheap substitute for calling /userinfo - Kimi's F4, the shape it warned about.
      synthetic('profile/E.ts', "const who = JSON.parse(atob(t.split('.')[1])).sub;"),
    ];
    const unflagged = offenders.filter((target) => hitsOf([target], tokenDecodes()).length === 0);
    expect(unflagged.map((target) => target.path)).toEqual([]);
    // And each one reads a forbidden claim, caught by the second instrument.
    const reading = offenders.filter((target) => hitsOf([target], forbiddenClaims()).length > 0);
    expect(reading).toHaveLength(4);
  });

  it('CONTROL: the decode scan stays SILENT on ordinary string and JSON work', () => {
    /**
     * The hazard Phase 5 called M2, and the reason the pattern is not the five-form list. `split` and
     * `JSON.parse` are how ordinary code works; a scan that reported them would fail on commissioned source
     * the first day somebody split a path, and would be deleted rather than fixed. T29's Kimi I1 is the same
     * lesson: a pattern right about intent and wrong about breadth is not an instrument.
     */
    const permitted = [
      synthetic('app/P.ts', "const parts = pathname.split('/');"),
      synthetic('app/Q.ts', "const config = JSON.parse(await file.text());"),
      synthetic('app/R.ts', "const [head] = label.split(' ');"),
      synthetic('app/S.ts', "const decoded = new TextDecoder().decode(bytes);"),
      synthetic('app/T.ts', 'const raw = Uint8Array.from(buffer);'),
    ];
    expect(hitsOf(permitted, tokenDecodes())).toEqual([]);
  });

  it('RECORDS the decode pattern BOUNDARY, and guards it with the dependency list', async () => {
    /**
     * Kimi's phase 8 I7, accepted in substance with its recommended form REFUSED.
     *
     * I7 asks for a synthetic case using an alternate decode helper - `jwtVerify`, `decode` - asserting the
     * scan FLAGS it. The scan does not flag it, so that assertion would simply fail; the recommendation is
     * self-contradictory unless the pattern is widened first, and widening it to bare `decode` would hit
     * `new TextDecoder().decode(...)` in commissioned code (Phase 6 §4 measured that class of failure).
     *
     * So the boundary is recorded as a NEGATIVE control - these are the shapes the scan does not see - and
     * guarded by something that actually fires: **no JWT-decoding library is a dependency.** That is the
     * event which would make the gap live, and it is checkable. The day `jose` or `jwt-decode` enters a
     * package.json, this case fails and the pattern has to be widened before anything else can proceed.
     */
    const unseen = [
      synthetic('profile/U.ts', "const claims = jwtVerify(token, key); return claims.email;"),
      synthetic('profile/V.ts', "const claims = decode(token); return claims.email;"),
      synthetic('profile/W.ts', "const payload = token.split('.').at(1);"),
      synthetic('profile/X.ts', "const raw = Buffer.from(token.split('.')[1], 'base64');"),
    ];
    // Stated as a fact about the instrument, not as an endorsement. Three of these four would be caught by
    // the forbidden-claim scan IF a decode had put them in scope; the decode scan is what fails to.
    expect(hitsOf(unseen.slice(0, 2), tokenDecodes())).toEqual([]);

    // `src` -> `frontend` -> the repository root, spelled out because the first version of this walked one
    // level too far and read a path outside the repository.
    const frontendDir = dirname(sourceRoot());
    const repoRoot = dirname(frontendDir);
    const manifests = [
      join(frontendDir, 'package.json'),
      join(repoRoot, 'libs/ts/api-client/package.json'),
    ];
    const JWT_LIBRARIES = ['jose', 'jwt-decode', 'jsonwebtoken', 'jws', 'jwa', 'fast-jwt', 'njwt'];
    for (const manifest of manifests) {
      const parsed = JSON.parse(await readFile(manifest, 'utf8')) as {
        dependencies?: Record<string, string>;
        devDependencies?: Record<string, string>;
      };
      const names = Object.keys({ ...parsed.dependencies, ...parsed.devDependencies });
      for (const library of JWT_LIBRARIES) {
        expect(names, `${manifest} / ${library}`).not.toContain(library);
      }
    }
  });

  it('CONTROL: a scan that reads nothing is refused, not reported clean', async () => {
    // The deepest vacuity: a walker pointed at an empty tree returns no hits, and no hits reads as a pass.
    expect(() => refuseEmpty([], NO_SOURCES)).toThrow(NO_SOURCES);
    // And the real walker is wired to that guard rather than merely having one available.
    const sources = await productionSources();
    expect(sources.length).toBeGreaterThan(0);
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC6 / AC7 — nothing survives, nothing is logged
// ---------------------------------------------------------------------------------------------------------

describe('T30: AC6/AC7 the profile never persists and is never logged', () => {
  it('leaves no claim on any surface after a successful read', async () => {
    const watch = watchConsole();
    const tab = harness([{ body: FULL_PROFILE }]);
    const profile = await readProfile(tab.api, tab.tokens);
    // The read succeeded with the full shape, so the values were genuinely in play - without this the sweep
    // below would be clean because nothing ever had the data.
    expect(profile).toEqual(FULL_PROFILE);
    expect(leaks(surfaces(), PROFILE_VALUES)).toEqual([]);
    expect(watch.lines).toEqual([]);
  });

  it('CONTROL: every swept surface is actually read, proven with a sentinel on each', () => {
    const sentinel = FULL_PROFILE.email as string;
    const planted: Array<[string, () => void]> = [
      ['body', () => (document.body.innerHTML = `<p>${sentinel}</p>`)],
      ['title', () => (document.title = sentinel)],
      ['head', () => document.head.insertAdjacentHTML('beforeend', `<meta name="x" content="${sentinel}">`)],
      ['window.name', () => (window.name = sentinel)],
      ['sessionStorage', () => globalThis.sessionStorage.setItem('x', sentinel)],
      ['localStorage', () => globalThis.localStorage.setItem('x', sentinel)],
    ];
    for (const [name, plant] of planted) {
      document.body.innerHTML = '';
      document.title = '';
      document.head.innerHTML = '';
      window.name = '';
      globalThis.sessionStorage.clear();
      globalThis.localStorage.clear();
      plant();
      // A surface added to a sweep without a sentinel is a surface nobody has proved is read. T28 found that
      // the hard way: a mutant writing into `document.title` survived its whole suite until the surface was
      // both widened AND sentinelled.
      expect(leaks(surfaces(), [sentinel]), name).not.toEqual([]);
    }
  });

  it('CONTROL: a claim surviving PERCENT-ENCODED in a URL is caught too', () => {
    // T29's find, carried here. `URLSearchParams` turns a space into `+`, so a raw-to-raw comparison reads
    // CLEAN on a value that survived into a URL - and a URL is the likeliest place for one to survive.
    const encoded = new URLSearchParams({ who: FULL_PROFILE.name as string }).toString();
    expect(encoded).toContain('Glenda+Fixture');
    expect(encoded).not.toContain('Glenda Fixture');
    expect(leaks([{ name: 'url', text: `?${encoded}` }], [FULL_PROFILE.name as string])).toEqual([
      'url/Glenda Fixture',
    ]);
  });

  it('CONTROL: all six console channels are watched, not only the ones a flow happens to use', () => {
    const watch = watchConsole();
    expect(watch.channels).toEqual(['log', 'info', 'warn', 'error', 'debug', 'trace']);
    for (const channel of CONSOLE_CHANNELS) console[channel](`${channel}-sentinel`);
    for (const channel of CONSOLE_CHANNELS) {
      expect(watch.lines.join('\n'), channel).toContain(`${channel}: ${channel}-sentinel`);
    }
  });

  it('writes nothing to either store, even on the failure paths', async () => {
    for (const answer of [{ status: 401 }, { status: 500 }, { status: 302 }]) {
      const tab = harness([answer, answer]);
      await expect(readProfile(tab.api, tab.tokens)).rejects.toBeInstanceOf(ReadProfileError);
      expect(Object.keys({ ...globalThis.sessionStorage })).toEqual([]);
      expect(Object.keys({ ...globalThis.localStorage })).toEqual([]);
    }
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC8 — a failure says nothing, including nothing a header carried
// ---------------------------------------------------------------------------------------------------------

describe('T30: AC8 a failed read surfaces no detail, not even from a header', () => {
  /** Verbatim from the running auth service, 2026-10-31, with a malformed bearer. */
  const LIVE_401_HEADER =
    'Bearer error="invalid_token", error_description="An error occurred while attempting to decode the Jwt:'
    + ' Malformed token", error_uri="https://tools.ietf.org/html/rfc6750#section-3.1"';

  it('carries nothing from the WWW-Authenticate header, which is where the live detail is', async () => {
    const watch = watchConsole();
    // MEASURED: the live 401 body is EMPTY and the detail is in this header. A requirement written about
    // response bodies would have missed it entirely, which is why AC8 was rewritten to name headers.
    const tab = harness([
      { status: 401, headers: { 'www-authenticate': LIVE_401_HEADER } },
      { status: 401, headers: { 'www-authenticate': LIVE_401_HEADER } },
    ]);
    const error = await readProfile(tab.api, tab.tokens).catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(ReadProfileError);
    const text = `${(error as Error).name}: ${(error as Error).message}\n${(error as Error).stack ?? ''}`;
    for (const detail of ['invalid_token', 'decode the Jwt', 'Malformed', 'rfc6750', 'error_description']) {
      expect(text, detail).not.toContain(detail);
    }
    expect(watch.lines).toEqual([]);
  });

  it('names no cause and reaches no surface, for every failure shape', async () => {
    const shapes: Array<[string, Answer]> = [
      ['401 with header', { status: 401, headers: { 'www-authenticate': LIVE_401_HEADER } }],
      ['RFC 9457 body', { status: 500, body: { type: 'urn:x:db-down', detail: 'connection refused at pg-1' } }],
      ['OAuth2 body', { status: 400, body: { error: 'invalid_token', error_description: 'the jwt expired' } }],
      ['HTML login page', { status: 200, body: '<!DOCTYPE html><html lang="en"><head>' }],
    ];
    for (const [label, answer] of shapes) {
      const watch = watchConsole();
      const tab = harness([answer, answer]);
      const error = await readProfile(tab.api, tab.tokens).catch((caught: unknown) => caught);
      expect(error, label).toBeInstanceOf(ReadProfileError);
      const message = (error as Error).message;
      for (const detail of ['pg-1', 'connection refused', 'the jwt expired', 'db-down', 'DOCTYPE']) {
        expect(message, `${label}/${detail}`).not.toContain(detail);
      }
      expect(leaks(surfaces(), ['pg-1', 'connection refused', 'the jwt expired']), label).toEqual([]);
      expect(watch.lines, label).toEqual([]);
      vi.restoreAllMocks();
    }
  });

  it('converts a THROWING transport into a neutral error, keeping neither message nor stack', async () => {
    /**
     * Kimi's phase 8 I1, the one High-confidence finding in that review, and it deserved it. MEASURED before
     * the fix: with `fetch` throwing
     * `TypeError('Failed to fetch: net::ERR_NAME_NOT_RESOLVED at https://internal.example')`, that exact
     * `TypeError` escaped `readProfile` with its message and its stack intact.
     *
     * No status check could have caught it, because no response existed - `openapi-fetch` resolves an HTTP
     * error status but REJECTS when the transport throws. An internal hostname and a browser error code
     * reaching a caller is exactly what AC8 forbids.
     */
    const watch = watchConsole();
    const thrown = new TypeError('Failed to fetch: net::ERR_NAME_NOT_RESOLVED at https://internal.example');
    const tab = harness([{ body: FULL_PROFILE }], { throws: thrown });
    const error = await readProfile(tab.api, tab.tokens).catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(ReadProfileError);
    expect((error as ReadProfileError).failure).toBe('unavailable');
    const text = `${(error as Error).message}\n${(error as Error).stack ?? ''}`;
    for (const detail of ['ERR_NAME_NOT_RESOLVED', 'internal.example', 'Failed to fetch']) {
      expect(text, detail).not.toContain(detail);
    }
    expect(watch.lines).toEqual([]);
  });

  it('does the same for an abort and for a CORS-style failure', async () => {
    for (const thrown of [
      new DOMException('The operation was aborted.', 'AbortError'),
      new TypeError('NetworkError when attempting to fetch resource.'),
      'a string thrown by something that should have thrown an Error',
    ]) {
      const tab = harness([{ body: FULL_PROFILE }], { throws: thrown });
      await expect(readProfile(tab.api, tab.tokens)).rejects.toMatchObject({ failure: 'unavailable' });
    }
  });

  it('collapses a malformed JSON body into unavailable, with no parse detail', async () => {
    /**
     * MEASURED rather than assumed, because Kimi's phase 11 states the mechanism and the mechanism decides
     * which clause does the work: it reports that `openapi-fetch` sets `data` to `undefined` on a parse
     * failure, which would make the `data === undefined` arm responsible. It does not. The parse happens
     * INSIDE the awaited `api.GET`, so the `SyntaxError` is thrown there and caught by the transport
     * `try/catch` I1 added - the same arm that handles a dropped connection.
     *
     * So this was already correct, and is now pinned: a future change that moved the parse out of the `try`,
     * or inspected `answer.error` instead, would surface a `SyntaxError` naming the offending token.
     */
    const watch = watchConsole();
    const tab = harness([{ rawBody: '{ not json' }]);
    const error = await readProfile(tab.api, tab.tokens).catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(ReadProfileError);
    expect((error as ReadProfileError).failure).toBe('unavailable');
    const text = `${(error as Error).message}\n${(error as Error).stack ?? ''}`;
    for (const detail of ['JSON', 'Unexpected', 'token', 'not json', 'position']) {
      expect(text, detail).not.toContain(detail);
    }
    expect(watch.lines).toEqual([]);
  });

  it('carries no cause on any failure path, which is the other place detail can hide', async () => {
    /**
     * Kimi's phase 11. `Error` has taken a `cause` since ES2022, and `new Error(msg, { cause })` is the
     * idiomatic way to wrap a lower-level failure - so a maintainer closing I1 differently would reach for it
     * by reflex, and every assertion AC8 makes about `message` and `stack` would still pass while the original
     * `TypeError`, its hostname and its stack rode along on `error.cause`.
     *
     * Asserted over every path that can reject, not only the wrapping one: `cause` is absent, not merely
     * undefined, so setting it to anything at all fails here.
     */
    const paths: Array<[string, () => Promise<unknown>]> = [
      ['no token', () => readProfile(harness([{ body: FULL_PROFILE }], { token: null }).api, { current: () => null })],
      ['401', async () => {
        const tab = harness([{ status: 401 }, { status: 401 }]);
        return readProfile(tab.api, tab.tokens);
      }],
      ['302', async () => {
        const tab = harness([{ status: 302 }, { status: 302 }]);
        return readProfile(tab.api, tab.tokens);
      }],
      ['throwing transport', async () => {
        const tab = harness([{ body: FULL_PROFILE }], { throws: new TypeError('net::ERR_FAILED') });
        return readProfile(tab.api, tab.tokens);
      }],
      ['unusable body', async () => {
        const tab = harness([{ body: { sub: '' } }]);
        return readProfile(tab.api, tab.tokens);
      }],
    ];
    for (const [label, run] of paths) {
      const error = await run().catch((caught: unknown) => caught);
      expect(error, label).toBeInstanceOf(ReadProfileError);
      expect('cause' in (error as object), label).toBe(false);
      expect((error as Error).cause, label).toBeUndefined();
    }
  });

  it('the error message is INTERNAL: none of the three strings is in the catalogue', async () => {
    /**
     * Kimi's phase 8 I3, taken as option (a). `ReadProfileError.message` is English and is not catalogue
     * copy, so a screen rendering `error.message` would be rendering a string L11 says must live in the
     * catalogue. The trap is real because `error.message` is the obvious thing for a first consumer to reach
     * for.
     *
     * Asserted by INVERSION, which is the strongest form available here: the three strings appear nowhere in
     * the catalogue, so rendering one cannot be mistaken for rendering commissioned copy. A screen maps
     * `failure` - a closed set of three, for exactly this purpose - onto a key of its own.
     */
    const catalogue = await readFile(join(sourceRoot(), 'i18n/catalogue.ts'), 'utf8');
    for (const failure of ['no-session', 'unauthorized', 'unavailable'] as const) {
      const message = new ReadProfileError(failure).message;
      expect(catalogue, message).not.toContain(message);
    }
    // And the closed set is what a consumer is meant to switch on.
    expect(new ReadProfileError('no-session').failure).toBe('no-session');
  });

  it('CONTROL: an error carrying server detail WOULD be caught', () => {
    // Without this the case above passes for any error type at all, including one that happens not to have
    // been handed any detail. The instrument has to be shown to fail.
    const leaky = new Error('Profile unavailable: connection refused at pg-1');
    expect(leaky.message).toContain('connection refused');
    const neutral = new ReadProfileError('unavailable');
    expect(neutral.message).not.toContain('connection refused');
    // Three failures, three fixed strings, none interpolating anything from a response.
    for (const failure of ['no-session', 'unauthorized', 'unavailable'] as const) {
      expect(new ReadProfileError(failure).message.length).toBeGreaterThan(0);
    }
    /**
     * And the class cannot be TALKED INTO carrying detail, which is a stronger property than not doing so.
     *
     * MP09 is where this came from. It mutated the reader to throw
     * `new ReadProfileError(\`unauthorized${header}\`)` and SURVIVED - and the survival was not an escape.
     * The constructor looks its message up by key, so a key with a header glued on finds nothing and the
     * message becomes `undefined`. The leak was prevented by construction, not by a test, and the mutant was
     * INERT rather than uncaught. That distinction is T28's MB27 lesson, and the honest response is to pin
     * the property here and re-aim the mutant at the shape that can actually leak: a different error type.
     */
    const smuggled = new ReadProfileError(
      `unauthorized Bearer error="invalid_token"` as 'unauthorized',
    );
    expect(smuggled.message).not.toContain('invalid_token');
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC9 / AC11 — the live shape and the shape O6 will produce
// ---------------------------------------------------------------------------------------------------------

describe('T30: AC9/AC11 a sub-only response is a success, and a full profile parses', () => {
  it('treats {"sub"} alone as a SUCCESS, because that is what the server returns today', async () => {
    /**
     * Not a degenerate case. MEASURED 2026-10-31: a real interactive access token carrying
     * `scope: ["openid","profile","email"]` gets `200 {"sub":"<uuid>"}` and nothing else, because Spring
     * Authorization Server returns only `sub` unless an OIDC user-info mapper is configured and auth-service
     * configures none (`spec/auth-service/design.md` O6).
     *
     * A reader that treated the missing claims as an error would be broken against the only server that
     * exists, and would have looked correct in a suite that only ever fed it a full fixture.
     */
    const tab = harness([{ body: SUB_ONLY }]);
    const profile = await readProfile(tab.api, tab.tokens);
    expect(profile).toEqual(SUB_ONLY);
    expect(profile.email).toBeUndefined();
    expect(profile.email_verified).toBeUndefined();
    expect(profile.name).toBeUndefined();
  });

  it('parses the full profile O6 will produce, so closing it needs no change here', async () => {
    const tab = harness([{ body: FULL_PROFILE }]);
    expect(await readProfile(tab.api, tab.tokens)).toEqual(FULL_PROFILE);
  });

  it('rejects a JSON null body as unavailable rather than throwing a raw TypeError', async () => {
    /**
     * Kimi's phase 11, and A LIVE DEFECT rather than the unpinned-safety it was reported as.
     *
     * Its evidence says `typeof null.sub` evaluates to `'undefined'`, so the code was already safe. It does
     * not: `typeof` suppresses a ReferenceError for an undeclared identifier, while property access on `null`
     * throws like any other. MEASURED before the fix, with a `200` whose body is the JSON literal `null`:
     *
     *     TypeError: Cannot read properties of null (reading 'sub')
     *
     * thrown out of `readProfile` with its stack, past a `try/catch` that only wraps the transport. That is
     * I1's leak again on the body path. Right recommendation, wrong reason - which is the whole argument for
     * running a cited line instead of reading it.
     */
    const watch = watchConsole();
    const tab = harness([{ body: null }]);
    const error = await readProfile(tab.api, tab.tokens).catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(ReadProfileError);
    expect(error).not.toBeInstanceOf(TypeError);
    expect((error as ReadProfileError).failure).toBe('unavailable');
    expect((error as Error).message).not.toContain('sub');
    expect(watch.lines).toEqual([]);
  });

  it('rejects a body with no usable sub, which is the one claim the contract requires', async () => {
    for (const body of [{}, { sub: '' }, { sub: 42 }, { sub: null }]) {
      const tab = harness([{ body }, { body }]);
      await expect(readProfile(tab.api, tab.tokens)).rejects.toMatchObject({ failure: 'unavailable' });
    }
  });

  it('the O6 dependency is a stated fact here, not a skipped test', () => {
    /**
     * Kimi's F9 accepted in substance, its recommended FORM refused.
     *
     * F9 asked for `it.skip('live /userinfo returns only sub - pending O6')`. This suite is jsdom with no
     * network: such a case could never run in it, and a permanently skipped test is not a pin - it is a
     * comment that reports green and that somebody eventually deletes. The concern behind it is real, so it
     * is answered by having BOTH shapes asserted above: the live one as a success, the aspirational one as a
     * parse. Closing O6 changes nothing in this file, which is the correct outcome - a frontend unit suite is
     * not where a backend gap is detected.
     */
    expect(Object.keys(SUB_ONLY)).toEqual(['sub']);
    expect(Object.keys(FULL_PROFILE).length).toBeGreaterThan(Object.keys(SUB_ONLY).length);
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC12 — no session means no request, and a redirect is never a profile
// ---------------------------------------------------------------------------------------------------------

describe('T30: AC12 a missing session is not a 401, and a redirect is not a success', () => {
  it('issues NO request at all when the tab holds no token', async () => {
    const tab = harness([{ body: FULL_PROFILE }], { token: null });
    await expect(readProfile(tab.api, tab.tokens)).rejects.toMatchObject({ failure: 'no-session' });
    /**
     * The assertion that matters is the empty call list.
     *
     * MEASURED: a bearer-less `GET /userinfo` answers `302 Found, Location: /login` with a
     * `Set-Cookie: JSESSIONID` - so the request is not merely useless, it starts a SAS session as a side
     * effect of a read. And there is no 401 in that sequence for the boundary's renewal to act on, so
     * "let the boundary handle it" is not available here however reasonable it sounds.
     */
    expect(tab.calls).toEqual([]);
  });

  it('asks the transport not to follow redirects', async () => {
    const tab = harness([{ body: FULL_PROFILE }]);
    await readProfile(tab.api, tab.tokens);
    // The structural half. jsdom does not model redirect modes the way a browser does (Phase 5 hazard M3),
    // so what can be asserted here is that the option reaches the request; the behavioural half is below.
    expect(tab.calls[0].redirect).toBe('manual');
  });

  it('treats a 3xx as no session, whatever the body looks like', async () => {
    for (const answer of [
      { status: 302, headers: { location: '/login' } },
      { status: 303, headers: { location: '/login' } },
      { status: 307, body: SUB_ONLY },
    ]) {
      const tab = harness([answer, answer]);
      await expect(readProfile(tab.api, tab.tokens)).rejects.toMatchObject({ failure: 'no-session' });
    }
  });

  it('treats a FOLLOWED redirect as no session, which is the shape fetch produces by default', async () => {
    /**
     * The behavioural half, and the one that would have been the defect.
     *
     * MEASURED: `curl -L` on a bearer-less `/userinfo` ends at `200 OK, content-type: text/html`, 1103 bytes
     * of the SAS login page. `fetch` follows redirects by default, so without `redirect: 'manual'` a client
     * receives a SUCCESS status carrying HTML, and `openapi-fetch` would hand that to a caller expecting a
     * profile. The status is 200: nothing about it says the session is gone.
     */
    const followed = { status: 200, redirected: true, body: '<!DOCTYPE html><html lang="en"><head>' };
    const tab = harness([followed, followed]);
    await expect(readProfile(tab.api, tab.tokens)).rejects.toMatchObject({ failure: 'no-session' });
  });

  it('treats an OPAQUE redirect as no session, which is what a browser actually returns', async () => {
    /**
     * Kimi's phase 11. `redirect: 'manual'` does not hand a browser client a readable 3xx: it hands back an
     * opaque response, `type: 'opaqueredirect'`, `status: 0`, no headers and no body. So the status arm and
     * the `redirected` arm of the reader's check are both dead in a real browser, and the third arm is the
     * only one doing the work - while every case here drove one of the first two. Deleting
     * `response.type === 'opaqueredirect'` left the suite green and broke the no-session path everywhere it
     * matters most.
     *
     * jsdom models neither the redirect mode nor the opaque response, so this is a stub, and said so.
     */
    const opaque = { opaque: true };
    const tab = harness([opaque, opaque]);
    await expect(readProfile(tab.api, tab.tokens)).rejects.toMatchObject({ failure: 'no-session' });
    expect(tab.calls).toHaveLength(1);
  });

  it('issues no request for an EMPTY token either, which sends no bearer rather than a bad one', async () => {
    /**
     * Kimi's phase 11, accepted with its mechanism corrected. It reports that an empty token sends
     * `Authorization: Bearer ` - a malformed bearer that would draw a 401. The boundary attaches the header
     * with `if (token)` (`sessionBoundary.ts:89`), so an empty string sends NO Authorization header at all:
     * not a malformed bearer but the bearer-less GET this whole clause exists to prevent, `302` and
     * `Set-Cookie: JSESSIONID` included. Same conclusion, different mechanism.
     *
     * The guard names `''` explicitly rather than turning truthy, so that this case fails if it is removed.
     */
    const tab = harness([{ status: 302, headers: { location: '/login' } }], { token: '' });
    await expect(readProfile(tab.api, tab.tokens)).rejects.toMatchObject({ failure: 'no-session' });
    expect(tab.calls).toEqual([]);
  });

  it('rejects a FOLLOWED redirect even when it carries a perfectly valid profile', async () => {
    /**
     * Kimi's phase 11, and the case that makes the check ORDER load-bearing rather than incidental.
     *
     * The case above drives a followed redirect carrying HTML, which `!response.ok`-style reasoning would
     * also reject for the wrong reason - the body is not a profile. This one carries a body that parses
     * cleanly into a complete `UserInfoResponse`, so the ONLY thing standing between it and a successful
     * read is that `response.redirected` is checked before the body is looked at. A reordering that moved
     * the redirect arm after the shape arm would pass every other case in this block.
     */
    const followed = { status: 200, redirected: true, body: FULL_PROFILE };
    const tab = harness([followed, followed]);
    await expect(readProfile(tab.api, tab.tokens)).rejects.toMatchObject({ failure: 'no-session' });
  });

  it('MEASURES the race Kimi I6 names: a token cleared after the check still ends in no-session', async () => {
    /**
     * Kimi's phase 8 I6, accepted as a MEASUREMENT rather than as a restructuring.
     *
     * The window is real: `readProfile` reads `tokens.current()` synchronously, and the boundary's
     * `onRequest` reads it again when it attaches the bearer. Between the two, a cross-tab renewal failure,
     * a sign-out or a `clearAccessToken()` can clear it - so the guard passes, the boundary sends a
     * bearer-less request, and the 302 side-effect AC12 exists to prevent happens anyway.
     *
     * Kimi's own first option is the right one and this case is the evidence for it: the OUTCOME is safe,
     * because `redirect: 'manual'` and the 3xx check turn that response into a no-session failure regardless.
     * What remains is a narrow side-effect window, and closing it properly means moving the guard into the
     * boundary's `onRequest` - which is `api/sessionBoundary.ts`, named in the frozen brief as not T30's to
     * change, and shared by every other client. Recorded as T30-D6 rather than half-closed here.
     *
     * The sequence is: the reader's check sees a token, the boundary's read sees none.
     */
    const tab = harness(
      [{ status: 302, headers: { location: '/login' } }, { status: 302, headers: { location: '/login' } }],
      { tokenSequence: [TOKEN, null, null] },
    );
    await expect(readProfile(tab.api, tab.tokens)).rejects.toMatchObject({ failure: 'no-session' });
    // A request WAS issued - that is the window, stated rather than hidden - and it carried no bearer.
    expect(tab.calls).toHaveLength(1);
    expect(tab.calls[0].auth).toBeNull();
  });

  it('CONTROL: a 200 that is NOT a redirect still succeeds, so the check is not just rejecting everything', async () => {
    const tab = harness([{ status: 200, body: FULL_PROFILE }]);
    expect(await readProfile(tab.api, tab.tokens)).toEqual(FULL_PROFILE);
    expect(tab.calls).toHaveLength(1);
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC10 — every instrument shown to report dirty
// ---------------------------------------------------------------------------------------------------------

describe('T30: AC10 every instrument has been shown to report dirty', () => {
  /**
   * A SIGNPOST, NOT EVIDENCE, and said so because T27's Kimi review found a maintainer could read its
   * equivalent as proof. This table cannot fail for the right reason - only if a row is deleted without
   * updating the count. The real proof is the CONTROL cases each row names.
   *
   * The harness that scored this file:
   *
   *     python3 -I .ai/prompts/frontend/T30/artifacts/07-mutation-harness.py
   *
   * It reverts with `git checkout -- src/`, so commit before running it.
   */
  const INSTRUMENTS = [
    { instrument: 'contract declaration', dirtiedBy: 'a line-wise security: [] check, which prose defeated (AC1)' },
    { instrument: 'generated client', dirtiedBy: 'a typed GET that would not compile if the path were absent (AC2)' },
    { instrument: 'transport scan', dirtiedBy: 'five banned forms, and a shape check on the one permitted path (AC3)' },
    { instrument: 'L6 lint rule', dirtiedBy: 'the measurement that it bans identifiers, not routes (AC3, Kimi F7)' },
    { instrument: 'bearer', dirtiedBy: 'a real boundary renewing once and re-sending (AC4)' },
    { instrument: 'token decode scan', dirtiedBy: 'five synthetic decodes, and five legitimate non-decodes (AC5)' },
    { instrument: 'forbidden claim scan', dirtiedBy: 'four of the five synthetic decodes reading a claim (AC5)' },
    { instrument: 'decode allowlist', dirtiedBy: 'the raw hits containing the file the row removes (AC5)' },
    { instrument: 'empty-tree guard', dirtiedBy: 'a refusal rather than a clean report (AC5)' },
    { instrument: 'leak sweep', dirtiedBy: 'a sentinel on each of six surfaces (AC6)' },
    { instrument: 'encoding normalisation', dirtiedBy: 'a + -encoded name in a query string (AC6)' },
    { instrument: 'console watcher', dirtiedBy: 'a sentinel on each of six channels (AC7)' },
    { instrument: 'neutral error', dirtiedBy: 'a leaky Error beside a ReadProfileError (AC8)' },
    { instrument: 'sub-only success', dirtiedBy: 'four bodies with no usable sub, rejected (AC9)' },
    { instrument: 'no-session short-circuit', dirtiedBy: 'an empty call list, asserted (AC12)' },
    { instrument: 'redirect handling', dirtiedBy: 'three 3xx shapes and one FOLLOWED 200 text/html (AC12)' },
    { instrument: 'throwing transport', dirtiedBy: 'a TypeError, a DOMException and a thrown string (AC8, Kimi I1)' },
    { instrument: 'internal message', dirtiedBy: 'inversion - none of the three is in the catalogue (AC8, Kimi I3)' },
    { instrument: 'rate-limit wiring', dirtiedBy: 'a 429 on the RE-SEND after a 401, which is what the order is for (AC4, Kimi I5)' },
    { instrument: 'decode-pattern boundary', dirtiedBy: 'a dependency guard over seven JWT libraries (AC5, Kimi I7)' },
    { instrument: 'opaque redirect', dirtiedBy: "a response whose type is 'opaqueredirect' with status 0 (AC12, phase 11)" },
    { instrument: 'empty-token guard', dirtiedBy: "'' as well as null, sending NO bearer rather than a bad one (AC12, phase 11)" },
    { instrument: 'null body', dirtiedBy: 'a 200 whose JSON body is null, measured to throw a raw TypeError (AC9, phase 11)' },
    { instrument: 'absent cause', dirtiedBy: 'five rejecting paths, each asserted to have no cause at all (AC8, phase 11)' },
    { instrument: 'token sweep', dirtiedBy: "the access token over T30's own flow, which no other file drives (AC6, phase 11)" },
  ];

  it('names one dirtying violation per instrument', () => {
    expect(INSTRUMENTS).toHaveLength(25);
    expect(INSTRUMENTS.filter((row) => row.dirtiedBy.length === 0)).toEqual([]);
  });

  it('refuses a scan that reads nothing, through the one guard every walker calls', () => {
    expect(() => refuseEmpty([], NO_SOURCES)).toThrow(NO_SOURCES);
    expect(refuseEmpty([1], NO_SOURCES)).toEqual([1]);
  });

  it('CONTROL: comment stripping is what makes the decode scan survivable at all', () => {
    /**
     * This file's own header names `atob`, `JSON.parse` and every forbidden claim, a dozen times over, while
     * explaining why they are forbidden. So does `readProfile.ts`. An unstripped scan reads the package's own
     * justification as the defect.
     */
    const commented = synthetic('app/X.ts', '// we must never call atob on a token to read .email\nconst x = 1;');
    expect(hitsOf([commented], tokenDecodes())).toEqual([]);
    // And a literal is KEPT, which the two-regex stripper this replaced would have eaten.
    const literal = synthetic('app/Y.ts', "const help = 'https://id.example/atob-explainer';");
    expect(hitsOf([literal], tokenDecodes()).map((hit) => hit.hit)).toEqual(['atob']);
  });
});
