/// <reference types="node" />
/**
 * T48 — `verifyFromLinkShowsSuccessState` (`package.md` §8), R3.
 *
 * **EVERY CASE ARRIVES THROUGH THE REAL ROUTE TABLE**, because the claim is about a link. A direct mount of
 * `VerifyScreen` would prove the component works and say nothing about whether an emailed URL reaches it,
 * which is the half that had been missing since task 16 - `/app/verify` was declared with no element, so
 * every verification email the platform ever sent landed on a blank document.
 *
 * **THE TOKEN IS THE ONLY INPUT, AND THAT IS WHY THIS SCREEN IS THE MIRROR OF TASK 47'S.**
 * `RegisterSentScreen` is forbidden by a source scan from so much as mentioning `useSearchParams`, because
 * R2 asks it to render identically for two outcomes and an input is how two journeys would differ. R3 asks
 * this screen to act on the one input the visitor brought with them. Opposite requirements, adjacent files.
 *
 * **WHAT THIS SUITE COULD NOT SEE WHEN TASK 48 WROTE IT, AND CAN NOW.** This paragraph used to say that
 * `idle` was the rendering for three different situations - the request in flight, no token in the link, and
 * a request that failed - that R4's uniform failure message was task 49's, and that the cases below
 * therefore pinned the no-token branch by its CONSEQUENCE (no request is sent) rather than by anything on
 * screen. **All three clauses are now false**, and the third was falsified by this file's own edits: task 49
 * split the state machine, so `idle` means in-flight and nothing else; it owns the failure copy, which
 * exists; and the two AC12 cases below now assert the full rendered failure text, not just the silence.
 *
 * **Corrected in task 49's phase 12, having been walked past twice** - this file was edited in phase 6 and
 * again in phase 10 while its header went on describing the screen it used to test. That is the shape this
 * project keeps finding: a comment is the only part of a suite with no instrument, and the ones that rot are
 * the ones whose assertions still pass for an unrelated reason. Every assertion here still passed, because
 * none of them was ever about the sentence above.
 */
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { act, StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider, type createMemoryRouter as CreateMemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { APP_BASENAME, appRoutes, PUBLIC_ROUTE_PATHS, VERIFY_PATH } from '../../app/routes';
import { en } from '../../i18n/catalogue';
import { createI18n } from '../../i18n/i18n';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const srcDir = dirname(dirname(dirname(expect.getState().testPath as string)));
const read = (relativePath: string) => readFile(join(srcDir, relativePath), 'utf8');

/**
 * A token in the alphabet the server actually issues.
 *
 * `VerificationTokenService.generateRawToken` (`:189-193`) is
 * `Base64.getUrlEncoder().withoutPadding().encodeToString(32 bytes)`, so the alphabet is `A-Za-z0-9-_` with
 * no padding - **no `+`, no `/`, no `=`**. That is not decoration: see the last case in this file, which
 * measures what a `+` would do if the encoder were ever changed.
 */
const TOKEN = 'abcDEF123_-xyzABC456_-defGHI789_-jklMNO012_x';

let mounted: { root: Root; container: HTMLElement } | undefined;
let router: ReturnType<typeof CreateMemoryRouter> | undefined;

/** Node's Request rejects a relative URL; a browser resolves it against the document (T05). */
function installBrowserRequest(): void {
  const NodeRequest = globalThis.Request;
  vi.stubGlobal('Request', class extends NodeRequest {
    constructor(input: RequestInfo | URL, init?: RequestInit) {
      super(typeof input === 'string' ? new URL(input, window.location.href) : input, init);
    }
  });
}

type Sent = { path: string; body: string; authorization: string | null; method: string; contentType: string | null };

/**
 * The body AND the headers are read inside the handler. The body because a `Request`'s body can be consumed
 * only once, so reading it from a case afterwards gets an exception or an empty string depending on who got
 * there first; the headers because `authorization` is the whole of AC3's second half and reading it later
 * would be reading a request the client may already have released.
 */
function installTransport(status = 204): { sent: Sent[] } {
  const sent: Sent[] = [];
  vi.stubGlobal('fetch', vi.fn(async (request: Request) => {
    sent.push({
      path: new URL(request.url).pathname,
      body: await request.clone().text(),
      authorization: request.headers.get('authorization'),
      // THE METHOD AND CONTENT TYPE ARE RECORDED FROM PHASE 10. The contract fixes both, and nothing in the
      // suite was looking at either: a call with the right body on the wrong verb, or with a content type
      // the server will not parse, fails in production and passed here.
      method: request.method,
      contentType: request.headers.get('content-type'),
    });
    return new Response(null, { status });
  }));
  return { sent };
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
  // Lets the POST's promise and the state update that follows it settle.
  await act(async () => { await Promise.resolve(); await Promise.resolve(); });
  return container;
}

// ==========================================================================================================
// AC1 / AC2 / AC3 / AC4 — the link arrives, the token is sent, the success state shows
// ==========================================================================================================

describe('AC1-AC4 a valid token shows the verified success state', () => {
  it('renders the success state for a 204 at /app/verify?token=... (AC1, AC4)', async () => {
    installTransport(204);

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    expect(container.textContent).toBe(`${en.verify.heading}${en.verify.success}`);
    expect(router?.state.location.pathname).toBe(`${APP_BASENAME}/${VERIFY_PATH}`);
  });

  it('sends the token FROM THE URL, as the body of one public POST (AC2, AC3)', async () => {
    const { sent } = installTransport();

    await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    expect(sent).toHaveLength(1);
    expect(sent[0]!.path).toBe('/accounts/verify-email');
    // THE TOKEN ITSELF, not merely "a body". A screen that posted `{ token: '' }` would satisfy a case that
    // only counted requests, and would then show task 49's failure for every visitor.
    expect(JSON.parse(sent[0]!.body) as { token: string }).toEqual({ token: TOKEN });
  });

  it('carries NO Authorization header, because the operation is security: [] (AC3, L6)', async () => {
    const { sent } = installTransport();

    await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    // `createPublicAuthApi` exists precisely to have no session boundary. `createAuthApi` here would attach
    // a bearer the visitor does not have and arm renewal on a 401 - on a screen reached with no session at
    // all, which is the one place renewal can only loop.
    expect(sent[0]!.authorization, 'the verification call carried a bearer').toBeNull();
  });

  it('stays on the screen: nothing navigates away from a successful verification', async () => {
    installTransport(204);

    await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    expect(router?.state.location.pathname).toBe(`${APP_BASENAME}/${VERIFY_PATH}`);
    expect(router?.state.location.search).toBe(`?token=${TOKEN}`);
    expect(router?.state.historyAction, 'the screen pushed or replaced a history entry').toBe('POP');
  });
});

// ==========================================================================================================
// AC7 — exactly one request, including under StrictMode
// ==========================================================================================================

describe('AC7 one request per arrival', () => {
  it('sends exactly one request', async () => {
    const { sent } = installTransport();

    await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    expect(sent).toHaveLength(1);
  });

  it('sends exactly one request under StrictMode double invocation (AC7)', async () => {
    // THE CASE THIS SCREEN EXISTS TO SURVIVE. StrictMode runs effects twice in development; the token is
    // one-time and `markConsumed` burns it, so a second POST is REJECTED - the visitor would watch a
    // successful verification turn into task 49's failure, in development only. `CallbackScreen` carries a
    // comment saying its own missing guard once made it never navigate at all, under exactly these
    // conditions, while every other assertion still passed.
    const { sent } = installTransport();

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`, { strict: true });

    expect(sent, 'the effect ran twice and spent the token twice').toHaveLength(1);
    expect(container.textContent).toBe(`${en.verify.heading}${en.verify.success}`);
  });

  it('a token arriving AFTER a tokenless visit is still sent (AC7, AC12)', async () => {
    // THE SCREEN'S HEADER CLAIMS THIS AND NOTHING MEASURED IT (phase 7, F1; phase 8, I1). The comment says
    // the guard exists to mean "a request has been made", not "this effect has run" - and the difference is
    // only visible on a screen that goes from having no token to having one WITHOUT REMOUNTING. Every other
    // case here starts in its final state, so harness row TR05, which latches the guard above the
    // empty-token return, survived all 2042 cases.
    //
    // Navigating to the same path with a new query string keeps the same route matched, so React Router
    // re-renders the mounted screen with new `params` rather than building a new one - which is exactly the
    // condition the guard must survive.
    const { sent } = installTransport();
    await arrive(VERIFY_PATH);
    expect(sent, 'the tokenless arrival already posted, so this case cannot show anything').toHaveLength(0);

    await act(async () => { await router?.navigate(`/${VERIFY_PATH}?token=${TOKEN}`); });
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });

    expect(sent, 'the guard latched on the tokenless arrival, so the real token was never sent')
      .toHaveLength(1);
    expect((JSON.parse(sent[0]!.body) as { token: string }).token).toBe(TOKEN);
    expect(mounted!.container.textContent).toBe(`${en.verify.heading}${en.verify.success}`);
  });

  it('sets the guard BEFORE the request, not after (AC7)', async () => {
    // ORDER, not presence. A guard set after the call returns is no guard at all under StrictMode: both
    // invocations read `false` before either writes `true`, because nothing between them yields.
    //
    // KEPT, THOUGH IT IS THE BRITTLE HALF (phase 8, I10). This reads source text, so extracting a helper or
    // renaming a variable breaks it without anything being wrong - loudly, which is why Kimi rated it
    // acceptable. Its robust counterpart now exists: the case above drives the same property through
    // behaviour, by handing a mounted screen a token it did not arrive with. Both are kept because they
    // fail for different reasons - this one when the ORDER changes, that one when the guard's MEANING does -
    // and the StrictMode case covers a third, when the guard goes missing altogether.
    const code = (await read('features/account/VerifyScreen.tsx'))
      .replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');

    const guard = code.indexOf('handled.current = true');
    const post = code.indexOf('.POST(');
    expect(guard, 'the guard is gone').toBeGreaterThan(-1);
    expect(post, 'the request is gone').toBeGreaterThan(-1);
    expect(guard, 'the guard is set after the request, so StrictMode sends two').toBeLessThan(post);
  });
});

// ==========================================================================================================
// AC12 — no token, and an empty token, send nothing
// ==========================================================================================================

// THESE TWO CASES WERE THE REAL HANDOFF TO TASK 49, AND NEITHER SAID SO. Task 48 wrote a case further down
// labelled `PINS A LIMIT`, whose comment claimed "when task 49 lands, this case MUST FAIL - that is its
// purpose". Task 49 landed and it did not fail. These two did, loudly, because they pinned `textContent`
// equal to the heading ALONE - which is exactly the claim task 49's D-T49-2 reverses. A case labelled as a
// handoff was weaker than two cases that were not, and the label is not what made the difference: an
// EQUALITY against the full rendered text is.
//
// What they still measure is the half that did not change: **no request is sent**. Task 49 renders the
// uniform failure here WITHOUT asking the server, which is the whole point - there is no token to ask about.
describe('AC12 a link with no usable token sends no request, and now says so (T49 D-T49-2)', () => {
  it('sends nothing when the link carries no token at all', async () => {
    const { sent } = installTransport();

    const container = await arrive(VERIFY_PATH);

    expect(sent, 'a tokenless arrival posted something').toHaveLength(0);
    expect(container.textContent)
      .toBe(`${en.verify.heading}${en.verify.failureMessage}${en.verify.failureAction}`);
  });

  it('sends nothing when the token is present but empty', async () => {
    const { sent } = installTransport();

    const container = await arrive(`${VERIFY_PATH}?token=`);

    expect(sent, 'an empty token was posted, and the server would reject it').toHaveLength(0);
    expect(container.textContent)
      .toBe(`${en.verify.heading}${en.verify.failureMessage}${en.verify.failureAction}`);
  });

  it('a tokenless arrival and a rejected token render the SAME uniform failure (R4, T49)', async () => {
    // REWRITTEN, NOT DELETED, as task 48's own comment asked - and the rewrite is where the lesson is.
    //
    // Task 48 wrote this expecting it to FAIL when task 49 landed. **It passed.** Its assertion was that the
    // two arms render identically, and that was true when both rendered NOTHING and is true again now that
    // both render the uniform failure. A case written to detect a change survived the change, because what
    // it asserted was a SAMENESS that the change preserved - and sameness between two screens says nothing
    // about what is on either of them. Five empty strings are equal too; this project has had that bite
    // before, on T173's five-state pin.
    //
    // So the third assertion below is the one that gives the other two meaning: both arms must CONTAIN the
    // failure message. Without it, deleting the whole failure branch would leave this case green.
    const { sent } = installTransport(400);

    const noToken = (await arrive(VERIFY_PATH)).textContent;
    await act(async () => mounted!.root.unmount());
    mounted!.container.remove();
    mounted = undefined;
    router?.dispose?.();

    const rejected = (await arrive(`${VERIFY_PATH}?token=${TOKEN}`)).textContent;

    expect(rejected, 'a rejected token renders differently from a tokenless arrival, so R4 is not uniform')
      .toBe(noToken);
    expect(noToken, 'both arms are identical and BOTH ARE MISSING the failure message, which is how this '
      + 'case passed before task 49 landed').toContain(en.verify.failureMessage);
    expect(sent, 'the rejected arm did not actually send a request, so this proves nothing').toHaveLength(1);
  });
});

// ==========================================================================================================
// AC5 / AC6 — catalogue copy, moved focus, and an announcement
// ==========================================================================================================

describe('AC5/AC6 the copy is the catalogue\'s and the outcome is announced', () => {
  it('spells both catalogue keys as literal English (AC5, D7)', async () => {
    expect(en.verify.heading).toBe('Verify email');
    expect(en.verify.success).toBe('Your email has been verified.');
  });

  it('renders no untranslated key, which is what a missing catalogue entry looks like (AC5)', async () => {
    installTransport();

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    expect(container.textContent, 'a raw i18n key reached the DOM').not.toMatch(/verify\.(heading|success)/);
  });

  it('renders nothing the server sent (AC5)', async () => {
    // The contract gives 204 - NO body at all - so there is nothing to render even by mistake. The case is
    // here because `VerifyScreen` is shared with task 49, which will read a FAILURE response, and the first
    // edit that renders part of one breaks L11 in a file this assertion already watches.
    vi.stubGlobal('fetch', vi.fn(async () => new Response(
      JSON.stringify({ message: 'SERVER-SUPPLIED-COPY' }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    )));

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    expect(container.textContent, 'the screen rendered the response body').not.toContain('SERVER-SUPPLIED-COPY');
    // AND THE SUCCESS SENTENCE IS SHOWN FOR A 200, which is what holds `response.ok` against `=== 204`
    // (phase 7, F2; phase 8, I2). This case already mocked a non-204 success and asserted only what must
    // NOT appear, so harness row TR10 - narrowing the condition to the one status the contract declares -
    // survived all 2042 cases. The direction matters: with `=== 204`, a server answering 200 would leave
    // the visitor on `idle`, which after task 49 is the uniform FAILURE message, for a verification that
    // actually succeeded. Any 2xx means the account is verified, so any 2xx shows the success state.
    expect(container.textContent, 'a 200 did not reach the success state, so `response.ok` is not held')
      .toContain(en.verify.success);
  });

  it('CONTROL: focus is on body before anything mounts', () => {
    expect(document.activeElement).toBe(document.body);
  });

  it('moves focus to the heading on arrival (AC6, L11)', async () => {
    installTransport();

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    const heading = container.querySelector('h1');
    expect(heading, 'the screen has no h1 to receive focus').not.toBeNull();
    expect(document.activeElement, 'focus stayed on body, so an arriving visitor hears nothing').toBe(heading);
  });

  it('moves focus even when the link carried no token (AC6)', async () => {
    // Focus is unconditional and the request is not - which is why they are two effects in the screen. A
    // visitor who arrives at a broken link must still be told where they are.
    installTransport();

    const container = await arrive(VERIFY_PATH);

    expect(document.activeElement).toBe(container.querySelector('h1'));
  });

  it('announces the success sentence by filling a region that was already mounted (AC6)', async () => {
    // THE ANNOUNCEMENT IS A PROPERTY OF THE ORDER, NOT THE WORDS. `StatusRegion`'s own header says a live
    // region mounted at the same moment its text arrives is a new element with content already in it, and a
    // screen reader announces nothing. So the region must exist, empty, on arrival - which is observable:
    // a transport that never answers leaves the screen in exactly that state.
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => {})));

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    const region = container.querySelector('[role="status"]');
    expect(region, 'there is no live region on arrival, so the success sentence cannot be announced').not.toBeNull();
    expect(region!.getAttribute('aria-live')).toBe('polite');
    expect(region!.textContent, 'the region already holds text before the reply lands').toBe('');
  });

  it('adds no tab stop: the heading is focusable but not tabbable (AC6, L11)', async () => {
    installTransport();

    const container = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    expect(container.querySelector('h1')!.getAttribute('tabindex')).toBe('-1');
    expect([...container.querySelectorAll('[tabindex]')]
      .filter((node) => Number(node.getAttribute('tabindex')) >= 0),
    'the screen added a tab stop').toHaveLength(0);
  });
});

// ==========================================================================================================
// AC8 — the token is stored nowhere
// ==========================================================================================================

describe('AC8 the token reaches no storage', () => {
  it('writes no storage, and the token appears in none of it (AC8, L16)', async () => {
    // ON `Storage.prototype`, NEVER AN INSTANCE. T42 shipped a vacuous version of this exact claim because
    // an instance spy on jsdom storage does not intercept, and the assertion was NEGATIVE, so it passed
    // while eighty real writes went by.
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    installTransport();

    await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    expect(setItem.mock.calls.map(([key]) => key), 'the screen wrote to storage').toEqual([]);
    expect(JSON.stringify({ ...sessionStorage, ...localStorage }),
      'the token is sitting in a web storage somewhere').not.toContain(TOKEN);
  });

  it('CONTROL: the storage spy really does intercept', () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    sessionStorage.setItem('probe', 'value');
    expect(setItem, 'the spy is inert, so the negative assertion above measures nothing').toHaveBeenCalledWith('probe', 'value');
    sessionStorage.removeItem('probe');
  });
});

// ==========================================================================================================
// AC9 / AC10 — the route and the primitives
// ==========================================================================================================

describe('AC9/AC10 the path is declared and the screen is built from primitives', () => {
  it('changes no declared path set: `verify` was already there, with no element (AC9)', async () => {
    expect(PUBLIC_ROUTE_PATHS).toEqual(['sign-in', 'callback', 'register', 'register/sent', 'verify',
      'resend-verification', 'reset/request', 'reset', 'signed-out']);
    expect(VERIFY_PATH, 'the published constant and the declared path have drifted apart').toBe('verify');
    expect(PUBLIC_ROUTE_PATHS).toContain(VERIFY_PATH);
  });

  it('is attached by matching the constant, not by a literal (AC9, L14)', async () => {
    const code = await read('app/routes.tsx');

    expect(code, 'the attach branch spells the path instead of matching the constant')
      .toMatch(/if \(path === VERIFY_PATH\) return \{ path, element: <VerifyScreen \/> \};/);
  });

  it('bears no lowercase tag of its own, and takes no props (AC10)', async () => {
    const code = (await read('features/account/VerifyScreen.tsx'))
      .replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');

    expect(code, 'the screen renders an element instead of a primitive').not.toMatch(/<[a-z]/);
    expect(code, 'the screen takes a prop, so its one-input claim needs a case per value')
      .toMatch(/export function VerifyScreen\(\): ReactElement \{/);
  });

  it('reaches for no storage, no bare fetch and no timer (AC8, module boundaries)', async () => {
    const code = (await read('features/account/VerifyScreen.tsx'))
      .replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');

    // SCOPED TO WHAT TASK 48 OWNS, and `useNavigate` is deliberately NOT on this list. R4 gives task 49 "a
    // neutral resend action" on this same screen; a pin written here would box in a task nobody has designed
    // yet, which is the mistake T47's AC7 count pin made in the other direction.
    for (const shape of [/sessionStorage/, /localStorage/, /\bfetch\s*\(/, /setTimeout/, /setInterval/,
      /console\./]) {
      expect(code, `the verification screen reaches ${String(shape)}`).not.toMatch(shape);
    }
    expect(code, 'the call bypasses the generated client (L6)').toMatch(/createPublicAuthApi\(\)/);
  });
});

// ==========================================================================================================
// PHASE 10 — the constraints the brief wrote down and nothing aimed at
// ==========================================================================================================

describe('phase 10: the token is a credential, and the call has a shape', () => {
  it('never renders the token, in ANY state (L16, and L3\'s instinct)', async () => {
    // THE BRIEF SAYS THE TOKEN IS "NOT STORED, NOT LOGGED, NOT SENT TO ANALYTICS, NOT RENDERED" and only the
    // first of those had an instrument. A rendered credential is shoulder-surfable, lands in every DOM
    // snapshot and screenshot, and would be carried into any error report that serialises the page. The
    // success path happens to be covered by the equality assertions elsewhere; `idle` was not covered at
    // all, and `idle` is the state a visitor sits in while the request is in flight.
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(() => {})));

    const inFlight = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);
    expect(inFlight.innerHTML, 'the token is in the DOM while the request is in flight').not.toContain(TOKEN);

    await act(async () => mounted!.root.unmount());
    mounted!.container.remove();
    mounted = undefined;
    router?.dispose?.();

    installTransport(204);
    const succeeded = await arrive(`${VERIFY_PATH}?token=${TOKEN}`);
    expect(succeeded.innerHTML, 'the token is in the DOM after success').not.toContain(TOKEN);
    expect(succeeded.textContent, 'this arm did not actually reach the success state')
      .toContain(en.verify.success);
  });

  it('logs nothing at all, so the token cannot reach a console (L16)', async () => {
    // BEHAVIOURAL, where the source scan is structural, and the two catch different things. The scan forbids
    // the literal `console.`; this forbids a console call however it is spelled - through an alias, a
    // re-export, or a logger a later edit introduces. `tokensAreNeverWrittenToAnyStorage` does the same for
    // access tokens, and the reasoning transfers: a credential in a log is a credential in a log aggregator.
    // EVERY FUNCTION ON `console`, DERIVED RATHER THAN LISTED (phase 11, F4). The first version spied on
    // log/warn/error/debug/info, and Kimi pointed out that `table`, `dir`, `trace`, `group` and four more
    // exist and were unwatched - so `console.table(token)` would have leaked a credential while this case
    // passed. A hand-written list is a list that goes stale; deriving it means a console method added by a
    // future runtime is covered the day it appears.
    //
    // THE DERIVATION IS ITSELF ASSERTED, because a list built by reflection can quietly come back empty and
    // an empty list makes the negative assertion below vacuous - which is the failure mode this project has
    // now hit three times from the other direction.
    const methods = (Object.keys(console) as (keyof Console)[])
      .filter((name) => typeof console[name] === 'function');
    expect(methods, 'the console reflection found nothing, so the assertion below is vacuous')
      .toEqual(expect.arrayContaining(['log', 'warn', 'error', 'debug', 'info', 'table', 'dir', 'trace']));
    const spies = Object.fromEntries(methods.map((name) =>
      [name, vi.spyOn(console, name as 'log').mockImplementation(() => {})]));
    installTransport(204);

    await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    const logged = Object.values(spies).flatMap((spy) => spy.mock.calls.flat()).map(String).join('\n');
    expect(logged, 'the verification path logged something, and the token is in scope where it did').toBe('');
  });

  it('sends a POST with a JSON content type, as the contract declares (AC3)', async () => {
    const { sent } = installTransport();

    await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    // `POST /accounts/verify-email` with `VerifyEmailRequest`. A GET with the token in the query would be a
    // credential in a URL the server logs; a content type the server will not parse is a uniform failure the
    // visitor cannot distinguish from a bad token.
    expect(sent[0]!.method).toBe('POST');
    expect(sent[0]!.contentType, 'the body will not be parsed as VerifyEmailRequest')
      .toMatch(/^application\/json/);
  });

  it('verifies from a link with a trailing slash (AC1)', async () => {
    // `publicRouteSetIsExactlyTheDeclaredSet` already pins that `/app/verify/` RESOLVES, but it does so
    // against a synthetic table of probes, because when that case was written this path had no element.
    // It has one now, so the claim can be made end to end: a mail client that appends a slash still gets a
    // verified account rather than a uniform failure.
    const { sent } = installTransport(204);

    const container = await arrive(`${VERIFY_PATH}/?token=${TOKEN}`);

    expect(sent, 'a trailing slash stopped the verification').toHaveLength(1);
    expect(container.textContent).toBe(`${en.verify.heading}${en.verify.success}`);
  });

  it('takes the FIRST token when a link carries two (AC2)', async () => {
    // PINNED RATHER THAN DISCOVERED LATER. `?token=a&token=b` is a URL anyone can construct, and
    // `URLSearchParams.get` returns the first while `getAll` returns both - so the difference between this
    // screen sending one token and sending "a,b" is one method name. First-wins is also the safer of the
    // two: a token appended to a legitimate link is ignored rather than concatenated into something the
    // server might parse.
    const { sent } = installTransport();

    await arrive(`${VERIFY_PATH}?token=${TOKEN}&token=SECOND-TOKEN-APPENDED`);

    expect((JSON.parse(sent[0]!.body) as { token: string }).token).toBe(TOKEN);
  });
});

// ==========================================================================================================
// The token's alphabet — a latent trap, measured
// ==========================================================================================================

describe('the token alphabet', () => {
  it('round-trips a base64url token unchanged, including - and _', async () => {
    const { sent } = installTransport();

    await arrive(`${VERIFY_PATH}?token=${TOKEN}`);

    expect(TOKEN, 'this fixture no longer exercises the characters it was chosen for').toMatch(/[-_]/);
    expect((JSON.parse(sent[0]!.body) as { token: string }).token).toBe(TOKEN);
  });

  it('PINS A LATENT TRAP - a `+` in a token would arrive as a space', async () => {
    // NOT A DEFECT TODAY, AND THE CASE SAYS WHY IT IS HERE ANYWAY. `+` means "space" in a query string, so
    // `useSearchParams` decodes it to one; a token containing `+` would be sent wrong and rejected, and the
    // visitor would see task 49's failure with nothing anywhere explaining it.
    //
    // It cannot happen today because the server issues URL-SAFE base64:
    // `VerificationTokenService.generateRawToken` (:189-193) is `Base64.getUrlEncoder().withoutPadding()`,
    // whose alphabet is `A-Za-z0-9-_`. This case exists so that a change to a standard-alphabet encoder, or
    // to any token containing `+`, fails HERE with this comment attached rather than in production as an
    // unexplained verification failure. The assertion is on the DECODING, which is the part that would bite.
    const { sent } = installTransport();

    await arrive(`${VERIFY_PATH}?token=a+b`);

    expect((JSON.parse(sent[0]!.body) as { token: string }).token,
      'a `+` no longer decodes to a space - check whether the server still issues url-safe base64').toBe('a b');
  });
});
