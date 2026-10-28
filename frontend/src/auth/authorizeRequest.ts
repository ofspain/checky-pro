import { APP_BASENAME } from '../app/routes';
import { generatePkcePair, isValidCodeVerifier, randomUrlSafeToken } from './pkce';

/**
 * The OIDC authorization request (R6, R20, L1).
 *
 * This is the task that starts the flow. T14 built the PKCE primitive and published S256 as a literal type; this is
 * what actually sends it, and nothing downstream checks: the auth service requires a challenge
 * (requireProofKey(true)) but accepts `plain` and offers no switch to forbid it. The scope rule is the opposite
 * case - the server registers exactly these three, so a fourth is refused on both sides.
 *
 * Every value is derived, never configured. The SPA and every backend route share one origin (L9), so the authorize
 * endpoint is this page's own origin, and the redirect URI is built from the route table. That is why this module
 * reads no environment key - which it also may not, since the shell's own suite forbids any source naming the
 * compile-time environment object or an absolute address.
 *
 * It is also the writer of three of L16's four items: tasks 17-20 compare the callback against the STORED state and
 * exchange the code with the STORED verifier, and no task between this one and those could have written them.
 * Silent renewal runs through here too, so this is not a sign-in-only path.
 */

/** Spring Authorization Server's authorize endpoint, reached through the edge on this origin. */
export const AUTHORIZE_ENDPOINT_PATH = '/oauth2/authorize';

/** The public client identifier. Not a secret, and fixed on the server rather than per-environment. */
export const CLIENT_ID = 'checky-spa';

/** agents.md: "The requested scopes are openid profile email." Exactly these, in this order. */
export const REQUESTED_SCOPES = ['openid', 'profile', 'email'] as const;

/**
 * Where the three L16 items live. Published so tasks 17-20 read them back by name, and so task 26's carve-out guard
 * enumerates the same keys, rather than either copying the strings.
 */
export const AUTHORIZE_STORAGE_KEYS = {
  state: 'themistra.auth.state',
  nonce: 'themistra.auth.nonce',
  codeVerifier: 'themistra.auth.code_verifier',
} as const;

/**
 * The URL to navigate to, with the two public parameters a caller may want to inspect.
 *
 * The verifier is deliberately not here. It is written to session storage and read back at the token exchange, and a
 * caller has no business holding it - so it cannot be put into a URL, a log or a React prop by accident.
 */
export type AuthorizeRequest = {
  url: string;
  state: string;
  nonce: string;
};

/**
 * The exact-match redirect URI the server has registered, derived from the route table rather than configured.
 *
 * The trailing slash is stripped first: APP_BASENAME is `/app` today, but a future `/app/` would otherwise produce
 * `/app//callback`, which an exact-match comparison rejects at sign-in with nothing failing here first.
 */
export function redirectUri(): string {
  const base = APP_BASENAME.endsWith('/') ? APP_BASENAME.slice(0, -1) : APP_BASENAME;
  return `${globalThis.location.origin}${base}/callback`;
}

/**
 * Builds the request and stores what the callback will need.
 *
 * The three items are written only once the challenge is in hand. Storing a state before the derivation could fail
 * would leave a state with no verifier behind it, and the next callback would be refused for a reason nothing here
 * would explain.
 */
export async function buildAuthorizeRequest(): Promise<AuthorizeRequest> {
  const state = randomUrlSafeToken();
  const nonce = randomUrlSafeToken();
  const pkce = await generatePkcePair();
  // The pair's own contract, checked rather than trusted: storing a verifier the token endpoint will refuse would
  // strand this sign-in at the exchange, far from the cause.
  if (!isValidCodeVerifier(pkce.verifier)) {
    throw new TypeError('PKCE pair carried a verifier that is not RFC 7636 valid');
  }

  const session = globalThis.sessionStorage;
  session.setItem(AUTHORIZE_STORAGE_KEYS.state, state);
  session.setItem(AUTHORIZE_STORAGE_KEYS.nonce, nonce);
  session.setItem(AUTHORIZE_STORAGE_KEYS.codeVerifier, pkce.verifier);

  const parameters = new URLSearchParams({
    response_type: 'code',
    client_id: CLIENT_ID,
    redirect_uri: redirectUri(),
    scope: REQUESTED_SCOPES.join(' '),
    state,
    nonce,
    code_challenge: pkce.challenge,
    code_challenge_method: pkce.method,
  });

  return { url: `${globalThis.location.origin}${AUTHORIZE_ENDPOINT_PATH}?${parameters}`, state, nonce };
}

/**
 * Removes the three single-use items the authorize step stored - what L16 calls "deleted at callback".
 *
 * Contract: removes exactly the three keys `AUTHORIZE_STORAGE_KEYS` names, is idempotent, never throws, never
 * clears the store, and never touches another key. It is NOT a general cleanup helper. L16's fourth item, the
 * post-login return target, does not exist yet and belongs to task 24; R29's mid-task form input is task 26's.
 *
 * Each removal is guarded on its own, because `removeItem` can throw rather than fail quietly - the same reason
 * every read in this package is wrapped. The realistic failure is a store denied outright, which throws for all
 * three and so leaves all three. `sessionStorage` has no transaction, so "all three or none" is a property of
 * there being ONE call site, not of atomicity this function could provide, and saying otherwise would promise
 * something the platform does not offer.
 *
 * Iterated from the constant rather than written as three named calls, so a fourth key added above cannot be
 * left behind here - the writer is twenty lines up, and these two are the only places that know the set.
 *
 * Why not inside the readers: `codeExchange.ts` reads the verifier and `idTokenNonce.ts` reads the nonce, and
 * both are pure functions that three existing tests assert take no action on storage. Why not three removals at
 * the call site: the keys are spelled here, once, and a second spelling is what T22's scan forbids.
 */
export function consumeAuthorizeState(): void {
  for (const key of Object.values(AUTHORIZE_STORAGE_KEYS)) {
    try {
      globalThis.sessionStorage.removeItem(key);
    } catch {
      // A cleanup that cannot run is not a failed sign-in (T23-D4). The visitor's outcome does not change, and
      // the next authorize request overwrites all three anyway.
    }
  }
}
