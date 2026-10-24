import { createAuthClient } from '@themistra/api-client';
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
