import { forwardRef, useId, type ReactNode } from 'react';

/**
 * The email field: a labelled `<input type="email">`.
 *
 * **IT RENDERS TWO ELEMENTS, WHICH NO PRIMITIVE BEFORE IT DID** (task 43, D3). A control's label is part of
 * the control: an input with no `<label>` has no accessible name, and a `<label>` with no input is a word on
 * a page. `useId()` links them, and it is called HERE because the link is `htmlFor`/`id` - the attribute pair
 * that IS this primitive's contract, which rule 2 says is set inside and never accepted. Splitting the two
 * into separate primitives would have meant handing those ids to a screen; using `aria-label` on a bare
 * input instead would have given the field a name with no visible text, which WCAG 2.1 3.3.2 expects a
 * data-entry field to have. So the design-system suite's "exactly one element" rule grows a second, named
 * exception rather than this file taking a worse shape.
 *
 * **`type="email"` IS SET INSIDE AND IS NOT A PROP.** It is the whole difference between this file and its
 * sibling: it asks a phone for the keyboard that has an `@` on it, and it tells a password
 * manager this is the account identifier rather than a secret. A single `TextField` taking a `type` prop would put that decision in the hands of every
 * consumer, which is rule 2's exact prohibition - and the layer already works this way, with `ActionButton`
 * and `TextLink` as two files rather than one `Button` with a `kind`.
 *
 * `onChange` hands back the STRING. A primitive that passed the `ChangeEvent` through would make every
 * screen reach into `event.target`, which is the element this layer exists to keep out of screens.
 *
 * `label` is a `ReactNode` the screen passes from the catalogue: rule 3 forbids a primitive from CARRYING
 * copy, not from rendering it.
 */
export const EmailField = forwardRef<HTMLInputElement, {
  label: ReactNode;
  value: string;
  onChange: (next: string) => void;
}>(function EmailField({ label, value, onChange }, ref) {
  const id = useId();
  return (
    <>
      <label htmlFor={id} className="text-step-0 font-medium text-text">
        {label}
      </label>
      <input
        ref={ref}
        id={id}
        type="email"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="mb-2 rounded-md border border-muted bg-surface px-3 py-2 text-step-0 text-text outline-offset-2 focus-visible:outline-2 focus-visible:outline-focus"
      />
    </>
  );
});
