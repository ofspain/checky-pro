/// <reference types="node" />
import { execFile } from 'node:child_process';
import { access, copyFile, cp, mkdir, mkdtemp, readFile, readdir, rm, symlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { promisify } from 'node:util';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

const run = promisify(execFile);

// This file is frontend/src/api/<name>. Vitest's own test path is used because jsdom gives
// import.meta.url a non-file URL.
const frontendDir = dirname(dirname(dirname(expect.getState().testPath as string)));
const configPath = join(frontendDir, '.oxlintrc.json');
const oxlintBin = join(frontendDir, 'node_modules/.bin/oxlint');
const L6_MESSAGE = 'Call the backend through @themistra/api-client (L6).';
const WINDOW_OBJECTS = ['window', 'globalThis', 'self', 'top', 'parent', 'opener', 'frames'];

type Rule = 'no-restricted-globals' | 'no-restricted-properties';

/** Every hand-written way to reach the backend that the L6 rule must report (AC1). */
const BANNED: ReadonlyArray<{ form: string; source: string; rule: Rule }> = [
  { form: 'bare fetch()', source: 'fetch("/accounts/me");', rule: 'no-restricted-globals' },
  { form: 'const f = fetch', source: 'const f = fetch; f("/accounts/me");', rule: 'no-restricted-globals' },
  { form: 'new XMLHttpRequest()', source: 'new XMLHttpRequest();', rule: 'no-restricted-globals' },
  ...WINDOW_OBJECTS.map((object) => ({
    form: `${object}.fetch`,
    source: `${object}.fetch("/accounts/me");`,
    rule: 'no-restricted-properties' as const,
  })),
  { form: "window['fetch']", source: 'window["fetch"]("/accounts/me");', rule: 'no-restricted-properties' },
  { form: "globalThis['fetch']", source: 'globalThis["fetch"]("/accounts/me");', rule: 'no-restricted-properties' },
  { form: "self['fetch']", source: 'self["fetch"]("/accounts/me");', rule: 'no-restricted-properties' },
  { form: 'window.fetch.bind', source: 'window.fetch.bind(window);', rule: 'no-restricted-properties' },
  { form: 'window.fetch.call', source: 'window.fetch.call(window, "/accounts/me");', rule: 'no-restricted-properties' },
  { form: 'const { fetch } = window', source: 'const { fetch } = window; fetch("/x");', rule: 'no-restricted-properties' },
  { form: 'window.XMLHttpRequest', source: 'new window.XMLHttpRequest();', rule: 'no-restricted-properties' },
  { form: 'self.XMLHttpRequest', source: 'new self.XMLHttpRequest();', rule: 'no-restricted-properties' },
  { form: "window['XMLHttpRequest']", source: 'new window["XMLHttpRequest"]();', rule: 'no-restricted-properties' },
  { form: "self['XMLHttpRequest']", source: 'new self["XMLHttpRequest"]();', rule: 'no-restricted-properties' },
  {
    form: "globalThis['XMLHttpRequest']",
    source: 'new globalThis["XMLHttpRequest"]();',
    rule: 'no-restricted-properties',
  },
];

/** Code that names `fetch` without hand-writing a backend call (AC3). */
const ALLOWED: ReadonlyArray<{ form: string; source: string }> = [
  { form: 'a parameter named fetch', source: 'export function h(fetch: (u: string) => void) { fetch("/x"); }' },
  { form: 'a local const fetch', source: 'const fetch = (u: string) => u; fetch("/x");' },
  {
    form: 'the generated client',
    source: 'import { createAuthClient } from "@themistra/api-client";\nvoid createAuthClient().GET("/accounts/me");',
  },
  {
    form: 'the generated OIDC client',
    source:
      'import { createOidcClient } from "@themistra/api-client";\nvoid createOidcClient().POST("/oauth2/token");',
  },
  { form: "vi.spyOn(globalThis, 'fetch')", source: 'import { vi } from "vitest";\nvi.spyOn(globalThis, "fetch");' },
  // A type position makes no request; oxlint 1.87.0 doesn't report it, and test mocks may need it.
  { form: 'XMLHttpRequest as a type only', source: 'export const echo = (xhr: XMLHttpRequest) => xhr;' },
];

let fixtureDir: string;

/** Runs oxlint and returns its exit code and output; a non-zero exit is a result, not an error. */
async function lint(args: string[], cwd = frontendDir): Promise<{ code: number; output: string }> {
  try {
    const { stdout, stderr } = await run(oxlintBin, args, { cwd });
    return { code: 0, output: stdout + stderr };
  } catch (error) {
    const failed = error as { code?: unknown; stdout?: string; stderr?: string };
    if (typeof failed.code !== 'number') throw error;
    return { code: failed.code, output: `${failed.stdout ?? ''}${failed.stderr ?? ''}` };
  }
}

/** Lints one fixture file with the real config and the lint script's flags. */
async function lintSource(name: string, source: string) {
  const file = join(fixtureDir, `${name.replace(/[^A-Za-z0-9]+/g, '_')}.ts`);
  await writeFile(file, `${source}\n`);
  return lint(['--no-ignore', '-c', configPath, file]);
}

async function readJson<T>(path: string): Promise<T> {
  return JSON.parse(await readFile(path, 'utf8')) as T;
}

type RuleEntry = ['error' | 'warn' | 'off', ...Array<Record<string, string>>];
type OxlintConfig = { rules: Record<string, RuleEntry>; overrides?: unknown };

beforeAll(async () => {
  fixtureDir = await mkdtemp(join(tmpdir(), 'lint-l6-'));
});

afterAll(async () => {
  await rm(fixtureDir, { recursive: true, force: true });
});

describe('lintRejectsHandWrittenBackendFetch', () => {
  it.each(BANNED)('reports $form with $rule and the L6 message (AC1)', async ({ form, source, rule }) => {
    const { code, output } = await lintSource(`banned ${form}`, source);
    expect(code, output).toBe(1);
    expect(output).toContain(`eslint(${rule})`);
    expect(output).toContain(L6_MESSAGE);
  }, 30_000);

  it.each(ALLOWED)('allows $form (AC3)', async ({ form, source }) => {
    const { code, output } = await lintSource(`allowed ${form}`, source);
    expect(code, output).toBe(0);
  }, 30_000);

  it('passes today\'s frontend/src through the lint script, running only the two L6 rules (AC4)', async () => {
    const { code, output } = await lint(['--no-ignore', '-c', '.oxlintrc.json', 'src']);
    expect(code, output).toBe(0);
    expect(output).toContain('Found 0 warnings and 0 errors');
    expect(output).toContain('with 2 rules');
  }, 30_000);

  it('runs lint first in build, with no ignore files honoured (AC2)', async () => {
    const pkg = await readJson<{ scripts: Record<string, string>; devDependencies: Record<string, string> }>(
      join(frontendDir, 'package.json'),
    );
    expect(pkg.scripts.lint).toBe('oxlint --no-ignore -c .oxlintrc.json src');
    expect(pkg.scripts.build).toBe('pnpm run lint && tsc --noEmit && vite build');
    expect(pkg.devDependencies.oxlint).toBe('1.87.0');

    // The binary that actually runs is the pinned one, not a stale or global install.
    const { stdout } = await run(oxlintBin, ['--version']);
    expect(stdout.trim()).toBe('Version: 1.87.0');
  });

  it('stops build at lint, so tsc and Vite never run on a violation (AC2, end to end)', async () => {
    // A copy of frontend/ with its real scripts, config and sources. Dependencies are dropped from the copy's
    // manifest so pnpm doesn't try to install them; node_modules is linked from the real package.
    const project = join(fixtureDir, 'build-project');
    await mkdir(project);
    for (const file of ['.oxlintrc.json', 'index.html', 'tsconfig.json', 'vite.config.ts']) {
      await copyFile(join(frontendDir, file), join(project, file));
    }
    const pkg = await readJson<Record<string, unknown>>(join(frontendDir, 'package.json'));
    delete pkg.dependencies;
    delete pkg.devDependencies;
    await writeFile(join(project, 'package.json'), JSON.stringify(pkg));
    await symlink(join(frontendDir, 'node_modules'), join(project, 'node_modules'), 'dir');
    await cp(join(frontendDir, 'src'), join(project, 'src'), { recursive: true });

    const build = async () => {
      try {
        const { stdout, stderr } = await run('pnpm', ['run', 'build'], { cwd: project });
        return { code: 0, output: stdout + stderr };
      } catch (error) {
        const failed = error as { code?: unknown; stdout?: string; stderr?: string };
        if (typeof failed.code !== 'number') throw error;
        return { code: failed.code, output: `${failed.stdout ?? ''}${failed.stderr ?? ''}` };
      }
    };
    const distExists = () => access(join(project, 'dist')).then(() => true, () => false);

    // Control: the same copy, with today's clean sources, runs all three steps.
    const clean = await build();
    expect(clean.code, clean.output).toBe(0);
    expect(await distExists()).toBe(true);
    await rm(join(project, 'dist'), { recursive: true, force: true });

    // One violation, which is also a type error, so tsc would report it if it ran.
    await writeFile(
      join(project, 'src/handWritten.ts'),
      'export const call = () => fetch("/accounts/me");\nexport const wrong: number = "not a number";\n',
    );
    const violating = await build();
    expect(violating.code, violating.output).not.toBe(0);
    expect(violating.output).toContain('eslint(no-restricted-globals)');
    expect(violating.output).not.toMatch(/error TS\d+/);
    expect(violating.output).not.toContain('built in');
    expect(await distExists()).toBe(false);
  }, 120_000);

  it('keeps a violation listed in .eslintignore failing under the lint script', async () => {
    const pkg = await readJson<{ scripts: Record<string, string> }>(join(frontendDir, 'package.json'));
    const [bin, ...args] = pkg.scripts.lint!.split(' ');
    expect(bin).toBe('oxlint');

    const project = join(fixtureDir, 'ignored-project');
    await mkdir(join(project, 'src'), { recursive: true });
    await copyFile(configPath, join(project, '.oxlintrc.json'));
    await writeFile(join(project, 'src/hidden.ts'), 'fetch("/accounts/me");\n');
    await writeFile(join(project, '.eslintignore'), 'src/hidden.ts\n');

    const { code, output } = await lint(args, project);
    expect(code, output).toBe(1);
    expect(output).toContain('src/hidden.ts');
  }, 30_000);

  it('bans exactly fetch and XMLHttpRequest, as globals and on the seven window objects, with no overrides (AC5)', async () => {
    const config = await readJson<OxlintConfig>(configPath);
    expect(config).not.toHaveProperty('overrides');
    expect(Object.keys(config.rules).sort()).toEqual(['no-restricted-globals', 'no-restricted-properties']);

    const [globalsLevel, ...globals] = config.rules['no-restricted-globals']!;
    expect(globalsLevel).toBe('error');
    expect(globals.map((entry) => entry.name).sort()).toEqual(['XMLHttpRequest', 'fetch']);

    const [propertiesLevel, ...properties] = config.rules['no-restricted-properties']!;
    expect(propertiesLevel).toBe('error');
    const pairs = properties.map((entry) => `${entry.object}.${entry.property}`).sort();
    const expected = WINDOW_OBJECTS.flatMap((object) => [`${object}.fetch`, `${object}.XMLHttpRequest`]).sort();
    expect(pairs).toEqual(expected);

    for (const entry of [...globals, ...properties]) {
      expect(entry.message).toBe(L6_MESSAGE);
    }
  });

  it('finds no inline directive in frontend/src that could silence the rule', async () => {
    // Built from parts so this file doesn't match its own scan.
    const directive = new RegExp('(?:oxlint|eslint)' + '-disable');
    // Positive control: the pattern matches the directive forms that silence oxlint.
    for (const sample of [
      '// oxlint' + '-disable-next-line no-restricted-globals',
      '// eslint' + '-disable-next-line',
      '/* oxlint' + '-disable */',
      '/* eslint' + '-disable no-restricted-properties */',
    ]) {
      expect(directive.test(sample), sample).toBe(true);
    }
    const srcDir = join(frontendDir, 'src');
    const files = (await readdir(srcDir, { recursive: true })).filter((path) => /\.(?:ts|tsx)$/.test(path));
    expect(files.length).toBeGreaterThan(0);

    const offenders: string[] = [];
    for (const file of files) {
      if (directive.test(await readFile(join(srcDir, file), 'utf8'))) offenders.push(file);
    }
    expect(offenders).toEqual([]);
  });
});
