#!/usr/bin/env python3
"""T40 mutation harness - the public route set is exact.

WHAT THIS IS. Each mutant is a regression this task exists to prevent, applied to real source,
measured against a real baseline, and reverted. A case that cannot tell the mutant from the
original is a case that was never holding anything.

IT REVERTS WITH `git checkout -- frontend/src/`. COMMIT YOUR TESTS FIRST. That wipes the whole
tree, not just the mutated file - it cost 19 cases in T14, 5 in T15, and a full phase-9 edit in
T36 because I ran it on an uncommitted test file.

THE MACHINERY IS T37'S - eighth copy, OQ-T32-4 - with one thing this task needed and no earlier
copy did:

  IT IS RUN AT PHASE 7, BEFORE THE NAMED SUITE EXISTS. Every earlier task built its harness after
  its own cases were written, so a survivor meant a gap in finished work. Here a survivor mostly
  means the work has not been written yet, and the two readings must not be confused. So each row
  carries an EXPECTATION - `inherited` if an instrument that already exists should kill it, `phase
  10` if nothing can kill it until the named suite is written - and the run reports the rows whose
  outcome DISAGREES with their expectation. That disagreement is the finding; the raw survivor
  count at this phase is not.

  A row expected `inherited` that survives is a real gap in instruments that already exist.
  A row expected `phase 10` that DIES is better than predicted, and worth knowing: it means an
  inherited instrument covers more than this task assumed, and phase 10 must not then claim the
  kill for a case of its own.

    python3 -I .ai/prompts/frontend/T40/artifacts/07-mutation-harness.py        # all
    python3 -I .ai/prompts/frontend/T40/artifacts/07-mutation-harness.py RS05   # one
"""

import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[5]
FRONTEND = REPO / "frontend"

GUARD = "frontend/src/auth/RequireSession.tsx"
ROUTES = "frontend/src/app/routes.tsx"
T33 = "frontend/src/auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx"
T25 = "frontend/src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx"

# The files that measure this task: the named test (phase 10's), the sibling whose pathTree pin T40 grew,
# and the two closed-task instruments T40's change made fire in phase 6.
OWN_TESTS = [
    "src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx",
    "src/app/routesResolveUnderAppPrefixOnly.test.tsx",
    "src/auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx",
    "src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx",
]

CANARIES = set()

# SCORED APART. `null` and `<></>` both render nothing: React produces no node for either, so no assertion
# about the DOM can separate them, and AC8's claim is about OUTPUT. A row that cannot be killed without
# changing production to make an internal choice observable is a limit of the code's observability, not a
# gap in the suite, and counting it either way would lie.
UNFALSIFIABLE = {"RS04"}

FILES = []  # empty = the whole suite, so inherited kills anywhere are visible

EXPECTED_BASELINE_FAILURES = 1
KNOWN_ENVIRONMENTAL_FAILURES = {
    # This host's oxlint@1.87.0 prints no summary line, so the assertion that parses it cannot pass.
    "src/api/lintRejectsHandWrittenBackendFetch.test.ts",
}

# --- anchors, copied from the real source ----------------------------------------------------------------
COND = "  const hasSession = Boolean(accessToken.current());"
RETURN = "  return hasSession ? <Outlet /> : null;"
GUARD_IMPORTS = "import { Outlet } from 'react-router';\nimport { accessToken } from './accessToken';"

GATE = ("      { element: <RequireSession />, children: AUTHENTICATED_ROUTE_PATHS.map((path) => ({ path })) },")
CATCH_ALL = "      { path: '*' },"
ROOT = "    path: '/',\n    children: ["
PUBLIC_MAP_END = "        return { path };\n      }),"

T33_WALK = ("      const walk = (routes: readonly Node[]): Node[] =>\n"
            "        routes.flatMap((route) => [route, ...walk(route.children ?? [])]);\n"
            "      const everyRoute = walk(appRoutes as Node[]);")

T25_CHAIN = "          matched: ['/', '(index)', HOME_PATH],"

# label, file, old, new, expectation
MUTANTS = [
    # --- the gate's decision ----------------------------------------------------------------------------
    # R28's first clause, switched off. The authenticated eleven are served to a visitor with no session,
    # which is the whole defect this task exists to prevent.
    ("RS01 the gate is open: every authenticated path is served with no session",
     GUARD, COND, "  const hasSession = true;", "phase 10"),

    # The opposite: a split that is a deny. A visitor WITH a session is served nothing, which looks like
    # security and is a dead application.
    ("RS02 the gate never opens: an authenticated path is refused even with a session",
     GUARD, COND, "  const hasSession = false;", "phase 10"),

    # The empty-string hole the brief's constraint names and F12 settled. `retainAccessToken('')` assigns
    # whatever string it is given (`accessToken.ts:90`), so `!== null` calls an empty token a session.
    ("RS03 an empty-string token counts as a session (Boolean -> !== null)",
     GUARD, COND, "  const hasSession = accessToken.current() !== null;", "phase 10"),

    # SCORED APART. Nothing renders either way.
    ("RS04 the blocked arm returns an empty fragment instead of null",
     GUARD, RETURN, "  return hasSession ? <Outlet /> : <></>;", "unfalsifiable"),

    # F5's precision issue, as a measurement. An empty <div> renders NO TEXT, so a case asserting only
    # `textContent === ''` passes it; only a case asserting no ELEMENT in the outlet can tell.
    ("RS05 the blocked arm renders an empty element rather than nothing",
     GUARD, RETURN, "  return hasSession ? <Outlet /> : <div />;", "phase 10"),

    # The gate takes task 41's work. R28's second clause is not this task's, and a guard that redirects
    # would make the redirect's return target and replace-vs-push decisions implicit rather than designed.
    ("RS06 the gate redirects instead of rendering nothing, taking task 41's clause",
     GUARD,
     (GUARD_IMPORTS, RETURN),
     ("import { Navigate, Outlet } from 'react-router';\nimport { accessToken } from './accessToken';",
      "  return hasSession ? <Outlet /> : <Navigate to=\"/sign-in\" replace />;"),
     "inherited"),

    # AC7's forbidden surfaces, one planted reach. The gate is a read; a console write is the cheapest
    # thing to plant that a source scan must see and no render comparison can.
    ("RS07 the gate writes to console",
     GUARD, COND, "  console.log('gate');\n  const hasSession = Boolean(accessToken.current());", "phase 10"),

    # AC7/F18. The gate asks the session owner and nothing else; a route read is how a gate starts
    # growing per-path policy that no spec authorizes.
    ("RS08 the gate reads the route it is gating",
     GUARD, COND,
     "  const hasSession = Boolean(accessToken.current()) && globalThis.location.pathname !== '/app/admin';",
     "phase 10"),

    # --- where the gate is applied ----------------------------------------------------------------------
    # The nesting undone: the eleven go back to being direct children of the root and the gate disappears.
    # This is the shape the table had before this task, so anything that passes it has measured nothing.
    ("RS09 the gate is removed and the eleven are root children again",
     ROUTES, GATE, "      ...AUTHENTICATED_ROUTE_PATHS.map((path) => ({ path })),", "inherited"),

    # The gate kept but given a path. `path: ''` reads as a string in the route tree, changes what the
    # bare `/app/` matches, and makes the authenticated paths resolve one level deeper than declared.
    ("RS10 the gate route is given path: '' instead of being pathless",
     ROUTES, GATE,
     "      { path: '', element: <RequireSession />, children: AUTHENTICATED_ROUTE_PATHS.map((path) => ({ path })) },",
     "inherited"),

    # The catch-all moved behind the gate. It still has no element, so a case asserting "the catch-all has
    # no element" passes - and task 41, which replaces it with the redirect, would be redirecting only the
    # visitors who do not need it.
    ("RS11 the catch-all is moved inside the gate",
     ROUTES,
     (GATE, CATCH_ALL),
     ("      { element: <RequireSession />, children: [...AUTHENTICATED_ROUTE_PATHS.map((path) => ({ path })),\n"
      "        { path: '*' }] },",
      ""),
     "phase 10"),

    # The gate's children built from both arrays. The public nine keep working - they match at the root
    # first - so NO behavioural case can see this. Only a structural claim about the gate's reach can.
    ("RS12 the gate's children are built from both arrays, so the public nine are also inside it",
     ROUTES, GATE,
     "      { element: <RequireSession />,\n"
     "        children: [...AUTHENTICATED_ROUTE_PATHS, ...PUBLIC_ROUTE_PATHS].map((path) => ({ path })) },",
     "phase 10"),

    # The gate moved onto the root, so it gates the public nine and the catch-all too. R28 inverted: a
    # visitor with no session is served NOTHING, including the sign-in screen they need.
    ("RS13 the gate is moved onto the root, so it gates the public nine as well",
     ROUTES,
     (ROOT, GATE),
     ("    path: '/',\n    element: <RequireSession />,\n    children: [",
      "      ...AUTHENTICATED_ROUTE_PATHS.map((path) => ({ path })),"),
     "inherited"),

    # AC13, the hole this task OPENS. Both screen-count pins record a route only when it has a `path`, so
    # an element on a second pathless route is invisible to both - a screen smuggled past the handoff.
    ("RS14 a second pathless route carries a screen, invisible to both count pins",
     ROUTES, PUBLIC_MAP_END,
     "        return { path };\n      }),\n      { element: <SignInScreen /> },",
     "phase 10"),

    # --- the instruments phase 6 grew -------------------------------------------------------------------
    # Task 33's AC11 walker, put back the way it was. The claim becomes vacuous: the eleven are no longer
    # root children, so the filter finds nothing and the case would pass while measuring nothing. Its own
    # LOWER BOUND is what must catch this - and catching it is what proves the growth kept the claim.
    ("RS15 task 33's AC11 walker is shallow again, making its claim vacuous",
     T33, T33_WALK,
     "      const everyRoute = (appRoutes[0].children ?? []) as Node[];",
     "inherited"),

    # Task 25's counterfactual control, with the gate's level dropped from the chain rather than named.
    # The control exists to prove the instrument can report a SUCCESSFUL navigation; a chain assertion
    # that no longer matches the real chain reports nothing of the kind.
    ("RS16 task 25's control drops the gate's level from the match chain",
     T25, T25_CHAIN, "          matched: ['/', HOME_PATH],", "inherited"),
]


def run(files, verbose=False):
    cmd = ["npx", "-y", "pnpm@12.14.0", "exec", "vitest", "run", *files]
    proc = subprocess.run(cmd, cwd=FRONTEND, capture_output=True, text=True)
    failed = total = None
    files_failed = set()
    names = []
    for line in (proc.stdout + proc.stderr).splitlines():
        stripped = line.strip()
        if stripped.startswith("Tests "):
            body = stripped[len("Tests "):]
            failed = int(body.split("failed")[0].strip()) if "failed" in body else 0
            if "(" in body:
                total = int(body.split("(")[-1].rstrip(")"))
        match = re.search(r"\bFAIL\b\s+(\S+\.(?:ts|tsx))", stripped)
        if match:
            files_failed.add(match.group(1))
        if verbose and stripped.startswith("× "):
            leaf = stripped[2:].split(" > ")[-1]
            names.append(re.sub(r"\s+\d+ms$", "", leaf).strip())
    return proc.returncode, failed, total, files_failed, names


def edits_of(old, new):
    """One mutant, one or more replacements. A single replacement cannot express a REORDER or a move."""
    return list(zip(old, new)) if isinstance(old, tuple) else [(old, new)]


def apply(path, old, new):
    target = REPO / path
    text = target.read_text()
    for find, replace in edits_of(old, new):
        if text.count(find) != 1:
            return False
        text = text.replace(find, replace, 1)
    target.write_text(text)
    return True


def revert():
    subprocess.run(["git", "checkout", "--", "frontend/src/"], cwd=REPO, check=False)
    subprocess.run(["git", "clean", "-fq", "--", "frontend/src/"], cwd=REPO, check=False)


def preflight():
    """Every anchor in its file, exactly once, REPLAYED IN MEMORY so a mid-mutant ambiguity is caught
    before any verdict prints. T36 shipped a BROKEN row that the per-anchor check on unmutated text had
    passed, and BROKEN is not a survivor, so the run still ended `survivors: none`."""
    stale = []
    for label, path, old, new, _expect in MUTANTS:
        text = (REPO / path).read_text()
        for find, replace in edits_of(old, new):
            count = text.count(find)
            if count != 1:
                stale.append(f"  {label.split()[0]:<6} {path} matches an anchor {count} times, expected 1")
                break
            text = text.replace(find, replace, 1)
    if stale:
        print("REFUSING TO RUN: anchors no longer match the source.\n")
        print("\n".join(stale))
        print("\nEach would print BROKEN, which is NOT a survivor, so the run would still end\n"
              "`survivors: none` and the score would be overstated by exactly this many.")
        return False
    return True


def score(label, base_failed):
    code, failed, total = run(FILES)[:3]
    if failed is None:
        print(f"  BROKEN    -              {label}")
        return None, True, None
    if failed > base_failed:
        _, own_failed, _, _, names = run(OWN_TESTS, verbose=True)
        own = "own" if (own_failed or 0) > 0 else "INHERITED-ONLY"
        print(f"  KILLED    {own:<14} {label}  ({failed} failed of {total}, exit {code})")
        for name in names:
            print(f"            └ {name}")
        return None, False, names
    print(f"  SURVIVED  -              {label}  ({failed} failed of {total}, exit {code})")
    return label, False, None


def main():
    wanted = sys.argv[1] if len(sys.argv) > 1 else ""
    print(f"REPO = {REPO}\n")
    revert()
    if not preflight():
        return 1

    code, failed, total, files_failed, _ = run(FILES)
    print(f"baseline whole suite: {failed} failed of {total}, exit {code}\n")
    if not files_failed <= KNOWN_ENVIRONMENTAL_FAILURES:
        print("REFUSING TO RUN: a file outside the known environmental set is failing.\n"
              f"  unexpectedly failing: {sorted(files_failed - KNOWN_ENVIRONMENTAL_FAILURES)}")
        return 1
    if failed != EXPECTED_BASELINE_FAILURES:
        print(f"NOTE: baseline is {failed}, not the recorded {EXPECTED_BASELINE_FAILURES}; all are in the\n"
              "known environmental set, so the measured floor is sound.\n")
    base_failed = failed

    survivors, broken, outcomes = [], [], {}
    for label, path, old, new, expect in MUTANTS:
        if wanted and wanted not in label:
            continue
        if not apply(path, old, new):
            print(f"  BROKEN    anchor missed  {label}")
            broken.append(label)
            revert()
            continue
        lost, was_broken, _names = score(label, base_failed)
        outcomes[label.split()[0]] = ("SURVIVED" if lost else "KILLED", expect)
        if lost:
            survivors.append(lost)
        if was_broken:
            broken.append(label)
        revert()

    kept = [row for row in MUTANTS if not wanted or wanted in row[0]]
    apart = CANARIES | UNFALSIFIABLE
    regressions = [row for row in kept if row[0].split()[0] not in apart]
    lost_labels = [label for label in survivors + broken if label.split()[0] not in apart]

    print(f"\nregressions: {len(regressions) - len(lost_labels)}/{len(regressions)} killed"
          f"{'' if not broken else f'  ({len(broken)} BROKEN, not counted as killed)'}")
    if apart:
        rows = [label for label in survivors if label.split()[0] in apart]
        print(f"unfalsifiable: {len(apart)} - no observable effect to detect: {rows or 'none - SEE BELOW'}")
    print(f"survivors:   {[label.split()[0] for label in survivors] or 'none'}")
    print(f"broken:      {broken or 'none'}")

    # THE PHASE 7 VERDICT. A survivor here is expected for every row phase 10 owns; what matters is a row
    # whose outcome disagrees with its expectation.
    print("\n--- expectation vs outcome ---")
    disagreed = []
    for key, (outcome, expect) in sorted(outcomes.items()):
        if expect == "unfalsifiable":
            flag = "as expected" if outcome == "SURVIVED" else "DISAGREES - it has an observable effect"
        elif expect == "inherited":
            flag = "as expected" if outcome == "KILLED" else "DISAGREES - a gap in instruments that EXIST"
        else:
            flag = "as expected" if outcome == "SURVIVED" else "DISAGREES - inherited cover is wider than assumed"
        if flag != "as expected":
            disagreed.append(key)
        print(f"  {key:<6} expected {expect:<13} got {outcome:<9} {flag}")
    print(f"\ndisagreements: {disagreed or 'none'}")
    return 1 if (disagreed or broken) else 0


if __name__ == "__main__":
    sys.exit(main())
