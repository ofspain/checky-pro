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
 * normal outcome — the same path taken under jsdom and on an insecure origin.
 */

/** The worker's scope, stated rather than inherited, so it cannot widen silently (L14, L9, R57). */
export const SERVICE_WORKER_SCOPE = '/app/';

/** Where the build emits the worker: `filename` in the plugin's config, under Vite's `/app/` base. */
export const SERVICE_WORKER_URL = '/app/serviceWorker.js';

type UpdateListener = () => void;

const listeners = new Set<UpdateListener>();
let registering: Promise<ServiceWorkerRegistration | undefined> | undefined;

/** One listener's failure must not hide the update from the others, nor surface as an unhandled rejection. */
function callListener(listener: UpdateListener): void {
  try {
    listener();
  } catch {
    // Deliberately swallowed: a screen that cannot render the notice is not this module's problem to solve.
  }
}

function notifyUpdateWaiting(): void {
  for (const listener of [...listeners]) callListener(listener);
}

/** Reports the waiting worker, now or whenever one arrives. Never promotes it. */
function trackWaiting(registration: ServiceWorkerRegistration): void {
  if (registration.waiting) notifyUpdateWaiting();

  registration.addEventListener('updatefound', () => {
    const installing = registration.installing;
    if (!installing) return;
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

/** Subscribes to "a new build is waiting". Fires immediately if one already is. Returns an unsubscribe. */
export function onUpdateWaiting(listener: UpdateListener): () => void {
  listeners.add(listener);
  void registerServiceWorker().then((registration) => {
    if (registration?.waiting && listeners.has(listener)) callListener(listener);
  });
  return () => {
    listeners.delete(listener);
  };
}
