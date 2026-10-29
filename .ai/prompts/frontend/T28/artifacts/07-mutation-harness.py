"""T28 mutation harness: every mutant is a way the SPA LEARNS ABOUT ENROLLMENT, or a
way its failures stop being uniform.

!!! THIS SCRIPT EDITS PRODUCTION SOURCE AND REVERTS WITH `git checkout -- src/`. !!!
!!! COMMIT OR STASH EVERYTHING UNDER frontend/src/ BEFORE RUNNING IT.            !!!
Uncommitted edits are destroyed silently. That has cost real work twice in this
package (19 test cases in T14, 5 in T15), which is why the warning is this loud.

    python3 -I .ai/prompts/frontend/T28/artifacts/07-mutation-harness.py [filter]

Run it from anywhere; it locates `frontend/` relative to its own path. It needs `npx`,
and takes several minutes - each mutant is two vitest runs of the whole suite. An
optional substring argument runs one family, e.g. `MB07`.

WHY THIS LIST LOOKS LIKE THIS. R10's obligation on the client has two halves and they
fail differently, so the mutants come in two kinds:

  MB01-MB05  the error code, description or URI SURVIVES the discard        (AC4, F8)
  MB06-MB09  the copy names a cause, or a seventh sentence arrives     (AC8, AC9, L4)
  MB10-MB12  the failures stop being uniform                           (AC2, R10, F4)
  MB13-MB15  something GATES on an account state or a role                  (AC7, F6)
  MB16-MB18  enrollment arrives structurally - in code, or in a FILE NAME (AC5, AC6)
  MB19       /login stops being denylisted                               (AC10, R57)
  MB20-MB22  written to escape, or to strip an instrument                (AC5, AC7, AC2)
  MB24-MB28  the five instruments Phase 11 added or widened           (Kimi Gap 1-5)

MB17 IS THE MUTANT THIS WHOLE TASK EXISTS FOR. It is Kimi F3's own example sentence,
verbatim. T27's `MFA_IDENTIFIER_SOURCE` has `multi[-_ ]?factor` and NO
`two[-_ ]?factor`, so this sentence passed every scan in the package before T28. If
MB17 ever survives, the content pattern has been narrowed back and the hole is open.

MB18 IS A FILE, NOT AN EDIT, which is why this harness can create one. A module named
`EnrollMfaScreen.tsx` with an innocent body passes the content scan, passes T27's
identifier scan, and passes T27's import scan until something imports it. Only AC6's
path scan sees it. It is the one mutant no other instrument in this package can catch.

MB20 IS WRITTEN TO ESCAPE, in T26's and T27's tradition: a branch that fires only for
an error code no test drives, writing under a computed key. Runtime instruments cannot
see dormant code; only the source scan can. If MB20 survives, the structural half of
AC5/AC7 has been weakened into a behavioural one.

MB12 AND MB23 ARE THE ORDERING PAIR, and the distinction cost a wrong prediction. MB12
DELETES the error branch; MB23 MOVES it below the state check. The first draft of this
header claimed MB12 was a reordering and predicted it would survive. It was neither:
it is a deletion, and it is KILLED - an inherited case asserts the error path. MB23 is
the reordering, and THAT is the one expected to survive, because a URL carrying
`error=` carries no `code`, so the guard below catches what the branch above would
have. The reordering is behaviourally invisible TODAY. What the AC2 ordering case
forecloses is a FUTURE divergence between the three state shapes - it pins them
identical now, so anything that later told them apart fails. Asking this file to
detect MB23 would be asking it to distinguish two things no observation separates.

MB24-MB28 ARE PHASE 11'S, one per gap Kimi found that changed an instrument. Each was
written to be INVISIBLE to the file as it stood before the fix, which is the only way
to show a new assertion is not decoration:

  MB24, MB25  `multi-factor` in the catalogue and in a component. Both passed every
              scan in the package until Gap 1 was closed - T27's pattern has
              `multi-factor` and no `two-factor`, T28's had the reverse (cf. MB17).
  MB26        a role gate written as `roles.includes('MERCHANT')`, which names no
              `role ===` and so slipped past the gating scan.
  MB27        the discard carries `location.search` onto the sign-in URL for EVERY
              failure kind, so AC2's equivalence still passes - only Gap 5's absolute
              `search === ''` sees it.
  MB28        the exchange is attempted for an error response that carries no `state`.
              The old exchange-count table drove the matching-state shape only, so
              this was a retry on one shape and not another with nothing watching.

HOW TO READ THE OUTPUT. Each line reports the verdict and, separately, whether T28's
OWN file caught the mutant or only an inherited case did. Those are different claims
and only the second column is T28's. A mutant that makes a run unparseable is scored
BROKEN, not KILLED: a mutant has to COMPILE to be evidence of anything (T27 scored one
BROKEN twice before its anchor was right). Read a BROKEN line by hand.

EXPECTED BASELINE on this host after the Phase 9 fixes: the whole suite runs 1338
passed / 1 failed - the one is the pre-existing
`src/api/lintRejectsHandWrittenBackendFetch.test.ts:118`, an oxlint summary-line
difference unrelated to this task - and T28's own file runs 43/43 with exit 0.

SCORE: 23 of 23 KILLED, 21 by T28's own file. MB04 survived until the Phase 9 fix
widened the document surface to the title and the head.
"""

import re
import subprocess
import sys
from pathlib import Path

# Derived from this script's own location: <repo>/.ai/prompts/frontend/T28/artifacts/ -> <repo>/frontend
ROOT = Path(__file__).resolve().parents[5] / "frontend"

CALLBACK = "src/auth/CallbackScreen.tsx"
SIGN_IN = "src/auth/SignInScreen.tsx"
CATALOGUE = "src/i18n/catalogue.ts"
DENYLIST = "src/pwa/navigationDenylist.ts"

T28_ONLY = ["src/auth/sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx"]
FILES = []  # empty = the whole suite, so inherited kills anywhere are visible

# --- anchors, copied from the real source ----------------------------------------------------------------
ERROR_READ = "    const error = params.get('error')?.trim() ?? null;"
ERROR_BRANCH = """    if (error !== null && error !== '') {
      discard();
      return;
    }"""
NOTICE = "      setSignInNotice('discarded');"
NAVIGATE = "      navigate(`/${SIGN_IN_PATH}`, { replace: true });"
STATE_CHECK = """    if (stored === null || stored === '' || stored !== state) {
      discard();
      return;
    }"""
DISCARDED_COPY = "    discarded: 'Your sign-in did not complete. Try again.',"
FAILED_COPY = "    failed: 'Sign-in could not be started. Try again.',"
NOTICE_RENDER = "        {notice !== null && status === 'idle' ? t('signIn.discarded') : null}"
SIGN_IN_LIVE_REGION = """      <div role="status" aria-live="polite">
        {/* The notice yields to a fresh attempt"""
LOGIN_DENY = "  '/login',"

MUTANTS = [
    # --- MB01-MB05: the error survives the discard (AC4, Kimi F8) ----------------------------------------
    ("MB01 the error code is stored in session storage", CALLBACK, NOTICE,
     NOTICE + "\n      globalThis.sessionStorage.setItem('themistra.auth.last_error', error ?? '');"),
    ("MB02 the error_description is stored", CALLBACK, NOTICE,
     NOTICE + "\n      globalThis.sessionStorage.setItem('themistra.auth.why',"
     " params.get('error_description') ?? '');"),
    ("MB03 the error_description is logged", CALLBACK, NOTICE,
     NOTICE + "\n      console.error('sign-in refused:', params.get('error_description'));"),
    ("MB04 the error_description is written to the document title", CALLBACK, NOTICE,
     NOTICE + "\n      document.title = params.get('error_description') ?? document.title;"),
    ("MB05 the error code is carried to the sign-in URL", CALLBACK, NAVIGATE,
     "      navigate(`/${SIGN_IN_PATH}${error ? `?reason=${error}` : ''}`, { replace: true });"),

    # --- MB06-MB09: the copy names a cause (AC8, AC9, L4) ------------------------------------------------
    ("MB06 the discard sentence names two-factor enrollment", CATALOGUE, DISCARDED_COPY,
     "    discarded: 'Your sign-in did not complete. You may need to set up two-factor"
     " authentication.',"),
    ("MB07 the discard sentence mentions an authenticator code", CATALOGUE, DISCARDED_COPY,
     "    discarded: 'Your sign-in did not complete. Check your authenticator code and try again.',"),
    ("MB08 a seventh signIn key arrives", CATALOGUE, DISCARDED_COPY,
     DISCARDED_COPY + "\n    enrollmentNeeded: 'Set up your authenticator to continue.',"),
    ("MB09 the failed and discarded sentences become the same", CATALOGUE, DISCARDED_COPY,
     "    discarded: 'Sign-in could not be started. Try again.',"),

    # --- MB10-MB12: the failures stop being uniform (AC2, Kimi F4) ---------------------------------------
    ("MB10 the refusal pushes history instead of replacing it", CALLBACK, ERROR_BRANCH,
     """    if (error !== null && error !== '') {
      clearAccessToken();
      setSignInNotice('discarded');
      navigate(`/${SIGN_IN_PATH}`, { replace: false });
      return;
    }"""),
    ("MB11 the refusal deletes the stored items and the other failures do not", CALLBACK, ERROR_BRANCH,
     """    if (error !== null && error !== '') {
      consumeAuthorizeState();
      discard();
      return;
    }"""),
    ("MB12 the error branch is deleted outright", CALLBACK, ERROR_BRANCH, ""),

    # --- MB13-MB15: a gate on an account state or a role (AC7, Kimi F6) ----------------------------------
    ("MB13 a PENDING_ENROLLMENT account-state gate", CALLBACK, ERROR_READ,
     ERROR_READ + "\n    const accountState = params.get('account_state');"
     "\n    if (accountState === 'PENDING_ENROLLMENT') { discard(); return; }"),
    ("MB14 a requiresMfa gate", CALLBACK, ERROR_READ,
     ERROR_READ + "\n    const requiresMfa = params.get('requires_mfa') === 'true';"
     "\n    if (requiresMfa) { discard(); return; }"),
    ("MB15 a role comparison decides what a failure shows", CALLBACK, ERROR_READ,
     ERROR_READ + "\n    const role = params.get('role');"
     "\n    if (role === 'MERCHANT' || role === 'ADMIN') { discard(); return; }"),

    # --- MB16-MB18: enrollment arrives structurally (AC5, AC6, Kimi F3 and F5) ---------------------------
    ("MB16 an enrollment string arrives in production code", SIGN_IN, NOTICE_RENDER,
     "        {notice !== null && status === 'idle'\n"
     "          ? t('signIn.discarded') + ' (you may need to enroll)'\n"
     "          : null}"),
    ("MB17 KIMI F3's OWN SENTENCE - two-factor, which T27's pattern misses", SIGN_IN, NOTICE_RENDER,
     "        {/* eslint-disable-next-line */}\n"
     "        {notice !== null && status === 'idle'\n"
     "          ? 'Your account needs two-factor authentication set up before you can sign in'\n"
     "          : null}"),
    ("MB18 a module named for enrollment, with an innocent body (A FILE, NOT AN EDIT)",
     "src/auth/EnrollMfaScreen.tsx", None,
     "export function EnrollMfaScreen() {\n"
     "  return <p>Follow the steps on the next page.</p>;\n"
     "}\n"),

    # --- MB23: the ordering pair's other half (EXPECTED SURVIVOR - see header) --------------------------
    # TWO edits in one file - a move is a deletion plus an insertion, and either alone is a different
    # mutant. `old` may therefore be a list of (old, new) pairs applied in order.
    ("MB23 the error branch is MOVED below the state check (INHERITED-ONLY BY DESIGN - see header)", CALLBACK,
     [(ERROR_BRANCH + "\n\n", ""), (STATE_CHECK, STATE_CHECK + "\n\n" + ERROR_BRANCH)], None),

    # --- MB19: the denylist (AC10, R57) ------------------------------------------------------------------
    ("MB19 /login stops being denylisted", DENYLIST, LOGIN_DENY, ""),

    # --- MB20-MB22: written to escape, or to strip an instrument -----------------------------------------
    ("MB20 ESCAPE: a dormant branch for a code no test drives, under a computed key", CALLBACK, NOTICE,
     NOTICE + "\n      const mfaRequired = error === 'enrollment_pending_vendor_specific';"
     "\n      if (mfaRequired) {"
     "\n        globalThis.sessionStorage.setItem(['themistra', 'mfa', 'state'].join('.'),"
     " params.get('error_description') ?? '');"
     "\n      }"),
    ("MB21 the sign-in live region is removed, so nothing is announced", SIGN_IN, SIGN_IN_LIVE_REGION,
     """      <div>
        {/* The notice yields to a fresh attempt"""),
    ("MB22 the refusal lands somewhere other than the sign-in start", CALLBACK, ERROR_BRANCH,
     """    if (error !== null && error !== '') {
      clearAccessToken();
      setSignInNotice('discarded');
      navigate('/signed-out', { replace: true });
      return;
    }"""),

    # --- MB24-MB28: the instruments Phase 11 added or widened (Kimi Gap 1-5) ----------------------------
    ("MB24 `multi-factor` in the catalogue - Gap 1, the mirror of MB17", CATALOGUE, DISCARDED_COPY,
     "    discarded: 'Your sign-in did not complete. Multi-factor authentication may not be set up yet.',"),
    ("MB25 `multi-factor` in a component - Gap 1, seen only by the source scan", SIGN_IN, NOTICE_RENDER,
     "        {notice !== null && status === 'idle'\n"
     "          ? t('signIn.discarded') + ' Multi factor setup may be outstanding.'\n"
     "          : null}"),
    ("MB26 a role gate written as a membership test - Gap 4", CALLBACK, ERROR_READ,
     ERROR_READ + "\n    const roles = (params.get('roles') ?? '').split(',');"
     "\n    if (roles.includes('MERCHANT')) { discard(); return; }"),
    # MB27's FIRST DRAFT USED `globalThis.location.search` AND SURVIVED, and it was not an escape - it was
    # INERT. The suite drives a `createMemoryRouter`, so the arrival's query lives on the router and the jsdom
    # window's `location.search` is empty; the mutant appended nothing and changed no behaviour at all. A
    # mutant that does not express its own defect proves nothing about the assertion meant to catch it. Read
    # from `params`, which is what the screen itself reads.
    ("MB27 every failure carries its own query string onto the sign-in URL - Gap 5", CALLBACK, NAVIGATE,
     "      navigate(`/${SIGN_IN_PATH}?${params.toString()}`, { replace: true });"),
    # AND THE ONE THAT ISOLATES GAP 5. MB27 is caught by the equivalence too, because each failure kind
    # arrives with a different query and so leaves with a different one. A query that is the SAME for all four
    # is invisible to AC2 by construction - uniform is exactly what AC2 checks for - and invisible to AC1's
    # destination, which is a pathname. Only `expect(outcome.search).toBe('')` sees it. If MB29 ever survives,
    # Gap 5's assertion has been deleted and the URL is no longer known to be clean.
    ("MB29 the sign-in URL grows a uniform marker no other assertion can see - Gap 5, isolated",
     CALLBACK, NAVIGATE,
     "      navigate(`/${SIGN_IN_PATH}?from=callback`, { replace: true });"),
    ("MB28 an error response with no state exchanges anyway - Gap 3", CALLBACK, ERROR_BRANCH,
     """    if (error !== null && error !== '') {
      if (state === null) void exchangeAuthorizationCode({ code: code ?? '', state: '' });
      discard();
      return;
    }"""),
]


def run(files):
    proc = subprocess.run(
        ["npx", "-y", "pnpm@12.14.0", "exec", "vitest", "run"] + files,
        cwd=ROOT, capture_output=True, text=True,
    )
    out = proc.stdout + proc.stderr
    m = re.search(r"Tests\s+(?:(\d+) failed \| )?(\d+) passed(?: \| (\d+) todo)?\s+\((\d+)\)", out)
    if not m:
        return None, None, proc.returncode
    return int(m.group(1) or 0), int(m.group(4)), proc.returncode


def revert():
    subprocess.run(["git", "checkout", "--", "src/"], cwd=ROOT, check=True)
    # `git checkout` restores tracked files; a mutant that CREATES one has to be removed by hand.
    subprocess.run(["git", "clean", "-fq", "--", "src/"], cwd=ROOT, check=True)


wanted = sys.argv[1] if len(sys.argv) > 1 else ""
selected = [m for m in MUTANTS if wanted in m[0]]

revert()
base_failed, base_total, base_code = run(FILES)
own_base_failed, own_base_total, own_base_code = run(T28_ONLY)
print(f"ROOT = {ROOT}\n")
print(f"baseline whole suite: {base_failed} failed of {base_total}, exit {base_code}")
print(f"baseline T28 file:    {own_base_failed} failed of {own_base_total}, exit {own_base_code}\n")

survivors = []
for name, path, old, new in selected:
    target = ROOT / path
    if old is None:
        # A CREATED file (MB18). There is nothing to anchor on, and `git clean` removes it afterwards.
        if target.exists():
            print(f"  !! {name}: {path} already exists - refusing to overwrite")
            survivors.append(f"{name} (NOT APPLIED)")
            continue
        target.write_text(new)
    else:
        # One pair, or several applied in order - a MOVE is a deletion plus an insertion and needs both.
        edits = old if isinstance(old, list) else [(old, new)]
        source = target.read_text()
        missing = [pair[0] for pair in edits if pair[0] not in source]
        if missing:
            print(f"  !! {name}: pattern not found in {path}")
            survivors.append(f"{name} (NOT APPLIED)")
            continue
        for before, after in edits:
            source = source.replace(before, after, 1)
        target.write_text(source)
    failed, total, code = run(FILES)
    own_failed, own_total, own_code = run(T28_ONLY)
    revert()
    if failed is None or total is None:
        verdict = "BROKEN"
    elif failed > base_failed or total != base_total or code != base_code:
        verdict = "KILLED"
    else:
        verdict = "SURVIVED"
    alone = ("own" if (own_failed or 0) > (own_base_failed or 0) or own_code != own_base_code
             else "INHERITED-ONLY")
    if verdict != "KILLED":
        survivors.append(f"{name} [{verdict}]")
    print(f"  {verdict:9} {alone:14} {name}  ({failed} failed of {total}, exit {code})")

print("\nsurvivors:", survivors or "none")
revert()
