import createClient from 'openapi-fetch';
import type { paths } from './generated/auth';
import type { paths as oidcPaths } from './generated/oidc';

export type { components, operations, paths } from './generated/auth';
export type { components as oidcComponents, operations as oidcOperations, paths as oidcPaths } from './generated/oidc';

/** The typed auth-service client, generated from contracts/api/auth.yaml (L6). Paths are relative, so every call goes
 * to the page's own origin and the edge routes it (L9). There is no base URL to configure and no env key (L10). */
export function createAuthClient() {
  return createClient<paths>({ baseUrl: '' });
}

/** The typed client for the SAS protocol endpoints the SPA calls, generated from contracts/api/oidc.yaml (L6, T20-D3).
 * Separate from the auth client because the two contracts are separate - SAS governs its own protocol surface, and
 * auth.yaml says so in its own description - not because the transport differs. It does not: same construction, same
 * relative paths, same origin, same absence of a base URL or env key (L9, L10).
 *
 * The token request is form-encoded, which needs no serializer here. openapi-fetch's stock body serializer encodes a
 * body as a form when the request carries `Content-Type: application/x-www-form-urlencoded`, and an explicitly passed
 * header overrides the JSON default it would otherwise apply. The caller sets that header; the comparison upstream is
 * `===`, so the value must be exactly that string. */
export function createOidcClient() {
  return createClient<oidcPaths>({ baseUrl: '' });
}
