/**
 * The tab's ID token, held only to end the SAS session.
 *
 * **Why this is a module and not a second variable in `accessToken.ts`.** Task 35 established the shape the
 * whole sign-out rests on: every piece of session state has a module that owns it and a primitive that clears
 * it, and `signOut()` composes those primitives rather than reaching into any of them. A new piece of session
 * state getting a new owner is that pattern; folding it into an existing owner would have been the exception.
 * Two further reasons, both about not editing a closed file: `accessToken.ts:45` exports the `LocalSession`
 * shape that tasks 5 and 6 chose and says in its own header that the shape is not its to choose, and its
 * `clearAccessToken` would have started clearing two tokens for three existing callers - `cleanReauth.ts`, the
 * callback's discard, and sign-out - none of which asked for that.
 *
 * **There is no plain reader, and that is the point.** `takeIdToken()` returns the token and clears it in one
 * operation, so there is no way to obtain this value without consuming it. That forecloses the hazard Kimi's
 * phase 3 F2 described: a `currentIdToken()` would let a caller read the token, then fail or return before
 * clearing it, and the window between the two would be maintained by hand forever. It also means a module that
 * wanted to log the token would have to clear it to get it, which is a loud thing to write by accident.
 *
 * **What is in here, and why it is less dangerous than an access token.** An ID token is a signed assertion
 * about who signed in, for this client. It is not a bearer credential: nothing on the backend accepts it in an
 * `Authorization` header, so possession does not buy an API call. It is still identity - it carries `sub` and
 * the claims the `profile` and `email` scopes granted - so L3 governs it exactly as it governs the access
 * token: memory only, never storage, never a log, never analytics. L16's sessionStorage carve-out is
 * exhaustive and names no token, so there is nowhere else it could legally go, and a reload therefore loses it
 * along with the session it belongs to.
 *
 * **The one reason it is retained at all.** R19 clause 2 ends the SAS session through `/connect/logout`, and
 * SAS wants an `id_token_hint` to know which session to end. That is the whole use. If a future task measures
 * that SAS 1.5.1 honours a `post_logout_redirect_uri` without the hint (OQ-T36-4, unanswered - both backend
 * tests supply one), this module stops being necessary and should be deleted rather than kept for a
 * someday-caller.
 */

/** The one and only copy, as `accessToken.ts:24` holds the other. */
let held: string | null = null;

/**
 * Retains the ID token a completed exchange returned.
 *
 * Nothing validates the string, for the same reason `retainAccessToken` does not: the exchange already refused
 * a response whose `id_token` was missing, empty or not a string (`codeExchange.ts:76-77`), and the callback
 * has already checked its nonce before this is reached (`CallbackScreen.tsx:156`). A third check here would be
 * a second opinion with no new information and a second place to get it wrong.
 */
export function retainIdToken(token: string): void {
  held = token;
}

/**
 * Returns what was held and clears it, in one operation.
 *
 * `take` rather than `get` + `clear`, matching `takeSignInNotice()` and `takeReturnTarget()`: the package's
 * word for a read that consumes. Two callers, and the pair is deliberate:
 *
 * - `endSession()` calls it for the `id_token_hint` it is about to send, and
 * - `signOut()` calls it and discards the value, exactly as it discards `takeSignInNotice()`'s.
 *
 * The second call is not redundant. `signOut()` can be entered on its own, and when it is, this is what makes
 * the ID token gone; when sign-out was entered through `endSession()`, this call finds `null` and clears
 * nothing. One idempotent primitive serving both entry points is the reason neither has to know about the
 * other.
 *
 * Idempotent, and it never throws: there is no store to refuse the operation, which is the whole advantage of
 * holding this in memory.
 */
export function takeIdToken(): string | null {
  const token = held;
  held = null;
  return token;
}
