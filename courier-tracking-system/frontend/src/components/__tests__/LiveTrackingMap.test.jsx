import React from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import LiveTrackingMap from '../LiveTrackingMap';

describe('LiveTrackingMap Component', () => {
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
});
