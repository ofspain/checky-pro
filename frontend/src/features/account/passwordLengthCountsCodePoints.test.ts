/// <reference types="node" />
/**
 * `passwordLengthCountsCodePoints` (package.md §8) — task 46.
 *
 * **THIS TASK OWNS THREE WORDS OF R1**: *"Unicode code points"*. Tasks 43, 44 and 45 built the check, both
 * its bounds and the guidance that explains them, and each of them measured the counting unit only where it
 * happened to travel through a screen. `passwordLength.ts` has named this task in its own header since task
 * 43 wrote it: *"this function is written here, by the task that first needs a count, in the unit that task
 * will come back and assert."*
 *
 * **WHAT THIS FILE IS, AND WHAT MAKES IT DIFFERENT FROM EVERY OTHER SUITE HERE.** `countCodePoints` is one
 * expression - `[...value].length` - with no branches, no state and no environment. It is the first suite in
 * this package with nothing to arrange: no DOM, no stubbed global, no router, no transport. That costs it
 * the instrument this package leans on everywhere else, a control proving a query can find what it looks
 * for, because there is no query. What replaces it is two controls on the TABLE and the SOURCE:
 *
 *   1. A table whose rows all agree in both units measures nothing about the unit. Task 44 learnt this with
 *      its own table and task 45 with its guidance table, and the control below says so in three clauses.
 *   2. An expectation computed with `[...s].length` is the implementation restated. The self-scan forbids it
 *      in the table region, so every number in this file is a Unicode fact stated as a literal.
 *
 * **THE UNIT HAS A WRONG NEIGHBOUR ON EACH SIDE.** UTF-16 units are SMALLER than code points for astral
 * input - the bug this package has chased for three tasks. Grapheme clusters are LARGER: a flag is two
 * regional indicators, a family emoji is three people and two joiners. R1 names the middle one, so both
 * neighbours are wrong, and before this task only the small one had ever been pinned.
 */
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { describe, expect, it } from 'vitest';
import {
  ADVISORY_MAXIMUM_CODE_POINTS,
  ADVISORY_MINIMUM_CODE_POINTS,
  countCodePoints,
} from './passwordLength';
import * as passwordLength from './passwordLength';

const srcDir = dirname(dirname(dirname(expect.getState().testPath as string)));
const read = (relativePath: string) => readFile(join(srcDir, relativePath), 'utf8');
const withoutComments = (code: string) =>
  code.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');

/**
 * Every number here is a LITERAL, and that is the whole design of this file.
 *
 * The implementation is `[...value].length`. An expected count computed the same way would assert that the
 * function equals itself, which is true of every function and is what the self-scan below exists to forbid.
 * So each row states two Unicode facts - how many code points the string has, and how many UTF-16 units -
 * and the case asserts both against the same string. Stating only the first would halve the claim: the
 * disagreement between the two numbers IS the subject of this task.
 *
 * Every number was measured on this host before it was written down, because task 44 carried `512` through
 * two artifacts and an adversarial review on the strength of everyone's intuition. A thumbs-up is one code
 * point and TWO units, so 128 of them are 256.
 */
const CASES: ReadonlyArray<{ name: string; input: string; codePoints: number; utf16Units: number }> = [
  { name: 'the empty string', input: '', codePoints: 0, utf16Units: 0 },
  { name: 'twelve ASCII characters, the lower bound', input: 'a'.repeat(12), codePoints: 12, utf16Units: 12 },
  { name: '128 ASCII characters, the upper bound', input: 'a'.repeat(128), codePoints: 128, utf16Units: 128 },
  // THE ASTRAL ROWS, where the two units part company. One character, two units: a surrogate pair.
  { name: 'one astral character', input: '\u{1F44D}', codePoints: 1, utf16Units: 2 },
  {
    name: 'eleven astral code points, one short of the lower bound',
    input: '\u{1F44D}'.repeat(11), codePoints: 11, utf16Units: 22,
  },
  {
    name: 'twelve astral code points, the lower bound in the other unit',
    input: '\u{1F44D}'.repeat(12), codePoints: 12, utf16Units: 24,
  },
  {
    name: '128 astral code points, the upper bound in the other unit',
    input: '\u{1F44D}'.repeat(128), codePoints: 128, utf16Units: 256,
  },
  // THE OTHER NEIGHBOUR: one PERCEIVED character, several code points. R1 counts these as 2, 2 and 5, and a
  // visitor would call every one of them "one character" - which is why task 45's copy says "characters"
  // and accepts the imprecision deliberately rather than teaching the term.
  { name: 'a skin-toned thumbs-up', input: '\u{1F44D}\u{1F3FD}', codePoints: 2, utf16Units: 4 },
  { name: 'a flag, which is two regional indicators', input: '\u{1F1EC}\u{1F1E7}', codePoints: 2, utf16Units: 4 },
  {
    name: 'a family emoji, three people joined by two zero-width joiners',
    input: '\u{1F469}‍\u{1F469}‍\u{1F467}', codePoints: 5, utf16Units: 8,
  },
  // THE SAME LETTER, SPELLED TWO WAYS (OQ-T46-2). Composed is one code point; decomposed is a letter and a
  // combining accent, so it is two. Nothing in this client normalises, so the same password typed on two
  // keyboards can count 1 or 2 here. That is pinned as it BEHAVES, not decided: whether R1 means to be
  // normalisation-sensitive is the owner's question, and this row is what a normalising change would break.
  { name: 'a composed accent', input: 'é', codePoints: 1, utf16Units: 1 },
  { name: 'a decomposed accent', input: 'é', codePoints: 2, utf16Units: 2 },
  // LONE SURROGATES. The iterator yields the half pair as one code point rather than throwing, which is
  // what `passwordLength.ts`'s header means by total.
  { name: 'a lone high surrogate', input: '\uD83D', codePoints: 1, utf16Units: 1 },
  { name: 'a lone low surrogate', input: '\uDC4D', codePoints: 1, utf16Units: 1 },
  { name: 'a lone surrogate between two letters', input: 'a\uD83Db', codePoints: 3, utf16Units: 3 },
];

describe('AC1/AC3 the count is in code points, and every expectation is a Unicode fact', () => {
  it.each(CASES)('counts $name as code points, not UTF-16 units', ({ input, codePoints, utf16Units }) => {
    expect(countCodePoints(input), 'the count disagrees with the Unicode fact in the row').toBe(codePoints);
    // THE SECOND NUMBER IS NOT DECORATION. It is what makes the row a claim about the UNIT rather than about
    // an arbitrary integer: without it, a row saying 11 would be satisfied by any function that happens to
    // return 11, including `value.length` for an eleven-unit string.
    expect(input.length, 'the UTF-16 count in the row is wrong, so the row proves nothing').toBe(utf16Units);
  });

  it('CONTROL: the table can tell the two units apart', () => {
    // A TABLE OF ROWS THAT AGREE IN BOTH UNITS MEASURES NOTHING ABOUT THE UNIT - it measures its own row
    // count. Every ASCII row above is satisfied by `value.length`, so if the astral rows were ever deleted
    // as duplicates this file would go on passing while proving the opposite of its name. Task 44 pinned
    // both ends of its table for this reason and task 45 pinned the row that discriminates; this is the
    // same control, stated as three properties of the table rather than as cases that can be removed.
    expect(CASES.filter((row) => row.codePoints !== row.utf16Units),
      'no row distinguishes code points from UTF-16 units').not.toEqual([]);
    // THE LOWER CROSSING: fewer code points than the minimum, but enough UTF-16 units to pass it. This is
    // the one direction of the bug that is SILENT - `value.length` would accept the password and say
    // nothing, where the other direction at least shows a visitor a message they can argue with.
    expect(CASES.filter((row) =>
      row.codePoints < ADVISORY_MINIMUM_CODE_POINTS && row.utf16Units >= ADVISORY_MINIMUM_CODE_POINTS),
    'no row crosses the minimum between the two units').not.toEqual([]);
    // THE UPPER CROSSING: inside the maximum by code points, outside it by units.
    expect(CASES.filter((row) =>
      row.codePoints <= ADVISORY_MAXIMUM_CODE_POINTS && row.utf16Units > ADVISORY_MAXIMUM_CODE_POINTS),
    'no row crosses the maximum between the two units').not.toEqual([]);
  });

  it('CONTROL: no expectation in this file is computed by the function under test', async () => {
    // THE CIRCULARITY GUARD. `countCodePoints` is `[...value].length`, so a row whose expected count were
    // written as `[...input].length` would assert that the function equals itself - true of every function,
    // and the most comfortable way to ship a suite that proves nothing. The scan is aimed at the TABLE
    // REGION rather than the whole file, because the source scans below legitimately mention these shapes.
    const self = withoutComments(await read('features/account/passwordLengthCountsCodePoints.test.ts'));
    const table = /const CASES[\s\S]*?\n\];/.exec(self)?.[0];
    expect(table, 'the table region could not be found, so this control measured nothing').toBeDefined();
    for (const circular of [/\[\.\.\./, /Array\.from\s*\(/, /countCodePoints\s*\(/]) {
      expect(table, `an expectation in the table is computed with ${circular.source}`).not.toMatch(circular);
    }
  });
});

describe('AC2 the two units disagree across both bounds', () => {
  it('the lower bound: eleven astral code points are twenty-two units, so `.length` would ACCEPT what R1 excludes', () => {
    const password = '\u{1F44D}'.repeat(11);
    expect(countCodePoints(password)).toBe(11);
    expect(password.length).toBe(22);
    // The two verdicts, spelled out. R1 excludes this password; a UTF-16 count admits it.
    expect(countCodePoints(password) >= ADVISORY_MINIMUM_CODE_POINTS, 'R1 would admit eleven code points').toBe(false);
    expect(password.length >= ADVISORY_MINIMUM_CODE_POINTS, 'the wrong unit would have excluded it too').toBe(true);
  });

  it('the upper bound: 128 astral code points are 256 units, so `.length` would REJECT what R1 allows', () => {
    const password = '\u{1F44D}'.repeat(128);
    expect(countCodePoints(password)).toBe(128);
    expect(password.length).toBe(256);
    expect(countCodePoints(password) <= ADVISORY_MAXIMUM_CODE_POINTS, 'R1 would reject 128 code points').toBe(true);
    expect(password.length <= ADVISORY_MAXIMUM_CODE_POINTS, 'the wrong unit would have allowed it too').toBe(false);
  });
});

describe('AC5 a grapheme cluster is not one code point', () => {
  it('counts the parts, not the picture', () => {
    // THE UNIT R1 CHOSE, NOT THE UNIT A VISITOR WOULD GUESS. Each of these is one thing on screen and more
    // than one code point underneath, so R1's allowance is smaller than it looks for anyone whose password
    // is made of them. Task 45's guidance says "characters" to the visitor and the catalogue comment
    // records that as a deliberate imprecision; this case is the other half of that decision, written down
    // where the unit actually lives.
    expect(countCodePoints('\u{1F44D}\u{1F3FD}'), 'a skin tone modifier is its own code point').toBe(2);
    expect(countCodePoints('\u{1F1EC}\u{1F1E7}'), 'a flag is two regional indicators').toBe(2);
    expect(countCodePoints('\u{1F469}‍\u{1F469}‍\u{1F467}'),
      'a ZWJ sequence counts every person and every joiner').toBe(5);
    // And the contrast that makes the point: one picture, five code points, eight units - three different
    // answers to "how long is this", of which R1 names the middle one.
    expect('\u{1F469}‍\u{1F469}‍\u{1F467}'.length).toBe(8);
  });
});

describe('AC4 the count is total, and it is not additive', () => {
  it('returns a number for every string, and throws for none', () => {
    for (const { input } of CASES) {
      expect(() => countCodePoints(input)).not.toThrow();
      expect(Number.isInteger(countCodePoints(input)), 'the count is not a whole number').toBe(true);
    }
    // A LONG VALUE COSTS ONE PASS. R1 makes the check advisory, so a count that could hang on a paste would
    // be a client-side gate by another name.
    expect(countCodePoints('a'.repeat(100_000))).toBe(100_000);
  });

  it('is NOT additive across a concatenation, because a surrogate pair can be split', () => {
    // 1 + 1 = 1. Each half of a surrogate pair counts as one code point on its own; joined, they are ONE
    // character. No other row implies this, and any future implementation that counted incrementally - over
    // a chunked paste, a streamed value, two fields joined - would get it wrong while passing every other
    // case in this file. Phase 3 called this case unnecessary on the grounds that the function takes one
    // string; the arithmetic is why it is here anyway.
    const high = '\uD83D';
    const low = '\uDC4D';
    expect(countCodePoints(high)).toBe(1);
    expect(countCodePoints(low)).toBe(1);
    expect(countCodePoints(high + low), 'the halves were counted separately').toBe(1);
    expect(high + low).toBe('\u{1F44D}');
  });
});

describe('AC6 the module depends on its argument alone', () => {
  const ENVIRONMENT = [
    /localStorage/, /sessionStorage/, /\bdocument\b/, /\bwindow\b/, /\bfetch\s*\(/, /\bDate\b/,
    /setTimeout/, /setInterval/, /console\./, /\bcrypto\b/, /\bprocess\b/, /^import\s/m,
  ];

  it('reaches for no storage, no DOM, no clock and no global, and imports nothing', async () => {
    // ASSERTED AT THE SOURCE, NOT BEHAVIOURALLY, and the reason is a bug this package shipped: task 42
    // asserted "writes no storage" with a spy on a storage INSTANCE, which does not intercept in jsdom, so
    // the claim was vacuous from the day it was written and survived seven tasks because it was NEGATIVE.
    // A pure function's purity has the same shape - nothing happens, and nothing happening is exactly what
    // a broken instrument also reports. The file text cannot be vacuous in that way.
    const module = withoutComments(await read('features/account/passwordLength.ts'));
    for (const reach of ENVIRONMENT) {
      expect(module, `passwordLength.ts reaches for ${reach.source}`).not.toMatch(reach);
    }
  });

  it('CONTROL: the environment scan fires on a module that DOES reach for one', () => {
    // Without this, the case above passes against a scan aimed at nothing - a list of patterns none of
    // which could ever match, which is the second way a negative assertion lies.
    const impure = [
      "import { thing } from './thing';",
      'export function impure(value: string): number {',
      '  sessionStorage.setItem("count", String(value.length));',
      '  console.log(Date.now(), document.title);',
      '  return value.length;',
      '}',
    ].join('\n');
    const fired = ENVIRONMENT.filter((reach) => reach.test(impure));
    expect(fired.length, 'the scan cannot see storage, a clock, a console, the DOM or an import')
      .toBeGreaterThanOrEqual(5);
  });
});

describe('AC7 the module publishes what its consumers use, and the bounds are R1\'s', () => {
  it('publishes exactly three names, and the bounds are 12 and 128', () => {
    // THE KEY SET, SORTED, AND THE ORDER IS DELIBERATELY NOT THE CLAIM. Measured: under Vite's transform
    // `Object.keys` on this namespace returns DECLARATION order, but a real ES module namespace exotic
    // object has SORTED own keys, so an order-sensitive assertion would pass here for a reason that has
    // nothing to do with this module and would break under a runner that does not transform. Phase 3
    // proposed the ordered form; this is the same claim without the bundler in it.
    expect([...Object.keys(passwordLength)].sort()).toEqual([
      'ADVISORY_MAXIMUM_CODE_POINTS',
      'ADVISORY_MINIMUM_CODE_POINTS',
      'countCodePoints',
    ]);
    expect(ADVISORY_MINIMUM_CODE_POINTS, "R1's lower bound moved").toBe(12);
    expect(ADVISORY_MAXIMUM_CODE_POINTS, "R1's upper bound moved").toBe(128);
    expect(typeof countCodePoints).toBe('function');
  });
});
