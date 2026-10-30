import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';
import ProtectedRoute from '../ProtectedRoute';
import * as authContextModule from '../../context/AuthContext';

vi.mock('../../context/AuthContext', async () => {
  const actual = await vi.importActual('../../context/AuthContext');
  return {
    ...actual,
    useAuth: vi.fn(),
  };
});

function LoginCapture() {
  const location = useLocation();
  return (
    <div>
      <span>Login Screen</span>
      <span data-testid="from-path">{location.state?.from?.pathname || 'none'}</span>
    </div>
  );
}

describe('ProtectedRoute Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders loading state when authentication check is in progress', () => {
    vi.spyOn(authContextModule, 'useAuth').mockReturnValue({
      user: null,
      isAuthenticated: false,
      loading: true,
    });

    render(
      <MemoryRouter initialEntries={['/protected']}>
        <ProtectedRoute>
          <div>Protected Content</div>
        </ProtectedRoute>
      </MemoryRouter>
    );

    expect(screen.getByText(/Validating credentials/i)).toBeInTheDocument();
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  it('redirects unauthenticated user to /login with state', () => {
    vi.spyOn(authContextModule, 'useAuth').mockReturnValue({
      user: null,
      isAuthenticated: false,
      loading: false,
    });

    render(
      <MemoryRouter initialEntries={['/customer/dashboard']}>
        <Routes>
          <Route
            path="/customer/dashboard"
            element={
              <ProtectedRoute allowedRoles={['ROLE_CUSTOMER']}>
                <div>Customer Dashboard</div>
              </ProtectedRoute>
            }
          />
          <Route path="/login" element={<LoginCapture />} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('Login Screen')).toBeInTheDocument();
    expect(screen.getByTestId('from-path').textContent).toBe('/customer/dashboard');
    expect(screen.queryByText('Customer Dashboard')).not.toBeInTheDocument();
  });

  it('renders children when authenticated with authorized role', () => {
    vi.spyOn(authContextModule, 'useAuth').mockReturnValue({
      user: { id: 1, email: 'customer@example.com', role: 'ROLE_CUSTOMER' },
      isAuthenticated: true,
      loading: false,
    });

    render(
      <MemoryRouter initialEntries={['/customer/dashboard']}>
        <Routes>
          <Route
            path="/customer/dashboard"
            element={
              <ProtectedRoute allowedRoles={['ROLE_CUSTOMER']}>
                <div>Customer Secret Area</div>
              </ProtectedRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('Customer Secret Area')).toBeInTheDocument();
  });

  it('redirects to /unauthorized when user does not have required role', () => {
    vi.spyOn(authContextModule, 'useAuth').mockReturnValue({
      user: { id: 2, email: 'customer@example.com', role: 'ROLE_CUSTOMER' },
      isAuthenticated: true,
      loading: false,
    });

    render(
      <MemoryRouter initialEntries={['/admin/dashboard']}>
        <Routes>
          <Route
            path="/admin/dashboard"
            element={
              <ProtectedRoute allowedRoles={['ROLE_ADMIN']}>
                <div>Admin Dispatch Console</div>
              </ProtectedRoute>
            }
          />
          <Route path="/unauthorized" element={<div>Access Restricted View</div>} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('Access Restricted View')).toBeInTheDocument();
    expect(screen.queryByText('Admin Dispatch Console')).not.toBeInTheDocument();
  });
});
