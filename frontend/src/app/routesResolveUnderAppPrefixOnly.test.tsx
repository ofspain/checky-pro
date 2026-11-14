import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { createMemoryRouter, matchRoutes, RouterProvider, type RouteObject } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';
import { AppShell } from './AppShell';
import { APP_BASENAME, appRoutes, AUTHENTICATED_ROUTE_PATHS, createAppRouter, PUBLIC_ROUTE_PATHS,
  SIGN_IN_PATH } from './routes';

// Lets act() flush React 19 renders synchronously in this test environment.
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/** Fills every `:id` segment. The router doesn't validate it; it only exercises the parameter. */
const SAMPLE_UUID = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11';

const DECLARED_PATHS: readonly string[] = [...PUBLIC_ROUTE_PATHS, ...AUTHENTICATED_ROUTE_PATHS];

/** The screens attached so far, in route order. A task that attaches one adds it here deliberately (T16: sign-in). */
const SCREENS_ATTACHED_SO_FAR = ['sign-in', 'callback',
  'signed-out', // TASK 37 - the signed-out landing. Additive; every case below is task 16's, unchanged.
  // TASK 174 - the home screen, and the FIRST entry here that is not a public route. It goes last because
  // this list is in ROUTE ORDER and the authenticated paths sit after the public ones in the table, under
  // the gate. Still additive: no case below changed.
  //
  // This constant belongs to TASK 16 and always has. T174's design challenge asserted that task 173
  // introduced it; that is false, and the correction is recorded here because a comment is where the next
  // reader will look. T173 touched neither this list nor this file.
  'home',
] as const;

/**
 * Routes carrying an element that is NOT a screen, kept apart from the count above (TASK 41).
 *
 * A redirect is an element, and the walker below counts elements - correctly, because a pin that learned to
 * ignore a class of them would stop being one. So the redirect is named here instead, and the two lists are
 * added together at the assertion. That keeps `SCREENS_ATTACHED_SO_FAR` meaning what its name says, which is
 * what makes it a handoff: task 42 attaches a SCREEN and extends that list, not this one.
 *
 * Only the catch-all appears here. Task 41's other two redirects are on routes with no `path` - the gate's
 * negative branch and the index route - and the walker records a route only when it has one, which is why
 * the pin for an element on a PATHLESS route lives in the named suite instead (task 40's AC13).
 */
const REDIRECTS_ATTACHED_SO_FAR = ['*'] as const;

/** Every route path carrying a rendered element, by whichever of the three keys React Router accepts. */
function routesWithElements(routes: readonly RouteObject[]): string[] {
  return routes.flatMap((route) => {
    const attached = route.element !== undefined || route.Component !== undefined || route.errorElement !== undefined;
    return [...(attached && route.path !== undefined ? [route.path] : []), ...routesWithElements(route.children ?? [])];
  });
}

/** The edge prefixes that belong to the backend services, verbatim from design.md §4c note 2026-10-21 (2).
 * If the edge gains a prefix, add it here. */
const BACKEND_PREFIXES = [
  '/oauth2',
  '/login',
  '/logout',
  '/connect',
  '/.well-known',
  '/userinfo',
  '/error',
  '/accounts',
  '/admin',
  '/api-keys',
  '/api/v1',
  '/notifications',
] as const;

/** Each prefix bare, with a trailing slash, and with a representative sub-path. */
const BACKEND_PATHS = BACKEND_PREFIXES.flatMap((prefix) => [prefix, `${prefix}/`, `${prefix}/x`]);

function concrete(path: string): string {
  return path.replaceAll(':id', SAMPLE_UUID);
}

function leafPath(location: string): string | undefined | null {
  const matches = matchRoutes(appRoutes, location, APP_BASENAME);
  return matches === null ? null : matches[matches.length - 1]?.route.path;
}

/** The route tree as nested paths only, so a router's processed routes compare with the declared ones. */
function pathTree(routes: readonly RouteObject[]): unknown[] {
  return routes.map((route) => [route.path, route.children ? pathTree(route.children) : []]);
}

async function renderAt(location: string) {
  const router = createMemoryRouter(appRoutes, { basename: APP_BASENAME, initialEntries: [location] });
  toDispose = router;
  const container = document.createElement('div');
  document.body.append(container);
  const root = createRoot(container);
  mounted = { root, container };
  await act(async () => {
    root.render(
      <AppShell>
        <RouterProvider router={router} />
      </AppShell>,
    );
  });
  return { router, shellText: container.querySelector('[data-testid="app-shell"]')?.textContent };
}

let mounted: { root: Root; container: HTMLElement } | undefined;
let toDispose: { dispose(): void } | undefined;

afterEach(async () => {
  if (mounted) {
    const { root, container } = mounted;
    await act(async () => root.unmount());
    container.remove();
    mounted = undefined;
  }
  toDispose?.dispose();
  toDispose = undefined;
  window.history.replaceState(null, '', '/');
});

describe('routesResolveUnderAppPrefixOnly', () => {
  it('resolves every declared route to its own leaf under /app (AC1)', () => {
    for (const path of DECLARED_PATHS) {
      expect(leafPath(`${APP_BASENAME}/${concrete(path)}`), path).toBe(path);
    }
  });

  it('resolves an emailed link with a query string and keeps the query (AC1, AC5)', async () => {
    expect(leafPath(`${APP_BASENAME}/verify?token=abc`)).toBe('verify');
    expect(leafPath(`${APP_BASENAME}/reset?token=abc`)).toBe('reset');

    const { router } = await renderAt(`${APP_BASENAME}/verify?token=abc`);
    expect(router.state.location.pathname).toBe('/app/verify');
    expect(router.state.location.search).toBe('?token=abc');
  });

  it('resolves none of the declared routes without the /app prefix (AC2)', () => {
    for (const path of DECLARED_PATHS) {
      expect(matchRoutes(appRoutes, `/${concrete(path)}`, APP_BASENAME), path).toBeNull();
    }
  });

  it('resolves no backend edge prefix (AC3, L9)', () => {
    expect(BACKEND_PATHS).toHaveLength(36);
    for (const path of BACKEND_PATHS) {
      expect(matchRoutes(appRoutes, path, APP_BASENAME), path).toBeNull();
    }
  });

  // GROWN BY T41, which gave the catch-all the sign-in redirect. The case was "sends an unmatched /app path
  // to the SILENT catch-all, so the shell shows no text", and the catch-all is not silent any more: it was
  // always the arrival that had nowhere to go, and R28's second clause is that it goes to sign-in. What is
  // kept is the part that was always the claim - an unmatched path produces no error and no 404 text of
  // React Router's own (L11) - and what is added is where it lands.
  it('sends an unmatched /app path through the catch-all to the sign-in start (AC4, L11, R28)', async () => {
    const { router, shellText } = await renderAt(`${APP_BASENAME}/no-such-route`);

    expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
    expect(router.state.historyAction, 'pushed, so Back lands on the route that refused them').toBe('REPLACE');
    expect(router.state.matches.map((match) => match.route.path)).toEqual(['/', SIGN_IN_PATH]);
    expect(router.state.errors).toBeNull();
    // Not empty any more, because the sign-in screen renders - which is the point. What the shell must never
    // show is React Router's own English 404, and the catalogue assertion in the sign-in suite is what pins
    // the words; here it is enough that the text is the screen's and not the router's.
    expect(shellText).not.toBe('');
    expect(shellText).not.toMatch(/404|Not Found/i);
  });

  // GROWN BY T41, and this case's own title said it would be: "(T41 owns its redirect)". The bare `/app/`
  // matched the root alone and rendered nothing; an INDEX route now matches it and redirects. The pin that
  // the root still matches is kept, because the index route is a CHILD of it - a redirect that had replaced
  // the root rather than nesting under it would gate the public nine too, and that is task 40's RS13.
  it('sends the bare /app/ through its index route to the sign-in start (R28, T41)', async () => {
    const { router, shellText } = await renderAt(`${APP_BASENAME}/`);

    expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
    expect(router.state.historyAction).toBe('REPLACE');
    expect(router.state.matches.map((match) => match.route.path)).toEqual(['/', SIGN_IN_PATH]);
    expect(router.state.errors).toBeNull();
    expect(shellText).not.toBe('');
  });

  // And the form with no trailing slash, which task 41 measured at phase 4 to match the same index route.
  it('sends the bare /app through the same index route (R28, T41)', async () => {
    const { router } = await renderAt(APP_BASENAME);

    expect(router.state.location.pathname).toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
    expect(router.state.matches.map((match) => match.route.path)).toEqual(['/', SIGN_IN_PATH]);
  });

  it('declares exactly the 20 §4c paths, all relative, with the catch-all as the only wildcard (L14)', () => {
    expect(PUBLIC_ROUTE_PATHS).toEqual([
      'sign-in', 'callback', 'register', 'register/sent', 'verify', 'resend-verification', 'reset/request', 'reset',
      'signed-out',
    ]);
    expect(AUTHENTICATED_ROUTE_PATHS).toEqual([
      'home', 'account', 'account/sessions', 'account/mfa', 'api-keys', 'admin', 'invoices', 'invoices/:id', 'payments',
      'notifications', 'receipts/:id',
    ]);
    expect(new Set(DECLARED_PATHS).size).toBe(20);
    for (const path of DECLARED_PATHS) {
      expect(path.startsWith('/'), path).toBe(false);
      expect(path.includes('*'), path).toBe(false);
    }
    // GROWN BY T40, which nested the authenticated eleven under one pathless guard route. The level is named
    // EXPLICITLY rather than the assertion being loosened, because the flat form was carrying three claims
    // worth keeping: the ORDER of the root's children, that the authenticated paths appear NOWHERE else, and
    // - now - that the guard's path is `undefined` rather than a string. `path: ''` would read as `['', [...]]`
    // here and would change what the bare `/app/` matches (the case above).
    expect(pathTree(appRoutes)).toEqual([['/', [
      // GROWN BY T41: the index route for the bare `/app/`, first child of the root. It carries no `path`, so
      // it reads as `undefined` here exactly as the gate's level does - and naming it rather than loosening
      // the assertion is what keeps this pin able to say WHERE in the order it sits. An index route placed
      // after a public path would still match `/app/`, so order is not behaviour here; it is intent, and
      // intent is what a reader of the table needs.
      [undefined, []],
      ...PUBLIC_ROUTE_PATHS.map((path) => [path, []]),
      // GROWN BY T42: a second pathless level inside the gate's, which is its layout. The nesting is
      // written out rather than flattened for T41's reason one level up - the shape is the claim, and a
      // reader of this table needs to see that the eleven sit under TWO pathless routes now: the gate that
      // decides, and the layout that wraps.
      [undefined, [[undefined, AUTHENTICATED_ROUTE_PATHS.map((path) => [path, []])]]],
      ['*', []],
    ]]]);
  });

  // Narrowed by T16, which attached the first screen. It was "no route carries an element", which was the
  // route-table twin of T01's "the shell renders nothing" - and like those, it retires one exception at a time
  // rather than all at once. A task that attaches a screen must come here and name it, which is the point.
  it('attaches a screen only to the routes whose task has run (L11, L12)', () => {
    // STILL THREE AFTER T40, and that is not an accident worth leaving unsaid: T40 attached an element to a
    // route with no `path`, and the predicate below only records a route that HAS one - so a guard is not
    // counted as a screen, and this pin keeps its meaning. The filter is load-bearing from now on, so the
    // thing it cannot see is pinned where T40's claims live: `publicRouteSetIsExactlyTheDeclaredSet.test.tsx`
    // asserts that exactly ONE pathless route in the whole tree carries an element, and that it is the guard.
    expect(routesWithElements(appRoutes))
      .toEqual([...SCREENS_ATTACHED_SO_FAR, ...REDIRECTS_ATTACHED_SO_FAR]);

    // The predicate counts, rather than always returning a one-element list that happens to match: given two
    // attached routes it reports both, and given none it reports none.
    const twoAttached: RouteObject[] = [
      { path: 'a', element: <i /> },
      { path: 'b', children: [{ path: 'c', Component: () => null }] },
    ];
    expect(routesWithElements(twoAttached)).toEqual(['a', 'c']);
    expect(routesWithElements([{ path: 'plain' }])).toEqual([]);
  });

  it('creates a browser router with the /app basename and the declared routes', async () => {
    const router = createAppRouter();
    toDispose = router;

    expect(router.basename).toBe('/app');
    expect(pathTree(router.routes)).toEqual(pathTree(appRoutes));

    // Only a browser router moves the real address bar; a memory or hash router would leave the path at '/'.
    await router.navigate('/home');
    expect(window.location.pathname).toBe('/app/home');
    expect(window.location.hash).toBe('');
  });
});
