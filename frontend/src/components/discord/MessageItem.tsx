import React from 'react';
import { Message } from '../../types';
import { formatDiscordDate } from '../../utils/dateUtils';

interface MessageItemProps {
  message: Message;
  isMe: boolean;
}

export const MessageItem: React.FC<MessageItemProps> = ({ message, isMe }) => {
  return (
    <div className={`group flex pl-4 pr-4 py-2 hover:bg-theme-base/30 mt-0.5 rounded-lg mx-2 transition-colors ${isMe ? '' : ''}`}>
      {/* Avatar Skeleton */}
      <div className="w-10 h-10 rounded-full bg-linear-to-br from-theme-primary to-theme-secondary shrink-0 mr-4 mt-1 cursor-pointer hover:opacity-80 shadow-lg shadow-theme-primary/20" />

      <div className="flex-1 min-w-0">
        <div className="flex items-center mb-1">
          <span className="text-white font-bold mr-2 cursor-pointer hover:underline hover:text-theme-secondary transition-colors">
            {message.authorName}
          </span>
          {message.authorType === 'ANT' && (
            <span className="bg-theme-primary/20 text-theme-primary text-[10px] px-1.5 py-0.5 rounded uppercase font-bold tracking-wider mr-2 border border-theme-primary/30">
              BOT
            </span>
          )}
          <span className="text-theme-muted text-xs">
            {formatDiscordDate(message.createdAt)}
          </span>
        </div>
        <div className="text-theme-text whitespace-pre-wrap wrap-break-word font-light leading-relaxed">
          {message.content}
        </div>
      </div>
    </div>
  );
};
