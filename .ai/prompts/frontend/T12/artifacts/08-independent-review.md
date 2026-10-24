# frontend · T12 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T12 — Prompt-to-reload update policy |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | Current `frontend/src/pwa/serviceWorker.ts`, `frontend/src/pwa/registerServiceWorker.ts`, `frontend/vite.config.ts`, `frontend/src/main.tsx`, `frontend/src/pwa/apiRequestPolicy.ts`, `frontend/src/pwa/serviceWorkerUpdateWaitsForReload.test.ts`, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/08-independent-review.md` |

**Task statement (verbatim):**
> **Prompt-to-reload update policy.** The worker never calls `skipWaiting` or `clients.claim`, and a new build waits for a user reload. Verifies `serviceWorkerUpdateWaitsForReload`. Cites L17, `package.md` §10.

---

## Review scope

Reviewed the Phase 6 T12 implementation against the frozen brief, `agents.md`, `package.md`, and the Phase 7 self-review. No code was rewritten.

The implementation is well-structured: `injectManifest` strategy, worker imports T09/T11 policies by identity, scope is `/app/`, registration is single-site and idempotent, no `skipWaiting`/`clients.claim`, and the emitted bundle is scanned. The self-review found eight issues; this review corroborates them and adds one fresh observation.

---

## New finding

### 1. The service-worker scope trailing slash may not control navigation to `/app`
**Issue ·** `SERVICE_WORKER_SCOPE` is `/app/`. Service worker scope matching uses the scope path as a prefix; a navigation to `/app` (no trailing slash) is not unambiguously within `/app/` in all browsers. The edge redirects `/` to `/app/`, so the typical entry point is `/app/`, but a direct link to `/app` could load the shell without a controlling worker.  
**Severity ·** Low — the redirect makes it unlikely in practice, and the denylist is still protected by the edge for out-of-scope navigations.  
**Evidence ·** `frontend/src/pwa/registerServiceWorker.ts:17` (`export const SERVICE_WORKER_SCOPE = '/app/'`); `infra/local/edge/Caddyfile:32-34` (root redirect to `/app/`).  
**Recommendation ·** Either (a) change the scope to `/app` (without trailing slash), which controls both `/app` and `/app/...`, or (b) add a guard test that asserts the registration scope matches the SPA base and document the trailing-slash semantics in the module comment. Option (a) is safer if the plugin supports it.

---

## Corroborated findings (from Phase 7 self-review)

| # | Self-review finding | Independent view |
|---|---|---|
| S1 | Subscriber before registration resolves is notified twice | **Confirmed — this is the most important defect.** `trackWaiting` notifies on an already-waiting worker, and `onUpdateWaiting`'s `.then` also notifies when registration resolves. The two paths intersect for early subscribers. Fix by making immediate notification the responsibility of only one path, and add a test that subscribes before registration resolves. |
| S2 | Failed registration is memoised forever | **Confirmed, low.** The memo is assigned before the result is known. Document "one attempt per page load" or clear the memo on failure. |
| S3 | Subscribing has the side effect of registering | **Confirmed, design smell.** It is convenient but surprising. Document it in the public API comment or split observation from registration. |
| S4 | `trackWaiting` can miss an update if `installing` is null | **Confirmed, defensive gap.** The `updatefound` handler returns silently when `installing` is null. Add a fallback to check `registration.waiting` on the next tick. |
| S5 | Forbidden-call bundle assertion depends on minification stripping comments | **Confirmed.** The source comment explains why `skipWaiting`/`clients.claim` are absent; the emitted bundle scan would fail if minification were disabled. Add a test comment documenting this dependency. |
| S6 | `main.tsx` is outside the forbidden-call source scan | **Confirmed.** Add `src/main.tsx` to the scanned set so a fast source assertion catches a misplaced call before the slow build scan. |
| S7 | Scope and worker URL are literals, not derived from Vite's `base` | **Confirmed.** Add a guard test that `SERVICE_WORKER_SCOPE` matches `vite.config.ts`'s `base` and that `SERVICE_WORKER_URL` is `base + filename` with `.ts` → `.js`. |
| S8 | AC4's "one worker-wide policy" is only partly asserted | **Confirmed.** Add assertions that exactly one file in `src/**` references `__WB_MANIFEST` and that the plugin config names exactly one `filename`. |

---

## Non-gaps (acknowledged, no change needed)

- **`injectManifest` strategy** is correctly chosen so the worker can import policy modules.
- **No `skipWaiting`, `clients.claim`, `postMessage`, `location.reload`** in source or emitted bundle.
- **Single registration site** in `src/main.tsx`, using `registerServiceWorker()` from `src/pwa/registerServiceWorker.ts`.
- **Worker scope `/app/`** keeps denylisted paths outside the worker's reach (belt-and-braces with the edge).
- **Navigation route uses `NAVIGATION_DENYLIST` by identity** — not a copy of the eleven paths.
- **Shell fallback bound to `SHELL_DOCUMENT_URL`** from `apiRequestPolicy.ts`.
- **`setDefaultHandler(new NetworkOnly())`** implements T11's default-deny for all non-precached requests.
- **Precache manifest contains only `/app/*` URLs**, including the shell and hashed assets, and no denylisted path.
- **`/app/signed-out`** is not in the precache manifest and is still answered by the shell navigation fallback.
- **No manifest emitted**, no env keys, no absolute URLs, no storage, no user-facing strings.
- **Drift guards** against `agents.md:91` and `package.md:320` are in place.
- **`registerServiceWorker` is idempotent** and degrades quietly when service workers are unavailable or registration fails.
- **T01 `shellBuildsAndMounts.test.tsx` absence assertions** are edited only where needed (lines described in the brief).

---

## Summary

T12's implementation correctly realizes the prompt-to-reload policy and wires T09/T11 into a real Workbox worker. The highest-priority Phase 9 fix is **S1** (double notification for early subscribers). The remaining self-review findings are low-severity and mostly about adding assertions or documentation. The fresh finding about the `/app/` scope trailing slash is minor but worth settling before the task is considered complete.
