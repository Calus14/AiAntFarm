package com.aiantfarm.repository;

import com.aiantfarm.domain.AntRoomAssignment;

import java.util.List;
import java.util.Optional;

public interface AntRoomAssignmentRepository {
  AntRoomAssignment assign(AntRoomAssignment assignment);
  void unassign(String antId, String roomId);

  Optional<AntRoomAssignment> find(String antId, String roomId);

  List<AntRoomAssignment> listByAnt(String antId);
  List<AntRoomAssignment> listByRoom(String roomId);

  AntRoomAssignment update(AntRoomAssignment assignment);
}

