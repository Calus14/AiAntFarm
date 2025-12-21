import React, { useState } from 'react';
import { apiClient } from '../../api/client';
import { Room } from '../../types';

interface CreateRoomModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (newRoom: Room) => void;
}

export const CreateRoomModal: React.FC<CreateRoomModalProps> = ({ isOpen, onClose, onSuccess }) => {
  const [name, setName] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;

    setIsLoading(true);
    setError('');

    try {
      // Assuming the endpoint returns the created room object
      const res = await apiClient.post('/api/v1/rooms', { name: name.trim() });
      onSuccess(res.data);
      setName('');
      onClose();
    } catch (err: any) {
      console.error('Failed to create room', err);
      setError(err.response?.data?.message || 'Failed to create channel');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div 
        className="absolute inset-0 bg-black/60 backdrop-blur-sm transition-opacity" 
        onClick={onClose}
      />

      {/* Modal Content */}
      <div className="relative w-full max-w-md bg-theme-panel border border-white/10 rounded-2xl shadow-2xl overflow-hidden animate-fade-in-up">
        <div className="p-6">
          <h2 className="text-2xl font-bold text-white mb-2">Create Channel</h2>
          <p className="text-theme-muted text-sm mb-6">Give your new channel a name to get started.</p>

          {error && (
            <div className="bg-red-500/10 border border-red-500/50 text-red-400 p-3 rounded-lg text-sm mb-4">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit}>
            <div className="mb-6">
              <label className="block text-xs font-bold text-theme-muted uppercase tracking-wider mb-2">
                Channel Name
              </label>
              <div className="relative">
                <span className="absolute left-3 top-3 text-theme-muted">#</span>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="new-channel"
                  className="w-full bg-theme-base/50 border border-theme-lighter rounded-xl py-2.5 pl-8 pr-3 text-white focus:outline-none focus:border-theme-primary focus:ring-1 focus:ring-theme-primary transition-all"
                  autoFocus
                />
              </div>
            </div>

            <div className="flex justify-end space-x-3">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 rounded-xl text-theme-text hover:bg-theme-lighter transition-colors font-medium text-sm"
              >
                Cancel
              </button>
                <button
                  type="submit"
                  disabled={isLoading || !name.trim()}
                  className="px-6 py-2 rounded-xl bg-linear-to-r from-theme-primary to-theme-secondary text-white font-bold text-sm shadow-lg shadow-theme-primary/20 hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed flex items-center"
              >
                {isLoading ? (
                  <>
                    <svg className="animate-spin -ml-1 mr-2 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                    Creating...
                  </>
                ) : (
                  'Create Channel'
                )}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
};
