/// <reference types="node" />
/**
 * `identityErrorsIndistinguishableAcrossAccountStates` (package.md §8) — R30, R10, L4, `agents.md:40-44`.
 *
 * R30 says the copy, redirect target and visual treatment an identity screen shows are identical across
 * unknown or missing, locked, suspended and deleted accounts. L4 says the same thing `[ALL]`, with no
 * carve-out on a public surface, and adds TIMING to the list. The failure mode is not a bug - it is a
 * sympathetic addition. "That account has been suspended - contact support." A lockout countdown. A pause
 * before the error, so a locked account feels different from an unknown one. Each looks like courtesy, and
 * each hands an anonymous prober a test for whether an email exists and what state it is in.
 *
 * WHAT WAS MEASURED IN THE BACKEND FIRST, because it decides what there is left to verify:
 *
 *     `AccountUserDetailsService.java:43-58` collapses all five states into two `UserDetails` flags -
 *     unknown and deleted become an empty `findLoginView` (its own comment: "uniform message: unknown and
 *     deleted are indistinguishable (enumeration defense)"), PENDING_VERIFICATION and SUSPENDED become
 *     `disabled`, LOCKED becomes `accountLocked`. `TotpAuthenticationProvider` then throws the IDENTICAL
 *     `BadCredentialsException("Bad credentials")` for every one of them.
 *
 * So a login failure never returns to the SPA at all. It re-renders SAS's own `/login` (L2), the visitor
 * never leaves it, and the five states are not merely indistinguishable to this bundle - they are
 * UNOBSERVABLE by it.
 *
 * WHICH IS WHY THE FIXTURES BELOW ARE DELIBERATELY MORE HOSTILE THAN REALITY (Kimi F2, Option B, frozen at
 * the Phase 4 gate). Kimi put the vacuity exactly right: if all five states look identical at the SPA
 * boundary, then comparing them proves only that the SPA is indifferent to indistinguishable inputs, which
 * is nothing. So each state arrives as its OWN callback, carrying its own vocabulary in `error`,
 * `error_description` and `error_uri` - "This account has been suspended by an administrator", and a help
 * URI that spells it again. The claim is then two things at once, and both are real:
 *
 *     1. the five arrivals produce ONE outcome struct, and
 *     2. none of the five state words, descriptions or URIs reaches ANY swept surface.
 *
 * Nothing sends these arrivals today. That is declared as T29-D1 rather than hidden, and it is the right
 * trade: an indifference proved against hostile input is stronger than one proved against uniform input.
 *
 * THE TIMING CLAUSE IS THIS FILE'S OWN GROUND, and the one claim no earlier task in the package makes. L4
 * says timing is identical; R30 says server timing is out of SPA scope; `agents.md:44` reconciles them by
 * keeping the obligation and moving its subject - "The timing requirement applies to the SPA's own rendering
 * only." A cause-keyed `setTimeout` before the notice would pass every copy, redirect and leak assertion in
 * T16-T28 and still be an oracle. AC5 is where that is closed.
 *
 * AND THE TIMING CLAIM NEEDED A SECOND ARRIVAL HELPER, measured in Phase 5 before any of this was written.
 * The file's ordinary flush is `turn()`, which awaits `setTimeout(resolve, 0)` inside `act`. Under
 * `vi.useFakeTimers()` that NEVER SETTLES, so a case that reused it under a fake clock would hang until
 * vitest's timeout rather than fail. Measured: the `error=` discard path in `CallbackScreen.tsx:86-104` is
 * synchronous - the parameter is read and `discard()` is called with nothing awaited between them - so a
 * MICROTASK-ONLY flush renders the sentence with the clock still at zero. `arriveUnderFakeClock` is that
 * helper, and the five AC5 cases are the only things that use it.
 *
 * WHY THE STATE VOCABULARY IS SPLIT IN TWO, and why the split is the most important decision in the file
 * (Kimi F7, accepted in part). Every word Kimi proposed - `banned`, `terminated`, `archived`, `expired`,
 * `revoked`, `dormant` - measures ZERO comment-stripped hits in production today. So "does it fire on
 * existing code" is not the question. The question is whether the spec COMMISSIONS the word later, and for
 * four of them it does:
 *
 *     `EXPIRED` is a specified invoice state (`design.md` O1: OPEN, PAID, UNDERPAID, OVERPAID, EXPIRED,
 *     HELD). `revoke` is a specified session action with four named tests of its own
 *     (`revokeOneSessionRemovesIt`, `revokeAllSessionsRemovesAll`, R18, R19). `invalid` is the
 *     form-validation copy tasks 43-57 must write. `blocked` and `denied` belong to the `HELD` compliance
 *     path (R56, L8).
 *
 * Forbidding those globally would block specified work while reporting an R30 breach. That is exactly the
 * error T28 rejected half of its own Kimi F3 for, and then committed anyway six terms deep until Kimi I5
 * caught it. So: the words with no commissioned future are forbidden EVERYWHERE, and the rest are forbidden
 * only on a `signIn.*` or `callback.*` key - where no spec surface wants them either way.
 *
 * WHAT THE PATTERNS DELIBERATELY DO NOT CONTAIN, each with its measured count of legitimate hits:
 * bare `unknown` (14 - the TypeScript type, in `crossTabRenewal`, `sessionBoundary`, `codeExchange`,
 * `idTokenNonce`), `missing` (2 - `idTokenNonce`'s `'claim-missing'` reason code), `pending` (9 -
 * `sessionBoundary`'s in-flight renewal and `signInNotice`'s module variable), `closed` (3 -
 * `crossTabRenewal`'s channel), bare `disabled` (1 - `aria-disabled` in `SignInScreen.tsx:61`, which L11
 * requires), `not found` (1 - `mountShell`'s developer-facing throw). A scan that fired on any of them would
 * be deleted by the next person to watch it fail, not fixed.
 *
 * THE TIMER SCAN RUNS OVER THE WHOLE TREE, NOT OVER AN "IDENTITY PATH" (Kimi F4 and F5, where F5's
 * recommendation replaced F4's). F4 asked for an explicit identity-path file list and then defined it as
 * "and any file they import that participates in failure rendering", which is the vagueness F4 was objecting
 * to. F5 found the reason a file list is the wrong shape at all: a `setTimeout` in a helper imported by
 * `SignInScreen` is invisible to a file-scoped scan. So the scan covers every production module and the three
 * real timers are an ALLOWLIST CARRYING REASONS - `RENEWAL_TIMEOUT_MS` is required by R21 and L18, and a scan
 * that forbade it would fail code the spec commissions.
 *
 * WHAT THIS FILE CANNOT DO, said plainly because a page of green invites the opposite conclusion:
 *   - It cannot read the page a refused visitor actually sees. That is SAS's server-rendered `/login` (L2,
 *     T29-D1), and the only test that could is `oidcRoundTripEndToEnd` - task 134, which has no suite.
 *   - It says nothing about the three TOKEN states. Task 59 owns `identityErrorsIdenticalAcrossTokenStates`
 *     by name, and independently there is no production path: `mountShell.tsx:20-28` never calls
 *     `createSessionBoundary` or `createRateLimitNotice`, so `onRenewalFailed` has no caller and
 *     `<RateLimitNotice>` is never mounted (T29-D2).
 *   - Its "identical visual treatment" is a PIN, not a proof. Measured: no production file contains
 *     `className`, `style=`, `styled` or `.css`. Task 173 owns making AC4 non-trivial (T29-D3).
 *   - The vocabulary is enumerative. A phrasing nobody listed passes, which is why every pattern has a
 *     control and why T27's and T28's patterns had to be diffed against each other to find that one had
 *     `multi-factor` and no `two-factor` and the other the reverse.
 *   - It performs no navigation (T23-D5); `document.cookie` is empty and there is no `indexedDB` in jsdom,
 *     so those two surfaces are declared out of reach rather than swept and reported clean.
 *
 * THE SPLIT WITH TASKS 58 AND 136, frozen at the Phase 4 gate and reversible by the owner. All three cite
 * this one named test. T29 owns the sign-in and callback axis - the only identity routes with an element
 * today. T58 extends this file to registration, verification and password reset once those screens exist.
 * T136 is the Phase 1 gate that re-runs the property across all of them. Precedent:
 * `returnTargetOutsideAllowlistFallsBackToHome` was created by T24 at 67 cases and grown by T25 to 184, in
 * one file, with each task's cases labelled.
 *
 * THE MUTATION HARNESS THAT SCORED THIS FILE is `.ai/prompts/frontend/T29/artifacts/07-mutation-harness.py`,
 * run as `python3 -I .ai/prompts/frontend/T29/artifacts/07-mutation-harness.py [filter]` from anywhere in the
 * repository. It EDITS PRODUCTION SOURCE and reverts with `git checkout -- src/` plus `git clean`, so
 * everything under `frontend/src/` must be committed before it runs.
 */
import { readdir, readFile } from 'node:fs/promises';
import { dirname, join, relative } from 'node:path';
import { act, useEffect, useState } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, describe, expect, it, type Mock, vi } from 'vitest';
import { en } from '../i18n/catalogue';
import { createI18n } from '../i18n/i18n';
import { AUTHORIZE_STORAGE_KEYS } from './authorizeRequest';

// ---------------------------------------------------------------------------------------------------------
// The five states, as the axis — and as fixtures more hostile than SAS would send
// ---------------------------------------------------------------------------------------------------------

type StateFixture = {
  /** The state's own name, used as a leak needle and as the case label. */
  state: string;
  error: string;
  description: string;
  uri: string;
};

/**
 * The five, enumerated.
 *
 * FIVE, FROM `tasks.md:95`, NOT FROM R30. Kimi F1 attributes "the five account states" to R30, and R30 does
 * not say it: `requirements.md:104-106` reads "unknown or missing, locked, suspended, and deleted" - four
 * clauses, with the first covering two states or one depending on how it is read. Task 58 spells them out as
 * five: "Unknown, missing, locked, suspended, and deleted accounts". Task 58 is the unit that will extend
 * this file, so its count is the one used here; a split count between the two would be worse than either.
 */
const ACCOUNT_STATES: readonly StateFixture[] = [
  {
    state: 'unknown',
    error: 'access_denied',
    description: 'No account exists for that email address',
    uri: 'https://id.example/help/unknown-account',
  },
  {
    state: 'missing',
    error: 'invalid_request',
    description: 'The account record is missing',
    uri: 'https://id.example/help/missing-account',
  },
  {
    state: 'locked',
    error: 'access_denied',
    description: 'This account is locked after too many attempts',
    uri: 'https://id.example/help/locked-account',
  },
  {
    state: 'suspended',
    error: 'unauthorized_client',
    description: 'This account has been suspended by an administrator',
    uri: 'https://id.example/help/suspended-account',
  },
  {
    state: 'deleted',
    error: 'access_denied',
    description: 'This account has been deleted',
    uri: 'https://id.example/help/deleted-account',
  },
];

/** What must not survive the discard, for one state: its name, the whole description, and the help URI. */
function needlesFor(fixture: StateFixture): string[] {
  return [fixture.state, fixture.description, fixture.uri];
}

// ---------------------------------------------------------------------------------------------------------
// The vocabulary, and why each pattern is drawn where it is
// ---------------------------------------------------------------------------------------------------------

/**
 * Account-state words with no commissioned future ON A SIGN-IN SURFACE.
 *
 * Every one measures zero comment-stripped hits in production today, and none appears in copy any
 * requirement, locked decision or later task asks for. Those are two different tests and a word has to pass
 * both to be here (Kimi F7).
 *
 * **CORRECTED 2026-10-31 by T31, and the correction is to the PREMISE rather than to the pattern.** This
 * comment used to claim no later task asks for any of these words. That was already untrue when it was
 * written: task 31 was in `tasks.md`, **R20(b)** commissions reading the account's status from
 * `GET /accounts/me`, and that contract's enum is literally
 * `[PENDING_VERIFICATION, ACTIVE, LOCKED, SUSPENDED, DELETED]`. So `locked`, `suspended` and `deleted` have
 * a commissioned future in production SOURCE - they just have none in sign-in COPY, which is what R30 and L4
 * actually protect.
 *
 * The pattern is unchanged and still global over copy. What changed is that the SOURCE scan now reads
 * `PERMITTED_STATE_SOURCE`, so a file may name a state for a stated reason and nothing else may. Widening the
 * pattern's exceptions would have been the wrong repair; this file's own header names that error class twice
 * (T26's localStorage scan, T28's Kimi I5), and the honest version is an allowlist with a reason and a bound.
 */
const ALWAYS_FORBIDDEN_STATE =
  'locked|lockout|suspended|deleted|deactivat|inactive|no\\s*such|banned|terminated|archived|dormant'
  // Kimi I4, the half with no commissioned future. Both measure zero comment-stripped hits and neither
  // appears in any requirement, locked decision or later task.
  + '|nonexistent|unrecogni[sz]ed';

/**
 * Words that are forbidden ON A SIGN-IN SURFACE and permitted elsewhere, each because the spec commissions it.
 *
 * `expired` - `EXPIRED` is a specified invoice state (`design.md` O1). `revoke` - session revocation, four
 * named tests (R18, R19). `invalid` - form-validation copy, tasks 43-57. `denied`, `forbidden`, `blocked`,
 * `frozen`, `rejected` - the `HELD` compliance path (R56, L8) and 403 copy.
 *
 * A global forbid on any of them would block specified work while reporting an R30 breach, which is the one
 * error class this package has made twice (T26's localStorage scan, T28's Kimi I5).
 */
const SIGN_IN_SCOPED_STATE =
  'expired|revoke|invalid|denied|forbidden|blocked|frozen|rejected'
  + '|unknown\\s+account|account\\s+(?:not|does\\s+not)|wrong\\s+account'
  // Kimi I4's other half, scoped rather than global for the same reason the first tier exists. `limited` is
  // rate-limit copy (R23); `restricted` and `flagged` belong to the `HELD` compliance path (R56, L8);
  // `compromised` and `breached` are password vocabulary that tasks 43-57 may need (R17 forbids the client
  // doing the LOOKUP, not the server saying so). Word-bounded, so `unlimited` and `unrestricted` do not
  // match - there is no word boundary inside either.
  + '|compromised|breached|flagged|restricted|limited';

/**
 * Catalogue keys the two tiers may legally contain, each with the reason. Empty today.
 *
 * Kept, and its accounting exercised by a control with synthetic rows, because an empty table leaves the
 * accounting itself untested - T28 found exactly that hole in its own first draft.
 */
const PERMITTED_STATE_COPY: ReadonlyArray<{ key: string; why: string }> = [];

/**
 * Production files permitted to NAME an account state, with the reason and the bound that makes it safe.
 *
 * Added by T31. An allowlist, not a hole: the row names the file, the exact words, and what the file is
 * forbidden from doing with them. R30's concern is a screen that shows a visitor WHICH account state they hit;
 * a module that validates a status against the contract's enum and returns it is the opposite of that, and
 * `readAccount.ts` renders nothing and translates nothing - asserted, not asserted-about.
 *
 * An upper AND lower bound, as T29's timer allowlist and T30's decode allowlist are: a control removes the row
 * and proves the scan reports the file it protects.
 */
const PERMITTED_STATE_SOURCE: ReadonlyArray<{
  file: string;
  words: readonly string[];
  why: string;
}> = [
  {
    file: 'profile/accountStatus.ts',
    words: ['locked', 'suspended', 'deleted'],
    why: 'R20(b) and task 31: the account status is READ from GET /accounts/me and the contract enum names '
      + 'these three, so a runtime list of them is the only way an out-of-contract status can be rejected. '
      + 'The licensed file holds that list and the guard over it and nothing else - no carrier can take a '
      + 'state word out of it.',
  },
];

/**
 * Every way a state word could leave a licensed file and reach a visitor.
 *
 * This is the PLACE bound, and it is the second attempt at one. Kimi I2 found the first version - `.ts` plus
 * no translator call - too weak: that bounds the FILE, and a file licensed to name states can still log one
 * or put one in a thrown message. The repair was a regex pinning the words inside one array literal, and
 * Kimi A2 then found THAT too brittle: it pins a textual shape, so the first reformat breaks it in a way
 * whose obvious repair is to widen the regex and quietly lose the property.
 *
 * So the licence moved to a file that contains nothing but the list and its guard, and the bound became the
 * carriers. A state word can only reach a visitor by being rendered, thrown, logged or translated. Forbid
 * all four in the licensed file and the words cannot travel, whatever shape the list is written in.
 */
const STATE_WORD_CARRIERS: ReadonlyArray<{ carrier: string; pattern: RegExp }> = [
  { carrier: 'thrown', pattern: /\bthrow\b/ },
  { carrier: 'logged', pattern: /\bconsole\./ },
  { carrier: 'translated', pattern: /\bt\(|useTranslation/ },
];
// The fourth carrier, RENDERING, is not in that table and is not matched by a regex. A `.ts` file cannot
// contain JSX at all under this toolchain, so the extension assertion in the control below is the whole
// bound - structurally, which is stronger than any pattern. Both earlier attempts at a regex for it fired
// on a generic type parameter instead: `/<[A-Za-z]/` on `Partial<Record<...>>`, then `/<[A-Z]/` on
// `AssertNever<Exclude<...>>`. A pattern that cannot tell `<Exclude` from `<Button` would be loosened by the
// next reader who hit it, and then it would mean nothing.

/** The carriers present in a licensed file. Empty is the only acceptable answer. */
function carriersIn(code: string): string[] {
  return STATE_WORD_CARRIERS.filter((row) => row.pattern.test(code)).map((row) => row.carrier);
}

/** State-word hits in files no `PERMITTED_STATE_SOURCE` row covers. */
function unpermittedState(hits: Hit[], rows = PERMITTED_STATE_SOURCE): Hit[] {
  const allowed = new Map(rows.map((row) => [row.file, new Set(row.words)]));
  return hits.filter((hit) => !allowed.get(hit.file)?.has(hit.hit.toLowerCase()));
}

const SIGN_IN_SCOPED_PREFIXES = ['signIn.', 'callback.'] as const;

/** Values are prose, so word-bounded. A bare `locked` must not match `unlocked-by-design` in a sentence. */
function forbiddenStateCopy(scoped: boolean): RegExp {
  const source = scoped ? `${ALWAYS_FORBIDDEN_STATE}|${SIGN_IN_SCOPED_STATE}` : ALWAYS_FORBIDDEN_STATE;
  return new RegExp(`\\b(?:${source})`, 'i');
}

/**
 * Keys are identifiers, so UNBOUNDED - `signIn.accountLocked` has no word boundary before `locked`.
 *
 * Written out rather than derived from the prose pattern by stripping `\b`, which is what T28 did until Kimi
 * I2 pointed out that the two grammars were then coupled through a textual rewrite.
 */
const FORBIDDEN_STATE_KEY =
  'locked|lockout|suspended|deleted|deactivat|inactive|banned|terminated|archived|dormant'
  // Kimi I5. A key that names the CONCEPT is a signal that state-specific copy exists or is coming, even
  // when today's value is uniform - `signIn.accountState` with a neutral sentence is a slot waiting to be
  // filled. Deliberately NOT a bare `status`, which would flag `invoice.status` and every other commissioned
  // status key in the package.
  + '|accountState|accountStatus|account_status|lockoutReason|lockedReason'
  + '|suspensionReason|deletionReason';

/** Timers, anywhere in the tree. The allowlist below is what keeps this from failing specified code. */
function timerCalls(): RegExp {
  return /setTimeout|setInterval|requestIdleCallback|requestAnimationFrame|queueMicrotask/g;
}

/**
 * The three production modules that may hold a timer, with counts and reasons (Kimi F4, F5).
 *
 * MEASURED, comment-stripped, in Phase 2. `auth/`, `i18n/`, `app/` and `pwa/` hold none at all, so the
 * identity path is clean today without the scan needing to know what the identity path is.
 */
const PERMITTED_TIMERS: ReadonlyArray<{ file: string; hit: string; count: number; why: string }> = [
  {
    file: 'api/sessionBoundary.ts',
    hit: 'settimeout',
    count: 2,
    why: 'RENEWAL_TIMEOUT_MS and its type annotation - required by R21 and L18',
  },
  {
    file: 'api/sessionBoundary.ts',
    hit: 'queuemicrotask',
    count: 1,
    why: 'onRenewalFailed is notified outside the response chain (R21)',
  },
  {
    file: 'api/crossTabRenewal.ts',
    hit: 'settimeout',
    count: 2,
    why: 'the tab-lock grace window and the renewal deadline (R21, L18)',
  },
  {
    file: 'api/crossTabRenewal.ts',
    hit: 'queuemicrotask',
    count: 1,
    why: 'a broken listener is re-thrown outside the channel callback (R21)',
  },
  {
    file: 'api/rateLimitNotice.ts',
    hit: 'queuemicrotask',
    count: 1,
    why: 'a broken listener must not affect the response chain (R23)',
  },
];

/**
 * AC10's pin, cited to T28 rather than re-derived: exactly these keys exist under `signIn`.
 *
 * ONE MORE, JUSTIFIED: `sessionEnded` was added by **task 33** (R22) for a session that HAD completed and is
 * no longer usable. It could not reuse `discarded`, whose sentence says sign-in did not complete - false for a
 * visitor who signed in hours ago - and L4 does not require the reuse, because L4 makes copy identical across
 * account states and token conditions, not across every event in a session's life. The sentence names no state
 * and no cause, which the cases below now check for it too.
 */
const SIGN_IN_KEYS = [
  'action', 'discarded', 'explanation', 'failed', 'heading', 'sessionEnded', 'starting',
] as const;

/**
 * Account-state-shaped and role-shaped names, inherited from T28's AC7 instrument.
 *
 * NEVER a bare `state ===`, which T28 measured to have three legitimate hits - `CallbackScreen`'s two OAuth
 * comparisons, which R8 requires, and `registerServiceWorker.ts:72`'s Workbox lifecycle read.
 */
const ACCOUNT_STATE_GATE =
  'accountState|account_state|\\.mfaEnrolled|mfa_enrolled|requiresMfa|requires_mfa|enrollmentState'
  + '|enrollment_state|mfaRequired|mfa_required|PENDING_ENROLLMENT|accountLocked|account_locked'
  + '|isSuspended|is_suspended|isDeleted|is_deleted|isLocked|is_locked'
  // Kimi I1, accepted in substance and NOT in the form it was recommended. Kimi asked for a bare
  // `\.state\s*[=!]==` and `\.status\s*[=!]==`. MEASURED, both fire on legitimate code the spec
  // commissions: `registerServiceWorker.ts` compares `installing.state === 'installed'` (Workbox's own
  // lifecycle), and `rateLimitNotice.ts` and `sessionBoundary.ts` both compare `response.status` - an HTTP
  // status, which R23 and R21 require them to read. So the alternatives key on the RECEIVER as well: it is
  // the account's state that may not be branched on, not every object's.
  + '|(?:account|user|session)\\.(?:state|status)\\s*[=!]=='
  + '|(?:account|user|session)\\.locked\\b'
  + "|role\\s*[=!]==|\\.role\\s*[=!]==|=== *'(?:MERCHANT|ADMIN)'|=== *\"(?:MERCHANT|ADMIN)\""
  + "|\\.includes\\s*\\( *['\"](?:MERCHANT|ADMIN)"
  // Kimi phase 11, and the answer to the hole its own I1 fix left. Keying on the receiver means
  // `const { state } = account; if (state === 'LOCKED')` has no receiver left to key on, and the scan goes
  // green over a live gate. The alternative below keys on the LITERAL instead, so it survives any amount of
  // destructuring, aliasing or renaming - the one thing a state comparison cannot do without is the state's
  // name. It cannot be relaxed to a bare `state ===`: CallbackScreen compares the OIDC `state` parameter,
  // which is not an account state, and a case below pins that it stays silent there.
  + "|[=!]== *['\"](?:LOCKED|SUSPENDED|DELETED|PENDING_VERIFICATION|PENDING_ENROLLMENT)['\"]"
  + "|case +['\"](?:LOCKED|SUSPENDED|DELETED|PENDING_VERIFICATION)['\"] *:";

function accountStateGates(): RegExp {
  return new RegExp(ACCOUNT_STATE_GATE, 'g');
}

function namesForbiddenState(text: string, scoped = true): boolean {
  return forbiddenStateCopy(scoped).test(text);
}

// ---------------------------------------------------------------------------------------------------------
// Scanning the tree
// ---------------------------------------------------------------------------------------------------------

type ScanTarget = { path: string; code: string };
type Hit = { file: string; hit: string };

/**
 * Comments removed, STRING LITERALS KEPT.
 *
 * The naive two-regex version this replaced was an instrument defect, and Kimi I8 found it by reading rather
 * than by running. `source.replace(/\/\/.*$/gm, '')` deletes from the first `//` to the end of the line -
 * INCLUDING inside a string. Measured:
 *
 *     const help = "https://id.example/help/locked-account";
 *       ->  const help = "https:
 *
 * The word `locked` is gone, and every scan built on the stripper reports clean. A help URL carrying a state
 * word is the most likely shape for this leak to take, because a URL is the one place `//` appears in
 * ordinary code - so the hole sat exactly where the data would be.
 *
 * This walks the source instead: comments outside strings are dropped, quotes and template literals are
 * copied through with their escapes, and a backslash always consumes the character after it. Measured against
 * all 24 production modules: byte-identical output to the old stripper today, so nothing about the current
 * scan results changes - the fix is for the file that has not been written yet.
 *
 * Known limit, declared: a `//` in JSX TEXT (not in a string) is still read as a comment. No production
 * module has one, and the old stripper had the same limit.
 */
function strip(source: string): string {
  let out = '';
  let index = 0;
  while (index < source.length) {
    const char = source[index];
    const next = source[index + 1];
    if (char === '/' && next === '/') {
      while (index < source.length && source[index] !== '\n') index += 1;
      continue;
    }
    if (char === '/' && next === '*') {
      index += 2;
      while (index < source.length && !(source[index] === '*' && source[index + 1] === '/')) index += 1;
      index += 2;
      continue;
    }
    if (char === '"' || char === "'" || char === '`') {
      const quote = char;
      out += char;
      index += 1;
      while (index < source.length) {
        if (source[index] === '\\') {
          out += source[index] + (source[index + 1] ?? '');
          index += 2;
          continue;
        }
        if (source[index] === quote) {
          out += quote;
          index += 1;
          break;
        }
        out += source[index];
        index += 1;
      }
      continue;
    }
    out += char;
    index += 1;
  }
  return out;
}

/**
 * The emptiness guard, factored out so the CONTROL cases drive the REAL one.
 *
 * T28's first draft inlined it at each call site and its controls re-implemented the same three lines, so
 * they tested a copy and would have stayed green if the guard were deleted.
 */
function refuseEmpty<T>(items: T[], refusal: string): T[] {
  if (items.length === 0) throw new Error(refusal);
  return items;
}

const NO_SOURCES = 'scan refused: no production sources found under src/';
const NO_STRINGS = 'catalogue scan refused: no strings found';

function sourceRoot(): string {
  // Two levels up from this file: `src/auth/<this>` -> `src`. Derived from the runner rather than written, so
  // moving the file cannot silently narrow the scan to a directory that no longer exists.
  return dirname(dirname(expect.getState().testPath as string));
}

function rel(root: string, file: string): string {
  return relative(root, file).split(/[\\/]/).join('/');
}

async function productionFiles(): Promise<string[]> {
  async function walk(dir: string): Promise<string[]> {
    const found: string[] = [];
    for (const entry of await readdir(dir, { withFileTypes: true })) {
      const full = join(dir, entry.name);
      if (entry.isDirectory()) found.push(...(await walk(full)));
      else if (/\.tsx?$/.test(entry.name) && !/\.test\.tsx?$/.test(entry.name)) found.push(full);
    }
    return found;
  }
  return refuseEmpty((await walk(sourceRoot())).sort(), NO_SOURCES);
}

/**
 * Every production file, comment-stripped.
 *
 * STRIPPING IS LOAD-BEARING. `CallbackScreen.tsx:35` reads "A missing `state`, a mismatched `state`, a
 * refused exchange and a bad `nonce` are four different faults and one single outcome" - it describes this
 * very property, in a comment, using two of the words a state scan looks for. An unstripped scan reads the
 * package's own justification as the defect, and this file's header does the same thing a dozen times over.
 */
async function productionSources(): Promise<ScanTarget[]> {
  const files = await productionFiles();
  const srcRoot = sourceRoot();
  const targets: ScanTarget[] = [];
  for (const file of files) {
    targets.push({ path: rel(srcRoot, file), code: strip(await readFile(file, 'utf8')) });
  }
  return targets;
}

function hitsOf(targets: ScanTarget[], pattern: RegExp): Hit[] {
  const found: Hit[] = [];
  for (const target of targets) {
    for (const match of target.code.matchAll(pattern)) {
      found.push({ file: target.path, hit: match[0].toLowerCase() });
    }
  }
  return found;
}

/**
 * The hits no allowlist row accounts for.
 *
 * COUNTED, not merely matched: a row says how many times a file may legally make the call, so a THIRD
 * `setTimeout` in a module allowed two is reported while the first two are not. That is the whole point of
 * the allowlist replacing a file list - a new timer anywhere needs a new row with a reason.
 */
function unexplained(
  hits: Hit[],
  permitted: ReadonlyArray<{ file: string; hit: string; count: number }>,
): Hit[] {
  const budget = new Map(permitted.map((row) => [`${row.file}:${row.hit}`, row.count]));
  return hits.filter((hit) => {
    const key = `${hit.file}:${hit.hit}`;
    const allowed = budget.get(key) ?? 0;
    if (allowed === 0) return true;
    budget.set(key, allowed - 1);
    return false;
  });
}

/** A hand-written target, for the synthetic violations AC11 requires. Never read from disk. */
function synthetic(path: string, code: string): ScanTarget {
  return { path, code: strip(code) };
}

/**
 * Every string in the catalogue, flattened to `a.b.c` keys.
 *
 * `root` is a parameter so the controls can scan a synthetic structure instead of the real catalogue. An
 * array is walked too, because `typeof [] === 'object'` and `Object.entries` yields its indices - measured in
 * T28 when the same claim was raised and rejected there.
 */
function catalogueStrings(root: unknown = en): Array<{ key: string; value: string }> {
  const flat: Array<{ key: string; value: string }> = [];
  const visit = (node: unknown, path: string[]): void => {
    if (typeof node === 'string') {
      flat.push({ key: path.join('.'), value: node });
      return;
    }
    if (typeof node === 'object' && node !== null) {
      for (const [key, value] of Object.entries(node)) visit(value, [...path, key]);
    }
  };
  visit(root, []);
  return refuseEmpty(flat, NO_STRINGS);
}

/** Which catalogue entries name an account state, and whether by value or by key name. */
function forbiddenStateEntries(entries: Array<{ key: string; value: string }>): string[] {
  const permitted = new Set(PERMITTED_STATE_COPY.map((row) => row.key));
  const flagged: string[] = [];
  for (const entry of entries) {
    if (permitted.has(entry.key)) continue;
    const scoped = SIGN_IN_SCOPED_PREFIXES.some((prefix) => entry.key.startsWith(prefix));
    if (forbiddenStateCopy(scoped).test(entry.value)) flagged.push(`${entry.key} (value)`);
    else if (new RegExp(FORBIDDEN_STATE_KEY, 'i').test(entry.key)) flagged.push(`${entry.key} (key)`);
  }
  return flagged;
}

// ---------------------------------------------------------------------------------------------------------
// The console watcher
// ---------------------------------------------------------------------------------------------------------

const CONSOLE_CHANNELS = ['log', 'info', 'warn', 'error', 'debug', 'trace'] as const;

type ConsoleChannel = (typeof CONSOLE_CHANNELS)[number];

type ConsoleWatch = { lines: string[]; channels: readonly ConsoleChannel[] };

/** Installed BEFORE the code under test runs, always. AC11 holds the control that proves it matters. */
function watchConsole(): ConsoleWatch {
  const lines: string[] = [];
  for (const channel of CONSOLE_CHANNELS) {
    vi.spyOn(console, channel).mockImplementation((...args: unknown[]) => {
      lines.push(`${channel}: ${args.map((arg) => String(arg)).join(' ')}`);
    });
  }
  return { lines, channels: CONSOLE_CHANNELS };
}

// ---------------------------------------------------------------------------------------------------------
// The arrival harness
// ---------------------------------------------------------------------------------------------------------

const STATE = 'the-state-this-tab-stored';
const NONCE = 'the-nonce-this-tab-stored';
const CODE = 'THE-AUTHORIZATION-CODE';
const VERIFIER = 'v'.repeat(64);

/** Mints an ID token. Encoded through TextEncoder rather than `btoa(JSON.stringify())`, which throws above U+00FF. */
function tokenWith(claims: unknown): string {
  const segment = (value: unknown) => {
    const bytes = new TextEncoder().encode(JSON.stringify(value));
    let binary = '';
    for (const byte of bytes) binary += String.fromCharCode(byte);
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  };
  return `${segment({ alg: 'RS256', kid: 'test' })}.${segment(claims)}.c2lnbmF0dXJl`;
}

function tokenEndpointAnswers(claims: Record<string, unknown> = {}): () => Response {
  return () =>
    new Response(
      JSON.stringify({
        id_token: tokenWith({ nonce: NONCE, sub: 'the-subject', iss: 'http://localhost:5173', ...claims }),
        access_token: 'the-access-token',
        token_type: 'Bearer',
        expires_in: 300,
      }),
      { status: 200, headers: { 'content-type': 'application/json' } },
    );
}

let mounted: { root: Root; container: HTMLElement } | undefined;

afterEach(() => {
  if (mounted) {
    act(() => mounted?.root.unmount());
    mounted.container.remove();
    mounted = undefined;
  }
  document.body.innerHTML = '';
  // GLOBAL, and they survive a document reset, so one case's sentinel would be the next case's leak. T28's
  // Kimi Gap 2 was exactly this, found after the surfaces were added and before the contamination was seen.
  document.title = '';
  window.name = '';
  globalThis.sessionStorage.clear();
  globalThis.localStorage.clear();
  // AFTER the unmount above, which runs inside `act` and may schedule work. Restoring the clock first would
  // hand that work a real timer mid-teardown.
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  vi.resetModules();
});

/** One macrotask turn inside act, for the paths that do real async work. NEVER used under a fake clock. */
async function turn(): Promise<void> {
  await act(async () => {
    await new Promise((resolve) => {
      setTimeout(resolve, 0);
    });
  });
}

function stubFetch(answer: () => Response | Promise<Response>): Mock {
  const NodeRequest = globalThis.Request;
  vi.stubGlobal(
    'Request',
    class extends NodeRequest {
      constructor(input: RequestInfo | URL, init?: RequestInit) {
        super(typeof input === 'string' ? new URL(input, 'http://localhost:3000') : input, init);
      }
    },
  );
  const spy = vi.fn(async () => answer());
  vi.stubGlobal('fetch', spy);
  return spy;
}

/** The three items T15 writes and the callback reads back (L16). Seeded so a callback can be admitted at all. */
function seedPkceItems(): void {
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.state, STATE);
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.nonce, NONCE);
  globalThis.sessionStorage.setItem(AUTHORIZE_STORAGE_KEYS.codeVerifier, VERIFIER);
}

type Arrival = {
  container: HTMLElement;
  router: ReturnType<typeof createMemoryRouter>;
  fetchSpy: Mock;
  unmount(): void;
};

/** One place that builds a callback URL, so no case writes one. */
function queryForState(fixture: StateFixture): string {
  const params = new URLSearchParams({
    error: fixture.error,
    error_description: fixture.description,
    error_uri: fixture.uri,
    state: STATE,
  });
  return `?${params.toString()}`;
}

async function mountRoute(query: string, answer: () => Response | Promise<Response>): Promise<Arrival> {
  const fetchSpy = stubFetch(answer);
  // Imported DYNAMICALLY because `vi.resetModules()` runs in `afterEach`: a statically imported module would
  // be a different instance than the screens hold (recorded in T26). A dynamic import is still
  // compile-checked, so a renamed export is a type error rather than a silent `undefined`.
  const routes = await import('../app/routes');
  const router = createMemoryRouter(routes.appRoutes, {
    basename: routes.APP_BASENAME,
    initialEntries: [`${routes.APP_BASENAME}/${routes.CALLBACK_PATH}${query}`],
  });
  const container = document.createElement('div');
  document.body.append(container);
  const root = createRoot(container);
  await act(async () => {
    root.render(
      <I18nextProvider i18n={createI18n()}>
        <RouterProvider router={router} />
      </I18nextProvider>,
    );
  });
  mounted = { root, container };
  return {
    container,
    router,
    fetchSpy,
    // `arrival.unmount()`, never `mounted = undefined` - abandoning a root without unmounting leaves its
    // effects live, and a second `CallbackScreen` still running would have its writes attributed to the next
    // arrival. T26's self-review found exactly that defect in its own first draft.
    unmount: () => {
      act(() => root.unmount());
      container.remove();
      mounted = undefined;
    },
  };
}

/** The ordinary arrival: real clock, one macrotask turn, everything the screen does has settled. */
async function arriveAt(query: string, answer: () => Response | Promise<Response>): Promise<Arrival> {
  const arrival = await mountRoute(query, answer);
  await turn();
  return arrival;
}

/**
 * AC5's arrival, and the reason it exists is measured rather than cautious.
 *
 * `turn()` awaits `setTimeout(resolve, 0)`. Under `vi.useFakeTimers()` that callback is queued on the fake
 * clock and never runs unless the clock is advanced - so a case that reused `arriveAt` here would HANG until
 * vitest's timeout rather than fail, which is the worst shape a timing test can take.
 *
 * The fake clock is installed BEFORE the render, so a `setTimeout` the screen schedules is captured rather
 * than having already fired. The flush is microtasks only: `CallbackScreen.tsx:86-104` reads `error` and
 * calls `discard()` with nothing awaited between them, so the sentence is rendered without the clock moving.
 * Measured in Phase 5, against the real route graph, before this file was written.
 */
async function arriveUnderFakeClock(query: string): Promise<Arrival> {
  vi.useFakeTimers();
  seedPkceItems();
  const arrival = await mountRoute(query, tokenEndpointAnswers());
  await act(async () => {});
  return arrival;
}

/** Everything one arrival leaves behind, cleared in ONE place - including the two global surfaces. */
async function clearArrivalSurfaces(): Promise<void> {
  const { clearAccessToken } = await import('./accessToken');
  const { takeSignInNotice } = await import('./signInNotice');
  clearAccessToken();
  takeSignInNotice();
  document.body.innerHTML = '';
  document.title = '';
  window.name = '';
  globalThis.sessionStorage.clear();
  globalThis.localStorage.clear();
}

// ---------------------------------------------------------------------------------------------------------
// The outcome struct — the uniformity half of the claim
// ---------------------------------------------------------------------------------------------------------

type Outcome = {
  destination: string;
  historyAction: string;
  search: string;
  hash: string;
  text: string;
  liveText: string[];
  liveRegions: number;
  // Kimi phase 11. `liveRegions` is a COUNT, which is what let these three through: two states could announce
  // the same sentence through regions of different shape, land focus in different places, or render different
  // element trees, and every field above would still compare equal.
  liveShape: string[];
  focus: string;
  skeleton: string;
  storedKeys: string[];
  tokenHeld: boolean;
};

/**
 * The rendered element tree with every piece of TEXT removed: tag names, nesting depth, and the ARIA
 * attributes that decide how the tree is announced.
 *
 * Kimi's phase 11 gap on structural equivalence. AC4 pins the absence of `class` and `style`, which is the
 * whole of "visual treatment" only while no design system exists. A cause-specific icon, an extra wrapper, a
 * different heading level or an `<aside>` around one state's sentence needs neither attribute, and the text
 * comparison would not see it because the sentence itself is unchanged. Text is excluded deliberately: the
 * copy is already compared field by field, and including it here would make the skeleton fail for reasons
 * that have nothing to do with structure.
 */
function skeletonOf(root: Element): string {
  const lines: string[] = [];
  const walk = (node: Element, depth: number): void => {
    const aria = [...node.attributes]
      .filter((attribute) => attribute.name === 'role' || attribute.name.startsWith('aria-'))
      .map((attribute) => `${attribute.name}=${attribute.value}`)
      .sort();
    lines.push(`${'  '.repeat(depth)}${node.tagName.toLowerCase()}${aria.length > 0 ? ` [${aria.join(' ')}]` : ''}`);
    for (const child of node.children) walk(child, depth + 1);
  };
  walk(root, 0);
  return lines.join('\n');
}

/**
 * Where the keyboard is, named by what a visitor would reach rather than by object identity - the elements
 * are torn down between arrivals, so identity could not be compared across five of them anyway.
 *
 * `SignInScreen.tsx:68-69` says focus is deliberately NOT moved, so the honest expectation is that all five
 * agree on <body>. The claim is the agreement, not the particular answer: a state-keyed `.focus()` call, or
 * one state's focus deferred by a timer, is exactly the kind of difference a screen-reader user hears and
 * no other field in this struct records.
 */
/**
 * The sign-in screen's element tree, pinned. Kimi's phase 11 asked for structural equivalence across the five
 * states; this is the absolute half of that, and it is the half that matters, because the five cannot diverge
 * from each other here - they all arrive through one closure - but they can all move together.
 *
 * Text is excluded by construction, so the sentence may change without touching this. What cannot change
 * quietly is a new wrapper, a different heading level, a cause-specific <section>, or a live region that
 * stops being a live region.
 */
const SIGN_IN_SKELETON = [
  'body',
  '  div',
  '    main',
  '      h1',
  '      p',
  '      button [aria-busy=false aria-describedby=sign-in-explanation aria-disabled=false]',
  '      div [aria-live=polite role=status]',
].join('\n');

function focusOf(): string {
  const active = document.activeElement;
  if (active === null) return 'none';
  const role = active.getAttribute('role');
  return `${active.tagName.toLowerCase()}${role === null ? '' : `[role=${role}]`}`;
}

/**
 * `search` and `hash` are fields AND are asserted absolutely in AC1.
 *
 * Both, deliberately. As fields they catch a DIVERGENCE between two states; only the absolute assertion
 * catches five states that all kept their query string, which compares equal and passes. T28's MB29 is the
 * mutant that made the distinction, and it survived that file until the absolute assertion was added.
 *
 * The NOTICE is not a field, and that is T28-D3 rather than an omission: `SignInScreen.tsx:22` takes it in a
 * `useState` initialiser and `takeSignInNotice()` clears on read, so by the time a test can look it is `null`
 * on every path - identical for the right and the wrong reason. It is observed here through the rendered
 * document, which is where it reaches the visitor anyway.
 */
async function outcomeOf(arrival: Arrival): Promise<Outcome> {
  const { accessToken } = await import('./accessToken');
  const regions = [...document.querySelectorAll('[aria-live], [role=status], [role=alert]')];
  return {
    destination: arrival.router.state.location.pathname,
    historyAction: arrival.router.state.historyAction,
    search: arrival.router.state.location.search,
    hash: arrival.router.state.location.hash,
    text: document.body.textContent ?? '',
    liveText: regions.map((node) => node.textContent ?? ''),
    liveRegions: regions.length,
    liveShape: regions.map(
      (node) => `${node.getAttribute('role') ?? '-'}/${node.getAttribute('aria-live') ?? '-'}`,
    ),
    focus: focusOf(),
    skeleton: skeletonOf(document.body),
    storedKeys: Object.keys({ ...globalThis.sessionStorage }).sort(),
    tokenHeld: accessToken.current() !== null,
  };
}

async function measure(query: string, answer: () => Response | Promise<Response>): Promise<Outcome> {
  seedPkceItems();
  const arrival = await arriveAt(query, answer);
  const outcome = await outcomeOf(arrival);
  arrival.unmount();
  await clearArrivalSurfaces();
  return outcome;
}

function measureState(fixture: StateFixture): Promise<Outcome> {
  return measure(queryForState(fixture), tokenEndpointAnswers());
}

/** The live regions' text, read the one way AC5 asserts it. Empty strings dropped, so the shape is stable. */
function renderedSentences(): string[] {
  return [...document.querySelectorAll('[aria-live], [role=status], [role=alert]')]
    .map((node) => node.textContent ?? '')
    .filter((text) => text.length > 0);
}

// ---------------------------------------------------------------------------------------------------------
// The leak sweep
// ---------------------------------------------------------------------------------------------------------

type Surface = { name: string; text: string };

/**
 * Every surface a state word could survive on, after the discard.
 *
 * The document surface reaches the TITLE and the HEAD, not only the body. T28's mutation harness found that
 * hole the hard way: a mutant writing `error_description` into `document.title` survived its whole suite
 * until the surface was widened. The tab title is read by the visitor, announced by screen readers on
 * navigation, and kept in browser history and session restore, so it outlives the discard in a way the DOM
 * does not.
 *
 * `document.cookie` and `indexedDB` are NOT here, declared rather than quietly omitted: jsdom gives an empty
 * cookie jar and no `indexedDB`, so sweeping them would report clean for an environmental reason and read as
 * evidence. T22 owns the leak claim across the application; this sweep is R30's slice of it.
 */
function surfacesAfter(
  router: ReturnType<typeof createMemoryRouter>,
  consoleWatch: ConsoleWatch,
): Surface[] {
  return [
    {
      name: 'document',
      text: [
        document.body.textContent ?? '',
        document.body.innerHTML,
        document.title,
        document.head.innerHTML,
      ].join(' '),
    },
    { name: 'window.name', text: window.name },
    { name: 'sessionStorage', text: JSON.stringify({ ...globalThis.sessionStorage }) },
    { name: 'localStorage', text: JSON.stringify({ ...globalThis.localStorage }) },
    { name: 'router location', text: JSON.stringify(router.state.location) },
    { name: 'console', text: consoleWatch.lines.join('\n') },
  ];
}

/**
 * A surface's text in every encoding a value can arrive in.
 *
 * MEASURED WHILE THIS FILE WAS BEING WRITTEN, AND IT WAS VERY NEARLY A HOLE. `URLSearchParams.toString()`
 * encodes a space as `+`, so the router location carries
 * `error_description=This+account+has+been+suspended+by+an+administrator` while the needle is the same
 * sentence with spaces. A sweep comparing raw text to a raw needle would have reported CLEAN on a description
 * that survived in a URL - which is the single most likely shape for this leak to take, because a URL is
 * where the description arrives in the first place.
 *
 * Found by a control that failed for the wrong reason: the assertion guessed `%20` and the measurement said
 * `+`. Both forms are searched now, and the percent-decoded form with them.
 */
function encodings(text: string): string[] {
  const forms = new Set([text]);
  try {
    forms.add(decodeURIComponent(text.replace(/\+/g, ' ')));
  } catch {
    // A malformed escape sequence is not a reason to stop sweeping the raw form.
  }
  return [...forms];
}

/** Which surfaces carry which needle, in any encoding. Returns `surface/needle` pairs so a failure names both. */
function leaks(surfaces: Surface[], needles: string[]): string[] {
  const found: string[] = [];
  for (const surface of surfaces) {
    const forms = encodings(surface.text).map((form) => form.toLowerCase());
    for (const needle of needles) {
      if (forms.some((form) => form.includes(needle.toLowerCase()))) found.push(`${surface.name}/${needle}`);
    }
  }
  return found;
}

/** Every `class` and `style` attribute on every node under `root`, including `root` itself (AC4). */
function styleAttributesOf(root: HTMLElement): string[] {
  const found: string[] = [];
  for (const node of [root, ...root.querySelectorAll('*')]) {
    for (const attribute of ['class', 'style']) {
      const value = node.getAttribute(attribute);
      if (value !== null) found.push(`${node.nodeName.toLowerCase()}[${attribute}=${value}]`);
    }
  }
  return found;
}

/** F9's synthetic: the sentence arrives only once the clock moves. The control that AC5 can fail. */
function Delayed() {
  const [text, setText] = useState('');
  useEffect(() => {
    const timer = setTimeout(() => setText(en.signIn.discarded), 100);
    return () => clearTimeout(timer);
  }, []);
  return (
    <div role="status" aria-live="polite">
      {text}
    </div>
  );
}

// ---------------------------------------------------------------------------------------------------------
// AC2 first — the fixtures are distinct, before anything claims they are indistinguishable
// ---------------------------------------------------------------------------------------------------------

describe('AC2: the five fixtures are genuinely five', () => {
  /**
   * THE DOMINANT VACUITY RISK IN THIS FILE, and the reason this block comes first.
   *
   * Everything below compares five arrivals and asserts they are indistinguishable. Five fixtures that were
   * secretly one fixture would satisfy every one of those assertions while proving nothing at all - which is
   * precisely the objection Kimi F2 raised against representing all five states with a single response. The
   * fixtures are checked for distinctness HERE so that the equivalence claims downstream mean something.
   */
  it('names five states, with five distinct descriptions and five distinct URIs', () => {
    expect(ACCOUNT_STATES).toHaveLength(5);
    expect(ACCOUNT_STATES.map((fixture) => fixture.state)).toEqual([
      'unknown',
      'missing',
      'locked',
      'suspended',
      'deleted',
    ]);
    expect(new Set(ACCOUNT_STATES.map((fixture) => fixture.description)).size).toBe(5);
    expect(new Set(ACCOUNT_STATES.map((fixture) => fixture.uri)).size).toBe(5);
    expect(new Set(ACCOUNT_STATES.map((fixture) => queryForState(fixture))).size).toBe(5);
  });

  it("each fixture's own state word appears in its own description and URI and in no other's", () => {
    // Not just distinct - DISCRIMINATING. A leak sweep can only name which state leaked if the needle for one
    // state is absent from the other four.
    for (const fixture of ACCOUNT_STATES) {
      const others = ACCOUNT_STATES.filter((other) => other.state !== fixture.state);
      expect(`${fixture.description} ${fixture.uri}`.toLowerCase()).toContain(fixture.state);
      expect(
        others.filter((other) =>
          `${other.description} ${other.uri}`.toLowerCase().includes(fixture.state),
        ),
      ).toEqual([]);
    }
  });

  it('every fixture carries all three error fields onto the arrival URL', () => {
    for (const fixture of ACCOUNT_STATES) {
      const params = new URLSearchParams(queryForState(fixture));
      expect(params.get('error')).toBe(fixture.error);
      expect(params.get('error_description')).toBe(fixture.description);
      expect(params.get('error_uri')).toBe(fixture.uri);
      expect(params.get('state')).toBe(STATE);
    }
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC1 — each of the five ends in the one discard closure
// ---------------------------------------------------------------------------------------------------------

describe('AC1: every account state ends in the one discard closure', () => {
  it('routes each state to the sign-in start with the discarded copy and no token', async () => {
    const seen: Array<{ state: string; outcome: Outcome }> = [];
    for (const fixture of ACCOUNT_STATES) {
      seen.push({ state: fixture.state, outcome: await measureState(fixture) });
    }
    expect(seen).toHaveLength(5);
    for (const { state, outcome } of seen) {
      expect(outcome.destination, state).toBe('/app/sign-in');
      expect(outcome.historyAction, state).toBe('REPLACE');
      expect(outcome.liveText, state).toEqual([en.signIn.discarded]);
      expect(outcome.tokenHeld, state).toBe(false);
      // Absolute, not merely equal across the five. Five states that all kept their query string would
      // compare equal and pass the equivalence below - T28's MB29 is the mutant that proved it.
      expect(outcome.search, state).toBe('');
      expect(outcome.hash, state).toBe('');
      // The same reasoning, applied to the three fields Kimi's phase 11 added. Writing them into the struct
      // catches a DIVERGENCE, and nothing else: all five announced through `role="alert"`, or all five with
      // focus yanked to the heading, or all five wrapped in an extra <section>, compare equal and pass. The
      // sign-in screen cannot see which state refused the visitor - every one arrives through the single
      // `discard()` closure - so a cause-keyed divergence here is not even expressible in one file. What is
      // expressible, and what these three pin, is a UNIFORM change to the announcement, the keyboard
      // position or the element tree.
      expect(outcome.liveShape, state).toEqual(['status/polite']);
      // `body`, because `SignInScreen.tsx:68-69` declines to move focus and says why: the button the visitor
      // pressed is still there and is the control they need. Pinned so that a `.focus()` added later is a
      // decision somebody makes against this line, rather than a silent change in what a screen reader says
      // first.
      expect(outcome.focus, state).toBe('body');
      expect(outcome.skeleton, state).toBe(SIGN_IN_SKELETON);
    }
  });

  it('never exchanges a code, because an error response carries none (RFC 6749 §4.1.2.1)', async () => {
    for (const fixture of ACCOUNT_STATES) {
      seedPkceItems();
      const arrival = await arriveAt(queryForState(fixture), tokenEndpointAnswers());
      expect(arrival.fetchSpy, fixture.state).not.toHaveBeenCalled();
      arrival.unmount();
      await clearArrivalSurfaces();
    }
  });

  /**
   * Kimi's phase 11. The case above drives error-ONLY callbacks, which is what SAS sends - so it proves the
   * code is not exchanged when there is no code to exchange, and that is a weaker claim than it reads as.
   *
   * RFC 6749 does not forbid an authorization response carrying both, and the SPA's own guard is an ordering
   * decision rather than an impossibility: `CallbackScreen` reads `error` first and discards before it ever
   * looks for `code`. A refactor that read `code` first would exchange a code handed over alongside a refusal
   * - a credential the server has already said no to - and every other assertion in this file would still
   * pass, because the outcome after the exchange failed would look the same.
   */
  it('discards without exchanging even when a code arrives ALONGSIDE the error', async () => {
    for (const fixture of ACCOUNT_STATES) {
      seedPkceItems();
      const query = `${queryForState(fixture)}&code=A-CODE-THAT-MUST-NOT-BE-SENT`;
      const arrival = await arriveAt(query, tokenEndpointAnswers());
      expect(arrival.fetchSpy, fixture.state).not.toHaveBeenCalled();
      const outcome = await outcomeOf(arrival);
      expect(outcome.destination, fixture.state).toBe('/app/sign-in');
      expect(outcome.text, fixture.state).toContain(en.signIn.discarded);
      expect(outcome.tokenHeld, fixture.state).toBe(false);
      arrival.unmount();
      await clearArrivalSurfaces();
    }
  });

  /**
   * Kimi's phase 11. `error_description` and `error_uri` are OPTIONAL in RFC 6749 §4.1.2.1. SAS sends both
   * today, and every fixture in this file sends both, so the whole suite rests on a response shape no
   * standard guarantees - a proxy that strips them, or a backend change, would produce an arrival nothing
   * here has driven.
   *
   * The risk is specific rather than theoretical: code that renders a description would most naturally fall
   * back to something ELSE when it is missing, and a fallback keyed on the error code is a state-specific
   * message arriving by the back door. The five `error` codes are driven bare, with nothing else to render.
   */
  it('discards identically when the description and URI are absent entirely', async () => {
    const outcomes: Outcome[] = [];
    for (const fixture of ACCOUNT_STATES) {
      seedPkceItems();
      const bare = new URLSearchParams({ error: fixture.error, state: STATE });
      const arrival = await arriveAt(`?${bare.toString()}`, tokenEndpointAnswers());
      outcomes.push(await outcomeOf(arrival));
      arrival.unmount();
      await clearArrivalSurfaces();
    }
    for (const outcome of outcomes) {
      expect(outcome.destination).toBe('/app/sign-in');
      expect(outcome.historyAction).toBe('REPLACE');
      expect(outcome.search).toBe('');
      expect(outcome.text).toContain(en.signIn.discarded);
      expect(outcome.tokenHeld).toBe(false);
    }
    // And bare arrivals are indistinguishable from each other, not merely each acceptable on its own.
    for (const outcome of outcomes.slice(1)) expect(outcome).toEqual(outcomes[0]);
  });

  it('shows the sentence the catalogue owns, not a literal written here', async () => {
    const outcome = await measureState(ACCOUNT_STATES[0]);
    expect(outcome.text).toContain(en.signIn.discarded);
    expect(namesForbiddenState(outcome.text)).toBe(false);
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC2 — one outcome struct for all five
// ---------------------------------------------------------------------------------------------------------

describe('AC2: five account states, one outcome', () => {
  it('produces an identical struct for all five', async () => {
    const measured: Array<{ state: string; outcome: Outcome }> = [];
    for (const fixture of ACCOUNT_STATES) {
      measured.push({ state: fixture.state, outcome: await measureState(fixture) });
    }
    // An absolute five, not `ACCOUNT_STATES.length`: both sides of that comparison come from the same array,
    // so it would stay green if the list were emptied and every assertion below became vacuous.
    expect(measured).toHaveLength(5);
    const reference = measured[0];
    for (const { state, outcome } of measured.slice(1)) {
      expect(outcome, `${state} diverged from ${reference.state}`).toEqual(reference.outcome);
    }
  });

  it('CONTROL: the comparison fails on a successful callback, so it is not blind', async () => {
    const failure = await measureState(ACCOUNT_STATES[0]);
    const success = await measure(`?code=${CODE}&state=${STATE}`, tokenEndpointAnswers());
    expect(success).not.toEqual(failure);
    // Named rather than left to `not.toEqual`, which would also pass if only one field differed. Measured: a
    // success lands on /app/home with no live region and no stored items - and, SINCE TASK 174, with the
    // landing screen's own copy. It used to read `''`, because that route had no element and a successful
    // sign-in ended on a blank document. The empty string was never the claim; what this control needs is
    // that a success differs from a failure, and copy that is demonstrably NOT a failure message is a
    // stronger form of that than no copy at all.
    expect(success.destination).toBe('/app/home');
    // GROWN AGAIN BY TASK 42, which put the authenticated chrome above the landing screen - so the body
    // text of a SUCCESS now begins with the sign-out label, in DOM order. Grown to the exact string rather
    // than loosened to a `toContain`: a `toContain` would also pass for chrome that had started leaking a
    // name into the page, which is the one thing R22 and this file's own subject forbid.
    expect(success.text).toBe(`${en.shell.signOut}${en.home.heading}${en.home.explanation}`);
    expect(success.liveRegions).toBe(0);
    expect(success.storedKeys).toEqual([]);
    expect(failure.storedKeys.length).toBeGreaterThan(0);
  });

  it('CONTROL: the struct comparison alone would MISS a query string kept by all five', () => {
    /**
     * Kimi I7. AC1 asserts `search` and `hash` are empty ABSOLUTELY, and the reason is easy to lose: the
     * struct carries both fields, so a reader can conclude the equivalence check already covers them. It does
     * not. Five states that all kept the same query string compare equal and pass, and the error description
     * would be sitting in the URL of every one of them.
     *
     * Built here rather than mutated into production, because the point is about the COMPARISON and not about
     * any code path. T28's MB29 is the production-side proof; this is the explanation beside it.
     */
    const kept = (state: string): Outcome => ({
      destination: '/app/sign-in',
      historyAction: 'REPLACE',
      search: '?why=This+account+has+been+suspended',
      hash: '',
      text: en.signIn.discarded,
      liveText: [en.signIn.discarded],
      liveRegions: 1,
      liveShape: ['status/polite'],
      focus: 'body',
      skeleton: 'body\n  main',
      storedKeys: [],
      tokenHeld: false,
      state,
    } as Outcome & { state: string });
    const five = ['unknown', 'missing', 'locked', 'suspended', 'deleted'].map((name) => kept(name));
    // The equivalence this file makes elsewhere - every field but the label - passes.
    for (const outcome of five.slice(1)) {
      expect({ ...outcome, state: undefined }).toEqual({ ...five[0], state: undefined });
    }
    // The absolute assertion is the only thing that fails.
    expect(() => expect(five[0].search).toBe('')).toThrow();
  });

  it('CONTROL: the struct is not a shell of empty fields', async () => {
    const outcome = await measureState(ACCOUNT_STATES[0]);
    expect(outcome.text.length).toBeGreaterThan(0);
    expect(outcome.liveText.join('').length).toBeGreaterThan(0);
    expect(outcome.storedKeys).toEqual(
      [AUTHORIZE_STORAGE_KEYS.state, AUTHORIZE_STORAGE_KEYS.nonce, AUTHORIZE_STORAGE_KEYS.codeVerifier].sort(),
    );
    // The three fields Kimi's phase 11 added. A field that is always '' or always [] compares equal across
    // five states for the wrong reason, which is the whole failure mode this control exists to rule out.
    expect(outcome.liveShape).not.toEqual([]);
    expect(outcome.liveShape.every((shape) => shape === '-/-')).toBe(false);
    expect(outcome.focus).not.toBe('none');
    expect(outcome.skeleton.split('\n').length).toBeGreaterThan(1);
    expect(outcome.skeleton).toContain('[');
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC3 — no state word, description or URI survives the discard
// ---------------------------------------------------------------------------------------------------------

describe('AC3: the state, its description and its URI do not survive the discard', () => {
  it('leaves none of the three on any surface, for any of the five states', async () => {
    for (const fixture of ACCOUNT_STATES) {
      const consoleWatch = watchConsole();
      seedPkceItems();
      const arrival = await arriveAt(queryForState(fixture), tokenEndpointAnswers());
      const found = leaks(surfacesAfter(arrival.router, consoleWatch), needlesFor(fixture));
      expect(found, `${fixture.state} leaked`).toEqual([]);
      arrival.unmount();
      await clearArrivalSurfaces();
      vi.restoreAllMocks();
    }
  });

  it('CONTROL: every swept surface is actually read, proven with a sentinel on each', async () => {
    const consoleWatch = watchConsole();
    seedPkceItems();
    const arrival = await arriveAt(queryForState(ACCOUNT_STATES[2]), tokenEndpointAnswers());
    const sentinel = 'SENTINEL-NEEDLE';
    document.body.innerHTML += `<span>${sentinel}</span>`;
    document.title = sentinel;
    document.head.innerHTML += `<meta name="t29" content="${sentinel}">`;
    window.name = sentinel;
    globalThis.sessionStorage.setItem('t29.sentinel', sentinel);
    globalThis.localStorage.setItem('t29.sentinel', sentinel);
    consoleWatch.lines.push(sentinel);
    const surfaces = surfacesAfter(arrival.router, consoleWatch);
    const found = leaks(surfaces, [sentinel]);
    // Five of the six, by name. The router location is excluded: a sentinel cannot be planted there without
    // navigating, and the real claim about it is made by the case above, where the arrival URL carried the
    // state words and the destination did not.
    expect(found).toEqual([
      'document/SENTINEL-NEEDLE',
      'window.name/SENTINEL-NEEDLE',
      'sessionStorage/SENTINEL-NEEDLE',
      'localStorage/SENTINEL-NEEDLE',
      'console/SENTINEL-NEEDLE',
    ]);
    expect(surfaces).toHaveLength(6);
    arrival.unmount();
  });

  it('CONTROL: the router-location surface is read, proven on a router parked at the callback URL', async () => {
    /**
     * THE FIRST DRAFT OF THIS CONTROL WAS WRONG, and the way it was wrong is worth keeping.
     *
     * It mounted the route without flushing, expecting to catch the router still at the callback URL before
     * the discard navigated. MEASURED: there is no such moment. `CallbackScreen`'s error branch is
     * synchronous, so the effect, the `discard()` and the navigation all complete inside the `act` that
     * renders - the router is already at `/app/sign-in` by the time `mountRoute` returns, and the control
     * failed with the needle absent.
     *
     * Which is good news about the production code and bad news about the instrument: the only way to show
     * this surface is live is to park a router at the callback URL and never render it. The sweep reads
     * `router.state.location`, and that is what is checked here; the case above is what shows the real
     * post-discard location is clean.
     */
    const consoleWatch = watchConsole();
    const fixture = ACCOUNT_STATES[3];
    const routes = await import('../app/routes');
    const parked = createMemoryRouter(routes.appRoutes, {
      basename: routes.APP_BASENAME,
      initialEntries: [`${routes.APP_BASENAME}/${routes.CALLBACK_PATH}${queryForState(fixture)}`],
    });
    const surfaces = surfacesAfter(parked, consoleWatch);
    // Through `leaks`, not by reading the surface text, so the control exercises the whole path the real case
    // uses - including the encoding normalisation, which is what this control's own failure discovered.
    expect(leaks(surfaces, needlesFor(fixture))).toEqual([
      `router location/${fixture.state}`,
      `router location/${fixture.description}`,
      `router location/${fixture.uri}`,
    ]);
    // Measured: the raw text carries `+` for each space, so the description is only found because `leaks`
    // searches the decoded form too.
    const raw = surfaces.find((s) => s.name === 'router location')?.text ?? '';
    expect(raw).toContain('suspended+by+an+administrator');
    expect(raw).not.toContain('suspended by an administrator');
    // And a different state's needle is absent, so the surface carries this arrival's query, not a generic one.
    expect(leaks(surfaces, [ACCOUNT_STATES[0].state])).toEqual([]);
  });

  it('the discard has already navigated by the time the render returns, so the location never holds the query', async () => {
    // The measurement behind the control above, kept as a case of its own because it is a fact about
    // production code rather than about an instrument: there is no window in which a mounted callback screen
    // still shows the state words in its location.
    seedPkceItems();
    const fixture = ACCOUNT_STATES[3];
    const arrival = await mountRoute(queryForState(fixture), tokenEndpointAnswers());
    expect(arrival.router.state.location.pathname).toBe('/app/sign-in');
    expect(arrival.router.state.location.search).toBe('');
    arrival.unmount();
  });

  it('CONTROL: every console channel is watched, not only the ones the flow happens to use', async () => {
    const consoleWatch = watchConsole();
    for (const channel of CONSOLE_CHANNELS) console[channel](`${channel}-sentinel`);
    expect(consoleWatch.channels).toHaveLength(6);
    for (const channel of CONSOLE_CHANNELS) {
      expect(consoleWatch.lines.join('\n')).toContain(`${channel}-sentinel`);
    }
  });

  it('CONTROL: a watcher installed after the arrival sees nothing written before it', async () => {
    seedPkceItems();
    const arrival = await arriveAt(queryForState(ACCOUNT_STATES[4]), tokenEndpointAnswers());
    const late = watchConsole();
    expect(late.lines).toEqual([]);
    arrival.unmount();
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC4 — identical visual treatment, pinned rather than proved
// ---------------------------------------------------------------------------------------------------------

describe('AC4: no failure screen carries a class or an inline style', () => {
  it('renders no class and no style attribute, for any of the five states', async () => {
    const seen: Array<{ state: string; attributes: string[] }> = [];
    for (const fixture of ACCOUNT_STATES) {
      seedPkceItems();
      const arrival = await arriveAt(queryForState(fixture), tokenEndpointAnswers());
      seen.push({ state: fixture.state, attributes: styleAttributesOf(arrival.container) });
      arrival.unmount();
      await clearArrivalSurfaces();
    }
    expect(seen).toHaveLength(5);
    // THE PIN FIRED, AND TASK 173 IS THE TASK IT WAS WRITTEN FOR. It read `toEqual([[], [], [], [], []])`,
    // with a comment saying so: "A PIN FOR TASK 173, not a claim that styling will never exist... When the
    // design system lands, this case is what fails if one failure is styled differently from another."
    // The design system landed, so the claim moves from "no styling" to R30's actual words - the visual
    // treatment SHALL be IDENTICAL across the five account states.
    //
    // IT ASSERTS SAMENESS **AND** NON-EMPTINESS, and the second half is not decoration. Five empty lists
    // satisfy sameness perfectly, which is how this case passed for 27 units; a version that checked only
    // sameness would therefore also pass if task 173 were reverted, or if a later refactor quietly stripped
    // every class. That is the T41 phase-11 F3 lesson - sameness is not correctness - arriving one task later
    // on a different instrument.
    const treatments = seen.map((row) => row.attributes);
    expect(new Set(treatments.map((row) => JSON.stringify(row))),
      'one account state is styled differently from another (R30)').toHaveLength(1);
    expect(treatments[0].length,
      'no element carries styling at all, so this case cannot tell the five states apart').toBeGreaterThan(0);
    // CLASSES ONLY. An inline `style` keyed on the cause would be the same leak by another route, and
    // `styleAttributesOf` reports both shapes, so the distinction has to be asserted rather than assumed.
    for (const attribute of treatments[0]) {
      expect(attribute, 'an inline style reached a failure screen').toMatch(/^[a-z0-9]+\[class=/);
    }
  });

  it('CONTROL: a class keyed on the cause is caught', () => {
    const container = document.createElement('div');
    container.innerHTML =
      '<main><div role="status" class="sign-in-error sign-in-error--locked">Your sign-in did not complete.</div></main>';
    expect(styleAttributesOf(container)).toEqual([
      'div[class=sign-in-error sign-in-error--locked]',
    ]);
  });

  it('CONTROL: an inline style is caught too, and the walker reaches the root itself', () => {
    const container = document.createElement('div');
    container.setAttribute('style', 'color: red');
    container.innerHTML = '<p style="display:none">hidden</p>';
    expect(styleAttributesOf(container)).toEqual(['div[style=color: red]', 'p[style=display:none]']);
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC5 — the SPA's own rendering is not timed by cause
// ---------------------------------------------------------------------------------------------------------

describe("AC5: the sentence is rendered with the clock at zero, whatever the cause", () => {
  /**
   * L4's timing clause, as `agents.md:44` reconciles it with R30: "The timing requirement applies to the
   * SPA's own rendering only."
   *
   * THIS IS THE ONE CLAIM NO EARLIER TASK IN THE PACKAGE MAKES. A `setTimeout` keyed on the error code,
   * placed before the notice, would pass every copy assertion in T16-T28, every redirect assertion, every
   * leak sweep - and still be an oracle, because a visitor who can time the difference can enumerate.
   *
   * The clock is installed before the arrival and NEVER ADVANCED. A `setTimeout(..., 0)` fails this, which is
   * the shape Kimi F3 named: zero-delay is still deferred on a fake clock, so "present at zero" is a real
   * assertion and not a tautology.
   */
  it('renders exactly the discarded sentence at clock zero, for every account state', async () => {
    const seen: Array<{ state: string; sentences: string[]; inBody: boolean }> = [];
    for (const fixture of ACCOUNT_STATES) {
      const arrival = await arriveUnderFakeClock(queryForState(fixture));
      seen.push({
        state: fixture.state,
        sentences: renderedSentences(),
        inBody: (document.body.textContent ?? '').includes(en.signIn.discarded),
      });
      arrival.unmount();
      await clearArrivalSurfaces();
    }
    expect(seen).toHaveLength(5);
    expect(seen).toEqual([
      { state: 'unknown', sentences: [en.signIn.discarded], inBody: true },
      { state: 'missing', sentences: [en.signIn.discarded], inBody: true },
      { state: 'locked', sentences: [en.signIn.discarded], inBody: true },
      { state: 'suspended', sentences: [en.signIn.discarded], inBody: true },
      { state: 'deleted', sentences: [en.signIn.discarded], inBody: true },
    ]);
  });

  it('lands on the sign-in start at clock zero as well, for every state, so no redirect is deferred', async () => {
    /**
     * Kimi I2. The first draft checked one state - `locked` - and that was a real gap rather than a
     * shortcut. `discard()` sets the notice and navigates in the same closure, so a `setTimeout(navigate, …)`
     * keyed on the error code would leave the SENTENCE at zero and defer only the NAVIGATION, which the case
     * above cannot see. And a delay applied uniformly to four of the five states would leave the real-clock
     * struct comparison identical too, because every outcome would be measured before the navigation landed.
     * Five states, both halves, at zero.
     */
    const landed: Array<{ state: string; pathname: string; historyAction: string }> = [];
    for (const fixture of ACCOUNT_STATES) {
      const arrival = await arriveUnderFakeClock(queryForState(fixture));
      landed.push({
        state: fixture.state,
        pathname: arrival.router.state.location.pathname,
        historyAction: arrival.router.state.historyAction,
      });
      arrival.unmount();
      await clearArrivalSurfaces();
    }
    expect(landed).toHaveLength(5);
    expect(landed).toEqual(
      ['unknown', 'missing', 'locked', 'suspended', 'deleted'].map((state) => ({
        state,
        pathname: '/app/sign-in',
        historyAction: 'REPLACE',
      })),
    );
  });
});

describe('AC5: the clock-at-zero assertion can fail', () => {
  it('CONTROL: a sentence rendered inside a timer is ABSENT at zero and present once the clock moves', async () => {
    // Kimi F9. A fake-timer assertion with nothing to advance passes whether or not the clock is fake, so
    // without this the AC5 case above would be decoration. Both halves are needed: absent at zero proves the
    // assertion bites, and present after 100ms proves the synthetic is not simply broken.
    vi.useFakeTimers();
    const container = document.createElement('div');
    document.body.append(container);
    const root = createRoot(container);
    await act(async () => {
      root.render(<Delayed />);
    });
    await act(async () => {});
    expect(renderedSentences()).toEqual([]);
    expect(document.body.textContent).not.toContain(en.signIn.discarded);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(100);
    });
    expect(renderedSentences()).toEqual([en.signIn.discarded]);
    act(() => root.unmount());
    container.remove();
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC6 — no unpermitted timer anywhere in production
// ---------------------------------------------------------------------------------------------------------

describe('AC6: no production module holds an unexplained timer', () => {
  it('finds no timer outside the three permitted modules', async () => {
    const targets = await productionSources();
    expect(targets.length).toBeGreaterThanOrEqual(20);
    const hits = hitsOf(targets, timerCalls());
    expect(unexplained(hits, PERMITTED_TIMERS)).toEqual([]);
  });

  it('the permitted rows describe exactly the timers that exist, with no slack', async () => {
    // The allowlist is an upper bound AND a lower bound. A row for a timer that has been deleted would leave
    // budget behind, and a later addition in that file would then be absorbed silently - which is how an
    // allowlist stops being evidence.
    const targets = await productionSources();
    const hits = hitsOf(targets, timerCalls());
    const counted = new Map<string, number>();
    for (const hit of hits) {
      const key = `${hit.file}:${hit.hit}`;
      counted.set(key, (counted.get(key) ?? 0) + 1);
    }
    expect([...counted.entries()].sort()).toEqual(
      PERMITTED_TIMERS.map((row) => [`${row.file}:${row.hit}`, row.count] as [string, number]).sort(),
    );
    expect(PERMITTED_TIMERS.filter((row) => row.why.length === 0)).toEqual([]);
  });

  it('CONTROL: a timer in an auth module is reported', () => {
    // Kimi F5's subject: a file-scoped scan over an "identity path" list would see this only if the file
    // happened to be on the list. The whole-tree scan sees it wherever it is put.
    const offenders = [
      synthetic('auth/SignInScreen.tsx', 'setTimeout(() => setStatus("failed"), 2000);'),
      synthetic('auth/lockoutCountdown.ts', 'export const tick = () => setInterval(count, 1000);'),
      synthetic('auth/helpers/pause.ts', 'export const pause = () => requestAnimationFrame(next);'),
    ];
    const unflagged = offenders.filter(
      (target) => unexplained(hitsOf([target], timerCalls()), PERMITTED_TIMERS).length === 0,
    );
    expect(unflagged.map((target) => target.path)).toEqual([]);
  });

  it('CONTROL: the accounting admits a permitted count and no more', () => {
    // The real table is non-empty, unlike T28's, but the budget arithmetic still needs driving from both
    // sides: a file allowed two calls that makes three must report exactly one.
    const target = synthetic(
      'api/sessionBoundary.ts',
      'setTimeout(a, 1); setTimeout(b, 2); setTimeout(c, 3); queueMicrotask(d);',
    );
    const hits = hitsOf([target], timerCalls());
    expect(hits).toHaveLength(4);
    expect(unexplained(hits, PERMITTED_TIMERS)).toEqual([
      { file: 'api/sessionBoundary.ts', hit: 'settimeout' },
    ]);
    // And a row for another file explains nothing here.
    expect(
      unexplained(hits, [{ file: 'api/crossTabRenewal.ts', hit: 'settimeout', count: 9 }]),
    ).toHaveLength(4);
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC7 — no production module names an account state
// ---------------------------------------------------------------------------------------------------------

describe('AC7: no production module names an account state', () => {
  it('finds no globally-forbidden state word in the comment-stripped tree', async () => {
    const targets = await productionSources();
    expect(targets.length).toBeGreaterThanOrEqual(20);
    expect(unpermittedState(hitsOf(targets, new RegExp(ALWAYS_FORBIDDEN_STATE, 'gi')))).toEqual([]);
  });

  it('CONTROL: the scan flags each forbidden state word on its own', () => {
    const phrasings = [
      'This account is locked',
      'lockout expires in 15 minutes',
      'the account was suspended',
      'this account has been deleted',
      'the account is deactivated',
      'an inactive account',
      'no such account',
      'this account is banned',
      'the account was terminated',
      'an archived account',
      'a dormant account',
      'that account is nonexistent',
      'an unrecognized account',
      'an unrecognised account',
    ];
    const missed = phrasings.filter((phrasing) => !namesForbiddenState(phrasing, false));
    expect(missed).toEqual([]);
  });

  it('CONTROL: the scan stays SILENT on the words the package legitimately uses', async () => {
    // Every one of these was MEASURED to have legitimate hits, and a scan that fired on any of them would be
    // deleted by the next person to watch it fail rather than fixed. The counts are in this file's header.
    const legitimate = [
      synthetic('api/sessionBoundary.ts', 'onRenewalFailed: (reason: unknown) => void'),
      synthetic('auth/idTokenNonce.ts', "if (!('nonce' in payload)) return { reason: 'claim-missing' };"),
      synthetic('auth/signInNotice.ts', 'let pending: SignInNotice | null = null;'),
      synthetic('api/crossTabRenewal.ts', 'if (channelClosed) return; channel.close();'),
      synthetic('auth/SignInScreen.tsx', "aria-disabled={status === 'starting'}"),
      synthetic('app/mountShell.tsx', "throw new Error('mountShell: root element #root not found');"),
    ];
    const wronglyFlagged = legitimate.filter(
      (target) => hitsOf([target], new RegExp(ALWAYS_FORBIDDEN_STATE, 'gi')).length > 0,
    );
    expect(wronglyFlagged.map((target) => target.path)).toEqual([]);
    // And the real tree agrees: the four excluded words are PRESENT in production and the scan is silent.
    const targets = await productionSources();
    expect(hitsOf(targets, /unknown|missing|pending|closed/gi).length).toBeGreaterThan(10);
    expect(unpermittedState(hitsOf(targets, new RegExp(ALWAYS_FORBIDDEN_STATE, 'gi')))).toEqual([]);
  });

  it('CONTROL: the state-source allowlist is an upper AND lower bound, and its row carries a bound', async () => {
    /**
     * Added by T31 with the allowlist, because an exception without a dirty-proof is how a scan rots. Three
     * claims:
     *
     *   1. With the row REMOVED, the scan reports the file it protects - so the row is load-bearing.
     *   2. The row is not stale: the file still names every word the row claims.
     *   3. The row's BOUND holds - `readAccount.ts` renders nothing and translates nothing, so naming a
     *      state there cannot become showing one. That is the whole reason the exception is safe, and it is
     *      the part a future edit would break silently.
     */
    const targets = await productionSources();
    const hits = hitsOf(targets, new RegExp(ALWAYS_FORBIDDEN_STATE, 'gi'));

    expect(unpermittedState(hits, []).map((hit) => hit.file)).toEqual([
      'profile/accountStatus.ts',
      'profile/accountStatus.ts',
      'profile/accountStatus.ts',
    ]);

    for (const row of PERMITTED_STATE_SOURCE) {
      const code = targets.find((target) => target.path === row.file);
      expect(code, row.file).toBeDefined();
      for (const word of row.words) {
        expect(new RegExp(word, 'i').test((code as ScanTarget).code), `${row.file} still names ${word}`)
          .toBe(true);
      }
      // 3a. A `.ts` file cannot contain JSX at all under this toolchain, which is structurally stronger than
      //     any regex for the rendering carrier. An earlier draft tested `/<[A-Za-z]/` and failed on
      //     `Partial<Record<...>>`: a generic parameter is not a JSX element.
      expect(row.file.endsWith('.ts'), `${row.file} is not a component`).toBe(true);

      // 3b. And no carrier of any kind - Kimi I2 for the property, Kimi A2 for this form of it. A state word
      //     reaches a visitor by being rendered, thrown, logged or translated, and the licensed file does
      //     none of those, whatever shape the list inside it is written in.
      expect(carriersIn((code as ScanTarget).code), `${row.file} can carry a state word out`).toEqual([]);
    }
  });

  it('CONTROL: every carrier of a state word is detected, one synthetic file each', () => {
    /**
     * The dirty-proof for the place bound, and the case that makes it non-vacuous. Each shape below keeps the
     * file a `.ts` with a perfectly ordinary status list in it - which is exactly why the earlier structural
     * pair, and then the earlier array-literal regex, could not see them.
     */
    const clean = "export const ACCOUNT_STATUSES = ['ACTIVE', 'SUSPENDED'] as const;";
    expect(carriersIn(clean)).toEqual([]);

    const dirty: ReadonlyArray<[string, string]> = [
      ['thrown', `${clean}\nthrow new ReadAccountError('suspended');`],
      ['logged', `${clean}\nconsole.warn('account suspended');`],
      ['translated', `${clean}\nconst copy = t('account.suspended');`],
    ];
    for (const [carrier, code] of dirty) {
      expect(carriersIn(code), carrier).toContain(carrier);
    }

    // And the fourth carrier, which is bounded by the extension rather than by a pattern: a licensed row
    // naming a component would fail the assertion in the case above.
    expect('profile/AccountStatus.tsx'.endsWith('.ts')).toBe(false);
    expect('profile/accountStatus.ts'.endsWith('.ts')).toBe(true);
  });

  it('CONTROL: a state word inside a URL STRING survives stripping, which it did not before', () => {
    /**
     * Kimi I8's regression guard. The old stripper deleted from the first `//` to end of line regardless of
     * context, so a help URL in a string literal lost everything after `https:` - and with it the state word
     * the scan exists to find. This case is the one that would fail if anyone restores the two-regex version.
     */
    const withUrl = 'export const HELP = "https://id.example/help/locked-account";';
    expect(strip(withUrl)).toContain('locked-account');
    expect(hitsOf([synthetic('auth/help.ts', withUrl)], new RegExp(ALWAYS_FORBIDDEN_STATE, 'gi')))
      .toEqual([{ file: 'auth/help.ts', hit: 'locked' }]);
    // Template literals and single quotes too, and an escaped quote must not end the literal early.
    expect(strip('const a = `see https://x/suspended-account`;')).toContain('suspended-account');
    expect(strip("const b = 'it\\'s https://x/deleted-account';")).toContain('deleted-account');
  });

  it('CONTROL: comments are still removed, including ones that contain a quote', () => {
    expect(strip('const a = 1; // this account was deleted\nconst b = 2;')).not.toContain('deleted');
    expect(strip('/* suspended */ const c = 3;')).not.toContain('suspended');
    // An apostrophe inside a comment must not open a string and swallow the rest of the file.
    expect(strip("// don't say locked here\nconst d = 'kept';")).toContain("'kept'");
    expect(strip("// don't say locked here\nconst d = 'kept';")).not.toContain('locked');
  });

  it('CONTROL: comment stripping is what makes this scan survivable at all', async () => {
    const srcRoot = sourceRoot();
    const raw = await readFile(join(srcRoot, 'auth', 'CallbackScreen.tsx'), 'utf8');
    // Unstripped, the screen's own comment explains uniformity using the words a state scan looks for.
    expect(/deleted/i.test(raw)).toBe(true);
    expect(/deleted/i.test(strip(raw))).toBe(false);
  });

  it('CONTROL: a scan over an empty tree is refused rather than reported clean', () => {
    // `hitsOf([], ...)` returns `[]`, which satisfies the claim above while proving nothing. The guard is the
    // real one, called here, not a copy of its three lines.
    expect(() => refuseEmpty([], NO_SOURCES)).toThrow(NO_SOURCES);
    expect(hitsOf([], new RegExp(ALWAYS_FORBIDDEN_STATE, 'gi'))).toEqual([]);
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC8 — no catalogue string names an account state, and the split holds both ways
// ---------------------------------------------------------------------------------------------------------

describe('AC8: no user-facing string names an account state', () => {
  it('scans every string in the catalogue and finds none', () => {
    const strings = catalogueStrings();
    expect(strings.length).toBeGreaterThanOrEqual(10);
    expect(forbiddenStateEntries(strings)).toEqual([]);
  });

  it('CONTROL: a state sentence is flagged by its VALUE', () => {
    expect(
      forbiddenStateEntries([
        { key: 'signIn.discarded', value: 'This account has been suspended. Contact support.' },
      ]),
    ).toEqual(['signIn.discarded (value)']);
  });

  it('CONTROL: a state key is flagged by its NAME even when its value is innocent', () => {
    expect(
      forbiddenStateEntries([{ key: 'signIn.accountLocked', value: 'Your sign-in did not complete.' }]),
    ).toEqual(['signIn.accountLocked (key)']);
  });

  it('CONTROL: a key that names the CONCEPT is flagged even when its value is uniform', () => {
    // Kimi I5. A slot waiting to be filled is worth catching before it is filled.
    expect(
      forbiddenStateEntries([
        { key: 'signIn.accountState', value: 'Your sign-in did not complete. Try again.' },
        { key: 'callback.accountStatus', value: 'Checking your sign-in…' },
        { key: 'signIn.lockoutReason', value: 'Try again.' },
      ]),
    ).toEqual([
      'signIn.accountState (key)',
      'callback.accountStatus (key)',
      'signIn.lockoutReason (key)',
    ]);
    // And a bare `status` key elsewhere is NOT flagged - `invoice.status` is commissioned, and a pattern
    // wide enough to catch it would block every status key in the package.
    expect(
      forbiddenStateEntries([
        { key: 'invoice.status', value: 'Open' },
        { key: 'payment.status.label', value: 'Awaiting confirmation' },
      ]),
    ).toEqual([]);
  });

  it('CONTROL: the sign-in-scoped vocabulary is forbidden on a sign-in key and PERMITTED elsewhere', () => {
    // Kimi F7's accepted-in-part half, held from the OVER-BROAD side. Every row below is copy the spec
    // commissions, and a global pattern would block it while reporting an R30 breach.
    expect(
      forbiddenStateEntries([
        { key: 'signIn.failed', value: 'That link has expired' },
        { key: 'callback.working', value: 'This session was revoked' },
      ]),
    ).toEqual(['signIn.failed (value)', 'callback.working (value)']);
    expect(
      forbiddenStateEntries([
        { key: 'invoice.status.expired', value: 'This invoice has expired' },
        { key: 'sessions.action', value: 'Revoke this session' },
        { key: 'sessions.revokeAll', value: 'Revoke every other session' },
        { key: 'form.amount', value: 'Enter a valid amount' },
        { key: 'payment.held', value: 'This payment is blocked pending a compliance review' },
        { key: 'admin.roles', value: 'Access denied for this role' },
        { key: 'rateLimit.notice', value: 'You have been rate limited. Try again shortly.' },
        { key: 'payment.held', value: 'This payment was flagged for review' },
        { key: 'account.password', value: 'This password has appeared in a known breach' },
        { key: 'plan.quota', value: 'Your plan has unlimited transfers' },
      ]),
    ).toEqual([]);
  });

  it('CONTROL: the globally-forbidden vocabulary is forbidden EVERYWHERE, not only on a sign-in key', () => {
    // And from the TOO-NARROW side. `suspended` has no commissioned use anywhere, so an admin screen may not
    // carry it either - L4's carve-out is `adminGetAccount` the API (R27, D-033), not copy in the catalogue.
    expect(
      forbiddenStateEntries([
        { key: 'admin.account.status', value: 'This account is suspended' },
        { key: 'home.notice', value: 'Your account was deleted' },
      ]),
    ).toEqual(['admin.account.status (value)', 'home.notice (value)']);
  });

  it('CONTROL: a permitted-copy row admits exactly the key it names', () => {
    const entry = { key: 'admin.account.status', value: 'This account is suspended' };
    expect(forbiddenStateEntries([entry])).toEqual(['admin.account.status (value)']);
    const permitted = new Set(['admin.account.status']);
    const withRow = [entry].filter((row) => !permitted.has(row.key));
    expect(withRow).toEqual([]);
    // A row for a DIFFERENT key explains nothing.
    const otherRow = new Set(['admin.account.other']);
    expect([entry].filter((row) => !otherRow.has(row.key))).toEqual([entry]);
    expect(PERMITTED_STATE_COPY).toEqual([]);
  });

  it('CONTROL: an array-valued entry is flattened, so a list of bullets cannot hide a state word', () => {
    const strings = catalogueStrings({ help: ['This account is locked', 'Innocent sentence'] });
    expect(strings).toEqual([
      { key: 'help.0', value: 'This account is locked' },
      { key: 'help.1', value: 'Innocent sentence' },
    ]);
    expect(forbiddenStateEntries(strings)).toEqual(['help.0 (value)']);
  });

  it('CONTROL: an empty catalogue is refused rather than reported clean', () => {
    expect(() => catalogueStrings({})).toThrow(NO_STRINGS);
    expect(() => refuseEmpty([], NO_STRINGS)).toThrow(NO_STRINGS);
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC9 / AC10 — the gating scan and the key-set pin, inherited from T28
// ---------------------------------------------------------------------------------------------------------

describe('AC9: nothing gates a failed sign-in on an account state or a role', () => {
  it('finds no account-state-shaped or role-shaped comparison in production source', async () => {
    const targets = await productionSources();
    expect(hitsOf(targets, accountStateGates())).toEqual([]);
  });

  it('CONTROL: the scan flags a gate on each of the five states', () => {
    const offenders = [
      synthetic('auth/A.tsx', "if (accountState === 'LOCKED') showCountdown();"),
      synthetic('auth/B.tsx', 'if (account.accountLocked) discard();'),
      synthetic('auth/C.tsx', 'if (user.isSuspended) return <p>Suspended.</p>;'),
      synthetic('auth/D.tsx', 'if (account.isDeleted) navigate(GONE);'),
      synthetic('auth/E.tsx', "if (account.role === 'MERCHANT') discard();"),
      synthetic('auth/F.tsx', "if (roles.includes('ADMIN')) return null;"),
    ];
    const unflagged = offenders.filter((target) => hitsOf([target], accountStateGates()).length === 0);
    expect(unflagged.map((target) => target.path)).toEqual([]);
  });

  it('CONTROL: the scan flags the gate shapes Kimi I1 named', () => {
    const offenders = [
      synthetic('auth/A.tsx', "if (account.state === 'LOCKED') showCountdown();"),
      synthetic('auth/B.tsx', "if (user.state !== 'ACTIVE') return null;"),
      synthetic('auth/C.tsx', "if (account.status === 'SUSPENDED') discard();"),
      synthetic('auth/D.tsx', 'if (user.isLocked) return null;'),
      synthetic('auth/E.tsx', 'if (account.locked) discard();'),
      synthetic('auth/F.tsx', "switch (accountState) { case 'LOCKED': break; }"),
      synthetic('auth/G.tsx', 'if (accountState === AccountState.Locked) return null;'),
    ];
    const unflagged = offenders.filter((target) => hitsOf([target], accountStateGates()).length === 0);
    expect(unflagged.map((target) => target.path)).toEqual([]);
  });

  /**
   * Kimi's phase 11, and it is a hole that KIMI'S OWN I1 FIX OPENED. Keying the state and status alternatives
   * on the receiver was the right answer to three legitimate hits, but it left the receiver as the thing the
   * scan needs, and a destructuring assignment takes it away.
   *
   * The literal alternative is the answer because it keys on what the gate cannot be written without. A
   * developer may rename the variable, alias the property, destructure it or pass it through three functions;
   * the comparison still has to name the state.
   */
  it('CONTROL: a gate survives destructuring, and is still flagged by the state LITERAL', () => {
    const offenders = [
      synthetic('auth/H.tsx', "const { state } = account;\nif (state === 'LOCKED') showCountdown();"),
      synthetic('auth/I.tsx', "const { status: s } = account;\nif (s === 'SUSPENDED') return null;"),
      synthetic('auth/J.tsx', "const flag = readIt();\nif (flag !== 'DELETED') return null;"),
      synthetic('auth/K.tsx', "switch (whatever) { case 'SUSPENDED': return null; }"),
    ];
    const unflagged = offenders.filter((target) => hitsOf([target], accountStateGates()).length === 0);
    expect(unflagged.map((target) => target.path)).toEqual([]);

    // And the alternative is NOT a bare `state ===`, which would fire on the OIDC state parameter. These two
    // are the shapes CallbackScreen actually writes, and the scan must stay silent on both.
    const permitted = [
      synthetic('auth/L.tsx', "const { state } = params;\nif (state !== stored) discard();"),
      synthetic('auth/M.tsx', "if (returned.state === sessionStorage.getItem(KEY)) exchange();"),
    ];
    expect(hitsOf(permitted, accountStateGates())).toEqual([]);
  });

  it('CONTROL: the state and status alternatives key on the RECEIVER, not on the property alone', async () => {
    /**
     * Kimi I1 recommended a bare `\.state\s*[=!]==` and `\.status\s*[=!]==`. MEASURED, both fire on code
     * the spec commissions: `registerServiceWorker.ts` reads Workbox's `installing.state === 'installed'`,
     * and `rateLimitNotice.ts` and `sessionBoundary.ts` both compare `response.status`, which R23 and R21
     * require. A scan that failed on those would be deleted rather than fixed - T26's lesson, and T28's.
     */
    const legitimate = [
      synthetic('pwa/registerServiceWorker.ts', "if (installing.state === 'installed') prompt();"),
      synthetic('api/rateLimitNotice.ts', 'if (response.status === 429) setShown(true);'),
      synthetic('api/sessionBoundary.ts', 'if (response.status !== 401) return response;'),
    ];
    const wronglyFlagged = legitimate.filter((target) => hitsOf([target], accountStateGates()).length > 0);
    expect(wronglyFlagged.map((target) => target.path)).toEqual([]);
    // And the real tree agrees: those three comparisons are PRESENT and the scan is silent on all of them.
    const targets = await productionSources();
    expect(hitsOf(targets, /(?:\.state|\.status)\s*[=!]==/g).length).toBeGreaterThanOrEqual(3);
    expect(hitsOf(targets, accountStateGates())).toEqual([]);
  });

  it('CONTROL: the role variants Kimi I6 listed were ALREADY caught, and the suggested fix was refused', () => {
    /**
     * Kimi I6, rejected on a measurement. Every variant it named is caught today, because the role
     * alternatives key on the LITERAL being compared rather than on the receiver or the method:
     * `=== 'MERCHANT'` matches inside an arrow body as happily as at the top of an `if`, and
     * `.includes('MERCHANT')` does not care what the array is called.
     *
     * The suggested addition - a bare `\.some\s*\(|\.find\s*\(` - was refused for the reason this file
     * refuses every over-broad pattern: those are ordinary array methods, and forbidding them would fail code
     * the spec commissions while reporting an enumeration breach.
     */
    const alreadyCaught = [
      synthetic('auth/H.tsx', "if (roles.some((r) => r === 'MERCHANT')) gate();"),
      synthetic('auth/I.tsx', "if (roles.find((r) => r === 'ADMIN')) gate();"),
      synthetic('auth/J.tsx', "if (account.roleName === 'MERCHANT') gate();"),
      synthetic('auth/K.tsx', "if (user.permissions.includes('MERCHANT')) gate();"),
      synthetic('auth/L.tsx', "if (account.type === 'MERCHANT') gate();"),
    ];
    const missed = alreadyCaught.filter((target) => hitsOf([target], accountStateGates()).length === 0);
    expect(missed.map((target) => target.path)).toEqual([]);
    // The refused half, held as a case so it cannot be re-accepted quietly: ordinary array use is silent.
    const ordinary = [
      synthetic('app/routes.tsx', 'const found = PUBLIC_ROUTE_PATHS.some((path) => path === target);'),
      synthetic('api/client.ts', 'const row = rows.find((entry) => entry.id === id);'),
    ];
    expect(ordinary.filter((target) => hitsOf([target], accountStateGates()).length > 0)).toEqual([]);
  });

  it("CONTROL: the scan stays SILENT on CallbackScreen's own OAuth state comparisons", async () => {
    // T28 measured a bare `state ===` to have three legitimate hits: the two comparisons R8 requires, and
    // Workbox's `installing.state === 'installed'`. The pattern keys on the name of the thing being asked
    // about for exactly that reason, and this case is what holds it there.
    const targets = await productionSources();
    const bareState = targets.flatMap((target) =>
      [...target.code.matchAll(/state\s*===/g)].map(() => target.path),
    );
    expect(bareState.length).toBeGreaterThanOrEqual(3);
    expect(bareState).toContain('auth/CallbackScreen.tsx');
    expect(hitsOf(targets, accountStateGates())).toEqual([]);
  });
});

describe('AC10: the failure sentences, and exactly the signIn keys that exist', () => {
  it('pins the signIn key set to exactly the seven that exist, so an eighth fails', () => {
    expect(Object.keys(en.signIn).sort()).toEqual([...SIGN_IN_KEYS]);
    expect(SIGN_IN_KEYS).toHaveLength(7);
  });

  it('keeps the three failure sentences distinct, and none of them names a state', () => {
    // `sessionEnded` joined these in task 33 and is held to the same bar. Distinctness matters because each
    // reports a different EVENT in one session's life; what L4 forbids is a sentence that reports a different
    // ACCOUNT, and `namesForbiddenState` is what checks that of all three.
    const sentences = [en.signIn.failed, en.signIn.discarded, en.signIn.sessionEnded];
    expect(new Set(sentences).size).toBe(sentences.length);
    for (const sentence of sentences) {
      expect(namesForbiddenState(sentence)).toBe(false);
    }
  });

  it('CONTROL: the key-set pin fails when one more key arrives, whatever it is called', () => {
    const withSeventh = { ...en.signIn, accountSuspended: 'This account has been suspended.' };
    expect(Object.keys(withSeventh).sort()).not.toEqual([...SIGN_IN_KEYS]);
    expect(forbiddenStateEntries([{ key: 'signIn.accountSuspended', value: withSeventh.accountSuspended }]))
      .toEqual(['signIn.accountSuspended (value)']);
  });
});

// ---------------------------------------------------------------------------------------------------------
// AC11 — every instrument has been shown to report dirty
// ---------------------------------------------------------------------------------------------------------

describe('AC11: every instrument has been shown to report dirty', () => {
  /**
   * A SIGNPOST, NOT EVIDENCE, and said so because T27's Kimi review found a maintainer could read its
   * equivalent as proof. This table cannot fail for the right reason - it fails only if a row is deleted
   * without updating the count. The real proof is the CONTROL cases named in each row.
   *
   * The harness that scored this file:
   *
   *     python3 -I .ai/prompts/frontend/T29/artifacts/07-mutation-harness.py
   *
   * It reverts with `git checkout -- src/`, so commit before running it.
   */
  const INSTRUMENTS = [
    { instrument: 'fixture distinctness', dirtiedBy: 'five descriptions and five URIs, pairwise (AC2)' },
    { instrument: 'outcome struct', dirtiedBy: 'a successful callback (AC2)' },
    { instrument: 'leak sweep', dirtiedBy: 'a sentinel on five surfaces, a parked router for the sixth (AC3)' },
    { instrument: 'encoding normalisation', dirtiedBy: 'a `+`-encoded description on the router surface (AC3)' },
    { instrument: 'console watcher', dirtiedBy: 'a sentinel on each of six channels (AC3)' },
    { instrument: 'style pin', dirtiedBy: 'a cause-keyed class and an inline style (AC4)' },
    { instrument: 'clock-at-zero', dirtiedBy: 'a sentence rendered inside setTimeout(100) (AC5)' },
    { instrument: 'timer scan', dirtiedBy: 'three timers in auth modules, and a fourth call in a permitted file (AC6)' },
    { instrument: 'state source scan', dirtiedBy: 'fourteen state phrasings, one per forbidden word (AC7)' },
    { instrument: 'comment stripper', dirtiedBy: 'a state word inside a URL string literal (AC7)' },
    { instrument: 'catalogue scan', dirtiedBy: 'a value, a key name, and an array element (AC8)' },
    { instrument: 'gating scan', dirtiedBy: 'six gates, one per state and two on roles (AC9)' },
    { instrument: 'destructured gate', dirtiedBy: 'four gates with no receiver left to key on (AC9)' },
    { instrument: 'live-region shape', dirtiedBy: 'the shell control, which rejects an all-`-/-` field (AC2)' },
    { instrument: 'focus', dirtiedBy: 'the shell control, which rejects `none` (AC2)' },
    { instrument: 'DOM skeleton', dirtiedBy: 'the shell control, which rejects a single line with no ARIA (AC2)' },
    { instrument: 'key-set pin', dirtiedBy: 'a seventh signIn key (AC10)' },
  ];

  it('names one dirtying violation per instrument', () => {
    expect(INSTRUMENTS).toHaveLength(17);
    expect(INSTRUMENTS.filter((row) => row.dirtiedBy.length === 0)).toEqual([]);
  });

  it('refuses a scan that reads nothing, through the one guard every walker calls', async () => {
    expect(() => refuseEmpty([], NO_SOURCES)).toThrow(NO_SOURCES);
    expect(() => refuseEmpty([], NO_STRINGS)).toThrow(NO_STRINGS);
    expect((await productionSources()).length).toBeGreaterThanOrEqual(20);
    expect(catalogueStrings().length).toBeGreaterThanOrEqual(10);
  });
});
