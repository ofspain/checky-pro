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

/** Edge prefixes that belong to the backend services (design.md §4c note 2026-10-21 (2)), each bare and with a sub-path. */
const BACKEND_PATHS = [
  '/oauth2',
  '/oauth2/authorize',
  '/oauth2/token',
  '/login',
  '/logout',
  '/connect',
  '/connect/logout',
  '/.well-known',
  '/.well-known/openid-configuration',
  '/userinfo',
  '/error',
  '/accounts',
  '/accounts/me',
  '/admin',
  '/admin/accounts',
  '/api-keys',
  '/api/v1',
  '/api/v1/invoices',
  '/notifications',
  '/notifications/preferences',
];

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
});

describe('routesResolveUnderAppPrefixOnly', () => {
  it('resolves every declared route to its own leaf under /app (AC1)', () => {
    for (const path of DECLARED_PATHS) {
      expect(leafPath(`${APP_BASENAME}/${concrete(path)}`), path).toBe(path);
    }
  });

  it('resolves none of the declared routes without the /app prefix (AC2)', () => {
    for (const path of DECLARED_PATHS) {
      expect(matchRoutes(appRoutes, `/${concrete(path)}`, APP_BASENAME), path).toBeNull();
    }
  });

  it('resolves no backend edge prefix (AC3, L9)', () => {
    for (const path of BACKEND_PATHS) {
      expect(matchRoutes(appRoutes, path, APP_BASENAME), path).toBeNull();
    }
  });

  it('sends an unmatched /app path to the silent catch-all, so the shell shows no text (AC4, L11)', async () => {
    const router = createMemoryRouter(appRoutes, {
      basename: APP_BASENAME,
      initialEntries: [`${APP_BASENAME}/no-such-route`],
    });
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

    expect(router.state.matches.map((match) => match.route.path)).toEqual(['/', '*']);
    expect(router.state.errors).toBeNull();
    expect(container.querySelector('[data-testid="app-shell"]')?.textContent).toBe('');
  });

  it('declares exactly the 20 §4c paths, all relative, with the catch-all as the only wildcard (L14)', () => {
    expect(PUBLIC_ROUTE_PATHS).toHaveLength(9);
    expect(AUTHENTICATED_ROUTE_PATHS).toHaveLength(11);
    expect(new Set(DECLARED_PATHS).size).toBe(20);
    for (const path of DECLARED_PATHS) {
      expect(path.startsWith('/'), path).toBe(false);
      expect(path.includes('*'), path).toBe(false);
    }
    expect(pathTree(appRoutes)).toEqual([['/', [...DECLARED_PATHS.map((path) => [path, []]), ['*', []]]]]);
  });

  it('creates the browser router with the /app basename and the declared routes', () => {
    const router = createAppRouter();
    toDispose = router;

    expect(router.basename).toBe('/app');
    expect(pathTree(router.routes)).toEqual(pathTree(appRoutes));
  });
});
