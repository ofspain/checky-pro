# frontend · T08 · Phase 7 — Self-Review

Reviews the phase 6 change against the frozen brief and `spec/frontend/agents.md`. Findings only; fixes are applied
in phase 9.

## Findings

### 1. A T07 helper is now shared with T08
- **Issue:** `installTransport` gained a `body` field, so a T07-owned helper is no longer T07-only. The frozen brief
  allows the file to change and forbids editing T07's cases (none was), but a future T07 change now has a T08
  consumer.
- **Severity:** Low.
- **Evidence:** the `Sent` type and `installTransport` in the test file; T07's 29 cases re-run green before and after.
- **Recommendation:** none beyond recording it. Splitting the helper per task would duplicate it inside one file,
  which is worse than one shared recorder. Decide at phase 9.

### 2. `'<uncloneable>'` is a sentinel, so AC4 cannot distinguish "unreadable" from "empty"
- **Issue:** when a case makes `clone()` throw, the transport records the string `'<uncloneable>'`. A POST with a
  genuinely empty body records `''`, so the two are distinguishable today, but the sentinel is test-only fiction
  sitting in the same field as real bodies.
- **Severity:** Info.
- **Evidence:** the `try/catch` in `installTransport`; the AC4 case asserts `body === '<uncloneable>'`.
- **Recommendation:** none. The call count is what AC4 is about, and the sentinel keeps the record total.

### 3. The AC7 guard fails on a POST *removal* as well as an addition
- **Issue:** the assertion is set equality, so deleting a POST from `auth.yaml` also fails it, with a message about
  the test's own list rather than about the contract.
- **Severity:** Info — a removed operation is a contract change that should fail loudly anyway (T03's drift check
  covers the generated client).
- **Recommendation:** none.

### 4. The contract parse is line-based
- **Issue:** the guard matches `^ {4}post:` under `^ {2}(/…):`, so a POST declared at another indentation, inside a
  `$ref`'d file, or via a path-item `$ref` would be missed.
- **Severity:** Low. It is T05's own parser (`:513-527`), `auth.yaml` is generated flat, and `generatedClientCompiles`
  plus the CI drift step cover structural change.
- **Recommendation:** none for T08; worth knowing if `contracts/` ever splits into multiple files.

### 5. Ten POST operations are accounted for but not driven
- **Issue:** `UNEXERCISED_POST_PATHS` holds ten operations that no case calls. A defect specific to one of them —
  say `/api-keys/token` — would not be caught here.
- **Severity:** Info. This is owner decision 2, and each of the ten repeats a shape that is driven; the boundary and
  the notice are operation-agnostic (`rateLimitNotice.ts:41` reads only `response.status`).
- **Recommendation:** none.

### 6. AC6's timer assertion runs unmounted
- **Issue:** as in T07's case 11, the `setTimeout`/`setInterval` spies run with no component mounted, to keep React's
  scheduler out of the result. So no case asserts the *mounted* path creates no timer.
- **Severity:** Low, and inherited from T07 rather than introduced here.
- **Evidence:** the AC6 case; T07 phase 10 recorded the same caveat.
- **Recommendation:** the behavioural half already covers it — the hour of faked time runs with the component
  mounted in T07's AC4 case and clears nothing. Carry forward rather than fix.

### 7. Most T08 cases do not assert `failures` is empty
- **Issue:** `createApi()`'s `renew()` rejects by design, so if a case ever did trigger a renewal, the rejection
  would land in `failures` via `onRenewalFailed` — and only three T08 cases assert `failures` is empty.
- **Severity:** Low. The renewal-capable case asserts `renew` was never called, which is the stronger statement, but
  the cheap assertion is missing from the table-driven ones.
- **Recommendation:** add `expect(failures).toEqual([])` to the two `it.each` tables at phase 9. One line each.

## Checklist (no finding)
- **R23, this task:** one transport call per rate-limited POST call, bodies recorded and compared, nine operations
  end to end plus a drift guard over all 19;
- **AC3's boundary** is asserted as exactly two sends with identical bodies, and the rationale is in the file;
- **no replay path exists:** the notice middleware has only `onResponse` (asserted), and the boundary returns early
  for any status but 401 (asserted by the renewal-capable case and by M15);
- **no resumption:** no timer created, an hour of faked time changes nothing, `dismiss()` sends nothing;
- **privacy:** a marker body reaches the transport and nothing else;
- **L12:** T05 POST parity asserted; T07's 29 cases unedited and green; no production file touched;
- **Module boundaries:** the whole change is one test file under `src/api`; no spec, contract, lint config or
  dependency change (`contracts/api/auth.yaml` byte-identical after the M18 mutation run);
- **Mutations:** 18/18, including four written for this task.
- **Not applicable:** transactions, money, enumeration safety (the copy is identical for every caller), i18n (T08
  adds no string).
