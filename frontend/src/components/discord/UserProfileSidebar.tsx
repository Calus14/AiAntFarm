import React, { useEffect, useMemo, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { antApi } from '../../api/ants';
import type { AntDetailDto, AntDto } from '../../api/dto';
import { startRoomsCachePolling } from '../../api/roomsCache';
import { AntSettingsModal } from './AntSettingsModal';
import { DeleteAntModal } from './DeleteAntModal';

type AntUsage = {
  roomIds?: string[];
};

export const UserProfileSidebar: React.FC = () => {
  const { user, logout } = useAuth();

  const [ants, setAnts] = useState<AntDto[]>([]);
  const [antsDetailById, setAntsDetailById] = useState<Record<string, AntDetailDto>>({});
  const [loadingAnts, setLoadingAnts] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [editAntId, setEditAntId] = useState<string | null>(null);
  const [deleteAntModalOpen, setDeleteAntModalOpen] = useState(false);
  const [antToDelete, setAntToDelete] = useState<AntDto | null>(null);

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

  // Prefetch ant details (room assignment counts + weekly usage fields when available)
  useEffect(() => {
    const ids = ants.map((a) => a.id).filter(Boolean);
    if (ids.length === 0) return;

    void Promise.all(ids.map(async (id) => {
      if (antsDetailById[id]) return;
      try {
        const res = await antApi.get(id);
        setAntsDetailById((prev) => ({ ...prev, [id]: res.data }));
      } catch {
        // ignore
      }
    }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ants]);

  const antLimit = user?.antLimit;
  const antRoomLimit = user?.antRoomLimit;

  const currentAntCount = ants.length;
  const atAntLimit = antLimit != null && antLimit > 0 && currentAntCount >= antLimit;

  const antsHeaderText = useMemo(() => {
    if (antLimit == null || antLimit <= 0) return `Ants: ${currentAntCount}`;
    return `Ants: ${currentAntCount} / ${antLimit}`;
  }, [antLimit, currentAntCount]);

  const openDeleteAntModal = (e: React.MouseEvent, ant: AntDto) => {
    e.stopPropagation();
    setAntToDelete(ant);
    setDeleteAntModalOpen(true);
  };

  return (
    <div className="w-full bg-theme-panel flex flex-col h-full border-l border-white/5 min-w-0">
      <div className="p-4 bg-theme-panel shadow-sm h-16 flex items-center justify-end">
        <button
          onClick={logout}
          className="px-3 py-1.5 rounded-lg text-xs font-bold bg-theme-base/40 border border-white/10 text-white hover:bg-theme-base/60 transition-colors"
          title="Log out"
        >
          Log out
        </button>
      </div>

      <div className="flex-1 p-6 overflow-hidden min-w-0">
        <div className="bg-theme-base/50 rounded-2xl p-6 flex flex-col items-center border border-white/5 shadow-xl h-full overflow-hidden min-w-0 w-full">
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
              <div className="text-theme-muted text-xs uppercase font-bold mb-3 tracking-widest">Limits</div>
              <div className="bg-theme-lighter/20 rounded-xl w-full border border-white/5 p-3 space-y-2 text-sm">
                <div
                  className="flex items-center justify-between"
                  title="How many ants you can create. When you hit the limit, you’ll need to delete an ant (or upgrade later) to create another."
                >
                  <span className="text-theme-muted">Ants</span>
                  <span className="text-white font-semibold">{antLimit == null || antLimit <= 0 ? currentAntCount : `${currentAntCount} / ${antLimit}`}</span>
                </div>
                <div
                  className="flex items-center justify-between"
                  title="How many rooms each ant can be assigned to. This is enforced per ant."
                >
                  <span className="text-theme-muted">Rooms per ant</span>
                  <span className="text-white font-semibold">{antRoomLimit == null || antRoomLimit <= 0 ? '—' : antRoomLimit}</span>
                </div>
              </div>
            </div>

            <div className="w-full mt-6 flex flex-col min-h-0">
              <div className="flex items-center justify-between mb-3">
                <div className="text-theme-muted text-xs uppercase font-bold tracking-widest">My Ants</div>
                <button
                  onClick={() => setShowCreate(true)}
                  className="text-theme-primary hover:text-theme-secondary transition-colors text-xs font-bold disabled:opacity-50 disabled:cursor-not-allowed"
                  disabled={atAntLimit}
                  title={atAntLimit ? `Ant limit reached (${antsHeaderText}). Delete an ant to create another.` : 'Create a new ant'}
                >
                  + Create
                </button>
              </div>

              <div className="text-[11px] text-theme-muted mb-2" title="Your limits are enforced by the API. If you hit a limit, actions will be blocked until you free space.">
                {antsHeaderText}
              </div>

              {loadingAnts ? (
                <div className="text-center text-theme-muted text-sm py-3">Loading…</div>
              ) : ants.length === 0 ? (
                <div className="text-theme-muted text-sm py-3">No ants yet.</div>
              ) : (
                <div className="space-y-2 overflow-y-auto min-h-0 pr-1 scrollbar-thin custom-scrollbar">
                  {ants.map((a) => {
                    const d = antsDetailById[a.id];
                    const roomsUsed = d?.roomIds?.length ?? undefined;
                    const roomsMax = antRoomLimit;

                    const maxMsgs = a.maxMessagesPerWeek;
                    const usedMsgs = a.messagesSentThisPeriod;
                    const msgsText = usedMsgs == null ? `— / ${maxMsgs}` : `${usedMsgs} / ${maxMsgs}`;

                    return (
                      <button
                        key={a.id}
                        onClick={() => setEditAntId(a.id)}
                        className="w-full text-left bg-theme-lighter/20 hover:bg-theme-lighter/30 border border-white/5 rounded-xl px-3 py-2 transition-colors relative"
                        title="Click to edit ant settings"
                      >
                        <button
                          onClick={(e) => openDeleteAntModal(e, a)}
                          className="absolute right-2 top-2 flex items-center justify-center w-6 h-6 rounded text-red-300/40 hover:text-red-200 hover:bg-red-500/20 transition-colors"
                          title="Delete ant"
                        >
                          🗑
                        </button>

                        <div className="flex items-center justify-between gap-3 pr-8">
                          <div className="text-white font-semibold truncate">{a.name}</div>
                          <div className={`text-[10px] px-2 py-0.5 rounded-full ${a.enabled ? 'bg-green-500/20 text-green-400' : 'bg-yellow-500/20 text-yellow-400'}`}>
                            {a.enabled ? 'Enabled' : 'Paused'}
                          </div>
                        </div>
                        <div className="text-xs text-theme-muted mt-1">Interval: {a.intervalSeconds}s</div>
                        <div className="flex items-center justify-between mt-2 text-[11px] text-theme-muted">
                          <div
                            title="Rooms used / max rooms this ant can be assigned to"
                          >
                            Rooms: {roomsUsed == null || roomsMax == null ? '—' : `${roomsUsed} / ${roomsMax}`}
                          </div>
                          <div
                            title="Messages used / weekly message budget for this ant"
                          >
                            Msgs/week: {msgsText}
                          </div>
                        </div>
                      </button>
                    );
                  })}
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

      <DeleteAntModal
        isOpen={deleteAntModalOpen}
        ant={antToDelete}
        onClose={() => {
          setDeleteAntModalOpen(false);
          setAntToDelete(null);
        }}
        onDeleted={async (deletedAntId) => {
          // close edit modal if the currently-editing ant was deleted
          if (editAntId === deletedAntId) setEditAntId(null);
          await loadMyAnts();
        }}
      />
    </div>
  );
};
