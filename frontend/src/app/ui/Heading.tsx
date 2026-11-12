import { forwardRef, type ReactNode } from 'react';

/**
 * The level-one heading, one per screen.
 *
 * THERE IS NO `level` PROP, and that is deliberate. Every screen in this application has exactly one heading
 * and it is the screen's name, so a level prop would be an API with no consumer - and the first thing it would
 * permit is a second `<h1>`, or an `<h2>` with no `<h1>` above it, both of which break the document outline
 * L11 requires. A screen that needs a subheading adds a `Subheading` primitive with a real consumer.
 *
 * `tabIndex={-1}` IS SET HERE AND IS NOT A PROP (task 174, D1). A single-page application replaces a
 * document's contents without a document load, so nothing moves the reader: focus stays where it was, or on
 * `<body>`, and a screen reader announces nothing on arrival. The fix is to move focus to the new screen, and
 * the heading is the right target because it is the only element that announces the screen BY NAME - a
 * `<main>` announces a landmark and leaves the name to chance, and a focusable wrapper announces a label
 * borrowed from this heading anyway.
 *
 * It is set inside rather than accepted, and that is `index.ts` rule 2 observed rather than bent. Phase 3
 * proposed a narrowly typed `tabIndex?: -1` prop plus an amendment to the rule that names `tabIndex`
 * explicitly. Both were rejected: the rule's own sentence already prescribes the remedy ("set inside"), and a
 * rule relaxed by its first customer - one task after it was frozen - was never a rule. The props type is
 * still `{ children }`, which the build gate enforces.
 *
 * EVERY heading becomes programmatically focusable, not only the one screen that needed it today. That is the
 * decision, not a side effect: `-1` adds nothing to the tab order, so there is no cost to a keyboard user,
 * and the alternative is a prop every future screen must remember to pass - an accessibility contract that
 * silently does nothing when forgotten. Task 42 and every screen after it get the move for free.
 */
export const Heading = forwardRef<HTMLHeadingElement, { children: ReactNode }>(
  function Heading({ children }, ref) {
    return (
      <h1 ref={ref} tabIndex={-1} className="text-step-1 font-semibold tracking-tight text-text">
        {children}
      </h1>
    );
  },
);
