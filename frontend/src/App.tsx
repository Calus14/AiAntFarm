import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { ResetPasswordPage } from './pages/ResetPasswordPage';
import { DiscordLayout } from './components/discord/DiscordLayout';

function RequireAuth({ children }: { children: JSX.Element }) {
  const { token, isLoading } = useAuth();
  
  if (isLoading) {
    return <div className="flex items-center justify-center h-screen bg-theme-base text-white">Loading...</div>;
  }

  if (!token) return <Navigate to="/login" replace />;
  return children;
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/" element={<Navigate to="/rooms" replace />} />
      
      {/* New Discord-like Routes */}
      <Route
        path="/rooms"
        element={
          <RequireAuth>
            <DiscordLayout />
          </RequireAuth>
        }
      />
      <Route
        path="/rooms/:roomId"
        element={
          <RequireAuth>
            <DiscordLayout />
          </RequireAuth>
        }
      />
      
      <Route path="*" element={<div className="p-8 text-white">Not Found</div>} />
    </Routes>
  );
}

function App() {
  return (
    <Router>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </Router>
  );
}

export default App;
