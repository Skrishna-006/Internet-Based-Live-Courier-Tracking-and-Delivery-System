import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Package,
  Truck,
  ShieldCheck,
  Activity,
  CheckCircle2,
  AlertCircle,
  ArrowRight,
  RefreshCw,
  Radio,
  Search,
} from 'lucide-react';
import { systemService } from '../services/api';

export default function LandingPage() {
  const [healthData, setHealthData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [trackingInput, setTrackingInput] = useState('');
  const navigate = useNavigate();

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

  const handleTrackSubmit = (e) => {
    e.preventDefault();
    if (trackingInput.trim()) {
      navigate(`/track/${encodeURIComponent(trackingInput.trim().toUpperCase())}`);
    }
  };

  return (
    <div className="space-y-12 pb-16 text-slate-100">
      {/* Hero Section */}
      <section className="text-center space-y-4 pt-6 max-w-3xl mx-auto">
        <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-blue-500/10 border border-blue-500/30 text-accent-cyan text-xs font-semibold uppercase tracking-wider">
          <Activity className="w-3.5 h-3.5 animate-pulse text-accent-blue" />
          Live Telemetry & Tracking Platform
        </div>
        <h1 className="text-4xl sm:text-5xl font-extrabold text-slate-100 tracking-tight">
          Internet-Based Live Courier Tracking & Delivery System
        </h1>
        <p className="text-lg text-slate-400 leading-relaxed">
          High-performance, event-driven logistics platform featuring real-time GPS telemetry,
          state-machine consignment tracking, and role-governed dispatching.
        </p>

        {/* Hero Quick Tracking Form */}
        <div className="pt-4 max-w-lg mx-auto">
          <form onSubmit={handleTrackSubmit} className="relative flex items-center shadow-dark-card rounded-2xl">
            <Search className="absolute left-4 w-5 h-5 text-slate-500 pointer-events-none" />
            <input
              type="text"
              value={trackingInput}
              onChange={(e) => setTrackingInput(e.target.value)}
              placeholder="Track package (e.g. TRK-20261026-ABCD)"
              className="w-full pl-12 pr-28 py-4 rounded-2xl bg-navy-850 text-slate-100 placeholder-slate-500 font-medium text-sm border border-navy-700 focus:outline-none focus:ring-2 focus:ring-accent-blue/30 focus:border-accent-blue"
            />
            <button
              type="submit"
              className="absolute right-2.5 px-5 py-2.5 rounded-xl bg-accent-blue hover:bg-blue-600 text-white text-xs font-bold transition-colors shadow-sm"
            >
              Track Live
            </button>
          </form>
        </div>
      </section>

      {/* Backend Connectivity Status Widget */}
      <section className="bg-navy-850 rounded-2xl border border-navy-700/60 shadow-dark-card p-6 max-w-2xl mx-auto">
        <div className="flex items-center justify-between border-b border-navy-700/60 pb-4 mb-4">
          <div className="flex items-center gap-3">
            <Radio className="w-5 h-5 text-accent-blue" />
            <h2 className="text-base font-bold text-slate-100">Backend Service Diagnostic</h2>
          </div>
          <button
            onClick={checkBackendHealth}
            disabled={loading}
            className="inline-flex items-center gap-1.5 text-xs font-medium text-slate-300 hover:text-white bg-navy-800 hover:bg-navy-700 border border-navy-700 px-3 py-1.5 rounded-lg transition-colors disabled:opacity-50"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            Refresh Probe
          </button>
        </div>

        {loading ? (
          <div className="flex items-center justify-center py-6 text-slate-400 text-sm">
            <RefreshCw className="w-4 h-4 animate-spin mr-2 text-accent-blue" />
            Connecting to Spring Boot REST endpoint (/api/v1/health)...
          </div>
        ) : healthData ? (
          <div className="space-y-3">
            <div className="flex items-center justify-between p-3 bg-emerald-950/30 border border-emerald-500/40 rounded-xl">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="w-5 h-5 text-emerald-400" />
                <span className="font-semibold text-emerald-300 text-sm">Backend Connected & Responsive</span>
              </div>
              <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                {healthData.status}
              </span>
            </div>
            <div className="grid grid-cols-2 gap-2 text-xs text-slate-300 pt-1">
              <div className="bg-navy-900/80 p-2.5 rounded-lg border border-navy-700/50">
                <span className="font-semibold text-slate-400 block">Service Name</span>
                {healthData.systemName}
              </div>
              <div className="bg-navy-900/80 p-2.5 rounded-lg border border-navy-700/50">
                <span className="font-semibold text-slate-400 block">Database Status</span>
                {healthData.components?.database?.status || 'NOT CONFIGURED'}
              </div>
            </div>
          </div>
        ) : (
          <div className="p-4 bg-amber-950/30 border border-amber-500/40 rounded-xl space-y-2">
            <div className="flex items-center gap-2 text-amber-300 font-semibold text-sm">
              <AlertCircle className="w-4 h-4 text-amber-400" />
              <span>Backend Server Offline or Standby</span>
            </div>
            <p className="text-xs text-amber-400/90">
              Run <code className="bg-navy-900 px-1 py-0.5 rounded text-amber-300 font-mono">mvn spring-boot:run</code> inside the <code className="font-mono">backend</code> directory to boot the service.
            </p>
          </div>
        )}
      </section>

      {/* Role Architecture Cards */}
      <section className="grid md:grid-cols-3 gap-6 max-w-5xl mx-auto">
        <Link
          to="/track"
          className="bg-navy-850 rounded-xl border border-navy-700/60 p-6 space-y-3 shadow-dark-card hover:border-accent-blue/50 transition-all group block"
        >
          <div className="w-10 h-10 rounded-lg bg-blue-500/10 text-accent-blue border border-blue-500/20 flex items-center justify-center">
            <Package className="w-5 h-5" />
          </div>
          <h3 className="text-lg font-bold text-slate-100 group-hover:text-accent-blue transition-colors">
            Live Consignment Tracking
          </h3>
          <p className="text-sm text-slate-400 leading-relaxed">
            Consignment booking, instant waybill generation, public tracking lookup with privacy masking, and live milestone updates.
          </p>
          <div className="pt-2 text-xs font-semibold text-accent-cyan inline-flex items-center gap-1">
            Open Live Map <ArrowRight className="w-3 h-3 group-hover:translate-x-1 transition-transform" />
          </div>
        </Link>

        <div className="bg-navy-850 rounded-xl border border-navy-700/60 p-6 space-y-3 shadow-dark-card hover:border-emerald-500/40 transition-all">
          <div className="w-10 h-10 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 flex items-center justify-center">
            <Truck className="w-5 h-5" />
          </div>
          <h3 className="text-lg font-bold text-slate-100">Delivery Agent Mode</h3>
          <p className="text-sm text-slate-400 leading-relaxed">
            Mobile-optimized dispatch run, throttled GPS location broadcasting, proof-of-delivery (OTP/Signature) validation.
          </p>
          <div className="pt-2 text-xs font-semibold text-emerald-400 inline-flex items-center gap-1">
            Active Feature <CheckCircle2 className="w-3 h-3" />
          </div>
        </div>

        <div className="bg-navy-850 rounded-xl border border-navy-700/60 p-6 space-y-3 shadow-dark-card hover:border-accent-cyan/40 transition-all">
          <div className="w-10 h-10 rounded-lg bg-cyan-500/10 text-accent-cyan border border-cyan-500/20 flex items-center justify-center">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <h3 className="text-lg font-bold text-slate-100">Dispatch & Admin Control</h3>
          <p className="text-sm text-slate-400 leading-relaxed">
            Live fleet map, manual consignment reassignments, comprehensive audit logs, and branch-level analytics.
          </p>
          <div className="pt-2 text-xs font-semibold text-accent-cyan inline-flex items-center gap-1">
            Active Hub <ArrowRight className="w-3 h-3" />
          </div>
        </div>
      </section>

      {/* Engineering Foundations Matrix */}
      <section className="bg-navy-900 border border-navy-700/70 shadow-dark-card text-white rounded-2xl p-8 max-w-5xl mx-auto space-y-6">
        <div>
          <h2 className="text-xl font-bold text-slate-100">Real-Time Telemetry & Tracking Infrastructure</h2>
          <p className="text-slate-400 text-sm">Leaflet OpenStreetMap, STOMP WebSockets, and polling fallback verified.</p>
        </div>
        <div className="grid sm:grid-cols-2 gap-4 text-sm">
          <div className="flex items-start gap-3 bg-navy-850 p-4 rounded-xl border border-navy-700/60">
            <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
            <div>
              <span className="font-semibold block text-slate-100">Leaflet & OpenStreetMap Visualization</span>
              <span className="text-slate-400 text-xs">Smooth vector markers, directional bearing indicator, and responsive container.</span>
            </div>
          </div>
          <div className="flex items-start gap-3 bg-navy-850 p-4 rounded-xl border border-navy-700/60">
            <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
            <div>
              <span className="font-semibold block text-slate-100">STOMP Real-Time Broadcasting</span>
              <span className="text-slate-400 text-xs">Subscribes to `/topic/track/{'{'}trackingNumber{'}'}` with JWT authentication where required.</span>
            </div>
          </div>
          <div className="flex items-start gap-3 bg-navy-850 p-4 rounded-xl border border-navy-700/60">
            <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
            <div>
              <span className="font-semibold block text-slate-100">Automated Polling Fallback</span>
              <span className="text-slate-400 text-xs">Seamless failover to `/api/v1/tracking/public/{'{'}trackingNumber{'}'}/location` on disconnect.</span>
            </div>
          </div>
          <div className="flex items-start gap-3 bg-navy-850 p-4 rounded-xl border border-navy-700/60">
            <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
            <div>
              <span className="font-semibold block text-slate-100">PII & Privacy Protection</span>
              <span className="text-slate-400 text-xs">Masked customer phone, address, and agent contact info strictly upheld.</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
