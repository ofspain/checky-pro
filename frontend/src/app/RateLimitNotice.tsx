import { useRef, useSyncExternalStore } from 'react';
import { useTranslation } from 'react-i18next';
import type { RateLimitNoticeState } from '../api/rateLimitNotice';
import { ActionButton, FocusHolder, StatusRegion } from './ui';

/**
 * Shows the fixed "too many requests" copy while `notice` is shown (R23), with no countdown and no timer. The live
 * region is always rendered, so screen readers announce the copy when it appears (L11). Only the dismiss button
 * clears it.
 *
 * Render it inside an `<I18nextProvider>` holding the tab's `createI18n()` instance. Don't mount it in `AppShell`
 * until the first screen adds that provider tree: T01's test asserts the shell renders no markup.
 *
 * TASK 173 moved it onto three primitives, and this component is the reason `FocusHolder` exists. The focus
 * dance below needs a ref to a real element that is focusable but not tabbable; `FocusHolder` forwards its ref
 * for exactly that, and `ActionButton` forwards one too. If either stopped forwarding, `wrapper.current` would
 * be `null`, `?.focus()` would do nothing, and a keyboard user would be dropped to `<body>` with the button
 * they pressed gone - silently, with no test failing unless one asserts `document.activeElement`. One already
 * does: `rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx:399-400` asserts the active element IS the
 * wrapper and is NOT `<body>`, and it passed unchanged across this move. That existing case is the evidence,
 * deliberately cited instead of the criterion (AC6) that phase 10 will add - a comment that names a test which
 * does not exist yet is a claim, not a measurement (T41 phase 8, I10).
 */
export function RateLimitNotice({ notice }: { notice: RateLimitNoticeState }) {
  const shown = useSyncExternalStore(notice.subscribe, notice.isShown);
  const { t } = useTranslation();
  const wrapper = useRef<HTMLDivElement>(null);
  const dismissButton = useRef<HTMLButtonElement>(null);

  // Dismissing unmounts the button, which would drop a keyboard user's focus to <body> (L11). The wrapper is
  // focusable programmatically only (tabIndex -1) and takes the focus the button is about to lose. A screen can
  // later restore the element that had focus before the notice appeared; the component can't know it.
  function dismiss() {
    const buttonHadFocus = document.activeElement === dismissButton.current;
    notice.dismiss();
    if (buttonHadFocus) wrapper.current?.focus();
  }

  return (
    <FocusHolder ref={wrapper}>
      {/* Always rendered, never conditional: a live region mounted at the same moment its text arrives is a
          new element with content already in it, and a screen reader announces nothing. `StatusRegion` is
          where that rule now lives, and it is why the ternary is INSIDE it rather than around it. */}
      <StatusRegion>
        {shown ? t('rateLimit.message') : null}
      </StatusRegion>
      {shown ? (
        <ActionButton ref={dismissButton} onClick={dismiss}>
          {t('rateLimit.dismiss')}
        </ActionButton>
      ) : null}
    </FocusHolder>
  );
}
