# frontend · T07 · Phase 6 — Implementation Notes

Consumes `04-frozen-task-brief.md` and `05-implementation-plan.md`. Date: 2026-10-22. All runs were in
`themistra-local-frontend-1`, restarted after the dependency install.

## What changed

| File | Content | AC |
|---|---|---|
| `frontend/package.json`, `pnpm-lock.yaml` | `i18next` 26.4.2 and `react-i18next` 17.0.16, exact pins | — |
| `frontend/src/i18n/catalogue.ts` (new) | `en.rateLimit.message` / `.dismiss`, `as const` | AC5 |
| `frontend/src/i18n/i18n.ts` (new) | `createI18n()`: `createInstance()` + `init({ lng 'en', fallbackLng 'en', resources, escapeValue false, initAsync false })`; the `CustomTypeOptions` augmentation; no `initReactI18next` | AC5 |
| `frontend/src/api/rateLimitNotice.ts` (new) | `RateLimitNoticeState` and `createRateLimitNotice()`: one `shown` flag, listeners notified only on a change, a throwing listener rethrown in a microtask, and a middleware with `onResponse` only (`status === 429` → shown; returns `undefined`) | AC1–AC4, AC6 |
| `frontend/src/api/client.ts` | `createAuthApi(boundary, rateLimit?)` registers the notice middleware first, with the order comment | AC1, AC7 |
| `frontend/src/app/RateLimitNotice.tsx` (new) | `useSyncExternalStore` + `useTranslation`; an always-present `role="status" aria-live="polite"` region with the copy while shown, and a `type="button"` dismiss after it; a doc comment with the provider contract and the no-`AppShell` note | AC4, AC5 |

No other file changed. T05/T06, the shell, routes and every earlier test are untouched.

## Verification
- `typecheck` exit 0. `lint`: 0 errors over 17 files, 2 rules. `build` exit 0. `test` **99/99**, unchanged.
- **The bundle is unchanged in substance.** Nothing imports the new modules from `main` yet, so they are tree-shaken.
- **Scratch probe** (`src/api/zzProbe.test.tsx`, deleted after the run). It used a real T05 boundary and
  `createAuthApi(boundary, notice)`. The transport answered 401, then 429 with `Retry-After: 11` and a `title`.
  The component was rendered under `I18nextProvider` with `createI18n()`. Observed:
  - `isInitialized` was `true` straight after `createI18n()`, so `initAsync: false` is synchronous;
  - **before:** `<div role="status" aria-live="polite"></div>`, an empty region;
  - **after** the re-send 429 and two more 429s: the region held exactly the catalogue message, followed by the
    `Dismiss` button. The re-send 429 was seen, so the order is right;
  - **1** notification for 3 429s;
  - the caller got status 429, and there were **4** sends in total (401, re-send, 2 direct), so nothing was replayed;
  - `setInterval` ×0. `setTimeout` ×1, and it comes from **T05's renewal timeout** (the 401 path), not from the
    notice. Phase 10's no-timer case must therefore use a 429 with no renewal, or count only timers started by the
    429 handling;
  - **after dismiss:** an empty region, and the button is gone.
- **Type probe** (`src/i18n/zzTypeProbe.ts`, deleted after the run): `t('rateLimit.nope')` fails `tsc` with TS2345,
  and the accepted keys are `rateLimit.dismiss | rateLimit.message`. A missing key is a compile error.

## Deviations
None.
