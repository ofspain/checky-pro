import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { App } from './App';

describe('shellBuildsAndMounts', () => {
  it('renders the app shell', () => {
    render(<App />);
    expect(screen.getByTestId('app-shell')).toBeInTheDocument();
  });

  it('renders the home route under /app', () => {
    window.history.pushState({}, '', '/app');
    render(<App />);
    expect(screen.getByTestId('home-route')).toBeInTheDocument();
    expect(screen.getByTestId('home-route')).toHaveTextContent('Checky Pro');
  });
});
