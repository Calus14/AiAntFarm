import React, { useEffect, useMemo, useState } from 'react';
import { antApi } from '../../api/ants';
import type { AntDto, AntDetailDto, CreateAntRequest, UpdateAntRequest } from '../../api/dto';
import { AiModel } from '../../api/enums';
import { AssignAntToRoomModal } from './AssignAntToRoomModal';
import { getRoomsCached, getRoomName } from '../../api/roomsCache';

type Mode = 'create' | 'edit';

interface AntSettingsModalProps {
  isOpen: boolean;
  mode: Mode;
  antId?: string;
  onClose: () => void;
  onSaved: () => void;
  onDeleted: () => void;
}

function pickEditableFields(ant: AntDto) {
  return {
    name: ant.name,
    model: ant.model as AiModel,
    personalityPrompt: ant.personalityPrompt,
    intervalSeconds: ant.intervalSeconds,
    enabled: ant.enabled,
    replyEvenIfNoNew: ant.replyEvenIfNoNew,
  };
}

export const AntSettingsModal: React.FC<AntSettingsModalProps> = ({
  isOpen,
  mode,
  antId,
  onClose,
  onSaved,
  onDeleted,
}) => {
  const MAX_ROOM_ASSIGNMENTS = 5;

  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<AntDetailDto | null>(null);
  const [initial, setInitial] = useState<ReturnType<typeof pickEditableFields> | null>(null);

  const [name, setName] = useState('');
  const [modelValue, setModelValue] = useState<AiModel>(AiModel.MOCK);
  const [prompt, setPrompt] = useState('');
  const [intervalSeconds, setIntervalSeconds] = useState(300);
  const [enabled, setEnabled] = useState(true);
  const [replyEvenIfNoNew, setReplyEvenIfNoNew] = useState(false);

  const [showAssign, setShowAssign] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [unassigningRoomId, setUnassigningRoomId] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) return;

    const resetForCreate = () => {
      setDetail(null);
      const defaults = {
        name: '',
        model: AiModel.MOCK,
        personalityPrompt: '',
        intervalSeconds: 300,
        enabled: true,
        replyEvenIfNoNew: false,
      };
      setInitial(defaults);
      setName(defaults.name);
      setModelValue(defaults.model);
      setPrompt(defaults.personalityPrompt);
      setIntervalSeconds(defaults.intervalSeconds);
      setEnabled(defaults.enabled);
      setReplyEvenIfNoNew(defaults.replyEvenIfNoNew);
    };

    const load = async () => {
      if (mode === 'create') {
        resetForCreate();
        return;
      }

      if (!antId) return;

      setLoading(true);
      try {
        const res = await antApi.get(antId);
        const d = res.data;
        setDetail(d);
        // Ensure we have a recent rooms cache so we can show room names
        try {
          await getRoomsCached();
        } catch {
          // ignore cache failures
        }
        const editable = pickEditableFields(d.ant);
        setInitial(editable);
        setName(editable.name);
        setModelValue(editable.model);
        setPrompt(editable.personalityPrompt);
        setIntervalSeconds(editable.intervalSeconds);
        setEnabled(editable.enabled);
        setReplyEvenIfNoNew(editable.replyEvenIfNoNew);
      } catch (err) {
        console.error('Failed to load ant', err);
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [isOpen, mode, antId]);

  const current = useMemo(() => {
    return {
      name,
      model: modelValue,
      personalityPrompt: prompt,
      intervalSeconds,
      enabled,
      replyEvenIfNoNew,
    };
  }, [name, modelValue, prompt, intervalSeconds, enabled, replyEvenIfNoNew]);

  const isDirty = useMemo(() => {
    if (!initial) return false;
    return JSON.stringify(initial) !== JSON.stringify(current);
  }, [initial, current]);

  const canSave = mode === 'create'
    ? !!name.trim() && !!prompt.trim() && intervalSeconds >= 60
    : isDirty;

  const handleSave = async () => {
    if (saving) return;
    setSaving(true);
    try {
      if (mode === 'create') {
        const req: CreateAntRequest = {
          name: name.trim(),
          model: modelValue,
          personalityPrompt: prompt,
          intervalSeconds,
          enabled,
          replyEvenIfNoNew,
        };
        await antApi.create(req);
        onSaved();
        onClose();
        return;
      }

      if (!antId) return;

      const req: UpdateAntRequest = {
        name: name.trim(),
        model: modelValue,
        personalityPrompt: prompt,
        intervalSeconds,
        enabled,
        replyEvenIfNoNew,
      };
      await antApi.update(antId, req);
      setInitial(current);
      onSaved();
    } catch (err) {
      console.error('Failed to save ant', err);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (deleting) return;
    if (!antId) return;

    const antName = detail?.ant?.name || name || 'this ant';
    const ok = window.confirm(`Delete "${antName}"? This cannot be undone.`);
    if (!ok) return;

    setDeleting(true);
    try {
      await antApi.delete(antId);
      onDeleted();
      onClose();
    } catch (err) {
      console.error('Failed to delete ant', err);
    } finally {
      setDeleting(false);
    }
  };

  const refreshDetail = async () => {
    if (!antId) return;
    try {
      const res = await antApi.get(antId);
      const d = res.data;
      setDetail(d);
      try {
        await getRoomsCached();
      } catch {
        // ignore
      }
    } catch {
      // ignore
    }
  };

  const assignedRoomIds = useMemo(() => {
    return (detail?.roomIds ?? []).slice(0, MAX_ROOM_ASSIGNMENTS);
  }, [detail?.roomIds]);

  const atAssignmentLimit = (detail?.roomIds?.length ?? 0) >= MAX_ROOM_ASSIGNMENTS;

  const handleUnassign = async (roomId: string) => {
    if (!antId) return;
    if (unassigningRoomId) return;
    setUnassigningRoomId(roomId);
    try {
      await antApi.unassignFromRoom(antId, roomId);
      await refreshDetail();
      onSaved();
    } catch (err) {
      console.error('Failed to unassign ant from room', err);
    } finally {
      setUnassigningRoomId(null);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
      <div className="bg-theme-panel w-full max-w-2xl rounded-xl shadow-2xl border border-white/10 overflow-hidden flex flex-col max-h-[85vh]">
        <div className="p-4 border-b border-white/5 flex justify-between items-center bg-theme-base/50">
          <h2 className="text-xl font-bold text-white">
            {mode === 'create' ? 'Create Ant' : 'Ant Settings'}
          </h2>
          <button onClick={onClose} className="text-theme-muted hover:text-white transition-colors">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-6 space-y-5">
          {loading ? (
            <div className="text-center text-theme-muted">Loading...</div>
          ) : (
            <>
              {mode === 'edit' && antId && (
                <div className="flex gap-3">
                  <button
                    type="button"
                    onClick={() => setShowAssign(true)}
                    className="flex-1 px-4 py-2 rounded-xl bg-theme-base/40 border border-white/10 text-white hover:bg-theme-base/60 transition-colors font-semibold"
                  >
                    Assign Ant To Room
                  </button>
                </div>
              )}

              <div>
                <label className="block text-xs font-bold text-theme-muted uppercase tracking-wider mb-2">Name</label>
                <input
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full bg-theme-base/50 border border-white/10 rounded-xl p-3 text-white focus:outline-none focus:ring-2 focus:ring-theme-primary"
                  placeholder="e.g. Helper Ant"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-theme-muted uppercase tracking-wider mb-2">Model</label>
                <select
                  value={modelValue}
                  onChange={(e) => setModelValue(e.target.value as AiModel)}
                  className="w-full bg-theme-base/50 border border-white/10 rounded-xl p-3 text-white focus:outline-none focus:ring-2 focus:ring-theme-primary"
                >
                  {Object.values(AiModel).map((m) => (
                    <option key={m} value={m}>
                      {m}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-bold text-theme-muted uppercase tracking-wider mb-2">Personality</label>
                <textarea
                  value={prompt}
                  onChange={(e) => setPrompt(e.target.value)}
                  rows={5}
                  className="w-full bg-theme-base/50 border border-white/10 rounded-xl p-3 text-white focus:outline-none focus:ring-2 focus:ring-theme-primary"
                  placeholder="Describe how this ant should behave..."
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-theme-muted uppercase tracking-wider mb-2">Interval (seconds)</label>
                  <input
                    type="number"
                    min={60}
                    value={intervalSeconds}
                    onChange={(e) => setIntervalSeconds(parseInt(e.target.value || '0', 10))}
                    className="w-full bg-theme-base/50 border border-white/10 rounded-xl p-3 text-white focus:outline-none focus:ring-2 focus:ring-theme-primary"
                  />
                </div>

                <div className="flex items-center gap-4 pt-7">
                  <label className="flex items-center gap-2 text-sm text-white">
                    <input
                      type="checkbox"
                      checked={enabled}
                      onChange={(e) => setEnabled(e.target.checked)}
                      className="rounded border-white/10 bg-theme-base/50 text-theme-primary focus:ring-theme-primary"
                    />
                    Enabled
                  </label>

                  <label className="flex items-center gap-2 text-sm text-white">
                    <input
                      type="checkbox"
                      checked={replyEvenIfNoNew}
                      onChange={(e) => setReplyEvenIfNoNew(e.target.checked)}
                      className="rounded border-white/10 bg-theme-base/50 text-theme-primary focus:ring-theme-primary"
                    />
                    Reply always
                  </label>
                </div>
              </div>

              {mode === 'edit' && detail && (
                <div
                  className="text-xs text-theme-muted"
                  title={atAssignmentLimit ? `Max ${MAX_ROOM_ASSIGNMENTS} room assignments per ant. Unassign one to add another.` : undefined}
                >
                  <div className="font-semibold text-theme-muted mb-2">Assigned rooms</div>
                  {assignedRoomIds.length === 0 ? (
                    <div>—</div>
                  ) : (
                    <div className="overflow-x-auto">
                      <table className="w-full text-xs">
                        <thead className="text-theme-muted">
                          <tr className="border-b border-white/5">
                            <th className="text-left py-2 pr-2 font-semibold">Room</th>
                            <th className="text-right py-2 pl-2 font-semibold">Action</th>
                          </tr>
                        </thead>
                        <tbody>
                          {assignedRoomIds.map((rid) => (
                            <tr key={rid} className="border-b border-white/5">
                              <td className="py-2 pr-2 text-theme-text whitespace-nowrap">
                                {getRoomName(rid) ?? rid}
                              </td>
                              <td className="py-2 pl-2 text-right whitespace-nowrap">
                                <button
                                  type="button"
                                  onClick={() => handleUnassign(rid)}
                                  disabled={!!unassigningRoomId}
                                  className="px-3 py-1 rounded-lg bg-theme-base/40 border border-white/10 text-white hover:bg-theme-base/60 transition-colors font-semibold disabled:opacity-50 disabled:cursor-not-allowed"
                                  title="Unassign this ant from the room"
                                >
                                  {unassigningRoomId === rid ? 'Removing…' : 'Remove'}
                                </button>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                      <div className="text-[11px] text-theme-muted mt-2">
                        {Math.min(detail.roomIds.length, MAX_ROOM_ASSIGNMENTS)}/{MAX_ROOM_ASSIGNMENTS} assignments
                      </div>
                    </div>
                  )}
                </div>
              )}
            </>
          )}
        </div>

        <div className="p-4 border-t border-white/5 bg-theme-base/30 flex items-center justify-between gap-3">
          <div>
            {mode === 'edit' && (
              <button
                type="button"
                onClick={handleDelete}
                disabled={deleting || saving}
                className="px-4 py-2 rounded-xl bg-theme-base/40 border border-white/10 text-white hover:bg-theme-base/60 transition-colors font-semibold disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {deleting ? 'Deleting…' : 'Delete'}
              </button>
            )}
          </div>

          <button
            type="button"
            onClick={handleSave}
            disabled={saving || !canSave}
            className="px-6 py-2 rounded-xl bg-linear-to-r from-theme-primary to-theme-secondary text-white font-bold shadow-lg shadow-theme-primary/20 hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {mode === 'create' ? (saving ? 'Creating...' : 'Create Ant') : (saving ? 'Updating...' : 'Update Ant')}
          </button>
        </div>
      </div>

      {mode === 'edit' && antId && (
        <>
          <AssignAntToRoomModal
            isOpen={showAssign}
            antId={antId}
            currentRoomIds={detail?.roomIds ?? []}
            onClose={() => setShowAssign(false)}
            onAssigned={async () => {
              await refreshDetail();
              onSaved();
            }}
          />
        </>
      )}
    </div>
  );
};
