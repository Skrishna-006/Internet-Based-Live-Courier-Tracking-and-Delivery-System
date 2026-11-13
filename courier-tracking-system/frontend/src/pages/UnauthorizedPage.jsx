import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ShieldAlert, ArrowLeft, LogOut } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export default function UnauthorizedPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleSwitchAccount = () => {
    logout();
    navigate('/login');
  };

  const formatRoleName = (role) => {
    if (!role) return 'Unauthenticated';
    return role.replace('ROLE_', '').replace('_', ' ');
  };

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] text-center px-4 text-slate-100">
      <div className="w-16 h-16 rounded-2xl bg-amber-500/10 text-amber-400 flex items-center justify-center mb-4 border border-amber-500/30 shadow-dark-card">
        <ShieldAlert className="w-8 h-8" />
      </div>

      <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-100 tracking-tight mb-2">
        Access Restricted (403)
      </h1>

      <p className="text-sm text-slate-400 max-w-md mb-6 leading-relaxed">
        You do not have the required role permissions to access this requested resource.
        {user && (
          <span className="block mt-2 font-medium text-slate-300">
            Current Role: <span className="px-2.5 py-0.5 rounded-full bg-navy-800 border border-navy-700 text-xs font-bold text-accent-cyan">{formatRoleName(user.role)}</span>
          </span>
        )}
      </p>

      <div className="flex flex-wrap items-center justify-center gap-3">
        <Link
          to="/"
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-navy-800 hover:bg-navy-700 border border-navy-700 text-slate-200 text-xs font-semibold transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          Back to Home
        </Link>
        <button
          onClick={handleSwitchAccount}
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-accent-blue hover:bg-blue-600 text-white text-xs font-semibold transition-colors shadow-sm"
        >
          <LogOut className="w-4 h-4" />
          Switch Account
        </button>
      </div>
    </div>
  );
}
