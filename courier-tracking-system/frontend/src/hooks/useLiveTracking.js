import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { trackingService } from '../services/api';
import { createStompClient } from '../services/websocket';

const POLLING_INTERVAL_MS = 6000;
const TERMINAL_STATUSES = new Set(['DELIVERED', 'CANCELLED', 'RETURNED']);

/**
 * Custom React hook for live shipment tracking.
 * Coordinates real-time STOMP WebSocket broadcasts with automated REST polling fallback.
 *
 * @param {string} trackingNumber - Public tracking number
 * @param {Object} [options]
 * @param {number} [options.pollingInterval=6000] - Polling interval in ms
 * @param {string} [options.token] - Optional explicit auth token
 */
export function useLiveTracking(trackingNumber, options = {}) {
  const { pollingInterval = POLLING_INTERVAL_MS, token = null } = options;

  const [shipment, setShipment] = useState(null);
  const [location, setLocation] = useState(null);
  const [connectionStatus, setConnectionStatus] = useState('offline'); // 'live' | 'polling' | 'reconnecting' | 'offline'
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState(null);

  const clientRef = useRef(null);
  const subscriptionRef = useRef(null);
  const pollingTimerRef = useRef(null);
  const isPollingBusyRef = useRef(false);
  const isMountedRef = useRef(true);
  const activeWsConnectedRef = useRef(false);

  const destination = useMemo(() => {
    if (
      shipment &&
      shipment.destinationLatitude != null &&
      shipment.destinationLongitude != null &&
      !isNaN(Number(shipment.destinationLatitude)) &&
      !isNaN(Number(shipment.destinationLongitude))
    ) {
      return {
        latitude: Number(shipment.destinationLatitude),
        longitude: Number(shipment.destinationLongitude),
        label: 'Delivery Destination',
      };
    }
    return null;
  }, [shipment]);

  // Poll live location fallback
  const fetchLiveLocationFallback = useCallback(async (trk) => {
    if (!trk || isPollingBusyRef.current) return;
    isPollingBusyRef.current = true;

    try {
      const response = await trackingService.getPublicLiveLocation(trk);
      if (!isMountedRef.current) return;

      const locData = response?.data;
      if (locData) {
        setLocation({
          latitude: locData.latitude !== undefined && locData.latitude !== null ? Number(locData.latitude) : null,
          longitude: locData.longitude !== undefined && locData.longitude !== null ? Number(locData.longitude) : null,
          recordedAt: locData.recordedAt,
          status: locData.status,
          fresh: Boolean(locData.fresh),
          speed: null,
          bearing: null,
        });

        if (locData.recordedAt) {
          setLastUpdatedAt(new Date(locData.recordedAt));
        }

        // Check if shipment reached terminal state
        if (TERMINAL_STATUSES.has(locData.status)) {
          stopPolling();
          setConnectionStatus('offline');
        }
      }
    } catch (err) {
      // In polling fallback, non-fatal errors should not crash the page
      console.warn('Live location polling fallback query failed:', err?.message || err);
    } finally {
      isPollingBusyRef.current = false;
    }
  }, []);

  // Polling controller
  const startPolling = useCallback(() => {
    if (pollingTimerRef.current) return;
    // Don't poll if WebSocket is actively live
    if (activeWsConnectedRef.current) return;

    setConnectionStatus((prev) => (prev === 'reconnecting' ? 'reconnecting' : 'polling'));

    // Trigger initial poll immediately
    fetchLiveLocationFallback(trackingNumber);

    pollingTimerRef.current = setInterval(() => {
      if (!activeWsConnectedRef.current && isMountedRef.current) {
        fetchLiveLocationFallback(trackingNumber);
      }
    }, pollingInterval);
  }, [fetchLiveLocationFallback, trackingNumber, pollingInterval]);

  const stopPolling = useCallback(() => {
    if (pollingTimerRef.current) {
      clearInterval(pollingTimerRef.current);
      pollingTimerRef.current = null;
    }
  }, []);

  // Initial fetch of shipment summary and tracking metadata
  const loadShipmentData = useCallback(async (trk) => {
    if (!trk) return;
    setIsLoading(true);
    setError(null);

    try {
      const res = await trackingService.getPublicTracking(trk);
      if (!isMountedRef.current) return;

      const data = res?.data;
      setShipment(data);

      if (TERMINAL_STATUSES.has(data?.status)) {
        setConnectionStatus('offline');
      }
    } catch (err) {
      if (!isMountedRef.current) return;
      setError(err?.message || 'Shipment tracking information not found');
      setConnectionStatus('offline');
    } finally {
      if (isMountedRef.current) {
        setIsLoading(false);
      }
    }
  }, []);

  // Handle incoming STOMP message
  const handleStompMessage = useCallback((message) => {
    try {
      const payload = JSON.parse(message.body);
      if (!isMountedRef.current) return;

      setLocation({
        latitude: payload.latitude !== undefined && payload.latitude !== null ? Number(payload.latitude) : null,
        longitude: payload.longitude !== undefined && payload.longitude !== null ? Number(payload.longitude) : null,
        recordedAt: payload.timestamp,
        status: payload.status,
        fresh: true,
        speed: payload.speed !== undefined && payload.speed !== null ? Number(payload.speed) : null,
        bearing: payload.bearing !== undefined && payload.bearing !== null ? Number(payload.bearing) : null,
      });

      setLastUpdatedAt(payload.timestamp ? new Date(payload.timestamp) : new Date());

      if (TERMINAL_STATUSES.has(payload.status)) {
        setConnectionStatus('offline');
        activeWsConnectedRef.current = false;
        stopPolling();
      }
    } catch (err) {
      console.error('Failed to parse STOMP tracking broadcast:', err);
    }
  }, [stopPolling]);

  // Main setup effect
  useEffect(() => {
    isMountedRef.current = true;
    activeWsConnectedRef.current = false;

    if (!trackingNumber) {
      setShipment(null);
      setLocation(null);
      setConnectionStatus('offline');
      setIsLoading(false);
      return;
    }

    // 1. Load initial public shipment metadata
    loadShipmentData(trackingNumber);

    // 2. Fetch initial location
    fetchLiveLocationFallback(trackingNumber);

    // 3. Setup WebSocket STOMP client
    const client = createStompClient({
      token,
      onConnect: () => {
        if (!isMountedRef.current) return;
        activeWsConnectedRef.current = true;
        setConnectionStatus('live');
        // Stop polling now that real-time socket is active
        stopPolling();

        // Subscribe to customer tracking topic
        subscriptionRef.current = client.subscribe(
          `/topic/track/${trackingNumber}`,
          handleStompMessage
        );
      },
      onDisconnect: () => {
        if (!isMountedRef.current) return;
        activeWsConnectedRef.current = false;
        if (subscriptionRef.current) {
          try {
            subscriptionRef.current.unsubscribe();
          } catch {
            // ignore
          }
          subscriptionRef.current = null;
        }
        // Fall back to polling
        startPolling();
      },
      onError: (err) => {
        if (!isMountedRef.current) return;
        activeWsConnectedRef.current = false;
        // If STOMP error or authentication rejection, activate polling fallback
        startPolling();
      },
    });

    clientRef.current = client;

    try {
      client.activate();
    } catch (err) {
      // If activation fails immediately, activate polling fallback
      startPolling();
    }

    // Fallback timer: if WebSocket has not connected within 2 seconds, begin polling
    const wsWatchdog = setTimeout(() => {
      if (!activeWsConnectedRef.current && isMountedRef.current) {
        startPolling();
      }
    }, 2000);

    return () => {
      isMountedRef.current = false;
      activeWsConnectedRef.current = false;
      clearTimeout(wsWatchdog);
      stopPolling();

      if (subscriptionRef.current) {
        try {
          subscriptionRef.current.unsubscribe();
        } catch {
          // ignore
        }
        subscriptionRef.current = null;
      }

      if (clientRef.current) {
        try {
          clientRef.current.deactivate();
        } catch {
          // ignore
        }
        clientRef.current = null;
      }
    };
  }, [trackingNumber, token, loadShipmentData, fetchLiveLocationFallback, handleStompMessage, startPolling, stopPolling]);

  const refresh = useCallback(() => {
    if (trackingNumber) {
      loadShipmentData(trackingNumber);
      fetchLiveLocationFallback(trackingNumber);
    }
  }, [trackingNumber, loadShipmentData, fetchLiveLocationFallback]);

  return {
    shipment,
    location,
    connectionStatus,
    isLoading,
    error,
    lastUpdatedAt,
    refresh,
    destination,
  };
}
