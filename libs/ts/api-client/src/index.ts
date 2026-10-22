import createClient from 'openapi-fetch';
import type { paths } from './generated/auth';

export type { components, operations, paths } from './generated/auth';

/** The typed auth-service client, generated from contracts/api/auth.yaml (L6). Paths are relative, so every call goes
 * to the page's own origin and the edge routes it (L9). There is no base URL to configure and no env key (L10). */
export function createAuthClient() {
  return createClient<paths>({ baseUrl: '' });
}
