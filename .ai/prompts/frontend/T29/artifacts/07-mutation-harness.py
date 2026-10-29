"""T29 mutation harness: every mutant makes the SPA's failures TELL THEM APART — by
copy, by redirect, by styling, or by how long the sentence takes to arrive.

!!! THIS SCRIPT EDITS PRODUCTION SOURCE AND REVERTS WITH `git checkout -- src/`. !!!
!!! COMMIT OR STASH EVERYTHING UNDER frontend/src/ BEFORE RUNNING IT.            !!!
Uncommitted edits are destroyed silently. That has cost real work twice in this
package (19 test cases in T14, 5 in T15), which is why the warning is this loud.

    python3 -I .ai/prompts/frontend/T29/artifacts/07-mutation-harness.py [filter]

Run it from anywhere; it locates `frontend/` relative to its own path. It needs `npx`,
and takes several minutes - each mutant is two vitest runs of the whole suite. An
optional substring argument runs one family, e.g. `MC07`.

WHY THIS LIST LOOKS LIKE THIS. R30 names four things that must be identical - copy,
redirect target, visual treatment - and L4 adds timing. So the mutants come in four
kinds, one per clause, plus the structural ones:

  MC01-MC04  the COPY names a state, or a state key arrives        (AC7, AC8, AC10)
  MC05-MC06  the REDIRECT diverges for one state                        (AC1, AC2)
  MC07       the TREATMENT diverges - a class keyed on the cause             (AC4)
  MC08-MC11  the TIMING diverges - a delay before the sentence              (AC5)
  MC12-MC14  a gate, a timer in auth/, a leak of the description   (AC9, AC6, AC3)
  MC15       written to escape: a delay for a state no test drives          (AC5)

MC08-MC11 ARE THIS TASK'S REASON TO EXIST. No earlier file in the package asserts
anything about WHEN the sentence appears, so every one of these four passes T16-T28
untouched. MC11 is the subtlest: `setTimeout(discard, 0)`, which renders identical
copy to an identical destination and differs only in that it is deferred by one
macrotask. On a fake clock that is never advanced, it is absent. That is the whole
content of AC5, and if MC11 ever survives, the timing clause has stopped being
verified.

MC15 IS WRITTEN TO ESCAPE, in T26's, T27's and T28's tradition: a delay that fires
only for an `error` code no test drives. A runtime instrument cannot see dormant code.
Unlike T28's MB20, however, this one is NOT caught by a source scan either - a
`setTimeout` inside `CallbackScreen` is caught by AC6's timer scan, which is exactly
why that scan covers the whole tree rather than an "identity path" file list (Kimi F5).

MC03 IS THE ENCODING MUTANT, and it is here because the instrument it tests was very
nearly blind. It writes `error_description` into the sign-in URL, where
`URLSearchParams` encodes each space as `+`. The leak sweep's first draft compared raw
text to a raw needle and would have reported CLEAN; `encodings()` is what catches it.

HOW TO READ THE OUTPUT. Each line reports the verdict and, separately, whether T29's
OWN file caught the mutant or only an inherited case did. Those are different claims
and only the second column is T29's. A mutant that makes a run unparseable is scored
BROKEN, not KILLED: a mutant has to COMPILE to be evidence of anything.

EXPECTED BASELINE on this host: the whole suite runs 1388 passed / 1 failed - the one
is the pre-existing `src/api/lintRejectsHandWrittenBackendFetch.test.ts`, an oxlint
summary-line quirk of this machine, unrelated to T29 and failing before and after it.
T29's own file runs 46 / 46, exit 0.
"""

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5] / "frontend"

CALLBACK = "src/auth/CallbackScreen.tsx"
SIGN_IN = "src/auth/SignInScreen.tsx"
CATALOGUE = "src/i18n/catalogue.ts"

T29_ONLY = ["src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx"]
FILES = []  # empty = the whole suite, so inherited kills anywhere are visible

# --- anchors, copied from the real source ----------------------------------------------------------------
ERROR_READ = "    const error = params.get('error')?.trim() ?? null;"
ERROR_BRANCH = """    if (error !== null && error !== '') {
      discard();
      return;
    }"""
NOTICE = "      setSignInNotice('discarded');"
NAVIGATE = "      navigate(`/${SIGN_IN_PATH}`, { replace: true });"
DISCARD_OPEN = "    const discard = () => {"
DISCARDED_COPY = "    discarded: 'Your sign-in did not complete. Try again.',"
NOTICE_RENDER = "        {notice !== null && status === 'idle' ? t('signIn.discarded') : null}"
SIGN_IN_LIVE_REGION = """      <div role="status" aria-live="polite">
        {/* The notice yields to a fresh attempt"""

MUTANTS = [
    # --- MC01-MC04: the COPY names a state (AC7, AC8, AC10) ----------------------------------------------
    ("MC01 the discard sentence names a suspended account", CATALOGUE, DISCARDED_COPY,
     "    discarded: 'Your sign-in did not complete. This account has been suspended.',"),
    ("MC02 a seventh signIn key arrives, naming a state", CATALOGUE, DISCARDED_COPY,
     DISCARDED_COPY + "\n    accountLocked: 'This account is locked. Try again later.',"),
    ("MC03 ENCODING: the description is carried to the sign-in URL (space becomes +)", CALLBACK, NAVIGATE,
     "      navigate(`/${SIGN_IN_PATH}?${new URLSearchParams({ why:"
     " params.get('error_description') ?? '' }).toString()}`, { replace: true });"),
    ("MC04 a state word arrives in a component", SIGN_IN, NOTICE_RENDER,
     "        {notice !== null && status === 'idle'\n"
     "          ? t('signIn.discarded') + ' Your account may be locked.'\n"
     "          : null}"),

    # --- MC05-MC06: the REDIRECT diverges (AC1, AC2) -----------------------------------------------------
    ("MC05 one state lands somewhere else", CALLBACK, ERROR_BRANCH,
     """    if (error !== null && error !== '') {
      if ((params.get('error_description') ?? '').includes('suspended')) {
        clearAccessToken();
        setSignInNotice('discarded');
        navigate('/signed-out', { replace: true });
        return;
      }
      discard();
      return;
    }"""),
    ("MC06 the refusal pushes history instead of replacing it", CALLBACK, NAVIGATE,
     "      navigate(`/${SIGN_IN_PATH}`, { replace: false });"),

    # --- MC07: the TREATMENT diverges (AC4) --------------------------------------------------------------
    ("MC07 a class keyed on the cause reaches the sign-in screen", SIGN_IN, NOTICE_RENDER,
     "        {notice !== null && status === 'idle'\n"
     "          ? <span className=\"sign-in-error--locked\">{t('signIn.discarded')}</span>\n"
     "          : null}"),

    # --- MC08-MC11: the TIMING diverges (AC5) - THIS TASK'S REASON TO EXIST ------------------------------
    ("MC08 a cause-keyed delay before the notice", CALLBACK, ERROR_BRANCH,
     """    if (error !== null && error !== '') {
      const slow = (params.get('error_description') ?? '').includes('locked');
      setTimeout(discard, slow ? 1500 : 0);
      return;
    }"""),
    ("MC09 a uniform 1.5s delay before every discard", CALLBACK, ERROR_BRANCH,
     """    if (error !== null && error !== '') {
      setTimeout(discard, 1500);
      return;
    }"""),
    ("MC10 a lockout countdown is rendered", SIGN_IN, NOTICE_RENDER,
     "        {notice !== null && status === 'idle'\n"
     "          ? t('signIn.discarded') + ` Try again in ${Math.ceil(Date.now() / 1000) % 60}s.`\n"
     "          : null}"),
    ("MC11 SUBTLEST: the discard is deferred by one macrotask", CALLBACK, ERROR_BRANCH,
     """    if (error !== null && error !== '') {
      setTimeout(discard, 0);
      return;
    }"""),

    # --- MC12-MC14: a gate, a timer in auth/, a leak (AC9, AC6, AC3) -------------------------------------
    ("MC12 an account-state gate decides what a failure shows", CALLBACK, ERROR_READ,
     ERROR_READ + "\n    const accountState = params.get('account_state');"
     "\n    if (accountState === 'LOCKED') { discard(); return; }"),
    ("MC13 a timer appears inside an auth module", CALLBACK, DISCARD_OPEN,
     "    const tick = () => setInterval(() => undefined, 1000);\n"
     "    void tick;\n" + DISCARD_OPEN),
    ("MC14 the description is written to the document title", CALLBACK, NOTICE,
     NOTICE + "\n      document.title = params.get('error_description') ?? document.title;"),

    # --- MC15: written to escape -------------------------------------------------------------------------
    ("MC15 ESCAPE: a delay that fires only for a code no test drives", CALLBACK, ERROR_BRANCH,
     """    if (error !== null && error !== '') {
      if (error === 'account_state_vendor_specific') {
        setTimeout(discard, 2000);
        return;
      }
      discard();
      return;
    }"""),

    # --- MC16: the live region is stripped, so nothing is announced at all -------------------------------
    ("MC16 the sign-in live region is removed", SIGN_IN, SIGN_IN_LIVE_REGION,
     """      <div>
        {/* The notice yields to a fresh attempt"""),
]


def run(files):
    proc = subprocess.run(
        ["npx", "-y", "pnpm@12.14.0", "exec", "vitest", "run", *files],
        cwd=ROOT, capture_output=True, text=True,
    )
    out = proc.stdout + proc.stderr
    failed = total = None
    for line in out.splitlines():
        stripped = line.strip()
        if stripped.startswith("Tests "):
            # "Tests  1 failed | 1388 passed (1389)" or "Tests  46 passed (46)"
            body = stripped[len("Tests "):]
            if "failed" in body:
                failed = int(body.split("failed")[0].strip())
            else:
                failed = 0
            if "(" in body:
                total = int(body.split("(")[-1].rstrip(")"))
    return proc.returncode, failed, total


def apply(path, old, new):
    target = ROOT / path
    if old is None:
        target.write_text(new)
        return True
    text = target.read_text()
    edits = old if isinstance(old, list) else [(old, new)]
    for before, after in edits:
        if text.count(before) != 1:
            return False
        text = text.replace(before, after, 1)
    target.write_text(text)
    return True


def revert():
    subprocess.run(["git", "checkout", "--", "src/"], cwd=ROOT, check=False)
    subprocess.run(["git", "clean", "-fq", "--", "src/"], cwd=ROOT, check=False)


def main():
    wanted = sys.argv[1] if len(sys.argv) > 1 else ""
    print(f"ROOT = {ROOT}\n")
    code, failed, total = run(FILES)
    print(f"baseline whole suite: {failed} failed of {total}, exit {code}")
    code29, failed29, total29 = run(T29_ONLY)
    print(f"baseline T29 file:    {failed29} failed of {total29}, exit {code29}\n")
    base_failed = failed

    survivors = []
    for label, path, old, new in MUTANTS:
        if wanted and wanted not in label:
            continue
        if not apply(path, old, new):
            print(f"  BROKEN    anchor missed  {label}")
            revert()
            continue
        code, failed, total = run(FILES)
        if failed is None:
            verdict, own = "BROKEN", "-"
        elif failed > base_failed:
            verdict = "KILLED"
            _, own_failed, _ = run(T29_ONLY)
            own = "own" if (own_failed or 0) > 0 else "INHERITED-ONLY"
        else:
            verdict, own = "SURVIVED", "-"
            survivors.append(f"{label} [SURVIVED]")
        print(f"  {verdict:<9} {own:<14} {label}  ({failed} failed of {total}, exit {code})")
        revert()

    print(f"\nsurvivors: {survivors or 'none'}")


if __name__ == "__main__":
    main()
