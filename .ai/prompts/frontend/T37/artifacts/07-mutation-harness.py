#!/usr/bin/env python3
"""T37 mutation harness - the signed-out landing.

WHAT THIS IS. Each mutant is a regression this task exists to prevent, applied to real source,
measured against a real baseline, and reverted. A case that cannot tell the mutant from the
original is a case that was never holding anything.

IT REVERTS WITH `git checkout -- frontend/src/`. COMMIT YOUR TESTS FIRST. That wipes the whole
tree, not just the mutated file - it cost 19 cases in T14, 5 in T15, and a full phase-9 edit in
T36 because I ran it on an uncommitted test file.

THE MACHINERY IS T36'S, with T36's two phase-10 fixes in it: a BROKEN mutant is excluded from the
kill numerator (the inherited tally counted one as killed), every anchor must match exactly once
before a verdict prints, and a mutant may carry several edits so a reorder is not a deletion
wearing another name. Sixth copy; the extraction is OQ-T32-4's.

A NOTE ON WHAT A MUTANT CANNOT DO HERE, because this task is mostly words. Several of T37's
criteria are claims about COPY, and the harness can only change the copy to something wrong and
check that a case notices. It cannot tell whether the chosen words are GOOD - that is OQ-T37-1,
the owner's. What it can do, and does, is prove the copy is not free to change silently: AC11
asserts the whole rendered text, so every copy mutant below dies on it.

NO CANARIES. Nothing here pins a hazard as-is.

    python3 -I .ai/prompts/frontend/T37/artifacts/07-mutation-harness.py        # all
    python3 -I .ai/prompts/frontend/T37/artifacts/07-mutation-harness.py SL05   # one
"""

import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[5]
FRONTEND = REPO / "frontend"

SCREEN = "frontend/src/auth/SignedOutScreen.tsx"
ROUTES = "frontend/src/app/routes.tsx"
CATALOGUE = "frontend/src/i18n/catalogue.ts"
END_SESSION = "frontend/src/auth/endSession.ts"

OWN_TEST = "src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx"

CANARIES = set()
FILES = []  # empty = the whole suite, so inherited kills anywhere are visible

EXPECTED_BASELINE_FAILURES = 1
KNOWN_ENVIRONMENTAL_FAILURES = {
    # This host's oxlint@1.87.0 prints no summary line, so the assertion that parses it cannot pass.
    "src/api/lintRejectsHandWrittenBackendFetch.test.ts",
}

# --- anchors, copied from the real source ----------------------------------------------------------------
HEADING = "      <h1>{t('signedOut.heading')}</h1>"
EXPLANATION = "      <p>{t('signedOut.explanation')}</p>"
LINK = "      <Link to={`/${SIGN_IN_PATH}`}>{t('signedOut.action')}</Link>"
SCREEN_SIG = "export function SignedOutScreen() {"
SCREEN_IMPORTS = "import { SIGN_IN_PATH } from '../app/routes';"
ATTACH = "        if (path === SIGNED_OUT_PATH) return { path, element: <SignedOutScreen /> };"
PATH_CONST = "export const SIGNED_OUT_PATH = 'signed-out';"
PUBLIC_ENTRY = "  SIGNED_OUT_PATH,\n] as const;"
CAT_HEADING = "    heading: 'Signed out',"
CAT_EXPLANATION = "    explanation: 'Sign in again whenever you are ready.',"
CAT_ACTION = "    action: 'Sign in',"
END_SESSION_IMPORT = "import { APP_BASENAME, SIGNED_OUT_PATH } from '../app/routes';"
CALLBACK_ATTACH = "        if (path === CALLBACK_PATH) return { path, element: <CallbackScreen /> };"

MUTANTS = [
    # --- AC1: the route resolves to a screen ------------------------------------------------------------
    # The state the route was in before this task: declared, public, and rendering nothing. A successful
    # sign-out lands on a blank page, which reads as a broken application.
    ("SL01 the route loses its element, so sign-out lands on a blank page again",
     ROUTES, ATTACH, "        if (path === SIGNED_OUT_PATH) return { path };"),

    # --- AC3: every string comes from the catalogue -----------------------------------------------------
    ("SL02 the heading is spelled in the component instead of read from the catalogue",
     SCREEN, HEADING, "      <h1>Signed out</h1>"),

    ("SL03 the catalogue section is renamed, so every `t()` call renders its key",
     CATALOGUE, "  signedOut: {", "  signedOutLanding: {"),

    # --- AC4, AC11, AC13: what the copy may say ---------------------------------------------------------
    # Names a CAUSE. L4: an expired token and a deleted account must read the same, and this tells them
    # apart. AC4's vocabulary scan is written for exactly this.
    ("SL04 the copy names a cause, so an expired session reads differently from a locked account",
     CATALOGUE, CAT_EXPLANATION, "    explanation: 'Your session expired. Sign in again.',"),

    # ASSERTS STATE, in words AC4's denylist does not contain - no cause word appears. Only AC11's
    # whole-text equality can see it, which is why that case asserts the entire string rather than a
    # list of absences.
    ("SL05 the heading asserts a state the screen cannot verify",
     CATALOGUE, CAT_HEADING, "    heading: 'You have been signed out of all devices',"),

    # `signIn.sessionEnded`'s own sentence, which phase 4 rejected BY NAME: it is false for a visitor who
    # typed this URL while still holding a token.
    ("SL06 the explanation borrows signIn.sessionEnded's claim about the visitor's session",
     CATALOGUE, CAT_EXPLANATION, "    explanation: 'Your session has ended. Sign in again.',"),

    ("SL07 the action borrows the sign-in screen's hand-over wording",
     CATALOGUE, CAT_ACTION, "    action: 'Continue to sign in',"),

    # --- AC5: one way back, and only one ----------------------------------------------------------------
    ("SL08 the link becomes a button wired to navigate, losing middle-click and open-in-new-tab",
     SCREEN,
     (SCREEN_IMPORTS, LINK),
     ("import { SIGN_IN_PATH } from '../app/routes';\nimport { useNavigate } from 'react-router';",
      "      <button type=\"button\" onClick={() => undefined}>{t('signedOut.action')}</button>")),

    ("SL09 a second way out appears beside the first",
     SCREEN, LINK,
     "      <Link to={`/${SIGN_IN_PATH}`}>{t('signedOut.action')}</Link>\n"
     "      <Link to=\"/home\">{t('signedOut.action')}</Link>"),

    ("SL10 the way back points somewhere other than sign-in",
     SCREEN, LINK, "      <Link to=\"/home\">{t('signedOut.action')}</Link>"),

    # --- AC6: indifferent to the query string -----------------------------------------------------------
    # SAS can return an opaque `state`. Rendering it is both ugly and a disclosure of a correlation value.
    ("SL11 the screen reads the query string and renders the opaque state value",
     SCREEN,
     (SCREEN_IMPORTS, EXPLANATION),
     ("import { SIGN_IN_PATH } from '../app/routes';\nimport { useSearchParams } from 'react-router';",
      "      <p>{t('signedOut.explanation')}{useSearchParams()[0].get('state')}</p>")),

    # --- AC7: it does nothing ---------------------------------------------------------------------------
    ("SL12 the screen reports its own arrival to the backend",
     SCREEN,
     (SCREEN_SIG, HEADING),
     ("export function SignedOutScreen() {\n  void fetch('/api/signed-out');",
      "      <h1>{t('signedOut.heading')}</h1>")),

    # --- AC8: it clears nothing -------------------------------------------------------------------------
    # The mutant that makes a URL into a sign-out affordance: any cross-site link to /app/signed-out would
    # then end the visitor's session in that tab.
    ("SL13 the landing clears the session on arrival, making a URL a sign-out affordance",
     SCREEN,
     (SCREEN_IMPORTS, SCREEN_SIG),
     ("import { SIGN_IN_PATH } from '../app/routes';\nimport { clearAccessToken } from './accessToken';",
      "export function SignedOutScreen() {\n  clearAccessToken();")),

    # --- AC13: the module-scope read, and what it ACTUALLY does ------------------------------------------
    # This mutant corrected its own description. It was written as "a startup failure rather than a test
    # failure", on the strength of `routes.tsx:2-5`. Measured, it throws NOTHING: zero ReferenceErrors, the
    # screen renders, and the only visible damage is `href="/app/undefined"`. The same mutation in
    # `authorizeRequest.ts` - task 16's actual cycle - also throws nothing and fails one case. The runtime
    # does not catch a module-scope read here; a test does. See the header of SignedOutScreen.tsx.
    ("SL14 SIGN_IN_PATH is read at module scope, rendering a dead href=\"/app/undefined\" and throwing nothing",
     SCREEN,
     (SCREEN_IMPORTS, LINK),
     ("import { SIGN_IN_PATH } from '../app/routes';\nconst BACK = `/${SIGN_IN_PATH}`;",
      "      <Link to={BACK}>{t('signedOut.action')}</Link>")),

    # --- AC2, AC10: the route's declaration -------------------------------------------------------------
    ("SL15 the route is dropped from the public set, so the landing needs a session",
     ROUTES, PUBLIC_ENTRY, "] as const;"),

    ("SL16 the published constant is misspelled, so SAS is sent to a route that does not exist",
     ROUTES, PATH_CONST, "export const SIGNED_OUT_PATH = 'signed-off';"),

    # --- AC10: the single spelling task 37 exists to create ---------------------------------------------
    # The drift T36-D2 described, reintroduced: endSession.ts goes back to its own copy of the literal.
    ("SL17 endSession.ts reverts to its own private copy of the route",
     END_SESSION,
     (END_SESSION_IMPORT, "${base}/${SIGNED_OUT_PATH}"),
     ("import { APP_BASENAME } from '../app/routes';\nconst SIGNED_OUT_PATH = 'signed-out';",
      "${base}/${SIGNED_OUT_PATH}")),

    # --- AC12: the design phase 4 REJECTED, planted ------------------------------------------------------
    # Phase 4 considered redirecting a token-holding tab away from this screen and rejected it on a
    # measurement: no authenticated route has an element, so `/app/home` renders nothing and the redirect
    # would trade a screen that says something for a blank page. AC12 says the screen stays put, and until
    # phase 7 no mutant made it move - the only criterion with no mutant of its own.
    ("SL22 a token-holding tab is bounced to /app/home, the design phase 4 rejected",
     SCREEN,
     ("import { Link } from 'react-router';", "  const { t } = useTranslation();"),
     ("import { Link, Navigate } from 'react-router';\nimport { accessToken } from './accessToken';",
      "  const { t } = useTranslation();\n  if (accessToken() !== null) return <Navigate replace to=\"/home\" />;")),

    # --- document structure, a family NOTHING in the criteria names -------------------------------------
    # Added in phase 7 after the mutant-to-case mapping showed every mutant above landing on a copy, a route
    # or an affordance criterion, and none on the screen's SHAPE. L4's neutrality and R19's landing say
    # nothing about landmarks or heading level, so these three measure what the suite WOULD say - and two of
    # them die on a crash rather than on a case written for them, which is a finding, not a kill to be proud
    # of. Recorded as FU-T37-1.
    ("SL19 the landmark becomes a plain div, so the screen has no main region",
     SCREEN, ("    <main>", "    </main>"), ("    <div>", "    </div>")),

    ("SL20 the heading drops to h2, so the page has no level-one heading",
     SCREEN, HEADING, "      <h2>{t('signedOut.heading')}</h2>"),

    ("SL21 the way back is rendered above the heading, so the screen opens on its action",
     SCREEN,
     (HEADING + "\n" + EXPLANATION + "\n" + LINK,),
     (LINK + "\n" + HEADING + "\n" + EXPLANATION,)),

    # --- the instrument gap phase 7 FOUND, by reading the suite rather than the source -------------------
    # Every assertion in the file reaches through `main!` - AC5's link count, AC11's whole-text equality,
    # AC6's byte-identical compare, all of them. Nothing looks at the container OUTSIDE the landmark. So a
    # sibling rendered beside `<main>` is invisible to all 25 cases. This mutant was written expecting to
    # SURVIVE, and did; the fix is in the suite, not here.
    ("SL23 a second way out is rendered OUTSIDE <main>, where every case is scoped not to look",
     SCREEN,
     ("    <main>", "    </main>\n  );"),
     ("    <>\n    <main>",
      "    </main>\n    <a href=\"/elsewhere\">{t('signedOut.action')}</a>\n    </>\n  );")),

    # --- PHASE 9: the regressions phase 8's findings said were unmeasured --------------------------------
    # I1. The screen reads the session and renders the SAME markup. AC6 and AC13 compare rendered output, so
    # neither can see this; only the source sweep can. Written to prove the new entries are load-bearing.
    ("SL25 the screen reads the access token and branches to identical markup (I1)",
     SCREEN,
     ("import { SIGN_IN_PATH } from '../app/routes';", "  const { t } = useTranslation();"),
     ("import { SIGN_IN_PATH } from '../app/routes';\nimport { accessToken } from './accessToken';",
      "  const { t } = useTranslation();\n  const held = accessToken() !== null;\n  void held;")),

    # I1, the other half: a leak to a surface T36's AC8 sweeps and T37's did not.
    ("SL26 the screen writes the route to document.title (I1)",
     SCREEN, "  const { t } = useTranslation();",
     "  const { t } = useTranslation();\n  document.title = t('signedOut.heading');"),

    # I2. The token task 36 introduced, taken by the one screen that must leave it alone. Before phase 9
    # nothing retained an ID token, so this changed no assertion anywhere.
    ("SL27 the landing takes the ID token task 36 holds for the end-session hint (I2)",
     SCREEN,
     ("import { SIGN_IN_PATH } from '../app/routes';", "  const { t } = useTranslation();"),
     ("import { SIGN_IN_PATH } from '../app/routes';\nimport { takeIdToken } from './idToken';",
      "  const { t } = useTranslation();\n  void takeIdToken();")),

    # I3. Imports the published constant - so the two inherited assertions pass - and spells the route
    # literally where the URL is actually built. The drift AC10 exists to prevent, wearing its own fix.
    ("SL28 endSession.ts imports the constant and hardcodes the route anyway (I3)",
     END_SESSION, "${base}/${SIGNED_OUT_PATH}", "${base}/signed-out"),

    # I5. An anchor dressed as a button: counted as a link by "only one way out", while losing the
    # affordances the Link was chosen for.
    ("SL29 the way back becomes an anchor with role=button (I5)",
     SCREEN, LINK,
     "      <Link role=\"button\" to={`/${SIGN_IN_PATH}`}>{t('signedOut.action')}</Link>"),

    # I12, which phase 9 REJECTED as already covered, kept as the measurement that rejected it: AC5 asserts
    # the source matches /<Link\s/, and `<NavLink ` does not contain `<Link`. If this ever survives, the
    # rejection was wrong and I12 should be reopened.
    ("SL30 the way back becomes a NavLink, which phase 9 claims AC5 already catches (I12)",
     SCREEN,
     ("import { Link } from 'react-router';", LINK),
     ("import { NavLink } from 'react-router';",
      "      <NavLink to={`/${SIGN_IN_PATH}`}>{t('signedOut.action')}</NavLink>")),

    # --- AC9: the count pin, from the other side --------------------------------------------------------
    # A fourth screen attached without either list being extended. Task 16's pin and task 37's AC9 should
    # BOTH fail - the deliberate redundancy at the handoff.
    ("SL18 a fourth screen is attached without extending either attached-screen list",
     ROUTES, CALLBACK_ATTACH,
     "        if (path === CALLBACK_PATH) return { path, element: <CallbackScreen /> };\n"
     "        if (path === 'register') return { path, element: <SignedOutScreen /> };"),
]

def run(files, name_filter=None):
    command = ["npx", "-y", "pnpm@12.14.0", "exec", "vitest", "run", *files]
    if name_filter:
        command += ["-t", name_filter]
    proc = subprocess.run(command, cwd=FRONTEND, capture_output=True, text=True)
    out = proc.stdout + proc.stderr
    failed = total = None
    files_failed = set()
    for line in out.splitlines():
        stripped = line.strip()
        if stripped.startswith("Tests "):
            body = stripped[len("Tests "):]
            failed = int(body.split("failed")[0].strip()) if "failed" in body else 0
            if "(" in body:
                total = int(body.split("(")[-1].rstrip(")"))
        match = re.search(r"\bFAIL\b\s+(\S+\.(?:ts|tsx))", stripped)
        if match:
            files_failed.add(match.group(1))
    return proc.returncode, failed, total, files_failed


def edits_of(old, new):
    """One mutant, one or more replacements.

    A single replacement cannot express a REORDER - it can only delete or rewrite in place - and NR03
    needs one: moving the clear below the navigation is a different regression from removing it, and the
    first version of that row deleted the line and called itself a reorder, which made it a duplicate of
    NR01 wearing another name. A mutant that measures something other than its own description is worse
    than a missing mutant, because the score counts it.
    """
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
    """Every anchor in its file, exactly once, before a single verdict is printed."""
    stale = []
    for label, path, old, new in MUTANTS:
        text = (REPO / path).read_text()
        for find, _replace in edits_of(old, new):
            count = text.count(find)
            if count != 1:
                stale.append(f"  {label.split()[0]:<6} {path} matches an anchor {count} times, expected 1")
    if stale:
        print("REFUSING TO RUN: anchors no longer match the source.\n")
        print("\n".join(stale))
        print(
            "\nThe source moved and these mutants did not follow. Each would print BROKEN, which is\n"
            "NOT a survivor, so the run would still end `survivors: none` and the score would be\n"
            "overstated by exactly this many. Re-copy the anchors and run again."
        )
        return False
    return True


def score(label, base_failed):
    code, failed, total = run(FILES)[:3]
    if failed is None:
        print(f"  BROKEN    -              {label}")
        return None, True
    if failed > base_failed:
        own_failed = run([OWN_TEST])[1]
        own = "own" if (own_failed or 0) > 0 else "INHERITED-ONLY"
        print(f"  KILLED    {own:<14} {label}  ({failed} failed of {total}, exit {code})")
        return None, False
    print(f"  SURVIVED  -              {label}  ({failed} failed of {total}, exit {code})")
    return f"{label} [SURVIVED]", False


def main():
    wanted = sys.argv[1] if len(sys.argv) > 1 else ""
    print(f"REPO = {REPO}\n")
    revert()
    if not preflight():
        return 1

    code, failed, total, files_failed = run(FILES)
    print(f"baseline whole suite: {failed} failed of {total}, exit {code}")
    own_code, own_failed, own_total, _ = run([OWN_TEST])
    print(f"baseline own cases:   {own_failed} failed of {own_total}, exit {own_code}\n")

    # The NAMES are the real guard, so they are checked first and the count only warns.
    if not files_failed <= KNOWN_ENVIRONMENTAL_FAILURES:
        unexpected = sorted(files_failed - KNOWN_ENVIRONMENTAL_FAILURES)
        print(
            "REFUSING TO RUN: a file outside the known environmental set is failing.\n"
            f"  unexpectedly failing: {unexpected}\n"
            "A floor made of a different test measures every verdict below against the wrong\n"
            "thing. Fix the tree, or update KNOWN_ENVIRONMENTAL_FAILURES deliberately."
        )
        return 1
    if failed != EXPECTED_BASELINE_FAILURES:
        print(
            f"NOTE: the baseline is {failed} failures, not the recorded {EXPECTED_BASELINE_FAILURES}, but\n"
            "every one of them is in the known environmental set, so the measured floor is sound.\n"
        )
    if own_failed != 0:
        print(f"REFUSING TO RUN: this task's own cases fail {own_failed} on a clean tree.")
        return 1
    base_failed = failed

    survivors = []
    broken = []
    regressions = canaries = 0
    for label, path, old, new in MUTANTS:
        if wanted and wanted not in label:
            continue
        if label.split()[0] in CANARIES:
            canaries += 1
        else:
            regressions += 1
        if not apply(path, old, new):
            print(f"  BROKEN    anchor missed  {label}")
            broken.append(label)
            revert()
            continue
        survived, was_broken = score(label, base_failed)
        if survived:
            survivors.append(survived)
        if was_broken:
            broken.append(label)
        revert()

    kept = [label for label, *_ in MUTANTS if not wanted or wanted in label]
    scored = [label for label in kept if label.split()[0] not in CANARIES]
    # A BROKEN mutant is NOT a kill, and the inherited tally counted it as one: `len(scored) - survivors`
    # reported 30/30 on a run whose own `broken:` line named ES29. The number a reader quotes must not be
    # inflated by a mutant that never ran, so broken rows come out of the numerator here. Found in T36's
    # phase 10; the four earlier copies have the same arithmetic.
    lost = [label for label in survivors + broken if label.split()[0] not in CANARIES]
    print(f"\nregressions: {len(scored) - len(lost)}/{regressions} killed"
          f"{'' if not broken else f'  ({len(broken)} BROKEN, not counted as killed)'}")
    if canaries:
        print(f"canaries:    {canaries} - a change elsewhere that must still be caught today, scored apart")
    print(f"survivors:   {survivors or 'none'}")
    print(f"broken:      {broken or 'none'}")
    return 1 if (survivors or broken) else 0


if __name__ == "__main__":
    sys.exit(main())
