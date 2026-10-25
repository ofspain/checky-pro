import { StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { I18nextProvider } from 'react-i18next';
import { RouterProvider } from 'react-router';
import { createI18n } from '../i18n/i18n';
import { AppShell } from './AppShell';
import { createAppRouter } from './routes';

/** Mounts the shell into `container`. Throws if it is missing, so a broken page fails loudly
 * instead of rendering nothing. Called once per page load from main.tsx, so the browser router it
 * creates lives as long as the page and is never disposed. */
export function mountShell(container: HTMLElement | null): Root {
  if (container === null) {
    throw new Error('mountShell: root element #root not found');
  }
  const root = createRoot(container);
  // One i18n instance per mount, which is once per page load - createI18n's own contract, and the reason there is
  // no global instance to fall back on. The provider sits OUTSIDE AppShell so the shell still renders nothing but
  // its children, which is what T01 asserts.
  root.render(
    <StrictMode>
      <I18nextProvider i18n={createI18n()}>
        <AppShell>
          <RouterProvider router={createAppRouter()} />
        </AppShell>
      </I18nextProvider>
    </StrictMode>,
  );
  return root;
}
