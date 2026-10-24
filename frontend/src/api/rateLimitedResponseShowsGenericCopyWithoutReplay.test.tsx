import i18next, { type i18n } from 'i18next';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { RateLimitNotice } from '../app/RateLimitNotice';
import { en } from '../i18n/catalogue';
import { createI18n } from '../i18n/i18n';
import { AUTH_PUBLIC_OPERATIONS, createAuthApi, type AuthApi } from './client';
import { createRateLimitNotice, type RateLimitNoticeState } from './rateLimitNotice';
import { createSessionBoundary, type SessionTokens } from './sessionBoundary';

// Lets act() flush React 19 renders synchronously in this test environment.
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

type Reply = { status: number; headers?: Record<string, string>; body?: string };
type Sent = { method: string; path: string; auth: string | null; response?: Response };

/** The live 429, as probed through the edge on 2026-10-22: problem+json, a title, and Retry-After. */
const RATE_LIMITED: Reply = {
  status: 429,
  headers: { 'Content-Type': 'application/problem+json', 'Retry-After': '11' },
  body: JSON.stringify({
    type: 'https://themistra.example/problems/rate-limit-exceeded',
    title: 'Too many requests',
    status: 429,
  }),
};

const PASSWORD_RESET_BODY = { token: 'reset-token', newPassword: 'twelve-chars-ok' };

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

/** Browser-style relative URL resolution: Node's Request rejects relative URLs, a browser resolves them (T05). */
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

/** A fake transport (T05's, plus the reply's headers and body): records every request and the Response it answered
 * with, so a test can prove the caller got that very object back. */
function installTransport(respond: (request: Request) => Reply | Promise<Reply>) {
  const sent: Sent[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (request: Request) => {
      if (request.signal.aborted) throw new DOMException('The operation was aborted.', 'AbortError');
      const record: Sent = {
        method: request.method,
        path: new URL(request.url).pathname,
        auth: request.headers.get('Authorization'),
      };
      sent.push(record);
      const reply = await respond(request);
      const response = new Response(reply.body ?? '{}', {
        status: reply.status,
        headers: reply.headers ?? { 'Content-Type': 'application/json' },
      });
      record.response = response;
      return response;
    }),
  );
  return sent;
}

/** A fake auth module (T05's): `renew()` hands the test a deferred per attempt, a resolved one becomes current(). */
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

/** Lets scheduled microtasks and promise chains run. */
const settle = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

let failures: unknown[];
const onRenewalFailed = (reason: unknown) => {
  failures.push(reason);
};

/** A client on a boundary whose token is always accepted, so only the notice's own behaviour is in play. */
function createApi(notice?: RateLimitNoticeState): AuthApi {
  const tokens: SessionTokens = {
    current: () => 'token',
    renew: vi.fn(() => Promise.reject(new Error('no renewal expected in this case'))),
  };
  return createAuthApi(createSessionBoundary(tokens, onRenewalFailed), notice);
}

let mounted: { root: Root; container: HTMLElement } | undefined;

/** Renders the notice the way wiring must: one `createI18n()` instance per tab, behind `<I18nextProvider>`. */
async function renderNotice(notice: RateLimitNoticeState, instance: i18n = createI18n()) {
  const container = document.createElement('div');
  document.body.append(container);
  const root = createRoot(container);
  await act(async () => {
    root.render(
      <I18nextProvider i18n={instance}>
        <RateLimitNotice notice={notice} />
      </I18nextProvider>,
    );
  });
  mounted = { root, container };
  return {
    container,
    wrapper: container.firstElementChild as HTMLDivElement,
    region: () => container.querySelector('[role="status"]') as HTMLElement,
    button: () => container.querySelector('button'),
    text: () => container.querySelector('[role="status"]')?.textContent ?? null,
  };
}

/** Runs a call with React's updates flushed: the notice's subscriber re-renders the component. */
async function callThrough<T>(run: () => Promise<T>): Promise<T> {
  let result!: T;
  await act(async () => {
    result = await run();
  });
  return result;
}

beforeEach(() => {
  failures = [];
  installBrowserRequest();
});

afterEach(async () => {
  if (mounted) await act(async () => mounted?.root.unmount());
  mounted?.container.remove();
  mounted = undefined;
  vi.useRealTimers();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('rateLimitedResponseShowsGenericCopyWithoutReplay', () => {
  it('shows the generic copy for a 429 on a protected operation (AC1, R23)', async () => {
    const sent = installTransport(() => RATE_LIMITED);
    const notice = createRateLimitNotice();
    const api = createApi(notice);
    const view = await renderNotice(notice);

    expect(notice.isShown()).toBe(false);
    expect(view.text()).toBe('');

    const result = await callThrough(() => api.GET('/accounts/me'));

    expect(result.response.status).toBe(429);
    expect(notice.isShown()).toBe(true);
    expect(view.text()).toBe(en.rateLimit.message);
    expect(view.button()?.textContent).toBe(en.rateLimit.dismiss);
    expect(sent).toHaveLength(1);
    expect(failures).toEqual([]);
  });

  it('shows it for a 429 on the public password-reset operation, the one live SPA 429 today (AC1)', async () => {
    expect(AUTH_PUBLIC_OPERATIONS.has('POST /accounts/password-reset')).toBe(true);
    const sent = installTransport(() => RATE_LIMITED);
    const notice = createRateLimitNotice();
    const api = createApi(notice);
    const view = await renderNotice(notice);

    const result = await callThrough(() =>
      api.POST('/accounts/password-reset', { body: PASSWORD_RESET_BODY }),
    );

    expect(result.response.status).toBe(429);
    expect(sent.map((request) => `${request.method} ${request.path} ${request.auth}`)).toEqual([
      'POST /accounts/password-reset null',
    ]);
    expect(notice.isShown()).toBe(true);
    expect(view.text()).toBe(en.rateLimit.message);
  });

  it("shows it for a 429 on the boundary's re-send after a renewal (AC1, the notice middleware is first)", async () => {
    const session = fakeSession('old');
    const sent = installTransport((request) =>
      request.headers.get('Authorization') === 'Bearer old' ? { status: 401 } : RATE_LIMITED,
    );
    const notice = createRateLimitNotice();
    const api = createAuthApi(createSessionBoundary(session.tokens, onRenewalFailed), notice);
    const view = await renderNotice(notice);

    let status = 0;
    await act(async () => {
      const call = api.GET('/accounts/me');
      await vi.waitFor(() => expect(session.renew).toHaveBeenCalledTimes(1));
      session.attempts[0]!.resolve('new');
      status = (await call).response.status;
    });

    // The 401 is not the final response, and the re-send bypasses middleware: only a notice registered first sees it.
    expect(status).toBe(429);
    expect(sent.map((request) => request.auth)).toEqual(['Bearer old', 'Bearer new']);
    expect(notice.isShown()).toBe(true);
    expect(view.text()).toBe(en.rateLimit.message);
    expect(failures).toEqual([]);
  });

  const COPY_VARIANTS: Array<{ name: string; reply: Reply }> = [
    { name: 'Retry-After 1', reply: { ...RATE_LIMITED, headers: { 'Retry-After': '1' } } },
    { name: 'Retry-After 3600', reply: { ...RATE_LIMITED, headers: { 'Retry-After': '3600' } } },
    { name: 'no Retry-After at all', reply: { status: 429, body: RATE_LIMITED.body } },
    {
      name: 'Retry-After as an HTTP date',
      reply: { ...RATE_LIMITED, headers: { 'Retry-After': 'Wed, 21 Oct 2026 07:28:00 GMT' } },
    },
    {
      name: 'another problem title and detail',
      reply: {
        status: 429,
        headers: { 'Content-Type': 'application/problem+json' },
        body: JSON.stringify({
          type: 'https://themistra.example/problems/edge-limit',
          title: 'Slow down: 42 requests in 7 seconds',
          detail: 'Try again in 59 minutes.',
        }),
      },
    },
    { name: 'an empty body', reply: { status: 429, body: '' } },
    {
      name: 'a non-JSON body',
      reply: {
        status: 429,
        headers: { 'Content-Type': 'text/html' },
        body: '<html><body>429 Too Many Requests — retry in 30 seconds</body></html>',
      },
    },
  ];

  it.each(COPY_VARIANTS)('shows the same copy, and no server text, for $name (AC2)', async ({ reply }) => {
    installTransport(() => reply);
    const notice = createRateLimitNotice();
    const api = createApi(notice);
    const view = await renderNotice(notice);

    const result = await callThrough(() => api.GET('/accounts/me'));

    expect(result.response.status).toBe(429);
    expect(view.text()).toBe(en.rateLimit.message);
    // The whole rendered notice is catalogue copy: nothing from the body, the title or a header reaches it.
    expect(view.container.textContent).toBe(`${en.rateLimit.message}${en.rateLimit.dismiss}`);
  });

  it('handling a 429 calls neither setTimeout nor setInterval (AC3)', async () => {
    installTransport(() => RATE_LIMITED);
    const notice = createRateLimitNotice();
    const api = createApi(notice);
    const setTimeoutSpy = vi.spyOn(globalThis, 'setTimeout');
    const setIntervalSpy = vi.spyOn(globalThis, 'setInterval');

    // No component is mounted here, so only the middleware and the notice run: React's own scheduling can't confuse it.
    const result = await api.GET('/accounts/me');

    expect(result.response.status).toBe(429);
    expect(notice.isShown()).toBe(true);
    expect(setTimeoutSpy).not.toHaveBeenCalled();
    expect(setIntervalSpy).not.toHaveBeenCalled();
  });

  it('renders no digit and no time unit (AC3; asserted on text, since the wrapper carries tabindex="-1")', async () => {
    installTransport(() => RATE_LIMITED);
    const notice = createRateLimitNotice();
    const api = createApi(notice);
    const view = await renderNotice(notice);

    await callThrough(() => api.GET('/accounts/me'));

    expect(view.container.textContent).not.toMatch(/\d/);
    expect(view.container.textContent?.toLowerCase()).not.toMatch(/second|minute|hour|\bms\b/);
    expect(en.rateLimit.message).not.toMatch(/\d/);
    expect(en.rateLimit.dismiss).not.toMatch(/\d/);
    // The only digit in the markup is the focus fix's tabindex, which is not user-facing copy.
    expect(view.wrapper.getAttribute('tabindex')).toBe('-1');
  });

  it('stays shown across an hour and later 200s, clears only on dismiss, re-shows, and notifies once per burst (AC4)', async () => {
    const server = { status: 429 };
    const sent = installTransport(() => (server.status === 429 ? RATE_LIMITED : { status: 200 }));
    const notice = createRateLimitNotice();
    const api = createApi(notice);
    const view = await renderNotice(notice);
    // Faked from here on, before the first 429, so a timer the handling sets would be advanced below.
    vi.useFakeTimers({ toFake: ['setTimeout', 'setInterval', 'clearTimeout', 'clearInterval'] });
    let notifications = 0;
    notice.subscribe(() => {
      notifications += 1;
    });

    // A burst of three 429s: one notice, one notification.
    await callThrough(async () => {
      await api.GET('/accounts/me');
      await api.GET('/api-keys');
      await api.GET('/accounts/me/sessions');
    });
    expect(sent).toHaveLength(3);
    expect(notifications).toBe(1);
    expect(view.text()).toBe(en.rateLimit.message);

    // An hour of timers clears nothing: there is no countdown and no auto-dismiss.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(3_600_000);
    });
    vi.useRealTimers();
    expect(notice.isShown()).toBe(true);
    expect(view.text()).toBe(en.rateLimit.message);

    // Nor does a later success.
    server.status = 200;
    expect((await callThrough(() => api.GET('/accounts/me'))).response.status).toBe(200);
    expect(notice.isShown()).toBe(true);
    expect(view.text()).toBe(en.rateLimit.message);

    // Only the dismiss button does, and it is a native, keyboard-activatable button.
    const button = view.button()!;
    expect(button.tagName).toBe('BUTTON');
    expect(button.type).toBe('button');
    await act(async () => button.click());
    expect(notice.isShown()).toBe(false);
    expect(view.text()).toBe('');
    expect(view.button()).toBeNull();
    expect(notifications).toBe(2);

    // A 429 after a dismissal shows it again, and announces again.
    server.status = 429;
    await callThrough(() => api.GET('/accounts/me'));
    expect(notice.isShown()).toBe(true);
    expect(view.text()).toBe(en.rateLimit.message);
    expect(notifications).toBe(3);
  });

  it('keeps keyboard focus on the notice when the dismiss button is activated (AC4, L11; phase 9)', async () => {
    installTransport(() => RATE_LIMITED);
    const notice = createRateLimitNotice();
    const api = createApi(notice);
    const view = await renderNotice(notice);
    await callThrough(() => api.GET('/accounts/me'));

    const button = view.button()!;
    button.focus();
    expect(document.activeElement).toBe(button);

    await act(async () => button.click());

    // The button is gone, so focus must not have gone with it.
    expect(view.button()).toBeNull();
    expect(document.activeElement).not.toBe(document.body);
    expect(document.activeElement).toBe(view.wrapper);
    expect(view.wrapper.tabIndex).toBe(-1);
  });

  it('leaves focus where it is when the notice is dismissed from elsewhere (AC4; phase 9)', async () => {
    installTransport(() => RATE_LIMITED);
    const notice = createRateLimitNotice();
    const api = createApi(notice);
    const view = await renderNotice(notice);
    await callThrough(() => api.GET('/accounts/me'));

    const elsewhere = document.createElement('input');
    document.body.append(elsewhere);
    elsewhere.focus();
    await act(async () => view.button()!.click());

    expect(notice.isShown()).toBe(false);
    expect(document.activeElement).toBe(elsewhere);
    elsewhere.remove();
  });

  it('puts the copy in a role="status" region that exists and is empty before the 429 (AC5, L11)', async () => {
    installTransport(() => RATE_LIMITED);
    const notice = createRateLimitNotice();
    const api = createApi(notice);
    const view = await renderNotice(notice);

    const regionBefore = view.region();
    expect(regionBefore).not.toBeNull();
    expect(regionBefore.getAttribute('aria-live')).toBe('polite');
    expect(regionBefore.textContent).toBe('');

    await callThrough(() => api.GET('/accounts/me'));

    // The same node, now holding the copy: the announcement is a text change inside a region that already existed.
    expect(view.region()).toBe(regionBefore);
    expect(regionBefore.textContent).toBe(en.rateLimit.message);
    expect(regionBefore.textContent).not.toBe('rateLimit.message');
    // The dismiss button sits outside the region, so it is not part of the announcement.
    expect(regionBefore.querySelector('button')).toBeNull();
    expect(view.button()).not.toBeNull();
  });

  it('takes both strings from the catalogue, not from the component (AC5, O3)', async () => {
    installTransport(() => RATE_LIMITED);
    const notice = createRateLimitNotice();
    const api = createApi(notice);
    const withoutResources = i18next.createInstance();
    void withoutResources.init({
      lng: 'en',
      fallbackLng: 'en',
      resources: { en: { translation: {} } },
      interpolation: { escapeValue: false },
      initAsync: false,
    });
    const view = await renderNotice(notice, withoutResources);

    await callThrough(() => api.GET('/accounts/me'));

    // With the catalogue gone the copy is gone too (the key renders instead); a hardcoded string would survive.
    expect(notice.isShown()).toBe(true);
    expect(view.text()).not.toBe(en.rateLimit.message);
    expect(view.container.textContent).not.toContain(en.rateLimit.message);
    expect(view.container.textContent).not.toContain(en.rateLimit.dismiss);
  });

  it('hands each 429 to the caller unchanged, body included, and calls the transport once (AC6)', async () => {
    const sent = installTransport(() => RATE_LIMITED);
    const notice = createRateLimitNotice();
    const api = createApi(notice);
    await renderNotice(notice);

    const result = await callThrough(() => api.GET('/accounts/me'));

    // The very Response the transport produced: the middleware returns undefined, so nothing re-wraps it.
    expect(result.response).toBe(sent[0]!.response);
    expect(result.response.status).toBe(429);
    expect(result.response.headers.get('Retry-After')).toBe('11');
    expect(result.error).toEqual(JSON.parse(RATE_LIMITED.body!));
    expect(result.data).toBeUndefined();
    // One transport call: no replay was added, and nothing is re-sent on a 429.
    expect(sent).toHaveLength(1);
  });

  it.each([200, 400, 403, 503])('shows nothing for a %i (AC7)', async (status) => {
    const sent = installTransport(() => ({ status }));
    const notice = createRateLimitNotice();
    const api = createApi(notice);
    const view = await renderNotice(notice);

    const result = await callThrough(() => api.GET('/accounts/me'));

    expect(result.response.status).toBe(status);
    expect(notice.isShown()).toBe(false);
    expect(view.text()).toBe('');
    expect(view.button()).toBeNull();
    expect(sent).toHaveLength(1);
  });

  it('shows nothing for a 401, renewed or final (AC7)', async () => {
    const session = fakeSession('old');
    const sent = installTransport((request) => ({
      status: request.headers.get('Authorization') === 'Bearer new' ? 200 : 401,
    }));
    const notice = createRateLimitNotice();
    const api = createAuthApi(createSessionBoundary(session.tokens, onRenewalFailed), notice);
    const view = await renderNotice(notice);

    await act(async () => {
      const call = api.GET('/accounts/me');
      await vi.waitFor(() => expect(session.renew).toHaveBeenCalledTimes(1));
      session.attempts[0]!.resolve('new');
      expect((await call).response.status).toBe(200);
    });
    expect(notice.isShown()).toBe(false);

    // A public operation never renews, so its 401 is final — and still not a rate-limit notice.
    const final = await callThrough(() => api.POST('/accounts/password-reset', { body: PASSWORD_RESET_BODY }));
    expect(final.response.status).toBe(401);
    expect(notice.isShown()).toBe(false);
    expect(view.text()).toBe('');
    expect(sent.map((request) => request.auth)).toEqual(['Bearer old', 'Bearer new', null]);
  });

  it('leaves createAuthApi(boundary) behaving as in T05 when no notice is passed (AC7, L12)', async () => {
    const session = fakeSession('old');
    const sent = installTransport((request) =>
      request.headers.get('Authorization') === 'Bearer new' ? RATE_LIMITED : { status: 401 },
    );
    const notice = createRateLimitNotice(); // deliberately not passed to the client
    const api = createAuthApi(createSessionBoundary(session.tokens, onRenewalFailed));
    const view = await renderNotice(notice);

    await act(async () => {
      const call = api.GET('/accounts/me');
      await vi.waitFor(() => expect(session.renew).toHaveBeenCalledTimes(1));
      session.attempts[0]!.resolve('new');
      expect((await call).response.status).toBe(429);
    });

    // T05's behaviour is intact: one renewal, one re-send with the fresh token, the 429 passed through.
    expect(session.renew).toHaveBeenCalledTimes(1);
    expect(sent.map((request) => request.auth)).toEqual(['Bearer old', 'Bearer new']);
    expect(failures).toEqual([]);
    // And the copy is lost, which is why wiring must pass the notice. The live region itself is always rendered
    // (AC5), so what must be absent is the copy and the control, not the region.
    expect(notice.isShown()).toBe(false);
    expect(view.region()).not.toBeNull();
    expect(view.text()).toBe('');
    expect(view.button()).toBeNull();
  });

  it('writes nothing to storage or cookies, logs nothing, and leaves the URL alone (AC8)', async () => {
    installTransport(() => RATE_LIMITED);
    const notice = createRateLimitNotice();
    const api = createApi(notice);
    const view = await renderNotice(notice);
    const hrefBefore = window.location.href;
    const cookieBefore = document.cookie;
    const consoleSpies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((level) =>
      vi.spyOn(console, level).mockImplementation(() => undefined),
    );

    await callThrough(() => api.GET('/accounts/me'));
    await act(async () => view.button()!.click());

    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(document.cookie).toBe(cookieBefore);
    expect(window.location.href).toBe(hrefBefore);
    expect(consoleSpies.flatMap((spy) => spy.mock.calls)).toEqual([]);
  });

  it('keeps a throwing subscriber away from the response and the component; its error surfaces uncaught', async () => {
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
    const sent = installTransport(() => RATE_LIMITED);
    const notice = createRateLimitNotice();
    const api = createApi(notice);
    const defect = new Error('subscriber defect');
    notice.subscribe(() => {
      throw defect;
    });
    // Registered after the thrower, so it only runs if the notification loop carries on past it.
    let healthy = 0;
    notice.subscribe(() => {
      healthy += 1;
    });
    const view = await renderNotice(notice);
    const cookieBefore = document.cookie;
    const consoleSpies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((level) =>
      vi.spyOn(console, level).mockImplementation(() => undefined),
    );

    const result = await callThrough(() => api.GET('/accounts/me'));
    await act(async () => settle());

    expect(result.response.status).toBe(429);
    expect(notice.isShown()).toBe(true);
    expect(view.text()).toBe(en.rateLimit.message);
    expect(healthy).toBe(1);
    expect(uncaught).toEqual([defect]);
    expect(sent).toHaveLength(1);
    // The error path is no excuse for logging or storing anything (AC8).
    expect(consoleSpies.flatMap((spy) => spy.mock.calls)).toEqual([]);
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(document.cookie).toBe(cookieBefore);
  });

  it('gives one notice and one notification for a protected and a public 429 together (AC4, phase 11 gap 1)', async () => {
    const sent = installTransport(() => RATE_LIMITED);
    const notice = createRateLimitNotice();
    const api = createApi(notice);
    const view = await renderNotice(notice);
    let notifications = 0;
    notice.subscribe(() => {
      notifications += 1;
    });

    // One bearing a token, one not: the notice is the tab's, not the request's.
    const results = await callThrough(() =>
      Promise.all([api.GET('/accounts/me'), api.POST('/accounts/password-reset', { body: PASSWORD_RESET_BODY })]),
    );

    expect(results.map((result) => result.response.status)).toEqual([429, 429]);
    expect(sent.map((request) => `${request.method} ${request.path} ${request.auth}`).sort()).toEqual([
      'GET /accounts/me Bearer token',
      'POST /accounts/password-reset null',
    ]);
    expect(notifications).toBe(1);
    expect(notice.isShown()).toBe(true);
    expect(view.text()).toBe(en.rateLimit.message);
  });

  it('clears on a direct dismiss() call, and shows again on a 429 after a 200 (AC4, phase 11 gaps 2 and 6)', async () => {
    const server = { status: 429 };
    const sent = installTransport(() => (server.status === 429 ? RATE_LIMITED : { status: 200 }));
    const notice = createRateLimitNotice();
    const api = createApi(notice);
    const view = await renderNotice(notice);
    let notifications = 0;
    notice.subscribe(() => {
      notifications += 1;
    });

    await callThrough(() => api.GET('/accounts/me'));
    expect(notifications).toBe(1);

    // dismiss() is public API: wiring or a screen may call it without the button being clicked.
    await act(async () => notice.dismiss());
    expect(notice.isShown()).toBe(false);
    expect(view.text()).toBe('');
    expect(view.button()).toBeNull();
    expect(notifications).toBe(2);

    // Already dismissed: a second call changes nothing and notifies nobody.
    await act(async () => notice.dismiss());
    expect(notifications).toBe(2);

    // 429 -> dismiss -> 200 -> 429: the next episode starts cleanly.
    server.status = 200;
    expect((await callThrough(() => api.GET('/accounts/me'))).response.status).toBe(200);
    expect(notice.isShown()).toBe(false);
    server.status = 429;
    await callThrough(() => api.GET('/accounts/me'));
    expect(notice.isShown()).toBe(true);
    expect(view.text()).toBe(en.rateLimit.message);
    expect(notifications).toBe(3);
    expect(sent).toHaveLength(3);
  });

  it("ignores the body and headers of a 429 on the boundary's re-send too (AC2, phase 11 gap 3)", async () => {
    const session = fakeSession('old');
    const noisy: Reply = {
      status: 429,
      headers: { 'Content-Type': 'text/html', 'Retry-After': '3600' },
      body: '<html><body>Too many requests — wait 60 minutes</body></html>',
    };
    const sent = installTransport((request) =>
      request.headers.get('Authorization') === 'Bearer old' ? { status: 401 } : noisy,
    );
    const notice = createRateLimitNotice();
    const api = createAuthApi(createSessionBoundary(session.tokens, onRenewalFailed), notice);
    const view = await renderNotice(notice);

    await act(async () => {
      const call = api.GET('/accounts/me');
      await vi.waitFor(() => expect(session.renew).toHaveBeenCalledTimes(1));
      session.attempts[0]!.resolve('new');
      expect((await call).response.status).toBe(429);
    });

    expect(view.container.textContent).toBe(`${en.rateLimit.message}${en.rateLimit.dismiss}`);
    expect(sent).toHaveLength(2);
    expect(failures).toEqual([]);
  });
});
