#!/usr/bin/env python3
"""T173 mutation harness - the design system.

WHAT THIS IS. Each mutant is a regression this task exists to prevent, applied to real source,
measured against a real baseline, and reverted. A case that cannot tell the mutant from the
original is a case that was never holding anything.

IT REVERTS WITH `git checkout`. COMMIT YOUR TESTS FIRST. It wipes the whole working tree under
the paths in REVERT_PATHS, not just the mutated file - it cost 19 cases in T14, 5 in T15, and a
full phase-9 edit in T36 because I ran it on uncommitted test edits.

THE MACHINERY IS T37'S - TENTH COPY, OQ-T32-4 - with two changes this task forced:

  1. A MUTANT MAY SPAN SEVERAL FILES. `path`/`old`/`new` may each be a tuple, applied in
     lockstep. RD04 needs it: the R30 pin's non-emptiness half cannot be falsified by stripping
     ONE primitive's class, because four identical lists are still identical and still non-empty.
     The regression it guards against is "the design system was reverted", and that is five
     files at once. A single-file harness would have scored that row SURVIVED and concluded the
     assertion was dead weight.
  2. REVERT_PATHS REACHES OUTSIDE `frontend/src/`. Two rows mutate `frontend/vite.config.ts`.
     Every previous copy reverted `frontend/src/` alone, so those two mutants would have been
     left in the tree - and every row after them would have been measured against a mutated
     build config, silently.

T173'S NAMED TEST DOES NOT EXIST YET: `package.md` §8 calls it
`screensRenderThroughDesignSystemPrimitivesOnly`, phase 10 writes it, and at phase 7 the only
things measuring this change are OTHER TASKS' INSTRUMENTS - three that phase 6 grew, and several
it did not touch at all. So each row predicts one of four outcomes, and the fourth is new:

  `own`           killed, AND with a named failure in a file this task owns or grew.
  `inherited`     killed ONLY outside this task's files, AND THAT IS THE POINT. The primitives
                  took over obligations other tasks already pin - a forwarded ref, a live
                  region, a landmark, a `data-testid`. Phase 6 claimed those obligations
                  survived the move; these rows are that claim under measurement. In every
                  previous copy this outcome printed `INHERITED-ONLY` and was reported as a
                  DISAGREEMENT, which would have been exactly backwards here.
  `phase 10`      nothing can kill it until the named suite exists; a kill here is a
                  DISAGREEMENT worth reading, because an instrument covers more than I thought.
  `unfalsifiable` no observable effect of any kind, in the DOM or in the file; scored apart.

WHY `inherited` IS NOT A LOOPHOLE. It is only legitimate where phase 6 named the inherited
instrument in advance. RD11's kill, for instance, must land in
`rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx` - the file `RateLimitNotice.tsx`'s
header cites as the evidence for AC6 - and the run prints the failing case names, so a kill by
something else is visible rather than absorbed.

    python3 -I .ai/prompts/frontend/T173/artifacts/07-mutation-harness.py        # all
    python3 -I .ai/prompts/frontend/T173/artifacts/07-mutation-harness.py RD04   # one
"""

import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[5]
FRONTEND = REPO / "frontend"

UI = "frontend/src/app/ui/"
SCREEN, HEADING, TEXT = UI + "Screen.tsx", UI + "Heading.tsx", UI + "Text.tsx"
STATUS, BUTTON, LINK = UI + "StatusRegion.tsx", UI + "ActionButton.tsx", UI + "TextLink.tsx"
SURFACE, HOLDER = UI + "Surface.tsx", UI + "FocusHolder.tsx"
STYLES = "frontend/src/app/styles.css"
MOUNT = "frontend/src/app/mountShell.tsx"
VITE = "frontend/vite.config.ts"
SHELL = "frontend/src/app/AppShell.tsx"
SIGNIN = "frontend/src/auth/SignInScreen.tsx"
R30 = "frontend/src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx"

# The three files phase 6 GREW. A kill in any of them is this task's own, because the growth is
# this task's work. The named suite is phase 10's and is not here.
OWN_TESTS = [
    "src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx",
    "src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx",
    "src/app/bundleContainsNoSecretEnvKeys.test.ts",
]

# RD17 mutates TEST code, so it measures the instrument rather than the implementation. Scored
# apart: the R30 pin's whole sameness half rests on `styleAttributesOf` reporting `style` as well
# as `class`, and nothing in this task's production code can falsify that - the cause of an
# identity failure never reaches the screen, which is the file's own premise. Its two synthetic
# controls are what hold it, so this row asks whether those controls are awake.
CANARIES = {"RD17"}

UNFALSIFIABLE = set()

FILES = []  # empty = the whole suite, so inherited kills anywhere are visible

REVERT_PATHS = ["frontend/src/", "frontend/vite.config.ts"]

EXPECTED_BASELINE_FAILURES = 1
KNOWN_ENVIRONMENTAL_FAILURES = {
    # This host's oxlint@1.87.0 prints no summary line, so the assertion that parses it cannot pass.
    "src/api/lintRejectsHandWrittenBackendFetch.test.ts",
}

# --- anchors, copied from the real source ----------------------------------------------------------------
S_MAIN = '      <main ref={ref} className="mx-auto flex max-w-prose flex-col gap-4 px-6 py-12">'
S_CLOSE = "      </main>"
H_OPEN = '      <h1 ref={ref} className="text-step-1 font-semibold tracking-tight text-text">'
H_CLOSE = "      </h1>"
T_OPEN = '      <p ref={ref} id={id} className="text-step-0 leading-relaxed text-text">'
ST_OPEN = '      <div ref={ref} role="status" aria-live="polite" className="text-step--1 text-muted">'
ST_CLOSE = "      </div>"
B_CLASS = ('      className="self-start rounded-md bg-accent px-4 py-2 text-step-0 font-medium '
           'text-text-inverse outline-offset-2 focus-visible:outline-2 focus-visible:outline-focus '
           'aria-disabled:opacity-70"')
B_REF = "      ref={ref}\n      type=\"button\""
L_OPEN = "      <Link\n        ref={ref}\n        to={to}"
L_CLOSE = "      </Link>"
SU_PROPS = "  function Surface({ children, 'data-testid': testId }, ref) {"
SU_OPEN = '      <div ref={ref} data-testid={testId} className="min-h-dvh bg-surface text-text">'
F_OPEN = ('      <div ref={ref} tabIndex={-1} className="flex flex-col gap-2 outline-offset-2 '
          'focus-visible:outline-2 focus-visible:outline-focus">')
ACCENT = "  --color-accent: #1b4ed8;"
CSS_IMPORT = "import './styles.css';"
PLUGIN = "    tailwindcss(),"
SHELL_RENDER = '  return <Surface data-testid="app-shell">{children}</Surface>;'
SIGNIN_SCREEN = "    <Screen>"
SIGNIN_CLOSE = "    </Screen>"
WALKER = "    for (const attribute of ['class', 'style']) {"

# label, file(s), old, new, expectation
MUTANTS = [
    # --- the primitives stop being the elements they claim to be -----------------------------------------
    # The landmark. `Screen` exists so every screen is a <main>; a <div> leaves the document with no
    # landmark at all, which is the L11 failure a styling task is most likely to cause by accident.
    ("RD01 the Screen primitive renders a div, so no screen has a landmark",
     SCREEN, (S_MAIN, S_CLOSE),
     ('      <div ref={ref} className="mx-auto flex max-w-prose flex-col gap-4 px-6 py-12">',
      "      </div>"), "own"),

    # The outline. An <h2> with no <h1> above it is the exact defect `Heading.tsx`'s header argues the
    # missing `level` prop prevents - so this row asks whether anything would have noticed.
    ("RD02 the Heading primitive renders an h2, so no screen has a level-one heading",
     HEADING, (H_OPEN, H_CLOSE),
     ('      <h2 ref={ref} className="text-step-1 font-semibold tracking-tight text-text">',
      "      </h2>"), "own"),

    # The one case phase 6 had to grow one layer down (D3). T37 forbade a hand-rolled anchor in
    # SignedOutScreen; the primitive is now where that could reappear, and a full-page navigation out of
    # the SPA is the regression.
    ("RD03 TextLink renders a bare anchor instead of the router's Link (D3)",
     LINK, (L_OPEN, L_CLOSE),
     ("      <a\n        ref={ref}\n        href={to}", "      </a>"), "own"),

    # THE WHOLE TASK, REVERTED - five primitives lose their class in one edit, which is the only shape
    # that can falsify the R30 pin's non-emptiness half. Stripping one leaves four identical non-empty
    # lists and proves nothing; this is why the harness grew multi-file mutants.
    ("RD04 every primitive on the sign-in screen loses its class: the design system is reverted",
     (SCREEN, HEADING, TEXT, BUTTON, STATUS),
     (S_MAIN, H_OPEN, T_OPEN, B_CLASS, ST_OPEN),
     ("      <main ref={ref}>",
      "      <h1 ref={ref}>",
      "      <p ref={ref} id={id}>",
      "",
      '      <div ref={ref} role="status" aria-live="polite">'), "own"),

    # --- the build: is the stylesheet actually in the bundle? --------------------------------------------
    # Phase 6 admitted `https://tailwindcss.com` to the bundle's URL allowlist because Tailwind's licence
    # banner is now in the emitted CSS (D2). That admission turns out to be a PIN: remove the import and
    # the banner goes with it, so the exact-list assertion fails. An allowlist entry that only ever
    # permits is weaker than one that also requires.
    ("RD05 the stylesheet is never imported, so no CSS is emitted at all",
     MOUNT, CSS_IMPORT + "\n", "", "own"),

    ("RD06 the Tailwind build plugin is dropped, so the utilities compile to nothing",
     VITE, PLUGIN + "\n", "", "own"),

    # A token deleted: `bg-accent` then emits no rule and the one control in the app has no background.
    # Nothing reads `styles.css` today. AC9's contrast arithmetic and AC14's built-asset check are
    # phase 10's, and this row is what says they are worth writing.
    ("RD07 the accent token is deleted, so bg-accent silently emits nothing",
     STYLES, ACCENT + "\n", "", "phase 10"),

    # --- obligations the primitives INHERITED from other tasks' screens ---------------------------------
    # Phase 6's one real behavioural risk, named in the plan's risk table and in RateLimitNotice's
    # header: if the holder stops forwarding its ref, `wrapper.current` is null, `?.focus()` is a no-op,
    # and a keyboard user is dropped to <body> with the button they pressed gone.
    ("RD08 FocusHolder stops forwarding its ref: focus falls to <body> on dismiss (L11)",
     HOLDER, "<div ref={ref} tabIndex={-1}", "<div tabIndex={-1}", "inherited"),

    ("RD09 ActionButton stops forwarding its ref, so the notice cannot tell if its button had focus",
     BUTTON, B_REF, "      type=\"button\"", "inherited"),

    ("RD10 StatusRegion loses aria-live, so nothing it holds is ever announced (L11)",
     STATUS, ' role="status" aria-live="polite"', ' role="status"', "inherited"),

    # The rule the primitive exists to centralise: a live region mounted at the same moment its text
    # arrives is a new element with content already in it, and a screen reader announces nothing.
    ("RD11 StatusRegion renders only when it has something to say, which is the announcement bug",
     STATUS, (ST_OPEN, ST_CLOSE),
     ('      {children === null ? null : (\n        <div ref={ref} role="status" aria-live="polite" '
      'className="text-step--1 text-muted">',
      "        </div>\n      )}"), "inherited"),

    ("RD12 Surface drops the data-testid, so the shell is unfindable",
     SURFACE, " data-testid={testId}", "", "inherited"),

    # --- the holes the named suite has to close ---------------------------------------------------------
    # The `Only` in the task statement. Both of these are "a render site went back to bare elements",
    # which is the regression AC2/AC3's sweep is for - and neither changes anything any current
    # instrument reads. They are the argument for writing that sweep.
    ("RD13 AppShell goes back to a bare div, bypassing the primitive layer (AC2)",
     SHELL, SHELL_RENDER, '  return <div data-testid="app-shell">{children}</div>;', "phase 10"),

    ("RD14 SignInScreen renders a bare main instead of the Screen primitive (AC2)",
     SIGNIN, (SIGNIN_SCREEN, SIGNIN_CLOSE), ("    <main>", "    </main>"), "phase 10"),

    # PHASE 6'S D4 CLAIMED THIS IS "very visible to the R30 pin". This row is that claim under
    # measurement, and I expect it to be WRONG: a literal newline lands identically in all five states,
    # so sameness holds, the list is non-empty, and the classes-only regex only anchors the start.
    ("RD15 a className is wrapped across lines, putting a literal newline in the attribute (D4)",
     SCREEN, S_MAIN,
     '      <main\n        ref={ref}\n        className="mx-auto flex max-w-prose\n          flex-col gap-4'
     ' px-6 py-12"\n      >', "phase 10"),

    # The type-level guarantee is AC4's real instrument - `className` is absent from all eight types, so
    # a screen that styles itself is a compile error. But THE HARNESS DOES NOT RUN TSC and vitest strips
    # types, so this row asks what survives at runtime. It takes TWO files on purpose: a primitive that
    # merely accepts a class nobody passes is a capability with no caller, and its survival would be
    # vacuous - the T41 RD06/RD07 lesson, where two rows spent phase 7 mutating a line that could not
    # run. So the escape has to actually happen: Surface applies it AND AppShell passes one.
    ("RD16 a screen styles itself through Surface, escaping the primitive layer (AC4)",
     (SURFACE, SURFACE, SHELL),
     (SU_PROPS, SU_OPEN, SHELL_RENDER),
     ("  function Surface({ children, 'data-testid': testId, className: extra }, ref) {",
      '      <div ref={ref} data-testid={testId} className={`min-h-dvh bg-surface text-text '
      '${extra ?? \'\'}`}>',
      '  return <Surface data-testid="app-shell" className="bg-red-500">{children}</Surface>;'),
     "phase 10"),

    # --- canary: is the pin's own walker awake? ---------------------------------------------------------
    # The sameness half of R30's pin cannot be falsified from production code - the cause of an identity
    # failure never reaches the screen, which is that file's premise. Its two synthetic controls carry
    # it instead, and this row asks whether they would notice if the walker stopped seeing inline styles.
    ("RD17 CANARY: the pin's walker stops reporting inline styles",
     R30, WALKER, "    for (const attribute of ['class']) {", "own"),
]


def as_tuple(value):
    return value if isinstance(value, tuple) else (value,)


def edits_of(path, old, new):
    """One mutant, one or more (path, old, new) triples. A single replacement cannot express a
    REORDER, a move, or - new in this copy - a change that spans five files."""
    paths, olds, news = as_tuple(path), as_tuple(old), as_tuple(new)
    if len(paths) == 1 and len(olds) > 1:
        paths = paths * len(olds)
    assert len(paths) == len(olds) == len(news), "path/old/new must line up"
    return list(zip(paths, olds, news))


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


def apply(path, old, new):
    for target_path, find, replace in edits_of(path, old, new):
        target = REPO / target_path
        text = target.read_text()
        if text.count(find) != 1:
            return False
        target.write_text(text.replace(find, replace, 1))
    return True


def revert():
    subprocess.run(["git", "checkout", "--", *REVERT_PATHS], cwd=REPO, check=False)
    subprocess.run(["git", "clean", "-fq", "--", *REVERT_PATHS], cwd=REPO, check=False)


def preflight():
    """Every anchor in its file, exactly once, REPLAYED IN MEMORY so a mid-mutant ambiguity is caught
    before any verdict prints. T36 shipped a BROKEN row that the per-anchor check on unmutated text had
    passed, and BROKEN is not a survivor, so the run still ended `survivors: none`."""
    stale = []
    for label, path, old, new, _expect in MUTANTS:
        texts = {}
        for target_path, find, replace in edits_of(path, old, new):
            text = texts.setdefault(target_path, (REPO / target_path).read_text())
            count = text.count(find)
            if count != 1:
                stale.append(f"  {label.split()[0]:<6} {target_path} matches an anchor {count} times, "
                             "expected 1")
                break
            texts[target_path] = text.replace(find, replace, 1)
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
        own = "own" if by_own else "inherited"
        print(f"  KILLED    {own:<14} {label}  ({failed} failed of {total}, exit {code})")
        for name in names:
            print(f"            └ {name}")
        if not by_own:
            _, _, _, where, elsewhere = run(FILES, verbose=True)
            print(f"            └ killed in: {sorted(where - KNOWN_ENVIRONMENTAL_FAILURES)}")
            for name in elsewhere[:4]:
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
        outcomes[label.split()[0]] = (
            "SURVIVED" if lost else ("KILLED/own" if by_own else "KILLED/inherited"), expect)
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
    scored_apart = [label.split()[0] for label in survivors if label.split()[0] in apart]
    if CANARIES:
        print(f"canaries:    {sorted(CANARIES)} - scored apart; a SURVIVING canary means the "
              f"instrument is inert: {[k for k in scored_apart if k in CANARIES] or 'none survived'}")
    if UNFALSIFIABLE:
        print(f"unfalsifiable: {sorted(UNFALSIFIABLE)} - no observable effect to detect")
    print(f"survivors:   {[label.split()[0] for label in survivors] or 'none'}")
    print(f"broken:      {broken or 'none'}")

    # THE PHASE 7 VERDICT. A survivor here is expected for every row phase 10 owns; what matters is a
    # row whose outcome disagrees with its expectation.
    print("\n--- expectation vs outcome ---")
    disagreed = []
    for key, (outcome, expect) in sorted(outcomes.items()):
        if expect == "unfalsifiable":
            flag = "as expected" if outcome == "SURVIVED" else "DISAGREES - it has an observable effect"
        elif expect == "phase 10":
            flag = "as expected" if outcome == "SURVIVED" else f"DISAGREES - already killed ({outcome})"
        elif outcome == "SURVIVED":
            flag = "DISAGREES - nothing detects it"
        elif expect == "inherited" and outcome == "KILLED/own":
            flag = "DISAGREES - this task's own files caught it too (stronger than claimed)"
        elif expect == "own" and outcome == "KILLED/inherited":
            flag = "DISAGREES - killed only OUTSIDE this task's files"
        else:
            flag = "as expected"
        if flag != "as expected":
            disagreed.append(key)
        print(f"  {key:<6} expected {expect:<13} got {outcome:<16} {flag}")
    print(f"\ndisagreements: {disagreed or 'none'}")
    return 1 if broken else 0


if __name__ == "__main__":
    sys.exit(main())
