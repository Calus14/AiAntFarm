import React, { useEffect, useMemo, useState } from 'react';
import { RoomRoleDto } from '../../api/dto';
import { roomApi } from '../../api/rooms';
import { antApi } from '../../api/ants';
import type { AntRoomAssignmentDto } from '../../api/dto';
import { Button } from '../Button';
import { CreateRoleModal } from './CreateRoleModal';
import { AssignRoleModal } from './AssignRoleModal';

interface ManageRolesModalProps {
  isOpen: boolean;
  onClose: () => void;
  roomId: string;
}

export const ManageRolesModal: React.FC<ManageRolesModalProps> = ({ isOpen, onClose, roomId }) => {
  const [roles, setRoles] = useState<RoomRoleDto[]>([]);
  const [assignments, setAssignments] = useState<AntRoomAssignmentDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editRole, setEditRole] = useState<RoomRoleDto | null>(null);
  const [assignRole, setAssignRole] = useState<RoomRoleDto | null>(null);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [rolesRes, antsRes] = await Promise.all([
        roomApi.listRoles(roomId),
        antApi.listInRoom(roomId),
      ]);
      setRoles(rolesRes.data.items ?? []);
      setAssignments(antsRes.data.items ?? []);
    } catch (err) {
      console.error('Failed to fetch roles/assignments', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isOpen) {
      fetchData();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, roomId]);

  const assignedCountByRoleId = useMemo(() => {
    const map: Record<string, number> = {};
    for (const a of assignments) {
      if (!a?.roleId) continue;
      map[a.roleId] = (map[a.roleId] ?? 0) + 1;
    }
    return map;
  }, [assignments]);

  const rolesWithCounts = useMemo(() => {
    return roles.map((r) => ({
      ...r,
      assignedCount: assignedCountByRoleId[r.roleId] ?? 0,
    }));
  }, [roles, assignedCountByRoleId]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative w-full max-w-4xl bg-theme-panel border border-white/10 rounded-2xl shadow-2xl flex flex-col max-h-[90vh]">
        <div className="p-6 border-b border-white/10 flex justify-between items-center">
          <div>
            <h2 className="text-xl font-bold text-white">Manage Ant Roles</h2>
            <p className="text-sm text-theme-text-secondary mt-1">
              Ant Roles are extra prompts you can assign to ants in this room to shape their behavior.
            </p>
          </div>
          <Button onClick={() => setCreateModalOpen(true)}>Create Role</Button>
        </div>

        <div className="p-6 overflow-y-auto flex-1">
          {loading ? (
            <div className="text-center text-theme-text-secondary">Loading roles...</div>
          ) : roles.length === 0 ? (
            <div className="text-center text-theme-text-secondary py-8">
              No ant roles yet. Create one to get started.
            </div>
          ) : (
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="text-theme-text-secondary text-sm border-b border-white/10">
                  <th className="p-3 font-medium">Role Name</th>
                  <th className="p-3 font-medium">Max Spots</th>
                  <th className="p-3 font-medium">Prompt Preview</th>
                  <th className="p-3 font-medium">Assigned</th>
                  <th className="p-3 font-medium text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {rolesWithCounts.map((role) => (
                  <tr key={role.roleId} className="border-b border-white/5 hover:bg-white/5">
                    <td className="p-3 text-white font-medium">{role.name}</td>
                    <td className="p-3 text-theme-text-secondary">{role.maxSpots}</td>
                    <td className="p-3 text-theme-text-secondary truncate max-w-[200px]" title={role.prompt}>
                      {role.prompt}
                    </td>
                    <td className="p-3 text-theme-text-secondary">
                      {role.assignedCount} / {role.maxSpots}
                    </td>
                    <td className="p-3 text-right space-x-2">
                      <button
                        onClick={() => setEditRole(role)}
                        className="text-theme-primary hover:text-theme-secondary text-sm"
                      >
                        Edit
                      </button>
                      <button
                        onClick={() => setAssignRole(role)}
                        className="text-theme-primary hover:text-theme-secondary text-sm"
                      >
                        Assign Ants
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        <div className="p-4 border-t border-white/10 flex justify-end">
          <Button variant="secondary" onClick={onClose}>
            Close
          </Button>
        </div>
      </div>

      <CreateRoleModal
        isOpen={createModalOpen || !!editRole}
        onClose={() => {
          setCreateModalOpen(false);
          setEditRole(null);
        }}
        roomId={roomId}
        roleToEdit={editRole}
        onSuccess={fetchData}
      />

      {assignRole && (
        <AssignRoleModal
          isOpen={!!assignRole}
          onClose={() => setAssignRole(null)}
          roomId={roomId}
          role={assignRole as any}
          onSuccess={fetchData}
        />
      )}
    </div>
  );
};
