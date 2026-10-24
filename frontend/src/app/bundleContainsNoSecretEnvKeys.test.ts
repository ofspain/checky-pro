/// <reference types="node" />
/**
 * `bundleContainsNoSecretEnvKeys` (package.md §8, line 161) — R58, L10.
 *
 * The bundle is the one artifact the SPA hands to anyone who asks. R58: "WHEN the production bundle is built,
 * THEN it SHALL contain no secret value and no environment key outside the public configuration list in
 * `design.md` §4c." Two obligations, needing different instruments - the key half is decidable from a list, the
 * secret half is not.
 *
 * So the primary instrument is not pattern matching, which only ever catches the shapes someone thought of. It is
 * closing the routes by which Vite can inline a value at all: `envPrefix` pinned to `VITE_` (so nothing else on
 * the build machine can be baked in), no `define` block, no committed `.env` file, no surviving
 * `import.meta.env.` access, and no sourcemap. Shape matching is the second net, not the first.
 *
 * Everything goes through one `scanBundle`, and the negative control feeds synthetic files to the *same* function.
 * A verification task whose scanner has never been shown to catch a leak has verified nothing - and a scan over an
 * empty tree satisfies every prohibition in this file at once, which is why `scanBundle` refuses one.
 */
import { execFile } from 'node:child_process';
import { mkdtemp, mkdir, readdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, relative } from 'node:path';
import { promisify } from 'node:util';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

const run = promisify(execFile);

// This file is frontend/src/app/<name>; the repo root is one above frontend/.
const frontendDir = dirname(dirname(dirname(expect.getState().testPath as string)));
const repoRoot = dirname(frontendDir);

/** The §4c allowlist, parsed from the spec rather than restated here (AC6). */
const EXPECTED_ALLOWLIST = [
  'VITE_API_BASE_PATH',
  'VITE_OIDC_CLIENT_ID',
  'VITE_OIDC_ISSUER',
  'VITE_OIDC_REDIRECT_URI',
] as const;

/**
 * Absolute URLs the build legitimately contains, every one from a dependency rather than from our own source -
 * which T01 already forbids from naming any absolute URL at all. Asserted by **set equality**, so a URL arriving
 * from a new dependency, a plugin or a slip of ours fails this file until someone justifies it here (AC8).
 */
const KNOWN_ABSOLUTE_URLS = [
  // react-router 8.4.0 internals: a fallback origin when there is no window, and a base for parsing relative
  // hrefs. Never a fetch target, and not configuration.
  'http://localhost',
  // XML namespace identifiers from React's SVG and MathML attribute handling. Names, not addresses.
  'http://www.w3.org/1998/Math/MathML',
  'http://www.w3.org/1999/xlink',
  'http://www.w3.org/2000/svg',
  'http://www.w3.org/XML/1998/namespace',
  // Documentation links inside library warning and error messages.
  'https://bit.ly/wb-precache',
  'https://react.dev/errors/',
  'https://reactrouter.com/en/main/routers/picking-a-router.',
] as const;

/** This platform's own services. No emitted file may name one (L9, and the leak that would matter most). */
const PLATFORM_HOSTS = ['auth', 'payment', 'notification', 'crypto', 'postgres', 'localstack'] as const;

const ENV_KEY_PATTERNS = [/VITE_[A-Z0-9_]+/g, /process\.env\.([A-Za-z0-9_]+)/g];

const SECRET_PATTERNS: ReadonlyArray<{ name: string; pattern: RegExp }> = [
  { name: 'private key block', pattern: /-----BEGIN [A-Z ]*PRIVATE KEY-----/g },
  { name: 'client_secret', pattern: /client_secret/g },
  { name: 'bearer token', pattern: /Bearer\s+ey[A-Za-z0-9._-]{10,}/g },
  { name: 'JWT', pattern: /\bey[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}/g },
  { name: 'AWS access key id', pattern: /\bAKIA[0-9A-Z]{16}\b/g },
];

/**
 * Distinctive real values from the platform's own configuration. Only distinctive ones: the stack also sets
 * AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY to `test` (compose.yaml:21-22), which matches ordinary words and
 * would fire on any bundle containing "test".
 */
const REAL_PLATFORM_VALUES = ['checky-local-only'] as const;

const ABSOLUTE_URL_PATTERN = /https?:\/\/[A-Za-z0-9._:-]+(?:\/[A-Za-z0-9._\-/]*)?/g;

type BundleScan = {
  files: string[];
  envKeys: string[];
  secrets: Array<{ file: string; hit: string }>;
  absoluteUrls: string[];
  sourcemaps: string[];
};

async function walk(dir: string, base = ''): Promise<string[]> {
  const entries = await readdir(dir, { withFileTypes: true });
  const out: string[] = [];
  for (const entry of entries) {
    const rel = join(base, entry.name);
    if (entry.isDirectory()) out.push(...(await walk(join(dir, entry.name), rel)));
    else out.push(rel);
  }
  return out.sort();
}

/**
 * The single definition of "what counts as a hit", used by the real assertions and by the negative control.
 *
 * Throws on an empty tree. Every prohibition below is of the form "this set is empty", and over zero files they
 * are all satisfied by accident - the one failure mode that would make this whole file decorative.
 */
async function scanBundle(treeRoot: string): Promise<BundleScan> {
  const files = await walk(treeRoot);
  if (files.length === 0) throw new Error(`scanBundle: nothing to scan in ${treeRoot}`);

  const envKeys = new Set<string>();
  const secrets: Array<{ file: string; hit: string }> = [];
  const absoluteUrls = new Set<string>();

  for (const file of files) {
    const source = await readFile(join(treeRoot, file), 'utf8');
    for (const pattern of ENV_KEY_PATTERNS) {
      for (const match of source.matchAll(pattern)) envKeys.add(match[1] ?? match[0]);
    }
    for (const { name, pattern } of SECRET_PATTERNS) {
      if (pattern.test(source)) secrets.push({ file, hit: name });
      pattern.lastIndex = 0;
    }
    for (const value of REAL_PLATFORM_VALUES) {
      if (source.includes(value)) secrets.push({ file, hit: `real platform value ${value}` });
    }
    for (const match of source.matchAll(ABSOLUTE_URL_PATTERN)) absoluteUrls.add(match[0]);
  }

  return {
    files,
    envKeys: [...envKeys].sort(),
    secrets,
    absoluteUrls: [...absoluteUrls].sort(),
    sourcemaps: files.filter((file) => file.endsWith('.map')),
  };
}

/** The five-step parser from the frozen brief: find the heading, take the backticked VITE_ tokens under it. */
async function allowlistFromSpec(): Promise<string[]> {
  const design = await readFile(join(repoRoot, 'spec/frontend/design.md'), 'utf8');
  const lines = design.split('\n');
  const headingIndex = lines.findIndex((line) => /Environment keys/i.test(line));
  expect(headingIndex, 'design.md no longer declares an "Environment keys" section').toBeGreaterThan(-1);

  const paragraph: string[] = [];
  for (let i = headingIndex; i < lines.length; i += 1) {
    if (i > headingIndex && lines[i]!.trim() === '') break;
    paragraph.push(lines[i]!);
  }
  const tokens = [...paragraph.join(' ').matchAll(/`([^`]+)`/g)].map((match) => match[1]!);
  return [...new Set(tokens.filter((token) => /^VITE_[A-Z0-9_]+$/.test(token)))].sort();
}

describe('bundleContainsNoSecretEnvKeys', () => {
  let outDir: string;
  let scan: BundleScan;

  beforeAll(async () => {
    outDir = await mkdtemp(join(tmpdir(), 'checky-bundle-scan-'));
    // The real CLI, as `pnpm build` runs it - not Vite's JS API. They do not produce the same artifact: invoked
    // in-process from vitest, NODE_ENV is already `test`, so React's development build survives into the output
    // (hundreds of react.dev warning links, which is how this was found). R58 is about the production bundle, so
    // the production bundle is what this file scans. T07 sets the precedent for shelling out to a real binary.
    // NODE_ENV must not be inherited. Vitest sets it to `test`, and a child build that sees it keeps React's
    // development build - so the environment is cleaned to what a shell running `pnpm build` would give, letting
    // `vite build` apply its own production default.
    const buildEnv = { ...process.env };
    delete buildEnv.NODE_ENV;
    delete buildEnv.VITEST;
    delete buildEnv.VITEST_MODE;
    await run(join(frontendDir, 'node_modules/.bin/vite'), ['build', '--outDir', outDir, '--emptyOutDir'], {
      cwd: frontendDir,
      env: buildEnv,
    });
    scan = await scanBundle(outDir);
  }, 120_000);

  afterAll(async () => {
    await rm(outDir, { recursive: true, force: true });
  });

  // ── The tree the prohibitions are asserted over (AC1) ──────────────────────────────────────────────────────

  describe('the artifact being scanned', () => {
    it('is the whole emitted tree, discovered rather than assumed', () => {
      expect(scan.files.length).toBeGreaterThan(0);
      expect(scan.files).toContain('index.html');
      expect(scan.files.filter((file) => /^assets\/.+\.js$/.test(file))).toHaveLength(1);
      // The worker is found by shape, not by name, so a renamed or hashed filename is still scanned (D-5).
      expect(scan.files.filter((file) => /^serviceWorker(\.\w+)?\.js$/.test(file))).toHaveLength(1);
    });

    it('is a production build, not a development one carrying dev-only code', async () => {
      // The property that makes every assertion below meaningful. A development bundle is a different artifact
      // with a different surface: it keeps React's warning machinery, and it would keep anything else the build
      // strips. Asserted on the artifact, so a change of build invocation cannot quietly swap what is scanned.
      const chunk = await readFile(join(outDir, scan.files.find((f) => f.startsWith('assets/'))!), 'utf8');
      expect(chunk).not.toMatch(/react\.dev\/(link|warnings)/);
      expect(chunk).not.toContain('process.env.NODE_ENV');
    });

    it('includes the files that sit outside assets/, which a JS glob would skip', () => {
      const outsideAssets = scan.files.filter((file) => !file.startsWith('assets/'));
      expect(outsideAssets).toEqual(['index.html', 'serviceWorker.js']);
    });
  });

  // ── AC7 · the negative control, through the same scanner ───────────────────────────────────────────────────

  describe('the scanner reports a leak when there is one (AC7)', () => {
    let controlScan: BundleScan;
    let controlDir: string;

    beforeAll(async () => {
      controlDir = await mkdtemp(join(tmpdir(), 'checky-bundle-control-'));
      await mkdir(join(controlDir, 'assets'), { recursive: true });
      await writeFile(
        join(controlDir, 'assets/leaky.js'),
        [
          'const a = import.meta.env.VITE_NOT_ALLOWLISTED;',
          'const b = process.env.POSTGRES_PASSWORD;',
          'const c = "checky-local-only";',
          'const d = "-----BEGIN RSA PRIVATE KEY-----";',
          'const e = "client_secret=hunter2";',
          'const f = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.not-a-real-signature";',
          'const g = "AKIAIOSFODNN7EXAMPLE";',
          'const h = "http://auth:8080/oauth2/token";',
        ].join('\n'),
      );
      await writeFile(join(controlDir, 'assets/leaky.js.map'), '{"version":3}');
      controlScan = await scanBundle(controlDir);
    });

    afterAll(async () => {
      await rm(controlDir, { recursive: true, force: true });
    });

    it('reports an environment key outside the allowlist', () => {
      expect(controlScan.envKeys).toContain('VITE_NOT_ALLOWLISTED');
      expect(controlScan.envKeys.filter((key) => !(EXPECTED_ALLOWLIST as readonly string[]).includes(key))).not.toEqual([]);
    });

    it('reports a process.env key', () => {
      expect(controlScan.envKeys).toContain('POSTGRES_PASSWORD');
    });

    it.each([
      'private key block',
      'client_secret',
      'bearer token',
      'JWT',
      'AWS access key id',
      'real platform value checky-local-only',
    ])('reports %s', (hit) => {
      expect(controlScan.secrets.map((secret) => secret.hit)).toContain(hit);
    });

    it('reports an absolute URL naming one of this platform\'s services', () => {
      expect(controlScan.absoluteUrls).toContain('http://auth:8080/oauth2/token');
      expect(controlScan.absoluteUrls.filter((url) => !(KNOWN_ABSOLUTE_URLS as readonly string[]).includes(url))).not.toEqual([]);
    });

    it('reports a sourcemap', () => {
      expect(controlScan.sourcemaps).toEqual(['assets/leaky.js.map']);
    });

    it('refuses an empty tree instead of passing every prohibition vacuously', async () => {
      const empty = await mkdtemp(join(tmpdir(), 'checky-bundle-empty-'));
      try {
        await expect(scanBundle(empty)).rejects.toThrow('nothing to scan');
      } finally {
        await rm(empty, { recursive: true, force: true });
      }
    });
  });

  // ── AC1, AC7 · environment keys, default-deny ──────────────────────────────────────────────────────────────

  describe('environment keys (AC1, AC7)', () => {
    it('contains no key outside the §4c allowlist', async () => {
      const allowed = await allowlistFromSpec();
      expect(scan.envKeys.filter((key) => !allowed.includes(key))).toEqual([]);
    });

    it('contains no environment key at all today, which is stricter than R58 requires', () => {
      // Recorded separately so the present state is visible without being mistaken for the rule: the SPA derives
      // its OIDC configuration at runtime, so none of the four allowlisted keys is baked in. See OQ-1 - the
      // allowlist is a ceiling, not a plan, and T01 forbids reading one in source at all.
      expect(scan.envKeys).toEqual([]);
    });

    it('contains no surviving import.meta.env access (AC11)', async () => {
      for (const file of scan.files) {
        const source = await readFile(join(outDir, file), 'utf8');
        expect(source, file).not.toContain('import.meta' + '.env.');
      }
    });
  });

  // ── AC6 · the allowlist is the spec's ──────────────────────────────────────────────────────────────────────

  describe('the allowlist tracks design.md §4c (AC6)', () => {
    it('is exactly the four public keys', async () => {
      expect(await allowlistFromSpec()).toEqual([...EXPECTED_ALLOWLIST]);
    });

    it('is a set equality, so adding or removing a key in the spec fails here', async () => {
      const allowed = await allowlistFromSpec();
      expect(allowed).toHaveLength(4);
      for (const key of allowed) expect(key).toMatch(/^VITE_[A-Z0-9_]+$/);
    });
  });

  // ── AC2, AC5 · secrets ─────────────────────────────────────────────────────────────────────────────────────

  describe('secrets (AC2, AC5)', () => {
    it('contains no secret-shaped value and no real platform value', () => {
      expect(scan.secrets).toEqual([]);
    });
  });

  // ── AC3, AC4 · the mechanisms by which a value could be inlined ────────────────────────────────────────────

  describe('the inlining mechanisms are closed (AC3, AC4)', () => {
    it('pins envPrefix to VITE_, so nothing else on the build machine can be baked in', async () => {
      const config = await readFile(join(frontendDir, 'vite.config.ts'), 'utf8');
      expect(config).toContain("envPrefix: ['VITE_']");
      // The catastrophic one-word edits: an empty prefix inlines every variable the build machine holds.
      expect(config).not.toMatch(/envPrefix:\s*\[?\s*''/);
      expect(config).not.toMatch(/envPrefix:\s*\[\s*\]/);
    });

    it('declares no define block, which would inline arbitrary values', async () => {
      const config = await readFile(join(frontendDir, 'vite.config.ts'), 'utf8');
      expect(config).not.toMatch(/^\s*define:/m);
    });

    it('has no .env file for Vite to load', async () => {
      const entries = await readdir(frontendDir, { withFileTypes: true });
      expect(entries.filter((entry) => entry.name.startsWith('.env')).map((entry) => entry.name)).toEqual([]);
    });

    it('emits no sourcemap, which would carry original source text (AC10)', () => {
      expect(scan.sourcemaps).toEqual([]);
    });
  });

  // ── AC8 · absolute URLs, by set equality ───────────────────────────────────────────────────────────────────

  describe('absolute URLs (AC8)', () => {
    it('contains exactly the enumerated vendor URLs and no others', () => {
      expect(scan.absoluteUrls).toEqual([...KNOWN_ABSOLUTE_URLS]);
    });

    it('names no service of this platform', () => {
      for (const url of scan.absoluteUrls) {
        for (const host of PLATFORM_HOSTS) {
          expect(url, `${url} names ${host}`).not.toMatch(new RegExp(`//${host}[:/]|//${host}$`));
        }
      }
    });

    it('keeps every entry justified, not merely listed', async () => {
      // Each KNOWN_ABSOLUTE_URLS entry must sit under a comment line, so a future addition cannot be slipped in
      // without a reason. Asserted over this file's own source.
      const self = await readFile(expect.getState().testPath as string, 'utf8');
      const block = self.slice(
        self.indexOf('const KNOWN_ABSOLUTE_URLS'),
        self.indexOf('] as const;', self.indexOf('const KNOWN_ABSOLUTE_URLS')),
      );
      expect(block.split('\n').filter((line) => line.trim().startsWith('//')).length).toBeGreaterThanOrEqual(3);
      expect(relative(repoRoot, expect.getState().testPath as string)).toBe(
        'frontend/src/app/bundleContainsNoSecretEnvKeys.test.ts',
      );
    });
  });
});
