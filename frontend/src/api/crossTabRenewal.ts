import { RENEWAL_TIMEOUT_MS, type SessionTokens } from './sessionBoundary';

/** The tab's own session, supplied by the auth module: its silent re-auth, plus a way to take a sibling's token. */
export interface LocalSession extends SessionTokens {
  adopt(token: string): void;
}

export interface TabLockOptions {
  ifAvailable?: boolean;
  signal?: AbortSignal;
}

/** The part of the Web Locks `LockManager` this module uses; production passes `navigator.locks`. */
export interface TabLocks {
  request<T>(name: string, options: TabLockOptions, callback: (lock: unknown) => Promise<T>): Promise<T>;
}

export const RENEWAL_CHANNEL = 'themistra.session.renewal';
export const RENEWAL_LOCK = 'themistra.session.renewal';

/**
 * How long a tab that waited behind a renewing sibling waits for that sibling's outcome after it gets the lock.
 * Lock grant and channel delivery are not ordered across tabs, so the outcome can arrive just after the lock.
 */
export const SIBLING_GRACE_MS = 1_000;

/** A sibling tab's renewal failed; this tab takes that as its own outcome rather than renewing again. */
export class SiblingRenewalFailedError extends Error {
  constructor() {
    super('Session renewal failed in a sibling tab');
    this.name = 'SiblingRenewalFailedError';
  }
}

class RenewalTimeoutError extends Error {
  constructor(timeoutMs: number) {
    super(`Session renewal did not settle within ${timeoutMs} ms`);
    this.name = 'RenewalTimeoutError';
  }
}

/** The SessionTokens that createSessionBoundary uses in this tab, with a way to detach from the other tabs. */
export interface SharedRenewal extends SessionTokens {
  close(): void;
}

// The failure message carries no reason, so nothing about why renewal failed crosses tabs.
type Outcome = { type: 'renewal:succeeded'; token: string } | { type: 'renewal:failed' };

function parseOutcome(data: unknown): Outcome | null {
  if (typeof data !== 'object' || data === null) return null;
  const message = data as { type?: unknown; token?: unknown };
  if (message.type === 'renewal:failed') return { type: 'renewal:failed' };
  if (message.type === 'renewal:succeeded' && typeof message.token === 'string' && message.token !== '') {
    return { type: 'renewal:succeeded', token: message.token };
  }
  return null;
}

/**
 * Shares the tab's renewal with its sibling tabs (R21, L18): a Web Locks leader renews, and a BroadcastChannel
 * carries the outcome.
 *
 * The tab that gets the lock without waiting leads: it renews and posts the outcome. A tab that finds the lock held
 * follows: once it gets the lock, it uses the outcome that arrived, or arrives within the grace period. If none
 * comes, the leader went away mid-renewal, and the follower renews itself. A renewed token is posted to the app's own
 * same-origin tabs, which adopt it; it is never stored, logged or put in a URL (L3).
 *
 * Every renewal is bounded by `timeoutMs`, both the wait for the lock and the holder's renewal, so a hung renewal
 * can't keep the lock from the other tabs. Without BroadcastChannel or Web Locks, renewal stays per tab.
 */
export function shareRenewalAcrossTabs(
  local: LocalSession,
  options: { channel?: BroadcastChannel; locks?: TabLocks; timeoutMs?: number; graceMs?: number } = {},
): SharedRenewal {
  const timeoutMs = options.timeoutMs ?? RENEWAL_TIMEOUT_MS;
  const graceMs = options.graceMs ?? SIBLING_GRACE_MS;
  const locks = options.locks ?? (typeof navigator !== 'undefined' ? navigator.locks : undefined);
  const channel =
    options.channel ??
    (locks && typeof BroadcastChannel === 'function' ? new BroadcastChannel(RENEWAL_CHANNEL) : undefined);

  if (!locks || !channel) {
    channel?.close();
    return { current: () => local.current(), renew: () => local.renew(), close: () => undefined };
  }
  const tabLocks: TabLocks = locks;
  const tabChannel: BroadcastChannel = channel;

  let outcomeCount = 0;
  let lastOutcome: Outcome | null = null;
  let closed = false;
  const waiters = new Set<() => void>();

  function onMessage(event: MessageEvent) {
    const outcome = parseOutcome(event.data);
    if (!outcome) return;
    if (outcome.type === 'renewal:succeeded') local.adopt(outcome.token);
    outcomeCount += 1;
    lastOutcome = outcome;
    for (const wake of [...waiters]) wake();
  }
  tabChannel.addEventListener('message', onMessage);

  function post(outcome: Outcome) {
    // Posting on a closed channel throws; the local caller still gets its result.
    if (!closed) tabChannel.postMessage(outcome);
  }

  function untilAborted<T>(attempt: Promise<T>, signal: AbortSignal): Promise<T> {
    attempt.catch(() => undefined);
    return new Promise<T>((resolve, reject) => {
      const onAbort = () => reject(signal.reason);
      if (signal.aborted) return onAbort();
      signal.addEventListener('abort', onAbort, { once: true });
      attempt.then(resolve, reject).finally(() => signal.removeEventListener('abort', onAbort));
    });
  }

  async function lead(signal: AbortSignal): Promise<string> {
    try {
      const token = await untilAborted(
        Promise.resolve().then(() => local.renew()),
        signal,
      );
      post({ type: 'renewal:succeeded', token });
      return token;
    } catch (reason) {
      post({ type: 'renewal:failed' });
      throw reason;
    }
  }

  function outcomeSince(mark: number): Promise<Outcome | null> {
    if (outcomeCount > mark) return Promise.resolve(lastOutcome);
    return new Promise((resolve) => {
      const wake = () => {
        clearTimeout(timer);
        waiters.delete(wake);
        resolve(lastOutcome);
      };
      const timer = setTimeout(() => {
        waiters.delete(wake);
        resolve(null);
      }, graceMs);
      waiters.add(wake);
    });
  }

  function use(outcome: Outcome): string {
    if (outcome.type === 'renewal:succeeded') return outcome.token;
    throw new SiblingRenewalFailedError();
  }

  async function renew(): Promise<string> {
    const mark = outcomeCount;
    const deadline = new AbortController();
    const timer = setTimeout(() => deadline.abort(new RenewalTimeoutError(timeoutMs)), timeoutMs);
    try {
      const led = await tabLocks.request(RENEWAL_LOCK, { ifAvailable: true }, async (lock) =>
        lock ? { token: await lead(deadline.signal) } : null,
      );
      if (led) return led.token;
      return await tabLocks.request(RENEWAL_LOCK, { signal: deadline.signal }, async () => {
        const outcome = await outcomeSince(mark);
        // Checked as late as possible: a message landing after this point can't stop the renewal below.
        return outcome ? use(outcome) : lead(deadline.signal);
      });
    } catch (reason) {
      if (deadline.signal.aborted && !(reason instanceof SiblingRenewalFailedError)) throw deadline.signal.reason;
      throw reason;
    } finally {
      clearTimeout(timer);
    }
  }

  return {
    current: () => local.current(),
    renew,
    close() {
      closed = true;
      tabChannel.removeEventListener('message', onMessage);
      tabChannel.close();
    },
  };
}
