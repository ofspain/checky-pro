import { useEffect, useRef, useState, type ReactElement } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';
import { createPublicAuthApi } from '../../api/client';
import { Heading, Screen, StatusRegion } from '../../app/ui';

/**
 * The email-verification screen — R3, task 48, and the destination of every verification email the platform
 * has sent. `/app/verify` has been a declared public path with no element since task 16, so until now that
 * link landed on a blank document; the demo rehearsal found exactly that.
 *
 * **THIS SCREEN READS A QUERY PARAMETER, AND THE SCREEN ONE FILE AWAY IS FORBIDDEN TO.** `RegisterSentScreen`
 * (task 47) has a source scan that fails if it so much as mentions `useSearchParams`, and the contrast is not
 * an inconsistency: R2 asks that screen to render *identically* for two different outcomes, which it keeps by
 * having no inputs at all, so a query parameter would be a way for the two journeys to differ. R3 asks this
 * screen to act on *the one input the visitor brought with them*. An acknowledgement with an input cannot be
 * identical; a verification screen without one cannot verify. Same package, opposite requirements - and
 * `design.md:145` is the author's own note fixing the shape as `/app/verify?token=<token>`.
 *
 * **`handled` IS SET BEFORE THE REQUEST AND AFTER THE EMPTY-TOKEN RETURN**, and both halves of that sentence
 * are load-bearing. Before the request, because StrictMode invokes effects twice in development and the token
 * is one-time: a second POST would be rejected by the server, so the visitor would watch a successful
 * verification turn into a failure. `CallbackScreen:48,75` does the same thing for the same reason, and its
 * comment records that the guard's absence once made it never navigate at all, in development only, while
 * every other assertion still passed. After the empty-token return, because latching the guard on a tokenless
 * arrival would make a later `params` change carrying a real token do nothing - the guard exists to mean *a
 * request has been made*, not *this effect has run*.
 *
 * **FOCUS IS A SEPARATE EFFECT ON PURPOSE.** Moving focus is unconditional (L11); verifying is not. Combining
 * them would make focus depend on whether the link carried a token, which is a different screen's bug waiting
 * to be written.
 *
 * **THE TOKEN IS STORED NOWHERE.** L16 lists what `sessionStorage` may hold - `state`, `nonce`,
 * `code_verifier` and the rest - and a verification token is not on it. It is read from the URL, sent once,
 * and never written, logged, or passed to anything else. It is a bearer-equivalent possession credential that
 * happens to arrive in a query string.
 *
 * **IDLE IS WHAT THIS SCREEN SHOWS FOR THREE DIFFERENT SITUATIONS** - the request in flight, no token in the
 * link, and a request that failed - and task 48 cannot tell them apart, deliberately. R3 covers the success
 * state; R4's one uniform failure message is task 49's, and inventing its copy here would pre-empt it. The
 * empty rejection handler below is therefore not laziness: without it a transport failure becomes an
 * unhandled rejection, which phase 5 measured failing a whole test file while every assertion in it passed.
 *
 * **RISK R-T48-1, recorded here because this is the file someone would come to in order to fix it.** The POST
 * runs on arrival, which R3's wording requires - "WHEN a visitor opens a verification link ... THEN the SPA
 * SHALL show the verified success state". An email-scanning gateway or link previewer that executes
 * JavaScript therefore spends the token before the human clicks:
 * `VerificationTokenService.consumeForPurpose` calls `markConsumed` at :133 and a second attempt finds
 * nothing, so the account really is verified and the human is shown task 49's uniform failure - which R4
 * forbids task 49 from explaining, because one uniform message cannot say "you may already be verified". The
 * owner decided on arrival at the phase 4 gate, since a confirm control would mean opening the link shows a
 * button instead of the success state, and that needs an R3 amendment rather than a client change. Kimi's
 * phase 3 argued a confirm control "would still be consumed by anything that executes JavaScript"; that was
 * rejected, because a previewer renders without clicking. If R3 is ever amended, the change is this effect.
 */
export function VerifyScreen(): ReactElement {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const headingRef = useRef<HTMLHeadingElement>(null);
  const handled = useRef(false);
  const [status, setStatus] = useState<'idle' | 'success'>('idle');

  useEffect(() => {
    headingRef.current?.focus();
  }, []);

  useEffect(() => {
    if (handled.current) return;
    const token = params.get('token');
    if (token === null || token === '') return;
    handled.current = true;
    void createPublicAuthApi()
      .POST('/accounts/verify-email', { body: { token } })
      // `response.ok` rather than `=== 204`: the contract declares one success response and AC4 is about
      // reaching the success state, not about the number. openapi-fetch does NOT reject on an HTTP error - it
      // resolves with `error` and `response` set - so the rejection arm below sees transport failures only.
      .then(({ response }) => {
        if (response.ok) setStatus('success');
      }, () => {});
  }, [params]);

  return (
    <Screen>
      <Heading ref={headingRef}>{t('verify.heading')}</Heading>
      <StatusRegion>{status === 'success' ? t('verify.success') : null}</StatusRegion>
    </Screen>
  );
}
