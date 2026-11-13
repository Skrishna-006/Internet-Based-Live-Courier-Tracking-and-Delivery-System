import React from 'react';
import { NavLink, Link, useNavigate } from 'react-router-dom';
import {
  Package,
  Navigation,
  MapPin,
  PlusCircle,
  Truck,
  Users,
  Shield,
  LogOut,
  LogIn,
  UserPlus,
  Home,
  X,
  Layers,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export default function Sidebar({ isOpen, onClose }) {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();

  // Keep drawer unmounted when closed to avoid duplicate accessible navigation trees
  if (!isOpen) return null;

  const handleLogout = () => {
    logout();
    navigate('/login');
    if (onClose) onClose();
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

  const linkClass = ({ isActive }) =>
    `flex items-center gap-3 px-3 py-2.5 rounded-xl text-xs font-semibold transition-all duration-200 ${
      isActive
        ? 'bg-blue-500/15 text-blue-400 border border-blue-500/30 shadow-glow-blue'
        : 'text-slate-400 hover:text-[#F8FAFC] hover:bg-[#18243A] border border-transparent'
    }`;

  return (
    <>
      {/* Mobile Backdrop */}
      <div
        onClick={onClose}
        className="fixed inset-0 bg-black/70 backdrop-blur-sm z-40 lg:hidden transition-opacity"
      />

      {/* Sidebar Container */}
      <aside
        className="fixed top-0 bottom-0 left-0 z-50 w-72 bg-[#0D1424] border-r border-[#263449] flex flex-col justify-between transition-transform duration-300 ease-in-out"
      >
        {/* Top Header & Branding */}
        <div>
          <div className="h-16 px-5 border-b border-[#263449] flex items-center justify-between">
            <Link to="/" onClick={onClose} className="flex items-center gap-3 group">
              <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-blue-600 via-indigo-600 to-cyan-500 text-white flex items-center justify-center shadow-glow-blue group-hover:scale-105 transition-transform">
                <Package className="w-5 h-5" />
              </div>
              <div>
                <span className="font-bold text-sm text-[#F8FAFC] tracking-tight block leading-tight">
                  SwiftTrack
                </span>
                <span className="text-[10px] text-cyan-400 font-medium tracking-wider uppercase">
                  Live Logistics System
                </span>
              </div>
            </Link>

            {/* Close Button */}
            <button
              onClick={onClose}
              className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-[#18243A] transition-colors"
              aria-label="Close Sidebar"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Navigation Links */}
          <div className="px-3 py-5 space-y-6 overflow-y-auto max-h-[calc(100vh-140px)]">
            {/* General Section */}
            <div className="space-y-1">
              <p className="px-3 text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-2">
                Navigation
              </p>
              <NavLink to="/" onClick={onClose} className={linkClass} end>
                <Home className="w-4 h-4" />
                <span>Home</span>
              </NavLink>
              <NavLink to="/track" onClick={onClose} className={linkClass}>
                <Navigation className="w-4 h-4 text-cyan-400" />
                <span>Consignment Tracking</span>
              </NavLink>
            </div>

            {/* Role-Specific Workflows */}
            {isAuthenticated && user && (
              <div className="space-y-1 pt-2 border-t border-[#263449]/60">
                <p className="px-3 text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-2">
                  {user.role === 'ROLE_ADMIN'
                    ? 'Dispatch Operations'
                    : user.role === 'ROLE_DELIVERY_AGENT'
                    ? 'Field Deliveries'
                    : 'Customer Portal'}
                </p>

                {user.role === 'ROLE_CUSTOMER' && (
                  <>
                    <NavLink to="/customer/dashboard" onClick={onClose} className={linkClass}>
                      <Package className="w-4 h-4 text-blue-400" />
                      <span>Bookings Directory</span>
                    </NavLink>
                    <NavLink to="/customer/book" onClick={onClose} className={linkClass}>
                      <PlusCircle className="w-4 h-4 text-cyan-400" />
                      <span>New Consignment</span>
                    </NavLink>
                  </>
                )}

                {user.role === 'ROLE_DELIVERY_AGENT' && (
                  <>
                    <NavLink to="/driver/dashboard" onClick={onClose} className={linkClass}>
                      <Truck className="w-4 h-4 text-emerald-400" />
                      <span>Delivery Cockpit</span>
                    </NavLink>
                  </>
                )}

                {user.role === 'ROLE_ADMIN' && (
                  <>
                    <NavLink to="/admin/dashboard" onClick={onClose} className={linkClass}>
                      <Layers className="w-4 h-4 text-purple-400" />
                      <span>Dispatch Central</span>
                    </NavLink>
                    <NavLink to="/admin/fleet" onClick={onClose} className={linkClass}>
                      <MapPin className="w-4 h-4 text-cyan-400" />
                      <span>Live Fleet Radar</span>
                    </NavLink>
                  </>
                )}
              </div>
            )}

            {/* Unauthenticated Shortcuts */}
            {!isAuthenticated && (
              <div className="space-y-1 pt-2 border-t border-[#263449]/60">
                <p className="px-3 text-[10px] font-bold text-slate-400 uppercase tracking-widest mb-2">
                  Account Access
                </p>
                <NavLink to="/login" onClick={onClose} className={linkClass}>
                  <LogIn className="w-4 h-4" />
                  <span>Account Login</span>
                </NavLink>
                <NavLink to="/register" onClick={onClose} className={linkClass}>
                  <UserPlus className="w-4 h-4 text-blue-400" />
                  <span>Create Account</span>
                </NavLink>
              </div>
            )}
          </div>
        </div>

        {/* Bottom User Profile Section */}
        {isAuthenticated && user ? (
          <div className="p-3 border-t border-[#263449] bg-[#0A0F1D]">
            <div className="p-2.5 rounded-xl bg-[#121C2E] border border-[#263449] flex items-center justify-between gap-2">
              <div className="flex items-center gap-2.5 min-w-0">
                <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-blue-600 to-indigo-600 text-white flex items-center justify-center font-bold text-xs shrink-0 shadow-sm">
                  {(user.fullName || user.email || 'U').charAt(0).toUpperCase()}
                </div>
                <div className="min-w-0">
                  <p className="text-xs font-semibold text-[#F8FAFC] truncate">
                    {user.fullName || user.email}
                  </p>
                  <span className={`inline-block px-1.5 py-0.2 rounded-full border text-[9px] font-bold uppercase tracking-wider ${getRoleBadge(user.role).color}`}>
                    {getRoleBadge(user.role).label}
                  </span>
                </div>
              </div>
              <button
                onClick={handleLogout}
                className="p-1.5 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 transition-colors"
                title="Sign Out"
                aria-label="Sign Out"
              >
                <LogOut className="w-4 h-4" />
              </button>
            </div>
          </div>
        ) : (
          <div className="p-4 border-t border-[#263449] bg-[#0A0F1D]">
            <div className="flex items-center gap-2 text-xs text-slate-400">
              <Shield className="w-3.5 h-3.5 text-blue-400" />
              <span>Secure JWT Architecture</span>
            </div>
          </div>
        )}
      </aside>
    </>
  );
}
