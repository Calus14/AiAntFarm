import React, { useEffect, useMemo, useState } from 'react';
import { antApi } from '../../api/ants';
import { roomApi } from '../../api/rooms';
import { userApi } from '../../api/users';
import type { AntDetailDto, AntRoomAssignmentDto, AntDto, RoomRoleDto } from '../../api/dto';
import { Button } from '../Button';

type Mode =
  | { kind: 'USER'; userId: string; displayName: string }
  | { kind: 'ANT'; antId: string; displayName: string; roomId?: string };

interface ParticipantInfoModalProps {
  isOpen: boolean;
  onClose: () => void;
  mode: Mode | null;
}

function truncate(s: string, n: number) {
  const v = (s ?? '').trim();
  if (!v) return '';
  if (v.length <= n) return v;
  return v.slice(0, n).trimEnd() + '...';
}

export const ParticipantInfoModal: React.FC<ParticipantInfoModalProps> = ({ isOpen, onClose, mode }) => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // user mode
  const [myAnts, setMyAnts] = useState<AntDto[]>([]);

  // ant mode
  const [antDetail, setAntDetail] = useState<AntDetailDto | null>(null);
  const [assignment, setAssignment] = useState<AntRoomAssignmentDto | null>(null);
  const [role, setRole] = useState<RoomRoleDto | null>(null);

  const title = useMemo(() => {
    if (!mode) return '';
    return mode.displayName || (mode.kind === 'ANT' ? 'Ant' : 'User');
  }, [mode]);

  useEffect(() => {
    if (!isOpen || !mode) return;

    setLoading(true);
    setError(null);
    setMyAnts([]);
    setAntDetail(null);
    setAssignment(null);
    setRole(null);

    const run = async () => {
      try {
        if (mode.kind === 'USER') {
          const antsRes = await userApi.listAntsForUser(mode.userId);
          setMyAnts(antsRes.data.items ?? []);
          return;
        }

        // ANT
        const [detailRes, assignmentsRes] = await Promise.all([
          antApi.getPublic(mode.antId),
          mode.roomId ? antApi.listInRoom(mode.roomId) : Promise.resolve({ data: { items: [] as AntRoomAssignmentDto[] } }),
        ]);

        setAntDetail(detailRes.data);

        const found = (assignmentsRes.data.items ?? []).find((a) => a.antId === mode.antId) ?? null;
        setAssignment(found);

        if (mode.roomId) {
          // IMPORTANT: use PUBLIC room-roles endpoint for this modal.
          const rolesRes = await roomApi.listRolesPublic(mode.roomId);
          const roleId = found?.roleId;
          const roleFound = roleId ? (rolesRes.data.items ?? []).find((r) => r.roleId === roleId) ?? null : null;
          setRole(roleFound);
        }
      } catch (e: any) {
        console.error('Failed to load participant info', e);
        setError('Failed to load info');
      } finally {
        setLoading(false);
      }
    };

    void run();
  }, [isOpen, mode]);

  if (!isOpen || !mode) return null;

  const ant = antDetail?.ant;
  const roleLabel = role
    ? `${role.name}`
    : assignment?.roleName
      ? `${assignment.roleName}`
      : 'No Assigned Role';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />

      <div className="relative w-full max-w-2xl bg-theme-panel border border-white/10 rounded-2xl shadow-2xl flex flex-col max-h-[90vh] overflow-hidden">
        <div className="p-6 border-b border-white/10 flex items-start justify-between gap-4">
          <div className="min-w-0">
            <h2 className="text-xl font-bold text-white truncate">{title}</h2>
            <div className="text-xs text-theme-muted mt-1">
              {mode.kind === 'ANT' ? 'Bot' : 'Human'}
            </div>
          </div>
          <Button variant="secondary" onClick={onClose}>Close</Button>
        </div>

        <div className="p-6 overflow-y-auto flex-1 min-h-0">
          {loading ? (
            <div className="text-theme-text-secondary">Loading…</div>
          ) : error ? (
            <div className="text-red-400 text-sm">{error}</div>
          ) : mode.kind === 'USER' ? (
            <div className="space-y-6">
              <div>
                <div className="text-theme-muted text-xs uppercase font-bold tracking-widest mb-2">Ants</div>
                <div className="bg-theme-base/40 border border-white/10 rounded-xl p-3">
                  {myAnts.length === 0 ? (
                    <div className="text-theme-text-secondary text-sm">No visible ants.</div>
                  ) : (
                    <div className="space-y-2">
                      {myAnts.map((a) => (
                        <div key={a.id} className="flex items-center justify-between">
                          <div className="text-white font-semibold truncate">{a.name}</div>
                          <div className="text-[11px] text-theme-muted">{a.model}</div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>
          ) : (
            <div className="space-y-6">
              <div>
                <div className="text-theme-muted text-xs uppercase font-bold tracking-widest mb-2">Bot Personality</div>
                <div className="bg-theme-base/40 border border-white/10 rounded-xl p-3 text-sm text-theme-text-secondary whitespace-pre-wrap">
                  {ant?.personalityPrompt?.trim() ? ant.personalityPrompt : <span className="italic opacity-60">No personality prompt</span>}
                </div>
              </div>

              <div>
                <div className="text-theme-muted text-xs uppercase font-bold tracking-widest mb-2">Role</div>
                <div className="bg-theme-base/40 border border-white/10 rounded-xl p-3 text-sm text-theme-text-secondary whitespace-pre-wrap">
                  {roleLabel}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
