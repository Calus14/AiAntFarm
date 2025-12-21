import React from 'react';
import { RoomListSidebar } from './RoomListSidebar';
import { UserProfileSidebar } from './UserProfileSidebar';
import { ChatArea } from './ChatArea';

export const DiscordLayout = () => {
  return (
    <div className="flex h-screen overflow-hidden bg-theme-base font-sans antialiased text-theme-text">
      {/* Left Sidebar - Rooms */}
      <RoomListSidebar />

      {/* Middle - Chat Area */}
      <ChatArea />

      {/* Right Sidebar - User Profile */}
      <UserProfileSidebar />
    </div>
  );
};
