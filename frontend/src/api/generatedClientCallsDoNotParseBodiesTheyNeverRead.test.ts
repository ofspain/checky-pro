import { readdir, readFile } from 'node:fs/promises';
import { dirname, join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * TASK 175 — a call that does not read the body must not let the library parse it.
 *
 * `openapi-fetch` parses the reply body on its own behalf. For a call whose settle handler never looks at
 * `data`, that parse is pure liability: a reply whose body is not valid JSON makes the promise REJECT with a
 * SyntaxError, and a handler written as `.then(ok, failure)` cannot tell that from a dropped connection.
 * Three screens were written that way and all three shipped a user-visible bug:
 *
 *   - `ResendVerificationScreen` told a visitor "we could not send that" about an email the server HAD sent
 *     (task 51).
 *   - `VerifyScreen` told a visitor whose verification SUCCEEDED that their link was not valid (task 175).
 *   - `RegisterScreen` left the form unchanged, so the visitor saw nothing happen and would submit a second
 *     registration for an address the server had already accepted (task 175).
 *
 * Every one was invisible to the whole suite, because the contract declares `204` and `202` and every fixture
 * answers the way the contract says. THIS FILE EXISTS SO THE FOURTH ONE IS NOT WRITTEN.
 *
 * WHY THE RULE IS NOT "ALWAYS PASS parseAs": three calls legitimately READ the body, and for them a parse
 * failure IS a failure - no token, no profile, no account. A blanket rule would break them and a blanket ban
 * would let the defect back. The invariant is narrower than either: IF YOU DO NOT READ THE BODY, DO NOT LET
 * THE LIBRARY PARSE IT.
 */

const SRC = dirname(dirname(new URL(import.meta.url).pathname));

/**
 * HOW A CLIENT IS IDENTIFIED, and the first draft of this scan got it wrong twice.
 *
 * It looked only for a factory call in the same expression. That found three of the six call sites. The two
 * `profile/` readers DO NOT CALL A FACTORY AT ALL - the client arrives as a function PARAMETER, typed
 * `OidcApi` or `AuthApi` (`readProfile(api: OidcApi, …)`), so there is no `create…()` anywhere in either
 * file. A scan keyed on factories alone is structurally blind to every call site that takes its client as an
 * argument, which is the shape a testable module naturally has.
 *
 * So a client-bearing identifier is one of: bound from a factory, or declared with a client TYPE. Both lists
 * are asserted against `api/client.ts` below, so adding a factory or a type without telling this scan breaks
 * a case rather than silently shrinking its reach.
 */
const FACTORY = /\bcreate\w*(?:Api|Client)\s*\(/;
/** Relative to the repository root, which is two levels above `frontend/src`. A move breaks the read, and a
 *  failed read is the correct outcome: the audit below cannot be allowed to silently stop happening. */
const PACKAGE_INDEX = 'libs/ts/api-client/src/index.ts';
const CLIENT_TYPES = ['AuthApi', 'OidcApi'];

type Site = { file: string; method: string; kind: 'reader' | 'non-reader'; streams: boolean };

async function productionFiles(dir: string): Promise<string[]> {
  const out: string[] = [];
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) out.push(...(await productionFiles(full)));
    else if (/\.tsx?$/.test(entry.name) && !/\.test\.tsx?$/.test(entry.name)) out.push(full);
  }
  return out;
}

const stripComments = (source: string) =>
  source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');

/**
 * Collects one row per generated-client call, working in LINES rather than by character offsets.
 *
 * The first draft looked backwards for the nearest `;` or `{` to find where the statement began. That is what
 * misclassified `codeExchange.ts`: its call is `const { data } = await createOidcClient().POST(…)`, and the
 * nearest `{` is the DESTRUCTURING BRACE, so the slice began after `const {` and the binding that makes it a
 * reader had been cut off. A brace is not a statement boundary when the statement starts by destructuring.
 */
function sitesIn(file: string, source: string): Site[] {
  const lines = stripComments(source).split('\n');
  const clientNames = new Set<string>();
  for (const line of lines) {
    for (const m of line.matchAll(/\b(?:const|let|var)\s+(\w+)\s*=\s*(?:await\s+)?create\w*(?:Api|Client)\s*\(/g)) {
      clientNames.add(m[1]!);
    }
    for (const type of CLIENT_TYPES) {
      for (const m of line.matchAll(new RegExp(`\\b(\\w+)\\s*:\\s*${type}\\b`, 'g'))) clientNames.add(m[1]!);
    }
  }

  const sites: Site[] = [];
  for (let i = 0; i < lines.length; i += 1) {
    const call = /\.\s*(POST|GET|PUT|PATCH|DELETE)\s*\(/.exec(lines[i]!);
    if (!call) continue;

    // The statement starts at the nearest line at or above this one that OPENS a statement, and runs to the
    // first line at or below that closes one. Both ends matter and they sit on opposite sides of the call: an
    // `await` binding comes before it, a `.then` settle handler after it.
    let start = i;
    while (start > 0 && !/^\s*(?:const|let|var|void|return|await|export|if|})/.test(lines[start]!)) start -= 1;
    let end = i;
    while (end < lines.length - 1 && !/;\s*$/.test(lines[end]!)) end += 1;
    const statement = lines.slice(start, end + 1).join('\n');

    const named = [...clientNames].some((n) => new RegExp(`\\b${n}\\s*\\.\\s*(?:POST|GET|PUT|PATCH|DELETE)\\s*\\(`).test(statement));
    if (!FACTORY.test(statement) && !named) continue;

    // A READER binds the result or names `data` in a settle handler. BOTH SHAPES ARE NEEDED: all three
    // readers in this bundle `await`, so a scan that only inspected `.then` callbacks would have called every
    // one of them a non-reader and demanded the option on exactly the calls that must not have it.
    const bound = /\b(?:const|let|var)\s+(?:\{[^}]*\}|\w+)\s*=\s*await\b/.test(statement);
    const namesData = /\.then\s*\(\s*(?:async\s*)?\(\s*\{[^}]*\bdata\b/.test(statement);
    sites.push({
      file: relative(SRC, file),
      method: call[1]!,
      kind: bound || namesData ? 'reader' : 'non-reader',
      streams: /parseAs:\s*'stream'/.test(statement),
    });
  }
  return sites;
}

describe('generatedClientCallsDoNotParseBodiesTheyNeverRead', () => {
  it('knows every factory and client type that api/client.ts exports, so its reach cannot shrink quietly', async () => {
    // THE SCAN'S OWN REACH IS THE THING MOST LIKELY TO ROT. A new factory or a new exported client type would
    // leave it finding fewer call sites than exist, and a scan that finds nothing passes. This case fails the
    // day `client.ts` grows one, which forces the lists above to be updated rather than silently outgrown.
    const client = stripComments(await readFile(join(SRC, 'api/client.ts'), 'utf8'));

    const factories = [...client.matchAll(/export function (create\w+)\s*\(/g)].map((m) => m[1]!).sort();
    expect(factories, 'api/client.ts exports a factory this scan has never been told about')
      .toEqual(['createAuthApi', 'createOidcApi', 'createPublicAuthApi']);
    // Tested as a CALL, because `FACTORY` requires the opening parenthesis - the first draft asserted the
    // bare name against it and failed on every factory, which is the whole value of running a claim.
    for (const factory of factories) {
      expect(`${factory}(`, `${factory} does not match the factory pattern, so calls through it are invisible`)
        .toMatch(FACTORY);
    }

    const types = [...client.matchAll(/export type (\w+) = ReturnType</g)].map((m) => m[1]!).sort();
    expect(types, 'api/client.ts exports a client type this scan has never been told about')
      .toEqual([...CLIENT_TYPES].sort());

    // AND THE PACKAGE'S OWN FACTORIES, which is the hole phase 7 reported on itself as OQ-T175-7 and Kimi
    // confirmed as its only Medium. `codeExchange` does not import from `api/client.ts` at all - it takes
    // `createOidcClient` straight from `@themistra/api-client` - so auditing only the local module left the
    // package free to grow a factory that widened the surface without breaking a case.
    const pkg = stripComments(await readFile(join(SRC, '..', '..', PACKAGE_INDEX), 'utf8'));
    const packaged = [...pkg.matchAll(/export function (create\w+)\s*\(/g)].map((m) => m[1]!).sort();
    expect(packaged, '@themistra/api-client exports a factory this scan has never been told about')
      .toEqual(['createAuthClient', 'createOidcClient']);
    for (const factory of packaged) {
      expect(`${factory}(`, `${factory} does not match the factory pattern, so calls through it are invisible`)
        .toMatch(FACTORY);
    }
  });

  it('classifies each call SHAPE the way it is documented to, measured on source rather than reasoned about', () => {
    // WHY A FIXTURE AND NOT A WIDER REGEX. Kimi's phase 8 (I11) noticed that `.then((r) => r.data)` reads the
    // body and is classified a NON-READER here, and recommended broadening the reader test to `.data`
    // anywhere in the statement. That was declined, and the asymmetry is the reason:
    //
    //   a false NON-READER demands `parseAs` on a call that reads the body - it FAILS LOUDLY, and someone
    //     must look at it (Kimi's own phase 3 design erred this way, and phase 6 caught it);
    //   a false READER is EXEMPT FROM THE RULE - the scan goes green and the defect that cost three screens
    //     can ship a fourth time.
    //
    // A safety scan must err toward the loud mistake, so the narrow test stays. What this case adds is the
    // thing a regex argument cannot give: the classifier measured on source, one row per shape, so the day
    // someone widens it they must change a case that records what the old answer was.
    //
    // It also exercises `.PUT`, `.PATCH` and `.DELETE`, which NO PRODUCTION CALL SITE USES (Kimi I13). Those
    // branches of the pattern were matched against nothing at all, and a pattern aimed at nothing is what
    // task 37 paid to learn - its forbidden-call regex turned out to forbid an impossible call shape.
    const fixture = [
      "import { createAuthClient } from '@themistra/api-client';",
      "import type { AuthApi } from './client';",
      '',
      'export async function readsByAwait() {',
      "  const { data } = await createAuthClient().GET('/a');",
      '  return data;',
      '}',
      '',
      'export async function readsByDestructuredThen() {',
      "  return createAuthClient().PUT('/b').then(({ data }) => data);",
      '}',
      '',
      'export function readsByPropertyAccess() {',
      "  return createAuthClient().PATCH('/c').then((r) => r.data);",
      '}',
      '',
      'export function takesClientAsAParameter(api: AuthApi) {',
      "  void api.DELETE('/d');",
      '}',
      '',
      'export function neverReads() {',
      "  void createAuthClient().POST('/e', { body: {} });",
      '}',
      '',
      'export function neverReadsButStreams() {',
      "  void createAuthClient().POST('/f', { body: {}, parseAs: 'stream' });",
      '}',
      '',
      'export function notAClientAtAll(thing: SomethingElse) {',
      "  void thing.POST('/g');",
      '}',
      '',
    ].join('\n');

    expect(sitesIn(join(SRC, 'fixture.ts'), fixture), 'a documented call shape classifies differently now')
      .toEqual([
        { file: 'fixture.ts', method: 'GET', kind: 'reader', streams: false },
        { file: 'fixture.ts', method: 'PUT', kind: 'reader', streams: false },
        // THE SHAPE KIMI NAMED, pinned as a NON-reader on purpose. If this row ever reads `reader`, someone
        // has widened the classifier into the silent-failure direction and must say why here.
        { file: 'fixture.ts', method: 'PATCH', kind: 'non-reader', streams: false },
        { file: 'fixture.ts', method: 'DELETE', kind: 'non-reader', streams: false },
        { file: 'fixture.ts', method: 'POST', kind: 'non-reader', streams: false },
        { file: 'fixture.ts', method: 'POST', kind: 'non-reader', streams: true },
      ]);

    // AND IT DOES NOT OVER-MATCH. `thing.POST('/g')` is a call on something that is neither a factory result
    // nor typed as a client, and it must be absent - seven calls in the fixture, six rows. A scan that
    // matched every `.POST(` in the tree would be unusable, and would hide its own imprecision behind a
    // green census.
    expect(sitesIn(join(SRC, 'fixture.ts'), fixture).length, 'a non-client call was counted as a client call')
      .toBe(6);
  });

  it('finds every generated-client call, and the census is ASSERTED so an empty scan fails (AC10)', async () => {
    const files = await productionFiles(SRC);
    const sites = (await Promise.all(files.map(async (f) => sitesIn(f, await readFile(f, 'utf8')))))
      .flat()
      .sort((a, b) => a.file.localeCompare(b.file) || a.method.localeCompare(b.method));

    // THE WHOLE TABLE IS THE ASSERTION. It pins the census, every classification and every option in one
    // comparison, so adding a call site, moving one, or changing what one reads all break this case and have
    // to be argued for. A bare `toHaveLength` would let a new non-reading call in as soon as someone bumped
    // the number.
    expect(sites, 'the set of generated-client calls changed - every row here is a decision').toEqual([
      // THE THREE READERS. Exempt because for them a parse failure IS a failure: no token, no profile, no
      // account. Two of them take their client as a PARAMETER and name no factory at all.
      { file: 'auth/codeExchange.ts', method: 'POST', kind: 'reader', streams: false },
      // TASK 52 - THE FIRST CALL IN THE BUNDLE WRITTEN WITH THE OPTION FROM BIRTH, which is what this scan
      // was built for. The three rows around it were each retrofitted after a visitor-facing bug; this one
      // arrived already correct, and the only case that fired when it appeared was the census below - not
      // the invariant. That asymmetry is the scan working: a new non-reading call that STREAMS changes the
      // census and nothing else, while one that does not would fail both.
      { file: 'features/account/PasswordResetRequestScreen.tsx', method: 'POST', kind: 'non-reader', streams: true },
      { file: 'features/account/RegisterScreen.tsx', method: 'POST', kind: 'non-reader', streams: true },
      { file: 'features/account/ResendVerificationScreen.tsx', method: 'POST', kind: 'non-reader', streams: true },
      { file: 'features/account/VerifyScreen.tsx', method: 'POST', kind: 'non-reader', streams: true },
      { file: 'profile/readAccount.ts', method: 'GET', kind: 'reader', streams: false },
      { file: 'profile/readProfile.ts', method: 'GET', kind: 'reader', streams: false },
    ]);
  });

  it('holds the invariant itself, so a new non-reading call fails even if the table above is edited', async () => {
    const files = await productionFiles(SRC);
    const sites = (await Promise.all(files.map(async (f) => sitesIn(f, await readFile(f, 'utf8'))))).flat();

    expect(sites.length, 'the scan found no generated-client calls at all, so it is measuring nothing')
      .toBeGreaterThan(3);

    const offenders = sites.filter((s) => s.kind === 'non-reader' && !s.streams)
      .map((s) => `${s.file} (${s.method})`);
    expect(offenders, 'a call that never reads the body lets openapi-fetch parse it, so a reply that is not '
      + 'valid JSON reaches its failure arm and the visitor is told the request failed when it did not')
      .toEqual([]);
  });
});
