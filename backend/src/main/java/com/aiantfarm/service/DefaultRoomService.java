package com.aiantfarm.service;

import com.aiantfarm.api.dto.*;
import com.aiantfarm.domain.AuthorType;
import com.aiantfarm.domain.Message;
import com.aiantfarm.domain.Room;
import com.aiantfarm.exception.ResourceNotFoundException;
import com.aiantfarm.exception.RoomAlreadyExistsException;
import com.aiantfarm.repository.MessageRepository;
import com.aiantfarm.repository.Page;
import com.aiantfarm.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DefaultRoomService implements IRoomService {

  private final RoomRepository roomRepository;
  private final MessageRepository messageRepository;

  public DefaultRoomService(RoomRepository roomRepository, MessageRepository messageRepository) {
    this.roomRepository = roomRepository;
    this.messageRepository = messageRepository;
  }

  @Override
  public RoomDto createRoom(String userId, CreateRoomRequest req) {
    if (req == null || req.name() == null || req.name().isBlank()) {
      throw new IllegalArgumentException("name required");
    }

    var existingOpt = roomRepository.findByName(req.name());
    if (existingOpt != null && existingOpt.isPresent()) {
      throw new RoomAlreadyExistsException("room already exists");
    }

    var domainRoom = Room.create(req.name(), userId);
    domainRoom = roomRepository.create(domainRoom);
    return toRoomDto(domainRoom);
  }

  @Override
  public ListResponse<RoomDto> listCreated(String userId) {
    Page<Room> page = roomRepository.listByUserCreatedId(userId, 100, null);
    List<RoomDto> items = page.items().stream().map(this::toRoomDto).collect(Collectors.toList());
    return new ListResponse<>(items);
  }

  @Override
  public ListResponse<RoomDto> listAll(int limit, String nextToken) {
    Page<Room> page = roomRepository.listAll(limit, nextToken);
    List<RoomDto> items = page.items().stream().map(this::toRoomDto).collect(Collectors.toList());
    return new ListResponse<>(items);
  }

  @Override
  public RoomDetailDto getRoomDetail(String roomId) {
    var optRoom = roomRepository.findById(roomId);
    if (optRoom.isEmpty()) {
      throw new ResourceNotFoundException("room not found");
    }
    var roomDto = toRoomDto(optRoom.get());

    Page<Message> page = messageRepository.listByRoom(roomId, 200, null);
    List<MessageDto> msgs = page.items().stream().map(this::toMessageDto).collect(Collectors.toList());

    return new RoomDetailDto(roomDto, msgs);
  }

  @Override
  public MessageDto postMessage(String userId, String userName, String roomId, PostMessageRequest req) {
    if (roomRepository.findById(roomId).isEmpty()) {
      throw new ResourceNotFoundException("room not found");
    }
    var domainMsg = Message.createUserMsg(roomId, userId, userName, req.text());
    domainMsg = messageRepository.create(domainMsg);
    return toMessageDto(domainMsg);
  }

  // --- mappers (domain -> dto) ---

  private RoomDto toRoomDto(Room r) {
    return new RoomDto(r.id(), r.name(), r.createdByUserId(), r.scenarioText(), r.createdAt().toString());
  }

  private MessageDto toMessageDto(Message m) {
    String author =
        m.authorType() == AuthorType.USER
            ? "user"
            : (m.authorType() == AuthorType.ANT ? "ant" : "system");
    long tsMs = m.createdAt().toEpochMilli();
    return new MessageDto(m.id(), m.roomId(), tsMs, author, m.authorId(), m.authorName(), m.content());
  }
}
