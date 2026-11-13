import React, { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import {
  Package,
  Search,
  Truck,
  CheckCircle2,
  Clock,
  Calendar,
  AlertCircle,
  RefreshCw,
  ArrowRight,
  ShieldCheck,
  ChevronRight,
  MapPin,
} from 'lucide-react';
import { useLiveTracking } from '../hooks/useLiveTracking';
import LiveTrackingMap from '../components/LiveTrackingMap';

export default function PublicTrackingPage() {
  const { trackingNumber: routeTrackingNumber } = useParams();
  const navigate = useNavigate();

  const [searchInput, setSearchInput] = useState(routeTrackingNumber || '');
  const activeTrackingNumber = routeTrackingNumber ? routeTrackingNumber.trim().toUpperCase() : '';

  const {
    shipment,
    location,
    connectionStatus,
    isLoading,
    error,
    lastUpdatedAt,
    refresh,
    destination,
  } = useLiveTracking(activeTrackingNumber);

  const handleSearch = (e) => {
    e.preventDefault();
    if (searchInput.trim()) {
      navigate(`/track/${encodeURIComponent(searchInput.trim().toUpperCase())}`);
    }
  };

  const getStatusBadgeStyle = (status) => {
    switch (status) {
      case 'DELIVERED':
        return 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40';
      case 'OUT_FOR_DELIVERY':
        return 'bg-blue-500/20 text-blue-400 border-blue-500/40 animate-pulse';
      case 'IN_TRANSIT':
      case 'PICKED_UP':
        return 'bg-cyan-500/20 text-cyan-400 border-cyan-500/40';
      case 'CANCELLED':
      case 'RETURNED':
        return 'bg-rose-500/20 text-rose-400 border-rose-500/40';
      default:
        return 'bg-slate-800 text-slate-300 border-navy-700';
    }
  };

  const formatDateTime = (dateInput) => {
    if (!dateInput) return '—';
    const d = new Date(dateInput);
    if (isNaN(d.getTime())) return '—';
    return d.toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  return (
    <div className="space-y-8 pb-16 text-slate-100">
      {/* Search Header Banner */}
      <section className="bg-gradient-to-r from-navy-950 via-navy-900 to-navy-950 rounded-3xl p-6 sm:p-10 text-white shadow-dark-card border border-navy-700/70">
        <div className="max-w-3xl mx-auto space-y-4 text-center">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-blue-500/10 border border-blue-500/30 text-accent-blue text-xs font-semibold uppercase tracking-wider">
            <Truck className="w-3.5 h-3.5 text-accent-blue" />
            Live Shipment Telemetry & Tracking
          </div>
          <h1 className="text-3xl sm:text-4xl font-extrabold tracking-tight text-slate-100">
            Track Your Delivery in Real Time
          </h1>
          <p className="text-sm sm:text-base text-slate-400 max-w-xl mx-auto">
            View live GPS movements, delivery milestones, and instant status updates with zero PII exposure.
          </p>

          {/* Search Form */}
          <form onSubmit={handleSearch} className="pt-2 max-w-xl mx-auto">
            <div className="relative flex items-center">
              <Search className="absolute left-4 w-5 h-5 text-slate-400 pointer-events-none" />
              <input
                type="text"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder="Enter Tracking Number (e.g. TRK-20261026-ABCD)"
                className="w-full pl-12 pr-28 py-3.5 rounded-2xl bg-navy-850 border border-navy-700 text-slate-100 placeholder-slate-500 font-medium text-sm focus:outline-none focus:ring-2 focus:ring-accent-blue/30 focus:border-accent-blue shadow-md"
              />
              <button
                type="submit"
                className="absolute right-2 px-5 py-2.5 rounded-xl bg-accent-blue hover:bg-blue-600 text-white text-xs font-bold transition-colors shadow-sm"
              >
                Track
              </button>
            </div>
          </form>
        </div>
      </section>

      {/* Main Content Area */}
      {!activeTrackingNumber ? (
        /* Empty State Prompting User to Search */
        <div className="bg-navy-850 rounded-2xl border border-navy-700/60 p-12 text-center max-w-md mx-auto space-y-4 shadow-dark-card">
          <div className="w-16 h-16 rounded-2xl bg-blue-500/10 text-accent-blue flex items-center justify-center mx-auto border border-blue-500/20">
            <Package className="w-8 h-8" />
          </div>
          <h2 className="text-xl font-bold text-slate-100">Ready to Track</h2>
          <p className="text-xs text-slate-400 leading-relaxed">
            Enter a consignment tracking number above to follow vehicle GPS coordinates, milestone logs, and delivery progress.
          </p>
        </div>
      ) : isLoading ? (
        /* Loading Skeleton */
        <div className="flex flex-col items-center justify-center py-20 space-y-4">
          <RefreshCw className="w-8 h-8 text-accent-blue animate-spin" />
          <p className="text-sm font-medium text-slate-300">
            Querying tracking records and initializing telemetry channel...
          </p>
        </div>
      ) : error ? (
        /* Error State */
        <div className="bg-navy-850 rounded-2xl border border-rose-500/40 p-8 text-center max-w-lg mx-auto space-y-4 shadow-dark-card">
          <div className="w-14 h-14 rounded-2xl bg-rose-500/10 text-rose-400 flex items-center justify-center mx-auto border border-rose-500/20">
            <AlertCircle className="w-7 h-7" />
          </div>
          <h2 className="text-lg font-bold text-slate-100">Shipment Not Found</h2>
          <p className="text-xs text-slate-300 leading-relaxed">{error}</p>
          <div className="pt-2">
            <Link
              to="/"
              className="inline-flex items-center gap-1.5 text-xs font-semibold text-accent-cyan hover:text-cyan-300"
            >
              Return to Home <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>
        </div>
      ) : (
        /* Loaded Shipment Details & Live Map */
        <div className="space-y-6">
          {/* Tracking Summary Card */}
          <div className="bg-navy-850 rounded-2xl border border-navy-700/60 shadow-dark-card p-6 sm:p-8 space-y-6">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-navy-700/60 pb-6">
              <div>
                <span className="text-xs uppercase tracking-wider font-semibold text-slate-400 block mb-1">
                  Tracking Number
                </span>
                <div className="flex items-center gap-3">
                  <h2 className="text-2xl sm:text-3xl font-black text-slate-100 tracking-tight font-mono">
                    {shipment?.trackingNumber || activeTrackingNumber}
                  </h2>
                  <span
                    className={`px-3 py-1 rounded-full text-xs font-bold border ${getStatusBadgeStyle(
                      shipment?.status
                    )}`}
                  >
                    {shipment?.status?.replace(/_/g, ' ') || 'ACTIVE'}
                  </span>
                </div>
              </div>

              <div className="flex items-center gap-3">
                <button
                  onClick={refresh}
                  className="inline-flex items-center gap-1.5 text-xs font-semibold px-3 py-2 rounded-xl bg-navy-800 hover:bg-navy-700 text-slate-200 border border-navy-700 transition-colors"
                >
                  <RefreshCw className="w-3.5 h-3.5 text-slate-400" />
                  Refresh
                </button>
              </div>
            </div>

            {/* Estimated Dates & Metadata */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs">
              <div className="bg-navy-900/70 p-3.5 rounded-xl border border-navy-700/60 flex items-start gap-3">
                <Clock className="w-4 h-4 text-accent-blue shrink-0 mt-0.5" />
                <div>
                  <span className="text-slate-400 font-medium block">Estimated Delivery</span>
                  <span className="font-semibold text-slate-200">
                    {formatDateTime(shipment?.estimatedDeliveryAt)}
                  </span>
                </div>
              </div>

              <div className="bg-navy-900/70 p-3.5 rounded-xl border border-navy-700/60 flex items-start gap-3">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                <div>
                  <span className="text-slate-400 font-medium block">Actual Delivery</span>
                  <span className="font-semibold text-slate-200">
                    {formatDateTime(shipment?.actualDeliveryAt)}
                  </span>
                </div>
              </div>

              <div className="bg-navy-900/70 p-3.5 rounded-xl border border-navy-700/60 flex items-start gap-3">
                <Calendar className="w-4 h-4 text-accent-cyan shrink-0 mt-0.5" />
                <div>
                  <span className="text-slate-400 font-medium block">Booked On</span>
                  <span className="font-semibold text-slate-200">
                    {formatDateTime(shipment?.createdAt)}
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Interactive Live Map Section */}
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <MapPin className="w-5 h-5 text-accent-blue" />
                <h3 className="text-lg font-bold text-slate-100">Live Delivery Route & Map</h3>
              </div>
              <span className="text-xs text-slate-400">
                OpenStreetMap & GPS Telemetry
              </span>
            </div>

            <LiveTrackingMap
              trackingNumber={activeTrackingNumber}
              location={location}
              destination={destination}
              connectionStatus={connectionStatus}
              lastUpdatedAt={lastUpdatedAt}
              height="480px"
            />
          </div>

          {/* Shipment Milestone Journey Timeline */}
          <div className="bg-navy-850 rounded-2xl border border-navy-700/60 shadow-dark-card p-6 sm:p-8 space-y-6">
            <h3 className="text-base font-bold text-slate-100 flex items-center gap-2">
              <CheckCircle2 className="w-5 h-5 text-accent-blue" />
              Consignment Journey Milestones
            </h3>

            {shipment?.milestones && shipment.milestones.length > 0 ? (
              <div className="relative pl-6 space-y-6 before:absolute before:left-2.5 before:top-2 before:bottom-2 before:w-0.5 before:bg-navy-700">
                {shipment.milestones.map((milestone, idx) => (
                  <div key={idx} className="relative flex items-start gap-4">
                    <div className="absolute -left-6 top-1 w-5 h-5 rounded-full bg-navy-850 border-2 border-accent-blue flex items-center justify-center">
                      <div className="w-2 h-2 rounded-full bg-accent-blue"></div>
                    </div>
                    <div className="space-y-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-bold text-sm text-slate-200">
                          {milestone.status?.replace(/_/g, ' ')}
                        </span>
                        {(milestone.location || milestone.locationName) && (
                          <span className="text-xs px-2 py-0.5 bg-navy-800 border border-navy-700 rounded-md text-accent-cyan font-medium">
                            {milestone.location || milestone.locationName}
                          </span>
                        )}
                      </div>
                      <p className="text-xs text-slate-400">{milestone.description || milestone.remarks}</p>
                      <span className="text-[11px] text-slate-500 block">
                        {formatDateTime(milestone.timestamp)}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-center py-6 text-xs text-slate-400">
                No milestone events recorded yet.
              </div>
            )}
          </div>

          {/* Privacy & Safeguard Notice */}
          <div className="bg-navy-900/80 rounded-2xl border border-navy-700/70 p-4 flex items-center gap-3 text-xs text-slate-300">
            <ShieldCheck className="w-5 h-5 text-accent-blue shrink-0" />
            <div>
              <span className="font-semibold block text-slate-200">Privacy & Security Shield Active</span>
              <span className="text-slate-400">
                Sender, recipient phone numbers, billing keys, and courier personal identity details are completely shielded.
              </span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
