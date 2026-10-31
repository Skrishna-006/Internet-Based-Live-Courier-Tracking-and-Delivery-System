import React, { lazy, Suspense } from 'react';
import { Routes, Route, Link, useNavigate } from 'react-router-dom';
import { Package, MapPin, Shield, Navigation, LogIn, UserPlus, LogOut, User as UserIcon, PlusCircle, RefreshCw } from 'lucide-react';
import { AuthProvider, useAuth } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import LandingPage from './pages/LandingPage';
import PublicTrackingPage from './pages/PublicTrackingPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import UnauthorizedPage from './pages/UnauthorizedPage';
import CustomerDashboardPage from './pages/CustomerDashboardPage';
import BookingPage from './pages/BookingPage';
import DriverDashboardPage from './pages/DriverDashboardPage';
import DriverDeliveryPage from './pages/DriverDeliveryPage';
import PlaceholderDashboard from './pages/PlaceholderDashboard';
import NotFoundPage from './pages/NotFoundPage';

// Code-split heavy admin modules so public landing/tracking pages load without admin/leaflet overhead
const AdminDashboardPage = lazy(() => import('./pages/AdminDashboardPage'));
const AdminFleetMapPage = lazy(() => import('./pages/AdminFleetMapPage'));

function NavigationBar() {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const getRoleBadge = (role) => {
    switch (role) {
      case 'ROLE_ADMIN':
        return { label: 'Admin', color: 'bg-purple-100 text-purple-800 border-purple-200' };
      case 'ROLE_DELIVERY_AGENT':
        return { label: 'Agent', color: 'bg-emerald-100 text-emerald-800 border-emerald-200' };
      default:
        return { label: 'Customer', color: 'bg-blue-100 text-blue-800 border-blue-200' };
    }
  };

  const getRoleDashboardLink = (role) => {
    switch (role) {
      case 'ROLE_ADMIN':
        return { to: '/admin/dashboard', label: 'Dispatch Hub' };
      case 'ROLE_DELIVERY_AGENT':
        return { to: '/driver/dashboard', label: 'Driver Mode' };
      default:
        return { to: '/customer/dashboard', label: 'My Bookings' };
    }
  };

  return (
    <header className="sticky top-0 z-50 bg-white/90 backdrop-blur-md border-b border-slate-200">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        {/* Brand Logo */}
        <Link to="/" className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-blue-600 to-indigo-600 text-white flex items-center justify-center shadow-sm">
            <Package className="w-5 h-5" />
          </div>
          <div>
            <span className="font-bold text-base text-slate-900 tracking-tight block leading-tight">
              SwiftTrack Courier
            </span>
            <span className="text-[10px] text-slate-500 font-medium tracking-wide uppercase">
              Live Tracking Platform
            </span>
          </div>
        </Link>

        {/* Navigation Controls */}
        <nav className="flex items-center gap-3 text-xs font-semibold">
          {/* Always Accessible Public Live Tracking */}
          <Link
            to="/track"
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-indigo-50 text-indigo-700 hover:bg-indigo-100 transition-colors border border-indigo-200"
          >
            <Navigation className="w-3.5 h-3.5 text-indigo-600" />
            <span className="hidden sm:inline">Live Tracking</span>
          </Link>

          {/* Authenticated State */}
          {isAuthenticated && user ? (
            <div className="flex items-center gap-3">
              {/* Role-Specific Portal Shortcut */}
              <Link
                to={getRoleDashboardLink(user.role).to}
                className="hidden sm:inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 transition-colors"
              >
                {getRoleDashboardLink(user.role).label}
              </Link>

              {user.role === 'ROLE_CUSTOMER' && (
                <Link
                  to="/customer/book"
                  className="hidden sm:inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-indigo-50 hover:bg-indigo-100 text-indigo-700 transition-colors border border-indigo-200"
                >
                  <PlusCircle className="w-3.5 h-3.5 text-indigo-600" />
                  <span>Book Shipment</span>
                </Link>
              )}

              {user.role === 'ROLE_ADMIN' && (
                <Link
                  to="/admin/fleet"
                  className="hidden sm:inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-purple-50 hover:bg-purple-100 text-purple-700 transition-colors border border-purple-200"
                >
                  <MapPin className="w-3.5 h-3.5 text-purple-600" />
                  <span>Fleet Map</span>
                </Link>
              )}

              {/* User Identity & Role Badge */}
              <div className="flex items-center gap-2 pl-1 border-l border-slate-200">
                <span
                  data-testid="navbar-role-badge"
                  className={`px-2 py-0.5 rounded-full border text-[10px] font-bold uppercase tracking-wider ${getRoleBadge(user.role).color}`}
                >
                  {getRoleBadge(user.role).label}
                </span>
                <span
                  data-testid="navbar-user-name"
                  className="hidden md:inline text-slate-700 text-xs font-medium max-w-[120px] truncate"
                >
                  {user.fullName || user.email}
                </span>
              </div>

              {/* Sign Out Action */}
              <button
                onClick={handleLogout}
                data-testid="navbar-logout-btn"
                aria-label="Sign Out"
                className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-slate-500 hover:text-rose-600 hover:bg-rose-50 transition-colors"
              >
                <LogOut className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">Sign Out</span>
              </button>
            </div>
          ) : (
            /* Unauthenticated State */
            <div className="flex items-center gap-2">
              <Link
                to="/login"
                data-testid="navbar-login-link"
                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-slate-600 hover:text-indigo-600 hover:bg-slate-100 transition-colors"
              >
                <LogIn className="w-3.5 h-3.5" />
                Sign In
              </Link>
              <Link
                to="/register"
                data-testid="navbar-register-link"
                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white transition-colors shadow-sm"
              >
                <UserPlus className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">Register</span>
              </Link>
            </div>
          )}
        </nav>
      </div>
    </header>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <div className="min-h-screen flex flex-col bg-slate-50 text-slate-800">
        <NavigationBar />

        {/* Main Content View */}
        <main className="flex-1 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 w-full">
          <Routes>
            {/* Public Routes */}
            <Route path="/" element={<LandingPage />} />
            <Route path="/track" element={<PublicTrackingPage />} />
            <Route path="/track/:trackingNumber" element={<PublicTrackingPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/unauthorized" element={<UnauthorizedPage />} />

            {/* Protected Role-Based Routes */}
            <Route
              path="/customer/dashboard"
              element={
                <ProtectedRoute allowedRoles={['ROLE_CUSTOMER']}>
                  <CustomerDashboardPage />
                </ProtectedRoute>
              }
            />

            <Route
              path="/customer/book"
              element={
                <ProtectedRoute allowedRoles={['ROLE_CUSTOMER']}>
                  <BookingPage />
                </ProtectedRoute>
              }
            />

            <Route
              path="/driver/dashboard"
              element={
                <ProtectedRoute allowedRoles={['ROLE_DELIVERY_AGENT']}>
                  <DriverDashboardPage />
                </ProtectedRoute>
              }
            />

            <Route
              path="/driver/delivery/:id"
              element={
                <ProtectedRoute allowedRoles={['ROLE_DELIVERY_AGENT']}>
                  <DriverDeliveryPage />
                </ProtectedRoute>
              }
            />

            <Route
              path="/admin/dashboard"
              element={
                <ProtectedRoute allowedRoles={['ROLE_ADMIN']}>
                  <Suspense
                    fallback={
                      <div className="h-96 flex flex-col items-center justify-center p-8 space-y-3" data-testid="admin-lazy-loading">
                        <RefreshCw className="w-8 h-8 text-purple-600 animate-spin" />
                        <p className="text-xs font-semibold text-slate-500">Loading Dispatch Hub...</p>
                      </div>
                    }
                  >
                    <AdminDashboardPage />
                  </Suspense>
                </ProtectedRoute>
              }
            />

            <Route
              path="/admin/fleet"
              element={
                <ProtectedRoute allowedRoles={['ROLE_ADMIN']}>
                  <Suspense
                    fallback={
                      <div className="h-96 flex flex-col items-center justify-center p-8 space-y-3" data-testid="fleet-lazy-loading">
                        <RefreshCw className="w-8 h-8 text-purple-600 animate-spin" />
                        <p className="text-xs font-semibold text-slate-500">Loading Live Fleet Map...</p>
                      </div>
                    }
                  >
                    <AdminFleetMapPage />
                  </Suspense>
                </ProtectedRoute>
              }
            />

            {/* Fallback */}
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </main>

        {/* Footer */}
        <footer className="border-t border-slate-200 bg-white py-6">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-slate-500">
            <p>© 2026 Internet-Based Live Courier Tracking and Delivery System. All rights reserved.</p>
            <div className="flex items-center gap-4">
              <span className="inline-flex items-center gap-1">
                <Shield className="w-3.5 h-3.5 text-slate-400" />
                Role-Based Security
              </span>
              <span>•</span>
              <span className="inline-flex items-center gap-1">
                <MapPin className="w-3.5 h-3.5 text-slate-400" />
                Live Telemetry
              </span>
            </div>
          </div>
        </footer>
      </div>
    </AuthProvider>
  );
}
