/// <reference lib="webworker" />
/**
 * The service worker entry. It makes the policies in this directory real, and it decides nothing itself.
 *
 * Every rule here is imported: the navigation denylist from `navigationDenylist.ts` (R57, L17, the eleven paths in
 * `agents.md` lines 88-89) and the shell URL from `apiRequestPolicy.ts` (design.md §5). The file is deliberately
 * six statements long — a rule restated here would be a rule that can drift from the spec it came from.
 *
 * `skipWaiting()` and `clients.claim()` are absent, and that is the point of this task. `agents.md` line 91: "The
 * worker uses one worker-wide update policy: prompt-to-reload. `skipWaiting` and `clients.claim` are not used."
 * A worker that activates mid-session would swap the caching and navigation rules underneath a page that already
 * holds tokens in memory (L3), with a half-applied session. So a new build installs, waits, and does nothing until
 * no client of the old build is left.
 *
 * Scope: T12 implements only the prompt-to-reload update policy. The forced-update-for-rollback mechanism of
 * `package.md` §10 — the remote configuration document, the build-time flag, the kill switch and the
 * build-identifier denylist — is unowned by any task in `tasks.md` and out of scope here. So is the visible reload
 * prompt; `registerServiceWorker.ts` exposes the hook a later screen will render from.
 */
import { createHandlerBoundToURL, precacheAndRoute } from 'workbox-precaching';
import { NavigationRoute, registerRoute, setDefaultHandler } from 'workbox-routing';
import { NetworkOnly } from 'workbox-strategies';
import { SHELL_DOCUMENT_URL } from './apiRequestPolicy';
import { NAVIGATION_DENYLIST } from './navigationDenylist';

declare const self: ServiceWorkerGlobalScope & {
  __WB_MANIFEST: Array<{ url: string; revision: string | null }>;
};

// The shell and the hashed assets, and nothing else (design.md §5). Registered first, because a precache route
// matches by URL ahead of the navigation fallback: a denylisted URL inside this manifest would be served from
// cache whatever the denylist below says. The build cannot produce one while `base` is `/app/`, and the suite
// asserts it over the real manifest so that stays true.
precacheAndRoute(self.__WB_MANIFEST);

// Navigations the SPA owns get the shell from the precache. The eleven identity and backend paths are excluded by
// NAVIGATION_DENYLIST itself, not by a copy of it, so this route cannot disagree with `agents.md`.
// The cast keeps the identity of T09's array: Workbox types `denylist` as mutable `RegExp[]` though it only ever
// reads it, and spreading into a copy would let this route hold a filtered or stale version of the eleven paths.
registerRoute(
  new NavigationRoute(createHandlerBoundToURL(SHELL_DOCUMENT_URL), {
    denylist: NAVIGATION_DENYLIST as RegExp[],
  }),
);

// Everything that did not match a route above - every API call, every identity request, every unrecognised path -
// is fetched and never stored. This is `isNetworkOnlyRequest`'s default-deny expressed as Workbox's default
// handler: the negation of the cacheable set, so a path nobody enumerated cannot fall through into a cache.
setDefaultHandler(new NetworkOnly());
