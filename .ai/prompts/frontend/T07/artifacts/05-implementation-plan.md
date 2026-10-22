# frontend · T07 · Phase 5 — Implementation Plan

Consumes `04-frozen-task-brief.md`. Date: 2026-10-22. All commands run in `themistra-local-frontend-1` (`/repo`).

## Steps

1. **Dependencies.** `pnpm -C frontend add i18next@26.4.2 react-i18next@17.0.16 --save-exact`. Restart the
   container, then confirm that `pnpm-lock.yaml` and `frontend/package.json` are the only files changed by the
   install.
2. **`frontend/src/i18n/catalogue.ts`:** `export const en = { rateLimit: { message, dismiss } } as const`.
3. **`frontend/src/i18n/i18n.ts`:**
   - `createI18n(): i18n`. It calls `i18next.createInstance()`, then `init({ lng: 'en', fallbackLng: 'en',
     resources: { en: { translation: en } }, interpolation: { escapeValue: false }, initAsync: false })`.
   - **Fallback, if synchronous `init` returns before `isInitialized`:** stop and record it, rather than adding an
     await.
   - `declare module 'i18next' { interface CustomTypeOptions { defaultNS: 'translation'; resources: { translation:
     typeof en } } }`. This is i18next 26's own type-augmentation point. react-i18next's `useTranslation` reads the
     same `CustomTypeOptions`; the packed typings will be checked to confirm it.
4. **`frontend/src/api/rateLimitNotice.ts`:**
   - `RateLimitNoticeState { readonly middleware: Middleware; isShown(): boolean; subscribe(listener): () => void;
     dismiss(): void }`;
   - `createRateLimitNotice()` holds a closure with `shown` and a `Set` of listeners;
   - `notify()` calls each listener in a `try`; a throw is rethrown in a `queueMicrotask`;
   - the middleware is `{ onResponse({ response }) { if (response.status === 429) show(); return undefined; } }`.
5. **`frontend/src/api/client.ts`:**
   - `createAuthApi(boundary, rateLimit?)`;
   - `if (rateLimit) client.use(rateLimit.middleware)` before `client.use(boundary.middleware(...))`;
   - an order comment.
6. **`frontend/src/app/RateLimitNotice.tsx`:**
   - `useSyncExternalStore(notice.subscribe, notice.isShown)`;
   - `useTranslation()`;
   - markup: `<div>` containing `<div role="status" aria-live="polite">{shown ? t('rateLimit.message') : null}</div>`,
     then `{shown && <button type="button" onClick={notice.dismiss}>{t('rateLimit.dismiss')}</button>}`;
   - a doc comment with the provider contract and the no-`AppShell` note.
7. **Verification:**
   - `typecheck`, `lint`, `test` (99 unchanged) and `build`;
   - a scratch probe, deleted afterwards: a 429 shows the copy; the re-send 429 is seen; no timers; a burst gives one
     notification;
   - `git status` shows exactly the planned files.

## Risks
- **The JSX transform with TS 7:** the existing `.tsx` files compile with `jsx: react-jsx`, so no change is expected.
- **The lint:** `i18next` doesn't use `fetch` in our `src`, and lint scans `src` only.
- **The bundle:** react-i18next pulls in `@babel/runtime`, `html-parse-stringify` and `use-sync-external-store`.
  These are only reachable once the component is imported from `main`, and nothing imports it yet, so the production
  bundle is unchanged until the first screen. `shellBuildsAndMounts` keeps checking the bundle.

## Tests
Written in phase 10 (T03 precedent), in `rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx`.
