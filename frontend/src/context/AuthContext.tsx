import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { apiClient } from '../api/client';
import { userApi } from '../api/users';
import { User } from '../types';

interface AuthContextType {
  user: User | null;
  token: string | null;
  login: (accessToken: string, refreshToken: string) => void;
  logout: () => void;
  isAuthenticated: boolean;
  isLoading: boolean;
  refreshMe: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<User | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(localStorage.getItem('accessToken'));
  const [refreshToken, setRefreshToken] = useState<string | null>(localStorage.getItem('refreshToken'));
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const logout = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    setAccessToken(null);
    setRefreshToken(null);
    setUser(null);
  };

  const refreshMe = async () => {
    if (!accessToken) return;

    const response = await apiClient.get<User>('/api/v1/auth/me');
    let u = response.data;

    // Fallback: if backend doesn't include limits in /auth/me, fetch dedicated settings.
    if (u?.antLimit == null || u?.antRoomLimit == null) {
      try {
        const settings = await userApi.getMySettings();
        u = { ...u, ...settings.data };
      } catch {
        // ignore; limits will just be unavailable
      }
    }

    setUser(u);
  };

  useEffect(() => {
    const initAuth = async () => {
      if (accessToken) {
        try {
          await refreshMe();
        } catch (error: any) {
          const status = error?.response?.status;
          console.error('Failed to fetch user', error);

          // Only drop tokens if we are truly unauthorized.
          if (status === 401 || status === 403) {
            logout();
          }
        }
      }
      setIsLoading(false);
    };

    void initAuth();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken]);

  const login = (newAccessToken: string, newRefreshToken: string) => {
    localStorage.setItem('accessToken', newAccessToken);
    localStorage.setItem('refreshToken', newRefreshToken);
    setAccessToken(newAccessToken);
    setRefreshToken(newRefreshToken);
  };

  return (
    <AuthContext.Provider value={{ user, token: accessToken, login, logout, isAuthenticated: !!accessToken, isLoading, refreshMe }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
