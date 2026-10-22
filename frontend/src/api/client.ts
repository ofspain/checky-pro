import { createAuthClient } from '@themistra/api-client';
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

/** The auth-service client behind the tab's session boundary; screens call the backend through this, never directly. */
export function createAuthApi(boundary: SessionBoundary): AuthApi {
  const client = createAuthClient();
  client.use(boundary.middleware(AUTH_PUBLIC_OPERATIONS));
  return client;
}
