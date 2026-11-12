/// <reference types="node" />
/**
 * `homeRouteRendersLandingScreenAndManagesFocus` (package.md §8) — task 174, the home screen.
 *
 * TWO CLAIMS, AND THE SECOND IS THE ONE WITH TEETH. "Renders a landing screen" is nearly self-evident once
 * a component exists. "Moves focus to it on arrival" is not: a single-page application swaps the document's
 * contents without a document load, so unless something moves it, focus stays where it was or sits on
 * `<body>`, and a screen-reader user is told nothing happened. Every case under AC2 and AC8 is about that.
 *
 * WHAT THIS FILE DOES NOT PROVE, said here so no reader mistakes a green run for more than it is:
 *
 *   1. It does not prove a real Tab keypress leaves the screen. `@testing-library/user-event` is not a
 *      dependency and jsdom does not implement sequential focus navigation, so a synthetic Tab moves focus
 *      NOWHERE - a case that pressed it and asserted movement would fail against correct code, and one that
 *      asserted stillness would pass against a real trap. AC8 asserts the STRUCTURAL property that causes
 *      trapping instead (OQ-T174-5 carries the gap, with the dependency it would cost).
 *   2. It does not prove the ARRIVAL. These cases mount the screen directly, which measures the screen, not
 *      the journey to it. The end-to-end proof is real and it is elsewhere:
 *      `loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx`'s T27-D1 case drives a real callback
 *      through a real exchange onto `/app/home` and asserts focus lands on this heading. That case was a
 *      PIN on an unmet obligation for eleven tasks and task 174 discharged it; it is named here rather than
 *      duplicated, so a rename breaks a test instead of orphaning a citation.
 *
 * AC9 IS ENFORCED BY `tsc`, NOT BY AN ASSERTION HERE. `Heading`'s props type is `{ children }`, so a screen
 * passing `tabIndex` is a compile error, and the test behind that guarantee is
 * `lintRejectsHandWrittenBackendFetch.test.ts`'s case *"stops build at lint, so tsc and Vite never run on a
 * violation"*, which runs a real `pnpm run build` over today's sources. The source assertion below is
 * corroboration that the type was not widened, not the enforcement.
 */
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { act, StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { accessToken, clearAccessToken, retainAccessToken } from '../auth/accessToken';
import { en } from '../i18n/catalogue';
import { createI18n } from '../i18n/i18n';
import { HomeScreen } from './HomeScreen';
import { APP_BASENAME, appRoutes, HOME_PATH, SIGN_IN_PATH } from './routes';

const srcDir = join(dirname(fileURLToPath(import.meta.url)), '..');
const read = (relativePath: string) => readFile(join(srcDir, relativePath), 'utf8');

/** Comment-stripping, so a scan for a construct cannot be satisfied or defeated by prose about it. Task 173's
 *  shape, including the `(?<!:)` guard that keeps a `//` inside `https://` from eating the rest of a line. */
function withoutComments(code: string): string {
  return code.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(?<!:)\/\/[^\n]*/g, '');
}

/** Lowercase JSX tags, with the lookbehind task 173 needed: without it `Promise<string>` reports `<string>`
 *  as an element. A generic is not a tag, and the scan that cannot tell them apart measures nothing. */
function lowercaseJsxTags(code: string): string[] {
  return [...withoutComments(code).matchAll(/(?<![A-Za-z0-9_$])<([a-z][a-zA-Z0-9-]*)(?=[\s/>])/g)]
    .map((match) => match[1] as string);
}

let mounted: { root: Root; container: HTMLElement } | undefined;

/** Mounts the screen alone, inside the providers it needs and nothing else, so `querySelectorAll` needs no
 *  scoping to be right (T37's lesson: a scoped query cannot measure an unscoped claim, and the inverse). */
async function mountScreen(options: { strict?: boolean } = {}): Promise<HTMLElement> {
  const container = document.createElement('div');
  document.body.append(container);
  const router = createMemoryRouter([{ path: '*', element: <HomeScreen /> }], { initialEntries: ['/'] });
  const root = createRoot(container);
  const tree = (
    <I18nextProvider i18n={createI18n()}>
      <RouterProvider router={router} />
    </I18nextProvider>
  );
  await act(async () => {
    root.render(options.strict === true ? <StrictMode>{tree}</StrictMode> : tree);
  });
  mounted = { root, container };
  return container;
}

/** Mounts the REAL route table at a real path, which is the only way to measure the gate (AC4). */
async function mountRealTable(path: string): Promise<{ container: HTMLElement; pathname: () => string }> {
  const container = document.createElement('div');
  document.body.append(container);
  const router = createMemoryRouter(appRoutes, {
    basename: APP_BASENAME,
    initialEntries: [`${APP_BASENAME}/${path}`],
  });
  const root = createRoot(container);
  await act(async () => {
    root.render(
      <I18nextProvider i18n={createI18n()}>
        <RouterProvider router={router} />
      </I18nextProvider>,
    );
  });
  mounted = { root, container };
  return { container, pathname: () => router.state.location.pathname };
}

beforeEach(() => {
  clearAccessToken();
  globalThis.sessionStorage.clear();
  globalThis.localStorage.clear();
});

afterEach(async () => {
  if (mounted) {
    const { root, container } = mounted;
    await act(async () => root.unmount());
    container.remove();
    mounted = undefined;
  }
  clearAccessToken();
  globalThis.sessionStorage.clear();
  globalThis.localStorage.clear();
  vi.restoreAllMocks();
});

// ==========================================================================================================
// AC1 — it renders a landing screen, through the design system
// ==========================================================================================================

describe('AC1 the screen renders through T173 primitives', () => {
  it('renders one main landmark holding exactly one h1', async () => {
    const container = await mountScreen();

    expect(container.querySelectorAll('main'), 'the landmark a screen-reader user navigates by').toHaveLength(1);
    expect(container.querySelectorAll('h1'), 'the document outline L11 requires has one top level').toHaveLength(1);
    expect(container.querySelector('main h1'), 'the heading is outside the landmark').not.toBeNull();
  });

  it('writes no lowercase JSX tag of its own (§6e(3))', async () => {
    expect(lowercaseJsxTags(await read('app/HomeScreen.tsx'))).toEqual([]);
  });

  it('CONTROL: the tag scan still finds a real element, so the emptiness above is a result', async () => {
    // Without this, the case above passes against a scan that matches nothing at all - and task 173 shipped
    // exactly that bug once, when a generic made the pattern over-match and the fix made it under-match.
    expect(lowercaseJsxTags(await read('app/ui/Screen.tsx')), 'Screen renders <main>').toContain('main');
    expect(lowercaseJsxTags('const x: Promise<string> = y;'), 'a generic is not a tag').toEqual([]);
  });
});

// ==========================================================================================================
// AC2 — the focus move, which is the half of the task statement with teeth
// ==========================================================================================================

describe('AC2 arrival moves focus into the screen', () => {
  it('focuses the heading itself, and it is the heading that carries the screen name', async () => {
    const container = await mountScreen();
    const heading = container.querySelector('h1');

    expect(document.activeElement, 'focus was not moved to the heading on arrival (L11)').toBe(heading);
    expect(heading?.textContent, 'focus landed on an element that announces nothing').toBe(en.home.heading);
  });

  it('distinguishes the three candidates the freeze weighed: not body, not main, not a wrapper', async () => {
    // D1 chose the heading over `<main>` and over a labelled holder. A case asserting only "not body" would
    // pass for all three, so the rejected two are named: this fails if a later edit moves the target.
    const container = await mountScreen();

    expect(document.activeElement, 'focus was left on body - the gap this task exists to close')
      .not.toBe(document.body);
    expect(document.activeElement, 'focus moved to the landmark, which announces no screen name')
      .not.toBe(container.querySelector('main'));
    expect(document.activeElement?.tagName).toBe('H1');
  });

  it('CONTROL: body is what holds focus when nothing moves it', async () => {
    // The assertions above are only meaningful if `<body>` is the real default here - if jsdom happened to
    // focus the first element itself, "not body" would be true for a screen that managed nothing.
    expect(document.activeElement, 'focus was somewhere else before any mount, so AC2 measures nothing')
      .toBe(document.body);
  });
});

// ==========================================================================================================
// AC3 — every word comes from the catalogue
// ==========================================================================================================

describe('AC3 the copy is catalogue copy', () => {
  it('renders the catalogue entries, as literal English, with no key left in the DOM', async () => {
    const text = (await mountScreen()).textContent ?? '';

    expect(text).toContain(en.home.heading);
    expect(text).toContain(en.home.explanation);
    // Asserted as literals too: comparing the DOM to `en` alone passes when both are the key, which is what
    // a failed lookup renders.
    expect(text).toContain('Home');
    expect(text).toContain('You are signed in.');
    expect(text, 'a key leaked into the DOM, so the catalogue lookup failed').not.toMatch(/home\./);
  });

  it('holds no user-facing string in its own source', async () => {
    const source = withoutComments(await read('app/HomeScreen.tsx'));

    expect(source).toContain("t('home.heading')");
    expect(source).toContain("t('home.explanation')");
    expect(source, 'the screen hardcoded its own copy, which no translator can reach')
      .not.toContain(en.home.explanation);
  });
});

// ==========================================================================================================
// AC4 — an unauthenticated arrival at THIS path is redirected (R28)
// ==========================================================================================================

describe('AC4 the gate covers /app/home specifically', () => {
  it('redirects an arrival with no session, and renders no part of the screen', async () => {
    const { container, pathname } = await mountRealTable(HOME_PATH);

    expect(pathname(), 'an unauthenticated visitor reached the authenticated landing (R28)')
      .toBe(`${APP_BASENAME}/${SIGN_IN_PATH}`);
    expect(container.textContent ?? '', 'the landing screen rendered before the redirect, leaking a signed-in view')
      .not.toContain(en.home.heading);
  });

  it('renders the screen when there IS a session, so the case above measures the gate and not a broken route',
    async () => {
      retainAccessToken('a-token-for-this-case');
      const { container, pathname } = await mountRealTable(HOME_PATH);

      expect(pathname()).toBe(`${APP_BASENAME}/${HOME_PATH}`);
      expect(container.textContent ?? '').toContain(en.home.heading);
      expect(document.activeElement, 'focus is managed through the real route table too, not only a direct mount')
        .toBe(container.querySelector('h1'));
    });
});

// ==========================================================================================================
// AC5 / AC6 — it touches nothing, and knows nothing about who is reading it
// ==========================================================================================================

describe('AC5 the screen reads and writes nothing', () => {
  it('makes no network call and writes no storage', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const sessionWrite = vi.spyOn(Storage.prototype, 'setItem');

    await mountScreen();

    expect(fetchSpy, 'the landing screen called an endpoint; task 42 owns the data, not this one').not.toHaveBeenCalled();
    expect(sessionWrite, 'the landing screen wrote storage (R29, L16)').not.toHaveBeenCalled();
    expect(globalThis.sessionStorage.length).toBe(0);
    expect(globalThis.localStorage.length).toBe(0);
  });

  it('does not read the access token, which is where a name would have to come from (R20)', async () => {
    const tokenRead = vi.spyOn(accessToken, 'current');
    await mountScreen();
    expect(tokenRead, 'the screen read the session token').not.toHaveBeenCalled();
  });
});

describe('AC6 nothing it renders depends on identity (R22)', () => {
  it('imports only react, react-i18next and the primitive barrel', async () => {
    // THE IMPORT LIST IS THE CONTRACT (D6). A screen cannot show a name it has no way to obtain, so the
    // cheapest durable enforcement is the set of modules it is allowed to reach. `react` is here and is NOT
    // in the frozen AC6 list - recorded as a deviation in phase 6, because the same brief mandates a focus
    // effect and `useEffect`/`useRef` come from nowhere else. The prohibition the AC was written for - no
    // `auth/`, no storage, no network - is unchanged and is what the second assertion holds.
    const imports = [...withoutComments(await read('app/HomeScreen.tsx'))
      .matchAll(/from '([^']+)'/g)].map((match) => match[1]);

    expect(imports.sort()).toEqual(['./ui', 'react', 'react-i18next']);
  });

  it('reaches no identity source, named one by one rather than by the list above', async () => {
    const source = withoutComments(await read('app/HomeScreen.tsx'));

    for (const forbidden of [/\bauth\//, /accessToken/, /idToken/, /userinfo/i, /accounts\/me/,
      /sessionStorage/, /localStorage/, /document\.cookie/, /\bfetch\s*\(/, /globalThis\.location/]) {
      expect(source, `the screen reaches ${String(forbidden)}`).not.toMatch(forbidden);
    }
  });

  it('renders no identity-shaped text', async () => {
    const text = (await mountScreen()).textContent ?? '';

    for (const word of ['@', 'name', 'email', 'account', 'welcome back', 'suspended', 'verified']) {
      expect(text.toLowerCase(), `the landing screen says "${word}", which it cannot know (R22)`)
        .not.toContain(word);
    }
  });
});

// ==========================================================================================================
// AC7 — the three closed-task instruments really were grown
// ==========================================================================================================

describe('AC7 the instruments this task was obliged to grow', () => {
  it('names home in the screen-count pin that belongs to task 16', async () => {
    const source = await read('app/routesResolveUnderAppPrefixOnly.test.tsx');
    expect(source, "task 16's SCREENS_ATTACHED_SO_FAR did not learn about the fourth screen").toMatch(/'home',/);
  });

  it('names HomeScreen.tsx in the render-site list and counts the union at fourteen', async () => {
    const source = await read('app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx');
    expect(source).toContain("'app/HomeScreen.tsx',");
    expect(source, 'the union pin still says thirteen, so a render site is unpinned').toMatch(/\.toBe\(14\)/);
  });

  it('names home in the attached-element pin, which is read from the route table itself', () => {
    // Read from the TABLE rather than from a test file: this is the one of the three whose subject is
    // production code, and a source scan of a test would pass if the table and the pin both regressed.
    type Node = { path?: string; element?: unknown; children?: Node[] };
    const walk = (routes: readonly Node[]): Node[] =>
      routes.flatMap((route) => [route, ...walk(route.children ?? [])]);
    const attached = walk(appRoutes as Node[])
      .filter((route) => route.path !== undefined && route.element !== undefined)
      .map((route) => route.path);

    expect(attached, 'the home route lost its element').toContain(HOME_PATH);
  });
});

// ==========================================================================================================
// AC8 / AC9 / AC10 — focusable without being a trap, set inside the primitive, stable under StrictMode
// ==========================================================================================================

describe('AC8 focus is moved without creating a trap', () => {
  it('gives the focus target a negative tabindex, so it is reachable by code and not by Tab', async () => {
    const heading = (await mountScreen()).querySelector('h1');

    expect(heading?.getAttribute('tabindex'), 'the heading is in the tab order, which reorders the screen')
      .toBe('-1');
  });

  it('renders no element with a non-negative tabindex, which is what actually captures keyboard navigation',
    async () => {
      const container = await mountScreen();
      const offenders = [...container.querySelectorAll('[tabindex]')]
        .filter((element) => !(element.getAttribute('tabindex') ?? '').startsWith('-'))
        .map((element) => element.outerHTML);

      expect(offenders, 'an element was put into or ahead of the tab order').toEqual([]);
    });

  it('installs no key handler and declares no modal, the two other ways a screen holds focus', async () => {
    const container = await mountScreen();
    const source = withoutComments(await read('app/HomeScreen.tsx'));

    expect(container.querySelectorAll('[aria-modal]')).toHaveLength(0);
    expect(source, 'the screen listens for keys, which is how a trap is written').not.toMatch(/onKeyDown|addEventListener/);
  });
});

describe('AC9 tabIndex is set inside the primitive, not accepted from a consumer', () => {
  it('renders tabindex="-1" from Heading, for every consumer and not only this screen', async () => {
    const source = withoutComments(await read('app/ui/Heading.tsx'));
    expect(source, 'Heading no longer makes its h1 focusable, so every screen loses its focus target')
      .toMatch(/<h1[^>]*tabIndex=\{-1\}/);
  });

  it('keeps the props type at { children }, so ui/index.ts rule 2 holds literally', async () => {
    const source = withoutComments(await read('app/ui/Heading.tsx'));

    expect(source, "Heading's props type was widened - rule 2 forbids accepting the attribute it sets")
      .toMatch(/forwardRef<HTMLHeadingElement,\s*\{\s*children:\s*ReactNode\s*\}>/);
    expect(source, 'tabIndex became a prop, which is what the freeze rejected').not.toMatch(/tabIndex\?\s*:/);
    // The ENFORCEMENT is `tsc`, not the two assertions above: a consumer passing `tabIndex` fails the real
    // `pnpm run build` run by lintRejectsHandWrittenBackendFetch's case "stops build at lint, so tsc and
    // Vite never run on a violation". Named so a rename there breaks a test rather than rotting a comment.
    expect(await read('app/ui/index.ts'), 'rule 2 was edited away rather than observed')
      .toMatch(/No primitive accepts the attribute that is its own contract/);
  });
});

describe('AC10 the focus move survives StrictMode double invocation', () => {
  it('lands on the same heading when the effect runs twice', async () => {
    const container = await mountScreen({ strict: true });

    expect(document.activeElement, 'the second effect invocation moved or lost focus')
      .toBe(container.querySelector('h1'));
    expect(container.querySelectorAll('h1'), 'StrictMode rendered the screen twice into the DOM').toHaveLength(1);
  });
});
