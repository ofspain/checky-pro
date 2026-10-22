import { BrowserRouter } from 'react-router-dom';
import { Shell } from './Shell';

export function App() {
  return (
    <BrowserRouter
      future={{
        v7_startTransition: true,
        v7_relativeSplatPath: true,
      }}
    >
      <Shell />
    </BrowserRouter>
  );
}
