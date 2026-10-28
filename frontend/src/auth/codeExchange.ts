import { createOidcClient } from '@themistra/api-client';
import { AUTHORIZE_STORAGE_KEYS, CLIENT_ID, redirectUri } from './authorizeRequest';

/**
 * The authorization-code exchange.
 *
 * This is the whole of R7's first clause. `checky-spa` is registered with `ClientAuthenticationMethod.NONE`, so the
 * request carries no client credential of any kind and **the stored `code_verifier` is the only proof that the code
 * is being redeemed by the tab that requested it**. Send the wrong one, or a freshly generated one, and the exchange
 * is an unauthenticated code redemption that the authorization server has no way to tell from the real thing.
 *
 * The grant keeps `state` even though the token endpoint has no such parameter and nothing below puts it on the wire
 * (T20-D8). It is here because T17's and T18's suites watch the shape of this call to prove that a callback with a
 * missing or mismatched `state` completes no exchange at all. This function does not re-check `state`: R8 is the call
 * site's duty and stays there, which also means silent renewal (L18) can call this without re-running a check it has
 * already made.
 */
export type AuthorizationCodeGrant = {
  code: string;
  state: string;
};

/**
 * As much of the token endpoint's response as the SPA uses.
 *
 * Two fields, and deliberately not five. The ID token is what T19's nonce check consumes; the access token is what
 * task 21 will hold in memory. `expires_in`, `scope` and `token_type` are on the wire and typed in the contract, and
 * they stay there until a task needs them - task 21 owns how long a token is good for, and returning a field nobody
 * reads yet would be a guess that task has to undo.
 *
 * Measured against the running auth service, the real response carries no refresh token at all, which is the
 * empirical basis for L3's silent re-authentication model. `contracts/api/oidc.yaml` is where that absence is
 * recorded and pinned; this file names no such field precisely so that a scan of it can be absolute.
 */
export type TokenResponse = {
  idToken: string;
  accessToken: string;
};

/**
 * Exactly what `openapi-fetch`'s stock body serializer looks for before encoding a body as a form, and it compares
 * with `===`. A charset parameter, a capital letter or a stray space falls through to `JSON.stringify` and a JSON
 * content type, which the authorization server rejects with nothing useful to say about why.
 */
const FORM_CONTENT_TYPE = 'application/x-www-form-urlencoded';

/**
 * The stored verifier, or `null` for every way there isn't one.
 *
 * Wrapped because TOUCHING `sessionStorage` can throw, not merely return `null`: a context with site data blocked
 * raises instead of answering. There is no error boundary anywhere in this application, so an uncaught throw here
 * would blank the page at the end of a sign-in, after the visitor has already authenticated. An empty string is
 * normalized to `null` for the same reason `idTokenNonce.ts` does it - one absence value to test rather than three,
 * and an empty verifier is not a verifier.
 */
function storedCodeVerifier(): string | null {
  let stored: string | null = null;
  try {
    stored = globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.codeVerifier);
  } catch {
    stored = null;
  }
  return stored === null || stored === '' ? null : stored;
}

/**
 * The wire boundary. Everything that arrives here is `unknown` until it has been checked.
 *
 * The contract types this response, and the compiler believes it - but the compiler cannot see the network, and T19's
 * first self-review finding was exactly this mistake: a typed field trusted because nothing in the editor said
 * otherwise. A response that is not an object, or whose tokens are missing, empty or not strings, produces `null` and
 * therefore the same single rejection as a 400.
 */
function usableTokens(payload: unknown): TokenResponse | null {
  if (typeof payload !== 'object' || payload === null) return null;
  const { id_token: idToken, access_token: accessToken } = payload as Record<string, unknown>;
  if (typeof idToken !== 'string' || idToken === '') return null;
  if (typeof accessToken !== 'string' || accessToken === '') return null;
  return { idToken, accessToken };
}

/**
 * The one rejection, carrying nothing.
 *
 * A missing verifier, a refused exchange, a 5xx, a dropped connection, a body that will not parse and a response with
 * no usable tokens are six different faults and one single outcome, by L4: a visitor who can tell them apart can
 * enumerate. The authorization server's own `error` and `error_description` are never read - the contract gives the
 * 400 response no schema, so there is no typed way to reach them - and this message is a fixed string that names no
 * status, no server text and no part of any token.
 */
function fail(): never {
  throw new Error('The authorization-code exchange did not produce usable tokens.');
}

/**
 * Exchanges a callback's authorization code for tokens, proving the redemption with the stored verifier.
 *
 * The client is built per call rather than held in a module variable: this runs once per sign-in, a module-level
 * value would be state task 21 has to reason about when it decides where the access token lives, and there is
 * nothing to amortize. Nothing is written, deleted or cleared here - the three stored items survive both outcomes,
 * and deleting them is task 23's, which `CallbackScreen` does after the nonce check so that this function still
 * finds the verifier where task 15 left it.
 */
export async function exchangeAuthorizationCode(grant: AuthorizationCodeGrant): Promise<TokenResponse> {
  const codeVerifier = storedCodeVerifier();
  if (codeVerifier === null) fail();

  // Declared `unknown` on purpose, discarding the contract's typing of the body, so that `usableTokens` has to earn
  // what it returns. The call itself is wrapped because a rejected connection and an unparseable body both throw out
  // of here, and either one escaping would be a second kind of failure with a different message - which is what L4
  // forbids.
  let payload: unknown;
  try {
    const { data } = await createOidcClient().POST('/oauth2/token', {
      // Set explicitly, and it must be: the serializer's JSON default is applied at the lowest priority and an
      // explicit header overrides it. Without this line the body is JSON and the verifier never arrives.
      headers: { 'Content-Type': FORM_CONTENT_TYPE },
      body: {
        grant_type: 'authorization_code',
        code: grant.code,
        // Derived from the one exported helper rather than spelled again here. The authorization server matches this
        // value exactly against the registered redirect URI, so two independent derivations that agree today are a
        // failure waiting for the first change to either (T20-D7).
        redirect_uri: redirectUri(),
        client_id: CLIENT_ID,
        code_verifier: codeVerifier,
      },
    });
    payload = data;
  } catch {
    fail();
  }

  return usableTokens(payload) ?? fail();
}
