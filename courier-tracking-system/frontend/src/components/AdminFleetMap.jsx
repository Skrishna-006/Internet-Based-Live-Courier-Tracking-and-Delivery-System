import React, { useEffect, useMemo } from 'react';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import {
  Truck,
  AlertTriangle,
  Radio,
  Clock,
  Compass,
  Gauge,
  User,
  Package,
} from 'lucide-react';

export const STALE_THRESHOLD_MS = 5 * 60 * 1000; // 5 minutes

/**
 * Validates coordinate pair against strict numerical and geographical bounds.
 */
export function isValidCoordinate(lat, lon) {
  if (lat === null || lat === undefined || lon === null || lon === undefined) return false;
  if (typeof lat === 'boolean' || typeof lon === 'boolean') return false;
  if (typeof lat === 'string' && lat.trim() === '') return false;
  if (typeof lon === 'string' && lon.trim() === '') return false;
  const numLat = Number(lat);
  const numLon = Number(lon);
  if (isNaN(numLat) || isNaN(numLon)) return false;
  if (numLat < -90 || numLat > 90) return false;
  if (numLon < -180 || numLon > 180) return false;
  return true;
}

/**
 * Determines whether a location timestamp is older than the 5-minute threshold.
 */
export function isLocationStale(timestamp, now = Date.now()) {
  if (!timestamp) return true;
  const recordedTime = new Date(timestamp).getTime();
  if (isNaN(recordedTime)) return true;
  return now - recordedTime > STALE_THRESHOLD_MS;
}

/**
 * Custom Leaflet divIcon representing a fleet vehicle with dynamic freshness styling.
 */
function createFleetVehicleIcon(isStale, bearing = null, isSelected = false) {
  const rotation = typeof bearing === 'number' && !isNaN(bearing) ? `transform: rotate(${bearing}deg);` : '';
  const bgColor = isStale ? 'bg-amber-500' : 'bg-blue-600';
  const pingEffect = !isStale ? '<div class="absolute w-10 h-10 bg-blue-500/30 rounded-full animate-ping"></div>' : '';
  const selectedRing = isSelected ? 'ring-4 ring-indigo-400 ring-offset-2' : '';

  return L.divIcon({
    className: 'leaflet-fleet-marker-wrapper',
    html: `
      <div class="relative flex items-center justify-center w-10 h-10" style="pointer-events: auto;">
        ${pingEffect}
        <div class="relative w-8 h-8 ${bgColor} ${selectedRing} rounded-full shadow-lg border-2 border-white flex items-center justify-center text-white" style="${rotation}">
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
 * Controller inside MapContainer to dynamically adjust viewport when vehicles or selection change.
 */
function FleetMapController({ validVehicles, selectedVehicle }) {
  const map = useMap();

  useEffect(() => {
    if (selectedVehicle && isValidCoordinate(selectedVehicle.latitude, selectedVehicle.longitude)) {
      map.flyTo([Number(selectedVehicle.latitude), Number(selectedVehicle.longitude)], 15, {
        duration: 1.2,
      });
    } else if (validVehicles && validVehicles.length > 0) {
      if (validVehicles.length === 1) {
        map.setView([Number(validVehicles[0].latitude), Number(validVehicles[0].longitude)], 13);
      } else {
        const bounds = L.latLngBounds(
          validVehicles.map((v) => [Number(v.latitude), Number(v.longitude)])
        );
        map.fitBounds(bounds, { padding: [50, 50], maxZoom: 14 });
      }
    }
  }, [map, selectedVehicle, validVehicles]);

  return null;
}

/**
 * Interactive Leaflet multi-vehicle fleet map component.
 */
export default function AdminFleetMap({
  vehicles = [],
  selectedShipmentId = null,
  onSelectVehicle = null,
  now = Date.now(),
  height = '580px',
  className = '',
}) {
  const validVehicles = useMemo(() => {
    return vehicles.filter((v) => isValidCoordinate(v.latitude, v.longitude));
  }, [vehicles]);

  const selectedVehicle = useMemo(() => {
    if (!selectedShipmentId) return null;
    return validVehicles.find((v) => v.shipmentId === selectedShipmentId) || null;
  }, [validVehicles, selectedShipmentId]);

  // Default central fallback if no coordinates available
  const defaultCenter = [30.2672, -97.7431];

  const formatTimestamp = (ts) => {
    if (!ts) return 'Unknown';
    try {
      const date = new Date(ts);
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    } catch {
      return String(ts);
    }
  };

  const getMinutesAgo = (ts) => {
    if (!ts) return null;
    const diffMs = now - new Date(ts).getTime();
    if (isNaN(diffMs) || diffMs < 0) return 0;
    return Math.floor(diffMs / 60000);
  };

  return (
    <div
      className={`relative rounded-2xl overflow-hidden border border-navy-700/60 shadow-dark-card bg-navy-950 dark-map-container ${className}`}
      style={{ height }}
      data-testid="admin-fleet-map-container"
    >
      <MapContainer
        center={
          validVehicles.length > 0
            ? [Number(validVehicles[0].latitude), Number(validVehicles[0].longitude)]
            : defaultCenter
        }
        zoom={12}
        scrollWheelZoom={true}
        className="w-full h-full z-0"
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />

        <FleetMapController validVehicles={validVehicles} selectedVehicle={selectedVehicle} />

        {validVehicles.map((vehicle) => {
          const isStale = isLocationStale(vehicle.recordedAt || vehicle.timestamp, now);
          const isSelected = selectedShipmentId === vehicle.shipmentId;
          const icon = createFleetVehicleIcon(isStale, vehicle.bearing, isSelected);
          const minutesAgo = getMinutesAgo(vehicle.recordedAt || vehicle.timestamp);

          return (
            <Marker
              key={vehicle.shipmentId}
              position={[Number(vehicle.latitude), Number(vehicle.longitude)]}
              icon={icon}
              eventHandlers={{
                click: () => {
                  if (onSelectVehicle) onSelectVehicle(vehicle);
                },
              }}
            >
              <Popup className="custom-fleet-popup" minWidth={260} maxWidth={320}>
                <div className="p-1 space-y-2.5 text-xs text-slate-200 font-sans">
                  {/* Header / Freshness Indicator */}
                  <div className="flex items-center justify-between pb-1.5 border-b border-navy-700/60">
                    <span className="font-bold text-slate-100 flex items-center gap-1.5 text-sm">
                      <Truck className="w-4 h-4 text-accent-blue" />
                      {vehicle.agentName || `Agent #${vehicle.agentId || 'N/A'}`}
                    </span>
                    {isStale ? (
                      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-amber-500/20 text-amber-300 border border-amber-500/40">
                        <AlertTriangle className="w-3 h-3 text-amber-400" />
                        Stale ({minutesAgo !== null ? `${minutesAgo}m ago` : 'telemetry'})
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-500/20 text-emerald-300 border border-emerald-500/40">
                        <Radio className="w-3 h-3 text-emerald-400 animate-pulse" />
                        Live GPS
                      </span>
                    )}
                  </div>

                  {/* Consignment Details */}
                  <div className="space-y-1.5 bg-navy-900/80 p-2 rounded-lg border border-navy-700/50">
                    <div className="flex items-center justify-between">
                      <span className="text-slate-400 font-medium flex items-center gap-1">
                        <Package className="w-3.5 h-3.5 text-slate-500" />
                        Waybill:
                      </span>
                      <span className="font-mono font-bold text-accent-cyan">
                        {vehicle.trackingNumber}
                      </span>
                    </div>

                    <div className="flex items-center justify-between">
                      <span className="text-slate-400 font-medium">Status:</span>
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-blue-500/20 text-accent-blue uppercase">
                        {vehicle.status || 'IN_TRANSIT'}
                      </span>
                    </div>

                    {vehicle.agentId && (
                      <div className="flex items-center justify-between">
                        <span className="text-slate-400 font-medium flex items-center gap-1">
                          <User className="w-3.5 h-3.5 text-slate-500" />
                          Driver ID:
                        </span>
                        <span className="font-medium text-slate-300">#{vehicle.agentId}</span>
                      </div>
                    )}
                  </div>

                  {/* Telemetry Coordinates & Speed */}
                  <div className="space-y-1 text-[11px] text-slate-300">
                    <div className="flex justify-between">
                      <span className="text-slate-400">Coordinates:</span>
                      <span className="font-mono font-medium text-slate-200">
                        {Number(vehicle.latitude).toFixed(5)}, {Number(vehicle.longitude).toFixed(5)}
                      </span>
                    </div>

                    {vehicle.speed !== null && vehicle.speed !== undefined && (
                      <div className="flex justify-between">
                        <span className="text-slate-400 flex items-center gap-1">
                          <Gauge className="w-3 h-3 text-slate-500" />
                          Velocity:
                        </span>
                        <span className="font-medium text-slate-200">
                          {Number(vehicle.speed).toFixed(1)} km/h
                        </span>
                      </div>
                    )}

                    {vehicle.bearing !== null && vehicle.bearing !== undefined && (
                      <div className="flex justify-between">
                        <span className="text-slate-400 flex items-center gap-1">
                          <Compass className="w-3 h-3 text-slate-500" />
                          Heading:
                        </span>
                        <span className="font-medium text-slate-200">
                          {Number(vehicle.bearing).toFixed(0)}°
                        </span>
                      </div>
                    )}

                    <div className="flex justify-between pt-1 border-t border-navy-700/50">
                      <span className="text-slate-400 flex items-center gap-1">
                        <Clock className="w-3 h-3 text-slate-500" />
                        Telemetry Time:
                      </span>
                      <span className="font-medium text-slate-300">
                        {formatTimestamp(vehicle.recordedAt || vehicle.timestamp)}
                      </span>
                    </div>
                  </div>
                </div>
              </Popup>
            </Marker>
          );
        })}
      </MapContainer>

      {/* Floating Map Watermark / Active Badge */}
      <div className="absolute top-3 right-3 z-[1000] pointer-events-none">
        <div className="bg-navy-900/90 backdrop-blur-md px-3 py-1.5 rounded-xl border border-navy-700 shadow-dark-card flex items-center gap-2 text-xs font-semibold text-slate-200">
          <Truck className="w-4 h-4 text-accent-blue" />
          <span>
            {validVehicles.length} {validVehicles.length === 1 ? 'Vehicle' : 'Vehicles'} Active
          </span>
        </div>
      </div>
    </div>
  );
}
