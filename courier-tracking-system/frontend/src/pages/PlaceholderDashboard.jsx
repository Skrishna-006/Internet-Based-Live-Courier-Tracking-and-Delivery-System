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
        return 'bg-emerald-50 text-emerald-700 border-emerald-200';
      case 'admin':
        return 'bg-purple-50 text-purple-700 border-purple-200';
      default:
        return 'bg-blue-50 text-blue-700 border-blue-200';
    }
  };

  return (
    <div className="max-w-3xl mx-auto py-12 px-4 space-y-6">
      <div className="bg-white rounded-3xl border border-slate-200 p-8 shadow-sm space-y-6">
        <div className="flex items-center gap-4 border-b border-slate-100 pb-6">
          <div className="w-14 h-14 rounded-2xl bg-slate-50 border border-slate-200 flex items-center justify-center shrink-0">
            {getIcon()}
          </div>
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className={`px-2.5 py-0.5 rounded-full border text-[10px] font-bold uppercase tracking-wider ${getBadgeColor()}`}>
                {user?.role?.replace('ROLE_', '') || 'AUTHENTICATED'}
              </span>
              <span className="text-xs text-slate-400 font-mono">ID #{user?.id || '—'}</span>
            </div>
            <h1 className="text-2xl font-black text-slate-900 tracking-tight">{title}</h1>
          </div>
        </div>

        <div className="space-y-4">
          <p className="text-sm text-slate-600 leading-relaxed">{description}</p>

          <div className="p-4 bg-slate-50 rounded-2xl border border-slate-200/80 space-y-2">
            <div className="flex items-center gap-2 text-xs font-bold text-slate-800">
              <CheckCircle2 className="w-4 h-4 text-emerald-500" />
              Role Authorization Verified: {user?.role}
            </div>
            <p className="text-xs text-slate-500">
              Authenticated user: <span className="font-semibold text-slate-700">{user?.fullName}</span> ({user?.email}).
              Full interactive dashboard components are scheduled in <span className="font-semibold text-indigo-600">{plannedFeature}</span>.
            </p>
          </div>

          <div className="flex items-center gap-4 pt-2">
            <Link
              to="/track"
              className="inline-flex items-center gap-1.5 text-xs font-semibold text-indigo-600 hover:text-indigo-800"
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
