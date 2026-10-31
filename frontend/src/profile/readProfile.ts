import type { OidcApi } from '../api/client';
import type { SessionTokens } from '../api/sessionBoundary';

/**
 * The profile read. R20's first clause, and the only sanctioned way the SPA learns who the visitor is.
 *
 * R20 exists because a token's claims are whatever the issuer put there for its own purposes, not a user
 * record. `contracts/api/token-claims.md` makes that concrete: the access token's `email_verified` reflects
 * which scopes were authorized for one request, not whether anybody verified an email, and on the API-key
 * path it is hardcoded `false`. Reading profile data from a token the client happens to be holding is
 * therefore not a shortcut to the same answer - it is a different and wrong answer that looks right.
 *
 * `/userinfo` returns only `{"sub"}` on the running auth service today. That is not this module's defect and
 * not a reason to read the token instead: it is `spec/auth-service/design.md` O6, measured 2026-10-31 with a
 * real interactive token carrying `scope: ["openid","profile","email"]`. Spring Authorization Server answers
 * with `sub` alone unless an OIDC user-info mapper is configured, and auth-service configures none. This
 * reader is written against the shape the contract declares, so closing O6 needs no change here.
 */

/** Exactly the contract's `UserInfoResponse`. Every property but `sub` is optional, because the live server
 * sends nothing else - absence is a normal answer here and never an error (T30 AC9). */
export type Profile = {
  sub: string;
  email?: string;
  email_verified?: boolean;
  name?: string;
  given_name?: string;
  family_name?: string;
  preferred_username?: string;
  picture?: string;
};

/** Why a read did not produce a profile. Three cases, and none of them carries a cause the visitor can see. */
export type ReadProfileFailure = 'no-session' | 'unauthorized' | 'unavailable';

/**
 * Neutral by construction. The message is a fixed string per failure and never interpolates anything from the
 * response - which matters more here than it looks, because the live 401 carries no body at all and puts its
 * detail in a `WWW-Authenticate` header reading `error_description="An error occurred while attempting to
 * decode the Jwt: Malformed token"`. A rule written about response bodies would have let that through. This
 * class cannot carry it: there is nowhere to put it.
 */
export class ReadProfileError extends Error {
  readonly failure: ReadProfileFailure;

  constructor(failure: ReadProfileFailure) {
    super(FAILURE_MESSAGE[failure]);
    this.name = 'ReadProfileError';
    this.failure = failure;
  }
}

const FAILURE_MESSAGE: Record<ReadProfileFailure, string> = {
  'no-session': 'No session.',
  unauthorized: 'Not authorized.',
  unavailable: 'Profile unavailable.',
};

/**
 * Reads the profile, or rejects with a `ReadProfileError`.
 *
 * `tokens` is taken separately from `api` because the boundary does not expose `current()` - it holds
 * `renew()` and `middleware()` only - and because reaching into `auth/accessToken.ts` from here would make
 * this module own the token's lifetime, which it does not. Both arrive as parameters rather than being
 * constructed, so the composition root stays exactly as unwired as it already is (T30-D4).
 */
export async function readProfile(api: OidcApi, tokens: Pick<SessionTokens, 'current'>): Promise<Profile> {
  // No token, no request. Not an optimisation - a correctness fix for something measured.
  //
  // A bearer-less GET /userinfo does not answer 401. It answers `302 Found, Location: /login`, with a
  // `Set-Cookie: JSESSIONID`, so an unauthenticated read would also start a SAS session as a side effect. And
  // `fetch` follows redirects by default, so the call would come back `200 OK, text/html` carrying 1103 bytes
  // of the login page - which openapi-fetch would then hand to a caller expecting a profile, on a success
  // status. There is no 401 anywhere in that sequence for the boundary's renewal to act on.
  if (tokens.current() === null) throw new ReadProfileError('no-session');

  const { data, response } = await api.GET('/userinfo', {
    // The second half of the same measurement, and a security option rather than a preference: without it a
    // redirect to the login page is followed and arrives as a successful response.
    redirect: 'manual',
  });

  // Checked before `data`, and before the status, because a followed or opaque redirect can present as
  // success. A 3xx here means the session is gone, whatever the body looks like.
  if (response.redirected || (response.status >= 300 && response.status < 400) || response.type === 'opaqueredirect') {
    throw new ReadProfileError('no-session');
  }
  // 401 only after the boundary has already had its single-flight renewal and re-send: a 401 reaching here is
  // the final one, so the session is genuinely unusable rather than merely stale.
  if (response.status === 401 || response.status === 403) throw new ReadProfileError('unauthorized');
  // Everything else that is not a profile, collapsed into one answer on purpose: the visitor learns that it
  // did not work and nothing about why (L4).
  if (!response.ok || data === undefined || typeof data.sub !== 'string' || data.sub === '') {
    throw new ReadProfileError('unavailable');
  }

  return data;
}
