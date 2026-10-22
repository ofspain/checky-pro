import { Routes, Route, Navigate } from 'react-router-dom';

export function Shell() {
  return (
    <div data-testid="app-shell">
      <Routes>
        <Route path="/app" element={<div data-testid="home-route">Checky Pro</div>} />
        <Route path="*" element={<Navigate to="/app" replace />} />
      </Routes>
    </div>
  );
}
