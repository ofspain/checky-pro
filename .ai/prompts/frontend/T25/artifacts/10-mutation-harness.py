"""T25 mutation harness: every mutant is a MEMBERSHIP or FALLBACK error.

!!! THIS SCRIPT EDITS PRODUCTION SOURCE AND REVERTS WITH `git checkout -- src/auth/`. !!!
!!! COMMIT OR STASH EVERYTHING UNDER frontend/src/auth/ BEFORE RUNNING IT.           !!!
Uncommitted edits in that directory are destroyed silently. That has cost real work twice
in this package (19 test cases in T14, 5 in T15), which is why the warning is this loud.

    python3 -I .ai/prompts/frontend/T25/artifacts/10-mutation-harness.py

Run it from the repository root; it locates `frontend/` relative to its own path. It needs
`npx` and takes a few minutes - 25 mutants x 2 vitest runs each.

WHY THE LIST LOOKS LIKE THIS. It is written from the Phase 4 measurements rather than
generically. M1-M3 - case-folding, the trailing-slash strip, trimming - are the three whose
results **react-router would honour**: it matches paths case-insensitively and ignores a
trailing slash, and a trimmed leading-space value becomes an absolute path. A table that
does not kill those three proves nothing about the shapes that matter most.

HOW TO READ THE OUTPUT. Each line reports the verdict and, separately, whether T25's OWN
file caught the mutant or only an inherited T24/T23 case did. Those are different claims
and only the second column is T25's. A mutant that makes a run unparseable is scored BROKEN,
not KILLED: read it by hand before calling it detected.

M8 (`strips a leading slash from both sides`) survived the first run of this harness and is
the reason the `no leading slash` families exist. If it ever survives again, a family has
been deleted.
"""

import re, subprocess

from pathlib import Path

# Derived from this script's own location: <repo>/.ai/prompts/frontend/T25/artifacts/ -> <repo>/frontend
ROOT = str(Path(__file__).resolve().parents[5] / "frontend")
R = "src/auth/returnTarget.ts"
C = "src/auth/CallbackScreen.tsx"

HAS = "  return allowlistedTargets().has(target);"
FILTER = "  const parameterised = (path: string) => path.split('/').some((segment) => segment.startsWith(':'));"
WRITE_GUARD = "  if (!isAllowlistedTarget(target)) return;\n"
NULL_GUARD = "    if (stored === null) return homeTarget();"
VALIDATE = "    return isAllowlistedTarget(stored) ? stored : homeTarget();"
REMOVE = "      globalThis.sessionStorage.removeItem(RETURN_TARGET_KEY);"
TAKE_NAV = "      const target = takeReturnTarget();\n      if (onScreen.current) navigate(target, { replace: true });"

src = open(f"{ROOT}/{R}").read()
TAKE_BODY = src[src.index("export function takeReturnTarget(): string {"):]

NO_OUTER_GUARD = """export function takeReturnTarget(): string {
  const stored = globalThis.sessionStorage.getItem(RETURN_TARGET_KEY);
  if (stored === null) return homeTarget();
  try {
    globalThis.sessionStorage.removeItem(RETURN_TARGET_KEY);
  } catch {
    /* no outer guard */
  }
  return isAllowlistedTarget(stored) ? stored : homeTarget();
}
"""

def member(expr):
    return f"  return [...allowlistedTargets()].some((member) => {expr});"

MUTANTS = [
 # --- the three the router would HONOUR ---------------------------------------------------------
 ("M1  membership folds case", R, HAS, member("member.toLowerCase() === target.toLowerCase()")),
 ("M2  membership strips a trailing slash", R, HAS, member("member === target.replace(/\\/$/, '')")),
 ("M3  membership trims the target", R, HAS, member("member === target.trim()")),
 # --- the loosenings ---------------------------------------------------------------------------
 ("M4  membership becomes a prefix test (target starts with a member)", R, HAS, member("target.startsWith(member)")),
 ("M5  membership becomes the reverse prefix test", R, HAS, member("member.startsWith(target)")),
 ("M6  membership becomes a substring match", R, HAS, member("target.includes(member)")),
 ("M7  membership decodes the target first", R, HAS, member("member === decodeURIComponent(target)")),
 ("M8  membership strips a leading slash from both sides", R, HAS,
  member("member.replace(/^\\//, '') === target.replace(/^\\//, '')")),
 ("M9  membership strips the basename", R, HAS, member("member === target.replace(/^\\/app/, '')")),
 ("M10 membership ignores a query string", R, HAS, member("member === target.split('?')[0]")),
 ("M11 membership ignores a fragment", R, HAS, member("member === target.split('#')[0]")),
 ("M12 membership matches on the first segment only", R, HAS,
  member("member.split('/')[1] === target.split('/')[1]")),
 ("M13 the parameterised-route filter is dropped, so :id paths join the allowlist", R,
  "    AUTHENTICATED_ROUTE_PATHS.filter((path) => !parameterised(path)).map((path) => `/${path}`),",
  "    AUTHENTICATED_ROUTE_PATHS.map((path) => `/${path}`),"),
 ("M14 public routes join the allowlist", R,
  "  return new Set(",
  "  return new Set(\n    [...PUBLIC_ROUTE_PATHS, ...AUTHENTICATED_ROUTE_PATHS].filter((path) => !parameterised(path)).map((path) => `/${path}`),\n  ) as Set<string> ?? new Set("),
 # --- the fallback -----------------------------------------------------------------------------
 ("M15 the stored value is returned unvalidated", R, VALIDATE, "    return stored;"),
 ("M16 the fallback returns null instead of home", R, VALIDATE,
  "    return isAllowlistedTarget(stored) ? stored : (null as unknown as string);"),
 ("M17 the fallback returns the sign-in route instead of home", R, VALIDATE,
  "    return isAllowlistedTarget(stored) ? stored : '/sign-in';"),
 # --- consumption ------------------------------------------------------------------------------
 ("M18 a refused target is NOT consumed", R, REMOVE,
  "      if (isAllowlistedTarget(stored)) globalThis.sessionStorage.removeItem(RETURN_TARGET_KEY);"),
 ("M19 the whole store is cleared instead of one removal", R, REMOVE, "      globalThis.sessionStorage.clear();"),
 ("M20 the removal is unguarded (T24-D9 gone)", R,
  "    try {\n      globalThis.sessionStorage.removeItem(RETURN_TARGET_KEY);\n    } catch {",
  "    if (true) {\n      globalThis.sessionStorage.removeItem(RETURN_TARGET_KEY);\n    } else if (false) {"),
 ("M21 the whole body is unguarded (T24-D10 gone)", R, TAKE_BODY, NO_OUTER_GUARD),
 # --- the producer and the screen ---------------------------------------------------------------
 ("M22 the write-side filter is dropped", R, WRITE_GUARD, ""),
 ("M23 the write-side filter is inverted", R, WRITE_GUARD, "  if (isAllowlistedTarget(target)) return;\n"),
 ("M24 the screen ignores the fallback and stays on the callback", C, TAKE_NAV,
  "      const target = takeReturnTarget();\n      if (onScreen.current && target !== `/${HOME_PATH}`) navigate(target, { replace: true });"),
 ("M25 the navigation pushes instead of replacing when falling back", C, TAKE_NAV,
  "      const target = takeReturnTarget();\n      if (onScreen.current) navigate(target, { replace: target !== `/${HOME_PATH}` });"),
]

FILES = [
    "src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx",
    "src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx",
    "src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx",
    "src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx",
    "src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx",
    "src/auth/callbackValidatesNonce.test.tsx",
]

T25_ONLY = ["src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx"]


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
t25_failed, t25_total, t25_code = run(T25_ONLY)
print(f"ROOT = {ROOT}\n")
print(f"baseline all-auth: {base_failed} failed of {base_total}, exit {base_code}")
print(f"baseline T25 file: {t25_failed} failed of {t25_total}, exit {t25_code}\n")

survivors = []
for name, path, old, new in MUTANTS:
    s = open(f"{ROOT}/{path}").read()
    if old not in s:
        print(f"  !! {name}: pattern not found")
        survivors.append(name + " (NOT APPLIED)")
        continue
    open(f"{ROOT}/{path}", "w").write(s.replace(old, new, 1))
    failed, total, code = run(FILES)
    # Does the T25 FILE catch it on its own, or only the inherited T24/T23 cases?
    own_failed, own_total, own_code = run(T25_ONLY)
    subprocess.run(["git", "checkout", "--", path], cwd=ROOT, check=True)
    if failed is None or total is None:
        verdict = "BROKEN"
    elif failed > base_failed or total != base_total or code != base_code:
        verdict = "KILLED"
    else:
        verdict = "SURVIVED"
    alone = "own" if (own_failed or 0) > (t25_failed or 0) or own_code != t25_code else "INHERITED-ONLY"
    if verdict != "KILLED":
        survivors.append(f"{name} [{verdict}]")
    print(f"  {verdict:9} {alone:14} {name}  ({failed} failed of {total}, exit {code})")

print("\nsurvivors:", survivors or "none")
subprocess.run(["git", "checkout", "--", "src/auth/"], cwd=ROOT, check=True)
