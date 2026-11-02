import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import DriverDeliveryPage from '../DriverDeliveryPage';
import * as authContextModule from '../../context/AuthContext';
import { agentService, shipmentService } from '../../services/api';

vi.mock('../../services/api', () => ({
  agentService: {
    getAssignmentById: vi.fn(),
  },
  shipmentService: {
    sendLiveLocation: vi.fn(),
    generateDeliveryOtp: vi.fn(),
    verifyDeliveryOtp: vi.fn(),
    updateStatus: vi.fn(),
  },
}));

vi.mock('../../components/LiveTrackingMap', () => ({
  default: ({ trackingNumber, location, connectionStatus }) => (
    <div data-testid="mock-live-tracking-map">
      <span>Map: {trackingNumber}</span>
      <span>Status: {connectionStatus}</span>
      {location && (
        <span data-testid="mock-map-coords">
          {location.latitude}, {location.longitude}
        </span>
      )}
    </div>
  ),
}));

describe('DriverDeliveryPage Component', () => {
  const mockAgent = {
    id: 42,
    fullName: 'Alex Delivery Agent',
    email: 'alex.driver@example.com',
    role: 'ROLE_DELIVERY_AGENT',
  };

  const sampleShipment = {
    id: 101,
    trackingNumber: 'TRK-20261031-DELIV01',
    status: 'OUT_FOR_DELIVERY',
    recipientName: 'Robert Vance',
    recipientAddress: '100 Industrial Parkway, Scranton, PA',
    recipientPhone: '+1-555-0199',
    originAddress: 'Scranton Central Depot',
    weightKg: 4.2,
    estimatedPrice: 32.5,
  };

  let watchPositionCallback = null;
  let watchPositionErrorCallback = null;
  const mockWatchId = 999;
  let mockClearWatch = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    watchPositionCallback = null;
    watchPositionErrorCallback = null;
    mockClearWatch = vi.fn();

    vi.spyOn(authContextModule, 'useAuth').mockReturnValue({
      user: mockAgent,
      token: 'mock-agent-jwt',
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
      loading: false,
    });

    agentService.getAssignmentById.mockResolvedValue({
      success: true,
      data: sampleShipment,
    });

    // Mock navigator.geolocation
    const mockGeolocation = {
      watchPosition: vi.fn((success, error) => {
        watchPositionCallback = success;
        watchPositionErrorCallback = error;
        return mockWatchId;
      }),
      clearWatch: mockClearWatch,
    };
    Object.defineProperty(global.navigator, 'geolocation', {
      value: mockGeolocation,
      writable: true,
      configurable: true,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  const renderComponent = (shipmentId = 101) => {
    return render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={[`/driver/delivery/${shipmentId}`]}>
        <Routes>
          <Route path="/driver/delivery/:id" element={<DriverDeliveryPage />} />
        </Routes>
      </MemoryRouter>
    );
  };

  it('renders shipment header, recipient details, and initial stopped GPS status', async () => {
    renderComponent();

    await waitFor(() => {
      expect(screen.getByTestId('delivery-mode-title')).toHaveTextContent('Active Delivery Run Mode');
      expect(screen.getByText('TRK-20261031-DELIV01')).toBeInTheDocument();
      expect(screen.getByTestId('shipment-status-badge')).toHaveTextContent('OUT FOR DELIVERY');
      expect(screen.getByTestId('recipient-name-info')).toHaveTextContent('Robert Vance');
      expect(screen.getByTestId('recipient-address-info')).toHaveTextContent('100 Industrial Parkway, Scranton, PA');
    });

    expect(screen.getByTestId('gps-broadcast-status')).toHaveTextContent('Stopped');
    expect(screen.getByTestId('gps-permission-status')).toHaveTextContent('prompt');
    expect(screen.getByTestId('start-tracking-btn')).toBeInTheDocument();
  });

  it('starts live GPS tracking, accepts genuine coordinates, and transmits to backend', async () => {
    shipmentService.sendLiveLocation.mockResolvedValueOnce({
      success: true,
      data: {
        shipmentId: 101,
        trackingNumber: 'TRK-20261031-DELIV01',
        latitude: 41.4089,
        longitude: -75.6624,
        recordedAt: '2026-10-31T10:00:00Z',
      },
    });

    renderComponent();

    await waitFor(() => {
      expect(screen.getByTestId('start-tracking-btn')).toBeInTheDocument();
    });

    // Explicitly click "Start Live Tracking"
    fireEvent.click(screen.getByTestId('start-tracking-btn'));

    expect(navigator.geolocation.watchPosition).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId('stop-tracking-btn')).toBeInTheDocument();

    // Simulate browser returning high-accuracy GPS fix
    act(() => {
      watchPositionCallback({
        coords: {
          latitude: 41.4089,
          longitude: -75.6624,
          accuracy: 12.0, // within 30m limit
          speed: 8.5,     // 8.5 m/s = 30.6 km/h
          heading: 180,
        },
        timestamp: Date.now(),
      });
    });

    await waitFor(() => {
      expect(screen.getByTestId('gps-permission-status')).toHaveTextContent('granted');
      expect(screen.getByTestId('gps-broadcast-status')).toHaveTextContent('Active');
      expect(screen.getByTestId('gps-accuracy-indicator')).toHaveTextContent('±12m');
      expect(screen.getByTestId('transmission-status-indicator')).toHaveTextContent(/synced/i);
      expect(screen.getByTestId('current-coords-display')).toHaveTextContent('41.4089, -75.6624');
      expect(screen.getByTestId('current-coords-display')).toHaveTextContent('30.6 km/h');
    });

    expect(shipmentService.sendLiveLocation).toHaveBeenCalledWith(101, {
      latitude: 41.4089,
      longitude: -75.6624,
      accuracy: 12.0,
      speed: 30.6,
      bearing: 180,
      timestamp: expect.any(String),
    });
  });

  it('handles GPS permission denied and stops tracking with user guidance', async () => {
    renderComponent();

    await waitFor(() => {
      expect(screen.getByTestId('start-tracking-btn')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId('start-tracking-btn'));

    // Simulate browser permission denied error (code = 1)
    act(() => {
      watchPositionErrorCallback({
        code: 1,
        message: 'User denied Geolocation',
      });
    });

    await waitFor(() => {
      expect(screen.getByTestId('gps-permission-status')).toHaveTextContent('denied');
      expect(screen.getByTestId('gps-broadcast-status')).toHaveTextContent('Stopped');
      expect(screen.getByTestId('gps-error-alert')).toHaveTextContent(/Location access was denied/i);
    });

    expect(mockClearWatch).toHaveBeenCalledWith(mockWatchId);
    expect(screen.getByTestId('start-tracking-btn')).toBeInTheDocument();
  });

  it('handles geolocation unsupported browser scenario', async () => {
    // Delete geolocation from navigator
    Object.defineProperty(global.navigator, 'geolocation', {
      value: undefined,
      writable: true,
      configurable: true,
    });

    renderComponent();

    await waitFor(() => {
      expect(screen.getByTestId('start-tracking-btn')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId('start-tracking-btn'));

    await waitFor(() => {
      expect(screen.getByTestId('gps-permission-status')).toHaveTextContent('unsupported');
      expect(screen.getByTestId('gps-error-alert')).toHaveTextContent(/not supported by your current browser/i);
    });
  });

  it('filters out inaccurate coordinates (> 30m) without transmitting to backend', async () => {
    renderComponent();

    await waitFor(() => {
      expect(screen.getByTestId('start-tracking-btn')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId('start-tracking-btn'));

    // Emit position with accuracy = 45 meters (> 30m)
    act(() => {
      watchPositionCallback({
        coords: {
          latitude: 41.4089,
          longitude: -75.6624,
          accuracy: 45.0,
          speed: null,
          heading: null,
        },
        timestamp: Date.now(),
      });
    });

    await waitFor(() => {
      expect(screen.getByTestId('gps-error-alert')).toHaveTextContent(/exceeds the 30m maximum threshold/i);
      expect(screen.getByTestId('gps-accuracy-indicator')).toHaveTextContent('±45m');
    });

    // Must NOT call backend API with invalid accuracy
    expect(shipmentService.sendLiveLocation).not.toHaveBeenCalled();
  });

  it('clears geolocation watch on stop button click and on component unmount', async () => {
    const { unmount } = renderComponent();

    await waitFor(() => {
      expect(screen.getByTestId('start-tracking-btn')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId('start-tracking-btn'));
    expect(navigator.geolocation.watchPosition).toHaveBeenCalledTimes(1);

    // Stop tracking via button
    fireEvent.click(screen.getByTestId('stop-tracking-btn'));
    expect(mockClearWatch).toHaveBeenCalledWith(mockWatchId);
    expect(screen.getByTestId('gps-broadcast-status')).toHaveTextContent('Stopped');

    // Start again and test unmount cleanup
    fireEvent.click(screen.getByTestId('start-tracking-btn'));
    unmount();
    expect(mockClearWatch).toHaveBeenCalledTimes(2);
  });

  it('initiates delivery OTP generation, displays simulated dev OTP, and sets cooldown timer', async () => {
    shipmentService.generateDeliveryOtp.mockResolvedValueOnce({
      success: true,
      data: {
        shipmentId: 101,
        trackingNumber: 'TRK-20261031-DELIV01',
        otp: '849201',
        expiresAt: '2026-10-31T10:10:00Z',
        message: 'Delivery OTP generated successfully and dispatched to recipient',
      },
    });

    renderComponent();

    await waitFor(() => {
      expect(screen.getByTestId('request-otp-btn')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId('request-otp-btn'));

    await waitFor(() => {
      expect(shipmentService.generateDeliveryOtp).toHaveBeenCalledWith(101);
      expect(screen.getByTestId('dev-otp-simulation-box')).toBeInTheDocument();
      expect(screen.getByTestId('dev-otp-code')).toHaveTextContent('849201');
      expect(screen.getByTestId('request-otp-btn')).toHaveTextContent(/Re-send OTP in/i);
    });
  });

  it('handles production mode where OTP is omitted from response and hides simulation box', async () => {
    shipmentService.generateDeliveryOtp.mockResolvedValueOnce({
      success: true,
      data: {
        shipmentId: 101,
        trackingNumber: 'TRK-20261031-DELIV01',
        otp: null,
        expiresAt: '2026-10-31T10:10:00Z',
        message: 'Delivery OTP generated successfully and dispatched to recipient',
      },
    });

    renderComponent();

    await waitFor(() => {
      expect(screen.getByTestId('request-otp-btn')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId('request-otp-btn'));

    await waitFor(() => {
      expect(shipmentService.generateDeliveryOtp).toHaveBeenCalledWith(101);
      expect(screen.queryByTestId('dev-otp-simulation-box')).not.toBeInTheDocument();
      expect(screen.getByTestId('request-otp-btn')).toHaveTextContent(/Re-send OTP in/i);
    });
  });

  it('validates 6-digit numeric constraint on client before OTP submission', async () => {
    renderComponent();

    await waitFor(() => {
      expect(screen.getByTestId('otp-code-input')).toBeInTheDocument();
    });

    const input = screen.getByTestId('otp-code-input');
    const submitBtn = screen.getByTestId('verify-otp-submit-btn');

    // Enter only 4 digits
    fireEvent.change(input, { target: { value: '1234' } });
    expect(submitBtn).toBeDisabled();

    // Verify non-numeric characters are stripped
    fireEvent.change(input, { target: { value: '12ab34' } });
    expect(input.value).toBe('1234');
  });

  it('submits valid OTP, confirms delivery handover, stops GPS, and displays confirmation card', async () => {
    shipmentService.verifyDeliveryOtp.mockResolvedValueOnce({
      success: true,
      data: {
        shipmentId: 101,
        trackingNumber: 'TRK-20261031-DELIV01',
        status: 'DELIVERED',
        deliveryProofId: 501,
        proofType: 'OTP',
        recipientName: 'Robert Vance Jr.',
        deliveredAt: '2026-10-31T10:05:00Z',
      },
    });

    renderComponent();

    await waitFor(() => {
      expect(screen.getByTestId('otp-code-input')).toBeInTheDocument();
    });

    // Start tracking first to test that it is stopped upon delivery
    fireEvent.click(screen.getByTestId('start-tracking-btn'));

    // Fill handover details
    fireEvent.change(screen.getByTestId('otp-code-input'), { target: { value: '849201' } });
    fireEvent.change(screen.getByTestId('recipient-name-input'), { target: { value: 'Robert Vance Jr.' } });
    fireEvent.change(screen.getByTestId('verification-notes-input'), { target: { value: 'Handed to recipient at door' } });

    // Submit verification
    fireEvent.click(screen.getByTestId('verify-otp-submit-btn'));

    await waitFor(() => {
      expect(shipmentService.verifyDeliveryOtp).toHaveBeenCalledWith(101, {
        otp: '849201',
        recipientName: 'Robert Vance Jr.',
        verificationNotes: 'Handed to recipient at door',
        locationName: undefined,
        latitude: undefined,
        longitude: undefined,
      });
      expect(screen.getByTestId('delivery-confirmed-card')).toBeInTheDocument();
      expect(screen.getByText('Parcel Successfully Delivered!')).toBeInTheDocument();
      expect(screen.getByText(/Proof ID: #501/i)).toBeInTheDocument();
    });

    // GPS watch must be stopped upon delivery
    expect(mockClearWatch).toHaveBeenCalledWith(mockWatchId);
    expect(screen.getByTestId('start-tracking-btn')).toBeDisabled();
  });

  it('displays error message when OTP verification fails (invalid or expired)', async () => {
    shipmentService.verifyDeliveryOtp.mockRejectedValueOnce(
      new Error('Invalid delivery OTP. Please verify with recipient.')
    );

    renderComponent();

    await waitFor(() => {
      expect(screen.getByTestId('otp-code-input')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByTestId('otp-code-input'), { target: { value: '000000' } });
    fireEvent.click(screen.getByTestId('verify-otp-submit-btn'));

    await waitFor(() => {
      expect(screen.getByTestId('otp-verification-error')).toBeInTheDocument();
      expect(screen.getByTestId('otp-verification-error')).toHaveTextContent(
        /Invalid delivery OTP. Please verify with recipient./i
      );
    });

    // Delivered card must NOT be present
    expect(screen.queryByTestId('delivery-confirmed-card')).not.toBeInTheDocument();
  });

  it('shows guidance banner and allows starting delivery run when status is ASSIGNED or PICKED_UP', async () => {
    agentService.getAssignmentById.mockResolvedValueOnce({
      success: true,
      data: {
        ...sampleShipment,
        status: 'PICKED_UP',
      },
    });

    shipmentService.updateStatus.mockResolvedValueOnce({
      success: true,
      data: {
        ...sampleShipment,
        status: 'OUT_FOR_DELIVERY',
      },
    });

    renderComponent();

    await waitFor(() => {
      expect(screen.getByTestId('status-guidance-banner')).toBeInTheDocument();
      expect(screen.getByTestId('start-delivery-run-btn')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId('start-delivery-run-btn'));

    await waitFor(() => {
      expect(shipmentService.updateStatus).toHaveBeenCalledWith(101, {
        status: 'OUT_FOR_DELIVERY',
        remarks: 'Delivery agent activated live delivery run mode',
      });
      expect(screen.queryByTestId('status-guidance-banner')).not.toBeInTheDocument();
    });
  });
});
