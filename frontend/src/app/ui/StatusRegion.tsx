import { forwardRef, type ReactNode } from 'react';

/**
 * A polite live region, for the one thing this application announces asynchronously: what just happened.
 *
 * **IT RENDERS UNCONDITIONALLY, AND THE CONSUMER PASSES CHILDREN ONLY WHEN THERE IS TEXT.** That order
 * matters and is not a style preference: a region that is mounted at the same moment its text arrives is a
 * new element with content already in it, and a screen reader announces nothing. Mounted first and filled
 * later, the same words are announced. Both existing consumers - the sign-in screen's status line and the
 * rate-limit notice - already do it this way, with a comment each saying so; this primitive is where the rule
 * now lives.
 *
 * `role` and `aria-live` are set here and are NOT props. An ARIA contract a consumer can override is not a
 * contract, and `aria-live="assertive"` would interrupt a screen reader mid-sentence for a status message.
 */
export const StatusRegion = forwardRef<HTMLDivElement, { children: ReactNode }>(
  function StatusRegion({ children }, ref) {
    return (
      <div ref={ref} role="status" aria-live="polite" className="text-step--1 text-muted">
        {children}
      </div>
    );
  },
);
