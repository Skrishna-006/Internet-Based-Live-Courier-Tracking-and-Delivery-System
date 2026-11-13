import React, { lazy, Suspense, useState } from 'react';
import { Routes, Route, Link, useNavigate } from 'react-router-dom';
import {
  Package,
  MapPin,
  Shield,
  Navigation,
  LogIn,
  UserPlus,
  LogOut,
  PlusCircle,
  RefreshCw,
  Menu,
} from 'lucide-react';
import { AuthProvider, useAuth } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import Sidebar from './components/Sidebar';
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

function NavigationBar({ onOpenSidebar }) {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const getRoleBadge = (role) => {
    switch (role) {
      case 'ROLE_ADMIN':
        return { label: 'Admin', color: 'bg-purple-500/15 text-purple-300 border-purple-500/30' };
      case 'ROLE_DELIVERY_AGENT':
        return { label: 'Agent', color: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30' };
      default:
        return { label: 'Customer', color: 'bg-blue-500/15 text-blue-300 border-blue-500/30' };
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
    <header className="sticky top-0 z-40 bg-[#0D1424]/90 backdrop-blur-md border-b border-[#263449]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        {/* Brand Logo & Mobile Sidebar Trigger */}
        <div className="flex items-center gap-3">
          <button
            onClick={onOpenSidebar}
            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-[#18243A] border border-[#263449] lg:hidden transition-colors"
            aria-label="Open Navigation Sidebar"
          >
            <Menu className="w-4 h-4" />
          </button>

          <Link to="/" className="flex items-center gap-2.5 group">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-blue-600 via-indigo-600 to-cyan-500 text-white flex items-center justify-center shadow-glow-blue group-hover:scale-105 transition-transform">
              <Package className="w-5 h-5" />
            </div>
            <div>
              <span className="font-bold text-base text-[#F8FAFC] tracking-tight block leading-tight">
                SwiftTrack Courier
              </span>
              <span className="text-[10px] text-cyan-400 font-medium tracking-wide uppercase">
                Live Tracking Platform
              </span>
            </div>
          </Link>
        </div>

        {/* Navigation Controls */}
        <nav className="flex items-center gap-3 text-xs font-semibold">
          {/* Always Accessible Public Live Tracking */}
          <Link
            to="/track"
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-cyan-500/10 text-cyan-400 hover:bg-cyan-500/20 transition-all border border-cyan-500/30"
          >
            <Navigation className="w-3.5 h-3.5 text-cyan-400" />
            <span className="hidden sm:inline">Live Tracking</span>
          </Link>

          {/* Authenticated State */}
          {isAuthenticated && user ? (
            <div className="flex items-center gap-3">
              {/* Role-Specific Portal Shortcut */}
              <Link
                to={getRoleDashboardLink(user.role).to}
                className="hidden sm:inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[#121C2E] hover:bg-[#18243A] text-slate-300 hover:text-white border border-[#263449] transition-all"
              >
                {getRoleDashboardLink(user.role).label}
              </Link>

              {user.role === 'ROLE_CUSTOMER' && (
                <Link
                  to="/customer/book"
                  className="hidden sm:inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-blue-500/10 hover:bg-blue-500/20 text-blue-400 transition-all border border-blue-500/30"
                >
                  <PlusCircle className="w-3.5 h-3.5 text-blue-400" />
                  <span>Book Shipment</span>
                </Link>
              )}

              {user.role === 'ROLE_ADMIN' && (
                <Link
                  to="/admin/fleet"
                  className="hidden sm:inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-purple-500/10 hover:bg-purple-500/20 text-purple-300 transition-all border border-purple-500/30"
                >
                  <MapPin className="w-3.5 h-3.5 text-purple-400" />
                  <span>Fleet Map</span>
                </Link>
              )}

              {/* User Identity & Role Badge */}
              <div className="flex items-center gap-2 pl-2 border-l border-[#263449]">
                <span
                  data-testid="navbar-role-badge"
                  className={`px-2 py-0.5 rounded-full border text-[10px] font-bold uppercase tracking-wider ${getRoleBadge(user.role).color}`}
                >
                  {getRoleBadge(user.role).label}
                </span>
                <span
                  data-testid="navbar-user-name"
                  className="hidden md:inline text-slate-300 text-xs font-medium max-w-[120px] truncate"
                >
                  {user.fullName || user.email}
                </span>
              </div>

              {/* Sign Out Action */}
              <button
                onClick={handleLogout}
                data-testid="navbar-logout-btn"
                aria-label="Sign Out"
                className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 transition-colors"
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
                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-slate-300 hover:text-white hover:bg-[#18243A] transition-colors"
              >
                <LogIn className="w-3.5 h-3.5" />
                Sign In
              </Link>
              <Link
                to="/register"
                data-testid="navbar-register-link"
                className="inline-flex items-center gap-1 px-3.5 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-500 text-white transition-all shadow-glow-blue"
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
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <AuthProvider>
      <div className="min-h-screen flex flex-col bg-[#080D19] text-[#F8FAFC]">
        {/* Responsive Drawer Sidebar */}
        <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />

        {/* Modern Dark Top Navigation Bar */}
        <NavigationBar onOpenSidebar={() => setSidebarOpen(true)} />

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
                        <RefreshCw className="w-8 h-8 text-purple-400 animate-spin" />
                        <p className="text-xs font-semibold text-slate-400">Loading Dispatch Hub...</p>
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
                        <RefreshCw className="w-8 h-8 text-cyan-400 animate-spin" />
                        <p className="text-xs font-semibold text-slate-400">Loading Live Fleet Map...</p>
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
        <footer className="border-t border-[#263449] bg-[#0D1424] py-6">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-slate-400">
            <p>© 2026 Internet-Based Live Courier Tracking and Delivery System. All rights reserved.</p>
            <div className="flex items-center gap-4">
              <span className="inline-flex items-center gap-1.5 text-slate-400">
                <Shield className="w-3.5 h-3.5 text-blue-400" />
                Role-Based Security
              </span>
              <span className="text-[#263449]">•</span>
              <span className="inline-flex items-center gap-1.5 text-slate-400">
                <MapPin className="w-3.5 h-3.5 text-cyan-400" />
                Live Telemetry
              </span>
            </div>
          </div>
        </footer>
      </div>
    </AuthProvider>
  );
}
