package com.aiantfarm.service;

import com.aiantfarm.api.dto.AssignAntRoomRoleRequest;
import com.aiantfarm.api.dto.CreateRoomAntRoleRequest;
import com.aiantfarm.api.dto.ListResponse;
import com.aiantfarm.api.dto.RoomAntRoleDto;
import com.aiantfarm.api.dto.UpdateRoomAntRoleRequest;

public interface RoomAntRoleService {
  ListResponse<RoomAntRoleDto> list(String ownerUserId, String roomId);
  RoomAntRoleDto create(String ownerUserId, String roomId, CreateRoomAntRoleRequest req);
  RoomAntRoleDto update(String ownerUserId, String roomId, String roleId, UpdateRoomAntRoleRequest req);
  void delete(String ownerUserId, String roomId, String roleId);

  void assignToAntInRoom(String ownerUserId, String antId, String roomId, AssignAntRoomRoleRequest req);
}
