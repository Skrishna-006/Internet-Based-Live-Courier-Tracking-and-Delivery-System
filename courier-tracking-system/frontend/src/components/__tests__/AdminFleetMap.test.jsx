import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import AdminFleetMap, { isValidCoordinate, isLocationStale, STALE_THRESHOLD_MS } from '../AdminFleetMap';

vi.mock('react-leaflet', () => ({
  MapContainer: ({ children }) => <div data-testid="mock-map-container">{children}</div>,
  TileLayer: () => <div data-testid="mock-tile-layer" />,
  Marker: ({ children, eventHandlers }) => (
    <div
      data-testid="mock-vehicle-marker"
      onClick={eventHandlers?.click}
    >
      {children}
    </div>
  ),
  Popup: ({ children }) => <div data-testid="mock-popup">{children}</div>,
  useMap: () => ({
    flyTo: vi.fn(),
    setView: vi.fn(),
    fitBounds: vi.fn(),
  }),
}));

describe('AdminFleetMap Component & Helper Utilities', () => {
  describe('isValidCoordinate helper', () => {
    it('accepts valid geographical latitude and longitude', () => {
      expect(isValidCoordinate(30.2672, -97.7431)).toBe(true);
      expect(isValidCoordinate('30.2672', '-97.7431')).toBe(true);
      expect(isValidCoordinate(0, 0)).toBe(true);
      expect(isValidCoordinate(90, 180)).toBe(true);
      expect(isValidCoordinate(-90, -180)).toBe(true);
    });

    it('rejects out of range latitudes and longitudes', () => {
      expect(isValidCoordinate(91, 0)).toBe(false);
      expect(isValidCoordinate(-91, 0)).toBe(false);
      expect(isValidCoordinate(0, 181)).toBe(false);
      expect(isValidCoordinate(0, -181)).toBe(false);
    });

    it('rejects null, undefined, boolean, empty strings, and non-numeric values', () => {
      expect(isValidCoordinate(null, -97.74)).toBe(false);
      expect(isValidCoordinate(30.26, null)).toBe(false);
      expect(isValidCoordinate(undefined, undefined)).toBe(false);
      expect(isValidCoordinate('not-a-number', 10)).toBe(false);
      expect(isValidCoordinate(true, -97.74)).toBe(false);
      expect(isValidCoordinate(30.26, false)).toBe(false);
      expect(isValidCoordinate('', '')).toBe(false);
      expect(isValidCoordinate('   ', -97.74)).toBe(false);
    });
  });

  describe('isLocationStale helper', () => {
    const baseNow = 1730376000000;

    it('identifies fresh locations within 5 minutes', () => {
      const freshTimestamp = new Date(baseNow - 2 * 60 * 1000).toISOString(); // 2m ago
      expect(isLocationStale(freshTimestamp, baseNow)).toBe(false);

      const justNow = new Date(baseNow - 30 * 1000).toISOString(); // 30s ago
      expect(isLocationStale(justNow, baseNow)).toBe(false);
    });

    it('identifies stale locations exceeding 5 minutes threshold', () => {
      const staleTimestamp = new Date(baseNow - (STALE_THRESHOLD_MS + 1000)).toISOString(); // 5m 1s ago
      expect(isLocationStale(staleTimestamp, baseNow)).toBe(true);

      const veryStale = new Date(baseNow - 60 * 60 * 1000).toISOString(); // 1 hr ago
      expect(isLocationStale(veryStale, baseNow)).toBe(true);
    });

    it('treats missing or invalid timestamps as stale', () => {
      expect(isLocationStale(null, baseNow)).toBe(true);
      expect(isLocationStale(undefined, baseNow)).toBe(true);
      expect(isLocationStale('invalid-date', baseNow)).toBe(true);
    });
  });

  describe('Component Rendering', () => {
    const sampleVehicles = [
      {
        shipmentId: 101,
        trackingNumber: 'TRK-20261031-FLEET01',
        agentId: 10,
        agentName: 'Driver Alice',
        status: 'OUT_FOR_DELIVERY',
        latitude: 30.2672,
        longitude: -97.7431,
        speed: 45.0,
        bearing: 180,
        recordedAt: new Date(Date.now() - 60 * 1000).toISOString(),
      },
      {
        shipmentId: 102,
        trackingNumber: 'TRK-20261031-FLEET02',
        agentId: 11,
        agentName: 'Driver Bob',
        status: 'IN_TRANSIT',
        latitude: 30.28,
        longitude: -97.73,
        speed: null,
        bearing: null,
        recordedAt: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
      },
    ];

    it('renders markers for each valid vehicle', () => {
      render(<AdminFleetMap vehicles={sampleVehicles} />);

      const markers = screen.getAllByTestId('mock-vehicle-marker');
      expect(markers).toHaveLength(2);
      expect(screen.getByText('2 Vehicles Active')).toBeInTheDocument();
    });

    it('calls onSelectVehicle when a marker is clicked', () => {
      const handleSelect = vi.fn();
      render(<AdminFleetMap vehicles={sampleVehicles} onSelectVehicle={handleSelect} />);

      const markers = screen.getAllByTestId('mock-vehicle-marker');
      fireEvent.click(markers[0]);

      expect(handleSelect).toHaveBeenCalledWith(sampleVehicles[0]);
    });

    it('renders singular vehicle label when only 1 vehicle is active', () => {
      render(<AdminFleetMap vehicles={[sampleVehicles[0]]} />);
      expect(screen.getByText('1 Vehicle Active')).toBeInTheDocument();
    });
  });
});
