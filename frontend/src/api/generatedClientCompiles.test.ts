/// <reference types="node" />
import { execFile } from 'node:child_process';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { promisify } from 'node:util';
import { createAuthClient, type paths } from '@themistra/api-client';
import { afterEach, describe, expect, expectTypeOf, it, vi } from 'vitest';

const run = promisify(execFile);

// This file is frontend/src/api/<name>. Vitest's own test path is used because jsdom gives
// import.meta.url a non-file URL, and the working directory depends on how the runner was started.
const repoRoot = dirname(dirname(dirname(dirname(expect.getState().testPath as string))));
const clientDir = join(repoRoot, 'libs/ts/api-client');
const contractPath = join(repoRoot, 'contracts/api/auth.yaml');
const generatedPath = join(clientDir, 'src/generated/auth.ts');

/** SAS-owned endpoints. They aren't in auth.yaml, and the OIDC client handles them, not the generated client (R20). */
const SAS_PREFIXES = ['/oauth2', '/.well-known', '/userinfo', '/login', '/logout', '/connect'];

/** The members of one top-level `export interface <name> {` block in the generated file. */
function interfaceMembers(source: string, name: string, member: RegExp): string[] {
  const start = source.indexOf(`export interface ${name} {`);
  expect(start, `interface ${name}`).toBeGreaterThanOrEqual(0);
  const end = source.indexOf('\n}\n', start);
  return [...source.slice(start, end).matchAll(member)].map((match) => match[1] as string);
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('generatedClientCompiles', () => {
  it('reproduces the committed client byte for byte from auth.yaml (L6, not hand-edited)', async () => {
    const pkg = JSON.parse(await readFile(join(clientDir, 'package.json'), 'utf8')) as { name: string };
    expect(pkg.name, `resolved client directory ${clientDir}`).toBe('@themistra/api-client');

    const outDir = await mkdtemp(join(tmpdir(), 'checky-api-client-'));
    try {
      const regenerated = join(outDir, 'auth.ts');
      await run(join(clientDir, 'node_modules/.bin/openapi-typescript'), [contractPath, '-o', regenerated], {
        cwd: clientDir,
      });
      expect(await readFile(regenerated, 'utf8')).toBe(await readFile(generatedPath, 'utf8'));
    } finally {
      await rm(outDir, { recursive: true, force: true });
    }
  }, 60_000);

  it('type-checks under the project compiler, TypeScript 7 strict, with zero errors', async () => {
    const tsc = join(repoRoot, 'frontend/node_modules/typescript/bin/tsc');
    const { stdout: version } = await run(process.execPath, [tsc, '--version']);
    expect(version.trim()).toBe('Version 7.0.2');

    // execFile rejects on a non-zero exit, so reaching the assertion means the compile passed.
    const { stdout, stderr } = await run(process.execPath, [
      tsc,
      '--noEmit',
      '-p',
      join(clientDir, 'tsconfig.json'),
    ]);
    expect(stdout + stderr).toBe('');
  }, 60_000);

  it('generates every auth.yaml operation, a typed GET /accounts/me and no SAS endpoint (R20)', async () => {
    // Type level, enforced by `pnpm -C frontend typecheck`: the account-status read has a typed 200 JSON body.
    type OwnAccount = paths['/accounts/me']['get']['responses']['200']['content']['application/json'];
    expectTypeOf<OwnAccount>().toBeObject();

    const generated = await readFile(generatedPath, 'utf8');
    const contract = await readFile(contractPath, 'utf8');

    const pathKeys = interfaceMembers(generated, 'paths', /^ {4}"(\/[^"]*)": \{$/gm);
    expect(pathKeys).toContain('/accounts/me');
    for (const path of pathKeys) {
      expect(SAS_PREFIXES.some((prefix) => path.startsWith(prefix)), path).toBe(false);
    }

    const operationIds = [...contract.matchAll(/^\s+operationId:\s*(\S+)\s*$/gm)].map((match) => match[1]);
    const operations = interfaceMembers(generated, 'operations', /^ {4}([A-Za-z0-9_]+): \{$/gm);
    expect(operationIds.length).toBeGreaterThan(0);
    expect([...operations].sort()).toEqual([...operationIds].sort());
  });

  it('calls the page origin on a relative path and runs registered middleware (L9)', async () => {
    // Node's Request rejects relative URLs; a browser resolves them against the document, so mirror that.
    const NodeRequest = globalThis.Request;
    vi.stubGlobal(
      'Request',
      class extends NodeRequest {
        constructor(input: RequestInfo | URL, init?: RequestInit) {
          super(typeof input === 'string' ? new URL(input, window.location.href) : input, init);
        }
      },
    );
    const fetchSpy = vi.fn(
      async () => new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }),
    );
    vi.stubGlobal('fetch', fetchSpy);

    const client = createAuthClient();
    const seenByMiddleware: string[] = [];
    client.use({
      onRequest({ request }) {
        seenByMiddleware.push(request.url);
      },
    });
    await client.GET('/accounts/me');

    const expected = `${window.location.origin}/accounts/me`;
    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const sent = (fetchSpy.mock.calls[0] as unknown as [Request])[0];
    expect(sent.url).toBe(expected);
    expect(sent.method).toBe('GET');
    expect(seenByMiddleware).toEqual([expected]);
  });

  it('reads no environment key and names no absolute URL in the hand-written entry (L9, L10)', async () => {
    const entry = await readFile(join(clientDir, 'src/index.ts'), 'utf8');
    expect(entry).not.toContain('import.meta' + '.env');
    expect(entry).not.toMatch(/https?:\/\//);
  });
});
