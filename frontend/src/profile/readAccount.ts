import type { components } from '@themistra/api-client';
import type { AuthApi } from '../api/client';
import type { SessionTokens } from '../api/sessionBoundary';

/**
 * The account read. R20's second clause, and the SPA's only truthful source of account state.
 *
 * MEASURED 2026-10-31, and the measurement is the argument for the whole module: suspending an account
 * through the admin endpoint does NOT invalidate its access token. The same token then answers `200` with
 * `status: "SUSPENDED"`, and no 401 ever arrives. So for up to the token's fifteen minutes a tab can hold a
 * working session for an account the platform has suspended, and this read is the one thing that can say so.
 *
 * That is also why a token-borne shortcut would be worse here than merely wrong. The access token was minted
 * while the account was `ACTIVE` and goes on asserting that until it expires - a stale answer that looks
 * current, which is the same failure mode R20(c) exists to prevent for `email_verified`.
 *
 * `/accounts/me` is a RESOURCE-SERVER endpoint, not a protocol one, and T31 reads nothing across from T30 on
 * that account. A bearer-less call here answers a real `401`, so the session boundary's single-flight renewal
 * works exactly as designed (R21, L18) and there is no `redirect: 'manual'` and no 3xx handling in this file:
 * `/userinfo` needed those because a bearer-less call there answers `302` to the login page, which `fetch`
 * follows into a `200` carrying HTML. Carrying that across would be untestable code guarding a shape this
 * endpoint does not produce.
 */

/**
 * The contract's `AccountResponse`, DERIVED - and with its `status` union left exactly as generated.
 *
 * Kimi's phase 3 is right that this collides with reporting an unrecognised status faithfully, and the
 * collision is resolved by failing rather than by widening (G2). Widening `status` to `string` would buy this
 * one reader's honesty with every future consumer's narrowing: from then on every screen touching `status`
 * must handle an arbitrary string forever, and the exhaustive `switch` that ought to be sound becomes
 * impossible to write.
 *
 * The hazard that decides it is sharper than it looks. With a closed union, a consumer writing an exhaustive
 * `switch (account.status)` is COMPILE-TIME complete and RUNTIME incomplete - TypeScript proves all five arms
 * are handled while a sixth value walks through to whatever the default path happens to be, and the type
 * system actively reassures the author. So an unrecognised status is a failed read here, which fails CLOSED;
 * on a field that gates access that is the only safe direction.
 */
export type Account = components['schemas']['AccountResponse'];

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
export type NoUnlistedStatus = AssertNever<Exclude<Account['status'], (typeof ACCOUNT_STATUSES)[number]>>;

/** Why a read did not produce an account. Three cases, and none of them carries a cause the visitor can see. */
export type ReadAccountFailure = 'no-session' | 'unauthorized' | 'unavailable';

/**
 * Neutral by construction, and its own class rather than a rename of `ReadProfileError` (G3).
 *
 * The two readers' failures are not the same set even where the words coincide: T30's `no-session` exists
 * because a bearer-less `/userinfo` answers a redirect into an HTML page, while this one exists because an
 * unauthenticated read sets a session cookie. Same word, different reason - and renaming T30's class to a
 * shared one would edit a module T31 does not own.
 *
 * **`message` IS INTERNAL AND MUST NOT BE RENDERED.** It is English and it is not in the catalogue, which
 * L11 requires every user-facing string to be. A screen showing a failed account read maps `failure` - three
 * values, a closed set for exactly this - onto a catalogue key of its own. The message is looked up BY KEY
 * from the record below, so there is nowhere for a detail to be put even by a caller trying to.
 */
export class ReadAccountError extends Error {
  readonly failure: ReadAccountFailure;

  constructor(failure: ReadAccountFailure) {
    super(FAILURE_MESSAGE[failure]);
    this.name = 'ReadAccountError';
    this.failure = failure;
  }
}

const FAILURE_MESSAGE: Record<ReadAccountFailure, string> = {
  'no-session': 'No session.',
  unauthorized: 'Not authorized.',
  unavailable: 'Account unavailable.',
};

function isAccountStatus(value: unknown): value is Account['status'] {
  return typeof value === 'string' && (ACCOUNT_STATUSES as readonly string[]).includes(value);
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value !== '';
}

/**
 * The whole of AC7 and AC8 in one place, and it takes `unknown` on purpose.
 *
 * The generated type says the body is an `AccountResponse`; the measurements say a body is whatever arrived.
 * Typing this parameter as `Account` would make every check below look redundant to the next reader and
 * invite their removal - which is how the checks stop existing.
 *
 * All five fields are contract-required, so absence is an ERROR here. That is the opposite of T30, where
 * `UserInfoResponse` requires only `sub` and every other claim absent is the normal answer; the two readers
 * cannot share a validation rule even though they share a shape.
 *
 * `emailVerified` is checked for being a BOOLEAN, not for being truthy. `false` is a contract-valid,
 * measured-real answer, and a single "missing or empty" rule across five fields of three types would reject
 * it - Kimi's phase 3 caught exactly that in the brief's wording. T30's `sub === ''` check is the analogue
 * that IS right, because an empty string is genuinely unusable where `false` is an answer.
 *
 * The `null` arm is first and is not defensive decoration: a `200` whose body is the JSON literal `null`
 * parses to `null`, and reaching a property access on it throws a raw `TypeError` out of the reader, past the
 * `try/catch` that only wraps the transport. That shipped in T30 and was found in its phase 11.
 */
function isUsableAccount(body: unknown): body is Account {
  if (typeof body !== 'object' || body === null) return false;
  const account = body as Partial<Record<keyof Account, unknown>>;
  return (
    isNonEmptyString(account.accountUuid)
    && isNonEmptyString(account.email)
    && isNonEmptyString(account.createdAt)
    && typeof account.emailVerified === 'boolean'
    && isAccountStatus(account.status)
  );
}

/**
 * Reads the caller's own account, or rejects with a `ReadAccountError`.
 *
 * `tokens` is taken separately from `api` because the boundary does not expose `current()` - it holds
 * `renew()` and `middleware()` only - and because reaching into `auth/accessToken.ts` from here would make
 * this module own the token's lifetime, which it does not. Both arrive as parameters rather than being
 * constructed, so the composition root stays exactly as unwired as it already is (G6).
 */
export async function readAccount(api: AuthApi, tokens: Pick<SessionTokens, 'current'>): Promise<Account> {
  // No token, no request - and the reason differs from T30's, which is why it is written out.
  //
  // This endpoint answers a well-behaved `401`, so the boundary could handle a missing session on its own.
  // But a bearer-less call was measured to answer that 401 WITH a `Set-Cookie: JSESSIONID`: an
  // unauthenticated read has a server-side side effect, and it also provokes a renewal that cannot succeed.
  //
  // `''` is guarded alongside `null` because the boundary attaches the header with `if (token)`
  // (`sessionBoundary.ts:89`), so an empty string sends NO `Authorization` header at all and produces exactly
  // the bearer-less request this guard exists to prevent. T30 learned that one from a phase 11 finding.
  const token = tokens.current();
  if (token === null || token === '') throw new ReadAccountError('no-session');

  /**
   * Wrapped, because `openapi-fetch` resolves an HTTP error status but REJECTS when the underlying `fetch`
   * throws - a DNS failure, a dropped connection, a CORS refusal, an abort. T30 shipped without this and a
   * raw `TypeError` carrying an internal hostname and a browser error code escaped its reader; no status
   * check could have caught it, because no response existed (G5).
   */
  let data: Account | undefined | null;
  let response: Response;
  try {
    const answer = await api.GET('/accounts/me');
    data = answer.data;
    response = answer.response;
  } catch {
    // Discarded, not inspected: there is nothing in a transport failure a visitor should be told, and the
    // reasons are indistinguishable from the client's side anyway.
    throw new ReadAccountError('unavailable');
  }

  // After the boundary has already had its single-flight renewal and re-send, so a 401 reaching here is the
  // final one and the session is genuinely unusable rather than merely stale (R21, L18).
  //
  // Nothing is read off the response to build this error. The live 401 carries NO BODY at all and puts its
  // detail in a `WWW-Authenticate` header, reading `error_description="An error occurred while attempting to
  // decode the Jwt: Malformed token"` for a malformed bearer - so a no-leak rule written about response
  // bodies would have let the only copy of that detail straight through.
  if (response.status === 401 || response.status === 403) throw new ReadAccountError('unauthorized');

  // Everything else that is not a usable account, collapsed into one answer on purpose: the visitor learns
  // that it did not work and nothing about why (L4). An unrecognised `status` lands here too, by G2.
  if (!response.ok || !isUsableAccount(data)) throw new ReadAccountError('unavailable');

  // Unchanged: no defaulting, no coercion, no narrowing of `status` to a boolean, nothing inferred from
  // another field. The server's answer is the answer (AC6).
  return data;
}
