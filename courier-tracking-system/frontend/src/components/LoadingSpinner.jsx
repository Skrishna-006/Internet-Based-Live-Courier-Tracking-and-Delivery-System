import React from 'react';
import { Loader2 } from 'lucide-react';

export default function LoadingSpinner({
  size = 'md', // 'sm' | 'md' | 'lg'
  label,
  className = '',
}) {
  const sizeMap = {
    sm: 'w-4 h-4',
    md: 'w-6 h-6',
    lg: 'w-10 h-10',
  };

  return (
    <div className={`flex flex-col items-center justify-center gap-3 p-6 text-slate-400 ${className}`}>
      <Loader2 className={`${sizeMap[size] || sizeMap.md} animate-spin text-blue-500`} />
      {label && <p className="text-xs font-medium text-slate-400">{label}</p>}
    </div>
  );
}
