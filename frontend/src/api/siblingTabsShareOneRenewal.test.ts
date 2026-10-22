import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createAuthApi } from './client';
import {
  RENEWAL_CHANNEL,
  RENEWAL_LOCK,
  SIBLING_GRACE_MS,
  SiblingRenewalFailedError,
  shareRenewalAcrossTabs,
  type LocalSession,
  type SharedRenewal,
  type TabLocks,
} from './crossTabRenewal';
import { createSessionBoundary } from './sessionBoundary';

interface Deferred<T> {
  promise: Promise<T>;
  resolve(value: T): void;
  reject(reason: unknown): void;
}

function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

/** Web Locks semantics for one origin: one holder, FIFO queue, `ifAvailable`, and abort while queued. */
function fakeLocks(): TabLocks & { isHeld(): boolean } {
  let held = false;
  const queue: Array<() => void> = [];
  const release = () => {
    held = false;
    queue.shift()?.();
  };
  return {
    isHeld: () => held,
    request<T>(_name: string, options: { ifAvailable?: boolean; signal?: AbortSignal }, callback: (lock: unknown) => Promise<T>) {
      if (options.ifAvailable && (held || queue.length > 0)) return callback(null);
      return new Promise<T>((resolve, reject) => {
        const grant = () => {
          held = true;
          callback({ name: RENEWAL_LOCK }).then(resolve, reject).finally(release);
        };
        if (!held && queue.length === 0) return grant();
        queue.push(grant);
        options.signal?.addEventListener(
          'abort',
          () => {
            const index = queue.indexOf(grant);
            if (index >= 0) {
              queue.splice(index, 1);
              reject(options.signal!.reason);
            }
          },
          { once: true },
        );
      });
    },
  };
}

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

let channelName: string;
let opened: Array<{ close(): void }>;
let channelSequence = 0;

/** One simulated tab: its own fake LocalSession, sharing the test's channel name and lock manager. */
function openTab(locks: TabLocks, options: { timeoutMs?: number; graceMs?: number; adopt?: (token: string) => void } = {}) {
  let token: string | null = 'old';
  const attempts: Array<Deferred<string>> = [];
  const renew = vi.fn(() => {
    const attempt = deferred<string>();
    attempts.push(attempt);
    return attempt.promise.then((fresh) => {
      token = fresh;
      return fresh;
    });
  });
  const adopt = vi.fn(
    options.adopt ??
      ((fresh: string) => {
        token = fresh;
      }),
  );
  const local: LocalSession = { current: () => token, renew, adopt };
  const shared: SharedRenewal = shareRenewalAcrossTabs(local, {
    channel: new BroadcastChannel(channelName),
    locks,
    graceMs: options.graceMs ?? 25,
    timeoutMs: options.timeoutMs,
  });
  opened.push(shared);
  return { shared, renew, adopt, attempts, current: () => token };
}

/** A bystander on the channel: records what tabs post, and can post as a foreign sender. */
function observeChannel() {
  const channel = new BroadcastChannel(channelName);
  const seen: unknown[] = [];
  channel.onmessage = (event: MessageEvent) => seen.push(event.data);
  opened.push(channel);
  return { seen, post: (data: unknown) => channel.postMessage(data) };
}

/** Holds the lock the way a tab mid-renewal would, then lets go, with no outcome posted (a tab that went away). */
function vanishingLeader(locks: TabLocks) {
  const gone = deferred<void>();
  void locks.request(RENEWAL_LOCK, {}, () => gone.promise);
  return { vanish: () => gone.resolve() };
}

const settled = <T>(promise: Promise<T>) =>
  promise.then(
    (value) => ({ ok: true as const, value }),
    (error: unknown) => ({ ok: false as const, error }),
  );

beforeEach(() => {
  channelName = `${RENEWAL_CHANNEL}.test-${(channelSequence += 1)}`;
  opened = [];
});

afterEach(() => {
  for (const item of opened) item.close();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('siblingTabsShareOneRenewal', () => {
  it('lets a sibling wait on the renewing tab and use its token, adopted before its own call settles (AC1, AC2)', async () => {
    const locks = fakeLocks();
    const a = openTab(locks);
    const b = openTab(locks);
    const order: string[] = [];
    b.adopt.mockImplementation(() => order.push('b adopted'));

    const leading = a.shared.renew();
    await vi.waitFor(() => expect(a.renew).toHaveBeenCalledTimes(1));
    const following = b.shared.renew().then((token) => {
      order.push('b settled');
      return token;
    });
    a.attempts[0]!.resolve('fresh');

    expect(await leading).toBe('fresh');
    expect(await following).toBe('fresh');
    expect(b.renew).not.toHaveBeenCalled();
    expect(b.adopt).toHaveBeenCalledWith('fresh');
    expect(order).toEqual(['b adopted', 'b settled']);
  });

  it("hands a sibling the leader's failure, with no renewal of its own and no reason on the wire (AC3, AC7)", async () => {
    const locks = fakeLocks();
    const observer = observeChannel();
    const a = openTab(locks);
    const b = openTab(locks);

    const leading = settled(a.shared.renew());
    await vi.waitFor(() => expect(a.renew).toHaveBeenCalledTimes(1));
    const following = settled(b.shared.renew());
    const noSession = new Error('no SAS session for checky-spa');
    a.attempts[0]!.reject(noSession);

    expect(await leading).toEqual({ ok: false, error: noSession });
    const outcome = await following;
    expect(outcome.ok).toBe(false);
    expect(outcome.ok ? undefined : outcome.error).toBeInstanceOf(SiblingRenewalFailedError);
    expect(b.renew).not.toHaveBeenCalled();
    await vi.waitFor(() => expect(observer.seen).toHaveLength(1));
    expect(observer.seen).toEqual([{ type: 'renewal:failed' }]);
  });

  it('runs one renewal when two tabs ask in the same tick (AC4)', async () => {
    const locks = fakeLocks();
    const a = openTab(locks);
    const b = openTab(locks);

    const both = Promise.all([a.shared.renew(), b.shared.renew()]);
    await vi.waitFor(() => expect(a.renew.mock.calls.length + b.renew.mock.calls.length).toBe(1));
    (a.attempts[0] ?? b.attempts[0])!.resolve('fresh');

    expect(await both).toEqual(['fresh', 'fresh']);
    expect(a.renew.mock.calls.length + b.renew.mock.calls.length).toBe(1);
  });

  it("runs one renewal for three tabs, and both followers get the leader's token (AC4)", async () => {
    const locks = fakeLocks();
    const [a, b, c] = [openTab(locks), openTab(locks), openTab(locks)];

    const leading = a.shared.renew();
    await vi.waitFor(() => expect(a.renew).toHaveBeenCalledTimes(1));
    const followers = Promise.all([b.shared.renew(), c.shared.renew()]);
    a.attempts[0]!.resolve('fresh');

    expect(await leading).toBe('fresh');
    expect(await followers).toEqual(['fresh', 'fresh']);
    expect(b.renew).not.toHaveBeenCalled();
    expect(c.renew).not.toHaveBeenCalled();
    expect([b.current(), c.current()]).toEqual(['fresh', 'fresh']);
  });

  it('renews itself once the grace period passes when the renewing tab went away without an outcome (AC5)', async () => {
    const locks = fakeLocks();
    const leader = vanishingLeader(locks);
    const b = openTab(locks, { graceMs: 25 });

    const following = b.shared.renew();
    await sleep(10);
    expect(b.renew).not.toHaveBeenCalled();
    leader.vanish();
    await vi.waitFor(() => expect(b.renew).toHaveBeenCalledTimes(1));
    b.attempts[0]!.resolve('own');

    expect(await following).toBe('own');
  });

  it('gives up at the deadline without renewing or posting, whether still queued or in its grace wait (AC5, phase 9)', async () => {
    const locks = fakeLocks();
    const observer = observeChannel();

    // Still queued at the deadline: the request is withdrawn.
    const stillHolding = vanishingLeader(locks);
    const queued = openTab(locks, { timeoutMs: 60, graceMs: 25 });
    const queuedOutcome = await settled(queued.shared.renew());
    expect(queuedOutcome.ok ? 'resolved' : (queuedOutcome.error as Error).name).toBe('RenewalTimeoutError');
    expect(queued.renew).not.toHaveBeenCalled();
    stillHolding.vanish();
    await vi.waitFor(() => expect(locks.isHeld()).toBe(false));

    // Granted just before the deadline: the grace wait is cut short, and nothing is renewed or posted.
    const briefly = vanishingLeader(locks);
    const late = openTab(locks, { timeoutMs: 80, graceMs: 200 });
    const started = Date.now();
    const lateOutcome = settled(late.shared.renew());
    await sleep(50);
    briefly.vanish();
    const result = await lateOutcome;
    const elapsed = Date.now() - started;

    expect(result.ok ? 'resolved' : (result.error as Error).name).toBe('RenewalTimeoutError');
    expect(elapsed).toBeLessThan(200);
    expect(late.renew).not.toHaveBeenCalled();
    await sleep(10);
    expect(observer.seen).toEqual([]);
  });

  it('releases the lock from a hung leader at its deadline and tells the siblings it failed (AC5)', async () => {
    const locks = fakeLocks();
    const observer = observeChannel();
    const a = openTab(locks, { timeoutMs: 60 });

    const hung = await settled(a.shared.renew());
    expect(hung.ok ? 'resolved' : (hung.error as Error).name).toBe('RenewalTimeoutError');
    await vi.waitFor(() => expect(observer.seen).toEqual([{ type: 'renewal:failed' }]));
    expect(locks.isHeld()).toBe(false);

    // The next renewal is not blocked behind the hung one.
    const b = openTab(locks);
    const next = b.shared.renew();
    await vi.waitFor(() => expect(b.renew).toHaveBeenCalledTimes(1));
    b.attempts[0]!.resolve('fresh');
    expect(await next).toBe('fresh');
  });

  it('renews once across two tabs whose session boundaries both see a burst of 401s (AC6, end to end)', async () => {
    const NodeRequest = globalThis.Request;
    vi.stubGlobal(
      'Request',
      class extends NodeRequest {
        constructor(input: RequestInfo | URL, init?: RequestInit) {
          super(typeof input === 'string' ? new URL(input, window.location.href) : input, init);
        }
      },
    );
    const auths: Array<string | null> = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (request: Request) => {
        const auth = request.headers.get('Authorization');
        auths.push(auth);
        return new Response('{}', { status: auth === 'Bearer fresh' ? 200 : 401 });
      }),
    );
    const locks = fakeLocks();
    const a = openTab(locks);
    const b = openTab(locks);
    const failures: unknown[] = [];
    const apiA = createAuthApi(createSessionBoundary(a.shared, (reason) => failures.push(reason)));
    const apiB = createAuthApi(createSessionBoundary(b.shared, (reason) => failures.push(reason)));

    const burst = Promise.all([
      apiA.GET('/accounts/me'),
      apiA.GET('/api-keys'),
      apiB.GET('/accounts/me'),
      apiB.GET('/accounts/me/sessions'),
    ]);
    await vi.waitFor(() => expect(a.renew.mock.calls.length + b.renew.mock.calls.length).toBe(1));
    (a.attempts[0] ?? b.attempts[0])!.resolve('fresh');

    expect((await burst).map((result) => result.response.status)).toEqual([200, 200, 200, 200]);
    expect(a.renew.mock.calls.length + b.renew.mock.calls.length).toBe(1);
    expect(auths.filter((auth) => auth === 'Bearer old')).toHaveLength(4);
    expect(auths.filter((auth) => auth === 'Bearer fresh')).toHaveLength(4);
    expect(failures).toEqual([]);
  });

  it('ignores malformed and foreign messages: no adoption, and no stand-in for a real outcome (AC7)', async () => {
    const locks = fakeLocks();
    const observer = observeChannel();
    const leader = vanishingLeader(locks);
    const b = openTab(locks, { graceMs: 40 });

    const following = b.shared.renew();
    for (const junk of [
      null,
      'renewal:succeeded',
      { type: 'renewal:succeeded' },
      { type: 'renewal:succeeded', token: '' },
      { type: 'renewal:succeeded', token: 42 },
      { type: 'signout:everywhere' },
      { kind: 'renewal:failed' },
    ]) {
      observer.post(junk);
    }
    await sleep(10);
    leader.vanish();

    await vi.waitFor(() => expect(b.renew).toHaveBeenCalledTimes(1));
    b.attempts[0]!.resolve('own');
    expect(await following).toBe('own');
    expect(b.adopt).not.toHaveBeenCalled();

    observer.post({ type: 'renewal:succeeded', token: 'from-sibling' });
    await vi.waitFor(() => expect(b.adopt).toHaveBeenCalledWith('from-sibling'));
  });

  it('writes nothing to storage or cookies and logs no token while sharing a renewal (AC7)', async () => {
    const consoleSpies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((level) =>
      vi.spyOn(console, level).mockImplementation(() => undefined),
    );
    const cookieBefore = document.cookie;
    const locks = fakeLocks();
    const a = openTab(locks);
    const b = openTab(locks);

    const both = Promise.all([a.shared.renew(), b.shared.renew()]);
    await vi.waitFor(() => expect(a.renew.mock.calls.length + b.renew.mock.calls.length).toBe(1));
    (a.attempts[0] ?? b.attempts[0])!.resolve('secret-access-token');
    await both;

    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(document.cookie).toBe(cookieBefore);
    expect(JSON.stringify(consoleSpies.map((spy) => spy.mock.calls))).not.toContain('secret-access-token');
  });

  it('still settles an in-flight renewal locally after close(), and posts nothing (AC7)', async () => {
    const locks = fakeLocks();
    const observer = observeChannel();
    const a = openTab(locks);

    const inFlight = a.shared.renew();
    await vi.waitFor(() => expect(a.renew).toHaveBeenCalledTimes(1));
    a.shared.close();
    a.attempts[0]!.resolve('fresh');

    expect(await inFlight).toBe('fresh');
    await sleep(10);
    expect(observer.seen).toEqual([]);
  });

  it('falls back to the local renewal when Web Locks or BroadcastChannel is missing (AC8)', async () => {
    const local: LocalSession = { current: () => 'old', renew: vi.fn(async () => 'local'), adopt: vi.fn() };

    // jsdom has no navigator.locks: the default instance is local-only.
    expect(navigator.locks).toBeUndefined();
    const noLocks = shareRenewalAcrossTabs(local);
    expect(await noLocks.renew()).toBe('local');
    expect(noLocks.current()).toBe('old');
    noLocks.close();

    // An injected channel without locks is closed, not left open.
    const unused = new BroadcastChannel(channelName);
    const withoutLocks = shareRenewalAcrossTabs(local, { channel: unused });
    expect(await withoutLocks.renew()).toBe('local');
    expect(() => unused.postMessage('x')).toThrow();

    // Locks but no BroadcastChannel in this browser.
    vi.stubGlobal('BroadcastChannel', undefined);
    const noChannel = shareRenewalAcrossTabs(local, { locks: fakeLocks() });
    expect(await noChannel.renew()).toBe('local');
    expect(local.renew).toHaveBeenCalledTimes(3);
  });

  it("runs exactly one more renewal for a later episode, and a follower never reuses the earlier episode's outcome", async () => {
    const locks = fakeLocks();
    const a = openTab(locks);
    const b = openTab(locks);

    for (const [episode, token] of [
      [1, 'fresh'],
      [2, 'fresher'],
    ] as const) {
      const leading = a.shared.renew();
      await vi.waitFor(() => expect(a.renew).toHaveBeenCalledTimes(episode));
      const following = b.shared.renew();
      await sleep(5);
      a.attempts[episode - 1]!.resolve(token);

      expect(await leading).toBe(token);
      expect(await following).toBe(token);
    }
    expect(a.renew).toHaveBeenCalledTimes(2);
    expect(b.renew).not.toHaveBeenCalled();
    expect(b.current()).toBe('fresher');
  });

  it('still gives a waiting sibling the token when its adopt throws, and surfaces that defect (phase 9)', async () => {
    const realQueueMicrotask = globalThis.queueMicrotask;
    const uncaught: unknown[] = [];
    vi.stubGlobal('queueMicrotask', (task: () => void) =>
      realQueueMicrotask(() => {
        try {
          task();
        } catch (error) {
          uncaught.push(error);
        }
      }),
    );
    const locks = fakeLocks();
    const adoptDefect = new Error('adopt defect');
    const a = openTab(locks);
    const b = openTab(locks, {
      adopt: () => {
        throw adoptDefect;
      },
    });

    const leading = a.shared.renew();
    await vi.waitFor(() => expect(a.renew).toHaveBeenCalledTimes(1));
    const following = b.shared.renew();
    a.attempts[0]!.resolve('fresh');

    expect(await leading).toBe('fresh');
    expect(await following).toBe('fresh');
    expect(b.renew).not.toHaveBeenCalled();
    await vi.waitFor(() => expect(uncaught).toEqual([adoptDefect]));
  });

  it('defaults the grace period to one second', () => {
    expect(SIBLING_GRACE_MS).toBe(1_000);
  });
});
