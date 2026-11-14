import { createAuthClient, createOidcClient } from '@themistra/api-client';
import type { RateLimitNoticeState } from './rateLimitNotice';
import type { SessionBoundary } from './sessionBoundary';

/** The `security: []` operations of contracts/api/auth.yaml: sent without a bearer, and never renewed on 401. */
export const AUTH_PUBLIC_OPERATIONS: ReadonlySet<string> = new Set([
  'POST /accounts',
  'POST /accounts/verify-email',
  'POST /accounts/resend-verification',
  'POST /accounts/password-reset-request',
  'POST /accounts/password-reset',
]);

export type AuthApi = ReturnType<typeof createAuthClient>;

/**
 * The `security: []` operations of contracts/api/oidc.yaml.
 *
 * One entry, and the asymmetry with the auth client is the point. `POST /oauth2/token` carries no bearer
 * because `checky-spa` is a public client whose PKCE verifier is its only proof, so a 401 on it is final and
 * renewing would be circular - renewal is itself an authorize-and-exchange. `GET /userinfo` is the opposite:
 * it is the first authenticated operation in that contract, it carries the tab's token, and its 401 is
 * exactly the signal the boundary exists to act on.
 */
export const OIDC_PUBLIC_OPERATIONS: ReadonlySet<string> = new Set(['POST /oauth2/token']);

export type OidcApi = ReturnType<typeof createOidcClient>;

/** The auth-service client behind the tab's session boundary; screens call the backend through this, never directly.
 * Production wiring passes the tab's rate-limit notice; it is optional only so callers without one keep working.
 * Any further generated client (payments, notifications) takes the same notice and registers it first, as here, or it
 * loses R23's copy. */
export function createAuthApi(boundary: SessionBoundary, rateLimit?: RateLimitNoticeState): AuthApi {
  const client = createAuthClient();
  // Registered first on purpose: openapi-fetch runs onResponse in reverse registration order, so the notice runs
  // last and sees the final response, including a 429 on the session boundary's re-send after a renewal.
  if (rateLimit) client.use(rateLimit.middleware);
  client.use(boundary.middleware(AUTH_PUBLIC_OPERATIONS));
  return client;
}

/**
 * The auth-service client for a PUBLIC surface: no session boundary, because there is no session.
 *
 * **WHY THIS EXISTS AT ALL** (task 43, D4). `createAuthApi` needs a `SessionBoundary`, and a boundary is the
 * tab's single in-flight renewal (R21, L18) - "one per tab", built from the tab's tokens and its re-auth
 * hook. A registration screen has neither: `POST /accounts` is in `AUTH_PUBLIC_OPERATIONS`, so it is sent
 * with no bearer and its 401 never renews, which means the boundary's entire contribution to such a call is
 * to do nothing. Nothing in production constructs a boundary yet either (`auth/cleanReauth.ts` says so in
 * its own words, OQ-T33-2), so the alternative was a screen hand-writing an inert implementation of a
 * security interface and a reader having to compare it with the real one to see that it was inert.
 *
 * The rate-limit notice is still installed, because a public operation is exactly the kind that gets a 429
 * (R23) and that copy is the visitor's only explanation.
 *
 * It is NOT for protected operations. A caller that reaches a `security`-bearing path through this client
 * sends it without a token and gets a 401 that nothing renews - which is why the one line of middleware here
 * is the rate-limit notice and the public-operations set is not passed to anything: there is no boundary to
 * pass it to, and every operation on this client is treated as public by construction.
 */
export function createPublicAuthApi(rateLimit?: RateLimitNoticeState): AuthApi {
  const client = createAuthClient();
  if (rateLimit) client.use(rateLimit.middleware);
  return client;
}

/** The SAS protocol client behind the same session boundary, for the one protocol operation that needs a bearer.
 *
 * A deliberate mirror of `createAuthApi`, down to the middleware order, and mirrored rather than generalised on
 * purpose: two factories that differ only in their contract and their public-operations set can be compared at a
 * glance, and two that differ in registration order cannot. The comment above applies here unchanged - the notice
 * is registered first so it runs last and sees the final response.
 *
 * Until `/userinfo` was documented this had no reason to exist: `POST /oauth2/token` is public, so the OIDC client
 * needed no boundary at all. */
export function createOidcApi(boundary: SessionBoundary, rateLimit?: RateLimitNoticeState): OidcApi {
  const client = createOidcClient();
  if (rateLimit) client.use(rateLimit.middleware);
  client.use(boundary.middleware(OIDC_PUBLIC_OPERATIONS));
  return client;
}
