import React, { useEffect, useMemo, useRef, useState } from 'react';
import { MapContainer, TileLayer, Marker, Popup, Polyline, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { Truck, Navigation, AlertTriangle, Radio, RefreshCw, CheckCircle2 } from 'lucide-react';

/**
 * Haversine distance in metres between two GPS coordinates.
 * Pure function with no side effects.
 *
 * @param {number} lat1
 * @param {number} lon1
 * @param {number} lat2
 * @param {number} lon2
 * @returns {number} Distance in metres
 */
function haversineMetres(lat1, lon1, lat2, lon2) {
  const R = 6371000;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLon / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/**
 * Fetches a driving route from the public OSRM demo API.
 * Returns an array of [lat, lon] pairs for the route geometry, or null on failure.
 *
 * @param {number} vLat - Vehicle latitude
 * @param {number} vLon - Vehicle longitude
 * @param {number} dLat - Destination latitude
 * @param {number} dLon - Destination longitude
 * @param {AbortSignal} signal - AbortController signal
 * @returns {Promise<Array<[number, number]>|null>}
 */
async function fetchOsrmRoute(vLat, vLon, dLat, dLon, signal) {
  const url = `https://router.project-osrm.org/route/v1/driving/${vLon},${vLat};${dLon},${dLat}?overview=full&geometries=geojson`;
  try {
    const options = {};
    if (signal && typeof AbortSignal !== 'undefined' && signal instanceof AbortSignal) {
      options.signal = signal;
    }
    const response = await fetch(url, options);
    const data = await response.json();
    if (data?.routes?.[0]?.geometry?.coordinates) {
      // OSRM returns [lon, lat]; Leaflet wants [lat, lon]
      return data.routes[0].geometry.coordinates.map(([lon, lat]) => [lat, lon]);
    }
    return null;
  } catch (err) {
    if (err.name !== 'AbortError') {
      console.warn('OSRM route fetch failed — falling back to no route line:', err.message);
    }
    return null;
  }
}

/**
 * Custom Leaflet divIcon for the live delivery vehicle.
 */
function createVehicleIcon(bearing = null) {
  const rotation = typeof bearing === 'number' ? `transform: rotate(${bearing}deg);` : '';
  return L.divIcon({
    className: 'leaflet-vehicle-marker-wrapper',
    html: `
      <div class="relative flex items-center justify-center w-10 h-10" style="pointer-events: auto;">
        <div class="absolute w-10 h-10 bg-blue-500/30 rounded-full animate-ping"></div>
        <div class="relative w-8 h-8 bg-blue-600 rounded-full shadow-md border-2 border-white flex items-center justify-center text-white" style="${rotation}">
          <svg class="w-4.5 h-4.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M8 17a2 2 0 100-4 2 2 0 000 4zm10 0a2 2 0 100-4 2 2 0 000 4z" />
            <path stroke-linecap="round" stroke-linejoin="round" d="M13 16V6a1 1 0 00-1-1H4a1 1 0 00-1 1v10a1 1 0 001 1h1m8-1a1 1 0 01-1 1H9m4-1V8a1 1 0 011-1h2.586a1 1 0 01.707.293l3.414 3.414a1 1 0 01.293.707V16a1 1 0 01-1 1h-1m-6-1a1 1 0 001 1h1M5 17a2 2 0 104 0m-4 0a2 2 0 114 0m6 0a2 2 0 104 0m-4 0a2 2 0 114 0" />
          </svg>
        </div>
      </div>
    `,
    iconSize: [40, 40],
    iconAnchor: [20, 20],
    popupAnchor: [0, -20],
  });
}

/**
 * Custom Leaflet divIcon for the destination if coordinates are safely provided.
 */
function createDestinationIcon() {
  return L.divIcon({
    className: 'leaflet-destination-marker-wrapper',
    html: `
      <div class="relative flex items-center justify-center w-8 h-8" style="pointer-events: auto;">
        <div class="relative w-7 h-7 bg-emerald-600 rounded-full shadow-md border-2 border-white flex items-center justify-center text-white">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
            <path stroke-linecap="round" stroke-linejoin="round" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
          </svg>
        </div>
      </div>
    `,
    iconSize: [32, 32],
    iconAnchor: [16, 32],
    popupAnchor: [0, -32],
  });
}

/**
 * Controller component inside MapContainer to keep view centered smoothly without full remounting.
 */
function MapViewController({ vehiclePos, destPos }) {
  const map = useMap();

  useEffect(() => {
    if (vehiclePos && destPos) {
      const bounds = L.latLngBounds([vehiclePos, destPos]);
      map.fitBounds(bounds, { padding: [40, 40], maxZoom: 15 });
    } else if (vehiclePos) {
      map.panTo(vehiclePos, { animate: true, duration: 0.8 });
    }
  }, [map, vehiclePos, destPos]);

  return null;
}

/**
 * Reusable Live Shipment Tracking Map Component.
 *
 * @param {Object} props
 * @param {string} props.trackingNumber
 * @param {Object} props.location - Current live location object { latitude, longitude, recordedAt, status, fresh, speed, bearing }
 * @param {Object} [props.destination] - Optional destination coordinates { latitude, longitude, label }
 * @param {'live'|'polling'|'reconnecting'|'offline'} [props.connectionStatus='offline']
 * @param {Date|string} [props.lastUpdatedAt]
 * @param {string} [props.className='']
 * @param {string} [props.height='460px']
 */
export default function LiveTrackingMap({
  trackingNumber,
  location,
  destination,
  connectionStatus = 'offline',
  lastUpdatedAt = null,
  className = '',
  height = '460px',
}) {
  const hasVehicleCoords = useMemo(() => {
    return (
      location &&
      location.latitude !== null &&
      location.latitude !== undefined &&
      location.longitude !== null &&
      location.longitude !== undefined &&
      !isNaN(location.latitude) &&
      !isNaN(location.longitude)
    );
  }, [location]);

  const hasDestCoords = useMemo(() => {
    return (
      destination &&
      destination.latitude !== null &&
      destination.latitude !== undefined &&
      destination.longitude !== null &&
      destination.longitude !== undefined &&
      !isNaN(destination.latitude) &&
      !isNaN(destination.longitude)
    );
  }, [destination]);

  const vehiclePosition = hasVehicleCoords ? [location.latitude, location.longitude] : null;
  const destinationPosition = hasDestCoords ? [destination.latitude, destination.longitude] : null;
  const initialCenter = vehiclePosition || destinationPosition || [20.5937, 78.9629]; // Default fallback center
  const initialZoom = hasVehicleCoords ? 14 : 5;

  const vehicleIcon = useMemo(() => {
    return createVehicleIcon(location?.bearing);
  }, [location?.bearing]);

  const destIcon = useMemo(() => {
    return createDestinationIcon();
  }, []);

  // --- Smooth animation state ---
  const [animatedPos, setAnimatedPos] = useState(null);
  const prevPosRef = useRef(null);
  const animFrameRef = useRef(null);
  const animStartRef = useRef(null);
  const animFromRef = useRef(null);
  const ANIM_DURATION_MS = 800;

  useEffect(() => {
    if (!hasVehicleCoords) {
      setAnimatedPos(null);
      prevPosRef.current = null;
      return;
    }
    const newPos = [location.latitude, location.longitude];
    const prev = prevPosRef.current;

    // Cancel any in-progress animation
    if (animFrameRef.current) {
      cancelAnimationFrame(animFrameRef.current);
      animFrameRef.current = null;
    }

    if (!prev || location?.fresh === false) {
      // No previous position or stale: jump immediately
      setAnimatedPos(newPos);
      prevPosRef.current = newPos;
      return;
    }

    // Animate from prev to newPos
    animFromRef.current = prev;
    animStartRef.current = performance.now();

    function step(now) {
      const elapsed = now - animStartRef.current;
      const t = Math.min(elapsed / ANIM_DURATION_MS, 1);
      // ease-out cubic
      const eased = 1 - Math.pow(1 - t, 3);
      const lat = animFromRef.current[0] + (newPos[0] - animFromRef.current[0]) * eased;
      const lng = animFromRef.current[1] + (newPos[1] - animFromRef.current[1]) * eased;
      setAnimatedPos([lat, lng]);
      if (t < 1) {
        animFrameRef.current = requestAnimationFrame(step);
      } else {
        animFrameRef.current = null;
        prevPosRef.current = newPos;
      }
    }

    animFrameRef.current = requestAnimationFrame(step);

    return () => {
      if (animFrameRef.current) {
        cancelAnimationFrame(animFrameRef.current);
        animFrameRef.current = null;
      }
    };
  }, [location?.latitude, location?.longitude, location?.fresh, hasVehicleCoords]);

  // --- OSRM route ---
  const [routeCoords, setRouteCoords] = useState(null);
  const lastRouteFetchPosRef = useRef(null);
  const routeFetchAbortRef = useRef(null);

  useEffect(() => {
    if (!hasVehicleCoords || !hasDestCoords) {
      setRouteCoords(null);
      lastRouteFetchPosRef.current = null;
      return;
    }

    const vLat = location.latitude;
    const vLon = location.longitude;
    const dLat = destination.latitude;
    const dLon = destination.longitude;

    // Only refetch if moved >50m from last fetch point or destination changed
    const lastPos = lastRouteFetchPosRef.current;
    if (lastPos) {
      const dist = haversineMetres(lastPos[0], lastPos[1], vLat, vLon);
      if (dist < 50) return;
    }

    // Abort previous pending request
    if (routeFetchAbortRef.current) {
      routeFetchAbortRef.current.abort();
    }
    const controller = new AbortController();
    routeFetchAbortRef.current = controller;

    lastRouteFetchPosRef.current = [vLat, vLon];

    fetchOsrmRoute(vLat, vLon, dLat, dLon, controller.signal).then((coords) => {
      if (!controller.signal.aborted) {
        setRouteCoords(coords);
      }
    });

    return () => {
      controller.abort();
    };
  }, [hasVehicleCoords, hasDestCoords, location?.latitude, location?.longitude, destination?.latitude, destination?.longitude]);

  const formatTimestamp = (dateInput) => {
    if (!dateInput) return 'Awaiting signal';
    const date = new Date(dateInput);
    if (isNaN(date.getTime())) return 'Awaiting signal';
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  };

  // Use animated position for the vehicle marker; fall back to vehiclePosition only if animation hasn't started
  const displayVehiclePos = animatedPos ?? vehiclePosition;

  return (
    <div
      data-testid="live-tracking-map-container"
      className={`relative rounded-2xl overflow-hidden border border-slate-200 bg-slate-100 shadow-sm ${className}`}
      style={{ height }}
    >
      {/* Top Floating Telemetry & Connection Status Bar */}
      <div className="absolute top-3 left-3 right-3 z-[1000] flex flex-wrap items-center justify-between gap-2 pointer-events-none">
        {/* Connection Mode Pill */}
        <div className="pointer-events-auto flex items-center gap-2 bg-white/95 backdrop-blur-sm px-3 py-1.5 rounded-full border border-slate-200 shadow-sm text-xs font-semibold">
          {connectionStatus === 'live' && (
            <>
              <span className="relative flex h-2.5 w-2.5">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-500"></span>
              </span>
              <span className="text-emerald-700">Live Telemetry (WebSocket)</span>
            </>
          )}

          {connectionStatus === 'polling' && (
            <>
              <RefreshCw className="w-3.5 h-3.5 text-blue-600 animate-spin" />
              <span className="text-blue-700">Live Polling Active (Fallback)</span>
            </>
          )}

          {connectionStatus === 'reconnecting' && (
            <>
              <Radio className="w-3.5 h-3.5 text-amber-600 animate-pulse" />
              <span className="text-amber-700">Reconnecting...</span>
            </>
          )}

          {connectionStatus === 'offline' && (
            <>
              <span className="h-2 w-2 rounded-full bg-slate-400"></span>
              <span className="text-slate-600">Telemetry Inactive</span>
            </>
          )}
        </div>

        {/* Freshness / Last Update Pill */}
        <div className="pointer-events-auto bg-white/95 backdrop-blur-sm px-3 py-1.5 rounded-full border border-slate-200 shadow-sm text-xs text-slate-600 font-medium flex items-center gap-2">
          {location?.fresh === false ? (
            <span className="text-amber-600 inline-flex items-center gap-1 font-semibold">
              <AlertTriangle className="w-3.5 h-3.5" /> Stale Signal
            </span>
          ) : (
            <span className="inline-flex items-center gap-1">
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" />
              Updated: {formatTimestamp(lastUpdatedAt || location?.recordedAt)}
            </span>
          )}
        </div>
      </div>

      {/* Map Rendering or Missing GPS State */}
      {hasVehicleCoords ? (
        <MapContainer
          center={initialCenter}
          zoom={initialZoom}
          scrollWheelZoom={false}
          className="w-full h-full z-0"
        >
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a> contributors'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            maxZoom={19}
          />

          <MapViewController vehiclePos={displayVehiclePos} destPos={destinationPosition} />

          {/* Route Polyline (rendered when OSRM route is available) */}
          {routeCoords && routeCoords.length > 1 && (
            <Polyline
              data-testid="route-polyline"
              positions={routeCoords}
              pathOptions={{ color: '#4f46e5', weight: 4, opacity: 0.7, dashArray: null }}
            />
          )}

          {/* Delivery Vehicle Marker */}
          <Marker position={displayVehiclePos} icon={vehicleIcon}>
            <Popup className="custom-popup">
              <div className="p-1 space-y-1 text-xs">
                <div className="font-bold text-slate-800 flex items-center gap-1.5">
                  <Truck className="w-3.5 h-3.5 text-blue-600" />
                  Courier Vehicle
                </div>
                <div className="text-slate-600 font-mono text-[11px]">
                  {location.latitude.toFixed(5)}, {location.longitude.toFixed(5)}
                </div>
                {location.speed !== null && location.speed !== undefined && (
                  <div className="text-slate-500 text-[11px]">
                    Speed: {location.speed} km/h
                  </div>
                )}
                {location.status && (
                  <div className="text-blue-600 font-semibold text-[11px]">
                    Status: {location.status}
                  </div>
                )}
              </div>
            </Popup>
          </Marker>

          {/* Destination Marker (Rendered only if safely provided by API) */}
          {destinationPosition && (
            <Marker position={destinationPosition} icon={destIcon}>
              <Popup>
                <div className="p-1 text-xs">
                  <span className="font-bold text-slate-800 block">
                    {destination.label || 'Delivery Destination'}
                  </span>
                </div>
              </Popup>
            </Marker>
          )}
        </MapContainer>
      ) : (
        /* Informational Card When GPS Coordinates are Not Yet Available or Inactive */
        <div
          data-testid="no-location-notice"
          className="w-full h-full flex flex-col items-center justify-center p-6 text-center bg-slate-50 border border-slate-200"
        >
          <div className="w-14 h-14 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center mb-3 border border-blue-100 shadow-sm">
            <Navigation className="w-7 h-7 text-blue-500" />
          </div>
          <h3 className="text-base font-bold text-slate-800 mb-1">
            Live Vehicle Location Not Available
          </h3>
          <p className="text-xs text-slate-500 max-w-sm mb-4 leading-relaxed">
            {location?.status === 'OUT_FOR_DELIVERY'
              ? 'Consignment is out for delivery. Waiting for the delivery agent GPS telemetry pulse.'
              : 'Live vehicle telemetry is activated strictly when the consignment status is OUT FOR DELIVERY.'}
          </p>
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-slate-200/70 text-slate-700 text-[11px] font-medium">
            <Radio className="w-3 h-3 text-slate-500 animate-pulse" />
            Consignment Status: <span className="font-semibold">{location?.status || 'PENDING'}</span>
          </div>
        </div>
      )}

      {/* Floating Stale Location Banner */}
      {hasVehicleCoords && location?.fresh === false && (
        <div
          data-testid="stale-location-alert"
          className="absolute bottom-3 left-3 right-3 z-[1000] bg-amber-500/95 text-white backdrop-blur-sm px-4 py-2 rounded-xl shadow-md text-xs font-medium flex items-center justify-between"
        >
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 shrink-0 text-white" />
            <span>
              GPS coordinates are older than the freshness threshold. Showing last reported position.
            </span>
          </div>
          <span className="text-[11px] opacity-90 hidden sm:inline">
            {formatTimestamp(location.recordedAt)}
          </span>
        </div>
      )}
    </div>
  );
}
