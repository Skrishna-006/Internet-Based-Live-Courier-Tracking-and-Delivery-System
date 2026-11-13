import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, Compass } from 'lucide-react';

export default function NotFoundPage() {
  return (
    <div className="min-h-[60vh] flex flex-col items-center justify-center text-center p-6 space-y-4 text-slate-100">
      <div className="w-16 h-16 bg-navy-850 border border-navy-700/60 rounded-full flex items-center justify-center text-accent-blue shadow-dark-card">
        <Compass className="w-8 h-8 animate-spin" />
      </div>
      <h1 className="text-3xl font-bold text-slate-100">404 — Page Not Found</h1>
      <p className="text-slate-400 max-w-md">
        The requested logistics route or endpoint does not exist or has been relocated.
      </p>
      <Link
        to="/"
        className="inline-flex items-center gap-2 bg-accent-blue text-white px-4 py-2 rounded-xl font-medium hover:bg-blue-600 transition-colors shadow-sm"
      >
        <ArrowLeft className="w-4 h-4" />
        Return to Foundation Overview
      </Link>
    </div>
  );
}
