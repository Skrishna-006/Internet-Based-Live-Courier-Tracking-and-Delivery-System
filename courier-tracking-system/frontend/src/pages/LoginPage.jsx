import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { Lock, Mail, AlertCircle, ArrowRight, Package, Loader2 } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    // Client-side validations
    if (!email.trim()) {
      setError('Email address is required');
      return;
    }
    if (!password) {
      setError('Password is required');
      return;
    }

    setLoading(true);

    try {
      const result = await login(email.trim(), password);
      const userRole = result?.user?.role;

      // Check if redirected from a protected route
      const fromPath = location.state?.from?.pathname;
      if (fromPath && fromPath !== '/login' && fromPath !== '/register' && fromPath !== '/unauthorized') {
        navigate(fromPath, { replace: true });
        return;
      }

      // Default role-based redirection
      if (userRole === 'ROLE_ADMIN') {
        navigate('/admin/dashboard', { replace: true });
      } else if (userRole === 'ROLE_DELIVERY_AGENT') {
        navigate('/driver/dashboard', { replace: true });
      } else {
        navigate('/customer/dashboard', { replace: true });
      }
    } catch (err) {
      setError(err?.message || 'Authentication failed. Please verify your credentials.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex flex-col items-center justify-center min-h-[70vh] py-8 px-4">
      <div className="w-full max-w-md bg-white rounded-3xl border border-slate-200 shadow-xl p-8 space-y-6">
        {/* Header */}
        <div className="text-center space-y-2">
          <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-blue-600 to-indigo-600 text-white flex items-center justify-center mx-auto shadow-md">
            <Package className="w-6 h-6" />
          </div>
          <h1 className="text-2xl font-black text-slate-900 tracking-tight">
            Sign In to SwiftTrack
          </h1>
          <p className="text-xs text-slate-500">
            Access your customer bookings, driver assignments, or dispatcher dashboard.
          </p>
        </div>

        {/* Error Alert */}
        {error && (
          <div
            data-testid="login-error-alert"
            className="p-3.5 bg-rose-50 border border-rose-200 rounded-xl flex items-start gap-2.5 text-xs text-rose-800"
          >
            <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
            <span className="font-medium">{error}</span>
          </div>
        )}

        {/* Login Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-700 block">
              Email Address
            </label>
            <div className="relative flex items-center">
              <Mail className="absolute left-3.5 w-4 h-4 text-slate-400 pointer-events-none" />
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="name@example.com"
                autoComplete="email"
                required
                className="w-full pl-10 pr-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 text-slate-900 text-xs focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500 transition-all font-medium"
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <label className="text-xs font-semibold text-slate-700 block">
                Password
              </label>
            </div>
            <div className="relative flex items-center">
              <Lock className="absolute left-3.5 w-4 h-4 text-slate-400 pointer-events-none" />
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                autoComplete="current-password"
                required
                className="w-full pl-10 pr-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 text-slate-900 text-xs focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500 transition-all font-medium"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-3 px-4 rounded-xl bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 text-white text-xs font-bold transition-all shadow-md flex items-center justify-center gap-2 disabled:opacity-50"
          >
            {loading ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                Signing in...
              </>
            ) : (
              <>
                Sign In <ArrowRight className="w-4 h-4" />
              </>
            )}
          </button>
        </form>

        {/* Footer Navigation Links */}
        <div className="pt-2 border-t border-slate-100 flex flex-col items-center gap-2 text-xs text-slate-500">
          <div>
            Don't have an account?{' '}
            <Link to="/register" className="font-semibold text-indigo-600 hover:text-indigo-800">
              Create customer account
            </Link>
          </div>
          <Link to="/track" className="text-slate-400 hover:text-slate-600 text-[11px]">
            Looking for public tracking? Track without signing in &rarr;
          </Link>
        </div>
      </div>
    </div>
  );
}
