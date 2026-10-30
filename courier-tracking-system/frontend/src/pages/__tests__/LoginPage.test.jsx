import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import LoginPage from '../LoginPage';
import * as authContextModule from '../../context/AuthContext';

vi.mock('../../context/AuthContext', async () => {
  const actual = await vi.importActual('../../context/AuthContext');
  return {
    ...actual,
    useAuth: vi.fn(),
  };
});

describe('LoginPage Component', () => {
  let mockLogin;

  beforeEach(() => {
    vi.clearAllMocks();
    mockLogin = vi.fn();
    vi.spyOn(authContextModule, 'useAuth').mockReturnValue({
      user: null,
      isAuthenticated: false,
      login: mockLogin,
      logout: vi.fn(),
      loading: false,
    });
  });

  it('renders login form elements and links', () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    expect(screen.getByText('Sign In to SwiftTrack')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('name@example.com')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('••••••••')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
    expect(screen.getByText(/Create customer account/i)).toBeInTheDocument();
  });

  it('validates required fields before submitting', async () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    const submitBtn = screen.getByRole('button', { name: /sign in/i });
    fireEvent.click(submitBtn);

    expect(mockLogin).not.toHaveBeenCalled();
  });

  it('displays API error message when login fails', async () => {
    mockLogin.mockRejectedValueOnce(new Error('Invalid email or password'));

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    );

    fireEvent.change(screen.getByPlaceholderText('name@example.com'), {
      target: { value: 'user@example.com' },
    });
    fireEvent.change(screen.getByPlaceholderText('••••••••'), {
      target: { value: 'wrongpassword' },
    });

    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(screen.getByTestId('login-error-alert')).toBeInTheDocument();
    });

    expect(screen.getByText(/Invalid email or password/i)).toBeInTheDocument();
  });

  it('redirects to customer dashboard on successful customer authentication', async () => {
    mockLogin.mockResolvedValueOnce({
      success: true,
      user: { role: 'ROLE_CUSTOMER', email: 'customer@example.com' },
    });

    render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/customer/dashboard" element={<div data-testid="customer-dest">Customer Dashboard</div>} />
        </Routes>
      </MemoryRouter>
    );

    fireEvent.change(screen.getByPlaceholderText('name@example.com'), {
      target: { value: 'customer@example.com' },
    });
    fireEvent.change(screen.getByPlaceholderText('••••••••'), {
      target: { value: 'secret123' },
    });

    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(screen.getByTestId('customer-dest')).toBeInTheDocument();
    });
  });

  it('redirects to driver dashboard on delivery agent login', async () => {
    mockLogin.mockResolvedValueOnce({
      success: true,
      user: { role: 'ROLE_DELIVERY_AGENT', email: 'agent@example.com' },
    });

    render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/driver/dashboard" element={<div data-testid="driver-dest">Driver Dashboard</div>} />
        </Routes>
      </MemoryRouter>
    );

    fireEvent.change(screen.getByPlaceholderText('name@example.com'), {
      target: { value: 'agent@example.com' },
    });
    fireEvent.change(screen.getByPlaceholderText('••••••••'), {
      target: { value: 'agentSecret123' },
    });

    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(screen.getByTestId('driver-dest')).toBeInTheDocument();
    });
  });

  it('redirects to admin dashboard on admin login', async () => {
    mockLogin.mockResolvedValueOnce({
      success: true,
      user: { role: 'ROLE_ADMIN', email: 'admin@example.com' },
    });

    render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/admin/dashboard" element={<div data-testid="admin-dest">Admin Dashboard</div>} />
        </Routes>
      </MemoryRouter>
    );

    fireEvent.change(screen.getByPlaceholderText('name@example.com'), {
      target: { value: 'admin@example.com' },
    });
    fireEvent.change(screen.getByPlaceholderText('••••••••'), {
      target: { value: 'adminSecret123' },
    });

    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(screen.getByTestId('admin-dest')).toBeInTheDocument();
    });
  });
});
