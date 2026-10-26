import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useSearchParams } from 'react-router';
import { SIGN_IN_PATH } from '../app/routes';
import { AUTHORIZE_STORAGE_KEYS } from './authorizeRequest';
import { exchangeAuthorizationCode } from './codeExchange';
import { setSignInNotice } from './signInNotice';

/**
 * Where the browser returns from the Spring Authorization Server flow.
 *
 * Both halves of R8 live here. A callback that arrives with no usable `state` is discarded (T17), and so is one
 * whose `state` does not match the value stored when the request was built (T18). Neither is exchanged, and both
 * return the visitor to the sign-in start with the same neutral copy - identical by requirement, since L4
 * forbids copy that would tell a visitor which check failed.
 *
 * This screen reads exactly one stored item, the `state`, and only once the query has been found usable. It
 * writes nothing and deletes nothing: the `nonce` is task 19's, the `code_verifier` is task 20's, and deleting
 * all three is task 23's. A discarded attempt leaves a verifier whose challenge was never used, which T15's
 * next request overwrites.
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
    const stored = globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state);
    // Nothing stored is tested on its own, so no reader has to work out whether `null !== state` happened to be
    // true for the right reason. The stored value is never trimmed or case-folded - T15 wrote it, so padding
    // there would mean something is wrong - and anything that widens equality widens the injection surface.
    if (stored === null || stored === '' || stored !== state) {
      discard();
      return;
    }

    // The seam cannot reject while it is empty. It will once task 20 fills it with network I/O - offline, a 400
    // from the token endpoint, a clock skew - and a dropped rejection there would leave the visitor on this
    // holding screen indefinitely. Handled here so the failure mode is defined by the task that introduced the
    // seam rather than inherited by the one that fills it. Task 20 may route it somewhere more specific.
    exchangeAuthorizationCode({ code, state }).catch(discard);
  }, []);

  return (
    <main>
      <h1>{t('callback.heading')}</h1>
      <div role="status" aria-live="polite">{t('callback.working')}</div>
    </main>
  );
}
