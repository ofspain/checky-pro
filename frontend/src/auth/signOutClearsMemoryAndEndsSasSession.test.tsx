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
import { AUTHORIZE_STORAGE_KEYS, buildAuthorizeRequest } from './authorizeRequest';
import { RETURN_TARGET_KEY, rememberReturnTarget } from './returnTarget';
import { setSignInNotice, takeSignInNotice } from './signInNotice';
import { signOut } from './signOut';

/** A key prefix, not a key: AC8 scans for it and also proves no whole key is spelled here. */
const KEY_PREFIX = 'themistra.';

/** A token nobody could mistake for real, and distinct per use so a stale assertion cannot pass. */
let tokenSequence = 0;
const aToken = () => `T35-access-token-${(tokenSequence += 1)}`;

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
    file: 'pwa/registerServiceWorker.ts',
    why:
      'the service-worker registration promise and its waiting-update flag. Not session state and not the ' +
      "visitor's: a sign-out that dropped them would re-register a worker and re-prompt for an update that is " +
      'already waiting.',
  },
] as const;

/* ─────────────────────────── storage and console watches ─────────────────────────── */

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
    it('is harmless run twice', () => {
      retainAccessToken(aToken());
      signOut();
      expect(() => signOut()).not.toThrow();
      expect(accessToken.current()).toBeNull();
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
      for (const deferral of [/\basync\b/, /\bawait\b/, /setTimeout\s*\(/, /queueMicrotask\s*\(/, /\.then\s*\(/, /\bPromise\b/]) {
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

      expect(keysOf(watch.removeItem).sort(), 'the four inventory keys, once each').toEqual(inventoryKeys().sort());
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
      const holders = files.filter((file) => /^(let|var)\s/m.test(file.code)).map((file) => file.path);
      const cleared = INVENTORY.filter((row) => row.key === null).map((row) => row.owner);
      const excused = NOT_SESSION_STATE.map((entry) => entry.file);

      expect(holders.sort(), 'a module holds mutable state that sign-out neither clears nor excuses. Either '
        + 'clear it in signOut.ts, or add it to NOT_SESSION_STATE with a reason.')
        .toEqual([...cleared, ...excused].sort());

      for (const entry of NOT_SESSION_STATE) {
        expect(entry.why.length, `${entry.file} is excused without a reason`).toBeGreaterThan(60);
      }
      // The limitation, stated rather than implied: closure-held state is not module-scope and not reachable
      // from a zero-argument function. AC9 is what that limitation costs.
      expect(files.some((file) => file.path === 'api/crossTabRenewal.ts' && !/^(let|var)\s/m.test(file.code)),
        'crossTabRenewal holds its state in a factory closure, which this scan cannot see - see AC9')
        .toBe(true);
    });

    it('CONTROL: a module-scope mutable in a scanned file is caught', async () => {
      const files = await production();
      const planted = files.map((file) =>
        file.path === 'app/AppShell.tsx' ? { ...file, code: `let leaked = 1;\n${file.code}` } : file,
      );
      const holders = planted.filter((file) => /^(let|var)\s/m.test(file.code)).map((file) => file.path);
      expect(holders, 'the planted mutable went unseen').toContain('app/AppShell.tsx');
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
    it('adopts a sibling token AFTER sign-out, which is a session nobody asked for', async () => {
      const name = `${RENEWAL_CHANNEL}.t35-${Date.now()}`;
      const locks: TabLocks = {
        request: () => {
          throw new Error('AC9 never takes the lock: it only needs the listener installed');
        },
      };
      const channel = new BroadcastChannel(name);
      const shared = shareRenewalAcrossTabs(accessToken, { channel, locks });
      const sibling = new BroadcastChannel(name);
      try {
        retainAccessToken(aToken());
        signOut();
        expect(accessToken.current(), 'sign-out did clear the token').toBeNull();

        const siblingToken = aToken();
        sibling.postMessage({ type: 'renewal:succeeded', token: siblingToken });
        await new Promise((resolve) => setTimeout(resolve, 0));

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
        clearAccessToken();
      }
    });
  });

  describe('AC10 no second, partial sign-out may be assembled elsewhere', () => {
    it('finds every call to a clearing primitive inside the measured allowlist', async () => {
      const files = await production();
      expect(files.map((file) => file.path), 'the modules that declare the primitives are missing from the scan')
        .toEqual(expect.arrayContaining(DEFINERS));

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

    it('CONTROL: the denied store really does throw', () => {
      denyStorage();
      expect(() => globalThis.sessionStorage.removeItem('anything')).toThrow();
    });
  });
});
