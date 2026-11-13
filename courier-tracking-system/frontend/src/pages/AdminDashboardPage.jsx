import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Link } from 'react-router-dom';
import {
  Package,
  Truck,
  Users,
  Navigation,
  Clock,
  CheckCircle2,
  AlertTriangle,
  AlertCircle,
  RefreshCw,
  Search,
  Filter,
  ChevronLeft,
  ChevronRight,
  ExternalLink,
  MapPin,
  Calendar,
  X,
  Phone,
  Mail,
  User,
  ShieldCheck,
  Check,
  ArrowRight,
  UserCheck,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { adminService } from '../services/api';

const SHIPMENT_STATUSES = [
  { value: 'ALL', label: 'All Statuses' },
  { value: 'CREATED', label: 'Created' },
  { value: 'ASSIGNED', label: 'Assigned' },
  { value: 'PICKED_UP', label: 'Picked Up' },
  { value: 'IN_TRANSIT', label: 'In Transit' },
  { value: 'OUT_FOR_DELIVERY', label: 'Out for Delivery' },
  { value: 'DELIVERED', label: 'Delivered' },
  { value: 'ATTEMPT_FAILED', label: 'Attempt Failed' },
  { value: 'RETURNED', label: 'Returned' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

export function getStatusBadge(status) {
  switch (status) {
    case 'CREATED':
      return {
        label: 'Created',
        classes: 'bg-slate-500/10 text-slate-300 border-slate-500/30',
        dot: 'bg-slate-400',
      };
    case 'ASSIGNED':
      return {
        label: 'Assigned',
        classes: 'bg-purple-500/10 text-purple-300 border-purple-500/30',
        dot: 'bg-purple-400',
      };
    case 'PICKED_UP':
      return {
        label: 'Picked Up',
        classes: 'bg-cyan-500/10 text-cyan-300 border-cyan-500/30',
        dot: 'bg-cyan-400',
      };
    case 'IN_TRANSIT':
      return {
        label: 'In Transit',
        classes: 'bg-indigo-500/10 text-indigo-300 border-indigo-500/30',
        dot: 'bg-indigo-400',
      };
    case 'OUT_FOR_DELIVERY':
      return {
        label: 'Out for Delivery',
        classes: 'bg-blue-500/10 text-blue-300 border-blue-500/30',
        dot: 'bg-blue-400 animate-pulse',
      };
    case 'DELIVERED':
      return {
        label: 'Delivered',
        classes: 'bg-emerald-500/10 text-emerald-300 border-emerald-500/30',
        dot: 'bg-emerald-400',
      };
    case 'ATTEMPT_FAILED':
      return {
        label: 'Attempt Failed',
        classes: 'bg-rose-500/10 text-rose-300 border-rose-500/30',
        dot: 'bg-rose-400',
      };
    case 'RETURNED':
      return {
        label: 'Returned',
        classes: 'bg-amber-500/10 text-amber-300 border-amber-500/30',
        dot: 'bg-amber-400',
      };
    case 'CANCELLED':
      return {
        label: 'Cancelled',
        classes: 'bg-red-500/10 text-red-300 border-red-500/30',
        dot: 'bg-red-400',
      };
    default:
      return {
        label: status || 'Unknown',
        classes: 'bg-slate-800 text-slate-300 border-slate-700',
        dot: 'bg-slate-400',
      };
  }
}

export default function AdminDashboardPage() {
  const { user } = useAuth();

  // Active Tab: 'shipments' | 'agents'
  const [activeTab, setActiveTab] = useState('shipments');

  // Overview State
  const [overview, setOverview] = useState(null);
  const [overviewLoading, setOverviewLoading] = useState(true);
  const [overviewError, setOverviewError] = useState(null);

  // Shipments Management State
  const [shipments, setShipments] = useState([]);
  const [shipmentsLoading, setShipmentsLoading] = useState(true);
  const [shipmentsError, setShipmentsError] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [appliedSearch, setAppliedSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [unassignedOnly, setUnassignedOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [isFirstPage, setIsFirstPage] = useState(true);
  const [isLastPage, setIsLastPage] = useState(true);

  // Agent Directory State
  const [agents, setAgents] = useState([]);
  const [agentsLoading, setAgentsLoading] = useState(false);
  const [agentsError, setAgentsError] = useState(null);
  const [agentSearchQuery, setAgentSearchQuery] = useState('');

  // Shipment Details & Events Modal State
  const [selectedShipmentId, setSelectedShipmentId] = useState(null);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [detailsError, setDetailsError] = useState(null);
  const [shipmentDetails, setShipmentDetails] = useState(null);
  const [shipmentEvents, setShipmentEvents] = useState([]);

  // Agent Assignment State
  const [assignModalShipmentId, setAssignModalShipmentId] = useState(null);
  const [selectedAgentId, setSelectedAgentId] = useState('');
  const [assignLoading, setAssignLoading] = useState(false);
  const [assignError, setAssignError] = useState(null);
  const [assignSuccess, setAssignSuccess] = useState(false);

  // Request Sequence & Active Modal References
  const shipmentRequestIdRef = useRef(0);
  const activeModalShipmentIdRef = useRef(null);

  // Fetch Overview Metrics
  const fetchOverview = useCallback(async () => {
    setOverviewLoading(true);
    setOverviewError(null);

    try {
      const res = await adminService.getOverview();
      if (res.success && res.data) {
        setOverview(res.data);
      } else {
        setOverviewError(res.message || 'Failed to retrieve overview metrics');
      }
    } catch (err) {
      setOverviewError(err.message || 'Error communicating with administration backend');
    } finally {
      setOverviewLoading(false);
    }
  }, []);

  // Fetch Paginated Shipments with Request Sequence Protection
  const fetchShipments = useCallback(async () => {
    const currentReqId = ++shipmentRequestIdRef.current;
    setShipmentsLoading(true);
    setShipmentsError(null);

    try {
      const params = {
        page,
        size: pageSize,
        sort: 'createdAt,desc',
      };

      if (statusFilter !== 'ALL') {
        params.status = statusFilter;
      }

      if (appliedSearch) {
        params.search = appliedSearch;
      }

      if (unassignedOnly) {
        params.unassignedOnly = true;
      }

      const res = await adminService.getShipments(params);

      // Discard stale out-of-order response if another request has been initiated
      if (currentReqId !== shipmentRequestIdRef.current) return;

      if (res.success && res.data) {
        const pageData = res.data;
        setShipments(pageData.content || []);
        setTotalPages(pageData.totalPages || 0);
        setTotalElements(pageData.totalElements || 0);
        setIsFirstPage(pageData.first ?? (page === 0));
        setIsLastPage(pageData.last ?? (page >= (pageData.totalPages || 1) - 1));
      } else {
        setShipmentsError(res.message || 'Failed to retrieve shipments');
      }
    } catch (err) {
      if (currentReqId !== shipmentRequestIdRef.current) return;
      setShipmentsError(err.message || 'Error communicating with shipment service');
    } finally {
      if (currentReqId === shipmentRequestIdRef.current) {
        setShipmentsLoading(false);
      }
    }
  }, [page, pageSize, statusFilter, appliedSearch, unassignedOnly]);

  // Fetch Agents Directory
  const fetchAgents = useCallback(async () => {
    setAgentsLoading(true);
    setAgentsError(null);

    try {
      const res = await adminService.getAgents();
      if (res.success && res.data) {
        setAgents(Array.isArray(res.data) ? res.data : []);
      } else {
        setAgentsError(res.message || 'Failed to retrieve delivery agent directory');
      }
    } catch (err) {
      setAgentsError(err.message || 'Error retrieving delivery agent directory');
    } finally {
      setAgentsLoading(false);
    }
  }, []);

  // Fetch Shipment Details & Audit Events for Modal with Cancellation Protection
  const fetchShipmentDetails = useCallback(async (id) => {
    if (!id) return;
    activeModalShipmentIdRef.current = id;
    setSelectedShipmentId(id);
    setDetailsLoading(true);
    setDetailsError(null);
    setShipmentDetails(null);
    setShipmentEvents([]);

    try {
      const [detailsRes, eventsRes] = await Promise.allSettled([
        adminService.getShipmentById(id),
        adminService.getShipmentEvents(id),
      ]);

      // Discard response if modal was closed or switched to another consignment
      if (activeModalShipmentIdRef.current !== id) return;

      if (detailsRes.status === 'fulfilled' && detailsRes.value?.data) {
        setShipmentDetails(detailsRes.value.data);
      } else {
        const errorMsg = detailsRes.reason?.message || 'Failed to retrieve shipment details';
        setDetailsError(errorMsg);
        return;
      }

      if (eventsRes.status === 'fulfilled' && eventsRes.value?.data) {
        setShipmentEvents(Array.isArray(eventsRes.value.data) ? eventsRes.value.data : []);
      } else {
        setShipmentEvents([]);
      }
    } catch (err) {
      if (activeModalShipmentIdRef.current !== id) return;
      setDetailsError(err.message || 'Error communicating with shipment service');
    } finally {
      if (activeModalShipmentIdRef.current === id) {
        setDetailsLoading(false);
      }
    }
  }, []);

  const handleCloseModal = () => {
    activeModalShipmentIdRef.current = null;
    setSelectedShipmentId(null);
  };

  // Assign delivery agent to a CREATED shipment
  const handleAssignAgent = useCallback(async (shipmentId) => {
    if (!selectedAgentId) {
      setAssignError('Please select a delivery agent.');
      return;
    }
    setAssignLoading(true);
    setAssignError(null);
    setAssignSuccess(false);
    try {
      const res = await adminService.assignAgent(shipmentId, Number(selectedAgentId));
      if (res && res.success) {
        setAssignSuccess(true);
        setAssignModalShipmentId(null);
        setSelectedAgentId('');
        // Refresh lists
        fetchShipments();
        fetchOverview();
        // If details modal is open for this shipment, refresh it
        if (selectedShipmentId === shipmentId) {
          fetchShipmentDetails(shipmentId);
        }
      } else {
        setAssignError(res?.message || 'Assignment failed. Please try again.');
      }
    } catch (err) {
      setAssignError(err?.message || 'Error assigning agent.');
    } finally {
      setAssignLoading(false);
    }
  }, [selectedAgentId, fetchShipments, fetchOverview, selectedShipmentId, fetchShipmentDetails]);

  // Dedicated effects to avoid redundant calls to overview and agents upon shipment paging/filtering
  useEffect(() => {
    fetchOverview();
  }, [fetchOverview]);

  useEffect(() => {
    fetchAgents();
  }, [fetchAgents]);

  useEffect(() => {
    fetchShipments();
  }, [fetchShipments]);

  // Handle Search Submission
  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setAppliedSearch(searchQuery.trim());
    setPage(0);
  };

  // Handle Reset Filters
  const handleResetFilters = () => {
    setSearchQuery('');
    setAppliedSearch('');
    setStatusFilter('ALL');
    setUnassignedOnly(false);
    setPage(0);
  };

  // Filtered Agents for Directory Tab
  const filteredAgents = agents.filter((agent) => {
    if (!agentSearchQuery.trim()) return true;
    const query = agentSearchQuery.toLowerCase().trim();
    const nameMatch = (agent.fullName || '').toLowerCase().includes(query);
    const emailMatch = (agent.email || '').toLowerCase().includes(query);
    return nameMatch || emailMatch;
  });

  return (
    <div className="space-y-6">
      {/* Top Header & Breadcrumb */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-[#121C2E] p-6 rounded-2xl border border-[#263449] shadow-dark-card">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
            <ShieldCheck className="w-3.5 h-3.5 text-purple-400" />
            <span>Admin Operations</span>
            <span className="text-[#263449]">•</span>
            <span className="text-purple-400">Dispatch Control Center</span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-extrabold text-[#F8FAFC] tracking-tight flex items-center gap-2.5">
            <Package className="w-6 h-6 text-purple-400" />
            Logistics Dispatch &amp; Admin Dashboard
          </h1>
          <p className="text-xs sm:text-sm text-slate-400 mt-1">
            Consignment management, driver workload oversight, and real-time situational operations.
          </p>
        </div>

        <div className="flex items-center gap-3">
          {/* Quick Action: Open Fleet Map */}
          <Link
            to="/admin/fleet"
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-purple-600 hover:bg-purple-500 text-white text-xs font-bold shadow-glow-purple transition-all"
            data-testid="admin-open-fleet-map-btn"
          >
            <Truck className="w-4 h-4" />
            <span>Live Fleet Map</span>
          </Link>

          {/* Refresh Data Button */}
          <button
            onClick={() => {
              fetchOverview();
              fetchShipments();
              fetchAgents();
            }}
            disabled={overviewLoading || shipmentsLoading || agentsLoading}
            className="inline-flex items-center gap-2 px-3.5 py-2.5 rounded-xl border border-[#263449] bg-[#0D1424] hover:bg-[#18243A] text-slate-300 text-xs font-semibold transition-colors disabled:opacity-50"
            title="Refresh Dashboard Data"
            data-testid="admin-refresh-dashboard-btn"
          >
            <RefreshCw
              className={`w-3.5 h-3.5 text-slate-400 ${
                overviewLoading || shipmentsLoading || agentsLoading ? 'animate-spin text-purple-400' : ''
              }`}
            />
            <span className="hidden sm:inline">Refresh</span>
          </button>
        </div>
      </div>

      {/* Section 1: Overview Operational Metrics */}
      {overviewError ? (
        <div
          className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 flex items-center justify-between gap-3 text-xs"
          data-testid="overview-error-banner"
        >
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-rose-400 flex-shrink-0" />
            <span>Failed to load operational overview metrics: {overviewError}</span>
          </div>
          <button
            onClick={fetchOverview}
            className="px-3.5 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-500 text-white font-semibold transition-colors"
          >
            Retry
          </button>
        </div>
      ) : overviewLoading && !overview ? (
        <div
          className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-3"
          data-testid="overview-loading-skeleton"
        >
          {[...Array(8)].map((_, i) => (
            <div
              key={i}
              className="bg-[#121C2E] p-4 rounded-xl border border-[#263449] shadow-dark-sm animate-pulse space-y-2"
            >
              <div className="w-12 h-3 bg-[#18243A] rounded"></div>
              <div className="w-8 h-6 bg-[#18243A] rounded"></div>
              <div className="w-16 h-2 bg-[#18243A] rounded"></div>
            </div>
          ))}
        </div>
      ) : overview ? (
        <div
          className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-3"
          data-testid="overview-metrics-grid"
        >
          {/* 1. Total Shipments */}
          <div className="bg-[#121C2E] p-3.5 rounded-2xl border border-[#263449] shadow-dark-card space-y-1">
            <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wide block flex items-center gap-1">
              <Package className="w-3 h-3 text-blue-400" />
              Total
            </span>
            <div className="flex items-baseline gap-1.5">
              <span className="text-2xl font-extrabold text-[#F8FAFC]" data-testid="metric-total-shipments">
                {overview.totalShipments}
              </span>
              <span className="text-[10px] text-slate-400">units</span>
            </div>
            <span className="text-[10px] text-slate-400 block truncate">All-time consignments</span>
          </div>

          {/* 2. Unassigned Shipments */}
          <div
            onClick={() => {
              setUnassignedOnly(true);
              setActiveTab('shipments');
              setPage(0);
            }}
            className="bg-[#121C2E] p-3.5 rounded-2xl border border-amber-500/30 hover:border-amber-500/60 shadow-dark-card space-y-1 cursor-pointer transition-colors"
            title="Click to view unassigned shipments"
          >
            <span className="text-[11px] font-semibold text-amber-400 uppercase tracking-wide block flex items-center gap-1">
              <AlertCircle className="w-3 h-3 text-amber-400" />
              Unassigned
            </span>
            <div className="flex items-baseline gap-1.5">
              <span className="text-2xl font-extrabold text-amber-400" data-testid="metric-unassigned-shipments">
                {overview.unassignedShipments}
              </span>
              <span className="text-[10px] text-amber-300/80">needs agent</span>
            </div>
            <span className="text-[10px] text-amber-300/70 block truncate">Pending assignment</span>
          </div>

          {/* 3. Assigned Shipments */}
          <div className="bg-[#121C2E] p-3.5 rounded-2xl border border-[#263449] shadow-dark-card space-y-1">
            <span className="text-[11px] font-semibold text-indigo-400 uppercase tracking-wide block flex items-center gap-1">
              <Clock className="w-3 h-3 text-indigo-400" />
              Assigned
            </span>
            <div className="flex items-baseline gap-1.5">
              <span className="text-2xl font-extrabold text-indigo-400" data-testid="metric-assigned-shipments">
                {overview.assignedShipments}
              </span>
              <span className="text-[10px] text-slate-400">queued</span>
            </div>
            <span className="text-[10px] text-slate-400 block truncate">Awaiting pickup</span>
          </div>

          {/* 4. In Transit */}
          <div className="bg-[#121C2E] p-3.5 rounded-2xl border border-[#263449] shadow-dark-card space-y-1">
            <span className="text-[11px] font-semibold text-purple-400 uppercase tracking-wide block flex items-center gap-1">
              <Truck className="w-3 h-3 text-purple-400" />
              In Transit
            </span>
            <div className="flex items-baseline gap-1.5">
              <span className="text-2xl font-extrabold text-purple-400" data-testid="metric-in-transit-shipments">
                {overview.inTransitShipments}
              </span>
              <span className="text-[10px] text-slate-400">moving</span>
            </div>
            <span className="text-[10px] text-slate-400 block truncate">Hub conveyance</span>
          </div>

          {/* 5. Out For Delivery */}
          <div className="bg-[#121C2E] p-3.5 rounded-2xl border border-blue-500/30 shadow-dark-card space-y-1">
            <span className="text-[11px] font-semibold text-blue-400 uppercase tracking-wide block flex items-center gap-1">
              <Navigation className="w-3 h-3 text-blue-400" />
              Delivery Leg
            </span>
            <div className="flex items-baseline gap-1.5">
              <span className="text-2xl font-extrabold text-blue-400" data-testid="metric-out-for-delivery-shipments">
                {overview.outForDeliveryShipments}
              </span>
              <span className="text-[10px] text-blue-300">on road</span>
            </div>
            <span className="text-[10px] text-blue-300/80 block truncate">Final delivery leg</span>
          </div>

          {/* 6. Delivered */}
          <div className="bg-[#121C2E] p-3.5 rounded-2xl border border-emerald-500/30 shadow-dark-card space-y-1">
            <span className="text-[11px] font-semibold text-emerald-400 uppercase tracking-wide block flex items-center gap-1">
              <CheckCircle2 className="w-3 h-3 text-emerald-400" />
              Delivered
            </span>
            <div className="flex items-baseline gap-1.5">
              <span className="text-2xl font-extrabold text-emerald-400" data-testid="metric-delivered-shipments">
                {overview.deliveredShipments}
              </span>
              <span className="text-[10px] text-emerald-300">done</span>
            </div>
            <span className="text-[10px] text-emerald-300/80 block truncate">Completed orders</span>
          </div>

          {/* 7. Failed Deliveries */}
          <div className="bg-[#121C2E] p-3.5 rounded-2xl border border-rose-500/30 shadow-dark-card space-y-1">
            <span className="text-[11px] font-semibold text-rose-400 uppercase tracking-wide block flex items-center gap-1">
              <AlertTriangle className="w-3 h-3 text-rose-400" />
              Failed Att.
            </span>
            <div className="flex items-baseline gap-1.5">
              <span className="text-2xl font-extrabold text-rose-400" data-testid="metric-failed-delivery-shipments">
                {overview.failedDeliveryShipments}
              </span>
              <span className="text-[10px] text-rose-300">alerts</span>
            </div>
            <span className="text-[10px] text-rose-300/80 block truncate">Requires intervention</span>
          </div>

          {/* 8. Active Couriers */}
          <div
            onClick={() => setActiveTab('agents')}
            className="bg-[#121C2E] p-3.5 rounded-2xl border border-[#263449] shadow-dark-card space-y-1 cursor-pointer hover:border-slate-500 transition-colors"
            title="Click to view courier roster"
          >
            <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wide block flex items-center gap-1">
              <Users className="w-3 h-3 text-slate-400" />
              Couriers
            </span>
            <div className="flex items-baseline gap-1.5">
              <span className="text-2xl font-extrabold text-[#F8FAFC]" data-testid="metric-active-agents">
                {overview.activeAgents}
              </span>
              <span className="text-[10px] text-slate-400">active</span>
            </div>
            <span className="text-[10px] text-slate-400 block truncate">Enabled driver accounts</span>
          </div>
        </div>
      ) : null}

      {/* Section 2: Tab Navigation */}
      <div className="flex items-center gap-2 border-b border-[#263449] pb-2">
        <button
          onClick={() => setActiveTab('shipments')}
          className={`px-4 py-2.5 rounded-xl text-xs font-bold transition-all flex items-center gap-2 ${
            activeTab === 'shipments'
              ? 'bg-purple-600 text-white shadow-glow-purple'
              : 'text-slate-400 hover:text-[#F8FAFC] hover:bg-[#18243A]'
          }`}
          data-testid="tab-shipments"
        >
          <Package className="w-4 h-4" />
          <span>Shipment Management ({totalElements})</span>
        </button>

        <button
          onClick={() => setActiveTab('agents')}
          className={`px-4 py-2.5 rounded-xl text-xs font-bold transition-all flex items-center gap-2 ${
            activeTab === 'agents'
              ? 'bg-purple-600 text-white shadow-glow-purple'
              : 'text-slate-400 hover:text-[#F8FAFC] hover:bg-[#18243A]'
          }`}
          data-testid="tab-agents"
        >
          <Users className="w-4 h-4" />
          <span>Delivery Agent Directory ({agents.length})</span>
        </button>
      </div>

      {/* Tab Content 1: Shipments Management */}
      {activeTab === 'shipments' && (
        <div className="space-y-4" data-testid="shipments-management-section">
          {/* Filter & Search Bar */}
          <div className="bg-[#121C2E] p-4 rounded-2xl border border-[#263449] shadow-dark-card space-y-3">
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
              {/* Search Input */}
              <form onSubmit={handleSearchSubmit} className="relative">
                <Search className="w-4 h-4 text-slate-400 absolute left-3 top-3 pointer-events-none" />
                <input
                  type="text"
                  placeholder="Search tracking number..."
                  value={searchQuery}
                  onChange={(e) => {
                    setSearchQuery(e.target.value);
                    if (e.target.value === '' && appliedSearch !== '') {
                      setAppliedSearch('');
                      setPage(0);
                    }
                  }}
                  className="w-full pl-9 pr-8 py-2.5 rounded-xl border border-[#263449] bg-[#0D1424] text-xs text-[#F8FAFC] placeholder-slate-400 focus:outline-none focus:border-purple-500 transition-colors"
                  data-testid="shipment-search-input"
                />
                {searchQuery && (
                  <button
                    type="button"
                    onClick={() => {
                      setSearchQuery('');
                      setAppliedSearch('');
                      setPage(0);
                    }}
                    className="absolute right-2.5 top-3 text-slate-400 hover:text-white"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                )}
              </form>

              {/* Status Dropdown */}
              <div>
                <select
                  value={statusFilter}
                  onChange={(e) => {
                    setStatusFilter(e.target.value);
                    setPage(0);
                  }}
                  className="w-full px-3 py-2.5 rounded-xl border border-[#263449] text-xs text-slate-200 bg-[#0D1424] focus:outline-none focus:border-purple-500 transition-colors"
                  data-testid="shipment-status-filter"
                >
                  {SHIPMENT_STATUSES.map((status) => (
                    <option key={status.value} value={status.value}>
                      {status.label}
                    </option>
                  ))}
                </select>
              </div>

              {/* Unassigned Only Toggle */}
              <div className="flex items-center gap-2 px-3 py-2.5 rounded-xl border border-[#263449] bg-[#0D1424]">
                <input
                  type="checkbox"
                  id="unassigned-toggle"
                  checked={unassignedOnly}
                  onChange={(e) => {
                    setUnassignedOnly(e.target.checked);
                    setPage(0);
                  }}
                  className="w-4 h-4 text-purple-600 rounded bg-[#121C2E] border-[#263449] focus:ring-purple-500"
                  data-testid="unassigned-filter-toggle"
                />
                <label
                  htmlFor="unassigned-toggle"
                  className="text-xs font-semibold text-slate-300 cursor-pointer select-none"
                >
                  Unassigned Only
                </label>
              </div>

              {/* Page Size & Reset Actions */}
              <div className="flex items-center gap-2">
                <select
                  value={pageSize}
                  onChange={(e) => {
                    setPageSize(Number(e.target.value));
                    setPage(0);
                  }}
                  className="px-2.5 py-2.5 rounded-xl border border-[#263449] text-xs text-slate-200 bg-[#0D1424] focus:outline-none focus:border-purple-500"
                  data-testid="page-size-selector"
                >
                  <option value={5}>5 / page</option>
                  <option value={10}>10 / page</option>
                  <option value={20}>20 / page</option>
                  <option value={50}>50 / page</option>
                </select>

                <button
                  type="button"
                  onClick={handleResetFilters}
                  className="flex-1 px-3 py-2.5 rounded-xl border border-[#263449] bg-[#0D1424] hover:bg-[#18243A] text-slate-300 text-xs font-semibold transition-colors shadow-dark-sm"
                  data-testid="reset-filters-btn"
                >
                  Reset
                </button>
              </div>
            </div>
          </div>

          {/* Shipment Table Container */}
          <div className="bg-[#121C2E] rounded-2xl border border-[#263449] shadow-dark-card overflow-hidden">
            {shipmentsError ? (
              <div
                className="p-8 text-center space-y-3"
                data-testid="shipments-error-state"
              >
                <AlertTriangle className="w-8 h-8 text-rose-400 mx-auto" />
                <p className="text-sm font-semibold text-slate-200">
                  Error loading consignments: {shipmentsError}
                </p>
                <button
                  onClick={fetchShipments}
                  className="px-4 py-2 rounded-xl bg-purple-600 hover:bg-purple-500 text-white text-xs font-semibold shadow-glow-purple transition-colors"
                >
                  Retry Query
                </button>
              </div>
            ) : shipmentsLoading ? (
              <div
                className="p-12 text-center space-y-3"
                data-testid="shipments-loading-state"
              >
                <RefreshCw className="w-7 h-7 text-purple-400 animate-spin mx-auto" />
                <p className="text-xs font-semibold text-slate-400">Retrieving system consignments...</p>
              </div>
            ) : shipments.length === 0 ? (
              <div
                className="p-12 text-center space-y-3"
                data-testid="shipments-empty-state"
              >
                <Package className="w-10 h-10 text-slate-500 mx-auto" />
                <h3 className="text-sm font-bold text-[#F8FAFC]">No Consignments Match Selected Filters</h3>
                <p className="text-xs text-slate-400 max-w-sm mx-auto">
                  Try clearing your search terms or adjusting the status and unassigned filters.
                </p>
                <button
                  onClick={handleResetFilters}
                  className="px-4 py-2 rounded-xl border border-[#263449] bg-[#0D1424] hover:bg-[#18243A] text-slate-300 text-xs font-semibold shadow-dark-sm"
                >
                  Clear Filters
                </button>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs text-slate-300" data-testid="shipments-table">
                  <thead className="bg-[#0D1424] border-b border-[#263449] text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                    <tr>
                      <th className="py-3.5 px-4">Tracking Waybill</th>
                      <th className="py-3.5 px-4">Status</th>
                      <th className="py-3.5 px-4">Assigned Agent</th>
                      <th className="py-3.5 px-4">Sender &amp; Recipient</th>
                      <th className="py-3.5 px-4">Weight &amp; Price</th>
                      <th className="py-3.5 px-4">Created Date</th>
                      <th className="py-3.5 px-4 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#263449]/60">
                    {shipments.map((shipment) => {
                      const statusBadge = getStatusBadge(shipment.status);

                      return (
                        <tr
                          key={shipment.id}
                          className="hover:bg-[#18243A]/40 transition-colors"
                          data-testid={`shipment-row-${shipment.id}`}
                        >
                          {/* Tracking Number */}
                          <td className="py-3.5 px-4 font-mono font-bold text-cyan-400">
                            <span className="block">{shipment.trackingNumber}</span>
                            <span className="text-[10px] font-sans font-normal text-slate-400">
                              ID #{shipment.id}
                            </span>
                          </td>

                          {/* Status */}
                          <td className="py-3.5 px-4">
                            <span
                              className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-[10px] font-bold ${statusBadge.classes}`}
                            >
                              <span className={`w-1.5 h-1.5 rounded-full ${statusBadge.dot}`}></span>
                              {statusBadge.label}
                            </span>
                          </td>

                          {/* Assigned Agent */}
                          <td className="py-3.5 px-4">
                            {shipment.assignedAgentName ? (
                              <div className="space-y-0.5">
                                <span className="font-semibold text-slate-200 block">
                                  {shipment.assignedAgentName}
                                </span>
                                <span className="text-[10px] text-slate-400 font-mono">
                                  Agent ID #{shipment.assignedAgentId}
                                </span>
                              </div>
                            ) : (
                              <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold bg-amber-500/15 text-amber-300 border border-amber-500/30">
                                Unassigned
                              </span>
                            )}
                          </td>

                          {/* Sender & Recipient */}
                          <td className="py-3.5 px-4 space-y-0.5">
                            <div className="flex items-center gap-1 text-slate-200 font-medium truncate max-w-[180px]">
                              <span className="text-slate-400 text-[10px]">To:</span>
                              <span>{shipment.recipientName}</span>
                            </div>
                            <div className="text-[10px] text-slate-400 truncate max-w-[180px]">
                              {shipment.recipientAddress}
                            </div>
                          </td>

                          {/* Weight & Price */}
                          <td className="py-3.5 px-4">
                            <div className="font-semibold text-[#F8FAFC]">
                              {shipment.currency || 'USD'} {Number(shipment.price || 0).toFixed(2)}
                            </div>
                            <div className="text-[10px] text-slate-400">
                              {shipment.weightKg ? `${shipment.weightKg} kg` : 'N/A'}
                            </div>
                          </td>

                          {/* Created Date */}
                          <td className="py-3.5 px-4 text-slate-400 text-[11px]">
                            {shipment.createdAt
                              ? new Date(shipment.createdAt).toLocaleDateString([], {
                                  month: 'short',
                                  day: 'numeric',
                                  year: 'numeric',
                                })
                              : '—'}
                          </td>

                          {/* Actions */}
                          <td className="py-3.5 px-4 text-right">
                            <div className="flex items-center justify-end gap-2">
                              {/* Assign Agent - only shown for unassigned / CREATED shipments */}
                              {!shipment.assignedAgentId && shipment.status === 'CREATED' && (
                                <div className="relative">
                                  {assignModalShipmentId === shipment.id ? (
                                    <div className="flex items-center gap-1.5" data-testid={`assign-panel-${shipment.id}`}>
                                      <select
                                        value={selectedAgentId}
                                        onChange={(e) => { setSelectedAgentId(e.target.value); setAssignError(null); }}
                                        className="text-xs border border-[#263449] rounded-lg px-2 py-1.5 text-slate-200 focus:outline-none focus:border-indigo-500 bg-[#0D1424] shadow-sm"
                                        data-testid={`assign-agent-select-${shipment.id}`}
                                        disabled={assignLoading}
                                      >
                                        <option value="">— Select Agent —</option>
                                        {agents
                                          .filter((a) => a.enabled !== false)
                                          .map((a) => (
                                            <option key={a.id} value={a.id}>
                                              {a.fullName}
                                            </option>
                                          ))}
                                      </select>
                                      <button
                                        onClick={() => handleAssignAgent(shipment.id)}
                                        disabled={assignLoading || !selectedAgentId}
                                        className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold shadow-sm transition-colors disabled:opacity-50"
                                        data-testid={`assign-confirm-btn-${shipment.id}`}
                                      >
                                        {assignLoading ? '...' : <><Check className="w-3 h-3" /><span>Assign</span></>}
                                      </button>
                                      <button
                                        onClick={() => { setAssignModalShipmentId(null); setSelectedAgentId(''); setAssignError(null); }}
                                        className="inline-flex items-center px-2 py-1.5 rounded-lg border border-[#263449] bg-[#0D1424] hover:bg-[#18243A] text-slate-400 hover:text-white text-xs shadow-sm transition-colors"
                                        data-testid={`assign-cancel-btn-${shipment.id}`}
                                      >
                                        <X className="w-3 h-3" />
                                      </button>
                                    </div>
                                  ) : (
                                    <button
                                      onClick={() => { setAssignModalShipmentId(shipment.id); setAssignError(null); setAssignSuccess(false); }}
                                      className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg border border-indigo-500/30 bg-indigo-500/10 hover:bg-indigo-500/20 text-indigo-400 text-xs font-semibold shadow-sm transition-colors"
                                      data-testid={`assign-agent-btn-${shipment.id}`}
                                    >
                                      <UserCheck className="w-3 h-3" />
                                      <span>Assign</span>
                                    </button>
                                  )}
                                  {assignError && assignModalShipmentId === shipment.id && (
                                    <p className="absolute top-full left-0 mt-1 text-[10px] text-rose-300 bg-[#0D1424] border border-rose-500/30 rounded-lg px-2 py-1 shadow z-10 whitespace-nowrap" data-testid="assign-error-msg">{assignError}</p>
                                  )}
                                </div>
                              )}

                              <button
                                onClick={() => fetchShipmentDetails(shipment.id)}
                                className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg border border-[#263449] bg-[#0D1424] hover:bg-[#18243A] text-purple-400 text-xs font-semibold shadow-sm transition-colors"
                                data-testid={`view-shipment-${shipment.id}`}
                              >
                                <span>Details</span>
                                <ArrowRight className="w-3 h-3 text-purple-400" />
                              </button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}

            {/* Pagination Controls */}
            {!shipmentsLoading && shipments.length > 0 && (
              <div
                className="flex flex-col sm:flex-row items-center justify-between gap-3 p-4 border-t border-[#263449] bg-[#0D1424] text-xs text-slate-400"
                data-testid="shipments-pagination"
              >
                <div>
                  Showing <span className="font-bold text-slate-200">{page * pageSize + 1}</span> to{' '}
                  <span className="font-bold text-slate-200">
                    {Math.min((page + 1) * pageSize, totalElements)}
                  </span>{' '}
                  of <span className="font-bold text-slate-200">{totalElements}</span> consignments
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setPage((prev) => Math.max(0, prev - 1))}
                    disabled={isFirstPage || page === 0}
                    className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border border-[#263449] bg-[#121C2E] hover:bg-[#18243A] text-slate-300 font-semibold disabled:opacity-30 disabled:cursor-not-allowed transition-colors shadow-dark-sm"
                    data-testid="pagination-prev-btn"
                  >
                    <ChevronLeft className="w-3.5 h-3.5" />
                    <span>Previous</span>
                  </button>

                  <span className="px-2 font-medium text-slate-300" data-testid="pagination-current-page">
                    Page {page + 1} of {Math.max(1, totalPages)}
                  </span>

                  <button
                    onClick={() => setPage((prev) => Math.min(totalPages - 1, prev + 1))}
                    disabled={isLastPage || page >= totalPages - 1}
                    className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border border-[#263449] bg-[#121C2E] hover:bg-[#18243A] text-slate-300 font-semibold disabled:opacity-30 disabled:cursor-not-allowed transition-colors shadow-dark-sm"
                    data-testid="pagination-next-btn"
                  >
                    <span>Next</span>
                    <ChevronRight className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Tab Content 2: Delivery Agent Directory */}
      {activeTab === 'agents' && (
        <div className="space-y-4" data-testid="agents-directory-section">
          {/* Agent Search & Notice Bar */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-[#121C2E] p-4 rounded-2xl border border-[#263449] shadow-dark-card">
            <div className="relative flex-1 max-w-md">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-3 pointer-events-none" />
              <input
                type="text"
                placeholder="Filter agents by name or email..."
                value={agentSearchQuery}
                onChange={(e) => setAgentSearchQuery(e.target.value)}
                className="w-full pl-9 pr-3 py-2.5 rounded-xl border border-[#263449] bg-[#0D1424] text-xs text-[#F8FAFC] placeholder-slate-400 focus:outline-none focus:border-purple-500"
                data-testid="agent-search-input"
              />
            </div>

            <div className="text-[11px] text-slate-400 bg-[#0D1424] px-3.5 py-2 rounded-xl border border-[#263449]">
              Note: "Account Enabled" indicates system authorization, not real-time GPS connectivity.
            </div>
          </div>

          {/* Agents Grid / Table Container */}
          <div className="bg-[#121C2E] rounded-2xl border border-[#263449] shadow-dark-card overflow-hidden">
            {agentsError ? (
              <div
                className="p-8 text-center space-y-3"
                data-testid="agents-error-state"
              >
                <AlertTriangle className="w-8 h-8 text-rose-400 mx-auto" />
                <p className="text-sm font-semibold text-slate-200">
                  Failed to load delivery agent directory: {agentsError}
                </p>
                <button
                  onClick={fetchAgents}
                  className="px-4 py-2 rounded-xl bg-purple-600 hover:bg-purple-500 text-white text-xs font-semibold shadow-glow-purple transition-colors"
                >
                  Retry
                </button>
              </div>
            ) : agentsLoading ? (
              <div
                className="p-12 text-center space-y-3"
                data-testid="agents-loading-state"
              >
                <RefreshCw className="w-7 h-7 text-purple-400 animate-spin mx-auto" />
                <p className="text-xs font-semibold text-slate-400">Retrieving agent roster...</p>
              </div>
            ) : filteredAgents.length === 0 ? (
              <div
                className="p-12 text-center space-y-3"
                data-testid="agents-empty-state"
              >
                <Users className="w-10 h-10 text-slate-500 mx-auto" />
                <h3 className="text-sm font-bold text-[#F8FAFC]">No Delivery Agents Found</h3>
                <p className="text-xs text-slate-400 max-w-sm mx-auto">
                  No agents matched your search query. Try clearing the filter.
                </p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs text-slate-300" data-testid="agents-table">
                  <thead className="bg-[#0D1424] border-b border-[#263449] text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                    <tr>
                      <th className="py-3.5 px-4">Courier Name &amp; ID</th>
                      <th className="py-3.5 px-4">Account Status</th>
                      <th className="py-3.5 px-4">Contact Info</th>
                      <th className="py-3.5 px-4">Total Assigned</th>
                      <th className="py-3.5 px-4">Active Deliveries</th>
                      <th className="py-3.5 px-4">Delivered</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#263449]/60">
                    {filteredAgents.map((agent) => (
                      <tr
                        key={agent.id}
                        className="hover:bg-[#18243A]/40 transition-colors"
                        data-testid={`agent-row-${agent.id}`}
                      >
                        {/* Name & ID */}
                        <td className="py-3.5 px-4">
                          <div className="flex items-center gap-2.5">
                            <div className="w-8 h-8 rounded-full bg-purple-500/20 text-purple-300 font-bold flex items-center justify-center shrink-0 border border-purple-500/30">
                              {agent.fullName ? agent.fullName.charAt(0).toUpperCase() : 'A'}
                            </div>
                            <div>
                              <span className="font-bold text-slate-200 block">{agent.fullName}</span>
                              <span className="text-[10px] text-slate-400 font-mono">
                                Driver ID #{agent.id}
                              </span>
                            </div>
                          </div>
                        </td>

                        {/* Account Status */}
                        <td className="py-3.5 px-4">
                          {agent.active ? (
                            <span
                              className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30"
                              data-testid={`agent-status-${agent.id}`}
                            >
                              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400"></span>
                              Account Enabled
                            </span>
                          ) : (
                            <span
                              className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-bold bg-slate-800 text-slate-400 border border-slate-700"
                              data-testid={`agent-status-${agent.id}`}
                            >
                              <span className="w-1.5 h-1.5 rounded-full bg-slate-500"></span>
                              Account Deactivated
                            </span>
                          )}
                        </td>

                        {/* Contact Info */}
                        <td className="py-3.5 px-4 space-y-0.5">
                          <div className="flex items-center gap-1.5 text-slate-300">
                            <Mail className="w-3 h-3 text-slate-400" />
                            <span className="truncate max-w-[180px]">{agent.email}</span>
                          </div>
                          {agent.phoneNumber && (
                            <div className="flex items-center gap-1.5 text-[10px] text-slate-400">
                              <Phone className="w-3 h-3 text-slate-400" />
                              <span>{agent.phoneNumber}</span>
                            </div>
                          )}
                        </td>

                        {/* Total Assigned */}
                        <td className="py-3.5 px-4 font-semibold text-slate-200">
                          {agent.totalAssignedShipments || 0}
                        </td>

                        {/* Active Deliveries */}
                        <td className="py-3.5 px-4 font-semibold text-blue-400">
                          {agent.activeDeliveriesCount || 0}
                        </td>

                        {/* Delivered Shipments */}
                        <td className="py-3.5 px-4 font-semibold text-emerald-400">
                          {agent.deliveredShipmentsCount || 0}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Section 4: Shipment Details & Tracking Events Modal */}
      {selectedShipmentId && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-md overflow-y-auto"
          data-testid="shipment-details-modal"
          role="dialog"
          aria-modal="true"
          onClick={(e) => {
            if (e.target === e.currentTarget) handleCloseModal();
          }}
        >
          <div className="bg-[#121C2E] rounded-3xl border border-[#263449] shadow-dark-elevated max-w-3xl w-full max-h-[90vh] flex flex-col overflow-hidden my-8 animate-in zoom-in-95 duration-150">
            {/* Modal Header */}
            <div className="flex items-center justify-between p-6 border-b border-[#263449] bg-[#0D1424]">
              <div className="space-y-0.5">
                <div className="flex items-center gap-2">
                  <Package className="w-5 h-5 text-purple-400" />
                  <h3 className="text-lg font-bold text-[#F8FAFC]">
                    Consignment Audit Details
                  </h3>
                  {shipmentDetails && (
                    <span
                      className={`ml-2 px-2.5 py-0.5 rounded-full text-[10px] font-bold border ${
                        getStatusBadge(shipmentDetails.status).classes
                      }`}
                    >
                      {getStatusBadge(shipmentDetails.status).label}
                    </span>
                  )}
                </div>
                <p className="text-xs text-cyan-400 font-mono">
                  Waybill: {shipmentDetails?.trackingNumber || `Shipment ID #${selectedShipmentId}`}
                </p>
              </div>

              <button
                onClick={handleCloseModal}
                className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-[#18243A] transition-colors"
                aria-label="Close dialog"
                data-testid="close-modal-btn"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Modal Body */}
            <div className="p-6 overflow-y-auto space-y-6 flex-1 text-xs">
              {detailsLoading ? (
                <div className="py-16 text-center space-y-3" data-testid="modal-loading-state">
                  <RefreshCw className="w-7 h-7 text-purple-400 animate-spin mx-auto" />
                  <p className="text-xs font-semibold text-slate-400">
                    Retrieving full consignment record and audit milestones...
                  </p>
                </div>
              ) : detailsError ? (
                <div className="py-12 text-center space-y-3" data-testid="modal-error-state">
                  <AlertTriangle className="w-8 h-8 text-rose-400 mx-auto" />
                  <p className="text-xs font-semibold text-rose-300">{detailsError}</p>
                  <button
                    onClick={() => fetchShipmentDetails(selectedShipmentId)}
                    className="px-4 py-2 rounded-xl bg-purple-600 hover:bg-purple-500 text-white font-semibold transition-colors shadow-glow-purple"
                  >
                    Retry
                  </button>
                </div>
              ) : shipmentDetails ? (
                <div className="space-y-6">
                  {/* Grid 1: Consignment Summary */}
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    {/* Origin / Sender Card */}
                    <div className="bg-[#0D1424] p-4 rounded-2xl border border-[#263449] space-y-2">
                      <span className="font-bold text-[#F8FAFC] block flex items-center gap-1.5 text-xs">
                        <User className="w-3.5 h-3.5 text-slate-400" />
                        Sender / Origin
                      </span>
                      <div className="space-y-1 text-slate-300 text-[11px]">
                        <div className="font-semibold text-slate-200">{shipmentDetails.senderName}</div>
                        <div>{shipmentDetails.senderAddress}</div>
                        {shipmentDetails.senderPhone && (
                          <div className="flex items-center gap-1 text-slate-400">
                            <Phone className="w-3 h-3" />
                            <span>{shipmentDetails.senderPhone}</span>
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Destination / Recipient Card */}
                    <div className="bg-[#0D1424] p-4 rounded-2xl border border-[#263449] space-y-2">
                      <span className="font-bold text-[#F8FAFC] block flex items-center gap-1.5 text-xs">
                        <MapPin className="w-3.5 h-3.5 text-blue-400" />
                        Recipient / Destination
                      </span>
                      <div className="space-y-1 text-slate-300 text-[11px]">
                        <div className="font-semibold text-slate-200">{shipmentDetails.recipientName}</div>
                        <div>{shipmentDetails.recipientAddress}</div>
                        {shipmentDetails.recipientPhone && (
                          <div className="flex items-center gap-1 text-slate-400">
                            <Phone className="w-3 h-3" />
                            <span>{shipmentDetails.recipientPhone}</span>
                          </div>
                        )}
                        {shipmentDetails.recipientLatitude && shipmentDetails.recipientLongitude && (
                          <div className="text-[10px] text-cyan-400 font-mono">
                            Coords: {Number(shipmentDetails.recipientLatitude).toFixed(4)},{' '}
                            {Number(shipmentDetails.recipientLongitude).toFixed(4)}
                          </div>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Grid 2: Assignment & Logistics Specs */}
                  <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 bg-[#0D1424] p-4 rounded-2xl border border-[#263449] text-[11px]">
                    <div>
                      <span className="text-slate-400 block mb-0.5">Assigned Agent</span>
                      <span className="font-semibold text-slate-200 block">
                        {shipmentDetails.assignedAgentName || 'Unassigned'}
                      </span>
                      {shipmentDetails.assignedAgentId && (
                        <span className="text-[10px] text-slate-400 font-mono">
                          ID #{shipmentDetails.assignedAgentId}
                        </span>
                      )}
                    </div>

                    <div>
                      <span className="text-slate-400 block mb-0.5">Customer</span>
                      <span className="font-semibold text-slate-200 block truncate">
                        {shipmentDetails.customerEmail || `User #${shipmentDetails.customerId || '—'}`}
                      </span>
                    </div>

                    <div>
                      <span className="text-slate-400 block mb-0.5">Weight &amp; Price</span>
                      <span className="font-semibold text-slate-200 block">
                        {shipmentDetails.weightKg ? `${shipmentDetails.weightKg} kg` : 'N/A'} •{' '}
                        {shipmentDetails.currency || 'USD'} {Number(shipmentDetails.price || 0).toFixed(2)}
                      </span>
                    </div>

                    <div>
                      <span className="text-slate-400 block mb-0.5">Booking Date</span>
                      <span className="font-semibold text-slate-200 block">
                        {shipmentDetails.createdAt
                          ? new Date(shipmentDetails.createdAt).toLocaleDateString([], {
                              month: 'short',
                              day: 'numeric',
                              year: 'numeric',
                            })
                          : '—'}
                      </span>
                    </div>

                    <div>
                      <span className="text-slate-400 block mb-0.5">Delivery Timeline</span>
                      <span className="font-semibold text-slate-200 block">
                        {shipmentDetails.actualDeliveryAt ? (
                          <span className="text-emerald-400">
                            Delivered:{' '}
                            {new Date(shipmentDetails.actualDeliveryAt).toLocaleDateString([], {
                              month: 'short',
                              day: 'numeric',
                            })}
                          </span>
                        ) : shipmentDetails.estimatedDeliveryAt ? (
                          <span className="text-blue-400">
                            Est:{' '}
                            {new Date(shipmentDetails.estimatedDeliveryAt).toLocaleDateString([], {
                              month: 'short',
                              day: 'numeric',
                            })}
                          </span>
                        ) : (
                          'In Progress'
                        )}
                      </span>
                    </div>
                  </div>

                  {/* Section: Chronological Audit Milestones */}
                  <div className="space-y-3">
                    <h4 className="font-bold text-[#F8FAFC] text-xs flex items-center gap-2">
                      <Clock className="w-4 h-4 text-purple-400" />
                      Chronological Tracking Audit Milestones ({shipmentEvents.length})
                    </h4>

                    {shipmentEvents.length === 0 ? (
                      <div className="p-6 text-center rounded-xl border border-dashed border-[#263449] text-slate-400 text-xs">
                        No tracking event milestones recorded yet for this consignment.
                      </div>
                    ) : (
                      <div className="space-y-3 border-l-2 border-purple-500/40 ml-3 pl-4">
                        {shipmentEvents.map((evt, idx) => (
                          <div key={idx} className="relative pb-3 text-xs space-y-0.5">
                            {/* Timeline bullet */}
                            <span className="absolute -left-[23px] top-1 w-3 h-3 rounded-full bg-purple-500 border-2 border-[#121C2E] shadow-sm"></span>

                            <div className="flex items-center justify-between">
                              <span className="font-bold text-slate-200">{evt.status}</span>
                              <span className="text-[10px] text-cyan-400 font-mono">
                                {evt.timestamp
                                  ? new Date(evt.timestamp).toLocaleString([], {
                                      month: 'short',
                                      day: 'numeric',
                                      hour: '2-digit',
                                      minute: '2-digit',
                                    })
                                  : '—'}
                              </span>
                            </div>

                            <div className="text-[11px] text-slate-400">
                              <span className="font-medium text-slate-300">{evt.locationName || 'Hub'}</span>
                              {evt.remarks && <span> — {evt.remarks}</span>}
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              ) : null}
            </div>

            {/* Modal Footer */}
            <div className="p-4 border-t border-[#263449] bg-[#0D1424] flex items-center justify-between text-xs">
              {shipmentDetails?.trackingNumber ? (
                <Link
                  to={`/track/${shipmentDetails.trackingNumber}`}
                  className="inline-flex items-center gap-1.5 text-cyan-400 hover:text-cyan-300 font-semibold"
                >
                  <ExternalLink className="w-3.5 h-3.5" />
                  <span>Open Public Live Tracking</span>
                </Link>
              ) : (
                <div></div>
              )}

              <button
                onClick={handleCloseModal}
                className="px-4 py-2 rounded-xl border border-[#263449] bg-[#121C2E] hover:bg-[#18243A] text-slate-200 font-semibold shadow-dark-sm transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
