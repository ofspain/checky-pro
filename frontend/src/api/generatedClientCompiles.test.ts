/// <reference types="node" />
import { execFile } from 'node:child_process';
import { createRequire } from 'node:module';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { promisify } from 'node:util';
import { createAuthClient, createOidcClient, type components, type paths } from '@themistra/api-client';
import { afterEach, describe, expect, expectTypeOf, it, vi } from 'vitest';

const run = promisify(execFile);

// This file is frontend/src/api/<name>. Vitest's own test path is used because jsdom gives
// import.meta.url a non-file URL, and the working directory depends on how the runner was started.
const repoRoot = dirname(dirname(dirname(dirname(expect.getState().testPath as string))));
const clientDir = join(repoRoot, 'libs/ts/api-client');
const contractPath = join(repoRoot, 'contracts/api/auth.yaml');
const generatedPath = join(clientDir, 'src/generated/auth.ts');
// The SAS protocol surface is a second contract, not an addition to auth.yaml, whose own description says the
// protocol endpoints are intentionally absent (T20-D1, T20-D2).
const oidcContractPath = join(repoRoot, 'contracts/api/oidc.yaml');
const oidcGeneratedPath = join(clientDir, 'src/generated/oidc.ts');

/** The project compiler, resolved the way Node resolves `typescript` from frontend/ (typescript exports no `bin/tsc`). */
const projectTsc = join(
  dirname(createRequire(join(repoRoot, 'frontend/package.json')).resolve('typescript/package.json')),
  'bin/tsc',
);

/** Browser-style URL resolution and a stubbed fetch that records each request. */
function stubBrowserFetch() {
  const NodeRequest = globalThis.Request;
  vi.stubGlobal(
    'Request',
    class extends NodeRequest {
      constructor(input: RequestInfo | URL, init?: RequestInit) {
        // Node's Request rejects relative URLs; a browser resolves them against the document.
        super(typeof input === 'string' ? new URL(input, window.location.href) : input, init);
      }
    },
  );
  const fetchSpy = vi.fn(
    async (_request: Request) =>
      new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }),
  );
  vi.stubGlobal('fetch', fetchSpy);
  return fetchSpy;
}

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
    const pkg = JSON.parse(await readFile(join(clientDir, 'package.json'), 'utf8')) as {
      name: string;
      scripts: Record<string, string>;
    };
    expect(pkg.name, `resolved client directory ${clientDir}`).toBe('@themistra/api-client');
    // The documented command, which CI's drift step runs, is exactly the invocation this case reproduces.
    expect(pkg.scripts.generate).toBe(
      'openapi-typescript ../../../contracts/api/auth.yaml -o src/generated/auth.ts && ' +
        'openapi-typescript ../../../contracts/api/oidc.yaml -o src/generated/oidc.ts',
    );

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

  it('reproduces the committed OIDC client byte for byte from oidc.yaml (AC23, T20-D4)', async () => {
    const outDir = await mkdtemp(join(tmpdir(), 'checky-oidc-client-'));
    try {
      const regenerated = join(outDir, 'oidc.ts');
      await run(join(clientDir, 'node_modules/.bin/openapi-typescript'), [oidcContractPath, '-o', regenerated], {
        cwd: clientDir,
      });
      expect(await readFile(regenerated, 'utf8')).toBe(await readFile(oidcGeneratedPath, 'utf8'));
    } finally {
      await rm(outDir, { recursive: true, force: true });
    }
  }, 60_000);

  it('keeps the two contracts apart: the SAS path is generated into oidc.ts and not into auth.ts (T20-D2)', async () => {
    expect(await readFile(oidcGeneratedPath, 'utf8')).toContain('"/oauth2/token"');
    // The guard above on auth.ts is what the SAS_PREFIXES case enforces; this states the other half, so neither
    // file can quietly absorb the other's surface.
    expect(await readFile(generatedPath, 'utf8')).not.toContain('/oauth2');
  });

  it('sends a form-encoded body through the OIDC client with the exact content type (AC22, T20-D5)', async () => {
    const fetchSpy = stubBrowserFetch();
    const fields = {
      grant_type: 'authorization_code' as const,
      code: 'the-authorization-code',
      redirect_uri: `${window.location.origin}/app/callback`,
      client_id: 'checky-spa',
      code_verifier: 'the-stored-verifier',
    };

    await createOidcClient().POST('/oauth2/token', {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: fields,
    });

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const sent = fetchSpy.mock.calls[0]![0];
    expect(sent.method).toBe('POST');
    expect(sent.url).toBe(`${window.location.origin}/oauth2/token`);
    // No custom serializer is involved: openapi-fetch's stock one form-encodes when it sees exactly this header,
    // and an explicitly passed header overrides the JSON default it would otherwise apply. Proven here, in the
    // client package, so a content-type regression is caught without any auth logic in the way.
    expect(sent.headers.get('Content-Type')).toBe('application/x-www-form-urlencoded');
    const body = await sent.text();
    expect(body.startsWith('{')).toBe(false);
    expect(Object.fromEntries(new URLSearchParams(body))).toEqual(fields);
  });

  it('falls back to JSON when the content type is not byte-identical (T20-D5, the trap)', async () => {
    const fetchSpy = stubBrowserFetch();

    await createOidcClient().POST('/oauth2/token', {
      // One charset parameter away from the value the serializer looks for. It compares with `===`, so this is
      // not a near miss - it is a miss, and the body silently becomes JSON that the token endpoint cannot read.
      headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
      body: { grant_type: 'authorization_code' as const, code: 'c', redirect_uri: '/r', client_id: 'i', code_verifier: 'v' },
    });

    const body = await fetchSpy.mock.calls[0]![0].text();
    // Demonstrated rather than described, so the constant in codeExchange.ts reads as load-bearing instead of
    // stylistic to whoever next edits it.
    expect(body.startsWith('{')).toBe(true);
    expect(body).not.toContain('grant_type=');
  });

  it('type-checks under the project compiler, TypeScript 7 strict, with zero errors', async () => {
    const tsc = projectTsc;
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
    // Schemas reach consumers through the package entry too.
    expectTypeOf<components['schemas']['AccountResponse']>().toEqualTypeOf<OwnAccount>();
    // A non-GET operation is typed with its request body.
    type Register = NonNullable<paths['/accounts']['post']['requestBody']>['content']['application/json'];
    expectTypeOf<Register>().toEqualTypeOf<components['schemas']['RegisterAccountRequest']>();

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
    const fetchSpy = stubBrowserFetch();

    const client = createAuthClient();
    for (const method of ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'use'] as const) {
      expect(client[method], method).toBeTypeOf('function');
    }
    const seenByMiddleware: string[] = [];
    client.use({
      onRequest({ request }) {
        seenByMiddleware.push(request.url);
      },
    });
    await client.GET('/accounts/me');

    const expected = `${window.location.origin}/accounts/me`;
    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const sent = fetchSpy.mock.calls[0]![0];
    expect(sent.url).toBe(expected);
    expect(sent.method).toBe('GET');
    expect(seenByMiddleware).toEqual([expected]);
  });

  it('sends a non-GET operation with its JSON body to the page origin (L9)', async () => {
    const fetchSpy = stubBrowserFetch();
    const body = { email: 'someone@example.com', password: 'twelve-chars-ok' };

    await createAuthClient().POST('/accounts', { body });

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const sent = fetchSpy.mock.calls[0]![0];
    expect(sent.method).toBe('POST');
    expect(sent.url).toBe(`${window.location.origin}/accounts`);
    expect(sent.headers.get('Content-Type')).toBe('application/json');
    expect(await sent.json()).toEqual(body);
  });

  it('reads no environment key and names no absolute URL in the hand-written entry (L9, L10)', async () => {
    const entry = await readFile(join(clientDir, 'src/index.ts'), 'utf8');
    expect(entry).not.toContain('import.meta' + '.env');
    expect(entry).not.toMatch(/https?:\/\//);
    expect(entry).toContain("export type { components, operations, paths } from './generated/auth';");
  });
});
