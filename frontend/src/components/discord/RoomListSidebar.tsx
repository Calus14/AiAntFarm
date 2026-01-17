import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { apiClient } from '../../api/client';
import { Room } from '../../types';
import { CreateRoomModal } from './CreateRoomModal';
import { useAuth } from '../../context/AuthContext';
import { DeleteRoomModal } from './DeleteRoomModal';

export const RoomListSidebar = () => {
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const { roomId } = useParams<{ roomId?: string }>();
  const [rooms, setRooms] = useState<Room[]>([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { user } = useAuth();

  const [deleteRoomModalOpen, setDeleteRoomModalOpen] = useState(false);
  const [roomToDelete, setRoomToDelete] = useState<Room | null>(null);

  const myRoomsCount = useMemo(() => {
    if (!user?.id) return 0;
    return rooms.filter((r) => r.ownerId === user.id).length;
  }, [rooms, user?.id]);

  const roomLimit = user?.roomLimit;
  const atRoomLimit = roomLimit != null && roomLimit > 0 && myRoomsCount >= roomLimit;

  const fetchRooms = async () => {
    setLoading(true);
    try {
      const res = await apiClient.get('/api/v1/rooms');
      const payload = res.data ?? {};

      // Normalize to an array whether backend returns: [{...}] or { items: [...] }
      const rawItems = Array.isArray(payload) ? payload : payload.items ?? [];

      // Map backend fields to frontend Room shape (ownerId -> createdBy, default createdAt)
      const normalized: Room[] = (rawItems || []).map((r: any) => ({
        roomId: r.roomId ?? r.id ?? '',
        name: r.name ?? 'Untitled',
        // Backend uses ownerId (RoomDto.ownerId) but older payloads may use createdByUserId.
        ownerId: r.ownerId ?? '',
        createdAt: r.createdAt ?? new Date().toISOString(),
      }));

      setRooms(normalized);
    } catch (err) {
      console.error('Failed to fetch rooms', err);
      setRooms([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRooms();
  }, []);

  const handleCreateSuccess = (newRoom: Room) => {
    fetchRooms(); // Refresh the list
    navigate(`/rooms/${newRoom.roomId}`); // Navigate to the new room
  };

  const openDeleteModal = (e: React.MouseEvent, room: Room) => {
    e.stopPropagation();
    setRoomToDelete(room);
    setDeleteRoomModalOpen(true);
  };

  const handleDeleted = async (deletedRoomId: string) => {
    await fetchRooms();
    if (deletedRoomId === roomId) {
      navigate('/');
    }
  };

  return (
    <>
      <div className="w-64 bg-theme-base flex flex-col h-full overflow-y-auto border-r border-white/5">
        <div className="p-5 shadow-sm bg-theme-base sticky top-0 z-10">
          <div className="flex items-center justify-between group">
            <h2 className="text-theme-primary font-bold text-xs uppercase tracking-widest">Rooms</h2>
            <button
              onClick={() => setIsCreateModalOpen(true)}
              disabled={atRoomLimit}
              className="text-theme-muted hover:text-white transition-colors p-1 rounded hover:bg-theme-lighter disabled:opacity-50 disabled:cursor-not-allowed"
              title={atRoomLimit ? `Room limit reached (${myRoomsCount} / ${roomLimit}). Delete a room to create another.` : 'Create Room'}
            >
              <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
                <path fillRule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clipRule="evenodd" />
              </svg>
            </button>
          </div>

          <div
            className="text-[11px] text-theme-muted mt-2"
            title="Rooms you own / the maximum rooms your account can own."
          >
            Rooms: {roomLimit == null || roomLimit <= 0 ? myRoomsCount : `${myRoomsCount} / ${roomLimit}`}
          </div>
        </div>

        <div className="flex-1 px-3 space-y-1 mt-2">
          {loading ? (
            <div className="text-theme-muted text-sm px-3 py-2">Loading…</div>
          ) : Array.isArray(rooms) && rooms.map((room) => {
            const isActive = room.roomId === roomId;
            const canDelete = !!user?.id && room.ownerId === user.id;
            return (
              <div
                key={room.roomId}
                onClick={() => navigate(`/rooms/${room.roomId}`)}
                className={`
                  group flex items-center px-3 py-2.5 rounded-lg cursor-pointer transition-all duration-200 relative
                  ${isActive ? 'bg-linear-to-r from-theme-primary/20 to-transparent text-white border-l-2 border-theme-primary' : 'text-theme-muted hover:bg-theme-panel hover:text-theme-text'}
                `}
              >
                {/* Skeleton Icon */}
                <div className={`w-8 h-8 rounded-lg mr-3 shrink-0 flex items-center justify-center transition-colors ${isActive ? 'bg-theme-primary text-white' : 'bg-theme-lighter text-theme-muted group-hover:bg-theme-lightest'}`}>
                  <span className="text-xs font-bold">#</span>
                </div>
                
                <span className="font-medium truncate text-sm pr-10">{room.name}</span>

                {canDelete && (
                  <button
                    onClick={(e) => openDeleteModal(e, room)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 flex items-center justify-center w-6 h-6 rounded text-red-300/40 hover:text-red-200 hover:bg-red-500/20 transition-colors"
                    title="Delete room"
                  >
                    🗑
                  </button>
                )}
              </div>
            );
          })}
        </div>
      </div>

      <CreateRoomModal 
        isOpen={isCreateModalOpen} 
        onClose={() => setIsCreateModalOpen(false)} 
        onSuccess={handleCreateSuccess}
      />

      <DeleteRoomModal
        isOpen={deleteRoomModalOpen}
        room={roomToDelete}
        onClose={() => {
          setDeleteRoomModalOpen(false);
          setRoomToDelete(null);
        }}
        onDeleted={handleDeleted}
      />
    </>
  );
};
