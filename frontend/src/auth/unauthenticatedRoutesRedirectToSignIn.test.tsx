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
import { StrictMode } from 'react';
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

/** Mounts the REAL table at an `/app` location. No `AppShell`: it is a bare div task 16's suite wraps. */
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
  // `expect.getState().testPath` rather than `import.meta.url`: under vitest's jsdom environment the module
  // URL is not a `file:` URL, and `readFile` rejects it - measured at phase 10, where all seven source cases
  // failed with "The URL must be of scheme file" while every behavioural case passed.
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
});
