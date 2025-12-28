import axios from 'axios';

const DEFAULT_API_BASE_URL = import.meta.env.VITE_API_BASE || 'http://localhost:9000';

// Prefer runtime config (served from the same origin as the SPA) so we can change API endpoints
// without rebuilding the frontend bundle.
// Terraform writes this file to the frontend bucket as /config.json.
type RuntimeConfig = { apiBaseUrl?: string };

function normalizeBaseUrl(u: string) {
  return u.replace(/\/+$/, '');
}

function readRuntimeApiBaseUrl(): string | null {
  try {
    const w = window as any;
    const cfg: RuntimeConfig | undefined = w.__AI_ANTFARM_CONFIG__;
    if (cfg?.apiBaseUrl && typeof cfg.apiBaseUrl === 'string') {
      return normalizeBaseUrl(cfg.apiBaseUrl);
    }
  } catch {
    // ignore
  }
  return null;
}

function readLocalOverride(): string | null {
  // handy for debugging without rebuild/restart
  try {
    const v = localStorage.getItem('AI_ANTFARM_API_BASE_URL');
    return v ? normalizeBaseUrl(v) : null;
  } catch {
    return null;
  }
}

const runtimeApiBase = readRuntimeApiBaseUrl();
const localOverride = readLocalOverride();

export const API_BASE_URL = localOverride || runtimeApiBase || DEFAULT_API_BASE_URL;

// Tiny debug breadcrumb so it's obvious why the app is pointing to some URL.
// (This shows in the devtools console.)
if (import.meta.env.DEV) {
  // eslint-disable-next-line no-console
  console.log('[AiAntFarm] API_BASE_URL =', API_BASE_URL, {
    localOverride: localOverride ?? undefined,
    runtimeConfig: runtimeApiBase ?? undefined,
    env: import.meta.env.VITE_API_BASE ?? undefined,
  });
}

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
