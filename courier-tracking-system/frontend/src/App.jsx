import React from 'react';
import { Routes, Route, Link } from 'react-router-dom';
import { Package, MapPin, Shield, Layers } from 'lucide-react';
import LandingPage from './pages/LandingPage';
import NotFoundPage from './pages/NotFoundPage';

export default function App() {
  return (
    <div className="min-h-screen flex flex-col bg-slate-50 text-slate-800">
      {/* Navigation Header */}
      <header className="sticky top-0 z-50 bg-white/80 backdrop-blur-md border-b border-slate-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
          <Link to="/" className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-blue-600 to-indigo-600 text-white flex items-center justify-center shadow-sm">
              <Package className="w-5 h-5" />
            </div>
            <div>
              <span className="font-bold text-base text-slate-900 tracking-tight block leading-tight">
                SwiftTrack Courier
              </span>
              <span className="text-[10px] text-slate-500 font-medium tracking-wide uppercase">
                Live Tracking Platform
              </span>
            </div>
          </Link>

          <nav className="flex items-center gap-4 text-xs font-semibold text-slate-600">
            <span className="hidden sm:inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-slate-100 text-slate-600 border border-slate-200">
              <Layers className="w-3.5 h-3.5 text-blue-500" />
              Phase 1 Baseline
            </span>
          </nav>
        </div>
      </header>

      {/* Main Content View */}
      <main className="flex-1 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 w-full">
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-200 bg-white py-6">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-slate-500">
          <p>© 2026 Internet-Based Live Courier Tracking and Delivery System. All rights reserved.</p>
          <div className="flex items-center gap-4">
            <span className="inline-flex items-center gap-1">
              <Shield className="w-3.5 h-3.5 text-slate-400" />
              Role-Based Security
            </span>
            <span>•</span>
            <span className="inline-flex items-center gap-1">
              <MapPin className="w-3.5 h-3.5 text-slate-400" />
              Live Telemetry
            </span>
          </div>
        </div>
      </footer>
    </div>
  );
}
