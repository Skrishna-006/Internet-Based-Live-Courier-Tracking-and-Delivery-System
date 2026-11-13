import React, { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { Link } from 'react-router-dom';
import {
  Truck,
  Radio,
  AlertTriangle,
  RefreshCw,
  Search,
  Wifi,
  WifiOff,
  Users,
  ChevronRight,
  Shield,
  Layers,
  Activity,
  CheckCircle2,
} from 'lucide-react';
import AdminFleetMap, {
  isValidCoordinate,
  isLocationStale,
  STALE_THRESHOLD_MS,
} from '../components/AdminFleetMap';
import { adminService } from '../services/api';
import { createStompClient } from '../services/websocket';

export default function AdminFleetMapPage() {
  const [fleetMap, setFleetMap] = useState({});
  const [agents, setAgents] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedShipmentId, setSelectedShipmentId] = useState(null);
  const [searchFilter, setSearchFilter] = useState('');
  const [freshnessFilter, setFreshnessFilter] = useState('ALL'); // 'ALL' | 'LIVE' | 'STALE'
  const [wsStatus, setWsStatus] = useState('disconnected'); // 'connecting' | 'connected' | 'disconnected' | 'error'
  const [lastSyncTime, setLastSyncTime] = useState(null);
  const [currentTime, setCurrentTime] = useState(Date.now());

  const stompClientRef = useRef(null);
  const fleetSubscriptionRef = useRef(null);
  const isMountedRef = useRef(true);
  const isInitialMountRef = useRef(true);
  const agentsRef = useRef(agents);

  // Keep agentsRef synchronized with latest agents
  useEffect(() => {
    agentsRef.current = agents;
  }, [agents]);

  // Periodically refresh current time to update 5-minute stale badges
  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentTime(Date.now());
    }, 30000); // every 30s
    return () => clearInterval(timer);
  }, []);

  // Fetch initial REST snapshot of fleet and agent directory
  const loadInitialData = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    const fetchStartTime = Date.now();

    try {
      const [fleetRes, agentsRes] = await Promise.allSettled([
        adminService.getFleet(),
        adminService.getAgents(),
      ]);

      if (!isMountedRef.current) return;

      let rawAgents = [];
      if (agentsRes.status === 'fulfilled' && agentsRes.value?.data) {
        rawAgents = Array.isArray(agentsRes.value.data) ? agentsRes.value.data : [];
        setAgents(rawAgents);
        agentsRef.current = rawAgents;
      }

      if (fleetRes.status === 'fulfilled' && fleetRes.value?.data) {
        const rawFleet = Array.isArray(fleetRes.value.data) ? fleetRes.value.data : [];
        const agentMapById = new Map(rawAgents.map((a) => [a.id, a.fullName]));
        const rawFleetShipmentIds = new Set();

        setFleetMap((prev) => {
          const merged = {};

          for (const item of rawFleet) {
            if (!isValidCoordinate(item.latitude, item.longitude)) continue;
            rawFleetShipmentIds.add(item.shipmentId);

            const existing = prev[item.shipmentId];
            const restTimestamp = item.recordedAt ? new Date(item.recordedAt).getTime() : NaN;

            const matchedName = item.agentId ? agentMapById.get(item.agentId) : null;
            const agentName = item.agentName && item.agentName !== 'Unassigned'
              ? item.agentName
              : (matchedName || (item.agentId ? `Agent #${item.agentId}` : 'Unassigned'));

            // Do not overwrite coordinates if an existing WebSocket frame already set newer coordinates
            if (existing) {
              const existingTimestamp = new Date(existing.recordedAt || existing.timestamp).getTime();
              if (!isNaN(existingTimestamp) && !isNaN(restTimestamp) && restTimestamp < existingTimestamp) {
                // Keep newer coordinates and timestamp, but update agent identity & status
                merged[item.shipmentId] = {
                  ...existing,
                  agentId: item.agentId,
                  agentName,
                  status: item.status || existing.status,
                  trackingNumber: item.trackingNumber || existing.trackingNumber,
                };
                continue;
              }
            }

            merged[item.shipmentId] = {
              ...item,
              agentName,
              latitude: Number(item.latitude),
              longitude: Number(item.longitude),
            };
          }

          // Heal missed updates: if an item in prev was NOT returned in rawFleet,
          // only keep it if a live update arrived DURING this fetch (timestamp >= fetchStartTime).
          // Otherwise, it has reached a terminal status (DELIVERED, RETURNED, CANCELLED) or was unassigned,
          // so drop it from the active fleet map.
          for (const [shipmentIdStr, existingItem] of Object.entries(prev)) {
            const sId = Number(shipmentIdStr);
            if (rawFleetShipmentIds.has(sId)) continue;

            const existingTimestamp = new Date(existingItem.recordedAt || existingItem.timestamp).getTime();
            if (!isNaN(existingTimestamp) && existingTimestamp >= fetchStartTime) {
              merged[sId] = existingItem;
            }
          }

          return merged;
        });

        setLastSyncTime(new Date());
      } else if (fleetRes.status === 'rejected') {
        const errorMsg = fleetRes.reason?.message || 'Failed to load fleet telemetry snapshot';
        setError(errorMsg);
      }
    } catch (err) {
      if (isMountedRef.current) {
        setError(err.message || 'Error communicating with administration backend');
      }
    } finally {
      if (isMountedRef.current) {
        setIsLoading(false);
      }
    }
  }, []);

  // Handle incoming STOMP fleet update broadcast
  const handleFleetWsUpdate = useCallback((broadcast) => {
    if (!broadcast || !broadcast.shipmentId) return;

    setFleetMap((prev) => {
      const existing = prev[broadcast.shipmentId];

      const rawUpdateTs = broadcast.timestamp || broadcast.recordedAt;
      const updateTimestamp = rawUpdateTs ? new Date(rawUpdateTs).getTime() : NaN;

      // Enforce out-of-order protection: newer messages always win
      if (existing) {
        const existingRawTs = existing.recordedAt || existing.timestamp;
        const existingTimestamp = existingRawTs ? new Date(existingRawTs).getTime() : NaN;

        if (!isNaN(existingTimestamp)) {
          if (!isNaN(updateTimestamp) && updateTimestamp < existingTimestamp) {
            return prev; // Ignore stale out-of-order broadcast
          }
        }
      }

      // If status is terminal (DELIVERED, RETURNED, CANCELLED), remove from active fleet map
      const statusUpper = broadcast.status ? String(broadcast.status).toUpperCase() : '';
      if (['DELIVERED', 'RETURNED', 'CANCELLED'].includes(statusUpper)) {
        if (!existing) return prev;
        const updated = { ...prev };
        delete updated[broadcast.shipmentId];
        return updated;
      }

      // Reject invalid coordinates
      if (!isValidCoordinate(broadcast.latitude, broadcast.longitude)) {
        return prev;
      }

      // Resolve agent identity cleanly without cross-contaminating on reassignment
      const currentAgentId = broadcast.agentId !== undefined && broadcast.agentId !== null
        ? Number(broadcast.agentId)
        : (existing?.agentId || null);

      const agentInfo = agentsRef.current.find((a) => a.id === currentAgentId);

      let agentName;
      if (broadcast.agentName && broadcast.agentName !== 'Unassigned') {
        agentName = broadcast.agentName;
      } else if (agentInfo?.fullName) {
        agentName = agentInfo.fullName;
      } else if (existing && existing.agentId === currentAgentId && existing.agentName && existing.agentName !== 'Unassigned') {
        agentName = existing.agentName;
      } else if (currentAgentId) {
        agentName = `Agent #${currentAgentId}`;
      } else {
        agentName = 'Unassigned';
      }

      return {
        ...prev,
        [broadcast.shipmentId]: {
          shipmentId: broadcast.shipmentId,
          trackingNumber: broadcast.trackingNumber,
          agentId: currentAgentId,
          agentName,
          status: broadcast.status,
          latitude: Number(broadcast.latitude),
          longitude: Number(broadcast.longitude),
          speed: broadcast.speed,
          bearing: broadcast.bearing,
          recordedAt: rawUpdateTs || new Date().toISOString(),
        },
      };
    });
  }, []);

  // Initial load
  useEffect(() => {
    isMountedRef.current = true;
    loadInitialData();
    return () => {
      isMountedRef.current = false;
    };
  }, [loadInitialData]);

  // Manage STOMP WebSocket connection to /topic/admin/fleet
  useEffect(() => {
    let client = null;

    try {
      client = createStompClient({
        onConnect: () => {
          if (!isMountedRef.current) return;
          setWsStatus('connected');

          // If reconnecting (not initial mount), refresh REST snapshot to heal missed updates
          if (isInitialMountRef.current) {
            isInitialMountRef.current = false;
          } else {
            loadInitialData();
          }

          // Unsubscribe prior subscription if exists to avoid duplicate handlers
          if (fleetSubscriptionRef.current) {
            try {
              fleetSubscriptionRef.current.unsubscribe();
            } catch {
              // ignore
            }
            fleetSubscriptionRef.current = null;
          }

          fleetSubscriptionRef.current = client.subscribe('/topic/admin/fleet', (frame) => {
            if (!isMountedRef.current) return;
            try {
              const payload = JSON.parse(frame.body);
              handleFleetWsUpdate(payload);
            } catch (err) {
              console.warn('Failed to parse STOMP fleet frame:', err);
            }
          });
        },
        onDisconnect: () => {
          if (!isMountedRef.current) return;
          setWsStatus('disconnected');
        },
        onError: () => {
          if (!isMountedRef.current) return;
          setWsStatus('error');
        },
      });

      stompClientRef.current = client;
      setWsStatus('connecting');
      client.activate();
    } catch (err) {
      if (isMountedRef.current) setWsStatus('error');
    }

    return () => {
      if (fleetSubscriptionRef.current) {
        try {
          fleetSubscriptionRef.current.unsubscribe();
        } catch {
          // ignore
        }
        fleetSubscriptionRef.current = null;
      }
      if (client) {
        try {
          client.deactivate();
        } catch {
          // ignore cleanup errors
        }
      }
    };
  }, [handleFleetWsUpdate, loadInitialData]);

  // Convert dictionary to array
  const allFleetVehicles = useMemo(() => {
    return Object.values(fleetMap);
  }, [fleetMap]);

  // Compute metrics
  const metrics = useMemo(() => {
    const totalVehicles = allFleetVehicles.length;
    let freshCount = 0;
    let staleCount = 0;

    for (const v of allFleetVehicles) {
      if (isLocationStale(v.recordedAt || v.timestamp, currentTime)) {
        staleCount++;
      } else {
        freshCount++;
      }
    }

    const totalAgentsCount = agents.length;
    const activeAgentsCount = agents.filter((a) => a.active === true).length;

    // Active agents that currently have no active GPS telemetry
    const agentsWithFleet = new Set(allFleetVehicles.map((v) => v.agentId).filter(Boolean));
    const noLocationAgentsCount = agents.filter((a) => a.active === true && !agentsWithFleet.has(a.id)).length;

    return {
      totalVehicles,
      freshCount,
      staleCount,
      totalAgentsCount,
      activeAgentsCount,
      noLocationAgentsCount,
      idleActiveAgentsCount: noLocationAgentsCount, // backwards compatibility alias
    };
  }, [allFleetVehicles, agents, currentTime]);

  // Filtered vehicles for sidebar and map
  const filteredVehicles = useMemo(() => {
    return allFleetVehicles.filter((v) => {
      // Search filter (tracking number or agent name)
      if (searchFilter) {
        const query = searchFilter.toLowerCase().trim();
        const trk = (v.trackingNumber || '').toLowerCase();
        const agent = (v.agentName || '').toLowerCase();
        if (!trk.includes(query) && !agent.includes(query)) return false;
      }

      // Freshness filter
      const isStale = isLocationStale(v.recordedAt || v.timestamp, currentTime);
      if (freshnessFilter === 'LIVE' && isStale) return false;
      if (freshnessFilter === 'STALE' && !isStale) return false;

      return true;
    });
  }, [allFleetVehicles, searchFilter, freshnessFilter, currentTime]);

  return (
    <div className="min-h-screen bg-navy-950 py-8 px-4 sm:px-6 lg:px-8 text-slate-100">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Top Header & Breadcrumb */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 bg-navy-850 p-6 rounded-2xl border border-navy-700/60 shadow-dark-card">
          <div>
            <div className="flex items-center gap-2 text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
              <Link to="/admin/dashboard" className="hover:text-accent-blue transition-colors">
                Admin Dispatch
              </Link>
              <ChevronRight className="w-3.5 h-3.5" />
              <span className="text-accent-cyan">Live Fleet Map</span>
            </div>
            <h1 className="text-2xl font-bold text-slate-100 tracking-tight flex items-center gap-2.5">
              <Truck className="w-6 h-6 text-accent-blue" />
              Active Fleet Logistics Map
            </h1>
            <p className="text-sm text-slate-400 mt-1">
              Real-time situational telemetry and GPS positions of couriers in transit.
            </p>
          </div>

          <div className="flex items-center gap-3">
            {/* WebSocket Connection Status Badge */}
            <div
              className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-xl border text-xs font-semibold ${
                wsStatus === 'connected'
                  ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
                  : wsStatus === 'connecting'
                  ? 'bg-blue-500/10 text-blue-400 border-blue-500/30 animate-pulse'
                  : 'bg-amber-500/10 text-amber-400 border-amber-500/30'
              }`}
              data-testid="ws-connection-badge"
            >
              {wsStatus === 'connected' ? (
                <>
                  <Wifi className="w-4 h-4 text-emerald-400" />
                  <span>Live Stream Active</span>
                </>
              ) : wsStatus === 'connecting' ? (
                <>
                  <Activity className="w-4 h-4 text-blue-400 animate-spin" />
                  <span>Connecting Stream...</span>
                </>
              ) : (
                <>
                  <WifiOff className="w-4 h-4 text-amber-400" />
                  <span>Stream Offline</span>
                </>
              )}
            </div>

            {/* Manual Refresh Button */}
            <button
              onClick={loadInitialData}
              disabled={isLoading}
              className="inline-flex items-center gap-2 px-3 py-1.5 rounded-xl border border-navy-700 bg-navy-800 hover:bg-navy-700 text-slate-200 text-xs font-semibold transition-colors disabled:opacity-50"
              title="Re-sync initial snapshot"
            >
              <RefreshCw className={`w-3.5 h-3.5 text-slate-400 ${isLoading ? 'animate-spin' : ''}`} />
              <span>Refresh</span>
            </button>
          </div>
        </div>

        {/* Operational Metrics Cards */}
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
          {/* 1. Vehicles with Available Locations */}
          <div className="bg-navy-850 p-4 rounded-xl border border-navy-700/60 shadow-dark-card space-y-1">
            <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wide block">
              Moving Fleet
            </span>
            <div className="flex items-baseline gap-2">
              <span className="text-2xl font-bold text-slate-100" data-testid="metric-total-vehicles">
                {metrics.totalVehicles}
              </span>
              <span className="text-xs text-slate-400">units</span>
            </div>
            <span className="text-[10px] text-slate-500 block">With GPS coordinates</span>
          </div>

          {/* 2. Live / Fresh Coordinates (< 5m) */}
          <div className="bg-navy-850 p-4 rounded-xl border border-emerald-500/30 shadow-dark-card space-y-1 bg-gradient-to-b from-navy-850 to-emerald-950/20">
            <span className="text-[11px] font-semibold text-emerald-400 uppercase tracking-wide block flex items-center gap-1">
              <Radio className="w-3 h-3 text-emerald-400" />
              Live Telemetry
            </span>
            <div className="flex items-baseline gap-2">
              <span className="text-2xl font-bold text-emerald-400" data-testid="metric-fresh-vehicles">
                {metrics.freshCount}
              </span>
              <span className="text-xs text-emerald-500">fresh</span>
            </div>
            <span className="text-[10px] text-emerald-500/80 block">Updated &lt; 5m ago</span>
          </div>

          {/* 3. Stale Coordinates (> 5m) */}
          <div className="bg-navy-850 p-4 rounded-xl border border-amber-500/30 shadow-dark-card space-y-1 bg-gradient-to-b from-navy-850 to-amber-950/20">
            <span className="text-[11px] font-semibold text-amber-400 uppercase tracking-wide block flex items-center gap-1">
              <AlertTriangle className="w-3 h-3 text-amber-400" />
              Stale GPS
            </span>
            <div className="flex items-baseline gap-2">
              <span className="text-2xl font-bold text-amber-400" data-testid="metric-stale-vehicles">
                {metrics.staleCount}
              </span>
              <span className="text-xs text-amber-500">units</span>
            </div>
            <span className="text-[10px] text-amber-500/80 block">No signal &gt; 5m</span>
          </div>

          {/* 4. Total Drivers */}
          <div className="bg-navy-850 p-4 rounded-xl border border-navy-700/60 shadow-dark-card space-y-1">
            <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wide block">
              Drivers Total
            </span>
            <div className="flex items-baseline gap-2">
              <span className="text-2xl font-bold text-slate-100">
                {metrics.totalAgentsCount}
              </span>
              <span className="text-xs text-slate-400">roster</span>
            </div>
            <span className="text-[10px] text-slate-500 block">Registered couriers</span>
          </div>

          {/* 5. Active Driver Accounts */}
          <div className="bg-navy-850 p-4 rounded-xl border border-navy-700/60 shadow-dark-card space-y-1">
            <span className="text-[11px] font-semibold text-accent-blue uppercase tracking-wide block">
              Active Accounts
            </span>
            <div className="flex items-baseline gap-2">
              <span className="text-2xl font-bold text-accent-blue">
                {metrics.activeAgentsCount}
              </span>
              <span className="text-xs text-blue-400">enabled</span>
            </div>
            <span className="text-[10px] text-slate-500 block">System account active</span>
          </div>

          {/* 6. Active Drivers Without Active Shipment Telemetry */}
          <div className="bg-navy-850 p-4 rounded-xl border border-navy-700/60 shadow-dark-card space-y-1">
            <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wide block">
              No Active Location
            </span>
            <div className="flex items-baseline gap-2">
              <span className="text-2xl font-bold text-slate-300" data-testid="metric-no-location-agents">
                {metrics.noLocationAgentsCount}
              </span>
              <span className="text-xs text-slate-400">couriers</span>
            </div>
            <span className="text-[10px] text-slate-500 block">No active shipment location available</span>
          </div>
        </div>

        {/* Error Banner */}
        {error && (
          <div
            className="p-4 rounded-xl bg-red-950/40 border border-red-500/40 text-red-300 flex items-center justify-between gap-3 text-xs"
            data-testid="fleet-error-banner"
          >
            <div className="flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 text-red-400 flex-shrink-0" />
              <span>{error}</span>
            </div>
            <button
              onClick={loadInitialData}
              className="px-3 py-1 rounded-lg bg-red-600 hover:bg-red-500 text-white font-semibold transition-colors"
            >
              Retry
            </button>
          </div>
        )}

        {/* Main Grid: Interactive Map (Left/Center) + Vehicle Dispatch Drawer (Right) */}
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6 items-start">
          {/* Map Column (3 cols on large screens) */}
          <div className="lg:col-span-3 space-y-4">
            {isLoading && allFleetVehicles.length === 0 ? (
              <div
                className="h-[580px] rounded-2xl border border-navy-700/60 bg-navy-850 flex flex-col items-center justify-center p-8 space-y-3"
                data-testid="fleet-loading-skeleton"
              >
                <RefreshCw className="w-8 h-8 text-accent-blue animate-spin" />
                <p className="text-sm font-semibold text-slate-200">Loading Fleet Telemetry...</p>
                <p className="text-xs text-slate-400">
                  Retrieving GPS coordinates and establishing live STOMP pipeline.
                </p>
              </div>
            ) : allFleetVehicles.length === 0 ? (
              <div
                className="h-[580px] rounded-2xl border border-dashed border-navy-700 bg-navy-850/60 flex flex-col items-center justify-center p-8 text-center space-y-3"
                data-testid="fleet-empty-state"
              >
                <div className="w-14 h-14 rounded-2xl bg-blue-500/10 text-accent-blue flex items-center justify-center mx-auto border border-blue-500/20">
                  <Truck className="w-7 h-7" />
                </div>
                <h3 className="text-base font-bold text-slate-100">No Vehicles Currently Transmitting</h3>
                <p className="text-xs text-slate-400 max-w-sm">
                  There are currently no active deliveries transmitting live GPS coordinates. Vehicles will appear automatically once drivers enter delivery mode.
                </p>
              </div>
            ) : (
              <AdminFleetMap
                vehicles={filteredVehicles}
                selectedShipmentId={selectedShipmentId}
                onSelectVehicle={(v) => setSelectedShipmentId(v.shipmentId)}
                now={currentTime}
                height="580px"
              />
            )}
          </div>

          {/* Vehicle Directory Drawer Column (1 col on large screens) */}
          <div className="lg:col-span-1 bg-navy-850 rounded-2xl border border-navy-700/60 shadow-dark-card p-4 space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-navy-700/60">
              <h3 className="font-bold text-slate-100 text-sm flex items-center gap-2">
                <Layers className="w-4 h-4 text-accent-blue" />
                Active Units ({filteredVehicles.length})
              </h3>
            </div>

            {/* Search Input */}
            <div className="relative">
              <Search className="w-4 h-4 text-slate-500 absolute left-3 top-2.5" />
              <input
                type="text"
                placeholder="Search waybill or courier..."
                value={searchFilter}
                onChange={(e) => setSearchFilter(e.target.value)}
                className="w-full pl-9 pr-3 py-1.5 rounded-xl border border-navy-700 bg-navy-900 text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-accent-blue/30 focus:border-accent-blue"
              />
            </div>

            {/* Filter Pills */}
            <div className="flex items-center gap-1.5 text-[11px] font-semibold">
              <button
                onClick={() => setFreshnessFilter('ALL')}
                className={`px-2.5 py-1 rounded-lg border transition-colors ${
                  freshnessFilter === 'ALL'
                    ? 'bg-accent-blue text-white border-accent-blue'
                    : 'bg-navy-800 text-slate-300 border-navy-700 hover:bg-navy-700'
                }`}
              >
                All ({allFleetVehicles.length})
              </button>
              <button
                onClick={() => setFreshnessFilter('LIVE')}
                className={`px-2.5 py-1 rounded-lg border transition-colors ${
                  freshnessFilter === 'LIVE'
                    ? 'bg-emerald-600 text-white border-emerald-600'
                    : 'bg-navy-800 text-slate-300 border-navy-700 hover:bg-navy-700'
                }`}
              >
                Live ({metrics.freshCount})
              </button>
              <button
                onClick={() => setFreshnessFilter('STALE')}
                className={`px-2.5 py-1 rounded-lg border transition-colors ${
                  freshnessFilter === 'STALE'
                    ? 'bg-amber-600 text-white border-amber-600'
                    : 'bg-navy-800 text-slate-300 border-navy-700 hover:bg-navy-700'
                }`}
              >
                Stale ({metrics.staleCount})
              </button>
            </div>

            {/* Vehicle List */}
            <div className="space-y-2 max-h-[400px] overflow-y-auto pr-1">
              {filteredVehicles.length === 0 ? (
                <div className="py-6 text-center text-xs text-slate-500">
                  No vehicles match the selected filter.
                </div>
              ) : (
                filteredVehicles.map((vehicle) => {
                  const isStale = isLocationStale(vehicle.recordedAt || vehicle.timestamp, currentTime);
                  const isSelected = selectedShipmentId === vehicle.shipmentId;

                  return (
                    <div
                      key={vehicle.shipmentId}
                      onClick={() => setSelectedShipmentId(vehicle.shipmentId)}
                      className={`p-3 rounded-xl border text-xs cursor-pointer transition-all ${
                        isSelected
                          ? 'border-accent-blue bg-blue-950/30 shadow-sm ring-1 ring-accent-blue'
                          : 'border-navy-700/50 bg-navy-900/60 hover:bg-navy-800/80'
                      }`}
                    >
                      <div className="flex items-center justify-between font-semibold text-slate-200 mb-1">
                        <span className="truncate max-w-[140px]">{vehicle.agentName}</span>
                        {isStale ? (
                          <span className="text-[10px] px-1.5 py-0.5 rounded font-bold bg-amber-500/20 text-amber-400 border border-amber-500/30">
                            Stale
                          </span>
                        ) : (
                          <span className="text-[10px] px-1.5 py-0.5 rounded font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                            Live
                          </span>
                        )}
                      </div>

                      <div className="text-[11px] font-mono text-accent-cyan mb-1">
                        {vehicle.trackingNumber}
                      </div>

                      <div className="flex items-center justify-between text-[10px] text-slate-400">
                        <span>{vehicle.status}</span>
                        <span>
                          {vehicle.recordedAt
                            ? new Date(vehicle.recordedAt).toLocaleTimeString([], {
                                hour: '2-digit',
                                minute: '2-digit',
                              })
                            : 'N/A'}
                        </span>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
