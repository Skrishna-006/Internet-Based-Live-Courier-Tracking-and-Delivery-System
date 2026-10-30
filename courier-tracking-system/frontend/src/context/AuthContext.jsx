import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { authService } from '../services/api';

const AuthContext = createContext(null);

/**
 * Safely decodes a standard Base64URL-encoded JWT payload.
 * Returns decoded claims object or null if malformed.
 */
export function parseJwt(token) {
  if (!token || typeof token !== 'string') return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;

  try {
    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload);
  } catch {
    return null;
  }
}

/**
 * Checks whether a JWT has expired according to its exp claim.
 */
export function isTokenExpired(token) {
  const claims = parseJwt(token);
  if (!claims || typeof claims.exp !== 'number') return true;
  return claims.exp * 1000 <= Date.now();
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [loading, setLoading] = useState(true);

  // Restore authentication state on initial load
  useEffect(() => {
    try {
      const storedToken = localStorage.getItem('token');
      if (storedToken) {
        if (isTokenExpired(storedToken)) {
          // Token is expired; clean up state
          localStorage.removeItem('token');
          localStorage.removeItem('user');
          setToken(null);
          setUser(null);
        } else {
          const claims = parseJwt(storedToken);
          if (claims && claims.role) {
            let restoredUser = null;
            try {
              const cachedUser = localStorage.getItem('user');
              if (cachedUser) {
                restoredUser = JSON.parse(cachedUser);
              }
            } catch {
              // Ignore JSON parse failure
            }

            // Always enforce role and email from signed JWT claims
            setUser({
              id: claims.userId || restoredUser?.id || null,
              email: claims.sub || restoredUser?.email || '',
              fullName: restoredUser?.fullName || claims.sub || 'User',
              phoneNumber: restoredUser?.phoneNumber || '',
              role: claims.role, // Strictly bound to token claim
              active: restoredUser?.active ?? true,
            });
            setToken(storedToken);
          } else {
            // Malformed token without valid role claim
            localStorage.removeItem('token');
            localStorage.removeItem('user');
            setToken(null);
            setUser(null);
          }
        }
      }
    } catch {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      setToken(null);
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  const login = useCallback(async (email, password) => {
    const response = await authService.login({ email, password });
    const payload = response?.data;
    if (!payload || !payload.accessToken) {
      throw new Error('Authentication response did not contain a valid access token');
    }

    const claims = parseJwt(payload.accessToken);
    if (!claims || !claims.role) {
      throw new Error('Server token is missing necessary authorization claims');
    }

    const authenticatedUser = {
      id: payload.user?.id || claims.userId,
      email: payload.user?.email || claims.sub,
      fullName: payload.user?.fullName || claims.sub,
      phoneNumber: payload.user?.phoneNumber || '',
      role: claims.role, // Bound to token claim
      active: payload.user?.active ?? true,
    };

    localStorage.setItem('token', payload.accessToken);
    localStorage.setItem('user', JSON.stringify(authenticatedUser));

    setToken(payload.accessToken);
    setUser(authenticatedUser);

    return { success: true, user: authenticatedUser };
  }, []);

  const register = useCallback(async (registrationData) => {
    const response = await authService.registerCustomer(registrationData);
    return { success: true, user: response?.data };
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setToken(null);
    setUser(null);
  }, []);

  const value = {
    user,
    token,
    isAuthenticated: Boolean(token && user && !isTokenExpired(token)),
    loading,
    login,
    register,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
