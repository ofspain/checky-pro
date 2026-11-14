import { forwardRef, useId, type ReactNode } from 'react';

/**
 * The password field: a labelled `<input type="password">`.
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
 * **`type="password"` IS SET INSIDE AND IS NOT A PROP.** It is the whole difference between this file and its
 * sibling: this one is MASKED, is kept out of autofill
 * heuristics for ordinary text, and is the one field in this application whose value must never be read
 * back by anything but the submit that sends it. A single `TextField` taking a `type` prop would put that decision in the hands of every
 * consumer, which is rule 2's exact prohibition - and the layer already works this way, with `ActionButton`
 * and `TextLink` as two files rather than one `Button` with a `kind`.
 *
 * `onChange` hands back the STRING. A primitive that passed the `ChangeEvent` through would make every
 * screen reach into `event.target`, which is the element this layer exists to keep out of screens.
 *
 * `label` is a `ReactNode` the screen passes from the catalogue: rule 3 forbids a primitive from CARRYING
 * copy, not from rendering it.
 */
export const PasswordField = forwardRef<HTMLInputElement, {
  label: ReactNode;
  value: string;
  onChange: (next: string) => void;
  /**
   * The advisory state, and the only thing this field says about the value's acceptability (task 43).
   *
   * It is a STATE the screen computes, like `ActionButton`'s `aria-disabled`, not a contract attribute -
   * which is why it is a prop here and `type` is not. R1 makes the client's 12-to-128 check advisory and
   * the server the authority, so this marks the field and blocks nothing. The WORDS that explain it are
   * task 45's, which owns the rejection path and should choose its own copy; until then this is a state
   * with no sentence, which is deliberate and incomplete rather than wrong.
   */
  'aria-invalid'?: boolean;
}>(function PasswordField({ label, value, onChange, 'aria-invalid': ariaInvalid }, ref) {
  const id = useId();
  return (
    <>
      <label htmlFor={id} className="text-step-0 font-medium text-text">
        {label}
      </label>
      <input
        ref={ref}
        id={id}
        type="password"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={ariaInvalid}
        className="mb-2 rounded-md border border-muted bg-surface px-3 py-2 text-step-0 text-text outline-offset-2 focus-visible:outline-2 focus-visible:outline-focus"
      />
    </>
  );
});
