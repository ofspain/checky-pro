import { forwardRef, type ReactNode } from 'react';

/**
 * The form element — the first one in this bundle, and the reason three other primitives exist.
 *
 * **IT OWNS `preventDefault`, AND THAT IS A CONTRACT RATHER THAN A COURTESY** (task 43, D13). A native
 * submit navigates: the browser leaves the page, which in a single-page application is the end of the
 * session's in-memory state (L3) and of everything else this tab was holding. jsdom does not implement form
 * submission at all, so it logs "Not implemented" instead, which means a forgotten `preventDefault` would
 * fail in production and merely print in the suite. Owning it here is the only place a consumer cannot
 * forget it.
 *
 * **IT SETS `noValidate`.** The browser's own constraint validation would otherwise block the submit for an
 * `<input type="email">` it dislikes and explain why in a bubble whose words come from the browser, in the
 * browser's language. Every string in this application comes from the catalogue (L11), and R1 puts the
 * authority for what is acceptable on the SERVER, with the client's checks advisory. A native bubble is both
 * a string we did not write and a gate the spec does not grant, so the attribute is set here, inside, where
 * no screen can lose it.
 *
 * `onSubmit` is behaviour, not an ARIA contract, so accepting it is not rule 2's business - what rule 2
 * forbids is a consumer overriding what the ELEMENT means, and a form that did not submit would be the
 * override. It takes no arguments: the event is prevented and consumed here, and a handler that received it
 * could re-enable the navigation this file exists to stop.
 */
export const Form = forwardRef<HTMLFormElement, { children: ReactNode; onSubmit: () => void }>(
  function Form({ children, onSubmit }, ref) {
    return (
      <form
        ref={ref}
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          onSubmit();
        }}
        className="flex w-full flex-col gap-2"
      >
        {children}
      </form>
    );
  },
);
