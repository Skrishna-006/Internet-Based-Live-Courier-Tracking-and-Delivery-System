import React, { useState, useEffect, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Package,
  Send,
  MapPin,
  Phone,
  User,
  DollarSign,
  AlertCircle,
  CheckCircle2,
  Loader2,
  ArrowRight,
  ExternalLink,
  Copy,
  Check,
  RotateCcw,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { shipmentService } from '../services/api';

const PHONE_REGEX = /^[+0-9\-\s()]{7,30}$/;

export default function BookingPage() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    senderName: user?.fullName || '',
    senderAddress: '',
    senderPhone: user?.phoneNumber || '',
    recipientName: '',
    recipientAddress: '',
    recipientPhone: '',
    weightKg: '',
    recipientLatitude: '',
    recipientLongitude: '',
  });

  const [quote, setQuote] = useState(null);
  const [quoteLoading, setQuoteLoading] = useState(false);
  const [quoteError, setQuoteError] = useState(null);

  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState(null);
  const [confirmedShipment, setConfirmedShipment] = useState(null);
  const [copiedTracking, setCopiedTracking] = useState(false);

  const debounceTimerRef = useRef(null);

  // Update initial sender details when auth user loads
  useEffect(() => {
    if (user) {
      setFormData((prev) => ({
        ...prev,
        senderName: prev.senderName || user.fullName || '',
        senderPhone: prev.senderPhone || user.phoneNumber || '',
      }));
    }
  }, [user]);

  // Debounced quote estimation based on package weight
  useEffect(() => {
    const rawWeight = formData.weightKg.trim();
    if (!rawWeight) {
      setQuote(null);
      setQuoteError(null);
      setQuoteLoading(false);
      return;
    }

    const weightNum = parseFloat(rawWeight);
    if (isNaN(weightNum) || weightNum < 0.05 || weightNum > 1000) {
      setQuote(null);
      setQuoteError('Weight must be between 0.050 kg (50g) and 1,000 kg.');
      setQuoteLoading(false);
      return;
    }

    setQuoteError(null);
    setQuoteLoading(true);

    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }

    debounceTimerRef.current = setTimeout(async () => {
      try {
        const res = await shipmentService.getPriceQuote(weightNum);
        if (res.success && res.data) {
          setQuote(res.data);
        } else {
          setQuote(null);
          setQuoteError(res.message || 'Unable to fetch price estimate');
        }
      } catch (err) {
        setQuote(null);
        setQuoteError(err.message || 'Error communicating with pricing service');
      } finally {
        setQuoteLoading(false);
      }
    }, 400);

    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
    };
  }, [formData.weightKg]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleCopyTracking = (trackingNum) => {
    navigator.clipboard?.writeText(trackingNum);
    setCopiedTracking(true);
    setTimeout(() => setCopiedTracking(false), 2000);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFormError(null);

    // Validation
    const senderName = formData.senderName.trim();
    const senderAddress = formData.senderAddress.trim();
    const senderPhone = formData.senderPhone.trim();
    const recipientName = formData.recipientName.trim();
    const recipientAddress = formData.recipientAddress.trim();
    const recipientPhone = formData.recipientPhone.trim();
    const weightNum = parseFloat(formData.weightKg.trim());

    if (senderName.length < 2 || senderName.length > 120) {
      setFormError('Sender name must be between 2 and 120 characters.');
      return;
    }
    if (senderAddress.length < 5 || senderAddress.length > 1000) {
      setFormError('Sender address must be between 5 and 1000 characters.');
      return;
    }
    if (!PHONE_REGEX.test(senderPhone)) {
      setFormError('Sender phone number format is invalid.');
      return;
    }
    if (recipientName.length < 2 || recipientName.length > 120) {
      setFormError('Recipient name must be between 2 and 120 characters.');
      return;
    }
    if (recipientAddress.length < 5 || recipientAddress.length > 1000) {
      setFormError('Recipient address must be between 5 and 1000 characters.');
      return;
    }
    if (!PHONE_REGEX.test(recipientPhone)) {
      setFormError('Recipient phone number format is invalid.');
      return;
    }
    if (isNaN(weightNum) || weightNum < 0.05 || weightNum > 1000) {
      setFormError('Package weight must be between 0.050 kg and 1,000 kg.');
      return;
    }

    // Optional coordinates validation
    let recipientLat = null;
    let recipientLng = null;
    if (formData.recipientLatitude.trim()) {
      const lat = parseFloat(formData.recipientLatitude.trim());
      if (isNaN(lat) || lat < -90 || lat > 90) {
        setFormError('Recipient latitude must be between -90 and 90.');
        return;
      }
      recipientLat = lat;
    }
    if (formData.recipientLongitude.trim()) {
      const lng = parseFloat(formData.recipientLongitude.trim());
      if (isNaN(lng) || lng < -180 || lng > 180) {
        setFormError('Recipient longitude must be between -180 and 180.');
        return;
      }
      recipientLng = lng;
    }

    setSubmitting(true);

    try {
      const payload = {
        senderName,
        senderAddress,
        senderPhone,
        recipientName,
        recipientAddress,
        recipientPhone,
        weightKg: weightNum,
        ...(recipientLat !== null ? { recipientLatitude: recipientLat } : {}),
        ...(recipientLng !== null ? { recipientLongitude: recipientLng } : {}),
      };

      const res = await shipmentService.createShipment(payload);
      if (res.success && res.data) {
        setConfirmedShipment(res.data);
      } else {
        setFormError(res.message || 'Failed to book shipment');
      }
    } catch (err) {
      setFormError(err.message || 'Error occurred while creating shipment booking');
    } finally {
      setSubmitting(false);
    }
  };

  const handleResetForm = () => {
    setConfirmedShipment(null);
    setFormData({
      senderName: user?.fullName || '',
      senderAddress: '',
      senderPhone: user?.phoneNumber || '',
      recipientName: '',
      recipientAddress: '',
      recipientPhone: '',
      weightKg: '',
      recipientLatitude: '',
      recipientLongitude: '',
    });
    setQuote(null);
    setQuoteError(null);
    setFormError(null);
  };

  // If shipment is confirmed, display Waybill Confirmation Card
  if (confirmedShipment) {
    return (
      <div className="max-w-2xl mx-auto space-y-6 text-slate-100" data-testid="waybill-confirmation">
        {/* Success Banner */}
        <div className="bg-emerald-950/30 border border-emerald-500/40 rounded-2xl p-6 text-center space-y-2 shadow-dark-card">
          <div className="w-12 h-12 bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 rounded-full flex items-center justify-center mx-auto">
            <CheckCircle2 className="w-7 h-7" />
          </div>
          <h2 className="text-xl font-bold text-slate-100">Shipment Booked Successfully!</h2>
          <p className="text-xs text-slate-400 max-w-md mx-auto">
            Your courier consignment has been registered in the logistics dispatch network. Keep your tracking number handy.
          </p>
        </div>

        {/* Waybill Card */}
        <div className="bg-navy-850 border border-navy-700/60 rounded-2xl shadow-dark-card overflow-hidden">
          <div className="px-6 py-4 bg-navy-900 text-white flex items-center justify-between border-b border-navy-700/60">
            <div className="flex items-center gap-2">
              <Package className="w-5 h-5 text-accent-cyan" />
              <span className="font-semibold text-sm text-slate-100">Official Consignment Waybill</span>
            </div>
            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-blue-500/20 text-accent-cyan border border-blue-500/30">
              {confirmedShipment.status || 'CREATED'}
            </span>
          </div>

          <div className="p-6 space-y-6">
            {/* Tracking Number Highlight */}
            <div className="bg-navy-900/80 border border-navy-700/60 rounded-xl p-4 flex flex-col sm:flex-row items-center justify-between gap-3">
              <div>
                <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block">
                  Tracking Number
                </span>
                <span
                  data-testid="waybill-tracking-number"
                  className="font-mono text-lg font-bold text-accent-cyan tracking-wider"
                >
                  {confirmedShipment.trackingNumber}
                </span>
              </div>
              <button
                type="button"
                onClick={() => handleCopyTracking(confirmedShipment.trackingNumber)}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-navy-700 bg-navy-800 hover:bg-navy-700 text-xs font-semibold text-slate-200 transition-colors shadow-xs"
              >
                {copiedTracking ? (
                  <>
                    <Check className="w-3.5 h-3.5 text-emerald-400" />
                    <span>Copied!</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-3.5 h-3.5 text-slate-400" />
                    <span>Copy Code</span>
                  </>
                )}
              </button>
            </div>

            {/* Consignment Metrics */}
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-4 pt-1">
              <div className="bg-navy-900/50 p-3 rounded-xl border border-navy-700/50">
                <span className="text-[10px] uppercase font-bold text-slate-400 tracking-wider block">Total Cost</span>
                <span className="text-base font-bold text-slate-100">
                  ${confirmedShipment.price ? Number(confirmedShipment.price).toFixed(2) : '0.00'}{' '}
                  <span className="text-xs text-slate-400 font-medium">{confirmedShipment.currency || 'USD'}</span>
                </span>
              </div>
              <div className="bg-navy-900/50 p-3 rounded-xl border border-navy-700/50">
                <span className="text-[10px] uppercase font-bold text-slate-400 tracking-wider block">Weight</span>
                <span className="text-base font-bold text-slate-100">
                  {confirmedShipment.weightKg} <span className="text-xs text-slate-400 font-medium">kg</span>
                </span>
              </div>
              <div className="bg-navy-900/50 p-3 rounded-xl border border-navy-700/50 col-span-2 sm:col-span-1">
                <span className="text-[10px] uppercase font-bold text-slate-400 tracking-wider block">Booked Date</span>
                <span className="text-xs font-semibold text-slate-300">
                  {confirmedShipment.createdAt
                    ? new Date(confirmedShipment.createdAt).toLocaleDateString(undefined, {
                        month: 'short',
                        day: 'numeric',
                        year: 'numeric',
                      })
                    : 'Today'}
                </span>
              </div>
            </div>

            {/* Sender & Recipient Route */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 border-t border-navy-700/60 pt-5">
              <div className="space-y-1">
                <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider flex items-center gap-1">
                  <User className="w-3.5 h-3.5 text-slate-500" />
                  Origin / Sender
                </span>
                <p className="font-semibold text-sm text-slate-200">{confirmedShipment.senderName}</p>
                <p className="text-xs text-slate-400">{confirmedShipment.senderPhone}</p>
                <p className="text-xs text-slate-300 mt-1 whitespace-pre-wrap">{confirmedShipment.senderAddress}</p>
              </div>

              <div className="space-y-1">
                <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider flex items-center gap-1">
                  <MapPin className="w-3.5 h-3.5 text-accent-blue" />
                  Destination / Recipient
                </span>
                <p className="font-semibold text-sm text-slate-200">{confirmedShipment.recipientName}</p>
                <p className="text-xs text-slate-400">{confirmedShipment.recipientPhone}</p>
                <p className="text-xs text-slate-300 mt-1 whitespace-pre-wrap">{confirmedShipment.recipientAddress}</p>
              </div>
            </div>
          </div>

          {/* Action Bar */}
          <div className="px-6 py-4 bg-navy-900/90 border-t border-navy-700/60 flex flex-col sm:flex-row items-center justify-between gap-3">
            <button
              type="button"
              onClick={handleResetForm}
              data-testid="btn-book-another"
              className="w-full sm:w-auto inline-flex items-center justify-center gap-1.5 px-4 py-2 rounded-xl border border-navy-700 bg-navy-800 hover:bg-navy-700 text-xs font-semibold text-slate-200 transition-colors shadow-xs"
            >
              <RotateCcw className="w-3.5 h-3.5 text-slate-400" />
              Book Another Parcel
            </button>

            <div className="w-full sm:w-auto flex items-center gap-2">
              <Link
                to="/customer/dashboard"
                data-testid="btn-go-dashboard"
                className="flex-1 sm:flex-initial inline-flex items-center justify-center gap-1.5 px-4 py-2 rounded-xl border border-navy-700 bg-navy-800 hover:bg-navy-700 text-xs font-semibold text-slate-200 transition-colors shadow-xs"
              >
                My Shipments
              </Link>
              <Link
                to={`/track/${confirmedShipment.trackingNumber}`}
                data-testid="btn-track-live"
                className="flex-1 sm:flex-initial inline-flex items-center justify-center gap-1.5 px-4 py-2 rounded-xl bg-accent-blue hover:bg-blue-600 text-white text-xs font-semibold transition-colors shadow-sm"
              >
                Track Live Map
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto space-y-6 text-slate-100">
      {/* Page Header */}
      <div>
        <div className="flex items-center gap-2 text-accent-blue font-semibold text-xs tracking-wider uppercase mb-1">
          <Package className="w-4 h-4" />
          <span>New Consignment</span>
        </div>
        <h1 className="text-2xl font-bold text-slate-100 tracking-tight">Book Courier Shipment</h1>
        <p className="text-xs text-slate-400 mt-1">
          Provide origin and destination contact details, enter parcel weight for an authoritative server price estimate, and confirm booking.
        </p>
      </div>

      {/* Error Alert */}
      {formError && (
        <div
          role="alert"
          className="p-4 rounded-xl bg-rose-950/40 border border-rose-500/40 text-rose-300 text-xs flex items-start gap-2.5 animate-in fade-in"
        >
          <AlertCircle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
          <div>
            <span className="font-bold">Booking Validation Error:</span> {formError}
          </div>
        </div>
      )}

      {/* Booking Form */}
      <form onSubmit={handleSubmit} className="space-y-6">
        {/* Step 1: Origin / Sender Details */}
        <div className="bg-navy-850 border border-navy-700/60 rounded-2xl p-6 shadow-dark-card space-y-4">
          <div className="flex items-center gap-2 pb-2 border-b border-navy-700/60">
            <User className="w-4 h-4 text-accent-blue" />
            <h2 className="text-sm font-bold text-slate-100 uppercase tracking-wider">1. Sender (Origin) Information</h2>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label htmlFor="senderName" className="block text-xs font-semibold text-slate-300 mb-1">
                Sender Full Name <span className="text-rose-400">*</span>
              </label>
              <input
                type="text"
                id="senderName"
                name="senderName"
                data-testid="input-sender-name"
                value={formData.senderName}
                onChange={handleChange}
                placeholder="e.g. Alice Johnson"
                required
                className="w-full px-3 py-2 text-xs border border-navy-700 bg-navy-900 text-slate-100 placeholder-slate-500 rounded-xl focus:ring-2 focus:ring-accent-blue/30 focus:border-accent-blue outline-none transition-all"
              />
            </div>

            <div>
              <label htmlFor="senderPhone" className="block text-xs font-semibold text-slate-300 mb-1">
                Sender Contact Phone <span className="text-rose-400">*</span>
              </label>
              <input
                type="tel"
                id="senderPhone"
                name="senderPhone"
                data-testid="input-sender-phone"
                value={formData.senderPhone}
                onChange={handleChange}
                placeholder="+1-555-0199"
                required
                className="w-full px-3 py-2 text-xs border border-navy-700 bg-navy-900 text-slate-100 placeholder-slate-500 rounded-xl focus:ring-2 focus:ring-accent-blue/30 focus:border-accent-blue outline-none transition-all"
              />
            </div>
          </div>

          <div>
            <label htmlFor="senderAddress" className="block text-xs font-semibold text-slate-300 mb-1">
              Pickup / Origin Street Address <span className="text-rose-400">*</span>
            </label>
            <textarea
              id="senderAddress"
              name="senderAddress"
              data-testid="input-sender-address"
              value={formData.senderAddress}
              onChange={handleChange}
              rows={2}
              placeholder="e.g. 100 Main St, Warehouse Bay 4, Seattle, WA"
              required
              className="w-full px-3 py-2 text-xs border border-navy-700 bg-navy-900 text-slate-100 placeholder-slate-500 rounded-xl focus:ring-2 focus:ring-accent-blue/30 focus:border-accent-blue outline-none transition-all resize-none"
            />
          </div>
        </div>

        {/* Step 2: Destination / Recipient Details */}
        <div className="bg-navy-850 border border-navy-700/60 rounded-2xl p-6 shadow-dark-card space-y-4">
          <div className="flex items-center gap-2 pb-2 border-b border-navy-700/60">
            <MapPin className="w-4 h-4 text-accent-cyan" />
            <h2 className="text-sm font-bold text-slate-100 uppercase tracking-wider">2. Recipient (Destination) Information</h2>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label htmlFor="recipientName" className="block text-xs font-semibold text-slate-300 mb-1">
                Recipient Full Name <span className="text-rose-400">*</span>
              </label>
              <input
                type="text"
                id="recipientName"
                name="recipientName"
                data-testid="input-recipient-name"
                value={formData.recipientName}
                onChange={handleChange}
                placeholder="e.g. Bob Smith"
                required
                className="w-full px-3 py-2 text-xs border border-navy-700 bg-navy-900 text-slate-100 placeholder-slate-500 rounded-xl focus:ring-2 focus:ring-accent-blue/30 focus:border-accent-blue outline-none transition-all"
              />
            </div>

            <div>
              <label htmlFor="recipientPhone" className="block text-xs font-semibold text-slate-300 mb-1">
                Recipient Contact Phone <span className="text-rose-400">*</span>
              </label>
              <input
                type="tel"
                id="recipientPhone"
                name="recipientPhone"
                data-testid="input-recipient-phone"
                value={formData.recipientPhone}
                onChange={handleChange}
                placeholder="+1-555-0144"
                required
                className="w-full px-3 py-2 text-xs border border-navy-700 bg-navy-900 text-slate-100 placeholder-slate-500 rounded-xl focus:ring-2 focus:ring-accent-blue/30 focus:border-accent-blue outline-none transition-all"
              />
            </div>
          </div>

          <div>
            <label htmlFor="recipientAddress" className="block text-xs font-semibold text-slate-300 mb-1">
              Delivery Destination Street Address <span className="text-rose-400">*</span>
            </label>
            <textarea
              id="recipientAddress"
              name="recipientAddress"
              data-testid="input-recipient-address"
              value={formData.recipientAddress}
              onChange={handleChange}
              rows={2}
              placeholder="e.g. 742 Evergreen Terrace, Springfield, OR"
              required
              className="w-full px-3 py-2 text-xs border border-navy-700 bg-navy-900 text-slate-100 placeholder-slate-500 rounded-xl focus:ring-2 focus:ring-accent-blue/30 focus:border-accent-blue outline-none transition-all resize-none"
            />
          </div>

          {/* Optional Coordinates */}
          <div className="pt-2">
            <span className="text-[11px] font-semibold text-slate-400 block mb-2">
              Geographic Coordinates (Optional for Map Pins)
            </span>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label htmlFor="recipientLatitude" className="block text-[11px] text-slate-400 mb-1">
                  Recipient Latitude (-90 to 90)
                </label>
                <input
                  type="number"
                  step="0.000001"
                  id="recipientLatitude"
                  name="recipientLatitude"
                  data-testid="input-recipient-lat"
                  value={formData.recipientLatitude}
                  onChange={handleChange}
                  placeholder="e.g. 37.7749"
                  className="w-full px-3 py-1.5 text-xs border border-navy-700 bg-navy-900 text-slate-100 placeholder-slate-500 rounded-xl focus:ring-2 focus:ring-accent-blue/30 focus:border-accent-blue outline-none transition-all"
                />
              </div>

              <div>
                <label htmlFor="recipientLongitude" className="block text-[11px] text-slate-400 mb-1">
                  Recipient Longitude (-180 to 180)
                </label>
                <input
                  type="number"
                  step="0.000001"
                  id="recipientLongitude"
                  name="recipientLongitude"
                  data-testid="input-recipient-lng"
                  value={formData.recipientLongitude}
                  onChange={handleChange}
                  placeholder="e.g. -122.4194"
                  className="w-full px-3 py-1.5 text-xs border border-navy-700 bg-navy-900 text-slate-100 placeholder-slate-500 rounded-xl focus:ring-2 focus:ring-accent-blue/30 focus:border-accent-blue outline-none transition-all"
                />
              </div>
            </div>
          </div>
        </div>

        {/* Step 3: Package Specs & Authoritative Price Quote */}
        <div className="bg-navy-850 border border-navy-700/60 rounded-2xl p-6 shadow-dark-card space-y-4">
          <div className="flex items-center gap-2 pb-2 border-b border-navy-700/60">
            <Package className="w-4 h-4 text-emerald-400" />
            <h2 className="text-sm font-bold text-slate-100 uppercase tracking-wider">
              3. Parcel Specifications & Delivery Quote
            </h2>
          </div>

          <div>
            <label htmlFor="weightKg" className="block text-xs font-semibold text-slate-300 mb-1">
              Package Weight (Kilograms) <span className="text-rose-400">*</span>
            </label>
            <div className="relative">
              <input
                type="number"
                step="0.001"
                min="0.050"
                max="1000.000"
                id="weightKg"
                name="weightKg"
                data-testid="input-package-weight"
                value={formData.weightKg}
                onChange={handleChange}
                placeholder="e.g. 2.500"
                required
                className="w-full px-3 py-2 text-xs border border-navy-700 bg-navy-900 text-slate-100 placeholder-slate-500 rounded-xl focus:ring-2 focus:ring-accent-blue/30 focus:border-accent-blue outline-none transition-all pr-12"
              />
              <span className="absolute right-3 top-2 text-xs font-bold text-slate-500">kg</span>
            </div>
            <p className="text-[11px] text-slate-500 mt-1">
              Min: 0.050 kg (50 grams) • Max: 1,000.000 kg. Real-time authoritative price estimate updates automatically.
            </p>
          </div>

          {/* Authoritative Price Quote Box */}
          <div
            data-testid="price-estimate-box"
            className="rounded-xl border border-cyan-500/30 bg-navy-900/80 p-4 transition-all"
          >
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-accent-cyan uppercase tracking-wider flex items-center gap-1.5">
                <DollarSign className="w-4 h-4 text-accent-blue" />
                Authoritative Delivery Quote
              </span>
              {quoteLoading && (
                <div data-testid="price-calculating" className="flex items-center gap-1.5 text-xs text-accent-blue">
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  <span>Calculating quote...</span>
                </div>
              )}
            </div>

            {quoteError && (
              <p className="text-xs text-rose-400 mt-2 flex items-center gap-1">
                <AlertCircle className="w-3.5 h-3.5" />
                {quoteError}
              </p>
            )}

            {quote && !quoteLoading && (
              <div className="mt-3 pt-3 border-t border-navy-700/60 flex flex-col sm:flex-row sm:items-baseline justify-between gap-2">
                <div>
                  <div className="text-2xl font-extrabold text-accent-cyan">
                    ${Number(quote.price).toFixed(2)}{' '}
                    <span className="text-xs font-semibold text-slate-300">{quote.currency}</span>
                  </div>
                  <p className="text-[11px] text-slate-400 mt-0.5">
                    Server-calculated: Base Rate ${Number(quote.baseRate).toFixed(2)} + ($
                    {Number(quote.perKgRate).toFixed(2)}/kg × {quote.weightKg} kg)
                  </p>
                </div>
                <div className="text-[10px] font-semibold text-emerald-400 bg-emerald-500/10 border border-emerald-500/30 px-2 py-0.5 rounded-full inline-block self-start sm:self-auto">
                  Authoritative Quote Locked
                </div>
              </div>
            )}

            {!quote && !quoteLoading && !quoteError && (
              <p className="text-xs text-slate-400 mt-2">
                Enter package weight above to view the exact server-calculated delivery quote.
              </p>
            )}
          </div>
        </div>

        {/* Submit Actions */}
        <div className="flex items-center justify-end gap-3 pt-2">
          <Link
            to="/customer/dashboard"
            className="px-4 py-2 text-xs font-semibold text-slate-400 hover:text-slate-200 transition-colors"
          >
            Cancel
          </Link>
          <button
            type="submit"
            data-testid="btn-submit-booking"
            disabled={submitting || quoteLoading || !!quoteError || !formData.weightKg}
            className="inline-flex items-center gap-2 px-6 py-2.5 rounded-xl bg-accent-blue hover:bg-blue-600 text-white text-xs font-bold transition-all shadow-sm disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {submitting ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                <span>Confirming Booking...</span>
              </>
            ) : (
              <>
                <Send className="w-4 h-4" />
                <span>Confirm & Book Shipment</span>
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  );
}
