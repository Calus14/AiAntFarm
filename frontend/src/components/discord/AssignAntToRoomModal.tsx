import React, { useEffect, useMemo, useState } from 'react';
import { antApi } from '../../api/ants';
import type { RoomDto } from '../../api/dto';
import { getRoomsCached } from '../../api/roomsCache';

interface AssignAntToRoomModalProps {
  isOpen: boolean;
  antId: string;
  currentRoomIds: string[];
  onClose: () => void;
  onAssigned: () => void;
}

export const AssignAntToRoomModal: React.FC<AssignAntToRoomModalProps> = ({
  isOpen,
  antId,
  currentRoomIds,
  onClose,
  onAssigned,
}) => {
  const MAX_ROOM_ASSIGNMENTS = 5;
  const [loading, setLoading] = useState(false);
  const [rooms, setRooms] = useState<RoomDto[]>([]);
  const [selectedRoomId, setSelectedRoomId] = useState('');
  const [saving, setSaving] = useState(false);

  const atAssignmentLimit = (currentRoomIds?.length ?? 0) >= MAX_ROOM_ASSIGNMENTS;

  useEffect(() => {
    if (!isOpen) return;

    const load = async () => {
      setLoading(true);
      try {
        const all = await getRoomsCached({ force: true });
        setRooms(all);
      } catch (err) {
        console.error('Failed to load rooms', err);
        setRooms([]);
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [isOpen]);

  const available = useMemo(() => {
    const exclude = new Set(currentRoomIds || []);
    return (rooms || []).filter((r) => r.roomId && !exclude.has(r.roomId));
  }, [rooms, currentRoomIds]);

  useEffect(() => {
    if (!isOpen) return;
    if (atAssignmentLimit) {
      setSelectedRoomId('');
      return;
    }
    setSelectedRoomId(available[0]?.roomId ?? '');
  }, [isOpen, available]);

  const handleAssign = async () => {
    if (atAssignmentLimit) return;
    if (!selectedRoomId) return;
    setSaving(true);
    try {
      await antApi.assignToRoom(antId, selectedRoomId);
      onAssigned();
      onClose();
    } catch (err) {
      console.error('Failed to assign ant to room', err);
    } finally {
      setSaving(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
      <div className="bg-theme-panel w-full max-w-lg rounded-xl shadow-2xl border border-white/10 overflow-hidden flex flex-col">
        <div className="p-4 border-b border-white/5 flex justify-between items-center bg-theme-base/50">
          <h2 className="text-xl font-bold text-white">Assign Ant To Room</h2>
          <button onClick={onClose} className="text-theme-muted hover:text-white transition-colors">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <div className="p-6 space-y-4">
          {atAssignmentLimit && (
            <div
              className="text-xs text-theme-muted"
              title={`Max ${MAX_ROOM_ASSIGNMENTS} room assignments per ant. Unassign one from the Assigned rooms table first.`}
            >
              Max {MAX_ROOM_ASSIGNMENTS} room assignments reached. Unassign one first.
            </div>
          )}

          {loading ? (
            <div className="text-center text-theme-muted">Loading rooms...</div>
          ) : available.length === 0 ? (
            <div className="text-center text-theme-muted">No available rooms to assign.</div>
          ) : (
            <div>
              <label className="block text-xs font-bold text-theme-muted uppercase tracking-wider mb-2">Room</label>
              <select
                value={selectedRoomId}
                onChange={(e) => setSelectedRoomId(e.target.value)}
                disabled={atAssignmentLimit}
                className="w-full bg-theme-base/50 border border-white/10 rounded-md p-2 text-white focus:outline-none focus:ring-2 focus:ring-theme-primary"
              >
                {available.map((r) => (
                  <option key={r.roomId} value={r.roomId}>
                    {r.name} ({r.roomId})
                  </option>
                ))}
              </select>
            </div>
          )}

          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-xl text-theme-text hover:bg-theme-lighter transition-colors font-medium text-sm"
            >
              Cancel
            </button>
            <button
              type="button"
              disabled={saving || atAssignmentLimit || !selectedRoomId || available.length === 0}
              onClick={handleAssign}
              className="px-6 py-2 rounded-xl bg-linear-to-r from-theme-primary to-theme-secondary text-white font-bold text-sm shadow-lg shadow-theme-primary/20 hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed"
              title={atAssignmentLimit ? `Max ${MAX_ROOM_ASSIGNMENTS} assignments reached.` : undefined}
            >
              {saving ? 'Assigning...' : 'Assign'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
