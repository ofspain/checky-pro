/// <reference types="node" />
/**
 * `serviceWorkerUpdateWaitsForReload` (package.md §8, line 172).
 *
 * The rule under test is a LOCKED standing rule rather than a numbered requirement: `agents.md` line 91, "The
 * worker uses one worker-wide update policy: prompt-to-reload. `skipWaiting` and `clients.claim` are not used",
 * restated at `package.md` §10. requirements.md has no update-policy requirement at all - §8 maps this test to
 * R57, which is only about navigation and caching - so AC10 guards the two sentences that do state it.
 *
 * This is also the task that first makes T09's and T11's policies run, so the file asserts the wiring by identity:
 * the navigation route must hold *their* denylist, not a copy of the eleven paths.
 */
import { execFile } from 'node:child_process';
import { mkdtemp, readdir, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { promisify } from 'node:util';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { SHELL_DOCUMENT_URL, isCacheableRequest, isNetworkOnlyRequest, NETWORK_ONLY_PATH_PREFIXES } from './apiRequestPolicy';
import { NAVIGATION_DENYLIST, isShellNavigation } from './navigationDenylist';

const run = promisify(execFile);

// This file is frontend/src/pwa/<name>; the repo root is three levels above frontend/.
const frontendDir = dirname(dirname(dirname(expect.getState().testPath as string)));
const srcDir = join(frontendDir, 'src');
const repoRoot = dirname(frontendDir);

/** The two calls that would promote a waiting worker, in every spelling that reaches them. */
const FORBIDDEN = ['skipWaiting', 'clients.claim', 'clientsClaim'] as const;

/** The origin the SPA is served from, used to resolve the build's base-relative precache URLs. */
const ORIGIN = 'http://localhost:5173';

function repoFile(...parts: string[]): Promise<string> {
  return readFile(join(repoRoot, ...parts), 'utf8');
}

/**
 * The AC7 rule itself, as a function, because the build cannot produce a violation of it while `base` is `/app/`.
 * Entries are base-relative ("index.html"), and Workbox resolves them against the worker's own location, so they
 * must be resolved before matching patterns anchored at "^/" - match the raw strings and every assertion passes
 * vacuously. Exercised below against both the real manifest and entries no build would emit.
 */
function denylistedPrecacheUrls(urls: readonly string[], base = `${ORIGIN}/app/`): string[] {
  return urls.filter((url) => {
    const { pathname } = new URL(url, base);
    return NAVIGATION_DENYLIST.some((pattern) => pattern.test(pathname));
  });
}

/** The precache manifest the build injected, read back out of the emitted worker. */
function precacheManifest(workerSource: string): Array<{ url: string; revision: string | null }> {
  const match = workerSource.match(/\[\{"revision":.*?\}\]/s);
  expect(match, 'no precache manifest found in the emitted worker').not.toBeNull();
  return JSON.parse((match as RegExpMatchArray)[0]) as Array<{ url: string; revision: string | null }>;
}

// ── A fake registration, so the waiting lifecycle can be driven without a browser ────────────────────────────────

class FakeWorker extends EventTarget {
  state: ServiceWorkerState = 'installing';

  install(): void {
    this.state = 'installed';
    this.dispatchEvent(new Event('statechange'));
  }
}

class FakeRegistration extends EventTarget {
  installing: FakeWorker | null = null;
  waiting: FakeWorker | null = null;
  active: FakeWorker | null = null;

  /** A new build arrives: it installs, then sits in `waiting`. Nothing here promotes it. */
  pushUpdate(): FakeWorker {
    const next = new FakeWorker();
    this.installing = next;
    this.dispatchEvent(new Event('updatefound'));
    this.waiting = next;
    this.installing = null;
    next.install();
    return next;
  }
}

type Container = { register: ReturnType<typeof vi.fn>; controller: unknown };

function installFakeContainer(registration: FakeRegistration | undefined, options?: { reject?: boolean }): Container {
  const controller = { id: 'the-old-build' };
  const container = {
    register: vi.fn(() => (options?.reject ? Promise.reject(new Error('404')) : Promise.resolve(registration))),
    controller,
  };
  Object.defineProperty(globalThis.navigator, 'serviceWorker', { value: container, configurable: true });
  return container as unknown as Container;
}

/** Each case gets a fresh module registry, because registration is deliberately idempotent per module instance. */
async function freshRegistrationModule() {
  vi.resetModules();
  return import('./registerServiceWorker');
}

afterEach(() => {
  Reflect.deleteProperty(globalThis.navigator, 'serviceWorker');
  vi.restoreAllMocks();
});

// ── AC1, AC2 · the forbidden calls, in the source and in what ships ──────────────────────────────────────────────

describe('serviceWorkerUpdateWaitsForReload', () => {
  describe('the worker never promotes itself (AC1, AC2)', () => {
    it.each(['src/pwa/serviceWorker.ts', 'src/pwa/registerServiceWorker.ts', 'src/main.tsx', 'vite.config.ts'])(
      '%s names no call that would activate a waiting worker',
      async (path) => {
        const source = await repoFile('frontend', path);
        for (const forbidden of FORBIDDEN) {
          // The worker's own doc comment explains why these are absent, so only code lines are checked.
          const code = source
            .split('\n')
            .filter((line) => !/^\s*(\*|\/\/|\/\*)/.test(line))
            .join('\n');
          expect(code, `${path} names ${forbidden}`).not.toContain(forbidden);
        }
      },
    );

    it('enables no plugin option that would inject one', async () => {
      const config = await repoFile('frontend', 'vite.config.ts');
      expect(config).toContain("strategies: 'injectManifest'");
      // generateSW is what takes `skipWaiting`/`clientsClaim` options; injectManifest has no such switch, and the
      // worker is ours. Asserted as the absence of the whole option surface, not of one spelling.
      expect(config).not.toContain('skipWaiting');
      expect(config).not.toContain('clientsClaim');
      expect(config).not.toContain('registerType');
    });

    it('registers no update-promoting message channel', async () => {
      const source = await repoFile('frontend', 'src/pwa/registerServiceWorker.ts');
      const code = source.split('\n').filter((line) => !/^\s*(\*|\/\/|\/\*)/.test(line)).join('\n');
      for (const promoter of ['postMessage', 'location.reload', 'location.assign', 'location.href']) {
        expect(code, `registration names ${promoter}`).not.toContain(promoter);
      }
    });
  });

  // ── AC3 · a new build waits ────────────────────────────────────────────────────────────────────────────────────

  describe('a new build waits instead of taking over (AC3)', () => {
    it('leaves the new worker in waiting and the old one in control', async () => {
      const registration = new FakeRegistration();
      const container = installFakeContainer(registration);
      const { registerServiceWorker } = await freshRegistrationModule();

      await registerServiceWorker();
      const controllerBefore = container.controller;
      const next = registration.pushUpdate();

      expect(next.state).toBe('installed');
      expect(registration.waiting).toBe(next);
      expect(registration.active).toBeNull(); // nothing in this module activated it
      expect(container.controller).toBe(controllerBefore);
    });

    it('reports the waiting build to a subscriber exactly once', async () => {
      const registration = new FakeRegistration();
      installFakeContainer(registration);
      const { registerServiceWorker, onUpdateWaiting } = await freshRegistrationModule();
      await registerServiceWorker();

      const listener = vi.fn();
      onUpdateWaiting(listener);
      registration.pushUpdate();

      expect(listener).toHaveBeenCalledTimes(1);
    });

    it('tells a late subscriber that a build is already waiting, exactly once', async () => {
      const registration = new FakeRegistration();
      registration.waiting = new FakeWorker();
      installFakeContainer(registration);
      const { registerServiceWorker, onUpdateWaiting } = await freshRegistrationModule();
      await registerServiceWorker();

      const listener = vi.fn();
      onUpdateWaiting(listener);
      expect(listener).toHaveBeenCalledTimes(1);
    });

    it('tells a subscriber that arrives before registration settles exactly once (S1)', async () => {
      // The ordering that used to double-notify: trackWaiting fanned out to the whole listener set for an
      // already-waiting worker, and onUpdateWaiting's own promise path reported the same build again.
      const registration = new FakeRegistration();
      registration.waiting = new FakeWorker();
      installFakeContainer(registration);
      const { registerServiceWorker, onUpdateWaiting } = await freshRegistrationModule();

      const pending = registerServiceWorker();
      const listener = vi.fn();
      onUpdateWaiting(listener);
      await pending;
      await Promise.resolve();

      expect(listener).toHaveBeenCalledTimes(1);
    });

    it('reports one waiting build once, however many lifecycle events announce it (S4)', async () => {
      const registration = new FakeRegistration();
      installFakeContainer(registration);
      const { registerServiceWorker, onUpdateWaiting } = await freshRegistrationModule();
      await registerServiceWorker();

      const listener = vi.fn();
      onUpdateWaiting(listener);
      registration.pushUpdate();
      registration.pushUpdate();

      expect(listener).toHaveBeenCalledTimes(1);
    });

    it('does not drop an update whose installing worker is already gone (S4)', async () => {
      const registration = new FakeRegistration();
      installFakeContainer(registration);
      const { registerServiceWorker, onUpdateWaiting } = await freshRegistrationModule();
      await registerServiceWorker();

      const listener = vi.fn();
      onUpdateWaiting(listener);
      // updatefound with no `installing`: the lifecycle should not do this, so the fallback is what is tested.
      registration.waiting = new FakeWorker();
      registration.dispatchEvent(new Event('updatefound'));
      await vi.waitFor(() => expect(listener).toHaveBeenCalledTimes(1));
    });

    it('observing does not register; main.tsx owns that (S3)', async () => {
      const container = installFakeContainer(new FakeRegistration());
      const { onUpdateWaiting } = await freshRegistrationModule();

      onUpdateWaiting(vi.fn());

      expect(container.register).not.toHaveBeenCalled();
    });

    it('stops reporting after unsubscribe', async () => {
      const registration = new FakeRegistration();
      installFakeContainer(registration);
      const { registerServiceWorker, onUpdateWaiting } = await freshRegistrationModule();
      await registerServiceWorker();

      const listener = vi.fn();
      onUpdateWaiting(listener)();
      registration.pushUpdate();

      expect(listener).not.toHaveBeenCalled();
    });

    it('keeps reporting to the other subscribers when one throws', async () => {
      const registration = new FakeRegistration();
      installFakeContainer(registration);
      const { registerServiceWorker, onUpdateWaiting } = await freshRegistrationModule();
      await registerServiceWorker();

      const second = vi.fn();
      onUpdateWaiting(() => {
        throw new Error('a screen blew up');
      });
      onUpdateWaiting(second);
      expect(() => registration.pushUpdate()).not.toThrow();
      expect(second).toHaveBeenCalledTimes(1);
    });
  });

  // ── AC4, AC5 · one policy, one site, one scope ─────────────────────────────────────────────────────────────────

  describe('one worker-wide policy (AC4, AC5)', () => {
    it('has exactly one registration site in the whole of src', async () => {
      const entries = await readdir(srcDir, { withFileTypes: true, recursive: true });
      const sources = entries
        .filter((e) => e.isFile() && /\.(ts|tsx)$/.test(e.name) && !/\.test\.(ts|tsx)$/.test(e.name))
        .map((e) => join(e.parentPath, e.name));
      const registering: string[] = [];
      for (const file of sources) {
        if ((await readFile(file, 'utf8')).includes('.register(')) registering.push(file);
      }
      expect(registering).toEqual([join(srcDir, 'pwa', 'registerServiceWorker.ts')]);
    });

    it('registers once however many times it is called', async () => {
      const container = installFakeContainer(new FakeRegistration());
      const { registerServiceWorker } = await freshRegistrationModule();

      await Promise.all([registerServiceWorker(), registerServiceWorker()]);
      await registerServiceWorker();

      expect(container.register).toHaveBeenCalledTimes(1);
    });

    it('states the scope rather than inheriting it, and keeps it under the SPA prefix', async () => {
      const container = installFakeContainer(new FakeRegistration());
      const { registerServiceWorker, SERVICE_WORKER_SCOPE, SERVICE_WORKER_URL } = await freshRegistrationModule();
      await registerServiceWorker();

      expect(SERVICE_WORKER_SCOPE).toBe('/app/');
      expect(container.register).toHaveBeenCalledWith(SERVICE_WORKER_URL, { scope: SERVICE_WORKER_SCOPE });
      // The scope is what makes the denylist belt-and-braces: nothing outside /app/ can reach this worker.
      for (const pattern of NAVIGATION_DENYLIST) {
        expect(pattern.test(SERVICE_WORKER_SCOPE)).toBe(false);
      }
      expect(SERVICE_WORKER_URL.startsWith(SERVICE_WORKER_SCOPE)).toBe(true);
    });

    it('keeps scope and worker URL in step with the build config (S7)', async () => {
      const config = await repoFile('frontend', 'vite.config.ts');
      const { SERVICE_WORKER_SCOPE, SERVICE_WORKER_URL } = await import('./registerServiceWorker');

      const base = config.match(/base: '([^']+)'/)?.[1];
      const filename = config.match(/filename: '([^']+)'/g);
      expect(base, 'vite.config.ts no longer states a base').toBe(SERVICE_WORKER_SCOPE);
      // S8's other half: exactly one worker filename, so there cannot be a second worker-wide policy.
      expect(filename).toHaveLength(1);
      const emitted = (filename as string[])[0]!.replace("filename: '", '').replace("'", '').replace(/\.ts$/, '.js');
      expect(SERVICE_WORKER_URL).toBe(`${base}${emitted}`);
    });

    it('keeps the scope trailing slash, which is a segment boundary rather than a prefix (Kimi 8.1)', async () => {
      const { SERVICE_WORKER_SCOPE } = await import('./registerServiceWorker');
      // Scope matching is a prefix match over the whole URL string. Dropping the slash would claim `/apple.html`
      // too, which is wider than the SPA (AC5, L14). The accepted cost: a client at exactly `/app` would be
      // uncontrolled - and the edge serves no such URL.
      expect(SERVICE_WORKER_SCOPE.endsWith('/')).toBe(true);
      expect('/apple.html'.startsWith(SERVICE_WORKER_SCOPE)).toBe(false);
      expect('/app'.startsWith(SERVICE_WORKER_SCOPE)).toBe(false);
      expect('/app/sign-in'.startsWith(SERVICE_WORKER_SCOPE)).toBe(true);
    });

    it('declares exactly one precache manifest in the whole of src (S8)', async () => {
      const entries = await readdir(srcDir, { withFileTypes: true, recursive: true });
      const sources = entries
        .filter((e) => e.isFile() && /\.(ts|tsx)$/.test(e.name) && !/\.test\.(ts|tsx)$/.test(e.name))
        .map((e) => join(e.parentPath, e.name));
      const declaring: string[] = [];
      for (const file of sources) {
        if ((await readFile(file, 'utf8')).includes('__WB_MANIFEST')) declaring.push(file);
      }
      expect(declaring).toEqual([join(srcDir, 'pwa', 'serviceWorker.ts')]);
    });

    it('degrades quietly where there is no service worker at all', async () => {
      const { registerServiceWorker } = await freshRegistrationModule();
      await expect(registerServiceWorker()).resolves.toBeUndefined();
    });

    it('treats a failed registration as a normal outcome (dev serves no worker)', async () => {
      installFakeContainer(undefined, { reject: true });
      const { registerServiceWorker } = await freshRegistrationModule();
      await expect(registerServiceWorker()).resolves.toBeUndefined();
    });

    it('makes one attempt per page load and does not retry a failure (S2)', async () => {
      const container = installFakeContainer(undefined, { reject: true });
      const { registerServiceWorker } = await freshRegistrationModule();

      await registerServiceWorker();
      await expect(registerServiceWorker()).resolves.toBeUndefined();

      // Deliberate: the memo holds the failure, so nothing retries behind the user's back.
      expect(container.register).toHaveBeenCalledTimes(1);
    });
  });

  // ── AC6 · the worker runs T09's and T11's policies, by identity ─────────────────────────────────────────────────

  describe('the worker is wired from the existing policies (AC6)', () => {
    const calls: string[] = [];
    let navigationRouteArgs: Array<[unknown, { denylist?: RegExp[] } | undefined]>;
    let defaultHandlers: unknown[];
    let boundUrls: string[];
    // Captured from the same module registry the worker imports from: vi.resetModules() means the top-level
    // class and the worker's class would otherwise be two different NetworkOnly functions.
    let NetworkOnly: new () => unknown;
    // Same reason: the worker imports its denylist from a fresh registry too. Identity against *that* array is
    // still the real assertion - a worker that spread or filtered the eleven paths would hand over a new array.
    let freshDenylist: readonly RegExp[];

    beforeEach(async () => {
      calls.length = 0;
      navigationRouteArgs = [];
      defaultHandlers = [];
      boundUrls = [];

      vi.doMock('workbox-precaching', () => ({
        precacheAndRoute: (manifest: unknown) => {
          calls.push('precacheAndRoute');
          expect(Array.isArray(manifest)).toBe(true);
        },
        createHandlerBoundToURL: (url: string) => {
          boundUrls.push(url);
          return () => undefined;
        },
      }));
      vi.doMock('workbox-routing', () => ({
        registerRoute: (route: unknown) => {
          calls.push('registerRoute');
          void route;
        },
        setDefaultHandler: (handler: unknown) => {
          calls.push('setDefaultHandler');
          defaultHandlers.push(handler);
        },
        NavigationRoute: class {
          constructor(handler: unknown, options?: { denylist?: RegExp[] }) {
            navigationRouteArgs.push([handler, options]);
          }
        },
      }));

      (globalThis as { __WB_MANIFEST?: unknown }).__WB_MANIFEST = [{ url: 'index.html', revision: 'abc' }];
      vi.resetModules();
      ({ NetworkOnly } = (await import('workbox-strategies')) as unknown as { NetworkOnly: new () => unknown });
      ({ NAVIGATION_DENYLIST: freshDenylist } = await import('./navigationDenylist'));
      await import('./serviceWorker');
    });

    afterEach(() => {
      vi.doUnmock('workbox-precaching');
      vi.doUnmock('workbox-routing');
      Reflect.deleteProperty(globalThis as object, '__WB_MANIFEST');
    });

    it('gives the navigation route T09\'s denylist itself, not a copy of the eleven paths', () => {
      expect(navigationRouteArgs).toHaveLength(1);
      expect(navigationRouteArgs[0]![1]?.denylist).toBe(freshDenylist);
      expect(freshDenylist).toHaveLength(NAVIGATION_DENYLIST.length);
    });

    it('binds the fallback to T11\'s shell constant', () => {
      expect(boundUrls).toEqual([SHELL_DOCUMENT_URL]);
      expect(isCacheableRequest(new URL(SHELL_DOCUMENT_URL, ORIGIN))).toBe(true);
    });

    it('makes NetworkOnly the default, so an unrouted request is fetched and never stored', () => {
      expect(defaultHandlers).toHaveLength(1);
      expect(defaultHandlers[0]).toBeInstanceOf(NetworkOnly);
    });

    it('precaches before it registers the navigation fallback, which is what makes AC7 load-bearing', () => {
      expect(calls).toEqual(['precacheAndRoute', 'registerRoute', 'setDefaultHandler']);
    });

    it.each(NETWORK_ONLY_PATH_PREFIXES)('leaves %s to the NetworkOnly default', (prefix) => {
      expect(isNetworkOnlyRequest(new URL(prefix, ORIGIN))).toBe(true);
      expect(isCacheableRequest(new URL(prefix, ORIGIN))).toBe(false);
    });
  });

  // ── AC7 · the precache manifest, both halves ───────────────────────────────────────────────────────────────────

  describe('what ships (AC1, AC5, AC7, AC8)', () => {
    let outDir: string;
    let worker: string;

    beforeAll(async () => {
      outDir = await mkdtemp(join(tmpdir(), 't12-build-'));
      await run(join(frontendDir, 'node_modules/.bin/vite'), ['build', '--outDir', outDir, '--emptyOutDir'], {
        cwd: frontendDir,
      });
      worker = await readFile(join(outDir, 'serviceWorker.js'), 'utf8');
    }, 120_000);

    afterAll(async () => {
      await rm(outDir, { recursive: true, force: true });
    });

    it('emits the worker at the URL the registration asks for', async () => {
      const { SERVICE_WORKER_URL } = await import('./registerServiceWorker');
      expect(SERVICE_WORKER_URL).toBe('/app/serviceWorker.js');
      expect(worker.length).toBeGreaterThan(0);
    });

    // Depends on the production build minifying the worker: serviceWorker.ts's own doc comment names both calls
    // in order to explain their absence, so setting `injectManifest.minify = false` would ship that prose and
    // fail this case for a reason unrelated to the policy. A loud failure is the right outcome; this note is so
    // whoever sees it knows where to look.
    it.each(FORBIDDEN)('ships no %s, in the worker or in any app chunk', async (forbidden) => {
      expect(worker).not.toContain(forbidden);
      for (const asset of (await readdir(join(outDir, 'assets'))).filter((n) => n.endsWith('.js'))) {
        expect(await readFile(join(outDir, 'assets', asset), 'utf8'), asset).not.toContain(forbidden);
      }
    });

    it('precaches nothing a navigation denylist would have refused', () => {
      const urls = precacheManifest(worker).map(({ url }) => url);
      expect(urls.length).toBeGreaterThan(0);
      expect(denylistedPrecacheUrls(urls)).toEqual([]);
      // Why it can hold: every entry resolves under the SPA's own prefix, and /app is not a denylisted path.
      for (const url of urls) {
        expect(new URL(url, `${ORIGIN}/app/`).pathname.startsWith('/app/'), url).toBe(true);
      }
    });

    it('would refuse a denylisted precache entry, which no build emits today', () => {
      // The honest limit, made testable: while `base` is `/app/` the build cannot produce a violation, so the
      // case above can only ever pass. This one exercises the same matcher with entries that do violate it -
      // including a dot-segment escape, which URL resolution really does collapse - so the guard is known to
      // work rather than merely known to pass.
      expect(denylistedPrecacheUrls(['../login'])).toEqual(['../login']);
      expect(denylistedPrecacheUrls(['../api/v1/invoices'])).toEqual(['../api/v1/invoices']);
      expect(denylistedPrecacheUrls(['/oauth2/authorize'])).toEqual(['/oauth2/authorize']);
      expect(denylistedPrecacheUrls(['index.html', 'assets/index-abc123.js'])).toEqual([]);
      // And it is the resolution that does the work: unresolved, the same entry matches nothing.
      expect(NAVIGATION_DENYLIST.some((p) => p.test('../login'))).toBe(false);
    });

    it('precaches the shell the fallback is bound to', () => {
      const resolved = precacheManifest(worker).map(({ url }) => new URL(url, `${ORIGIN}/app/`).pathname);
      expect(resolved).toContain(SHELL_DOCUMENT_URL);
    });

    it('still answers /app/signed-out from the shell rather than from a precache hit', () => {
      // The other half of AC7, pulling the opposite way: T10's landing route must not be precached away, and must
      // still be a shell navigation. It is not in the manifest, so it reaches the navigation fallback.
      const resolved = precacheManifest(worker).map(({ url }) => new URL(url, `${ORIGIN}/app/`).pathname);
      expect(resolved).not.toContain('/app/signed-out');
      expect(isShellNavigation(new URL('/app/signed-out', ORIGIN), 'navigate')).toBe(true);
    });

    it('precaches only the shell and the hashed assets', () => {
      for (const { url } of precacheManifest(worker)) {
        const { pathname } = new URL(url, `${ORIGIN}/app/`);
        expect(isCacheableRequest(new URL(pathname, ORIGIN)), pathname).toBe(true);
      }
    });

    it('emits no web app manifest, so T01 keeps that assertion', async () => {
      expect(await readdir(outDir)).not.toContain('manifest.webmanifest');
    });
  });

  // ── AC8, AC9 · hygiene ─────────────────────────────────────────────────────────────────────────────────────────

  describe('hygiene (AC8, AC9)', () => {
    it.each(['src/pwa/serviceWorker.ts', 'src/pwa/registerServiceWorker.ts'])(
      '%s reads no environment key, names no absolute URL and writes no storage',
      async (path) => {
        const source = await repoFile('frontend', path);
        expect(source).not.toContain('import.meta' + '.env');
        expect(source).not.toMatch(/https?:\/\//);
        for (const store of ['localStorage', 'sessionStorage', 'indexedDB', 'document.cookie']) {
          expect(source, `${path} touches ${store}`).not.toContain(store);
        }
      },
    );

    it.each(['src/pwa/serviceWorker.ts', 'src/pwa/registerServiceWorker.ts'])(
      '%s introduces no user-facing copy (L11)',
      async (path) => {
        const source = await repoFile('frontend', path);
        expect(source).not.toContain('i18next');
        expect(source).not.toMatch(/\bt\(/);
      },
    );
  });

  // ── AC10 · the rule this task enforces must stay in the spec ───────────────────────────────────────────────────

  describe('drift against the standing rule (AC10)', () => {
    it('agents.md still forbids both calls as one worker-wide policy', async () => {
      const agents = (await repoFile('spec', 'frontend', 'agents.md')).replace(/\s+/g, ' ');
      expect(agents).toContain(
        'The worker uses one worker-wide update policy: prompt-to-reload. `skipWaiting` and `clients.claim` are not used.',
      );
    });

    it('package.md §10 still states prompt-to-reload', async () => {
      const pkg = (await repoFile('spec', 'frontend', 'package.md')).replace(/\s+/g, ' ');
      expect(pkg).toContain(
        '**Service-worker update policy (one worker-wide policy)**: prompt-to-reload. `skipWaiting` and `clients.claim` are not used.',
      );
    });

    it('the named test is still the one package.md asks for', async () => {
      const pkg = await repoFile('spec', 'frontend', 'package.md');
      expect(pkg).toContain('`serviceWorkerUpdateWaitsForReload`');
    });
  });
});
