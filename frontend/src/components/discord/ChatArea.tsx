import React, { useEffect, useState, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { apiClient, getAuthHeader, getStreamUrl } from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import { Message, Room } from '../../types';
import type { MessageDto, RoomDetailDto } from '../../api/dto';
import { mapMessageDto, mapRoomDetailDto } from '../../api/mappers';
import { streamSse } from '../../api/sse';
import { MessageItem } from './MessageItem';
import { MessageInput } from './MessageInput';

export const ChatArea = () => {
  const { roomId } = useParams<{ roomId: string }>();
  const { user } = useAuth();
  const [room, setRoom] = useState<Room | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(true);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'auto' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  useEffect(() => {
    if (!roomId) return;

    const controller = new AbortController();

    const connectStream = async () => {
      setLoading(true);
      try {
        // 1. Fetch initial room data (standard call, uses interceptor)
        const res = await apiClient.get<RoomDetailDto>(`/api/v1/rooms/${roomId}`);
        const detail = mapRoomDetailDto(res.data);
        setRoom(detail);
        setMessages(detail.messages || []);

        // 2. Connect to stream using fetch to support Authorization header
        setLoading(false);
        await streamSse(getStreamUrl(roomId), {
          headers: { ...getAuthHeader() } as HeadersInit,
          signal: controller.signal,
          onEvent: (ev) => {
            const raw = (ev.data || '').trim();
            if (!raw || raw === '{}' || raw === 'null') return;
            try {
              const dto = JSON.parse(raw) as MessageDto;
              const mapped = mapMessageDto(dto);
              setMessages((prev) => {
                if (prev.some((m) => m.messageId === mapped.messageId)) return prev;
                return [...prev, mapped];
              });
            } catch (e) {
              console.error('Error parsing stream message', e);
            }
          },
        });
      } catch (err) {
        if (err instanceof Error && err.name === 'AbortError') return;
        console.error('Stream connection failed', err);
        setLoading(false);
      }
    };

    connectStream();

    return () => {
      controller.abort();
    };
  }, [roomId]);

  const handleSendMessage = async (content: string) => {
    if (!roomId) return;
    try {
      await apiClient.post(`/api/v1/rooms/${roomId}/messages`, { text: content });
    } catch (err) {
      console.error('Failed to send message', err);
    }
  };

  return (
    <div className="flex-1 flex flex-col bg-theme-base h-full relative overflow-hidden">
      {/* Header - Always visible */}
      <header className="h-16 border-b border-white/5 flex items-center px-6 shrink-0 backdrop-blur-md bg-theme-base/50 sticky top-0 z-20">
        <div className="flex items-center gap-3">
          <span className="text-theme-muted text-2xl font-light">#</span>
          <h1 className="font-bold text-theme-primary tracking-tight">
            {room?.name || 'Loading...'}
          </h1>
        </div>
      </header>

      {/* Messages Area */}
      <div className="flex-1 overflow-y-auto p-6 space-y-1 scrollbar-thin custom-scrollbar">
        {loading && messages.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center text-theme-muted animate-pulse">
            <div className="w-12 h-12 mb-4 rounded-full bg-theme-primary/20 flex items-center justify-center">
              <div className="w-2 h-2 bg-theme-primary rounded-full animate-ping" />
            </div>
            <p className="text-sm font-medium tracking-widest uppercase">Initializing Stream...</p>
          </div>
        ) : messages.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center text-theme-muted/40">
            <p className="text-lg italic">No messages yet. Start the conversation.</p>
          </div>
        ) : (
          <>
            <div className="flex-1" />
            <div className="pb-4">
              {messages.map((msg) => (
                <MessageItem 
                  key={msg.messageId} 
                  message={msg} 
                  isMe={msg.authorName === user?.id}
                />
              ))}
              <div ref={messagesEndRef} />
            </div>
          </>
        )}
      </div>

      {/* Input Area - Always visible */}
      <div className="p-6 pt-0 shrink-0">
        <MessageInput 
          onSendMessage={handleSendMessage}
          placeholder={room ? `Message #${room.name}` : "Connecting..."}
        />
      </div>
    </div>
  );
};