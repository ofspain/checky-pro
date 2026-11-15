/// <reference types="node" />
/**
 * `registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints` (package.md §8) — task 45.
 *
 * **"REJECTS" IS THE TEST'S NAME, NOT THE SCREEN'S BEHAVIOUR**, and the difference is the whole point. R1
 * makes the 12-to-128 check "advisory guidance only" and names the server as the authority, so nothing here
 * is rejected by the client: the request goes, every time, exactly as it does for a password in range. What
 * this task adds is the GUIDANCE - the words R1 asks for and that tasks 43 and 44 deliberately left for it.
 * Before this task a visitor who typed eleven characters saw an outlined field, a status line saying the
 * account was being created, and no explanation anywhere.
 *
 * WHAT THIS FILE PROVES AND WHAT IT CANNOT:
 *
 *   1. It proves the guidance appears for a password outside the range, in BOTH directions and for astral
 *      input, and does not appear for one inside it - AC3 being the half that stops "always on" from
 *      passing, with its control in the same case.
 *   2. It proves the guidance is ATTACHED to the field, by walking from the input's `aria-describedby` to
 *      the element it names. It cannot prove a screen reader announces it: that is D-T45-1's accepted
 *      residual risk, and it is why this task describes rather than announces.
 *   3. It proves the sentence's numbers ARE `passwordLength.ts`'s constants. A catalogue string with "12 to
 *      128" typed into it renders identically, so this is the only assertion that can tell the two apart.
 *   4. It does not re-prove task 44's table. The rows there already assert that an out-of-range password
 *      reaches the transport; this file asserts the same thing once, for the guidance path, rather than
 *      copying a table that is not its own.
 *
 * THE HELPERS BELOW ARE COPIED FROM TASK 44'S SUITE, not imported (freeze, F7). Every named suite in this
 * package is self-contained; there is no shared test-util module, which is FU-T43-2 and is now recorded for
 * the third time rather than fixed inside a task that has no mandate to add one.
 */
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { REGISTER_PATH } from '../../app/routes';
import { en } from '../../i18n/catalogue';
import { createI18n } from '../../i18n/i18n';
import { ADVISORY_MAXIMUM_CODE_POINTS, ADVISORY_MINIMUM_CODE_POINTS, countCodePoints } from './passwordLength';
import { RegisterScreen } from './RegisterScreen';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const srcDir = dirname(dirname(dirname(expect.getState().testPath as string)));
const read = (relativePath: string) => readFile(join(srcDir, relativePath), 'utf8');

type Reply = { status: number; headers?: Record<string, string>; body?: string };
type Sent = { method: string; path: string; body: string };

function installBrowserRequest(): void {
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

function installTransport(respond: (request: Request) => Reply | Promise<Reply>): Sent[] {
  const sent: Sent[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (request: Request) => {
      sent.push({
        method: request.method,
        path: new URL(request.url).pathname,
        body: request.method === 'GET' ? '' : await request.clone().text(),
      });
      const reply = await respond(request);
      return new Response(reply.body ?? '{}', {
        status: reply.status,
        headers: reply.headers ?? { 'Content-Type': 'application/json' },
      });
    }),
  );
  return sent;
}

const ACCEPTED: Reply = { status: 202, body: JSON.stringify({ status: 'pending_verification' }) };

const settle = () => act(async () => {
  await new Promise<void>((resolve) => setTimeout(resolve, 0));
});

let mounted: { root: Root; container: HTMLElement } | undefined;

async function renderScreen(): Promise<HTMLElement> {
  const container = document.createElement('div');
  document.body.append(container);
  const router = createMemoryRouter([{ path: `/${REGISTER_PATH}`, element: <RegisterScreen /> }], {
    initialEntries: [`/${REGISTER_PATH}`],
  });
  const root = createRoot(container);
  await act(async () => {
    root.render(
      <I18nextProvider i18n={createI18n()}>
        <RouterProvider router={router} />
      </I18nextProvider>,
    );
  });
  mounted = { root, container };
  return container;
}

const emailInput = (container: HTMLElement) => container.querySelector<HTMLInputElement>('input[type="email"]')!;
const passwordInput = (container: HTMLElement) =>
  container.querySelector<HTMLInputElement>('input[type="password"]')!;
const submitControl = (container: HTMLElement) =>
  container.querySelector<HTMLButtonElement>('button[type="submit"]')!;

/**
 * The guidance element, found THROUGH THE FIELD rather than beside it.
 *
 * It deliberately does not query by id directly: reading `aria-describedby` off the input and following it
 * is the same walk assistive technology makes, so a case that finds the sentence here has also shown the
 * association exists. A sentence rendered with no `aria-describedby` pointing at it returns null, which is
 * the correct answer to "what describes this field".
 */
function describedElement(container: HTMLElement): HTMLElement | null {
  const id = passwordInput(container).getAttribute('aria-describedby');
  // SCOPED TO THE CONTAINER, not to the document (phase 7's F6, phase 8's I5). `GUIDANCE_ID` is a module
  // constant rather than a `useId()` value, so a second mount - or one container this file forgot to
  // remove - would make `document.getElementById` return whichever copy comes first in document order,
  // and a case could then assert against a screen it did not render. The walk is unchanged: the id still
  // comes off the field, which is what makes finding the sentence here evidence of the association.
  return id === null ? null : container.querySelector<HTMLElement>(`#${CSS.escape(id)}`);
}

/** The sentence as the catalogue renders it once the bounds are interpolated. */
const GUIDANCE = en.register.passwordGuidance
  .replace('{{min}}', String(ADVISORY_MINIMUM_CODE_POINTS))
  .replace('{{max}}', String(ADVISORY_MAXIMUM_CODE_POINTS));

async function type(input: HTMLInputElement, value: string): Promise<void> {
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
  expect(setter, 'HTMLInputElement has no value setter, so nothing below types anything').toBeTypeOf('function');
  await act(async () => {
    setter?.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

async function fillAndSubmit(container: HTMLElement, email: string, password: string): Promise<void> {
  await type(emailInput(container), email);
  await type(passwordInput(container), password);
  await act(async () => {
    submitControl(container).click();
  });
}

beforeEach(() => {
  installBrowserRequest();
  globalThis.localStorage.clear();
  globalThis.sessionStorage.clear();
});

afterEach(async () => {
  if (mounted) {
    const { root, container } = mounted;
    await act(async () => root.unmount());
    container.remove();
    mounted = undefined;
  }
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  globalThis.localStorage.clear();
  globalThis.sessionStorage.clear();
});

// ==========================================================================================================
// AC1 / AC2 — out of range, at either end, shows the guidance
// ==========================================================================================================

/**
 * One row per password this task speaks for, and every one of them is OUTSIDE the range.
 *
 * The in-range rows live in AC3's case, where they are the claim rather than the backdrop. Task 44's table
 * is not copied here: it owns whether a password is ACCEPTED, this file owns whether the visitor is TOLD.
 */
const OUT_OF_RANGE: ReadonlyArray<{ name: string; password: string; codePoints: number }> = [
  { name: '11 ASCII characters, one short of the lower bound', password: 'a'.repeat(11), codePoints: 11 },
  { name: '129 ASCII characters, one past the upper bound', password: 'a'.repeat(129), codePoints: 129 },
  // 129 CODE POINTS, 258 UTF-16 UNITS. Task 44 measured that the counting bug points a different way at
  // each end, so an astral row belongs on the side of the range where the bug REJECTS: if the screen ever
  // counted units, this password would be out of range for the wrong reason and the guidance would be
  // right by accident. The case below asserts the count as well as the sentence, so the two cannot agree
  // by coincidence.
  { name: '129 astral code points, which are 258 UTF-16 units', password: '\u{1F44D}'.repeat(129), codePoints: 129 },
  // D16's clause and the freeze's F11: zero is out of range, the count is total, and an untouched form is
  // the most ordinary accidental submit there is. It gets the same sentence as every other row because the
  // sentence names the RULE and never the input.
  { name: 'the empty string, which is zero code points', password: '', codePoints: 0 },
];

describe('AC1/AC2 a password outside the advisory range shows the guidance', () => {
  it.each(OUT_OF_RANGE)('shows the guidance for $name', async ({ password, codePoints }) => {
    const sent = installTransport(() => ACCEPTED);
    const container = await renderScreen();

    await fillAndSubmit(container, 'visitor@example.test', password);
    await settle();

    // THE SAME SENTENCE FOR EVERY ROW, asserted as equality rather than as "some guidance appeared".
    // D-T45-2 chose one string naming the rule precisely so neither end gets its own words, and an
    // assertion that only checked for a non-empty element would pass a screen that invented two.
    expect(describedElement(container)?.textContent,
      `${codePoints} code points is outside the range and the visitor was told nothing`).toBe(GUIDANCE);
    expect(countCodePoints(password), 'the counter disagrees with the row').toBe(codePoints);
    // AC6 in the same breath, because the two claims are only interesting together: R1's check is
    // ADVISORY, so the guidance appears AND the request goes. A client that explained itself and then
    // blocked would pass every other assertion in this file.
    expect(sent, 'the guidance came with a block, which R1 forbids').toHaveLength(1);
    expect(JSON.parse(sent[0]!.body) as { password: string },
      'the password was altered on the way out').toEqual({ email: 'visitor@example.test', password });
    expect(submitControl(container).hasAttribute('disabled')).toBe(false);
    expect(passwordInput(container).hasAttribute('disabled')).toBe(false);
  });
});

// ==========================================================================================================
// AC3 — in range shows nothing, and the query that says so can find it when it is there
// ==========================================================================================================

describe('AC3 a password inside the range shows no guidance', () => {
  it.each([
    { name: 'the lower bound itself', password: 'a'.repeat(ADVISORY_MINIMUM_CODE_POINTS) },
    { name: 'the upper bound itself', password: 'a'.repeat(ADVISORY_MAXIMUM_CODE_POINTS) },
    { name: '128 astral code points, which are 256 UTF-16 units', password: '\u{1F44D}'.repeat(128) },
  ])('shows nothing for $name', async ({ password }) => {
    installTransport(() => ACCEPTED);
    const container = await renderScreen();

    await fillAndSubmit(container, 'visitor@example.test', password);
    await settle();

    // THE NEGATIVE, and on its own it is worth nothing: a screen that never renders guidance at all passes
    // it, and so does a query that looks in the wrong place. The control is in the same case, below.
    expect(describedElement(container), 'an in-range password was given guidance it does not need').toBeNull();
    expect(container.textContent, 'the sentence is on the page without being attached to the field')
      .not.toContain(GUIDANCE);
  });

  it('CONTROL: the same query finds the guidance when it IS there', async () => {
    // THE SAME WALK AS THE CASES ABOVE - `aria-describedby` off the input, then `getElementById` - run
    // against a password that must produce a sentence. Without this, every assertion in this describe
    // block would pass against a screen with no guidance feature at all, which is exactly what this task
    // started from. T42's AC11 was vacuous for seven tasks for the want of four lines like these.
    installTransport(() => ACCEPTED);
    const container = await renderScreen();

    expect(describedElement(container), 'nothing describes the field before a submit').toBeNull();
    await fillAndSubmit(container, 'visitor@example.test', 'a'.repeat(11));
    await settle();
    expect(describedElement(container)?.textContent,
      'the query cannot see guidance that is there, so the negatives above measure nothing').toBe(GUIDANCE);
  });
});

// ==========================================================================================================
// AC4 — the sentence is attached to the field, not merely near it
// ==========================================================================================================

describe('AC4 the guidance is attached to the password field', () => {
  it('points the field at the element that holds the sentence, and at nothing else', async () => {
    installTransport(() => ACCEPTED);
    const container = await renderScreen();
    await fillAndSubmit(container, 'visitor@example.test', 'a'.repeat(11));
    await settle();

    const id = passwordInput(container).getAttribute('aria-describedby');
    expect(id, 'the field describes itself with nothing, so the sentence is visual only (L11)').not.toBeNull();
    const described = container.ownerDocument.getElementById(id!);
    expect(described, `aria-describedby names "${id}" and no element has that id`).not.toBeNull();
    expect(described?.textContent).toBe(GUIDANCE);
    // ONE id, not a list. `aria-describedby` takes space-separated ids and a screen that accumulated them
    // would announce the same sentence twice after two submits; this is the cheapest pin on that.
    expect(id).not.toContain(' ');
    // The EMAIL field is not described and not marked: R1's bound is the password's, and nothing in this
    // client judges an address (task 43, phase 7 F8).
    expect(emailInput(container).hasAttribute('aria-describedby')).toBe(false);
    expect(emailInput(container).getAttribute('aria-invalid')).toBeNull();
    // The mark and the sentence are one decision (phase 6's comment says so in the screen): a field marked
    // invalid with nothing to read is what task 43 shipped and what this task finishes.
    expect(passwordInput(container).getAttribute('aria-invalid')).toBe('true');
    // THE READING ORDER THE SCREEN'S OWN COMMENT CLAIMS, measured (phase 7's F7, phase 8's I4). The screen
    // says the sentence sits "between the field and the control so the reading order matches the visual
    // one", and nothing held it: the whole suite stayed green with the element moved after the submit
    // control. `aria-describedby` makes the association order-independent for anyone whose software
    // follows it, but a visitor reading or tabbing linearly meets the sentence where the DOM puts it, and
    // guidance that arrives after the button it should have informed is guidance arriving too late.
    const precedes = (first: Element, second: Element) =>
      (first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0;
    expect(precedes(passwordInput(container), described!),
      'the sentence is rendered before the field it explains').toBe(true);
    expect(precedes(described!, submitControl(container)),
      'the sentence is rendered after the control it is meant to come before').toBe(true);
  });

  it('removes both the mark and the sentence when a later submit is in range', async () => {
    // THE STATE MACHINE HAS A WAY BACK, and nothing measured it. `advisory` moves out-of-range -> in-range
    // on the next submit, and if the sentence outlived the mark the field would be described as invalid
    // while reading as acceptable - the worst of the three possible disagreements, because assistive
    // technology would announce a rule the screen no longer thinks is broken.
    installTransport(() => ACCEPTED);
    const container = await renderScreen();

    await fillAndSubmit(container, 'visitor@example.test', 'a'.repeat(11));
    await settle();
    expect(describedElement(container)?.textContent).toBe(GUIDANCE);

    await type(passwordInput(container), 'a'.repeat(ADVISORY_MINIMUM_CODE_POINTS));
    await act(async () => {
      submitControl(container).click();
    });
    await settle();

    expect(describedElement(container), 'the sentence outlived the state that justified it').toBeNull();
    expect(passwordInput(container).getAttribute('aria-invalid'),
      'the mark outlived the state that justified it').toBeNull();
  });
});

// ==========================================================================================================
// AC5 — nothing before the first submit
// ==========================================================================================================

describe('AC5 the guidance is a submit-time answer, not a running commentary', () => {
  it.each([
    { name: 'three characters', password: 'abc' },
    { name: 'two hundred characters', password: 'a'.repeat(200) },
  ])('shows nothing while $name are typed', async ({ password }) => {
    installTransport(() => ACCEPTED);
    const container = await renderScreen();

    await type(emailInput(container), 'visitor@example.test');
    await type(passwordInput(container), password);

    // BOTH ENDS OF THE RANGE WHILE TYPING, because a check that ran on change would be wrong in two
    // different ways: it would call a password of three characters a problem before the visitor has
    // finished, and it would do the same at two hundred. R1 asks for the check when the form is SUBMITTED.
    expect(describedElement(container), 'the screen judged a password that was still being typed').toBeNull();
    expect(container.textContent, 'the sentence is rendered before any submit').not.toContain(GUIDANCE);
    expect(passwordInput(container).getAttribute('aria-invalid')).toBeNull();
  });
});

// ==========================================================================================================
// AC7 — the words come from the catalogue, name the bounds, and deliver no verdict
// ==========================================================================================================

/**
 * The words that would make this sentence a verdict, in ONE place.
 *
 * The scan below and the control below it both read this list (phase 7's F5, phase 8's I6). They used to
 * hold a copy each, which meant the control proved something about its own copy: a word added to the scan
 * was uncontrolled, and a word added to the control was unenforced. A control has to measure the thing it
 * controls, not a sibling of it.
 */
const VERDICT_WORDS = ['short', 'long', 'invalid', 'wrong', 'error', 'failed'];

describe('AC7 the sentence names the rule, from the catalogue, with the real bounds', () => {
  it('renders the two bounds, and they ARE the constants the check uses', async () => {
    installTransport(() => ACCEPTED);
    const container = await renderScreen();
    await fillAndSubmit(container, 'visitor@example.test', 'a'.repeat(11));
    await settle();
    const sentence = describedElement(container)?.textContent ?? '';

    // TWO ASSERTIONS, TWO DIFFERENT MUTANTS, and the first draft of this comment credited the wrong one.
    // It said the digit identity below was "the only assertion that can kill the literal-bounds mutant",
    // which the freeze also said. It is not: a catalogue reading 'Use a password of 12 to 128 characters.'
    // renders 12 and 128, and 12 and 128 ARE the constants, so the identity passes. What kills that mutant
    // is the `{{min}}`/`{{max}}` pin on the catalogue, two assertions down.
    //
    // The identity earns its place against a different mistake: bounds SWAPPED or RENAMED in the template.
    // Measured - `{{max}} to {{min}}` fails here and nowhere else in the file but the literal pin.
    //
    // And NEITHER of them sees the third mistake, because the DOM cannot: a screen interpolating the
    // literals 12 and 128 renders the same sentence with the same numbers from the same template. That one
    // is pinned in SOURCE, in the case below, at the only end of the wire where provenance is visible.
    expect(sentence.match(/\d+/g), 'the sentence names no numbers, so it cannot be naming the rule')
      .toEqual([String(ADVISORY_MINIMUM_CODE_POINTS), String(ADVISORY_MAXIMUM_CODE_POINTS)]);
    expect(en.register.passwordGuidance,
      'the catalogue spells the bounds instead of interpolating them, so they can drift from the check')
      .toContain('{{min}}');
    expect(en.register.passwordGuidance).toContain('{{max}}');
    expect(sentence, 'the template reached the DOM unrendered').not.toContain('{{');
  });

  it('reaches the catalogue for the sentence and spells no part of it', async () => {
    const code = (await read('features/account/RegisterScreen.tsx'))
      .replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');

    expect(code, 'the screen does not reach the catalogue for register.passwordGuidance')
      .toContain("'register.passwordGuidance'");
    // THE SCREEN END OF THE PROVENANCE WIRE (phase 7's F1, phase 8's I1). Reaching the catalogue is not
    // enough: the bounds it interpolates must be the ones `passwordLength.ts` enforces. A screen passing
    // `{ min: 12, max: 128 }` renders a byte-identical sentence, leaves the catalogue's template intact and
    // satisfies the digit identity in the case above by arithmetic - measured at 126 of 126 cases passing,
    // which is how this hole was found rather than argued. Provenance is invisible to the DOM at BOTH ends,
    // so both ends are pinned in source: the catalogue must interpolate, and the screen must interpolate
    // THESE TWO NAMES.
    expect(code, 'the screen interpolates a literal minimum instead of the constant that enforces it')
      .toMatch(/min:\s*ADVISORY_MINIMUM_CODE_POINTS\b/);
    expect(code, 'the screen interpolates a literal maximum instead of the constant that enforces it')
      .toMatch(/max:\s*ADVISORY_MAXIMUM_CODE_POINTS\b/);
    // THE SENTENCE, NOT ITS WORDS, and the first draft of this case got that wrong and failed immediately.
    // It looped over every word longer than three characters and flagged `password` - which this screen
    // holds as a state variable, a prop name and a catalogue key fragment. The claim was never that the
    // screen may not contain the WORDS: it is that the screen must not SPELL the sentence instead of
    // looking it up, which is the shape task 43 settled on for exactly this reason. Both forms are
    // checked, because a screen could hardcode either the rendered sentence or the raw template.
    for (const spelled of [GUIDANCE, en.register.passwordGuidance]) {
      const escaped = spelled.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      expect(code, `the screen spells "${spelled}" instead of looking it up`)
        .not.toMatch(new RegExp(`(?<![A-Za-z0-9_$])${escaped}(?![A-Za-z0-9_$])`));
    }
  });

  it('delivers guidance, not a verdict (D-T45-2)', () => {
    // THE COPY DECISION, PINNED. R1 makes the client's check advisory and the server the authority, so the
    // client has no standing to tell a visitor their password is wrong - it can only name the rule. This
    // is the one case in this package that polices prose, and it is deliberately short.
    for (const verdict of VERDICT_WORDS) {
      expect(GUIDANCE.toLowerCase(), `the guidance delivers a verdict: "${verdict}"`)
        .not.toMatch(new RegExp(`\\b${verdict}`));
    }
    expect(GUIDANCE, 'the guidance stopped naming the rule').toBe('Use a password of 12 to 128 characters.');
  });

  it('CONTROL: the verdict scan fires on a sentence that DOES deliver one, and not on a word that contains it', () => {
    // BOTH DIRECTIONS, because a forbidden-word scan fails in two ways and only one of them is loud. A
    // `toContain('long')` would report "belongs" as a verdict; a scan aimed at nothing would pass anything.
    const fires = (text: string) =>
      VERDICT_WORDS.some((word) => new RegExp(`\\b${word}`).test(text.toLowerCase()));
    expect(fires('Your password is too short.'), 'the scan cannot see a verdict').toBe(true);
    expect(fires('That password is invalid.'), 'the scan cannot see a verdict').toBe(true);
    expect(fires('This password belongs to someone else.'),
      'the scan reads "belongs" as "long", which is the substring trap this package has hit twice')
      .toBe(false);
  });
});
