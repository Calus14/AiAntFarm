package com.aiantfarm.service;

import com.aiantfarm.api.dto.*;
import com.aiantfarm.domain.Ant;
import com.aiantfarm.domain.AntRoomAssignment;
import com.aiantfarm.domain.Room;
import com.aiantfarm.domain.RoomAntRole;
import com.aiantfarm.exception.ResourceNotFoundException;
import com.aiantfarm.repository.AntRepository;
import com.aiantfarm.repository.AntRoomAssignmentRepository;
import com.aiantfarm.repository.RoomAntRoleRepository;
import com.aiantfarm.repository.RoomRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class DefaultRoomAntRoleService implements RoomAntRoleService {

  private final RoomRepository roomRepository;
  private final RoomAntRoleRepository roomAntRoleRepository;
  private final AntRepository antRepository;
  private final AntRoomAssignmentRepository antRoomAssignmentRepository;

  private final int maxSpotsLimit;

  public DefaultRoomAntRoleService(
      RoomRepository roomRepository,
      RoomAntRoleRepository roomAntRoleRepository,
      AntRepository antRepository,
      AntRoomAssignmentRepository antRoomAssignmentRepository,
      @Value("${antfarm.rooms.antRoles.maxSpotsLimit:5}") int maxSpotsLimit
  ) {
    this.roomRepository = roomRepository;
    this.roomAntRoleRepository = roomAntRoleRepository;
    this.antRepository = antRepository;
    this.antRoomAssignmentRepository = antRoomAssignmentRepository;
    this.maxSpotsLimit = maxSpotsLimit;
  }

  @Override
  public ListResponse<RoomAntRoleDto> list(String ownerUserId, String roomId) {
    Room room = requireOwnedRoom(ownerUserId, roomId);
    List<RoomAntRoleDto> items = roomAntRoleRepository.listByRoom(room.id()).stream().map(this::toDto).toList();
    return new ListResponse<>(items);
  }

  @Override
  public RoomAntRoleDto create(String ownerUserId, String roomId, CreateRoomAntRoleRequest req) {
    Room room = requireOwnedRoom(ownerUserId, roomId);
    if (req == null || req.name() == null || req.name().isBlank()) {
      throw new IllegalArgumentException("name required");
    }

    int maxSpots = req.maxSpots() == null ? 1 : req.maxSpots();
    validateMaxSpots(maxSpots);

    RoomAntRole role = RoomAntRole.create(room.id(), req.name(), req.prompt(), maxSpots);
    roomAntRoleRepository.create(role);
    return toDto(role);
  }

  @Override
  public RoomAntRoleDto update(String ownerUserId, String roomId, String roleId, UpdateRoomAntRoleRequest req) {
    requireOwnedRoom(ownerUserId, roomId);

    RoomAntRole existing = roomAntRoleRepository.find(roomId, roleId)
        .orElseThrow(() -> new ResourceNotFoundException("role not found"));

    Integer maxSpots = req == null ? null : req.maxSpots();
    if (maxSpots != null) validateMaxSpots(maxSpots);

    RoomAntRole updated = existing.withUpdated(
        req == null ? null : req.name(),
        req == null ? null : req.prompt(),
        maxSpots
    );

    roomAntRoleRepository.update(updated);
    return toDto(updated);
  }

  @Override
  public void delete(String ownerUserId, String roomId, String roleId) {
    requireOwnedRoom(ownerUserId, roomId);

    // Clear assignments that reference this role
    try {
      List<AntRoomAssignment> assigns = antRoomAssignmentRepository.listByRoom(roomId);
      for (AntRoomAssignment a : assigns) {
        if (roleId != null && roleId.equals(a.roleId())) {
          antRoomAssignmentRepository.update(a.withRole(null, null));
        }
      }
    } catch (Exception e) {
      log.warn("Failed to clear role assignments roomId={} roleId={} (continuing)", roomId, roleId, e);
    }

    roomAntRoleRepository.delete(roomId, roleId);
  }

  @Override
  public void assignToAntInRoom(String ownerUserId, String antId, String roomId, AssignAntRoomRoleRequest req) {
    // authz: must own room AND ant
    requireOwnedRoom(ownerUserId, roomId);

    Ant ant = antRepository.findById(antId).orElseThrow(() -> new ResourceNotFoundException("ant not found"));
    if (!ownerUserId.equals(ant.ownerUserId())) {
      throw new SecurityException("forbidden");
    }

    AntRoomAssignment assignment = antRoomAssignmentRepository.find(antId, roomId)
        .orElseThrow(() -> new ResourceNotFoundException("ant not assigned to room"));

    // Clearing role
    if (req == null || req.roleId() == null || req.roleId().isBlank()) {
      antRoomAssignmentRepository.update(assignment.withRole(null, null));
      return;
    }

    RoomAntRole role = roomAntRoleRepository.find(roomId, req.roleId())
        .orElseThrow(() -> new ResourceNotFoundException("role not found"));

    // Enforce capacity: count current assignments in the room referencing this role id.
    int used = 0;
    for (AntRoomAssignment a : antRoomAssignmentRepository.listByRoom(roomId)) {
      if (role.roleId().equals(a.roleId())) used++;
    }

    if (used >= role.maxSpots()) {
      throw new IllegalArgumentException("role is full");
    }

    antRoomAssignmentRepository.update(assignment.withRole(role.roleId(), role.name()));
  }

  private Room requireOwnedRoom(String ownerUserId, String roomId) {
    Room room = roomRepository.findById(roomId)
        .orElseThrow(() -> new ResourceNotFoundException("room not found"));
    if (room.createdByUserId() == null || !room.createdByUserId().equals(ownerUserId)) {
      throw new SecurityException("forbidden");
    }
    return room;
  }

  private void validateMaxSpots(int maxSpots) {
    if (maxSpots < 1) throw new IllegalArgumentException("maxSpots must be >= 1");
    if (maxSpotsLimit > 0 && maxSpots > maxSpotsLimit) {
      throw new IllegalArgumentException("maxSpots must be <= " + maxSpotsLimit);
    }
  }

  private RoomAntRoleDto toDto(RoomAntRole r) {
    // RoomAntRoleDto is defined as: (maxSpots, prompt, name, roleId, roomId)
    return new RoomAntRoleDto(r.maxSpots(), r.prompt(), r.name(), r.roleId(), r.roomId());
  }
}
