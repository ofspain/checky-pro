import { useEffect, useRef, useState, type ReactElement } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';
import { createPublicAuthApi } from '../../api/client';
import { RESEND_VERIFICATION_PATH } from '../../app/routes';
import { Heading, Screen, StatusRegion, TextLink } from '../../app/ui';

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
 * **IDLE MEANT THREE SITUATIONS UNDER TASK 48 AND MEANS EXACTLY ONE NOW: the request is in flight.** Task 49
 * split the other two out. A failed request and a link carrying no token both reach `'failure'`, which is
 * R4's single uniform message plus its resend action.
 *
 * **A TOKENLESS ARRIVAL IS A FAILURE HERE, AND R4 DOES NOT LITERALLY SAY SO** (D-T49-2, decided by the owner
 * at the phase 4 gate against Kimi's phase 3, which chose to render the heading alone). R4 fires "IF the
 * verification *request* fails", and with no token no request is made - so on the letter, nothing failed. It
 * is still the right screen: a truncated or wrapped URL is the likeliest broken link anyone will actually
 * meet, the remedy is identical to a rejected token, and the alternative left that visitor on a heading with
 * no way forward - the blank screen the demo rehearsal had already found once.
 *
 * **`setStatus('idle')` BEFORE THE POST IS NOT REDUNDANT**, which is the kind of line a later reader deletes.
 * It is reachable: arrive with no token, land in `'failure'`, then follow a complete link into the same
 * mounted screen - task 48 measured that transition and its case still runs. Without this line the failure
 * sentence would sit on screen for the whole round trip of a verification that is about to succeed. With it,
 * the in-flight state is neutral whatever preceded it, which is what makes "a valid token never renders the
 * failure copy" true without an exception.
 *
 * **THE RESEND LINK SITS OUTSIDE THE LIVE REGION, DELIBERATELY.** An interactive control inside a
 * `role="status"` region is re-announced every time the region's text changes. The message is what gets
 * announced; the link is what gets operated.
 *
 * **NOTHING HERE READS THE RESPONSE BODY, AND THAT IS THE STRUCTURAL HALF OF L4.** `response.ok` is the only
 * thing consulted - not `status`, not `error`, not a problem document's `type` or `title`. The backend maps
 * every rejection reason onto one fixed 400, so the only way this screen could leak which reason occurred is
 * by looking; a source scan in the named suite forbids it, because "one uniform message" is a property a
 * future edit can take away one `if` at a time.
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
  const [status, setStatus] = useState<'idle' | 'success' | 'failure'>('idle');

  useEffect(() => {
    headingRef.current?.focus();
  }, []);

  useEffect(() => {
    if (handled.current) return;
    const token = params.get('token');
    if (token === null || token === '') {
      setStatus('failure');
      return;
    }
    handled.current = true;
    setStatus('idle');
    void createPublicAuthApi()
      .POST('/accounts/verify-email', { body: { token } })
      // `response.ok` rather than `=== 204`: the contract declares one success response and AC4 is about
      // reaching the success state, not about the number. openapi-fetch does NOT reject on an HTTP error - it
      // resolves with `error` and `response` set - so the rejection arm below sees transport failures only.
      // Task 49 filled that arm: it was `() => {}` under task 48, which kept a transport failure from
      // becoming an unhandled rejection but left the visitor on a blank screen. R4 says "fails for any
      // reason", and a network that never answered is a reason.
      .then(({ response }) => {
        setStatus(response.ok ? 'success' : 'failure');
      }, () => {
        setStatus('failure');
      });
  }, [params]);

  return (
    <Screen>
      <Heading ref={headingRef}>{t('verify.heading')}</Heading>
      <StatusRegion>
        {status === 'success' ? t('verify.success') : null}
        {status === 'failure' ? t('verify.failureMessage') : null}
      </StatusRegion>
      {status === 'failure' ? (
        <TextLink to={`/${RESEND_VERIFICATION_PATH}`}>{t('verify.failureAction')}</TextLink>
      ) : null}
    </Screen>
  );
}
