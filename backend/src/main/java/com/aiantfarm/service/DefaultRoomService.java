package com.aiantfarm.service;

import com.aiantfarm.api.dto.*;
import com.aiantfarm.domain.AuthorType;
import com.aiantfarm.domain.Message;
import com.aiantfarm.domain.Room;
import com.aiantfarm.domain.User;
import com.aiantfarm.exception.QuotaExceededException;
import com.aiantfarm.exception.ResourceNotFoundException;
import com.aiantfarm.exception.RoomAlreadyExistsException;
import com.aiantfarm.repository.AntRoomAssignmentRepository;
import com.aiantfarm.repository.MessageRepository;
import com.aiantfarm.repository.Page;
import com.aiantfarm.repository.RoomAntRoleRepository;
import com.aiantfarm.repository.RoomRepository;
import com.aiantfarm.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DefaultRoomService implements IRoomService {

  private final RoomRepository roomRepository;
  private final MessageRepository messageRepository;
  private final RoomAntRoleRepository roomAntRoleRepository;
  private final AntRoomAssignmentRepository antRoomAssignmentRepository;
  private final UserRepository userRepository;

  private final int defaultRoomLimit;

  public DefaultRoomService(RoomRepository roomRepository,
                            MessageRepository messageRepository,
                            RoomAntRoleRepository roomAntRoleRepository,
                            AntRoomAssignmentRepository antRoomAssignmentRepository,
                            UserRepository userRepository,
                            @Value("${antfarm.limits.defaultRoomLimit:1}") int defaultRoomLimit) {
    this.roomRepository = roomRepository;
    this.messageRepository = messageRepository;
    this.roomAntRoleRepository = roomAntRoleRepository;
    this.antRoomAssignmentRepository = antRoomAssignmentRepository;
    this.userRepository = userRepository;
    this.defaultRoomLimit = defaultRoomLimit;
  }

  @Override
  public RoomDto createRoom(String userId, CreateRoomRequest req) {

    // Per-user room creation limit
    User user = userRepository.findByUserId(userId).orElseThrow(() -> new SecurityException("forbidden"));
    int roomLimit = user.roomLimit() != null ? user.roomLimit() : defaultRoomLimit;
    if (roomLimit > 0) {
      int existingForUser = roomRepository.listByUserCreatedId(userId, roomLimit + 1, null).items().size();
      if (existingForUser >= roomLimit) {
        throw new QuotaExceededException("Room creation limit reached (max " + roomLimit + " rooms)");
      }
    }

    if (req == null || req.name() == null || req.name().isBlank()) {
      throw new IllegalArgumentException("name required");
    }

    // Enforce globally-unique room names via GSI (no scan)
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

  @Override
  public void deleteRoom(String ownerUserId, String roomId) {
    if (roomId == null || roomId.isBlank()) throw new IllegalArgumentException("roomId required");

    Room room = roomRepository.findById(roomId)
        .orElseThrow(() -> new ResourceNotFoundException("room not found"));

    if (ownerUserId == null || !ownerUserId.equals(room.createdByUserId())) {
      throw new SecurityException("forbidden");
    }

    // Best-effort cascades. For MVP, we do not attempt transactional deletes.

    // 1) Delete room roles
    try {
      var roles = roomAntRoleRepository.listByRoom(roomId);
      for (var role : roles) {
        if (role == null) continue;
        roomAntRoleRepository.delete(roomId, role.roleId());
      }
    } catch (Exception e) {
      log.warn("Room delete cascade failed: roles roomId={} ownerUserId={}", roomId, ownerUserId, e);
    }

    // 2) Delete ant-room assignments for this room
    try {
      var assigns = antRoomAssignmentRepository.listByRoom(roomId);
      for (var a : assigns) {
        if (a == null) continue;
        antRoomAssignmentRepository.unassign(a.antId(), roomId);
      }
    } catch (Exception e) {
      log.warn("Room delete cascade failed: ant-room-assignments roomId={} ownerUserId={}", roomId, ownerUserId, e);
    }

    // 3) Delete all messages for this room
    try {
      messageRepository.deleteAllByRoom(roomId);
    } catch (Exception e) {
      log.warn("Room delete cascade failed: messages roomId={} ownerUserId={}", roomId, ownerUserId, e);
    }

    // 4) Delete the room metadata itself
    roomRepository.deleteByRoomId(roomId);
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
