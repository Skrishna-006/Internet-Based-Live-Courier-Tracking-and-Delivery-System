import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10000,
});

// Request interceptor for JWT authentication if present
api.interceptors.request.use(
  (config) => {
    try {
      const token = localStorage.getItem('token');
      if (token && !config.headers.Authorization) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    } catch {
      // In SSR or non-standard environments
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Interceptor for uniform response unwrapping & error handling
api.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const errorPayload = error.response?.data || {
      success: false,
      message: error.message || 'Network communication error',
    };
    return Promise.reject(errorPayload);
  }
);

export const systemService = {
  getHealth: () => api.get('/health'),
};

export const authService = {
  login: (credentials) => api.post('/auth/login', credentials),
  registerCustomer: (registrationData) => api.post('/auth/register', registrationData),
};

export const trackingService = {
  getPublicTracking: (trackingNumber) =>
    api.get(`/tracking/public/${encodeURIComponent(trackingNumber)}`),
  getPublicLiveLocation: (trackingNumber) =>
    api.get(`/tracking/public/${encodeURIComponent(trackingNumber)}/location`),
};

export const shipmentService = {
  getPriceQuote: (weightKg) =>
    api.get('/shipments/quote', { params: { weightKg } }),
  createShipment: (shipmentData) =>
    api.post('/shipments', shipmentData),
  getCustomerShipments: (params = {}) =>
    api.get('/shipments', { params }),
  getShipmentById: (id) =>
    api.get(`/shipments/${id}`),
  updateStatus: (id, statusData) =>
    api.patch(`/shipments/${id}/status`, statusData),
  sendLiveLocation: (id, locationData) =>
    api.post(`/shipments/${id}/location`, locationData),
  generateDeliveryOtp: (id) =>
    api.post(`/shipments/${id}/delivery-otp`),
  verifyDeliveryOtp: (id, verificationData) =>
    api.post(`/shipments/${id}/delivery/verify-otp`, verificationData),
  getCustomerDeliveryPin: (id) =>
    api.get(`/shipments/${id}/delivery-pin`),
};

export const agentService = {
  getMyAssignments: (params = {}) =>
    api.get('/agents/me/assignments', { params }),
  getMySummary: () =>
    api.get('/agents/me/assignments/summary'),
  getAssignmentById: (id) =>
    api.get(`/agents/me/assignments/${id}`),
};

export const adminService = {
  getOverview: () =>
    api.get('/admin/overview'),
  getShipments: (params = {}) =>
    api.get('/admin/shipments', { params }),
  getShipmentById: (id) =>
    api.get(`/admin/shipments/${id}`),
  getShipmentEvents: (id) =>
    api.get(`/admin/shipments/${id}/events`),
  getAgents: (params = {}) =>
    api.get('/admin/agents', { params }),
  assignAgent: (shipmentId, agentId) =>
    api.patch(`/shipments/${shipmentId}/assign`, { agentId }),
  getFleet: () =>
    api.get('/admin/fleet'),
};

export default api;
