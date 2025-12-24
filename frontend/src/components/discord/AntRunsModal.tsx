import React, { useEffect, useMemo, useState } from 'react';
import { antApi } from '../../api/ants';
import type { AntRunDto } from '../../api/dto';
import { getRoomsCached, getRoomName } from '../../api/roomsCache';

interface AntRunsModalProps {
  isOpen: boolean;
  antId: string;
  onClose: () => void;
}

export const AntRunsModal: React.FC<AntRunsModalProps> = ({ isOpen, antId, onClose }) => {
  const [loading, setLoading] = useState(false);
  const [runs, setRuns] = useState<AntRunDto[]>([]);

  useEffect(() => {
    if (!isOpen) return;

    const load = async () => {
      setLoading(true);
      try {
        const res = await antApi.listRuns(antId);
        const items = res.data.items ?? [];
        try {
          await getRoomsCached();
        } catch {
          // ignore cache errors
        }
        setRuns(items);
      } catch (err) {
        console.error('Failed to load ant runs', err);
        setRuns([]);
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [isOpen, antId]);

  const sortedRuns = useMemo(() => {
    return [...runs].sort((a, b) => (b.startedAtMs ?? 0) - (a.startedAtMs ?? 0));
  }, [runs]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
      <div className="bg-theme-panel w-full max-w-3xl rounded-xl shadow-2xl border border-white/10 overflow-hidden flex flex-col max-h-[80vh]">
        <div className="p-4 border-b border-white/5 flex justify-between items-center bg-theme-base/50">
          <h2 className="text-xl font-bold text-white">Ant Runs</h2>
          <button onClick={onClose} className="text-theme-muted hover:text-white transition-colors">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-6">
          {loading ? (
            <div className="text-center text-theme-muted">Loading runs...</div>
          ) : sortedRuns.length === 0 ? (
            <div className="text-center text-theme-muted">No runs yet.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="text-theme-muted">
                  <tr className="border-b border-white/5">
                    <th className="text-left py-2 px-2 font-semibold">Room</th>
                    <th className="text-left py-2 px-2 font-semibold">Started</th>
                    <th className="text-left py-2 px-2 font-semibold">Finished</th>
                    <th className="text-left py-2 px-2 font-semibold">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {sortedRuns.map((r) => (
                    <tr key={r.id} className="border-b border-white/5 hover:bg-white/5 transition-colors group">
                      <td className="py-2 px-2 text-theme-text whitespace-nowrap">
                        {getRoomName(r.roomId) ?? r.roomId}
                      </td>
                      <td className="py-2 px-2 text-theme-text whitespace-nowrap">
                        {r.startedAtMs ? new Date(r.startedAtMs).toLocaleString() : '—'}
                      </td>
                      <td className="py-2 px-2 text-theme-text whitespace-nowrap">
                        {r.finishedAtMs ? new Date(r.finishedAtMs).toLocaleString() : '—'}
                      </td>
                      <td className="py-2 px-2 whitespace-nowrap">
                        <span className="text-theme-primary font-semibold">{r.status}</span>
                      </td>
                      <td className="py-2 px-2">
                        <div className="hidden group-hover:block text-xs">
                          {r.antNotes && (
                            <div className="text-theme-muted whitespace-pre-wrap">{r.antNotes}</div>
                          )}
                          {r.error && (
                            <div className="text-red-400 whitespace-pre-wrap mt-1">{r.error}</div>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <div className="text-xs text-theme-muted mt-3">
                Hover a row to see notes/error.
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
