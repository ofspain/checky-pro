import type { LocalSession } from '../api/crossTabRenewal';

/**
 * The tab's access token.
 *
 * **This is the one place in this package where a module-level `let` is the requirement rather than a smell.**
 * T19's and T20's suites scan their own production files for module-level mutable state and fail on a hit; this
 * file is what they are protecting, and the variable below is task 21's entire deliverable. Do not move it into
 * storage, a store, a React context or a second module, and do not copy it anywhere - each of those is a thing
 * sign-out would then have to find and clear, and a thing an attacker would then have a second route to.
 *
 * L3 says access tokens are never written to storage, logs or analytics, and L16's sessionStorage carve-out is
 * exhaustive - `state`, `nonce`, `code_verifier`, the return target and mid-task form input - with no token on
 * it. So the token lives for exactly as long as this module instance does: one tab, until it is reloaded or
 * closed. A reload therefore ends the session, which is not a defect but the point. Silent re-authentication
 * (R21, L18) is what recovers from it, on the SAS session cookie the browser still holds.
 *
 * The shape is not this module's to choose. `LocalSession` was written in task 6 and `SessionTokens` in task 5,
 * both saying the session is "supplied by the auth module", and both are consumed by built, tested code. This
 * module is that supply; the type is imported rather than restated so the two cannot drift.
 */

/** The one and only copy. */
let held: string | null = null;

/**
 * The message `renew()` rejects with.
 *
 * Fixed, and carrying nothing: no token, no status, no server detail. It travels to `onRenewalFailed`
 * (`sessionBoundary.ts:70-77`), which task 33 will route to clean re-auth, and L4 forbids a visitor learning
 * which step failed by any route.
 */
const RENEWAL_NOT_BUILT = 'Silent re-authentication is not built; this tab cannot renew its session.';

/**
 * The tab's session, as the client boundary and the cross-tab renewal consume it.
 *
 * `renew()` is a SEAM, in the same sense `codeExchange.ts` was one from task 17 until task 20 filled it. Silent
 * re-authentication - re-opening `/oauth2/authorize` on the existing SAS session cookie - is the model the owner
 * chose, and NO TASK IN `tasks.md` BUILDS IT. Rejecting states that truth and keeps the failure defined: the
 * boundary catches a rejected renewal, hands each waiting call its original 401 and runs `onRenewalFailed` once
 * (`sessionBoundary.ts:120-124`). Resolving instead - with a stale token, or a fabricated one - would make every
 * 401 look recoverable and hide the gap behind a retry that cannot work.
 */
export const accessToken: LocalSession = {
  current() {
    return held;
  },

  /**
   * Takes a token renewed by a sibling tab (L18). It replaces whatever is held, unconditionally: the last writer
   * wins, which is safe because `current()` is read when each request is built and never cached, so the token in
   * flight is always the newest one.
   */
  adopt(token: string) {
    held = token;
  },

  renew() {
    // `Promise.reject` rather than a throw, so the rejection is the function's value in every caller's hands -
    // including one that forgets to await it - and there is no synchronous throw for the boundary to miss.
    return Promise.reject(new Error(RENEWAL_NOT_BUILT));
  },
};

/**
 * Retains the token a completed exchange returned.
 *
 * The same write as `adopt`, under the name its caller means. The callback screen is not adopting a sibling's
 * token, and a reader following the sign-in path should not have to translate.
 *
 * Nothing validates the string here. The exchange already refused a response whose `access_token` was missing,
 * empty or not a string (`codeExchange.ts:74-81`), and re-checking at the holder would be a second opinion with
 * no new information and a second place to get it wrong.
 */
export function retainAccessToken(token: string): void {
  held = token;
}

/**
 * Drops the held token.
 *
 * Idempotent, because the discard path calls it on failures that happen before any exchange - a callback with no
 * `state` never reaches one. It exists because `LocalSession` cannot express it: `adopt` takes a string, not
 * `string | null`, and widening that interface would edit code tasks 5 and 6 are built on.
 *
 * The reason the discard path clears rather than just declining to set: a tab that has signed in successfully is
 * holding a token, and a SECOND sign-in attempt in that tab that is then discarded would otherwise leave the old
 * token held - a half-authenticated tab, which R22 forbids.
 */
export function clearAccessToken(): void {
  held = null;
}
