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
import { RENEWAL_CHANNEL } from './crossTabRenewal';
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

/**
 * A FIXED BOUND, and the comment here used to deny that - it said "waits for `received` to stop growing,
 * with delivery observed rather than guessed at", naming a variable this file does not have (T39 F7, Kimi
 * phase 8 I7). Fifty milliseconds of ticks is a guess, which is fine for what it is used for and not fine
 * to misdescribe: the sentence a reader relies on when deciding whether a negative case needs its own
 * positive control was telling them it did not. Every case below that asserts something did NOT arrive now
 * ends by asserting that something DID, on the same channel, because a channel that delivers nothing
 * satisfies every negative assertion in this file. Task 35's `settle()` observes delivery before polling
 * and explains why; that technique is what this is not.
 */
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
function strip(text: string): string {
  return text
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .split('\n')
    .map((line) => {
      const at = line.indexOf('//');
      return at >= 0 && !/['"`]/.test(line.slice(0, at)) ? line.slice(0, at) : line;
    })
    .join('\n');
}

async function moduleSource(): Promise<string> {
  source ??= strip(
    await readFile(join(dirname(expect.getState().testPath as string), 'crossTabSignOut.ts'), 'utf8'));
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
      // STRIPPED, for the reason `moduleSource()` gives and for a second one this site has of its own
      // (T39 F4, Kimi phase 8 I4). `signOut.ts`'s header was rewritten in phase 9 and now NAMES
      // `markSignedOut()` in prose - so the second assertion below would pass on a comment while the call
      // itself had been deleted. A scan that reads prose can be satisfied by a file that does nothing.
      const signOut = strip(await readFile(
        join(dirname(dirname(expect.getState().testPath as string)), 'auth', 'signOut.ts'), 'utf8'));
      expect(signOut, 'signOut() broadcasts, so every receiving tab would echo')
        .not.toMatch(/broadcastSignOut/);
      expect(signOut, 'signOut() no longer sets the latch at all').toMatch(/markSignedOut\(\)/);
      // CONTROL: the strip kept the code and dropped the prose that would have satisfied the line above.
      expect(signOut, 'stripping removed the function this case reads').toContain('export function signOut()');
      expect(signOut, 'the header was not stripped, so the assertion above may be reading prose')
        .not.toContain('cross-tab');
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

    it('pins the channel NAME by literal, because nothing else in the repository does', () => {
      // Kimi phase 8 I8, half accepted. The TYPE is pinned three times already - the whole-message JSON
      // equality above, and twice in task 35's suite - so a rename of its value fails those. The CHANNEL
      // NAME was spelled in exactly one place in the whole repository: the production declaration. Every
      // test read the constant, so a rename passed everything.
      //
      // It matters for the reason the parser is lenient rather than strict: two tabs running different
      // versions of this app must agree on the channel, or a sign-out reaches nobody and R22 is broken by
      // a refactor. The literal is split so this case does not supply the string it is checking for.
      expect(SIGN_OUT_CHANNEL, 'the sign-out channel was renamed, so an older tab hears nothing')
        .toBe(['themistra', 'session', 'signout'].join('.'));
      expect(SIGN_OUT_TYPE, 'the sign-out message type was renamed')
        .toBe(['session', 'signed-out'].join(':'));
      // And it is not the renewal channel: two channels is the design, for the reason in AC5.
      expect(SIGN_OUT_CHANNEL).not.toBe(RENEWAL_CHANNEL);
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
        // WORDED AS WHAT IT MEASURES (T39 F12, Kimi phase 8 I14). This said "close() is not idempotent",
        // which it cannot show: the harness removed the `if (closed) return;` guard (XT18) and nothing
        // failed, because a double `removeEventListener` and a double `BroadcastChannel.close()` are both
        // no-ops by specification. The guard stays - it documents the intent and survives a `close()` that
        // one day does something observable - but the claim here is the one that is actually held.
        expect(() => listener.close(), 'a second close() on the degraded listener throws').not.toThrow();
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
      const acted: string[] = [];
      opened.push(listenForSignOut(() => { acted.push('received'); markSignedOut(); },
        { channel: channel(name) }));
      broadcastSignOut(channel(name));
      markSignedOut();
      markSignedIn();
      await quiet();

      // The same positive control AC11 needed, for the same reason: a listener that never fires touches no
      // store either, so "no store was touched" has to be measured over a delivery that happened.
      expect(acted, 'nothing was delivered, so the untouched store below proves nothing').toEqual(['received']);
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

      /*
       * THE POSITIVE CONTROL, INSIDE THE CASE (T39 F3, Kimi phase 8 I3), and it belongs inside because the
       * twelve refusals above are only evidence if this channel was live while they were refused.
       *
       * Measured before it existed: with `channel.addEventListener` replaced by a no-op, six cases in this
       * file failed and THIS ONE PASSED - a listener that hears nothing refuses everything. And measured
       * the other way, this case is the only case in all 1 720 tests that fails when the parser is made
       * always-true, so it is simultaneously the most load-bearing and the least self-verifying case here.
       * Now a real sign-out follows the twelve, on the same channel, through the same listener.
       */
      broadcastSignOut(other);
      await quiet();
      expect(heard, 'the channel was never live, so the twelve refusals above measured nothing')
        .toEqual(['acted']);
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
      listener.close();          // a second call throws nothing; see AC6 on why that is all it shows
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
      /*
       * EXACTLY ONE PIECE OF MODULE-SCOPE STATE, which is the count task 35's `NOT_SESSION_STATE` excuses.
       *
       * THE PATTERN WAS WIDENED, and the reason is worth keeping (Kimi phase 8 I10, which I rejected and
       * then reversed). It matched `^(?:let|var)\s` only. A mutant - XT32 - turned `SIGN_OUT_TYPE` into
       * `export const SIGN_OUT_MESSAGE = { … }` and SURVIVED all 1 720 tests. Task 35's shared scan missed
       * it too, for one anchor character: `MUTABLE_AT_MODULE_SCOPE` requires `^const`, so every EXPORTED
       * object, array or Set literal is invisible to it. Phase 6 caught the object form only because the
       * version written then was a bare `const`.
       *
       * A `const` binding to an object literal is not immutable - the binding is, the contents are not,
       * and `as const` is a compile-time claim - which is exactly why task 35's pattern includes `= {`
       * and `= [` in the first place. So the exported form is the same hazard wearing a keyword.
       *
       * TWO AXES, both measured holes in the inherited pattern:
       *
       *  - `^const` → `^(?:export\\s+)?const`. Fourteen exported module-scope object, array and Set
       *    literals in production source are invisible to the shared scan, among them `accessToken.ts:46`
       *    - the session object itself - and `api/client.ts:6`'s `new Set([…])`. Two non-exported lookup
       *    tables are caught and excused by name; the fourteen have never been looked at.
       *  - `new Set\\s*\\(` → `new Set\\s*(?:<[^>]*>)?\\s*\\(`. A type argument on the constructor defeats
       *    it. `pwa/registerServiceWorker.ts` is excused with a PINNED `declarations: 2` and holds THREE
       *    pieces of module-scope state: two `let`s and `const listeners = new Set<UpdateListener>()`.
       *    The pin says two because the third is unseen, not because it is not there.
       *
       * Widened HERE and not in task 35's shared pattern, which is read by task 22's accounting and task
       * 36's two cases. Changing it would change what fourteen files in four closed tasks are measured
       * against and would turn an excused file's pinned count false - decisions about those tasks, not
       * about T39. FU-T39-1.
       */
      const MUTABLE = '^(?:let|var)\\s|^(?:export\\s+)?const\\s+\\w+\\s*(?::[^=\\n]*)?='
        + '\\s*(?:new\\s+(?:Map|Set|WeakMap|WeakSet)\\s*(?:<[^>]*>)?\\s*\\(|\\[|\\{)';
      const mutables = code.match(new RegExp(MUTABLE, 'gm')) ?? [];
      expect(mutables, 'the module grew a second piece of module-scope state').toHaveLength(1);
      expect(mutables[0], 'the one declaration found is not the latch').toMatch(/^let\s/);

      /*
       * CONTROL: the widened half fires, planted both ways round, and the narrow case does not.
       *
       * A FRESH RegExp PER PROBE, because the first version of this control shared one `/gm` regex with
       * the match above and reported FALSE for a string it matches: `.test()` on a global regex advances
       * `lastIndex`, so the second probe started reading part-way through the third. The control caught a
       * defect in the control, which is the only reason it was not shipped as a passing scan that had
       * stopped looking.
       */
      const fires = (line: string) => new RegExp(MUTABLE, 'm').test(line);
      expect(fires('export const MESSAGE = { type: \'x\' } as const;'),
        'the widened half does not fire on an exported object, so XT32 would survive again').toBe(true);
      expect(fires('export const PATHS = [\'a\'] as const;')).toBe(true);
      expect(fires('export const SEEN = new Set<string>();')).toBe(true);
      expect(fires('const LOOKUP: Record<string, string> = {')).toBe(true);
      expect(fires('let signedOut = false;')).toBe(true);
      expect(fires("export const SIGN_OUT_TYPE = 'session:signed-out';"),
        'the pattern now fires on a plain string constant, so the count above means nothing').toBe(false);
      expect(fires("export function broadcastSignOut(channel?: BroadcastChannel): void {")).toBe(false);
    });
  });
});
