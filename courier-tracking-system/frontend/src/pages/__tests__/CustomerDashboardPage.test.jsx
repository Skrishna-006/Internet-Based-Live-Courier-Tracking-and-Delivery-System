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
});
