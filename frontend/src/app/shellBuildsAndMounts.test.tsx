/// <reference types="node" />
import { mkdtemp, readdir, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { act } from 'react';
import type { Root } from 'react-dom/client';
import { build } from 'vite';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mountShell } from './mountShell';

// Lets act() flush React 19 renders synchronously in this test environment.
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// jsdom gives import.meta.url a non-file URL, so the frontend directory comes from the working
// directory, which `pnpm -C frontend test` sets; the first case checks it is the right package.
const frontendDir = process.cwd();
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

async function productionSourceFiles(dir: string): Promise<string[]> {
  const entries = await readdir(dir, { withFileTypes: true, recursive: true });
  return entries
    .filter((entry) => entry.isFile() && /\.(ts|tsx)$/.test(entry.name) && !/\.test\.(ts|tsx)$/.test(entry.name))
    .map((entry) => join(entry.parentPath, entry.name));
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
  it('builds for production with every asset under /app/ (L13, L14)', async () => {
    const pkg = JSON.parse(await readFile(join(frontendDir, 'package.json'), 'utf8')) as { name: string };
    expect(pkg.name).toBe('@themistra/frontend');
    const outDir = await mkdtemp(join(tmpdir(), 'checky-shell-build-'));
    try {
      await build({
        root: frontendDir,
        configFile: join(frontendDir, 'vite.config.ts'),
        logLevel: 'silent',
        build: { outDir, emptyOutDir: true },
      });

      const html = await readFile(join(outDir, 'index.html'), 'utf8');
      expect(html).toMatch(/<script type="module"[^>]*src="\/app\/assets\/[^"]+\.js"/);
      expect(html).not.toMatch(/src="\/assets\//);
    } finally {
      await rm(outDir, { recursive: true, force: true });
    }
  }, 60_000);

  it('mounts the empty shell into the container (L13, L11)', async () => {
    const container = await mountIntoFreshContainer();

    const shell = container.querySelector('[data-testid="app-shell"]');
    expect(shell).not.toBeNull();
    expect(shell?.textContent).toBe('');
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

  it('reads no environment key anywhere in the app source (L10, R58)', async () => {
    const files = await productionSourceFiles(srcDir);
    expect(files.length).toBeGreaterThan(0);

    for (const file of files) {
      expect(await readFile(file, 'utf8'), file).not.toContain('import.meta' + '.env');
    }
  });

  it('throws when the root element is missing instead of rendering nothing', () => {
    expect(() => mountShell(null)).toThrow('mountShell: root element #root not found');
  });
});
