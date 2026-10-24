import type { Middleware } from './sessionBoundary';

/**
 * The tab's one "too many requests" notice (R23). It holds a single `shown` flag and nothing else: no retry queue, no
 * request log, no link to renewal or the transport. A 429 on any call through a client carrying `middleware` raises
 * it; only `dismiss()`, a user action, clears it. There is no timer, and the 429's body and headers (including
 * `Retry-After`) are never read, so the copy is the same for every 429.
 *
 * Wiring creates exactly one instance per tab and passes that same instance to `createAuthApi` and to
 * `<RateLimitNotice>`; with two, the component subscribes to a flag the middleware never sets.
 */
export interface RateLimitNoticeState {
  readonly middleware: Middleware;
  isShown(): boolean;
  subscribe(listener: () => void): () => void;
  dismiss(): void;
}

export function createRateLimitNotice(): RateLimitNoticeState {
  let shown = false;
  const listeners = new Set<() => void>();

  function setShown(next: boolean) {
    if (shown === next) return;
    shown = next;
    for (const listener of [...listeners]) {
      try {
        listener();
      } catch (error) {
        // A broken listener must not affect the response chain or the other listeners; it surfaces uncaught.
        queueMicrotask(() => {
          throw error;
        });
      }
    }
  }

  return {
    middleware: {
      onResponse({ response }) {
        if (response.status === 429) setShown(true);
        return undefined;
      },
    },
    isShown: () => shown,
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    dismiss: () => setShown(false),
  };
}
