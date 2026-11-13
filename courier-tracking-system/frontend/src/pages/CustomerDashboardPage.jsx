import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { Link } from 'react-router-dom';
import {
  Package,
  PlusCircle,
  Navigation,
  ChevronLeft,
  ChevronRight,
  Clock,
  MapPin,
  RefreshCw,
  AlertCircle,
  Truck,
  CheckCircle2,
  Key,
  ShieldCheck,
  Calendar,
  Layers,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { shipmentService } from '../services/api';
import CustomerDeliveryPinModal from '../components/CustomerDeliveryPinModal';
import DashboardCard from '../components/DashboardCard';

const STATUS_FILTERS = [
  { key: 'ALL', label: 'All Shipments' },
  { key: 'CREATED', label: 'Created' },
  { key: 'ASSIGNED', label: 'Assigned' },
  { key: 'PICKED_UP', label: 'Picked Up' },
  { key: 'IN_TRANSIT', label: 'In Transit' },
  { key: 'OUT_FOR_DELIVERY', label: 'Out for Delivery' },
  { key: 'DELIVERED', label: 'Delivered' },
  { key: 'ATTEMPT_FAILED', label: 'Attempt Failed' },
  { key: 'CANCELLED', label: 'Cancelled' },
  { key: 'RETURNED', label: 'Returned' },
];

export default function CustomerDashboardPage() {
  const { user } = useAuth();

  const [shipments, setShipments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [statusFilter, setStatusFilter] = useState('ALL');
  const [page, setPage] = useState(0);
  const [pageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [isFirstPage, setIsFirstPage] = useState(true);
  const [isLastPage, setIsLastPage] = useState(true);

  // Delivery OTP / PIN viewing modal state
  const [selectedShipmentForPin, setSelectedShipmentForPin] = useState(null);
  const [isPinModalOpen, setIsPinModalOpen] = useState(false);

  const handleOpenPinModal = (shipment) => {
    setSelectedShipmentForPin(shipment);
    setIsPinModalOpen(true);
  };

  const handleClosePinModal = () => {
    setIsPinModalOpen(false);
    setSelectedShipmentForPin(null);
  };

  const fetchShipments = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const params = {
        page,
        size: pageSize,
      };
      if (statusFilter !== 'ALL') {
        params.status = statusFilter;
      }

      const res = await shipmentService.getCustomerShipments(params);

      if (res.success && res.data) {
        const pageData = res.data;
        setShipments(pageData.content || []);
        setTotalPages(pageData.totalPages || 0);
        setTotalElements(pageData.totalElements || 0);
        setIsFirstPage(pageData.first ?? true);
        setIsLastPage(pageData.last ?? true);
      } else {
        setError(res.message || 'Failed to retrieve shipments');
      }
    } catch (err) {
      setError(err.message || 'Error communicating with shipment service');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, statusFilter]);

  useEffect(() => {
    fetchShipments();
  }, [fetchShipments]);

  const handleFilterChange = (newStatus) => {
    if (newStatus !== statusFilter) {
      setStatusFilter(newStatus);
      setPage(0);
    }
  };

  // Derive real summary counts from retrieved shipments
  const metrics = useMemo(() => {
    const inTransitCount = shipments.filter((s) =>
      ['IN_TRANSIT', 'OUT_FOR_DELIVERY', 'PICKED_UP'].includes(s.status)
    ).length;
    const deliveredCount = shipments.filter((s) => s.status === 'DELIVERED').length;
    const pendingCount = shipments.filter((s) =>
      ['CREATED', 'ASSIGNED'].includes(s.status)
    ).length;

    return {
      total: totalElements,
      inTransit: inTransitCount,
      delivered: deliveredCount,
      pending: pendingCount,
    };
  }, [shipments, totalElements]);

  const getStatusBadge = (status) => {
    switch (status) {
      case 'DELIVERED':
        return {
          label: 'Delivered',
          classes: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30',
          dot: 'bg-emerald-400',
        };
      case 'OUT_FOR_DELIVERY':
        return {
          label: 'Out for Delivery',
          classes: 'bg-blue-500/10 text-blue-400 border-blue-500/30 animate-pulse',
          dot: 'bg-blue-400',
        };
      case 'IN_TRANSIT':
        return {
          label: 'In Transit',
          classes: 'bg-indigo-500/10 text-indigo-400 border-indigo-500/30',
          dot: 'bg-indigo-400',
        };
      case 'PICKED_UP':
        return {
          label: 'Picked Up',
          classes: 'bg-cyan-500/10 text-cyan-400 border-cyan-500/30',
          dot: 'bg-cyan-400',
        };
      case 'ASSIGNED':
        return {
          label: 'Assigned',
          classes: 'bg-purple-500/10 text-purple-400 border-purple-500/30',
          dot: 'bg-purple-400',
        };
      case 'CREATED':
        return {
          label: 'Created',
          classes: 'bg-slate-500/10 text-slate-300 border-slate-500/30',
          dot: 'bg-slate-400',
        };
      case 'ATTEMPT_FAILED':
        return {
          label: 'Attempt Failed',
          classes: 'bg-amber-500/10 text-amber-400 border-amber-500/30',
          dot: 'bg-amber-400',
        };
      case 'RETURNED':
        return {
          label: 'Returned',
          classes: 'bg-orange-500/10 text-orange-400 border-orange-500/30',
          dot: 'bg-orange-400',
        };
      case 'CANCELLED':
        return {
          label: 'Cancelled',
          classes: 'bg-rose-500/10 text-rose-400 border-rose-500/30',
          dot: 'bg-rose-400',
        };
      default:
        return {
          label: status,
          classes: 'bg-slate-800 text-slate-300 border-slate-700',
          dot: 'bg-slate-500',
        };
    }
  };

  return (
    <div className="space-y-8">
      {/* Header Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-4 border-b border-[#263449]">
        <div>
          <div className="flex items-center gap-2 text-cyan-400 font-semibold text-xs tracking-wider uppercase mb-1">
            <Package className="w-4 h-4" />
            <span>Customer Consignment Portal</span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-extrabold text-[#F8FAFC] tracking-tight">
            My Shipments
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            Welcome back, <span className="font-semibold text-slate-200">{user?.fullName || user?.email}</span>. Track active deliveries and view your shipping history.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={fetchShipments}
            disabled={loading}
            aria-label="Refresh shipments"
            className="p-2.5 rounded-xl border border-[#263449] bg-[#121C2E] hover:bg-[#18243A] text-slate-300 hover:text-white transition-colors"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-blue-400' : ''}`} />
          </button>
          <Link
            to="/customer/book"
            data-testid="btn-book-shipment"
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-bold transition-all shadow-glow-blue"
          >
            <PlusCircle className="w-4 h-4" />
            <span>Book New Shipment</span>
          </Link>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <DashboardCard
          title="Total Shipments"
          value={metrics.total}
          subtitle="All time bookings"
          icon={Package}
          accentColor="blue"
        />
        <DashboardCard
          title="In-Transit / Active"
          value={metrics.inTransit}
          subtitle="Currently on the move"
          icon={Truck}
          accentColor="cyan"
        />
        <DashboardCard
          title="Delivered"
          value={metrics.delivered}
          subtitle="Successfully handed over"
          icon={CheckCircle2}
          accentColor="emerald"
        />
        <DashboardCard
          title="Pending Pickup"
          value={metrics.pending}
          subtitle="Awaiting courier dispatch"
          icon={Clock}
          accentColor="purple"
        />
      </div>

      {/* Status Filter Bar */}
      <div className="flex items-center gap-2 overflow-x-auto pb-2 scrollbar-thin">
        {STATUS_FILTERS.map((f) => {
          const isActive = statusFilter === f.key;
          return (
            <button
              key={f.key}
              onClick={() => handleFilterChange(f.key)}
              data-testid={`filter-${f.key.toLowerCase()}`}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-semibold whitespace-nowrap transition-all border ${
                isActive
                  ? 'bg-blue-600 text-white border-blue-500 shadow-glow-blue'
                  : 'bg-[#121C2E] text-slate-400 border-[#263449] hover:bg-[#18243A] hover:text-[#F8FAFC]'
              }`}
            >
              {f.label}
            </button>
          );
        })}
      </div>

      {/* Error State */}
      {error && (
        <div
          role="alert"
          className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs flex items-center justify-between gap-3 animate-in fade-in"
        >
          <div className="flex items-center gap-2">
            <AlertCircle className="w-4 h-4 text-rose-400 shrink-0" />
            <span>{error}</span>
          </div>
          <button
            onClick={fetchShipments}
            className="px-3 py-1.5 rounded-lg bg-rose-500/20 hover:bg-rose-500/30 border border-rose-500/40 font-bold text-rose-200 transition-colors"
          >
            Retry
          </button>
        </div>
      )}

      {/* Loading Skeleton */}
      {loading && (
        <div className="bg-[#121C2E] border border-[#263449] rounded-2xl p-12 text-center space-y-3">
          <RefreshCw className="w-7 h-7 animate-spin text-blue-500 mx-auto" />
          <p className="text-xs font-semibold text-slate-400">Loading your consignments...</p>
        </div>
      )}

      {/* Empty State */}
      {!loading && !error && shipments.length === 0 && (
        <div
          data-testid="empty-shipments-state"
          className="bg-[#121C2E] border border-dashed border-[#263449] rounded-2xl p-12 text-center space-y-4"
        >
          <div className="w-14 h-14 rounded-2xl bg-[#18243A] border border-[#263449] text-blue-400 flex items-center justify-center mx-auto shadow-inner">
            <Package className="w-7 h-7" />
          </div>
          <div>
            <h3 className="text-base font-bold text-[#F8FAFC]">No shipments found</h3>
            <p className="text-xs text-slate-400 max-w-sm mx-auto mt-1">
              {statusFilter === 'ALL'
                ? 'You have not booked any courier consignments yet. Create your first booking to start shipping.'
                : `No shipments currently match the status "${statusFilter}".`}
            </p>
          </div>
          <div className="pt-2">
            <Link
              to="/customer/book"
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-bold transition-all shadow-glow-blue"
            >
              <PlusCircle className="w-4 h-4" />
              <span>Book a Shipment Now</span>
            </Link>
          </div>
        </div>
      )}

      {/* Shipment Content List / Table */}
      {!loading && !error && shipments.length > 0 && (
        <div className="space-y-4">
          <div className="bg-[#121C2E] border border-[#263449] rounded-2xl shadow-dark-card overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="border-b border-[#263449] bg-[#0D1424] text-slate-400 font-semibold uppercase text-[10px] tracking-wider">
                    <th className="py-3.5 px-4">Tracking Code</th>
                    <th className="py-3.5 px-4">Status</th>
                    <th className="py-3.5 px-4">Destination</th>
                    <th className="py-3.5 px-4">Weight</th>
                    <th className="py-3.5 px-4">Cost</th>
                    <th className="py-3.5 px-4">Booked</th>
                    <th className="py-3.5 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#263449]/60 font-normal">
                  {shipments.map((shipment) => {
                    const badge = getStatusBadge(shipment.status);
                    return (
                      <tr
                        key={shipment.id || shipment.trackingNumber}
                        data-testid={`shipment-row-${shipment.trackingNumber}`}
                        className="hover:bg-[#18243A]/50 transition-colors"
                      >
                        {/* Tracking Number */}
                        <td className="py-4 px-4 whitespace-nowrap">
                          <Link
                            to={`/track/${shipment.trackingNumber}`}
                            className="font-mono font-bold text-cyan-400 hover:text-cyan-300 hover:underline flex items-center gap-1"
                          >
                            <span>{shipment.trackingNumber}</span>
                          </Link>
                        </td>

                        {/* Status */}
                        <td className="py-4 px-4 whitespace-nowrap">
                          <span
                            className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-[11px] font-bold ${badge.classes}`}
                          >
                            <span className={`w-1.5 h-1.5 rounded-full ${badge.dot}`} />
                            {badge.label}
                          </span>
                        </td>

                        {/* Destination */}
                        <td className="py-4 px-4 max-w-xs">
                          <div className="font-semibold text-slate-200">{shipment.recipientName}</div>
                          <div className="text-[11px] text-slate-400 truncate" title={shipment.recipientAddress}>
                            {shipment.recipientAddress}
                          </div>
                        </td>

                        {/* Weight */}
                        <td className="py-4 px-4 whitespace-nowrap font-medium text-slate-300">
                          {shipment.weightKg} kg
                        </td>

                        {/* Price */}
                        <td className="py-4 px-4 whitespace-nowrap font-semibold text-[#F8FAFC]">
                          ${Number(shipment.price).toFixed(2)}{' '}
                          <span className="text-[10px] text-slate-400 font-normal">{shipment.currency}</span>
                        </td>

                        {/* Booked Date */}
                        <td className="py-4 px-4 whitespace-nowrap text-slate-400 text-[11px]">
                          {shipment.createdAt
                            ? new Date(shipment.createdAt).toLocaleDateString(undefined, {
                                month: 'short',
                                day: 'numeric',
                                year: 'numeric',
                              })
                            : '—'}
                        </td>

                        {/* Actions */}
                        <td className="py-4 px-4 text-right whitespace-nowrap">
                          <div className="flex items-center justify-end gap-2">
                            {shipment.status === 'OUT_FOR_DELIVERY' && (
                              <button
                                type="button"
                                onClick={() => handleOpenPinModal(shipment)}
                                data-testid={`view-otp-btn-${shipment.trackingNumber}`}
                                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-emerald-500/15 hover:bg-emerald-500/25 text-emerald-400 font-semibold text-xs transition-colors border border-emerald-500/30 shadow-2xs"
                              >
                                <Key className="w-3.5 h-3.5 text-emerald-400" />
                                <span>View Delivery OTP</span>
                              </button>
                            )}
                            <Link
                              to={`/track/${shipment.trackingNumber}`}
                              data-testid={`track-btn-${shipment.trackingNumber}`}
                              className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl bg-cyan-500/10 hover:bg-cyan-500/20 text-cyan-400 font-semibold text-xs transition-colors border border-cyan-500/30"
                            >
                              <Navigation className="w-3 h-3 text-cyan-400" />
                              <span>Track Live</span>
                            </Link>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            <div className="px-4 py-3.5 bg-[#0D1424] border-t border-[#263449] flex flex-col sm:flex-row items-center justify-between gap-3 text-xs">
              <span className="text-slate-400 font-medium" data-testid="pagination-info">
                Showing page <span className="font-bold text-slate-200">{page + 1}</span> of{' '}
                <span className="font-bold text-slate-200">{totalPages || 1}</span> ({totalElements} total shipments)
              </span>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={isFirstPage || page === 0}
                  data-testid="pagination-prev"
                  aria-label="Previous Page"
                  className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl border border-[#263449] bg-[#121C2E] hover:bg-[#18243A] disabled:opacity-30 disabled:cursor-not-allowed font-semibold text-slate-300 transition-colors shadow-dark-sm"
                >
                  <ChevronLeft className="w-3.5 h-3.5" />
                  <span>Previous</span>
                </button>

                <button
                  type="button"
                  onClick={() => setPage((p) => p + 1)}
                  disabled={isLastPage || page >= totalPages - 1}
                  data-testid="pagination-next"
                  aria-label="Next Page"
                  className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl border border-[#263449] bg-[#121C2E] hover:bg-[#18243A] disabled:opacity-30 disabled:cursor-not-allowed font-semibold text-slate-300 transition-colors shadow-dark-sm"
                >
                  <span>Next</span>
                  <ChevronRight className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Secure Customer Delivery PIN / OTP Viewing Modal */}
      <CustomerDeliveryPinModal
        isOpen={isPinModalOpen}
        shipment={selectedShipmentForPin}
        onClose={handleClosePinModal}
      />
    </div>
  );
}
