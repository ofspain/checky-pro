/**
 * The one fact a discarded callback carries to the sign-in screen (R8: "return the visitor to the sign-in start
 * with neutral copy").
 *
 * In memory, module-scoped, and single-read. It is NOT in session storage, because L16's carve-out is exhaustive
 * and names four items; this is not one of them. It is not in history state either, which the browser persists
 * across a reload - wrong for a notice that should die with the navigation that caused it. Taking it clears it,
 * so a refreshed sign-in page does not keep scolding the visitor.
 *
 * The union had one member until task 33. It was written as a forward-compatibility hook, not a commitment, and
 * task 33 is what took it up: a failed renewal cannot reuse `'discarded'`, because that member's sentence says
 * sign-in did not complete and for a lost session that is false. L4 does not require the reuse either - L4 makes
 * copy identical across ACCOUNT STATES and TOKEN CONDITIONS, so an anonymous prober learns nothing, and the
 * visitor's own session having ended discloses no account.
 */
export type SignInNotice = 'discarded' | 'session-ended';

/**
 * Which catalogue sentence each notice shows.
 *
 * Here and not in the screen, so the union and the table keyed by it sit in ONE module: a third member is then a
 * compile error in this file rather than a silently missing branch in a component, and a scan can license a file
 * instead of a regex over a `.tsx`. `satisfies` keeps every member mapped, and `NoUnmappedNotice` fails the build
 * from the other direction if a member is ever added without a sentence.
 */
export const NOTICE_COPY_KEY = {
  discarded: 'signIn.discarded',
  'session-ended': 'signIn.sessionEnded',
} as const satisfies Record<SignInNotice, string>;

type AssertNever<T extends never> = T;
type NoUnmappedNotice = AssertNever<Exclude<SignInNotice, keyof typeof NOTICE_COPY_KEY>>;

let pending: SignInNotice | null = null;

export function setSignInNotice(notice: SignInNotice): void {
  pending = notice;
}

/** Returns the pending notice and clears it. Returns null when there is none, which is the ordinary case. */
export function takeSignInNotice(): SignInNotice | null {
  const taken = pending;
  pending = null;
  return taken;
}
