# frontend · T09 — Phase 3 — Design Challenge

## Scope and method

This is an adversarial review of `artifacts/02-task-implementation-brief.md` for **T09 — Denylisted navigations bypass the worker**. Findings are against the brief itself; no code was changed.

---

## Findings

### 1. The module's exact Workbox interface is not specified
**Issue:** The brief says the module must be "shaped to plug into a Workbox navigation route (a `denylist`-style predicate)", but it also says the module is a pure function with no `fetch`, no `caches`, no `self`. Workbox's `NavigationRoute` expects a `denylist`/`allowlist` of `RegExp[]` *or* a `Route` with a `matchCallback`; a plain boolean function cannot be dropped directly into `new NavigationRoute(handler, { denylist })`. The intended API shape matters for how T12 wires it.  
**Severity:** High — ambiguity now will force a breaking change when the worker is turned on.  
**Evidence ·** Brief §Locked Decisions `L13`; §Files to Create `navigationDenylist.ts`; §Inputs/Outputs "A boolean decision (handle / decline)."  
**Recommendation ·** Define the module exports explicitly in the frozen brief. A good shape is:
```ts
export const DENYLISTED_NAVIGATION_PREFIXES: readonly string[];
export function isDenylistedNavigation(url: URL, mode: string): boolean;
export function isShellNavigation(url: URL, mode: string): boolean;
```
Add a note that `isShellNavigation` is the Workbox `matchCallback` form (`/app/*` navigations that are not denylisted), and `isDenylistedNavigation` is the `denylist` predicate form. Both are pure and testable.

### 2. The "cache read before denylist check" mutation break has no target
**Issue:** The brief lists a mutation break: "a cache read before the denylist check." If the decision module is pure and never references `caches`, there is no production code to mutate. The break can only be applied to a worker handler that does not exist in T09 (emission/registration is task 12).  
**Severity:** Medium — either the break is misplaced or the module's scope needs to grow.  
**Evidence ·** Brief §Scope: "a single-purpose module that decides ... whether the worker handles it"; §Mutation breaks; §Files to Create: no worker handler file.  
**Recommendation ·** Either (a) remove the cache-order mutation break from T09 and assign it to task 11/12, or (b) add a minimal `createNavigationHandler(match, shell)` factory to T09 that takes a cache strategy and *uses the denylist first*. Option (b) reintroduces a `caches` reference, so the brief's "pure" and "no cache" constraints would need to be relaxed to "no global cache reads; cache passed as a strategy." Keep the decision function pure either way.

### 3. Prefix matching boundary is unspecified
**Issue:** The brief says the eleven entries are "prefixes" and AC1 says sub-paths are declined. It does not say how to avoid false positives such as `/accountsxyz` matching the `/accounts` prefix, or `/application` matching `/app`. A naive `String.prototype.startsWith` would over-match.  
**Severity:** Medium — could denylist unintended paths as soon as the worker scope widens.  
**Evidence ·** Brief §AC1, AC4.  
**Recommendation ·** Define matching as a path-segment prefix: a denylisted path `p` matches a navigation to `p` *or* `p + '/' + ...*. Equivalently, use `new RegExp('^' + escapeRegex(p) + '(?:/|$)')`. Add AC4 rows for `/accountsxyz` (not denylisted) and `/application` (not shell) as negative cases.

### 4. `/app` without a trailing slash is not covered
**Issue:** AC2 names `/app/` and `/app/sign-in` as shell navigations. It does not say whether a navigation to `/app` (no trailing slash) should be handled as a shell route, redirected, or left undefined.  
**Severity:** Low — the edge currently routes only `/app*` to the SPA, so `/app` would reach the SPA.  
**Evidence ·** Brief §AC2.  
**Recommendation ·** Decide and document: treat `/app` the same as `/app/` (shell), using the segment-boundary rule `/app(?:/|$)`. Add a test case for `/app`.

### 5. The drift-guard parser for `agents.md` is unspecified
**Issue:** AC7 requires the code list to equal the list parsed from `agents.md`. The list lives inside a prose bullet paragraph (`agents.md:88-89`) with paths in backticks and an "or" before the last item. A line-based or naive regex parser could miss entries or break if the paragraph is rewrapped.  
**Severity:** Low — the file is stable, but the parser is a maintenance risk.  
**Evidence ·** Brief §AC7, §Dependencies.  
**Recommendation ·** Define the parser in the frozen brief: locate the `Navigation requests to these paths are never answered from cache` paragraph, extract every backtick-delimited path, and ignore the trailing `/` on each path before comparing. Use a robust regex such as ``/`(/[^`]+)`/g`` within that block.

### 6. Non-navigation requests are outside scope but the decision function may receive them
**Issue:** The module's function will likely be called from a Workbox `matchCallback` that only runs for navigation requests, but the function itself checks `mode === 'navigate'`. The brief says "navigation-only." It should be explicit what the function returns for non-navigations: `false` (decline) is the safe choice, so it does not quietly become the API policy.  
**Severity:** Low — defensive contract.  
**Evidence ·** Brief §Inputs: "A request's URL path and its `mode` (`'navigate'` or not)."; §Constraints: "navigation-only."  
**Recommendation ·** Add to the frozen brief: `isDenylistedNavigation` returns `false` for `mode !== 'navigate'`; `isShellNavigation` returns `false` for non-navigations. This prevents accidental reuse for API requests.

### 7. `/notifications` is not in R57 but is an API route
**Issue:** The brief notes this in Open Question 4. `/notifications` is an API route and will be NetworkOnly in task 11. It is not part of the navigation denylist. This is correct but should be locked so it is not added later.  
**Severity:** Info.  
**Evidence ·** Brief §Open Questions #4.  
**Recommendation ·** Record in the frozen brief: "The R57 list is copied verbatim; `/notifications` and other API routes are handled by task 11's NetworkOnly policy, not this denylist."

### 8. No test for the denylist order or comment
**Issue:** The constraints require the prefixes to be copied "exactly, in order, with a comment citing R57 and the file." There is no acceptance criterion or mutation break that checks the order or the comment.  
**Severity:** Info.  
**Evidence ·** Brief §Constraints: "Verbatim list."  
**Recommendation ·** Add an AC or test that asserts the exported array order matches the parsed `agents.md` order and that the source file contains a comment referencing `agents.md:88-89` and R57.

### 9. The test file will stub worker globals that may not be needed
**Issue:** The brief says tests use hand-stubbed worker globals. If the module is a pure function of `(url, mode)`, the tests only need a `URL` and a string; no `ServiceWorkerGlobalScope`, `FetchEvent`, or `caches` are required for the decision unit. Stubbing them may add noise.  
**Severity:** Info.  
**Evidence ·** Brief §Dependencies; §Inputs/Outputs.  
**Recommendation ·** Keep the tests focused on the pure function and the drift guard. Only stub `FetchEvent`/`caches` if the module scope grows to include a handler factory (finding 2).

---

## Open Questions

- Findings 1 and 2 are the most significant: the module's Workbox interface and the cache-order mutation break must be resolved before implementation starts.
- Findings 3 and 4 are matching/edge-case details that should be locked in AC4.
