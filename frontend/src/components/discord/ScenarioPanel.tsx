import React, { useState, useEffect } from 'react';
import { Room } from '../../types';
import { Button } from '../Button';
import { useAuth } from '../../context/AuthContext';

interface ScenarioPanelProps {
  room: Room;
  onUpdate: (newScenario: string) => Promise<void>;
  onManageRoles?: () => void;
}

export const ScenarioPanel: React.FC<ScenarioPanelProps> = ({ room, onUpdate, onManageRoles }) => {
  const { user } = useAuth();
  const [isEditing, setIsEditing] = useState(false);
  const [editValue, setEditValue] = useState(room.scenarioText || '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isOwner = user?.id === room.ownerId;

  useEffect(() => {
    setEditValue(room.scenarioText || '');
  }, [room.scenarioText]);

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    try {
      await onUpdate(editValue);
      setIsEditing(false);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to save scenario');
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = () => {
    setEditValue(room.scenarioText || '');
    setIsEditing(false);
    setError(null);
  };

  if (isEditing) {
    return (
      <div className="bg-theme-bg-secondary p-4 border-b border-white/10 h-full overflow-hidden flex flex-col">
        <h3 className="text-sm font-bold text-theme-text-primary mb-2">Edit Room Scenario</h3>
        <textarea
          className="w-full bg-theme-bg-primary text-theme-text-primary p-2 rounded border border-white/10 focus:border-theme-primary outline-none flex-1 min-h-0"
          value={editValue}
          onChange={(e) => setEditValue(e.target.value)}
          placeholder="Enter scenario text..."
        />
        {error && <div className="text-red-500 text-xs mt-2">{error}</div>}
        <div className="flex gap-2 mt-2 justify-end">
          <Button variant="secondary" onClick={handleCancel} disabled={saving}>
            Cancel
          </Button>
          <Button variant="primary" onClick={handleSave} disabled={saving}>
            {saving ? 'Saving...' : 'Save'}
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-theme-bg-secondary p-4 border-b border-white/10 h-full overflow-hidden flex flex-col">
      <div className="flex justify-between items-start mb-2 shrink-0">
        <h3 className="text-sm font-bold text-theme-text-primary">Room Scenario</h3>
        <div className="flex gap-3">
          {isOwner && onManageRoles && (
            <button
              onClick={onManageRoles}
              className="text-xs text-theme-primary hover:text-theme-secondary transition-colors"
            >
              Manage Ant Roles
            </button>
          )}
          {isOwner && (
            <button
              onClick={() => setIsEditing(true)}
              className="text-xs text-theme-primary hover:text-theme-secondary transition-colors"
            >
              Edit Scenario
            </button>
          )}
        </div>
      </div>
      <div className="text-sm text-theme-text-secondary whitespace-pre-wrap overflow-y-auto min-h-0 pr-2 scrollbar-thin custom-scrollbar">
        {room.scenarioText ? (
          room.scenarioText
        ) : (
          <span className="italic opacity-50">No scenario set yet.</span>
        )}
      </div>
    </div>
  );
};
