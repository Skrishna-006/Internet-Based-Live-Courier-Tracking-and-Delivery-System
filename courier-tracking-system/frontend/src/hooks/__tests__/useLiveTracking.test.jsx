import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useLiveTracking } from '../useLiveTracking';
import { trackingService } from '../../services/api';
import * as wsModule from '../../services/websocket';

vi.mock('../../services/api', () => ({
  trackingService: {
    getPublicTracking: vi.fn(),
    getPublicLiveLocation: vi.fn(),
  },
}));

describe('useLiveTracking Hook', () => {
  let mockStompClient;
  let mockSubscription;
  let clientCallbacks = {};

  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers({ shouldAdvanceTime: true });

    mockSubscription = {
      unsubscribe: vi.fn(),
    };

    clientCallbacks = {};

    mockStompClient = {
      activate: vi.fn(),
      deactivate: vi.fn(),
      subscribe: vi.fn((destination, callback) => {
        clientCallbacks.destination = destination;
        clientCallbacks.onMessage = callback;
        return mockSubscription;
      }),
    };

    vi.spyOn(wsModule, 'createStompClient').mockImplementation((options) => {
      clientCallbacks.options = options;
      return mockStompClient;
    });

    trackingService.getPublicTracking.mockResolvedValue({
      data: {
        trackingNumber: 'TRK-20261026-TEST',
        status: 'OUT_FOR_DELIVERY',
        milestones: [
          { status: 'BOOKED', description: 'Consignment booked', timestamp: '2026-10-26T10:00:00Z' },
        ],
      },
    });

    trackingService.getPublicLiveLocation.mockResolvedValue({
      data: {
        trackingNumber: 'TRK-20261026-TEST',
        latitude: 12.9716,
        longitude: 77.5946,
        recordedAt: '2026-10-26T10:15:00Z',
        status: 'OUT_FOR_DELIVERY',
        fresh: true,
      },
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('initializes and fetches public tracking and live location data', async () => {
    const { result } = renderHook(() => useLiveTracking('TRK-20261026-TEST'));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(trackingService.getPublicTracking).toHaveBeenCalledWith('TRK-20261026-TEST');
    expect(trackingService.getPublicLiveLocation).toHaveBeenCalledWith('TRK-20261026-TEST');
    expect(result.current.shipment?.trackingNumber).toBe('TRK-20261026-TEST');
    expect(result.current.location?.latitude).toBe(12.9716);
    expect(result.current.location?.longitude).toBe(77.5946);
  });

  it('subscribes to STOMP customer topic on WebSocket connect and updates location on broadcast', async () => {
    let result;
    // Drain the initial async effects (loadShipmentData + fetchLiveLocationFallback) so
    // their promise resolutions are properly wrapped in act() before we fire STOMP callbacks.
    await act(async () => {
      ({ result } = renderHook(() => useLiveTracking('TRK-20261026-TEST')));
      // Flush microtask queue so mock resolved promises settle inside act()
      await Promise.resolve();
    });

    // Trigger STOMP onConnect callback
    act(() => {
      clientCallbacks.options.onConnect({});
    });

    expect(result.current.connectionStatus).toBe('live');
    expect(mockStompClient.subscribe).toHaveBeenCalledWith(
      '/topic/track/TRK-20261026-TEST',
      expect.any(Function)
    );

    // Simulate incoming broadcast message
    const incomingBroadcast = {
      trackingNumber: 'TRK-20261026-TEST',
      latitude: 12.9800,
      longitude: 77.6000,
      status: 'OUT_FOR_DELIVERY',
      speed: 35.2,
      bearing: 90,
      timestamp: '2026-10-26T10:16:00Z',
    };

    act(() => {
      clientCallbacks.onMessage({ body: JSON.stringify(incomingBroadcast) });
    });

    expect(result.current.location.latitude).toBe(12.98);
    expect(result.current.location.longitude).toBe(77.6);
    expect(result.current.location.speed).toBe(35.2);
    expect(result.current.location.bearing).toBe(90);
    expect(result.current.location.fresh).toBe(true);
  });

  it('falls back to polling when WebSocket disconnects or fails', async () => {
    let result;
    // Wrap renderHook + initial async effects together so promise resolutions
    // from loadShipmentData and fetchLiveLocationFallback are covered by act().
    await act(async () => {
      ({ result } = renderHook(() =>
        useLiveTracking('TRK-20261026-TEST', { pollingInterval: 5000 })
      ));
      await Promise.resolve();
    });

    trackingService.getPublicLiveLocation.mockClear();

    // Simulate STOMP disconnect or error — startPolling() fires fetchLiveLocationFallback()
    // which is async, so use await act(async) to drain the microtask queue fully.
    await act(async () => {
      clientCallbacks.options.onError(new Error('Connection failed'));
      await Promise.resolve();
    });

    expect(result.current.connectionStatus).toBe('polling');
    expect(trackingService.getPublicLiveLocation).toHaveBeenCalledWith('TRK-20261026-TEST');
  });

  it('restores live status and stops polling when WebSocket reconnects', async () => {
    let result;
    await act(async () => {
      ({ result } = renderHook(() => useLiveTracking('TRK-20261026-TEST')));
      await Promise.resolve();
    });

    // Disconnect -> polling — onDisconnect calls startPolling() → fetchLiveLocationFallback() (async)
    await act(async () => {
      clientCallbacks.options.onDisconnect();
      await Promise.resolve();
    });
    expect(result.current.connectionStatus).toBe('polling');

    // Reconnect -> live
    act(() => {
      clientCallbacks.options.onConnect({});
    });
    expect(result.current.connectionStatus).toBe('live');
  });

  it('handles stale location from API response without crashing', async () => {
    trackingService.getPublicLiveLocation.mockResolvedValueOnce({
      data: {
        trackingNumber: 'TRK-20261026-TEST',
        latitude: 12.9716,
        longitude: 77.5946,
        recordedAt: '2026-10-26T09:00:00Z',
        status: 'OUT_FOR_DELIVERY',
        fresh: false,
      },
    });

    const { result } = renderHook(() => useLiveTracking('TRK-20261026-TEST'));

    await waitFor(() => {
      expect(result.current.location?.fresh).toBe(false);
    });
  });

  it('stops polling and transitions to offline when shipment reaches DELIVERED', async () => {
    let result;
    await act(async () => {
      ({ result } = renderHook(() => useLiveTracking('TRK-20261026-TEST')));
      await Promise.resolve();
    });

    act(() => {
      clientCallbacks.options.onConnect({});
    });

    // Delivered broadcast
    act(() => {
      clientCallbacks.onMessage({
        body: JSON.stringify({
          trackingNumber: 'TRK-20261026-TEST',
          latitude: 12.9716,
          longitude: 77.5946,
          status: 'DELIVERED',
          timestamp: new Date().toISOString(),
        }),
      });
    });

    expect(result.current.connectionStatus).toBe('offline');
  });

  it('cleans up STOMP client, subscriptions, and polling timers on unmount', async () => {
    let unmount;
    await act(async () => {
      ({ unmount } = renderHook(() => useLiveTracking('TRK-20261026-TEST')));
      await Promise.resolve();
    });

    act(() => {
      clientCallbacks.options.onConnect({});
    });

    unmount();

    expect(mockSubscription.unsubscribe).toHaveBeenCalled();
    expect(mockStompClient.deactivate).toHaveBeenCalled();
  });

  it('returns destination when shipment has destinationLatitude and destinationLongitude', async () => {
    trackingService.getPublicTracking.mockResolvedValue({
      data: {
        trackingNumber: 'TRK-20261026-TEST',
        status: 'OUT_FOR_DELIVERY',
        destinationLatitude: 13.0827,
        destinationLongitude: 80.2707,
        milestones: [],
      },
    });

    const { result } = renderHook(() => useLiveTracking('TRK-20261026-TEST'));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.destination).not.toBeNull();
    expect(result.current.destination.latitude).toBe(13.0827);
    expect(result.current.destination.longitude).toBe(80.2707);
    expect(result.current.destination.label).toBe('Delivery Destination');
  });

  it('returns null destination when shipment has no destination coordinates', async () => {
    trackingService.getPublicTracking.mockResolvedValue({
      data: {
        trackingNumber: 'TRK-20261026-TEST',
        status: 'OUT_FOR_DELIVERY',
        destinationLatitude: null,
        destinationLongitude: null,
        milestones: [],
      },
    });

    const { result } = renderHook(() => useLiveTracking('TRK-20261026-TEST'));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.destination).toBeNull();
  });
});
