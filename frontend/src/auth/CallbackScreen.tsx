import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useSearchParams } from 'react-router';
import { SIGN_IN_PATH } from '../app/routes';
import { AUTHORIZE_STORAGE_KEYS } from './authorizeRequest';
import { exchangeAuthorizationCode } from './codeExchange';
import { idTokenNonceMatchesStoredValue } from './idTokenNonce';
import { setSignInNotice } from './signInNotice';

/**
 * Where the browser returns from the Spring Authorization Server flow.
 *
 * Both halves of R8 live here. A callback that arrives with no usable `state` is discarded (T17), and so is one
 * whose `state` does not match the value stored when the request was built (T18). Neither is exchanged, and both
 * return the visitor to the sign-in start with the same neutral copy - identical by requirement, since L4
 * forbids copy that would tell a visitor which check failed.
 *
 * R7's first clause lives here too. A callback that survives both halves of R8 is exchanged, and the ID token
 * that comes back is only accepted if its `nonce` claim is the value this tab stored (T19). That is a different
 * protection from `state`: a token minted for some other login, substituted into this callback, carries a
 * perfectly good `state` and is caught by nothing but the nonce.
 *
 * This screen reads two stored items, and neither before the query has been found usable: the `state` here, and
 * the `nonce` inside `idTokenNonce.ts`. Both reads are guarded - if storage is denied the value is treated as
 * absent, which discards safely and preserves L4. It writes nothing and deletes nothing: the `code_verifier` is
 * task 20's, and deleting all three is task 23's. A discarded attempt leaves a verifier whose challenge was
 * never used, which T15's next request overwrites.
 *
 * Every failure on this screen ends the same way, by requirement. A missing `state`, a mismatched `state`, a
 * refused exchange and a bad `nonce` are four different faults and one single outcome: the same neutral notice,
 * the same replaced history entry, the same sign-in screen. They share the one `discard` closure below rather
 * than four copies of it, so they cannot drift apart (L4).
 */
export function CallbackScreen() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  // Set before anything else runs. StrictMode invokes effects twice in development, and this effect navigates and
  // sets module state - both things that must happen once.
  const handled = useRef(false);

  // One mount handles one callback, which is why the dependency list is empty. The browser arrives here by a
  // fresh navigation every time, so there is no second set of parameters to react to; a list naming `params`
  // would claim otherwise while the latch above silently refused to act on it.
  //
  // The latch is set BEFORE the exchange is started, which matters more now that starting it means network I/O:
  // it closes the whole window the request is in flight for, so nothing that re-runs this effect in that window
  // can redeem the code a second time. A replayed code is refused anyway (measured: 400 invalid_grant), but the
  // refusal would arrive as a discard on a sign-in that had already succeeded.
  useEffect(() => {
    if (handled.current) return;
    handled.current = true;

    // Trimmed before the emptiness test. A value of nothing but whitespace is what a stripped or truncated query
    // produces, exactly as an empty one is, and it carries no more proof that the callback belongs to a request
    // this tab made. The trimmed values travel onward, so the exchange never receives padding the authorization
    // server did not send.
    const state = params.get('state')?.trim() ?? null;
    const code = params.get('code')?.trim() ?? null;
    // An authorization error response carries `error` in PLACE of `code` (RFC 6749 §4.1.2.1). A URL carrying
    // both is malformed - the server sends one or the other - so it is an injected or mangled link rather than
    // anything this flow produced, and the safe reading is that no usable grant arrived. Nothing in the package
    // surfaces the error itself: L4 forbids copy that distinguishes one failure from another, and task 28 maps
    // even a SAS refusal onto the generic wrong-password copy. The discard is the whole outcome.
    const error = params.get('error')?.trim() ?? null;

    /** R8's consequent, and the only outcome T17 produces. Neutral by requirement: it names no cause (L4). */
    const discard = () => {
      setSignInNotice('discarded');
      // The leading slash matters: measured, a relative target resolves against the current route and lands on
      // the catch-all, silently. `replace` so a discarded callback is not reachable with the back button.
      navigate(`/${SIGN_IN_PATH}`, { replace: true });
    };

    if (error !== null && error !== '') {
      discard();
      return;
    }

    // R8's antecedent, in T17's half. A missing `code` is discarded too - beyond R8's literal words, which speak
    // only of `state`, but the alternative is handing the exchange a grant it cannot use and calling that
    // someone else's problem.
    if (state === null || state === '' || code === null || code === '') {
      discard();
      return;
    }

    // R8's other half. The stored state is the only evidence that this callback answers a request THIS tab
    // made: without the comparison, anyone able to open /app/callback?code=...&state=... in the visitor's
    // browser could have the exchange run on a code of their choosing.
    // Reading is wrapped because TOUCHING sessionStorage can throw, not merely return null: a context with site
    // data blocked raises instead of answering. There is no error boundary in this application, so an uncaught
    // throw here would blank the page at the end of a sign-in, after the visitor has already authenticated. A
    // denial is treated as nothing stored, which is also the honest security reading - without the stored value
    // there is no evidence the callback belongs to a request this tab made - and is L4-neutral, since the
    // visitor gets the same sentence as every other discard.
    let stored: string | null = null;
    try {
      stored = globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state);
    } catch {
      stored = null;
    }
    // Nothing stored is tested on its own, so no reader has to work out whether `null !== state` happened to be
    // true for the right reason. The stored value is never trimmed or case-folded - T15 wrote it, so padding
    // there would mean something is wrong - and anything that widens equality widens the injection surface.
    //
    // The first two tests are REDUNDANT TODAY and deliberately kept: neither `null` nor `''` can equal `state`,
    // because the guard above has already returned for an empty one, so no test can tell them apart from the
    // inequality alone. What they defend is that coupling. Were the empty-`state` guard above ever relaxed,
    // `'' !== ''` is false and a callback carrying no state would match an empty stored value and be exchanged.
    // Do not fold these into the inequality to tidy it.
    if (stored === null || stored === '' || stored !== state) {
      discard();
      return;
    }

    // There is real network I/O behind this call now (task 20), and every way it can fail - offline, a 400 from
    // the token endpoint, a body that will not parse, a response with no usable tokens - arrives here as one
    // rejection carrying nothing. Nothing more specific happens to it: the discard IS the whole outcome, by L4,
    // because a visitor who can tell a refused code from a dropped connection can enumerate. A dropped rejection
    // would be the one real alternative, and it would leave the visitor on this holding screen for good.
    exchangeAuthorizationCode({ code, state }).then((response) => {
      // The nonce decides whether this token belongs to the request this tab made (R7, L1). The reason the check
      // failed is deliberately dropped here: it exists so the tests can tell the five rejection modes apart, and
      // L4 forbids any of it reaching the visitor.
      //
      // This runs INSIDE the exchange's fulfillment, after the state check admitted the callback, and it may not
      // be moved earlier. R8 requires a bad state to discard before any nonce work: hoisting this would read the
      // stored nonce for callbacks that were never this tab's to begin with.
      if (!idTokenNonceMatchesStoredValue(response.idToken).ok) discard();
      // Two arguments rather than a trailing .catch, so that a failure INSIDE this handler is not then handed to
      // `discard` a second time. Both calls would navigate to the same place, so nothing visible would differ -
      // but a screen whose whole design is a single-invocation latch should not carry a second way to run its
      // one outcome twice.
    }, discard);
  }, []);

  return (
    <main>
      <h1>{t('callback.heading')}</h1>
      <div role="status" aria-live="polite">{t('callback.working')}</div>
    </main>
  );
}
