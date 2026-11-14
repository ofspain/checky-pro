/**
 * How long a password is, counted the way R1 counts.
 *
 * **CODE POINTS, NOT UTF-16 UNITS.** `'\u{1F44D}'.length` is 2, because JavaScript strings are UTF-16 and an
 * astral character is a surrogate pair; the spread iterates the string's code points, so the same character
 * counts once. Twelve emoji are twelve code points and twenty-four units, and R1's bound is on the former.
 * Task 46 (`passwordLengthCountsCodePoints`) is the task that makes that claim in general; this function is
 * written here, by the task that first needs a count, in the unit that task will come back and assert.
 *
 * **IT RETURNS THE COUNT, NOT A VERDICT.** A `isTwelve(password): boolean` would have been shorter and is the
 * shape phase 3 proposed; it was declined at the freeze (D6) because task 44 needs 128, task 45 needs
 * "outside 12 to 128", and task 46 needs the counting unit itself - all three would have had to rewrite it,
 * which L12 forbids this task from doing to them.
 *
 * It is total: an empty string is 0, a lone surrogate counts as the one code point the iterator yields, and a
 * very long paste costs one pass. It never throws, because R1 makes it ADVISORY - the server is the authority
 * and a client-side count that could throw would be a client-side gate.
 */
export function countCodePoints(value: string): number {
  return [...value].length;
}

/** R1's advisory bound, as the numbers rather than as a predicate (D6). The server is the authority. */
export const ADVISORY_MINIMUM_CODE_POINTS = 12;
export const ADVISORY_MAXIMUM_CODE_POINTS = 128;
