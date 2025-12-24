import React, { useEffect, useMemo, useState } from 'react';
import { antApi } from '../../api/ants';
import type { AntDto, AntRoomAssignmentDto } from '../../api/dto';

interface AntsModalProps {
  roomId: string;
  onClose: () => void;
}

export const AntsModal: React.FC<AntsModalProps> = ({ roomId, onClose }) => {
  const [assignments, setAssignments] = useState<AntRoomAssignmentDto[]>([]);
  const [antsById, setAntsById] = useState<Record<string, AntDto>>({});
  const [loading, setLoading] = useState(false);
  const [hoveredAntId, setHoveredAntId] = useState<string | null>(null);

  useEffect(() => {
    loadRoomAnts();
  }, [roomId]);

  const loadRoomAnts = async () => {
    setLoading(true);
    try {
      const res = await antApi.listInRoom(roomId);
      const items = res.data.items ?? [];
      setAssignments(items);

      // Fetch ant details for each antId (best-effort, cached in state)
      const uniqueAntIds = Array.from(
        new Set(items.map((a: AntRoomAssignmentDto) => a.antId).filter((id): id is string => !!id))
      );
      await Promise.all(
        uniqueAntIds.map(async (antId) => {
          if (!antId) return;
          try {
            const detail = await antApi.get(antId);
            const ant = detail.data?.ant;
            if (!ant) return;
            setAntsById((prev) => {
              if (prev[antId]) return prev;
              return { ...prev, [antId]: ant };
            });
          } catch (err) {
            console.error('Failed to fetch ant detail', antId, err);
          }
        })
      );
    } catch (err) {
      console.error('Failed to load ants', err);
    } finally {
      setLoading(false);
    }
  };

  const rows = useMemo(() => {
    return assignments
      .map((a) => {
        const ant = antsById[a.antId];
        return { assignment: a, ant };
      })
      .filter((x) => !!x.assignment.antId);
  }, [assignments, antsById]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
      <div className="bg-theme-panel w-full max-w-2xl rounded-xl shadow-2xl border border-white/10 overflow-hidden flex flex-col max-h-[80vh]">
        
        {/* Header */}
        <div className="p-4 border-b border-white/5 flex justify-between items-center bg-theme-base/50">
          <h2 className="text-xl font-bold text-white">
            Room Ants
          </h2>
          <button onClick={onClose} className="text-theme-muted hover:text-white transition-colors">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6">
          <div className="space-y-4">
            {loading ? (
              <div className="text-center text-theme-muted">Loading ants...</div>
            ) : rows.length === 0 ? (
              <div className="text-center text-theme-muted py-8">
                No ants are assigned to this room.
              </div>
            ) : (
              <div className="space-y-3">
                {rows.map(({ assignment, ant }) => {
                  const isHovered = hoveredAntId === assignment.antId;
                  const enabled = ant?.enabled;
                  return (
                    <div
                      key={assignment.antId}
                      className="bg-theme-base/30 border border-white/5 rounded-lg p-4"
                      onMouseEnter={() => setHoveredAntId(assignment.antId)}
                      onMouseLeave={() => setHoveredAntId(null)}
                    >
                      <div className="flex items-center justify-between gap-4">
                        <div className="min-w-0">
                          <div className="flex items-center gap-2">
                            <h3 className="font-bold text-white truncate">
                              {ant?.name ?? assignment.antId}
                            </h3>
                            {ant ? (
                              <span
                                className={`text-xs px-2 py-0.5 rounded-full ${
                                  enabled ? 'bg-green-500/20 text-green-400' : 'bg-yellow-500/20 text-yellow-400'
                                }`}
                              >
                                {enabled ? 'Enabled' : 'Paused'}
                              </span>
                            ) : (
                              <span className="text-xs px-2 py-0.5 rounded-full bg-white/5 text-theme-muted">
                                Loading...
                              </span>
                            )}
                          </div>

                          <div className="text-xs text-theme-muted mt-2 flex gap-3">
                            {ant ? (
                              <>
                                <span>Model: {ant.model}</span>
                                <span>Interval: {ant.intervalSeconds}s</span>
                              </>
                            ) : (
                              <span>Ant ID: {assignment.antId}</span>
                            )}
                          </div>
                        </div>

                        <div className="text-xs text-theme-muted shrink-0">
                          {assignment.lastRunAtMs
                            ? `Last run: ${new Date(assignment.lastRunAtMs).toLocaleString()}`
                            : 'Last run: —'}
                        </div>
                      </div>

                      {isHovered && ant && (
                        <div className="mt-3 pt-3 border-t border-white/5">
                          <div className="text-xs text-theme-muted uppercase font-bold tracking-widest mb-2">
                            Personality
                          </div>
                          <div className="text-sm text-theme-text whitespace-pre-wrap">
                            {ant.personalityPrompt || '—'}
                          </div>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
