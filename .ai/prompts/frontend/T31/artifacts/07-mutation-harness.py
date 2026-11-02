#!/usr/bin/env python3
"""T31's mutation harness: account status from GET /accounts/me.

    python3 -I .ai/prompts/frontend/T31/artifacts/07-mutation-harness.py [FILTER]

IT REVERTS WITH `git checkout -- src/`. COMMIT YOUR TESTS FIRST. Uncommitted test
edits are destroyed silently - that cost 19 cases in T14 and 5 in T15.

WHAT THE FAMILIES PROBE

  MQ01-MQ04  the status is read off a TOKEN instead of the endpoint   (AC5 - the security clause)
  MQ05-MQ07  the reader builds its own request, or goes public        (AC1, AC3, L6)
  MQ08-MQ10  the 401 stops being the boundary's                      (AC4, R21)
  MQ11-MQ15  a body that is not an account is accepted anyway        (AC7, AC8)
  MQ16-MQ17  the status is not reported as sent                      (AC6)
  MQ18-MQ19  the account survives the read, or is logged             (AC10)
  MQ20-MQ21  a failure carries detail                                (AC9)
  MQ22-MQ23  no-session stops meaning no request                     (AC13)
  MQ24       the rate-limit notice sees the wrong response           (AC11, R23)
  MQ25       the runtime status list drifts from the contract        (AC2)

MQ01-MQ04 ARE THIS TASK'S REASON TO EXIST, and they are sharper here than T30's
equivalents. Reading `status` off the access token is not merely against R20(c) - it is
MEASURABLY STALE. Suspending an account does not invalidate its token: the token was
minted while the account was ACTIVE and goes on asserting that until it expires, while
`/accounts/me` answers 200 with SUSPENDED. So a token-borne status is a wrong answer that
looks current, and no runtime test can see the difference. A source scan or nothing.

MQ11-MQ15 ARE THE ONES THE CONTRACT DIFFERENCE BUYS. `AccountResponse` requires ALL FIVE
fields where `UserInfoResponse` requires only `sub`, so absence is an error here and was
the normal answer in T30. MQ13 accepts any string as a status, which is the mutant for the
hazard that decided G2: with a closed union a consumer's exhaustive `switch` is
compile-time complete and runtime incomplete, so a sixth value walks through while the
type system reassures the author.

HOW TO READ THE OUTPUT. The `own` column asks whether T31's OWN cases caught the mutant,
run with `-t T31:` over the shared file, because that file also holds T30's 53 cases and a
kill by one of those is not T31's evidence. A mutant that makes a run unparseable is
scored BROKEN, not KILLED: a mutant has to COMPILE to be evidence of anything.

THE BASELINE IS ASSERTED, AND SO ARE THE ANCHORS. Both guards exist because both failures
happened. T29's baseline drifted - one run read 3 failures instead of 1, and because every
verdict is `failed > base_failed`, the two mutants producing exactly 3 failures were
printed as SURVIVORS. And T30's `GET_CALL` anchor went stale when a phase 8 fix rewrote
the call it pointed at, so MP06 and MP07 printed BROKEN - which is NOT SURVIVED, so the
run still ended `survivors: none` and a 26-killed run was published as 28 of 28. This
harness refuses to start on either fault.

EXPECTED BASELINE on this host: the whole suite runs 1501 passed / 1 failed - the one is
the pre-existing `src/api/lintRejectsHandWrittenBackendFetch.test.ts`, an oxlint
summary-line quirk of this machine, unrelated to T31 and failing before and after it.
T31's own 50 cases run 50 / 50, exit 0.

MQ06 SURVIVED the first run, and it was a REAL escape rather than an inert mutant - the
distinction T28's MB27 and T30's MP09/MP26 each turned on. It assembles the path as
`` `/accounts/${'me'}` as '/accounts/me' ``, which yields the identical runtime string, so
no runtime case could ever tell the two apart. The frozen brief's AC1 asked for the path to
be "a quoted literal argument and never assembled" in those words and simply had no case
behind it. Held now by a source scan, because that is the only instrument that can see it.
"""

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5] / "frontend"

READER = "src/profile/readAccount.ts"
# Kimi A2: the status list and its guard moved here, so T29's allowlist can license a FILE instead of a
# regex over a construct inside the reader. The licence is now reachable by a production mutant (MQ31).
STATUS = "src/profile/accountStatus.ts"
CLIENT = "src/api/client.ts"
NONCE = "src/auth/idTokenNonce.ts"

SHARED_TEST = "src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx"
FILES = []  # empty = the whole suite, so inherited kills anywhere are visible

EXPECTED_BASELINE_FAILURES = 1

# Kimi I6. The count alone absorbs a swap: if the environmental failure were fixed on the same day an
# unrelated test broke, the floor would still be 1 and every verdict would be measured against the wrong
# one. So the failing FILES are pinned too, and the count stays to catch a second failure inside this file.
KNOWN_ENVIRONMENTAL_FAILURES = {
    # This host's oxlint@1.87.0 prints no summary line, so the assertion that parses it cannot pass here.
    "src/api/lintRejectsHandWrittenBackendFetch.test.ts",
}

# --- anchors, copied from the real source ----------------------------------------------------------------
NO_SESSION = """  const token = tokens.current();
  if (token === null || token.trim() === '') throw new ReadAccountError('no-session');"""
EMPTY_ARM = " || token.trim() === ''"
TRIM = "token.trim() === ''"
BLANK_STRING = "  return typeof value === 'string' && value.trim() !== '';"
GET_CALL = "    const answer = await api.GET('/accounts/me');"
UNAUTHORIZED = (
    "  if (response.status === 401 || response.status === 403)"
    " throw new ReadAccountError('unauthorized');"
)
UNAVAILABLE = "  if (!response.ok || !isUsableAccount(data)) throw new ReadAccountError('unavailable');"
RETURN = "  return data;"
NULL_GUARD = "  if (typeof body !== 'object' || body === null) return false;"
BOOL_GUARD = "    && typeof account.emailVerified === 'boolean'"
STATUS_GUARD = "    && isAccountStatus(account.status)"
UUID_GUARD = "    isNonBlankString(account.accountUuid)"
DELETED_ROW = "  'DELETED',\n"
TRANSPORT_CATCH = """  } catch {
    // Discarded, not inspected: there is nothing in a transport failure a visitor should be told, and the
    // reasons are indistinguishable from the client's side anyway.
    throw new ReadAccountError('unavailable');
  }"""
PUBLIC_OPS = """export const AUTH_PUBLIC_OPERATIONS: ReadonlySet<string> = new Set([
  'POST /accounts',
  'POST /accounts/verify-email',
  'POST /accounts/resend-verification',
  'POST /accounts/password-reset-request',
  'POST /accounts/password-reset',
]);"""
AUTH_FACTORY = """  const client = createAuthClient();
  // Registered first on purpose: openapi-fetch runs onResponse in reverse registration order, so the notice runs
  // last and sees the final response, including a 429 on the session boundary's re-send after a renewal.
  if (rateLimit) client.use(rateLimit.middleware);
  client.use(boundary.middleware(AUTH_PUBLIC_OPERATIONS));"""

MUTANTS = [
    # --- MQ01-MQ04: the status is read off a TOKEN (AC5) ------------------------------------------------
    ("MQ01 SUBTLEST: status is taken off the access token, which is MEASURABLY stale", READER, RETURN,
     "  const claims = JSON.parse(atob((data.accountUuid ?? '').split('.')[1])) as { status: string };\n"
     "  return { ...data, status: claims.status as Account['status'] };"),
    ("MQ02 emailVerified is read off the access token - VERIFICATION STATE", READER, RETURN,
     "  const claims = JSON.parse(atob('x.y.z'.split('.')[1])) as { emailVerified?: boolean };\n"
     "  return { ...data, emailVerified: claims.emailVerified ?? data.emailVerified };"),
    ("MQ03 the ALLOWLISTED decoding file also reads status", NONCE,
     "    return payload as Record<string, unknown>;",
     "    void (payload as { status?: string }).status;\n"
     "    return payload as Record<string, unknown>;"),
    ("MQ04 the ALLOWLISTED decoding file reads emailVerified", NONCE,
     "    return payload as Record<string, unknown>;",
     "    void (payload as { emailVerified?: boolean }).emailVerified;\n"
     "    return payload as Record<string, unknown>;"),

    # --- MQ05-MQ07: the request (AC1, AC3, L6) ----------------------------------------------------------
    ("MQ05 the reader calls fetch directly instead of the generated client", READER, GET_CALL,
     "    const raw = await globalThis.fetch('/accounts/me');\n"
     "    const answer = { data: (await raw.json()) as Account, response: raw };"),
    ("MQ06 the path is assembled rather than named", READER, GET_CALL,
     "    const answer = await api.GET(`/accounts/${'me'}` as '/accounts/me');"),
    ("MQ07 /accounts/me is made PUBLIC, so it carries no bearer and never renews", CLIENT, PUBLIC_OPS,
     PUBLIC_OPS.replace("  'POST /accounts',", "  'POST /accounts',\n  'GET /accounts/me',")),

    # --- MQ08-MQ10: whose job the 401 is (AC4) ----------------------------------------------------------
    ("MQ08 the reader renews the 401 itself, bypassing the boundary's single flight", READER, UNAUTHORIZED,
     "  if (response.status === 401 || response.status === 403) {\n"
     "    await (tokens as { renew?: () => Promise<string> }).renew?.();\n"
     "    throw new ReadAccountError('unauthorized');\n"
     "  }"),
    ("MQ09 a 403 is reported as unavailable, losing the unauthorized answer", READER, UNAUTHORIZED,
     "  if (response.status === 401) throw new ReadAccountError('unauthorized');"),
    ("MQ10 a final 401 becomes a SUCCESS with an invented account", READER, UNAUTHORIZED,
     "  if (response.status === 401 || response.status === 403) {\n"
     "    return { accountUuid: 'unknown', email: '', emailVerified: false, status: 'ACTIVE',\n"
     "      createdAt: new Date(0).toISOString() };\n"
     "  }"),

    # --- MQ11-MQ15: a body that is not an account (AC7, AC8) --------------------------------------------
    ("MQ11 the null-body guard is removed, so a JSON null reaches a property access", READER, NULL_GUARD,
     "  if (typeof body !== 'object') return false;"),
    ("MQ12 emailVerified is checked for TRUTHINESS, so false is rejected", READER, BOOL_GUARD,
     "    && Boolean(account.emailVerified)"),
    ("MQ13 any string is accepted as a status, so a sixth value walks through", READER, STATUS_GUARD,
     "    && typeof account.status === 'string'"),
    ("MQ14 the accountUuid check is dropped, so a body missing it is an account", READER, UUID_GUARD,
     "    true"),
    ("MQ15 the whole shape check is dropped, so any 200 body is an account", READER, UNAVAILABLE,
     "  if (!response.ok) throw new ReadAccountError('unavailable');"),

    # --- MQ16-MQ17: the status is not reported as sent (AC6) --------------------------------------------
    ("MQ16 an unrecognised status is DEFAULTED to ACTIVE rather than rejected", READER, STATUS_GUARD,
     "    && (isAccountStatus(account.status) || ((account as { status: string }).status = 'ACTIVE') === 'ACTIVE')"),
    ("MQ17 the status is narrowed to an is-active flag on the way out", READER, RETURN,
     "  return { ...data, status: data.status === 'ACTIVE' ? 'ACTIVE' : 'SUSPENDED' };"),

    # --- MQ18-MQ19: the account survives the read (AC10) ------------------------------------------------
    ("MQ18 the account is cached in sessionStorage", READER, RETURN,
     "  globalThis.sessionStorage.setItem('themistra.account', JSON.stringify(data));\n"
     "  return data;"),
    ("MQ19 the account status is logged", READER, RETURN,
     "  console.info('account status', data.status, data.email);\n"
     "  return data;"),

    # --- MQ20-MQ21: a failure carries detail (AC9) ------------------------------------------------------
    ("MQ20 the WWW-Authenticate header is carried into a plain Error, which CAN hold it", READER,
     UNAUTHORIZED,
     "  if (response.status === 401 || response.status === 403) {\n"
     "    throw new Error(`Account read refused: ${response.headers.get('www-authenticate') ?? ''}`);\n"
     "  }"),
    ("MQ21 the transport failure is wrapped with a cause, which carries the original error whole", READER,
     TRANSPORT_CATCH,
     """  } catch (caught) {
    const error = new ReadAccountError('unavailable');
    (error as { cause?: unknown }).cause = caught;
    throw error;
  }"""),

    # --- MQ22-MQ23: no session, no request (AC13) -------------------------------------------------------
    ("MQ22 the no-session short-circuit is removed, so a bearer-less request is sent", READER,
     NO_SESSION, "  const token = tokens.current();\n  void token;"),
    ("MQ23 the guard is narrowed back to null only, so '' sends a request with NO bearer", READER,
     EMPTY_ARM, ""),

    # --- MQ24: R23 through this client (AC11) -----------------------------------------------------------
    ("MQ24 the rate-limit notice is registered AFTER the boundary, so it sees the wrong response", CLIENT,
     AUTH_FACTORY,
     """  const client = createAuthClient();
  client.use(boundary.middleware(AUTH_PUBLIC_OPERATIONS));
  if (rateLimit) client.use(rateLimit.middleware);"""),

    # --- MQ25: the runtime list drifts from the contract (AC2) ------------------------------------------
    # Not a compile error under vitest, which transpiles without typechecking - so the YAML comparison is
    # what has to catch it, and that is the point of having it.
    ("MQ25 the runtime status list loses DELETED, drifting from the contract enum", STATUS, DELETED_ROW, ""),

    # --- MQ26-MQ28: the three phase-9 fixes, each with a mutant of its own ------------------------------
    # Kimi I4. The NARROW cache a needle sweep cannot see: one stringified boolean, no distinctive value
    # anywhere in it. Only the store-emptiness assertion added in phase 9 can kill this.
    ("MQ26 only the emailVerified flag is cached, which no needle in the sweep is distinctive enough to find",
     READER, RETURN,
     "  globalThis.sessionStorage.setItem('ev', String(data.emailVerified));\n"
     "  return data;"),
    # Kimi I5. Back to the narrower guard: whitespace is truthy, so the boundary attaches it and a malformed
    # bearer goes out with the measured Set-Cookie and one doomed renewal.
    ("MQ27 the no-session guard loses trim(), so a whitespace token sends a malformed bearer", READER,
     TRIM, "token === ''"),
    # Kimi I8. Back to emptiness: a required field present as one space is handed on as an account.
    ("MQ28 blankness narrows back to emptiness, so a one-space email is a usable account", READER,
     BLANK_STRING, "  return typeof value === 'string' && value !== '';"),

    # --- MQ29-MQ31: the three phase-11 fixes -----------------------------------------------------------
    # Kimi A5, second attempt. The FIRST attempt reordered the status and body checks and proved INERT,
    # measured in `openapi-fetch@0.17.0/dist/index.mjs:232-261`: a non-ok response returns `{ error, response }`
    # and NEVER populates `data`, so every arm that consults `data` on a failure is already looking at
    # `undefined` whatever order it runs in. The reachable version is this one - the reader reaching for the
    # error body it is not supposed to know about. Behaviourally inert as well (`!response.ok` still refuses),
    # so like MQ06 it is killable by a source scan or not at all.
    ("MQ29 the reader adopts the server's ERROR body as data, which is detail it must not touch", READER,
     "    data = answer.data;", "    data = answer.data ?? (answer.error as typeof data);"),
    # Kimi A3. The escape MQ06 could not reach: an ASSEMBLED path in a DIFFERENT production file. The
    # single-file source case stays green through this one; only the tree-wide scan sees it.
    ("MQ30 a second caller assembles the path, in a file the single-file case never reads", CLIENT,
     "export function createOidcApi(",
     "export function accountPath(): string {\n"
     "  return `/accounts/${'me'}`;\n"
     "}\n\n"
     "export function createOidcApi("),
    # Kimi I2 + A2. A CARRIER in the licensed module - the regression the allowlist exception exists to
    # prevent, and the first mutant able to reach that instrument at all: before A2 it lived behind a regex
    # over a construct, and the harness mutates production source while the instrument is in a test file.
    # Expect this one killed INHERITED-ONLY: the instrument is T29's, and that is the point.
    ("MQ31 the licensed status module logs a state word, which is what its licence forbids", STATUS,
     "export function isAccountStatus",
     "console.warn('account suspended');\n\nexport function isAccountStatus"),
]


def run(files, name_filter=None):
    command = ["npx", "-y", "pnpm@12.14.0", "exec", "vitest", "run", *files]
    if name_filter:
        command += ["-t", name_filter]
    proc = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
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


def apply(path, old, new):
    target = ROOT / path
    text = target.read_text()
    if text.count(old) != 1:
        return False
    target.write_text(text.replace(old, new, 1))
    return True


def revert():
    subprocess.run(["git", "checkout", "--", "src/"], cwd=ROOT, check=False)
    subprocess.run(["git", "clean", "-fq", "--", "src/"], cwd=ROOT, check=False)


def preflight():
    """Every anchor must be in the source, EXACTLY ONCE, before a single verdict is printed.

    T30's lesson, carried forward as code rather than as a note. A stale anchor prints BROKEN,
    BROKEN is not SURVIVED, and the run still ends `survivors: none` - so the score comes out
    overstated by exactly the number of anchors that rotted, silently.
    """
    stale = []
    for label, path, old, _new in MUTANTS:
        count = (ROOT / path).read_text().count(old)
        if count != 1:
            stale.append(f"  {label.split()[0]:<6} {path} matches its anchor {count} times, expected 1")
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
    code, failed, total, _ = run(FILES)
    if failed is None:
        print(f"  BROKEN    -              {label}")
        return None
    if failed > base_failed:
        _, own_failed, _, _ = run([SHARED_TEST], name_filter="T31:")
        own = "own" if (own_failed or 0) > 0 else "INHERITED-ONLY"
        print(f"  KILLED    {own:<14} {label}  ({failed} failed of {total}, exit {code})")
        return None
    print(f"  SURVIVED  -              {label}  ({failed} failed of {total}, exit {code})")
    return f"{label} [SURVIVED]"


def main():
    wanted = sys.argv[1] if len(sys.argv) > 1 else ""
    print(f"ROOT = {ROOT}\n")
    if not preflight():
        return 1
    code, failed, total, files_failed = run(FILES)
    print(f"baseline whole suite: {failed} failed of {total}, exit {code}")
    code31, failed31, total31, _ = run([SHARED_TEST], name_filter="T31:")
    print(f"baseline T31 cases:   {failed31} failed of {total31}, exit {code31}\n")
    # Kimi A7. The NAMES are the real guard, so they are checked first and the count only warns. A host where
    # the known environmental test fails twice is still a host this harness can measure on - the floor is
    # MEASURED, not assumed, and `score` compares against it - but an unknown file failing is not, because
    # then the floor is made of a different test. Refusing on the count alone made the harness unrunnable in
    # exactly the environment that found this, which is the opposite of what a guard is for.
    if not files_failed <= KNOWN_ENVIRONMENTAL_FAILURES:
        unexpected = sorted(files_failed - KNOWN_ENVIRONMENTAL_FAILURES)
        fixed = sorted(KNOWN_ENVIRONMENTAL_FAILURES - files_failed)
        print(
            "REFUSING TO RUN: a file outside the known environmental set is failing.\n"
            f"  unexpectedly failing: {unexpected or 'none'}\n"
            f"  no longer failing:    {fixed or 'none'}\n"
            "A floor made of a different test measures every verdict below against the wrong\n"
            "thing. Fix the tree, or update KNOWN_ENVIRONMENTAL_FAILURES deliberately."
        )
        return 1
    if failed != EXPECTED_BASELINE_FAILURES:
        print(
            f"NOTE: the baseline is {failed} failures, not the recorded {EXPECTED_BASELINE_FAILURES}, but\n"
            "every one of them is in the known environmental set, so the measured floor is sound.\n"
        )
    if failed31 != 0:
        print(f"REFUSING TO RUN: T31's own cases fail {failed31} on a clean tree.")
        return 1
    base_failed = failed

    survivors = []
    broken = []
    for label, path, old, new in MUTANTS:
        if wanted and wanted not in label:
            continue
        if not apply(path, old, new):
            print(f"  BROKEN    anchor missed  {label}")
            broken.append(label)
            revert()
            continue
        survived = score(label, base_failed)
        if survived:
            survivors.append(survived)
        revert()

    print(f"\nsurvivors: {survivors or 'none'}")
    # A BROKEN mutant is not a survivor, and until phase 9 it did not reach the exit code either - so a run
    # whose anchors had rotted could still exit 0 and be published as a full score. That was T30's exact
    # defect, and `preflight` only guards the anchors it can see before the first verdict: `score` prints
    # BROKEN too, when a run produces no parseable count at all. Both now fail the run.
    print(f"broken:    {broken or 'none'}")
    return 1 if (survivors or broken) else 0


if __name__ == "__main__":
    sys.exit(main())
