import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { createMemoryRouter, matchRoutes, RouterProvider, type RouteObject } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';
import { AppShell } from './AppShell';
import { APP_BASENAME, appRoutes, AUTHENTICATED_ROUTE_PATHS, createAppRouter, PUBLIC_ROUTE_PATHS } from './routes';

// Lets act() flush React 19 renders synchronously in this test environment.
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/** Fills every `:id` segment. The router doesn't validate it; it only exercises the parameter. */
const SAMPLE_UUID = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11';

const DECLARED_PATHS: readonly string[] = [...PUBLIC_ROUTE_PATHS, ...AUTHENTICATED_ROUTE_PATHS];

/** The screens attached so far, in route order. A task that attaches one adds it here deliberately (T16: sign-in). */
const SCREENS_ATTACHED_SO_FAR = ['sign-in'] as const;

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

  it('sends an unmatched /app path to the silent catch-all, so the shell shows no text (AC4, L11)', async () => {
    const { router, shellText } = await renderAt(`${APP_BASENAME}/no-such-route`);

    expect(router.state.matches.map((match) => match.route.path)).toEqual(['/', '*']);
    expect(router.state.errors).toBeNull();
    expect(shellText).toBe('');
  });

  it('treats the bare /app/ as no route: only the root matches and nothing renders (T41 owns its redirect)', async () => {
    const { router, shellText } = await renderAt(`${APP_BASENAME}/`);

    expect(router.state.matches.map((match) => match.route.path)).toEqual(['/']);
    expect(router.state.errors).toBeNull();
    expect(shellText).toBe('');
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
    expect(pathTree(appRoutes)).toEqual([['/', [...DECLARED_PATHS.map((path) => [path, []]), ['*', []]]]]);
  });

  // Narrowed by T16, which attached the first screen. It was "no route carries an element", which was the
  // route-table twin of T01's "the shell renders nothing" - and like those, it retires one exception at a time
  // rather than all at once. A task that attaches a screen must come here and name it, which is the point.
  it('attaches a screen only to the routes whose task has run (L11, L12)', () => {
    expect(routesWithElements(appRoutes)).toEqual([...SCREENS_ATTACHED_SO_FAR]);

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
