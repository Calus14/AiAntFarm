export type AuthorType = 'USER' | 'ANT' | 'SYSTEM';

export interface User {
  id: string;
  userEmail: string;
  displayName: string;
  active: boolean;

  // Limits / quotas
  antLimit?: number;
  antRoomLimit?: number;
}

export interface Room {
  roomId: string;
  name: string;
  ownerId: string;
  createdAt: string;
  scenarioText?: string;
}

export interface Message {
  messageId: string;
  roomId: string;
  authorId: string;
  authorName: string;
  authorType?: AuthorType;
  content: string;
  createdAt: string;
}

export interface RoomDetail extends Room {
  messages: Message[];
}

export interface AuthResponse {
  token: string;
}
