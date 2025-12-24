import React, { useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { antApi } from '../../api/ants';
import type { AntDto } from '../../api/dto';
import { startRoomsCachePolling } from '../../api/roomsCache';
import { AntSettingsModal } from './AntSettingsModal';

export const UserProfileSidebar = () => {
  const { user } = useAuth();

  const [ants, setAnts] = useState<AntDto[]>([]);
  const [loadingAnts, setLoadingAnts] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [editAntId, setEditAntId] = useState<string | null>(null);

  const loadMyAnts = async () => {
    setLoadingAnts(true);
    try {
      const res = await antApi.listMine();
      setAnts(res.data.items ?? []);
    } catch (err) {
      console.error('Failed to load my ants', err);
      setAnts([]);
    } finally {
      setLoadingAnts(false);
    }
  };

  useEffect(() => {
    void loadMyAnts();
    const stop = startRoomsCachePolling();
    return () => stop();
  }, []);

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

            <div className="w-full mt-6">
              <div className="flex items-center justify-between mb-3">
                <div className="text-theme-muted text-xs uppercase font-bold tracking-widest">My Ants</div>
                <button
                  onClick={() => setShowCreate(true)}
                  className="text-theme-primary hover:text-theme-secondary transition-colors text-xs font-bold"
                >
                  + Create
                </button>
              </div>

              {loadingAnts ? (
                <div className="text-center text-theme-muted text-sm py-3">Loading…</div>
              ) : ants.length === 0 ? (
                <div className="text-theme-muted text-sm py-3">No ants yet.</div>
              ) : (
                <div className="space-y-2">
                  {ants.map((a) => (
                    <button
                      key={a.id}
                      onClick={() => setEditAntId(a.id)}
                      className="w-full text-left bg-theme-lighter/20 hover:bg-theme-lighter/30 border border-white/5 rounded-xl px-3 py-2 transition-colors"
                    >
                      <div className="flex items-center justify-between gap-3">
                        <div className="text-white font-semibold truncate">{a.name}</div>
                        <div className={`text-[10px] px-2 py-0.5 rounded-full ${a.enabled ? 'bg-green-500/20 text-green-400' : 'bg-yellow-500/20 text-yellow-400'}`}>
                          {a.enabled ? 'Enabled' : 'Paused'}
                        </div>
                      </div>
                      <div className="text-xs text-theme-muted mt-1">Interval: {a.intervalSeconds}s</div>
                    </button>
                  ))}
                </div>
              )}
            </div>
        </div>
      </div>

      <AntSettingsModal
        isOpen={showCreate}
        mode="create"
        onClose={() => setShowCreate(false)}
        onSaved={loadMyAnts}
        onDeleted={loadMyAnts}
      />

      <AntSettingsModal
        isOpen={!!editAntId}
        mode="edit"
        antId={editAntId ?? undefined}
        onClose={() => setEditAntId(null)}
        onSaved={loadMyAnts}
        onDeleted={loadMyAnts}
      />
    </div>
  );
};
