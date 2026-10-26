import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, Compass } from 'lucide-react';

export default function NotFoundPage() {
  return (
    <div className="min-h-[60vh] flex flex-col items-center justify-center text-center p-6 space-y-4">
      <div className="w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center text-slate-500">
        <Compass className="w-8 h-8 animate-spin" />
      </div>
      <h1 className="text-3xl font-bold text-slate-900">404 — Page Not Found</h1>
      <p className="text-slate-600 max-w-md">
        The requested logistics route or endpoint does not exist or has been relocated.
      </p>
      <Link
        to="/"
        className="inline-flex items-center gap-2 bg-blue-600 text-white px-4 py-2 rounded-lg font-medium hover:bg-blue-700 transition-colors"
      >
        <ArrowLeft className="w-4 h-4" />
        Return to Foundation Overview
      </Link>
    </div>
  );
}
