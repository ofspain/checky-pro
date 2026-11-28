import { useEffect, useRef, useState, type ReactElement } from 'react';
import { useTranslation } from 'react-i18next';
import { createPublicAuthApi } from '../../api/client';
import { EmailField, Form, Heading, Screen, StatusRegion, SubmitButton, Text } from '../../app/ui';

/**
 * The resend-verification screen — R5, and the destination task 49 deliberately left blank.
 *
 * Task 49 promoted `RESEND_VERIFICATION_PATH` and attached no element, because R4's uniform failure carries a
 * resend action and the spelling was needed in two places. So this path has been a declared public route
 * rendering an empty document for one task, and the link on `VerifyScreen` really did land there.
 *
 * **THE RESPONSE IS NEVER READ, AND THAT IS THE WHOLE OF R5'S UNIFORMITY ON THIS CLIENT.** `auth.yaml`
 * declares exactly one response - a `200` whose own description is "Always returned regardless of whether the
 * email matches an account" - and `AccountService.resendVerificationIfPending` never throws for a non-match.
 * So the server hands back the same thing for a registered address, an unregistered one, a locked account and
 * a deleted one. This screen does not look at which it was: the settle handler takes no argument.
 *
 * THIS PARAGRAPH USED TO SAY "the only way this screen could leak which of those happened is by looking",
 * AND TASK 51 DISPROVED IT. The screen leaked WITHOUT LOOKING: `openapi-fetch` parsed the body on its own
 * behalf, so a reply that did not parse took the reject arm and rendered a different screen - and which
 * reply parses is a property of the body the server chose to send. Not looking was necessary and not
 * sufficient, because a dependency can arrive through a library the source never names. `parseAs: 'stream'`
 * below is what actually closed it, and the thirteen-arm comparison in the suite is what holds it. **`response.ok` IS DELIBERATELY NOT CONSULTED** - the mirror image of
 * `VerifyScreen`, where R4 required failure to be distinguished and `ok` was the one thing read. A case scans
 * this file's source for any read of the response, because one uniform acknowledgement is a property a future
 * edit removes one `if` at a time.
 *
 * **THE CONTRACT'S `message` IS REQUIRED AND DELIBERATELY UNREAD**, exactly as task 47 settled for
 * registration (D2, `RegisterSentScreen.tsx`): `RegistrationAcknowledgement` is `required: [message]`, so the
 * server always sends a sentence and this client always ignores it. L11 locks every user-facing string to the
 * catalogue, and a server-supplied sentence has no catalogue key - no `t()` call, no D7 case measuring it, and
 * no way for the owner to change the words without a server deploy. Do not "fix" this by rendering the body.
 *
 * **A DROPPED CONNECTION SHOWS A NEUTRAL RETRY LINE, AND THAT IS AN OWNER DECISION** (D-T50-1, reversing the
 * phase 3 design, which returned the form to `'idle'` showing nothing and matched `RegisterScreen`). It is the
 * second time running that a "render nothing" choice was reversed - the first was task 49's tokenless arrival
 * - and the reason is the same: a visitor who presses a button and sees the screen go back to how it looked
 * has been told nothing at all.
 *
 * It costs nothing R5 asks for. The server answers every account state with one `200`, so **every server
 * outcome still renders the identical acknowledgement**; this line is reached only when the request never
 * arrived. L4 is about unknown, locked, suspended and deleted accounts and bad, expired and used tokens, and a
 * dropped connection is orthogonal to all of them: it says nothing about whether the address is registered.
 * The rendering varies by TRANSPORT, never by account state.
 *
 * **ONE `<Heading>`, WHOSE TEXT CHANGES - NOT TWO** (D-T50-2, forced by measurement). The frozen design put a
 * second `Heading` inside the acknowledged branch, under the screen's own. `Heading` renders an `<h1>`, every
 * one of the seven screens that existed before this one renders exactly one, and the design-system suite pins
 * `h1` to a length of 1 for three of them with the note that "the document outline L11 requires has one top
 * level". Two would have made this the first screen in the bundle to break a LOCKED accessibility rule, to
 * say two things one task apart. Changing the heading's words instead keeps the outline and improves the
 * announcement: focus moves to a heading whose text is now the acknowledgement.
 *
 * **THE FOCUS MOVE IS THE ACKNOWLEDGEMENT'S ANNOUNCEMENT; THE REGION IS THE IN-FLIGHT ONE.** Each mechanism
 * is already established here and they are not interchangeable. A live region alone would announce the
 * acknowledgement and leave focus on `document.body`, because the submit button that held it has been
 * unmounted. A focus move alone would say nothing while the request was in flight. So `'submitting'` and
 * `'unsent'` are region text, and `'acknowledged'` is a focus move.
 *
 * **THE FORM IS REPLACED ON ACKNOWLEDGEMENT, AND STAYS MOUNTED ON `'unsent'`.** L14's verbatim route list has
 * no `/sent` sibling for this path - registration has one and resend does not - so the acknowledgement renders
 * in place, and replacing the form is what stops a second send against an acknowledgement that already says a
 * link is on its way. A failed send is the opposite case: the retry is the point, so the form is still there.
 *
 * **`setStatus('submitting')` ALSO CLEARS A PREVIOUS `'unsent'`**, which is the kind of line a later reader
 * deletes as redundant. Without it the retry line would sit under a fresh in-flight attempt, telling the
 * visitor the send failed while it is still running. Task 49 needed the same guarantee for the same reason and
 * met it the same way; a case drives submit, reject, submit and reads the region in between.
 */
export function ResendVerificationScreen(): ReactElement {
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
      // `parseAs: 'stream'` IS THE WHOLE OF TASK 51'S PRODUCTION CHANGE, and R5's uniformity depends on it.
      //
      // Without it `openapi-fetch` parses the body itself, and a `200` whose body is not valid JSON - an edge
      // interstitial, a response truncated mid-document, a plain-text answer from a proxy - makes the promise
      // REJECT with a SyntaxError. The reject arm below cannot tell that apart from a dropped connection, so
      // the visitor was told "we could not send that" about an email the server had just sent.
      //
      // Worse than wrong: the rendering then depended on the SHAPE OF THE RESPONSE BODY, so a proxy or a
      // future backend answering differently-shaped bodies for different account states would have turned
      // this public form into the enumeration oracle L4 exists to close.
      //
      // The option states this screen's actual contract - IT HAS NO INTEREST IN THE BODY - where a catch on
      // SyntaxError would have described a symptom. Measured at phase 2: thirteen outcome arms collapse to one
      // rendering, and a dropped connection still shows the retry line, which is the half that must not move.
      .POST('/accounts/resend-verification', { body: { email }, parseAs: 'stream' })
      .then(() => setStatus('acknowledged'), () => setStatus('unsent'));
  }

  const acknowledged = status === 'acknowledged';

  return (
    <Screen>
      <Heading ref={headingRef}>
        {acknowledged ? t('resend.acknowledgedHeading') : t('resend.heading')}
      </Heading>
      {acknowledged ? (
        <Text>{t('resend.acknowledgedText')}</Text>
      ) : (
        <Form onSubmit={submit}>
          <EmailField label={t('resend.emailLabel')} value={email} onChange={setEmail} />
          <SubmitButton>{t('resend.submit')}</SubmitButton>
        </Form>
      )}
      <StatusRegion>
        {status === 'submitting' ? t('resend.submitting') : null}
        {status === 'unsent' ? t('resend.unsent') : null}
      </StatusRegion>
    </Screen>
  );
}
