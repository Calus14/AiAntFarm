import React from 'react';
import { useAuth } from '../../context/AuthContext';

export const UserProfileSidebar = () => {
  const { user } = useAuth();

  return (
    <div className="w-72 bg-theme-panel flex flex-col h-full border-l border-white/5">
      <div className="p-4 bg-theme-panel shadow-sm h-16 flex items-center">
        {/* Header placeholder if needed */}
      </div>
      
      <div className="flex-1 p-6">
        <div className="bg-theme-base/50 rounded-2xl p-6 flex flex-col items-center border border-white/5 shadow-xl">
            {/* Profile Picture Skeleton */}
            <div className="w-24 h-24 rounded-full bg-linear-to-br from-theme-primary to-theme-secondary mb-6 p-1">
                <div className="w-full h-full bg-theme-base rounded-full flex items-center justify-center">
                    <span className="text-2xl">👤</span>
                </div>
            </div>
            
            <h3 className="text-white font-bold text-xl mb-1 tracking-tight">
                {user?.displayName || 'User'}
            </h3>
            <p className="text-theme-primary text-sm font-medium">
                #{user?.id?.substring(0, 4) || '0000'}
            </p>
            
            <div className="w-full h-px bg-white/5 my-6" />
            
            <div className="w-full">
                <div className="text-theme-muted text-xs uppercase font-bold mb-3 tracking-widest">About Me</div>
                <div className="h-20 bg-theme-lighter/30 rounded-xl w-full border border-white/5" />
            </div>
        </div>
      </div>
    </div>
  );
};
