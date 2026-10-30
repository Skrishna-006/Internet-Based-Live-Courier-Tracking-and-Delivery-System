import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import App from '../App';

// Mock systemService for LandingPage health check
vi.mock('../services/api', () => ({
  systemService: {
    getHealth: vi.fn().mockResolvedValue({
      data: {
        status: 'UP',
        systemName: 'Courier Tracking System',
        components: { database: { status: 'UP' } },
      },
    }),
  },
  trackingService: {
    getPublicTracking: vi.fn().mockResolvedValue({ data: null }),
    getPublicLiveLocation: vi.fn().mockResolvedValue({ data: null }),
  },
}));

describe('App Routing Integration', () => {
  it('renders landing page on root route /', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    );

    expect(screen.getByText(/SwiftTrack Courier/i)).toBeInTheDocument();
    expect(
      screen.getByText(/Internet-Based Live Courier Tracking & Delivery System/i)
    ).toBeInTheDocument();
    expect(screen.getByText(/Backend Service Diagnostic/i)).toBeInTheDocument();
  });

  it('renders public live tracking view on /track route', () => {
    render(
      <MemoryRouter initialEntries={['/track']}>
        <App />
      </MemoryRouter>
    );

    expect(screen.getByText(/Ready to Track/i)).toBeInTheDocument();
    expect(screen.getByText(/Track Your Delivery in Real Time/i)).toBeInTheDocument();
  });

  it('renders not found page on unknown route', () => {
    render(
      <MemoryRouter initialEntries={['/some/unknown/page']}>
        <App />
      </MemoryRouter>
    );

    expect(screen.getByText(/404/i)).toBeInTheDocument();
  });
});
