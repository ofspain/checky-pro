/// <reference types="node" />
/**
 * T49 — `verifyFailureShowsOneUniformMessage` (`package.md` §8), R4.
 *
 * **"ONE UNIFORM FAILURE FOR ANY REASON" CANNOT BE TESTED THE WAY THE TASK LINE PHRASES IT**, and saying so
 * is the first thing this suite has to get right. The task names four reasons - invalid, expired, used,
 * deleted-account - and the backend collapses all four into ONE response before the SPA sees any of them:
 * `AccountService:38/:46/:47-48` throw the same `VerificationTokenRejectedException`, and
 * `AccountExceptionHandler:46-52` maps it to a fixed 400 with a fixed type and title, with a javadoc saying
 * it "never distinguishes 'not found' from 'expired' from 'already used' from 'wrong account state'". A
 * client-side test that mocked four reasons would be mocking a distinction that does not exist on the wire.
 *
 * So what this suite holds instead is the property that WOULD let a reason leak: that several genuinely
 * different failure responses - different statuses, with and without a problem document - render
 * byte-identical DOM, and that the screen never reads the response body at all. One `if` on `error.title` is
 * all it would take, which is why there is a source scan as well as behaviour.
 *
 * **EVERY CASE ARRIVES THROUGH THE REAL ROUTE TABLE**, as in task 48's suite, because the claim is about an
 * emailed link rather than a component.
 *
 * **THE TOKENLESS ARRIVAL IS IN SCOPE HERE BY OWNER DECISION, NOT BY R4's LETTER** (D-T49-2). R4 fires "IF
 * the verification *request* fails", and with no token no request is made. Kimi's phase 3 chose to render
 * the heading alone; the owner reversed it at the phase 4 gate, because a truncated or wrapped URL is the
 * likeliest broken link anyone meets and the remedy is identical to a rejected token.
 */
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { act, StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider, type createMemoryRouter as CreateMemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { APP_BASENAME, appRoutes, RESEND_VERIFICATION_PATH, VERIFY_PATH } from '../../app/routes';
import { en } from '../../i18n/catalogue';
import { createI18n } from '../../i18n/i18n';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const srcDir = dirname(dirname(dirname(expect.getState().testPath as string)));
const read = (relativePath: string) => readFile(join(srcDir, relativePath), 'utf8');

/** The alphabet the server issues: `Base64.getUrlEncoder().withoutPadding()`, so `A-Za-z0-9-_` only. */
const TOKEN = 'abcDEF123_-xyzABC456_-defGHI789_-jklMNO012_x';

/** What the failure state renders, in order. Written once so every uniformity case compares the same thing. */
const FAILURE_TEXT = `${en.verify.heading}${en.verify.failureMessage}${en.verify.failureAction}`;

let mounted: { root: Root; container: HTMLElement } | undefined;
let router: ReturnType<typeof CreateMemoryRouter> | undefined;

/** Node's `Request` rejects a relative URL; a browser resolves it against the document (T05). */
function installBrowserRequest(): void {
  const NodeRequest = globalThis.Request;
  vi.stubGlobal('Request', class extends NodeRequest {
    constructor(input: RequestInfo | URL, init?: RequestInit) {
      super(typeof input === 'string' ? new URL(input, window.location.href) : input, init);
    }
  });
}

type Sent = { path: string; body: string; authorization: string | null };

/**
 * Copied from task 48's suite WITH the assertions that hardened it, not just its shape. The body is read
 * inside the handler because a `Request`'s body can be consumed only once; `authorization` likewise, because
 * reading it from a case afterwards reads a request the client may already have released.
 *
 * `body` here is the RESPONSE body, which task 48's version had no need for: R4's risk is a screen that
 * renders or branches on what the server said, so these cases have to be able to make the server say
 * something.
 */
function installTransport(status: number, body: string | null = null): { sent: Sent[] } {
  const sent: Sent[] = [];
  vi.stubGlobal('fetch', vi.fn(async (request: Request) => {
    sent.push({
      path: new URL(request.url).pathname,
      body: await request.clone().text(),
      authorization: request.headers.get('authorization'),
    });
    return new Response(body, {
      status,
      headers: body === null ? undefined : { 'Content-Type': 'application/problem+json' },
    });
  }));
  return { sent };
}

/** A transport that never answers, for the in-flight assertions. */
function installNeverAnswering(): void {
  vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => {})));
}

/** A transport that fails the way a dropped connection does: the promise REJECTS (AC3). */
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
  await act(async () => { await Promise.resolve(); await Promise.resolve(); });
  return container;
}

/** Discards the mounted screen so a case can arrive twice and compare the two renderings. */
async function teardownCurrent(): Promise<void> {
  await act(async () => mounted!.root.unmount());
  mounted!.container.remove();
  mounted = undefined;
  router?.dispose?.();
  router = undefined;
}

// ==========================================================================================================
// AC1, AC15 — the failure is shown, and for a tokenless link it is shown without asking anyone
// ==========================================================================================================

describe('AC1/AC15 the uniform failure appears', () => {
  it('shows the failure message when the server rejects the token', async () => {
    const { sent } = installTransport(400);

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    expect(container.textContent).toContain(en.verify.failureMessage);
    expect(sent, 'the rejection was not actually exercised').toHaveLength(1);
  });

  it('shows the same failure for a tokenless link, and sends NOTHING (D-T49-2)', async () => {
    // The two halves matter separately. The message, because this is the owner's reversal of Kimi's phase 3
    // and the case is what makes it real. The silence, because the uniform failure here is rendered WITHOUT
    // asking the server - there is no token to ask about - so a screen that posted `{ token: '' }` to reach
    // the same words would be wrong in a way the words cannot show.
    const { sent } = installTransport(400);

    const container = await arrive(VERIFY_PATH);

    expect(container.textContent).toBe(FAILURE_TEXT);
    expect(sent, 'a tokenless arrival asked the server about a token it did not have').toHaveLength(0);
  });

  it('shows the same failure for an empty token, and sends nothing', async () => {
    const { sent } = installTransport(400);

    const container = await arrive(`${VERIFY_PATH}?token=`);

    expect(container.textContent).toBe(FAILURE_TEXT);
    expect(sent).toHaveLength(0);
  });
});

// ==========================================================================================================
// AC2, AC13 — uniformity, which is the requirement
// ==========================================================================================================

describe('AC2/AC13 every failure renders the same thing', () => {
  // 410 and 429 are not idle choices. 410 Gone is what a used token would be if anyone ever gave the four
  // reasons distinct statuses, and 429 is the one status with machinery already in the bundle that could
  // render something else: `createRateLimitNotice` and `RateLimitNotice.tsx` exist and have NO production
  // consumer, so R4's uniformity currently holds for a rate-limited verification partly by accident. If a
  // later task wires that notice into the public client, this row is what fails.
  for (const status of [400, 401, 403, 404, 409, 410, 429, 500, 503]) {
    it(`renders the uniform failure for ${String(status)}`, async () => {
      installTransport(status);

      const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

      expect(container.textContent).toBe(FAILURE_TEXT);
    });
  }

  it('renders the same thing whether or not the server sent a problem document', async () => {
    installTransport(400, JSON.stringify({
      type: 'https://themistra.example/problems/invalid-token',
      title: 'Verification token is invalid or expired',
      detail: 'The token was already used at 2026-11-28T09:14:02Z by 10.1.2.3',
      status: 400,
    }));

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    // The DOM is identical to the bodyless 400 above - AND none of the server's words reached it. The
    // `detail` here is deliberately the kind of string a real ProblemDetail might carry one day: it names a
    // reason, a timestamp and an address, so if any of it is ever echoed this case says which.
    expect(container.textContent).toBe(FAILURE_TEXT);
    expect(container.textContent).not.toContain('already used');
    expect(container.textContent).not.toContain('invalid or expired');
    expect(container.textContent).not.toContain('10.1.2.3');
  });

  it('renders the uniform failure when the transport never answers at all (AC3)', async () => {
    const { calls } = installTransportFailure();

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    // THE ARM TASK 48 LEFT EMPTY. openapi-fetch resolves on HTTP errors and rejects only on transport
    // failure, so this is the only path that reaches the rejection handler - which was `() => {}`, enough to
    // stop an unhandled rejection and not enough to tell the visitor anything. R4 says "fails for any
    // reason", and a network that never answered is a reason.
    expect(container.textContent).toBe(FAILURE_TEXT);
    expect(calls(), 'no request was attempted, so the rejection path was not exercised').toBe(1);
  });
});

// ==========================================================================================================
// AC4, AC5 — the resend action
// ==========================================================================================================

describe('AC4/AC5 the neutral resend action', () => {
  it('offers a resend link that resolves under the app basename', async () => {
    installTransport(400);

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);
    const link = container.querySelector('a');

    expect(link, 'the failure state offers nowhere to go, so R4 is half implemented').not.toBeNull();
    expect(link!.textContent).toBe(en.verify.failureAction);
    // `/app/resend-verification`, not `/resend-verification`: the basename belongs to the router, which is
    // why `TextLink` takes `to` and has no `href` prop. A screen that built its own href would work in this
    // test and break under a deployment that moved the basename.
    expect(link!.getAttribute('href')).toBe(`${APP_BASENAME}/${RESEND_VERIFICATION_PATH}`);
  });

  it('offers the identical action for every failure, including one the server never answered', async () => {
    installTransport(400);
    const rejected = (await arrive(`${VERIFY_PATH}?token=${TOKEN}`)).querySelector('a')!.outerHTML;
    await teardownCurrent();

    installTransport(429);
    const limited = (await arrive(`${VERIFY_PATH}?token=${TOKEN}`)).querySelector('a')!.outerHTML;
    await teardownCurrent();

    installTransportFailure();
    const dropped = (await arrive(`${VERIFY_PATH}?token=${TOKEN}`)).querySelector('a')!.outerHTML;
    await teardownCurrent();

    installTransport(400);
    const tokenless = (await arrive(VERIFY_PATH)).querySelector('a')!.outerHTML;

    // Compared as OUTER HTML, so the label, the target and every class are in scope: "neutral" is not only
    // about words. A styling difference between a rejected token and a rate-limited one would be exactly the
    // kind of tell L4 lists alongside copy.
    expect(limited).toBe(rejected);
    expect(dropped).toBe(rejected);
    expect(tokenless).toBe(rejected);
    expect(rejected, 'every arm rendered an empty anchor, so this proves nothing')
      .toContain(en.verify.failureAction);
  });

  it('has no control in the success state', async () => {
    installTransport(204);

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    expect(container.textContent).toContain(en.verify.success);
    expect(container.querySelector('a'), 'the success state offers a resend link, which makes no sense')
      .toBeNull();
  });
});

// ==========================================================================================================
// AC6 — the flash, which is the defect this task was most likely to ship
// ==========================================================================================================

describe('AC6 a valid token never renders the failure copy', () => {
  it('shows no failure copy while a valid token is in flight', async () => {
    installNeverAnswering();

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    // `idle` means exactly one thing now - in flight - and this is what holds it. Under task 48 `idle` meant
    // three things, and task 48's own OQ-T48-3 said task 49 would "render that state as the uniform failure
    // state". Taken literally that is a bug: `idle` is held for the whole round trip, so every successful
    // visitor would see the failure sentence first.
    expect(container.textContent).toBe(en.verify.heading);
    expect(container.querySelector('a')).toBeNull();
  });

  it('clears an earlier failure while the next token is in flight (the step-5 line)', async () => {
    // THE CASE THAT JUSTIFIES A LINE SOMEONE WOULD OTHERWISE DELETE. Arrive with no token, land in failure,
    // then follow a complete link into the SAME mounted screen - the transition task 48 measured when it
    // proved the one-request guard means "a request has been made" rather than "this effect has run".
    // Without `setStatus('idle')` at the start of the request, the failure sentence would sit on screen for
    // the whole round trip of a verification that is about to succeed.
    installNeverAnswering();
    const container = await arrive(VERIFY_PATH);
    expect(container.textContent, 'the tokenless arrival did not reach the failure state').toBe(FAILURE_TEXT);

    await act(async () => { await router?.navigate(`/${VERIFY_PATH}?token=${TOKEN}`); });
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });

    expect(container.textContent, 'the stale failure is still on screen while a good token is in flight')
      .toBe(en.verify.heading);
    expect(container.querySelector('a'), 'the stale resend link is still offered').toBeNull();
  });

  it('reaches success after an earlier failure, with no failure copy left behind', async () => {
    installTransport(204);
    const container = await arrive(VERIFY_PATH);
    expect(container.textContent).toBe(FAILURE_TEXT);

    await act(async () => { await router?.navigate(`/${VERIFY_PATH}?token=${TOKEN}`); });
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });

    expect(container.textContent).toBe(`${en.verify.heading}${en.verify.success}`);
  });

  it('sends exactly one request under StrictMode and still fails uniformly', async () => {
    const { sent } = installTransport(400);

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`, { strict: true });

    expect(sent, 'StrictMode sent the one-time token twice, so the second attempt rejected it')
      .toHaveLength(1);
    expect(container.textContent).toBe(FAILURE_TEXT);
  });
});

// ==========================================================================================================
// AC7 — the announcement, and focus that does NOT move twice
// ==========================================================================================================

describe('AC7 the failure is announced, and focus stays put', () => {
  it('mounts the live region empty before the failure fills it', async () => {
    installNeverAnswering();

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);
    const region = container.querySelector('[role="status"]');

    // The ordering is the whole announcement. `StatusRegion`'s own header says a region mounted at the same
    // moment its text arrives is a new element with content already in it, and a screen reader announces
    // nothing. So this asserts the region EXISTS while there is nothing to say.
    expect(region, 'there is no live region to announce into').not.toBeNull();
    expect(region!.textContent).toBe('');
    expect(region!.getAttribute('aria-live')).toBe('polite');
  });

  it('puts the failure sentence inside that same region', async () => {
    installTransport(400);

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    expect(container.querySelector('[role="status"]')!.textContent).toBe(en.verify.failureMessage);
  });

  it('keeps the resend link OUTSIDE the live region', async () => {
    installTransport(400);

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    // Not a detail. An interactive control inside a `role="status"` region is re-announced every time the
    // region's text changes, so the link would be read out again on each transition. The message is what is
    // announced; the link is what is operated.
    expect(container.querySelector('[role="status"] a'), 'the resend link is inside the live region')
      .toBeNull();
    expect(container.querySelector('a'), 'the resend link is gone entirely').not.toBeNull();
  });

  it('leaves focus on the heading when the failure lands', async () => {
    installTransport(400);

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    // OQ-T49-4: focus moves once, on arrival, and never again. Moving it when an async result lands would
    // interrupt a screen-reader user who may already be reading, which is why the announcement is the live
    // region's job and not focus's.
    expect(document.activeElement).toBe(container.querySelector('h1'));
    expect(document.activeElement, 'focus jumped to the resend link when the failure landed')
      .not.toBe(container.querySelector('a'));
  });

  it('keeps the resend link keyboard-reachable with an accessible name', async () => {
    installTransport(400);

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);
    const link = container.querySelector('a')!;

    // An anchor WITH an href is in the tab order natively, which is why this asserts the href rather than a
    // tabindex: adding `tabindex="0"` to a real link is the redundant spelling, and a link without an href
    // is not focusable at all however it is styled.
    expect(link.getAttribute('href')).not.toBeNull();
    expect(link.getAttribute('tabindex'), 'a real link does not need a tabindex').toBeNull();
    expect(link.textContent?.trim()).toBe(en.verify.failureAction);
  });
});

// ==========================================================================================================
// AC8 — the copy itself
// ==========================================================================================================

describe('AC8 the copy comes from the catalogue and names no reason', () => {
  it('renders both new keys as literal English', async () => {
    installTransport(400);

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    // Spelled out rather than compared to `en.*` alone, so a catalogue edit that silently changed the
    // meaning would have to change this line too. The pairing is the point: the message states an outcome,
    // the action offers the one next step.
    expect(container.textContent).toContain('This verification link is not valid.');
    expect(container.textContent).toContain('Resend verification email');
  });

  it('names no failure reason anywhere on screen (L4)', async () => {
    installTransport(400);

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);
    const text = container.textContent ?? '';

    // Each of these would let a visitor distinguish two outcomes by reading one sentence, which is exactly
    // what L4 forbids - and each is a word someone writing "helpful" copy reaches for first.
    for (const reason of ['expired', 'already', 'used', 'deleted', 'suspended', 'locked', 'not found',
      'unknown', 'invalid token', 'verified']) {
      expect(text.toLowerCase(), `the copy names a reason: ${reason}`).not.toContain(reason);
    }
  });

  it('leaks no untranslated key into the DOM', async () => {
    installTransport(400);

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    expect(container.textContent).not.toMatch(/verify\./);
  });
});

// ==========================================================================================================
// AC9 — the call, and the structural half of L4
// ==========================================================================================================

describe('AC9 one public call, and the body is never read', () => {
  it('sends one request with no Authorization header', async () => {
    const { sent } = installTransport(400);

    await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    expect(sent).toHaveLength(1);
    expect(sent[0]!.path).toBe('/accounts/verify-email');
    expect(sent[0]!.authorization, 'a public endpoint was called with a credential').toBeNull();
    expect((JSON.parse(sent[0]!.body) as { token: string }).token).toBe(TOKEN);
  });

  it('never reads the response body, which is what keeps the failure uniform', async () => {
    const code = (await read('features/account/VerifyScreen.tsx'))
      .replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');

    // STRUCTURAL, BECAUSE BEHAVIOUR CANNOT SEE THE FIRST STEP TOWARDS A REASON-SPECIFIC MESSAGE. A screen
    // that read `error.title` and did nothing with it passes every case above. The next edit is the one that
    // renders it, and by then the reading is established and looks deliberate.
    expect(code, 'the file was not read, so none of these bans mean anything').toContain('response.ok');
    for (const forbidden of [/\berror\b/, /\.title\b/, /\.detail\b/, /\.type\b/, /response\.status\b/,
      /\bstatusText\b/, /\.json\s*\(/, /\.text\s*\(/]) {
      expect(code, `VerifyScreen reads the response body: ${String(forbidden)}`).not.toMatch(forbidden);
    }
  });

  it('goes through the generated client and not a bare fetch', async () => {
    const code = (await read('features/account/VerifyScreen.tsx'))
      .replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');

    expect(code).toContain('createPublicAuthApi');
    expect(code, 'a bare fetch bypasses the generated client (L6)').not.toMatch(/\bfetch\s*\(/);
    expect(code, 'a storage write would put a credential where L16 does not allow one')
      .not.toMatch(/sessionStorage|localStorage/);
  });
});

// ==========================================================================================================
// PHASE 10 — the handoff, the credential in a navigable URL, and one limit
// ==========================================================================================================

describe('phase 10: what happens next, and what this screen does not do', () => {
  it('HANDOFF TO TASK 50 - the resend target renders NOTHING today, so this case MUST FAIL then', async () => {
    // THE INSTRUMENT PHASE 7 FOUND MISSING (F5), AND KIMI CONFIRMED (I4). R4's action is live and its
    // destination is element-less until task 50, which the owner accepted at the phase 4 gate as a known
    // interim state. Nothing in the repository would have failed when that element landed, so nothing would
    // have prompted anyone to re-check that this screen's link still goes somewhere sensible.
    //
    // **WRITTEN AS A POSITIVE CLAIM THAT INVERTS, NOT AS A SAMENESS**, because phase 5 measured why that
    // matters: task 48's `PINS A LIMIT` case was labelled a handoff and did NOT fail when this task landed,
    // since its assertion was that two arms were identical and they stayed identical. What caught this task
    // was two unlabelled cases asserting an EQUALITY against the full rendered text.
    //
    // WHOEVER CLOSES TASK 50: this failing is the point. Replace it with an assertion that the resend form
    // renders, and check that `verify.failureAction`'s wording still describes what the visitor will find.
    const container = document.createElement('div');
    document.body.append(container);
    const resendRouter = createMemoryRouter(appRoutes, {
      basename: APP_BASENAME,
      initialEntries: [`${APP_BASENAME}/${RESEND_VERIFICATION_PATH}`],
    });
    router = resendRouter;
    const root = createRoot(container);
    mounted = { root, container };
    await act(async () => {
      root.render(
        <I18nextProvider i18n={createI18n()}>
          <RouterProvider router={resendRouter} />
        </I18nextProvider>,
      );
    });

    // The path RESOLVES - it is a declared public route - and renders no element, so the visitor who follows
    // the resend link today gets a blank document. Both halves are asserted: without the first, a typo in
    // the constant would make this pass for the wrong reason.
    expect(resendRouter.state.location.pathname, 'the resend path no longer resolves at all')
      .toBe(`${APP_BASENAME}/${RESEND_VERIFICATION_PATH}`);
    expect(container.innerHTML,
      'the resend target renders something now - task 50 has landed, so re-check this screen\'s link')
      .toBe('');
  });

  it('never puts the token in the resend link, where it would enter browser history', async () => {
    installTransport(400);

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);
    const href = container.querySelector('a')!.getAttribute('href') ?? '';

    // THE "HELPFUL" EDIT THIS FORBIDS is carrying the token over so the resend form can prefill something.
    // A token in a link the visitor CLICKS is worse than one in a link they arrived on: it enters browser
    // history, it is offered by autocomplete afterwards, and it goes in the Referer header of whatever the
    // next page loads. AC10 covers the token in rendered TEXT; this covers it in a navigable URL, which no
    // other case looks at.
    expect(href, 'the resend link carries the verification token').not.toContain(TOKEN);
    expect(href, 'the resend link carries a query string, and the only thing here to put in one is the token')
      .not.toContain('?');
  });

  it('PINS A LIMIT - an in-flight token replaced by a tokenless URL leaves the heading alone', async () => {
    // MEASURED, NOT ASSUMED, and recorded as a limit rather than fixed. Arrive with a token, then navigate
    // to the same route WITHOUT one while the request is still in flight: the one-request guard is already
    // latched, so the effect returns early and `status` stays `'idle'` - a heading and empty space, which is
    // the exact defect this task exists to remove, surviving in a corner.
    //
    // **NOT REACHABLE IN THE PRODUCT TODAY**: nothing in the SPA navigates to `/app/verify`, and every real
    // arrival is a fresh page load from an email, so `handled` is false. Only in-app navigation or a history
    // move can produce this.
    //
    // **THE FIX WAS CONSIDERED AND REJECTED.** Moving the tokenless branch ABOVE the `handled` check would
    // close it - that branch sends no request, so the guard does not need to cover it - but it would also
    // mean a Back navigation after a SUCCESSFUL verification displays "This verification link is not valid",
    // which is worse than a stale heading. So the guard keeps its position and this case documents the cost.
    // If anyone closes this, the assertion below must fail first.
    installNeverAnswering();
    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);
    expect(container.textContent, 'the arrival did not reach the in-flight state').toBe(en.verify.heading);

    await act(async () => { await router?.navigate(`/${VERIFY_PATH}`); });
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });

    expect(container.textContent,
      'the tokenless URL now shows the uniform failure - the limit is closed, so delete this case')
      .toBe(en.verify.heading);
  });
});

// ==========================================================================================================
// AC10 — the token is a credential, and the failure state is new DOM
// ==========================================================================================================

describe('AC10 the token does not leak in the failure state', () => {
  it('renders the token nowhere when the server rejects it', async () => {
    installTransport(400, JSON.stringify({ title: 'rejected', detail: `token ${TOKEN} was already used` }));

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    // Kimi's task 48 review asked for exactly this once this task rendered new DOM, and the problem document
    // above is the realistic way it would happen: a server that echoes the token in `detail`, and a screen
    // that renders `detail`.
    expect(container.innerHTML, 'the token is in the failure DOM').not.toContain(TOKEN);
  });

  it('writes the token to no storage and logs nothing, in the failure state', async () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    const methods = (Object.keys(console) as (keyof Console)[])
      .filter((name) => typeof console[name] === 'function');
    expect(methods, 'the console reflection found nothing, so the assertion below is vacuous')
      .toEqual(expect.arrayContaining(['log', 'warn', 'error', 'debug', 'info', 'table', 'dir', 'trace']));
    const spies = methods.map((name) => vi.spyOn(console, name as 'log').mockImplementation(() => {}));
    installTransport(400);

    await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    expect(setItem, 'something wrote to storage during a failed verification').not.toHaveBeenCalled();
    expect(spies.flatMap((spy) => spy.mock.calls.flat()).map(String).join('\n'),
      'the failure path logged something, and the token is in scope where it did').toBe('');
  });

  it('CONTROL: the storage spy and the console spy both intercept', async () => {
    // Task 42 shipped a storage assertion that was vacuous from birth because it spied on an INSTANCE rather
    // than `Storage.prototype`, and it survived because the assertion was negative. A negative assertion
    // needs a positive control, so this is it.
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    const log = vi.spyOn(console, 'log').mockImplementation(() => {});

    sessionStorage.setItem('control', 'written');
    console.log('control');

    expect(setItem).toHaveBeenCalledWith('control', 'written');
    expect(log).toHaveBeenCalledWith('control');
  });
});
