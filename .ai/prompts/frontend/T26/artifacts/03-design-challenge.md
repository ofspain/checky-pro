# frontend · T26 · Phase 3 — Design Challenge

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T26 — Session storage holds only L16 items |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the Phase 2 task implementation brief. T26 is verification-only: one new test file that asserts sessionStorage is exhausted by the four L16 keys. The brief is tight, but the source-scan instruments have edges that a determined evasion could use, and two required-test lists are missing a couple of realistic cases.

---

## F1 · The source scan for storage method calls can miss `.call`, `.apply`, `.bind` and bracket notation

**Severity.** Medium.

**Evidence.**
- AC3 requires that every production module calling `setItem`, `removeItem` or `clear` on a storage object is one of the two permitted modules.
- Required Test 4 covers alias resistance for `globalThis['sessionStorage']` and `const s = sessionStorage`.
- It does not cover `s.setItem.call(s, key, value)`, `Storage.prototype.setItem.apply(s, [key, value])`, `const set = s.setItem.bind(s)`, or `s['setItem'](key, value)`.

**Why it matters.** A source scan that looks for `.setItem(`, `.removeItem(` and `.clear(` will miss these forms. They are unusual in application code, but a test whose entire value is "report dirty" must prove its instrument against synthetic violations that look like real evasions, not only obvious ones.

**Recommended brief amendment.** Extend Required Test 4 (alias resistance) to include:
- a synthetic file using `sessionStorage['setItem'](key, value)`;
- a synthetic file using `const set = sessionStorage.setItem.bind(sessionStorage)`;
- a synthetic file using `Storage.prototype.setItem.call(sessionStorage, key, value)`.

Each should fail the scan, with a control proving the scan still permits the legitimate `authorizeRequest.ts` and `returnTarget.ts` patterns.

**Confidence.** Medium — the forms are rare, but the scan is the core of AC3.

---

## F2 · The value sweep omits the authorization code

**Severity.** Medium.

**Evidence.**
- AC5 lists "whole access token, whole ID token, each of its three segments, each decoded claim value, a synthetic `refresh_token`, a password, a recovery code, an API key".
- The authorization code (`code=THE-CODE` in T22's flow) is not listed.

**Why it matters.** The authorization code is a single-use credential and is present in the callback URL. T22's leak sweep already asserts it never reaches storage, logs or surfaces. T26 re-asserts the storage half over its own flows, but if the code is not in the value inventory, a regression that stored the code under an in-inventory key (e.g., disguised as `state`) would pass AC5.

**Recommended brief amendment.** Add the authorization code to AC5 and Required Test 5's value sweep.

**Confidence.** Medium — L3 and T22 already forbid it, but T26's own instrument should look for it.

---

## F3 · No required test exercises the inventory sweep when `getItem` throws

**Severity.** Medium.

**Evidence.**
- The brief's "Null / absence handling" constraint says `getItem` may throw rather than return `null`, and the guard must distinguish "empty" from "unreachable".
- Required Test 2 lists "a store that throws" as one discard path, but that is about the flow completing, not about the inventory sweep itself.
- There is no required test that installs a throwing `getItem` and asserts the inventory sweep does not silently pass.

**Why it matters.** If the sweep catches the `getItem` throw and treats it as "no keys", an unreachable store would look compliant. The test must fail or at least report unreachable, not empty.

**Recommended brief amendment.** Add a required test: with a `sessionStorage` whose `getItem` throws, the inventory sweep either throws/fails or reports "store unreachable", and does not report "subset of inventory".

**Confidence.** Medium — directly supports the stated constraint.

---

## F4 · The four never-named surfaces may not be exhaustive

**Severity.** Low.

**Evidence.**
- AC7 names `indexedDB`, `document.cookie`, `cookieStore` and `caches`.
- Other client-side storage surfaces exist: `navigator.storage` (StorageManager / Origin Private File System), `navigator.webkitTemporaryStorage`, `FileSystemFileHandle`, etc.

**Why it matters.** L16 says "Nothing else client-side". If the scan only covers four surfaces, a future module could write to `navigator.storage.getDirectory()` and the structural scan would not name it.

**Recommended brief amendment.** Either add `navigator.storage` to the never-named surfaces in AC7, or explicitly document in the Open Questions that `design.md` §5 governs the list and any new surface requires a design decision. The test should still scan for `navigator.storage` as a defensive measure.

**Confidence.** Low — no production caller exists today, but the claim "nothing else client-side" is absolute.

---

## F5 · The source scan conflates "names a surface" with "calls a method on it"

**Severity.** Low.

**Evidence.**
- AC3 says "calls `setItem`, `removeItem` or `clear` on a storage object".
- AC7 says "No production module names `indexedDB`, `document.cookie`, `cookieStore` or `caches`".
- These are different prohibitions. A module that logs `console.log('caches disabled')` names `caches` but does not call it. A scan that flags any occurrence would reject that module.

**Why it matters.** The test's failure message should be accurate: a surface name in a string or non-comment context is suspicious and probably worth flagging, but the assertion should be worded as "names" (AC7) rather than "calls". AC3 should remain about method calls.

**Recommended brief amendment.** Keep AC7 as a name scan (with comments stripped) and AC3 as a method-call scan, and report them with different messages. The Required Test 7 controls should include a synthetic file that names a forbidden surface without calling it, proving the name scan fires.

**Confidence.** Low — wording and test design, not a behaviour defect.

---

## F6 · No required test verifies `removeItem` identity by module

**Severity.** Low.

**Evidence.**
- AC3 says permitted modules write only their own keys. It does not explicitly say they remove only their own keys.
- `CallbackScreen.tsx` triggers removal by calling `consumeAuthorizeState()`, but the actual `removeItem` call is inside `authorizeRequest.ts`.

**Why it matters.** A future refactor could move a removal into a third module without the scan noticing, as long as the write scan still sees only two modules. The brief's purpose is to pin the entire inventory surface; removals are part of that surface.

**Recommended brief amendment.** Add to AC3: "and removes only keys in the inventory" (implied already, but make it explicit). Add to Required Test 3 or 7: assert that every `removeItem` key is in the inventory, and that the two permitted modules are the only ones that call `removeItem` on a storage object.

**Confidence.** Low — implied by AC1/AC4, but worth stating.

---

## F7 · No required test covers a write that is later removed under an alias key

**Severity.** Low.

**Evidence.**
- AC4 says a write-then-delete is still a violation.
- The write-path assertion (Required Test 3) watches every `setItem` key.

**Why it matters.** A bug or malicious write might store under `themistra.auth.state_backup` and then remove it. The write-path watch catches the `setItem`, but only if the instrument is installed before the write and not reset. The controls (Required Test 8) prove the instrument ordering; an additional control that writes and then removes an out-of-inventory key would prove the write-path assertion survives the removal.

**Recommended brief amendment.** Add to Required Test 8: a synthetic flow that writes an out-of-inventory key and then removes it; the sweep must still fail on the write.

**Confidence.** Low — already implied by AC4, but a control makes it durable.

---

## F8 · The repetition test should include a discard followed by a fresh sign-in

**Severity.** Low.

**Evidence.**
- Required Test 9 says "two sign-ins in one tab, and a replayed callback, add no fifth key."
- It does not explicitly cover a discard followed by a fresh sign-in.

**Why it matters.** A discard leaves PKCE items in storage (T23-D2). A fresh sign-in overwrites them. This is a realistic sequence and ensures the inventory is still exactly the four keys during the transition.

**Recommended brief amendment.** Extend Required Test 9 to include: discard → fresh sign-in → complete callback, with inventory assertions after each step.

**Confidence.** Low — coverage strengthener.

---

## Summary

F1 and F2 are the strongest: they close real holes in the source-scan instrument and the value sweep. F3 supports a stated constraint that has no dedicated required test. F4-F8 are smaller completeness and documentation items. No production change is anticipated; all recommendations are additions to the test plan.
