import type { oidcComponents } from '@themistra/api-client';
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

/**
 * The contract's `UserInfoResponse`, DERIVED rather than restated.
 *
 * It was hand-written first, and the hand-written version happened to match. Kimi's phase 8 is right that
 * matching today is not the property worth having: a contract that gained `middle_name`, or changed `picture`
 * to an object, would leave a hand-written copy compiling and silently wrong. Phase 4 froze this as F10 and
 * the first draft quietly did not honour it.
 *
 * Every property but `sub` is optional in the schema, because the live server sends nothing else - absence is
 * a normal answer here and never an error (AC9).
 */
export type Profile = oidcComponents['schemas']['UserInfoResponse'];

/** Why a read did not produce a profile. Three cases, and none of them carries a cause the visitor can see. */
export type ReadProfileFailure = 'no-session' | 'unauthorized' | 'unavailable';

/**
 * Neutral by construction. The message is a fixed string per failure and never interpolates anything from the
 * response - which matters more here than it looks, because the live 401 carries no body at all and puts its
 * detail in a `WWW-Authenticate` header reading `error_description="An error occurred while attempting to
 * decode the Jwt: Malformed token"`. A rule written about response bodies would have let that through. This
 * class cannot carry it: there is nowhere to put it.
 *
 * **`message` IS INTERNAL AND MUST NOT BE RENDERED.** It is English, it is not in the catalogue, and L11
 * requires every user-facing string to be. A screen showing a failed profile read maps `failure` - the three
 * values are a closed set for exactly this - onto a catalogue key of its own. Kimi's phase 8 raised this as a
 * trap for the first consumer, and it is a fair one: `error.message` is the obvious thing to reach for. A
 * case in T30's suite asserts these strings appear nowhere in the catalogue, so rendering one would be
 * rendering copy the catalogue does not own.
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
  //
  // `''` is guarded as well as `null`, and Kimi's phase 11 is right that it mattered while being wrong about
  // why: it reports that an empty token sends `Authorization: Bearer `, a malformed bearer. The boundary
  // attaches the header with `if (token)` (`sessionBoundary.ts:89`), so an empty string sends NO Authorization
  // header at all - which is not a malformed bearer, it is exactly the bearer-less GET described above,
  // `Set-Cookie` and all. Same conclusion, different mechanism, so the guard names both values rather than
  // leaning on truthiness.
  const token = tokens.current();
  if (token === null || token === '') throw new ReadProfileError('no-session');

  /**
   * Wrapped, because `openapi-fetch` resolves an HTTP error status but REJECTS when the underlying `fetch`
   * throws - a DNS failure, a dropped connection, a CORS refusal, an abort.
   *
   * MEASURED, after Kimi's phase 8 named it: with `fetch` throwing a `TypeError` whose message carried
   * `net::ERR_NAME_NOT_RESOLVED` and an internal hostname, that exact `TypeError` escaped `readProfile` with
   * its message and its stack intact. An internal hostname and a browser error code reaching a caller is
   * precisely what AC8 forbids, and no status check could have caught it because no response existed. This
   * was the one finding in that review with High confidence and it deserved it.
   *
   * The hostname is described rather than quoted here on purpose: `shellBuildsAndMounts` scans app source
   * for `https?://` WITHOUT stripping comments (L9, L10, R58), so writing the measured URL out would fail
   * that scan. Its conservatism is reasonable - a scanner cannot tell a comment from a constant - and
   * rephrasing is the right answer rather than loosening another task's instrument.
   */
  /**
   * `null` is in the type because it is in the runtime, and leaving it out was a live defect.
   *
   * MEASURED after Kimi's phase 11 raised it: a `200` whose body is the JSON literal `null` parses to `null`,
   * `data === undefined` is then false, and `typeof data.sub` throws
   * `TypeError: Cannot read properties of null (reading 'sub')` straight out of this function - message, stack
   * and all. That is the I1 leak again on the body path, outside the `try` that closed it on the transport
   * path.
   *
   * Kimi's own reasoning for the finding is wrong: it says `typeof null.sub` evaluates to `'undefined'`, so
   * the code is already safe and only unpinned. `typeof` suppresses a ReferenceError for an undeclared
   * identifier; property access on `null` still throws. The recommendation was right for a reason its
   * evidence did not state, which is why the cited line gets run rather than read.
   */
  let data: Profile | undefined | null;
  let response: Response;
  try {
    const answer = await api.GET('/userinfo', {
      // The second half of the same measurement, and a security option rather than a preference: without it a
      // redirect to the login page is followed and arrives as a successful response.
      redirect: 'manual',
    });
    data = answer.data;
    response = answer.response;
  } catch {
    // Discarded, not inspected: there is nothing in a transport failure a visitor should be told, and the
    // reasons are indistinguishable from the client's side anyway.
    throw new ReadProfileError('unavailable');
  }

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
  if (!response.ok || data === undefined || data === null || typeof data.sub !== 'string' || data.sub === '') {
    throw new ReadProfileError('unavailable');
  }

  return data;
}
