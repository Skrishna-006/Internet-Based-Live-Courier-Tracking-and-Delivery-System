import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import App from '../App';

function createMockToken(claims) {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = btoa(JSON.stringify(claims))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `${header}.${payload}.mockSignature123456789`;
}

// Mock systemService for LandingPage health check
vi.mock('../services/api', () => ({
  authService: {
    login: vi.fn(),
    registerCustomer: vi.fn(),
  },
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
  shipmentService: {
    getPriceQuote: vi.fn().mockResolvedValue({ data: null }),
    createShipment: vi.fn().mockResolvedValue({ data: null }),
    getCustomerShipments: vi.fn().mockResolvedValue({
      success: true,
      data: {
        content: [],
        totalElements: 0,
        totalPages: 0,
        number: 0,
        first: true,
        last: true,
      },
    }),
    getShipmentById: vi.fn().mockResolvedValue({ data: null }),
  },
}));

describe('App Routing & Authentication Integration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('renders landing page on root route / without authentication', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    );

    expect(screen.getByText(/SwiftTrack Courier/i)).toBeInTheDocument();
    expect(
      screen.getByText(/Internet-Based Live Courier Tracking & Delivery System/i)
    ).toBeInTheDocument();
  });

  it('renders public live tracking view on /track route without authentication', () => {
    render(
      <MemoryRouter initialEntries={['/track']}>
        <App />
      </MemoryRouter>
    );

    expect(screen.getByText(/Ready to Track/i)).toBeInTheDocument();
    expect(screen.getByText(/Track Your Delivery in Real Time/i)).toBeInTheDocument();
  });

  it('renders sign in and register navbar links when unauthenticated', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    );

    expect(screen.getByTestId('navbar-login-link')).toBeInTheDocument();
    expect(screen.getByTestId('navbar-register-link')).toBeInTheDocument();
    expect(screen.queryByTestId('navbar-logout-btn')).not.toBeInTheDocument();
  });

  it('renders customer role badge, shortcuts, and logout when logged in as customer', async () => {
    const futureTimestamp = Math.floor(Date.now() / 1000) + 3600;
    const token = createMockToken({
      sub: 'customer@example.com',
      userId: 10,
      role: 'ROLE_CUSTOMER',
      exp: futureTimestamp,
    });
    localStorage.setItem('token', token);
    localStorage.setItem(
      'user',
      JSON.stringify({
        id: 10,
        email: 'customer@example.com',
        fullName: 'Jane Customer',
        role: 'ROLE_CUSTOMER',
      })
    );

    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('navbar-role-badge')).toBeInTheDocument();
    });

    expect(screen.getByTestId('navbar-role-badge').textContent).toBe('Customer');
    expect(screen.getByText('My Bookings')).toBeInTheDocument();
    expect(screen.getByTestId('navbar-logout-btn')).toBeInTheDocument();
  });

  it('renders agent role badge and driver mode shortcut when logged in as agent', async () => {
    const futureTimestamp = Math.floor(Date.now() / 1000) + 3600;
    const token = createMockToken({
      sub: 'agent@example.com',
      userId: 20,
      role: 'ROLE_DELIVERY_AGENT',
      exp: futureTimestamp,
    });
    localStorage.setItem('token', token);
    localStorage.setItem(
      'user',
      JSON.stringify({
        id: 20,
        email: 'agent@example.com',
        fullName: 'Field Agent',
        role: 'ROLE_DELIVERY_AGENT',
      })
    );

    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('navbar-role-badge')).toBeInTheDocument();
    });

    expect(screen.getByTestId('navbar-role-badge').textContent).toBe('Agent');
    expect(screen.getByText('Driver Mode')).toBeInTheDocument();
  });

  it('renders admin role badge and dispatch hub shortcut when logged in as admin', async () => {
    const futureTimestamp = Math.floor(Date.now() / 1000) + 3600;
    const token = createMockToken({
      sub: 'admin@example.com',
      userId: 1,
      role: 'ROLE_ADMIN',
      exp: futureTimestamp,
    });
    localStorage.setItem('token', token);
    localStorage.setItem(
      'user',
      JSON.stringify({
        id: 1,
        email: 'admin@example.com',
        fullName: 'Dispatcher Admin',
        role: 'ROLE_ADMIN',
      })
    );

    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('navbar-role-badge')).toBeInTheDocument();
    });

    expect(screen.getByTestId('navbar-role-badge').textContent).toBe('Admin');
    expect(screen.getByText('Dispatch Hub')).toBeInTheDocument();
  });

  it('redirects unauthenticated user accessing customer dashboard to /login', async () => {
    render(
      <MemoryRouter initialEntries={['/customer/dashboard']}>
        <App />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Sign In to SwiftTrack')).toBeInTheDocument();
    });
  });

  it('navbar logout button clears user state and transitions to guest', async () => {
    const futureTimestamp = Math.floor(Date.now() / 1000) + 3600;
    const token = createMockToken({
      sub: 'user@example.com',
      role: 'ROLE_CUSTOMER',
      exp: futureTimestamp,
    });
    localStorage.setItem('token', token);

    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('navbar-logout-btn')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId('navbar-logout-btn'));

    await waitFor(() => {
      expect(screen.getByTestId('navbar-login-link')).toBeInTheDocument();
    });

    expect(localStorage.getItem('token')).toBeNull();
  });

  it('allows authenticated customer to access /customer/dashboard and /customer/book', async () => {
    const futureTimestamp = Math.floor(Date.now() / 1000) + 3600;
    const token = createMockToken({
      sub: 'customer@example.com',
      userId: 10,
      role: 'ROLE_CUSTOMER',
      exp: futureTimestamp,
    });
    localStorage.setItem('token', token);
    localStorage.setItem(
      'user',
      JSON.stringify({
        id: 10,
        email: 'customer@example.com',
        fullName: 'Jane Customer',
        role: 'ROLE_CUSTOMER',
      })
    );

    const { unmount } = render(
      <MemoryRouter initialEntries={['/customer/dashboard']}>
        <App />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('My Shipments')).toBeInTheDocument();
    });

    unmount();

    render(
      <MemoryRouter initialEntries={['/customer/book']}>
        <App />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Book Courier Shipment')).toBeInTheDocument();
    });
  });

  it('redirects unauthenticated user accessing /customer/book to /login', async () => {
    render(
      <MemoryRouter initialEntries={['/customer/book']}>
        <App />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Sign In to SwiftTrack')).toBeInTheDocument();
    });
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
