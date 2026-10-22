import type { ReactNode } from 'react';

/** The application shell: the root every later route and provider mounts inside (L12). It renders no copy
 * of its own, because user-facing text comes only from the i18n catalogue (L11). */
export function AppShell({ children }: { children?: ReactNode }) {
  return <div data-testid="app-shell">{children}</div>;
}
