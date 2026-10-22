import type { createAuthClient } from '@themistra/api-client';

/** The generated client's middleware shape, taken from the client so frontend needs no direct openapi-fetch dependency. */
export type Middleware = Parameters<ReturnType<typeof createAuthClient>['use']>[0];

/**
 * The client boundary's view of the session, supplied by the auth module.
 * `renew()` is silent re-authentication on the SAS session cookie (L3): it needs no current token, resolves with the
 * fresh access token (and makes `current()` return it), or rejects when the session can't be resumed.
 */
export interface SessionTokens {
  current(): string | null;
  renew(): Promise<string>;
}

/** Upper bound on one renewal, so a renewal that never settles can't hold every waiting call forever. */
export const RENEWAL_TIMEOUT_MS = 30_000;

/** One per tab: every client's middleware shares this boundary's single in-flight renewal (R21, L18). */
export interface SessionBoundary {
  /** Joins the renewal in flight, or starts exactly one. */
  renew(): Promise<string>;
  /**
   * Middleware for one generated client. `publicOperations` holds the contract's `security: []` operations as
   * `"<METHOD> <schemaPath>"`: they are sent without a bearer and their 401 never renews.
   */
  middleware(publicOperations: ReadonlySet<string>): Middleware;
}

class RenewalTimeoutError extends Error {
  constructor(timeoutMs: number) {
    super(`Session renewal did not settle within ${timeoutMs} ms`);
    this.name = 'RenewalTimeoutError';
  }
}

/**
 * Creates the tab's session boundary.
 *
 * A protected request carries the current token. When it gets a 401, the request waits on the single shared renewal
 * and is then re-sent once with the new token. The re-send goes straight to the client's transport, bypassing every
 * middleware, so it can't loop, and a 401 on it is final. When renewal fails or times out, each waiting call gets its
 * original 401, and `onRenewalFailed` runs once for that renewal.
 */
export function createSessionBoundary(
  tokens: SessionTokens,
  onRenewalFailed: (reason: unknown) => void,
  options: { timeoutMs?: number } = {},
): SessionBoundary {
  const timeoutMs = options.timeoutMs ?? RENEWAL_TIMEOUT_MS;
  let inFlight: Promise<string> | null = null;

  function withTimeout(attempt: Promise<string>): Promise<string> {
    // A result that arrives after the timeout is ignored, and must not surface as an unhandled rejection.
    attempt.catch(() => undefined);
    let timer: ReturnType<typeof setTimeout> | undefined;
    const timeout = new Promise<never>((_, reject) => {
      timer = setTimeout(() => reject(new RenewalTimeoutError(timeoutMs)), timeoutMs);
    });
    return Promise.race([attempt, timeout]).finally(() => clearTimeout(timer));
  }

  function renew(): Promise<string> {
    if (inFlight) return inFlight;
    const attempt = withTimeout(Promise.resolve().then(() => tokens.renew()))
      .catch((reason: unknown) => {
        // Scheduled, so a throwing hook can't affect the waiters; its error still surfaces as uncaught.
        queueMicrotask(() => onRenewalFailed(reason));
        throw reason;
      })
      .finally(() => {
        inFlight = null;
      });
    inFlight = attempt;
    return attempt;
  }

  function middleware(publicOperations: ReadonlySet<string>): Middleware {
    const pending = new Map<string, { clone: Request | null; tokenUsed: string | null }>();
    const isPublic = (request: Request, schemaPath: string) => publicOperations.has(`${request.method} ${schemaPath}`);
    const setBearer = (request: Request, token: string | null) => {
      if (token) request.headers.set('Authorization', `Bearer ${token}`);
      else request.headers.delete('Authorization');
    };

    return {
      async onRequest({ request, schemaPath, id }) {
        if (isPublic(request, schemaPath)) return undefined;
        // Don't knowingly send a token that is being replaced; the renewal's outcome is handled on the response.
        if (inFlight) await inFlight.catch(() => undefined);
        const tokenUsed = tokens.current();
        setBearer(request, tokenUsed);
        let clone: Request | null = null;
        try {
          clone = request.clone();
        } catch {
          // An uncloneable body can't be re-sent; its 401 still drives the renewal.
        }
        pending.set(id, { clone, tokenUsed });
        return request;
      },

      async onResponse({ request, response, schemaPath, id, options: clientOptions }) {
        const entry = pending.get(id);
        pending.delete(id);
        if (!entry || response.status !== 401 || isPublic(request, schemaPath)) return response;

        // A different current token means a renewal already completed after this request was sent.
        if (entry.tokenUsed === tokens.current()) {
          try {
            await renew();
          } catch {
            return response;
          }
        }
        if (!entry.clone) return response;
        setBearer(entry.clone, tokens.current());
        return clientOptions.fetch(entry.clone);
      },

      onError({ id }) {
        pending.delete(id);
        return undefined;
      },
    };
  }

  return { renew, middleware };
}
