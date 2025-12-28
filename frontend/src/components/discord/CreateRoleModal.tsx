import React, { useEffect, useState } from 'react';
import { RoomRoleDto } from '../../api/dto';
import { roomApi } from '../../api/rooms';
import { Button } from '../Button';
import { Input } from '../Input';

interface CreateRoleModalProps {
  isOpen: boolean;
  onClose: () => void;
  roomId: string;
  roleToEdit: RoomRoleDto | null;
  onSuccess: () => void;
}

export const CreateRoleModal: React.FC<CreateRoleModalProps> = ({
  isOpen,
  onClose,
  roomId,
  roleToEdit,
  onSuccess,
}) => {
  const [name, setName] = useState('');
  const [maxSpots, setMaxSpots] = useState(1);
  const [prompt, setPrompt] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      if (roleToEdit) {
        setName(roleToEdit.name);
        setMaxSpots(roleToEdit.maxSpots);
        setPrompt(roleToEdit.prompt);
      } else {
        setName('');
        setMaxSpots(1);
        setPrompt('');
      }
      setError(null);
    }
  }, [isOpen, roleToEdit]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setError('Role name is required');
      return;
    }
    if (maxSpots < 1) {
      setError('Max spots must be at least 1');
      return;
    }
    if (roleToEdit && maxSpots < roleToEdit.assignedCount) {
      setError(`Cannot reduce max spots below current assigned count (${roleToEdit.assignedCount})`);
      return;
    }

    setLoading(true);
    setError(null);

    try {
      if (roleToEdit) {
        await roomApi.updateRole(roomId, roleToEdit.roleId, {
          name: name.trim(),
          maxSpots,
          prompt: prompt.trim(),
        });
      } else {
        await roomApi.createRole(roomId, {
          name: name.trim(),
          maxSpots,
          prompt: prompt.trim(),
        });
      }
      onSuccess();
      onClose();
    } catch (err: any) {
      console.error('Failed to save role', err);
      setError(err.response?.data?.message || 'Failed to save role');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!roleToEdit || !window.confirm('Are you sure you want to delete this role?')) return;
    
    setLoading(true);
    try {
      await roomApi.deleteRole(roomId, roleToEdit.roleId);
      onSuccess();
      onClose();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to delete role');
      setLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative w-full max-w-md bg-theme-panel border border-white/10 rounded-2xl shadow-2xl p-6">
        <h2 className="text-xl font-bold text-white mb-4">
          {roleToEdit ? 'Edit Role' : 'Create Role'}
        </h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-theme-text-secondary mb-1">
              Role Name
            </label>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Game Master"
              autoFocus
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-theme-text-secondary mb-1">
              Max Spots
            </label>
            <Input
              type="number"
              min={1}
              value={maxSpots}
              onChange={(e) => setMaxSpots(parseInt(e.target.value) || 0)}
            />
            {roleToEdit && (
              <p className="text-xs text-theme-text-secondary mt-1">
                Currently assigned: {roleToEdit.assignedCount}
              </p>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-theme-text-secondary mb-1">
              Role Prompt
            </label>
            <textarea
              className="w-full bg-theme-bg-primary text-theme-text-primary p-3 rounded-lg border border-white/10 focus:border-theme-primary outline-none min-h-[120px]"
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="Enter the instructions for ants with this role..."
            />
          </div>

          {error && <div className="text-red-500 text-sm">{error}</div>}

          <div className="flex justify-between pt-2">
            {roleToEdit ? (
              <button
                type="button"
                onClick={handleDelete}
                className="text-red-500 hover:text-red-400 text-sm font-medium"
                disabled={loading}
              >
                Delete Role
              </button>
            ) : (
              <div />
            )}
            <div className="flex gap-2">
              <Button variant="secondary" onClick={onClose} disabled={loading} type="button">
                Cancel
              </Button>
              <Button variant="primary" type="submit" disabled={loading}>
                {loading ? 'Saving...' : 'Save'}
              </Button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
};
