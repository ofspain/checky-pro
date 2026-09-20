#!/usr/bin/env python3
"""
.ai/generate.py — Themistra spec-driven AI engineering framework generator.

Discovers every specification package under spec/<service>/ and emits, for every
implementation task in that package's tasks.md, the complete 14-phase prompt workflow
plus a per-task README, a per-service index, and the top-level .ai/WORKFLOW.md and
.ai/README.md.

This is framework TOOLING, not application code. It reads the specs; it never edits them
and never writes application/business logic. Re-run it whenever a spec package changes:

    python3 .ai/generate.py            # regenerate everything under .ai/prompts/
    python3 .ai/generate.py --check    # parse only; print the discovered inventory

A generated prompt references spec documents by path (it never copies them), injects the
task-scoped requirement / locked-decision / test / contract IDs into a header, and pins
the preferred model per phase. The prompt bodies are phase-generic; the header carries the
task specifics, so every file stays token-lean and immediately usable.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SPEC = REPO / "spec"
OUT = REPO / ".ai" / "prompts"

# --- Phase table -------------------------------------------------------------------------
# (filename, phase title, preferred model, artifact this phase produces relative to T##/)
PHASES = [
    ("00-repository-understanding.md", "Repository Understanding",   "Claude Sonnet",  "artifacts/00-repository-understanding.md"),
    ("01-specification-extraction.md", "Specification Extraction",   "Claude Sonnet",  "artifacts/01-specification-extraction.md"),
    ("02-task-implementation-brief.md","Task Implementation Brief",  "Claude Sonnet",  "artifacts/02-task-implementation-brief.md"),
    ("03-design-challenge.md",         "Design Challenge",           "Kimi 2.7",       "artifacts/03-design-challenge.md"),
    ("04-freeze-task.md",              "Freeze Task Brief",          "Human Approval", "artifacts/04-frozen-task-brief.md"),
    ("05-implementation-plan.md",      "Implementation Plan",        "Claude Sonnet",  "artifacts/05-implementation-plan.md"),
    ("06-implementation.md",           "Implementation",             "Claude Sonnet",  "artifacts/06-implementation-notes.md"),
    ("07-self-review.md",              "Self Review",                "Claude Sonnet",  "artifacts/07-self-review.md"),
    ("08-independent-review.md",       "Independent Code Review",    "Kimi 2.7",       "artifacts/08-independent-review.md"),
    ("09-review-resolution.md",        "Review Resolution",          "Human Approval", "artifacts/09-review-resolution.md"),
    ("10-test-generation.md",          "Test Generation",            "Claude Sonnet",  "artifacts/10-test-generation.md"),
    ("11-test-review.md",              "Test Review",                "Kimi 2.7",       "artifacts/11-test-review.md"),
    ("12-specification-verification.md","Specification Verification","Claude Sonnet",  "artifacts/12-specification-verification.md"),
    ("13-pr-preparation.md",           "PR / Commit Preparation",    "Claude Sonnet",  "artifacts/13-pr-preparation.md"),
]

MODEL_NOTE = ("Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs "
              "architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review "
              "phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs.")

# --- Manual ID overrides -----------------------------------------------------------------
# Some spec packages do not cite requirement/locked IDs inline in tasks.md (auth), and auth's
# package.md §8 uses a parallel test-numbering that does not line up with requirements.md. For
# those, the task->ID mapping is authored here by hand: requirement IDs from requirements.md,
# LOCKED IDs from design.md §4a, and test names verbatim from package.md §8. Any key provided
# (rids / lids / tests) replaces the auto-parsed value for that task; tasks omitted here fall
# back to auto-parsing. This is the ONE place that needs a human when a package lacks inline cites.
OVERRIDES: dict[str, dict[int, dict[str, list[str]]]] = {
    "auth": {
        1:  {"rids": ["R17", "R40"], "lids": ["L1"], "tests": []},
        2:  {"rids": ["R22"], "lids": ["L6", "L13"], "tests": []},
        3:  {"rids": ["R8", "R9", "R10"], "lids": ["L2"],
             "tests": ["shouldRejectPasswordShorterThan12OrLongerThan128",
                       "shouldRejectBreachedPasswordUsingHibpRange",
                       "shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure"]},
        4:  {"rids": ["R10", "R43"], "lids": ["L2"],
             "tests": ["shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure"]},
        5:  {"rids": ["R3", "R4", "R5"], "lids": ["L5"],
             "tests": ["shouldActivateAccountWithValidVerificationToken",
                       "shouldNotRevealAccountExistenceForInvalidVerificationToken"]},
        6:  {"rids": ["R3", "R4", "R6", "R44"], "lids": ["L5"],
             "tests": ["shouldActivateAccountWithValidVerificationToken",
                       "shouldResendVerificationOnlyForPending accounts",
                       "shouldEmitVerifyEmailEventOnRegistration",
                       "shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic"]},
        7:  {"rids": ["R12", "R13", "R14", "R15"], "lids": ["L5"],
             "tests": ["shouldEmitPasswordResetEventOnlyWhenEmailExists",
                       "shouldResetPasswordAndRevokeAllFamiliesWithValidToken"]},
        8:  {"rids": ["R11"], "lids": ["L2", "L3"],
             "tests": ["shouldRejectPasswordShorterThan12OrLongerThan128"]},
        9:  {"rids": ["R8", "R9", "R10"], "lids": ["L2"],
             "tests": ["shouldRejectPasswordShorterThan12OrLongerThan128",
                       "shouldRejectBreachedPasswordUsingHibpRange"]},
        10: {"rids": ["R2", "R5", "R15"], "lids": ["L5"],
             "tests": ["shouldReturnSameAcknowledgementForDuplicateAndNewRegistration",
                       "shouldNotRevealAccountExistenceForInvalidVerificationToken"]},
        11: {"rids": ["R16", "R17", "R18", "R19"], "lids": ["L4"],
             "tests": ["shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes",
                       "shouldResetLockoutCounterOnSuccessfulLogin"]},
        12: {"rids": ["R16", "R17", "R18", "R19"], "lids": ["L4"],
             "tests": ["shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes",
                       "shouldResetLockoutCounterOnSuccessfulLogin"]},
        13: {"rids": ["R16", "R18", "R43"], "lids": ["L4"],
             "tests": ["shouldAppendRowAndMirrorAuditEventForLoginFailure",
                       "shouldResetLockoutCounterOnSuccessfulLogin"]},
        14: {"rids": ["R20"], "lids": ["L4"],
             "tests": ["shouldUnlockAccountViaAdminEndpoint"]},
        15: {"rids": ["R21"], "lids": ["L5"],
             "tests": ["shouldReturnIndistinguishableResponseForLockedAndBadCredentials"]},
        16: {"rids": ["R22"], "lids": ["L6", "L13"],
             "tests": ["shouldReturnTotpProvisioningUriOnEnrollmentBegin"]},
        17: {"rids": ["R22", "R23"], "lids": ["L6"],
             "tests": ["shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes"]},
        18: {"rids": ["R22", "R23", "R28", "R29"], "lids": ["L6"],
             "tests": ["shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes",
                       "shouldRequirePasswordAndTotpToDisableMfa"]},
        19: {"rids": ["R22", "R23", "R28"], "lids": ["L6", "L11"],
             "tests": ["shouldReturnTotpProvisioningUriOnEnrollmentBegin",
                       "shouldRequirePasswordAndTotpToDisableMfa"]},
        20: {"rids": ["R24", "R25", "R26", "R27"], "lids": ["L10"],
             "tests": ["shouldRequireMfaEnrollmentForMerchantAdminBeforeAuthorization",
                       "shouldRequireValidTotpOrRecoveryCodeWhenMfaIsEnrolled",
                       "shouldIssueTokenWithOtpAmrAndAcrAfterMfa"]},
        21: {"rids": ["R26", "R27", "R31"], "lids": ["L9"],
             "tests": ["shouldIssueTokenWithOtpAmrAndAcrAfterMfa",
                       "shouldIssueTokenWithPwdAmrWhenMfaNotRequired"]},
        22: {"rids": ["R24", "R25", "R26"], "lids": ["L10"],
             "tests": ["shouldRequireMfaEnrollmentForMerchantAdminBeforeAuthorization",
                       "shouldRequireValidTotpOrRecoveryCodeWhenMfaIsEnrolled",
                       "shouldIssueTokenWithOtpAmrAndAcrAfterMfa"]},
        23: {"rids": ["R30"], "lids": ["L7"], "tests": []},
        24: {"rids": ["R30", "R32", "R33"], "lids": ["L7"],
             "tests": ["shouldCreateApiKeyAndShowPlaintextExactlyOnce",
                       "shouldRejectRevokedOrUnknownApiKeyWithUniform401"]},
        25: {"rids": ["R31", "R32", "R33"], "lids": ["L8", "L11"],
             "tests": ["shouldExchangeValidApiKeyForMerchantJwt",
                       "shouldRejectRevokedOrUnknownApiKeyWithUniform401"]},
        26: {"rids": ["R30", "R34", "R35"], "lids": ["L7"],
             "tests": ["shouldCreateApiKeyAndShowPlaintextExactlyOnce",
                       "shouldListAndRevokeOwnApiKeys"]},
        27: {"rids": ["R30", "R31", "R33", "R35"], "lids": ["L7", "L8"],
             "tests": ["shouldCreateApiKeyAndShowPlaintextExactlyOnce",
                       "shouldExchangeValidApiKeyForMerchantJwt",
                       "shouldRejectRevokedOrUnknownApiKeyWithUniform401",
                       "shouldListAndRevokeOwnApiKeys"]},
        28: {"rids": ["R36", "R37", "R38"], "lids": [],
             "tests": ["shouldListActiveSessions", "shouldRevokeSingleSessionFamily",
                       "shouldRevokeAllSessionFamilies"]},
        29: {"rids": ["R39"], "lids": [], "tests": []},
        30: {"rids": ["R40"], "lids": ["L1"],
             "tests": ["shouldCleanupExpiredTokensAndFamilies"]},
        31: {"rids": ["R41", "R42"], "lids": [],
             "tests": ["shouldReturn429WhenPerAccountRateLimitExceeded"]},
        32: {"rids": [], "lids": ["L11", "L12"], "tests": ["shouldEnforcePublicEndpointAllowlist"]},
        33: {"rids": ["R44", "R45", "R47"], "lids": [],
             "tests": ["shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic",
                       "shouldConformToAuthOpenApiContract"]},
        34: {"rids": ["R48"], "lids": ["L9"], "tests": []},
        35: {"rids": [], "lids": ["L12"], "tests": ["shouldPreventCrossModuleEntityImports"]},
        36: {"rids": ["R1", "R4", "R24", "R30", "R31", "R36"], "lids": [],
             "tests": ["shouldConformToAuthOpenApiContract"]},
        37: {"rids": ["R43"], "lids": [], "tests": []},   # run full suite — broad audit/coverage
        38: {"rids": [], "lids": ["L11", "L12", "L13"], "tests": []},  # defect-catalogue review
        39: {"rids": [], "lids": [], "tests": []},        # process: update decision log
        40: {"rids": [], "lids": [], "tests": []},        # process: bump spec status
    },
}


# --- Parsing -----------------------------------------------------------------------------
def expand_ids(text: str, letter: str) -> list[str]:
    """Collect R#/L# ids from free text, expanding simple ranges like R1-R3 / R1–R3."""
    ids: set[int] = set()
    pat = re.compile(rf"{letter}(\d+)(?:\s*[–\-]\s*{letter}?(\d+))?")
    for m in pat.finditer(text):
        lo = int(m.group(1))
        hi = int(m.group(2)) if m.group(2) else lo
        if hi >= lo and hi - lo < 60:
            ids.update(range(lo, hi + 1))
        else:
            ids.add(lo)
    return [f"{letter}{n}" for n in sorted(ids)]


def parse_tasks(tasks_md: str) -> list[dict]:
    """Return ordered tasks: {num, title, section, text, rids, lids}."""
    tasks: list[dict] = []
    section = ""
    for line in tasks_md.splitlines():
        h = re.match(r"^##+\s+(.*)", line)
        if h:
            section = h.group(1).strip()
            continue
        m = re.match(r"^(\d+)\.\s+(.*)$", line)
        if not m:
            continue
        num = int(m.group(1))
        rest = m.group(2).strip()
        bold = re.search(r"\*\*(.+?)\*\*", rest)
        title = bold.group(1).rstrip(".").strip() if bold else " ".join(rest.split()[:6])
        tasks.append({
            "num": num,
            "title": title,
            "section": section,
            "text": rest,
            "rids": expand_ids(rest, "R"),
            "lids": expand_ids(rest, "L"),
        })
    return tasks


def parse_test_map(package_md: str) -> dict[str, list[str]]:
    """From package.md §8, map each named test -> [requirement/locked ids it verifies]."""
    testmap: dict[str, list[str]] = {}
    in_sec = False
    for line in package_md.splitlines():
        if re.match(r"^##+\s*8\.", line):
            in_sec = True
            continue
        if in_sec and re.match(r"^##+\s", line):
            break
        if not in_sec or "→" not in line:
            continue
        name = re.search(r"`([^`]+)`", line)
        if not name:
            continue
        after = line.split("→", 1)[1]
        ids = expand_ids(after, "R") + expand_ids(after, "L")
        testmap[name.group(1).strip()] = ids
    return testmap


def parse_contracts(design_md: str) -> list[str]:
    found = sorted(set(re.findall(r"contracts/[A-Za-z0-9_./*\-]+", design_md)))
    # trim trailing punctuation/backticks that regex may have grabbed
    return [c.rstrip(".`,)") for c in found]


def discover() -> list[dict]:
    services = []
    for d in sorted(SPEC.iterdir()):
        if not d.is_dir():
            continue
        needed = ["tasks.md", "requirements.md", "design.md", "package.md"]
        if not all((d / f).exists() for f in needed):
            continue
        tasks_md = (d / "tasks.md").read_text()
        package_md = (d / "package.md").read_text()
        design_md = (d / "design.md").read_text()
        tasks = parse_tasks(tasks_md)
        testmap = parse_test_map(package_md)
        contracts = parse_contracts(design_md)
        short = d.name[:-8] if d.name.endswith("-service") else d.name
        ov = OVERRIDES.get(short, {})
        for t in tasks:
            scope = set(t["rids"]) | set(t["lids"])
            t["tests"] = [name for name, ids in testmap.items() if scope & set(ids)]
            # Apply hand-authored overrides for packages that lack inline ID cites.
            o = ov.get(t["num"])
            t["_explicit"] = set()
            if o:
                for key in ("rids", "lids", "tests"):
                    if key in o:
                        t[key] = o[key]
                        t["_explicit"].add(key)
        services.append({
            "dir": d.name,
            "short": short,
            "has_agents": (d / "agents.md").exists(),
            "contracts": contracts,
            "tasks": tasks,
        })
    return services


# --- Rendering ---------------------------------------------------------------------------
def fmt_list(items: list[str], empty: str) -> str:
    return ", ".join(f"`{i}`" for i in items) if items else empty


def fmt_scoped(task: dict, key: str, empty_derive: str, empty_none: str) -> str:
    """Format a scoped-ID line. An explicitly-authored empty set reads 'none apply';
    an un-parsed empty set reads 'derive in Phase 1'."""
    items = task[key]
    if items:
        return ", ".join(f"`{i}`" for i in items)
    return empty_none if key in task.get("_explicit", set()) else empty_derive


def header(svc: dict, task: dict, i: int) -> str:
    fn, name, model, art = PHASES[i]
    prev = PHASES[i - 1][3] if i > 0 else "—"
    tid = f"{task['num']:02d}"
    d = svc["dir"]
    rids = fmt_scoped(task, "rids",
                      "none cited inline — derive them in Phase 1 from `requirements.md`",
                      "none — this task carries no direct requirement ID (process/verification step)")
    lids = fmt_scoped(task, "lids",
                      "none cited inline — derive them in Phase 1 from `design.md` §4a",
                      "none — no LOCKED decision constrains this task")
    tests = fmt_scoped(task, "tests",
                       "none pre-mapped — derive from the acceptance criteria + `package.md` §8",
                       "none — no named §8 test maps to this task")
    contracts = fmt_list(svc["contracts"], "see `design.md` §4c / §6")
    agents = f"`spec/{d}/agents.md` is authoritative — never restate or violate it." if svc["has_agents"] \
        else "no `agents.md` for this service; use `ARCHITECTURE.md` + `docs/`."
    prevref = f"`{prev}`" if i > 0 else "— (entry phase)"
    return (
        f"<!-- MODEL: {model} — Phase {i} ({name}). {MODEL_NOTE} -->\n\n"
        f"# {svc['short']} · T{tid} · Phase {i} — {name}\n\n"
        f"| | |\n|---|---|\n"
        f"| **Service** | `{d}` |\n"
        f"| **Task** | T{tid} — {task['title']} |\n"
        f"| **Spec section** | {task['section'] or '—'} |\n"
        f"| **Model** | {model} |\n"
        f"| **Consumes** | {prevref} |\n"
        f"| **Produces** | `{art}` (write it here; exactly one artifact) |\n\n"
        f"**Task statement (verbatim from `spec/{d}/tasks.md`, task {task['num']}):**\n"
        f"> {task['text']}\n\n"
        f"**Spec package:** `spec/{d}/` → `package.md` · `requirements.md` · `design.md` · `tasks.md`"
        f"{' · `agents.md`' if svc['has_agents'] else ''}\n\n"
        f"- **Scoped requirement IDs:** {rids}\n"
        f"- **Scoped LOCKED decisions:** {lids}\n"
        f"- **Named tests (`package.md` §8):** {tests}\n"
        f"- **Contracts:** {contracts}\n"
        f"- **Standing rules:** {agents}\n\n"
        f"---\n\n"
    )


def guard(svc: dict, task: dict, i: int) -> str:
    art = PHASES[i][3]
    tid = f"{task['num']:02d}"
    human = PHASES[i][2] == "Human Approval"
    stop = ("This is a **Human Approval gate** — a person makes the decision. The model only "
            "assembles the material for review; it does not advance the pipeline itself."
            if human else "Do this phase's work, write the one artifact, then STOP and wait.")
    return (
        f"\n---\n\n## Guardrails (apply to every phase)\n"
        f"- Work ONLY on **T{tid}**. Ignore every other task in the package.\n"
        f"- No unrelated refactoring. No speculative improvements. No scope beyond this task.\n"
        f"- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under "
        f"Open Questions — never deviate silently.\n"
        f"- Never modify the specification files under `spec/`.\n"
        f"- Reference documents; do not paste whole specs into the artifact.\n"
        f"- Produce exactly one artifact: `{art}`. {stop}\n"
    )


# Phase-generic bodies. The header already injects all task-scoped IDs, tests, and contracts.
def body(i: int) -> str:
    B = {
        0: (
            "You are joining an existing engineering team. **Do NOT write code.** Read the "
            "repository and this service's spec package (only the four/five files listed above), "
            "then ground yourself in what already exists that this task will touch.\n\n"
            "Produce, in the artifact:\n"
            "1. **Architecture summary** of this service (modules, persistence, events/outbox, security).\n"
            "2. **Existing code this task touches** — packages/classes/tables that already exist vs. are new.\n"
            "3. **Established patterns** to follow — persistence (JPA/Flyway), outbox/idempotency, "
            "resource-server security, error handling, configuration.\n"
            "4. **Testing conventions** (unit vs. Testcontainers, fixed `Clock`, ArchUnit).\n"
            "5. **Known gaps / unknowns.** If something required by the task is missing, write "
            "\"I do not know\" — do not speculate.\n\n"
            "Do not design and do not extract requirements yet — that is Phase 1."
        ),
        1: (
            "Consume the Phase 0 artifact. We are implementing ONLY this task. From `requirements.md`, "
            "`design.md`, `package.md`, and `tasks.md`, extract everything needed to implement it — "
            "and nothing about any other task.\n\n"
            "Return, in the artifact, these sections only:\n"
            "- **Business Rules** — each applicable requirement, by ID, one sentence.\n"
            "- **Locked Decisions** — every LOCKED decision that constrains this task, by ID.\n"
            "- **Files involved** — existing files to read/extend, and new files the spec expects.\n"
            "- **Dependencies** — classes, services, repositories, entities, config keys, contracts.\n"
            "- **Acceptance Criteria** — mapped to requirement IDs.\n"
            "- **Tests required** — the named tests from `package.md` §8 plus boundary tests implied.\n"
            "- **Open Questions** — only genuine blockers (cite `package.md` §11 where relevant).\n\n"
            "Do not design. Do not implement. Reference the header's scoped IDs as your starting set and "
            "widen only if the task clearly requires it."
        ),
        2: (
            "Consume the Phase 1 extraction. You are preparing work for a senior engineer. Do NOT design, "
            "write code, or suggest improvements. Convert the extraction into a concise **Task "
            "Implementation Brief (TIB)** — this becomes the ONLY specification the implementation and "
            "review phases use.\n\n"
            "Use EXACTLY these sections, nothing else:\n"
            "`Task` · `Purpose` · `Scope` (In / Out) · `Business Rules` (by requirement ID, one line each) · "
            "`Locked Decisions` (by ID) · `Dependencies` · `Inputs` · `Outputs` · `State Changes` (or None) · "
            "`Files to Create` · `Files to Modify` · `Files NOT to Modify` · `Acceptance Criteria` (by ID) · "
            "`Required Tests` · `Constraints` (performance, security, thread-safety, transaction, module "
            "boundaries, null handling) · `Open Questions` (blockers only; else \"No blockers\").\n\n"
            "Keep it under three pages. Do not invent requirements. Do not restate unrelated spec parts."
        ),
        3: (
            "Consume the Phase 2 TIB. You are an adversarial reviewer. Do NOT redesign and do NOT implement. "
            "Challenge the brief before it is frozen:\n"
            "- Hidden or unstated assumptions.\n"
            "- Ambiguous or untestable business rules.\n"
            "- Missing edge cases and failure modes.\n"
            "- Any conflict with a LOCKED decision or `agents.md`.\n"
            "- Unstated dependencies, ordering hazards, or contract mismatches.\n\n"
            "For each finding, return: **Issue · Severity · Evidence · Recommended brief amendment.** "
            "Output findings only — the human folds accepted ones into the brief in Phase 4."
        ),
        4: (
            "**Human Approval gate.** Consume the Phase 2 TIB and the Phase 3 challenge. A human (not the "
            "model) decides. The model's job is only to assemble the decision packet:\n"
            "- Fold each ACCEPTED Phase 3 amendment into the brief; list each REJECTED one with a reason.\n"
            "- Confirm every Open Question is resolved or explicitly deferred with an owner.\n"
            "- Confirm scope, Files-to-Create/Modify/NOT-Modify, and acceptance criteria are final.\n\n"
            "Write the **frozen brief** to the artifact and mark it `STATUS: FROZEN`. Downstream phases may "
            "not renegotiate it. If the human does not approve, stop — do not advance."
        ),
        5: (
            "Consume the frozen brief (Phase 4). Plan the implementation — **do NOT write code.**\n\n"
            "Return: Files to create · Files to modify · Public methods (signatures) · Private methods · "
            "Entities used · Repositories used · Services used · Unit/integration tests required · "
            "**Execution order** (front-load schema/migration, then dao, service, api, tests).\n\n"
            "Every planned file must trace to the frozen brief's Files sections. Do not add files the brief "
            "does not authorize."
        ),
        6: (
            "Consume the frozen brief (Phase 4) and the plan (Phase 5). Implement ONLY this task's scope, "
            "following the plan and `agents.md` conventions exactly.\n\n"
            "Rules: production-ready code only — no TODO, no placeholder methods, no pseudocode. Touch only "
            "the files the plan authorizes. Money as `BigDecimal`/`NUMERIC`; outbox for publishes; idempotent "
            "consumers; validated `@ConfigurationProperties`; no secrets in code. Do NOT write tests here "
            "(that is Phase 10) unless the task itself is test-only.\n\n"
            "Then write the artifact as **implementation notes**: what changed, how each change maps to the "
            "plan and to the acceptance criteria, and any deviation forced by reality (flag it, don't hide it)."
        ),
        7: (
            "Consume the implementation (Phase 6). Self-review the diff against the frozen brief and "
            "`agents.md`. Do NOT rewrite. Evaluate: correctness, boundary conditions, null-safety, "
            "thread-safety, transaction boundaries, module boundaries, idempotency, money types, "
            "enumeration-safety/secret-handling, readability, complexity.\n\n"
            "Return, per finding: **Issue · Severity · Evidence (file:line) · Recommendation.** "
            "Findings only — fixes are applied in Phase 9."
        ),
        8: (
            "Consume the implementation (Phase 6) and the self-review (Phase 7) — with fresh, adversarial "
            "eyes. You are reviewing a pull request; the implementation is complete. Do NOT rewrite.\n\n"
            "Hunt for: logic bugs, missing edge cases, time/ordering bugs, race conditions, incorrect state "
            "transitions, wrong assumptions, performance issues, security issues, and any LOCKED-decision or "
            "spec deviation.\n\n"
            "Return, per finding: **Issue · Evidence · Recommendation · Confidence.** Findings only."
        ),
        9: (
            "**Human Approval gate.** Consume the self-review (Phase 7) and independent review (Phase 8). A "
            "human decides which comments are ACCEPTED. Then apply ONLY the accepted comments.\n\n"
            "Rules: do not refactor, do not optimize, do not change public APIs, do not rename classes. Write "
            "the artifact as a **resolution log**: each comment → accepted/rejected (+ reason) → the exact "
            "change made. If nothing is accepted, say so. Do not advance without human sign-off."
        ),
        10: (
            "Consume the frozen brief (Phase 4) and the resolved implementation (Phase 9). Generate ONLY "
            "tests. Cover every acceptance criterion, every boundary, and every state transition; implement "
            "each named test listed in the header. Follow `agents.md` testing conventions — unit tests use "
            "plain JUnit with a fixed `Clock`; integration tests use Testcontainers (Postgres + Kafka); "
            "contract tests validate against the referenced contracts.\n\n"
            "Do NOT change production code. Write the artifact as a **test manifest** mapping each test to the "
            "acceptance criterion / requirement ID it verifies."
        ),
        11: (
            "Consume the tests (Phase 10). Do the tests actually verify the specification? Do NOT rewrite. "
            "Look for: missing cases, weak/absent assertions, false positives, flakiness, duplicate tests, and "
            "coverage gaps against the acceptance criteria and named tests in the header.\n\n"
            "Return recommendations only — each as **Gap · Why it matters · Suggested test.**"
        ),
        12: (
            "Consume all prior artifacts. Compare the final implementation and tests against `requirements.md`, "
            "`design.md`, and `tasks.md` for THIS task. Produce a **traceability matrix** with columns: "
            "`Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation?`.\n\n"
            "Then, as the approving principal engineer, answer: (1) Is the task fully complete? (2) Does it "
            "satisfy every acceptance criterion? (3) Does it violate any LOCKED decision? (4) Remaining risks? "
            "End with a single verdict line: **PASS** or **FAIL**, with a one-line reason."
        ),
        13: (
            "Consume the verification (Phase 12) — proceed only if it is **PASS**. Prepare the task for merge. "
            "Produce, in the artifact: **Commit title**, **Commit message** (imperative; end with the "
            "`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` trailer), **Files changed**, "
            "**Summary**, **Testing performed**, and **Specification references** (task number + the requirement "
            "and LOCKED-decision IDs from the header). No code. Branch off `main`; `main` stays deployable."
        ),
    }
    return B[i]


def render_prompt(svc: dict, task: dict, i: int) -> str:
    return header(svc, task, i) + body(i) + guard(svc, task, i)


def render_task_readme(svc: dict, task: dict) -> str:
    tid = f"{task['num']:02d}"
    d = svc["dir"]
    rows = []
    for i, (fn, name, model, art) in enumerate(PHASES):
        prev = PHASES[i - 1][3].split("/")[-1] if i > 0 else "—"
        when = "human sign-off" if model == "Human Approval" else "artifact written"
        rows.append(f"| {i} | [`{fn}`]({fn}) | {name} | {model} | `{prev}` | `{art}` | after {when} |")
    table = "\n".join(rows)
    return (
        f"# {svc['short']} · T{tid} — {task['title']}\n\n"
        f"Complete 14-phase prompt workflow for **Task {task['num']}** of the `{d}` spec package.\n"
        f"Run the phases in order; each consumes the previous phase's artifact and writes exactly one of its own.\n\n"
        f"**Task (verbatim, `spec/{d}/tasks.md` task {task['num']}):**\n> {task['text']}\n\n"
        f"**Spec section:** {task['section'] or '—'}\n\n"
        f"**Scoped IDs** — requirements: {fmt_scoped(task, 'rids', 'derive in Phase 1', 'none (process step)')} · "
        f"locked: {fmt_scoped(task, 'lids', 'derive in Phase 1', 'none apply')} · "
        f"tests: {fmt_scoped(task, 'tests', 'derive from §8', 'none map')}\n\n"
        f"## Phases\n\n"
        f"| # | Prompt | Phase | Responsible agent | Input | Output artifact | When to stop |\n"
        f"|---|---|---|---|---|---|---|\n{table}\n\n"
        f"- **Purpose:** turn task {task['num']} of `{d}` into merged, spec-verified code with a full audit trail.\n"
        f"- **Inputs:** the `spec/{d}/` package (+ `agents.md`) and, per phase, the prior artifact under `artifacts/`.\n"
        f"- **Outputs:** the 14 artifacts under `artifacts/`, plus the implementation + tests in `services/{svc['short']}/`.\n"
        f"- **Expected final artifact:** `artifacts/13-pr-preparation.md` (a merge-ready commit package), gated on a "
        f"Phase 12 **PASS**.\n"
        f"- **When to stop:** each phase stops after its single artifact; the human gates (Phases 4 and 9) stop until "
        f"sign-off. Do not skip an earlier artifact.\n\n"
        f"See [`../../../WORKFLOW.md`](../../../WORKFLOW.md) for the full pipeline and [`../README.md`](../README.md) "
        f"for this service's task index.\n"
    )


def render_service_readme(svc: dict) -> str:
    d = svc["dir"]
    rows = [f"| [T{t['num']:02d}](T{t['num']:02d}/) | {t['title']} | {t['section'] or '—'} |" for t in svc["tasks"]]
    table = "\n".join(rows)
    return (
        f"# AI prompt workflows — `{d}`\n\n"
        f"One task folder per implementation task in [`spec/{d}/tasks.md`]"
        f"(../../../spec/{d}/tasks.md). Each folder holds the 14-phase prompt set and a README.\n\n"
        f"**Spec package:** [`spec/{d}/`](../../../spec/{d}/) — "
        f"`package.md` · `requirements.md` · `design.md` · `tasks.md`"
        f"{' · `agents.md`' if svc['has_agents'] else ''}\n\n"
        f"**Tasks ({len(svc['tasks'])}):**\n\n"
        f"| Task | Title | Section |\n|---|---|---|\n{table}\n\n"
        f"To execute a task, open its folder and run `00-…` through `13-…` in order with the model named in each "
        f"file's header. See [`../../WORKFLOW.md`](../../WORKFLOW.md).\n"
    )


def render_workflow(services: list[dict]) -> str:
    total = sum(len(s["tasks"]) for s in services)
    inv = "\n".join(f"| `{s['dir']}` | `{s['short']}` | {len(s['tasks'])} | `.ai/prompts/{s['short']}/` |"
                    for s in services)
    prow = "\n".join(f"| {i} | {name} | {model} | `{art}` |" for i, (fn, name, model, art) in enumerate(PHASES))
    return f"""# Themistra AI Engineering Workflow

Specification-driven development: every implementation task flows through a fixed 14-phase
pipeline, each phase producing exactly one artifact that the next phase consumes. No phase
skips an earlier artifact. The specs in `spec/<service>/` are the source of truth; this
framework turns each `tasks.md` task into merged, spec-verified code with a full audit trail.

## Inventory (generated)

| Spec package | Short name | Tasks | Prompts |
|---|---|---|---|
{inv}

**Total: {total} tasks × 14 phase prompts.** Regenerate with `python3 .ai/generate.py`.

## The pipeline

| # | Phase | Responsible agent | Produces |
|---|---|---|---|
{prow}

Phases 4 and 9 are **Human Approval** gates — a person decides; the model only assembles the
review packet. {MODEL_NOTE}

## Artifact chain

Each task folder (`.ai/prompts/<service>/T##/`) carries the 14 prompts plus an `artifacts/`
directory. Phase _n_ reads `artifacts/(n-1)-…` and writes `artifacts/n-…`. The implementation
(Phase 6) and tests (Phase 10) additionally land in `services/<service>/`. The terminal
artifact is `artifacts/13-pr-preparation.md`, gated on a Phase 12 **PASS**.

## Directory layout

```
.ai/
  generate.py          # this framework's generator (re-runnable)
  WORKFLOW.md          # this file
  README.md            # how to use the system
  prompts/
    <service>/
      README.md        # task index for the service
      T01/ … T##/
        00-repository-understanding.md  … 13-pr-preparation.md
        README.md      # task purpose, phase table, when-to-stop
        artifacts/     # the 14 produced artifacts land here
```

## How an engineer executes a task

1. Open `.ai/prompts/<service>/T##/` and read its `README.md`.
2. Run `00 → 13` in order. Use the model named in each prompt's `<!-- MODEL: … -->` header.
3. Do not advance past a phase until its one artifact exists. Stop at the human gates (4, 9)
   until you have sign-off.
4. Phase 12 must return **PASS** before Phase 13 prepares the commit.
5. Branch off `main`; keep `main` deployable.

## Common mistakes

- **Widening scope.** A prompt is scoped to one task. Do not fix neighbouring code or refactor.
- **Skipping the freeze (Phase 4).** Implementing against an unfrozen brief invites churn.
- **Silently deviating from a LOCKED decision or `agents.md`.** Stop and log it instead.
- **Writing tests in Phase 6 or code in Phase 10.** Keep the phases separate.
- **Copying whole specs into artifacts.** Reference by path; keep artifacts lean.
- **Running review phases (3, 8, 11) on the default model.** Use Kimi 2.7 for adversarial eyes.

## Cost optimization

- Sonnet handles construction phases; reserve Opus/Fable for genuine architecture only.
- Kimi 2.7 for the three review phases keeps a second, independent perspective cheap.
- Human gates cost no tokens — use them to kill bad direction early.
- Prompts reference documents instead of pasting them, so context stays small per phase.
- Regenerate prompts (don't hand-edit) when a spec changes, so scoped IDs stay accurate.
"""


def render_root_readme(services: list[dict]) -> str:
    total = sum(len(s["tasks"]) for s in services)
    links = "\n".join(f"- [`{s['short']}`](prompts/{s['short']}/) — {len(s['tasks'])} tasks "
                      f"(spec: `spec/{s['dir']}/`)" for s in services)
    return f"""# `.ai/` — Themistra spec-driven engineering framework

This directory is the AI-native engineering workspace for the repository. It converts each
specification package under `spec/<service>/` into a per-task, 14-phase prompt workflow so any
engineer or agent can implement a task independently, consistently, and with a full audit trail.

It contains **no application code** and never edits the specs — only the framework.

## What's here

- **[`WORKFLOW.md`](WORKFLOW.md)** — the 14-phase pipeline, artifact chain, model responsibilities,
  directory layout, and cost guidance. Read this first.
- **[`generate.py`](generate.py)** — the generator. Re-run it whenever a spec package changes:
  `python3 .ai/generate.py` (or `--check` to print the discovered inventory without writing).
- **`prompts/<service>/T##/`** — the prompt set for one task: `00-…` through `13-…`, a `README.md`,
  and an `artifacts/` output directory.

## Services ({total} tasks total)

{links}

## How to use it

**New engineer or agent:**
1. Pick a task. Open `.ai/prompts/<service>/T##/README.md`.
2. Run the phases `00 → 13` in order, each with the model named in its header comment.
3. Each phase consumes the previous artifact and writes exactly one new one under `artifacts/`.
4. Stop at the Human Approval gates (Phases 4 and 9) until a person signs off.
5. Ship only after Phase 12 returns **PASS**; Phase 13 produces the commit package.

**Ground rules** (see each service's `spec/<service>/agents.md`): one task per workflow, obey LOCKED
decisions, no unrelated refactoring, reference specs rather than copying them, `main` stays deployable.

Prompts assume Claude Code has repository access. When a spec changes, **regenerate** rather than
hand-editing individual prompts — that keeps the scoped requirement/test IDs in every header accurate.
"""


# --- Main --------------------------------------------------------------------------------
def main() -> None:
    check = "--check" in sys.argv
    services = discover()
    if not services:
        print("No spec packages found under spec/*/ (need tasks.md + requirements.md + design.md + package.md).")
        sys.exit(1)

    total_tasks = sum(len(s["tasks"]) for s in services)
    print(f"Discovered {len(services)} service(s), {total_tasks} task(s):")
    for s in services:
        print(f"  {s['dir']:24s} -> {s['short']:14s} {len(s['tasks'])} tasks, "
              f"{len(s['contracts'])} contract refs")
    if check:
        return

    files = 0
    for s in services:
        svc_root = OUT / s["short"]
        svc_root.mkdir(parents=True, exist_ok=True)
        (svc_root / "README.md").write_text(render_service_readme(s))
        files += 1
        for t in s["tasks"]:
            tdir = svc_root / f"T{t['num']:02d}"
            (tdir / "artifacts").mkdir(parents=True, exist_ok=True)
            (tdir / "artifacts" / ".gitkeep").write_text("")
            for i, (fn, _n, _m, _a) in enumerate(PHASES):
                (tdir / fn).write_text(render_prompt(s, t, i))
                files += 1
            (tdir / "README.md").write_text(render_task_readme(s, t))
            files += 1

    (REPO / ".ai" / "WORKFLOW.md").write_text(render_workflow(services))
    (REPO / ".ai" / "README.md").write_text(render_root_readme(services))
    files += 2
    print(f"Wrote {files} files under .ai/")


if __name__ == "__main__":
    main()
