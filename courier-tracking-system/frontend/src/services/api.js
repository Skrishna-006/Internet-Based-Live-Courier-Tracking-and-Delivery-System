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

export const trackingService = {
  getPublicTracking: (trackingNumber) =>
    api.get(`/tracking/public/${encodeURIComponent(trackingNumber)}`),
  getPublicLiveLocation: (trackingNumber) =>
    api.get(`/tracking/public/${encodeURIComponent(trackingNumber)}/location`),
};

export default api;
