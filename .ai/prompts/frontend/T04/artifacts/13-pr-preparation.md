# frontend · T04 · Phase 13 — PR / Commit Preparation

Gate: the Phase 12 verdict is **PASS**.

Branch note: work lands on `spec/service-specs-and-ai-framework` and is pushed after each phase. For `main`, merge or
squash the branch's final tree. The prompt's trailer names an older model; the repository's standing attribution
(`Claude Opus 5.5`) is used, as in T01–T03.

## Commit title
`frontend: lint rule bans hand-written backend fetch, run first in build (T04)`

## Commit message
```
frontend: lint rule bans hand-written backend fetch, run first in build (T04)

Makes L6 mechanical: app code reaches the backend only through
@themistra/api-client, and a violation fails the build and CI.

- oxlint 1.87.0 (exact; parses TS 7 code itself, where typescript-eslint
  peers on typescript <6.1).
- frontend/.oxlintrc.json runs only the L6 rules: no-restricted-globals
  for fetch and XMLHttpRequest, and no-restricted-properties for both on
  window, globalThis, self, top, parent, opener and frames. Every
  category is off and there are no overrides.
- lint = oxlint --no-ignore -c .oxlintrc.json src, so an .eslintignore
  can't hide a file; build runs lint before tsc and vite.
- lintRejectsHandWrittenBackendFetch (8 cases, 32 results): 21 banned
  forms reported, 5 legitimate ones allowed, today's src clean with
  exactly 2 rules, the pinned binary, a violating build stopping before
  tsc and Vite against a control build, .eslintignore not honoured, the
  exact config shape, and no inline disable directive in src.

Name-based limits (an aliased window, document.defaultView, Reflect.get)
and non-fetch transports are left to review and the R59 stream task.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

## Files changed
- `frontend/.oxlintrc.json` (new).
- `frontend/package.json`: the `oxlint` devDependency; the `lint` script; `build` runs lint first.
- `pnpm-lock.yaml`: `oxlint` and its platform bindings.
- `frontend/src/api/lintRejectsHandWrittenBackendFetch.test.ts` (new).

There are no production code, `spec/`, `contracts/`, `services/`, `infra/` or CI workflow changes. CI already runs
`pnpm -C frontend build`.

## Summary
The fourth frontend task makes it impossible to ship a hand-written call to the backend. Any `fetch` or
`XMLHttpRequest` in app code fails lint, and lint is the first step of the build, so the build stops before anything
is compiled or bundled. Nothing on screen changes.

## Testing performed
In the `infra/local` `frontend` container:
- `pnpm install --frozen-lockfile`: OK.
- `lint`: 0 errors, 2 rules. `typecheck` and `build`: clean.
- `pnpm -C frontend test`: **54/54** (32 new).

Manual check in phase 6: a temporary violating file under `src` made `pnpm -C frontend build` stop at lint, with no
`tsc` or Vite output. The file was removed. Phase 11 automated this check on a temp copy.

Mutation breaks: **12, each caught**. Each was applied to a backup and restored from it.
- `no-restricted-globals` removed;
- `window.fetch` dropped;
- `top` dropped;
- the XHR property entries removed;
- an override exempting `src`;
- lint removed from `build`;
- `--no-ignore` removed;
- a disable directive in `src`;
- a `fetch` in `src`;
- `;` instead of `&&` in `build`;
- lint moved last;
- `build` = lint only.

## Specification references
- **Task:** `spec/frontend/tasks.md` task 4, "No hand-written backend fetch".
- **Locked decision:** L6, generated client only (`design.md`).
- **Standing rule:** `agents.md`, "API access".
- **Named test:** `package.md` §8, `lintRejectsHandWrittenBackendFetch` → L6.
- **Requirement IDs:** none are cited by the task. R59 bounds the one future exception.
