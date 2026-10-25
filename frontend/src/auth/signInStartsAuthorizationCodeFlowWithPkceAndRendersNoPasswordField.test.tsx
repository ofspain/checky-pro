/// <reference types="node" />
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { APP_BASENAME } from '../app/routes';
import { en } from '../i18n/catalogue';
import { createI18n } from '../i18n/i18n';
import { AUTHORIZE_ENDPOINT_PATH, AUTHORIZE_STORAGE_KEYS, buildAuthorizeRequest } from './authorizeRequest';
import { deriveCodeChallenge } from './pkce';
import { SignInScreen } from './SignInScreen';

/**
 * T16 - the first screen in the application. It renders no password field and starts the OIDC authorization code
 * flow with PKCE (R6).
 *
 * **T15 is authoritative for the request itself.** Its 50 cases own the eight parameters, the S256 method, the
 * three registered scopes and the three stored items. What this file owns is narrower: that the screen offers the
 * choice rather than taking it, that the URL reaching the browser is the one T15 built, and that no credential
 * input is rendered. The three parameter checks below are deliberate duplication - they demonstrate R6's own
 * words, "the authorization code flow with PKCE", on the screen that starts it - and they are not a second home
 * for the request shape.
 *
 * Two rules here could pass for the wrong reason, so each is paid for. "No password field" would be satisfied by
 * a screen that rendered nothing at all, so the same matchers are run against markup that does contain one.
 * "Every string comes from the catalogue" would be satisfied by a component with no strings, so the rendered text
 * is compared to the catalogue's values and the source is scanned for those same values as literals.
 */

// Lets act() flush React 19 renders synchronously in this test environment.
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const STUB_ORIGIN = 'https://checky.example';

/** The jsdom default, used where a case needs the real document location rather than a stub. */
const JSDOM_ORIGIN = 'http://localhost:3000';

let mounted: { root: Root; container: HTMLElement } | undefined;

/** Mounts the screen inside its own i18n provider, as mountShell does for the real application. */
function renderSignIn(): HTMLElement {
  const container = document.createElement('div');
  document.body.append(container);
  const root = createRoot(container);
  act(() => {
    root.render(
      <I18nextProvider i18n={createI18n()}>
        <SignInScreen />
      </I18nextProvider>,
    );
  });
  mounted = { root, container };
  return container;
}

/** Replaces location with a stub carrying an assign spy. `origin` is here because T15's module reads it. */
function stubLocation(origin = STUB_ORIGIN) {
  const assign = vi.fn();
  vi.stubGlobal('location', {
    origin,
    href: `${origin}/app/sign-in`,
    protocol: 'https:',
    host: 'checky.example',
    hostname: 'checky.example',
    pathname: '/app/sign-in',
    search: '',
    hash: '',
    assign,
    replace: vi.fn(),
  });
  return assign;
}

function button(container: HTMLElement): HTMLButtonElement {
  const found = container.querySelector('button');
  expect(found, 'the screen renders no button').not.toBeNull();
  return found as HTMLButtonElement;
}

function liveRegion(container: HTMLElement): HTMLElement {
  const found = container.querySelector('[role="status"]');
  expect(found, 'the screen renders no live region').not.toBeNull();
  return found as HTMLElement;
}

/** The credential-input shapes a sign-in screen must not have. Used on the real screen and on a decoy. */
const CREDENTIAL_SELECTORS = [
  'input[type=password]',
  'input',
  'textarea',
  'select',
  '[contenteditable]',
  'form',
] as const;

function credentialInputsIn(root: ParentNode): string[] {
  return CREDENTIAL_SELECTORS.filter((selector) => root.querySelector(selector) !== null);
}

/** One macrotask turn, inside act, so React commits anything the resolved promise caused. */
async function turn(): Promise<void> {
  await act(async () => {
    await new Promise((resolve) => {
      setTimeout(resolve, 0);
    });
  });
}

/**
 * Clicks, then waits for the handler to finish - which act alone does not do.
 *
 * Measured: the handler awaits a real SHA-256 through `crypto.subtle`, and that settles on a later macrotask than
 * act's microtask flush. Without these turns the assertions raced the navigation, and worse, the attempt resolved
 * during the NEXT case and called that case's assign stub: the same spy showed 0 calls in one run and 2 in
 * another. Draining here keeps each case's async work inside the case.
 */
async function clickAndSettle(target: HTMLButtonElement): Promise<void> {
  await act(async () => {
    target.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  });
  for (let index = 0; index < 5; index += 1) await turn();
}

describe('signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField', () => {
  let componentSource: string;

  beforeEach(async () => {
    globalThis.sessionStorage.clear();
    globalThis.localStorage.clear();
    const here = dirname(expect.getState().testPath as string);
    componentSource ??= await readFile(join(here, 'SignInScreen.tsx'), 'utf8');
  });

  afterEach(() => {
    if (mounted) {
      const { root, container } = mounted;
      act(() => root.unmount());
      container.remove();
      mounted = undefined;
    }
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    vi.resetModules();
    vi.doUnmock('./authorizeRequest');
  });

  describe('it renders no credential input (AC1)', () => {
    it('has no password field, no input of any kind, and no form', () => {
      const container = renderSignIn();
      expect(credentialInputsIn(container)).toEqual([]);
    });

    // Without this, a screen that rendered nothing at all would pass the case above. The decoy proves the
    // selectors find what they are looking for.
    it('and the same selectors find a password field when one is there (AC12)', () => {
      const decoy = document.createElement('div');
      decoy.innerHTML = '<form><input type="password" name="p" /></form>';
      // Containment, not equality: an exact list would couple this control to the order and membership of
      // CREDENTIAL_SELECTORS, so adding a selector would fail it for no real reason. The meaningful equality is
      // the empty one, on the real screen.
      const found = credentialInputsIn(decoy);
      expect(found).toContain('input[type=password]');
      expect(found).toContain('form');
    });

    // "No credential input" and "exactly one control, and it is the sign-in button" are different claims, and on
    // the one screen an unauthenticated visitor meets, the second is worth pinning: a stray second button or an
    // unexpected link would satisfy every other case in this file.
    it('offers exactly one interactive control, and it is the action', () => {
      const container = renderSignIn();
      const controls = container.querySelectorAll('button, a, input, select, textarea, [tabindex]');
      expect(controls).toHaveLength(1);
      expect(controls[0]).toBe(button(container));
    });

    it('renders no password-ish attribute anywhere in its own source either', () => {
      expect(componentSource).not.toMatch(/type\s*=\s*["']password["']/);
      expect(componentSource).not.toMatch(/<\s*input\b/);
      expect(componentSource).not.toMatch(/<\s*form\b/);
    });
  });

  describe('choosing sign-in starts the flow (AC2)', () => {
    it('navigates the whole page to the authorize URL', async () => {
      const assign = stubLocation();
      const container = renderSignIn();
      await clickAndSettle(button(container));

      expect(assign).toHaveBeenCalledTimes(1);
      const url = new URL(assign.mock.calls[0]?.[0] as string);
      expect(url.origin).toBe(STUB_ORIGIN);
      expect(url.pathname).toBe(AUTHORIZE_ENDPOINT_PATH);
    });

    // R6's own words, demonstrated on the screen that starts the flow. T15 owns the shape; these three are what
    // make this "the authorization code flow with PKCE" rather than some other request.
    it('asks for a code, with PKCE and S256, and exactly the three registered scopes', async () => {
      const assign = stubLocation();
      const container = renderSignIn();
      await clickAndSettle(button(container));

      const sent = new URL(assign.mock.calls[0]?.[0] as string).searchParams;
      expect(sent.get('response_type')).toBe('code');
      expect(sent.get('code_challenge_method')).toBe('S256');
      expect(sent.get('scope')).toBe('openid profile email');
      expect(sent.get('code_challenge')).not.toBeNull();
    });

    // The screen sends what T15 built, not something of its own: every parameter matches a directly-built request
    // except the three that are fresh per call by design.
    it('sends the request T15 builds, parameter for parameter', async () => {
      const assign = stubLocation();
      const container = renderSignIn();
      await clickAndSettle(button(container));
      const fromScreen = new URL(assign.mock.calls[0]?.[0] as string).searchParams;

      globalThis.sessionStorage.clear();
      const direct = new URL((await buildAuthorizeRequest()).url).searchParams;

      const perCall = new Set(['state', 'nonce', 'code_challenge']);
      expect([...fromScreen.keys()].sort()).toEqual([...direct.keys()].sort());
      for (const [key, value] of fromScreen) {
        if (perCall.has(key)) expect(value, key).not.toBe(direct.get(key));
        else expect(value, key).toBe(direct.get(key));
      }
    });
  });

  describe('it does nothing until the visitor chooses (AC3)', () => {
    it('makes no request, writes no storage and does not navigate on mount', () => {
      const assign = stubLocation();
      const setItem = vi.spyOn(Storage.prototype, 'setItem');

      renderSignIn();

      expect(assign).not.toHaveBeenCalled();
      expect(setItem).not.toHaveBeenCalled();
      expect(Object.keys(globalThis.sessionStorage)).toEqual([]);
    });
  });

  describe('the three L16 items (AC4)', () => {
    it('are in session storage after the action, and nothing else is', async () => {
      stubLocation();
      const container = renderSignIn();
      await clickAndSettle(button(container));

      expect(Object.keys(globalThis.sessionStorage).sort()).toEqual(
        Object.values(AUTHORIZE_STORAGE_KEYS).sort(),
      );
    });

    // Written by T15, not fabricated here: the stored verifier derives the challenge that went into the URL.
    // A fourth key, or a value this screen invented, could not satisfy this.
    it('hold the verifier belonging to the challenge that was sent', async () => {
      const assign = stubLocation();
      const container = renderSignIn();
      await clickAndSettle(button(container));

      const sent = new URL(assign.mock.calls[0]?.[0] as string).searchParams.get('code_challenge');
      const storedVerifier = globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.codeVerifier);
      expect(storedVerifier).not.toBeNull();
      await expect(deriveCodeChallenge(storedVerifier as string)).resolves.toBe(sent);
    });

    it('never reach the URL: the verifier is not in it', async () => {
      const assign = stubLocation();
      const container = renderSignIn();
      await clickAndSettle(button(container));

      const url = assign.mock.calls[0]?.[0] as string;
      const verifier = globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.codeVerifier) as string;
      expect(url.includes(verifier)).toBe(false);
    });
  });

  describe('every string comes from the catalogue (AC5)', () => {
    it('renders the catalogue values, not keys and not copy of its own', () => {
      const container = renderSignIn();
      const text = container.textContent ?? '';

      expect(text).toContain(en.signIn.heading);
      expect(text).toContain(en.signIn.explanation);
      expect(text).toContain(en.signIn.action);
      // A missing provider makes useTranslation return the key itself, which this catches.
      expect(text).not.toContain('signIn.');
    });

    // Inverted on purpose. Asserting "the source holds no string that appears on screen" cannot fail - every
    // component source is full of quoted strings. Reading the catalogue's values and forbidding them as literals
    // fails the moment someone inlines the real copy.
    it('holds none of the catalogue values as a literal in its own source', () => {
      // The input is asserted non-empty first. Without that, a scan that iterated nothing would pass - measured:
      // mutant M20 emptied this loop and the whole suite stayed green.
      const values = Object.values(en.signIn);
      expect(values.length).toBeGreaterThanOrEqual(5);
      expect(values.filter((value) => componentSource.includes(value))).toEqual([]);
    });

    /**
     * The wording is the owner's (OQ-1) and every other case here compares the screen to the catalogue, which
     * means the catalogue is compared with itself - mutant M18 changed "Sign in" to "Sign in now" and the whole
     * suite stayed green. That is correct for wording, which must be changeable in one file. It is NOT correct
     * for the properties the wording must have, which come from requirements and are pinned here.
     */
    it('says nothing about a password and discloses no account state (R6, L2, L4)', () => {
      const copy = Object.values(en.signIn).join(' ').toLowerCase();
      for (const forbidden of ['password', 'passcode', 'pin ', 'username', 'email address']) {
        expect(copy, forbidden).not.toContain(forbidden);
      }
      // Nothing that would differ between a known and an unknown account, or claim what the server will ask for.
      for (const forbidden of ['account exists', 'no such', 'not found', 'locked', 'suspended', 'incorrect']) {
        expect(copy, forbidden).not.toContain(forbidden);
      }
      // And the matcher has reach: it finds a phrase that would break the rule.
      expect(['Enter your password'].join(' ').toLowerCase()).toContain('password');
    });

    it('and that scan reports a source which does inline the copy (AC12)', () => {
      const inlined = `<h1>${en.signIn.heading}</h1>`;
      const found = Object.values(en.signIn).filter((value) => inlined.includes(value));
      expect(found).toEqual([en.signIn.heading]);
    });
  });

  describe('it is operable by keyboard (AC6)', () => {
    it('offers a real button with the catalogue label and no negative tab index', () => {
      const container = renderSignIn();
      const action = button(container);

      expect(action.tagName).toBe('BUTTON');
      expect(action.getAttribute('type')).toBe('button');
      expect(action.textContent).toBe(en.signIn.action);
      expect(action.tabIndex).toBeGreaterThanOrEqual(0);
    });

    // A focused element that becomes `disabled` drops the keyboard user to <body>, and this is the only control
    // on the screen. aria-disabled says "inert" without taking the focus away.
    it('is never the disabled attribute, before or during the attempt', async () => {
      const assign = stubLocation();
      const container = renderSignIn();
      const action = button(container);

      expect(action.disabled).toBe(false);
      // A string, not a boolean: React renders aria-* booleans as "true"/"false" attribute values.
      expect(action.getAttribute('aria-disabled')).toBe('false');

      // aria-busy as well as aria-disabled: "inert" and "working" are different things to announce, and the
      // attribute was added in phase 9 in response to a review finding and shipped untested - mutant M23 removed
      // it and the whole suite stayed green.
      expect(action.getAttribute('aria-busy')).toBe('false');

      await clickAndSettle(action);

      expect(assign).toHaveBeenCalledTimes(1);
      expect(button(container).disabled).toBe(false);
      expect(button(container).getAttribute('aria-disabled')).toBe('true');
      expect(button(container).getAttribute('aria-busy')).toBe('true');
      expect(componentSource).not.toMatch(/\sdisabled=/);
    });

    // The guard has to be synchronous. setStatus only schedules a re-render, so two clicks in one task would both
    // pass a state-based check, build two requests, overwrite each other's stored verifier and navigate twice.
    it('starts one flow even when clicked twice in the same task (AC6, D-1)', async () => {
      const assign = stubLocation();
      const container = renderSignIn();
      const action = button(container);

      await act(async () => {
        action.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        action.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      });
      for (let index = 0; index < 5; index += 1) await turn();

      expect(assign).toHaveBeenCalledTimes(1);
      expect(Object.keys(globalThis.sessionStorage).sort()).toEqual(
        Object.values(AUTHORIZE_STORAGE_KEYS).sort(),
      );
    });

    it('keeps focus on the button it was pressed with', async () => {
      stubLocation();
      const container = renderSignIn();
      const action = button(container);
      action.focus();
      expect(document.activeElement).toBe(action);

      await clickAndSettle(action);

      expect(document.activeElement).toBe(button(container));
    });
  });

  describe('structure and the live region (AC7)', () => {
    it('has one h1, a main landmark, and a polite status region from first paint', () => {
      const container = renderSignIn();

      const headings = container.querySelectorAll('h1');
      expect(headings).toHaveLength(1);
      expect(headings[0]?.textContent).toBe(en.signIn.heading);
      // Count, not existence: two main landmarks is a worse failure than none, and querySelector cannot tell
      // them apart. Same for the live region.
      expect(container.querySelectorAll('main')).toHaveLength(1);
      expect(container.querySelectorAll('[role="status"]')).toHaveLength(1);

      const region = liveRegion(container);
      expect(region.getAttribute('aria-live')).toBe('polite');
      expect(region.textContent).toBe('');
    });

    // Visual proximity is not association: a keyboard user tabbing straight to the button would otherwise never
    // hear that they are about to leave the application for a server-hosted page.
    it('associates the explanation with the action for assistive technology', () => {
      const container = renderSignIn();
      const describedBy = button(container).getAttribute('aria-describedby');
      expect(describedBy).not.toBeNull();
      const description = container.querySelector(`#${describedBy as string}`);
      expect(description?.textContent).toBe(en.signIn.explanation);
    });

    // Asserts the state is RENDERED, not that it is heard. In a real browser the document is replaced - measured
    // in phase 7, where the page became SAS's /login - so the window this occupies may be imperceptible. It still
    // earns its place: a slow or blocked navigation leaves it on screen, which is exactly when it is needed.
    it('renders the in-flight state while the browser is still navigating', async () => {
      stubLocation();
      const container = renderSignIn();
      await clickAndSettle(button(container));

      expect(liveRegion(container).textContent).toBe(en.signIn.starting);
    });
  });

  describe('it fails closed and can be retried (AC8)', () => {
    async function renderWithFailingRequest(message: string) {
      vi.doMock('./authorizeRequest', async () => {
        const actual = await vi.importActual<typeof import('./authorizeRequest')>('./authorizeRequest');
        return {
          ...actual,
          buildAuthorizeRequest: async () => {
            throw new TypeError(message);
          },
        };
      });
      const { SignInScreen: Screen } = await import('./SignInScreen');
      const container = document.createElement('div');
      document.body.append(container);
      const root = createRoot(container);
      act(() => {
        root.render(
          <I18nextProvider i18n={createI18n()}>
            <Screen />
          </I18nextProvider>,
        );
      });
      mounted = { root, container };
      return container;
    }

    it('stays on the screen, announces the catalogue message, and does not navigate', async () => {
      const assign = stubLocation();
      const container = await renderWithFailingRequest('not an RFC 7636 code verifier');
      await clickAndSettle(button(container));

      expect(assign).not.toHaveBeenCalled();
      expect(liveRegion(container).textContent).toBe(en.signIn.failed);
      expect(Object.keys(globalThis.sessionStorage)).toEqual([]);
    });

    // The handler is invoked as `void startSignIn()`, so anything it rethrows becomes an unhandled rejection
    // rather than a visible failure. Mutant M8 did exactly that and every other case stayed green: the screen
    // looks right, and the noise lands somewhere nothing in this package watches.
    it('lets no rejection escape the handler', async () => {
      // On process, not on window: measured in this environment, an unhandled rejection surfaces as Node's
      // `unhandledRejection` and jsdom dispatches no window event for it. A guard on the window channel passed
      // while mutant M8 was live, which is how the channel came to be checked rather than assumed.
      const unhandled: unknown[] = [];
      const onUnhandled = (reason: unknown) => unhandled.push(reason);
      process.on('unhandledRejection', onUnhandled);
      try {
        stubLocation();
        const container = await renderWithFailingRequest('escaping');
        await clickAndSettle(button(container));
        for (let index = 0; index < 5; index += 1) await turn();
        expect(unhandled).toEqual([]);
      } finally {
        process.off('unhandledRejection', onUnhandled);
      }
    });

    it('never shows the underlying error text', async () => {
      stubLocation();
      const container = await renderWithFailingRequest('verifier contract violated at offset 7');
      await clickAndSettle(button(container));

      expect(container.textContent ?? '').not.toContain('verifier');
      expect(container.textContent ?? '').not.toContain('offset 7');
    });

    // aria-disabled returning to "false" is the RENDERED consequence of setStatus('failed'), which is not the
    // same fact as the in-flight ref having been reset. If a regression reset the status but not the ref, the
    // button would look enabled and do nothing - the worst failure mode, because it is invisible. So the retry is
    // actually performed.
    it('re-arms the attempt itself: a second click after a failure succeeds', async () => {
      const assign = stubLocation();
      let shouldFail = true;
      vi.doMock('./authorizeRequest', async () => {
        const actual = await vi.importActual<typeof import('./authorizeRequest')>('./authorizeRequest');
        return {
          ...actual,
          buildAuthorizeRequest: async () => {
            if (shouldFail) throw new TypeError('transient');
            return actual.buildAuthorizeRequest();
          },
        };
      });
      const { SignInScreen: Screen } = await import('./SignInScreen');
      const container = document.createElement('div');
      document.body.append(container);
      const root = createRoot(container);
      act(() => {
        root.render(
          <I18nextProvider i18n={createI18n()}>
            <Screen />
          </I18nextProvider>,
        );
      });
      mounted = { root, container };

      await clickAndSettle(button(container));
      expect(assign).not.toHaveBeenCalled();
      expect(button(container).getAttribute('aria-disabled')).toBe('false');

      shouldFail = false;
      await clickAndSettle(button(container));
      expect(assign).toHaveBeenCalledTimes(1);
    });
  });

  describe('it touches no storage and no console of its own (AC13)', () => {
    it('writes exactly the three items T15 writes, and calls no console method', async () => {
      stubLocation();
      const setItem = vi.spyOn(Storage.prototype, 'setItem');
      const removeItem = vi.spyOn(Storage.prototype, 'removeItem');
      const clear = vi.spyOn(Storage.prototype, 'clear');
      const consoleSpies = Object.keys(globalThis.console)
        .filter((key) => typeof (globalThis.console as unknown as Record<string, unknown>)[key] === 'function')
        .map((key) => vi.spyOn(globalThis.console as unknown as Record<string, () => void>, key));
      // A sample, not an exhaustive guard: it covers console's own enumerable function keys, so a host exposing
      // them non-enumerably would be under-spied. The source scan below is the authoritative check.
      expect(consoleSpies.length).toBeGreaterThan(10);

      const container = renderSignIn();
      await clickAndSettle(button(container));

      expect(setItem).toHaveBeenCalledTimes(3);
      expect(removeItem).not.toHaveBeenCalled();
      expect(clear).not.toHaveBeenCalled();
      for (const spy of consoleSpies) expect(spy).not.toHaveBeenCalled();
    });

    it('names no localStorage, no console and no absolute address in its source', () => {
      expect(componentSource).not.toMatch(/\blocalStorage\s*[.[]/);
      expect(componentSource).not.toMatch(/\bconsole\s*\./);
      expect(componentSource).not.toMatch(/https?:\/\//);
      expect(componentSource).not.toContain('import.meta' + '.env');
    });
  });

  /**
   * T16 closed a cycle: app/routes -> auth/SignInScreen -> auth/authorizeRequest -> app/routes, because
   * authorizeRequest reads APP_BASENAME to derive the redirect URI. It resolves only because that read happens
   * inside a function, so nothing touches the binding while it is still in its temporal dead zone. Hoisting it to
   * module scope - an ordinary-looking refactor - would throw at application START, and Rollup emits no
   * circular-dependency warning, so nothing in the build would say so. The safety is pinned here rather than left
   * to where one const happens to sit.
   */
  describe('the circular import it introduces stays safe (AC15)', () => {
    it('reads APP_BASENAME only inside a function body, never at module scope', async () => {
      const here = dirname(expect.getState().testPath as string);
      const source = await readFile(join(here, 'authorizeRequest.ts'), 'utf8');

      const uses = [...source.matchAll(/^(\s*).*\bAPP_BASENAME\b.*$/gm)]
        .filter((match) => !match[0].trimStart().startsWith('*') && !match[0].trimStart().startsWith('//'))
        .filter((match) => !match[0].startsWith('import'));
      expect(uses.length).toBeGreaterThan(0);
      // Every remaining use is indented, which at this file's top level means it is inside a block.
      for (const match of uses) expect(match[1], match[0].trim()).not.toBe('');
    });

    it('resolves under the order the application actually loads it in', async () => {
      // mountShell imports app/routes first; that is the order that puts authorizeRequest in the middle of the
      // cycle. If the dead zone were ever touched, this import would throw rather than return.
      const routes = await import('../app/routes');
      const { buildAuthorizeRequest: build } = await import('./authorizeRequest');

      expect(routes.APP_BASENAME).toBe('/app');
      const assign = stubLocation();
      expect(assign).not.toHaveBeenCalled();
      const { url } = await build();
      expect(new URL(url).searchParams.get('redirect_uri')).toBe(`${STUB_ORIGIN}${routes.APP_BASENAME}/callback`);
    });
  });

  describe('it is reachable in the real application (AC14)', () => {
    // T02's file disposes every router it creates (toDispose/afterEach). This case cannot: mountShell returns the
    // React Root, not the router it built, so the history subscription outlives the unmount. Accepted rather than
    // worked around, because the alternative - rebuilding mountShell's tree by hand here - would stop this case
    // testing the thing it exists to test, which is that the REAL entry point renders the screen. jsdom is torn
    // down per file, so the leak does not reach another file.
    it('renders at /app/sign-in, and the shell outside /app still renders nothing', async () => {
      const { mountShell } = await import('../app/mountShell');

      window.history.pushState({}, '', '/app/sign-in');
      const atSignIn = document.createElement('div');
      document.body.append(atSignIn);
      let root: Root | undefined;
      try {
        act(() => {
          root = mountShell(atSignIn);
        });
        expect(atSignIn.textContent ?? '').toContain(en.signIn.heading);
        expect(credentialInputsIn(atSignIn)).toEqual([]);
      } finally {
        if (root) act(() => (root as Root).unmount());
        atSignIn.remove();
        window.history.pushState({}, '', '/');
      }

      // T01's invariant, asserted here rather than only inherited: outside the basename the router matches
      // nothing, so the shell is empty even now that a screen exists. The origin is asserted first because it is
      // the REASON the shell is empty - without it the case would pass for an unrelated reason.
      expect(globalThis.location.origin).toBe(JSDOM_ORIGIN);
      expect(globalThis.location.pathname.startsWith(APP_BASENAME)).toBe(false);

      const outside = document.createElement('div');
      document.body.append(outside);
      let outsideRoot: Root | undefined;
      try {
        act(() => {
          outsideRoot = mountShell(outside);
        });
        expect(outside.querySelector('[data-testid="app-shell"]')?.innerHTML).toBe('');
      } finally {
        if (outsideRoot) act(() => (outsideRoot as Root).unmount());
        outside.remove();
      }
    });
  });
});
