import { forwardRef, type ReactNode } from 'react';

/**
 * The level-one heading, one per screen.
 *
 * THERE IS NO `level` PROP, and that is deliberate. Every screen in this application has exactly one heading
 * and it is the screen's name, so a level prop would be an API with no consumer - and the first thing it would
 * permit is a second `<h1>`, or an `<h2>` with no `<h1>` above it, both of which break the document outline
 * L11 requires. A screen that needs a subheading adds a `Subheading` primitive with a real consumer.
 */
export const Heading = forwardRef<HTMLHeadingElement, { children: ReactNode }>(
  function Heading({ children }, ref) {
    return (
      <h1 ref={ref} className="text-step-1 font-semibold tracking-tight text-text">
        {children}
      </h1>
    );
  },
);
