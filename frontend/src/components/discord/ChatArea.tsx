import React, { useEffect, useState, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { apiClient, getAuthHeader, getStreamUrl } from '../../api/client';
import { roomApi } from '../../api/rooms';
import { useAuth } from '../../context/AuthContext';
import { Message, Room } from '../../types';
import type { MessageDto, RoomDetailDto } from '../../api/dto';
import { mapMessageDto, mapRoomDetailDto } from '../../api/mappers';
import { streamSse } from '../../api/sse';
import { SseEnvelopeType } from '../../api/enums';
import { MessageItem } from './MessageItem';
import { MessageInput } from './MessageInput';
import { AntsModal } from './AntsModal';
import { ScenarioPanel } from './ScenarioPanel';
import { ManageRolesModal } from './ManageRolesModal';

export const ChatArea = () => {
  const { roomId } = useParams<{ roomId: string }>();
  const { user } = useAuth();
  const [room, setRoom] = useState<Room | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(true);
  const [showAntsModal, setShowAntsModal] = useState(false);
  const [showRolesModal, setShowRolesModal] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'auto' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const handleUpdateScenario = async (newScenario: string) => {
    if (!roomId || !room) return;
    try {
      await roomApi.updateScenario(roomId, newScenario);
      setRoom({ ...room, scenarioText: newScenario });
    } catch (err) {
      console.error('Failed to update scenario', err);
      throw err;
    }
  };

  useEffect(() => {
    if (!roomId) return;

    // Switching rooms must reset local state; otherwise we merge the next room's
    // messages into the previous room's list.
    setRoom(null);
    setMessages([]);
    setLoading(true);

    const controller = new AbortController();
    let isMounted = true;
    let retryCount = 0;

    const fetchRoomState = async () => {
      try {
        const res = await apiClient.get<RoomDetailDto>(`/api/v1/rooms/${roomId}`);
        const detail = mapRoomDetailDto(res.data);
        
        if (!isMounted) return;
        
        setRoom(detail);
        const incomingMessages = (detail.messages || []);
        
        setMessages(prev => {
          const existing = new Map(prev.map(m => [m.messageId, m]));
          incomingMessages.forEach(m => existing.set(m.messageId, m));
          return Array.from(existing.values()).sort((a, b) => 
            new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
          );
        });
      } catch (err) {
        console.error('Failed to fetch room state', err);
        throw err;
      }
    };

    const connectLoop = async () => {
      setLoading(true);
      
      // Initial fetch
      try {
        await fetchRoomState();
        if (isMounted) setLoading(false);
      } catch (err) {
        // If initial fetch fails, we continue to retry loop
      }

      while (isMounted && !controller.signal.aborted) {
        try {
          await streamSse(getStreamUrl(roomId), {
            headers: { ...getAuthHeader() } as HeadersInit,
            signal: controller.signal,
            onEvent: (ev) => {
              const raw = (ev.data || '').trim();
              if (!raw || raw === '{}' || raw === 'null') return;
              try {
                const envelope = JSON.parse(raw) as { type?: string; payload?: MessageDto };
                if (envelope.type && envelope.type !== SseEnvelopeType.Message) return;
                const dto = envelope.payload;
                if (!dto) return;
                const mapped = mapMessageDto(dto);
                setMessages((prev) => {
                  if (prev.some((m) => m.messageId === mapped.messageId)) return prev;
                  return [...prev, mapped];
                });
              } catch (e) {
                console.error('Error parsing stream message', e, 'Raw data:', raw);
              }
            },
          });
          
          // If streamSse returns normally, reset retry count
          retryCount = 0;
          
        } catch (err: any) {
          if (controller.signal.aborted || err.name === 'AbortError') break;
          
          if (err.name === 'SseError' && err.status === 401) {
             console.error("SSE Unauthorized, stopping retry");
             break;
          }

          console.error('SSE Error, retrying...', err);
        }

        if (controller.signal.aborted) break;

        // Backoff
        const delay = Math.min(1000 * Math.pow(2, retryCount), 5000);
        await new Promise(resolve => setTimeout(resolve, delay));
        retryCount++;

        // Catch-up before reconnecting
        if (isMounted && !controller.signal.aborted) {
             await fetchRoomState().catch(() => {});
        }
      }
    };

    connectLoop();

    return () => {
      isMounted = false;
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
      <header className="h-16 border-b border-white/5 flex items-center justify-between px-6 shrink-0 backdrop-blur-md bg-theme-base/50 sticky top-0 z-20">
        <div className="flex items-center gap-3">
          <span className="text-theme-muted text-2xl font-light">#</span>
          <h1 className="font-bold text-theme-primary tracking-tight">
            {room?.name || 'Loading...'}
          </h1>
        </div>
        
        <button 
          onClick={() => setShowAntsModal(true)}
          className="flex items-center gap-2 px-3 py-1.5 rounded-md bg-theme-primary/10 text-theme-primary hover:bg-theme-primary/20 transition-colors text-sm font-medium"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path></svg>
          <span>Ants</span>
        </button>
      </header>

      {room && (
        <ScenarioPanel
          room={room}
          onUpdate={handleUpdateScenario}
          onManageRoles={() => setShowRolesModal(true)}
        />
      )}

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

      {showAntsModal && roomId && (
        <AntsModal roomId={roomId} onClose={() => setShowAntsModal(false)} />
      )}

      {showRolesModal && roomId && (
        <ManageRolesModal
          isOpen={showRolesModal}
          onClose={() => setShowRolesModal(false)}
          roomId={roomId}
        />
      )}
    </div>
  );
};