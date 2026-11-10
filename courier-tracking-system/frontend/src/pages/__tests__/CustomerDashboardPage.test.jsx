import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import CustomerDashboardPage from '../CustomerDashboardPage';
import * as authContextModule from '../../context/AuthContext';
import { shipmentService } from '../../services/api';

vi.mock('../../services/api', () => ({
  shipmentService: {
    getCustomerShipments: vi.fn(),
    getCustomerDeliveryPin: vi.fn(),
  },
}));

describe('CustomerDashboardPage Component', () => {
  const mockUser = {
    id: 1,
    fullName: 'Jane Customer',
    email: 'jane@example.com',
    role: 'ROLE_CUSTOMER',
  };

  const sampleShipment = {
    id: 101,
    trackingNumber: 'TRK-20261030-SAMPLE1',
    status: 'OUT_FOR_DELIVERY',
    recipientName: 'Robert Vance',
    recipientAddress: '300 Refrigeration Way, Scranton, PA',
    weightKg: 4.5,
    price: 32.5,
    currency: 'USD',
    createdAt: '2026-10-30T09:00:00Z',
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(authContextModule, 'useAuth').mockReturnValue({
      user: mockUser,
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
      loading: false,
    });
  });

  it('renders dashboard header and CTA button', async () => {
    shipmentService.getCustomerShipments.mockResolvedValueOnce({
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
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <CustomerDashboardPage />
      </MemoryRouter>
    );

    expect(screen.getByText('My Shipments')).toBeInTheDocument();
    expect(screen.getByText(/Jane Customer/i)).toBeInTheDocument();
    expect(screen.getByTestId('btn-book-shipment')).toHaveAttribute('href', '/customer/book');

    // Drain the async useEffect data fetch so it doesn't leak state updates after test end
    await act(async () => {});
  });

  it('renders shipments table when shipments are returned', async () => {
    shipmentService.getCustomerShipments.mockResolvedValueOnce({
      success: true,
      data: {
        content: [sampleShipment],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        first: true,
        last: true,
      },
    });

    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <CustomerDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('TRK-20261030-SAMPLE1')).toBeInTheDocument();
      expect(screen.getByTestId('shipment-row-TRK-20261030-SAMPLE1')).toHaveTextContent('Out for Delivery');
      expect(screen.getByText('Robert Vance')).toBeInTheDocument();
      expect(screen.getByText('4.5 kg')).toBeInTheDocument();
      expect(screen.getByText('$32.50')).toBeInTheDocument();
    });

    const trackBtn = screen.getByTestId('track-btn-TRK-20261030-SAMPLE1');
    expect(trackBtn).toHaveAttribute('href', '/track/TRK-20261030-SAMPLE1');
  });

  it('filters shipments by status when a filter pill is clicked', async () => {
    shipmentService.getCustomerShipments.mockResolvedValue({
      success: true,
      data: {
        content: [sampleShipment],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        first: true,
        last: true,
      },
    });

    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <CustomerDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(shipmentService.getCustomerShipments).toHaveBeenCalledWith({
        page: 0,
        size: 10,
      });
    });

    // Click "Out for Delivery" filter
    const filterBtn = screen.getByTestId('filter-out_for_delivery');
    fireEvent.click(filterBtn);

    await waitFor(() => {
      expect(shipmentService.getCustomerShipments).toHaveBeenCalledWith({
        page: 0,
        size: 10,
        status: 'OUT_FOR_DELIVERY',
      });
    });
  });

  it('handles pagination next and previous clicks', async () => {
    shipmentService.getCustomerShipments.mockResolvedValueOnce({
      success: true,
      data: {
        content: [sampleShipment],
        totalElements: 15,
        totalPages: 2,
        number: 0,
        first: true,
        last: false,
      },
    });

    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <CustomerDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('pagination-info')).toHaveTextContent('Showing page 1 of 2');
    });

    const nextBtn = screen.getByTestId('pagination-next');
    expect(nextBtn).not.toBeDisabled();

    // Mock page 2 response
    shipmentService.getCustomerShipments.mockResolvedValueOnce({
      success: true,
      data: {
        content: [
          {
            ...sampleShipment,
            id: 102,
            trackingNumber: 'TRK-20261030-SAMPLE2',
          },
        ],
        totalElements: 15,
        totalPages: 2,
        number: 1,
        first: false,
        last: true,
      },
    });

    fireEvent.click(nextBtn);

    await waitFor(() => {
      expect(shipmentService.getCustomerShipments).toHaveBeenCalledWith({
        page: 1,
        size: 10,
      });
    });
  });

  it('renders empty state illustration when no consignments exist', async () => {
    shipmentService.getCustomerShipments.mockResolvedValueOnce({
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
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <CustomerDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('empty-shipments-state')).toBeInTheDocument();
      expect(screen.getByText('No shipments found')).toBeInTheDocument();
      expect(screen.getByText('Book a Shipment Now')).toBeInTheDocument();
    });
  });

  it('renders error banner with retry option when request fails', async () => {
    shipmentService.getCustomerShipments.mockRejectedValueOnce({
      success: false,
      message: 'Network connection lost',
    });

    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <CustomerDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
      expect(screen.getByText(/Network connection lost/i)).toBeInTheDocument();
    });

    // Test retry button
    shipmentService.getCustomerShipments.mockResolvedValueOnce({
      success: true,
      data: {
        content: [sampleShipment],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        first: true,
        last: true,
      },
    });

    fireEvent.click(screen.getByText('Retry'));

    await waitFor(() => {
      expect(screen.getByText('TRK-20261030-SAMPLE1')).toBeInTheDocument();
    });
  });

  it('renders View Delivery OTP button strictly for OUT_FOR_DELIVERY shipments and not for other statuses', async () => {
    const createdShipment = {
      ...sampleShipment,
      id: 102,
      trackingNumber: 'TRK-20261030-CREATED',
      status: 'CREATED',
    };
    const inTransitShipment = {
      ...sampleShipment,
      id: 103,
      trackingNumber: 'TRK-20261030-TRANSIT',
      status: 'IN_TRANSIT',
    };
    const deliveredShipment = {
      ...sampleShipment,
      id: 104,
      trackingNumber: 'TRK-20261030-DELIVERED',
      status: 'DELIVERED',
    };

    shipmentService.getCustomerShipments.mockResolvedValueOnce({
      success: true,
      data: {
        content: [sampleShipment, createdShipment, inTransitShipment, deliveredShipment],
        totalElements: 4,
        totalPages: 1,
        number: 0,
        first: true,
        last: true,
      },
    });

    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <CustomerDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      // OUT_FOR_DELIVERY shipment MUST have the View Delivery OTP button
      expect(screen.getByTestId('view-otp-btn-TRK-20261030-SAMPLE1')).toBeInTheDocument();
    });

    // Other statuses must NOT have the View Delivery OTP button
    expect(screen.queryByTestId('view-otp-btn-TRK-20261030-CREATED')).not.toBeInTheDocument();
    expect(screen.queryByTestId('view-otp-btn-TRK-20261030-TRANSIT')).not.toBeInTheDocument();
    expect(screen.queryByTestId('view-otp-btn-TRK-20261030-DELIVERED')).not.toBeInTheDocument();

    // Verify delivery pin was NOT called automatically on dashboard load
    expect(shipmentService.getCustomerDeliveryPin).not.toHaveBeenCalled();
  });

  it('opens delivery PIN modal on click and retrieves PIN on demand', async () => {
    shipmentService.getCustomerShipments.mockResolvedValueOnce({
      success: true,
      data: {
        content: [sampleShipment],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        first: true,
        last: true,
      },
    });

    shipmentService.getCustomerDeliveryPin.mockResolvedValueOnce({
      success: true,
      message: 'Delivery PIN retrieved successfully',
      data: {
        shipmentId: 101,
        trackingNumber: 'TRK-20261030-SAMPLE1',
        deliveryPin: '592814',
        expiresAt: '2026-10-30T10:00:00Z',
        message: 'Delivery PIN active',
      },
    });

    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <CustomerDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('view-otp-btn-TRK-20261030-SAMPLE1')).toBeInTheDocument();
    });

    // Ensure OTP was NOT fetched yet
    expect(shipmentService.getCustomerDeliveryPin).not.toHaveBeenCalled();

    // Click View Delivery OTP button
    fireEvent.click(screen.getByTestId('view-otp-btn-TRK-20261030-SAMPLE1'));

    // Modal opens, API called with shipment id
    expect(shipmentService.getCustomerDeliveryPin).toHaveBeenCalledWith(101);

    await waitFor(() => {
      expect(screen.getByTestId('delivery-pin-modal')).toBeInTheDocument();
      expect(screen.getByTestId('delivery-pin-value')).toHaveTextContent('592814');
    });

    // Close the modal
    fireEvent.click(screen.getByTestId('dismiss-pin-modal-btn'));

    await waitFor(() => {
      expect(screen.queryByTestId('delivery-pin-modal')).not.toBeInTheDocument();
    });
  });
});
