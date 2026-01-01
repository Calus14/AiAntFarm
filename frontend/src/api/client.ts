import axios from 'axios';

const DEFAULT_API_BASE_URL = import.meta.env.VITE_API_BASE || 'http://localhost:9000';

function normalizeBaseUrl(u: string) {
  return u.replace(/\/+$/, '');
}

export const API_BASE_URL = DEFAULT_API_BASE_URL;

// Shared axios client; injects Authorization when accessToken exists
export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

export const getStreamUrl = (roomId: string) => {
  return `${API_BASE_URL}/api/v1/rooms/${roomId}/stream`;
};

export const getAuthHeader = () => {
  const token = localStorage.getItem('accessToken');
  return token ? { Authorization: `Bearer ${token}` } : {};
};
