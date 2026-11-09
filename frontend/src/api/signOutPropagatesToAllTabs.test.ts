/**
 * R22's last unowned clause — "Sign-out SHALL propagate to every open tab" — and L18's second obligation.
 *
 * **WHAT THIS FILE OWNS AND WHAT IT DOES NOT.** It owns `crossTabSignOut.ts`: the channel, the message, the
 * latch, the broadcast and the listener. The fix it enables for OQ-T35-3 is asserted in task 35's suite,
 * beside the hazard case it replaced, because that is where the handoff was written. That `endSession()`
 * broadcasts is task 36's suite's, for the same reason.
 *
 * **NOTHING INSTALLS THE LISTENER IN PRODUCTION.** `endSession()` has no caller (OQ-T36-5) and no module
 * owns app-level listeners - `mountShell.tsx` is unowned. So every case here installs the listener itself,
 * and "signing out in one tab signs out all open tabs" is verified as a mechanism rather than as a journey.
 * Task 39 is the ninth consecutive task in this position.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import {
  SIGN_OUT_CHANNEL,
  SIGN_OUT_TYPE,
  broadcastSignOut,
  hasSignedOut,
  listenForSignOut,
  markSignedIn,
  markSignedOut,
} from './crossTabSignOut';

/** Every channel a case opened, closed in `afterEach` whether or not the case reached its end. */
let opened: Array<{ close(): void }> = [];

function channel(name: string): BroadcastChannel {
  const made = new BroadcastChannel(name);
  opened.push(made);
  return made;
}

/** A channel name unique per case, so two cases in one file never hear each other. */
let counter = 0;
function uniqueName(): string {
  counter += 1;
  return `${SIGN_OUT_CHANNEL}.test-${counter}-${Date.now()}`;
}

/** Waits for `received` to stop growing, with delivery observed rather than guessed at. */
async function quiet(): Promise<void> {
  for (let tick = 0; tick < 25; tick += 1) await new Promise((resolve) => setTimeout(resolve, 2));
}

let source: string | undefined;

/**
 * The module's own source with COMMENTS STRIPPED, which every scan below depends on.
 *
 * Written without the stripping first, and two scans failed immediately - on the header's prose, which
 * names `endSession()` and `accessToken.ts:82` while explaining why neither is reachable from here. A scan
 * that reads comments measures what a module SAYS instead of what it DOES, and in this module the comments
 * are long precisely because the reasoning is the deliverable. Same technique as
 * `publicRouteSetIsExactlyTheDeclaredSet.test.tsx`'s `source()`.
 */
async function moduleSource(): Promise<string> {
  source ??= (await readFile(join(dirname(expect.getState().testPath as string), 'crossTabSignOut.ts'), 'utf8'))
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .split('\n')
    .map((line) => {
      const at = line.indexOf('//');
      return at >= 0 && !/['"`]/.test(line.slice(0, at)) ? line.slice(0, at) : line;
    })
    .join('\n');
  return source;
}

beforeEach(() => {
  vi.restoreAllMocks();
  markSignedIn();
});

afterEach(() => {
  for (const item of opened) item.close();
  opened = [];
  markSignedIn();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('signOutPropagatesToAllTabs', () => {
  describe('AC1 a sign-out in one tab reaches a sibling', () => {
    it('runs the local action in the receiving tab', async () => {
      const name = uniqueName();
      const inbox = channel(name);
      const actingTab = channel(name);
      const cleared: string[] = [];
      opened.push(listenForSignOut(() => cleared.push('signed out'), { channel: inbox }));

      broadcastSignOut(actingTab);
      await quiet();

      expect(cleared, 'the sign-out never reached the sibling tab').toEqual(['signed out']);
    });

    it('reaches EVERY listening tab, not just the first', async () => {
      // "every open tab" is the requirement's own word, and one listener cannot measure it.
      const name = uniqueName();
      const heard: string[] = [];
      for (const tab of ['a', 'b', 'c']) {
        opened.push(listenForSignOut(() => heard.push(tab), { channel: channel(name) }));
      }
      broadcastSignOut(channel(name));
      await quiet();

      expect(heard.sort(), 'a sign-out reached some tabs and not others').toEqual(['a', 'b', 'c']);
    });
  });

  describe('AC2 the receiving tab does not echo', () => {
    it('puts exactly one message on the channel, counted by a third endpoint', async () => {
      // The structural argument is that a receiver signs out through `signOut()`, which broadcasts nothing.
      // This measures it: a receiver whose action re-broadcast would show as a second message here, and two
      // such tabs would ping-pong without bound.
      const name = uniqueName();
      const observer = channel(name);
      const seen: unknown[] = [];
      observer.addEventListener('message', (event) => void seen.push(event.data));

      // A receiver whose local action is the most echo-prone thing it could do short of a real loop.
      opened.push(listenForSignOut(() => markSignedOut(), { channel: channel(name) }));
      broadcastSignOut(channel(name));
      await quiet();

      expect(seen, 'more than one message crossed the sign-out channel for one sign-out').toHaveLength(1);
      expect(hasSignedOut(), 'the receiver did not act at all, so the count above proves nothing').toBe(true);
    });

    it('never calls a broadcast from signOut.ts, by source', async () => {
      const signOut = await readFile(
        join(dirname(dirname(expect.getState().testPath as string)), 'auth', 'signOut.ts'), 'utf8');
      expect(signOut, 'signOut() broadcasts, so every receiving tab would echo')
        .not.toMatch(/broadcastSignOut/);
      expect(signOut, 'signOut() no longer sets the latch at all').toMatch(/markSignedOut\(\)/);
    });
  });

  describe('AC3 the SAS session is ended once, by the acting tab (R19)', () => {
    it('names no identity path in the propagation module', async () => {
      const code = await moduleSource();
      // A receiving tab must clear LOCALLY. N tabs navigating to `/connect/logout` is N navigations for one
      // intent, and the end-session hand-over is task 36's and belongs to the tab the visitor acted in.
      for (const forbidden of [/connect\/logout/, /location\s*\.\s*assign/, /location\s*\.\s*replace/,
        /location\s*\.\s*href/, /window\.open/, /endSession/]) {
        expect(code, `crossTabSignOut.ts reaches for ${forbidden.source}`).not.toMatch(forbidden);
      }
    });

    it('CONTROL: that scan fires on a planted navigation', async () => {
      const code = await moduleSource();
      expect(/location\s*\.\s*assign/.test(`${code}\nglobalThis.location.assign('/connect/logout');`)).toBe(true);
      expect(/location\s*\.\s*assign/.test(code)).toBe(false);
    });
  });

  describe('AC4 the message carries nothing', () => {
    it('is exactly a type and no other property', async () => {
      const name = uniqueName();
      const observer = channel(name);
      const seen: unknown[] = [];
      observer.addEventListener('message', (event) => void seen.push(event.data));

      broadcastSignOut(channel(name));
      await quiet();

      expect(seen).toHaveLength(1);
      expect(seen[0], 'the sign-out message grew a field').toEqual({ type: SIGN_OUT_TYPE });
      expect(Object.keys(seen[0] as object), 'the sign-out message carries more than its type')
        .toEqual(['type']);
      expect(JSON.stringify(seen[0]), 'something token-shaped or identifying is in the message')
        .toBe('{"type":"session:signed-out"}');
    });

    it('spells no token, identifier or timestamp anywhere in the module', async () => {
      const code = await moduleSource();
      for (const forbidden of [/accessToken/, /idToken/, /Date\.now/, /performance\.now/, /crypto\./,
        /randomUUID/, /\btoken\b/]) {
        expect(code, `crossTabSignOut.ts names ${forbidden.source}`).not.toMatch(forbidden);
      }
    });
  });

  describe('AC5 propagation does not depend on Web Locks', () => {
    it('works with navigator.locks removed entirely', async () => {
      // The whole reason for a second channel. `crossTabRenewal.ts:78-86` builds ITS channel only when Web
      // Locks are present and degrades to no listener at all without them - acceptable when the cost is a
      // duplicate request, not when it is a sibling tab left signed in.
      vi.stubGlobal('navigator', { ...globalThis.navigator, locks: undefined });

      const name = uniqueName();
      const heard: string[] = [];
      opened.push(listenForSignOut(() => heard.push('signed out'), { channel: channel(name) }));
      broadcastSignOut(channel(name));
      await quiet();

      expect(heard, 'propagation stopped working without Web Locks').toEqual(['signed out']);
    });

    it('names no lock anywhere in the module, by source', async () => {
      const code = await moduleSource();
      expect(code, 'the propagation module reaches for Web Locks').not.toMatch(/navigator\s*\.\s*locks/);
      expect(code, 'the propagation module reaches for a lock request').not.toMatch(/\.request\s*\(/);
    });
  });

  describe('AC6 no BroadcastChannel at all is a no-op, not a throw', () => {
    it('broadcasts nothing and listens to nothing, and returns a callable close()', () => {
      const saved = globalThis.BroadcastChannel;
      try {
        // @ts-expect-error - removing the global is the condition under test.
        delete globalThis.BroadcastChannel;

        expect(() => broadcastSignOut(), 'broadcasting threw where the channel does not exist').not.toThrow();
        const listener = listenForSignOut(() => undefined);
        expect(() => listener.close(), 'close() threw on the degraded listener').not.toThrow();
        expect(() => listener.close(), 'close() is not idempotent on the degraded listener').not.toThrow();
      } finally {
        globalThis.BroadcastChannel = saved;
      }
      // Restored for every case after this one - a missing global would silently pass AC1 by never
      // delivering anything, so this is asserted rather than trusted.
      expect(typeof globalThis.BroadcastChannel, 'the global was not restored').toBe('function');
    });
  });

  describe('AC10 nothing is stored on any path (L16)', () => {
    it('touches no store while broadcasting, listening or latching', async () => {
      const watch = {
        getItem: vi.spyOn(Storage.prototype, 'getItem'),
        setItem: vi.spyOn(Storage.prototype, 'setItem'),
        removeItem: vi.spyOn(Storage.prototype, 'removeItem'),
        clear: vi.spyOn(Storage.prototype, 'clear'),
      };
      const name = uniqueName();
      opened.push(listenForSignOut(() => markSignedOut(), { channel: channel(name) }));
      broadcastSignOut(channel(name));
      markSignedOut();
      markSignedIn();
      await quiet();

      for (const [method, spy] of Object.entries(watch)) {
        expect(spy, `propagation called Storage.${method}`).not.toHaveBeenCalled();
      }
      expect(globalThis.sessionStorage.length + globalThis.localStorage.length).toBe(0);
    });

    it('names no store and no cookie in the module, by source', async () => {
      const code = await moduleSource();
      for (const forbidden of [/sessionStorage/, /localStorage/, /document\.cookie/, /indexedDB/,
        /console\./, /sendBeacon/, /document\.title/, /window\.name/, /history\.state/]) {
        expect(code, `crossTabSignOut.ts reaches for ${forbidden.source}`).not.toMatch(forbidden);
      }
    });
  });

  describe('AC11 only a sign-out is acted on, and extra properties are not grounds for refusal', () => {
    it('ignores every shape that is not a sign-out', async () => {
      const name = uniqueName();
      const heard: unknown[] = [];
      opened.push(listenForSignOut(() => heard.push('acted'), { channel: channel(name) }));
      const other = channel(name);

      for (const payload of [null, undefined, 0, '', 'session:signed-out', [], { type: 'renewal:failed' },
        { type: 'renewal:succeeded', token: 'x' }, { type: 'session:signed-in' }, { type: null },
        { kind: 'session:signed-out' }, { type: 'SESSION:SIGNED-OUT' }]) {
        other.postMessage(payload);
      }
      await quiet();

      expect(heard, 'a message that is not a sign-out signed this tab out').toEqual([]);
    });

    it('ACTS on a sign-out carrying an unknown extra field, deliberately', async () => {
      /*
       * The leniency is a DECISION, pinned so it reads as one (phase 4, rejecting Kimi F8's stricter half).
       * `BroadcastChannel` is same-origin, so what reaches this channel is not a hostile page - it is our own
       * code at a different version. A parser that refused unknown properties would make an old tab IGNORE a
       * future `{ type, reason }` sign-out, and an ignored sign-out is a tab that stays signed in, which is
       * what R22 forbids. The failure modes are asymmetric: acting on a slightly-unknown shape costs a
       * redundant local clear, refusing one costs a live session.
       */
      const name = uniqueName();
      const heard: unknown[] = [];
      opened.push(listenForSignOut(() => heard.push('acted'), { channel: channel(name) }));

      channel(name).postMessage({ type: SIGN_OUT_TYPE, reason: 'idle', version: 2 });
      await quiet();

      expect(heard, 'a sign-out from a newer version of this app was ignored').toEqual(['acted']);
    });
  });

  describe('AC12 close() stops the listening', () => {
    it('does nothing with a message that arrives after close', async () => {
      const name = uniqueName();
      const heard: unknown[] = [];
      const listener = listenForSignOut(() => heard.push('acted'), { channel: channel(name) });
      const other = channel(name);

      broadcastSignOut(other);
      await quiet();
      expect(heard, 'the listener never worked, so closing it proves nothing').toEqual(['acted']);

      listener.close();
      listener.close();                                  // idempotent
      broadcastSignOut(other);
      await quiet();

      expect(heard, 'a closed listener still acted on a sign-out').toEqual(['acted']);
    });

    it('leaves an INJECTED channel open for its owner to close', async () => {
      // The lifecycle rule: a channel this module created is closed by `close()`; one handed in belongs to
      // the caller. Closing someone else's channel would make the next post on it throw a DOMException.
      const name = uniqueName();
      const injected = channel(name);
      listenForSignOut(() => undefined, { channel: injected }).close();

      expect(() => injected.postMessage({ type: SIGN_OUT_TYPE }),
        'close() closed a channel it did not create, so its owner can no longer post').not.toThrow();
    });
  });

  describe('the scans are reading code, not prose', () => {
    it('CONTROL: stripping comments leaves the real declarations behind', async () => {
      // Every source scan in this file reads the stripped text. An over-eager strip would empty the module
      // and make all of them pass, so what must survive is asserted here once rather than in each scan.
      const code = await moduleSource();
      for (const needed of ['export const SIGN_OUT_CHANNEL', 'export function markSignedOut',
        'export function broadcastSignOut', 'export function listenForSignOut', 'let signedOut = false']) {
        expect(code, `stripping removed ${needed}, so every scan here is vacuous`).toContain(needed);
      }
      // And it really did strip: the header's prose is gone, which is what the two failing scans needed.
      expect(code, 'the comments were not stripped at all').not.toContain('OQ-T35-3');
    });
  });

  describe('AC13 the layering that makes the callback necessary', () => {
    it('imports nothing from auth/', async () => {
      const code = await moduleSource();
      // This is why `listenForSignOut` takes the local action as a PARAMETER. `api/` imports nothing from
      // `auth/`, and the latch is read by `crossTabRenewal.ts`, which is in `api/`. An import upward would
      // invert the direction the whole directory depends on.
      expect(code, 'the propagation module imports from auth/, inverting the layering')
        .not.toMatch(/from\s+['"][^'"]*\.\.\/auth\//);
      expect(code, 'the propagation module imports a screen or a token module')
        .not.toMatch(/from\s+['"]\.\.\/auth/);
    });

    it('CONTROL: that scan fires on a planted upward import', async () => {
      const code = await moduleSource();
      const planted = `import { signOut } from '../auth/signOut';\n${code}`;
      expect(/from\s+['"][^'"]*\.\.\/auth\//.test(planted)).toBe(true);
      expect(/from\s+['"][^'"]*\.\.\/auth\//.test(code)).toBe(false);
    });
  });

  describe('AC16 the module holds no channel of its own', () => {
    it('declares no BroadcastChannel at module scope', async () => {
      const code = await moduleSource();
      // A held channel would make the module stateful in a way nothing closes, and would leak one channel
      // per import. Every channel here is created inside a function and closed by it or by `close()`.
      expect(code, 'a BroadcastChannel is constructed at module scope')
        .not.toMatch(/^(?:const|let|var)\s+\w+\s*(?::[^=\n]*)?=\s*new\s+BroadcastChannel/m);
      // And exactly one module-scope mutable, which is the count task 35's NOT_SESSION_STATE excuses.
      const mutables = code.match(/^(?:let|var)\s/gm) ?? [];
      expect(mutables, 'the module grew a second piece of module-scope state').toHaveLength(1);
    });
  });
});
