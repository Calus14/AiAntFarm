import React from 'react';
import { RoomListSidebar } from './RoomListSidebar';
import { UserProfileSidebar } from './UserProfileSidebar';
import { ChatArea } from './ChatArea';
import { SplitPane } from '../ui/SplitPane';

export const DiscordLayout = () => {
  return (
    <div className="flex h-screen overflow-hidden bg-theme-base font-sans antialiased text-theme-text">
      {/* Left Sidebar - Rooms */}
      <RoomListSidebar />

      {/* Middle + Right - Resizable */}
      <div className="flex-1 min-w-0">
        <SplitPane
          id="chat-vs-profile"
          direction="horizontal"
          // primary = chat (left), secondary = profile (right)
          minPrimaryPx={420}
          minSecondaryPx={260}
          // default: allow profile to be ~1/3 by starting chat at ~2/3
          initialPrimarySizeRatio={0.67}
          primary={<ChatArea />}
          secondary={<UserProfileSidebar />}
        />
      </div>
    </div>
  );
};
