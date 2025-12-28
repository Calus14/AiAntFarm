import React, { useEffect, useState } from 'react';
import { AntRoomAssignmentDto, RoomRoleDto } from '../../api/dto';
import { antApi } from '../../api/ants';
import { roomApi } from '../../api/rooms';
import { Button } from '../Button';

interface AssignRoleModalProps {
  isOpen: boolean;
  onClose: () => void;
  roomId: string;
  role: RoomRoleDto;
  onSuccess: () => void;
}

export const AssignRoleModal: React.FC<AssignRoleModalProps> = ({
  isOpen,
  onClose,
  roomId,
  role,
  onSuccess,
}) => {
  const [assignments, setAssignments] = useState<AntRoomAssignmentDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [processing, setProcessing] = useState<string | null>(null);

  const fetchAssignments = async () => {
    setLoading(true);
    try {
      const res = await antApi.listInRoom(roomId);
      setAssignments(res.data.items);
    } catch (err) {
      console.error('Failed to fetch assignments', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isOpen) {
      fetchAssignments();
    }
  }, [isOpen, roomId]);

  const handleAssign = async (antId: string) => {
    if (role.assignedCount >= role.maxSpots) {
      alert('Role is full');
      return;
    }
    setProcessing(antId);
    try {
      await roomApi.assignAntRole(antId, roomId, role.roleId);
      await fetchAssignments();
      onSuccess(); // To refresh role counts in parent
    } catch (err) {
      console.error('Failed to assign role', err);
    } finally {
      setProcessing(null);
    }
  };

  const handleUnassign = async (antId: string) => {
    setProcessing(antId);
    try {
      await roomApi.assignAntRole(antId, roomId, null);
      await fetchAssignments();
      onSuccess(); // To refresh role counts in parent
    } catch (err) {
      console.error('Failed to unassign role', err);
    } finally {
      setProcessing(null);
    }
  };

  if (!isOpen) return null;

  const assignedToThisRole = assignments.filter((a) => a.roleId === role.roleId);
  const availableAnts = assignments.filter((a) => a.roleId !== role.roleId);

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative w-full max-w-2xl bg-theme-panel border border-white/10 rounded-2xl shadow-2xl flex flex-col max-h-[90vh]">
        <div className="p-6 border-b border-white/10">
          <h2 className="text-xl font-bold text-white">Assign Ants to Role: {role.name}</h2>
          <p className="text-sm text-theme-text-secondary mt-1">
            {role.assignedCount} / {role.maxSpots} spots filled
          </p>
        </div>

        <div className="p-6 overflow-y-auto flex-1 space-y-6">
          {loading ? (
            <div className="text-center text-theme-text-secondary">Loading ants...</div>
          ) : (
            <>
              <div>
                <h3 className="text-sm font-bold text-theme-text-primary mb-2">
                  Assigned to this role
                </h3>
                {assignedToThisRole.length === 0 ? (
                  <p className="text-sm text-theme-text-secondary italic">No ants assigned.</p>
                ) : (
                  <table className="w-full text-left border-collapse">
                    <tbody>
                      {assignedToThisRole.map((a) => (
                        <tr key={a.antId} className="border-b border-white/5">
                          <td className="p-2 text-white">{a.antName || 'Unknown Ant'}</td>
                          <td className="p-2 text-theme-text-secondary text-sm">
                            {a.antModel || 'Unknown Model'}
                          </td>
                          <td className="p-2 text-right">
                            <Button
                              variant="secondary"
                              size="sm"
                              onClick={() => handleUnassign(a.antId)}
                              disabled={!!processing}
                            >
                              {processing === a.antId ? '...' : 'Remove'}
                            </Button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>

              <div>
                <h3 className="text-sm font-bold text-theme-text-primary mb-2">
                  Available ants in room
                </h3>
                {availableAnts.length === 0 ? (
                  <p className="text-sm text-theme-text-secondary italic">
                    No other ants in this room.
                  </p>
                ) : (
                  <table className="w-full text-left border-collapse">
                    <tbody>
                      {availableAnts.map((a) => (
                        <tr key={a.antId} className="border-b border-white/5">
                          <td className="p-2 text-white">{a.antName || 'Unknown Ant'}</td>
                          <td className="p-2 text-theme-text-secondary text-sm">
                            {a.antModel || 'Unknown Model'}
                          </td>
                          <td className="p-2 text-theme-text-secondary text-xs">
                            {a.roleName ? `Role: ${a.roleName}` : 'No Role'}
                          </td>
                          <td className="p-2 text-right">
                            <Button
                              variant="primary"
                              size="sm"
                              onClick={() => handleAssign(a.antId)}
                              disabled={
                                !!processing || role.assignedCount >= role.maxSpots
                              }
                            >
                              {processing === a.antId ? '...' : 'Assign'}
                            </Button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </>
          )}
        </div>

        <div className="p-4 border-t border-white/10 flex justify-end">
          <Button variant="secondary" onClick={onClose}>
            Done
          </Button>
        </div>
      </div>
    </div>
  );
};
