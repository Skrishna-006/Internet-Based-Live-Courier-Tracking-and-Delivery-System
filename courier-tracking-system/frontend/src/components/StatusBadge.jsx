import React from 'react';

/**
 * Standardized status badge for consignments across all dark dashboard views.
 */
export function getStatusTheme(status) {
  switch (status) {
    case 'DELIVERED':
      return {
        label: 'Delivered',
        bg: 'bg-emerald-500/10',
        text: 'text-emerald-400',
        border: 'border-emerald-500/30',
        dot: 'bg-emerald-400',
      };
    case 'OUT_FOR_DELIVERY':
      return {
        label: 'Out for Delivery',
        bg: 'bg-blue-500/10',
        text: 'text-blue-400',
        border: 'border-blue-500/30',
        dot: 'bg-blue-400 animate-pulse',
      };
    case 'IN_TRANSIT':
      return {
        label: 'In Transit',
        bg: 'bg-indigo-500/10',
        text: 'text-indigo-400',
        border: 'border-indigo-500/30',
        dot: 'bg-indigo-400',
      };
    case 'PICKED_UP':
      return {
        label: 'Picked Up',
        bg: 'bg-cyan-500/10',
        text: 'text-cyan-400',
        border: 'border-cyan-500/30',
        dot: 'bg-cyan-400',
      };
    case 'ASSIGNED':
      return {
        label: 'Assigned',
        bg: 'bg-purple-500/10',
        text: 'text-purple-400',
        border: 'border-purple-500/30',
        dot: 'bg-purple-400',
      };
    case 'CREATED':
      return {
        label: 'Created',
        bg: 'bg-slate-500/10',
        text: 'text-slate-300',
        border: 'border-slate-500/30',
        dot: 'bg-slate-400',
      };
    case 'ATTEMPT_FAILED':
      return {
        label: 'Attempt Failed',
        bg: 'bg-amber-500/10',
        text: 'text-amber-400',
        border: 'border-amber-500/30',
        dot: 'bg-amber-400',
      };
    case 'RETURNED':
      return {
        label: 'Returned',
        bg: 'bg-orange-500/10',
        text: 'text-orange-400',
        border: 'border-orange-500/30',
        dot: 'bg-orange-400',
      };
    case 'CANCELLED':
      return {
        label: 'Cancelled',
        bg: 'bg-rose-500/10',
        text: 'text-rose-400',
        border: 'border-rose-500/30',
        dot: 'bg-rose-400',
      };
    default:
      return {
        label: status || 'Unknown',
        bg: 'bg-slate-800',
        text: 'text-slate-300',
        border: 'border-slate-700',
        dot: 'bg-slate-500',
      };
  }
}

export default function StatusBadge({ status, className = '' }) {
  const theme = getStatusTheme(status);

  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold border ${theme.bg} ${theme.text} ${theme.border} ${className}`}
    >
      <span className={`w-1.5 h-1.5 rounded-full ${theme.dot}`} />
      <span>{theme.label}</span>
    </span>
  );
}
