/// <reference types="node" />
/**
 * `registrationAcceptsTwelveAndOneTwentyEightCodePoints` (package.md §8) — task 43, the registration form.
 *
 * THE NAME IS FULLY EARNED AS OF TASK 44, and it was half a promise before that. Task 43 owned the 12 end
 * and wrote the length cases as a TABLE precisely so the 128 end would arrive as rows rather than as copied
 * cases; task 44 added them - 128 ASCII, 128 astral, 129 - and extended the control that keeps the column
 * load-bearing at both ends. The two halves of the range live in one file because they are one claim about
 * one check, and splitting a range across two files is how its halves drift apart.
 *
 * WHAT THIS FILE PROVES AND WHAT IT CANNOT:
 *
 *   1. It proves the advisory check does not BLOCK. Every row in the table reaches the transport, including
 *      the one that is out of range - which is R1's actual requirement ("advisory guidance only ... The
 *      server is the authority") and the one thing a client-side length check is most likely to get wrong.
 *   2. It cannot prove that pressing Enter in a field submits the form. jsdom does not implement implicit
 *      submission, so a case that pressed Enter and asserted a submit would fail against correct code. AC9
 *      asserts the STRUCTURAL cause instead - the control is `type="submit"` inside the form - and measures
 *      the click path that shares the handler. The gap is OQ-T43-8, with the dependency it would cost.
 *   3. It does not prove the ARRIVAL at `/app/register`. These cases mount the screen under a memory router.
 *      The route attach is pinned where the route claims live: `publicRouteSetIsExactlyTheDeclaredSet` and
 *      `routesResolveUnderAppPrefixOnly` both name `register` now, by walking the real table.
 */
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { accessToken } from '../../auth/accessToken';
import { APP_BASENAME, appRoutes, AUTHENTICATED_ROUTE_PATHS, PUBLIC_ROUTE_PATHS,
  REGISTER_PATH } from '../../app/routes';
import { en } from '../../i18n/catalogue';
import { createI18n } from '../../i18n/i18n';
import { ADVISORY_MAXIMUM_CODE_POINTS, ADVISORY_MINIMUM_CODE_POINTS, countCodePoints } from './passwordLength';
import { RegisterScreen } from './RegisterScreen';

// Lets act() flush React 19 renders synchronously - the shape eleven of the mounting suites use, and task
// 174's phase 11 recorded why it is written in every one of them rather than relied on from a neighbour.
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// This file is frontend/src/features/account/<name>; four levels up is the repository root (T05's technique,
// used because jsdom gives `import.meta.url` a non-file URL).
const srcDir = dirname(dirname(dirname(expect.getState().testPath as string)));
const read = (relativePath: string) => readFile(join(srcDir, relativePath), 'utf8');

type Reply = { status: number; headers?: Record<string, string>; body?: string };
type Sent = { method: string; path: string; auth: string | null; body: string };

/** Browser-style relative URL resolution: Node's Request rejects relative URLs, a browser resolves them (T05). */
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

/** T05's fake transport, narrowed to what this file needs: every request recorded, every reply the test's. */
function installTransport(respond: (request: Request) => Reply | Promise<Reply>): Sent[] {
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

/** Lets scheduled microtasks and promise chains run, which is what `openapi-fetch` needs between send and read. */
const settle = () => act(async () => {
  await new Promise<void>((resolve) => setTimeout(resolve, 0));
});

let mounted: { root: Root; container: HTMLElement; router: ReturnType<typeof createMemoryRouter> } | undefined;

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
  mounted = { root, container, router };
  return container;
}

const emailInput = (container: HTMLElement) => container.querySelector<HTMLInputElement>('input[type="email"]')!;
const passwordInput = (container: HTMLElement) =>
  container.querySelector<HTMLInputElement>('input[type="password"]')!;
const submitControl = (container: HTMLElement) =>
  container.querySelector<HTMLButtonElement>('button[type="submit"]')!;
const statusRegion = (container: HTMLElement) => container.querySelector<HTMLElement>('[role="status"]')!;

/**
 * Types into a CONTROLLED input the way React can see.
 *
 * `input.value = x` alone is invisible to React: it tracks the previous value on the node and skips the
 * change event when its own tracker already holds the new one. Calling the prototype's setter through
 * `Object.getOwnPropertyDescriptor` is what updates the node without touching React's tracker, which is why
 * every testing library does it this way. Measured in phase 6 - the plain assignment left `email` at '' and
 * the body reached the transport empty.
 */
async function type(input: HTMLInputElement, value: string): Promise<void> {
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
  // ASSERTED RATHER THAN OPTIONAL (phase 7, F7; phase 8, I11). `setter?.call` silently did nothing if the
  // descriptor ever lacked a setter, and the failure would have surfaced as an empty request body three
  // cases away from the cause.
  expect(setter, 'HTMLInputElement has no value setter, so nothing below types anything').toBeTypeOf('function');
  await act(async () => {
    setter?.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

/** Fills both fields and clicks the submit control. Returns the submit event seen by the form, if any. */
async function fillAndSubmit(container: HTMLElement, email: string, password: string): Promise<Event | undefined> {
  await type(emailInput(container), email);
  await type(passwordInput(container), password);
  let submitEvent: Event | undefined;
  container.querySelector('form')?.addEventListener('submit', (event) => {
    submitEvent = event;
  });
  await act(async () => {
    submitControl(container).click();
  });
  return submitEvent;
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
// AC1 — the form renders, and every control is named
// ==========================================================================================================

describe('AC1 the registration form renders a named email field, password field and submit control', () => {
  it('renders exactly one form holding one email input, one password input and one submit control', async () => {
    const container = await renderScreen();

    expect(container.querySelectorAll('form'), 'the screen renders no form, or more than one').toHaveLength(1);
    expect(container.querySelectorAll('input'), 'the screen renders a third input').toHaveLength(2);
    expect(container.querySelectorAll('input[type="email"]')).toHaveLength(1);
    expect(container.querySelectorAll('input[type="password"]')).toHaveLength(1);
    expect(container.querySelectorAll('button')).toHaveLength(1);
    // And all three are INSIDE the form: a submit control outside it submits nothing, and a field outside it
    // is not sent. `contains` rather than a selector, so a wrapper added later does not break the claim.
    const form = container.querySelector('form')!;
    for (const control of [emailInput(container), passwordInput(container), submitControl(container)]) {
      expect(form.contains(control), 'a control sits outside the form, so submitting cannot carry it').toBe(true);
    }
  });

  it('gives each field a programmatic label, not a placeholder and not an adjacent word', async () => {
    const container = await renderScreen();

    for (const [input, copy] of [
      [emailInput(container), en.register.emailLabel],
      [passwordInput(container), en.register.passwordLabel],
    ] as const) {
      // `labels` is the DOM's own answer to "what names this control", and it is populated only by a real
      // `<label for>` association or by an ancestor label. A `<p>` beside the input, or a placeholder, gives
      // an empty list - which is exactly the mistake this case exists to fail on.
      expect([...input.labels ?? []].map((label) => label.textContent),
        'the field has no programmatic label, so a screen reader announces it unnamed').toEqual([copy]);
      expect(input.id, 'the field has no id, so nothing can point a label at it').not.toBe('');
      expect(input.labels?.[0]?.getAttribute('for'), 'the label points somewhere else').toBe(input.id);
      expect(input.getAttribute('placeholder'), 'the field uses a placeholder as its name').toBeNull();
    }
  });

  it('names the submit control with the catalogue word and types it as a submit', async () => {
    const container = await renderScreen();
    const control = submitControl(container);

    expect(control.textContent).toBe(en.register.submit);
    expect(control.getAttribute('type'), 'the control is not a submit, so Enter in a field does nothing')
      .toBe('submit');
  });
});

// ==========================================================================================================
// AC2 / AC3 / AC4 — the advisory range, as a table: task 43 wrote the 12 end, task 44 added the 128 end
// ==========================================================================================================

/**
 * One row per password this task can speak for.
 *
 * TASK 44 ADDED ROWS AND NOTHING ELSE, which is the whole reason the length cases are a table: the 12 end
 * and the 128 end are the same claim measured at two boundaries, and a copied case is where the two would
 * drift apart. This paragraph named two rows - `'a'.repeat(128)` in range and `'a'.repeat(129)` out of it -
 * and THREE landed. The third is astral, because at this end the counting bug rejects rather than accepts,
 * and nobody had looked at which way it points at 128 when the prediction was written.
 */
const PASSWORDS: ReadonlyArray<{ name: string; password: string; codePoints: number; inRange: boolean }> = [
  { name: '12 ASCII characters, the lower bound itself', password: 'a'.repeat(12), codePoints: 12, inRange: true },
  { name: '13 ASCII characters, just inside', password: 'a'.repeat(13), codePoints: 13, inRange: true },
  // 12 code points, 24 UTF-16 code units: the case `password.length` gets WRONG, and the reason R1 says code
  // points. A check written with `.length` would call this 24 and accept it for the wrong reason - and would
  // call a 7-emoji password 14 and accept that too, which is the failure that matters.
  { name: '12 astral code points, which are 24 UTF-16 units', password: '👍'.repeat(12), codePoints: 12, inRange: true },
  // TASK 44, THE UPPER BOUND, and the two rows below are what the header above promised - plus one it did
  // not name. The header predicted `'a'.repeat(128)` and `'a'.repeat(129)`; the astral row is the third,
  // because it is the only row that fails in the direction this END of the range fails in.
  { name: '128 ASCII characters, the upper bound itself', password: 'a'.repeat(128), codePoints: 128, inRange: true },
  // 128 code points, 256 UTF-16 units - and the SAME `.length` bug as the 12-astral row above, pointing the
  // other way. At 12 the over-count pulls a short password INTO range and accepts what R1 excludes; at 128
  // it pushes a valid password ABOVE the maximum, so `.length` reads 256 > 128 and marks a passphrase of
  // emoji invalid. A false reject is the worse half: the visitor cannot see what the client counted.
  // (The factor is two - one astral character is one code point and two units, 24 for the row above and 256
  // for this one. Phases 0 to 3 all said 512 until phase 4 measured it.)
  { name: '128 astral code points, which are 256 UTF-16 units', password: '👍'.repeat(128), codePoints: 128, inRange: true },
  { name: '11 ASCII characters, one short', password: 'a'.repeat(11), codePoints: 11, inRange: false },
  { name: '7 emoji, which `.length` would call 14', password: '👍'.repeat(7), codePoints: 7, inRange: false },
  // One past the maximum, and MARKED ONLY - no sentence explains it, exactly as the 11-character row above
  // explains nothing. Task 45 owns the advisory guidance for 11 and 129 and should choose its own words.
  // Without this row the claim "128 is the maximum" is unfalsifiable: a bound of `<= 999` passes every
  // other row in this table.
  { name: '129 ASCII characters, one past the upper bound', password: 'a'.repeat(129), codePoints: 129, inRange: false },
  // D16's clause, and it was unmeasured until phase 10 (phase 7 F5, phase 8 I10): "the empty string must
  // not crash the count". Both fields empty is the most ordinary accidental submit there is, and the row
  // asserts what every other row asserts - the request still goes, because R1's check is advisory and the
  // server is the authority on an empty password as much as on a short one.
  { name: 'the empty string, which must not crash the count', password: '', codePoints: 0, inRange: false },
];

describe('AC2/AC3/AC4 the advisory range marks and never blocks', () => {
  it.each(PASSWORDS)('sends $name to the server either way', async ({ password, codePoints, inRange }) => {
    const sent = installTransport(() => ACCEPTED);
    const container = await renderScreen();

    await fillAndSubmit(container, 'visitor@example.test', password);
    await settle();

    // THE CLAIM WITH TEETH, and it is the same for every row: R1 makes the check advisory, so the request
    // goes out whether the password is 11 code points or 12. A blocking check would pass every other case in
    // this file and fail only this one.
    expect(sent, 'the advisory check blocked the submit, which R1 forbids').toHaveLength(1);
    expect(JSON.parse(sent[0]!.body) as { password: string }, 'the password was altered on the way out')
      .toEqual({ email: 'visitor@example.test', password });
    expect(countCodePoints(password), 'the counter disagrees with the row').toBe(codePoints);
    // Nothing is disabled at any point - not the control, not the fields. A disabled control is the other
    // way a check blocks, and it is invisible to a request count taken after the fact.
    expect(submitControl(container).hasAttribute('disabled')).toBe(false);
    expect(emailInput(container).hasAttribute('disabled')).toBe(false);
    expect(passwordInput(container).hasAttribute('disabled')).toBe(false);
    // The ONE thing the screen does with the count (task 43's own choice, beyond D16, recorded in phase 6's
    // notes): out of range marks the field invalid. No sentence explains it - task 45 owns that copy, and it
    // should choose its own words rather than inherit the words of the task that wrote the check.
    expect(passwordInput(container).getAttribute('aria-invalid'),
      `the advisory mark disagrees with the row (${codePoints} code points)`).toBe(inRange ? null : 'true');
  });

  it('counts astral code points the way R1 says, which `String.length` does not', async () => {
    const container = await renderScreen();
    await type(passwordInput(container), '👍'.repeat(12));

    // The DOM's own value is the evidence that the two numbers really differ in the browser, rather than
    // only in a unit test of `countCodePoints`: 24 UTF-16 units, 12 code points, the same string.
    expect(passwordInput(container).value.length, 'jsdom stopped measuring UTF-16 units').toBe(24);
    expect(countCodePoints(passwordInput(container).value)).toBe(12);
    expect(ADVISORY_MINIMUM_CODE_POINTS).toBe(12);
    expect(ADVISORY_MAXIMUM_CODE_POINTS).toBe(128);
  });

  it('T44 AC2 — 128 emoji are 256 UTF-16 units in the DOM and 128 code points to the check', async () => {
    // THE MIRROR OF THE CASE ABOVE, AT THE OTHER END - the direction task 43 had no way to measure. The
    // table row asserts the outcome; this case asserts the two NUMBERS that produce it, through the DOM's
    // own value rather than through a unit test of `countCodePoints`, so that the disagreement between the
    // units is shown where a browser would show it.
    //
    // NOT TASK 46'S WORK DONE EARLY: task 46 owns the claim that the counter counts code points in general.
    // What this case owns is that the SCREEN accepts a password at the upper bound when the two units
    // disagree about whether it is there.
    const sent = installTransport(() => ACCEPTED);
    const container = await renderScreen();

    await fillAndSubmit(container, 'visitor@example.test', '👍'.repeat(128));
    await settle();

    expect(passwordInput(container).value.length, 'jsdom stopped measuring UTF-16 units').toBe(256);
    expect(countCodePoints(passwordInput(container).value)).toBe(128);
    // Accepted: not marked, and sent unaltered. Both are what `.length` would break here, and neither is
    // broken by `.length` at the lower bound - which is why one end could not stand in for the other.
    expect(passwordInput(container).getAttribute('aria-invalid'),
      'a valid 128-code-point password was marked invalid').toBeNull();
    expect(JSON.parse(sent[0]!.body) as { email: string; password: string }).toEqual({
      email: 'visitor@example.test',
      password: '👍'.repeat(128),
    });
  });

  it('T44 AC4 — neither field caps its own length, which would count the wrong unit and truncate', async () => {
    // `maxLength` IS COUNTED IN UTF-16 UNITS. A `maxLength={128}` on the password field would cut the row
    // above in half - 64 emoji - and the advisory check would then count a value the visitor never typed
    // and find it SHORT. It would also be a BLOCK rather than a mark, which is the one thing R1 forbids the
    // client to be.
    //
    // THIS NEGATIVE CANNOT BE PROVED BY THE BODY-EQUALITY ASSERTIONS ABOVE, which is why it is its own
    // case: `maxLength` constrains USER EDITING only, and every case in this file sets the value through
    // `HTMLInputElement.prototype`'s setter, which is not an edit. Measured at the freeze - with
    // `maxlength=128` on the element, a 256-unit value went in and 256 units came back, untouched.
    //
    // ITS POSITIVE CONTROL IS THE HARNESS: the `maxLength={128}` mutant must die. If this case and the
    // source scan in `screensRenderThroughDesignSystemPrimitivesOnly` both survive it, both are vacuous.
    const container = await renderScreen();

    for (const field of [passwordInput(container), emailInput(container)]) {
      expect(field.hasAttribute('maxlength'), 'a field caps its length, in UTF-16 units').toBe(false);
      expect(field.hasAttribute('minlength'), 'a field floors its length, in UTF-16 units').toBe(false);
    }
  });

  it('submits with BOTH fields empty, because the server is the authority on an empty address too', async () => {
    // PHASE 11, F7. The table's empty row covers an empty PASSWORD with a real address; this is the
    // accident - a visitor pressing the button on an untouched form. R1 puts the authority on the server
    // for both fields, and `noValidate` (D13) is what stops the browser from inventing a rejection of its
    // own, so the request goes exactly as it does for an 11-code-point password.
    const sent = installTransport(() => ACCEPTED);
    const container = await renderScreen();

    await act(async () => {
      submitControl(container).click();
    });
    await settle();

    expect(sent, 'an untouched form was blocked in the client').toHaveLength(1);
    expect(JSON.parse(sent[0]!.body)).toEqual({ email: '', password: '' });
    expect(passwordInput(container).getAttribute('aria-invalid'), 'zero code points is out of range')
      .toBe('true');
    // The email field is NOT marked: R1's advisory bound is the password's, and nothing in the client
    // judges an address (phase 7, F8). A mark here would be a verdict no requirement grants.
    expect(emailInput(container).getAttribute('aria-invalid')).toBeNull();
  });

  it('counts a total function: empty, astral, and a lone surrogate all return a number (D16)', () => {
    // `passwordLength.ts`'s header claims this and nothing asserted it. R1 makes the check ADVISORY, so a
    // count that could throw would be a client-side gate - which is the one thing R1 forbids the client to
    // be. A lone surrogate is included because the spread iterates CODE POINTS and a half pair is one.
    //
    // NOT TASK 46'S WORK DONE EARLY, and the line between them is worth naming because every other
    // boundary in this package is. Task 46 (`passwordLengthCountsCodePoints`) owns the claim that length IS
    // counted in code points, in its own suite. What this case owns is TOTALITY - that the count returns a
    // number for every input this screen can hand it - which task 43 needs because D16 froze it and because
    // an advisory check that throws stops being advisory. The 128 assertion is a count, not an acceptance:
    // task 44 owns whether a 128-code-point password is accepted, and adds a row to the table above for it.
    expect(countCodePoints('')).toBe(0);
    expect(countCodePoints('\uD83D')).toBe(1);
    expect(countCodePoints('a\uD83Db')).toBe(3);
    expect(countCodePoints('👍')).toBe(1);
    expect(countCodePoints('👍'.repeat(128))).toBe(128);
  });

  it('marks nothing before the first submit, because the check is a submit-time one', async () => {
    const container = await renderScreen();
    await type(passwordInput(container), 'a'.repeat(3));

    // Typing is not submitting. A field marked invalid while a visitor is three characters into a password
    // is the kind of help that reads as a rejection, and R1 asks for the check on submit.
    expect(passwordInput(container).getAttribute('aria-invalid')).toBeNull();
  });

  it('CONTROL: the table measures something — the two ends disagree about the mark', () => {
    // An `it.each` over a table whose rows all expect the same outcome measures the row count and nothing
    // else. These two assertions are what make the `inRange` column load-bearing.
    expect(PASSWORDS.some((row) => row.inRange), 'no row is in range').toBe(true);
    expect(PASSWORDS.some((row) => !row.inRange), 'no row is out of range').toBe(true);
    expect(PASSWORDS.filter((row) => row.codePoints === ADVISORY_MINIMUM_CODE_POINTS),
      'no row sits ON the lower bound, which is the one this task is named for').not.toEqual([]);
    // TASK 44 PINS THE OTHER END, and the asymmetry was the hole: a control that names only the minimum
    // cannot see a maximum silently widened to 999, because every row under 128 passes under either number.
    // The three assertions together are what make `inRange` measure R1's RANGE rather than R1's floor.
    expect(PASSWORDS.filter((row) => row.codePoints === ADVISORY_MAXIMUM_CODE_POINTS),
      'no row sits ON the upper bound, which is the one task 44 is named for').not.toEqual([]);
    expect(PASSWORDS.some((row) => row.codePoints > ADVISORY_MAXIMUM_CODE_POINTS),
      'no row is above the maximum, so a widened maximum would pass unseen').toBe(true);
  });
});

// ==========================================================================================================
// AC5 — one request, through the generated client, with no bearer
// ==========================================================================================================

describe('AC5 the submit reaches POST /accounts through the generated client and carries no token', () => {
  it('sends exactly one POST to /accounts, as JSON, with no Authorization header', async () => {
    const sent = installTransport(() => ACCEPTED);
    const container = await renderScreen();

    await fillAndSubmit(container, 'visitor@example.test', 'a'.repeat(12));
    await settle();

    expect(sent).toHaveLength(1);
    expect(sent[0]!.method).toBe('POST');
    expect(sent[0]!.path).toBe('/accounts');
    // NO BEARER, and this is the half that makes `createPublicAuthApi` more than a convenience: a
    // registration request carrying somebody's access token would send a credential to a public endpoint.
    expect(sent[0]!.auth, 'the registration request carried an Authorization header').toBeNull();
    expect(accessToken.current(), 'the screen put a token in memory').toBeNull();
  });

  it('builds no URL and calls no transport of its own', async () => {
    const source = await read('features/account/RegisterScreen.tsx');
    const code = source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');

    // L6's rule, measured on this screen specifically. The lint script enforces it across the tree; this is
    // the case that fails in THIS task's suite when THIS screen is the file that broke it.
    // `/['"`]\/accounts/` WAS IN THIS LIST AND WAS WRONG IN BOTH DIRECTIONS (measured, phase 6). It forbade
    // the one call the next assertion REQUIRES - `.POST('/accounts')` - because the generated client takes
    // the path as a typed literal, which is the correct shape and not a hand-written URL at all. A mask that
    // forbids the right answer is worse than no mask: it would have been "fixed" by deleting the case.
    for (const shape of [/\bfetch\s*\(/, /XMLHttpRequest/, /axios/, /new URL\s*\(/]) {
      expect(code, `the screen reaches the network itself: ${String(shape)}`).not.toMatch(shape);
    }
    expect(code, 'the screen no longer goes through the generated client').toMatch(/createPublicAuthApi\(\)/);
    expect(code, 'the screen calls the operation by a path the generated types do not check')
      .toMatch(/\.POST\('\/accounts'/);
  });

  it('CONTROL: the source scan fails on a screen that fetches by hand', () => {
    const planted = "export function X() { void fetch('/accounts', { method: 'POST' }); }";
    expect(planted, 'the scan cannot see a hand-written fetch, so the case above is vacuous')
      .toMatch(/\bfetch\s*\(/);
    expect(planted).toMatch(/['"`]\/accounts/);
  });
});

// ==========================================================================================================
// AC6 — every word comes from the catalogue
// ==========================================================================================================

describe('AC6 the screen says what the catalogue says, and never a key', () => {
  it('renders the heading, both labels and the control from the catalogue, as literal English', async () => {
    const text = (await renderScreen()).textContent ?? '';

    for (const [copy, literal] of [
      [en.register.heading, 'Create your account'],
      [en.register.emailLabel, 'Email address'],
      [en.register.passwordLabel, 'Password'],
      [en.register.submit, 'Create account'],
    ] as const) {
      expect(text, 'the screen does not render the catalogue entry').toContain(copy);
      // BOTH SIDES, the shape every copy case in this package uses: comparing the DOM with the catalogue
      // alone would pass while both said the wrong thing.
      expect(copy, 'the catalogue was reworded without this suite noticing').toBe(literal);
    }
    expect(text, 'a key leaked into the DOM, so the catalogue lookup failed').not.toMatch(/register\./);
  });

  it('reaches the catalogue for every word it renders, which the DOM cannot tell from a literal', async () => {
    const code = (await read('features/account/RegisterScreen.tsx'))
      .replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');

    // THE HOLE THE HARNESS FOUND (TR09, a survivor). Hardcoded English is BYTE-FOR-BYTE what the catalogue
    // holds, so every assertion above passes against a screen that spells its own words: comparing the DOM
    // with the catalogue cannot distinguish a lookup from a literal. Only the source can, and it is
    // asserted in both directions - every key is reached, and no sentence is spelled.
    for (const key of ['register.heading', 'register.emailLabel', 'register.passwordLabel',
      'register.submit', 'register.submitting', 'register.success']) {
      expect(code, `the screen does not reach the catalogue for ${key}`).toContain(`'${key}'`);
    }
    // A WORD-BOUNDARY MATCH, NOT A SUBSTRING, AND THE TRAP BIT A SECOND TIME TO TEACH IT. Written as a
    // plain `toContain` this failed instantly: 'Password' is the catalogue's one single-word string and it
    // is a substring of `PasswordField` in this screen's own import. T173's copy scan was corrected for
    // exactly this in phase 6 - the same mistake, in a different file, because the matcher lives in two
    // suites and not in a shared module (FU-T43-2). A sentence is SPELLED in a string literal or as JSX
    // text; inside an identifier it is a name.
    for (const sentence of Object.values(en.register)) {
      const escaped = sentence.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      expect(code, `the screen spells "${sentence}" instead of looking it up`)
        .not.toMatch(new RegExp(`(?<![A-Za-z0-9_$])${escaped}(?![A-Za-z0-9_$])`));
    }
  });

  it('renders the success sentence from the catalogue after the submit, as literal English', async () => {
    installTransport(() => ACCEPTED);
    const container = await renderScreen();

    await fillAndSubmit(container, 'visitor@example.test', 'a'.repeat(12));
    await settle();

    expect(statusRegion(container).textContent).toBe(en.register.success);
    expect(en.register.success).toBe('Check your email to finish creating your account.');
    // R2's words, where this suite can see them: the sentence says to check the mail and says nothing about
    // whether the address was already registered. Task 47's acknowledgement screen owns the full claim.
    expect(en.register.success.toLowerCase(), 'the success copy distinguishes an existing account')
      .not.toMatch(/already|exists|taken|new account/);
  });
});

// ==========================================================================================================
// AC7 — the screen renders through the design system, and the lists say so
// ==========================================================================================================

describe('AC7 the screen renders no element of its own, and the pinned lists name what it added', () => {
  it('bears no lowercase JSX tag', async () => {
    const source = await read('features/account/RegisterScreen.tsx');
    const code = source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');
    const tags = [...code.matchAll(/(?<![A-Za-z0-9_$])<([a-z][a-zA-Z0-9-]*)(?=[\s/>])/g)].map((m) => m[1]);

    // §6e(3). The screen composes `Form`, the two fields and `SubmitButton`; a `<div>` for layout here is
    // the first step back to a screen that styles itself.
    expect(tags, `the screen renders ${tags.join(', ')} itself instead of a primitive`).toEqual([]);
  });

  it('is named in the render-site list, and the four primitives in the primitive list (task 174\'s shape)', async () => {
    const only = await read('app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx');

    expect(only, 'the render-site list does not name this screen')
      .toContain("'features/account/RegisterScreen.tsx',");
    for (const primitive of ['EmailField', 'Form', 'PasswordField', 'SubmitButton']) {
      expect(only, `the primitive list does not name ${primitive}`).toContain(`'${primitive}',`);
    }
    expect(only, 'the render-site count still says seven').toMatch(/toHaveLength\(8\)/);
    expect(only, 'the primitive count still says nine').toMatch(/toHaveLength\(13\)/);
    expect(only, 'the union pin no longer says twenty-one').toMatch(/\.toBe\(21\)/);
  });

  it('is named in the credential table that task 16 built, with the reason', async () => {
    const scan = await read('auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx');

    // THE INSTRUMENT NO DOCUMENT NAMED. T16's `<form>` pattern is "broad ON PURPOSE ... a production form is
    // a thing somebody should have to justify in this table rather than add quietly". Task 43 is the task
    // that paid it, and this case is what makes the payment permanent: delete the rows and this fails.
    expect(scan, 'the credential table does not name the form primitive').toContain("file: 'app/ui/Form.tsx',");
    expect(scan, 'the credential table does not name the password primitive')
      .toContain("file: 'app/ui/PasswordField.tsx',");
    expect(scan, 'the credential table does not name this screen')
      .toContain("file: 'features/account/RegisterScreen.tsx',");
  });
});

// ==========================================================================================================
// AC8 — a public screen: no session, no new path
// ==========================================================================================================

describe('AC8 the screen is public and declares no new route', () => {
  it('renders with no session and reads no token', async () => {
    const container = await renderScreen();

    expect(accessToken.current(), 'a token exists before the screen even asks for one').toBeNull();
    expect(container.querySelector('form'), 'the screen rendered nothing without a session').not.toBeNull();

    const code = (await read('features/account/RegisterScreen.tsx'))
      .replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');
    // It imports nothing from `auth/` AT ALL, which is stronger than "does not read a token" and is the
    // reason the screen lives in `features/account/` rather than beside the three OIDC screens.
    expect(code, 'the registration screen reaches into the auth flow').not.toMatch(/from '\.\.\/\.\.\/auth\//);
  });

  it('arrives at /app/register through the REAL route table, with no session (phase 8, I2)', async () => {
    // EVERY OTHER CASE IN THIS FILE MOUNTS THE SCREEN UNDER A PROBE ROUTE, which measures the screen and
    // not the journey to it. `routesResolveUnderAppPrefixOnly` resolves this path with `matchRoutes` -
    // resolution, not rendering - and `publicRouteSetIsExactlyTheDeclaredSet` walks the table for an
    // element. Neither RENDERS it, and T41 and T42 both proved their composition this way.
    const container = document.createElement('div');
    document.body.append(container);
    const router = createMemoryRouter(appRoutes, {
      basename: APP_BASENAME,
      initialEntries: [`${APP_BASENAME}/${REGISTER_PATH}`],
    });
    const root = createRoot(container);
    await act(async () => {
      root.render(
        <I18nextProvider i18n={createI18n()}>
          <RouterProvider router={router} />
        </I18nextProvider>,
      );
    });
    mounted = { root, container, router };

    // THE CLAIM IS THAT IT IS OUTSIDE THE GATE, and no token was retained before this mount. A screen
    // attached inside `RequireSession` would have redirected to sign-in instead of rendering a form.
    expect(accessToken.current(), 'a token was present, so this proves nothing about a public path').toBeNull();
    expect(container.querySelector('form'), 'the real table did not render the registration form').not.toBeNull();
    expect(container.textContent).toContain(en.register.heading);
    expect(router.state.location.pathname, 'the gate redirected a visitor with no session')
      .toBe(`${APP_BASENAME}/${REGISTER_PATH}`);

    // AND OUTSIDE THE LAYOUT, NOT ONLY OUTSIDE THE GATE (phase 11, F5). `AuthenticatedLayout` renders the
    // `Banner` - a `<header>` holding a sign-out control - and it sits INSIDE `RequireSession`, so a
    // registration route that had drifted under either would hand a visitor with no session a control that
    // ends one. The form rendering proves the gate let it through; this proves the chrome did not come with
    // it, and the two together are what "public path" means in this route table.
    expect(container.querySelector('header'), 'the authenticated chrome rendered on a public path').toBeNull();
    expect(container.textContent, 'the sign-out control reached a visitor with no session')
      .not.toContain(en.shell.signOut);
  });

  it('changes neither declared path set: `register` was already there, with no element', () => {
    // R1 needed no new route. `register` and `register/sent` have been declared since task 16 and carried no
    // element; this task attached one to the first of them. A task that ADDED a path would be changing the
    // set `publicRouteSetIsExactlyTheDeclaredSet` exists to pin, which is a different and larger claim.
    expect(PUBLIC_ROUTE_PATHS).toEqual(['sign-in', 'callback', 'register', 'register/sent', 'verify',
      'resend-verification', 'reset/request', 'reset', 'signed-out']);
    expect(REGISTER_PATH, 'the published constant and the declared path have drifted apart').toBe('register');
    expect(PUBLIC_ROUTE_PATHS).toContain(REGISTER_PATH);
    expect(AUTHENTICATED_ROUTE_PATHS, 'a registration screen changed the authenticated set').toEqual([
      'home', 'account', 'account/sessions', 'account/mfa', 'api-keys', 'admin', 'invoices', 'invoices/:id',
      'payments', 'notifications', 'receipts/:id']);
    // `register/sent` still carries no element: task 47 owns the acknowledgement, and D1 is why this screen
    // announces its result in place instead of navigating to a blank document.
    expect(PUBLIC_ROUTE_PATHS).toContain('register/sent');
  });
});

// ==========================================================================================================
// AC9 — reachable by keyboard
// ==========================================================================================================

describe('AC9 every control is reachable by keyboard, and the control is a real submit', () => {
  it('puts no control out of the tab order', async () => {
    const container = await renderScreen();

    for (const control of [emailInput(container), passwordInput(container), submitControl(container)]) {
      // A NEGATIVE tabindex is the one that removes an element from sequential navigation. Absent is
      // correct for a natively focusable control; `0` would be redundant but harmless.
      const tabIndex = control.getAttribute('tabindex');
      expect(tabIndex === null || Number(tabIndex) >= 0,
        `${control.tagName} is out of the tab order, so a keyboard visitor cannot reach it`).toBe(true);
      await act(async () => control.focus());
      expect(document.activeElement, `${control.tagName} cannot take focus`).toBe(control);
    }
  });

  it('submits through the control, and the form prevents the document navigation', async () => {
    installTransport(() => ACCEPTED);
    const container = await renderScreen();

    const submitEvent = await fillAndSubmit(container, 'visitor@example.test', 'a'.repeat(12));

    // The CAUSE of Enter-to-submit, measured as far as jsdom allows: the click produced a real `submit`
    // event on the form - which only a `type="submit"` control inside it can do - and the handler called
    // `preventDefault`, without which a browser leaves the SPA for a document request.
    expect(submitEvent, 'clicking the control did not submit the form').toBeDefined();
    expect(submitEvent?.type).toBe('submit');
    expect(submitEvent?.defaultPrevented,
      'the form did not prevent its default, so a browser would navigate away from the SPA').toBe(true);
  });

  it('keeps `preventDefault` inside the primitive, where no screen can forget it (D13)', async () => {
    const form = (await read('app/ui/Form.tsx')).replace(/\/\*[\s\S]*?\*\//g, '');

    // STRUCTURAL ON PURPOSE. The case above proves the default was prevented on THIS screen; this one proves
    // the next screen cannot get it wrong, because the call is in the primitive and not in a consumer.
    expect(form, 'the form primitive stopped preventing its default').toMatch(/preventDefault\(\)/);
    expect(form, 'the form primitive accepts no submit handler of its own').toMatch(/onSubmit/);
    const screen = await read('features/account/RegisterScreen.tsx');
    expect(screen, 'the screen prevents the default itself, which means a later screen can forget to')
      .not.toMatch(/preventDefault/);
  });
});

// ==========================================================================================================
// AC10 — nothing is stored, nothing is logged, nothing leaks the password
// ==========================================================================================================

describe('AC10 the journey writes no storage and leaves no password in the document', () => {
  it('writes to neither storage across the whole journey (L3, R18)', async () => {
    installTransport(() => ACCEPTED);
    // SPIED ON `Storage.prototype`, NOT ON THE TWO INSTANCES, AND THE CONTROL BELOW IS WHY (phase 7, F4;
    // phase 8, I9). `vi.spyOn(globalThis.localStorage, 'setItem')` **does not intercept in jsdom**: measured
    // with a probe, the instance spy saw 0 of 1 writes while a prototype spy saw 2 of 2 and
    // `mock.instances` told the two storages apart. So the first version of this case asserted that a spy
    // which never fires had not fired - vacuous in the exact shape its own control was written to catch,
    // and the control failed on its first run.
    //
    // AND IT IS ONE CASE, NOT A PATTERN. This comment first said the finding was "carried for the other
    // suites that do this"; a sweep of every `vi.spyOn` target in `src/` says there are none - 80 uses of
    // `Storage.prototype` across 18 files, 8 on `globalThis` (`fetch`, `setTimeout`, `setInterval`, all
    // plain writable properties), and exactly ONE on a storage instance: task 42's AC11, now fixed. So the
    // correct pattern was the norm all along and one case deviated from it, surviving two review phases
    // because its assertion was NEGATIVE - the one direction in which a dead instrument and a clean result
    // are indistinguishable. FU-T43-1 is closed.
    const writes = vi.spyOn(Storage.prototype, 'setItem');
    const wroteTo = (store: Storage) => writes.mock.instances.filter((seen) => seen === store).length;
    const container = await renderScreen();

    await fillAndSubmit(container, 'visitor@example.test', 'a'.repeat(12));
    await settle();

    expect(writes, 'the registration journey wrote to a web storage').not.toHaveBeenCalled();
    expect(Object.keys(globalThis.localStorage)).toEqual([]);
    expect(Object.keys(globalThis.sessionStorage)).toEqual([]);

    // THE CONTROL, in the same case so the two cannot drift apart (phase 7, F4; phase 8, I9). A spy
    // installed on the wrong object, or one that failed to intercept, reports exactly what a journey that
    // writes nothing reports. Every spy mistake in this package has had that shape, and the `Object.keys`
    // assertions above are weaker evidence than they look - they would miss a write followed by a remove.
    globalThis.localStorage.setItem('control', 'seen');
    globalThis.sessionStorage.setItem('control', 'seen');
    expect(writes, 'the spy is not intercepting, so the assertion above was vacuous')
      .toHaveBeenCalledWith('control', 'seen');
    // And it tells the two apart, which is what one prototype spy has to prove to replace two instance
    // spies: a write to one storage must not be reported as a write to the other.
    expect(wroteTo(globalThis.localStorage), 'the spy cannot attribute a write to localStorage').toBe(1);
    expect(wroteTo(globalThis.sessionStorage), 'the spy cannot attribute a write to sessionStorage').toBe(1);
  });

  it('leaves the password nowhere but the field that holds it, before or after the submit', async () => {
    installTransport(() => ACCEPTED);
    const container = await renderScreen();
    const password = 'correct horse battery';

    await fillAndSubmit(container, 'visitor@example.test', password);
    await settle();

    // THE CLAIM THIS CASE STARTED WITH WAS FALSE, and measurement is what said so. It asserted the password
    // was in no ATTRIBUTE of any element; React 19 mirrors a controlled input's value into the `value`
    // content attribute, so `form.outerHTML` DOES contain it. That is a real consequence for anything that
    // serializes the DOM - an error reporter, a session recorder, a snapshot test - and it belongs to the
    // password field, which is the one element that is allowed to hold what was typed. So the claim is the
    // one worth making instead: the password reaches NOTHING ELSE. Every other element, the status region
    // included, is checked, and so is the email field, which is the nearest place a copy could appear.
    const field = passwordInput(container);
    for (const element of container.querySelectorAll('*')) {
      if (element === field) continue;
      for (const attribute of element.attributes) {
        expect(attribute.value, `${element.tagName}'s ${attribute.name} carries the password`)
          .not.toContain(password);
      }
      expect(element.textContent ?? '', `${element.tagName} renders the password as text`)
        .not.toContain(password);
    }
    expect(emailInput(container).value, 'the password reached the email field').not.toContain(password);
    expect(statusRegion(container).textContent, 'the status region echoes what was typed')
      .not.toContain(password);
    // And the one element that does hold it holds it as a password field: its type is what keeps it off the
    // screen, and a field whose type changed would leak it visually with every assertion above still green.
    expect(field.getAttribute('type'), 'the field stopped masking what is typed in it').toBe('password');
  });

  it('reaches no console and no storage in its own source (D14)', async () => {
    const code = (await read('features/account/RegisterScreen.tsx'))
      .replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');

    for (const shape of [/console\./, /localStorage/, /sessionStorage/, /document\.cookie/]) {
      expect(code, `the screen reaches ${String(shape)}`).not.toMatch(shape);
    }
  });
});

// ==========================================================================================================
// AC11 — the handler does not branch on the response
// ==========================================================================================================

describe('AC11 the same result is rendered whatever the server answers (R2, L4)', () => {
  it.each([
    ['202 accepted', { status: 202, body: JSON.stringify({ status: 'pending_verification' }) }],
    ['409 conflict, which the server does not send and this screen would not read', { status: 409, body: '{}' }],
    ['500 with no body at all', { status: 500, body: '' }],
  ] as const)('renders the same success state for a %s', async (_name, reply) => {
    installTransport(() => reply as Reply);
    const container = await renderScreen();

    await fillAndSubmit(container, 'visitor@example.test', 'a'.repeat(12));
    await settle();

    // R2 and L4: the client adds no branching. The server answers 202 for a new address AND for one already
    // registered, so a screen that read the status would be building a distinction the server removed - and
    // the 500 row is the honest consequence, recorded rather than hidden: this screen reports success it
    // cannot know. OQ-T43-9 carries it, and task 45 owns the failure state.
    expect(statusRegion(container).textContent, 'the screen branched on the response').toBe(en.register.success);
    expect(container.textContent, 'a status code leaked into the copy').not.toMatch(/\b(409|500|202)\b/);
  });

  it('reads neither the body nor the status, and sets no timer', async () => {
    const code = (await read('features/account/RegisterScreen.tsx'))
      .replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');

    // STRUCTURAL, and it is the only honest way: a behavioural case cannot distinguish "did not read the
    // status" from "read it and did the same thing either way". The three rows above measure the OUTCOME;
    // this measures the shape that guarantees it.
    for (const shape of [/\.status\b/, /\bdata\b/, /\berror\b/, /setTimeout/, /setInterval/]) {
      expect(code, `the handler reaches ${String(shape)}, so it branches on or delays the response`)
        .not.toMatch(shape);
    }
  });
});

// ==========================================================================================================
// D1 / D7 — the status region is pre-mounted, filled, and the screen never navigates
// ==========================================================================================================

describe('the transport itself fails, which is not a response (phase 7 F2, phase 8 I7)', () => {
  it('returns the form to its idle state when the send rejects, and claims nothing', async () => {
    // `openapi-fetch` RESOLVES for every HTTP answer - which is why the three AC11 rows above all reach the
    // success branch - and REJECTS when the transport throws: offline, DNS failure, a blocked request. The
    // first version of this screen had no handler for that, so the rejection escaped and the live region
    // stayed on "Creating your account..." forever. This is the case that holds the fix.
    // NOTHING MAY ESCAPE, and the listener is Node's rather than the DOM's because that is the one that
    // fires (phase 11, F3; measured with a probe: `process.on('unhandledRejection')` saw the rejection
    // within two turns, `globalThis.addEventListener('unhandledrejection')` saw nothing in jsdom). The
    // original defect had TWO halves - a live region stuck on the in-flight copy, and a rejection nobody
    // handled - and the assertions below the journey only ever covered the first.
    const escaped: string[] = [];
    const collect = (reason: unknown) => void escaped.push(String(reason));
    process.on('unhandledRejection', collect);

    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))));
    const container = await renderScreen();

    await fillAndSubmit(container, 'visitor@example.test', 'a'.repeat(12));
    await settle();

    expect(escaped, 'the rejection escaped the handler, which is half of the original defect').toEqual([]);

    // NOTHING IS CLAIMED. Not success - no account was requested; and not a sentence, because D7 froze this
    // screen's key set at six and a failure word is an owner's decision (OQ-T43-9). The live region empties.
    expect(statusRegion(container).textContent, 'the screen reported an outcome it cannot know').toBe('');
    expect(container.textContent, 'a transport error leaked into the copy').not.toMatch(/fetch|error|failed/i);

    // AND THE FORM IS STILL USABLE, which is the only thing that can help when the request never left the
    // tab: both values are still there and the control is still live, so pressing it again retries.
    expect(emailInput(container).value).toBe('visitor@example.test');
    expect(passwordInput(container).value).toBe('a'.repeat(12));
    expect(submitControl(container).hasAttribute('disabled')).toBe(false);

    // The retry really is a retry: a second submit reaches the transport again.
    const sent = installTransport(() => ACCEPTED);
    await act(async () => {
      submitControl(container).click();
    });
    await settle();
    expect(sent, 'the form was left in a state that cannot submit again').toHaveLength(1);
    expect(statusRegion(container).textContent).toBe(en.register.success);
    expect(escaped, 'something escaped during the retry').toEqual([]);

    // THE CONTROL, in the same case so the two cannot drift apart. An empty list means nothing until the
    // collector is shown to collect: a listener registered on the wrong object, or one removed too early,
    // reports exactly what a handled rejection reports.
    void Promise.reject(new TypeError('control')).then(() => undefined);
    await settle();
    expect(escaped, 'the collector sees nothing, so the two assertions above were vacuous')
      .toEqual(['TypeError: control']);
    process.off('unhandledRejection', collect);
  });
});

describe('D1/D7 the result is announced in place, and the screen does not navigate', () => {
  it('mounts the live region before the submit, empty, so the fill is an announcement (D1)', async () => {
    installTransport(() => ACCEPTED);
    const container = await renderScreen();

    // THE ORDER IS THE CLAIM. A live region that appears WITH its text is not announced by most screen
    // readers: the region has to be in the accessibility tree before the content changes. `StatusRegion`'s
    // own header is where that rule is written; this is the case that holds a screen to it.
    const region = statusRegion(container);
    expect(region, 'the live region is created only after the submit').not.toBeNull();
    expect(region.textContent, 'the region starts with words in it').toBe('');
    expect(region.getAttribute('aria-live')).toBe('polite');
    expect(region.getAttribute('role')).toBe('status');

    await fillAndSubmit(container, 'visitor@example.test', 'a'.repeat(12));
    await settle();

    // THE SAME NODE, which is what makes it an announcement rather than a new region: a region replaced
    // between renders is a new one to the accessibility tree.
    expect(statusRegion(container), 'the live region was replaced rather than filled').toBe(region);
    expect(region.textContent).toBe(en.register.success);
  });

  it('shows the submitting word while the request is in flight, then the success one (D7)', async () => {
    let release!: () => void;
    const held = new Promise<void>((resolve) => {
      release = resolve;
    });
    installTransport(async () => {
      await held;
      return ACCEPTED;
    });
    const container = await renderScreen();

    await fillAndSubmit(container, 'visitor@example.test', 'a'.repeat(12));

    // MEASURED RATHER THAN MERELY WRITTEN. `register.submitting` is a catalogue key that only exists for
    // this window, and a deferred reply is the only way to stand inside it. Without this case the key could
    // be unreachable and every other case in the file would still pass.
    expect(statusRegion(container).textContent, 'the in-flight word is never shown, so the key is dead')
      .toBe(en.register.submitting);
    expect(en.register.submitting).toBe('Creating your account…');

    release();
    await settle();

    expect(statusRegion(container).textContent).toBe(en.register.success);
  });

  it('does not navigate: the location is still the registration path (D1, OQ-T43-5)', async () => {
    installTransport(() => ACCEPTED);
    const container = await renderScreen();

    await fillAndSubmit(container, 'visitor@example.test', 'a'.repeat(12));
    await settle();

    // `router.state.location.pathname`, NOT `useLocation().pathname` - T41's finding, and it cost production
    // code: the hook's value is basename-relative and the router's state is the one that carries the whole
    // path. Navigating to `register/sent` would hand the visitor a declared path with no element, which is
    // exactly what D1 chose against.
    expect(mounted?.router.state.location.pathname, 'the screen navigated away from the form')
      .toBe(`/${REGISTER_PATH}`);
    expect(container.querySelector('form'), 'the form was unmounted, so something navigated').not.toBeNull();
  });
});
