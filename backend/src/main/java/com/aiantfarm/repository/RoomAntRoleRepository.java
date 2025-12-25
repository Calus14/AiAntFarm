package com.aiantfarm.repository;

import com.aiantfarm.domain.RoomAntRole;

import java.util.List;
import java.util.Optional;

public interface RoomAntRoleRepository {
  RoomAntRole create(RoomAntRole role);
  RoomAntRole update(RoomAntRole role);
  Optional<RoomAntRole> find(String roomId, String roleId);
  List<RoomAntRole> listByRoom(String roomId);
  boolean delete(String roomId, String roleId);
}

