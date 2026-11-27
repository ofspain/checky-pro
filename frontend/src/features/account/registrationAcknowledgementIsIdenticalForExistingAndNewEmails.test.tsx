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

function installTransport(reply: Reply): { sent: Request[] } {
  const sent: Request[] = [];
  vi.stubGlobal('fetch', vi.fn(async (request: Request) => {
    sent.push(request);
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
  await act(async () => {
    input.value = value;
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
// AC4 / AC5 — nothing is read and nothing is delayed
// ==========================================================================================================

describe('AC4/AC5 the acknowledgement reads nothing and delays nothing', () => {
  it('reaches for no response, no route state and no timer', async () => {
    const code = (await read('features/account/RegisterSentScreen.tsx'))
      .replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');

    // STRUCTURAL, and it is the only honest shape: a behavioural case cannot tell "did not read the status"
    // from "read it and did the same thing either way". The identical-output cases above measure the
    // OUTCOME; this measures the property that guarantees it for inputs nobody has mocked yet.
    for (const shape of [/\bfetch\s*\(/, /\.status\b/, /\bdata\b/, /\berror\b/, /setTimeout/, /setInterval/,
      /useLocation/, /useSearchParams/, /useParams/, /sessionStorage/, /localStorage/, /\bprops\b/]) {
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
    expect(new URL(sent[0]!.url).pathname).toBe('/accounts');
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
