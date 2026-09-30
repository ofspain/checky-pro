# notification · T10 · Phase 0 — Repository Understanding

## 1. Architecture summary

`notification-service` as of T09: schema/config/security (T02/T03), idempotency ledger (T04),
recipient-contact projection (T05), a real `AuthEventConsumer` (T06), a read-only
`PreferenceResolver` (T08), and a real `TemplateRenderer` (T09) that computes rendered
subject/body — including, by design, the recipient's real verification/reset token embedded in a
computed link. T10 adds `SecretSafeLogging`, `design.md` §6's own named artifact for this task, with
no further detail on its own shape given anywhere in the spec.

## 2. Existing code this task touches

**Already exists — the discipline this task is meant to formalize, not originate:**
- `EmailRequestedEvent.toString()` (T06) — excludes the raw `token` field.
- `NoOpNotificationDispatcher`'s own log statement (T06) — logs `eventData.keySet()` only, never
  `eventData`'s own values.
- `RenderedMessage.toString()` (T09, Kimi Phase 8 Finding #2) — excludes `subject`/`body` content
  entirely (both can carry the real token embedded in a computed `verificationLink`/`resetLink`),
  logs only `version` and each field's own length.
- **This means the core token-safety property this task names (L4/R15) is already enforced,
  per-class, since T06** — not a gap T10 discovers, but a pattern T10 is the first task explicitly
  named to formalize/consolidate and comprehensively test.

**Not found anywhere in this repo — a real, disclosable gap in the spec's own cross-reference:**
- `package.md` §9's own checklist item cites "mirrors auth `target-design.md` §13" — **no file named
  `target-design.md` exists anywhere in this repository** (verified via a repo-wide search). Auth's
  own real precedent for this exact concern is not a centralized utility class at all — it's the
  same scattered, per-DTO `toString()` override discipline already mirrored here
  (`PasswordResetConfirmRequest.toString()` excludes `newPassword`; similar overrides exist on
  `EmailRequestedEventPayload`/`VerificationTokenResult`). "Mirrors auth" therefore means "mirrors
  auth's own established *practice*," not a literal shared utility auth itself has — auth has no
  `SecretSafeLogging`-equivalent class either.

## 3. Established patterns to follow

**Per-class `toString()` overrides** — the only pattern this codebase (and auth-service) actually
uses today. A brand-new, standalone `SecretSafeLogging` class would be genuinely novel structure,
not a continuation of an existing pattern — its own real value-add over what's already there needs
its own justification (see Open Questions).

## 4. Testing conventions

Whatever this task's own tests turn out to be, the natural shape (given no event/DB dependency) is
plain JUnit, no Spring context, no Docker — matching `PreferenceResolverTest`/`TemplateRendererTest`'s
own precedent for pure-logic modules.

## 5. Known gaps / unknowns

**Primary open question — not a blocker, resolvable within this task's own design judgment:**
what does `SecretSafeLogging` actually *do* that isn't already covered by the per-class `toString()`
discipline already in place since T06? Two real possibilities, not decided here:
1. A reusable `redact(String text)` utility masking any secret-shaped substring (a `token=...` query
   parameter, a JWT-shaped triple-dot-delimited string) in arbitrary free-form text — useful for
   future code (e.g. an exception message, a debug log of a whole rendered body) that can't rely on
   a dedicated DTO's own `toString()` override, since it isn't logging a DTO at all.
2. A comprehensive, cross-cutting **test** proving the already-established per-class disciplines
   hold together end-to-end — e.g., render every seeded template with a real fake token, capture
   all log output produced during that render (via a Logback `ListAppender` at the root logger, not
   one class's own logger), and assert the raw token never appears anywhere in the captured output.
   This is the first test in this module proving the property holistically, not one class at a
   time.

Recommend building both: (1) is genuinely novel, reusable infrastructure for content this task's
own two named risks (secrets, full API keys) can't be handled by a fixed DTO's own `toString()`
alone; (2) is the "assert" half of the task's own literal text ("assert no token/secret/full-key
leaks into bodies or logs").

**Secondary finding — a real inconsistency in the spec's own two statements of this rule, not a
blocker:** `package.md` §9's own checklist phrasing ("No secret, token, password-reset value, or
full API key ever appears in a rendered message body or a log line") is imprecise compared to L4's
own more careful text ("...reset-token values **beyond the single intended one-time link**"). Taken
literally, the checklist's own wording would forbid `TemplateRenderer`'s own T09 design entirely
(`verificationLink`/`resetLink` legitimately embed the real token — that's the whole point of a
one-time link). L4 is the authoritative LOCKED decision; the checklist is a shorthand paraphrase
that drops L4's own carve-out, not a stricter, independently-binding second rule. Phase 1/2 should
state this explicitly rather than silently picking one reading.

**Tertiary**: "full API key" has no concrete referent anywhere in this codebase today — no API-key
config exists (`themistra.notification.email.transport=ses`, T03, implies AWS IAM-based auth, not a
literal key). This part of L4/R15 is forward-looking, not yet triggered by any real code path.
