import { forwardRef, type ReactNode } from 'react';

/**
 * A plain container with the application's own background and text colour — the shell's element.
 *
 * ONE CONSUMER: `app/AppShell.tsx`, which is why `data-testid` is a prop rather than a hardcoded value. The
 * shell's own identity (`app-shell`) belongs to the shell, not to a primitive that any later screen may use.
 *
 * It takes `className` and `style` NOWHERE in this layer, and that is a type-level decision rather than a
 * convention: a primitive that accepted one would let a screen style around the layer, and
 * `screensRenderThroughDesignSystemPrimitivesOnly`'s claim would become decorative. The violation is a
 * compile error, which is a stronger instrument than any scan this task could write.
 */
export const Surface = forwardRef<HTMLDivElement, { children: ReactNode; 'data-testid'?: string }>(
  function Surface({ children, 'data-testid': testId }, ref) {
    return (
      <div ref={ref} data-testid={testId} className="min-h-dvh bg-surface text-text">
        {children}
      </div>
    );
  },
);
