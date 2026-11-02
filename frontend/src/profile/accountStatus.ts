import type { components } from '@themistra/api-client';

/**
 * The account status enum, ALONE in a file of its own.
 *
 * This module exists for a reason that is about tests, not about code: T29's scan forbids the words `locked`,
 * `suspended` and `deleted` anywhere in production source, because naming an account state is the first step
 * towards telling an unauthenticated visitor which state an account is in (R20(b)). T31 needs a RUNTIME list
 * of exactly those states, so it holds the one allowlisted exception to that scan.
 *
 * Kimi's phase 11 A2 is why the exception lives here rather than inside `readAccount.ts`. An exception
 * bounded by a regex over its host file - "these words may appear, but only inside this one array literal" -
 * pins a textual SHAPE, and the first refactor that reformats or generates the list breaks the bound in a way
 * whose obvious repair is to widen the regex and lose the property. A module that contains nothing else needs
 * no regex: the licence is the file, and anything that could carry a state word to a visitor - a thrown
 * message, a log line, a translator call, a component - cannot be added here without being obvious in review.
 *
 * So: no imports but the contract type, no logging, no throwing, no rendering. A list and the guard over it.
 */
type Account = components['schemas']['AccountResponse'];

/**
 * The five, as a RUNTIME list - the one place T31 restates any part of the schema, and deliberately the
 * narrowest form that makes the check possible at all.
 *
 * A union is a compile-time artifact: it does not exist at runtime, so nothing can be checked against it
 * without a list. `satisfies` holds this one to the contract in the direction that matters most day to day -
 * a value that stops being a status stops compiling here - and `NoUnlistedStatus` below holds the other.
 * T30's F10 lesson stands: a hand-written copy that happens to match today is not the property worth having.
 */
export const ACCOUNT_STATUSES = [
  'PENDING_VERIFICATION',
  'ACTIVE',
  'LOCKED',
  'SUSPENDED',
  'DELETED',
] as const satisfies readonly Account['status'][];

/**
 * A status ADDED to the contract and not added above fails here, at compile time, rather than at runtime in
 * front of a visitor. `Exclude` is `never` while the list is complete, and any leftover member makes this
 * alias illegal.
 */
type AssertNever<T extends never> = T;
type NoUnlistedStatus = AssertNever<Exclude<Account['status'], (typeof ACCOUNT_STATUSES)[number]>>;

/** Fails CLOSED: a sixth value is not a status, which is the only safe direction on a field that gates access. */
export function isAccountStatus(value: unknown): value is Account['status'] {
  return typeof value === 'string' && (ACCOUNT_STATUSES as readonly string[]).includes(value);
}
