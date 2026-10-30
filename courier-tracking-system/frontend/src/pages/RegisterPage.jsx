import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { User, Mail, Lock, Phone, AlertCircle, CheckCircle2, ArrowRight, Package, Loader2 } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export default function RegisterPage() {
  const [formData, setFormData] = useState({
    fullName: '',
    email: '',
    password: '',
    confirmPassword: '',
    phoneNumber: '',
  });

  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);

  const { register } = useAuth();
  const navigate = useNavigate();

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    // Client-side validations
    if (!formData.fullName.trim() || formData.fullName.trim().length < 2) {
      setError('Full name must be at least 2 characters long');
      return;
    }
    if (!formData.email.trim()) {
      setError('Valid email address is required');
      return;
    }
    if (!formData.password || formData.password.length < 8) {
      setError('Password must be at least 8 characters long');
      return;
    }
    if (formData.password !== formData.confirmPassword) {
      setError('Passwords do not match');
      return;
    }

    setLoading(true);

    try {
      await register({
        fullName: formData.fullName.trim(),
        email: formData.email.trim(),
        password: formData.password,
        phoneNumber: formData.phoneNumber.trim() || null,
      });

      setSuccess(true);
    } catch (err) {
      setError(err?.message || 'Registration failed. Please verify your details.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex flex-col items-center justify-center min-h-[75vh] py-8 px-4">
      <div className="w-full max-w-lg bg-white rounded-3xl border border-slate-200 shadow-xl p-8 space-y-6">
        {/* Header */}
        <div className="text-center space-y-2">
          <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-blue-600 to-indigo-600 text-white flex items-center justify-center mx-auto shadow-md">
            <Package className="w-6 h-6" />
          </div>
          <h1 className="text-2xl font-black text-slate-900 tracking-tight">
            Create Customer Account
          </h1>
          <p className="text-xs text-slate-500">
            Sign up to book consignments, generate labels, and track delivery history.
          </p>
        </div>

        {/* Success Notice */}
        {success ? (
          <div
            data-testid="register-success-card"
            className="p-6 bg-emerald-50 border border-emerald-200 rounded-2xl text-center space-y-4"
          >
            <div className="w-12 h-12 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center mx-auto">
              <CheckCircle2 className="w-6 h-6" />
            </div>
            <div className="space-y-1">
              <h3 className="text-base font-bold text-emerald-900">
                Registration Successful!
              </h3>
              <p className="text-xs text-emerald-700">
                Your customer account for <span className="font-semibold">{formData.email}</span> has been created.
              </p>
            </div>
            <button
              onClick={() => navigate('/login')}
              className="w-full py-3 px-4 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold transition-all shadow-md flex items-center justify-center gap-2"
            >
              Proceed to Sign In <ArrowRight className="w-4 h-4" />
            </button>
          </div>
        ) : (
          <>
            {/* Error Alert */}
            {error && (
              <div
                data-testid="register-error-alert"
                className="p-3.5 bg-rose-50 border border-rose-200 rounded-xl flex items-start gap-2.5 text-xs text-rose-800"
              >
                <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                <span className="font-medium">{error}</span>
              </div>
            )}

            {/* Registration Form */}
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700 block">
                  Full Name <span className="text-rose-500">*</span>
                </label>
                <div className="relative flex items-center">
                  <User className="absolute left-3.5 w-4 h-4 text-slate-400 pointer-events-none" />
                  <input
                    type="text"
                    name="fullName"
                    value={formData.fullName}
                    onChange={handleChange}
                    placeholder="Jane Doe"
                    required
                    className="w-full pl-10 pr-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 text-slate-900 text-xs focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500 transition-all font-medium"
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700 block">
                  Email Address <span className="text-rose-500">*</span>
                </label>
                <div className="relative flex items-center">
                  <Mail className="absolute left-3.5 w-4 h-4 text-slate-400 pointer-events-none" />
                  <input
                    type="email"
                    name="email"
                    value={formData.email}
                    onChange={handleChange}
                    placeholder="jane.doe@example.com"
                    autoComplete="email"
                    required
                    className="w-full pl-10 pr-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 text-slate-900 text-xs focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500 transition-all font-medium"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-700 block">
                    Password <span className="text-rose-500">*</span>
                  </label>
                  <div className="relative flex items-center">
                    <Lock className="absolute left-3.5 w-4 h-4 text-slate-400 pointer-events-none" />
                    <input
                      type="password"
                      name="password"
                      value={formData.password}
                      onChange={handleChange}
                      placeholder="Min 8 characters"
                      autoComplete="new-password"
                      required
                      className="w-full pl-10 pr-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 text-slate-900 text-xs focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500 transition-all font-medium"
                    />
                  </div>
                </div>

                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-700 block">
                    Confirm Password <span className="text-rose-500">*</span>
                  </label>
                  <div className="relative flex items-center">
                    <Lock className="absolute left-3.5 w-4 h-4 text-slate-400 pointer-events-none" />
                    <input
                      type="password"
                      name="confirmPassword"
                      value={formData.confirmPassword}
                      onChange={handleChange}
                      placeholder="Re-enter password"
                      autoComplete="new-password"
                      required
                      className="w-full pl-10 pr-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 text-slate-900 text-xs focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500 transition-all font-medium"
                    />
                  </div>
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700 block">
                  Phone Number <span className="text-slate-400 font-normal">(Optional)</span>
                </label>
                <div className="relative flex items-center">
                  <Phone className="absolute left-3.5 w-4 h-4 text-slate-400 pointer-events-none" />
                  <input
                    type="tel"
                    name="phoneNumber"
                    value={formData.phoneNumber}
                    onChange={handleChange}
                    placeholder="+1 555-019-2834"
                    className="w-full pl-10 pr-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50/50 text-slate-900 text-xs focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500 transition-all font-medium"
                  />
                </div>
              </div>

              <button
                type="submit"
                disabled={loading}
                className="w-full py-3 px-4 rounded-xl bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 text-white text-xs font-bold transition-all shadow-md flex items-center justify-center gap-2 disabled:opacity-50"
              >
                {loading ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Registering account...
                  </>
                ) : (
                  <>
                    Register Customer Account <ArrowRight className="w-4 h-4" />
                  </>
                )}
              </button>
            </form>

            <div className="pt-2 border-t border-slate-100 text-center text-xs text-slate-500">
              Already have an account?{' '}
              <Link to="/login" className="font-semibold text-indigo-600 hover:text-indigo-800">
                Sign in here
              </Link>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
