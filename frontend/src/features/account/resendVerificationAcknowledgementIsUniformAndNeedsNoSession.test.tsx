/// <reference types="node" />
/**
 * T50 — `resendVerificationAcknowledgementIsUniformAndNeedsNoSession` (`package.md` §8), R5.
 *
 * **THIS SUITE IS SHARED WITH TASK 51, AND THE SPLIT IS DELIBERATE.** The named test carries two claims and
 * `tasks.md` gives one to each task: task 50 is "the resend form works without a session", task 51 is "the
 * resend ack is identical whatever the outcome". So **nothing here proves uniformity across server
 * outcomes** - one acknowledgement is rendered and task 51 proves it is the same one for every reply. Writing
 * those cases now would leave task 51 with nothing to verify, exactly as task 49 declined to invent task 54's
 * copy.
 *
 * **"NEEDS NO SESSION" IS TWO DIFFERENT CLAIMS AND BOTH ARE HELD BELOW.** One is reachability: the route is
 * public, so the screen renders with nothing signed in. The other is the request: it carries no bearer *even
 * when a session exists*, which is the half that could rot silently - a client factory swapped for the
 * authenticated one would still pass every reachability case in this file.
 *
 * **EVERY CASE ARRIVES THROUGH THE REAL ROUTE TABLE.** The claim is about a public route, and a component
 * mounted directly cannot be unreachable.
 *
 * **THE RESPONSE IS NEVER READ, AND A SOURCE SCAN HOLDS IT AS WELL AS BEHAVIOUR.** `auth.yaml` declares one
 * `200` - "Always returned regardless of whether the email matches an account" - and
 * `AccountService.resendVerificationIfPending` never throws for a non-match, so the server distinguishes
 * nothing for this client to leak. The screen's settle handler therefore takes no argument. One `if` on
 * `response.ok` is all it would take to make this surface reveal an outcome, which is why behaviour alone is
 * not trusted here.
 */
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { act, StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider, type createMemoryRouter as CreateMemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { APP_BASENAME, appRoutes, PUBLIC_ROUTE_PATHS, RESEND_VERIFICATION_PATH } from '../../app/routes';
import { en } from '../../i18n/catalogue';
import { createI18n } from '../../i18n/i18n';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const srcDir = dirname(dirname(dirname(expect.getState().testPath as string)));
const read = (relativePath: string) => readFile(join(srcDir, relativePath), 'utf8');

const EMAIL = 'visitor@example.test';

/** What the form state renders, in order, so a case can assert the whole screen rather than a fragment. */
const FORM_TEXT = `${en.resend.heading}${en.resend.emailLabel}${en.resend.submit}`;
/** And what replaces it. */
const ACK_TEXT = `${en.resend.acknowledgedHeading}${en.resend.acknowledgedText}`;

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
 * Carried over from task 49's suite WITH the assertions that hardened it. The body and the header are read
 * INSIDE the handler because a `Request`'s body can be consumed only once and its headers may be released
 * after the call settles - a case that read them afterwards would be reading a request that no longer exists.
 */
function installTransport(status = 200, body: string | null = '{"message":"server copy"}'): { sent: Sent[] } {
  const sent: Sent[] = [];
  vi.stubGlobal('fetch', vi.fn(async (request: Request) => {
    sent.push({
      path: new URL(request.url).pathname,
      method: request.method,
      body: await request.clone().text(),
      authorization: request.headers.get('authorization'),
    });
    return new Response(body, { status, headers: { 'Content-Type': 'application/json' } });
  }));
  return { sent };
}

/** A transport that never answers, for the in-flight assertions. */
function installNeverAnswering(): void {
  vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => {})));
}

/** A transport that fails the way a dropped connection does: the promise REJECTS (AC16). */
function installTransportFailure(): { calls: () => number } {
  const fetchMock = vi.fn(() => Promise.reject(new TypeError('Failed to fetch')));
  vi.stubGlobal('fetch', fetchMock);
  return { calls: () => fetchMock.mock.calls.length };
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
});

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

const settle = () => act(async () => {
  await Promise.resolve();
  await Promise.resolve();
});

/**
 * THROUGH THE PROTOTYPE'S SETTER, AND THE DESCRIPTOR IS ASSERTED RATHER THAN OPTIONAL-CHAINED PAST.
 * React installs its own `value` setter on the element instance to keep its change tracker in step, so
 * assigning through the instance lets React conclude nothing changed. Two closed tasks have paid to learn
 * this - task 47's I9 and the registration suite's F7/I11, where `setter?.call` silently did nothing and
 * surfaced as an empty request body three cases away from the cause.
 *
 * Copied a fourth time, knowingly: there is still no shared test-util module (FU-T43-2), and this is one more
 * argument for one rather than for quietly copying the fix again.
 */
async function type(input: HTMLInputElement, value: string): Promise<void> {
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
  expect(setter, 'HTMLInputElement has no value setter, so nothing below types anything').toBeTypeOf('function');
  await act(async () => {
    setter?.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

const emailField = (container: HTMLElement) =>
  container.querySelector<HTMLInputElement>('input[type="email"]')!;

/** Fills the real field and presses the real button. */
async function send(container: HTMLElement, email = EMAIL): Promise<void> {
  await type(emailField(container), email);
  await act(async () => {
    container.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
  });
  await settle();
}

// ==========================================================================================================
// AC1, AC8 — the screen exists, on the path that was already declared, and renders in place
// ==========================================================================================================

describe('the resend form is a public screen on the path task 49 left empty (AC1, AC8)', () => {
  it('renders a heading, an email field and a submit control', async () => {
    installTransport();
    const container = await arrive(RESEND_VERIFICATION_PATH);

    expect(container.textContent, 'the resend screen does not render its form').toBe(FORM_TEXT);
    expect(emailField(container), 'no email field').not.toBeNull();
    expect(container.querySelector('button[type="submit"]'), 'no submit control').not.toBeNull();
  });

  it('is reached through the declared route set, not by a path this task invented', async () => {
    // AC8's real content. L14 lists the public paths VERBATIM and `A new public route requires an ADR`, so
    // the claim worth holding is not that the screen renders somewhere - it is that it renders on a path that
    // was already in the declared set BEFORE this task, which is why task 49 could link to it.
    expect(PUBLIC_ROUTE_PATHS, 'the resend path is not in the declared public set')
      .toContain(RESEND_VERIFICATION_PATH);

    const container = await arrive(RESEND_VERIFICATION_PATH);
    expect(router!.state.location.pathname).toBe(`${APP_BASENAME}/${RESEND_VERIFICATION_PATH}`);
    expect(container.textContent).toBe(FORM_TEXT);
  });

  it('acknowledges IN PLACE: no navigation, and the path is unchanged afterwards', async () => {
    // L14 again, from the other side. `design.md` §4c has BOTH `/app/register` and `/app/register/sent`, and
    // for resend it has only `/app/resend-verification` - so unlike registration, this screen cannot send
    // the visitor anywhere. If someone adds a `/sent` sibling, this fails before the ADR is missed.
    installTransport();
    const container = await arrive(RESEND_VERIFICATION_PATH);
    const before = router!.state.location.pathname;

    await send(container);

    expect(router!.state.location.pathname, 'the acknowledgement navigated somewhere').toBe(before);
    expect(container.textContent).toContain(en.resend.acknowledgedText);
  });
});

// ==========================================================================================================
// AC2, AC3, AC11 — no session, not behind the gate, and nothing stored
// ==========================================================================================================

describe('the form needs no signed-in session (AC2, AC3, AC11)', () => {
  it('renders with nothing signed in and nothing in sessionStorage', async () => {
    // The whole of task 50's half of the named test, in its simplest form: no session is established in this
    // file, by any case, ever. There is no sign-in, no token in memory and no storage seeded.
    expect(window.sessionStorage.length, 'the test seeded storage, so this proves nothing').toBe(0);

    installTransport();
    const container = await arrive(RESEND_VERIFICATION_PATH);

    expect(container.textContent).toBe(FORM_TEXT);
  });

  it('is not behind the session gate: the route is a direct child of the root', async () => {
    // AC3 structurally. T40 split the table into nine public children and eleven behind one pathless
    // `RequireSession`, so "public" is a position in the table rather than a property of the screen. Read
    // from the table, because a behavioural case cannot tell a public route from a gate that happened to let
    // it through.
    const resend = appRoutes
      .flatMap((route) => route.children ?? [])
      .filter((child) => 'path' in child && child.path === RESEND_VERIFICATION_PATH);

    expect(resend, 'the resend route is no longer a direct child of the root - it may be behind the gate')
      .toHaveLength(1);
    expect(resend[0], 'the resend route has no element').toHaveProperty('element');
  });

  it('writes nothing to any storage, and a CONTROL proves the spy would see it', async () => {
    // L16 and AC11. **ON `Storage.prototype`, NOT ON THE INSTANCE** - task 42 shipped a storage assertion
    // that was vacuous from birth because an instance spy on jsdom storage does not intercept, and it
    // survived review because the assertion was NEGATIVE. So the control is not optional here: it is the
    // only thing standing between this case and that one.
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    const getItem = vi.spyOn(Storage.prototype, 'getItem');

    installTransport();
    const container = await arrive(RESEND_VERIFICATION_PATH);
    await send(container);

    expect(setItem, 'the resend screen wrote to storage').not.toHaveBeenCalled();

    // THE READS ARE NAMED RATHER THAN FORBIDDEN, because the first version of this case forbade them all and
    // failed - measured, not guessed: the one read is `remix-router-transitions`, which is REACT-ROUTER'S own
    // scroll-restoration bookkeeping and belongs to the router rather than to this screen. Loosening the
    // assertion to `not.toHaveBeenCalled()` on writes alone would have been the easy fix and would have
    // stopped measuring reads entirely; naming the allowed key keeps the claim, so the day this screen reads
    // a session value, this fails.
    expect(getItem.mock.calls.map(([key]) => key), 'the resend screen read a storage key of its own')
      .toEqual(getItem.mock.calls.map(() => 'remix-router-transitions'));

    // CONTROL. Both spies must be able to see a real call, or the two assertions above say nothing.
    window.sessionStorage.setItem('control', 'seen');
    expect(window.sessionStorage.getItem('control')).toBe('seen');
    expect(setItem, 'the setItem spy does not intercept, so the assertion above was vacuous')
      .toHaveBeenCalledWith('control', 'seen');
    expect(getItem, 'the getItem spy does not intercept, so the assertion above was vacuous')
      .toHaveBeenCalledWith('control');
    // And the read assertion above is not vacuous either: it compared a derived list against a derived list,
    // so it has to be shown that the derivation finds something. The router's read is that something.
    expect(getItem.mock.calls.length, 'the getItem spy recorded nothing at all').toBeGreaterThan(0);
    window.sessionStorage.clear();
  });
});

// ==========================================================================================================
// AC4, AC5, AC6 — one call, to the public operation, carrying no bearer and nothing but the email
// ==========================================================================================================

describe('the submit calls the public operation exactly once (AC4, AC5, AC6)', () => {
  it('POSTs to /accounts/resend-verification once, with the typed email as the whole body', async () => {
    const { sent } = installTransport();
    const container = await arrive(RESEND_VERIFICATION_PATH);

    await send(container);

    expect(sent, 'the submit sent more or fewer than one request').toHaveLength(1);
    expect(sent[0]!.path).toBe('/accounts/resend-verification');
    expect(sent[0]!.method).toBe('POST');
    // EQUALITY, not a `toContain`. Task 49 measured why: an exact comparison against the whole value already
    // forbids every extra field, where a substring check lets one ride along unseen.
    expect(JSON.parse(sent[0]!.body), 'the body carries something other than the email')
      .toEqual({ email: EMAIL });
  });

  it('carries no Authorization header EVEN WHEN A SESSION EXISTS', async () => {
    // THE HALF THAT COULD ROT SILENTLY. Every other case here runs with nothing signed in, so all of them
    // would pass if `createPublicAuthApi` were swapped for the authenticated factory. This one seeds the
    // storage a session uses and still demands a bare request, so the factory is pinned rather than the
    // circumstances.
    window.sessionStorage.setItem('themistra.returnTarget', '/app/home');
    const { sent } = installTransport();
    const container = await arrive(RESEND_VERIFICATION_PATH);

    await send(container);

    expect(sent).toHaveLength(1);
    expect(sent[0]!.authorization, 'the resend request carried a bearer').toBeNull();
    window.sessionStorage.clear();
  });

  it('sends exactly one request however many times the effect could run (StrictMode)', async () => {
    const { sent } = installTransport();
    const container = await arrive(RESEND_VERIFICATION_PATH, { strict: true });

    await send(container);

    expect(sent, 'StrictMode double-invocation produced a second send').toHaveLength(1);
  });
});

// ==========================================================================================================
// AC7 — the acknowledgement appears on RESOLVE, whatever the status, and replaces the form
// ==========================================================================================================

describe('the acknowledgement replaces the form once the call settles (AC7)', () => {
  it('shows the acknowledgement and removes the form', async () => {
    installTransport();
    const container = await arrive(RESEND_VERIFICATION_PATH);

    await send(container);

    expect(container.textContent, 'the acknowledged state does not render its two sentences').toBe(ACK_TEXT);
    expect(container.querySelector('form'), 'the form is still mounted after the acknowledgement').toBeNull();
    expect(container.querySelector('input'), 'the email field survived the acknowledgement').toBeNull();
    expect(container.querySelector('button'), 'the submit control survived the acknowledgement').toBeNull();
  });

  it('shows no acknowledgement while the request is still in flight', async () => {
    installNeverAnswering();
    const container = await arrive(RESEND_VERIFICATION_PATH);

    await send(container);

    expect(container.textContent, 'the acknowledgement appeared before the server answered')
      .not.toContain(en.resend.acknowledgedText);
    expect(container.querySelector('form'), 'the form was removed before the server answered').not.toBeNull();
  });

  it('acknowledges on a non-200 resolve too, because the status is never consulted', async () => {
    // NOT A UNIFORMITY CASE - that is task 51's, across the whole range. This one holds the single property
    // task 50 owns about the handler's shape: it acknowledges on RESOLVE, so no branch on status exists for
    // task 51 to have to prove absent. A `500` saying "check your email" is recorded as OQ-T50-6: deliberate,
    // because branching on status is the one thing that could leak an outcome.
    installTransport(500, null);
    const container = await arrive(RESEND_VERIFICATION_PATH);

    await send(container);

    expect(container.textContent).toBe(ACK_TEXT);
  });
});

// ==========================================================================================================
// AC16 — a dropped connection says so, and a retry clears it (D-T50-1, the owner's reversal)
// ==========================================================================================================

describe('a transport failure shows the neutral retry line (AC16, D-T50-1)', () => {
  it('renders the unsent line and keeps the form mounted', async () => {
    // THE OWNER'S REVERSAL, MEASURED. Kimi's phase 3 returned the form to `idle` showing nothing, matching
    // `RegisterScreen`; the owner reversed it because a visitor who presses a button and sees the screen go
    // back to how it looked has been told nothing. The form staying mounted is half the decision: the retry
    // is the point.
    const transport = installTransportFailure();
    const container = await arrive(RESEND_VERIFICATION_PATH);

    await send(container);

    expect(transport.calls(), 'the request was not attempted').toBe(1);
    expect(container.textContent, 'a dropped connection renders no retry line')
      .toContain(en.resend.unsent);
    expect(container.querySelector('form'), 'the form was removed by a failed send').not.toBeNull();
    // AND IT IS NOT THE ACKNOWLEDGEMENT. The line this case is really defending is the one that would claim
    // a link had been sent when the request never left the browser.
    expect(container.textContent, 'a dropped connection claimed a link was sent')
      .not.toContain(en.resend.acknowledgedText);
  });

  it('clears the unsent line when a new send starts', async () => {
    // `setStatus('submitting')` BEFORE THE POST IS NOT REDUNDANT, which is the kind of line a later reader
    // deletes. Without it the retry line would sit under a fresh in-flight attempt, telling the visitor the
    // send failed while it is still running. Task 49 needed the same guarantee and met it the same way.
    installTransportFailure();
    const container = await arrive(RESEND_VERIFICATION_PATH);
    await send(container);
    expect(container.textContent).toContain(en.resend.unsent);

    // Second attempt, this one hanging, so the assertion lands mid-flight.
    installNeverAnswering();
    await act(async () => {
      container.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    });

    expect(container.textContent, 'the stale retry line survived into the next attempt')
      .not.toContain(en.resend.unsent);
    expect(container.textContent, 'the in-flight line is not shown').toContain(en.resend.submitting);
  });
});

// ==========================================================================================================
// PHASE 10 — the two AC11 clauses that had no instrument, and the journey D-T50-1 exists to enable
// ==========================================================================================================

describe('phase 10: the email goes nowhere it was not sent, and a retry can succeed', () => {
  it('puts the email in no URL - not the path, not a query string, not a hash', async () => {
    // AC11 READS "the email in no URL and no console" AND ONLY THE STORAGE CLAUSE HAD AN INSTRUMENT. The
    // brief was doing an assertion's job (phase 7 F5, Kimi I3/I11). The acknowledgement case nearby asserts
    // the PATHNAME is unchanged, which says nothing about `?email=` or `#email=`.
    //
    // An email is not a credential the way a token is, but it is the one piece of personal data this screen
    // handles, and L4's whole subject is not disclosing who has an account. A URL is the worst place for it:
    // browser history, autocomplete, and the `Referer` of whatever loads next - the same reasoning task 49
    // applied to its resend link, which is where this screen's visitors arrive from.
    const { sent } = installTransport();
    const container = await arrive(RESEND_VERIFICATION_PATH);

    await send(container);

    // THE ROUTER'S LOCATION IS NOT THE BROWSER'S, AND THE FIRST VERSION OF THIS CASE ONLY READ THE ROUTER.
    // Found by TR21, which disagreed: the mutant put the email in the real URL with
    // `window.history.replaceState`, and a MEMORY router never reads `window.history`, so
    // `router.state.location.search` stayed empty and this case passed. The kill came from an unrelated
    // tree-wide scan in the profile suite that happens to forbid a template literal before an `/accounts`
    // path - a real kill, by an instrument with no interest in this claim.
    //
    // So both are asserted. The router's location is what the SPA believes; `window.location` is what the
    // visitor's history, autocomplete and `Referer` actually carry, and it is the one that matters.
    const { pathname, search, hash } = router!.state.location;
    expect(search, 'a query string appeared, and the only value here to put in one is the email').toBe('');
    expect(hash, 'a hash fragment appeared').toBe('');
    expect(pathname, 'the email reached the path').not.toContain(EMAIL);
    // The local-part alone, in case an encoder splits it: `visitor` would be enough to identify a person.
    expect(`${pathname}${search}${hash}`, 'part of the email reached the URL').not.toContain('visitor');

    expect(window.location.search, 'the email reached the real query string').toBe('');
    expect(window.location.hash, 'the email reached the real hash fragment').toBe('');
    expect(window.location.href, 'part of the email reached the real browser URL').not.toContain('visitor');

    // AND IT IS WHERE IT BELONGS, which is what makes the four assertions above a claim about PLACEMENT
    // rather than an accident of the email never being used at all.
    expect(JSON.parse(sent[0]!.body)).toEqual({ email: EMAIL });
    expect(sent[0]!.path, 'the email reached the request URL').not.toContain('visitor');
  });

  it('logs nothing anywhere on console, over a DERIVED method list that is asserted non-empty', async () => {
    // THE SECOND CLAUSE WITH NO INSTRUMENT. This suite did not spy on `console` at all, where task 49's
    // derives every function on it. A `console.info(email)` left behind by a debugging session would ship
    // silently and no case in the repository would notice.
    //
    // DERIVED AT RUNTIME, NOT ENUMERATED, and the derivation is asserted first - a reflection that found
    // nothing would make every assertion below vacuous, which is the T48 F4 lesson. The eight names are the
    // NON-VACUITY GUARD rather than the coverage: the spies cover every function on `console`, including any
    // a future runtime adds.
    const methods = (Object.keys(console) as (keyof Console)[])
      .filter((name) => typeof console[name] === 'function');
    expect(methods, 'the console reflection found nothing, so the assertions below are vacuous')
      .toEqual(expect.arrayContaining(['log', 'warn', 'error', 'debug', 'info', 'table', 'dir', 'trace']));
    const spies = Object.fromEntries(methods.map((name) =>
      [name, vi.spyOn(console, name as 'log').mockImplementation(() => {})]));

    installTransport();
    const container = await arrive(RESEND_VERIFICATION_PATH);
    await send(container);

    for (const [name, spy] of Object.entries(spies)) {
      const logged = spy.mock.calls.flat().map((arg) => String(arg)).join(' ');
      expect(logged, `console.${name} was given the email`).not.toContain('visitor');
    }

    // CONTROL, because every assertion above is negative and a spy that does not intercept passes them all.
    console.info('control', EMAIL);
    expect(spies.info!.mock.calls.flat().map(String).join(' '),
      'the console spy does not intercept, so the assertions above were vacuous').toContain('visitor');
  });

  it('acknowledges a retry that succeeds after a dropped connection', async () => {
    // THE JOURNEY D-T50-1 EXISTS TO ENABLE, and nothing drove it end to end. AC16's clearing case submits
    // again with a hanging transport and asserts the stale line is gone MID-FLIGHT; this one lets the second
    // attempt answer. Without it, "the form stays mounted so retrying is one click" was a claim about the
    // DOM rather than about the outcome.
    //
    // The state machine makes this hard to get wrong, which is exactly the argument that was wrong about
    // task 42's storage spy - so it is measured rather than reasoned about.
    installTransportFailure();
    const container = await arrive(RESEND_VERIFICATION_PATH);
    await send(container);
    expect(container.textContent, 'the first attempt did not fail as set up').toContain(en.resend.unsent);

    // The same field, the same button, a transport that now answers. The email is still in the field, so the
    // retry needs no retyping - which is the other half of keeping the form mounted.
    const { sent } = installTransport();
    expect(emailField(container).value, 'the typed email was lost, so a retry means retyping it').toBe(EMAIL);
    await act(async () => {
      container.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    });
    await settle();

    expect(sent, 'the retry sent nothing').toHaveLength(1);
    expect(JSON.parse(sent[0]!.body)).toEqual({ email: EMAIL });
    expect(container.textContent, 'the successful retry did not acknowledge').toBe(ACK_TEXT);
    expect(container.textContent, 'the retry line survived a successful send')
      .not.toContain(en.resend.unsent);
  });
});

// ==========================================================================================================
// AC9 — catalogue copy only, and the response body is never read
// ==========================================================================================================

describe('every word comes from the catalogue and none from the server (AC9)', () => {
  it('renders literal English for all seven keys, and no untranslated key', async () => {
    installTransport();
    const container = await arrive(RESEND_VERIFICATION_PATH);

    for (const key of ['heading', 'emailLabel', 'submit'] as const) {
      expect(container.textContent, `${key} did not render`).toContain(en.resend[key]);
    }
    expect(container.textContent, 'a raw catalogue key reached the DOM').not.toMatch(/resend\./);

    await send(container);
    expect(container.textContent).toContain(en.resend.acknowledgedHeading);
    expect(container.textContent).toContain(en.resend.acknowledgedText);
  });

  it('renders nothing the server sent, even when the server sends a sentence', async () => {
    // T47's D2, carried over: `RegistrationAcknowledgement` is `required: [message]`, so the server ALWAYS
    // sends a sentence and this client always ignores it. The transport replies with one that would be
    // obvious on screen.
    installTransport(200, '{"message":"SERVER SUPPLIED SENTENCE"}');
    const container = await arrive(RESEND_VERIFICATION_PATH);

    await send(container);

    expect(container.textContent, 'the server\'s message was rendered').not.toContain('SERVER SUPPLIED');
    expect(container.textContent).toBe(ACK_TEXT);
  });

  it('reads no part of the response anywhere in its source', async () => {
    // STRUCTURAL, because behaviour cannot see a read that changes nothing today. One `if` on `response.ok`
    // is all it would take for this surface to start distinguishing outcomes, and it would pass every
    // behavioural case above while doing it.
    const source = await read('features/account/ResendVerificationScreen.tsx');
    const code = source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');

    for (const forbidden of [/\bresponse\b/, /\bdata\b/, /\berror\b/, /\.ok\b/, /\.status\b/, /statusText/,
      /\.json\s*\(/, /\.text\s*\(/, /\.message\b/]) {
      expect(code, `the screen reads the response (${String(forbidden)})`).not.toMatch(forbidden);
    }

    // AND THE SCAN IS AIMED AT SOMETHING. Task 49's F3 found a pattern forbidding an impossible call, which
    // measured nothing; so the settle handler is asserted to be the argument-less shape the rules above
    // describe, rather than left to the absence of words.
    expect(code, 'the settle handler takes an argument, so there is something to read')
      .toMatch(/\.then\(\(\) => setStatus\('acknowledged'\), \(\) => setStatus\('unsent'\)\)/);
  });

  it('uses the generated client and no bare fetch (L6)', async () => {
    const source = await read('features/account/ResendVerificationScreen.tsx');
    const code = source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');

    expect(code).toContain('createPublicAuthApi()');
    expect(code, 'the screen calls fetch directly').not.toMatch(/(?<!\.)\bfetch\s*\(/);
    expect(code, 'the screen uses the AUTHENTICATED factory').not.toMatch(/createAuthApi\b/);
  });
});

// ==========================================================================================================
// AC10 — the in-flight line is announced, the acknowledgement takes focus, and Enter submits
// ==========================================================================================================

describe('both states are announced, by the mechanism each one needs (AC10)', () => {
  it('mounts the live region EMPTY and fills it only while submitting', async () => {
    // A live region mounted WITH content announces nothing, which is why `StatusRegion` renders
    // unconditionally and the consumer passes children only when there is text.
    installNeverAnswering();
    const container = await arrive(RESEND_VERIFICATION_PATH);
    const region = container.querySelector('[role="status"]');

    expect(region, 'the live region is not mounted on arrival').not.toBeNull();
    expect(region!.textContent, 'the region arrived with content').toBe('');

    await send(container);

    expect(container.querySelector('[role="status"]')!.textContent).toBe(en.resend.submitting);
  });

  it('moves focus to the heading when the acknowledgement replaces the form', async () => {
    // THE FOCUS MOVE IS THE ACKNOWLEDGEMENT'S ANNOUNCEMENT. A region alone would announce the words and
    // leave focus on `document.body`, because the button that held it has been unmounted - which is exactly
    // what this case would catch.
    installTransport();
    const container = await arrive(RESEND_VERIFICATION_PATH);

    await send(container);

    const heading = container.querySelector('h1');
    expect(document.activeElement, 'focus was not moved to the acknowledgement').toBe(heading);
    expect(heading!.textContent, 'focus landed on a heading that is not the acknowledgement')
      .toBe(en.resend.acknowledgedHeading);
  });

  it('leaves focus alone on arrival, so the form is usable from the first keystroke', async () => {
    // The opposite claim, and it has to be made separately: a screen that focused its heading on mount would
    // pass the case above for the wrong reason. `RegisterScreen` does not move focus either - a form's first
    // field is where a visitor is going.
    installTransport();
    const container = await arrive(RESEND_VERIFICATION_PATH);

    expect(document.activeElement, 'the screen took focus on arrival').toBe(document.body);
    expect(container.querySelector('h1')!.textContent).toBe(en.resend.heading);
  });

  it('submits on Enter in the email field, not only on a click', async () => {
    // The form's own submit event, which is what a keyboard visitor produces. `Form` sets `noValidate`, so
    // the browser's constraint bubbles cannot block it.
    const { sent } = installTransport();
    const container = await arrive(RESEND_VERIFICATION_PATH);
    await type(emailField(container), EMAIL);

    await act(async () => {
      container.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    });
    await settle();

    expect(sent, 'an implicit submit sent nothing').toHaveLength(1);
    expect(JSON.parse(sent[0]!.body)).toEqual({ email: EMAIL });
  });

  it('labels the email field, so the control has an accessible name', async () => {
    installTransport();
    const container = await arrive(RESEND_VERIFICATION_PATH);
    const field = emailField(container);
    const label = container.querySelector<HTMLLabelElement>(`label[for="${field.id}"]`);

    expect(field.id, 'the email field has no id, so no label can point at it').not.toBe('');
    expect(label, 'the email field has no label element').not.toBeNull();
    expect(label!.textContent).toBe(en.resend.emailLabel);
  });
});

// ==========================================================================================================
// AC12, D-T50-2 — primitives only, and ONE h1 in every state
// ==========================================================================================================

describe('the screen composes primitives and keeps one document outline (AC12, D-T50-2)', () => {
  it('bears no lowercase JSX tag of its own', async () => {
    const source = await read('features/account/ResendVerificationScreen.tsx');
    const code = source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');
    const tags = [...code.matchAll(/(?<![A-Za-z0-9_$])<([a-z][a-zA-Z0-9-]*)(?=[\s/>])/g)].map((m) => m[1]);

    expect(tags, `the screen renders ${tags.join(', ')} itself instead of a primitive`).toEqual([]);
  });

  it('passes no className and no style', async () => {
    const source = await read('features/account/ResendVerificationScreen.tsx');
    const code = source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');

    expect(code, 'the screen styles itself').not.toMatch(/className/);
    expect(code, 'the screen styles itself').not.toMatch(/\bstyle=/);
  });

  it('renders exactly one h1 in EVERY state, which the frozen design would not have', async () => {
    // D-T50-2, AND THIS CASE IS WHY THE DEVIATION EXISTS. The phase 3 design put a second `Heading` inside
    // the acknowledged branch, under the screen's own. `Heading` renders an `<h1>`; all seven screens that
    // existed before this one render exactly one, and the design-system suite pins `h1` to a length of 1 for
    // three of them because "the document outline L11 requires has one top level". Two would have made this
    // the first screen in the bundle to break a LOCKED rule.
    installTransport();
    const container = await arrive(RESEND_VERIFICATION_PATH);
    expect(container.querySelectorAll('h1'), 'the form state has more than one top-level heading')
      .toHaveLength(1);

    await send(container);
    expect(container.querySelectorAll('h1'), 'the acknowledged state has more than one top-level heading')
      .toHaveLength(1);
  });

  it('is named in the pinned render-site list (task 174\'s shape)', async () => {
    const only = await read('app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx');

    expect(only, 'the render-site list does not name this screen')
      .toContain("'features/account/ResendVerificationScreen.tsx',");
    // AND IN THE CREDENTIAL TABLE, which is the pin this task did not expect to touch: the scan matches
    // case-insensitively, so mounting the `<Form>` primitive counts as a form element there.
    const login = await read('auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx');
    expect(login, 'the credential table does not name this screen')
      .toContain("file: 'features/account/ResendVerificationScreen.tsx',");
  });
});
