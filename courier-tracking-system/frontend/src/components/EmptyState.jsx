import React from 'react';
import { Package } from 'lucide-react';

export default function EmptyState({
  icon: Icon = Package,
  title = 'No records found',
  description = 'There is currently no data matching your criteria.',
  action,
  className = '',
}) {
  return (
    <div className={`flex flex-col items-center justify-center p-8 sm:p-12 text-center rounded-2xl bg-[#121C2E]/60 border border-[#263449] border-dashed space-y-4 ${className}`}>
      <div className="w-14 h-14 rounded-2xl bg-[#18243A] border border-[#263449] text-blue-400 flex items-center justify-center shadow-inner">
        <Icon className="w-7 h-7" />
      </div>
      <div className="space-y-1.5 max-w-sm">
        <h3 className="text-base font-bold text-[#F8FAFC] tracking-tight">{title}</h3>
        <p className="text-xs text-slate-400 leading-relaxed">{description}</p>
      </div>
      {action && (
        <div className="pt-2">
          {action}
        </div>
      )}
    </div>
  );
}
