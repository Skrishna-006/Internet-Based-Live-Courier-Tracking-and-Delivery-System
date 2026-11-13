import React from 'react';

/**
 * Premium dark theme metric/summary card.
 */
export default function DashboardCard({
  title,
  value,
  subtitle,
  icon: Icon,
  accentColor = 'blue', // 'blue' | 'cyan' | 'emerald' | 'amber' | 'purple' | 'rose'
  onClick,
  badge,
  className = '',
}) {
  const colorMap = {
    blue: {
      bg: 'bg-blue-500/10',
      text: 'text-blue-400',
      border: 'border-blue-500/20',
      hoverBorder: 'hover:border-blue-500/40',
      glow: 'group-hover:shadow-glow-blue',
    },
    cyan: {
      bg: 'bg-cyan-500/10',
      text: 'text-cyan-400',
      border: 'border-cyan-500/20',
      hoverBorder: 'hover:border-cyan-500/40',
      glow: 'group-hover:shadow-glow-cyan',
    },
    emerald: {
      bg: 'bg-emerald-500/10',
      text: 'text-emerald-400',
      border: 'border-emerald-500/20',
      hoverBorder: 'hover:border-emerald-500/40',
      glow: '',
    },
    amber: {
      bg: 'bg-amber-500/10',
      text: 'text-amber-400',
      border: 'border-amber-500/20',
      hoverBorder: 'hover:border-amber-500/40',
      glow: '',
    },
    purple: {
      bg: 'bg-purple-500/10',
      text: 'text-purple-400',
      border: 'border-purple-500/20',
      hoverBorder: 'hover:border-purple-500/40',
      glow: '',
    },
    rose: {
      bg: 'bg-rose-500/10',
      text: 'text-rose-400',
      border: 'border-rose-500/20',
      hoverBorder: 'hover:border-rose-500/40',
      glow: '',
    },
  };

  const scheme = colorMap[accentColor] || colorMap.blue;

  return (
    <div
      onClick={onClick}
      className={`group relative overflow-hidden bg-[#121C2E] border border-[#263449] ${scheme.hoverBorder} rounded-2xl p-5 transition-all duration-300 shadow-dark-card ${onClick ? 'cursor-pointer hover:-translate-y-0.5' : ''} ${className}`}
    >
      {/* Top subtle highlight bar */}
      <div className="flex items-start justify-between gap-3">
        <div className="space-y-1">
          <p className="text-xs font-medium text-slate-400 uppercase tracking-wider">{title}</p>
          <div className="text-2xl sm:text-3xl font-extrabold text-[#F8FAFC] tracking-tight">
            {value}
          </div>
          {subtitle && (
            <p className="text-xs text-slate-400 font-medium">{subtitle}</p>
          )}
        </div>

        {Icon && (
          <div className={`p-3 rounded-xl ${scheme.bg} ${scheme.text} border ${scheme.border} transition-transform duration-300 group-hover:scale-105`}>
            <Icon className="w-5 h-5" />
          </div>
        )}
      </div>

      {badge && (
        <div className="mt-3 pt-3 border-t border-[#263449]/60 flex items-center justify-between text-xs">
          {badge}
        </div>
      )}
    </div>
  );
}
