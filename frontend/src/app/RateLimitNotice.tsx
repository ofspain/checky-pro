import { useSyncExternalStore } from 'react';
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
  return (
    <div>
      <div role="status" aria-live="polite">
        {shown ? t('rateLimit.message') : null}
      </div>
      {shown ? (
        <button type="button" onClick={notice.dismiss}>
          {t('rateLimit.dismiss')}
        </button>
      ) : null}
    </div>
  );
}
