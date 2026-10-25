import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import {
  PKCE_CODE_CHALLENGE_METHOD,
  PKCE_VERIFIER_MAX_LENGTH,
  PKCE_VERIFIER_MIN_LENGTH,
  deriveCodeChallenge,
  generateCodeVerifier,
  generatePkcePair,
  isValidCodeVerifier,
} from './pkce';

/**
 * T14 - the code verifier is 43-128 characters from the RFC 7636 unreserved set, and its S256 challenge is derived
 * correctly (R6, L1). Task 15 owns the authorize request; nothing here builds a URL.
 *
 * The one assertion that cannot be satisfied by a wrong implementation is RFC 7636 Appendix B's published vector
 * (AC3). Everything else either feeds the published validator input our generator never produces, or measures a
 * property of a large sample.
 */

/** RFC 7636 Appendix B, verbatim. Verified computationally in phase 1 rather than quoted from memory. */
const RFC_7636_APPENDIX_B = {
  verifier: 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk',
  challenge: 'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM',
} as const;

/** base64url's alphabet - what the generator can emit. The unreserved set also holds `.` and `~`; see AC1. */
const BASE64URL_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';

/**
 * 32 bytes is 256 bits, but 43 base64url characters carry 258, so the final character encodes only 4 bits and can
 * take exactly 16 of the 64 values. Any uniformity assertion covering position 42 is permanently red - measured in
 * phase 4, where the review's suggested test failed 40 runs out of 40 for this reason.
 */
const UNIFORM_PREFIX_LENGTH = 42;
const TAIL_VALUE_COUNT = 16;

/** One sample, shared by every statistical case, so the suite generates it once. */
const SAMPLE_SIZE = 2000;

/**
 * Chi-square at 63 degrees of freedom, p ~ 1e-6. Measured in phase 4: this scheme produces 39.7-97.8 over 200 runs,
 * while drawing from the full 66-character set with `% 66` produces 540-744. The threshold sits above the former and
 * far below the latter, so the case is neither flaky nor blind.
 *
 * Honest limit: the mapping is btoa's, not ours, so this validates no arithmetic written here. It is a regression
 * guard against a future reimplementation that introduces bias, and a record of the entropy actually achieved.
 */
const CHI_SQUARE_THRESHOLD = 139;

/**
 * Every name whose presence in the module would be a defect - the verifier must never reach a URL, a store or a log.
 *
 * Matched as whole identifiers, not substrings: a bare `includes('URL')` would flag a helper renamed to
 * `base64URLFromBytes`, which has nothing to do with the global. `\bURL\b` keeps the reach - it still matches
 * `new URL(`, `URL.createObjectURL` and `globalThis.URL` - and loses the trap.
 */
const FORBIDDEN_IN_SOURCE: ReadonlyArray<readonly [string, RegExp]> = [
  ['Math.random', /\bMath\.random\b/],
  ['Date.now', /\bDate\.now\b/],
  ['localStorage', /\blocalStorage\b/],
  ['sessionStorage', /\bsessionStorage\b/],
  ['document.cookie', /\bdocument\.cookie\b/],
  ['console', /\bconsole\b/],
  ['Buffer', /\bBuffer\b/],
  ['an http(s) URL', /https?/],
  ['URL', /\bURL\b/],
  ['location', /\blocation\b/],
  ['history', /\bhistory\b/],
  ['encodeURIComponent', /\bencodeURIComponent\b/],
  ['fetch', /\bfetch\b/],
  ['XMLHttpRequest', /\bXMLHttpRequest\b/],
];

/**
 * Comments stripped so the module may quote the rules it obeys - its doc comments name Buffer, sessionStorage and
 * plain on purpose, in explaining why each is absent.
 *
 * A scanner rather than a regex, because BOTH regex forms have a demonstrated hole: `//.*$` eats everything after a
 * `//` inside a string literal (phase 7, S1) and the block form eats from a `/*` inside a string literal to the
 * next real terminator (Kimi 11.1). Either hides a forbidden name that follows. This tracks string and template
 * state, so a comment delimiter inside a literal is just text.
 *
 * Residual limit, stated rather than implied: an UNESCAPED comment delimiter inside a regular-expression character
 * class would still be read as a comment start, because telling a regex literal from a division needs real parse
 * context. Not reachable in this module, and the negative control below covers every form that is.
 */
function codeWithoutComments(source: string): string {
  type Mode = 'code' | 'line' | 'block' | "'" | '"' | '`';
  let mode: Mode = 'code';
  let out = '';
  let index = 0;
  while (index < source.length) {
    const character = source[index];
    const following = source[index + 1];
    if (mode === 'code') {
      if (character === '/' && following === '/') {
        mode = 'line';
        index += 2;
      } else if (character === '/' && following === '*') {
        mode = 'block';
        index += 2;
      } else {
        if (character === "'" || character === '"' || character === '`') mode = character;
        out += character;
        index += 1;
      }
    } else if (mode === 'line') {
      if (character === '\n') {
        mode = 'code';
        out += character;
      }
      index += 1;
    } else if (mode === 'block') {
      if (character === '*' && following === '/') {
        mode = 'code';
        index += 2;
      } else {
        index += 1;
      }
    } else if (character === '\\') {
      out += character + (following ?? '');
      index += 2;
    } else {
      if (character === mode) mode = 'code';
      out += character;
      index += 1;
    }
  }
  return out;
}

function chiSquare(counts: readonly number[], total: number): number {
  const expected = total / counts.length;
  return counts.reduce((sum, count) => sum + (count - expected) ** 2 / expected, 0);
}

describe('pkceVerifierMeetsRfc7636', () => {
  let sample: string[];
  let moduleSource: string;

  beforeAll(async () => {
    // Before the sample, not after: generating it calls getRandomValues, so a missing Web Crypto would throw here
    // and the readable environment case below would never run (Kimi 8.1).
    if (typeof crypto?.getRandomValues !== 'function' || typeof crypto?.subtle?.digest !== 'function') {
      throw new Error(
        'This suite needs Web Crypto: crypto.getRandomValues and crypto.subtle.digest must both be functions',
      );
    }
    sample = Array.from({ length: SAMPLE_SIZE }, () => generateCodeVerifier());
    const here = dirname(expect.getState().testPath as string);
    moduleSource = await readFile(join(here, 'pkce.ts'), 'utf8');
  });

  describe('the environment this test needs', () => {
    // A future jsdom or Vitest change should produce one readable failure, not a cryptic error inside a derivation.
    it('exposes the two Web Crypto calls the module depends on', () => {
      expect(typeof crypto.getRandomValues, 'crypto.getRandomValues is missing').toBe('function');
      expect(typeof crypto.subtle?.digest, 'crypto.subtle.digest is missing').toBe('function');
    });
  });

  describe('the S256 derivation (AC3, AC4, AC5)', () => {
    it("matches RFC 7636 Appendix B's published vector", async () => {
      await expect(deriveCodeChallenge(RFC_7636_APPENDIX_B.verifier)).resolves.toBe(RFC_7636_APPENDIX_B.challenge);
    });

    it('is base64url and not base64, so it is URL-safe unmodified (AC4)', async () => {
      const challenge = await deriveCodeChallenge(RFC_7636_APPENDIX_B.verifier);
      expect(challenge).not.toMatch(/[=+/]/);
      expect(challenge).toMatch(/^[A-Za-z0-9\-_]+$/);
    });

    it('is a hash and not the verifier itself, which is what plain would send (AC5)', async () => {
      const verifier = generateCodeVerifier();
      await expect(deriveCodeChallenge(verifier)).resolves.not.toBe(verifier);
    });

    it('changes completely when one character of the verifier changes (AC5)', async () => {
      const { verifier, challenge } = RFC_7636_APPENDIX_B;
      const altered = `${verifier.slice(0, -1)}${verifier.at(-1) === 'k' ? 'j' : 'k'}`;
      expect(altered).not.toBe(verifier);
      const alteredChallenge = await deriveCodeChallenge(altered);
      expect(alteredChallenge).not.toBe(challenge);
      // A truncating or prefix-only hash would still agree on most characters.
      const shared = [...alteredChallenge].filter((character, index) => character === challenge[index]).length;
      expect(shared).toBeLessThan(challenge.length / 2);
    });

    it('publishes S256 as the method, so no caller can spell it otherwise (AC5)', async () => {
      expect(PKCE_CODE_CHALLENGE_METHOD).toBe('S256');
      await expect(generatePkcePair()).resolves.toMatchObject({ method: 'S256' });
    });

    it('returns a pair whose challenge is its own verifier\'s (AC3)', async () => {
      const pair = await generatePkcePair();
      await expect(deriveCodeChallenge(pair.verifier)).resolves.toBe(pair.challenge);
    });

    // Every other derivation case uses a 43-character verifier, so a derivation that assumed 43 would pass the file
    // while the published contract is 43-128 (phase 7, S5).
    it('derives for a verifier at the other end of the legal range, 128 characters', async () => {
      const long = `${'a'.repeat(127)}~`;
      expect(long).toHaveLength(PKCE_VERIFIER_MAX_LENGTH);
      expect(isValidCodeVerifier(long)).toBe(true);
      const challenge = await deriveCodeChallenge(long);
      expect(challenge).toMatch(/^[A-Za-z0-9\-_]{43}$/);
      await expect(deriveCodeChallenge('a'.repeat(43))).resolves.not.toBe(challenge);
    });

    it('rejects an invalid verifier instead of hashing it (AC9)', async () => {
      await expect(deriveCodeChallenge('a'.repeat(42))).rejects.toThrow(TypeError);
      // The upper bound too: deriveCodeChallenge is advertised as self-validating, so it may not rely on a caller
      // having consulted isValidCodeVerifier first (Kimi 11.3).
      await expect(deriveCodeChallenge('a'.repeat(129))).rejects.toThrow(TypeError);
      await expect(deriveCodeChallenge(`${'a'.repeat(42)}+`)).rejects.toThrow(TypeError);
      await expect(deriveCodeChallenge('')).rejects.toThrow(TypeError);
    });
  });

  describe('a generated verifier (AC1, AC2)', () => {
    it('is 43 characters, inside the RFC bounds', () => {
      const verifier = generateCodeVerifier();
      expect(verifier).toHaveLength(43);
      expect(verifier.length).toBeGreaterThanOrEqual(PKCE_VERIFIER_MIN_LENGTH);
      expect(verifier.length).toBeLessThanOrEqual(PKCE_VERIFIER_MAX_LENGTH);
    });

    it('holds only unreserved characters, and the module says so of itself', () => {
      const verifier = generateCodeVerifier();
      expect(verifier).toMatch(/^[A-Za-z0-9\-._~]+$/);
      expect(isValidCodeVerifier(verifier)).toBe(true);
    });

    it('is different every time, over the whole sample (AC2)', () => {
      expect(new Set(sample).size).toBe(SAMPLE_SIZE);
    });

    // AC1's length case catches a changed byte count indirectly - 31 bytes encodes to 42 characters and 33 to 44 -
    // but nothing pinned the contract with the CSPRNG itself (Kimi 8.3).
    it('asks the platform CSPRNG for exactly 32 bytes, once', () => {
      const getRandomValues = vi.spyOn(crypto, 'getRandomValues');
      try {
        generateCodeVerifier();
        expect(getRandomValues).toHaveBeenCalledTimes(1);
        const [filled] = getRandomValues.mock.calls[0];
        expect(filled).toBeInstanceOf(Uint8Array);
        expect((filled as Uint8Array).byteLength).toBe(32);
      } finally {
        getRandomValues.mockRestore();
      }
    });

    it('gives each pair its own verifier, at the public API (Kimi 8.4)', async () => {
      const [first, second] = await Promise.all([generatePkcePair(), generatePkcePair()]);
      expect(first.verifier).not.toBe(second.verifier);
      expect(first.challenge).not.toBe(second.challenge);
    });

    it('is valid for every member of the sample (AC1)', () => {
      expect(sample.filter((verifier) => !isValidCodeVerifier(verifier))).toEqual([]);
    });
  });

  describe('the published validator, fed what the generator never produces (AC1)', () => {
    // The generator emits 43 base64url characters, so every bound and every character outside that set is
    // unreachable from our own output. Asserting the rule only over generated verifiers would pass vacuously -
    // the hole that bit T12's AC7 and T13's AC8.
    it.each([
      ['exactly the minimum, 43', 'a'.repeat(43), true],
      ['exactly the maximum, 128', 'a'.repeat(128), true],
      ['one below the minimum, 42', 'a'.repeat(42), false],
      ['one above the maximum, 129', 'a'.repeat(129), false],
      ['empty', '', false],
      ['base64 padding', `${'a'.repeat(42)}=`, false],
      ['base64 plus', `${'a'.repeat(42)}+`, false],
      ['base64 slash', `${'a'.repeat(42)}/`, false],
      ['a space', `${'a'.repeat(42)} `, false],
      ['a non-ASCII code point', `${'a'.repeat(42)}é`, false],
      ['a percent escape', `${'a'.repeat(40)}%7E`, false],
    ])('%s -> %s', (_label, candidate, expected) => {
      expect(isValidCodeVerifier(candidate)).toBe(expected);
    });

    // base64url never emits `.` or `~`, so without this case a validator written as [A-Za-z0-9-_] - base64url's
    // alphabet rather than the RFC's unreserved set - would pass every other case in this file.
    it('accepts the two unreserved characters base64url never emits', () => {
      const withDotAndTilde = `${'a'.repeat(41)}.~`;
      expect(withDotAndTilde).toHaveLength(43);
      expect(isValidCodeVerifier(withDotAndTilde)).toBe(true);
      expect(isValidCodeVerifier(`${'~'.repeat(21)}${'.'.repeat(22)}`)).toBe(true);
      // The most stressful valid input: the upper bound AND the two characters base64url omits (Kimi 11.4).
      expect(isValidCodeVerifier(`${'a'.repeat(126)}.~`)).toBe(true);
    });
  });

  describe('the randomness, measured over the sample (AC7)', () => {
    it('shows no bias across the uniform prefix', () => {
      const counts = new Map([...BASE64URL_ALPHABET].map((character) => [character, 0]));
      let total = 0;
      for (const verifier of sample) {
        for (const character of verifier.slice(0, UNIFORM_PREFIX_LENGTH)) {
          counts.set(character, (counts.get(character) ?? 0) + 1);
          total += 1;
        }
      }
      expect(total).toBe(SAMPLE_SIZE * UNIFORM_PREFIX_LENGTH);
      expect(chiSquare([...counts.values()], total)).toBeLessThan(CHI_SQUARE_THRESHOLD);
    });

    it('reaches all 64 base64url characters, so no alphabet entry is unreachable', () => {
      const seen = new Set(sample.flatMap((verifier) => [...verifier.slice(0, UNIFORM_PREFIX_LENGTH)]));
      expect([...seen].sort().join('')).toBe([...BASE64URL_ALPHABET].sort().join(''));
    });

    it('carries exactly 4 bits in the final character, which is 16 of the 64 values', () => {
      const tail = new Set(sample.map((verifier) => verifier[UNIFORM_PREFIX_LENGTH]));
      expect(tail.size).toBe(TAIL_VALUE_COUNT);
      // 4 bits means the two low bits of the encoded sextet are always zero: every 4th alphabet entry.
      const expected = [...BASE64URL_ALPHABET].filter((_character, index) => index % 4 === 0);
      expect([...tail].sort().join('')).toBe(expected.sort().join(''));
    });
  });

  describe('the module cannot leak the verifier (AC2, AC6, AC10)', () => {
    // Over the code, not the raw file: a module that only MENTIONED this in a doc comment while calling something
    // else would otherwise satisfy the assertion (phase 7, S2).
    it('draws from the platform CSPRNG', () => {
      expect(codeWithoutComments(moduleSource)).toContain('crypto.getRandomValues');
    });

    it.each(FORBIDDEN_IN_SOURCE)('never names %s', (_label, pattern) => {
      expect(codeWithoutComments(moduleSource)).not.toMatch(pattern);
    });

    // Every row above asserts an ABSENCE over a file that contains none of these names, so a broken pattern or a
    // broken stripper would be silently green. These feed the same detection logic a source that does contain each
    // one, including both comment-delimiter-inside-a-string forms that were real holes (Kimi 11.6).
    describe('the scanner itself detects what it is looking for', () => {
      it.each(FORBIDDEN_IN_SOURCE)('finds %s in a synthetic source', (label, pattern) => {
        const snippet = {
          'Math.random': 'const r = Math.random();',
          'Date.now': 'const t = Date.now();',
          localStorage: "localStorage.setItem('k', v);",
          sessionStorage: "sessionStorage.setItem('k', v);",
          'document.cookie': "document.cookie = 'v=' + v;",
          console: 'console.log(v);',
          Buffer: "Buffer.from(v, 'utf8');",
          'an http(s) URL': "const u = 'https://auth:8080/oauth2/token';",
          URL: "new URL('/x', origin);",
          location: 'location.assign(target);',
          history: "history.pushState({}, '', target);",
          encodeURIComponent: 'encodeURIComponent(v);',
          fetch: 'fetch(target);',
          XMLHttpRequest: 'new XMLHttpRequest();',
        }[label];
        expect(snippet, `no synthetic source for ${label}`).toBeDefined();
        expect(codeWithoutComments(snippet as string)).toMatch(pattern);
      });

      it.each([
        ['a // inside a string literal, same line (phase 7, S1)', "const x = 'a//b'; localStorage.setItem('k', v);"],
        ['a block delimiter inside a string literal (Kimi 11.1)', "const x = '/*'; localStorage.setItem('k', v);\n/** real */"],
        ['a line comment after real code', "localStorage.setItem('k', v); // a trailing comment"],
        ['a template literal holding //', 'const x = `a//b`; localStorage.setItem(\'k\', v);'],
      ])('is not blinded by %s', (_label, snippet) => {
        expect(codeWithoutComments(snippet)).toMatch(/\blocalStorage\b/);
      });

      it('still removes the comments it is meant to remove', () => {
        const source = [
          '/** names Buffer and sessionStorage on purpose */',
          '// and localStorage here',
          'const a = 1;',
        ].join('\n');
        const code = codeWithoutComments(source);
        expect(code).toContain('const a = 1;');
        for (const name of ['Buffer', 'sessionStorage', 'localStorage']) expect(code).not.toContain(name);
      });
    });

    it('does not flag the module\'s own base64Url helper, which is not the URL global', () => {
      expect(moduleSource).toContain('base64UrlFromBytes');
      expect(codeWithoutComments(moduleSource)).not.toMatch(/\bURL\b/);
    });

    it('runs in a browser: no Node-only global, and btoa is the encoder (AC10)', () => {
      const code = codeWithoutComments(moduleSource);
      expect(code).toContain('btoa(');
      expect(code).not.toMatch(/\bnode:|require\(|process\./);
    });

    /**
     * The scan reads text; this watches behaviour. They fail for different reasons, which is why both are here
     * (phase 7, S7).
     *
     * Every Storage method and every function on `console`, not a hand-picked few (Kimi 11.2, 11.5). The asymmetry
     * that remains is worth naming: an indexed assignment onto a Storage object is a property set, not a method
     * call, so NO method spy can see it. For that form the source scan is the authoritative guard and these spies
     * are the second opinion - not the other way round.
     */
    it('calls no Storage method and no console method while producing a pair', async () => {
      const spies = [
        ...(['setItem', 'getItem', 'removeItem', 'clear', 'key'] as const).map((method) =>
          vi.spyOn(Storage.prototype, method),
        ),
        ...Object.keys(globalThis.console)
          .filter((key) => typeof (globalThis.console as unknown as Record<string, unknown>)[key] === 'function')
          .map((key) => vi.spyOn(globalThis.console as unknown as Record<string, () => void>, key)),
      ];
      expect(spies.length, 'expected a non-trivial spy surface').toBeGreaterThan(10);
      try {
        const pair = await generatePkcePair();
        expect(pair.verifier).not.toBe('');
        for (const spy of spies) expect(spy).not.toHaveBeenCalled();
      } finally {
        for (const spy of spies) spy.mockRestore();
      }
    });
  });
});
