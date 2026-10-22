# frontend · T01 · Phase 7 — Self Review

Reviewed the Phase 6 diff against the FROZEN brief and `agents.md`. Findings only; fixes are for Phase 9.

Checks with no findings:
- **Scope:** only the authorized files changed. The `infra/local/bin/up` fix is outside the T01 change set, in its own commit.
- **L13:** only Vite, React, TypeScript and Vitest/jsdom are installed.
- **L9:** no proxy, no backend URL.
- **L10:** no env key; `grep` finds no `import.meta.env`, `fetch`, storage or `serviceWorker` in `src/`.
- **L11:** the shell has no text.
- **Null handling:** `mountShell(null)` throws.
- **Secrets:** none.
- **Types:** `strict`, no `any`.
- **Thread-safety, transactions, money, idempotency:** not applicable (static SPA shell).

| # | Issue | Severity | Evidence | Recommendation |
|---|---|---|---|---|
| 1 | The Vitest default environment is `jsdom` for every test file, and the planned `shellBuildsAndMounts` runs a Vite production build inside that environment. A bundler running with jsdom's `window`/`document` globals may behave differently from a normal Node build, or fail. | Medium | `frontend/vite.config.ts:16-18` (`test.environment: 'jsdom'`); plan, test case 1 | In Phase 10, check that the build case works under jsdom. If it doesn't, split the cases with a per-file `// @vitest-environment` annotation, keeping one `describe('shellBuildsAndMounts')` name across the files. |
| 2 | The root `engines.node` is `>=22.12`, but `jsdom@30.1.3` requires `^22.22.2` and `vitest@5.0.5` `^22.12.0`. A Node 22.12–22.22.1 machine passes the root engines check and then fails at jsdom. | Low | `package.json:5-7`; jsdom engines (checked in Phase 5) | Raise the root `engines.node` to `>=22.22.2`, the effective floor. |
| 3 | `mountShell` creates a new React root on every call, so calling it twice on the same container would warn and double-mount. Only `main.tsx` calls it, once. | Low | `frontend/src/app/mountShell.tsx:9-17` | No change needed for T01. Tests must `unmount()` the returned `Root` and use a fresh container per case. |
| 4 | The CI `frontend` job does not cache the pnpm store, so every run downloads all packages. | Low | `.github/workflows/ci.yml:47-59` | Optional: `actions/setup-node` `cache: pnpm` needs pnpm on PATH first, so `corepack enable` would have to run before `setup-node`. Defer unless CI time matters. |
| 5 | `server.host: true` makes the dev server listen on every interface. That's required in the container (the edge reaches it as `frontend:5173`), but someone running `pnpm dev` on a host outside Docker would expose it on the LAN. | Low | `frontend/vite.config.ts:10-14` | Accept. The frozen brief and `KIMI-LOCAL-STACK.md` require it, and the dev server is not a production surface. |
| 6 | Vite blocks requests whose `Host` header is not allowed (`server.allowedHosts`). Through Caddy the Host is `localhost:5173`, which is allowed by default, and the live check passed. A future edge or container name used as Host (e.g. `frontend`) would be refused. | Info | Live check in `06-implementation-notes.md` | No change. If an E2E setup ever sends a different Host, add it to `allowedHosts` then. |
| 7 | The `<title>Checky Pro</title>` is hard-coded in `index.html`, outside the catalogue. | Info | `frontend/index.html:6` | Accepted in Phase 4 (disposition 2). The first i18n task should take ownership of `document.title`. |
