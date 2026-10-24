/// <reference types="node" />
import { existsSync } from 'node:fs';
import { mkdtemp, readdir, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { act } from 'react';
import type { Root } from 'react-dom/client';
import { build, type UserConfig } from 'vite';
import { afterEach, describe, expect, it, vi } from 'vitest';
import viteConfig from '../../vite.config';
import { mountShell } from './mountShell';

// Lets act() flush React 19 renders synchronously in this test environment.
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// This file is frontend/src/app/<name>. Vitest's own test path is used because jsdom gives
// import.meta.url a non-file URL, and the working directory depends on how the runner was started.
const frontendDir = dirname(dirname(dirname(expect.getState().testPath as string)));
const srcDir = join(frontendDir, 'src');

let mounted: { root: Root; container: HTMLElement } | undefined;

async function mountIntoFreshContainer(): Promise<HTMLElement> {
  const container = document.createElement('div');
  document.body.append(container);
  let root: Root | undefined;
  await act(async () => {
    root = mountShell(container);
  });
  mounted = { root: root as Root, container };
  return container;
}

async function productionSourceFiles(): Promise<string[]> {
  const entries = await readdir(srcDir, { withFileTypes: true, recursive: true });
  const files = entries
    .filter((entry) => entry.isFile() && /\.(ts|tsx)$/.test(entry.name) && !/\.test\.(ts|tsx)$/.test(entry.name))
    .map((entry) => join(entry.parentPath, entry.name));
  expect(files.length).toBeGreaterThan(0);
  return files;
}

afterEach(async () => {
  if (mounted) {
    const { root, container } = mounted;
    await act(async () => root.unmount());
    container.remove();
    mounted = undefined;
  }
  vi.restoreAllMocks();
});

describe('shellBuildsAndMounts', () => {
  it('builds for production with every module script under /app/ and no service worker (L13, L14)', async () => {
    const pkg = JSON.parse(await readFile(join(frontendDir, 'package.json'), 'utf8')) as { name: string };
    expect(pkg.name, `resolved frontend directory ${frontendDir}`).toBe('@themistra/frontend');

    const outDir = await mkdtemp(join(tmpdir(), 'checky-shell-build-'));
    try {
      await build({
        root: frontendDir,
        configFile: join(frontendDir, 'vite.config.ts'),
        logLevel: 'silent',
        build: { outDir, emptyOutDir: true },
      });

      const html = await readFile(join(outDir, 'index.html'), 'utf8');
      const scriptSources = [...html.matchAll(/<script\b[^>]*\bsrc="([^"]+)"/g)].map((match) => match[1]);
      expect(scriptSources.length).toBeGreaterThan(0);
      for (const source of scriptSources) {
        expect(source).toMatch(/^\/app\/assets\/[^/]+\.js$/);
      }

      // T12 registers the worker, so it is emitted now - under the plugin's own `filename`, not Workbox's
      // default, and still with no web app manifest (`manifest: false`; an installable PWA is a later task).
      expect(existsSync(join(outDir, 'serviceWorker.js'))).toBe(true);
      expect(existsSync(join(outDir, 'sw.js'))).toBe(false);
      expect(existsSync(join(outDir, 'manifest.webmanifest'))).toBe(false);
      // The bundle may now name `navigator.serviceWorker` - one registration site does (T12). What it may never
      // do is promote a waiting worker: the update policy is prompt-to-reload (agents.md line 91), so neither
      // call that would activate a new build behind the user may appear in anything that ships.
      const assets = await readdir(join(outDir, 'assets'));
      for (const asset of assets.filter((name) => name.endsWith('.js'))) {
        const source = await readFile(join(outDir, 'assets', asset), 'utf8');
        expect(source, asset).not.toContain('skipWaiting');
        expect(source, asset).not.toContain('clients.claim');
      }
    } finally {
      await rm(outDir, { recursive: true, force: true });
    }
  }, 60_000);

  it('serves the dev server on 5173 for the edge, with no proxy (L9, L14)', () => {
    const config = viteConfig as UserConfig;
    expect(config.base).toBe('/app/');
    expect(config.server?.host).toBe(true);
    expect(config.server?.port).toBe(5173);
    expect(config.server?.strictPort).toBe(true);
    expect(config.server?.proxy).toBeUndefined();
  });

  it('mounts the empty shell into the container (L13, L11)', async () => {
    const container = await mountIntoFreshContainer();

    const shell = container.querySelector('[data-testid="app-shell"]');
    expect(shell).not.toBeNull();
    expect(shell?.innerHTML).toBe(''); // no text, not even whitespace
  });

  it('makes no network call, writes no storage and registers no service worker (L9, L10, R58)', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem');
    const register = vi.fn();
    Object.defineProperty(navigator, 'serviceWorker', { value: { register }, configurable: true });

    try {
      await mountIntoFreshContainer();

      expect(fetchSpy).not.toHaveBeenCalled();
      expect(setItemSpy).not.toHaveBeenCalled();
      expect(register).not.toHaveBeenCalled();
    } finally {
      Reflect.deleteProperty(navigator, 'serviceWorker');
    }
  });

  it('reads no environment key and names no absolute URL anywhere in the app source (L9, L10, R58)', async () => {
    for (const file of await productionSourceFiles()) {
      const source = await readFile(file, 'utf8');
      expect(source, file).not.toContain('import.meta' + '.env');
      expect(source, file).not.toMatch(/https?:\/\//);
    }
  });

  it('keeps the entry to a single mountShell call (shell code lives in src/app/)', async () => {
    const entry = await readFile(join(srcDir, 'main.tsx'), 'utf8');
    const imports = [...entry.matchAll(/^\s*import\b[^'"]*['"]([^'"]+)['"]/gm)].map((match) => match[1]);
    expect(imports).toEqual(['./app/mountShell', './pwa/registerServiceWorker']);

    const statements = entry
      .split('\n')
      .map((line) => line.trim())
      .filter((line) => line !== '' && !line.startsWith('import ') && !line.startsWith('//'));
    // T12 adds the one registration call. The entry stays thin: two statements, each a call into a module.
    expect(statements).toEqual([
      "mountShell(document.getElementById('root'));",
      'void registerServiceWorker();',
    ]);
  });

  it('throws when the root element is missing instead of rendering nothing', () => {
    expect(() => mountShell(null)).toThrow('mountShell: root element #root not found');
  });
});
