import React, { useState, useEffect } from 'react';
import { Package, Truck, ShieldCheck, Activity, CheckCircle2, AlertCircle, ArrowRight, RefreshCw, Radio } from 'lucide-react';
import { systemService } from '../services/api';

export default function LandingPage() {
  const [healthData, setHealthData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const checkBackendHealth = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await systemService.getHealth();
      setHealthData(response.data);
    } catch (err) {
      setError(err.message || 'Could not communicate with Spring Boot backend');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    checkBackendHealth();
  }, []);

  return (
    <div className="space-y-12 pb-16">
      {/* Hero Section */}
      <section className="text-center space-y-4 pt-6 max-w-3xl mx-auto">
        <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-blue-50 border border-blue-200 text-blue-700 text-xs font-semibold uppercase tracking-wider">
          <Activity className="w-3.5 h-3.5 animate-pulse text-blue-600" />
          Phase 1 — Architecture & Project Foundation
        </div>
        <h1 className="text-4xl sm:text-5xl font-extrabold text-slate-900 tracking-tight">
          Internet-Based Live Courier Tracking & Delivery System
        </h1>
        <p className="text-lg text-slate-600 leading-relaxed">
          High-performance, event-driven logistics platform featuring real-time GPS telemetry,
          state-machine consignment tracking, and role-governed dispatching.
        </p>
      </section>

      {/* Backend Connectivity Status Widget */}
      <section className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 max-w-2xl mx-auto">
        <div className="flex items-center justify-between border-b border-slate-100 pb-4 mb-4">
          <div className="flex items-center gap-3">
            <Radio className="w-5 h-5 text-blue-600" />
            <h2 className="text-base font-bold text-slate-800">Backend Service Diagnostic</h2>
          </div>
          <button
            onClick={checkBackendHealth}
            disabled={loading}
            className="inline-flex items-center gap-1.5 text-xs font-medium text-slate-600 hover:text-blue-600 bg-slate-100 hover:bg-blue-50 px-3 py-1.5 rounded-lg transition-colors disabled:opacity-50"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            Refresh Probe
          </button>
        </div>

        {loading ? (
          <div className="flex items-center justify-center py-6 text-slate-500 text-sm">
            <RefreshCw className="w-4 h-4 animate-spin mr-2 text-blue-500" />
            Connecting to Spring Boot REST endpoint (/api/v1/health)...
          </div>
        ) : healthData ? (
          <div className="space-y-3">
            <div className="flex items-center justify-between p-3 bg-emerald-50 border border-emerald-200 rounded-xl">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="w-5 h-5 text-emerald-600" />
                <span className="font-semibold text-emerald-900 text-sm">Backend Connected & Responsive</span>
              </div>
              <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-200 text-emerald-800">
                {healthData.status}
              </span>
            </div>
            <div className="grid grid-cols-2 gap-2 text-xs text-slate-600 pt-1">
              <div className="bg-slate-50 p-2.5 rounded-lg border border-slate-100">
                <span className="font-semibold text-slate-700 block">Service Name</span>
                {healthData.systemName}
              </div>
              <div className="bg-slate-50 p-2.5 rounded-lg border border-slate-100">
                <span className="font-semibold text-slate-700 block">Database Status</span>
                {healthData.components?.database?.status || 'NOT CONFIGURED'}
              </div>
            </div>
          </div>
        ) : (
          <div className="p-4 bg-amber-50 border border-amber-200 rounded-xl space-y-2">
            <div className="flex items-center gap-2 text-amber-800 font-semibold text-sm">
              <AlertCircle className="w-4 h-4 text-amber-600" />
              <span>Backend Server Offline or Standby</span>
            </div>
            <p className="text-xs text-amber-700">
              Run <code className="bg-amber-100 px-1 py-0.5 rounded text-amber-900 font-mono">mvn spring-boot:run</code> inside the <code className="font-mono">backend</code> directory to boot the service.
            </p>
          </div>
        )}
      </section>

      {/* Role Architecture Cards */}
      <section className="grid md:grid-cols-3 gap-6 max-w-5xl mx-auto">
        <div className="bg-white rounded-xl border border-slate-200 p-6 space-y-3 shadow-sm hover:border-blue-300 transition-all">
          <div className="w-10 h-10 rounded-lg bg-blue-100 text-blue-600 flex items-center justify-center">
            <Package className="w-5 h-5" />
          </div>
          <h3 className="text-lg font-bold text-slate-900">Customer Portal</h3>
          <p className="text-sm text-slate-600 leading-relaxed">
            Consignment booking, instant waybill generation, public tracking lookup with privacy masking, and live milestone updates.
          </p>
          <div className="pt-2 text-xs font-semibold text-blue-600 inline-flex items-center gap-1">
            Planned for Phase 4 <ArrowRight className="w-3 h-3" />
          </div>
        </div>

        <div className="bg-white rounded-xl border border-slate-200 p-6 space-y-3 shadow-sm hover:border-emerald-300 transition-all">
          <div className="w-10 h-10 rounded-lg bg-emerald-100 text-emerald-600 flex items-center justify-center">
            <Truck className="w-5 h-5" />
          </div>
          <h3 className="text-lg font-bold text-slate-900">Delivery Agent Mode</h3>
          <p className="text-sm text-slate-600 leading-relaxed">
            Mobile-optimized dispatch run, throttled GPS location broadcasting, proof-of-delivery (OTP/Signature) validation.
          </p>
          <div className="pt-2 text-xs font-semibold text-emerald-600 inline-flex items-center gap-1">
            Planned for Phase 5 <ArrowRight className="w-3 h-3" />
          </div>
        </div>

        <div className="bg-white rounded-xl border border-slate-200 p-6 space-y-3 shadow-sm hover:border-purple-300 transition-all">
          <div className="w-10 h-10 rounded-lg bg-purple-100 text-purple-600 flex items-center justify-center">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <h3 className="text-lg font-bold text-slate-900">Dispatch & Admin Control</h3>
          <p className="text-sm text-slate-600 leading-relaxed">
            Live fleet map, manual consignment reassignments, comprehensive audit logs, and branch-level analytics.
          </p>
          <div className="pt-2 text-xs font-semibold text-purple-600 inline-flex items-center gap-1">
            Planned for Phase 6 <ArrowRight className="w-3 h-3" />
          </div>
        </div>
      </section>

      {/* Engineering Foundations Matrix */}
      <section className="bg-slate-900 text-white rounded-2xl p-8 max-w-5xl mx-auto space-y-6">
        <div>
          <h2 className="text-xl font-bold">Phase 1 Foundation Checklist</h2>
          <p className="text-slate-400 text-sm">Architectural guarantees strictly verified and established.</p>
        </div>
        <div className="grid sm:grid-cols-2 gap-4 text-sm">
          <div className="flex items-start gap-3 bg-slate-800/80 p-4 rounded-xl border border-slate-700">
            <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
            <div>
              <span className="font-semibold block text-slate-100">Layered Modular Monolith</span>
              <span className="text-slate-400 text-xs">Strict separation of controllers, services, repositories, DTOs, and error handlers.</span>
            </div>
          </div>
          <div className="flex items-start gap-3 bg-slate-800/80 p-4 rounded-xl border border-slate-700">
            <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
            <div>
              <span className="font-semibold block text-slate-100">Spring Boot 3.4 & Java 21</span>
              <span className="text-slate-400 text-xs">Tested bytecode compilation using modern release 21 target with JUnit 5.</span>
            </div>
          </div>
          <div className="flex items-start gap-3 bg-slate-800/80 p-4 rounded-xl border border-slate-700">
            <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
            <div>
              <span className="font-semibold block text-slate-100">Flyway Database Versioning</span>
              <span className="text-slate-400 text-xs">Immutable SQL migrations preventing unpredictable hibernate auto-mutations.</span>
            </div>
          </div>
          <div className="flex items-start gap-3 bg-slate-800/80 p-4 rounded-xl border border-slate-700">
            <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
            <div>
              <span className="font-semibold block text-slate-100">React + Vite + Tailwind CSS</span>
              <span className="text-slate-400 text-xs">High-speed development HMR, reverse-proxy routing, and zero coupled logic.</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
