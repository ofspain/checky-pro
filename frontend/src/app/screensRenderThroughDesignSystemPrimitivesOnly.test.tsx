/// <reference types="node" />
/**
 * `screensRenderThroughDesignSystemPrimitivesOnly` (package.md §8) — task 173, the design system.
 *
 * THE WORD THE TASK STATEMENT TURNS ON IS "Only": the screens *"render through its primitives rather than bare
 * elements"*. A pinned list of screens cannot see a sixth screen; a glob cannot say which files were
 * deliberate. So §6e of the frozen brief freezes BOTH, cross-checked, and this file implements all three parts
 * - two pinned lists, a derived sweep over real source, and a per-file scan - because each alone has a hole.
 *
 * THE NUMBER IN §6e(2) IS A TRAP AND IT IS DISARMED HERE (phase 7 F7, phase 8 I7). The brief says *"after
 * this task there must be thirteen"*. Thirteen is the size of the UNION of the two lists, 5 + 8. The number of
 * files that CONTAIN a lowercase JSX tag is EIGHT, because a render site containing none is the whole point of
 * AC2. `expect(derived).toHaveLength(13)` fails; `toHaveLength(8)` passes while quietly dropping the
 * render-site half of the claim. Three assertions keep both halves: the derived set equals the primitive list,
 * its intersection with the render sites is empty, and the union is thirteen.
 *
 * TASK 174 MADE THE UNION FOURTEEN, and found a hole in the cross-check while doing it. The sweep's
 * `for (const file of bearing)` clause only reaches files the sweep FINDS, and the sweep finds files by
 * lowercase JSX tag. `HomeScreen.tsx` renders entirely through primitives - which is this suite's whole
 * point - so it contains no such tag, and when it was added the sweep said nothing. A screen could be
 * written correctly and never enter `RENDER_SITES`, and the instrument that exists to pin the render sites
 * would have been the last to notice. The clause measured the files least likely to be missing.
 *
 * What closes it is a SECOND derivation that does not go through tags at all: every production file outside
 * `app/ui/` that imports from the barrel is a render site, by definition, because the barrel is the only
 * place the primitives come from. That set is checked against `RENDER_SITES` in both directions below, so a
 * new screen is caught whether or not it ever writes a `<div>`.
 *
 * WHAT ENFORCES AC4 IS `tsc`, NOT THIS FILE (phase 7 F5). `className` and `style` are absent from all eight
 * primitive types, so a screen that styles itself is a compile error - and that is not a developer's
 * discipline, it is measured: `lintRejectsHandWrittenBackendFetch.test.ts`'s end-to-end case runs a real
 * `pnpm run build` and asserts today's sources compile, so the type-level guarantee has a test behind it that
 * this task did not have to write. The scans below are corroboration, written so the rule is READABLE in one
 * place; the case that would actually fail is named in a test of its own so the citation cannot rot.
 *
 * AC9 PRINTS THE COMPUTED RATIO IN EVERY MESSAGE, which is the remaining half of phase 7's F1. All four
 * ratios in `styles.css`'s comments were wrong - one by 73% - while every threshold was met, so the
 * assertions passed and the prose lied. A test that recomputes from the declarations cannot catch a wrong
 * comment; a test that prints what it computed puts the real number in front of the next reader.
 */
import { readdir, readFile, mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, relative } from 'node:path';
import { act, createRef } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { build } from 'vite';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AUTHORIZE_STORAGE_KEYS } from '../auth/authorizeRequest';
import { CallbackScreen } from '../auth/CallbackScreen';
import { SignedOutScreen } from '../auth/SignedOutScreen';
import { SignInScreen } from '../auth/SignInScreen';
import { en } from '../i18n/catalogue';
import { createI18n } from '../i18n/i18n';
import { createRateLimitNotice } from '../api/rateLimitNotice';
import { RateLimitNotice } from './RateLimitNotice';
import {
  ActionButton, Banner, EmailField, FocusHolder, Form, Heading, PasswordField, Screen, StatusRegion,
  SubmitButton, Surface, Text, TextLink,
} from './ui';

// The exchange is stubbed to a promise that never settles, so `CallbackScreen` STAYS on its working state and
// its own DOM can be read. Without this it discards and navigates, and the structure under test is gone
// before the assertion runs.
vi.mock('../auth/codeExchange', () => ({
  exchangeAuthorizationCode: () => new Promise(() => {}),
}));

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// This file is frontend/src/app/<name>. `expect.getState().testPath` is used here and works; the REASON
// recorded for it was false, and task 174 measured that rather than inheriting it (phase 8 I5). T41's
// phase 10 wrote that "`import.meta.url` is not a `file:` URL under vitest's jsdom environment, so
// `readFile` rejects it", quoting the error "The URL must be of scheme file". Probed directly under this
// exact config: `import.meta.url` IS `file:///Users/.../<name>`, and `readFile(import.meta.url)` fails
// with ENOENT - it treats the string as a PATH, and no file is literally named `file:///...`. The cause
// was passing a URL where a path was expected; `fileURLToPath(import.meta.url)` works, and
// `homeRouteRendersLandingScreenAndManagesFocus.test.tsx` uses it. Both patterns are correct. The quoted
// error message was never what happens, which is why the note is corrected rather than just dropped.
const appDir = dirname(expect.getState().testPath as string);
const srcDir = dirname(appDir);
const frontendDir = dirname(srcDir);

/** §6e(1), first pinned list: the five files that render a screen. A sixth must be added deliberately. */
const RENDER_SITES = [
  'app/AppShell.tsx',
  // TASK 42 - the authenticated layout, the seventh render site and the second behind the auth gate.
  'app/AuthenticatedLayout.tsx',
  // TASK 174 - the home screen, the sixth render site and the first behind the auth gate.
  'app/HomeScreen.tsx',
  'app/RateLimitNotice.tsx',
  'auth/CallbackScreen.tsx',
  'auth/SignInScreen.tsx',
  'auth/SignedOutScreen.tsx',
  // TASK 43 - the registration screen, the eighth render site and the FIRST under `features/`. It is also
  // the first render site at a third directory level, which is what broke the barrel-import scan below:
  // that scan matched './ui' and '../app/ui' and nothing deeper, so this file was invisible to it while
  // being perfectly visible to the tag sweep. Adding it here is what surfaced that, because the scan's
  // equality is against THIS list.
  'features/account/RegisterScreen.tsx',
] as const;

/**
 * §6e(1), second pinned list: the eight primitives. A ninth must be added deliberately.
 *
 * SEVEN OF THE EIGHT RENDER A LOWERCASE TAG, NOT EIGHT, and the frozen brief's §6e(2) assumed otherwise.
 * `TextLink` renders react-router's `<Link>` - a component tag, which challenge F14 and §6c required of it
 * (`to`, never `href`) - so it bears no lowercase tag of its own. The sweep below therefore pins seven and
 * names the exception, rather than asserting a number that is wrong in the safe direction.
 */
const PRIMITIVES = [
  'ActionButton',
  // TASK 42 - the ninth primitive, the `<header>` banner landmark. Added because the authenticated layout
  // must hold a sign-out control a keyboard user can FIND, and rule 2 of this layer sets `role` inside a
  // primitive and never accepts it from a consumer: a landmark can therefore exist only inside one.
  'Banner',
  // TASK 43 - FOUR AT ONCE, which no task before this one has done, and the reason is rule 2 rather than
  // convenience: `type="password"`, `type="submit"` and the `<form>`'s own submit handling are each a
  // contract this layer sets INSIDE a primitive and never accepts from a consumer. A registration screen
  // built out of the nine that existed would have had to render `<form>`, `<label>` and `<input>` itself,
  // which §6e(3) forbids outright - so the alternative to these four files was not a cheaper form, it was
  // no form. `EmailField` and `PasswordField` are two files rather than one parameterised field for the
  // same reason: the type is the contract, and a `type` prop would hand it back to the caller.
  'EmailField',
  'FocusHolder',
  'Form',
  'Heading',
  'PasswordField',
  'Screen',
  'StatusRegion',
  'SubmitButton',
  'Surface',
  'Text',
  'TextLink',
] as const;

const PRIMITIVE_FILES = PRIMITIVES.map((name) => `app/ui/${name}.tsx`);

/** The one primitive that delegates its element to react-router instead of rendering one (see above). */
const DELEGATES_ITS_ELEMENT = 'app/ui/TextLink.tsx';

/**
 * How many DISTINCT lowercase elements a primitive renders, wherever that is not one (task 43, D3).
 *
 * THE CHALLENGE THAT ADDED THE FIELDS MISSED THIS, and the freeze caught it: a labelled control is two
 * elements, not one. `<input>` alone is a control a screen reader announces without a name, and the
 * association is `<label htmlFor>` - which rule 2 keeps INSIDE the primitive, exactly as it keeps `role`
 * inside `Banner` and `type` inside `ActionButton`. So the field files render two, and the number is pinned
 * per file rather than loosened to "one or two": a third element appearing in either is a change somebody
 * should have to come here and justify.
 *
 * The table's SIZE is pinned too. Three rows is the whole set of primitives that are not one element, and a
 * fourth is a deliberate edit rather than a quiet one.
 */
const ELEMENT_COUNT_EXCEPTIONS: Readonly<Record<string, number>> = {
  [DELEGATES_ITS_ELEMENT]: 0,
  'app/ui/EmailField.tsx': 2,
  'app/ui/PasswordField.tsx': 2,
};

/**
 * Comments stripped BEFORE any tag is counted, because four of the five render sites discuss `<div>`,
 * `<body>` or `<main>` in prose - `AppShell`'s own header names the `<div>` its primitive renders. A scan that
 * counted those would report the layer broken while it was intact, which is the worse direction: it trains
 * the next person to loosen the scan.
 *
 * `(?<!:)` keeps `https://` out of the line-comment pattern. No render site holds a URL today; the guard is
 * there so that adding one is not a silent change in what this file measures.
 */
function withoutComments(code: string): string {
  return code.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');
}

/**
 * Every lowercase JSX tag name opened in `code`. A fragment (`<>`) has no name, a component tag starts
 * uppercase, and a spread or an expression is not a tag at all - none of them match, which is challenge F20's
 * requirement and is proved by a control below rather than asserted here.
 */
function lowercaseJsxTags(code: string): string[] {
  // `(?<![A-Za-z0-9_$])` IS LOAD-BEARING AND WAS FOUND BY MEASUREMENT. Without it, a TypeScript generic whose
  // type argument is a lowercase builtin matches: `Promise<string>` in `auth/pkce.ts` and `Set<string>` in
  // `auth/returnTarget.ts` were both reported as rendering a `<string>` element, which put two files that
  // contain no JSX at all into the derived set. A JSX tag's `<` is never preceded by an identifier character;
  // a generic's always is.
  return [...withoutComments(code).matchAll(/(?<![A-Za-z0-9_$])<([a-z][a-zA-Z0-9-]*)(?=[\s/>])/g)]
    .map((match) => match[1] as string);
}

/**
 * Every non-test source file in `src/`, at any depth.
 *
 * SCOPED TO THE WHOLE TREE, NOT TO `app/` AND `auth/` (phase 11, F2). It read two directories until that
 * review pointed out the obvious: §6e's intent is *every* screen, and a later domain directory - a
 * `src/profile/` - would have been invisible to the sweep AND absent from the pinned list, so the
 * cross-check would have passed by knowing about neither. `src/main.tsx` is already outside the old scope.
 */
async function productionFiles(): Promise<string[]> {
  const entries = await readdir(srcDir, { withFileTypes: true, recursive: true });
  const found = entries
    .filter((entry) => entry.isFile() && /\.tsx?$/.test(entry.name) && !/\.test\.tsx?$/.test(entry.name))
    .map((entry) => relative(srcDir, join(entry.parentPath, entry.name)));
  expect(found.length).toBeGreaterThan(0);
  return found.sort();
}

const read = (relativePath: string) => readFile(join(srcDir, relativePath), 'utf8');

// --- mounting ---------------------------------------------------------------------------------------------

let mounted: { root: Root; container: HTMLElement } | undefined;

/** Mounts one screen inside the providers it needs and nothing else: no `AppShell`, so the only markup in the
 *  container is the screen's own and `querySelectorAll` needs no scoping to get right (T37's lesson). */
async function mount(element: React.ReactElement, path = '/'): Promise<HTMLElement> {
  const container = document.createElement('div');
  document.body.append(container);
  const router = createMemoryRouter([{ path: '*', element }], { initialEntries: [path] });
  const root = createRoot(container);
  await act(async () => {
    root.render(
      <I18nextProvider i18n={createI18n()}>
        <RouterProvider router={router} />
      </I18nextProvider>,
    );
  });
  mounted = { root, container };
  return container;
}

const STATE = 'a-state-this-tab-stored';

beforeEach(() => {
  globalThis.sessionStorage.clear();
});

afterEach(async () => {
  if (mounted) {
    const { root, container } = mounted;
    await act(async () => root.unmount());
    container.remove();
    mounted = undefined;
  }
  globalThis.sessionStorage.clear();
  vi.restoreAllMocks();
});

// ==========================================================================================================
// AC2 / AC3 — the `Only`, in all three parts (§6e)
// ==========================================================================================================

describe('T173 AC2/AC3: only the primitives render bare elements', () => {
  it('pins both lists at the sizes the brief froze, with nothing in both (§6e(1))', () => {
    // SIX SINCE TASK 174. The number is pinned rather than derived on purpose - a list that counted itself
    // would accept any edit - so each task that adds a screen moves it here, deliberately, once.
    expect(RENDER_SITES, 'the render-site list changed size without the brief changing').toHaveLength(8);
    expect(PRIMITIVES, 'the primitive list changed size without the brief changing').toHaveLength(13);
    expect(new Set(PRIMITIVES).size).toBe(PRIMITIVES.length);
    expect(PRIMITIVES, 'the primitive list is not sorted, so a duplicate is easy to miss')
      .toEqual([...PRIMITIVES].sort());
    const both = PRIMITIVE_FILES.filter((file) => (RENDER_SITES as readonly string[]).includes(file));
    expect(both, 'a file is in both lists, so the sweep below cannot say which rule applies').toEqual([]);
  });

  it('finds exactly twelve files bearing a lowercase tag, in all of src/ — not twenty-one (§6e(2), I7, F2)', async () => {
    const files = await productionFiles();
    const bearing: string[] = [];
    for (const file of files) {
      if (lowercaseJsxTags(await read(file)).length > 0) bearing.push(file);
    }

    // (a) the derived set is the primitive list MINUS the one that delegates to react-router. A ninth
    // primitive, or a helper anywhere under app/ or auth/ that renders a bare element, fails here.
    const expectedBearers = PRIMITIVE_FILES.filter((file) => file !== DELEGATES_ITS_ELEMENT);
    expect(expectedBearers, 'the exception list grew: more than one primitive renders no element of its own')
      .toHaveLength(12);
    expect(bearing.sort(), 'a file outside app/ui/ renders a bare element, or a primitive stopped rendering one')
      .toEqual([...expectedBearers].sort());
    // and the exception is the one named, for the reason named: a component tag, not a bare element.
    const delegate = await read(DELEGATES_ITS_ELEMENT);
    expect(lowercaseJsxTags(delegate), `${DELEGATES_ITS_ELEMENT} now renders an element of its own`).toEqual([]);
    expect(delegate, `${DELEGATES_ITS_ELEMENT} no longer renders the router's Link`).toMatch(/<Link\s/);
    // (b) and no render site is in it. This is the half a `toHaveLength` on the derived set silently drops.
    expect(bearing.filter((file) => (RENDER_SITES as readonly string[]).includes(file)),
      'a render site renders a bare element').toEqual([]);
    // (c) and the union of THE TWO PINNED LISTS is the thirteen the brief names. Not the union involving
    // `bearing`: that is 12, because `TextLink` bears no tag. I7's trap has a second floor, and I fell
    // through it writing the case that disarms the first - thirteen is a statement about what the two lists
    // DECLARE, never about what the sweep FINDS.
    expect(new Set([...PRIMITIVE_FILES, ...RENDER_SITES]).size,
      'the union of the two pinned lists is no longer twenty-one').toBe(21);
    // and every file the sweep found is in one of them, which is the cross-check §6e(2) actually asks for.
    for (const file of bearing) {
      expect([...PRIMITIVE_FILES, ...RENDER_SITES], `${file} renders an element and is in neither list`)
        .toContain(file);
    }
  });

  it('names every file that imports the primitive barrel, tags or not (task 174)', async () => {
    // THE HOLE THE TAG SWEEP LEFT. Nothing here counts tags: a file that consumes the design system imports
    // it, and that import is visible whether the file renders `<div>` or nothing but primitives. Both
    // directions are asserted, because each catches a different mistake - a screen added without being
    // pinned, and a pin left behind after a screen is deleted.
    const candidates = (await productionFiles()).filter((file) => !file.startsWith('app/ui/'));
    const importers: string[] = [];
    for (const file of candidates) {
      // The barrel is reached as './ui' from `app/` and '../app/ui' from anywhere else. A deep import of a
      // single primitive would be a different shape, and is not matched on purpose: `index.ts` is the
      // published surface and a file that went around it is a violation this suite already pins elsewhere.
      // `(?:\.\.\/)+` RATHER THAN ONE `../`, FOUND BY MEASUREMENT IN TASK 43. The old pattern named exactly
      // two depths - `./ui` from `app/` and `../app/ui` from `auth/` - so the first screen one level deeper
      // (`features/account/`) imported the barrel as '../../app/ui' and this scan could not see it. It was
      // not a failing case: the equality passed while silently measuring one file fewer, which is the
      // direction that trains nobody. Any depth now matches.
      if (/from '(\.\/ui|(?:\.\.\/)+app\/ui)'/.test(withoutComments(await read(file)))) importers.push(file);
    }

    expect(importers.sort(), 'a file consumes the design system and is not a pinned render site')
      .toEqual([...RENDER_SITES].sort());

    // CONTROL. The assertion above is an equality between two lists, and an equality passes just as well
    // when the left side is empty and the right one is too - so it has to be shown that this derivation
    // finds anything at all, and that it is not simply reproducing the pinned list by accident.
    expect(importers.length, 'the import scan found nothing, so the equality above is vacuous')
      .toBeGreaterThan(0);
    expect(importers, 'the scan cannot see a screen that renders only primitives - the hole it exists to close')
      .toContain('app/HomeScreen.tsx');
  });

  it('renders no lowercase JSX tag in any of the seven render sites (§6e(3))', async () => {
    for (const site of RENDER_SITES) {
      const tags = lowercaseJsxTags(await read(site));
      expect(tags, `${site} renders ${tags.join(', ')} itself instead of a primitive`).toEqual([]);
    }
  });

  it('imports the primitives only from the barrel, in every render site', async () => {
    for (const site of RENDER_SITES) {
      const code = await read(site);
      const deep = [...code.matchAll(/from '[^']*\/ui\/([A-Za-z]+)'/g)].map((match) => match[1]);
      expect(deep, `${site} imports ${deep.join(', ')} by deep path rather than from the barrel`).toEqual([]);
    }
  });

  it('CONTROL: the sweep catches a planted bare element in a render site', () => {
    const planted = [
      "import { Heading, Screen } from '../app/ui';",
      'export function Planted() {',
      '  return (',
      '    <Screen>',
      '      <Heading>hello</Heading>',
      '      <div className="oops">a bare element</div>',
      '    </Screen>',
      '  );',
      '}',
    ].join('\n');
    expect(lowercaseJsxTags(planted)).toEqual(['div']);
  });

  it('CONTROL: the sweep flags no fragment, component tag, spread or expression (challenge F20)', () => {
    const innocent = [
      'export function Innocent({ rest }: { rest: object }) {',
      '  return (',
      '    <>',
      '      <Screen {...rest}>',
      '        <Heading>{`a template ${1 < 2 ? "yes" : "no"}`}</Heading>',
      '        <Text>{2 > 1 && "and"}</Text>',
      '      </Screen>',
      '    </>',
      '  );',
      '}',
    ].join('\n');
    expect(lowercaseJsxTags(innocent),
      'the scan flags a shape that is not a lowercase tag, so it would force a screen to be loosened')
      .toEqual([]);
  });

  it('CONTROL: the sweep reads code, not prose — a comment naming <div> is not a tag', () => {
    const commented = [
      '/** The shell is a <div>, and <main> belongs to Screen. */',
      '// A <section> would be wrong here.',
      'export const url = "https://example.test/x";',
      'export function Commented() { return <Surface>{null}</Surface>; }',
    ].join('\n');
    expect(lowercaseJsxTags(commented),
      'prose mentioning an element is counted as rendering one, which would report an intact layer as broken')
      .toEqual([]);
    expect(withoutComments(commented), 'a URL inside a string was eaten with the line comments')
      .toContain('https://example.test/x');
  });

  it('gives each primitive exactly one element to render, which is its whole job', async () => {
    for (const file of PRIMITIVE_FILES) {
      const tags = new Set(lowercaseJsxTags(await read(file)));
      const expected = ELEMENT_COUNT_EXCEPTIONS[file] ?? 1;
      expect([...tags], `${file} renders ${[...tags].join(', ') || 'nothing'} rather than `
        + `${expected === 0 ? 'delegating to a component' : `exactly ${expected} element(s)`}`)
        .toHaveLength(expected);
    }
    // The table is exact in both directions: every row names a real primitive, and its size is the pin that
    // makes a fourth exception deliberate. A row for a file that no longer exists would silently excuse
    // nothing while reading as though it excused something.
    expect(Object.keys(ELEMENT_COUNT_EXCEPTIONS).sort(), 'the exception table names a file that is not a primitive')
      .toEqual(Object.keys(ELEMENT_COUNT_EXCEPTIONS).filter((file) => PRIMITIVE_FILES.includes(file)).sort());
    expect(Object.keys(ELEMENT_COUNT_EXCEPTIONS), 'the one-element exception table changed size').toHaveLength(3);
    // And the two field rows are the LABELLED pair specifically, which is what makes the number two rather
    // than an arbitrary loosening: each renders its own `<label>` and its own control.
    for (const field of ['app/ui/EmailField.tsx', 'app/ui/PasswordField.tsx']) {
      const tags = lowercaseJsxTags(await read(field));
      expect([...new Set(tags)].sort(), `${field} is excused two elements but they are not a label and a control`)
        .toEqual(['input', 'label']);
      // AND IN THAT ORDER (phase 11, F6). `htmlFor`/`id` associates them whatever the order, so this is not
      // the association being re-checked: it is that the visible order matches the reading order, which is
      // what a sighted keyboard user tabbing down the form sees and what a label-after-control layout
      // quietly inverts. `lowercaseJsxTags` returns them in source order, so the first is the claim.
      expect(tags[0], `${field} renders its control before its label`).toBe('label');
    }
  });

  it('gives NO primitive a length cap of its own, because the attribute counts the wrong unit (T44)', async () => {
    // R1's bound is TWELVE TO 128 CODE POINTS and `maxLength` is counted in UTF-16 UNITS, so the attribute
    // cannot express the rule it looks like it expresses: `maxLength={128}` truncates a valid 128-code-point
    // passphrase of emoji to 64 characters, and it does so BEFORE any handler runs, so the advisory check
    // would then measure a value the visitor never typed. It would also BLOCK, and R1 makes the client's
    // check advisory - the server is the authority.
    //
    // EVERY PRIMITIVE, NOT THE TWO FIELDS THAT EXIST TODAY (phase 7 F3, phase 8 I3). This loop ran over a
    // hardcoded `['app/ui/EmailField.tsx', 'app/ui/PasswordField.tsx']` while the comment claimed it stopped
    // the attribute reaching "a field a later task adds" - which was false in the direction that matters,
    // because it told the next reader the hazard was handled. A `TextAreaField` or a `SearchField` would
    // have been invisible on the day it landed. `PRIMITIVE_FILES` is the list the file already pins BY SIZE,
    // so a new primitive joins this scan the moment it joins the layer, and there is no second list to keep.
    //
    // THE MATCH IS DELIBERATELY BROAD - the bare word, not `maxLength=`. A forbidden-token scan is the one
    // place where breadth is the safe direction: the narrow form would miss `{...{maxLength: 128}}`, a
    // `const cap = { maxLength }`, and a prop forwarded under another name, while the cost of breadth is a
    // loud failure on an identifier like `maxLengthHint` that nobody has written and that would want a
    // second look anyway. `withoutComments` is what lets the paragraph above exist in a primitive's own
    // header without tripping it (its control is above).
    for (const file of PRIMITIVE_FILES) {
      const code = withoutComments(await read(file));
      expect(code, `${file} caps its own length, in UTF-16 units rather than code points`)
        .not.toMatch(/maxLength/i);
      expect(code, `${file} floors its own length, in UTF-16 units rather than code points`)
        .not.toMatch(/minLength/i);
    }
  });

  it('CONTROL: a string literal holding a tag IS counted — the known limit of a text scan (F11)', () => {
    // Recorded as a limitation rather than fixed. The scan reads source text, not an AST, so a file holding
    // the STRING "<div>" would be reported as rendering one. No file in the package does, and the honest
    // move is to pin the behaviour so the next person meets it as a documented edge rather than a mystery -
    // narrowing the pattern further to exclude strings is how a sweep goes blind (see the generic control).
    expect(lowercaseJsxTags('export const sample = "<div>";'),
      'the scan silently started ignoring string contents, which is a wider hole than the one it closes')
      .toEqual(['div']);
  });

  it('CONTROL: a TypeScript generic is not a tag, in either direction', () => {
    // The hole this closes was real, not hypothetical: `Promise<string>` and `Set<string>` put two files with
    // no JSX into the derived set before the lookbehind was added.
    expect(lowercaseJsxTags('async function f(): Promise<string> { return new Set<string>().size + ""; }'))
      .toEqual([]);
    expect(lowercaseJsxTags('const node = <span>text</span>;'), 'the lookbehind now hides a real tag')
      .toEqual(['span']);
    expect(lowercaseJsxTags('return (\n  <div>\n    <p />\n  </div>\n);'))
      .toEqual(['div', 'p']);
  });
});

// ==========================================================================================================
// AC4 — className and style are absent from the TYPES; tsc is the instrument, this is the readable copy
// ==========================================================================================================

/** The type argument of `forwardRef<Ref, Props>(`, which is where a stylable prop would have to be declared. */
function propsType(code: string): string {
  const start = code.indexOf('forwardRef<');
  expect(start, 'the primitive no longer uses forwardRef, so this scan measures nothing').toBeGreaterThan(-1);
  const end = code.indexOf('>(', start);
  expect(end).toBeGreaterThan(start);
  return code.slice(start, end);
}

describe('T173 AC4: no primitive accepts a class or a style', () => {
  it('declares neither className nor style in any of the eight prop types', async () => {
    for (const file of PRIMITIVE_FILES) {
      const props = propsType(await read(file));
      expect(props, `${file} accepts a className, so a screen can style itself`).not.toMatch(/className/);
      expect(props, `${file} accepts a style, so a screen can style itself`).not.toMatch(/\bstyle\b/);
    }
  });

  it('keeps every className literal on one line (phase 7 F6, phase 8 I5)', async () => {
    for (const file of PRIMITIVE_FILES) {
      const code = await read(file);
      const literals = [...code.matchAll(/className=(?:"([^"]*)"|\{`([^`]*)`\})/g)]
        .map((match) => match[1] ?? match[2]);
      expect(literals, `${file} sets no className, so the design system is not applied there`).not.toEqual([]);
      for (const literal of literals) {
        // A wrapped literal puts a real newline in the DOM's class attribute. The R30 pin cannot see it - the
        // newline lands identically in all five account states, so sameness and non-emptiness both hold - and
        // no other criterion covered it. RD15 survived the whole suite for exactly this long.
        expect(literal, `${file} wraps a className across lines, putting a newline in the class attribute`)
          .not.toMatch(/[\n\r]/);
      }
    }
  });

  it('forbids a hardcoded inline style in any primitive body (phase 11, F6)', async () => {
    // Symmetric to the newline check, and not covered by the prop-type scan: a primitive could write
    // `style={{ color: 'red' }}` without ACCEPTING a style prop, and every type assertion above would pass.
    // The R30 pin would see it only on the five identity-failure screens.
    for (const file of PRIMITIVE_FILES) {
      const code = withoutComments(await read(file));
      expect(code, `${file} hardcodes an inline style, which is styling outside the token system`)
        .not.toMatch(/\bstyle\s*=\s*\{/);
    }
  });

  it('CONTROL: a planted className prop is caught, in the type and not merely in the body', () => {
    const planted = [
      "import { forwardRef, type ReactNode } from 'react';",
      'export const Loose = forwardRef<HTMLDivElement, { children: ReactNode; className?: string }>(',
      '  function Loose({ children, className }, ref) {',
      '    return <div ref={ref} className={className}>{children}</div>;',
      '  },',
      ');',
    ].join('\n');
    expect(propsType(planted)).toMatch(/className/);
  });

  it('names the case that actually enforces this, so the citation cannot rot (phase 7 F5)', async () => {
    const gate = await read('api/lintRejectsHandWrittenBackendFetch.test.ts');
    // That case runs a real `pnpm run build` over a copy of src/ and asserts today's sources compile. It is
    // what fails when a screen passes `className` to a primitive - measured in phase 7 as RD16, which breaks
    // tsc in two places. This file's scans are the readable statement of the rule; that case is the gate.
    expect(gate, 'the end-to-end build case was renamed or removed, so AC4 has no gate behind its scans')
      .toContain('stops build at lint, so tsc and Vite never run on a violation');
    expect(gate, 'the build case no longer asserts that today\'s sources build cleanly')
      .toContain('const clean = await build();');
  });
});

// ==========================================================================================================
// AC1 — each primitive carries the design system it exists to apply (phase 11, F1 and F4)
// ==========================================================================================================

/**
 * WHAT THE SUITE DID NOT MEASURE UNTIL PHASE 11 ASKED. Every case before this one checked tags, attributes,
 * refs, the token block and the emitted CSS - and not one of them checked that a primitive carries the
 * utilities it exists to carry. Kimi's F1: remove `bg-accent` from `ActionButton` and the sign-in screen
 * still renders a button, AC9 still finds the token, AC14 still finds other utilities in the build, and the
 * R30 pin only asks that class attributes be identical across account states, never WHICH classes.
 *
 * The required set per primitive is deliberately NOT the exact class string. Spacing and radius are phase 5's
 * to choose and a later task's to change; what this task owes is that the COLOUR TOKENS, the TYPE STEPS and
 * the FOCUS INDICATOR are applied where they belong. Pinning the whole literal would turn every padding
 * adjustment into a test edit, which is how a pin gets deleted rather than updated.
 */
const REQUIRED_UTILITIES: Record<(typeof PRIMITIVES)[number], readonly string[]> = {
  Surface: ['min-h-dvh', 'bg-surface', 'text-text'],
  Screen: ['mx-auto', 'max-w-prose'],
  // TASK 42. The banner owns no colour token and no type step - it holds a control that carries its own -
  // so what it owes is the one visual claim its own header makes: the chrome and the screen below it share
  // ONE content column. These two utilities are that claim, and `Screen`'s row above is the other half of
  // it. An empty row here would have been the easy answer and would have measured nothing.
  Banner: ['mx-auto', 'max-w-prose'],
  Heading: ['text-step-1', 'text-text'],
  Text: ['text-step-0', 'text-text'],
  ActionButton: ['bg-accent', 'text-text-inverse', 'text-step-0'],
  TextLink: ['text-accent', 'underline'],
  StatusRegion: ['text-step--1', 'text-muted'],
  FocusHolder: [],
  // TASK 43's four. The fields own a type step, the body text colour and the surface they sit on - an
  // `<input>` with no background inherits the browser's, which is white in a dark theme. `SubmitButton`
  // owes what `ActionButton` owes, because it IS that button with `type="submit"`: if the two rows ever
  // disagree, two controls that look alike have stopped being alike. `Form` carries layout only, and an
  // empty row says so rather than inventing a token for it to own.
  EmailField: ['text-step-0', 'text-text', 'bg-surface'],
  PasswordField: ['text-step-0', 'text-text', 'bg-surface'],
  SubmitButton: ['bg-accent', 'text-text-inverse', 'text-step-0'],
  Form: [],
};

/**
 * The six a keyboard user has to see a ring on: the two controls, the link, the focus holder, and - added by
 * task 43 - the two text fields.
 *
 * AN INSTRUMENT THE FREEZE DID NOT NAME. D11 listed eleven rows to grow and this was not among them; it was
 * found by asking what an `<input>` owes that a `<div>` does not. A field is focusable by default, so it is
 * exactly the kind of element whose ring is easy to leave to the browser's - and the browser's is what
 * `focus-visible:outline-*` replaces. Three of the six rows here are task 43's.
 */
const FOCUSABLE = ['ActionButton', 'EmailField', 'FocusHolder', 'PasswordField', 'SubmitButton',
  'TextLink'] as const;

/**
 * A catalogue sentence as it would appear if a primitive SPELLED it: between quotes, or as JSX text. Never as
 * part of a longer identifier - see the control beside the case that uses this.
 */
function spelledOut(sentence: string): RegExp {
  const escaped = sentence.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`(?<![A-Za-z0-9_$])${escaped}(?![A-Za-z0-9_$])`);
}

function classesOf(code: string): string[] {
  return [...withoutComments(code).matchAll(/className=(?:"([^"]*)"|\{`([^`]*)`\})/g)]
    .flatMap((match) => (match[1] ?? match[2] ?? '').split(/\s+/))
    .filter(Boolean);
}

describe('T173 AC1: every primitive applies the tokens it exists to apply', () => {
  it.each(PRIMITIVES)('gives %s the colour and type utilities it owns', async (name) => {
    const classes = classesOf(await read(`app/ui/${name}.tsx`));
    for (const utility of REQUIRED_UTILITIES[name]) {
      expect(classes, `${name} no longer applies ${utility}, so the design system is not reaching it`)
        .toContain(utility);
    }
  });

  it('keeps the two buttons wearing ONE appearance, which is a pair claim and not two rows (task 43)', async () => {
    // `SubmitButton.tsx`'s header says both files are "pinned to the same token list by the design-system
    // suite, which is the pair pin task 42 learned to ask for: two literals that happen to agree are not the
    // same claim as two that must". THAT WAS FALSE WHEN IT WAS WRITTEN (phase 7, F3; phase 8, I8): the table
    // above holds two INDEPENDENT rows that happen to match, which is `Banner`'s `max-w-prose` coincidence
    // exactly. This case is what makes the sentence true. Change `ActionButton`'s accent alone and it fails.
    expect(REQUIRED_UTILITIES.SubmitButton, 'the two buttons no longer owe the same tokens')
      .toEqual(REQUIRED_UTILITIES.ActionButton);
    const action = classesOf(await read('app/ui/ActionButton.tsx'));
    const submit = classesOf(await read('app/ui/SubmitButton.tsx'));
    // And the FILES agree, not only the table: a row both files satisfy is still two literals until the
    // literals are compared. Only the shared appearance is compared - layout is each file's own, and
    // `ActionButton` has no form to sit in.
    for (const utility of ['bg-accent', 'text-text-inverse', 'text-step-0', 'rounded-md', 'px-4', 'py-2',
      'font-medium', 'focus-visible:outline-2', 'focus-visible:outline-focus']) {
      expect(action, `ActionButton lost ${utility}`).toContain(utility);
      expect(submit, `SubmitButton and ActionButton disagree about ${utility}`).toContain(utility);
    }
  });

  it.each(FOCUSABLE)('gives %s a visible focus ring (L11, WCAG 2.4.7)', async (name) => {
    const classes = classesOf(await read(`app/ui/${name}.tsx`));
    // Without these a keyboard user has no indicator on the application's only control and only link - and
    // `--color-focus` is the one token whose contrast threshold exists for exactly this (§6d, 1.4.11).
    expect(classes, `${name} has no focus ring width`).toContain('focus-visible:outline-2');
    expect(classes, `${name} has no focus ring colour, so the ring is the browser default`)
      .toContain('focus-visible:outline-focus');
  });

  it('CONTROL: the utility check is not vacuous — a missing class is caught, a present one is seen', () => {
    const stripped = 'export const X = () => <button className="self-start px-4 py-2">x</button>;';
    expect(classesOf(stripped), 'the accent was removed and the check did not notice')
      .not.toContain('bg-accent');
    expect(classesOf(stripped)).toEqual(['self-start', 'px-4', 'py-2']);
    expect(classesOf('<div className={`a ${x} b`}>'), 'a template literal hides its static utilities')
      .toContain('a');
  });
});

// ==========================================================================================================
// AC5 — the rendered structure each primitive is responsible for (L11)
// ==========================================================================================================

describe('T173 AC5: one landmark, one level-one heading, and announced regions', () => {
  it('renders the signed-out screen as one main holding one h1, a paragraph and a router link', async () => {
    const container = await mount(<SignedOutScreen />);
    expect(container.querySelectorAll('main')).toHaveLength(1);
    expect(container.querySelectorAll('h1')).toHaveLength(1);
    expect(container.querySelectorAll('p')).toHaveLength(1);
    const link = container.querySelector('a');
    expect(link?.getAttribute('href')).toBe('/sign-in');
    expect(container.querySelector('button'), 'the way back is a button, not a link').toBeNull();
  });

  it('renders the sign-in screen with a described, idle, non-busy control and one live region', async () => {
    const container = await mount(<SignInScreen />);
    expect(container.querySelectorAll('main')).toHaveLength(1);
    expect(container.querySelectorAll('h1')).toHaveLength(1);
    expect(container.querySelector('p')?.getAttribute('id')).toBe('sign-in-explanation');

    const button = container.querySelector('button');
    expect(button?.getAttribute('type'), 'a button with no type submits, and a later form would navigate')
      .toBe('button');
    expect(button?.getAttribute('aria-describedby')).toBe('sign-in-explanation');
    expect(button?.getAttribute('aria-disabled')).toBe('false');
    expect(button?.getAttribute('aria-busy')).toBe('false');
    // `aria-disabled`, never `disabled`: a focused element that becomes disabled drops the keyboard user to
    // <body>, and this is the only control on the screen. `aria-disabled` CONTAINS the substring, so the
    // lookahead is what keeps the correct code from tripping the scan (challenge F18).
    expect(button?.hasAttribute('disabled'), 'the control uses disabled rather than aria-disabled').toBe(false);

    const region = container.querySelector('[role="status"]');
    expect(container.querySelectorAll('[role="status"]')).toHaveLength(1);
    expect(region?.getAttribute('aria-live')).toBe('polite');
    expect(region?.textContent, 'the region carries text before anything has happened').toBe('');
  });

  it('renders the callback screen as one main, one h1 and one announced region', async () => {
    globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.state, STATE);
    globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.nonce, 'a-nonce');
    globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.codeVerifier, 'a-verifier');
    const container = await mount(<CallbackScreen />, `/callback?state=${STATE}&code=a-code`);

    expect(container.querySelectorAll('main')).toHaveLength(1);
    expect(container.querySelectorAll('h1')).toHaveLength(1);
    const region = container.querySelector('[role="status"]');
    expect(region, 'the transient screen has no live region, so nothing it says is announced').not.toBeNull();
    expect(region?.getAttribute('aria-live')).toBe('polite');
  });

  it('gives the notice a programmatically focusable holder and an always-present region (L11)', async () => {
    const notice = createRateLimitNotice();
    const container = await mount(<RateLimitNotice notice={notice} />);

    const holder = container.querySelector('[tabindex]');
    expect(holder?.getAttribute('tabindex'), 'the focus holder is tabbable, or is not there at all').toBe('-1');
    const region = container.querySelector('[role="status"]');
    expect(region, 'the live region is conditional, so a screen reader announces nothing when text arrives')
      .not.toBeNull();
    expect(region?.getAttribute('aria-live')).toBe('polite');
    expect(region?.textContent).toBe('');
    expect(container.querySelector('button'), 'a dismiss button before anything was shown').toBeNull();
  });
});

// ==========================================================================================================
// AC6 — the focus dance the primitives have to keep working (challenge F9)
// ==========================================================================================================

/**
 * THIS SECTION DELIBERATELY DOES NOT RE-RENDER THE 429 FLOW. `RateLimitNoticeState` exposes only `isShown`,
 * `subscribe` and `dismiss`, so showing the notice means driving a real rate-limited response through
 * `createAuthApi` - which `rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx` already does, and which
 * already asserts `document.activeElement` is the holder and is not `<body>`. Copying that harness here would
 * produce a second case measuring the same thing with more machinery; phase 7's F5 recorded the rule - an
 * existing passing instrument is stronger evidence than a criterion I write to replace it.
 *
 * So this section measures the MECHANISM that case depends on and nothing else does: every primitive forwards
 * its ref to the real element. That is precisely what RD08 and RD09 break, and until now both died only
 * outside this task's files.
 */
describe('T173 AC6: every primitive forwards its ref to the element it renders', () => {
  it('hands back the real DOM node for all thirteen', async () => {
    const refs = Object.fromEntries(PRIMITIVES.map((name) => [name, createRef<HTMLElement>()])) as
      Record<(typeof PRIMITIVES)[number], React.RefObject<HTMLElement | null>>;

    await mount(
      <>
        <Surface ref={refs.Surface as React.Ref<HTMLDivElement>}>surface</Surface>
        <Screen ref={refs.Screen}>screen</Screen>
        <Heading ref={refs.Heading as React.Ref<HTMLHeadingElement>}>heading</Heading>
        <Text ref={refs.Text as React.Ref<HTMLParagraphElement>}>text</Text>
        <ActionButton ref={refs.ActionButton as React.Ref<HTMLButtonElement>}>button</ActionButton>
        <TextLink ref={refs.TextLink as React.Ref<HTMLAnchorElement>} to="/sign-in">link</TextLink>
        <StatusRegion ref={refs.StatusRegion as React.Ref<HTMLDivElement>}>status</StatusRegion>
        <FocusHolder ref={refs.FocusHolder as React.Ref<HTMLDivElement>}>holder</FocusHolder>
        <Banner ref={refs.Banner as React.Ref<HTMLElement>}>banner</Banner>
        <Form ref={refs.Form as React.Ref<HTMLFormElement>} onSubmit={() => undefined}>
          <EmailField
            ref={refs.EmailField as React.Ref<HTMLInputElement>}
            label="email"
            value=""
            onChange={() => undefined}
          />
          <PasswordField
            ref={refs.PasswordField as React.Ref<HTMLInputElement>}
            label="password"
            value=""
            onChange={() => undefined}
          />
          <SubmitButton ref={refs.SubmitButton as React.Ref<HTMLButtonElement>}>submit</SubmitButton>
        </Form>
      </>,
    );

    const expected: Record<(typeof PRIMITIVES)[number], string> = {
      ActionButton: 'BUTTON',
      // TASK 42. `HEADER` is the whole point of the ninth primitive: a `<div>` would have been one fewer
      // file and no landmark at all.
      Banner: 'HEADER',
      FocusHolder: 'DIV',
      Heading: 'H1',
      Screen: 'MAIN',
      StatusRegion: 'DIV',
      Surface: 'DIV',
      Text: 'P',
      TextLink: 'A',
      // TASK 43. The two fields forward to the CONTROL, never to the `<label>` and never to a wrapper -
      // which is the only answer that lets a caller focus a field, and is why they render a fragment
      // instead of a `<div>` around the pair. `Form` forwards to the `<form>` itself.
      EmailField: 'INPUT',
      Form: 'FORM',
      PasswordField: 'INPUT',
      SubmitButton: 'BUTTON',
    };
    for (const name of PRIMITIVES) {
      // A primitive that drops `forwardRef` leaves this null, `?.focus()` becomes a no-op, and a keyboard
      // user is dropped to <body> with no test failing unless one asserts activeElement (L11). RD08 and RD09.
      expect(refs[name].current, `${name} did not forward its ref, so a caller holds null`).not.toBeNull();
      expect(refs[name].current?.tagName, `${name} forwarded a ref to the wrong element`).toBe(expected[name]);
    }
  });

  it('names the case that measures the control\'s busy state, so the citation cannot rot (F7)', async () => {
    // F7 asked this suite to click the button and assert `aria-disabled`/`aria-busy` flip to "true".
    // Declined for the reason phase 7's F5 recorded: `signInStartsAuthorizationCodeFlowWithPkce…` already
    // asserts both, on both edges, including the return to "false" after a failure. A second copy here
    // would measure the same claim through a click that also reaches PKCE and `location.assign`, and would
    // be a worse instrument for being noisier. What this task owes is that the citation cannot rot.
    const owner = await read('auth/signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx');
    expect(owner, 'the case that measures the busy state was renamed or removed')
      .toContain("expect(button(container).getAttribute('aria-disabled')).toBe('true');");
    expect(owner, 'that case no longer asserts the busy flag alongside the disabled one')
      .toContain("expect(button(container).getAttribute('aria-busy')).toBe('true');");
  });

  it('names the case that measures the focus move itself, so the citation cannot rot', async () => {
    const owner = await read('api/rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx');
    expect(owner, 'the focus case was renamed, so AC6 has no behavioural instrument behind it')
      .toContain('keeps keyboard focus on the notice when the dismiss button is activated');
    expect(owner, 'that case no longer asserts focus stayed off <body>')
      .toContain('expect(document.activeElement).not.toBe(document.body);');
  });
});

// ==========================================================================================================
// AC7 / AC8 — the copy is unchanged, and still comes from the catalogue (challenge F17)
// ==========================================================================================================

describe('T173 AC7/AC8: the design system changed no words', () => {
  it('renders the signed-out screen\'s three catalogue strings, as literal English', async () => {
    const container = await mount(<SignedOutScreen />);
    const text = container.textContent ?? '';
    for (const value of [en.signedOut.heading, en.signedOut.explanation, en.signedOut.action]) {
      expect(text).toContain(value);
    }
    // ALSO as literals, because `t()` returns the KEY when an entry is missing: a catalogue emptied by a bad
    // merge would satisfy the loop above against its own emptiness.
    expect(text).toContain('Signed out');
    expect(text).toContain('Sign in again whenever you are ready.');
    expect(text, 'a key leaked into the DOM, so the catalogue lookup failed').not.toMatch(/signedOut\./);
  });

  it('renders the sign-in screen\'s copy, as literal English', async () => {
    const text = (await mount(<SignInScreen />)).textContent ?? '';
    expect(text).toContain(en.signIn.heading);
    expect(text).toContain(en.signIn.explanation);
    expect(text).toContain(en.signIn.action);
    expect(text).toContain('Continue to the secure sign-in page to enter your details.');
    expect(text, 'a key leaked into the DOM').not.toMatch(/signIn\./);
  });

  it('renders the callback screen\'s copy, as literal English (phase 11, F5)', async () => {
    globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.state, STATE);
    globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.nonce, 'a-nonce');
    globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.codeVerifier, 'a-verifier');
    const text = (await mount(<CallbackScreen />, `/callback?state=${STATE}&code=a-code`)).textContent ?? '';

    expect(text).toContain(en.callback.heading);
    expect(text).toContain(en.callback.working);
    expect(text).toContain('Finishing sign-in');
    expect(text, 'a key leaked into the DOM, so the catalogue lookup failed').not.toMatch(/callback\./);
    // It says what is happening and never that sign-in has succeeded: the flow is not finished until the
    // exchange, which this file stubs to a promise that never settles.
    expect(text.toLowerCase(), 'the transient screen claims success it cannot know').not.toContain('success');
  });

  it('keeps the catalogue\'s key set exactly as it was (AC11)', () => {
    const paths: string[] = [];
    for (const [group, entries] of Object.entries(en)) {
      for (const key of Object.keys(entries as Record<string, string>)) paths.push(`${group}.${key}`);
    }
    expect(paths.sort()).toEqual([
      'callback.heading',
      'callback.working',
      'rateLimit.dismiss',
      'rateLimit.message',
      'signIn.action',
      'signIn.discarded',
      'signIn.explanation',
      'signIn.failed',
      'signIn.heading',
      'signIn.sessionEnded',
      'signIn.starting',
      'signedOut.action',
      'signedOut.explanation',
      'signedOut.heading',
      // TASK 174's two, and they are the first entries this pin has ever ADMITTED. Its claim is that T173
      // changed no words, and that claim is unharmed: a later task adding its own screen's copy is not the
      // design system rewriting anyone's. What the pin still forbids is the thing it was built for - a
      // primitive, or a restyling, quietly editing or dropping a string. Sorted, so 'home.*' lands here
      // rather than in route order.
      'home.explanation',
      'home.heading',
      // TASK 43's six, on the same terms as task 174's two and task 42's one: a screen's own words, not a
      // primitive's. The pin's claim - that T173 changed no words - is untouched.
      'register.emailLabel',
      'register.heading',
      'register.passwordLabel',
      'register.submit',
      'register.submitting',
      'register.success',
      // TASK 42's one, admitted for the same reason and on the same terms: it is the authenticated chrome's
      // own word, not a primitive's. `Banner` carries no copy - the case below this one is what says so.
      'shell.signOut',
    ].sort());
  });

  it('holds no user-facing copy in any primitive (L11)', async () => {
    const copy = Object.values(en).flatMap((group) => Object.values(group as Record<string, string>));
    for (const file of PRIMITIVE_FILES) {
      const code = withoutComments(await read(file));
      for (const sentence of copy) {
        expect(code, `${file} spells "${sentence}" instead of taking it as children`)
          .not.toMatch(spelledOut(sentence));
      }
      expect(code, `${file} reads the catalogue, so a primitive decides what a screen says`)
        .not.toMatch(/useTranslation|\bt\(/);
    }
  });

  it('CONTROL: the copy scan reads words, not identifiers that contain them (task 43)', () => {
    // CORRECTED BY MEASUREMENT, NOT BY REVIEW. The scan was a plain `toContain`, and task 43 added the
    // first catalogue string that is a single WORD - 'Password'. `PasswordField.tsx` was then reported as
    // spelling its own screen's copy, because the word is a substring of the component's name. The claim
    // was never about substrings: it is that a primitive must not SPELL a sentence it should take as
    // children, and a sentence is spelled in a string literal or as JSX text. Both directions are pinned
    // here, because loosening a scan is exactly the edit that needs a control beside it.
    expect("export const PasswordField = forwardRef(function PasswordField() {})")
      .not.toMatch(spelledOut('Password'));
    expect('<label>Password</label>', 'JSX text is how copy is spelled, and the scan must see it')
      .toMatch(spelledOut('Password'));
    expect("const label = 'Password';", 'a string literal is the other way, and the scan must see it')
      .toMatch(spelledOut('Password'));
    // And a multi-word sentence is unaffected by the change: it was caught before and still is.
    expect('<p>Check your email to finish creating your account.</p>')
      .toMatch(spelledOut('Check your email to finish creating your account.'));
  });
});

// ==========================================================================================================
// AC10 — rendering a screen touches no storage and no network
// ==========================================================================================================

describe('T173 AC10: the primitive layer added no side effect', () => {
  it.each([
    ['SignedOutScreen', <SignedOutScreen />],
    ['SignInScreen', <SignInScreen />],
    ['RateLimitNotice', <RateLimitNotice notice={createRateLimitNotice()} />],
  ] as const)('writes no storage and makes no request while %s renders (phase 11, F12)', async (_name, element) => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    const fetchSpy = vi.spyOn(globalThis, 'fetch');

    await mount(element);

    expect(setItem).not.toHaveBeenCalled();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  // `CallbackScreen` is deliberately absent: its whole job is an effect that reads storage and calls the
  // token endpoint, so including it would mean asserting it does not do the thing it exists to do.

  it('declares no storage key and no endpoint in any primitive', async () => {
    for (const file of PRIMITIVE_FILES) {
      const code = withoutComments(await read(file));
      for (const forbidden of [/sessionStorage/, /localStorage/, /\bfetch\s*\(/, /themistra\./, /https?:\/\//]) {
        expect(code, `${file} reaches for ${forbidden.source}`).not.toMatch(forbidden);
      }
    }
  });
});

// ==========================================================================================================
// AC9 — the contrast claim, computed from the stylesheet's own declarations (§6d)
// ==========================================================================================================

/** sRGB hex to WCAG relative luminance (WCAG 2.1 §1.4.3's definition, not a perceptual approximation). */
function luminance(hex: string): number {
  const channels = [1, 3, 5].map((at) => Number.parseInt(hex.slice(at, at + 2), 16) / 255);
  const linear = channels.map((value) =>
    value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4);
  return 0.2126 * linear[0]! + 0.7152 * linear[1]! + 0.0722 * linear[2]!;
}

function contrast(a: string, b: string): number {
  const [high, low] = [luminance(a), luminance(b)].sort((x, y) => y - x) as [number, number];
  return (high + 0.05) / (low + 0.05);
}

async function themeColours(): Promise<Record<string, string>> {
  const css = await read('app/styles.css');
  const block = css.slice(css.indexOf('@theme'));
  const found: Record<string, string> = {};
  for (const match of block.matchAll(/--color-([a-z-]+):\s*(#[0-9a-fA-F]{6});/g)) {
    found[match[1]!] = match[2]!;
  }
  return found;
}

describe('T173 AC9: the three frozen pairs clear WCAG AA, computed not asserted', () => {
  const PAIRS = [
    { fore: 'text', back: 'surface', least: 4.5, why: 'body text, WCAG AA 1.4.3' },
    { fore: 'text-inverse', back: 'accent', least: 4.5, why: "the one control's label" },
    { fore: 'focus', back: 'surface', least: 3, why: 'focus indicator, WCAG 2.1 1.4.11 non-text contrast' },
  ] as const;

  it('declares the six colour tokens the brief froze, and no more', async () => {
    expect(Object.keys(await themeColours()).sort())
      .toEqual(['accent', 'focus', 'muted', 'surface', 'text', 'text-inverse']);
  });

  it.each(PAIRS)('clears $least:1 for --color-$fore on --color-$back ($why)', async ({ fore, back, least }) => {
    const colours = await themeColours();
    const ratio = contrast(colours[fore]!, colours[back]!);
    // THE COMPUTED NUMBER GOES IN THE MESSAGE (phase 7 F1). Four comments in styles.css stated ratios that
    // were wrong while every threshold held, so an assertion that merely passed told nobody.
    expect(ratio, `--color-${fore} (${colours[fore]}) on --color-${back} (${colours[back]}) is `
      + `${ratio.toFixed(2)}:1, under the ${least}:1 this pair needs`).toBeGreaterThanOrEqual(least);
  });

  it('states each ratio correctly in the stylesheet\'s own comments (phase 7 F1)', async () => {
    const colours = await themeColours();
    const css = await read('app/styles.css');
    for (const { fore, back } of PAIRS) {
      const stated = contrast(colours[fore]!, colours[back]!).toFixed(1);
      expect(css, `the comment for --color-${fore} on --color-${back} does not state the computed `
        + `${stated}:1, so the prose disagrees with the arithmetic`).toContain(`${stated}:1`);
    }
  });

  it('CONTROL: a pair just under 4.5:1 is reported as failing', () => {
    // #767676 on white is the canonical boundary case: 4.54:1 passes, #777777 is 4.48:1 and does not.
    expect(contrast('#777777', '#ffffff')).toBeLessThan(4.5);
    expect(contrast('#767676', '#ffffff')).toBeGreaterThanOrEqual(4.5);
    expect(contrast('#000000', '#ffffff')).toBeCloseTo(21, 1);
    expect(contrast('#ffffff', '#ffffff')).toBeCloseTo(1, 5);
  });
});

// ==========================================================================================================
// AC14 / AC15 — the build emits the stylesheet, and the two dependencies are pinned exactly
// ==========================================================================================================

describe('T173 AC14/AC15: the stylesheet reaches the built output', () => {
  it('emits a non-empty CSS asset, links it from index.html, and compiles the tokens into it', async () => {
    const outDir = await mkdtemp(join(tmpdir(), 'checky-t173-css-'));
    try {
      await build({
        root: frontendDir,
        configFile: join(frontendDir, 'vite.config.ts'),
        logLevel: 'silent',
        build: { outDir, emptyOutDir: true },
      });

      const assets = await readdir(join(outDir, 'assets'));
      const stylesheets = assets.filter((name) => name.endsWith('.css'));
      expect(stylesheets, 'the build emitted no CSS asset, so no screen is styled in production')
        .toHaveLength(1);

      const css = await readFile(join(outDir, 'assets', stylesheets[0]!), 'utf8');
      expect(css.length, 'the emitted stylesheet is empty').toBeGreaterThan(1000);
      // The utilities and the tokens, both: a typo'd Tailwind class emits NOTHING and fails silently, which
      // jsdom can never see. This is the only place in the repository where that is measurable.
      for (const token of ['--color-surface', '--color-accent', '--text-step-0']) {
        expect(css, `the @theme token ${token} did not reach the build`).toContain(token);
      }
      // EVERY required utility, not a sample: a typo'd Tailwind class emits NOTHING and fails silently, and
      // jsdom can never see it. Backslashes are stripped because the emitted selector escapes the colon -
      // `.focus-visible\:outline-2` - so a literal match would fail on exactly the classes F4 cares about.
      const flat = css.replaceAll('\\', '');
      const required = [...new Set([
        ...Object.values(REQUIRED_UTILITIES).flat(),
        'focus-visible:outline-2',
        'focus-visible:outline-focus',
      ])];
      expect(required.length, 'the required-utility table emptied, so this case asserts nothing')
        .toBeGreaterThan(8);
      for (const utility of required) {
        expect(flat, `the utility ${utility} emitted no rule, so it is a typo rather than a class`)
          .toContain(utility);
      }

      const html = await readFile(join(outDir, 'index.html'), 'utf8');
      // `rel="stylesheet"` IS PART OF THE CLAIM (phase 11, F3). `<link href="...css">` with no rel is not a
      // stylesheet to a browser; it is an unused resource hint. The old pattern accepted it, so the case
      // could have passed on a build that shipped the CSS and never applied it.
      const links = [...html.matchAll(/<link\b[^>]*>/g)].map((match) => match[0]);
      const sheets = links.filter((tag) => /\brel="stylesheet"/.test(tag) && /\.css"/.test(tag));
      expect(sheets, 'index.html has no rel="stylesheet" link, so the CSS is emitted but never applied')
        .toHaveLength(1);
      expect(sheets[0], 'the stylesheet link does not point at the emitted asset')
        .toContain(`href="/app/assets/${stylesheets[0]}"`);
    } finally {
      await rm(outDir, { recursive: true, force: true });
    }
  }, 120_000);

  it('pins tailwindcss and @tailwindcss/vite exactly, as devDependencies (AC15, challenge F13)', async () => {
    const pkg = JSON.parse(await readFile(join(frontendDir, 'package.json'), 'utf8')) as {
      dependencies?: Record<string, string>;
      devDependencies: Record<string, string>;
    };
    for (const name of ['tailwindcss', '@tailwindcss/vite']) {
      expect(pkg.devDependencies[name], `${name} is not a devDependency`).toMatch(/^\d+\.\d+\.\d+$/);
      expect(pkg.dependencies ?? {}, `${name} ships as a runtime dependency`).not.toHaveProperty(name);
    }
    expect(pkg.devDependencies.tailwindcss, 'the plugin and the engine are on different versions')
      .toBe(pkg.devDependencies['@tailwindcss/vite']);
  });

  it('keeps one stylesheet in the whole of src/ (AC12)', async () => {
    const entries = await readdir(srcDir, { withFileTypes: true, recursive: true });
    const sheets = entries
      .filter((entry) => entry.isFile() && /\.(css|scss|sass|less)$/.test(entry.name))
      .map((entry) => relative(srcDir, join(entry.parentPath, entry.name)));
    expect(sheets).toEqual(['app/styles.css']);
  });
});
