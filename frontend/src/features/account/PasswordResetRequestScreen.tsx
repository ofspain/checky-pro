import { useEffect, useRef, useState, type ReactElement } from 'react';
import { useTranslation } from 'react-i18next';
import { createPublicAuthApi } from '../../api/client';
import { EmailField, Form, Heading, Screen, StatusRegion, SubmitButton, Text } from '../../app/ui';

/**
 * The password-reset REQUEST screen — R14, and the third public form in the bundle.
 *
 * **THE PATH WAS ALREADY PUBLIC AND ALREADY EMPTY.** `'reset/request'` has been in `PUBLIC_ROUTE_PATHS`
 * since that list was written, rendering an empty document - the state task 49 left `resend-verification`
 * in and task 50 picked up. One difference matters: task 49 LINKED to its path before anything rendered
 * there, and **nothing in this bundle links here.** The screen is reachable by typing the URL only
 * (OQ-T52-3), accepted at the phase 4 gate as a known hole in the demo path rather than an oversight.
 *
 * **THE RESPONSE IS NEVER READ, AND THAT IS THE WHOLE OF R14's UNIFORMITY ON THIS CLIENT.** `auth.yaml`
 * declares exactly one response - a `200` whose own description is "Always returned regardless of whether
 * the email matches an account" - so the server hands back the same thing for a registered address, an
 * unregistered one, a locked account and a deleted one. This screen does not look at which it was: the
 * settle handler takes no argument.
 *
 * **BUT NOT LOOKING IS NECESSARY AND NOT SUFFICIENT, WHICH TWO TASKS PAID TO ESTABLISH.**
 * `parseAs: 'stream'` below is not a preference. Without it `openapi-fetch` parses the reply body on its own
 * behalf, so a `200` whose body is not valid JSON - an edge interstitial, a proxy's plain text, a response
 * cut mid-document - makes the promise REJECT, and the reject arm cannot tell that from a dropped
 * connection. The visitor would be told "we could not send that" about a link the server had just sent, and
 * worse, **the rendering would then depend on the SHAPE OF THE BODY the server chose**, which is exactly the
 * enumeration oracle L4 exists to close. Task 51 found that defect on the sibling screen and task 175 found
 * it on two more; this screen is the first to be written with the option from birth, and a source scan in
 * `api/` now fails for any non-reading call that omits it.
 *
 * **`response.ok` IS DELIBERATELY NOT CONSULTED** — the mirror image of `VerifyScreen`, where R4 required a
 * failure to be distinguished and `ok` was the one thing read. Here ONE acknowledgement is the requirement,
 * so every reply the transport delivers acknowledges. A case in the named suite consults this file's source
 * to say so, because one uniform acknowledgement is a property a future edit removes one `if` at a time.
 *
 * **A DROPPED CONNECTION SHOWS A NEUTRAL RETRY LINE** (OQ-T52-2, ratified at the phase 4 gate). R14 names no
 * failure behaviour at all - R15, one line below it, is the requirement that cites R4's uniform failure - so
 * this was an owner decision, and it follows D-T50-1, which reversed the opposite choice on the sibling
 * screen because *a visitor who presses a button and sees the screen go back to how it looked has been told
 * nothing at all*. It costs R14 nothing: the server answers every account state with one `200`, so every
 * SERVER outcome still renders the identical acknowledgement, and this line is reached only when the request
 * never arrived. A dropped connection says nothing about whether the address is registered, so the rendering
 * varies by TRANSPORT and never by account state.
 *
 * **ONE `<Heading>`, WHOSE TEXT CHANGES - NOT TWO** (D-T50-2, which measurement forced on the sibling).
 * `Heading` renders an `<h1>`, every screen in the bundle renders exactly one, and the design-system suite
 * pins `h1` to a length of 1 with the note that the document outline L11 requires has one top level.
 * Changing the heading's words keeps the outline and improves the announcement: focus moves to a heading
 * whose text is now the acknowledgement.
 *
 * **THE FOCUS MOVE IS THE ACKNOWLEDGEMENT'S ANNOUNCEMENT; THE REGION IS THE IN-FLIGHT ONE.** They are not
 * interchangeable. A live region alone would announce the acknowledgement and leave focus on
 * `document.body`, because the submit button that held it has been unmounted. A focus move alone would say
 * nothing while the request was in flight.
 *
 * **`setStatus('submitting')` ALSO CLEARS A PREVIOUS `'unsent'`**, which is the kind of line a later reader
 * deletes as redundant. Without it the retry line sits under a fresh in-flight attempt, telling the visitor
 * the send failed while it is still running. A case drives submit, reject, submit and reads the region in
 * between.
 */
export function PasswordResetRequestScreen(): ReactElement {
  const { t } = useTranslation();
  const [email, setEmail] = useState('');
  const [status, setStatus] = useState<'idle' | 'submitting' | 'acknowledged' | 'unsent'>('idle');
  const headingRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    if (status === 'acknowledged') headingRef.current?.focus();
  }, [status]);

  function submit(): void {
    setStatus('submitting');
    void createPublicAuthApi()
      .POST('/accounts/password-reset-request', { body: { email }, parseAs: 'stream' })
      .then(() => setStatus('acknowledged'), () => setStatus('unsent'));
  }

  const acknowledged = status === 'acknowledged';

  return (
    <Screen>
      <Heading ref={headingRef}>
        {acknowledged ? t('passwordResetRequest.acknowledgedHeading') : t('passwordResetRequest.heading')}
      </Heading>
      {acknowledged ? (
        <Text>{t('passwordResetRequest.acknowledgedText')}</Text>
      ) : (
        <Form onSubmit={submit}>
          <EmailField label={t('passwordResetRequest.emailLabel')} value={email} onChange={setEmail} />
          <SubmitButton>{t('passwordResetRequest.submit')}</SubmitButton>
        </Form>
      )}
      <StatusRegion>
        {status === 'submitting' ? t('passwordResetRequest.submitting') : null}
        {status === 'unsent' ? t('passwordResetRequest.unsent') : null}
      </StatusRegion>
    </Screen>
  );
}
