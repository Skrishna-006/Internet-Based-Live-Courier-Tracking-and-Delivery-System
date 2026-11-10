import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import BookingPage from '../pages/BookingPage';
import CustomerDashboardPage from '../pages/CustomerDashboardPage';
import DriverDeliveryPage from '../pages/DriverDeliveryPage';
import AdminDashboardPage from '../pages/AdminDashboardPage';
import PublicTrackingPage from '../pages/PublicTrackingPage';
import * as authContextModule from '../context/AuthContext';
import { shipmentService, agentService, trackingService, adminService } from '../services/api';

// Mock API and admin services
vi.mock('../services/api', () => ({
  shipmentService: {
    getPriceQuote: vi.fn(),
    createShipment: vi.fn(),
    getCustomerShipments: vi.fn(),
    getShipmentById: vi.fn(),
    sendLiveLocation: vi.fn(),
    generateDeliveryOtp: vi.fn(),
    verifyDeliveryOtp: vi.fn(),
    getCustomerDeliveryPin: vi.fn(),
    updateStatus: vi.fn(),
  },
  agentService: {
    getMyAssignments: vi.fn(),
    getMySummary: vi.fn(),
    getAssignmentById: vi.fn(),
  },
  trackingService: {
    getPublicTracking: vi.fn(),
    getPublicLiveLocation: vi.fn(),
  },
  adminService: {
    getOverview: vi.fn(),
    getShipments: vi.fn(),
    getShipmentById: vi.fn(),
    getShipmentEvents: vi.fn(),
    getAgents: vi.fn(),
    getFleet: vi.fn(),
  },
}));

vi.mock('../components/LiveTrackingMap', () => ({
  default: ({ trackingNumber, location, connectionStatus }) => (
    <div data-testid="mock-live-tracking-map">
      <span>Tracking: {trackingNumber}</span>
      <span>Status: {connectionStatus}</span>
      {location && (
        <span data-testid="mock-map-coords">
          {location.latitude}, {location.longitude}
        </span>
      )}
    </div>
  ),
}));

describe('Frontend End-to-End Delivery Workflow Integration', () => {
  const mockCustomer = {
    id: 1,
    fullName: 'Alice Customer',
    email: 'alice@example.com',
    role: 'ROLE_CUSTOMER',
    phoneNumber: '+1-555-0101',
  };

  const mockAgent = {
    id: 42,
    fullName: 'Bob Driver',
    email: 'bob.driver@example.com',
    role: 'ROLE_DELIVERY_AGENT',
    phoneNumber: '+1-555-0102',
  };

  const mockAdmin = {
    id: 99,
    fullName: 'Dispatcher Dan',
    email: 'dan.admin@example.com',
    role: 'ROLE_ADMIN',
    phoneNumber: '+1-555-0103',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // =========================================================================
  // SCENARIO A & B: Customer Booking & Authoritative Price Quote
  // =========================================================================
  it('Scenario B: Customer calculates authoritative price quote and books shipment', async () => {
    vi.spyOn(authContextModule, 'useAuth').mockReturnValue({
      user: mockCustomer,
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
      loading: false,
    });

    shipmentService.getPriceQuote.mockResolvedValue({
      success: true,
      data: {
        weightKg: 3.5,
        price: 27.5,
        currency: 'USD',
        baseRate: 10.0,
        perKgRate: 5.0,
      },
    });

    shipmentService.createShipment.mockResolvedValue({
      success: true,
      data: {
        id: 501,
        trackingNumber: 'TRK-20261101-E2E01',
        status: 'CREATED',
        price: 27.5,
        currency: 'USD',
      },
    });

    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <BookingPage />
      </MemoryRouter>
    );

    // Verify sender pre-fill
    expect(screen.getByTestId('input-sender-name')).toHaveValue('Alice Customer');

    // Fill recipient and consignment details
    fireEvent.change(screen.getByTestId('input-sender-address'), { target: { value: '100 Sender Way, Austin, TX' } });
    fireEvent.change(screen.getByTestId('input-recipient-name'), { target: { value: 'Charlie Recipient' } });
    fireEvent.change(screen.getByTestId('input-recipient-phone'), { target: { value: '+1-555-0999' } });
    fireEvent.change(screen.getByTestId('input-recipient-address'), { target: { value: '200 Delivery Blvd, Austin, TX' } });

    // Enter weight to trigger debounced quote
    fireEvent.change(screen.getByTestId('input-package-weight'), { target: { value: '3.5' } });

    await waitFor(() => {
      expect(shipmentService.getPriceQuote).toHaveBeenCalledWith(3.5);
    });

    await waitFor(() => {
      expect(screen.getByText('$27.50')).toBeInTheDocument();
    });

    // Submit booking
    fireEvent.click(screen.getByTestId('btn-submit-booking'));

    await waitFor(() => {
      expect(shipmentService.createShipment).toHaveBeenCalledWith(
        expect.objectContaining({
          senderName: 'Alice Customer',
          recipientName: 'Charlie Recipient',
          weightKg: 3.5,
        })
      );
    });
  });

  // =========================================================================
  // SCENARIO C: Admin Dashboard Overview & Shipment Queue Inspection
  // =========================================================================
  it('Scenario C: Admin retrieves overview metrics and filters unassigned consignments', async () => {
    vi.spyOn(authContextModule, 'useAuth').mockReturnValue({
      user: mockAdmin,
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
      loading: false,
    });

    adminService.getOverview.mockResolvedValue({
      success: true,
      data: {
        totalShipments: 12,
        activeShipments: 4,
        unassignedShipments: 2,
        outForDeliveryShipments: 1,
        deliveredShipments: 7,
      },
    });

    adminService.getShipments.mockResolvedValue({
      success: true,
      data: {
        content: [
          {
            id: 501,
            trackingNumber: 'TRK-20261101-E2E01',
            status: 'CREATED',
            senderName: 'Alice Customer',
            recipientName: 'Charlie Recipient',
            price: 27.5,
            currency: 'USD',
            assignedAgentId: null,
            assignedAgentName: null,
          },
        ],
        totalElements: 1,
        totalPages: 1,
        number: 0,
      },
    });

    adminService.getAgents.mockResolvedValue({
      success: true,
      data: [
        {
          id: 42,
          fullName: 'Bob Driver',
          email: 'bob.driver@example.com',
          activeDeliveriesCount: 0,
          deliveredShipmentsCount: 5,
        },
      ],
    });

    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AdminDashboardPage />
      </MemoryRouter>
    );

    // Verify overview cards rendered
    await waitFor(() => {
      expect(screen.getByTestId('overview-metrics-grid')).toBeInTheDocument();
    });
    expect(screen.getByTestId('metric-total-shipments')).toHaveTextContent('12');
    expect(screen.getByTestId('metric-unassigned-shipments')).toHaveTextContent('2');

    // Verify shipment listed
    await waitFor(() => {
      expect(screen.getByText('TRK-20261101-E2E01')).toBeInTheDocument();
    });

    // Check filter toggles
    const unassignedToggle = screen.getByTestId('unassigned-filter-toggle');
    fireEvent.click(unassignedToggle);

    await waitFor(() => {
      expect(adminService.getShipments).toHaveBeenCalledWith(
        expect.objectContaining({
          unassignedOnly: true,
        })
      );
    });
  });

  // =========================================================================
  // SCENARIO D & E: Delivery Agent Queue, FSM Transitions & GPS Telemetry
  // =========================================================================
  it('Scenario D & E: Delivery agent transitions status and streams live GPS telemetry', async () => {
    vi.spyOn(authContextModule, 'useAuth').mockReturnValue({
      user: mockAgent,
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
      loading: false,
    });

    const activeShipment = {
      id: 501,
      trackingNumber: 'TRK-20261101-E2E01',
      status: 'OUT_FOR_DELIVERY',
      recipientName: 'Charlie Recipient',
      recipientAddress: '200 Delivery Blvd, Austin, TX',
      recipientPhone: '+1-555-0999',
      weightKg: 3.5,
      estimatedPrice: 27.5,
    };

    agentService.getAssignmentById.mockResolvedValue({
      success: true,
      data: activeShipment,
    });

    shipmentService.sendLiveLocation.mockResolvedValue({
      success: true,
      data: {
        shipmentId: 501,
        trackingNumber: 'TRK-20261101-E2E01',
        latitude: 30.2672,
        longitude: -97.7431,
        recordedAt: new Date().toISOString(),
      },
    });

    // Deterministic mock for navigator.geolocation
    let watchSuccessCallback = null;
    const mockWatchId = 1234;
    navigator.geolocation = {
      watchPosition: vi.fn().mockImplementation((success) => {
        watchSuccessCallback = success;
        return mockWatchId;
      }),
      clearWatch: vi.fn(),
    };

    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={['/driver/delivery/501']}>
        <Routes>
          <Route path="/driver/delivery/:id" element={<DriverDeliveryPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('TRK-20261101-E2E01')).toBeInTheDocument();
    });

    // Start GPS tracking
    const startGpsBtn = screen.getByTestId('start-tracking-btn');
    fireEvent.click(startGpsBtn);

    expect(navigator.geolocation.watchPosition).toHaveBeenCalled();

    // Simulate browser GPS coordinate emission
    await act(async () => {
      watchSuccessCallback({
        coords: {
          latitude: 30.2672,
          longitude: -97.7431,
          accuracy: 12.0,
          speed: 10.5,
          heading: 90.0,
        },
        timestamp: Date.now(),
      });
    });

    await waitFor(() => {
      expect(shipmentService.sendLiveLocation).toHaveBeenCalledWith(
        501,
        expect.objectContaining({
          latitude: 30.2672,
          longitude: -97.7431,
          accuracy: 12.0,
        })
      );
    });
  });

  // =========================================================================
  // SCENARIO F: Delivery Agent Generates & Confirms OTP Delivery
  // =========================================================================
  it('Scenario F: Delivery agent requests OTP and confirms delivery handover', async () => {
    vi.spyOn(authContextModule, 'useAuth').mockReturnValue({
      user: mockAgent,
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
      loading: false,
    });

    const activeShipment = {
      id: 501,
      trackingNumber: 'TRK-20261101-E2E01',
      status: 'OUT_FOR_DELIVERY',
      recipientName: 'Charlie Recipient',
      recipientAddress: '200 Delivery Blvd, Austin, TX',
      recipientPhone: '+1-555-0999',
      weightKg: 3.5,
    };

    agentService.getAssignmentById.mockResolvedValue({
      success: true,
      data: activeShipment,
    });

    shipmentService.generateDeliveryOtp.mockResolvedValue({
      success: true,
      data: {
        shipmentId: 501,
        trackingNumber: 'TRK-20261101-E2E01',
        otp: '654321',
        expiresAt: new Date(Date.now() + 600000).toISOString(),
        message: 'Delivery OTP generated successfully and dispatched to recipient',
      },
    });

    shipmentService.verifyDeliveryOtp.mockResolvedValue({
      success: true,
      data: {
        shipmentId: 501,
        trackingNumber: 'TRK-20261101-E2E01',
        status: 'DELIVERED',
        proofType: 'OTP',
        recipientName: 'Charlie Recipient',
        deliveredAt: new Date().toISOString(),
      },
    });

    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={['/driver/delivery/501']}>
        <Routes>
          <Route path="/driver/delivery/:id" element={<DriverDeliveryPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('TRK-20261101-E2E01')).toBeInTheDocument();
    });

    // Generate OTP
    const genOtpBtn = screen.getByTestId('request-otp-btn');
    fireEvent.click(genOtpBtn);

    await waitFor(() => {
      expect(shipmentService.generateDeliveryOtp).toHaveBeenCalledWith(501);
    });

    // In test environment, simulated OTP is presented
    await waitFor(() => {
      expect(screen.getByTestId('dev-otp-code')).toHaveTextContent('654321');
    });

    // Enter OTP into input
    const otpInput = screen.getByTestId('otp-code-input');
    fireEvent.change(otpInput, { target: { value: '654321' } });

    // Confirm delivery
    const verifyBtn = screen.getByTestId('verify-otp-submit-btn');
    fireEvent.click(verifyBtn);

    await waitFor(() => {
      expect(shipmentService.verifyDeliveryOtp).toHaveBeenCalledWith(
        501,
        expect.objectContaining({
          otp: '654321',
          recipientName: 'Charlie Recipient',
        })
      );
    });
  });

  // =========================================================================
  // SCENARIO G: Public Tracking Timeline & Chronological Milestone Audit
  // =========================================================================
  it('Scenario G: Public tracking view renders parcel details and chronological audit milestones', async () => {
    trackingService.getPublicTracking.mockResolvedValue({
      success: true,
      data: {
        trackingNumber: 'TRK-20261101-E2E01',
        status: 'DELIVERED',
        estimatedDeliveryAt: new Date(Date.now() + 86400000).toISOString(),
        actualDeliveryAt: new Date().toISOString(),
        createdAt: new Date(Date.now() - 7200000).toISOString(),
        milestones: [
          { status: 'CREATED', locationName: 'Austin Hub', remarks: 'Booked', timestamp: '2026-11-01T10:00:00Z' },
          { status: 'ASSIGNED', locationName: 'Austin Hub', remarks: 'Assigned', timestamp: '2026-11-01T10:30:00Z' },
          { status: 'PICKED_UP', locationName: 'Austin Hub', remarks: 'Collected', timestamp: '2026-11-01T11:00:00Z' },
          { status: 'IN_TRANSIT', locationName: 'Corridor', remarks: 'In transit', timestamp: '2026-11-01T11:30:00Z' },
          { status: 'OUT_FOR_DELIVERY', locationName: 'Austin Central', remarks: 'On route', timestamp: '2026-11-01T12:00:00Z' },
          { status: 'DELIVERED', locationName: 'Austin Congress', remarks: 'Handover complete', timestamp: '2026-11-01T12:30:00Z' },
        ],
      },
    });

    trackingService.getPublicLiveLocation.mockResolvedValue({
      success: true,
      data: {
        trackingNumber: 'TRK-20261101-E2E01',
        latitude: 30.2672,
        longitude: -97.7431,
        fresh: false,
        status: 'DELIVERED',
      },
    });

    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={['/track/TRK-20261101-E2E01']}>
        <Routes>
          <Route path="/track/:trackingNumber" element={<PublicTrackingPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('TRK-20261101-E2E01')).toBeInTheDocument();
    });

    // Verify terminal status badge and milestones
    expect(screen.getAllByText('DELIVERED').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('Handover complete')).toBeInTheDocument();
    expect(screen.getByText('Austin Congress')).toBeInTheDocument();
  });
});
