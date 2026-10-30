import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/**
 * Route protection wrapper enforcing authentication and role-based permissions.
 *
 * @param {Object} props
 * @param {string[]} [props.allowedRoles=[]] - Roles authorized to access this route (e.g. ['ROLE_CUSTOMER'])
 * @param {React.ReactNode} props.children
 */
export default function ProtectedRoute({ allowedRoles = [], children }) {
  const { isAuthenticated, user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[50vh] space-y-3">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
        <span className="text-xs text-slate-500 font-medium">Validating credentials...</span>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (allowedRoles.length > 0 && (!user || !allowedRoles.includes(user.role))) {
    return <Navigate to="/unauthorized" state={{ from: location }} replace />;
  }

  return children;
}
