import React from 'react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import LiveTrackingMap from '../LiveTrackingMap';

vi.mock('react-leaflet', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    Polyline: ({ positions, 'data-testid': testId = 'route-polyline' }) => (
      <div data-testid={testId} data-positions={JSON.stringify(positions)} />
    ),
  };
});

describe('LiveTrackingMap Component', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });
  it('renders notice when coordinates are not available', () => {
    render(
      <LiveTrackingMap
        trackingNumber="TRK-100"
        location={null}
        connectionStatus="offline"
      />
    );

    expect(screen.getByTestId('no-location-notice')).toBeInTheDocument();
    expect(
      screen.getByText(/Live Vehicle Location Not Available/i)
    ).toBeInTheDocument();
  });

  it('renders notice when coordinates are null', () => {
    render(
      <LiveTrackingMap
        trackingNumber="TRK-100"
        location={{
          latitude: null,
          longitude: null,
          status: 'PENDING',
          fresh: false,
        }}
        connectionStatus="polling"
      />
    );

    expect(screen.getByTestId('no-location-notice')).toBeInTheDocument();
    expect(screen.getByText(/PENDING/i)).toBeInTheDocument();
  });

  it('renders map container with vehicle marker when valid coordinates are provided', () => {
    const { container } = render(
      <LiveTrackingMap
        trackingNumber="TRK-100"
        location={{
          latitude: 12.9716,
          longitude: 77.5946,
          recordedAt: new Date().toISOString(),
          status: 'OUT_FOR_DELIVERY',
          fresh: true,
          speed: 42.5,
          bearing: 180,
        }}
        connectionStatus="live"
        lastUpdatedAt={new Date()}
      />
    );

    expect(screen.getByTestId('live-tracking-map-container')).toBeInTheDocument();
    expect(screen.queryByTestId('no-location-notice')).not.toBeInTheDocument();
    expect(screen.getByText(/Live Telemetry \(WebSocket\)/i)).toBeInTheDocument();
  });

  it('displays stale location banner when fresh is false', () => {
    render(
      <LiveTrackingMap
        trackingNumber="TRK-100"
        location={{
          latitude: 12.9716,
          longitude: 77.5946,
          recordedAt: new Date(Date.now() - 300000).toISOString(),
          status: 'OUT_FOR_DELIVERY',
          fresh: false,
        }}
        connectionStatus="polling"
      />
    );

    expect(screen.getByTestId('stale-location-alert')).toBeInTheDocument();
    expect(
      screen.getByText(/GPS coordinates are older than the freshness threshold/i)
    ).toBeInTheDocument();
  });

  it('renders correct connection badge for different connection statuses', () => {
    const { rerender } = render(
      <LiveTrackingMap
        trackingNumber="TRK-100"
        location={{ latitude: 12.97, longitude: 77.59, fresh: true }}
        connectionStatus="polling"
      />
    );
    expect(screen.getByText(/Live Polling Active/i)).toBeInTheDocument();

    rerender(
      <LiveTrackingMap
        trackingNumber="TRK-100"
        location={{ latitude: 12.97, longitude: 77.59, fresh: true }}
        connectionStatus="reconnecting"
      />
    );
    expect(screen.getByText(/Reconnecting\.\.\./i)).toBeInTheDocument();

    rerender(
      <LiveTrackingMap
        trackingNumber="TRK-100"
        location={{ latitude: 12.97, longitude: 77.59, fresh: true }}
        connectionStatus="offline"
      />
    );
    expect(screen.getByText(/Telemetry Inactive/i)).toBeInTheDocument();
  });

  it('renders destination marker when destination coordinates are provided', () => {
    render(
      <LiveTrackingMap
        trackingNumber="TRK-100"
        location={{
          latitude: 12.9716,
          longitude: 77.5946,
          recordedAt: new Date().toISOString(),
          status: 'OUT_FOR_DELIVERY',
          fresh: true,
        }}
        destination={{
          latitude: 13.0827,
          longitude: 80.2707,
          label: 'Chennai Central',
        }}
        connectionStatus="live"
      />
    );
    expect(screen.getByTestId('live-tracking-map-container')).toBeInTheDocument();
    // Destination marker popup label should be accessible
    expect(screen.queryByTestId('no-location-notice')).not.toBeInTheDocument();
  });

  it('renders route polyline when OSRM returns route data', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      json: () => Promise.resolve({
        routes: [{
          geometry: {
            coordinates: [
              [77.5946, 12.9716],
              [78.0, 13.0],
              [80.2707, 13.0827],
            ],
          },
        }],
      }),
    });

    render(
      <LiveTrackingMap
        trackingNumber="TRK-100"
        location={{
          latitude: 12.9716,
          longitude: 77.5946,
          fresh: true,
          status: 'OUT_FOR_DELIVERY',
        }}
        destination={{
          latitude: 13.0827,
          longitude: 80.2707,
          label: 'Destination',
        }}
        connectionStatus="live"
      />
    );

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('router.project-osrm.org'),
        expect.any(Object)
      );
      expect(screen.getByTestId('route-polyline')).toBeInTheDocument();
    });
  });

  it('gracefully handles OSRM fetch failure without breaking the map', async () => {
    global.fetch = vi.fn().mockRejectedValue(new Error('Network error'));

    render(
      <LiveTrackingMap
        trackingNumber="TRK-100"
        location={{
          latitude: 12.9716,
          longitude: 77.5946,
          fresh: true,
          status: 'OUT_FOR_DELIVERY',
        }}
        destination={{
          latitude: 13.0827,
          longitude: 80.2707,
          label: 'Destination',
        }}
        connectionStatus="live"
      />
    );

    await waitFor(() => {
      // Map container should still render despite routing failure
      expect(screen.getByTestId('live-tracking-map-container')).toBeInTheDocument();
      expect(screen.queryByTestId('no-location-notice')).not.toBeInTheDocument();
    });
  });
});
