"""T26 mutation harness: every mutant puts something in a store that L16 does not permit.

!!! THIS SCRIPT EDITS PRODUCTION SOURCE AND REVERTS WITH `git checkout -- src/auth/`. !!!
!!! COMMIT OR STASH EVERYTHING UNDER frontend/src/auth/ BEFORE RUNNING IT.            !!!
Uncommitted edits in that directory are destroyed silently. That has cost real work twice in
this package (19 test cases in T14, 5 in T15), which is why the warning is this loud.

    python3 -I .ai/prompts/frontend/T26/artifacts/07-mutation-harness.py

Run it from the repository root; it locates `frontend/` relative to its own path. It needs
`npx` and takes a few minutes - each mutant costs two vitest runs.

WHY THE LIST LOOKS LIKE THIS. T26 guards an EXHAUSTIVE claim, so a mutant is not a wrong
answer - it is an extra item. The families are the ways something gets into a store it should
not be in: a fifth key (MA1-MA4), a third writing module or a reader that starts writing
(MA5-MA6), the three indirect call forms a text scan misses (MA7-MA9), a wrong store or a wipe
(MA10-MA13), a forbidden surface named (MA14-MA16), a secret hidden under a legitimate key
(MA17-MA18), the transient write that leaves a clean store behind it (MA19-MA20), and two
written to ESCAPE (MA21-MA22). MA12 is the `clear()` mutant that escaped T25's own rejection
rows; it is here because this file is the one that should catch it. MA22 is the dormant write
with a computed method name: it was killed only by a NEIGHBOUR until the namespace scan landed,
and that one column change is why the third instrument exists.

THE BASELINE TO EXPECT, so a run can be read without the artifacts: auth suite 726 passed / 0
failed, T26's own file 50 / 0, every mutant KILLED and every one of them `own`. A SURVIVED or an
INHERITED-ONLY line is a finding.

HOW TO READ THE OUTPUT. Two columns, two different claims. The verdict says the mutant was
caught by SOMETHING in the auth suite; `own` says T26's own file caught it, and
`INHERITED-ONLY` says only its neighbours did - which for a guard whose whole purpose is the
package-level claim is a finding, not a pass. A mutant that makes a run unparseable is scored
BROKEN, not KILLED: read it by hand before calling it detected.
"""

import re
import subprocess
import sys
from pathlib import Path

# Derived from this script's own location: <repo>/.ai/prompts/frontend/T26/artifacts/ -> <repo>/frontend
ROOT = str(Path(__file__).resolve().parents[5] / "frontend")
A = "src/auth/authorizeRequest.ts"
R = "src/auth/returnTarget.ts"
C = "src/auth/CallbackScreen.tsx"
N = "src/auth/idTokenNonce.ts"

WRITE_THREE = """  session.setItem(AUTHORIZE_STORAGE_KEYS.state, state);
  session.setItem(AUTHORIZE_STORAGE_KEYS.nonce, nonce);
  session.setItem(AUTHORIZE_STORAGE_KEYS.codeVerifier, pkce.verifier);"""
CONSUME_LOOP = """  for (const key of Object.values(AUTHORIZE_STORAGE_KEYS)) {
    try {
      globalThis.sessionStorage.removeItem(key);
    } catch {"""
TARGET_WRITE = "    globalThis.sessionStorage.setItem(RETURN_TARGET_KEY, target);"
TARGET_REMOVE = "      globalThis.sessionStorage.removeItem(RETURN_TARGET_KEY);"
STATE_READ = "      stored = globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state);"
NONCE_READ = "    stored = globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.nonce);"

MUTANTS = [
    # --- a fifth key -------------------------------------------------------------------------
    ("MA1  a fifth key is written beside the three", A, WRITE_THREE,
     WRITE_THREE + "\n  session.setItem('themistra.auth.started_at', String(Date.now()));"),
    ("MA2  the verifier is written twice, under a second key", A, WRITE_THREE,
     WRITE_THREE + "\n  session.setItem('themistra.auth.verifier_backup', pkce.verifier);"),
    ("MA3  a key is written with a literal instead of the constant (rename drift)", A, WRITE_THREE,
     """  session.setItem('themistra.auth.state', state);
  session.setItem(AUTHORIZE_STORAGE_KEYS.nonce, nonce);
  session.setItem(AUTHORIZE_STORAGE_KEYS.codeVerifier, pkce.verifier);
  session.setItem('themistra.auth.state_v2', state);"""),
    ("MA4  the return target is written under a second key as well", R, TARGET_WRITE,
     TARGET_WRITE + "\n    globalThis.sessionStorage.setItem('themistra.auth.return_to_last', target);"),
    # --- a third writing module ---------------------------------------------------------------
    ("MA5  a third module writes (CallbackScreen records its own arrival)", C, STATE_READ,
     STATE_READ + "\n      globalThis.sessionStorage.setItem('themistra.auth.callback_seen', '1');"),
    ("MA6  a reader starts writing (idTokenNonce caches what it read)", N, NONCE_READ,
     NONCE_READ + "\n    globalThis.sessionStorage.setItem('themistra.auth.nonce_seen', String(stored));"),
    # --- the three indirect call forms a text scan misses -------------------------------------
    ("MA7  a fifth key via bracket notation", A, WRITE_THREE,
     WRITE_THREE + "\n  (session as unknown as Record<string, (k: string, v: string) => void>)['setItem']('themistra.auth.bracket', state);"),
    ("MA8  a fifth key via a bound alias", A, WRITE_THREE,
     WRITE_THREE + "\n  const put = session.setItem.bind(session);\n  put('themistra.auth.bound', state);"),
    ("MA9  a fifth key via Storage.prototype.setItem.call", A, WRITE_THREE,
     WRITE_THREE + "\n  Storage.prototype.setItem.call(session, 'themistra.auth.called', state);"),
    # --- the wrong store, or a wipe -----------------------------------------------------------
    ("MA10 the nonce is mirrored into localStorage", A, WRITE_THREE,
     WRITE_THREE + "\n  globalThis.localStorage.setItem(AUTHORIZE_STORAGE_KEYS.nonce, nonce);"),
    ("MA11 the return target is written to localStorage instead", R, TARGET_WRITE,
     "    globalThis.localStorage.setItem(RETURN_TARGET_KEY, target);"),
    ("MA12 the target removal becomes a whole-store clear (T25's escapee)", R, TARGET_REMOVE,
     "      globalThis.sessionStorage.clear();"),
    ("MA13 consumeAuthorizeState clears the whole store", A, CONSUME_LOOP,
     """  for (const key of Object.values(AUTHORIZE_STORAGE_KEYS)) {
    try {
      globalThis.sessionStorage.clear();
      void key;
    } catch {"""),
    # --- a forbidden surface ------------------------------------------------------------------
    ("MA14 a module names indexedDB", R, TARGET_WRITE,
     TARGET_WRITE + "\n    void indexedDB;"),
    ("MA15 a module reads document.cookie", R, TARGET_WRITE,
     TARGET_WRITE + "\n    void document.cookie;"),
    ("MA16 a module names navigator.storage", A, WRITE_THREE,
     WRITE_THREE + "\n  void navigator.storage;"),
    # --- a secret under a legitimate key -------------------------------------------------------
    ("MA17 the state key carries the authorization code too", C, STATE_READ,
     STATE_READ + "\n      globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.state, `${stored ?? ''}|${new URLSearchParams(window.location.search).get('code') ?? ''}`);"),
    ("MA18 the return target carries the verifier", R, TARGET_WRITE,
     "    globalThis.sessionStorage.setItem(RETURN_TARGET_KEY, `${target}?v=${globalThis.sessionStorage.getItem('themistra.auth.code_verifier') ?? ''}`);"),
    # --- the transient write that leaves a clean store -----------------------------------------
    ("MA19 a fifth key is written and removed in the same breath", A, WRITE_THREE,
     WRITE_THREE + "\n  session.setItem('themistra.auth.transient', state);\n  session.removeItem('themistra.auth.transient');"),
    ("MA20 a module removes a key it does not own", R, TARGET_REMOVE,
     TARGET_REMOVE + "\n      globalThis.sessionStorage.removeItem('themistra.auth.nonce');"),
    # --- the two written to ESCAPE, so the limitation is measured rather than assumed ----------
    # MA21 hides the method name from the text scan by computing it. The scan's patterns need the
    # literal `setItem`, so only the runtime watch can see this one - and it does, because the
    # write sits on a path every flow drives. MA22 is the same write behind a condition no test
    # produces: the scan cannot see the name and the watch never runs. If MA22 survives, that is
    # the honest boundary of this file, and the self-review says so rather than discovering it later.
    ("MA21 a fifth key via a COMPUTED method name, on a driven path", A, WRITE_THREE,
     WRITE_THREE + "\n  const method = 'set' + 'Item';\n  (session as unknown as Record<string, (k: string, v: string) => void>)[method]('themistra.auth.computed', state);"),
    ("MA22 the same write behind a condition no test produces", A, WRITE_THREE,
     WRITE_THREE + "\n  const method = 'set' + 'Item';\n  if (globalThis.navigator.userAgent.includes('ZZZ-NEVER-MATCHES')) {\n    (session as unknown as Record<string, (k: string, v: string) => void>)[method]('themistra.auth.dormant', state);\n  }"),
]

FILES = [
    "src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx",
    "src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx",
    "src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx",
    "src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx",
    "src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx",
    "src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx",
    "src/auth/callbackValidatesNonce.test.tsx",
    "src/auth/authorizeRequestsExactlyTheDeclaredScopes.test.ts",
]

T26_ONLY = ["src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx"]


def run(files):
    proc = subprocess.run(["npx", "-y", "pnpm@12.14.0", "exec", "vitest", "run"] + files,
                          cwd=ROOT, capture_output=True, text=True)
    out = proc.stdout + proc.stderr
    m = re.search(r"Tests\s+(?:(\d+) failed \| )?(\d+) passed(?: \| (\d+) todo)?\s+\((\d+)\)", out)
    if not m:
        return None, None, proc.returncode
    return int(m.group(1) or 0), int(m.group(4)), proc.returncode


subprocess.run(["git", "checkout", "--", "src/auth/"], cwd=ROOT, check=True)
base_failed, base_total, base_code = run(FILES)
own_base_failed, own_base_total, own_base_code = run(T26_ONLY)
print(f"ROOT = {ROOT}\n")
print(f"baseline all-auth: {base_failed} failed of {base_total}, exit {base_code}")
print(f"baseline T26 file: {own_base_failed} failed of {own_base_total}, exit {own_base_code}\n")

# An optional substring filter, so one family can be re-measured without paying for all of them.
only = sys.argv[1] if len(sys.argv) > 1 else None

survivors = []
inherited_only = []
for name, path, old, new in MUTANTS:
    if only is not None and only not in name:
        continue
    s = open(f"{ROOT}/{path}").read()
    if old not in s:
        print(f"  !! {name}: pattern not found")
        survivors.append(name + " (NOT APPLIED)")
        continue
    open(f"{ROOT}/{path}", "w").write(s.replace(old, new, 1))
    failed, total, code = run(FILES)
    own_failed, own_total, own_code = run(T26_ONLY)
    subprocess.run(["git", "checkout", "--", path], cwd=ROOT, check=True)
    if failed is None or total is None:
        verdict = "BROKEN"
    elif failed > base_failed or total != base_total or code != base_code:
        verdict = "KILLED"
    else:
        verdict = "SURVIVED"
    caught_by_own = (
        own_failed is None
        or (own_failed or 0) > (own_base_failed or 0)
        or own_total != own_base_total
        or own_code != own_base_code
    )
    alone = "own" if caught_by_own else "INHERITED-ONLY"
    if verdict != "KILLED":
        survivors.append(f"{name} [{verdict}]")
    elif not caught_by_own:
        inherited_only.append(name)
    print(f"  {verdict:9} {alone:14} {name}  ({failed} failed of {total}, exit {code})")

print("\nsurvivors:", survivors or "none")
print("killed only by a neighbour:", inherited_only or "none")
subprocess.run(["git", "checkout", "--", "src/auth/"], cwd=ROOT, check=True)
