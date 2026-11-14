/**
 * R28's SECOND clause — **TASK 41 OWNS THIS FILE, AND TASK 42 EXTENDS IT.**
 *
 * `package.md` §8 names `unauthenticatedRoutesRedirectToSignIn`. What it measures is the half of task 41 that
 * nothing else can see: the eleven grown instruments in tasks 16's, 37's and 40's files pin the route table's
 * SHAPE and the fact that a blocked arrival lands on sign-in. They cannot see the **return target**, the
 * **source**, or the **authenticated visitor who asked for a route that matches nothing** — and at phase 7 the
 * mutation harness said so in numbers: six rows survived all 1746 tests, every one of them in those three
 * families. This file is written in the order those survivors give, not in AC order.
 *
 * **IT MOUNTS THE REAL ROUTE TABLE**, unlike task 40's suite, which derives a probe table because a blocked
 * route rendered nothing there and an arm needs something to look at. After task 41 a blocked arrival renders
 * the sign-in screen, so the real table is the honest instrument and a probe would be a second variable.
 *
 * **THE ONE EXCEPTION IS `callback`.** It is one of the nine public routes and its screen acts on arrival -
 * task 6 owns what it does with a missing grant - so mounting it here would measure that behaviour and call it
 * task 41's. Its public-ness is therefore asserted by MATCHING, which happens when the router is created and
 * needs no render at all, and its own suites own the rest.
 *
 * **DESCRIBES ARE PREFIXED FROM THE FIRST CASE** (phase 3, F13): task 42 adds authenticated screens behind the
 * gate and will extend this file, and a failure naming "AC1" must say whose AC1 it is. Task 40's phase 12 found
 * exactly that collision after the fact, in a file where two tasks had both numbered from one.
 */
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { StrictMode, type ReactElement } from 'react';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { accessToken, clearAccessToken, retainAccessToken } from './accessToken';
import { RETURN_TARGET_KEY, clearReturnTarget, takeReturnTarget } from './returnTarget';
import { createI18n } from '../i18n/i18n';
import { en } from '../i18n/catalogue';
import { APP_BASENAME, AUTHENTICATED_ROUTE_PATHS, CALLBACK_PATH, HOME_PATH, PUBLIC_ROUTE_PATHS,
  SIGNED_OUT_PATH, SIGN_IN_PATH, appRoutes } from '../app/routes';
// TASK 42's four. `AuthenticatedLayout` is read as a component (the route table's element must BE it) and as
// source (its import list is its contract); the channel constants are how a SIBLING tab is simulated.
import { AuthenticatedLayout } from '../app/AuthenticatedLayout';
import { SIGN_OUT_CHANNEL, SIGN_OUT_TYPE } from '../api/crossTabSignOut';

/** A concrete stand-in for a parameterised segment, so `invoices/:id` can actually be arrived at. */
const SAMPLE_ID = '9f1c2b7a-5d3e-4c8a-9b0f-21e6d4c7a8b3';
function concrete(path: string): string {
  return path.replaceAll(':id', SAMPLE_ID);
}

/** The paths that are NOT parameterised, which is the set `returnTarget.ts`'s allowlist is built from (T24-D5). */
const REMEMBERABLE = AUTHENTICATED_ROUTE_PATHS.filter(
  (path) => !path.split('/').some((segment) => segment.startsWith(':')));

let mounted: { root: Root; container: HTMLElement; router: ReturnType<typeof createMemoryRouter> } | undefined;

beforeEach(() => {
  clearAccessToken();
  clearReturnTarget();
  globalThis.sessionStorage.clear();
});

afterEach(() => {
  release();
  vi.restoreAllMocks();
  globalThis.sessionStorage.clear();
});

/** Creates the real router WITHOUT rendering, which is enough for a claim about matching. */
function routerAt(path: string) {
  return createMemoryRouter(appRoutes, { basename: APP_BASENAME, initialEntries: [path] });
}

/** Mounts the REAL table at an `/app` location. No `AppShell`: it renders the `Surface` primitive that
 *  task 16's suite wraps, and nothing here mounts it, so its class never reaches this container. The
 *  comment read "a bare div" until task 173 made that false (phase 8, I8). */
async function arrive(path: string, options: { strict?: boolean } = {}) {
  const router = routerAt(path);
  const container = document.createElement('div');
  document.body.append(container);
  const root = createRoot(container);
  const tree = (
    <I18nextProvider i18n={createI18n()}>
      <RouterProvider router={router} />
    </I18nextProvider>
  );
  await act(async () => {
    root.render(options.strict === true ? <StrictMode>{tree}</StrictMode> : tree);
  });
  mounted = { root, container, router };
  return { container, router };
}

function release(): void {
  if (mounted) {
    const { root, container, router } = mounted;
    act(() => root.unmount());
    container.remove();
    router.dispose();
    mounted = undefined;
  }
  clearAccessToken();
}

/** What is actually sitting in storage, read with no helper in between (AC7, AC14). */
function stored(): string | null {
  return globalThis.sessionStorage.getItem(RETURN_TARGET_KEY);
}

/** The heading the sign-in screen renders, asserted as literal English AND against the catalogue. */
function headingIn(container: HTMLElement): string | null {
  return container.querySelector('h1')?.textContent ?? null;
}

let redirectCode: string | undefined;

/**
 * `RedirectToSignIn.tsx`'s own source, comments stripped.
 *
 * Stripping is not optional, and this module is the strongest case for it in the package: its header NAMES
 * `redirect(`, `/app/sign-in`, `useLayoutEffect` and `globalThis.location` while explaining which of them it
 * does not use, and a scan that read prose would fail on the paragraph that proves the code is clean. T39's
 * AC2 scan passed on prose its own phase-9 fix had written.
 */
async function redirectSource(): Promise<string> {
  // `expect.getState().testPath` rather than `import.meta.url`. THE REASON RECORDED HERE WAS WRONG and is
  // corrected rather than repeated (task 174 phase 9 measured it, and fixed the same sentence in
  // `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:69`): under this config `import.meta.url` IS a
  // `file:` URL, and the error quoted here - "The URL must be of scheme file" - never occurs. What actually
  // happens is ENOENT, because `readFile` reads the string as a path rather than as a URL. `testPath` works,
  // `fileURLToPath(import.meta.url)` would too, and the false reason mattered because it told the next
  // reader a working pattern was broken.
  redirectCode ??= (await readFile(
    join(dirname(expect.getState().testPath as string), 'RedirectToSignIn.tsx'), 'utf8'))
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .split('\n')
    .map((line) => {
      const at = line.indexOf('//');
      return at >= 0 && !/['"`]/.test(line.slice(0, at)) ? line.slice(0, at) : line;
    })
    .join('\n');
  return redirectCode;
}

/**
 * Every surface the redirect must not reach, each paired with a snippet that WOULD trip it.
 *
 * The session READER is absent and its writers are present, for the same reason as task 40's sweep: asking
 * `accessToken.current()` is half this module's job, while writing the session from inside a render would make
 * a redirect a cause. `rememberReturnTarget` is likewise absent - calling it IS the job - but every OTHER way
 * of writing storage is forbidden, because the allowlist is only the only filter while nothing bypasses it.
 */
const FORBIDDEN: readonly [RegExp, string][] = [
  [/\bfetch\s*\(/, 'await fetch("/api/accounts/me");'],
  [/XMLHttpRequest/, 'new XMLHttpRequest();'],
  [/accounts\/me/, 'const where = "/accounts/me";'],
  [/\blocalStorage\b/, 'globalThis.localStorage.setItem("k", "v");'],
  [/\bsessionStorage\b/, 'globalThis.sessionStorage.setItem("k", "v");'],
  [/\bdocument\b/, 'document.title = "x";'],
  [/\bconsole\b/, 'console.log("arrived");'],
  [/setTimeout|setInterval/, 'setTimeout(() => undefined, 0);'],
  [/\buseState\b/, 'const [a] = useState(1);'],
  [/\buseEffect\b/, 'useEffect(() => undefined, []);'],
  [/\buseLayoutEffect\b/, 'useLayoutEffect(() => undefined, []);'],
  [/\bglobalThis\.location\b/, 'globalThis.location.assign("/x");'],
  [/\bretainAccessToken\b/, 'retainAccessToken("x");'],
  [/\bclearAccessToken\b/, 'clearAccessToken();'],
  [/\bretainIdToken\b/, 'retainIdToken("x");'],
  [/\btakeIdToken\b/, 'takeIdToken();'],
  [/\badopt\b/, 'accessToken.adopt("x");'],
  [/\brenew\b/, 'void accessToken.renew();'],
  [/\bsignOut\s*\(/, 'signOut();'],
  [/\bendSession\s*\(/, 'endSession();'],
  [/\bmarkSignedOut\b/, 'markSignedOut();'],
  [/\bmarkSignedIn\b/, 'markSignedIn();'],
  [/\bbroadcastSignOut\b/, 'broadcastSignOut();'],
  [/\blistenForSignOut\b/, 'listenForSignOut(() => undefined);'],
  // The sign-out LATCH, as a read. A redirect that consulted it would be deciding WHY it is redirecting, and
  // the three arrivals must be indistinguishable (L4, AC9) - including to the component itself.
  [/\bhasSignedOut\b/, 'if (hasSignedOut()) return null;'],
  // The notice shown on the sign-in screen. Setting it here would name a cause to the visitor, which is the
  // one thing R28's redirect may not do - and it would do it for all three arrivals at once.
  [/\bsetSignInNotice\b/, "setSignInNotice('discarded');"],
  // The OTHER two return-target entry points. `rememberReturnTarget` is this module's job and is deliberately
  // absent from this sweep; reading or clearing the target is not, and either would make the redirect a
  // second authority over something `returnTarget.ts` owns.
  [/\bclearReturnTarget\b/, 'clearReturnTarget();'],
  [/\btakeReturnTarget\b/, 'const back = takeReturnTarget();'],
  // IMPERATIVE navigation, which is how a component navigates when it should be declaring. `<Navigate>` is
  // the declarative form and is what this module uses; `useNavigate` would move the decision into an effect.
  [/\buseNavigate\b/, 'const go = useNavigate();'],
  [/\bnavigate\s*\(/, 'navigate("/sign-in");'],
  [/\buseHref\b/, 'const href = useHref("/sign-in");'],
  // BROAD BY CHOICE, and strictly stronger than the narrow rows phase 11's F7 proposed: the module reaches
  // `window` nowhere, so one row covers `window.name`, `window.history` and anything else arriving that way.
  // The same reasoning is why F14's `document.cookie` row was declined - `\bdocument\b` already covers it,
  // and a row that can only fire when a broader one already has adds a number, not a surface.
  [/\bwindow\b/, 'window.name = "x";'],
  [/\bhistory\./, 'history.pushState(null, "", "/x");'],
  // A `switch` is a branch, and the no-branch scan below used to look only for `if`, `?`, `&&` and `||`
  // (phase 11, F1 and F4). Forbidding it here as well means the rule holds anywhere in the module, not only
  // in the region between the arrival and the allowlist.
  [/\bswitch\b/, 'switch (arrival) { default: break; }'],
  [/\bredirect\s*\(/, 'return redirect("/sign-in");'],
  [/from ['"]\.\.\/app\/(?!routes)/, "import { AppShell } from '../app/AppShell';"],
  [/\bt\s*\(/, 'const copy = t("signIn.heading");'],
];

describe('unauthenticatedRoutesRedirectToSignIn', () => {
  describe('T41 AC1 arrival (a): a blocked authenticated path', () => {
    it('sends all eleven to the sign-in start, replacing the entry (AC1, AC6, R28)', async () => {
      for (const path of AUTHENTICATED_ROUTE_PATHS) {
        const { container, router } = await arrive(`${APP_BASENAME}/${concrete(path)}`);

        expect(router.state.location.pathname, path).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
        // REPLACE, not PUSH. A pushed redirect leaves the route that refused the visitor on the back stack,
        // so Back bounces straight off it again - the trap task 25's suite forbids at its line 599.
        expect(router.state.historyAction, `${path} pushed rather than replaced`).toBe('REPLACE');
        // And the screen really arrived: a destination with no render would be a blank page at a new address.
        expect(headingIn(container), path).toBe(en.signIn.heading);
        expect(headingIn(container), path).toBe('Sign in');
        release();
      }
    });

    it('does the same under StrictMode, where every component renders twice', async () => {
      const { container, router } = await arrive(`${APP_BASENAME}/${HOME_PATH}`, { strict: true });
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      expect(router.state.historyAction).toBe('REPLACE');
      expect(headingIn(container)).toBe(en.signIn.heading);
    });

    it('does not count an empty-string token as a session (AC1)', async () => {
      // `retainAccessToken` assigns whatever string it is given (`accessToken.ts:90` is `held = token;`), so
      // `current()` can return `''`. An empty token is not a session, in BOTH components that ask.
      retainAccessToken('');
      expect(accessToken.current()).toBe('');
      const { router } = await arrive(`${APP_BASENAME}/${HOME_PATH}`);
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
    });
  });

  describe('T41 AC2 arrival (b): a path that matches nothing', () => {
    it('sends an unmatched path to the sign-in start (AC2, R28)', async () => {
      const { container, router } = await arrive(`${APP_BASENAME}/no-such-route`);
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      expect(router.state.historyAction).toBe('REPLACE');
      expect(headingIn(container)).toBe(en.signIn.heading);
    });

    it('sends a path that RESEMBLES a public one to the sign-in start too (AC2)', async () => {
      // `sign-in-x` is not `sign-in`. React Router matches whole segments, and this is the case that would
      // fail if the catch-all were ever given a prefix match or the public set a wildcard.
      const { router } = await arrive(`${APP_BASENAME}/${SIGN_IN_PATH}-x`);
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      expect(router.state.historyAction).toBe('REPLACE');
    });
  });

  describe('T41 AC3 arrival (c): the bare /app', () => {
    it('sends /app/ to the sign-in start (AC3, R28)', async () => {
      const { container, router } = await arrive(`${APP_BASENAME}/`);
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      expect(router.state.historyAction).toBe('REPLACE');
      expect(headingIn(container)).toBe(en.signIn.heading);
    });

    it('sends /app with no trailing slash through the same index route (AC3)', async () => {
      const { router } = await arrive(APP_BASENAME);
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      expect(router.state.historyAction).toBe('REPLACE');
    });
  });

  describe('T41 AC4 the nine public routes are still served', () => {
    it('matches every one of the nine to its own leaf, callback included (AC4, L14)', () => {
      // MATCHING ONLY, which is why `callback` can be in this arm: the router is created and never rendered,
      // so nothing in any screen runs. This is also the render-free pin that the index route is NARROW -
      // `/app/sign-in` matches `sign-in`, not the index (phase 8, I5).
      for (const path of PUBLIC_ROUTE_PATHS) {
        const router = routerAt(`${APP_BASENAME}/${path}`);
        expect(router.state.matches.at(-1)?.route.path, path).toBe(path);
        router.dispose();
      }
    });

    it('renders eight of them without redirecting, and stays put (AC4, R28)', async () => {
      // `callback` is excluded and the file header says why: its screen acts on arrival, and that is task 6's
      // behaviour, not task 41's. Six of the eight have no element yet, so "not redirected" is the claim -
      // the location is unchanged and the action is the initial POP.
      for (const path of PUBLIC_ROUTE_PATHS.filter((candidate) => candidate !== CALLBACK_PATH)) {
        const { router } = await arrive(`${APP_BASENAME}/${path}`);
        expect(router.state.location.pathname, path).toBe(`${APP_BASENAME}/${path}`);
        expect(router.state.historyAction, `${path} was redirected, and it is public`).toBe('POP');
        release();
      }
    });

    it('serves the signed-out landing, which is the one a redirect loop would eat (AC4, R19)', async () => {
      // R19's landing is reached WITHOUT a session by definition - the session just ended - so a redirect
      // applied one level too high would send it to sign-in and R19 would be unreachable in production while
      // every structural pin still passed.
      const { container, router } = await arrive(`${APP_BASENAME}/${SIGNED_OUT_PATH}`);
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGNED_OUT_PATH}`);
      expect(headingIn(container)).toBe(en.signedOut.heading);
    });

    it('serves the sign-in screen itself at its own path, with no loop (AC4, phase 8 I5)', async () => {
      const { container, router } = await arrive(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      expect(router.state.historyAction, 'the sign-in screen redirected to itself').toBe('POP');
      expect(headingIn(container)).toBe(en.signIn.heading);
    });

    it('keeps the query string on an emailed public link (AC4)', async () => {
      const { router } = await arrive(`${APP_BASENAME}/verify?token=abc`);
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/verify`);
      expect(router.state.location.search).toBe('?token=abc');
      expect(router.state.historyAction).toBe('POP');
    });

    it('serves a public path arriving with a trailing slash (AC4)', async () => {
      const { router } = await arrive(`${APP_BASENAME}/verify/`);
      expect(router.state.matches.at(-1)?.route.path).toBe('verify');
      expect(router.state.historyAction).toBe('POP');
    });
  });

  describe('T41 AC5 a visitor WITH a session', () => {
    it('is not redirected from any of the eleven (AC5)', async () => {
      for (const path of AUTHENTICATED_ROUTE_PATHS) {
        retainAccessToken('a-token-this-tab-holds');
        const { router } = await arrive(`${APP_BASENAME}/${concrete(path)}`);
        expect(router.state.location.pathname, path).toBe(`${APP_BASENAME}/${concrete(path)}`);
        expect(router.state.historyAction, `${path} was refused to a visitor holding a token`).toBe('POP');
        // CHEAP CROSS-CHECK, NOT THE INSTRUMENT phase 11's F2 took it for. On an authenticated path WITH a
        // session the gate renders `<Outlet />`, so `RedirectToSignIn` never mounts and the order of reads
        // inside it cannot be measured from here - the arrivals that mount it for a signed-in visitor are
        // the catch-all and the index route, which the two cases below cover. What this holds is the wider
        // claim that NOTHING on the authenticated-with-session path writes a return target.
        expect(stored(), `${path} stored a return target for a visitor who already has a session`).toBeNull();
        release();
      }
    });

    it('is not redirected from a path that matches nothing either, and nothing renders (F7, R28)', async () => {
      // R28 is conditioned WHILE a visitor is unauthenticated, so a signed-in visitor at an address that
      // matches nothing is OUTSIDE the requirement. Task 40's blank page is kept for them deliberately:
      // redirecting them would log them out of their own address bar, and `tasks.md:72` says only that a
      // session renders the shell and the authenticated routes.
      retainAccessToken('a-token-this-tab-holds');
      const { container, router } = await arrive(`${APP_BASENAME}/no-such-route`);
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/no-such-route`);
      expect(router.state.historyAction).toBe('POP');
      expect(container.innerHTML, 'something rendered for an authenticated unmatched path').toBe('');
      expect(stored(), 'a return target was stored for a visitor who already has a session').toBeNull();
    });

    it('is not redirected from the bare /app/ either (F7)', async () => {
      retainAccessToken('a-token-this-tab-holds');
      const { container, router } = await arrive(`${APP_BASENAME}/`);
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/`);
      expect(container.innerHTML).toBe('');
      // The same assertion as the unmatched-path case above, and here it is load-bearing rather than cheap:
      // the index route is OUTSIDE the gate, so this arrival really does mount the redirect for a signed-in
      // visitor, and a return target written before the session check would land in their storage (F5).
      expect(stored(), 'a return target was stored for the bare /app/ visitor who has a session').toBeNull();
    });
  });

  describe('T41 AC7, AC14 the return target', () => {
    it('remembers the arrival for a real authenticated path, basename-free (AC7, AC14)', async () => {
      await arrive(`${APP_BASENAME}/account`);

      // THE EXACT STORED VALUE, which is the whole of AC14. `/app/account` is not in the allowlist -
      // `returnTarget.ts` builds it from `AUTHENTICATED_ROUTE_PATHS` as `/account` - so a target stored with
      // the prefix still on it would be refused in SILENCE, and the deep link would be gone with no throw,
      // no log, and a visitor who simply lands on home. Measured at phase 9: `useLocation().pathname` is
      // already basename-relative and `router.state.location.pathname` is the one that carries the prefix.
      expect(stored()).toBe('/account');
      expect(stored()).not.toBe(`${APP_BASENAME}/account`);
      // And the far end agrees: the reader validates, deletes and hands back the same path.
      expect(takeReturnTarget()).toBe('/account');
      expect(stored(), 'the reader did not delete it').toBeNull();
    });

    it('remembers every rememberable authenticated path as its own leading-slash form (AC14)', async () => {
      for (const path of REMEMBERABLE) {
        await arrive(`${APP_BASENAME}/${path}`);
        expect(stored(), path).toBe(`/${path}`);
        release();
        globalThis.sessionStorage.clear();
      }
    });

    it('remembers NOTHING for a parameterised path, by the allowlist (AC7, phase 3 F9)', async () => {
      // `invoices/:id` is excluded from the allowlist at its source (T24-D5), so `/invoices/<uuid>` redirects
      // and remembers nothing. There is no branch in the redirect for this: the filter has one home.
      const { router } = await arrive(`${APP_BASENAME}/invoices/${SAMPLE_ID}`);
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      expect(stored(), 'a parameterised deep link was stored').toBeNull();
    });

    it('remembers nothing for arrival (b) or arrival (c) (AC7)', async () => {
      await arrive(`${APP_BASENAME}/no-such-route`);
      expect(stored(), 'an unmatched path was stored as a return target').toBeNull();
      release();
      globalThis.sessionStorage.clear();

      await arrive(`${APP_BASENAME}/`);
      expect(stored(), 'the bare /app/ was stored as a return target').toBeNull();
    });

    it('remembers nothing for a DOUBLED basename, which is the defect phase 9 removed', async () => {
      // `/app/app/account` matches the catch-all, and `useLocation()` gives `/app/account`. The first version
      // of this module stripped a leading `/app/` from the arrival, and this is the ONE arrival where that
      // strip could fire - turning a route the visitor never asked for into a remembered `/account`. Nothing
      // else in the suite can see it, which is why it is a case of its own.
      const { router } = await arrive(`${APP_BASENAME}${APP_BASENAME}/account`);
      expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      expect(stored(), 'a doubled prefix was normalised into a real target').toBeNull();
    });

    it('stores the right VALUE under StrictMode, and this suite never counts writes (phase 3 F4)', async () => {
      // The write happens during render, and StrictMode renders twice, so it writes twice with the same
      // value. A case asserting a write COUNT would be asserting React's internals; the value is the claim.
      await arrive(`${APP_BASENAME}/account`, { strict: true });
      expect(stored()).toBe('/account');
    });
  });

  describe('T41 AC8, AC10 nothing new is added', () => {
    it('writes only L16\'s return-target key (AC8)', async () => {
      const setItem = vi.spyOn(Storage.prototype, 'setItem');
      await arrive(`${APP_BASENAME}/account`);

      expect(setItem.mock.calls.length, 'nothing was written at all').toBeGreaterThan(0);
      expect([...new Set(setItem.mock.calls.map(([key]) => key))]).toEqual([RETURN_TARGET_KEY]);
    });

    it('adds no catalogue entry of its own (AC10, L11)', () => {
      // The redirect renders nothing, so it has no copy; what this forbids is a later "redirecting…" sentence
      // appearing in the catalogue, which would also be a cause named to an unauthenticated prober (L4).
      expect(JSON.stringify(en)).not.toMatch(/redirect/i);
    });

    it('renders byte-identical output to a direct arrival at the sign-in screen (AC10)', async () => {
      const { container: viaRedirect } = await arrive(`${APP_BASENAME}/${HOME_PATH}`);
      const redirected = viaRedirect.innerHTML;
      release();

      const { container: direct } = await arrive(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      // The redirect contributes NO markup of its own: not a wrapper, not a notice, not a word.
      expect(redirected).toBe(direct.innerHTML);
    });
  });

  describe('T41 AC9 the three arrivals are indistinguishable (L4)', () => {
    it('gives the same destination, the same action and the same markup for (a), (b) and (c)', async () => {
      const seen: { where: string; action: string; html: string }[] = [];
      for (const arrival of [`${APP_BASENAME}/${HOME_PATH}`, `${APP_BASENAME}/no-such-route`,
        `${APP_BASENAME}/`]) {
        const { container, router } = await arrive(arrival);
        seen.push({
          where: router.state.location.pathname,
          action: router.state.historyAction,
          html: container.innerHTML,
        });
        release();
        globalThis.sessionStorage.clear();
      }

      expect(new Set(seen.map((row) => row.where)).size, 'the arrivals end somewhere different').toBe(1);
      expect(new Set(seen.map((row) => row.action)).size, 'the arrivals differ in history action').toBe(1);
      expect(new Set(seen.map((row) => row.html)).size, 'the arrivals render differently').toBe(1);
      expect(seen[0].where).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      // SAMENESS IS NOT CORRECTNESS (phase 11, F3): three arrivals that all PUSHED would collapse to one
      // action and pass every assertion above. The shared value has to be named.
      expect(seen[0].action, 'the arrivals agree on a history action that is not REPLACE').toBe('REPLACE');
      // The ONLY thing that differs is the return target, and it differs in the visitor's own storage rather
      // than on the page: (a) remembers, (b) and (c) do not, which the AC7 cases above pin.
      expect(seen[0].html).not.toBe('');
    });
  });

  describe('T41 AC11 the destination and the source', () => {
    it('builds the destination from SIGN_IN_PATH, in the `to` prop itself (AC11)', async () => {
      const code = await redirectSource();
      expect(code, 'the destination is not built from SIGN_IN_PATH')
        .toMatch(/to=\{`\/\$\{SIGN_IN_PATH\}`\}/);
      // A hardcoded spelling would work today and break silently the day the constant changes - and the
      // broken destination would match the catch-all, which redirects, which is a loop.
      expect(code, 'a literal sign-in path is spelled in the source').not.toMatch(/['"]\/?sign-in['"]/);
    });

    it('reads SIGN_IN_PATH inside the function, never at module scope (AC11)', async () => {
      // T16 measured that a module-scope read of a route constant yields `undefined` rather than throwing,
      // so `/undefined` would be a live destination that matches the catch-all.
      const code = await redirectSource();
      const body = code.slice(code.indexOf('export function RedirectToSignIn'));
      expect(body, 'the component body was not found, so this scan reads the whole file')
        .toContain('SIGN_IN_PATH');
      const outside = code.slice(0, code.indexOf('export function RedirectToSignIn'))
        .split('\n')
        .filter((line) => line.includes('SIGN_IN_PATH') && !line.trimStart().startsWith('import'));
      expect(outside, 'SIGN_IN_PATH is read at module scope').toEqual([]);
    });

    it('replaces rather than pushes, in the source as well as the behaviour (AC6)', async () => {
      const code = await redirectSource();
      expect(code, 'the Navigate does not replace').toMatch(/<Navigate\b[^>]*\breplace\b/);
    });

    it('puts no branch on the arrival before remembering it (AC7, phase 8 I3)', async () => {
      // THE RULE THIS ENFORCES IS A SOURCE RULE, and phase 8 is why it is here. A branch skipping the write
      // for `/` or for an unmatched path is behaviourally INVISIBLE - the allowlist refuses those anyway - so
      // the mutation harness scored it unfalsifiable until I3 pointed out that "the allowlist is the only
      // filter" is a claim about where a rule LIVES. A second copy of the rule is the defect, and only the
      // source can show it.
      const code = await redirectSource();
      const call = code.indexOf('rememberReturnTarget(');
      const read = code.indexOf('const { pathname }');
      // BOTH ANCHORS MUST EXIST, or the slice between them is empty and this case passes while measuring
      // nothing (phase 11, F4). A rename of the local would land here, as a failure that says so, rather
      // than silently reducing the scan to `''.not.toMatch(...)`.
      expect(call, 'the call was not found, so this scan measures nothing').toBeGreaterThan(0);
      expect(read, 'the arrival read was not found, so this scan measures nothing').toBeGreaterThan(0);
      expect(read, 'the arrival is read AFTER it is remembered').toBeLessThan(call);
      const before = code.slice(read, call);
      expect(before).toContain('const { pathname }');
      // Every control-flow keyword, not only `if` and the operators. A `switch` whose default case skipped
      // the write would be behaviourally invisible - the allowlist refuses those paths anyway - and would
      // still be a second copy of the rule.
      expect(before, 'a branch stands between the arrival and the allowlist')
        .not.toMatch(/\bif\b|\bswitch\b|\bwhile\b|\bfor\b|\btry\b|\bdo\b|\?|&&|\|\|/);
    });

    it('asks whether a session is held BEFORE it reads or remembers anything (phase 11, F6)', async () => {
      // THE ORDER IS LOAD-BEARING AND BEHAVIOUR CANNOT FULLY SEE IT. A return target written for a visitor
      // who already has a session is a stale deep link waiting in their storage for a sign-in that is not
      // coming; the arrivals that would expose it are the two outside the gate, and those are covered. This
      // is the claim stated where it actually lives - in the order of three reads in one function body.
      const code = await redirectSource();
      const body = code.slice(code.indexOf('export function RedirectToSignIn'));
      const ask = body.indexOf('accessToken.current()');
      const location = body.indexOf('useLocation(');
      const remember = body.indexOf('rememberReturnTarget(');
      for (const [label, at] of [['the session read', ask], ['the arrival read', location],
        ['the remember call', remember]] as const) {
        expect(at, `${label} was not found, so this scan measures nothing`).toBeGreaterThan(-1);
      }
      expect(ask, 'the arrival is read before the session is checked').toBeLessThan(location);
      expect(ask, 'the arrival is remembered before the session is checked').toBeLessThan(remember);
    });

    it('reaches for nothing it should not, by source (AC10, phase 3 F17, F18)', async () => {
      const code = await redirectSource();
      for (const [forbidden] of FORBIDDEN) {
        expect(forbidden.test(code), `the redirect reaches ${forbidden.source}`).toBe(false);
      }
    });

    it('CONTROL: EVERY pattern in the sweep fires on a planted reach, and the sweep cannot shrink', async () => {
      const code = await redirectSource();
      for (const [forbidden, probe] of FORBIDDEN) {
        // (1) The pattern must see its own probe, or the row is a typo that forbids nothing.
        expect(forbidden.test(probe),
          `the scan cannot see a planted ${forbidden.source} - the pattern or the probe is wrong`).toBe(true);
        // (2) And the real source must not ALREADY trip it, or the row above proves nothing. T40's AC7 shipped
        // without this half and two of its rows were measuring the probe only.
        expect(forbidden.test(code),
          `the real source already trips ${forbidden.source}, so that entry proves nothing`).toBe(false);
      }
      // (3) A deleted row would leave the loop passing on fewer surfaces, and a DUPLICATED one would mean a
      // surface swept twice and another not at all while the length still looked right (T40 phase 11, F2).
      expect(FORBIDDEN, 'the forbidden-surface sweep shrank').toHaveLength(37);
      expect(new Set(FORBIDDEN.map(([pattern]) => pattern.source)),
        'two rows of the sweep hold the same pattern').toHaveLength(FORBIDDEN.length);
    });

    it('asks the session owner, which is one of the two things it is allowed to reach for (AC5)', async () => {
      const code = await redirectSource();
      expect(code, 'the redirect does not ask whether a session is held')
        .toMatch(/accessToken\.current\s*\(\s*\)/);
      expect(code, 'the redirect does not remember the arrival').toContain('rememberReturnTarget(');
    });
  });

  describe('T41 AC13 the route set is unchanged', () => {
    it('still declares nine public paths, eleven authenticated and one catch-all (AC13, L14)', () => {
      // The SHAPE of the table is pinned in `routesResolveUnderAppPrefixOnly.test.tsx`, which task 41 grew.
      // What is asserted here is only the size of the two sets this file iterates, so an arm that loops over
      // them cannot silently loop over fewer.
      expect(PUBLIC_ROUTE_PATHS).toHaveLength(9);
      expect(AUTHENTICATED_ROUTE_PATHS).toHaveLength(11);
      expect(REMEMBERABLE.length, 'every authenticated path became parameterised, so AC14 loops over nothing')
        .toBeGreaterThan(0);
      expect(REMEMBERABLE.length).toBeLessThan(AUTHENTICATED_ROUTE_PATHS.length);
    });
  });
  /**
   * TASK 42 — THE AUTHENTICATED LAYOUT. R28's POSITIVE CLAUSE.
   *
   * Prefixed, like every describe above it, because T41's header reserved that convention for this task:
   * *"a failure naming 'AC1' must say whose AC1 it is."* These are task 42's AC1-AC15, and T41's are above.
   *
   * WHAT THIS BLOCK ADDS THAT NOTHING ELSE CAN SEE. T41's cases prove a blocked arrival is redirected; task
   * 40's prove the gate splits the set. Neither can see what a visitor WITH a session is shown, because
   * until this task ten of the eleven paths rendered an empty document and the eleventh hung straight off
   * the gate. Every case here is about that side of the split.
   */
  describe('T42 the authenticated layout', () => {
    /** Any file under `src/`, comments stripped. `testPath` is this file; the layout lives one dir over. */
    async function srcFile(relative: string): Promise<string> {
      const code = await readFile(
        join(dirname(expect.getState().testPath as string), '..', relative), 'utf8');
      return code.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');
    }

    const banner = (container: HTMLElement) => container.querySelector('header');
    const signOutButton = (container: HTMLElement) => container.querySelector('header button');

    /**
     * Every navigation mechanism, watched rather than just `assign`.
     *
     * The SIXTH copy of this helper in the package (OQ-T32-4 counts them), kept rather than imported for the
     * reason task 36's own version gives: the failure worth catching is an ADDITION, and a spy on `assign`
     * alone reports one call while the SPA has grown two hand-overs.
     */
    function watchNavigation(): string[] {
      const seen: string[] = [];
      const fake = {
        origin: 'https://app.example',
        assign: (url: string) => void seen.push(`assign:${url}`),
        replace: (url: string) => void seen.push(`replace:${url}`),
      } as unknown as Location;
      Object.defineProperty(fake, 'href', {
        get: () => 'https://app.example/app/home',
        set: (url: string) => void seen.push(`href:${url}`),
        configurable: true,
      });
      vi.stubGlobal('location', fake);
      vi.stubGlobal('open', (url?: string | URL) => {
        seen.push(`open:${String(url)}`);
        return null;
      });
      return seen;
    }

    it('AC1 renders the layout on every one of the eleven, including the two parameterised ones', async () => {
      // THE CLAIM THE TASK STATEMENT MAKES AND NOTHING MEASURED: "a session renders the shell and the
      // authenticated routes". Ten of these eleven still have no screen of their own, which is deliberate -
      // each is a later unit's - so the layout is the whole of what a visitor is shown on ten of them, and
      // the difference between "an empty document" and "the application" is this case.
      for (const path of AUTHENTICATED_ROUTE_PATHS) {
        retainAccessToken('a-token-this-tab-holds');
        const { container, router } = await arrive(`${APP_BASENAME}/${concrete(path)}`);

        expect(router.state.location.pathname, path).toBe(`${APP_BASENAME}/${concrete(path)}`);
        expect(banner(container), `${path} rendered no chrome, so the visitor got an empty document`)
          .not.toBeNull();
        expect(banner(container)?.textContent, path).toBe(en.shell.signOut);
        release();
      }
    });

    it('AC2 nests the home screen INSIDE the layout rather than replacing it', async () => {
      retainAccessToken('a-token-this-tab-holds');
      const { container } = await arrive(`${APP_BASENAME}/${HOME_PATH}`);

      const main = container.querySelector('main');
      expect(banner(container), 'the chrome is missing on the one path that has a screen').not.toBeNull();
      expect(main, 'the layout replaced the screen instead of wrapping it').not.toBeNull();
      expect(main?.querySelector('h1')?.textContent).toBe(en.home.heading);
      // Task 174's own pin, re-measured through the layout: the screen's landmark holds exactly its heading
      // and its paragraph, so the chrome is OUTSIDE `<main>` and not merely elsewhere in the document.
      expect([...(main?.children ?? [])].map((child) => child.tagName)).toEqual(['H1', 'P']);
      expect(main?.contains(banner(container)), 'the chrome rendered inside the screen\'s landmark')
        .toBe(false);
      // PHASE 11 F1. "Outside `<main>`" is satisfied by chrome rendered BELOW the screen, and the finding
      // said nothing would catch that. Two grown instruments do - `identityErrors…:1250` and
      // `sasRefusal…:866` pin the signed-in document's text as `signOut + heading + explanation`, which is
      // DOM order - but they hold it incidentally, in suites about something else entirely. The direct
      // claim belongs here, and a keyboard user is the reason: chrome that renders last is reached last.
      expect(main && (main.compareDocumentPosition(banner(container) as Node)
        & Node.DOCUMENT_POSITION_PRECEDING) !== 0,
        'the chrome renders after the screen, so a keyboard user reaches the sign-out control last')
        .toBe(true);
    });

    it('AC3 leaves focus on the screen\'s heading, which the chrome must not take', async () => {
      // THE HOLE TASK 174 OPENED AND THIS TASK COULD HAVE FALLEN INTO. `Heading` makes every `<h1>`
      // focusable, task 174 moves focus to it on arrival, and the discharged T27-D1 case in
      // `loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` asserts a real sign-in lands there.
      // A layout that focused itself would have taken that away silently.
      retainAccessToken('a-token-this-tab-holds');
      const { container } = await arrive(`${APP_BASENAME}/${HOME_PATH}`);

      expect(document.activeElement, 'the chrome took the focus the landing screen moved')
        .toBe(container.querySelector('main h1'));
    });

    it('AC8 renders exactly one h1, and takes no focus where there is no screen at all', async () => {
      // The second half is the one worth having: on the ten paths with no screen there is nothing to hold
      // focus, so if the layout ever grabbed it this is where it would show. `<body>` is correct here.
      retainAccessToken('a-token-this-tab-holds');
      const { container } = await arrive(`${APP_BASENAME}/account`);

      expect(banner(container), 'nothing rendered, so this measures nothing').not.toBeNull();
      expect(container.querySelectorAll('h1'), 'the chrome rendered a heading of its own').toHaveLength(0);
      expect(document.activeElement, 'the chrome took focus on a path with no screen').toBe(document.body);
    });

    it('AC9 is a <header> banner outside every landmark that would demote it', async () => {
      // `<header>` maps to the `banner` role NATIVELY, but only while it is not inside `article`, `aside`,
      // `main`, `nav` or `section`. That makes the ancestry part of the contract, not a detail of how the
      // layout happens to be arranged today - so the ancestry is what this case reads. A task that nested
      // the layout inside a screen would demote the landmark without editing one line of `Banner.tsx`.
      retainAccessToken('a-token-this-tab-holds');
      const { container } = await arrive(`${APP_BASENAME}/${HOME_PATH}`);
      const header = banner(container);

      expect(header?.tagName).toBe('HEADER');
      expect(header?.getAttribute('role'), 'a redundant role was set on an element that carries it natively')
        .toBeNull();
      for (const demoting of ['article', 'aside', 'main', 'nav', 'section']) {
        expect(header?.closest(demoting), `the banner sits inside <${demoting}>, which demotes it to a group`)
          .toBeNull();
      }
    });

    it('AC12 holds one keyboard-reachable control, labelled from the catalogue', async () => {
      retainAccessToken('a-token-this-tab-holds');
      const { container } = await arrive(`${APP_BASENAME}/${HOME_PATH}`);
      const button = signOutButton(container);

      expect(button?.tagName).toBe('BUTTON');
      expect(button?.getAttribute('type'), 'a button that defaults to submit would navigate inside a form')
        .toBe('button');
      expect(button?.textContent).toBe(en.shell.signOut);
      expect(button?.textContent, 'the label is a raw catalogue key, not English').toBe('Sign out');
      // A negative tabindex would make it findable by code and NOT by Tab, which is the opposite of what a
      // control needs - and it is the exact attribute task 174 set on the heading for the opposite reason.
      expect(button?.getAttribute('tabindex'), 'the one control was taken out of the tab order').toBeNull();
      expect(container.querySelectorAll('header button'), 'the chrome grew a second control').toHaveLength(1);
    });

    it('AC13 hands over to SAS exactly once when the control is activated, through endSession', async () => {
      // THE FIRST CALLER THE SIGN-OUT PATH HAS EVER HAD (OQ-T36-5, carried for seven tasks). The assertion
      // is on the REAL hand-over rather than on a mocked module: the button calls `endSession()`, which
      // clears this tab, broadcasts, and leaves for the end-session endpoint - and R19 is only satisfied if
      // all three happen, in that order, once.
      retainAccessToken('a-token-this-tab-holds');
      const { container } = await arrive(`${APP_BASENAME}/${HOME_PATH}`);
      const navigations = watchNavigation();
      try {
        await act(async () => {
          (signOutButton(container) as HTMLButtonElement).click();
        });

        expect(navigations, `expected one hand-over, saw: ${navigations.join(', ')}`).toHaveLength(1);
        const [mechanism] = navigations;
        expect(mechanism?.startsWith('assign:'), `the mechanism was ${String(mechanism)}`).toBe(true);
        expect(mechanism, 'the hand-over did not go to the end-session endpoint (R19)')
          .toContain('/connect/logout');
        expect(accessToken.current(), 'the tab kept its token through its own sign-out').toBeNull();
      } finally {
        vi.unstubAllGlobals();
      }
    });

    it('AC13 reaches for none of the pieces endSession composes, by source', async () => {
      // `endSession()` takes the id token, signs out, broadcasts and navigates, and ITS OWN HEADER says the
      // order is the criterion. A layout that composed any of that itself would be a second sign-out path
      // that happened to look right today. The allowlist is the first assertion; the named reaches are the
      // second, because an allowlist passes a file that imports nothing and reaches a global.
      const code = await srcFile('app/AuthenticatedLayout.tsx');
      const imports = [...code.matchAll(/from '([^']+)'/g)].map((match) => match[1]);

      expect(imports.sort(), 'the layout reaches a module its contract does not allow').toEqual([
        '../api/crossTabSignOut', '../auth/RedirectToSignIn', '../auth/endSession', '../auth/signOut',
        './ui', 'react', 'react-i18next', 'react-router',
      ].sort());
      // PHASE 8 I4. A bare `onClick={endSession}` hands the handler React's `MouseEvent`, which the current
      // signature drops and `tsc` cannot object to - `(hint?: string) => void` is assignable to `() => void`,
      // so the day someone adds an optional parameter the click event arrives as the hint. Nothing
      // BEHAVIOURAL can see that today, which is why this is a shape assertion and sits here rather than in
      // the case above: `endSession` is not mocked there, deliberately, and the hazard has no effect yet.
      expect(code, 'the handler is a bare reference, so React hands endSession the click event')
        .toMatch(/onClick=\{\(\) => endSession\(\)\}/);
      for (const forbidden of [/\btakeIdToken\b/, /\bretainIdToken\b/, /\bbroadcastSignOut\b/,
        /\bmarkSignedOut\b/, /\bmarkSignedIn\b/, /\bhasSignedOut\b/, /\bEND_SESSION_PATH\b/,
        /\baccessToken\b/, /\bglobalThis\.location\b/, /\bhistory\b/, /\bdocument\.cookie\b/,
        /\bsessionStorage\b/, /\blocalStorage\b/, /indexedDB/i, /\bfetch\s*\(/, /XMLHttpRequest/,
        /userinfo/i, /accounts\/me/, /\buseNavigate\b/]) {
        expect(code, `the layout reaches ${String(forbidden)}`).not.toMatch(forbidden);
      }
    });

    it('CONTROL: the forbidden sweep above sees a planted reach, and does not already trip', async () => {
      // Without this, a typo in any pattern forbids nothing and the case above passes by accident - T40's
      // AC7 shipped with two rows that were measuring their own probe.
      const code = await srcFile('app/AuthenticatedLayout.tsx');
      for (const [forbidden, probe] of [
        [/\btakeIdToken\b/, 'takeIdToken();'], [/\bretainIdToken\b/, 'retainIdToken("x");'],
        [/\bbroadcastSignOut\b/, 'broadcastSignOut();'], [/\bmarkSignedOut\b/, 'markSignedOut();'],
        [/\bmarkSignedIn\b/, 'markSignedIn();'], [/\bhasSignedOut\b/, 'if (hasSignedOut()) return null;'],
        [/\bEND_SESSION_PATH\b/, 'const to = END_SESSION_PATH;'],
        [/\baccessToken\b/, 'accessToken.current();'],
        [/\bglobalThis\.location\b/, 'globalThis.location.assign("/x");'],
        [/\bhistory\b/, 'globalThis.history.back();'], [/\bdocument\.cookie\b/, 'document.cookie = "a=b";'],
        [/\bsessionStorage\b/, 'globalThis.sessionStorage.setItem("k", "v");'],
        [/\blocalStorage\b/, 'globalThis.localStorage.setItem("k", "v");'],
        [/indexedDB/i, 'globalThis.indexedDB.open("db");'], [/\bfetch\s*\(/, 'await fetch("/accounts/me");'],
        [/XMLHttpRequest/, 'new XMLHttpRequest();'], [/userinfo/i, 'const at = "/userinfo";'],
        [/accounts\/me/, 'const at = "/accounts/me";'], [/\buseNavigate\b/, 'const go = useNavigate();'],
      ] as [RegExp, string][]) {
        expect(forbidden.test(probe), `the pattern ${String(forbidden)} cannot see its own probe`).toBe(true);
        expect(forbidden.test(code), `the real source already trips ${String(forbidden)}`).toBe(false);
      }
    });

    it('AC14 signs this tab out and redirects it when a SIBLING tab signs out', async () => {
      // R22's last clause - "sign-out SHALL propagate to every open tab" - built in task 39 and never
      // installed: `signOutPropagatesToAllTabs.test.ts` verified the mechanism and said so in its header,
      // because no module owned an app-level listener. This is the installer, and this is the journey.
      retainAccessToken('a-token-this-tab-holds');
      const { container, router } = await arrive(`${APP_BASENAME}/${HOME_PATH}`);
      expect(banner(container), 'nothing rendered, so the broadcast below proves nothing').not.toBeNull();

      const sibling = new BroadcastChannel(SIGN_OUT_CHANNEL);
      try {
        await act(async () => {
          sibling.postMessage({ type: SIGN_OUT_TYPE });
          await Promise.resolve();
        });
      } finally {
        sibling.close();
      }

      expect(accessToken.current(), 'the tab kept its token after a sibling signed out').toBeNull();
      expect(router.state.location.pathname, 'the tab stayed on an authenticated screen with no session')
        .toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      expect(banner(container), 'the chrome is still rendered for a tab with no session').toBeNull();
      expect(headingIn(container), 'the redirected tab shows something other than the sign-in screen')
        .toBe(en.signIn.heading);
    });

    it('AC14 closes the listener on unmount, so an unmounted layout signs nothing out', async () => {
      retainAccessToken('a-token-this-tab-holds');
      await arrive(`${APP_BASENAME}/${HOME_PATH}`);
      release();
      retainAccessToken('a-token-a-later-screen-holds');

      const sibling = new BroadcastChannel(SIGN_OUT_CHANNEL);
      try {
        await act(async () => {
          sibling.postMessage({ type: SIGN_OUT_TYPE });
          await Promise.resolve();
        });
      } finally {
        sibling.close();
      }

      expect(accessToken.current(), 'an unmounted layout still listens and signed this tab out')
        .toBe('a-token-a-later-screen-holds');
    });

    it('AC4, AC15 renders no part of the chrome without a session, nor for an empty-string token', async () => {
      // The empty string is the case `RequireSession` exists to refuse (`retainAccessToken` assigns whatever
      // it is given), and it matters more now than it did: what sits behind the gate is no longer nothing,
      // it is a control that ends a session.
      for (const token of [undefined, '']) {
        if (token !== undefined) retainAccessToken(token);
        const { container, router } = await arrive(`${APP_BASENAME}/account`);

        expect(router.state.location.pathname, `token ${JSON.stringify(token)}`)
          .toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
        expect(banner(container), `the chrome rendered for token ${JSON.stringify(token)}`).toBeNull();
        expect(container.textContent, 'the sign-out control reached a visitor with no session')
          .not.toContain(en.shell.signOut);
        release();
      }
    });

    it('AC5 adds no path: the layout is pathless and the two declared sets are untouched', () => {
      const gate = (appRoutes[0]?.children ?? []).find(
        (route) => route.path === undefined && route.children !== undefined);
      const layouts = gate?.children ?? [];

      expect(layouts, "the gate's children are not exactly one layout route").toHaveLength(1);
      expect(layouts[0]?.path, 'the layout took a path, which changes what the bare /app/ matches')
        .toBeUndefined();
      expect((layouts[0]?.element as ReactElement).type).toBe(AuthenticatedLayout);
      expect((layouts[0]?.children ?? []).map((route) => route.path))
        .toEqual([...AUTHENTICATED_ROUTE_PATHS]);
      expect(PUBLIC_ROUTE_PATHS).toHaveLength(9);
      expect(AUTHENTICATED_ROUTE_PATHS).toHaveLength(11);
    });

    it('AC6, AC10 says one word, takes it from the catalogue, and says nothing about who is reading', async () => {
      const code = await srcFile('app/AuthenticatedLayout.tsx');
      retainAccessToken('a-token-this-tab-holds');
      const { container } = await arrive(`${APP_BASENAME}/account`);

      // (a) no copy in the component, and the one string it renders is the catalogue's.
      for (const sentence of Object.values(en).flatMap((group) => Object.values(group))) {
        expect(code, `the layout spells "${sentence}" instead of taking it from the catalogue`)
          .not.toContain(sentence);
      }
      expect(container.textContent).toBe(en.shell.signOut);
      // (b) no raw key leaked, which is what a missing catalogue entry looks like in the DOM (O3).
      expect(container.textContent, 'a raw catalogue key reached the DOM').not.toMatch(/shell\./);
      // (c) and nothing identity-shaped, on a path where the chrome is the whole page.
      for (const word of ['@', 'name', 'email', 'account', 'admin', 'merchant', 'suspended', 'welcome']) {
        expect(container.textContent?.toLowerCase(), `the chrome rendered "${word}"`).not.toContain(word);
      }
    });

    it('AC11 writes no storage and makes no network call while rendering', async () => {
      const setItem = vi.spyOn(globalThis.sessionStorage, 'setItem');
      const fetchSpy = vi.spyOn(globalThis, 'fetch');
      retainAccessToken('a-token-this-tab-holds');
      const { container } = await arrive(`${APP_BASENAME}/invoices`);

      expect(banner(container), 'nothing rendered, so the spies below prove nothing').not.toBeNull();
      expect(setItem, 'the layout wrote storage on render').not.toHaveBeenCalled();
      expect(fetchSpy, 'the layout called the network on render').not.toHaveBeenCalled();
    });

    it('AC7 names the layout and the ninth primitive in the lists that pin them', async () => {
      // Read from the instrument's SOURCE, the shape task 174 used: this task's obligation is that the two
      // pinned lists name what it added, and a behavioural case cannot see a list.
      const only = await srcFile('app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx');
      expect(only, 'the render-site list does not name the layout').toContain("'app/AuthenticatedLayout.tsx',");
      expect(only, 'the primitive list does not name Banner').toContain("'Banner',");
      // GROWN BY TASK 43, which added one render site and four primitives. The numbers follow the two lists
      // rather than being frozen at what task 42 saw: this case's interest is that the layout and `Banner`
      // are PINNED somewhere the counts are exact, and a count that stopped moving would stop being exact.
      expect(only, 'the render-site count still says seven').toMatch(/toHaveLength\(8\)/);
      expect(only, 'the primitive count still says nine').toMatch(/toHaveLength\(13\)/);
      expect(only, 'the union pin no longer says twenty-one').toMatch(/\.toBe\(21\)/);
    });

    it('OQ-T36-5: the end-session suite names this layout as its first permitted importer', async () => {
      // SPLIT OUT OF THE AC7 CASE (phase 8 I8). It was a third list, in another file, about an open question
      // rather than about AC7's two pins - and an open question with no case named after it is an open
      // question nobody can find. T36 wrote `PERMITTED_IMPORTERS` empty with the instructions in its own
      // failure message, for whichever task first called the sign-out path. This is that task.
      const permitted = await srcFile('auth/signOutClearsMemoryAndEndsSasSession.test.tsx');
      expect(permitted, 'the end-session importer list does not name the layout, so OQ-T36-5 is still open')
        .toContain("'app/AuthenticatedLayout.tsx',");
    });

    // ===== PHASE 10 - the five gaps phases 7 and 8 named, each now an instrument =====================
    // Grouped together deliberately: every case below exists because a REVIEW said it was missing, and three
    // of them pin a LIMIT rather than a feature. A limit with no case is indistinguishable from an oversight.

    it('AC14 under StrictMode: the sibling journey still works when the effect is double-invoked (I5)', async () => {
      // The risk is not the double subscribe; it is the CLEANUP in between. React mounts, cleans up, mounts
      // again, so a listener keyed on anything shared - a module-level channel, a count - could be closed by
      // the first cleanup and leave the second mount deaf. That failure is silent: the chrome renders, the
      // control works, and only a sibling's broadcast can tell. The non-strict case next door cannot see it.
      retainAccessToken('a-token-this-tab-holds');
      const { container, router } = await arrive(`${APP_BASENAME}/${HOME_PATH}`, { strict: true });
      expect(banner(container), 'nothing rendered under StrictMode, so the broadcast proves nothing')
        .not.toBeNull();

      const sibling = new BroadcastChannel(SIGN_OUT_CHANNEL);
      try {
        await act(async () => {
          sibling.postMessage({ type: SIGN_OUT_TYPE });
          await Promise.resolve();
        });
      } finally {
        sibling.close();
      }

      expect(accessToken.current(), 'the double-invoked effect left no live listener, so the tab kept its token')
        .toBeNull();
      expect(router.state.location.pathname, 'the tab stayed put under StrictMode')
        .toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
    });

    it('AC14 leaves the return target pointing at where THIS tab was when the sibling signed out', async () => {
      // D11's accepted consequence, measured instead of described: `signOut()` clears the return target (T35)
      // and `RedirectToSignIn` then remembers the path this tab was on - so a visitor who signs out in tab A
      // and signs back in from tab B lands on tab B's screen, not tab A's. Nothing measured which of the two
      // wins, in either direction, which is exactly how a documented consequence becomes a surprise.
      retainAccessToken('a-token-this-tab-holds');
      const { router } = await arrive(`${APP_BASENAME}/${HOME_PATH}`);

      const sibling = new BroadcastChannel(SIGN_OUT_CHANNEL);
      try {
        await act(async () => {
          sibling.postMessage({ type: SIGN_OUT_TYPE });
          await Promise.resolve();
        });
      } finally {
        sibling.close();
      }

      expect(router.state.location.pathname, 'the tab did not reach sign-in, so the value below is not D11\'s')
        .toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      expect(stored(), 'the redirect remembered something other than the path this tab was on')
        .toBe(`/${HOME_PATH}`);
    });

    it('A TAB ON A PUBLIC PATH DOES NOT REACT to a sibling signing out, and that is a measured limit', async () => {
      // THIS CASE PINS A HOLE, NOT A FEATURE (OQ-T42-5). The listener is installed by this layout, which is
      // mounted only behind the gate, so a signed-in tab parked on `/app/sign-in` keeps its in-memory token
      // until it navigates - and R22's "every open tab" is satisfied only in the sense that the token is dead
      // at SAS. Closing it means an app-level listener, which is `AppShell`'s ground and L12 keeps this task
      // off it (D9, D10). If a later task installs one, THIS CASE MUST FAIL - that is the point of writing it.
      retainAccessToken('a-token-this-tab-holds');
      const { container, router } = await arrive(`${APP_BASENAME}/${SIGN_IN_PATH}`);
      expect(banner(container), 'the chrome rendered on a PUBLIC path, so this measures the wrong tree')
        .toBeNull();

      const sibling = new BroadcastChannel(SIGN_OUT_CHANNEL);
      try {
        await act(async () => {
          sibling.postMessage({ type: SIGN_OUT_TYPE });
          await Promise.resolve();
        });
      } finally {
        sibling.close();
      }

      expect(accessToken.current(), 'a public-path tab now reacts, so SOMETHING outside the authenticated '
        + 'layout is listening - an app-level installer exists, OQ-T42-5 is closed, and this case is the one '
        + 'that has to change').toBe('a-token-this-tab-holds');
      expect(router.state.location.pathname, 'the public tab navigated on a sibling broadcast')
        .toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
    });

    it('the banner declares no utility that could clip the focus ring the primitives pin (I6)', async () => {
      // The ring itself is measured twice in T173's suite - as `focus-visible:outline-2` and
      // `focus-visible:outline-focus` on each focusable primitive, and again in the BUILT css. Neither can see
      // a wrapper that clips it, and `outline` is drawn OUTSIDE the box: `overflow-hidden` on the chrome would
      // cut the ring off the only control in it. A computed-style assertion cannot stand in - jsdom loads no
      // stylesheet, so every element's `outline` is the empty string (phase 9, I6).
      // PHASE 11 F4: THE WRAPPER COUNTS TOO. `Banner.tsx` can be clean and the ring still clipped, by a
      // `<div className="overflow-hidden">` around `<Banner>` in the layout. The layout renders a fragment
      // today and declares no class at all, so this half of the scan passes trivially - which is the point:
      // it is a guard on a file that has nothing to guard yet.
      for (const file of ['app/ui/Banner.tsx', 'app/AuthenticatedLayout.tsx']) {
        const code = await srcFile(file);
        for (const clipping of [/\boverflow-/, /\bclip-/, /\bcontain-/, /\bmask-/]) {
          expect(code, `${file} declares ${String(clipping)}, which can clip the control's focus ring`)
            .not.toMatch(clipping);
        }
      }
      // The control is inside the banner, so the ring that must survive is ActionButton's. Named here so a
      // rename of the pin cannot leave this case guarding nothing.
      const only = await srcFile('app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx');
      expect(only, 'the focus-ring pin this case leans on is gone')
        .toContain("gives %s a visible focus ring (L11, WCAG 2.4.7)");
    });

    it('the banner landmark has NO accessible name, which is a decision and not an oversight (F5)', async () => {
      // PINS A DECISION, SO CHANGING IT HAS TO BE ONE (phase 8 I7, phase 11 F5). A screen reader navigating
      // by landmark hears "banner" with no name. That is correct while there is exactly one banner in the
      // document - a name distinguishes a landmark from its siblings - and naming it means copy, a catalogue
      // key and an owner's call, which D1 deliberately did not make. If the chrome should identify the
      // application, THIS CASE IS THE ONE TO CHANGE, and that is the whole reason it exists.
      retainAccessToken('a-token-this-tab-holds');
      const { container } = await arrive(`${APP_BASENAME}/${HOME_PATH}`);
      const header = banner(container);

      expect(header, 'nothing rendered, so the absences below prove nothing').not.toBeNull();
      expect(header?.getAttribute('aria-label'), 'the banner grew a name without a catalogue key').toBeNull();
      expect(header?.getAttribute('aria-labelledby'), 'the banner grew a name by reference').toBeNull();
      expect(header?.querySelectorAll('h1, h2, h3, h4, h5, h6'),
        'the chrome grew a heading, which names the landmark and adds copy the catalogue does not hold')
        .toHaveLength(0);
    });

    it('the banner and the screen share one content column, which was a coincidence of two literals (I9)', async () => {
      // PHASE 7 F4 AND PHASE 8 I9. `Banner` mirrors `Screen`'s width, gutter and centring so the chrome lines
      // up with the screen below it - and nothing asserted that. Change `Screen` to a wider column and the
      // banner stays at `max-w-prose`: every case passes and the control sits off the content edge. Each
      // primitive owns its own width (rule 1), so the pin is on the PAIR, not on either literal.
      const column = ['mx-auto', 'max-w-prose', 'px-6'];
      const sources = {
        Banner: await srcFile('app/ui/Banner.tsx'),
        Screen: await srcFile('app/ui/Screen.tsx'),
      };
      for (const [name, code] of Object.entries(sources)) {
        const classes = [...code.matchAll(/className="([^"]+)"/g)].flatMap((match) =>
          (match[1] ?? '').split(/\s+/));
        // A CONTROL ON THE COMPARISON ITSELF: two empty lists agree about everything, and a regex that stops
        // matching is how a sameness assertion starts passing for free (T173's R30 pin, five empty lists).
        expect(classes.length, `no className literal was found in ${name}, so the comparison is vacuous`)
          .toBeGreaterThan(3);
        for (const utility of column) {
          expect(classes, `${name} no longer carries ${utility}, so the chrome and the screen have drifted`)
            .toContain(utility);
        }
      }
    });
  });
});
