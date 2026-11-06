/**
 * R19 clause 1 — sign-out clears in-memory token state. **Task 35 owns this half only.**
 *
 * The file carries the name `tasks.md:63` and `:64` both give, because both tasks verify one named test. Clause
 * 2 - ending the SAS session through `/connect/logout` with an allowlisted `post_logout_redirect_uri` - is
 * TASK 36, and there is nothing about it here yet. The describe blocks are one per acceptance criterion, in
 * criterion order, so task 36 appends its own rather than restructuring these.
 *
 * `.tsx` with no JSX in it, deliberately: task 36 extends this file with navigation cases, and renaming a test
 * file later is churn for no gain.
 *
 * WHAT THIS SUITE CANNOT PROVE. Nothing in the SPA calls `signOut`, and nothing can - no authenticated route
 * has an element, `AppShell.tsx` renders a bare `<div>`, and no task owns a sign-out affordance. Every
 * behavioural case below is therefore a statement about how the function behaves WHEN CALLED, not that it is.
 * There is no dormancy canary here, and that is a deliberate departure from task 33's AC12: that canary failed
 * on wiring because its message carried an INSTRUCTION the author needed - pass this handler as that callback -
 * whereas wiring sign-out needs nothing but calling it, so the same test here would only obstruct the person
 * doing the right thing. AC10 guards the shape of the wiring instead: no other module may call the clearing
 * primitives, so a partial sign-out hand-rolled beside this one fails rather than ships. (OQ-T35-2.)
 *
 * NO KEY IS SPELLED IN THIS FILE. Every needle is built at runtime from the imported production constants, so a
 * rename breaks these cases instead of passing them. `themistra.` appears as a bare PREFIX for AC8's scan, and
 * AC8 proves no whole key appears.
 */
import { readdir, readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { RENEWAL_CHANNEL, RENEWAL_LOCK, shareRenewalAcrossTabs, type TabLocks } from '../api/crossTabRenewal';
import { accessToken, clearAccessToken, retainAccessToken } from './accessToken';
import * as authorizeRequestModule from './authorizeRequest';
import { AUTHORIZE_STORAGE_KEYS, buildAuthorizeRequest } from './authorizeRequest';
import { RETURN_TARGET_KEY, rememberReturnTarget } from './returnTarget';
import * as returnTargetModule from './returnTarget';
import * as signInNoticeModule from './signInNotice';
import { setSignInNotice, takeSignInNotice } from './signInNotice';
import { signOut } from './signOut';

/** A key prefix, not a key: AC8 scans for it and also proves no whole key is spelled here. */
const KEY_PREFIX = 'themistra.';

/**
 * A token nobody could mistake for real, and distinct per use so a stale assertion cannot pass.
 *
 * The counter lives in a closure rather than at module scope (Kimi phase 8 I7): AC7 scans production files
 * only, so a module-scope `let` here fails nothing - and a suite that enforces a rule it does not itself
 * follow invites the reader to discount the rule.
 */
const aToken = (() => {
  let sequence = 0;
  return () => `T35-access-token-${(sequence += 1)}`;
})();

/* ─────────────────────────── source scanning ─────────────────────────── */

/**
 * Comments removed before any scan, fourth copy in this package; the original is
 * `api/parallel401sTriggerExactlyOneRenewal.test.ts`'s and task 33 copied it third. Said out loud rather than
 * quietly re-typed: this is the duplication OQ-T32-4 tracks, and a shared helper is the fix when someone is
 * allowed to touch the closed suites that hold the other copies.
 */
function strip(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .split('\n')
    .map((line) => {
      const at = line.indexOf('//');
      return at >= 0 && !/['"`]/.test(line.slice(0, at)) ? line.slice(0, at) : line;
    })
    .join('\n');
}

let productionFiles: Array<{ path: string; code: string }> | undefined;

/** Every production `.ts`/`.tsx` under `src/`, stripped of comments, read once per run. */
async function production(): Promise<Array<{ path: string; code: string }>> {
  if (productionFiles) return productionFiles;
  const srcRoot = dirname(dirname(expect.getState().testPath as string));
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
  expect(files.length, 'the scan read no production files, so every scan below would pass vacuously')
    .toBeGreaterThan(20);
  productionFiles = await Promise.all(
    files.map(async (path) => ({ path: path.slice(srcRoot.length + 1), code: strip(await readFile(path, 'utf8')) })),
  );
  return productionFiles;
}

/* ─────────────────────────── the inventory ─────────────────────────── */

/**
 * THE STATE INVENTORY, AND WHAT IT IS NOT.
 *
 * It is NOT a second opinion on L16. Task 22's `sessionStorageHoldsOnlyTheL16Items` owns that question: it
 * derives the four permitted keys from these same two production constants, pins the carve-out at four with a
 * message naming clause (b) as the next entrant, and keeps a `PERMITTED_WRITERS` table of which module may call
 * which storage method on which key. Duplicating it here would be the helper debt OQ-T32-4 already tracks, and
 * worse, a second derivation free to drift from the first.
 *
 * This table answers the question task 35 does own: OF THE STATE THAT EXISTS, DOES SIGN-OUT CLEAR EACH PIECE?
 * So each row names a piece of state, the module that owns it, and the primitive that clears it. Keys come from
 * the imported constants - never a literal - so a rename breaks these cases rather than passing them.
 */
const INVENTORY = [
  {
    what: 'the held access token',
    owner: 'auth/accessToken.ts',
    clearedBy: 'clearAccessToken',
    key: null,
  },
  {
    what: 'the pending sign-in notice',
    owner: 'auth/signInNotice.ts',
    clearedBy: 'takeSignInNotice',
    key: null,
  },
  { what: 'the authorize state', owner: 'auth/authorizeRequest.ts', clearedBy: 'consumeAuthorizeState', key: AUTHORIZE_STORAGE_KEYS.state },
  { what: 'the authorize nonce', owner: 'auth/authorizeRequest.ts', clearedBy: 'consumeAuthorizeState', key: AUTHORIZE_STORAGE_KEYS.nonce },
  { what: 'the PKCE code verifier', owner: 'auth/authorizeRequest.ts', clearedBy: 'consumeAuthorizeState', key: AUTHORIZE_STORAGE_KEYS.codeVerifier },
  { what: 'the post-login return target', owner: 'auth/returnTarget.ts', clearedBy: 'clearReturnTarget', key: RETURN_TARGET_KEY },
] as const;

const inventoryKeys = (): string[] =>
  INVENTORY.flatMap((row): string[] => (row.key === null ? [] : [row.key]));

/**
 * `themistra.`-prefixed literals that are NOT storage keys, so AC8's scan can tell them apart with a reason
 * rather than a guess. Both are names on the wire, not values in a store: nothing is persisted under either,
 * and sign-out has nothing to remove for them.
 */
const NOT_STORAGE = [
  { value: RENEWAL_CHANNEL, why: 'a BroadcastChannel name (L18), not a stored value' },
  { value: RENEWAL_LOCK, why: 'a Web Locks name (L18), not a stored value' },
] as const;

/**
 * L16 clause (b)'s mid-task form input, pre-authorized so the day it lands this scan does not fail for the
 * wrong reason. Kimi phase 3 F7 asked for the pre-authorization; the ORDER matters and is the correction:
 * task 22's count pin fails FIRST, because task 22 owns the carve-out's growth, and only then is it task 26's
 * decision whether a DELIBERATE sign-out should destroy a visitor's in-progress form. Task 35 does not presume
 * that answer - a 15-minute non-sensitive draft is not token state, and R19 says nothing about it.
 */
const CLAUSE_B_HANDOFF =
  "L16 clause (b)'s mid-task form input (task 26) is not in this inventory and is not sign-out's to clear " +
  'until task 26 decides it is. Task 22 pins the carve-out at four and owns that growth event.';

/**
 * Who may call each clearing primitive, measured from source on 2026-11-06 rather than imagined.
 *
 * The point is not to freeze the callers - it is that a SECOND, partial sign-out assembled next to this one
 * fails a test instead of shipping. That is the failure mode that actually threatens R19: three of the four
 * calls made somewhere else, and a tab that looks signed out while still holding something.
 */
const PERMITTED_CALLERS: Readonly<Record<string, readonly string[]>> = {
  clearAccessToken: ['auth/cleanReauth.ts', 'auth/CallbackScreen.tsx', 'auth/signOut.ts'],
  takeSignInNotice: ['auth/SignInScreen.tsx', 'auth/signOut.ts'],
  consumeAuthorizeState: ['auth/CallbackScreen.tsx', 'auth/signOut.ts'],
  takeReturnTarget: ['auth/CallbackScreen.tsx'],
  clearReturnTarget: ['auth/signOut.ts'],
};

/** The modules that DECLARE the primitives; excluded by path, because a declaration is not a call. */
const DEFINERS = ['auth/accessToken.ts', 'auth/signInNotice.ts', 'auth/authorizeRequest.ts', 'auth/returnTarget.ts'];

/**
 * Module-scope mutable declarations that sign-out does NOT clear, each with its reason.
 *
 * Narrowed to module-scope declarations after Kimi phase 3 F8: state held in a factory closure
 * (`crossTabRenewal.ts`, `sessionBoundary.ts`'s pending map) is unreachable from a function that takes no
 * arguments, so a criterion promising to cover it would be promising what it cannot do. That limitation is not
 * hypothetical - it is AC9's hazard, pinned below.
 */
const NOT_SESSION_STATE = [
  {
    file: 'profile/readAccount.ts',
    declarations: 1,
    why:
      'a Record of failure copy keyed by a closed union, declared once and never assigned to at runtime. ' +
      "Caught because the scan reaches object literals (phase 11 F3) and this one is not `as const`; it is " +
      "a message table, not the visitor's state, and clearing it would leave the module unable to speak.",
  },
  {
    file: 'profile/readProfile.ts',
    declarations: 1,
    why:
      'the same message table for the profile read, excused for the same reason. Two entries rather than ' +
      'one pattern, because an exclusion that matches by shape would excuse the next real piece of state ' +
      'that happens to be shaped like a lookup table.',
  },
  {
    file: 'pwa/registerServiceWorker.ts',
    // How many module-scope declarations this entry excuses. Pinned because the frozen criterion says the
    // list holds "registerServiceWorker.ts's TWO PWA entries and nothing else", and excusing a FILE excuses
    // every declaration that ever appears in it - so a third one added later would be waved through in
    // silence. The count is what makes the exclusion specific rather than a blanket.
    declarations: 2,
    why:
      'the service-worker registration promise and its waiting-update flag. Not session state and not the ' +
      "visitor's: a sign-out that dropped them would re-register a worker and re-prompt for an update that is " +
      'already waiting.',
  },
] as const;

/* ─────────────────────────── storage and console watches ─────────────────────────── */

/**
 * Mutable state at MODULE scope, in the three shapes it can take here.
 *
 * `let`/`var` is the obvious one. A `const` holding a container (`new Map()`, an array) is the one Kimi
 * phase 8 I4 added. A `const` holding an object literal is the one phase 11 F3 added - and unlike the other
 * two, that shape DOES exist in production, so it comes with two excused entries below rather than passing
 * for free. `as const` is not exempted by the pattern: TypeScript makes such a value deeply readonly, so
 * mutating it is a compile error, but the exemption would have to be a second regex and the two real cases
 * are not `as const` anyway.
 *
 * Hoisted to module scope in this file so the scan and its control cannot drift apart - they were two copies
 * of the same literal for one commit, which is the bug this instrument exists to catch in other files.
 */
const MUTABLE_AT_MODULE_SCOPE =
  /^(?:let|var)\s|^const\s+\w+\s*(?::[^=\n]*)?=\s*(?:new\s+(?:Map|Set|WeakMap|WeakSet)\s*\(|\[|\{)/m;

type StorageWatch = { getItem: Mock; setItem: Mock; removeItem: Mock; clear: Mock };

/**
 * Spies the storage PROTOTYPE, not a store object: jsdom's `sessionStorage` is proxied, so an own-property
 * `vi.spyOn` on it never intercepts - task 33 measured that one the hard way. The prototype is also alias-proof,
 * which is why task 22 chose it; this is the same technique, named rather than reinvented.
 */
function watchStorage(): StorageWatch {
  return {
    getItem: vi.spyOn(Storage.prototype, 'getItem') as unknown as Mock,
    setItem: vi.spyOn(Storage.prototype, 'setItem') as unknown as Mock,
    removeItem: vi.spyOn(Storage.prototype, 'removeItem') as unknown as Mock,
    clear: vi.spyOn(Storage.prototype, 'clear') as unknown as Mock,
  };
}

const keysOf = (spy: Mock): string[] => (spy.mock.calls as Array<[string]>).map(([key]) => key);

const CONSOLE_CHANNELS = ['log', 'info', 'warn', 'error', 'debug', 'trace'] as const;

function watchConsole(): Mock[] {
  return CONSOLE_CHANNELS.map((channel) => vi.spyOn(console, channel).mockImplementation(() => undefined) as unknown as Mock);
}

/** A store that refuses every operation, for AC12. */
function denyStorage(): void {
  for (const method of ['getItem', 'setItem', 'removeItem', 'clear'] as const) {
    vi.spyOn(Storage.prototype, method).mockImplementation(() => {
      throw new DOMException('denied', 'SecurityError');
    });
  }
}

/* ─────────────────────────── the suite ─────────────────────────── */

describe('signOutClearsMemoryAndEndsSasSession', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    globalThis.sessionStorage.clear();
    globalThis.localStorage.clear();
    clearAccessToken();
    takeSignInNotice();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    clearAccessToken();
    takeSignInNotice();
  });

  describe('AC1 no token is held afterwards', () => {
    it('drops a token this tab exchanged for itself', () => {
      retainAccessToken(aToken());
      expect(accessToken.current(), 'the precondition: a token really was held').not.toBeNull();
      signOut();
      expect(accessToken.current()).toBeNull();
    });

    /**
     * THE ORDER, measured rather than claimed. This case exists because a mutant that moved the clear to
     * LAST survived the suite as first written: `signOut.ts` says in so many words that the position is
     * load-bearing, AC1 asserted the outcome, and nothing asserted the sequence. Designing the mutant found
     * the hole; re-reading the cases had not.
     *
     * The step made to throw is `consumeAuthorizeState`, because in real life none of the three later steps
     * can - each is guarded inside its own module - which is exactly why the gap was invisible. `signOut`
     * has no `catch`, so the throw escapes, and the assertion is about what is already true when it does.
     * Spying the module object works because Vitest keeps a named import as a live binding (task 33
     * measured this after an own-property spy on jsdom's proxied `Storage` silently intercepted nothing).
     */
    it('clears FIRST, proven by a later step that throws', () => {
      retainAccessToken(aToken());
      vi.spyOn(authorizeRequestModule, 'consumeAuthorizeState').mockImplementation(() => {
        throw new Error('the store died mid-sign-out');
      });

      expect(() => signOut()).toThrow('the store died mid-sign-out');

      expect(
        accessToken.current(),
        'a step after the clear threw and the token was still held: that is the half-authenticated tab R22 '
          + 'forbids. clearAccessToken() must be the FIRST statement in signOut().',
      ).toBeNull();
    });

    /**
     * KIMI PHASE 8 I9 ASKED FOR THE OPPOSITE OF THIS CASE, and the opposite is false.
     *
     * It recommended spying `takeSignInNotice` to throw and asserting the storage primitives are "still
     * reached". They are not: `signOut` has no try/catch, so a throw from step 2 abandons steps 3 and 4. That
     * test would have been added broken. The finding still pointed at something real - the module's comment
     * implied a resilience the code does not have - so the comment is corrected and the TRUE behaviour is
     * pinned here instead.
     *
     * And this is why the order matters rather than being a style choice. When a later step fails, what is
     * left behind is residue in a store; what is NOT left behind is a token. That asymmetry is the whole
     * argument for clearing first, and it is now measured from both ends.
     */
    it('and when a later step throws, the rest are abandoned - leaving residue, never a token', () => {
      retainAccessToken(aToken());
      rememberReturnTarget('/account');
      vi.spyOn(signInNoticeModule, 'takeSignInNotice').mockImplementation(() => {
        throw new Error('step two died');
      });
      const consumed = vi.spyOn(authorizeRequestModule, 'consumeAuthorizeState');
      const cleared = vi.spyOn(returnTargetModule, 'clearReturnTarget');

      expect(() => signOut()).toThrow('step two died');

      expect(accessToken.current(), 'the token went first and is gone').toBeNull();
      expect(consumed, 'step three was abandoned, which is the honest behaviour').not.toHaveBeenCalled();
      expect(cleared, 'and so was step four').not.toHaveBeenCalled();
      expect(globalThis.sessionStorage.getItem(RETURN_TARGET_KEY), 'residue survives, and residue is not a session')
        .toBe('/account');
    });

    it('and its source puts the clear before the other three (source)', async () => {
      const self = (await production()).find((file) => file.path === 'auth/signOut.ts');
      const body = self!.code.slice(self!.code.indexOf('export function signOut'));
      const at = (primitive: string) => body.search(new RegExp(`\\b${primitive}\\s*\\(`));
      for (const later of ['takeSignInNotice', 'consumeAuthorizeState', 'clearReturnTarget']) {
        expect(at('clearAccessToken'), `${later} is called before the token is cleared`).toBeLessThan(at(later));
      }
      // The cheap pin beside the behavioural one, and not instead of it: this one would still pass if the
      // clear were moved into a branch that does not run.
      expect(at('clearAccessToken'), 'the scan found no call at all').toBeGreaterThan(-1);
    });

    it('drops a token ADOPTED from a sibling tab, which is written by a different path', () => {
      // `adopt` is the cross-tab contract's entry point (L18) and delegates to `retainAccessToken`. Measured
      // rather than assumed, because "the only place a non-null token is assigned" is a claim in a comment.
      accessToken.adopt(aToken());
      expect(accessToken.current()).not.toBeNull();
      signOut();
      expect(accessToken.current()).toBeNull();
    });
  });

  describe('AC2 it is idempotent', () => {
    it('is harmless run twice, and the second call leaves the state the first did', async () => {
      await buildAuthorizeRequest();
      rememberReturnTarget('/account');
      setSignInNotice('session-ended');
      retainAccessToken(aToken());

      signOut();
      // The whole observable state after one call, captured rather than asserted piecemeal: the criterion
      // says both calls leave the SAME state, which is a comparison and not a list of nulls.
      const afterOnce = { token: accessToken.current(), keys: Object.keys(globalThis.sessionStorage).sort() };

      expect(() => signOut()).not.toThrow();

      expect({ token: accessToken.current(), keys: Object.keys(globalThis.sessionStorage).sort() })
        .toEqual(afterOnce);
      expect(afterOnce, 'and the state they agree on is the cleared one').toEqual({ token: null, keys: [] });
    });

    it('is harmless in a cold tab that never signed in - the state task 39 will call it from', () => {
      expect(accessToken.current(), 'the precondition: nothing held').toBeNull();
      expect(() => signOut()).not.toThrow();
      expect(accessToken.current()).toBeNull();
      expect(globalThis.sessionStorage.length, 'and it invented nothing to clear').toBe(0);
    });
  });

  describe('AC3 no sign-in residue survives', () => {
    it('leaves no pending notice, so a deliberate leaver is not told their session ended', () => {
      // The realistic way this gets set: `cleanReauth` queued it, then the visitor signed out instead.
      setSignInNotice('session-ended');
      signOut();
      expect(takeSignInNotice()).toBeNull();
    });

    it('leaves no return target, so the NEXT sign-in is not sent where this visitor was', () => {
      rememberReturnTarget('/account');
      expect(globalThis.sessionStorage.getItem(RETURN_TARGET_KEY), 'the precondition').toBe('/account');
      signOut();
      expect(globalThis.sessionStorage.getItem(RETURN_TARGET_KEY)).toBeNull();
    });

    it('leaves none of the three authorize items, from a genuinely mid-flight sign-in', async () => {
      // Built by the real authorize step, not by writing three keys by hand: a sign-out during a second
      // sign-in attempt is the state this clause exists for, and a hand-made triple would not prove the
      // production writer and this reader agree on the key names.
      await buildAuthorizeRequest();
      for (const key of Object.values(AUTHORIZE_STORAGE_KEYS)) {
        expect(globalThis.sessionStorage.getItem(key), `the precondition: ${key} was written`).not.toBeNull();
      }
      retainAccessToken(aToken());
      signOut();
      expect(globalThis.sessionStorage.length, 'the whole store is empty, all four keys gone').toBe(0);
      expect(accessToken.current()).toBeNull();
    });
  });

  describe('AC4 it is synchronous and complete on return', () => {
    it('returns undefined rather than a promise, and the state is already clear on the next line', () => {
      retainAccessToken(aToken());
      rememberReturnTarget('/account');
      const returned: unknown = signOut();
      // Asserted on the very next statement, with no await and no tick: task 36 follows this call with a
      // full-page navigation to `/connect/logout` (R57), and a full-page navigation waits for no microtask.
      expect(accessToken.current()).toBeNull();
      expect(globalThis.sessionStorage.length).toBe(0);
      // `undefined` and not merely "not a promise": a thenable has properties and this has none, so the one
      // assertion forecloses both. A `.not.toHaveProperty('then')` beside it cannot even run on undefined.
      expect(returned).toBeUndefined();
    });

    it('defers nothing: its source has no async, await, timer or microtask (source)', async () => {
      const self = (await production()).find((file) => file.path === 'auth/signOut.ts');
      expect(self, 'auth/signOut.ts is missing from the scan').toBeDefined();
      // `Promise` itself is NOT banned (Kimi phase 8 I10): `Promise.resolve()` defers nothing, and a ban that
      // wide would block a legitimate future change while catching nothing this list misses.
      for (const deferral of [/\basync\b/, /\bawait\b/, /setTimeout\s*\(/, /setInterval\s*\(/, /queueMicrotask\s*\(/, /requestAnimationFrame\s*\(/, /\.then\s*\(/]) {
        expect(self!.code, `signOut defers work via ${deferral.source}`).not.toMatch(deferral);
      }
    });
  });

  describe('AC5 it takes nothing and returns nothing', () => {
    it('declares zero parameters, so there is no reason or outcome to branch on', () => {
      // A type-level guarantee as well as a runtime one: `() => void` cannot receive a failure reason, which is
      // what makes L4's indifference a property of the signature rather than a discipline.
      expect(signOut.length).toBe(0);
      // There is no runtime case for "it ignores an argument", and that is the point: `signOut(undefined)`
      // is a COMPILE error here ("Expected 0 arguments, but got 1"), measured while writing this case. So the
      // guarantee is stronger than a test - a caller cannot even express the reason it would want to pass.
      expect(signOut()).toBeUndefined();
    });
  });

  describe('AC6 exactly four removals, and nothing else is touched', () => {
    it('removes the four inventory keys and performs no read, write or clear', async () => {
      await buildAuthorizeRequest();
      rememberReturnTarget('/account');
      retainAccessToken(aToken());
      // Installed AFTER the setup and BEFORE the call, so the counts are sign-out's alone. Task 22 measured a
      // watch installed one line too late reporting zero writes for a flow that had just made three.
      const watch = watchStorage();
      const channels = watchConsole();

      signOut();

      // WHICH STORE, not only which keys (Kimi phase 11 F8). `watchStorage` spies `Storage.prototype`, which
      // `sessionStorage` and `localStorage` SHARE - so a removal aimed at the wrong store would have matched
      // the key list and passed. The receivers are recorded alongside the keys, and localStorage is asserted
      // untouched from its own side as well.
      const receivers = watch.removeItem.mock.instances as unknown[];
      expect(keysOf(watch.removeItem).sort(), 'the four inventory keys, once each').toEqual(inventoryKeys().sort());
      expect(
        receivers.every((receiver) => receiver === globalThis.sessionStorage),
        'a removal was aimed at a store other than sessionStorage',
      ).toBe(true);
      expect(globalThis.localStorage.length, 'localStorage was touched').toBe(0);
      expect(watch.removeItem.mock.calls, 'four removals, not five').toHaveLength(4);
      expect(watch.getItem, 'sign-out reads nothing - takeReturnTarget would have added a getItem').not.toHaveBeenCalled();
      expect(watch.setItem, 'sign-out writes nothing').not.toHaveBeenCalled();
      expect(
        watch.clear,
        "clear() would wipe the other half of L16's carve-out (clause (b), task 26) as well as these four",
      ).not.toHaveBeenCalled();
      for (const [index, channel] of channels.entries()) {
        expect(channel, `console.${CONSOLE_CHANNELS[index]} was written to`).not.toHaveBeenCalled();
      }
    });

    it('CONTROL: the same watch sees a removal, a read, a write and a clear when they happen', () => {
      const watch = watchStorage();
      globalThis.sessionStorage.setItem(`${KEY_PREFIX}probe.sentinel`, '1');
      globalThis.sessionStorage.getItem(`${KEY_PREFIX}probe.sentinel`);
      globalThis.sessionStorage.removeItem(`${KEY_PREFIX}probe.sentinel`);
      globalThis.sessionStorage.clear();
      expect(watch.setItem).toHaveBeenCalled();
      expect(watch.getItem).toHaveBeenCalled();
      expect(watch.removeItem).toHaveBeenCalled();
      expect(watch.clear).toHaveBeenCalled();
    });

    it('CONTROL: the console watch sees every one of the six channels', () => {
      const channels = watchConsole();
      for (const channel of CONSOLE_CHANNELS) console[channel]('probe');
      for (const [index, channel] of channels.entries()) {
        expect(channel, `console.${CONSOLE_CHANNELS[index]} went unwatched`).toHaveBeenCalled();
      }
    });
  });

  describe('AC7 every module-scope mutable is cleared or excluded with a reason', () => {
    it('finds mutable state only where sign-out clears it or this list excuses it', async () => {
      const files = await production();
      // `let`/`var`, AND a top-level `const` holding a mutable container - Kimi phase 8 I4 is right that a
      // `const cache = new Map()` is tab-lifetime mutable state that `let` alone would miss. Measured before
      // choosing: no production module has one today, so the scan is EXTENDED rather than the claim narrowed.
      const holders = files.filter((file) => MUTABLE_AT_MODULE_SCOPE.test(file.code)).map((file) => file.path);
      const cleared = INVENTORY.filter((row) => row.key === null).map((row) => row.owner);
      const excused = NOT_SESSION_STATE.map((entry) => entry.file);

      expect(holders.sort(), 'a module holds mutable state that sign-out neither clears nor excuses. Either '
        + 'clear it in signOut.ts, or add it to NOT_SESSION_STATE with a reason.')
        .toEqual([...cleared, ...excused].sort());

      for (const entry of NOT_SESSION_STATE) {
        expect(entry.why.length, `${entry.file} is excused without a reason`).toBeGreaterThan(60);
        const excused = files.find((file) => file.path === entry.file);
        expect(excused, `${entry.file} is excused but is not in the scan`).toBeDefined();
        expect(
          (excused!.code.match(new RegExp(MUTABLE_AT_MODULE_SCOPE.source, 'gm')) ?? []).length,
          `${entry.file} now holds a different number of module-scope mutables than this entry excuses. `
            + 'Excusing a file excuses every declaration in it; say which, and why the new one is also not '
            + "the visitor's session state.",
        ).toBe(entry.declarations);
      }
      // The limitation, stated rather than implied: closure-held state is not module-scope and not reachable
      // from a zero-argument function. AC9 is what that limitation costs.
      expect(files.some((file) => file.path === 'api/crossTabRenewal.ts' && !MUTABLE_AT_MODULE_SCOPE.test(file.code)),
        'crossTabRenewal holds its state in a factory closure, which this scan cannot see - see AC9')
        .toBe(true);
    });

    it('CONTROL: a module-scope mutable in a scanned file is caught', async () => {
      const files = await production();
      // Every alternative the pattern has, because a control that exercises one branch of three is a control
      // for a third of a scan.
      for (const planted of [
        'let leaked = 1;',
        'var older = 1;',
        'const cache = new Map();',
        'const seen: string[] = [];',
        'const state = { count: 0 };',
      ]) {
        const files2 = files.map((file) =>
          file.path === 'app/AppShell.tsx' ? { ...file, code: `${planted}\n${file.code}` } : file,
        );
        const holders = files2.filter((file) => MUTABLE_AT_MODULE_SCOPE.test(file.code)).map((file) => file.path);
        expect(holders, `the planted \`${planted}\` went unseen`).toContain('app/AppShell.tsx');
      }
    });
  });

  describe('AC8 the inventory is derived, and no key escapes it', () => {
    it('names an owner and a clearing primitive for every piece of state', () => {
      expect(INVENTORY).toHaveLength(6);
      expect(inventoryKeys()).toHaveLength(4);
      expect(new Set(inventoryKeys()).size, 'four distinct keys, not one counted twice').toBe(4);
      for (const row of INVENTORY) {
        expect(row.owner, `${row.what} names no owning module`).toMatch(/^(auth|api|app|pwa|profile)\/\w+\.tsx?$/);
        expect(Object.keys(PERMITTED_CALLERS), `${row.what}'s primitive is not in the caller allowlist`)
          .toContain(row.clearedBy);
      }
    });

    it('spells no whole key of its own, so a rename breaks it rather than passing it', async () => {
      const ownSource = await readFile(expect.getState().testPath as string, 'utf8');
      for (const key of inventoryKeys()) {
        expect(ownSource, `${key} is spelled out in this file; import the constant instead`).not.toContain(key);
      }
      expect(ownSource, 'the control: the bare prefix IS in this file, which is how AC8 scans').toContain(KEY_PREFIX);
    });

    it('accounts for every themistra. literal in production source', async () => {
      const files = await production();
      const found = new Set<string>();
      for (const file of files) {
        for (const match of file.code.matchAll(new RegExp(`['"\`](${KEY_PREFIX}[\\w.$-]*)['"\`]`, 'g'))) {
          found.add(match[1]);
        }
      }
      const accounted = new Set([...inventoryKeys(), ...NOT_STORAGE.map((entry) => entry.value)]);
      expect(
        [...found].filter((literal) => !accounted.has(literal)),
        `a client-side key exists that sign-out does not clear and nothing excuses. ${CLAUSE_B_HANDOFF}`,
      ).toEqual([]);
      // Both directions: an inventory key that no longer appears in production is a stale row, not a pass.
      expect([...found].sort(), 'the scan found fewer literals than the inventory claims')
        .toEqual([...accounted].sort());
      for (const entry of NOT_STORAGE) {
        expect(entry.why, `${entry.value} is excused without a reason`).toMatch(/not a stored value/);
      }
    });

    /**
     * The scan above reads LITERALS, so a key assembled at runtime would slip past it - Kimi phase 8 I5.
     *
     * Rather than document the hole, forbid the construction: every client-side key in this package is a
     * module constant today (measured - no production file concatenates or interpolates one), so a dynamic
     * key is a new practice and should arrive as a deliberate decision rather than as a silent gap in this
     * scan. The ban is narrow: it catches `themistra.` inside a template literal or either side of a `+`.
     */
    it('and no production module BUILDS a key, so the literal scan cannot be side-stepped', async () => {
      const files = await production();
      const DYNAMIC = [
        new RegExp(`\`[^\`]*${KEY_PREFIX.replace('.', '\\.')}[^\`]*\\$\\{`),
        new RegExp(`['"]${KEY_PREFIX.replace('.', '\\.')}[\\w.$-]*['"]\\s*\\+`),
        new RegExp(`\\+\\s*['"]${KEY_PREFIX.replace('.', '\\.')}`),
      ];
      const builders: string[] = [];
      for (const file of files) {
        for (const pattern of DYNAMIC) if (pattern.test(file.code)) builders.push(file.path);
      }
      expect(
        [...new Set(builders)],
        'a client-side key is assembled rather than declared. The inventory scan above reads literals, so it '
          + 'cannot see this one. Declare it as a module constant, or decide deliberately that dynamic keys '
          + 'are permitted and give this scan a way to enumerate them.',
      ).toEqual([]);

      // CONTROL: all three shapes are caught, so a clean report is evidence.
      for (const planted of [
        `const k = \`${KEY_PREFIX}auth.\${name}\`;`,
        `const k = '${KEY_PREFIX}auth.' + name;`,
        `const k = name + '${KEY_PREFIX}auth';`,
      ]) {
        expect(DYNAMIC.some((pattern) => pattern.test(planted)), `the planted ${planted} went unseen`).toBe(true);
      }
    });

    /**
     * AC8 finds keys and AC3/AC6 prove the clearing, which is a clean separation and leaves nobody holding
     * the join (Kimi phase 11 F7). One case walks the inventory itself, so adding a row without clearing it
     * fails here rather than in whichever behavioural case happened to mention that key.
     */
    it('and every key in it is really gone after sign-out', async () => {
      await buildAuthorizeRequest();
      rememberReturnTarget('/account');
      for (const key of inventoryKeys()) {
        expect(globalThis.sessionStorage.getItem(key), `the precondition: ${key} was present`).not.toBeNull();
      }

      signOut();

      for (const key of inventoryKeys()) {
        expect(globalThis.sessionStorage.getItem(key), `${key} is in the inventory and survived sign-out`).toBeNull();
      }
    });

    it('CONTROL: a key outside the inventory is caught', async () => {
      const files = await production();
      const planted = [...files, { path: 'app/planted.ts', code: `const k = '${KEY_PREFIX}auth.smuggled';` }];
      const found = new Set<string>();
      for (const file of planted) {
        for (const match of file.code.matchAll(new RegExp(`['"\`](${KEY_PREFIX}[\\w.$-]*)['"\`]`, 'g'))) {
          found.add(match[1]);
        }
      }
      const accounted = new Set([...inventoryKeys(), ...NOT_STORAGE.map((entry) => entry.value)]);
      expect([...found].filter((literal) => !accounted.has(literal)), 'the planted key went unseen')
        .toHaveLength(1);
    });
  });

  describe('AC9 the hazard sign-out cannot close (task 39 owns it, OQ-T35-3)', () => {
    /**
     * A SIGNED-OUT TAB IS RE-AUTHENTICATED BY A SIBLING, and this case proves it rather than describing it.
     *
     * `crossTabRenewal.ts`'s listener calls `local.adopt(token)` on any `renewal:succeeded` message and never
     * asks whether this tab signed out. Task 39's propagation narrows the window and cannot close it: a sibling
     * whose renewal resolves before it handles the sign-out broadcast still posts a success, and the channel
     * orders nothing.
     *
     * It is asserted as it IS, not as it should be, because a test asserting the fix would have to be added
     * broken. The message is the handover. Whoever builds task 39 meets this as a failing expectation.
     */
    it('KNOWN HAZARD: a signed-out tab still adopts a sibling renewal (task 39 must replace this)', async () => {
      const name = `${RENEWAL_CHANNEL}.t35-${Date.now()}`;
      const locks: TabLocks = {
        request: () => {
          throw new Error('AC9 never takes the lock: it only needs the listener installed');
        },
      };
      const channel = new BroadcastChannel(name);
      const shared = shareRenewalAcrossTabs(accessToken, { channel, locks });
      const sibling = new BroadcastChannel(name);
      // The observer: a third endpoint whose only job is to say when the message arrived at all.
      const observer = new BroadcastChannel(name);
      const delivered = new Promise<void>((resolve) => {
        observer.addEventListener('message', () => resolve(), { once: true });
      });
      try {
        retainAccessToken(aToken());
        signOut();
        expect(accessToken.current(), 'sign-out did clear the token').toBeNull();

        const siblingToken = aToken();
        sibling.postMessage({ type: 'renewal:succeeded', token: siblingToken });

        /*
         * DELIVERY IS OBSERVED, NOT WAITED FOR - which is the part Kimi phase 11 F2 was reaching for, and
         * the part that fixes the dangerous failure mode rather than just widening the window.
         *
         * History: one `setTimeout(0)` passed three runs and then returned `null` the moment two cases ran
         * ahead of it, because `BroadcastChannel` delivery is a macrotask racing the timer. A bounded poll
         * followed. F2 objected that a poll is still non-deterministic, and gave two reasons - one of which
         * is wrong and one of which is the whole point:
         *
         *  - "on a fast runner the loop may exit early and still assert the hazard exists" - exiting early
         *    means the token is NON-null, which IS the hazard. Early exit is the correct outcome.
         *  - "on a slow runner the bound may not be enough" - this one matters, and it is not a flake, it is
         *    a FALSE REPAIR REPORT: the poll exhausts, the token is null, and the assertion below announces
         *    "GOOD NEWS, the hazard is fixed" when all that happened was a slow machine.
         *
         * So the two events are separated. A third endpoint on the same channel observes the message, and
         * `delivered` is awaited with NO bound: if delivery never happens the case fails as a Vitest timeout,
         * which reads as a channel problem and not as a fix. Only once delivery is known to have occurred is
         * adoption polled, and a few ticks then suffice because the remaining lag is one listener's turn.
         */
        await delivered;
        for (let attempt = 0; attempt < 50 && accessToken.current() === null; attempt += 1) {
          await new Promise((resolve) => setTimeout(resolve, 2));
        }

        expect(
          accessToken.current(),
          'GOOD NEWS, AND THIS CASE MUST CHANGE: a signed-out tab no longer adopts a sibling renewal. If you '
            + 'are building task 39, that is the fix OQ-T35-3 asked for - replace this case with the '
            + 'assertion that the token stays null and delete OQ-T35-3. If you did NOT intend this, '
            + 'something changed crossTabRenewal or accessToken by accident.',
        ).toBe(siblingToken);
      } finally {
        shared.close();
        channel.close();
        sibling.close();
        observer.close();
        clearAccessToken();
      }
    });
  });

  describe('AC9b the handover is addressed to someone', () => {
    /**
     * The frozen criterion says AC9 carries "a message naming task 39 as the owner", and until now nothing
     * checked that it does. A pinned hazard whose message loses its addressee becomes a green test beside a
     * real defect with no one told - which is the failure mode the whole case exists to avoid. Task 32
     * learned the same thing about a contract sentence: if the message is the deliverable, pin the message.
     */
    it("names task 39 and OQ-T35-3 in the hazard case's own source", async () => {
      const ownSource = await readFile(expect.getState().testPath as string, 'utf8');
      const block = ownSource.slice(ownSource.indexOf('KNOWN HAZARD'));
      const hazard = block.slice(0, block.indexOf('describe('));
      expect(hazard, 'the hazard case no longer names task 39').toMatch(/task 39/);
      expect(hazard, 'the hazard case no longer names its open question').toMatch(/OQ-T35-3/);
      // And the instruction, not only the addressee (Kimi phase 11 F12): a message that names task 39 but
      // no longer says what to DO leaves its reader knowing they are responsible and not what for.
      expect(hazard, 'the hazard message no longer says to replace the case').toMatch(/replace this case/i);
      expect(hazard, 'the hazard message no longer says what the fixed assertion is').toMatch(/stays null/i);
    });
  });

  describe('AC10 no second, partial sign-out may be assembled elsewhere', () => {
    it('finds every call to a clearing primitive inside the measured allowlist', async () => {
      const files = await production();
      expect(files.map((file) => file.path), 'the modules that declare the primitives are missing from the scan')
        .toEqual(expect.arrayContaining(DEFINERS));

      // WHAT THIS CATCHES AND WHAT IT DOES NOT (Kimi phase 8 I6). It sees a DIRECT call. An alias
      // (`const drop = clearAccessToken; drop()`), a wrapper, or a reference passed as a callback would pass.
      // Accepted rather than solved: the threat is someone assembling a second sign-out, which is written as
      // direct calls, and an AST pass to cover the rest is disproportionate to that. Comments are stripped
      // first, so a doc comment naming a primitive is not a stray.
      const strays: string[] = [];
      for (const file of files) {
        if (DEFINERS.includes(file.path)) continue;
        for (const [primitive, permitted] of Object.entries(PERMITTED_CALLERS)) {
          if (new RegExp(`\\b${primitive}\\s*\\(`).test(file.code) && !permitted.includes(file.path)) {
            strays.push(`${file.path} calls ${primitive}`);
          }
        }
      }
      expect(
        strays,
        'a module calls a clearing primitive directly. If you are building sign-out, call signOut() - three '
          + 'of the four calls made somewhere else is a tab that looks signed out while still holding '
          + 'something, which is what R19 forbids. If this is a new legitimate consumer, add it to '
          + 'PERMITTED_CALLERS with a reason.',
      ).toEqual([]);
    });

    it('CONTROL: a stray call in a non-allowlisted file is caught', async () => {
      const files = await production();
      const planted = [...files, { path: 'app/planted.ts', code: 'clearAccessToken();' }];
      const strays: string[] = [];
      for (const file of planted) {
        if (DEFINERS.includes(file.path)) continue;
        for (const [primitive, permitted] of Object.entries(PERMITTED_CALLERS)) {
          if (new RegExp(`\\b${primitive}\\s*\\(`).test(file.code) && !permitted.includes(file.path)) {
            strays.push(`${file.path} calls ${primitive}`);
          }
        }
      }
      expect(strays, 'the planted stray call went unseen').toEqual(['app/planted.ts calls clearAccessToken']);
    });

    /**
     * Kimi phase 8 I8: a stale entry never admits an unauthorized caller, but it makes the allowlist less
     * precise every time a file moves. The same both-directions rule AC8 already applies to the inventory -
     * a row that no longer describes the code is a failure, not a pass.
     */
    it('and every permitted caller really does call its primitive, so no entry is stale', async () => {
      const files = await production();
      const missing: string[] = [];
      for (const [primitive, permitted] of Object.entries(PERMITTED_CALLERS)) {
        for (const path of permitted) {
          const file = files.find((candidate) => candidate.path === path);
          if (!file) {
            missing.push(`${path} is permitted to call ${primitive} but no longer exists`);
          } else if (!new RegExp(`\\b${primitive}\\s*\\(`).test(file.code)) {
            missing.push(`${path} is permitted to call ${primitive} and does not`);
          }
        }
      }
      expect(missing, 'PERMITTED_CALLERS has gone stale; remove what no longer applies').toEqual([]);
    });

    /**
     * THE MONOPOLY (Kimi phase 11 F9). Each primitive's list is checked independently above, so a module
     * added to every list would assemble a second sign-out one entry at a time and never trip the stray
     * scan. What makes sign-out sign-out is holding the whole SET together, so that is asserted directly.
     *
     * Written first as "no other module may hold more than one", which is FALSE and the measurement said so:
     * `CallbackScreen.tsx` legitimately holds three of the five - it clears the token on a discard, consumes
     * the authorize state, and takes the return target - because that is the callback's own job. Two of
     * those are in sign-out's set, which is exactly why the claim has to be about the SET and not a count.
     * A rule that would have forbidden the callback is a rule about nothing.
     */
    it('is the only module permitted to call the whole sign-out set', () => {
      const SIGN_OUT_SET = ['clearAccessToken', 'takeSignInNotice', 'consumeAuthorizeState', 'clearReturnTarget'];
      const held = new Map<string, string[]>();
      for (const primitive of SIGN_OUT_SET) {
        for (const path of PERMITTED_CALLERS[primitive]) {
          held.set(path, [...(held.get(path) ?? []), primitive]);
        }
      }
      const whole = [...held].filter(([, primitives]) => primitives.length === SIGN_OUT_SET.length);
      expect(
        whole.map(([path]) => path),
        'a module other than signOut.ts is permitted to call every clearing primitive sign-out uses. That is '
          + 'a second sign-out with permission, assembled one allowlist entry at a time. Route it through '
          + 'signOut() instead.',
      ).toEqual(['auth/signOut.ts']);
      expect(whole[0]?.[1].sort(), 'and it holds all four of them').toEqual([...SIGN_OUT_SET].sort());
    });

    it('and sign-out itself calls all four, so the allowlist is not vacuous', async () => {
      const self = (await production()).find((file) => file.path === 'auth/signOut.ts');
      for (const primitive of ['clearAccessToken', 'takeSignInNotice', 'consumeAuthorizeState', 'clearReturnTarget']) {
        expect(self!.code, `signOut.ts does not call ${primitive}`).toMatch(new RegExp(`\\b${primitive}\\s*\\(`));
      }
      // And it reaches no store itself: the knowledge of which key holds what stays in the owning module.
      for (const forbidden of [/sessionStorage/, /localStorage/, /\bdocument\.cookie\b/, /indexedDB/]) {
        expect(self!.code, `signOut.ts reaches a storage surface directly (${forbidden.source})`).not.toMatch(forbidden);
      }
    });
  });

  describe('AC11 the task adds nothing it was not authorized to add', () => {
    it('adds no route, no network call and no catalogue key', async () => {
      const self = (await production()).find((file) => file.path === 'auth/signOut.ts');
      for (const forbidden of [/\bfetch\s*\(/, /XMLHttpRequest/, /react-router/, /\bt\s*\(/, /catalogue/, /navigate/]) {
        expect(self!.code, `signOut.ts reaches for ${forbidden.source}`).not.toMatch(forbidden);
      }
      // And it renders nothing (Kimi phase 11 F4). The file is `.tsx` only so task 36 can extend it; the
      // extension is not a licence to put a screen in the clearing half, and nothing else noticed if it did.
      for (const rendering of [/from 'react'/, /from "react"/, /\bReact\b/, /<\/?[A-Z]\w*[\s/>]/, /<>/]) {
        expect(self!.code, `signOut.ts renders or imports React (${rendering.source})`).not.toMatch(rendering);
      }
    });

    /**
     * THE FROZEN CLAUSE WAS "modifies no file but the two it creates and the one additive export", AND IT IS
     * FALSE. T35 modified four files outside its own: `returnTarget.ts` as authorized, plus task 23's and
     * task 24's suites. Phase 10's audit is what caught the discrepancy between the criterion and the task.
     *
     * A changed-file set cannot honestly be pinned from inside a test - it would be a git diff asserted at
     * runtime, green on a dirty tree and red on a rebase. So the corrected clause is recorded in phase 10's
     * artifact, and what IS pinned here is the rule those edits had to satisfy: an export added to another
     * task's module must be held to a contract in THAT module's suite (Kimi phase 8 I2). That is the check
     * which would have caught the omission at the time.
     */
    it("the export added to task 24's module is held to a contract in task 24's suite", async () => {
      const root = dirname(expect.getState().testPath as string);
      const owner = await readFile(join(root, 'returnTargetOutsideAllowlistFallsBackToHome.test.tsx'), 'utf8');
      expect(
        owner,
        'clearReturnTarget lives in returnTarget.ts, which is task 24\'s. Its contract belongs in task 24\'s '
          + 'suite: without it, a refactor of that module breaks sign-out and the failure reads as a '
          + 'sign-out defect rather than a return-target one.',
      ).toContain('clearReturnTarget');
      for (const claim of [/idempotent/i, /never throws/i, /reads nothing/i]) {
        expect(owner, `task 24's suite does not hold the new export to: ${claim.source}`).toMatch(claim);
      }
    });

    it('holds no module-level mutable state of its own', async () => {
      const self = (await production()).find((file) => file.path === 'auth/signOut.ts');
      expect(self!.code).not.toMatch(/^(let|var)\s/m);
    });
  });

  describe('AC12 it never throws, whatever the store does', () => {
    it('clears the token even when every storage operation is refused', () => {
      retainAccessToken(aToken());
      setSignInNotice('session-ended');
      denyStorage();

      expect(() => signOut(), 'a sign-out that cannot tidy is still a sign-out').not.toThrow();

      vi.restoreAllMocks();
      expect(accessToken.current(), 'the security property survives a denied store').toBeNull();
      expect(takeSignInNotice(), 'and so does the in-memory notice').toBeNull();
    });

    /**
     * Absent, not refusing - and they are different failures. `denyStorage` replaces the methods, so the
     * store object still exists; some privacy modes remove `sessionStorage` altogether, and then the
     * expression that reaches for `.removeItem` throws a TypeError before any method is called. The
     * criterion says "any storage condition", so the two are measured separately.
     */
    it('clears the token when sessionStorage is absent altogether', () => {
      retainAccessToken(aToken());
      vi.stubGlobal('sessionStorage', undefined);

      expect(() => signOut()).not.toThrow();

      expect(accessToken.current()).toBeNull();
    });

    /**
     * A PARTIAL failure, which is the third shape and the only one with an ordering consequence (Kimi phase
     * 11 F6). `removeItem` throws while the store is otherwise healthy - the realistic version is a store
     * that answers reads and refuses writes - and the point is that the FIRST of the four removals throwing
     * must not prevent the other three. It does not, because each primitive guards its own.
     *
     * `QuotaExceededError` is deliberately not a case: quota applies to writes and sign-out never writes.
     * Naming the conditions actually covered - absent, refusing everything, refusing removals - is honest
     * where the frozen criterion's "any storage condition" was not.
     */
    it('clears everything it can when only removeItem throws', async () => {
      await buildAuthorizeRequest();
      rememberReturnTarget('/account');
      retainAccessToken(aToken());
      const attempted: string[] = [];
      vi.spyOn(Storage.prototype, 'removeItem').mockImplementation((key: string) => {
        attempted.push(key);
        throw new DOMException('denied', 'SecurityError');
      });

      expect(() => signOut()).not.toThrow();

      expect(accessToken.current(), 'the security property is unaffected by a store that will not tidy').toBeNull();
      expect(
        attempted.sort(),
        'one removal throwing stopped the others: each primitive must guard its own, so a refusal on the '
          + 'first key cannot leave the remaining three unattempted',
      ).toEqual(inventoryKeys().sort());
    });

    it('CONTROL: the denied store really does throw', () => {
      denyStorage();
      expect(() => globalThis.sessionStorage.removeItem('anything')).toThrow();
    });
  });
});
