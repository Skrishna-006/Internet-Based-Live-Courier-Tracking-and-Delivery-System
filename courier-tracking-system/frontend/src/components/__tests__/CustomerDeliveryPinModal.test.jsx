import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import CustomerDeliveryPinModal from '../CustomerDeliveryPinModal';
import { shipmentService } from '../../services/api';

vi.mock('../../services/api', () => ({
  shipmentService: {
    getCustomerDeliveryPin: vi.fn(),
  },
}));

describe('CustomerDeliveryPinModal Component', () => {
  const sampleShipment = {
    id: 101,
    trackingNumber: 'TRK-20261110-SAMPLE',
    status: 'OUT_FOR_DELIVERY',
    recipientName: 'Alice Customer',
  };

  const samplePinResponse = {
    success: true,
    message: 'Delivery PIN retrieved successfully',
    data: {
      shipmentId: 101,
      trackingNumber: 'TRK-20261110-SAMPLE',
      deliveryPin: '482915',
      expiresAt: '2026-11-10T10:00:00Z',
      message: 'Delivery PIN active. Please share this PIN verbally with your delivery courier upon handover.',
    },
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders nothing when isOpen is false or shipment is null', () => {
    const { rerender } = render(
      <CustomerDeliveryPinModal isOpen={false} shipment={sampleShipment} onClose={vi.fn()} />
    );
    expect(screen.queryByTestId('delivery-pin-modal')).not.toBeInTheDocument();

    rerender(<CustomerDeliveryPinModal isOpen={true} shipment={null} onClose={vi.fn()} />);
    expect(screen.queryByTestId('delivery-pin-modal')).not.toBeInTheDocument();
  });

  it('shows loading state on open and retrieves delivery PIN via API', async () => {
    shipmentService.getCustomerDeliveryPin.mockReturnValueOnce(new Promise(() => {})); // pending promise

    render(<CustomerDeliveryPinModal isOpen={true} shipment={sampleShipment} onClose={vi.fn()} />);

    expect(screen.getByTestId('delivery-pin-modal')).toBeInTheDocument();
    expect(screen.getByTestId('pin-loading-state')).toBeInTheDocument();
    expect(screen.getByText(/Retrieving active delivery PIN/i)).toBeInTheDocument();
    expect(shipmentService.getCustomerDeliveryPin).toHaveBeenCalledWith(101);
  });

  it('displays the 6-digit PIN and expiration time on successful response', async () => {
    shipmentService.getCustomerDeliveryPin.mockResolvedValueOnce(samplePinResponse);

    render(<CustomerDeliveryPinModal isOpen={true} shipment={sampleShipment} onClose={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByTestId('delivery-pin-value')).toHaveTextContent('482915');
    });

    expect(screen.getByTestId('delivery-pin-expires')).toBeInTheDocument();
    expect(screen.getByText(/Valid until/i)).toBeInTheDocument();
    expect(screen.getByText(/Share this 6-digit PIN verbally/i)).toBeInTheDocument();
  });

  it('copies PIN to clipboard when Copy PIN button is clicked', async () => {
    const writeTextMock = vi.fn().mockResolvedValue();
    Object.assign(navigator, {
      clipboard: {
        writeText: writeTextMock,
      },
    });

    shipmentService.getCustomerDeliveryPin.mockResolvedValueOnce(samplePinResponse);

    render(<CustomerDeliveryPinModal isOpen={true} shipment={sampleShipment} onClose={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByTestId('delivery-pin-value')).toHaveTextContent('482915');
    });

    const copyBtn = screen.getByTestId('copy-pin-btn');
    fireEvent.click(copyBtn);

    expect(writeTextMock).toHaveBeenCalledWith('482915');
    await waitFor(() => {
      expect(screen.getByText('Copied!')).toBeInTheDocument();
    });
  });

  it('displays error state and allows retry on API failure', async () => {
    shipmentService.getCustomerDeliveryPin.mockRejectedValueOnce({
      success: false,
      message: 'Access denied: You do not have permission to view the delivery PIN',
    });

    render(<CustomerDeliveryPinModal isOpen={true} shipment={sampleShipment} onClose={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByTestId('pin-error-state')).toBeInTheDocument();
      expect(screen.getByTestId('pin-error-message')).toHaveTextContent(
        'Access denied: You do not have permission to view the delivery PIN'
      );
    });

    // Retry button click
    shipmentService.getCustomerDeliveryPin.mockResolvedValueOnce(samplePinResponse);
    const retryBtn = screen.getByTestId('retry-fetch-pin-btn');
    fireEvent.click(retryBtn);

    await waitFor(() => {
      expect(screen.getByTestId('delivery-pin-value')).toHaveTextContent('482915');
    });
  });

  it('triggers onClose when close button or dismiss button is clicked', async () => {
    const onCloseMock = vi.fn();
    shipmentService.getCustomerDeliveryPin.mockResolvedValueOnce(samplePinResponse);

    render(<CustomerDeliveryPinModal isOpen={true} shipment={sampleShipment} onClose={onCloseMock} />);

    await waitFor(() => {
      expect(screen.getByTestId('delivery-pin-value')).toBeInTheDocument();
    });

    // Close with header X button
    const closeHeaderBtn = screen.getByTestId('close-pin-modal-btn');
    fireEvent.click(closeHeaderBtn);
    expect(onCloseMock).toHaveBeenCalledTimes(1);

    // Close with footer button
    const dismissBtn = screen.getByTestId('dismiss-pin-modal-btn');
    fireEvent.click(dismissBtn);
    expect(onCloseMock).toHaveBeenCalledTimes(2);
  });

  it('closes when Escape key is pressed', async () => {
    const onCloseMock = vi.fn();
    shipmentService.getCustomerDeliveryPin.mockResolvedValueOnce(samplePinResponse);

    render(<CustomerDeliveryPinModal isOpen={true} shipment={sampleShipment} onClose={onCloseMock} />);

    await waitFor(() => {
      expect(screen.getByTestId('delivery-pin-value')).toBeInTheDocument();
    });

    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onCloseMock).toHaveBeenCalledTimes(1);
  });

  it('closes when clicking the backdrop outside modal container', async () => {
    const onCloseMock = vi.fn();
    shipmentService.getCustomerDeliveryPin.mockResolvedValueOnce(samplePinResponse);

    render(<CustomerDeliveryPinModal isOpen={true} shipment={sampleShipment} onClose={onCloseMock} />);

    await waitFor(() => {
      expect(screen.getByTestId('delivery-pin-value')).toBeInTheDocument();
    });

    const backdrop = screen.getByTestId('delivery-pin-modal');
    fireEvent.click(backdrop);
    expect(onCloseMock).toHaveBeenCalledTimes(1);
  });
});
