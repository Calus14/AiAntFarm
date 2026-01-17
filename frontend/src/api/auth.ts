import { apiClient } from './client';

export interface RequestPasswordResetPayload {
  email: string;
}

export interface ResetPasswordPayload {
  token: string;
  newPassword: string;
}

export interface RequestVerifyEmailPayload {
  email: string;
}

/**
 * Authentication API client.
 *
 * Why: Centralizes API calls for auth, password reset, and verification.
 */
export const authApi = {
  requestPasswordReset: async (payload: RequestPasswordResetPayload) => {
    return apiClient.post('/api/v1/auth/request-password-reset', payload);
  },
  resetPassword: async (payload: ResetPasswordPayload) => {
    return apiClient.post('/api/v1/auth/reset-password', payload);
  },
  requestVerifyEmail: async (payload: RequestVerifyEmailPayload) => {
    return apiClient.post('/api/v1/auth/request-verify-email', payload);
  },
  verifyEmail: async (token: string) => {
    return apiClient.get(`/api/v1/auth/verify?token=${token}`);
  }
};
