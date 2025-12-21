import React, { useEffect, useState, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { apiClient, getStreamUrl, getAuthHeader } from '../api/client';
import { streamSse } from '../api/sse';
import { mapMessageDto } from '../api/mappers';
import type { MessageDto } from '../api/dto';
import { useAuth } from '../context/AuthContext';
import { Message, RoomDetail } from '../types';
import { Button, Input, Card } from '../components/ui';

export const RoomPage = () => {
  const { roomId } = useParams<{ roomId: string }>();
  const { token, user } = useAuth();
  const [room, setRoom] = useState<RoomDetail | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [newMessage, setNewMessage] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  // Fetch initial room details
  useEffect(() => {
    if (!roomId) return;
    apiClient.get(`/api/v1/rooms/${roomId}`)
      .then(res => {
        setRoom(res.data);
        setMessages(res.data.messages || []);
      })
      .catch(err => console.error('Failed to fetch room', err));
  }, [roomId]);

  // Setup SSE
  useEffect(() => {
    if (!roomId || !token) return;

    const controller = new AbortController();

    streamSse(getStreamUrl(roomId), {
      headers: { ...getAuthHeader() } as HeadersInit,
      signal: controller.signal,
      onEvent: (ev) => {
        try {
          const dto = JSON.parse(ev.data) as MessageDto;
          const mapped = mapMessageDto(dto);
          setMessages(prev => {
            if (prev.some(m => m.messageId === mapped.messageId)) return prev;
            return [...prev, mapped];
          });
        } catch (e) {
          console.error('Error parsing SSE message', e);
        }
      }
    }).catch(err => {
      if ((err as any)?.name === 'AbortError') return;
      console.error('SSE Error', err);
    });

    return () => {
      controller.abort();
    };
  }, [roomId, token]);

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newMessage.trim() || !roomId) return;

    try {
      await apiClient.post(`/api/v1/rooms/${roomId}/messages`, { content: newMessage });
      setNewMessage('');
    } catch (err) {
      console.error('Failed to send message', err);
    }
  };

  if (!room) return <div className="p-8 text-center">Loading room...</div>;

  return (
    <div className="flex flex-col h-[calc(100vh-10rem)]">
      <div className="mb-4">
        <h1 className="text-2xl font-bold text-gray-900">{room.name}</h1>
        <p className="text-sm text-gray-500">Room ID: {room.roomId}</p>
      </div>

      <Card className="flex-1 flex flex-col overflow-hidden p-0">
        <div className="flex-1 overflow-y-auto p-4 space-y-4 bg-gray-50">
          {messages.map((msg) => {
            const isMe = msg.authorName === user?.displayName;
            return (
              <div key={msg.messageId} className={`flex ${isMe ? 'justify-end' : 'justify-start'}`}>
                <div className={`max-w-[70%] rounded-lg p-3 ${isMe ? 'bg-blue-600 text-white' : 'bg-white border border-gray-200'}`}>
                  {!isMe && <div className="text-xs font-bold text-gray-600 mb-1">{msg.authorName}</div>}
                  <div className="wrap-break-word">{msg.content}</div>
                  <div className={`text-xs mt-1 ${isMe ? 'text-blue-200' : 'text-gray-400'}`}>
                    {new Date(msg.createdAt).toLocaleTimeString()}
                  </div>
                </div>
              </div>
            );
          })}
          <div ref={messagesEndRef} />
        </div>
        
        <div className="p-4 bg-white border-t border-gray-200">
          <form onSubmit={handleSendMessage} className="flex gap-2">
            <Input
              className="flex-1"
              value={newMessage}
              onChange={(e) => setNewMessage(e.target.value)}
              placeholder="Type a message..."
            />
            <Button type="submit">Send</Button>
          </form>
        </div>
      </Card>
    </div>
  );
};
