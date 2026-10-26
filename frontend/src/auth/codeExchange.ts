/**
 * The authorization-code exchange.
 *
 * **Task 20 implements the transport.** This seam exists so that the checks around it are testable facts rather
 * than claims: task 17 needed a real call site to watch in order to prove a discarded callback does NOT exchange,
 * and task 19 needs the exchange to YIELD something in order to have an ID token to validate. Without the seam
 * the assertions would pass against an empty file, and would keep passing once task 20 wired an exchange that
 * fired unconditionally.
 *
 * The grant type is expected to GROW - task 20 needs at least the stored code verifier, and probably the redirect
 * URI - and so is the response. Callers and tests must not depend on the exact shape of either; the suites assert
 * calls with `objectContaining` for exactly this reason.
 */
export type AuthorizationCodeGrant = {
  code: string;
  state: string;
};

/**
 * As much of the token endpoint's response as the SPA uses.
 *
 * Only the ID token today, because only task 19's nonce check consumes it. Task 20 adds the access token, task 21
 * decides where it lives and task 22 proves it reaches no storage. Measured against the running auth service, the
 * real response carries `access_token`, `expires_in`, `id_token`, `scope` and `token_type` - and no
 * `refresh_token`, which is the empirical basis for L3's silent-re-authentication model.
 */
export type TokenResponse = {
  idToken: string;
};

/**
 * Rejects until task 20 fills it in.
 *
 * A seam that must hand back an ID token cannot honestly resolve while there is no transport behind it: the only
 * way to resolve would be to fabricate a token, and fabricated values in production source are how a check gets
 * proven against something that will never exist. Rejecting states the truth - the exchange is not built.
 *
 * The caller already routes a rejection to the discard path, so a visitor who completes sign-in now returns to
 * the sign-in start with neutral copy instead of waiting on the callback screen forever. That is a change from
 * task 17's choice to hold, and it is the more truthful of the two while the flow genuinely cannot complete.
 * Task 20 reverts it by returning a real response.
 */
export async function exchangeAuthorizationCode(_grant: AuthorizationCodeGrant): Promise<TokenResponse> {
  throw new Error('The authorization-code exchange is task 20; there is no transport behind this seam yet.');
}
