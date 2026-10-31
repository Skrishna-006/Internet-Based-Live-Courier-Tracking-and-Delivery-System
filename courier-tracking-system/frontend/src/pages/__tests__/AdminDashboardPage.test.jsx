import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import AdminDashboardPage from '../AdminDashboardPage';
import { adminService } from '../../services/api';
import * as authContextModule from '../../context/AuthContext';
import ProtectedRoute from '../../components/ProtectedRoute';

vi.mock('../../services/api', () => ({
  adminService: {
    getOverview: vi.fn(),
    getShipments: vi.fn(),
    getShipmentById: vi.fn(),
    getShipmentEvents: vi.fn(),
    getAgents: vi.fn(),
    getFleet: vi.fn(),
  },
}));

describe('AdminDashboardPage Component', () => {
  const sampleAdminUser = {
    id: 1,
    fullName: 'Chief Administrator',
    email: 'admin@logistics.com',
    role: 'ROLE_ADMIN',
  };

  const sampleCustomerUser = {
    id: 99,
    fullName: 'Jane Customer',
    email: 'jane@customer.com',
    role: 'ROLE_CUSTOMER',
  };

  const sampleOverview = {
    totalShipments: 120,
    unassignedShipments: 15,
    assignedShipments: 25,
    inTransitShipments: 30,
    outForDeliveryShipments: 10,
    deliveredShipments: 35,
    failedDeliveryShipments: 5,
    activeAgents: 8,
  };

  const sampleShipments = [
    {
      id: 101,
      trackingNumber: 'TRK-20261031-ADMIN01',
      status: 'OUT_FOR_DELIVERY',
      customerId: 5,
      customerEmail: 'cust5@example.com',
      assignedAgentId: 10,
      assignedAgentName: 'Driver Alex',
      senderName: 'Origin Depot',
      senderAddress: '100 Warehouse Way',
      senderPhone: '+1-555-0199',
      recipientName: 'Recipient Alice',
      recipientAddress: '200 Main St',
      recipientPhone: '+1-555-0198',
      recipientLatitude: 30.2672,
      recipientLongitude: -97.7431,
      weightKg: 3.5,
      price: 45.0,
      currency: 'USD',
      estimatedDeliveryAt: '2026-10-31T18:00:00Z',
      createdAt: '2026-10-31T08:00:00Z',
    },
    {
      id: 102,
      trackingNumber: 'TRK-20261031-ADMIN02',
      status: 'CREATED',
      customerId: 6,
      customerEmail: 'cust6@example.com',
      assignedAgentId: null,
      assignedAgentName: null,
      senderName: 'Origin Depot',
      senderAddress: '100 Warehouse Way',
      senderPhone: '+1-555-0199',
      recipientName: 'Recipient Bob',
      recipientAddress: '300 Oak Ave',
      recipientPhone: '+1-555-0197',
      recipientLatitude: null,
      recipientLongitude: null,
      weightKg: 1.2,
      price: 18.5,
      currency: 'USD',
      estimatedDeliveryAt: null,
      createdAt: '2026-10-31T09:00:00Z',
    },
  ];

  const sampleAgents = [
    {
      id: 10,
      fullName: 'Driver Alex',
      email: 'alex@driver.com',
      phoneNumber: '+1-555-0101',
      active: true,
      totalAssignedShipments: 8,
      activeDeliveriesCount: 3,
      deliveredShipmentsCount: 5,
    },
    {
      id: 11,
      fullName: 'Driver Sam',
      email: 'sam@driver.com',
      phoneNumber: '+1-555-0102',
      active: false,
      totalAssignedShipments: 2,
      activeDeliveriesCount: 0,
      deliveredShipmentsCount: 2,
    },
  ];

  const sampleEvents = [
    {
      status: 'CREATED',
      locationName: 'Central Sorting Facility',
      remarks: 'Consignment booked and label generated',
      timestamp: '2026-10-31T08:00:00Z',
    },
    {
      status: 'OUT_FOR_DELIVERY',
      locationName: 'Austin Metro Hub',
      remarks: 'Courier dispatched on final delivery leg',
      timestamp: '2026-10-31T11:30:00Z',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();

    vi.spyOn(authContextModule, 'useAuth').mockReturnValue({
      user: sampleAdminUser,
      isAuthenticated: true,
      token: 'admin-jwt-token',
    });

    adminService.getOverview.mockResolvedValue({
      success: true,
      data: sampleOverview,
    });

    adminService.getShipments.mockResolvedValue({
      success: true,
      data: {
        content: sampleShipments,
        totalElements: 2,
        totalPages: 1,
        number: 0,
        size: 10,
        first: true,
        last: true,
      },
    });

    adminService.getAgents.mockResolvedValue({
      success: true,
      data: sampleAgents,
    });

    adminService.getShipmentById.mockResolvedValue({
      success: true,
      data: sampleShipments[0],
    });

    adminService.getShipmentEvents.mockResolvedValue({
      success: true,
      data: sampleEvents,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders operational overview metrics correctly from API response', async () => {
    render(
      <MemoryRouter>
        <AdminDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('overview-metrics-grid')).toBeInTheDocument();
    });

    // Check all 8 metrics from sampleOverview
    expect(screen.getByTestId('metric-total-shipments')).toHaveTextContent('120');
    expect(screen.getByTestId('metric-unassigned-shipments')).toHaveTextContent('15');
    expect(screen.getByTestId('metric-assigned-shipments')).toHaveTextContent('25');
    expect(screen.getByTestId('metric-in-transit-shipments')).toHaveTextContent('30');
    expect(screen.getByTestId('metric-out-for-delivery-shipments')).toHaveTextContent('10');
    expect(screen.getByTestId('metric-delivered-shipments')).toHaveTextContent('35');
    expect(screen.getByTestId('metric-failed-delivery-shipments')).toHaveTextContent('5');
    expect(screen.getByTestId('metric-active-agents')).toHaveTextContent('8');
  });

  it('handles overview API error and allows retrying', async () => {
    adminService.getOverview.mockRejectedValueOnce({
      message: 'Failed to connect to overview service',
    });

    render(
      <MemoryRouter>
        <AdminDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('overview-error-banner')).toBeInTheDocument();
      expect(screen.getByText(/Failed to load operational overview metrics/i)).toBeInTheDocument();
    });

    // Retry overview fetch
    adminService.getOverview.mockResolvedValueOnce({
      success: true,
      data: sampleOverview,
    });

    fireEvent.click(screen.getByRole('button', { name: /retry/i }));

    await waitFor(() => {
      expect(screen.queryByTestId('overview-error-banner')).not.toBeInTheDocument();
      expect(screen.getByTestId('metric-total-shipments')).toHaveTextContent('120');
    });
  });

  it('renders shipments table with tracking numbers, status badges, and courier assignments', async () => {
    render(
      <MemoryRouter>
        <AdminDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('shipments-table')).toBeInTheDocument();
    });

    const row1 = screen.getByTestId('shipment-row-101');
    expect(within(row1).getByText('TRK-20261031-ADMIN01')).toBeInTheDocument();
    expect(within(row1).getByText('Driver Alex')).toBeInTheDocument();
    expect(within(row1).getByText('Out for Delivery')).toBeInTheDocument();

    const row2 = screen.getByTestId('shipment-row-102');
    expect(within(row2).getByText('TRK-20261031-ADMIN02')).toBeInTheDocument();
    expect(within(row2).getByText('Unassigned')).toBeInTheDocument();
  });

  it('filters shipments by status when status dropdown changes', async () => {
    render(
      <MemoryRouter>
        <AdminDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('shipments-table')).toBeInTheDocument();
    });

    const statusSelect = screen.getByTestId('shipment-status-filter');
    fireEvent.change(statusSelect, { target: { value: 'OUT_FOR_DELIVERY' } });

    await waitFor(() => {
      expect(adminService.getShipments).toHaveBeenCalledWith(
        expect.objectContaining({
          status: 'OUT_FOR_DELIVERY',
          page: 0,
        })
      );
    });
  });

  it('submits search query and triggers shipment filter', async () => {
    render(
      <MemoryRouter>
        <AdminDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('shipments-table')).toBeInTheDocument();
    });

    const searchInput = screen.getByTestId('shipment-search-input');
    fireEvent.change(searchInput, { target: { value: 'ADMIN01' } });
    fireEvent.submit(searchInput.closest('form'));

    await waitFor(() => {
      expect(adminService.getShipments).toHaveBeenCalledWith(
        expect.objectContaining({
          search: 'ADMIN01',
          page: 0,
        })
      );
    });
  });

  it('filters by unassignedOnly when checkbox is checked', async () => {
    render(
      <MemoryRouter>
        <AdminDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('shipments-table')).toBeInTheDocument();
    });

    const unassignedToggle = screen.getByTestId('unassigned-filter-toggle');
    fireEvent.click(unassignedToggle);

    await waitFor(() => {
      expect(adminService.getShipments).toHaveBeenCalledWith(
        expect.objectContaining({
          unassignedOnly: true,
          page: 0,
        })
      );
    });
  });

  it('handles page size changes and pagination controls', async () => {
    adminService.getShipments.mockResolvedValueOnce({
      success: true,
      data: {
        content: sampleShipments,
        totalElements: 45,
        totalPages: 5,
        number: 0,
        size: 10,
        first: true,
        last: false,
      },
    });

    render(
      <MemoryRouter>
        <AdminDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('shipments-pagination')).toBeInTheDocument();
    });

    expect(screen.getByTestId('pagination-current-page')).toHaveTextContent('Page 1 of 5');

    // Click next page
    fireEvent.click(screen.getByTestId('pagination-next-btn'));

    await waitFor(() => {
      expect(adminService.getShipments).toHaveBeenCalledWith(
        expect.objectContaining({
          page: 1,
        })
      );
    });

    // Change page size to 20
    const pageSizeSelect = screen.getByTestId('page-size-selector');
    fireEvent.change(pageSizeSelect, { target: { value: '20' } });

    await waitFor(() => {
      expect(adminService.getShipments).toHaveBeenCalledWith(
        expect.objectContaining({
          size: 20,
          page: 0,
        })
      );
    });
  });

  it('resets all filters when Reset button is clicked', async () => {
    render(
      <MemoryRouter>
        <AdminDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('shipments-table')).toBeInTheDocument();
    });

    // Apply some filters
    fireEvent.change(screen.getByTestId('shipment-search-input'), { target: { value: 'QUERY' } });
    fireEvent.change(screen.getByTestId('shipment-status-filter'), { target: { value: 'DELIVERED' } });
    fireEvent.click(screen.getByTestId('unassigned-filter-toggle'));

    // Click reset
    fireEvent.click(screen.getByTestId('reset-filters-btn'));

    await waitFor(() => {
      expect(screen.getByTestId('shipment-search-input')).toHaveValue('');
      expect(screen.getByTestId('shipment-status-filter')).toHaveValue('ALL');
      expect(screen.getByTestId('unassigned-filter-toggle')).not.toBeChecked();
    });
  });

  it('opens shipment details modal and renders consignment information and audit events', async () => {
    render(
      <MemoryRouter>
        <AdminDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('view-shipment-101')).toBeInTheDocument();
    });

    // Click Details button on shipment 101
    fireEvent.click(screen.getByTestId('view-shipment-101'));

    await waitFor(() => {
      expect(screen.getByTestId('shipment-details-modal')).toBeInTheDocument();
      expect(adminService.getShipmentById).toHaveBeenCalledWith(101);
      expect(adminService.getShipmentEvents).toHaveBeenCalledWith(101);
    });

    // Verify modal content using within(modal)
    const modal = screen.getByTestId('shipment-details-modal');
    expect(within(modal).getByText('Recipient Alice')).toBeInTheDocument();
    expect(within(modal).getByText('200 Main St')).toBeInTheDocument();
    expect(within(modal).getByText('Origin Depot')).toBeInTheDocument();
    expect(within(modal).getByText(/USD 45.00/i)).toBeInTheDocument();

    // Verify audit milestones
    expect(within(modal).getByText('Central Sorting Facility')).toBeInTheDocument();
    expect(within(modal).getByText(/Consignment booked and label generated/i)).toBeInTheDocument();
    expect(within(modal).getByText('Austin Metro Hub')).toBeInTheDocument();

    // Close modal
    fireEvent.click(within(modal).getByTestId('close-modal-btn'));
    expect(screen.queryByTestId('shipment-details-modal')).not.toBeInTheDocument();
  });

  it('switches to agent directory tab and renders courier workload summaries', async () => {
    render(
      <MemoryRouter>
        <AdminDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('tab-agents')).toBeInTheDocument();
    });

    // Switch to Agents Directory tab
    fireEvent.click(screen.getByTestId('tab-agents'));

    await waitFor(() => {
      expect(screen.getByTestId('agents-directory-section')).toBeInTheDocument();
      expect(screen.getByTestId('agents-table')).toBeInTheDocument();
    });

    // Check Driver Alex
    expect(screen.getByText('Driver Alex')).toBeInTheDocument();
    expect(screen.getByText('alex@driver.com')).toBeInTheDocument();
    expect(screen.getByTestId('agent-status-10')).toHaveTextContent('Account Enabled');

    // Check Driver Sam
    expect(screen.getByText('Driver Sam')).toBeInTheDocument();
    expect(screen.getByText('sam@driver.com')).toBeInTheDocument();
    expect(screen.getByTestId('agent-status-11')).toHaveTextContent('Account Deactivated');

    // Test Agent Search Filter
    const agentSearch = screen.getByTestId('agent-search-input');
    fireEvent.change(agentSearch, { target: { value: 'Alex' } });

    expect(screen.getByText('Driver Alex')).toBeInTheDocument();
    expect(screen.queryByText('Driver Sam')).not.toBeInTheDocument();
  });

  it('provides navigation button to existing live fleet map', async () => {
    render(
      <MemoryRouter>
        <AdminDashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('overview-metrics-grid')).toBeInTheDocument();
    });

    const fleetMapBtn = screen.getByTestId('admin-open-fleet-map-btn');
    expect(fleetMapBtn).toBeInTheDocument();
    expect(fleetMapBtn).toHaveAttribute('href', '/admin/fleet');
  });

  it('enforces admin-only route protection: redirects customer role to /unauthorized', async () => {
    vi.spyOn(authContextModule, 'useAuth').mockReturnValue({
      user: sampleCustomerUser,
      isAuthenticated: true,
      token: 'customer-jwt-token',
    });

    render(
      <MemoryRouter initialEntries={['/admin/dashboard']}>
        <Routes>
          <Route
            path="/admin/dashboard"
            element={
              <ProtectedRoute allowedRoles={['ROLE_ADMIN']}>
                <AdminDashboardPage />
              </ProtectedRoute>
            }
          />
          <Route path="/unauthorized" element={<div data-testid="unauthorized-page">Access Denied</div>} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('unauthorized-page')).toBeInTheDocument();
      expect(screen.queryByTestId('overview-metrics-grid')).not.toBeInTheDocument();
    });
  });
});
