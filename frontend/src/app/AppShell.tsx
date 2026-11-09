import type { ReactNode } from 'react';
import { Surface } from './ui';

/** The application shell: the root every later route and provider mounts inside (L12). It renders no copy
 * of its own, because user-facing text comes only from the i18n catalogue (L11).
 *
 * TASK 173 moved it onto `Surface`, the primitive that carries the page's background and text colour. The
 * `data-testid` stays HERE rather than inside the primitive: `app-shell` is this component's identity, and a
 * primitive a later screen may reuse has no business hardcoding it. `shellBuildsAndMounts.test.tsx:102`
 * asserts this element's `innerHTML` is empty before any route matches, which a class attribute on the
 * element itself does not change - its children are what that case reads. */
export function AppShell({ children }: { children?: ReactNode }) {
  return <Surface data-testid="app-shell">{children}</Surface>;
}
