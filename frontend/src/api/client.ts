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
