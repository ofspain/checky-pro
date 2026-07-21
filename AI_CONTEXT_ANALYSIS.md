# AI Context Analysis — Contract Audit Before Auth Service Implementation

Scope: full read of `spec/*/{package,requirements,design,tasks,agents}.md`, the `.ai/` prompt-generation
framework (`generate.py`, `WORKFLOW.md`, `README.md`), the generated `.ai/prompts/auth/T01–T40` prompt
set, `contracts/`, and the existing `services/auth` code, to resolve what every contract reference in
the Auth Service task prompts means before any implementation begins.

No implementation was started. This is analysis only.

---

## 1. What the "Task statement" line is

Every phase file (`.ai/prompts/<service>/T##/0N-*.md`) opens with:

```
**Task statement (verbatim from `spec/auth-service/tasks.md`, task N):**
> ...
```

This is a **verbatim quote of one line from `spec/auth-service/tasks.md` §7**, injected by
`.ai/generate.py::header()`. It is not implementation work, and it is not something to analyze in
the abstract — it **is** the entire scope of the task. Mechanically:

- `tasks.md` is the ordered execution plan (objective 7 of `package.md`). Each numbered item is one
  task; the whole package expects tasks executed **in order**, each leaving the module buildable and
  green (`tasks.md` line 3).
- The generator (`generate_task_prompts()` in `generate.py`) copies that single line into all 14 phase
  files for that task so every phase — from Phase 0 repository understanding through Phase 13 PR
  prep — repeats the same anchor. This is deliberate: `WORKFLOW.md` "Common mistakes" explicitly warns
  against "widening scope" and fixing neighboring code.
- **It is the actual, sole unit of implementation work for the task's slot in the pipeline** — not a
  work item under analysis, and not a summary. Phase 1–2 exist to extract *only* what this one line
  requires from `requirements.md` / `design.md` / `package.md`; nothing else in the spec package is in
  scope for that task's 14-phase run.
- How it should influence the current phase: it is the literal boundary. `agents.md` (auth) and
  `WORKFLOW.md` both instruct: obey it, don't restate the rest of the spec, don't implement anything the
  line doesn't call for, and stop at the human-approval gates (Phases 4, 9) before proceeding further.

For T01 specifically, the task statement is *only*: add `V5__lockout_cleanup_and_shedlock.sql` and run
the migration. Nothing about lockout logic, MFA, API keys, or contracts is in scope for T01's
implementation — those are later tasks' statements.

## 2. What the "Contracts" line in each header actually is

This is the crux of the audit, and it is **not** what it first appears to be.

Every task header (T01 through T40, all four services) carries a `Contracts:` line. In auth, it is
**identically** `contracts/api/auth.yaml`, `contracts/api/token-claims.md`,
`contracts/events/auth/email-requested.v1.schema.json`,
`contracts/events/auth/security-audit.v1.schema.json` — on T01 (a DB migration task) exactly as on T33
(the task that authors those files) and T40 (a spec status bump).

Tracing `generate.py` confirms why: `discover()` calls `parse_contracts(design_md)` **once per service**
(`.ai/generate.py:227-230,246,264`) — a regex sweep of the *entire* `design.md` for any
`contracts/...` path — and stores the result once as `svc["contracts"]`. `header()` then renders that
same service-level list into *every* task's header via `fmt_list(svc["contracts"], ...)`
(`generate.py:298,319`). Unlike `rids`/`lids` (requirement/LOCKED-decision IDs), which genuinely are
scoped per task through `expand_ids`, **there is no per-task scoping logic for contracts at all**. This
is a known simplification of the generator, not a claim from the specification that T01 depends on or
produces four contract files.

Confirming evidence: `spec/auth-service/artifacts/T11/TIB-T11.md`, a hand-authored reference brief for
Task 11 (Lockout State Machine) produced with correct scoping, **omits the Contracts section entirely**
— because none of the four contracts are relevant to lockout logic. That is the standard the generated
per-task "Contracts:" line should be held to, and mostly isn't.

Answering the audit's five sub-questions for the four listed contracts, **as they relate to the
package as a whole, not to any one task**:

| Contract | What it represents | Input or output? | Owner | Consumer(s) | Exists now? |
|---|---|---|---|---|---|
| `contracts/api/auth.yaml` | OpenAPI spec for every non-SAS auth REST endpoint | **Output** of `tasks.md` #33 | auth-service | Generated Java models + `libs/ts/api-client` (per `contracts/README.md`); any frontend/service calling auth's REST API | No |
| `contracts/api/token-claims.md` | Documents the exact access-token claim set (L9) | **Output** of `tasks.md` #34 | auth-service | Every resource server validating JWTs: payment, crypto, notification services; frontend | No |
| `contracts/events/auth/email-requested.v1.schema.json` | Kafka payload schema for `auth.email.requested` | **Output** of `tasks.md` #33 | auth-service | notification-service (R1/R2, its task #15 "Consumed-contract tests") | No |
| `contracts/events/auth/security-audit.v1.schema.json` | Kafka payload schema for `auth.security.audit` mirror | **Output** of `tasks.md` #33 | auth-service | No consumer specified in any of the four service specs yet (compliance/SIEM sink, out of current scope) | No |

None of these four are inputs to T01 (or to most other auth tasks). They are the **output artifacts of
tasks 33 and 34**, which sit near the end of the 40-task sequence, after the account, lockout, MFA,
API-key, and session work they document is built.

## 3. Ownership matrix — every contract referenced anywhere in the project

Built from a full-project grep (`spec/**/{design,tasks,requirements,package}.md`), not assumption.

| Contract path | Owning service | Authoring task | Consumer(s) | Status |
|---|---|---|---|---|
| `contracts/events/auth/user-lifecycle.v1.schema.json` | auth | *(pre-existing — authored before this Phase-1 spec, backing the already-built account/lifecycle module)* | notification-service (R6, welcome email on `user.registered`) | **Exists** — has a contract test (`UserLifecycleEventPayloadContractTest`) |
| `contracts/api/auth.yaml` | auth | tasks.md #33 | TS/Java generated clients, frontend, any REST caller | Missing (expected) |
| `contracts/api/token-claims.md` | auth | tasks.md #34 | payment, crypto, notification (JWT validation); frontend | Missing (expected) |
| `contracts/events/auth/email-requested.v1.schema.json` | auth | tasks.md #33 | notification-service (task #15) | Missing (expected) |
| `contracts/events/auth/security-audit.v1.schema.json` | auth | tasks.md #33 | none specified yet | Missing (expected) |
| `contracts/api/crypto-internal.yaml` | crypto | crypto tasks.md #23 | internal callers (payment-service watch/attest calls) | Missing (expected) |
| `contracts/events/chain/tx-seen.v1.schema.json` | crypto | crypto tasks.md #23 | payment-service | Missing (expected) |
| `contracts/events/chain/tx-confirmed.v1.schema.json` | crypto | crypto tasks.md #23 | payment-service | Missing (expected) |
| `contracts/events/chain/tx-finalized.v1.schema.json` | crypto | crypto tasks.md #23 | payment-service | Missing (expected) |
| `contracts/events/chain/tx-reorged.v1.schema.json` | crypto | crypto tasks.md #23 | payment-service | Missing (expected) |
| `contracts/events/chain/provider-degraded.v1.schema.json` | crypto | crypto tasks.md #23 | payment-service (ops signal) | Missing (expected) |
| `contracts/api/payments.yaml` | payment | payment tasks.md #24 | frontend, generated clients | Missing (expected) |
| `contracts/events/payments/invoice-created.v1.schema.json` | payment | payment tasks.md #24 | notification-service (R3) | Missing (expected) |
| `contracts/events/payments/payment-seen.v1.schema.json` | payment | payment tasks.md #24 | notification-service (R4) | Missing (expected) |
| `contracts/events/payments/payment-finalized.v1.schema.json` | payment | payment tasks.md #24 | notification-service | Missing (expected) |
| `contracts/events/payments/receipt-issued.v1.schema.json` | payment | payment tasks.md #24 | notification-service (R5) | Missing (expected) |
| `contracts/events/notifications/*` | notification (conditional) | only if O5 is taken (notification `design.md` §"Package & file map") | undetermined — no consumer spec'd | N/A — not yet decided |

`notification-service` is **consume-only at launch** (its own `package.md` §"Contract artifacts" says so
explicitly) — it authors no published-event contract, only deserializes against auth's and payment's.
Its task #15 ("Consumed-contract tests") is therefore the one genuine **cross-package** dependency in
this audit: it cannot pass until auth task #33 and payment task #24 have produced their event schemas.
That is a real sequencing dependency worth tracking at the project level, but it does not change what
auth's T01 needs today.

## 4. Missing-artifact analysis

All eight auth/crypto/payment contract files above are missing from `contracts/` (only
`contracts/api/.gitkeep` and the one pre-existing `contracts/events/auth/user-lifecycle.v1.schema.json`
are present). For each: **is this intentional, and does it block T01?**

**Intentional — not a gap.** Four independent lines of evidence, all pointing the same way:

1. **Explicit task ownership.** `tasks.md` #33 says, verbatim, "Author `contracts/api/auth.yaml`... and
   `contracts/events/auth/email-requested.v1.schema.json` / `security-audit.v1.schema.json`." Task #34
   says "Write `contracts/api/token-claims.md`." These are *future* tasks in the same sequence T01
   belongs to — the spec itself names the task responsible, and it isn't T01.
2. **Requirement phrasing.** R47/R48 use EARS `WHERE ... is authored, THEN ...` — a conditional on a
   not-yet-true precondition, not a mandate that the file already exists.
3. **Established precedent in this exact repo.** The one contract that does exist
   (`user-lifecycle.v1.schema.json`) backs code that was *already built* (the account/lifecycle module,
   built before this Phase-1 spec). The pattern this project actually follows is: build the feature,
   then formalize its shape into `contracts/` as a dedicated, late task with a contract test — not
   contract-first design. `AccountController`, `AdminAccountController`, `AdminAuditController`,
   `AdminRoleController`, and `AdminAccountRoleController` are all already implemented in
   `services/auth` with **no** `contracts/api/auth.yaml` yet, which matches task #33 covering "all
   non-SAS endpoints" (i.e., including these) in one pass at the end, not incrementally.
4. **The reference brief for T11 omits contracts entirely** when they're irrelevant to the task,
   demonstrating that a correctly-scoped brief doesn't inherit the generator's blanket service-level
   list. If T11 (a mid-sequence task) has no reason to touch contracts, T01 (an earlier, purely
   schema-migration task) certainly doesn't.

No task before #33/#34 treats these contracts as an input to be read, conformed to, or built against.
Nothing indicates an earlier task should have produced them. There is no unresolved gap for the Auth
Service's own sequencing.

**The one real coordination note:** notification-service task #15 depends on auth #33 and payment #24.
This is a cross-service ordering fact worth keeping visible when work moves to notification, not
something to resolve inside auth's package.

## 5. Newly generated contracts

**None generated.** Generating `contracts/api/auth.yaml`, `contracts/api/token-claims.md`,
`contracts/events/auth/email-requested.v1.schema.json`, or `security-audit.v1.schema.json` now would be
unjustified and actively harmful to the spec's own design:

- It would violate the single-task scope discipline that `WORKFLOW.md`, `agents.md`, and every phase
  prompt's guardrails enforce ("Work ONLY on T01... No scope beyond this task").
- An OpenAPI spec or token-claims doc authored before the 30+ endpoints/behaviors it documents (tasks
  2–32: verification, password reset, lockout, MFA, API keys, sessions) exist would not be
  "implementation-independent" or authoritative — it would necessarily be wrong or fictional in places,
  contradicting the audit brief's own requirement that a generated contract "become the authoritative
  contract for subsequent implementation tasks." A contract that has to be rewritten once real
  endpoints exist is not authoritative; it's a draft with an authoritative label on it.
- It duplicates work the spec has already assigned, by name, to tasks #33 and #34.

## 6. Conclusion

**Implementation of T01 (Schema V5) can proceed.** The "Contracts" line on every generated auth task
prompt, including T01's, is a generator artifact — `parse_contracts()` computes one service-wide list
from `design.md` and stamps it onto all 40 tasks uniformly, with no per-task relevance filter. It is
**not** a claim that T01 (or most other early/mid-sequence tasks) depends on, reads, or must produce
`contracts/api/auth.yaml`, `contracts/api/token-claims.md`,
`contracts/events/auth/email-requested.v1.schema.json`, or `security-audit.v1.schema.json`.

Those four files are real, currently-missing artifacts, but the specification itself assigns their
authorship to tasks #33 and #34 — deliberately late, after the behavior they describe has been built —
consistent with how the one contract that already exists
(`contracts/events/auth/user-lifecycle.v1.schema.json`) came to exist. This is not a gap in the
specification; it is the specification's intended sequencing, and it should be left alone until tasks
#33/#34 are reached.

**Recommendation for the `.ai/` framework** (not a spec gap, a tooling note): `generate.py::header()`
renders `svc["contracts"]` — a whole-service list — into every task instead of filtering by task
relevance the way it already does for `rids`/`lids` via `expand_ids`/`OVERRIDES`. Phase 1 of each task
run should treat the header's `Contracts:` line as a superset to narrow, not a per-task fact, until the
generator is taught to scope it (e.g., only surface a contract on the task(s) whose task-statement text
names that contract path, as tasks #33/#34 do). This does not block T01 or any other task — Phase 1's
own instructions already say "reference the header's scoped IDs as your starting set and widen only if
the task clearly requires it" — but it explains why the header will keep looking this way until the
generator changes.

No unresolved specification gap blocks Auth Service implementation. Proceed task-by-task from T01 in
the order `tasks.md` defines.
