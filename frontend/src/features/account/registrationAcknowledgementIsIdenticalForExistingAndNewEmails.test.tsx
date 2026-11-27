/// <reference types="node" />
/**
 * T47 — `registrationAcknowledgementIsIdenticalForExistingAndNewEmails` (`package.md` §8), R2 and L4.
 *
 * **WHAT MAKES THIS HARD TO TEST IS THAT THE SERVER ALREADY DOES THE WORK.** `POST /accounts` answers 202
 * for a new address AND for one already registered, so a suite that mocks the same reply twice and finds the
 * same screen has measured that a pure function is pure. Every case below that claims "identical" therefore
 * mocks REPLIES THAT DIFFER - different bodies, and a status the contract never declares - and asserts the
 * output does not. That is the only shape in which the claim has content.
 *
 * **THE SCREEN'S DEFENCE IS THAT IT HAS NO INPUTS.** `RegisterSentScreen` takes no props, reads no router
 * state, no search params, no storage and makes no call, so there is no value in scope that could differ
 * between the two cases. AC8's cold-arrival case and AC4's source scan are what hold that, and they are the
 * reason the identical claim is cheap to keep rather than something to re-prove per field later.
 *
 * **THE THREE WORDS THE SERVER SENDS ARE NEVER RENDERED** (D2). `RegistrationAcknowledgement` is
 * `required: [message]`, so every reply carries a sentence this client ignores. Two cases assert the rendered
 * text equals the CATALOGUE literal and does NOT contain the mocked message - trap 3 - because a screen that
 * rendered the body would pass an identical-output test whenever the two mocks happened to agree, and would
 * drift the moment they did not.
 */
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { act, StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider, type createMemoryRouter as CreateMemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { APP_BASENAME, appRoutes, REGISTER_PATH, REGISTER_SENT_PATH } from '../../app/routes';
import { en } from '../../i18n/catalogue';
import { createI18n } from '../../i18n/i18n';
import { RegisterSentScreen } from './RegisterSentScreen';

// Lets act() flush React 19 renders synchronously - the shape eleven of the mounting suites use, written out
// here rather than relied on from a neighbour (task 174, phase 11).
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// This file is frontend/src/features/account/<name>; three dirnames up is `src/` (T05's technique, used
// because jsdom gives `import.meta.url` a non-file URL).
const srcDir = dirname(dirname(dirname(expect.getState().testPath as string)));
const read = (relativePath: string) => readFile(join(srcDir, relativePath), 'utf8');

type Reply = { status: number; body?: string };

/** The two replies a registration can draw, written to DIFFER in every way the contract allows. */
const NEW_ADDRESS: Reply = { status: 202, body: JSON.stringify({ message: 'Verification sent to a new address.' }) };
const ALREADY_REGISTERED: Reply = {
  status: 202,
  body: JSON.stringify({ message: 'This address is already registered; no action taken.' }),
};

let mounted: { root: Root; container: HTMLElement } | undefined;
let router: ReturnType<typeof CreateMemoryRouter> | undefined;

/** Browser-style relative URL resolution: Node's Request rejects relative URLs, a browser resolves them (T05). */
function installBrowserRequest(): void {
  const NodeRequest = globalThis.Request;
  vi.stubGlobal('Request', class extends NodeRequest {
    constructor(input: RequestInfo | URL, init?: RequestInit) {
      super(typeof input === 'string' ? new URL(input, window.location.href) : input, init);
    }
  });
}

type Sent = { path: string; body: string };

/**
 * THE BODY IS READ INSIDE THE HANDLER, which is not a style choice: a `Request`'s body can be consumed
 * once, so reading it after the fact in a case gets an exception or an empty string depending on who got
 * there first. `request.clone().text()` here is the shape the sibling registration suite uses.
 */
function installTransport(reply: Reply): { sent: Sent[] } {
  const sent: Sent[] = [];
  vi.stubGlobal('fetch', vi.fn(async (request: Request) => {
    sent.push({ path: new URL(request.url).pathname, body: await request.clone().text() });
    return new Response(reply.body ?? '{}', {
      status: reply.status,
      headers: { 'Content-Type': 'application/json' },
    });
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

/** Mounts the REAL route table at an `/app` location, so every case goes through `routes.tsx` (trap 4). */
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
  return container;
}

const field = (container: HTMLElement, type: string) =>
  container.querySelector<HTMLInputElement>(`input[type="${type}"]`)!;

async function type(input: HTMLInputElement, value: string): Promise<void> {
  // THROUGH THE PROTOTYPE'S SETTER, NOT `input.value =`, AND THE FIRST VERSION OF THIS HELPER GOT IT WRONG
  // (phase 8, I9). React installs its own `value` setter on the element instance to keep the change tracker
  // in step, so assigning through the instance updates the tracker as well and React can conclude nothing
  // changed. It happens to work here, because no case in this file types twice into one field - every field
  // goes from empty to a value exactly once - which is precisely the kind of accident that survives review
  // and then fails in the next suite that copies it.
  //
  // The sibling registration suite already landed on this form, and the reason is in ITS phase 7 F7 and
  // phase 8 I11: `setter?.call` silently did nothing when the descriptor lacked a setter, and the failure
  // surfaced as an empty request body three cases away from the cause. So the descriptor is ASSERTED rather
  // than optional-chained past. This task re-introduced the weaker shape a closed task had already paid to
  // learn about, which is the argument for FU-T43-2 (no shared test-util module) getting stronger, not for
  // copying the fix a fourth time quietly.
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
  expect(setter, 'HTMLInputElement has no value setter, so nothing below types anything').toBeTypeOf('function');
  await act(async () => {
    setter?.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

/** Lets the POST's promise, React's state update and the navigation all settle. */
const settle = () => act(async () => {
  await Promise.resolve();
  await Promise.resolve();
});

/** The whole journey through the real table: fill the real form, submit, land wherever the screen sends us. */
async function registerWith(reply: Reply): Promise<HTMLElement> {
  installTransport(reply);
  const container = await arrive(REGISTER_PATH);
  await type(field(container, 'email'), 'visitor@example.test');
  await type(field(container, 'password'), 'a'.repeat(12));
  await act(async () => {
    container.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
  });
  await settle();
  return container;
}

// ==========================================================================================================
// AC1 / AC2 / AC3 — the acknowledgement, and that it is the SAME one
// ==========================================================================================================

describe('registrationAcknowledgementIsIdenticalForExistingAndNewEmails', () => {
  it('renders a non-empty acknowledgement at /app/register/sent (AC1)', async () => {
    const container = await arrive(REGISTER_SENT_PATH);

    expect(container.querySelector('h1'), 'the route still carries no element').not.toBeNull();
    expect(container.textContent).toContain(en.registerSent.heading);
    expect(container.textContent).toContain(en.register.success);
  });

  it('renders byte-identical markup for a new address and an already-registered one (AC2, AC3)', async () => {
    const forNew = (await registerWith(NEW_ADDRESS)).innerHTML;
    const landedNew = router?.state.location.pathname;

    await act(async () => mounted!.root.unmount());
    mounted!.container.remove();
    mounted = undefined;
    router?.dispose?.();

    const forExisting = (await registerWith(ALREADY_REGISTERED)).innerHTML;

    // COMPARED AS MARKUP, NOT AS TEXT, because R2 says "copy AND layout": a screen that reordered its
    // elements or changed a tag would pass a textContent comparison while looking different to anyone
    // reading it, and different again to a screen reader walking the tree.
    // THE PROBES MUST DIFFER OR EVERY LINE BELOW IS VACUOUS (phase 7, F2; harness row TR16). The server
    // answers 202 both times, so two identical mocks would prove only that the same input gives the same
    // output - and nothing else in this file noticed if someone edited one body to match the other. Trap 1
    // was defended by a comment until this assertion existed; now the probe and the claim cannot drift apart.
    expect(ALREADY_REGISTERED.body, 'the two probe replies are identical, so "identical" measures nothing')
      .not.toBe(NEW_ADDRESS.body);
    expect(forExisting, 'the acknowledgement differs between a new and an existing address').toBe(forNew);
    expect(landedNew, 'the two cases landed in different places').toBe(router?.state.location.pathname);
    expect(forNew, 'the acknowledgement rendered empty, so identical means nothing').toContain(
      en.registerSent.heading,
    );
  });

  it('is identical even for a status the contract never declares (AC2, trap 1)', async () => {
    // THE REPLIES MUST DIFFER OR THIS SUITE MEASURES NOTHING. The server answers 202 both times, so two
    // identical mocks would prove only that the same input gives the same output. A 409 is the reply a
    // server that DID distinguish the two cases would send, which makes it the sharpest available probe:
    // if anything in this client reads the status, this is the case that diverges.
    const accepted = (await registerWith(NEW_ADDRESS)).innerHTML;

    await act(async () => mounted!.root.unmount());
    mounted!.container.remove();
    mounted = undefined;
    router?.dispose?.();

    const conflicted = (await registerWith({ status: 409, body: JSON.stringify({ message: 'exists' }) })).innerHTML;

    expect(conflicted, 'the client branched on a status the contract does not declare').toBe(accepted);
  });

  it('lands on the same path in both cases, and it is REGISTER_SENT_PATH (AC3)', async () => {
    await registerWith(ALREADY_REGISTERED);

    // `router.state.location.pathname`, NOT `useLocation().pathname` - T41's finding, and it cost production
    // code: the hook's value is basename-relative while the router's state carries the whole path.
    expect(router?.state.location.pathname, 'the form did not reach the acknowledgement')
      .toBe(`${APP_BASENAME}/${REGISTER_SENT_PATH}`);
  });

  it('REPLACES the form in history, so Back cannot resubmit the address (AC3)', async () => {
    // PHASE 7's F1, AND IT WAS A REAL HOLE: the plan chose `replace` over a push with a stated reason -
    // Back from the acknowledgement must not return to a filled form whose resubmission posts the same
    // address again - and nothing asserted it. Harness row TR10 dropped `replace: true` and survived all
    // 2010 cases, which is what turned an argument into a finding.
    const container = await registerWith(NEW_ADDRESS);

    // THE FLAG, which is the cheap half.
    expect(router?.state.historyAction, 'the acknowledgement was PUSHED, so Back returns to the form')
      .toBe('REPLACE');

    // AND THE CONSEQUENCE, which is the half worth having. A visitor pressing Back must not arrive at a
    // form that would send the same address a second time - two verification emails for one registration,
    // which is OQ-T43-11's shape reached by the browser's own control rather than a double click.
    await act(async () => {
      await router?.navigate(-1);
    });

    expect(container.querySelector('form'), 'Back reached the registration form, which could resubmit')
      .toBeNull();
  }, 30_000);

  it('renders the catalogue sentence and NOT the message the server sent (AC6, trap 3)', async () => {
    const container = await registerWith(ALREADY_REGISTERED);

    expect(container.textContent).toContain(en.register.success);
    expect(container.textContent).toBe(`${en.registerSent.heading}${en.register.success}`);
    // D2: the contract requires `message` and this client ignores it. Asserting its ABSENCE is what stops a
    // later "improvement" from rendering the body and passing every identical-output case above by accident.
    expect(container.textContent, 'the server\'s message reached the screen (D2, L11)')
      .not.toContain('already registered; no action taken');
  });

  it('spells both catalogue keys as literal English (AC6, D7)', async () => {
    const container = await arrive(REGISTER_SENT_PATH);

    expect(en.registerSent.heading).toBe('Check your email');
    expect(en.register.success).toBe('Check your email to finish creating your account.');
    expect(container.querySelector('h1')?.textContent).toBe(en.registerSent.heading);
    // Neither key may be a translation miss: i18next renders the KEY when a lookup fails, which reads as
    // plausible prose in a terminal and as nonsense on a screen.
    expect(container.textContent).not.toMatch(/registerSent|register\.success/);
  });
});

// ==========================================================================================================
// PHASE 10 — the criteria no mutant had aimed at
// ==========================================================================================================

describe('phase 10: the claims the implementation left structural', () => {
  it('renders IDENTICALLY on a cold arrival and at the end of a submit (AC2, AC8)', async () => {
    // THE STRONGEST FORM OF "IT HAS NO INPUTS", and the one the suite was missing. Every other identical
    // case compares two JOURNEYS, which differ only in what the server said; this compares a journey with
    // NO JOURNEY AT ALL. A screen that took anything from the submit - a prop, router state, a flag in
    // storage - renders differently for a visitor who pasted the URL, and nothing above this line could
    // tell. AC8 asserted the cold arrival renders; this asserts it renders THE SAME THING.
    const cold = (await arrive(REGISTER_SENT_PATH)).innerHTML;

    await act(async () => mounted!.root.unmount());
    mounted!.container.remove();
    mounted = undefined;
    router?.dispose?.();

    const afterSubmit = (await registerWith(ALREADY_REGISTERED)).innerHTML;

    expect(afterSubmit, 'the acknowledgement depends on how the visitor arrived').toBe(cold);
    expect(cold, 'the cold arrival rendered nothing, so this comparison is vacuous')
      .toContain(en.registerSent.heading);
  }, 30_000);

  it('is identical for a reply that is not JSON at all (AC2, AC4)', async () => {
    // THE BOUNDARY THE PROBE TABLE DOES NOT REACH. `NEW_ADDRESS` and `ALREADY_REGISTERED` are both
    // well-formed JSON objects, and the contract says `message` is REQUIRED - so a client that parsed the
    // body would work for every row above and fall over here. An empty body is what a 202 with no content
    // actually looks like on the wire, and it is the shape `JSON.parse` throws on.
    const container = await registerWith({ status: 202, body: '' });

    expect(container.textContent).toBe(`${en.registerSent.heading}${en.register.success}`);
    expect(router?.state.location.pathname).toBe(`${APP_BASENAME}/${REGISTER_SENT_PATH}`);
  }, 30_000);

  it('renders exactly one heading, so the document outline has no gap (L11)', async () => {
    const container = await arrive(REGISTER_SENT_PATH);

    // `Heading` HAS NO `level` PROP, and its own header says why: a level prop's first use would be a
    // second `<h1>` or an `<h2>` with no `<h1>` above it. This is the case that would notice.
    expect(container.querySelectorAll('h1')).toHaveLength(1);
    expect(container.querySelectorAll('h2, h3, h4, h5, h6'),
      'a subheading appeared with no Subheading primitive to own it').toHaveLength(0);
  });

  it('OPEN QUESTION PINNED - the acknowledgement offers nowhere to go (D3, OQ-T47-4)', async () => {
    // THIS CASE MUST FAIL WHEN OQ-T47-4 IS ANSWERED YES, and that is its entire job. The freeze deferred
    // the sign-in link on the grounds that R2 does not need one and that adding it would introduce
    // catalogue keys and navigation R2 never asked for. A deferral nobody can see is indistinguishable
    // from an oversight, so the decision is written here as an assertion: the day someone adds a
    // `TextLink`, this breaks and names the open question instead of letting a quiet change answer it.
    const container = await arrive(REGISTER_SENT_PATH);

    // THE ROLE SELECTORS ARE DEFENCE IN DEPTH AND CANNOT BE FALSIFIED FROM HERE, which is worth saying
    // plainly rather than leaving a reader to assume a mutant proved them (phase 11, F3). Kimi's point is
    // right on the wording: "nowhere to go" should cover a focusable `div` with `role="link"`, and the
    // five-element list did not. But the only way to put a roled element on this screen is a lowercase
    // tag, and AC11 below forbids `/<[a-z]/` in this file's source outright - so every mutant that would
    // exercise the widening is killed by a different assertion first. It is kept because it costs nothing
    // and because the day AC11 is relaxed for some primitive that forwards a role, this is already right.
    expect(container.querySelectorAll('a, button, input, select, textarea, [role="button"], [role="link"], [contenteditable]'),
      'the acknowledgement grew a control - if that is intended, answer OQ-T47-4 and delete this case')
      .toHaveLength(0);
    // The one focusable node is the heading, and it is not a tab stop.
    expect([...container.querySelectorAll('[tabindex]')].map((node) => `${node.tagName}:${node.getAttribute('tabindex')}`))
      .toEqual(['H1:-1']);
  });

  it('OPEN QUESTION PINNED - a double submit sends TWO requests (OQ-T43-11)', async () => {
    // MEASURED, NOT ASSUMED, AND IT IS THE UNHAPPY ANSWER. Two presses before the first reply send two
    // POSTs, which means two verification emails for one registration. Nothing in this task or task 43
    // guards it - no disable, no in-flight latch - and OQ-T43-11 is where that is recorded as the owner's
    // call rather than a bug anyone has authorised fixing.
    //
    // The case pins TODAY'S behaviour so the question cannot be closed silently in either direction: it
    // fails if a guard appears (and should then be rewritten to assert one request), and it fails if the
    // navigation starts happening twice. What it also proves is the half that IS safe - the journey still
    // ends in one place, once, with the history entry replaced rather than stacked.
    const { sent } = installTransport(ALREADY_REGISTERED);
    const container = await arrive(REGISTER_PATH);
    await type(field(container, 'email'), 'visitor@example.test');
    await type(field(container, 'password'), 'a'.repeat(12));
    const control = container.querySelector<HTMLButtonElement>('button[type="submit"]')!;
    await act(async () => {
      control.click();
      control.click();
    });
    await settle();

    expect(sent, 'a double submit no longer sends two requests - OQ-T43-11 has been answered')
      .toHaveLength(2);
    expect(router?.state.location.pathname, 'the second submit navigated somewhere else')
      .toBe(`${APP_BASENAME}/${REGISTER_SENT_PATH}`);
    expect(router?.state.historyAction, 'the second navigation stacked a history entry').toBe('REPLACE');
    expect(container.textContent).toBe(`${en.registerSent.heading}${en.register.success}`);
  }, 30_000);
});

// ==========================================================================================================
// AC4 / AC5 — nothing is read and nothing is delayed
// ==========================================================================================================

describe('AC4/AC5 the acknowledgement reads nothing and delays nothing', () => {
  it('reaches for no response, no route state and no timer', async () => {
    const code = (await read('features/account/RegisterSentScreen.tsx'))
      .replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');

    // STRUCTURAL, and it is the only honest shape: a behavioural case cannot tell "did not read the status"
    // from "read it and did the same thing either way". The identical-output cases above measure the
    // OUTCOME; this measures the property that guarantees it for inputs nobody has mocked yet.
    // `useNavigate` AND `useHref` JOINED THE LIST IN PHASE 11 (F4). Every other router hook here is
    // forbidden because READING route state would give the screen an input; these two are forbidden for
    // the opposite reason - an acknowledgement that navigates, or that computes a URL to navigate to, is
    // no longer the end of the journey. Both are real react-router exports, which is checked rather than
    // assumed: a pattern aimed at a name that does not exist forbids nothing and invalidates its own
    // mutant, which is how T37 lost a row.
    for (const shape of [/\bfetch\s*\(/, /\.status\b/, /\bdata\b/, /\berror\b/, /setTimeout/, /setInterval/,
      /useLocation/, /useSearchParams/, /useParams/, /useNavigate/, /useHref/,
      /sessionStorage/, /localStorage/, /\bprops\b/]) {
      expect(code, `the acknowledgement reaches ${String(shape)}`).not.toMatch(shape);
    }
  });

  it('sends exactly one request for the whole journey, and no retry (AC4)', async () => {
    const { sent } = installTransport(ALREADY_REGISTERED);
    const container = await arrive(REGISTER_PATH);
    await type(field(container, 'email'), 'visitor@example.test');
    await type(field(container, 'password'), 'a'.repeat(12));
    await act(async () => {
      container.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    });
    await settle();

    expect(sent, 'the journey sent more than the one POST, so something retried').toHaveLength(1);
    expect(sent[0]!.path).toBe('/accounts');
    // AND WHAT WAS TYPED REALLY LEFT THE TAB, which is the assertion that makes `type()` above an
    // instrument instead of a hope (phase 8, I9; measured in phase 9). Without it a helper that typed
    // NOTHING passed all eighteen cases in this file: the transport is mocked, so an empty form still
    // gets a 202 and still navigates, and every identical-output and focus case stays green. The
    // asserted descriptor in `type()` catches a MISSING setter; only this line catches a setter that is
    // never called. The sibling suite had body assertions from the start, which is why its helper was
    // never silently inert, and why copying its helper without copying this line would have left the
    // same hole with better-looking code in it.
    expect(JSON.parse(sent[0]!.body) as { email: string; password: string },
      'the form sent something other than what was typed into it')
      .toEqual({ email: 'visitor@example.test', password: 'a'.repeat(12) });
  });
});

// ==========================================================================================================
// AC7 — focus, through the real table and under StrictMode
// ==========================================================================================================

describe('AC7 focus moves to the heading on arrival (L11)', () => {
  it('CONTROL: body holds focus before anything mounts', () => {
    // Without this, "not body" below would be true for a screen that managed nothing at all.
    expect(document.activeElement, 'focus was elsewhere before the mount, so the next case measures nothing')
      .toBe(document.body);
  });

  it('focuses the h1 when arrived at through the real route table', async () => {
    const container = await arrive(REGISTER_SENT_PATH);
    const heading = container.querySelector('h1');

    expect(document.activeElement, 'focus was left where the submit button used to be (L11)').toBe(heading);
    expect(document.activeElement?.textContent, 'focus moved to something that does not name the screen')
      .toBe(en.registerSent.heading);
  });

  it('focuses the h1 at the end of the real submit journey, not only on a direct arrival', async () => {
    // A direct arrival is the screen test; this is the journey test. They differ in the way that matters:
    // on the journey, focus starts on the submit button that is about to be unmounted, which is the state
    // in which a missing focus move strands a keyboard user with nowhere to be.
    const container = await registerWith(NEW_ADDRESS);

    expect(document.activeElement, 'focus was dropped by the navigation').toBe(container.querySelector('h1'));
  });

  it('survives StrictMode double invocation', async () => {
    const container = await arrive(REGISTER_SENT_PATH, { strict: true });

    expect(document.activeElement, 'the second effect invocation moved or lost focus')
      .toBe(container.querySelector('h1'));
  });

  it('adds no tab stop: the heading is focusable but not tabbable', async () => {
    const container = await arrive(REGISTER_SENT_PATH);

    expect(container.querySelector('h1')?.getAttribute('tabindex')).toBe('-1');
    expect(container.querySelectorAll('[tabindex]:not([tabindex="-1"])'),
      'the acknowledgement inserted a tab stop').toHaveLength(0);
  });
});

// ==========================================================================================================
// AC8 / AC9 — a cold arrival, and no storage
// ==========================================================================================================

describe('AC8/AC9 a cold arrival writes nothing and needs nothing', () => {
  it('renders on a cold arrival with no preceding submit and no router state (AC8)', async () => {
    // L14 and L16: every route must survive being typed, pasted or reloaded. Nothing above this line has
    // submitted a form, so if the screen depended on state handed over by the navigation it renders empty
    // here - which is the failure mode a journey-only suite never sees.
    const container = await arrive(REGISTER_SENT_PATH);

    expect(container.textContent).toBe(`${en.registerSent.heading}${en.register.success}`);
  });

  it('writes no storage, and reads nothing but react-router\'s own key (AC9)', async () => {
    // SPIED ON `Storage.prototype`, NOT ON AN INSTANCE. T42 shipped a vacuous "writes no storage" case for
    // exactly this reason: an instance spy on jsdom storage does not intercept, so the assertion was
    // negative, passed from birth, and said nothing while eighty correct uses existed elsewhere.
    //
    // THE READ EXCEPTION IS MEASURED, NOT ASSUMED, and it is not this screen's. The first version of this
    // case asserted `getItem` was never called and failed with one call; the stack named
    // `restoreAppliedTransitions` inside `createMemoryRouter(...).initialize()`, which reads
    // `sessionStorage['remix-router-transitions']` before any component exists. Mounting the REAL table is
    // what brings it in, and the alternative - mounting the screen alone - would have made the case pass by
    // measuring less. So the KEYS are asserted rather than the call count: a read by this screen names a
    // different key and fails, while react-router's own bookkeeping is named here with its reason.
    const touched = (spy: { mock: { calls: readonly unknown[][] } }) =>
      spy.mock.calls.map((call) => String(call[0]));
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    const removeItem = vi.spyOn(Storage.prototype, 'removeItem');
    const getItem = vi.spyOn(Storage.prototype, 'getItem');

    await arrive(REGISTER_SENT_PATH);

    expect(touched(setItem), 'the acknowledgement wrote storage').toEqual([]);
    expect(touched(removeItem), 'the acknowledgement cleared storage').toEqual([]);
    expect(touched(getItem).filter((key) => key !== 'remix-router-transitions'),
      'the acknowledgement read storage of its own').toEqual([]);
  });

  it('CONTROL: the storage spy really does intercept', () => {
    // The three negatives above are only worth their lines if a write WOULD be seen. This is the case T42
    // did not have.
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    globalThis.sessionStorage.setItem('probe', 'x');

    expect(setItem, 'the spy does not intercept, so AC9 above measures nothing').toHaveBeenCalledWith('probe', 'x');
    globalThis.sessionStorage.clear();
  });
});

// ==========================================================================================================
// AC11 — the screen renders only through the design system
// ==========================================================================================================

describe('AC11 the acknowledgement renders through primitives only', () => {
  it('bears no lowercase tag of its own, and takes no props', async () => {
    const code = (await read('features/account/RegisterSentScreen.tsx'))
      .replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');

    expect(code, 'the acknowledgement renders an element instead of a primitive').not.toMatch(/<[a-z]/);
    expect(code, 'the screen takes a prop, so identical output needs a case per value')
      .toMatch(/export function RegisterSentScreen\(\): ReactElement \{/);
    // The pinned render-site list in `screensRenderThroughDesignSystemPrimitivesOnly` is the other half of
    // this, and it is the instrument that actually fails if a future edit reaches past the barrel.
    expect(code).toMatch(/from '\.\.\/\.\.\/app\/ui'/);
  });
});
