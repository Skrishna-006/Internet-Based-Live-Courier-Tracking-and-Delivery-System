import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import RegisterPage from '../RegisterPage';
import * as authContextModule from '../../context/AuthContext';

vi.mock('../../context/AuthContext', async () => {
  const actual = await vi.importActual('../../context/AuthContext');
  return {
    ...actual,
    useAuth: vi.fn(),
  };
});

describe('RegisterPage Component', () => {
  let mockRegister;

  beforeEach(() => {
    vi.clearAllMocks();
    mockRegister = vi.fn();
    vi.spyOn(authContextModule, 'useAuth').mockReturnValue({
      user: null,
      isAuthenticated: false,
      login: vi.fn(),
      register: mockRegister,
      logout: vi.fn(),
      loading: false,
    });
  });

  it('renders registration form fields', () => {
    render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    );

    expect(screen.getByText('Create Customer Account')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Jane Doe')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('jane.doe@example.com')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Min 8 characters')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Re-enter password')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /register customer account/i })).toBeInTheDocument();
  });

  it('validates password length >= 8 characters', async () => {
    render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    );

    fireEvent.change(screen.getByPlaceholderText('Jane Doe'), { target: { value: 'Jane Customer' } });
    fireEvent.change(screen.getByPlaceholderText('jane.doe@example.com'), { target: { value: 'jane@example.com' } });
    fireEvent.change(screen.getByPlaceholderText('Min 8 characters'), { target: { value: 'short' } });
    fireEvent.change(screen.getByPlaceholderText('Re-enter password'), { target: { value: 'short' } });

    fireEvent.click(screen.getByRole('button', { name: /register customer account/i }));

    expect(screen.getByTestId('register-error-alert')).toBeInTheDocument();
    expect(screen.getByText(/Password must be at least 8 characters long/i)).toBeInTheDocument();
    expect(mockRegister).not.toHaveBeenCalled();
  });

  it('validates password confirmation match', async () => {
    render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    );

    fireEvent.change(screen.getByPlaceholderText('Jane Doe'), { target: { value: 'Jane Customer' } });
    fireEvent.change(screen.getByPlaceholderText('jane.doe@example.com'), { target: { value: 'jane@example.com' } });
    fireEvent.change(screen.getByPlaceholderText('Min 8 characters'), { target: { value: 'password123' } });
    fireEvent.change(screen.getByPlaceholderText('Re-enter password'), { target: { value: 'different123' } });

    fireEvent.click(screen.getByRole('button', { name: /register customer account/i }));

    expect(screen.getByTestId('register-error-alert')).toBeInTheDocument();
    expect(screen.getByText(/Passwords do not match/i)).toBeInTheDocument();
    expect(mockRegister).not.toHaveBeenCalled();
  });

  it('displays API error message when duplicate email is rejected by server', async () => {
    mockRegister.mockRejectedValueOnce(
      new Error('A user account with this email address already exists')
    );

    render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    );

    fireEvent.change(screen.getByPlaceholderText('Jane Doe'), { target: { value: 'Jane Existing' } });
    fireEvent.change(screen.getByPlaceholderText('jane.doe@example.com'), { target: { value: 'duplicate@example.com' } });
    fireEvent.change(screen.getByPlaceholderText('Min 8 characters'), { target: { value: 'password123' } });
    fireEvent.change(screen.getByPlaceholderText('Re-enter password'), { target: { value: 'password123' } });

    fireEvent.click(screen.getByRole('button', { name: /register customer account/i }));

    await waitFor(() => {
      expect(screen.getByTestId('register-error-alert')).toBeInTheDocument();
    });

    expect(
      screen.getByText(/A user account with this email address already exists/i)
    ).toBeInTheDocument();
  });

  it('displays success card upon valid customer registration', async () => {
    mockRegister.mockResolvedValueOnce({
      success: true,
      user: { id: 10, email: 'newbie@example.com', role: 'ROLE_CUSTOMER' },
    });

    render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    );

    fireEvent.change(screen.getByPlaceholderText('Jane Doe'), { target: { value: 'New User' } });
    fireEvent.change(screen.getByPlaceholderText('jane.doe@example.com'), { target: { value: 'newbie@example.com' } });
    fireEvent.change(screen.getByPlaceholderText('Min 8 characters'), { target: { value: 'securePass123' } });
    fireEvent.change(screen.getByPlaceholderText('Re-enter password'), { target: { value: 'securePass123' } });

    fireEvent.click(screen.getByRole('button', { name: /register customer account/i }));

    await waitFor(() => {
      expect(screen.getByTestId('register-success-card')).toBeInTheDocument();
    });

    expect(screen.getByText(/Registration Successful!/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /proceed to sign in/i })).toBeInTheDocument();
  });
});
