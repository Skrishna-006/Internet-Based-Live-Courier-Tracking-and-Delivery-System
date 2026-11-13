import React from 'react';
import { useAuth } from '../context/AuthContext';
import { Package, Truck, ShieldCheck, CheckCircle2, Link as LinkIcon } from 'lucide-react';
import { Link } from 'react-router-dom';

export default function PlaceholderDashboard({ portalType, title, description, plannedFeature }) {
  const { user } = useAuth();

  const getIcon = () => {
    switch (portalType) {
      case 'driver':
        return <Truck className="w-8 h-8 text-emerald-600" />;
      case 'admin':
        return <ShieldCheck className="w-8 h-8 text-purple-600" />;
      default:
        return <Package className="w-8 h-8 text-blue-600" />;
    }
  };

  const getBadgeColor = () => {
    switch (portalType) {
      case 'driver':
        return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30';
      case 'admin':
        return 'bg-purple-500/10 text-purple-400 border-purple-500/30';
      default:
        return 'bg-blue-500/10 text-accent-cyan border-blue-500/30';
    }
  };

  return (
    <div className="max-w-3xl mx-auto py-12 px-4 space-y-6 text-slate-100">
      <div className="bg-navy-850 rounded-3xl border border-navy-700/60 p-8 shadow-dark-card space-y-6">
        <div className="flex items-center gap-4 border-b border-navy-700/60 pb-6">
          <div className="w-14 h-14 rounded-2xl bg-navy-900 border border-navy-700/60 flex items-center justify-center shrink-0">
            {getIcon()}
          </div>
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className={`px-2.5 py-0.5 rounded-full border text-[10px] font-bold uppercase tracking-wider ${getBadgeColor()}`}>
                {user?.role?.replace('ROLE_', '') || 'AUTHENTICATED'}
              </span>
              <span className="text-xs text-slate-400 font-mono">ID #{user?.id || '—'}</span>
            </div>
            <h1 className="text-2xl font-black text-slate-100 tracking-tight">{title}</h1>
          </div>
        </div>

        <div className="space-y-4">
          <p className="text-sm text-slate-300 leading-relaxed">{description}</p>

          <div className="p-4 bg-navy-900/80 rounded-2xl border border-navy-700/60 space-y-2">
            <div className="flex items-center gap-2 text-xs font-bold text-slate-200">
              <CheckCircle2 className="w-4 h-4 text-emerald-400" />
              Role Authorization Verified: {user?.role}
            </div>
            <p className="text-xs text-slate-400">
              Authenticated user: <span className="font-semibold text-slate-200">{user?.fullName}</span> ({user?.email}).
              Full interactive dashboard components are scheduled in <span className="font-semibold text-accent-cyan">{plannedFeature}</span>.
            </p>
          </div>

          <div className="flex items-center gap-4 pt-2">
            {portalType === 'admin' && (
              <Link
                to="/admin/fleet"
                className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-accent-blue hover:bg-blue-600 text-white text-xs font-semibold shadow-sm transition-colors"
                data-testid="admin-open-fleet-map-btn"
              >
                <Truck className="w-3.5 h-3.5" />
                Open Live Fleet Map
              </Link>
            )}
            <Link
              to="/track"
              className="inline-flex items-center gap-1.5 text-xs font-semibold text-accent-cyan hover:text-cyan-300"
            >
              <LinkIcon className="w-3.5 h-3.5" />
              Test Live Tracking Map
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
