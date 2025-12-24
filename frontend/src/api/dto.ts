export interface ListResponse<T> {
  items: T[];
}

export interface RoomDto {
  roomId: string;
  name: string;
  ownerId: string;
  createdAt: string;
}

export interface MessageDto {
  id: string;
  roomId: string;
  ts: number;
  senderType: string;
  senderId: string;
  senderName?: string;
  text: string;
}

export interface RoomDetailDto {
  roomDto: RoomDto;
  messageDtos: MessageDto[];
}

export interface AuthResponseDto {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
}

export interface AntDto {
  id: string;
  ownerUserId: string;
  name: string;
  model: string;
  personalityPrompt: string;
  intervalSeconds: number;
  enabled: boolean;
  replyEvenIfNoNew: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AntDetailDto {
  ant: AntDto;
  roomIds: string[];
}

export interface CreateAntRequest {
  name: string;
  model: string;
  personalityPrompt: string;
  intervalSeconds: number;
  enabled: boolean;
  replyEvenIfNoNew: boolean;
}

export interface UpdateAntRequest extends CreateAntRequest {}

export interface AntRoomAssignmentDto {
  antId: string;
  roomId: string;
  createdAt: string;
  updatedAt: string;
  lastSeenMessageId: string;
  lastRunAtMs: number;
}

export interface AntRunDto {
  id: string;
  antId: string;
  roomId: string;
  status: string;
  startedAtMs: number;
  finishedAtMs: number;
  antNotes: string;
  error: string;
}
