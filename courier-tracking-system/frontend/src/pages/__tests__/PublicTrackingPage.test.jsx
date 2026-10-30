import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import PublicTrackingPage from '../PublicTrackingPage';
import * as hookModule from '../../hooks/useLiveTracking';

vi.mock('../../hooks/useLiveTracking');

// Mock LiveTrackingMap to keep unit test focused on page container logic
vi.mock('../../components/LiveTrackingMap', () => ({
  default: (props) => (
    <div data-testid="mock-live-tracking-map">
      <span>Map for {props.trackingNumber}</span>
      <span>Status: {props.connectionStatus}</span>
    </div>
  ),
}));

describe('PublicTrackingPage Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders prompt to enter tracking number when none is provided in URL', () => {
    vi.spyOn(hookModule, 'useLiveTracking').mockReturnValue({
      shipment: null,
      location: null,
      connectionStatus: 'offline',
      isLoading: false,
      error: null,
      lastUpdatedAt: null,
      refresh: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/track']}>
        <Routes>
          <Route path="/track" element={<PublicTrackingPage />} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText(/Ready to Track/i)).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText(/Enter Tracking Number/i)
    ).toBeInTheDocument();
  });

  it('renders loading state when fetching shipment data', () => {
    vi.spyOn(hookModule, 'useLiveTracking').mockReturnValue({
      shipment: null,
      location: null,
      connectionStatus: 'offline',
      isLoading: true,
      error: null,
      lastUpdatedAt: null,
      refresh: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/track/TRK-TEST']}>
        <Routes>
          <Route path="/track/:trackingNumber" element={<PublicTrackingPage />} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText(/Querying tracking records/i)).toBeInTheDocument();
  });

  it('renders error notice when shipment is not found', () => {
    vi.spyOn(hookModule, 'useLiveTracking').mockReturnValue({
      shipment: null,
      location: null,
      connectionStatus: 'offline',
      isLoading: false,
      error: 'Consignment not found for tracking number TRK-INVALID',
      lastUpdatedAt: null,
      refresh: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/track/TRK-INVALID']}>
        <Routes>
          <Route path="/track/:trackingNumber" element={<PublicTrackingPage />} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText(/Shipment Not Found/i)).toBeInTheDocument();
    expect(
      screen.getByText(/Consignment not found for tracking number TRK-INVALID/i)
    ).toBeInTheDocument();
  });

  it('renders shipment summary, live map, and milestones when data is loaded', () => {
    const mockShipment = {
      trackingNumber: 'TRK-20261026-LIVE',
      status: 'OUT_FOR_DELIVERY',
      estimatedDeliveryAt: '2026-10-26T18:00:00Z',
      actualDeliveryAt: null,
      createdAt: '2026-10-26T08:00:00Z',
      milestones: [
        {
          status: 'BOOKED',
          location: 'Hub North',
          description: 'Package booked',
          timestamp: '2026-10-26T08:00:00Z',
        },
        {
          status: 'OUT_FOR_DELIVERY',
          location: 'City Metro',
          description: 'Package out for delivery with agent',
          timestamp: '2026-10-26T11:00:00Z',
        },
      ],
    };

    vi.spyOn(hookModule, 'useLiveTracking').mockReturnValue({
      shipment: mockShipment,
      location: {
        latitude: 12.9716,
        longitude: 77.5946,
        status: 'OUT_FOR_DELIVERY',
        fresh: true,
      },
      connectionStatus: 'live',
      isLoading: false,
      error: null,
      lastUpdatedAt: new Date(),
      refresh: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/track/TRK-20261026-LIVE']}>
        <Routes>
          <Route path="/track/:trackingNumber" element={<PublicTrackingPage />} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('TRK-20261026-LIVE')).toBeInTheDocument();
    expect(screen.getByTestId('mock-live-tracking-map')).toBeInTheDocument();
    expect(screen.getByText(/Consignment Journey Milestones/i)).toBeInTheDocument();
    expect(screen.getByText('Package out for delivery with agent')).toBeInTheDocument();
    expect(screen.getByText(/Privacy & Security Shield Active/i)).toBeInTheDocument();
  });
});
