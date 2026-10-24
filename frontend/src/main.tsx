import { mountShell } from './app/mountShell';
import { registerServiceWorker } from './pwa/registerServiceWorker';

mountShell(document.getElementById('root'));

// After the shell is mounted, never before: a new build waits for the user either way (agents.md line 91).
void registerServiceWorker();
