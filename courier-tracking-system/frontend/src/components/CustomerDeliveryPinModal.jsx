import React, { useState, useEffect, useCallback } from 'react';
import {
  Key,
  ShieldCheck,
  Clock,
  X,
  Copy,
  Check,
  AlertCircle,
  RefreshCw,
} from 'lucide-react';
import { shipmentService } from '../services/api';

/**
 * Modal dialog for authenticated customer to view the delivery OTP / PIN.
 * Securely retrieves the active PIN on demand for an OUT_FOR_DELIVERY shipment.
 *
 * @param {Object} props
 * @param {boolean} props.isOpen
 * @param {Object} props.shipment
 * @param {Function} props.onClose
 */
export default function CustomerDeliveryPinModal({ isOpen, shipment, onClose }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [pinData, setPinData] = useState(null);
  const [copied, setCopied] = useState(false);

  const fetchPin = useCallback(async () => {
    if (!shipment?.id) return;
    setLoading(true);
    setError(null);
    setCopied(false);

    try {
      const res = await shipmentService.getCustomerDeliveryPin(shipment.id);
      if (res && res.success && res.data) {
        setPinData(res.data);
      } else {
        setError(res?.message || 'Failed to retrieve delivery PIN');
      }
    } catch (err) {
      setError(err?.message || 'Error communicating with delivery PIN service');
    } finally {
      setLoading(false);
    }
  }, [shipment?.id]);

  useEffect(() => {
    if (isOpen && shipment?.id) {
      fetchPin();
    } else {
      setPinData(null);
      setError(null);
      setLoading(false);
      setCopied(false);
    }
  }, [isOpen, shipment?.id, fetchPin]);

  // Handle ESC key press
  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  const handleCopy = () => {
    if (pinData?.deliveryPin) {
      navigator.clipboard?.writeText(pinData.deliveryPin);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const formatExpiresAt = (isoString) => {
    if (!isoString) return '';
    try {
      const d = new Date(isoString);
      if (isNaN(d.getTime())) return isoString;
      return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    } catch {
      return isoString;
    }
  };

  if (!isOpen || !shipment) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="delivery-pin-modal-title"
      data-testid="delivery-pin-modal"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-md animate-in fade-in duration-150"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="bg-[#121C2E] rounded-2xl shadow-dark-elevated border border-[#263449] w-full max-w-md overflow-hidden animate-in zoom-in-95 duration-150">
        {/* Modal Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#263449] bg-[#0D1424]">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 flex items-center justify-center shadow-xs">
              <Key className="w-5 h-5 text-emerald-400" />
            </div>
            <div>
              <h3 id="delivery-pin-modal-title" className="text-sm font-bold text-[#F8FAFC]">
                Delivery Handover PIN
              </h3>
              <p className="font-mono text-xs text-cyan-400 font-semibold">
                {shipment.trackingNumber}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            data-testid="close-pin-modal-btn"
            aria-label="Close modal"
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-[#18243A] transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-6 space-y-4">
          {/* Loading State */}
          {loading && (
            <div
              data-testid="pin-loading-state"
              className="py-10 text-center space-y-3"
            >
              <RefreshCw className="w-7 h-7 animate-spin text-emerald-400 mx-auto" />
              <p className="text-xs font-semibold text-slate-400">
                Retrieving active delivery PIN...
              </p>
            </div>
          )}

          {/* Error State */}
          {!loading && error && (
            <div
              role="alert"
              data-testid="pin-error-state"
              className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 space-y-3"
            >
              <div className="flex items-start gap-2.5 text-xs text-rose-300">
                <AlertCircle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
                <span data-testid="pin-error-message">{error}</span>
              </div>
              <div className="flex items-center justify-end gap-2 pt-1">
                <button
                  type="button"
                  onClick={fetchPin}
                  data-testid="retry-fetch-pin-btn"
                  className="px-3 py-1.5 rounded-lg bg-rose-500/20 hover:bg-rose-500/30 border border-rose-500/40 font-bold text-xs text-rose-200 transition-colors"
                >
                  Retry
                </button>
              </div>
            </div>
          )}

          {/* Success State */}
          {!loading && !error && pinData && (
            <div className="space-y-4">
              {/* PIN Display Card */}
              <div className="bg-[#0D1424] border border-[#263449] rounded-xl p-5 text-center space-y-2 shadow-inner">
                <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400 block">
                  One-Time Delivery PIN
                </span>
                <div
                  data-testid="delivery-pin-value"
                  className="font-mono text-3xl sm:text-4xl font-extrabold tracking-[0.25em] text-emerald-400 select-all py-1 drop-shadow-md"
                >
                  {pinData.deliveryPin}
                </div>
                <div className="pt-1">
                  <button
                    type="button"
                    onClick={handleCopy}
                    data-testid="copy-pin-btn"
                    className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-[#18243A] border border-[#263449] hover:border-slate-500 text-slate-200 text-xs font-semibold transition-colors shadow-dark-sm"
                  >
                    {copied ? (
                      <>
                        <Check className="w-3.5 h-3.5 text-emerald-400" />
                        <span className="text-emerald-400 font-bold">Copied!</span>
                      </>
                    ) : (
                      <>
                        <Copy className="w-3.5 h-3.5 text-slate-400" />
                        <span>Copy PIN</span>
                      </>
                    )}
                  </button>
                </div>
              </div>

              {/* Expiration Info */}
              {pinData.expiresAt && (
                <div
                  data-testid="delivery-pin-expires"
                  className="flex items-center justify-center gap-1.5 text-xs text-amber-300 bg-amber-500/10 border border-amber-500/30 rounded-xl py-2 px-3 font-medium"
                >
                  <Clock className="w-4 h-4 text-amber-400 shrink-0" />
                  <span>
                    Valid until <span className="font-bold">{formatExpiresAt(pinData.expiresAt)}</span>
                  </span>
                </div>
              )}

              {/* Instructions / Security notice */}
              <div className="flex items-start gap-2.5 p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-300 text-xs leading-relaxed">
                <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                <span>
                  Share this 6-digit PIN verbally with your delivery courier upon handover to confirm receipt of your parcel.
                </span>
              </div>
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="px-6 py-3.5 border-t border-[#263449] bg-[#0D1424] flex justify-end">
          <button
            type="button"
            onClick={onClose}
            data-testid="dismiss-pin-modal-btn"
            className="w-full sm:w-auto px-4 py-2 rounded-xl bg-[#18243A] hover:bg-[#263449] border border-[#263449] text-[#F8FAFC] font-semibold text-xs transition-colors shadow-dark-sm"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
