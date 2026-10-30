import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act, waitFor } from '@testing-library/react';
import { AuthProvider, useAuth, parseJwt, isTokenExpired } from '../AuthContext';
import { authService } from '../../services/api';

vi.mock('../../services/api', () => ({
  authService: {
    login: vi.fn(),
    registerCustomer: vi.fn(),
  },
  systemService: {
    getHealth: vi.fn().mockResolvedValue({ data: { status: 'UP' } }),
  },
  trackingService: {
    getPublicTracking: vi.fn(),
    getPublicLiveLocation: vi.fn(),
  },
}));

function createMockToken(claims) {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = btoa(JSON.stringify(claims))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `${header}.${payload}.mockSignature123456789`;
}

function ConsumerComponent() {
  const { user, token, isAuthenticated, loading, login, logout } = useAuth();
  return (
    <div>
      <div data-testid="auth-loading">{loading ? 'loading' : 'ready'}</div>
      <div data-testid="auth-status">{isAuthenticated ? 'authenticated' : 'guest'}</div>
      <div data-testid="auth-user-role">{user?.role || 'none'}</div>
      <div data-testid="auth-user-email">{user?.email || 'none'}</div>
      <button onClick={() => login('customer@example.com', 'password123')}>Login</button>
      <button onClick={logout}>Logout</button>
    </div>
  );
}

describe('AuthContext & JWT Utilities', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('parseJwt correctly parses valid JWT payload', () => {
    const token = createMockToken({ sub: 'test@example.com', role: 'ROLE_CUSTOMER', userId: 123 });
    const parsed = parseJwt(token);
    expect(parsed).not.toBeNull();
    expect(parsed.sub).toBe('test@example.com');
    expect(parsed.role).toBe('ROLE_CUSTOMER');
    expect(parsed.userId).toBe(123);
  });

  it('parseJwt returns null for malformed or invalid tokens', () => {
    expect(parseJwt(null)).toBeNull();
    expect(parseJwt('')).toBeNull();
    expect(parseJwt('invalid.token')).toBeNull();
    expect(parseJwt('not.a.valid.jwt.payload')).toBeNull();
  });

  it('isTokenExpired accurately detects expired and active tokens', () => {
    const expiredTimestamp = Math.floor(Date.now() / 1000) - 60; // 1 min ago
    const futureTimestamp = Math.floor(Date.now() / 1000) + 3600; // in 1 hour

    const expiredToken = createMockToken({ sub: 'user@exp.com', exp: expiredTimestamp });
    const validToken = createMockToken({ sub: 'user@valid.com', exp: futureTimestamp });

    expect(isTokenExpired(expiredToken)).toBe(true);
    expect(isTokenExpired(validToken)).toBe(false);
    expect(isTokenExpired('malformed')).toBe(true);
  });

  it('restores authenticated state from valid token in localStorage on mount', async () => {
    const futureTimestamp = Math.floor(Date.now() / 1000) + 7200;
    const token = createMockToken({
      sub: 'stored@example.com',
      userId: 42,
      role: 'ROLE_DELIVERY_AGENT',
      exp: futureTimestamp,
    });

    localStorage.setItem('token', token);
    localStorage.setItem(
      'user',
      JSON.stringify({
        id: 42,
        email: 'stored@example.com',
        fullName: 'Stored Agent',
        role: 'ROLE_DELIVERY_AGENT',
      })
    );

    render(
      <AuthProvider>
        <ConsumerComponent />
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByTestId('auth-loading').textContent).toBe('ready');
    });

    expect(screen.getByTestId('auth-status').textContent).toBe('authenticated');
    expect(screen.getByTestId('auth-user-role').textContent).toBe('ROLE_DELIVERY_AGENT');
    expect(screen.getByTestId('auth-user-email').textContent).toBe('stored@example.com');
  });

  it('purges localStorage and starts unauthenticated when stored token is expired', async () => {
    const expiredTimestamp = Math.floor(Date.now() / 1000) - 300;
    const token = createMockToken({
      sub: 'expired@example.com',
      userId: 99,
      role: 'ROLE_CUSTOMER',
      exp: expiredTimestamp,
    });

    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify({ email: 'expired@example.com' }));

    render(
      <AuthProvider>
        <ConsumerComponent />
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByTestId('auth-loading').textContent).toBe('ready');
    });

    expect(screen.getByTestId('auth-status').textContent).toBe('guest');
    expect(localStorage.getItem('token')).toBeNull();
  });

  it('login updates authentication state, stores token, and parses role', async () => {
    const futureTimestamp = Math.floor(Date.now() / 1000) + 3600;
    const mockAccessToken = createMockToken({
      sub: 'customer@example.com',
      userId: 5,
      role: 'ROLE_CUSTOMER',
      exp: futureTimestamp,
    });

    authService.login.mockResolvedValueOnce({
      data: {
        accessToken: mockAccessToken,
        tokenType: 'Bearer',
        expiresIn: 3600,
        user: {
          id: 5,
          email: 'customer@example.com',
          fullName: 'Jane Customer',
          role: 'ROLE_CUSTOMER',
        },
      },
    });

    render(
      <AuthProvider>
        <ConsumerComponent />
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByTestId('auth-loading').textContent).toBe('ready');
    });

    act(() => {
      screen.getByText('Login').click();
    });

    await waitFor(() => {
      expect(screen.getByTestId('auth-status').textContent).toBe('authenticated');
    });

    expect(screen.getByTestId('auth-user-role').textContent).toBe('ROLE_CUSTOMER');
    expect(localStorage.getItem('token')).toBe(mockAccessToken);
  });

  it('logout cleans up state and clears localStorage tokens', async () => {
    const futureTimestamp = Math.floor(Date.now() / 1000) + 3600;
    const token = createMockToken({
      sub: 'user@example.com',
      role: 'ROLE_ADMIN',
      exp: futureTimestamp,
    });
    localStorage.setItem('token', token);

    render(
      <AuthProvider>
        <ConsumerComponent />
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByTestId('auth-status').textContent).toBe('authenticated');
    });

    act(() => {
      screen.getByText('Logout').click();
    });

    expect(screen.getByTestId('auth-status').textContent).toBe('guest');
    expect(screen.getByTestId('auth-user-role').textContent).toBe('none');
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
  });
});
