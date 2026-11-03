import { readdir, readFile } from 'node:fs/promises';
import { dirname, join, relative } from 'node:path';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ACCOUNT_STATUSES } from '../profile/accountStatus';
import { REQUESTED_SCOPES } from './authorizeRequest';

/**
 * R20's third clause: **verification state is never read from a token.**
 *
 * `spec/frontend/agents.md:37-38` states it as a standing rule - "The access-token `email_verified` claim is
 * never treated as account verification state" - so this file instruments a rule rather than inventing one.
 *
 * WHY THE CLAIM IS WORSE THAN STALE, from `contracts/api/token-claims.md` rather than from reasoning. The
 * access token's `email_verified` is `true` if and only if the `email` scope was authorized **for that one
 * request**. It reports scope authorization, not verification state, confirmed against a real token: one
 * requesting `scope=openid` alone carries `false` for an account that IS verified. And this client requests
 * `email` on every authorize (`authorizeRequest.ts`), so for us it reads `true` for an account that has
 * never verified anything.
 *
 * So it is not a stale answer to the right question, which is what a token-borne `status` would have been
 * (T31). It answers a DIFFERENT question while wearing the name of the question a verification surface wants
 * to ask, and it fails in the dangerous direction: unverified shown as verified.
 *
 * WHERE VERIFICATION STATE DOES COME FROM, because a prohibition with no stated alternative invites the next
 * author to guess: `AccountResponse.emailVerified` from `GET /accounts/me` (required, boolean - T31 reads
 * it), the `PENDING_VERIFICATION` status from the same response, and `UserInfoResponse.email_verified` from
 * `GET /userinfo`, which `contracts/api/oidc.yaml` documents as verification state "as the profile endpoint
 * reports it. NOT the access token's `email_verified` claim" - a line that cites this task by name. The
 * `/userinfo` field is unavailable in practice today: the running server answers `{"sub"}` only (O6).
 *
 * WHAT THIS FILE CAN AND CANNOT PROVE - Kimi's phase 3, finding 4, and the most important paragraph here.
 * These are **source-side** guards, not data-flow guards. They catch a module READING the claim. They cannot
 * trace a value that was read off a token somewhere, renamed to `isEmailVerified`, and passed along as a
 * prop - the enforcement point is token access, not use. The honest claim is "no production module reads the
 * claim", and the broader "no UI is driven by it" rests on that plus review, not on a scan.
 *
 * AND IT IS A FORWARD GUARD. No task commissions a signed-in "your email is not verified" surface: tasks
 * 48-51 build the verification LINK flow, where a visitor clicks a link with no session and no claim
 * involved, and nothing in `tasks.md` reads `PENDING_VERIFICATION` to tell a signed-in user anything
 * (OQ-T32-1). So these instruments mostly fire on code that does not exist yet. That is deliberate - a
 * prohibition is cheapest to install before its first consumer - but it should not be mistaken for a
 * present-tense proof about a running screen.
 *
 * SPELLING IS NOT A CHANNEL, which was the design challenge's blocker. `oidc.yaml` spells the PERMITTED
 * `/userinfo` field `email_verified`, exactly as the prohibited claim is spelled, because both are OIDC
 * artifacts. An earlier draft of this task tried to tell them apart by snake versus camel case and was
 * incoherent: it would have flagged the legitimate read. Provenance is the only discriminator, so AC1 bounds
 * the files that can decode a token and AC2 asks of every mention which CHANNEL the file reads.
 */

// ---------------------------------------------------------------------------------------------------------
// Helpers. Defined here, as every instrument file in this package defines its own - they are not shared and
// not exported anywhere in `src/`. That is deliberate: these are adversarial instruments, and a helper
// edited for one task would silently change what another task's scan MEANS. The cost is duplication, logged
// as OQ-T32-4. Each arrives with the reason it has its shape, because a helper stripped of its reason is how
// the next task simplifies it back into the defect it was written to avoid.
// ---------------------------------------------------------------------------------------------------------

const NO_SOURCES = 'scan refused: no production sources found under src/';

/**
 * Comments removed, string and template literals KEPT.
 *
 * Carried from T29, where the two-regex version it replaced was a real defect found by reading rather than
 * by running: deleting from the first `//` to end of line turns `'https://id.example/help'` into `'https:`,
 * and a claim name sitting inside a literal would leave the source before any scan saw it. This file's own
 * header names the forbidden claim a dozen times while explaining why it is forbidden, so an unstripped scan
 * would read the explanation as the defect.
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

/** An empty tree is REFUSED, never reported clean (T22-D14). A scan of nothing passes every absence claim. */
function refuseEmpty<T>(items: T[], refusal: string): T[] {
  if (items.length === 0) throw new Error(refusal);
  return items;
}

function sourceRoot(): string {
  // `src/auth/<this>` -> `src`. Derived from the runner, so moving this file cannot silently narrow the scan
  // to a directory that no longer exists.
  return dirname(dirname(expect.getState().testPath as string));
}

function rel(root: string, file: string): string {
  return relative(root, file).split(/[\\/]/).join('/');
}

/** `contracts/api/<name>`: `src` -> `frontend` -> the repository root. */
function contract(name: string): Promise<string> {
  return readFile(join(dirname(dirname(sourceRoot())), 'contracts/api', name), 'utf8');
}

/** One stanza of a YAML file, by its opening key and the key that follows it. */
function stanza(text: string, from: string, to: string): string {
  const start = text.indexOf(from);
  const end = text.indexOf(to, start + from.length);
  return text.slice(start, end === -1 ? undefined : end);
}

type ScanTarget = { path: string; code: string };
type Hit = { file: string; hit: string };

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
  const root = sourceRoot();
  const targets: ScanTarget[] = [];
  for (const file of await productionFiles()) {
    targets.push({ path: rel(root, file), code: strip(await readFile(file, 'utf8')) });
  }
  return targets;
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

function synthetic(path: string, code: string): ScanTarget {
  return { path, code: strip(code) };
}

// ---------------------------------------------------------------------------------------------------------
// The patterns
// ---------------------------------------------------------------------------------------------------------

/**
 * Reading a claim off a JWT, as opposed to naming one.
 *
 * NARROW ON PURPOSE, carried from T30's M2 with its reason. The obvious pattern would be the five decode
 * forms `idTokenNonce.ts` actually uses - `split`, `atob`, `TextDecoder`, `Uint8Array.from`, `JSON.parse` -
 * but `split` alone matches ordinary string work across the tree and `JSON.parse` matches every response
 * body in it. A bound that matches everything is not a bound. Breadth is applied instead to the NAMED
 * file's own accounting below, where it is safe because the file is named.
 */
const TOKEN_DECODE = '\\batob\\b|\\bjwt[Dd]ecode\\b|\\bdecodeJwt\\b|split\\([\'"]\\.[\'"]\\)';

/** The five forms the one decoding file actually uses - its LOWER bound. */
const DECODE_FORMS = ['atob', 'TextDecoder', 'Uint8Array.from', 'JSON.parse', "split('.')"] as const;

/**
 * BOTH spellings, inside a decoding file.
 *
 * Elsewhere the spelling carries no information about provenance, which is the whole lesson of this task's
 * design challenge. But inside a file that decodes a token the channel is ALREADY a token, so excluding
 * either spelling there would be a gap for free.
 */
const CLAIM_EITHER_SPELLING = '\\bemail_verified\\b|\\bemailVerified\\b';

/** The OIDC/token spelling, which is also the `/userinfo` contract's spelling. Hence AC2's channel rule. */
const CLAIM_OIDC_SPELLING = '\\bemail_verified\\b';

/** Reading `/userinfo` through the generated client - the one channel that may carry this field legitimately. */
const USERINFO_READ = "GET('/userinfo'";

/** The one production module permitted to decode a token at all, with what it reads and why. */
const PERMITTED_DECODE = {
  file: 'auth/idTokenNonce.ts',
  reads: 'nonce',
  why: 'R7 and L1 require `nonce` validation, and a nonce can only be read by decoding the id_token. It '
    + 'reads the nonce and no claim besides - no PII, and no verification state (T19, T30, T31).',
} as const;

describe('emailVerifiedClaimIsNotAccountState', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    globalThis.sessionStorage.clear();
    globalThis.localStorage.clear();
    document.body.innerHTML = '';
    document.head.innerHTML = '';
    document.title = '';
    window.name = '';
    // The URL is reset HERE and not at the end of the case that dirties it: a case that fails before its own
    // cleanup would otherwise leak `?email_verified=true` into the next one, which is exactly what happened
    // the first time this file ran.
    globalThis.history.replaceState({}, '', '/app');
  });

  // -------------------------------------------------------------------------------------------------------
  // AC1 — the decode bound. A token is opaque bytes until something decodes it, so the set of files that
  // decode is where a token-sourced read must originate. This is the implementable form of "never off a
  // token", and every claim below is only as sound as this bound - so it is re-asserted here rather than
  // inherited from T30 or T31.
  // -------------------------------------------------------------------------------------------------------

  describe('AC1 the files that decode a token are bounded, and none reads this claim', () => {
    it('is exactly one file, and it is the nonce reader', async () => {
      const targets = await productionSources();
      const decoders = [...new Set(hitsOf(targets, new RegExp(TOKEN_DECODE, 'g')).map((hit) => hit.file))];
      expect(decoders).toEqual([PERMITTED_DECODE.file]);
    });

    it('reads NEITHER spelling of the claim, in the one file that decodes', async () => {
      const targets = await productionSources();
      const decoding = targets.filter((target) => target.path === PERMITTED_DECODE.file);
      expect(decoding, 'the permitted decoder is missing from the scan').toHaveLength(1);
      expect(hitsOf(decoding, new RegExp(CLAIM_EITHER_SPELLING, 'g'))).toEqual([]);
      // And it still reads what its row claims, so the row cannot describe a file that moved on.
      expect(decoding[0].code).toContain(PERMITTED_DECODE.reads);
    });

    it('still DECODES, which is the allowlist row lower bound', async () => {
      // An upper bound alone lets a row outlive the thing it permits: delete the decode and the row keeps
      // granting budget nobody is using, which is how an allowlist stops being evidence (T29's lesson).
      const targets = await productionSources();
      const decoding = targets.find((target) => target.path === PERMITTED_DECODE.file);
      expect(decoding).toBeDefined();
      for (const form of DECODE_FORMS) {
        expect((decoding as ScanTarget).code, `${PERMITTED_DECODE.file} no longer uses ${form}`)
          .toContain(form);
      }
    });

    it('CONTROL: the scan FIRES when a decoding file reads the claim, in either spelling', () => {
      for (const spelling of ['email_verified', 'emailVerified']) {
        const dirty = synthetic('auth/Sneaky.ts', `const v = JSON.parse(globalThis.atob(p)).${spelling};`);
        expect(hitsOf([dirty], new RegExp(TOKEN_DECODE, 'g')), spelling).not.toEqual([]);
        expect(hitsOf([dirty], new RegExp(CLAIM_EITHER_SPELLING, 'g')).map((hit) => hit.hit), spelling)
          .toEqual([spelling.toLowerCase()]);
      }
    });

    it('CONTROL: the two spellings do not match each other, which is one character class apart', () => {
      const snake = synthetic('x/a.ts', 'const a = body.email_verified;');
      const camel = synthetic('x/b.ts', 'const b = body.emailVerified;');
      expect(hitsOf([snake], new RegExp(CLAIM_OIDC_SPELLING, 'g')).map((hit) => hit.hit))
        .toEqual(['email_verified']);
      expect(hitsOf([camel], new RegExp(CLAIM_OIDC_SPELLING, 'g'))).toEqual([]);
    });

    it('CONTROL: a scan that reads nothing is refused, not reported clean', () => {
      expect(() => refuseEmpty([], NO_SOURCES)).toThrow(NO_SOURCES);
      expect(refuseEmpty([1], NO_SOURCES)).toEqual([1]);
    });
  });

  // -------------------------------------------------------------------------------------------------------
  // AC2 — the channel rule, tree-wide. `oidc.yaml` spells the PERMITTED /userinfo field exactly as the
  // prohibited claim, so the question a scan must ask is not how a mention is spelled but which channel the
  // file that mentions it reads. Measured today: zero production files name it comment-stripped, so the rule
  // holds over an empty permitted set - which is why the controls exercise the RULE and not a list.
  // -------------------------------------------------------------------------------------------------------

  describe('AC2 a file may name the claim only if it reads /userinfo', () => {
    function offChannel(targets: ScanTarget[]): Hit[] {
      const named = hitsOf(targets, new RegExp(CLAIM_OIDC_SPELLING, 'g'));
      const byChannel = new Map(targets.map((target) => [target.path, target.code.includes(USERINFO_READ)]));
      return named.filter((hit) => byChannel.get(hit.file) !== true);
    }

    it('no production file names the claim off the /userinfo channel', async () => {
      expect(offChannel(await productionSources())).toEqual([]);
    });

    it('and today nothing names it at all, which is the stronger statement while it is true', async () => {
      const targets = await productionSources();
      expect(hitsOf(targets, new RegExp(CLAIM_OIDC_SPELLING, 'g'))).toEqual([]);
      // The file that WOULD be permitted exists and reads the channel, so the rule is not hypothetical.
      const reader = targets.find((target) => target.code.includes(USERINFO_READ));
      expect(reader?.path).toBe('profile/readProfile.ts');
    });

    it('CONTROL: the rule FIRES on a mention with no /userinfo read', () => {
      const dirty = synthetic('app/Banner.ts', 'export const verified = (a: Account) => a.email_verified;');
      expect(offChannel([dirty]).map((hit) => hit.file)).toEqual(['app/Banner.ts']);
    });

    it('CONTROL: the rule is SILENT on a mention in a file that does read /userinfo', () => {
      // The admission rule, exercised rather than described. `oidc.yaml` documents this field as verification
      // state as the profile endpoint reports it, so this shape is legitimate and must not be flagged.
      const permitted = synthetic(
        'profile/readProfile.ts',
        "const answer = await api.GET('/userinfo', {});\nconst v = answer.data?.email_verified;",
      );
      expect(offChannel([permitted])).toEqual([]);
      // And the mention really is there, so the silence is not vacuous.
      expect(hitsOf([permitted], new RegExp(CLAIM_OIDC_SPELLING, 'g'))).toHaveLength(1);
    });

    it('CONTROL: the rule is SILENT on the verification LINK flow, which is tasks 48-51', () => {
      // R3-R5: a visitor opens a link carrying a URL token, with no session and no claim anywhere. An
      // instrument that fired here would be a defect landing in someone else's task - T31 had to repair
      // three inherited instruments that did exactly that, so the false-positive proof is written first.
      const link = synthetic(
        'auth/VerifyEmail.ts',
        "const token = new URLSearchParams(location.search).get('token');\n"
          + "await api.POST('/accounts/verify', { body: { token } });",
      );
      expect(offChannel([link])).toEqual([]);
      expect(hitsOf([link], new RegExp(CLAIM_EITHER_SPELLING, 'g'))).toEqual([]);
    });
  });

  // -------------------------------------------------------------------------------------------------------
  // AC3 — where verification state DOES come from. The positive half, because a prohibition with no stated
  // alternative invites the next author to guess.
  // -------------------------------------------------------------------------------------------------------

  describe('AC3 the truthful sources are pinned', () => {
    it('AccountResponse requires emailVerified as a boolean', async () => {
      const account = stanza(await contract('auth.yaml'), '    AccountResponse:', '    AccountStatus');
      expect(account).toContain('required: [accountUuid, email, emailVerified, status, createdAt]');
      expect(stanza(account, '        emailVerified:', '        status:')).toContain('type: boolean');
    });

    it('PENDING_VERIFICATION is in the contract enum AND in the runtime list T31 checks against it', async () => {
      const account = stanza(await contract('auth.yaml'), '    AccountResponse:', '    AccountStatus');
      expect(account).toContain('PENDING_VERIFICATION');
      expect(ACCOUNT_STATUSES).toContain('PENDING_VERIFICATION');
    });

    it('the /userinfo field is documented as NOT the token claim, in the line that cites this task', async () => {
      const userinfo = stanza(await contract('oidc.yaml'), '    UserInfoResponse:', '    TokenResponse');
      const field = stanza(userinfo, '        email_verified:', '        name:');
      expect(field).toContain('type: boolean');
      expect(field).toContain('NOT the access');
      expect(field).toContain('token-claims.md');
      expect(field).toContain('T32');
    });
  });

  // -------------------------------------------------------------------------------------------------------
  // AC4 — the premise. If any of these three drifts, this task's reasoning has changed and the file should be
  // re-read rather than patched. The assertion messages say so.
  // -------------------------------------------------------------------------------------------------------

  describe('AC4 the claim semantics this task depends on are still what the contract says', () => {
    it('token-claims.md still documents the claim as reflecting scope authorization', async () => {
      const claims = await contract('token-claims.md');
      const rows = claims.split('\n').filter((line) => line.includes('`email_verified`'));
      expect(rows.length, 'token-claims.md no longer documents email_verified').toBeGreaterThan(0);
      expect(
        rows.some((row) => row.includes('scope authorization')),
        'the claim no longer reports SCOPE AUTHORIZATION: re-read T32, do not patch this assertion',
      ).toBe(true);
    });

    it('the SPA still requests the email scope, which is what makes the claim read true for us', () => {
      expect([...REQUESTED_SCOPES]).toEqual(['openid', 'profile', 'email']);
    });
  });

  // -------------------------------------------------------------------------------------------------------
  // AC7 — the surface sweep, as a DIRTY-PROOF. A "no hits" report from code that writes nothing is
  // indistinguishable from a skipped test, and this package has been bitten by exactly that.
  // -------------------------------------------------------------------------------------------------------

  describe('AC7 a verification flag on any surface would be found', () => {
    const CHANNELS = ['log', 'info', 'warn', 'error', 'debug', 'trace'] as const;
    const NEEDLES = ['email_verified', 'emailVerified', 'PENDING_VERIFICATION'] as const;

    function surfaces(): { name: string; text: string }[] {
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

    /**
     * `document.cookie` and `indexedDB` are NOT swept, declared rather than quietly omitted - the same
     * declaration T29, T30 and T31 each make at their own sweeps. jsdom gives an empty cookie jar and has no
     * `indexedDB`, so sweeping either would report clean for the wrong reason, which is worse than not
     * sweeping it.
     */
    function leaks(needles: readonly string[]): string[] {
      const hits: string[] = [];
      for (const surface of surfaces()) {
        const lowered = surface.text.toLowerCase();
        for (const needle of needles) {
          if (lowered.includes(needle.toLowerCase())) hits.push(`${surface.name}/${needle}`);
        }
      }
      return hits;
    }

    it('reports a flag written to EVERY one of the seven surfaces', () => {
      document.body.innerHTML = '<p>emailVerified</p>';
      // `head` BEFORE `title`, and the order is load-bearing: `document.title` writes a `<title>` element
      // into the head, so assigning `head.innerHTML` afterwards deletes it and the title sentinel vanishes.
      // The first run of this file did exactly that and reported six surfaces of seven. T30 learned the same
      // coupling from the other side - a title sentinel is ALSO found in `head.innerHTML` - which is why
      // every surface here carries a sentinel of its own.
      document.head.innerHTML = '<meta name="x" content="PENDING_VERIFICATION">';
      document.title = 'email_verified';
      window.name = 'emailVerified';
      globalThis.history.replaceState({}, '', '/app?email_verified=true');
      globalThis.sessionStorage.setItem('a', 'PENDING_VERIFICATION');
      globalThis.localStorage.setItem('b', 'emailVerified');

      const found = new Set(leaks(NEEDLES).map((hit) => hit.split('/')[0]));
      expect([...found].sort())
        .toEqual(['body', 'head', 'localStorage', 'sessionStorage', 'title', 'url', 'window.name']);
    });

    it('reports a flag logged to EVERY one of the six console channels', () => {
      const lines: string[] = [];
      for (const channel of CHANNELS) {
        vi.spyOn(console, channel).mockImplementation((...args: unknown[]) => {
          lines.push(`${channel}: ${args.map((arg) => String(arg)).join(' ')}`);
        });
      }
      for (const channel of CHANNELS) console[channel]('emailVerified', true);
      expect(lines).toHaveLength(6);
      expect(lines.filter((line) => line.includes('emailVerified'))).toHaveLength(6);
    });

    it('and the surfaces are clean when nothing has written to them', () => {
      expect(leaks(NEEDLES)).toEqual([]);
    });
  });

  // -------------------------------------------------------------------------------------------------------
  // AC8 — the limitation, asserted rather than only described, so it cannot be deleted from the header
  // without a test noticing.
  // -------------------------------------------------------------------------------------------------------

  describe('AC8 the limitation is stated in this file', () => {
    it('says in its own header that it is a source-side guard and a forward guard', async () => {
      const self = await readFile(expect.getState().testPath as string, 'utf8');
      const header = self.slice(0, self.indexOf('// ---'));
      for (const phrase of ['source-side', 'not data-flow guards', 'forward guard', 'OQ-T32-1']) {
        expect(header.toLowerCase(), `the header no longer says: ${phrase}`)
          .toContain(phrase.toLowerCase());
      }
    });
  });
});
