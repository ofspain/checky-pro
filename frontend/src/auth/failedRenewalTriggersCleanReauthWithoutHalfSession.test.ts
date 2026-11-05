/// <reference types="node" />
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createAuthApi } from '../api/client';
import { SiblingRenewalFailedError } from '../api/crossTabRenewal';
import { createSessionBoundary, type SessionTokens } from '../api/sessionBoundary';
import { APP_BASENAME, AUTHENTICATED_ROUTE_PATHS, appRoutes, PUBLIC_ROUTE_PATHS, SIGN_IN_PATH } from '../app/routes';
import { en } from '../i18n/catalogue';
import { accessToken, clearAccessToken, retainAccessToken } from './accessToken';
import { createRenewalFailureHandler } from './cleanReauth';
import { RETURN_TARGET_KEY, takeReturnTarget } from './returnTarget';
import { NOTICE_COPY_KEY, takeSignInNotice } from './signInNotice';

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

/** Records where the handler navigated, standing in for the router the wiring will supply. */
function recorder() {
  const to: string[] = [];
  return { to, navigate: (path: string) => to.push(path) };
}

const SURFACE_NEEDLES = ['held-token', 'renewal', 'Renewal', '401', 'sibling', 'Sibling'] as const;
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
      process.removeAllListeners('uncaughtException');
      const escaped: unknown[] = [];
      process.on('uncaughtException', (error) => escaped.push(error));
      try {
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

  describe('AC11 no authenticated route can render anything yet', () => {
    it('pins that every authenticated path has no element - a T41 obligation when it changes', () => {
      const children = appRoutes[0].children ?? [];
      const withElements = [...AUTHENTICATED_ROUTE_PATHS].filter(
        (path) => children.find((route) => route.path === path)?.element !== undefined,
      );
      expect(
        withElements,
        'An authenticated route now renders a screen, so "no half-authenticated render" is no longer true by '
          + 'accident of an empty route table. T41 owns the route guard; this task cannot cover it.',
      ).toEqual([]);
      // Lower bound: the paths really are in the table, so the check is not passing over an empty list.
      expect(children.map((route) => route.path)).toEqual(expect.arrayContaining([...AUTHENTICATED_ROUTE_PATHS]));
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
      const forbidden = /expire|invalid|revoke|renew|refresh|token|lock|suspend|delet|unknown|password|mfa|totp|server|network|401/i;
      expect(en.signIn.sessionEnded).not.toMatch(forbidden);
      // CONTROL: the pattern really does fire on the sentence this rejects.
      expect('Your token expired, so renewal failed.').toMatch(forbidden);
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

    it('does not name the reason parameter in its own source', async () => {
      const code = strip(await moduleSource());
      for (const forbidden of [/\breason\b/, /\bcatch\b/, /instanceof/]) {
        expect(code, `cleanReauth.ts now writes ${forbidden}`).not.toMatch(forbidden);
      }
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
      // removed before the sweep rather than excused inside it.
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
