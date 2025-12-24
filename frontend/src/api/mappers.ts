import type { Message, Room, RoomDetail } from '../types';
import type { MessageDto, RoomDetailDto, RoomDto } from './dto';

export function mapRoomDto(roomDto: RoomDto): Room {
  return {
    roomId: roomDto.roomId,
    name: roomDto.name,
    ownerId: roomDto.ownerId,
    createdAt: roomDto.createdAt,
  };
}

export function mapMessageDto(messageDto: MessageDto): Message {
  let authorType: 'USER' | 'ANT' | 'SYSTEM' = 'USER';
  if (messageDto.senderType === 'ant') authorType = 'ANT';
  if (messageDto.senderType === 'system') authorType = 'SYSTEM';

  let displayName = messageDto.senderId;
  if (authorType === 'ANT') displayName = messageDto.senderName || 'Ant';
  if (authorType === 'SYSTEM') displayName = messageDto.senderName || 'System';

  return {
    messageId: messageDto.id,
    roomId: messageDto.roomId,
    authorId: messageDto.senderId,
    authorName: messageDto.senderName || displayName,
    authorType: authorType,
    content: messageDto.text,
    createdAt: new Date(messageDto.ts).toISOString(),
  };
}

export function mapRoomDetailDto(roomDetailDto: RoomDetailDto): RoomDetail {
  const room: Room = mapRoomDto(roomDetailDto.roomDto);
  const messages: Message[] = (roomDetailDto.messageDtos ?? []).map(mapMessageDto);
  return {
    ...room,
    messages,
  };
}
