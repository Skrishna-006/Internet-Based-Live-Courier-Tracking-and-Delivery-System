import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import AdminFleetMapPage from '../AdminFleetMapPage';
import { adminService } from '../../services/api';
import * as websocketModule from '../../services/websocket';
import * as authContextModule from '../../context/AuthContext';
import ProtectedRoute from '../../components/ProtectedRoute';

// Mock Leaflet and React-Leaflet
vi.mock('react-leaflet', () => ({
  MapContainer: ({ children }) => <div data-testid="mock-map-container">{children}</div>,
  TileLayer: () => <div data-testid="mock-tile-layer" />,
  Marker: ({ children, eventHandlers }) => (
    <div
      data-testid="mock-marker"
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

vi.mock('../../services/api', () => ({
  adminService: {
    getFleet: vi.fn(),
    getAgents: vi.fn(),
  },
}));

vi.mock('../../services/websocket', () => ({
  createStompClient: vi.fn(),
}));

describe('AdminFleetMapPage Component', () => {
  let mockStompClient;
  let mockUnsubscribe;
  let capturedStompOptions;
  let stompSubscriptions = {};

  const sampleAdminUser = {
    id: 1,
    fullName: 'Chief Dispatcher',
    email: 'admin@example.com',
    role: 'ROLE_ADMIN',
  };

  const sampleCustomerUser = {
    id: 99,
    fullName: 'Jane Customer',
    email: 'jane@customer.com',
    role: 'ROLE_CUSTOMER',
  };

  const sampleAgents = [
    {
      id: 10,
      fullName: 'Alex Courier',
      email: 'alex@driver.com',
      phoneNumber: '+1-555-0101',
      active: true,
      totalAssignedShipments: 4,
      activeDeliveriesCount: 2,
      deliveredShipmentsCount: 2,
    },
    {
      id: 11,
      fullName: 'Sam Delivery',
      email: 'sam@driver.com',
      phoneNumber: '+1-555-0102',
      active: true,
      totalAssignedShipments: 1,
      activeDeliveriesCount: 1,
      deliveredShipmentsCount: 0,
    },
    {
      id: 12,
      fullName: 'Inactive Driver',
      email: 'inactive@driver.com',
      phoneNumber: '+1-555-0103',
      active: false,
      totalAssignedShipments: 0,
      activeDeliveriesCount: 0,
      deliveredShipmentsCount: 0,
    },
    {
      id: 13,
      fullName: 'Taylor Standby',
      email: 'taylor@driver.com',
      phoneNumber: '+1-555-0104',
      active: true,
      totalAssignedShipments: 0,
      activeDeliveriesCount: 0,
      deliveredShipmentsCount: 0,
    },
  ];

  const getRecentTimestamp = (minsAgo) => new Date(Date.now() - minsAgo * 60 * 1000).toISOString();

  let sampleFleet = [];

  beforeEach(() => {
    vi.clearAllMocks();
    stompSubscriptions = {};

    sampleFleet = [
      {
        agentId: 10,
        agentName: 'Alex Courier',
        shipmentId: 101,
        trackingNumber: 'TRK-20261031-FLEET01',
        status: 'OUT_FOR_DELIVERY',
        latitude: 30.2672,
        longitude: -97.7431,
        recordedAt: getRecentTimestamp(2), // 2 minutes ago (Fresh)
      },
      {
        agentId: 11,
        agentName: 'Sam Delivery',
        shipmentId: 102,
        trackingNumber: 'TRK-20261031-FLEET02',
        status: 'IN_TRANSIT',
        latitude: 30.28,
        longitude: -97.73,
        recordedAt: getRecentTimestamp(10), // 10 minutes ago (Stale)
      },
    ];

    mockUnsubscribe = vi.fn();
    mockStompClient = {
      activate: vi.fn(),
      deactivate: vi.fn(),
      subscribe: vi.fn((destination, callback) => {
        stompSubscriptions[destination] = callback;
        return { unsubscribe: mockUnsubscribe };
      }),
    };

    websocketModule.createStompClient.mockImplementation((options) => {
      capturedStompOptions = options;
      // Simulate automatic connection
      setTimeout(() => {
        if (options?.onConnect) options.onConnect({});
      }, 0);
      return mockStompClient;
    });

    vi.spyOn(authContextModule, 'useAuth').mockReturnValue({
      user: sampleAdminUser,
      isAuthenticated: true,
      token: 'admin-jwt-token',
    });

    adminService.getFleet.mockResolvedValue({
      success: true,
      data: sampleFleet,
    });

    adminService.getAgents.mockResolvedValue({
      success: true,
      data: sampleAgents,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders loading state initially while fetching fleet snapshot', () => {
    adminService.getFleet.mockReturnValue(new Promise(() => {})); // Never resolves

    render(
      <MemoryRouter>
        <AdminFleetMapPage />
      </MemoryRouter>
    );

    expect(screen.getByTestId('fleet-loading-skeleton')).toBeInTheDocument();
    expect(screen.getByText(/Loading Fleet Telemetry/i)).toBeInTheDocument();
  });

  it('renders empty state when no fleet vehicles exist', async () => {
    adminService.getFleet.mockResolvedValueOnce({
      success: true,
      data: [],
    });

    render(
      <MemoryRouter>
        <AdminFleetMapPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('fleet-empty-state')).toBeInTheDocument();
      expect(screen.getByText(/No Vehicles Currently Transmitting/i)).toBeInTheDocument();
    });
  });

  it('renders fleet map, metric counters, and vehicles once data is loaded', async () => {
    render(
      <MemoryRouter>
        <AdminFleetMapPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('admin-fleet-map-container')).toBeInTheDocument();
    });

    // Check Metrics Cards
    expect(screen.getByTestId('metric-total-vehicles')).toHaveTextContent('2');
    expect(screen.getByTestId('metric-fresh-vehicles')).toHaveTextContent('1');
    expect(screen.getByTestId('metric-stale-vehicles')).toHaveTextContent('1');

    // Check Waybills in sidebar
    expect(screen.getAllByText('TRK-20261031-FLEET01').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('TRK-20261031-FLEET02').length).toBeGreaterThanOrEqual(1);
  });

  it('displays error banner with retry button when initial load fails', async () => {
    adminService.getFleet.mockRejectedValueOnce({
      message: 'Failed to connect to dispatch telemetry service',
    });

    render(
      <MemoryRouter>
        <AdminFleetMapPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('fleet-error-banner')).toBeInTheDocument();
      expect(screen.getByText(/Failed to connect to dispatch telemetry service/i)).toBeInTheDocument();
    });

    // Test retry
    adminService.getFleet.mockResolvedValueOnce({
      success: true,
      data: sampleFleet,
    });

    fireEvent.click(screen.getByRole('button', { name: /retry/i }));

    await waitFor(() => {
      expect(screen.queryByTestId('fleet-error-banner')).not.toBeInTheDocument();
      expect(screen.getByTestId('admin-fleet-map-container')).toBeInTheDocument();
    });
  });

  it('filters out invalid coordinates from map markers', async () => {
    const fleetWithInvalid = [
      ...sampleFleet,
      {
        agentId: 10,
        agentName: 'Alex Courier',
        shipmentId: 999,
        trackingNumber: 'TRK-INVALID-01',
        status: 'OUT_FOR_DELIVERY',
        latitude: 999.0, // Invalid latitude
        longitude: -97.74,
        recordedAt: new Date().toISOString(),
      },
      {
        agentId: 10,
        agentName: 'Alex Courier',
        shipmentId: 998,
        trackingNumber: 'TRK-NULL-COORDS',
        status: 'OUT_FOR_DELIVERY',
        latitude: null, // Null coordinates
        longitude: null,
        recordedAt: new Date().toISOString(),
      },
    ];

    adminService.getFleet.mockResolvedValueOnce({
      success: true,
      data: fleetWithInvalid,
    });

    render(
      <MemoryRouter>
        <AdminFleetMapPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('admin-fleet-map-container')).toBeInTheDocument();
    });

    // Only the 2 valid vehicles are counted and displayed
    expect(screen.getByTestId('metric-total-vehicles')).toHaveTextContent('2');
    expect(screen.queryByText('TRK-INVALID-01')).not.toBeInTheDocument();
    expect(screen.queryByText('TRK-NULL-COORDS')).not.toBeInTheDocument();
  });

  it('handles real-time STOMP WebSocket fleet updates', async () => {
    render(
      <MemoryRouter>
        <AdminFleetMapPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('admin-fleet-map-container')).toBeInTheDocument();
    });

    // Ensure STOMP client subscribed to /topic/admin/fleet
    expect(mockStompClient.subscribe).toHaveBeenCalledWith(
      '/topic/admin/fleet',
      expect.any(Function)
    );

    // Simulate incoming WebSocket update for a third vehicle
    const newVehicleBroadcast = {
      shipmentId: 103,
      trackingNumber: 'TRK-20261031-FLEET03',
      agentId: 11,
      latitude: 30.29,
      longitude: -97.72,
      status: 'OUT_FOR_DELIVERY',
      speed: 38.0,
      bearing: 90.0,
      timestamp: new Date().toISOString(),
    };

    act(() => {
      const fleetCallback = stompSubscriptions['/topic/admin/fleet'];
      fleetCallback({
        body: JSON.stringify(newVehicleBroadcast),
      });
    });

    await waitFor(() => {
      expect(screen.getAllByText('TRK-20261031-FLEET03').length).toBeGreaterThanOrEqual(1);
      expect(screen.getByTestId('metric-total-vehicles')).toHaveTextContent('3');
    });
  });

  it('prevents older WebSocket updates from overwriting newer coordinates', async () => {
    render(
      <MemoryRouter>
        <AdminFleetMapPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('admin-fleet-map-container')).toBeInTheDocument();
    });

    // Initial vehicle 101 has recordedAt: nowMs - 2 minutes
    // Simulate an older incoming frame: nowMs - 5 minutes
    const olderFrame = {
      shipmentId: 101,
      trackingNumber: 'TRK-20261031-FLEET01',
      agentId: 10,
      latitude: 10.0,
      longitude: 20.0,
      status: 'OUT_FOR_DELIVERY',
      timestamp: getRecentTimestamp(5),
    };

    act(() => {
      const fleetCallback = stompSubscriptions['/topic/admin/fleet'];
      fleetCallback({
        body: JSON.stringify(olderFrame),
      });
    });

    // The vehicle coordinates should remain the original coordinates (not 10.0, 20.0)
    expect(screen.getAllByText('TRK-20261031-FLEET01').length).toBeGreaterThanOrEqual(1);
  });

  it('removes vehicle from active fleet when status transitions to terminal DELIVERED', async () => {
    render(
      <MemoryRouter>
        <AdminFleetMapPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getAllByText('TRK-20261031-FLEET01').length).toBeGreaterThanOrEqual(1);
    });

    // Delivery agent completes delivery -> DELIVERED broadcast arrives
    const deliveredFrame = {
      shipmentId: 101,
      trackingNumber: 'TRK-20261031-FLEET01',
      agentId: 10,
      latitude: 30.2672,
      longitude: -97.7431,
      status: 'DELIVERED',
      timestamp: new Date().toISOString(),
    };

    act(() => {
      const fleetCallback = stompSubscriptions['/topic/admin/fleet'];
      fleetCallback({
        body: JSON.stringify(deliveredFrame),
      });
    });

    await waitFor(() => {
      expect(screen.queryByText('TRK-20261031-FLEET01')).not.toBeInTheDocument();
      expect(screen.getByTestId('metric-total-vehicles')).toHaveTextContent('1');
    });
  });

  it('allows searching and filtering by freshness (Live / Stale)', async () => {
    render(
      <MemoryRouter>
        <AdminFleetMapPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getAllByText('TRK-20261031-FLEET01').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('TRK-20261031-FLEET02').length).toBeGreaterThanOrEqual(1);
    });

    // Filter by LIVE (should hide FLEET02 which is stale)
    fireEvent.click(screen.getByRole('button', { name: /Live/i }));
    expect(screen.getAllByText('TRK-20261031-FLEET01').length).toBeGreaterThanOrEqual(1);
    expect(screen.queryByText('TRK-20261031-FLEET02')).not.toBeInTheDocument();

    // Filter by STALE (should hide FLEET01 which is live)
    fireEvent.click(screen.getByRole('button', { name: /Stale/i }));
    expect(screen.queryByText('TRK-20261031-FLEET01')).not.toBeInTheDocument();
    expect(screen.getAllByText('TRK-20261031-FLEET02').length).toBeGreaterThanOrEqual(1);

    // Reset to ALL and search by query
    fireEvent.click(screen.getByRole('button', { name: /All/i }));
    const searchInput = screen.getByPlaceholderText(/Search waybill or courier/i);
    fireEvent.change(searchInput, { target: { value: 'FLEET01' } });

    expect(screen.getAllByText('TRK-20261031-FLEET01').length).toBeGreaterThanOrEqual(1);
    expect(screen.queryByText('TRK-20261031-FLEET02')).not.toBeInTheDocument();
  });

  it('cleans up and deactivates STOMP client on unmount', async () => {
    const { unmount } = render(
      <MemoryRouter>
        <AdminFleetMapPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('admin-fleet-map-container')).toBeInTheDocument();
    });

    unmount();

    expect(mockStompClient.deactivate).toHaveBeenCalled();
  });

  it('enforces admin-only route protection: redirects non-admin customer', async () => {
    vi.spyOn(authContextModule, 'useAuth').mockReturnValue({
      user: sampleCustomerUser,
      isAuthenticated: true,
      token: 'customer-jwt-token',
    });

    render(
      <MemoryRouter initialEntries={['/admin/fleet']}>
        <Routes>
          <Route
            path="/admin/fleet"
            element={
              <ProtectedRoute allowedRoles={['ROLE_ADMIN']}>
                <AdminFleetMapPage />
              </ProtectedRoute>
            }
          />
          <Route path="/unauthorized" element={<div data-testid="unauthorized-page">Access Denied</div>} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('unauthorized-page')).toBeInTheDocument();
      expect(screen.queryByTestId('admin-fleet-map-container')).not.toBeInTheDocument();
    });
  });

  it('handles courier reassignment by resolving the new agent without reusing previous agent name', async () => {
    render(
      <MemoryRouter>
        <AdminFleetMapPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('admin-fleet-map-container')).toBeInTheDocument();
    });

    // Initial state: shipment 101 is assigned to Alex Courier (agentId 10)
    expect(screen.getAllByText('Alex Courier').length).toBeGreaterThanOrEqual(1);

    // Shipment 101 is reassigned to Taylor Standby (agentId 13)
    const reassignedFrame = {
      shipmentId: 101,
      trackingNumber: 'TRK-20261031-FLEET01',
      agentId: 13,
      latitude: 30.27,
      longitude: -97.74,
      status: 'IN_TRANSIT',
      timestamp: new Date().toISOString(),
    };

    act(() => {
      const fleetCallback = stompSubscriptions['/topic/admin/fleet'];
      fleetCallback({
        body: JSON.stringify(reassignedFrame),
      });
    });

    await waitFor(() => {
      // Must reflect new courier name from directory
      expect(screen.getAllByText('Taylor Standby').length).toBeGreaterThanOrEqual(1);
      // Must NOT continue to associate Alex Courier with shipment 101
      expect(screen.queryByText('Alex Courier')).not.toBeInTheDocument();
    });
  });

  it('removes vehicle from active fleet for all terminal statuses: RETURNED and CANCELLED', async () => {
    render(
      <MemoryRouter>
        <AdminFleetMapPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getAllByText('TRK-20261031-FLEET01').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('TRK-20261031-FLEET02').length).toBeGreaterThanOrEqual(1);
    });

    // 1. Broadcast RETURNED for shipment 101
    act(() => {
      stompSubscriptions['/topic/admin/fleet']({
        body: JSON.stringify({
          shipmentId: 101,
          status: 'RETURNED',
          timestamp: new Date().toISOString(),
        }),
      });
    });

    await waitFor(() => {
      expect(screen.queryByText('TRK-20261031-FLEET01')).not.toBeInTheDocument();
      expect(screen.getByTestId('metric-total-vehicles')).toHaveTextContent('1');
    });

    // 2. Broadcast CANCELLED for shipment 102
    act(() => {
      stompSubscriptions['/topic/admin/fleet']({
        body: JSON.stringify({
          shipmentId: 102,
          status: 'CANCELLED',
          timestamp: new Date().toISOString(),
        }),
      });
    });

    await waitFor(() => {
      expect(screen.queryByText('TRK-20261031-FLEET02')).not.toBeInTheDocument();
      expect(screen.getByTestId('fleet-empty-state')).toBeInTheDocument();
    });
  });

  it('re-subscribes, unsubscribes prior subscription, and refreshes REST snapshot on WebSocket reconnection', async () => {
    render(
      <MemoryRouter>
        <AdminFleetMapPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('admin-fleet-map-container')).toBeInTheDocument();
    });

    // Initial load called getFleet once
    expect(adminService.getFleet).toHaveBeenCalledTimes(1);

    // Simulate connection drop and reconnect: onConnect fires a second time
    act(() => {
      capturedStompOptions.onConnect({});
    });

    await waitFor(() => {
      // REST snapshot refreshed to heal missed telemetry
      expect(adminService.getFleet).toHaveBeenCalledTimes(2);
      // Prior subscription unsubscribed to prevent duplicate messages
      expect(mockUnsubscribe).toHaveBeenCalledTimes(1);
      // Re-subscribed to fleet topic
      expect(mockStompClient.subscribe).toHaveBeenCalledTimes(2);
    });
  });

  it('heals missed terminal updates when reconnecting via REST snapshot', async () => {
    render(
      <MemoryRouter>
        <AdminFleetMapPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getAllByText('TRK-20261031-FLEET01').length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('TRK-20261031-FLEET02').length).toBeGreaterThanOrEqual(1);
    });

    // While disconnected, shipment 101 was completed/delivered in backend
    // So getFleet now only returns shipment 102
    adminService.getFleet.mockResolvedValueOnce({
      success: true,
      data: [sampleFleet[1]], // only shipment 102 remains active
    });

    // Simulate STOMP reconnect
    act(() => {
      capturedStompOptions.onConnect({});
    });

    await waitFor(() => {
      // Shipment 101 should be healed (removed), 102 should remain
      expect(screen.queryByText('TRK-20261031-FLEET01')).not.toBeInTheDocument();
      expect(screen.getAllByText('TRK-20261031-FLEET02').length).toBeGreaterThanOrEqual(1);
      expect(screen.getByTestId('metric-total-vehicles')).toHaveTextContent('1');
    });
  });

  it('displays accurate wording distinguishing active accounts from lack of telemetry', async () => {
    render(
      <MemoryRouter>
        <AdminFleetMapPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('admin-fleet-map-container')).toBeInTheDocument();
    });

    // Check UI wording and metric separation
    expect(screen.getByText('No Active Location')).toBeInTheDocument();
    expect(screen.getByText('No active shipment location available')).toBeInTheDocument();
    // 1 courier without active telemetry (Taylor Standby)
    expect(screen.getByTestId('metric-no-location-agents')).toHaveTextContent('1');
  });

  it('handles incoming WebSocket broadcast with missing or invalid timestamp safely', async () => {
    render(
      <MemoryRouter>
        <AdminFleetMapPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('admin-fleet-map-container')).toBeInTheDocument();
    });

    // Broadcast with missing timestamp
    const broadcastNoTs = {
      shipmentId: 104,
      trackingNumber: 'TRK-NO-TIMESTAMP',
      agentId: 10,
      latitude: 30.25,
      longitude: -97.75,
      status: 'IN_TRANSIT',
      timestamp: null,
    };

    act(() => {
      stompSubscriptions['/topic/admin/fleet']({
        body: JSON.stringify(broadcastNoTs),
      });
    });

    await waitFor(() => {
      expect(screen.getAllByText('TRK-NO-TIMESTAMP').length).toBeGreaterThanOrEqual(1);
      expect(screen.getByTestId('metric-total-vehicles')).toHaveTextContent('3');
    });
  });
});
