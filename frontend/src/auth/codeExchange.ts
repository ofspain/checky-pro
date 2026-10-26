/**
 * The authorization-code exchange.
 *
 * **Task 20 implements this.** T17 needs it only to exist: a callback that discards an attempt must be shown not
 * to exchange, and "did not exchange" is only a testable fact when there is a real call site to watch. Without
 * this seam the assertion would pass against an empty file, and would keep passing once task 20 wired an
 * exchange that fired unconditionally.
 *
 * The grant type is expected to GROW - task 20 needs at least the stored code verifier, and probably the
 * redirect URI. Callers and tests must not depend on its exact shape; T17's suite asserts the call with
 * `objectContaining` for exactly this reason.
 */
export type AuthorizationCodeGrant = {
  code: string;
  state: string;
};

export async function exchangeAuthorizationCode(_grant: AuthorizationCodeGrant): Promise<void> {
  // Deliberately empty until task 20. Between now and then a callback carrying a state reaches this and returns,
  // leaving the visitor on the holding screen, which is the honest state of a half-built flow.
}
