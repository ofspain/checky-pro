/// <reference types="node" />
import { readdir, readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { act, type ReactElement } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createI18n } from '../i18n/i18n';
import { createAuthApi } from '../api/client';
import { SiblingRenewalFailedError } from '../api/crossTabRenewal';
import { createSessionBoundary, type SessionTokens } from '../api/sessionBoundary';
import { APP_BASENAME, AUTHENTICATED_ROUTE_PATHS, appRoutes, HOME_PATH, PUBLIC_ROUTE_PATHS, SIGN_IN_PATH } from '../app/routes';
import { en } from '../i18n/catalogue';
import { accessToken, clearAccessToken, retainAccessToken } from './accessToken';
import { createRenewalFailureHandler, inAppPath } from './cleanReauth';
import * as returnTargetModule from './returnTarget';
import { RETURN_TARGET_KEY, takeReturnTarget } from './returnTarget';
import { SignInScreen } from './SignInScreen';
import * as noticeModule from './signInNotice';
import { NOTICE_COPY_KEY, setSignInNotice, takeSignInNotice } from './signInNotice';

/**
 * R22, clauses 1 to 3: a failed renewal transitions to clean re-auth, shows no half-authenticated screen, and
 * shows neutral copy.
 *
 * WHAT MAKES THIS TASK UNUSUAL, and it is worth knowing before reading a single case. Renewal in this SPA does
 * not work and is not meant to yet: `accessToken.renew()` always rejects, because silent re-authentication is
 * the model the owner chose and no unit of `tasks.md` builds it. So the failed path is not an edge case here -
 * **it is the only renewal path that exists**, and this task is what happens on every 401 that outlives a
 * token.
 *
 * WHAT THIS FILE CANNOT PROVE. `createSessionBoundary` has no production caller: every non-test reference in
 * `src/` is a doc comment, task 1 mounts "an empty shell", tasks 5 and 6 built the boundary and the cross-tab
 * renewal, and no unit wires them in. So the handler below is reached by this suite and by nothing else
 * (OQ-T33-2). The cases drive the REAL boundary rather than a stub for that reason - how the hook is called is
 * the part that has to stay true when the wiring arrives, and a stub would only prove the module can be called
 * the way a test chose to call it.
 *
 * AND WHY "NO HALF-AUTHENTICATED SCREEN" IS AN ENVIRONMENTAL CLAIM TODAY. There is no route guard - T41 owns
 * it - so nothing stops a tab from rendering an authenticated route after its session dies. What makes the
 * claim true right now is that **no authenticated route has an element at all**. That is an assumption about
 * the repository, not a property of this code, so AC11 pins it: the moment a screen lands on one of those
 * paths, that case fails and names T41, which is an instruction rather than a puzzle.
 */

// This file is frontend/src/auth/<name>; jsdom gives import.meta.url a non-file URL.
const repoRoot = dirname(dirname(dirname(dirname(expect.getState().testPath as string))));
const MODULE = 'frontend/src/auth/cleanReauth.ts';

const NO_SOURCE = 'refusing to report on a source file that was not read';

/** An empty scan is REFUSED, never reported clean (T22-D14). A scan of nothing passes every absence claim. */
function refuseEmpty(text: string): string {
  if (text.trim() === '') throw new Error(NO_SOURCE);
  return text;
}

function moduleSource(): Promise<string> {
  return readFile(join(repoRoot, MODULE), 'utf8').then(refuseEmpty);
}

/** Comments removed, string literals KEPT - a route name inside a literal must not leave before the scan. */
function strip(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .split('\n')
    .map((line) => {
      const at = line.indexOf('//');
      // Only a `//` with no quote before it on the line, so a URL inside a literal survives.
      return at >= 0 && !/['"`]/.test(line.slice(0, at)) ? line.slice(0, at) : line;
    })
    .join('\n');
}

// ---------------------------------------------------------------------------------------------------------
// The boundary harness, copied from `api/parallel401sTriggerExactlyOneRenewal.test.ts:30-100`.
//
// THE THIRD COPY of this shape, and said out loud rather than quietly repeated: tasks 5 and 6 each built
// their own, both are closed, and extracting it now would edit two closed tasks' instruments. That is the
// same decision OQ-T32-4 holds open for the scan helpers, and the count of copies is the signal.
// ---------------------------------------------------------------------------------------------------------

type Sent = { path: string; auth: string | null };

/** Browser-style relative URL resolution: Node's Request rejects relative URLs, a browser resolves them. */
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

/** A fake transport that answers every protected call 401, which is what provokes a renewal. */
function installTransport(status = 401) {
  const sent: Sent[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (request: Request) => {
      sent.push({ path: new URL(request.url).pathname, auth: request.headers.get('Authorization') });
      return new Response('{}', { status, headers: { 'Content-Type': 'application/json' } });
    }),
  );
  return sent;
}

/** A session whose renewal always rejects - which is production's behaviour today, not a pessimistic fake. */
function rejectingSession(reason: unknown, initial: string | null = 'held-token'): { tokens: SessionTokens; renew: ReturnType<typeof vi.fn> } {
  const renew = vi.fn(() => Promise.reject(reason));
  return { tokens: { current: () => initial, renew }, renew };
}

/** Lets the microtask the boundary schedules the hook in actually run. */
const settle = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

/**
 * Renders the real sign-in screen, the shape copied from T15's suite.
 *
 * Needed because the handler's whole visible effect is a sentence on THAT screen: a case that stopped at
 * "the notice was set" would pass while the screen displayed the wrong sentence, which is exactly what the
 * screen did before this task changed it - it rendered `signIn.discarded` for any notice at all.
 */
let mounted: { root: Root; container: HTMLElement } | null = null;

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

/** Records where the handler navigated, standing in for the router the wiring will supply. */
function recorder() {
  const to: string[] = [];
  return { to, navigate: (path: string) => to.push(path) };
}

/*
 * THE CAUSE WORDS, grounded in L4's own text and used in BOTH places - Kimi's phase 11 F3, which found the
 * two lists had drifted apart. The catalogue check forbade `expire|token|lock|...`; the surface sweep looked
 * only for this run's token value and the words `renewal`, `401` and `sibling`. So a regression that logged
 * "Your token expired" would have passed BOTH: the sweep because `expired` was not a needle, and the
 * catalogue check because the catalogue sentence was unchanged. One list now feeds both.
 *
 *   account states      unknown | lock | suspend | delet
 *   token conditions    invalid | expire | revoke | token | refresh | renew | 401
 *   steps and factors   password | mfa | totp        (L2: which factors are required is the server's)
 *   infrastructure      server | network             (not L4's text; a cause is a cause)
 */
const CAUSE_WORDS = [
  'unknown', 'lock', 'suspend', 'delet',
  'invalid', 'expire', 'revoke', 'token', 'refresh', 'renew', '401',
  'password', 'mfa', 'totp',
  'server', 'network',
] as const;

const FORBIDDEN_CAUSE = new RegExp(CAUSE_WORDS.join('|'), 'i');

/** Every cause word, plus this run's own token value and the cross-tab vocabulary. */
const SURFACE_NEEDLES = [...CAUSE_WORDS, 'held-token', 'sibling'] as const;
const CONSOLE_CHANNELS = ['log', 'info', 'warn', 'error', 'debug', 'trace'] as const;

function watchConsole() {
  const spies = CONSOLE_CHANNELS.map((level) => vi.spyOn(console, level).mockImplementation(() => undefined));
  return () => JSON.stringify(spies.map((spy) => spy.mock.calls));
}

/** Every surface a failure could leak onto, swept together so a new one cannot be added quietly. */
function surfaces(): { name: string; text: string }[] {
  return [
    { name: 'body', text: document.body.innerHTML },
    { name: 'title', text: document.title },
    { name: 'head', text: document.head.innerHTML },
    { name: 'url', text: window.location.href },
    { name: 'window.name', text: window.name },
    { name: 'localStorage', text: JSON.stringify({ ...globalThis.localStorage }) },
    { name: 'sessionStorage', text: JSON.stringify({ ...globalThis.sessionStorage }) },
  ];
}

function leaks(needles: readonly string[], logged: string): string[] {
  const found: string[] = [];
  for (const surface of [...surfaces(), { name: 'console', text: logged }]) {
    for (const needle of needles) {
      if (surface.text.toLowerCase().includes(needle.toLowerCase())) found.push(`${surface.name}/${needle}`);
    }
  }
  return found;
}

beforeEach(() => {
  installBrowserRequest();
  clearAccessToken();
  takeSignInNotice();
  globalThis.sessionStorage.clear();
  globalThis.localStorage.clear();
  globalThis.history.replaceState({}, '', `${APP_BASENAME}/home`);
});

afterEach(() => {
  if (mounted) {
    const { root, container } = mounted;
    act(() => root.unmount());
    container.remove();
    mounted = null;
  }
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  clearAccessToken();
  takeSignInNotice();
  globalThis.sessionStorage.clear();
  globalThis.localStorage.clear();
  document.body.innerHTML = '';
  // Head before title: assigning `head.innerHTML` deletes a `<title>` that `document.title =` wrote into it.
  document.head.innerHTML = '';
  document.title = '';
  window.name = '';
  // Reset HERE and not at the end of the case that navigates, so a case that fails before its own cleanup
  // cannot leak its path into the next one's return target.
  globalThis.history.replaceState({}, '', APP_BASENAME);
});

describe('failedRenewalTriggersCleanReauthWithoutHalfSession', () => {
  // -------------------------------------------------------------------------------------------------------
  // AC1 — nothing is left held. The first clause of "no half-session": a tab holding a token it cannot use.
  // -------------------------------------------------------------------------------------------------------

  describe('AC1 the held token is dropped', () => {
    it('leaves no token held after the transition', () => {
      retainAccessToken('held-token');
      expect(accessToken.current()).toBe('held-token');
      createRenewalFailureHandler(recorder().navigate)();
      expect(accessToken.current()).toBeNull();
    });

    it('clears FIRST, so a later step that throws cannot leave a token behind', () => {
      retainAccessToken('held-token');
      const explode = () => {
        throw new Error('the wiring supplied a navigate that throws');
      };
      expect(() => createRenewalFailureHandler(explode)()).toThrow();
      // The worst case is a cleared session that did not announce itself - never a tab that looks signed in.
      expect(accessToken.current()).toBeNull();
    });

    it('clears through the one primitive, holding no second copy of its own (source)', async () => {
      const code = strip(await moduleSource());
      expect(code).toContain('clearAccessToken()');
      // No private store, and no second way to drop the token: `accessToken.ts` owns the only one.
      for (const forbidden of [/\bheld\b/, /let\s+\w+\s*(:|=)/, /sessionStorage/, /localStorage/]) {
        expect(code, `cleanReauth.ts now writes ${forbidden}`).not.toMatch(forbidden);
      }
    });
  });

  // -------------------------------------------------------------------------------------------------------
  // AC2/AC3 — once per renewal, and the waiting calls are unharmed. Driven through the REAL boundary.
  // -------------------------------------------------------------------------------------------------------

  describe('AC2 one transition per renewal, and repeating it is harmless', () => {
    it('transitions ONCE when three parallel 401s share one failed renewal', async () => {
      const sent = installTransport();
      const session = rejectingSession(new Error('renewal rejected'));
      const nav = recorder();
      const api = createAuthApi(createSessionBoundary(session.tokens, createRenewalFailureHandler(nav.navigate)));

      const answers = await Promise.all([
        api.GET('/accounts/me'),
        api.GET('/accounts/me'),
        api.GET('/api-keys'),
      ]);
      await settle();

      expect(session.renew).toHaveBeenCalledTimes(1);
      expect(nav.to).toEqual([`/${SIGN_IN_PATH}`]);
      // AC3: every waiting call still gets its own 401 back, and none was re-sent.
      expect(answers.map((answer) => answer.response.status)).toEqual([401, 401, 401]);
      expect(sent).toHaveLength(3);
    });

    it('transitions again on a SECOND failed renewal in the same tab', async () => {
      installTransport();
      const session = rejectingSession(new Error('renewal rejected'));
      const nav = recorder();
      const api = createAuthApi(createSessionBoundary(session.tokens, createRenewalFailureHandler(nav.navigate)));

      await api.GET('/accounts/me');
      await settle();
      await api.GET('/accounts/me');
      await settle();

      // Once per RENEWAL, not once per tab: a tab can lose several sessions over its life.
      expect(session.renew).toHaveBeenCalledTimes(2);
      expect(nav.to).toEqual([`/${SIGN_IN_PATH}`, `/${SIGN_IN_PATH}`]);
    });

    it('is harmless to repeat while the tab is already at the destination', () => {
      globalThis.history.replaceState({}, '', `${APP_BASENAME}/${SIGN_IN_PATH}`);
      retainAccessToken('held-token');
      const nav = recorder();
      const handler = createRenewalFailureHandler(nav.navigate);

      expect(() => {
        handler();
        handler();
      }).not.toThrow();

      expect(accessToken.current()).toBeNull();
      expect(takeSignInNotice()).toBe('session-ended');
      expect(nav.to).toEqual([`/${SIGN_IN_PATH}`, `/${SIGN_IN_PATH}`]);
      // Sign-in is not an allowlisted return target, so nothing was remembered and no branch was needed.
      expect(globalThis.sessionStorage.getItem(RETURN_TARGET_KEY)).toBeNull();
    });
  });

  describe('AC3 the waiting calls are unharmed and nothing renews twice', () => {
    it('does not start a second renewal for a call that waited behind the failed one', async () => {
      installTransport();
      const session = rejectingSession(new Error('renewal rejected'));
      const nav = recorder();
      const api = createAuthApi(createSessionBoundary(session.tokens, createRenewalFailureHandler(nav.navigate)));

      const first = api.GET('/accounts/me');
      const second = api.GET('/accounts/me');
      const [one, two] = await Promise.all([first, second]);
      await settle();

      expect(session.renew).toHaveBeenCalledTimes(1);
      expect(one.response.status).toBe(401);
      expect(two.response.status).toBe(401);
    });
  });

  // -------------------------------------------------------------------------------------------------------
  // AC8 — a throwing transition must not reach the callers. The microtask at sessionBoundary.ts:71 exists for
  // this, and no existing suite drives it from the routing side.
  // -------------------------------------------------------------------------------------------------------

  describe('AC8 a transition that throws does not reach the waiting callers', () => {
    it('hands the caller its 401, and the throw escapes as an uncaught exception instead', async () => {
      // `sessionBoundary.ts:69-71` schedules the hook in a microtask "so a throwing hook can't affect the
      // waiters; its error still surfaces as uncaught". Both halves of that sentence are asserted here, and
      // the second half is the reason this case has to take the runner's handlers off for a moment: an
      // exception thrown inside a microtask reaches `uncaughtException`, and the runner would otherwise
      // report this deliberate throw as a failure of the file. Nothing is silenced - it is CAUGHT and
      // counted, which turns a comment in another module into something that fails if it stops being true.
      const saved = process.listeners('uncaughtException');
      const escaped: unknown[] = [];
      try {
        // Inside the try, not before it - Kimi's phase 8 I3. With these two calls above the `try`, a throw
        // between them would leave the runner with no handlers at all and the `finally` would never run.
        process.removeAllListeners('uncaughtException');
        process.on('uncaughtException', (error) => escaped.push(error));

        installTransport();
        const session = rejectingSession(new Error('renewal rejected'));
        const api = createAuthApi(createSessionBoundary(session.tokens, () => {
          throw new Error('the wiring supplied a navigate that throws');
        }));

        const answer = await api.GET('/accounts/me');
        await settle();
        expect(answer.response.status).toBe(401);
        expect(answer.error).toBeDefined();
      } finally {
        process.removeAllListeners('uncaughtException');
        for (const listener of saved) process.on('uncaughtException', listener);
      }

      expect(escaped).toHaveLength(1);
      expect((escaped[0] as Error).message).toBe('the wiring supplied a navigate that throws');
    });

    it('clears the token BEFORE remembering, proven by a remember that throws', async () => {
      // Kimi's phase 11 F4. The existing order case supplies a throwing `navigate`, which only pins
      // clear-before-navigate; a reorder putting the clear after `rememberReturnTarget` passed every case,
      // because the real `rememberReturnTarget` swallows its own storage errors and never throws here.
      // The MODULE function, not `sessionStorage.setItem`: jsdom's Storage is proxied, so an own-property
      // spy on it never intercepts - measured, that was this case's first version and it reported
      // "expected setItem to be called at least once" while the real write went through underneath.
      const remember = vi.spyOn(returnTargetModule, 'rememberReturnTarget').mockImplementation(() => {
        throw new Error('the return-target store is gone');
      });
      retainAccessToken('held-token');
      globalThis.history.replaceState({}, '', `${APP_BASENAME}/account`);
      const nav = recorder();

      expect(() => createRenewalFailureHandler(nav.navigate)()).toThrow('the return-target store is gone');
      // The order, which is the whole claim: the token was already gone when the throw happened.
      expect(accessToken.current()).toBeNull();
      expect(remember).toHaveBeenCalledWith('/account');
      // Nothing downstream ran, so no notice and no navigation claim the visitor was taken anywhere.
      expect(takeSignInNotice()).toBeNull();
      expect(nav.to).toEqual([]);
      remember.mockRestore();
    });

    it('survives a notice setter that throws, with the token already gone', () => {
      // Kimi's phase 11 F2, the second half. If `setSignInNotice` ever throws, the visitor is left with a
      // cleared token, no notice and no navigation - which is a dead end, but NOT a half-authenticated tab.
      // That ordering is the whole point of clearing first, so it is asserted rather than assumed.
      retainAccessToken('held-token');
      const notice = vi.spyOn(noticeModule, 'setSignInNotice').mockImplementation(() => {
        throw new Error('the notice store is gone');
      });
      const nav = recorder();
      expect(() => createRenewalFailureHandler(nav.navigate)()).toThrow('the notice store is gone');
      expect(accessToken.current()).toBeNull();
      // And the navigation never happened, so nothing claims the visitor was taken anywhere.
      expect(nav.to).toEqual([]);
      notice.mockRestore();
    });

    it('and the real handler does not throw for any location it can be run at', () => {
      // The case above proves the boundary's containment. This one proves the handler does not NEED it: the
      // three shapes a tab can be in when its session dies - inside the SPA, at the destination already, and
      // outside the SPA entirely - all complete without throwing.
      for (const path of [`${APP_BASENAME}/account`, `${APP_BASENAME}/${SIGN_IN_PATH}`, '/elsewhere']) {
        globalThis.history.replaceState({}, '', path);
        expect(() => createRenewalFailureHandler(recorder().navigate)(), path).not.toThrow();
      }
    });
  });

  // -------------------------------------------------------------------------------------------------------
  // AC4/AC10 — the destination, and that it is the START of the auth flow rather than an authenticated page.
  // -------------------------------------------------------------------------------------------------------

  describe('AC4/AC10 the destination is the public start of the auth flow', () => {
    it('navigates to SIGN_IN_PATH, basename-relative', () => {
      const nav = recorder();
      createRenewalFailureHandler(nav.navigate)();
      expect(nav.to).toEqual([`/${SIGN_IN_PATH}`]);
    });

    it('is a PUBLIC route and not an authenticated one', () => {
      expect([...PUBLIC_ROUTE_PATHS]).toContain(SIGN_IN_PATH);
      expect([...AUTHENTICATED_ROUTE_PATHS]).not.toContain(SIGN_IN_PATH);
    });

    it('has a screen attached, which is what makes it the START of the flow and not a dead end', () => {
      const children = appRoutes[0].children ?? [];
      const signIn = children.find((route) => route.path === SIGN_IN_PATH);
      expect(signIn, 'the sign-in route is missing from the route table').toBeDefined();
      // Kimi phase 3 F1: landing here is a transition to re-auth only if this screen can start one.
      expect(signIn?.element).toBeDefined();
    });

    it('and the destination route carries SignInScreen, not merely some element', () => {
      /*
       * Found in phase 10 by reading AC10 clause by clause. The clause says the destination's screen
       * "builds an authorize request on the visitor's action"; the case above only asserts that SOME
       * element is attached. A route carrying a different element would have passed it - and would have
       * passed T15's suite too, because T15 renders `SignInScreen` directly rather than through the route
       * table. So nothing tied the ROUTE this task navigates to, to the SCREEN that can start re-auth.
       *
       * This case is the tie, and its name says only what it proves - Kimi's phase 11 F6 was right that
       * the earlier name ("the one that STARTS the flow") claimed the whole clause. What that screen does
       * with the visitor's click is T15's to prove, and T15 proves it; this fixes which screen is at the
       * other end of the navigation, which is the half nobody owned.
       */
      const children = appRoutes[0].children ?? [];
      const signIn = children.find((route) => route.path === SIGN_IN_PATH);
      expect((signIn?.element as ReactElement | undefined)?.type).toBe(SignInScreen);
    });

    it('reads the route names INSIDE a function, never at module scope (source)', async () => {
      const code = strip(await moduleSource());
      const lines = code.split('\n');
      for (const name of ['SIGN_IN_PATH', 'APP_BASENAME']) {
        const uses = lines.filter((line) => new RegExp(`\\b${name}\\b`).test(line) && !line.includes('import'));
        expect(uses, `${name} is never used`).not.toHaveLength(0);
        // Indented, therefore inside a block. T16 measured that a module-scope read of a route constant
        // yields `undefined` rather than throwing, and an `undefined` allowlist fails closed SILENTLY.
        for (const use of uses) expect(use, `${name} read at module scope: ${use}`).toMatch(/^\s+/);
      }
    });
  });

  // -------------------------------------------------------------------------------------------------------
  // AC11 — the environmental assumption behind "no half-authenticated screen", pinned so it fails when it
  // stops being true.
  // -------------------------------------------------------------------------------------------------------

  describe('AC4 the basename boundary is exact', () => {
    it('normalises the tab path, and keeps a lookalike prefix OUTSIDE the SPA', () => {
      // Kimi's phase 11 F7. Driven through the real `location`, so the normalisation is measured and not
      // re-implemented here. `/applications/x` is the case worth having: a bare `startsWith(base)` would
      // have claimed it for the SPA and remembered a return target pointing out of it.
      for (const [path, expected] of [
        [APP_BASENAME, '/'],
        [`${APP_BASENAME}/`, '/'],
        [`${APP_BASENAME}/account`, '/account'],
        [`${APP_BASENAME}/invoices/123`, '/invoices/123'],
        ['/applications/x', null],
        ['/elsewhere', null],
        ['/', null],
      ] as const) {
        globalThis.history.replaceState({}, '', path);
        expect(inAppPath(), path).toBe(expected);
      }
    });
  });

  describe('AC11 no authenticated route can render anything yet', () => {
    it('pins that every authenticated path has no element - a T41 obligation when it changes', () => {
      // GROWN BY T40, and the growth is in WHERE it looks, not in what it claims. T40 moved the authenticated
      // eleven out of the root's direct children and under one pathless guard route, so a search restricted to
      // `appRoutes[0].children` stopped finding them - and the lower bound below is what turned that into a
      // failure rather than a silent pass over an empty list. It walks the tree now.
      //
      // The attribution in this case's title is also now wrong, and is left as written rather than edited,
      // because the title is what the task that wrote it claimed: the ROUTE GUARD is task 40's, which is the
      // change this comment is about, and only the REDIRECT is task 41's. The guard carries no `path`, so it
      // is not an element on an authenticated path and this pin still reads zero - which is the point. A
      // SCREEN on one of these paths remains task 42's to attach, and this case is still what will stop it
      // from landing unnoticed.
      // GROWN AGAIN BY TASK 174, and this is the day the paragraph above was written for: "a SCREEN on one
      // of these paths remains task 42's to attach, and this case is still what will stop it from landing
      // unnoticed." It landed, and this case stopped it. The attribution was off by one task - the home
      // screen is 174's, not 42's - but the instrument did exactly its job.
      //
      // WHAT CHANGED IS THE CLAIM, not the tolerance. "No authenticated route renders anything" was never
      // the requirement; it was a stand-in for R22, and it held for free while the route table was empty. It
      // cannot hold any more, so the pin now states the real thing: `home` is the ONE authenticated path
      // with an element, every other one still has none, and - the part that matters for R22 - the screen it
      // renders sits behind the guard, so a failed renewal reaches the redirect and not a half-session.
      // Loosening this to "zero or more" would have been the easy edit and would have measured nothing; an
      // exact list fails the next time a screen is attached, which is what a handoff is.
      type Node = { path?: string; element?: unknown; children?: Node[] };
      const walk = (routes: readonly Node[]): Node[] =>
        routes.flatMap((route) => [route, ...walk(route.children ?? [])]);
      const everyRoute = walk(appRoutes as Node[]);
      const withElements = [...AUTHENTICATED_ROUTE_PATHS].filter(
        (path) => everyRoute.find((route) => route.path === path)?.element !== undefined,
      );
      expect(
        withElements,
        'The set of authenticated routes carrying a screen changed. R22 is no longer held by an empty route '
          + 'table, so whoever attached this one must show the guard still covers it (T40 owns the guard).',
      ).toEqual([HOME_PATH]);
      // Lower bound: the paths really are in the table, so the check is not passing over an empty list.
      expect(everyRoute.map((route) => route.path)).toEqual(expect.arrayContaining([...AUTHENTICATED_ROUTE_PATHS]));
    });
  });

  // -------------------------------------------------------------------------------------------------------
  // AC5 — the copy. Neutral, catalogue-only, and not the sentence that would be false here.
  // -------------------------------------------------------------------------------------------------------

  describe('AC5 the notice is neutral and is not the discarded sentence', () => {
    it('sets the session-ended notice, which maps to its own catalogue sentence', () => {
      createRenewalFailureHandler(recorder().navigate)();
      const notice = takeSignInNotice();
      expect(notice).toBe('session-ended');
      expect(NOTICE_COPY_KEY[notice as 'session-ended']).toBe('signIn.sessionEnded');
      expect(en.signIn.sessionEnded).not.toBe(en.signIn.discarded);
    });

    it('names no cause, status, step or factor', () => {
      /*
       * GROUNDED IN L4, GROUP BY GROUP - Kimi's phase 8 I5, which was right that the list was judgement
       * presented as a rule. L4 requires copy to be identical across "unknown or missing, locked, suspended,
       * and deleted accounts and across bad, expired, and used tokens":
       *
       *   account states      unknown | lock | suspend | delet
       *   token conditions    invalid | expire | revoke | token | refresh | renew | 401
       *   steps and factors   password | mfa | totp        (L2: which factors are required is the server's)
       *   infrastructure      server | network             (not L4's text; a cause is a cause)
       *
       * The last group is mine and is marked as mine. What L4 does NOT forbid is naming the EVENT, which is
       * why `session` is absent and why the sentence may say a session has ended - the case below draws that
       * boundary explicitly rather than leaving it to be inferred from an absence.
       */
      // The list and its grouping are at the top of this file, shared with the surface sweep so the two
      // cannot drift - which they had, until Kimi's phase 11 F3.
      expect(en.signIn.sessionEnded).not.toMatch(FORBIDDEN_CAUSE);
      // CONTROL: the pattern really does fire on the sentence this rejects.
      expect('Your token expired, so renewal failed.').toMatch(FORBIDDEN_CAUSE);
    });

    it('draws the line at naming the EVENT, which L4 does not forbid', () => {
      // Making the boundary visible, so the next author does not read the list above as "no nouns allowed"
      // and does not widen it without noticing what that would cost. `session` is deliberately absent: a
      // sentence may say WHAT happened to the visitor's own session, never WHY or to WHOSE account.
      expect(en.signIn.sessionEnded.toLowerCase()).toContain('session');
      // And a list that also banned the event word would reject the sentence this task is required to show,
      // which is the trade being made - measured, not asserted.
      expect(en.signIn.sessionEnded).toMatch(/session/i);
    });

    it('puts THAT sentence on the sign-in screen, and not the discarded one', () => {
      createRenewalFailureHandler(recorder().navigate)();
      const text = renderSignIn().textContent ?? '';
      expect(text).toContain(en.signIn.sessionEnded);
      expect(text).not.toContain(en.signIn.discarded);
      // A missing provider makes useTranslation return the key, which this catches.
      expect(text).not.toContain('signIn.');
    });

    it('CONTROL: the same screen still shows the discarded sentence for a discarded callback', () => {
      // The screen now chooses by notice, so this is the proof that choosing did not break R8's path -
      // T18 owns that sentence and this task must not have quietly taken it over.
      setSignInNotice('discarded');
      const text = renderSignIn().textContent ?? '';
      expect(text).toContain(en.signIn.discarded);
      expect(text).not.toContain(en.signIn.sessionEnded);
    });

    it('REPLACES a notice already pending, rather than queueing behind it', () => {
      // Kimi's phase 11 F9. The store holds one value and taking it clears it, so queueing is impossible
      // today - but nothing failed if that changed, and a queue would show the visitor the older, wrong
      // sentence first. The newer event is always the truer one.
      setSignInNotice('discarded');
      createRenewalFailureHandler(recorder().navigate)();
      expect(takeSignInNotice()).toBe('session-ended');
      expect(takeSignInNotice()).toBeNull();
    });

    it('is taken once, so a reloaded sign-in page stops saying it', () => {
      createRenewalFailureHandler(recorder().navigate)();
      expect(takeSignInNotice()).toBe('session-ended');
      expect(takeSignInNotice()).toBeNull();
    });
  });

  // -------------------------------------------------------------------------------------------------------
  // AC9 — the reason is unreachable, which is how L4 is kept by construction rather than by discipline.
  // -------------------------------------------------------------------------------------------------------

  describe('AC9 the failure reason cannot be read', () => {
    it('takes no parameter at all, so there is nothing to branch on', () => {
      expect(createRenewalFailureHandler(recorder().navigate)).toHaveLength(0);
    });

    it('gives an identical outcome for a local failure and a sibling tab failure', async () => {
      const outcomes: { to: string[]; notice: string | null; held: string | null }[] = [];
      for (const reason of [new Error('renewal rejected'), new SiblingRenewalFailedError()]) {
        installTransport();
        retainAccessToken('held-token');
        const session = rejectingSession(reason);
        const nav = recorder();
        const api = createAuthApi(createSessionBoundary(session.tokens, createRenewalFailureHandler(nav.navigate)));
        await api.GET('/accounts/me');
        await settle();
        outcomes.push({ to: nav.to, notice: takeSignInNotice(), held: accessToken.current() });
        vi.unstubAllGlobals();
        installBrowserRequest();
      }
      expect(outcomes[0]).toEqual(outcomes[1]);
      expect(outcomes[0]).toEqual({ to: [`/${SIGN_IN_PATH}`], notice: 'session-ended', held: null });
    });

    it('does not name the reason parameter, nor bind a caught error, in its own source', async () => {
      const code = strip(await moduleSource());
      // `catch (error)` BINDS what was thrown and so can inspect it; `catch {}` cannot. Kimi's phase 8 I4:
      // the earlier `/\bcatch\b/` ban blocked both, so a future author guarding a throwing `navigate` -
      // which is safe and which this module may one day want - would have failed a test about the failure
      // REASON. The ban is now on the binding, which is the thing that could leak a cause.
      for (const forbidden of [/\breason\b/, /catch\s*\(/, /instanceof/]) {
        expect(code, `cleanReauth.ts now writes ${forbidden}`).not.toMatch(forbidden);
      }
      // CONTROL: the narrowed pattern still fires on the shape it is meant to stop, and lets the safe one by.
      expect('try { navigate(x); } catch (error) { report(error); }').toMatch(/catch\s*\(/);
      expect('try { navigate(x); } catch { /* discarded */ }').not.toMatch(/catch\s*\(/);
    });

    it('imports no router, so the injected navigate cannot be quietly bypassed (source)', async () => {
      // Kimi's phase 8 I9. Injection is the architectural protection; without this scan a future edit could
      // import the router directly and every behavioural case here would still pass.
      const code = strip(await moduleSource());
      // `location.assign` and `location.href =` are the other ways past the injected navigate, and both
      // would be a FULL-PAGE navigation - which discards the in-memory notice this task just set, so the
      // visitor would arrive at sign-in with nothing to read. Kimi's phase 11 F5.
      for (const forbidden of [
        /react-router/, /useNavigate/, /createBrowserRouter/,
        /globalThis\.history/, /\blocation\s*\.\s*(assign|replace|href)/, /window\.location/,
      ]) {
        expect(code, `cleanReauth.ts now imports or reaches for ${forbidden}`).not.toMatch(forbidden);
      }
      // Lower bound: it really does take the navigate it is given, so the scan is not passing over nothing.
      expect(code).toContain('navigate(');
      // CONTROL: the location patterns fire on the shapes they exist to stop.
      for (const bypass of ['location.assign(x)', "globalThis.location.href = '/app/sign-in'", 'window.location.reload()']) {
        expect([/\blocation\s*\.\s*(assign|replace|href)/, /window\.location/].some((p) => p.test(bypass)), bypass)
          .toBe(true);
      }
      // And it does read `location.pathname`, which is a READ and not a navigation - so the ban is on the
      // three navigating members by name rather than on the object.
      expect(code).toContain('location.pathname');
    });
  });

  // -------------------------------------------------------------------------------------------------------
  // AC7 — every tab transitions for itself. A follower adopts a sibling's failure as its own outcome.
  // -------------------------------------------------------------------------------------------------------

  describe('AC7 a follower tab transitions itself, once', () => {
    it('treats a sibling failure as its own and renews nothing further', async () => {
      installTransport();
      const session = rejectingSession(new SiblingRenewalFailedError());
      const nav = recorder();
      const api = createAuthApi(createSessionBoundary(session.tokens, createRenewalFailureHandler(nav.navigate)));

      const [one, two] = await Promise.all([api.GET('/accounts/me'), api.GET('/accounts/me')]);
      await settle();

      expect(session.renew).toHaveBeenCalledTimes(1);
      expect(nav.to).toEqual([`/${SIGN_IN_PATH}`]);
      expect(one.response.status).toBe(401);
      expect(two.response.status).toBe(401);
    });
  });

  // -------------------------------------------------------------------------------------------------------
  // AC6 — no failure information anywhere, and exactly one permitted write.
  // -------------------------------------------------------------------------------------------------------

  describe('AC12 the dormancy is pinned, so wiring cannot happen without reading this', () => {
    it('records that NO production module constructs the boundary or this handler', async () => {
      /*
       * Kimi phase 11 F1 asked for a test asserting that `mountShell` constructs the boundary with this
       * handler. That test cannot pass today - nothing constructs it - so it would have to be added broken.
       * This is the same claim from the other side: it pins the CURRENT state, and it fails on the day
       * somebody wires the SPA up, which is precisely the day they should be reading this file.
       *
       * It is a canary, like AC11's. Its message is the handover note.
       */
      const root = dirname(expect.getState().testPath as string);
      const srcRoot = dirname(root);
      async function walk(dir: string): Promise<string[]> {
        const found: string[] = [];
        for (const entry of await readdir(dir, { withFileTypes: true })) {
          const full = join(dir, entry.name);
          if (entry.isDirectory()) found.push(...(await walk(full)));
          else if (/\.tsx?$/.test(entry.name) && !/\.test\.tsx?$/.test(entry.name)) found.push(full);
        }
        return found;
      }
      const files = await walk(srcRoot);
      expect(files.length, 'the scan read no production files').toBeGreaterThan(10);

      // The two files that DECLARE these functions name them too, so they are excluded by path and their
      // presence is the scan's lower bound. Matching a declaration as a call was this case's first version.
      const DEFINERS = ['api/sessionBoundary.ts', 'auth/cleanReauth.ts'];
      const relative = (file: string) => file.slice(srcRoot.length + 1);
      expect(files.map(relative), 'the files that declare these functions are missing from the scan')
        .toEqual(expect.arrayContaining(DEFINERS));

      const callers: string[] = [];
      for (const file of files) {
        if (DEFINERS.includes(relative(file))) continue;
        const code = strip(await readFile(file, 'utf8'));
        if (/createSessionBoundary\s*\(|createRenewalFailureHandler\s*\(/.test(code)) callers.push(file);
      }
      expect(
        callers.map(relative),
        'Something now constructs the session boundary or this handler. That is the wiring T33 was written '
          + 'for and could not reach: pass `createRenewalFailureHandler(navigate)` as the boundary\'s '
          + '`onRenewalFailed`, give it a navigate backed by the router, and delete this case. Until then '
          + 'every behavioural case in this file is a statement about how the hook WILL be called.',
      ).toEqual([]);
    });
  });

  describe('AC6 nothing about the failure is left on any surface', () => {
    it('remembers the allowlisted location and NOTHING else', () => {
      globalThis.history.replaceState({}, '', `${APP_BASENAME}/account`);
      createRenewalFailureHandler(recorder().navigate)();
      expect(Object.keys({ ...globalThis.sessionStorage })).toEqual([RETURN_TARGET_KEY]);
      expect(takeReturnTarget()).toBe('/account');
      expect(globalThis.localStorage.length).toBe(0);
    });

    it('remembers nothing for a location the allowlist cannot honour', () => {
      // A parameterised route is not allowlisted (T24-D5), so the visitor returns to home instead. Decided
      // there, measured here, so this task is not assumed to have changed it.
      globalThis.history.replaceState({}, '', `${APP_BASENAME}/invoices/123`);
      createRenewalFailureHandler(recorder().navigate)();
      expect(Object.keys({ ...globalThis.sessionStorage })).toEqual([]);
    });

    it('remembers nothing when the tab is not inside the SPA at all', () => {
      globalThis.history.replaceState({}, '', '/elsewhere/home');
      createRenewalFailureHandler(recorder().navigate)();
      expect(Object.keys({ ...globalThis.sessionStorage })).toEqual([]);
    });

    it('leaves no trace of the failure on any of the seven surfaces or six console channels', async () => {
      const logged = watchConsole();
      installTransport();
      retainAccessToken('held-token');
      const session = rejectingSession(new Error('renewal rejected: 401 from the server'));
      const api = createAuthApi(createSessionBoundary(session.tokens, createRenewalFailureHandler(recorder().navigate)));
      await api.GET('/accounts/me');
      await settle();

      // The return target is the one permitted write (L16a) and carries no failure information, so it is
      // removed before the sweep rather than excused inside it. Kimi's phase 8 I10 asked why: excusing it
      // INSIDE the sweep would mean writing a needle exception, and an exception in a leak sweep is the one
      // place a real leak can hide. Taking it means the sweep has no exceptions at all. What the stored
      // value IS stays covered by the case above, which asserts it equals `/account` exactly.
      takeReturnTarget();
      expect(leaks(SURFACE_NEEDLES, logged())).toEqual([]);
    });

    it('CONTROL: the sweep reports a flag on every surface and channel it watches', () => {
      const logged = watchConsole();
      // Head before title, for the same reason the cleanup does it in that order.
      document.head.innerHTML = '<meta name="x" content="held-token">';
      document.title = 'held-token';
      document.body.innerHTML = 'held-token';
      window.name = 'held-token';
      globalThis.localStorage.setItem('a', 'held-token');
      globalThis.sessionStorage.setItem('b', 'held-token');
      globalThis.history.replaceState({}, '', `${APP_BASENAME}?x=held-token`);
      for (const channel of CONSOLE_CHANNELS) console[channel]('held-token');

      const found = new Set(leaks(['held-token'], logged()).map((hit) => hit.split('/')[0]));
      expect([...found].sort())
        .toEqual(['body', 'console', 'head', 'localStorage', 'sessionStorage', 'title', 'url', 'window.name']);
    });

    it('CONTROL: a scan of nothing is refused, not reported clean', () => {
      expect(() => refuseEmpty('   ')).toThrow(NO_SOURCE);
      expect(refuseEmpty('x')).toBe('x');
    });
  });
});
