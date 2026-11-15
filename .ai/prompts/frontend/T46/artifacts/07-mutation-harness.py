#!/usr/bin/env python3
"""T46 mutation harness - the counting unit. SIXTEENTH COPY of the machinery (OQ-T32-4).

WHAT THIS IS. Each mutant is a regression this task exists to prevent, applied to real source,
measured against a real baseline, and reverted. A case that cannot tell the mutant from the
original is a case that was never holding anything.

IT REVERTS WITH `git checkout`. COMMIT YOUR TESTS FIRST. It wipes the whole working tree under
the paths in REVERT_PATHS, not just the mutated file.

RUN IT WITH THE LOCAL DOCKER STACK STOPPED. T44 diagnosed FU-T42-1: the sign-in cases that count
hand-overs and storage writes, and the two lint cases that spawn a process, are load-sensitive. Nine
clean runs on a quiet machine against three flaky of seven with eleven containers up. Two T44 runs
REFUSED at the baseline guard because of it, which is the guard working rather than the harness failing.

INHERITED FROM T44's FOURTEENTH COPY: TR01-TR35 and all of the machinery.

NEW: SEVEN ROWS ON THE GUIDANCE PATH, AND TWO OF THEM ARE INVISIBLE ON SCREEN.

  TR36  the guidance never renders        - the task undone. Everything that looks for the sentence.
  TR37  the guidance always renders       - in range too, which only AC3 and AC5 can see.
  TR38  rendered unless IN range          - so it is up before the first submit, which only AC5 sees.
                                            `!= 'in-range'` rather than `!= 'unchecked'`: the latter is
                                            equivalent to the original, since the state has three values
                                            and only one of them renders.
  TR39  the field stops pointing at it    - the sentence is still on the page and nothing names it.
  TR40  the sentence hardcoded in the screen - BYTE-IDENTICAL ON SCREEN. Only the source scan can see it.
  TR41  the catalogue's {{min}}/{{max}} replaced by 12 and 128 - ALSO BYTE-IDENTICAL, and this is the row
                                            this task was built around: the rendered sentence is the same
                                            to the pixel, so the only thing that can tell them apart is an
                                            assertion that the rendered numbers ARE the constants.
  TR42  the copy becomes a verdict        - "Your password is too short." The equality cases do NOT die
                                            (they derive the expected sentence from the catalogue, so they
                                            move with it); what dies is the pin on the actual words, the
                                            number extraction, and the verdict scan.
  TR43  CANARY an in-range password claims to be out of range.
  TR44  the SCREEN interpolates literals - phase 7 measured this surviving all 126 cases in the
                                            three files this task owns. Only a source scan sees it.
  TR45  the template's bounds swapped - the mutant the digit-identity assertion never had.
  TR46  the sentence after the submit control - the reading-order claim the screen's comment made
                                            and nothing held until phase 9.
  TR47  the LOWER comparison counts units - the end where the counting bug withholds the sentence
                                            rather than raising it wrongly.
  TR48  the advisory resets on every keystroke - the state machine's other edge.
  TR49  DELETED - it would have been TR05 byte for byte. The mutant this task is named for was
                                            written by task 43; what changes here is WHERE it dies.
  TR50  the spread becomes Array.from - EQUIVALENT, and it MUST SURVIVE. A suite that killed it would
                                            be pinning the syntax of today's implementation rather than
                                            the unit R1 names. Scored as unfalsifiable.
  TR51  the count is always 0 - the crudest break there is; if this survives anywhere, that case is
                                            asserting nothing.
  TR52  the count normalises first - dies on ONE row, the decomposed accent, which is what makes
                                            OQ-T46-2's pin load-bearing rather than decorative.
  TR53  the function throws on the empty string - totality, which `passwordLength.ts`'s header claims.
  TR54  a fourth export nobody uses - the export-shape pin.
  TR55  the minimum becomes 11 - predicted own AND inherited: three screen suites pin it too.
  TR56  CANARY a table row claims an astral string's code points equal its UTF-16 units.

FOUR OUTCOMES, AND EVERY ROW PREDICTS ONE:

  `own`           killed, AND with a named failure in a file this task owns or grew.
  `inherited`     killed ONLY outside this task's files.
  `unfalsifiable` no observable effect of any kind; scored apart.
  canary          mutates TEST code to ask whether an instrument is awake; scored apart.

    python3 -I .ai/prompts/frontend/T46/artifacts/07-mutation-harness.py        # all
    python3 -I .ai/prompts/frontend/T46/artifacts/07-mutation-harness.py TR49   # one
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

# THIS TASK'S OWN FILES: the suite it wrote, and the two instruments it grew. T44's own two files are
# here as well - this task edited one line in each - so a kill there is reported `own`, which is honest:
# the reachable-keys line and the catalogue key-set row are task 45's edits inside task 43's and 173's
# files.
OWN_TESTS = [
    "src/features/account/passwordLengthCountsCodePoints.test.ts",
]

# TR25 and TR34 mutate TEST code: each plants a row into the length table claiming a password OUTSIDE
# the range is inside it - 11 code points at the low end, 129 at the high end. `it.each` over a table whose rows all expect the same outcome measures the row count and
# nothing else, so this is the row that asks whether the table's assertions are awake. Scored apart.
CANARIES = {"TR25", "TR34", "TR43", "TR56"}

# TR24 is predicted to have no observable effect HERE, and the limit is jsdom's rather than the suite's:
# it performs no interactive constraint validation, so removing `noValidate` changes nothing a case can
# see. Phase 6's D-2 deviation is the decision under measurement, and a KILL would be news.
UNFALSIFIABLE = {"TR24", "TR50"}

FILES = []  # empty = the whole suite, so inherited kills anywhere are visible

REVERT_PATHS = ["frontend/src/", "frontend/vite.config.ts"]

EXPECTED_BASELINE_FAILURES = 1  # of 1962: 16 in task 45's named suite
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
MINIMUM_CONST = "export const ADVISORY_MINIMUM_CODE_POINTS = 12;"

# --- task 46's anchors: one row of its table. THE FUNCTION'S BODY IS ALREADY AN ANCHOR, `COUNT_BODY`
# above, written by task 43 for TR05 - see the note on TR49. --------------------------------------------
COUNT_SUITE = "frontend/src/features/account/passwordLengthCountsCodePoints.test.ts"
ASTRAL_ROW = ("  { name: 'one astral character', input: '\\u{1F44D}', codePoints: 1, utf16Units: 2 },")
UPPER_COMPARISON = "length <= ADVISORY_MAXIMUM_CODE_POINTS"
LOWER_COMPARISON = "length >= ADVISORY_MINIMUM_CODE_POINTS"
PASSWORD_ONCHANGE = "          onChange={setPassword}"
UPPER_HALF = " && length <= ADVISORY_MAXIMUM_CODE_POINTS"
PASSWORD_ROW_129 = ("  { name: '129 ASCII characters, one past the upper bound', password: 'a'.repeat(129), "
                    "codePoints: 129, inRange: false },")

# --- task 45's anchors ------------------------------------------------------------------------------------
GUIDANCE_ELEMENT = "        {advisory === 'out-of-range' ? <Text id={GUIDANCE_ID}>{guidance}</Text> : null}"
# (GUIDANCE_CONDITION was removed: it matched three lines, and the preflight refused the run.)
FIELD_DESCRIBEDBY = "          aria-describedby={advisory === 'out-of-range' ? GUIDANCE_ID : undefined}\n"
GUIDANCE_LOOKUP = """  const guidance = t('register.passwordGuidance', {
    min: ADVISORY_MINIMUM_CODE_POINTS,
    max: ADVISORY_MAXIMUM_CODE_POINTS,
  });"""
GUIDANCE_STRING = "    passwordGuidance: 'Use a password of {{min}} to {{max}} characters.',"
# The two adjacent lines whose ORDER is the claim (TR46). A reorder of adjacent lines is ONE edit (T36).
GUIDANCE_THEN_SUBMIT = (GUIDANCE_ELEMENT
                        + "\n        <SubmitButton>{t('register.submit')}</SubmitButton>")
REJECT_SUITE = ("frontend/src/features/account/"
                "registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx")
OUT_OF_RANGE_LAST_ROW = ("  { name: 'the empty string, which is zero code points', password: '', "
                         "codePoints: 0 },")

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
    #
    # TASK 46 GAVE THIS ROW A SECOND JOB. Until now it died only in suites about what a VISITOR IS TOLD;
    # `passwordLengthCountsCodePoints` is the first file that asserts the unit as a property of the
    # function, so from this task on a kill here that does NOT name that file means task 46's suite is
    # asleep. Task 46 wrote a duplicate of this row, found it was identical, and deleted it (see TR49).
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
    # RE-AIMED FOR T44, NOT RELABELLED (phase 7 F2). Task 43 predicted `own` and was right for itself: the
    # guard that catches this lives in `homeRouteRendersLandingScreenAndManagesFocus`, which task 43 grew.
    # Task 44 grew two files and that is not one of them, so the SAME kill is `inherited` here. The row's
    # text is unchanged; only the prediction moved, and this comment is why.
    ("TR21 the screen focuses the first field on mount",
     SCREEN, (REACT_IMPORT, ADVISORY_STATE),
     ("import { useEffect, useState, type ReactElement } from 'react';",
      ADVISORY_STATE + "\n\n  useEffect(() => {\n"
      "    document.querySelector<HTMLInputElement>('input')?.focus();\n  }, []);"), "inherited"),

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

    # PHASE 8's I2 AND PHASE 7's F4, IN ONE ROW. TR32 proved the two pins can see a cap on `PasswordField`;
    # four of AC4's six assertions had nothing aimed at them - `EmailField`'s `maxLength`, and `minLength`
    # on either field. This row crosses both axes at once: the OTHER file and the OTHER attribute. It is
    # also the realistic mistake, which is why it is this row rather than a repeat of TR32 on the sibling -
    # somebody expressing R1's LOWER bound as an attribute, on the field that does not even have a bound.
    # Two instruments must see it: the widened primitive scan, and the rendered pin that reads `minlength`
    # off both live fields.
    ("TR35 EmailField floors its own length at 12 UTF-16 units, which is R1's bound on the wrong field",
     EMAIL, EMAIL_TYPE, '        type="email"\n        minLength={12}', "own"),

    # ======================================================================================================
    # TASK 45 - the advisory guidance. Two of these seven are invisible on screen.
    # ======================================================================================================

    ("TR36 the guidance never renders, so the field is marked with nothing to read",
     SCREEN, GUIDANCE_ELEMENT, "        {null}", "own"),

    # In range too. The field still points at nothing when the password is fine, so a case that looks for
    # the sentence THROUGH `aria-describedby` cannot see this - which is why AC3 also asserts the sentence
    # is absent from the page text.
    ("TR37 the guidance renders for every password, including the ones that are fine",
     SCREEN, GUIDANCE_ELEMENT,
     "        <Text id={GUIDANCE_ID}>{guidance}</Text>", "own"),

    # Up before the first submit. `!= 'in-range'` and not `!= 'unchecked'`: the state has three values,
    # only one of them renders today, so the second form would be the original in different words.
    ("TR38 the guidance is up from the first render, before anything has been submitted",
     SCREEN, GUIDANCE_ELEMENT,
     "        {advisory !== 'in-range' ? <Text id={GUIDANCE_ID}>{guidance}</Text> : null}", "own"),

    # The sentence stays on the page and nothing names it. This is the whole of AC4's claim, and it is also
    # what L11 means by an association: a message a sighted visitor reads and a screen-reader user never
    # meets.
    ("TR39 the field stops pointing at the guidance, which is then visual only",
     SCREEN, FIELD_DESCRIBEDBY, "", "own"),

    # BYTE-IDENTICAL ON SCREEN. The DOM says exactly what it said before, so every rendering assertion in
    # the suite passes; only a scan of the SOURCE can tell a lookup from a literal (task 43's TR09 taught
    # this once already).
    ("TR40 the sentence is spelled in the screen instead of looked up",
     SCREEN, GUIDANCE_LOOKUP, "  const guidance = 'Use a password of 12 to 128 characters.';", "own"),

    # THE ROW THIS TASK WAS BUILT AROUND, and also byte-identical: the catalogue names the bounds as
    # literals instead of interpolating them. Nothing on screen moves. What makes the two different is
    # PROVENANCE - these numbers no longer come from `passwordLength.ts` - so the only assertion that can
    # kill it is one that says the rendered numbers ARE the constants.
    ("TR41 the catalogue types the bounds in, so they can drift from the check that enforces them",
     CATALOGUE, GUIDANCE_STRING,
     "    passwordGuidance: 'Use a password of 12 to 128 characters.',", "own"),

    # The copy becomes a verdict. The equality cases do NOT die - they build the expected sentence from the
    # catalogue, so they move with it - which is exactly why the suite also pins the words themselves.
    ("TR42 the guidance delivers a verdict instead of naming the rule",
     CATALOGUE, GUIDANCE_STRING,
     "    passwordGuidance: 'Your password is too short.',", "own"),

    # TR25's and TR34's shape at this task's table: a row claiming a password INSIDE the range is outside
    # it. If the guidance assertions are awake, the sentence will be absent and the row will fail.
    ("TR43 CANARY a 12-code-point row claims to be out of range",
     REJECT_SUITE, OUT_OF_RANGE_LAST_ROW,
     OUT_OF_RANGE_LAST_ROW + "\n  { name: 'CANARY', password: 'a'.repeat(12), codePoints: 12 },", "own"),

    # --- phase 9: the three rows the reviews asked for ------------------------------------------------
    # THE HOLE PHASE 7 MEASURED. The screen interpolates the literals instead of the constants: the
    # sentence is byte-identical, the catalogue's template is untouched, and the digit identity passes
    # because 12 and 128 ARE the constants. Before phase 9 this survived all 126 cases in the three files
    # this task owns. The only instrument that can see it is a scan of the SCREEN's source, which is why
    # TR40's lesson had to be learnt twice: provenance is invisible to the DOM at BOTH ends of the wire.
    ("TR44 the screen interpolates literal bounds, so the catalogue's template is fed from nowhere",
     SCREEN, GUIDANCE_LOOKUP,
     "  const guidance = t('register.passwordGuidance', {\n    min: 12,\n    max: 128,\n  });", "own"),

    # THE BOUNDS SWAPPED IN THE TEMPLATE: "Use a password of 128 to 12 characters." Nothing in the source
    # moves - the catalogue still interpolates, the screen still names the constants - and the sentence
    # still carries both numbers. What sees it is the digit identity, which asserts the ORDER of the
    # rendered numbers against the constants, and this row is the mutant it never had (phase 8's I3).
    ("TR45 the template names the bounds in the wrong order, so the sentence states an empty range",
     CATALOGUE, GUIDANCE_STRING,
     "    passwordGuidance: 'Use a password of {{max}} to {{min}} characters.',", "own"),

    # THE SENTENCE AFTER THE BUTTON. The association still holds, every equality still passes, and the
    # screen's own comment - "between the field and the control so the reading order matches the visual
    # one" - becomes false. It was false-able for a whole phase: this row and the assertion it answers
    # both arrived in phase 9, because the claim was stated in production prose and held by nothing.
    ("TR46 the guidance is rendered after the submit control, so it informs a decision already made",
     SCREEN, GUIDANCE_THEN_SUBMIT,
     "        <SubmitButton>{t('register.submit')}</SubmitButton>\n" + GUIDANCE_ELEMENT, "own"),

    # --- phase 10: the two rows the new cases answer -------------------------------------------------
    # TR33'S MIRROR, AT THE END WHERE THE BUG WITHHOLDS THE SENTENCE. TR33 changes the upper comparison and
    # is caught through a false REJECT; this changes the lower one, where 11 astral code points are 22
    # units, 22 is inside the range, and the screen says NOTHING to a visitor R1 says must be told. Task
    # 43's 7-emoji row catches this comparison through the MARK, in a file this task did not write, so
    # before phase 10 the sentence was uncovered at this end: silence is the hardest failure to notice.
    ("TR47 the lower comparison counts UTF-16 units, so an astral password short of the bound is told nothing",
     SCREEN, LOWER_COMPARISON, "password.length >= ADVISORY_MINIMUM_CODE_POINTS", "own"),

    # THE STATE MACHINE'S OTHER EDGE. `advisory` is set in `submit()` and nowhere else; this resets it on
    # every keystroke, which is the shape a well-meaning "clear the error as they fix it" change would
    # take. Nothing measured it before phase 10 - task 43 pinned that nothing is marked BEFORE a submit and
    # stopped there - so the sentence could have been made to vanish on a keystroke with 18 of 19 cases
    # still green.
    ("TR48 the advisory resets on every keystroke, so the sentence vanishes as soon as the visitor types",
     SCREEN, PASSWORD_ONCHANGE,
     "          onChange={(next) => { setPassword(next); setAdvisory('unchecked'); }}", "own"),

    # --- task 46: the counting unit itself -----------------------------------------------------------
    # TR49 WAS WRITTEN AND THEN DELETED, and the reason is worth more than the row would have been. It
    # mutated `[...value].length` to `value.length` - and that is TR05, byte for byte, written by task 43
    # three tasks ago. The mutant this task is named for already existed. What task 46 changes is not that
    # it exists but WHERE IT DIES: before this task TR05 died only in screen suites, each of them a claim
    # about what a visitor is told, and now it must also die in the module's own suite. That is checked by
    # reading TR05's attribution, not by adding a second copy of it - a duplicate row would have inflated
    # the kill count and measured nothing new.

    # EQUIVALENT, AND IT MUST SURVIVE. `Array.from` iterates code points exactly as the spread does, so a
    # suite that killed this would be pinning the SYNTAX of today's implementation rather than the UNIT R1
    # names - and would obstruct the next person who touches the file for a good reason. A surviving row
    # is usually a hole; this one is the proof that the suite is not over-fitted. Scored apart.
    ("TR50 the spread becomes Array.from, which counts identically",
     LENGTH, COUNT_BODY, "  return Array.from(value).length;", "unfalsifiable"),

    # The crudest break there is. Any case that survives this is asserting nothing at all.
    ("TR51 the count is always zero",
     LENGTH, COUNT_BODY, "  return 0;", "own"),

    # NORMALISATION, WHICH IS OQ-T46-2 MADE FALSIFIABLE. `'e\u0301'.normalize()` is one code point, not
    # two, so this row dies on exactly one table row - the decomposed accent. Without that row the
    # question would be recorded in a comment and held by nothing.
    ("TR52 the count normalises before counting, so a decomposed accent silently becomes one",
     LENGTH, COUNT_BODY, "  return [...value.normalize()].length;", "own"),

    # TOTALITY, which the module's header claims in prose: an advisory check that can throw is a
    # client-side gate by another name, which is the one thing R1 forbids the client to be.
    ("TR53 the function throws on the empty string instead of answering zero",
     LENGTH, COUNT_BODY,
     "  if (value === '') throw new RangeError('empty');\n  return [...value].length;", "own"),

    # A fourth export nobody uses. The export-shape pin is the only thing that can see it, and a module
    # that quietly grows a surface is how a helper becomes a utility nobody owns.
    ("TR54 a fourth name is exported that no consumer asked for",
     LENGTH, COUNT_BODY,
     "  return [...value].length;\n}\n\nexport function countUtf16Units(value: string): number {\n"
     "  return value.length;", "own"),

    # PREDICTED own AND inherited. Three screen suites already pin the minimum; this task pins it in the
    # module's own suite, which is a different claim - that the MODULE publishes R1's number, not that a
    # screen reads it. If attribution ever names only the screen suites, this task's pin is asleep.
    ("TR55 the advisory minimum becomes 11, so R1's lower bound is off by one",
     LENGTH, MINIMUM_CONST, "export const ADVISORY_MINIMUM_CODE_POINTS = 11;", "own"),

    # THE CANARY. A table row claiming an astral character's code points equal its UTF-16 units - the
    # exact confusion this whole task exists to forbid. If the table's assertions are awake, both numbers
    # in that row are wrong and the row fails twice. Scored apart.
    ("TR56 CANARY a table row claims an astral string's code points equal its UTF-16 units",
     COUNT_SUITE, ASTRAL_ROW,
     ASTRAL_ROW + "\n  { name: 'CANARY', input: '\\u{1F44D}', codePoints: 2, utf16Units: 2 },", "own"),
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
