#!/usr/bin/env python3
"""T44 mutation harness - the 128 end of the advisory range. FOURTEENTH COPY of the machinery (OQ-T32-4).

WHAT THIS IS. Each mutant is a regression this task exists to prevent, applied to real source,
measured against a real baseline, and reverted. A case that cannot tell the mutant from the
original is a case that was never holding anything.

IT REVERTS WITH `git checkout`. COMMIT YOUR TESTS FIRST. It wipes the whole working tree under
the paths in REVERT_PATHS, not just the mutated file - it cost 19 cases in T14, 5 in T15, and a
full phase-9 edit in T36 because I ran it on uncommitted test edits.

INHERITED FROM T43's THIRTEENTH COPY, UNCHANGED: TR01-TR28 and all of the machinery - multi-file and
multi-part mutants; the expectation table that prints BROKEN instead of crediting it as KILLED; `FAIL`-line
pairing with case names from `x` lines only; attribution that subtracts BASELINE-FAILING CASE NAMES rather
than a whole environmental file; BROKEN detected by a DROP IN THE TOTAL; a JSX mutant anchored on a whole
element; and a mutant on a list that feeds `it.each` RENAMING rather than deleting.

WHAT IS NEW: SIX ROWS AT THE UPPER BOUND, AND FIVE OF THEM EXIST BECAUSE TR05 CANNOT ISOLATE THIS END.

  TR05 (`countCodePoints` -> `String.length`) is task 43's row and it is killed by task 43's 7-emoji row.
  It would be killed by this task's rows too, which is exactly why it cannot measure them: a kill that
  several cases produce says nothing about which case is holding the claim. T44's rows are each aimed at
  ONE thing that only the 128 end can see.

  TR29  the maximum widened to 999      - only the 129 row, and the control's new upper-bound pins.
  TR30  the `<=` half of the check gone - only the 129 row. The constant is untouched, so the control
                                          still passes and this is the narrowest possible row for AC3.
  TR31  `<=` becomes `<`                - only the two rows that sit ON 128. An off-by-one at a boundary
                                          is invisible to every row that is not at the boundary.
  TR32  `maxLength={128}` on the field  - THE ROW THIS TASK WAS BUILT AROUND. Phase 3's F4 claimed the
                                          body-equality assertion would catch it. The freeze measured that
                                          it will not: `maxLength` constrains USER EDITING, and every case
                                          in the suite sets `value` through `HTMLInputElement.prototype`'s
                                          setter, so 256 units go in and 256 come back. If this row dies
                                          ONLY in the two new AC4 pins, the measurement was right and both
                                          pins are load-bearing. If it dies anywhere else, F4 was right and
                                          the freeze was wrong - which is news either way.
  TR33  the upper comparison reads      - the `.length` bug ISOLATED to this end. At 12 ASCII and at 12
        `password.length`                 astral it changes nothing (24 <= 128), so every task 43 row still
                                          passes; at 128 astral it reads 256 > 128 and rejects a valid
                                          password. Only the astral row at the upper bound can see it.
  TR34  CANARY a 129-in-range row       - TR25's mirror: it asks whether the table's `aria-invalid`
                                          assertion is awake at the END the rows were added to, rather
                                          than at the end task 43 built.

FOUR OUTCOMES, AND EVERY ROW PREDICTS ONE:

  `own`           killed, AND with a named failure in a file this task owns or grew.
  `inherited`     killed ONLY outside this task's files.
  `unfalsifiable` no observable effect of any kind, in the DOM or in the file; scored apart.
  canary          mutates TEST code to ask whether an instrument is awake; scored apart.

    python3 -I .ai/prompts/frontend/T44/artifacts/07-mutation-harness.py        # all
    python3 -I .ai/prompts/frontend/T44/artifacts/07-mutation-harness.py TR32   # one
"""

import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[5]
FRONTEND = REPO / "frontend"

SCREEN = "frontend/src/features/account/RegisterScreen.tsx"
LENGTH = "frontend/src/features/account/passwordLength.ts"
FORM = "frontend/src/app/ui/Form.tsx"
EMAIL = "frontend/src/app/ui/EmailField.tsx"
PASSWORD = "frontend/src/app/ui/PasswordField.tsx"
SUBMIT = "frontend/src/app/ui/SubmitButton.tsx"
ROUTES = "frontend/src/app/routes.tsx"
ONLY = "frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx"
CREDENTIALS = "frontend/src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx"
CATALOGUE = "frontend/src/i18n/catalogue.ts"
SUITE = "frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx"

# THIS TASK'S OWN FILES: the two test files phase 6 changed. Task 43 grew six more instruments and
# listed them here; T44 touched none of those, so a kill in one of them is INHERITED for this task - the
# case that did the killing is task 43's work, not this one's.
OWN_TESTS = [
    "src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx",
    "src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx",
]

# TR25 and TR34 mutate TEST code: each plants a row into the length table claiming a password OUTSIDE
# the range is inside it - 11 code points at the low end, 129 at the high end. `it.each` over a table whose rows all expect the same outcome measures the row count and
# nothing else, so this is the row that asks whether the table's assertions are awake. Scored apart.
CANARIES = {"TR25", "TR34"}

# TR24 is predicted to have no observable effect HERE, and the limit is jsdom's rather than the suite's:
# it performs no interactive constraint validation, so removing `noValidate` changes nothing a case can
# see. Phase 6's D-2 deviation is the decision under measurement, and a KILL would be news.
UNFALSIFIABLE = {"TR24"}

FILES = []  # empty = the whole suite, so inherited kills anywhere are visible

REVERT_PATHS = ["frontend/src/", "frontend/vite.config.ts"]

EXPECTED_BASELINE_FAILURES = 1  # of 1945: 45 in the named suite, +1 in the design-system suite
KNOWN_ENVIRONMENTAL_FAILURES = {
    # This host's oxlint@1.87.0 prints no summary line, so the assertion that parses it cannot pass.
    # USED ONLY FOR THE BASELINE REFUSAL CHECK. Attribution subtracts baseline-failing CASE NAMES, not
    # this file, because 32 of its 33 cases are real - including the repository's only end-to-end build
    # gate, which runs a real `pnpm run build` and is the instrument that holds every type-level claim.
    "src/api/lintRejectsHandWrittenBackendFetch.test.ts",
}

# --- anchors, copied from the real source ----------------------------------------------------------------
# Each is CODE, never prose. A mutant whose anchor matches a comment is a mutant that might edit the
# comment instead (T174's preflight refused a run for exactly that).
REACT_IMPORT = "import { useState, type ReactElement } from 'react';"
ADVISORY_STATE = ("  const [advisory, setAdvisory] = useState<'unchecked' | 'in-range' | "
                  "'out-of-range'>('unchecked');")
ADVISORY_CHECK = """    setAdvisory(length >= ADVISORY_MINIMUM_CODE_POINTS && length <= ADVISORY_MAXIMUM_CODE_POINTS
      ? 'in-range' : 'out-of-range');"""
# PHASE 9 ADDED THE SECOND ARGUMENT (I7): a rejected send returns the form to idle. Every row that
# replaces this block keeps that handler, so each changes only the thing it names - and TR26 is the row
# that measures the handler itself by deleting it.
SEND = """    void createPublicAuthApi().POST('/accounts', { body: { email, password } })
      .then(() => setStatus('sent'), () => setStatus('idle'));"""
REJECTION_HANDLER = ", () => setStatus('idle')"
ACTION_CLASSES = ('      className="self-start rounded-md bg-accent px-4 py-2 text-step-0 font-medium '
                  'text-text-inverse outline-offset-2 focus-visible:outline-2 focus-visible:outline-focus '
                  'aria-disabled:opacity-70"')
ACTION_BUTTON = "frontend/src/app/ui/ActionButton.tsx"
SUBMIT_UTILITIES = "  SubmitButton: ['bg-accent', 'text-text-inverse', 'text-step-0'],"
SUBMITTING = "    setStatus('submitting');"
FORM_OPEN = "      <Form onSubmit={submit}>"
FORM_CLOSE = "      </Form>"
EMAIL_FIELD = "        <EmailField label={t('register.emailLabel')} value={email} onChange={setEmail} />"
STATUS_BLOCK = """      <StatusRegion>
        {status === 'idle' ? null : t(status === 'submitting' ? 'register.submitting' : 'register.success')}
      </StatusRegion>"""
COUNT_BODY = "  return [...value].length;"

# THE WHOLE ELEMENT, NOT ITS OPENING TAG (T42's three BROKEN rows). `Form.tsx`'s block closes with
# `</form>`, so a mutant that replaced only the opening line would leave the file unparseable and every
# importer of the barrel would fail to LOAD - which is not a kill, and which measures nothing.
FORM_IMPORT = "import { forwardRef, type ReactNode } from 'react';"
FORM_BLOCK = """      <form
        ref={ref}
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          onSubmit();
        }}
        className="flex w-full flex-col gap-2"
      >
        {children}
      </form>"""
NO_VALIDATE = "        noValidate\n"
PREVENT = "          event.preventDefault();\n"
EMAIL_TYPE = '        type="email"'
PASSWORD_TYPE = '        type="password"'
EMAIL_LABEL_BLOCK = """      <label htmlFor={id} className="text-step-0 font-medium text-text">
        {label}
      </label>"""
SUBMIT_TYPE = '        type="submit"'
SUBMIT_CLASSES = ('        className="self-start rounded-md bg-accent px-4 py-2 text-step-0 font-medium '
                  'text-text-inverse outline-offset-2 focus-visible:outline-2 focus-visible:outline-focus"')
RENDER_SITE = "  'features/account/RegisterScreen.tsx',"
PRIMITIVE = "  'Form',"
PRIMITIVE_COUNT = ("    expect(PRIMITIVES, 'the primitive list changed size without the brief changing')"
                   ".toHaveLength(13);")
CREDENTIAL_ROW = """  {
    file: 'features/account/RegisterScreen.tsx',
    what: 'a form element',
    count: 1,"""
AUTHENTICATED_SET = """export const AUTHENTICATED_ROUTE_PATHS = [
  HOME_PATH,"""
EMAIL_KEY = "    emailLabel: 'Email address',"
TABLE_LAST_ROW = ("  { name: '7 emoji, which `.length` would call 14', password: '\U0001f44d'.repeat(7), "
                  "codePoints: 7, inRange: false },")

# --- task 44's anchors, all of them CODE ------------------------------------------------------------------
MAXIMUM_CONST = "export const ADVISORY_MAXIMUM_CODE_POINTS = 128;"
UPPER_COMPARISON = "length <= ADVISORY_MAXIMUM_CODE_POINTS"
UPPER_HALF = " && length <= ADVISORY_MAXIMUM_CODE_POINTS"
PASSWORD_ROW_129 = ("  { name: '129 ASCII characters, one past the upper bound', password: 'a'.repeat(129), "
                    "codePoints: 129, inRange: false },")

MUTANTS = [
    # --- AC1: the form, its two named fields and its one submit control --------------------------------
    # The form element itself, gone, with the screen untouched: `Form` renders a `<div>`, so nothing
    # submits and no `<form>` exists to carry the fields. Two-part, one file, because the ref cast needs
    # `Ref` imported - `Form.tsx` imports no React namespace and `React.Ref` would fail the build gate,
    # which is a kill in a file this task does not own and not what this row claims to measure.
    ("TR01 Form renders a div, so there is no form element and nothing submits",
     FORM, (FORM_IMPORT, FORM_BLOCK),
     ("import { forwardRef, type ReactNode, type Ref } from 'react';",
      '      <div ref={ref as unknown as Ref<HTMLDivElement>} className="flex w-full flex-col gap-2">\n'
      "        {children}\n      </div>"), "own"),
    ("TR02 EmailField renders type=text, so the email field is an ordinary text box",
     EMAIL, EMAIL_TYPE, '        type="text"', "own"),
    # The password unmasked. Every value typed into it becomes visible, and the one field whose content
    # must never be read back is readable by anything that walks the DOM.
    ("TR03 PasswordField renders type=text, so the password is not masked",
     PASSWORD, PASSWORD_TYPE, '        type="text"', "own"),
    # The LABEL kept as text and the ASSOCIATION dropped. A `<span>` renders the same words in the same
    # place and leaves the control with no accessible name - the failure a reader of the screen cannot
    # see and `input.labels` can.
    ("TR04 EmailField labels with a span, so the control has no accessible name",
     EMAIL, EMAIL_LABEL_BLOCK,
     '      <span className="text-step-0 font-medium text-text">\n        {label}\n      </span>', "own"),

    # --- AC3: code points, not UTF-16 units -----------------------------------------------------------
    # THE ROW THIS TASK IS NAMED FOR. `.length` calls twelve emoji 24 - still in range, so the accepting
    # end cannot tell - and calls SEVEN emoji 14, which is in range when the truth is 7. The table's
    # 7-emoji row exists for exactly this, and nothing else in the suite would notice.
    ("TR05 the count becomes String.length, so emoji count twice",
     LENGTH, COUNT_BODY, "  return value.length;", "own"),

    # --- AC4: advisory means it marks and does not block -----------------------------------------------
    ("TR06 the submit is blocked unless the count is in range",
     SCREEN, SEND,
     "    if (length >= ADVISORY_MINIMUM_CODE_POINTS && length <= ADVISORY_MAXIMUM_CODE_POINTS) {\n"
     "      void createPublicAuthApi().POST('/accounts', { body: { email, password } })\n"
     "        .then(() => setStatus('sent'), () => setStatus('idle'));\n    }", "own"),

    # --- AC5: through the generated client, with no bearer ---------------------------------------------
    ("TR07 the call is replaced by a hand-written fetch",
     SCREEN, SEND,
     "    void fetch('/accounts', { method: 'POST', body: JSON.stringify({ email, password }) })\n"
     "      .then(() => setStatus('sent'), () => setStatus('idle'));", "own"),

    # --- AC6: every word comes from the catalogue ------------------------------------------------------
    # The key renamed, which is what a missing key looks like: the DOM shows `register.emailLabel`.
    ("TR08 the catalogue key is renamed, so the label resolves to the key itself",
     CATALOGUE, EMAIL_KEY, "    emailLabelText: 'Email address',", "own"),
    # PREDICTED TO SURVIVE, AND IT IS A HOLE. Hardcoded English is BYTE-FOR-BYTE what the catalogue
    # holds, so every copy assertion in the suite passes: comparing the DOM with the catalogue cannot
    # distinguish a lookup from a literal. Phase 10 owes a source assertion that the screen reaches `t()`
    # for all five strings.
    # RE-AIMED TO `own` AFTER PHASE 10 ADDED THE SOURCE SCAN. It survived in phase 7 because hardcoded
    # English is byte-for-byte the catalogue's, so every DOM assertion passed; the scan asserts both
    # directions - every key reached, no sentence spelled - and is the only instrument that can tell.
    ("TR09 the email label is hardcoded in English in the screen",
     SCREEN, EMAIL_FIELD,
     '        <EmailField label="Email address" value={email} onChange={setEmail} />', "own"),

    # --- AC7: the screen renders no element of its own, and the lists say so ---------------------------
    # The whole point of §6e(3), and three instruments should see it: the screen's tag scan, the bearer
    # sweep across all of src/, and the credential table, which counts `<Form` and would now find two.
    ("TR10 the screen renders a lowercase form tag of its own",
     SCREEN, (FORM_OPEN, FORM_CLOSE),
     ("      <form onSubmit={submit}>", "      </form>"), "own"),
    ("TR11 RegisterScreen.tsx is dropped from the render-site list",
     ONLY, RENDER_SITE, "", "own"),
    # A RENAME, NOT A DELETION (T42's lesson). `it.each(PRIMITIVES)` generates one case per entry, so
    # dropping one drops a TEST and the fall in the total is indistinguishable from a file that never
    # loaded. A rename keeps the count and breaks the read.
    ("TR12 Form is renamed in the primitive list, so the layer has an unpinned primitive",
     ONLY, PRIMITIVE, "  'Forme',", "own"),
    ("TR13 the primitive count is reverted to nine",
     ONLY, PRIMITIVE_COUNT,
     "    expect(PRIMITIVES, 'the primitive list changed size without the brief changing')"
     ".toHaveLength(9);", "own"),
    # The excuse deleted. T16's scan then reports an unexplained credential shape in production, which is
    # the friction working - and the staleness case reports the row that no longer describes anything.
    ("TR14 the screen's row is deleted from the credential table",
     CREDENTIALS, CREDENTIAL_ROW, "  {\n    file: 'nothing/at/all.tsx',\n    what: 'a form element',\n"
     "    count: 1,", "own"),

    # --- AC8: public, and no new path -----------------------------------------------------------------
    ("TR15 register is added to the authenticated route set",
     ROUTES, AUTHENTICATED_SET,
     "export const AUTHENTICATED_ROUTE_PATHS = [\n  REGISTER_PATH,\n  HOME_PATH,", "own"),

    # --- AC9: keyboard-reachable, and a real submit ----------------------------------------------------
    # `type="button"` is the regression `ActionButton`'s own header describes: Enter inside a field stops
    # submitting, and the only thing that still works is a mouse click on the control.
    ("TR16 SubmitButton becomes type=button, so Enter in a field submits nothing",
     SUBMIT, SUBMIT_TYPE, '        type="button"', "own"),
    ("TR17 SubmitButton loses its focus ring",
     SUBMIT, SUBMIT_CLASSES,
     '        className="self-start rounded-md bg-accent px-4 py-2 text-step-0 font-medium '
     'text-text-inverse"', "own"),

    # --- AC10: nothing is stored --------------------------------------------------------------------
    ("TR18 the email is written to sessionStorage",
     SCREEN, SUBMITTING,
     "    globalThis.sessionStorage.setItem('register.email', email);\n" + SUBMITTING, "own"),

    # --- AC11: no branching on the response ----------------------------------------------------------
    ("TR19 the handler branches on the response status",
     SCREEN, SEND,
     "    void createPublicAuthApi().POST('/accounts', { body: { email, password } })\n"
     "      .then((reply) => setStatus(reply.response.status === 202 ? 'sent' : 'idle'),\n"
     "        () => setStatus('idle'));", "own"),

    # --- D1: the live region is pre-mounted ----------------------------------------------------------
    # The regression D1 exists to prevent, and it is INVISIBLE to a case that only reads the text after
    # the submit: the words appear either way. Only the before-state and the node identity can tell.
    ("TR20 the status region is rendered conditionally instead of pre-mounted",
     SCREEN, STATUS_BLOCK,
     "      {status === 'idle' ? null : (\n        <StatusRegion>\n"
     "          {t(status === 'submitting' ? 'register.submitting' : 'register.success')}\n"
     "        </StatusRegion>\n      )}", "own"),

    # --- D5: the screen does not move focus ----------------------------------------------------------
    # PREDICTED TO SURVIVE. D5 froze "no focus move on mount" and the suite has no case for it: AC9
    # focuses each control deliberately, so a screen that stole focus on arrival would pass every row.
    # Phase 10 owes the case - the shape task 174 wrote for the opposite claim.
    ("TR21 the screen focuses the first field on mount",
     SCREEN, (REACT_IMPORT, ADVISORY_STATE),
     ("import { useEffect, useState, type ReactElement } from 'react';",
      ADVISORY_STATE + "\n\n  useEffect(() => {\n"
      "    document.querySelector<HTMLInputElement>('input')?.focus();\n  }, []);"), "own"),

    # --- D13: preventDefault lives in the primitive ---------------------------------------------------
    ("TR22 Form stops calling preventDefault, so a browser leaves the SPA",
     FORM, PREVENT, "", "own"),

    # --- D17's unfalsifiable row, RE-AIMED (phase 6's D-1) -------------------------------------------
    # The freeze called this unfalsifiable: an advisory check nothing consumes changes nothing. Phase 6
    # gave it a consumer - `aria-invalid` - so it is now a regression with an observable effect, and the
    # re-aim is recorded here rather than applied to the outcome after the fact.
    ("TR23 the advisory check always reports in range",
     SCREEN, ADVISORY_CHECK, "    setAdvisory('in-range');", "own"),

    # --- the honest unfalsifiable row ----------------------------------------------------------------
    ("TR24 Form drops noValidate, so the browser validates before the handler runs",
     FORM, NO_VALIDATE, "", "unfalsifiable"),

    # --- the canary: a TEST mutation, scored apart ---------------------------------------------------
    ("TR25 CANARY an 11-code-point row claims to be in range",
     SUITE, TABLE_LAST_ROW,
     TABLE_LAST_ROW + "\n  { name: 'CANARY', password: 'a'.repeat(11), codePoints: 11, inRange: true },",
     "own"),

    # --- F2 / I7, now that there is a handler to delete ----------------------------------------------
    # THE ROW PHASE 7 SHOULD HAVE WRITTEN. Its predecessor replaced the whole send with a rejecting
    # promise, so thirteen cases failed because NO REQUEST WAS SENT - a kill that measured the absence of
    # a send, not the absence of a handler. This deletes the handler alone, leaves the real call in place,
    # and can only be seen by a case whose transport throws. Everything else in the suite still passes.
    ("TR26 the rejection handler is deleted, so an offline submit hangs on the in-flight copy",
     SCREEN, REJECTION_HANDLER, "", "own"),

    # --- the pair pin phase 9 added (F3 / I8) --------------------------------------------------------
    # `SubmitButton`'s header claimed the two buttons were pinned to ONE appearance when nothing compared
    # them. This changes `ActionButton`'s accent alone: both files still satisfy their own rows, every
    # other case passes, and only a pin on the PAIR can tell that the application now has two button
    # appearances. It is the row that makes a comment true.
    ("TR27 ActionButton's accent is changed, so the two buttons stop matching",
     ACTION_BUTTON, ACTION_CLASSES,
     ACTION_CLASSES.replace("bg-accent", "bg-surface"), "own"),

    # TR27 WAS KILLED AND DID NOT ISOLATE ITS CLAIM - the same defect the old TR26 had, found by reading
    # WHICH cases died rather than how many. T173's own `gives ActionButton the colour and type utilities
    # it owns` fails on that mutant too, so the pair pin was never the only thing that could see it.
    #
    # This is the row that isolates it: `SubmitButton`'s accent AND its row in `REQUIRED_UTILITIES` change
    # together, which is what a well-meant restyle of one button looks like. Every per-file row still
    # passes - each file satisfies its own, now different, row - and the ONLY instrument left that can say
    # the application has two button appearances is the pin on the PAIR.
    ("TR28 SubmitButton is restyled AND its utility row is updated with it, so only the pair pin can tell",
     (SUBMIT, ONLY), (SUBMIT_CLASSES, SUBMIT_UTILITIES),
     (SUBMIT_CLASSES.replace("bg-accent", "bg-surface"),
      SUBMIT_UTILITIES.replace("bg-accent", "bg-surface")), "own"),

    # ======================================================================================================
    # TASK 44 - the upper bound. Every row below is aimed at something ONLY the 128 end can see.
    # ======================================================================================================

    # The maximum widened past anything a visitor would type. The check still runs, still marks, and still
    # counts code points - it just stops being a maximum. The 129 row is the only password in the table it
    # judges differently, and the control's two new assertions fail with it because no row sits on 999.
    ("TR29 the advisory maximum is widened to 999, so 128 stops being the maximum",
     LENGTH, MAXIMUM_CONST, "export const ADVISORY_MAXIMUM_CODE_POINTS = 999;", "own"),

    # THE NARROWEST ROW FOR AC3, and narrower than TR29 on purpose: the constant keeps its value, so the
    # control still finds its row on the maximum and its row above it, and the ONLY thing left that can
    # notice there is no upper bound any more is a password that is over it.
    ("TR30 the upper half of the comparison is deleted, so there is no maximum at all",
     SCREEN, UPPER_HALF, "", "own"),

    # An off-by-one AT the boundary, which is invisible to every row that is not at the boundary: 13 and
    # 11 and 7 emoji are judged exactly as before, and the two rows sitting ON 128 flip to out of range.
    ("TR31 the maximum becomes exclusive, so the bound itself is rejected",
     SCREEN, UPPER_COMPARISON, "length < ADVISORY_MAXIMUM_CODE_POINTS", "own"),

    # THE ROW THIS TASK WAS BUILT AROUND (phase 3 F4, and the freeze's measurement against it). A field
    # that caps its own length in UTF-16 units truncates a 128-code-point passphrase of emoji to 64
    # characters before any handler runs. The claim under test is NEGATIVE and belongs to the two pins
    # phase 6 added: if this row dies anywhere else, the freeze's measurement was wrong.
    ("TR32 PasswordField caps its own length at 128 UTF-16 units, which truncates a valid password",
     PASSWORD, PASSWORD_TYPE, '        type="password"\n        maxLength={128}', "own"),

    # THE `.length` BUG, ISOLATED TO THIS END. TR05 swaps the counter and is killed by task 43's 7-emoji
    # row, so it cannot say whether the 128 end is measured. This changes only the UPPER comparison, and
    # it is correct for every password task 43 wrote a row for: 12 ASCII is 12 units, 12 emoji are 24, and
    # both are under 128 either way. At 128 emoji it reads 256, calls a valid password too long, and marks
    # it invalid - the false REJECT, which is the direction the bug points at this end.
    ("TR33 the upper comparison counts UTF-16 units, so a valid astral password is marked too long",
     SCREEN, UPPER_COMPARISON, "password.length <= ADVISORY_MAXIMUM_CODE_POINTS", "own"),

    # TR25's mirror. A table whose rows all expect the same outcome measures the row count and nothing
    # else; TR25 asks whether the `aria-invalid` assertion is awake at the end task 43 built, and this
    # asks it at the end task 44 added. Scored apart.
    ("TR34 CANARY a 129-code-point row claims to be in range",
     SUITE, PASSWORD_ROW_129,
     PASSWORD_ROW_129 + "\n  { name: 'CANARY', password: 'a'.repeat(129), codePoints: 129, inRange: true },",
     "own"),
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
    pairs = []
    names = []
    for line in (proc.stdout + proc.stderr).splitlines():
        stripped = line.strip()
        if stripped.startswith("Tests "):
            body = stripped[len("Tests "):]
            failed = int(body.split("failed")[0].strip()) if "failed" in body else 0
            if "(" in body:
                total = int(body.split("(")[-1].rstrip(")"))
        # `(?:ts|tsx)` tried `ts` FIRST and nothing anchored the end, so every `.tsx` path was recorded
        # as `.ts` - a file that does not exist. Nine copies printed corrupt file lists (phase 8, I4).
        # ELEVENTH COPY. The tenth collected files from `FAIL` lines and case names from `×` lines, as two
        # independent sets - so `in:` named every file that had ANY failure, including one whose only
        # failing case was already failing at baseline. A `FAIL` line carries both halves itself
        # (`FAIL  <file> > <suite> > <case>`), so they are read from the same line and stay paired.
        match = re.search(r"\bFAIL\b\s+(\S+\.tsx?)\b(.*)$", stripped)
        if match:
            pairs.append((match.group(1), match.group(2).split(" > ")[-1].strip()))
        # NAMES COME FROM `×` LINES ONLY. vitest prints every failing case TWICE - once in the file
        # listing and once in the summary - so taking names from both sources printed each kill twice.
        # The FAIL line is used for the file/case PAIRING above and for nothing else.
        if verbose and stripped.startswith("× "):
            leaf = stripped[2:].split(" > ")[-1]
            names.append(re.sub(r"\s+\d+ms$", "", leaf).strip())
    files_failed = {path for path, _ in pairs}
    return proc.returncode, failed, total, files_failed, names, pairs


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


def score(label, base):
    """`base` is (failed, total, cases) from the unmutated run. Two guards phase 7 did not have:

    A MUTANT THAT STOPS A FILE FROM LOADING IS BROKEN, NOT KILLED. RD11 was a syntax error; 23 suites
    failed to PARSE and the raised failure count was credited as a kill, with `broken: none` printed
    underneath (F2, I2). A file that cannot be loaded contributes NO tests, so the total drops - and that
    is the one signal which separates "a case caught it" from "nothing ran". T36 lost the same way.

    ATTRIBUTION IS BY CASE NAME, NOT BY FILE. Subtracting the whole known-environmental FILE hid RD16's
    real kill inside it and printed `killed in: []` (F3, I3). What is subtracted now is the set of case
    names that already fail at baseline, so the other 32 cases in that file - including the only
    end-to-end build gate in the repository - can kill a mutant and be seen doing it."""
    base_failed, base_total, base_cases = base
    code, failed, total, files_failed, names, pairs = run(FILES, verbose=True)
    if failed is None:
        print(f"  BROKEN    no summary     {label}")
        return None, True, None, False
    if total is not None and base_total is not None and total < base_total:
        print(f"  BROKEN    {total} of {base_total} ran  {label}  "
              f"-> {base_total - total} tests never ran: a file did not LOAD, so nothing measured it")
        return None, True, None, False
    new = [name for name in names if name not in base_cases]
    if not new:
        print(f"  SURVIVED  -              {label}  ({failed} failed of {total}, exit {code})")
        return label, False, None, False
    _, _, _, _, own_names, _ = run(OWN_TESTS, verbose=True)
    own_new = [name for name in own_names if name not in base_cases]
    by_own = bool(own_new)
    own = "own" if by_own else "inherited"
    print(f"  KILLED    {own:<14} {label}  ({failed} failed of {total}, exit {code})")
    # ELEVENTH COPY: `in:` names only files holding a NEW failure. The tenth printed every file with any
    # failure at all, so a file whose sole failure was the environmental one was reported as a killer.
    print(f"            └ in: {sorted({f for f, case in pairs if case not in base_cases})}")
    for name in (own_new if by_own else new):
        print(f"            └ {name}")
    return None, False, new, by_own


def main():
    wanted = sys.argv[1] if len(sys.argv) > 1 else ""
    print(f"REPO = {REPO}\n")
    revert()
    if not preflight():
        return 1

    code, failed, total, files_failed, base_cases, _ = run(FILES, verbose=True)
    print(f"baseline whole suite: {failed} failed of {total}, exit {code}")
    print(f"baseline failing cases (excluded from every attribution below): {base_cases}\n")
    if not files_failed <= KNOWN_ENVIRONMENTAL_FAILURES:
        print("REFUSING TO RUN: a file outside the known environmental set is failing.\n"
              f"  unexpectedly failing: {sorted(files_failed - KNOWN_ENVIRONMENTAL_FAILURES)}")
        return 1
    if failed != EXPECTED_BASELINE_FAILURES:
        print(f"NOTE: baseline is {failed}, not the recorded {EXPECTED_BASELINE_FAILURES}; all are in the\n"
              "known environmental set, so the measured floor is sound.\n")
    base = (failed, total, set(base_cases))

    survivors, broken, outcomes = [], [], {}
    for label, path, old, new, expect in MUTANTS:
        if wanted and wanted not in label:
            continue
        if not apply(path, old, new):
            print(f"  BROKEN    anchor missed  {label}")
            outcomes[label.split()[0]] = ("BROKEN", expect)
            broken.append(label)
            revert()
            continue
        lost, was_broken, _names, by_own = score(label, base)
        # A BROKEN ROW IS NOT AN OUTCOME. `score` returns (None, True, None, False) for BROKEN, which
        # this line read as "not a survivor, not own" and printed as KILLED/inherited - the exact sin the
        # guard inside `score` exists to prevent, one level up. The kill COUNT already excluded it; the
        # table credited it. Eleven copies carried this and only this one had a BROKEN row to expose it.
        outcomes[label.split()[0]] = (
            "BROKEN" if was_broken else ("SURVIVED" if lost else
                                         ("KILLED/own" if by_own else "KILLED/inherited")), expect)
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
        if outcome == "BROKEN":
            flag = "DISAGREES - nothing ran, so this criterion is UNMEASURED"
        elif expect == "unfalsifiable":
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
