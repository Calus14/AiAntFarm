import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { apiClient } from '../api/client';
import { Room } from '../types';
import { Button, Input, Card } from '../components/ui';

export const RoomListPage = () => {
  const [rooms, setRooms] = useState<Room[]>([]);
  const [newRoomName, setNewRoomName] = useState('');
  const [isCreating, setIsCreating] = useState(false);

  useEffect(() => {
    fetchRooms();
  }, []);

  const fetchRooms = async () => {
    try {
      const res = await apiClient.get('/api/v1/rooms');
      // Adjust based on whether it returns { items: [] } or just []
      setRooms(res.data.items || res.data || []);
    } catch (err) {
      console.error('Failed to fetch rooms', err);
    }
  };

  const handleCreateRoom = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newRoomName.trim()) return;
    
    try {
      await apiClient.post('/api/v1/rooms', { name: newRoomName });
      setNewRoomName('');
      setIsCreating(false);
      fetchRooms();
    } catch (err) {
      console.error('Failed to create room', err);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-3xl font-bold text-gray-900">Rooms</h1>
        <Button onClick={() => setIsCreating(!isCreating)}>
          {isCreating ? 'Cancel' : 'Create Room'}
        </Button>
      </div>

      {isCreating && (
        <Card className="mb-6">
          <form onSubmit={handleCreateRoom} className="flex gap-4 items-end">
            <Input
              className="flex-1"
              label="Room Name"
              value={newRoomName}
              onChange={(e) => setNewRoomName(e.target.value)}
              placeholder="Enter room name..."
              autoFocus
            />
            <Button type="submit">Create</Button>
          </form>
        </Card>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {rooms.map((room) => (
          <Link key={room.roomId} to={`/rooms/${room.roomId}`}>
            <Card className="hover:shadow-lg transition-shadow cursor-pointer h-full flex flex-col justify-between">
              <div>
                <h3 className="text-xl font-semibold text-gray-800">{room.name}</h3>
                <p className="text-sm text-gray-500 mt-2">ID: {room.roomId}</p>
              </div>
              <div className="mt-4 text-xs text-gray-400">
                Created by {room.ownerId}
              </div>
            </Card>
          </Link>
        ))}
        {rooms.length === 0 && (
          <p className="text-gray-500 col-span-full text-center py-10">No rooms found. Create one to get started!</p>
        )}
      </div>
    </div>
  );
};
