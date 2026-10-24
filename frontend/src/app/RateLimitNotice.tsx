import { useRef, useSyncExternalStore } from 'react';
import { useTranslation } from 'react-i18next';
import type { RateLimitNoticeState } from '../api/rateLimitNotice';

/**
 * Shows the fixed "too many requests" copy while `notice` is shown (R23), with no countdown and no timer. The live
 * region is always rendered, so screen readers announce the copy when it appears (L11). Only the dismiss button
 * clears it.
 *
 * Render it inside an `<I18nextProvider>` holding the tab's `createI18n()` instance. Don't mount it in `AppShell`
 * until the first screen adds that provider tree: T01's test asserts the shell renders no markup.
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
    <div ref={wrapper} tabIndex={-1}>
      <div role="status" aria-live="polite">
        {shown ? t('rateLimit.message') : null}
      </div>
      {shown ? (
        <button type="button" ref={dismissButton} onClick={dismiss}>
          {t('rateLimit.dismiss')}
        </button>
      ) : null}
    </div>
  );
}
