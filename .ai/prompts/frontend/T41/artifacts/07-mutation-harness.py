#!/usr/bin/env python3
"""T41 mutation harness - the unauthenticated redirect.

WHAT THIS IS. Each mutant is a regression this task exists to prevent, applied to real source,
measured against a real baseline, and reverted. A case that cannot tell the mutant from the
original is a case that was never holding anything.

IT REVERTS WITH `git checkout -- frontend/src/`. COMMIT YOUR TESTS FIRST. That wipes the whole
tree, not just the mutated file - it cost 19 cases in T14, 5 in T15, and a full phase-9 edit in
T36 because I ran it on an uncommitted test file.

THE MACHINERY IS T37'S - NINTH COPY, OQ-T32-4 - with T40's expectation field, which matters more
here than it did there. T41's named test does not exist yet either: `package.md` §8 calls it
`unauthenticatedRoutesRedirectToSignIn`, phase 10 writes it, and at phase 7 the only things
measuring this change are ELEVEN INSTRUMENTS OF OTHER TASKS that phase 6 grew. So each row
predicts:

  `own`           killed, AND with a named failure in a file this task owns or grew.
  `phase 10`      nothing can kill it until the named suite exists; a kill here is a DISAGREEMENT
                  worth reading, because it means an instrument covers more than I thought.
  `unfalsifiable` no observable effect to detect; scored apart, never as a kill or a loss.

A row that dies only outside OWN_TESTS prints `INHERITED-ONLY` and is reported as a DISAGREEMENT
rather than a kill, because a kill by an unrelated suite is evidence about the mutant and not
about this task.

ONE HAZARD IS DELIBERATELY NOT A ROW. Spelling the destination WITH the basename -
`to={`${APP_BASENAME}/${SIGN_IN_PATH}`}` - makes react-router apply the basename twice, so
`/app/app/sign-in` matches the catch-all, which redirects again. Phase 4 measured that this does
not fail: it exhausts the V8 heap and kills the process. A mutant that crashes the runner measures
nothing and would take every row after it down with it, so the hazard lives in
`RedirectToSignIn.tsx`'s header, where the next person to touch the destination will read it,
rather than here.

    python3 -I .ai/prompts/frontend/T41/artifacts/07-mutation-harness.py        # all
    python3 -I .ai/prompts/frontend/T41/artifacts/07-mutation-harness.py RD05   # one
"""

import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[5]
FRONTEND = REPO / "frontend"

REDIR = "frontend/src/auth/RedirectToSignIn.tsx"
GUARD = "frontend/src/auth/RequireSession.tsx"
ROUTES = "frontend/src/app/routes.tsx"
T40 = "frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx"
T16 = "frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx"

# The files that measure this task TODAY. Task 37's AC9 and task 40's six arms live in the first;
# task 16's four pins live in the second. The named test is phase 10's and is not here yet.
OWN_TESTS = [
    # PHASE 10 ADDED THE FIRST ONE, and it is the file `package.md` §8 names. The other two are task 37's and
    # task 16's, grown by phase 6; a kill in either still counts as this task's, because the growth is this
    # task's work.
    "src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx",
    "src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx",
    "src/app/routesResolveUnderAppPrefixOnly.test.tsx",
]

CANARIES = set()

# EMPTY AT PHASE 9, AND IT HELD ONE ROW AT PHASE 7. RD09 adds the branch the component's header argues
# against - skipping the write for the arrival that matched nothing - and the allowlist refuses `/` anyway,
# so no BEHAVIOURAL assertion can separate them. Phase 8's I3 pointed out that this does not make it
# unkillable: the rule is about where a rule LIVES, which is a claim about source, and source has been an
# instrument in this package since T22. So RD09 became a `phase 10` row with a scan to write, and
# "unfalsifiable" went back to meaning no observable effect of ANY kind, in the DOM or in the file.
#
# AND THE SAME CORRECTION WENT THE OTHER WAY. Two rows that phase 7 scored as `phase 10` were mutating a
# line that could not run (see RD06/RD07 below). Phase 7 therefore mislabelled in both directions at once:
# one row called unkillable that a scan can kill, and two called future work that were measuring nothing.
UNFALSIFIABLE = set()

FILES = []  # empty = the whole suite, so inherited kills anywhere are visible

EXPECTED_BASELINE_FAILURES = 1
KNOWN_ENVIRONMENTAL_FAILURES = {
    # This host's oxlint@1.87.0 prints no summary line, so the assertion that parses it cannot pass.
    "src/api/lintRejectsHandWrittenBackendFetch.test.ts",
}

# --- anchors, copied from the real source ----------------------------------------------------------------
R_COND = "  const hasSession = Boolean(accessToken.current());\n  if (hasSession) return null;"
R_ARRIVAL = "  const arrival = pathname;"
R_REMEMBER = "  rememberReturnTarget(arrival);"
R_NAV = "  return <Navigate to={`/${SIGN_IN_PATH}`} replace />;"

G_RETURN = "  return hasSession ? <Outlet /> : <RedirectToSignIn />;"

INDEX_ROUTE = "      { index: true, element: <RedirectToSignIn /> },"
CATCH_ALL = "      { path: '*', element: <RedirectToSignIn /> },"
PUBLIC_MAP_END = "        return { path };\n      }),"

# --- anchors in the instruments phase 6 grew -------------------------------------------------------------
PROBE_GATE = "        if (child.path === undefined && child.children === undefined) {"
AC9_LIST = "      expect(attached).toEqual(['sign-in', 'callback', SIGNED_OUT_PATH, '*']);"
AC12_FILTER = ("      const pathless = children.filter((route) => route.path === undefined "
               "&& route.children !== undefined);")
AC13_COUNT = ("      expect(carrying, 'a pathless route beyond the gate and task 41\\'s index redirect "
              "carries an element')\n        .toHaveLength(2);")
PATH_TREE = "      [undefined, []],\n      ...PUBLIC_ROUTE_PATHS.map((path) => [path, []]),"
WITH_ELEMENTS = ("    expect(routesWithElements(appRoutes))\n"
                 "      .toEqual([...SCREENS_ATTACHED_SO_FAR, ...REDIRECTS_ATTACHED_SO_FAR]);")

# label, file, old, new, expectation
MUTANTS = [
    # --- the redirect's own decisions -------------------------------------------------------------------
    # The session read, stuck shut. Every unauthenticated arrival gets `null` - which is task 40's
    # behaviour exactly, so this mutant is "task 41 was never written" with task 41's file present.
    ("RD01 the redirect never fires: an unauthenticated arrival renders nothing",
     REDIR, R_COND, "  const hasSession = true;\n  if (hasSession) return null;", "own"),

    # F7, inverted. R28 is conditioned WHILE unauthenticated; without the clause an authenticated visitor
    # at a path that matches nothing is bounced to sign-in, which is a claim this package has not made -
    # and would log a signed-in visitor out of their own address bar.
    ("RD02 the authenticated clause is dropped: a signed-in visitor is redirected too (F7)",
     REDIR, R_COND, "  const hasSession = false;\n  if (hasSession) return null;", "own"),

    # The empty-string hole, from the other side. `retainAccessToken('')` assigns whatever it is given
    # (`accessToken.ts:90`), so `!== null` calls an empty token a session and the redirect is suppressed.
    ("RD03 an empty-string token suppresses the redirect (Boolean -> !== null)",
     REDIR, R_COND,
     "  const hasSession = accessToken.current() !== null;\n  if (hasSession) return null;", "own"),

    # AC6. A pushed redirect leaves the route that just refused the visitor on the back stack, so Back
    # bounces off it - the trap `returnTargetOutsideAllowlistFallsBackToHome.test.tsx:599` forbids.
    # Nothing but `historyAction` can tell; rendered output is identical.
    ("RD04 `replace` is dropped, so the redirect PUSHES",
     REDIR, R_NAV, "  return <Navigate to={`/${SIGN_IN_PATH}`} />;", "own"),

    # AC11 and the import cycle. Behaviourally identical today, which is the point: only a source scan can
    # see it, and the day `SIGN_IN_PATH` changes spelling the literal silently redirects to the catch-all.
    ("RD05 the destination is a hardcoded literal rather than SIGN_IN_PATH",
     REDIR, R_NAV, '  return <Navigate to="/sign-in" replace />;', "own"),

    # AC14, AND THE WHOLE POINT OF THE TASK - BOTH ROWS REWRITTEN AT PHASE 9, because what they used to
    # mutate should never have existed. RD06 removed a BASENAME STRIP and RD07 put an off-by-one in it, on
    # the premise that `useLocation().pathname` carries the `/app` prefix. Kimi's I6 doubted it; measured
    # again over six arrivals through both router constructors, `useLocation()` is basename-RELATIVE and it
    # is `router.state.location.pathname` that carries the prefix. Both rows were mutating DEAD CODE, so
    # their survival at phase 7 did not mean "phase 10 has not written the case yet" - there was nothing
    # there to break. Two rows were reported as future work when they were measuring nothing, and the
    # phase-7 artifact called one of them "the mutant most likely to ship".
    #
    # This is the real defect in that family: the arrival made ABSOLUTE. `/app/account` is not in the
    # allowlist, which holds `/account`, so every deep link is refused in silence - no throw, no log, just a
    # visitor who lands on home after signing in. The redirect still works, which is why it would ship.
    ("RD06 the arrival is made absolute, so every deep link is silently discarded (AC14)",
     REDIR, R_ARRIVAL, "  const arrival = `/app${pathname}`;", "own"),

    # The strip PUT BACK, which is the defect its removal fixed rather than a hypothetical.
    # `/app/app/account` matches the catch-all; `useLocation()` gives `/app/account`; the strip turns that
    # into `/account` and remembers a route the visitor never asked for. Only a case arriving at a DOUBLED
    # prefix can see it, which is why phase 10 must write one.
    ("RD07 the basename strip is put back, so a doubled prefix remembers a route nobody asked for",
     REDIR, R_ARRIVAL,
     "  const base = '/app';\n"
     "  const arrival = pathname.startsWith(`${base}/`) ? pathname.slice(base.length) : pathname;",
     "own"),

    # AC7. The redirect happens, nothing is remembered, and the visitor lands on home after signing in.
    ("RD08 the return target is never remembered",
     REDIR, R_REMEMBER, "", "own"),

    # BEHAVIOURALLY unfalsifiable, and phase 8's I3 is why that is not the same as unkillable: "no branch,
    # the allowlist is the only filter" is a claim about SOURCE, and this package has measured source since
    # T22. A scan forbidding a branch on `arrival` before the call kills this row, phase 10 writes it, and
    # the expectation moves accordingly.
    ("RD09 a branch is added where the allowlist belongs",
     REDIR, R_REMEMBER, "  if (arrival !== '/') rememberReturnTarget(arrival);", "own"),

    # --- the three arrivals ------------------------------------------------------------------------------
    # The gate's negative branch, back to task 40's `null`. This is the arrival R28's first clause creates,
    # and it is the one with eleven paths behind it.
    ("RD10 the gate renders nothing again instead of delegating",
     GUARD, G_RETURN, "  return hasSession ? <Outlet /> : null;", "own"),

    # Arrival (c). The bare `/app/` goes back to matching the root alone and rendering a blank page - the
    # address the SPA's own origin resolves to.
    ("RD11 the index route is removed, so the bare /app/ renders nothing",
     ROUTES, INDEX_ROUTE + "\n", "", "own"),

    # Arrival (b). Anything unmatched renders nothing again.
    ("RD12 the catch-all loses its element",
     ROUTES, CATCH_ALL, "      { path: '*' },", "own"),

    # THE OVER-REACH, which is the failure mode opposite to all of the above: the redirect applied to
    # routes that are public by L14. Six of the nine have no element of their own, so this compiles and
    # leaves sign-in, callback and signed-out working - the three a smoke test would check.
    ("RD13 the redirect is applied to the public routes too",
     ROUTES, PUBLIC_MAP_END,
     "        return { path, element: <RedirectToSignIn /> };\n      }),", "own"),

    # `index: true` versus `path: ''`, which is the same distinction task 40 made for the gate. A path of
    # `''` is a string in the route tree, and the pin that can see the difference is `pathTree`.
    ("RD14 the index route is spelled `path: ''` instead of `index: true`",
     ROUTES, INDEX_ROUTE, "      { path: '', element: <RedirectToSignIn /> },", "own"),

    # --- the instruments phase 6 grew --------------------------------------------------------------------
    # `withProbes`, back to identifying the gate as "the pathless child". This is D1: with task 41's index
    # route present, the derived table finds two gates, fails its own baseline, and takes nine arms down
    # with it - including the four phase 5 named as this task's boundary.
    ("RD15 withProbes identifies the gate by path alone again (D1)",
     T40, PROBE_GATE, "        if (child.path === undefined) {", "own"),

    # Task 37's AC9, back to three. The claim becomes false rather than weaker, which is the growth being
    # necessary: the catch-all now carries an element and has a path, so the walker sees it.
    ("RD16 task 37's AC9 counts three attached routes again",
     T40, AC9_LIST, "      expect(attached).toEqual(['sign-in', 'callback', SIGNED_OUT_PATH]);", "own"),

    # Task 40's AC12, with the narrowing removed. The filter finds two pathless children and the length
    # assertion fails - which is what makes "narrow the filter" different from "raise the count".
    ("RD17 task 40's AC12 filters pathless routes by path alone again",
     T40, AC12_FILTER,
     "      const pathless = children.filter((route) => route.path === undefined);", "own"),

    # Task 40's AC13, back to one. It is the only instrument in the repository that can see an element on
    # a pathless route, and task 41 added the second one.
    ("RD18 task 40's AC13 admits only one pathless element again",
     T40, AC13_COUNT,
     "      expect(carrying, 'more than one pathless route carries an element, or none does')"
     "\n        .toHaveLength(1);", "own"),

    # Task 16's `pathTree`, with the index route's level dropped rather than named. A tree pin that no
    # longer matches the tree reports nothing about it.
    ("RD19 task 16's pathTree drops the index route's level",
     T16, PATH_TREE, "      ...PUBLIC_ROUTE_PATHS.map((path) => [path, []]),", "own"),

    # Task 16's screen count, with the redirect folded back out. The number would be wrong by one in the
    # direction that PASSES nothing - and the reason the two lists are separate is that folding the
    # redirect INTO the screen list would have passed while meaning something false.
    ("RD20 task 16's element count forgets the redirect",
     T16, WITH_ELEMENTS, "    expect(routesWithElements(appRoutes)).toEqual([...SCREENS_ATTACHED_SO_FAR]);",
     "own"),
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
        return None, True, None, False
    if failed > base_failed:
        _, own_failed, _, _, names = run(OWN_TESTS, verbose=True)
        by_own = (own_failed or 0) > 0
        own = "own" if by_own else "INHERITED-ONLY"
        print(f"  KILLED    {own:<14} {label}  ({failed} failed of {total}, exit {code})")
        for name in names:
            print(f"            └ {name}")
        return None, False, names, by_own
    print(f"  SURVIVED  -              {label}  ({failed} failed of {total}, exit {code})")
    return label, False, None, False


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
        lost, was_broken, _names, by_own = score(label, base_failed)
        outcomes[label.split()[0]] = ("SURVIVED" if lost else ("KILLED/own" if by_own else "KILLED/other"), expect)
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
    print("\n--- expectation vs outcome (`own` = killed BY A CASE THIS TASK OWNS) ---")
    disagreed = []
    for key, (outcome, expect) in sorted(outcomes.items()):
        if expect == "unfalsifiable":
            flag = "as expected" if outcome == "SURVIVED" else "DISAGREES - it has an observable effect"
        elif expect == "phase 10":
            flag = "as expected" if outcome == "SURVIVED" else f"DISAGREES - already killed ({outcome})"
        elif outcome == "SURVIVED":
            flag = "DISAGREES - nothing detects it"
        elif outcome == "KILLED/other":
            flag = "DISAGREES - killed only OUTSIDE this task's files"
        else:
            flag = "as expected"
        if flag != "as expected":
            disagreed.append(key)
        print(f"  {key:<6} expected {expect:<13} got {outcome:<9} {flag}")
    print(f"\ndisagreements: {disagreed or 'none'}")
    return 1 if broken else 0


if __name__ == "__main__":
    sys.exit(main())
