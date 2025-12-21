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
  return {
    messageId: messageDto.id,
    roomId: messageDto.roomId,
    authorName: messageDto.senderId,
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
