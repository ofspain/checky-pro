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
import { ActionButton, FocusHolder, Heading, Screen, StatusRegion, Surface, Text, TextLink } from './ui';

// The exchange is stubbed to a promise that never settles, so `CallbackScreen` STAYS on its working state and
// its own DOM can be read. Without this it discards and navigates, and the structure under test is gone
// before the assertion runs.
vi.mock('../auth/codeExchange', () => ({
  exchangeAuthorizationCode: () => new Promise(() => {}),
}));

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// This file is frontend/src/app/<name>; `import.meta.url` is not a file: URL under jsdom (T41 phase 10).
const appDir = dirname(expect.getState().testPath as string);
const srcDir = dirname(appDir);
const frontendDir = dirname(srcDir);

/** §6e(1), first pinned list: the five files that render a screen. A sixth must be added deliberately. */
const RENDER_SITES = [
  'app/AppShell.tsx',
  'app/RateLimitNotice.tsx',
  'auth/CallbackScreen.tsx',
  'auth/SignInScreen.tsx',
  'auth/SignedOutScreen.tsx',
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
  'FocusHolder',
  'Heading',
  'Screen',
  'StatusRegion',
  'Surface',
  'Text',
  'TextLink',
] as const;

const PRIMITIVE_FILES = PRIMITIVES.map((name) => `app/ui/${name}.tsx`);

/** The one primitive that delegates its element to react-router instead of rendering one (see above). */
const DELEGATES_ITS_ELEMENT = 'app/ui/TextLink.tsx';

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

async function productionFilesUnder(...dirs: string[]): Promise<string[]> {
  const found: string[] = [];
  for (const dir of dirs) {
    const entries = await readdir(join(srcDir, dir), { withFileTypes: true, recursive: true });
    for (const entry of entries) {
      if (!entry.isFile() || !/\.tsx?$/.test(entry.name) || /\.test\.tsx?$/.test(entry.name)) continue;
      found.push(relative(srcDir, join(entry.parentPath, entry.name)));
    }
  }
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
    expect(RENDER_SITES, 'the render-site list changed size without the brief changing').toHaveLength(5);
    expect(PRIMITIVES, 'the primitive list changed size without the brief changing').toHaveLength(8);
    expect(new Set(PRIMITIVES).size).toBe(PRIMITIVES.length);
    expect(PRIMITIVES, 'the primitive list is not sorted, so a duplicate is easy to miss')
      .toEqual([...PRIMITIVES].sort());
    const both = PRIMITIVE_FILES.filter((file) => (RENDER_SITES as readonly string[]).includes(file));
    expect(both, 'a file is in both lists, so the sweep below cannot say which rule applies').toEqual([]);
  });

  it('finds exactly the eight primitives bearing a lowercase tag — not thirteen (§6e(2), phase 8 I7)', async () => {
    const files = await productionFilesUnder('app', 'auth');
    const bearing: string[] = [];
    for (const file of files) {
      if (lowercaseJsxTags(await read(file)).length > 0) bearing.push(file);
    }

    // (a) the derived set is the primitive list MINUS the one that delegates to react-router. A ninth
    // primitive, or a helper anywhere under app/ or auth/ that renders a bare element, fails here.
    const expectedBearers = PRIMITIVE_FILES.filter((file) => file !== DELEGATES_ITS_ELEMENT);
    expect(expectedBearers, 'the exception list grew: more than one primitive renders no element of its own')
      .toHaveLength(7);
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
      'the union of the two pinned lists is no longer thirteen').toBe(13);
    // and every file the sweep found is in one of them, which is the cross-check §6e(2) actually asks for.
    for (const file of bearing) {
      expect([...PRIMITIVE_FILES, ...RENDER_SITES], `${file} renders an element and is in neither list`)
        .toContain(file);
    }
  });

  it('renders no lowercase JSX tag in any of the five render sites (§6e(3))', async () => {
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
      const expected = file === DELEGATES_ITS_ELEMENT ? 0 : 1;
      expect([...tags], `${file} renders ${[...tags].join(', ') || 'nothing'} rather than `
        + `${expected === 0 ? 'delegating to a component' : 'exactly one element'}`).toHaveLength(expected);
    }
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
  it('hands back the real DOM node for all eight', async () => {
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
      </>,
    );

    const expected: Record<(typeof PRIMITIVES)[number], string> = {
      ActionButton: 'BUTTON',
      FocusHolder: 'DIV',
      Heading: 'H1',
      Screen: 'MAIN',
      StatusRegion: 'DIV',
      Surface: 'DIV',
      Text: 'P',
      TextLink: 'A',
    };
    for (const name of PRIMITIVES) {
      // A primitive that drops `forwardRef` leaves this null, `?.focus()` becomes a no-op, and a keyboard
      // user is dropped to <body> with no test failing unless one asserts activeElement (L11). RD08 and RD09.
      expect(refs[name].current, `${name} did not forward its ref, so a caller holds null`).not.toBeNull();
      expect(refs[name].current?.tagName, `${name} forwarded a ref to the wrong element`).toBe(expected[name]);
    }
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
    ]);
  });

  it('holds no user-facing copy in any primitive (L11)', async () => {
    const copy = Object.values(en).flatMap((group) => Object.values(group as Record<string, string>));
    for (const file of PRIMITIVE_FILES) {
      const code = withoutComments(await read(file));
      for (const sentence of copy) {
        expect(code, `${file} spells "${sentence}" instead of taking it as children`).not.toContain(sentence);
      }
      expect(code, `${file} reads the catalogue, so a primitive decides what a screen says`)
        .not.toMatch(/useTranslation|\bt\(/);
    }
  });
});

// ==========================================================================================================
// AC10 — rendering a screen touches no storage and no network
// ==========================================================================================================

describe('T173 AC10: the primitive layer added no side effect', () => {
  it('writes no storage and makes no request while the two static screens render', async () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    const fetchSpy = vi.spyOn(globalThis, 'fetch');

    await mount(<SignedOutScreen />);
    expect(setItem).not.toHaveBeenCalled();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

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
      for (const utility of ['min-h-dvh', 'max-w-prose', 'text-step-1']) {
        expect(css, `the utility ${utility} emitted no rule, so it is a typo rather than a class`)
          .toContain(utility);
      }

      const html = await readFile(join(outDir, 'index.html'), 'utf8');
      const hrefs = [...html.matchAll(/<link\b[^>]*\bhref="([^"]+\.css)"/g)].map((match) => match[1]);
      expect(hrefs, 'index.html links no stylesheet, so the CSS asset is emitted but never loaded')
        .toEqual([`/app/assets/${stylesheets[0]}`]);
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
