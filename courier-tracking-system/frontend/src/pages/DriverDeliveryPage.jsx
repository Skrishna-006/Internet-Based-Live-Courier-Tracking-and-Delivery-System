import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import {
  Navigation,
  Radio,
  CheckCircle2,
  AlertCircle,
  MapPin,
  User,
  Phone,
  ShieldCheck,
  Clock,
  ArrowLeft,
  RefreshCw,
  Play,
  Square,
  KeyRound,
  Check,
  Truck,
  Info,
  ExternalLink,
  FileText,
  AlertTriangle,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { agentService, shipmentService } from '../services/api';
import LiveTrackingMap from '../components/LiveTrackingMap';

export default function DriverDeliveryPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();

  // Shipment Detail State
  const [shipment, setShipment] = useState(null);
  const [loadingShipment, setLoadingShipment] = useState(true);
  const [shipmentError, setShipmentError] = useState(null);
  const [isDelivered, setIsDelivered] = useState(false);
  const [deliveryProof, setDeliveryProof] = useState(null);

  // Status progression action state
  const [progressingStatus, setProgressingStatus] = useState(false);

  // GPS Telemetry State
  const [isTracking, setIsTracking] = useState(false);
  const [gpsPermission, setGpsPermission] = useState('prompt'); // 'prompt' | 'granted' | 'denied' | 'unsupported'
  const [currentCoords, setCurrentCoords] = useState(null);
  const [lastSentLocation, setLastSentLocation] = useState(null);
  const [transmissionStatus, setTransmissionStatus] = useState('idle'); // 'idle' | 'transmitting' | 'synced' | 'throttled' | 'error'
  const [gpsError, setGpsError] = useState(null);

  const watchIdRef = useRef(null);
  const lastSentTimeRef = useRef(0);
  const isTrackingRef = useRef(false);

  // Keep ref in sync with state for callbacks
  useEffect(() => {
    isTrackingRef.current = isTracking;
  }, [isTracking]);

  // OTP Verification State
  const [otpRequested, setOtpRequested] = useState(false);
  const [devOtp, setDevOtp] = useState(null);
  const [otpExpiresAt, setOtpExpiresAt] = useState(null);
  const [otpCountdown, setOtpCountdown] = useState(0);
  const [otpInput, setOtpInput] = useState('');
  const [recipientName, setRecipientName] = useState('');
  const [verificationNotes, setVerificationNotes] = useState('');
  const [locationName, setLocationName] = useState('');
  const [requestingOtp, setRequestingOtp] = useState(false);
  const [requestOtpError, setRequestOtpError] = useState(null);
  const [submittingOtp, setSubmittingOtp] = useState(false);
  const [otpError, setOtpError] = useState(null);

  // Fetch Shipment Details
  const fetchShipment = useCallback(async () => {
    setLoadingShipment(true);
    setShipmentError(null);

    try {
      const res = await agentService.getAssignmentById(id);
      if (res.success && res.data) {
        setShipment(res.data);
        setRecipientName(res.data.recipientName || '');
        if (res.data.status === 'DELIVERED') {
          setIsDelivered(true);
        }
      } else {
        setShipmentError(res.message || 'Failed to retrieve shipment details');
      }
    } catch (err) {
      setShipmentError(err.message || 'Error communicating with assignment service');
    } finally {
      setLoadingShipment(false);
    }
  }, [id]);

  useEffect(() => {
    fetchShipment();
  }, [fetchShipment]);

  // Handle GPS cleanup on unmount
  useEffect(() => {
    return () => {
      if (watchIdRef.current !== null) {
        navigator.geolocation.clearWatch(watchIdRef.current);
        watchIdRef.current = null;
      }
    };
  }, []);

  // Stop tracking when shipment reaches terminal status
  useEffect(() => {
    if (shipment && (shipment.status === 'DELIVERED' || shipment.status === 'CANCELLED' || shipment.status === 'RETURNED')) {
      if (watchIdRef.current !== null) {
        navigator.geolocation.clearWatch(watchIdRef.current);
        watchIdRef.current = null;
      }
      setIsTracking(false);
      setTransmissionStatus('idle');
    }
  }, [shipment?.status]);

  // OTP 60-Second Cooldown Timer
  useEffect(() => {
    if (otpCountdown <= 0) return;
    const interval = setInterval(() => {
      setOtpCountdown((prev) => prev - 1);
    }, 1000);
    return () => clearInterval(interval);
  }, [otpCountdown]);

  // Geolocation Position Handler
  const handleNewPosition = useCallback(async (position) => {
    if (!position || !position.coords) return;

    const { latitude, longitude, accuracy, speed, heading } = position.coords;
    const timestamp = position.timestamp ? new Date(position.timestamp) : new Date();

    if (latitude == null || longitude == null || isNaN(latitude) || isNaN(longitude)) {
      return;
    }

    const speedKmh = speed != null && speed >= 0 ? Number((speed * 3.6).toFixed(1)) : null;
    const bearing = heading != null && !isNaN(heading) ? Math.round(heading) : null;
    const roundedAccuracy = accuracy != null ? Math.round(accuracy) : null;

    const coords = {
      latitude: Number(latitude.toFixed(7)),
      longitude: Number(longitude.toFixed(7)),
      accuracy: roundedAccuracy,
      speed: speedKmh,
      bearing,
      timestamp: timestamp.toISOString(),
    };

    setCurrentCoords(coords);

    // Validate accuracy: backend requires accuracy <= 30.0 meters
    if (accuracy != null && accuracy > 30.0) {
      setGpsError(
        `GPS accuracy radius (±${roundedAccuracy}m) exceeds the 30m maximum threshold. Waiting for stronger satellite fix...`
      );
      setTransmissionStatus('throttled');
      return;
    }

    setGpsError(null);

    // Only broadcast telemetry when shipment is OUT_FOR_DELIVERY
    if (!shipment || shipment.status !== 'OUT_FOR_DELIVERY') {
      return;
    }

    // Rate-limiting: backend enforces at least 5 seconds between updates
    const now = Date.now();
    if (now - lastSentTimeRef.current < 6000) {
      setTransmissionStatus('throttled');
      return;
    }

    // Send valid telemetry to backend
    try {
      setTransmissionStatus('transmitting');
      lastSentTimeRef.current = now;

      const payload = {
        latitude: coords.latitude,
        longitude: coords.longitude,
        accuracy: coords.accuracy != null ? Math.min(coords.accuracy, 30.0) : 10.0,
        speed: coords.speed != null && coords.speed <= 140.0 ? coords.speed : null,
        bearing: coords.bearing,
        timestamp: coords.timestamp,
      };

      const res = await shipmentService.sendLiveLocation(shipment.id, payload);

      if (res.success && res.data) {
        setLastSentLocation({
          latitude: res.data.latitude,
          longitude: res.data.longitude,
          recordedAt: res.data.recordedAt || new Date().toISOString(),
        });
        setTransmissionStatus('synced');
      }
    } catch (err) {
      if (err.message && err.message.includes('5 seconds')) {
        setTransmissionStatus('throttled');
      } else {
        setTransmissionStatus('error');
        setGpsError(err.message || 'Telemetry transmission rejected by server');
      }
    }
  }, [shipment]);

  // Geolocation Error Handler
  const handlePositionError = useCallback((err) => {
    setTransmissionStatus('error');
    switch (err.code) {
      case 1: // PERMISSION_DENIED
        setGpsPermission('denied');
        setGpsError(
          'Location access was denied. Please allow location access in your browser permissions to transmit live coordinates.'
        );
        if (watchIdRef.current !== null) {
          navigator.geolocation.clearWatch(watchIdRef.current);
          watchIdRef.current = null;
        }
        setIsTracking(false);
        break;
      case 2: // POSITION_UNAVAILABLE
        setGpsError('Position unavailable. Unable to determine device GPS coordinates.');
        break;
      case 3: // TIMEOUT
        setGpsError('GPS acquisition timed out. Re-attempting satellite lock...');
        break;
      default:
        setGpsError(err.message || 'Error acquiring device position.');
        break;
    }
  }, []);

  // Start Live Tracking Watch
  const startLiveTracking = () => {
    if (!navigator.geolocation) {
      setGpsPermission('unsupported');
      setGpsError('Geolocation is not supported by your current browser.');
      return;
    }

    setGpsError(null);
    setIsTracking(true);

    // Clear any previous active watch
    if (watchIdRef.current !== null) {
      navigator.geolocation.clearWatch(watchIdRef.current);
      watchIdRef.current = null;
    }

    const options = {
      enableHighAccuracy: true,
      timeout: 15000,
      maximumAge: 0,
    };

    const id = navigator.geolocation.watchPosition(
      (position) => {
        setGpsPermission('granted');
        handleNewPosition(position);
      },
      (err) => {
        handlePositionError(err);
      },
      options
    );

    watchIdRef.current = id;
  };

  // Stop Live Tracking Watch
  const stopLiveTracking = () => {
    if (watchIdRef.current !== null) {
      navigator.geolocation.clearWatch(watchIdRef.current);
      watchIdRef.current = null;
    }
    setIsTracking(false);
    setTransmissionStatus('idle');
  };

  // Quick Action to transition shipment to OUT_FOR_DELIVERY if in previous state
  const handleStartDeliveryRun = async () => {
    if (!shipment) return;
    setProgressingStatus(true);
    try {
      const res = await shipmentService.updateStatus(shipment.id, {
        status: 'OUT_FOR_DELIVERY',
        remarks: 'Delivery agent activated live delivery run mode',
      });
      if (res.success && res.data) {
        setShipment(res.data);
      }
    } catch (err) {
      setShipmentError(err.message || 'Failed to start delivery run');
    } finally {
      setProgressingStatus(false);
    }
  };

  // Initiate Delivery OTP Generation
  const handleRequestOtp = async () => {
    if (!shipment) return;
    setRequestingOtp(true);
    setRequestOtpError(null);
    setOtpError(null);

    try {
      const res = await shipmentService.generateDeliveryOtp(shipment.id);
      if (res.success && res.data) {
        setOtpRequested(true);
        setDevOtp(res.data.otp || null);
        setOtpExpiresAt(res.data.expiresAt || null);
        setOtpCountdown(60); // 60-second cooldown
      } else {
        setRequestOtpError(res.message || 'Failed to generate delivery OTP');
      }
    } catch (err) {
      setRequestOtpError(err.message || 'Error communicating with OTP service');
    } finally {
      setRequestingOtp(false);
    }
  };

  // Submit OTP Verification to Complete Delivery
  const handleVerifyOtp = async (e) => {
    e.preventDefault();
    if (!otpInput || otpInput.trim().length !== 6 || !/^\d{6}$/.test(otpInput.trim())) {
      setOtpError('OTP must be exactly 6 numeric digits.');
      return;
    }

    setSubmittingOtp(true);
    setOtpError(null);

    try {
      const payload = {
        otp: otpInput.trim(),
        recipientName: recipientName.trim() || undefined,
        verificationNotes: verificationNotes.trim() || undefined,
        locationName: locationName.trim() || undefined,
        latitude: currentCoords?.latitude,
        longitude: currentCoords?.longitude,
      };

      const res = await shipmentService.verifyDeliveryOtp(shipment.id, payload);

      if (res.success && res.data) {
        // Stop active GPS tracking upon successful delivery handover
        stopLiveTracking();
        setIsDelivered(true);
        setDeliveryProof(res.data);
        setShipment((prev) => ({
          ...prev,
          status: 'DELIVERED',
        }));
      } else {
        setOtpError(res.message || 'Delivery verification failed');
      }
    } catch (err) {
      setOtpError(err.message || 'Invalid delivery OTP. Please verify with recipient.');
    } finally {
      setSubmittingOtp(false);
    }
  };

  if (loadingShipment) {
    return (
      <div className="p-16 text-center" data-testid="delivery-page-loading">
        <RefreshCw className="w-8 h-8 text-indigo-600 animate-spin mx-auto mb-3" />
        <p className="text-sm font-semibold text-slate-700">Loading delivery run...</p>
        <p className="text-xs text-slate-500 mt-1">Connecting to driver navigation and telemetry hub.</p>
      </div>
    );
  }

  if (shipmentError && !shipment) {
    return (
      <div className="max-w-2xl mx-auto space-y-4" data-testid="delivery-page-error">
        <Link
          to="/driver/dashboard"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-600 hover:text-slate-900 transition-colors"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>Back to Assigned Queue</span>
        </Link>
        <div className="bg-rose-50 border border-rose-200 rounded-2xl p-6 text-rose-800 shadow-sm">
          <div className="flex items-center gap-2 mb-2 font-bold text-sm">
            <AlertCircle className="w-5 h-5 text-rose-600" />
            <span>Unable to Access Delivery Run</span>
          </div>
          <p className="text-xs text-rose-700 mb-4">{shipmentError}</p>
          <button
            onClick={fetchShipment}
            className="px-3.5 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-700 text-white font-semibold text-xs transition-colors shadow-sm"
          >
            Retry Connection
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6" data-testid="driver-delivery-container">
      {/* Navigation & Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <Link
            to="/driver/dashboard"
            data-testid="back-to-queue-link"
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-slate-900 transition-colors mb-2"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            <span>Back to Driver Portal</span>
          </Link>
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="text-2xl font-bold text-slate-900 tracking-tight" data-testid="delivery-mode-title">
              Active Delivery Run Mode
            </h1>
            <span className="font-mono text-xs font-bold text-slate-900 bg-slate-100 px-2.5 py-1 rounded-lg border border-slate-200">
              {shipment.trackingNumber}
            </span>
            <span
              className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-bold border ${
                shipment.status === 'DELIVERED'
                  ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                  : shipment.status === 'OUT_FOR_DELIVERY'
                  ? 'bg-indigo-50 text-indigo-700 border-indigo-200'
                  : 'bg-amber-50 text-amber-700 border-amber-200'
              }`}
              data-testid="shipment-status-badge"
            >
              <span
                className={`w-2 h-2 rounded-full ${
                  shipment.status === 'DELIVERED'
                    ? 'bg-emerald-500'
                    : shipment.status === 'OUT_FOR_DELIVERY'
                    ? 'bg-indigo-500 animate-pulse'
                    : 'bg-amber-500'
                }`}
              />
              {shipment.status.replace(/_/g, ' ')}
            </span>
          </div>
        </div>

        {/* Global Public Tracking Link */}
        <div className="flex items-center gap-2">
          <Link
            to={`/track/${shipment.trackingNumber}`}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg bg-white border border-slate-200 text-slate-700 hover:bg-slate-50 text-xs font-semibold shadow-sm transition-colors"
          >
            <ExternalLink className="w-3.5 h-3.5 text-slate-400" />
            <span>Open Public Tracking</span>
          </Link>
        </div>
      </div>

      {/* Guidance Banner if shipment is NOT YET OUT_FOR_DELIVERY */}
      {shipment.status !== 'OUT_FOR_DELIVERY' && shipment.status !== 'DELIVERED' && (
        <div
          data-testid="status-guidance-banner"
          className="bg-amber-50 border border-amber-200 rounded-2xl p-4 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 text-amber-900 shadow-sm"
        >
          <div className="flex items-start gap-3">
            <Info className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
            <div className="text-xs">
              <p className="font-bold text-sm text-amber-950">Shipment Status: {shipment.status.replace(/_/g, ' ')}</p>
              <p className="text-amber-800 mt-0.5">
                Live GPS telemetry transmission and OTP delivery confirmation are active only when the parcel is Out for Delivery.
              </p>
            </div>
          </div>
          <button
            onClick={handleStartDeliveryRun}
            disabled={progressingStatus}
            data-testid="start-delivery-run-btn"
            className="inline-flex items-center justify-center gap-1.5 px-4 py-2 rounded-lg bg-amber-600 hover:bg-amber-700 text-white font-bold text-xs shadow-sm transition-colors shrink-0 disabled:opacity-50"
          >
            {progressingStatus && <RefreshCw className="w-3.5 h-3.5 animate-spin" />}
            <span>Start Delivery Run (Set Out for Delivery)</span>
          </button>
        </div>
      )}

      {/* Confirmed Delivery Handover Card */}
      {isDelivered && (
        <div
          data-testid="delivery-confirmed-card"
          className="bg-emerald-50 border border-emerald-200 rounded-2xl p-6 shadow-sm animate-in fade-in zoom-in-95 duration-200"
        >
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="flex items-center gap-4">
              <div className="w-12 h-12 rounded-2xl bg-emerald-100 text-emerald-700 flex items-center justify-center shadow-inner">
                <CheckCircle2 className="w-7 h-7 text-emerald-600" />
              </div>
              <div>
                <span className="text-[10px] font-bold text-emerald-700 uppercase tracking-wider block">
                  Proof of Delivery Recorded
                </span>
                <h3 className="text-lg font-bold text-emerald-950">Parcel Successfully Delivered!</h3>
                <p className="text-xs text-emerald-800 mt-0.5">
                  Handover confirmed to <strong className="font-semibold">{deliveryProof?.recipientName || shipment.recipientName}</strong> via electronic OTP.
                </p>
              </div>
            </div>
            <div className="text-left sm:text-right text-xs text-emerald-700 font-mono">
              {deliveryProof?.deliveryProofId && (
                <div>Proof ID: #{deliveryProof.deliveryProofId}</div>
              )}
              {deliveryProof?.deliveredAt && (
                <div>Verified: {new Date(deliveryProof.deliveredAt).toLocaleTimeString()}</div>
              )}
              <Link
                to="/driver/dashboard"
                className="mt-2 inline-flex items-center gap-1 text-xs font-bold text-emerald-800 hover:text-emerald-900 underline"
              >
                Return to Active Queue
              </Link>
            </div>
          </div>
        </div>
      )}

      {/* Main Grid: Telemetry & Navigation on Left, Delivery Actions & Handover on Right */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column: Live GPS Transmission & Interactive Map (7 cols) */}
        <div className="lg:col-span-7 space-y-6">
          {/* GPS Telemetry Control Panel */}
          <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-sm space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 border-b border-slate-100 pb-4">
              <div>
                <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                  <Radio className={`w-4 h-4 ${isTracking ? 'text-emerald-500 animate-pulse' : 'text-slate-400'}`} />
                  <span>Live GPS Telemetry Broadcasting</span>
                </h2>
                <p className="text-xs text-slate-500 mt-0.5">
                  Stream genuine browser GPS coordinates to the customer live tracking map.
                </p>
              </div>

              {/* Start / Stop GPS Tracking Button */}
              <div>
                {isTracking ? (
                  <button
                    onClick={stopLiveTracking}
                    data-testid="stop-tracking-btn"
                    className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-700 text-white font-bold text-xs shadow-sm transition-all"
                  >
                    <Square className="w-3.5 h-3.5 fill-current" />
                    <span>Stop Live Tracking</span>
                  </button>
                ) : (
                  <button
                    onClick={startLiveTracking}
                    disabled={isDelivered}
                    data-testid="start-tracking-btn"
                    className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs shadow-sm transition-all disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    <Play className="w-3.5 h-3.5 fill-current" />
                    <span>Start Live Tracking</span>
                  </button>
                )}
              </div>
            </div>

            {/* GPS Diagnostic Alerts */}
            {gpsError && (
              <div
                data-testid="gps-error-alert"
                className="p-3.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-start gap-2.5 shadow-sm"
              >
                <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                <div className="flex-1">
                  <p className="font-semibold">{gpsError}</p>
                  {gpsPermission === 'denied' && (
                    <p className="text-[11px] text-rose-600 mt-1">
                      To re-enable: Click the lock/tune icon in your browser URL bar, allow Location access, and click Start Live Tracking again.
                    </p>
                  )}
                </div>
              </div>
            )}

            {/* Telemetry Status Strip */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs" data-testid="telemetry-status-grid">
              {/* Permission Indicator */}
              <div className="bg-slate-50 p-2.5 rounded-xl border border-slate-200/60">
                <span className="text-[10px] uppercase font-bold text-slate-400 block mb-0.5">Permission</span>
                <span
                  data-testid="gps-permission-status"
                  className={`font-bold capitalize ${
                    gpsPermission === 'granted'
                      ? 'text-emerald-700'
                      : gpsPermission === 'denied'
                      ? 'text-rose-700'
                      : 'text-slate-600'
                  }`}
                >
                  {gpsPermission}
                </span>
              </div>

              {/* Broadcast Status Indicator */}
              <div className="bg-slate-50 p-2.5 rounded-xl border border-slate-200/60">
                <span className="text-[10px] uppercase font-bold text-slate-400 block mb-0.5">Broadcast</span>
                <span
                  data-testid="gps-broadcast-status"
                  className={`font-bold ${
                    isTracking ? 'text-emerald-700' : 'text-slate-500'
                  }`}
                >
                  {isTracking ? 'Active' : 'Stopped'}
                </span>
              </div>

              {/* Transmission State Indicator */}
              <div className="bg-slate-50 p-2.5 rounded-xl border border-slate-200/60">
                <span className="text-[10px] uppercase font-bold text-slate-400 block mb-0.5">Server Sync</span>
                <span
                  data-testid="transmission-status-indicator"
                  className={`font-bold capitalize ${
                    transmissionStatus === 'synced'
                      ? 'text-teal-700'
                      : transmissionStatus === 'transmitting'
                      ? 'text-blue-700'
                      : transmissionStatus === 'throttled'
                      ? 'text-amber-700'
                      : transmissionStatus === 'error'
                      ? 'text-rose-700'
                      : 'text-slate-500'
                  }`}
                >
                  {transmissionStatus === 'throttled' ? 'Throttled (5s)' : transmissionStatus}
                </span>
              </div>

              {/* Accuracy Indicator */}
              <div className="bg-slate-50 p-2.5 rounded-xl border border-slate-200/60">
                <span className="text-[10px] uppercase font-bold text-slate-400 block mb-0.5">Accuracy</span>
                <span
                  data-testid="gps-accuracy-indicator"
                  className={`font-bold ${
                    currentCoords?.accuracy && currentCoords.accuracy <= 30
                      ? 'text-emerald-700'
                      : 'text-slate-700'
                  }`}
                >
                  {currentCoords?.accuracy ? `±${currentCoords.accuracy}m` : 'Awaiting lock'}
                </span>
              </div>
            </div>

            {/* Live Coordinates Detail Box */}
            {currentCoords && (
              <div
                data-testid="current-coords-display"
                className="bg-indigo-50/50 border border-indigo-100 rounded-xl p-3 text-xs flex flex-wrap items-center justify-between gap-3 text-slate-700"
              >
                <div>
                  <span className="text-[10px] uppercase font-bold text-indigo-700 block">Coordinates</span>
                  <span className="font-mono font-bold text-slate-900">
                    {currentCoords.latitude}, {currentCoords.longitude}
                  </span>
                </div>
                {currentCoords.speed != null && (
                  <div>
                    <span className="text-[10px] uppercase font-bold text-indigo-700 block">Speed</span>
                    <span className="font-bold text-slate-800">{currentCoords.speed} km/h</span>
                  </div>
                )}
                {lastSentLocation?.recordedAt && (
                  <div>
                    <span className="text-[10px] uppercase font-bold text-indigo-700 block">Last Synced</span>
                    <span className="font-mono text-slate-800 text-[11px]" data-testid="last-synced-time">
                      {new Date(lastSentLocation.recordedAt).toLocaleTimeString()}
                    </span>
                  </div>
                )}
              </div>
            )}

            {/* Mobile Browser Background Limitation Disclaimer */}
            <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/60 text-[11px] text-slate-500 flex items-start gap-2">
              <Info className="w-3.5 h-3.5 text-slate-400 shrink-0 mt-0.5" />
              <span>
                <strong>Browser Geolocation Notice:</strong> Browser location streaming requires this tab to remain open and active. Continuous background GPS cannot be guaranteed if the browser is backgrounded or your device goes to sleep.
              </span>
            </div>
          </div>

          {/* Real-time Map Preview */}
          <div className="bg-white border border-slate-200 rounded-2xl overflow-hidden shadow-sm">
            <div className="border-b border-slate-200 p-4 flex items-center justify-between">
              <h3 className="font-bold text-slate-900 text-xs uppercase tracking-wider flex items-center gap-1.5">
                <MapPin className="w-4 h-4 text-indigo-600" />
                <span>Live Route Map</span>
              </h3>
              <span className="text-[11px] text-slate-500">
                Destination: {shipment.recipientAddress}
              </span>
            </div>
            <LiveTrackingMap
              trackingNumber={shipment.trackingNumber}
              location={
                currentCoords
                  ? {
                      latitude: currentCoords.latitude,
                      longitude: currentCoords.longitude,
                      recordedAt: currentCoords.timestamp,
                      status: shipment.status,
                      fresh: true,
                      speed: currentCoords.speed,
                      bearing: currentCoords.bearing,
                    }
                  : null
              }
              connectionStatus={isTracking ? 'live' : 'offline'}
              height="380px"
            />
          </div>
        </div>

        {/* Right Column: Consignment Details & OTP Verification (5 cols) */}
        <div className="lg:col-span-5 space-y-6">
          {/* Recipient Handover Details Card */}
          <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-sm space-y-3">
            <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Recipient & Consignment
            </h3>

            <div className="space-y-2 text-xs">
              <div className="bg-slate-50 p-3 rounded-xl border border-slate-200/60 space-y-1.5">
                <div className="flex items-center gap-1.5 font-bold text-slate-900 text-sm">
                  <User className="w-4 h-4 text-slate-400" />
                  <span data-testid="recipient-name-info">{shipment.recipientName}</span>
                </div>
                <div className="text-slate-600 flex items-start gap-1.5">
                  <MapPin className="w-4 h-4 text-rose-500 shrink-0 mt-0.5" />
                  <span data-testid="recipient-address-info">{shipment.recipientAddress}</span>
                </div>
                {shipment.recipientPhone && (
                  <div className="text-slate-600 flex items-center gap-1.5 font-mono">
                    <Phone className="w-3.5 h-3.5 text-slate-400" />
                    <span>{shipment.recipientPhone}</span>
                  </div>
                )}
              </div>

              <div className="flex items-center justify-between text-[11px] text-slate-500 px-1">
                <span>Weight: <strong className="text-slate-700">{shipment.weightKg} kg</strong></span>
                {shipment.estimatedPrice && (
                  <span>Fee: <strong className="text-slate-700">${Number(shipment.estimatedPrice).toFixed(2)}</strong></span>
                )}
                <span>Origin: <strong className="text-slate-700">{shipment.originAddress || 'Central Depot'}</strong></span>
              </div>
            </div>
          </div>

          {/* Electronic OTP Delivery Confirmation Handover Section */}
          <div
            data-testid="otp-confirmation-section"
            className="bg-white border border-slate-200 rounded-2xl p-5 shadow-sm space-y-4"
          >
            <div className="border-b border-slate-100 pb-3">
              <div className="flex items-center justify-between">
                <h3 className="font-bold text-slate-900 text-sm flex items-center gap-2">
                  <KeyRound className="w-4 h-4 text-indigo-600" />
                  <span>Electronic Proof of Delivery</span>
                </h3>
                <span className="text-[10px] font-bold text-indigo-700 bg-indigo-50 border border-indigo-200 px-2 py-0.5 rounded-full">
                  Secure OTP
                </span>
              </div>
              <p className="text-xs text-slate-500 mt-1">
                Collect the 6-digit verification code from the recipient to confirm delivery handover.
              </p>
            </div>

            {/* OTP Generation Trigger & Cooldown */}
            <div>
              <div className="flex items-center justify-between gap-2">
                <button
                  type="button"
                  onClick={handleRequestOtp}
                  disabled={requestingOtp || otpCountdown > 0 || isDelivered}
                  data-testid="request-otp-btn"
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 font-semibold text-xs shadow-sm transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {requestingOtp ? (
                    <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                  ) : (
                    <KeyRound className="w-3.5 h-3.5" />
                  )}
                  <span>
                    {otpCountdown > 0
                      ? `Re-send OTP in ${otpCountdown}s`
                      : otpRequested
                      ? 'Re-send Delivery OTP'
                      : 'Generate / Send Delivery OTP'}
                  </span>
                </button>

                {otpExpiresAt && (
                  <span className="text-[11px] text-slate-500 font-medium flex items-center gap-1">
                    <Clock className="w-3 h-3 text-slate-400" />
                    <span>Valid 10 mins</span>
                  </span>
                )}
              </div>

              {requestOtpError && (
                <div
                  data-testid="request-otp-error"
                  className="mt-2 p-2.5 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-center gap-2"
                >
                  <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                  <span>{requestOtpError}</span>
                </div>
              )}
            </div>

            {/* Development Mode Simulated OTP Display Box */}
            {devOtp && !isDelivered && (
              <div
                data-testid="dev-otp-simulation-box"
                className="bg-amber-50 border border-amber-200 rounded-xl p-3 text-xs text-amber-900 space-y-1 shadow-sm"
              >
                <div className="flex items-center justify-between font-bold">
                  <span className="flex items-center gap-1.5 text-amber-950">
                    <ShieldCheck className="w-4 h-4 text-amber-600" />
                    <span>Development Simulation</span>
                  </span>
                  <span className="font-mono text-sm tracking-widest bg-amber-100 text-amber-900 px-2.5 py-0.5 rounded border border-amber-300 font-bold" data-testid="dev-otp-code">
                    {devOtp}
                  </span>
                </div>
                <p className="text-[11px] text-amber-800">
                  Authoritative OTP generated by server. In production, this code is transmitted directly and privately to the customer phone/email.
                </p>
              </div>
            )}

            {/* Handover Verification Form */}
            {!isDelivered ? (
              <form onSubmit={handleVerifyOtp} className="space-y-4 pt-1" data-testid="otp-verification-form">
                {otpError && (
                  <div
                    data-testid="otp-verification-error"
                    className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-start gap-2 shadow-sm"
                  >
                    <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                    <span>{otpError}</span>
                  </div>
                )}

                {/* 6-Digit OTP Code Input */}
                <div>
                  <label htmlFor="otp-input" className="block text-xs font-bold text-slate-700 mb-1">
                    Recipient 6-Digit OTP <span className="text-rose-500">*</span>
                  </label>
                  <input
                    id="otp-input"
                    data-testid="otp-code-input"
                    type="text"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    maxLength={6}
                    value={otpInput}
                    onChange={(e) => {
                      const val = e.target.value.replace(/\D/g, '');
                      setOtpInput(val);
                      if (otpError) setOtpError(null);
                    }}
                    placeholder="Enter 6 digits (e.g. 123456)"
                    className="w-full tracking-widest font-mono text-base font-bold text-center px-4 py-2.5 border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-600"
                  />
                  <p className="text-[10px] text-slate-400 mt-1">
                    Maximum 3 failed attempts before OTP lock.
                  </p>
                </div>

                {/* Handover Recipient Name */}
                <div>
                  <label htmlFor="recipient-name-input" className="block text-xs font-bold text-slate-700 mb-1">
                    Handover Person Name <span className="text-slate-400 font-normal">(if different)</span>
                  </label>
                  <input
                    id="recipient-name-input"
                    data-testid="recipient-name-input"
                    type="text"
                    value={recipientName}
                    onChange={(e) => setRecipientName(e.target.value)}
                    maxLength={120}
                    placeholder="e.g. Robert Vance (neighbor)"
                    className="w-full text-xs px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                  />
                </div>

                {/* Verification Notes / Remarks */}
                <div>
                  <label htmlFor="verification-notes-input" className="block text-xs font-bold text-slate-700 mb-1">
                    Handover Remarks <span className="text-slate-400 font-normal">(Optional)</span>
                  </label>
                  <input
                    id="verification-notes-input"
                    data-testid="verification-notes-input"
                    type="text"
                    value={verificationNotes}
                    onChange={(e) => setVerificationNotes(e.target.value)}
                    maxLength={500}
                    placeholder="e.g. Handed to recipient in person at front door"
                    className="w-full text-xs px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                  />
                </div>

                {/* Submit Verification Button */}
                <button
                  type="submit"
                  disabled={submittingOtp || otpInput.length !== 6 || shipment.status !== 'OUT_FOR_DELIVERY'}
                  data-testid="verify-otp-submit-btn"
                  className="w-full inline-flex items-center justify-center gap-2 px-4 py-3 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-bold text-xs shadow-md transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {submittingOtp ? (
                    <RefreshCw className="w-4 h-4 animate-spin" />
                  ) : (
                    <ShieldCheck className="w-4 h-4" />
                  )}
                  <span>{submittingOtp ? 'Verifying Handover...' : 'Verify OTP & Complete Delivery'}</span>
                </button>
              </form>
            ) : (
              <div className="bg-emerald-50 rounded-xl p-4 text-center text-xs text-emerald-800 space-y-1">
                <CheckCircle2 className="w-6 h-6 text-emerald-600 mx-auto mb-1" />
                <p className="font-bold">Delivery Handover Finalized</p>
                <p className="text-[11px] text-emerald-700">This consignment is officially marked as Delivered.</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
