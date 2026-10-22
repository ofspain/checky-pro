/// <reference types="node" />
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AUTH_PUBLIC_OPERATIONS, createAuthApi } from './client';
import { createSessionBoundary, RENEWAL_TIMEOUT_MS, type SessionTokens } from './sessionBoundary';

// This file is frontend/src/api/<name>; jsdom gives import.meta.url a non-file URL.
const repoRoot = dirname(dirname(dirname(dirname(expect.getState().testPath as string))));

type Sent = { method: string; path: string; auth: string | null; body: string };

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

/** Browser-style relative URL resolution: Node's Request rejects relative URLs, a browser resolves them. */
function installBrowserRequest() {
  const NodeRequest = globalThis.Request;
  vi.stubGlobal(
    'Request',
    class extends NodeRequest {
      constructor(input: RequestInfo | URL, init?: RequestInit) {
        super(typeof input === 'string' ? new URL(input, window.location.href) : input, init);
      }
    },
  );
}

/** A fake transport: records every request and answers with the status `respond` picks. */
function installTransport(respond: (request: Request) => number | Promise<number>) {
  const sent: Sent[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (request: Request) => {
      sent.push({
        method: request.method,
        path: new URL(request.url).pathname,
        auth: request.headers.get('Authorization'),
        body: request.method === 'GET' ? '' : await request.clone().text(),
      });
      const status = await respond(request);
      return new Response('{}', { status, headers: { 'Content-Type': 'application/json' } });
    }),
  );
  return sent;
}

/** The server accepts exactly one token at a time: `Bearer <server.valid>`. */
function acceptOnly(server: { valid: string | null }) {
  return (request: Request) => (request.headers.get('Authorization') === `Bearer ${server.valid}` ? 200 : 401);
}

/** A fake auth module: `renew()` hands the test a deferred per attempt, and a resolved attempt becomes current(). */
function fakeSession(initial: string | null) {
  let token = initial;
  const attempts: Array<Deferred<string>> = [];
  const renew = vi.fn(() => {
    const attempt = deferred<string>();
    attempts.push(attempt);
    return attempt.promise.then((fresh) => {
      token = fresh;
      return fresh;
    });
  });
  const tokens: SessionTokens = { current: () => token, renew };
  return { tokens, renew, attempts };
}

/** Lets scheduled microtasks and promise chains (including the failure hook) run. */
const settle = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

let failures: unknown[];
const onRenewalFailed = (reason: unknown) => {
  failures.push(reason);
};

beforeEach(() => {
  failures = [];
  installBrowserRequest();
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('parallel401sTriggerExactlyOneRenewal', () => {
  it('renews once for five concurrent 401s and re-sends each once with the new token (AC1, AC2, AC6)', async () => {
    const server = { valid: 'new' };
    const sent = installTransport(acceptOnly(server));
    const session = fakeSession('old');
    const api = createAuthApi(createSessionBoundary(session.tokens, onRenewalFailed));
    const consoleSpies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((level) =>
      vi.spyOn(console, level).mockImplementation(() => undefined),
    );
    const hrefBefore = window.location.href;

    const calls = Promise.all([
      api.GET('/accounts/me'),
      api.GET('/accounts/me'),
      api.GET('/accounts/me/sessions'),
      api.GET('/api-keys'),
      api.POST('/api-keys', { body: { name: 'ci key' } }),
    ]);
    await vi.waitFor(() => expect(sent).toHaveLength(5));
    expect(session.renew).toHaveBeenCalledTimes(1);
    session.attempts[0]!.resolve('new');
    const results = await calls;

    expect(session.renew).toHaveBeenCalledTimes(1);
    expect(results.map((result) => result.response.status)).toEqual([200, 200, 200, 200, 200]);
    expect(sent).toHaveLength(10);
    expect(sent.slice(0, 5).every((request) => request.auth === 'Bearer old')).toBe(true);
    expect(sent.slice(5).every((request) => request.auth === 'Bearer new')).toBe(true);
    const resentPost = sent.slice(5).find((request) => request.method === 'POST');
    expect(resentPost).toEqual({ method: 'POST', path: '/api-keys', auth: 'Bearer new', body: '{"name":"ci key"}' });
    expect(failures).toEqual([]);

    // L3: no token reaches storage, the URL or the console.
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(window.location.href).toBe(hrefBefore);
    for (const spy of consoleSpies) {
      const logged = JSON.stringify(spy.mock.calls);
      expect(logged).not.toContain('old');
      expect(logged).not.toContain('new');
    }
  });

  it('shares one renewal across every client on the boundary, and only there (AC1, R21 per tab)', async () => {
    const server = { valid: 'new' };
    installTransport(acceptOnly(server));

    const shared = fakeSession('old');
    const boundary = createSessionBoundary(shared.tokens, onRenewalFailed);
    const first = createAuthApi(boundary);
    const second = createAuthApi(boundary);
    const sharedCalls = Promise.all([first.GET('/accounts/me'), second.GET('/api-keys')]);
    await vi.waitFor(() => expect(shared.renew).toHaveBeenCalledTimes(1));
    shared.attempts[0]!.resolve('new');
    expect((await sharedCalls).map((result) => result.response.status)).toEqual([200, 200]);
    expect(shared.renew).toHaveBeenCalledTimes(1);

    // Contrast: two boundaries are two renewal domains, which is why production creates exactly one per tab.
    const split = fakeSession('old');
    const left = createAuthApi(createSessionBoundary(split.tokens, onRenewalFailed));
    const right = createAuthApi(createSessionBoundary(split.tokens, onRenewalFailed));
    const splitCalls = Promise.all([left.GET('/accounts/me'), right.GET('/accounts/me')]);
    await vi.waitFor(() => expect(split.renew).toHaveBeenCalledTimes(2));
    split.attempts.forEach((attempt) => attempt.resolve('new'));
    await splitCalls;
  });

  it('re-sends a late old-token 401 without renewing, and renews once per later wave (AC3)', async () => {
    const server = { valid: 'new' as string };
    const slowSessionsList = deferred<void>();
    const sent = installTransport(async (request) => {
      const path = new URL(request.url).pathname;
      if (path === '/accounts/me/sessions' && request.headers.get('Authorization') === 'Bearer old') {
        await slowSessionsList.promise;
      }
      return acceptOnly(server)(request);
    });
    const session = fakeSession('old');
    const api = createAuthApi(createSessionBoundary(session.tokens, onRenewalFailed));

    // `late` is sent with the old token and its 401 is held back until the renewal has finished.
    const late = api.GET('/accounts/me/sessions');
    const early = api.GET('/accounts/me');
    await vi.waitFor(() => expect(session.renew).toHaveBeenCalledTimes(1));
    session.attempts[0]!.resolve('new');
    expect((await early).response.status).toBe(200);
    slowSessionsList.resolve();
    expect((await late).response.status).toBe(200);
    expect(session.renew).toHaveBeenCalledTimes(1);
    expect(sent.filter((request) => request.path === '/accounts/me/sessions').map((request) => request.auth)).toEqual([
      'Bearer old',
      'Bearer new',
    ]);

    // The server rotates again: a fresh wave of 401s with the current token is exactly one new renewal.
    server.valid = 'newer';
    const wave = Promise.all([api.GET('/accounts/me'), api.GET('/api-keys'), api.GET('/accounts/me')]);
    await vi.waitFor(() => expect(session.renew).toHaveBeenCalledTimes(2));
    session.attempts[1]!.resolve('newer');
    expect((await wave).map((result) => result.response.status)).toEqual([200, 200, 200]);
    expect(session.renew).toHaveBeenCalledTimes(2);
  });

  it('on a failed renewal gives every waiter its 401, reports once and re-sends nothing (AC4)', async () => {
    const sent = installTransport(() => 401);
    const session = fakeSession('old');
    const api = createAuthApi(createSessionBoundary(session.tokens, onRenewalFailed));

    const calls = Promise.all([api.GET('/accounts/me'), api.GET('/api-keys'), api.GET('/accounts/me/sessions')]);
    await vi.waitFor(() => expect(sent).toHaveLength(3));
    const noSession = new Error('no SAS session');
    session.attempts[0]!.reject(noSession);
    const results = await calls;
    await settle();

    expect(results.map((result) => result.response.status)).toEqual([401, 401, 401]);
    expect(session.renew).toHaveBeenCalledTimes(1);
    expect(failures).toEqual([noSession]);
    expect(sent).toHaveLength(3);
  });

  it('does not start a second renewal for a request that waited on the failed one (AC4, phase 9)', async () => {
    const sent = installTransport(() => 401);
    const session = fakeSession('old');
    const api = createAuthApi(createSessionBoundary(session.tokens, onRenewalFailed));

    const first = api.GET('/accounts/me');
    await vi.waitFor(() => expect(session.renew).toHaveBeenCalledTimes(1));
    const waiting = api.GET('/api-keys');
    await settle();
    expect(sent).toHaveLength(1); // it waits for the renewal before it is sent
    session.attempts[0]!.reject(new Error('no SAS session'));

    expect((await first).response.status).toBe(401);
    expect((await waiting).response.status).toBe(401);
    await settle();
    expect(session.renew).toHaveBeenCalledTimes(1);
    expect(failures).toHaveLength(1);
    expect(sent.map((request) => `${request.path} ${request.auth}`)).toEqual([
      '/accounts/me Bearer old',
      '/api-keys Bearer old',
    ]);
  });

  it('treats a renewal that never settles as failed after the timeout, ignoring its late result (AC4)', async () => {
    expect(RENEWAL_TIMEOUT_MS).toBe(30_000);
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    const sent = installTransport(() => 401);
    const session = fakeSession('old');
    const api = createAuthApi(createSessionBoundary(session.tokens, onRenewalFailed, { timeoutMs: 1_000 }));

    const calls = Promise.all([api.GET('/accounts/me'), api.GET('/api-keys')]);
    await vi.advanceTimersByTimeAsync(999);
    expect(session.renew).toHaveBeenCalledTimes(1);
    expect(failures).toEqual([]);
    await vi.advanceTimersByTimeAsync(1);
    expect((await calls).map((result) => result.response.status)).toEqual([401, 401]);
    await vi.advanceTimersByTimeAsync(0);
    expect(failures).toHaveLength(1);
    expect((failures[0] as Error).name).toBe('RenewalTimeoutError');

    session.attempts[0]!.resolve('late');
    await vi.advanceTimersByTimeAsync(0);
    expect(sent).toHaveLength(2);
    expect(session.renew).toHaveBeenCalledTimes(1);
    expect(failures).toHaveLength(1);
  });

  it('returns a 401 on the re-send as final, with no second renewal (AC4)', async () => {
    const sent = installTransport(() => 401);
    const session = fakeSession('old');
    const api = createAuthApi(createSessionBoundary(session.tokens, onRenewalFailed));

    const call = api.GET('/accounts/me');
    await vi.waitFor(() => expect(session.renew).toHaveBeenCalledTimes(1));
    session.attempts[0]!.resolve('new');

    expect((await call).response.status).toBe(401);
    await settle();
    expect(session.renew).toHaveBeenCalledTimes(1);
    expect(sent.map((request) => request.auth)).toEqual(['Bearer old', 'Bearer new']);
    expect(failures).toEqual([]);
  });

  it("keeps a throwing failure hook away from the waiters; its error surfaces uncaught, not swallowed (AC4)", async () => {
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
    installTransport(() => 401);
    const session = fakeSession('old');
    const hookDefect = new Error('hook defect');
    const api = createAuthApi(
      createSessionBoundary(session.tokens, () => {
        throw hookDefect;
      }),
    );

    const calls = Promise.all([api.GET('/accounts/me'), api.GET('/api-keys')]);
    await vi.waitFor(() => expect(session.renew).toHaveBeenCalledTimes(1));
    session.attempts[0]!.reject(new Error('no SAS session'));

    expect((await calls).map((result) => result.response.status)).toEqual([401, 401]);
    await settle();
    expect(uncaught).toEqual([hookDefect]);
  });

  it.each([200, 403, 429, 500])('never renews or re-sends on a %i (AC5)', async (status) => {
    const sent = installTransport(() => status);
    const session = fakeSession('old');
    const api = createAuthApi(createSessionBoundary(session.tokens, onRenewalFailed));

    const result = await api.GET('/accounts/me');
    await settle();

    expect(result.response.status).toBe(status);
    expect(session.renew).not.toHaveBeenCalled();
    expect(sent).toHaveLength(1);
    expect(sent[0]!.auth).toBe('Bearer old');
  });

  it('holds a request started mid-renewal until the renewal is done, then sends the new token (AC6)', async () => {
    const server = { valid: 'new' };
    const sent = installTransport(acceptOnly(server));
    const session = fakeSession('old');
    const api = createAuthApi(createSessionBoundary(session.tokens, onRenewalFailed));

    const first = api.GET('/accounts/me');
    await vi.waitFor(() => expect(session.renew).toHaveBeenCalledTimes(1));
    const during = api.GET('/api-keys');
    await settle();
    expect(sent.map((request) => request.path)).toEqual(['/accounts/me']);
    session.attempts[0]!.resolve('new');

    expect((await during).response.status).toBe(200);
    expect((await first).response.status).toBe(200);
    expect(sent.find((request) => request.path === '/api-keys')!.auth).toBe('Bearer new');
    expect(session.renew).toHaveBeenCalledTimes(1);
  });

  it('sends no Authorization without a token, and still renews once on its 401 (AC6, the reload case)', async () => {
    const server = { valid: 'restored' };
    const sent = installTransport(acceptOnly(server));
    const session = fakeSession(null);
    const api = createAuthApi(createSessionBoundary(session.tokens, onRenewalFailed));

    const call = api.GET('/accounts/me');
    await vi.waitFor(() => expect(session.renew).toHaveBeenCalledTimes(1));
    session.attempts[0]!.resolve('restored');

    expect((await call).response.status).toBe(200);
    expect(sent.map((request) => request.auth)).toEqual([null, 'Bearer restored']);
    expect(session.renew).toHaveBeenCalledTimes(1);
  });

  it('sends the public operations without a bearer and never renews on their 401 (AC7)', async () => {
    const sent = installTransport(() => 401);
    const session = fakeSession('old');
    const api = createAuthApi(createSessionBoundary(session.tokens, onRenewalFailed));

    const results = await Promise.all([
      api.POST('/accounts', { body: { email: 'someone@example.com', password: 'twelve-chars-ok' } }),
      api.POST('/accounts/verify-email', { body: { token: 'verification-token' } }),
      api.POST('/accounts/resend-verification', { body: { email: 'someone@example.com' } }),
      api.POST('/accounts/password-reset-request', { body: { email: 'someone@example.com' } }),
      api.POST('/accounts/password-reset', { body: { token: 'reset-token', newPassword: 'twelve-chars-ok' } }),
    ]);
    await settle();

    expect(results.map((result) => result.response.status)).toEqual([401, 401, 401, 401, 401]);
    expect(sent).toHaveLength(5);
    expect(sent.map((request) => request.auth)).toEqual([null, null, null, null, null]);
    expect(session.renew).not.toHaveBeenCalled();
  });

  it("lists exactly auth.yaml's security: [] operations as public (AC7)", async () => {
    const contract = await readFile(join(repoRoot, 'contracts/api/auth.yaml'), 'utf8');
    const declaredPublic: string[] = [];
    let path = '';
    let method = '';
    for (const line of contract.split('\n')) {
      const pathLine = /^ {2}(\/\S*):\s*$/.exec(line);
      if (pathLine) path = pathLine[1]!;
      const methodLine = /^ {4}(get|put|post|patch|delete):\s*$/.exec(line);
      if (methodLine) method = methodLine[1]!.toUpperCase();
      if (/^ {6}security: \[\]\s*$/.test(line)) declaredPublic.push(`${method} ${path}`);
    }

    expect(declaredPublic.length).toBeGreaterThan(0);
    expect([...AUTH_PUBLIC_OPERATIONS].sort()).toEqual(declaredPublic.sort());
  });

  it('still renews once for an uncloneable request but returns its 401 instead of re-sending (clone fallback)', async () => {
    const server = { valid: 'new' };
    const sent = installTransport(acceptOnly(server));
    vi.spyOn(Request.prototype, 'clone').mockImplementation(() => {
      throw new TypeError('body cannot be cloned');
    });
    const session = fakeSession('old');
    const api = createAuthApi(createSessionBoundary(session.tokens, onRenewalFailed));

    const call = api.GET('/accounts/me');
    await vi.waitFor(() => expect(session.renew).toHaveBeenCalledTimes(1));
    session.attempts[0]!.resolve('new');

    expect((await call).response.status).toBe(401);
    expect(sent).toHaveLength(1);
    expect(session.tokens.current()).toBe('new');
  });
});
