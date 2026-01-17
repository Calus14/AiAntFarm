import React, { useState } from 'react';
import type { Room } from '../../types';
import { roomApi } from '../../api/rooms';

interface DeleteRoomModalProps {
  isOpen: boolean;
  room: Room | null;
  onClose: () => void;
  onDeleted: (deletedRoomId: string) => void;
}

export const DeleteRoomModal: React.FC<DeleteRoomModalProps> = ({
  isOpen,
  room,
  onClose,
  onDeleted,
}) => {
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen || !room) return null;

  const handleDelete = async () => {
    if (isDeleting) return;
    setIsDeleting(true);
    setError(null);
    try {
      await roomApi.deleteRoom(room.roomId);
      onDeleted(room.roomId);
      onClose();
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.response?.data?.error || err?.message;
      setError(msg || 'Failed to delete room');
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={() => !isDeleting && onClose()} />

      <div className="relative w-full max-w-md bg-theme-panel border border-white/10 rounded-2xl shadow-2xl overflow-hidden animate-fade-in-up">
        <div className="p-6">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 className="text-2xl font-bold text-white">Delete Room</h2>
              <p className="text-theme-muted text-sm mt-1">
                You’re about to delete <span className="text-white font-semibold">{room.name}</span>.
              </p>
            </div>
            <button
              onClick={onClose}
              disabled={isDeleting}
              className="text-theme-muted hover:text-white transition-colors"
              title="Close"
            >
              ✕
            </button>
          </div>

          <div className="mt-5 bg-red-500/10 border border-red-500/30 text-red-200 rounded-xl p-4 text-sm">
            <div className="font-semibold mb-1">This cannot be undone.</div>
            <ul className="list-disc ml-5 space-y-1">
              <li>All messages in this room will be deleted</li>
              <li>All ant assignments to this room will be removed</li>
              <li>All roles configured for this room will be deleted</li>
            </ul>
          </div>

          {error && (
            <div className="mt-4 bg-red-500/10 border border-red-500/50 text-red-400 p-3 rounded-lg text-sm">
              {error}
            </div>
          )}

          <div className="mt-6 flex justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              disabled={isDeleting}
              className="px-4 py-2 rounded-xl text-theme-text hover:bg-theme-lighter transition-colors font-medium text-sm disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={handleDelete}
              disabled={isDeleting}
              className="px-6 py-2 rounded-xl bg-red-500/80 hover:bg-red-500 text-white font-bold text-sm shadow-lg shadow-red-500/20 disabled:opacity-60 disabled:cursor-not-allowed flex items-center"
            >
              {isDeleting ? (
                <>
                  <svg className="animate-spin -ml-1 mr-2 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  Deleting...
                </>
              ) : (
                'Delete'
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

