<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) -->

# frontend · T35 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T35 — Sign-out clears memory |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Findings

### F1 — The sign-out function has no production caller
· **Severity:** **Blocker** (scope / process)
· **Evidence:** The brief states "Nothing in the SPA offers sign-out" and OQ-T35-2 says T35 joins tasks 5, 6, 30, 31 and 33 as "built and uncalled". `AppShell.tsx` renders a bare `<div>` and no authenticated route has an element.
· **Assessment:** `tasks.md:63` says "Sign-out clears in-memory token state. Verifies `signOutClearsMemoryAndEndsSasSession`." The brief interprets this as building the clearing half only, leaving the button/affordance unowned. But a function that is never called does not clear anything in production. This is the same substance as OQ-T33-2 and is honest, but it means the task's deliverable is dormant until an unscheduled wiring task appears.
· **Recommended brief amendment:** Either (a) identify the task that will call `signOut()`, (b) have T35 include a minimal caller (e.g., a sign-out button in `AppShell.tsx` or an authenticated route element), or (c) record explicitly that AC1–AC6 are verified only by direct invocation in tests and cannot be exercised end-to-end until the caller is built.

### F2 — AC7's source-side inventory scan is brittle and under-specified
· **Severity:** **Major/Blocker**
· **Evidence:** AC7 requires "a scan over `src/` enumerates the package's module-level mutable state and L16's carve-out keys and fails if one exists that sign-out does not clear."
· **Assessment:** The brief does not say how the scan enumerates these two inventories. For module-level mutable state, the existing pattern is `/^(let|var)\s/m`, which catches `accessToken.ts:24` and would need a hard-coded allowlist for that file. It does **not** catch mutable values held in `const` objects, closure variables (e.g., `crossTabRenewal.ts:90-93`), or `Map`/`Set` instances created inside exported factories but retained per module. For L16 keys, the scan must discover `themistra.auth.*` keys from `sessionStorage.setItem`/`getItem`/`removeItem` calls across `src/`, but keys can be constructed dynamically, stored in constants, or hidden behind helpers. False positives and false negatives are both likely.
· **Recommended brief amendment:** Replace the open-ended scan with two concrete, falsifiable instruments: (a) a source scan that asserts `signOut.ts` calls exactly the four clearing primitives and no other state-mutating APIs, and (b) a maintained inventory list in `signOut.ts` or its test that names the five pieces of state and the primitive that clears each one. If a future task adds state, it must edit that list, which is a deliberate handoff rather than a discovery problem.

### F3 — `takeReturnTarget()` is the wrong primitive for sign-out
· **Severity:** **Major**
· **Evidence:** The brief's dependency table says sign-out clears the return target by calling `takeReturnTarget()`. `returnTarget.ts:120-139` returns a destination string (validated, with fallback to home) and removes the key as a side effect.
· **Assessment:** Sign-out does not need a destination. Calling `takeReturnTarget()` makes the reader ask "what happens to the returned string?" and forces the function to compute `homeTarget()` even when storage throws. A `clearReturnTarget()` primitive that only removes the key would be cleaner and would match `consumeAuthorizeState()`'s semantics. The brief forbids editing `returnTarget.ts`, but the right design may be to add one exported function there rather than misuse the reader.
· **Recommended brief amendment:** Add `clearReturnTarget()` to `returnTarget.ts` or relax the "Files NOT to Modify" rule for this additive export. If the brief insists on `takeReturnTarget()`, add a comment in `signOut.ts` explicitly discarding the return value and explaining why.

### F4 — The dormancy canary is backwards
· **Severity:** **Major**
· **Evidence:** The brief's Required Tests include "A **dormancy canary**, as T33's AC12: no production module calls sign-out today; the day one does, a case fails and its message hands that author the next step."
· **Assessment:** A test that fails when a production module correctly calls sign-out is not a canary — it is an obstacle. T33's dormancy canary was for an environmental assumption (no authenticated route has an element), which is appropriate because the day it stops being true, another task must act. Here, the desired event is that sign-out **becomes** called; failing on that event punishes the author who wires it up.
· **Recommended brief amendment:** Replace the failing canary with a source scan that asserts no production caller currently exists, reported as an informational note rather than a failing test. When a caller is added, the scan is updated or removed.

### F5 — Cross-tab renewal closure state is not cleared
· **Severity:** **Major**
· **Evidence:** `api/crossTabRenewal.ts:90-93` holds `outcomeCount`, `lastOutcome`, `waiters`, and `closed` in the closure of `shareRenewalAcrossTabs`. After sign-out, a tab could still receive a `renewal:succeeded` message on its `BroadcastChannel` and adopt a new token via `local.adopt()` (`:102-103`).
· **Assessment:** R19 cl. 1 says sign-out clears in-memory token state. The cross-tab channel is in-memory state that can re-establish a session without the visitor's action. The brief says task 39 owns broadcasting sign-out to siblings, but task 39 is about **telling** siblings to clear, not about **this tab** stopping its own listener. If this tab signs out and its channel is still open, it can be re-authenticated by a sibling that has not yet received the broadcast.
· **Recommended brief amendment:** Define whether T35 closes the local `SharedRenewal` (by calling `close()`) or whether that is task 39's responsibility. If `signOut()` takes no parameters, it cannot reach the `SharedRenewal` instance; either the instance must be reachable (e.g., via a global or injected dependency) or the boundary/renewal wiring must register a sign-out callback.

### F6 — AC6 undercounts the storage operations
· **Severity:** **Minor/Major**
· **Evidence:** AC6 says "the only storage operations are the four removals its primitives perform." The four primitives are `clearAccessToken()`, `takeSignInNotice()`, `consumeAuthorizeState()`, and `takeReturnTarget()`.
· **Assessment:** `consumeAuthorizeState()` performs three `removeItem` calls. `takeReturnTarget()` performs one `getItem` and one `removeItem` (plus a possible second guarded `removeItem`). The total is therefore **one read and up to five removals**, not "four removals." The brief's wording is imprecise and could cause a test author to write an assertion that fails against correct code.
· **Recommended brief amendment:** Rewrite AC6 to say "the only storage operations are the reads and removals performed by the four clearing primitives" and enumerate them.

### F7 — L16's "mid-task form input" creates a conflict with AC7
· **Severity:** **Minor/Major**
· **Evidence:** L16's carve-out is "`state`, `nonce`, `code_verifier`, the return target and mid-task form input." The brief's inventory lists only the first four and says mid-task form input is task 26's.
· **Assessment:** AC7 says the scan "fails if one exists that sign-out does not clear." If task 26 already has a key (or is added before T35 runs), AC7 fails unless mid-task form input is in the exclusion list. The brief mentions "A declared exclusion list, with a justification per entry, is the only permitted escape," but it does not list the mid-task form input key as an excluded item.
· **Recommended brief amendment:** Pre-authorize the mid-task form input key in the exclusion list with the justification that task 26 owns it and sign-out must not destroy a visitor's in-progress form. If the key does not yet exist, document the expected key name so the scan can ignore it when it appears.

### F8 — The inventory scan cannot detect closure state
· **Severity:** **Minor/Major**
· **Evidence:** AC7 says "module-level mutable state," which the existing scan defines as top-level `let`/`var`. `crossTabRenewal.ts` keeps state in function closures; `sessionBoundary.ts` keeps a `pending` Map inside `createSessionBoundary`.
· **Assessment:** These are not module-level declarations, but they are per-instance state that persists for the tab's lifetime. Sign-out cannot clear them without a reference to the instance. AC7's wording therefore promises more than it can enforce.
· **Recommended brief amendment:** Narrow AC7 to "module-scope declarations" and add a separate criterion that any per-instance state holder must register a cleanup hook with sign-out. Or, more pragmatically, scope AC7 to the five known pieces of state and document the limitation.

### F9 — The test file name conflates T35 and T36
· **Severity:** **Minor**
· **Evidence:** The brief creates `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx`, citing `tasks.md:63`. The name includes "AndEndsSasSession", which is task 36.
· **Assessment:** Task 36 extends the same file, so the name is forward-looking. But during T35 the file contains only the clearing half, which makes the name misleading. A reader running the file will expect end-session tests.
· **Recommended brief amendment:** Either create the file with a T35-only name now and rename it in task 36, or accept the shared name and add a prominent comment that T35 owns only the "clears memory" half.

### F10 — `consumeAuthorizeState()` is named for the callback path, not sign-out
· **Severity:** **Minor**
· **Evidence:** `authorizeRequest.ts:100-101` says it "Removes the three single-use items the authorize step stored - what L16 calls 'deleted at callback'."
· **Assessment:** Reusing the function for sign-out is correct because it is idempotent and removes exactly the right keys. But the name and doc comment frame it as callback cleanup. A future maintainer might refactor it under the assumption that only the callback calls it, breaking sign-out silently.
· **Recommended brief amendment:** Update the comment in `consumeAuthorizeState()` to name sign-out as an additional caller, or rename it to `clearAuthorizeState()` if the brief allows editing `authorizeRequest.ts` comments. At minimum, `signOut.ts` should comment why it calls this primitive.

### F11 — Pending API calls are not addressed
· **Severity:** **Minor**
· **Evidence:** R19 cl. 1 clears in-memory token state. The brief does not mention in-flight requests.
· **Assessment:** A request already dispatched with the old token will still reach the server after sign-out. This is not a half-authenticated tab (the tab has cleared its token), but it is a token that outlives the visitor's session intent. Aborting in-flight requests is complex and may be out of scope, but the brief should say so explicitly.
· **Recommended brief amendment:** Add a note that T35 does not abort in-flight requests; that behavior, if required, is owned by a future task.

### F12 — The `.tsx` extension is unnecessary for T35
· **Severity:** **Informational**
· **Evidence:** The brief creates a `.tsx` test file, but the clearing function and its tests use no JSX.
· **Assessment:** The extension is justified by task 36's future extension, but it forces the T35 suite to use the React transform unnecessarily. This is minor.
· **Recommended brief amendment:** None required, but document why the extension is `.tsx`.

---

## Summary

The brief correctly identifies the five pieces of state and the four primitives that own them, and it rightly keeps task 36's navigation and task 39's broadcasting out of scope. The largest risks are **F1** (uncalled function), **F2** (under-specified source scan), **F3** (wrong primitive for return target), and **F5** (cross-tab listener not closed). AC7 as written promises an automatic inventory that the proposed scan pattern cannot reliably deliver; narrowing it to a maintained list or splitting it into scan + explicit inventory would make it falsifiable. The dormancy canary should not fail on the correct event of sign-out being wired.

**Phase 3 complete.** Twelve findings returned; no code or contract changed.
