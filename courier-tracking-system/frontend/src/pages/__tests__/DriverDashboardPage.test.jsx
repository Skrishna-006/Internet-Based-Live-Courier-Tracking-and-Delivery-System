import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import DriverDashboardPage from '../DriverDashboardPage';
import * as authContextModule from '../../context/AuthContext';
import { agentService, shipmentService } from '../../services/api';
import * as websocketModule from '../../services/websocket';

vi.mock('../../services/api', () => ({
  agentService: {
    getMyAssignments: vi.fn(),
    getMySummary: vi.fn(),
  },
  shipmentService: {
    updateStatus: vi.fn(),
  },
}));

describe('DriverDashboardPage Component', () => {
  const mockDriver = {
    id: 42,
    fullName: 'Alex Delivery Agent',
    email: 'alex.driver@example.com',
    role: 'ROLE_DELIVERY_AGENT',
  };

  const sampleSummary = {
    totalAssigned: 5,
    awaitingPickup: 2,
    inTransit: 1,
    outForDelivery: 1,
    completed: 1,
  };

  const sampleShipmentAssigned = {
    id: 101,
    trackingNumber: 'TRK-20261030-AGENT01',
    status: 'ASSIGNED',
    recipientName: 'Alice Green',
    recipientAddress: '123 Pine St, City A',
    recipientPhone: '+1-555-0101',
    originAddress: 'Central Hub, Bay 4',
    weightKg: 3.5,
    estimatedPrice: 25.0,
    createdAt: '2026-10-30T10:00:00Z',
  };

  const sampleShipmentOutForDelivery = {
    id: 102,
    trackingNumber: 'TRK-20261030-AGENT02',
    status: 'OUT_FOR_DELIVERY',
    recipientName: 'Bob Vance',
    recipientAddress: '456 Oak Rd, City B',
    recipientPhone: '+1-555-0202',
    originAddress: 'Suburban Depot',
    weightKg: 5.0,
    estimatedPrice: 38.5,
    createdAt: '2026-10-30T10:15:00Z',
  };

  let mockStompSubscriptionCallback = null;
  const mockStompClient = {
    activate: vi.fn(),
    deactivate: vi.fn(),
    subscribe: vi.fn((topic, cb) => {
      mockStompSubscriptionCallback = cb;
      return { unsubscribe: vi.fn() };
    }),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockStompSubscriptionCallback = null;

    vi.spyOn(authContextModule, 'useAuth').mockReturnValue({
      user: mockDriver,
      token: 'mock-jwt-token-agent',
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
      loading: false,
    });

    vi.spyOn(websocketModule, 'createStompClient').mockImplementation(({ onConnect }) => {
      setTimeout(() => {
        if (onConnect) onConnect({});
      }, 0);
      return mockStompClient;
    });

    agentService.getMySummary.mockResolvedValue({
      success: true,
      data: sampleSummary,
    });

    agentService.getMyAssignments.mockResolvedValue({
      success: true,
      data: {
        content: [sampleShipmentAssigned],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        first: true,
        last: true,
      },
    });
  });

  it('renders driver portal header, agent details, and connection status', async () => {
    render(
      <MemoryRouter>
        <DriverDashboardPage />
      </MemoryRouter>
    );

    expect(screen.getByTestId('driver-portal-title')).toHaveTextContent('Delivery Agent Route Portal');
    expect(screen.getByText(/Alex Delivery Agent/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByTestId('ws-connection-indicator')).toHaveTextContent('Live Dispatch');
    });
  });

  it('renders queue summary metrics from getMySummary', async () => {
    render(
      <MemoryRouter>
        <DriverDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('metric-total-assigned')).toHaveTextContent('5');
      expect(screen.getByTestId('metric-awaiting-pickup')).toHaveTextContent('2');
      expect(screen.getByTestId('metric-in-transit')).toHaveTextContent('1');
      expect(screen.getByTestId('metric-out-for-delivery')).toHaveTextContent('1');
      expect(screen.getByTestId('metric-completed')).toHaveTextContent('1');
    });
  });

  it('renders assigned shipment card with details and live tracking link', async () => {
    render(
      <MemoryRouter>
        <DriverDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('TRK-20261030-AGENT01')).toBeInTheDocument();
      expect(screen.getByTestId('status-badge-101')).toHaveTextContent('Awaiting Pickup');
      expect(screen.getByText('Alice Green')).toBeInTheDocument();
      expect(screen.getByText('123 Pine St, City A')).toBeInTheDocument();
      expect(screen.getByText('+1-555-0101')).toBeInTheDocument();
      expect(screen.getByText('3.5 kg')).toBeInTheDocument();
    });

    const trackLink = screen.getByTestId('track-link-101');
    expect(trackLink).toHaveAttribute('href', '/track/TRK-20261030-AGENT01');
  });

  it('filters assignments when status filter button is clicked', async () => {
    render(
      <MemoryRouter>
        <DriverDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(agentService.getMyAssignments).toHaveBeenCalledWith({ page: 0, size: 10 });
    });

    fireEvent.click(screen.getByTestId('filter-ASSIGNED'));

    await waitFor(() => {
      expect(agentService.getMyAssignments).toHaveBeenCalledWith({
        page: 0,
        size: 10,
        status: 'ASSIGNED',
      });
    });
  });

  it('renders empty queue state when no assignments exist', async () => {
    agentService.getMyAssignments.mockResolvedValueOnce({
      success: true,
      data: {
        content: [],
        totalElements: 0,
        totalPages: 0,
        number: 0,
        first: true,
        last: true,
      },
    });

    render(
      <MemoryRouter>
        <DriverDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('empty-queue-state')).toBeInTheDocument();
      expect(screen.getByText('No shipments found')).toBeInTheDocument();
    });
  });

  it('opens transition modal and submits status update ASSIGNED -> PICKED_UP', async () => {
    shipmentService.updateStatus.mockResolvedValueOnce({
      success: true,
      data: {
        ...sampleShipmentAssigned,
        status: 'PICKED_UP',
      },
    });

    render(
      <MemoryRouter>
        <DriverDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('action-picked_up-101')).toBeInTheDocument();
    });

    // Click "Pick Up Package" button
    fireEvent.click(screen.getByTestId('action-picked_up-101'));

    // Modal opens
    expect(screen.getByTestId('status-transition-modal')).toBeInTheDocument();
    expect(screen.getByTestId('modal-target-status')).toHaveTextContent('PICKED UP');

    // Fill optional fields
    fireEvent.change(screen.getByTestId('input-location-name'), {
      target: { value: 'Customer Origin Warehouse' },
    });
    fireEvent.change(screen.getByTestId('input-remarks'), {
      target: { value: 'Package picked up in good condition' },
    });

    // Submit modal
    fireEvent.click(screen.getByTestId('modal-confirm-btn'));

    await waitFor(() => {
      expect(shipmentService.updateStatus).toHaveBeenCalledWith(101, {
        status: 'PICKED_UP',
        locationName: 'Customer Origin Warehouse',
        remarks: 'Package picked up in good condition',
      });
      expect(screen.getByTestId('success-banner')).toHaveTextContent('successfully updated to PICKED UP');
    });

    expect(screen.queryByTestId('status-transition-modal')).not.toBeInTheDocument();
  });

  it('renders OTP mode badge and failed attempt action for OUT_FOR_DELIVERY shipment', async () => {
    agentService.getMyAssignments.mockResolvedValueOnce({
      success: true,
      data: {
        content: [sampleShipmentOutForDelivery],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        first: true,
        last: true,
      },
    });

    render(
      <MemoryRouter>
        <DriverDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('TRK-20261030-AGENT02')).toBeInTheDocument();
      expect(screen.getByText('OTP Mode')).toBeInTheDocument();
      expect(screen.getByText(/Delivery completion requires OTP verification/i)).toBeInTheDocument();
      expect(screen.getByTestId('action-attempt_failed-102')).toBeInTheDocument();
    });
  });

  it('handles server-side pagination controls', async () => {
    agentService.getMyAssignments.mockImplementation((params) =>
      Promise.resolve({
        success: true,
        data: {
          content: [sampleShipmentAssigned],
          totalElements: 25,
          totalPages: 3,
          number: params?.page || 0,
          first: (params?.page || 0) === 0,
          last: (params?.page || 0) === 2,
        },
      })
    );

    render(
      <MemoryRouter>
        <DriverDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('pagination-controls')).toBeInTheDocument();
      expect(screen.getByTestId('pagination-controls')).toHaveTextContent(/Page 1 of 3/i);
    });

    const prevBtn = screen.getByTestId('prev-page-btn');
    const nextBtn = screen.getByTestId('next-page-btn');

    expect(prevBtn).toBeDisabled();
    expect(nextBtn).not.toBeDisabled();

    fireEvent.click(nextBtn);

    await waitFor(() => {
      expect(agentService.getMyAssignments).toHaveBeenCalledWith({ page: 1, size: 10 });
    });
  });

  it('displays real-time STOMP notification when new order assigned', async () => {
    render(
      <MemoryRouter>
        <DriverDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(mockStompClient.subscribe).toHaveBeenCalledWith(
        '/topic/agent/42/orders',
        expect.any(Function)
      );
    });

    // Simulate incoming STOMP message from dispatcher
    const incomingOrder = {
      trackingNumber: 'TRK-20261030-NEW999',
      recipientName: 'David Miller',
    };

    mockStompSubscriptionCallback({
      body: JSON.stringify(incomingOrder),
    });

    await waitFor(() => {
      expect(screen.getByTestId('new-assignment-alert')).toBeInTheDocument();
      expect(screen.getByText(/New Delivery Assigned by Dispatch!/i)).toBeInTheDocument();
      expect(screen.getByText(/TRK-20261030-NEW999/i)).toBeInTheDocument();
      expect(screen.getByText(/David Miller/i)).toBeInTheDocument();
    });

    // Dismiss alert
    fireEvent.click(screen.getByLabelText('Dismiss alert'));
    expect(screen.queryByTestId('new-assignment-alert')).not.toBeInTheDocument();
  });

  it('renders error banner when assignments fetch fails and retries', async () => {
    agentService.getMyAssignments.mockRejectedValueOnce(new Error('Network error loading queue'));

    render(
      <MemoryRouter>
        <DriverDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('error-banner')).toBeInTheDocument();
      expect(screen.getByText('Network error loading queue')).toBeInTheDocument();
    });

    agentService.getMyAssignments.mockResolvedValueOnce({
      success: true,
      data: {
        content: [sampleShipmentAssigned],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        first: true,
        last: true,
      },
    });

    fireEvent.click(screen.getByText('Retry'));

    await waitFor(() => {
      expect(screen.getByText('TRK-20261030-AGENT01')).toBeInTheDocument();
      expect(screen.queryByTestId('error-banner')).not.toBeInTheDocument();
    });
  });
});
