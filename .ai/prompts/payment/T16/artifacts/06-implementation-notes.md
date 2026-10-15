# payment · T16 · Phase 6 — Implementation Notes

Consumes: `artifacts/05-implementation-plan.md`

## What changed

**New files:**
- `common/CanonicalJson.java` — `serialize(Map<String,Object>)`, a recursive `canonicalize(...)`
  helper (sorts nested `Map` keys via `TreeMap`, preserves `List` order, validates every scalar
  leaf against the allowed-type list, throwing `IllegalArgumentException` otherwise), backed by
  one private, bare `ObjectMapper` (no `JavaTimeModule`, no UUID module).
- `receipt/ReceiptPayload.java` (new package `receipt`) — 12-field record, compact-constructor
  `Objects.requireNonNull` on every field except `fromAddress`.
- `receipt/ReceiptDigest.java` — `digest(ReceiptPayload)`, `toCanonicalMap(...)` (converts
  `UUID`/`BigDecimal`/`Instant` to `String`, leaves `tokenDecimals` as a boxed `Short`), `sha256(...)`
  (mirrors `auth-service`'s own `Hashing.sha256` shape, independently implemented per `agents.md`'s
  "never depend on another service's source").

**Modified:** none — the frozen brief's own "Files to Modify" was empty.

**Deferred to Phase 10** (this task is not test-only, standard rule applies): all 14 planned
test methods from Phase 5 (`CanonicalJsonTest`, `ReceiptDigestTest`).

## Mapping to the frozen brief / acceptance criteria

- **AC1** — `canonicalize`'s `TreeMap` conversion sorts keys at every nesting level;
  `serialize`'s own single entry point applies it to the top-level map too.
- **AC2** — `toCanonicalMap` puts `amount` as `payload.amount().toPlainString()` (a `String`) and
  `tokenDecimals` as `payload.tokenDecimals()` (auto-boxed to `Short`, kept as a number).
- **AC3** — Jackson's default (non-pretty) writer produces no insignificant whitespace;
  `canonicalize` passes a `null` value straight through (not omitted, not special-cased) and
  Jackson's own default `Map`-serialization writes it as a literal JSON `null`.
- **AC4/AC5** — `ReceiptDigest.digest` is a pure function of its `ReceiptPayload` input;
  `MessageDigest.getInstance("SHA-256")` + `HexFormat.of().formatHex(...)` always produces exactly
  64 lowercase hex characters.
- **AC6** — neither `ReceiptPayload` nor `ReceiptDigest` imports `com.themistra.payment.invoice.Invoice`
  or `com.themistra.payment.payment.Payment` (confirmed by inspection — no such import appears in
  either file).
- **AC7** (Phase 3/4 Finding 1) — `canonicalize`'s own final `throw new IllegalArgumentException(...)`
  branch is reached for any value that isn't `String`/`Boolean`/`null`/`Short`/`Integer`/`Long`/
  `Byte`/`Map`/`List` — including `UUID`, `Instant`, `BigDecimal`, `Float`, `Double`, `BigInteger`,
  and any custom type, at any nesting depth (the recursive call happens before the type check on
  each element, so a disallowed type nested inside a `Map` or `List` value is caught the same way
  a top-level one is).

## Deviations forced by reality

One small implementation-level detail not explicitly pinned by the frozen brief: `canonicalize`
checks `Map`/`List` via Java's pattern-matching `instanceof` with a type-pattern binding
(`value instanceof Map<?, ?> map`), compiled against Java 21 (this codebase's own language
level, confirmed via `pom.xml`) — not a deviation from anything, just the concrete syntax choice
for an unconstrained design decision the brief correctly left to implementation.

A genuine, self-caught issue during this phase: the first draft of `CanonicalJson`'s own Javadoc
(Phase 3/4 Finding 4's ASCII-scope note) wrote the literal four-character sequence after a
backslash-u directly inside a `{@code}` tag to describe Jackson's own escaping behavior — Java's
lexer processes such escapes at the raw source-character level, before any comment/Javadoc
parsing, and `javac` rejected it as an illegal escape (the placeholder text wasn't valid hex).
Fixed by rewording the sentence to describe the behavior without writing the literal escape
sequence. Caught immediately by the first `mvn compile` of this phase, not discovered later.

## Verification performed this phase

- `mvn -o -pl services/payment -am compile`: clean (after the one self-caught Javadoc-escape fix
  above; 63 source files, up from 60 — the three new classes).
- `mvn -o -pl services/payment -am test` (full suite, Docker up): **316/316 passing**, unchanged
  from the T15 baseline — no regression, no new test yet (Phase 10's own job).
