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

type RefreshResponse = {
  accessToken: string;
  refreshToken: string;
};

let refreshInFlight: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  // If multiple requests fail at once, funnel them through a single refresh call.
  if (refreshInFlight) return refreshInFlight;

  const refreshToken = localStorage.getItem('refreshToken');
  if (!refreshToken) {
    throw new Error('No refresh token');
  }

  refreshInFlight = (async () => {
    try {
      // Use a plain axios instance so this request does not recurse through interceptors.
      const res = await axios.post<RefreshResponse>(
        `${normalizeBaseUrl(API_BASE_URL)}/api/v1/auth/refresh`,
        { refreshToken },
        { headers: { 'Content-Type': 'application/json' } }
      );

      const { accessToken, refreshToken: newRefreshToken } = res.data;
      if (!accessToken || !newRefreshToken) {
        throw new Error('Invalid refresh response');
      }

      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('refreshToken', newRefreshToken);
      return accessToken;
    } finally {
      refreshInFlight = null;
    }
  })();

  return refreshInFlight;
}

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

apiClient.interceptors.response.use(
  (res) => res,
  async (error) => {
    const status = error?.response?.status;
    const originalRequest = error?.config;

    // Guard: nothing to retry.
    if (!originalRequest) {
      return Promise.reject(error);
    }

    // Prevent infinite loops.
    if ((originalRequest as any)._retry) {
      return Promise.reject(error);
    }

    // If access token expired, refresh and retry once.
    // We treat both 401 and 403 as potentially-expired JWT because your backend sometimes returns 403.
    if (status === 401 || status === 403) {
      try {
        (originalRequest as any)._retry = true;
        const newAccessToken = await refreshAccessToken();
        originalRequest.headers = originalRequest.headers || {};
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return apiClient.request(originalRequest);
      } catch (refreshErr) {
        // Refresh failed (expired/invalid refresh token). Clear local auth and let callers redirect.
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        return Promise.reject(refreshErr);
      }
    }

    return Promise.reject(error);
  }
);

export const getStreamUrl = (roomId: string) => {
  return `${API_BASE_URL}/api/v1/rooms/${roomId}/stream`;
};

export const getAuthHeader = () => {
  const token = localStorage.getItem('accessToken');
  return token ? { Authorization: `Bearer ${token}` } : {};
};
