import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Link } from 'react-router-dom';
import {
  Package,
  Truck,
  CheckCircle,
  AlertCircle,
  Clock,
  MapPin,
  RefreshCw,
  Navigation,
  ChevronLeft,
  ChevronRight,
  Phone,
  User,
  Radio,
  X,
  FileText,
  RotateCcw,
  Check,
  ShieldCheck,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { agentService, shipmentService } from '../services/api';
import { createStompClient } from '../services/websocket';
import DashboardCard from '../components/DashboardCard';

const STATUS_FILTERS = [
  { key: 'ALL', label: 'All Assigned' },
  { key: 'ASSIGNED', label: 'Awaiting Pickup' },
  { key: 'PICKED_UP', label: 'Picked Up' },
  { key: 'IN_TRANSIT', label: 'In Transit' },
  { key: 'OUT_FOR_DELIVERY', label: 'Out for Delivery' },
  { key: 'ATTEMPT_FAILED', label: 'Attempt Failed' },
  { key: 'DELIVERED', label: 'Delivered' },
  { key: 'RETURNED', label: 'Returned' },
];

export default function DriverDashboardPage() {
  const { user, token } = useAuth();

  // Assignments & Summary State
  const [assignments, setAssignments] = useState([]);
  const [summary, setSummary] = useState({
    totalAssigned: 0,
    awaitingPickup: 0,
    inTransit: 0,
    outForDelivery: 0,
    completed: 0,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successBanner, setSuccessBanner] = useState(null);

  // Pagination & Filter State
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [page, setPage] = useState(0);
  const [pageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [isFirstPage, setIsFirstPage] = useState(true);
  const [isLastPage, setIsLastPage] = useState(true);

  // WebSocket / Real-time Notification State
  const [wsConnected, setWsConnected] = useState(false);
  const [newAssignmentAlert, setNewAssignmentAlert] = useState(null);
  const stompClientRef = useRef(null);

  // Status Progression Modal State
  const [modalOpen, setModalOpen] = useState(false);
  const [activeShipment, setActiveShipment] = useState(null);
  const [targetStatus, setTargetStatus] = useState('');
  const [locationName, setLocationName] = useState('');
  const [remarks, setRemarks] = useState('');
  const [submittingTransition, setSubmittingTransition] = useState(false);
  const [modalError, setModalError] = useState(null);

  // Fetch summary counts across all assignments
  const fetchSummary = useCallback(async () => {
    try {
      const res = await agentService.getMySummary();
      if (res.success && res.data) {
        setSummary(res.data);
      }
    } catch {
      // Non-fatal summary fetch failure
    }
  }, []);

  // Fetch paginated assignments for the driver
  const fetchAssignments = useCallback(async () => {
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

      const res = await agentService.getMyAssignments(params);

      if (res.success && res.data) {
        const pageData = res.data;
        setAssignments(pageData.content || []);
        setTotalPages(pageData.totalPages || 0);
        setTotalElements(pageData.totalElements || 0);
        setIsFirstPage(pageData.first ?? true);
        setIsLastPage(pageData.last ?? true);
      } else {
        setError(res.message || 'Failed to retrieve assigned shipments');
      }
    } catch (err) {
      setError(err.message || 'Error communicating with assignment service');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, statusFilter]);

  // Initial load and filter/page dependency
  useEffect(() => {
    fetchAssignments();
    fetchSummary();
  }, [fetchAssignments, fetchSummary]);

  // Real-Time STOMP WebSocket Subscription for New Assignments
  useEffect(() => {
    if (!user?.id) return;

    let isMounted = true;
    const client = createStompClient({
      token,
      onConnect: () => {
        if (!isMounted) return;
        setWsConnected(true);

        client.subscribe(`/topic/agent/${user.id}/orders`, (message) => {
          try {
            const assignment = JSON.parse(message.body);
            if (isMounted) {
              setNewAssignmentAlert({
                trackingNumber: assignment.trackingNumber,
                recipientName: assignment.recipientName,
                timestamp: new Date().toLocaleTimeString(),
              });
              // Automatically refresh queue and summary
              fetchAssignments();
              fetchSummary();
            }
          } catch {
            // Malformed message payload ignored
          }
        });
      },
      onDisconnect: () => {
        if (isMounted) setWsConnected(false);
      },
      onError: () => {
        if (isMounted) setWsConnected(false);
      },
    });

    stompClientRef.current = client;
    try {
      client.activate();
    } catch {
      // STOMP activation fallback
    }

    return () => {
      isMounted = false;
      if (stompClientRef.current) {
        try {
          stompClientRef.current.deactivate();
        } catch {
          // Ignore deactivation errors
        }
      }
    };
  }, [user?.id, token, fetchAssignments, fetchSummary]);

  const handleFilterChange = (newStatus) => {
    if (newStatus !== statusFilter) {
      setStatusFilter(newStatus);
      setPage(0);
    }
  };

  const handleRefresh = () => {
    fetchAssignments();
    fetchSummary();
  };

  // Open the Status Progression Modal
  const openTransitionModal = (shipment, target) => {
    setActiveShipment(shipment);
    setTargetStatus(target);
    setLocationName('');
    setRemarks('');
    setModalError(null);
    setModalOpen(true);
  };

  const closeTransitionModal = () => {
    setModalOpen(false);
    setActiveShipment(null);
    setTargetStatus('');
    setLocationName('');
    setRemarks('');
    setModalError(null);
  };

  // Submit Status Progression to Backend
  const handleSubmitTransition = async (e) => {
    e.preventDefault();
    if (!activeShipment || !targetStatus) return;

    setSubmittingTransition(true);
    setModalError(null);

    try {
      const payload = {
        status: targetStatus,
        locationName: locationName.trim() || undefined,
        remarks: remarks.trim() || undefined,
      };

      const res = await shipmentService.updateStatus(activeShipment.id, payload);

      if (res.success) {
        setSuccessBanner(
          `Shipment ${activeShipment.trackingNumber} successfully updated to ${targetStatus.replace(/_/g, ' ')}.`
        );
        closeTransitionModal();
        fetchAssignments();
        fetchSummary();
        setTimeout(() => setSuccessBanner(null), 6000);
      } else {
        setModalError(res.message || 'Failed to update status');
      }
    } catch (err) {
      setModalError(err.message || 'Error occurred while updating shipment status');
    } finally {
      setSubmittingTransition(false);
    }
  };

  // Allowed transitions per status
  const getPermittedTransitions = (status) => {
    switch (status) {
      case 'ASSIGNED':
        return [
          {
            target: 'PICKED_UP',
            label: 'Pick Up Package',
            style: 'bg-emerald-600 hover:bg-emerald-500 text-white shadow-sm',
            icon: Check,
          },
        ];
      case 'PICKED_UP':
        return [
          {
            target: 'IN_TRANSIT',
            label: 'Move to Hub / In Transit',
            style: 'bg-blue-600 hover:bg-blue-500 text-white shadow-sm',
            icon: Truck,
          },
          {
            target: 'OUT_FOR_DELIVERY',
            label: 'Start Delivery Run',
            style: 'bg-indigo-600 hover:bg-indigo-500 text-white shadow-sm',
            icon: Navigation,
          },
        ];
      case 'IN_TRANSIT':
        return [
          {
            target: 'OUT_FOR_DELIVERY',
            label: 'Start Delivery Run',
            style: 'bg-indigo-600 hover:bg-indigo-500 text-white shadow-sm',
            icon: Navigation,
          },
        ];
      case 'OUT_FOR_DELIVERY':
        return [
          {
            target: 'ATTEMPT_FAILED',
            label: 'Mark Attempt Failed',
            style: 'bg-rose-600 hover:bg-rose-500 text-white shadow-sm',
            icon: AlertCircle,
          },
        ];
      case 'ATTEMPT_FAILED':
        return [
          {
            target: 'OUT_FOR_DELIVERY',
            label: 'Re-attempt Delivery',
            style: 'bg-indigo-600 hover:bg-indigo-500 text-white shadow-sm',
            icon: Navigation,
          },
          {
            target: 'RETURNED',
            label: 'Return to Sender',
            style: 'bg-amber-600 hover:bg-amber-500 text-white shadow-sm',
            icon: RotateCcw,
          },
        ];
      default:
        return [];
    }
  };

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
          classes: 'bg-indigo-500/10 text-indigo-400 border-indigo-500/30',
          dot: 'bg-indigo-400 animate-pulse',
        };
      case 'IN_TRANSIT':
        return {
          label: 'In Transit',
          classes: 'bg-blue-500/10 text-blue-400 border-blue-500/30',
          dot: 'bg-blue-400',
        };
      case 'PICKED_UP':
        return {
          label: 'Picked Up',
          classes: 'bg-teal-500/10 text-teal-400 border-teal-500/30',
          dot: 'bg-teal-400',
        };
      case 'ASSIGNED':
        return {
          label: 'Awaiting Pickup',
          classes: 'bg-amber-500/10 text-amber-400 border-amber-500/30',
          dot: 'bg-amber-400',
        };
      case 'ATTEMPT_FAILED':
        return {
          label: 'Attempt Failed',
          classes: 'bg-rose-500/10 text-rose-400 border-rose-500/30',
          dot: 'bg-rose-400',
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
          classes: 'bg-slate-500/10 text-slate-400 border-slate-500/30',
          dot: 'bg-slate-400',
        };
      default:
        return {
          label: status,
          classes: 'bg-slate-800 text-slate-300 border-slate-700',
          dot: 'bg-slate-400',
        };
    }
  };

  return (
    <div className="space-y-6" data-testid="driver-dashboard-container">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-4 border-b border-[#263449]">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl sm:text-3xl font-extrabold text-[#F8FAFC] tracking-tight" data-testid="driver-portal-title">
              Delivery Agent Route Portal
            </h1>
            <span
              className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold border ${
                wsConnected
                  ? 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30 shadow-glow-emerald'
                  : 'bg-[#18243A] text-slate-400 border-[#263449]'
              }`}
              data-testid="ws-connection-indicator"
              title={wsConnected ? 'Connected to real-time dispatch STOMP feed' : 'Polling mode active'}
            >
              <Radio className={`w-3.5 h-3.5 ${wsConnected ? 'text-emerald-400 animate-pulse' : 'text-slate-400'}`} />
              <span>{wsConnected ? 'Live Dispatch' : 'Sync Active'}</span>
            </span>
          </div>
          <p className="text-xs sm:text-sm text-slate-400 mt-1">
            Logged in as <span className="font-semibold text-slate-200">{user?.fullName || user?.email}</span> — Manage your active delivery queue and package progressions.
          </p>
        </div>

        {/* Action Controls */}
        <div className="flex items-center gap-2">
          <button
            onClick={handleRefresh}
            disabled={loading}
            data-testid="refresh-assignments-btn"
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-[#121C2E] border border-[#263449] text-slate-300 hover:text-white hover:bg-[#18243A] font-semibold text-xs shadow-dark-sm transition-colors disabled:opacity-50"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin text-blue-400' : 'text-slate-400'}`} />
            <span>Refresh Queue</span>
          </button>
        </div>
      </div>

      {/* Real-Time Assignment Alert Banner */}
      {newAssignmentAlert && (
        <div
          data-testid="new-assignment-alert"
          className="bg-emerald-500/10 border border-emerald-500/30 rounded-2xl p-4 flex items-center justify-between shadow-dark-card animate-in fade-in slide-in-from-top-2 duration-300"
        >
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold">
              <Truck className="w-5 h-5 text-emerald-400 animate-bounce" />
            </div>
            <div>
              <p className="text-sm font-semibold text-emerald-300">
                New Delivery Assigned by Dispatch!
              </p>
              <p className="text-xs text-emerald-400/90">
                Tracking <span className="font-mono font-bold text-white">{newAssignmentAlert.trackingNumber}</span> for <span className="font-medium text-slate-200">{newAssignmentAlert.recipientName}</span> (Received at {newAssignmentAlert.timestamp})
              </p>
            </div>
          </div>
          <button
            onClick={() => setNewAssignmentAlert(null)}
            className="p-1.5 rounded-lg text-emerald-400 hover:bg-emerald-500/20 transition-colors"
            aria-label="Dismiss alert"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Operation Success Banner */}
      {successBanner && (
        <div
          data-testid="success-banner"
          className="bg-teal-500/10 border border-teal-500/30 rounded-xl p-3.5 flex items-center justify-between text-teal-300 text-sm shadow-dark-card"
        >
          <div className="flex items-center gap-2.5">
            <CheckCircle className="w-4 h-4 text-teal-400 shrink-0" />
            <span>{successBanner}</span>
          </div>
          <button
            onClick={() => setSuccessBanner(null)}
            className="p-1 rounded-md text-teal-400 hover:bg-teal-500/20"
            aria-label="Close"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      {/* Error Banner */}
      {error && (
        <div
          data-testid="error-banner"
          className="bg-rose-500/10 border border-rose-500/30 rounded-xl p-4 flex items-center justify-between text-rose-300 text-sm shadow-dark-card"
        >
          <div className="flex items-center gap-2.5">
            <AlertCircle className="w-4 h-4 text-rose-400 shrink-0" />
            <span>{error}</span>
          </div>
          <button
            onClick={fetchAssignments}
            className="px-3 py-1 text-xs font-semibold bg-rose-500/20 hover:bg-rose-500/30 border border-rose-500/40 text-rose-200 rounded-lg transition-colors"
          >
            Retry
          </button>
        </div>
      )}

      {/* Metric Queue Summary Cards */}
      <div className="grid grid-cols-2 sm:grid-cols-5 gap-3" data-testid="queue-summary-cards">
        <div className="bg-[#121C2E] border border-[#263449] hover:border-blue-500/40 rounded-2xl p-4 shadow-dark-card transition-all">
          <div className="flex items-center justify-between text-slate-400 mb-1">
            <span className="text-xs font-medium uppercase tracking-wider">Total Queue</span>
            <Package className="w-4 h-4 text-blue-400" />
          </div>
          <span className="text-2xl sm:text-3xl font-extrabold text-[#F8FAFC]" data-testid="metric-total-assigned">
            {summary.totalAssigned}
          </span>
        </div>

        <div className="bg-[#121C2E] border border-[#263449] hover:border-amber-500/40 rounded-2xl p-4 shadow-dark-card transition-all">
          <div className="flex items-center justify-between text-amber-400 mb-1">
            <span className="text-xs font-medium uppercase tracking-wider">Awaiting Pickup</span>
            <Clock className="w-4 h-4 text-amber-400" />
          </div>
          <span className="text-2xl sm:text-3xl font-extrabold text-amber-400" data-testid="metric-awaiting-pickup">
            {summary.awaitingPickup}
          </span>
        </div>

        <div className="bg-[#121C2E] border border-[#263449] hover:border-blue-500/40 rounded-2xl p-4 shadow-dark-card transition-all">
          <div className="flex items-center justify-between text-blue-400 mb-1">
            <span className="text-xs font-medium uppercase tracking-wider">In Transit</span>
            <Truck className="w-4 h-4 text-blue-400" />
          </div>
          <span className="text-2xl sm:text-3xl font-extrabold text-blue-400" data-testid="metric-in-transit">
            {summary.inTransit}
          </span>
        </div>

        <div className="bg-[#121C2E] border border-[#263449] hover:border-indigo-500/40 rounded-2xl p-4 shadow-dark-card transition-all">
          <div className="flex items-center justify-between text-indigo-400 mb-1">
            <span className="text-xs font-medium uppercase tracking-wider">Out for Delivery</span>
            <Navigation className="w-4 h-4 text-indigo-400" />
          </div>
          <span className="text-2xl sm:text-3xl font-extrabold text-indigo-400" data-testid="metric-out-for-delivery">
            {summary.outForDelivery}
          </span>
        </div>

        <div className="col-span-2 sm:col-span-1 bg-[#121C2E] border border-[#263449] hover:border-emerald-500/40 rounded-2xl p-4 shadow-dark-card transition-all">
          <div className="flex items-center justify-between text-emerald-400 mb-1">
            <span className="text-xs font-medium uppercase tracking-wider">Completed</span>
            <CheckCircle className="w-4 h-4 text-emerald-400" />
          </div>
          <span className="text-2xl sm:text-3xl font-extrabold text-emerald-400" data-testid="metric-completed">
            {summary.completed}
          </span>
        </div>
      </div>

      {/* Main Delivery Queue Section */}
      <div className="bg-[#121C2E] border border-[#263449] rounded-2xl shadow-dark-card overflow-hidden">
        {/* Status Filter Header */}
        <div className="border-b border-[#263449] p-4 sm:p-5 bg-[#0D1424]">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <div>
              <h2 className="text-base font-bold text-[#F8FAFC]">Assigned Shipments</h2>
              <p className="text-xs text-slate-400 mt-0.5">
                {totalElements} package{totalElements === 1 ? '' : 's'} in queue
              </p>
            </div>

            {/* Status Pills */}
            <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0 scrollbar-none" data-testid="status-filter-tabs">
              {STATUS_FILTERS.map((f) => (
                <button
                  key={f.key}
                  onClick={() => handleFilterChange(f.key)}
                  data-testid={`filter-${f.key}`}
                  className={`px-3 py-1.5 rounded-xl text-xs font-semibold whitespace-nowrap transition-all border ${
                    statusFilter === f.key
                      ? 'bg-blue-600 text-white border-blue-500 shadow-glow-blue'
                      : 'bg-[#121C2E] text-slate-400 border-[#263449] hover:bg-[#18243A] hover:text-[#F8FAFC]'
                  }`}
                >
                  {f.label}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Content Body */}
        {loading && assignments.length === 0 ? (
          <div className="p-16 text-center" data-testid="loading-state">
            <RefreshCw className="w-8 h-8 text-blue-500 animate-spin mx-auto mb-3" />
            <p className="text-sm font-semibold text-slate-200">Loading assigned shipments...</p>
            <p className="text-xs text-slate-400 mt-1">Fetching live driver route manifests.</p>
          </div>
        ) : assignments.length === 0 ? (
          <div className="p-16 text-center" data-testid="empty-queue-state">
            <div className="w-14 h-14 rounded-2xl bg-[#18243A] border border-[#263449] text-slate-400 flex items-center justify-center mx-auto mb-3 shadow-inner">
              <Package className="w-7 h-7 text-blue-400" />
            </div>
            <h3 className="text-sm font-bold text-[#F8FAFC]">No shipments found</h3>
            <p className="text-xs text-slate-400 mt-1 max-w-sm mx-auto">
              {statusFilter === 'ALL'
                ? 'You currently have no active deliveries assigned to your queue.'
                : `No shipments with status "${statusFilter}" were found in your assigned queue.`}
            </p>
            {statusFilter !== 'ALL' && (
              <button
                onClick={() => handleFilterChange('ALL')}
                className="mt-4 px-3.5 py-1.5 text-xs font-semibold text-blue-400 hover:bg-blue-500/10 rounded-xl border border-blue-500/30 transition-colors"
              >
                Clear status filter
              </button>
            )}
          </div>
        ) : (
          <div className="divide-y divide-[#263449]/60" data-testid="assignments-list">
            {assignments.map((shipment) => {
              const badge = getStatusBadge(shipment.status);
              const transitions = getPermittedTransitions(shipment.status);

              return (
                <div
                  key={shipment.id}
                  data-testid={`shipment-card-${shipment.id}`}
                  className="p-4 sm:p-5 hover:bg-[#18243A]/40 transition-colors"
                >
                  <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
                    {/* Left: Identification & Core Details */}
                    <div className="space-y-3 flex-1">
                      <div className="flex flex-wrap items-center gap-2.5">
                        <span className="font-mono text-sm font-bold text-cyan-400 bg-[#0D1424] px-2.5 py-0.5 rounded-lg border border-[#263449]">
                          {shipment.trackingNumber}
                        </span>
                        <span
                          className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold border ${badge.classes}`}
                          data-testid={`status-badge-${shipment.id}`}
                        >
                          <span className={`w-1.5 h-1.5 rounded-full ${badge.dot}`} />
                          {badge.label}
                        </span>
                        {shipment.status === 'OUT_FOR_DELIVERY' && (
                          <span
                            className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-amber-500/15 text-amber-300 border border-amber-500/30"
                            title="Proof-of-delivery OTP confirmation required"
                          >
                            <ShieldCheck className="w-3.5 h-3.5 text-amber-400" />
                            <span>OTP Mode</span>
                          </span>
                        )}
                      </div>

                      {/* Recipient & Origin Grid */}
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
                        <div className="bg-[#0D1424] p-3 rounded-xl border border-[#263449]">
                          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block mb-1">
                            Recipient & Delivery Address
                          </span>
                          <div className="font-semibold text-slate-200 flex items-center gap-1.5">
                            <User className="w-3.5 h-3.5 text-slate-400" />
                            <span>{shipment.recipientName}</span>
                          </div>
                          <div className="text-slate-300 flex items-start gap-1.5 mt-0.5">
                            <MapPin className="w-3.5 h-3.5 text-rose-400 shrink-0 mt-0.5" />
                            <span className="line-clamp-2">{shipment.recipientAddress}</span>
                          </div>
                          {shipment.recipientPhone && (
                            <div className="text-slate-400 flex items-center gap-1.5 mt-1 font-mono text-[11px]">
                              <Phone className="w-3 h-3 text-slate-400" />
                              <span>{shipment.recipientPhone}</span>
                            </div>
                          )}
                        </div>

                        <div className="bg-[#0D1424] p-3 rounded-xl border border-[#263449]">
                          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block mb-1">
                            Sender & Origin
                          </span>
                          <div className="text-slate-300 flex items-start gap-1.5">
                            <MapPin className="w-3.5 h-3.5 text-blue-400 shrink-0 mt-0.5" />
                            <span className="line-clamp-2">{shipment.originAddress || 'Origin Depot'}</span>
                          </div>
                          <div className="mt-2 flex items-center gap-3 text-slate-400 text-[11px]">
                            <span>Weight: <strong className="text-slate-200">{shipment.weightKg} kg</strong></span>
                            {shipment.estimatedPrice && (
                              <span>Fee: <strong className="text-slate-200">${Number(shipment.estimatedPrice).toFixed(2)}</strong></span>
                            )}
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Right: Progression Actions & Navigation */}
                    <div className="flex flex-col sm:flex-row lg:flex-col items-stretch sm:items-center lg:items-end gap-2 shrink-0">
                      {/* Permitted Workflow Status Transitions */}
                      {transitions.length > 0 && (
                        <div className="flex flex-wrap items-center gap-1.5 w-full sm:w-auto">
                          {transitions.map((t) => {
                            const IconComponent = t.icon;
                            return (
                              <button
                                key={t.target}
                                onClick={() => openTransitionModal(shipment, t.target)}
                                data-testid={`action-${t.target.toLowerCase()}-${shipment.id}`}
                                className={`inline-flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold transition-all shadow-dark-sm ${t.style}`}
                              >
                                <IconComponent className="w-3.5 h-3.5" />
                                <span>{t.label}</span>
                              </button>
                            );
                          })}
                        </div>
                      )}

                      {/* Out For Delivery Action & OTP Guidance */}
                      {shipment.status === 'OUT_FOR_DELIVERY' && (
                        <>
                          <Link
                            to={`/driver/delivery/${shipment.id}`}
                            data-testid={`delivery-mode-link-${shipment.id}`}
                            className="inline-flex items-center justify-center gap-1.5 px-3.5 py-1.5 rounded-xl text-xs font-bold text-white bg-blue-600 hover:bg-blue-500 shadow-glow-blue transition-all"
                          >
                            <Navigation className="w-3.5 h-3.5" />
                            <span>Delivery Mode (GPS & OTP)</span>
                          </Link>
                          <div className="text-[11px] text-amber-300 bg-amber-500/10 border border-amber-500/30 px-2.5 py-1 rounded-lg text-center sm:text-right">
                            Delivery completion requires OTP verification
                          </div>
                        </>
                      )}

                      {/* Live Tracking Link */}
                      <Link
                        to={`/track/${shipment.trackingNumber}`}
                        data-testid={`track-link-${shipment.id}`}
                        className="inline-flex items-center justify-center gap-1 px-3 py-1.5 rounded-xl bg-[#0D1424] hover:bg-[#18243A] text-cyan-400 border border-[#263449] text-xs font-semibold transition-colors"
                      >
                        <Navigation className="w-3.5 h-3.5 text-cyan-400" />
                        <span>Live Map</span>
                      </Link>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Server-Side Pagination Bar */}
        {totalPages > 1 && (
          <div
            className="border-t border-[#263449] px-4 py-3 flex items-center justify-between text-xs text-slate-400 bg-[#0D1424]"
            data-testid="pagination-controls"
          >
            <div>
              Page <span className="font-bold text-slate-200">{page + 1}</span> of{' '}
              <span className="font-bold text-slate-200">{totalPages}</span> ({totalElements} total)
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={isFirstPage || loading}
                data-testid="prev-page-btn"
                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl border border-[#263449] bg-[#121C2E] hover:bg-[#18243A] text-slate-300 disabled:opacity-30 disabled:cursor-not-allowed font-medium transition-colors shadow-dark-sm"
              >
                <ChevronLeft className="w-3.5 h-3.5" />
                <span>Prev</span>
              </button>
              <button
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={isLastPage || loading}
                data-testid="next-page-btn"
                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl border border-[#263449] bg-[#121C2E] hover:bg-[#18243A] text-slate-300 disabled:opacity-30 disabled:cursor-not-allowed font-medium transition-colors shadow-dark-sm"
              >
                <span>Next</span>
                <ChevronRight className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Status Progression Confirmation Modal */}
      {modalOpen && activeShipment && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-md p-4 overflow-y-auto"
          role="dialog"
          aria-modal="true"
          data-testid="status-transition-modal"
        >
          <div className="bg-[#121C2E] rounded-2xl shadow-dark-elevated max-w-md w-full overflow-hidden border border-[#263449] animate-in fade-in zoom-in-95 duration-150">
            {/* Modal Header */}
            <div className="border-b border-[#263449] p-4 flex items-center justify-between bg-[#0D1424]">
              <div>
                <h3 className="font-bold text-[#F8FAFC] text-sm">
                  Update Shipment Lifecycle Status
                </h3>
                <p className="text-xs text-cyan-400 font-mono mt-0.5">
                  {activeShipment.trackingNumber}
                </p>
              </div>
              <button
                onClick={closeTransitionModal}
                disabled={submittingTransition}
                className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-[#18243A] transition-colors"
                aria-label="Close dialog"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Modal Form */}
            <form onSubmit={handleSubmitTransition} className="p-5 space-y-4">
              {modalError && (
                <div
                  data-testid="modal-error-banner"
                  className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs flex items-center gap-2"
                >
                  <AlertCircle className="w-4 h-4 shrink-0 text-rose-400" />
                  <span>{modalError}</span>
                </div>
              )}

              {/* Status Change Overview */}
              <div className="bg-[#0D1424] border border-[#263449] rounded-xl p-3 flex items-center justify-between text-xs">
                <div>
                  <span className="text-[10px] uppercase font-bold text-slate-400 block">Current Status</span>
                  <span className="font-bold text-slate-200">{activeShipment.status.replace(/_/g, ' ')}</span>
                </div>
                <ChevronRight className="w-4 h-4 text-blue-400" />
                <div>
                  <span className="text-[10px] uppercase font-bold text-blue-400 block">Target Status</span>
                  <span className="font-bold text-cyan-400" data-testid="modal-target-status">
                    {targetStatus.replace(/_/g, ' ')}
                  </span>
                </div>
              </div>

              {/* Optional Waypoint / Scan Location Name */}
              <div>
                <label className="block text-xs font-bold text-slate-300 mb-1" htmlFor="locationName">
                  Scan Location / Hub Name <span className="text-slate-400 font-normal">(Optional)</span>
                </label>
                <div className="relative">
                  <MapPin className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
                  <input
                    id="locationName"
                    data-testid="input-location-name"
                    type="text"
                    value={locationName}
                    onChange={(e) => setLocationName(e.target.value)}
                    placeholder="e.g. North Terminal Sorting Facility"
                    maxLength={255}
                    className="w-full pl-9 pr-3 py-2 text-xs bg-[#0D1424] border border-[#263449] rounded-xl text-[#F8FAFC] placeholder-slate-400 focus:outline-none focus:border-blue-500 transition-colors"
                  />
                </div>
              </div>

              {/* Optional Remarks */}
              <div>
                <label className="block text-xs font-bold text-slate-300 mb-1" htmlFor="remarks">
                  Driver Remarks / Notes <span className="text-slate-400 font-normal">(Optional)</span>
                </label>
                <div className="relative">
                  <FileText className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
                  <textarea
                    id="remarks"
                    data-testid="input-remarks"
                    value={remarks}
                    onChange={(e) => setRemarks(e.target.value)}
                    placeholder={
                      targetStatus === 'ATTEMPT_FAILED'
                        ? 'e.g. Gate was locked, recipient did not answer phone'
                        : 'e.g. Loaded onto delivery van, all packages scanned'
                    }
                    rows={2}
                    maxLength={1000}
                    className="w-full pl-9 pr-3 py-2 text-xs bg-[#0D1424] border border-[#263449] rounded-xl text-[#F8FAFC] placeholder-slate-400 focus:outline-none focus:border-blue-500 transition-colors resize-none"
                  />
                </div>
              </div>

              {/* Action Buttons */}
              <div className="pt-2 flex items-center justify-end gap-2 border-t border-[#263449]">
                <button
                  type="button"
                  onClick={closeTransitionModal}
                  disabled={submittingTransition}
                  data-testid="modal-cancel-btn"
                  className="px-3.5 py-2 text-xs font-semibold text-slate-400 hover:text-white hover:bg-[#18243A] rounded-xl transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submittingTransition}
                  data-testid="modal-confirm-btn"
                  className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-bold text-white bg-blue-600 hover:bg-blue-500 rounded-xl shadow-glow-blue transition-all disabled:opacity-50"
                >
                  {submittingTransition && <RefreshCw className="w-3.5 h-3.5 animate-spin" />}
                  <span>{submittingTransition ? 'Updating...' : `Confirm ${targetStatus.replace(/_/g, ' ')}`}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
