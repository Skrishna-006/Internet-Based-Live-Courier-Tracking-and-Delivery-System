import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import BookingPage from '../BookingPage';
import * as authContextModule from '../../context/AuthContext';
import { shipmentService } from '../../services/api';

vi.mock('../../services/api', () => ({
  shipmentService: {
    getPriceQuote: vi.fn(),
    createShipment: vi.fn(),
  },
}));

describe('BookingPage Component', () => {
  const mockUser = {
    id: 1,
    fullName: 'Jane Customer',
    email: 'jane@example.com',
    role: 'ROLE_CUSTOMER',
    phoneNumber: '+1-555-0100',
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

  it('renders booking form with pre-filled sender information from authenticated user', () => {
    render(
      <MemoryRouter>
        <BookingPage />
      </MemoryRouter>
    );

    expect(screen.getByText('Book Courier Shipment')).toBeInTheDocument();
    expect(screen.getByTestId('input-sender-name')).toHaveValue('Jane Customer');
    expect(screen.getByTestId('input-sender-phone')).toHaveValue('+1-555-0100');
    expect(screen.getByTestId('input-sender-address')).toHaveValue('');
    expect(screen.getByTestId('input-recipient-name')).toHaveValue('');
    expect(screen.getByTestId('input-recipient-phone')).toHaveValue('');
    expect(screen.getByTestId('input-recipient-address')).toHaveValue('');
    expect(screen.getByTestId('input-package-weight')).toHaveValue(null);
  });

  it('fetches and displays authoritative price quote when valid package weight is entered', async () => {
    shipmentService.getPriceQuote.mockResolvedValueOnce({
      success: true,
      data: {
        weightKg: 2.5,
        price: 22.5,
        currency: 'USD',
        baseRate: 10.0,
        perKgRate: 5.0,
      },
    });

    render(
      <MemoryRouter>
        <BookingPage />
      </MemoryRouter>
    );

    const weightInput = screen.getByTestId('input-package-weight');
    fireEvent.change(weightInput, { target: { value: '2.500' } });

    await waitFor(() => {
      expect(shipmentService.getPriceQuote).toHaveBeenCalledWith(2.5);
    });

    await waitFor(() => {
      expect(screen.getByText('$22.50')).toBeInTheDocument();
      expect(screen.getByText(/Authoritative Quote Locked/i)).toBeInTheDocument();
    });
  });

  it('shows validation warning when weight is below minimum or exceeds maximum', async () => {
    render(
      <MemoryRouter>
        <BookingPage />
      </MemoryRouter>
    );

    const weightInput = screen.getByTestId('input-package-weight');
    fireEvent.change(weightInput, { target: { value: '0.010' } });

    await waitFor(() => {
      expect(
        screen.getByText(/Weight must be between 0.050 kg \(50g\) and 1,000 kg/i)
      ).toBeInTheDocument();
    });

    expect(shipmentService.getPriceQuote).not.toHaveBeenCalled();
    expect(screen.getByTestId('btn-submit-booking')).toBeDisabled();
  });

  it('validates client-side constraints on submit', async () => {
    shipmentService.getPriceQuote.mockResolvedValueOnce({
      success: true,
      data: {
        weightKg: 2.0,
        price: 20.0,
        currency: 'USD',
        baseRate: 10.0,
        perKgRate: 5.0,
      },
    });

    render(
      <MemoryRouter>
        <BookingPage />
      </MemoryRouter>
    );

    const submitBtn = screen.getByTestId('btn-submit-booking');
    fireEvent.change(screen.getByTestId('input-package-weight'), { target: { value: '2.0' } });

    await waitFor(() => {
      expect(submitBtn).not.toBeDisabled();
    });

    fireEvent.submit(submitBtn.closest('form'));

    await waitFor(() => {
      expect(
        screen.getByText(/Sender address must be between 5 and 1000 characters/i)
      ).toBeInTheDocument();
    });
  });

  it('successfully creates shipment and renders waybill confirmation card', async () => {
    shipmentService.getPriceQuote.mockResolvedValue({
      success: true,
      data: {
        weightKg: 3.0,
        price: 25.0,
        currency: 'USD',
        baseRate: 10.0,
        perKgRate: 5.0,
      },
    });

    shipmentService.createShipment.mockResolvedValueOnce({
      success: true,
      data: {
        id: 99,
        trackingNumber: 'TRK-20261030-CONFIRMED',
        senderName: 'Jane Customer',
        senderAddress: '100 Market St, San Francisco, CA',
        senderPhone: '+1-555-0100',
        recipientName: 'Bob Receiver',
        recipientAddress: '200 Oak Ave, Portland, OR',
        recipientPhone: '+1-555-0200',
        weightKg: 3.0,
        price: 25.0,
        currency: 'USD',
        status: 'CREATED',
        createdAt: '2026-10-30T10:00:00Z',
      },
    });

    render(
      <MemoryRouter>
        <BookingPage />
      </MemoryRouter>
    );

    fireEvent.change(screen.getByTestId('input-sender-address'), {
      target: { value: '100 Market St, San Francisco, CA' },
    });
    fireEvent.change(screen.getByTestId('input-recipient-name'), {
      target: { value: 'Bob Receiver' },
    });
    fireEvent.change(screen.getByTestId('input-recipient-phone'), {
      target: { value: '+1-555-0200' },
    });
    fireEvent.change(screen.getByTestId('input-recipient-address'), {
      target: { value: '200 Oak Ave, Portland, OR' },
    });
    fireEvent.change(screen.getByTestId('input-package-weight'), {
      target: { value: '3.000' },
    });

    await waitFor(() => {
      expect(screen.getByTestId('btn-submit-booking')).not.toBeDisabled();
    });

    fireEvent.click(screen.getByTestId('btn-submit-booking'));

    await waitFor(() => {
      expect(screen.getByTestId('waybill-confirmation')).toBeInTheDocument();
      expect(screen.getByTestId('waybill-tracking-number')).toHaveTextContent(
        'TRK-20261030-CONFIRMED'
      );
      expect(screen.getByText('Shipment Booked Successfully!')).toBeInTheDocument();
      expect(screen.getByTestId('btn-track-live')).toHaveAttribute(
        'href',
        '/track/TRK-20261030-CONFIRMED'
      );
    });

    // Test "Book Another Parcel" button resets form
    fireEvent.click(screen.getByTestId('btn-book-another'));
    expect(screen.queryByTestId('waybill-confirmation')).not.toBeInTheDocument();
    expect(screen.getByText('Book Courier Shipment')).toBeInTheDocument();
  });

  it('displays API error message when booking creation fails', async () => {
    shipmentService.getPriceQuote.mockResolvedValue({
      success: true,
      data: {
        weightKg: 1.0,
        price: 15.0,
        currency: 'USD',
        baseRate: 10.0,
        perKgRate: 5.0,
      },
    });

    shipmentService.createShipment.mockRejectedValueOnce({
      success: false,
      message: 'Network error occurred while booking',
    });

    render(
      <MemoryRouter>
        <BookingPage />
      </MemoryRouter>
    );

    fireEvent.change(screen.getByTestId('input-sender-address'), {
      target: { value: '100 Market St, San Francisco, CA' },
    });
    fireEvent.change(screen.getByTestId('input-recipient-name'), {
      target: { value: 'Bob Receiver' },
    });
    fireEvent.change(screen.getByTestId('input-recipient-phone'), {
      target: { value: '+1-555-0200' },
    });
    fireEvent.change(screen.getByTestId('input-recipient-address'), {
      target: { value: '200 Oak Ave, Portland, OR' },
    });
    fireEvent.change(screen.getByTestId('input-package-weight'), {
      target: { value: '1.0' },
    });

    await waitFor(() => {
      expect(screen.getByTestId('btn-submit-booking')).not.toBeDisabled();
    });

    fireEvent.click(screen.getByTestId('btn-submit-booking'));

    await waitFor(() => {
      expect(
        screen.getByText(/Network error occurred while booking/i)
      ).toBeInTheDocument();
    });
  });
});
