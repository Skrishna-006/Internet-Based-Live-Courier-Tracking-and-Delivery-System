import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import {
  Package,
  PlusCircle,
  Navigation,
  ExternalLink,
  ChevronLeft,
  ChevronRight,
  Clock,
  MapPin,
  RefreshCw,
  AlertCircle,
  CheckCircle,
  Truck,
  RotateCcw,
  Ban,
  Calendar,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { shipmentService } from '../services/api';

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

  const getStatusBadge = (status) => {
    switch (status) {
      case 'DELIVERED':
        return {
          label: 'Delivered',
          classes: 'bg-emerald-50 text-emerald-700 border-emerald-200',
          dot: 'bg-emerald-500',
        };
      case 'OUT_FOR_DELIVERY':
        return {
          label: 'Out for Delivery',
          classes: 'bg-blue-50 text-blue-700 border-blue-200 animate-pulse',
          dot: 'bg-blue-500',
        };
      case 'IN_TRANSIT':
        return {
          label: 'In Transit',
          classes: 'bg-indigo-50 text-indigo-700 border-indigo-200',
          dot: 'bg-indigo-500',
        };
      case 'PICKED_UP':
        return {
          label: 'Picked Up',
          classes: 'bg-cyan-50 text-cyan-700 border-cyan-200',
          dot: 'bg-cyan-500',
        };
      case 'ASSIGNED':
        return {
          label: 'Assigned',
          classes: 'bg-violet-50 text-violet-700 border-violet-200',
          dot: 'bg-violet-500',
        };
      case 'CREATED':
        return {
          label: 'Created',
          classes: 'bg-slate-100 text-slate-700 border-slate-200',
          dot: 'bg-slate-500',
        };
      case 'ATTEMPT_FAILED':
        return {
          label: 'Attempt Failed',
          classes: 'bg-amber-50 text-amber-800 border-amber-200',
          dot: 'bg-amber-500',
        };
      case 'RETURNED':
        return {
          label: 'Returned',
          classes: 'bg-purple-50 text-purple-700 border-purple-200',
          dot: 'bg-purple-500',
        };
      case 'CANCELLED':
        return {
          label: 'Cancelled',
          classes: 'bg-rose-50 text-rose-700 border-rose-200',
          dot: 'bg-rose-500',
        };
      default:
        return {
          label: status,
          classes: 'bg-slate-100 text-slate-700 border-slate-200',
          dot: 'bg-slate-500',
        };
    }
  };

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-2 border-b border-slate-200">
        <div>
          <div className="flex items-center gap-2 text-indigo-600 font-semibold text-xs tracking-wider uppercase mb-1">
            <Package className="w-4 h-4" />
            <span>Customer Consignment Portal</span>
          </div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">My Shipments</h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Welcome back, <span className="font-semibold text-slate-700">{user?.fullName || user?.email}</span>. Track active deliveries and view your shipping history.
          </p>
        </div>

        <div className="flex items-center gap-2.5">
          <button
            onClick={fetchShipments}
            disabled={loading}
            aria-label="Refresh shipments"
            className="p-2 rounded-xl border border-slate-200 hover:bg-slate-100 text-slate-600 transition-colors"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
          <Link
            to="/customer/book"
            data-testid="btn-book-shipment"
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold transition-all shadow-sm"
          >
            <PlusCircle className="w-4 h-4" />
            <span>Book New Shipment</span>
          </Link>
        </div>
      </div>

      {/* Status Filter Bar */}
      <div className="flex items-center gap-1.5 overflow-x-auto pb-2 scrollbar-thin">
        {STATUS_FILTERS.map((f) => {
          const isActive = statusFilter === f.key;
          return (
            <button
              key={f.key}
              onClick={() => handleFilterChange(f.key)}
              data-testid={`filter-${f.key.toLowerCase()}`}
              className={`px-3 py-1.5 rounded-xl text-xs font-semibold whitespace-nowrap transition-colors border ${
                isActive
                  ? 'bg-slate-900 text-white border-slate-900 shadow-xs'
                  : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50 hover:text-slate-900'
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
          className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-center justify-between gap-3 animate-in fade-in"
        >
          <div className="flex items-center gap-2">
            <AlertCircle className="w-4 h-4 text-rose-500 shrink-0" />
            <span>{error}</span>
          </div>
          <button
            onClick={fetchShipments}
            className="px-3 py-1 rounded-lg bg-rose-100 hover:bg-rose-200 font-bold text-rose-900 transition-colors"
          >
            Retry
          </button>
        </div>
      )}

      {/* Loading Skeleton */}
      {loading && (
        <div className="bg-white border border-slate-200 rounded-2xl p-8 text-center space-y-3">
          <RefreshCw className="w-6 h-6 animate-spin text-indigo-600 mx-auto" />
          <p className="text-xs font-semibold text-slate-500">Loading your consignments...</p>
        </div>
      )}

      {/* Empty State */}
      {!loading && !error && shipments.length === 0 && (
        <div
          data-testid="empty-shipments-state"
          className="bg-white border border-dashed border-slate-300 rounded-2xl p-12 text-center space-y-4"
        >
          <div className="w-12 h-12 rounded-full bg-slate-100 text-slate-400 flex items-center justify-center mx-auto">
            <Package className="w-6 h-6" />
          </div>
          <div>
            <h3 className="text-base font-bold text-slate-800">No shipments found</h3>
            <p className="text-xs text-slate-500 max-w-sm mx-auto mt-1">
              {statusFilter === 'ALL'
                ? 'You have not booked any courier consignments yet. Create your first booking to start shipping.'
                : `No shipments currently match the status "${statusFilter}".`}
            </p>
          </div>
          <div>
            <Link
              to="/customer/book"
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold transition-all shadow-sm"
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
          <div className="bg-white border border-slate-200 rounded-2xl shadow-xs overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50/80 text-slate-500 font-semibold uppercase text-[10px] tracking-wider">
                    <th className="py-3 px-4">Tracking Code</th>
                    <th className="py-3 px-4">Status</th>
                    <th className="py-3 px-4">Destination</th>
                    <th className="py-3 px-4">Weight</th>
                    <th className="py-3 px-4">Cost</th>
                    <th className="py-3 px-4">Booked</th>
                    <th className="py-3 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 font-normal">
                  {shipments.map((shipment) => {
                    const badge = getStatusBadge(shipment.status);
                    return (
                      <tr
                        key={shipment.id || shipment.trackingNumber}
                        data-testid={`shipment-row-${shipment.trackingNumber}`}
                        className="hover:bg-slate-50/70 transition-colors"
                      >
                        {/* Tracking Number */}
                        <td className="py-3.5 px-4">
                          <Link
                            to={`/track/${shipment.trackingNumber}`}
                            className="font-mono font-bold text-indigo-600 hover:text-indigo-800 hover:underline flex items-center gap-1"
                          >
                            <span>{shipment.trackingNumber}</span>
                          </Link>
                        </td>

                        {/* Status */}
                        <td className="py-3.5 px-4 whitespace-nowrap">
                          <span
                            className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-[11px] font-bold ${badge.classes}`}
                          >
                            <span className={`w-1.5 h-1.5 rounded-full ${badge.dot}`} />
                            {badge.label}
                          </span>
                        </td>

                        {/* Destination */}
                        <td className="py-3.5 px-4 max-w-xs">
                          <div className="font-semibold text-slate-800">{shipment.recipientName}</div>
                          <div className="text-[11px] text-slate-500 truncate" title={shipment.recipientAddress}>
                            {shipment.recipientAddress}
                          </div>
                        </td>

                        {/* Weight */}
                        <td className="py-3.5 px-4 whitespace-nowrap font-medium text-slate-700">
                          {shipment.weightKg} kg
                        </td>

                        {/* Price */}
                        <td className="py-3.5 px-4 whitespace-nowrap font-semibold text-slate-900">
                          ${Number(shipment.price).toFixed(2)}{' '}
                          <span className="text-[10px] text-slate-500 font-normal">{shipment.currency}</span>
                        </td>

                        {/* Booked Date */}
                        <td className="py-3.5 px-4 whitespace-nowrap text-slate-500 text-[11px]">
                          {shipment.createdAt
                            ? new Date(shipment.createdAt).toLocaleDateString(undefined, {
                                month: 'short',
                                day: 'numeric',
                                year: 'numeric',
                              })
                            : '—'}
                        </td>

                        {/* Actions */}
                        <td className="py-3.5 px-4 text-right whitespace-nowrap">
                          <Link
                            to={`/track/${shipment.trackingNumber}`}
                            data-testid={`track-btn-${shipment.trackingNumber}`}
                            className="inline-flex items-center gap-1 px-3 py-1 rounded-lg bg-indigo-50 hover:bg-indigo-100 text-indigo-700 font-semibold text-xs transition-colors border border-indigo-200"
                          >
                            <Navigation className="w-3 h-3 text-indigo-600" />
                            <span>Track Live</span>
                          </Link>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            <div className="px-4 py-3 bg-slate-50/80 border-t border-slate-200 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs">
              <span className="text-slate-500 font-medium" data-testid="pagination-info">
                Showing page <span className="font-bold text-slate-800">{page + 1}</span> of{' '}
                <span className="font-bold text-slate-800">{totalPages || 1}</span> ({totalElements} total shipments)
              </span>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={isFirstPage || page === 0}
                  data-testid="pagination-prev"
                  aria-label="Previous Page"
                  className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border border-slate-200 bg-white hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed font-semibold text-slate-700 transition-colors shadow-2xs"
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
                  className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border border-slate-200 bg-white hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed font-semibold text-slate-700 transition-colors shadow-2xs"
                >
                  <span>Next</span>
                  <ChevronRight className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
