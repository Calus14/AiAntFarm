import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { apiClient } from '../../api/client';
import { Room } from '../../types';
import { CreateRoomModal } from './CreateRoomModal';

export const RoomListSidebar = () => {
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const { roomId } = useParams<{ roomId?: string }>();
  const [rooms, setRooms] = useState<Room[]>([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

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
        name: r.name ?? r.channelName ?? 'Untitled',
        ownerId: r.ownerId ?? r.ownerId ?? '',
        createdAt: r.createdAt ?? r.createdAt ?? new Date().toISOString(),
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

  return (
    <>
      <div className="w-64 bg-theme-base flex flex-col h-full overflow-y-auto border-r border-white/5">
        <div className="p-5 shadow-sm bg-theme-base sticky top-0 z-10 flex items-center justify-between group">
          <h2 className="text-theme-primary font-bold text-xs uppercase tracking-widest">Channels</h2>
          <button 
            onClick={() => setIsCreateModalOpen(true)}
            className="text-theme-muted hover:text-white transition-colors p-1 rounded hover:bg-theme-lighter"
            title="Create Channel"
          >
            <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clipRule="evenodd" />
            </svg>
          </button>
        </div>
        <div className="flex-1 px-3 space-y-1 mt-2">
          {Array.isArray(rooms) && rooms.map((room) => {
            const isActive = room.roomId === roomId;
            return (
              <div
                key={room.roomId}
                onClick={() => navigate(`/rooms/${room.roomId}`)}
                className={`
                  group flex items-center px-3 py-2.5 rounded-lg cursor-pointer transition-all duration-200
                  ${isActive ? 'bg-linear-to-r from-theme-primary/20 to-transparent text-white border-l-2 border-theme-primary' : 'text-theme-muted hover:bg-theme-panel hover:text-theme-text'}
                `}
              >
                {/* Skeleton Icon */}
                <div className={`w-8 h-8 rounded-lg mr-3 shrink-0 flex items-center justify-center transition-colors ${isActive ? 'bg-theme-primary text-white' : 'bg-theme-lighter text-theme-muted group-hover:bg-theme-lightest'}`}>
                  <span className="text-xs font-bold">#</span>
                </div>
                
                <span className="font-medium truncate text-sm">
                  {room.name}
                </span>
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
    </>
  );
};
