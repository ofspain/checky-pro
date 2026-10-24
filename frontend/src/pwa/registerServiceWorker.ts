/**
 * The one place the SPA registers its service worker, and the one place that watches for a new build.
 *
 * The update policy is prompt-to-reload (`agents.md` line 91, `package.md` §10): a new build installs, enters
 * `waiting`, and is never promoted from here. There is deliberately no function that activates it — the only way a
 * page can promote a waiting worker is to message it so it calls `self.skipWaiting()`, which is the exact call the
 * policy forbids. `onUpdateWaiting` therefore reports the fact and stops; what a screen does with that is a later
 * task's decision, and this module introduces no user-facing copy (L11).
 *
 * Registration is not guarded by an environment check, because it cannot be: no production source may name Vite's
 * compile-time environment object (R58, L10, asserted over all of `src/` by T01 — which this comment itself has to
 * respect). In development no worker is emitted, the request 404s, and a rejected registration is treated as a
 * normal outcome — the same path taken under jsdom and on an insecure origin. One attempt per page load: a failed
 * registration is remembered, and nothing retries it.
 *
 * `onUpdateWaiting` only observes. It does not register — `main.tsx` owns that, as the single site — so a screen
 * that subscribes cannot cause a registration as a side effect, and a subscriber is told about a waiting build
 * exactly once however it is ordered against registration.
 */

/**
 * The worker's scope, stated rather than inherited, so it cannot widen silently (L14, L9, R57). It must keep its
 * trailing slash: scope matching is a prefix match over the whole URL string, not over path segments, so `/app`
 * would also claim `/apple.html` — the widening T09's segment matching exists to prevent, and more than the SPA's
 * own prefix (AC5). The cost is that a client at exactly `/app`, with no slash, would be uncontrolled; the edge
 * serves no such URL (it 404s, verified against the running stack), and every URL that does serve the shell is
 * under `/app/`. A guard case ties this constant to Vite's `base`.
 */
export const SERVICE_WORKER_SCOPE = '/app/';

/** Where the build emits the worker: `filename` in the plugin's config, under Vite's `/app/` base. */
export const SERVICE_WORKER_URL = '/app/serviceWorker.js';

type UpdateListener = () => void;

const listeners = new Set<UpdateListener>();
let registering: Promise<ServiceWorkerRegistration | undefined> | undefined;
/** Whether a new build is known to be waiting. The single source of that fact, so it is reported once. */
let updateIsWaiting = false;

/** One listener's failure must not hide the update from the others, nor surface as an unhandled rejection. */
function callListener(listener: UpdateListener): void {
  try {
    listener();
  } catch {
    // Deliberately swallowed: a screen that cannot render the notice is not this module's problem to solve.
  }
}

/** Records that a build is waiting and tells everyone subscribed. Repeat calls for the same build are no-ops. */
function notifyUpdateWaiting(): void {
  if (updateIsWaiting) return;
  updateIsWaiting = true;
  for (const listener of [...listeners]) callListener(listener);
}

/** Reports the waiting worker, now or whenever one arrives. Never promotes it. */
function trackWaiting(registration: ServiceWorkerRegistration): void {
  if (registration.waiting) notifyUpdateWaiting();

  registration.addEventListener('updatefound', () => {
    const installing = registration.installing;
    if (!installing) {
      // The lifecycle sets `installing` before this fires, so this is defensive: check once the current task
      // finishes rather than dropping an update that is already waiting.
      void Promise.resolve().then(() => {
        if (registration.waiting) notifyUpdateWaiting();
      });
      return;
    }
    installing.addEventListener('statechange', () => {
      if (installing.state === 'installed' && registration.waiting) notifyUpdateWaiting();
    });
  });
}

/**
 * Registers the worker once. Resolves `undefined` where service workers are unavailable or registration fails, and
 * never rejects: mounting the shell must not depend on it (T01).
 */
export function registerServiceWorker(): Promise<ServiceWorkerRegistration | undefined> {
  registering ??= (async () => {
    const container = globalThis.navigator?.serviceWorker;
    if (!container) return undefined;
    try {
      const registration = await container.register(SERVICE_WORKER_URL, { scope: SERVICE_WORKER_SCOPE });
      trackWaiting(registration);
      return registration;
    } catch {
      return undefined;
    }
  })();
  return registering;
}

/**
 * Subscribes to "a new build is waiting", and returns an unsubscribe. Fires immediately if one already is, and
 * exactly once either way: the fact lives in `updateIsWaiting`, so subscribing before or after the worker is
 * found cannot produce two notifications. Observing does not register; `main.tsx` does that.
 */
export function onUpdateWaiting(listener: UpdateListener): () => void {
  listeners.add(listener);
  if (updateIsWaiting) callListener(listener);
  return () => {
    listeners.delete(listener);
  };
}
