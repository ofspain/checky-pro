import { AUTHENTICATED_ROUTE_PATHS, HOME_PATH } from '../app/routes';

/**
 * L16's fourth item: the post-login return target.
 *
 * L1 and `agents.md:21` are one sentence between them - an **allowlisted** post-login return target, falling back
 * to the home route, "never to an arbitrary URL". That last clause is what makes this a security control rather
 * than a convenience: an open redirect at the end of an OIDC flow is how a phishing chain ends, because the
 * victim really did sign in, so whatever page they land on inherits the trust of a completed login.
 *
 * WHAT IS STORED (T24-D8). A **router-relative path with a leading slash** - `/home`, `/account/sessions` - which
 * is exactly what `useLocation().pathname` yields inside a router with a basename (measured: `/callback`, not
 * `/app/callback`, while `router.state.location.pathname` is the qualified form). So the one API the future caller
 * will have hands over the right shape with no conversion, and `navigate()` takes it back unchanged.
 *
 * THE VALUE IS UNTRUSTED ON READ, whoever wrote it (T24-D3). Same-origin code put it there, but a stale value
 * from an older build, a hand-edited store and a future caller passing a full URL are all reachable, and the read
 * is the only place the decision can be made once. So the check is **set membership** - not a prefix test, not a
 * pattern match, not a normalisation pass. `/app/home` is not a member and falls back; so does `/app/homeward`,
 * `//evil.example`, a scheme-qualified `evil.example` address, `javascript:alert(1)`, `\\evil`, `/home/../admin` and `/home?x=1`.
 * There is nothing to strip and nothing to decode, which is the point: every one of those is wrong in a different
 * way, and membership refuses them all for the same reason. (The examples are written without a scheme on
 * purpose: `bundleContainsNoSecretEnvKeys` forbids a scheme-qualified absolute address anywhere in production
 * source, comments included, under L9, L10 and R58.)
 *
 * WHAT IS NOT HONOURED, said here rather than discovered later:
 * - **Parameterised routes** (T24-D5). `invoices/:id` and `receipts/:id` carry a `:` segment, so no exact path
 *   can match them. Those are precisely the two routes notification emails link to, so a visitor who clicks an
 *   invoice email, signs in, and should return to that invoice lands on home instead. Honouring them means
 *   pattern matching, and with it traversal and encoded-separator handling; it is a decision with a price, and it
 *   has not been taken.
 * - **A query string or fragment** (T24-D6). No caller needs one today, and allowing one means deciding what may
 *   appear in it.
 *
 * WHERE THE VISITOR ACTUALLY LANDS (T24-D11). `/app/home` is registered in the route table with **no element**,
 * and no task in the package builds a home screen, so the fallback is a blank route today. The navigation is
 * real; what renders afterwards is someone else's task.
 */
export const RETURN_TARGET_KEY = 'themistra.auth.return_to';

/**
 * Every name from `routes.tsx` is read INSIDE a function, never at module scope.
 *
 * This closes the THIRD strand of the import cycle: `routes -> CallbackScreen -> returnTarget -> routes`. The
 * route table imports `CallbackScreen` before it declares `AUTHENTICATED_ROUTE_PATHS`, so a module-scope read
 * here touches the binding inside its temporal dead zone. Measured in T16: Vite yields `undefined` rather than
 * throwing - and `undefined` here is the worst of both worlds, because an empty allowlist **fails closed
 * silently**: every target would be rejected, the SPA would look like it worked, and nothing would ever be
 * honoured again. A test scans this file for a module-scope use for that reason.
 */
function allowlistedTargets(): Set<string> {
  return new Set(
    AUTHENTICATED_ROUTE_PATHS.filter((path) => !path.includes(':')).map((path) => `/${path}`),
  );
}

/** The fallback, derived rather than spelled, so the home route has one spelling in the package. */
function homeTarget(): string {
  return `/${HOME_PATH}`;
}

/**
 * Whether a target may be navigated to. Exported so T25's rejection table exercises the real predicate rather
 * than rebuilding the allowlist beside it (F6).
 */
export function isAllowlistedTarget(target: string): boolean {
  return allowlistedTargets().has(target);
}

/**
 * Remembers where the visitor was going, for the sign-in that is about to start.
 *
 * Nothing in production calls this yet: R28's redirect (task 41) is its caller, and this exists so that task is a
 * one-line call rather than a design (T24-D2). The input is typed `string` so a non-string caller is a compile
 * error, and it is stored as given - the decision about whether it may be honoured belongs to the read, which is
 * the trust boundary, and making it twice would be two code paths for one rule.
 */
export function rememberReturnTarget(target: string): void {
  try {
    globalThis.sessionStorage.setItem(RETURN_TARGET_KEY, target);
  } catch {
    // A target that cannot be stored is a visitor who lands on home: worse than being returned where they were,
    // and not a failed sign-in. The alternative - letting this throw - would end the journey before it started.
  }
}

/**
 * Takes the remembered target, validates it, deletes it, and returns somewhere safe to go.
 *
 * **It returns a destination, never an option.** No caller then has to decide what a rejected target means, so
 * there is one place the fallback can be wrong instead of one per call site.
 *
 * **Read before delete, by construction** (AC7): the value is in hand before the removal, so no arrangement of
 * these statements can delete before reading. Single-use, which is L16's own word for it, and deleted at
 * callback - by the module that owns its reader rather than by `consumeAuthorizeState`, because the three PKCE
 * items are deleted for having been consumed by the exchange and this one for having been read by the navigation.
 * Different consumers, different moments (T24-D4).
 *
 * **It never throws** (T24-D10). `CallbackScreen` calls it inside the exchange's fulfilment handler, which is
 * `.then(onFulfilled, discard)` - and a synchronous throw from `onFulfilled` is NOT caught by `discard`, as T21
 * measured. An uncaught throw here would blank the page at the end of a successful sign-in, after the visitor has
 * already authenticated: the worst moment this application has to offer. So the whole body is guarded and every
 * failure - a store that throws on access, a corrupted value, anything unforeseen - yields the home path.
 *
 * The removal is guarded separately so that a store which answers the read and then refuses the removal still
 * returns the validated target (T24-D9). The target then survives in storage, which L16 would rather it did not;
 * the next `rememberReturnTarget` overwrites it, and the visitor's outcome does not change.
 */
export function takeReturnTarget(): string {
  try {
    const stored = globalThis.sessionStorage.getItem(RETURN_TARGET_KEY);
    // Nothing remembered, nothing to consume, and so no removal: a sign-in that began at the sign-in screen
    // leaves this store exactly as it found it. Measured rather than stylistic - an unconditional removal makes
    // a FOURTH removal on every successful callback, for a value that was never written, and three inherited
    // assertions about exactly which keys a sign-in removes are right to object to it.
    if (stored === null) return homeTarget();
    try {
      globalThis.sessionStorage.removeItem(RETURN_TARGET_KEY);
    } catch {
      // Read and not removable: see T24-D9. The journey continues.
    }
    // Removed whether or not it survives validation: a rejected target was still used up by this callback, and
    // leaving it would hand it to the NEXT sign-in - a target that can neither be honoured nor got rid of.
    return isAllowlistedTarget(stored) ? stored : homeTarget();
  } catch {
    return homeTarget();
  }
}
