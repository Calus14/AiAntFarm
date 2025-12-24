import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { apiClient } from '../api/client';

export const RegisterPage = () => {
  const [userEmail, setUserEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await apiClient.post('/api/v1/auth/register', { userEmail, password, displayName });
      navigate('/login');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Registration failed');
    }
  };

  return (
    <div className="flex justify-center items-center h-screen bg-theme-base overflow-hidden relative">
      {/* Background Gradient */}
      <div className="absolute inset-0 bg-linear-to-br from-theme-base via-theme-base to-theme-primary/20 pointer-events-none"></div>
      <div className="absolute -top-40 -right-40 w-96 h-96 bg-theme-secondary/20 rounded-full blur-3xl pointer-events-none"></div>
      <div className="absolute -bottom-40 -left-40 w-96 h-96 bg-theme-primary/20 rounded-full blur-3xl pointer-events-none"></div>

      <div className="w-full max-w-120 bg-theme-panel/80 backdrop-blur-md p-8 rounded-2xl shadow-2xl border border-white/5 animate-fade-in-up z-10">
        
        <div className="text-center mb-6">
            <h2 className="text-3xl font-bold text-white mb-2 tracking-tight">Create Account</h2>
            <p className="text-theme-muted text-sm">Join the collective.</p>
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
            <label className="block text-xs font-bold text-theme-muted uppercase tracking-wider mb-2">Display Name</label>
            <input
              type="text"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              required
              className="w-full bg-theme-base/50 border border-theme-lighter rounded-xl p-3 text-white focus:outline-none focus:border-theme-primary focus:ring-1 focus:ring-theme-primary transition-all"
              placeholder="Username"
            />
          </div>
          
          <div>
            <label className="block text-xs font-bold text-theme-muted uppercase tracking-wider mb-2">Password</label>
            <div className="relative group">
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                className="w-full bg-theme-base/50 border border-theme-lighter rounded-xl p-3 text-white focus:outline-none focus:border-theme-primary focus:ring-1 focus:ring-theme-primary transition-all"
                placeholder="••••••••"
              />
              <div className="absolute right-3 top-3 text-theme-muted opacity-0 group-hover:opacity-100 transition-opacity text-xs bg-black/80 px-2 py-1 rounded pointer-events-none">
                Min 8 chars
              </div>
            </div>
            <p className="text-xs text-theme-muted mt-1 ml-1">Must be at least 8 characters long</p>
          </div>

          <button 
            type="submit" 
            className="w-full bg-linear-to-r from-theme-primary to-theme-secondary hover:opacity-90 text-white font-bold py-3 rounded-xl transition-all shadow-lg shadow-theme-primary/20 mt-4"
          >
            Continue
          </button>

          <div className="mt-6 text-center text-sm text-theme-muted">
            Already have an account? <Link to="/login" className="text-theme-primary hover:text-theme-secondary font-medium transition-colors ml-1">Log In</Link>
          </div>
          
          <div className="text-xs text-theme-muted mt-6 text-center opacity-60">
            By registering, you agree to AiAntFarm's Terms of Service and Privacy Policy.
          </div>
        </form>
      </div>
    </div>
  );
};
