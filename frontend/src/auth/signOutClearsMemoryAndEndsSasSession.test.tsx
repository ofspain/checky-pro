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
import { DENYLISTED_NAVIGATION_PATHS } from '../pwa/navigationDenylist';
import { APP_BASENAME, PUBLIC_ROUTE_PATHS } from '../app/routes';
import { END_SESSION_PATH, endSession } from './endSession';
import { retainIdToken, takeIdToken } from './idToken';
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
  // TASK 36. The ID token, held only so sign-out can send an `id_token_hint` to the end-session endpoint
  // (R19 clause 2). No key: it lives in a module-level `let`, as the access token does, because L3 governs it
  // the same way - identity in memory, never in a store.
  {
    what: 'the held ID token',
    owner: 'auth/idToken.ts',
    clearedBy: 'takeIdToken',
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
  // TASK 36. Two callers, and the pair is the design: `endSession.ts` takes the token for the hint it is
  // about to send, `signOut.ts` takes it and discards the value so the token is gone on either entry path.
  takeIdToken: ['auth/endSession.ts', 'auth/signOut.ts'],
  takeReturnTarget: ['auth/CallbackScreen.tsx'],
  clearReturnTarget: ['auth/signOut.ts'],
};

/** The modules that DECLARE the primitives; excluded by path, because a declaration is not a call. */
const DEFINERS = ['auth/accessToken.ts', 'auth/signInNotice.ts', 'auth/authorizeRequest.ts', 'auth/returnTarget.ts',
  'auth/idToken.ts', // TASK 36
];

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

/* ─────────────────────── task 36: the hand-over ─────────────────────── */

/** An ID token nobody could mistake for real, distinct per use, for the same reason `aToken` is. */
const anIdToken = (() => {
  let sequence = 0;
  return () => `T36-id-token-${(sequence += 1)}`;
})();

type Handover = { seen: string[]; targets(): string[] };

/**
 * Replaces `location` and `window.open` with one counter over all FOUR hand-over mechanisms.
 *
 * Copied from `loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx:277-300`, task 16's, named rather
 * than quietly re-typed - the fifth duplicated helper OQ-T32-4 tracks.
 *
 * Why four and not the two this module uses, which Kimi's phase 3 F5 proposed narrowing to: the failure this
 * counter exists to catch is an ADDITION. A second hand-over fired through `href` or `window.open` ALONGSIDE
 * the existing `assign` leaves an `assign`-only spy reporting exactly one call, and the case passes while the
 * SPA has grown two navigations. `window.open` being an unrealistic mechanism for this flow is precisely why
 * it must be watched: an unrealistic second hand-over is the one nobody would think to look for.
 *
 * The `href` setter is reachable only because the WHOLE object is replaced - jsdom's real descriptor is
 * `configurable: false` and cannot be patched in place (task 16 measured that). `origin` must be carried on
 * the fake or `postLogoutRedirectUri()` reads `undefined` and builds a silently wrong URL while the hand-over
 * COUNT still passes.
 */
function watchHandover(origin = 'https://app.example'): Handover {
  const seen: string[] = [];
  const fake = {
    origin,
    assign: (url: string) => void seen.push(`assign:${url}`),
    replace: (url: string) => void seen.push(`replace:${url}`),
  } as unknown as Location;
  Object.defineProperty(fake, 'href', {
    get: () => `${origin}/app/home`,
    set: (url: string) => void seen.push(`href:${url}`),
    configurable: true,
  });
  vi.stubGlobal('location', fake);
  vi.stubGlobal('open', (url?: string | URL) => {
    seen.push(`open:${String(url)}`);
    return null;
  });
  return { seen, targets: () => seen.map((entry) => entry.slice(entry.indexOf(':') + 1)) };
}

/** The one hand-over's URL, with the count asserted first so a two-navigation bug cannot read as a pass. */
function soleHandover(handover: Handover): URL {
  expect(handover.seen, `expected exactly one hand-over, saw: ${handover.seen.join(', ')}`).toHaveLength(1);
  return new URL(handover.targets()[0] as string);
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
    takeIdToken(); // TASK 36
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    clearAccessToken();
    takeSignInNotice();
    takeIdToken(); // TASK 36
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
      expect(INVENTORY).toHaveLength(7); // 6 in task 35; the ID token is task 36's.
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
    it('is the only module permitted to call the whole sign-out set', async () => {
      // TASK 36 STRENGTHENS THIS, and the reason is a measurement task 36 made about task 35. The set was a
      // HARDCODED FOUR here. When task 36 added a fifth primitive to signOut.ts, this case went on passing
      // without noticing: the four were still held only by signOut.ts. So its own description - "the whole
      // sign-out set" - had quietly become false, because the set it measured was task 35's four rather than
      // the set sign-out actually calls, and a second module permitted to call all FIVE would not have been
      // caught. DERIVED FROM SOURCE now, so the set cannot go stale the next time sign-out grows a step.
      const self = (await production()).find((file) => file.path === 'auth/signOut.ts');
      const SIGN_OUT_SET = Object.keys(PERMITTED_CALLERS)
        .filter((primitive) => new RegExp(`\\b${primitive}\\s*\\(`).test(self!.code));
      expect(SIGN_OUT_SET.length, 'the derivation found no primitives in signOut.ts, so this case is vacuous')
        .toBe(5);
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
      expect(whole[0]?.[1].sort(), 'and it holds every one of them').toEqual([...SIGN_OUT_SET].sort());
    });

    it('CONTROL: a second module permitted to call the WHOLE derived set is caught (task 36)', async () => {
      // The precision half, and it cannot be a mutant: the gap task 36 found was in this file's logic, not
      // in production source, so there is nothing for a harness to mutate. This plants the thing the
      // strengthened case exists to catch - a module added to every primitive's list, one entry at a time -
      // and shows it is seen. Under the OLD hardcoded four, a module holding four-of-five passed; the second
      // assertion below is what that version could not make.
      const self = (await production()).find((file) => file.path === 'auth/signOut.ts');
      const derived = Object.keys(PERMITTED_CALLERS)
        .filter((primitive) => new RegExp(`\\b${primitive}\\s*\\(`).test(self!.code));
      const planted: Record<string, readonly string[]> = Object.fromEntries(
        Object.entries(PERMITTED_CALLERS).map(([primitive, permitted]) => [
          primitive,
          derived.includes(primitive) ? [...permitted, 'app/secondSignOut.ts'] : permitted,
        ]),
      );
      const held = new Map<string, string[]>();
      for (const primitive of derived) {
        for (const path of planted[primitive] as readonly string[]) {
          held.set(path, [...(held.get(path) ?? []), primitive]);
        }
      }
      const whole = [...held].filter(([, primitives]) => primitives.length === derived.length);
      expect(whole.map(([path]) => path).sort(), 'the planted second sign-out went unseen')
        .toEqual(['app/secondSignOut.ts', 'auth/signOut.ts']);
      expect(derived, 'the derived set is still task 35\'s four, so the strengthening did nothing')
        .toContain('takeIdToken');
    });

    it('and sign-out itself calls all four, so the allowlist is not vacuous', async () => {
      const self = (await production()).find((file) => file.path === 'auth/signOut.ts');
      for (const primitive of ['clearAccessToken', 'takeSignInNotice', 'consumeAuthorizeState', 'clearReturnTarget',
        'takeIdToken', // TASK 36
      ]) {
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

  /* ═══════════════════════════════════════════════════════════════════════════════════════════════════════
   * TASK 36 — R19 clause 2. Sign-out ends the SAS session.
   *
   * Everything above is task 35's and is left as it was, except where a structure had to grow to admit a
   * fifth piece of state; each of those is marked `TASK 36`.
   *
   * These cases are verified BY DIRECT INVOCATION ONLY. Nothing in the SPA calls `endSession` either, for the
   * same reason nothing calls `signOut` - the affordance is unowned (OQ-T36-5).
   * ═══════════════════════════════════════════════════════════════════════════════════════════════════════ */

  describe('AC1 the clearing completes before the browser leaves', () => {
    it('holds nothing by the time the hand-over happens', () => {
      retainAccessToken(aToken());
      retainIdToken(anIdToken());
      setSignInNotice('session-ended');
      const handover = watchHandover();

      endSession();

      soleHandover(handover);
      expect(accessToken.current(), 'a tab navigated away still holding a bearer token').toBeNull();
      expect(takeIdToken(), 'a tab navigated away still holding an identity assertion').toBeNull();
      expect(takeSignInNotice(), 'the stale notice survived').toBeNull();
    });

    it('clears even when the hand-over itself throws', () => {
      // The order is not a preference: it is what makes a broken navigation safe. A browser that refuses the
      // assign - an extension, a blocked navigation - must still leave this tab signed out locally.
      retainAccessToken(aToken());
      retainIdToken(anIdToken());
      vi.stubGlobal('location', {
        origin: 'https://app.example',
        assign: () => {
          throw new Error('navigation refused');
        },
      } as unknown as Location);

      expect(() => endSession()).toThrow('navigation refused');

      expect(accessToken.current(), 'the clear did not happen before the hand-over').toBeNull();
      expect(takeIdToken()).toBeNull();
    });

    it('takes the ID token BEFORE calling sign-out, by source order (phase 10)', async () => {
      // PHASE 10 GAP, and it is a near-miss rather than a hole: reversing these two lines makes `hint` null,
      // the parameter absent, and AC5 fails - so the behaviour IS pinned. What was not pinned is that the
      // reversal is a REORDER rather than a bug in the hint. A reader looking at a failing AC5 would suspect
      // `takeIdToken`, which would be the wrong place to look. This names the cause.
      const self = (await production()).find((file) => file.path === 'auth/endSession.ts');
      const takeAt = self!.code.indexOf('takeIdToken()');
      const signOutAt = self!.code.search(/\bsignOut\s*\(\s*\)/);
      expect(takeAt, 'endSession.ts does not take the ID token at all').toBeGreaterThan(0);
      expect(signOutAt, 'endSession.ts does not call signOut at all').toBeGreaterThan(0);
      expect(takeAt, 'signOut() runs before the ID token is taken, so the hint is always null')
        .toBeLessThan(signOutAt);
    });

    it('and the clearing really is sign-out, not a second hand-rolled copy (source)', async () => {
      const self = (await production()).find((file) => file.path === 'auth/endSession.ts');
      expect(self, 'auth/endSession.ts is not in the scan').toBeDefined();
      expect(self!.code, 'endSession must delegate the clearing half, not repeat it').toMatch(/\bsignOut\s*\(\s*\)/);
      // AC10's monopoly is what makes this sufficient: no module but signOut may call the clearing primitives,
      // so delegating is the only way endSession can clear anything at all.
      for (const primitive of ['clearAccessToken', 'takeSignInNotice', 'consumeAuthorizeState', 'clearReturnTarget']) {
        expect(self!.code, `endSession.ts calls ${primitive} directly instead of through signOut`)
          .not.toMatch(new RegExp(`\\b${primitive}\\s*\\(`));
      }
    });
  });

  describe('AC2 exactly one hand-over, counted across all four mechanisms', () => {
    it('performs one navigation and no more', () => {
      retainIdToken(anIdToken());
      const handover = watchHandover();

      endSession();

      expect(handover.seen).toHaveLength(1);
      expect(handover.seen[0]!.startsWith('assign:'), `the mechanism was ${handover.seen[0]}`).toBe(true);
    });

    it('CONTROL: a watcher installed AFTER the call sees nothing', () => {
      retainIdToken(anIdToken());
      const before = watchHandover();
      endSession();
      const after = watchHandover();
      expect(before.seen, 'the real call went uncounted').toHaveLength(1);
      expect(after.seen, 'a watcher installed too late is what every spy mistake in this package looks like')
        .toEqual([]);
    });

    it('CONTROL: the watcher catches a second hand-over through every one of the four mechanisms', () => {
      const handover = watchHandover();
      globalThis.location.assign('/one');
      globalThis.location.replace('/two');
      globalThis.location.href = '/three';
      globalThis.open('/four');
      expect(handover.seen).toEqual(['assign:/one', 'replace:/two', 'href:/three', 'open:/four']);
    });

    it('reaches for no mechanism but assign, in its own source', async () => {
      const self = (await production()).find((file) => file.path === 'auth/endSession.ts');
      for (const forbidden of [/\blocation\s*\.\s*replace\b/, /\blocation\s*\.\s*href\s*=/, /\bopen\s*\(/, /\breload\b/]) {
        expect(self!.code, `endSession.ts reaches for ${forbidden.source}`).not.toMatch(forbidden);
      }
      // Lower bound: it does hand over, so the four bans are not passing over a module that navigates nowhere.
      expect(self!.code).toMatch(/location\s*\.\s*assign\s*\(/);
    });
  });

  describe('AC3 the URL is the end-session path on this origin', () => {
    it('navigates to END_SESSION_PATH on the origin it read from location', () => {
      retainIdToken(anIdToken());
      const handover = watchHandover('https://checky.example');

      endSession();

      const url = soleHandover(handover);
      expect(url.origin).toBe('https://checky.example');
      expect(url.pathname).toBe(END_SESSION_PATH);
    });

    it('spells no http or https host anywhere in production source', async () => {
      // NARROWED from "no absolute host" after Kimi's I8, which is right that the regex sees neither a
      // protocol-relative `//example.com` nor an exotic scheme. Narrowing the claim rather than widening the
      // scan: a `//` needle over stripped source would fire on a regex literal or a template expression, and
      // a scan with false positives gets an exclusion list, which is how a scan stops meaning anything.
      // L9's single origin, and R58: a hostname in the bundle is a deployment baked into code. The scan is
      // package-wide rather than scoped to this module, because the hazard is package-wide.
      const offenders = (await production())
        .filter((file) => /https?:\/\/(?!localhost|127\.0\.0\.1)[\w.-]+\.\w/.test(file.code))
        .map((file) => file.path);
      expect(offenders, 'a production module spells an absolute host').toEqual([]);
      // CONTROL: the pattern fires on the shape it exists to stop.
      expect(/https?:\/\/(?!localhost|127\.0\.0\.1)[\w.-]+\.\w/.test("const sas = 'https://auth.checky.example';"))
        .toBe(true);
    });
  });

  describe('AC4 post_logout_redirect_uri is derived, and checked against the route table', () => {
    it('is the origin, the basename and the signed-out route', () => {
      retainIdToken(anIdToken());
      const handover = watchHandover('https://checky.example');

      endSession();

      expect(soleHandover(handover).searchParams.get('post_logout_redirect_uri'))
        .toBe('https://checky.example/app/signed-out');
    });

    it('percent-encodes the target, so SAS parses one parameter and not three', () => {
      // PHASE 10 GAP. Every case above reads the value through `searchParams.get()`, which DECODES - so a URL
      // built by string concatenation, with the target's `://` and `/` left raw, returns the right string
      // from every one of them and passes. The raw query is where the difference lives: an unencoded
      // `https://app.example/app/signed-out` puts a bare `:` and three `/` into the query, and what SAS
      // receives depends on its parser rather than on this module. `URLSearchParams.toString()` is what makes
      // that not a question, and this is the case that notices if it stops being used.
      retainIdToken(anIdToken());
      const handover = watchHandover('https://checky.example');

      endSession();

      const raw = handover.targets()[0] as string;
      expect(raw, 'the post-logout target is not percent-encoded').toContain('https%3A%2F%2Fchecky.example%2Fapp%2Fsigned-out');
      expect(raw, 'a raw target leaked into the query string').not.toContain('=https://');
      // And it still round-trips, so the encoding is correct rather than merely present.
      expect(new URL(raw).searchParams.get('post_logout_redirect_uri')).toBe('https://checky.example/app/signed-out');
    });

    it('names a route the route table actually declares, and a PUBLIC one', () => {
      // `routes.tsx` has no SIGNED_OUT_PATH export - the route is a bare literal in PUBLIC_ROUTE_PATHS - and
      // that module is closed to this task, so `endSession.ts` declares the spelling privately. This is the
      // instrument that keeps the two from drifting: a rename of the route fails HERE rather than becoming a
      // 400 from SAS in a browser nobody is watching. PUBLIC because a visitor arriving there has no session.
      retainIdToken(anIdToken());
      const handover = watchHandover('https://checky.example');

      endSession();

      const target = new URL(soleHandover(handover).searchParams.get('post_logout_redirect_uri') as string);
      const route = target.pathname.slice(`${APP_BASENAME}/`.length);
      expect(PUBLIC_ROUTE_PATHS as readonly string[], `${route} is not a declared public route`).toContain(route);
    });

    it('URL DERIVATION ONLY: follows the route table when the basename moves, not hardcoding /app', async () => {
      // THE CASE THAT SEPARATES DERIVATION FROM COINCIDENCE. Task 22 recorded why
      // (`authorizeRequestsExactlyTheDeclaredScopes.test.ts:286`): while APP_BASENAME is '/app', `const base =
      // '/app'` and `const base = APP_BASENAME` produce the same string, so no assertion against the real
      // route table can tell them apart. Task 36 inherits that hole exactly and closes it the same way.
      vi.doMock('../app/routes', async () => {
        const actual = await vi.importActual<typeof import('../app/routes')>('../app/routes');
        return { ...actual, APP_BASENAME: '/console' };
      });
      vi.resetModules();
      const { endSession: moved } = await import('./endSession');
      const handover = watchHandover('https://checky.example');

      // The dynamic import brings its own `idToken` instance, so no token is retained here: this case is
      // about the derived URL, and the hint is AC5's. The absent hint is what AC12 covers.
      moved();

      const target = soleHandover(handover).searchParams.get('post_logout_redirect_uri');
      expect(target).toBe('https://checky.example/console/signed-out');
      expect(target).not.toContain('/app');
      vi.doUnmock('../app/routes');
      vi.resetModules();
    });

    it('URL DERIVATION ONLY: strips a trailing slash the route table might grow', async () => {
      vi.doMock('../app/routes', async () => {
        const actual = await vi.importActual<typeof import('../app/routes')>('../app/routes');
        return { ...actual, APP_BASENAME: '/console/' };
      });
      vi.resetModules();
      const { endSession: slashed } = await import('./endSession');
      const handover = watchHandover('https://checky.example');

      slashed();

      // Not `/console//signed-out`: the registered allowlist is compared exactly, and the rejection would be
      // a 400 at sign-out with nothing here failing first.
      expect(soleHandover(handover).searchParams.get('post_logout_redirect_uri'))
        .toBe('https://checky.example/console/signed-out');
      vi.doUnmock('../app/routes');
      vi.resetModules();
    });
  });

  describe('AC5 id_token_hint carries the retained ID token', () => {
    it('sends exactly what the callback retained', () => {
      const identity = anIdToken();
      retainIdToken(identity);
      const handover = watchHandover();

      endSession();

      expect(soleHandover(handover).searchParams.get('id_token_hint')).toBe(identity);
    });

    it('sends the token this tab holds, not a remembered one', () => {
      // Two unrelated values cannot both be a constant. Task 19's suite makes the same argument about the
      // verifier, for the same reason: one stored value proves nothing.
      for (const identity of [anIdToken(), anIdToken()]) {
        retainIdToken(identity);
        const handover = watchHandover();
        endSession();
        expect(soleHandover(handover).searchParams.get('id_token_hint')).toBe(identity);
        vi.unstubAllGlobals();
      }
    });

    it('consumes it through takeIdToken, so there is no reader to leave it behind', async () => {
      const self = (await production()).find((file) => file.path === 'auth/idToken.ts');
      expect(self, 'auth/idToken.ts is not in the scan').toBeDefined();
      // AC15's structural half: no plain reader exists, so a read that forgets to clear cannot be written.
      expect(self!.code).toMatch(/export function takeIdToken\(\)/);
      expect(self!.code, 'idToken.ts grew a plain reader; the read-before-clear window is open again')
        .not.toMatch(/export function (current|get|read|peek)/);
    });
  });

  describe('AC6 exactly two parameters', () => {
    it('sends id_token_hint and post_logout_redirect_uri, and nothing else', () => {
      retainAccessToken(aToken());
      retainIdToken(anIdToken());
      const handover = watchHandover();

      endSession();

      expect([...soleHandover(handover).searchParams.keys()].sort())
        .toEqual(['id_token_hint', 'post_logout_redirect_uri']);
    });

    it('names no state, no client id, no scope and no account identifier', async () => {
      const self = (await production()).find((file) => file.path === 'auth/endSession.ts');
      for (const forbidden of [/'state'/, /"state"/, /client_id/, /\bscope\b/, /CLIENT_ID/, /REQUESTED_SCOPES/]) {
        expect(self!.code, `endSession.ts names ${forbidden.source}`).not.toMatch(forbidden);
      }
    });
  });

  describe('AC7 a full-page navigation, not a router one', () => {
    it('imports no router and reaches for no history', async () => {
      const self = (await production()).find((file) => file.path === 'auth/endSession.ts');
      for (const forbidden of [/react-router/, /useNavigate/, /\bnavigate\s*\(/, /globalThis\.history/, /\bhistory\s*\./]) {
        expect(self!.code, `endSession.ts reaches for ${forbidden.source}`).not.toMatch(forbidden);
      }
      // And the positive: it really does leave the SPA. A module that navigated nowhere would pass every ban.
      expect(self!.code).toMatch(/globalThis\.location\.assign\(/);
    });
  });

  describe('AC8 the ID token appears exactly once, and only in the hint', () => {
    it('reaches the hint and no store, no console channel and no second place in the URL', () => {
      const identity = anIdToken();
      retainIdToken(identity);
      const watch = watchStorage();
      const channels = watchConsole();
      const handover = watchHandover();

      endSession();

      const raw = handover.targets()[0] as string;
      // EXACTLY ONCE, which is the claim phase 4 corrected: the earlier wording forbade every surface and so
      // forbade the parameter it requires. A token repeated in a fragment or a second parameter fails here.
      expect(raw.split(identity), `the ID token appears ${raw.split(identity).length - 1} times in the URL`)
        .toHaveLength(2);
      expect(new URL(raw).searchParams.get('id_token_hint')).toBe(identity);

      for (const [index, channel] of channels.entries()) {
        const calls = JSON.stringify((channel.mock.calls as unknown[][]) ?? []);
        expect(calls, `the ID token reached console.${CONSOLE_CHANNELS[index]}`).not.toContain(identity);
      }
      const written = JSON.stringify((watch.setItem.mock.calls as unknown[][]) ?? []);
      expect(written, 'the ID token reached a store').not.toContain(identity);
      expect(watch.setItem, 'sign-out wrote to a store').not.toHaveBeenCalled();
    });

    it('and neither module names a storage surface at all (source)', async () => {
      const files = await production();
      for (const path of ['auth/idToken.ts', 'auth/endSession.ts']) {
        const self = files.find((file) => file.path === path);
        // The last three are Kimi's I7, and its premise needed correcting before the remedy was worth
        // taking: I7 says these are "the same seven surfaces T35's AC6 covers". T35's AC6 covers NONE of
        // them - measured, `document.title` appeared nowhere in this file before this line. So this is not
        // parity with task 35; it is a gap BOTH tasks had, closed in the two modules task 36 owns.
        // `window.name` is the nastiest of the three: it survives a cross-origin navigation, so a token
        // written there would outlive the very hand-over this module performs.
        for (const forbidden of [/sessionStorage/, /localStorage/, /\bdocument\.cookie\b/, /indexedDB/, /console\./,
          /document\.title/, /window\.name/, /history\.state/]) {
          expect(self!.code, `${path} reaches ${forbidden.source}`).not.toMatch(forbidden);
        }
      }
    });
  });

  describe('AC9 sign-out clears the ID token', () => {
    it('clears it when sign-out is called on its own, with no hand-over at all', () => {
      // The entry path that would otherwise leak: `endSession()` consumes the token for its hint, so a test
      // that only ever calls `endSession` would pass even if `signOut` ignored the ID token entirely.
      retainIdToken(anIdToken());

      signOut();

      expect(takeIdToken(), 'signOut() left the ID token held').toBeNull();
    });

    it('is idempotent across both entry paths', () => {
      retainIdToken(anIdToken());
      const handover = watchHandover();
      endSession();
      expect(soleHandover(handover).searchParams.has('id_token_hint')).toBe(true);
      // signOut's own call found null and cleared nothing; calling either again changes nothing.
      expect(takeIdToken()).toBeNull();
      signOut();
      expect(takeIdToken()).toBeNull();
    });

    it('CONTROL: the ID token really is held until something clears it', () => {
      const identity = anIdToken();
      retainIdToken(identity);
      expect(takeIdToken(), 'retainIdToken did not hold, so every clearing case above is vacuous').toBe(identity);
    });
  });

  describe('AC10 only sign-out ends the SAS session', () => {
    it('spells the end-session path in exactly two production modules, each for a different reason', async () => {
      const PERMITTED = {
        'auth/endSession.ts': 'the hand-over itself: the one module that navigates there (R19 cl. 2)',
        'pwa/navigationDenylist.ts': 'a DENYLIST entry (R57), so the worker never answers this path from cache',
      } as const;
      const holders = (await production())
        .filter((file) => file.code.includes(END_SESSION_PATH))
        .map((file) => file.path);

      expect(holders.sort(), 'a third production module names the end-session path. Only the hand-over may '
        + 'navigate there and only the service-worker denylist may name it; add it to PERMITTED with a reason '
        + 'or route it through endSession().').toEqual(Object.keys(PERMITTED).sort());
      for (const reason of Object.values(PERMITTED)) expect(reason.length).toBeGreaterThan(40);
    });

    it('is imported by nothing, so the exported constant is not a second route to the endpoint (Kimi I3)', async () => {
      // THE HOLE THE LITERAL SCAN CANNOT SEE, and it is real: the case above matches
      // `file.code.includes(END_SESSION_PATH)`, so a module that does `import { END_SESSION_PATH } from
      // './endSession'` and then navigates to the imported variable spells no literal and passes. The path
      // constant is deliberately EXPORTED (AC16 compares it against the service-worker denylist), so the
      // export is the bypass. This scan closes it from the other side: who imports this module at all.
      const PERMITTED_IMPORTERS: readonly string[] = [
        // Empty, and that is the honest state of the package: nothing may reach the end-session endpoint
        // because nothing owns a sign-out affordance (OQ-T36-5). The day a task adds one, it adds itself
        // here - which is the handoff, not an obstacle: the message below says so.
      ];
      const importers = (await production())
        .filter((file) => file.path !== 'auth/endSession.ts' && /from '\.{1,2}(?:\/auth)?\/endSession'/.test(file.code))
        .map((file) => file.path);

      expect(importers.sort(), 'a module imports auth/endSession.ts. If you are wiring the sign-out '
        + 'affordance, that is the right thing to do - add the module to PERMITTED_IMPORTERS here, and note '
        + 'that OQ-T36-5 can then be closed. If you are reaching for END_SESSION_PATH to navigate there '
        + 'yourself, call endSession() instead.').toEqual([...PERMITTED_IMPORTERS].sort());
    });

    it('CONTROL: the import scan catches both spellings of the path it must see', () => {
      // A control rather than a planted file, because the needle is the thing that could be wrong: a
      // same-directory import and one from a sibling directory are spelled differently, and a scan that
      // matched only the first would pass over exactly the module most likely to add the affordance.
      const needle = /from '\.{1,2}(?:\/auth)?\/endSession'/;
      for (const shape of [
        "import { endSession } from './endSession';",
        "import { endSession } from '../auth/endSession';",
        "import { END_SESSION_PATH } from './endSession';",
      ]) {
        expect(needle.test(shape), `the import scan is blind to ${shape}`).toBe(true);
      }
      expect(needle.test("import { signOut } from './signOut';"), 'the needle matches an unrelated import')
        .toBe(false);
    });

    it('CONTROL: a third module naming the path is caught', async () => {
      const files = await production();
      const planted = files.map((file) =>
        file.path === 'app/AppShell.tsx' ? { ...file, code: `const out = '${END_SESSION_PATH}';\n${file.code}` } : file,
      );
      expect(planted.filter((file) => file.code.includes(END_SESSION_PATH)).map((file) => file.path))
        .toContain('app/AppShell.tsx');
    });

    it('and endSession is the only module permitted to take the ID token besides signOut', () => {
      // PERMITTED_CALLERS already holds this, checked by task 35's own stale-caller and allowlist cases. Named
      // here so AC10's claim is traceable to the instrument that enforces it rather than restated loosely.
      expect(PERMITTED_CALLERS.takeIdToken).toEqual(['auth/endSession.ts', 'auth/signOut.ts']);
    });
  });

  describe('AC11 task 36 adds no route, catalogue key, backend call or storage key', () => {
    it('adds nothing to the route table, the catalogue or the wire', async () => {
      const files = await production();
      for (const path of ['auth/idToken.ts', 'auth/endSession.ts']) {
        const self = files.find((file) => file.path === path);
        for (const forbidden of [/\bfetch\s*\(/, /XMLHttpRequest/, /useTranslation/, /\bt\s*\(/, /catalogue/, /createAuthApi/]) {
          expect(self!.code, `${path} reaches for ${forbidden.source}`).not.toMatch(forbidden);
        }
      }
      // The storage half is AC8's source scan; the route half is AC4's PUBLIC_ROUTE_PATHS check, which proves
      // the target is a route that ALREADY existed. This case is the remaining third: no wire, no copy.
      expect(PUBLIC_ROUTE_PATHS as readonly string[]).toContain('signed-out');
    });
  });

  describe('AC12 a tab holding no ID token still clears and still hands over', () => {
    it('clears before the URL is built, so a failure there cannot leave a token held (Kimi I9)', () => {
      // AC1 already pins a throwing `assign`. This pins the step BEFORE it: the URL is built as the
      // argument to `assign`, so a throw in `URLSearchParams` happens after `signOut()` has run and before
      // the browser is asked to go anywhere. Correct by construction, and now by measurement.
      retainAccessToken(aToken());
      retainIdToken(anIdToken());
      vi.stubGlobal('location', { origin: 'https://app.example', assign: () => undefined } as unknown as Location);
      vi.stubGlobal('URLSearchParams', class {
        constructor() {
          throw new Error('URL building failed');
        }
      });

      expect(() => endSession()).toThrow('URL building failed');

      expect(accessToken.current(), 'a failure building the URL left a bearer token held').toBeNull();
      expect(takeIdToken(), 'a failure building the URL left an identity assertion held').toBeNull();
    });

    it('omits the parameter rather than sending an empty one', () => {
      retainAccessToken(aToken());
      const handover = watchHandover();

      endSession();

      const url = soleHandover(handover);
      // ABSENT, not empty. `id_token_hint=` asks SAS to end the session identified by the empty string, and a
      // `?? ''` is exactly the shape that mistake takes - URLSearchParams would serialise it without complaint.
      expect(url.searchParams.has('id_token_hint'), 'an empty hint was sent instead of none').toBe(false);
      expect(url.search).not.toContain('id_token_hint');
      expect(url.searchParams.get('post_logout_redirect_uri')).toBeTruthy();
      expect(accessToken.current()).toBeNull();
    });

    it('hands over rather than skipping, because skipping is the worse failure', () => {
      // OQ-T36-3: unreachable in production today (`renew()` always rejects, so `adopt` fires only from
      // crossTabRenewal.ts:103). Kept because the alternative is worse - a skipped hand-over leaves the SAS
      // cookie alive, and the next /oauth2/authorize returns a code without asking for anything. A failed
      // end-session leaves the visitor locally signed out and confused; a skipped one leaves them signed in
      // and reassured. Task 36 makes this shape PRODUCIBLE for the first time: after a sibling renewal a
      // signed-out tab holds an access token and no ID token (OQ-T35-3).
      const handover = watchHandover();
      endSession();
      expect(handover.seen, 'a tab with no ID token skipped the hand-over').toHaveLength(1);
    });
  });

  describe('AC13 a second endSession, pinned as it is rather than latched', () => {
    it('clears nothing further, omits the hint, and hands over again', () => {
      retainIdToken(anIdToken());
      const handover = watchHandover();

      endSession();
      endSession();

      // KNOWN AND ACCEPTED, not a defect: in a real browser the first navigation has already left the page.
      // A latch would be module-level mutable state - the one thing this module may not hold (AC15) - traded
      // for a second navigation that cannot happen. If a future task needs idempotence, this case is what it
      // changes, and it should change this message with it.
      expect(handover.seen, 'two calls, two hand-overs').toHaveLength(2);
      expect(new URL(handover.targets()[0] as string).searchParams.has('id_token_hint')).toBe(true);
      expect(
        new URL(handover.targets()[1] as string).searchParams.has('id_token_hint'),
        'the second call re-sent a hint, which means the first did not consume the token',
      ).toBe(false);
      expect(takeIdToken()).toBeNull();
    });
  });

  describe('AC14 the callback retains the ID token AFTER the nonce check', () => {
    it('places the retention on the far side of the check, with the early return between (source)', async () => {
      // Structural because it has to be, exactly as task 19's AC14 case is for the access token
      // (`callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx:1019`): a token retained before the check
      // and discarded after would leave both holders null at the end of a behavioural case, so the correct and
      // incorrect orders are distinguishable only in the source. An ID token hoisted above the check is worse
      // than an access token would be: it is a signed identity assertion for a login this tab did not start,
      // and it looks perfectly valid afterwards - only its nonce was wrong.
      const screen = (await production()).find((file) => file.path === 'auth/CallbackScreen.tsx');
      expect(screen, 'auth/CallbackScreen.tsx is not in the scan').toBeDefined();
      const nonceAt = screen!.code.indexOf('idTokenNonceMatchesStoredValue(response.idToken)');
      const retainAt = screen!.code.indexOf('retainIdToken(response.idToken)');
      expect(nonceAt, 'the nonce check is not where this scan expects it').toBeGreaterThan(0);
      expect(retainAt, 'CallbackScreen.tsx does not retain the ID token at all').toBeGreaterThan(0);
      expect(retainAt, 'the ID token is retained BEFORE the nonce check').toBeGreaterThan(nonceAt);
      expect(screen!.code.slice(nonceAt, retainAt), 'nothing stops the two being reordered').toContain('return;');
    });

    it('CONTROL: the scan catches a hoisted retention', () => {
      const hoisted = [
        'retainIdToken(response.idToken);',
        'if (!idTokenNonceMatchesStoredValue(response.idToken).ok) { discard(); return; }',
      ].join('\n');
      const nonceAt = hoisted.indexOf('idTokenNonceMatchesStoredValue(response.idToken)');
      const retainAt = hoisted.indexOf('retainIdToken(response.idToken)');
      expect(retainAt).toBeLessThan(nonceAt);
    });

    it('and retains it in exactly one place', async () => {
      const holders = (await production())
        // Excluding the DEFINER by path, as task 35's AC10 does: `export function retainIdToken(` is a
        // declaration, not a call, and a scan that cannot tell them apart would report the owning module as
        // its own caller forever. Measured - this case failed on exactly that before the exclusion.
        .filter((file) => file.path !== 'auth/idToken.ts' && /\bretainIdToken\s*\(/.test(file.code))
        .map((file) => file.path);
      expect(holders.sort(), 'the ID token is retained somewhere besides the callback').toEqual(['auth/CallbackScreen.tsx']);
      // CONTROL: the needle really does fire, so the exclusion has not emptied the scan.
      expect(/\bretainIdToken\s*\(/.test('retainIdToken(response.idToken);')).toBe(true);
    });
  });

  describe('AC15 neither new module holds mutable state it should not', () => {
    it('gives endSession.ts no module-scope mutable at all', async () => {
      const self = (await production()).find((file) => file.path === 'auth/endSession.ts');
      expect(MUTABLE_AT_MODULE_SCOPE.test(self!.code), 'endSession.ts grew module-scope mutable state').toBe(false);
      // Task 35's AC7 scan is what makes this binding package-wide; this case states it for the module that
      // AC13 explicitly declines to latch, so the two decisions are recorded in one place.
    });

    it("counts idToken.ts's one mutable, which is the whole deliverable", async () => {
      const self = (await production()).find((file) => file.path === 'auth/idToken.ts');
      const declarations = (self!.code.match(new RegExp(MUTABLE_AT_MODULE_SCOPE.source, 'gm')) ?? []).length;
      // ONE. Task 35's AC7 proves sign-out clears this file's state; it does not pin how much of it there is,
      // which is the hole phase 2 found and AC9 closes - a SECOND `let` added here would be cleared by nothing
      // and excused by nothing, and AC7 would still pass because the file is in the cleared list.
      expect(declarations, 'idToken.ts holds more than the one token it exists to hold').toBe(1);
    });
  });

  describe('AC16 the two spellings of the end-session path cannot drift', () => {
    it('is on the service-worker navigation denylist', () => {
      // R57, and the reason the hand-over is a full-page navigation at all: without this entry the worker's
      // navigation fallback would hand index.html to a browser on its way to the end-session endpoint, and
      // nobody could sign out. Held by a test rather than by an import, so the pwa layer keeps no dependency
      // on the auth layer for the sake of a string.
      expect(DENYLISTED_NAVIGATION_PATHS as readonly string[]).toContain(END_SESSION_PATH);
    });
  });
});
