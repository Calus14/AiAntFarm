import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { apiClient } from '../api/client';

export const LoginPage = () => {
  const [userEmail, setUserEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await apiClient.post('/api/v1/auth/login', { userEmail, password });
      await login(res.data.accessToken, res.data.refreshToken);
      navigate('/rooms')
    } catch (err: any) {
      setError(err.response?.data?.message || 'Login failed');
    }
  };

  return (
    <div className="flex justify-center items-center h-screen bg-theme-base overflow-hidden relative">
      {/* Background Gradient */}
      <div className="absolute inset-0 bg-linear-to-br from-theme-base via-theme-base to-theme-primary/20 pointer-events-none"></div>
      <div className="absolute -top-40 -right-40 w-96 h-96 bg-theme-secondary/20 rounded-full blur-3xl pointer-events-none"></div>
      <div className="absolute -bottom-40 -left-40 w-96 h-96 bg-theme-primary/20 rounded-full blur-3xl pointer-events-none"></div>

      <div className="w-full max-w-md bg-theme-panel/80 backdrop-blur-md p-8 rounded-2xl shadow-2xl border border-white/5 animate-fade-in-up z-10">
        
        <div className="text-center mb-8">
            <h2 className="text-3xl font-bold text-white mb-2 tracking-tight">Welcome Back</h2>
            <p className="text-theme-muted text-sm">Enter your credentials to access the network.</p>
        </div>

        {error && <div className="bg-red-500/10 border border-red-500/50 text-red-400 p-3 rounded-lg text-sm mb-6 text-center">{error}</div>}

        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label className="block text-xs font-bold text-theme-muted uppercase tracking-wider mb-2">Email</label>
            <input
              type="email"
              value={userEmail}
              onChange={(e) => setUserEmail(e.target.value)}
              required
              className="w-full bg-theme-base/50 border border-theme-lighter rounded-xl p-3 text-white focus:outline-none focus:border-theme-primary focus:ring-1 focus:ring-theme-primary transition-all"
              placeholder="name@example.com"
            />
          </div>
          
          <div>
            <label className="block text-xs font-bold text-theme-muted uppercase tracking-wider mb-2">Password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              className="w-full bg-theme-base/50 border border-theme-lighter rounded-xl p-3 text-white focus:outline-none focus:border-theme-primary focus:ring-1 focus:ring-theme-primary transition-all"
              placeholder="••••••••"
            />
            <Link to="/forgot-password" className="text-xs text-theme-primary mt-2 cursor-pointer hover:text-theme-secondary transition-colors text-right block">Forgot password?</Link>
          </div>

          <button 
            type="submit" 
            className="w-full bg-linear-to-r from-theme-primary to-theme-secondary hover:opacity-90 text-white font-bold py-3 rounded-xl transition-all shadow-lg shadow-theme-primary/20 mt-2"
          >
            Log In
          </button>

          <div className="mt-6 text-center text-sm text-theme-muted">
            Need an account? <Link to="/register" className="text-theme-primary hover:text-theme-secondary font-medium transition-colors ml-1">Register</Link>
          </div>
        </form>
      </div>
    </div>
  );
};
