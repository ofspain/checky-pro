/**
 * T52 — `passwordResetRequestAcknowledgementIsUniform` (`package.md` §8), R14.
 *
 * **R14 IS TWO OBLIGATIONS AND THE SECOND ONE IS THE TASK.** The call is easy to get right and easy to
 * verify. "SHALL show the **same** uniform acknowledgement" is the half that rots silently, because the
 * screen that leaks is indistinguishable from the screen that does not until someone sends it a reply the
 * happy path never produces.
 *
 * **WHAT "EVERY OUTCOME" MEANS HERE, AND WHY IT IS NOT THE CONTRACT'S LIST.** `auth.yaml` declares exactly
 * one response for `requestPasswordReset` - a `200` whose own description is *"Always returned regardless of
 * whether the email matches an account"* - so a CONFORMING server cannot distinguish anything, and a suite
 * that only exercised declared replies would be vacuous by construction. The replies that can actually
 * differ come from the path between this screen and that server: an edge interstitial, a proxy answering
 * plain text, a connection cut mid-document, a `304` from a cache. **That is what the arm table below is
 * made of**, and task 51 found a real defect in exactly that gap on the sibling screen.
 *
 * **`response.ok` IS DELIBERATELY NOT CONSULTED**, which is the mirror image of `VerifyScreen`: R4 required
 * failure to be distinguishable there and `ok` was the one thing read. Here one acknowledgement IS the
 * requirement, so every resolved reply acknowledges. A mutant that consults it must kill the arm table, or
 * the table is decoration.
 *
 * **THE EMAIL IS THE OTHER ENUMERATION CHANNEL.** L4 covers copy, redirects, styling and timing; a form that
 * put the address in the URL would leak it to history, the referer header and any log in between while every
 * copy assertion in this file still passed. The storage spies here are on `Storage.prototype`, not on an
 * instance, because an instance spy on jsdom storage **does not intercept** - that mistake made a task 42
 * claim vacuous from birth, and it survived because the assertion was NEGATIVE.
 */
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { act, StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider, type createMemoryRouter as CreateMemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { APP_BASENAME, appRoutes, PASSWORD_RESET_REQUEST_PATH, PUBLIC_ROUTE_PATHS } from '../../app/routes';
import { en } from '../../i18n/catalogue';
import { createI18n } from '../../i18n/i18n';

// THREE `dirname`s, FROM `testPath`, which is the house pattern and not a style choice: this file sits at
// `src/features/account/`, so two of them land on `src/features` and every source read below resolves to
// `src/features/features/account/...`. The first run of this suite failed three cases that way - an ENOENT
// where the intended failure was "the screen does not exist yet", which is a different claim.
const srcDir = dirname(dirname(dirname(expect.getState().testPath as string)));
const read = (relativePath: string) => readFile(join(srcDir, relativePath), 'utf8');

/**
 * THE SOURCE SCANS BELOW READ CODE, NOT PROSE, and the first run of this suite proved why: the screen's own
 * header explains that `response.ok` is deliberately NOT consulted, and the forbidden-read scan matched that
 * sentence. The bare-element scan did the same with an `<h1>` inside a paragraph about the document outline.
 * Two cases that were measuring the documentation instead of the module - which is T173's lesson arriving
 * from the other direction, since that suite needed a control to prove a comment naming `<div>` is not a tag.
 */
const withoutComments = (code: string) =>
  code.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');

/** What the form state renders, in order, so a case can assert the whole screen rather than a fragment. */
const FORM_TEXT = `${en.passwordResetRequest.heading}${en.passwordResetRequest.emailLabel}`
  + `${en.passwordResetRequest.submit}`;
/** And what replaces it. */
const ACK_TEXT = `${en.passwordResetRequest.acknowledgedHeading}${en.passwordResetRequest.acknowledgedText}`;

const VISITOR = 'visitor@example.test';

let mounted: { root: Root; container: HTMLElement } | undefined;
let router: ReturnType<typeof CreateMemoryRouter> | undefined;

/** Node's `Request` rejects a relative URL; a browser resolves it against the document (task 49's T05). */
function installBrowserRequest(): void {
  const NodeRequest = globalThis.Request;
  vi.stubGlobal('Request', class extends NodeRequest {
    constructor(input: RequestInfo | URL, init?: RequestInit) {
      super(typeof input === 'string' ? new URL(input, window.location.href) : input, init);
    }
  });
}

type Sent = { path: string; method: string; body: string; authorization: string | null };

/**
 * The transport every arm is built from. `contentType` is a parameter because it is the obvious suspect for
 * what makes a reply parseable, and it is **not** the thing that decides: `openapi-fetch` parses on its own
 * behalf, so a body that is not valid JSON rejects under `application/json`, `text/html` and no header
 * alike. The fourth copy of this helper in `features/account/` (FU-T43-2).
 */
function installTypedTransport(status = 200, body: string | null = '{"message":"server copy"}',
  contentType: string | null = 'application/json'): { sent: Sent[] } {
  const sent: Sent[] = [];
  vi.stubGlobal('fetch', vi.fn(async (request: Request) => {
    sent.push({
      path: new URL(request.url).pathname,
      method: request.method,
      body: await request.clone().text(),
      authorization: request.headers.get('authorization'),
    });
    return new Response(body, {
      status,
      ...(contentType === null ? {} : { headers: { 'Content-Type': contentType } }),
    });
  }));
  return { sent };
}

/** A transport that never answers, for the in-flight assertions. */
function installNeverAnswering(): void {
  vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => {})));
}

/** A transport that fails the way a dropped connection does: the promise REJECTS. */
function installTransportFailure(): { calls: () => number } {
  const fetchMock = vi.fn(() => Promise.reject(new TypeError('Failed to fetch')));
  vi.stubGlobal('fetch', fetchMock);
  return { calls: () => fetchMock.mock.calls.length };
}

const settle = () => act(async () => {
  await Promise.resolve();
  await Promise.resolve();
});

/** Mounts the REAL route table at an `/app` location, so no case can pass on an unreachable screen. */
async function arrive(path: string, options: { strict?: boolean } = {}): Promise<HTMLElement> {
  const container = document.createElement('div');
  document.body.append(container);
  router = createMemoryRouter(appRoutes, {
    basename: APP_BASENAME,
    initialEntries: [`${APP_BASENAME}/${path}`],
  });
  const root = createRoot(container);
  mounted = { root, container };
  const tree = (
    <I18nextProvider i18n={createI18n()}>
      <RouterProvider router={router} />
    </I18nextProvider>
  );
  await act(async () => {
    root.render(options.strict === true ? <StrictMode>{tree}</StrictMode> : tree);
  });
  await settle();
  return container;
}

const field = (container: HTMLElement) => container.querySelector<HTMLInputElement>('input[type="email"]')!;
const submitControl = (container: HTMLElement) =>
  container.querySelector<HTMLButtonElement>('button[type="submit"]')!;
const region = (container: HTMLElement) => container.querySelector('[role="status"]');

/** `await`ed, because `type` is async and calling it without one gets "'set value' called on an object that
 *  is not a valid instance of HTMLInputElement" on every arm after the first (T51). */
async function type(input: HTMLInputElement, value: string): Promise<void> {
  await act(async () => {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
    setter.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

/** The whole journey: arrive through the real table, fill the real form, submit, let everything settle. */
async function send(email = VISITOR, options: { strict?: boolean } = {}): Promise<HTMLElement> {
  const container = await arrive(PASSWORD_RESET_REQUEST_PATH, options);
  await type(field(container), email);
  await act(async () => {
    submitControl(container).click();
  });
  await settle();
  return container;
}

beforeEach(() => {
  installBrowserRequest();
});

afterEach(async () => {
  if (mounted) {
    const { root, container } = mounted;
    await act(async () => root.unmount());
    container.remove();
    mounted = undefined;
  }
  router?.dispose?.();
  router = undefined;
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  window.sessionStorage.clear();
  window.localStorage.clear();
});

describe('passwordResetRequestAcknowledgementIsUniform', () => {
  // ==========================================================================================================
  // AC1 / AC2 — one correct, public call
  // ==========================================================================================================

  it('posts the email once to the declared path (AC1)', async () => {
    const { sent } = installTypedTransport();
    await send();

    expect(sent).toHaveLength(1);
    expect(sent[0]!.path, 'the screen called a path the contract does not declare')
      .toBe('/accounts/password-reset-request');
    expect(sent[0]!.method).toBe('POST');
    expect(JSON.parse(sent[0]!.body), 'the body is not the contract\'s PasswordResetRequest')
      .toEqual({ email: VISITOR });
  });

  it('sends no Authorization header EVEN WITH A SESSION SEEDED (AC2)', async () => {
    // THE HALF THAT COULD ROT SILENTLY. `security: []` makes this endpoint public, and every reachability
    // case in this file would still pass if the authenticated client factory were swapped in - the only
    // thing that would change is a bearer appearing on a public request.
    window.sessionStorage.setItem('themistra.returnTarget', '/app/home');
    const { sent } = installTypedTransport();
    await send();

    expect(sent[0]!.authorization, 'a public reset request carried a bearer token').toBeNull();
  });

  it('renders and submits with nothing signed in at all (AC2)', async () => {
    const { sent } = installTypedTransport();
    const container = await send();

    expect(container.textContent, 'the screen did not acknowledge without a session').toContain(ACK_TEXT);
    expect(sent).toHaveLength(1);
  });

  it('sends exactly one request under StrictMode double invocation (AC1)', async () => {
    const { sent } = installTypedTransport();
    await send(VISITOR, { strict: true });

    expect(sent, 'StrictMode produced a second reset request').toHaveLength(1);
  });

  // ==========================================================================================================
  // AC3 / AC5 / AC9 — the acknowledgement, where it comes from, and how it is announced
  // ==========================================================================================================

  it('replaces the form with a non-empty acknowledgement from the catalogue (AC3)', async () => {
    installTypedTransport();
    const container = await send();

    expect(container.textContent).toContain(ACK_TEXT);
    expect(container.querySelector('form'), 'the form survived the acknowledgement, so a second send is '
      + 'possible against an acknowledgement that already says a link is on its way').toBeNull();
    expect(en.passwordResetRequest.acknowledgedText.length, 'the acknowledgement is empty, so every '
      + 'comparison in this file is vacuous').toBeGreaterThan(20);
  });

  it('renders nothing the server sent (AC5)', async () => {
    installTypedTransport(200, '{"message":"account found, link queued for visitor@example.test"}');
    const container = await send();

    expect(container.textContent, 'the server\'s own sentence reached the screen')
      .not.toContain('account found');
    expect(container.textContent).not.toContain('queued');
  });

  it('moves focus to the heading, and the screen holds exactly one h1 (AC9)', async () => {
    installTypedTransport();
    const container = await send();

    const headings = container.querySelectorAll('h1');
    expect(headings, 'a second h1 would break the document outline L11 requires').toHaveLength(1);
    expect(document.activeElement, 'focus stayed on document.body after the submit button unmounted')
      .toBe(headings[0]);
    expect(headings[0]!.textContent).toBe(en.passwordResetRequest.acknowledgedHeading);
  });

  it('announces the in-flight state in the live region, and shows no acknowledgement yet (AC9)', async () => {
    installNeverAnswering();
    const container = await arrive(PASSWORD_RESET_REQUEST_PATH);
    await type(field(container), VISITOR);
    await act(async () => {
      submitControl(container).click();
    });
    await settle();

    expect(region(container)?.textContent).toBe(en.passwordResetRequest.submitting);
    expect(container.textContent, 'the screen acknowledged a request that has not been answered')
      .not.toContain(en.passwordResetRequest.acknowledgedText);
    expect(container.querySelector('form'), 'the form went away before the reply arrived').not.toBeNull();
  });

  // ==========================================================================================================
  // AC4 — THE TASK. One acknowledgement for every outcome.
  // ==========================================================================================================

  /**
   * Fifteen arms. The declared `200` is first so the comparison has a known-good baseline, and the rest are
   * what the path between this screen and the server can produce. `204` and `304` carry NO body because
   * `new Response(body, { status: 204 })` THROWS - they are null-body statuses, which is also why a
   * conforming fixture could never reproduce the task-51 defect.
   */
  const ARMS: ReadonlyArray<{ label: string; status: number; body: string | null; contentType: string | null;
    serverWords?: string }> = [
    // THE DECLARED ARM'S `message` DELIBERATELY AVOIDS THE SCREEN'S OWN WORDS. Its first draft used the
    // contract's own example sentence, "Check your email" - which is also `acknowledgedHeading`, so the leak
    // case below could never pass that arm and reported a leak that was the catalogue doing its job. A leak
    // detector whose needle is part of the haystack's intended content measures nothing and lies about it.
    { label: 'the declared 200 with JSON', status: 200, body: '{"message":"queued for delivery to a known address"}', contentType: 'application/json', serverWords: 'queued for delivery' },
    { label: '200 whose message differs', status: 200, body: '{"message":"no account matched that address"}', contentType: 'application/json', serverWords: 'no account matched' },
    { label: '200 with no body', status: 200, body: null, contentType: 'application/json' },
    { label: '200 with an empty string body', status: 200, body: '', contentType: 'application/json' },
    { label: '200 with an HTML error page, as an edge returns', status: 200, body: '<html><body>502 Bad Gateway</body></html>', contentType: 'text/html', serverWords: 'Bad Gateway' },
    { label: '200 with plain text, as a proxy returns', status: 200, body: 'no account matched: nothing queued', contentType: 'text/plain', serverWords: 'nothing queued' },
    { label: '200 with JSON truncated mid-document', status: 200, body: '{"message":"nothing was queued at al', contentType: 'application/json', serverWords: 'nothing was queued' },
    { label: '200 with no content-type at all', status: 200, body: '{"message":"ok"}', contentType: null },
    { label: '204', status: 204, body: null, contentType: null },
    { label: '400', status: 400, body: '{"detail":"email is not valid"}', contentType: 'application/problem+json', serverWords: 'not valid' },
    { label: '404', status: 404, body: '{"detail":"no such account"}', contentType: 'application/problem+json', serverWords: 'no such account' },
    { label: '429', status: 429, body: '{"detail":"too many attempts for this address"}', contentType: 'application/problem+json', serverWords: 'too many attempts' },
    { label: '500', status: 500, body: '{"detail":"mail transport down"}', contentType: 'application/problem+json', serverWords: 'transport down' },
    { label: '503', status: 503, body: '<html>maintenance</html>', contentType: 'text/html', serverWords: 'maintenance' },
    { label: '302 carrying a body', status: 302, body: '{"message":"moved"}', contentType: 'application/json' },
  ] as const;

  /** ONE definition, used by the guard above and the assertion below, so the two cannot drift apart and
   *  leave the guard checking a different string from the one actually searched for. */
  const needle = (words: string) => words.toLowerCase().slice(0, 12);

  type Arm = { html: string; pathname: string; focus: string | null; regionText: string; text: string };

  async function renderArm(arm: (typeof ARMS)[number]): Promise<Arm> {
    installTypedTransport(arm.status, arm.body, arm.contentType);
    const container = await send();
    const captured: Arm = {
      html: container.innerHTML,
      pathname: router!.state.location.pathname,
      // `innerHTML` DOES NOT CARRY `document.activeElement`, so focus is a channel a markup comparison is
      // blind to. It is captured separately for that reason (T51).
      focus: document.activeElement === document.body ? 'body'
        : (document.activeElement?.tagName.toLowerCase() ?? null),
      regionText: region(container)?.textContent ?? '',
      text: container.textContent ?? '',
    };
    if (mounted) {
      const { root, container: node } = mounted;
      await act(async () => root.unmount());
      node.remove();
      mounted = undefined;
      router?.dispose?.();
      router = undefined;
    }
    vi.unstubAllGlobals();
    installBrowserRequest();
    return captured;
  }

  it('renders the IDENTICAL acknowledgement for all fifteen outcomes (AC4, R14)', async () => {
    const rendered: Arm[] = [];
    for (const arm of ARMS) rendered.push(await renderArm(arm));

    // THE BASELINE IS GUARDED BEFORE ANYTHING IS COMPARED TO IT. Fifteen identically BROKEN screens are also
    // identical, and that is how a uniformity claim passes while measuring nothing (T51).
    const baseline = rendered[0]!;
    expect(baseline.html.length, 'the baseline renders almost nothing, so every comparison below is vacuous')
      .toBeGreaterThan(100);
    expect(baseline.text, 'the baseline is not the acknowledgement').toContain(ACK_TEXT);
    expect(baseline.html, 'the baseline still has a form, so the submit never settled').not.toContain('<form');
    expect(baseline.focus, 'the baseline never moved focus, so the focus column cannot discriminate')
      .toBe('h1');

    for (const [index, arm] of ARMS.entries()) {
      const got = rendered[index]!;
      expect(got.html, `markup differs for ${arm.label}`).toBe(baseline.html);
      expect(got.pathname, `the pathname differs for ${arm.label}`).toBe(baseline.pathname);
      expect(got.focus, `the focus target differs for ${arm.label}`).toBe(baseline.focus);
      expect(got.regionText, `the live region differs for ${arm.label}`).toBe(baseline.regionText);
    }
  });

  it('leaks none of the words the server sent, in any arm (AC4, AC5, L4)', async () => {
    // THE COMPARISON ABOVE IS NOT ENOUGH ON ITS OWN. Fifteen arms that all rendered the SERVER's sentence
    // would also be identical to each other; what makes them safe is that none of them renders it.
    const withWords = ARMS.filter((arm) => arm.serverWords !== undefined);
    expect(withWords.length, 'no arm carries distinguishing server words, so this case measures nothing')
      .toBeGreaterThan(8);

    // EVERY NEEDLE MUST BE ABSENT FROM THE HAYSTACK'S INTENDED CONTENT, and this guard exists because the
    // same mistake was made TWICE in one sitting: the declared arm and the truncated arm were both given the
    // contract's example sentence, "Check your email", which is also `acknowledgedHeading`. The case then
    // reported a leak that was the catalogue rendering its own copy - a false positive, which is the worse
    // direction for a security assertion because it trains the reader to loosen it. Now a colliding needle
    // fails HERE, naming the arm, instead of masquerading as a leak.
    const ownCopy = ACK_TEXT.toLowerCase();
    for (const arm of withWords) {
      expect(ownCopy, `${arm.label}'s server words are part of the screen's OWN acknowledgement, so that `
        + 'arm cannot distinguish a leak from the catalogue').not.toContain(needle(arm.serverWords!));
    }

    for (const arm of withWords) {
      const got = await renderArm(arm);
      expect(got.text.toLowerCase(), `${arm.label} leaked the server's own words`)
        .not.toContain(needle(arm.serverWords!));
    }
  });

  // ==========================================================================================================
  // The failure arm — a dropped connection (OQ-T52-2, ratified at the phase 4 gate)
  // ==========================================================================================================

  it('shows a neutral retry line on a dropped connection, and keeps the form', async () => {
    const { calls } = installTransportFailure();
    const container = await send();

    expect(calls(), 'the submit never reached the transport, so this is not the failure path').toBe(1);
    expect(region(container)?.textContent).toBe(en.passwordResetRequest.unsent);
    expect(container.querySelector('form'), 'the retry is the point, so the form must still be there')
      .not.toBeNull();
    expect(container.textContent, 'a request that never arrived was acknowledged')
      .not.toContain(en.passwordResetRequest.acknowledgedText);
  });

  it('names no cause in the retry line (L4)', async () => {
    installTransportFailure();
    const container = await send();
    const text = (container.textContent ?? '').toLowerCase();

    for (const word of ['lock', 'suspend', 'delet', 'unknown', 'not found', 'no account', 'invalid',
      'expir', 'exist']) {
      expect(text, `the copy contains "${word}", which names a cause L4 forbids naming`).not.toContain(word);
    }
  });

  it('clears a stale retry line when a fresh attempt starts', async () => {
    // `setStatus('submitting')` IS WHAT DOES THIS, and it is the kind of line a later reader deletes as
    // redundant. Without it the retry line sits under a fresh in-flight attempt, telling the visitor the
    // send failed while it is still running.
    installTransportFailure();
    const container = await send();
    expect(region(container)?.textContent).toBe(en.passwordResetRequest.unsent);

    installNeverAnswering();
    await act(async () => {
      submitControl(container).click();
    });
    await settle();

    expect(region(container)?.textContent, 'the failure line survived into a fresh attempt')
      .toBe(en.passwordResetRequest.submitting);
  });

  // ==========================================================================================================
  // AC6 — the email reaches no URL and no storage
  // ==========================================================================================================

  it('puts the email in no URL the browser or the router can see (AC6)', async () => {
    installTypedTransport();
    await send();

    expect(router!.state.location.search, 'the email reached the router\'s query string').toBe('');
    expect(router!.state.location.hash, 'the email reached the router\'s hash').toBe('');
    expect(window.location.search, 'the email reached the real query string').toBe('');
    expect(window.location.hash, 'the email reached the real hash fragment').toBe('');
    expect(window.location.href, 'part of the email reached the real browser URL').not.toContain('visitor');
  });

  it('writes the email to neither storage, and the spy is PROVEN to intercept (AC6, L16)', async () => {
    // THE SPY IS ON `Storage.prototype`. An instance spy on jsdom storage does not intercept at all, which
    // made a task 42 claim vacuous from birth - and it survived because the assertion was NEGATIVE, so the
    // control below is the only thing that makes this case evidence.
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    installTypedTransport();
    await send();

    const written = setItem.mock.calls.map((call) => String(call[1]));
    expect(written.some((value) => value.includes('visitor')), 'the email was written to storage')
      .toBe(false);
    expect(window.sessionStorage.getItem('email')).toBeNull();
    expect(window.localStorage.getItem('email')).toBeNull();

    // CONTROL: the negative above is worthless unless this spy sees a real write.
    window.localStorage.setItem('t52.control', 'visitor@example.test');
    expect(setItem.mock.calls.map((call) => String(call[1])).some((value) => value.includes('visitor')),
      'the prototype spy does not intercept, so the assertions above measured nothing').toBe(true);
  });

  // ==========================================================================================================
  // AC7 / AC10 / AC11 — the call shape, the primitives, and the route
  // ==========================================================================================================

  it('never reads the response, and says so in its source (AC5, AC7)', async () => {
    const source = withoutComments(await read('features/account/PasswordResetRequestScreen.tsx'));

    for (const forbidden of [/\.\s*json\s*\(/, /\bresponse\s*\.\s*ok\b/, /\bresponse\s*\.\s*status\b/,
      /\bdata\b\s*[.?[]/, /\.\s*text\s*\(/]) {
      expect(source, `the screen reads the response (${String(forbidden)}), which one uniform `
        + 'acknowledgement cannot survive').not.toMatch(forbidden);
    }
    expect(source, 'the settle handler takes an argument, so something about the reply is in scope')
      .toMatch(/\.then\s*\(\s*\(\s*\)\s*=>/);
  });

  it('passes parseAs: stream, without which an unparseable reply takes the failure arm (AC7)', async () => {
    const source = await read('features/account/PasswordResetRequestScreen.tsx');
    expect(source, 'the option is missing, so a 200 whose body is not valid JSON would reject and this '
      + 'screen would tell a visitor it could not send a link the server had sent')
      .toContain("parseAs: 'stream'");
  });

  it('renders through the design system only, with no bare element (AC10)', async () => {
    const source = withoutComments(await read('features/account/PasswordResetRequestScreen.tsx'));
    const tags = [...source.matchAll(/<\s*([a-z][a-zA-Z0-9-]*)[\s/>]/g)].map((match) => match[1]);
    expect(tags, 'the screen renders a bare element instead of a primitive').toEqual([]);
  });

  it('keeps reset/request public and resolvable only under /app (AC11)', async () => {
    expect([...PUBLIC_ROUTE_PATHS], 'the path left the public set')
      .toContain(PASSWORD_RESET_REQUEST_PATH);
    expect(PASSWORD_RESET_REQUEST_PATH, 'the spelling moved away from design.md §4c').toBe('reset/request');

    const container = await arrive(PASSWORD_RESET_REQUEST_PATH);
    expect(container.textContent, 'the route resolved to nothing').toContain(FORM_TEXT);
    expect(router!.state.location.pathname, 'the route does not sit under the app basename')
      .toBe(`${APP_BASENAME}/${PASSWORD_RESET_REQUEST_PATH}`);
  });
});
