import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10000,
});

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

export default api;
