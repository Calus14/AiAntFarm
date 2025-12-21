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
